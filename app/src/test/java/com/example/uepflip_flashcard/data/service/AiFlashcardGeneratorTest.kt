package com.example.uepflip_flashcard.data.service

import com.squareup.moshi.Moshi
import com.squareup.moshi.Types
import com.squareup.moshi.kotlin.reflect.KotlinJsonAdapterFactory
import kotlinx.coroutines.test.runTest
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Protocol
import okhttp3.Response
import okhttp3.ResponseBody.Companion.toResponseBody
import okio.Buffer
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test

/**
 * Exercises [AiFlashcardGenerator] end-to-end through a stubbed OkHttp client: request
 * serialisation, Gemini envelope parsing, markdown-fence stripping and error reporting.
 */
class AiFlashcardGeneratorTest {

    private val moshi = Moshi.Builder()
        .add(KotlinJsonAdapterFactory())
        .build()

    private val mapAdapter = moshi.adapter<Map<String, Any>>(
        Types.newParameterizedType(Map::class.java, String::class.java, Any::class.java)
    )

    // Wire-format stand-ins so the test never hand-writes escaped JSON.
    private data class TPart(val text: String)
    private data class TContent(val parts: List<TPart>)
    private data class TCandidate(val content: TContent)
    private data class TEnvelope(val candidates: List<TCandidate>)

    private fun envelope(innerText: String): String = moshi
        .adapter(TEnvelope::class.java)
        .toJson(TEnvelope(listOf(TCandidate(TContent(listOf(TPart(innerText)))))))

    private fun client(code: Int, body: String, onRequest: ((String) -> Unit)? = null): OkHttpClient =
        OkHttpClient.Builder()
            .addInterceptor { chain ->
                val request = chain.request()
                onRequest?.invoke(request.body?.let { requestBody ->
                    val buffer = Buffer()
                    requestBody.writeTo(buffer)
                    buffer.readUtf8()
                } ?: "")
                Response.Builder()
                    .request(request)
                    .protocol(Protocol.HTTP_1_1)
                    .code(code)
                    .message("test")
                    .body(body.toResponseBody("application/json".toMediaType()))
                    .build()
            }
            .build()

    private fun generator(
        code: Int,
        body: String,
        fastThinkingLevel: String? = null,
        onRequest: ((String) -> Unit)? = null
    ) = AiFlashcardGenerator(
        apiKey = "test-key",
        endpoint = "http://localhost/test",
        client = client(code, body, onRequest),
        fastThinkingLevel = fastThinkingLevel
    )

    @Test
    fun parsesFlashcardsFromSuccessfulResponse() = runTest {
        val inner =
            """{"flashcards":[{"question":"What is 2+2?","answer":"4"},{"question":"Capital of France?","answer":"Paris"}]}"""

        val cards = generator(200, envelope(inner)).generateFlashcards("some notes")

        assertEquals(2, cards.size)
        assertEquals("What is 2+2?", cards[0].question)
        assertEquals("4", cards[0].answer)
        assertEquals("Paris", cards[1].answer)
    }

    @Test
    fun parsesFlashcardsWrappedInMarkdownFences() = runTest {
        val inner = "```json\n{\"flashcards\":[{\"question\":\"Q\",\"answer\":\"A\"}]}\n```"

        val cards = generator(200, envelope(inner)).generateFlashcards("notes")

        assertEquals(1, cards.size)
        assertEquals("Q", cards[0].question)
        assertEquals("A", cards[0].answer)
    }

    @Test
    fun dropsCardsWithBlankFields() = runTest {
        val inner =
            """{"flashcards":[{"question":"Good","answer":"A"},{"question":"","answer":"A"},{"question":"Q","answer":"   "}]}"""

        val cards = generator(200, envelope(inner)).generateFlashcards("notes")

        assertEquals(1, cards.size)
        assertEquals("Good", cards[0].question)
    }

    // --- Per-card hint (generated alongside the card) ------------------------------------------
    //
    // The hint must never cost the card: any unusable hint degrades to `null`, which the study
    // screen treats as "not generated yet" and falls back to `generateHint`.

    @Test
    fun parsesTheHintFromTheFlashcardResponse() = runTest {
        val inner =
            """{"flashcards":[{"question":"How do plants make food?","answer":"Photosynthesis","hint":"  Think about sunlight and chlorophyll.  "}]}"""

        val cards = generator(200, envelope(inner)).generateFlashcards("notes")

        assertEquals(1, cards.size)
        assertEquals("Think about sunlight and chlorophyll.", cards[0].hint)
    }

