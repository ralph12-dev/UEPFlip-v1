# AI Workforce — Test Results Log

Append-only. One entry per command actually run. Record what happened, including failures.
Distinguish compilation from tests from runtime verification. Never mark `PASS` for a command that
was not executed.

---

### 2026-10-19 · T-001 · verification of the workforce bootstrap

| Command | Result | Output / notes |
|---|---|---|
| `.\gradlew.bat :app:compileDebugKotlin testDebugUnitTest --console=plain` | PASS | `BUILD SUCCESSFUL in 14s`, 30 actionable tasks (4 executed, 26 up-to-date). Compiles AND the JVM suite is green — two different things in one run. |
| `.\gradlew.bat assembleDebug --console=plain` | PASS | `BUILD SUCCESSFUL in 9s`; `:app:assembleDebug` executed. APK built. |
| subagent probe — `qa-engineer` write `app/src/main/java/.../domain/permtest/PermProbe.kt` | REFUSED | `{"error":{"type":"permission.rejected","message":"Permission denied: edit"}}` |
| subagent probe — `qa-engineer` write `.opencode/permtest_qa.txt` | REFUSED | same `permission.rejected` payload (absolute/relative coverage) |
| subagent probe — `qa-engineer` write `app/src/test/java/.../PermProbeTest.kt` | ALLOWED | file created in its own lane; deleted afterwards, verified gone |
| subagent probe — `qa-engineer` shell `git status --short` | ALLOWED | listed ` M AGENTS.md`, `?? .opencode/`, the probe file |
| subagent probe — `qa-engineer` shell `git commit -m probe` | REFUSED | `permission.rejected: shell`, refused before the command ran |
| subagent probe — `code-reviewer` write/edit tools | NOT_AVAILABLE | `ERROR: Unknown tool 'write'` / `'edit'` — read-only at the tool-catalog layer too |
| subagent probe — `code-reviewer` shell `git status --short` | ALLOWED | read-only allowlist works |
| subagent probe — `code-reviewer` shell `Write-Output hello` | REFUSED | `{"error":{"type":"permission.rejected","message":"Permission denied: shell"}}` |
| `opencode api get "/api/agent?location[directory]=<root>"` | PASS | 7 custom agents listed, `orchestrator` first as `primary` |
| `opencode api get /api/agent` (no location) | MISLEADING | returns a **default** location's agents; only built-ins appear. Not a failure of the config — see `handoff.md` §9. |

### 2026-10-19 00:45 (+08:00) · T-002 · verification of the handoff protocol

| Command | Result | Output / notes |
|---|---|---|
| `opencode api get "/api/agent?location[directory]=<root>"` | PASS | 14 agents (7 custom + 7 built-in). `orchestrator` now carries `allow: handoff.md`, `allow: **/handoff.md`, `allow: docs/ai-workforce/**`, `allow: **/docs/ai-workforce/**`. `qa-engineer` and `code-reviewer` each carry the four matching `deny:` rules. |
| `opencode api get "/api/command?location[directory]=<root>"` | PASS | `handoff` is registered with description `Write a durable project handoff checkpoint before context is lost`. The endpoint returns only `name` + `description`, so `agent: orchestrator` in the frontmatter is **not** visible from the API — it is verified by file inspection only. |
| `opencode api get "/api/config?location[directory]=<root>"` | PASS | `instructions: ["handoff.md", "docs/ai-workforce/task-board.md"]`; `default_agent: orchestrator`. |
| `.\gradlew.bat testDebugUnitTest --console=plain` | PASS | `BUILD SUCCESSFUL in 6s`, 30 actionable tasks, all up-to-date, exit 0, config cache reused. The JVM suite is green after the doc/config changes. |
| `.\gradlew.bat connectedDebugAndroidTest` | **NOT_RUN** | no device or emulator attached in this workspace. Runtime behaviour remains unverified. |

### 2026-10-19 00:57 (+08:00) · T-003a · de-risk run of the existing instrumented suite

Purpose: prove the instrumented harness works **before** writing tests. Nobody had ever run
`connectedDebugAndroidTest` in this workspace, and `androidx.test:runner` is not declared in
`app/build.gradle.kts` — it arrives only transitively via `androidx.test.ext:junit` and espresso.

| Command | Result | Output / notes |
|---|---|---|
| `emulator -avd Pixel_9 -no-boot-anim -no-audio` | OK | booted in background, serial `emulator-5554` |
| `adb shell getprop sys.boot_completed` | OK | `sys.boot_completed=1`; `ro.build.version.sdk=36`, `ro.build.version.release=16` |
| `.\\gradlew.bat connectedDebugAndroidTest --console=plain` | **PASS** | `BUILD SUCCESSFUL in 1m 37s`, 74 actionable tasks (34 executed, 40 up-to-date), exit 0. `Running tests on devices: Pixel_9(AVD) - 16`. Existing `ExampleInstrumentedTest` passed. Runner classpath resolves transitively — **no Gradle change required**. |

