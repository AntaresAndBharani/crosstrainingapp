# Quickstart & Validation Guide: 21D/28D Pace Grid & Visual Multi-Horizon Goal Estimations

## Prerequisites
- JDK 17 configured for Gradle
- Windows PowerShell 5.1+ or PowerShell 7+

## 1. Run Unit Tests
Execute the JUnit 4 test suite covering `FatLossAnalyticsTest` and `WeightProgressionCardTest`:

```powershell
.\gradlew.bat testDebugUnitTest --no-daemon
```

**Expected Outcome**: `BUILD SUCCESSFUL` with 100% passing unit tests for `compute21DayPace`, `compute28DayPace`, 2-decimal pace formatting, and `computeMultiHorizonForecast` across 7D, 14D, 21D, and 28D horizons.

## 2. Run PowerShell Script Regression Suite
```powershell
.\scripts\tests\Invoke-ScriptTests.ps1
```

**Expected Outcome**: All Pester script tests pass with 0 failures.

## 3. Run Android Lint Verification
```powershell
.\gradlew.bat lintDebug --no-daemon
```

**Expected Outcome**: `BUILD SUCCESSFUL` with zero lint errors.

## 4. Build Debug APK & Sync to `local_test\latest.apk`
```powershell
.\gradlew.bat assembleDebug --no-daemon
Copy-Item -Path "app\build\outputs\apk\debug\app-debug.apk" -Destination "local_test\latest.apk" -Force
```
