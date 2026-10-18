package com.example.uepflip_flashcard.data.local

import androidx.room.Room
import androidx.room.withTransaction
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.example.uepflip_flashcard.data.local.entities.CardEntity
import com.example.uepflip_flashcard.data.local.entities.DocumentEntity
import com.example.uepflip_flashcard.data.local.entities.ReviewEntity
import com.example.uepflip_flashcard.data.repository.FlashcardRepository
import com.example.uepflip_flashcard.domain.scheduler.Sm2Scheduler
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import kotlin.math.roundToInt

/**
 * In-memory Room coverage — the first tests in this repo to exercise persistence for real.
 *
 * Everything runs against [Room.inMemoryDatabaseBuilder] via [FlashcardRepository]'s injectable
 * database, so no production code changes were needed. The database is created in [setUp] and
 * closed in [tearDown]; leaking a RoomDatabase instance on device fails the run.
 *
 * Fixed timestamps ([T0]) keep every time-sensitive assertion deterministic — including the
 * `getDueCardsForDeck` filtering, which takes an explicit `currentTime`.
 */
@RunWith(AndroidJUnit4::class)
class AppDatabaseTest {

    private lateinit var db: AppDatabase
    private lateinit var repository: FlashcardRepository

    @Before
    fun setUp() {
        db = Room.inMemoryDatabaseBuilder(
            ApplicationProvider.getApplicationContext(),
            AppDatabase::class.java
        ).build()
        repository = FlashcardRepository(db, Sm2Scheduler())
    }

    @After
    fun tearDown() {
        db.close()
    }

    // --- 1. The review double-write (withTransaction contract) ---------------------------------

    @Test
    fun reviewDoubleWrite_persistsAuditRowAndUpdatedCardTogether() = runBlocking<Unit> {
        val deckId = repository.createDeck("Double write")
        val cardId = repository.insertCard(deckId, "What is SM-2?", "A spaced-repetition algorithm")

        repository.processCardReview(cardId, Sm2Scheduler.RATING_EASY, reviewedAt = T0)

        // Both halves of the transaction must be present — a review may never half-apply.
        val card = repository.getCardById(cardId)
        assertNotNull("card must survive its own review", card)
        val reviews = db.reviewDao().getReviewsForCard(cardId).first()

        // Half A: exactly one audit row describing this review.
        assertEquals(1, reviews.size)
        val review = reviews.single()
        assertEquals(cardId, review.cardId)
        assertEquals(T0, review.reviewedAt)
        assertEquals(Sm2Scheduler.RATING_EASY, review.rating)
        assertEquals(0, review.previousInterval) // fresh card: interval was 0 days
        assertEquals(1, review.resultingInterval) // first successful review -> 1 day
        assertNull("AI score stays null for a hand-made review", review.aiScore)

        // Half B: the card's scheduling fields updated in the same transaction.
        assertEquals(1, card!!.repetitions)
        assertEquals(1, card.interval)
        assertEquals(
            (Sm2Scheduler.DEFAULT_EASE_FACTOR + 0.1f).toDouble(),
            card.easeFactor.toDouble(),
            FLOAT_TOLERANCE
        )
        assertEquals(T0, card.lastReviewedAt)
        assertEquals(T0 + DAY, card.nextReviewAt)
        assertEquals(Sm2Scheduler.RATING_EASY, card.rating) // v2 column
        assertEquals(T0, card.updatedAt)

        // And the two halves agree with each other: the review's resulting interval is the
        // card's persisted interval.
        assertEquals(review.resultingInterval, card.interval)
    }

    @Test
    fun reviewOfUnknownCard_isNoOp_noRowsWritten() = runBlocking<Unit> {
        // Guard branch of processCardReview: the card lookup fails -> return before the
        // transaction, so nothing is written at all.
        repository.processCardReview(
            cardId = 999_999L,
            rating = Sm2Scheduler.RATING_EASY,
            reviewedAt = T0
        )

        assertEquals(0, db.reviewDao().getTotalReviewCount())
        assertEquals(0, db.cardDao().getTotalCardCount())
    }

