package com.example.uepflip_flashcard.ui.main

import android.content.Context
import android.widget.Toast
import com.example.uepflip_flashcard.R

object OnboardingFlow {

    const val KEY_DAILY_GOAL = "daily_goal_cards"
    const val DEFAULT_DAILY_GOAL = 25
    const val KEY_NOTIFY_DAILY = "notify_daily_reminder"
    const val KEY_NOTIFY_STREAK = "notify_streak_alerts"
    const val KEY_NOTIFY_SUGGESTIONS = "notify_deck_suggestions"
    const val KEY_ONBOARDING_COMPLETE = "onboarding_complete"

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

    /** Read the onboarding-completed flag from shared prefs. */
    fun isComplete(context: Context): Boolean =
        prefs(context).getBoolean(KEY_ONBOARDING_COMPLETE, false)

    /** Clear the onboarding-completed flag (used when signing out so a different account can re-onboard). */
    fun reset(context: Context) {
        prefs(context).edit().remove(KEY_ONBOARDING_COMPLETE).apply()
    }

    /** Launches the onboarding setup flow.
     *  Returns true if the onboarding was not already completed.
     */
    fun startIfNeeded(context: Context): Boolean = if (isComplete(context)) false else true

    /** Shows the features-selection screen and returns when the user finishes.
     *  Called from MainActivity when the user enters the onboarding path.
     */
    fun showFeaturesScreen(main: MainActivity) {
        main.push(SetupFeaturesFragment())
    }

    /** Mark onboarding complete and navigate to Home. */
    fun finish(main: MainActivity) {
        prefs(main).edit().putBoolean(KEY_ONBOARDING_COMPLETE, true).apply()
        main.goHome()
    }
}