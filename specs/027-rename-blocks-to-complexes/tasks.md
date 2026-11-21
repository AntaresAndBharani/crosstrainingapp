# Tasks: Rename Blocks to Complexes (`SPEC-027`)

**Input**: Design documents from `/specs/027-rename-blocks-to-complexes/`  
**Prerequisites**: `plan.md` (required), `spec.md` (required), `research.md`, `data-model.md`, `contracts/complexes-ui-and-mcp-contract.md`

**Format**: `[ID] [P?] [Story] Description`

## Phase 1: Setup (Shared Infrastructure)

- [X] T001 Verify feature branch `feat/027-rename-blocks-to-complexes` and baseline specs/plan/checklists in `specs/027-rename-blocks-to-complexes/`

---

## Phase 2: Foundational (Pure UI Helper Foundations & MCP Schema Foundations)

- [X] T002 Update pure Library/Responsive/Validation helper functions (`formatLibraryTabLabels`, `formatLibraryTabContentDescription`, `shouldUseScrollableLibraryTabs`, `shouldStackCompactActions`, `librarySearchPlaceholder`, `canSaveComplexEditor`, `complexEditorValidationError`) in `app/src/main/java/com/fractanomics/crosstraining/ui/screens/LibraryScreen.kt`
- [X] T003 [P] Add Complex-first flat Zod schemas (`ListComplexesInputSchema`, `UpsertComplexInputSchema`, `BatchUpsertComplexesInputSchema`, `DeleteComplexInputSchema`, `LogComplexSessionInputSchema`, `UpdateComplexSessionLogInputSchema`), extend legacy Block/Daily Routine schemas (`item_type: ["COMPLEX", "EXERCISE", "BLOCK"]`, `complex_id`, `complex_title`, `complex_notes`, `session_complex_id`, `sync_parent_complex_default_prs`), and update descriptions without `$ref` or `exclusiveMinimum` in `mcp-server/src/schemas.ts`

**Checkpoint**: Foundation helpers and MCP schemas ready — user story implementation can proceed cleanly.

---

## Phase 3: User Story 1 - Library Browsing & Complex Creation/Editing (Priority: P1) 🎯 MVP

**Goal**: Rename Library `Blocks` / `Available Block` UI to `Complexes` / `Complex`, fix `360dp` tab & search clipping, add `fontScale >= 1.2f` `ScrollableTabRow` and `fontScale >= 1.4f` action stacking, wrap `Complex Name` up to 2 lines, and enforce name + `>= 1` exercise validation in `QuickCreateBlockSheet`.

**Independent Test**: Run `LibraryDailyRoutinesTest` verifying tab labels, TalkBack descriptions, fontScale thresholds, search placeholders, and Complex editor save/error validation rules.

### Tests for User Story 1

- [X] T004 [P] [US1] Update tab label test and add unit tests for `formatLibraryTabContentDescription`, `shouldUseScrollableLibraryTabs`, `shouldStackCompactActions`, `librarySearchPlaceholder`, `canSaveComplexEditor`, and `complexEditorValidationError` in `app/src/test/java/com/fractanomics/crosstraining/ui/screens/LibraryDailyRoutinesTest.kt`

### Implementation for User Story 1

- [X] T005 [US1] Update 3-tab row (`ScrollableTabRow` fallback when `shouldUseScrollableLibraryTabs(fontScale)` is true, `13.sp`, `tnum`, TalkBack contentDescriptions), tab search placeholders (`"Search routines..."`, `"Search complexes..."`, `"Search exercises..."`), Complexes empty/search states, delete Snackbar (`"Complex removed"`), and Journey snackbar (`"(Complex '${routine.name}')"`) in `app/src/main/java/com/fractanomics/crosstraining/ui/screens/LibraryScreen.kt`
- [X] T006 [US1] Update `AvailableBlockCard` (`"Log Complex ▸"`, `fontScale >= 1.4f` stacking, `"Complex options for ${block.title}"`) and `QuickCreateBlockSheet` (`"New Complex"` / `"Edit Complex"`, `"Complex Name"` with `maxLines = 2`, `"Filter exercises..."`, `navigationBarsPadding().imePadding()`, strict name + `>= 1` exercise validation with `"Enter a complex name and select at least 1 exercise."`, `"Create Complex"` / `"Save Complex"`) in `app/src/main/java/com/fractanomics/crosstraining/ui/screens/LibraryScreen.kt`
- [X] T007 [P] [US1] Update `duplicateAvailableBlock` blank fallback title from `"Block"` to `"Complex"` in `app/src/main/java/com/fractanomics/crosstraining/ui/AppViewModel.kt`

**Checkpoint**: User Story 1 functional and unit-tested.

---

## Phase 4: User Story 2 - Composite Daily Routine Composer & Cards (Priority: P1)

