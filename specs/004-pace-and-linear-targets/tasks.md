---
description: "Task list for Required-Pace & Linear Cycle Weight Targets for 7D Average and Daily Weight"
---

# Tasks: Required-Pace & Linear Cycle Weight Targets for 7D Average and Daily Weight

**Input**: Design documents from `/specs/004-pace-and-linear-targets/`

**Prerequisites**: plan.md (required), spec.md (required for user stories), research.md, data-model.md, contracts/analytics-contract.md

**Tests**: Comprehensive JUnit 4 and Compose UI test tasks are included to enforce TDD and 100% CI parity.

**Organization**: Tasks are grouped by user story (`US1`, `US2`, `US3`) to enable clear, incremental implementation and verification.

## Format: `[ID] [P?] [Story] Description`

- **[P]**: Can run in parallel (different files, no dependencies)
- **[Story]**: Which user story this task belongs to (`[US1]`, `[US2]`, `[US3]`)
- Exact file paths are included in every description

---

## Phase 1: Setup (Shared Infrastructure)

**Purpose**: Verify baseline test suite and project setup prior to domain and UI modifications.

- [X] T001 Verify baseline compilation and unit tests in `app/src/main/java/com/fractanomics/crosstraining/data/analytics/FatLossAnalytics.kt`

---

## Phase 2: Foundational (Blocking Prerequisites)

**Purpose**: Core 2-decimal math helpers (`round2`, `round2InUnit`, `diff2`) and updated domain state hierarchy (`PaceTargetsState`, `TargetRowState`, `ColumnState`) required by all user stories.

- [X] T002 Add `round2`, `round2InUnit`, and `diff2` helpers in `app/src/main/java/com/fractanomics/crosstraining/data/analytics/FatLossAnalytics.kt`
- [X] T003 Update `PaceTargetsState` (`Unavailable`, `GoalReached`, `CycleEnded`, `Available(sevenDayAvgRow, dailyWeightRow)`), `TargetRowState(linearToday, expectedToday, targetTomorrow)`, and `ColumnState` (`Value` with `isDailyStep: Boolean`, `NeedsLog`, `NeedsTodayLog`, `InsufficientHistory`) in `app/src/main/java/com/fractanomics/crosstraining/data/analytics/FatLossAnalytics.kt`

**Checkpoint**: Foundational domain types and 2-decimal precision helpers ready.

---

## Phase 3: User Story 1 - Required-Pace Expected Today & Target Tomorrow for 7D Average and Daily Weight (Priority: P1) 🎯 MVP

**Goal**: Compute and display 2-decimal `Expected Today` (`W_{t-7d} + weeklyRequiredRate`) and `Target Tomorrow` (`W_today + dailyRequiredRate`) across both the `7D ROLLING AVERAGE` row and the `DAILY WEIGH-IN` row using the exact unrounded Required Pace to `cycle.endDate`.

**Independent Test**: Verify via unit tests in `FatLossAnalyticsTest.kt` and `WeightProgressionCardTest.kt` that `Expected Today` and `Target Tomorrow` reconcile to `0.01` precision (`%.2f`) in both `kg` and `lbs`, including sub-`0.1` daily steps (`-0.06 kg/day`).

### Implementation & Tests for User Story 1

- [X] T004 [US1] Implement exact Required Pace calculation (`dailyRequiredRateInUnit`, `weeklyRequiredRateInUnit`), `Expected Today`, and `Target Tomorrow` for both `sevenDayAvgRow` and `dailyWeightRow` inside `compute7DayPaceTargets` in `app/src/main/java/com/fractanomics/crosstraining/data/analytics/FatLossAnalytics.kt`
- [X] T005 [US1] Update 2-decimal formatting resolvers (`resolveExpectedTodayValue`, `resolveExpectedTodaySubtitle`, `resolveTargetTomorrowValue`, `resolveTargetTomorrowSubtitle`, and cell-level resolvers) in `app/src/main/java/com/fractanomics/crosstraining/ui/screens/ProgressScreen.kt`
- [X] T006 [P] [US1] Add unit tests for 2-decimal `Expected Today` and `Target Tomorrow` across 7D Average and Daily Weight in `kg` and `lbs` in `app/src/test/java/com/fractanomics/crosstraining/data/analytics/FatLossAnalyticsTest.kt`

**Checkpoint**: User Story 1 domain and resolver logic verified.

---

## Phase 4: User Story 2 - Theoretical Linear Cycle Weight & Comparison with Key Measures (Priority: P1)

**Goal**: Compute 2-decimal `Linear Today` (`W_start + (W_target - W_start) * elapsedDays / totalCycleDays`) and compare it against both today's `7D AVG` and today's `TODAY` daily weigh-in inside the 3-column × 2-row `PACE & LINEAR TARGETS` UI container.

