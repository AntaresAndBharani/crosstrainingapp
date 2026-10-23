---
description: "Task list for 21D/28D Pace Grid & Visual Multi-Horizon Goal Estimations Card (7D, 14D, 21D, 28D)"
---

# Tasks: 21D/28D Pace Grid & Visual Multi-Horizon Goal Estimations Card (7D, 14D, 21D, 28D)

**Input**: Design documents from `/specs/005-multi-horizon-pace-estimations/`

**Prerequisites**: plan.md (required), spec.md (required for user stories), research.md, data-model.md, contracts/analytics-contract.md

**Tests**: Comprehensive JUnit 4 unit tests and Compose UI tests are included per Constitution Principle III (Test-First & 100% CI Parity).

**Organization**: Tasks are grouped by user story (`US1`, `US2`, `US3`) to enable structured implementation and verification.

## Format: `[ID] [P?] [Story] Description`

- **[P]**: Can run in parallel (different files, no dependencies)
- **[Story]**: Which user story this task belongs to (`US1`, `US2`, `US3`)
- Exact file paths are included in every task description

---

## Phase 1: Setup (Shared Infrastructure)

**Purpose**: Verify branch and Spec Kit workspace readiness

- [X] T001 Create and switch to feature branch `feat/005-multi-horizon-pace-estimations` from `main`

---

## Phase 2: Foundational (Blocking Prerequisites)

**Purpose**: Core data structures and 2-decimal formatting primitives in `FatLossAnalytics.kt` that block all user stories

- [X] T002 Add `PaceRateResult.formatDisplay2(unit: String = "kg"): String` for 2-decimal weekly rate formatting (`%.2f kg/wk`, `+%.2f kg/wk`, `0.00 kg/wk`) and extend `RequiredPaceResult` with `requiredRate2InUnit`, `formatRate2()`, and `formatBenchmarkHeader()` in `app/src/main/java/com/fractanomics/crosstraining/data/analytics/FatLossAnalytics.kt`
- [X] T003 Define `MultiHorizonForecastState` (`GoalReached`, `CycleEnded`, `NotEnoughData`, `Active`), `HorizonEstimationRow`, and `HorizonRowState` (`NotEnoughData`, `Stalled`, `Projected`) in `app/src/main/java/com/fractanomics/crosstraining/data/analytics/FatLossAnalytics.kt`

---

## Phase 3: User Story 1 - 2×2 Rolling Pace Grid with 7D, 14D, 21D, and 28D Paces at 2-Decimal Precision (Priority: P1) 🎯 MVP

**Goal**: Compute 21-Day and 28-Day smoothed rolling paces and week-over-week comparisons, and render all 4 pace cards (`7-Day Pace`, `14-Day Pace`, `21-Day Pace`, `28-Day Pace`) in a 2×2 grid at 2-decimal precision.

**Independent Test**: Evaluate `compute21DayPace`, `compute28DayPace`, `format21DayPaceSubtitle`, `format28DayPaceSubtitle`, and the 2×2 Pace Grid in `WeightProgressionHeroCard` across 30 days of weigh-ins and verify 2-decimal weekly rates and comparison subtitles.

### Implementation & Tests for User Story 1

- [X] T004 [US1] Implement `compute21DayPace` (`windowDays = 21`) and `compute28DayPace` (`windowDays = 28`) overloads, and update `formatPaceSubtitle` (adding `useTwoDecimals: Boolean = false` and generalized `"Needs weigh-in ${windowDays}+ days ago"` fallback) plus `format21DayPaceSubtitle` and `format28DayPaceSubtitle` in `app/src/main/java/com/fractanomics/crosstraining/data/analytics/FatLossAnalytics.kt`
- [X] T005 [US1] Update `resolvePaceChipValue` (with `useTwoDecimals: Boolean = false`), add `resolvePace21Subtitle` and `resolvePace28Subtitle`, and upgrade `WeightProgressionCard` and `WeightProgressionHeroCard` to compute and render the 2×2 Pace Cards Grid (`7-Day Pace`, `14-Day Pace`, `21-Day Pace`, `28-Day Pace`) at 2-decimal precision in `app/src/main/java/com/fractanomics/crosstraining/ui/screens/ProgressScreen.kt`
- [X] T006 [P] [US1] Add unit tests for `compute21DayPace`, `compute28DayPace`, `formatDisplay2`, `format21DayPaceSubtitle`, and `format28DayPaceSubtitle` (including cold-start `<21d` / `<28d`, `"Smoothed trend"`, and `lbs` conversion) in `app/src/test/java/com/fractanomics/crosstraining/data/analytics/FatLossAnalyticsTest.kt`
- [X] T007 [P] [US1] Add UI resolver unit tests for 2-decimal pace chip formatting and 21D/28D subtitles in `app/src/test/java/com/fractanomics/crosstraining/ui/screens/WeightProgressionCardTest.kt`

---

## Phase 4: User Story 2 - Redrawn Visual Multi-Horizon Goal Estimations Card (7D, 14D, 21D, 28D) (Priority: P1)

