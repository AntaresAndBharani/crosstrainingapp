# Quickstart & Validation Guide: Goal Arrival Estimations Average Weights & Section Reordering

**Feature**: `010-goal-estimations-weights-order`
**Date**: 2026-11-01

## Prerequisites

- JDK 17 configured for Gradle (`.\gradlew.bat`).
- Windows PowerShell / `pwsh` for script regression tests.

## Verification Commands

### 1. Run Unit Tests (`FatLossAnalyticsTest` & `WeightProgressionCardTest`)

```powershell
.\gradlew.bat testDebugUnitTest --tests "com.fractanomics.crosstraining.data.analytics.FatLossAnalyticsTest" --tests "com.fractanomics.crosstraining.ui.screens.WeightProgressionCardTest" --no-daemon
```

**Expected Outcome**:
- `computeMultiHorizonForecast` populates `averageWeightDisplay` for `7D`, `14D`, `21D`, `28D`, and `weightedAverageRow` in both `kg` and `lbs`.
- `HorizonEstimationRow.formatLeftLabel()` produces `"7D (-0.49 kg/wk · 81.5 kg)"` and `formatWeightedSummary()` produces `"(Weighted pace of -0.39 kg/wk · 82.1 kg · 7.9 wks · 26 Dec 2026)"`.
- Projected weeks and arrival dates remain anchored to the current 7D remaining weight to goal.

### 2. Run Full Pre-PR Verification Suite & Lint

```powershell
.\gradlew.bat testDebugUnitTest --no-daemon
.\scripts\tests\Invoke-ScriptTests.ps1
.\gradlew.bat lintDebug --no-daemon
```

**Expected Outcome**:
- 100% of unit tests, PowerShell script tests, and Android Lint checks pass with zero errors.
