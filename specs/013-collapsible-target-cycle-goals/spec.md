# Feature Specification: Collapsible & Target-Only Movement Goal Cards

**Feature Branch**: `feat/013-collapsible-target-cycle-goals`

**Created**: 2026-11-02

**Status**: Approved

**Input**: User description: "I want to implement the requirement discussed here: https://share.gemini.google/Fce4PSB85ZVo. (SPEC-CYCLE-GOAL-04: Cycle Goal Card Collapse Redesign — Remove the Start field when adding or editing a cycle movement goal so only the Target value is required with direct additive/subtractive quick-stepper pills, and replace the permanently expanded edit form with a Collapsed Summary View once a goal is saved/configured in the cycle.)"

## Clarifications

### Session 2026-11-02

- Q: Now that `Start` is removed from the movement goal editor card, what should appear below the `Target` box? → A: Option 1A: Quick adjustment stepper pills (`-2.5 kg` | `+2.5 kg` | `+5 kg` for Weight; `-5 cal` | `+5 cal` | `+10 cal` for Calories; `-1 rep` | `+1 rep` | `+5 reps` for Reps) that step the `Target` value up or down with one tap (clamped at a minimum of `0`).
- Q: How should an expanded goal card collapse into the summary view, and what should the collapsed summary display? → A: Option 2: Support collapsing via a `Done ▲` button in the expanded card header, auto-collapsing when the user taps `+ Movement` to add the next goal, and auto-collapsing when expanding another collapsed card (exclusive accordion). The collapsed view displays the movement name, context badge (`3RM` / time window), and target badge (`65 kg` / `45 cal` / `15 reps`) alongside expand (`▾`) and delete (`🗑`) actions.

## User Scenarios & Testing *(mandatory)*

### User Story 1 - Target-Only Movement Goal Configuration with Quick Stepper Pills (Priority: P1)

When adding or editing a Basic Movement Goal inside the New/Edit Cycle modal, a coach or athlete wants to configure a goal using a single full-width `Target (<unit>)` input field (without a `Start` input field or `+ →` connector) and one-tap additive/subtractive stepper pills (`-2.5 kg` / `+2.5 kg` / `+5 kg` for Weight, `-5 cal` / `+5 cal` / `+10 cal` for Calories, `-1 rep` / `+1 rep` / `+5 reps` for Reps) while keeping the `Goal Metric:` selector pills (`Weight (kg)`, `Calories (cal)`, `Reps`) on a single non-wrapping horizontal row.

**Why this priority**: Directly simplifies the movement goal creation form by eliminating redundant `Start` input entry and replacing start-dependent percentage chips with direct target increment/decrement steppers.

**Independent Test**: Can be fully tested by opening the New/Edit Cycle modal, adding a movement goal, verifying that no `Start` field or `+ →` connector is rendered, verifying that `Goal Metric:` renders on a single horizontal row, verifying that `Target` auto-populates from historical best RM / working set when selecting a movement or RM, and tapping the negative and positive quick-stepper pills to step `Target` up and down with a floor of `0`.

**Acceptance Scenarios**:

1. **Given** an expanded movement goal card in the New or Edit Cycle modal, **When** rendered for any `Goal Metric` (`Weight`, `Calories`, or `Reps`), **Then** the `Start` input field and `+ →` connector are absent, a single full-width `Target (<unit>)` input field (`Target (kg)`, `Target (cal)`, or `Target (reps)`) is displayed, and the `Goal Metric:` pills (`Weight (kg)`, `Calories (cal)`, `Reps`) fit on a single horizontal row without wrapping.
2. **Given** an expanded movement goal card with `Weight (kg)` active and `Target (kg)` set to `100` (or auto-filled from historical best RM), **When** the user taps `+2.5 kg`, `+5 kg`, or `-2.5 kg`, **Then** `Target (kg)` updates in-place by adding or subtracting that exact step (`100` → `102.5` → `107.5` → `105`), and if `Target` is empty or `0`, tapping a positive pill initializes `Target` to the step value while tapping a negative pill clamps at `0` (never negative).
3. **Given** an expanded movement goal card in `Calories (cal)` or `Reps` mode, **When** the user views and taps the quick adjustment pills below `Target`, **Then** `Calories (cal)` offers `-5 cal`, `+5 cal`, `+10 cal` and `Reps` offers `-1 rep`, `+1 rep`, `+5 reps`, stepping `Target` additively/subtractively with a minimum floor of `0`.

---

### User Story 2 - Collapsible Summary View & Exclusive Accordion Workflow (Priority: P1)

When viewing or managing multiple movement goals inside the New/Edit Cycle modal, a coach or athlete wants saved/configured goals to display in a compact Collapsed Summary View showing the movement name, context badge (`3RM` or time window), and target badge (`65 kg`, `45 cal`, `15 reps`), with seamless accordion transitions when tapping `Done ▲`, tapping `+ Movement`, or tapping another collapsed card.

