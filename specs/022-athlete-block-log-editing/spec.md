# Feature Specification: Unified Athlete Block Log Editing & PR Reconciliation

**Feature Branch**: `feat/022-athlete-block-log-editing`  
**Created**: 2026-11-15  
**Status**: Clarified & Approved  
**Input**: User description: `SPEC-022`: Unified Athlete Block Log Editing (`SPEC-BLOCK-LOG-EDIT-01`) and Remote MCP Connector Parity (`SPEC-MCP-BLOCKS-02`) from `https://share.gemini.google/RYFcilk6VJK7`

## Clarifications

### Session 2026-11-15

- **Q1 (Entry Point & UI Parity)**: Should editing a logged block session from History route into an inline History modal or reuse `SessionEditorScreen` with the simplified `Log Session` card layout?  
  - **Locked Decision (Option 1A)**: Triggered from `History` -> expand workout -> tap `Edit` (`SessionEditorScreen`, `copy = false`). Logged blocks render with exact UI parity to `Log Session`'s simplified block card (`BLOCK: {Title}`, `⏱` timer & `🗑` delete buttons, read-only prescription box, 4-column/2-column weight-only `Set 1..N` tiles with `✕` + `+ Add Set`, tracked PR chips with `✕`, and `+ Record PR 🔥` opening `Record / Track PRs` modal bottom sheet).
- **Q2 (History Edit Container & Session Actions)**: How should pre-fill hints, adding blocks, and accidental back navigation behave when editing an existing logged session?  
  - **Locked Decision (Option 2.1A)**: Hide `"Pre-filled from last session ({Date})"` when editing an existing session (`isEditMode == true`), keep `Load Block ▾` / `+ Add Block` available to append blocks to that session, and guard unsaved edits with `BackHandler(enabled = isDirty)` showing a Discard Changes confirmation dialog.
- **Q3 (Live Set-Weight to PR-Weight Auto-Sync)**: Should modifying set weights automatically update PR chip weights, or preserve manual PR edits?  
  - **Locked Decision (Option 2.3A)**: Track `isCustomWeightOverride` per tracked PR slot. While `false` (default when initial weight equals `maxSetWeightFor(movementId)` or unset), editing/adding/removing set tiles live-updates the PR chip and picker input to the new `maxSetWeightFor(movementId)`. Editing the weight manually inside `Record / Track PRs` (or hydrating a persisted `RepMax` whose stored weight differs from `maxSetWeightFor(movementId)`) sets `isCustomWeightOverride = true` so subsequent set edits do not clobber the explicit PR weight.
- **Q4 (Full DB PR Reconciliation, Parent Template Inheritance & Sync)**: How should Save reconcile PRs, update the parent block template, and sync across Firestore and MCP?  
  - **Locked Decision (Option 2.2A)**: Saving an edited session (`reconcileRepMaxes = true`) atomically inserts newly added PRs, updates modified PR weights/dates/cycleIds/blockIds in place (preserving `RepMax.id`), deletes `RepMax` rows removed via `✕` or unchecked in `Record / Track PRs`, refreshes Active Cycle Goals & Progress (`By exercise` and `By block`), and merges newly tracked `(movementId, movementName, rmReps)` slots onto the parent `AvailableBlock` (`[ab_prs=...]`). MCP adds `update_block_session_log` (`34` tools total) and `RepMaxRecord.deletedAtMillis` tombstones so remote deletions and weight updates converge cleanly via `UserCloudSyncManager`.

---

## User Scenarios & Testing *(mandatory)*

### User Story 1 - Edit Logged Sets, Weights & PRs Using Simplified Block Card Parity (Priority: P1)

As an athlete reviewing a past workout in **History**, when I expand a workout card and tap **Edit**, I want each logged strength/Available Block to open in the same clean, simplified block card I use during **Log Session** so that fixing a typo in a set weight, adding/removing sets, or updating a PR takes seconds without navigating legacy complex forms.

