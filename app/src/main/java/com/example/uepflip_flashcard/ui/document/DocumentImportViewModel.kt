package com.example.uepflip_flashcard.ui.document

import android.app.Application
import android.net.Uri
import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.room.withTransaction
import com.example.uepflip_flashcard.R
import com.example.uepflip_flashcard.data.local.AppDatabase
import com.example.uepflip_flashcard.data.local.entities.CardEntity
import com.example.uepflip_flashcard.data.local.entities.DocumentEntity
import com.example.uepflip_flashcard.data.service.AiFlashcardGenerator
import com.example.uepflip_flashcard.data.service.AiGenerationException
import com.example.uepflip_flashcard.data.service.AiGeneratorProvider
import com.example.uepflip_flashcard.data.service.PdfExtractionException
import com.example.uepflip_flashcard.data.service.PdfTextExtractor
import com.google.firebase.remoteconfig.FirebaseRemoteConfig
import com.google.firebase.remoteconfig.FirebaseRemoteConfigSettings
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Drives AI flashcard creation for the Create screen. Two sources feed the same pipeline:
 * an uploaded PDF (extract text first) and a typed topic/lesson. In both cases the generated cards
 * are saved into the chosen deck inside a transaction, and every step reports [UiState] so the UI
 * can show progress and recover from failures.
 *
 * The Gemini API key is read at runtime from Firebase Remote Config (`gemini_api_key`); it is never
 * stored in the app bundle or logged.
 */
class DocumentImportViewModel(private val application: Application) : ViewModel() {

    companion object {
        private const val TAG = "DocumentImportViewModel"
    }

    private val _uiState = MutableStateFlow<UiState>(UiState.Idle)
    val uiState: StateFlow<UiState> = _uiState.asStateFlow()

    private val pdfTextExtractor = PdfTextExtractor(application)
    private val database = AppDatabase.getDatabase(application)
    private val remoteConfig = FirebaseRemoteConfig.getInstance()

    private var aiFlashcardGenerator: AiFlashcardGenerator? = null

    init {
        remoteConfig.setConfigSettingsAsync(
            FirebaseRemoteConfigSettings.Builder()
                .setMinimumFetchIntervalInSeconds(0) // Developer mode: always fetch when asked
                .build()
        )
        remoteConfig.setDefaultsAsync(mapOf(AiGeneratorProvider.API_KEY_CONFIG_KEY to ""))
    }

    /** Resets state so a completed/failed creation does not replay when the screen is revisited. */
    fun resetState() {
        _uiState.value = UiState.Idle
    }

    /** Extracts text from [pdfUri], generates flashcards from it and saves them into [deckId]. */
    fun importDocument(pdfUri: Uri, deckId: Long) {
        if (deckId <= 0L) {
            _uiState.value = UiState.Error(application.getString(R.string.create_error_no_deck))
            return
        }

        viewModelScope.launch {
            var documentId: Long? = null
            try {
                _uiState.value = UiState.Loading(application.getString(R.string.create_loading_extracting))
                documentId = database.documentDao().insertDocument(
                    DocumentEntity(deckId = deckId, uriString = pdfUri.toString())
                )

                val extractedText = withContext(Dispatchers.IO) {
                    pdfTextExtractor.extractText(pdfUri)
                }
                if (extractedText.isBlank()) {
                    throw AiGenerationException(
                        application.getString(R.string.create_error_empty_pdf)
                    )
                }

                database.documentDao().updateDocument(
                    DocumentEntity(
                        id = documentId,
                        deckId = deckId,
                        uriString = pdfUri.toString(),
                        extractedText = extractedText
                    )
                )

                generateAndSave(extractedText, isTopic = false, deckId = deckId)
            } catch (e: CancellationException) {
                throw e
            } catch (e: PdfExtractionException) {
                // Tell the user the real reason rather than always blaming scanned images.
                deleteDocument(documentId)
                _uiState.value = UiState.Error(
                    application.getString(
                        when (e.reason) {
                            PdfExtractionException.Reason.TOO_LARGE ->
                                R.string.create_error_pdf_too_large
                            PdfExtractionException.Reason.UNREADABLE ->
                                R.string.create_error_pdf_unreadable
                        }
                    )
                )
            } catch (e: AiGenerationException) {
                deleteDocument(documentId)
                _uiState.value = UiState.Error(
                    e.message ?: application.getString(R.string.create_error_no_cards)
                )
            } catch (e: Exception) {
                Log.e(TAG, "Unexpected error during PDF import", e)
                deleteDocument(documentId)
                _uiState.value = UiState.Error(
                    application.getString(R.string.create_error_generic, e.message.orEmpty())
                )
            }
        }
    }

    /** Generates flashcards for a typed topic/lesson and saves them into [deckId]. */
    fun generateFromText(text: String, deckId: Long) {
        if (deckId <= 0L) {
            _uiState.value = UiState.Error(application.getString(R.string.create_error_no_deck))
            return
        }
        val topic = text.trim()
        if (topic.isEmpty()) {
            _uiState.value = UiState.Error(application.getString(R.string.create_error_no_input))
            return
        }

        viewModelScope.launch {
            try {
                generateAndSave(topic, isTopic = true, deckId = deckId)
            } catch (e: CancellationException) {
                throw e
            } catch (e: AiGenerationException) {
                _uiState.value = UiState.Error(
                    e.message ?: application.getString(R.string.create_error_no_cards)
                )
            } catch (e: Exception) {
                Log.e(TAG, "Unexpected error during text generation", e)
                _uiState.value = UiState.Error(
                    application.getString(R.string.create_error_generic, e.message.orEmpty())
                )
            }
        }
    }

    /** Shared second half of both sources: ask the AI, then persist the cards atomically. */
    private suspend fun generateAndSave(text: String, isTopic: Boolean, deckId: Long) {
        _uiState.value = UiState.Loading(application.getString(R.string.create_loading_generating))
        val cards = ensureGenerator().generateFlashcards(text, isTopic)
        if (cards.isEmpty()) {
            throw AiGenerationException(application.getString(R.string.create_error_no_cards))
        }

        _uiState.value = UiState.Loading(application.getString(R.string.create_loading_saving))
        database.withTransaction {
            database.cardDao().insertCards(
                cards.map { card ->
                    CardEntity(
                        deckId = deckId,
                        question = card.question.trim(),
                        answer = card.answer.trim()
                    )
                }
            )
        }

        _uiState.value = UiState.Success(cardsImported = cards.size, deckId = deckId)
    }

    /** Fetches the API key on demand and caches a configured generator. */
    private suspend fun ensureGenerator(): AiFlashcardGenerator {
        aiFlashcardGenerator?.let { return it }
        return AiGeneratorProvider.create(remoteConfig).also { aiFlashcardGenerator = it }
    }

    private suspend fun deleteDocument(documentId: Long?) {
        if (documentId == null) return
        try {
            database.documentDao().getDocumentById(documentId)?.let {
                database.documentDao().deleteDocument(it)
            }
        } catch (e: Exception) {
            Log.w(TAG, "Failed to clean up document $documentId", e)
        }
    }

    sealed class UiState {
        object Idle : UiState()
        data class Loading(val message: String) : UiState()
        data class Success(val cardsImported: Int, val deckId: Long) : UiState()
        data class Error(val message: String) : UiState()
    }
}