**Why this priority**: Prevents vertical scroll overload in the cycle modal when configuring multiple movement goals and provides an immediate at-a-glance summary of all cycle targets.

**Independent Test**: Can be fully tested by opening Edit Cycle on a cycle with saved goals (verifying all load collapsed by default), tapping a collapsed card to expand it, tapping `Done ▲` to collapse it back into summary view, and tapping `+ Movement` to verify the open valid card auto-collapses while the newly added goal appends in the expanded state.

**Acceptance Scenarios**:

1. **Given** a cycle with existing saved movement goals is opened in the `Edit cycle` modal, **When** the modal loads, **Then** all existing valid movement goals render in the **Collapsed Summary State** by default, displaying `Goal <N>: <Movement Name>` (single-line ellipsis if long), the context badge (`<N>RM` for Weight, or `<N> min` / `Open` for Calories/Reps), the target badge (`Target: <value> <unit>`), the expand chevron (`▾`), and the delete icon (`🗑`).
2. **Given** an expanded goal card with a valid positive `Target > 0`, **When** the user taps the card header `Done ▲` button (or presses IME `Done` on the `Target` input), **Then** the card collapses into its compact summary card.
3. **Given** an expanded goal card with a valid positive `Target > 0`, **When** the user taps `+ Movement` (or taps another collapsed goal card), **Then** the currently expanded goal card automatically collapses into its summary state and the newly added goal card (or tapped card) opens as the sole expanded card (exclusive single-expand accordion).
4. **Given** an expanded goal card where `Target` is empty or `0`, **When** the user attempts to collapse it via `Done ▲`, `+ Movement`, or tapping another card, **Then** the card remains expanded, marks the `Target` field in an error state, and displays the inline helper message `"Please enter a target value greater than 0"`.

---

### User Story 3 - Backward-Compatible Persistence, Summary Chips & Progress Tracking (Priority: P2)

When saving a cycle with target-only movement goals and viewing it on the Cycles list screen or in `Progress & Goals` → `Cycle goals`, the user wants target-only goals to persist cleanly, display concise summary chips on the cycle card, and automatically evaluate progress against prior/initial logged records when no explicit `startWeight` was stored.

**Why this priority**: Ensures 100% backward compatibility with existing database tables, CSV backups, cloud sync, and `Progress & Goals` analytics.

**Independent Test**: Can be tested by saving a cycle with target-only `Weight`, `Calories`, and `Reps` goals (`startWeight = 0.0`), verifying `CycleGoal.formatSummaryChip` outputs concise target chips (`Front Squat (3RM): 65kg`), and verifying `evaluateCycleGoalProgress` derives the baseline start value from historical RM or logged working sets while preserving any legacy `startWeight > 0.0` on older goals.

**Acceptance Scenarios**:

1. **Given** a newly created target-only movement goal saved in a cycle, **When** converted to a `CycleGoal` entity, **Then** `targetWeight` stores the positive target value, `startWeight` defaults to `0.0` (or preserves legacy `startWeight` if an existing legacy goal was not re-targeted from scratch), and `CycleCard` displays the formatted summary chip without a redundant `0 →` prefix.
2. **Given** a target-only `CycleGoal` (`startWeight = 0.0`) evaluated in `Progress & Goals` → `Cycle goals`, **When** historical RM or session working sets exist on or before the cycle start (or first session in cycle), **Then** `evaluateCycleGoalProgress` automatically derives `baselineStartWeight` from those records and computes `currentBest` and `progressPercent` accurately.

---

### Edge Cases

- **Attempting to collapse or add another movement when `Target` is empty or `<= 0`**: The currently expanded card blocks the collapse transition, highlights the `Target` input with an error state, and shows `"Please enter a target value greater than 0"`. Typing a valid positive number or tapping a positive quick-stepper pill immediately clears the error state.
- **Stepping down with negative quick-stepper pills (`-2.5 kg`, `-5 cal`, `-1 rep`) when `Target` is empty, `0`, or smaller than the step size**: The resulting `Target` value is clamped at `0` (`max(0.0, currentTarget - step)`) and never becomes negative.
- **Deleting a goal card while collapsed or expanded**: Tapping the trash icon (`🗑`) on either a collapsed or expanded goal card removes that goal and re-indexes remaining goals (`Goal 2` → `Goal 1`). If all goals are deleted, an empty-state placeholder message is shown (`"No movement goals in this cycle yet. Tap + Movement to add a target."`).
- **Long movement names on narrow (`360dp`) screens**: In the collapsed summary card, the movement title truncates cleanly with a single-line ellipsis (`maxLines = 1`) and summary badges wrap cleanly if needed so action icons (`▾`, `🗑`) are never clipped or pushed off-screen.
- **Saving the modal while a card is still expanded**: Tapping the bottom modal `Save` CTA validates and includes any expanded card that has a valid `Target > 0` without requiring the user to manually tap `Done ▲` first.

## Requirements *(mandatory)*

### Functional Requirements

