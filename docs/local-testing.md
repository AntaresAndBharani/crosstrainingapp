# 📱 Local Testing Guide for CrossTraining App

This document outlines the local testing infrastructure for the CrossTraining Android app, combining fast JVM checks, the automated `crosstrainingapp` emulator provisioning CLI, and visual End-to-End (E2E) flows.

---

## 1. Quick Verification (JVM Unit Tests & Build)

Runs in ~3 seconds without launching an Android emulator:
```powershell
.\gradlew.bat testDebugUnitTest assembleSnapshot -PsnapshotLabel=localtest --no-daemon
```

---

## 2. Automated Emulator Provisioning & Build Deployment (`crosstrainingapp` CLI)

The `scripts/crosstrainingapp.ps1` CLI automates Android Virtual Device (AVD) discovery, cold boot synchronization, verified build retrieval with local SHA caching, and deployment.

### Usage & Supported Flags

```powershell
# Deploy latest verified main-branch build to emulator
pwsh ./scripts/crosstrainingapp.ps1 emulator --latest-main

# Compile local source code and deploy immediately
pwsh ./scripts/crosstrainingapp.ps1 emulator --local

# Run emulator in headless mode (no GUI window)
pwsh ./scripts/crosstrainingapp.ps1 emulator --latest-main --headless

# Clear application data before launching
pwsh ./scripts/crosstrainingapp.ps1 emulator --latest-main --clear-data
```

### CLI Flag Reference

| Flag | Description |
| :--- | :--- |
| `--latest-main` | Fetches the latest verified APK corresponding to `origin/main` HEAD commit SHA via SHA-caching. Falls back gracefully to local compilation if unauthenticated or offline. |
| `--local` | Compiles the current local workspace using `./gradlew :app:assembleDebug --build-cache --parallel` (or `gradlew.bat` on Windows) and deploys immediately without contacting GitHub. |
| `--headless` | Launches the Android emulator with the `-no-window` flag for background test runs and headless environments. |
| `--clear-data` | Runs `adb shell pm clear com.fractanomics.crosstraining` as part of deployment to ensure a pristine application state. |

### SHA-Based Artifact Caching

To eliminate redundant downloads and local compilation bottlenecks, the CLI implements SHA-based artifact caching:
1. Determines the HEAD commit SHA of `origin/main`.
2. Checks for a cached artifact at `app/build/outputs/apk/ci-main/app-debug-<SHA>.apk`.
3. If the cached artifact exists locally, network transfers are skipped entirely.
4. If absent, the CLI retrieves the verified build from GitHub releases/artifacts and populates the cache.
5. If GitHub artifacts are unavailable or authentication fails, the CLI prints an `[ERROR]` badge and gracefully falls back to local Gradle compilation (`./gradlew :app:assembleDebug`).

### AVD Discovery & Naming Conventions

- The CLI discovers available emulators via `emulator -list-avds`.
- Filters available AVDs matching the regex `"Pixel|Phone"` (case-insensitive) to ensure a phone form factor is selected.
- Ignores Wear OS, Android Auto, and TV system images.
- If no matching AVD is installed, the CLI aborts with explicit instructions containing the exact `sdkmanager` and `avdmanager` commands required to install a compatible system image.

### Bulletproof 3-Stage Boot Sequencing

When cold-booting an emulator, the CLI ensures the Android system is fully responsive before attempting installation by waiting for:
1. `sys.boot_completed` property equals `1`.
2. `init.svc.bootanim` property equals `stopped`.
3. `adb shell pm path android` succeeds (verifying Package Manager availability).

### Signature Conflict Auto-Healing

When switching between debug builds and release-signed builds, Android may reject installation with `INSTALL_FAILED_UPDATE_INCOMPATIBLE`. The CLI detects this error code during `adb install -r -d`, automatically uninstalls `com.fractanomics.crosstraining`, and cleanly retries deployment.

### ANSI Status Badge Conventions

The CLI formats console output using standard ANSI status badges:
- `[INFO]` (Cyan): General informational output and routing steps.
- `[AVD]` (Yellow): AVD discovery, boot-lock polling, and device status.
- `[FETCH]` (Magenta): GitHub artifact queries and SHA cache verification.
- `[DEPLOY]` (Blue): Gradle compilation, package installation, and activity launch.
- `[SUCCESS]` (Green): Operation completed successfully.
- `[ERROR]` (Red): Error notifications and graceful fallback alerts.

