package com.example.uepflip_flashcard.ui.study

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.example.uepflip_flashcard.data.local.entities.CardEntity
import com.example.uepflip_flashcard.data.repository.FlashcardRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

data class StudyUiState(
    val isLoading: Boolean = true,
    val cards: List<CardEntity> = emptyList(),
    val currentIndex: Int = 0,
    val isAnswerRevealed: Boolean = false,
    val isSessionCompleted: Boolean = false,
    val reviewedCount: Int = 0
) {
    val currentCard: CardEntity?
        get() = cards.getOrNull(currentIndex)

    val totalCards: Int
        get() = cards.size
}

class StudyViewModel(
    private val deckId: Long,
    private val repository: FlashcardRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(StudyUiState())
    val uiState: StateFlow<StudyUiState> = _uiState.asStateFlow()

    init {
        loadSessionCards()
    }

    fun loadSessionCards() {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isLoading = true)
            val dueCards = repository.getDueCardsForDeck(deckId)
            _uiState.value = _uiState.value.copy(
                isLoading = false,
                cards = dueCards,
                currentIndex = 0,
                isAnswerRevealed = false,
                isSessionCompleted = dueCards.isEmpty(),
                reviewedCount = 0
            )
        }
    }

    fun revealAnswer() {
        _uiState.value = _uiState.value.copy(isAnswerRevealed = true)
    }

    fun submitRating(rating: Int) {
        val currentCard = _uiState.value.currentCard ?: return

        viewModelScope.launch {
            repository.processCardReview(
                card = currentCard,
                rating = rating
            )

            val nextIndex = _uiState.value.currentIndex + 1
            val isComplete = nextIndex >= _uiState.value.cards.size

            _uiState.value = _uiState.value.copy(
                currentIndex = nextIndex,
                isAnswerRevealed = false,
                isSessionCompleted = isComplete,
                reviewedCount = _uiState.value.reviewedCount + 1
            )
        }
    }
}

class StudyViewModelFactory(
    private val deckId: Long,
    private val repository: FlashcardRepository
) : ViewModelProvider.Factory {
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        if (modelClass.isAssignableFrom(StudyViewModel::class.java)) {
            @Suppress("UNCHECKED_CAST")
            return StudyViewModel(deckId, repository) as T
        }
        throw IllegalArgumentException("Unknown ViewModel class")
    }
}
