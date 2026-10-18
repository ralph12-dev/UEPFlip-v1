# Project Handoff

Durable execution record for UEPflip. This file is the **current state only** — detailed history
lives in [`docs/ai-workforce/`](docs/ai-workforce/task-board.md). Written and maintained by the
`orchestrator` (see **Handoff Protocol** in `AGENTS.md`). A checkpoint, not proof: verify every
claim against the repository before acting on it.

## 1. Project Objective

- **Project:** UEPflip — offline-first Android flashcard app. Package / namespace / appId
  `com.example.uepflip_flashcard`. Single Gradle module `:app`.
- **Platform:** Android. Kotlin 2.3.21, AGP 9.4.1, Gradle wrapper 9.6.0, KSP 2.3.9, Room 2.8.5,
  Java 11 source/target, JDK 25 daemon, `compileSdk`/`targetSdk` 37, `minSdk` 26.
- **Primary goal:** SM-2 spaced repetition + AI-assisted card generation, all offline-first, with
  Gemini used only through Firebase Remote Config.
- **Current milestone:** the AI engineering workforce is bootstrapped and verified. Application
  feature work is on hold pending a delegated task.
- **Definition of done:** requirements implemented **and** relevant tests run **and** changes
  reviewed by `code-reviewer`. A successful build alone does not prove functional correctness.

## 2. Current Status

- **Overall status:** `IN_PROGRESS`
- **Last updated:** 2026-10-19 01:43 (+08:00) — immediately after `T-005` (both commits landed).
- **Current phase:** the workforce is complete **and committed**. `T-001`, `T-002`, `T-003a` and
  `T-005` are `DONE`. Nothing is in flight. `main` is at `e106f30`, the tree is clean, and no push
  has been made.
- **Last verified build:** `.\gradlew.bat assembleDebug` → `BUILD SUCCESSFUL in 9s` (2026-10-19)
- **Last verified tests:** `.\gradlew.bat testDebugUnitTest` → PASS at 00:45 on 2026-10-19
  (30/30 up-to-date, exit 0, JVM suite). `.\gradlew.bat connectedDebugAndroidTest` → PASS at 00:57
  on 2026-10-19 on `emulator-5554` (Pixel_9, API 36) — but that run covered only the pre-existing
  `ExampleInstrumentedTest`; at 01:16 the orchestrator forced a fresh run of both suites — **11 instrumented tests** (10 new in
  `AppDatabaseTest`) and **57 JVM tests / 7 suites**, 0 failures across both.

## 3. Architecture and Constraints

- **Existing architecture:** MVVM + Repository over Room. `data/local` (entities, DAOs,
  `AppDatabase`), `data/repository/FlashcardRepository`, `domain/scheduler` (SM-2),
  `domain/DeckStats`, `data/service` (AI), `ui/*` (screens). `AGENTS.md` is the authoritative
  description — verify it still matches the code.
- **UI framework:** Kotlin + ViewBinding + Material 3, **light-only**
  (`Theme.Material3.Light.NoActionBar`, no `res/values-night/`). No Compose.
- **Navigation:** one fragment shell in `ui/main` — `MainActivity` hosts Home / Decks / Stats /
  Tutor tabs plus pushed `CreateFragment` and `StudyFragment`. Bottom-bar visibility is derived
  from the `NavDestination` marker, never assigned at the call site.
- **Persistence:** Room 2.8.5, `AppDatabase` version 2, `exportSchema = false`, hand-written
  `MIGRATION_1_2`. Multi-table writes run in `withTransaction` inside the repository.
- **Network/API integrations:** Gemini `generateContent` via `data/service/AiFlashcardGenerator`
  (OkHttp + Moshi), API key from Firebase Remote Config key `gemini_api_key`; Apache PDFBox 2.0.24
  for on-device PDF extraction; Firebase Remote Config for key resolution.
- **Minimum Android SDK:** 26.
- **Required design conventions:** the 13 `res/layout/` files match a designer's reference; all
  user-facing copy in `res/values/strings.xml`; UI label "Normal" for `Sm2Scheduler.RATING_MEDIUM`.
