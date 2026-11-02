# Tasks: Cycle Movement Goal Search Keyboard & Filtering

**Input**: Design documents from `/specs/012-fix-movement-search-keyboard/`

**Prerequisites**: `plan.md`, `spec.md`, `research.md`, `data-model.md`, `contracts/ui-movement-goal-selector-contract.md`

**Tests**: Unit tests in `CyclesScreenTest.kt` are required by Constitution Principle III (Test-First & 100% CI Parity).

**Organization**: Tasks are grouped by phase and user story (`[US1]`, `[US2]`) to enable independent implementation and verification.

## Format: `[ID] [P?] [Story?] Description`

- **[P]**: Can run in parallel (different files, no dependencies)
- **[Story]**: Which user story this task belongs to (`[US1]`, `[US2]`)
- Exact file paths are included in every task description

---

## Phase 1: Setup (Shared Infrastructure)

**Purpose**: Verify baseline project state and inspect existing `MovementGoalSelector` and `CyclesScreenTest` structure.

- [X] T001 Inspect `MovementGoalSelector` and imports in `app/src/main/java/com/fractanomics/crosstraining/ui/screens/CyclesScreen.kt` and existing test cases in `app/src/test/java/com/fractanomics/crosstraining/ui/screens/CyclesScreenTest.kt`

---

## Phase 2: Foundational (Blocking Prerequisites)

**Purpose**: Implement pure deterministic state and filtering helpers (`MovementSelectorUiState` and `filterMovementExercises`) that power both User Story 1 and User Story 2.

- [X] T002 Implement `internal fun filterMovementExercises(exercises: List<Exercise>, searchQuery: String): List<Exercise>` (returning `exercises` unchanged when `searchQuery.isBlank()`, or filtering by `exercise.name.contains(searchQuery.trim(), ignoreCase = true)`) and `internal data class MovementSelectorUiState(val expanded: Boolean = false, val searchQuery: String = "")` with pure transitions (`hasSearchQuery`, `toggleExpanded()`, `open()`, `dismiss()`, `onSearchQueryChanged(String)`, `clearSearchQuery()`, `onExerciseSelected()`, `filterExercises(List<Exercise>)`) in `app/src/main/java/com/fractanomics/crosstraining/ui/screens/CyclesScreen.kt`

**Checkpoint**: Foundational state model and filtering function ready for User Story 1 and User Story 2.

---

## Phase 3: User Story 1 - On-Screen Keyboard Appearance & Accurate Movement Search in Cycle Goal Selector (Priority: P1) 🎯 MVP

**Goal**: Ensure that expanding the `Movement (Basic)` selector or tapping `"Search movement…"` inside `CycleEditorDialog` renders the search box inside an IME-focusable surface (instead of a `FLAG_ALT_FOCUSABLE_IM` popup), requests focus and shows the Android soft keyboard immediately, filters movements in real time as the user types, and selects a movement cleanly.

**Independent Test**: Open `CycleEditorDialog`, expand `Movement (Basic)`, verify `"Search movement…"` focuses and shows the soft keyboard, type a query (`"squat"`), verify the movement list filters case-insensitively, and select a movement to update the goal draft and collapse the panel.

### Tests for User Story 1

- [X] T003 [P] [US1] Add unit tests in `app/src/test/java/com/fractanomics/crosstraining/ui/screens/CyclesScreenTest.kt` verifying `MovementSelectorUiState` expansion, real-time `onSearchQueryChanged` filtering via `filterMovementExercises` (case-insensitive substring match and whitespace trimming), and `onExerciseSelected` state reset + `GoalDraftState.onExerciseChanged` integration

### Implementation for User Story 1

