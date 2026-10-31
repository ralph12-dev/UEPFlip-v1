package com.example.uepflip_flashcard.ui.main

import android.os.Bundle
import android.view.View
import androidx.fragment.app.Fragment
import com.example.uepflip_flashcard.R

/**
 * My Profile screen, opened by tapping the mascot circle (`btn_profile`) in an app header.
 * The gear opens Settings, where Notification Preferences, Privacy & Policy and About live.
 */
class MyProfileFragment : Fragment(R.layout.screen_my_profile) {

    override fun onViewCreated(v: View, savedInstanceState: Bundle?) {
        val main = requireActivity() as MainActivity

        v.findViewById<View>(R.id.btn_back).setOnClickListener { parentFragmentManager.popBackStack() }
        v.findViewById<View>(R.id.btn_settings).setOnClickListener { main.goSettings() }
    }
}
