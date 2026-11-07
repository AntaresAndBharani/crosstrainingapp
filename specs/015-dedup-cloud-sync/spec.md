# Feature Specification: Idempotent Cloud Synchronization & Database Deduplication

**Feature Branch**: `feat/015-dedup-cloud-sync`

**Created**: 2026-11-07

**Status**: Approved

**Input**: User description: "Everytime I synchronize the data it looks the data is duplicated in the database. I want to fix the issue and clean the database to have only the real data with no duplicates. Everytime I click on synchronize button, the app must check if that data is already in the database and never duplicate entries."

## User Scenarios & Testing *(mandatory)*

### User Story 1 - Automatic Cleanup of Existing Duplicate Records (Priority: P1)

As an athlete or coach whose training cycle goals, routines, exercises, workout sessions, or personal records were previously duplicated in the local database and cloud backup (e.g., 64 duplicate target goals appearing on the active `Recomposition` cycle card instead of the 2 real unique goals), I want the app to automatically deduplicate and clean my database on launch and during synchronization so that only the real, unique records remain without losing any legitimate training history.

**Why this priority**: The user's active database and cloud backup are already polluted with repeated duplicate entries (such as 32 copies each of `Front Squat (3RM): 50 -> 65kg` and `Snatch (2RM): 40kg`). Cleaning existing duplicates immediately restores UI usability and accurate goal counts (`Target Goals (2)` instead of `Target Goals (64)`).

**Independent Test**: Seed a database containing 32 duplicate copies of two cycle goals (`Front Squat 3RM` and `Snatch 2RM`, 64 total), duplicate exercises/routines/sessions/rep-maxes, run the database cleanup routine or launch the view model, and verify that exactly 2 unique cycle goals and 1 canonical copy of each unique entity remain with all relational references preserved.

**Acceptance Scenarios**:

1. **Given** a local database with 64 target goals consisting of 32 identical copies of `Front Squat (3RM): 50 -> 65kg` and 32 identical copies of `Snatch (2RM): 40kg` under the active cycle, **When** the application initializes or executes synchronization, **Then** the duplicate rows are removed and the cycle displays `Target Goals (2):` with one `Front Squat (3RM): 50 -> 65kg` and one `Snatch (2RM): 40kg`.
2. **Given** duplicate exercises (differing only by casing or whitespace), duplicate routines (matching normalized name), duplicate workout sessions (matching cycle, date, normalized title, and block structure), or duplicate rep-max entries (matching exercise, rep count, weight, and date), **When** database cleanup runs, **Then** a single canonical record is retained for each unique identity and all child/foreign references are remapped to the canonical record before duplicates are deleted.
3. **Given** cloud backup collections containing historical duplicate entries, **When** the user triggers synchronization, **Then** the cleaned and deduplicated dataset overwrites the polluted cloud lists so that subsequent restores or syncs on any device receive only deduplicated data.

---

### User Story 2 - Idempotent Manual & Automatic Synchronization ("Sync Now") (Priority: P1)

As an athlete or coach tapping "Sync Now" on the Profile & Settings screen (or signing in to my account), I want every synchronization pass to check whether each incoming or outgoing record already exists in the database and update/skip it in place so that tapping "Sync Now" any number of times never duplicates entries.

**Why this priority**: Preventing duplication on every click of "Sync Now" and during sign-in/restore flows directly addresses the root cause of the exponential duplication bug (`2 -> 4 -> 8 -> 16 -> 32 -> 64`).

**Independent Test**: Populate a cycle with target goals, exercises, routines, logged workout sessions, rep maxes, weight entries, and daily logs; trigger "Sync Now" 5 consecutive times (and simulate sign-in restore flows); verify that record counts across all collections remain unchanged after every sync pass.

**Acceptance Scenarios**:

1. **Given** a signed-in user with 2 cycle goals, 10 exercises, 4 routines, 5 logged sessions, and 6 rep maxes, **When** the user taps "Sync Now" once or 10 consecutive times, **Then** the total count of cycle goals remains 2, exercises remains 10, routines remains 4, sessions remains 5, and rep maxes remains 6.
2. **Given** a remote cloud payload containing multiple duplicate entries within the same collection list (e.g., 64 duplicated cycle goals or repeated sessions/rep-maxes in the cloud document), **When** `downloadUserData` imports the remote payload, **Then** incoming items are deduplicated by their natural identity key and matched against existing local records so at most 1 record per natural identity exists locally after import.
3. **Given** a sign-in flow (such as Google Sign-In) that triggers both an initial account download and an immediate follow-up cloud sync, **When** both sync requests overlap in time, **Then** synchronization execution is serialized so concurrent reads and writes never race to insert duplicate records.

