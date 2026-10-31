package com.example.uepflip_flashcard.ui.main

import android.app.TimePickerDialog
import android.os.Bundle
import android.text.format.DateFormat
import android.view.View
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.widget.SwitchCompat
import androidx.fragment.app.Fragment
import com.example.uepflip_flashcard.R

/** Notification preferences. Nothing is stored until "Save preferences" is tapped. */
class NotificationsFragment : Fragment(R.layout.fragment_notifications) {

    private var quietFrom = AppSettings.DEFAULT_QUIET_FROM
    private var quietTo = AppSettings.DEFAULT_QUIET_TO

    override fun onViewCreated(v: View, savedInstanceState: Bundle?) {
        super.onViewCreated(v, savedInstanceState)
        val ctx = requireContext()

        v.findViewById<View>(R.id.btn_back).setOnClickListener { parentFragmentManager.popBackStack() }

        val reminders = v.findViewById<SwitchCompat>(R.id.sw_reminders)
        val streak = v.findViewById<SwitchCompat>(R.id.sw_streak)
        val reviews = v.findViewById<SwitchCompat>(R.id.sw_reviews)
        val deckUpdates = v.findViewById<SwitchCompat>(R.id.sw_deck_updates)
        val tutorTips = v.findViewById<SwitchCompat>(R.id.sw_tutor_tips)
        val productNews = v.findViewById<SwitchCompat>(R.id.sw_product_news)
        val quiet = v.findViewById<SwitchCompat>(R.id.sw_quiet)

        reminders.isChecked = OnboardingFlow.notifyDaily(ctx)
        streak.isChecked = OnboardingFlow.notifyStreak(ctx)
        reviews.isChecked = AppSettings.reviewsDue(ctx)
        deckUpdates.isChecked = AppSettings.deckUpdates(ctx)
        tutorTips.isChecked = OnboardingFlow.notifySuggestions(ctx)
        productNews.isChecked = AppSettings.productNews(ctx)
        quiet.isChecked = AppSettings.quietEnabled(ctx)
        quietFrom = AppSettings.quietFrom(ctx)
        quietTo = AppSettings.quietTo(ctx)

        val fromBox = v.findViewById<View>(R.id.quiet_from)
        val toBox = v.findViewById<View>(R.id.quiet_to)
        val fromText = v.findViewById<TextView>(R.id.quiet_from_value)
        val toText = v.findViewById<TextView>(R.id.quiet_to_value)

        fun render() {
            fromText.text = AppSettings.formatTime(ctx, quietFrom)
            toText.text = AppSettings.formatTime(ctx, quietTo)
            val alpha = if (quiet.isChecked) 1f else 0.5f
            fromBox.alpha = alpha
            toBox.alpha = alpha
        }
        render()
        quiet.setOnCheckedChangeListener { _, _ -> render() }

        fromBox.setOnClickListener { pickTime(quietFrom) { quietFrom = it; render() } }
        toBox.setOnClickListener { pickTime(quietTo) { quietTo = it; render() } }

        v.findViewById<View>(R.id.btn_save).setOnClickListener {
            AppSettings.saveNotifications(
                ctx,
                reminders = reminders.isChecked,
                streak = streak.isChecked,
                reviewsDue = reviews.isChecked,
                deckUpdates = deckUpdates.isChecked,
                tutorTips = tutorTips.isChecked,
                productNews = productNews.isChecked,
                quietEnabled = quiet.isChecked,
                quietFrom = quietFrom,
                quietTo = quietTo
            )
            Toast.makeText(ctx, R.string.notif_saved, Toast.LENGTH_SHORT).show()
            parentFragmentManager.popBackStack()
        }
    }

    private fun pickTime(initialMinutes: Int, onPicked: (Int) -> Unit) {
        val ctx = requireContext()
        TimePickerDialog(
            ctx,
            { _, hour, minute -> onPicked(hour * 60 + minute) },
            initialMinutes / 60,
            initialMinutes % 60,
            DateFormat.is24HourFormat(ctx)
        ).show()
    }
}