    @Test
    fun aResponseWithoutAHintStillParses() = runTest {
        // The pre-hint response shape must keep working: absent field -> null, not a parse error.
        val inner = """{"flashcards":[{"question":"Capital of France?","answer":"Paris"}]}"""

        val cards = generator(200, envelope(inner)).generateFlashcards("notes")

        assertEquals(1, cards.size)
        assertNull(cards[0].hint)
    }

    @Test
    fun whitespaceOnlyHintBecomesNull() = runTest {
        val inner = """{"flashcards":[{"question":"Q","answer":"Answer","hint":"   "}]}"""

        val cards = generator(200, envelope(inner)).generateFlashcards("notes")

        assertEquals(1, cards.size)
        assertNull("blank hints must normalise to null, never \"\"", cards[0].hint)
    }

    @Test
    fun aHintThatRepeatsTheAnswerIsDroppedWithoutDroppingTheCard() = runTest {
        // Case-insensitive verbatim containment: the hint gives the answer away, so it is discarded
        // while the question/answer survive intact.
        val inner =
            """{"flashcards":[{"question":"Capital of France?","answer":"Paris","hint":"Remember the city: PARIS is on the Seine."}]}"""

        val cards = generator(200, envelope(inner)).generateFlashcards("notes")

        assertEquals("a leak must cost the hint, never the card", 1, cards.size)
        assertEquals("Capital of France?", cards[0].question)
        assertEquals("Paris", cards[0].answer)
        assertNull(cards[0].hint)
    }

    @Test
    fun answersShorterThanThreeCharactersAreNotLeakChecked() = runTest {
        // `42` occurs inside innocent hints; the documented threshold leaves such hints alone.
        val inner =
            """{"flashcards":[{"question":"What is 6 times 7?","answer":"42","hint":"Think about the number 42."}]}"""

        val cards = generator(200, envelope(inner)).generateFlashcards("notes")

        assertEquals(1, cards.size)
        assertEquals("Think about the number 42.", cards[0].hint)
    }

    @Test
    fun capsAnOverlongHintAtAWordBoundary() = runTest {
        // Deterministic fixture: 60 repetitions of "word " = 300 chars.
        val longHint = buildString { repeat(60) { append("word ") } }
        assertTrue("fixture must exceed the 240-char cap", longHint.length > 240)

        val cappedHint = generator(200, envelope(cardWithHint(longHint)))
            .generateFlashcards("notes")
            .single()
            .hint

        assertNotNull("an overlong hint must be capped, not discarded", cappedHint)
        val capped = cappedHint!!
        assertTrue("capped hint must be at most 240 chars, was ${capped.length}", capped.length <= 240)
        assertEquals("capped hint must not end in whitespace", capped, capped.trim())
        // Every surviving token must be a whole "word": a mid-word cut would leave a fragment.
        assertTrue(
            "capped hint must be composed of whole words, was: $capped",
            capped.split(" ").all { it == "word" }
        )

        // In the fixture above character 240 happens to be a space, so even a hard cut would land
        // on a boundary. This fixture puts the cap *inside* a word: only a real last-space cut
        // survives with whole words, and a mid-word fragment would fail the token assertion.
        val crossing = buildString {
            repeat(47) { append("word ") } // 47 full tokens (235 chars) ...
            append("wordwordword") // ... then a word straddling index 240
        }
        assertTrue("fixture must cross the cap mid-word", crossing.length > 240)

        val cappedCrossing = generator(200, envelope(cardWithHint(crossing)))
            .generateFlashcards("notes")
            .single()
            .hint!!

        assertTrue(
            "capped hint must be at most 240 chars, was ${cappedCrossing.length}",
            cappedCrossing.length <= 240
        )
        assertEquals("capped hint must not end in whitespace", cappedCrossing, cappedCrossing.trim())
        assertTrue(
            "cap must cut at a space, not mid-word; was: $cappedCrossing",
            cappedCrossing.split(" ").all { it == "word" }
        )
    }

    /** Wraps [hint] in the minimal flashcard-response JSON the model is asked to return. */
    private fun cardWithHint(hint: String): String =
        """{"flashcards":[{"question":"Q","answer":"clipped","hint":"$hint"}]}"""

    @Test
    fun reportsServerErrorsWithStatusCode() = runTest {
        try {
            generator(500, """{"error":"boom"}""").generateFlashcards("notes")
            fail("Expected AiGenerationException")
        } catch (e: AiGenerationException) {
            assertTrue(e.message!!.contains("500"))
        }
    }