---

### User Story 3 - Repository-Level Upsert Guards on Entity Persistence (Priority: P2)

As an athlete or coach saving cycle goals, routines, exercises, or personal records through the app or sync pipeline, I want the persistence layer itself to enforce natural-key uniqueness so that saving an entry whose natural key already exists updates the existing record rather than creating a duplicate row.

**Why this priority**: Defense-in-depth at the repository boundary guarantees that even if a caller passes a new entity (`id = 0`) for an already-existing cycle goal, exercise, routine, or rep-max, the persistence layer upserts in place instead of appending a duplicate row.

**Independent Test**: Call repository save methods (`saveCycleGoal`, `saveCycleWithGoals`, `getOrCreateExercise`, `saveRoutineWithBlocks`, `recordRepMax`) multiple times with `id = 0` and identical natural keys, and verify only a single row exists in each table.

**Acceptance Scenarios**:

1. **Given** an existing cycle goal for `(cycleId, exerciseId, targetReps, goalMetric)`, **When** `saveCycleGoal` is called with `id = 0` for the same cycle, movement, rep target/time window, and metric, **Then** the existing goal row is updated in place and no second row is created.
2. **Given** a list of cycle goals passed to `saveCycleWithGoals` that contains duplicate entries for the same `(exerciseId, targetReps, goalMetric)`, **When** `saveCycleWithGoals` executes, **Then** only one canonical goal per unique `(exerciseId, targetReps, goalMetric)` is persisted for that cycle.
3. **Given** an existing rep-max entry for `(exerciseId, reps, weight, date)`, **When** `recordRepMax` is called with the same exercise, reps, weight, and date, **Then** the existing record ID is returned without inserting a duplicate row.

---

### Edge Cases

- **Remote Cycle ID Drift**: Cycle entities are local-only, so a cycle goal or workout session downloaded from cloud backup or an external assistant may reference a `cycleId` that does not match the local active cycle ID (or may carry a `cycleName`). The sync importer must resolve the target local cycle (by matching `cycleId`, or matching `cycleName` case-insensitively, or falling back to the active/primary local cycle) before checking for existing goals/sessions so that foreign-key constraints are satisfied and natural-key matching accurately finds the local record.
- **Remote Exercise ID Drift**: Exercises created on another device or seeded in a different order may have different numeric IDs in the cloud backup than locally. Sync upload must include exercise names alongside IDs, and sync download must resolve exercises by normalized name (and remote-to-local ID map) before matching cycle goals, sessions, routines, and rep maxes.
- **Adaptive Cycle Goal Metrics**: Cycle goals support `WEIGHT`, `CALORIES`, and `REPS` metrics (encoded in `notes` metadata alongside time windows). Natural-key deduplication for cycle goals must include `goalMetric` alongside `(cycleId, exerciseId, targetReps)` so that a `5RM Weight` goal and a `5 min Calories` goal on the same movement are recognized as distinct goals, while identical `(cycleId, exerciseId, goalMetric, targetReps)` goals are deduplicated.
- **Concurrent Sync Invocations**: Rapid or overlapping sync triggers (e.g., sign-in completion callback invoking `downloadUserData` while UI simultaneously invokes `triggerCloudSync`) must be serialized so two coroutines never read an empty/partial snapshot and double-insert records.
- **Preserving Richer Data During Cleanup**: When collapsing existing duplicates into a single canonical row, the cleanup logic must keep the most complete/recent record (e.g., non-zero target/start weights, non-empty notes, or sessions with populated blocks/sets) and preserve the lowest primary key ID where appropriate so UI ordering remains stable.

## Requirements *(mandatory)*

### Functional Requirements

