package com.example.uepflip_flashcard.data.local.dao

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import com.example.uepflip_flashcard.data.local.entities.CardEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface CardDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertCard(card: CardEntity): Long

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertCards(cards: List<CardEntity>): List<Long>

    @Update
    suspend fun updateCard(card: CardEntity)

    /**
     * Stores a generated hint without touching any other column.
     *
     * A targeted UPDATE (rather than reading the row, copying in the hint and writing the whole
     * entity back) avoids clobbering scheduling state that `processCardReview` may have written
     * concurrently. [updatedAt] is passed in so the caller controls the timestamp.
     */
    @Query("UPDATE cards SET hint = :hint, updatedAt = :updatedAt WHERE id = :cardId")
    suspend fun updateHint(cardId: Long, hint: String, updatedAt: Long = System.currentTimeMillis())

    @Delete
    suspend fun deleteCard(card: CardEntity)

    @Query("SELECT * FROM cards WHERE deckId = :deckId")
    fun getCardsByDeckId(deckId: Long): Flow<List<CardEntity>>

    @Query("SELECT * FROM cards WHERE deckId = :deckId AND (nextReviewAt <= :currentTime OR nextReviewAt = 0)")
    suspend fun getDueCardsForDeck(deckId: Long, currentTime: Long = System.currentTimeMillis()): List<CardEntity>

    @Query("SELECT * FROM cards WHERE (nextReviewAt <= :currentTime OR nextReviewAt = 0)")
    fun getAllDueCards(currentTime: Long = System.currentTimeMillis()): Flow<List<CardEntity>>

    @Query("SELECT COUNT(*) FROM cards WHERE deckId = :deckId")
    suspend fun getCardCountForDeck(deckId: Long): Int

    @Query("SELECT COUNT(*) FROM cards")
    suspend fun getTotalCardCount(): Int

    @Query("SELECT * FROM cards")
    fun getAllCards(): Flow<List<CardEntity>>

    @Query("SELECT COUNT(*) FROM cards WHERE nextReviewAt <= :currentTime OR nextReviewAt = 0")
    suspend fun getDueCardCount(currentTime: Long): Int

    @Query("SELECT * FROM cards WHERE id = :cardId LIMIT 1")
    suspend fun getCardById(cardId: Long): CardEntity?
}
