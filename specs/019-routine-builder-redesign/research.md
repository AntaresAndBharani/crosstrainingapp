# Phase 0 Research: Daily Routine Builder Redesign & Multi-Metric Per-Set Targets

**Feature**: `019-routine-builder-redesign`  
**Date**: 2026-11-12

## 1. Zero-Migration Lossless Persistence Strategy (`RoutineBlock` & Cross-Channel Sync)

- **Decision**: Encode structured per-exercise target configurations as a leading `[rb_v2=...]` metadata header inside `RoutineBlock.notes` (positional slots aligned 1:1 with `exerciseIdsCsv` / `exerciseNamesCsv`), strip the header via `RoutineTargetCodec.extractCleanNotes(notes)` for user-facing display, and simultaneously populate `RoutineBlock.targetRepsScheme` with a clean human-readable summary string.
- **Rationale**:
  1. **Constitution Principle V & Schema Export Stability**: `AppDatabase` is pinned at `version = 10` (`RoomSchemaExportTest`), and Principle V mandates avoiding unnecessary Room schema migrations when features can be represented losslessly on existing entities—matching the proven `[metric=...,window=...]` pattern in `CycleGoal.notes` and `[subcycle=...]` in `Cycle.goal`.
  2. **100% Cross-Channel Transport Without Extra Wire Tables**: `RoutineBlock.notes`, `setsCount`, `targetRepsScheme`, and `exerciseIdsCsv` / `exerciseNamesCsv` are already transported by:
     - Local Room SQLite (`routine_blocks`)
     - CSV Backup (`BackupCsv` `#routineBlocks`)
     - Cloud Firestore User Sync (`UserCloudSyncManager` `routines` collection)
     - Community / Code Workout Sharing (`FirebaseSyncManager` `SharedBlockPayload`)
     - Remote MCP Server (`mcp-server/src/store.ts` `RoutineBlockRecord`)
  3. **Device-Independent Positional Alignment**: `SharedBlockPayload` and cross-device sync map exercises by `exerciseNamesCsv` rather than raw local Room primary keys. Encoding `[rb_v2=<slot0>;<slot1>;...]` in 1:1 positional order with `exerciseIdsCsv` / `exerciseNamesCsv` guarantees zero ID drift across devices, backups, share codes, and MCP mutations.
  4. **Preserving Intentional Sequence Repeats**: Complex/conditioning sequences like `Shuttle Run -> Burpees -> Shuttle Run` repeat an exercise ID (`"1,2,1"`). Updating `Repository.remapExerciseIdsCsv` and `UserCloudSyncManager.resolveExerciseCsv` to preserve positional duplicates whenever `RoutineTargetCodec.hasStructuredHeader(notes)` is true (while keeping `.distinct()` for legacy unstructured blocks) satisfies both `DatabaseDeduplicationAndSyncIdempotencyTest` and repeated-movement sequences.
- **Alternatives Considered**:
  - *Adding a new Room `routine_block_exercises` child table (`AppDatabase` v11)*: Rejected because it requires Room schema migration v10→v11, breaks CSV backup v5 format, requires breaking changes to `SharedBlockPayload` and Firestore `routines` documents, and violates Constitution Principle V's directive against unnecessary schema churn.
  - *Embedding numeric Room `exerciseId`s inside `[rb_v2=...]`*: Rejected because `SharedWorkoutPayload` and cross-device sync re-resolve exercise IDs by name on the target device, which would invalidate embedded numeric IDs.

## 2. 4-Tier Collapsible Hierarchy & Exclusive Block Expansion

- **Decision**: Extract a dedicated, stateless Compose presentation module `RoutineBuilderSheet.kt` backed by pure immutable state/reducer types (`RoutineBuilderUiState`, `RoutineBlockUi`, `RoutineExerciseUi`) rendered inside a full-height Material 3 `ModalBottomSheet` (`skipPartiallyExpanded = true`) or full-screen `Dialog` surface with sticky top bar, `imePadding()`, sticky bottom save bar, and `BackHandler(enabled = state.isDirty)` discard confirmation.
- **Rationale**:
  - Satisfies Constitution Principle I (MVVM & UDF) and Principle III (deterministic unit testing of every state transition without requiring a heavy Android emulator for logic verification).
  - Enforces exclusive block-level expansion (`expandedBlockId: String?`) so opening or adding Block `k` automatically collapses Block `j` into a compact single-row badge summary (`{TypeIcon} {BlockName}` + `[Format]` + `[{N} Set(s)]` + `[{M} Ex]`).
  - Supports `[Collapse All]`, auto-collapse of `RoutineInfo` once named and user focuses a block, collapsible `Block Setup` (`44.dp` summary bar), and collapsible per-exercise cards with cached per-metric `(fixedValue, perSetValues)` maps so switching metrics back and forth never discards user input.
