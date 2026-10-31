package com.example.uepflip_flashcard.ui.main

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.example.uepflip_flashcard.data.model.Deck
import com.example.uepflip_flashcard.data.repository.FlashcardRepository
import com.example.uepflip_flashcard.domain.DeckStats
import com.example.uepflip_flashcard.domain.StudyActivity
import com.example.uepflip_flashcard.ui.SimpleViewModelFactory
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn

/**
 * Statistics derived entirely from persisted Room data. Nothing here is synthesised or hardcoded;
 * when there is no history the UI is told there is no data rather than being shown a fake 0%.
 */
data class StatsUiState(
    val isLoading: Boolean = true,
    val totalDecks: Int = 0,
    val totalCards: Int = 0,
    val dueNow: Int = 0,
    val matureCards: Int = 0,
    val masteryPercent: Int = 0,
    val totalReviews: Int = 0,
    val reviewsLast7Days: Int = 0,
    val recallRatePercent: Int? = null,
    /** Accuracy per day for the current Monday-Sunday week; null = no reviews that day. */
    val weekAccuracy: List<Int?> = List(7) { null },
    /** 0 = Monday ... 6 = Sunday. */
    val todayIndex: Int = 0,
    val streakDays: Int = 0,
    /** One entry per deck, for the "Progress by deck" list. */
    val decks: List<Deck> = emptyList(),
    val loadFailed: Boolean = false
)

class StatsViewModel(repository: FlashcardRepository) : ViewModel() {

    val uiState: StateFlow<StatsUiState> = combine(
        repository.getAllDecks(),
        repository.getAllCards(),
        repository.getAllReviews()
    ) { decks, cards, reviews ->
        val now = System.currentTimeMillis()
        val byDeck = cards.groupBy { it.deckId }
        StatsUiState(
            isLoading = false,
            totalDecks = decks.size,
            totalCards = cards.size,
            dueNow = cards.count { DeckStats.isDue(it) },
            matureCards = cards.count { DeckStats.isMature(it) },
            masteryPercent = DeckStats.masteryPercent(cards),
            totalReviews = reviews.size,
            reviewsLast7Days = DeckStats.reviewsLastDays(reviews, 7),
            recallRatePercent = DeckStats.recallRatePercent(reviews),
            weekAccuracy = StudyActivity.weekAccuracy(reviews, now),
            todayIndex = StudyActivity.todayIndex(now),
            streakDays = StudyActivity.streakDays(reviews, now),
            decks = decks.map { DeckStats.toUiDeck(it.id, it.title, byDeck[it.id].orEmpty(), now) }
        )
    }.catch {
        // A Room failure must not leave the figures as "—" forever with no explanation.
        emit(StatsUiState(isLoading = false, loadFailed = true))
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = StatsUiState()
    )

    companion object {
        fun factory(repository: FlashcardRepository): ViewModelProvider.Factory =
            SimpleViewModelFactory { StatsViewModel(repository) }
    }
}
