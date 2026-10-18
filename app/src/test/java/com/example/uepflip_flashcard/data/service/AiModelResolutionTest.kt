package com.example.uepflip_flashcard.data.service

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Pins the Gemini **model-resolution** contract in [AiFlashcardGenerator]: which configured ids are
 * accepted, what a blank or malformed value falls back to, and the exact URL that results.
 *
 * Why this file exists: every AI feature once answered `404` because the model id was hardcoded to
 * `gemini-2.0-flash`, which Google retired on 2026-06-01. The id now comes from Firebase Remote
 * Config (`gemini_model`), so the failure mode changed shape — a bad override is silently replaced
 * by the default instead of exploding, and a console value has to survive being pasted in the form
 * the Gemini `ListModels` API prints it (`models/gemini-…`). Both are contracts worth pinning.
 *
 * Scope, deliberately: this is **regression insurance, not a retirement detector**. A JVM test
 * cannot know which model ids Google currently serves — the original `404` was invisible to the
 * suite precisely because "the id is well formed" and "the id still exists" are different
 * questions, and only a live call answers the second. Nothing below asserts that any particular
 * model is or is not served. A retirement is caught by the log line in `AiGeneratorProvider` and
 * fixed in the Remote Config console, not here.
 *
 * Pure and hermetic: no network, no device, no Remote Config, no sleeps, no ordering assumptions.
 * Every function under test is a pure string function on the generator's companion object, so no
 * [AiFlashcardGenerator] instance is ever constructed.
 */
class AiModelResolutionTest {

    private val defaultModel = AiFlashcardGenerator.DEFAULT_MODEL

    /** Ids the resolver must accept unchanged (modulo trimming / `models/` stripping). */
    private val validInputs = listOf(
        "gemini-3.6-flash",
        "models/gemini-3.6-flash",
        "  gemini-3.6-flash  ",
        "gemini_2.5-pro",
        "x".repeat(64)
    )

    /** Ids the resolver must reject and replace with [AiFlashcardGenerator.DEFAULT_MODEL]. */
    private val invalidInputs = listOf(
        "bad/model",
        "a b",
        "gem ini",
        "x".repeat(65),
        ".hidden",
        "-x"
    )

    private val everyInput: List<String?> = listOf<String?>(null) + validInputs + invalidInputs

    // --- Fallback: blank or absent override ----------------------------------------------------

    @Test
    fun nullModelFallsBackToTheDefault() {
        assertEquals(defaultModel, AiFlashcardGenerator.resolveModel(null))
        assertFalse(AiFlashcardGenerator.isValidModelId(null))
    }

    @Test
    fun emptyModelFallsBackToTheDefault() {
        assertEquals(defaultModel, AiFlashcardGenerator.resolveModel(""))
        assertFalse(AiFlashcardGenerator.isValidModelId(""))
    }

    @Test
    fun blankModelFallsBackToTheDefault() {
        // An operator who saves "   " in Remote Config gets the default, not a blank model and not
        // a malformed URL.
        assertEquals(defaultModel, AiFlashcardGenerator.resolveModel("   "))
        assertFalse(AiFlashcardGenerator.isValidModelId("   "))
    }

    @Test
    fun theFallbackModelIdIsItselfUsable() {
        // If the default did not satisfy the pattern, resolveModel could return a value the rest of
        // the pipeline would reject — and endpointFor's fallback would be self-inconsistent.
        assertTrue(AiFlashcardGenerator.isValidModelId(defaultModel))
        assertTrue(defaultModel.isNotBlank())
    }

    // --- Accepted values ----------------------------------------------------------------------

    @Test
    fun acceptsAPlainModelIdUnchanged() {
        assertEquals("gemini-3.6-flash", AiFlashcardGenerator.resolveModel("gemini-3.6-flash"))
        assertTrue(AiFlashcardGenerator.isValidModelId("gemini-3.6-flash"))
    }

    @Test
    fun acceptsAndStripsTheListModelsPrefix() {
        // `models/gemini-…` is the exact string the Gemini ListModels API prints, so it is the value
        // an operator will paste into Remote Config verbatim. Accepting it must not produce
        // `models/models/…` in the request path.
        assertEquals("gemini-3.6-flash", AiFlashcardGenerator.resolveModel("models/gemini-3.6-flash"))
        assertTrue(AiFlashcardGenerator.isValidModelId("models/gemini-3.6-flash"))
    }

