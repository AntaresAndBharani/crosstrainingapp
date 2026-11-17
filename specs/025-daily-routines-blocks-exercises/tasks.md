---

description: "Executable task list for Daily Routines Library, Unified Block & Exercise Tracking, Routine Progress & MCP Parity (SPEC-025)"
---

# Tasks: Daily Routines (`SPEC-025`)

**Input**: Design documents from `/specs/025-daily-routines-blocks-exercises/`  
**Prerequisites**: `plan.md`, `spec.md`, `research.md`, `data-model.md`, `contracts/daily-routines-ui-and-mcp-contract.md`

## Phase 1: Setup

**Purpose**: Initialize Spec-Kit artifacts and verify feature branch isolation

- [X] T001 Create branch `feat/025-daily-routines-blocks-exercises` and set `.specify/feature.json` to `specs/025-daily-routines-blocks-exercises`
- [X] T002 Create `spec.md`, `research.md`, `data-model.md`, `contracts/daily-routines-ui-and-mcp-contract.md`, `quickstart.md`, `plan.md`, and checklists under `specs/025-daily-routines-blocks-exercises/`

---

## Phase 2: Foundational (Codecs, Persistence, Cloud Sync & ViewModel State)

**Purpose**: Core Daily Routine persistence encoding, namespace isolation, cross-app unified history lookups, and ViewModel orchestration required before UI stories

- [X] T003 [P] Update `METADATA_HEADER_LINE_REGEX` in `app/src/main/java/com/fractanomics/crosstraining/util/RoutineTargetCodec.kt` to strip `[dr_item=...]` headers cleanly while keeping `HEADER_LINE_REGEX` scoped strictly to `[rb_v2=...]`
- [X] T004 Implement `DailyRoutineCodec.kt` (`DAILY_ROUTINE`, `DR_BLOCK`, `DR_EXERCISE`, `STANDALONE_EXERCISE` constants, `[dr_item=...]` round-trip encode/decode, deleted-block omission & `missingBlockCount`, `routineNaturalKey`, `filterDailyRoutines`, `resolveLastExerciseSession`, `resolveDefaultTrackedPrSlotsForExercise`, and `computeDailyRoutineProgress`) in `app/src/main/java/com/fractanomics/crosstraining/util/DailyRoutineCodec.kt`
- [X] T005 Update `AvailableBlockCodec.kt` (`HydratedAvailableBlockSeed.isStandaloneExercise` and `STANDALONE_EXERCISE` hydration path) in `app/src/main/java/com/fractanomics/crosstraining/util/AvailableBlockCodec.kt`
- [X] T006 Update `Repository.kt` to isolate routine natural keys via `DailyRoutineCodec.routineNaturalKey(...)` across `saveRoutine`, `saveRoutineWithBlocks`, and `cleanupDuplicateRoutinesInternal`, and add `saveDailyRoutine` and `updateDailyRoutineItems` in `app/src/main/java/com/fractanomics/crosstraining/data/Repository.kt`
- [X] T007 Update `UserCloudSyncManager.kt` to deduplicate upload/download routines by `dr:`/`ab:` namespace key, scope tombstone deletions, and pre-create `[dr_item=EXERCISE:...]` exercises on download in `app/src/main/java/com/fractanomics/crosstraining/data/firebase/UserCloudSyncManager.kt`
- [X] T008 Update `SessionDraft.kt` (`syncRoutineTemplateId`, `syncRoutineTemplateItems`) and `AppViewModel.kt` (`dailyRoutines` StateFlow, `availableBlocks` excluding `DAILY_ROUTINE`, `pendingRoutineIdToLog`, `pendingLibraryTab`, Daily Routine CRUD/Undo actions, and routine template sync on `saveSession`/`updateSession`) in `app/src/main/java/com/fractanomics/crosstraining/ui/SessionDraft.kt` and `app/src/main/java/com/fractanomics/crosstraining/ui/AppViewModel.kt`
- [X] T009 [P] Write JVM unit tests for `[dr_item=...]` round-trip encoding, deleted block omission, unified exercise pre-fill, PR auto-slot resolution, and `computeDailyRoutineProgress` in `app/src/test/java/com/fractanomics/crosstraining/util/DailyRoutineCodecTest.kt`
- [X] T010 [P] Write JVM unit tests for `Repository` namespace isolation (`dr:` vs `ab:`), `updateDailyRoutineItems`, and `UserCloudSyncManager` Daily Routine + `STANDALONE_EXERCISE` round-trip/tombstone sync in `app/src/test/java/com/fractanomics/crosstraining/data/DailyRoutineRepositoryAndSyncTest.kt`

**Checkpoint**: Foundational codec, persistence, cloud sync, and ViewModel APIs are ready and test-verified.

---

## Phase 3: User Story 1 — 3-Section Library & Fast Daily Routine Composer (Priority: P1)