**Why this priority**: Eliminates UI fragmentation between logging and editing (`SPEC-BLOCK-LOG-EDIT-01`), solving the core usability gap where `SessionEditorScreen` forced athletes back into legacy `CompactBlockEditor` cards.

**Independent Test**: Seed a logged session containing an Available Block with 3 weighted sets and 1 stored `RepMax`, open `SessionEditorScreen` with `editingSessionId`, verify `SimplifiedBlockLogCard` renders without `"Pre-filled from last session"`, edit a set weight or add/remove sets, verify live PR auto-sync when no manual override is set, and save.

**Acceptance Scenarios**:

1. **Given** an athlete taps **Edit** on a logged workout in History containing strength/Available Block entries, **When** `SessionEditorScreen` opens (`isEditMode = true`), **Then** those blocks hydrate into `SimplifiedBlockLogCard` instances displaying `BLOCK: {Title}`, timer (`⏱`) and delete (`🗑`) icon buttons with `>= 48x48.dp` touch targets, the read-only prescription box (when notes/scheme exist), 4-column (`2`-column at high font scale) weight-only `Set 1..N` tiles with `✕`, `[ + Add Set ]`, PR status chips with `✕`, and `[ + Record PR 🔥 ]`.
2. **Given** `isEditMode == true`, **When** the athlete views the card header or edits sets, **Then** the `"Pre-filled from last session ({Date})"` subtitle is hidden, `Load Block ▾` / `[ + Add Block ]` remains available to append additional blocks, and pressing system Back after modifying any field triggers a Discard Changes confirmation dialog via `BackHandler(enabled = isDirty)`.
3. **Given** a logged session mixes a strength/Available Block and a legacy score/time-based metcon block (`resultText` or non-weight `metricValue > 0`), **When** hydrated into the editor, **Then** the strength/Available Block renders as `SimplifiedBlockLogCard` while the unlinked score-based metcon block remains in `residualLegacyBlocks` (`CompactBlockEditor`) without losing `resultText`, `resultValue`, `metricType`, or `metricValue`.

---

### User Story 2 - Live Set-to-PR Weight Auto-Sync & Non-Destructive `Record / Track PRs` Modal (Priority: P1)

As an athlete correcting set weights or tracking a new PR inside a logged or active block, I want un-overridden PR chip weights to automatically follow my heaviest logged set for that movement, while preserving any custom PR weight I manually enter in the `Record / Track PRs` bottom sheet, and letting me undo accidental chip removals before saving.

**Why this priority**: Ensures fast single-pass weight corrections while protecting athletes from accidentally overwriting intentional PR overrides (e.g., logging working sets at 30kg alongside a separate 35kg single) or losing a tracked PR on an accidental tap.

**Independent Test**: Verify via unit tests on `LoadedAvailableBlockState` and UI state transitions that (a) changing `Set 2` from `32.5` to `35` auto-updates a synchronized `3RM` PR chip from `32.5` to `35`, (b) entering `37.5` inside `Record / Track PRs` sets `isCustomWeightOverride = true` so subsequent set edits do not overwrite `37.5`, (c) hydrating a historical PR (`30 kg`) when sets max out at `27.5 kg` preserves `isCustomWeightOverride = true`, and (d) tapping `✕` on a PR chip shows an `[Undo]` Snackbar restoring the chip if tapped before timeout.

**Acceptance Scenarios**:

