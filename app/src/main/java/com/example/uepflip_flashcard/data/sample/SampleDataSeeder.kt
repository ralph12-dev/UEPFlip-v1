package com.example.uepflip_flashcard.data.sample

import android.content.Context
import android.content.pm.ApplicationInfo
import android.util.Log
import androidx.room.withTransaction
import com.example.uepflip_flashcard.data.local.AppDatabase
import com.example.uepflip_flashcard.data.local.entities.CardEntity
import com.example.uepflip_flashcard.data.local.entities.DeckEntity
import com.example.uepflip_flashcard.data.local.entities.ReviewEntity
import kotlinx.coroutines.flow.first

/**
 * Fills a freshly installed **debug** build with realistic decks, cards and review history so Home,
 * My Decks, Statistics and Study can all be exercised without a Gemini API key.
 *
 * This is developer tooling, not product behaviour:
 *  - nothing happens in a non-debuggable build, so a release build can never seed;
 *  - it only writes when the database is completely empty, so it cannot touch real study data;
 *  - it remembers that it ran, so deleting the sample decks keeps them deleted.
 *
 * Clear the app's data (Settings → Apps → UEPFlip → Storage) to have the samples appear again.
 */
object SampleDataSeeder {

    private const val TAG = "SampleDataSeeder"
    private const val PREFS_NAME = "uepflip_sample_data"
    private const val KEY_SEEDED = "seeded"

    private const val DAY = 86_400_000L

    /**
     * The real SM-2 review steps (see `domain/scheduler/Sm2Scheduler`): one day, then six, then each
     * interval multiplied by the ease factor. Reusing the true sequence means a seeded card keeps
     * scheduling sensibly once the student reviews it for real.
     */
    internal val INTERVAL_STEPS = intArrayOf(0, 1, 6, 15, 37, 92)

    /** Rating cycle used to rebuild review history (1 = Hard, 2 = Normal, 3 = Easy). */
    private val RATING_CYCLE = intArrayOf(3, 2, 2, 1, 3)

    /**
     * Seeds the sample data once, if this is a debug build and the database is still empty.
     * Safe to call on every launch: it returns immediately once it has run.
     */
    suspend fun seedIfFirstRun(context: Context) {
        if (!isDebuggable(context)) return

        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        if (prefs.getBoolean(KEY_SEEDED, false)) return

        try {
            val database = AppDatabase.getDatabase(context)
            if (database.deckDao().getAllDecks().first().isNotEmpty()) {
                // There is already data on this device; never add samples on top of it.
                prefs.edit().putBoolean(KEY_SEEDED, true).apply()
                return
            }

            seed(database)
            prefs.edit().putBoolean(KEY_SEEDED, true).apply()
            Log.i(TAG, "Sample decks seeded for local testing")
        } catch (e: Exception) {
            // Seeding is a convenience: never let it stop the app from starting.
            Log.w(TAG, "Could not seed sample data", e)
        }
    }

    private fun isDebuggable(context: Context): Boolean =
        (context.applicationInfo.flags and ApplicationInfo.FLAG_DEBUGGABLE) != 0

    private suspend fun seed(database: AppDatabase) {
        val deckDao = database.deckDao()
        val cardDao = database.cardDao()
        val reviewDao = database.reviewDao()
        val now = System.currentTimeMillis()

        database.withTransaction {
            SAMPLE_DECKS.forEach { deck ->
                val deckId = deckDao.insertDeck(
                    DeckEntity(
                        title = deck.title,
                        description = deck.description,
                        createdAt = now - deck.createdDaysAgo * DAY,
                        updatedAt = now - deck.updatedDaysAgo * DAY
                    )
                )

                val cardIds = cardDao.insertCards(
                    deck.cards.map { it.toEntity(deckId, deck.createdDaysAgo, now) }
                )

                deck.cards.forEachIndexed { index, card ->
                    card.reviewHistory(now).forEach { review ->
                        reviewDao.insertReview(review.copy(cardId = cardIds[index]))
                    }
                }
            }
        }
    }

    // The sample data and the pure helpers that turn it into entities are `internal` rather than
    // `private` so `SampleDataSeederTest` can assert that the data produces the situations each
    // screen is meant to show.

