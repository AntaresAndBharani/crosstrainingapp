---

description: "Task list for Adaptive Metric Cycle Goals implementation"
---

# Tasks: Adaptive Metric Cycle Goals

**Input**: Design documents from `/specs/011-adaptive-cycle-goals/`

**Prerequisites**: plan.md (required), spec.md (required for user stories), research.md, data-model.md, contracts/

**Tests**: Unit and regression tests are included in accordance with Constitution Principle III (Test-First & 100% CI Parity).

**Organization**: Tasks are grouped by user story to enable independent implementation and testing of each story.

## Format: `[ID] [P?] [Story] Description`

- **[P]**: Can run in parallel (different files, no dependencies)
- **[Story]**: Which user story this task belongs to (e.g., US1, US2, US3)
- Include exact file paths in descriptions

---

## Phase 1: Setup (Shared Infrastructure)

**Purpose**: Verify baseline project configuration and feature branch state

- [X] T001 Verify feature branch `feat/011-adaptive-cycle-goals` and `.specify/feature.json` pointing to `specs/011-adaptive-cycle-goals`

---

## Phase 2: Foundational (Blocking Prerequisites)

**Purpose**: Core `CycleGoal` backward-compatible domain properties, metadata encoding/decoding, default metric inference, and repository/viewmodel historical best value lookup

**⚠️ CRITICAL**: No user story work can begin until this phase is complete

- [X] T002 Extend `CycleGoal` with backward-compatible `@Ignore` properties (`goalMetric`, `timeWindowMinutes`, `cleanNotes`, `unitLabel`, `contextBadge`), metadata encoding/decoding (`[metric=<CALORIES|REPS>,window=<1|3|5|OPEN>]` when `goalMetric != MetricType.WEIGHT`), and secondary `@Ignore` constructor in `app/src/main/java/com/fractanomics/crosstraining/data/model/CycleGoal.kt`
- [X] T003 Implement `getBestMetricValueForExercise(exerciseId: Long, goalMetric: MetricType): Double?` in `app/src/main/java/com/fractanomics/crosstraining/data/Repository.kt` and expose it in `app/src/main/java/com/fractanomics/crosstraining/ui/AppViewModel.kt`

**Checkpoint**: Foundation ready - user story implementation can now begin

---

## Phase 3: User Story 1 - Adaptive Weight-Based Movement Goal Creation & Editing (Priority: P1) 🎯 MVP

**Goal**: Implement the redesigned goal card for `Weight (kg)` goals with searchable `Movement (Basic)` selector (`Change ▾`), `Goal Metric:` segmented pills (`Weight (kg)`, `Calories (cal)`, `Reps`), `Reps (RM)` `-` / `+` stepper (clamped `>= 1`) with `1`, `3`, `5`, `10` quick preset pills, `Start (kg)` + `→` + `Target (kg)` fields, and `+2.5%`, `+5%`, `+10%`, `0%` scale chips.

**Independent Test**: Open `CycleEditorDialog`, add a goal for "Back Squat", verify `Weight (kg)` pill selection, test `-` / `+` RM stepper and `1`, `3`, `5`, `10` presets, verify RM auto-fill, and verify percentage scaling (`+2.5%`, `+5%`, `+10%`, `0%`) rounded half-up to nearest `0.5`.

### Tests for User Story 1

- [X] T004 [P] [US1] Add unit tests for `GoalDraftState` weight-based RM stepper (`stepReps` clamped `>= 1`), RM preset selection (`1`, `3`, `5`, `10`), and percentage scaling in `app/src/test/java/com/fractanomics/crosstraining/ui/screens/CyclesScreenTest.kt`

### Implementation for User Story 1

- [X] T005 [US1] Update `GoalDraftState` and `CycleEditorDialog` with searchable `Movement (Basic)` selector (`Change ▾`), `Goal Metric:` pill row, `Reps (RM)` `-` / `+` stepper + `1`, `3`, `5`, `10` preset pills, and `Start (kg)` → `Target (kg)` layout in `app/src/main/java/com/fractanomics/crosstraining/ui/screens/CyclesScreen.kt`

**Checkpoint**: User Story 1 is fully functional and testable independently

---

## Phase 4: User Story 2 - Adaptive Calorie-Based & Reps-Based Movement Goal Creation (Priority: P1)

**Goal**: Support `Calories (cal)` and `Reps` goal metrics in `GoalDraftState` and `CycleEditorDialog` with automatic default metric selection per exercise, contextual `Time Window` pills (`1 min`, `3 min`, `5 min`, `Open`), all-time best logged working set auto-fill, and additive `Scale:` chips (`+5 cal`, `+10 cal`, `+20 cal` for Calories; `+2 reps`, `+5 reps`, `+10 reps` for Reps).

**Independent Test**: Select "Air Bike" (or "Pull-ups") in `CycleEditorDialog`, verify `Calories (cal)` (or `Reps`) auto-selection, select a `Time Window` pill (`1 min`, `3 min`, `5 min`, `Open`), verify `Start` auto-fill from best logged session working set, and apply additive scale chips.

### Tests for User Story 2

