package com.example.uepflip_flashcard.data.model

/**
 * UI-friendly deck model used by DeckAdapter.
 * `id` is the Room primary key (0 if not persisted).
 * `count` is the number of cards in the deck.
 * `mastery` is a 0-100 progress figure derived from review history / SM-2 intervals.
 * `mono` is a 1-2 letter monogram used in the deck item UI.
 * `review` is an optional next-review date string (null if not yet due).
 */
data class Deck(
    val id: Long = 0,
    val name: String,
    val count: Int = 0,
    val mastery: Int = 0,
    val mono: String,
    val review: String? = null
)

/**
 * UI-friendly card model used by StudyFragment (legacy source fragment).
 */
data class StudyCard(
    val id: Long,
    val q: String,
    val a: String,
    val hint: String
)
