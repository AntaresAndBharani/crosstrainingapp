# Feature Specification: Compact Complex Session Logging (`SPEC-028` / `SPEC-COMPLEX-LOGGING-02`)

**Feature Branch**: `feat/028-compact-complex-logging`  
**Created**: 2026-11-21  
**Status**: Approved  
**Input**: User request (`/e2e-sdlc --full --prompt https://share.gemini.google/2WP81OQxUocH`): Stakeholder UX/UI specification `SPEC-COMPLEX-LOGGING-02` locking Decisions `1A` (Unified Complex Set Table), `2A` (Linked by Default + Inline Override Escape Hatch), and `3A` (Hide 4-button metric switcher from default Complex logging view), plus per-set skipped movement sequences such as `1-2-3-1-2-3` and `3-1-3-1-3-1`.

## User Scenarios & Testing *(mandatory)*

### User Story 1 - Unified Compact Complex Set Table with Shared Per-Set Weights (Priority: P1)

As an athlete logging or editing a multi-exercise Complex (2 or more constituent movements) in `Log Session` or `History -> Edit Session`, I want to see all constituent movements and all sets on a single screen in a unified `S1..SN` compact set table where editing a set's shared weight updates every active movement in that round simultaneously, while clearly showing which movements are active or skipped (`—`) in each round.

**Why this priority**: Currently each sub-exercise inside a Complex renders its own stacked metric switcher bar and 2-column tall dual-row card grid, consuming ~1,800dp across 3 screens for a 3-movement, 6-set Complex and forcing athletes to re-type the same weight 12–18 times. Collapsing this into a unified Complex-level compact set table directly resolves the core screen-density and data-entry bottleneck.

**Independent Test**: Load a 3-movement, 6-set Complex (e.g. `Snatch + Hang Snatch + Snatch Balance` with a `1-2-3-1-2-3` or `3-1-3-1-3-1` target pattern) in `Log Session` and `Edit Session`, verify the unified `S1..S6` table renders with numbered legend (`①..③`), active/skipped micro-pills (`[① 1]`, `[② —]`), and linked shared `kg`/`lbs` weight inputs; type weights across `S1..S6` using `Next`, verify only active weighted movements receive weights, test `+ Add Set` (copying Set N weight and active/skip mask) and `✕` remove set with 4s `[Undo]` Snackbar, and verify pre-fill caption visibility (`Log Session` vs hidden in `Edit Session`).

**Acceptance Scenarios**:

1. **Given** a multi-exercise Complex (`>= 2` movements) is loaded in `Log Session` or `Edit Session`, **When** `SimplifiedBlockLogCard` renders, **Then** it delegates to `CompactComplexLogCard` showing: header (`"{order} COMPLEX: {title}"`, timer `⏱`, delete `🗑`), prescription box (`E4MOM · ...`), numbered legend strip (`① Name · ② Name ...` with trailing `By ex. ▸`), table caption row (`SET`, `MOVEMENTS`, `WEIGHT (KG/LBS)`), unified `S1..SN` rows (eliminating divergent `Set N` vs `Rd N` labels), `[ + Add Set ]` + conditional `"Pre-filled from last session ({Date})"` footer (hidden in Edit Mode), and tracked PR chips + `+ Record PR 🔥`.
2. **Given** a Complex has skipped rounds on some movements (e.g. `1-2-3-1-2-3` or `3-1-3-1-3-1`), **When** viewing `S1..S6`, **Then** each row displays one micro-badge per movement in order (`[① 1]` / `[① 10c]` / `[① 30s]` / `[① 200m]` tinted blue for active movements; `[② —]` dashed muted for `0` / skipped movements), and entering or editing a set's shared weight writes that `weightKg` to every active weighted (`REPS` + `WEIGHT`) movement in Set `k` while leaving skipped (`0`) movements untouched (`null`).
3. **Given** the athlete is typing weights across `S1..SN` on the decimal keypad, **When** tapping `ImeAction.Next`, **Then** focus traverses straight down the shared weight column (`S1 -> S2 -> ... -> SN`, skipping disabled rows and ending with `ImeAction.Done` on the last editable set), normalizing `,` to `.`, rejecting negative/malformed characters, and clamping `<= 9999`.
4. **Given** the athlete taps `[ + Add Set ]` or `✕` remove on Set `k`, **When** adding a set, **Then** `Set N+1` appends copying `Set N`'s shared weight (blank if skipped) and active/skipped movement mask with `CLOCK_TICK` haptic; **And When** removing a set (when `> 1` set remains), **Then** Set `k` removes across all movements, remaining rounds re-index cleanly `S1..S(N-1)`, and a 4-second `[Undo]` Snackbar restores the exact removed round slice.