- **Constraints that must not be violated:**
  - Pinned toolchain; dependency changes need justification.
  - Room schema change ⇒ version bump **plus** migration. Never
    `fallbackToDestructiveMigration()`.
  - No fragment-ktx (`by viewModels {}` unavailable), no Hilt/Koin.
  - No hardcoded secrets; `google-services.json` stays committed.
  - Specialists may not write outside their lane or into `handoff.md` / `docs/ai-workforce/**`.

## 4. Completed Work

### 4.1 AI engineering workforce (task `T-001`)

- **Feature:** a multi-agent OpenCode workforce with enforced file ownership.
- **Implementation:** 7 agent files under `.opencode/agents/` (1 primary `orchestrator`, 6
  subagents), global guardrails in `.opencode/opencode.jsonc`, a `/handoff` command, and the
  **Handoff Protocol** section in `AGENTS.md`.
- **Relevant files:** `.opencode/agents/*.md`, `.opencode/opencode.jsonc`,
  `.opencode/commands/handoff.md`, `AGENTS.md`, `docs/ai-workforce/*`, `handoff.md`.
- **Verification performed:** live subagent permission probes (`qa-engineer`, `code-reviewer`);
  `GET /api/agent?location[directory]=…`; Gradle compile, unit tests, and `assembleDebug`.
- **Evidence:** `{"error":{"type":"permission.rejected","message":"Permission denied: edit"}}` for
  out-of-lane writes, in-lane write succeeded, `git commit` refused, `Write-Output hello` refused
  for `code-reviewer`, `BUILD SUCCESSFUL in 9s`. Full detail: `docs/ai-workforce/logs/task-001.md`.

### 4.2 UEPflip application features (prior milestones)

- **Feature:** deck management, study loop with SM-2, statistics, AI import (topic + PDF), tutor
  grading, hints, seeded debug data.
- **Implementation:** see `AGENTS.md` — Architecture / UI layers sections.
- **Relevant files:** `app/src/main/java/com/example/uepflip_flashcard/**`, `app/src/main/res/**`.
- **Verification performed:** `:app:compileDebugKotlin`, `testDebugUnitTest`, `assembleDebug`.
- **Evidence:** `BUILD SUCCESSFUL` on all three, 2026-10-19. No device run has ever been recorded.

### 4.3 Handoff protocol and task-state split (task `T-002`)

- **Feature:** the workforce now maintains a durable execution record instead of losing context.
- **Implementation:** root `handoff.md` (checkpoint), `docs/ai-workforce/` (task board, decision
  log, test log, per-task logs), a `## Handoff Protocol` section in `AGENTS.md`, a `/handoff`
  command pinned to `agent: orchestrator`, and `instructions` in `.opencode/opencode.jsonc` so the
  record is auto-loaded every session. Write access is enforced: `orchestrator` holds the only
  allows, all six specialists hold explicit denies.
- **Relevant files:** `handoff.md`, `docs/ai-workforce/**`, `.opencode/commands/handoff.md`,
  `.opencode/opencode.jsonc`, `.opencode/agents/*.md`, `AGENTS.md`.
- **Verification performed:** `GET /api/agent` (rules confirmed present on the parsed agents),
  `GET /api/command` (`handoff` registered), `GET /api/config` (`instructions` and
  `default_agent` loaded), `testDebugUnitTest` PASS, `git status`.
- **Evidence:** orchestrator shows `allow: handoff.md` / `allow: docs/ai-workforce/**`; `qa-engineer`
  and `code-reviewer` show the matching `deny:` rules; `BUILD SUCCESSFUL in 6s` at 00:45; no
  application source touched.

## 5. Active Work

**None in flight.** `T-003a` is `DONE` — completed, independently re-verified, and reviewed
(§4.4, `docs/ai-workforce/logs/task-003.md`). `T-005` is also `DONE`: with explicit user
authorization the workforce and the tests were committed as `11b7058` and `e106f30`, and the
working tree is clean, so the "untracked and unrecoverable" risk recorded earlier is resolved.
Nothing has been pushed.

**Next task (not started):** `T-007` — the `DocumentEntity` missing foreign key found by the
`T-003a` review, owned by `data-engineer`. See §6 for the alternatives (`T-004`, `T-006`, `T-008`).