- [X] T004 [US1] Replace `ExposedDropdownMenuBox` / `ExposedDropdownMenu` in `MovementGoalSelector` with an inline expandable `Surface` inside the `CycleEditorDialog` layout hierarchy, wire `FocusRequester` and `LocalSoftwareKeyboardController` via `LaunchedEffect(state.expanded)` so `"Search movement…"` automatically receives focus and displays the soft keyboard, configure `KeyboardOptions(imeAction = ImeAction.Search)` / `KeyboardActions`, and render the bounded (`Modifier.heightIn(max = 220.dp).verticalScroll(rememberScrollState())`) filtered movement list in `app/src/main/java/com/fractanomics/crosstraining/ui/screens/CyclesScreen.kt`

**Checkpoint**: User Story 1 is complete and independently verifiable.

---

## Phase 4: User Story 2 - Search Query Clearing, Toggle Collapse & Empty Filter Feedback (Priority: P2)

**Goal**: Provide a one-tap `"Clear search"` trailing button when `"Search movement…"` is non-empty, display a `"No matching movements found"` empty-state message when a search query matches zero exercises, and allow toggling the selector closed via `Change ▴` without losing the currently selected movement.

**Independent Test**: Expand `Movement (Basic)`, type a non-matching query (`"zzzz"`), verify `"No matching movements found"` is rendered, tap `"Clear search"` to restore the full list while keeping the panel open, and tap `Change ▴` to collapse the panel and preserve the selected movement.

### Tests for User Story 2

- [X] T005 [P] [US2] Add unit tests in `app/src/test/java/com/fractanomics/crosstraining/ui/screens/CyclesScreenTest.kt` verifying `clearSearchQuery()` restores the full unfiltered exercise list while keeping `expanded == true`, non-matching queries return an empty list, and `toggleExpanded()` collapses an open selector and clears any partial `searchQuery` without altering the selected exercise in `GoalDraftState`

### Implementation for User Story 2

- [X] T006 [US2] Add the trailing `"Clear search"` `IconButton` (`Icons.Filled.Close`, shown when `state.hasSearchQuery`), the `"No matching movements found"` empty-state message when `filteredExercises.isEmpty()`, and `"Change ▴"` / `"Change ▾"` toggle collapse behavior on the `Movement (Basic)` trigger surface in `app/src/main/java/com/fractanomics/crosstraining/ui/screens/CyclesScreen.kt`

**Checkpoint**: User Stories 1 and 2 are both complete and independently tested.

---

## Phase 5: Polish & Cross-Cutting Concerns

**Purpose**: Run full verification suite, lint checks, and sync debug APK artifact.

- [X] T007 Run `.\gradlew.bat testDebugUnitTest --no-daemon`, `.\scripts\tests\Invoke-ScriptTests.ps1`, and `.\gradlew.bat lintDebug --no-daemon` to verify 100% test and lint parity across `app/src/main/java/com/fractanomics/crosstraining/ui/screens/CyclesScreen.kt` and `app/src/test/java/com/fractanomics/crosstraining/ui/screens/CyclesScreenTest.kt`

---

## Dependencies & Execution Order

### Phase Dependencies

- **Setup (Phase 1)**: No dependencies (`T001`).
- **Foundational (Phase 2)**: Depends on `T001` (`T002`).
- **User Story 1 (Phase 3)**: Depends on `T002` (`T003` → `T004`).
- **User Story 2 (Phase 4)**: Depends on `T002` and `T004` (`T005` → `T006`).
- **Polish (Phase 5)**: Depends on `T004` and `T006` (`T007`).

### Parallel Opportunities

- Unit test authoring (`T003`, `T005` in `CyclesScreenTest.kt`) can be prepared alongside UI composable updates in `CyclesScreen.kt` once `T002` defines the state signatures.

## Implementation Strategy

### MVP First (User Story 1)

1. Complete `T001` and `T002` (`MovementSelectorUiState` & `filterMovementExercises`).
2. Complete `T003` and `T004` (inline IME-focusable `MovementGoalSelector` with `FocusRequester` + `LocalSoftwareKeyboardController`).
3. Complete `T005` and `T006` (clear action, empty state, and toggle collapse).
4. Validate with `T007` (`testDebugUnitTest`, `Invoke-ScriptTests.ps1`, `lintDebug`).