### Terminal Detachment & Logcat Diagnostics

The emulator is launched as a detached background process that continues running even if Ctrl+C is pressed in the invoking terminal. Once deployed, the CLI outputs a diagnostic logcat command to monitor application and timer service logs:
```powershell
adb logcat -s CrossTrainingApp:* TimerService:*
```

---

## 3. End-to-End (E2E) UI Testing with Maestro

The project uses **Maestro** (the modern declarative mobile test framework) to automate user journeys on real emulators or physical Android devices.

### Prerequisites (Already Configured)
- **Android SDK & ADB**: Located in `%LOCALAPPDATA%\Android\Sdk\platform-tools\adb.exe`
- **Maestro CLI**: Installed in `%USERPROFILE%\.maestro\bin\maestro.bat`
- **Android Virtual Device (AVD)**: `Pixel_10_API_35` (or any AVD matching `Pixel|Phone`)

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

## 4. Included E2E Test Flows

Located in `e2e/flows/`:

1. **`01_auth_flow.yaml`**: Verifies Welcome screen, Google Sign-in button, and Guest access entry.
2. **`02_log_session_flow.yaml`**: Selects a movement, enters reps and weight in the spreadsheet table, toggles Warmup `[W]`, adds a second set, and saves the workout session.
3. **`03_history_and_search_flow.yaml`**: Navigates to History, verifies the compact summary cards, expands details, and tests the live search filter.
4. **`04_library_categories_flow.yaml`**: Tests the exercise library search bar and category filter chips (`Weightlifting`, `Powerlifting`, `Gymnastics`).
5. **`05_theme_mode_flow.yaml`**: Opens Profile & Settings and verifies instant theme mode switching (Default Light, Dark, Dark High Contrast, Light High Contrast).
6. **`06_coach_mode_flow.yaml`**: Tests role switching between Athlete Mode and Coach Mode and verifies dynamic bottom navigation tabs (`Log/History/Progress` vs `Cycles/Library/Progress`).

---

## 5. Writing New Test Flows

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

---

## 6. Release Workflow Step Names

For maintainers and contributors referencing steps in `.github/workflows/release.yml`:

An audit of `.github/workflows/release.yml` confirmed the canonical step names used during release build and distribution packaging:
- **Build Step:** `Build distributable APK (release variant)`
- **Packaging Step:** `Package release-signed APK as release distribution artifact`

The workflow file was audited and required no renaming. Stale references (such as `Build distributable APK (debug variant, debug-signed)` or `Package debug-signed APK as release distribution artifact`) originated in historical issue acceptance-criteria text (e.g. subtask #212), not in the workflow itself. Future stories and subtasks referencing release workflow steps should quote the verbatim step names above.

---

## 7. Branch Protection: Script-Tests Matrix Check Names

For maintainers and contributors configuring required status checks in repository branch protection settings:

An audit of `.github/workflows/build.yml` confirmed the canonical status-check context names emitted by the `script-tests` matrix job across runner operating systems (`matrix.os: [ubuntu-latest, windows-latest]`):
- **Ubuntu Runner Check:** `script-tests (ubuntu-latest)`
- **Windows Runner Check:** `script-tests (windows-latest)`

These are the exact required-status-check context names to configure in GitHub branch protection rules for `main` (replacing any deprecated monolithic `script-tests` check name). Future stories and subtasks referencing branch protection status checks for script tests should quote the verbatim check names above.

### Live Verification Evidence

Empirical verification against active pull requests targeting `main` (captured via `gh pr checks` and `gh api /repos/AntaresAndBharani/crosstrainingapp/branches/main/protection` on 2026-08-26 across PR #358 and PR #370) confirms:
- Both `script-tests (ubuntu-latest)` and `script-tests (windows-latest)` appear as distinct **REQUIRED** status checks in repository branch protection settings.
- PR merge state is strictly gated on both matrix legs reporting success (`mergeStateStatus: CLEAN` only when both checks pass).
