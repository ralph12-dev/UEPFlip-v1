package com.example.uepflip_flashcard.ui.decklist

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.text.InputType
import android.view.View
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.Toast
import androidx.activity.viewModels
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.recyclerview.widget.LinearLayoutManager
import com.example.uepflip_flashcard.R
import com.example.uepflip_flashcard.data.local.AppDatabase
import com.example.uepflip_flashcard.data.repository.FlashcardRepository
import com.example.uepflip_flashcard.databinding.ActivityDeckListBinding
import com.example.uepflip_flashcard.domain.scheduler.Sm2Scheduler
import com.example.uepflip_flashcard.ui.deck.DockListAdapter
import com.example.uepflip_flashcard.ui.study.StudyActivity
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch

class DeckListActivity : AppCompatActivity() {

    private lateinit var binding: ActivityDeckListBinding
    private lateinit var deckAdapter: DockListAdapter

    private val viewModel: DeckListViewModel by viewModels {
        val database = AppDatabase.getDatabase(applicationContext)
        val repository = FlashcardRepository(
            deckDao = database.deckDao(),
            cardDao = database.cardDao(),
            reviewDao = database.reviewDao(),
            scheduler = Sm2Scheduler()
        )
        DeckListViewModelFactory(repository)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityDeckListBinding.inflate(layoutInflater)
        setContentView(binding.root)

        setupRecyclerView()
        setupObservers()
        setupFabClickListener()
    }

    private fun setupRecyclerView() {
        deckAdapter = DockListAdapter { deck ->
            launchStudyActivity(deck.id)
        }
        binding.recyclerDecks.layoutManager = LinearLayoutManager(this)
        binding.recyclerDecks.adapter = deckAdapter
    }

    private fun setupObservers() {
        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                launch {
                    viewModel.decks.collectLatest { decks ->
                        if (decks.isEmpty()) {
                            showEmptyState()
                        } else {
                            hideEmptyState()
                            deckAdapter.submitList(decks)
                        }
                    }
                }
                launch {
                    viewModel.uiState.collectLatest { state ->
                        if (state.creationSuccess) {
                            Toast.makeText(this@DeckListActivity, "Deck created successfully", Toast.LENGTH_SHORT).show()
                        }
                        state.creationError?.let { error ->
                            Toast.makeText(this@DeckListActivity, error, Toast.LENGTH_SHORT).show()
                        }
                    }
                }
            }
        }
    }

    private fun setupFabClickListener() {
        binding.fabAddDeck.setOnClickListener {
            showCreateDeckDialog()
        }
    }

    private fun launchStudyActivity(deckId: Long) {
        val intent = StudyActivity.createIntent(this, deckId)
        startActivity(intent)
    }

    private fun showCreateDeckDialog() {
        val titleInput = EditText(this).apply {
            hint = getString(R.string.deck_title_hint)
            inputType = InputType.TYPE_CLASS_TEXT
        }
        val descInput = EditText(this).apply {
            hint = getString(R.string.deck_description_hint)
            inputType = InputType.TYPE_CLASS_TEXT
        }
        val layout = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(48, 24, 48, 24)
            addView(titleInput)
            addView(descInput)
        }

        AlertDialog.Builder(this)
            .setTitle(getString(R.string.create_new_deck))
            .setView(layout)
            .setPositiveButton(getString(R.string.btn_create_deck)) { _, _ ->
                val title = titleInput.text.toString().trim()
                val description = descInput.text.toString().trim()
                if (title.isNotEmpty()) {
                    viewModel.createDeck(title, if (description.isEmpty()) null else description)
                } else {
                    Toast.makeText(this, "Title cannot be empty", Toast.LENGTH_SHORT).show()
                }
            }
            .setNegativeButton(android.R.string.cancel, null)
            .show()
    }

    private fun showEmptyState() {
        binding.emptyStateCard.visibility = View.VISIBLE
        binding.recyclerDecks.visibility = View.GONE
    }

    private fun hideEmptyState() {
        binding.emptyStateCard.visibility = View.GONE
        binding.recyclerDecks.visibility = View.VISIBLE
    }

    companion object {
        fun newIntent(context: Context): Intent {
            return Intent(context, DeckListActivity::class.java)
        }
    }
}