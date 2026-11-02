# Research: Collapsible & Target-Only Movement Goal Cards (`013-collapsible-target-cycle-goals`)

## 1. Removing the `Start` Input Field While Preserving Historical Auto-Fill & Progress Tracking

- **Decision**:
  - Remove the `Start (<unit>)` `OutlinedTextField` and `+ →` connector from the `CycleEditorDialog` movement goal card UI, leaving a single full-width `Target (<unit>)` `OutlinedTextField`.
  - When a user selects a movement (`onExerciseChanged`), switches `Goal Metric` (`onGoalMetricChanged`), or steps/selects `Reps (RM)` (`onRepsChanged` / `stepReps`), auto-populate `targetWeight` with the queried historical best RM or best working set value (if `> 0.0`) or `""` when no record exists, and set `startWeight = ""` on new/re-queried target-only drafts so `toCycleGoal()` persists `startWeight = 0.0` (while preserving `startWeight` on untouched legacy goals loaded via `fromCycleGoal`).
  - Keep existing `onStartWeightChanged`, `applyModifier`, and `resetModifier` helpers on `GoalDraftState` (alongside the new `stepTarget(delta: Double)` and `targetStepperOptions`) or adapt them cleanly so existing unit tests and callers remain backward compatible.
- **Rationale**:
  - `CycleGoal.formatSummaryChip` already formats `startWeight == 0.0` concisely as `"$exerciseName (${targetReps}RM): ${targetWeight.trimmed()}$unit"` (and `"$exerciseName ($badge): ${targetWeight.trimmed()} $unit"` for Calories/Reps).
  - `evaluateCycleGoalProgress` in `ProgressScreen.kt` already handles `goal.startWeight == 0.0` by automatically deriving `baselineStartWeight` from `priorRm ?: firstLoggedWeightInCycle ?: 0.0` (for Weight) or `priorBest ?: firstInCycle ?: 0.0` (for Calories/Reps).
  - Auto-filling `targetWeight` from the user's historical best RM/set allows the user to immediately tap `+2.5 kg` or `+5 kg` (or `+5 cal` / `+1 rep`) to set a progressive target in one tap.
- **Alternatives Considered**:
  - Storing historical best into `CycleGoal.startWeight` even though `Start` is hidden: Rejected for newly created target-only goals because the stakeholder explicitly stated `Start` is unnecessary and only `Target` should be configured (`Front Squat 3RM 65 Kilos`), while `ProgressScreen` already derives baseline start dynamically from `RepMax` / `SessionWithBlocks` when `startWeight == 0.0`.

## 2. Quick Target Stepper Pills (`1A`: Direct Additive/Subtractive Increments)

- **Decision**:
  - Introduce `targetStepperOptions: List<Pair<Double, String>>` and `stepTarget(delta: Double): GoalDraftState` on `GoalDraftState`:
    - `MetricType.WEIGHT`: `listOf(-2.5 to "-2.5 $unitLabel", 2.5 to "+2.5 $unitLabel", 5.0 to "+5 $unitLabel")`
    - `MetricType.CALORIES`: `listOf(-5.0 to "-5 cal", 5.0 to "+5 cal", 10.0 to "+10 cal")`
    - `MetricType.REPS`: `listOf(-1.0 to "-1 rep", 1.0 to "+1 rep", 5.0 to "+5 reps")`
  - `stepTarget(delta)` parses the current `targetWeight` (`toDoubleOrNull()?.coerceAtLeast(0.0) ?: 0.0`), computes `(current + delta).coerceAtLeast(0.0)` using `BigDecimal` `HALF_UP` arithmetic to avoid floating-point drift, formats via `.trimmed().replace(',', '.')`, sets `selectedModifier = delta`, `isManuallyEdited = true`, and clears `showTargetError = false` whenever the resulting target is `> 0.0`.
- **Rationale**:
  - Directly implements Option `1A` from `SPEC-CYCLE-GOAL-04` with deterministic `BigDecimal` precision and a strict `>= 0` floor guardrail.
- **Alternatives Considered**:
  - Percentage chips (`+2.5%`, `+5%`, `+10%`): Rejected because without a visible `Start` input box, percentage scaling has no baseline when no historical PR exists.

