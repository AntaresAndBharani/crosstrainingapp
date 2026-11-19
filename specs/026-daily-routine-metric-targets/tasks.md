---

description: "Actionable task breakdown for SPEC-026: Optional Daily Routine Multi-Metric Targets & Metric-Aware Workout Logging"
---

# Tasks: Optional Daily Routine Multi-Metric Targets & Metric-Aware Workout Logging (`SPEC-026`)

**Input**: Design documents from `/specs/026-daily-routine-metric-targets/`  
**Prerequisites**: `plan.md`, `spec.md`, `research.md`, `data-model.md`, `contracts/routine-targets-and-logging-contract.md`

## Format: `[ID] [P?] [Story] Description`

- **[P]**: Parallelizable task (independent files)
- **[Story]**: Maps to `spec.md` User Stories (`[US1]`, `[US2]`, `[US3]`, `[US4]`)

---

## Phase 1: Setup (Shared Infrastructure)

**Purpose**: Ensure clean feature branch & Spec-Kit tracking

- [X] T001 Verify branch `feat/026-daily-routine-metric-targets` off `origin/main` and `.specify/feature.json` pointing to `specs/026-daily-routine-metric-targets`

---

## Phase 2: Foundational (Blocking Codecs, Persistence & Analytics Primitives)

**Purpose**: Core metadata codecs, draft/entity persistence, and multi-metric performance models shared across all stories

- [X] T002 [P] Add `[log_metric=...]` marker codec (`encodeLogMetricHeader`, `decodeLogMetricHeader`, `encodeNotesWithLogMetric`) and order-independent leading metadata header scanning (`hasStructuredHeader`, `decodeHeaderSlots`, `METADATA_HEADER_LINE_REGEX`) in `app/src/main/java/com/fractanomics/crosstraining/util/RoutineTargetCodec.kt`
- [X] T003 [P] Add `notes: String = ""` to `BlockDraft` in `app/src/main/java/com/fractanomics/crosstraining/ui/SessionDraft.kt` and forward `notes = bd.notes` into `SessionBlock` inside `buildBlockInserts` in `app/src/main/java/com/fractanomics/crosstraining/ui/AppViewModel.kt`
- [X] T004 Extend `DailyRoutineItem.BlockRef` (`targetSetsCount`, `exerciseTargets`, `exerciseTargetsByName`, `hasConfiguredTargets`) and `DailyRoutineItem.StandaloneExercise` (`targetSetsCount`, `targetSpec`, `hasConfiguredTarget`), update `toRoutineWithBlocks`/`fromRoutineWithBlocks` (`[rb_v2=...]` optional emission & decoding), and update `resolveLastExerciseSession` + `computeDailyRoutineProgress` for weighted reps, bodyweight reps, and `metricValue` (`cal`/`s`/`m`) in `app/src/main/java/com/fractanomics/crosstraining/util/DailyRoutineCodec.kt`
- [X] T005 [P] Update `BlockPerformance.workingSetValues` and `List<BlockPerformance>.byDay()` to extract `weight`, `metricValue`, and non-placeholder bodyweight `reps` in `app/src/main/java/com/fractanomics/crosstraining/ui/ProgressAnalytics.kt`
- [X] T006 [P] Extend `LastBlockSessionSummary` and `HydratedBlockExerciseSeed` with multi-metric fields (`reps`, `metricValues`, `groupIndices`, `loggedMetric`, `rawNotes`), update `resolveLastBlockSession`, and update `isResidualLegacyBlock` + `hydrateSessionBlocksForEditor` to hydrate unweighted rep-only, `metricValue`, and `groupIndex` sets while preserving warm-up/failed/Metcon isolation in `app/src/main/java/com/fractanomics/crosstraining/util/AvailableBlockCodec.kt`
- [X] T007 [P] Add unit tests covering `[log_metric=...]` / header-order scanning in `app/src/test/java/com/fractanomics/crosstraining/util/RoutineTargetCodecTest.kt` and Edit Session hydration boundaries in `app/src/test/java/com/fractanomics/crosstraining/util/AvailableBlockCodecTest.kt`

---

## Phase 3: User Story 1 — Optional Per-Exercise Multi-Metric Targets on Daily Routine Items (Priority: P1)

