package com.example.uepflip_flashcard.ui.main

import android.os.Bundle
import android.view.View
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.fragment.app.Fragment
import androidx.fragment.app.FragmentManager
import androidx.lifecycle.lifecycleScope
import com.example.uepflip_flashcard.R
import com.example.uepflip_flashcard.data.sample.SampleDataSeeder
import com.google.android.material.bottomnavigation.BottomNavigationView
import kotlinx.coroutines.launch

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
                when (item.itemId) {
                    R.id.nav_home -> show(HomeFragment(), R.id.nav_home)
                    R.id.nav_decks -> show(DecksFragment(), R.id.nav_decks)
                    R.id.nav_stats -> show(StatsFragment(), R.id.nav_stats)
                    R.id.nav_tutor -> show(TutorFragment(), R.id.nav_tutor)
                }
            } finally {
                selectingNavItem = false
            }
            true
        }

        // The navigation bar belongs to the screen that ends up on show, not to whoever started the
        // transaction. Deriving it from the resumed fragment means returning from a pushed detail
        // screen — or rotating while on one — restores it on its own (see [NavDestination]).
        supportFragmentManager.registerFragmentLifecycleCallbacks(
            object : FragmentManager.FragmentLifecycleCallbacks() {
                override fun onFragmentResumed(fm: FragmentManager, f: Fragment) {
                    nav.visibility = if (f is NavDestination) View.VISIBLE else View.GONE
                }
            },
            false
        )

        if (savedInstanceState == null) {
            // Developer convenience: a fresh debug build starts with sample decks so every screen has
            // something real to show. No-op outside debuggable builds (see SampleDataSeeder).
            lifecycleScope.launch { SampleDataSeeder.seedIfFirstRun(applicationContext) }

            val onboardingSeen = getSharedPreferences(PREFS_NAME, MODE_PRIVATE)
                .getBoolean(KEY_ONBOARDING_COMPLETE, false)
            if (onboardingSeen) {
                show(HomeFragment(), R.id.nav_home)
            } else {
                show(OnboardingFragment())
            }
        }
    }

    /**
     * Shows a top-level destination. Detail screens use [push] instead so that back returns here;
     * any detail screen already on the back stack is dropped first, because a top-level destination
     * is not a descendant of it.
     */
    fun show(f: Fragment, checkId: Int? = null) {
        if (f is NavDestination) {
            supportFragmentManager.popBackStack(null, FragmentManager.POP_BACK_STACK_INCLUSIVE)
        }
        supportFragmentManager.beginTransaction()
            .replace(R.id.fragment_container, f)
            .commit()
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
    fun push(f: Fragment) {
        supportFragmentManager.beginTransaction()
            .replace(R.id.fragment_container, f)
            .addToBackStack(null)
            .commit()
    }

    fun goHome() = show(HomeFragment(), R.id.nav_home)
    fun goDecks() = show(DecksFragment(), R.id.nav_decks)
    fun goStats() = show(StatsFragment(), R.id.nav_stats)
    fun goProfile() = push(MyProfileFragment())
    fun goSettings() = push(SettingsFragment())
    fun goNotifications() = push(NotificationsFragment())
    fun goAbout() = push(AboutFragment())
    fun goCreate() = push(CreateFragment())
    fun goStudy(deckId: Long) = push(StudyFragment.newInstance(deckId))

    /** Opens the tutor on a card so the AI can grade the student's recalled answer. */
    fun goTutor(question: String, answer: String, deckId: Long) =
        push(TutorFragment.newInstance(question, answer, deckId))
}
