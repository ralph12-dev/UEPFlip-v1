package com.example.uepflip_flashcard.data.local.entities

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "cards",
    foreignKeys = [
        ForeignKey(
            entity = DeckEntity::class,
            parentColumns = ["id"],
            childColumns = ["deckId"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [Index("deckId")]
)
data class CardEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val deckId: Long,
    val question: String,
    val answer: String,
    /**
     * A pre-generated nudge shown on the study screen before the answer is revealed.
     *
     * Generated alongside the question and answer at import time, so showing it needs no network
     * call. It is null for cards created before this column existed (and for the debug sample data),
     * which is the real "no hint generated yet" state — never an empty string. A null hint makes the
     * study screen generate and persist one lazily. Must never reveal or paraphrase the answer.
     */
    val hint: String? = null,
    val category: String = "",
    val repetitions: Int = 0,
    val interval: Int = 0,
    val easeFactor: Float = 2.5f,
    val lastReviewedAt: Long = 0L,
    val nextReviewAt: Long = 0L,
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis(),
    val rating: Int = 0,
    val userId: String? = null,
    val remoteId: String? = null,
    val syncState: Int = UserEntity.SYNCED,
    val lastSyncedAt: Long = 0L
)
