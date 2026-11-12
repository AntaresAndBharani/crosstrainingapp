---
description: "Task list for Daily Routine Builder Redesign & Multi-Metric Per-Set Targets"
---

# Tasks: Daily Routine Builder Redesign & Multi-Metric Per-Set Targets

**Input**: Design documents from `/specs/019-routine-builder-redesign/`

**Prerequisites**: [plan.md](./plan.md), [spec.md](./spec.md), [research.md](./research.md), [data-model.md](./data-model.md), [contracts/](./contracts/)

**Tests**: Deterministic unit tests are MANDATORY per Constitution Principle III (`testDebugUnitTest`, `Invoke-ScriptTests.ps1`, `lintDebug`, and `mcp-server` `npm test`).

**Organization**: Tasks are grouped by user story (`US1`..`US4`) so each increment is independently buildable and verifiable.

## Format: `[ID] [P?] [Story] Description`

- **[P]**: Parallelizable task (distinct files, no incomplete task dependencies)
- **[Story]**: User story traceability label (`[US1]`, `[US2]`, `[US3]`, `[US4]`)
- Exact repository file paths included in every task

---

## Phase 1: Setup (Shared Domain Enumerations)

**Purpose**: Extend shared `BlockKind` enumerations across Android and Remote MCP while preserving concise badge invariants (`shortLabel.length <= 15`).

- [X] T001 [P] Add `GYMNASTICS("Gymnastics")` (`shortLabel = "Gymnastics"`, length `<= 15`) to `BlockKind` in `app/src/main/java/com/fractanomics/crosstraining/data/model/Enums.kt`
- [X] T002 [P] Add `"GYMNASTICS"` to `BlockKindValues` in `mcp-server/src/schemas.ts`

---

## Phase 2: Foundational (Lossless `[rb_v2=...]` Codec & Sequence-Preserving Persistence)

**Purpose**: Implement pure, deterministic multi-metric / per-set target encoding, decoding, legacy fallback parsing, and sequence-preserving exercise ID resolution before UI, logging, or MCP stories.

**⚠️ CRITICAL**: Complete before User Story phases.

- [X] T003 Create `ExerciseTargetMetric` (`REPS`, `CALORIES`, `TIME_SECONDS`, `METERS` with step deltas `±1/±1`, `±2/±1`, `±15/±5`, `±50/±50`, defaults `8`, `15`, `45`, `250`, presets `3,5,8,10,12,15` / `10,12,15,20,25,30` / `20,30,45,60,90,120` / `100,200,250,400,500,1000`, and `MetricType` default mapping), `TargetMode` (`FIXED`, `PER_SET`), `[rb_v2=<METRIC>:<MODE>:<fixedValue>:<v1>.<v2>...<vN>;...]` encoder/decoder, `extractCleanNotes`, `formatExerciseTargetBadge`, `formatBlockTargetSummary`, `defaultSetsForFormat` (`1` for `AMRAP`/`For Time`/`Chipper`/`Death by`/`Time cap`, `8` for `Tabata`, `4` otherwise), `resizePerSetValues` (`Set N` copies `Set N-1` including `0`, trims when smaller), pattern helpers (`copyFirstSetToAll`, `applyDescendingPattern`, `applyAlternatingZerosPattern`), and legacy `RoutineBlock` fallback parser in `app/src/main/java/com/fractanomics/crosstraining/util/RoutineTargetCodec.kt`
- [X] T004 [P] Write comprehensive unit tests for `[rb_v2=...]` round-trip encoding/decoding, `0 (Skip)` slots, `extractCleanNotes`, format-driven set defaults, set-count resizing, pattern helpers, all-zero validation, and legacy `targetRepsScheme` fallback (`"5"`, `"21-15-9"`, `"1 + 1"`, `"TRISET_1"`) in `app/src/test/java/com/fractanomics/crosstraining/util/RoutineTargetCodecTest.kt`
- [X] T005 Update `remapExerciseIdsCsv` and routine block deduplication in `app/src/main/java/com/fractanomics/crosstraining/data/Repository.kt` to preserve intentional repeated exercise IDs in a block sequence (e.g. `"1,2,1"`) when `RoutineTargetCodec.hasStructuredHeader(block.notes)` is true while keeping `.distinct()` for legacy unstructured blocks
- [X] T006 Update `AppViewModel.saveRoutineWithBlocks`, `AppViewModel.deleteRoutine` (triggering asynchronous `UserCloudSyncManager.uploadUserData`), `AppViewModel.importSharedWorkout` (preserving positional `exerciseNamesCsv` repeats when `[rb_v2=...]` is present), and add `AppViewModel.getOrCreateExerciseForRoutine(name, onCreated)` in `app/src/main/java/com/fractanomics/crosstraining/ui/AppViewModel.kt`

