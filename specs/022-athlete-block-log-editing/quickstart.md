# Quickstart & Verification Guide (`SPEC-022`)

## Local Verification Commands (Windows)

1. **Android JVM Unit Tests**:
   ```powershell
   .\gradlew.bat testDebugUnitTest --no-daemon
   ```
2. **Cross-Platform Script Regression Tests**:
   ```powershell
   .\scripts\tests\Invoke-ScriptTests.ps1
   ```
3. **Android Lint Check**:
   ```powershell
   .\gradlew.bat lintDebug --no-daemon
   ```
4. **MCP Server Build & Contract Tests**:
   ```powershell
   Push-Location mcp-server; npm run build; npm test; Pop-Location
   ```
5. **Debug APK Build & Local Sync**:
   ```powershell
   .\gradlew.bat assembleDebug --no-daemon
   Copy-Item app\build\outputs\apk\debug\app-debug.apk local_test\latest.apk -Force
   ```

> **Constraint**: NEVER start an Android emulator or run Maestro E2E tests (`run-e2e-tests.ps1`).
