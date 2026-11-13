# Quickstart & Verification Guide: `020-estimated-finish-weight`

## Pre-PR Verification Commands (Constitution Principle III)

1. **Android JVM Unit Tests**:
   ```powershell
   .\gradlew.bat testDebugUnitTest --no-daemon
   ```
2. **Android Lint Check**:
   ```powershell
   .\gradlew.bat lintDebug --no-daemon
   ```
3. **Cross-Platform Script Regression Tests**:
   ```powershell
   .\scripts\tests\Invoke-ScriptTests.ps1
   ```
4. **Remote MCP Server Build & Tests**:
   ```powershell
   Push-Location mcp-server
   npm run build
   npm test
   Pop-Location
   ```
5. **Debug APK Build & Local Sync**:
   ```powershell
   .\gradlew.bat assembleDebug --no-daemon
   Copy-Item app\build\outputs\apk\debug\app-debug.apk local_test\latest.apk -Force
   ```

> **Note**: Per repository guidelines (`GEMINI.md`), do NOT start an Android emulator or execute Maestro E2E tests unless explicitly requested.
