# AI Workforce — Task Board

Authority for **who owns what** and **what is in flight**. `handoff.md` is the checkpoint that
points here; this board holds ownership and dependencies; `logs/task-NNN.md` holds debugging
history. Do not turn `handoff.md` or this file into an activity log.

## Conventions

- **Task IDs:** `T-NNN`, zero-padded, never reused. A new task gets the next free number.
- **Status:** `NOT_STARTED` | `IN_PROGRESS` | `BLOCKED` | `DONE` | `ABANDONED`.
- **Detailed log:** one file per task, `logs/task-NNN.md`, created when the task starts and
  finished before the task is marked `DONE`. Never delete a log; mark it superseded instead.
- **History files are append-only:** `decisions.md` and `test-results.md` only gain entries.
- **One writer:** the `orchestrator` maintains this board and every file in
  `docs/ai-workforce/`. Specialists are denied writes here and report results upward instead.
- **Ownership:** lanes are defined in `AGENTS.md` and enforced by `.opencode/agents/*.md`
  permissions. Never assign two agents to the same file.

## Task board

| ID | Task | Owner | Depends on | Files owned | Status |
|----|------|-------|------------|-------------|--------|
| T-001 | Bootstrap the AI engineering workforce | orchestrator | — | `.opencode/**`, `AGENTS.md` | DONE |
| T-002 | Handoff protocol + task-state split | orchestrator | T-001 | `handoff.md`, `docs/ai-workforce/**`, `AGENTS.md`, `.opencode/**` | DONE |
| T-003a | In-memory Room persistence tests on a device | qa-engineer | T-002 | `app/src/androidTest/**` | DONE |
| T-004 | Configure `gemini_api_key` and verify the live AI flow | ai-engineer | T-002 | `data/service/**`, `ui/document/**` | IN_PROGRESS |
| T-005 | Commit the workforce, docs, handoff files and the `T-003a` tests | orchestrator | T-002 | tracked by Git | DONE |
| T-006 | Real `MIGRATION_1_2` test via `MigrationTestHelper` | data-engineer + orchestrator + qa-engineer | T-003a | see blockers below | NOT_STARTED |
| T-007 | `DocumentEntity` missing FK → documents orphaned on deck delete | data-engineer | T-003a | `data/local/**` | NOT_STARTED |
| T-008 | Test follow-ups raised by the `T-003a` review (production-path rollback, deck-cascade test, `SQLiteConstraintException`, test naming) | qa-engineer | T-003a | `app/src/androidTest/**` | NOT_STARTED |
| T-009 | Retired model id `gemini-2.0-flash` hardcoded → every AI feature returned `404`. Patch makes the model a Remote Config value (`gemini_model`, default `gemini-flash-latest`) with URL-safe validation and `models/`-prefix stripping | ai-engineer | T-004 | `data/service/**`, `README.md` | IN_PROGRESS — applied and **reviewed** (`code-reviewer`, APPROVE WITH NITS, no BLOCKER/HIGH, inspection-only) but **never compiled or tested**; do not promote to DONE without `:app:compileDebugKotlin` + `testDebugUnitTest` |
| T-010 | Unit-test the model-resolution fallback contract (`resolveModel` / `isValidModelId` / `endpointFor`: null, blank, `models/` prefix, junk, over-long, exact URL). Raised by the `T-009` review — the new public surface shipped with zero tests | T-009 | medium | IN_PROGRESS — test file written and orchestrator-inspected, **never compiled or run** (freeze); it is untracked, so `git clean -fd` would delete it |
| T-011 | Two `T-009` review leftovers in `data/service/**`: `GeminiCandidate.finishReason` is parsed and never read, so a `MAX_TOKENS` truncation surfaces as the uninformative "The AI returned an empty response"; and `"$endpoint?key=$apiKey"` interpolates the key unencoded (`&`/`#` would corrupt it) | T-009 | low | NOT_STARTED |
| T-012 | Hint latency: no `generationConfig` was sent, so Gemini 3.x thought at its default level (`medium`/`high`) before producing any text and the non-streaming call returned only at the end. Fix: `thinkingLevel = "low"` for hint/grading/explanation only; flashcards keep the model default; field emitted only for `gemini-3*`/`gemini-4*`/`*-latest`; no `maxOutputTokens` (it counts thought tokens and truncates). Owner: `ai-engineer` (`data/service/**`); tests by `qa-engineer` | T-009 | medium | IN_PROGRESS — applied, inspected, **never compiled or run** |
| T-013 | Two `T-012` review follow-ups: (a) the model→level policy `AiGeneratorProvider.acceptsThinkingLevel()` has no test, so the three wire tests stay green even if it answers wrongly for real ids — extract a pure `internal fun supportsThinkingLevel(model)` to the generator companion and table-test the documented rows; (b) only `generateHint` is asserted to propagate the level, so a dropped argument in `evaluateAnswer`/`generateExplanation` would pass the suite. MEDIUM/LOW, not defects today | T-012 | medium | NOT_STARTED |
| T-014 | Hints are pre-generated with the card and stored so "show hint" is instant. `GeneratedCard.hint` → `CardEntity.hint` (nullable TEXT, Room 2→3 + `MIGRATION_2_3`), persisted by `DocumentImportViewModel` and `TutorViewModel`; `StudyViewModel` renders a stored hint with no network, falls back to `generateHint` + `saveHint` for old/sample cards; `saveHint` is a targeted UPDATE. Owners: `ai-engineer` (`data/service`, `ui/document`), `data-engineer` (`data/local`, `data/repository`), `android-engineer` (`ui/study`, `ui/main`), `qa-engineer` (tests) | T-009 | **high** | IN_PROGRESS — applied, reviewed (APPROVE WITH NITS; the two MEDIUM code gaps were fixed in the tree), **never compiled or run**; migration unverified without a v2→v3 upgrade run |
| T-015 | `T-014` review follow-ups: (a) **MEDIUM** — no test executes `MIGRATION_2_3`; the in-memory DB is created fresh at v3, so only a hand-built v2 file opened through the builder proves the upgrade (and it must be run on a device before release); (b) **MEDIUM** — `saveHint` trim/blank no-op, `insertCard(hint=…)` normalisation, the stored-hint fast path and the mid-request card-change guard are untested; (c) **LOW** — the leak check is not Unicode-normalised, so NFC/NFD spellings of the same accented answer can slip through; (d) **NIT** — the no-space hard cut can split a surrogate pair (emoji) at the 240-char boundary | T-014 | medium | NOT_STARTED |

