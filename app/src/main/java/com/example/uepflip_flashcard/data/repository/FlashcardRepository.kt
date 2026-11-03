package com.example.uepflip_flashcard.data.repository

import android.content.Context
import androidx.room.withTransaction
import com.example.uepflip_flashcard.data.local.AppDatabase
import com.example.uepflip_flashcard.data.local.entities.CardEntity
import com.example.uepflip_flashcard.data.local.entities.DeckEntity
import com.example.uepflip_flashcard.data.local.entities.ReviewEntity
import com.example.uepflip_flashcard.domain.scheduler.ReviewApplication
import com.example.uepflip_flashcard.domain.scheduler.Sm2Scheduler
import kotlinx.coroutines.flow.Flow

/**
 * Single entry point for all flashcard persistence. Owns the [AppDatabase] so that operations
 * touching more than one table (for example recording a review) can run inside a transaction.
 */
class FlashcardRepository(
    private val database: AppDatabase,
    private val scheduler: Sm2Scheduler
) {

    private val deckDao = database.deckDao()
    private val cardDao = database.cardDao()
    private val reviewDao = database.reviewDao()
    private val documentDao = database.documentDao()

    // --- Decks ---------------------------------------------------------------------------------

    fun getAllDecks(): Flow<List<DeckEntity>> = deckDao.getAllDecks()

    suspend fun getDeckById(deckId: Long): DeckEntity? = deckDao.getDeckById(deckId)

    suspend fun createDeck(title: String, description: String = ""): Long {
        return deckDao.insertDeck(DeckEntity(title = title.trim(), description = description.trim()))
    }

    suspend fun updateDeck(deck: DeckEntity) {
        deckDao.updateDeck(deck.copy(updatedAt = System.currentTimeMillis()))
    }

    suspend fun renameDeck(deckId: Long, newTitle: String) {
        val deck = deckDao.getDeckById(deckId) ?: return
        deckDao.updateDeck(
            deck.copy(
                title = newTitle.trim(),
                updatedAt = System.currentTimeMillis()
            )
        )
    }

    suspend fun deleteDeck(deck: DeckEntity) {
        deleteDeckById(deck.id)
    }

    /**
     * Deletes a deck together with its documents in one transaction.
     *
     * `DocumentEntity.deckId` carries no foreign key (pre-v5 debt, task T-007), so without this
     * ordering a plain deck delete would orphan the deck's document rows — including imported
     * PDFs' full `extractedText` — as a silent storage leak.
     */
    suspend fun deleteDeckById(deckId: Long) {
        database.withTransaction {
            documentDao.deleteDocumentsForDeck(deckId)
            deckDao.deleteDeckById(deckId)
        }
    }

    /** Removes a deck only if it has no cards — used to roll back a failed AI import. */
    suspend fun deleteDeckIfEmpty(deckId: Long) {
        if (cardDao.getCardCountForDeck(deckId) == 0) {
            deleteDeckById(deckId)
        }
    }

    // --- Cards ---------------------------------------------------------------------------------

    fun getCardsByDeckId(deckId: Long): Flow<List<CardEntity>> = cardDao.getCardsByDeckId(deckId)

    fun getAllCards(): Flow<List<CardEntity>> = cardDao.getAllCards()

    suspend fun getCardById(cardId: Long): CardEntity? = cardDao.getCardById(cardId)

    suspend fun insertCard(
        deckId: Long,
        question: String,
        answer: String,
        category: String = "",
        hint: String? = null
    ): Long {
        return cardDao.insertCard(
            CardEntity(
                deckId = deckId,
                question = question.trim(),
                answer = answer.trim(),
                // A blank hint carries no information: store null ("not generated yet"), never "".
                hint = hint?.trim()?.takeIf { it.isNotEmpty() },
                category = category
            )
        )
    }

    /**
     * Persists a hint generated lazily by the study screen for a card that had none.
     *
     * Only the `hint` and `updatedAt` columns are written, so a concurrent review's scheduling state
     * and the review history can never be clobbered by this call. The reverse race is still possible
     * and acceptable: a review that read the row *before* this write commits would write its stale
     * copy back and the hint would be lost — it is regenerated on the next request. A blank hint is
     * ignored: null means "not generated yet" and an empty string must never be stored in its place.
     *
     * The hint is a convenience, not study state. This is best-effort at the call site: a failure is
     * deliberately allowed to propagate so the caller can log it, but it must not be treated as a
     * reason to interrupt the study session.
     */
    suspend fun saveHint(cardId: Long, hint: String) {
        val trimmed = hint.trim()
        if (trimmed.isEmpty()) return
        cardDao.updateHint(cardId, trimmed, System.currentTimeMillis())
    }

    suspend fun insertCards(cards: List<CardEntity>): List<Long> = cardDao.insertCards(cards)

    suspend fun updateCard(card: CardEntity) {
        cardDao.updateCard(card.copy(updatedAt = System.currentTimeMillis()))
    }

    suspend fun deleteCard(card: CardEntity) {
        cardDao.deleteCard(card)
    }

    suspend fun getCardCountForDeck(deckId: Long): Int = cardDao.getCardCountForDeck(deckId)

    // --- Scheduling / reviews ------------------------------------------------------------------

    suspend fun getDueCardsForDeck(deckId: Long): List<CardEntity> {
        return cardDao.getDueCardsForDeck(deckId, System.currentTimeMillis())
    }

    fun getAllDueCards(): Flow<List<CardEntity>> = cardDao.getAllDueCards()

    suspend fun getDueCardCount(): Int = cardDao.getDueCardCount(System.currentTimeMillis())

    suspend fun getTotalCardCount(): Int = cardDao.getTotalCardCount()

    fun getAllReviews(): Flow<List<ReviewEntity>> = reviewDao.getAllReviews()

    suspend fun getTotalReviewCount(): Int = reviewDao.getTotalReviewCount()

    /**
     * Applies a review rating to a card: computes the next SM-2 schedule, appends a review-history
     * row and updates the card's persisted scheduling state atomically.
     *
     * @param rating 1 = Hard, 2 = Medium, 3 = Easy ([Sm2Scheduler.RATING_HARD] etc.)
     */
    suspend fun processCardReview(
        cardId: Long,
        rating: Int,
        reviewedAt: Long = System.currentTimeMillis()
    ) {
        val card = cardDao.getCardById(cardId) ?: return
        val scheduleResult = scheduler.calculateNextReview(
            rating = rating,
            currentRepetitions = card.repetitions,
            currentInterval = card.interval,
            currentEaseFactor = card.easeFactor,
            currentTimeMillis = reviewedAt
        )

        database.withTransaction {
            reviewDao.insertReview(ReviewApplication.review(card, rating, scheduleResult, reviewedAt))
            cardDao.updateCard(ReviewApplication.updatedCard(card, rating, scheduleResult, reviewedAt))
        }
    }

    companion object {
        /** Builds a repository backed by the app-wide Room database. */
        fun create(context: Context): FlashcardRepository {
            return FlashcardRepository(AppDatabase.getDatabase(context), Sm2Scheduler())
        }
    }
}
