# Quickstart & Validation Guide: Cycle End-Date Required Pace in Goal Estimation Banner

**Feature**: `003-cycle-end-required-pace`
**Date**: 2026-10-22

## Prerequisites
- JDK 17 configured for Gradle (`org.gradle.java.home` in `~/.gradle/gradle.properties` or `JAVA_HOME`).
- Android SDK 35 installed.
- PowerShell 7+ (for script regression tests).

## 1. Run Unit Tests for Required Pace Analytics & Forecast Banner

Execute the targeted unit test suites for `FatLossAnalytics` and `WeightProgressionCard`:

```powershell
.\gradlew.bat testDebugUnitTest --tests "com.fractanomics.crosstraining.data.analytics.FatLossAnalyticsTest" --tests "com.fractanomics.crosstraining.ui.screens.WeightProgressionCardTest" --no-daemon
```

**Expected Outcome**:
- `FatLossAnalyticsTest` passes 100% of test cases, verifying:
  - `computeRequiredPaceToEndDate` calculates 1-decimal display-consistent required weekly rates in both `kg/wk` and `lbs/wk`.
  - Exact calendar weeks denominator (`daysRemaining / 7.0`) and `-0.1` floor clamping when `0 < remainingDisplay / weeksRemaining < 0.05`.
  - Null suppression when `cycleEndDate == null`, `today >= cycleEndDate` (`daysRemaining <= 0`), `remainingDisplay <= 0.0`, or `targetWeightKg >= startingWeightKg`.
- `WeightProgressionCardTest` passes 100% of test cases, verifying:
  - `resolveRequiredPaceSubtitle` emits `"Required pace: -X.X <kg/wk|lbs/wk> (by <d MMM yyyy>)"` across `Projected`, `Stalled`, and `NotEnoughData` states.
  - `resolveRequiredPaceSubtitle` returns `null` for `GoalReached`, `CycleEnded`, and open-ended cycles (`cycleEndDate == null`).

## 2. Run Full Pre-PR Verification Suite

```powershell
.\gradlew.bat testDebugUnitTest --no-daemon; .\scripts\tests\Invoke-ScriptTests.ps1; .\gradlew.bat lintDebug --no-daemon
```

**Expected Outcome**:
- All Android JVM unit tests, PowerShell script regression tests, and Android lint checks pass with 0 errors.

## 3. Build & Sync Local Debug APK

```powershell
.\gradlew.bat assembleDebug --no-daemon
Copy-Item -Path "app\build\outputs\apk\debug\app-debug.apk" -Destination "local_test\latest.apk" -Force
```

**Expected Outcome**:
- Debug APK compiles cleanly and is synced to `local_test\latest.apk`.
