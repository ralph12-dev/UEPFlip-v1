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
| T-004 | Configure `gemini_api_key` and verify the live AI flow | ai-engineer | T-002 | `data/service/**`, `ui/document/**` | NOT_STARTED |
| T-005 | Commit the workforce, docs, and handoff files | orchestrator | T-002 | tracked by Git | NOT_STARTED |
| T-006 | Real `MIGRATION_1_2` test via `MigrationTestHelper` | data-engineer + orchestrator + qa-engineer | T-003a | see blockers below | NOT_STARTED |
| T-007 | `DocumentEntity` missing FK → documents orphaned on deck delete | data-engineer | T-003a | `data/local/**` | NOT_STARTED |
| T-008 | Test follow-ups raised by the `T-003a` review (production-path rollback, deck-cascade test, `SQLiteConstraintException`, test naming) | qa-engineer | T-003a | `app/src/androidTest/**` | NOT_STARTED |

`T-005` needs explicit user authorization before any commit.

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
- New logs: `logs/task-NNN.md` matching a board row.
