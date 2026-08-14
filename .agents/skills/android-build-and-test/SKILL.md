---
name: android-build-and-test
description: >-
  Build, test, and package the CrossTraining Android app. Use when the user asks to run unit tests, assemble debug/snapshot APKs, or sync test APKs.
---

# Android Build & Verification Runbook

## 1. Run Unit Tests
Execute the unit test suite with Gradle:
```powershell
.\gradlew.bat testDebugUnitTest --no-daemon
```
- On test failures, check `app\build\reports\tests\testDebugUnitTest\index.html` or terminal output for failed assertions.

## 2. Assemble and Sync Test APK
To build and sync a debug APK:
```powershell
.\gradlew.bat assembleDebug --no-daemon
if (!(Test-Path "local_test")) { New-Item -ItemType Directory -Path "local_test" }
Copy-Item -Path "app\build\outputs\apk\debug\app-debug.apk" -Destination "local_test\latest.apk" -Force
```

## 3. Run Lint
```powershell
.\gradlew.bat lintDebug --no-daemon
```
