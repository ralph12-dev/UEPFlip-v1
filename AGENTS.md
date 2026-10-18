# AGENTS.md

UEPflip — offline-first Android flashcard app. Single Gradle module `:app`, Kotlin + ViewBinding,
MVVM/Repository over Room, with an SM-2 spaced-repetition engine. Package / namespace / appId:
`com.example.uepflip_flashcard`.

This project is worked on by an AI engineering team defined in `.opencode/agents/`: an
`orchestrator` plus the specialists `android-engineer`, `ui-engineer`, `data-engineer`,
`ai-engineer`, `qa-engineer`, and the read-only `code-reviewer`. Their roles, permitted files, and
verification duties are defined in **Android Project Engineering Rules** at the end of this file.

## Commands (run from repo root; this repo is on Windows)

- Compile check: `.\gradlew.bat :app:compileDebugKotlin`
- Build debug APK: `.\gradlew.bat assembleDebug`
- Unit tests (JVM): `.\gradlew.bat testDebugUnitTest`
- Single test class: `.\gradlew.bat testDebugUnitTest --tests "com.example.uepflip_flashcard.domain.scheduler.Sm2SchedulerTest"`
- Instrumented tests (needs emulator/device): `.\gradlew.bat connectedDebugAndroidTest`
- POSIX shells: use `./gradlew` instead of `.\gradlew.bat`.

There is no CI, lint, or formatter config in the repo. The Android `lint` task exists but is not wired
into anything.

JVM tests that should stay green (no device needed): `Sm2SchedulerTest` (SM-2 maths),
`ReviewApplicationTest` (which fields a review persists), `DeckStatsTest` (mastery/due/labels),
`AiFlashcardGeneratorTest` (Gemini request/response handling via a stubbed OkHttp client, including
hint + answer-evaluation parsing), and `AiModelResolutionTest` (Gemini model-id resolution: `models/`
prefix stripping, rejection of junk, and the exact endpoint URL). `AiModelResolutionTest` is
**currently unverified** — it was added while builds were frozen and has never been compiled, so it
must be run before its green is claimed.
`app/build.gradle.kts` sets `testOptions.unitTests.isReturnDefaultValues = true` so `android.util.Log`
calls on the JVM don't throw.

## Toolchain (pinned — do not casually change)

- Gradle wrapper 9.6.0, AGP 9.4.1, Kotlin 2.3.21, KSP 2.3.9, Room 2.8.5.
- `compileSdk`/`targetSdk` 37, `minSdk` 26, Java 11 source/target.
- The Gradle daemon JVM is pinned to **JDK 25** in `gradle/gradle-daemon-jvm.properties` and
  auto-provisioned via the foojay resolver. An older JDK breaks configuration.
- All versions live in `gradle/libs.versions.toml`. `gradle/libs.versions.toml.bak` / `.old` and root
  `repo_backup.kt` are stale untracked leftovers — ignore or delete, don't edit.
- Config cache is on (`org.gradle.configuration-cache=true`). The
  `android.disallowKotlinSourceSets=false` experimental warning is expected.

## Architecture

- Data: `data/local` (Room entities / DAOs / `AppDatabase`), `data/repository/FlashcardRepository`,
  `domain/scheduler` (SM-2: `Sm2Scheduler`, `SpacedRepetitionScheduler`, `ScheduleResult`,
  `ReviewApplication`). `domain/DeckStats` holds the pure "mastery / due / label" helpers the UI uses.
