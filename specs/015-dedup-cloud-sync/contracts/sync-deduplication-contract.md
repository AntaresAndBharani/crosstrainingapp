# Contract: Idempotent Cloud Synchronization & Database Deduplication

## 1. Repository Deduplication & Upsert Contract (`com.fractanomics.crosstraining.data.Repository`)

### `suspend fun cleanupDuplicateData()`
Executes atomically inside `withDatabaseTransaction` in foreign-key-safe order:
1. `cleanupDuplicateExercisesInternal()`:
   - Groups exercises by `name.trim().lowercase()`.
   - Keeps canonical exercise (lowest `id`).
   - Remaps `cycle_goals.exerciseId`, `rep_maxes.exerciseId`, `routines.mainExerciseId`, `session_blocks.mainExerciseId`, `routine_blocks.exerciseIdsCsv`, and `session_blocks.exerciseIdsCsv` from duplicate IDs to canonical ID.
   - Deletes duplicate `Exercise` rows.
2. `cleanupDuplicateCyclesInternal()`:
   - Groups cycles by `(name.trim().lowercase(), startDate, endDate, type)`.
   - Keeps canonical cycle (preferring `isActive`, then lowest `id`).
   - Remaps `cycle_goals.cycleId`, `sessions.cycleId`, and `rep_maxes.cycleId` to canonical cycle ID.
   - Deletes duplicate `Cycle` rows.
3. `cleanupDuplicateRoutinesInternal()`:
   - Groups routines by `name.trim().lowercase()`.
   - Keeps canonical routine (preferring non-empty blocks, then lowest `id`).
   - Remaps `session_blocks.routineId` from duplicate routine IDs to canonical routine ID.
   - Deletes duplicate `RoutineBlock` and `Routine` rows.
4. `cleanupDuplicateCycleGoalsInternal()`:
   - Groups cycle goals by `(cycleId, exerciseId, goalMetric, targetReps)`.
   - Keeps canonical goal (lowest `id`, preserving non-zero `targetWeight`/`startWeight` and non-blank `notes` from duplicates if primary lacked them).
   - Deletes all redundant `CycleGoal` rows.
5. `cleanupDuplicateSessionsInternal()`:
   - Groups sessions by `(date, title.trim().lowercase(), blocksSignature)`.
   - Keeps canonical session (lowest `id`).
   - Remaps `rep_maxes.sessionId` from duplicate session IDs to canonical session ID.
   - Deletes duplicate `Session` rows and their blocks/sets.
6. `cleanupDuplicateRepMaxesInternal()`:
   - Groups rep maxes by `(exerciseId, reps, BigDecimal.valueOf(weight).setScale(2, HALF_UP), date)`.
   - Keeps canonical rep max (lowest `id`).
   - Deletes duplicate `RepMax` rows.

### Upsert Guards on Write Methods
- `saveCycleGoal(goal: CycleGoal): Long`:
  - Checks existing goals for `(goal.cycleId, goal.exerciseId, goal.goalMetric, goal.targetReps)`.
  - If `goal.id == 0L` and a matching goal exists, updates the existing row in place and returns its `id`.
- `saveCycleWithGoals(cycle: Cycle, goals: List<CycleGoal>): Long`:
  - Deduplicates `goals` by `(it.exerciseId, it.goalMetric, it.targetReps)` before inserting.
- `recordRepMax(exerciseId: Long, reps: Int, weight: Double, date: LocalDate, cycleId: Long?): Long`:
  - Checks if an entry with matching `(exerciseId, reps, weight, date)` already exists; if so, returns its `id` without inserting a duplicate.

---

## 2. Cloud Sync Contract (`com.fractanomics.crosstraining.data.firebase.UserCloudSyncManager`)

### Concurrency Serialization
- All cloud sync mutations (`uploadUserData`, `downloadUserData`, `recoverAllCloudRoutines`) are guarded by a shared `Mutex` (`syncMutex`) so overlapping invocations (e.g., sign-in download + immediate manual `triggerCloudSync`) execute sequentially without race conditions.

### `downloadUserData(repo: Repository, importUnmarkedRemoteRecords: Boolean = true): Result<Unit>`
1. Runs `repo.cleanupDuplicateData()` before merging remote records.
2. Builds `remoteExerciseIdToLocalId` for all remote exercises (matching existing local exercises case-insensitively even when `shouldImport` is `false`).
3. Builds `localCycles` lookup to resolve remote `cycleId` / `cycleName` to a valid local `cycleId` (matching `cycleName` case-insensitively, or `cycleId` if present locally, or active/first local cycle).
4. Deduplicates each remote collection list (`exList`, `routList`, `goalsList`, `sessList`, `rmList`, `weightList`, `dailyLogsList`) by natural key (preferring entries with higher `updatedAtMillis`) before importing.
5. Maintains mutable local tracking sets/lists during import so duplicate items within the same payload or already in the local DB are updated/skipped in place.
6. Runs `repo.cleanupDuplicateData()` after import completes.

### `uploadUserData(repo: Repository): Result<Unit>`
1. Runs `repo.cleanupDuplicateData()` as the synchronous pre-flight step.
2. Deduplicates outgoing snapshots (`exercises`, `routines`, `sessions`, `cycle_goals`, `rep_maxes`, `weight_entries`, `daily_logs`) by natural key.
3. Enriches `cycle_goals`, `rep_maxes`, `routines`, and `sessions` payloads with resolved `exerciseName` (and `cycleName` where applicable) alongside numeric IDs so cross-device restores never suffer from numeric ID drift.
4. Writes the clean, deduplicated lists to Firestore, healing any previously polluted cloud documents.
