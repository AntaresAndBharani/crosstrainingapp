# Quickstart & Verification Guide: Continuous Workout Timer (SPEC-023)

## Key Modified & Added Files
- Domain & Engine:
  - `app/src/main/java/com/fractanomics/crosstraining/ui/timer/WorkoutTimer.kt`
  - `app/src/main/java/com/fractanomics/crosstraining/ui/timer/TimerEngine.kt`
  - `app/src/main/java/com/fractanomics/crosstraining/ui/timer/ContinuousTimerUiResolver.kt`
- Service & Notifications:
  - `app/src/main/java/com/fractanomics/crosstraining/ui/timer/TimerTeardownController.kt`
  - `app/src/main/java/com/fractanomics/crosstraining/ui/timer/TimerNotificationFormatter.kt`
  - `app/src/main/java/com/fractanomics/crosstraining/ui/timer/TimerNotificationSpec.kt`
- Compose UI:
  - `app/src/main/java/com/fractanomics/crosstraining/ui/components/ContinuousTimerComponents.kt`
  - `app/src/main/java/com/fractanomics/crosstraining/ui/screens/TimerScreen.kt`
  - `app/src/main/java/com/fractanomics/crosstraining/ui/components/InSessionTimerSheet.kt`
  - `app/src/main/java/com/fractanomics/crosstraining/ui/components/InSessionTimerBar.kt`
  - `app/src/main/java/com/fractanomics/crosstraining/ui/components/SubBlockInlineTimer.kt`
- Unit Tests:
  - `app/src/test/java/com/fractanomics/crosstraining/ui/timer/ContinuousTimerTest.kt`

## Verification Commands
```powershell
.\gradlew.bat testDebugUnitTest --no-daemon
.\gradlew.bat lintDebug assembleDebug --no-daemon
.\scripts\tests\Invoke-ScriptTests.ps1
Copy-Item app\build\outputs\apk\debug\app-debug.apk local_test\latest.apk -Force
```
