# Quickstart & Verification Guide: Body-Weight Percentage Index (`% BW`)

## Local Verification Commands (Windows PowerShell)

1. **Android JVM Unit Tests** *(must pass 100%, no emulator)*:
   ```powershell
   .\gradlew.bat testDebugUnitTest --no-daemon
   ```
2. **Android Lint Check**:
   ```powershell
   .\gradlew.bat lintDebug --no-daemon
   ```
3. **PowerShell Static & Behavioral Script Tests**:
   ```powershell
   .\scripts\tests\Invoke-ScriptTests.ps1
   ```
4. **Remote MCP Server Build & Unit/Integration Tests**:
   ```powershell
   Push-Location mcp-server; npm run build; npm test; Pop-Location
   ```
