package com.example.uepflip_flashcard.data.local.entities

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * Represents a document that was uploaded for AI processing.
 *
 * `deckId` is a foreign key to [DeckEntity] with ON DELETE CASCADE, so deleting a deck
 * automatically removes its documents (including their full `extractedText`) — no silent storage
 * leak. Room schema v5 added this FK via a table-rebuild migration ([MIGRATION_4_5]).
 *
 * @param id Auto-generated primary key
 * @param deckId Id of the [DeckEntity] this document belongs to (FK with ON DELETE CASCADE)
 * @param uriString The Uri string of the selected PDF document
 * @param extractedText The text content extracted from the PDF (populated after parsing)
 * @param createdAt Timestamp of when the entity was created
 */
@Entity(
    tableName = "documents",
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
data class DocumentEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val deckId: Long,
    val uriString: String,
    val extractedText: String = "",
    val createdAt: Long = System.currentTimeMillis(),
    val userId: String? = null,
    val remoteId: String? = null,
    val syncState: Int = UserEntity.SYNCED,
    val lastSyncedAt: Long = 0L
)