- **Alternatives Considered**:
  - *Mutating `SnapshotStateList` directly inside `LibraryScreen.kt`*: Rejected because `LibraryScreen.kt` is already 1,681 lines long and inline mutable state makes exclusive expansion, dirty-state detection, per-metric caching, set-count sync, and Undo restoration brittle and harder to unit-test.

## 3. Contextual Execution Formats & Format-Driven Default Block Sets

- **Decision**: Add `GYMNASTICS("Gymnastics")` to `BlockKind` (both Android `Enums.kt` and MCP `BlockKindValues`), present 4 primary visual Block Type chips (`Weightlifting`, `WOD / Metcon`, `Gymnastics`, `Accessory`) + `More ▾` overflow menu for all remaining `BlockKind` values (`Strength`, `Hypertrophy`, `Complex`, `Superset`, `Cardio`, `Warm-up`, `Metabolic`, `Other`), filter primary Execution Format chips contextually by `BlockKind` (+ `More ▾`), and bind format selection to deterministic default `setsCount`:
  - `1` set: `AMRAP`, `For Time`, `Chipper`, `Death by`, `Time cap` (with unlocked `+` stepper and helper copy `"Defaults to 1 set for {format} — tap + for multiple sets"`).
  - `8` sets: `Tabata`.
  - `4` sets: `Sets x Reps`, `EMOM`, `E2MOM`, `E3MOM`, `Rounds for Time`, `Work/Rest Intervals`, `Calorie Sprints`, `Distance Sprints`, `Wave`, `Tempo` (and fallback default).
- **Rationale**: Eliminates the legacy 18-chip wall, duplicate format text field, and block-level rep inputs while preserving 100% of existing `BlockKind` enum values and keeping `shortLabel.length <= 15` (`LibraryScreenTest`).
- **Alternatives Considered**:
  - *Locking `Sets = 1` on AMRAP / For Time*: Explicitly rejected per specification—athletes frequently program multi-bout conditioning such as `3 Sets x 4-min AMRAP`, so the stepper must remain unlocked.

## 4. Responsive 360dp Layout, OS Font Scaling & Non-Color Skipped-Set Accessibility

- **Decision**:
  1. **Target Metric Selector (`Reps | Calories | Time | Meters`)**: Render single-line non-wrapping segments (`maxLines = 1`, `softWrap = false`) on `360dp` viewports; when `LocalDensity.current.fontScale >= 1.4f`, switch automatically to a horizontally scrollable `LazyRow` so labels never clip or wrap mid-word (`Cal-\nories`).
  2. **Per-Set Target Grid**: Compute column count purely via `resolvePerSetGridColumns(widthDp, fontScale)`: `2` columns when `fontScale >= 1.4f`, `6` columns when `widthDp >= 600`, and `4` columns otherwise.
  3. **Non-Color `0 (Skip)` Styling & Semantics**: Render `value == 0` set cards with `surfaceVariant.copy(alpha = 0.35f)` fill, a `1.dp` dashed outline (`PathEffect.dashPathEffect`), explicit `(Skip)` text (`0 (Skip)`, `0 cal (Skip)`, `0s (Skip)`, `0m (Skip)`), and merged TalkBack `contentDescription` (`"Set 4, 0 meters, skipped in this set"`), plus `HapticFeedbackType.TextHandleMove` ticks on steppers and toggles.
- **Rationale**: Satisfies Constitution Principle IV and WCAG AA across all 4 app themes (`LIGHT`, `DARK`, `LIGHT_HIGH_CONTRAST`, `DARK_HIGH_CONTRAST`).
- **Alternatives Considered**:
  - *Fixed 4-column `Row` at all font scales*: Rejected because `200%` OS font scaling on `360dp` screens clips micro-stepper labels.

## 5. Workout Logging Expansion (`Load Routine...` in `SessionEditor.kt`)

