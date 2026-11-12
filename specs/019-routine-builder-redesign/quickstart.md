# Quickstart & End-to-End Validation Guide: `019-routine-builder-redesign`

## Prerequisites
- JDK 17 configured in `~/.gradle/gradle.properties` (never in repository `gradle.properties`)
- Android SDK 35 installed
- Node.js `>= 20` (for `mcp-server/` build & unit tests)

## 1. Run Automated Verification Suites

### Android Unit Tests (Codec, UI State Reducers, Session Expansion, Backup, Deduplication)
```powershell
.\gradlew.bat testDebugUnitTest --no-daemon
```
**Expected Outcome**: `BUILD SUCCESSFUL` with 100% passing tests across `RoutineTargetCodecTest`, `RoutineBuilderUiStateTest`, `RoutineSessionExpansionTest`, `LibraryScreenTest`, `BackupCsvV4AndDraftParityTest`, `DatabaseDeduplicationAndSyncIdempotencyTest`, and `RoomSchemaExportTest`.

### PowerShell Workflow & Script Regression Tests
```powershell
.\scripts\tests\Invoke-ScriptTests.ps1
```
**Expected Outcome**: All Pester/script assertions pass with zero failures.

### Android Lint & Debug APK Assembly
```powershell
.\gradlew.bat lintDebug assembleDebug --no-daemon
Copy-Item app\build\outputs\apk\debug\app-debug.apk local_test\latest.apk -Force
```
**Expected Outcome**: Zero lint errors and synced debug APK at `local_test\latest.apk`.

### Remote MCP Server Build & Test Suite
```powershell
Push-Location mcp-server
npm run build
npm test
Pop-Location
```
**Expected Outcome**: TypeScript compiles cleanly (`tsc`) and `node --test` passes 100%, including connector schema checks (zero `$ref`, zero `exclusiveMinimum`) and mixed-metric `upsert_routine` / `list_routines` round-trip tests.

## 2. Manual End-to-End Walkthrough Scenarios

1. **4-Tier Collapsible Routine Creation**:
   - Open **Library → Daily Routines → `+`**.
   - Enter `"Monday Conditioning"`, tap into **Block 1** → verify **Routine Info** collapses into a single-row summary.
   - Select **WOD / Metcon** → **E3MOM** (`4 Sets` auto-selected). Select **AMRAP** (`1 Set` auto-selected with unlocked `+` hint), then switch back to **E3MOM** (`4 Sets`).
2. **Mixed-Metric & `0 (Skip)` Per-Set Configuration**:
   - Search and add `Bench Press` (`Reps`, Fixed `8` → `8 reps / set`).
   - Search and add `Air Bike` (`Calories` auto-selected), switch to **Per-Set**, set `Set 3 = 12` and `Set 4 = 0 (Skip)` → verify dashed skipped card `0 cal (Skip)` and collapsed badge `15 • 15 • 12 • 0 cal`.
   - Search and add `Concept2 Row` (`Meters` auto-selected), switch to **Per-Set**, set `[250, 250, 200, 0]`.
   - Step `Block Sets` from `4 → 5` and verify `Set 5` copies `Set 4` (`0`), then step back `5 → 4`.
   - Save Routine and verify clean summary badges on the **Library Routine Card**.
3. **Workout Logging (`Load Routine...`)**:
   - Open **Log Session → Load Routine... → `"Monday Conditioning"`**.
   - Verify a grouped **Mixed Conditioning** Sub-Block (`E3MOM · 4 Rounds`) renders with 3 movement cards (`Bench Press` with 4 sets of 8 reps; `Air Bike` with 3 active sets `15, 15, 12` cal on rounds `1..3`; `Concept2 Row` with 3 active sets `250, 250, 200` m on rounds `1..3`).
