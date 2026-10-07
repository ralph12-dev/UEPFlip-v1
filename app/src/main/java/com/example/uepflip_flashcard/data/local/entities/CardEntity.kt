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
    val category: String = "",
    val repetitions: Int = 0,
    val interval: Int = 0,
    val easeFactor: Float = 2.5f,
    val lastReviewedAt: Long = 0L,
    val nextReviewAt: Long = 0L
)
