---
description: "Task list for Subcycle Taxonomy & Velocity Corridors (SPEC-WEIGHT-SUBTYPE-SHEET-02 & SPEC-WEIGHT-CARD-02)"
---

# Tasks: Subcycle Taxonomy & Velocity Corridors (`SPEC-WEIGHT-SUBTYPE-SHEET-02` & `SPEC-WEIGHT-CARD-02`)

**Input**: Design documents from `/specs/017-subcycle-velocity-corridors/`

**Prerequisites**: [plan.md](plan.md), [spec.md](spec.md), [research.md](research.md), [data-model.md](data-model.md), [contracts/subcycle-velocity-corridors-contract.md](contracts/subcycle-velocity-corridors-contract.md)

**Tests**: Deterministic unit tests are included across Android (`FatLossAnalyticsTest`, `WeightProgressionCardTest`, `CyclesScreenTest`) and MCP (`mcp-server.test.ts`) per Constitution Principle III.

**Organization**: Tasks are grouped by user story (`US1`–`US4`) to enable independent implementation and verification.

## Format: `[ID] [P?] [Story] Description`

- **[P]**: Can run in parallel (different files, no dependencies)
- **[Story]**: Which user story this task belongs to (`[US1]`, `[US2]`, `[US3]`, `[US4]`)
- Include exact file paths in descriptions

---

## Phase 1: Setup (Shared Infrastructure)

**Purpose**: Verify active Spec-Kit feature pointer and clean baseline before domain modifications.

- [X] T001 Verify `.specify/feature.json` points to `specs/017-subcycle-velocity-corridors` in `.specify/feature.json`

---

## Phase 2: Foundational (Blocking Prerequisites)

**Purpose**: Core `WeightSubcycleType` taxonomy (`FAT_LOSS`, `RECOMPOSITION`, `MUSCLE_GAIN`), velocity corridors (`[-1.00%, -0.25%]`, `[-0.25%, +0.25%]`, `[+0.25%, +0.75%]`), and backward-compatible metadata parsing in `Enums.kt` and `Cycle.kt`.

- [X] T002 Update `WeightSubcycleType` enum with `FAT_LOSS` (`"Fat Loss"`, `-1.00` to `-0.25`), `RECOMPOSITION` (`"Recomposition"`, `-0.25` to `+0.25`), and `MUSCLE_GAIN` (`"Muscle Gain"`, `+0.25` to `+0.75`), plus companion aliases `WEIGHT_LOSS = FAT_LOSS`, `WEIGHT_GAIN = MUSCLE_GAIN`, and `fromToken(token: String)` in `app/src/main/java/com/fractanomics/crosstraining/data/model/Enums.kt`
- [X] T003 Update `Cycle.SUBCYCLE_METADATA_REGEX`, `inferDefaultSubcycleType`, and `parseSubcycleMetadata` to encode canonical `[subcycle=FAT_LOSS|RECOMPOSITION|MUSCLE_GAIN]` while decoding both canonical and legacy (`WEIGHT_LOSS`, `WEIGHT_GAIN`) tokens in `app/src/main/java/com/fractanomics/crosstraining/data/model/Cycle.kt`

**Checkpoint**: Foundation ready — `WeightSubcycleType` and `Cycle` metadata support the new taxonomy and corridors with 100% legacy compatibility.

---

## Phase 3: User Story 1 - Updated Subcycle Taxonomy & Tightened Physiological Velocity Corridors (`SPEC-WEIGHT-SUBTYPE-SHEET-02`) (Priority: P1) 🎯 MVP

**Goal**: Deliver `Fat Loss` (`-1.00% to -0.25%/wk`), `Recomposition` (`-0.25% to +0.25%/wk`), and `Muscle Gain` (`+0.25% to +0.75%/wk`) in `Select Subcycle Type` (`SPEC-WEIGHT-SUBTYPE-SHEET-02`) and `CyclesScreen`, with single-line non-wrapping corridor pill badges (`maxLines = 1`, `softWrap = false`, `12sp` `tabular-nums`, `8dp` horizontal padding) and cold-start projection placeholder.

**Independent Test**: Verify `Select Subcycle Type` bottom sheet and `CycleEditorDialog` display the updated subcycle names, tightened corridor pills on a single line without wrapping `/wk`, and compounded finish weight projections.

### Implementation & Tests for User Story 1