    @Test
    fun failingSecondWrite_rollsBackFirstWriteInsideTransaction() = runBlocking<Unit> {
        // Exercises the rollback side of the same withTransaction boundary the repository
        // uses for the review double-write: write A succeeds, write B violates the
        // reviews.cardId foreign key, so A must be discarded — never half-applied.
        val deckId = repository.createDeck("Rollback")
        val cardId = repository.insertCard(deckId, "Q", "A")

        var failure: Throwable? = null
        try {
            db.withTransaction {
                db.reviewDao().insertReview(
                    ReviewEntity(
                        cardId = cardId,
                        reviewedAt = T0,
                        rating = Sm2Scheduler.RATING_MEDIUM,
                        previousInterval = 0,
                        resultingInterval = 1
                    )
                )
                // No card with this id -> FOREIGN KEY constraint failed -> rollback.
                db.reviewDao().insertReview(
                    ReviewEntity(
                        cardId = 424_242L,
                        reviewedAt = T0,
                        rating = Sm2Scheduler.RATING_MEDIUM,
                        previousInterval = 0,
                        resultingInterval = 1
                    )
                )
            }
        } catch (t: Throwable) {
            failure = t
        }

        assertNotNull(
            "second insert must fail on the reviews.cardId foreign key " +
                "(if it did not, FK enforcement is off and CASCADE delete is broken too)",
            failure
        )
        assertEquals(
            "the first review must be rolled back with the second",
            0,
            db.reviewDao().getTotalReviewCount()
        )
    }

    // --- 2. Interval progression as persisted ------------------------------------------------------------------

    @Test
    fun intervalProgression_threeEasyReviews_persists1dayThen6daysThenScaled() = runBlocking<Unit> {
        val deckId = repository.createDeck("Progression")
        val cardId = repository.insertCard(deckId, "Q", "A")

        // Fresh, unreviewed card.
        var card = repository.getCardById(cardId)!!
        assertEquals(0, card.repetitions)
        assertEquals(0, card.interval)
        assertEquals(
            Sm2Scheduler.DEFAULT_EASE_FACTOR.toDouble(),
            card.easeFactor.toDouble(),
            FLOAT_TOLERANCE
        )
        assertEquals(0L, card.nextReviewAt)

        // Review 1 -> 1 day, ease 2.5 -> 2.6 (q=5 adds 0.1).
        repository.processCardReview(cardId, Sm2Scheduler.RATING_EASY, reviewedAt = T0)
        card = repository.getCardById(cardId)!!
        assertEquals(1, card.repetitions)
        assertEquals(1, card.interval)
        assertEquals(2.6, card.easeFactor.toDouble(), FLOAT_TOLERANCE)
        assertEquals(T0 + 1 * DAY, card.nextReviewAt)
        assertEquals(T0, card.lastReviewedAt)

        // Review 2 -> 6 days, ease 2.6 -> 2.7.
        val t1 = T0 + DAY
        repository.processCardReview(cardId, Sm2Scheduler.RATING_EASY, reviewedAt = t1)
        card = repository.getCardById(cardId)!!
        assertEquals(2, card.repetitions)
        assertEquals(6, card.interval)
        assertEquals(2.7, card.easeFactor.toDouble(), FLOAT_TOLERANCE)
        assertEquals(t1 + 6 * DAY, card.nextReviewAt)

        // Review 3 -> round(currentInterval * newEaseFactor) = round(6 * 2.8) = 17 days,
        // ease 2.7 -> 2.8. Everything is read back from the database, not from the scheduler.
        val t2 = t1 + 6 * DAY
        val expectedThirdInterval = (6 * 2.8f).roundToInt()
        repository.processCardReview(cardId, Sm2Scheduler.RATING_EASY, reviewedAt = t2)
        card = repository.getCardById(cardId)!!
        assertEquals(3, card.repetitions)
        assertEquals(expectedThirdInterval, card.interval)
        assertEquals(17, card.interval) // pinned: 6 * 2.8 rounds up from 16.8
        assertEquals(2.8, card.easeFactor.toDouble(), FLOAT_TOLERANCE)
        assertEquals(t2 + expectedThirdInterval * DAY, card.nextReviewAt)

        // Three reviews recorded, one per review.
        assertEquals(3, db.reviewDao().getTotalReviewCount())
    }

