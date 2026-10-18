# T-001 — Bootstrap the AI engineering workforce

- **Status:** DONE
- **Owner:** orchestrator
- **Dates:** 2026-10-18 → 2026-10-19
- **Related decision:** `decisions.md` D-001 … D-008

## Objective

Give this project a multi-agent engineering team in OpenCode where each agent has a defined role,
tools, responsibilities, and boundaries. OpenCode is the team's interface, Git is the coordination
and rollback mechanism, Android Studio builds, runs, and debugs the app. Agents must not be able to
overwrite each other's work.

## Outcome

7 agent files, global guardrails, a `/handoff` command, and the **Handoff Protocol** section in
`AGENTS.md`. Nothing in `app/src/**` was touched.

## Files created

| File | Role |
|---|---|
| `.opencode/agents/orchestrator.md` | `primary` — plans, assigns ownership, delegates, verifies, owns shared files |
| `.opencode/agents/android-engineer.md` | `subagent` — `domain/**`, `ui/main/**`, `ui/study/**`, `ui/SimpleViewModelFactory.kt` |
| `.opencode/agents/ui-engineer.md` | `subagent` — `app/src/main/res/**` |
| `.opencode/agents/data-engineer.md` | `subagent` — `data/{local,repository,model,sample}/**` |
| `.opencode/agents/ai-engineer.md` | `subagent` — `data/service/**`, `ui/document/**` |
| `.opencode/agents/qa-engineer.md` | `subagent` — `app/src/test/**`, `app/src/androidTest/**` |
| `.opencode/agents/code-reviewer.md` | `subagent` — read-only |
| `.opencode/opencode.jsonc` | `default_agent: orchestrator`, global permission guardrails |

## Boundary design

Each lane is `edit: * → deny` then `allow` for that lane only. Every lane pattern appears twice,
relative and `**/`-prefixed, because the matched path may be relative or absolute on Windows
(D-004). Shared files — Gradle scripts, `AndroidManifest.xml`, `res/values/strings.xml`,
`AGENTS.md`, `.opencode/**`, `handoff.md`, `docs/ai-workforce/**` — belong to the orchestrator only.

Shell: every specialist denies `git commit`, `git push`, `git reset --hard`, `git checkout`,
`git stash`, `git clean`, `*assembleRelease*`, `*bundleRelease*`; `code-reviewer` denies shell
entirely and re-allows only `git diff*`, `git status*`, `git log*`, `git show*`. Every specialist
denies `subagent` so nobody spawns an agent of an agent. `ai-engineer` alone may use
`webfetch`/`websearch`.

## Verification

1. **Discovery** — `opencode api get "/api/agent?location[directory]=<root>"` listed all 7 agents,
   `orchestrator` first as `primary`, each with its description and parsed permission rules.
2. **`qa-engineer` probe** — write to `domain/permtest/PermProbe.kt` →
   `{"error":{"type":"permission.rejected","message":"Permission denied: edit"}}`; write to
   `.opencode/permtest_qa.txt` → refused; write to `app/src/test/java/.../PermProbeTest.kt` →
   allowed (correct lane); `git status --short` → allowed; `git commit -m probe` →
   `permission.rejected: shell`.
   The probe file was deleted afterwards; `git status` confirmed it was gone.
3. **`code-reviewer` probe** — its tool catalog contains no `write` or `edit` tool at all
   (`ERROR: Unknown tool 'write'`), so it is read-only at two layers; `git status --short` →
   allowed; `Write-Output hello` → `permission.rejected: shell`.
4. **Build** — `.\gradlew.bat :app:compileDebugKotlin` PASS (14s);
   `.\gradlew.bat assembleDebug` PASS (9s); `.\gradlew.bat testDebugUnitTest` PASS (JVM suite).

## Findings worth keeping

- A bare `GET /api/agent` answers for a default location and makes the custom agents look missing.
  Always pass `?location[directory]=…` (D-009 / failed approach in `handoff.md` §9).
- Permission matching works for relative and absolute Windows paths, so the `deny`-first ordering
  is genuinely enforced, not merely declarative.
- `code-reviewer` having no edit tool at all is stronger than a permission deny.

## Handed off

- `T-002` — handoff protocol and task-state split (this file's follow-up).
