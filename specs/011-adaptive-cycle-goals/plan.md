# Implementation Plan: Adaptive Metric Cycle Goals

**Branch**: `feat/011-adaptive-cycle-goals` | **Date**: 2026-11-02 | **Spec**: [spec.md](spec.md)
**Input**: Feature specification from `/specs/011-adaptive-cycle-goals/spec.md`

## Summary

Redesign the Basic Movement Goals creation and editing experience in `CycleEditorDialog` (`CyclesScreen.kt`) to match the adaptive metric specification (`Weight (kg)`, `Calories (cal)`, and `Reps`), complete with a searchable `Movement (Basic)` selector (`Change ▾`), contextual `Reps (RM)` stepper + quick presets (`1`, `3`, `5`, `10`) for weight goals, contextual `Time Window` pills (`1 min`, `3 min`, `5 min`, `Open`) for calorie and rep goals, historical best auto-fill across all three metrics, and adaptive `Scale:` increment chips (`+2.5%, +5%, +10%` for weight; `+5, +10, +20 cal` for calories; `+2, +5, +10 reps` for reps). Maintain 100% backward compatibility across `CycleGoal` persistence, Cloud Sync, `CycleCard`, and `Progress & Goals` (`Cycle goals`, `By exercise`, `By routine`).

## Technical Context

**Language/Version**: Kotlin (JVM 17)
**Primary Dependencies**: Jetpack Compose (Material 3), AndroidX Room 2.6.x, Coroutines/StateFlow
**Storage**: Room SQLite (`AppDatabase` v10 — backward-compatible encoding in `cycle_goals` without schema version bump)
**Testing**: JUnit 4 (`testDebugUnitTest`), Robolectric / Compose UI tests, PowerShell Pester/static tests (`Invoke-ScriptTests.ps1`)
**Target Platform**: Android (`compileSdk 35`, `minSdk 26`)
**Project Type**: Mobile App (Android)
**Performance Goals**: Instant UI state transitions (`<16ms` frame budget) and asynchronous non-blocking baseline lookup
**Constraints**: 100% backward compatibility with existing `CycleGoal` rows, `UserCloudSyncManager` Firestore payloads, `RoomSchemaExportTest` (v10), and `By exercise` / `By routine` analytics
**Scale/Scope**: `CycleGoal.kt`, `Repository.kt`, `AppViewModel.kt`, `CyclesScreen.kt`, `ProgressScreen.kt`, and unit tests (`CyclesScreenTest.kt`, `ProgressScreenTrendVerificationTest.kt`)

## Constitution Check

*GATE: Must pass before Phase 0 research. Re-check after Phase 1 design.*

- **I. MVVM & Unidirectional Data Flow (UDF)**: PASS — `CyclesScreen` and `ProgressScreen` collect immutable `StateFlow` from `AppViewModel`; `GoalDraftState` transitions are pure functions.
- **II. Pure Deterministic Analytics & Display-Unit Consistency**: PASS — Percentage scaling (`scaleRepMax` with `BigDecimal` `HALF_UP` to nearest `0.5`) and flat additive scaling (`+5/+10/+20 cal`, `+2/+5/+10 reps`) are pure deterministic functions.
- **III. Test-First & 100% CI Parity (NON-NEGOTIABLE)**: PASS — Covered by deterministic unit tests in `CyclesScreenTest.kt` and `ProgressScreen` tests, verified via `testDebugUnitTest`, `Invoke-ScriptTests.ps1`, and `lintDebug`.
- **IV. Responsive Mobile UX & Accessibility**: PASS — Goal cards use responsive rows/chips with accessible content descriptions for steppers, preset pills, and delete actions.
- **V. Offline-First & Environment Isolation**: PASS — Uses existing `cycle_goals` Room table at schema version 10 without unnecessary schema migration, preserving offline-first and cloud sync parity.
- **VI. Agentic Collaboration, Automated PR Review & Self-Healing Governance**: PASS — No workflow modifications required; all existing CI workflow tests remain green.

## Project Structure

### Documentation (this feature)

```text
specs/011-adaptive-cycle-goals/
├── plan.md              # This file
├── research.md          # Phase 0 output
├── data-model.md        # Phase 1 output
├── quickstart.md        # Phase 1 output
├── contracts/
│   └── ui-cycle-goal-card-contract.md
├── checklists/
│   ├── requirements.md
│   └── ux-and-backward-compatibility.md
└── tasks.md             # Phase 2 output (/speckit-tasks)
```

### Source Code (repository root)

```text
app/src/main/java/com/fractanomics/crosstraining/
├── data/
│   ├── Repository.kt
│   └── model/
│       └── CycleGoal.kt
└── ui/
    ├── AppViewModel.kt
    └── screens/
        ├── CyclesScreen.kt
        └── ProgressScreen.kt

app/src/test/java/com/fractanomics/crosstraining/
└── ui/screens/
    └── CyclesScreenTest.kt
```

**Structure Decision**: Single-module Android MVVM architecture under `app/src/main` and `app/src/test`.

## Complexity Tracking

No constitution violations. Zero schema migrations required.