- `CardEntity` carries scheduling state (`repetitions`, `interval`, `easeFactor`, `nextReviewAt`,
  `lastReviewedAt`, `rating`) plus a nullable `hint` (pre-generated study nudge; `null` means "not
  generated yet", never `""`); `ReviewEntity` logs one row per review. `AppDatabase` is **version 3**
  with `exportSchema = false` and hand-written migrations: `MIGRATION_1_2` (documents table + three
  `cards` columns) and `MIGRATION_2_3` (`cards.hint`). If you change an entity, bump the version and
  add a migration — Round trips are validated at open, so a schema change without a version bump
  crashes the app on first DB access.
- Ratings are `1=Hard, 2=Medium, 3=Easy` (`Sm2Scheduler.RATING_*`), mapped internally to SM-2
  quality 2/4/5. Intervals: 1 day, 6 days, then `interval * newEaseFactor`.
- `FlashcardRepository` owns `AppDatabase` so multi-table work (recording a review = insert
  `ReviewEntity` + update `CardEntity`) runs in `withTransaction`. Build it with
  `FlashcardRepository.create(context)`.
- AI flashcard creation: the Create screen (`CreateFragment`) offers two sources — a typed
  topic/lesson or an uploaded PDF — both routed through `ui/document/DocumentImportViewModel`
  (`generateFromText(...)` / `importDocument(...)`). PDF text comes from
  `data/service/PdfTextExtractor` (Apache PDFBox 2.0.24, runs on-device). Both paths call
  `data/service/AiFlashcardGenerator` (OkHttp + Moshi → Gemini `generateContent`), whose `isTopic`
  flag switches the prompt between "expand this topic" and "summarise this material".
  `data/service/AiGeneratorProvider` resolves the API key from Remote Config and is shared by every AI
  feature. `AiFlashcardGenerator` accepts an injectable `OkHttpClient` so it can be unit tested
  without the network; `domain/DeckNaming` derives the default deck title from the file name or topic.
- Every generated card also carries a **`hint`** in the same response: one or two sentences that nudge
  the student without stating or paraphrasing the answer. `DocumentImportViewModel` and
  `TutorViewModel` persist it on `CardEntity.hint`, so the study screen shows it **instantly, with no
  network call**. `parseFlashcards` sanitises it (trim, 240-char word-boundary cap, and `null` when it
  contains the answer verbatim — a leak costs the hint, never the card). Cards created before this
  existed, and the debug sample data, have `hint == null`; the study screen then falls back to
  `generateHint` (which applies the same trim/cap/leak rule) and saves the result via
  `FlashcardRepository.saveHint(...)`, which uses a targeted `UPDATE` so it cannot clobber scheduling
  state.
- The same generator also backs the study screen's hint and the tutor's grading, all sharing one
  `requestModelText` call: `generateHint(question, answer)` (a nudge that must not reveal the answer),
  `evaluateAnswer(question, referenceAnswer, studentAnswer)` (returns an `AnswerEvaluation` whose
  0-100 score comes from the model and is clamped, never invented) and
  `generateExplanation(...)` behind the accuracy card's "View more explanation" button.
- Those three are **latency-sensitive**, so they pass the generator's `fastThinkingLevel` and send
  `generationConfig.thinkingConfig.thinkingLevel = "low"`: Gemini 3.x thinks at `medium`/`high` by
  default, and the non-streaming call shows nothing until thinking and the answer are both done.
  `generateFlashcards` deliberately sends no level (card quality wants the default). The field is
  emitted only for `gemini-3*`/`gemini-4*`/`*-latest` because Gemini 2.x rejects `thinkingLevel`
  (it uses `thinkingBudget`). Do **not** add a small `maxOutputTokens` to cut latency — the API counts
  thought tokens against it, so it truncates to empty output instead (decision D-014).
- `TutorViewModel` has two modes: plain chat turns a message into cards in the "AI Tutor" deck, while
  a `GradedCard` (handed over by `MainActivity.goTutor(question, answer, deckId)`) makes it grade the
  student's reply and emit a `Msg.ACCURACY` rendered by `item_chat_accuracy.xml`. `StudyViewModel`
  owns the AI hint and needs an `Application` (for Remote Config + localised errors).
- ViewModels are built by hand with a `FlashcardRepository` and per-screen factories. There is **no
  fragment-ktx** on the classpath, so `by viewModels {}` is unavailable: use
  `ViewModelProvider(this, XViewModel.factory(repo))[XViewModel::class.java]` and construct factories
  with `ui/SimpleViewModelFactory` (not `androidx.lifecycle.viewmodel.viewModelFactory`). No Hilt/Koin.

## UI layers

- Everything is now one app on the fragment shell in `ui/main`: `MainActivity` (launcher) hosts
  Home / Decks / Stats / Tutor tabs plus pushed detail screens `CreateFragment` and `StudyFragment`.
  All of it reads and writes Room; there is no hardcoded demo data in the UI layer (debug builds do
  seed real rows on first launch — see "Sample data").
- `MainActivity.show(f, checkId)` is for top-level destinations (clears the back stack), `push(f)` for
  detail screens. `MainActivity.goStudy(deckId)` / `goCreate()` / `goHome()` / `goDecks()` are the
  entry points fragments should use.
- The bottom bar's visibility is **derived, never set at the call site**: `HomeFragment`,
  `DecksFragment` and `StatsFragment` implement the `NavDestination` marker and `MainActivity` reads it
  in `FragmentManager.FragmentLifecycleCallbacks.onFragmentResumed`. Setting `nav.visibility` directly
  is what used to lose the bar — a reversed fragment transaction (Back / `popBackStack`) cannot restore
  a value the transaction never owned. `show()` also gates its back-stack pop on the same marker, so
  the marker is the single definition of "top-level". The Tutor screen deliberately is *not* a
  `NavDestination` (the design hides the bar there; the screen has its own back button).
- The whole UI is the fragment shell; the earlier Activity-based screens are gone. Tapping a deck row
  (Home or Decks) goes straight to study — there is no separate card-list screen, so cards are only
  created by the AI import (Create/Tutor) and are not hand-editable. Deck create/rename/delete live in
  `DecksFragment` (long-press a deck for rename/delete). `StudyViewModel` is shared by the shell and
  lives in `ui/study/`.
- `res/layout/` matches the designer's reference set exactly (13 files): `activity_main`,
  `activity_onboarding`, `fragment_create`, `fragment_decks`, `fragment_home`, `fragment_stats`,
  `fragment_study`, `fragment_tutor`, `item_chat_accuracy`, `item_chat_bot`, `item_chat_user`,
  `item_deck`, `item_onboarding`. The Tutor's accuracy card is `item_chat_accuracy`, inflated by
  `MsgAdapter` for `Msg.ACCURACY` and populated with the model's real score.
- Layout ids referenced via `findViewById` are not compiler-checked; verify against
  `res/layout/` when editing a fragment.
- The app is deliberately **light-only**: `Theme.UEPflip_flashcard` extends
  `Theme.Material3.Light.NoActionBar` and there is no `res/values-night/` override. All user-facing
  copy lives in `res/values/strings.xml` (including the `bottom_nav.xml` titles) — don't hardcode
  layout text.
- Study flow follows the design: the answer is revealed by tapping the card (`study_tap_reveal` /
  `study_tap_question`), the difficulty segment (`diff_easy` / `diff_normal` / `diff_hard`, always
  visible) only *selects* a rating, and `btn_next_card` commits it through
  `StudyViewModel.submitRating()` — which is a no-op until the answer is revealed, so a card can
  never be rated blind. The selected chip wears `bg_segment_selected` like the Create screen's
  Topic/PDF toggle. The UI label for `Sm2Scheduler.RATING_MEDIUM` is "Normal" (`@string/normal`);
  the domain constant keeps the SM-2 name.
- `StudyViewModel` owns the hint (`requestHint()` → `hint` / `isHintLoading`). A card with a stored
  `hint` renders it immediately with no spinner and no network; a card without one calls
  `generateHint`, shows the result, then persists it with `repository.saveHint(...)` (best-effort).
  The on-screen hint is cleared when the card changes.

## Firebase / secrets

- `app/google-services.json` is committed and required by the `com.google.gms.google-services` plugin.
- The Gemini API key is **not** in `local.properties` or `BuildConfig`. It is read at runtime from
  Firebase Remote Config under the key `gemini_api_key` (default empty → AI import stays disabled until
  configured).
- The **model id** also comes from Remote Config, under `gemini_model`, defaulting to
  `AiFlashcardGenerator.DEFAULT_MODEL` (`gemini-flash-latest`; `resolveModel()` / `endpointFor()`).
  A retired model id fails with `404` for every AI feature at once — `gemini-2.0-flash` was shut down
  on 2026-06-01 exactly that way — which is why the id is configuration rather than a constant: a
  retirement becomes a console change, not a release (decision D-013). Blank or malformed values fall
  back to the default, and a leading `models/` is stripped so an id can be pasted from the API listing.
- The app does **not** use the Firebase AI Logic SDK (`firebase-ai`). Enabling Gemini Developer API on
  that console page does not configure this app: it reads the key from Remote Config and calls the
  Gemini REST API directly through OkHttp. If AI reports `404`, check `gemini_model` before the code.

## Sample data (debug builds only)

- `data/sample/SampleDataSeeder.seedIfFirstRun(context)` fills a **debuggable** build with four
  realistic decks — one untouched, one partly mature, one mostly mature, one fully overdue — plus
  their cards and a review history, so Home / My Decks / Statistics / Study all have something real
  to show without a Gemini key. `MainActivity.onCreate` calls it once, off the main thread.
- It is deliberately inert in product builds: it returns immediately unless
  `ApplicationInfo.FLAG_DEBUGGABLE` is set, it only writes when the database is empty, and it records
  in the `uepflip_sample_data` prefs that it ran, so deleting the sample decks keeps them deleted.
  Clear the app's data to make the samples appear again.
- Sample cards are derived from the real SM-2 step sequence (`INTERVAL_STEPS`), so a seeded card still
  schedules sensibly once it is reviewed for real. `SampleDataSeederTest` asserts the data actually
  yields due cards, mature cards, future-scheduled cards, an untouched deck, an overdue deck and
  history consistent with each card's interval.

## Repo hygiene

- The root used to fill up with untracked scratch build logs (`build*.txt`, `build*.log`,
  `full_build*`, `latest_build.txt`, `fresh_build.txt`); `.gitignore` now covers those, plus
  `repo_backup.kt` and `gradle/libs.versions.toml.bak`/`.old`. Don't treat them as current status.
- `local.properties` is gitignored (`sdk.dir` only).
- The **old** `handoff.md` was deleted in commit `d5776c8` and is **not** at HEAD —
  `git show HEAD:handoff.md` fails. It survives only at `git show d5776c8^:handoff.md`, and it
  described an obsolete Activity-based phase (`DeckListActivity`, `StudyActivity`) that the
  fragment shell replaced. The current root `handoff.md` supersedes it. Do not restore the old
  file.

---

# Android Project Engineering Rules

## Role

Act as the engineering lead and orchestrator for this Android project.

The team runs on OpenCode, Git, and Android Studio: **OpenCode is the engineering team's
interface, Git is the coordination and rollback mechanism, and Android Studio is the environment
for building, running, and debugging the application.**

These are logical roles, not six autonomous agents running simultaneously. The key principle: the
orchestrator coordinates; specialist agents execute bounded tasks; the build and tests determine
whether the work is actually complete.

## Specialist agents (`.opencode/agents/`)

| Agent file | Role | May write | Must not |
| --- | --- | --- | --- |
| `orchestrator.md` | Engineering lead; plans, assigns ownership, delegates, verifies | shared files + anything it verifies and fixes itself | commit/push without authorization |
| `android-engineer.md` | Kotlin logic and architecture | `domain/**`, `ui/main/**`, `ui/study/**`, `ui/SimpleViewModelFactory.kt` | `res/`, `data/`, tests, Gradle, manifest |
| `ui-engineer.md` | Layouts, drawables, values, themes, string copy | `app/src/main/res/**` | any Kotlin/Java, manifest, Gradle |
| `data-engineer.md` | Room entities, DAOs, migrations, repositories, seed data | `data/local/**`, `data/repository/**`, `data/model/**`, `data/sample/**` | `data/service/**`, `ui/**`, `domain/**` |
| `ai-engineer.md` | Gemini, Remote Config, PDF extraction, import flow | `data/service/**`, `ui/document/**` | `domain/**`, `ui/main/**`, `ui/study/**` |
| `qa-engineer.md` | Unit and instrumented tests, regressions, triage | `app/src/test/**`, `app/src/androidTest/**` | **all production code** |
| `code-reviewer.md` | Independent review | **nothing — read-only** | edit, write, run arbitrary shell, launch agents |

Ownership is enforced in each agent file's `permissions` block (OpenCode V2 rule list), not just by
convention: a specialist that tries to edit outside its lane is refused by the tool layer.

## Before implementation

1. Inspect the repository, AGENTS.md files, Gradle configuration,
   AndroidManifest.xml, source code, resources, and existing tests.
2. Determine the app architecture and current implementation status.
3. Identify incomplete features, bugs, and missing tests.
4. Create a prioritized implementation plan with dependencies.
5. Delegate independent investigations to specialist subagents.

## Delegation

- UI and layouts: ui-engineer
- Kotlin logic and architecture: android-engineer
- Database and persistence: data-engineer
- AI and network integrations: ai-engineer
- Testing and regressions: qa-engineer
- Independent review: code-reviewer

Use the available subagent mechanism to delegate work.
Do not assume a specialist has completed a task until its result
has been returned and the changes have been verified.

## File ownership

Before parallel implementation, assign explicit file ownership.
Do not let multiple agents modify the same file concurrently.
Serialize changes to shared files such as Gradle scripts,
navigation graphs, AndroidManifest.xml, and shared models.

In this repo the shared files are: `build.gradle.kts`, `app/build.gradle.kts`,
`gradle/libs.versions.toml`, `gradle/gradle-daemon-jvm.properties`, `settings.gradle.kts`,
`gradle.properties`, `app/src/main/AndroidManifest.xml`, `res/values/strings.xml`, `README.md`,
`AGENTS.md`, `handoff.md`, everything under `docs/ai-workforce/`, and `.opencode/**`. **No specialist
owns these** — only the orchestrator changes them, one at a time.

The orchestrator is also the **handoff maintainer**: it is the only agent allowed to write
`handoff.md` and `docs/ai-workforce/**`. Every other specialist is explicitly denied those paths
and reports results upward instead, so concurrent agents can never overwrite the record.

A specialist whose task crosses its lane reports the dependency back to the orchestrator instead of
editing another specialist's file. The orchestrator sequences it as a follow-up for the owning lane.

## Engineering standards

- Preserve the established architecture and design.
- Do not replace working implementations without justification.
- Do not introduce unnecessary dependencies.
- Never hardcode secrets.
- Never delete user data or overwrite unrelated work.
- Do not commit, push, or release without authorization.

Additional constraints for this repository:

- The toolchain is pinned (see "Toolchain" above) — a dependency or Gradle change needs
  justification, not preference.
- Schema changes to Room require a version bump plus a hand-written migration; never use
  `fallbackToDestructiveMigration()`.
- All user-facing copy goes through `res/values/strings.xml`.
- The app is light-only; do not add `res/values-night/` unasked.

## Verification

1. Inspect the diff after each implementation task.
2. Run the relevant unit tests and Android lint checks.
3. Build the application using the project's Gradle wrapper (`.\gradlew.bat assembleDebug`).
4. Fix implementation-caused failures and retest.
5. Run the code reviewer after integration.
6. Report remaining failures and unverified behavior honestly.

This repo has no CI. There is no lint configuration wired into anything, so a lint claim must be
backed by the exact command that ran. `testDebugUnitTest` is the JVM suite; instrumented tests need
a device or emulator via `connectedDebugAndroidTest`.

## Completion criteria

A feature is complete only when its requirements are implemented,
its relevant tests have been run, and its changes have been reviewed.
A successful build alone does not prove functional correctness.

## Handoff Protocol

The project-root `handoff.md` is the persistent execution record.

At the beginning of every session:

1. Read `handoff.md`.
2. Inspect Git status and the current branch.
3. Verify the actual state of files relevant to the next task.
4. Reconcile discrepancies between the handoff and the repository.

During implementation:

- Update active task status when work materially changes.
- Record important architectural decisions and unresolved blockers.
- Record failed approaches when they provide useful diagnostic information.
- Never report unverified behavior as completed.

At every significant milestone:

1. Update completed and active tasks.
2. Record changed files and verification results.
3. Record the next concrete action.
4. Ensure the document reflects the actual workspace state.

Before context compaction or session termination:

1. Update `handoff.md` before losing operational context.
2. Record unfinished work and the exact next step.
3. Record any pending operations or unresolved failures.
4. Preserve relevant command results and technical decisions.
5. Keep the document concise and remove obsolete status information.

When multiple agents work concurrently:

- Assign one agent as the handoff maintainer.
- Allow other agents to report their results to that maintainer.
- Serialize edits to `handoff.md` to avoid conflicting updates.
- Do not allow an agent to overwrite another agent's active task record.

The handoff file is a checkpoint, not proof.
Inspect the repository and verify claims before acting on them.

In this repo the maintainer is the **orchestrator**: it holds the only `edit` permission for
`handoff.md` and `docs/ai-workforce/**`, and the six specialists are explicitly denied those paths.
Specialists therefore never write the record — they return their structured handoff block (files,
commands, tests, cross-lane items, risks) in their result, and the orchestrator transcribes it.
The `/handoff` command pins `agent: orchestrator` for the same reason.

### Pre-compaction checkpoint

When the context is getting long, issue this instruction to the active OpenCode agent — or run
`/handoff`, which submits exactly this:

```text
Prepare a durable project handoff before continuing.

1. Inspect the current repository and Git status.
2. Identify completed, active, pending, and blocked tasks.
3. Preserve architectural decisions, constraints, failed approaches,
   important errors, and exact next actions.
4. Record active agent assignments and file ownership.
5. Record verification commands and their actual results.
6. Update handoff.md without deleting still-relevant information.
7. Reconcile the document against the actual code and workspace.
8. Report any state that could not be verified.

Do not start unrelated implementation until the handoff is updated.
Do not claim the handoff is complete if the file was not saved.
```

### Task state tracking

Do not let `handoff.md` become an ever-growing activity log. It holds **current state** and points
at the detailed records:

```text
handoff.md
docs/
└── ai-workforce/
    ├── task-board.md
    ├── decisions.md
    ├── test-results.md
    └── logs/
        └── task-001.md
```

- `handoff.md` — the checkpoint: objective, status, architecture constraints, completed and active
  work, pending tasks, decisions, blockers, git state, verification record, next actions.
- `docs/ai-workforce/task-board.md` — authority for ownership, dependencies, and task status.
- `docs/ai-workforce/decisions.md` — append-only decision log. Never reverse an established
  decision without documenting why.
- `docs/ai-workforce/test-results.md` — append-only log of commands and actual results.
- `docs/ai-workforce/logs/task-NNN.md` — full per-task detail and debugging history.

`handoff.md` and `task-board.md` are both auto-loaded into every session through the `instructions`
key in `.opencode/opencode.jsonc`. Keep them concise so that stays cheap; long detail goes into the
logs. The orchestrator writes all of these files and nothing else may modify them.
