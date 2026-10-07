package com.example.uepflip_flashcard.domain.scheduler

/**
 * Represents the scheduling state for a single card after a review.
 */
data class ScheduleResult(
    val repetitions: Int,
    val interval: Int,       // days until next review
    val easeFactor: Float,
    val nextReviewAt: Long   // epoch millis
)
