package com.example.uepflip_flashcard.domain.scheduler

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.roundToInt

class Sm2SchedulerTest {

    private val scheduler = Sm2Scheduler()
    private val currentTime = 1_700_000_000_000L // Fixed time for deterministic tests

    @Test
    fun testFirstReviewHardRatingResetsTo1DayInterval() {
        val result = scheduler.calculateNextReview(
            rating = Sm2Scheduler.RATING_HARD,
            currentRepetitions = 0,
            currentInterval = 0,
            currentEaseFactor = Sm2Scheduler.DEFAULT_EASE_FACTOR,
            currentTimeMillis = currentTime
        )
        assertEquals(0, result.repetitions)
        assertEquals(1, result.interval)
        assertEquals(currentTime + Sm2Scheduler.MILLIS_IN_A_DAY, result.nextReviewAt)
    }

    @Test
    fun testFirstReviewMediumRating1DayInterval() {
        val result = scheduler.calculateNextReview(
            rating = Sm2Scheduler.RATING_MEDIUM,
            currentRepetitions = 0,
            currentInterval = 0,
            currentEaseFactor = Sm2Scheduler.DEFAULT_EASE_FACTOR,
            currentTimeMillis = currentTime
        )
        assertEquals(1, result.repetitions)
        assertEquals(1, result.interval)
    }

    @Test
    fun testFirstReviewEasyRating1DayInterval() {
        val result = scheduler.calculateNextReview(
            rating = Sm2Scheduler.RATING_EASY,
            currentRepetitions = 0,
            currentInterval = 0,
            currentEaseFactor = Sm2Scheduler.DEFAULT_EASE_FACTOR,
            currentTimeMillis = currentTime
        )
        assertEquals(1, result.repetitions)
        assertEquals(1, result.interval)
    }

    @Test
    fun testSecondReviewEasyRating6DayInterval() {
        val result = scheduler.calculateNextReview(
            rating = Sm2Scheduler.RATING_EASY,
            currentRepetitions = 1,
            currentInterval = 1,
            currentEaseFactor = Sm2Scheduler.DEFAULT_EASE_FACTOR,
            currentTimeMillis = currentTime
        )
        assertEquals(2, result.repetitions)
        assertEquals(6, result.interval)
    }

    @Test
    fun testThirdReviewEasyRatingEaseFactorMultipliedInterval() {
        // After 2 reps: interval = 6, ease = 2.5 (default) + 0.1 = 2.6
        val result = scheduler.calculateNextReview(
            rating = Sm2Scheduler.RATING_EASY,
            currentRepetitions = 2,
            currentInterval = 6,
            currentEaseFactor = Sm2Scheduler.DEFAULT_EASE_FACTOR,
            currentTimeMillis = currentTime
        )
        val expectedInterval = (6 * 2.6f).roundToInt()
        assertEquals(3, result.repetitions)
        assertEquals(expectedInterval, result.interval)
        assertEquals(currentTime + expectedInterval * Sm2Scheduler.MILLIS_IN_A_DAY, result.nextReviewAt)
    }

    @Test
    fun testHardRatingAfterEasyResetsRepetitions() {
        val result = scheduler.calculateNextReview(
            rating = Sm2Scheduler.RATING_HARD,
            currentRepetitions = 5,
            currentInterval = 30,
            currentEaseFactor = 2.5f,
            currentTimeMillis = currentTime
        )
        assertEquals(0, result.repetitions)
        assertEquals(1, result.interval)
    }

    @Test
    fun testEaseFactorDoesNotDropBelowMinimum() {
        // Repeated Hard ratings decrease ease factor
        var ease = Sm2Scheduler.DEFAULT_EASE_FACTOR
        repeat(10) {
            val result = scheduler.calculateNextReview(
                rating = Sm2Scheduler.RATING_HARD,
                currentRepetitions = 0,
                currentInterval = 1,
                currentEaseFactor = ease,
                currentTimeMillis = currentTime
            )
            ease = result.easeFactor
        }
        assertTrue(ease >= Sm2Scheduler.MIN_EASE_FACTOR)
    }

    @Test
    fun testNextReviewDateIsInTheFuture() {
        val result = scheduler.calculateNextReview(
            rating = Sm2Scheduler.RATING_MEDIUM,
            currentRepetitions = 0,
            currentInterval = 0,
            currentEaseFactor = Sm2Scheduler.DEFAULT_EASE_FACTOR,
            currentTimeMillis = currentTime
        )
        assertTrue(result.nextReviewAt > currentTime)
    }

    @Test
    fun testInvalidRatingDefaultsToMedium() {
        val result = scheduler.calculateNextReview(
            rating = 99,
            currentRepetitions = 0,
            currentInterval = 0,
            currentEaseFactor = Sm2Scheduler.DEFAULT_EASE_FACTOR,
            currentTimeMillis = currentTime
        )
        assertEquals(1, result.repetitions)
        assertEquals(1, result.interval)
    }
}