**Goal**: Provide the 3-tab Library (`Routines (N) | Blocks (N) | Exercises (N)`), `DailyRoutineCard` with `BLOCK:`/`EX:` chips and `Log Routine ▸`, and `DailyRoutineComposerSheet`.

**Independent Test**: `LibraryDailyRoutinesTest` verifies 3-tab helpers, composer item validation/reordering/note editing, `> 6` chip overflow collapsing, and missing block indicator formatting.

- [X] T011 [P] [US1] Write unit tests for Library 3-tab routing, `DailyRoutineComposerState` validation/reordering/notes, and `DailyRoutineCard` chip overflow / missing-block state in `app/src/test/java/com/fractanomics/crosstraining/ui/screens/LibraryDailyRoutinesTest.kt`
- [X] T012 [US1] Implement 3-tab `TabRow` (`Routines ({count}) | Blocks ({count}) | Exercises ({count})`), `DailyRoutinesTab`, `DailyRoutineCard`, and `DailyRoutineComposerSheet` (`+ Add Block` + `+ Add Exercise` pickers, `↑`/`↓`/`✕`, inline note input, `imePadding()`) in `app/src/main/java/com/fractanomics/crosstraining/ui/screens/LibraryScreen.kt`
- [X] T013 [US1] Wire `onLogRoutine` navigation handoff and `pendingLibraryTab` support in `app/src/main/java/com/fractanomics/crosstraining/ui/navigation/AppNavigation.kt`

**Checkpoint**: Library 3rd section (`Routines`), composer, and 1-tap `Log Routine ▸` handoff are complete.

---

## Phase 4: User Story 2 — Unified Block & Standalone Exercise Tracking in Log & Edit Session (Priority: P1)

**Goal**: Unify `Log Session` and `Edit Session` with `Load Daily Routine ▾`, `[ + Add Block ]`, `[ + Add Exercise ]`, `SimplifiedExerciseLogCard`, unified cross-app history pre-fill, conditional routine template sync checkbox, and single-line `DailyCheckInCard` `Rest` chip.

**Independent Test**: `SessionEditorDailyRoutineAndExerciseTest` verifies routine expansion, unified pre-fill vs 4 empty first-time tiles, standalone exercise PR auto-sync, `STANDALONE_EXERCISE` draft/hydration round-trip, and routine template diff/sync behavior.

- [X] T014 [P] [US2] Write unit tests for `DailyRoutine` session expansion, `buildLoadedStandaloneExerciseState`, live PR sync on standalone exercises, `STANDALONE_EXERCISE` hydration in `Edit Session`, and `hasStructuralDiffFromRoutine` template sync in `app/src/test/java/com/fractanomics/crosstraining/ui/screens/SessionEditorDailyRoutineAndExerciseTest.kt`
- [X] T015 [P] [US2] Fix `DailyCheckInCard.kt` `Rest` chip wrapping on compact viewports using `FlowRow` and `maxLines = 1, softWrap = false` in `app/src/main/java/com/fractanomics/crosstraining/ui/components/DailyCheckInCard.kt`
- [X] T016 [US2] Extend `LoadedAvailableBlockState` (`SessionItemKind`), add `buildLoadedStandaloneExerciseState`, implement `SimplifiedExerciseLogCard`, update `SimplifiedBlockLogCard` header with item index & `#0369A1`/`#7DD3FC` `BLOCK:` styling, update unlogged `buildLoadedAvailableBlockState` subtitle, and implement `LoadDailyRoutineSheet` and `AddExerciseToSessionSheet` in `app/src/main/java/com/fractanomics/crosstraining/ui/screens/SimplifiedBlockLogComponents.kt`
- [X] T017 [US2] Update `SessionEditor.kt` with top `LoadDailyRoutineSelectorBar`, `pendingRoutineIdToLog` consumption, blank legacy card suppression, `SimplifiedExerciseLogCard` / `SimplifiedBlockLogCard` dispatch, conditional `[ ] Also update "{Routine Title}" routine template` checkbox, and bottom `[ + Add Block ]` + `[ + Add Exercise ]` row in `app/src/main/java/com/fractanomics/crosstraining/ui/screens/SessionEditor.kt`

**Checkpoint**: Log Session & Edit Session track both Blocks and standalone Exercises identically with unified history and optional template sync.

---

## Phase 5: User Story 3 — Progress & Goals: `By routine` View (Priority: P2)

**Goal**: Add `By routine` (`ProgressMode.BY_DAILY_ROUTINE`) as the first chip in `ProgressScreen`, showing `All Items Overview` cards for every constituent Block and Exercise plus 1-tap drill-down to KPIs, charts, PRs, and set history.

**Independent Test**: `DailyRoutineProgressTest` and `ProgressScreenLazyRowAutoScrollTest` verify mode chip derivation (`By routine` first), constituent summary cards across unified history, unlogged item placeholders, and drill-down selection state.

