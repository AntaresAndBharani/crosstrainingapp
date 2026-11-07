# Phase 0 Research: Idempotent Cloud Synchronization & Database Deduplication

## Decision 1: Root Cause Analysis of Sync Duplication (`2 -> 4 -> 8 -> 16 -> 32 -> 64`)

- **Decision**: Address all three layers where duplication occurred or persisted:
  1. **Historical Local & Cloud Pollution**: Prior sync cycles inserted a fresh `CycleGoal(id = 0, ...)` on every download pass and uploaded the resulting duplicate rows back to Cloud Firestore (`environments/{env}/users/{uid}/data/cycle_goals`), causing exponential doubling (`2 * 2^5 = 64`). Existing databases and Firestore documents still hold these 64 duplicate rows because `Repository` only had `cleanupDuplicateRoutines()` and lacked deduplication for `cycle_goals`, `exercises`, `sessions`, `rep_maxes`, and `cycles`.
  2. **Sync Importer & Exporter Gaps (`UserCloudSyncManager`)**:
     - `downloadUserData` did not deduplicate incoming remote collection arrays (`goalsList`, `sessList`, `rmList`, `routList`, `exList`) before iterating, and `existingSessions` / `existingRms` were immutable snapshots not updated during iteration.
     - `downloadUserData` matched `CycleGoal` only on `(cycleId, exerciseId, targetReps)` without `goalMetric` or local cycle fallback resolution when remote `cycleId` did not exist locally, and did not clean up extra duplicate rows already present in `existingGoals`.
     - `uploadUserData` did not include `exerciseName` / `cycleName` metadata on `cycle_goals`, `rep_maxes`, `routines`, and `sessions`, nor did it deduplicate outgoing payloads before writing to Firestore.
     - Concurrent sync invocations (such as `logInWithGoogle` calling `downloadUserData` while `ProfileScreen` simultaneously calls `triggerCloudSync`) were not serialized by a `Mutex`.
  3. **Repository Persistence Boundary (`Repository`)**:
     - `saveCycleGoal(goal)` inserted a new row whenever `goal.id == 0L` instead of checking for an existing goal matching `(cycleId, exerciseId, goalMetric, targetReps)`.
     - `saveCycleWithGoals(cycle, goals)` did not deduplicate `goals` by `(exerciseId, goalMetric, targetReps)` before inserting.
     - `recordRepMax(...)` did not check if an identical `(exerciseId, reps, weight, date)` record already existed.
     - `getOrCreateExercise(name, ...)` matched via `exerciseDao.byName(trimmed)`, which needs case-insensitive and whitespace-trimmed lookup (`LOWER(TRIM(name)) = LOWER(TRIM(:name))`).

- **Rationale**: Fixing only `downloadUserData` leaves existing 64 duplicate rows in both Room SQLite and Cloud Firestore. Combining automatic local deduplication (`cleanupDuplicateData`), repository upsert guards, incoming/outgoing sync deduplication, cross-device name resolution, and a sync `Mutex` eliminates existing duplicates and guarantees zero future duplicates.
- **Alternatives Considered**:
  - *Manual "Clean Database" button in Settings*: Rejected because the user explicitly requested automatic cleanup of the database to have only the real data with no duplicates and automatic duplicate prevention on every click of the synchronize button.
  - *Destructive Room schema migration with SQLite composite UNIQUE constraints*: Rejected as sole mechanism because `CycleGoal.goalMetric` is encoded in `notes` metadata and foreign key remapping across tables (`exercises` -> `cycle_goals`/`sessions`/`routines`/`rep_maxes`) is cleaner, safer, and non-destructive when executed in a Kotlin `withDatabaseTransaction` block without incrementing the Room schema version unnecessarily (per Constitution Principle V).

## Decision 2: Natural Identity Keys & Canonical Row Selection

- **Decision**: Define deterministic natural identity keys and canonical tie-breaking rules for every entity type:
  - **Exercise**: `name.trim().lowercase()`
    - *Canonical selection*: Lowest `id`. Remap all references (`cycle_goals.exerciseId`, `rep_maxes.exerciseId`, `routines.mainExerciseId`, `session_blocks.mainExerciseId`, `routine_blocks.exerciseIdsCsv`, `session_blocks.exerciseIdsCsv`) before deleting duplicate rows.
  - **Routine**: `name.trim().lowercase()`
    - *Canonical selection*: Prefer routine with non-empty `blocks` (and lowest `id` on tie). Remap `session_blocks.routineId` before deleting duplicate routines and their blocks.
  - **Cycle**: `(name.trim().lowercase(), startDate, endDate, type)`
    - *Canonical selection*: Prefer `isActive == true`, then non-null `targetWeightKg`/`startingWeightKg`, then lowest `id`. Remap `cycle_goals.cycleId`, `sessions.cycleId`, and `rep_maxes.cycleId` before deleting duplicate cycles.
  - **CycleGoal**: `(cycleId, exerciseId, goalMetric, targetReps)`
    - *Canonical selection*: Keep the first goal with lowest `id` (preserving card chip order) while merging any non-zero `targetWeight`/`startWeight` or non-blank `notes` from later duplicates if the first had empty values; delete all other duplicates in the group.
  - **Session**: `(cycleId, date, title.trim().lowercase())` with identical block/set content signature (or during sync upsert by `(date, title.trim().lowercase())`)
    - *Canonical selection*: Prefer session with more blocks/sets or completed sets, then lowest `id`. Remap `rep_maxes.sessionId` before deleting duplicate sessions and their blocks/sets.
  - **RepMax**: `(exerciseId, reps, BigDecimal(weight).setScale(2, HALF_UP), date)`
    - *Canonical selection*: Prefer row with non-null `sessionId`/`cycleId`, then lowest `id`.
  - **WeightEntry & DailyLog**: `date` (`LocalDate` primary key), merged via tombstone-aware Last-Write-Wins (`max(updatedAtMillis, deletedAtMillis ?: 0L)`).

- **Rationale**: Preserves 100% of legitimate user training data and ordering while deterministically collapsing duplicate copies.
- **Alternatives Considered**: Keeping highest `id` for `CycleGoal`: rejected because keeping the lowest `id` preserves the original creation order of goals on the Cycle card (`Front Squat (3RM)` followed by `Snatch (2RM)`).
