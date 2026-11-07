# Quickstart & Validation Guide: Idempotent Cloud Synchronization & Database Deduplication

## Prerequisites
- JDK 17 configured in `~/.gradle/gradle.properties` or `JAVA_HOME`
- PowerShell (`pwsh` or Windows PowerShell)

## Automated Verification Commands

### 1. Run Unit Tests (including Sync & Deduplication Regression Suite)
```powershell
.\gradlew.bat testDebugUnitTest --no-daemon
```
Verifies:
- `DatabaseDeduplicationAndSyncIdempotencyTest`:
  - Polluted database with 64 duplicate `CycleGoal` rows (32 `Front Squat (3RM): 50 -> 65kg` and 32 `Snatch (2RM): 40kg`) is cleaned to exactly 2 unique goals.
  - Duplicate `Exercise`, `Routine`, `Session`, `RepMax`, and `Cycle` rows are collapsed to 1 canonical record each with foreign keys remapped.
  - Clicking "Sync Now" (`triggerCloudSync`) 10 consecutive times produces 0 duplicates across all 7 collections.
  - Downloading a polluted remote Firestore payload with 64 duplicate `cycle_goals` results in only 2 unique local goals, and the subsequent upload heals the remote Firestore payload to 2 goals.
  - Repository upsert guards (`saveCycleGoal`, `saveCycleWithGoals`, `recordRepMax`, `getOrCreateExercise`, `saveRoutineWithBlocks`) prevent duplicate insertion when called repeatedly with `id = 0`.

### 2. Run PowerShell Script Regression Suite
```powershell
.\scripts\tests\Invoke-ScriptTests.ps1
```

### 3. Run Android Lint Verification
```powershell
.\gradlew.bat lintDebug --no-daemon
```