**Checkpoint**: Foundational codec and repository helpers ready and unit-tested.

---

## Phase 3: User Story 1 - Build & Edit Collapsible Multi-Block Routines with Visual Controls (Priority: P1) 🎯 MVP

**Goal**: Deliver the full-height modal Daily Routine Builder with 4-tier collapsible accordions, exclusive block expansion, `Collapse All`, visual Block Type & contextual Execution Format chips, format-driven Fixed Block Sets stepper (`Sets >= 1`), and dirty-state discard guard (FR-001..FR-005).

**Independent Test**: Open New/Edit Daily Routine, verify Tier 1–3 expand/collapse rules, exclusive block expansion, `Collapse All`, format-to-default-sets transitions (`AMRAP -> 1`, `Tabata -> 8`, `E3MOM -> 4`) with unlocked stepper, and `"Discard unsaved routine?"` confirmation on `Cancel` and Back.

### Tests for User Story 1

- [X] T007 [P] [US1] Create unit tests for `RoutineBuilderUiState` initialization (New vs. Edit Routine), auto-collapse of `Routine Info` once named, exclusive block expansion on block tap/add, `Collapse All`, contextual format lists per `BlockKind`, format-driven default `setsCount` with unlocked manual override, block reordering/deletion with Undo buffer, and dirty-state detection in `app/src/test/java/com/fractanomics/crosstraining/ui/screens/RoutineBuilderUiStateTest.kt`

### Implementation for User Story 1

- [X] T008 [US1] Implement `RoutineBuilderUiState`, `RoutineBlockUi`, pure state reducer actions for Tiers 1–3 (`RoutineInfoAccordionCard`, `Collapse All` bar, `RoutineBlockAccordionCard` with exclusive expansion and `↑`/`↓`/`🗑`/`Done ▲` controls, `BlockSetupSubAccordion` with visual `BlockKind` chips + `More ▾`, contextual Execution Format chips + `More ▾`, and Fixed `Sets >= 1` stepper with quick pills `1, 3, 4, 5, 6, 8`), sticky header/footer, `imePadding()`, and `BackHandler` + `Cancel` `"Discard unsaved routine?"` dialog in `app/src/main/java/com/fractanomics/crosstraining/ui/screens/RoutineBuilderSheet.kt`

**Checkpoint**: 4-tier collapsible Routine Builder shell and Block Setup visual controls operate cleanly.

---

## Phase 4: User Story 2 - Configure Per-Exercise Mixed Metrics & Variable Per-Set Targets Including 0 / Skip (Priority: P1)

**Goal**: Enable inline exercise search & one-tap custom creation (`+ Create & Add "{query}"`), per-exercise 4-metric selector (`Reps | Calories | Time | Meters`) with per-metric value caching, `Fixed` vs. `Per-Set` toggle, responsive `2 / 4 / 6`-column `Per-Set` cards with non-color `0 (Skip)` styling & TalkBack semantics, pattern helpers, all-zero validation gate, Undo snackbar, and clean Library Routine Card summaries (FR-006..FR-012, SC-001, SC-003).

**Independent Test**: Configure a 4-set E3MOM block with Bench Press (`8 reps / set`), Air Bike (`15 • 15 • 12 • 0 cal`), and Concept2 Row (`250 • 250 • 200 • 0m`), test custom exercise creation, test `4 -> 5` sets copying `Set 4` (`0`), verify all-zero save block, save, and inspect Library Routine Card & reopened editor.

