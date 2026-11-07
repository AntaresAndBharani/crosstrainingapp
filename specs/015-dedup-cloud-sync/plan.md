# Implementation Plan: Idempotent Cloud Synchronization & Database Deduplication

**Branch**: `feat/015-dedup-cloud-sync` | **Date**: 2026-11-07 | **Spec**: [spec.md](spec.md)

**Input**: Feature specification from `/specs/015-dedup-cloud-sync/spec.md`

## Summary

Eliminate data duplication during Cloud Firestore synchronization and automatically clean existing duplicate records in the local Room SQLite database and remote Firestore backup. This is achieved by:
1. Adding an atomic, foreign-key-safe `cleanupDuplicateData()` routine in `Repository` that deduplicates `exercises`, `cycles`, `routines`, `cycle_goals`, `sessions`, and `rep_maxes` on app launch (`AppDatabase.onOpen` and `AppViewModel.init`) and before/after every cloud sync.
2. Enforcing natural-key upsert semantics in `Repository` write operations (`saveCycleGoal`, `saveCycleWithGoals`, `recordRepMax`, `getOrCreateExercise`, `saveRoutineWithBlocks`).
3. Hardening `UserCloudSyncManager` (`downloadUserData` and `uploadUserData`) with incoming/outgoing natural-key deduplication, cross-device `exerciseName`/`cycleName` resolution, local cycle fallback resolution, and a `Mutex` serializing concurrent sync calls.

## Technical Context

**Language/Version**: Kotlin (JVM 17), Android (`compileSdk 35`, `minSdk 26`)

**Primary Dependencies**: Jetpack Compose (Material 3), Room SQLite (`2.6.1`), Firebase Auth & Cloud Firestore, Kotlin Coroutines & Flow

**Storage**: Room SQLite (`crosstraining.db`) as primary offline-first source of truth; Cloud Firestore (`environments/{env}/users/{uid}/data/{collection}`) for cloud backup & sync

**Testing**: JUnit 4, Kotlinx Coroutines Test (`runTest`), `FakeSampleAppDatabase` in-memory test double (`.\gradlew.bat testDebugUnitTest --no-daemon`), PowerShell Pester tests (`.\scripts\tests\Invoke-ScriptTests.ps1`), Android Lint (`.\gradlew.bat lintDebug --no-daemon`)

**Target Platform**: Android (`minSdk 26`, `targetSdk 35`)

**Project Type**: Mobile application (Android)

**Performance Goals**: Database deduplication and in-memory natural-key resolution complete in `< 50ms` for typical user datasets (`< 1,000` records) inside a single SQLite transaction

**Constraints**: Offline-first; zero data loss of unique records; no destructive Room schema migration required; 100% CI parity

**Scale/Scope**: All 7 cloud-synced collections (`exercises`, `routines`, `sessions`, `cycle_goals`, `rep_maxes`, `weight_entries`, `daily_logs`) plus local `cycles`

## Constitution Check

*GATE: Must pass before Phase 0 research. Re-check after Phase 1 design.*

- **I. MVVM & Unidirectional Data Flow (UDF)**: **PASS** — Deduplication and idempotent sync logic reside strictly in `Repository` and `UserCloudSyncManager`, exposed reactively through existing `StateFlow` streams in `AppViewModel`.
- **II. Pure Deterministic Analytics & Display-Unit Consistency**: **PASS** — Canonical record selection and `BigDecimal` `HALF_UP` weight normalization for `RepMax` deduplication are deterministic.
- **III. Test-First & 100% CI Parity (NON-NEGOTIABLE)**: **PASS** — Full unit test coverage in `DatabaseDeduplicationAndSyncIdempotencyTest` plus existing test suites, script tests, and `lintDebug`.
- **IV. Responsive Mobile UX & Accessibility**: **PASS** — Removing duplicate `CycleGoal` chips immediately restores clean `CycleCard` presentation (`Target Goals (2)` instead of `Target Goals (64)`).
- **V. Offline-First, Idempotent Synchronization & Environment Isolation**: **PASS** — Directly implements idempotent synchronization and automatic deduplication without requiring a schema version bump or machine-specific configuration.
- **VI. Agentic Collaboration, Automated PR Review & Self-Healing Governance**: **PASS** — Preserves all GitHub Actions workflow contracts.

## Project Structure

### Documentation (this feature)

```text
specs/015-dedup-cloud-sync/
├── plan.md              # Implementation plan
├── research.md          # Phase 0 root cause & design research
├── data-model.md        # Natural identity keys & deduplication rules
├── quickstart.md        # Validation commands & test scenarios
├── contracts/
│   └── sync-deduplication-contract.md
├── checklists/
│   ├── requirements.md
│   └── sync-data-integrity.md
└── tasks.md             # Actionable task breakdown
```

### Source Code (repository root)

```text
app/src/main/java/com/fractanomics/crosstraining/
├── data/
│   ├── AppDatabase.kt                      # Invoke cleanupDuplicateData() on database open/create
│   ├── Repository.kt                       # cleanupDuplicateData() + upsert guards on saveCycleGoal/saveCycleWithGoals/recordRepMax
│   ├── dao/
│   │   ├── ExerciseDao.kt                  # Trimmed case-insensitive lookup
│   │   └── RoutineDao.kt                   # Trimmed case-insensitive lookup
│   └── firebase/
│       └── UserCloudSyncManager.kt         # Mutex serialization, incoming/outgoing deduplication, exerciseName/cycleId resolution
├── ui/
│   ├── AppViewModel.kt                     # Startup cleanupDuplicateData() in init
│   └── screens/
│       └── ProfileScreen.kt                # Avoid redundant double download+sync on Google sign-in
app/src/test/java/com/fractanomics/crosstraining/
└── data/
    ├── VoiceRepositoryIntegrationTest.kt   # Complete DbSnapshot in FakeSampleAppDatabase
    └── firebase/
        └── DatabaseDeduplicationAndSyncIdempotencyTest.kt # Comprehensive unit & integration tests
```

**Structure Decision**: Single Android application module (`app/`) following the established MVVM + Repository architecture.

## Complexity Tracking

No constitution violations.
