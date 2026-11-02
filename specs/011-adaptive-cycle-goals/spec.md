# Feature Specification: Adaptive Metric Cycle Goals

**Feature Branch**: `feat/011-adaptive-cycle-goals`

**Created**: 2026-11-02

**Status**: Approved

**Input**: User description: "I want to change how the cycle goals are added as per this document: https://share.gemini.google/uJEALAAjBylv. This implies that some other functionalities can be impacted (Progress & Goals, especially By exercise, By routine and Cycle Goals). Let's try to make as backward compatible as possible the other functionalities and put attention in the way to add cycle goals for now."

## Clarifications

### Session 2026-11-02

- Q: What should the goal card display when the `Reps` Goal Metric is selected? → A: Show `Time Window` pills (`1 min`, `3 min`, `5 min`, `Open`) + `Start (reps)` / `Target (reps)` + `Scale:` chips (`+2 reps`, `+5 reps`, `+10 reps`, `0`).
- Q: When the user selects or changes a Movement in the goal card, how should the `Goal Metric` pills behave? → A: Auto-select the default `Goal Metric` based on the exercise (`Weight (kg)` for loaded/barbell lifts, `Calories (cal)` for calorie machines, `Reps` for gymnastics/rep movements), while allowing the user to manually switch to any of the 3 metrics.
- Q: How should `Calories (cal)` and `Reps` cycle goals handle Start value initialization and backward compatibility in `Progress & Goals`? → A: Auto-fill `Start` for `Calories (cal)` and `Reps` from the all-time best logged session working set regardless of time window, format labels with unit & time window, and keep `By exercise` and `By routine` unchanged and backward-compatible.

## User Scenarios & Testing *(mandatory)*

### User Story 1 - Adaptive Weight-Based Movement Goal Creation & Editing (Priority: P1)

When creating or editing a training cycle, a coach or athlete wants to add a Basic Movement Goal for a loaded lift (e.g., Back Squat) using the adaptive goal card layout so they can quickly adjust the target Rep Max (RM) via a stepper or preset pills (`1`, `3`, `5`, `10`), auto-populate starting weight from historical records, and scale the target weight by percentage (`+2.5%`, `+5%`, `+10%`, `0%`).

**Why this priority**: Weight-based RM goals are the foundational cycle goal workflow and must match the new adaptive card design while remaining 100% backward compatible with existing saved goals.

**Independent Test**: Can be fully tested by opening the New/Edit Cycle modal, adding a Movement Goal for "Back Squat", verifying that `Weight (kg)` is automatically selected as the Goal Metric, interacting with the `Reps (RM)` `-` / `+` stepper and `1`, `3`, `5`, `10` quick preset pills, verifying `Start (kg)` and `Target (kg)` auto-fill from the best recorded RM, and tapping `+2.5%`, `+5%`, `+10%` scale chips.

**Acceptance Scenarios**:

1. **Given** the user opens the New or Edit Cycle dialog and taps `+ Add Movement`, **When** a weight-based movement (e.g., "Back Squat") is selected via the searchable `Movement (Basic)` selector (with `Change` dropdown/search), **Then** the `Goal Metric` pill row highlights `Weight (kg)`, displays the contextual `Reps (RM)` stepper row (`-`, `<N> RM`, `+`) and quick preset pills (`1`, `3`, `5`, `10`), and auto-populates `Start (kg)` and `Target (kg)` from the movement's historical best RM for `<N>` reps.
2. **Given** `Weight (kg)` is active on a goal card, **When** the user taps `-` or `+` on the `Reps (RM)` stepper or selects one of the preset pills (`1`, `3`, `5`, `10`), **Then** the active RM updates (clamped to a minimum of `1 RM`), the matching preset pill highlights if `1`, `3`, `5`, or `10`, and `Start (kg)` / `Target (kg)` re-query the historical best RM for that rep count.
3. **Given** `Weight (kg)` is active and `Start (kg)` contains a positive value (e.g., `100`), **When** the user selects `+2.5%`, `+5%`, `+10%`, or `0%` in the `Scale:` row, **Then** `Target (kg)` updates to the non-compounding scaled value rounded to the nearest `0.5` increment (e.g., `105` for `+5%`).