- [X] T004 [P] [US1] Update `CyclesScreen.kt` subcycle validation (`isTargetWeightInvalidForSubcycle`) and error copy for `FAT_LOSS`, `RECOMPOSITION`, and `MUSCLE_GAIN` in `app/src/main/java/com/fractanomics/crosstraining/ui/screens/CyclesScreen.kt`
- [X] T005 [US1] Upgrade `SubcycleTypePickerBottomSheet` (`SPEC-WEIGHT-SUBTYPE-SHEET-02`) and `resolveSubcyclePillColors` in `app/src/main/java/com/fractanomics/crosstraining/ui/screens/ProgressScreen.kt` to enforce single-line corridor pills (`maxLines = 1`, `softWrap = false`, `fontSize = 12.sp`, `fontFeatureSettings = "tnum"`, `horizontal = 8.dp, vertical = 4.dp`), vertical stacking at `fontScale >= 1.5f`, and cold-start projection placeholder (`"Projected Finish: Log first weigh-in to calculate range"`)
- [X] T006 [P] [US1] Update and expand unit tests for `FAT_LOSS`, `RECOMPOSITION`, `MUSCLE_GAIN` metadata encoding/decoding and legacy `WEIGHT_LOSS`/`WEIGHT_GAIN` compatibility in `app/src/test/java/com/fractanomics/crosstraining/ui/screens/CyclesScreenTest.kt`

**Checkpoint**: User Story 1 (`SPEC-WEIGHT-SUBTYPE-SHEET-02`) is functional and independently verified.

---

## Phase 4: User Story 2 - 5-Zone Universal Velocity Spectrum & Strict Inequality Diagnostic Engine (`SPEC-WEIGHT-CARD-02`) (Priority: P1)

**Goal**: Implement the 5-Zone Universal Velocity Spectrum, `MomentumSeverity` (`OPTIMAL_GREEN`, `WARNING_AMBER`, `CRITICAL_RED`, `NEUTRAL`), `CorridorComplianceStatus`, `CorridorDiagnosticResult`, strict mathematical inequality evaluation at `-1.00%`, `-0.25%`, `0.00%`, `+0.25%`, and `+0.75%`, and 5-segment gauge mapping (`15% | 22.5% | 25% | 22.5% | 15%`) in `FatLossAnalytics.kt`.

**Independent Test**: Verify every boundary and interior momentum value across `Fat Loss`, `Recomposition`, and `Muscle Gain` in `FatLossAnalyticsTest.kt`.

### Implementation & Tests for User Story 2

- [X] T007 [US2] Update `MomentumZone`, `MomentumSeverity`, `CorridorComplianceStatus`, `CorridorDiagnosticResult`, `HorizonMomentumRow`, `classifyMomentumZone`, `evaluateCorridorDiagnostic`, `classifyCorridorCompliance`, `computeCorridorGaugePosition` (5-zone `15% | 22.5% | 25% | 22.5% | 15%` geometry), and `Optimal Range:` header formatting in `app/src/main/java/com/fractanomics/crosstraining/data/analytics/FatLossAnalytics.kt`
- [X] T008 [US2] Add comprehensive unit tests for the 5-Zone Universal Velocity Spectrum, strict boundary inequalities (`-1.00%`, `-0.25%`, `0.00%`, `+0.25%`, `+0.75%`), diagnostic badges, coaching helper subtitles, and 5-segment gauge mapping in `app/src/test/java/com/fractanomics/crosstraining/data/analytics/FatLossAnalyticsTest.kt`

**Checkpoint**: User Story 2 domain diagnostic engine is complete and 100% covered by unit tests.

---

## Phase 5: User Story 3 - Composite-Only Warning Architecture & Clean Multi-Horizon Display (`SPEC-WEIGHT-CARD-02`) (Priority: P2)

**Goal**: Upgrade `CycleMomentumEstimationsCard` (`SPEC-WEIGHT-CARD-02`) in `ProgressScreen.kt` so that semantic diagnostic pill badges (`OPTIMAL_GREEN`, `WARNING_AMBER`, `CRITICAL_RED`), the 5-Segment Corridor Gauge, and the inline helper message (`↳ {diagnosticMessage}`) render **exclusively** on the `✦ Weekly Body Weight` composite row, while `7D Avg`, `14D Avg`, `21D Avg`, and `28D Avg` rows display their scalar weight, weekly pace, and weekly `%` variation cleanly without redundant per-row badges or gauges.

**Independent Test**: Verify `SPEC-WEIGHT-CARD-02` resolvers, semantic badge colors, composite helper subtitle rendering, and clean horizon row labels in `WeightProgressionCardTest.kt`.

### Implementation & Tests for User Story 3

