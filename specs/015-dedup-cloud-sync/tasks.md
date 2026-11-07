---
description: "Task list for Idempotent Cloud Synchronization & Database Deduplication"
---

# Tasks: Idempotent Cloud Synchronization & Database Deduplication

**Input**: Design documents from `/specs/015-dedup-cloud-sync/`

**Prerequisites**: plan.md (required), spec.md (required for user stories), research.md, data-model.md, contracts/

**Tests**: Included per Constitution Principle III (Test-First & 100% CI Parity).

**Organization**: Tasks are grouped by phase and user story to enable independent implementation and testing.

## Format: `[ID] [P?] [Story] Description`

- **[P]**: Can run in parallel (different files, no dependencies)
- **[Story]**: Which user story this task belongs to (e.g., US1, US2, US3)
- Exact file paths included in descriptions

---

## Phase 1: Setup (Shared Infrastructure & Test Harness)

**Purpose**: Ensure in-memory test database snapshot completeness for transactional deduplication tests.

- [X] T001 Extend `FakeSampleAppDatabase.DbSnapshot` to snapshot and restore `cycleGoalsStorage`, `routinesStorage`, `routineBlocksStorage`, and `repMaxesStorage` in `app/src/test/java/com/fractanomics/crosstraining/data/VoiceRepositoryIntegrationTest.kt`

---

## Phase 2: Foundational (DAO Lookup Normalization)

**Purpose**: Normalize case-insensitive and whitespace-trimmed DAO lookups before user story implementation.

- [X] T002 [P] Update `ExerciseDao.byName` query to match `LOWER(TRIM(name)) = LOWER(TRIM(:name))` in `app/src/main/java/com/fractanomics/crosstraining/data/dao/ExerciseDao.kt` and `app/src/test/java/com/fractanomics/crosstraining/data/VoiceRepositoryIntegrationTest.kt`
- [X] T003 [P] Update `RoutineDao.byName` query to match `LOWER(TRIM(name)) = LOWER(TRIM(:name))` in `app/src/main/java/com/fractanomics/crosstraining/data/dao/RoutineDao.kt` and `app/src/test/java/com/fractanomics/crosstraining/data/VoiceRepositoryIntegrationTest.kt`

**Checkpoint**: Foundation ready - user story implementation can now begin.

---

## Phase 3: User Story 1 - Automatic Cleanup of Existing Duplicate Records (Priority: P1) 🎯 MVP

**Goal**: Automatically deduplicate existing duplicate records across Exercises, Cycles, Routines, Cycle Goals (such as the 64 duplicate target goals), Workout Sessions, and Rep Maxes while remapping all relational references.

**Independent Test**: Seed 64 duplicate cycle goals (32 copies each of `Front Squat 3RM` and `Snatch 2RM`) plus duplicate exercises, routines, sessions, and rep maxes; invoke `cleanupDuplicateData()` (or `AppViewModel` initialization); verify only 2 unique cycle goals and 1 canonical copy of each unique entity remain with all references intact.

### Tests for User Story 1

- [X] T004 [P] [US1] Create unit tests verifying `cleanupDuplicateData` reduces 64 duplicated `CycleGoal`s to 2 unique goals and deduplicates `Exercise`, `Routine`, `Cycle`, `Session`, and `RepMax` rows with foreign-key remapping in `app/src/test/java/com/fractanomics/crosstraining/data/firebase/DatabaseDeduplicationAndSyncIdempotencyTest.kt`

### Implementation for User Story 1

- [X] T005 [US1] Implement `cleanupDuplicateData()`, `cleanupDuplicateExercises()`, `cleanupDuplicateCycles()`, enhanced `cleanupDuplicateRoutines()`, `cleanupDuplicateCycleGoals()`, `cleanupDuplicateSessions()`, and `cleanupDuplicateRepMaxes()` inside `withDatabaseTransaction` in `app/src/main/java/com/fractanomics/crosstraining/data/Repository.kt`
- [X] T006 [P] [US1] Invoke `Repository(database).cleanupDuplicateData()` during `onCreate` and `onOpen` callbacks in `app/src/main/java/com/fractanomics/crosstraining/data/AppDatabase.kt`
- [X] T007 [P] [US1] Invoke `repo.cleanupDuplicateData()` during startup initialization in `app/src/main/java/com/fractanomics/crosstraining/ui/AppViewModel.kt`

**Checkpoint**: User Story 1 is complete and independently testable.

---

## Phase 4: User Story 2 - Idempotent Manual & Automatic Synchronization ("Sync Now") (Priority: P1)

