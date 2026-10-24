# Implementation Plan: Multi-Window Rolling Average Scale Targets (7D, 14D, 21D, 28D) in Pace & Linear Targets

**Branch**: `feat/007-rolling-avg-scale-targets` | **Date**: 2026-10-24 | **Spec**: [spec.md](spec.md)
**Input**: Feature specification from `specs/007-rolling-avg-scale-targets/spec.md`

## Summary

Revise `FatLossAnalytics.compute7DayPaceTargets` and `TheoreticalPaceTargetsBox` (`ProgressScreen.kt`) so that:
1. For each rolling window $N \in \{7, 14, 21, 28\}$:
   - `Linear Today` displays the 2-decimal theoretical linear weight for today and compares it against the current $N\text{D}$ rolling average.
   - `Expected Today` solves for the exact 2-decimal **daily scale weight** needed today (`t`) so that today's $N\text{D}$ rolling average over $[t - (N - 1), t]$ equals today's required-pace $N\text{D}$ average ($\text{AVG}_{ND}(t - N) + N \times r_{\text{daily}, ND}$), and compares it against today's actual weigh-in when logged.
   - `Target Tomorrow` solves for the exact 2-decimal **daily scale weight** needed tomorrow (`t + 1`) so that tomorrow's $N\text{D}$ rolling average over $[t - (N - 2), t + 1]$ equals tomorrow's required-pace $N\text{D}$ average ($\text{AVG}_{ND}(t) + r_{\text{daily}, ND}$), with `-X.XX <unit>/day` as subtitle.
2. Remove the redundant `DAILY WEIGH-IN` row from `TheoreticalPaceTargetsBox` and replace it with four rolling average rows: `7D ROLLING AVERAGE`, `14D ROLLING AVERAGE`, `21D ROLLING AVERAGE`, and `28D ROLLING AVERAGE`.
3. Provide horizon-specific history subtitles (`"Needs 7 days history"`, `"Needs 14 days history"`, `"Needs 21 days history"`, `"Needs 28 days history"`) when `[t - 2N + 1, t - N]` precedes `cycleStartDate` with no entries.

## Technical Context

**Language/Version**: Kotlin (JVM 17)
**Primary Dependencies**: Jetpack Compose (Material 3), `java.time` (`LocalDate`, `ChronoUnit`), `java.math.BigDecimal`
**Storage**: N/A (pure in-memory domain analytics derived from existing Room `Cycle` and `WeightEntry` entities; zero schema changes)
**Testing**: JUnit 4 (`testDebugUnitTest`), AndroidX Compose UI Test (`WeightProgressionCardComposeTest`), PowerShell Pester/Script tests (`Invoke-ScriptTests.ps1`)
**Target Platform**: Android (`compileSdk 35`, `minSdk 26`)
**Project Type**: Mobile application (Android)
**Performance Goals**: Pure $O(E)$ calculation over active weight entries (< 1 ms per frame recomposition)
**Constraints**: Zero text clipping or overflow on `360dp`-wide screens at `fontScale = 1.3f`; `BigDecimal` `HALF_UP` 2-decimal display-unit arithmetic consistency in both `kg` and `lbs`.
**Scale/Scope**: 2 production files (`FatLossAnalytics.kt`, `ProgressScreen.kt`) and 3 test files (`FatLossAnalyticsTest.kt`, `WeightProgressionCardTest.kt`, `WeightProgressionCardComposeTest.kt`).

## Constitution Check

*GATE: Must pass before Phase 0 research. Re-check after Phase 1 design.*

- **I. MVVM & Unidirectional Data Flow (UDF)**: PASS — `FatLossAnalytics.compute7DayPaceTargets` remains a pure domain function invoked in `WeightProgressionCard` via `remember` and passed as immutable `PaceTargetsState` to stateless `TheoreticalPaceTargetsBox`.
- **II. Pure Deterministic Analytics & Display-Unit Consistency**: PASS — All window sums, required rates, target averages, and solved scale weights are computed deterministically with injected `referenceDate` (`today`) in the active display unit (`kg` or `lbs`) and rounded via `round2` / `diff2` (`BigDecimal` `HALF_UP`).
- **III. Test-First & 100% CI Parity**: PASS — Unit tests (`FatLossAnalyticsTest`, `WeightProgressionCardTest`), Compose UI tests (`WeightProgressionCardComposeTest`), script tests (`Invoke-ScriptTests.ps1`), and Android Lint (`lintDebug`) will all be updated and verified at 100%.
- **IV. Responsive Mobile UX & Accessibility**: PASS — All 4 rows (`7D`, `14D`, `21D`, `28D`) use the responsive 3-column `TargetRowSection` layout with `Modifier.semantics(mergeDescendants = true)` and font-scale-aware typography.
- **V. Offline-First & Environment Isolation**: PASS — No database schema changes and no `gradle.properties` modifications.
- **VI. Agentic Collaboration, Automated PR Review & Self-Healing Governance**: PASS — No workflow modifications required.

## Project Structure

### Documentation (this feature)

```text
specs/007-rolling-avg-scale-targets/
├── plan.md              # This file
├── research.md          # Phase 0 output
├── data-model.md        # Phase 1 output
├── quickstart.md        # Phase 1 output
├── contracts/
│   └── analytics-contract.md
├── checklists/
│   ├── requirements.md
│   └── analytics.md
└── tasks.md             # Phase 2 output
```

### Source Code (repository root)

```text
app/src/main/java/com/fractanomics/crosstraining/
├── data/analytics/FatLossAnalytics.kt
└── ui/screens/ProgressScreen.kt

app/src/test/java/com/fractanomics/crosstraining/
├── data/analytics/FatLossAnalyticsTest.kt
└── ui/screens/WeightProgressionCardTest.kt

app/src/androidTest/java/com/fractanomics/crosstraining/
└── ui/screens/WeightProgressionCardComposeTest.kt
```

**Structure Decision**: Existing single-module Android MVVM structure (`app/src/main`, `app/src/test`, `app/src/androidTest`).

## Complexity Tracking

No constitution violations or complexity exceptions required.
