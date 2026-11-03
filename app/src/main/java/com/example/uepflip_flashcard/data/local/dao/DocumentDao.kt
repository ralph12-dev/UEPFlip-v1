package com.example.uepflip_flashcard.data.local.dao

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import com.example.uepflip_flashcard.data.local.entities.DocumentEntity
import com.example.uepflip_flashcard.data.local.entities.UserEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface DocumentDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertDocument(document: DocumentEntity): Long

    @Update
    suspend fun updateDocument(document: DocumentEntity)

    @Delete
    suspend fun deleteDocument(document: DocumentEntity)

    @Query("SELECT * FROM documents WHERE deckId = :deckId ORDER BY createdAt DESC")
    fun getDocumentsByDeckId(deckId: Long): Flow<List<DocumentEntity>>

    @Query("SELECT * FROM documents WHERE deckId = :deckId AND userId = :userId ORDER BY createdAt DESC")
    fun getDocumentsByDeckIdAndUserId(deckId: Long, userId: String): Flow<List<DocumentEntity>>

    @Query("SELECT * FROM documents WHERE id = :documentId LIMIT 1")
    suspend fun getDocumentById(documentId: Long): DocumentEntity?

    @Query("SELECT * FROM documents WHERE remoteId = :remoteId AND userId = :userId LIMIT 1")
    suspend fun getDocumentByRemoteId(remoteId: String, userId: String): DocumentEntity?

    @Query("SELECT * FROM documents WHERE syncState = :syncState")
    suspend fun getDocumentsBySyncState(syncState: Int = UserEntity.PENDING): List<DocumentEntity>

    @Query("UPDATE documents SET syncState = :syncState, lastSyncedAt = :lastSyncedAt WHERE id = :documentId")
    suspend fun updateDocumentSyncState(documentId: Long, syncState: Int, lastSyncedAt: Long = System.currentTimeMillis())

    @Query("UPDATE documents SET remoteId = :remoteId, syncState = :syncState, lastSyncedAt = :lastSyncedAt WHERE id = :documentId")
    suspend fun setDocumentRemoteId(documentId: Long, remoteId: String, syncState: Int = UserEntity.SYNCED, lastSyncedAt: Long = System.currentTimeMillis())
}
