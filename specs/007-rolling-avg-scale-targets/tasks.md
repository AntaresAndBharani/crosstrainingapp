# Tasks: Multi-Window Rolling Average Scale Targets (7D, 14D, 21D, 28D) in Pace & Linear Targets

**Input**: Design documents from `/specs/007-rolling-avg-scale-targets/`

**Prerequisites**: `plan.md`, `spec.md`, `research.md`, `data-model.md`, `contracts/analytics-contract.md`

**Tests**: Included in alignment with Constitution Principle III (Test-First & 100% CI Parity).

**Organization**: Tasks are grouped by user story (`[US1]`, `[US2]`, `[US3]`) to enable independent implementation and testing.

## Format: `[ID] [P?] [Story] Description`

- **[P]**: Can run in parallel (different files, no dependencies)
- **[Story]**: Which user story this task belongs to (e.g., `US1`, `US2`, `US3`)

---

## Phase 1: Setup (Shared Infrastructure)

**Purpose**: Verify branch state and baseline models before domain and UI changes.

- [X] T001 Verify active feature branch `feat/007-rolling-avg-scale-targets` and Spec-Kit feature pointer in `.specify/feature.json`

---

## Phase 2: Foundational (Blocking Prerequisites)

**Purpose**: Extend `TargetRowState` and `PaceTargetsState.Available` data models in `FatLossAnalytics.kt` to carry `windowDays: Int` and the four rolling average rows (`7D`, `14D`, `21D`, `28D`).

- [X] T002 Add `val windowDays: Int = 7` and `val rowHeader: String` to `TargetRowState`, and update `PaceTargetsState.Available` with `sevenDayAvgRow`, `fourteenDayAvgRow`, `twentyOneDayAvgRow`, `twentyEightDayAvgRow`, and `val rows: List<TargetRowState>` in `app/src/main/java/com/fractanomics/crosstraining/data/analytics/FatLossAnalytics.kt`

---

## Phase 3: User Story 1 - Daily Scale Weights Needed Today & Tomorrow to Hit 7D Rolling Average Targets (Priority: P1) 🎯 MVP

**Goal**: Solve for the exact 2-decimal daily scale weight needed today (`Expected Today`) and tomorrow (`Target Tomorrow`) so that today's and tomorrow's 7D rolling averages hit their required-pace targets, comparing `Expected Today` against today's actual weigh-in when logged.

**Independent Test**: Run `FatLossAnalyticsTest` verifying that for a 7D rolling window `[t - 6, t]`, `Expected Today` equals `round2((k_today + 1) * targetAvgToday - sumPriorToday)` and `Target Tomorrow` equals `round2((k_tomorrow + 1) * targetAvgTomorrow - sumPriorTomorrow)` in both `kg` and `lbs`.

### Implementation & Tests for User Story 1

- [X] T003 [US1] Implement `computeRollingWindowTargetRow` helper in `app/src/main/java/com/fractanomics/crosstraining/data/analytics/FatLossAnalytics.kt` solving `Expected Today` (`(k_today + 1) * targetAvgToday - sumPriorToday`) and `Target Tomorrow` (`(k_tomorrow + 1) * targetAvgTomorrow - sumPriorTomorrow`) for a given `windowDays`
- [X] T004 [US1] Update 7D row unit tests in `app/src/test/java/com/fractanomics/crosstraining/data/analytics/FatLossAnalyticsTest.kt` to verify solved daily scale weights for `Expected Today` and `Target Tomorrow`, window-sum identity reconciliation, and unlogged today behavior

---

## Phase 4: User Story 2 - Multi-Window Rolling Average Rows (14D, 21D, 28D) & Removal of Redundant Daily Weigh-In Row (Priority: P1)

**Goal**: Compute `7D`, `14D`, `21D`, and `28D` rolling average target rows in `FatLossAnalytics.compute7DayPaceTargets` (each anchored to `t - N` plus `N * dailyRequiredRate`) and render all four rows in `TheoreticalPaceTargetsBox` while removing the redundant `DAILY WEIGH-IN` row.

**Independent Test**: Run `FatLossAnalyticsTest`, `WeightProgressionCardTest`, and `WeightProgressionCardComposeTest` verifying that `PACE & LINEAR TARGETS` computes and renders `7D ROLLING AVERAGE`, `14D ROLLING AVERAGE`, `21D ROLLING AVERAGE`, and `28D ROLLING AVERAGE` and does not render `DAILY WEIGH-IN` inside `TheoreticalPaceTargetsBox`.

### Implementation & Tests for User Story 2

