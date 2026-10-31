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

- **Overall status:** `IN_PROGRESS` — features implemented and working. **Live AI works** (the user
  rebuilt with the `T-009` patch and confirmed generation), and the `T-012` / `T-014` follow-on
  changes are now **compile- and test-verified** (see §11, 2026-10-31). The full designer UI
  (My Profile, Settings, Notifications, About, setup flow, redesigned Home/Stats/Decks) is merged
  in (§4.7 / `T-016`).
- **Last updated:** 2026-10-31 — full UI snapshot merged, sources normalized under `ui/main`, and
  `assembleDebug` + `testDebugUnitTest` verified green.
- **Current phase:** UI integration. The AI blockers remain closed (`gemini_api_key` served,
  retired `gemini-2.0-flash` root-caused and replaced by the Remote Config `gemini_model`).
- **Last verified build:** 2026-10-31 — `:app:assembleDebug` `BUILD SUCCESSFUL` in 2m 07s; artifacts
  in `app/build/outputs/apk/debug/app-debug.apk`.
- **Last verified tests:** 2026-10-31 — `testDebugUnitTest` 8 suites, **87 tests, 0 failures,
  0 errors, 0 skipped**.
- **No device run has been recorded yet** for the merged UI; instrumented tests were not executed
  (require an attached device/emulator).

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
- **Persistence:** Room 2.8.5, `AppDatabase` version 3, `exportSchema = false`, hand-written
  `MIGRATION_1_2` and `MIGRATION_2_3`. Multi-table writes run in `withTransaction` inside the repository.
- **Network/API integrations:** Gemini `generateContent` via `data/service/AiFlashcardGenerator`
  (OkHttp + Moshi), API key from Firebase Remote Config key `gemini_api_key`; **the model id also
  comes from Remote Config** (`gemini_model`, default `gemini-flash-latest` via
  `AiFlashcardGenerator.DEFAULT_MODEL` + `endpointFor()` — see D-013, added because the previously
  hardcoded `gemini-2.0-flash` was retired and broke every AI feature); Apache PDFBox 2.0.24 for
  on-device PDF extraction; Firebase Remote Config for key resolution. The app does **not** use the
  Firebase AI Logic SDK.
- **Minimum Android SDK:** 26.
- **Required design conventions:** the `res/layout/` files match a designer's reference (25 files:
  22 wired — including the shared `view_app_header` include — plus 3 **unwired mockups** kept as
  references: `screen_login.xml`, `screen_register.xml`, `screen_privacy_policy.xml`, with their
  `auth_*` drawables / `strings_auth` / `styles_auth` / `colors_auth`); all user-facing copy in
  `res/values/strings*.xml`; UI label "Normal" for `Sm2Scheduler.RATING_MEDIUM`.
- **Repository:** `UEPFLIP_` (local git, no remote) seeded from the prior
  `AndroidStudioProjects/UEPflip_flashcard` history; the full-UI snapshot merged from
  `Downloads/RALPH SERNA/UEPflip-main` (§4.7). Both source folders are retired backups — do not
  edit them.
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

### 4.4 Live AI bring-up — `404` root-caused and patched (`T-009`, under `T-004`)

- **Feature:** make the Gemini path actually work against a configured key.
- **Status:** patch **applied**, but **never compiled and never tested** — the user invoked
  `DO NOT BUILD AND TEST`. This is recorded work, not completed work.
- **Symptom:** after the user configured `gemini_api_key`, every AI feature reported `error 404`.
- **Root cause:** `AiFlashcardGenerator.DEFAULT_ENDPOINT` hardcoded
  `.../v1beta/models/gemini-2.0-flash:generateContent`. Google **shut `gemini-2.0-flash` down on
  2026-06-01**, so the id is dead and the API answers `404`. Create, Tutor and Hint all share
  `AiGeneratorProvider`, so all three broke at once.
- **Why no test caught it:** `AiFlashcardGeneratorTest` injects `endpoint = "http://localhost/test"`,
  so the real model id is never exercised — 57 green JVM tests are fully compatible with a broken AI
  path. This is a coverage limit, not a flake.
- **Also established:** the app does **not** use the Firebase AI Logic SDK (no `firebase-ai`
  dependency, no `GenerativeModel`/`FirebaseAI` symbol anywhere), so enabling "Gemini Developer API"
  in the AI Logic console cannot configure it. The app reads a raw API key from Remote Config and
  calls the REST API itself.
- **Implementation:** `DEFAULT_MODEL = "gemini-flash-latest"`; `resolveModel()` / `isValidModelId()` /
  `endpointFor()` validating against `^[A-Za-z0-9][A-Za-z0-9._-]{0,63}$` and stripping a leading
  `models/` (the exact form the `ListModels` response prints, so an id can be pasted from it); an
  optional Remote Config override `gemini_model` read in `AiGeneratorProvider.create()`; the API key
  is now `.trim()`ed (a pasted trailing newline would otherwise corrupt the query string); and the
  provider logs the model **actually in use**, warning when a configured value was rejected — that log
  line is the designated diagnostic for the next retirement, so it must not claim a dead value is live.
  Decision D-013.
