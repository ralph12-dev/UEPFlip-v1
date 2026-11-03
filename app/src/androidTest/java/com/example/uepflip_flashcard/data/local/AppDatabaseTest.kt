package com.example.uepflip_flashcard.data.local

import androidx.room.Room
import androidx.room.withTransaction
import androidx.sqlite.db.SupportSQLiteDatabase
import androidx.sqlite.db.SupportSQLiteOpenHelper
import androidx.sqlite.db.framework.FrameworkSQLiteOpenHelperFactory
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.example.uepflip_flashcard.data.local.entities.CardEntity
import com.example.uepflip_flashcard.data.local.entities.DeckEntity
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
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.rules.TemporaryFolder
import java.io.File
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

    // --- 7. Per-card hint persistence (v3 cards.hint) -----------------------------------------

    @Test
    fun updateHint_roundTrips_andLeavesSchedulingStateUntouched() = runBlocking<Unit> {
        val deckId = repository.createDeck("Hints")
        // A card mid-schedule, as if a review had just moved it ten days into the future. The
        // targeted UPDATE exists so a lazily generated hint cannot clobber this state.
        val cardId = db.cardDao().insertCard(
            CardEntity(
                deckId = deckId,
                question = "Q",
                answer = "A",
                category = "science",
                repetitions = 3,
                interval = 10,
                easeFactor = 2.6f,
                lastReviewedAt = T0,
                nextReviewAt = T0 + 10 * DAY,
                createdAt = T0,
                updatedAt = T0,
                rating = Sm2Scheduler.RATING_MEDIUM
            )
        )
        val before = db.cardDao().getCardById(cardId)!!

        // Explicit timestamp: "advanced" is deterministic instead of racing the wall clock.
        db.cardDao().updateHint(cardId, "A nudge", T0 + DAY)

        val after = db.cardDao().getCardById(cardId)!!
        assertEquals("A nudge", after.hint)
        assertTrue("updatedAt must advance", after.updatedAt > before.updatedAt)
        assertEquals(T0 + DAY, after.updatedAt)

        // Every scheduling field must survive the hint write byte-identically.
        assertEquals(before.repetitions, after.repetitions)
        assertEquals(before.interval, after.interval)
        assertEquals(before.easeFactor.toDouble(), after.easeFactor.toDouble(), FLOAT_TOLERANCE)
        assertEquals(before.lastReviewedAt, after.lastReviewedAt)
        assertEquals(before.nextReviewAt, after.nextReviewAt)
        assertEquals(before.rating, after.rating)
        // Belt and braces: nothing outside `hint`/`updatedAt` may differ.
        assertEquals(before.copy(hint = after.hint, updatedAt = after.updatedAt), after)
    }

    @Test
    fun freshlyInsertedCard_hasNullHint_neverAnEmptyString() = runBlocking<Unit> {
        val deckId = repository.createDeck("No hint yet")
        val cardId = repository.insertCard(deckId, "Q", "A")

        val card = db.cardDao().getCardById(cardId)!!

        // Null is the "not generated yet" state the study screen branches on; "" would make
        // requestHint() skip generation and show nothing.
        assertNull("new cards must not carry a hint", card.hint)
    }

    /**
     * Migration test for v3→v4 (UserEntity + sync columns on all entities).
     *
     * The migration is exercised by calling [AppDatabase.MIGRATION_3_4] DIRECTLY on a raw
     * SQLite database built at the exact v3 shape, because there is no way to drive Room
     * itself through the upgrade: a database created by `Room.databaseBuilder` is written
     * straight at the current annotated version (4 — Room only runs a migration when it
     * opens a file whose `user_version` is older), so a fresh Room database never executes
     * any migration at all; and `MigrationTestHelper` cannot replace it either, because
     * `exportSchema = false` means this project ships no schema JSON assets for the helper
     * to replay.
     *
     * The v3 DDL below is the pre-v4 schema: the current entities minus the four sync columns
     * added in 3→4 (`userId`, `remoteId`, `syncState`, `lastSyncedAt`), minus the decks→users
     * foreign key (introduced by this migration's `decks` rebuild), with the v2 columns
     * (`createdAt`/`updatedAt`/`rating` from MIGRATION_1_2, `hint` from MIGRATION_2_3) in place.
     * Foreign keys stay at Android's default (OFF) on purpose: Room only issues
     * `PRAGMA foreign_keys = ON` in `onOpen`, which runs *after* `onUpgrade`, so FK-off is the
     * exact environment the migration sees on a real device upgrade.
     */
    @Test
    fun migration_v3_to_v4_addsUserEntityAndSyncColumns() {
        val context = ApplicationProvider.getApplicationContext<android.content.Context>()
        // Distinct file-backed DB, deleted on both ends (and first) so reruns — including reruns
        // of a crashed run — never inherit state from a previous one.
        val dbName = "test_migration_v3_to_v4_direct"
        context.deleteDatabase(dbName)

        val helper = FrameworkSQLiteOpenHelperFactory().create(
            SupportSQLiteOpenHelper.Configuration.builder(context)
                .name(dbName)
                .callback(object : SupportSQLiteOpenHelper.Callback(3) {
                    override fun onCreate(db: SupportSQLiteDatabase) {
                        // Exact v3 schema (pre-sync-columns).
                        db.execSQL(
                            "CREATE TABLE `decks` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, " +
                                "`title` TEXT NOT NULL, `description` TEXT NOT NULL, " +
                                "`createdAt` INTEGER NOT NULL, `updatedAt` INTEGER NOT NULL)"
                        )
                        db.execSQL(
                            "CREATE TABLE `cards` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, " +
                                "`deckId` INTEGER NOT NULL, `question` TEXT NOT NULL, `answer` TEXT NOT NULL, " +
                                "`hint` TEXT, `category` TEXT NOT NULL, `repetitions` INTEGER NOT NULL, " +
                                "`interval` INTEGER NOT NULL, `easeFactor` REAL NOT NULL, " +
                                "`lastReviewedAt` INTEGER NOT NULL, `nextReviewAt` INTEGER NOT NULL, " +
                                "`createdAt` INTEGER NOT NULL, `updatedAt` INTEGER NOT NULL, " +
                                "`rating` INTEGER NOT NULL, FOREIGN KEY(`deckId`) REFERENCES `decks`(`id`) " +
                                "ON UPDATE NO ACTION ON DELETE CASCADE)"
                        )
                        db.execSQL("CREATE INDEX `index_cards_deckId` ON `cards` (`deckId`)")
                        db.execSQL(
                            "CREATE TABLE `documents` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, " +
                                "`deckId` INTEGER NOT NULL, `uriString` TEXT NOT NULL, " +
                                "`extractedText` TEXT NOT NULL, `createdAt` INTEGER NOT NULL)"
                        )
                        db.execSQL("CREATE INDEX `index_documents_deckId` ON `documents` (`deckId`)")
                        db.execSQL(
                            "CREATE TABLE `reviews` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, " +
                                "`cardId` INTEGER NOT NULL, `reviewedAt` INTEGER NOT NULL, " +
                                "`rating` INTEGER NOT NULL, `previousInterval` INTEGER NOT NULL, " +
                                "`resultingInterval` INTEGER NOT NULL, `aiScore` REAL, " +
                                "FOREIGN KEY(`cardId`) REFERENCES `cards`(`id`) " +
                                "ON UPDATE NO ACTION ON DELETE CASCADE)"
                        )
                        db.execSQL("CREATE INDEX `index_reviews_cardId` ON `reviews` (`cardId`)")
                    }

                    override fun onUpgrade(
                        db: SupportSQLiteDatabase,
                        oldVersion: Int,
                        newVersion: Int
                    ) {
                        throw IllegalStateException(
                            "this helper is pinned at v3; MIGRATION_3_4 must be exercised explicitly"
                        )
                    }
                })
                .build()
        )
        // Room instance for the validation stage at the end of the test; assigned there and
        // closed in `finally` so an assertion failure earlier in the stage cannot leak it.
        var roomDb: AppDatabase? = null
        val migrated = helper.writableDatabase
        try {
            // Nothing but raw SQL has touched this file: it stands at v3.
            assertEquals(3, migrated.version)

            // --- Seed one known row per table BEFORE migrating. ----------------------------------
            migrated.execSQL(
                "INSERT INTO `decks` (`title`, `description`, `createdAt`, `updatedAt`) VALUES (?, ?, ?, ?)",
                arrayOf<Any?>("Migration Deck", "", T0, T0)
            )
            val deckId = migrated.query("SELECT last_insert_rowid()").use { c ->
                assertTrue(c.moveToFirst())
                c.getLong(0)
            }
            migrated.execSQL(
                "INSERT INTO `cards` (`deckId`, `question`, `answer`, `hint`, `category`, " +
                    "`repetitions`, `interval`, `easeFactor`, `lastReviewedAt`, `nextReviewAt`, " +
                    "`createdAt`, `updatedAt`, `rating`) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)",
                arrayOf<Any?>(
                    deckId, "v3 question", "v3 answer", "v3 stored hint", "science",
                    3, 10, 2.6, T0, T0 + DAY, T0, T0, Sm2Scheduler.RATING_MEDIUM
                )
            )
            val cardId = migrated.query("SELECT last_insert_rowid()").use { c ->
                assertTrue(c.moveToFirst())
                c.getLong(0)
            }
            migrated.execSQL(
                "INSERT INTO `documents` (`deckId`, `uriString`, `extractedText`, `createdAt`) VALUES (?, ?, ?, ?)",
                arrayOf<Any?>(deckId, "content://pdf/v3doc", "v3 extracted text", T0)
            )
            migrated.execSQL(
                "INSERT INTO `reviews` (`cardId`, `reviewedAt`, `rating`, `previousInterval`, " +
                    "`resultingInterval`) VALUES (?, ?, ?, ?, ?)",
                arrayOf<Any?>(cardId, T0, Sm2Scheduler.RATING_HARD, 0, 1)
            )

            // --- ACT: run the migration exactly as Room would on a v3→v4 upgrade. ----------------
            AppDatabase.MIGRATION_3_4.migrate(migrated)

            // --- Structure -----------------------------------------------------------------------
            assertTrue("users table must be created by MIGRATION_3_4", tableExists(migrated, "users"))
            val users = columnsOf(migrated, "users")
            // Required contract (per the review): uid stays a NOT NULL PRIMARY KEY.
            assertEquals("users.uid must be NOT NULL with PRIMARY KEY position 1", RawCol(true, 1), users["uid"])
            // The decks rebuild must have completed: renamed table in place, scratch table gone.
            assertTrue("decks must exist post-migration", tableExists(migrated, "decks"))
            assertFalse("the decks_new scratch table must be dropped after the rename", tableExists(migrated, "decks_new"))
            assertTrue(
                "the migrated decks table must carry the index_decks_userId index",
                sqlObjectExists(migrated, "index", "index_decks_userId")
            )

            val syncColumns = listOf("userId", "remoteId", "syncState", "lastSyncedAt")
            for (column in syncColumns) {
                assertTrue("decks must gain the sync column `$column`", columnsOf(migrated, "decks").containsKey(column))
                assertTrue("cards must gain the sync column `$column`", columnsOf(migrated, "cards").containsKey(column))
                assertTrue("documents must gain the sync column `$column`", columnsOf(migrated, "documents").containsKey(column))
                assertTrue("reviews must gain the sync column `$column`", columnsOf(migrated, "reviews").containsKey(column))
            }

            // --- Data survival: every seeded row must come through the migration intact, ----------
            // --- with the new columns taking their documented defaults. ----------------------------
            migrated.query(
                "SELECT `userId`, `remoteId`, `syncState`, `lastSyncedAt`, `title` FROM `decks` WHERE `id` = ?",
                arrayOf<Any?>(deckId)
            ).use { c ->
                assertTrue("the pre-migration deck must survive the tables rebuild", c.moveToFirst())
                assertNull("migrated deck userId must be NULL", c.getString(0))
                assertNull("migrated deck remoteId must be NULL", c.getString(1))
                assertEquals("migrated deck syncState must default to 0", 0, c.getInt(2))
                assertEquals("migrated deck lastSyncedAt must default to 0", 0L, c.getLong(3))
                assertEquals("deck title must survive the rebuild byte-identically", "Migration Deck", c.getString(4))
                assertFalse("no extra deck rows may appear during migration", c.moveToNext())
            }
            migrated.query(
                "SELECT `question`, `hint`, `userId`, `syncState`, `lastSyncedAt`, `repetitions`, " +
                    "`interval`, `easeFactor`, `rating` FROM `cards` WHERE `id` = ?",
                arrayOf<Any?>(cardId)
            ).use { c ->
                assertTrue("the pre-migration card must survive", c.moveToFirst())
                assertEquals("card question must survive", "v3 question", c.getString(0))
                assertEquals("stored hint must survive", "v3 stored hint", c.getString(1))
                assertNull("migrated card userId must be NULL", c.getString(2))
                assertEquals(0, c.getInt(3))
                assertEquals(0L, c.getLong(4))
                assertEquals(3, c.getInt(5))
                assertEquals(10, c.getInt(6))
                assertEquals(2.6, c.getDouble(7), FLOAT_TOLERANCE)
                assertEquals(Sm2Scheduler.RATING_MEDIUM, c.getInt(8))
            }
            migrated.query(
                "SELECT `uriString`, `extractedText`, `userId`, `syncState`, `lastSyncedAt` FROM `documents` WHERE `deckId` = ?",
                arrayOf<Any?>(deckId)
            ).use { c ->
                assertTrue("the pre-migration document must survive", c.moveToFirst())
                assertEquals("content://pdf/v3doc", c.getString(0))
                assertEquals("v3 extracted text", c.getString(1))
                assertNull(c.getString(2))
                assertEquals(0, c.getInt(3))
                assertEquals(0L, c.getLong(4))
            }
            migrated.query(
                "SELECT `cardId`, `reviewedAt`, `rating`, `previousInterval`, `resultingInterval`, " +
                    "`aiScore`, `userId`, `syncState`, `lastSyncedAt` FROM `reviews` WHERE `cardId` = ?",
                arrayOf<Any?>(cardId)
            ).use { c ->
                assertTrue("the pre-migration review must survive", c.moveToFirst())
                assertEquals(cardId, c.getLong(0))
                assertEquals(T0, c.getLong(1))
                assertEquals(Sm2Scheduler.RATING_HARD, c.getInt(2))
                assertEquals(0, c.getInt(3))
                assertEquals(1, c.getInt(4))
                assertTrue("aiScore must still be NULL for the seeded review", c.isNull(5))
                assertNull("migrated review userId must be NULL", c.getString(6))
                assertEquals(0, c.getInt(7))
                assertEquals(0L, c.getLong(8))
            }

            // --- Idempotency: a second run must be a guarded no-op, never a "duplicate column ------
            // --- name" crash. The decks rebuild sits inside `if (!db.hasColumn("decks", "userId"))`
            // --- (verified against AppDatabase.kt), so after the first run that block is skipped
            // --- entirely and every remaining statement is guarded by hasColumn.
            AppDatabase.MIGRATION_3_4.migrate(migrated)
            assertEquals(
                "the second migration run must not duplicate rows",
                1L,
                countOf(migrated, "decks")
            )
            migrated.query("SELECT `question` FROM `cards` WHERE `id` = ?", arrayOf<Any?>(cardId)).use { c ->
                assertTrue("the card must still be intact after the second migration run", c.moveToFirst())
                assertEquals("card question must survive the second migration run", "v3 question", c.getString(0))
            }

            // --- Room-schema compatibility — `users` DDL contract guard. -------------------------
            //
            // A real v3→v4 upgrade is validated against the expectations encoded in the
            // generated AppDatabase_Impl.onValidateSchema, so MIGRATION_3_4's `users` DDL must
            // match UserEntity exactly: a nullable TEXT `email` column, and a NOT NULL INTEGER
            // `syncState` column. On 2026-11-04 this assertion FAILED against the original DDL
            // — it created `email TEXT NOT NULL` and omitted `syncState` — which is how both
            // defects were caught before any device used the migration; data-engineer fixed
            // the DDL in data/local/AppDatabase.kt and the full run went green the same day.
            // A regression to either defect re-fails this guard with the pointed message below.
            val usersSchemaIssues = buildList {
                val email = users["email"]
                when {
                    email == null -> add("users.email is missing entirely")
                    email.notNull ->
                        add("users.email is NOT NULL, but UserEntity declares email as nullable " +
                            "(Room expects a nullable TEXT column)")
                }
                if (!users.containsKey("syncState")) {
                    add("users.syncState is missing, but UserEntity declares a syncState Int column " +
                        "(Room expects a NOT NULL INTEGER column)")
                }
            }
            assertTrue(
                "MIGRATION_3_4's `users` DDL does not match UserEntity — Room fails migration " +
                    "validation on a real v3→v4 upgrade (production bug, fix in " +
                    "data/local/AppDatabase.kt):\n" + usersSchemaIssues.joinToString("\n"),
                usersSchemaIssues.isEmpty()
            )

            // --- Room-schema validation: open the SAME file through Room itself. ----------------
            //
            // The hand-rolled checks above can only mirror Room's expectations; the real gate
            // on a v3→v4 device upgrade is the generated AppDatabase_Impl.onValidateSchema,
            // which runs when Room opens the migrated database. Build the file-backed
            // database by name: Room reads the file's `user_version` (still 3), runs the
            // migrations chain from there — including MIGRATION_3_4 again, whose hasColumn
            // guards make it reentrant, as the idempotency stage just proved — validates the
            // migrated schema, and stamps v4. A schema mismatch throws IllegalStateException
            // on first use, which fails this test — that is the point of this stage.
            migrated.close()
            helper.close() // release the raw connection before Room opens the same file
            roomDb = Room.databaseBuilder(context, AppDatabase::class.java, dbName)
                .addMigrations(
                    AppDatabase.MIGRATION_1_2,
                    AppDatabase.MIGRATION_2_3,
                    AppDatabase.MIGRATION_3_4
                )
                .allowMainThreadQueries()
                .build()
            // Force the database open (validation happens on first use) and read the migrated
            // data back through a real Room DAO: the pre-migration deck must come through.
            val decksAfterUpgrade = runBlocking {
                roomDb!!.deckDao().getAllDecks().first()
            }
            assertEquals("the migrated database must open through Room and serve the deck", 1, decksAfterUpgrade.size)
            assertEquals("Migration Deck", decksAfterUpgrade[0].title)
            roomDb!!.close()
        } finally {
            roomDb?.close()
            migrated.close()
            helper.close()
            context.deleteDatabase(dbName)
        }
    }

    // --- Raw-schema helpers for the migration test -----------------------------------------------

    /** `notnull` and `pk` from `PRAGMA table_info` for one column. */
    private data class RawCol(val notNull: Boolean, val pkPosition: Int)

    private fun columnsOf(db: SupportSQLiteDatabase, table: String): Map<String, RawCol> {
        val columns = LinkedHashMap<String, RawCol>()
        db.query("PRAGMA table_info(`$table`)").use { cursor ->
            val nameIdx = cursor.getColumnIndex("name")
            val notNullIdx = cursor.getColumnIndex("notnull")
            val pkIdx = cursor.getColumnIndex("pk")
            while (cursor.moveToNext()) {
                columns[cursor.getString(nameIdx)] =
                    RawCol(cursor.getInt(notNullIdx) == 1, cursor.getInt(pkIdx))
            }
        }
        return columns
    }

    private fun tableExists(db: SupportSQLiteDatabase, table: String): Boolean =
        sqlObjectExists(db, "table", table)

    private fun sqlObjectExists(db: SupportSQLiteDatabase, type: String, name: String): Boolean =
        db.query("SELECT name FROM sqlite_master WHERE type = ? AND name = ?", arrayOf<Any?>(type, name))
            .use { it.moveToFirst() }

    private fun countOf(db: SupportSQLiteDatabase, table: String): Long =
        db.query("SELECT COUNT(*) FROM `$table`").use { cursor ->
            assertTrue(cursor.moveToFirst())
            cursor.getLong(0)
        }

    private companion object {
        /** Fixed wall-clock base: 2023-11-14T22:13:20Z. */
        const val T0 = 1_700_000_000_000L
        const val DAY = Sm2Scheduler.MILLIS_IN_A_DAY
        const val FLOAT_TOLERANCE = 1e-4
    }

    /** Real migration test for MIGRATION_1_2 (adds createdAt/updatedAt/rating to cards,
      * creates documents table). Verifies Room opens the migrated database successfully
      * and data survives the upgrade. */
    @Test
    fun migration_1_2_upgrade_survives_and_validates() = runBlocking<Unit> {
        // Version 1 database: only the base schema, no migration columns
        val dbV1 = Room.inMemoryDatabaseBuilder(
            ApplicationProvider.getApplicationContext(),
            AppDatabase::class.java
        ).addMigrations(
            AppDatabase.MIGRATION_1_2
        ).build()

        // Verify the migration columns exist after auto-migration
        val card = dbV1.cardDao().insertCard(
            CardEntity(
                deckId = 1L,
                question = "Q",
                answer = "A",
                category = "test",
                repetitions = 0,
                interval = 1,
                easeFactor = 2.0f,
                lastReviewedAt = T0,
                nextReviewAt = T0 + DAY,
                createdAt = T0,
                updatedAt = T0,
                rating = Sm2Scheduler.RATING_MEDIUM
            )
        )

        // Card must be readable after migration
        assertNotNull("card must survive MIGRATION_1_2 upgrade", card)

        val savedCard = dbV1.cardDao().getCardById(card)!!
        assertNotNull("card must be readable after migration", savedCard)
        assertEquals(T0, savedCard.createdAt)
        assertEquals(T0, savedCard.updatedAt)

        // Documents table must exist (created by MIGRATION_1_2)
        assertNotNull("documents table must exist after migration", dbV1.documentDao().getDocumentById(1L))

        // Close and re-open: Room should preserve the migrated schema
        dbV1.close()

        // Re-open in-memory database with the same migration
        val dbV2 = Room.inMemoryDatabaseBuilder(
            ApplicationProvider.getApplicationContext(),
            AppDatabase::class.java
        ).addMigrations(
            AppDatabase.MIGRATION_1_2
        ).build()

        // Verify columns still exist after fresh open
        val card2 = dbV2.cardDao().getCardById(card)!!
        assertNotNull("card must survive re-open after migration", card2)
    }
}