### Tests for User Story 2

- [X] T009 [P] [US2] Extend `app/src/test/java/com/fractanomics/crosstraining/ui/screens/RoutineBuilderUiStateTest.kt` with unit tests for metric auto-defaulting from `Exercise.metricType`, in-editor `cachedMetricValues` restoration across metric switches, `Fixed -> Per-Set` initialization, `Per-Set` `0 (Skip)` toggling, `4 -> 5` / `4 -> 3` block set-count sync, all-zero collapse/save blocking (`"Set a target above 0 on at least one set, or remove this exercise"`), repeated exercise slots (`Shuttle Run -> Burpees -> Shuttle Run`), exercise reordering/Undo, and `resolvePerSetGridColumns(widthDp, fontScale)` (`2` at `fontScale >= 1.4f`, `6` at `widthDp >= 600`, `4` default)

### Implementation for User Story 2

- [X] T010 [US2] Implement Tier 3B & Tier 4 in `app/src/main/java/com/fractanomics/crosstraining/ui/screens/RoutineBuilderSheet.kt`: unified inline Exercise Search + category filter chips + `+ Create & Add "{query}"`, `ExerciseAccordionCard` with `360dp`-safe non-wrapping `Reps | Calories | Time | Meters` selector (`LazyRow` fallback when `fontScale >= 1.4f`), `Fixed | Per-Set` toggle, Fixed stepper/keypad/preset pills, responsive `Per-Set` grid (`2 / 4 / 6` columns) with dashed `0 (Skip)` cards, haptic ticks, TalkBack merged semantics (`"Set {k}, 0 {unit}, skipped in this set"`), pattern helpers (`Copy Set 1 to All`, `Descending`, `Alternate 0s`), all-zero inline error highlight, and 4s `[Undo]` Snackbar
- [X] T011 [US2] Wire `RoutineBuilderSheet` into `LibraryScreen` (replacing legacy `RoutineEditorDialog`), pass `viewModel.getOrCreateExerciseForRoutine`, and update `RoutineCard` / `RoutineBlockCard` in `app/src/main/java/com/fractanomics/crosstraining/ui/screens/LibraryScreen.kt` to render structured per-exercise mixed-metric / `0 (Skip)` badges and `RoutineTargetCodec.extractCleanNotes(blk.notes)` without leaking `[rb_v2=...]` headers
- [X] T012 [P] [US2] Extend `app/src/test/java/com/fractanomics/crosstraining/ui/screens/LibraryScreenTest.kt` to verify `BlockKind.GYMNASTICS` badge conciseness (`<= 15` chars) and clean per-exercise target badge rendering on `RoutineBlock` without raw `[rb_v2=...]` metadata leakage

**Checkpoint**: User Stories 1 & 2 complete — full Daily Routine creation, editing, and Library display work end-to-end.

---

## Phase 5: User Story 3 - Load Multi-Exercise Mixed-Metric Routines into Workout Logging (Priority: P2)

**Goal**: Expand multi-exercise mixed-metric routine blocks loaded via `Load Routine...` in `SessionEditor` into grouped `SubBlockGroup` movement cards with pre-populated active sets, preserved 1-based round indices across skipped `0` sets, metric-appropriate target fields (`reps` vs. `value`), and clean block descriptions (FR-013, SC-004).

**Independent Test**: Load a saved 4-set E3MOM routine containing Bench Press (`8 reps`), Air Bike (`[15, 15, 12, 0]` cal), and Row (`[250, 250, 200, 0]` m) into `SessionEditor` and verify 3 grouped `BlockState` cards under `"Mixed Conditioning"` with 4, 3, and 3 active sets respectively, round numbers `"1".."3"` preserved, and `[rb_v2=...]` stripped from descriptions.

### Tests for User Story 3

