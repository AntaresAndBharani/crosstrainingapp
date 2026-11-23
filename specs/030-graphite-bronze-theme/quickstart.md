# Quickstart Validation Guide: Graphite, Titanium & Warm Bronze Theme

## Prerequisites

- Android SDK / JDK 17 configured locally
- PowerShell 7 (`pwsh`) for repository script regression tests

## Automated Verification Suite

Run all required quality gates from the repository root (`c:\Users\rogal\workspaces\ws-gym\crosstrainingapp`):

```powershell
# 1. Run JVM unit tests (includes ThemeModeTest WCAG checks, WeightProgressionCardTest, VoiceWorkoutIngestionSheetTest)
.\gradlew.bat testDebugUnitTest --no-daemon

# 2. Run PowerShell script & CI contract tests
.\scripts\tests\Invoke-ScriptTests.ps1

# 3. Run Android Lint checks
.\gradlew.bat lintDebug --no-daemon

# 4. Assemble debug APK and sync to local_test/latest.apk
.\gradlew.bat assembleDebug --no-daemon
Copy-Item app\build\outputs\apk\debug\app-debug.apk local_test\latest.apk -Force
```

## Static Hygiene Checks

Confirm zero leftover hardcoded screen colors, OS-theme leaks, or neon `#00E676` resource definitions:

```powershell
# Should return 0 matches outside ui/theme/Color.kt:
Get-ChildItem -Path app\src\main\java -Recurse -Include *.kt | Where-Object { $_.Name -ne 'Color.kt' } | Select-String -Pattern 'Color\(0x'

# Should return 0 matches:
Get-ChildItem -Path app\src\main\java -Recurse -Include *.kt | Select-String -Pattern 'isSystemInDarkTheme\('

# Should return 0 matches:
Get-ChildItem -Path app\src\main\res -Recurse -Include *.xml | Select-String -Pattern '#00E676'
```

## Manual Inspection Walkthrough (All 4 Modes)

1. Fresh launch -> verifies Dark Graphite (`#101012` + `#D49B6A` Warm Bronze) without green status bar flash.
2. Profile -> Appearance -> switch between `Dark`, `Light`, `Dark High Contrast`, and `Light High Contrast`; confirm preview card swatches match active UI and system bar icon tint adapts cleanly.
3. Log Session / Compact Complex -> confirm warm-up pills use Burnished Ochre, weight-linked states use Muted Sage, and skipped/active pills respect the active in-app theme even when OS dark mode disagrees.
4. Progress & Timer -> confirm subcycle pills, momentum badges, `MultiLineChart` series, and `WORK` / `REST` / `PREP` / `TRANSITION` timer rings follow the unified Graphite, Titanium & Warm Bronze system.
