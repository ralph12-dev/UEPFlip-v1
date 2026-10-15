package com.example.uepflip_flashcard.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider

/**
 * Minimal [ViewModelProvider.Factory] that defers construction to [creator]. Used instead of the
 * fragment-ktx `by viewModels` delegate, which is not on the classpath.
 */
class SimpleViewModelFactory<VM : ViewModel>(private val creator: () -> VM) : ViewModelProvider.Factory {

    @Suppress("UNCHECKED_CAST")
    override fun <T : ViewModel> create(modelClass: Class<T>): T = creator() as T
}