### How `T-003a` was delegated (completed record)

- **Task ID:** `T-003a` — in-memory Room persistence tests on a device.
- **Assigned agent:** `qa-engineer`, delegating upward to the `orchestrator`. Ran as a background
  subagent (session `ses_eb00b17d5ffeGZAabNTa87QHjs`); its structured handoff block was returned
  and **independently re-verified by the orchestrator** — every command was re-run rather than
  taken on trust. Reviewed by `code-reviewer` (session `ses_eaffb45afffeKTTJ8i7jHQUWbj`).
- **Objective (achieved):** close the largest coverage gap — **Room had zero tests anywhere in
  this repo** (the JVM suite covers only pure SM-2 maths, review field mapping, deck stats, and
  the AI generator). It now has 10 instrumented tests.
- **Files owned:** `app/src/androidTest/**` only. Specifically
  `app/src/androidTest/java/com/example/uepflip_flashcard/data/local/AppDatabaseTest.kt`.
- **Dependencies:** a booted device — satisfied: `Pixel_9`, API 36, serial `emulator-5554`.
- **Done so far:** harness de-risk run passed — `.\gradlew.bat connectedDebugAndroidTest` →
  `BUILD SUCCESSFUL in 1m 37s`, exit 0, 2026-10-19 00:57. This proved the runner classpath resolves
  transitively, so **no Gradle change is required** and `T-003a` has no cross-lane blocker.
- **Acceptance criteria:** `connectedDebugAndroidTest` green with the new tests on `emulator-5554`,
  `testDebugUnitTest` still green, `git status app/src/main` **empty**, and a structured handoff
  block from `qa-engineer` recorded in `test-results.md` / `logs/task-003.md`.
- **Allowed failure:** if a test exposes a real production bug, `T-003a` succeeds by **reporting**
  it. `qa-engineer` is permission-blocked from editing `app/src/main/**`; forcing a green suite by
  weakening an assertion is not an acceptable outcome.
- **Verification status:** **DONE.** Green on device and re-verified by the orchestrator;
  reviewed `APPROVE WITH CONCERNS`; lane boundary held.
- **Deliberately out of scope:** `T-006` (the real migration test) — see `task-board.md` blockers.
- **Consequence:** the review surfaced a genuine production defect (`DocumentEntity` missing a
  foreign key, so documents are orphaned on deck delete). It was **routed to `T-007`**, not
  patched from `qa-engineer`'s lane — the "report, don't route around the boundary" rule worked
  as intended.

**Last completed before this:** `T-002` (handoff protocol + task-state split) — verified
2026-10-19 00:45, detail in `docs/ai-workforce/logs/task-002.md`.

## 6. Pending Tasks

| ID | Task | Dependencies | Priority | Status |
|----|------|--------------|----------|--------|
| T-002 | Handoff protocol + task-state split | — | high | DONE |
| T-003a | In-memory Room persistence tests on a device (`app/src/androidTest/**`) | T-002 | high | DONE |
| T-004 | Configure `gemini_api_key` in Firebase Remote Config and verify the live AI flow end to end | T-002 | medium | NOT_STARTED |
| T-005 | Commit `.opencode/`, `docs/`, `handoff.md`, `AGENTS.md` (+ the `T-003a` tests) — user-authorized | T-002 | low | DONE |
| T-006 | Real `MIGRATION_1_2` test — blocked by `MIGRATION_1_2` being private, `exportSchema = false`, and missing `androidTestImplementation(room-testing)`; spans `data-engineer` + orchestrator + qa-engineer | T-003a | medium | NOT_STARTED |
| T-007 | `DocumentEntity` declares an index on `deckId` but **no foreign key** (its KDoc falsely claims one), so deleting a deck orphans its documents including imported PDFs' full `extractedText` — silent storage leak. Found by the `T-003a` review. Fix = FK + CASCADE = Room schema 2→3 with a row-preserving migration, or the cheap interim: correct the KDoc and delete documents by `deckId` in the deck-delete path | T-003a | medium | NOT_STARTED |
| T-008 | Test follow-ups from the `T-003a` review: the repository's own `withTransaction` in `processCardReview` is still untested (the rollback test hand-rolls its own), no deck-deletion cascade test, `catch (t: Throwable)` should be `SQLiteConstraintException`, and one test name overstates what it asserts | T-003a | low | NOT_STARTED |

