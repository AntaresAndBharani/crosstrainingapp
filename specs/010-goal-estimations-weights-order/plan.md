# Implementation Plan: Goal Arrival Estimations Average Weights & Section Reordering

**Branch**: `feat/010-goal-estimations-weights-order` | **Date**: 2026-11-01 | **Spec**: [spec.md](file:///c:/Users/rogal/workspaces/ws-gym/crosstrainingapp/specs/010-goal-estimations-weights-order/spec.md)

**Input**: Feature specification from `/specs/010-goal-estimations-weights-order/spec.md`

## Summary

1. **Domain Analytics (`FatLossAnalytics.kt`)**:
   - Add `averageWeightDisplay: String? = null` to `HorizonRowState.Projected` and `HorizonRowState.Stalled`.
   - Update `HorizonEstimationRow.formatLeftLabel()` to include ` · ${state.averageWeightDisplay}` when non-null (e.g., `"7D (-0.49 kg/wk · 81.5 kg)"`) and `HorizonEstimationRow.formatWeightedSummary()` to include ` · ${state.averageWeightDisplay}` after `paceDisplay2` (e.g., `"(Weighted pace of -0.39 kg/wk · 82.3 kg · 7.9 wks · 26 Dec 2026)"`).
   - In `FatLossAnalytics.computeMultiHorizonForecast`, compute the 1-decimal display-unit rolling average weight for each available horizon `w` in `{7, 14, 21, 28}` over `[today - (w - 1), today]` and the `4:3:2:1` recency-weighted average weight across available horizons for `weightedAverageRow`, passing `averageWeightDisplay` into `buildProjectedOrStalledHorizonRow` while keeping `remainingInUnit` anchored to the current 7D average weight.
2. **UI Layout & Responsive Rendering (`ProgressScreen.kt`)**:
   - Reorder `ForecastBanner` (`GOAL ARRIVAL ESTIMATIONS` / `MultiHorizonGoalEstimationsBanner`) inside `WeightProgressionHeroCard` so it renders immediately after the Hero Centerpiece (`ROLLING AVERAGES` + `DAILY WEIGH-IN` + streak pill) and immediately before `TheoreticalPaceTargetsBox` (`PACE & LINEAR TARGETS`).
   - Ensure `HorizonEstimationRowItem` handles the wider left label (`"28D (-0.31 kg/wk · 83.1 kg)"`) cleanly on `360dp` viewports up to `fontScale = 1.3f` without text clipping or overlap.
3. **End-to-End Verification**:
   - Update and expand unit tests in `FatLossAnalyticsTest.kt` and `WeightProgressionCardTest.kt`, and Compose UI tests in `WeightProgressionCardComposeTest.kt`, to verify the new weight formatting, `4:3:2:1` weighted average weight calculation in `kg` and `lbs`, TalkBack accessibility strings, and the updated section order in `WeightProgressionHeroCard`.

## Technical Context

**Language/Version**: Kotlin 1.9+ (JVM 17)

**Primary Dependencies**: Jetpack Compose (Material 3), AndroidX Lifecycle (`collectAsStateWithLifecycle`), Java Time (`java.time.LocalDate`, `java.time.temporal.ChronoUnit`, `DateTimeFormatter`), JUnit 4

**Storage**: Room SQLite (`Cycle`, `WeightEntry` — existing fields only, zero schema changes or migrations)

**Testing**: JUnit 4 (`.\gradlew.bat testDebugUnitTest --no-daemon`), Compose UI Test (`WeightProgressionCardComposeTest`), PowerShell Script Tests (`.\scripts\tests\Invoke-ScriptTests.ps1`), Android Lint (`.\gradlew.bat lintDebug --no-daemon`)

**Target Platform**: Android (compileSdk 35, minSdk 26)

**Project Type**: Mobile application (Android Jetpack Compose MVVM with Unidirectional Data Flow)

**Performance Goals**: Pure $O(1)$ window average and weighted combination over the 4 horizons (`< 0.1\text{ms}` on UI thread inside Compose `remember`).

**Constraints**: Offline-first, deterministic pure functions (`today` / `referenceDate` injected), 1-decimal display-unit arithmetic consistency (`round1`, `round1InUnit`) for weights and 2-decimal (`round2`) for pace rates, zero visual overflow on `360dp` screens at `fontScale = 1.3f`.

**Scale/Scope**: Touches 2 production source files ([`FatLossAnalytics.kt`](file:///c:/Users/rogal/workspaces/ws-gym/crosstrainingapp/app/src/main/java/com/fractanomics/crosstraining/data/analytics/FatLossAnalytics.kt), [`ProgressScreen.kt`](file:///c:/Users/rogal/workspaces/ws-gym/crosstrainingapp/app/src/main/java/com/fractanomics/crosstraining/ui/screens/ProgressScreen.kt)) and 3 test files ([`FatLossAnalyticsTest.kt`](file:///c:/Users/rogal/workspaces/ws-gym/crosstrainingapp/app/src/test/java/com/fractanomics/crosstraining/data/analytics/FatLossAnalyticsTest.kt), [`WeightProgressionCardTest.kt`](file:///c:/Users/rogal/workspaces/ws-gym/crosstrainingapp/app/src/test/java/com/fractanomics/crosstraining/ui/screens/WeightProgressionCardTest.kt), [`WeightProgressionCardComposeTest.kt`](file:///c:/Users/rogal/workspaces/ws-gym/crosstrainingapp/app/src/androidTest/java/com/fractanomics/crosstraining/ui/screens/WeightProgressionCardComposeTest.kt)).

## Constitution Check

*GATE: Must pass before Phase 0 research. Re-check after Phase 1 design.*

- **I. MVVM & Unidirectional Data Flow (UDF)**: **PASS** — Rolling/weighted average weight calculations reside in `FatLossAnalytics` and UI rendering in `ProgressScreen` remains stateless.
- **II. Pure Deterministic Analytics & Display-Unit Consistency**: **PASS** — Uses explicit `today` date injection and `BigDecimal` `HALF_UP` 1-decimal rounding (`round1InUnit`, `round1`) for displayed weights in `kg` and `lbs`.
- **III. Test-First & 100% CI Parity**: **PASS** — Unit tests and Compose UI tests are updated and expanded alongside production changes.
- **IV. Responsive Mobile UX & Accessibility**: **PASS** — Horizon rows and the Weighted Average summary render cleanly on `360dp` viewports up to `fontScale = 1.3f` and include the formatted weight in merged TalkBack `contentDescription`.
- **V. Offline-First & Environment Isolation**: **PASS** — Zero Room schema changes and zero environment-specific config changes.
- **VI. Agentic Collaboration, Automated PR Review & Self-Healing Governance**: **PASS** — All CI gates remain intact.
- **Post-Phase 1 Re-Check**: **PASS** — All contracts in `contracts/analytics-contract.md` and `data-model.md` satisfy all 6 Constitution principles.

## Project Structure

### Documentation (this feature)

```text
specs/010-goal-estimations-weights-order/
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
│   │   └── FatLossAnalytics.kt      # averageWeightDisplay in HorizonRowState, HorizonEstimationRow formatters, computeMultiHorizonForecast weight math
│   └── ui/screens/
│       └── ProgressScreen.kt        # Reorder ForecastBanner before TheoreticalPaceTargetsBox & responsive HorizonEstimationRowItem layout
├── test/java/com/fractanomics/crosstraining/
│   ├── data/analytics/
│   │   └── FatLossAnalyticsTest.kt
│   └── ui/screens/
│       └── WeightProgressionCardTest.kt
└── androidTest/java/com/fractanomics/crosstraining/ui/screens/
    └── WeightProgressionCardComposeTest.kt
```

**Structure Decision**: Single-module Android application architecture (`app/src/main`, `app/src/test`, and `app/src/androidTest`).

## Complexity Tracking

No constitution violations or structural complexity exceptions required.