**Device under test:** `emulator-5554`, Pixel_9 AVD, **API 36 / Android 16**, x86_64.
**Mismatch to note:** the app targets `targetSdk 37` while the device is API 36. Installation and
execution succeeded; a `targetSdk` above the device API is not a blocker, but the suite has not
been exercised on an API 37 image.

Only `sun.misc.Unsafe` / protobuf `UnsafeUtil` deprecation warnings appeared — pre-existing and
unrelated to this project.

**Not verified by anything above:** app behaviour on a device, Room migrations against an upgraded
install, live Gemini calls, Remote Config key resolution. Passing `testDebugUnitTest` does not
cover any of them.

### 2026-10-19 01:10–01:17 (+08:00) · T-003a · Room persistence tests — verified and reviewed

Delegated to `qa-engineer` (background session `ses_eb00b17d5ffeGZAabNTa87QHjs`), then
independently re-verified by the orchestrator. **The orchestrator did not take the agent's
numbers on trust** — every command below was re-run here.

| Command | Result | Output / notes |
|---|---|---|
| `.\\gradlew.bat connectedDebugAndroidTest --console=plain` (agent run) | **PASS** | `BUILD SUCCESSFUL in 42s`. Report XML `app\build\outputs\androidTest-results\connected\debug\TEST-Pixel_9(AVD) - 16.xml` → `<testsuites tests="11" failures="0" errors="0" skipped="0">`, of which `AppDatabaseTest tests="10"` and `ExampleInstrumentedTest tests="1"`. |
| `.\\gradlew.bat connectedDebugAndroidTest testDebugUnitTest --console=plain --rerun` (orchestrator's own re-run) | **PASS** | `BUILD SUCCESSFUL in 26s`, exit 0, 80 actionable tasks (2 executed, both forced). The suite is reproducible, not a one-off green. |
| tally of `app\build\test-results\testDebugUnitTest\*.xml` | **PASS** | **57 tests / 7 suites / 0 failures / 0 errors / 0 skipped** — `AiFlashcardGeneratorTest` 17, `SampleDataSeederTest` 9, `DeckStatsTest` 9, `Sm2SchedulerTest` 9, `DeckNamingTest` 8, `ReviewApplicationTest` 4, `ExampleUnitTest` 1. |
| `git status --short app/src/main` | **PASS** | **empty** — production code untouched, lane boundary held. |
| `git status --short` | PASS | only addition is `?? app/src/androidTest/java/.../data/`; `M AGENTS.md`, `?? .opencode/`, `?? docs/`, `?? handoff.md` are the pre-existing `T-001`/`T-002` record work. |

**Device under test:** `emulator-5554`, Pixel_9 AVD, **API 36 / Android 16** — confirmed twice,
by `adb shell getprop ro.build.version.sdk` (orchestrator) and by the report filename suffix.

**Review:** `code-reviewer`, session `ses_eaffb45afffeKTTJ8i7jHQUWbj` — verdict
**APPROVE WITH CONCERNS**. Full findings in `logs/task-003.md`; the two `MEDIUM` items are the
substantive ones:

1. The rollback test wraps **its own** `db.withTransaction { }` around two DAO inserts, so a
   mutant that deleted `database.withTransaction` from `FlashcardRepository.processCardReview`
   would still pass all 10 tests. It proves FK enforcement and Room's rollback, which is real —
   but it does **not** prove the repository's review transaction. Recorded as a coverage limit,
   not as coverage.
2. `DocumentEntity` declares `Index("deckId")` and a KDoc claiming a foreign key that does not
   exist, so deleting a deck does **not** cascade its documents — including imported PDFs' full
   `extractedText`. Silent storage leak. Pre-existing production issue, not a defect of this diff
   → `T-007`.

`code-reviewer` could not run Gradle (read-only + git-only shell allowlist) and said so; it
independently read and tallied both report-XML sets instead, matching the numbers above.

**Unverified by anything above:** the migration path (still `T-006`), Flow re-emission over time
(the tests use `first()` snapshots), concurrent writers, and real-file (non-in-memory) database
behaviour.

### 2026-10-19 01:42 (+08:00) · T-005 · committing the workforce (user-authorized)

AGENTS.md forbids committing without authorization; it was granted explicitly in-session and is
recorded in `logs/task-005.md`.

| Command | Result | Output / notes |
|---|---|---|
| `git log --oneline -12` | OK | checked existing style first: `docs:`, `fix:`, `test:`, `feature:` |
| `git add AGENTS.md .opencode docs handoff.md` + `git diff --cached --stat` | **PASS** | 17 files, 2090 insertions, inspected **before** committing — no `local.properties`, no scratch build logs |
| `git commit` (config) | **PASS** | `11b7058 chore: add AI engineering workforce and durable handoff record`, exit 0 |
| `git add app/src/androidTest` + `git commit` (tests) | **PASS** | `e106f30 test: add Room persistence instrumented tests (T-003a)`, 1 file / 380 insertions, exit 0 |
| `git status --short` | **PASS** | **clean** — nothing staged, nothing untracked |
| `git log --oneline -3` | PASS | `e106f30`, `11b7058`, `4bc2c48` |

Two commits on purpose, so the tests and the workforce config can be reverted independently
(decision D-012). Line-ending warnings (`LF will be replaced by CRLF`) appeared for every file —
benign autocrlf notices on a repo with no `.gitattributes`, not content changes.

**Not done:** no `git push`. `main` is local-only; pushing needs its own authorization, and
`git push` / `git reset --hard` / force-push are separately guarded in
`.opencode/opencode.jsonc`.

### 2026-10-19 · T-004 / T-009 · live-AI bring-up — **no build, no tests run**

The user invoked **`DO NOT BUILD AND TEST`** for this session. Nothing below is a test result; this
entry exists because recording that a command was *not* run is the point of this log. Read no green
out of it.

| Command | Result | Output / notes |
|---|---|---|
| `.\gradlew.bat :app:compileDebugKotlin` | **NOT_RUN** | Suppressed by explicit user instruction. The `T-009` patch is **compile-unverified**. |
| `.\gradlew.bat testDebugUnitTest` | **NOT_RUN** | Same. `AiFlashcardGeneratorTest` (17 tests) was **not** re-run. |
| `.\gradlew.bat connectedDebugAndroidTest` | **NOT_RUN** | Same; needs a device. No instrumented run has happened since 2026-10-19 01:16. |
| grep `firebase-ai` \| `FirebaseAI` \| `GenerativeModel` \| `com.google.firebase.ai` | NO_MATCHES | The app never uses the Firebase AI Logic SDK, so enabling "Gemini Developer API" inside AI Logic cannot configure it. `app/build.gradle.kts` declares only `firebase-config-ktx` and `firebase-analytics-ktx` among Firebase artifacts. |
| grep `gemini-2.0-flash` \| `DEFAULT_ENDPOINT` | 1 production hit | `AiFlashcardGenerator.DEFAULT_ENDPOINT` → `.../v1beta/models/gemini-2.0-flash:generateContent`. Tests pass their own `endpoint = "http://localhost/test"`, so **nothing in the suite ever asserted the real model id** — this is why 57 green JVM tests coexisted with a completely broken AI path. |
| read `app/google-services.json` → `project_info` | OK | Firebase project `uepflip-flashcard`, number `158917882025`, client `com.example.uepflip_flashcard`. The Remote Config parameter must live in **this** project. |
| user-observed device error `error 404` | OBSERVED (by user, not reproduced here) | Only reachable after a completed Gemini round trip: `AiFlashcardGenerator.requestModelText` throws locally on a blank key, so a `404` can only come from a request that reached Google with a non-blank key. That proves Remote Config now serves `gemini_api_key`, **without** relying on any claim about which status code an invalid key returns. |

**Root cause, verified by web search (external to the repo):** `gemini-2.0-flash` was shut down on
2026-06-01 and now returns `404` from `generativelanguage.googleapis.com`. The project's `AGENTS.md`
and `README.md` both still describe the model only implicitly, through the constant.

**Patch applied** (ai-engineer lane: `data/service/**`; `README.md` has no specialist lane — it was
edited by the orchestrator, and `AGENTS.md` has since been updated to list it as a shared,
orchestrator-owned file): `DEFAULT_MODEL =
"gemini-flash-latest"`, `resolveModel()` / `isValidModelId()` / `endpointFor()` with
`^[A-Za-z0-9][A-Za-z0-9._-]{0,63}$` validation and `models/`-prefix stripping, and an optional Remote
Config override `gemini_model`. Decision D-013.

**Independent review — `code-reviewer`, session `ses_eafb9783dffe7vIY7q6Sb4xTuN`:** verdict
**APPROVE WITH NITS**, no BLOCKER, no HIGH. Inspection-only (Gradle was off-limits, so it could not
confirm the code compiles and said so explicitly). It confirmed: companion initialisation order is
safe, `const val` → `val` on `DEFAULT_ENDPOINT` is harmless (its only reference is the constructor
default), the parenthesised `try` is load-bearing and valid, `app/src/test/**` still compiles, the
validator cannot redirect the request off-host, and **no existing test could have caught the original
`404`** — nor could any **hermetic/offline** JVM test, which is what this suite is: only a live call
can know a model was retired. (A networked JVM test *could* detect it, but that would put a real key
and a network dependency into the unit suite — rejected as cost/flakiness, not as impossibility.) Its MEDIUM
findings are folded in above; the remaining ones are recorded as handoff §12 follow-ups.

**Not verified by anything above:** that the patch compiles; that the user's key can serve
`gemini-flash-latest`; that the app can generate a single real flashcard. The review was static — it
was not allowed to run Gradle either. The first two are cheap to settle:
`:app:compileDebugKotlin`, and
`curl "https://generativelanguage.googleapis.com/v1beta/models?key=YOUR_KEY"` to list what the key
actually serves.

### 2026-10-19 · T-010 · model-resolution unit test — **written, never compiled, never run**

Raised by the `T-009` review: the new public surface (`resolveModel`, `isValidModelId`,
`endpointFor`, `DEFAULT_MODEL`) shipped with zero tests.

| Command | Result | Output / notes |
|---|---|---|
| `.\gradlew.bat testDebugUnitTest --tests "…AiModelResolutionTest"` | **NOT_RUN** | Frozen. This is the fastest signal on the new file once the freeze lifts. |
| `.\gradlew.bat testDebugUnitTest` | **NOT_RUN** | Frozen. The file is additive — no existing test was modified. |
| orchestrator inspection of the new file | INSPECTED | Read in full: 20 `@Test` methods; only `AiFlashcardGenerator` companion members and `org.junit.Assert` are referenced; no new dependency, no coroutines, no mocks. Spot-checked the expected/actual values against the production source **by hand** (blank/`null`/`models/`-prefix/junk/64-vs-65-char/leading-non-alphanumeric cases). Independently re-read by `code-reviewer` in a second pass, which judged it compile-safe by inspection and found no assertion that would fail today. |

`app/src/test/java/com/example/uepflip_flashcard/data/service/AiModelResolutionTest.kt` — **untracked**
(`git clean -fd` would delete it) and **uncompiled**. Reported by `qa-engineer` (session
`ses_eafb53eebffeYDDlUwTpJlMhgm`) with no Gradle invoked, matching the freeze; the orchestrator
inspected the file rather than trusting the report.

**Not verified:** that the file compiles or passes. A compile error in `app/src/test/**` would turn
`compileDebugUnitTestKotlin` red for the whole JVM suite, which is why its surface is deliberately
narrow. Explicitly out of scope: whether Google still serves any given model id — a JVM test cannot
answer that, and the file's KDoc says so.

### 2026-10-19 · T-012 · thinking-level wire tests — **written, never compiled, never run**

Raised by the hint-latency fix: `fastThinkingLevel` is the only place the request body can differ
from the wire format that shipped, so it needs a regression lock on both branches.

| Command | Result | Output / notes |
|---|---|---|
| `.\gradlew.bat :app:compileDebugKotlin` | **NOT_RUN** | Frozen by `DO NOT BUILD AND TEST`. |
| `.\gradlew.bat testDebugUnitTest --tests "…AiFlashcardGeneratorTest"` | **NOT_RUN** | Frozen. Covers the three new cases below. |
| `.\gradlew.bat testDebugUnitTest` | **NOT_RUN** | Frozen. The last green run (57 tests / 7 suites) predates `T-012` and says nothing about it. |
| orchestrator inspection of the diff | INSPECTED | `app/src/test/.../AiFlashcardGeneratorTest.kt`, `+65` lines, no production file touched by `qa-engineer`. Assertions decode the body with the existing Moshi map adapter instead of comparing raw strings, so key order is not under test. |

New cases (session `ses_eaf9d23a5ffer4XM4ezdBvTf67`):

1. `hintRequestsLowThinkingLevelWhenConfigured` — `generationConfig.thinkingConfig.thinkingLevel == "low"`.
2. `hintOmitsGenerationConfigWithoutThinkingLevel` — the key is absent when no level is configured
   (the Gemini 2.x compatibility path).
3. `flashcardGenerationNeverRequestsAThinkingLevel` — card generation ignores a configured level.

**Not verified:** that these compile or pass, that `buildRequestBody` emits the shape the assertions
expect, and that the live endpoint accepts it. By design they do **not** cover
`evaluateAnswer`/`generateExplanation`, `AiGeneratorProvider`'s model→level decision, or any network
behaviour.

### 2026-10-19 · T-014 · stored hints — **written, never compiled, never run**

Two suites were extended: the JVM parser contract and the instrumented DAO round trip.

| Command | Result | Output / notes |
|---|---|---|
| `.\gradlew.bat :app:compileDebugKotlin` | **NOT_RUN** | Frozen by `DO NOT BUILD AND TEST`. |
| `.\gradlew.bat testDebugUnitTest --tests "…AiFlashcardGeneratorTest"` | **NOT_RUN** | Frozen. 7 new hint cases (and the 3 `T-012` cases) unrun. |
| `.\gradlew.bat testDebugUnitTest` | **NOT_RUN** | Frozen. The last green run (57 tests / 7 suites) predates all AI work. |
| `.\gradlew.bat connectedDebugAndroidTest --tests "…AppDatabaseTest"` | **NOT_RUN** | Frozen; no device was started. The two new DAO tests are unrun. |
| `code-reviewer` on the hint diff | **see handoff §11** | Inspection-only; it cannot verify compilation or the migration. |

JVM cases (`AiFlashcardGeneratorTest`, session `ses_eaf874012ffe6A6dqaDmQsQDSQ`):

1. `parsesTheHintFromTheFlashcardResponse` — hint survives trimming.
2. `aResponseWithoutAHintStillParses` — old response shape parses with `hint == null`.
3. `whitespaceOnlyHintBecomesNull` — blank hint normalises to `null`, never `""`.
4. `aHintThatRepeatsTheAnswerIsDroppedWithoutDroppingTheCard` — case-insensitive leak; card kept.
5. `answersShorterThanThreeCharactersAreNotLeakChecked` — pins the documented threshold (`42`).
6. `capsAnOverlongHintAtAWordBoundary` — 300-char fixture plus a fixture whose cap falls mid-word;
   asserts `<= 240`, trimmed, and only whole tokens survive.
7. `flashcardPromptAsksForAHintForEveryCard` — asserts the requested JSON fragment.

Instrumented cases (`AppDatabaseTest`):

8. `updateHint_roundTrips_andLeavesSchedulingStateUntouched` — explicit `updatedAt`; every scheduling
   field compared before/after, plus full-entity equality with only `hint`/`updatedAt` changed.
9. `freshlyInsertedCard_hasNullHint_neverAnEmptyString`.

**Not verified:** that either file compiles or passes, that Room accepts the migrated schema, or that
Gemini returns usable hints. The `MIGRATION_2_3` upgrade path has **no automated test** — the migration
is private and `exportSchema = false`, the same `T-006` blocker — so a v2 database upgraded on a device
is the only proof.


### 2026-10-31 — T-016 — full UI merge verification (repo `UEPFLIP_`)

| Command | Result | Output / notes |
|---|---|---|
| `./gradlew.bat :app:assembleDebug :app:testDebugUnitTest --console=plain` | PASS | `BUILD SUCCESSFUL in 2m 07s`, 46 tasks executed. First green build that covers the T-009 / T-012 / T-014 tree changes together with the merged UI. |
| `app/build/outputs/apk/debug/app-debug.apk` | PASS | APK produced, ~15.0 MB (14,999,429 B). |
| `app/build/test-results/testDebugUnitTest` | PASS | 8 suites, **87 tests, 0 failures, 0 errors, 0 skipped**. Includes `AiModelResolutionTest` (20 tests) — previously claimed unverified. |
| `git diff --ignore-cr-at-eol --quiet` sweep + `git checkout --` | PASS | 128 EOL-only modifications reverted; 12 real changes left for the commit. Prevents line-ending noise from entering history. |
| `connectedDebugAndroidTest` | NOT_RUN | no device/emulator attached during the merge. |
| device run of the redesigned Home/Stats/Decks | NOT_RUN | no UI-level verification recorded; do not claim rendering correctness. |
### 2026-11-03 · verification · all gates green

| Command | Result | Output / notes |
|---|---|---|
| `.\gradlew.bat :app:compileDebugKotlin` | PASS | `BUILD SUCCESSFUL in 2s`, 30 actionable tasks |
| `.\gradlew.bat assembleDebug --console=plain` | PASS | `BUILD SUCCESSFUL in 1s`, APK at `app/build/outputs/apk/debug/app-debug.apk` |
| `.\gradlew.bat testDebugUnitTest --console=plain` | PASS | 8 suites, **87 tests, 0 failures, 0 errors**, 0 skipped |

