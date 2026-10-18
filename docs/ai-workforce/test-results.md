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