- [X] T013 [P] [US3] Create deterministic unit tests for `expandRoutineIntoSessionBlocks` covering multi-exercise `SubBlockGroup` expansion, `0 (Skip)` omission with preserved 1-based `SetState.group` round numbers (including alternating `1, 3` EMOM sets), `REPS` vs. `CALORIES`/`TIME_SECONDS`/`METERS` column pre-filling, single-exercise standalone blocks, repeated sequence exercises, and legacy unstructured routines in `app/src/test/java/com/fractanomics/crosstraining/ui/screens/RoutineSessionExpansionTest.kt`

### Implementation for User Story 3

- [X] T014 [US3] Implement pure `expandRoutineIntoSessionBlocks(rwb: RoutineWithBlocks, exercises: List<Exercise>, defaultCompleted: Boolean): List<BlockState>` and wire it into the `"Load Routine"` selector in `app/src/main/java/com/fractanomics/crosstraining/ui/screens/SessionEditor.kt`

**Checkpoint**: Routine Builder templates flow seamlessly into live Workout Logging sessions.

---

## Phase 6: User Story 4 - Remote AI Assistant (MCP), Cloud Sync, Backup & Workout Sharing Parity (Priority: P2)

**Goal**: Achieve 100% lossless round-trip parity across Cloud Sync (`UserCloudSyncManager`), CSV Backup (`BackupCsv`), Workout Share Codes (`FirebaseSyncManager`), and Remote MCP tools (`list_routines`, `upsert_routine`) using flat connector-compatible JSON Schemas (FR-012, FR-014, SC-002).

**Independent Test**: Upsert a routine with mixed-metric `FIXED` and `PER_SET` (`0` skip) exercises via MCP `upsert_routine`, verify `list_routines` returns structured `exercises` + `cleanNotes`, verify zero `$ref` / zero `exclusiveMinimum` in advertised tool schemas, and verify Cloud Sync / Backup CSV / Share Code preservation of `[rb_v2=...]` and repeated sequence movements.

### Tests for User Story 4

- [X] T015 [P] [US4] Add MCP unit & schema compliance tests in `mcp-server/test/mcp-server.test.ts` covering `upsert_routine` + `list_routines` with mixed metrics (`REPS`, `CALORIES`, `TIME_SECONDS`, `METERS`), `PER_SET` `0 (Skip)` arrays, repeated sequence movements, automatic missing-exercise creation with inferred `metricType`, all-zero exercise rejection, legacy `exerciseNames` + `targetRepsScheme` compatibility, and zero `$ref` / zero `exclusiveMinimum` across all registered MCP tool schemas

### Implementation for User Story 4

- [X] T016 [US4] Update `resolveExerciseCsv` and `recoverAllCloudRoutinesInternal` in `app/src/main/java/com/fractanomics/crosstraining/data/firebase/UserCloudSyncManager.kt` to preserve positional sequence exercise repeats when `RoutineTargetCodec.hasStructuredHeader(notes)` is true (keeping `.distinct()` for legacy unstructured blocks) and resolve `exerciseNamesCsv` on routine recovery
- [X] T017 [US4] Implement `RoutineTargetMetricValues` (`"REPS"`, `"CALORIES"`, `"TIME_SECONDS"`, `"METERS"`), `RoutineTargetModeValues` (`"FIXED"`, `"PER_SET"`), fresh factory `makeRoutineBlockExerciseInputSchema()` (using `.int().min(0)` — zero `$ref`, zero `exclusiveMinimum`), updated `makeRoutineBlockInputSchema()`, and TypeScript `[rb_v2=...]` codec helpers (`encodeRoutineBlockTargets`, `decodeRoutineBlockTargets`, `extractCleanRoutineNotes`) in `mcp-server/src/schemas.ts`
- [X] T018 [US4] Update `WorkoutDomainService.upsertRoutine` and `WorkoutDomainService.listRoutines` in `mcp-server/src/store.ts` and tool descriptions in `mcp-server/src/mcp.ts` to validate non-zero targets, auto-create exercises with inferred `metricType`, persist `[rb_v2=...]` + human-readable `targetRepsScheme`, and return decoded `exercises` + `cleanNotes` on every routine block
- [X] T019 [P] [US4] Add cross-channel parity unit test cases in `app/src/test/java/com/fractanomics/crosstraining/util/RoutineTargetCodecTest.kt` verifying `BackupCsv.encode`/`decode` and `UserCloudSyncManager` / `Repository` sequence-repeat preservation with `[rb_v2=...]` blocks

