---
description: "Task list for Universal Cycle Weight Tracking & Momentum Engine"
---

# Tasks: Universal Cycle Weight Tracking & Momentum Engine

**Input**: Design documents from `/specs/016-cycle-weight-momentum/`

**Prerequisites**: `plan.md`, `spec.md`, `research.md`, `data-model.md`, `contracts/cycle-weight-momentum-contract.md`

**Tests**: Unit tests are included per Constitution Principle III ("Test-First & Zero CI Regression").

**Organization**: Tasks are grouped by user story (`US1`–`US4`) to enable independent implementation and verification.

## Format: `[ID] [P?] [Story] Description`

- **[P]**: Can run in parallel (different files, no dependencies)
- **[Story]**: Which user story this task belongs to (`[US1]`, `[US2]`, `[US3]`, `[US4]`)
- Exact file paths are included in every task description

---

## Phase 1: Setup (Shared Infrastructure)

**Purpose**: Verify baseline project configuration and feature specification links.

- [X] T001 Verify active feature directory configuration in `.specify/feature.json` and Room v10 schema baseline in `app/schemas/com.fractanomics.crosstraining.data.AppDatabase/10.json`

---

## Phase 2: Foundational (Blocking Prerequisites)

**Purpose**: Core domain enums and backward-compatible `Cycle` subcycle metadata encoding required by all user stories.

- [X] T002 Define `WeightSubcycleType` (`WEIGHT_LOSS`: `-1.50%` to `-0.50%/wk`, `RECOMPOSITION`: `-0.50%` to `+0.25%/wk`, `WEIGHT_GAIN`: `+0.25%` to `+1.50%/wk`) and `WeightMeasureSource` (`REAL`, `THEORETICAL`) enums in `app/src/main/java/com/fractanomics/crosstraining/data/model/Enums.kt`
- [X] T003 Implement backward-compatible `[subcycle=WEIGHT_LOSS|RECOMPOSITION|WEIGHT_GAIN]` metadata encoding/parsing, `subcycleType`, `hasExplicitSubcycleType`, `cleanGoal`, and `withSubcycleType(...)` on `Cycle` in `app/src/main/java/com/fractanomics/crosstraining/data/model/Cycle.kt`
- [X] T004 Add `updateCycleSubcycleType(cycle: Cycle, subcycleType: WeightSubcycleType)` in `app/src/main/java/com/fractanomics/crosstraining/ui/AppViewModel.kt` and update `refreshActiveCycleBaseline()` in `app/src/main/java/com/fractanomics/crosstraining/data/Repository.kt` to support universal cycle baselines

**Checkpoint**: Foundation ready — `WeightSubcycleType`, `WeightMeasureSource`, `Cycle` metadata persistence, and `AppViewModel` subcycle update flow are available.

---

## Phase 3: User Story 1 - Daily Weight Timeline with Real vs. Theoretical Linear Interpolation & Cumulative Multi-Horizon Averages (Priority: P1) 🎯 MVP

**Goal**: Build the continuous daily weight timeline with `REAL` vs. `THEORETICAL` linear interpolation, cumulative cold-start and historical lookback moving averages (`7D`, `14D`, `21D`, `28D`), `3:4:2:1` Weekly Body Weight composite ($\text{BW}_{\text{weekly}}$), weekly `%` Momentum, 8-day warm-up calibration gate, diagnostic velocity zones, and dynamic finish weight range compounding.

**Independent Test**: Run `.\gradlew.bat testDebugUnitTest --tests "com.fractanomics.crosstraining.data.analytics.FatLossAnalyticsTest" --no-daemon` to verify exact daily linear interpolation, boundary recalculation on edit/delete, cold-start cumulative horizons (Days 1–28), historical lookback, `3:4:2:1` composite weight, and Momentum `%` math.

### Implementation & Tests for User Story 1

- [X] T005 [US1] Implement `DailyWeightPoint`, `DailyHorizonSnapshot`, `MomentumZone`, `CorridorComplianceStatus`, `HorizonMomentumRow`, `SubcyclePreviewProjection`, and `CycleMomentumState` data models and pure functions (`interpolateDailyWeightTimeline`, `computeDailyHorizonSnapshots`, `computeCycleMomentumState`, `computeCorridorGaugePosition`) in `app/src/main/java/com/fractanomics/crosstraining/data/analytics/FatLossAnalytics.kt`
- [X] T006 [P] [US1] Expose daily interpolated timeline helper in `app/src/main/java/com/fractanomics/crosstraining/data/analytics/WeightAnalytics.kt`
- [X] T007 [US1] Add comprehensive unit tests for `REAL` vs. `THEORETICAL` linear interpolation (including the Friday 80.0 kg -> Friday 76.5 kg `-0.5 kg/day` scenario), reactive boundary mutation, cold-start cumulative windows (Days 1–28), historical lookback, `3:4:2:1` Weekly Body Weight composite, Momentum `%`, diagnostic zones, and compounded finish weight projections in `app/src/test/java/com/fractanomics/crosstraining/data/analytics/FatLossAnalyticsTest.kt`

