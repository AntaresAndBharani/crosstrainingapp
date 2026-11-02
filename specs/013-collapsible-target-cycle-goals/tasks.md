# Tasks: Collapsible & Target-Only Movement Goal Cards

**Input**: Design documents from `/specs/013-collapsible-target-cycle-goals/`
**Prerequisites**: plan.md (required), spec.md (required for user stories), research.md, data-model.md, contracts/

**Tests**: Deterministic JUnit 4 unit tests are REQUIRED per Constitution Principle III (`Test-First & 100% CI Parity`).

**Organization**: Tasks are grouped by user story to enable independent implementation and testing of each story.

## Format: `[ID] [P?] [Story] Description`

- **[P]**: Can run in parallel (different files, no dependencies)
- **[Story]**: Which user story this task belongs to (e.g., US1, US2, US3)
- Include exact file paths in descriptions

---

## Phase 1: Setup (Shared Infrastructure)

**Purpose**: Verify baseline branch state and inspect target files (`CyclesScreen.kt`, `CycleGoal.kt`, `CyclesScreenTest.kt`).

- [X] T001 Verify feature branch `feat/013-collapsible-target-cycle-goals` and Spec-Kit artifacts in `specs/013-collapsible-target-cycle-goals/plan.md`

---

## Phase 2: Foundational (Blocking Prerequisites)

**Purpose**: Extend `GoalDraftState` with target-only stepper properties, collapsed/error state properties, and pure accordion list-transition helpers in `app/src/main/java/com/fractanomics/crosstraining/ui/screens/CyclesScreen.kt`.

- [X] T002 Extend `GoalDraftState` in `app/src/main/java/com/fractanomics/crosstraining/ui/screens/CyclesScreen.kt` with `isCollapsed: Boolean = false`, `showTargetError: Boolean = false`, `hasValidTarget: Boolean`, `targetStepperOptions: List<Pair<Double, String>>`, `stepTarget(delta: Double): GoalDraftState` (using `BigDecimal` `HALF_UP` rounding and `0.0` floor clamping), `contextBadgeText: String`, `targetBadgeText: String`, `formatCollapsedSummaryTitle(index: Int): String`, `collapsedAccessibilityDescription(index: Int): String`, `tryCollapse(): GoalDraftState`, and `expand(): GoalDraftState`
- [X] T003 Add pure exclusive-accordion list transition helpers (`collapseGoalDraftAt`, `expandGoalDraftAt`, `appendMovementGoalDraft`) in `app/src/main/java/com/fractanomics/crosstraining/ui/screens/CyclesScreen.kt`

**Checkpoint**: Foundational `GoalDraftState` and accordion state machine helpers ready for User Stories 1, 2, and 3.

---

## Phase 3: User Story 1 - Target-Only Movement Goal Configuration with Quick Stepper Pills (Priority: P1) 🎯 MVP

**Goal**: Replace the `Start (<unit>) + → Target (<unit>)` dual-input row and percentage `Scale:` row with a single full-width `Target (<unit>)` input field (`Target (kg)`, `Target (cal)`, `Target (reps)`), a single-row non-wrapping `Goal Metric:` pill selector, and a single horizontal row of 3 direct additive/subtractive quick-stepper pills (`-2.5/+2.5/+5 kg`, `-5/+5/+10 cal`, `-1/+1/+5 reps`) clamped at `>= 0`.

**Independent Test**: Verify via unit tests in `CyclesScreenTest.kt` and UI rendering in `CyclesScreen.kt` that `GoalDraftState` auto-populates `targetWeight` from historical best RM/working set, exposes metric-specific `targetStepperOptions`, steps `targetWeight` additively/subtractively with `BigDecimal` precision and a `0` floor, and renders a single full-width `Target (<unit>)` input and single-row `Goal Metric:` selector.

### Tests for User Story 1

- [X] T004 [US1] Add unit tests in `app/src/test/java/com/fractanomics/crosstraining/ui/screens/CyclesScreenTest.kt` verifying `targetStepperOptions` and `stepTarget(delta)` for `Weight` (`-2.5`, `+2.5`, `+5`), `Calories` (`-5`, `+5`, `+10`), and `Reps` (`-1`, `+1`, `+5`), including initialization from empty/`0` target and floor clamping at `0`

### Implementation for User Story 1

- [X] T005 [US1] Update the expanded movement goal card in `app/src/main/java/com/fractanomics/crosstraining/ui/screens/CyclesScreen.kt` to render `Goal Metric:` on a single non-wrapping horizontal `Row` (`Modifier.weight(1f)` per `FilterChip`), replace the `Start + → Target` row with a single full-width `OutlinedTextField` (`Target ($unit)` with `isError`, inline error helper text `"Please enter a target value greater than 0"`, and `ImeAction.Done`), and replace the `Scale:` row with the 3 equal-width quick target stepper pills (`draft.targetStepperOptions`) with haptic feedback

