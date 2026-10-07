package com.example.uepflip_flashcard.data.repository

import com.example.uepflip_flashcard.data.local.dao.CardDao
import com.example.uepflip_flashcard.data.local.dao.DeckDao
import com.example.uepflip_flashcard.data.local.dao.ReviewDao
import com.example.uepflip_flashcard.data.local.entities.CardEntity
import com.example.uepflip_flashcard.data.local.entities.DeckEntity
import com.example.uepflip_flashcard.data.local.entities.ReviewEntity
import com.example.uepflip_flashcard.domain.scheduler.Sm2Scheduler
import com.example.uepflip_flashcard.domain.scheduler.SpacedRepetitionScheduler
import kotlinx.coroutines.flow.Flow

class FlashcardRepository(
    private val deckDao: DeckDao,
    private val cardDao: CardDao,
    private val reviewDao: ReviewDao,
    private val scheduler: SpacedRepetitionScheduler = Sm2Scheduler()
) {

    fun getAllDecks(): Flow<List<DeckEntity>> = deckDao.getAllDecks()

    suspend fun getDeckById(deckId: Long): DeckEntity? = deckDao.getDeckById(deckId)

    suspend fun createDeck(title: String, description: String = ""): Long {
        val deck = DeckEntity(title = title, description = description)
        return deckDao.insertDeck(deck)
    }

    suspend fun deleteDeck(deck: DeckEntity) {
        deckDao.deleteDeck(deck)
    }

    fun getCardsByDeckId(deckId: Long): Flow<List<CardEntity>> = cardDao.getCardsByDeckId(deckId)

    suspend fun getDueCardsForDeck(deckId: Long): List<CardEntity> {
        return cardDao.getDueCardsForDeck(deckId)
    }

    fun getAllDueCards(): Flow<List<CardEntity>> {
        return cardDao.getAllDueCards()
    }

    suspend fun insertCard(deckId: Long, question: String, answer: String, category: String = ""): Long {
        val card = CardEntity(
            deckId = deckId,
            question = question,
            answer = answer,
            category = category
        )
        return cardDao.insertCard(card)
    }

    suspend fun insertCards(cards: List<CardEntity>): List<Long> {
        return cardDao.insertCards(cards)
    }

    suspend fun processCardReview(
        card: CardEntity,
        rating: Int,
        aiScore: Float? = null
    ): CardEntity {
        val scheduleResult = scheduler.calculateNextReview(
            rating = rating,
            currentRepetitions = card.repetitions,
            currentInterval = card.interval,
            currentEaseFactor = card.easeFactor,
            currentTimeMillis = System.currentTimeMillis()
        )

        val updatedCard = card.copy(
            repetitions = scheduleResult.repetitions,
            interval = scheduleResult.interval,
            easeFactor = scheduleResult.easeFactor,
            lastReviewedAt = System.currentTimeMillis(),
            nextReviewAt = scheduleResult.nextReviewAt
        )

        cardDao.updateCard(updatedCard)

        val review = ReviewEntity(
            cardId = card.id,
            rating = rating,
            previousInterval = card.interval,
            resultingInterval = scheduleResult.interval,
            aiScore = aiScore
        )
        reviewDao.insertReview(review)

        // Touch deck's updatedAt
        deckDao.getDeckById(card.deckId)?.let {
            deckDao.updateDeck(it.copy(updatedAt = System.currentTimeMillis()))
        }

        return updatedCard
    }

    fun getAllReviews(): Flow<List<ReviewEntity>> = reviewDao.getAllReviews()
}