**Goal**: Rename `Block` references to `Complex` on Daily Routine cards and inside `DailyRoutineComposerSheet` / `RoutineItemTargetsEditor`, and move `COMPLEX` / `EXERCISE` badges onto Row 1 (eyebrow) above full-width Row 2 titles while stacking `+ Add Complex` / `+ Add Exercise` at `fontScale >= 1.4f`.

**Independent Test**: Run `LibraryDailyRoutinesTest` verifying `COMPLEX:` chip formatting, `1 Complex` / `2 Complexes` telemetry, and `"Add a routine title and at least 1 complex or exercise."` composer validation.

### Tests for User Story 2

- [X] T008 [P] [US2] Update `formatDailyRoutineItemChipLabel`, `formatDailyRoutineTelemetry`, and `DailyRoutineComposerState.validationErrorMessage` assertions for `COMPLEX:` and `Complex` / `Complexes` in `app/src/test/java/com/fractanomics/crosstraining/ui/screens/LibraryDailyRoutinesTest.kt`

### Implementation for User Story 2

- [X] T009 [US2] Update `formatDailyRoutineItemChipLabel` (`"COMPLEX: ..."`), `formatDailyRoutineTelemetry` (`"1 Complex"` / `"{N} Complexes"`), `DailyRoutineComposerState.validationErrorMessage`, `DailyRoutineCard` TalkBack & missing-complex warnings (`"1 removed complex hidden"` / `"{N} removed complexes hidden"` / `"Dismiss removed complex notice"`), and `DailyRoutineComposerSheet` copy, Row 1 eyebrow badge + Row 2 full-width title layout, `fontScale >= 1.4f` button stacking, and Complex Picker copy in `app/src/main/java/com/fractanomics/crosstraining/ui/screens/LibraryScreen.kt`
- [X] T010 [P] [US2] Update `InlineBlockTargetsConfigPanel` stepper label from `"Block Sets (this routine)"` to `"Complex Sets (this routine)"` in `app/src/main/java/com/fractanomics/crosstraining/ui/screens/RoutineItemTargetsEditor.kt`

**Checkpoint**: User Stories 1 & 2 functional and unit-tested.

---

## Phase 5: User Story 3 - Workout Session Logging, Editing & PR Tracking (Priority: P2)

**Goal**: Rename `Block` terminology to `Complex` across `SessionEditor`, `SimplifiedBlockLogCard`, `AddBlockToSessionSheet`, `MultiMovementPrPickerSheet`, and `LoadDailyRoutineSheet`.

**Independent Test**: Run `SessionEditorAddBlockTest` verifying `"Select Complex(es)"`, `"Add 1 Complex"`, `"Add 3 Complexes"`, plus `SessionEditorDailyRoutineAndExerciseTest` and `SessionEditorBlockLogEditTest`.

### Tests for User Story 3

- [X] T011 [P] [US3] Update `formatSavedBlocksConfirmLabel` test assertions (`"Select Complex(es)"`, `"Add 1 Complex"`, `"Add 3 Complexes"`) in `app/src/test/java/com/fractanomics/crosstraining/ui/screens/SessionEditorAddBlockTest.kt`

### Implementation for User Story 3

- [X] T012 [US3] Update `SimplifiedBlockLogCard` (`"COMPLEX: ..."`, `"Untitled Complex"`, `"Remove complex ..."`), `formatSavedBlocksConfirmLabel`, `AddBlockToSessionSheet` (`"Add Complex to Session"`, `"Saved Complexes"`, `"Custom Complex"`, `"Complex Name (optional)"` with `maxLines = 2`, `"Save to Complexes library"`, `"Add Complex"`), `MultiMovementPrPickerSheet` (`"In this complex"`), and `LoadDailyRoutineSheet` (`"Search routines..."`, `Complex`/`Complexes`, `"COMPLEX: ..."`) in `app/src/main/java/com/fractanomics/crosstraining/ui/screens/SimplifiedBlockLogComponents.kt`
- [X] T013 [P] [US3] Update `"Add Block"` button to `"Add Complex"` (with vertical stacking at `fontScale >= 1.4f`) and empty validation snackbar (`"Add at least one complex or exercise with content."`) in `app/src/main/java/com/fractanomics/crosstraining/ui/screens/SessionEditor.kt`

**Checkpoint**: User Story 3 functional and unit-tested.

---

## Phase 6: User Story 4 - Progress & Goals Analytics Views (Priority: P2)

**Goal**: Rename `Block` labels to `Complex` in `ProgressScreen` (`By complex`, `All complexes`, `Complex` dropdown, `Filter Performance by Complex:`, `All Complexes (Global)`, `Per-Complex Routine Breakdown`, `Complex history`, `COMPLEX:` / `COMPLEX` Daily Routine chips & badges) while leaving Fat Loss `"Block Completion"` untouched.

**Independent Test**: Verify `DailyRoutineProgressTest` and `ProgressScreen` compilation/lint plus manual grep verifying zero leftover Block copy outside Fat Loss.

### Implementation for User Story 4