---

### User Story 2 - Inline Per-Set Override Drawer (`2A`) & De-cluttered Metric Controls (`3A`) (Priority: P1)

As an athlete logging a Complex, I want the 4-button `Reps | Cal | Time | Meters` metric bar hidden from the default Complex card (`3A`) while still being able to tap any set row's movement strip to expand an inline drawer (`2A`) where I can toggle individual movements between `Active` and `Skip`, adjust per-movement target values, or split weights per exercise (showing clear `Mixed` / `Re-link all` states), plus optionally switch to a secondary `By ex. ▸` collapsible view when I need to inspect one movement or switch its logging unit.

**Why this priority**: Keeps the default Complex workout view ultra-clean (~520dp vs ~1,800dp) while preserving 100% power-user flexibility for mid-workout weight splits, skipped-movement toggles, rep adjustments, and rare metric switches without leaving the screen or popping disruptive dialogs.

**Independent Test**: Tap Set `3`'s movement strip on `CompactComplexLogCard`, verify `SetExerciseOverrideDrawer` expands inline via `AnimatedVisibility`, toggle movement ② between `Active` and `Skip` (verifying `Skip -> Active` inherits linked shared weight and `Active -> Skip` clears weight), change movement ③'s weight to differ from ① & ② (verifying Set 3 weight box switches to amber `Mixed`, movement pill ③ displays amber dot `•`, and `"Re-link all to {X} {unit}"` appears), test typing directly into the `Mixed` field to re-link with 4s `[Undo]` Snackbar, toggle `"By ex. ▸"` to inspect individual exercises and switch a movement's metric via `"Unit: {Metric} ▾"`, then tap `"Compact ▾"` to return.

**Acceptance Scenarios**:

1. **Given** a multi-exercise Complex is rendered in default compact mode, **When** inspecting the card, **Then** NO always-visible `Reps | Cal | Time | Meters` filter chip bars appear; each movement inherits its configured metric from Daily Routine target -> hydrated `[log_metric=...]` -> library `Exercise.metricType`, and the secondary `By ex. ▸` toggle switches to a collapsible per-exercise list containing a subtle `Unit: {Metric} ▾` selector that switches metrics while preserving cached per-metric inputs and canonical round alignment.
2. **Given** the athlete taps a set row's `MovementStatusStrip`, **When** `SetExerciseOverrideDrawer` expands inline below that row, **Then** each constituent movement renders on 1 compact row with movement name (`maxLines = 1`), `Active` / `Skip` `Role.Switch` pill (`CLOCK_TICK` haptic), inline integer target input + inherited unit (`reps`, `cal`, `s`, `m`), and individual decimal weight box for `REPS` movements.
3. **Given** a movement in Set `k` is toggled `Skip -> Active`, **When** toggled, **Then** its target restores to its prior non-zero / default value (fallback `1`) and if Set `k` was `LINKED` with a non-blank weight and the movement supports load, it immediately inherits that shared weight; **And When** toggled `Active -> Skip`, **Then** its weight/metric input clears and it is excluded from Set `k` weight writes and PR evaluation.
4. **Given** two active weighted movements in Set `k` have different positive weights (or one blank and one positive), **When** `resolveSharedSetWeight(k)` evaluates, **Then** Set `k`'s shared weight box displays amber `Mixed`, divergent movement pills render an amber indicator dot (`•`), the expanded drawer displays `"Re-link all to {majorityWeight} {unit}"`, and typing a weight directly into `Mixed` (or tapping `"Re-link all"`) applies one weight to all active weighted movements with a 4-second `[Undo]` Snackbar restoring the prior `Mixed` weights.
5. **Given** all movements in Set `k` are toggled to `Skip`, **When** inspecting Set `k`, **Then** all pills display `—`, the shared weight field shows disabled `"Skipped"` state, and the drawer shows `"All movements skipped in Set {k} — tap any exercise to re-enable or remove set."`.

