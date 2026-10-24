# Quickstart & Verification Guide: Multi-Window Rolling Average Scale Targets

## Prerequisites
- JDK 17 configured for Gradle (`.\gradlew.bat`).
- PowerShell (`pwsh` / Windows PowerShell) for script regression tests.

## Verification Commands

1. **Run Android Unit Tests**:
   ```powershell
   .\gradlew.bat testDebugUnitTest --no-daemon
   ```
   Verifies `FatLossAnalyticsTest` and `WeightProgressionCardTest` covering:
   - `7D`, `14D`, `21D`, and `28D` scale weight solving (`Expected Today` and `Target Tomorrow`) in `kg` and `lbs`.
   - Window-sum identity reconciliation ($(k + 1) \times \text{TargetAvg} - \sum W = W_{\text{scale}}$).
   - Unlogged today behavior (`deltaDisplay == null` on `Expected Today`, valid `Target Tomorrow` from remaining window logs).
   - Horizon-specific history maturity (`"Needs 7 days history"`, `"Needs 14 days history"`, `"Needs 21 days history"`, `"Needs 28 days history"`).

2. **Run Script Tests**:
   ```powershell
   .\scripts\tests\Invoke-ScriptTests.ps1
   ```

3. **Run Android Lint**:
   ```powershell
   .\gradlew.bat lintDebug --no-daemon
   ```
