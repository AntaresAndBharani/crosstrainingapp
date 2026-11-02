# Quickstart & Validation Guide: Collapsible & Target-Only Movement Goal Cards (`013-collapsible-target-cycle-goals`)

## Prerequisites
- JDK 17 configured for Gradle (`.\gradlew.bat`).
- PowerShell 7 (`pwsh`) for script regression tests.

## 1. Run Unit Tests (`CyclesScreenTest` & Full Suite)
Execute the unit test suite verifying target-only goal creation, quick target stepper pills (`-2.5/+2.5/+5 kg`, `-5/+5/+10 cal`, `-1/+1/+5 reps`), floor clamping at `0`, collapsed summary formatting, exclusive single-expand accordion transitions, and dirty-state validation on empty/zero `Target`:

```powershell
.\gradlew.bat testDebugUnitTest --no-daemon
```

## 2. Run Script Regression Tests & Android Lint
```powershell
.\scripts\tests\Invoke-ScriptTests.ps1
.\gradlew.bat lintDebug --no-daemon
```

## 3. Manual / E2E UI Verification Flow
1. Open the app in **Coach Mode** and navigate to **Training Cycles**.
2. Tap **Edit** on a cycle with existing movement goals:
   - Verify all existing saved goals load in the **Collapsed Summary View** (`Goal 1: Front Squat`, `3RM` badge, `Target: 65 kg` badge, `🗑`, `▾`).
3. Tap a collapsed goal card:
   - Verify it expands smoothly, showing `Done ▲` in the header, `Movement (Basic)`, single-row `Goal Metric:` (`Weight (kg)` | `Calories (cal)` | `Reps`), `Reps (RM)` stepper/presets, a single full-width `Target (kg)` field (with no `Start` field), and quick stepper pills (`-2.5 kg`, `+2.5 kg`, `+5 kg`).
4. Tap `+2.5 kg` and `+5 kg` to step the target value up, then tap `Done ▲`:
   - Verify the card collapses back into the updated summary card.
5. Tap `+ Movement`:
   - Verify any open valid card auto-collapses and a new expanded goal card appears at the bottom.
   - Clear `Target` and tap `Done ▲`: verify the card stays expanded and shows `"Please enter a target value greater than 0"`.