    // --- 3. getDueCardsForDeck time filtering -------------------------------------------------------------------

    @Test
    fun dueCardsForDeck_returnsPastAndFresh_excludesFuture_andStaysInsideDeck() = runBlocking<Unit> {
        val deckA = repository.createDeck("Deck A")
        val deckB = repository.createDeck("Deck B")
        val now = T0
        val hour = 3_600_000L

        val overdueId = db.cardDao().insertCard(
            CardEntity(deckId = deckA, question = "overdue", answer = "a", nextReviewAt = now - hour)
        )
        val futureId = db.cardDao().insertCard(
            CardEntity(deckId = deckA, question = "future", answer = "a", nextReviewAt = now + hour)
        )
        val freshId = db.cardDao().insertCard(
            CardEntity(deckId = deckA, question = "fresh", answer = "a", nextReviewAt = 0L)
        )
        // Overdue, but in a different deck — must not leak into deck A's due list.
        val otherDeckId = db.cardDao().insertCard(
            CardEntity(deckId = deckB, question = "other deck", answer = "a", nextReviewAt = now - hour)
        )

        val dueIds = db.cardDao().getDueCardsForDeck(deckA, now).map { it.id }

        assertEquals(2, dueIds.size)
        assertTrue("overdue card must be due", overdueId in dueIds)
        assertTrue("fresh card (nextReviewAt = 0) must be due", freshId in dueIds)
        assertFalse("future card must not be due", futureId in dueIds)
        assertFalse("card from another deck must not leak in", otherDeckId in dueIds)
    }

    // --- 4. deleteDeckIfEmpty — the AI-import rollback guard -----------------------------------

    @Test
    fun deleteDeckIfEmpty_onDeckWithCards_keepsDeckAndItsCards() = runBlocking<Unit> {
        // The guard must NOT fire when the deck has cards: an inverted guard would silently
        // destroy a deck that finished importing.
        val deckId = repository.createDeck("Finished import")
        repository.insertCard(deckId, "Q1", "A1")
        repository.insertCard(deckId, "Q2", "A2")

        repository.deleteDeckIfEmpty(deckId)

        assertNotNull(
            "deck with cards must survive deleteDeckIfEmpty",
            repository.getDeckById(deckId)
        )
        assertEquals(2, repository.getCardCountForDeck(deckId))
    }

    @Test
    fun deleteDeckIfEmpty_onDeckWithoutCards_deletesTheDeck() = runBlocking<Unit> {
        // The other branch: a failed import leaves a cardless deck, which must roll back.
        val deckId = repository.createDeck("Failed import")
        assertNotNull(repository.getDeckById(deckId))

        repository.deleteDeckIfEmpty(deckId)

        assertNull(
            "cardless deck must be removed by the rollback guard",
            repository.getDeckById(deckId)
        )
        assertEquals(0, repository.getCardCountForDeck(deckId))
    }

    // --- 5. Counter queries (Home / Statistics) ---------------------------------------------------------------

    @Test
    fun counterQueries_cardCountAndReviewCount_matchPersistedRows() = runBlocking<Unit> {
        val deckA = repository.createDeck("Stats A")
        val deckB = repository.createDeck("Stats B")
        val c1 = repository.insertCard(deckA, "q1", "a1")
        val c2 = repository.insertCard(deckA, "q2", "a2")
        val c3 = repository.insertCard(deckB, "q3", "a3")

        // Per-deck card counts, scoped correctly.
        assertEquals(2, repository.getCardCountForDeck(deckA))
        assertEquals(1, repository.getCardCountForDeck(deckB))
        assertEquals(0, repository.getCardCountForDeck(999_999L))
        assertEquals(3, db.cardDao().getTotalCardCount())

        // No reviews yet.
        assertEquals(0, repository.getTotalReviewCount())

        // Three reviews across two cards — the audit count feeds the Statistics screen.
        repository.processCardReview(c1, Sm2Scheduler.RATING_EASY, reviewedAt = T0)
        repository.processCardReview(c1, Sm2Scheduler.RATING_MEDIUM, reviewedAt = T0 + DAY)
        repository.processCardReview(c2, Sm2Scheduler.RATING_HARD, reviewedAt = T0)

        assertEquals(3, repository.getTotalReviewCount())
        // Reviews are logged rows: they must not disturb the card counts.
        assertEquals(2, repository.getCardCountForDeck(deckA))
        assertEquals(1, repository.getCardCountForDeck(deckB))
        assertEquals(3, db.cardDao().getTotalCardCount())
        // c3 was never reviewed.
        assertEquals(0, db.reviewDao().getReviewsForCard(c3).first().size)
    }

