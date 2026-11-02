# Implementation Plan: Cycle Movement Goal Search Keyboard & Filtering

**Branch**: `feat/012-fix-movement-search-keyboard` | **Date**: 2026-11-02 | **Spec**: [spec.md](spec.md)
**Input**: Feature specification from `/specs/012-fix-movement-search-keyboard/spec.md`

## Summary

Fix the missing soft keyboard bug in the New/Edit Cycle modal's `Movement (Basic)` selector (`MovementGoalSelector` in `CyclesScreen.kt`). Previously, the `"Search movement…"` `OutlinedTextField` was placed inside a Material 3 `ExposedDropdownMenu` with a `MenuAnchorType.PrimaryNotEditable` anchor, which caused Compose Material 3 to set `WindowManager.LayoutParams.FLAG_ALT_FOCUSABLE_IM` on the popup window—allowing the text field to draw a focus border and cursor while blocking Android's `InputMethodManager` from ever showing the soft keyboard. Replace the nested `ExposedDropdownMenu` popup with an inline expandable search surface inside the dialog's IME-enabled window hierarchy, auto-request focus and show the soft keyboard via `FocusRequester` + `LocalSoftwareKeyboardController`, add a one-tap `"Clear search"` button and `"No matching movements found"` empty state, and extract deterministic `MovementSelectorUiState` + `filterMovementExercises` helpers covered by unit tests in `CyclesScreenTest.kt`.

## Technical Context

**Language/Version**: Kotlin (JVM 17)
**Primary Dependencies**: Jetpack Compose (Material 3, `FocusRequester`, `LocalSoftwareKeyboardController`), Coroutines/StateFlow
**Storage**: N/A (UI state and in-memory filtering only; zero database or schema changes)
**Testing**: JUnit 4 (`testDebugUnitTest` via `CyclesScreenTest.kt`), PowerShell static/regression suite (`Invoke-ScriptTests.ps1`), Android Lint (`lintDebug`)
**Target Platform**: Android (`compileSdk 35`, `minSdk 26`)
**Project Type**: Mobile App (Android)
**Performance Goals**: Instant (`<16ms`) keystroke filtering and immediate soft keyboard presentation on expanding/tapping `"Search movement…"`
**Constraints**: Must operate inside `CycleEditorDialog` (`AlertDialog`) without nested non-IME popup window flags; 100% backward compatible with `GoalDraftState` and `CycleGoal`
**Scale/Scope**: `app/src/main/java/com/fractanomics/crosstraining/ui/screens/CyclesScreen.kt` and `app/src/test/java/com/fractanomics/crosstraining/ui/screens/CyclesScreenTest.kt`

## Constitution Check

*GATE: Must pass before Phase 0 research. Re-check after Phase 1 design.*

- **I. MVVM & Unidirectional Data Flow (UDF)**: PASS — `MovementSelectorUiState` models state transitions as pure immutable updates, and `MovementGoalSelector` emits selected `Exercise` events upward via `onSelect`.
- **II. Pure Deterministic Analytics & Display-Unit Consistency**: PASS — `filterMovementExercises` and `MovementSelectorUiState` are pure, deterministic Kotlin functions with zero side effects.
- **III. Test-First & 100% CI Parity (NON-NEGOTIABLE)**: PASS — State transitions and filtering logic are covered by deterministic unit tests in `CyclesScreenTest.kt`, and verified with `testDebugUnitTest`, `Invoke-ScriptTests.ps1`, and `lintDebug`.
- **IV. Responsive Mobile UX & Accessibility**: PASS — Inline expandable search surface uses bounded height (`heightIn(max = 220.dp)`), accessible content descriptions (`"Search movement"`, `"Clear search"`), and proper keyboard IME action (`ImeAction.Search`).
- **V. Offline-First & Environment Isolation**: PASS — No database schema changes or environment-specific paths.
- **VI. Agentic Collaboration, Automated PR Review & Self-Healing Governance**: PASS — Preserves all CI workflow contracts.

## Project Structure

### Documentation (this feature)

```text
specs/012-fix-movement-search-keyboard/
├── plan.md              # This file
├── research.md          # Phase 0 output
├── data-model.md        # Phase 1 output
├── quickstart.md        # Phase 1 output
├── contracts/
│   └── ui-movement-goal-selector-contract.md
├── checklists/
│   ├── requirements.md
│   └── ux-and-ime-focus.md
└── tasks.md             # Phase 2 output (/speckit-tasks)
```

### Source Code (repository root)

```text
app/src/main/java/com/fractanomics/crosstraining/
└── ui/
    └── screens/
        └── CyclesScreen.kt

app/src/test/java/com/fractanomics/crosstraining/
└── ui/
    └── screens/
        └── CyclesScreenTest.kt
```

**Structure Decision**: Single-module Android MVVM architecture under `app/src/main` and `app/src/test`.

## Complexity Tracking

No constitution violations. Zero database migrations or new dependencies required.
