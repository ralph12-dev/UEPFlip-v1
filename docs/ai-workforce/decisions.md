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

### D-013 — The Gemini model id is Remote Config, not a hardcoded constant

- **Decision:** `AiFlashcardGenerator` stops hardcoding one model. It exposes
  `DEFAULT_MODEL = "gemini-flash-latest"`, `resolveModel()`, `isValidModelId()` and `endpointFor()`,
  and `AiGeneratorProvider.create()` reads an optional Remote Config parameter `gemini_model` that
  overrides the default. An absent, blank, or implausible value falls back to `DEFAULT_MODEL`. The
  value is trimmed, a leading `models/` is stripped (the form the `ListModels` API prints), and it is
  validated against `^[A-Za-z0-9][A-Za-z0-9._-]{0,63}$` before it is interpolated into the request
  URL — so `/`, `:`, `@`, `?` and `#` cannot reach the path.
- **Reason:** the hardcoded `gemini-2.0-flash` endpoint answered `404` for every AI call — Google
  shut that model down on 2026-06-01. A retired model id is an *operational* event, not a code
  defect, and it broke Create, Tutor and Hint at once because all three share `AiGeneratorProvider`.
  Keeping the id in Remote Config makes the next retirement a console change, not an APK release.
- **Alternatives rejected:** (a) pinning a different literal model id — works today, fails the same
  way at the next retirement; (b) adopting the Firebase AI Logic SDK (`firebase-ai`) — a large change
  to a pinned toolchain for a symptom that a string fixes, and the user explicitly chose to keep the
  Remote Config key path; (c) a `local.properties`/`BuildConfig` debug key store — declined by the
  user in the same exchange because it moves a secret into the build.
- **Precedent:** this extends the existing rule that runtime configuration arrives through Remote
  Config. The model id is configuration of exactly that kind, so it belongs beside `gemini_api_key`
  rather than in code. The API key itself still never enters the APK.
- **Consequences:** `README.md` documents `gemini_model`, the `404` symptom, and when a new value
  applies: the next time an AI screen is opened (the generator is built once per screen-scoped
  ViewModel, not per call), which can be mid-session. A junk console value degrades to the default
  instead of producing a malformed URL or a request to another host, and a leading `models/` is
  stripped so an id can be pasted straight from the `ListModels` response.
- **Accepted trade-off, stated precisely:** `DEFAULT_MODEL` uses the `-latest` alias, which follows
  Google's retirement schedule by design, so the underlying model can change under a shipped build.
  That degradation is **safe but not self-explaining**: a model that renamed the `flashcards` field
  would parse into an empty list and surface the generic "no cards" error rather than inventing cards,
  and `extractJsonObject` captures text up to the *last* `}` so trailing prose containing a brace can
  over-capture. No data loss and no fabricated content — but the user-facing message will not name the
  cause, which is why `AiGeneratorProvider` logs the model actually in use, and warns when a configured
  value was rejected. Pinning a verified-live concrete id as the default was the reviewer's preference;
  the alias was kept for durability, with the override as the escape hatch.
- **Status:** patch applied to `data/service/**` and `README.md`; **not compiled and not tested** —
  the user invoked `DO NOT BUILD AND TEST`. Tracked as `T-009`, `IN_PROGRESS`, never promote it to
  `DONE` without the compile and JVM runs.

## D-014 — The short AI calls ask for `thinkingLevel: "low"`

- **Context:** after `T-009` restored AI, the study screen's hint took several seconds. The request
  body was `{"contents":[...]}` only: no `generationConfig`, therefore no thinking choice. Gemini 3.x
  models think dynamically by default (`medium` on 3.6/3.8 Flash, `high` on 3 Flash) and
  `generateContent` is non-streaming, so the app waited for thinking *and* the answer before showing
  anything.
- **Decision:** `AiFlashcardGenerator` accepts a `fastThinkingLevel` and sends
  `generationConfig.thinkingConfig.thinkingLevel = "low"` for `generateHint`, `evaluateAnswer` and
  `generateExplanation`. `generateFlashcards` keeps the model default, because card quality benefits
  from reasoning and the user is already waiting on a bulk action.
- **Reason:** the Gemini docs (`ai.google.dev/gemini-api/docs/generate-content/thinking`, updated
  2026-10-14) describe `low` as "minimises latency and cost", which is exactly the trade a
  one-sentence hint wants. That page is the Generate Content API one — the sibling Interactions API
  page prints levels for 2.5 ids too, which does not apply here and must not be used to "fix" the
  guard backwards. Grading and explanation share the shape (short output, user waiting), so they share
  the setting rather than growing a per-call configuration API.
