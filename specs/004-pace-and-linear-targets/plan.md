# Implementation Plan: Required-Pace & Linear Cycle Weight Targets for 7D Average and Daily Weight

**Branch**: `feat/004-pace-and-linear-targets` | **Date**: 2026-10-22 | **Spec**: [spec.md](file:///c:/Users/rogal/workspaces/ws-gym/crosstrainingapp/specs/004-pace-and-linear-targets/spec.md)

**Input**: Feature specification from `/specs/004-pace-and-linear-targets/spec.md`

## Summary

1. **Domain Calculation (`FatLossAnalytics.kt`)**:
   - Add 2-decimal precision helpers (`round2`, `round2InUnit`, `diff2`) to ensure sub-`0.1` daily required steps are preserved without rounding to zero.
   - Update `PaceTargetsState` (`Unavailable`, `GoalReached`, `CycleEnded`, `Available(sevenDayAvgRow, dailyWeightRow)`), removing the legacy `Stalled` blocker and introducing `TargetRowState(linearToday, expectedToday, targetTomorrow)` and `ColumnState.NeedsTodayLog`.
   - Update `compute7DayPaceTargets` (and provide `computePaceTargets` alias/overloads) to compute:
     - **Exact Required Pace**: `dailyRequiredRateInUnit = -(toUnit(today7dAvgKg) - toUnit(targetWeightKg)) / daysRemaining`, `weeklyRequiredRateInUnit = dailyRequiredRateInUnit * 7.0`.
     - **`Linear Today`**: `round2(startInUnit + (targetInUnit - startInUnit) * (elapsedDays.toDouble() / totalCycleDays.toDouble()))`, compared against both `today7dAvgInUnit` and `todayDailyInUnit`.
     - **`Expected Today`**: `round2(lastWeek7dAvgInUnit + weeklyRequiredRateInUnit)` for the 7D Average row and `round2(tMinus7DailyInUnit + weeklyRequiredRateInUnit)` for the Daily Weight row, compared against today's actual 7D Average and today's raw weigh-in.
     - **`Target Tomorrow`**: `round2(today7dAvgInUnit + dailyRequiredRateInUnit)` for the 7D Average row and `round2(todayDailyInUnit + dailyRequiredRateInUnit)` for the Daily Weight row (or `NeedsTodayLog` when today is unlogged), with daily step subtitle `"-X.XX <unit>/day"`.
2. **UI Container Redesign (`ProgressScreen.kt`)**:
   - Update `TheoreticalPaceTargetsBox` to render the unified `"PACE & LINEAR TARGETS"` container with two labeled rows (`"7D ROLLING AVERAGE"` and `"DAILY WEIGH-IN"`), each displaying 3 columns (`Linear Today`, `Expected Today`, `Target Tomorrow`) formatted to 2 decimal places (`%.2f`) with status/step subtitles and merged accessibility semantics.
3. **End-to-End Verification**:
   - Update and expand unit tests in `FatLossAnalyticsTest.kt` and `WeightProgressionCardTest.kt`, and Compose UI tests in `WeightProgressionCardComposeTest.kt`, verifying 2-decimal precision, `kg` and `lbs` localization, unlogged-today / missing-`t-7d` fallbacks, boundary states (`Unavailable`, `GoalReached`, `CycleEnded`), and `360dp` / `fontScale = 1.3f` layout rendering.

## Technical Context

**Language/Version**: Kotlin 1.9+ (JVM 17)

**Primary Dependencies**: Jetpack Compose (Material 3), AndroidX Lifecycle (`collectAsStateWithLifecycle`), Java Time (`java.time.LocalDate`, `java.time.temporal.ChronoUnit`, `DateTimeFormatter`), JUnit 4

**Storage**: Room SQLite (`Cycle.startDate`, `Cycle.endDate`, `Cycle.startingWeightKg`, `Cycle.targetWeightKg`, `Cycle.targetBodyWeightKg`, `WeightEntry` — existing fields only, zero schema changes or migrations)

**Testing**: JUnit 4 (`.\gradlew.bat testDebugUnitTest --no-daemon`), Compose UI Test (`WeightProgressionCardComposeTest`), PowerShell Script Tests (`.\scripts\tests\Invoke-ScriptTests.ps1`), Android Lint (`.\gradlew.bat lintDebug --no-daemon`)

**Target Platform**: Android (compileSdk 35, minSdk 26)

**Project Type**: Mobile application (Android Jetpack Compose MVVM with Unidirectional Data Flow)

**Performance Goals**: Pure $O(N)$ window filtering over active weight entries (`< 0.2\text{ms}` on UI thread inside Compose `remember`).

**Constraints**: Offline-first, deterministic pure functions (`referenceDate` / `today` injected), strict 2-decimal display-unit arithmetic consistency (`diff2(round2(actualInUnit), round2(targetInUnit))`), zero visual overflow on `360dp` screens at `fontScale = 1.3f`.

**Scale/Scope**: Touches 2 production source files ([`FatLossAnalytics.kt`](file:///c:/Users/rogal/workspaces/ws-gym/crosstrainingapp/app/src/main/java/com/fractanomics/crosstraining/data/analytics/FatLossAnalytics.kt), [`ProgressScreen.kt`](file:///c:/Users/rogal/workspaces/ws-gym/crosstrainingapp/app/src/main/java/com/fractanomics/crosstraining/ui/screens/ProgressScreen.kt)) and 3 test files ([`FatLossAnalyticsTest.kt`](file:///c:/Users/rogal/workspaces/ws-gym/crosstrainingapp/app/src/test/java/com/fractanomics/crosstraining/data/analytics/FatLossAnalyticsTest.kt), [`WeightProgressionCardTest.kt`](file:///c:/Users/rogal/workspaces/ws-gym/crosstrainingapp/app/src/test/java/com/fractanomics/crosstraining/ui/screens/WeightProgressionCardTest.kt), [`WeightProgressionCardComposeTest.kt`](file:///c:/Users/rogal/workspaces/ws-gym/crosstraining/ui/screens/WeightProgressionCardComposeTest.kt)).

## Constitution Check

*GATE: Must pass before Phase 0 research. Re-check after Phase 1 design.*

- **MVVM & Unidirectional Data Flow (`GEMINI.md` Guideline 1 & 2)**: **PASS** — All target calculations are pure, stateless functions in `FatLossAnalytics` and UI string resolvers in `ProgressScreen`.
- **Testing & CI Parity (`GEMINI.md` Guideline 3)**: **PASS** — Unit and Compose tests are updated and expanded in lockstep and verified with the full pre-PR verification suite.
- **Zero Database Migration Risk**: **PASS** — Uses existing `Cycle` and `WeightEntry` columns with zero Room schema changes.
- **Post-Phase 1 Re-Check**: **PASS** — Contracts in `contracts/analytics-contract.md` and `data-model.md` preserve clean state modeling and full testability.

## Project Structure

### Documentation (this feature)

```text
specs/004-pace-and-linear-targets/
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
│   │   └── FatLossAnalytics.kt      # PaceTargetsState, TargetRowState, ColumnState, compute7DayPaceTargets
│   └── ui/screens/
│       └── ProgressScreen.kt        # TheoreticalPaceTargetsBox & 2-decimal UI resolvers
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