**Goal**: Ensure every manual ("Sync Now") and automatic (sign-in restore) synchronization checks whether each incoming/outgoing record already exists in the database by its natural identity key, deduplicates remote payloads, heals polluted remote collections on upload, and serializes concurrent sync calls with a `Mutex`.

**Independent Test**: Trigger `triggerCloudSync` and `downloadUserData` 10 consecutive times against both clean and 64-entry-polluted remote payloads; verify 0 duplicate entries exist locally or in the uploaded cloud payloads.

### Tests for User Story 2

- [X] T008 [P] [US2] Add unit tests for 10x consecutive `triggerCloudSync` idempotency, 64-duplicate remote `cycle_goals` payload healing, cross-device `exerciseName`/`cycleId` resolution, and concurrent sync serialization in `app/src/test/java/com/fractanomics/crosstraining/data/firebase/DatabaseDeduplicationAndSyncIdempotencyTest.kt`

### Implementation for User Story 2

- [X] T009 [US2] Add `syncMutex` serialization, pre-upload/post-download `repo.cleanupDuplicateData()` calls, outgoing payload deduplication with `exerciseName`/`cycleName` enrichment, and incoming payload natural-key deduplication & local cycle resolution in `app/src/main/java/com/fractanomics/crosstraining/data/firebase/UserCloudSyncManager.kt`
- [X] T010 [P] [US2] Remove redundant concurrent `triggerCloudSync` call immediately inside `logInWithGoogle` callback in `app/src/main/java/com/fractanomics/crosstraining/ui/screens/ProfileScreen.kt` (or rely on `logInWithGoogle` + serialized sync) so sign-in does not fire overlapping uncoordinated syncs

**Checkpoint**: User Story 2 is complete and independently testable.

---

## Phase 5: User Story 3 - Repository-Level Upsert Guards on Entity Persistence (Priority: P2)

**Goal**: Enforce natural-key upsert semantics directly in `Repository` write methods (`saveCycleGoal`, `saveCycleWithGoals`, `recordRepMax`, `addRepMax`, `getOrCreateExercise`, `saveRoutineWithBlocks`) so callers passing `id = 0` for an existing natural key update/reuse the existing record in place.

**Independent Test**: Call `saveCycleGoal`, `saveCycleWithGoals`, `recordRepMax`, `getOrCreateExercise`, and `saveRoutineWithBlocks` repeatedly with `id = 0` and identical natural keys; verify only a single row exists in each table.

### Tests for User Story 3

- [X] T011 [P] [US3] Add unit tests verifying natural-key upsert guards on `saveCycleGoal`, `saveCycleWithGoals`, `recordRepMax`, `getOrCreateExercise`, and `saveRoutineWithBlocks` in `app/src/test/java/com/fractanomics/crosstraining/data/firebase/DatabaseDeduplicationAndSyncIdempotencyTest.kt`

### Implementation for User Story 3

- [X] T012 [US3] Update `saveCycleGoal`, `saveCycleWithGoals`, `recordRepMax`, `addRepMax`, `saveSession`, and `updateSession` to enforce natural-key upsert semantics in `app/src/main/java/com/fractanomics/crosstraining/data/Repository.kt`

**Checkpoint**: All user stories (US1, US2, US3) are independently functional and tested.

---

## Phase 6: Polish & Cross-Cutting Concerns

**Purpose**: Final verification across unit tests, script tests, and Android lint.

- [X] T013 Execute full verification suite (`.\gradlew.bat testDebugUnitTest --no-daemon`, `.\scripts\tests\Invoke-ScriptTests.ps1`, `.\gradlew.bat lintDebug --no-daemon`) and update `CHANGELOG.md` under `## [Unreleased]`

---

## Dependencies & Execution Order

### Phase Dependencies

- **Setup (Phase 1)**: No dependencies - starts immediately.
- **Foundational (Phase 2)**: Depends on Phase 1 - normalizes DAO lookups.
- **User Story 1 (Phase 3)**: Depends on Phase 2 - implements core database deduplication.
- **User Story 2 (Phase 4)**: Depends on Phase 3 - integrates deduplication and natural-key matching into cloud sync.
- **User Story 3 (Phase 5)**: Depends on Phase 2 - hardens repository write methods with natural-key upsert guards.
- **Polish (Phase 6)**: Depends on Phases 3–5 completion.

### Parallel Opportunities

- `T002` and `T003` can run in parallel.
- `T006` and `T007` can run in parallel once `T005` is complete.
- `T004`, `T008`, and `T011` test suites can be authored together in `DatabaseDeduplicationAndSyncIdempotencyTest.kt`.
