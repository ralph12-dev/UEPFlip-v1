package com.example.uepflip_flashcard.domain

import com.example.uepflip_flashcard.data.local.entities.ReviewEntity
import java.util.Calendar

/**
 * Pure helpers that turn review history into the figures on the Statistics screen: per-day accuracy for the
 * current week and the study streak. Everything is derived from stored reviews; days without reviews are
 * reported as null so the UI shows "no data" instead of a made-up 0%.
 */
object StudyActivity {

    private fun startOfDay(millis: Long): Calendar = Calendar.getInstance().apply {
        timeInMillis = millis
        set(Calendar.HOUR_OF_DAY, 0)
        set(Calendar.MINUTE, 0)
        set(Calendar.SECOND, 0)
        set(Calendar.MILLISECOND, 0)
    }

    /** 0 = Monday ... 6 = Sunday, for the day containing [now]. */
    fun todayIndex(now: Long = System.currentTimeMillis()): Int =
        (Calendar.getInstance().apply { timeInMillis = now }.get(Calendar.DAY_OF_WEEK) + 5) % 7

    /**
     * Accuracy (share of reviews not rated Hard, same definition as [DeckStats.recallRatePercent]) for each
     * day of the current Monday-to-Sunday week. Null means no reviews that day (including days still to come).
     */
    fun weekAccuracy(reviews: List<ReviewEntity>, now: Long = System.currentTimeMillis()): List<Int?> {
        val day = startOfDay(now).apply { add(Calendar.DAY_OF_MONTH, -todayIndex(now)) }
        return List(7) {
            val from = day.timeInMillis
            day.add(Calendar.DAY_OF_MONTH, 1)
            val to = day.timeInMillis
            val inDay = reviews.filter { it.reviewedAt in from until to }
            if (inDay.isEmpty()) null else inDay.count { it.rating >= 2 } * 100 / inDay.size
        }
    }

    /**
     * Consecutive days with at least one review, ending today. A streak is still alive if the last review was
     * yesterday (today is not over yet); otherwise it is 0.
     */
    fun streakDays(reviews: List<ReviewEntity>, now: Long = System.currentTimeMillis()): Int {
        if (reviews.isEmpty()) return 0
        val days = reviews.map { startOfDay(it.reviewedAt).timeInMillis }.toSet()
        val cursor = startOfDay(now)
        if (cursor.timeInMillis !in days) {
            cursor.add(Calendar.DAY_OF_MONTH, -1)
            if (cursor.timeInMillis !in days) return 0
        }
        var count = 0
        while (cursor.timeInMillis in days) {
            count++
            cursor.add(Calendar.DAY_OF_MONTH, -1)
        }
        return count
    }
}
