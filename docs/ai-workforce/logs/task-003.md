# T-003a — In-memory Room persistence tests on a device

- **Status:** DONE (with concerns recorded — see **Coverage limits**)
- **Owner:** `qa-engineer`, delegating upward to `orchestrator`
- **Date:** 2026-10-19, 00:57 → 01:17 (+08:00)
- **Depends on:** `T-002`
- **Delegation:** background subagent session `ses_eb00b17d5ffeGZAabNTa87QHjs`
- **Review:** `code-reviewer`, session `ses_eaffb45afffeKTTJ8i7jHQUWbj` — **APPROVE WITH CONCERNS**
- **Follow-ups raised:** `T-007` (production), `T-008` (tests)
- **Related decision:** `decisions.md` D-010, D-011

## Objective

Close the largest coverage gap in the repo: **Room had zero test coverage.** The JVM suite covers
pure SM-2 maths, review field mapping, deck stats, AI generation and seeding — nothing opened a
database. For an offline-first app that is the highest-risk layer.

## Outcome

One new file, **`app/src/androidTest/java/com/example/uepflip_flashcard/data/local/AppDatabaseTest.kt`**
— 10 tests, all passing on a real device. Zero production, Gradle, manifest or resource changes:
`FlashcardRepository`'s constructor takes an `AppDatabase`, so an in-memory database could be
injected directly. That is also what kept the whole task inside `qa-engineer`'s lane.

## Verification (orchestrator re-ran everything)

| Command | Result |
|---|---|
| `.\\gradlew.bat connectedDebugAndroidTest --console=plain` (agent) | `BUILD SUCCESSFUL in 42s` — `AppDatabaseTest tests="10" failures="0" errors="0"`, `ExampleInstrumentedTest tests="1"` |
| `.\\gradlew.bat connectedDebugAndroidTest testDebugUnitTest --console=plain --rerun` (orchestrator) | `BUILD SUCCESSFUL in 26s`, exit 0 — reproducible, not a one-off green |
| tally of `app\build\test-results\testDebugUnitTest\*.xml` (orchestrator) | **57 tests / 7 suites / 0 failures / 0 errors / 0 skipped** |
| `git status --short app/src/main` | **empty** — lane boundary held |
| `git status --short` | only addition `?? app/src/androidTest/.../data/` |

Device: `emulator-5554`, Pixel_9 AVD, **API 36 / Android 16** (confirmed via
`adb shell getprop ro.build.version.sdk` and the report filename).

## Tests

| # | Test | Invariant protected |
|---|---|---|
| 1 | `reviewDoubleWrite_persistsAuditRowAndUpdatedCardTogether` | the audit row and the card's scheduling fields are written together; `review.resultingInterval == persisted card.interval` |
| 2 | `reviewOfUnknownCard_isNoOp_noRowsWritten` | guard branch of `processCardReview` — missing card writes zero rows, no crash |
| 3 | `failingSecondWrite_rollsBackFirstWriteInsideTransaction` | FK enforcement is **on**, and Room's `withTransaction` rolls back (see limits below) |
| 4 | `intervalProgression_threeEasyReviews_persists1dayThen6daysThenScaled` | SM-2 steps **as persisted**: interval `0→1→6→17`, reps `0→1→2→3`, EF `2.5→2.6→2.7→2.8` |
| 5 | `dueCardsForDeck_returnsPastAndFresh_excludesFuture_andStaysInsideDeck` | time filtering plus deck scoping |
| 6 | `deleteDeckIfEmpty_onDeckWithCards_keepsDeckAndItsCards` | the AI-import guard is non-destructive |
| 7 | `deleteDeckIfEmpty_onDeckWithoutCards_deletesTheDeck` | the guard's other branch actually fires |
| 8 | `counterQueries_cardCountAndReviewCount_matchPersistedRows` | the Home / Statistics numbers |
| 9 | `perCardReviewLookup_returnsThatCardsLatestReview` | `ORDER BY reviewedAt DESC` returns the latest |
| 10 | `documentsTable_roundTripsAndDeckIndexFindsTheDecksDocument` | the v2 `documents` table works and is deck-scoped |

