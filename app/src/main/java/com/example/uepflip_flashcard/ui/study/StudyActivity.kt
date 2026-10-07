package com.example.uepflip_flashcard.ui.study

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.view.View
import android.widget.Toast
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import com.example.uepflip_flashcard.data.local.AppDatabase
import com.example.uepflip_flashcard.data.repository.FlashcardRepository
import com.example.uepflip_flashcard.databinding.ActivityStudyBinding
import com.example.uepflip_flashcard.domain.scheduler.Sm2Scheduler
import kotlinx.coroutines.launch

class StudyActivity : AppCompatActivity() {

    private lateinit var binding: ActivityStudyBinding

    private val viewModel: StudyViewModel by viewModels {
        val deckId = intent.getLongExtra(EXTRA_DECK_ID, -1L)
        val database = AppDatabase.getDatabase(applicationContext)
        val repository = FlashcardRepository(
            deckDao = database.deckDao(),
            cardDao = database.cardDao(),
            reviewDao = database.reviewDao(),
            scheduler = Sm2Scheduler()
        )
        StudyViewModelFactory(deckId, repository)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityStudyBinding.inflate(layoutInflater)
        setContentView(binding.root)

        val deckId = intent.getLongExtra(EXTRA_DECK_ID, -1L)
        if (deckId == -1L) {
            Toast.makeText(this, "Deck not found", Toast.LENGTH_SHORT).show()
            finish()
            return
        }

        setupClickListeners()
        observeState()
    }

    private fun setupClickListeners() {
        binding.btnReveal.setOnClickListener {
            viewModel.revealAnswer()
        }

        binding.btnHard.setOnClickListener {
            viewModel.submitRating(RATING_HARD)
        }

        binding.btnMedium.setOnClickListener {
            viewModel.submitRating(RATING_MEDIUM)
        }

        binding.btnEasy.setOnClickListener {
            viewModel.submitRating(RATING_EASY)
        }

        binding.btnFinish.setOnClickListener {
            finish()
        }
    }

    private fun observeState() {
        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.uiState.collect { state ->
                    renderUiState(state)
                }
            }
        }
    }

    private fun renderUiState(state: StudyUiState) {
        if (state.isLoading) {
            binding.loadingIndicator.visibility = View.VISIBLE
            binding.cardContainer.visibility = View.GONE
            binding.btnReveal.visibility = View.GONE
            binding.layoutRatings.visibility = View.GONE
            binding.layoutCompletion.visibility = View.GONE
            return
        }

        binding.loadingIndicator.visibility = View.GONE

        if (state.isSessionCompleted) {
            binding.cardContainer.visibility = View.GONE
            binding.btnReveal.visibility = View.GONE
            binding.layoutRatings.visibility = View.GONE
            binding.layoutCompletion.visibility = View.VISIBLE
            binding.tvCompletionSummary.text = "You studied ${state.reviewedCount} cards."
            binding.progressIndicator.progress = 100
            binding.tvProgressCounter.text = "Completed"
            return
        }

        state.currentCard?.let { card ->
            binding.cardContainer.visibility = View.VISIBLE
            binding.layoutCompletion.visibility = View.GONE

            binding.tvQuestion.text = card.question
            binding.tvAnswer.text = card.answer

            if (!card.category.isNullOrBlank()) {
                binding.tvCategory.visibility = View.VISIBLE
                binding.tvCategory.text = card.category
            } else {
                binding.tvCategory.visibility = View.GONE
            }

            val currentNum = state.currentIndex + 1
            val total = state.totalCards
            binding.tvProgressCounter.text = "Card $currentNum of $total"
            val progressPercent = if (total > 0) ((currentNum.toFloat() / total) * 100).toInt() else 0
            binding.progressIndicator.progress = progressPercent

            if (state.isAnswerRevealed) {
                binding.dividerAnswer.visibility = View.VISIBLE
                binding.lblAnswer.visibility = View.VISIBLE
                binding.tvAnswer.visibility = View.VISIBLE
                binding.btnReveal.visibility = View.GONE
                binding.layoutRatings.visibility = View.VISIBLE
            } else {
                binding.dividerAnswer.visibility = View.GONE
                binding.lblAnswer.visibility = View.GONE
                binding.tvAnswer.visibility = View.GONE
                binding.btnReveal.visibility = View.VISIBLE
                binding.layoutRatings.visibility = View.GONE
            }
        }
    }

    companion object {
        const val EXTRA_DECK_ID = "com.example.uepflip_flashcard.extra.DECK_ID"
        private const val RATING_HARD = 1
        private const val RATING_MEDIUM = 2
        private const val RATING_EASY = 3

        fun createIntent(context: Context, deckId: Long): Intent {
            return Intent(context, StudyActivity::class.java).apply {
                putExtra(EXTRA_DECK_ID, deckId)
            }
        }
    }
}
