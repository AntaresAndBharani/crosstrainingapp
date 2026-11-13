---

description: "Task breakdown for End-of-Cycle Estimated Body Weight on Weekly Body Weight (SPEC-020)"
---

# Tasks: End-of-Cycle Estimated Body Weight on Weekly Body Weight

**Input**: Design documents from `/specs/020-estimated-finish-weight/`
**Prerequisites**: `plan.md` (required), `spec.md` (required), `research.md`, `data-model.md`, `contracts/estimated-finish-weight-contract.md`, `quickstart.md`

## Format: `[ID] [P?] [Story] Description`

- **[P]**: Can run in parallel (different files, no dependencies)
- **[Story]**: Which user story this task belongs to (`US1`, `US2`, `US3`)

## Phase 1: Setup

**Purpose**: Ensure feature context and baseline build environment are ready.

- [X] T001 Verify `.specify/feature.json` points to `specs/020-estimated-finish-weight` and branch `feat/020-estimated-finish-weight` is checked out

---

## Phase 2: Foundational Domain Models

**Purpose**: Extend shared analytics data structures in `FatLossAnalytics.kt` without breaking existing test callers.

- [X] T002 Add `EstimatedFinishWeights` data class and extend `HorizonMomentumRow` (with `= null` defaults) and `CycleMomentumState.Active` (`estimatedFinishWeightText: String? = null`) in `app/src/main/java/com/fractanomics/crosstraining/data/analytics/FatLossAnalytics.kt`

---

## Phase 3: User Story 1 - Dual End-of-Cycle Body Weight Estimation Engine (Priority: P1)

**Goal**: Compute deterministic compounded Momentum `%` and linear Pace finish weight estimates on the calibrated `Weekly Body Weight` composite row with display-unit reconciliation.

**Independent Test**: Run `FatLossAnalyticsTest` verifying calibration gate (`Days 1–7`), screenshot scenario (`72.5 kg (Momentum) · 72.4 kg (Pace)`), corridor boundary parity, `0.00` velocity non-drift, completed/open-ended cycles, `lbs` conversion, and `0.0` floor clamping.

- [X] T003 [US1] Implement `computeEstimatedFinishWeights` and `formatEstimatedFinishWeightText` and wire them into `computeCycleMomentumState` (exclusively on `weeklyBodyWeightRow`, `null` on `horizonRows` and when calibrating) in `app/src/main/java/com/fractanomics/crosstraining/data/analytics/FatLossAnalytics.kt`
- [X] T004 [P] [US1] Add comprehensive `SPEC-020` unit tests covering calibration gate, screenshot scenario, corridor boundary parity (`kg` & `lbs`), zero-velocity non-drift, completed/open-ended cycles, and `0.0` floor in `app/src/test/java/com/fractanomics/crosstraining/data/analytics/FatLossAnalyticsTest.kt`

---

## Phase 4: User Story 2 - Contextual Card Presentation, Status Color & Accessibility (Priority: P2)

**Goal**: Render `"Estimated Finish Weight: ... (Momentum) · ... (Pace)"` inside `✦ Weekly Body Weight` directly below `"Pace: ... · Momentum: ..."` and above `CorridorRangeGauge`, using semantic status coloring, responsive wrapping, and merged TalkBack semantics.

**Independent Test**: Run `WeightProgressionCardTest` verifying `resolveWeeklyBodyWeightEstimatedFinishText` and `resolveHorizonMomentumContentDescription` for calibrated `kg`/`lbs` composite rows, calibrating rows, and `7D`–`28D` horizon rows.

- [X] T005 [US2] Add `ESTIMATED_FINISH_WEIGHT_LABEL` and `resolveWeeklyBodyWeightEstimatedFinishText`, update `resolveHorizonMomentumContentDescription`, and render the status-colored, non-truncated `Estimated Finish Weight` line inside `CycleMomentumEstimationsCard` in `app/src/main/java/com/fractanomics/crosstraining/ui/screens/ProgressScreen.kt`
- [X] T006 [P] [US2] Add `SPEC-020` UI resolver & TalkBack accessibility unit tests in `app/src/test/java/com/fractanomics/crosstraining/ui/screens/WeightProgressionCardTest.kt`

---

## Phase 5: User Story 3 - Remote MCP Analytics Parity (Priority: P3)

**Goal**: Expose `estimated_finish_weight_momentum_kg` and `estimated_finish_weight_pace_kg` inside `weekly_body_weight_composite` of `computeWeightMomentumAnalytics` / `get_weight_momentum_analytics` with full Android parity.

**Independent Test**: Run `npm run build && npm test` in `mcp-server/` verifying `null` during `< 8` day calibration, compounded/linear values when calibrated, and convergence when `asOfDate >= cycleFinishDate`.

- [X] T007 [P] [US3] Add `estimated_finish_weight_momentum_kg` and `estimated_finish_weight_pace_kg` to `weekly_body_weight_composite` in `mcp-server/src/schemas.ts` and update `get_weight_momentum_analytics` description in `mcp-server/src/mcp.ts`
- [X] T008 [P] [US3] Add Vitest unit tests asserting calibrating `null`, calibrated Momentum/Pace finish weights, and completed-cycle convergence in `mcp-server/test/mcp-server.test.ts`

---

## Phase 6: Polish & Cross-Cutting Verification

**Purpose**: Run full pre-PR verification suite, sync debug APK, and update `CHANGELOG.md`.

- [X] T009 Update `CHANGELOG.md` under `## [Unreleased]` with a concise Keep a Changelog entry for `SPEC-020`
- [X] T010 Run full local verification gate (`.\gradlew.bat testDebugUnitTest --no-daemon`, `.\scripts\tests\Invoke-ScriptTests.ps1`, `.\gradlew.bat lintDebug --no-daemon`, `mcp-server` `npm run build && npm test`, and `.\gradlew.bat assembleDebug --no-daemon` copied to `local_test\latest.apk`)

## Dependencies & Execution Order

- `T001` → `T002` → `T003` → `T005` → `T009` → `T010`
- `T004` (`[P]`) runs alongside or right after `T003`
- `T006` (`[P]`) runs alongside or right after `T005`
- `T007` and `T008` (`[P]`) can run in parallel with Android implementation (`US1`/`US2`) after `T001`