---

### User Story 2 - Adaptive Calorie-Based & Reps-Based Movement Goal Creation (Priority: P1)

When creating or editing a training cycle, a coach or athlete wants to add a Basic Movement Goal for a monostructural conditioning machine (e.g., Air Bike scored in `Calories (cal)`) or a bodyweight/gymnastics movement (e.g., Pull-ups scored in `Reps`), with a contextual `Time Window` selector (`1 min`, `3 min`, `5 min`, `Open`) and adaptive additive `Scale:` options (`+5 cal`, `+10 cal`, `+20 cal` for Calories; `+2 reps`, `+5 reps`, `+10 reps` for Reps).

**Why this priority**: Directly implements the second goal card type shown in the design specification and extends cycle goals beyond barbell 1RMs to conditioning and gymnastics targets.

**Independent Test**: Can be fully tested by adding a goal card in the Edit Cycle modal, selecting "Air Bike" (or manually switching `Goal Metric` to `Calories (cal)` or `Reps`), verifying the contextual `Time Window` pills (`1 min`, `3 min`, `5 min`, `Open`), verifying `Start` auto-fill from the all-time best logged session set for that movement, and applying the additive `Scale:` chips.

**Acceptance Scenarios**:

1. **Given** a goal card in the New/Edit Cycle modal, **When** the user selects a calorie-based movement (e.g., "Air Bike") or taps the `Calories (cal)` pill under `Goal Metric:`, **Then** the card switches to `Calories (cal)` mode, replaces the `Reps (RM)` stepper with a `Time Window` pill row (`1 min`, `3 min`, `5 min`, `Open`, defaulting to `Open`), updates field labels to `Start (cal)` and `Target (cal)`, auto-fills `Start (cal)` and `Target (cal)` from the all-time best logged working set for that movement (if any), and renders `Scale:` chips for `+5 cal`, `+10 cal`, `+20 cal` (plus `0` reset).
2. **Given** a goal card in `Calories (cal)` mode with `Start (cal)` set to `40`, **When** the user taps `+10 cal` in the `Scale:` row, **Then** `Target (cal)` updates to `50` (non-compounding `Start + 10`).
3. **Given** a goal card in the New/Edit Cycle modal, **When** the user selects a reps-based/gymnastics movement (e.g., "Pull-ups") or taps the `Reps` pill under `Goal Metric:`, **Then** the card switches to `Reps` mode, displays the `Time Window` pill row (`1 min`, `3 min`, `5 min`, `Open`, defaulting to `Open`), updates field labels to `Start (reps)` and `Target (reps)`, auto-fills `Start (reps)` and `Target (reps)` from the all-time best logged working set for that movement (if any), and renders `Scale:` chips for `+2 reps`, `+5 reps`, `+10 reps` (plus `0` reset).

---

### User Story 3 - Backward-Compatible Display & Progress Tracking Across Cycles and Progress & Goals (Priority: P2)

When viewing saved cycles on the Cycles screen or inspecting analytics in `Progress & Goals` (`Cycle goals`, `By exercise`, and `By routine`), the user wants all existing weight-based goals to work identically to before while newly added `Calories (cal)` and `Reps` goals display their appropriate unit and time window badge (`1 min`, `3 min`, `5 min`, `Open`) and track progress from logged sessions without breaking any existing charts or views.

**Why this priority**: Preserves full backward compatibility across persistence, cloud sync, backups, and `Progress & Goals` (`By exercise`, `By routine`, `Cycle goals`).

**Independent Test**: Can be tested by saving a cycle containing a `Weight (kg)` goal (`Back Squat 5RM: 100 -> 105 kg`), a `Calories (cal)` goal (`Air Bike 5 min: 40 -> 50 cal`), and a `Reps` goal (`Pull-ups Open: 12 -> 17 reps`), then inspecting the Cycles list card and all tabs of `Progress & Goals`.