**Checkpoint**: User Story 1 domain engine is complete and verified by unit tests.

---

## Phase 4: User Story 2 - Universal Cycle Weight Tracking & Subcycle Corridor Goal Estimations Card (`SPEC-WEIGHT-CARD-01`) (Priority: P1)

**Goal**: Render the upgraded `GOAL ARRIVAL ESTIMATIONS` card (`SPEC-WEIGHT-CARD-01`) and universal `WeightProgressionCard` across all cycle types (`STRENGTH_WEIGHTLIFTING` and `FAT_LOSS_BODYBUILDING`), featuring the `[Subtype ▾]` selector pill, cycle countdown, subtype target corridor (`%/wk` and `kg/wk` or `lbs/wk`), dynamically compounded finish weight range, primary `✦ Weekly Body Weight` composite row with 3-zone Corridor Range Gauge, and `7D`, `14D`, `21D`, `28D` horizon rows.

**Independent Test**: Run `.\gradlew.bat testDebugUnitTest --tests "com.fractanomics.crosstraining.ui.screens.WeightProgressionCardTest" --no-daemon` to verify all `SPEC-WEIGHT-CARD-01` string/state resolvers, corridor gauge mappings, universal cycle rendering, and `kg`/`lbs` localization.

### Implementation & Tests for User Story 2

- [X] T008 [US2] Implement `SPEC-WEIGHT-CARD-01` state/text resolvers (`resolveSubcyclePillLabel`, `resolveCycleTimelineHeader`, `resolveSubtypeTargetHeader`, `resolveProjectedFinishHeader`, `resolvePrimaryCompositeSummary`, `resolveMomentumHorizonRowLabel`, `resolveMomentumRowContentDescription`), 3-zone `CorridorRangeGauge` composable, and upgraded `MultiHorizonGoalEstimationsBanner` & universal `WeightProgressionCard` in `app/src/main/java/com/fractanomics/crosstraining/ui/screens/ProgressScreen.kt`
- [X] T009 [US2] Add unit tests for `SPEC-WEIGHT-CARD-01` header resolvers, `3:4:2:1` primary composite row formatting, `7D`/`14D`/`21D`/`28D` horizon row formatting, 3-zone corridor gauge positions, cold-start calibration (`Calibrating (Day K/8)`), universal cycle visibility, and imperial `lbs` localization in `app/src/test/java/com/fractanomics/crosstraining/ui/screens/WeightProgressionCardTest.kt`

**Checkpoint**: User Story 2 (`SPEC-WEIGHT-CARD-01`) is complete and verified by unit tests.

---

## Phase 5: User Story 3 - Interactive Subcycle Type Selection in Card Header & Cycle Editor (`SPEC-WEIGHT-SUBTYPE-SHEET-01`) (Priority: P2)

**Goal**: Provide the `Select Subcycle Type` modal bottom sheet (`SPEC-WEIGHT-SUBTYPE-SHEET-01`) triggered from the `[Subtype ▾]` pill in `ProgressScreen.kt` with live finish-weight range previews for `Weight Loss`, `Recomposition`, and `Weight Gain`, and enable universal `WeightSubcycleType` & body weight configuration for all cycles in `CyclesScreen.kt`.

**Independent Test**: Run `.\gradlew.bat testDebugUnitTest --tests "com.fractanomics.crosstraining.ui.screens.CyclesScreenTest" --no-daemon` and `WeightProgressionCardTest` to verify `Cycle` subcycle metadata persistence, target weight validation per subcycle type, and `SPEC-WEIGHT-SUBTYPE-SHEET-01` live preview calculations.

### Implementation & Tests for User Story 3

- [X] T010 [US3] Implement `SubcycleTypePickerBottomSheet` (`SPEC-WEIGHT-SUBTYPE-SHEET-01`) with selectable radio cards (`Weight Loss`, `Recomposition`, `Weight Gain`), live projected finish weight windows, `[ Apply Subcycle Type ]` action, and snackbar confirmation in `app/src/main/java/com/fractanomics/crosstraining/ui/screens/ProgressScreen.kt`
- [X] T011 [US3] Update `CycleEditorDialog` and `CycleCard` in `app/src/main/java/com/fractanomics/crosstraining/ui/screens/CyclesScreen.kt` so any cycle (`STRENGTH_WEIGHTLIFTING` or `FAT_LOSS_BODYBUILDING`) can select a `WeightSubcycleType` (`Weight Loss`, `Recomposition`, `Weight Gain`), configure optional starting/target weights with subcycle-aware validation (`FatLossValidation.isTargetWeightInvalidForSubcycle`), and display `cleanGoal` and subcycle badge
- [X] T012 [US3] Add unit tests for `Cycle` subcycle metadata encoding/decoding, universal cycle subcycle selection, subcycle-aware target weight validation, and `SPEC-WEIGHT-SUBTYPE-SHEET-01` preview state in `app/src/test/java/com/fractanomics/crosstraining/ui/screens/CyclesScreenTest.kt`

