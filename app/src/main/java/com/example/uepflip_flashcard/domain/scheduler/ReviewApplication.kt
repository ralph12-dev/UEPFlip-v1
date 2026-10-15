package com.example.uepflip_flashcard.domain.scheduler

import com.example.uepflip_flashcard.data.local.entities.CardEntity
import com.example.uepflip_flashcard.data.local.entities.ReviewEntity

/**
 * Pure mapping between an SM-2 [ScheduleResult] and the persisted Room rows.
 *
 * Kept free of Android/Room runtime so the exact write behaviour (which fields are persisted, what
 * `previousInterval` means) can be unit tested on the JVM.
 */
object ReviewApplication {

    /**
     * The updated card after applying [result].
     *
     * Crucially this persists the full scheduling state — including `nextReviewAt` and
     * `lastReviewedAt` — so a reviewed card actually leaves the "due" set.
     */
    fun updatedCard(
        card: CardEntity,
        rating: Int,
        result: ScheduleResult,
        reviewedAt: Long
    ): CardEntity = card.copy(
        repetitions = result.repetitions,
        interval = result.interval,
        easeFactor = result.easeFactor,
        lastReviewedAt = reviewedAt,
        nextReviewAt = result.nextReviewAt,
        rating = rating,
        updatedAt = reviewedAt
    )

    /** The review-history row to append. `previousInterval` is the card's interval, in days. */
    fun review(
        card: CardEntity,
        rating: Int,
        result: ScheduleResult,
        reviewedAt: Long
    ): ReviewEntity = ReviewEntity(
        cardId = card.id,
        reviewedAt = reviewedAt,
        rating = rating,
        previousInterval = card.interval,
        resultingInterval = result.interval
    )
}