**Independent Test**: Verify via `FatLossAnalyticsTest.kt` and `WeightProgressionCardTest.kt` that `Linear Today` interpolates accurately between `startDate` and `endDate` and displays row-specific 2-decimal deltas (`-X.XX kg ahead`, `+X.XX kg off pace`, `On pace`) alongside `Expected Today` and `Target Tomorrow` in `TheoreticalPaceTargetsBox`.

### Implementation & Tests for User Story 2

- [X] T007 [US2] Implement `Linear Today` interpolation (`wLinearDisplay`) and 2-decimal delta/status comparisons for `sevenDayAvgRow.linearToday` and `dailyWeightRow.linearToday` in `app/src/main/java/com/fractanomics/crosstraining/data/analytics/FatLossAnalytics.kt`
- [X] T008 [US2] Redesign `TheoreticalPaceTargetsBox` to render the `"PACE & LINEAR TARGETS"` 3-column (`Linear Today` | `Expected Today` | `Target Tomorrow`) × 2-row (`7D ROLLING AVERAGE` and `DAILY WEIGH-IN`) layout with accessibility semantics in `app/src/main/java/com/fractanomics/crosstraining/ui/screens/ProgressScreen.kt`
- [X] T009 [P] [US2] Add unit tests for `Linear Today` interpolation and delta comparisons against `7D AVG` and `TODAY` daily weight in `app/src/test/java/com/fractanomics/crosstraining/data/analytics/FatLossAnalyticsTest.kt` and `app/src/test/java/com/fractanomics/crosstraining/ui/screens/WeightProgressionCardTest.kt`

**Checkpoint**: User Story 2 linear cycle targets and 3×2 UI container verified.

---

## Phase 5: User Story 3 - Graceful Degradation for Unlogged Days & Cycle Boundary States (Priority: P2)

**Goal**: Ensure unlogged `today` (`NeedsTodayLog`), unlogged `t - 7d` (`NeedsLog` / `InsufficientHistory`), non-stalling on gain weeks, and container boundary states (`Unavailable`, `GoalReached`, `CycleEnded`) work seamlessly across domain, unit tests, and Compose UI tests.

**Independent Test**: Verify via unit and Compose UI tests that unlogged days degrade per cell without blocking the 7D Average row, gain weeks remain `Available`, and boundary states render cleanly at `360dp` and `fontScale = 1.3f`.

### Implementation & Tests for User Story 3

- [X] T010 [US3] Implement container boundary state precedence (`Unavailable` -> `GoalReached` -> `CycleEnded` -> `Available`) and per-cell missing-log fallbacks (`NeedsTodayLog`, `NeedsLog`, `InsufficientHistory`) in `app/src/main/java/com/fractanomics/crosstraining/data/analytics/FatLossAnalytics.kt` and `app/src/main/java/com/fractanomics/crosstraining/ui/screens/ProgressScreen.kt`
- [X] T011 [P] [US3] Add unit tests for unlogged today, missing `t - 7d`, non-stalling on gain weeks, and boundary states (`Unavailable`, `GoalReached`, `CycleEnded`) in `app/src/test/java/com/fractanomics/crosstraining/data/analytics/FatLossAnalyticsTest.kt` and `app/src/test/java/com/fractanomics/crosstraining/ui/screens/WeightProgressionCardTest.kt`
- [X] T012 [P] [US3] Update Compose UI tests for `"PACE & LINEAR TARGETS"` 3×2 layout and `360dp` / `fontScale = 1.3f` rendering in `app/src/androidTest/java/com/fractanomics/crosstraining/ui/screens/WeightProgressionCardComposeTest.kt`

**Checkpoint**: All user stories complete and verified.

---

## Phase 6: Polish & Cross-Cutting Concerns

**Purpose**: Changelog update, full pre-PR verification suite, E2E artifacts/APK sync, and PR creation.

- [X] T013 Update `CHANGELOG.md` under `## [Unreleased]` documenting the new Required-Pace & Linear Cycle Weight Targets feature
- [X] T014 Execute full pre-PR verification (`.\gradlew.bat testDebugUnitTest --no-daemon`, `.\scripts\tests\Invoke-ScriptTests.ps1`, `.\gradlew.bat lintDebug --no-daemon`, `.\gradlew.bat assembleDebug --no-daemon`) and sync APK to `local_test\latest.apk`

---

## Dependencies & Execution Order

- **Phase 1 (T001)** → **Phase 2 (T002–T003)** → **Phase 3 (T004–T006)** → **Phase 4 (T007–T009)** → **Phase 5 (T010–T012)** → **Phase 6 (T013–T014)**.
- Parallelizable test tasks (`T006`, `T009`, `T011`, `T012`) can be authored in lockstep with their corresponding domain/UI tasks.
