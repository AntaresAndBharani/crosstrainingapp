# Quickstart & Validation Guide: Persistent Active Workout Draft (`SPEC-032`)

**Feature**: `032-persistent-workout-draft`  
**Branch**: `feat/032-persistent-workout-draft`

## 1. Automated Verification Commands

Run the full pre-PR verification suite from the repository root (`c:\Users\rogal\workspaces\ws-gym\crosstrainingapp`):

```powershell
.\gradlew.bat testDebugUnitTest --no-daemon
.\scripts\tests\Invoke-ScriptTests.ps1
.\gradlew.bat lintDebug --no-daemon
.\gradlew.bat assembleDebug --no-daemon
Copy-Item app\build\outputs\apk\debug\app-debug.apk local_test\latest.apk -Force
```

## 2. Targeted Unit Test Suites

- `ActiveWorkoutDraftCodecTest` — JSON round-trip of full session state (routine metadata, complexes, standalone metrics, skips, PR overrides, legacy blocks, scroll offset), corrupt payload recovery, `isMeaningfullyActive()`, and `toSummary()` stale/counts logic.
- `ActiveWorkoutDraftPersistenceTest` — `AppViewModel` + `DataModeManager` debounce, immediate flush, simulated cold restart restoration, empty-draft suppression, `Discard`, `Save`, `Use Today`, and `Real` vs `Demo` mode isolation.
- `LiveSessionDockResolverTest` — Exhaustive matrix verification of `LiveDockMode` across every `(route × workoutActive × timerActive)` combination, `showInnerEditorTimerBar` Edit-Session suppression, badge visibility, atomic TalkBack descriptions, and `shouldStopTimerOnDraftReset` rules.

## 3. Manual / Device Sanity Scenarios (Optional User Verification)

1. On `Log`, load a Daily Routine, fill in 2 sets, scroll down, switch to `History` -> `Progress` -> `Timer` -> `Log`: confirm exact inputs and scroll position remain, and only 1 docked bar appears at a time.
2. Switch to Coach view in the Drawer while a workout is active: confirm the `"Active"` pill appears in the Drawer and the Live Session Dock returns to `Log` in 1 tap.
3. Force-close the app and relaunch: confirm the active workout rehydrates identically.
4. Open `⋮ -> Discard workout` and confirm: verify draft clears and block-linked timer stops while standalone timers stay running.