---

### User Story 3 - Skip-Aware PR Auto-Sync, Mixed Modalities, Accessibility & Zero-Migration Round-Trip (Priority: P2)

As an athlete tracking PRs across multi-movement Complexes (including mixed barbell + bodyweight/erg complexes, compact `360dp` phones, large `>= 520dp` foldables, scaled fonts, and TalkBack), I want PR auto-sync to inspect only rounds where each movement was actually performed, non-weighted exercises to avoid false `kg` assignments, and all sparse/split/skipped states to persist and re-hydrate cleanly in Room & Cloud Firestore with zero database schema migration.

**Why this priority**: Guarantees data integrity across PR tracking, mixed-modality conditioning complexes, adaptive layouts/accessibility compliance (Constitution Principle IV), and zero-migration offline-first Room/Firestore persistence (Constitution Principle V).

**Independent Test**: Configure a `1-2-3-1-2-3` complex tracking PRs on both `Snatch` and `Hang Snatch`; log `52.5 kg` on Set 4 (`Snatch` active, `Hang Snatch` skipped) and `47.5 kg` on Set 5 (`Snatch + Hang Snatch` active), verify `Snatch` PR evaluates `52.5 kg` while `Hang Snatch` stays `47.5 kg`; add `Air Bike` (`CALORIES`) to a complex and verify shared `kg` applies only to barbell movements (`— (BW)` when only non-weighted exercises are active); save to `BlockDraft` -> `SessionWithBlocks` -> `hydrateSessionBlocksForEditor` -> `buildHydratedBlockState` and assert lossless canonical round restoration; verify `fontScale >= 1.4f` 2-line row reflow, `>= 520dp` pill names, and TalkBack semantic announcements.

**Acceptance Scenarios**:

1. **Given** a tracked PR slot on a constituent movement (e.g., `Hang Snatch` in a `1-2-3-1-2-3` complex), **When** `maxSetWeightFor(movementId)` evaluates PR candidates, **Then** skipped rounds (`skippedFlags[idx] == true`) are excluded even if another movement in the same complex logged a heavier weight in that round.
2. **Given** a Complex mixes a barbell lift (`Snatch`, `MetricType.WEIGHT`) and a bodyweight/erg movement (`Air Bike`, `CALORIES` or `Pull-ups`, `MetricType.REPS`), **When** editing the shared `kg` input in Set `k`, **Then** only active weighted movements (`activeMetric == REPS && libraryMetricType == WEIGHT`) receive `weightKg`, and if only non-weighted movements are active in Set `k`, the shared weight cell renders muted `— (BW)` (while still allowing explicit per-movement load entry inside the drawer).
3. **Given** a Complex is viewed across viewports and accessibility settings, **When** `fontScale >= 1.4f`, **Then** each `ComplexSetRow` reflows cleanly onto 2 lines (`Set k` + weight + `✕` on line 1; `MovementStatusStrip` pills on line 2) without clipping; **When** available card width `>= 520dp`, **Then** movement pills include short exercise names (`① Snatch · 1`); **And** every interactive element enforces `>= 48x48.dp` touch targets and merged TalkBack `contentDescription` strings without cryptic symbols.
4. **Given** a Complex logged via `CompactComplexLogCard` (with sparse `groupIndex` rounds, `Mixed` weights, or an all-skipped movement) is saved and re-opened in `History -> Edit Session`, **When** `AvailableBlockCodec.hydrateSessionBlocksForEditor` and `buildHydratedBlockState` run, **Then** every round index `1..N`, active/skipped mask, weight (`Linked` or `Mixed`), target reps/metrics, and tracked PR reconciles losslessly with zero Room schema changes (`pre-filled` caption hidden in Edit Mode).

