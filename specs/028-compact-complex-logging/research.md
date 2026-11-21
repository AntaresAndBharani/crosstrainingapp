# Research & Codebase Decisions: Compact Complex Session Logging (`SPEC-028`)

## Decision 1: Presentation & Editing Projection Over Existing Room v10 Schema
- **Context**: `SessionBlock` and `BlockSet` already store one `SessionBlock` per constituent exercise of a Complex (grouped by `routineId` or `subBlock`) with `BlockSet.groupIndex` representing 1-based canonical round indices when rounds are skipped.
- **Decision**: Implement `CompactComplexLogCard` as an in-memory canonical round projection over `LoadedAvailableBlockState.exerciseSections` (`itemKind == SessionItemKind.BLOCK && exerciseSections.size >= 2`), requiring zero Room migrations and zero Firestore security rule changes.
- **Alternatives Considered**: Creating a new Room entity `ComplexSetOverride` or changing `SessionBlock` foreign keys — rejected because Constitution Principle V forbids unnecessary schema changes when existing entities suffice.

## Decision 2: Canonical Round Alignment & `hasNoLoggedSets` Hydration
- **Context**: In `SimplifiedBlockLogComponents.kt` (`buildTargetOrPrefillExerciseState`) and `AvailableBlockCodec.kt` (`hydrateSessionBlocksForEditor`), single-exercise blocks omit skipped `0`-target sets into sparse arrays (`[roundIdx + 1]`). For a single movement, that works cleanly; for multi-exercise complexes (`1-2-3-1-2-3`), each movement would otherwise have a different array length (`6`, `4`, `2` elements), breaking row alignment across `S1..S6`.
- **Decision**:
  1. Keep single-exercise blocks (`block.exercises.size <= 1`) and `StandaloneExercise` completely unchanged (`activeRoundIndices` behavior preserved for `SessionEditorDailyRoutineAndExerciseTest`).
  2. For multi-exercise complexes (`>= 2` movements), align every constituent `LoadedAvailableBlockExerciseState` onto the canonical `0 until totalRounds` grid paired with `skippedFlags: SnapshotStateList<Boolean>`.
  3. Add `exerciseGroupIndicesById`/`Name` to `LastBlockSessionSummary` so prior-session pre-fill maps historical weights back into canonical `groupIndex - 1` slots (falling back to active-slot ordinal alignment when historical logs lacked `groupIndex`).
  4. Add `hasNoLoggedSets: Boolean` on `HydratedBlockExerciseSeed` so an exercise skipped across all sets of a logged complex (`sets = emptyList()`) re-hydrates as skipped across all canonical rounds rather than silently appearing active on Round 1.

## Decision 3: Shared Per-Set Weight Rules, Mixed Detection, & Mixed-Modality Isolation
- **Context**: Complexes may combine weighted barbell lifts (`MetricType.WEIGHT` with `REPS`), bodyweight gymnastics (`MetricType.REPS`), or erg conditioning (`CALORIES`/`TIME`/`DISTANCE`). Additionally, athletes can split weights per movement in Set `k` (`2A`).
- **Decision**:
  - `supportsSharedWeight` is `true` iff `activeMetric == ExerciseTargetMetric.REPS && libraryMetricType == MetricType.WEIGHT`.
  - `resolveSharedSetWeight(setIndex)` returns `ALL_SKIPPED`, `NON_WEIGHTED_ONLY`, `LINKED`, or `MIXED` (using 2dp `BigDecimal` comparison via `normalizeWeight2dp` + raw string equality while typing trailing decimals like `"45."`).
  - Re-linking from `MIXED` (either by typing directly into `Mixed` or tapping `"Re-link all to {majority} {unit}"`) captures a `ComplexSetWeightsSnapshot` and surfaces a 4-second `[Undo]` Snackbar in `SessionEditor.kt`.
  - `blockMaxSetWeight()` and `maxSetWeightFor(movementId)` skip any index where `skippedFlags[idx]` is true, eliminating false PR propagation across skipped rounds.

## Decision 4: Layout, Metric Switcher Relocation (`3A`), & Accessibility
- **Context**: Current `SimplifiedBlockLogCard` renders a 4-button `LazyRow` (`Reps | Cal | Time | Meters`) inside every sub-exercise (`~56dp` x 3) and 2-column tall `RepsWeightSetTile` cards (`~1,800dp` total height).
- **Decision**:
  - Hide the 4-button metric bar from default `CompactComplexLogCard`; inherit target/session/library metric per movement and move metric switching into the collapsible `By ex. ▸` view via a subtle `Unit: {Metric} ▾` dropdown menu.
  - Render `S1..SN` inside `widthIn(max = 640.dp)`, `360dp` horizontal budget (`32dp` index + `MovementStatusStrip` `weight(1f)` + `94dp` weight field + `40dp` `✕` inside `48dp` hit area), reflowing to 2 lines at `fontScale >= 1.4f` and displaying short exercise names inside pills when `availableWidthDp >= 520`.
  - Use `FocusRequester` per round for `ImeAction.Next` vertical traversal down the weight column and `BringIntoViewRequester` + `.imePadding()` on `SessionEditor`.

## Decision 5: MCP Server Shared Weight Shorthand (`shared_set_weights_kg`)
- **Context**: MCP clients (`log_complex_session`, `log_block_session`, `update_complex_session_log`, `update_block_session_log`, `log_daily_routine_session`) currently require repeating the same `weights` array per exercise in `exerciseLogs` / `exercise_logs`.
- **Decision**: Add optional `shared_set_weights_kg` (and optional `exercise_targets` on complex log/update) to `schemas.ts` using fresh Zod helper factories (preserving `0` `$ref`, `0` `exclusiveMinimum`, `45` tools). Expand in `store.ts` onto weighted constituent exercises skipping `0`-target rounds and emitting 1-based `groupIndex`, while preserving existing `groupIndex` masks in `updateBlockSessionLog` and allowing explicit per-exercise overrides.
