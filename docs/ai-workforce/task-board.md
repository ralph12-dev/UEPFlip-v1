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
| T-006 | Real `MIGRATION_1_2` test — blocked by `MIGRATION_1_2` being private, `exportSchema = false`, and missing `androidTestImplementation(room-testing)`; spans `data-engineer` + orchestrator + qa-engineer | T-003a | medium | NOT_STARTED |
| T-007 | `DocumentEntity` declares an index on `deckId` but **no foreign key** (its KDoc falsely claims one), so deleting a deck orphans its documents including imported PDFs' full `extractedText` — silent storage leak. Found by the `T-003a` review. Fix = FK + CASCADE = Room schema 2→3 with a row-preserving migration, or the cheap interim: correct the KDoc and delete documents by `deckId` in the deck-delete path | T-003a | medium | NOT_STARTED |
| T-008 | Test follow-ups from the `T-003a` review: the repository's own `withTransaction` in `processCardReview` is still untested (the rollback test hand-rolls its own), no deck-deletion cascade test, `catch (t: Throwable)` should be `SQLiteConstraintException`, and one test name overstates what it asserts | T-003a | low | NOT_STARTED |
| T-017 | Add Firebase Auth, Google Play Services Auth, WorkManager, and Realtime Database dependencies | orchestrator | — | `gradle/libs.versions.toml`, `app/build.gradle.kts` | DONE |
| T-018 | Create UserEntity and extend Room entities with userId, remoteId, syncState, lastSyncedAt | data-engineer | T-017 | `data/local/entities/*.kt`, `data/local/dao/*.kt` | DONE |
| T-019 | Write Room migration v3 to v4 with hasColumn guards | data-engineer | T-018 | `data/local/AppDatabase.kt` | DONE |
| T-020 | Implement AuthRepository (Google Sign-In, Firebase auth state Flow, sign-out) | android-engineer | T-017 | `data/repository/AuthRepository.kt` | DONE |
| T-021 | Implement SyncEngine (WorkManager sync worker, push local, pull remote, conflict resolution) | android-engineer | T-018, T-020 | `data/repository/SyncEngine.kt` | DONE |
| T-022 | Wire LoginFragment into fragment shell and update navigation flow | android-engineer | T-020 | `ui/main/LoginFragment.kt`, `ui/main/MainActivity.kt`, `ui/main/OnboardingFlow.kt` | DONE |
| T-023 | Update MyProfileFragment to show user account state, photo, and sign-out button | android-engineer | T-020 | `ui/main/MyProfileFragment.kt`, `res/layout/screen_my_profile.xml` | DONE |
| T-024 | Update SettingsFragment with account section and sync triggers | android-engineer | T-020 | `ui/main/SettingsFragment.kt` | DONE |
| T-025 | Create Login screen layout and string resources adapting screen_login mockup | ui-engineer | - | `res/layout/fragment_login.xml`, `res/values/strings_auth.xml` | DONE |
| T-026 | Merge guest decks and cards to remote account on initial Google Sign-In | android-engineer | T-021 | `data/repository/AuthRepository.kt`, `data/repository/SyncEngine.kt`, `data/repository/FlashcardRepository.kt` | NOT_STARTED |
| T-027 | Fix LoginFragment crash, navigation, copy and auth flow | android-engineer + ui-engineer | T-022, T-025 | `ui/main/LoginFragment.kt`, `ui/main/MainActivity.kt`, `ui/main/OnboardingFlow.kt`, `data/repository/AuthRepository.kt`, `res/layout/fragment_login.xml`, `res/values/strings_auth.xml` | DONE |
| T-028 | Unit tests for AuthRepository | qa-engineer | T-020 | `app/src/test/.../AuthRepositoryTest.kt` | NOT_STARTED |
| T-028 | Instrumented tests for SyncEngine | qa-engineer | T-021 | `app/src/androidTest/.../SyncEngineTest.kt` | NOT_STARTED |
| T-029 | Integration tests for authentication and data sync loop | qa-engineer | T-022 | `app/src/androidTest/.../AuthSyncIntegrationTest.kt` | NOT_STARTED |

## 7. Technical Decisions

Full log with alternatives: `docs/ai-workforce/decisions.md`. Summary:

- **Lanes are directory-based, disjoint** — `android-engineer` owns `domain/**`, `ui/main/**`, `ui/study/**`; `ui-engineer` owns `res/**` only. A "layout plus its Kotlin" change is a deliberate two-lane handoff.
- **OpenCode V2 `permissions` rule lists**, never legacy fields.
- **Rules ordered `deny` first, then narrow `allow`** (V2 evaluates last match-wins).
- **Agents inherit the parent session model** — no `model:` field, so a provider/model change cannot break the team.
- **`orchestrator` is the sole handoff maintainer**; the six specialists are explicitly denied writes to `handoff.md` and `docs/ai-workforce/**`.
- **State split:** `handoff.md` holds current state and points to `docs/ai-workforce/`, which holds the board, decisions, test log, and per-task logs.

## Logs
- `logs/task-016.md` - full designer UI merge (DONE).
- `logs/task-001.md` — workforce bootstrap (done).
- `logs/task-002.md` — handoff protocol and task-state split (done).
- `logs/task-003.md` — `T-003a` Room persistence tests (DONE).
- `logs/task-005.md` — commit the workforce and tests (DONE).
- New logs: `logs/task-NNN.md` matching a board row.