- [X] T014 [US4] Update mode label (`"By complex"`), `"All complexes"`, Daily Routine Progress copy/chips/badges (`"COMPLEX: "`, `"COMPLEX"`), `RoutineProgress` (`"Complex"`, `"Filter Performance by Complex:"`, `"All Complexes (Global)"`, `"Per-Complex Routine Breakdown"`, empty text), and `BlockHistoryCard`/`BlockHistoryRow` (`"Complex history"`, `"Complex"`) while preserving Fat Loss `"Block Completion"` untouched in `app/src/main/java/com/fractanomics/crosstraining/ui/screens/ProgressScreen.kt`

**Checkpoint**: User Story 4 complete.

---

## Phase 7: User Story 5 - Remote MCP Complex-First Tools & Backward-Compatible Aliases (Priority: P2)

**Goal**: Expose 6 primary Complex MCP tools alongside 6 legacy Block tools (45 tools total), support `item_type: "COMPLEX"` + `complex_id` / `complex_title` precedence in Daily Routine tools, and return Complex-first response fields while maintaining zero-migration Firestore storage compatibility.

**Independent Test**: Run `npm run build && npm test` in `mcp-server/` verifying 45 tools, zero `$ref`/`exclusiveMinimum`, Complex CRUD/session logging/editing, Daily Routine `item_type: "COMPLEX"` + precedence, and legacy Block compatibility.

### Implementation for User Story 5

- [X] T015 [US5] Implement Complex/Block alias normalization helpers (`normalizeDailyRoutineItemType`, `resolveComplexId`, `resolveComplexTitle`, precedence rules), support `complex_id`/`complex_title`/`complexNotes`/`session_complex_id`/`sync_parent_complex_default_prs`, emit `itemType: "COMPLEX"`, `complexId`, `complexTitle`, `complexCount`, `missingComplexCount`, and `complexesCount` alongside legacy keys, and update error messages while preserving Firestore storage markers in `mcp-server/src/store.ts`
- [X] T016 [US5] Register 6 primary Complex tools (`list_complexes`, `upsert_complex`, `batch_upsert_complexes`, `delete_complex`, `log_complex_session`, `update_complex_session_log`), retain all 6 legacy Block tools routing to shared domain handlers, and update `CROSSTRAINING_MCP_INSTRUCTIONS` and tool descriptions in `mcp-server/src/mcp.ts`
- [X] T017 [US5] Update tool count (`45`), `expectedReadOnlyTools` / `expectedDeleteTools`, and `itemType: "COMPLEX"` assertions, and add comprehensive integration tests for Complex tools, Daily Routine `item_type: "COMPLEX"`, `complex_*` precedence over `block_*`, and legacy Block backward compatibility in `mcp-server/test/mcp-server.test.ts`

**Checkpoint**: User Story 5 functional and integration-tested.

---

## Phase 8: Polish, Convergence Audit & Cross-Cutting Verification

- [X] T018 Verify zero unintended `"Block"` copy across the 6 targeted Android surfaces (`LibraryScreen.kt`, `RoutineItemTargetsEditor.kt`, `SimplifiedBlockLogComponents.kt`, `SessionEditor.kt`, `ProgressScreen.kt` outside Fat Loss) and confirm out-of-scope surfaces (`CyclesScreen.kt`, Fat Loss `"Block Completion"`, `QuickAddWorkoutDialog.kt`, `WorkoutJourneyAssistantSheet.kt`, `VoiceWorkoutIngestionSheet.kt`, `RoutineBuilderSheet.kt`) remain untouched
- [X] T019 Run full pre-PR verification suite (`.\gradlew.bat testDebugUnitTest --no-daemon`, `.\gradlew.bat lintDebug --no-daemon`, `.\scripts\tests\Invoke-ScriptTests.ps1`, `npm run build && npm test` in `mcp-server/`) and confirm 100% pass
- [X] T020 Update `CHANGELOG.md` under `## [Unreleased]` summarizing `SPEC-027`
- [ ] T021 Stage changes, commit, push `feat/027-rename-blocks-to-complexes`, open Pull Request with Mission Plan summary, and verify remote CI checks pass 100% green (`gh pr checks --watch`)

## Dependencies & Execution Order

- **Phase 1 → Phase 2**: Must complete before user stories. `T002` and `T003` can run in parallel.
- **Phase 3–7 (User Stories)**:
  - `US1` (`T004–T007`) and `US2` (`T008–T010`) touch `LibraryScreen.kt` sequentially; `T007` and `T010` can run in parallel.
  - `US3` (`T011–T013`) and `US4` (`T014`) touch independent files (`SimplifiedBlockLogComponents.kt`, `SessionEditor.kt`, `ProgressScreen.kt`) and can proceed right after Phase 2.
  - `US5` (`T015–T017`) depends on `T003` (`schemas.ts`) and runs sequentially `T015 -> T016 -> T017`.
- **Phase 8 (Polish & Delivery)**: Depends on all stories (`T004–T017`) completing cleanly.
