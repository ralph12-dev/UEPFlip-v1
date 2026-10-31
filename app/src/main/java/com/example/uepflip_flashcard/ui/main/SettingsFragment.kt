package com.example.uepflip_flashcard.ui.main

import android.os.Bundle
import android.view.View
import android.widget.TextView
import androidx.appcompat.widget.SwitchCompat
import androidx.fragment.app.Fragment
import com.example.uepflip_flashcard.R
import com.google.android.material.dialog.MaterialAlertDialogBuilder

/** Settings: an expandable "Study" group plus links to the other settings screens. */
class SettingsFragment : Fragment(R.layout.fragment_settings) {

    override fun onViewCreated(v: View, savedInstanceState: Bundle?) {
        super.onViewCreated(v, savedInstanceState)
        val main = requireActivity() as MainActivity
        val ctx = requireContext()

        v.findViewById<View>(R.id.btn_back).setOnClickListener { parentFragmentManager.popBackStack() }

        val body = v.findViewById<View>(R.id.study_body)
        val chevron = v.findViewById<View>(R.id.study_chevron)
        v.findViewById<View>(R.id.study_header).setOnClickListener {
            val open = body.visibility != View.VISIBLE
            body.visibility = if (open) View.VISIBLE else View.GONE
            chevron.animate().rotation(if (open) 90f else 0f).setDuration(150).start()
        }

        val goalText = v.findViewById<TextView>(R.id.val_daily_goal)
        val difficultyText = v.findViewById<TextView>(R.id.val_difficulty)

        fun render() {
            goalText.text = getString(R.string.settings_cards_value, OnboardingFlow.dailyGoal(ctx))
            difficultyText.text = difficultyLabel(AppSettings.defaultDifficulty(ctx))
        }
        render()

        v.findViewById<View>(R.id.row_daily_goal).setOnClickListener {
            val choices = AppSettings.GOAL_CHOICES
            val labels = choices.map { getString(R.string.settings_cards_value, it) }.toTypedArray()
            MaterialAlertDialogBuilder(ctx)
                .setTitle(R.string.settings_daily_goal)
                .setSingleChoiceItems(labels, choices.indexOf(OnboardingFlow.dailyGoal(ctx))) { dialog, which ->
                    OnboardingFlow.saveDailyGoal(ctx, choices[which])
                    render()
                    dialog.dismiss()
                }
                .show()
        }

        v.findViewById<View>(R.id.row_difficulty).setOnClickListener {
            val values = intArrayOf(
                AppSettings.DIFFICULTY_EASY,
                AppSettings.DIFFICULTY_NORMAL,
                AppSettings.DIFFICULTY_HARD
            )
            MaterialAlertDialogBuilder(ctx)
                .setTitle(R.string.settings_default_difficulty)
                .setSingleChoiceItems(
                    values.map { difficultyLabel(it) }.toTypedArray(),
                    values.indexOf(AppSettings.defaultDifficulty(ctx))
                ) { dialog, which ->
                    AppSettings.saveDefaultDifficulty(ctx, values[which])
                    render()
                    dialog.dismiss()
                }
                .show()
        }

        val autoAdvance = v.findViewById<SwitchCompat>(R.id.sw_auto_advance)
        autoAdvance.isChecked = AppSettings.autoAdvance(ctx)
        autoAdvance.setOnCheckedChangeListener { _, on -> AppSettings.saveAutoAdvance(ctx, on) }
        v.findViewById<View>(R.id.row_auto_advance).setOnClickListener { autoAdvance.toggle() }

        v.findViewById<View>(R.id.row_notifications).setOnClickListener { main.goNotifications() }
        v.findViewById<View>(R.id.row_about).setOnClickListener { main.goAbout() }
        // Resolve sync conflict and Privacy & Policy have no screens yet.
    }

    private fun difficultyLabel(value: Int): String = getString(
        when (value) {
            AppSettings.DIFFICULTY_EASY -> R.string.settings_easy
            AppSettings.DIFFICULTY_HARD -> R.string.hard
            else -> R.string.normal
        }
    )
}