- **Independent review:** `code-reviewer`, session `ses_eafb9783dffe7vIY7q6Sb4xTuN` —
  **APPROVE WITH NITS**, no BLOCKER, no HIGH. Inspection-only, because Gradle was off-limits too, and
  it said so explicitly. It confirmed: companion initialisation order is safe, `const val` → `val` on
  `DEFAULT_ENDPOINT` is harmless (its only reference is the constructor default), the parenthesised
  `try` is load-bearing and valid, `app/src/test/**` still compiles, the validator cannot redirect the
  request off-host, and **no existing test could have caught the `404` — nor could any hermetic/offline
  JVM test**, since only a live call can know a model was retired (a networked unit test could, but at
  the cost of a real key and a flaky suite). Its MEDIUM findings are fixed in the tree; the
  remaining ones are §12 follow-ups.
- **Second review pass:** `code-reviewer`, session `ses_eafb29925ffewZ517GkywsyHfr` — re-read the fixes
  to its own findings plus the `T-010` test file. **APPROVE WITH NITS**, no BLOCKER, HIGH or MEDIUM.
  It confirmed the three functions cannot disagree (one `normalizeModel`, one pattern), that the
  provider can no longer log a value it is not using, that the tighter pattern and `models/` stripping
  break nothing previously accepted, that the new test is compile-safe by inference, and that **no
  assertion in it would fail today**. Its remaining LOW/NIT items (stale regex in D-013, a wrong test
  count, "next app start" wording, the "no new JVM test" overstatement, logging the raw value) are
  fixed in this tree.
- **Relevant files:** `data/service/AiFlashcardGenerator.kt`, `data/service/AiGeneratorProvider.kt`,
  `README.md`.
- **Verification performed:** none. Static inspection plus a web search for the model's retirement
  status, then a grep proving no code path references `firebase-ai`.
- **Evidence:** no Gradle command was run this session. See §11, and the `T-004 / T-009` entry in
  `docs/ai-workforce/test-results.md` where every row is `NOT_RUN`.
- **What would prove it:** `:app:compileDebugKotlin` + `testDebugUnitTest`, then one real generation
  on a device. Cheaper first step, outside the app:
  `curl "https://generativelanguage.googleapis.com/v1beta/models?key=YOUR_KEY"`.
- **Outcome:** the user rebuilt/reinstalled and reported **"it worked"**. `T-009` is functionally
  confirmed by a live request; its Gradle rows are still `NOT_RUN` here because the build happened
  outside this session.

### 4.5 Hint latency — thinking level was never set (`T-012`)

- **Feature:** the study screen's "Request hint" felt slow (several seconds of spinner).
- **Status:** patch **applied**, **never compiled and never tested** — the `DO NOT BUILD AND TEST`
  freeze still stands. Recorded work, not completed work.
- **Root cause:** `buildRequestBody()` sent only `{"contents":[...]}` — **no `generationConfig` at
  all**. `gemini-flash-latest` resolves to a Gemini 3.x Flash model, and those **think by default**
  (docs: dynamic thinking, default `medium` on 3.6/3.8 Flash, `high` on 3 Flash). Thinking tokens
  are produced before the first visible token, and the app uses non-streaming `generateContent`, so
  the hint could not appear until thinking *and* the answer had finished. Nothing in the code chose
  that: it was the model default.
- **Fix:** `AiFlashcardGenerator` gained `fastThinkingLevel` and sends
  `generationConfig.thinkingConfig.thinkingLevel = "low"` for the three short calls — hint, answer
  grading, explanation. `generateFlashcards` deliberately keeps the model default because card
  quality benefits from reasoning. `low` is the right level: the docs describe it as
  "minimises latency and cost", and `minimal` is **not** an option because `gemini-3.7-flash` and
  `gemini-3.8-flash` reject it with an error — a Remote Config model change must not be able to break
  hints via a constant.
- **Deliberately not done:** no `maxOutputTokens` cap. The docs state it counts **thought tokens
  too**, so a small cap makes the model hit `MAX_TOKENS` and return truncated/empty output — it would
  trade latency for "The AI returned an empty hint". Lowering the thinking level is the documented
  way to cut latency.
- **Compatibility guard:** Gemini 2.5 models do not support `thinkingLevel` (they use
  `thinkingBudget`), and an unknown field is a request error. `AiGeneratorProvider` therefore emits
  the field only for `gemini-3*`, `gemini-4*` and the version-agnostic `*-latest` aliases; for any
  other id the request is byte-for-byte what it was before. Moshi omits null properties, which is what
  keeps that path unchanged.
- **Secondary contributors (not fixed, documented):** the first AI call on each study screen also
  awaits `RemoteConfig.fetchAndActivate()` inside `AiGeneratorProvider.create()`; the generator is
  cached per `StudyViewModel`, so that is one round trip per screen, not per hint. And the call is not
  streamed, so the result still arrives in one piece — streaming would show the first words sooner
  but is a larger change (SSE parsing).
- **Evidence:** code read end to end (`StudyViewModel.requestHint` → `generateHint` →
  `requestModelText` → `buildRequestBody`), and the REST shape `generationConfig.thinkingConfig.
  thinkingLevel` confirmed against `ai.google.dev/gemini-api/docs/generate-content/thinking`
  (last updated 2026-10-14).
- **Relevant files:** `data/service/AiFlashcardGenerator.kt`, `data/service/AiGeneratorProvider.kt`,
  `app/src/test/.../AiFlashcardGeneratorTest.kt` (three new wire tests, `T-012`).
