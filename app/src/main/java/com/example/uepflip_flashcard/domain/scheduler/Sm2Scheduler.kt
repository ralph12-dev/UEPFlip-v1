package com.example.uepflip_flashcard.domain.scheduler

import kotlin.math.max
import kotlin.math.roundToInt

class Sm2Scheduler : SpacedRepetitionScheduler {

    companion object {
        const val MIN_EASE_FACTOR = 1.3f
        const val DEFAULT_EASE_FACTOR = 2.5f
        const val MILLIS_IN_A_DAY = 86_400_000L // 24 * 60 * 60 * 1000

        const val RATING_HARD = 1
        const val RATING_MEDIUM = 2
        const val RATING_EASY = 3
    }

    override fun calculateNextReview(
        rating: Int,
        currentRepetitions: Int,
        currentInterval: Int,
        currentEaseFactor: Float,
        currentTimeMillis: Long
    ): ScheduleResult {
        // Map 1..3 to SM-2 quality (0..5)
        // 1 (Hard) -> 2 (Fail)
        // 2 (Medium) -> 4 (Good)
        // 3 (Easy) -> 5 (Perfect)
        val quality = when (rating) {
            RATING_HARD -> 2
            RATING_MEDIUM -> 4
            RATING_EASY -> 5
            else -> 4
        }

        // Calculate new Ease Factor using standard SM-2 equation:
        // EF' = EF + (0.1 - (5 - q) * (0.08 + (5 - q) * 0.02))
        val qDiff = 5 - quality
        val delta = 0.1f - (qDiff * (0.08f + (qDiff * 0.02f)))
        val newEaseFactor = max(MIN_EASE_FACTOR, currentEaseFactor + delta)

        val newRepetitions: Int
        val newInterval: Int

        if (quality < 3) {
            // Failure / Hard: reset repetitions and schedule for review tomorrow (1 day)
            newRepetitions = 0
            newInterval = 1
        } else {
            // Successful recall
            newRepetitions = currentRepetitions + 1
            newInterval = when (newRepetitions) {
                1 -> 1
                2 -> 6
                else -> {
                    val computed = (currentInterval * currentEaseFactor).roundToInt()
                    max(1, computed)
                }
            }
        }

        val nextReviewAt = currentTimeMillis + (newInterval * MILLIS_IN_A_DAY)

        return ScheduleResult(
            repetitions = newRepetitions,
            interval = newInterval,
            easeFactor = newEaseFactor,
            nextReviewAt = nextReviewAt
        )
    }
}
