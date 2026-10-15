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

    suspend fun create(remoteConfig: FirebaseRemoteConfig): AiFlashcardGenerator {
        val apiKey = try {
            remoteConfig.fetchAndActivate().await()
            remoteConfig.getString(API_KEY_CONFIG_KEY)
        } catch (e: Exception) {
            Log.w(TAG, "Remote Config fetch failed; using cached value", e)
            remoteConfig.getString(API_KEY_CONFIG_KEY)
        }

        if (apiKey.isBlank()) {
            // Keep the actionable detail in Logcat for whoever configures the project; users get a
            // plain message instead of Firebase Remote Config instructions.
            Log.w(TAG, "Gemini API key is missing from Remote Config (key: $API_KEY_CONFIG_KEY)")
            throw AiGenerationException(
                "AI features are not set up yet. Please try again later."
            )
        }

        return AiFlashcardGenerator(apiKey)
    }
}
