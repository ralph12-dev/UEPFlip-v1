# AI Workforce — Decision Log

Append-only. Never reverse an established decision without recording why here. Newest last.

---

### D-001 — Ownership lanes are directory-based and strictly disjoint

- **Decision:** `android-engineer` edits `domain/**`, `ui/main/**`, `ui/study/**` and
  `ui/SimpleViewModelFactory.kt`. `ui-engineer` edits `app/src/main/res/**` only. `data-engineer`
  edits `data/{local,repository,model,sample}/**`. `ai-engineer` edits `data/service/**` and
  `ui/document/**`. `qa-engineer` edits `app/src/test/**` and `app/src/androidTest/**`.
- **Reason:** the core requirement is that agents cannot overwrite each other's work. Directory
  lanes are exact and enforceable with glob rules.
- **Alternatives rejected:** splitting `ui/main` by filename pattern (`*Fragment.kt` to one agent,
  `*ViewModel.kt` to another) — correct on paper but adds fragile filename globs to an already
  uncertain path-matching layer, and new files would silently change ownership.
- **Consequences:** a change that needs both a layout and its Kotlin is a deliberate two-lane
  handoff. The orchestrator sequences it.

### D-002 — OpenCode V2 `permissions` rule lists, never legacy fields

- **Decision:** every agent uses `permissions: [{action, resource, effect}]`. No `tools:`,
  `permission:`, `disable`, `maxSteps`, or `temperature`.
- **Reason:** OpenCode v2.0.26 with V2 docs; legacy fields are not supported in new V2 config.
- **Alternatives rejected:** V1-style `tools:` lists.
- **Consequences:** rules are ordered and evaluated last-match-wins.

### D-003 — Broad `deny` first, narrow `allow` after

- **Decision:** each lane is expressed as `edit: * → deny` followed by `allow` rules for the lane.
- **Reason:** V2 evaluates in order and the **last** matching rule wins, so exceptions must come
  after the blanket rule. Verified empirically: out-of-lane writes return
  `permission.rejected`.
- **Alternatives rejected:** an allowlist with no blanket deny — it fails open, losing ownership
  enforcement silently if a pattern never matches.
- **Consequences:** a pattern typo fails closed (the agent can write nothing), which is visible
  and recoverable.

### D-004 — Each agent carries both the relative and the `**/`-prefixed lane pattern

- **Decision:** lane rules are duplicated, e.g. `app/src/main/res/**` **and**
  `**/app/src/main/res/**`.
- **Reason:** the matched resource can arrive relative or absolute, and this repo is on Windows.
  Two probes (relative and absolute write attempts) both returned `permission.rejected`, confirming
  at least one form matches on each path.
- **Alternatives rejected:** guessing one canonical form.
- **Consequences:** slightly more verbose rule lists; ownership enforcement confirmed on both forms.

### D-005 — Agents inherit the parent session model

- **Decision:** no agent file sets `model:`.
- **Reason:** the configured provider is a local OpenAI-compatible endpoint
  (`omniroute/static-coding-2`). Pinning model IDs couples the team to IDs that may change or not
  exist.
- **Alternatives rejected:** pinning `static-best-reasoning` to `code-reviewer` and `qa-engineer`.
- **Consequences:** the team follows whatever model the session uses; changing the model changes
  every agent at once.

### D-006 — The `orchestrator` is the sole handoff maintainer

- **Decision:** `orchestrator` holds `edit` allows on `handoff.md` and `docs/ai-workforce/**`;
  all six specialists hold explicit denies on the same paths. `/handoff` pins `agent: orchestrator`.
- **Reason:** the protocol requires one maintainer and serialized edits. Making it a permission
  rule means it cannot be forgotten or overridden by a prompt.
- **Alternatives rejected:** letting whichever specialist finishes write the record — that is
  exactly the concurrent-write failure the protocol exists to prevent.
- **Consequences:** specialists report a handoff block upward and the orchestrator writes it.

### D-007 — Task detail lives outside `handoff.md`

- **Decision:** `handoff.md` holds current state and pointers; `docs/ai-workforce/` holds the
  board, the decision log, the test log, and per-task logs.