**Acceptance Scenarios**:

1. **Given** a cycle with saved `Weight (kg)`, `Calories (cal)`, and `Reps` goals, **When** viewed on the Cycles screen or reopened in the Edit Cycle modal, **Then** each goal preserves its stored metric, RM or time window (`1 min`, `3 min`, `5 min`, `Open`), start value, and target value without overwriting manual edits, and displays formatted summary chips (e.g., `Back Squat (5RM): 100 → 105kg`, `Air Bike (5 min): 40 → 50cal`, `Pull-ups (Open): 12 → 17reps`).
2. **Given** a cycle selected in `Progress & Goals` → `Cycle goals`, **When** rendering goal target tracking cards, **Then** `Weight (kg)` goals evaluate progress using historical/in-cycle RM and matching-rep working sets exactly as before, while `Calories (cal)` and `Reps` goals display their time window and unit (e.g., `Air Bike (5 min Goal)` / `Pull-ups (Open Goal)`) and evaluate current best from logged working sets for that movement.
3. **Given** any existing data and newly created cycle goals, **When** the user navigates to `Progress & Goals` → `By exercise` or `By routine`, **Then** all KPI cards, evolution charts, RM progression series, and block history tables remain 100% backward compatible and unaffected.

---

### Edge Cases

- **Movement has no historical RM or logged session sets**: When a movement is selected (or RM is changed) and no prior record exists, `Start` and `Target` are cleared to empty strings, `Scale:` chips are disabled until a positive `Start` value is entered, and the user can manually type `Start` and `Target`.
- **Manual switching of Goal Metric on the same Movement**: If a user selects "Back Squat" (auto-selecting `Weight (kg)`) and manually taps `Reps` or `Calories (cal)`, the card switches to the chosen metric, resets any incompatible modifier selection, and queries the appropriate best baseline for that metric.
- **Reopening an existing cycle for editing**: Existing saved goals load their stored metric type, target reps / time window, start value, and target value without auto-fill overwriting stored values unless the user explicitly changes the movement, metric, or RM.
- **Legacy CycleGoal records (created before this feature)**: Existing records without an explicit metric type or time window default seamlessly to `Weight (kg)` (`MetricType.WEIGHT`) and `Open` (`timeWindowMinutes = null`), preserving 100% compatibility across upgrades, CSV backups, and cloud sync.
- **Stepper lower bound**: Tapping the `-` button on the `Reps (RM)` stepper when at `1 RM` keeps the value at `1 RM` (never `0` or negative).

## Requirements *(mandatory)*

### Functional Requirements

- **FR-001**: The New/Edit Cycle modal MUST render each item under `Basic Movement Goals` as a distinct card titled `Goal <N>` with a delete action icon and a searchable `Movement (Basic)` selector displaying a search icon, movement name, and `Change` dropdown trigger.
- **FR-002**: Each goal card MUST display a `Goal Metric:` segmented pill row with three selectable options: `Weight (kg)`, `Calories (cal)`, and `Reps`.
- **FR-003**: Selecting a movement in `Movement (Basic)` MUST automatically select the default `Goal Metric` corresponding to that movement (`Weight (kg)` for weight-based movements, `Calories (cal)` for calorie/machine movements, `Reps` for gymnastics/rep-based movements), while allowing the user to manually switch to any of the three metrics at any time.
- **FR-004**: When `Weight (kg)` is active, the goal card MUST display a contextual `Reps (RM)` row featuring a `-` button, `<N> RM` display/input, and `+` button, accompanied by quick preset pills for `1`, `3`, `5`, and `10` RM.
- **FR-005**: When `Calories (cal)` or `Reps` is active, the goal card MUST replace the `Reps (RM)` stepper with a contextual `Time Window` pill row offering `1 min`, `3 min`, `5 min`, and `Open` (defaulting to `Open`).
- **FR-006**: Each goal card MUST display `Start (<unit>)` and `Target (<unit>)` numeric input fields linked visually by a progression indicator (`+ →`), where `<unit>` adapts to the active `Goal Metric` (`kg`, `cal`, or `reps`).
- **FR-007**: Each goal card MUST display an adaptive `Scale:` row whose quick-increment pills depend on the active `Goal Metric`:
  - `Weight (kg)`: `+2.5%`, `+5%`, `+10%` (and `0%` reset), scaling `Start` by percentage rounded half-up to the nearest `0.5`.
  - `Calories (cal)`: `+5 cal`, `+10 cal`, `+20 cal` (and `0 cal` reset), adding the flat calorie increment to `Start`.
  - `Reps`: `+2 reps`, `+5 reps`, `+10 reps` (and `0 reps` reset), adding the flat rep increment to `Start`.
