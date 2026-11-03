package com.example.uepflip_flashcard.data.local.entities

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * Represents a document that was uploaded for AI processing.
 *
 * NOTE: `deckId` is a plain indexed column, NOT a foreign key to [DeckEntity] — see
 * `@Entity.indices` below. Room will not cascade or reject deck deletes, so orphaned `documents`
 * rows (including their full `extractedText`) would be left behind unless the repository deletes
 * them first: `FlashcardRepository` removes a deck's documents in the same transaction as the deck
 * delete. If a caller ever deletes a deck without going through the repository, documents are
 * orphaned. Fixing this properly (FK + ON DELETE CASCADE = Room schema v5 with a table-rebuild
 * migration) is deferred pre-v5 debt (task T-007).
 *
 * @param id Auto-generated primary key
 * @param deckId Id of the [DeckEntity] this document belongs to (indexed, no FK — see above)
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
    val createdAt: Long = System.currentTimeMillis(),
    val userId: String? = null,
    val remoteId: String? = null,
    val syncState: Int = UserEntity.SYNCED,
    val lastSyncedAt: Long = 0L
)

