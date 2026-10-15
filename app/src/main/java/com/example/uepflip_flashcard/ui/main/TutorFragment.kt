package com.example.uepflip_flashcard.ui.main

import android.os.Bundle
import android.view.View
import android.view.inputmethod.EditorInfo
import android.widget.EditText
import android.widget.ImageButton
import androidx.fragment.app.Fragment
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.example.uepflip_flashcard.R
import com.example.uepflip_flashcard.ui.SimpleViewModelFactory
import kotlinx.coroutines.launch

/**
 * AI Tutor chat. Opened plainly it turns your message into flashcards; opened from a study card it
 * grades your recalled answer and shows the accuracy card. The ViewModel owns the conversation so it
 * survives configuration changes.
 */
class TutorFragment : Fragment(R.layout.fragment_tutor) {

    companion object {
        private const val ARG_QUESTION = "arg_question"
        private const val ARG_ANSWER = "arg_answer"
        private const val ARG_DECK_ID = "arg_deck_id"

        /** Opens the tutor as a card grader so the AI can score the student's answer. */
        fun newInstance(question: String, answer: String, deckId: Long): TutorFragment =
            TutorFragment().apply {
                arguments = Bundle().apply {
                    putString(ARG_QUESTION, question)
                    putString(ARG_ANSWER, answer)
                    putLong(ARG_DECK_ID, deckId)
                }
            }
    }

    private val deckId: Long by lazy { arguments?.getLong(ARG_DECK_ID, -1L) ?: -1L }

    private val gradedCard: GradedCard? by lazy {
        val question = arguments?.getString(ARG_QUESTION)
        val answer = arguments?.getString(ARG_ANSWER)
        if (question != null && answer != null) GradedCard(question, answer) else null
    }

    private val viewModel: TutorViewModel by lazy {
        ViewModelProvider(
            this,
            SimpleViewModelFactory { TutorViewModel(requireActivity().application, gradedCard) }
        )[TutorViewModel::class.java]
    }

    private lateinit var msgAdapter: MsgAdapter
    private lateinit var messagesView: RecyclerView
    private lateinit var input: EditText
    private lateinit var sendButton: ImageButton

    override fun onViewCreated(v: View, savedInstanceState: Bundle?) {
        super.onViewCreated(v, savedInstanceState)
        val main = requireActivity() as MainActivity

        messagesView = v.findViewById(R.id.messages)
        input = v.findViewById(R.id.message_input)
        sendButton = v.findViewById(R.id.btn_send)

        msgAdapter = MsgAdapter(
            onViewMore = { viewModel.viewMoreExplanation() },
            onContinue = { continueStudying(main) }
        )
        messagesView.layoutManager = LinearLayoutManager(requireContext())
        messagesView.adapter = msgAdapter

        v.findViewById<View>(R.id.btn_back).setOnClickListener {
            if (!parentFragmentManager.popBackStackImmediate()) main.goHome()
        }

        sendButton.setOnClickListener { submitMessage() }
        input.setOnEditorActionListener { _, actionId, _ ->
            if (actionId == EditorInfo.IME_ACTION_SEND) {
                submitMessage()
                true
            } else {
                false
            }
        }

        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.uiState.collect { state ->
                    msgAdapter.submit(state.messages)
                    sendButton.isEnabled = !state.isGenerating
                    if (state.messages.isNotEmpty()) {
                        messagesView.scrollToPosition(state.messages.size - 1)
                    }
                }
            }
        }
    }

    private fun submitMessage() {
        val text = input.text.toString()
        if (text.isBlank()) return
        input.text.clear()
        viewModel.send(text)
    }

    /** "Continue studying" returns to the session this tutor was opened from. */
    private fun continueStudying(main: MainActivity) {
        if (parentFragmentManager.popBackStackImmediate()) return
        if (deckId > 0) main.goStudy(deckId) else main.goHome()
    }
}