- **FR-001**: The New/Edit Cycle modal MUST support two visual states for each movement goal card (`Collapsed Summary State` and `Expanded Edit State`) governed by an exclusive single-expand accordion model where at most one goal card is expanded at a time.
- **FR-002**: When opening `Edit cycle` for a cycle with existing saved goals, all valid saved goals MUST initialize in the `Collapsed Summary State` by default.
- **FR-003**: A goal card in the `Collapsed Summary State` MUST display:
  - The goal index and movement name (`Goal <N>: <Movement Name>`, truncated to 1 line with ellipsis if overflowing).
  - A context badge (`<N>RM` for `Weight` goals; `<N> min` or `Open` for `Calories` and `Reps` goals).
  - A target badge (`Target: <value> <unit>`).
  - Trailing expand chevron (`▾`) and delete (`🗑`) actions, where tapping anywhere on the card (except delete) expands that card and collapses any other valid open card.
- **FR-004**: A goal card in the `Expanded Edit State` MUST display:
  - Header with `Goal <N>` on the left and a `Done ▲` collapse action plus delete (`🗑`) icon on the right.
  - Searchable `Movement (Basic)` selector.
  - Single-row `Goal Metric:` selector (`Weight (<unit>)`, `Calories (cal)`, `Reps`) that never wraps onto a second line on `360dp` viewports.
  - Contextual `Reps (RM)` stepper + presets (`1`, `3`, `5`, `10`) when `Weight` is active, or `Time Window` pills (`1 min`, `3 min`, `5 min`, `Open`) when `Calories` or `Reps` is active.
  - A single full-width outlined `Target (<unit>)` input field (`Target (kg)`, `Target (cal)`, `Target (reps)`), with the legacy `Start (<unit>)` input field and `+ →` connector removed.
  - A single horizontal row of three quick target adjustment stepper pills directly below `Target`.
- **FR-005**: The quick target adjustment stepper pills MUST adapt to the active `Goal Metric` and step the current `Target` value additively/subtractively in-place with a minimum floor of `0`:
  - `Weight`: `-2.5 <unit>`, `+2.5 <unit>`, `+5 <unit>`
  - `Calories`: `-5 cal`, `+5 cal`, `+10 cal`
  - `Reps`: `-1 rep`, `+1 rep`, `+5 reps`
- **FR-006**: Selecting a movement or changing `Goal Metric` or `Reps (RM)` on an expanded card MUST auto-populate `Target` with the movement's historical best RM / working set value (if available) or clear `Target` to `""` when no prior record exists.
- **FR-007**: Tapping `Done ▲` on an expanded card, pressing IME `Done` on the `Target` field, tapping `+ Movement`, or tapping another collapsed card MUST validate that the currently expanded card has `Target > 0`; if valid, the card collapses into `Collapsed Summary State`, and if `Target` is empty or `<= 0`, the card MUST remain expanded and display an inline error state (`"Please enter a target value greater than 0"`).
- **FR-008**: Collapsed movement goal cards MUST expose merged accessibility semantics (`contentDescription`) announcing the goal index, movement name, context badge, target value/unit, and collapsed state for screen readers (`TalkBack`).
- **FR-009**: Persisting a `CycleGoal` from `GoalDraftState` MUST remain 100% backward compatible with Room persistence, CSV export/import, `CycleCard` summary chips, and `Progress & Goals` (`Cycle goals`, `By exercise`, `By routine`).

### Key Entities

- **GoalDraftState**: UI state model for a movement goal card in the New/Edit Cycle modal, tracking `id`, `exercise`, `goalMetric`, `reps`, `timeWindowMinutes`, `targetWeight`, `isCollapsed`, `showTargetError`, and `notes`.
- **CycleGoal**: Persisted Room entity representing a cycle movement target (`exerciseId`, `targetReps`, `startWeight`, `targetWeight`, `notes` with encoded metric/time-window metadata).

## Success Criteria *(mandatory)*

### Measurable Outcomes

- **SC-001**: Users can scan up to 5 saved movement goals simultaneously within a single mobile viewport in the `Edit cycle` modal without scrolling, thanks to the compact collapsed summary cards.
- **SC-002**: Users can add or adjust a movement goal target in 2 taps or fewer using historical auto-fill and the one-tap quick target stepper pills (`-2.5/+2.5/+5`, `-5/+5/+10`, `-1/+1/+5`).
- **SC-003**: 100% of unit, lint, and script regression tests pass with zero regressions across `CyclesScreen` and `ProgressScreen`.

## Assumptions

- When a new goal is created without an explicit start field, `CycleGoal.startWeight` is persisted as `0.0` (and legacy goals with `startWeight > 0.0` retain their stored `startWeight` unless their movement/metric/RM is changed), allowing `evaluateCycleGoalProgress` in `ProgressScreen` to automatically derive the baseline start value from historical RM or logged session sets.
- The button in the `Basic Movement Goals` section header is labeled `+ Movement` (matching `SPEC-CYCLE-GOAL-04`).