`T-005` needs explicit user authorization before any commit.

## T-004 / T-009 — live-AI bring-up (current, read this before touching AI code)

- **T-004 is now functionally confirmed by the user:** `gemini_api_key` exists in the Firebase project
  `uepflip-flashcard`, and after installing a build containing `T-009` the user reported live AI
  generation works. The Gradle rows for that build remain `NOT_RUN` here (it was built outside this
  session); one Create / Tutor / Hint run each is still worth recording before `DONE`.
- **T-009 is the `404`:** `AiFlashcardGenerator.DEFAULT_ENDPOINT` hardcoded `gemini-2.0-flash`, which
  Google **shut down on 2026-06-01**, so Create, Tutor and Hint all failed together.
- **T-012 is the slow hint:** the same request carried no `generationConfig`, so the model chose its
  default thinking level (`medium` on 3.6/3.8 Flash, `high` on 3 Flash) and the non-streaming call
  returned nothing until thinking finished. The fix sets `thinkingLevel = "low"` for the three short
  calls only, and never sends the field to a model that rejects it (Gemini 2.x uses `thinkingBudget`).
  Do **not** add `maxOutputTokens` as a latency fix: the API counts thought tokens against it, so a
  small cap returns empty output.
- **T-009 status is `IN_PROGRESS`, not `DONE`, because the patch was never compiled or tested** — the
  user invoked `DO NOT BUILD AND TEST`. It **has** been reviewed (`code-reviewer`, APPROVE WITH NITS,
  no BLOCKER and no HIGH), but that review was inspection-only, exactly like the rest of this session,
  so it does not substitute for a compile. Do not promote `T-009` without running at least
  `:app:compileDebugKotlin` and `testDebugUnitTest`.
- **Next evidence to collect:** the exact Logcat payload under tag `AiFlashcardGenerator`
  (`Gemini request failed with 404: <body>`), which names the rejected model; and
  `curl "https://generativelanguage.googleapis.com/v1beta/models?key=YOUR_KEY"` to list what the key
  can actually serve. Both are outside the repo's test suite.
- **`AiFlashcardGeneratorTest` cannot catch this class of failure:** it injects
  `endpoint = "http://localhost/test"`, so the real model id is never exercised.
