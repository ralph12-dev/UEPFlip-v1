package com.example.uepflip_flashcard.domain.scheduler

import com.example.uepflip_flashcard.data.local.entities.CardEntity
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * Verifies exactly which fields are persisted when a card is reviewed. These are regressions for the
 * bug where `nextReviewAt` was never written (so cards stayed due forever) and `previousInterval`
 * was recorded as the repetition count instead of the interval in days.
 */
class ReviewApplicationTest {

    private val scheduler = Sm2Scheduler()
    private val reviewedAt = 1_700_000_000_000L

    private fun card(interval: Int = 0, repetitions: Int = 0, easeFactor: Float = 2.5f) = CardEntity(
        id = 7L,
        deckId = 1L,
        question = "q",
        answer = "a",
        interval = interval,
        repetitions = repetitions,
        easeFactor = easeFactor,
        createdAt = 1L,
        updatedAt = 1L
    )

    private fun schedule(c: CardEntity, rating: Int) = scheduler.calculateNextReview(
        rating = rating,
        currentRepetitions = c.repetitions,
        currentInterval = c.interval,
        currentEaseFactor = c.easeFactor,
        currentTimeMillis = reviewedAt
    )

    @Test
    fun updatedCardPersistsFullSchedulingState() {
        val c = card(interval = 6, repetitions = 2)
        val result = schedule(c, Sm2Scheduler.RATING_EASY)
        val updated = ReviewApplication.updatedCard(c, Sm2Scheduler.RATING_EASY, result, reviewedAt)

        assertEquals(result.repetitions, updated.repetitions)
        assertEquals(result.interval, updated.interval)
        assertEquals(result.easeFactor, updated.easeFactor, 0.0001f)
        // The critical field: without this the card would remain due forever.
        assertEquals(result.nextReviewAt, updated.nextReviewAt)
        assertEquals(reviewedAt, updated.lastReviewedAt)
        assertEquals(reviewedAt, updated.updatedAt)
        assertEquals(Sm2Scheduler.RATING_EASY, updated.rating)
    }

    @Test
    fun updatedCardMovesCardOutOfTheDueSet() {
        val c = card(interval = 0, repetitions = 0)
        val result = schedule(c, Sm2Scheduler.RATING_MEDIUM)
        val updated = ReviewApplication.updatedCard(c, Sm2Scheduler.RATING_MEDIUM, result, reviewedAt)

        // A card is due when nextReviewAt <= now; after review it must be in the future.
        assertEquals(true, updated.nextReviewAt > reviewedAt)
    }

    @Test
    fun hardRatingReschedulesForTomorrowAndResetsRepetitions() {
        val c = card(interval = 30, repetitions = 5)
        val result = schedule(c, Sm2Scheduler.RATING_HARD)
        val updated = ReviewApplication.updatedCard(c, Sm2Scheduler.RATING_HARD, result, reviewedAt)

        assertEquals(0, updated.repetitions)
        assertEquals(1, updated.interval)
        assertEquals(reviewedAt + Sm2Scheduler.MILLIS_IN_A_DAY, updated.nextReviewAt)
    }

    @Test
    fun reviewRowRecordsIntervalInDaysNotRepetitions() {
        val c = card(interval = 6, repetitions = 4)
        val result = schedule(c, Sm2Scheduler.RATING_MEDIUM)

        val review = ReviewApplication.review(c, Sm2Scheduler.RATING_MEDIUM, result, reviewedAt)

        assertEquals(c.id, review.cardId)
        assertEquals(6, review.previousInterval)
        assertEquals(result.interval, review.resultingInterval)
        assertEquals(reviewedAt, review.reviewedAt)
        assertEquals(Sm2Scheduler.RATING_MEDIUM, review.rating)
    }
}
