# Quickstart & Validation Guide: Fix Expected Today Pace & Linear Targets (7D–28D)

## Prerequisites
- JDK 17 configured for Gradle (`.\gradlew.bat`).
- PowerShell 7+ or Windows PowerShell for script regression tests.

## Validation Scenarios

### Scenario 1: Multi-Horizon `Expected Today` Stability (7D, 14D, 21D, 28D)
Verify that when a user has 35+ days of weigh-ins with real-world fluctuations (such as a slower week followed by a faster week), `Expected Today` across `7D`, `14D`, `21D`, and `28D` remains within a realistic daily range of `Target Tomorrow` and never exhibits $O(N^2)$ divergence (e.g., `71.50 kg` at `14D` vs `94.52 kg` at `28D`).

Run the unit test suite:
```powershell
.\gradlew.bat testDebugUnitTest --tests "com.fractanomics.crosstraining.data.analytics.FatLossAnalyticsTest" --no-daemon
```
**Expected Outcome**: `BUILD SUCCESSFUL` with 0 failures across all `paceAndLinearTargets` unit tests, including the multi-horizon screenshot regression test.

### Scenario 2: Full Unit Test, Script Regression, and Lint Suite
Run the full verification suite required by the project constitution:
```powershell
.\gradlew.bat testDebugUnitTest --no-daemon
.\scripts\tests\Invoke-ScriptTests.ps1
.\gradlew.bat lintDebug --no-daemon
```
**Expected Outcome**:
- All unit tests pass (`100%` green).
- All PowerShell script regression tests pass (`0` failures).
- Android Lint passes with no new errors.
