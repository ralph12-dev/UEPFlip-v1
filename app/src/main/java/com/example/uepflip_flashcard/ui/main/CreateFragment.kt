package com.example.uepflip_flashcard.ui.main

import android.net.Uri
import android.os.Bundle
import android.provider.OpenableColumns
import android.text.Editable
import android.text.TextWatcher
import android.view.View
import android.widget.Button
import android.widget.EditText
import android.widget.ImageButton
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.fragment.app.Fragment
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import com.example.uepflip_flashcard.R
import com.example.uepflip_flashcard.data.repository.FlashcardRepository
import com.example.uepflip_flashcard.domain.DeckNaming
import com.example.uepflip_flashcard.ui.SimpleViewModelFactory
import com.example.uepflip_flashcard.ui.document.DocumentImportViewModel
import kotlinx.coroutines.launch

/**
 * "Create flashcards" screen. The user picks one of two AI sources — a typed topic/lesson or an
 * uploaded PDF — names the deck and generates. All long-running work lives in
 * [DocumentImportViewModel]; this fragment only renders its state and owns the deck rollback.
 */
class CreateFragment : Fragment(R.layout.fragment_create) {

    private enum class Source { TOPIC, PDF }

    private lateinit var main: MainActivity
    private lateinit var tabTopic: TextView
    private lateinit var tabPdf: TextView
    private lateinit var panelTopic: View
    private lateinit var panelPdf: View
    private lateinit var topicInput: EditText
    private lateinit var deckNameInput: EditText
    private lateinit var helper: TextView
    private lateinit var fileCard: View
    private lateinit var fileNameView: TextView
    private lateinit var fileSizeView: TextView
    private lateinit var btnRemove: ImageButton
    private lateinit var generateBtn: Button
    private lateinit var uploadZone: View

    private val repository: FlashcardRepository by lazy {
        FlashcardRepository.create(requireContext().applicationContext)
    }

    private val viewModel: DocumentImportViewModel by lazy {
        ViewModelProvider(
            this,
            SimpleViewModelFactory { DocumentImportViewModel(requireActivity().application) }
        )[DocumentImportViewModel::class.java]
    }

    private var source = Source.TOPIC
    private var selectedFileUri: Uri? = null
    private var selectedFileName: String? = null
    private var createdDeckId: Long? = null
    private var successHandled = false
    private var isImporting = false

    /** True while the deck-name field is auto-filled, so the user-edit watcher ignores the change. */
    private var updatingDeckName = false

    /** Set once the user edits the deck name; auto-fill then stops overwriting their choice. */
    private var deckNameEdited = false

    private val pickPdf = registerForActivityResult(
        ActivityResultContracts.GetContent()
    ) { uri ->
        if (!isImporting) uri?.let { onPdfSelected(it) }
    }