1. **Given** a tracked PR slot `(movementId, rmReps)` whose `isCustomWeightOverride == false`, **When** the athlete edits, adds, or removes weighted set tiles for that movement, **Then** the slot's candidate weight immediately updates to the new `maxSetWeightFor(movementId)` on both the PR chip and `Record / Track PRs` sheet input, re-evaluating against prior sessions (`excludeSessionId = editingSessionId`) to show `🏆 New PR!` (`#0B6E3F` / `#2ECC71`) or `Current Best: X kg (PR)`.
2. **Given** an athlete edits the weight input inside `Record / Track PRs` to differ from `maxSetWeightFor(movementId)` and taps **Apply**, **Then** `isCustomWeightOverride` becomes `true`, protecting that PR weight from subsequent set-tile edits until the user aligns or resets it.
3. **Given** an athlete taps `✕` on a PR chip in edit mode (or unchecks it in `Record / Track PRs`), **When** removed, **Then** the UI immediately removes the chip, displays an `[Undo]` Snackbar in edit mode before Save, and removes `(movementId, rmReps)` from active tracked submission keys.
4. **Given** the athlete opens `Record / Track PRs` on a small viewport with a software keyboard open, **Then** `ModalBottomSheet` respects `imePadding()`, weight inputs stay visible without occlusion, numeric columns use `fontFeatureSettings = "tnum"`, and interactive targets meet `48.dp` minimum sizing with TalkBack semantics merging label + weight + PR status.

---

### User Story 3 - Atomic Database PR Reconciliation, Parent Template Inheritance & Analytics Refresh (Priority: P1)

As an athlete saving an edited workout, I want added, modified, and removed PRs to be reconciled cleanly in the database without creating duplicate `RepMax` records, have newly tracked PR slots inherited onto the parent `AvailableBlock` template, and immediately see accurate numbers in Active Cycle Goals & Progress.

**Why this priority**: Currently `Repository.updateSession` inserts new `RepMax` rows on weight changes while leaving stale rows intact and never deletes removed PRs, corrupting Progress analytics and Cycle Goal tracking.

**Independent Test**: Execute `Repository.updateSession(..., reconcileRepMaxes = true)` and `AppViewModel.updateSession` tests verifying: (1) adding a PR creates 1 `RepMax` row; (2) changing an existing PR weight updates the same `RepMax.id` in place rather than inserting a second row; (3) removing a PR deletes the exact `RepMax` row and leaves no orphan; (4) `reconcileRepMaxes = false` preserves legacy `UserCloudSyncManager` Step 4 relink semantics; (5) newly tracked `(movementId, movementName, rmReps)` slots are appended cleanly to the parent `AvailableBlock` `[ab_prs=...]` metadata on Save.

**Acceptance Scenarios**:

1. **Given** a session has a persisted `RepMax` row `R1(id=10, Snatch, 3RM, 35kg)` tied to `(sessionId, blockId)`, **When** the athlete edits `R1` to `37.5kg` and saves with `reconcileRepMaxes = true`, **Then** `R1` is updated **in place** (`id == 10`, `weight == 37.5`, matching `session.date`, `session.cycleId`, updated `blockId`), and total `RepMax` count for that session remains `1`.
2. **Given** the athlete removes `R1` via chip `✕` or unchecking in `Record / Track PRs` and saves, **When** `Repository.updateSession(..., reconcileRepMaxes = true)` completes, **Then** `R1` is deleted from `rep_maxes`, the best stored PR for Snatch falls back to the prior session's record (e.g., `32.5kg`), and `ActiveCycleGoals` & `Progress` (`By exercise` / `By block`) reflect `32.5kg`.
3. **Given** the athlete tracks a new `(movementId, movementName, rmReps)` slot not previously defined on the parent `AvailableBlock`, **When** the athlete taps **Save**, **Then** `AppViewModel` atomically merges the new slot into the parent `AvailableBlock`'s `[ab_prs=...]` encoding (preserving existing slots and `[rb_v2=...]` routine markers) and triggers cloud upload (`uploadUserData`).
4. **Given** two PR comparison calls run while editing `sessionId = 5` where `session 5` holds the current best `35kg` and `session 2` held `32.5kg`, **When** `evaluateTrackedPrChip` runs in edit mode, **Then** `bestStoredRepMaxWeight(..., excludeSessionId = 5)` returns `32.5kg` so `35kg` accurately displays `🏆 New PR!` instead of falsely comparing against itself (`35 > 35 == false`).

---

