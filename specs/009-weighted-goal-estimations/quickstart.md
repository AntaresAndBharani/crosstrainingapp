# Quickstart & Verification Guide: Weighted Average Pace Estimation & Goal Arrival Estimations Redesign

**Feature**: `009-weighted-goal-estimations`

## Prerequisites

- JDK 17 configured (`JAVA_HOME` or user `~/.gradle/gradle.properties`)
- Android SDK (`compileSdk 35`)
- PowerShell 7+ (`pwsh` or Windows PowerShell)

## Verification Scenarios

### 1. Run Unit Tests (`FatLossAnalyticsTest` & `WeightProgressionCardTest`)

Verifies:
- `4:3:2:1` weighted average pace math when all 4 horizons (`7D`, `14D`, `21D`, `28D`) are present.
- Dynamic weight re-normalization when only 1, 2, or 3 horizons are mature (e.g., `7D` + `14D` + `21D` with divisor `4+3+2 = 9`).
- Signed rate blending when one horizon is positive (gaining) and others are in deficit.
- Stalled and `> 1 year` capped states on `weightedAverageRow`.
- Imperial (`lbs`) 2-decimal conversion and display-unit reconciliation.
- UI string and TalkBack `contentDescription` resolvers for `Current Goal & Target` and `Weighted Average`.

```powershell
.\gradlew.bat testDebugUnitTest --no-daemon
```

### 2. Run PowerShell Script Regression Suite

```powershell
.\scripts\tests\Invoke-ScriptTests.ps1
```

### 3. Run Android Lint Check

```powershell
.\gradlew.bat lintDebug --no-daemon
```
