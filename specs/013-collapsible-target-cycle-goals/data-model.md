# Data Model: Collapsible & Target-Only Movement Goal Cards (`013-collapsible-target-cycle-goals`)

## 1. `GoalDraftState` (`com.fractanomics.crosstraining.ui.screens.GoalDraftState`)

UI state model representing a single movement goal card inside `CycleEditorDialog`.

### Properties

| Property | Type | Default | Description |
|----------|------|---------|-------------|
| `id` | `Long` | `0L` | Database primary key (`0L` for newly added unsaved goals). |
| `exercise` | `Exercise?` | `null` | Selected movement from the exercise catalog. |
| `reps` | `String` | `"1"` | Rep Max count string (`"1"`, `"3"`, `"5"`, `"10"`, etc.) when `goalMetric == MetricType.WEIGHT`. |
| `startWeight` | `String` | `""` | Legacy/optional start value string (`""` for new target-only goals; preserved when loading existing legacy goals that had `startWeight > 0.0` until movement/metric/RM is changed). |
| `targetWeight` | `String` | `""` | Required target value string in the active metric's unit (`kg`, `cal`, or `reps`). |
| `selectedModifier` | `Double?` | `null` | Last applied quick-stepper delta or scale modifier. |
| `isManuallyEdited` | `Boolean` | `false` | Whether the user has manually edited the target value. |
| `goalMetric` | `MetricType` | `CycleGoal.defaultGoalMetricFor(exercise)` | Active goal metric (`WEIGHT`, `CALORIES`, or `REPS`). |
| `timeWindowMinutes` | `Int?` | `null` | Time window in minutes (`1`, `3`, `5`, or `null` for `Open`) when `goalMetric != WEIGHT`. |
| `notes` | `String` | `""` | Clean user notes (without metadata prefix). |
| `isCollapsed` | `Boolean` | `false` | `true` when rendered in the compact **Collapsed Summary State**; `false` when rendered in the **Expanded Edit State**. |
| `showTargetError` | `Boolean` | `false` | `true` when the user attempted to collapse the card (via `Done ▲`, `+ Movement`, or accordion switch) while `targetWeight` was empty or `<= 0`. |

### Derived Properties & State Transitions

- **`hasValidTarget: Boolean`**: `targetWeight.replace(',', '.').toDoubleOrNull()?.let { it > 0.0 } == true`
- **`unitLabel: String`**: `CycleGoal.unitLabelFor(goalMetric, exercise?.unit)` (`"kg"`, `"cal"`, or `"reps"`)
- **`contextBadgeText: String`**:
  - When `goalMetric == MetricType.WEIGHT`: `"${(reps.toIntOrNull() ?: 1).coerceAtLeast(1)}RM"` (e.g., `"3RM"`)
  - When `goalMetric == MetricType.CALORIES` or `MetricType.REPS`: `CycleGoal.formatTimeWindowLabel(timeWindowMinutes)` (`"1 min"`, `"3 min"`, `"5 min"`, or `"Open"`)
- **`targetBadgeText: String`**: `"Target: ${targetWeight.ifBlank { "0" }} $unitLabel"` (e.g., `"Target: 65 kg"`, `"Target: 45 cal"`, `"Target: 15 reps"`)
- **`formatCollapsedSummaryTitle(index: Int): String`**: `"Goal ${index + 1}: ${exercise?.name ?: "Select movement…"}"`
- **`collapsedAccessibilityDescription(index: Int): String`**:
  `"Goal ${index + 1}: ${exercise?.name ?: "Select movement…"}, $contextBadgeText, Target ${targetWeight.ifBlank { "0" }} $unitLabel. Collapsed. Double-tap to edit goal."`
- **`targetStepperOptions: List<Pair<Double, String>>`**:
  - `MetricType.WEIGHT`: `listOf(-2.5 to "-2.5 $unitLabel", 2.5 to "+2.5 $unitLabel", 5.0 to "+5 $unitLabel")`
  - `MetricType.CALORIES`: `listOf(-5.0 to "-5 cal", 5.0 to "+5 cal", 10.0 to "+10 cal")`
  - `MetricType.REPS`: `listOf(-1.0 to "-1 rep", 1.0 to "+1 rep", 5.0 to "+5 reps")`
- **`stepTarget(delta: Double): GoalDraftState`**:
  - Parses current `targetWeight` (`0.0` if blank or invalid).
  - Computes `next = max(0.0, BigDecimal(current) + BigDecimal(delta))` rounded `HALF_UP` to 2 decimal places and formatted with `.trimmed()`.
  - Clears `showTargetError` when `next > 0.0`.
- **`tryCollapse(): GoalDraftState`**:
  - If `hasValidTarget`: returns `copy(isCollapsed = true, showTargetError = false)`.
  - If `!hasValidTarget`: returns `copy(isCollapsed = false, showTargetError = true)`.
- **`expand(): GoalDraftState`**:
  - Returns `copy(isCollapsed = false, showTargetError = false)`.

## 2. Accordion List Transitions (`CyclesScreen.kt`)

Pure helper functions operating on `List<GoalDraftState>` to enforce the exclusive single-expand accordion contract:

1. **`collapseGoalDraftAt(drafts: List<GoalDraftState>, index: Int): List<GoalDraftState>`**:
   - Invokes `drafts[index].tryCollapse()` when the user taps `Done ▲` or presses IME `Done` on `Target`.
2. **`expandGoalDraftAt(drafts: List<GoalDraftState>, targetIndex: Int): List<GoalDraftState>`**:
   - If another card `openIndex != targetIndex` is currently expanded (`!isCollapsed`):
     - If `!drafts[openIndex].hasValidTarget`, marks `drafts[openIndex]` with `showTargetError = true` and keeps `openIndex` expanded (blocks switching away from an invalid open card).
     - Otherwise, collapses all other cards (`isCollapsed = true, showTargetError = false`) and expands `targetIndex` (`isCollapsed = false, showTargetError = false`).
3. **`appendMovementGoalDraft(drafts: List<GoalDraftState>, newDraft: GoalDraftState): Pair<List<GoalDraftState>, Boolean>`**:
   - Checks if any currently expanded card has `!hasValidTarget`:
     - If so, sets `showTargetError = true` on the invalid expanded card and returns `updatedDrafts to false` (blocks adding a new card until the open card has a valid target).
     - Otherwise, collapses all existing cards and appends `newDraft.copy(isCollapsed = false, showTargetError = false)`, returning `updatedDrafts to true`.

## 3. `CycleGoal` (`com.fractanomics.crosstraining.data.model.CycleGoal`)

No Room schema changes required (`schemaVersion` unchanged).
- `CycleGoal.startWeight` defaults to `0.0` for newly created target-only goals.
- `CycleGoal.formatSummaryChip(exerciseName, fallbackWeightUnit)` already formats `startWeight == 0.0` as `"$exerciseName (${targetReps}RM): ${targetWeight.trimmed()}$unit"` (Weight) or `"$exerciseName ($badge): ${targetWeight.trimmed()} $unit"` (Calories/Reps).
- `evaluateCycleGoalProgress` in `ProgressScreen.kt` automatically derives `baselineStartWeight` from historical `RepMax` / `SessionWithBlocks` when `goal.startWeight == 0.0`.