### User Story 4 - Remote MCP Tool `update_block_session_log` & Tombstoned `RepMax` Cloud Sync (`SPEC-MCP-BLOCKS-02`) (Priority: P2)

As an athlete using a conversational AI assistant connected via MCP, I want to update an existing block workout's date, title, notes, block titles, sets, weights, and tracked PRs—and have any modified or removed PRs sync reliably down to my Android app without resurrecting deleted weights.

**Why this priority**: Completes cross-platform parity between Android local editing and the Firestore/MCP backend (`SPEC-MCP-BLOCKS-02`), preventing ghost/resurrected PRs when natural-keyed weights change remotely.

**Independent Test**: Run `mcp-server` Mocha tests verifying `34` registered tools, flat schema check (`0` `$ref` / `0` `exclusiveMinimum`), `update_block_session_log` atomic updates of sets/weights/notes, `RepMaxRecord.deletedAtMillis` tombstoning of replaced/removed PRs, parent `AvailableBlock` `[ab_prs=...]` slot inheritance, `computeCyclesFromRaw`/`listRepMaxes` ignoring tombstoned PRs, and Android `UserCloudSyncManager.downloadUserData` Step 5 deleting local `RepMax` rows matching tombstoned remote entries.

**Acceptance Scenarios**:

1. **Given** a logged block session exists in Firestore, **When** an MCP client invokes `update_block_session_log` with `sessionId` (or `date` + `blockId`) and updated `exercises`/`sets`/`trackedPrs`, **Then** only provided fields are mutated, sets/exercises are replaced cleanly, `[ab_prs=...]` on the parent `AvailableBlock` inherits any newly tracked PR slots, and the tool responds with `updatedSession`, reconciled `repMaxes`, `tombstonedRepMaxIds`, and `inheritedTrackedPrs`.
2. **Given** the logged session previously stored `RepMaxRecord(id=501, Snatch, 3RM, 35kg)`, **When** `update_block_session_log` updates that PR to `37.5kg` or omits/removes it, **Then** `RepMaxRecord(501)` is marked with `deletedAtMillis = now` & `updatedAtMillis = now`, any new weight (`37.5kg`) either revives a matching tombstone (`deletedAtMillis = null`) or inserts a fresh active record, and `listRepMaxes` / `computeCyclesFromRaw` exclude tombstoned records.
3. **Given** Android `UserCloudSyncManager.downloadUserData` runs Step 5 against Firestore `rep_maxes`, **When** two remote records share `(exerciseId, reps, normalizeWeight(weight), date)` with different timestamps or one has `deletedAtMillis != null`, **Then** Step 5 groups by natural key retaining the record with maximum `effectiveRemoteTs`, skips creation if `isRemoteDeleted(map)` is true, and deletes any matching local `RepMax` via `repo.deleteRepMax(existingRm)` without deleting unrelated sessions' `RepMax` entries.

---

### Edge Cases

