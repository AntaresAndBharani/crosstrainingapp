# Quickstart & Validation Guide: Rolling Window Average Pacing & Goal Forecast

**Feature**: `001-rolling-weight-pace`
**Date**: 2026-10-22

## Prerequisites
- JDK 17 configured for Gradle (`org.gradle.java.home` in `~/.gradle/gradle.properties` or `JAVA_HOME`).
- Android SDK 35 installed.
- PowerShell 7+ (for script regression tests).

## 1. Run Unit Tests for Analytics & Weight Progression UI

Execute the targeted unit test suites for `FatLossAnalytics` and `WeightProgressionCard`:

```powershell
.\gradlew.bat testDebugUnitTest --tests "com.fractanomics.crosstraining.data.analytics.FatLossAnalyticsTest" --tests "com.fractanomics.crosstraining.ui.screens.WeightProgressionCardTest" --no-daemon
```

**Expected Outcome**:
- `FatLossAnalyticsTest` passes 100% of test cases, including:
  - Smoothed 7-Day Pace ($\overline{W}_{7\text{d}}(t) - \overline{W}_{7\text{d}}(t-7)$) and 14-Day Pace ($(\overline{W}_{7\text{d}}(t) - \overline{W}_{7\text{d}}(t-14)) / 2.0$).
  - Pre-cycle lookback window inclusion for weeks 1–3 of a cycle.
  - Tiered forecast velocity fallback (Tier 1: 14D Smoothed Pace $\rightarrow$ Tier 2: 7D Smoothed Pace when Tier 1 is unavailable; Stalled when the selected tier shows no deficit).
- `WeightProgressionCardTest` passes 100% of test cases, verifying top reference track (`% to goal` and `-X.X kg left`) reconciliation with `TODAY` 7D average weight.

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
