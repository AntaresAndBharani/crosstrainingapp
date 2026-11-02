# Quickstart & Validation Guide: Adaptive Metric Cycle Goals

## Prerequisites
- JDK 17 configured in `~/.gradle/gradle.properties`
- Android SDK 35 installed
- Active branch: `feat/011-adaptive-cycle-goals`

## 1. Run Unit Tests
Verify all domain models, `CycleGoal` metadata encoding/decoding, `GoalDraftState` transitions (`Weight`, `Calories`, `Reps`), historical auto-fill, and `ProgressScreen` goal tracking:
```powershell
.\gradlew.bat testDebugUnitTest --no-daemon
```

## 2. Run PowerShell Governance & Workflow Tests
```powershell
.\scripts\tests\Invoke-ScriptTests.ps1
```

## 3. Run Android Lint & Build Debug APK
```powershell
.\gradlew.bat lintDebug assembleDebug --no-daemon
Copy-Item app\build\outputs\apk\debug\app-debug.apk local_test\latest.apk -Force
```

## 4. End-to-End Verification Scenarios
1. **Weight Goal Card**:
   - Open **Training Cycles** → **Edit** (or **+** in Coach Mode) → tap **+ Add Movement**.
   - Select **Back Squat** via the searchable `Movement (Basic)` selector.
   - Confirm `Goal Metric: Weight (kg)` is highlighted, `Reps (RM)` stepper (`-`, `1 RM`, `+`) and presets (`1`, `3`, `5`, `10`) are visible, `Start (kg)` and `Target (kg)` auto-fill from the best RM, and `Scale: +2.5%, +5%, +10%` scales `Target (kg)`.
2. **Calories Goal Card**:
   - Tap **+ Add Movement** and select **Air Bike**.
   - Confirm `Goal Metric: Calories (cal)` is highlighted, `Time Window` pills (`1 min`, `3 min`, `5 min`, `Open`) are displayed, `Start (cal)` auto-fills from the all-time best logged Air Bike set, and `Scale: +5 cal, +10 cal, +20 cal` increments `Target (cal)`.
3. **Reps Goal Card**:
   - Tap **+ Add Movement** and select **Pull-ups** (or switch `Goal Metric:` to `Reps`).
   - Confirm `Time Window` pills (`1 min`, `3 min`, `5 min`, `Open`) are displayed, `Start (reps)` auto-fills from best logged Pull-ups set, and `Scale: +2 reps, +5 reps, +10 reps` increments `Target (reps)`.
4. **Progress & Goals Compatibility**:
   - Save the cycle and open **Progress & Goals** → **Cycle goals**, **By exercise**, and **By routine**.
   - Confirm all three goal types render with their proper unit and badge (`5RM`, `5 min`, `Open`) and `By exercise` / `By routine` remain 100% functional.
