# Phase 1 Data Model: Idempotent Cloud Synchronization & Database Deduplication

## Entities & Natural Identity Keys

### 1. `Exercise` (`exercises`)
- **Primary Key**: `id: Long` (auto-generated)
- **Natural Identity Key**: `normalizedName = name.trim().lowercase()`
- **Referencing Entities**:
  - `CycleGoal.exerciseId` (FK CASCADE)
  - `RepMax.exerciseId` (FK CASCADE)
  - `Routine.mainExerciseId` (FK SET_NULL)
  - `SessionBlock.mainExerciseId`
  - `RoutineBlock.exerciseIdsCsv` (comma-separated IDs)
  - `SessionBlock.exerciseIdsCsv` (comma-separated IDs)
- **Deduplication & Upsert Rule**:
  - `ExerciseDao.byName` matches `LOWER(TRIM(name)) = LOWER(TRIM(:name))`.
  - During `cleanupDuplicateData()`, exercises sharing `normalizedName` are merged into the canonical row (lowest `id`), all foreign keys and CSV ID lists are rewritten to the canonical `id`, and redundant rows are deleted.

### 2. `Cycle` (`cycles`)
- **Primary Key**: `id: Long` (auto-generated)
- **Natural Identity Key**: `(name.trim().lowercase(), startDate, endDate, type)`
- **Referencing Entities**:
  - `CycleGoal.cycleId` (FK CASCADE)
  - `Session.cycleId` (FK CASCADE)
  - `RepMax.cycleId` (FK SET_NULL)
- **Deduplication & Sync Resolution Rule**:
  - Exact duplicate cycles sharing `(name.trim().lowercase(), startDate, endDate, type)` are merged into the canonical cycle (preferring `isActive == true`, then lowest `id`), remapping child `cycle_goals`, `sessions`, and `rep_maxes` before deleting redundant rows.
  - During cloud sync download, remote `cycleId` / `cycleName` resolves to: (1) local cycle matching `cycleName` case-insensitively if provided, (2) local cycle matching `cycleId`, or (3) active local cycle (or first local cycle / provisioned default cycle).

### 3. `CycleGoal` (`cycle_goals`)
- **Primary Key**: `id: Long` (auto-generated)
- **Natural Identity Key**: `CycleGoalNaturalKey(cycleId: Long, exerciseId: Long, goalMetric: MetricType, targetReps: Int)`
  - Note: For `MetricType.WEIGHT`, `targetReps` is the rep-max target (`>= 1`). For `MetricType.CALORIES` and `MetricType.REPS`, `targetReps` stores `timeWindowMinutes ?: 0` (`1`, `3`, `5`, or `0` for `Open`) and `goalMetric` is parsed from `notes` via `CycleGoal.parseMetadata(notes)`.
- **Deduplication & Upsert Rule**:
  - `Repository.saveCycleGoal(goal)`: When `goal.id == 0L` (or when upserting), looks up any existing goal in the same `cycleId` with matching `(exerciseId, goalMetric, targetReps)`. If found, updates that row in place (and deletes any extra duplicates for that key) instead of inserting a duplicate row.
  - `Repository.saveCycleWithGoals(cycle, goals)`: Deduplicates `goals` by `(exerciseId, goalMetric, targetReps)` before replacing the cycle's goals.
  - `Repository.cleanupDuplicateCycleGoals()`: Groups all `cycle_goals` by `(cycleId, exerciseId, goalMetric, targetReps)`. For any group with `size > 1`, keeps the canonical row (lowest `id`, enriched with latest non-zero `targetWeight`/`startWeight` and non-blank `notes`) and deletes all `size - 1` duplicate rows.

### 4. `Routine` (`routines`) & `RoutineBlock` (`routine_blocks`)
- **Primary Key**: `id: Long` (auto-generated)
- **Natural Identity Key**: `normalizedName = name.trim().lowercase()`
- **Referencing Entities**:
  - `RoutineBlock.routineId` (FK CASCADE)
  - `SessionBlock.routineId`
- **Deduplication & Upsert Rule**:
  - `RoutineDao.byName` matches `LOWER(TRIM(name)) = LOWER(TRIM(:name))`.
  - `Repository.cleanupDuplicateRoutines()` groups by `name.trim().lowercase()`, selects the primary routine (preferring one with blocks, then lowest `id`), remaps any `SessionBlock.routineId` pointing to duplicate IDs, and deletes duplicate routines and their blocks.

### 5. `Session` (`sessions`), `SessionBlock` (`session_blocks`), & `BlockSet` (`block_sets`)
- **Primary Key**: `id: Long` (auto-generated)
- **Sync Identity Key**: `(date: LocalDate, normalizedTitle = title.trim().lowercase())`
- **Local Deduplication Key**: `(cycleId, date, title.trim().lowercase(), blockSignature)` where `blockSignature` captures ordered block names/kinds/schemes/sets so identical duplicate sessions are collapsed while genuinely distinct multiple sessions on the same day with different content are preserved.
- **Deduplication & Upsert Rule**:
  - During `downloadUserData`, incoming `sessList` is deduplicated by `(date, title.trim().lowercase())` (keeping the latest `updatedAtMillis` entry), and `existingSessions` is maintained mutably so repeated entries in the same batch or across syncs update in place rather than inserting duplicates.
  - During `cleanupDuplicateSessions()`, duplicate sessions sharing `(date, title.trim().lowercase())` and identical block/set signatures are collapsed into the canonical session (lowest `id`), remapping any `RepMax.sessionId` references before deleting duplicates.

### 6. `RepMax` (`rep_maxes`)
- **Primary Key**: `id: Long` (auto-generated)
- **Natural Identity Key**: `RepMaxNaturalKey(exerciseId: Long, reps: Int, weightRounded: BigDecimal, date: LocalDate)`
- **Deduplication & Upsert Rule**:
  - `Repository.recordRepMax` / `addRepMax`: Checks if a `RepMax` with the same `(exerciseId, reps, weight, date)` already exists; if so, returns the existing `id` instead of inserting a duplicate.
  - `Repository.cleanupDuplicateRepMaxes()`: Groups all `rep_maxes` by `(exerciseId, reps, BigDecimal.valueOf(weight).setScale(2, RoundingMode.HALF_UP), date)`, keeps the canonical row (lowest `id`), and deletes duplicates.

### 7. `WeightEntry` (`weight_entries`) & `DailyLog` (`daily_logs`)
- **Primary Key**: `date: LocalDate`
- **Deduplication & Sync Rule**:
  - Deduplicate remote list by `date` keeping the entry with highest `max(updatedAtMillis, deletedAtMillis ?: 0L)` before LWW merge with local rows.