**Goal**: Allow users to configure optional (unset-by-default) `Sets`, `Metric` (`Reps`/`Calories`/`Time`/`Meters`), `Fixed` vs `Per-Set` (including `0 / Skip`), presets, and pattern helpers on Daily Routine `StandaloneExercise` and `BlockRef` items, while keeping `Library -> Blocks` and `Library -> Exercises` lightweight.

**Independent Test**: Unit tests for `DailyRoutineComposerState` target lifecycle (unset -> enable -> edit -> clear -> validation), `DailyRoutineCard` chip summary formatting, and `DailyRoutineCodec` round-trip.

- [X] T008 [P] [US1] Create `InlineExerciseTargetConfigPanel`, `InlineBlockTargetsConfigPanel`, target validation helper `isDailyRoutineItemTargetValid`, and summary formatters (`formatDailyRoutineTargetSummary`, `formatCompactChipTargetSummary`, `formatBlockRoutineTargetsSummary`) in `app/src/main/java/com/fractanomics/crosstraining/ui/screens/RoutineItemTargetsEditor.kt`
- [X] T009 [US1] Extend `DailyRoutineComposerState` with optional target reducers, per-metric input caches, shared Block sets resizing, and all-zero validation, integrate collapsible `"+ Set targets (optional)"` / target summary UI into `DailyRoutineComposerSheet` (keeping `QuickCreateBlockSheet` untouched), and display configured target summaries in `formatDailyRoutineItemChipLabel` in `app/src/main/java/com/fractanomics/crosstraining/ui/screens/LibraryScreen.kt`
- [X] T010 [P] [US1] Add unit tests for `DailyRoutineComposerState` optional targets, `BlockRef` shared sets resizing, `Clear targets`, all-zero validation, and chip formatting in `app/src/test/java/com/fractanomics/crosstraining/ui/screens/LibraryDailyRoutinesTest.kt` and round-trip codec tests in `app/src/test/java/com/fractanomics/crosstraining/util/DailyRoutineCodecTest.kt`

---

## Phase 4: User Story 2 — Metric-Aware Set Tiles & Edit Session Hydration in Log/Edit Session (Priority: P1)

**Goal**: Restore Reps (+ optional Weight), Calories, Time, and Meters logging and editing inside both `SimplifiedExerciseLogCard` and `SimplifiedBlockLogCard`, with Daily Routine target expansion (`0 / Skip` omission + round number preservation) and responsive `360dp` tile grids.

**Independent Test**: Unit tests verifying Daily Routine expansion with targets (`REPS` + prior weights, `CALORIES` with `0 (Skip)` preserved rounds), metric switching with input caching, `BlockDraft` serialization (`reps`, optional `weight`, `metricValue`, `groupIndex`, `[log_metric=...]`), and `Edit Session` hydration round-trips.

- [X] T011 [US2] Update `LoadedAvailableBlockExerciseState`, `LoadedAvailableBlockState` (including `sourceRoutineItem`, per-metric input caches, `updateSetReps`, `updateSetMetricValue`, `switchExerciseMetric`, `addSet`/`removeSet`, and metric-aware `toBlockDrafts`), state builders (`buildLoadedAvailableBlockState`, `buildLoadedStandaloneExerciseState`, `buildCustomAvailableBlockState`, `buildHydratedBlockState`), and `SessionExerciseSetTilesSection` (`Reps`/`Cal`/`Time`/`Meters` chips, 2-col `RepsWeightSetTile`, 3-col `MetricValueSetTile`, `Rd N` labels) in `app/src/main/java/com/fractanomics/crosstraining/ui/screens/SimplifiedBlockLogComponents.kt`
- [X] T012 [US2] Update `expandDailyRoutineForSession` and `deriveRoutineItemsFromSessionStates` to forward Daily Routine targets into session cards and preserve targets during template sync, passing full `exercises` into `buildCustomAvailableBlockState` in `app/src/main/java/com/fractanomics/crosstraining/ui/screens/SessionEditor.kt`
- [X] T013 [P] [US2] Expand unit tests for multi-metric routine expansion, `0 (Skip)` round preservation, metric switching cache, bodyweight & conditioning `BlockDraft` output, and Edit Session hydration in `app/src/test/java/com/fractanomics/crosstraining/ui/screens/SessionEditorDailyRoutineAndExerciseTest.kt`

---

