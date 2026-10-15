package com.example.uepflip_flashcard.ui.main

import android.os.Bundle
import android.view.View
import android.widget.TextView
import android.widget.Toast
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
import java.util.Calendar

class HomeFragment : Fragment(R.layout.fragment_home), NavDestination {

    private val viewModel: DashboardViewModel by lazy {
        ViewModelProvider(
            this,
            DashboardViewModel.factory(FlashcardRepository.create(requireContext()))
        )[DashboardViewModel::class.java]
    }

    override fun onViewCreated(v: View, savedInstanceState: Bundle?) {
        val main = requireActivity() as MainActivity

        v.findViewById<TextView>(R.id.greeting).text = getString(timeOfDayGreeting())

        v.findViewById<View>(R.id.btn_review).setOnClickListener {
            val deckId = viewModel.state.value.firstDueDeckId
            if (deckId != null) {
                main.goStudy(deckId)
            } else {
                Toast.makeText(requireContext(), R.string.no_cards_due, Toast.LENGTH_SHORT).show()
            }
        }
        v.findViewById<View>(R.id.btn_view_all).setOnClickListener { main.goDecks() }
        v.findViewById<View>(R.id.btn_create).setOnClickListener { main.goCreate() }
        v.findViewById<View>(R.id.card_progress).setOnClickListener { main.goStats() }

        val list = v.findViewById<RecyclerView>(R.id.recent_decks)
        list.layoutManager = LinearLayoutManager(requireContext())
        val adapter = DeckAdapter(
            showStudy = false,
            onStudy = { deck -> main.goStudy(deck.id) }
        )
        list.adapter = adapter

        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.state.collect { state -> render(v, state, adapter) }
            }
        }
    }

    private fun render(v: View, state: DashboardState, adapter: DeckAdapter) {
        adapter.submitList(state.decks.take(3))

        val hasDue = state.dueCardCount > 0
        v.findViewById<View>(R.id.btn_review).visibility = if (hasDue) View.VISIBLE else View.GONE
        v.findViewById<TextView>(R.id.overdue_text).text = when {
            // Avoid flashing "No decks yet" while the first Room emission is still in flight.
            state.isLoading -> ""
            state.loadFailed -> getString(R.string.data_load_error)
            state.decks.isEmpty() -> getString(R.string.home_no_decks)
            !hasDue -> getString(R.string.home_all_caught_up)
            else -> resources.getQuantityString(
                R.plurals.home_overdue,
                state.dueDeckCount,
                state.dueDeckCount
            )
        }
    }

    /** Greets by time of day; there is no account, so no name is invented. */
    private fun timeOfDayGreeting(): Int = when (Calendar.getInstance().get(Calendar.HOUR_OF_DAY)) {
        in 5..11 -> R.string.home_greeting_morning
        in 12..17 -> R.string.home_greeting_afternoon
        else -> R.string.home_greeting_evening
    }
}
