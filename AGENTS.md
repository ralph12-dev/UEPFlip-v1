# AGENTS.md

UEPflip — offline-first Android flashcard app. Single Gradle module `:app`, Kotlin + ViewBinding,
MVVM/Repository over Room, with an SM-2 spaced-repetition engine. Package / namespace / appId:
`com.example.uepflip_flashcard`.

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
`ReviewApplicationTest` (which fields a review persists), `DeckStatsTest` (mastery/due/labels) and
`AiFlashcardGeneratorTest` (Gemini request/response handling via a stubbed OkHttp client, including
hint + answer-evaluation parsing).
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
  `lastReviewedAt`, `rating`); `ReviewEntity` logs one row per review. `AppDatabase` is **version 2**
  with `exportSchema = false` and a hand-written `MIGRATION_1_2` (documents table + three `cards`
  columns). If you change an entity, bump the version and add a migration — Round trips are validated
  at open, so a schema change without a version bump crashes the app on first DB access.
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
- The same generator also backs the study screen's hint and the tutor's grading, all sharing one
  `requestModelText` call: `generateHint(question, answer)` (a nudge that must not reveal the answer),
  `evaluateAnswer(question, referenceAnswer, studentAnswer)` (returns an `AnswerEvaluation` whose
  0-100 score comes from the model and is clamped, never invented) and
  `generateExplanation(...)` behind the accuracy card's "View more explanation" button.
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
- `StudyViewModel` also owns the AI hint (`requestHint()` → `hint` / `isHintLoading`), which is not
  persisted and is cleared when the card changes.

## Firebase / secrets

- `app/google-services.json` is committed and required by the `com.google.gms.google-services` plugin.
- The Gemini API key is **not** in `local.properties` or `BuildConfig`. It is read at runtime from
  Firebase Remote Config under the key `gemini_api_key` (default empty → AI import stays disabled until
  configured).

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
- `local.properties` is gitignored (`sdk.dir` only). `handoff.md` was deleted from the working tree but
  is still available as prior-phase notes via `git show HEAD:handoff.md`.