## Phase 5: User Story 3 — Multi-Metric History, Progress Aggregation & Pre-Fill Parity (Priority: P2)

**Goal**: Ensure `HistoryScreen` and `ProgressScreen` (`By routine` overview, drill-down charts/KPIs, and `BlockHistoryCard`) accurately format and aggregate weighted reps, bodyweight reps, and conditioning metrics (`cal`/`s`/`m`).

**Independent Test**: Verify `DailyRoutineCodec.computeDailyRoutineProgress` and `DailyRoutineProgressTest` assert correct unit resolution (`kg`, `reps`, `cal`, `s`, `m`) and KPI values across mixed metrics.

- [X] T014 [P] [US3] Update `setToken` in `app/src/main/java/com/fractanomics/crosstraining/ui/screens/HistoryScreen.kt` and `BlockHistoryCard` + `DailyRoutineConstituentDrillDown` (`item.unit` propagation) in `app/src/main/java/com/fractanomics/crosstraining/ui/screens/ProgressScreen.kt`
- [X] T015 [P] [US3] Add multi-metric (`reps`, `cal`, `s`, `m`) pre-fill and `computeDailyRoutineProgress` assertions in `app/src/test/java/com/fractanomics/crosstraining/util/DailyRoutineCodecTest.kt` and `app/src/test/java/com/fractanomics/crosstraining/ui/screens/DailyRoutineProgressTest.kt`

---

## Phase 6: User Story 4 — Remote MCP & Cloud Sync Parity (Priority: P2)

**Goal**: Maintain 100% schema/storage parity across `mcp-server` Daily Routine and Block tools while preserving 39 flat tools (`0` `$ref`, `0` `exclusiveMinimum`) and backward compatibility.

**Independent Test**: `npm run build && npm test` inside `mcp-server/` covering schema flatness (`39` tools), optional Daily Routine targets (`EXERCISE` & `BLOCK`), multi-metric `sets`/`metric` logging & block updates, and multi-metric progress output.

- [X] T016 [P] [US4] Add `[log_metric=...]` codec helpers, extend `extractCleanRoutineNotes` / `decodeRoutineHeaderSlots` multi-line scanning, and add optional multi-metric target & `sets`/`metric` fields to flat factory schemas (`0` `$ref`, `0` `exclusiveMinimum`) in `mcp-server/src/schemas.ts`
- [X] T017 [US4] Implement optional Daily Routine targets (`upsertDailyRoutine`, `listDailyRoutines`), multi-metric `sets`/`metric` persistence (`logDailyRoutineSession`, `logBlockSession`, `updateBlockSessionLog`), and unit-aware `getDailyRoutineProgress` in `mcp-server/src/store.ts`
- [X] T018 [P] [US4] Add MCP parity unit/integration tests for optional Daily Routine targets, multi-metric `sets`/`metric` logging, all-zero target validation, and schema flatness in `mcp-server/test/mcp-server.test.ts`

---

## Phase 7: Polish, Full Verification, Changelog & PR Delivery

**Purpose**: Final convergence audit, verification suites, APK sync, changelog, commit, and PR with CI green

- [X] T019 Execute `speckit-converge` audit against `spec.md`, `plan.md`, `tasks.md`, and `.specify/memory/constitution.md`
- [X] T020 Run full pre-PR verification suite (`.\gradlew.bat testDebugUnitTest --no-daemon`, `.\scripts\tests\Invoke-ScriptTests.ps1`, `.\gradlew.bat lintDebug --no-daemon`, `mcp-server` `npm run build && npm test`) and build/sync debug APK to `local_test\latest.apk`
- [X] T021 Update `CHANGELOG.md` under `## [Unreleased]`, commit changes on `feat/026-daily-routine-metric-targets`, push, open Pull Request against `main`, and verify remote CI checks pass 100%

---

## Dependencies & Execution Order

- **Phase 1 -> Phase 2**: Must complete T001–T006 before UI stories (US1–US3) and before final MCP integration verification.
- **US1 (Phase 3)** and **US2 (Phase 4)** can proceed sequentially after Phase 2 (`US1` establishes `DailyRoutineItem` targets consumed by `US2` routine expansion).
- **US3 (Phase 5)** and **US4 (Phase 6)** can run in parallel once Phase 2 codecs are settled.
- **Phase 7** executes last after all user stories and tests are complete.