**Checkpoint**: All 4 user stories are complete and independently verified across mobile and remote MCP.

---

## Phase 7: Polish & Cross-Cutting Verification

**Purpose**: Run full repository verification suites, sync debug APK artifact, update `CHANGELOG.md`, and prepare PR governance gates.

- [X] T020 Execute full verification gate (`.\gradlew.bat testDebugUnitTest --no-daemon`, `.\scripts\tests\Invoke-ScriptTests.ps1`, `.\gradlew.bat lintDebug assembleDebug --no-daemon`, copy debug APK to `local_test/latest.apk`, and `cd mcp-server && npm run build && npm test`) per `specs/019-routine-builder-redesign/quickstart.md`
- [X] T021 Document Daily Routine Builder redesign, mixed-metric per-set (`0 / Skip`) targets, workout logging sub-block expansion, and MCP routine tool parity under `## [Unreleased]` in `CHANGELOG.md`

---

## Dependencies & Execution Order

### Phase Dependencies

- **Phase 1 (Setup)**: Starts immediately (`T001`, `T002` in parallel).
- **Phase 2 (Foundational)**: Depends on Phase 1 (`T003` → `T004`, `T005`, `T006`) — blocks all User Stories.
- **Phase 3 (US1 - P1)**: Depends on Phase 2 (`T007` → `T008`).
- **Phase 4 (US2 - P1)**: Extends `RoutineBuilderSheet.kt` from US1 (`T009` → `T010` → `T011`, `T012`).
- **Phase 5 (US3 - P2)**: Depends only on Phase 2 (`T013` → `T014`); independent of `RoutineBuilderSheet.kt`.
- **Phase 6 (US4 - P2)**: Depends on Phase 1 & Phase 2 (`T015`–`T019`); MCP tasks (`T015`, `T017`, `T018`) can run in parallel with US1–US3.
- **Phase 7 (Polish)**: Depends on Phases 3–6 completion (`T020` → `T021`).

### Parallel Opportunities

- `T001` (Android `Enums.kt`) and `T002` (MCP `schemas.ts`) run in parallel.
- Once `T003` (`RoutineTargetCodec.kt`) is complete, `T004`, `T005`, `T013`/`T014` (SessionEditor expansion), and `T015`–`T018` (MCP server parity) touch disjoint files and can execute independently.

---

## Parallel Example: Foundational + US3 + US4

```text
Task T004: "Write RoutineTargetCodecTest in app/src/test/java/com/fractanomics/crosstraining/util/RoutineTargetCodecTest.kt"
Task T013: "Create RoutineSessionExpansionTest in app/src/test/java/com/fractanomics/crosstraining/ui/screens/RoutineSessionExpansionTest.kt"
Task T015: "Add MCP routine mixed-metric & schema tests in mcp-server/test/mcp-server.test.ts"
```

---

## Implementation Strategy

1. **Foundation First**: Build and unit-test `RoutineTargetCodec.kt` (`T001`–`T006`) so every UI, logger, sync, and MCP layer shares an identical lossless `[rb_v2=...]` model.
2. **Core Mobile UX (`US1` + `US2`)**: Implement `RoutineBuilderSheet.kt` + `RoutineBuilderUiStateTest.kt` and wire into `LibraryScreen.kt` (`T007`–`T012`).
3. **Workout Logging & Remote Parity (`US3` + `US4`)**: Wire `expandRoutineIntoSessionBlocks` in `SessionEditor.kt` (`T013`–`T014`) and implement TypeScript parity in `mcp-server/` + Cloud Sync (`T015`–`T019`).
4. **Full CI Parity & Artifact Gate**: Run Android + PowerShell + Lint + APK Sync + MCP test suites (`T020`–`T021`).
