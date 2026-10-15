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
            throw AiGenerationException(
                "AI is not configured. Add the Gemini API key to Firebase Remote Config " +
                    "(key: ${API_KEY_CONFIG_KEY})."
            )
        }

        return AiFlashcardGenerator(apiKey)
    }
}
