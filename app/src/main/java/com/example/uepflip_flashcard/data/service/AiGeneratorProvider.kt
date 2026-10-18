package com.example.uepflip_flashcard.data.service

import android.util.Log
import com.google.firebase.remoteconfig.FirebaseRemoteConfig
import kotlinx.coroutines.tasks.await

/**
 * Builds an [AiFlashcardGenerator] from the Gemini API key stored in Firebase Remote Config.
 *
 * Keeping this in one place means every AI feature fails the same, clear way when the key is not
 * configured, and the key is never bundled into the APK.
 */
object AiGeneratorProvider {

    private const val TAG = "AiGeneratorProvider"

    /** Remote Config key that holds the Gemini API key. Defaults to empty (feature disabled). */
    const val API_KEY_CONFIG_KEY = "gemini_api_key"

    /**
     * Optional Remote Config key holding the Gemini model id (for example `gemini-3.6-flash`).
     * Google retires model ids regularly and a retired id fails with `404`; an override here lets
     * a live value be swapped in from the console without shipping a new APK. When absent or
     * malformed, [AiFlashcardGenerator.DEFAULT_MODEL] is used.
     */
    const val MODEL_CONFIG_KEY = "gemini_model"

    suspend fun create(remoteConfig: FirebaseRemoteConfig): AiFlashcardGenerator {
        val apiKey = (try {
            remoteConfig.fetchAndActivate().await()
            remoteConfig.getString(API_KEY_CONFIG_KEY)
        } catch (e: Exception) {
            Log.w(TAG, "Remote Config fetch failed; using cached value", e)
            remoteConfig.getString(API_KEY_CONFIG_KEY)
        }).trim()

        if (apiKey.isBlank()) {
            // Keep the actionable detail in Logcat for whoever configures the project; users get a
            // plain message instead of Firebase Remote Config instructions.
            Log.w(TAG, "Gemini API key is missing from Remote Config (key: $API_KEY_CONFIG_KEY)")
            throw AiGenerationException(
                "AI features are not set up yet. Please try again later."
            )
        }

        // The model comes from Remote Config so a model retirement is a console change, not a
        // release. resolveModel() substitutes the default for a blank/invalid value, so log the value
        // actually in use, and warn when the configured one was rejected: this log line is the
        // designated diagnostic for the next retirement (D-013), and it must not claim a rejected
        // value is live.
        //
        // Read defensively: unlike API_KEY_CONFIG_KEY, no screen sets a Remote Config default for this
        // key, so the read must not depend on how the SDK behaves for a key that was never declared.
        val configured = try {
            remoteConfig.getString(MODEL_CONFIG_KEY)
        } catch (e: Exception) {
            Log.w(TAG, "'$MODEL_CONFIG_KEY' could not be read from Remote Config; using the default", e)
            ""
        }
        val model = AiFlashcardGenerator.resolveModel(configured)
        when {
            configured.isBlank() ->
                Log.i(TAG, "No '$MODEL_CONFIG_KEY' override; using '$model'")

            AiFlashcardGenerator.isValidModelId(configured) ->
                Log.i(TAG, "Using Gemini model '$model' from Remote Config")

            else ->
                // Never echo the raw value: the same console field is where an operator is most likely
                // to paste the API key by mistake, and this app's contract is that the key never
                // reaches Logcat. The length is enough to spot that something unexpected was pasted.
                Log.w(
                    TAG,
                    "'$MODEL_CONFIG_KEY' was rejected as a model id (${configured.trim().length} chars); using '$model'"
                )
        }

        // The short calls (hint, grading, explanation) ask for the lowest thinking level the model
        // accepts: on Gemini 3.x dynamic thinking is on by default, and the user is waiting on a
        // one-sentence answer. Gemini 2.x rejects `thinkingLevel` (it uses `thinkingBudget`), so for
        // those ids the request stays exactly as it was.
        val fastThinkingLevel =
            if (acceptsThinkingLevel(model)) AiFlashcardGenerator.THINKING_LEVEL_LOW else null

        return AiFlashcardGenerator(
            apiKey,
            AiFlashcardGenerator.endpointFor(model),
            fastThinkingLevel = fastThinkingLevel
        )
    }

    /**
     * Whether [model] accepts `generationConfig.thinkingConfig.thinkingLevel`.
     *
     * Positive matching on purpose: sending the field to a model that does not know it is a request
     * error, so anything unrecognised (including a pinned Gemini 2.x id chosen through Remote
     * Config) keeps the previous, config-free request. The `-latest` aliases always point at the
     * current generation, which is why the default `gemini-flash-latest` qualifies.
     */
    private fun acceptsThinkingLevel(model: String): Boolean =
        model.startsWith("gemini-3") ||
            model.startsWith("gemini-4") ||
            model.endsWith("-latest")
}
