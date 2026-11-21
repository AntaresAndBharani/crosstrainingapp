# Tasks: Compact Complex Session Logging (`SPEC-028`)

**Input**: Design documents from `/specs/028-compact-complex-logging/`
**Prerequisites**: `plan.md`, `spec.md`, `research.md`, `data-model.md`, `contracts/mcp-complex-shared-weights.md`, `quickstart.md`

## Phase 1: Setup & String Resources

**Purpose**: Prepare string resources and confirm pre-existing baselines before implementing domain and UI changes.

- [X] T001 [P] Add Compact Complex UI string resources (`complex_col_set`, `complex_col_movements`, `complex_col_weight`, `complex_view_by_exercise`, `complex_view_compact`, `complex_state_mixed`, `complex_state_skipped`, `complex_state_bw`, `complex_status_active`, `complex_status_skip`, `complex_action_add_set`, `complex_relink_all`, `complex_prefilled_from`, `complex_all_skipped_hint`, `complex_applied_shared_weight`, `complex_set_removed`, `complex_unit_label`) in `app/src/main/res/values/strings.xml`

---

## Phase 2: Foundational (Codec & Canonical Multi-Exercise Complex State)

**Purpose**: Extend zero-migration codec projections and canonical multi-exercise complex state management (`LoadedAvailableBlockState` / `LoadedAvailableBlockExerciseState`) supporting linked shared weights, skipped rounds (`0`), and inline overrides.

**⚠️ CRITICAL**: Must complete before UI components and tests integrate against these models.

- [X] T002 Extend `LastBlockSessionSummary` (`exerciseGroupIndicesById`, `exerciseGroupIndicesByName`), `HydratedBlockExerciseSeed` (`hasNoLoggedSets`), `resolveLastBlockSession`, and `hydrateSessionBlocksForEditor` in `app/src/main/java/com/fractanomics/crosstraining/util/AvailableBlockCodec.kt`
- [X] T003 Extend `LoadedAvailableBlockExerciseState` (`libraryMetricType`, `skippedFlags`, `supportsSharedWeight`, `switchActiveMetric` skip preservation) and `LoadedAvailableBlockState` (`lastSessionDateFormatted`, `hasPreFilledHistory`, `isCompactComplex`, `complexSetCount`, skip-aware `blockMaxSetWeight`/`maxSetWeightFor`) in `app/src/main/java/com/fractanomics/crosstraining/ui/screens/SimplifiedBlockLogComponents.kt`
- [X] T004 Implement `SharedSetWeightStateKind`, `SharedSetWeightSummary`, `ComplexSetWeightsSnapshot`, `RemovedExerciseSetSlice`, `RemovedComplexSetSnapshot`, `resolveSharedSetWeight`, `captureComplexSetWeights`, `updateSharedSetWeight`, `relinkComplexSetWeights`, `restoreComplexSetWeights`, `toggleComplexExerciseSkipped`, `addComplexSet`, `removeComplexSet`, and `restoreRemovedComplexSet` on `LoadedAvailableBlockState` in `app/src/main/java/com/fractanomics/crosstraining/ui/screens/SimplifiedBlockLogComponents.kt`
- [X] T005 Update `buildLoadedAvailableBlockState`, `buildCustomAvailableBlockState`, `buildHydratedBlockState`, and `toBlockDrafts` for multi-exercise complexes (`>= 2` movements) to align canonical rounds (`0 until totalRounds`), pre-fill weights by `groupIndex` / active ordinal, preserve all-skipped movements (`sets = emptyList()`), and emit `groupIndex` whenever any movement skips in `app/src/main/java/com/fractanomics/crosstraining/ui/screens/SimplifiedBlockLogComponents.kt`

**Checkpoint**: Domain and codec state layer ready for UI composables and unit verification.

---

## Phase 3: User Story 1 & User Story 2 — Compact Complex Table, Inline Override Drawer & Secondary `By ex.` View (Priority: P1) 🎯 MVP

**Goal**: Deliver the single-screen `CompactComplexLogCard` (`1A`), inline `SetExerciseOverrideDrawer` (`2A`), hidden default metric switcher + `By ex. ▸` (`3A`), `ImeAction.Next` vertical traversal, and `[Undo]` Snackbars across `Log Session` and `Edit Session`.

**Independent Test**: Render `CompactComplexLogCard` for a multi-exercise complex, edit shared weights (`S1..S6`), expand `SetExerciseOverrideDrawer` to toggle `Active`/`Skip` or split weights (`Mixed` + `"Re-link all"` + `[Undo]`), toggle `By ex. ▸` to switch units, and add/remove sets with `[Undo]`.

