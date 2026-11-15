---
description: "Task list for Unified Add Block Workflow in Edit & Log Session (SPEC-024)"
---

# Tasks: Unified Add Block Workflow in Edit & Log Session (`SPEC-024`)

**Input**: Design documents from `/specs/024-session-add-block/`  
**Prerequisites**: `plan.md`, `spec.md`, `research.md`, `data-model.md`, `contracts/session-add-block-ui-contract.md`

**Organization**: Tasks are grouped by phase and user story (`[US1]`, `[US2]`, `[US3]`) so each slice is independently testable.

## Format: `[ID] [P?] [Story] Description`

- **[P]**: Can run in parallel (different files, no dependencies on incomplete tasks)
- **[Story]**: User story label (`US1`, `US2`, `US3`) for story phases

---

## Phase 1: Setup & Contract Scaffolding

**Purpose**: Establish stable UI keys, enums, and pure state helpers used across the unified Add Block workflow.

- [X] T001 Add stable `uiKey: String = java.util.UUID.randomUUID().toString()` to `LoadedAvailableBlockState` in `app/src/main/java/com/fractanomics/crosstraining/ui/screens/SimplifiedBlockLogComponents.kt`
- [X] T002 Add `AddBlockSheetTab`, `AddBlockSheetOpenMode`, and pure helpers `resolveInitialAddBlockTab`, `resolveCustomBlockTitle`, and `canSubmitCustomBlock` in `app/src/main/java/com/fractanomics/crosstraining/ui/screens/SimplifiedBlockLogComponents.kt` and `app/src/main/java/com/fractanomics/crosstraining/ui/screens/SessionEditor.kt`

---

## Phase 2: Foundational State & Persistence Helpers

**Purpose**: Core repository, ViewModel, and block-state builders required before sheet UI and editor wiring.

- [X] T003 [P] Add `prepareCustomAvailableBlock(rawTitle, instructions, selectedExerciseIds, saveToLibrary)` in `app/src/main/java/com/fractanomics/crosstraining/data/Repository.kt` handling zero-chip title-to-exercise resolution (`MetricType.WEIGHT`, `tracksRepMax = true`), session-only (`id = 0L`, no `routines` row) vs `saveAvailableBlock` persistence
- [X] T004 [P] Add `prepareCustomSessionBlock(rawTitle, instructions, selectedExerciseIds, saveToLibrary, onReady)` in `app/src/main/java/com/fractanomics/crosstraining/ui/AppViewModel.kt` delegating to `Repository.prepareCustomAvailableBlock` and triggering `UserCloudSyncManager.uploadUserData` when `saveToLibrary == true`
- [X] T005 Update `buildLoadedAvailableBlockState` (adding `isEditMode: Boolean = false`, `editingSessionId: Long? = null`, and `excludeSessionId = editingSessionId` on `resolveLastBlockSession`) and add `buildCustomAvailableBlockState` (3 empty tiles per movement, `lastSessionDateLabel = null`) in `app/src/main/java/com/fractanomics/crosstraining/ui/screens/SimplifiedBlockLogComponents.kt`

**Checkpoint**: Foundational state builders and persistence helpers compile cleanly and preserve existing callers.

---

## Phase 3: User Story 1 - Add Saved Available Blocks in Edit & Log Session (Priority: P1) 🎯 MVP

**Goal**: Open `AddBlockToSessionSheet` from `+ Add Block` and `Load Block ▾`, cleanly distinguish already-loaded saved blocks from new selections, pre-fill newly appended saved blocks in Edit Session with `"Pre-filled from last session ({Date})"` visible, and auto-scroll the new card into view.

**Independent Test**: Verify `resolveInitialAddBlockTab` routing, `buildLoadedAvailableBlockState(..., isEditMode = true, editingSessionId = 20L)` pre-filling from prior sessions excluding `session 20` with `lastSessionDateLabel` populated, and `buildHydratedBlockState` keeping `lastSessionDateLabel == null` on originally logged blocks.

- [X] T006 [US1] Update `SimplifiedBlockLogCard` in `app/src/main/java/com/fractanomics/crosstraining/ui/screens/SimplifiedBlockLogComponents.kt` to accept `modifier: Modifier = Modifier` on root `ElevatedCard` and render `exState.lastSessionDateLabel` whenever `!exState.lastSessionDateLabel.isNullOrBlank()` (allowing newly appended saved blocks in Edit Session to show their pre-fill hint while originally hydrated blocks keep `null`)
- [X] T007 [US1] Implement `AddBlockToSessionSheet` (`ModalBottomSheet` with `imePadding()`, `>= 48.dp` tabs/controls, and Saved Blocks tab marking `alreadyLoadedIds` as `"Already in session"`, non-selectable, and uncounted in `selectedNewIds` with `"Add 1 Block"` / `"Add {N} Blocks"` CTA and empty-state switch CTA) in `app/src/main/java/com/fractanomics/crosstraining/ui/screens/SimplifiedBlockLogComponents.kt`
- [X] T008 [US1] Wire `AddBlockSheetOpenMode` (`Load Block ▾` -> `PREFER_SAVED`, `+ Add Block` -> `SMART_DEFAULT`), `AddBlockToSessionSheet`, `key(availState.uiKey)`, `CardLayoutBounds` tracking, and frame-synchronized `scrollState.animateScrollTo` (`resolveGoalCardScrollOffset`) in `app/src/main/java/com/fractanomics/crosstraining/ui/screens/SessionEditor.kt`