**Goal**: Compute 4-horizon goal arrival projections (`7D`, `14D`, `21D`, `28D`) with 2-decimal velocity `v2`, estimated weeks, arrival dates, proportional sufficiency bars, and ahead/late week badges, and redraw `ForecastBanner` into the visual `GOAL ARRIVAL ESTIMATIONS` card.

**Independent Test**: Evaluate `computeMultiHorizonForecast` and `ForecastBanner` for a cycle with a target weight and end date, verifying the Target Benchmark Header and all 4 two-line horizon rows with their sufficiency bars and ahead/late badges.

### Implementation & Tests for User Story 2

- [X] T008 [US2] Implement `computeMultiHorizonForecast` (both raw-parameter and `Cycle` overloads) evaluating `[7, 14, 21, 28]` day horizons with 2-decimal velocity `v2`, estimated weeks, projected arrival dates, proportional `barFraction`, and `deltaBadgeText` (`On time (-X.Xw)`, `On time`, `+X.Xw late`, `> 1 yr late`) in `app/src/main/java/com/fractanomics/crosstraining/data/analytics/FatLossAnalytics.kt`
- [X] T009 [US2] Add UI resolvers (`resolveHorizonRowLeftLabel`, `resolveHorizonRowRightValue`, `resolveHorizonRowBadgeText`, `resolveHorizonRowContentDescription`, `resolveMultiHorizonBenchmarkHeader`) and redraw `ForecastBanner` to render the visual `GOAL ARRIVAL ESTIMATIONS` card with Target Benchmark Header and 4 comparative sufficiency bar rows in `app/src/main/java/com/fractanomics/crosstraining/ui/screens/ProgressScreen.kt`
- [X] T010 [P] [US2] Add unit tests for `computeMultiHorizonForecast` covering on-time, late, open-ended (`endDate = null`), and imperial `lbs` projections in `app/src/test/java/com/fractanomics/crosstraining/data/analytics/FatLossAnalyticsTest.kt`

---

## Phase 5: User Story 3 - Per-Horizon Graceful States for Insufficient History, Stalled Paces & Terminal Cycle States (Priority: P2)

**Goal**: Ensure independent per-horizon fallback states (`Needs 21+ days`, `Needs 28+ days`, `Pace stalled`, `> 1 year`) and terminal container states (`GoalReached`, `CycleEnded`) work seamlessly and are verified across unit and Compose UI tests.

**Independent Test**: Verify that a gaining/stalled `7D` pace (`+0.15 kg/wk`) or missing `28D` pace (`null`) renders `"Pace stalled"` / `"Needs 28+ days"` on its own row while `14D` and `21D` still render full projections, and verify `360dp` / `fontScale = 1.3f` Compose rendering.

### Implementation & Tests for User Story 3

- [X] T011 [P] [US3] Add unit tests for per-horizon `Stalled`, `NotEnoughData`, `> 1 year` capped projections, `GoalReached`, and `CycleEnded` states in `app/src/test/java/com/fractanomics/crosstraining/data/analytics/FatLossAnalyticsTest.kt` and `app/src/test/java/com/fractanomics/crosstraining/ui/screens/WeightProgressionCardTest.kt`
- [X] T012 [US3] Update and expand Compose UI tests for the 2×2 Pace Grid (`7-Day Pace`, `14-Day Pace`, `21-Day Pace`, `28-Day Pace`) and the 4-row `GOAL ARRIVAL ESTIMATIONS` card (including `360dp` viewport at `fontScale = 1.3f`) in `app/src/androidTest/java/com/fractanomics/crosstraining/ui/screens/WeightProgressionCardComposeTest.kt`

---

## Phase 6: Polish & Cross-Cutting Concerns

**Purpose**: Documentation, full verification suite, APK sync, and Pull Request creation

- [X] T013 Update `CHANGELOG.md` under `## [Unreleased]` documenting the 2×2 2-decimal Pace Grid (`7D`, `14D`, `21D`, `28D`) and the visual Multi-Horizon Goal Estimations card
- [X] T014 Execute full pre-PR verification suite (`.\gradlew.bat testDebugUnitTest --no-daemon`, `.\scripts\tests\Invoke-ScriptTests.ps1`, `.\gradlew.bat lintDebug --no-daemon`, `.\gradlew.bat assembleDebug --no-daemon` + copy APK to `local_test\latest.apk`)
- [X] T015 Commit changes on `feat/005-multi-horizon-pace-estimations`, push branch, open Pull Request on GitHub, and verify remote CI checks pass

---

## Dependencies & Execution Order

- **Phase 1 (T001)** → **Phase 2 (T002, T003)** → **Phase 3 (T004–T007)** → **Phase 4 (T008–T010)** → **Phase 5 (T011–T012)** → **Phase 6 (T013–T015)**.
- Within Phases 3–5, test tasks in `FatLossAnalyticsTest.kt` and `WeightProgressionCardTest.kt` marked `[P]` can be authored in parallel once domain and UI functions are in place.