    @Test
    fun reportsBlankApiKeyWithoutCallingTheNetwork() = runTest {
        val gen = AiFlashcardGenerator(
            apiKey = "",
            endpoint = "http://localhost/test",
            client = client(200, envelope("""{"flashcards":[]}"""))
        )

        try {
            gen.generateFlashcards("notes")
            fail("Expected AiGenerationException")
        } catch (e: AiGenerationException) {
            assertTrue(e.message!!.contains("not set up", ignoreCase = true))
        }
    }

    /** Pulls the prompt text back out of a serialised Gemini request body. */
    private fun promptTextOf(requestBody: String): String {
        val parsed = mapAdapter.fromJson(requestBody)!!
        val contents = parsed["contents"] as List<*>
        val parts = (contents[0] as Map<*, *>)["parts"] as List<*>
        return (parts[0] as Map<*, *>)["text"] as String
    }

    @Test
    fun escapesTrickyTextWhenBuildingTheRequest() = runTest {
        val tricky = "He said \"hi\" & left\nline two\\backslash <tag>"
        var captured = ""

        generator(200, envelope("""{"flashcards":[]}""")) { captured = it }
            .generateFlashcards(tricky)

        // If the prompt were interpolated into JSON naively this parse would fail.
        assertTrue(promptTextOf(captured).contains(tricky))
    }

    @Test
    fun usesATopicPromptWhenTheInputIsATopic() = runTest {
        var captured = ""

        generator(200, envelope("""{"flashcards":[]}""")) { captured = it }
            .generateFlashcards("Photosynthesis", isTopic = true)

        val prompt = promptTextOf(captured)
        assertTrue(prompt.contains("Topic or lesson:"))
        assertTrue(prompt.contains("Photosynthesis"))
    }

    @Test
    fun usesADocumentPromptByDefault() = runTest {
        var captured = ""

        generator(200, envelope("""{"flashcards":[]}""")) { captured = it }
            .generateFlashcards("Some long lecture notes")

        val prompt = promptTextOf(captured)
        assertTrue(prompt.contains("Material:"))
        assertTrue(prompt.contains("Some long lecture notes"))
    }

    @Test
    fun flashcardPromptAsksForAHintForEveryCard() = runTest {
        var captured = ""

        generator(200, envelope("""{"flashcards":[]}""")) { captured = it }
            .generateFlashcards("some notes")

        val prompt = promptTextOf(captured)
        // Assert the small stable fragment of the requested JSON shape, not the whole prompt's
        // prose, so a wording change does not fail the test spuriously.
        assertTrue(
            "prompt must request a hint field on each card, was: $prompt",
            prompt.contains("\"answer\":\"...\",\"hint\":\"...\"")
        )
    }

    // --- Hint / evaluation (study screen + tutor) ---------------------------------------------

    @Test
    fun returnsTheModelHintVerbatim() = runTest {
        val hint = generator(200, envelope("  Think about how the two memories differ.  "))
            .generateHint("Difference between RAM and ROM?", "RAM is volatile, ROM is not.")

        assertEquals("Think about how the two memories differ.", hint)
    }

    @Test
    fun hintPromptTellsTheModelNotToRevealTheAnswer() = runTest {
        var captured = ""

        generator(200, envelope("hint")) { captured = it }
            .generateHint("Capital of France?", "Paris")

        val prompt = promptTextOf(captured)
        assertTrue(prompt.contains("Capital of France?"))
        assertTrue(prompt.contains("do NOT state or paraphrase this"))
    }

    @Test
    fun parsesAnEvaluationWithTheModelsScore() = runTest {
        val inner =
            """{"score":85,"correct":true,"note":"Nearly there.","explanation":"RAM is volatile."}"""

        val evaluation = generator(200, envelope(inner))
            .evaluateAnswer("RAM vs ROM?", "RAM is volatile.", "RAM loses data when off.")

        assertEquals(85, evaluation.score)
        assertEquals(true, evaluation.correct)
        assertEquals("Nearly there.", evaluation.note)
        assertEquals("RAM is volatile.", evaluation.explanation)
    }

    @Test
    fun clampsAnOutOfRangeScoreInsteadOfTrustingIt() = runTest {
        val inner = """{"score":150,"correct":true,"note":"n","explanation":"e"}"""

        val evaluation = generator(200, envelope(inner)).evaluateAnswer("q", "a", "answer")

        assertEquals(100, evaluation.score)
    }

