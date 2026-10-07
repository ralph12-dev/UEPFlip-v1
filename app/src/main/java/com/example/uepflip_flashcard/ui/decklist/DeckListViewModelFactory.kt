package com.example.uepflip_flashcard.ui.decklist

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import com.example.uepflip_flashcard.data.repository.FlashcardRepository

class DeckListViewModelFactory(
    private val repository: FlashcardRepository
) : ViewModelProvider.Factory {
    @Suppress("UNCHECKED_CAST")
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        if (modelClass.isAssignableFrom(DeckListViewModel::class.java)) {
            return DeckListViewModel(repository) as T
        }
        throw IllegalArgumentException("Unknown ViewModel class")
    }
}