---
description: "Task list for Weighted Average Pace Estimation & Goal Arrival Estimations Redesign"
---

# Tasks: Weighted Average Pace Estimation & Goal Arrival Estimations Redesign

**Input**: Design documents from `/specs/009-weighted-goal-estimations/`

**Prerequisites**: plan.md (required), spec.md (required for user stories), research.md, data-model.md, contracts/analytics-contract.md

**Tests**: Unit tests and Compose UI tests are included in accordance with Constitution Principle III (Test-First & 100% CI Parity).

**Organization**: Tasks are grouped by user story to enable independent implementation and testing of each story.

## Format: `[ID] [P?] [Story] Description`

- **[P]**: Can run in parallel (different files, no dependencies)
- **[Story]**: Which user story this task belongs to (`[US1]`, `[US2]`, `[US3]`)
- Exact file paths are included in descriptions

---

## Phase 1: Setup (Shared Infrastructure)

**Purpose**: Verify baseline project state and constants before modifying domain analytics and UI components

- [X] T001 Verify feature branch `feat/009-weighted-goal-estimations` and spec artifacts in `specs/009-weighted-goal-estimations/plan.md`

---

## Phase 2: Foundational (Blocking Prerequisites)

**Purpose**: Core data model extensions and horizon weight constants required by all user stories

**⚠️ CRITICAL**: No user story work can begin until this phase is complete

- [X] T002 Define `HORIZON_WEIGHTS = mapOf(7 to 4, 14 to 3, 21 to 2, 28 to 1)` (`40% 7D`, `30% 14D`, `20% 21D`, `10% 28D`) in `app/src/main/java/com/fractanomics/crosstraining/data/analytics/FatLossAnalytics.kt`
- [X] T003 Extend `HorizonEstimationRow` with `isWeightedAverage: Boolean = windowDays == 0`, updated `formatLeftLabel()`, `formatRightValue()`, and `formatWeightedSummary()` (`"(Weighted pace of <paceDisplay2> · <weeks> wks · <d MMM yyyy>)"`, `"(Weighted pace of <paceDisplay2> · > 1 year)"`, `"(Weighted pace of <paceDisplay2> · Pace stalled)"`, `"(Needs 7+ days)"`) in `app/src/main/java/com/fractanomics/crosstraining/data/analytics/FatLossAnalytics.kt`
- [X] T004 Extend `MultiHorizonForecastState.Active` with `val weightedAverageRow: HorizonEstimationRow` (with default fallback for backward compatibility while keeping `rows` as the 4 historical horizons `7, 14, 21, 28`) in `app/src/main/java/com/fractanomics/crosstraining/data/analytics/FatLossAnalytics.kt`

**Checkpoint**: Foundation ready — user story implementation can now proceed

---

## Phase 3: User Story 1 - Weighted Average Pace Estimation Above 7D–28D Horizons (Priority: P1) 🎯 MVP

**Goal**: Compute the dynamically re-normalized `4:3:2:1` weighted average pace across available horizons (`7D`, `14D`, `21D`, `28D`) using signed 2-decimal display-unit rates and populate `weightedAverageRow` in `computeMultiHorizonForecast`.

**Independent Test**: Run `FatLossAnalyticsTest` verifying `4:3:2:1` full-horizon weighting, partial-horizon (`7D`–`21D`) re-normalization (`divisor = 9`), signed blending when `7D` is positive/gaining while `14D`–`28D` are in deficit, stalled/capped states, and `lbs` display-unit reconciliation.

### Tests for User Story 1

- [X] T005 [P] [US1] Add unit tests for `4:3:2:1` full and partial horizon weighted average pace calculation, signed-rate blending, stalled/capped states, and `lbs` reconciliation in `app/src/test/java/com/fractanomics/crosstraining/data/analytics/FatLossAnalyticsTest.kt`

### Implementation for User Story 1

- [X] T006 [US1] Implement `4:3:2:1` dynamically re-normalized weighted average pace calculation and `weightedAverageRow` projection inside `computeMultiHorizonForecast` (including `benchmarkV2` incorporation when `requiredPace == null`) in `app/src/main/java/com/fractanomics/crosstraining/data/analytics/FatLossAnalytics.kt`

**Checkpoint**: User Story 1 domain analytics and unit tests pass independently

---

## Phase 4: User Story 2 - Redesigned Goal Arrival Estimations Card Hierarchy & Visual Polish (Priority: P1)

**Goal**: Redesign `MultiHorizonGoalEstimationsBanner` (`Active` state) to display the `Current Goal & Target` sub-header, the highlighted primary `Weighted Average` row with trend/sparkle indicator and pill-shaped teal/emerald progress track above a horizontal divider, and the 4 historical breakdown rows (`7D`, `14D`, `21D`, `28D`).

**Independent Test**: Run `WeightProgressionCardTest` verifying `CURRENT_GOAL_AND_TARGET_HEADER`, `WEIGHTED_AVERAGE_ROW_TITLE`, `resolveWeightedRowSummaryText`, and `resolveHorizonRowLeftLabel` / `resolveHorizonRowRightValue` / `resolveHorizonRowBadgeText`.

### Tests for User Story 2

- [X] T007 [P] [US2] Add unit tests for `CURRENT_GOAL_AND_TARGET_HEADER`, `WEIGHTED_AVERAGE_ROW_TITLE`, and `resolveWeightedRowSummaryText` across `Projected`, `Capped`, and `Stalled` states in `app/src/test/java/com/fractanomics/crosstraining/ui/screens/WeightProgressionCardTest.kt`