- [X] T009 [US3] Update `CycleMomentumEstimationsCard`, `CorridorRangeGauge` (5-segment `0.15f | 0.225f | 0.25f | 0.225f | 0.15f` track), semantic diagnostic badge colors (`resolveMomentumBadgeColors`), composite helper subtitle resolver (`resolveCompositeDiagnosticSubtitle`), and clean `7D`/`14D`/`21D`/`28D` horizon rows in `app/src/main/java/com/fractanomics/crosstraining/ui/screens/ProgressScreen.kt`
- [X] T010 [US3] Update and expand unit tests for `SPEC-WEIGHT-CARD-02` and `SPEC-WEIGHT-SUBTYPE-SHEET-02` resolvers, badge colors, composite helper subtitles, and horizon row accessibility descriptions in `app/src/test/java/com/fractanomics/crosstraining/ui/screens/WeightProgressionCardTest.kt`

**Checkpoint**: User Story 3 (`SPEC-WEIGHT-CARD-02`) UI and unit tests are complete.

---

## Phase 6: User Story 4 - Coaching MCP Server Alignment with Updated Subcycle Taxonomy & Diagnostic Schema (Priority: P3)

**Goal**: Update `mcp-server/src/schemas.ts` and `mcp-server/test/mcp-server.test.ts` so `get_weight_momentum_analytics` uses the updated corridors (`[-1.00%, -0.25%]`, `[-0.25%, +0.25%]`, `[+0.25%, +0.75%]`), supports canonical (`FAT_LOSS`, `RECOMPOSITION`, `MUSCLE_GAIN`) and legacy (`WEIGHT_LOSS`, `WEIGHT_GAIN`) inputs, and returns `global_safety_bounds`, `severity`, `diagnostic_code`, `diagnostic_badge`, and `diagnostic_message`.

**Independent Test**: Run `npm test` in `mcp-server/` verifying all subcycle corridors, strict inequalities, diagnostic messages, and backward-compatible input handling.

### Implementation & Tests for User Story 4

- [X] T011 [P] [US4] Update `WeightSubcycleTypeValues`, `SUBCYCLE_CORRIDORS`, strict inequality diagnostic engine, `global_safety_bounds`, and composite diagnostic fields in `mcp-server/src/schemas.ts`
- [X] T012 [US4] Update and expand MCP weight momentum analytics tests in `mcp-server/test/mcp-server.test.ts`

**Checkpoint**: All 4 user stories are complete and independently verified across Android and MCP Server.

---

## Phase 7: Polish & Cross-Cutting Concerns

**Purpose**: Run full verification suite (`testDebugUnitTest`, `npm test`, `Invoke-ScriptTests.ps1`, `lintDebug`), sync debug APK, and update `CHANGELOG.md`.

- [X] T013 Execute `.\gradlew.bat testDebugUnitTest --no-daemon`, `npm test` in `mcp-server/`, `.\scripts\tests\Invoke-ScriptTests.ps1`, and `.\gradlew.bat lintDebug --no-daemon` to confirm 100% green local verification
- [X] T014 Update `CHANGELOG.md` under `## [Unreleased]` with `017-subcycle-velocity-corridors` (`SPEC-WEIGHT-SUBTYPE-SHEET-02` & `SPEC-WEIGHT-CARD-02`) summary in `CHANGELOG.md`

---

## Dependencies & Execution Order

- **Phase 1 (Setup)** $\rightarrow$ **Phase 2 (Foundational: `T002`, `T003`)** $\rightarrow$ **Phases 3–6 (`US1`–`US4`)** $\rightarrow$ **Phase 7 (Polish: `T013`, `T014`)**.
- `US1` (`T004`–`T006`), `US2` (`T007`–`T008`), and `US4` (`T011`–`T012`) can proceed once Phase 2 is complete; `US3` (`T009`–`T010`) builds on `US2`'s diagnostic fields.

## Parallel Example

```text
Task T004 [US1]: Update CyclesScreen.kt subcycle validation
Task T011 [US4]: Update mcp-server/src/schemas.ts corridors and diagnostic engine
```

## Implementation Strategy

1. Complete Phase 1 & Phase 2 (`Enums.kt`, `Cycle.kt`).
2. Complete Phase 3 (`US1`) & Phase 4 (`US2`) (`FatLossAnalytics.kt`, `CyclesScreen.kt`, `ProgressScreen.kt` bottom sheet).
3. Complete Phase 5 (`US3`) (`ProgressScreen.kt` composite-only warnings & 5-segment gauge) & Phase 6 (`US4`) (`mcp-server/`).
4. Execute Phase 7 full verification suite and update `CHANGELOG.md`.