**Checkpoint**: User Story 1 is complete and independently testable.

---

## Phase 4: User Story 2 - Collapsible Summary View & Exclusive Accordion Workflow (Priority: P1)

**Goal**: Render saved/collapsed goals in a compact `CollapsedGoalSummaryCard` (`Goal N: <Movement Name>`, context badge, target badge, `🗑`, `▾`) and enforce the exclusive single-expand accordion transitions when opening `Edit cycle`, tapping `Done ▲`, tapping `+ Movement`, or tapping another collapsed card, blocking collapse with an inline error when `Target <= 0`.

**Independent Test**: Verify via unit tests in `CyclesScreenTest.kt` that `fromCycleGoal` loads valid saved goals with `isCollapsed = true`, `collapseGoalDraftAt` collapses valid cards and flags `showTargetError = true` on empty/`0` targets, `expandGoalDraftAt` enforces exclusive single-card expansion while blocking switches from invalid open cards, and `appendMovementGoalDraft` auto-collapses the open valid card and appends a new expanded card.

### Tests for User Story 2

- [X] T006 [US2] Add unit tests in `app/src/test/java/com/fractanomics/crosstraining/ui/screens/CyclesScreenTest.kt` verifying `fromCycleGoal` default collapsed state, `tryCollapse` / `expand`, `collapseGoalDraftAt`, `expandGoalDraftAt` (exclusive accordion), `appendMovementGoalDraft` (`+ Movement` auto-collapse and invalid-target block), and collapsed summary badges/accessibility descriptions

### Implementation for User Story 2

- [X] T007 [US2] Implement `CollapsedGoalSummaryCard` and wire the exclusive accordion header (`Done ▲` collapse button, `+ Movement` button, empty-state placeholder `"No movement goals in this cycle yet. Tap + Movement to add a target."`, and `AnimatedVisibility` / state toggle) in `app/src/main/java/com/fractanomics/crosstraining/ui/screens/CyclesScreen.kt`

**Checkpoint**: User Stories 1 and 2 are complete and independently testable.

---

## Phase 5: User Story 3 - Backward-Compatible Persistence, Summary Chips & Progress Tracking (Priority: P2)

**Goal**: Ensure newly created target-only movement goals persist `startWeight = 0.0` cleanly, format concise summary chips on `CycleCard`, and automatically derive baseline start values in `evaluateCycleGoalProgress` (`ProgressScreen.kt`) while remaining 100% backward compatible with existing `CycleGoal` records.

**Independent Test**: Verify in `CyclesScreenTest.kt` that target-only goals (`startWeight = 0.0`) round-trip through `toCycleGoal` / `fromCycleGoal`, format summary chips without `0 →` prefixes, and evaluate progress accurately in `evaluateCycleGoalProgress` across `Weight`, `Calories`, and `Reps` goals.

### Tests for User Story 3

- [X] T008 [US3] Add unit tests in `app/src/test/java/com/fractanomics/crosstraining/ui/screens/CyclesScreenTest.kt` verifying target-only (`startWeight = 0.0`) persistence, `formatSummaryChip` formatting, and `evaluateCycleGoalProgress` baseline derivation from historical RM and session working sets

### Implementation for User Story 3

- [X] T009 [US3] Verify and refine `GoalDraftState.toCycleGoal` / `fromCycleGoal` in `app/src/main/java/com/fractanomics/crosstraining/ui/screens/CyclesScreen.kt` so newly created target-only goals persist `startWeight = 0.0` (while preserving legacy `startWeight` on untouched existing goals) and integrate cleanly with `CycleCard` and `ProgressScreen`

**Checkpoint**: All user stories (`US1`, `US2`, `US3`) are complete and independently testable.

---

## Phase 6: Polish & Cross-Cutting Concerns

**Purpose**: Full verification suite, APK sync, and changelog maintenance.

- [X] T010 Run `.\gradlew.bat testDebugUnitTest --no-daemon`, `.\scripts\tests\Invoke-ScriptTests.ps1`, and `.\gradlew.bat lintDebug --no-daemon` to verify 100% green tests and zero lint errors
- [X] T011 Update `CHANGELOG.md` under `## [Unreleased]` with the collapsible target-only cycle movement goal card redesign (`SPEC-CYCLE-GOAL-04`)

---

## Dependencies & Execution Order

- **Setup (Phase 1)** → **Foundational (Phase 2)** (`T002`, `T003`) → **User Story 1 (`T004`, `T005`)** → **User Story 2 (`T006`, `T007`)** → **User Story 3 (`T008`, `T009`)** → **Polish (`T010`, `T011`)**.
