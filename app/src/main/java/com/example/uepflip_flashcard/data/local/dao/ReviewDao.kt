package com.example.uepflip_flashcard.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.example.uepflip_flashcard.data.local.entities.ReviewEntity
import com.example.uepflip_flashcard.data.local.entities.UserEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface ReviewDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertReview(review: ReviewEntity): Long

    @Query("SELECT * FROM reviews WHERE cardId = :cardId ORDER BY reviewedAt DESC")
    fun getReviewsForCard(cardId: Long): Flow<List<ReviewEntity>>

    @Query("SELECT * FROM reviews WHERE cardId = :cardId ORDER BY reviewedAt DESC")
    suspend fun getReviewByCardId(cardId: Long): ReviewEntity?

    @Query("SELECT * FROM reviews ORDER BY reviewedAt DESC")
    fun getAllReviews(): Flow<List<ReviewEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun updateReview(review: ReviewEntity)

    @Query("SELECT COUNT(*) FROM reviews")
    suspend fun getTotalReviewCount(): Int

    @Query("SELECT * FROM reviews WHERE userId = :userId AND syncState = :syncState")
    fun getPendingReviews(userId: String, syncState: Int = UserEntity.PENDING): List<ReviewEntity>

    @Query("SELECT * FROM reviews WHERE syncState = :syncState")
    suspend fun getReviewsBySyncState(syncState: Int = UserEntity.PENDING): List<ReviewEntity>

    @Query("UPDATE reviews SET syncState = :syncState, lastSyncedAt = :lastSyncedAt WHERE id = :reviewId")
    suspend fun updateReviewSyncState(reviewId: Long, syncState: Int, lastSyncedAt: Long = System.currentTimeMillis())

    @Query("UPDATE reviews SET remoteId = :remoteId, syncState = :syncState, lastSyncedAt = :lastSyncedAt WHERE id = :reviewId")
    suspend fun setReviewRemoteId(reviewId: Long, remoteId: String, syncState: Int = UserEntity.SYNCED, lastSyncedAt: Long = System.currentTimeMillis())
}
