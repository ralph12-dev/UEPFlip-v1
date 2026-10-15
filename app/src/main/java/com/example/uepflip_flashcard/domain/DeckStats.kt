package com.example.uepflip_flashcard.domain

import com.example.uepflip_flashcard.data.local.entities.CardEntity
import com.example.uepflip_flashcard.data.model.Deck
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Pure, testable helpers that turn persisted scheduling state into the figures shown in the UI.
 *
 * "Mastery" is defined as the share of a deck's cards that have reached a mature review interval
 * (>= [MATURE_INTERVAL_DAYS] days). It is intentionally derived from real SM-2 state rather than
 * stored separately.
 */
object DeckStats {

    const val MATURE_INTERVAL_DAYS = 21

    private const val MILLIS_IN_A_DAY = 86_400_000L

    /** A card counts as "mature" once its interval has grown to at least three weeks. */
    fun isMature(card: CardEntity): Boolean = card.interval >= MATURE_INTERVAL_DAYS

    /** A card is due when it has never been reviewed (nextReviewAt == 0) or its due date has passed. */
    fun isDue(card: CardEntity, now: Long = System.currentTimeMillis()): Boolean =
        card.nextReviewAt <= now

    /** 0-100 percentage of mature cards in [cards]; 0 for an empty deck. */
    fun masteryPercent(cards: List<CardEntity>): Int {
        if (cards.isEmpty()) return 0
        return cards.count { isMature(it) } * 100 / cards.size
    }

    /** 1-2 letter monogram derived from a deck title, used by the deck list UI. */
    fun monogram(title: String): String {
        val words = title.trim().split(Regex("\\s+")).filter { it.isNotBlank() }
        return when {
            words.isEmpty() -> "?"
            words.size == 1 -> words[0].take(2).uppercase(Locale.getDefault())
            else -> (words[0].first().toString() + words[1].first())
                .uppercase(Locale.getDefault())
        }
    }

    /** Human-readable label for the earliest upcoming review, or null if the deck has no schedule. */
    fun nextReviewLabel(cards: List<CardEntity>, now: Long = System.currentTimeMillis()): String? {
        val next = cards
            .filter { it.nextReviewAt > now }
            .minOfOrNull { it.nextReviewAt }
            ?: return null
        return SimpleDateFormat("MMM d", Locale.getDefault()).format(Date(next))
    }

    /** Builds the UI-facing [Deck] model from a persisted deck plus its cards. */
    fun toUiDeck(
        deckId: Long,
        title: String,
        cards: List<CardEntity>,
        now: Long = System.currentTimeMillis()
    ): Deck = Deck(
        id = deckId,
        name = title,
        count = cards.size,
        mastery = masteryPercent(cards),
        mono = monogram(title),
        review = nextReviewLabel(cards, now)
    )

    fun reviewsLastDays(reviews: List<com.example.uepflip_flashcard.data.local.entities.ReviewEntity>, days: Int, now: Long = System.currentTimeMillis()): Int {
        val cutoff = now - days * MILLIS_IN_A_DAY
        return reviews.count { it.reviewedAt >= cutoff }
    }

    /**
     * Share of reviews that were not rated Hard. Returns null when there is no review history so the
     * UI can show "no data" instead of a fabricated 0%.
     */
    fun recallRatePercent(reviews: List<com.example.uepflip_flashcard.data.local.entities.ReviewEntity>): Int? {
        if (reviews.isEmpty()) return null
        return reviews.count { it.rating >= 2 } * 100 / reviews.size
    }
}