- **FR-001**: System MUST provide a comprehensive, atomic database deduplication operation (`cleanupDuplicateData`) that removes duplicate records across Exercises, Routines, Cycles, Cycle Goals, Workout Sessions, and Rep Maxes while preserving all relational links.
- **FR-002**: System MUST automatically execute database deduplication on application startup and as a pre-flight step before cloud upload and after cloud download.
- **FR-003**: System MUST deduplicate Cycle Goals by the natural identity key `(cycleId, exerciseId, goalMetric, targetReps)`, keeping a single canonical goal and deleting all redundant copies.
- **FR-004**: System MUST deduplicate Exercises by normalized name (`name.trim().lowercase()`), remapping all references in Cycle Goals, Rep Maxes, Routines, Routine Blocks, and Session Blocks to the canonical exercise ID before removing duplicate exercise rows.
- **FR-005**: System MUST deduplicate Routines by normalized name (`name.trim().lowercase()`), remapping any Session Block `routineId` references to the canonical routine ID before removing duplicate routine rows and their blocks.
- **FR-006**: System MUST deduplicate Workout Sessions that share the same `(cycleId, date, normalizedTitle)` and block/set signature (or created via duplicate sync imports), remapping any Rep Max `sessionId` references to the canonical session before deleting duplicate sessions.
- **FR-007**: System MUST deduplicate Rep Maxes by `(exerciseId, reps, weight, date)`, retaining a single canonical record per unique combination.
- **FR-008**: During cloud download (`downloadUserData`), the system MUST deduplicate incoming remote collection lists first and check whether each Exercise, Routine, Cycle Goal, Workout Session, Rep Max, Weight Entry, and Daily Log already exists in the local database before inserting or updating in place.
- **FR-009**: During cloud upload (`uploadUserData`), the system MUST clean local duplicates first, include resolved `exerciseName` / `cycleName` metadata where applicable to prevent cross-device ID drift, and upload only deduplicated collection lists so polluted cloud documents are automatically healed.
- **FR-010**: Repository persistence methods (`saveCycleGoal`, `saveCycleWithGoals`, `getOrCreateExercise`, `saveRoutineWithBlocks`, `recordRepMax`) MUST enforce natural-key upsert semantics so callers passing `id = 0` for an existing natural key update/reuse the existing record rather than inserting a duplicate.
- **FR-011**: Cloud synchronization operations (`uploadUserData`, `downloadUserData`, `recoverAllCloudRoutines`) MUST be serialized via mutual exclusion so concurrent or overlapping sync invocations cannot interleave and duplicate records.

### Key Entities

- **CycleGoal**: Target movement goal within a training cycle; natural identity is `(cycleId, exerciseId, goalMetric, targetReps)`.
- **Exercise**: Movement or machine tracked in the library; natural identity is normalized `name` (`name.trim().lowercase()`).
- **Routine**: Reusable workout template with ordered blocks; natural identity is normalized `name` (`name.trim().lowercase()`).
- **Session**: Logged workout on a specific date within a cycle; matched during sync by `(date, normalizedTitle)` and deduplicated when identical duplicate sessions exist.
- **RepMax**: Historical personal record for a movement; natural identity for deduplication is `(exerciseId, reps, weight, date)`.
- **Cycle**: Training periodization block; natural identity for duplicate cleanup is `(normalizedName, startDate, endDate, type)`.
- **WeightEntry & DailyLog**: Date-keyed body weight and nutrition/fasting logs; natural identity is `date` (`LocalDate`), merged via tombstone-aware Last-Write-Wins.

## Success Criteria *(mandatory)*

### Measurable Outcomes

- **SC-001**: A database polluted with 64 duplicated cycle goals (32 copies each of 2 unique goals) is automatically reduced to the exact 2 unique cycle goals (`0` duplicates remaining) upon app initialization or cloud sync.
- **SC-002**: Executing "Sync Now" (`triggerCloudSync`) or sign-in data download `N` consecutive times (`N >= 5`) produces `0` additional duplicate rows across all synced collections (Exercises, Routines, Cycle Goals, Sessions, Rep Maxes, Weight Entries, Daily Logs).
- **SC-003**: After a single "Sync Now" execution on a previously polluted account, the remote cloud backup payloads contain `0` duplicate entries across all 7 synced collections.
- **SC-004**: 100% of existing and new unit tests, script tests, and lint checks pass with zero regressions.

## Assumptions

- Within a single training cycle, an athlete or coach defines at most one target goal per unique `(exerciseId, goalMetric, targetReps)` combination (for example, one `Front Squat 3RM` weight goal and one `Snatch 2RM` weight goal per cycle).
- When multiple duplicate rows exist in the database for the same natural identity, keeping the canonical record (preferring non-zero weights/non-empty notes and the earliest primary key ID) preserves the user's intended training data and display order.
- Local SQLite remains the primary source of truth for Training Cycles (`Cycle` entities), while `CycleGoal` records synced via Cloud Firestore resolve their `cycleId` to the matching local cycle (or active local cycle if the remote `cycleId` does not exist locally).