- **Alternatives rejected:** (a) `minimal` — rejected by `gemini-3.7-flash` and `gemini-3.8-flash`,
  and the model id is Remote-Configurable, so a console change could break hints; (b) a small
  `maxOutputTokens` — the docs state it counts **thought tokens**, so it truncates to
  `MAX_TOKENS`/empty output instead of saving time; (c) pinning a Flash-Lite model for hints — that
  changes answer quality for every AI feature and needs a second model/key path; (d) making the level
  a Remote Config knob — more moving parts than the problem, and it belongs to the code that knows
  which calls are latency-sensitive.
- **Compatibility guard:** Gemini 2.5 does not support `thinkingLevel` (it uses `thinkingBudget`), and
  an unknown field is a request error. `AiGeneratorProvider.acceptsThinkingLevel()` emits the level
  only for `gemini-3*`, `gemini-4*` and `*-latest`; every other id gets the previous, config-free
  request byte for byte. Positive matching, so an unrecognised id fails safe.
- **Consequences:** hints, grading and explanations should return in roughly the time the model needs
  to write them, not think + write. Quality on hard comparisons may drop slightly; that is acceptable
  for a nudge and is bounded by `low` rather than `minimal`. The remaining latency sources, in order,
  are the per-screen `RemoteConfig.fetchAndActivate()` and the absence of streaming — both documented
  in `handoff.md` §4.5 rather than fixed here.
- **Status:** applied to `data/service/**` plus three wire tests in `AiFlashcardGeneratorTest`; **not
  compiled and not tested** — the freeze still stands. Tracked as `T-012`, `IN_PROGRESS`.

## D-015 — Hints are generated with the card and stored on it

- **Context:** the hint was generated on tap. Even after D-014 lowered the thinking level, it was a
  network round trip the student waits on for a one-sentence nudge — and the card already contains
  everything needed to write that nudge. The user asked for it to exist before the tap.
- **Decision:** the flashcard generation response includes a `hint` per card; the import and Tutor
  paths persist it on `CardEntity.hint`. The study screen renders a stored hint with no spinner and
  no network, and only calls `generateHint` when `hint` is null (pre-change cards, sample data),
  saving the result afterwards.
- **Reason:** one request instead of two, the hint is ready when the card is first studied, and the
  stored value survives process death and offline use — the app is offline-first.
- **Alternatives rejected:** (a) generating all hints in a second batched call after import — extra
  latency, a second failure mode, and partial results to reconcile; (b) caching hints in memory only —
  lost on process death; (c) a separate `hints` table — a 1:1 value with the card, so it belongs on
  the row; (d) auto-showing the hint on card display — the user asked for "click to show", and
  revealing a nudge unasked changes the study loop.
- **Consequence — schema v3:** `hint` is nullable TEXT, `null` means "not generated yet" (never `""`),
  with a hand-written `MIGRATION_2_3` and no destructive fallback. This is the riskiest part of the
  change: a mismatch crashes on first DB open, so it needs a real v2→v3 upgrade run, not just a
  fresh-install test.
- **Consequence — write path:** `saveHint` uses a targeted UPDATE (`hint`, `updatedAt`) because a
  read-modify-write of the whole entity could erase a review committed concurrently by
  `processCardReview`. The save is best-effort: the hint is shown first, and a failed write is logged
  because it only costs a regeneration.
- **Safety:** a pre-generated hint has no second review pass, so `parseFlashcards` removes the hint
  when it contains the answer verbatim (case-insensitive, answers ≥ 3 chars) and caps it at 240 chars
  on a word boundary. `generateHint` applies the same rule to the lazy fallback, so that path cannot
  store a leak either; an unusable fallback hint is reported as an error rather than shown. A bad hint
  degrades to `null` and the card is never discarded. Paraphrased leaks are the prompt's responsibility
  and are not detected — an accepted limit, stated so nobody assumes the guard is stronger than it is.
  A non-string `hint` in the response (object/array/number) is treated as `null` rather than failing
  the whole import, because one bad hint must not cost every card.
- **Status:** applied to `data/service/**`, `data/local/**`, `data/repository/**`,
  `ui/document/**`, `ui/study/**`, `ui/main/TutorViewModel.kt` plus tests; **not compiled and not
  tested** — the freeze still stands. Tracked as `T-014`, `IN_PROGRESS`.