**Checkpoint**: Tapping `+ Add Block` or `Load Block ▾` in Edit/Log/Copy Session opens `AddBlockToSessionSheet`, appends selected saved blocks with pre-fill hints, and auto-scrolls smoothly into view.

---

## Phase 4: User Story 2 - Create & Append Custom Exercise Blocks (Priority: P1)

**Goal**: Allow athletes to switch to `Custom Block` inside `AddBlockToSessionSheet`, choose movements or create new ones inline (or enter a title with 0 chips), keep `"Save to Available Blocks library"` OFF by default, and append a simplified weight-tile card (`Set 1..3`) scrolled into view.

**Independent Test**: Verify `resolveCustomBlockTitle`, `canSubmitCustomBlock`, `buildCustomAvailableBlockState`, and `Repository.prepareCustomAvailableBlock` for `saveToLibrary = false` (0 `Routine` rows) vs `saveToLibrary = true` (persisted `AVAILABLE_BLOCK` + weighted movement auto-creation).

- [X] T009 [US2] Implement the `Custom Block` tab inside `AddBlockToSessionSheet` in `app/src/main/java/com/fractanomics/crosstraining/ui/screens/SimplifiedBlockLogComponents.kt` with optional **Block Name** (dynamic placeholder), optional **Instructions**, searchable movement `FilterChip`s + inline `+ Add` weighted movement creation, `"Save to Available Blocks library"` `Switch` (`false` default, `>= 48.dp` row with merged TalkBack semantics), and `Add Block` CTA gated by `canSubmitCustomBlock`
- [X] T010 [US2] Wire `onConfirmCustomBlock` -> `viewModel.prepareCustomSessionBlock` in `app/src/main/java/com/fractanomics/crosstraining/ui/screens/SessionEditor.kt` to build `LoadedAvailableBlockState` (using `buildLoadedAvailableBlockState` when saved to library or `buildCustomAvailableBlockState` when session-only), append to `loadedAvailableBlocks`, mark `simplifiedStructuralDirty = true`, preserve existing `Edit Session` title, and trigger auto-scroll

**Checkpoint**: Custom exercise blocks append as `SimplifiedBlockLogCard`s (`Set 1..3`) with zero Library pollution unless opted in.

---

## Phase 5: User Story 3 - Mixed-Session Coexistence, Dirty Guard & Reconciliation (Priority: P2)

**Goal**: Protect unsaved state during live emissions, enable residual legacy block deletion alongside simplified blocks, auto-scroll `Quick Parse` additions, and verify end-to-end save/update + PR reconciliation.

**Independent Test**: Verify `SessionEditorAddBlockTest` edit-mode `Repository.updateSession(..., reconcileRepMaxes = true)` across hydrated + newly added Saved Block + newly added session-only Custom Block with PRs, and verify `canRemove` logic for mixed sessions.

- [X] T011 [US3] Guard edit-mode re-hydration `LaunchedEffect` against `activeAddBlockSheetMode != null`, clean up `blockCardBounds` on block deletion, pass `isEditMode`/`editingSessionId` to `pendingBlockIdsToLog` consumption, update `CompactBlockEditor` `canRemove` to `loadedAvailableBlocks.isNotEmpty() || blocks.size > 1`, and wire `Quick Parse` bounds + auto-scroll in `app/src/main/java/com/fractanomics/crosstraining/ui/screens/SessionEditor.kt`
- [X] T012 [P] [US1] [US2] [US3] Create comprehensive unit & Robolectric/Room tests covering initial tab routing, edit-mode pre-fill exclusion vs hydrated block suppression, custom block title/state helpers, `Repository.prepareCustomAvailableBlock` (`saveToLibrary = false/true` & zero-chip auto-exercise), and end-to-end `Repository.updateSession` PR reconciliation with appended blocks in `app/src/test/java/com/fractanomics/crosstraining/ui/screens/SessionEditorAddBlockTest.kt`

**Checkpoint**: All user stories are implemented, isolated, and verified by deterministic unit and repository integration tests.

---

## Phase 6: Polish & Cross-Cutting Verification

**Purpose**: Full verification suite, changelog maintenance, APK artifact sync, and PR preparation.

- [X] T013 Run full local verification suite (`.\gradlew.bat testDebugUnitTest --no-daemon`, `.\scripts\tests\Invoke-ScriptTests.ps1`, `.\gradlew.bat lintDebug --no-daemon`, `.\gradlew.bat assembleDebug --no-daemon`) and copy debug APK to `local_test\latest.apk`
- [X] T014 Add `SPEC-024` release note under `## [Unreleased]` in `CHANGELOG.md` and perform final convergence audit across `spec.md`, `plan.md`, `tasks.md`, and code

---

## Dependencies & Execution Order

- **Phase 1 (T001–T002)** -> **Phase 2 (T003–T005)** -> **Phase 3 (T006–T008)** & **Phase 4 (T009–T010)** -> **Phase 5 (T011–T012)** -> **Phase 6 (T013–T014)**
- Tasks marked `[P]` (`T003` & `T004`, `T012`) target independent files and can run concurrently once prerequisites are in place.
