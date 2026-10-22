# Implementation Plan: Rolling Window Average Pacing & Goal Forecast Alignment

**Branch**: `001-rolling-weight-pace` | **Date**: 2026-10-22 | **Spec**: [spec.md](file:///c:/Users/rogal/workspaces/ws-gym/crosstrainingapp/specs/001-rolling-weight-pace/spec.md)

**Input**: Feature specification from `/specs/001-rolling-weight-pace/spec.md`

## Summary

Unify the Weight Progression card's **7-Day Pace**, **14-Day Pace**, **Pace Comparison Subtitles**, **Top Reference Track (`% to goal` / `-X.X kg left`)**, and **Goal Forecast (`Estimated: X.X weeks`)** on 7-day rolling window averages with pre-cycle lookback continuity. Previously, while the 3-pillar hero row (`TODAY`, `LAST WEEK`, `14D AVG`) displayed rolling window averages, `compute7DayPace`, `compute14DayPace`, and `computePaceForecast` relied on single-day raw weigh-ins and excluded pre-cycle entries, allowing a single low reading 14 days ago to distort the 14-day pace to `-0.1 kg/wk` and project an unrealistic `36.6 weeks` to goal.

## Technical Context

**Language/Version**: Kotlin 1.9+ (JVM 17)

**Primary Dependencies**: Jetpack Compose (Material 3), AndroidX Lifecycle (`collectAsStateWithLifecycle`), Java Time (`java.time.LocalDate`, `ChronoUnit`), JUnit 4

**Storage**: Room SQLite (`WeightEntry`, `Cycle` entities — no schema changes or migrations required)

**Testing**: JUnit 4 (`.\gradlew.bat testDebugUnitTest --no-daemon`), Compose UI Test (`WeightProgressionCardComposeTest`), PowerShell Pester/Script Tests (`.\scripts\tests\Invoke-ScriptTests.ps1`)

**Target Platform**: Android (compileSdk 35, minSdk 26)

**Project Type**: Mobile application (Android Jetpack Compose MVVM with Unidirectional Data Flow)

**Performance Goals**: Pure $O(N)$ stateless domain evaluation in `FatLossAnalytics` executing in $< 1\text{ms}$ on the UI thread inside Compose `remember` blocks with zero unnecessary heap churn.

**Constraints**: Offline-first, deterministic pure functions (`referenceDate` / `today` injected for testability), strict unit localization parity (`kg` canonical storage/math with `lbs` display conversion).

**Scale/Scope**: Touches 2 production source files (`FatLossAnalytics.kt`, `ProgressScreen.kt`) and 2 unit test suites (`FatLossAnalyticsTest.kt`, `WeightProgressionCardTest.kt`).

## Constitution Check

*GATE: Must pass before Phase 0 research. Re-check after Phase 1 design.*

- **MVVM & Unidirectional Data Flow (`GEMINI.md` Guideline 1 & 2)**: **PASS** — All pace, rolling window, and forecast calculations remain pure, stateless domain functions in `FatLossAnalytics`, consumed declaratively via `remember` in `ProgressScreen.kt`.
- **Testing & CI Parity (`GEMINI.md` Guideline 3)**: **PASS** — Full unit test coverage in `FatLossAnalyticsTest.kt` and `WeightProgressionCardTest.kt` will be updated and verified via `.\gradlew.bat testDebugUnitTest --no-daemon`.
- **Zero Database Migration Risk**: **PASS** — Operates strictly on existing `WeightEntry` and `Cycle` models with zero Room schema changes.
- **Post-Phase 1 Re-Check**: **PASS** — All contracts in `contracts/analytics-contract.md` and `data-model.md` maintain backward compatibility and pure functional determinism.

## Project Structure

### Documentation (this feature)

```text
specs/001-rolling-weight-pace/
├── plan.md              # This file (/speckit-plan command output)
├── research.md          # Phase 0 output (/speckit-plan command)
├── data-model.md        # Phase 1 output (/speckit-plan command)
├── quickstart.md        # Phase 1 output (/speckit-plan command)
├── contracts/           # Phase 1 output (/speckit-plan command)
│   └── analytics-contract.md
└── tasks.md             # Phase 2 output (/speckit-tasks command - NOT created by /speckit-plan)
```

### Source Code (repository root)

```text
app/src/
├── main/java/com/fractanomics/crosstraining/
│   ├── data/
│   │   ├── analytics/
│   │   │   ├── FatLossAnalytics.kt      # Rolling window hero, 7D/14D smoothed pace, pace comparison, tiered forecast
│   │   │   └── WeightAnalytics.kt       # Unit conversion & chart series preparation
│   │   └── model/
│   │       ├── Cycle.kt                 # Training cycle entity
│   │       └── WeightEntry.kt           # Daily body weight entity
│   └── ui/screens/
│       └── ProgressScreen.kt            # WeightProgressionCard & WeightProgressionHeroCard UI
└── test/java/com/fractanomics/crosstraining/
    ├── data/analytics/
    │   └── FatLossAnalyticsTest.kt      # Domain analytics unit test suite
    └── ui/screens/
        └── WeightProgressionCardTest.kt # Card state & formatting unit test suite
```

**Structure Decision**: Existing single-module Android application architecture (`app/src/main` and `app/src/test`). All domain math resides in `com.fractanomics.crosstraining.data.analytics.FatLossAnalytics` and UI binding resides in `com.fractanomics.crosstraining.ui.screens.ProgressScreen`.

## Complexity Tracking

No constitution violations or structural complexity exceptions required.
