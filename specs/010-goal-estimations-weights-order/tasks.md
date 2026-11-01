---
description: "Task list for Goal Arrival Estimations Average Weights & Section Reordering"
---

# Tasks: Goal Arrival Estimations Average Weights & Section Reordering

**Input**: Design documents from `/specs/010-goal-estimations-weights-order/`

**Prerequisites**: plan.md (required), spec.md (required for user stories), research.md, data-model.md, contracts/

**Tests**: Unit and Compose UI tests are included in accordance with Constitution Principle III (Test-First & 100% CI Parity).

**Organization**: Tasks are grouped by phase and user story to enable independent implementation and verification.

## Format: `[ID] [P?] [Story] Description`

- **[P]**: Can run in parallel (different files, no dependencies)
- **[Story]**: Which user story this task belongs to (e.g., US1, US2, US3)
- Exact file paths are included in every task description

---

## Phase 1: Setup (Shared Infrastructure)

**Purpose**: Verify branch state and inspect existing Multi-Horizon Goal Estimations models and UI layout

- [X] T001 Inspect `HorizonRowState`, `HorizonEstimationRow`, and `computeMultiHorizonForecast` in `app/src/main/java/com/fractanomics/crosstraining/data/analytics/FatLossAnalytics.kt` and `WeightProgressionHeroCard` / `MultiHorizonGoalEstimationsBanner` in `app/src/main/java/com/fractanomics/crosstraining/ui/screens/ProgressScreen.kt`

---

## Phase 2: Foundational (Blocking Prerequisites)

**Purpose**: Extend domain state models (`HorizonRowState.Projected` and `HorizonRowState.Stalled`) and row formatting methods (`HorizonEstimationRow.formatLeftLabel` and `formatWeightedSummary`)

**⚠️ CRITICAL**: Must be complete before user story implementation and tests

- [X] T002 Add `val averageWeightDisplay: String? = null` to `HorizonRowState.Projected` and `HorizonRowState.Stalled`, and update `HorizonEstimationRow.formatLeftLabel()` and `HorizonEstimationRow.formatWeightedSummary()` to include ` · ${state.averageWeightDisplay}` when non-null in `app/src/main/java/com/fractanomics/crosstraining/data/analytics/FatLossAnalytics.kt`

**Checkpoint**: Foundation ready — `HorizonRowState` and `HorizonEstimationRow` support formatted average weights with backward-compatible `null` defaults

---

## Phase 3: User Story 1 - Display Rolling & Weighted Average Weights in Goal Arrival Estimations (Priority: P1) 🎯 MVP

**Goal**: Compute 1-decimal rolling average weights (`7D`, `14D`, `21D`, `28D`) and the `4:3:2:1` recency-weighted average weight in `computeMultiHorizonForecast`, populate `averageWeightDisplay` on each row, and keep projected weeks and arrival dates anchored to `remainingInUnit`.

**Independent Test**: Run `FatLossAnalyticsTest` and `WeightProgressionCardTest` to verify `averageWeightDisplay`, `formatLeftLabel()`, and `formatWeightedSummary()` across full and partial horizon availability, `Projected` and `Stalled` states, while confirming `weeks` and `targetDate` remain unchanged.

### Tests for User Story 1

- [X] T003 [P] [US1] Update existing multi-horizon forecast assertions and add unit tests for 7D/14D/21D/28D rolling average weights and `4:3:2:1` weighted average weight (full horizons, partial horizons, stalled states, and unchanged projected weeks) in `app/src/test/java/com/fractanomics/crosstraining/data/analytics/FatLossAnalyticsTest.kt`
- [X] T004 [P] [US1] Update and add UI string resolver tests for `resolveHorizonRowLeftLabel` and `resolveWeightedRowSummaryText` with `averageWeightDisplay` populated in `app/src/test/java/com/fractanomics/crosstraining/ui/screens/WeightProgressionCardTest.kt`

### Implementation for User Story 1

- [X] T005 [US1] Compute per-horizon 1-decimal display-unit rolling average weights (`[today - (w - 1), today]` with fallback to `newest.date` / `currentWeightKg`) and the `4:3:2:1` weighted average weight in `FatLossAnalytics.computeMultiHorizonForecast`, passing `averageWeightDisplay` into `buildProjectedOrStalledHorizonRow` in `app/src/main/java/com/fractanomics/crosstraining/data/analytics/FatLossAnalytics.kt`

**Checkpoint**: User Story 1 complete — all active horizon rows and the Weighted Average row compute and format their average weights accurately

---

## Phase 4: User Story 2 - Move Goal Arrival Estimations After Daily Weigh-In and Before Pace & Linear Targets (Priority: P1)

