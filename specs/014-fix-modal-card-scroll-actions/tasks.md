---

description: "Task list for Modal Card Auto-Scroll & Non-Blocking Accordion Actions"
---

# Tasks: Modal Card Auto-Scroll & Non-Blocking Accordion Actions

**Input**: Design documents from `/specs/014-fix-modal-card-scroll-actions/`

**Prerequisites**: plan.md (required), spec.md (required for user stories), research.md, data-model.md, contracts/

**Tests**: Unit tests in `CyclesScreenTest.kt` and full pre-PR verification suite (`testDebugUnitTest`, `Invoke-ScriptTests.ps1`, `lintDebug`).

**Organization**: Tasks are grouped by user story to enable independent implementation and testing of each story.

## Format: `[ID] [P?] [Story] Description`

- **[P]**: Can run in parallel (different files, no dependencies)
- **[Story]**: Which user story this task belongs to (e.g., US1, US2, US3)
- Include exact file paths in descriptions

---

## Phase 1: Setup (Shared Infrastructure)

**Purpose**: Verify baseline test suite and inspect existing modal state models

- [X] T001 Verify baseline unit test compilation and execution for `CyclesScreen.kt` and `LibraryScreen.kt` via `app/src/test/java/com/fractanomics/crosstraining/ui/screens/CyclesScreenTest.kt`

---

## Phase 2: Foundational (Blocking Prerequisites)

**Purpose**: Core pure state/scroll helpers that all user stories build upon

- [X] T002 Implement `CardLayoutBounds` and pure `resolveGoalCardScrollOffset(cardTopPx, cardHeightPx, viewportHeightPx, currentScrollPx, maxScrollPx, topPaddingPx)` helper in `app/src/main/java/com/fractanomics/crosstraining/ui/screens/CyclesScreen.kt`
- [X] T003 Enhance `GoalDraftState` in `app/src/main/java/com/fractanomics/crosstraining/ui/screens/CyclesScreen.kt` with `lastValidTargetWeight: String`, `canRestoreValidTarget: Boolean`, and `isDiscardableEmptyDraft: Boolean` so valid targets (`> 0.0`) are tracked across `fromCycleGoal`, `onExerciseChanged`, `onGoalMetricChanged`, `onRepsChanged`, `onStartWeightChanged`, `onTargetWeightChanged`, `stepTarget`, `applyModifier`, and `resetModifier`

**Checkpoint**: Foundation ready - user story implementation can now begin

---

## Phase 3: User Story 1 - Non-Blocking Goal Expansion & Auto-Scroll in Cycle Editor (Priority: P1) 🎯 MVP

**Goal**: Ensure tapping `+ Add Movement` or expanding any collapsed goal card in `CycleEditorDialog` never deadlocks or leaves cards off-screen after two or more goals are added.

**Independent Test**: Add two movement goals and collapse them, tap `+ Add Movement` (verifying `Goal 3` is appended and scrolled into view), and tap `▾` on `Goal 1` or `Goal 2` while `Goal 3` has an empty target (verifying `Goal 3` is auto-discarded and `Goal 1` / `Goal 2` immediately expands and scrolls into view).

### Tests for User Story 1

- [X] T004 [US1] Add unit tests in `app/src/test/java/com/fractanomics/crosstraining/ui/screens/CyclesScreenTest.kt` verifying `expandGoalDraftAt` auto-discards open unsaved empty drafts (`id == 0 && !canRestoreValidTarget`), restores `lastValidTargetWeight` on previously valid open goals whose target was cleared, and never blocks expanding `Goal 1` or `Goal 2`
- [X] T005 [US1] Add unit tests in `app/src/test/java/com/fractanomics/crosstraining/ui/screens/CyclesScreenTest.kt` verifying `resolveGoalCardScrollOffset` for bottom-clipped cards (adding Goal 3 below Goal 1 & Goal 2), top-clipped error cards, taller-than-viewport cards, and already-visible cards

### Implementation for User Story 1

- [X] T006 [US1] Update `expandGoalDraftAt` in `app/src/main/java/com/fractanomics/crosstraining/ui/screens/CyclesScreen.kt` so expanding `targetIndex` never blocks on an open empty draft (removing `isDiscardableEmptyDraft` items and adjusting `targetIndex`, or restoring `lastValidTargetWeight` and collapsing previously valid items)
- [X] T007 [US1] Wire `scrollState`, per-card `Modifier.onGloballyPositioned` bounds tracking, and frame-synchronized auto-scroll (`withFrameNanos` + `scrollState.animateScrollTo`) in `CycleEditorDialog` in `app/src/main/java/com/fractanomics/crosstraining/ui/screens/CyclesScreen.kt` when adding a movement goal, expanding a collapsed goal, or surfacing an inline target error