---

### User Story 4 - Remote MCP Shared Complex Weight Shorthand (`shared_set_weights_kg`) (Priority: P2)

As an athlete logging or updating Complex or Daily Routine sessions through an AI assistant via Remote MCP (`log_complex_session`, `log_block_session`, `update_complex_session_log`, `update_block_session_log`, `log_daily_routine_session`), I want to supply a single `shared_set_weights_kg` array (with optional `exercise_targets` skip patterns or pre-configured Daily Routine targets) so the MCP server expands shared per-set weights across active weighted movements while skipping `0`-target rounds and preserving backward compatibility with explicit per-exercise logs and flat tool schemas.

**Why this priority**: Enforces Constitution Principle VII and dual-client Android + MCP parity so AI assistants can log shared-weight complexes (including `1-2-3-1-2-3` skip patterns) cleanly in one concise call.

**Independent Test**: Invoke `log_complex_session` with `shared_set_weights_kg` and `exercise_targets` (`1-2-3-1-2-3`), `log_daily_routine_session` with `shared_set_weights_kg` on a Complex item having configured `exerciseTargets`, and `update_complex_session_log` with `shared_set_weights_kg`, verifying persisted sets, `groupIndex` values, non-weighted movement isolation, partial per-exercise override merging, and `tools/list` flat schema invariants (`45` tools, `0` `$ref`, `0` `exclusiveMinimum`).

**Acceptance Scenarios**:

1. **Given** an MCP client calls `log_complex_session` (or `log_block_session`) with `shared_set_weights_kg: [40, 42.5, 45, 47.5, 47.5, 50]` and `exercise_targets` specifying `1-2-3-1-2-3` (`0 = Skip`), **When** persisted, **Then** `Snatch` stores 6 sets (`groupIndex 1..6`), `Hang Snatch` stores 4 sets (`groupIndex 2,3,5,6`), `Snatch Balance` stores 2 sets (`groupIndex 3,6`), and non-weighted movements (`CALORIES`/`TIME`/`DISTANCE` or `REPS`) never receive `kg` loads.
2. **Given** an MCP client calls `log_daily_routine_session` with `shared_set_weights_kg` on a `COMPLEX` item whose Daily Routine template already defines per-exercise `0` (Skip) rounds, **When** persisted, **Then** the server automatically honors the routine item's `exerciseTargets`, applying shared weights strictly to active weighted rounds and permitting explicit `exercise_logs` overrides for mixed sets.
3. **Given** an MCP client calls `update_complex_session_log` (or `update_block_session_log`) with `shared_set_weights_kg`, **When** updating an existing Complex session without explicit `exercise_targets`, **Then** the server preserves each constituent block's existing active `groupIndex` round mask.
4. **Given** any MCP client inspects `tools/list`, **When** validating schemas, **Then** all `45` tools remain registered with `0` `$ref` and `0` `exclusiveMinimum` occurrences.

---

### Edge Cases

