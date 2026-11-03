package com.example.uepflip_flashcard.data.local.dao

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import com.example.uepflip_flashcard.data.local.entities.DeckEntity
import com.example.uepflip_flashcard.data.local.entities.UserEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface DeckDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertDeck(deck: DeckEntity): Long

    @Update
    suspend fun updateDeck(deck: DeckEntity)

    @Delete
    suspend fun deleteDeck(deck: DeckEntity)

    @Query("DELETE FROM decks WHERE id = :deckId")
    suspend fun deleteDeckById(deckId: Long)

    @Query("SELECT * FROM decks ORDER BY updatedAt DESC")
    fun getAllDecks(): Flow<List<DeckEntity>>

    @Query("SELECT * FROM decks WHERE userId = :userId ORDER BY updatedAt DESC")
    fun getDecksByUserId(userId: String): Flow<List<DeckEntity>>

    @Query("SELECT * FROM decks WHERE id = :deckId LIMIT 1")
    suspend fun getDeckById(deckId: Long): DeckEntity?

    @Query("SELECT * FROM decks WHERE remoteId = :remoteId AND userId = :userId LIMIT 1")
    suspend fun getDeckByRemoteId(remoteId: String, userId: String): DeckEntity?

    @Query("SELECT * FROM decks WHERE syncState = :syncState")
    suspend fun getDecksBySyncState(syncState: Int = UserEntity.PENDING): List<DeckEntity>

    @Query("UPDATE decks SET syncState = :syncState, lastSyncedAt = :lastSyncedAt WHERE id = :deckId")
    suspend fun updateDeckSyncState(deckId: Long, syncState: Int, lastSyncedAt: Long = System.currentTimeMillis())

    @Query("UPDATE decks SET remoteId = :remoteId, syncState = :syncState, lastSyncedAt = :lastSyncedAt WHERE id = :deckId")
    suspend fun setDeckRemoteId(deckId: Long, remoteId: String, syncState: Int = UserEntity.SYNCED, lastSyncedAt: Long = System.currentTimeMillis())
}
