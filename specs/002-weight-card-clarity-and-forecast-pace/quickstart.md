# Quickstart & Validation Guide: Weight Progression Table Clarity & Forecast Pace Attribution

**Feature**: `002-weight-card-clarity-and-forecast-pace`
**Date**: 2026-10-22

## Prerequisites
- JDK 17 configured for Gradle (`org.gradle.java.home` in `~/.gradle/gradle.properties` or `JAVA_HOME`).
- Android SDK 35 installed.
- PowerShell 7+ (for script regression tests).

## 1. Run Unit Tests for Domain Analytics & Weight Progression Card

Execute the targeted unit test suites for `FatLossAnalytics` and `WeightProgressionCard`:

```powershell
.\gradlew.bat testDebugUnitTest --tests "com.fractanomics.crosstraining.data.analytics.FatLossAnalyticsTest" --tests "com.fractanomics.crosstraining.ui.screens.WeightProgressionCardTest" --no-daemon
```

**Expected Outcome**:
- `FatLossAnalyticsTest` passes 100% of test cases, verifying:
  - 1-decimal display-consistent `delta7dKg` and `delta14dKg` in `computeRollingWeightHero`.
  - 1-decimal display-consistent `computePaceForecast` (`3.7 kg / 0.2 kg/wk = 18.5 weeks`) and `paceWindowDays` / `paceRateKgPerWeek` metadata on `PaceForecast.Projected`.
- `WeightProgressionCardTest` passes 100% of test cases, verifying:
  - `resolveLeftPillarHeader` returns `"LAST WK 7D"` and `resolveCenterPillarHeader` returns `"7D AVG"`.
  - `resolveDailyWeighInSectionHeader` returns `"DAILY WEIGH-IN (<DAY>)"` (e.g., `"DAILY WEIGH-IN (THU)"`).
  - `resolveForecastPaceSubtitle` formats `"Based on 14-day pace (-0.2 kg/wk)"` and `"Based on 7-day pace (-0.3 kg/wk)"` in both `kg` and `lbs` for `Projected` and `Stalled` states.

## 2. Run Full Pre-PR Verification Suite

```powershell
.\gradlew.bat testDebugUnitTest --no-daemon; .\scripts\tests\Invoke-ScriptTests.ps1
```

**Expected Outcome**:
- All Android JVM unit tests and PowerShell script regression tests pass with 0 failures.

## 3. Build & Sync Local Debug APK

```powershell
.\gradlew.bat assembleDebug --no-daemon
Copy-Item -Path "app\build\outputs\apk\debug\app-debug.apk" -Destination "local_test\latest.apk" -Force
```

**Expected Outcome**:
- Debug APK compiles cleanly and is synced to `local_test\latest.apk`.