### Implementation for User Story 2

- [X] T008 [US2] Add `CURRENT_GOAL_AND_TARGET_HEADER = "Current Goal & Target"`, `WEIGHTED_AVERAGE_ROW_TITLE = "Weighted Average"`, and `resolveWeightedRowSummaryText(row: HorizonEstimationRow)` in `app/src/main/java/com/fractanomics/crosstraining/ui/screens/ProgressScreen.kt`
- [X] T009 [US2] Redesign `MultiHorizonGoalEstimationsBanner` (`Active` state) and add `WeightedAverageEstimationRowItem` with `Current Goal & Target` sub-header, trend/sparkle indicator, primary weighted average summary, glowing/teal-green pill progress bar, mid-card horizontal divider, and refined `7D`–`28D` rows in `app/src/main/java/com/fractanomics/crosstraining/ui/screens/ProgressScreen.kt`

**Checkpoint**: User Stories 1 and 2 work together with full UI resolver test coverage

---

## Phase 5: User Story 3 - Terminal States, Accessibility & Responsive Layout Guarantees (Priority: P2)

**Goal**: Ensure TalkBack merged accessibility `contentDescription` for `weightedAverageRow`, preserve terminal banners (`GoalReached`, `CycleEnded`, `NotEnoughData`), and guarantee zero text clipping or layout overflow on `360dp`-wide viewports up to `fontScale = 1.3f`.

**Independent Test**: Run `WeightProgressionCardTest` and verify `WeightProgressionCardComposeTest` assertions for `Current Goal & Target`, `Weighted Average`, and `360dp` / `1.3f` font scale non-overflow.

### Tests for User Story 3

- [X] T010 [P] [US3] Add TalkBack `resolveHorizonRowContentDescription` unit tests for `weightedAverageRow` (`Projected`, `Capped`, `Stalled`) in `app/src/test/java/com/fractanomics/crosstraining/ui/screens/WeightProgressionCardTest.kt`
- [X] T011 [P] [US3] Update Compose UI test `scenario18_twoByTwoPaceGridAndMultiHorizonGoalEstimationsRendersAt360dpFontScale13` to assert `Current Goal & Target` and `Weighted Average` non-overflow at `360dp` width and `1.3f` font scale in `app/src/androidTest/java/com/fractanomics/crosstraining/ui/screens/WeightProgressionCardComposeTest.kt`

### Implementation for User Story 3

- [X] T012 [US3] Update `resolveHorizonRowContentDescription(row: HorizonEstimationRow)` in `app/src/main/java/com/fractanomics/crosstraining/ui/screens/ProgressScreen.kt` to format merged TalkBack announcements for `isWeightedAverage` rows (`"Weighted Average (<summary>), <badge>"`) while preserving historical horizon announcements

**Checkpoint**: All user stories are complete and independently verified

---

## Phase 6: Polish & Cross-Cutting Concerns

**Purpose**: Full verification suite execution (`testDebugUnitTest`, `Invoke-ScriptTests.ps1`, `lintDebug`), APK sync, and `CHANGELOG.md` update

- [X] T013 Execute full unit test suite (`.\gradlew.bat testDebugUnitTest --no-daemon`), PowerShell script regression tests (`.\scripts\tests\Invoke-ScriptTests.ps1`), and Android Lint (`.\gradlew.bat lintDebug --no-daemon`), and update `CHANGELOG.md` under `## [Unreleased]`

---

## Dependencies & Execution Order

### Phase Dependencies

- **Setup (Phase 1)**: No dependencies — starts immediately.
- **Foundational (Phase 2)**: Depends on Phase 1 — blocks User Stories 1, 2, and 3.
- **User Story 1 (Phase 3)**: Depends on Phase 2 (`T002`–`T004`).
- **User Story 2 (Phase 4)**: Depends on Phase 2 and User Story 1 (`T006`).
- **User Story 3 (Phase 5)**: Depends on Phase 2 and User Story 2 (`T008`–`T009`).
- **Polish (Phase 6)**: Depends on all user stories (`T001`–`T012`) completing.

### Parallel Opportunities

- `T005` (`FatLossAnalyticsTest.kt`) and `T007`/`T010` (`WeightProgressionCardTest.kt`) and `T011` (`WeightProgressionCardComposeTest.kt`) touch separate test files and can be authored in parallel once foundational models (`T002`–`T004`) are in place.

---

## Parallel Example: User Stories 1–3 Test Authoring

```bash
Task: "Add unit tests for 4:3:2:1 weighted average pace calculation in app/src/test/java/com/fractanomics/crosstraining/data/analytics/FatLossAnalyticsTest.kt"
Task: "Add unit tests for UI resolvers and TalkBack semantics in app/src/test/java/com/fractanomics/crosstraining/ui/screens/WeightProgressionCardTest.kt"
Task: "Update 360dp / 1.3f Compose UI test in app/src/androidTest/java/com/fractanomics/crosstraining/ui/screens/WeightProgressionCardComposeTest.kt"
```

---

## Implementation Strategy

### MVP First (User Story 1 Only)

1. Complete Phase 1: Setup (`T001`)
2. Complete Phase 2: Foundational (`T002`–`T004`)
3. Complete Phase 3: User Story 1 (`T005`–`T006`)
4. Validate `FatLossAnalyticsTest`

### Incremental Delivery

1. Deliver domain math (`US1`) → verify with unit tests
2. Deliver visual card redesign (`US2`) + accessibility & responsive guarantees (`US3`) → verify with UI unit tests and full CI verification suite (`Phase 6`)
