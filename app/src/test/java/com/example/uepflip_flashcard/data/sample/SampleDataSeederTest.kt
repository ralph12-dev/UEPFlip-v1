package com.example.uepflip_flashcard.data.sample

import com.example.uepflip_flashcard.data.local.entities.CardEntity
import com.example.uepflip_flashcard.data.local.entities.ReviewEntity
import com.example.uepflip_flashcard.domain.DeckStats
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The sample data exists so every screen can be exercised without an AI key, and that only works if
 * it really produces the situations those screens show: cards that are due, cards that are mature,
 * cards scheduled in the future, and review history for Statistics.
 */
class SampleDataSeederTest {

    /** Fixed clock so the derived scheduling state is deterministic. */
    private val now = 5_000_000_000_000L

    private val decks = SampleDataSeeder.SAMPLE_DECKS

    private fun cardsOf(deck: SampleDataSeeder.SampleDeck): List<CardEntity> = with(SampleDataSeeder) {
        deck.cards.map { it.toEntity(deckId = 1L, createdDaysAgo = deck.createdDaysAgo, now = now) }
    }

    private fun allCards(): List<CardEntity> = decks.flatMap { cardsOf(it) }

    private fun allReviews(): List<ReviewEntity> = with(SampleDataSeeder) {
        decks.flatMap { deck -> deck.cards.flatMap { it.reviewHistory(now) } }
    }

    @Test
    fun seedsSeveralDecksEachWithCards() {
        assertTrue("expected a handful of decks", decks.size >= 4)
        assertTrue("every deck must have cards", decks.all { it.cards.isNotEmpty() })
        assertTrue("expected a useful number of cards", allCards().size >= 20)
    }

    @Test
    fun includesCardsThatAreDueSoReviewsAreOffered() {
        assertTrue(
            "Home needs at least one due card",
            allCards().count { DeckStats.isDue(it, now) } > 0
        )
    }

    @Test
    fun includesMatureAndFutureCardsSoMasteryAndNextReviewAreNotEmpty() {
        assertTrue(
            "Statistics needs mature cards",
            allCards().count { DeckStats.isMature(it) } > 0
        )
        assertTrue(
            "My Decks needs a card scheduled in the future",
            allCards().count { it.nextReviewAt > now } > 0
        )
    }

    @Test
    fun includesAFreshDeckSoStudyAndStaleStatesCanBeTriedFromScratch() {
        val cards = cardsOf(decks.first())

        assertTrue("the first deck should be untouched", cards.all { DeckStats.isDue(it, now) })
        assertEquals(0, DeckStats.masteryPercent(cards))
        assertTrue(
            "nothing should look reviewed yet",
            cards.all { it.repetitions == 0 && it.interval == 0 && it.lastReviewedAt == 0L }
        )
    }

    @Test
    fun includesAFullyOverdueDeckSoHomeOffersReviewNow() {
        val overdue = decks.first { it.title == "General Chemistry" }
        val cards = cardsOf(overdue)

        assertTrue("every card should be waiting", cards.all { DeckStats.isDue(it, now) })
        assertTrue(
            "an overdue card must have been reviewed before",
            cards.any { it.lastReviewedAt > 0L }
        )
    }

    @Test
    fun neverReviewedCardsHaveNoSchedulingStateAndNoHistory() {
        with(SampleDataSeeder) {
            decks.flatMap { it.cards }
                .filter { it.stage == 0 }
                .forEach { card ->
                    val entity = card.toEntity(deckId = 1L, createdDaysAgo = 30, now = now)

                    assertEquals(0L, entity.nextReviewAt)
                    assertEquals(0L, entity.lastReviewedAt)
                    assertEquals(0, entity.repetitions)
                    assertEquals(0, entity.interval)
                    assertTrue(card.reviewHistory(now).isEmpty())
                }
        }
    }

    @Test
    fun reviewedCardsCarryAHistoryThatExplainsTheirCurrentInterval() {
        with(SampleDataSeeder) {
            decks.flatMap { deck -> deck.cards.map { deck to it } }
                .filter { (_, card) -> card.stage > 0 }
                .forEach { (deck, card) ->
                    val entity = card.toEntity(1L, deck.createdDaysAgo, now)
                    val history = card.reviewHistory(now)

                    assertEquals("one row per review", card.stage, history.size)
                    assertEquals("history starts from scratch", 0, history.first().previousInterval)
                    assertEquals(
                        "history ends at the card's current interval",
                        entity.interval,
                        history.last().resultingInterval
                    )
                    assertEquals(
                        "the last review is the most recent",
                        entity.lastReviewedAt,
                        history.last().reviewedAt
                    )

                    history.zipWithNext { before, after ->
                        assertTrue(
                            "reviews must be ordered oldest first",
                            before.reviewedAt < after.reviewedAt
                        )
                    }
                    assertTrue("history cannot be in the future", history.all { it.reviewedAt <= now })
                }
        }
    }

    @Test
    fun recentActivityExistsForTheSevenDayFigures() {
        assertTrue(
            "Statistics needs reviews from the last week",
            DeckStats.reviewsLastDays(allReviews(), 7, now) > 0
        )
        assertTrue("Statistics needs a recall rate to show", allReviews().size >= 20)
    }

    @Test
    fun seededSchedulingStateIsInternallyConsistent() {
        allCards().forEach { card ->
            assertEquals(
                "nextReviewAt must follow from the last review and the interval",
                card.lastReviewedAt + card.interval * 86_400_000L,
                card.nextReviewAt
            )
            assertTrue("intervals cannot be negative", card.interval >= 0)
        }
    }
}
