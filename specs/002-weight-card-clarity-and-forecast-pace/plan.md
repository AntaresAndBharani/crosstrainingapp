# Implementation Plan: Weight Progression Table Clarity & Forecast Pace Attribution

**Branch**: `002-weight-card-clarity-and-forecast-pace` | **Date**: 2026-10-22 | **Spec**: [spec.md](file:///c:/Users/rogal/workspaces/ws-gym/crosstrainingapp/specs/002-weight-card-clarity-and-forecast-pace/spec.md)

**Input**: Feature specification from `/specs/002-weight-card-clarity-and-forecast-pace/spec.md`

## Summary

1. **Distinguish Rolling Averages vs. Daily Weigh-In Tables**: Add explicit section headers (`ROLLING AVERAGES` and `DAILY WEIGH-IN (<DAY>)`) and a subtle horizontal divider between the two 3-column rows in [`WeightProgressionHeroCard`](file:///c:/Users/rogal/workspaces/ws-gym/crosstrainingapp/app/src/main/java/com/fractanomics/crosstraining/ui/screens/ProgressScreen.kt#L1694-L1960). Rename ambiguous/truncating column headers: top row becomes `LAST WK 7D | 7D AVG | 14D AVG` and bottom row (`SameDayWeightTrioRow`) becomes `LAST WEEK | TODAY | 2 WKS AGO`.
2. **Forecast Banner Pace Attribution**: Display `"Based on 14-day pace (-0.2 kg/wk)"` (or `"7-day pace"` when falling back to Tier 2) inside [`ForecastBanner`](file:///c:/Users/rogal/workspaces/ws-gym/crosstrainingapp/app/src/main/java/com/fractanomics/crosstraining/ui/screens/ProgressScreen.kt#L2118-L2178) for both `PaceForecast.Projected` and `PaceForecast.Stalled` states, localized to the active `weightUnit` (`kg` / `lbs`).
3. **1-Decimal Display Arithmetic Reconciliation**: Align [`computePaceForecast`](file:///c:/Users/rogal/workspaces/ws-gym/crosstrainingapp/app/src/main/java/com/fractanomics/crosstraining/data/analytics/FatLossAnalytics.kt#L1620-L1775), [`computeRollingWeightHero`](file:///c:/Users/rogal/workspaces/ws-gym/crosstrainingapp/app/src/main/java/com/fractanomics/crosstraining/data/analytics/FatLossAnalytics.kt#L1522-L1569), and the Top Reference Track in [`WeightProgressionHeroCard`](file:///c:/Users/rogal/workspaces/ws-gym/crosstrainingapp/app/src/main/java/com/fractanomics/crosstraining/ui/screens/ProgressScreen.kt#L1771-L1836) on 1-decimal rounded values (`round1` / `diff1`) so `3.7 kg left / 0.2 kg/wk = 18.5 weeks` and `75.7 kg - 75.9 kg = -0.2 kg` always reconcile on screen.

## Technical Context

**Language/Version**: Kotlin 1.9+ (JVM 17)

**Primary Dependencies**: Jetpack Compose (Material 3), AndroidX Lifecycle (`collectAsStateWithLifecycle`), Java Time (`java.time.LocalDate`, `TextStyle`), JUnit 4

**Storage**: Room SQLite (`WeightEntry`, `Cycle` entities — no schema changes or migrations required)

**Testing**: JUnit 4 (`.\gradlew.bat testDebugUnitTest --no-daemon`), Compose UI Test (`WeightProgressionCardComposeTest`), PowerShell Script Tests (`.\scripts\tests\Invoke-ScriptTests.ps1`)

**Target Platform**: Android (compileSdk 35, minSdk 26)

**Project Type**: Mobile application (Android Jetpack Compose MVVM with Unidirectional Data Flow)

**Performance Goals**: Pure $O(N)$ stateless domain evaluation in `FatLossAnalytics` executing in $< 1\text{ms}$ on the UI thread inside Compose `remember` blocks with zero unnecessary heap churn.

**Constraints**: Offline-first, deterministic pure functions (`referenceDate` / `today` injected for testability), strict unit localization parity (`kg` canonical storage/math with `lbs` display conversion), zero visual overflow on `360dp` screens at `fontScale = 1.3f`.

**Scale/Scope**: Touches 2 production source files ([`FatLossAnalytics.kt`](file:///c:/Users/rogal/workspaces/ws-gym/crosstrainingapp/app/src/main/java/com/fractanomics/crosstraining/data/analytics/FatLossAnalytics.kt), [`ProgressScreen.kt`](file:///c:/Users/rogal/workspaces/ws-gym/crosstrainingapp/app/src/main/java/com/fractanomics/crosstraining/ui/screens/ProgressScreen.kt)) and 3 test files ([`FatLossAnalyticsTest.kt`](file:///c:/Users/rogal/workspaces/ws-gym/crosstrainingapp/app/src/test/java/com/fractanomics/crosstraining/data/analytics/FatLossAnalyticsTest.kt), [`WeightProgressionCardTest.kt`](file:///c:/Users/rogal/workspaces/ws-gym/crosstrainingapp/app/src/test/java/com/fractanomics/crosstraining/ui/screens/WeightProgressionCardTest.kt), [`WeightProgressionCardComposeTest.kt`](file:///c:/Users/rogal/workspaces/ws-gym/crosstrainingapp/app/src/androidTest/java/com/fractanomics/crosstraining/ui/screens/WeightProgressionCardComposeTest.kt)).

## Constitution Check

*GATE: Must pass before Phase 0 research. Re-check after Phase 1 design.*

- **MVVM & Unidirectional Data Flow (`GEMINI.md` Guideline 1 & 2)**: **PASS** — All calculations and label resolvers remain pure, stateless functions in `FatLossAnalytics` and `ProgressScreen`.
- **Testing & CI Parity (`GEMINI.md` Guideline 3)**: **PASS** — Unit and Compose tests updated in lockstep and verified with the full pre-PR verification suite.
- **Zero Database Migration Risk**: **PASS** — Operates strictly on existing `WeightEntry` and `Cycle` models with zero Room schema changes.
- **Post-Phase 1 Re-Check**: **PASS** — All contracts in `contracts/analytics-contract.md` and `data-model.md` maintain backward compatibility and pure functional determinism.

## Project Structure

### Documentation (this feature)

```text
specs/002-weight-card-clarity-and-forecast-pace/
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
│   │   └── FatLossAnalytics.kt      # PaceForecast metadata, display-rounded forecast & hero deltas
│   └── ui/screens/
│       └── ProgressScreen.kt        # Section headers, pillar label resolvers, ForecastBanner pace line
├── test/java/com/fractanomics/crosstraining/
│   ├── data/analytics/
│   │   └── FatLossAnalyticsTest.kt
│   └── ui/screens/
│       └── WeightProgressionCardTest.kt
└── androidTest/java/com/fractanomics/crosstraining/ui/screens/
    └── WeightProgressionCardComposeTest.kt
```

**Structure Decision**: Existing single-module Android application architecture (`app/src/main`, `app/src/test`, and `app/src/androidTest`). All domain math resides in `com.fractanomics.crosstraining.data.analytics.FatLossAnalytics` and UI presentation resides in `com.fractanomics.crosstraining.ui.screens.ProgressScreen`.

## Complexity Tracking

No constitution violations or structural complexity exceptions required.
