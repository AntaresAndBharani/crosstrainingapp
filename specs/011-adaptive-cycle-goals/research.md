# Phase 0 Research: Adaptive Metric Cycle Goals

## 1. Persistence & Schema Backward Compatibility (`CycleGoal`)

- **Decision**: Preserve the Room `cycle_goals` table schema at Database Version 10 (`id`, `cycleId`, `exerciseId`, `targetReps`, `startWeight`, `targetWeight`, `notes`) and encode adaptive metric metadata (`goalMetric` and `timeWindowMinutes`) inside `CycleGoal` using a deterministic metadata header in `notes` (only when `goalMetric != MetricType.WEIGHT` or `timeWindowMinutes != null`), exposed via `@Ignore` properties (`goalMetric`, `timeWindowMinutes`, `cleanNotes`, `formattedBadge`, `unitLabel`) and a secondary `@Ignore` constructor.
- **Rationale**:
  - Satisfies Constitution Principle V (*"Database schema modifications require explicit migrations; features that can be derived from existing entities MUST avoid unnecessary schema changes."*).
  - Preserves 100% compatibility with `RoomSchemaExportTest` (which asserts `version = 10` on `AppDatabase`), `UserCloudSyncManager` (which syncs `id`, `cycleId`, `exerciseId`, `targetReps`, `startWeight`, `targetWeight`, `notes` to/from Cloud Firestore), and all existing unit/integration tests that construct `CycleGoal`.
  - Standard `Weight (kg)` goals (`MetricType.WEIGHT`) produce the exact same `notes` string (`""` or user notes) and `targetReps` integer (`1`, `3`, `5`, `10`, etc.) as before.
- **Alternatives Considered**:
  - Adding new SQLite columns `goalMetric` and `timeWindowMinutes` via `MIGRATION_10_11`: Rejected because it forces a schema version bump that conflicts with existing static schema version assertions and requires updating Firestore cloud sync payload schemas.

## 2. Default Goal Metric Inference from `Exercise`

- **Decision**: Provide a pure helper `CycleGoalDefaults.defaultGoalMetricFor(exercise: Exercise?): MetricType` that maps:
  - `exercise == null` → `MetricType.WEIGHT`
  - `exercise.metricType == MetricType.CALORIES` → `MetricType.CALORIES`
  - `exercise.metricType == MetricType.REPS` or `exercise.category == ExerciseCategory.GYMNASTICS` → `MetricType.REPS`
  - Otherwise (`MetricType.WEIGHT`, `MetricType.DISTANCE`, `MetricType.TIME`) → `MetricType.WEIGHT`
- **Rationale**: In `SeedData.kt`, gymnastics movements (`Pull-ups`, `Push-ups`, `Air Squat`, `Toes to Bar`, `Box Jumps`) are seeded with `ExerciseCategory.GYMNASTICS` and `MetricType.WEIGHT`. Checking `exercise.metricType == MetricType.REPS || exercise.category == ExerciseCategory.GYMNASTICS` ensures gymnastics movements automatically default to the `Reps` pill while still allowing the user to manually switch to `Weight (kg)` or `Calories (cal)`.
- **Alternatives Considered**:
  - Relying solely on `exercise.metricType`: Rejected because seeded gymnastics exercises have `metricType = MetricType.WEIGHT` in `SeedData.kt` for weighted-vest compatibility, which would prevent them from defaulting to `Reps` as clarified in Phase 1.

## 3. Adaptive Scale Calculations (`Weight`, `Calories`, `Reps`)

- **Decision**:
  - When `goalMetric == MetricType.WEIGHT`:
    - Presets: `+2.5%` (`2.5`), `+5%` (`5.0`), `+10%` (`10.0`), `0%` (`0.0`).
    - Formula: `FatLossAnalytics.scaleRepMax(startVal, percentage)` (non-compounding percentage of `startVal`, rounded `HALF_UP` to nearest `0.5`).
  - When `goalMetric == MetricType.CALORIES`:
    - Presets: `+5 cal` (`5.0`), `+10 cal` (`10.0`), `+20 cal` (`20.0`), `0 cal` (`0.0`).
    - Formula: `startVal + modifier` (non-compounding flat addition).
  - When `goalMetric == MetricType.REPS`:
    - Presets: `+2 reps` (`2.0`), `+5 reps` (`5.0`), `+10 reps` (`10.0`), `0 reps` (`0.0`).
    - Formula: `startVal + modifier` (non-compounding flat addition).
- **Rationale**: Directly implements the shared UI design specification and Phase 1 clarifications while keeping all arithmetic pure and deterministic (Constitution Principle II).
- **Alternatives Considered**:
  - Compounding increments on repeated chip taps: Rejected because `GoalDraftState` must remain non-compounding (`target = scale(start, modifier)`).

## 4. Historical Baseline Auto-Fill for `Weight`, `Calories`, and `Reps`

- **Decision**:
  - For `MetricType.WEIGHT`: Query `onFetchBestRm(exerciseId, reps)` (backed by `RepMaxDao.bestWeight(exerciseId, reps)`).
  - For `MetricType.CALORIES` and `MetricType.REPS`: Query `onFetchBestMetricValue(exerciseId, goalMetric)` which inspects all non-warmup, non-failed `BlockSet` entries across sessions for `SessionBlock.mainExerciseId == exerciseId` (returning max `metricValue ?: weight` for `CALORIES`, and max `metricValue ?: reps.toDouble()` for `REPS`).
- **Rationale**: Matches the Phase 1 requirement to auto-fill `Start` for `Calories (cal)` and `Reps` from the all-time best logged session set regardless of time window.
- **Alternatives Considered**:
  - Filtering session blocks by parsed duration strings: Rejected per user clarification in Phase 1 to use all-time best logged session set regardless of time window.