- [X] T006 [US1] Implement pure UI & accessibility helper functions (`circledMovementIndex`, `formatCompactMovementPillText`, `formatCompactSetAccessibilityLabel`, `shouldReflowComplexSetRow`, `shouldShowPillExerciseNames`, `sanitizeCompactWeightInput`) and semantic Light/Dark color tokens in `app/src/main/java/com/fractanomics/crosstraining/ui/screens/CompactComplexLogComponents.kt`
- [X] T007 [US1] Implement `CompactComplexLogCard`, `ComplexPrescriptionBox`, `MovementLegendRow`, `ComplexSetTableHeader`, `ComplexSetRow`, `MovementStatusStrip`, `SharedSetWeightInput` (with `FocusRequester` vertical `ImeAction.Next` traversal and `BringIntoViewRequester`), `RemoveSetButton`, and `ComplexTableFooterRow` in `app/src/main/java/com/fractanomics/crosstraining/ui/screens/CompactComplexLogComponents.kt`
- [X] T008 [US2] Implement inline `SetExerciseOverrideDrawer` (`AnimatedVisibility`, `Active`/`Skip` `Role.Switch` pill with `CLOCK_TICK` haptic, inline target box with inherited unit, per-movement weight field, `"Re-link all"` action, and all-skipped hint) and secondary `CompactComplexByExerciseView` (`Unit: {Metric} ▾` dropdown menu) in `app/src/main/java/com/fractanomics/crosstraining/ui/screens/CompactComplexLogComponents.kt`
- [X] T009 [US1] Make `SessionItemHeaderRow` and `TrackedPrChipsFlowSection` `internal` for reuse, add `onShowUndoSnackbar` to `SimplifiedBlockLogCard`, and delegate multi-exercise Complexes (`blockState.isCompactComplex`) to `CompactComplexLogCard` in `app/src/main/java/com/fractanomics/crosstraining/ui/screens/SimplifiedBlockLogComponents.kt`
- [X] T010 [US1] Wire `onShowUndoSnackbar` (with `SnackbarDuration.Short` and single active prompt dismissal) and `.imePadding()` on the main `Column` in `app/src/main/java/com/fractanomics/crosstraining/ui/screens/SessionEditor.kt`

---

## Phase 4: User Story 3 — Skip-Aware PR Auto-Sync, Zero-Migration Round-Trip & Comprehensive Android Tests (Priority: P2)

**Goal**: Verify skip-aware PR evaluation, mixed barbell + bodyweight/erg complexes, zero-migration Room/Firestore hydration parity, responsive/a11y helpers, and zero regressions across existing Session Editor & codec test suites.

**Independent Test**: Run `.\gradlew.bat testDebugUnitTest --no-daemon` covering `CompactComplexLoggingTest` and all existing test classes.

- [X] T011 [P] [US3] Create comprehensive JUnit tests in `app/src/test/java/com/fractanomics/crosstraining/ui/screens/CompactComplexLoggingTest.kt` covering `1-2-3-1-2-3` / `3-1-3-1-3-1` canonical alignment & pre-fill, linked shared weight edits, `Mixed` split + `"Re-link all"` + `[Undo]`, `toggleComplexExerciseSkipped` (`Active -> Skip`, `Skip -> Active`, `ALL_SKIPPED`), `addComplexSet`/`removeComplexSet` + `[Undo]`, skip-aware PR auto-sync, mixed barbell + erg (`CALORIES`) / bodyweight (`REPS`) isolation, `toBlockDrafts` -> `hydrateSessionBlocksForEditor` -> `buildHydratedBlockState` round-trip (including all-skipped movements), and pure UI/a11y helpers

---

## Phase 5: User Story 4 — Remote MCP `shared_set_weights_kg` Parity (Priority: P2)

**Goal**: Support `shared_set_weights_kg` (and optional `exercise_targets` skip expansion) in `mcp-server` while preserving flat schemas (`45` tools, `0` `$ref`, `0` `exclusiveMinimum`).

**Independent Test**: Run `npm run build && npm test` inside `mcp-server/` verifying all 45 tools, zero schema violations, and `shared_set_weights_kg` integration tests.

- [X] T012 [P] [US4] Add optional `shared_set_weights_kg` and `exercise_targets` (making `exerciseLogs` optional when `shared_set_weights_kg` is supplied) in `mcp-server/src/schemas.ts` using fresh Zod schema helpers
- [X] T013 [US4] Update `LogBlockSessionInput`, `UpdateBlockSessionLogInput`, `LogDailyRoutineSessionInput`, and add `expandSharedComplexSetWeights` integrated across `logBlockSession`, `logDailyRoutineSession`, and `updateBlockSessionLog` in `mcp-server/src/store.ts`
- [X] T014 [US4] Update `CROSSTRAINING_MCP_INSTRUCTIONS` and tool descriptions for `log_complex_session`, `log_block_session`, `update_complex_session_log`, `update_block_session_log`, and `log_daily_routine_session` in `mcp-server/src/mcp.ts`
- [X] T015 [US4] Add MCP integration tests covering `log_complex_session` (`shared_set_weights_kg` + `1-2-3-1-2-3` `exercise_targets` + mixed barbell/Air Bike), `log_daily_routine_session` (`shared_set_weights_kg` honoring saved `exerciseTargets` + `exercise_logs` overrides), and `update_complex_session_log` (preserving active `groupIndex` masks) in `mcp-server/test/mcp-server.test.ts`

---

## Phase 6: Polish & Cross-Cutting Verification

**Purpose**: Ensure full test, lint, script, changelog, and convergence parity before opening the PR.

- [X] T016 Run full local pre-PR verification suite (`.\gradlew.bat testDebugUnitTest --no-daemon`, `.\gradlew.bat lintDebug --no-daemon`, `.\scripts\tests\Invoke-ScriptTests.ps1`, and `npm run build && npm test` in `mcp-server/`) and resolve any issues
- [X] T017 Update `CHANGELOG.md` under `## [Unreleased]` with a concise Keep a Changelog entry for `SPEC-028`

---

## Dependencies & Execution Order

- **Phase 1 (`T001`)** -> independent, can run immediately.
- **Phase 2 (`T002` -> `T003` -> `T004` -> `T005`)** -> foundational domain/state layer blocking Phase 3 & Phase 4.
- **Phase 3 (`T006` -> `T007` -> `T008` -> `T009` -> `T010`)** -> UI implementation depending on Phase 1 & Phase 2.
- **Phase 4 (`T011`)** -> Android JUnit verification depending on `T002`–`T006`.
- **Phase 5 (`T012` -> `T013` -> `T014` -> `T015`)** -> independent of Android UI phases; can be built right after Phase 2/3.
- **Phase 6 (`T016` -> `T017`)** -> final verification and changelog after all code & test tasks finish.