- **Verification performed:** none — inspection and documentation only. See §11.

### 4.6 Pre-generated, stored hints (`T-014`)

- **Feature:** the study screen's hint should be available the moment the student taps, not generated
  on demand.
- **Status:** **applied, never compiled and never tested** — the freeze stands. Recorded work, not
  completed work.
- **Design:** the hint is generated in the **same** Gemini call as question/answer, so there is no
  extra request at import time and no extra quota. `GeneratedCard.hint` is persisted on
  `CardEntity.hint` by both card-creation paths (`DocumentImportViewModel`, `TutorViewModel`), so
  `StudyViewModel.requestHint()` can render it with no network and no spinner. Cards created before
  this change and the debug sample data have `hint == null`; the study screen then uses the existing
  `generateHint` and saves the result via `FlashcardRepository.saveHint(...)` for next time.
- **Leak guard:** a hint is only useful if it does not give the answer away, and pre-generation has no
  second review pass. `parseFlashcards` trims it, caps it at 240 chars on a word boundary, and drops
  it to `null` if it contains the answer verbatim (case-insensitive, answers ≥ 3 chars). A bad hint
  costs the hint, never the card. Paraphrased leaks are the prompt's responsibility — documented, not
  detected.
- **Schema:** `cards.hint` is a **nullable** TEXT column, so `null` (not `""`) is the real "not
  generated yet" state. Room goes **2 → 3** with a hand-written `MIGRATION_2_3`
  (`ALTER TABLE cards ADD COLUMN hint TEXT`, guarded by the existing `hasColumn`). No table rebuild,
  no row rewritten, so decks, cards, scheduling state and review history are preserved.
- **Concurrency:** `saveHint` uses `UPDATE cards SET hint = :hint, updatedAt = :updatedAt WHERE id =
  :cardId` rather than read-modify-write, so a hint written while `processCardReview` commits cannot
  erase the review's scheduling fields. The write is best-effort at the call site: the hint is shown
  before it is saved, and a failure is logged, not surfaced, because it only costs a regeneration.
- **Deliberately not done:** no hints are invented for the debug sample data (fabricated study help is
  worse than a lazy generation), and no migration test was added — `MIGRATION_2_3` is private and the
  DB is `exportSchema = false`, the same T-006 blocker.
- **Relevant files:** `data/service/AiFlashcardGenerator.kt`, `data/local/{entities/CardEntity,
  AppDatabase,dao/CardDao}.kt`, `data/repository/FlashcardRepository.kt`,
  `ui/document/DocumentImportViewModel.kt`, `ui/study/StudyViewModel.kt`, `ui/main/TutorViewModel.kt`,
  `app/src/test/.../AiFlashcardGeneratorTest.kt`, `app/src/androidTest/.../AppDatabaseTest.kt`.
- **Verification performed:** none. Every Gradle row is `NOT_RUN`. The migration is the riskiest part:
  a mismatch between the entity and the migrated schema throws on first `getDatabase()` and takes
  every screen with it.

### 4.7 Full designer UI merge (`T-016`)

- **Feature:** the complete designer UI snapshot (last built 2026-10-31 by the designer) merged onto
  the AI-complete codebase. The snapshot was a **strict superset** of the prior repo: 96 shared
  files byte-identical, and every data/domain/AI class (Room entities, DAOs, scheduler,
  `AiFlashcardGenerator`, `FlashcardRepository`, `AppDatabase`) unchanged — so nothing was lost.
- **Implementation:**
  - New repo `UEPFLIP_` created by cloning the prior local repo (full 11-commit history preserved),
    then overlaying the snapshot via `robocopy`; origin removed (local-only, user choice).
  - New screens, wired through `MainActivity` pushes: `MyProfileFragment` (`screen_my_profile`),
    `SettingsFragment`, `NotificationsFragment`, `AboutFragment`, and the onboarding setup flow
    (`SetupFeaturesFragment`, `SetupGoalFragment`, `SetupNotificationsFragment`) driven by
    `OnboardingFlow` + `AppSettings` (shared preferences keys with onboarding).
  - Redesigned `fragment_home`, `fragment_stats` (donut/bar charts, `StatsDeckAdapter`),
    `fragment_decks`, `item_deck`; shared `view_app_header` include used by home and stats.
  - New `domain/StudyActivity.kt`; `MainActivity` gained `goProfile/goSettings/goNotifications/goAbout`.
  - Structure normalized: the ten `.ui.main`-package sources that sat at `ui/` root in the snapshot
    moved to `ui/main/` (pure file move; packages already declared `ui.main`). Only
    `ui/SimpleViewModelFactory.kt` remains outside `ui/main`, matching its `ui` package.
  - Unwired mockups kept per user choice: `screen_login`, `screen_register`,
    `screen_privacy_policy` (+ `auth_*` resources). No Kotlin or manifest entries reference them.
- **Verification performed:** `:app:assembleDebug`, `:app:testDebugUnitTest` (§11).
- **Evidence:** `BUILD SUCCESSFUL in 2m 07s`, APK at `app/build/outputs/apk/debug/app-debug.apk`;
  tests 8 suites / 87 / 0 failures.
- **Not done here:** wiring the three auth mockups; `connectedDebugAndroidTest` (no device attached).