- **Unlinked / Ad-hoc Strength Block vs Linked Available Block**: A strength block logged without `[rb_v2]` or matching `AvailableBlock` (`resolvedAvailableBlockId == null`) still hydrates into `SimplifiedBlockLogCard` (showing `BLOCK: {title}`), supports weight-only set editing and `Record / Track PRs`, and skips parent template PR inheritance on Save (no phantom block template created).
- **Non-Weight Legacy Blocks Inside Mixed Sessions**: A block with `resultText.isNotBlank()`, `resultValue != null`, or `metricValue > 0 && (weight ?: 0.0) <= 0.0` (e.g., `For Time 12:34` or `185 reps`) that does NOT match an `AvailableBlock` stays in `residualLegacyBlocks` (`CompactBlockEditor`) so no score/time/metric fields are lost on edit/save.
- **Unresolved Custom Exercise Name (`movementId <= 0L`)**: Free-text movements resolve against `exercises` by case-insensitive exact match where possible; if `movementId <= 0L` when saving sets, sets persist with `exerciseName`, while `RepMax` insertion safely requires `exerciseId > 0L`.
- **Multiple Movements Per Block Sharing `rmReps`**: PR tracking, `persistedSessionPrKeys`, `explicitlyAppliedPrKeys`, and `customWeightOverrides` are strictly keyed by `(movementId, rmReps)` (not just `rmReps`) so Movement A `3RM` and Movement B `3RM` inside the same multi-movement block never collide or overwrite each other.
- **Copy Mode (`copy = true`) vs Edit Mode (`copy = false`)**: Opening a past session with `copy = true` (`Duplicate / Copy to today`) MUST NOT pass `editingSessionId` to PR exclusion or `reconcileRepMaxes`, and creates a brand new session on Save without mutating the source session's `RepMax` rows.
- **Database Schema & Security Rules Stability**: Room remains at version `10` (no schema bump, no Room `Migration` required) and `firestore.rules` remains untouched.
- **Remote Tombstone Resurrection Prevention**: Repeating `batchRecordRepMaxes` or `logBlockSession` or `updateBlockSessionLog` for a previously tombstoned `(exerciseId, reps, weight, date)` revives the existing tombstone (`deletedAtMillis: null, updatedAtMillis: now`) rather than appending duplicate Firestore documents with the same natural key.

---

## Requirements *(mandatory)*

### Functional Requirements

