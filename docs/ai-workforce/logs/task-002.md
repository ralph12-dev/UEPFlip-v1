# T-002 — Handoff protocol and task-state split

- **Status:** DONE
- **Owner:** orchestrator
- **Date:** 2026-10-19
- **Depends on:** `T-001`
- **Related decisions:** `decisions.md` D-006, D-007, D-008

## Objective

Make the workforce maintain a durable execution record automatically, with the current state
separated from the growing history, and with the record protected from concurrent writes.

## What changed

| Path | Change |
|---|---|
| `handoff.md` | new — the 13-section checkpoint, populated from verified state only |
| `docs/ai-workforce/task-board.md` | new — ownership, dependencies, status, plus the logging conventions |
| `docs/ai-workforce/decisions.md` | new — decisions `D-001` … `D-009` |
| `docs/ai-workforce/test-results.md` | new — append-only command/result log |
| `docs/ai-workforce/logs/task-001.md` | new — the `T-001` detail |
| `.opencode/commands/handoff.md` | new — `/handoff` checkpoint prompt, `agent: orchestrator` |
| `.opencode/opencode.jsonc` | `instructions: ["handoff.md", "docs/ai-workforce/task-board.md"]` |
| `AGENTS.md` | `## Handoff Protocol` + pre-compaction prompt + task-state tracking; shared-files list extended; stale `git show HEAD:handoff.md` claim corrected |
| `.opencode/agents/orchestrator.md` | 4 `edit` allows for the record |
| `.opencode/agents/*.md` (6) | 4 `edit` denies each, appended **after** the lane allows so they win last-match-wins |

The denies were placed at the **end** of each specialist's permission list on purpose: V2 evaluates
rules in order and the last match wins, so a record-protecting deny must come after any lane allow.

## Verification

- `GET /api/agent?location[directory]=<root>` — 14 agents; orchestrator carries
  `allow: handoff.md | allow: **/handoff.md | allow: docs/ai-workforce/** | allow: **/docs/ai-workforce/**`;
  `qa-engineer` and `code-reviewer` carry the matching four `deny:` rules (spot-checked; all six
  carry them).
- `GET /api/command?location[directory]=<root>` — `handoff` registered with the expected
  description. The endpoint exposes only `name` and `description`, so `agent: orchestrator` is
  verified by inspecting the file, **not** by the API. Recorded honestly in `test-results.md`.
- `GET /api/config?location[directory]=<root>` — `instructions` and `default_agent: orchestrator`
  both loaded, so the JSONC parses.
- `.\gradlew.bat testDebugUnitTest` — `BUILD SUCCESSFUL in 6s`, 30/30 up-to-date, exit 0.
- `git status --short` — `M AGENTS.md`, `?? .opencode/`, `?? docs/`, `?? handoff.md`. No
  application source touched.

## Open items

- `connectedDebugAndroidTest` still not run — unchanged from `T-001`; tracked as `T-003`.
- The whole `.opencode/`, `docs/`, `handoff.md` set is untracked, so `git checkout` cannot recover
  it. Tracked by `T-005`, which needs authorization.