    override fun onViewCreated(v: View, savedInstanceState: Bundle?) {
        super.onViewCreated(v, savedInstanceState)
        main = requireActivity() as MainActivity

        v.findViewById<ImageButton>(R.id.btn_back).setOnClickListener { goBack() }

        tabTopic = v.findViewById(R.id.tab_topic)
        tabPdf = v.findViewById(R.id.tab_pdf)
        panelTopic = v.findViewById(R.id.panel_topic)
        panelPdf = v.findViewById(R.id.panel_pdf)
        topicInput = v.findViewById(R.id.input_topic)
        deckNameInput = v.findViewById(R.id.input_deck_name)
        helper = v.findViewById(R.id.create_helper)
        fileCard = v.findViewById(R.id.file_card)
        fileNameView = v.findViewById(R.id.file_name)
        fileSizeView = v.findViewById(R.id.file_size)
        btnRemove = v.findViewById(R.id.btn_remove)
        generateBtn = v.findViewById(R.id.btn_generate)
        uploadZone = v.findViewById(R.id.upload_zone)

        tabTopic.setOnClickListener { if (!isImporting) selectSource(Source.TOPIC) }
        tabPdf.setOnClickListener { if (!isImporting) selectSource(Source.PDF) }

        uploadZone.setOnClickListener { if (!isImporting) pickPdf.launch(PDF_MIME) }
        btnRemove.setOnClickListener { if (!isImporting) clearSelectedFile() }
        generateBtn.setOnClickListener { onGenerateClicked() }

        topicInput.addTextChangedListener(object : TextWatcher {
            override fun afterTextChanged(s: Editable?) {
                if (!deckNameEdited) {
                    autoFillDeckName(
                        DeckNaming.titleFromTopic(topicInput.text.toString(), defaultDeckName())
                    )
                }
                applyIdleState()
            }

            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
        })

        deckNameInput.addTextChangedListener(object : TextWatcher {
            override fun afterTextChanged(s: Editable?) {
                if (!updatingDeckName) deckNameEdited = true
            }

            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
        })

        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.uiState.collect { render(it) }
            }
        }

        selectSource(Source.TOPIC)
    }

    // --- Source picker ---------------------------------------------------------------------------

    private fun selectSource(next: Source) {
        source = next
        val topicSelected = next == Source.TOPIC

        panelTopic.visibility = if (topicSelected) View.VISIBLE else View.GONE
        panelPdf.visibility = if (topicSelected) View.GONE else View.VISIBLE
        tabTopic.setBackgroundResource(if (topicSelected) R.drawable.bg_segment_selected else 0)
        tabPdf.setBackgroundResource(if (topicSelected) 0 else R.drawable.bg_segment_selected)
        tabTopic.setTextColor(requireContext().getColor(if (topicSelected) R.color.ink else R.color.mute))
        tabPdf.setTextColor(requireContext().getColor(if (topicSelected) R.color.mute else R.color.ink))
        helper.setText(if (topicSelected) R.string.create_helper_topic else R.string.create_helper_pdf)

        // Keep the deck name in step with the active source until the user names it themselves.
        if (!deckNameEdited) {
            autoFillDeckName(
                if (topicSelected) {
                    DeckNaming.titleFromTopic(topicInput.text.toString(), defaultDeckName())
                } else {
                    DeckNaming.titleFromFileName(selectedFileName, defaultDeckName())
                }
            )
        }
        applyIdleState()
    }

    // --- PDF handling ----------------------------------------------------------------------------

    private fun onPdfSelected(uri: Uri) {
        selectedFileUri = uri
        selectedFileName = queryDisplayName(uri)
        fileNameView.text = selectedFileName ?: getString(R.string.pdf_generic_name)
        fileSizeView.text = querySizeLabel(uri)
        fileCard.visibility = View.VISIBLE
        if (!deckNameEdited) {
            autoFillDeckName(
                DeckNaming.titleFromFileName(selectedFileName, defaultDeckName())
            )
        }
        applyIdleState()
    }

    private fun clearSelectedFile() {
        selectedFileUri = null
        selectedFileName = null
        fileCard.visibility = View.GONE
        Toast.makeText(requireContext(), R.string.file_removed, Toast.LENGTH_SHORT).show()
        applyIdleState()
    }

    // --- Generation ------------------------------------------------------------------------------

    private fun onGenerateClicked() {
        if (isImporting) return

        val deckTitle = deckNameInput.text.toString().trim()
            .ifEmpty { getString(R.string.create_default_deck_name) }

        when (source) {
            Source.TOPIC -> {
                val topic = topicInput.text.toString().trim()
                if (topic.isEmpty()) {
                    Toast.makeText(requireContext(), R.string.create_error_no_input, Toast.LENGTH_SHORT)
                        .show()
                    return
                }
                startGeneration(deckTitle) { deckId -> viewModel.generateFromText(topic, deckId) }
            }

            Source.PDF -> {
                val uri = selectedFileUri
                if (uri == null) {
                    Toast.makeText(requireContext(), R.string.select_pdf_first, Toast.LENGTH_SHORT)
                        .show()
                    return
                }
                startGeneration(deckTitle) { deckId -> viewModel.importDocument(uri, deckId) }
            }
        }
    }

    /** Creates the deck, then runs [start] against its id; the deck is rolled back if [start] fails. */
    private fun startGeneration(deckTitle: String, start: (Long) -> Unit) {
        isImporting = true
        generateBtn.isEnabled = false

        viewLifecycleOwner.lifecycleScope.launch {
            try {
                val deckId = repository.createDeck(deckTitle)
                createdDeckId = deckId
                start(deckId)
            } catch (e: Exception) {
                createdDeckId = null
                isImporting = false
                generateBtn.isEnabled = true
                Toast.makeText(
                    requireContext(),
                    getString(R.string.create_failed, e.message.orEmpty()),
                    Toast.LENGTH_LONG
                ).show()
            }
        }
    }

    private fun render(state: DocumentImportViewModel.UiState) {
        when (state) {
            is DocumentImportViewModel.UiState.Idle -> applyIdleState()

            is DocumentImportViewModel.UiState.Loading -> {
                isImporting = true
                generateBtn.isEnabled = false
                generateBtn.text = state.message
                setInputsEnabled(false)
            }

            is DocumentImportViewModel.UiState.Success -> {
                if (successHandled) return
                successHandled = true
                createdDeckId = null
                Toast.makeText(
                    requireContext(),
                    resources.getQuantityString(
                        R.plurals.create_success,
                        state.cardsImported,
                        state.cardsImported
                    ),
                    Toast.LENGTH_LONG
                ).show()
                main.goDecks()
            }

            is DocumentImportViewModel.UiState.Error -> {
                rollbackDeck()
                applyIdleState()
                Toast.makeText(requireContext(), state.message, Toast.LENGTH_LONG).show()
                viewModel.resetState()
            }
        }
    }

    private fun applyIdleState() {
        isImporting = false
        generateBtn.text = getString(R.string.generate_flashcards)
        generateBtn.isEnabled = when (source) {
            Source.TOPIC -> topicInput.text.isNotBlank()
            Source.PDF -> selectedFileUri != null
        }
        setInputsEnabled(true)
    }

    private fun setInputsEnabled(enabled: Boolean) {
        uploadZone.isEnabled = enabled
        btnRemove.isEnabled = enabled
        topicInput.isEnabled = enabled
        deckNameInput.isEnabled = enabled
        tabTopic.isEnabled = enabled
        tabPdf.isEnabled = enabled
    }

    private fun autoFillDeckName(title: String) {
        updatingDeckName = true
        deckNameInput.setText(title)
        deckNameInput.setSelection(deckNameInput.text.length)
        updatingDeckName = false
    }

    private fun defaultDeckName(): String = getString(R.string.create_default_deck_name)

    /** If generation failed, remove the deck we created so the user is not left with an empty one. */
    private fun rollbackDeck() {
        val deckId = createdDeckId ?: return
        createdDeckId = null
        viewLifecycleOwner.lifecycleScope.launch {
            repository.deleteDeckIfEmpty(deckId)
        }
    }

    private fun goBack() {
        if (!parentFragmentManager.popBackStackImmediate()) {
            main.goHome()
        }
    }

    private fun queryDisplayName(uri: Uri): String? {
        requireContext().contentResolver.query(uri, null, null, null, null)?.use { cursor ->
            if (cursor.moveToFirst()) {
                val index = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                if (index >= 0) return cursor.getString(index)
            }
        }
        return null
    }

    private fun querySizeLabel(uri: Uri): String {
        var size = 0L
        requireContext().contentResolver.query(uri, null, null, null, null)?.use { cursor ->
            if (cursor.moveToFirst()) {
                val index = cursor.getColumnIndex(OpenableColumns.SIZE)
                if (index >= 0 && !cursor.isNull(index)) size = cursor.getLong(index)
            }
        }
        return if (size > 0) String.format("%.1f MB", size / 1048576.0) else ""
    }

    private companion object {
        const val PDF_MIME = "application/pdf"
    }
}
