# Build & Local Test APK Sync Rules

## 1. Build Variants
- **Debug:** `.\gradlew.bat assembleDebug --no-daemon`
- **Snapshot:** `.\gradlew.bat assembleSnapshot --no-daemon`
- **Release:** `.\gradlew.bat assembleRelease --no-daemon`

## 2. Automated Emulator Provisioning & Verification
For agent-driven APK verification and emulator testing, always use the unified CLI entrypoint:
`./scripts/crosstrainingapp.ps1 emulator --latest-main` (or `--local` for workspace compilation).
See `.agents/rules/local_test_apk.md` and `docs/local-testing.md` for full CLI specifications.

## 3. Local Test APK Sync Automation
Whenever a new APK is built manually:
1. Locate the generated APK at:
   - `app\build\outputs\apk\debug\app-debug.apk` OR
   - `app\build\outputs\apk\snapshot\app-snapshot.apk`
2. Copy it to `local_test\latest.apk`:
   ```powershell
   Copy-Item -Path "app\build\outputs\apk\debug\app-debug.apk" -Destination "local_test\latest.apk" -Force
   ```
3. Ensure `local_test\` remains ignored in `.gitignore`.
