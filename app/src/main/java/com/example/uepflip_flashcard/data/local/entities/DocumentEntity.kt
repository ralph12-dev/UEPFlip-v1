package com.example.uepflip_flashcard.data.local.entities

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * Represents a document that was uploaded for AI processing.
 * @param id Auto-generated primary key
 * @param deckId Foreign key to the [DeckEntity] this document belongs to
 * @param uriString The Uri string of the selected PDF document
 * @param extractedText The text content extracted from the PDF (populated after parsing)
 * @param createdAt Timestamp of when the entity was created
 */
@Entity(
    tableName = "documents",
    indices = [androidx.room.Index("deckId")]
)
data class DocumentEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val deckId: Long,
    val uriString: String,
    val extractedText: String = "",
    val createdAt: Long = System.currentTimeMillis()
)