    @Test
    fun fallsBackToThePassMarkWhenCorrectIsMissing() = runTest {
        val inner = """{"score":40,"note":"n","explanation":"e"}"""

        val evaluation = generator(200, envelope(inner)).evaluateAnswer("q", "a", "answer")

        assertEquals(false, evaluation.correct)
    }

    @Test
    fun failsInsteadOfInventingAZeroScoreWhenTheModelOmitsTheScore() = runTest {
        // A note on its own is not a grade; showing "0%" would be a fabricated judgement.
        val inner = """{"note":"Looks about right.","explanation":"e"}"""

        try {
            generator(200, envelope(inner)).evaluateAnswer("q", "a", "answer")
            fail("Expected AiGenerationException")
        } catch (e: AiGenerationException) {
            assertTrue(e.message!!.contains("expected format", ignoreCase = true))
        }
    }

    @Test
    fun rejectsABlankStudentAnswerBeforeCallingTheNetwork() = runTest {
        try {
            generator(200, envelope("{}")).evaluateAnswer("q", "a", "   ")
            fail("Expected AiGenerationException")
        } catch (e: AiGenerationException) {
            assertTrue(e.message!!.contains("Write an answer", ignoreCase = true))
        }
    }

    @Test
    fun reportsAnUnreadableEvaluation() = runTest {
        try {
            generator(200, envelope("I think it is fine.")).evaluateAnswer("q", "a", "answer")
            fail("Expected AiGenerationException")
        } catch (e: AiGenerationException) {
            assertTrue(e.message!!.contains("expected format", ignoreCase = true))
        }
    }

    @Test
    fun returnsTheModelExplanation() = runTest {
        val text = generator(200, envelope("  Because RAM forgets.  "))
            .generateExplanation("RAM vs ROM?", "RAM is volatile.", "RAM keeps data")

        assertEquals("Because RAM forgets.", text)
    }

    // --- Thinking level (latency) --------------------------------------------------------------
    //
    // `fastThinkingLevel` is the only place the request body differs from the pre-existing wire
    // format. Hint / grading / explanation opt in; flashcard generation stays on the model default.

    @Test
    fun hintRequestsLowThinkingLevelWhenConfigured() = runTest {
        var captured = ""

        generator(
            code = 200,
            body = envelope("Think about which one survives a power cut."),
            fastThinkingLevel = AiFlashcardGenerator.THINKING_LEVEL_LOW,
            onRequest = { captured = it }
        ).generateHint("RAM vs ROM?", "RAM is volatile, ROM is not.")

        val parsed = mapAdapter.fromJson(captured)!!
        val generationConfig = parsed["generationConfig"] as? Map<*, *>
        assertTrue("Expected generationConfig in $parsed", generationConfig != null)
        val thinkingConfig = generationConfig!!["thinkingConfig"] as? Map<*, *>
        assertTrue("Expected thinkingConfig in $generationConfig", thinkingConfig != null)
        // Assert the decoded value, not the raw JSON: key order/spacing are Moshi's business.
        assertEquals("low", thinkingConfig!!["thinkingLevel"])
    }

    @Test
    fun hintOmitsGenerationConfigWithoutThinkingLevel() = runTest {
        var captured = ""

        // Default generator: `fastThinkingLevel` is null, which is the Gemini 2.x compatibility
        // path — those ids reject `thinkingLevel` outright.
        generator(200, envelope("Think about the capital.")) { captured = it }
            .generateHint("Capital of France?", "Paris")

        val parsed = mapAdapter.fromJson(captured)!!
        assertTrue(
            "Expected no generationConfig key, got $parsed",
            !parsed.containsKey("generationConfig")
        )
    }

    @Test
    fun flashcardGenerationNeverRequestsAThinkingLevel() = runTest {
        var captured = ""

        generator(
            code = 200,
            body = envelope("""{"flashcards":[{"question":"Q","answer":"A"}]}"""),
            fastThinkingLevel = "low",
            onRequest = { captured = it }
        ).generateFlashcards("some notes")

        val parsed = mapAdapter.fromJson(captured)!!
        // Card quality depends on the model's full thinking budget, so this call must never inherit
        // the latency-saving level even when the generator is configured with one.
        assertTrue(
            "Expected no generationConfig key, got $parsed",
            !parsed.containsKey("generationConfig")
        )
    }
}
