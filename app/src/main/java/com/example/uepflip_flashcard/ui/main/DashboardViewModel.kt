package com.example.uepflip_flashcard.ui.main

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.example.uepflip_flashcard.data.model.Deck
import com.example.uepflip_flashcard.data.repository.FlashcardRepository
import com.example.uepflip_flashcard.domain.DeckStats
import com.example.uepflip_flashcard.ui.SimpleViewModelFactory
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn

data class DashboardState(
    val isLoading: Boolean = true,
    val decks: List<Deck> = emptyList(),
    val dueCardCount: Int = 0,
    val dueDeckCount: Int = 0,
    val firstDueDeckId: Long? = null,
    val loadFailed: Boolean = false
)

/**
 * Read-only projection of persisted decks/cards for the Home and My Decks screens. Deck counts and
 * mastery are computed from real card scheduling state (see [DeckStats]).
 */
class DashboardViewModel(private val repository: FlashcardRepository) : ViewModel() {

    val state: StateFlow<DashboardState> = combine(
        repository.getAllDecks(),
        repository.getAllCards()
    ) { decks, cards ->
        val byDeck = cards.groupBy { it.deckId }
        val uiDecks = decks.map { deck ->
            DeckStats.toUiDeck(deck.id, deck.title, byDeck[deck.id].orEmpty())
        }
        val dueCards = cards.filter { DeckStats.isDue(it) }
        val dueDeckIds = dueCards.map { it.deckId }.toSet()
        DashboardState(
            isLoading = false,
            decks = uiDecks,
            dueCardCount = dueCards.size,
            dueDeckCount = dueDeckIds.size,
            firstDueDeckId = uiDecks.firstOrNull { it.id in dueDeckIds }?.id
        )
    }.catch {
        // A Room failure must not strand the screen in its loading visual (or crash the app);
        // surface it so the UI can tell the user instead of showing an empty list forever.
        emit(DashboardState(isLoading = false, loadFailed = true))
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = DashboardState()
    )

    suspend fun createDeck(title: String, description: String = ""): Long =
        repository.createDeck(title, description)

    suspend fun renameDeck(deckId: Long, newTitle: String) = repository.renameDeck(deckId, newTitle)

    suspend fun deleteDeck(deckId: Long) = repository.deleteDeckById(deckId)

    companion object {
        fun factory(repository: FlashcardRepository): ViewModelProvider.Factory =
            SimpleViewModelFactory { DashboardViewModel(repository) }
    }
}