**Four tasks are in flight and all are unverified: `T-009`** (§4.4) — the retired-model patch, now
functionally confirmed live but still never compiled in this session — **`T-010`**, the
model-resolution test, **`T-012`** (§4.5), the thinking-level patch plus its tests, **and `T-014`**
(§4.6), the pre-generated hint (generator + schema **v3** + three UI paths + tests). **All four were
committed and pushed on 2026-10-19 with the user's explicit authorization, and pushing compiles
nothing** — every Gradle row for them is still `NOT_RUN`. Promote any of them only after
`.\gradlew.bat :app:compileDebugKotlin` and `.\gradlew.bat testDebugUnitTest` are green, and give the
`T-014` migration a real upgrade run on a device. The user's standing `DO NOT BUILD AND TEST` must be
lifted first; do not run Gradle behind it.

**`T-005` is `DONE`:** with explicit user authorization the workforce and the tests were committed as
`11b7058` and `e106f30`. The later AI work is pushed as well (§10), so the remote now holds a copy of
everything.

**Next task (not started):** `T-007` — the `DocumentEntity` missing foreign key found by the
`T-003a` review, owned by `data-engineer`. See §6 for the alternatives (`T-004`, `T-006`, `T-008`).

**Last verified-complete task:** `T-003a` — completed, independently re-verified by the orchestrator,
and reviewed (`docs/ai-workforce/logs/task-003.md`).

## 5. Active Work

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
| T-004 | Configure `gemini_api_key` in Firebase Remote Config and verify the live AI flow end to end | T-002 | medium | IN_PROGRESS — key configured and **live flow confirmed**: the user rebuilt with `T-009` and reported "it worked". Gradle rows remain `NOT_RUN` here because that build happened outside this session; one Create/Tutor/Hint run each is still unrecorded |
| T-005 | Commit `.opencode/`, `docs/`, `handoff.md`, `AGENTS.md` (+ the `T-003a` tests) — user-authorized | T-002 | low | DONE |
| T-006 | Real `MIGRATION_1_2` test — blocked by `MIGRATION_1_2` being private, `exportSchema = false`, and missing `androidTestImplementation(room-testing)`; spans `data-engineer` + orchestrator + qa-engineer | T-003a | medium | NOT_STARTED |
| T-007 | `DocumentEntity` declares an index on `deckId` but **no foreign key** (its KDoc falsely claims one), so deleting a deck orphans its documents including imported PDFs' full `extractedText` — silent storage leak. Found by the `T-003a` review. Fix = FK + CASCADE = Room schema 2→3 with a row-preserving migration, or the cheap interim: correct the KDoc and delete documents by `deckId` in the deck-delete path | T-003a | medium | NOT_STARTED |
| T-008 | Test follow-ups from the `T-003a` review: the repository's own `withTransaction` in `processCardReview` is still untested (the rollback test hand-rolls its own), no deck-deletion cascade test, `catch (t: Throwable)` should be `SQLiteConstraintException`, and one test name overstates what it asserts | T-003a | low | NOT_STARTED |
| T-009 | Retired model id `gemini-2.0-flash` hardcoded in `AiFlashcardGenerator.DEFAULT_ENDPOINT` → every AI feature returns `404` (Google shut the model down 2026-06-01). Patch: `DEFAULT_MODEL = "gemini-flash-latest"`, `resolveModel()` / `isValidModelId()` / `endpointFor()` with `^[A-Za-z0-9][A-Za-z0-9._-]{0,63}$` validation and `models/`-prefix stripping, optional Remote Config override `gemini_model`, API key `.trim()`ed, provider logs the model actually in use | T-004 | **high** — all AI features are dead without it | IN_PROGRESS — applied and reviewed (`code-reviewer`, APPROVE WITH NITS, no BLOCKER/HIGH, inspection-only) but **never compiled or tested**; do not promote to DONE without `:app:compileDebugKotlin` + `testDebugUnitTest` |
| T-010 | Unit-test the model-resolution fallback contract (`resolveModel` / `isValidModelId` / `endpointFor`: null, blank, `models/` prefix, junk, 64-vs-65 chars, exact URL). Raised by the `T-009` review — the new public surface shipped with zero tests | T-009 | medium | IN_PROGRESS — file written and orchestrator-inspected, **never compiled or run**; untracked, so `git clean -fd` deletes it |
| T-011 | Two `T-009` review leftovers in `data/service/**`: `GeminiCandidate.finishReason` is parsed and never read, so a `MAX_TOKENS` truncation surfaces as the uninformative "The AI returned an empty response"; and `"$endpoint?key=$apiKey"` interpolates the key unencoded (`&`/`#` would corrupt it) | T-009 | low | NOT_STARTED |
| T-012 | Hint latency: the request sent no `generationConfig`, so Gemini 3.x thought at its default level (`medium`/`high`) before writing anything and the non-streaming call returned only at the end. Fix: `fastThinkingLevel` + `generationConfig.thinkingConfig.thinkingLevel = "low"` for hint/grading/explanation only; `generateFlashcards` keeps the model default; `AiGeneratorProvider` emits the field only for `gemini-3*`/`gemini-4*`/`*-latest` (Gemini 2.x rejects it); no `maxOutputTokens` because it counts thought tokens and truncates to empty. Three wire tests added to `AiFlashcardGeneratorTest` | T-009 | medium | IN_PROGRESS — applied, `code-reviewer` APPROVE WITH NITS (inspection-only), **never compiled or run** |
| T-013 | `T-012` review follow-ups: (a) MEDIUM — `AiGeneratorProvider.acceptsThinkingLevel()` is the only thing that can turn a working hint into a 400, and no test covers the model→level policy; extract a pure `internal fun supportsThinkingLevel(model)` and table-test the documented rows; (b) LOW — only `generateHint` is asserted to pass the level, so a dropped argument in `evaluateAnswer`/`generateExplanation` would pass | T-012 | medium | NOT_STARTED |
| T-014 | Hints are pre-generated in the same call as the card and stored, so "show hint" is instant instead of a multi-second request. `GeneratedCard.hint` (sanitised: trim, 240-char word-boundary cap, null when it leaks the answer verbatim) → `CardEntity.hint` (nullable TEXT, Room **2→3** + `MIGRATION_2_3`); import and Tutor paths persist it; `StudyViewModel` shows a stored hint with no network and lazily generates + `saveHint`s for old cards/sample data; `saveHint` is a targeted UPDATE so it cannot clobber scheduling state | T-009 | **high** — the UX request | IN_PROGRESS — applied across four lanes, tests written, **never compiled or run**; the migration needs a real v2→v3 upgrade run |

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

