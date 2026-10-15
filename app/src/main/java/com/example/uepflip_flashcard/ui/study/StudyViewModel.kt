package com.example.uepflip_flashcard.ui.study

import android.app.Application
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.uepflip_flashcard.R
import com.example.uepflip_flashcard.data.local.entities.CardEntity
import com.example.uepflip_flashcard.data.repository.FlashcardRepository
import com.example.uepflip_flashcard.data.service.AiFlashcardGenerator
import com.example.uepflip_flashcard.data.service.AiGeneratorProvider
import com.example.uepflip_flashcard.domain.scheduler.Sm2Scheduler
import com.example.uepflip_flashcard.ui.SimpleViewModelFactory
import com.google.firebase.remoteconfig.FirebaseRemoteConfig
import com.google.firebase.remoteconfig.FirebaseRemoteConfigSettings
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class StudyUiState(
    val isLoading: Boolean = true,
    val cards: List<CardEntity> = emptyList(),
    val currentIndex: Int = 0,
    val isAnswerRevealed: Boolean = false,
    val selectedRating: Int = Sm2Scheduler.RATING_MEDIUM,
    val isSessionCompleted: Boolean = false,
    val reviewedCount: Int = 0,
    val isSubmitting: Boolean = false,
    val hint: String? = null,
    val isHintLoading: Boolean = false,
    val errorMessage: String? = null,
    /** True when the deck itself has no cards, as opposed to none being due right now. */
    val deckIsEmpty: Boolean = false
) {
    val currentCard: CardEntity?
        get() = cards.getOrNull(currentIndex)

    val totalCards: Int
        get() = cards.size
}

/**
 * Drives a single study session for [deckId]. The ViewModel is the single source of truth for the
 * session; the UI only observes [uiState] and forwards user intent. Each rating is persisted before
 * the session advances, and repeated taps are ignored while a submission is in flight.
 *
 * Hints are requested from the AI on demand; a failure surfaces through [StudyUiState.errorMessage]
 * instead of inventing a hint.
 */
class StudyViewModel(
    private val deckId: Long,
    private val repository: FlashcardRepository,
    private val application: Application
) : ViewModel() {

    private val remoteConfig = FirebaseRemoteConfig.getInstance()
    private var generator: AiFlashcardGenerator? = null

    private val _uiState = MutableStateFlow(StudyUiState())
    val uiState: StateFlow<StudyUiState> = _uiState.asStateFlow()

    init {
        remoteConfig.setConfigSettingsAsync(
            FirebaseRemoteConfigSettings.Builder()
                .setMinimumFetchIntervalInSeconds(0)
                .build()
        )
        remoteConfig.setDefaultsAsync(mapOf(AiGeneratorProvider.API_KEY_CONFIG_KEY to ""))
        loadSessionCards()
    }

    private fun loadSessionCards() {
        viewModelScope.launch {
            try {
                val dueCards = repository.getDueCardsForDeck(deckId)
                _uiState.value = StudyUiState(
                    isLoading = false,
                    cards = dueCards,
                    isSessionCompleted = dueCards.isEmpty(),
                    // A deck with no cards at all is a different message from "nothing due yet".
                    deckIsEmpty = dueCards.isEmpty() &&
                        repository.getCardCountForDeck(deckId) == 0
                )
            } catch (e: Exception) {
                _uiState.value = StudyUiState(
                    isLoading = false,
                    isSessionCompleted = true,
                    errorMessage = e.message ?: "Could not load cards"
                )
            }
        }
    }

    /**
     * Asks the AI for a nudge on the current card. The hint is not persisted: it belongs to this
     * viewing of the card and is cleared as soon as the session moves on.
     */
    fun requestHint() {
        val state = _uiState.value
        if (state.isHintLoading) return
        val card = state.currentCard ?: return

        _uiState.update { it.copy(isHintLoading = true) }

        viewModelScope.launch {
            val hint = try {
                ensureGenerator().generateHint(card.question, card.answer)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                _uiState.update { current ->
                    // The session may have moved on while the request was in flight. A hint (or an
                    // error) that belongs to a card no longer on screen is dropped.
                    if (current.currentCard?.id != card.id) return@update current
                    current.copy(
                        isHintLoading = false,
                        errorMessage = application.getString(
                            R.string.study_hint_error,
                            e.message ?: application.getString(R.string.study_hint_failed)
                        )
                    )
                }
                return@launch
            }
            _uiState.update { current ->
                // Never show a hint for one question against a different card.
                if (current.currentCard?.id != card.id) return@update current
                current.copy(hint = hint, isHintLoading = false)
            }
        }
    }

    private suspend fun ensureGenerator(): AiFlashcardGenerator {
        generator?.let { return it }
        return AiGeneratorProvider.create(remoteConfig).also { generator = it }
    }

    /** Records the rating picked in the difficulty segment; nothing is persisted until submit. */
    fun selectRating(rating: Int) {
        if (_uiState.value.isSubmitting) return
        _uiState.update { it.copy(selectedRating = rating) }
    }

    fun revealAnswer() {
        _uiState.update { it.copy(isAnswerRevealed = true) }
    }

    fun hideAnswer() {
        _uiState.update { it.copy(isAnswerRevealed = false) }
    }

    /**
     * Persists the selected rating (1 = Hard, 2 = Medium, 3 = Easy) for the current card and advances
     * the session. Refused until the answer is on screen, so a card can never be rated blind, and
     * while a previous submission is still running.
     */
    fun submitRating() {
        val state = _uiState.value
        if (state.isSubmitting || !state.isAnswerRevealed) return
        val card = state.currentCard ?: return

        _uiState.update { it.copy(isSubmitting = true) }

        viewModelScope.launch {
            val error = try {
                repository.processCardReview(card.id, state.selectedRating)
                null
            } catch (e: Exception) {
                e.message ?: "Failed to save review"
            }
            advance(error)
        }
    }

    private fun advance(errorMessage: String?) {
        _uiState.update { state ->
            if (errorMessage != null) {
                // Persisting the review failed: stay on this card so the user can retry, and let the
                // UI surface the error instead of silently skipping a card that was never saved.
                state.copy(isSubmitting = false, errorMessage = errorMessage)
            } else {
                val nextIndex = state.currentIndex + 1
                val completed = nextIndex >= state.cards.size
                state.copy(
                    currentIndex = if (completed) state.cards.lastIndex.coerceAtLeast(0) else nextIndex,
                    isAnswerRevealed = false,
                    selectedRating = Sm2Scheduler.RATING_MEDIUM,
                    isSubmitting = false,
                    hint = null,
                    isHintLoading = false,
                    reviewedCount = state.reviewedCount + 1,
                    isSessionCompleted = completed
                )
            }
        }
    }

    fun consumeError() {
        _uiState.update { it.copy(errorMessage = null) }
    }

    companion object {
        fun factory(
            deckId: Long,
            repository: FlashcardRepository,
            application: Application
        ): androidx.lifecycle.ViewModelProvider.Factory =
            SimpleViewModelFactory { StudyViewModel(deckId, repository, application) }
    }
}
