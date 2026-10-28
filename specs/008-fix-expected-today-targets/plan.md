# Implementation Plan: Fix Expected Today Pace & Linear Targets Across Rolling Windows (7D–28D)

**Branch**: `feat/008-fix-expected-today-targets` | **Date**: 2026-10-28 | **Spec**: [spec.md](file:///c:/Users/rogal/workspaces/ws-gym/crosstrainingapp/specs/008-fix-expected-today-targets/spec.md)

**Input**: Feature specification from `/specs/008-fix-expected-today-targets/spec.md`

## Summary

Fix the mathematical divergence bug in the **Pace & Linear Targets** section (`FatLossAnalytics.computeRollingWindowTargetRow`) where `Expected Today` for `14D`, `21D`, and `28D` horizons produced extreme, non-monotonic weights (`71.50 kg` at `14D`, `78.30 kg` at `21D`, `94.52 kg` at `28D`). Instead of projecting $N$ days forward from the disjoint prior window $[t - 2N + 1, t - N]$ and forcing a single day's weigh-in on day $t$ to absorb $N$ days of cumulative moving-average drift, `Expected Today` now steps **1 day forward from yesterday's $N$-day rolling average** ($[t - N, t - 1] + 1 \times \text{dailyRequiredRateInUnit}$) and back-solves for today's scale weight over $[t - (N - 1), t - 1]$, matching `Target Tomorrow` while preserving the $[t - 2N + 1, t - N]$ maturity checks (`InsufficientHistory` and `NeedsLog`).

## Technical Context

**Language/Version**: Kotlin (JVM 17), Android SDK 35

**Primary Dependencies**: Jetpack Compose, Material 3, Room, Kotlin Coroutines / Flow, JUnit 4

**Storage**: N/A (pure in-memory domain analytics calculation in `FatLossAnalytics.kt`)

**Testing**: JUnit 4 (`.\gradlew.bat testDebugUnitTest --no-daemon`), Compose UI Test (`WeightProgressionCardComposeTest`), PowerShell script regression suite (`.\scripts\tests\Invoke-ScriptTests.ps1`), Android Lint (`.\gradlew.bat lintDebug --no-daemon`)

**Target Platform**: Android (Min SDK / Target SDK 35)

**Project Type**: Mobile application (Android Jetpack Compose + MVVM)

**Performance Goals**: $O(N)$ per horizon row over at most 56 days of daily weigh-ins ($< 1\text{ ms}$ execution time on main/background dispatcher)

**Constraints**: Pure deterministic domain function with no side effects, preserving unit conversion (`KG` / `LB`) before `round2`

**Scale/Scope**: Single domain analytics module (`FatLossAnalytics.kt`) and associated unit/instrumentation test files (`FatLossAnalyticsTest.kt`, `WeightProgressionCardComposeTest.kt`)

## Constitution Check

*GATE: Must pass before Phase 0 research. Re-check after Phase 1 design.*

- **I. Architecture & Unidirectional Data Flow (MVVM + UDF)**: **PASS** — Calculation remains encapsulated in the pure domain utility `FatLossAnalytics` consumed by `WeightTrackerViewModel` and rendered via immutable `StateFlow` in `ProgressScreen`.
- **II. Testing & CI Parity (Non-Negotiable)**: **PASS** — Unit tests in `FatLossAnalyticsTest.kt` and Compose tests in `WeightProgressionCardComposeTest.kt` are updated and expanded with a multi-horizon regression test; full pre-PR verification suite (`testDebugUnitTest`, `Invoke-ScriptTests.ps1`, `lintDebug`) will pass prior to PR.
- **III. Visual E2E Verification**: **PASS** — No UI layout or component structure changes; pure numeric bug fix in analytics calculation.
- **IV. Environment Isolation & Local Artifact Sync**: **PASS** — No machine-specific properties modified.
- **V. Autonomous Git & CI/CD Lifecycle Gate**: **PASS** — Feature branch `feat/008-fix-expected-today-targets`, `CHANGELOG.md` update, PR creation, and remote CI verification will be executed.

## Project Structure

### Documentation (this feature)

```text
specs/008-fix-expected-today-targets/
├── plan.md              # This file (/speckit-plan command output)
├── research.md          # Phase 0 output (/speckit-plan command)
├── data-model.md        # Phase 1 output (/speckit-plan command)
├── quickstart.md        # Phase 1 output (/speckit-plan command)
├── contracts/
│   └── analytics-contract.md
├── checklists/
│   ├── requirements.md
│   └── analytics.md
└── tasks.md             # Phase 2 output (/speckit-tasks command)
```

### Source Code (repository root)

```text
app/
├── src/main/java/com/fractanomics/crosstraining/
│   ├── data/analytics/
│   │   └── FatLossAnalytics.kt
│   └── ui/screens/
│       └── ProgressScreen.kt
├── src/test/java/com/fractanomics/crosstraining/
│   └── data/analytics/
│       └── FatLossAnalyticsTest.kt
└── src/androidTest/java/com/fractanomics/crosstraining/
    └── ui/screens/
        └── WeightProgressionCardComposeTest.kt
```

**Structure Decision**: Existing single-module Android application layout (`app/src/main`, `app/src/test`, `app/src/androidTest`). Only `FatLossAnalytics.kt`, `FatLossAnalyticsTest.kt`, and `WeightProgressionCardComposeTest.kt` require modification.

## Complexity Tracking

No constitution violations.
