package com.example.uepflip_flashcard.ui.main

import android.content.Context
import android.widget.Toast
import com.example.uepflip_flashcard.R

/**
 * The tail of onboarding (features screen, study-goal screen) and where it ends.
 *
 * To change where the user lands when setup is finished or skipped (for example the Sign in screen),
 * edit [finish] -- both setup screens go through it.
 */
object OnboardingFlow {

    const val KEY_DAILY_GOAL = "daily_goal_cards"
    const val DEFAULT_DAILY_GOAL = 25
    const val KEY_NOTIFY_DAILY = "notify_daily_reminder"
    const val KEY_NOTIFY_STREAK = "notify_streak_alerts"
    const val KEY_NOTIFY_SUGGESTIONS = "notify_deck_suggestions"

    private fun prefs(context: Context) =
        context.getSharedPreferences(MainActivity.PREFS_NAME, Context.MODE_PRIVATE)

    /** Cards per day the student picked on the goal screen (25 until they choose). */
    fun dailyGoal(context: Context): Int = prefs(context).getInt(KEY_DAILY_GOAL, DEFAULT_DAILY_GOAL)

    fun saveDailyGoal(context: Context, cardsPerDay: Int) {
        prefs(context).edit().putInt(KEY_DAILY_GOAL, cardsPerDay).apply()
    }

    /** Defaults match the design: reminder and streak alerts on, deck suggestions off. */
    fun notifyDaily(context: Context): Boolean = prefs(context).getBoolean(KEY_NOTIFY_DAILY, true)
    fun notifyStreak(context: Context): Boolean = prefs(context).getBoolean(KEY_NOTIFY_STREAK, true)
    fun notifySuggestions(context: Context): Boolean = prefs(context).getBoolean(KEY_NOTIFY_SUGGESTIONS, false)

    fun saveNotificationChoices(context: Context, daily: Boolean, streak: Boolean, suggestions: Boolean) {
        prefs(context).edit()
            .putBoolean(KEY_NOTIFY_DAILY, daily)
            .putBoolean(KEY_NOTIFY_STREAK, streak)
            .putBoolean(KEY_NOTIFY_SUGGESTIONS, suggestions)
            .apply()
    }

    /** Records that onboarding has been shown so it does not replay on every cold start, then leaves it. */
    fun finish(main: MainActivity) {
        prefs(main).edit().putBoolean(MainActivity.KEY_ONBOARDING_COMPLETE, true).apply()
        Toast.makeText(main, R.string.onboarding_welcome_toast, Toast.LENGTH_SHORT).show()
        main.goHome()
    }
}