- **`T-010` left an untracked, never-compiled test file** in the tree
  (`app/src/test/java/com/example/uepflip_flashcard/data/service/AiModelResolutionTest.kt`, 20 tests).
  Two consequences worth knowing before touching the tree: `git clean -fd` deletes it, and a compile
  error in it would turn `compileDebugUnitTestKotlin` red for everybody. It pins the fallback contract
  and the host/verb of the URL; it is regression insurance, **not** a retirement detector.
- **When the freeze lifts, run in this order:**
  1. `.\gradlew.bat :app:compileDebugKotlin` — the whole patch (`T-009` + `T-012`) is compile-unverified.
  2. `.\gradlew.bat testDebugUnitTest --tests "com.example.uepflip_flashcard.data.service.AiModelResolutionTest"` — fastest signal on the new file.
  3. `.\gradlew.bat testDebugUnitTest --tests "com.example.uepflip_flashcard.data.service.AiFlashcardGeneratorTest"` — covers the three `T-012` thinking-level cases.
  4. `.\gradlew.bat testDebugUnitTest` — the 7 pre-existing classes must stay green; both new files are additive.
  5. `.\gradlew.bat connectedDebugAndroidTest` — only with a device attached; unaffected by this change.

## T-014 — stored hints (read before touching hint code)

- **One request, not two:** the hint is part of the flashcard JSON (`{"question","answer","hint"}`),
  so imports pay no extra call and the hint is already in the DB when the student reaches the card.
- **The invariant:** `hint == null` means "not generated yet"; `""` must never be stored. Any new
  card-creation path must pass `GeneratedCard.hint` into `CardEntity.hint`, or the card silently
  falls back to a network call.
- **Sanitisation lives in the generator**, not the UI: trim, 240-char word-boundary cap, and `null`
  when the hint contains the answer verbatim (answers ≥ 3 chars). A bad hint never rejects the card.
  Paraphrased leaks are not detected — that is the prompt's job.
- **Schema is at v3.** `MIGRATION_2_3` is a single guarded `ALTER TABLE cards ADD COLUMN hint TEXT`.
  Do not add a second column to it; extend with v4. The entity and migration must stay in lockstep
  or Room throws on first open.
- **Never write the hint with a full-row update** (`updateCard`): a concurrent `processCardReview`
  would be clobbered. Use `saveHint` / `updateHint`, which touch only `hint` and `updatedAt`.

## Active-work rules

- Before starting parallel work, the orchestrator writes ownership into this table. Two agents may
  never hold the same file.
- Shared files — Gradle scripts, `AndroidManifest.xml`, `res/values/strings.xml`, `AGENTS.md`,
  `handoff.md`, everything in `docs/ai-workforce/`, `.opencode/**` — are owned by **no** specialist.
- A specialist that hits a cross-lane dependency stops and reports it; the orchestrator schedules a
  follow-up for the owning lane.

## T-006 — known blockers (already derived; do not re-investigate)

A genuine `MIGRATION_1_2` test needs three changes, none of them in `qa-engineer`'s lane:

1. `MIGRATION_1_2` in `AppDatabase` is **private** inside its companion object — unreachable from
   a test. Needs `data-engineer` to expose it (`internal` or a documented accessor).
2. `exportSchema = false` — there is no schema JSON, so `MigrationTestHelper` cannot construct
   the version-1 database to migrate *from*. Needs `data-engineer` to set `exportSchema = true`
   **plus** an orchestrator change to `app/build.gradle.kts`
   (`ksp { arg("room.schemaLocation", "$projectDir/schemas") }` and
   `sourceSets["androidTest"].assets.srcDir("$projectDir/schemas")`).
3. `room-testing` is declared as `testImplementation` only (`gradle/libs.versions.toml` line 34,
   `app/build.gradle.kts` line 73) and nothing on the JVM side can use it without Robolectric.
   The orchestrator must add `androidTestImplementation(libs.androidx.room.testing)`.

Enabling schema export starts committing `app/schemas/**` to the repo — that is an architectural
decision, so it needs a `decisions.md` entry before implementation begins.

## Logs

- `logs/task-001.md` — workforce bootstrap (done).
- `logs/task-002.md` — handoff protocol and task-state split (done).
- `logs/task-003.md` — `T-003a` Room persistence tests (DONE).
- `logs/task-005.md` — commit the workforce and tests (DONE).
- New logs: `logs/task-NNN.md` matching a board row.
