package com.example.uepflip_flashcard.ui.main

import android.os.Bundle
import android.view.View
import androidx.fragment.app.Fragment
import com.example.uepflip_flashcard.R

/** "Everything you need": a short overview of the three main features (step 2 of 4 in the setup bar). */
class SetupFeaturesFragment : Fragment(R.layout.fragment_setup_features) {

    override fun onViewCreated(v: View, savedInstanceState: Bundle?) {
        super.onViewCreated(v, savedInstanceState)
        val main = requireActivity() as MainActivity
        v.findViewById<View>(R.id.btn_continue).setOnClickListener { main.push(SetupGoalFragment()) }
        v.findViewById<View>(R.id.btn_skip_setup).setOnClickListener { OnboardingFlow.finish(main) }
    }
}
