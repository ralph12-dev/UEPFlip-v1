package com.example.uepflip_flashcard.data.service

import android.util.Log
import com.squareup.moshi.Moshi
import com.squareup.moshi.kotlin.reflect.KotlinJsonAdapterFactory
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.io.IOException
import java.util.concurrent.TimeUnit

/** A single flashcard produced by the AI. */
data class GeneratedCard(
    val question: String,
    val answer: String,
    /**
     * A nudge that points the student toward [answer] without stating or paraphrasing it.
     *
     * Nullable because older/cached response shapes and failed generations may omit it. The study
     * screen then falls back to the on-demand [AiFlashcardGenerator.generateHint], which carries an
     * explicit "do not reveal" prompt.
     */
    val hint: String? = null
)

/**
 * Raised when flashcards cannot be produced. The message is written for end users and is safe to
 * surface directly in the UI.
 */
class AiGenerationException(message: String, cause: Throwable? = null) : Exception(message, cause)

// --- Gemini wire models (only the fields we consume) -----------------------------------------

internal data class GeminiPart(val text: String? = null)
internal data class GeminiContent(val role: String? = null, val parts: List<GeminiPart> = emptyList())
internal data class GeminiCandidate(val content: GeminiContent? = null, val finishReason: String? = null)
internal data class GeminiPromptFeedback(val blockReason: String? = null)
internal data class GeminiResponse(
    val candidates: List<GeminiCandidate>? = null,
    val promptFeedback: GeminiPromptFeedback? = null
)

internal data class GeminiThinkingConfig(val thinkingLevel: String? = null)
internal data class GeminiGenerationConfig(val thinkingConfig: GeminiThinkingConfig? = null)
internal data class GeminiRequest(
    val contents: List<GeminiContent>,
    val generationConfig: GeminiGenerationConfig? = null
)

/**
 * Raw shape we ask the model to return inside its text part.
 *
 * [RawFlashcard.hint] is deliberately `Any?` rather than `String?`: Moshi's `String` adapter throws
 * on a JSON object/array/number, and that exception would fail the whole import and lose every card
 * in the response. With `Any?` an unusable hint simply becomes `null` in [GeneratedCard], which is
 * the documented "bad hint costs the hint, never the card" contract.
 */
internal data class RawFlashcard(
    val question: String? = null,
    val answer: String? = null,
    val hint: Any? = null
)

internal data class FlashcardResponse(val flashcards: List<RawFlashcard> = emptyList())

/** Shape we ask the model to return when grading a student's recall. */
internal data class EvaluationResponse(
    val score: Int? = null,
    val correct: Boolean? = null,
    val note: String? = null,
    val explanation: String? = null
)

/**
 * A graded answer. [score] is the model's 0-100 judgement and is never invented by the UI:
 * if the model returns nothing usable the call fails instead of showing a fake number.
 */
data class AnswerEvaluation(
    val score: Int,
    val correct: Boolean,
    val note: String,
    val explanation: String
)

/**
 * Talks to the Gemini `generateContent` API to turn raw text into flashcards.
 *
 * The API key is supplied by the caller (read at runtime from Firebase Remote Config) and is never
 * hardcoded or logged. All failures are surfaced as [AiGenerationException] with an actionable
 * message so the UI can recover gracefully.
 */