- **FR-008**: Auto-fill of `Start` and `Target` when selecting a movement (or changing RM / metric) MUST query:
  - For `Weight (kg)`: the all-time best recorded Rep Max (`RepMax`) or working set weight for `(exerciseId, targetReps)`.
  - For `Calories (cal)` and `Reps`: the all-time best logged working set value for `exerciseId` across sessions regardless of time window.
- **FR-009**: Persisting a `CycleGoal` MUST store the selected goal metric and time window (when in `Calories` or `Reps` mode) while maintaining full backward compatibility with existing `CycleGoal` database records, cloud synchronization payloads, and CSV backup snapshots.
- **FR-010**: The Cycles list card (`CycleCard`) and `Progress & Goals` → `Cycle goals` view MUST format goal labels and track goal progress according to each goal's stored metric (`<N>RM` with `kg` for Weight goals; `<Time Window>` with `cal` or `reps` for Calorie and Reps goals), while keeping `By exercise` and `By routine` views 100% backward compatible.

### Key Entities

- **CycleGoal**: Represents a movement target within a training cycle, including `id`, `cycleId`, `exerciseId`, `goalMetric` (`WEIGHT`, `CALORIES`, `REPS`, defaulting to `WEIGHT`), `targetReps` (used as RM count when `goalMetric == WEIGHT`), `timeWindowMinutes` (`1`, `3`, `5`, or `null` for `Open` when `goalMetric` is `CALORIES` or `REPS`), `startWeight` (stores the numeric starting value in the metric's unit), `targetWeight` (stores the numeric target value in the metric's unit), and `notes`.
- **GoalDraftState**: UI state model for a single goal card inside the New/Edit Cycle modal, encapsulating the selected `Exercise`, active `goalMetric`, `reps` (RM), `timeWindowMinutes`, `startValue`, `targetValue`, and active `selectedModifier`.

## Success Criteria *(mandatory)*

### Measurable Outcomes

- **SC-001**: Users can configure a weight-based, calorie-based, or reps-based movement goal in under 15 seconds using the adaptive metric pills, RM stepper/presets, time window pills, and scale chips.
- **SC-002**: 100% of existing saved cycles and legacy `CycleGoal` entries load, display, and calculate progress identically before and after the update without data loss or manual migration steps.
- **SC-003**: 100% of unit, migration, backup, and UI verification tests pass with zero regressions in `By exercise`, `By routine`, and `Cycle goals`.

## Assumptions

- Gymnastics exercises seeded in the starter library with `ExerciseCategory.GYMNASTICS` (such as Pull-ups, Push-ups, Air Squat, Toes to Bar, Box Jumps) default their initial `Goal Metric` selection in the Cycle Goal card to `Reps` unless their `metricType` is explicitly `CALORIES` or the user switches the pill to `Weight (kg)`.
- `timeWindowMinutes` uses `null` to represent the `"Open"` time window and `1`, `3`, `5` for the timed intervals (`1 min`, `3 min`, `5 min`).
- Numeric values for `Start` and `Target` continue to use the existing `startWeight` and `targetWeight` columns in `CycleGoal` so all existing queries and external consumers remain backward compatible.
