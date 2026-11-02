# Phase 1 Data Model: Adaptive Metric Cycle Goals

## 1. `CycleGoal` Entity (`app/src/main/java/com/fractanomics/crosstraining/data/model/CycleGoal.kt`)

### Table & Columns (Unchanged at Room v10)
| Column | Type | Default | Description |
|--------|------|---------|-------------|
| `id` | `Long` | `0` | Auto-generated primary key |
| `cycleId` | `Long` | `0` | Foreign key to `cycles(id)` (`CASCADE`) |
| `exerciseId` | `Long` | `0` | Foreign key to `exercises(id)` (`CASCADE`) |
| `targetReps` | `Int` | `1` | RM rep count when `goalMetric == WEIGHT`; stores `timeWindowMinutes ?: 0` when `CALORIES` or `REPS` |
| `startWeight` | `Double` | `0.0` | Starting numeric value in the active goal metric's unit (`kg`, `cal`, or `reps`) |
| `targetWeight` | `Double` | `0.0` | Target numeric value in the active goal metric's unit (`kg`, `cal`, or `reps`) |
| `notes` | `String` | `""` | User notes, optionally prefixed with `[metric=<CALORIES|REPS>,window=<1|3|5|OPEN>]` when `goalMetric != WEIGHT` |

### Computed Domain Properties (`@get:androidx.room.Ignore`)
- `goalMetric: MetricType`: Parsed from `notes` metadata prefix if present; defaults to `MetricType.WEIGHT`.
- `timeWindowMinutes: Int?`: Parsed from `notes` metadata prefix if present (`1`, `3`, `5`, or `null` for `Open`); defaults to `null`.
- `cleanNotes: String`: `notes` with any leading `[metric=...]` metadata prefix stripped.
- `unitLabel: String`: `"kg"` for `WEIGHT`, `"cal"` for `CALORIES`, `"reps"` for `REPS`.
- `contextBadge: String`: `"${targetReps}RM"` when `goalMetric == MetricType.WEIGHT`; `"${timeWindowMinutes} min"` when `timeWindowMinutes != null`; `"Open"` when `goalMetric != MetricType.WEIGHT && timeWindowMinutes == null`.

---

## 2. `GoalDraftState` UI State (`app/src/main/java/com/fractanomics/crosstraining/ui/screens/CyclesScreen.kt`)

| Field | Type | Default | Description |
|-------|------|---------|-------------|
| `id` | `Long` | `0` | Existing `CycleGoal.id` (`0` for new draft) |
| `exercise` | `Exercise?` | `null` | Selected movement |
| `goalMetric` | `MetricType` | `MetricType.WEIGHT` | Active Goal Metric pill (`WEIGHT`, `CALORIES`, `REPS`) |
| `reps` | `String` | `"1"` | Active RM string when `goalMetric == WEIGHT` |
| `timeWindowMinutes` | `Int?` | `null` | Selected Time Window (`1`, `3`, `5`, or `null` for `Open`) when `goalMetric` is `CALORIES` or `REPS` |
| `startWeight` | `String` | `""` | Text representation of Start value (`kg`, `cal`, or `reps`) |
| `targetWeight` | `String` | `""` | Text representation of Target value (`kg`, `cal`, or `reps`) |
| `selectedModifier` | `Double?` | `null` | Selected Scale modifier (`2.5`, `5.0`, `10.0` for `%`; `5.0`, `10.0`, `20.0` for `cal`; `2.0`, `5.0`, `10.0` for `reps`; `0.0` for reset) |
| `isManuallyEdited` | `Boolean` | `false` | True if loaded from an existing saved goal or manually typed |

### State Transitions
1. `onExerciseChanged(newExercise, bestValue, inferredMetric)`:
   - Updates `exercise = newExercise` and `goalMetric = inferredMetric`.
   - If `bestValue != null && bestValue > 0.0`, sets `startWeight` and `targetWeight` to `bestValue.trimmed()`, `selectedModifier = 0.0`, `isManuallyEdited = false`.
   - Otherwise clears `startWeight` and `targetWeight` to `""` and `selectedModifier = null`.
2. `onGoalMetricChanged(newMetric, bestValue)`:
   - Updates `goalMetric = newMetric`, resets `selectedModifier = if (bestValue != null && bestValue > 0.0) 0.0 else null`, and populates or clears `startWeight` and `targetWeight` from `bestValue`.
3. `onRepsChanged(newReps, bestWeight)` / `stepReps(delta, bestWeight)`:
   - Updates `reps` (clamped `>= 1` for stepper) and populates or clears `startWeight` / `targetWeight` from `bestWeight`.
4. `onTimeWindowChanged(minutes: Int?)`:
   - Updates `timeWindowMinutes = minutes` (`1`, `3`, `5`, or `null` for `Open`) while preserving `startWeight`, `targetWeight`, and `selectedModifier`.
5. `applyModifier(modifier: Double)`:
   - For `MetricType.WEIGHT`: `target = FatLossAnalytics.scaleRepMax(startVal, modifier)`.
   - For `MetricType.CALORIES` / `MetricType.REPS`: `target = startVal + modifier`.
6. `toCycleGoal(cycleId: Long): CycleGoal?`:
   - Returns `null` if `exercise == null` or `targetVal <= 0.0`.
   - Produces a `CycleGoal` with `goalMetric` and `timeWindowMinutes` encoded when `goalMetric != MetricType.WEIGHT`.
