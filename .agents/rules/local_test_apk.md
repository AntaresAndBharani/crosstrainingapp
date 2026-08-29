# 📱 Local Test APK & Emulator Verification Rules

Autonomous agents working on CrossTraining must follow standardized tooling for local APK provisioning, emulator management, and application verification.

## 1. Mandatory CLI Invocation Gate
- **Primary Command:**
  Autonomous agents MUST run `./scripts/crosstrainingapp.ps1 emulator --latest-main` (or `pwsh ./scripts/crosstrainingapp.ps1 emulator --latest-main`) to provision the Android emulator and deploy verified builds.
- **Rule:** DO NOT invoke raw `gradlew` (`assembleDebug`) or `adb` (`adb install`, `adb shell am start`) commands directly for agent-driven APK verification. The CLI encapsulates artifact caching, AVD boot synchronization, and signature conflict recovery.

## 2. CLI Execution Modes & Supported Flags
- **Latest Main Deployment (`--latest-main`):**
  `./scripts/crosstrainingapp.ps1 emulator --latest-main`
  - Leverages SHA-based caching (`app/build/outputs/apk/ci-main/app-debug-<SHA>.apk`) to avoid redundant compilation and network downloads.
  - Automatically provisions the preferred phone AVD and ensures the 3-stage boot lock is satisfied.
- **Local Compilation Mode (`--local`):**
  `./scripts/crosstrainingapp.ps1 emulator --local`
  - Compiles the local workspace APK (`./gradlew :app:assembleDebug --build-cache --parallel` or `gradlew.bat` on Windows) and deploys immediately without contacting GitHub.
- **Headless Execution Mode (`--headless`):**
  `./scripts/crosstrainingapp.ps1 emulator --latest-main --headless`
  - Boots the Android emulator in headless mode (`-no-window`) for automated environments, background validation, and CI parity.
- **Clean Application State (`--clear-data`):**
  `./scripts/crosstrainingapp.ps1 emulator --latest-main --clear-data`
  - Resets application state by executing `adb shell pm clear com.fractanomics.crosstraining` before launch.

## 3. Autonomous Quality & Diagnostic Guarantees
- **AVD Form Factor Filtering:** Selects AVDs matching the regex `Pixel|Phone` and ignores Wear OS or TV images.
- **3-Stage Boot Synchronization:** The CLI polls until `sys.boot_completed == 1`, `init.svc.bootanim == stopped`, and `adb shell pm path android` succeeds before attempting deployment.
- **Signature Auto-Healing:** Detects `INSTALL_FAILED_UPDATE_INCOMPATIBLE` during `adb install -r -d` and automatically uninstalls the existing package before cleanly reinstalling.
- **Logcat Diagnostics:** After deployment, observe application and timer logs via `adb logcat -s CrossTrainingApp:* TimerService:*`.