**Goal**: Position `ForecastBanner` (`GOAL ARRIVAL ESTIMATIONS`) immediately after the Hero Centerpiece (`ROLLING AVERAGES` + `DAILY WEIGH-IN` + streak pill) and immediately before `TheoreticalPaceTargetsBox` (`PACE & LINEAR TARGETS`) in `WeightProgressionHeroCard`, ensuring responsive layout without clipping.

**Independent Test**: Inspect and test `WeightProgressionHeroCard` in `ProgressScreen.kt` and `WeightProgressionCardComposeTest.kt` to verify section ordering and responsive rendering at `360dp` width and `1.3f` font scale.

### Tests for User Story 2

- [X] T006 [P] [US2] Update Compose UI test assertions in `app/src/androidTest/java/com/fractanomics/crosstraining/ui/screens/WeightProgressionCardComposeTest.kt` to verify `GOAL ARRIVAL ESTIMATIONS` renders with average weights and is positioned before `PACE & LINEAR TARGETS`

### Implementation for User Story 2

- [X] T007 [US2] Move the `ForecastBanner` block in `WeightProgressionHeroCard` to render immediately after the Hero Centerpiece `Column` (after `SameDayWeightTrioRow` and streak pill) and immediately before `TheoreticalPaceTargetsBox`, and ensure `HorizonEstimationRowItem` handles the wider left label cleanly in `app/src/main/java/com/fractanomics/crosstraining/ui/screens/ProgressScreen.kt`

**Checkpoint**: User Story 2 complete — `GOAL ARRIVAL ESTIMATIONS` appears after Daily Weigh-In and before Pace & Linear Targets

---

## Phase 5: User Story 3 - Accessibility Semantics & Display-Unit Consistency (Priority: P2)

**Goal**: Ensure `lbs` unit conversion and merged TalkBack `contentDescription` strings (`resolveHorizonRowContentDescription`) accurately include the formatted rolling/weighted average weights.

**Independent Test**: Run unit tests in `FatLossAnalyticsTest.kt` and `WeightProgressionCardTest.kt` verifying `lbs` conversion and `resolveHorizonRowContentDescription` output.

### Tests & Verification for User Story 3

- [X] T008 [P] [US3] Add unit tests for `lbs` display-unit conversion of rolling/weighted average weights in `app/src/test/java/com/fractanomics/crosstraining/data/analytics/FatLossAnalyticsTest.kt` and TalkBack `resolveHorizonRowContentDescription` with weights in `app/src/test/java/com/fractanomics/crosstraining/ui/screens/WeightProgressionCardTest.kt`

**Checkpoint**: All user stories complete and independently tested

---

## Phase 6: Polish & Cross-Cutting Concerns

**Purpose**: Full verification suite, lint, script tests, APK sync, and changelog

- [X] T009 Run `.\gradlew.bat testDebugUnitTest --no-daemon`, `.\scripts\tests\Invoke-ScriptTests.ps1`, and `.\gradlew.bat lintDebug --no-daemon` to verify 100% green test and lint parity across the repository
- [X] T010 Update `CHANGELOG.md` under `## [Unreleased]` documenting the addition of rolling/weighted average weights to `GOAL ARRIVAL ESTIMATIONS` and moving the section after `DAILY WEIGH-IN` and before `PACE & LINEAR TARGETS`

---

## Dependencies & Execution Order

- **Phase 1 (Setup)**: Starts immediately (`T001`).
- **Phase 2 (Foundational)**: Depends on `T001` (`T002`). Blocks User Stories 1–3.
- **Phase 3 (US1)**: Depends on `T002` (`T003`, `T004`, `T005`).
- **Phase 4 (US2)**: Depends on `T002` and `T005` (`T006`, `T007`).
- **Phase 5 (US3)**: Depends on `T005` and `T007` (`T008`).
- **Phase 6 (Polish)**: Depends on all user stories completing (`T009`, `T010`).

## Parallel Opportunities

- `T003` (`FatLossAnalyticsTest.kt`) and `T004` (`WeightProgressionCardTest.kt`) touch separate test files and can be developed in parallel.
- `T006` (`WeightProgressionCardComposeTest.kt`) and `T008` can be prepared in parallel with UI ordering changes.

## Implementation Strategy

1. Complete Phase 1 & Phase 2 (`T001`–`T002`) to establish the extended `HorizonRowState` and `HorizonEstimationRow` contracts.
2. Implement User Story 1 (`T003`–`T005`) in `FatLossAnalytics.kt` and verify unit tests.
3. Implement User Story 2 (`T006`–`T007`) in `ProgressScreen.kt` to move `ForecastBanner` before `TheoreticalPaceTargetsBox`.
4. Verify User Story 3 (`T008`) for `lbs` and TalkBack accessibility semantics, then run full verification (`T009`–`T010`).