- **Reason:** an ever-growing activity log burns the active agent's context and buries the state.
- **Alternatives rejected:** one large `handoff.md`.
- **Consequences:** two files must be read at session start — handled by auto-loading both through
  `instructions` in `.opencode/opencode.jsonc`.

### D-008 — The handoff record is auto-loaded, not requested

- **Decision:** `instructions: ["handoff.md", "docs/ai-workforce/task-board.md"]`.
- **Reason:** "maintain it automatically" fails if maintenance depends on the model remembering to
  read the file. Auto-loading makes the record part of every session's context.
- **Alternatives rejected:** relying only on the prose in `AGENTS.md`.
- **Consequences:** every session pays the context cost. Both files are kept concise by protocol;
  if they grow, drop `task-board.md` from `instructions` first.

### D-009 — The old handoff file is superseded, not restored

- **Decision:** do not resurrect the pre-`d5776c8` handoff.
- **Reason:** it described an Activity-based phase (`DeckListActivity`, `StudyActivity`) that no
  longer exists; the app is now a fragment shell. `AGENTS.md`'s claim that it sits at
  `git show HEAD:handoff.md` is false — it is not at HEAD.
- **Alternatives rejected:** restoring and updating the old file.
- **Consequences:** the historical version is reachable only at `d5776c8^:handoff.md` and is marked
  obsolete wherever referenced.

### D-010 — Integrate `T-003a` with its limits recorded precisely, rather than polishing first

- **Decision:** `T-003a` is integrated as `DONE`, and the review's `MEDIUM` finding is recorded
  verbatim as a **coverage limit** rather than fixed now. The rollback test hand-rolls its own
  `withTransaction`, so it does not exercise `FlashcardRepository.processCardReview`'s transaction.
- **Reason:** the honest options were (a) claim the transaction boundary is covered — false, or
  (b) build fault injection in production so the repository's own transaction can be failed
  deterministically. (b) is a production change for testability, which crosses lanes and needs its
  own justification; deferring it costs nothing because the gap is now written down where the next
  session will read it.
- **Alternatives rejected:** silently renaming the test and moving on (loses the finding);
  weakening the test (the reviewer confirmed it still guards FK enforcement and Room's rollback,
  which is real value); blocking integration on a problem that no existing code path can create.
- **Consequences:** nobody may read `logs/task-003.md` and believe the repository's review
  transaction is tested. If a future refactor removes `withTransaction` from `processCardReview`,
  these 10 tests stay green — that is now a known, documented hole, and `T-008` is the place to
  close it.

### D-011 — The `DocumentEntity` missing-FK fix is scheduled, not done casually

- **Decision:** `T-007` owns the orphaned-documents issue; nothing is changed now.
- **Reason:** a correct fix means adding the FK with `CASCADE` — a Room schema change, so version
  2→3 **plus** a hand-written table-rebuild migration that preserves existing rows, under AGENTS.md's
  explicit ban on destructive fallback. Doing that opportunistically inside a test task is exactly
  how migrations get written badly.
- **Alternatives rejected:** fixing it immediately as part of `T-003a` (different lane, different
  risk class); ignoring it (imported PDFs' full `extractedText` leaks permanently and invisibly).
- **Consequences:** the cheap interim steps — correcting the KDoc that falsely claims a foreign
  key, and deleting documents by `deckId` in the deck-delete path — are part of `T-007` and may be
  done before the schema change.

### D-012 — Commit the workforce as two commits, and record the authorization

- **Decision:** `T-005` produced `11b7058` (workforce config, docs, handoff record) and `e106f30`
  (the `T-003a` tests) as **separate** commits, and the user's authorization to commit is recorded
  in `logs/task-005.md` rather than left implicit.
- **Reason:** they are independent changes with different blast radii — reverting the tests should
  not remove the engineering team, and vice versa. The authorization is recorded because AGENTS.md
  forbids committing without it; a future session auditing `git log` should be able to see that the
  rule was followed, not guess.
- **Alternatives rejected:** one combined commit (convenient, but couples unrelated reverts);
  amending the record refresh into the test commit (rewrites history for no benefit, and hides the
  fact that the record lags the work by one commit by design).
- **Consequences:** the handoff record is committed, then immediately becomes stale about its own
  git state, so a further commit is always needed to describe the previous one. That is inherent to
  keeping the record inside the repo it describes; `logs/task-005.md` says so explicitly.