- [X] T005 [US2] Wire `compute7DayPaceTargets` in `app/src/main/java/com/fractanomics/crosstraining/data/analytics/FatLossAnalytics.kt` to compute `sevenDayAvgRow` (`N=7`), `fourteenDayAvgRow` (`N=14`), `twentyOneDayAvgRow` (`N=21`), and `twentyEightDayAvgRow` (`N=28`)
- [X] T006 [US2] Replace `TARGET_ROW_DAILY_HEADER` with `TARGET_ROW_14D_AVG_HEADER`, `TARGET_ROW_21D_AVG_HEADER`, and `TARGET_ROW_28D_AVG_HEADER` and update `TheoreticalPaceTargetsBox` in `app/src/main/java/com/fractanomics/crosstraining/ui/screens/ProgressScreen.kt` to render all 4 rolling average rows (`paceTargets.rows`) separated by horizontal dividers
- [X] T007 [P] [US2] Add multi-window (`7D`, `14D`, `21D`, `28D`) unit tests in `app/src/test/java/com/fractanomics/crosstraining/data/analytics/FatLossAnalyticsTest.kt`
- [X] T008 [P] [US2] Update UI resolver unit tests in `app/src/test/java/com/fractanomics/crosstraining/ui/screens/WeightProgressionCardTest.kt` and Compose UI tests in `app/src/androidTest/java/com/fractanomics/crosstraining/ui/screens/WeightProgressionCardComposeTest.kt` for the 4 rolling average rows

---

## Phase 5: User Story 3 - Horizon-Specific History Maturity & Missing-Log Fallbacks (Priority: P2)

**Goal**: Emit horizon-specific history prompts (`"Needs 7 days history"`, `"Needs 14 days history"`, `"Needs 21 days history"`, `"Needs 28 days history"`) and missing-log prompts (`"Needs log from <d MMM>"`) per `ND` row when `[t - 2N + 1, t - N]` has no entries.

**Independent Test**: Run `FatLossAnalyticsTest` and `WeightProgressionCardTest` verifying that a 10-day cycle resolves `7D` with numeric `Expected Today` while `14D`, `21D`, and `28D` emit `InsufficientHistory` formatted as `"Needs 14 days history"`, `"Needs 21 days history"`, and `"Needs 28 days history"`.

### Implementation & Tests for User Story 3

- [X] T009 [US3] Add `windowDays: Int = 7` parameter to `resolveExpectedTodaySubtitle`, `resolveExpectedTodayContentDescription`, `resolveTargetTomorrowSubtitle`, and `resolveTargetTomorrowContentDescription`, and pass `rowState.windowDays` from `TargetRowSection` in `app/src/main/java/com/fractanomics/crosstraining/ui/screens/ProgressScreen.kt`
- [X] T010 [P] [US3] Add unit tests in `app/src/test/java/com/fractanomics/crosstraining/data/analytics/FatLossAnalyticsTest.kt` and `app/src/test/java/com/fractanomics/crosstraining/ui/screens/WeightProgressionCardTest.kt` covering horizon-specific `"Needs N days history"` and `"Needs log from <d MMM>"` fallbacks across `7D`, `14D`, `21D`, and `28D`

---

## Phase 6: Polish & Cross-Cutting Concerns

**Purpose**: Full verification suite (`testDebugUnitTest`, `Invoke-ScriptTests.ps1`, `lintDebug`) and `CHANGELOG.md` update.

- [X] T011 Run `./gradlew.bat testDebugUnitTest --no-daemon`, `./scripts/tests/Invoke-ScriptTests.ps1`, and `./gradlew.bat lintDebug --no-daemon` to verify 100% green test and lint parity
- [X] T012 Update `CHANGELOG.md` under `## [Unreleased]` with a summary of the 7D/14D/21D/28D rolling average scale targets feature

---

## Dependencies & Execution Order

- **Phase 1 (Setup)** → **Phase 2 (Foundational)** → **Phase 3 (US1)** → **Phase 4 (US2)** → **Phase 5 (US3)** → **Phase 6 (Polish)**.
- Tasks `T007`, `T008`, and `T010` touch separate test files and can be executed in parallel once `T005`, `T006`, and `T009` are in place.

## Parallel Example: User Story 2

```bash
Task: "T007 [P] [US2] Add multi-window (7D, 14D, 21D, 28D) unit tests in app/src/test/java/com/fractanomics/crosstraining/data/analytics/FatLossAnalyticsTest.kt"
Task: "T008 [P] [US2] Update UI resolver unit tests in app/src/test/java/com/fractanomics/crosstraining/ui/screens/WeightProgressionCardTest.kt"
```

## Implementation Strategy

1. Complete Phase 1 & Phase 2 (`TargetRowState` & `PaceTargetsState.Available` models).
2. Implement `computeRollingWindowTargetRow` for `US1` and wire all 4 windows (`7D`, `14D`, `21D`, `28D`) for `US2`.
3. Update `ProgressScreen.kt` (`TheoreticalPaceTargetsBox` and horizon-aware subtitle resolvers) for `US2` and `US3`.
4. Update and expand unit & Compose tests across `FatLossAnalyticsTest.kt`, `WeightProgressionCardTest.kt`, and `WeightProgressionCardComposeTest.kt`, then run the full verification suite.
