# UI Contract: Adaptive Cycle Goal Card & Progress Views

## 1. Edit / New Cycle Modal (`CycleEditorDialog`)

### Section Header
- Title: `Basic Movement Goals`
- Action Button: `+ Add Movement`

### Goal Card (`Goal <index + 1>`)
1. **Card Header**:
   - Left: `Goal <index + 1>` label.
   - Right: Delete trash icon button (`contentDescription = "Remove goal"`).
2. **Movement Selector (`Movement (Basic)`)**:
   - Outlined field displaying a leading search icon, the movement's name, and a trailing `Change ▾` indicator.
   - Tapping opens a searchable movement picker/dropdown allowing instant filtering by movement name and selecting any exercise.
3. **Goal Metric Selector (`Goal Metric:`)**:
   - Horizontal row of three pill chips:
     - `Weight (kg)` (`MetricType.WEIGHT`)
     - `Calories (cal)` (`MetricType.CALORIES`)
     - `Reps` (`MetricType.REPS`)
4. **Contextual Specification Row**:
   - **When `Weight (kg)` is selected**:
     - Label: `Reps (RM)`
     - Stepper control: `-` button (`Decrease RM`), center value `<reps> RM`, `+` button (`Increase RM`).
     - Quick preset pills beneath the stepper: `1`, `3`, `5`, `10`.
   - **When `Calories (cal)` or `Reps` is selected**:
     - Label: `Time Window`
     - Pill options: `1 min`, `3 min`, `5 min`, `Open` (default `Open`).
5. **Start & Target Fields**:
   - Left field: `Start (<unit>)` (`kg`, `cal`, or `reps`)
   - Center connector: `+ →` progression badge
   - Right field: `Target (<unit>)` (`kg`, `cal`, or `reps`)
6. **Adaptive Scale Row (`Scale:`)**:
   - **When `Weight (kg)`**: `+2.5%`, `+5%`, `+10%`, `0%`
   - **When `Calories (cal)`**: `+5 cal`, `+10 cal`, `+20 cal`, `0 cal`
   - **When `Reps`**: `+2 reps`, `+5 reps`, `+10 reps`, `0 reps`
   - Enabled only when `Start` contains a positive number (`> 0`).

---

## 2. Cycles List Card (`CycleCard`)

- Summary chip format in `Target Goals (<N>):`:
  - `Weight (kg)`: `<Exercise> (<N>RM): <start> → <target>kg` (or `<Exercise> (<N>RM): <target>kg` if `start <= 0`)
  - `Calories (cal)`: `<Exercise> (<1 min|3 min|5 min|Open>): <start> → <target>cal`
  - `Reps`: `<Exercise> (<1 min|3 min|5 min|Open>): <start> → <target>reps`

---

## 3. Progress & Goals (`Cycle goals`, `By exercise`, `By routine`)

- **`Cycle goals` (`CycleGoalsProgress`)**:
  - Card title:
    - `Weight (kg)`: `<Exercise> (<N>RM Goal)`
    - `Calories (cal)` / `Reps`: `<Exercise> (<1 min|3 min|5 min|Open> Goal)`
  - Baseline Start value:
    - Uses `goal.startWeight` if `> 0.0`, otherwise falls back to historical prior RM / first logged value in cycle.
  - Current Best value:
    - `Weight (kg)`: Max of baseline start weight, cycle `RepMax` for `targetReps`, and cycle working sets matching `targetReps`.
    - `Calories (cal)`: Max of baseline start value and cycle working sets (`metricValue ?: weight`) for `exerciseId`.
    - `Reps`: Max of baseline start value and cycle working sets (`metricValue ?: reps.toDouble()`) for `exerciseId`.
- **`By exercise` & `By routine`**:
  - Unchanged and 100% backward compatible.
