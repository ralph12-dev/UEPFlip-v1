# T-005 — Commit the workforce and the T-003a tests

- **Status:** DONE
- **Owner:** `orchestrator`
- **Date:** 2026-10-19 01:42 (+08:00)
- **Depends on:** `T-002`
- **Authorization:** explicitly granted by the user in-session. AGENTS.md forbids committing
  without it, so this is recorded deliberately — the authorization is part of the task's evidence.

## Why it existed

Everything the workforce was made of — `.opencode/`, `docs/`, `handoff.md`, the `AGENTS.md`
changes — plus the `T-003a` test file was **untracked**. A single `git clean -fd` would have
deleted the entire engineering team with no recovery, and `git checkout` could not restore any of
it. The risk was flagged in `handoff.md` §8/§10/§12 from the moment it appeared.

## What was committed

Two commits, deliberately separate so either can be reverted alone:

| Commit | Contents | Size |
|---|---|---|
| `11b7058` `chore: add AI engineering workforce and durable handoff record` | `.opencode/**` (7 agent files, `opencode.jsonc`, `commands/handoff.md`), `docs/ai-workforce/**` (board, decisions, test results, 3 task logs), `handoff.md`, `AGENTS.md` | 17 files, 2090 insertions |
| `e106f30` `test: add Room persistence instrumented tests (T-003a)` | `app/src/androidTest/java/com/example/uepflip_flashcard/data/local/AppDatabaseTest.kt` | 1 file, 380 insertions |

## Verification

- Staged set inspected with `git diff --cached --stat` **before** committing: 17 files, exactly
  the intended ones. No `local.properties`, no scratch build logs (`build*.txt`, `build*.log`,
  `full_build*`, `latest_build.txt`, `fresh_build.txt` are gitignored and did not appear).
- `git status --short` after both commits: **clean** — nothing staged, nothing untracked.
- `git log --oneline -3`: `e106f30`, `11b7058`, `4bc2c48`.
- **Nothing was pushed.** `main` is local-only at `e106f30`.

## Notes

- Line-ending warnings (`LF will be replaced by CRLF`) appeared for all 17+1 files. Benign
  Windows autocrlf notices, not content changes — the repo has no `.gitattributes`, so this is the
  default behaviour on this machine.
- Conventional-commit prefixes follow the existing repo style (`docs:`, `fix:`, `test:`,
  `feature:`); `chore:` was used for the config commit since it is tooling rather than app code
  or documentation.
- After committing, the record files themselves changed (to reflect the new git state), so a third
  commit records that refresh. This is inherent to keeping a handoff file inside the repository
  it describes: the record cannot describe its own commit without changing itself.
