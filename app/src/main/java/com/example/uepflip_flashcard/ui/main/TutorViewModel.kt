package com.example.uepflip_flashcard.ui.main

import android.app.Application
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.uepflip_flashcard.R
import com.example.uepflip_flashcard.data.local.entities.CardEntity
import com.example.uepflip_flashcard.data.repository.FlashcardRepository
import com.example.uepflip_flashcard.data.service.AiFlashcardGenerator
import com.example.uepflip_flashcard.data.service.AiGenerationException
import com.example.uepflip_flashcard.data.service.AiGeneratorProvider
import com.google.firebase.remoteconfig.FirebaseRemoteConfig
import com.google.firebase.remoteconfig.FirebaseRemoteConfigSettings
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class TutorUiState(
    val messages: List<Msg> = emptyList(),
    val isGenerating: Boolean = false
)

/** A flashcard the tutor should grade, handed over from the study screen. */
data class GradedCard(val question: String, val referenceAnswer: String)

/**
 * Backs the AI Tutor chat. It has two jobs depending on how it was opened:
 *
 * - plain chat (`card == null`): each message is turned into flashcards with the same Gemini
 *   pipeline the PDF importer uses, saved into a dedicated deck;
 * - grading (`card != null`): the student answers that card from memory and the AI scores the
 *   answer, which is what the accuracy card displays.
 *
 * Every failure is reported with the real error rather than a fabricated reply or score.
 */
class TutorViewModel(
    private val application: Application,
    private val card: GradedCard? = null
) : ViewModel() {

    private val repository = FlashcardRepository.create(application)
    private val remoteConfig = FirebaseRemoteConfig.getInstance()
    private var generator: AiFlashcardGenerator? = null

    /** The student's most recent graded answer, kept so "View more explanation" can expand on it. */
    private var lastAnswer: String? = null

    /** Deck the tutor accumulates cards into; taken from resources so the name stays translatable. */
    private val tutorDeckTitle: String
        get() = application.getString(R.string.tutor_deck_title)

    private val _uiState = MutableStateFlow(TutorUiState())
    val uiState: StateFlow<TutorUiState> = _uiState.asStateFlow()

    init {
        remoteConfig.setConfigSettingsAsync(
            FirebaseRemoteConfigSettings.Builder()
                .setMinimumFetchIntervalInSeconds(0)
                .build()
        )
        remoteConfig.setDefaultsAsync(mapOf(AiGeneratorProvider.API_KEY_CONFIG_KEY to ""))

        val opening = card?.let {
            application.getString(R.string.tutor_eval_intro, it.question)
        } ?: application.getString(R.string.tutor_welcome)

        _uiState.value = TutorUiState(messages = listOf(Msg(Msg.BOT, opening)))
    }

    fun send(rawText: String) {
        val text = rawText.trim()
        if (text.isEmpty() || _uiState.value.isGenerating) return

        if (card != null) {
            grade(text)
        } else {
            generateCards(text)
        }
    }

    /** Asks the AI for a deeper explanation of the student's last graded answer. */
    fun viewMoreExplanation() {
        val target = card ?: return
        val answer = lastAnswer ?: return
        if (_uiState.value.isGenerating) return

        _uiState.update { it.copy(isGenerating = true) }

        viewModelScope.launch {
            val reply = try {
                Msg(
                    Msg.BOT,
                    ensureGenerator().generateExplanation(
                        target.question,
                        target.referenceAnswer,
                        answer
                    )
                )
            } catch (e: Exception) {
                Msg(Msg.BOT, application.getString(R.string.tutor_explanation_failed, reasonOf(e)))
            }
            append(listOf(reply))
        }
    }

    /** Grades [answer] against the card and shows the explanation plus the accuracy card. */
    private fun grade(answer: String) {
        val target = card ?: return
        _uiState.update {
            it.copy(messages = it.messages + Msg(Msg.USER, answer), isGenerating = true)
        }

        viewModelScope.launch {
            val replies = try {
                val evaluation = ensureGenerator()
                    .evaluateAnswer(target.question, target.referenceAnswer, answer)
                lastAnswer = answer
                listOf(
                    Msg(Msg.BOT, evaluation.explanation),
                    Msg(Msg.ACCURACY, score = AnswerScore(evaluation.score, evaluation.note))
                )
            } catch (e: Exception) {
                listOf(Msg(Msg.BOT, application.getString(R.string.tutor_grade_failed, reasonOf(e))))
            }
            append(replies)
        }
    }

    /** Plain-chat path: turns the message into flashcards saved into the tutor deck. */
    private fun generateCards(text: String) {
        _uiState.update {
            it.copy(messages = it.messages + Msg(Msg.USER, text), isGenerating = true)
        }

        viewModelScope.launch {
            val reply = try {
                val cards = ensureGenerator().generateFlashcards(text, isTopic = true)
                if (cards.isEmpty()) {
                    application.getString(R.string.tutor_no_material)
                } else {
                    val deckId = ensureTutorDeck()
                    repository.insertCards(
                        cards.map { card ->
                            CardEntity(
                                deckId = deckId,
                                question = card.question.trim(),
                                answer = card.answer.trim()
                            )
                        }
                    )
                    application.resources.getQuantityString(
                        R.plurals.tutor_created,
                        cards.size,
                        cards.size
                    )
                }
            } catch (e: AiGenerationException) {
                e.message ?: application.getString(R.string.tutor_error_generic)
            } catch (e: Exception) {
                application.getString(R.string.tutor_error_generic)
            }

            append(listOf(Msg(Msg.BOT, reply)))
        }
    }

    private fun append(replies: List<Msg>) {
        _uiState.update {
            it.copy(messages = it.messages + replies, isGenerating = false)
        }
    }

    private fun reasonOf(e: Exception): String =
        e.message ?: application.getString(R.string.tutor_error_unknown)

    private suspend fun ensureGenerator(): AiFlashcardGenerator {
        generator?.let { return it }
        return AiGeneratorProvider.create(remoteConfig).also { generator = it }
    }

    private suspend fun ensureTutorDeck(): Long {
        val title = tutorDeckTitle
        val existing = repository.getAllDecks().first().firstOrNull { it.title == title }
        return existing?.id ?: repository.createDeck(title)
    }
}