    @Test
    fun acceptsAndTrimsSurroundingWhitespace() {
        // The override is usually pasted from a browser or console field and arrives padded.
        assertEquals("gemini-3.6-flash", AiFlashcardGenerator.resolveModel("  gemini-3.6-flash  "))
        assertEquals("gemini-3.6-flash", AiFlashcardGenerator.resolveModel("\tgemini-3.6-flash\n"))
    }

    @Test
    fun acceptsTheFullAllowedCharacterSet() {
        // Letters, digits, `.`, `-` and `_` are the characters a Gemini id is made of.
        assertEquals("gemini_2.5-pro", AiFlashcardGenerator.resolveModel("gemini_2.5-pro"))
        assertTrue(AiFlashcardGenerator.isValidModelId("gemini_2.5-pro"))
    }

    @Test
    fun acceptsAModelIdOfExactlyTheMaximumLength() {
        val maxLength = "x".repeat(64)
        assertEquals(maxLength, AiFlashcardGenerator.resolveModel(maxLength))
        assertTrue(AiFlashcardGenerator.isValidModelId(maxLength))
    }

    // --- Rejected values ----------------------------------------------------------------------

    @Test
    fun rejectsAModelIdOneCharacterOverTheLimit() {
        val tooLong = "x".repeat(65)
        assertEquals(defaultModel, AiFlashcardGenerator.resolveModel(tooLong))
        assertFalse(AiFlashcardGenerator.isValidModelId(tooLong))
    }

    @Test
    fun rejectsValuesThatWouldEscapeTheUrlPath() {
        // These are the characters that could otherwise redirect the request or forge a query
        // string; the pattern must reject them rather than sanitise them into a different URL.
        listOf(
            "bad/model",
            "https://evil.example/v1beta/models/x",
            "gemini?x=1",
            "gemini#fragment",
            "gemini:generateContent",
            "gemini@evil.example"
        ).forEach { input ->
            assertEquals(
                "'$input' must fall back to the default model",
                defaultModel,
                AiFlashcardGenerator.resolveModel(input)
            )
            assertFalse(
                "'$input' must not be reported as a usable model id",
                AiFlashcardGenerator.isValidModelId(input)
            )
        }
    }

    @Test
    fun rejectsModelIdsContainingWhitespace() {
        listOf("a b", "gem ini", "gemini flash latest").forEach { input ->
            assertEquals(
                "'$input' must fall back to the default model",
                defaultModel,
                AiFlashcardGenerator.resolveModel(input)
            )
            assertFalse(
                "'$input' must not be reported as a usable model id",
                AiFlashcardGenerator.isValidModelId(input)
            )
        }
    }

    @Test
    fun rejectsAnIdStartingWithANonAlphanumericCharacter() {
        // A leading `.`/`-`/`_` is where a path traversal or a relative URL would try to start.
        listOf(".hidden", "-x", "_underscore", "/absolute").forEach { input ->
            assertEquals(
                "'$input' must fall back to the default model",
                defaultModel,
                AiFlashcardGenerator.resolveModel(input)
            )
            assertFalse(
                "'$input' must not be reported as a usable model id",
                AiFlashcardGenerator.isValidModelId(input)
            )
        }
    }

    // --- The two functions must not drift ------------------------------------------------------
    //
    // AiGeneratorProvider calls both: it resolves the configured value, then branches its log line
    // on isValidModelId(). If the two ever disagreed it would either log a model it is not using or
    // use a model it just called unusable — the exact dishonesty that made the original 404 hard to
    // diagnose.