- What happens when a Complex contains only 1 exercise (`exerciseSections.size == 1`) or is a standalone Exercise (`SessionItemKind.EXERCISE`)? It continues rendering via the single-exercise tile grid (`SessionExerciseSetTilesSection`), leaving single-movement flows and existing tests unaffected.
- What happens when a user types a trailing decimal separator like `"45."` or `"45,"` in a linked `SharedSetWeightInput`? `sanitizeCompactWeightInput` converts `,` to `.` while preserving the in-progress trailing `.` so `resolveSharedSetWeight` stays `LINKED` with `"45."` mid-typing rather than snapping to `MIXED` or stripping the decimal dot.
- What happens when a Complex has a constituent movement that is skipped across EVERY round (`sets = emptyList()`) on save? `toBlockDrafts` emits `sets = emptyList()`, `AvailableBlockCodec.hydrateSessionBlocksForEditor` marks `hasNoLoggedSets = true`, and `buildHydratedBlockState` restores that movement as skipped across all canonical rounds on Edit Session.
- What happens when a Complex is loaded from a Daily Routine whose prior session had NO `groupIndex` tags vs partial `groupIndex` tags? If `groupIndex` values exist, pre-fill aligns by canonical round (`groupIndex - 1`); otherwise pre-fill aligns sequentially onto active rounds (`activeSlotIdx`).
- What happens when `Update` or `Add Set` adds a 7th set onto a `1-2-3-1-2-3` complex? `addComplexSet()` copies Set 6's shared weight and active/skipped mask so the athlete can toggle any individual movement active or skipped inside Set 7's inline drawer in one tap.

## Requirements *(mandatory)*

### Functional Requirements

- **FR-001**: `SimplifiedBlockLogCard` MUST delegate multi-exercise Complexes (`itemKind == SessionItemKind.BLOCK && exerciseSections.size >= 2`) to `CompactComplexLogCard` across both `Log Session` and `History -> Edit Session`, while keeping single-exercise Blocks and `SimplifiedExerciseLogCard` unchanged.
- **FR-002**: `CompactComplexLogCard` MUST render a unified `S1..SN` compact set table (`SET`, `MOVEMENTS`, `WEIGHT ({UNIT})`) with numbered legend (`①..⑤`), skip-aware movement micro-badges (`[① 1]`, `[② —]`), and linked-by-default shared weight inputs (`KeyboardType.Decimal`, `,` -> `.` normalization, `ImeAction.Next` vertical column traversal).
- **FR-003**: `CompactComplexLogCard` MUST hide the 4-button `Reps | Cal | Time | Meters` chip bar from the default Complex logging view (`3A`) and relocate per-exercise metric switching behind a subtle `Unit: {Metric} ▾` control inside the secondary collapsible `By ex. ▸` / `Compact ▾` view.
- **FR-004**: Tapping any set's `MovementStatusStrip` MUST toggle an inline `SetExerciseOverrideDrawer` (`AnimatedVisibility`) listing each constituent movement's name (`maxLines = 1`), `Active` / `Skip` `Role.Switch` pill (`CLOCK_TICK` haptic), inline target value + inherited unit, and per-movement weight field (`2A`).
- **FR-005**: Toggling `Skip -> Active` MUST restore target reps/metric (defaulting to prior non-zero or `1`) and inherit Set `k`'s linked shared weight when weighted; toggling `Active -> Skip` MUST clear that movement's weight/metric for Set `k` and exclude it from shared weight writes and PR evaluation.
- **FR-006**: `LoadedAvailableBlockState.resolveSharedSetWeight(setIndex)` MUST deterministically classify every round into `LINKED`, `MIXED` (reporting `minWeight`, `maxWeight`, `majorityWeightText`, and `divergentExerciseIndices`), `ALL_SKIPPED`, or `NON_WEIGHTED_ONLY`.
- **FR-007**: `Mixed` weight rows MUST display amber styling + `"Mixed"` label, mark divergent movement pills with an amber dot (`•`), show `"Re-link all to {X} {unit}"` in the expanded drawer, and offer a 4-second `[Undo]` Snackbar whenever typing into `Mixed` or tapping `"Re-link all"` re-links divergent weights.
- **FR-008**: `[ + Add Set ]` MUST append a canonical round copying the final set's shared weight (blank if skipped) and active/skipped mask with `CLOCK_TICK` haptic; `✕` remove MUST delete the round across all constituent movements (when `complexSetCount > 1`), re-index remaining rounds, and offer a 4-second `[Undo]` Snackbar restoring the exact removed round.
- **FR-009**: `LoadedAvailableBlockState.blockMaxSetWeight` and `maxSetWeightFor` MUST ignore skipped rounds (`skippedFlags`) so tracked PR auto-sync never attributes weights from rounds where a movement was skipped.
- **FR-010**: `CompactComplexLogCard` and `SessionEditor` MUST preserve zero-migration Room v10 & Firestore compatibility using `SessionBlock` + `BlockSet` (`groupIndex` 1-based canonical round tags when any round skips a movement) and `AvailableBlockCodec.hydrateSessionBlocksForEditor` (`hasNoLoggedSets`, `exerciseGroupIndicesById`/`Name`), hiding the pre-fill caption in Edit Mode.
- **FR-011**: `CompactComplexLogCard` MUST enforce `>= 48.dp` touch targets, `tabular-nums`, WCAG AA Light/Dark contrast tokens, `widthIn(max = 640.dp)`, `>= 520dp` short movement names in pills, `fontScale >= 1.4f` 2-line row reflow, `.imePadding()` + `BringIntoViewRequester` keyboard avoidance, and merged TalkBack `contentDescription` labels free of cryptic symbols.
- **FR-012**: `mcp-server` (`log_complex_session`, `log_block_session`, `update_complex_session_log`, `update_block_session_log`, `log_daily_routine_session`) MUST accept optional `shared_set_weights_kg` (plus optional `exercise_targets` on complex log/update) and expand shared weights onto active weighted rounds while omitting `0`-target skipped rounds, keeping all `45` tools flat (`0` `$ref`, `0` `exclusiveMinimum`).

