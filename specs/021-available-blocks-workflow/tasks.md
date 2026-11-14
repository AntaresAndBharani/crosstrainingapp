# Tasks: Available Blocks Workflow (`SPEC-021`)

**Input**: Design documents from `specs/021-available-blocks-workflow/`
**Prerequisites**: `spec.md`, `plan.md`, `research.md`, `data-model.md`, `contracts/mcp-available-blocks.md`

## Phase 1: Setup & Core Codec (`AvailableBlockCodec`)

- [X] T001 Initialize `feat/021-available-blocks-workflow` and Spec-Kit artifacts under `specs/021-available-blocks-workflow/`
- [X] T002 Implement `AvailableBlockCodec.kt` in `app/src/main/java/com/fractanomics/crosstraining/util/AvailableBlockCodec.kt` (`AVAILABLE_BLOCK_FORMAT`, `TrackedPrSlot`, `AvailableBlock`, `[ab_prs=...]` codec, pre-fill resolver, decimal parser, search filter, live PR evaluator)
- [X] T003 Update `RoutineTargetCodec.extractCleanNotes` in `app/src/main/java/com/fractanomics/crosstraining/util/RoutineTargetCodec.kt` to strip `[ab_prs=...]` metadata lines

## Phase 2: Foundational Data, Sync & Analytics

- [X] T004 [US2] Add `newRepMaxes: List<RepMax>` to `BlockInsert` and update `Repository.saveSession` / `updateSession` (preserving PR `blockId` across block re-insertion) in `app/src/main/java/com/fractanomics/crosstraining/data/Repository.kt`
- [X] T005 [US2] Add `newRepMaxes: List<RepMax>` to `BlockDraft` in `app/src/main/java/com/fractanomics/crosstraining/ui/SessionDraft.kt`
- [X] T006 [US3] Scope `routineBlockPerformances` against cross-block contamination and add `resolveRepMaxAttribution` in `app/src/main/java/com/fractanomics/crosstraining/ui/ProgressAnalytics.kt`
- [X] T007 [US1][US2][US3] Update `UserCloudSyncManager.kt` (`[ab_prs=...]` exercise pre-creation, FK-safe session `routineId` resolution, `sourceBlockTitle`/`sessionTitle` `rep_maxes` sync)
- [X] T008 [US1][US2] Add `availableBlocks`, CRUD + undo helpers, `updateAvailableBlockTrackedPrs`, `pendingBlockIdsToLog`, and multi-PR `saveSession` wiring in `app/src/main/java/com/fractanomics/crosstraining/ui/AppViewModel.kt`

## Phase 3: User Story 1 - Available Blocks Library UI (P1)

- [X] T009 [US1] Wire Library `Log Block ▸` navigation callback to Log Session in `app/src/main/java/com/fractanomics/crosstraining/ui/navigation/AppNavigation.kt`
- [X] T010 [US1] Replace Tab 1 with `"Available Blocks ({count})"`, search bar, `AvailableBlockCard`, `QuickCreateBlockSheet` (3 fields), Duplicate/Delete + 4s `"Block removed"` `[Undo]` Snackbar in `app/src/main/java/com/fractanomics/crosstraining/ui/screens/LibraryScreen.kt`

## Phase 4: User Story 2 - Weight-Only Gym Session Logging & Auto-Inherited PRs (P1)

- [X] T011 [US2] Consume `pendingBlockIdsToLog`, replace `Load Routine` with `Load Block ▾` + `LoadAvailableBlockSheet`, and update primary CTA to `"Save Session ({N} New PRs)"` in `app/src/main/java/com/fractanomics/crosstraining/ui/screens/LogSessionScreen.kt`
- [X] T012 [US2] Render weight-only 4/2-column `FlowRow` set tiles (`minHeight = 64.dp`, `.`/`,` decimals, `"Pre-filled from last session ({Date})"`, `+ Add Set`), auto-inherited live PR chips (`[✕]` persistent removal), and `MultiMovementPrPickerSheet` (`+ Record PR 🔥` with `1RM..10RM`) in `app/src/main/java/com/fractanomics/crosstraining/ui/screens/SessionEditor.kt`

## Phase 5: User Story 3 - Progress & Goals Propagation (P2)

- [X] T013 [US3] Rename filter chip to `"By block"` (preserving `ProgressMode.BY_ROUTINE`), upgrade `RoutineProgress` header/KPIs/chart/expandable `Block history` PR badges, and render `"Achieved in {Block} · {Date}"` PR pills in `ExerciseProgress` in `app/src/main/java/com/fractanomics/crosstraining/ui/screens/ProgressScreen.kt`

## Phase 6: User Story 4 - Remote MCP Available Blocks & Logging (P1)

- [X] T014 [P] [US4] Add TS `[ab_prs=...]` codec and 5 flat Zod schemas in `mcp-server/src/schemas.ts`
- [X] T015 [US4] Implement Available Block CRUD, `log_block_session`, and `getWorkoutSummary` block counts in `mcp-server/src/store.ts`
- [X] T016 [US4] Register all 5 new MCP tools (`33` total) and update `CROSSTRAINING_MCP_INSTRUCTIONS` in `mcp-server/src/mcp.ts`

## Phase 7: Automated Tests, Convergence & Release Gate

- [X] T017 [P] Create `app/src/test/java/com/fractanomics/crosstraining/util/AvailableBlockCodecTest.kt`
- [X] T018 [P] Create `app/src/test/java/com/fractanomics/crosstraining/data/AvailableBlockRepositoryAndSyncTest.kt`
- [X] T019 [P] Extend `mcp-server/test/mcp-server.test.ts` (`33` tools, flat schemas, CRUD/session integration)
- [X] T020 Mark tasks complete (`[X]`), update `CHANGELOG.md`, sync `local_test\latest.apk`, run full verification suite (`mcp-server`, `testDebugUnitTest`, `Invoke-ScriptTests.ps1`, `lintDebug`, `assembleDebug`), open PR, and verify 100% green remote CI
