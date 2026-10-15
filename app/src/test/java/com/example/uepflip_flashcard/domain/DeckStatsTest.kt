package com.example.uepflip_flashcard.domain

import com.example.uepflip_flashcard.data.local.entities.CardEntity
import com.example.uepflip_flashcard.data.local.entities.ReviewEntity
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class DeckStatsTest {

    private val now = 1_700_000_000_000L

    private fun card(interval: Int = 0, nextReviewAt: Long = 0L) = CardEntity(
        deckId = 1L,
        question = "q",
        answer = "a",
        interval = interval,
        nextReviewAt = nextReviewAt
    )

    private fun review(rating: Int, reviewedAt: Long = now) = ReviewEntity(
        cardId = 1L,
        rating = rating,
        reviewedAt = reviewedAt,
        previousInterval = 0,
        resultingInterval = 1
    )

    @Test
    fun masteryIsZeroForAnEmptyDeck() {
        assertEquals(0, DeckStats.masteryPercent(emptyList()))
    }

    @Test
    fun masteryCountsOnlyMatureCards() {
        val cards = listOf(
            card(interval = 30),  // mature
            card(interval = 21),  // mature (boundary)
            card(interval = 6),   // learning
            card(interval = 0)    // new
        )
        assertEquals(50, DeckStats.masteryPercent(cards))
    }

    @Test
    fun newAndOverdueCardsCountAsDue() {
        val neverReviewed = card(nextReviewAt = 0L)
        val overdue = card(nextReviewAt = now - 1)
        val future = card(nextReviewAt = now + 1)

        assertEquals(true, DeckStats.isDue(neverReviewed, now))
        assertEquals(true, DeckStats.isDue(overdue, now))
        assertEquals(false, DeckStats.isDue(future, now))
    }

    @Test
    fun monogramUsesInitialsOfFirstTwoWords() {
        assertEquals("UR", DeckStats.monogram("UEP Review"))
        assertEquals("BI", DeckStats.monogram("Biology"))
        assertEquals("?", DeckStats.monogram("   "))
    }

    @Test
    fun nextReviewLabelIsNullWhenNothingScheduledInFuture() {
        assertNull(DeckStats.nextReviewLabel(listOf(card(nextReviewAt = 0L)), now))
    }

    @Test
    fun nextReviewLabelReturnsEarliestFutureReview() {
        val cards = listOf(
            card(nextReviewAt = now + 2 * 86_400_000L),
            card(nextReviewAt = now + 86_400_000L)
        )
        val label = DeckStats.nextReviewLabel(cards, now)
        assertEquals(true, label != null && label.isNotBlank())
    }

    @Test
    fun recallRateIsNullWithoutHistory() {
        assertNull(DeckStats.recallRatePercent(emptyList()))
    }

    @Test
    fun recallRateExcludesHardRatings() {
        val reviews = listOf(
            review(1), // Hard -> failure
            review(2), // Medium
            review(3), // Easy
            review(2)  // Medium
        )
        assertEquals(75, DeckStats.recallRatePercent(reviews))
    }

    @Test
    fun reviewsLastDaysOnlyCountsRecentReviews() {
        val day = 86_400_000L
        val reviews = listOf(
            review(2, reviewedAt = now),
            review(2, reviewedAt = now - 3 * day),
            review(2, reviewedAt = now - 30 * day)
        )
        assertEquals(2, DeckStats.reviewsLastDays(reviews, days = 7, now = now))
    }
}