class AiFlashcardGenerator(
    private val apiKey: String,
    private val endpoint: String = DEFAULT_ENDPOINT,
    private val client: OkHttpClient = defaultClient(),
    /**
     * Thinking level requested for the short, latency-sensitive calls (hint, grading, explanation).
     *
     * `null` means "send no `generationConfig` at all", which is what must happen for models that do
     * not accept `thinkingLevel` — Gemini 2.x uses `thinkingBudget` and the API rejects the newer
     * field. [AiGeneratorProvider] decides this from the resolved model id; the default keeps
     * injected-endpoint tests and older models on the previous behaviour.
     */
    private val fastThinkingLevel: String? = null
) {
    companion object {
        private const val TAG = "AiFlashcardGenerator"
        private const val MAX_INPUT_CHARS = 30_000

        /**
         * Longest hint we keep. A hint is one or two sentences, so this only clips runaway output;
         * trimming happens at a word boundary so it does not end mid-word.
         */
        private const val MAX_HINT_CHARS = 240

        /**
         * Shortest answer worth a verbatim leak-check. Very short answers such as `42` occur inside
         * innocent hints, so checking them would discard good hints; a three-character answer like
         * `ATP` is still checked.
         */
        private const val MIN_LEAK_CHECK_ANSWER_CHARS = 3

        /** Score at or above which an answer counts as correct. */
        private const val PASS_MARK = 60

        /**
         * Fastest thinking level that is valid across the Gemini 3.x Flash ids this app is likely to
         * be pointed at. `minimal` is deliberately not used: `gemini-3.7-flash` and
         * `gemini-3.8-flash` reject it with an error, and a Remote Config model change must not be
         * able to break hints via this constant.
         */
        const val THINKING_LEVEL_LOW = "low"

        private const val API_ROOT = "https://generativelanguage.googleapis.com/v1beta/models"

        /**
         * Prefix every id carries in the Gemini `ListModels` response (`models/gemini-…`). Accepted
         * and stripped, because that is the exact string an operator will paste out of that response
         * into Remote Config.
         */
        private const val MODELS_PREFIX = "models/"

        /**
         * Model used when Remote Config does not override it.
         *
         * This must be a model the Gemini API still serves. Google retires model ids on a
         * schedule (**`gemini-2.0-flash` was shut down on 2026-06-01 and now answers `404`**),
         * and a retired id breaks every AI feature at once. `AiGeneratorProvider` reads an
         * optional `gemini_model` Remote Config override so a retirement can be worked around
         * without shipping a new build; the version-agnostic `*-latest` alias is the default so the
         * alias keeps resolving across retirements.
         */
        const val DEFAULT_MODEL = "gemini-flash-latest"

        /**
         * Matches the model-id characters the Gemini API accepts: letters, digits, `.`, `-`, `_`,
         * beginning with a letter or digit.
         *
         * Must stay declared **before** [DEFAULT_ENDPOINT]: that property is initialised by calling
         * [endpointFor], which reads this pattern. Reordering the two makes the companion initialiser
         * dereference a null delegate — a failure the Kotlin compiler does not flag.
         */
        private val MODEL_PATTERN by lazy { Regex("^[A-Za-z0-9][A-Za-z0-9._-]{0,63}$") }

        /** Trims [model] and drops a leading `models/`; `null` and blank become `""`. */
        private fun normalizeModel(model: String?): String =
            model?.trim().orEmpty().removePrefix(MODELS_PREFIX)

        /**
         * True when [model] is usable **after normalisation** (trimmed, `models/` stripped). A raw
         * `models/gemini-x` therefore validates, which is intended — callers must use [resolveModel]
         * to get the id they actually send.
         */
        fun isValidModelId(model: String?): Boolean = MODEL_PATTERN.matches(normalizeModel(model))

        /** [model] when valid, otherwise [DEFAULT_MODEL]. Never throws, never returns blank. */
        fun resolveModel(model: String?): String {
            val candidate = normalizeModel(model)
            return if (MODEL_PATTERN.matches(candidate)) candidate else DEFAULT_MODEL
        }

        /**
         * The `generateContent` endpoint for [model], or the [DEFAULT_MODEL] endpoint when [model]
         * is blank or not a plausible model id.
         *
         * Validating here keeps a bad Remote Config value from producing a malformed URL or a
         * request to another host: `/`, `:`, `@`, `?` and `#` cannot survive the pattern.
         */
        fun endpointFor(model: String?): String = "$API_ROOT/${resolveModel(model)}:generateContent"

        /** Endpoint for [DEFAULT_MODEL]. Prefer [endpointFor] so the model stays configurable. */
        val DEFAULT_ENDPOINT: String = endpointFor(DEFAULT_MODEL)

        private val JSON_MEDIA_TYPE = "application/json; charset=utf-8".toMediaType()

        private fun defaultClient(): OkHttpClient = OkHttpClient.Builder()
            .connectTimeout(30, TimeUnit.SECONDS)
            .writeTimeout(60, TimeUnit.SECONDS)
            .readTimeout(60, TimeUnit.SECONDS)
            .build()
    }

    private val moshi = Moshi.Builder()
        .add(KotlinJsonAdapterFactory())
        .build()

    /**
     * Requests flashcards for [text]. Runs on an IO dispatcher and throws [AiGenerationException]
     * on any failure. Cancellation propagates normally.
     *
     * @param isTopic true when [text] is a short topic/lesson the student wants to learn about
     *   rather than a document to summarise; the model is prompted to expand it instead of treating
     *   it as source material.
     */
    suspend fun generateFlashcards(
        text: String,
        isTopic: Boolean = false
    ): List<GeneratedCard> {
        if (text.isBlank()) {
            throw AiGenerationException("There is no text to generate flashcards from.")
        }
        return parseFlashcards(requestModelText(buildFlashcardPrompt(text.take(MAX_INPUT_CHARS), isTopic)))
    }

    /**
     * Asks for a single nudge about [question] without revealing [answer]. Used by the study
     * screen's "Request hint" when the card has no stored hint; the hint is whatever the model
     * wrote, so nothing is fabricated locally.
     *
     * The result goes through the same [sanitizeHint] rule as import-time hints: a hint that repeats
     * the answer is not something we are willing to show, even on request. In that case the call
     * fails, which the study screen reports — a missing hint is better than a leaked answer.
     */
    suspend fun generateHint(question: String, answer: String): String {
        if (question.isBlank()) {
            throw AiGenerationException("There is nothing to give a hint for.")
        }
        val prompt = """
            You are a study coach. A student is looking at this flashcard and wants a nudge, not the
            answer.

            Question: ${question.take(MAX_INPUT_CHARS)}
            Answer (do NOT state or paraphrase this): ${answer.take(MAX_INPUT_CHARS)}

            Write one or two short sentences that point the student toward the right idea without
            giving the answer away. Reply with plain text only.
        """.trimIndent()
        val raw = requestModelText(prompt, thinkingLevel = fastThinkingLevel)
        return sanitizeHint(raw, answer)
            ?: throw AiGenerationException("The AI returned an unusable hint. Please try again.")
    }

    /**
     * Grades [studentAnswer] against the card's [referenceAnswer]. The score comes straight from the
     * model; an unparseable response is reported as a failure rather than papered over.
     */
    suspend fun evaluateAnswer(
        question: String,
        referenceAnswer: String,
        studentAnswer: String
    ): AnswerEvaluation {
        if (studentAnswer.isBlank()) {
            throw AiGenerationException("Write an answer before asking for feedback.")
        }
        val prompt = """
            You are grading a student's recall of a flashcard.

            Question: ${question.take(MAX_INPUT_CHARS)}
            Reference answer: ${referenceAnswer.take(MAX_INPUT_CHARS)}
            Student's answer: ${studentAnswer.take(MAX_INPUT_CHARS)}

            Judge only whether the student captured the reference answer's key idea. Be fair to
            paraphrases and minor wording differences.

            Respond with ONLY a JSON object in exactly this shape, with no extra commentary:
            {"score":0,"correct":false,"note":"one short sentence","explanation":"two or three sentences"}

            "score" is 0-100 and must agree with "correct" (below $PASS_MARK is incorrect).
        """.trimIndent()

        val parsed = parseEvaluation(requestModelText(prompt, thinkingLevel = fastThinkingLevel))
        val note = parsed.note?.trim().orEmpty()
        val modelScore = parsed.score
        // A missing score means the model never actually graded the answer. Fail the call rather
        // than defaulting to 0, which would show the student a fabricated "0%" accuracy card.
        if (modelScore == null || note.isBlank()) {
            throw AiGenerationException("The AI response was not in the expected format.")
        }
        val score = modelScore.coerceIn(0, 100)
        return AnswerEvaluation(
            score = score,
            correct = parsed.correct ?: (score >= PASS_MARK),
            note = note,
            explanation = parsed.explanation?.trim().orEmpty()
        )
    }

    /** Deeper explanation shown behind the accuracy card's "View more explanation" button. */
    suspend fun generateExplanation(
        question: String,
        referenceAnswer: String,
        studentAnswer: String
    ): String {
        if (question.isBlank()) {
            throw AiGenerationException("There is nothing to explain.")
        }
        val prompt = """
            A student is reviewing this flashcard.

            Question: ${question.take(MAX_INPUT_CHARS)}
            Reference answer: ${referenceAnswer.take(MAX_INPUT_CHARS)}
            The student answered: ${studentAnswer.take(MAX_INPUT_CHARS)}

            In three to five sentences, explain why the reference answer is correct, call out the
            part the student missed, and give one thing to remember next time. Reply with plain text
            only.
        """.trimIndent()
        return requestModelText(prompt, thinkingLevel = fastThinkingLevel).trim().ifBlank {
            throw AiGenerationException("The AI returned an empty explanation. Please try again.")
        }
    }

    /**
     * Performs the HTTPS call and returns the model's raw text part.
     *
     * [thinkingLevel] is sent only when non-null. Hint, grading and explanation pass the generator's
     * [fastThinkingLevel] because their answers are a sentence or two and the user is waiting; on
     * Gemini 3.x the default thinking level makes them take seconds, which is the latency this
     * parameter exists to remove. Flashcard generation deliberately keeps the model default.
     */
    private suspend fun requestModelText(
        prompt: String,
        thinkingLevel: String? = null
    ): String = withContext(Dispatchers.IO) {
        if (apiKey.isBlank()) {
            throw AiGenerationException("AI features are not set up yet. Please try again later.")
        }

        val request = Request.Builder()
            .url("$endpoint?key=$apiKey")
            .post(buildRequestBody(prompt, thinkingLevel).toRequestBody(JSON_MEDIA_TYPE))
            .build()

        val responseBody = try {
            client.newCall(request).execute().use { response ->
                val payload = response.body?.string().orEmpty()
                if (!response.isSuccessful) {
                    // Log the body for debugging but never echo the key or raw payload to the user.
                    Log.e(TAG, "Gemini request failed with ${response.code}: $payload")
                    throw AiGenerationException(
                        "The AI service returned an error (${response.code}). Please try again."
                    )
                }
                payload
            }
        } catch (e: IOException) {
            Log.e(TAG, "Network error calling Gemini", e)
            throw AiGenerationException("Network error. Check your connection and try again.", e)
        }

        extractCandidateText(responseBody)
    }

    private fun buildRequestBody(prompt: String, thinkingLevel: String? = null): String {
        // Moshi omits null properties, so a null thinkingLevel keeps the wire format exactly as it
        // was before this parameter existed (and keeps models that reject `thinkingLevel` working).
        val request = GeminiRequest(
            contents = listOf(GeminiContent(parts = listOf(GeminiPart(prompt)))),
            generationConfig = thinkingLevel?.let { level ->
                GeminiGenerationConfig(thinkingConfig = GeminiThinkingConfig(thinkingLevel = level))
            }
        )
        // Serialising with Moshi guarantees the prompt is correctly JSON-escaped.
        return moshi.adapter(GeminiRequest::class.java).toJson(request)
    }

    /** Unwraps the Gemini envelope and returns the model's text part. */
    private fun extractCandidateText(responseBody: String): String {
        val gemini = try {
            moshi.adapter(GeminiResponse::class.java).fromJson(responseBody)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to parse Gemini envelope", e)
            throw AiGenerationException("The AI returned an unreadable response.", e)
        }

        gemini?.promptFeedback?.blockReason?.let { reason ->
            throw AiGenerationException("The request was blocked by the AI service ($reason).")
        }

        val text = gemini?.candidates
            .orEmpty()
            .flatMap { it.content?.parts.orEmpty() }
            .mapNotNull { it.text }
            .joinToString("\n")
            .trim()

        if (text.isBlank()) {
            throw AiGenerationException("The AI returned an empty response. Please try again.")
        }
        return text
    }

    private fun parseFlashcards(modelText: String): List<GeneratedCard> {
        val parsed = try {
            moshi.adapter(FlashcardResponse::class.java).fromJson(extractJsonObject(modelText))
        } catch (e: Exception) {
            Log.e(TAG, "Failed to parse flashcard JSON: $modelText", e)
            throw AiGenerationException("The AI response was not in the expected flashcard format.", e)
        }

        return parsed?.flashcards.orEmpty()
            .map { raw ->
                GeneratedCard(
                    question = raw.question.orEmpty(),
                    answer = raw.answer.orEmpty(),
                    // A non-string hint (object/array/number) is unusable, not fatal.
                    hint = raw.hint as? String
                )
            }
            .filter { it.question.isNotBlank() && it.answer.isNotBlank() }
            .map { card -> card.copy(hint = sanitizeHint(card.hint, card.answer)) }
    }

    /**
     * Normalises a model-written [hint], or returns `null` when it is unusable.
     *
     * Used both for the cards generated at import time and for the on-demand [generateHint] fallback.
     * Neither has a second review pass, so a leak degrades to "no hint" rather than showing the
     * answer. For imports that means the stored hint is null and the study screen falls back to
     * [generateHint]; for the fallback itself an unusable hint is reported as a failure, because a
     * missing hint is better than a leaked answer.
     */
    private fun sanitizeHint(hint: String?, answer: String): String? {
        val trimmed = hint?.trim().orEmpty()
        if (trimmed.isBlank()) return null

        val capped = if (trimmed.length <= MAX_HINT_CHARS) {
            trimmed
        } else {
            // Cut at the last space before the cap so the text does not end mid-word.
            val lastSpace = trimmed.lastIndexOf(' ', MAX_HINT_CHARS)
            (if (lastSpace > 0) trimmed.substring(0, lastSpace) else trimmed.substring(0, MAX_HINT_CHARS))
                .trim()
        }
        if (capped.isBlank()) return null

        val reference = answer.trim()
        // A verbatim answer is a leak regardless of casing; paraphrases are the prompt's job.
        val leaks = reference.length >= MIN_LEAK_CHECK_ANSWER_CHARS &&
            capped.contains(reference, ignoreCase = true)
        return if (leaks) null else capped
    }

    private fun parseEvaluation(modelText: String): EvaluationResponse {
        val parsed = try {
            moshi.adapter(EvaluationResponse::class.java).fromJson(extractJsonObject(modelText))
        } catch (e: Exception) {
            Log.e(TAG, "Failed to parse evaluation JSON: $modelText", e)
            throw AiGenerationException("The AI response was not in the expected format.", e)
        }
        return parsed
            ?: throw AiGenerationException("The AI returned an empty evaluation. Please try again.")
    }

    /** Strips markdown code fences and any prose around the JSON object the model returned. */
    private fun extractJsonObject(raw: String): String {
        val cleaned = raw.replace("```json", "").replace("```", "").trim()
        val start = cleaned.indexOf('{')
        val end = cleaned.lastIndexOf('}')
        return if (start >= 0 && end > start) cleaned.substring(start, end + 1) else cleaned
    }

    private fun buildFlashcardPrompt(text: String, isTopic: Boolean): String {
        val introduction = if (isTopic) {
            """
            You are a flashcard generator for students. Build a study set for the topic or lesson
            below, covering its key concepts with concise question/answer flashcards.
            """.trimIndent()
        } else {
            """
            You are a flashcard generator for students. Read the material below and create concise
            question/answer flashcards that test understanding of the key concepts.
            """.trimIndent()
        }
        val label = if (isTopic) "Topic or lesson" else "Material"

        return """
            $introduction

            $label:
            $text

            Respond with ONLY a JSON object in exactly this shape, with no extra commentary:
            {"flashcards":[{"question":"...","answer":"...","hint":"..."}]}

            Create between 5 and 10 flashcards. Each question must be self-contained.
            Each card's "hint" is one or two short plain-text sentences that point the student toward
            the answer without stating or paraphrasing it. The hint is shown only after the student
            asks for it, so it must not give the answer away.
        """.trimIndent()
    }
}