- **FR-001 (Simplified Card Hydration & Parity)**: `SessionEditorScreen` MUST hydrate existing strength and `AvailableBlock`-linked `SessionBlock` records into `SimplifiedBlockLogCard` state (`LoadedAvailableBlockState`) when `copy == false`, rendering identical layout structure and interactions to `Log Session` (`BLOCK: {Title}`, `⏱`, `🗑`, read-only prescription box, 4/2-column weight-only `Set 1..N` tiles with `✕`, `[ + Add Set ]`, PR chips with `✕`, `[ + Record PR 🔥 ]`).
- **FR-002 (Legacy Block Safeguard)**: `AvailableBlockCodec.hydrateSessionBlocksForEditor` MUST keep non-weight score/time/metric legacy blocks (`resultText.isNotBlank()`, `resultValue != null`, or `metricValue > 0 && (weight ?: 0.0) <= 0.0` when not linked to an `AvailableBlock`) inside `residualLegacyBlocks` so legacy metcon blocks retain full score fidelity in `CompactBlockEditor`.
- **FR-003 (Edit Mode Container Chrome & Navigation Guard)**: When `isEditMode == true`, the editor MUST hide `"Pre-filled from last session ({Date})"`, MUST keep `Load Block ▾` / `[ + Add Block ]` enabled to append blocks, and MUST guard unsaved modifications with `BackHandler(enabled = isDirty)` presenting a Discard Changes confirmation dialog.
- **FR-004 (Lossless Block Field Preservation)**: `LoadedAvailableBlockState` MUST preserve original block `format`, `scheme`, `section`, `kind`, `isCompleted`, and per-set `reps` for existing sets; newly appended sets inherit the movement's default/last reps (`>= 1`).
- **FR-005 (Live Set-to-PR Weight Auto-Sync with Override Protection)**: Each tracked PR slot `(movementId, rmReps)` MUST maintain `isCustomWeightOverride`. While `false`, editing, adding, or deleting weighted sets for `movementId` MUST live-update the candidate weight on the PR chip and `Record / Track PRs` sheet input to `maxSetWeightFor(movementId)`. Manual weight edits inside `Record / Track PRs` (or loaded persisted `RepMax` rows whose weight differs from `maxSetWeightFor(movementId)`) MUST set `isCustomWeightOverride = true`, preventing set edits from overwriting the custom PR weight.
- **FR-006 (Session-Excluded PR Comparison)**: `AvailableBlockCodec.bestStoredRepMaxWeight` and `resolveLastBlockSession` MUST accept `excludeSessionId: Long? = null` so in edit mode a stored PR belonging to `editingSessionId` is excluded from the baseline comparison.
- **FR-007 (Explicit PR Submission & Accidental Removal Undo)**: `toBlockDrafts()` MUST emit `RepMaxDraft` only when `movementId > 0L && candidateWeight > 0.0 && (chip.isNewPr || key in persistedSessionPrKeys || key in explicitlyAppliedPrKeys)`. Removing a chip via `✕` or unchecking in `Record / Track PRs` MUST clear both key sets for `key` and, in edit mode, trigger an `[Undo]` Snackbar restoring the chip if clicked.
- **FR-008 (Atomic PR Reconciliation)**: `Repository.updateSession(session, blocks, reconcileRepMaxes = false)` MUST keep `reconcileRepMaxes == false` backward-compatible for `UserCloudSyncManager`, and when `reconcileRepMaxes == true` MUST atomically insert added PRs, update modified persisted `RepMax` rows **in place** (preserving `RepMax.id` while updating `weight`, `date`, `cycleId`, `blockId`), and delete removed `RepMax` rows within the single database transaction (`withDatabaseTransaction`).
- **FR-009 (Parent Template PR Inheritance)**: Saving an edited (or newly logged) block session MUST merge newly tracked `(movementId, movementName, rmReps)` slots onto the parent `AvailableBlock` (`[ab_prs=...]`) via `AvailableBlock.upsertTrackedPrSlots(...)` without losing existing slots or routine metadata (`[rb_v2=...]`).
- **FR-010 (Immediate Cloud Sync Upload)**: `AppViewModel.saveSession`, `updateSession`, and `deleteSession` MUST trigger `UserCloudSyncManager.uploadUserData(data.realRepository)` after local Room persistence completes so edits, PR reconciliations, and deletions propagate immediately to Firestore when signed in.
- **FR-011 (Visual & Accessibility Polish)**: Interactive card header buttons, set delete icons, and PR chip remove buttons MUST expose `>= 48.dp` touch targets (`minimumInteractiveComponentSize()`), `Record / Track PRs` MUST apply `Modifier.imePadding()`, PR badges MUST use accessible contrast (`#0B6E3F` badge / `#2ECC71` accent), weight typography MUST apply `fontFeatureSettings = "tnum"`, and PR chips / set tiles MUST provide merged TalkBack accessibility descriptions.
- **FR-012 (MCP `update_block_session_log` Tool)**: `mcp-server` MUST expose `update_block_session_log` (`34` tools total) using a flat Zod schema (`0` `$ref`, `0` `exclusiveMinimum`) and `IDEMPOTENT_WRITE_ANNOTATIONS`, supporting partial updates of session date/title/notes, block titles/notes/exercises/sets, and tracked PR reconciliation plus parent `AvailableBlock` `[ab_prs=...]` slot inheritance.
- **FR-013 (MCP `RepMaxRecord` Tombstones & Read Filtering)**: `RepMaxRecord` MUST support `deletedAtMillis?: number | null`. When `update_block_session_log` replaces or removes a prior PR linked to `(sessionId, blockId)` or `(sessionId)`, it MUST tombstone the obsolete `RepMaxRecord` (`deletedAtMillis: now, updatedAtMillis: now`) and revive or create new active records (`deletedAtMillis: null`). `computeCyclesFromRaw` and `listRepMaxes` MUST ignore records with `deletedAtMillis != null`.
- **FR-014 (Android Step 5 Natural-Key Tombstone Sync)**: `UserCloudSyncManager.downloadUserDataInternal` Step 5 (`rep_maxes`) MUST deduplicate incoming entries by `(exerciseId, reps, normalizeWeight(weight), date)` keeping maximum `effectiveRemoteTs`, MUST NOT recreate entries where `isRemoteDeleted(map)` is true, and MUST call `repo.deleteRepMax(existingRm)` when a matching local `RepMax` exists for a tombstoned remote entry.
- **FR-015 (Changelog & Non-Regression)**: Room stays at `v10`, `firestore.rules` remains unmodified, all pre-existing `SessionEditor`, `AvailableBlock`, cloud-sync, and MCP tests pass, and `CHANGELOG.md` documents the feature under `## [Unreleased]`.

