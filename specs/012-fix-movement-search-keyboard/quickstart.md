# Quickstart & Validation Guide: Cycle Movement Goal Search Keyboard & Filtering

## Prerequisites

- JDK 17 configured for Gradle (`.\gradlew.bat`).
- PowerShell 7 (`pwsh`) or Windows PowerShell for script verification.

## 1. Run Unit & State Verification Tests

Execute the unit test suite (including `CyclesScreenTest`):

```powershell
.\gradlew.bat testDebugUnitTest --no-daemon
```

**Expected Outcome**:
- All unit tests in `CyclesScreenTest` pass, covering:
  - `MovementSelectorUiState` opening, toggling collapse, typing search queries, clearing search queries, and selecting an exercise.
  - `filterMovementExercises` case-insensitive substring matching, whitespace trimming, blank query handling, and empty match results.
  - Integration with `GoalDraftState.onExerciseChanged` when selecting a filtered movement.

## 2. Run Full Pre-PR Verification Suite & Lint

```powershell
.\gradlew.bat testDebugUnitTest --no-daemon
.\scripts\tests\Invoke-ScriptTests.ps1
.\gradlew.bat lintDebug --no-daemon
```

**Expected Outcome**:
- `BUILD SUCCESSFUL` with zero test failures and zero lint errors.

## 3. Manual / Device Verification Flow

1. Open the app and navigate to the **Cycles** tab.
2. Tap **New Cycle** (or **Edit** on an existing cycle) to open the `Edit cycle` modal.
3. Under **Basic Movement Goals**, tap **+ Add Movement**.
4. Tap the **Movement (Basic)** selector (`[ 🔍 Air Bike   Change ▾ ]`).
5. Verify that:
   - The inline search box (`"Search movement…"`) expands directly beneath the selector row.
   - The `"Search movement…"` input field automatically receives focus and the Android soft keyboard appears at the bottom of the screen.
   - Typing `"squat"` filters the list to `"Air Squat"`, `"Back Squat"`, `"Front Squat"`, `"Overhead Squat"`.
   - Tapping the `"Clear search"` (`✕`) icon clears the text while keeping the picker and keyboard open.
   - Typing `"clean"` and tapping `"Clean & Jerk"` selects `"Clean & Jerk"`, closes the search panel, hides the keyboard, and updates the goal card's metric and baseline values.
