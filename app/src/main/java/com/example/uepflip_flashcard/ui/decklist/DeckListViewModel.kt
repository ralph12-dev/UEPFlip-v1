package com.example.uepflip_flashcard.ui.decklist

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.uepflip_flashcard.data.local.entities.DeckEntity
import com.example.uepflip_flashcard.data.repository.FlashcardRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.launch

class DeckListViewModel(
    private val repository: FlashcardRepository
) : ViewModel() {

    val decks: Flow<List<DeckEntity>> = repository.getAllDecks()
        .distinctUntilChanged()

    private val _uiState = MutableStateFlow(DeckListUiState())
    val uiState: StateFlow<DeckListUiState> = _uiState.asStateFlow()

    fun refreshDecks() {
        // Flow is automatically updated by Room
        // No need for manual refresh
    }

    fun createDeck(title: String, description: String?) {
        _uiState.value = _uiState.value.copy(isCreating = true)
        viewModelScope.launch {
            try {
                repository.createDeck(title, description ?: "")
                _uiState.value = _uiState.value.copy(isCreating = false, creationSuccess = true)
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(isCreating = false, creationError = e.message)
            }
        }
    }
}

data class DeckListUiState(
    val isCreating: Boolean = false,
    val creationSuccess: Boolean = false,
    val creationError: String? = null
)