## D-016 — Firebase Auth + Google Sign-In as the authentication stack

- **Decision:** The app uses Firebase Authentication with Google Sign-In (via Google Play Services Auth) as the only auth provider. No email/password, no anonymous auth.
- **Reason:** Matches the designer mockup (`screen_login.xml`), keeps implementation simple, and Google Sign-In is the only provider the Firebase project has enabled. Guest mode (local-only persistence) covers the "no account" case.
- **Alternatives rejected:** (a) Firebase Anonymous Auth — does not survive app reinstall or device migration, unlike the guest-mode SharedPreferences approach; (b) email/password — adds password recovery flow and UI the mockup doesn't have.
- **Consequences:** `AuthRepository` is the single source of auth state (`userFlow: MutableStateFlow<FirebaseUser?>`). `MainActivity` owns the routing branch: signed-in || guest → onboarding? → Home : Onboarding; else → LoginFragment.

## D-017 — Room schema version 4 with UserEntity and user-scoped entities

- **Decision:** `AppDatabase` version bumped from 3 to 4. New `UserEntity` table. `CardEntity`, `DeckEntity`, `DocumentEntity`, `ReviewEntity` each gain nullable `userId`, `remoteId`, `syncState`, `lastSyncedAt` columns. `MIGRATION_3_4` uses `hasColumn` guards and `ALTER TABLE ... ADD COLUMN` for each new column (no table rebuilds).
- **Reason:** Multi-device sync requires stable local IDs (`remoteId`) and a way to mark pending changes (`syncState`). `lastSyncedAt` enables incremental pulls. The FK from each entity to `UserEntity` is implicit via `userId` (not enforced by Room) to avoid cascade-delete complexity on a schema that already has migrations 1→2→3→4.
- **Alternatives rejected:** (a) separate database per user — breaks the single-process Room instance model; (b) hard-coded `userId` in entities before auth is set — requires nullable columns anyway; (c) enforcing FKs at DB level — would require a v5 migration with table rebuilds for entities that already have 3 migrations.
- **Consequences:** All existing local data survives the upgrade (columns default to `NULL`/`SYNCED`). `SyncEngine` interprets `syncState=PENDING` on local writes. The upgrade path from v2 (sample data) → v3 (hints) → v4 (sync) has **never been tested on device** — it is the single riskiest unverified change in the tree.

## D-018 — WorkManager background sync with local-first mutations

- **Decision:** `SyncEngine` schedules a `SyncWorker` (periodic, network-constrained) via WorkManager. Local mutations mark `syncState=PENDING`; the worker pushes pending rows to Firebase Realtime Database, pulls remote changes, and resolves conflicts by server timestamp (last-write-wins). `mergeGuestData()` moves guest-mode decks/cards to the signed-in user on first Google Sign-In.
- **Reason:** WorkManager survives process death and respects Doze/standby. Local-first means the study loop never blocks on network. Firebase Realtime Database is already in the project (google-services.json has it).
- **Alternatives rejected:** (a) `FirebaseFirestore` — requires a different data model and a Firestore dependency; (b) manual `AlarmManager` + `JobScheduler` — more boilerplate, less reliable across OEMs; (c) sync-on-app-start — user perceives latency.
- **Consequences:** `SyncEngine.initialize()` **is not called anywhere** — periodic work is never scheduled. `mergeGuestData()` has no caller. `T-026` is `NOT_STARTED` and blocks end-to-end sync verification.

## D-019 — Guest mode via SharedPreferences, no Anonymous Auth

- **Decision:** Guest mode is a boolean `guest_mode` in the same SharedPreferences file as onboarding (`uepflip_prefs`). No Firebase Anonymous Auth token is created. On Google Sign-In, `mergeGuestData()` re-assigns local `userId=null` rows to the new `FirebaseUser.uid`.
- **Reason:** Simpler than Anonymous Auth (no token refresh, no linking flow). The SharedPreferences flag survives app restart and reinstall (until data clear). Onboarding and settings screens already read/write this prefs file.
- **Alternatives rejected:** Firebase Anonymous Auth with `linkWithCredential` on Google Sign-In — works but adds a whole auth state machine the current UI doesn't reflect.
- **Consequences:** Guest data is truly local-only until the user signs in. If the user clears app data or uninstalls, guest decks are gone. `SettingsFragment` "Sign Out" clears `guest_mode` and returns to `LoginFragment`.

