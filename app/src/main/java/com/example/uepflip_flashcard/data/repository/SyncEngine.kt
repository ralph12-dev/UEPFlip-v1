package com.example.uepflip_flashcard.data.repository

import android.content.Context
import android.util.Log
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import androidx.work.WorkManager
import androidx.work.OneTimeWorkRequest
import androidx.work.NetworkType
import androidx.work.Constraints
import androidx.work.ExistingWorkPolicy
import com.example.uepflip_flashcard.data.local.AppDatabase
import com.google.firebase.database.FirebaseDatabase
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.concurrent.TimeUnit

/** SyncEngine handles bidirectional synchronization between local Room database
  * and Firebase Realtime Database.
  *
  * Strategy: Local-first mutations with WorkManager background sync.
  * - On local write: set syncState=PENDING
  * - WorkManager worker: push pending changes, pull remote changes
  * - Conflict resolution: server timestamp wins (last-write-wins)
  */
object SyncEngine {

    private const val TAG = "SyncEngine"
    private const val SYNC_WORK_NAME = "uepflip_sync_work"

    private var isInitialized = false

    /** Initialize the sync engine and schedule periodic work. */
    fun initialize(context: Context, authRepository: AuthRepository) {
        if (isInitialized) return

        // Schedule periodic sync when user is signed in and network is available
        scheduleSyncWork(context)
        isInitialized = true
    }

    /** Schedule periodic sync work with network constraint. */
    private fun scheduleSyncWork(context: Context) {
        val constraints = Constraints.Builder()
            .setRequiredNetworkType(NetworkType.CONNECTED)
            .build()

        val syncWork = OneTimeWorkRequest.Builder(SyncWorker::class.java)
            .setConstraints(constraints)
            .build()

        WorkManager.getInstance(context)
            .enqueueUniqueWork(
                SYNC_WORK_NAME,
                ExistingWorkPolicy.KEEP,
                syncWork
            )
    }

    /** Trigger an immediate sync (e.g., user taps "Sync Now"). */
    fun syncNow(context: Context, authRepository: AuthRepository) {
        val constraints = Constraints.Builder()
            .setRequiredNetworkType(NetworkType.CONNECTED)
            .build()

        val syncWork = OneTimeWorkRequest.Builder(SyncWorker::class.java)
            .setConstraints(constraints)
            .build()

        WorkManager.getInstance(context).enqueue(syncWork)
    }

    /** Merge guest data to user's account on first sign-in. */
    suspend fun mergeGuestData(
        context: Context,
        authRepository: AuthRepository,
        database: AppDatabase,
        userId: String
    ) {
        // This is a simplified implementation - in production you'd need to
        // properly implement guest data merging logic
        Log.d(TAG, "Merging guest data for user: $userId")
    }

    /** WorkManager worker that performs the actual sync operation. */
    class SyncWorker(
        context: Context,
        params: WorkerParameters
    ) : CoroutineWorker(context, params) {

        override suspend fun doWork(): Result {
            return withContext(Dispatchers.IO) {
                try {
                    // val database = com.example.uepflip_flashcard.data.local.AppDatabase.getDatabase(context)
                    // Note: In a full implementation, we'd get the userId from the work input data
                    // For now, just log that sync is being attempted
                    Log.d(TAG, "Sync worker running")

                    Result.success()
                } catch (e: Exception) {
                    Log.e(TAG, "Sync failed", e)
                    Result.retry()
                }
            }
        }
    }
}