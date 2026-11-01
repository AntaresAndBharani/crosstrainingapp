# Implementation Plan: Weighted Average Pace Estimation & Goal Arrival Estimations Redesign

**Branch**: `feat/009-weighted-goal-estimations` | **Date**: 2026-11-01 | **Spec**: [spec.md](file:///c:/Users/rogal/workspaces/ws-gym/crosstrainingapp/specs/009-weighted-goal-estimations/spec.md)

**Input**: Feature specification from `/specs/009-weighted-goal-estimations/spec.md`

## Summary

1. **Domain Analytics (`FatLossAnalytics.kt`)**:
   - Define `HORIZON_WEIGHTS = mapOf(7 to 4, 14 to 3, 21 to 2, 28 to 1)` (`40% 7D`, `30% 14D`, `20% 21D`, `10% 28D` when all 4 horizons are available).
   - Extend `HorizonEstimationRow` with `isWeightedAverage: Boolean = windowDays == 0` and `formatWeightedSummary()` (`"(Weighted pace of -0.39 kg/wk · 7.9 wks · 26 Dec 2026)"`, `"(Weighted pace of -0.05 kg/wk · > 1 year)"`, or `"(Weighted pace of +0.10 kg/wk · Pace stalled)"`), while preserving existing `formatLeftLabel()` and `formatRightValue()` contracts for the 4 historical rows (`7D`, `14D`, `21D`, `28D`).
   - Extend `MultiHorizonForecastState.Active` with `weightedAverageRow: HorizonEstimationRow` computed in `computeMultiHorizonForecast` by taking the dynamically re-normalized `4:3:2:1` weighted average of the 2-decimal signed display-unit rates of all non-null horizons and projecting weeks, arrival date, sufficiency bar fraction, and ahead/late badge using the shared projection rules.
2. **UI Layout & Visual Redesign (`ProgressScreen.kt`)**:
   - Add `CURRENT_GOAL_AND_TARGET_HEADER = "Current Goal & Target"` and `WEIGHTED_AVERAGE_ROW_TITLE = "Weighted Average"`, plus `resolveWeightedRowSummaryText(row)` and updated `resolveHorizonRowContentDescription(row)` supporting both `weightedAverageRow` and historical horizon rows.
   - Redesign `MultiHorizonGoalEstimationsBanner` (`Active` state) to display:
     - Uppercase `GOAL ARRIVAL ESTIMATIONS` overline.
     - Bold `Current Goal & Target` sub-header above `state.benchmarkHeader` (`Target: 28 Dec 2026 (8.1w left) · Required: -0.38 kg/wk`), followed by a horizontal divider.
     - Highlighted primary `WeightedAverageEstimationRowItem` above the `7D` row with a leading trend/sparkle indicator, bold `"Weighted Average"` label + inline summary text, sleek pill-shaped progress track with glowing teal/green on-track accent (or amber late tint), and timing status badge.
     - Subtle horizontal divider separating the primary `Weighted Average` estimation from the 4 historical horizon rows (`7D`, `14D`, `21D`, `28D`).
3. **End-to-End Verification**:
   - Add unit tests in `FatLossAnalyticsTest.kt` and `WeightProgressionCardTest.kt`, and Compose UI verification in `WeightProgressionCardComposeTest.kt`, covering `4:3:2:1` weighted pace math, partial-horizon re-normalization, signed-rate blending, `kg`/`lbs` conversion, TalkBack semantics, and `360dp` / `fontScale = 1.3f` layout rendering.

## Technical Context

**Language/Version**: Kotlin 1.9+ (JVM 17)

**Primary Dependencies**: Jetpack Compose (Material 3), AndroidX Lifecycle (`collectAsStateWithLifecycle`), Java Time (`java.time.LocalDate`, `java.time.temporal.ChronoUnit`, `DateTimeFormatter`), JUnit 4

**Storage**: Room SQLite (`Cycle`, `WeightEntry` — existing fields only, zero schema changes or migrations)

**Testing**: JUnit 4 (`.\gradlew.bat testDebugUnitTest --no-daemon`), Compose UI Test (`WeightProgressionCardComposeTest`), PowerShell Script Tests (`.\scripts\tests\Invoke-ScriptTests.ps1`), Android Lint (`.\gradlew.bat lintDebug --no-daemon`)

**Target Platform**: Android (compileSdk 35, minSdk 26)

**Project Type**: Mobile application (Android Jetpack Compose MVVM with Unidirectional Data Flow)

**Performance Goals**: Pure $O(1)$ weighted combination over the 4 computed horizon paces (`< 0.1\text{ms}` on UI thread inside Compose `remember`).

**Constraints**: Offline-first, deterministic pure functions (`today` / `referenceDate` injected), 2-decimal display-unit arithmetic consistency (`round2`), zero visual overflow on `360dp` screens at `fontScale = 1.3f`, 100% backward compatibility with existing `MultiHorizonForecastState.Active.rows` callers.

**Scale/Scope**: Touches 2 production source files ([`FatLossAnalytics.kt`](file:///c:/Users/rogal/workspaces/ws-gym/crosstrainingapp/app/src/main/java/com/fractanomics/crosstraining/data/analytics/FatLossAnalytics.kt), [`ProgressScreen.kt`](file:///c:/Users/rogal/workspaces/ws-gym/crosstrainingapp/app/src/main/java/com/fractanomics/crosstraining/ui/screens/ProgressScreen.kt)) and 3 test files ([`FatLossAnalyticsTest.kt`](file:///c:/Users/rogal/workspaces/ws-gym/crosstrainingapp/app/src/test/java/com/fractanomics/crosstraining/data/analytics/FatLossAnalyticsTest.kt), [`WeightProgressionCardTest.kt`](file:///c:/Users/rogal/workspaces/ws-gym/crosstrainingapp/app/src/test/java/com/fractanomics/crosstraining/ui/screens/WeightProgressionCardTest.kt), [`WeightProgressionCardComposeTest.kt`](file:///c:/Users/rogal/workspaces/ws-gym/crosstrainingapp/app/src/androidTest/java/com/fractanomics/crosstraining/ui/screens/WeightProgressionCardComposeTest.kt)).

## Constitution Check

*GATE: Must pass before Phase 0 research. Re-check after Phase 1 design.*

- **I. MVVM & Unidirectional Data Flow (UDF)**: **PASS** — Weighted Average pace and forecast calculations are pure, stateless functions in `FatLossAnalytics` and UI string/state resolvers in `ProgressScreen`.
- **II. Pure Deterministic Analytics & Display-Unit Consistency**: **PASS** — Uses explicit `today` date injection and `BigDecimal` `HALF_UP` 2-decimal rounding (`round2`) over the displayed 2-decimal horizon rates in the active display unit (`kg` or `lbs`).
- **III. Test-First & 100% CI Parity**: **PASS** — Unit tests and Compose UI tests are added alongside production changes while preserving all existing test contracts.
- **IV. Responsive Mobile UX & Accessibility**: **PASS** — Two-line wrapping on the Weighted Average summary and two-line horizon rows prevent horizontal overflow on `360dp` viewports up to `fontScale = 1.3f`, with merged TalkBack semantics per row.
- **V. Offline-First & Environment Isolation**: **PASS** — Zero Room schema changes and zero environment-specific config changes.
- **VI. Agentic Collaboration, Automated PR Review & Self-Healing Governance**: **PASS** — No workflow modifications required; all existing CI gates remain intact.
- **Post-Phase 1 Re-Check**: **PASS** — All contracts in `contracts/analytics-contract.md` and `data-model.md` satisfy all 6 Constitution principles.

## Project Structure

### Documentation (this feature)

```text
specs/009-weighted-goal-estimations/
├── spec.md              # Feature specification (/speckit-specify output)
├── plan.md              # This file (/speckit-plan output)
├── research.md          # Phase 0 output (/speckit-plan)
├── data-model.md        # Phase 1 output (/speckit-plan)
├── quickstart.md        # Phase 1 output (/speckit-plan)
├── checklists/
│   ├── requirements.md  # Specification quality validation checklist
│   └── ux.md            # UX & mathematical requirements quality checklist (/speckit-checklist)
├── contracts/           # Phase 1 output (/speckit-plan)
│   └── analytics-contract.md
└── tasks.md             # Phase 2 output (/speckit-tasks)
```

### Source Code (repository root)

```text
app/src/
├── main/java/com/fractanomics/crosstraining/
│   ├── data/analytics/
│   │   └── FatLossAnalytics.kt      # HORIZON_WEIGHTS, weightedAverageRow in MultiHorizonForecastState.Active & computeMultiHorizonForecast
│   └── ui/screens/
│       └── ProgressScreen.kt        # Redesigned MultiHorizonGoalEstimationsBanner, Current Goal & Target header, WeightedAverageEstimationRowItem & resolvers
├── test/java/com/fractanomics/crosstraining/
│   ├── data/analytics/
│   │   └── FatLossAnalyticsTest.kt
│   └── ui/screens/
│       └── WeightProgressionCardTest.kt
└── androidTest/java/com/fractanomics/crosstraining/ui/screens/
    └── WeightProgressionCardComposeTest.kt
```

**Structure Decision**: Single-module Android application architecture (`app/src/main`, `app/src/test`, and `app/src/androidTest`). Pure domain analytics reside in `com.fractanomics.crosstraining.data.analytics.FatLossAnalytics` and Compose UI components reside in `com.fractanomics.crosstraining.ui.screens.ProgressScreen`.

## Complexity Tracking

No constitution violations or structural complexity exceptions required.