- [X] T018 [P] [US3] Write unit tests for `DailyRoutine` progress overview + drill-down calculations in `app/src/test/java/com/fractanomics/crosstraining/ui/screens/DailyRoutineProgressTest.kt` and update mode derivation assertions in `app/src/test/java/com/fractanomics/crosstraining/ui/screens/ProgressScreenLazyRowAutoScrollTest.kt`
- [X] T019 [US3] Add `ProgressMode.BY_DAILY_ROUTINE` (`"By routine"` first in chip bar), filter `BY_ROUTINE` (`"By block"`) to non-Daily-Routine blocks, and implement `DailyRoutineProgressSection` (0-routines empty state + `Library -> Routines` CTA, `All Items Overview` cards with trend delta badges, and constituent drill-down KPIs/charts/PRs/history) in `app/src/main/java/com/fractanomics/crosstraining/ui/screens/ProgressScreen.kt` and `app/src/main/java/com/fractanomics/crosstraining/ui/navigation/AppNavigation.kt`

**Checkpoint**: Progress & Goals `By routine` view renders unified routine overview and drill-down analytics.

---

## Phase 6: User Story 4 — Remote MCP Daily Routines Parity (Priority: P2)

**Goal**: Expose `list_daily_routines`, `upsert_daily_routine`, `delete_daily_routine`, `log_daily_routine_session`, and `get_daily_routine_progress` in `mcp-server` with flat Gemini-compatible schemas and full test coverage.

**Independent Test**: `mcp-server` `npm run build && npm test` passes 39 tools (`0` `$ref`, `0` `exclusiveMinimum`), exercising all 5 Daily Routine tools, `update_routine_template`, unified progress queries, and `AvailableBlock` isolation.

- [X] T020 [US4] Add `DAILY_ROUTINE_FORMAT`, `DR_BLOCK`/`DR_EXERCISE`/`STANDALONE_EXERCISE` constants, `[dr_item=...]` helpers, and flat Zod input schemas (`ListDailyRoutinesInputSchema`, `UpsertDailyRoutineInputSchema`, `DeleteDailyRoutineInputSchema`, `LogDailyRoutineSessionInputSchema`, `GetDailyRoutineProgressInputSchema`) in `mcp-server/src/schemas.ts`
- [X] T021 [US4] Implement `listDailyRoutines`, `upsertDailyRoutine`, `deleteDailyRoutine`, `logDailyRoutineSession`, `getDailyRoutineProgress`, `dailyRoutineCount` on `getWorkoutSummary`, and `DAILY_ROUTINE` exclusion + namespace isolation in `mcp-server/src/store.ts`
- [X] T022 [US4] Register the 5 Daily Routine MCP tools with explicit `ToolAnnotations` and update `CROSSTRAINING_MCP_INSTRUCTIONS` in `mcp-server/src/mcp.ts`
- [X] T023 [US4] Update tool count (`39`), annotation sets, and add Daily Routines + `log_daily_routine_session` + `get_daily_routine_progress` integration tests in `mcp-server/test/mcp-server.test.ts`

**Checkpoint**: Remote MCP server achieves 100% parity with Android Daily Routines.

---

## Phase 7: Polish, Convergence & Pre-PR Verification

**Purpose**: Run full verification gates, build and sync debug APK, update `CHANGELOG.md`, commit, and open PR

- [X] T024 Run convergence audit (`speckit-converge` rules) verifying every `FR-001..FR-014`, `SC-001..SC-004`, and Constitution gate against implemented files
- [X] T025 Run full Pre-PR Verification Suite (`.\gradlew.bat testDebugUnitTest --no-daemon`, `.\scripts\tests\Invoke-ScriptTests.ps1`, `.\gradlew.bat lintDebug --no-daemon`, `mcp-server` `npm run build && npm test`) and `.\gradlew.bat assembleDebug --no-daemon` synced to `local_test\latest.apk`
- [X] T026 Update `CHANGELOG.md` under `## [Unreleased]` summarizing `SPEC-025`
- [ ] T027 Stage explicit feature files (excluding untracked `firestore.rules` and `weight-export-pv.joseangel.csv`), commit, push `feat/025-daily-routines-blocks-exercises`, open Pull Request via `gh pr create`, and verify remote CI passes 100% green (`gh pr checks --watch`)

---

## Dependencies & Execution Order

- **Phase 2 (Foundational)** blocks Phases 3, 4, 5, and 6 on Android/shared model conventions.
- **Phase 3 (US1)**, **Phase 4 (US2)**, **Phase 5 (US3)**, and **Phase 6 (US4)** can progress cleanly in order with unit tests written before or alongside implementation for each story.
- **Phase 7** executes after all user stories are implemented and verified.
