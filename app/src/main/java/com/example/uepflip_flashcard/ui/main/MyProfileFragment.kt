package com.example.uepflip_flashcard.ui.main

import android.os.Bundle
import android.view.View
import androidx.fragment.app.Fragment
import com.example.uepflip_flashcard.R
import com.google.android.material.snackbar.Snackbar

class MyProfileFragment : Fragment(R.layout.screen_my_profile) {

    override fun onViewCreated(v: View, savedInstanceState: Bundle?) {
        val main = requireActivity() as MainActivity

        v.findViewById<View>(R.id.btn_back).setOnClickListener { parentFragmentManager.popBackStack() }
        v.findViewById<View>(R.id.btn_settings).setOnClickListener { main.goSettings() }

        // Sign out button
        v.findViewById<View>(R.id.btn_sign_out).setOnClickListener {
            main.signOut()
            Snackbar.make(v, "Signed out successfully", Snackbar.LENGTH_SHORT).show()
        }
    }
}