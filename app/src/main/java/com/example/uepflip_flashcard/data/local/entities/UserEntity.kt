package com.example.uepflip_flashcard.data.local.entities

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "users")
data class UserEntity(
    @PrimaryKey
    val uid: String,
    val email: String? = null,
    val displayName: String? = null,
    val photoUrl: String? = null,
    val createdAt: Long = System.currentTimeMillis(),
    val lastSyncedAt: Long = 0L,
    val syncState: Int = SYNCED
) {
    companion object {
        const val SYNCED = 0
        const val PENDING = 1
        const val CONFLICT = 2
    }
}
