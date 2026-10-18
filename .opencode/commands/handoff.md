---
description: Write a durable project handoff checkpoint before context is lost
agent: orchestrator
subagent: false
---

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

Keep `handoff.md` a concise checkpoint that points at `docs/ai-workforce/` for
the task board, the decision log, the test log, and per-task logs. Append to
those records rather than duplicating their detail in the checkpoint. Mark any
task you finished as `DONE` in `docs/ai-workforce/task-board.md`, and update the
sections of `handoff.md` that no longer match the workspace.