**Checkpoint**: User Story 3 (`SPEC-WEIGHT-SUBTYPE-SHEET-01` and `CyclesScreen` subcycle selector) is complete and verified.

---

## Phase 6: User Story 4 - Real vs. Theoretical Weight History Inspection & Coaching MCP Analytics (Priority: P3)

**Goal**: Allow athletes to inspect both `REAL` and interpolated `THEORETICAL` daily weights in the Body Weight history view and tap any `THEORETICAL` entry to edit and promote it to `REAL`, and expose the `get_weight_momentum_analytics` tool in `mcp-server/`.

**Independent Test**: Run `npm test` in `mcp-server/` and `.\gradlew.bat testDebugUnitTest --no-daemon` to verify daily `REAL`/`THEORETICAL` history promotion and the MCP weight momentum analytics payload.

### Implementation & Tests for User Story 4

- [X] T013 [US4] Enhance `WeightOverviewContent` in `app/src/main/java/com/fractanomics/crosstraining/ui/screens/ProgressScreen.kt` with a toggle/display for interpolated `Theoretical` daily weights alongside `Real` weigh-ins so tapping any `Theoretical` day opens `WeightEntryBottomSheet` and promotes it to `Real` on save
- [X] T014 [P] [US4] Implement daily weight linear interpolation, cumulative/lookback `7D`/`14D`/`21D`/`28D` horizons, `3:4:2:1` Weekly Body Weight composite, Momentum `%`, and subcycle corridor projection in `mcp-server/src/schemas.ts` and expose `get_weight_momentum_analytics` in `mcp-server/src/mcp.ts`
- [X] T015 [US4] Add MCP server unit tests for `get_weight_momentum_analytics` (`REAL` vs `THEORETICAL` interpolation, cold-start calibration, `3:4:2:1` composite, and subcycle corridor projection) in `mcp-server/test/mcp-server.test.ts`

**Checkpoint**: All 4 user stories are complete and independently tested.

---

## Phase 7: Polish & Cross-Cutting Concerns

**Purpose**: Full verification suite, changelog maintenance, and release readiness.

- [X] T016 Run full verification suite (`.\gradlew.bat testDebugUnitTest --no-daemon`, `.\scripts\tests\Invoke-ScriptTests.ps1`, `.\gradlew.bat lintDebug --no-daemon`, and `npm test` in `mcp-server/`) and update `CHANGELOG.md` under `## [Unreleased]`

---

## Dependencies & Execution Order

### Phase Dependencies

- **Phase 1 (Setup)**: No dependencies.
- **Phase 2 (Foundational)**: Depends on Phase 1; blocks Phases 3–6 (`T002`–`T004`).
- **Phase 3 (US1 - Core Interpolation & Momentum Engine)**: Depends on Phase 2 (`T005`–`T007`).
- **Phase 4 (US2 - `SPEC-WEIGHT-CARD-01` UI)**: Depends on Phase 3 (`T008`–`T009`).
- **Phase 5 (US3 - `SPEC-WEIGHT-SUBTYPE-SHEET-01` & `CyclesScreen`)**: Depends on Phase 3 & 4 (`T010`–`T012`).
- **Phase 6 (US4 - Real/Theoretical History & MCP Analytics)**: Depends on Phase 3 (`T013`–`T015`; `T014`–`T015` in `mcp-server/` can run in parallel with Android UI tasks).
- **Phase 7 (Polish)**: Depends on Phases 3–6 (`T016`).

### Parallel Opportunities

- `T006` (`WeightAnalytics.kt`) can be developed in parallel with `T005` (`FatLossAnalytics.kt`).
- `T011` (`CyclesScreen.kt`) and `T014` (`mcp-server/src/mcp.ts`) touch independent files and can be developed in parallel with `ProgressScreen.kt` tasks.

## Implementation Strategy

1. Complete Phase 2 Foundational (`Enums.kt`, `Cycle.kt`, `AppViewModel.kt`, `Repository.kt`).
2. Complete Phase 3 User Story 1 (`FatLossAnalytics.kt`, `WeightAnalytics.kt`, `FatLossAnalyticsTest.kt`) and verify all math tests pass.
3. Complete Phase 4 & 5 User Stories 2 & 3 (`ProgressScreen.kt`, `CyclesScreen.kt`, `WeightProgressionCardTest.kt`, `CyclesScreenTest.kt`).
4. Complete Phase 6 User Story 4 (`ProgressScreen.kt` history + `mcp-server/`).
5. Run Phase 7 full verification suite.