### Key Entities

- **`HydratedAvailableBlockSeed` / `SessionBlockHydrationResult`**: Pure codec output partitioning a persisted `SessionWithBlocks` into simplified block editor seeds (with associated persisted `RepMax` entries per `(exerciseId, reps)`) and unlinked legacy metcon/score blocks.
- **`LoadedAvailableBlockState`**: Stateful Compose holder for a simplified logged block managing weight-only `Set 1..N` tiles, per-set `reps` preservation, `persistedSessionPrKeys`, `explicitlyAppliedPrKeys`, `customWeightOverrides`, `dismissedPrKeys`, and dirty tracking.
- **`SessionDraft` Extensions**: `reconcileRepMaxes: Boolean` and `inheritedBlockTrackedPrs: Map<Long, List<TrackedPrSlot>>` passed across `SessionEditorBody` -> `AppViewModel.updateSession` -> `Repository.updateSession`.
- **`RepMaxRecord` Tombstone (`deletedAtMillis`)**: Non-destructive soft-deletion marker on remote Firestore `rep_maxes` documents keyed by `(exerciseId, reps, normalizeWeight(weight), date)` to prevent deleted or weight-updated PRs from resurrecting during bidirectional sync.

---

## Success Criteria *(mandatory)*

### Measurable Outcomes

- **SC-001 (100% UI Parity & Zero Legacy Loss)**: Editing any logged strength/Available Block opens in `SimplifiedBlockLogCard` matching `Log Session` pixel/control parity while preserving 100% of legacy score-based metcon block fields in `residualLegacyBlocks`.
- **SC-002 (Zero Duplicate or Orphan `RepMax` Rows on Edit)**: Editing a PR weight `N` times or removing `K` PRs produces exact `RepMax` row counts (`added - removed`, `id` preserved on in-place weight changes) in both Room unit tests and Active Cycle Progress calculations.
- **SC-003 (100% Set-to-PR Auto-Sync & Override Accuracy)**: 100% of non-overridden PR slots track live `maxSetWeightFor(movementId)` across set edits/additions/deletions, while 100% of manual weight overrides remain insulated from set edits.
- **SC-004 (MCP Contract & Tombstone Convergence)**: `mcp-server` registers `34` tools with `0` `$ref` and `0` `exclusiveMinimum` schema violations; `update_block_session_log` + Android `UserCloudSyncManager` Step 5 converges PR reweights and deletions cleanly across local Room and Firestore in deterministic unit tests.
- **SC-005 (Accessibility & Responsive Layout)**: Zero clipping or touch-target violations (`>= 48.dp`) and full `imePadding()` / `tnum` compliance verified across new Compose UI/state unit tests and static inspections.
- **SC-006 (100% CI Parity)**: `.\gradlew.bat testDebugUnitTest --no-daemon`, `.\scripts\tests\Invoke-ScriptTests.ps1`, `.\gradlew.bat lintDebug --no-daemon`, `mcp-server` `npm run build && npm test`, `.\gradlew.bat assembleDebug --no-daemon`, and remote GitHub Actions PR workflows (`PR CI & Test Verification` + `PR Review`) all pass at 100%.

---

## Assumptions

- Users edit workouts one session at a time on `SessionEditorScreen` with a single active Room database instance; `withDatabaseTransaction` serializes local PR reconciliation.
- Unlinked legacy blocks without weight sets and with `resultText` / `resultValue` / non-weight `metricValue` continue to use `CompactBlockEditor` intentionally so score-based conditioning workouts lose no input controls.
- Authentication (`FirebaseAuth`) is optional (offline-first); when signed out, `UserCloudSyncManager.uploadUserData` exits gracefully as a no-op.
