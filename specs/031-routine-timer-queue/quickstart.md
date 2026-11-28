# Quickstart & Verification Guide: Routine-Aware Workout Timer Queue (`SPEC-031`)

## Local Automated Verification

Run the full pre-PR verification suite from the repository root (`c:\Users\rogal\workspaces\ws-gym\crosstrainingapp`):

```powershell
.\gradlew.bat testDebugUnitTest --no-daemon; .\scripts\tests\Invoke-ScriptTests.ps1; .\gradlew.bat lintDebug --no-daemon
```

Build and sync local test APK:

```powershell
.\gradlew.bat assembleDebug --no-daemon
Copy-Item app\build\outputs\apk\debug\app-debug.apk local_test\latest.apk -Force
```

## Manual Gym-Floor Walkthrough Checklist

1. **Daily Routine Queue Launch (Any Block)**:
   - Open `Log` → tap `Load Daily Routine` → pick a routine with 3+ blocks/exercises.
   - Tap the Timer icon on Block 2.
   - Verify `InSessionTimerSheet` opens with Block 2 selected in the horizontal Block Anchor Strip, starting configurator pre-filled as `E3MOM × {sets}`, and Blocks 3..N queued with `1:00 Transition` connectors.
   - Switch Block 2 to `Time Cap 10m`, verify queue updates total estimated time, and tap `START QUEUE`.
2. **Early Finish & Transition Progression**:
   - Tap `Next Stage` to finish `Time Cap` early → verify `TRANSITION` starts at `01:00` showing `Up Next: <Block 3>`, and `Log Session` highlights Block 3 with `Up Next`.
   - Let transition count down or tap `Start Now` → verify Block 3 starts `E3MOM` immediately with zero prep countdown.
3. **Top-Bar Timer & Keyboard Focus Preservation**:
   - Minimize sheet to docked `InSessionTimerBar`, tap into a weight input on Block 3, and tap Pause/Play on `InSessionTimerBar` → verify keyboard focus remains inside the weight input.
   - On a clean empty `Log Session`, tap the Top-Bar Timer icon → verify it opens standalone `TimerScreen`.
4. **Live Queue Customization & Conflict Handling**:
   - Expand `Remaining Queue` in active sheet, edit an upcoming stage, remove another, confirm `Log Session` cards remain untouched.
   - Tap Timer icon on a different block while running → confirm `Rebuild Queue` replaces timer atomically.
