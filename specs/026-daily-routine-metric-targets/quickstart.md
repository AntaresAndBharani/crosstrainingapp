# Quickstart Validation Guide: `SPEC-026`

## Prerequisites
- Android Gradle wrapper (`.\gradlew.bat`) on Windows with JVM 17
- Node.js 22+ (`mcp-server/`)
- PowerShell (`pwsh` or Windows PowerShell) for `scripts/tests/Invoke-ScriptTests.ps1`

## Verification Flows

### 1. Android Unit Tests (`testDebugUnitTest`)
```powershell
.\gradlew.bat testDebugUnitTest --no-daemon
```
Validates:
- `RoutineTargetCodecTest`: multi-line metadata scanning (`[rb_v2=...]`, `[dr_item=...]`, `[ab_prs=...]`, `[log_metric=...]`) and clean note extraction.
- `DailyRoutineCodecTest`: optional targets round-trip on `StandaloneExercise` & `BlockRef`, unset target emission (no `[rb_v2=...]`), multi-metric `resolveLastExerciseSession`, and `computeDailyRoutineProgress` across weighted reps, bodyweight reps, calories, time, and meters.
- `AvailableBlockCodecTest`: Edit Session hydration for bodyweight rep-only, `metricValue`, and `groupIndex` sets alongside warm-up/failed/Metcon isolation.
- `LibraryDailyRoutinesTest`: `DailyRoutineComposerState` target reducers, metric caching, shared block sets resize, all-zero validation, and chip labels.
- `SessionEditorDailyRoutineAndExerciseTest`: Daily Routine target expansion (including `0 / Skip` rounds), pre-filled target + historical weight merge, metric switching cache, bodyweight & metric-value `BlockDraft` serialization, and template sync preservation.

### 2. Full Pre-PR Verification Suite
```powershell
.\gradlew.bat testDebugUnitTest --no-daemon
.\scripts\tests\Invoke-ScriptTests.ps1
.\gradlew.bat lintDebug --no-daemon
Push-Location mcp-server
npm run build
npm test
Pop-Location
```

### 3. Debug APK Build & Local Sync
```powershell
.\gradlew.bat assembleDebug --no-daemon
Copy-Item app\build\outputs\apk\debug\app-debug.apk local_test\latest.apk -Force
```
