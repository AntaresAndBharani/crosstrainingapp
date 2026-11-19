# Feature Specification: Optional Daily Routine Multi-Metric Targets & Metric-Aware Workout Logging (`SPEC-026`)

**Feature Branch**: `feat/026-daily-routine-metric-targets`  
**Created**: 2026-11-19  
**Status**: Approved  
**Input**: User request (`/e2e-sdlc --full --prompt`): "After the latest changes when we create a daily routine or logging a workout we cannot add reps/cal/time to the exercises. We have to get that functionality back." + Stakeholder clarifications during `/grill-me` discovery.

## User Scenarios & Testing *(mandatory)*

### User Story 1 - Optional Per-Exercise Multi-Metric Targets on Daily Routine Items (Priority: P1)

As a coach or athlete building or editing a composite Daily Routine (`Library -> Routines`), I want optional, unset-by-default per-exercise target controls (`Sets`, `Metric`: Reps / Calories / Time / Meters, `Fixed` vs `Per-Set` including `0 (Skip)` rounds) on both standalone Exercises and Block items inside a Daily Routine, while keeping `Library -> Blocks` and `Library -> Exercises` lightweight and instructions-only.

**Why this priority**: Restores the ability to prescribe structured reps, calories, time, or distance targets inside Daily Routines without forcing extra fields when an athlete only wants a lightweight routine outline or block instructions.

**Independent Test**: Open `Library -> Routines`, create a Daily Routine containing a Saved Block and two standalone Exercises. Leave one exercise unset (`+ Set targets (optional)`), enable optional targets on the second exercise (e.g., `4 sets · 8 reps/set` or Per-Set `15 • 15 • 12 • 0 cal`), optionally enable per-routine targets on the Block item's constituent exercises, verify card chips/summaries show configured targets cleanly, re-open the routine to verify exact round-trip preservation, and clear targets back to unset cleanly.

**Acceptance Scenarios**:

