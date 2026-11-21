# Data Model: Compact Complex Session Logging (`SPEC-028`)

## 1. Existing Persistence Entities (Zero Schema Changes)
- **`SessionBlock` (Room v10)**: Represents one constituent exercise log inside a Complex (`routineId` / `subBlock` linking constituent blocks together). Uses `notes` `[log_metric=...]` tag for active non-default metrics.
- **`BlockSet` (Room v10)**:
  - `groupIndex: Int?`: 1-based canonical round index (`1..N`) persisted whenever any movement inside the Complex skips at least one round (e.g., `1-2-3-1-2-3` writes `groupIndex = 1..6` for Exercise 1, `[2,3,5,6]` for Exercise 2, `[3,6]` for Exercise 3).
  - `reps: Int`, `weight: Double?`, `metricValue: Double?`: Unchanged; skipped rounds simply omit `BlockSet` rows for that exercise.

## 2. Codec Projections (`AvailableBlockCodec.kt`)
- **`LastBlockSessionSummary`** (extended):
  - `exerciseGroupIndicesById: Map<Long, List<Int?>> = emptyMap()`
  - `exerciseGroupIndicesByName: Map<String, List<Int?>> = emptyMap()`
- **`HydratedBlockExerciseSeed`** (extended):
  - `hasNoLoggedSets: Boolean = false` (true when a constituent `SessionBlock` had 0 logged `BlockSet` rows, allowing Edit Session to distinguish an all-skipped exercise from a 1-set placeholder).

## 3. UI State Projections (`SimplifiedBlockLogComponents.kt`)
- **`LoadedAvailableBlockExerciseState`** (extended):
  - `libraryMetricType: MetricType = MetricType.WEIGHT`
  - `skippedFlags: SnapshotStateList<Boolean>` — aligned 1:1 with `weights`, `perSetReps`, `repsInputs`, `metricInputs`, `groupIndices` across canonical rounds `0 until complexSetCount`.
  - `supportsSharedWeight: Boolean` — `activeMetric == ExerciseTargetMetric.REPS && libraryMetricType == MetricType.WEIGHT`.
- **`SharedSetWeightStateKind`** (enum):
  - `LINKED`: All active weighted movements in Set `k` share one weight (or are all blank).
  - `MIXED`: Active weighted movements in Set `k` hold distinct weight values (e.g. `45.0 kg` vs `40.0 kg`, or one filled & one blank).
  - `ALL_SKIPPED`: Every movement in Set `k` has `skippedFlags[k] == true`.
  - `NON_WEIGHTED_ONLY`: At least one movement is active in Set `k`, and none have positive load or `supportsSharedWeight == true`.
- **`SharedSetWeightSummary`** (data class):
  - `kind: SharedSetWeightStateKind`
  - `uniformWeightText: String`
  - `minWeight: Double?`
  - `maxWeight: Double?`
  - `majorityWeightText: String`
  - `divergentExerciseIndices: Set<Int>`
- **Undo Snapshots**:
  - `ComplexSetWeightsSnapshot(setIndex: Int, previousWeightsByExercise: List<String>)`
  - `RemovedExerciseSetSlice(weight: String, perSetRep: Int, repsInput: String, metricInput: String, groupIndex: Int?, skipped: Boolean)`
  - `RemovedComplexSetSnapshot(setIndex: Int, perExerciseSlices: List<RemovedExerciseSetSlice>)`