    internal data class SampleCard(
        val question: String,
        val answer: String,
        /** How many times the card has been reviewed, i.e. its index into [INTERVAL_STEPS]. */
        val stage: Int,
        /** Days since the last review; null when the card has never been reviewed. */
        val reviewedDaysAgo: Int? = null
    )

    internal data class SampleDeck(
        val title: String,
        val description: String,
        val createdDaysAgo: Int,
        val updatedDaysAgo: Int,
        val cards: List<SampleCard>
    )

    internal fun SampleCard.toEntity(deckId: Long, createdDaysAgo: Int, now: Long): CardEntity {
        val interval = INTERVAL_STEPS[stage.coerceIn(0, INTERVAL_STEPS.lastIndex)]
        val lastReviewedAt = reviewedDaysAgo?.let { now - it * DAY } ?: 0L
        return CardEntity(
            deckId = deckId,
            question = question,
            answer = answer,
            repetitions = stage,
            interval = interval,
            easeFactor = 2.5f,
            lastReviewedAt = lastReviewedAt,
            // Never-reviewed cards use 0, which the DAO treats as "due".
            nextReviewAt = if (lastReviewedAt == 0L) 0L else lastReviewedAt + interval * DAY,
            createdAt = now - createdDaysAgo * DAY,
            updatedAt = if (lastReviewedAt == 0L) now - createdDaysAgo * DAY else lastReviewedAt,
            rating = if (stage == 0) 0 else RATING_CYCLE[stage % RATING_CYCLE.size]
        )
    }

    /**
     * Rebuilds the review rows that would have produced this card's current state, so Statistics has
     * real history to read. Timestamps walk backwards from the most recent review.
     */
    internal fun SampleCard.reviewHistory(now: Long): List<ReviewEntity> {
        if (stage <= 0 || reviewedDaysAgo == null) return emptyList()

        var time = now - reviewedDaysAgo * DAY
        val oldestFirst = mutableListOf<Long>()
        for (step in stage downTo 1) {
            oldestFirst += time
            time -= INTERVAL_STEPS[step] * DAY
        }
        oldestFirst.reverse()

        return (1..stage).map { step ->
            ReviewEntity(
                cardId = 0L, // replaced by the caller with the real id
                reviewedAt = oldestFirst[step - 1],
                rating = RATING_CYCLE[step % RATING_CYCLE.size],
                previousInterval = INTERVAL_STEPS[step - 1],
                resultingInterval = INTERVAL_STEPS[step]
            )
        }
    }