**Two real defects are known.** One is **open** — `DocumentEntity` has no foreign key to decks, so
deleting a deck orphans its document rows (including imported PDFs' full `extractedText`): a silent
storage leak, not user-visible breakage, since every document query is deck-scoped or by primary key.
Found by the `T-003a` review, tracked as `T-007`, not yet fixed. The other was **found by the user
and patched but not verified** — the retired `gemini-2.0-flash` model id (`T-009`, §4.4), which made
every AI feature return `404`. Everything else below is an *unverified area*, which is not the same
as a bug.

- **AI is broken as of this record** unless `T-009` is built and tested. The patch is in the working
  tree only. If `:app:compileDebugKotlin` has not been run since 2026-10-19, treat the AI path as
  non-functional and unverified — do not read the 57-test green suite as evidence about it.
- **A retired model id is a recurring failure mode, not a one-off:** Google shut `gemini-2.0-flash`
  down on 2026-06-01 and the app had no way to react without a code change. `T-009` moves the id to
  Remote Config (`gemini_model`) so the next retirement is a console change. If a future `404`
  appears, check `gemini_model` **before** touching the code.

- **Unverified areas remaining after `T-003a`:** Flow re-emission over time (the tests read
  `first()` snapshots), concurrent writers, real-file (non-in-memory) database behaviour, and —
  most importantly — the repository's own `withTransaction` in `processCardReview`. The rollback
  test hand-rolls its own transaction, so deleting the wrapper from `processCardReview` would
  still leave all 10 tests green. Known hole, documented: `T-008`, decision D-010.
- **Unverified area — migration:** `MIGRATION_1_2` has never been exercised against a real
  version-1 database. Blocked by three cross-lane changes. The exact blockers are already recorded
  under **T-006 — known blockers** in `docs/ai-workforce/task-board.md`; do not re-derive them.
- **Unverified area — live AI:** Gemini calls and Remote Config fetches cannot run on the JVM; only
  the stubbed-OkHttp unit tests pass. The key is now configured by the user (T-004, half done), but
  **the model-id fix (`T-009`) has never been compiled**, so the AI path is unverified *and*
  currently broken until it is built. `AiFlashcardGeneratorTest` can never cover this: it injects
  `endpoint = "http://localhost/test"` and therefore never exercises the configured model id.
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
- **Last known commit:** **do not trust a pinned hash in this section** — describing a commit
  changes the file that describes it, so this section always lags by one commit (decision D-012).
  Verify with `git log --oneline -3`. The two substantive commits are:
  - `e106f30` — `test: add Room persistence instrumented tests (T-003a)`
  - `11b7058` — `chore: add AI engineering workforce and durable handoff record`

  Everything after them is `docs: record …` commits updating this record. The pre-workforce
  baseline is `4bc2c48`.
- **Uncommitted changes:** **none.** The working tree is clean and `main` is level with
  `origin/main` (verify with `git status -sb`). The AI work was committed in two steps — code + tests,
  then the record — and **pushed to GitHub** (`ralph12-dev/UEPflip`) with explicit user authorization
  ("commit to github"). Verify the exact heads with `git log --oneline -4` rather than trusting a hash
  here (D-012).
- **The commit is unbuilt.** Pushing does not compile anything: every Gradle row in §11 is still
  `NOT_RUN`, and the `T-014` migration has never run against a v2 database. The first build after the
  freeze lifts is the real gate; `T-015` tracks the missing upgrade test.
- **Files currently being modified:** none. This file is edited only to record state.
- **Unresolved merge conflicts:** none. No stashes (`git stash list` is empty).
- **Backup/recovery notes:**
  - Everything is tracked and pushed, so `git checkout HEAD -- <path>` and `git reset --hard` recover
    **committed** work. The unrecoverable-untracked risk recorded earlier in this document is
    **resolved** by `T-005`, and `AiModelResolutionTest.kt` is now committed as well.
  - A clean tree is not evidence of correctness: the last build predates all of the AI work.
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
| `git commit` (record refresh) | **PASS** | `3f4c741 docs: record T-005 — workforce and T-003a tests committed` — 5 files, 126 insertions | 2026-10-19 01:44 |

Compile, unit tests, and APK assembly are distinct from runtime verification: nothing here proves
the app behaves correctly on a device.

### 2026-10-19 · `T-004` / `T-009` · live-AI bring-up — **nothing was built, nothing was tested**

Both tables below are recorded so a future session cannot mistake this session's work for verified
work. The user invoked `DO NOT BUILD AND TEST`.

| Command | Result | Relevant output | Date/time |
|---|---|---|---|
| `.\gradlew.bat :app:compileDebugKotlin` (`T-009`) | **NOT_RUN** | Suppressed by the user's instruction. The `T-009` patch is **compile-unverified**. | 2026-10-19 |
| `.\gradlew.bat testDebugUnitTest` (`T-009`) | **NOT_RUN** | Same. The last green JVM run (57 tests / 7 suites) **predates** `T-009` and says nothing about it. | 2026-10-19 |
| `.\gradlew.bat assembleDebug` (`T-009`) | **NOT_RUN** | Same. No APK containing the patch has been built. | 2026-10-19 |

| Diagnostic (static, not a test) | Result | Relevant output | Date/time |
|---|---|---|---|
| grep for `firebase-ai` / `FirebaseAI` / `GenerativeModel` / `com.google.firebase.ai` | PASS (diagnostic) | **No matches** — the app cannot be configured through the Firebase AI Logic console, which is exactly what the user had enabled. | 2026-10-19 |
| grep for `gemini-2.0-flash` / `DEFAULT_ENDPOINT` | PASS (diagnostic) | One production hit, in `AiFlashcardGenerator`. The tests override the endpoint (`endpoint = "http://localhost/test"`), so the suite never asserted the real model id. | 2026-10-19 |
| read `app/google-services.json` → `project_info` | PASS (diagnostic) | Project `uepflip-flashcard` (#158917882025), client `com.example.uepflip_flashcard` — Remote Config parameters must exist in this project. | 2026-10-19 |
| web search: `gemini-2.0-flash` shutdown status | PASS (diagnostic) | Google shut the model down **2026-06-01**; `generativelanguage.googleapis.com` now answers `404` for it. External evidence, not repo verification. | 2026-10-19 |
| user-reported device error `error 404` | OBSERVED (by user) | Not reproduced here. Only reachable after a completed Gemini round trip, so it also proves Remote Config now serves the key. | 2026-10-19 |
| `code-reviewer` review of the `T-009` diff | **APPROVE WITH NITS** | session `ses_eafb9783dffe7vIY7q6Sb4xTuN`. No BLOCKER, no HIGH. **Inspection-only** — it could not run Gradle either, so it did not verify compilation; it verified companion-init order, the `const val`→`val` change, the parenthesised `try`, that `app/src/test/**` still compiles, and that no existing test could have caught the `404`. MEDIUM findings fixed in the tree; remainder → §12. | 2026-10-19 |
| `git status --short` / `git diff --stat` after the fix | OK | At that point **8 files modified, 0 staged**: `AGENTS.md`, `README.md`, `handoff.md`, the two `data/service` Kotlin files, and `docs/ai-workforce/{decisions,task-board,test-results}.md`; plus **1 untracked** file, the `T-010` test. **Superseded:** all of it was committed and pushed on 2026-10-19 — see §10. The Kotlin is **built by nobody**. | 2026-10-19 |
| `T-010` — new `AiModelResolutionTest.kt` authored by `qa-engineer` | **INSPECTED, NOT RUN** | session `ses_eafb53eebffeYDDlUwTpJlMhgm`, no Gradle invoked (freeze). 20 `@Test` methods; orchestrator read the file in full and hand-checked its expected values against the production source, and `code-reviewer` re-read it in a second pass (compile-safe by inspection, no assertion would fail today). **Never compiled** — not coverage. | 2026-10-19 |
| `code-reviewer` second pass on the fixes + `T-010` | **APPROVE WITH NITS** | session `ses_eafb29925ffewZ517GkywsyHfr`. No BLOCKER/HIGH/MEDIUM. Inspection-only (it could not run `git diff` either). Confirmed: the three resolver functions agree structurally, the provider can no longer log a value it is not using, the tighter pattern breaks no previously-accepted id, and no assertion in the new test would fail today. Its LOW/NIT items are fixed in the tree. | 2026-10-19 |

**No row above verifies the `T-009` patch.** No compiler and no test has seen it. The diagnostics
explain *why* AI broke; they do not show that it works now.

### 2026-10-19 · `T-012` · hint latency — **again nothing was built, nothing was tested**

| Item | Result | Detail | Date/time |
|---|---|---|---|
| user-reported outcome of `T-009` after their own rebuild | **OBSERVED (by user)** | "it worked" — live generation now succeeds. `T-009` is functionally confirmed; the Gradle rows above remain `NOT_RUN` here. | 2026-10-19 |
| read hint path (`StudyViewModel.requestHint` → `generateHint` → `requestModelText` → `buildRequestBody`) | PASS (diagnostic) | Request body was `{"contents":[...]}` only — no `generationConfig`, so no thinking level was ever chosen by the app. | 2026-10-19 |
| checked `StudyFragment` for an artificial hint delay | PASS (diagnostic) | No `postDelayed`, no animation gate; the panel appears when state changes. The delay is the network/model call. | 2026-10-19 |
| official docs: `generationConfig.thinkingConfig.thinkingLevel` | PASS (diagnostic) | `ai.google.dev/gemini-api/docs/generate-content/thinking` (updated 2026-10-14): thinking is on by default, `low` "minimises latency and cost", `minimal` **errors** on `gemini-3.7-flash`/`3.8-flash`, `maxOutputTokens` counts thought tokens and can truncate to empty. Confirms both the fix and the decision not to cap tokens. | 2026-10-19 |
| `.\gradlew.bat :app:compileDebugKotlin` (`T-012`) | **NOT_RUN** | Freeze. The patch is compile-unverified. | 2026-10-19 |
| `.\gradlew.bat testDebugUnitTest` (`T-012`) | **NOT_RUN** | Freeze. The three new `AiFlashcardGeneratorTest` cases are unrun; the last green JVM run (57 tests / 7 suites) predates them. | 2026-10-19 |
| `.\gradlew.bat assembleDebug` (`T-012`) | **NOT_RUN** | Freeze. No APK contains the thinking-level change. | 2026-10-19 |
| new tests inspected by the orchestrator | INSPECTED, NOT RUN | `qa-engineer` session `ses_eaf9d23a5ffer4XM4ezdBvTf67`, only `AiFlashcardGeneratorTest.kt` touched; diff read in full. Asserts the decoded wire field rather than raw string order. | 2026-10-19 |
| `code-reviewer` review of the `T-012` diff | **APPROVE WITH NITS** | session `ses_eaf99e5e1ffeip80cfnSkktdQ7`. No BLOCKER, no HIGH. Inspection-only: no Gradle. Independently confirmed Moshi 1.15.0 omits nulls from `KotlinJsonAdapter` + `JsonWriter` (so the Gemini 2.x path is byte-identical), that the emitted body matches the documented REST shape, that every admitted id except the non-text `*-image`/`*-tts`/`*-live` variants accepts `low`, and that all four construction sites still compile by inspection. MEDIUM (policy predicate untested) and LOW (only `generateHint` covered) → `T-013`; the non-text-model caveat was added to `README.md`. | 2026-10-19 |

**Unverified by any executable:** that the code compiles, that Moshi emits
`{"generationConfig":{"thinkingConfig":{"thinkingLevel":"low"}}}` as expected, that the live endpoint
accepts it for the resolved model, and that the hint actually gets faster. Those are four separate
claims and only a build + a device run settle them.

### 2026-10-19 · `T-014` · stored hints — **nothing was built, nothing was tested**

| Item | Result | Detail | Date/time |
|---|---|---|---|
| Gemini request cost | unchanged count | The hint is part of the same `generateFlashcards` response, not a second call. Only the response grows. | 2026-10-19 |
| `code-reviewer` review of the `T-014` diff | **APPROVE WITH NITS** | session `ses_eaf80c4a4ffemECtSF3DVCC9x5`. No BLOCKER/HIGH. Inspection-only. Confirmed the entity↔migration match, the v1→v2→v3 and v2→v3 paths, the guarded `hasColumn`, `null` vs `""` consistency, and that the stored-hint branch returns before `ensureGenerator()`. | 2026-10-19 |
| review MEDIUM — non-string `hint` failed the whole import | **FIXED in the tree** | `FlashcardResponse` now parses `RawFlashcard.hint: Any?`; a non-string hint becomes `null` instead of throwing, so one bad hint cannot cost every card. | 2026-10-19 |
| review MEDIUM — lazy hint bypassed sanitisation | **FIXED in the tree** | `generateHint` now runs its result through `sanitizeHint`, so the lazy fallback cannot display or store a leaked/overlong hint; an unusable hint is reported as an error. | 2026-10-19 |
| review LOW/NIT — KDoc example, `saveHint` race wording, `CancellationException` | **FIXED in the tree** | `ATP` is 3 chars and is checked; the repository comment now states the narrower guarantee (a review can still write back a stale `null` hint, which is regenerated); the lazy save rethrows cancellation. | 2026-10-19 |
| review MEDIUMs left open — migration test, repository/VM coverage, Unicode normalisation | **ROUTED to `T-015`** | A real v2→v3 upgrade run is the release gate; the private migration + `exportSchema = false` is the known `T-006` blocker. | 2026-10-19 |
| `.\gradlew.bat :app:compileDebugKotlin` (`T-014`) | **NOT_RUN** | Freeze. | 2026-10-19 |
| `.\gradlew.bat testDebugUnitTest` (`T-014`, 7 new cases) | **NOT_RUN** | Freeze. | 2026-10-19 |
| `.\gradlew.bat connectedDebugAndroidTest` (`AppDatabaseTest`, 2 new cases) | **NOT_RUN** | Freeze; no device. | 2026-10-19 |
| `.\gradlew.bat assembleDebug` (`T-014`) | **NOT_RUN** | Freeze. | 2026-10-19 |

**The schema migration is the single riskiest unverified change in the tree.** A mismatch between
`CardEntity` and the migrated `cards` table throws on the first `getDatabase()` and crashes every
screen, so a fresh install is not enough — it needs an upgrade from a v2 database on a device.

### 2026-10-31 — `T-016` — full UI merge - **build + tests green**

- **Commands:** `.\gradlew.bat :app:assembleDebug :app:testDebugUnitTest` in `UEPFLIP_`.
- **Result:** `BUILD SUCCESSFUL in 2m 07s`, 46 tasks executed. `testDebugUnitTest` ran 8 suites:
  **87 tests, 0 failures, 0 errors, 0 skipped** (`app/build/test-results/testDebugUnitTest`).
- **Artifact:** `app/build/outputs/apk/debug/app-debug.apk` (≈15 MB).
- **Not run:** `connectedDebugAndroidTest` (no device/emulator attached); no device run of the
  merged UI has been recorded. The Remote Config `gemini_api_key` / `gemini_model` path inherits
  the user-confirmed `T-009` status; no AI request was re-fired this session.
- **Scope caveat:** this verifies compile + JVM behavior only — it does not prove the redesigned
  Home/Stats screens render correctly on a device.

## 12. Next Actions

1. **Highest-priority action (when the freeze lifts):** get the pushed AI work compiled. Four patches
   are complete and reviewed by inspection, but **nothing has compiled any of them**. `T-009`, `T-010`,
   `T-012` and `T-014` all land in this one build:
   - `.\gradlew.bat :app:compileDebugKotlin` then `.\gradlew.bat testDebugUnitTest` — **both are
     suppressed by the user's standing `DO NOT BUILD AND TEST`; get that lifted first.** The run must
     cover `AiModelResolutionTest` (`T-010`), the three `T-012` cases and the seven `T-014` hint cases
     in `AiFlashcardGeneratorTest`, and the pre-existing suites.
   - `curl "https://generativelanguage.googleapis.com/v1beta/models?key=YOUR_KEY"` — outside the app,
     proves which models the key can actually serve. If `gemini-flash-latest` is not listed, set the
     Remote Config parameter `gemini_model` to one that is (a leading `models/` is stripped, so the id
     can be pasted straight from that response); no code change needed. New values apply the next time
     an AI screen is opened.
   - **After installing the new build:** request a hint twice on one card set and compare. If it is
     still slow, the next suspects in order are the per-screen `RemoteConfig.fetchAndActivate()` in
     `AiGeneratorProvider.create()` and the absence of streaming — not the thinking level.
2. **Review follow-ups from the `T-009` review** (`code-reviewer`, session
   `ses_eafb9783dffe7vIY7q6Sb4xTuN`; APPROVE WITH NITS, no BLOCKER/HIGH — the MEDIUM findings are
   already fixed in the tree). Still open, cheapest first:
   - **`qa-engineer` — done, unrun:** `app/src/test/.../data/service/AiModelResolutionTest.kt` (20 tests,
     pure, no new dependency) now pins `resolveModel` / `isValidModelId` / `endpointFor` for `null`,
     blank, `models/`-prefix, junk, 64-vs-65 chars, and the exact URL. It is **untracked and never
     compiled**; run it before trusting it. It is regression insurance for the fallback contract — it is
     explicitly **not** a retirement detector, because no JVM test can know Google retired a model.
   - **`ai-engineer`, low (`T-011`):** `GeminiCandidate.finishReason` is parsed and never read, so a
     `MAX_TOKENS` truncation surfaces as the uninformative "The AI returned an empty response" — newly
     relevant, because the model can now change without a release.
   - **`ai-engineer`, low (`T-011`):** `"$endpoint?key=$apiKey"` interpolates the key unencoded.
     Pre-existing; the new `.trim()` covers the realistic case, but encoding closes `&`/`#`.
3. **Committed and pushed — done.** `T-009`, `T-010`, `T-012` and `T-014` went up in two commits
   (code + tests, then the record) with the user's explicit "commit to github" authorization. **This is
   not a verification step:** the commit is unbuilt, and the record says so. Do not promote a task to
   `DONE` on the strength of the push.
4. **Then close `T-004`** — the user confirmed live generation works after installing the `T-009`
   build, which is the missing evidence. Record one Create, one Tutor grading and one Hint run on a
   device before marking it `DONE`; the Gradle rows here are still `NOT_RUN`.
5. **Open defect, unrelated to AI:** `T-007` — the `DocumentEntity` missing foreign key found by the
   `T-003a` review, owned by `data-engineer`. A correct fix is Room schema 2→3 with a hand-written,
   row-preserving migration (AGENTS.md forbids `fallbackToDestructiveMigration()`); the cheap interim
   — correct the KDoc that falsely claims a foreign key, and delete documents by `deckId` in the
   deck-delete path — can land first and alone.
6. **Alternatives:** `T-006` (real `MIGRATION_1_2` test) is blocked by three cross-lane changes
   already recorded in `docs/ai-workforce/task-board.md` — start from that section, do not re-derive
   it. `T-008` closes the test holes the `T-003a` review identified (production-path rollback coverage,
   a deck-deletion cascade test, `SQLiteConstraintException` instead of `catch (t: Throwable)`, and one
   overstated test name).
7. **`main` is pushed and level with `origin/main`.** The push was authorized explicitly by the user
   on 2026-10-19 (`4bc2c48..4653eb2 main -> main`, repo `ralph12-dev/UEPflip`). Verify the head with
   `git log --oneline -4` rather than trusting a hash recorded here (D-012). A further push needs its
   own authorization; nothing is staged.

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
