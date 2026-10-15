package com.example.uepflip_flashcard.ui.main

import android.os.Bundle
import android.view.View
import android.widget.TextView
import androidx.fragment.app.Fragment
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import com.example.uepflip_flashcard.R
import com.example.uepflip_flashcard.data.repository.FlashcardRepository
import kotlinx.coroutines.launch

class StatsFragment : Fragment(R.layout.fragment_stats), NavDestination {

    private val viewModel: StatsViewModel by lazy {
        ViewModelProvider(
            this,
            StatsViewModel.factory(FlashcardRepository.create(requireContext()))
        )[StatsViewModel::class.java]
    }

    override fun onViewCreated(v: View, savedInstanceState: Bundle?) {
        super.onViewCreated(v, savedInstanceState)

        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.uiState.collect { render(v, it) }
            }
        }
    }

    private fun render(v: View, state: StatsUiState) {
        val loading = state.isLoading
        val placeholder = getString(R.string.stat_no_data)

        // Values are real Room data; while the first emission is in flight we show a dash rather
        // than a misleading 0.
        v.findViewById<TextView>(R.id.stat_decks).text =
            if (loading) placeholder else state.totalDecks.toString()
        v.findViewById<TextView>(R.id.stat_cards).text =
            if (loading) placeholder else state.totalCards.toString()
        v.findViewById<TextView>(R.id.stat_due).text =
            if (loading) placeholder else state.dueNow.toString()
        v.findViewById<TextView>(R.id.stat_mastered).text = if (loading) {
            placeholder
        } else {
            getString(R.string.stat_percent, state.masteryPercent)
        }
        v.findViewById<TextView>(R.id.stat_reviews).text =
            if (loading) placeholder else state.totalReviews.toString()
        v.findViewById<TextView>(R.id.stat_reviews_week).text =
            if (loading) placeholder else state.reviewsLast7Days.toString()
        v.findViewById<TextView>(R.id.stat_recall).text = when {
            loading -> placeholder
            state.recallRatePercent != null -> getString(R.string.stat_percent, state.recallRatePercent)
            else -> getString(R.string.stat_no_data)
        }
        v.findViewById<TextView>(R.id.stat_empty).text = if (state.loadFailed) {
            getString(R.string.data_load_error)
        } else {
            getString(R.string.stats_empty)
        }
        v.findViewById<View>(R.id.stat_empty).visibility =
            if (!loading && (state.loadFailed || state.totalReviews == 0)) View.VISIBLE else View.GONE
    }
}
