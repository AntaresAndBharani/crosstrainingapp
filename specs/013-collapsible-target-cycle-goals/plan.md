# Implementation Plan: Collapsible & Target-Only Movement Goal Cards

**Branch**: `feat/013-collapsible-target-cycle-goals` | **Date**: 2026-11-02 | **Spec**: [spec.md](spec.md)
**Input**: Feature specification from `/specs/013-collapsible-target-cycle-goals/spec.md`

## Summary

Redesign the Basic Movement Goals workflow in `CycleEditorDialog` (`SPEC-CYCLE-GOAL-04`) to:
1. Remove the redundant `Start (<unit>)` input field and `+ →` connector from the movement goal editor card, replacing them with a single full-width `Target (<unit>)` input field (`Target (kg)`, `Target (cal)`, `Target (reps)`) and a single horizontal row of 3 direct additive/subtractive quick-stepper pills (`-2.5 kg` / `+2.5 kg` / `+5 kg` for Weight; `-5 cal` / `+5 cal` / `+10 cal` for Calories; `-1 rep` / `+1 rep` / `+5 reps` for Reps) clamped at a minimum floor of `0`.
2. Enforce a single non-wrapping horizontal row for the 3 `Goal Metric:` pills (`Weight (kg)`, `Calories (cal)`, `Reps`).
3. Replace permanently expanded goal cards with an **Exclusive Single-Expand Accordion** featuring a compact **Collapsed Summary Card** (`Goal N: <Movement Name>`, `<Context Badge>`, `<Target Badge>`, `🗑`, `▾`) for saved/configured goals and an **Expanded Edit Card** (with `Done ▲` header button, auto-collapse on `+ Movement`, auto-collapse on switching cards, and inline validation when `Target <= 0`).

## Technical Context

**Language/Version**: Kotlin (JVM 17), Android SDK 35 (`minSdk 26`)
**Primary Dependencies**: Jetpack Compose (Material 3, Foundation, Animation), AndroidX Lifecycle, Room SQLite
**Storage**: Room SQLite (`CycleGoal` entity — zero schema migration required; backward-compatible `startWeight = 0.0` default with dynamic baseline resolution in `ProgressScreen`)
**Testing**: JUnit 4 (`CyclesScreenTest.kt` and full `testDebugUnitTest` suite), PowerShell Pester/static script tests (`Invoke-ScriptTests.ps1`), Android Lint (`lintDebug`)
**Target Platform**: Android (`com.fractanomics.crosstraining`)
**Project Type**: Mobile application (Android Jetpack Compose MVVM)
**Performance Goals**: Instant UI state transitions, smooth accordion animations (`AnimatedVisibility` / state toggle), zero main-thread blocking
**Constraints**: Must render cleanly without wrapping `Goal Metric:` pills or clipping collapsed summary badges/icons on `360dp`-wide viewports up to `fontScale = 1.3f`; must provide merged TalkBack semantics on collapsed cards
**Scale/Scope**: `CyclesScreen.kt`, `CycleGoal.kt` (if summary/helper adjustments needed), `CyclesScreenTest.kt`

## Constitution Check

*GATE: Must pass before Phase 0 research. Re-check after Phase 1 design.*

- **I. MVVM & Unidirectional Data Flow (UDF)**: PASS — `GoalDraftState` and list-level accordion helpers are immutable value types with pure deterministic state transition methods; UI components remain stateless/declarative.
- **II. Pure Deterministic Analytics & Display-Unit Consistency**: PASS — Quick target stepper arithmetic (`stepTarget`) uses `BigDecimal` `HALF_UP` rounding and `.trimmed()` formatting with a strict `0.0` lower bound.
- **III. Test-First & 100% CI Parity (NON-NEGOTIABLE)**: PASS — All `GoalDraftState` stepper, collapse/expand, validation, and accordion list transitions are covered by deterministic JUnit tests in `CyclesScreenTest.kt`.
- **IV. Responsive Mobile UX & Accessibility**: PASS — `Goal Metric:` and quick-stepper rows use `Row` with `Modifier.weight(1f)` and single-line ellipsis to prevent wrapping on `360dp` viewports; collapsed cards expose merged TalkBack semantics (`Modifier.semantics(mergeDescendants = true)`).
- **V. Offline-First & Environment Isolation**: PASS — No Room schema changes or machine-specific configuration edits; 100% backward compatible with existing `CycleGoal` rows and `evaluateCycleGoalProgress`.
- **VI. Agentic Collaboration, Automated PR Review & Self-Healing Governance**: PASS — No workflow regressions; full pre-PR verification suite passes before pushing.

## Project Structure

### Documentation (this feature)

```text
specs/013-collapsible-target-cycle-goals/
├── plan.md              # This file
├── research.md          # Phase 0 output
├── data-model.md        # Phase 1 output
├── quickstart.md        # Phase 1 output
├── checklists/
│   ├── requirements.md
│   └── ux-and-accordion-state.md
├── contracts/
│   └── ui-collapsible-cycle-goal-card-contract.md
└── tasks.md             # Phase 2 output (/speckit-tasks)
```

### Source Code (repository root)

```text
app/src/main/java/com/fractanomics/crosstraining/
├── data/model/
│   └── CycleGoal.kt
└── ui/screens/
    ├── CyclesScreen.kt
    └── ProgressScreen.kt

app/src/test/java/com/fractanomics/crosstraining/
└── ui/screens/
    └── CyclesScreenTest.kt
```

**Structure Decision**: Existing single-module Android MVVM architecture (`app/src/main` and `app/src/test`). All state model extensions (`GoalDraftState`, accordion list transition helpers) and Compose UI updates (`CollapsedGoalSummaryCard`, expanded `CycleGoalEditorCard`) reside in `CyclesScreen.kt` with comprehensive unit test coverage in `CyclesScreenTest.kt`.

## Complexity Tracking

No constitution violations. Zero database migrations required.
