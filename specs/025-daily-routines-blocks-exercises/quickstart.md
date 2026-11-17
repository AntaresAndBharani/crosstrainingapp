# Quickstart Validation Guide: Daily Routines (`SPEC-025`)

## Prerequisites
- JDK 17 & Android SDK (`compileSdk 35`)
- Node.js 22+ (for `mcp-server/`)
- PowerShell (`pwsh` / Windows PowerShell)

## Automated Verification Commands

### 1. Android JVM Unit Tests (including `DailyRoutineCodecTest`, `LibraryDailyRoutinesTest`, `SessionEditorDailyRoutineAndExerciseTest`, `DailyRoutineProgressTest`)
```powershell
.\gradlew.bat testDebugUnitTest --no-daemon
```

### 2. PowerShell & CI Static Regression Tests
```powershell
.\scripts\tests\Invoke-ScriptTests.ps1
```

### 3. Android Lint Verification
```powershell
.\gradlew.bat lintDebug --no-daemon
```

### 4. Remote MCP Server Build & Integration Tests (39 tools, flat schema check, Daily Routines suite)
```powershell
Push-Location mcp-server
npm run build
npm test
Pop-Location
```

### 5. Debug APK Build & Local Sync
```powershell
.\gradlew.bat assembleDebug --no-daemon
New-Item -ItemType Directory -Force -Path local_test | Out-Null
Copy-Item -Force app\build\outputs\apk\debug\app-debug.apk local_test\latest.apk
```

## End-to-End Functional Scenarios Verified
1. **3-Tab Library & Composer**: Create a Daily Routine combining Available Blocks and standalone Exercises (with optional notes & reordering), verify `BLOCK:` vs `EX:` badges, duplicate/delete+undo, and tap `Log Routine ▸`.
2. **Unified Session Logging & Template Sync**: Verify fresh `Log Session` hides the legacy spreadsheet table, loads routine items with unified cross-app pre-fill, tracks standalone Exercises via `SimplifiedExerciseLogCard`, optionally updates the routine template on save, and re-hydrates cleanly in `Edit Session`.
3. **Progress & Goals (`By routine`)**: Verify `By routine` first-chip placement, 0-routines empty state navigation, `All Items Overview` cards, and constituent drill-down KPIs/charts/history.
4. **Remote MCP Parity**: Verify `list_daily_routines`, `upsert_daily_routine`, `log_daily_routine_session`, `get_daily_routine_progress`, `delete_daily_routine`, and isolation from `list_available_blocks`.