**Checkpoint**: User Story 1 is fully functional and testable independently

---

## Phase 4: User Story 2 - Distinct Action Hit Targets & Semantics on Collapsed Goal Cards (Priority: P1)

**Goal**: Provide clear touch target spacing (`4.dp`) and non-swallowing accessibility semantics for `Remove goal` (`🗑`) and `Expand goal` (`▾`) on `CollapsedGoalSummaryCard`.

**Independent Test**: Verify `CollapsedGoalSummaryCard` separates trailing action buttons with `Arrangement.spacedBy(4.dp)` and preserves independent clickable semantics for `Remove goal` and `Expand goal`.

### Implementation for User Story 2

- [X] T008 [US2] Update `CollapsedGoalSummaryCard` in `app/src/main/java/com/fractanomics/crosstraining/ui/screens/CyclesScreen.kt` to add `Arrangement.spacedBy(4.dp)` between `Remove goal` and `Expand goal` `IconButton`s and avoid swallowing child button semantics via parent `mergeDescendants`

**Checkpoint**: User Stories 1 AND 2 work independently and together

---

## Phase 5: User Story 3 - Auto-Scroll When Appending Blocks in Scrollable Workout/Routine Modals (Priority: P2)

**Goal**: Automatically scroll `RoutineEditorDialog` when the user taps `Add Block to Routine` so newly appended routine blocks below the fold are brought into view immediately.

**Independent Test**: Open `RoutineEditorDialog`, tap `Add Block to Routine`, and verify the dialog scrolls down to reveal the newly added `Block N` card.

### Implementation for User Story 3

- [X] T009 [P] [US3] Update `RoutineEditorDialog` in `app/src/main/java/com/fractanomics/crosstraining/ui/screens/LibraryScreen.kt` to track block layout bounds / scroll requests and automatically scroll `scrollState` to the newly appended routine block when `Add Block to Routine` is tapped

**Checkpoint**: All user stories are independently functional

---

## Phase 6: Polish & Cross-Cutting Concerns

**Purpose**: Run full verification suite (`testDebugUnitTest`, `Invoke-ScriptTests.ps1`, `lintDebug`) and validate end-to-end behavior

- [X] T010 Run `.\gradlew.bat testDebugUnitTest --no-daemon`, `.\scripts\tests\Invoke-ScriptTests.ps1`, and `.\gradlew.bat lintDebug --no-daemon` to verify 100% test and lint parity across `app/src/main/java/com/fractanomics/crosstraining/ui/screens/CyclesScreen.kt` and `app/src/main/java/com/fractanomics/crosstraining/ui/screens/LibraryScreen.kt`

---

## Dependencies & Execution Order

### Phase Dependencies

- **Setup (Phase 1)**: No dependencies - starts immediately
- **Foundational (Phase 2)**: Depends on Phase 1 - BLOCKS User Stories 1–3
- **User Story 1 (Phase 3)**: Depends on Phase 2 (`T002`, `T003`)
- **User Story 2 (Phase 4)**: Depends on Phase 3 (`CyclesScreen.kt`)
- **User Story 3 (Phase 5)**: Depends on Phase 2 (`resolveGoalCardScrollOffset` in `CyclesScreen.kt`), modifies `LibraryScreen.kt` (`[P]`)
- **Polish (Phase 6)**: Depends on Phases 3, 4, and 5

### Parallel Opportunities

- `T009 [P] [US3]` (`LibraryScreen.kt`) can be developed independently of `T008 [US2]` (`CollapsedGoalSummaryCard` in `CyclesScreen.kt`).

---

## Parallel Example: User Story 1 & User Story 3

```bash
# After Foundational tasks T002 and T003 complete:
Task: "T006/T007 [US1] Update expandGoalDraftAt and wire auto-scroll in CyclesScreen.kt"
Task: "T009 [P] [US3] Wire auto-scroll on Add Block to Routine in LibraryScreen.kt"
```

---

## Implementation Strategy

### MVP First (User Story 1 Only)

1. Complete Phase 1 & Phase 2 (`T001`–`T003`)
2. Complete Phase 3 (`T004`–`T007`)
3. Validate unit tests in `CyclesScreenTest.kt`

### Incremental Delivery

1. Deliver Foundational helpers (`T002`, `T003`)
2. Deliver User Story 1 (`T004`–`T007`) & User Story 2 (`T008`) in `CyclesScreen.kt` / `CyclesScreenTest.kt`
3. Deliver User Story 3 (`T009`) in `LibraryScreen.kt`
4. Run full verification gate (`T010`)
