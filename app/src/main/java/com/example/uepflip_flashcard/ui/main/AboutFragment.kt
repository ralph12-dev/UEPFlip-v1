package com.example.uepflip_flashcard.ui.main

import android.os.Bundle
import android.view.View
import android.widget.TextView
import androidx.fragment.app.Fragment
import com.example.uepflip_flashcard.R

/** About screen, opened from Settings > About. */
class AboutFragment : Fragment(R.layout.fragment_about) {

    override fun onViewCreated(v: View, savedInstanceState: Bundle?) {
        super.onViewCreated(v, savedInstanceState)
        val ctx = requireContext()

        v.findViewById<View>(R.id.btn_back).setOnClickListener { parentFragmentManager.popBackStack() }

        val versionName = try {
            @Suppress("DEPRECATION")
            ctx.packageManager.getPackageInfo(ctx.packageName, 0).versionName
        } catch (e: Exception) {
            null
        } ?: "1.0.0"
        v.findViewById<TextView>(R.id.about_version).text = getString(R.string.about_version, versionName)
    }
}
