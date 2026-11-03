package com.example.uepflip_flashcard.ui.main

import android.os.Bundle
import android.view.View
import android.widget.LinearLayout
import android.widget.TextView
import androidx.appcompat.widget.SwitchCompat
import androidx.fragment.app.Fragment
import com.example.uepflip_flashcard.R
import com.example.uepflip_flashcard.data.repository.AuthRepository
import com.example.uepflip_flashcard.data.repository.SyncEngine
import com.google.android.material.button.MaterialButton
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.snackbar.Snackbar

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
                .setSingleChoiceItems(labels, choices.indexOf(OnboardingFlow.dailyGoal(ctx))) { _, which ->
                    OnboardingFlow.saveDailyGoal(ctx, choices[which])
                    render()
                }
                .setPositiveButton(android.R.string.ok, null)
                .show()
        }

        v.findViewById<View>(R.id.row_difficulty).setOnClickListener {
            val choices = listOf(AppSettings.DIFFICULTY_EASY, AppSettings.DIFFICULTY_NORMAL, AppSettings.DIFFICULTY_HARD)
            val labels = choices.map { difficultyLabel(it) }.toTypedArray()
            val currentIndex = when (AppSettings.defaultDifficulty(ctx)) {
                AppSettings.DIFFICULTY_EASY -> 0
                AppSettings.DIFFICULTY_HARD -> 2
                else -> 1
            }
            MaterialAlertDialogBuilder(ctx)
                .setTitle(R.string.settings_default_difficulty)
                .setSingleChoiceItems(labels, currentIndex) { _, which ->
                    AppSettings.saveDefaultDifficulty(ctx, choices[which])
                    render()
                }
                .setPositiveButton(android.R.string.ok, null)
                .show()
        }

        v.findViewById<View>(R.id.row_notifications).setOnClickListener { main.goNotifications() }
        v.findViewById<View>(R.id.row_about).setOnClickListener { main.goAbout() }

        // Account section - show if user is signed in
        val authRepository = AuthRepository.getInstance(ctx.applicationContext as android.app.Application)
        val isSignedIn = authRepository.isUserSignedIn()

        // Find the About row and add account section after it
        val aboutRow = v.findViewById<View>(R.id.row_about)

        if (isSignedIn && aboutRow != null) {
            val currentUser = authRepository.getCurrentUser()
            val email = currentUser?.email ?: ""

            val parent = aboutRow.parent as? LinearLayout
            parent?.let { p ->
                val aboutIndex = p.indexOfChild(aboutRow)

                // Add a separator
                val separator = View(ctx).apply {
                    layoutParams = LinearLayout.LayoutParams(
                        LinearLayout.LayoutParams.MATCH_PARENT,
                        1
                    ).apply {
                        setMargins(0, 24, 0, 16)
                    }
                    setBackgroundColor(requireContext().getColor(android.R.color.darker_gray))
                }

                // Account info text
                val accountText = TextView(ctx).apply {
                    layoutParams = LinearLayout.LayoutParams(
                        LinearLayout.LayoutParams.MATCH_PARENT,
                        LinearLayout.LayoutParams.WRAP_CONTENT
                    ).apply {
                        setMargins(16, 8, 16, 8)
                    }
                    text = getString(R.string.settings_account_signed_in, email)
                    textSize = 14f
                    setTextColor(requireContext().getColor(android.R.color.black))
                }

                // Sync Now button
                val syncButton = MaterialButton(ctx).apply {
                    layoutParams = LinearLayout.LayoutParams(
                        LinearLayout.LayoutParams.MATCH_PARENT,
                        LinearLayout.LayoutParams.WRAP_CONTENT
                    ).apply {
                        setMargins(16, 8, 16, 8)
                    }
                    text = "Sync Now"
                    setOnClickListener {
                        SyncEngine.syncNow(ctx, authRepository)
                        Snackbar.make(v, "Sync started", Snackbar.LENGTH_SHORT).show()
                    }
                }

                // Sign Out button
                val signOutButton = MaterialButton(ctx).apply {
                    layoutParams = LinearLayout.LayoutParams(
                        LinearLayout.LayoutParams.MATCH_PARENT,
                        LinearLayout.LayoutParams.WRAP_CONTENT
                    ).apply {
                        setMargins(16, 8, 16, 16)
                    }
                    text = "Sign Out"
                    setBackgroundColor(requireContext().getColor(android.R.color.holo_red_dark))
                    setOnClickListener {
                        main.signOut()
                        Snackbar.make(v, "Signed out successfully", Snackbar.LENGTH_SHORT).show()
                    }
                }

                p.addView(separator, aboutIndex + 1)
                p.addView(accountText, aboutIndex + 2)
                p.addView(syncButton, aboutIndex + 3)
                p.addView(signOutButton, aboutIndex + 4)
            }
        }
    }

    private fun difficultyLabel(difficulty: Int): String {
        return when (difficulty) {
            AppSettings.DIFFICULTY_EASY -> requireContext().getString(R.string.easy)
            AppSettings.DIFFICULTY_NORMAL -> requireContext().getString(R.string.normal)
            AppSettings.DIFFICULTY_HARD -> requireContext().getString(R.string.hard)
            else -> requireContext().getString(R.string.normal)
        }
    }
}