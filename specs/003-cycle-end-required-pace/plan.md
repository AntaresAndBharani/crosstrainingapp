# Implementation Plan: Cycle End-Date Required Pace in Goal Estimation Banner

**Branch**: `003-cycle-end-required-pace` | **Date**: 2026-10-22 | **Spec**: [spec.md](file:///c:/Users/rogal/workspaces/ws-gym/crosstrainingapp/specs/003-cycle-end-required-pace/spec.md)

**Input**: Feature specification from `/specs/003-cycle-end-required-pace/spec.md`

## Summary

1. **Domain Calculation (`FatLossAnalytics.kt`)**: Add `RequiredPaceResult` and `FatLossAnalytics.computeRequiredPaceToEndDate` to compute the signed weekly weight-loss rate required to reach `targetWeightKg` by `cycle.endDate` from the 1-decimal rounded 7-day rolling average (`today7dAvgKg`) in the active display unit (`kg` or `lbs`), dividing by exact remaining calendar weeks (`ChronoUnit.DAYS.between(today, cycleEndDate) / 7.0`) and clamping the loss rate to at least `-0.1` per week whenever remaining distance is positive.
2. **UI Banner Integration (`ProgressScreen.kt`)**: Add `resolveRequiredPaceSubtitle` and wire `cycleEndDate = cycle.endDate` through `WeightProgressionCard` -> `WeightProgressionHeroCard` -> `ForecastBanner` to append `"Required pace: -X.X <kg/wk|lbs/wk> (by <d MMM yyyy>)"` below existing banner lines when `forecast` is `Projected`, `Stalled`, or `NotEnoughData`.
3. **End-to-End Verification**: Add comprehensive unit and Compose UI tests in `FatLossAnalyticsTest.kt`, `WeightProgressionCardTest.kt`, and `WeightProgressionCardComposeTest.kt` covering `Projected`, `Stalled`, `NotEnoughData`, `GoalReached`, `CycleEnded`, `today == endDate`, `endDate == null`, `-0.1` floor clamping, `lbs/wk` conversion, and `360dp` / `fontScale = 1.3f` layout rendering.

## Technical Context

**Language/Version**: Kotlin 1.9+ (JVM 17)

**Primary Dependencies**: Jetpack Compose (Material 3), AndroidX Lifecycle (`collectAsStateWithLifecycle`), Java Time (`java.time.LocalDate`, `java.time.temporal.ChronoUnit`, `DateTimeFormatter`), JUnit 4

**Storage**: Room SQLite (`Cycle.endDate`, `Cycle.targetWeightKg`, `Cycle.startingWeightKg`, `WeightEntry` — existing fields only, zero schema changes or migrations)

**Testing**: JUnit 4 (`.\gradlew.bat testDebugUnitTest --no-daemon`), Compose UI Test (`WeightProgressionCardComposeTest`), PowerShell Script Tests (`.\scripts\tests\Invoke-ScriptTests.ps1`), Android Lint (`.\gradlew.bat lintDebug --no-daemon`)

**Target Platform**: Android (compileSdk 35, minSdk 26)

**Project Type**: Mobile application (Android Jetpack Compose MVVM with Unidirectional Data Flow)

**Performance Goals**: Pure $O(1)$ arithmetic over pre-resolved 7D average and cycle metadata (`< 0.1\text{ms}` on UI thread inside Compose `remember`).

**Constraints**: Offline-first, deterministic pure functions (`today` / `referenceDate` injected), strict display-unit arithmetic consistency (`remainingDisplay = diff1(round1(toDisplay(today7dAvgKg)), round1(toDisplay(targetWeightKg)))`), zero visual overflow on `360dp` screens at `fontScale = 1.3f`.

**Scale/Scope**: Touches 2 production source files ([`FatLossAnalytics.kt`](file:///c:/Users/rogal/workspaces/ws-gym/crosstrainingapp/app/src/main/java/com/fractanomics/crosstraining/data/analytics/FatLossAnalytics.kt), [`ProgressScreen.kt`](file:///c:/Users/rogal/workspaces/ws-gym/crosstrainingapp/app/src/main/java/com/fractanomics/crosstraining/ui/screens/ProgressScreen.kt)) and 3 test files ([`FatLossAnalyticsTest.kt`](file:///c:/Users/rogal/workspaces/ws-gym/crosstrainingapp/app/src/test/java/com/fractanomics/crosstraining/data/analytics/FatLossAnalyticsTest.kt), [`WeightProgressionCardTest.kt`](file:///c:/Users/rogal/workspaces/ws-gym/crosstrainingapp/app/src/test/java/com/fractanomics/crosstraining/ui/screens/WeightProgressionCardTest.kt), [`WeightProgressionCardComposeTest.kt`](file:///c:/Users/rogal/workspaces/ws-gym/crosstrainingapp/app/src/androidTest/java/com/fractanomics/crosstraining/ui/screens/WeightProgressionCardComposeTest.kt)).

## Constitution Check

*GATE: Must pass before Phase 0 research. Re-check after Phase 1 design.*

- **MVVM & Unidirectional Data Flow (`GEMINI.md` Guideline 1 & 2)**: **PASS** — Required pace is computed via pure, stateless functions in `FatLossAnalytics` and `ProgressScreen`.
- **Testing & CI Parity (`GEMINI.md` Guideline 3)**: **PASS** — Unit and Compose tests are added in lockstep and verified with the full pre-PR verification suite.
- **Zero Database Migration Risk**: **PASS** — Uses existing `Cycle.endDate` and `Cycle.targetWeightKg` columns with zero Room schema changes.
- **Post-Phase 1 Re-Check**: **PASS** — Contracts in `contracts/analytics-contract.md` and `data-model.md` preserve backward compatibility with all existing callers and singleton `PaceForecast` objects.

## Project Structure

### Documentation (this feature)

```text
specs/003-cycle-end-required-pace/
├── spec.md              # Feature specification (/speckit-specify output)
├── plan.md              # This file (/speckit-plan output)
├── research.md          # Phase 0 output (/speckit-plan)
├── data-model.md        # Phase 1 output (/speckit-plan)
├── quickstart.md        # Phase 1 output (/speckit-plan)
├── checklists/
│   └── requirements.md  # Specification quality validation checklist
├── contracts/           # Phase 1 output (/speckit-plan)
│   └── analytics-contract.md
└── tasks.md             # Phase 2 output (/speckit-tasks)
```

### Source Code (repository root)

```text
app/src/
├── main/java/com/fractanomics/crosstraining/
│   ├── data/analytics/
│   │   └── FatLossAnalytics.kt      # RequiredPaceResult & computeRequiredPaceToEndDate
│   └── ui/screens/
│       └── ProgressScreen.kt        # resolveRequiredPaceSubtitle & ForecastBanner rendering
├── test/java/com/fractanomics/crosstraining/
│   ├── data/analytics/
│   │   └── FatLossAnalyticsTest.kt
│   └── ui/screens/
│       └── WeightProgressionCardTest.kt
└── androidTest/java/com/fractanomics/crosstraining/ui/screens/
    └── WeightProgressionCardComposeTest.kt
```

**Structure Decision**: Existing single-module Android application architecture (`app/src/main`, `app/src/test`, and `app/src/androidTest`). Domain math resides in `com.fractanomics.crosstraining.data.analytics.FatLossAnalytics` and UI presentation resides in `com.fractanomics.crosstraining.ui.screens.ProgressScreen`.

## Complexity Tracking

No constitution violations or structural complexity exceptions required.
