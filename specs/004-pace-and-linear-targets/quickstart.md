# Quickstart & Validation Guide: Required-Pace & Linear Cycle Weight Targets

## Prerequisites
- JDK 17 configured in `~/.gradle/gradle.properties`
- Windows PowerShell environment

## Validation Commands

1. **Run Unit Test Suite (`FatLossAnalyticsTest` & `WeightProgressionCardTest`)**:
   ```powershell
   .\gradlew.bat testDebugUnitTest --no-daemon
   ```
   **Expected Outcome**: `BUILD SUCCESSFUL` with 100% passing tests verifying 2-decimal `Linear Today`, `Expected Today`, and `Target Tomorrow` calculations across both `7D Rolling Average` and `Daily Weight` in `kg` and `lbs`.

2. **Run PowerShell Script Regression Suite**:
   ```powershell
   .\scripts\tests\Invoke-ScriptTests.ps1
   ```
   **Expected Outcome**: All script tests pass with zero failures.

3. **Run Android Lint Check**:
   ```powershell
   .\gradlew.bat lintDebug --no-daemon
   ```
   **Expected Outcome**: `BUILD SUCCESSFUL` with zero lint errors.
