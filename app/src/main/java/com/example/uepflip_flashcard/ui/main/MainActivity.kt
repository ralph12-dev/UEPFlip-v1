package com.example.uepflip_flashcard.ui.main

import android.os.Bundle
import android.view.View
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.fragment.app.Fragment
import androidx.fragment.app.FragmentManager
import com.example.uepflip_flashcard.R
import com.google.android.material.bottomnavigation.BottomNavigationView

class MainActivity : AppCompatActivity() {

    companion object {
        const val PREFS_NAME = "uepflip_prefs"
        const val KEY_ONBOARDING_COMPLETE = "onboarding_complete"
    }

    private lateinit var nav: BottomNavigationView

    /** Guards against the bottom-nav listener re-entering when we set the selection ourselves. */
    private var selectingNavItem = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContentView(R.layout.activity_main)
        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.main)) { v, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom)
            insets
        }

        nav = findViewById(R.id.bottom_nav)
        nav.setOnItemSelectedListener { item ->
            if (selectingNavItem) return@setOnItemSelectedListener true
            selectingNavItem = true
            try {
                // Bottom-nav destinations are top level: drop any detail screen on the back stack so
                // pressing Back from a tab never returns to a stale detail screen.
                supportFragmentManager.popBackStack(null, FragmentManager.POP_BACK_STACK_INCLUSIVE)
                when (item.itemId) {
                    R.id.nav_home -> show(HomeFragment(), true, R.id.nav_home)
                    R.id.nav_decks -> show(DecksFragment(), true, R.id.nav_decks)
                    R.id.nav_stats -> show(StatsFragment(), true, R.id.nav_stats)
                    R.id.nav_tutor -> show(TutorFragment(), false, R.id.nav_tutor)
                }
            } finally {
                selectingNavItem = false
            }
            true
        }

        if (savedInstanceState == null) {
            val onboardingSeen = getSharedPreferences(PREFS_NAME, MODE_PRIVATE)
                .getBoolean(KEY_ONBOARDING_COMPLETE, false)
            if (onboardingSeen) {
                show(HomeFragment(), true, R.id.nav_home)
            } else {
                show(OnboardingFragment(), false)
            }
        }
    }

    /**
     * Shows a top-level destination. Detail screens use [push] instead so that back returns here;
     * any detail screen already on the back stack is dropped because a top-level destination is not
     * a descendant of it.
     */
    fun show(f: Fragment, showNav: Boolean, checkId: Int? = null) {
        if (showNav) {
            supportFragmentManager.popBackStack(null, FragmentManager.POP_BACK_STACK_INCLUSIVE)
        }
        supportFragmentManager.beginTransaction()
            .replace(R.id.fragment_container, f)
            .commit()
        nav.visibility = if (showNav) View.VISIBLE else View.GONE
        checkId?.let { id ->
            if (nav.selectedItemId != id) {
                selectingNavItem = true
                try {
                    nav.selectedItemId = id
                } finally {
                    selectingNavItem = false
                }
            }
        }
    }

    /** Shows a detail destination and adds it to the back stack. */
    fun push(f: Fragment, showNav: Boolean) {
        supportFragmentManager.beginTransaction()
            .replace(R.id.fragment_container, f)
            .addToBackStack(null)
            .commit()
        nav.visibility = if (showNav) View.VISIBLE else View.GONE
    }

    fun goHome() = show(HomeFragment(), true, R.id.nav_home)
    fun goDecks() = show(DecksFragment(), true, R.id.nav_decks)
    fun goStats() = show(StatsFragment(), true, R.id.nav_stats)
    fun goCreate() = push(CreateFragment(), false)
    fun goStudy(deckId: Long) = push(StudyFragment.newInstance(deckId), false)
    fun goTutor() = show(TutorFragment(), false, R.id.nav_tutor)

    /** Opens the tutor on a card so the AI can grade the student's recalled answer. */
    fun goTutor(question: String, answer: String, deckId: Long) =
        push(TutorFragment.newInstance(question, answer, deckId), false)
}
