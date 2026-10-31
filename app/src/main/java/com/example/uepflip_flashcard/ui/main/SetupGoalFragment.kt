package com.example.uepflip_flashcard.ui.main

import android.os.Bundle
import android.view.View
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import com.example.uepflip_flashcard.R
import com.google.android.material.card.MaterialCardView

/**
 * "Set a study goal": the student picks cards per day (step 3 of 4 in the setup bar). The choice is saved
 * in preferences; reminders are not scheduled yet.
 */
class SetupGoalFragment : Fragment(R.layout.fragment_setup_goal) {

    private val options = listOf(
        R.id.goal_casual to 10,
        R.id.goal_regular to 25,
        R.id.goal_intense to 50
    )

    override fun onViewCreated(v: View, savedInstanceState: Bundle?) {
        super.onViewCreated(v, savedInstanceState)
        val main = requireActivity() as MainActivity
        val density = resources.displayMetrics.density
        val ink = ContextCompat.getColor(requireContext(), R.color.ink)
        val line = ContextCompat.getColor(requireContext(), R.color.line)

        var selected = OnboardingFlow.dailyGoal(requireContext())
        if (options.none { it.second == selected }) selected = OnboardingFlow.DEFAULT_DAILY_GOAL

        fun paint() {
            options.forEach { (id, cards) ->
                val card = v.findViewById<MaterialCardView>(id)
                val on = cards == selected
                card.strokeColor = if (on) ink else line
                card.strokeWidth = ((if (on) 2 else 1) * density).toInt()
                card.isSelected = on
            }
        }
        options.forEach { (id, cards) ->
            v.findViewById<View>(id).setOnClickListener {
                selected = cards
                paint()
            }
        }
        paint()

        v.findViewById<View>(R.id.btn_continue).setOnClickListener {
            OnboardingFlow.saveDailyGoal(requireContext(), selected)
            main.push(SetupNotificationsFragment())
        }
        v.findViewById<View>(R.id.btn_skip_setup).setOnClickListener { OnboardingFlow.finish(main) }
    }
}