Tests 6 and 7 are mutually guarding: inverting the guard fails one of them.

## Coverage limits — do not over-read these tests

**The repository's own review transaction is NOT covered.** Test 3 hand-rolls its own
`db.withTransaction { }` around two DAO inserts. A mutant that deleted `database.withTransaction`
from `FlashcardRepository.processCardReview` (lines 137-140) **passes all 10 tests**, because the
happy path works fine without a transaction. What test 3 genuinely proves is still worth having —
FK enforcement is live, and Room's `withTransaction` rolls back — but closing this gap needs fault
injection that does not exist yet. Deferred to `T-008` (decision D-010).

Also not covered: the 1→2 migration (blocked, `T-006`), Flow re-emission over time (the tests use
`first()` snapshots), concurrent writers, and real-file (non-in-memory) database behaviour.

## Findings from the review

- **`MEDIUM` — `DocumentEntity` has no foreign key.** `DocumentEntity.kt:9,14-17` declares
  `Index("deckId")` and a KDoc that *claims* "Foreign key to the DeckEntity"; there is none. Deck
  deletion cascades cards→reviews (both FKs exist) but leaves `documents` rows orphaned, including
  the full `extractedText` of imported PDFs — a silent, permanent storage leak. Not user-visible:
  every query is deck-scoped or by PK, and `DocumentImportViewModel` deletes its own rows on
  failure paths. Pre-existing production issue, **not** a defect of this diff → `T-007` (D-011).
- **`LOW` — `ReviewDao.getReviewByCardId` has no `LIMIT 1`.** Non-issue today: Room reads the
  first cursor row only, and the ORDER BY makes it the latest — which test 9 pins. No production
  caller exists. Add `LIMIT 1` as hygiene when it gains one.
- **`LOW` — test 10's name overstates.** It asserts query *results*, which still pass if the index
  were dropped (index presence needs `EXPLAIN`). Rename-level nit.
- **`LOW` — the `OR nextReviewAt = 0` branch is untestable and effectively dead.** `0 <= currentTime`
  holds for any non-negative `currentTime`, so test 5 passes even if the clause is deleted.
- **`LOW` — deck deletion has no test at all.** Adding one would make the orphaning above visible
  to the suite → `T-008`.
- **`NIT` — `catch (t: Throwable)` at line 142** swallows `CancellationException` and unrelated
  failures, then reports them as an FK violation. `SQLiteConstraintException` would make a
  false-green impossible.
- **`NIT` — `deleteDeckIfEmpty` is count-then-delete with no transaction** (pre-existing,
  `FlashcardRepository.kt:59-63`). Safe under today's single-threaded cleanup, not atomic.

The reviewer found **no tautologies**: every test reads persisted state back through DAOs, and the
interval test pins literal values (`17`) independent of the formula. It also checked determinism:
DB built in `@Before`, closed in `@After`, fresh in-memory instance per test, the singleton bypassed,
fixed `T0` clock for every time-sensitive assertion. No flake vector identified.

## What the reviewer could not verify

It is read-only with a git-only shell allowlist, so it could not run Gradle — it said so rather
than implying otherwise, and instead read and tallied both report-XML sets itself, matching the
numbers above. It also could not confirm API 36 from the XML (which carries no SDK property); the
orchestrator had already confirmed it via `adb`.

## Open items

- `T-006` — real migration test (blocked by three cross-lane changes; blockers are documented in
  `task-board.md`, do not re-derive them).
- `T-007` — `DocumentEntity` missing FK.
- `T-008` — production-path rollback coverage, deck-cascade test, `SQLiteConstraintException`, and
  the test-naming nit.