## 7. Technical Decisions

Full log with alternatives: `docs/ai-workforce/decisions.md`. Summary:

- **Lanes are directory-based, disjoint** — `android-engineer` owns `domain/**`, `ui/main/**`,
  `ui/study/**`; `ui-engineer` owns `res/**` only. A "layout plus its Kotlin" change is a
  deliberate two-lane handoff.
- **OpenCode V2 `permissions` rule lists**, never legacy `tools:` / `permission:` / `disable`.
- **Rules ordered `deny` first, then narrow `allow`** (V2 = last matching rule wins).
- **Agents inherit the parent session model** — no `model:` field, so a provider/model change
  cannot break the team.
- **`orchestrator` is the sole handoff maintainer**; the six specialists are explicitly denied
  writes to `handoff.md` and `docs/ai-workforce/**`.
- **State split:** `handoff.md` holds current state and points to `docs/ai-workforce/`, which holds
  the board, decisions, test log, and per-task logs.

## 8. Known Bugs and Blockers

**One real defect is now known** — `DocumentEntity` has no foreign key to decks, so deleting a
deck orphans its document rows (including imported PDFs' full `extractedText`): a silent storage
leak, not user-visible breakage, since every document query is deck-scoped or by primary key.
Found by the `T-003a` review, tracked as `T-007`, not yet fixed. Everything else below is an
*unverified area*, which is not the same as a bug.

- **Unverified areas remaining after `T-003a`:** Flow re-emission over time (the tests read
  `first()` snapshots), concurrent writers, real-file (non-in-memory) database behaviour, and —
  most importantly — the repository's own `withTransaction` in `processCardReview`. The rollback
  test hand-rolls its own transaction, so deleting the wrapper from `processCardReview` would
  still leave all 10 tests green. Known hole, documented: `T-008`, decision D-010.
- **Unverified area — migration:** `MIGRATION_1_2` has never been exercised against a real
  version-1 database. Blocked by three cross-lane changes. The exact blockers are already recorded
  under **T-006 — known blockers** in `docs/ai-workforce/task-board.md`; do not re-derive them.
- **Unverified area — live AI:** Gemini calls and Remote Config fetches cannot run on the JVM; only
  the stubbed-OkHttp unit tests pass. Next diagnostic: configure the key on a device and run `T-004`.
- **Documentation defect (fixed in `T-002`):** `AGENTS.md` claimed the old handoff was at
  `git show HEAD:handoff.md`. It does not exist at HEAD; it was deleted in `d5776c8` and is only
  reachable at `d5776c8^:handoff.md`. That file described an obsolete Activity-based phase and is
  **superseded** by this document — do not resurrect it.

## 9. Failed Approaches

- **Approach:** `GET /api/agent` with no arguments to list the custom agents.
  **Why it failed:** it silently returns agents for a default location, so the 7 custom agents
  appear to have vanished and it looks like the config is broken.
  **Evidence:** default call listed only built-ins; adding
  `?location[directory]=<project root>` listed all 7.
  **Do not repeat unless conditions change:** always pass the location parameter.
- **Approach:** `git show HEAD:handoff.md` to recover prior-phase notes.
  **Why it failed:** the file is not at HEAD — it was deleted in commit `d5776c8`.
  **Do not repeat unless conditions change:** use `git show d5776c8^:handoff.md`, and treat the
  content as obsolete.
- **Approach:** grepping the dumped `openapi.json` output with PowerShell `Select-String` on the raw
  text. **Why it failed:** single enormous lines flooded the tool output.
  **Do not repeat unless conditions change:** use `grep` with a bounded regex, or extract a substring
  by index.
- **Approach:** `&&` / `||` as separators in a `shell` tool command.
  **Why it failed:** this is Windows PowerShell 5.1; they are not valid statement separators.
  **Do not repeat unless conditions change:** use `;` or separate calls.
- **Approach:** issuing two `edit` calls on the **same file** in one parallel tool block.
  **Why it failed:** the second call is evaluated against a stale snapshot and both fail with
  `Could not find oldString`, even though each would succeed alone. It happened twice on
  `handoff.md` and once on `task-board.md` during `T-003a`; the failures were harmless (no partial
  write) but they cost real time.
  **Do not repeat unless conditions change:** edits to different files may be parallelized; edits
  to one file must be sequential, in their own turn. If an `edit` unexpectedly cannot find a
  string you just read, suspect a collision rather than a corrupted file, and re-read before
  assuming damage.

## 10. Git and Workspace State

- **Current branch:** `main`
- **Last known commit:** `e106f30` — 2026-10-19 — `test: add Room persistence instrumented tests (T-003a)`;
  parent `11b7058` — `chore: add AI engineering workforce and durable handoff record`.
  Both authored this session with explicit user authorization (`T-005`), stacked on `4bc2c48`.
- **Uncommitted changes:** **none** — the working tree is clean, nothing staged. The entire
  workforce and the `T-003a` tests are committed, so `git checkout` / `git reset --hard` now
  recover everything. No push was made (`git push` still needs its own authorization).
- **Files currently being modified:** none. This file is edited only to record state.
- **Unresolved merge conflicts:** none. No stashes (`git stash list` is empty).
- **Backup/recovery notes:**
  - Everything is tracked, so `git checkout HEAD -- <path>` and `git reset --hard` recover all of
    it. The unrecoverable-untracked risk recorded earlier in this document is **resolved** by
    `T-005`.
  - `T-005` produced two commits rather than one on purpose: the workforce config
    (`11b7058`) and the tests (`e106f30`) are separate logical changes, so either can be reverted
    without touching the other.
  - Never assume the workspace is clean: check `git status` before editing.

## 11. Verification Record

| Exact command | Result | Relevant output | Date/time |
|---|---|---|---|
| `.\gradlew.bat :app:compileDebugKotlin` | PASS | `BUILD SUCCESSFUL in 14s`, 30 actionable tasks | 2026-10-19 |
| `.\gradlew.bat assembleDebug` | PASS | `BUILD SUCCESSFUL in 9s`, `:app:assembleDebug` executed | 2026-10-19 |
| `.\gradlew.bat testDebugUnitTest` (T-001) | PASS | `:app:testDebugUnitTest UP-TO-DATE`, JVM suite green | 2026-10-19 |
| subagent probe: `qa-engineer` writes | PASS | out-of-lane writes `permission.rejected`; in-lane write allowed; `git commit` refused | 2026-10-19 |
| subagent probe: `code-reviewer` tools | PASS | no `write`/`edit` tool in catalog; `Write-Output hello` → `permission.rejected`; `git status` allowed | 2026-10-19 |
| `opencode api get "/api/agent?location[directory]=<root>"` (T-002) | PASS | 14 agents; orchestrator shows 4 `allow` rules for `handoff.md` / `docs/ai-workforce/**`; specialists show the matching `deny` rules | 2026-10-19 00:45 |
| `opencode api get "/api/command?location[directory]=<root>"` (T-002) | PASS | `handoff` registered. Endpoint returns only name/description, so `agent: orchestrator` is verified by file inspection, not by API | 2026-10-19 00:45 |
| `opencode api get "/api/config?location[directory]=<root>"` (T-002) | PASS | `instructions: [handoff.md, docs/ai-workforce/task-board.md]`; `default_agent: orchestrator` | 2026-10-19 00:45 |
| `.\gradlew.bat testDebugUnitTest --console=plain` (T-002) | PASS | `BUILD SUCCESSFUL in 6s`, 30/30 up-to-date, exit 0, config cache reused | 2026-10-19 00:45 |
| `.\gradlew.bat connectedDebugAndroidTest` | **PASS** | `BUILD SUCCESSFUL in 1m 37s`, exit 0, `Running tests on devices: Pixel_9(AVD) - 16`. First ever instrumented run in this workspace — the existing `ExampleInstrumentedTest` passed, so the runner classpath resolves transitively and no Gradle change is needed | 2026-10-19 00:57 |
| `emulator -avd Pixel_9` + `adb shell getprop` (T-003a) | PASS | `sys.boot_completed=1`, serial `emulator-5554`, `ro.build.version.sdk=36`, `ro.build.version.release=16` | 2026-10-19 00:57 |
| `.\\gradlew.bat connectedDebugAndroidTest` (T-003a new tests) | **PASS** | agent run `BUILD SUCCESSFUL in 42s`; report XML → `AppDatabaseTest tests="10" failures="0" errors="0"` plus `ExampleInstrumentedTest tests="1"` | 2026-10-19 01:10 |
| `.\\gradlew.bat connectedDebugAndroidTest testDebugUnitTest --console=plain --rerun` (orchestrator's own re-run) | **PASS** | `BUILD SUCCESSFUL in 26s`, exit 0, both tasks forced — the green is reproducible, not a cached result | 2026-10-19 01:16 |
| tally of `app/build/test-results/testDebugUnitTest/*.xml` | **PASS** | **57 tests / 7 suites / 0 failures / 0 errors / 0 skipped** — AiFlashcardGenerator 17, SampleDataSeeder 9, DeckStats 9, Sm2Scheduler 9, DeckNaming 8, ReviewApplication 4, Example 1 | 2026-10-19 01:16 |
| `git status --short app/src/main` (lane boundary) | **PASS** | **empty** — `qa-engineer` wrote nothing outside `app/src/androidTest/**` | 2026-10-19 01:16 |
| `code-reviewer` review of the `T-003a` diff | **APPROVE WITH CONCERNS** | independently tallied both report-XML sets; no tautologies; determinism clean. Concern: the rollback test hand-rolls its own `withTransaction`, so the repository's transaction remains untested. Surfaces a real production defect → `T-007` | 2026-10-19 01:17 |
| `git add AGENTS.md .opencode docs handoff.md` → `git commit` (T-005) | **PASS** | `11b7058 chore: add AI engineering workforce and durable handoff record` — 17 files, 2090 insertions. Staged set checked against `git diff --cached --stat` first: no `local.properties`, no scratch build logs | 2026-10-19 01:42 |
| `git add app/src/androidTest` → `git commit` (T-005) | **PASS** | `e106f30 test: add Room persistence instrumented tests (T-003a)` — 1 file, 380 insertions. Kept separate from the config commit so either can be reverted alone | 2026-10-19 01:42 |
| `git status --short` after committing | **PASS** | clean — nothing staged, nothing untracked. Nothing pushed | 2026-10-19 01:42 |

Compile, unit tests, and APK assembly are distinct from runtime verification: nothing here proves
the app behaves correctly on a device.

## 12. Next Actions

1. **Highest-priority action:** `T-007` — the `DocumentEntity` missing foreign key found by the
   `T-003a` review, owned by `data-engineer`. A correct fix is Room schema 2→3 with a hand-written,
   row-preserving migration (AGENTS.md forbids `fallbackToDestructiveMigration()`); the cheap
   interim — correct the KDoc that falsely claims a foreign key, and delete documents by `deckId`
   in the deck-delete path — can land first and alone.
2. **Alternatives:** `T-006` (real `MIGRATION_1_2` test) is blocked by three cross-lane changes
   already recorded in `docs/ai-workforce/task-board.md` — start from that section, do not
   re-derive it. `T-008` closes the test holes the review identified (production-path rollback
   coverage, a deck-deletion cascade test, `SQLiteConstraintException` instead of
   `catch (t: Throwable)`, and one overstated test name). `T-004` needs the user at the Firebase
   console to set `gemini_api_key`.
3. **No push has been made.** `main` is at `e106f30`, local only, working tree clean. Pushing needs
   its own explicit authorization.

## 13. Instructions for the Next Session

1. Read `AGENTS.md` and this handoff file.
2. Inspect Git status before modifying files.
3. Inspect the relevant source files instead of trusting this document as proof of the current
   implementation.
4. Resume from the first unfinished action.
5. Do not repeat completed work without a specific reason.
6. Update this file after significant milestones.
7. Preserve all existing user requirements and constraints.
8. Verify changes before marking tasks complete.