## 3. Exclusive Accordion & Collapsible Summary Card State Machine (`Option 2`)

- **Decision**:
  - Add `isCollapsed: Boolean = false` and `showTargetError: Boolean = false` to `GoalDraftState`, plus pure state-transition helpers:
    - `hasValidTarget: Boolean` (`targetWeight.replace(',', '.').toDoubleOrNull()?.let { it > 0.0 } == true`)
    - `tryCollapse(): GoalDraftState` — if `hasValidTarget`, returns `copy(isCollapsed = true, showTargetError = false)`; otherwise returns `copy(isCollapsed = false, showTargetError = true)`.
    - `expand(): GoalDraftState` — returns `copy(isCollapsed = false, showTargetError = false)`.
    - `formatCollapsedSummaryTitle(index: Int): String` — `"Goal ${index + 1}: ${exercise?.name ?: "Select movement…"}"`.
    - `contextBadgeText: String` — `"${(reps.toIntOrNull() ?: 1).coerceAtLeast(1)}RM"` for `MetricType.WEIGHT`, or `CycleGoal.formatTimeWindowLabel(timeWindowMinutes)` (`"1 min"`, `"3 min"`, `"5 min"`, `"Open"`) for `MetricType.CALORIES` / `MetricType.REPS`.
    - `targetBadgeText: String` — `"Target: ${targetWeight.ifBlank { "0" }} $unitLabel"`.
    - `collapsedAccessibilityDescription(index: Int): String` — `"Goal ${index + 1}: ${exercise?.name ?: "Movement"}, $contextBadgeText, Target ${targetWeight.ifBlank { "0" }} $unitLabel. Collapsed. Double-tap to edit goal."`
  - Add pure list-level accordion transition functions (`collapseOrFlagActiveGoal`, `expandGoalAt`, `addNewMovementGoal`) so the exclusive single-expand accordion behavior in `CycleEditorDialog` is 100% unit-testable:
    - `fromCycleGoal(goal, exercise)` initializes `isCollapsed = goal.targetWeight > 0.0` so all existing saved goals open in the **Collapsed Summary State** by default when opening `Edit cycle`.
    - Tapping `Done ▲` on an expanded card calls `tryCollapse()` on that card.
    - Tapping `+ Movement` validates any currently expanded card first: if an expanded card has `!hasValidTarget`, it sets `showTargetError = true` on that card and blocks appending a new card; otherwise it collapses all existing cards and appends the new `GoalDraftState(isCollapsed = false)`.
    - Tapping a collapsed card at `targetIndex` validates any currently expanded card: if the currently expanded card has `!hasValidTarget`, it sets `showTargetError = true` on that open card and keeps it expanded; otherwise it collapses the previously open card and expands `targetIndex`.
- **Rationale**:
  - Pure list-level state transitions make every accordion rule in `SPEC-CYCLE-GOAL-04` deterministic and directly verifiable in `CyclesScreenTest`.
- **Alternatives Considered**:
  - Allowing multiple expanded cards simultaneously: Rejected per `SPEC-CYCLE-GOAL-04` (`Collapse Trigger 3: Exclusive Accordion Switch`) to prevent vertical scroll overload on mobile screens.

## 4. Single-Row `Goal Metric:` Layout on `360dp` Viewports

- **Decision**:
  - Replace the wrapping `FlowRow` for `Goal Metric:` with a single horizontal `Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp))` where each of the 3 `FilterChip` items uses `Modifier.weight(1f)` and `maxLines = 1` with `TextOverflow.Ellipsis` so `Weight (kg)`, `Calories (cal)`, and `Reps` always stay on a single line without wrapping.
  - Render the 3 quick target adjustment stepper pills (`-2.5 kg`, `+2.5 kg`, `+5 kg`) in a single horizontal `Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp))` with `Modifier.weight(1f)` on each pill.
- **Rationale**:
  - Eliminates the ~100dp vertical height waste identified in `SPEC-CYCLE-GOAL-04` Conflict 3 while complying with Constitution Principle IV (`360dp` viewport & `fontScale = 1.3f` responsiveness).
