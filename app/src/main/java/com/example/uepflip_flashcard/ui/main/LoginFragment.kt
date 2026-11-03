package com.example.uepflip_flashcard.ui.main

import android.app.Activity
import android.content.Intent
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.fragment.app.Fragment
import com.example.uepflip_flashcard.R
import com.example.uepflip_flashcard.data.local.AppDatabase
import com.example.uepflip_flashcard.data.repository.AuthRepository
import com.google.android.gms.auth.api.signin.GoogleSignIn
import com.google.android.gms.auth.api.signin.GoogleSignInClient
import com.google.android.gms.auth.api.signin.GoogleSignInOptions
import com.google.android.gms.common.api.ApiException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class LoginFragment : Fragment() {

    private var signInInProgress = false

    private lateinit var signInLauncher: androidx.activity.result.ActivityResultLauncher<Intent>
    private var googleButton: android.widget.Button? = null
    private var guestButton: android.widget.Button? = null

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        return inflater.inflate(R.layout.fragment_login, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        googleButton = view.findViewById<android.widget.Button>(R.id.btn_login_google)
        guestButton = view.findViewById<android.widget.Button>(R.id.btn_login_guest)
        val btnBack = view.findViewById<View>(R.id.btn_back)

        val main = requireActivity() as MainActivity

        updateBackArrow(btnBack)
        btnBack.setOnClickListener {
            if (parentFragmentManager.backStackEntryCount > 0) {
                parentFragmentManager.popBackStack()
            }
        }

        guestButton?.setOnClickListener {
            if (!signInInProgress) {
                main.onGuestContinue()
            }
        }

        setupSignInLauncher(main)
        googleButton?.setOnClickListener {
            if (!signInInProgress) {
                startGoogleSignIn(main)
            }
        }
    }

    private fun setupSignInLauncher(main: MainActivity) {
        signInLauncher = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
            signInInProgress = false
            googleButton?.isEnabled = true
            guestButton?.isEnabled = true
            if (result.resultCode == Activity.RESULT_OK) {
                val data = result.data
                if (data != null) {
                    handleGoogleSignInResult(data, main)
                } else {
                    showToast(getString(R.string.login_google_failed))
                }
            } else if (result.resultCode == Activity.RESULT_CANCELED) {
                // User cancelled
            } else {
                showToast(getString(R.string.login_google_failed))
            }
        }
    }

    private fun startGoogleSignIn(main: MainActivity) {
        val webClientIdRes = resources.getIdentifier("default_web_client_id", "string", requireContext().packageName)
        val webClientId = if (webClientIdRes != 0) resources.getString(webClientIdRes) else ""

        if (webClientId.isBlank()) {
            showToast(getString(R.string.login_google_unavailable))
            return
        }

        signInInProgress = true
        googleButton?.isEnabled = false
        guestButton?.isEnabled = false

        val authRepo = AuthRepository.getInstance(requireContext().applicationContext as android.app.Application)
        CoroutineScope(Dispatchers.IO).launch {
            val intent = authRepo.createGoogleSignInIntent(webClientId)
            requireActivity().runOnUiThread {
                signInLauncher.launch(intent)
            }
        }
    }

    private fun handleGoogleSignInResult(data: android.content.Intent, main: MainActivity) {
        val authRepo = AuthRepository.getInstance(requireContext().applicationContext as android.app.Application)
        CoroutineScope(Dispatchers.IO).launch {
            val result = authRepo.handleSignInResult(data)
            requireActivity().runOnUiThread {
                when (result) {
                    is AuthRepository.SignInResult.Success -> {
                        main.onUserSignedIn()
                    }
                    is AuthRepository.SignInResult.Cancelled -> {
                        // no-op
                    }
                    is AuthRepository.SignInResult.Failed -> {
                        showToast(getString(R.string.login_google_failed, result.exception?.message ?: ""))
                    }
                }
            }
        }
    }

    private fun updateBackArrow(btnBack: View) {
        val visible = parentFragmentManager.backStackEntryCount > 0
        btnBack.visibility = if (visible) View.VISIBLE else View.GONE
    }

    private fun showToast(message: String) {
        Toast.makeText(requireContext(), message, Toast.LENGTH_SHORT).show()
    }
}