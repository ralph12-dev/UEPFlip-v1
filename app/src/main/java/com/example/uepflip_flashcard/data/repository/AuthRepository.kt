package com.example.uepflip_flashcard.data.repository

import android.app.Application
import android.content.Context
import android.content.Intent
import android.util.Log
import androidx.annotation.MainThread
import androidx.annotation.WorkerThread
import com.example.uepflip_flashcard.data.local.AppDatabase
import com.example.uepflip_flashcard.data.local.dao.UserDao
import com.example.uepflip_flashcard.data.local.entities.UserEntity
import com.google.firebase.FirebaseApp
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.FirebaseUser
import com.google.firebase.auth.GoogleAuthProvider
import com.google.android.gms.auth.api.signin.GoogleSignIn
import com.google.android.gms.auth.api.signin.GoogleSignInOptions
import com.google.android.gms.common.api.ApiException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine

class AuthRepository(
    private val app: Application,
    private val userDao: UserDao
) {

    companion object {
        @Volatile
        private var INSTANCE: AuthRepository? = null
        fun getInstance(app: Application): AuthRepository {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: AuthRepository(app, AppDatabase.getDatabase(app).userDao()).also { INSTANCE = it }
            }
        }
        /** Full create with Application + pre-built UserDao (used by MainActivity). */
        fun getInstance(app: Application, userDao: UserDao): AuthRepository {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: AuthRepository(app, userDao).also { INSTANCE = it }
            }
        }
    }

    private var auth: FirebaseAuth? = null
    private var currentUser: FirebaseUser? = null
    private val lock = Any()

    sealed class SignInResult {
        data class Success(val user: FirebaseUser) : SignInResult()
        data class Cancelled(val reason: String = "") : SignInResult()
        data class Failed(val exception: Exception) : SignInResult()
    }

    sealed class SignOutResult {
        object Success : SignOutResult()
        data class Failed(val exception: Exception) : SignOutResult()
    }

    val userFlow: MutableStateFlow<FirebaseUser?> = MutableStateFlow(currentUser)

    init {
        FirebaseApp.initializeApp(app)
        initializeAuth()
    }

    fun getCurrentUserId(): String? = currentUser?.uid
    fun isUserSignedIn(): Boolean = currentUser != null

    suspend fun createGoogleSignInIntent(webClientId: String): Intent {
        return try {
            val googleSignInClient = GoogleSignIn.getClient(
                app,
                GoogleSignInOptions.Builder(GoogleSignInOptions.DEFAULT_SIGN_IN)
                    .requestIdToken(webClientId)
                    .requestEmail()
                    .build()
            )
            googleSignInClient.signInIntent
        } catch (e: Exception) {
            Log.e("AuthRepository", "Failed to create sign in intent", e)
            Intent() // Return empty intent on error
        }
    }

    fun handleSignInResult(data: Intent?): SignInResult {
        if (data == null) {
            return SignInResult.Cancelled("No data")
        }
        try {
            val task = GoogleSignIn.getSignedInAccountFromIntent(data)
            val account = task.getResult(ApiException::class.java)
            val idToken = account?.idToken
            if (idToken == null) {
                return SignInResult.Cancelled("No ID token")
            }
            signInWithCredential(idToken)
            return SignInResult.Success(currentUser!!)
        } catch (e: Exception) {
            return SignInResult.Failed(e)
        }
    }

    private fun signInWithCredential(idToken: String) {
        val credential = GoogleAuthProvider.getCredential(idToken, null)
        auth?.signInWithCredential(credential)?.addOnCompleteListener { task ->
            if (task.isSuccessful) {
                currentUser = task.result.user
                CoroutineScope(Dispatchers.IO).launch {
                    saveUserToDatabase(currentUser)
                }
            }
        } ?: run {
            Log.e("AuthRepository", "Auth not initialized")
        }
    }

    private suspend fun saveUserToDatabase(user: FirebaseUser?) {
        user?.let {
            val profile = UserEntity(
                uid = it.uid,
                email = it.email,
                displayName = it.displayName,
                photoUrl = it.photoUrl?.toString(),
                createdAt = System.currentTimeMillis(),
                lastSyncedAt = System.currentTimeMillis(),
                syncState = UserEntity.SYNCED
            )
            userDao.insertUser(profile)
        }
    }

    suspend fun signOut(): SignOutResult {
        return try {
            auth?.signOut()
            currentUser = null
            SignOutResult.Success
        } catch (e: Exception) {
            SignOutResult.Failed(e)
        }
    }

    fun getDisplayName(): String? = currentUser?.displayName
    fun getPhotoUrl(): String? = currentUser?.photoUrl?.toString()
    fun getEmail(): String? = currentUser?.email
    fun getCurrentUser(): FirebaseUser? = currentUser

    @MainThread
    private fun initializeAuth() {
        if (auth == null) {
            auth = FirebaseAuth.getInstance()
            observeAuthState()
        }
    }

    @MainThread
    private fun observeAuthState() {
        auth?.addAuthStateListener { authState ->
            val user = authState.currentUser
            currentUser = user
            userFlow.value = user
        }
    }

    fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        // This method is no longer needed with the new ActivityResultContracts approach
        // Keeping it for compatibility
    }
}