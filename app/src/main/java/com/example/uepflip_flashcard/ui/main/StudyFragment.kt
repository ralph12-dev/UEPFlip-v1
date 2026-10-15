package com.example.uepflip_flashcard.ui.main

import android.os.Bundle
import android.view.View
import android.widget.TextView
import android.widget.Toast
import androidx.activity.OnBackPressedCallback
import androidx.fragment.app.Fragment
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import com.example.uepflip_flashcard.R
import com.example.uepflip_flashcard.data.repository.FlashcardRepository
import com.example.uepflip_flashcard.domain.scheduler.Sm2Scheduler
import com.example.uepflip_flashcard.ui.study.StudyUiState
import com.example.uepflip_flashcard.ui.study.StudyViewModel
import com.google.android.material.button.MaterialButton
import com.google.android.material.progressindicator.LinearProgressIndicator
import kotlinx.coroutines.launch

/**
 * The app's primary study screen. It is a thin view over [StudyViewModel]; all session state and
 * scheduling persistence live in the ViewModel/repository so nothing is lost on rotation.
 */
class StudyFragment : Fragment(R.layout.fragment_study) {

    companion object {
        private const val ARG_DECK_ID = "arg_deck_id"

        fun newInstance(deckId: Long): StudyFragment = StudyFragment().apply {
            arguments = Bundle().apply { putLong(ARG_DECK_ID, deckId) }
        }
    }

    private val deckId: Long by lazy { requireArguments().getLong(ARG_DECK_ID, -1L) }

    private val viewModel: StudyViewModel by lazy {
        ViewModelProvider(
            this,
            StudyViewModel.factory(
                deckId,
                FlashcardRepository.create(requireContext()),
                requireActivity().application
            )
        )[StudyViewModel::class.java]
    }

    private lateinit var front: View
    private lateinit var back: View
    private lateinit var question: TextView
    private lateinit var answer: TextView
    private lateinit var counter: TextView
    private lateinit var progress: LinearProgressIndicator
    private lateinit var nextButton: MaterialButton
    private lateinit var hintPanel: View
    private lateinit var hintText: TextView
    private lateinit var hintButton: MaterialButton

    /** Difficulty chips keyed by the SM-2 rating they select. */
    private lateinit var ratingChips: Map<Int, TextView>

    private var completionNotified = false

    override fun onViewCreated(v: View, savedInstanceState: Bundle?) {
        super.onViewCreated(v, savedInstanceState)

        val main = requireActivity() as MainActivity

        front = v.findViewById(R.id.card_front)
        back = v.findViewById(R.id.card_back)
        question = v.findViewById(R.id.question)
        answer = v.findViewById(R.id.answer)
        counter = v.findViewById(R.id.study_counter)
        progress = v.findViewById(R.id.study_progress)
        nextButton = v.findViewById(R.id.btn_next_card)
        hintPanel = v.findViewById(R.id.hint_panel)
        hintText = v.findViewById(R.id.hint_text)
        hintButton = v.findViewById(R.id.btn_hint)

        v.findViewById<View>(R.id.btn_back).setOnClickListener { goBack() }
        // The design reveals the answer by tapping the card; "Next card" then commits the rating.
        front.setOnClickListener { viewModel.revealAnswer() }
        back.setOnClickListener { viewModel.hideAnswer() }
        nextButton.setOnClickListener { viewModel.submitRating() }

        hintButton.setOnClickListener { viewModel.requestHint() }
        // Hands this card to the tutor, which grades the student's recalled answer.
        v.findViewById<View>(R.id.btn_ai).setOnClickListener {
            val card = viewModel.uiState.value.currentCard ?: return@setOnClickListener
            main.goTutor(card.question, card.answer, deckId)
        }

        ratingChips = mapOf(
            Sm2Scheduler.RATING_EASY to v.findViewById(R.id.diff_easy),
            Sm2Scheduler.RATING_MEDIUM to v.findViewById(R.id.diff_normal),
            Sm2Scheduler.RATING_HARD to v.findViewById(R.id.diff_hard)
        )
        ratingChips.forEach { (rating, chip) ->
            chip.setOnClickListener { viewModel.selectRating(rating) }
        }

        requireActivity().onBackPressedDispatcher.addCallback(
            viewLifecycleOwner,
            object : OnBackPressedCallback(true) {
                override fun handleOnBackPressed() = goBack()
            }
        )

        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.uiState.collect { render(it) }
            }
        }
    }

    private fun render(state: StudyUiState) {
        if (state.isSessionCompleted) {
            if (!completionNotified) {
                completionNotified = true
                val message = state.errorMessage
                    ?: when {
                        state.reviewedCount > 0 -> resources.getQuantityString(
                            R.plurals.study_summary,
                            state.reviewedCount,
                            state.reviewedCount
                        )
                        state.deckIsEmpty -> getString(R.string.study_deck_empty)
                        else -> getString(R.string.no_cards_due)
                    }
                Toast.makeText(requireContext(), message, Toast.LENGTH_SHORT).show()
                goBack()
            }
            return
        }

        // A failed review keeps the current card; tell the user and let them retry.
        state.errorMessage?.let { message ->
            Toast.makeText(requireContext(), message, Toast.LENGTH_SHORT).show()
            viewModel.consumeError()
        }

        val card = state.currentCard ?: return

        // The hint belongs to the card on screen; the ViewModel clears it when the card changes.
        hintPanel.visibility = if (state.hint != null) View.VISIBLE else View.GONE
        hintText.text = state.hint.orEmpty()
        hintButton.isEnabled = !state.isHintLoading

        question.text = card.question
        answer.text = card.answer
        counter.text = getString(R.string.study_counter, state.currentIndex + 1, state.totalCards)
        progress.max = state.totalCards.coerceAtLeast(1)
        progress.progress = state.reviewedCount

        front.visibility = if (state.isAnswerRevealed) View.GONE else View.VISIBLE
        back.visibility = if (state.isAnswerRevealed) View.VISIBLE else View.GONE
        // The rating row is always on screen (as designed), but committing waits for the reveal so a
        // card can never be rated without the answer being seen.
        nextButton.isEnabled = state.isAnswerRevealed && !state.isSubmitting
        renderRatingSelection(state.selectedRating)
    }

    /** Mirrors the Create screen's segmented control: the picked chip gets the raised background. */
    private fun renderRatingSelection(selected: Int) {
        ratingChips.forEach { (rating, chip) ->
            val isSelected = rating == selected
            chip.setBackgroundResource(if (isSelected) R.drawable.bg_segment_selected else 0)
            chip.setTextColor(
                requireContext().getColor(if (isSelected) R.color.ink else R.color.mute)
            )
        }
    }

    private fun goBack() {
        if (!parentFragmentManager.popBackStackImmediate()) {
            requireActivity().onBackPressedDispatcher.onBackPressed()
        }
    }
}