1. **Given** a user creates or edits a Daily Routine in `Library -> Routines`, **When** adding a standalone Exercise or a Saved Block, **Then** target configuration starts **unset** (`+ Set targets (optional)`) so saving works without configuring targets, and existing `Library -> Blocks` (`QuickCreateBlockSheet`), `AddBlockToSessionSheet`, and `ExerciseEditorDialog` remain lightweight and instructions-only.
2. **Given** the user taps `+ Set targets (optional)` on a standalone Exercise item inside a Daily Routine, **When** the collapsible target panel expands, **Then** the user can configure `Sets` (`>= 1` stepper + quick pills `1, 3, 4, 5, 6, 8`), `Metric` (`Reps`, `Calories`, `Time`, `Meters`, auto-defaulted from the exercise's library metric), and `Mode` (`Fixed` with metric-aware presets vs `Per-Set` with per-set inputs, `0 (Skip)` visual indicators, and pattern helpers `Copy Set 1 to All`, `Descending`, `Alternate 0s`), plus collapse (`Done ▲`) or `Clear targets`.
3. **Given** the user taps `+ Set targets (optional)` on a Block item inside a Daily Routine (whose referenced Available Block contains exercises), **When** the collapsible block target panel expands, **Then** the user can configure a shared `Sets` count and per-exercise targets (`Metric`, `Fixed` vs `Per-Set`, `0 (Skip)`) scoped strictly to that Daily Routine without mutating the underlying Available Block.
4. **Given** an item has enabled targets where every set is `0` (`Fixed = 0` or all `Per-Set` values are `0`), **When** attempting to save the Daily Routine, **Then** saving is blocked with an inline error (`"Set a target above 0 on at least one set, or clear targets"`), whereas cleared/unset targets remain valid.

---

### User Story 2 - Metric-Aware Set Tiles & Edit Session Hydration in Log/Edit Session (Priority: P1)

As an athlete logging or editing a workout (`Log Session` or `Edit Session`), I want both standalone `EXERCISE:` cards and `BLOCK:` cards to let me record `Reps` (with optional working weight for bodyweight or loaded sets), `Calories`, `Time`, or `Meters`, pre-filled from Daily Routine targets and historical sessions, and re-open cleanly in the unified card editor when editing saved sessions.

**Why this priority**: Restores direct workout logging of reps, calories, time, and meters alongside weights—fixing the regression where only set weights could be entered and unweighted rep/metric blocks fell back or dropped data.

**Independent Test**: Load a Daily Routine with mixed targets (`Back Squat: 4×8 reps`, `Pull-ups: 3×12 reps`, `Air Bike: 15•15•12•0 cal`), verify active sets expand with `Reps + Weight` tiles for `REPS` exercises and `cal` tiles for `Air Bike` (skipping round 4 while displaying round badges `Rd 1..3`), switch an ad-hoc exercise's inline metric chip (`Reps` / `Cal` / `Time` / `Meters`) verifying value preservation, save weighted, bodyweight rep-only, and calorie/time/meter sets, then re-open the session in `Edit Session` and verify every set hydrates back into `SimplifiedExerciseLogCard` / `SimplifiedBlockLogCard` with its metric, reps, weight/metric value, and round index intact.

**Acceptance Scenarios**:

1. **Given** a `SimplifiedExerciseLogCard` or `SimplifiedBlockLogCard` section in `Log Session` or `Edit Session`, **When** viewing the exercise's set grid, **Then** single-line metric chips (`Reps`, `Cal`, `Time`, `Meters`) appear for switching the active logging metric (defaulting from Daily Routine target -> hydrated session metric -> library exercise metric, preserving cached per-metric inputs when toggling).
2. **Given** an exercise's active metric is `Reps`, **When** rendering set tiles on a phone viewport, **Then** responsive tiles (2 columns on `360dp`, 1 column at `fontScale >= 1.5f`, 4 columns on `>= 600dp`) show both an integer `Reps` input and an optional decimal `Weight` (`kg`/`lbs`) input, saving sets whenever a valid weight or rep count is entered (allowing bodyweight rep-only sets with blank/null weight).
3. **Given** an exercise's active metric is `Calories`, `Time`, or `Meters`, **When** rendering set tiles, **Then** responsive tiles (3 columns on `360dp`, 2 columns at `fontScale >= 1.3f`, 4 columns on `>= 600dp`) accept the metric value (`cal`, `s`, or `m`) and persist it into `metricValue` alongside the active metric tag so Edit Session and analytics recognize the unit.
4. **Given** a saved session contains unweighted rep-only sets, `metricValue` (cal/time/meters) sets, or preserved `groupIndex` round numbers (without warm-up/failed flags or legacy Metcon `resultText`), **When** opening `Edit Session`, **Then** those blocks hydrate directly into `SimplifiedExerciseLogCard` / `SimplifiedBlockLogCard` rather than falling back to the legacy `CompactBlockEditor` table (while warm-up, failed, and legacy metcon text/score blocks still stay on `CompactBlockEditor`).

---

### User Story 3 - Multi-Metric History, Progress Aggregation & Pre-Fill Parity (Priority: P2)

As an athlete reviewing training consistency and progressive overload, I want `History`, `Progress -> By routine`, and future session pre-fills to accurately display and track weighted sets (`80×8 kg`), bodyweight rep sets (`12 reps`), and conditioning metric sets (`15 cal`, `45s`, `250m`).

**Why this priority**: Logging reps, calories, time, and meters must flow end-to-end into session pre-fills, workout history pills, and Daily Routine progress KPIs/charts so non-barbell and rep-driven exercises show accurate progress instead of empty placeholders.

**Independent Test**: Log sessions containing weighted reps (`100×5`), bodyweight reps (`12, 14, 15`), and calories (`15, 18, 20 cal`), inspect `HistoryScreen`, `DailyRoutineCodec.resolveLastExerciseSession`, `AvailableBlockCodec.resolveLastBlockSession`, and `ProgressScreen -> By routine`, verifying set tokens, pre-filled reps/metric values, KPIs, and unit labels (`kg`, `reps`, `cal`, `s`, `m`) reflect all recorded data.

**Acceptance Scenarios**:

1. **Given** an exercise or block was previously logged with weighted reps, bodyweight reps, calories, time, or meters, **When** adding that exercise, block, or Daily Routine in a later `Log Session` (without overriding targets), **Then** the tiles pre-fill the prior session's active metric, reps, weights, and/or metric values with an accurate last-session summary banner.
2. **Given** a Daily Routine item has configured targets AND prior session weight history for a `Reps` exercise, **When** loading that Daily Routine in `Log Session`, **Then** active set count, round numbers, and target reps come from the Daily Routine targets while set weights pre-fill from the latest prior session.
3. **Given** `HistoryScreen` or `ProgressScreen` (`By routine` and `BlockHistoryCard`) renders sets, **When** inspecting weighted sets, bodyweight rep sets, or metric-value sets, **Then** weighted sets display `weight×reps`, metric-value sets display `value unit` (without spurious `×1`), bodyweight rep sets display `reps`, and Daily Routine constituent drill-downs pass the constituent's resolved unit (`kg`, `reps`, `cal`, `s`, or `m`) to KPI cards and charts.

---

### User Story 4 - Zero-Migration Persistence, Cloud Sync & Remote MCP Parity (Priority: P2)

As an athlete using both Android Cloud Sync and connected AI assistants (Remote MCP), I want Daily Routine optional targets and multi-metric workout logs (`reps`, `weight`, `metricValue`, `groupIndex`, `[log_metric=...]`) to persist without any Room database migration and stay 100% consistent across Firestore sync and all Remote MCP Daily Routine & Block tools.

**Why this priority**: Dual-client parity and zero-migration offline-first sync are non-negotiable architectural guarantees.

**Independent Test**: Round-trip Daily Routines (both unset and with optional multi-metric targets on `EXERCISE` and `BLOCK` items) and logged sessions across Android codecs, `mcp-server` (`upsert_daily_routine`, `list_daily_routines`, `log_daily_routine_session`, `get_daily_routine_progress`, `log_block_session`, `update_block_session_log`), and `tools/list` schema validation (`39` tools, `0` `$ref`, `0` `exclusiveMinimum`).

**Acceptance Scenarios**:

1. **Given** a Daily Routine with optional targets on `EXERCISE` and/or `BLOCK` items is created or updated via Android or Remote MCP `upsert_daily_routine`, **When** listed via `list_daily_routines` or synced via Firestore, **Then** all target fields (`sets_count`, `target_metric`, `target_mode`, `fixed_value`, `per_set_values` / `exercise_targets`) round-trip losslessly, while items without targets omit target metadata headers.
2. **Given** a Remote MCP client invokes `log_daily_routine_session`, `log_block_session`, or `update_block_session_log` with explicit `metric` and `sets` (`reps`, `weight_kg`/`weight`, `metric_value`, `group_index`), **When** stored and queried via `get_daily_routine_progress` or synced to Android, **Then** every set's reps, weight, metric value, round index, and metric header persist accurately while legacy `set_weights_kg` / `weights` callers continue working identically.
3. **Given** any MCP client queries `tools/list`, **When** inspecting all tool schemas, **Then** exactly 39 tools exist and every tool schema remains completely flat (`0` `$ref`, `0` `exclusiveMinimum`).

---

### Edge Cases

- What happens when a user edits a legacy Daily Routine whose items have NO targets configured? Items show `+ Set targets (optional)` and save cleanly with `setsCount = 1`, `targetRepsScheme = ""`, and no `[rb_v2=...]` header.
- What happens when a user switches an exercise's active metric in `Log Session` (`Reps` -> `Cal` -> `Time` -> `Reps`) after typing values? Previously typed inputs for each metric are cached per-section in editor state and restored when switching back.
- What happens when a user loads a Daily Routine with `[15, 15, 12, 0]` Calories and saves without checking `"Also update ... routine template"` vs checking it? Leaving it unchecked keeps the template unchanged; checking it updates the template structure while preserving existing configured targets on surviving items.
- What happens when a user adds a bodyweight exercise (or switches to `Reps`) and enters `12` reps with empty weight? The set saves with `reps = 12, weight = null, metricValue = null`, does NOT trigger a false PR chip, and re-opens cleanly in `SimplifiedExerciseLogCard` on `Edit Session`.
- What happens when a user edits a historical session containing warm-up sets, failed sets, or Metcon score (`resultText`/`resultValue`) blocks? Those blocks stay on `CompactBlockEditor` so no historical flags or WOD scores are lost.

## Requirements *(mandatory)*

### Functional Requirements

- **FR-001**: `LibraryScreen` (`QuickCreateBlockSheet`, `ExerciseEditorDialog`) and `AddBlockToSessionSheet` MUST remain lightweight and instructions-only without adding target configuration inputs to Available Blocks or Exercises.
- **FR-002**: `DailyRoutineItem.StandaloneExercise` and `DailyRoutineItem.BlockRef` MUST support optional, unset-by-default target configuration (`targetSetsCount`, `RoutineExerciseTargetSpec` per exercise) encoded via existing `RoutineBlock.setsCount`, `RoutineBlock.targetRepsScheme`, and `[rb_v2=...]` notes without altering Room schema version 10.
- **FR-003**: `DailyRoutineCodec.toRoutineWithBlocks` and `fromRoutineWithBlocks` MUST preserve `[dr_item=...]`, `[rb_v2=...]`, `[ab_prs=...]`, and clean notes across any header line order, emitting `[rb_v2=...]` ONLY when targets are explicitly configured.
- **FR-004**: `DailyRoutineComposerSheet` MUST display `"+ Set targets (optional)"` on unset items, expand inline collapsible `Sets + Metric (Reps / Calories / Time / Meters) + Fixed / Per-Set` controls (including `0 (Skip)` cards, presets, pattern helpers, per-metric caches, `Done ▲`, and `Clear targets`), and reject saving when an enabled target has `0` across all sets.
- **FR-005**: `DailyRoutineCard` item chips MUST display compact configured target summaries (e.g. `4×8 reps`, `15•15•12•0 cal`) when targets are set, while preserving clean `BLOCK: {Title}` / `EX: {Name} ({note})` labels when targets are unset.
- **FR-006**: `SimplifiedExerciseLogCard` and `SimplifiedBlockLogCard` MUST provide single-line per-exercise metric switcher chips (`Reps`, `Cal`, `Time`, `Meters`) auto-initialized from Daily Routine target -> hydrated session `[log_metric=...]` -> library `Exercise.metricType`, preserving per-metric entered inputs during editing.
- **FR-007**: When active metric is `Reps`, set tiles MUST render in a responsive 2-column grid (1 col at `fontScale >= 1.5f`, 4 cols on `>= 600dp`) with stacked integer `Reps` and optional decimal `Weight` inputs; when active metric is `Calories`, `Time`, or `Meters`, set tiles MUST render in a responsive 3-column grid (2 cols at `fontScale >= 1.3f`, 4 cols on `>= 600dp`) accepting integer/decimal metric values (`cal`, `s`, `m`).
- **FR-008**: Loading a Daily Routine with configured targets into `Log Session` / `Edit Session` MUST omit `0 (Skip)` rounds while displaying preserved round labels (`Rd N`), pre-fill target reps/cal/time/meters, and for `Reps` also pre-fill historical set weights from the latest prior session.
- **FR-009**: Saving sets from `LoadedAvailableBlockState` MUST persist `reps`, optional `weight`, optional `metricValue`, optional `groupIndex`, and `[log_metric=...]` in `SessionBlock.notes` via `BlockDraft` and `AppViewModel`, allowing bodyweight rep-only sets and non-weight metric sets without emitting bogus `1kg` or false PR chips.
- **FR-010**: `AvailableBlockCodec.hydrateSessionBlocksForEditor` MUST hydrate unweighted rep-only sets, `metricValue` sets, and `groupIndex` sets into `SimplifiedBlockLogCard` / `SimplifiedExerciseLogCard`, leaving only warm-up, failed, and legacy metcon/score blocks on `CompactBlockEditor`.
- **FR-011**: `DailyRoutineCodec.resolveLastExerciseSession`, `AvailableBlockCodec.resolveLastBlockSession`, `BlockPerformance`, `HistoryScreen`, and `ProgressScreen` MUST accurately summarize, pre-fill, chart, and format weighted reps (`weight×reps`), bodyweight reps (`reps`), and `metricValue` (`cal`/`s`/`m`) sets with accurate units.
- **FR-012**: `SessionEditor` structural diff & `"Also update ... routine template"` sync MUST preserve configured targets on surviving Daily Routine items.
- **FR-013**: `mcp-server` (`upsert_daily_routine`, `list_daily_routines`, `log_daily_routine_session`, `get_daily_routine_progress`, `log_block_session`, `update_block_session_log`) MUST maintain full parity with Android optional routine targets (`[rb_v2=...]`), per-set `reps`/`metric_value`/`group_index`/`metric` (`[log_metric=...]`), and multi-metric progress while maintaining `39` tools, `0` `$ref`, and `0` `exclusiveMinimum`.
- **FR-014**: All domain codecs, ViewModel/draft reducers, Daily Routine composer state, Session Editor expansion/hydration, Progress analytics, and MCP contracts MUST pass automated JVM (`testDebugUnitTest`), PowerShell (`Invoke-ScriptTests.ps1`), Lint (`lintDebug`), and MCP (`npm run build && npm test`) test suites at 100%.

### Key Entities

- **DailyRoutineItemTargetConfig**: Optional `targetSetsCount` and `RoutineExerciseTargetSpec` (on `StandaloneExercise`) or shared `targetSetsCount` + per-exercise map (on `BlockRef`), persisted in `RoutineBlock` without DB migration.
- **SessionExerciseLogState**: Per-exercise logging state tracking `activeMetric` (`REPS` | `CALORIES` | `TIME_SECONDS` | `METERS`), ordered `repsInputs`, `weights`, `metricInputs`, `groupIndices`, and per-metric input caches.
- **LogMetricHeader (`[log_metric=...]`)**: Metadata marker persisted in `SessionBlock.notes` disambiguating active logging metric across Android, Firestore sync, and Remote MCP.

## Success Criteria *(mandatory)*

### Measurable Outcomes

- **SC-001**: Users can configure optional `Sets` and `Reps` / `Calories` / `Time` / `Meters` (`Fixed` or `Per-Set` with `0 / Skip`) on any Daily Routine item—or leave items completely unset—and round-trip every configuration losslessly across save, edit, sync, and MCP.
- **SC-002**: Users logging or editing any workout (`EXERCISE:` or `BLOCK:` card) can record `Reps + optional Weight`, `Calories`, `Time`, or `Meters` per set, switch metrics without losing typed values, and re-open saved sessions in `Edit Session` with 100% of set values and round numbers preserved.
- **SC-003**: All interactive controls, metric chips, and set tiles render cleanly without text clipping or layout overflow on `360dp` viewports up to `fontScale = 1.5f` with `>= 48.dp` interactive touch targets and merged TalkBack accessibility descriptions.
- **SC-004**: 100% of automated verification checks (`testDebugUnitTest`, `Invoke-ScriptTests.ps1`, `lintDebug`, `mcp-server` `npm run build && npm test`) pass without error.

## Assumptions

- Room SQLite schema remains at version 10 with zero database migrations; all new metadata uses existing text/numeric columns and bracketed metadata markers (`[rb_v2=...]`, `[log_metric=...]`).
- Available Blocks in `Library -> Blocks` intentionally stay lightweight (title, instructions, exercises, tracked PRs) per stakeholder preference; per-routine target overrides for a block's exercises live on the `DailyRoutineItem.BlockRef` inside `DailyRoutine`.
- Existing `set_weights_kg` / `weights` MCP callers remain 100% backward compatible when `sets` / `metric` are omitted.