    @Test
    fun perCardReviewLookup_returnsThatCardsLatestReview() = runBlocking<Unit> {
        val deckId = repository.createDeck("Lookup")
        val cardA = repository.insertCard(deckId, "qA", "aA")
        val cardB = repository.insertCard(deckId, "qB", "aB")

        // Two reviews on card A at distinct times, one on card B.
        repository.processCardReview(cardA, Sm2Scheduler.RATING_EASY, reviewedAt = T0)
        repository.processCardReview(cardA, Sm2Scheduler.RATING_EASY, reviewedAt = T0 + DAY)
        repository.processCardReview(cardB, Sm2Scheduler.RATING_HARD, reviewedAt = T0)

        // The query orders by reviewedAt DESC: card A's row must be the *second* review.
        val latestA = db.reviewDao().getReviewByCardId(cardA)
        assertNotNull("lookup must return a row for a reviewed card", latestA)
        assertEquals(cardA, latestA!!.cardId)
        assertEquals(T0 + DAY, latestA.reviewedAt)
        assertEquals(1, latestA.previousInterval) // interval before the second review
        assertEquals(6, latestA.resultingInterval) // second review -> 6 days

        // Card B's row describes its own review (Hard first review: 0 -> 1 day, reps reset).
        val latestB = db.reviewDao().getReviewByCardId(cardB)
        assertNotNull(latestB)
        assertEquals(cardB, latestB!!.cardId)
        assertEquals(T0, latestB.reviewedAt)
        assertEquals(Sm2Scheduler.RATING_HARD, latestB.rating)
        assertEquals(0, latestB.previousInterval)
        assertEquals(1, latestB.resultingInterval)
    }

    // --- 6. v2 documents table round-trip ---------------------------------------------------------------------

    @Test
    fun documentsTable_roundTripsAndDeckIndexFindsTheDecksDocument() = runBlocking<Unit> {
        val deckId = repository.createDeck("PDF deck")
        val otherDeckId = repository.createDeck("No document deck")

        val docId = db.documentDao().insertDocument(
            DocumentEntity(
                deckId = deckId,
                uriString = "content://pdf/lesson1",
                extractedText = "Extracted lesson text",
                createdAt = T0
            )
        )

        // Round-trip by primary key.
        val byId = db.documentDao().getDocumentById(docId)
        assertNotNull("inserted document must be readable", byId)
        assertEquals(docId, byId!!.id)
        assertEquals(deckId, byId.deckId)
        assertEquals("content://pdf/lesson1", byId.uriString)
        assertEquals("Extracted lesson text", byId.extractedText)
        assertEquals(T0, byId.createdAt)

        // deckId lookup (the indexed column) finds exactly this deck's document...
        val forDeck = db.documentDao().getDocumentsByDeckId(deckId).first()
        assertEquals(1, forDeck.size)
        assertEquals(docId, forDeck[0].id)
        assertEquals(deckId, forDeck[0].deckId)

        // ...and no other deck's.
        val forOtherDeck = db.documentDao().getDocumentsByDeckId(otherDeckId).first()
        assertTrue("unrelated deck must have no documents", forOtherDeck.isEmpty())
    }

    private companion object {
        /** Fixed wall-clock base: 2023-11-14T22:13:20Z. */
        const val T0 = 1_700_000_000_000L
        const val DAY = Sm2Scheduler.MILLIS_IN_A_DAY
        const val FLOAT_TOLERANCE = 1e-4
    }
}
