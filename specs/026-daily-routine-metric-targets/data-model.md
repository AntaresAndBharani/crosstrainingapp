# Data Model: Optional Daily Routine Multi-Metric Targets & Metric-Aware Workout Logging (`SPEC-026`)

## Database Impact
- **Room Version**: Remains at **v10** (`0` schema migrations).
- **Firestore Schema / Security Rules**: Unchanged (`routines`, `exercises`, `sessions`, `rep_maxes`, `cycles`, `cycle_goals`, `weight_entries`, `daily_logs`).

## Domain Models & Persistence Mappings

### 1. `DailyRoutineItem` (`DailyRoutineCodec.kt`)
- **`DailyRoutineItem.BlockRef`**:
  - `blockId: Long`
  - `blockTitle: String`
  - `resolvedBlock: AvailableBlock? = null`
  - `targetSetsCount: Int? = null` *(nullable; null when unset)*
  - `exerciseTargets: Map<Long, RoutineExerciseTargetSpec> = emptyMap()` *(per-exercise ID target overrides scoped to this Daily Routine)*
  - `exerciseTargetsByName: Map<String, RoutineExerciseTargetSpec> = emptyMap()` *(fallback keyed by trimmed lowercase movement name for sync/cross-device resolution)*
  - Derived `val hasConfiguredTargets: Boolean` -> `targetSetsCount != null && (exerciseTargets.isNotEmpty() || exerciseTargetsByName.isNotEmpty())`
- **`DailyRoutineItem.StandaloneExercise`**:
  - `exerciseId: Long`
  - `exerciseName: String`
  - `note: String = ""`
  - `defaultTrackedPrs: List<TrackedPrSlot> = emptyList()`
  - `resolvedExercise: Exercise? = null`
  - `targetSetsCount: Int? = null` *(nullable; null when unset)*
  - `targetSpec: RoutineExerciseTargetSpec? = null` *(nullable; null when unset)*
  - Derived `val hasConfiguredTarget: Boolean` -> `targetSetsCount != null && targetSpec != null`

### 2. Wire Encoding on `RoutineBlock` (Zero Migration)
- **When targets are UNSET**:
  - `RoutineBlock.setsCount = 1`
  - `RoutineBlock.targetRepsScheme = ""`
  - `RoutineBlock.notes`: `[dr_item=BLOCK:...]` or `[dr_item=EXERCISE:...]` (+ `[ab_prs=...]` + clean note), NO `[rb_v2=...]` line.
- **When targets are CONFIGURED**:
  - `RoutineBlock.setsCount = targetSetsCount.coerceAtLeast(1)`
  - `RoutineBlock.targetRepsScheme = RoutineTargetCodec.formatBlockTargetSummary(slots, setsCount)`
  - `RoutineBlock.exerciseIdsCsv`: ordered comma-separated exercise IDs matching slot order
  - `RoutineBlock.notes`:
    ```
    [rb_v2=REPS:FIXED:8:8.8.8.8;CALORIES:PER_SET:15:15.15.12.0]
    [dr_item=BLOCK:id=10:title=...]   (or [dr_item=EXERCISE:...])
    [ab_prs=...]                     (optional, on StandaloneExercise)
    Clean human note                 (optional)
    ```
  - **Validation constraint**: When targets are enabled on an item, every configured `RoutineExerciseTargetSpec` MUST have at least one positive set value (`!spec.isAllZeroInvalid`), or saving is blocked.

### 3. Session Logging State & Persistence (`SimplifiedBlockLogComponents.kt`, `SessionDraft.kt`, `AppViewModel.kt`)
- **`BlockDraft.notes: String = ""`** -> Persisted into `SessionBlock.notes` inside `AppViewModel.buildBlockInserts`.
- **`[log_metric=...]` Marker (`RoutineTargetCodec`)**:
  - Stored in `SessionBlock.notes`: `[log_metric=REPS]`, `[log_metric=CALORIES]`, `[log_metric=TIME_SECONDS]`, or `[log_metric=METERS]`.
  - Stripped cleanly from visible notes by `RoutineTargetCodec.extractCleanNotes`.
- **`LoadedAvailableBlockExerciseState`**:
  - `activeMetric: ExerciseTargetMetric` (`REPS`, `CALORIES`, `TIME_SECONDS`, `METERS`)
  - `weights: SnapshotStateList<String>`
  - `repsInputs: SnapshotStateList<String>` *(synchronized with `perSetReps: SnapshotStateList<Int>`)*
  - `metricInputs: SnapshotStateList<String>`
  - `groupIndices: SnapshotStateList<Int?>` *(1-based round indices preserved when `0 (Skip)` rounds are omitted)*
  - `cachedInputsByMetric: MutableMap<ExerciseTargetMetric, CachedExerciseMetricInputs>` *(preserves user edits when toggling metric chips)*
  - `sourceRoutineItem: DailyRoutineItem?` on `LoadedAvailableBlockState` *(retains routine item targets during `[ ] Also update routine template` sync)*
- **`BlockSet` Persistence Rules**:
  - **`activeMetric == ExerciseTargetMetric.REPS`**:
    - Set saved if `parsedWeight > 0.0` OR `parsedReps != null`.
    - Emits `SetDraft(reps = parsedReps?.coerceAtLeast(1) ?: 1, weight = parsedWeight, metricValue = null, groupIndex = groupIdx)`.
    - PR candidate evaluation uses `weights` only (bodyweight rep-only sets with null weight never create `0kg` or bogus PRs).
  - **`activeMetric in {CALORIES, TIME_SECONDS, METERS}`**:
    - Set saved if `parsedMetricValue > 0.0`.
    - Emits `SetDraft(reps = 1, weight = null, metricValue = parsedMetricValue, groupIndex = groupIdx)`.

### 4. Hydration & Analytics Models (`AvailableBlockCodec.kt`, `ProgressAnalytics.kt`, `DailyRoutineCodec.kt`)
- **`LastExerciseSessionSummary`**: adds `reps: List<Int>`, `metricValues: List<Double?>`, `groupIndices: List<Int?>`, `loggedMetric: ExerciseTargetMetric?`.
- **`LastBlockSessionSummary`**: adds `exerciseRepsById`, `exerciseRepsByName`, `exerciseMetricValuesById`, `exerciseMetricValuesByName`, `exerciseLoggedMetricById`, `exerciseLoggedMetricByName`.
- **`HydratedBlockExerciseSeed`**: adds `metricValues: List<Double?>`, `groupIndices: List<Int?>`, `loggedMetric: ExerciseTargetMetric?`, `rawNotes: String`.
- **`ConstituentProgressSummary`**: populates `unit` dynamically (`weightUnit`, `"reps"`, `"cal"`, `"s"`, or `"m"`), and `BlockPerformance.workingSetValues` extracts `weight ?: metricValue` with fallback to working `reps` for unweighted rep-only exercises.
