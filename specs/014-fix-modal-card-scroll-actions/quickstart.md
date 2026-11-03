# Quickstart Validation Guide: Modal Card Auto-Scroll & Non-Blocking Accordion Actions

## Prerequisites
- JDK 17 configured for Gradle (`.\gradlew.bat`).
- PowerShell (`pwsh` or Windows PowerShell) for script regression tests.

## 1. Run Unit Tests (`CyclesScreenTest` & Full Suite)
```powershell
.\gradlew.bat testDebugUnitTest --no-daemon
```
**Expected Outcome**:
- All unit tests pass (`100%` green), including new tests in `CyclesScreenTest` verifying:
  - Non-blocking `expandGoalDraftAt` when an untouched/empty newly added draft (`Goal 3`) is open after adding two goals (`Goal 1` and `Goal 2`).
  - Restoring `lastValidTargetWeight` when switching away from an existing goal whose target was cleared.
  - Deterministic `resolveGoalCardScrollOffset` calculations for bottom-clipped, top-clipped, taller-than-viewport, and already-visible cards.

## 2. Run PowerShell Script Regression Tests & Android Lint
```powershell
.\scripts\tests\Invoke-ScriptTests.ps1
.\gradlew.bat lintDebug --no-daemon
```
**Expected Outcome**:
- 100% passing script tests and zero lint errors.

## 3. Manual / Emulator E2E Verification
1. Open **Training Cycles** in Coach Mode and tap **Edit** on a cycle (e.g., `Fat Loss & BB`).
2. Scroll down to **Basic Movement Goals** and add two goals (`Goal 1: Front Squat, 3RM, 65 kg` and `Goal 2: Snatch, 2RM, 40 kg`), collapsing both.
3. Tap **+ Add Movement**:
   - Verify the dialog automatically scrolls down so `Goal 3` is visible in the viewport.
4. Without typing a target in `Goal 3`, scroll up and tap `▾` on `Goal 1` (or `Goal 2`):
   - Verify the empty `Goal 3` draft is automatically discarded, `Goal 1` immediately expands, and the dialog scrolls `Goal 1`'s edit controls into view.
5. Open **Library** → **Routines** → **New Daily Routine** and tap **Add Block to Routine**:
   - Verify the dialog automatically scrolls down to reveal `Block 2`.
