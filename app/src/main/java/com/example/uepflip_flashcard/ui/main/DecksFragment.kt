package com.example.uepflip_flashcard.ui.main

import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.view.View
import android.widget.EditText
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
import com.example.uepflip_flashcard.data.model.Deck
import com.example.uepflip_flashcard.data.repository.FlashcardRepository
import com.google.android.material.chip.ChipGroup
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import kotlinx.coroutines.launch

class DecksFragment : Fragment(R.layout.fragment_decks) {

    private val viewModel: DashboardViewModel by lazy {
        ViewModelProvider(
            this,
            DashboardViewModel.factory(FlashcardRepository.create(requireContext()))
        )[DashboardViewModel::class.java]
    }

    private lateinit var adapter: DeckAdapter
    private lateinit var emptyView: TextView
    private lateinit var search: EditText
    private lateinit var chips: ChipGroup

    private var allDecks: List<Deck> = emptyList()
    private var isLoading = true

    override fun onViewCreated(v: View, savedInstanceState: Bundle?) {
        val main = requireActivity() as MainActivity

        search = v.findViewById(R.id.search)
        chips = v.findViewById(R.id.filters)
        emptyView = v.findViewById(R.id.decks_empty)

        adapter = DeckAdapter(
            showStudy = true,
            onStudy = { deck -> main.goStudy(deck.id) },
            onLongClick = { deck -> showDeckOptions(deck) }
        )
        val list = v.findViewById<RecyclerView>(R.id.deck_list)
        list.layoutManager = LinearLayoutManager(requireContext())
        list.adapter = adapter

        search.addTextChangedListener(object : TextWatcher {
            override fun afterTextChanged(s: Editable?) = applyFilter()
            override fun beforeTextChanged(s: CharSequence?, st: Int, c: Int, a: Int) {}
            override fun onTextChanged(s: CharSequence?, st: Int, b: Int, c: Int) {}
        })
        chips.setOnCheckedStateChangeListener { _, _ -> applyFilter() }

        v.findViewById<View>(R.id.btn_add_deck).setOnClickListener { showCreateDeckDialog() }

        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.state.collect { state ->
                    allDecks = state.decks
                    isLoading = state.isLoading
                    applyFilter()
                }
            }
        }
    }

    private fun applyFilter() {
        val query = search.text.toString().trim().lowercase()
        val filtered = allDecks.filter { deck ->
            deck.name.lowercase().contains(query) && when (chips.checkedChipId) {
                R.id.chip_progress -> deck.mastery < 100
                R.id.chip_done -> deck.mastery >= 100
                else -> true
            }
        }
        adapter.submitList(filtered)

        // A brand-new account and a filter with no hits are different situations, so they get
        // different copy; nothing is shown while the first Room emission is still in flight.
        emptyView.text = if (allDecks.isEmpty()) {
            getString(R.string.decks_empty)
        } else {
            getString(R.string.decks_no_matches)
        }
        emptyView.visibility = if (!isLoading && filtered.isEmpty()) View.VISIBLE else View.GONE
    }

    private fun showCreateDeckDialog() {
        promptForTitle(getString(R.string.create_new_deck), "") { title ->
            viewLifecycleOwner.lifecycleScope.launch { viewModel.createDeck(title) }
        }
    }

    private fun showDeckOptions(deck: Deck) {
        val options = arrayOf(getString(R.string.rename_deck), getString(R.string.delete_deck))
        MaterialAlertDialogBuilder(requireContext())
            .setTitle(deck.name)
            .setItems(options) { _, which ->
                when (which) {
                    0 -> promptForTitle(getString(R.string.rename_deck), deck.name) { title ->
                        viewLifecycleOwner.lifecycleScope.launch {
                            viewModel.renameDeck(deck.id, title)
                        }
                    }
                    1 -> confirmDelete(deck)
                }
            }
            .show()
    }

    private fun confirmDelete(deck: Deck) {
        MaterialAlertDialogBuilder(requireContext())
            .setTitle(getString(R.string.delete_deck))
            .setMessage(getString(R.string.confirm_delete_deck, deck.name))
            .setPositiveButton(R.string.delete) { _, _ ->
                viewLifecycleOwner.lifecycleScope.launch { viewModel.deleteDeck(deck.id) }
            }
            .setNegativeButton(R.string.cancel, null)
            .show()
    }

    private fun promptForTitle(dialogTitle: String, initial: String, onConfirm: (String) -> Unit) {
        val input = EditText(requireContext()).apply {
            hint = getString(R.string.deck_title_hint)
            setText(initial)
            setSelection(text.length)
        }

        MaterialAlertDialogBuilder(requireContext())
            .setTitle(dialogTitle)
            .setView(input)
            .setPositiveButton(R.string.save) { _, _ ->
                val title = input.text.toString().trim()
                if (title.isEmpty()) {
                    Toast.makeText(requireContext(), R.string.deck_title_required, Toast.LENGTH_SHORT).show()
                } else {
                    onConfirm(title)
                }
            }
            .setNegativeButton(android.R.string.cancel, null)
            .show()
    }
}
