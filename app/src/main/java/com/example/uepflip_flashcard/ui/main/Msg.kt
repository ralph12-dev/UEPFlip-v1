package com.example.uepflip_flashcard.ui.main

/** The AI's grade for one answer, shown on the Tutor's accuracy card. */
data class AnswerScore(
    val percent: Int,
    val note: String
)

/**
 * Message model used by TutorFragment and MsgAdapter.
 */
data class Msg(
    val type: Int,
    val text: String = "",
    val score: AnswerScore? = null
) {
    companion object {
        const val USER = 0
        const val BOT = 1
        const val ACCURACY = 2
    }
}
