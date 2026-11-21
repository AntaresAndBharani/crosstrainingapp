# Quickstart & Validation Guide: Rename Blocks to Complexes (`SPEC-027`)

## Prerequisites
- JDK 17 + Gradle wrapper (`.\gradlew.bat`)
- PowerShell 7 / Windows PowerShell (`.\scripts\tests\Invoke-ScriptTests.ps1`)
- Node.js >= 20 (`mcp-server/` dependencies installed)

## Verification Scenarios

1. **Android JVM Unit Tests (UI helpers, Complex editor validation, Routine composer, Session sheet labels, Codecs)**:
   ```powershell
   .\gradlew.bat testDebugUnitTest --no-daemon
   ```
   Verify `LibraryDailyRoutinesTest`, `SessionEditorAddBlockTest`, `DailyRoutineProgressTest`, `AvailableBlockCodecTest`, and `DailyRoutineCodecTest` all pass 100%.

2. **Android Lint Check**:
   ```powershell
   .\gradlew.bat lintDebug --no-daemon
   ```
   Verify zero new lint errors or accessibility/typography issues.

3. **PowerShell Script Regression Suite**:
   ```powershell
   .\scripts\tests\Invoke-ScriptTests.ps1
   ```
   Verify 100% passing CI/script checks.

4. **MCP Server TypeScript Build & Integration Suite**:
   ```powershell
   Push-Location mcp-server
   npm run build
   npm test
   Pop-Location
   ```
   Verify:
   - Total registered tools equals `45` (including all 6 Complex tools + 6 legacy Block aliases).
   - Generated tool JSON schemas contain zero `"$ref"` and zero `"exclusiveMinimum"`.
   - Complex tools, Daily Routine `item_type: "COMPLEX"` / `complex_id` / `complex_title` precedence, and legacy `"BLOCK"` backward compatibility tests all pass.