    /**
     * Four decks that between them exercise every screen: an untouched deck (everything due, 0%
     * mastery), a partly-mature deck, a mostly-mature deck with reviews in the last week, and an
     * overdue deck so Home shows overdue reviews and its "Review now" button.
     */
    internal val SAMPLE_DECKS = listOf(
        SampleDeck(
            title = "Data Structures & Algorithms",
            description = "Sample deck: complexity, core structures and traversal.",
            createdDaysAgo = 12,
            updatedDaysAgo = 12,
            cards = listOf(
                SampleCard(
                    question = "What is the time complexity of binary search on a sorted array?",
                    answer = "O(log n) — every comparison halves the search space.",
                    stage = 0
                ),
                SampleCard(
                    question = "Which data structure is FIFO?",
                    answer = "A queue — the first item added is the first one removed.",
                    stage = 0
                ),
                SampleCard(
                    question = "Which data structure is LIFO?",
                    answer = "A stack — pushes and pops both happen at the same end.",
                    stage = 0
                ),
                SampleCard(
                    question = "What is a hash collision?",
                    answer = "Two different keys mapping to the same bucket; resolved by chaining or open addressing.",
                    stage = 0
                ),
                SampleCard(
                    question = "What is the cost of inserting at the head of a singly linked list?",
                    answer = "O(1) — you only rewire one pointer.",
                    stage = 0
                ),
                SampleCard(
                    question = "What does breadth-first search use to track the frontier?",
                    answer = "A queue.",
                    stage = 0
                )
            )
        ),
        SampleDeck(
            title = "Networking Fundamentals",
            description = "Sample deck: OSI layers, addressing and the protocols behind the web.",
            createdDaysAgo = 40,
            updatedDaysAgo = 2,
            cards = listOf(
                SampleCard(
                    question = "What does TCP guarantee that UDP does not?",
                    answer = "Ordered, reliable delivery with retransmission; UDP just sends datagrams.",
                    stage = 5,
                    reviewedDaysAgo = 50
                ),
                SampleCard(
                    question = "Which OSI layer does a router work at?",
                    answer = "Layer 3, the network layer.",
                    stage = 4,
                    reviewedDaysAgo = 30
                ),
                SampleCard(
                    question = "What is a subnet mask for?",
                    answer = "It splits an IP address into its network part and its host part.",
                    stage = 3,
                    reviewedDaysAgo = 3
                ),
                SampleCard(
                    question = "Which port does HTTPS use by default?",
                    answer = "443.",
                    stage = 3,
                    reviewedDaysAgo = 3
                ),
                SampleCard(
                    question = "What does DNS resolve?",
                    answer = "Domain names into IP addresses.",
                    stage = 1,
                    reviewedDaysAgo = 6
                ),
                SampleCard(
                    question = "What is a MAC address used for?",
                    answer = "Identifying a network interface within a local network segment.",
                    stage = 2,
                    reviewedDaysAgo = 7
                ),
                SampleCard(
                    question = "How does a switch differ from a hub?",
                    answer = "A hub floods every port; a switch forwards only to the port the destination MAC address is on.",
                    stage = 1,
                    reviewedDaysAgo = 2
                )
            )
        ),
        SampleDeck(
            title = "Philippine History",
            description = "Sample deck: key dates and figures from the revolution to EDSA.",
            createdDaysAgo = 90,
            updatedDaysAgo = 20,
            cards = listOf(
                SampleCard(
                    question = "Who was the first President of the Philippine Republic?",
                    answer = "Emilio Aguinaldo, in 1899.",
                    stage = 5,
                    reviewedDaysAgo = 30
                ),
                SampleCard(
                    question = "When was Philippine independence declared?",
                    answer = "12 June 1898, in Kawit, Cavite.",
                    stage = 5,
                    reviewedDaysAgo = 60
                ),
                SampleCard(
                    question = "What was the Katipunan?",
                    answer = "A revolutionary society founded by Andres Bonifacio in 1892.",
                    stage = 4,
                    reviewedDaysAgo = 25
                ),
                SampleCard(
                    question = "Who wrote Noli Me Tángere?",
                    answer = "José Rizal; it was published in 1887.",
                    stage = 4,
                    reviewedDaysAgo = 15
                ),
                SampleCard(
                    question = "What was the EDSA People Power Revolution?",
                    answer = "The peaceful 1986 uprising that ended the Marcos administration.",
                    stage = 5,
                    reviewedDaysAgo = 5
                ),
                SampleCard(
                    question = "Who was the first woman to become President of the Philippines?",
                    answer = "Corazon Aquino, in 1986.",
                    stage = 2,
                    reviewedDaysAgo = 30
                )
            )
        ),
        SampleDeck(
            title = "General Chemistry",
            description = "Sample deck: atomic structure, bonding and stoichiometry.",
            createdDaysAgo = 60,
            updatedDaysAgo = 25,
            cards = listOf(
                SampleCard(
                    question = "How many protons does a carbon atom have?",
                    answer = "Six.",
                    stage = 3,
                    reviewedDaysAgo = 40
                ),
                SampleCard(
                    question = "What is an ionic bond?",
                    answer = "Electrostatic attraction between a cation and an anion after electrons are transferred.",
                    stage = 2,
                    reviewedDaysAgo = 30
                ),
                SampleCard(
                    question = "What is Avogadro's number?",
                    answer = "6.022 × 10^23 particles per mole.",
                    stage = 2,
                    reviewedDaysAgo = 20
                ),
                SampleCard(
                    question = "What does the pH scale measure?",
                    answer = "The concentration of hydrogen ions; below 7 is acidic, above 7 is basic.",
                    stage = 1,
                    reviewedDaysAgo = 10
                ),
                SampleCard(
                    question = "What is an isotope?",
                    answer = "An atom of the same element with a different number of neutrons.",
                    stage = 3,
                    reviewedDaysAgo = 25
                ),
                SampleCard(
                    question = "In a balanced equation, what must be conserved?",
                    answer = "The number of atoms of each element.",
                    stage = 0
                )
            )
        )
    )
}
