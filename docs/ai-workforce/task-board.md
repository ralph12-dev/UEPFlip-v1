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
| T-006 | Real `MIGRATION_1_2` test — blocked by `MIGRATION_1_2` being private, `exportSchema = false`, and missing `androidTestImplementation(room-testing)`; spans `data-engineer` + orchestrator + qa-engineer | T-003a | `data/local/**`, `app/src/androidTest/**` | NOT_STARTED |
| T-007 | `DocumentEntity` declares an index on `deckId` but **no foreign key** (its KDoc falsely claims one), so deleting a deck orphans its documents including imported PDFs' full `extractedText` — silent storage leak. Found by the `T-003a` review. Fix = FK + CASCADE = Room schema 2→3 with a row-preserving migration, or the cheap interim: correct the KDoc and delete documents by `deckId` in the deck-delete path | data-engineer | T-003a | `data/local/**` | NOT_STARTED |
| T-008 | Test follow-ups from the `T-003a` review: the repository's own `withTransaction` in `processCardReview` is still untested (the rollback test hand-rolls its own), no deck-deletion cascade test, `catch (t: Throwable)` should be `SQLiteConstraintException`, and one test name overstates what it asserts | qa-engineer | T-003a | `app/src/androidTest/**` | NOT_STARTED |
| T-009 | Retired model id `gemini-2.0-flash` hardcoded in `AiFlashcardGenerator.DEFAULT_ENDPOINT` → every AI feature returns `404` (Google shut the model down 2026-06-01). Patch: `DEFAULT_MODEL = "gemini-flash-latest"`, `resolveModel()` / `isValidModelId()` / `endpointFor()` with validation and `models/` prefix stripping, optional Remote Config override `gemini_model`, API key `.trim()`ed, provider logs the model actually in use | ai-engineer | T-004 | `data/service/**` | DONE |
| T-010 | Unit-test the model-resolution fallback contract (`resolveModel` / `isValidModelId` / `endpointFor`: null, blank, `models/` prefix, junk, 64-vs-65 chars, exact URL). Raised by the `T-009` review — the new public surface shipped with zero tests | qa-engineer | T-009 | `app/src/test/.../AiModelResolutionTest.kt` | DONE |
| T-011 | Two `T-009` review leftovers in `data/service/**`: `GeminiCandidate.finishReason` is parsed and never read, so a `MAX_TOKENS` truncation surfaces as the uninformative "The AI returned an empty response"; and `"$endpoint?key=$apiKey"` interpolates the key unencoded (`&`/`#` would corrupt it) | ai-engineer | T-009 | `data/service/**` | NOT_STARTED |
| T-012 | Hint latency: the request sent no `generationConfig`, so Gemini 3.x thought at its default level (`medium`/`high`) before writing anything and the non-streaming call returned only at the end. Fix: `fastThinkingLevel` + `generationConfig.thinkingConfig.thinkingLevel = "low"` for hint/grading/explanation only; `generateFlashcards` keeps the model default; `AiGeneratorProvider` emits the field only for `gemini-3*`/`gemini-4*`/`*-latest` (Gemini 2.x rejects it); no `maxOutputTokens` because it counts thought tokens and truncates to empty. Three wire tests added to `AiFlashcardGeneratorTest` | ai-engineer | T-009 | `data/service/**`, `app/src/test/**` | DONE |
| T-013 | `T-012` review follow-ups: (a) MEDIUM — `AiGeneratorProvider.acceptsThinkingLevel()` is the only thing that can turn a working hint into a 400, and no test covers the model→level policy; extract a pure `internal fun supportsThinkingLevel(model)` and table-test the documented rows; (b) LOW — only `generateHint` is asserted to pass the level, so a dropped argument in `evaluateAnswer`/`generateExplanation` would pass | ai-engineer | T-012 | `data/service/**` | NOT_STARTED |
| T-014 | Hints are pre-generated in the same call as the card and stored, so "show hint" is instant instead of a multi-second request. `GeneratedCard.hint` (sanitised: trim, 240-char word-boundary cap, null when it leaks the answer verbatim) → `CardEntity.hint` (nullable TEXT, Room **2→3** + `MIGRATION_2_3`); import and Tutor paths persist it; `StudyViewModel` shows a stored hint with no network and lazily generates + `saveHint`s for old cards/sample data; `saveHint` is a targeted UPDATE so it cannot clobber scheduling state | ai-engineer | T-009 | `data/service/**`, `data/local/**`, `ui/document/**`, `ui/study/**` | DONE |
| T-015 | Migration test for `MIGRATION_2_3` (v2→v3 hint column) — blocked by same cross-lane issues as T-006; a real v2 database upgraded on device is the only proof | data-engineer + qa-engineer | T-014 | `app/src/androidTest/**` | NOT_STARTED |
| T-016 | Full designer UI merge — My Profile, Settings, Notifications, About, setup flow, redesigned Home/Stats/Decks; `LoginFragment` wired | orchestrator + android-engineer + ui-engineer | T-005 | `ui/main/**`, `res/**` | DONE |
| T-017 | Add Firebase Auth, Google Play Services Auth, WorkManager, and Realtime Database dependencies | orchestrator | — | `gradle/libs.versions.toml`, `app/build.gradle.kts` | DONE |
| T-018 | Create UserEntity and extend Room entities with userId, remoteId, syncState, lastSyncedAt | data-engineer | T-017 | `data/local/entities/*.kt`, `data/local/dao/*.kt` | DONE |
| T-019 | Write Room migration v3 to v4 with hasColumn guards | data-engineer | T-018 | `data/local/AppDatabase.kt` | DONE |
| T-020 | Implement AuthRepository (Google Sign-In, Firebase auth state Flow, sign-out) | android-engineer | T-017 | `data/repository/AuthRepository.kt` | DONE |
| T-021 | Implement SyncEngine (WorkManager sync worker, push local, pull remote, conflict resolution) | android-engineer | T-018, T-020 | `data/repository/SyncEngine.kt` | DONE |
| T-022 | Wire LoginFragment into fragment shell and update navigation flow | android-engineer | T-020 | `ui/main/LoginFragment.kt`, `ui/main/MainActivity.kt`, `ui/main/OnboardingFlow.kt` | DONE |
| T-023 | Update MyProfileFragment to show user account state, photo, and sign-out button | android-engineer | T-020 | `ui/main/MyProfileFragment.kt`, `res/layout/screen_my_profile.xml` | DONE |
| T-024 | Update SettingsFragment with account section and sync triggers | android-engineer | T-020 | `ui/main/SettingsFragment.kt` | DONE |
| T-025 | Create Login screen layout and string resources adapting screen_login mockup | ui-engineer | — | `res/layout/fragment_login.xml`, `res/values/strings_auth.xml` | DONE |
| T-026 | Merge guest decks and cards to remote account on initial Google Sign-In | android-engineer | T-021 | `data/repository/AuthRepository.kt`, `data/repository/SyncEngine.kt`, `data/repository/FlashcardRepository.kt` | NOT_STARTED |
| T-027 | Fix LoginFragment crash, navigation, copy and auth flow | android-engineer + ui-engineer | T-022, T-025 | `ui/main/LoginFragment.kt`, `ui/main/MainActivity.kt`, `ui/main/OnboardingFlow.kt`, `data/repository/AuthRepository.kt`, `res/layout/fragment_login.xml`, `res/values/strings_auth.xml` | DONE |
| T-032 | Make Google Sign-In awaitable in AuthRepository (fix NPE from callback race) | android-engineer | T-027 | `data/repository/AuthRepository.kt` | DONE |
| T-033 | Expose Room migrations as `internal` for `MigrationTestHelper` + add `room-testing` dependency. NOTE: `app/build.gradle.kts` is a shared file, edited by the orchestrator, not this lane | data-engineer (AppDatabase.kt) + orchestrator (Gradle) | T-019 | `data/local/AppDatabase.kt`, `app/build.gradle.kts` | DONE |
| T-034 | Migration test v3→v4 (UserEntity + userId/remoteId/syncState/lastSyncedAt columns) in `AppDatabaseTest.migration_v3_to_v4_addsUserEntityAndSyncColumns`. Builds a v3-shape SQLite db, runs `MIGRATION_3_4.migrate()` directly, asserts schema, data survival, idempotency, and a Room open. **PASSED on device (TECNO CM7 API 36), 13/13 AppDatabaseTest green** | qa-engineer | T-033 | `app/src/androidTest/**` | DONE |
| T-028 | Unit tests for AuthRepository | qa-engineer | T-020 | `app/src/test/.../AuthRepositoryTest.kt` | NOT_STARTED |
| T-029 | Instrumented tests for SyncEngine | qa-engineer | T-021 | `app/src/androidTest/.../SyncEngineTest.kt` | NOT_STARTED |
| T-030 | Integration tests for authentication and data sync loop | qa-engineer | T-022 | `app/src/androidTest/.../AuthSyncIntegrationTest.kt` | NOT_STARTED |
