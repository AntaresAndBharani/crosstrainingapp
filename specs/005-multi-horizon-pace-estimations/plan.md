# Implementation Plan: 21D/28D Pace Grid & Visual Multi-Horizon Goal Estimations Card (7D, 14D, 21D, 28D)

**Branch**: `feat/005-multi-horizon-pace-estimations` | **Date**: 2026-10-23 | **Spec**: [spec.md](file:///c:/Users/rogal/workspaces/ws-gym/crosstrainingapp/specs/005-multi-horizon-pace-estimations/spec.md)

**Input**: Feature specification from `/specs/005-multi-horizon-pace-estimations/spec.md`

## Summary

1. **Domain Analytics (`FatLossAnalytics.kt`)**:
   - Add `compute21DayPace` (`windowDays = 21`) and `compute28DayPace` (`windowDays = 28`) overloads using the existing `computePaceRate` rolling 7D window average engine.
   - Add `format21DayPaceSubtitle` and `format28DayPaceSubtitle`, and update `formatPaceSubtitle` with an optional `useTwoDecimals: Boolean = false` parameter and generalized maturity fallback (`"Needs weigh-in ${windowDays}+ days ago"` when `pace == null`, `"Smoothed trend"` when `pace != null && comparison == null`).
   - Add `PaceRateResult.formatDisplay2(unit: String = "kg")` for 2-decimal weekly pace formatting (`"-0.28 kg/wk"`, `"+0.15 kg/wk"`, `"0.00 kg/wk"`).
   - Extend `RequiredPaceResult` with `requiredRate2InUnit`, `formatRate2()`, and `formatBenchmarkHeader()` (`"Target: 28 Dec 2026 (9.4w left) · Required: -0.39 kg/wk"`).
   - Add `MultiHorizonForecastState`, `HorizonEstimationRow`, `HorizonRowState`, and `computeMultiHorizonForecast` evaluating all 4 horizons (`7`, `14`, `21`, `28` days) with 2-decimal velocity `v2`, estimated weeks (`remainingInUnit / v2`), projected arrival date (`today.plusDays(round(rawWeeks * 7))`), proportional sufficiency bar fraction (`v2 / abs(requiredRate2InUnit)`), and ahead/late weeks delta badge (`On time (-X.Xw)`, `On time`, `+X.Xw late`, `> 1 yr late`, `Stalled`).
2. **UI Layout & Visual Redesign (`ProgressScreen.kt`)**:
   - Upgrade the Pace Cards section inside `WeightProgressionHeroCard` from a 1×2 row to a **2×2 Pace Grid** (`7-Day Pace`, `14-Day Pace`, `21-Day Pace`, `28-Day Pace`) displaying 2-decimal pace rates and 2-decimal week-over-week comparison subtitles.
   - Redraw `ForecastBanner` into the visual **Multi-Horizon Goal Estimations Card (`GOAL ARRIVAL ESTIMATIONS`)** featuring the Target Benchmark Header and 4 two-line horizon rows (`7D`, `14D`, `21D`, `28D`) with color-coded sufficiency bars (`primary` when on track, `tertiary` when behind, `0%` when stalled or insufficient history) and merged TalkBack accessibility semantics.
3. **End-to-End Verification**:
   - Expand unit tests in `FatLossAnalyticsTest.kt` and `WeightProgressionCardTest.kt`, and Compose UI tests in `WeightProgressionCardComposeTest.kt`, covering 21D/28D pace math, 2-decimal formatting, `computeMultiHorizonForecast`, `kg`/`lbs` localization, and `360dp` / `fontScale = 1.3f` layout rendering.

## Technical Context

**Language/Version**: Kotlin 1.9+ (JVM 17)

**Primary Dependencies**: Jetpack Compose (Material 3), AndroidX Lifecycle (`collectAsStateWithLifecycle`), Java Time (`java.time.LocalDate`, `java.time.temporal.ChronoUnit`, `DateTimeFormatter`), JUnit 4

**Storage**: Room SQLite (`Cycle`, `WeightEntry` — existing fields only, zero schema changes or migrations)

**Testing**: JUnit 4 (`.\gradlew.bat testDebugUnitTest --no-daemon`), Compose UI Test (`WeightProgressionCardComposeTest`), PowerShell Script Tests (`.\scripts\tests\Invoke-ScriptTests.ps1`), Android Lint (`.\gradlew.bat lintDebug --no-daemon`)

**Target Platform**: Android (compileSdk 35, minSdk 26)

**Project Type**: Mobile application (Android Jetpack Compose MVVM with Unidirectional Data Flow)

**Performance Goals**: Pure $O(N)$ rolling window evaluations across 4 horizons (`< 0.3\text{ms}` on UI thread inside Compose `remember`).

**Constraints**: Offline-first, deterministic pure functions (`today` / `referenceDate` injected), 2-decimal display-unit arithmetic consistency (`round2`), zero visual overflow on `360dp` screens at `fontScale = 1.3f`.

**Scale/Scope**: Touches 2 production source files ([`FatLossAnalytics.kt`](file:///c:/Users/rogal/workspaces/ws-gym/crosstrainingapp/app/src/main/java/com/fractanomics/crosstraining/data/analytics/FatLossAnalytics.kt), [`ProgressScreen.kt`](file:///c:/Users/rogal/workspaces/ws-gym/crosstrainingapp/app/src/main/java/com/fractanomics/crosstraining/ui/screens/ProgressScreen.kt)) and 3 test files ([`FatLossAnalyticsTest.kt`](file:///c:/Users/rogal/workspaces/ws-gym/crosstrainingapp/app/src/test/java/com/fractanomics/crosstraining/data/analytics/FatLossAnalyticsTest.kt), [`WeightProgressionCardTest.kt`](file:///c:/Users/rogal/workspaces/ws-gym/crosstrainingapp/app/src/test/java/com/fractanomics/crosstraining/ui/screens/WeightProgressionCardTest.kt), [`WeightProgressionCardComposeTest.kt`](file:///c:/Users/rogal/workspaces/ws-gym/crosstrainingapp/app/src/androidTest/java/com/fractanomics/crosstraining/ui/screens/WeightProgressionCardComposeTest.kt)).

## Constitution Check

*GATE: Must pass before Phase 0 research. Re-check after Phase 1 design.*

- **I. MVVM & Unidirectional Data Flow (UDF)**: **PASS** — All 21D/28D pace and multi-horizon forecast calculations are pure, stateless functions in `FatLossAnalytics` and UI string/state resolvers in `ProgressScreen`.
- **II. Pure Deterministic Analytics & Display-Unit Consistency**: **PASS** — Uses explicit `today` date injection and `BigDecimal` `HALF_UP` 2-decimal rounding (`round2`) in the active display unit (`kg` or `lbs`).
- **III. Test-First & 100% CI Parity**: **PASS** — Comprehensive unit tests and Compose UI tests are added in lockstep while preserving backward compatibility for existing test suites.
- **IV. Responsive Mobile UX & Accessibility**: **PASS** — Two-line horizon rows and 2×2 pace cards prevent horizontal overflow on `360dp` viewports up to `fontScale = 1.3f`, with merged semantics per row.
- **V. Offline-First & Environment Isolation**: **PASS** — Zero Room schema changes and zero environment-specific config changes.
- **Post-Phase 1 Re-Check**: **PASS** — All contracts in `contracts/analytics-contract.md` and `data-model.md` satisfy all 5 Constitution principles.

## Project Structure

### Documentation (this feature)

```text
specs/005-multi-horizon-pace-estimations/
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
│   │   └── FatLossAnalytics.kt      # compute21DayPace, compute28DayPace, MultiHorizonForecastState, computeMultiHorizonForecast
│   └── ui/screens/
│       └── ProgressScreen.kt        # 2x2 Pace Grid, redrawn ForecastBanner / MultiHorizonForecastCard & UI resolvers
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