### Key Entities

- **`SharedSetWeightStateKind` & `SharedSetWeightSummary`**: Derived per-round state projection classifying Set `k` across all constituent `LoadedAvailableBlockExerciseState` sections (`LINKED`, `MIXED`, `ALL_SKIPPED`, `NON_WEIGHTED_ONLY`) with divergent movement indices and majority weight metadata.
- **`ComplexSetWeightsSnapshot` & `RemovedComplexSetSnapshot`**: Immutable undo snapshots capturing per-exercise weights before a `Mixed -> Linked` edit or a full canonical round slice (`weight`, `perSetRep`, `repsInput`, `metricInput`, `groupIndex`, `skipped`) before `removeComplexSet`.
- **`LastBlockSessionSummary` & `HydratedBlockExerciseSeed` Extensions**: Existing codec models extended with `exerciseGroupIndicesById/Name` and `hasNoLoggedSets` so sparse/skipped complex sets align to canonical rounds `1..N` across `Log Session` pre-fill and `Edit Session` hydration.

## Success Criteria *(mandatory)*

### Measurable Outcomes

- **SC-001**: A 3-exercise, 6-set Complex (including `1-2-3-1-2-3` and `3-1-3-1-3-1` skip patterns) renders all 6 rounds and all 3 movements simultaneously inside a single compact card (~520dp vs ~1,800dp legacy stacked layout) without horizontal overflow or clipping on `360dp` viewports up to `fontScale = 2.0f`.
- **SC-002**: Entering or editing weights for a shared-weight 3-exercise, 6-set Complex requires 6 field entries (`1` per set) instead of `12–18`, with `ImeAction.Next` stepping sequentially from `S1` to `SN`.
- **SC-003**: 100% of existing and new Android unit tests (`.\gradlew.bat testDebugUnitTest --no-daemon`), Android lint (`.\gradlew.bat lintDebug --no-daemon`), script tests (`.\scripts\tests\Invoke-ScriptTests.ps1`), and MCP tests (`npm run build && npm test` in `mcp-server/`) pass with `0` regressions, `45` tools, `0` `$ref`, and `0` `exclusiveMinimum`.

## Assumptions

- Target platforms remain Android (Jetpack Compose / Material 3) and Remote MCP (`mcp-server/`); Web and iOS remain out of scope.
- Room database schema stays at version 10; sparse/skipped Complex rounds continue encoding via `BlockSet.groupIndex` (1-based canonical round index) and empty `BlockSet` rows (`hasNoLoggedSets`) without schema or Firestore security rule changes.
- All user-facing Complex UI terminology continues using `"Complex"` / `"Complexes"` per `SPEC-027`.
