package com.example.uepflip_flashcard.data.local.entities

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "reviews",
    foreignKeys = [
        ForeignKey(
            entity = CardEntity::class,
            parentColumns = ["id"],
            childColumns = ["cardId"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [Index("cardId")]
)
data class ReviewEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val cardId: Long,
    val reviewedAt: Long = System.currentTimeMillis(),
    val rating: Int, // 1 = Hard, 2 = Medium, 3 = Easy
    val previousInterval: Int,
    val resultingInterval: Int,
    val aiScore: Float? = null,
    val userId: String? = null,
    val remoteId: String? = null,
    val syncState: Int = UserEntity.SYNCED,
    val lastSyncedAt: Long = 0L
)
