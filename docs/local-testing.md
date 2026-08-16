# 📱 Local Testing Guide for CrossTraining App

This document outlines the local testing infrastructure for the CrossTraining Android app, combining fast JVM checks with visual End-to-End (E2E) flows.

---

## 1. Quick Verification (JVM Unit Tests & Build)

Runs in ~3 seconds without launching an Android emulator:
```powershell
.\gradlew.bat testDebugUnitTest assembleSnapshot -PsnapshotLabel=localtest --no-daemon
```

---

## 2. End-to-End (E2E) UI Testing with Maestro

The project uses **Maestro** (the modern declarative mobile test framework) to automate user journeys on real emulators or physical Android devices.

### Prerequisites (Already Configured)
- **Android SDK & ADB**: Located in `%LOCALAPPDATA%\Android\Sdk\platform-tools\adb.exe`
- **Maestro CLI**: Installed in `%USERPROFILE%\.maestro\bin\maestro.bat`
- **Android Virtual Device (AVD)**: `Pixel_10_API_35`

### Running the E2E Test Suite

Run all test flows with the automated runner:
```powershell
.\scripts\run-e2e-tests.ps1
```

Run a specific test flow:
```powershell
.\scripts\run-e2e-tests.ps1 -Flow "e2e\flows\01_auth_flow.yaml"
```

Or run directly with Maestro CLI:
```powershell
maestro test e2e\flows\
```

---

## 3. Included E2E Test Flows

Located in `e2e/flows/`:

1. **`01_auth_flow.yaml`**: Verifies Welcome screen, Google Sign-in button, and Guest access entry.
2. **`02_log_session_flow.yaml`**: Selects a movement, enters reps and weight in the spreadsheet table, toggles Warmup `[W]`, adds a second set, and saves the workout session.
3. **`03_history_and_search_flow.yaml`**: Navigates to History, verifies the compact summary cards, expands details, and tests the live search filter.
4. **`04_library_categories_flow.yaml`**: Tests the exercise library search bar and category filter chips (`Weightlifting`, `Powerlifting`, `Gymnastics`).
5. **`05_theme_mode_flow.yaml`**: Opens Profile & Settings and verifies instant theme mode switching (Default Light, Dark, Dark High Contrast, Light High Contrast).

---

## 4. Writing New Test Flows

Maestro flows are written in human-readable YAML:
```yaml
appId: com.fractanomics.crosstraining
---
- launchApp
- tapOn: "Log"
- assertVisible: "Log Session"
- tapOn: "+ Add Block"
- takeScreenshot: "screenshots/added_block"
```
