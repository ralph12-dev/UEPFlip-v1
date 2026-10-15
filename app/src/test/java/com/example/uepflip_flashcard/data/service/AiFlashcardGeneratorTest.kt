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
        onRequest: ((String) -> Unit)? = null
    ) = AiFlashcardGenerator(
        apiKey = "test-key",
        endpoint = "http://localhost/test",
        client = client(code, body, onRequest)
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
}
