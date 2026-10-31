package com.example.uepflip_flashcard.ui.main

import android.os.Bundle
import android.view.View
import androidx.appcompat.widget.SwitchCompat
import androidx.fragment.app.Fragment
import com.example.uepflip_flashcard.R

/**
 * "Stay on track": which notifications the student wants (step 4 of 4 in the setup bar). The choices are
 * saved in preferences; nothing is scheduled yet and the notification permission is not requested here, so the
 * student is not asked to allow something the app does not do.
 */
class SetupNotificationsFragment : Fragment(R.layout.fragment_setup_notifications) {

    override fun onViewCreated(v: View, savedInstanceState: Bundle?) {
        super.onViewCreated(v, savedInstanceState)
        val main = requireActivity() as MainActivity
        val ctx = requireContext()

        val daily = v.findViewById<SwitchCompat>(R.id.sw_daily)
        val streak = v.findViewById<SwitchCompat>(R.id.sw_streak)
        val suggestions = v.findViewById<SwitchCompat>(R.id.sw_suggestions)
        daily.isChecked = OnboardingFlow.notifyDaily(ctx)
        streak.isChecked = OnboardingFlow.notifyStreak(ctx)
        suggestions.isChecked = OnboardingFlow.notifySuggestions(ctx)

        v.findViewById<View>(R.id.btn_continue).setOnClickListener {
            OnboardingFlow.saveNotificationChoices(ctx, daily.isChecked, streak.isChecked, suggestions.isChecked)
            OnboardingFlow.finish(main)
        }
        v.findViewById<View>(R.id.btn_skip_setup).setOnClickListener { OnboardingFlow.finish(main) }
    }
}
