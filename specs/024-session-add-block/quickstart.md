# Quickstart Validation Guide: Unified Add Block Workflow (`SPEC-024`)

## Prerequisites

- Android JDK 17 + Gradle wrapper (`.\gradlew.bat`)
- PowerShell 7 / Windows PowerShell (`pwsh` / `.\scripts\tests\Invoke-ScriptTests.ps1`)
- No Android emulator or Maestro E2E run is required or permitted unless explicitly requested (`GEMINI.md` Rule 4).

## 1. Automated Verification Commands

Run the full JVM verification suite:

```powershell
.\gradlew.bat testDebugUnitTest --no-daemon
.\scripts\tests\Invoke-ScriptTests.ps1
.\gradlew.bat lintDebug --no-daemon
.\gradlew.bat assembleDebug --no-daemon
Copy-Item -Path "app\build\outputs\apk\debug\app-debug.apk" -Destination "local_test\latest.apk" -Force
```

## 2. Targeted Feature Verification Scenarios

1. **Edit Session — Add Another Saved Block**:
   - Navigate to `History` -> expand an existing workout -> tap `Edit` (`Edit Session`).
   - Tap `+ Add Block` (or `Load Block ▾`).
   - Verify `AddBlockToSessionSheet` opens on `Saved Blocks`, existing block(s) show `"Already in session"` and are disabled/uncounted, and selecting an unloaded saved block updates CTA to `"Add 1 Block"`.
   - Confirm: newly added `SimplifiedBlockLogCard` appears, scrolls smoothly into view, displays `"Pre-filled from last session ({Date})"` when prior history exists (while original session blocks keep it hidden), and `Update session` persists all blocks cleanly.
2. **Edit / Log Session — Create Custom Block (Session-Only vs Save to Library)**:
   - Open `+ Add Block`, switch to `Custom Block` (or observe `Custom Block` selected automatically when no unloaded saved blocks remain).
   - Select 1+ exercises (or type a Block Name with 0 chips selected), leave `Save to Available Blocks library` OFF, and tap `Add Block`.
   - Verify a `SimplifiedBlockLogCard` with 3 empty weight tiles (`Set 1..3`) is appended and scrolled into view, and Library's Available Blocks count stays unchanged.
   - Repeat with `Save to Available Blocks library` ON and verify the new block also persists in Library's `Available Blocks` tab.
3. **Mixed Session Residual Removal & Dirty Back Guard**:
   - In `Edit Session` with both a simplified block and 1 residual legacy block, verify `🗑` on the residual block is enabled.
   - After adding a block, tap `←` / system Back and verify `"Discard unsaved changes?"` appears.