    @Test
    fun isValidModelIdAndResolveModelAgreeOnEveryCase() {
        everyInput.forEach { input ->
            val resolved = AiFlashcardGenerator.resolveModel(input)
            if (AiFlashcardGenerator.isValidModelId(input)) {
                // Accepted ⟹ the configured value is the one actually used, and it is usable.
                // None of [validInputs] is the default id itself, so "accepted" here always means
                // "the configured value was used", never "it happened to equal the fallback".
                assertTrue(
                    "'$input' was accepted, so the resolved id must also be usable",
                    AiFlashcardGenerator.isValidModelId(resolved)
                )
                assertTrue(
                    "'$input' was accepted, so resolving it must not silently fall back",
                    resolved != defaultModel
                )
            } else {
                assertEquals(
                    "'$input' was rejected, so resolving it must yield the default",
                    defaultModel,
                    resolved
                )
            }
        }
    }

    @Test
    fun resolveModelIsIdempotent() {
        // AiGeneratorProvider resolves once and passes the result to endpointFor(); resolving an
        // already-resolved id must change nothing, or the endpoint and the log line would disagree.
        everyInput.forEach { input ->
            val once = AiFlashcardGenerator.resolveModel(input)
            assertEquals(
                "resolveModel must be idempotent for '$input'",
                once,
                AiFlashcardGenerator.resolveModel(once)
            )
        }
    }

    // --- Endpoint construction -----------------------------------------------------------------

    @Test
    fun theDefaultEndpointIsThePinnedUrl() {
        // Spelled out in full on purpose: this fails if anyone changes the host, the API version,
        // the `models` path segment or the `:generateContent` verb.
        //
        // The model id is spelled out too, which means retiring `gemini-flash-latest` requires
        // editing this literal alongside DEFAULT_MODEL. That is deliberate — a default model is a
        // contract, not an implementation detail, and changing it should be a visible edit. It
        // asserts nothing about whether Google currently serves that id (see the class comment).
        val expected =
            "https://generativelanguage.googleapis.com/v1beta/models/gemini-flash-latest:generateContent"

        assertEquals(expected, AiFlashcardGenerator.DEFAULT_ENDPOINT)
        assertEquals(expected, AiFlashcardGenerator.endpointFor(defaultModel))
    }

    @Test
    fun aBlankModelProducesTheSameEndpointAsTheDefault() {
        assertEquals(
            AiFlashcardGenerator.endpointFor(defaultModel),
            AiFlashcardGenerator.endpointFor("")
        )
        assertEquals(AiFlashcardGenerator.DEFAULT_ENDPOINT, AiFlashcardGenerator.endpointFor("  "))
        assertEquals(AiFlashcardGenerator.DEFAULT_ENDPOINT, AiFlashcardGenerator.endpointFor(null))
    }

    @Test
    fun anAcceptedModelProducesTheExpectedEndpoint() {
        val expected =
            "https://generativelanguage.googleapis.com/v1beta/models/gemini-3.6-flash:generateContent"

        assertEquals(expected, AiFlashcardGenerator.endpointFor("gemini-3.6-flash"))
        assertEquals(expected, AiFlashcardGenerator.endpointFor("  gemini-3.6-flash  "))
    }

    @Test
    fun aPrefixedModelDoesNotDoubleTheModelsPathSegment() {
        val url = AiFlashcardGenerator.endpointFor("models/gemini-3.6-flash")

        assertEquals(
            "https://generativelanguage.googleapis.com/v1beta/models/gemini-3.6-flash:generateContent",
            url
        )
        assertEquals("the path must contain exactly one 'models/' segment", 1, countOccurrences(url, "models/"))
    }

    @Test
    fun rejectedModelsNeverProduceAnotherHost() {
        // A malformed override must not be able to point the app at a different server, and must not
        // leave a stray `models/` behind from the value it was given.
        val root = "https://generativelanguage.googleapis.com/v1beta/models/"
        val blankInputs: List<String?> = listOf(null, "", "   ")

        (blankInputs + invalidInputs).forEach { input ->
            val url = AiFlashcardGenerator.endpointFor(input)
            assertEquals(
                "'$input' must produce the default endpoint",
                AiFlashcardGenerator.DEFAULT_ENDPOINT,
                url
            )
            assertTrue("'$input' must stay on the Gemini host", url.startsWith(root))
            assertEquals(
                "'$input' must not add a second 'models/' segment",
                1,
                countOccurrences(url, "models/")
            )
        }
    }

    private fun countOccurrences(haystack: String, needle: String): Int =
        haystack.split(needle).size - 1
}
