# Quickstart Verification: Compact Complex Session Logging (`SPEC-028`)

## 1. Android Unit Tests & Build Verification
```powershell
.\gradlew.bat testDebugUnitTest --no-daemon
.\gradlew.bat lintDebug --no-daemon
```
Focus tests:
- `CompactComplexLoggingTest`: canonical `1-2-3-1-2-3` and `3-1-3-1-3-1` alignment, shared weights, `Mixed` split + re-link undo, skip toggles, `+ Add Set` / `✕` undo, skip-aware PR auto-sync, mixed barbell+erg isolation, Room/codec zero-migration round-trip, a11y/reflow helpers.
- `SessionEditorBlockLogEditTest`, `SessionEditorDailyRoutineAndExerciseTest`, `AvailableBlockCodecTest`, `SessionEditorAddBlockTest`: zero regressions on single-exercise blocks and standalone exercises.

## 2. Script Regression Tests
```powershell
.\scripts\tests\Invoke-ScriptTests.ps1
```

## 3. MCP Server Build & Integration Tests
```powershell
Push-Location mcp-server
npm run build
npm test
Pop-Location
```
Verifies `45` tools, `0` `$ref`, `0` `exclusiveMinimum`, plus `shared_set_weights_kg` integration across `log_complex_session`, `log_daily_routine_session`, and `update_complex_session_log`.
