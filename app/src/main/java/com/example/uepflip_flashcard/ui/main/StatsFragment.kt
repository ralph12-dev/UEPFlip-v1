package com.example.uepflip_flashcard.ui.main

import android.os.Bundle
import android.view.View
import android.widget.TextView
import androidx.fragment.app.Fragment
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
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

    private val barIds = intArrayOf(
        R.id.bar_0, R.id.bar_1, R.id.bar_2, R.id.bar_3, R.id.bar_4, R.id.bar_5, R.id.bar_6
    )

    private lateinit var deckAdapter: StatsDeckAdapter

    override fun onViewCreated(v: View, savedInstanceState: Bundle?) {
        super.onViewCreated(v, savedInstanceState)
        val main = requireActivity() as MainActivity

        v.findViewById<View>(R.id.btn_profile).setOnClickListener { main.goProfile() }

        deckAdapter = StatsDeckAdapter { deck -> main.goStudy(deck.id) }
        v.findViewById<RecyclerView>(R.id.stats_decks).apply {
            layoutManager = LinearLayoutManager(requireContext())
            adapter = deckAdapter
        }

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
        v.findViewById<TextView>(R.id.stat_recall).text = when {
            loading -> placeholder
            state.recallRatePercent != null -> getString(R.string.stat_percent, state.recallRatePercent)
            else -> placeholder
        }
        v.findViewById<TextView>(R.id.stat_reviews_week).text =
            if (loading) placeholder else state.reviewsLast7Days.toString()
        v.findViewById<TextView>(R.id.stat_streak).text =
            if (loading) placeholder else state.streakDays.toString()

        renderWeekChart(v, state)
        deckAdapter.submitList(state.decks)

        v.findViewById<TextView>(R.id.stat_empty).text = if (state.loadFailed) {
            getString(R.string.data_load_error)
        } else {
            getString(R.string.stats_empty)
        }
        v.findViewById<View>(R.id.stat_empty).visibility =
            if (!loading && (state.loadFailed || state.totalReviews == 0)) View.VISIBLE else View.GONE
    }

    /** Bar height = that day's accuracy; days without reviews keep a short neutral stub. Today is dark. */
    private fun renderWeekChart(v: View, state: StatsUiState) {
        val density = resources.displayMetrics.density
        val maxPx = 100 * density
        val minPx = (4 * density).toInt()
        val dayNames = resources.getStringArray(R.array.stats_day_names)
        barIds.forEachIndexed { i, id ->
            val bar = v.findViewById<View>(id)
            val accuracy = state.weekAccuracy.getOrNull(i).takeUnless { state.isLoading }
            val barHeight = if (accuracy == null) minPx else maxOf(minPx, (accuracy * maxPx / 100).toInt())
            bar.layoutParams = bar.layoutParams.apply { this.height = barHeight }
            bar.setBackgroundResource(
                when {
                    !state.isLoading && i == state.todayIndex -> R.drawable.bg_bar_today
                    accuracy != null -> R.drawable.bg_bar_default
                    else -> R.drawable.bg_bar_empty
                }
            )
            bar.contentDescription = if (accuracy == null) {
                getString(R.string.stats_bar_none, dayNames[i])
            } else {
                getString(R.string.stats_bar_desc, dayNames[i], getString(R.string.stat_percent, accuracy))
            }
        }
    }
}