- **Decision**: Extract pure function `expandRoutineIntoSessionBlocks(rwb: RoutineWithBlocks, exercises: List<Exercise>, defaultCompleted: Boolean): List<BlockState>`:
  - **Multi-exercise routine block (`>= 2` resolved exercises)**: Expands into `M` `BlockState` entries sharing `subBlock = blk.subBlock.ifBlank { blk.name.ifBlank { "Block ${blockIdx + 1}" } }`, `format = blk.format`, and `section = blk.section` so `groupEditorBlocks(blocks)` automatically renders them inside a grouped `EditorBlockItem.SubBlockGroup` container in `SessionEditor` (with title, format timer chip, and round counter).
  - **Single-exercise routine block**: Creates a standalone `BlockState` (preserving `blk.subBlock` if explicitly set on a legacy triset).
  - **Active Set & Metric Pre-Population**:
    - Evaluates the `N` per-set target values (expanding `Fixed` into `List(setsCount) { fixedValue }`).
    - Filters out `0 (Skip)` sets (`target > 0`); if any set was skipped (or inside a multi-exercise sub-block), sets `SetState.group = (roundIndex + 1).toString()` so 1-based round numbers (`1, 2, 3` or `1, 3, 5` for alternating EMOMs) are preserved!
    - For `REPS`: pre-fills `SetState(reps = target.toString(), value = "", group = groupStr)` so athlete enters working weight.
    - For `CALORIES`, `TIME_SECONDS`, `METERS`: pre-fills `SetState(reps = "1", value = target.toString(), group = groupStr)` so the target calories/seconds/meters populate `SetDraft.metricValue` / `SetState.value` directly.
    - Strips `[rb_v2=...]` from `BlockState.description` via `RoutineTargetCodec.extractCleanNotes(blk.notes)`.
- **Rationale**: Directly implements the user-confirmed Option 1 behavior from Phase 1 (`Grill-Me`), leveraging the existing `EditorBlockItem.SubBlockGroup` infrastructure in `SessionEditor.kt` with zero Session/Room schema changes.
- **Alternatives Considered**:
  - *Collapsing all exercises of a mixed-metric block into a single `BlockState`*: Rejected by user in Phase 1 because a single `BlockState` can only track one `mainExerciseId` and one metric column, losing per-movement `Calories` + `Meters` + `Reps` sets and `0 (Skip)` round schedules.

## 6. Remote MCP Server Parity & Connector-Safe Schemas (`mcp-server/`)

- **Decision**:
  - Extend `BlockKindValues` in `mcp-server/src/schemas.ts` with `"GYMNASTICS"`.
  - Add `RoutineTargetMetricValues = ["REPS", "CALORIES", "TIME_SECONDS", "METERS"] as const` and `RoutineTargetModeValues = ["FIXED", "PER_SET"] as const`.
  - Add factory `makeRoutineBlockExerciseInputSchema()` returning fresh Zod instances (`name`, `metric?`, `mode?`, `fixedValue?` with `.int().min(0)`, `perSetValues?` array of `.int().min(0)`), attached as optional `exercises` on `makeRoutineBlockInputSchema()` while retaining optional `exerciseNames` and `targetRepsScheme` for 100% backward compatibility.
  - Implement TypeScript `encodeRoutineBlockTargets` / `decodeRoutineBlockTargets` in `mcp-server/src/schemas.ts` matching Android `RoutineTargetCodec` byte-for-byte (`[rb_v2=...]`), rejecting all-zero exercises (`fixedValue === 0` in `FIXED` mode or `perSetValues.every(v => v === 0)` in `PER_SET` mode), auto-creating missing exercises with inferred `metricType` (`CALORIES`→`CALORIES`, `TIME_SECONDS`→`TIME`, `METERS`→`DISTANCE`, `REPS`→`WEIGHT`/`REPS`), and returning enriched `exercises` + `cleanNotes` in `list_routines` and `upsert_routine`.
- **Rationale**: Satisfies User Story 4 and Constitution Principle VII (zero `$ref`, zero `exclusiveMinimum`, atomic `"exercises"` + `"routines"` transaction lock, full round-trip compatibility with Android Cloud Sync).
- **Alternatives Considered**:
  - *Reusing a shared `const RoutineBlockExerciseSchema = z.object(...)` across schemas*: Rejected because `zod-to-json-schema` deduplicates shared instances into `"$ref"`, which breaks Gemini MCP tool registration (Constitution Principle VII).