- [X] T006 [P] [US2] Add unit tests for default `Goal Metric` inference (`WEIGHT`, `CALORIES`, `REPS`), `Time Window` selection (`1`, `3`, `5`, `null`), all-time best logged set auto-fill, and additive `Scale:` modifiers (`+5/+10/+20 cal`, `+2/+5/+10 reps`) in `app/src/test/java/com/fractanomics/crosstraining/ui/screens/CyclesScreenTest.kt`

### Implementation for User Story 2

- [X] T007 [US2] Implement `Calories (cal)` and `Reps` mode transitions, `Time Window` pill row (`1 min`, `3 min`, `5 min`, `Open`), best-metric auto-fill wiring, and adaptive additive `Scale:` chips in `app/src/main/java/com/fractanomics/crosstraining/ui/screens/CyclesScreen.kt`

**Checkpoint**: User Stories 1 and 2 work independently and together

---

## Phase 5: User Story 3 - Backward-Compatible Display & Progress Tracking Across Cycles and Progress & Goals (Priority: P2)

**Goal**: Render formatted labels with unit and RM/time-window badges in `CycleCard` and `CycleGoalsProgress`, compute current best progress for `Weight (kg)`, `Calories (cal)`, and `Reps` goals in `Progress & Goals` → `Cycle goals`, and preserve 100% backward compatibility in `By exercise` and `By routine`.

**Independent Test**: Save a cycle with `Weight (kg)`, `Calories (cal)`, and `Reps` goals, verify `CycleCard` summary chips and reopen behavior in `CyclesScreen`, and verify `CycleGoalsProgress`, `ExerciseProgress`, and `RoutineProgress` in `ProgressScreen`.

### Tests for User Story 3

- [X] T008 [P] [US3] Add unit tests for `CycleGoal` metadata round-trip persistence, `CycleCard` label formatting, and `CycleGoalsProgress` evaluation across `Weight`, `Calories`, and `Reps` goals in `app/src/test/java/com/fractanomics/crosstraining/ui/screens/CyclesScreenTest.kt`

### Implementation for User Story 3

- [X] T009 [US3] Update `CycleCard` goal summary chip rendering and existing-goal reconstitution in `app/src/main/java/com/fractanomics/crosstraining/ui/screens/CyclesScreen.kt`
- [X] T010 [US3] Update `CycleGoalsProgress` to format goal titles/units and evaluate current best progress for `Weight (kg)`, `Calories (cal)`, and `Reps` goals while keeping `By exercise` and `By routine` untouched in `app/src/main/java/com/fractanomics/crosstraining/ui/screens/ProgressScreen.kt`

**Checkpoint**: All user stories are independently functional and backward-compatible

---

## Phase 6: Polish & Cross-Cutting Concerns

**Purpose**: Run full verification suite, update changelog, and prepare PR

- [X] T011 Run full verification suite (`.\gradlew.bat testDebugUnitTest --no-daemon`, `.\scripts\tests\Invoke-ScriptTests.ps1`, `.\gradlew.bat lintDebug assembleDebug --no-daemon`) and sync `local_test\latest.apk`
- [X] T012 Update `CHANGELOG.md` under `## [Unreleased]` with adaptive cycle goals summary

---

## Dependencies & Execution Order

### Phase Dependencies

- **Setup (Phase 1)**: No dependencies - starts immediately
- **Foundational (Phase 2)**: Depends on Setup completion - BLOCKS all user stories
- **User Stories (Phase 3–5)**: Depend on Foundational phase completion (`T002`, `T003`)
- **Polish (Phase 6)**: Depends on completion of User Stories 1–3

### User Story Dependencies

- **User Story 1 (P1)**: Starts after Phase 2 (`T002`, `T003`)
- **User Story 2 (P1)**: Builds on `GoalDraftState` and `CycleEditorDialog` from User Story 1 (`T005`)
- **User Story 3 (P2)**: Uses `CycleGoal` adaptive properties from Phase 2 (`T002`) and integrates with `CyclesScreen.kt` and `ProgressScreen.kt`

### Parallel Opportunities

- `T002` (`CycleGoal.kt`) and `T003` (`Repository.kt` / `AppViewModel.kt`) can be developed in parallel
- `T009` (`CyclesScreen.kt`) and `T010` (`ProgressScreen.kt`) touch separate screen files

---

## Parallel Example: User Story 1 & Foundational

```bash
# Foundational models & repository in parallel:
Task: "T002 Extend CycleGoal in app/src/main/java/com/fractanomics/crosstraining/data/model/CycleGoal.kt"
Task: "T003 Implement getBestMetricValueForExercise in app/src/main/java/com/fractanomics/crosstraining/data/Repository.kt"
```

---

## Implementation Strategy

### MVP First (User Story 1 Only)

1. Complete Phase 1: Setup (`T001`)
2. Complete Phase 2: Foundational (`T002`–`T003`)
3. Complete Phase 3: User Story 1 (`T004`–`T005`)
4. Validate weight-based adaptive card behavior

### Incremental Delivery

1. Complete Setup + Foundational → Foundation ready
2. Add User Story 1 (`Weight (kg)` card + RM stepper/presets)
3. Add User Story 2 (`Calories (cal)` & `Reps` modes + `Time Window` pills + additive `Scale:`)
4. Add User Story 3 (`CycleCard` & `CycleGoalsProgress` backward-compatible display & tracking)
5. Run full verification suite and update `CHANGELOG.md`
