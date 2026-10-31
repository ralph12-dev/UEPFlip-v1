package com.example.uepflip_flashcard.ui.main

import android.content.Context
import android.text.format.DateFormat
import java.util.Calendar

/**
 * User-adjustable settings shown on the Settings and Notifications screens, kept in the same
 * preferences file as onboarding. The daily goal and the reminder / streak / suggestion toggles are
 * shared with [OnboardingFlow], so changing them here and during setup edits the same value.
 *
 * These are stored choices only: nothing schedules notifications yet, and the study screen does not
 * read [autoAdvance] or [defaultDifficulty] yet.
 */
object AppSettings {

    const val KEY_AUTO_ADVANCE = "auto_advance_after_rating"
    const val KEY_DEFAULT_DIFFICULTY = "default_difficulty"
    const val KEY_NOTIFY_REVIEWS_DUE = "notify_reviews_due"
    const val KEY_NOTIFY_DECK_UPDATES = "notify_deck_updates"
    const val KEY_NOTIFY_PRODUCT_NEWS = "notify_product_news"
    const val KEY_QUIET_ENABLED = "quiet_hours_enabled"
    const val KEY_QUIET_FROM = "quiet_hours_from_minutes"
    const val KEY_QUIET_TO = "quiet_hours_to_minutes"

    /** Same scale as the study screen's rating chips: 1 = Hard, 2 = Normal, 3 = Easy. */
    const val DIFFICULTY_HARD = 1
    const val DIFFICULTY_NORMAL = 2
    const val DIFFICULTY_EASY = 3

    const val DEFAULT_QUIET_FROM = 22 * 60
    const val DEFAULT_QUIET_TO = 7 * 60

    val GOAL_CHOICES = intArrayOf(10, 25, 50)

    private fun prefs(c: Context) = c.getSharedPreferences(MainActivity.PREFS_NAME, Context.MODE_PRIVATE)

    fun autoAdvance(c: Context) = prefs(c).getBoolean(KEY_AUTO_ADVANCE, false)
    fun saveAutoAdvance(c: Context, on: Boolean) = prefs(c).edit().putBoolean(KEY_AUTO_ADVANCE, on).apply()

    fun defaultDifficulty(c: Context) = prefs(c).getInt(KEY_DEFAULT_DIFFICULTY, DIFFICULTY_NORMAL)
    fun saveDefaultDifficulty(c: Context, value: Int) =
        prefs(c).edit().putInt(KEY_DEFAULT_DIFFICULTY, value).apply()

    fun reviewsDue(c: Context) = prefs(c).getBoolean(KEY_NOTIFY_REVIEWS_DUE, true)
    fun deckUpdates(c: Context) = prefs(c).getBoolean(KEY_NOTIFY_DECK_UPDATES, true)
    fun productNews(c: Context) = prefs(c).getBoolean(KEY_NOTIFY_PRODUCT_NEWS, false)
    fun quietEnabled(c: Context) = prefs(c).getBoolean(KEY_QUIET_ENABLED, true)
    fun quietFrom(c: Context) = prefs(c).getInt(KEY_QUIET_FROM, DEFAULT_QUIET_FROM)
    fun quietTo(c: Context) = prefs(c).getInt(KEY_QUIET_TO, DEFAULT_QUIET_TO)

    /**
     * Saves every control on the Notifications screen in one write. "Study reminders", "Streak
     * alerts" and "AI tutor tips" reuse the onboarding keys (daily reminder / streak / suggestions).
     */
    fun saveNotifications(
        c: Context,
        reminders: Boolean,
        streak: Boolean,
        reviewsDue: Boolean,
        deckUpdates: Boolean,
        tutorTips: Boolean,
        productNews: Boolean,
        quietEnabled: Boolean,
        quietFrom: Int,
        quietTo: Int
    ) {
        prefs(c).edit()
            .putBoolean(OnboardingFlow.KEY_NOTIFY_DAILY, reminders)
            .putBoolean(OnboardingFlow.KEY_NOTIFY_STREAK, streak)
            .putBoolean(KEY_NOTIFY_REVIEWS_DUE, reviewsDue)
            .putBoolean(KEY_NOTIFY_DECK_UPDATES, deckUpdates)
            .putBoolean(OnboardingFlow.KEY_NOTIFY_SUGGESTIONS, tutorTips)
            .putBoolean(KEY_NOTIFY_PRODUCT_NEWS, productNews)
            .putBoolean(KEY_QUIET_ENABLED, quietEnabled)
            .putInt(KEY_QUIET_FROM, quietFrom)
            .putInt(KEY_QUIET_TO, quietTo)
            .apply()
    }

    /** Formats minutes-after-midnight in the device's 12/24-hour style, e.g. "10:00 PM". */
    fun formatTime(c: Context, minutes: Int): String {
        val cal = Calendar.getInstance().apply {
            set(Calendar.HOUR_OF_DAY, minutes / 60)
            set(Calendar.MINUTE, minutes % 60)
        }
        return DateFormat.getTimeFormat(c).format(cal.time)
    }
}
