# Phase 1 Data Model: Daily Routine Builder Redesign & Multi-Metric Per-Set Targets

**Feature**: `019-routine-builder-redesign`  
**Date**: 2026-11-12

## 1. Domain Enumerations & Value Types

### 1.1 `BlockKind` (`Enums.kt` & `mcp-server/src/schemas.ts`)
Adds `GYMNASTICS("Gymnastics")` (shortLabel `"Gymnastics"`, length `10 <= 15`):
- Primary visual chips in Routine Builder: `WEIGHTLIFTING`, `METCON`, `GYMNASTICS`, `ACCESSORY`
- Overflow (`More ▾`) chips: `STRENGTH`, `HYPERTROPHY`, `COMPLEX`, `SUPERSET`, `CARDIO`, `WARMUP`, `METABOLIC`, `OTHER`

### 1.2 `ExerciseTargetMetric` (`RoutineTargetCodec.kt` & `mcp-server/src/schemas.ts`)
| Enum Token | UI Label | Compact Unit | Stepper Delta (`Fixed` / `Per-Set`) | Default Fixed Value | Quick Presets (Stored Integer Values) | Default Mapping from `Exercise.metricType` |
| :--- | :--- | :--- | :--- | :--- | :--- | :--- |
| `REPS` | `Reps` | `reps` | `±1` / `±1` | `8` | `3, 5, 8, 10, 12, 15` | `MetricType.WEIGHT`, `MetricType.REPS` |
| `CALORIES` | `Calories` | `cal` | `±2` / `±1` | `15` | `10, 12, 15, 20, 25, 30` | `MetricType.CALORIES` |
| `TIME_SECONDS` | `Time` | `s` | `±15` / `±5` | `45` | `20, 30, 45, 60, 90, 120` (`2m`) | `MetricType.TIME` |
| `METERS` | `Meters` | `m` | `±50` / `±50` | `250` | `100, 200, 250, 400, 500, 1000` (`1km`) | `MetricType.DISTANCE` |

### 1.3 `TargetMode`
- `FIXED`: Single non-negative integer `fixedValue` applied across all `N = setsCount` sets. Validation requires `fixedValue > 0`.
- `PER_SET`: Ordered list `perSetValues: List<Int>` of length `N = setsCount`, where each element is `>= 0`. `0` means the exercise is skipped on that set (`0 (Skip)`). Validation requires `perSetValues.any { it > 0 }`.

---

## 2. Canonical Wire & Persistence Format (`[rb_v2=...]`)

Stored as an optional leading metadata line in `RoutineBlock.notes` (Room `routine_blocks.notes`, CSV `#routineBlocks.notes`, Firestore `routines.blocks[].notes`, `SharedBlockPayload.notes`):

```text
[rb_v2=<slot_0>;<slot_1>;...;<slot_k>]
<optional user coaching notes>
```

### Slot Grammar
Each `<slot_i>` corresponds to the `i`-th entry in `RoutineBlock.exerciseIdsCsv` (and `exerciseNamesCsv`):
```text
<METRIC>:<MODE>:<fixedValue>:<v1>.<v2>...<vN>
```
- `<METRIC>`: `REPS` | `CALORIES` | `TIME_SECONDS` | `METERS`
- `<MODE>`: `FIXED` | `PER_SET`
- `<fixedValue>`: Non-negative integer (`>= 0`)
- `<v1>.<v2>...<vN>`: Dot-separated non-negative integers of length `setsCount` (`>= 1`). Even in `FIXED` mode, `N` copies of `fixedValue` are emitted so consumers have an explicit `N`-length vector.

### Concrete Example
4-set `E3MOM` Mixed Conditioning block (`setsCount = 4`, `exerciseIdsCsv = "10,11,12"`):
- Slot 0 (Bench Press): `REPS:FIXED:8:8.8.8.8`
- Slot 1 (Air Bike): `CALORIES:PER_SET:15:15.15.12.0`
- Slot 2 (Concept2 Row): `METERS:PER_SET:250:250.250.200.0`

Encoded `RoutineBlock`:
- `setsCount`: `4`
- `exerciseIdsCsv`: `"10,11,12"`
- `targetRepsScheme`: `"8 reps + 15-15-12-0 cal + 250-250-200-0m"`
- `notes`: `"[rb_v2=REPS:FIXED:8:8.8.8.8;CALORIES:PER_SET:15:15.15.12.0;METERS:PER_SET:250:250.250.200.0]\n RPE 8 across"`
- `RoutineTargetCodec.extractCleanNotes(notes)`: `"RPE 8 across"`

### Legacy Fallback Decoding (When `[rb_v2=...]` is Absent)
When opening or listing a routine saved before `019-routine-builder-redesign`:
1. Resolve exercise IDs from `exerciseIdsCsv` (preserving order).
2. Infer default `ExerciseTargetMetric` for each exercise from `Exercise.metricType`.
3. Parse `targetRepsScheme`:
   - Hyphen-separated variable scheme matching multi-set pattern (e.g. `"21-15-9"` or `"12-10-8-6"`) on single-exercise block → `TargetMode.PER_SET` with `perSetValues` normalized to `setsCount` (or `parsed.size` if `setsCount <= 1` and `parsed.size > 1`).
   - Multi-exercise sequence separator (`+` or `/`, e.g. `"1 + 2 + 1"`) → assigns each movement `TargetMode.FIXED` with its positional integer (`>= 1`).
   - Single integer (e.g. `"5"` or `"8"`) → `TargetMode.FIXED` with `fixedValue = parsed`.
   - Non-numeric / sentinel string (e.g. `"TRISET_1"`, `""`, `"Max"`) → defaults to the metric's default fixed value (`8` reps / `15` cal / `45`s / `250`m) in `TargetMode.FIXED`.

---

## 3. UI State Entities (`RoutineBuilderUiState`)

```kotlin
data class MetricValueCache(
    val fixedValue: Int,
    val perSetValues: List<Int>
)

data class RoutineExerciseUi(
    val uiKey: String, // Unique per sequence slot (supports repeated exerciseIds in one block)
    val exerciseId: Long,
    val exerciseName: String,
    val category: ExerciseCategory,
    val targetMetric: ExerciseTargetMetric,
    val targetMode: TargetMode = TargetMode.FIXED,
    val fixedValue: Int = targetMetric.defaultFixedValue,
    val perSetValues: List<Int> = emptyList(), // Invariant: size == parent block.setsCount
    val cachedMetricValues: Map<ExerciseTargetMetric, MetricValueCache> = emptyMap(),
    val showValidationError: Boolean = false
) {
    val isAllZeroInvalid: Boolean
        get() = when (targetMode) {
            TargetMode.FIXED -> fixedValue <= 0
            TargetMode.PER_SET -> perSetValues.isEmpty() || perSetValues.all { it <= 0 }
        }
}

data class RoutineBlockUi(
    val uiKey: String,
    val name: String = "",
    val kind: BlockKind = BlockKind.WEIGHTLIFTING,
    val format: String = "Sets x Reps",
    val setsCount: Int = 4, // Invariant: >= 1
    val formatDefaultHint: String? = null,
    val isSetupExpanded: Boolean = true,
    val isExercisesExpanded: Boolean = true,
    val expandedExerciseKey: String? = null,
    val exercises: List<RoutineExerciseUi> = emptyList(),
    val notes: String = "", // Clean user notes (no [rb_v2=...] header)
    val section: String = "",
    val subBlock: String = ""
)

data class RoutineBuilderUiState(
    val routineId: Long = 0L,
    val mainExerciseId: Long? = null,
    val defaultFormat: String = "",
    val routineName: String = "",
    val description: String = "",
    val isRoutineInfoExpanded: Boolean = true,
    val expandedBlockKey: String? = null, // Exclusive block expansion invariant
    val blocks: List<RoutineBlockUi> = emptyList(),
    val initialSnapshotSignature: String = "",
    val undoBuffer: DeletedRoutineItem? = null
)
```

### State Transition Invariants
1. **Exclusive Block Expansion**: Setting `expandedBlockKey = key` or appending a new block sets `expandedBlockKey` to that block's `uiKey` (collapsing all other blocks), and if `routineName.isNotBlank()`, auto-collapses `isRoutineInfoExpanded = false`.
2. **Collapse All**: Sets `isRoutineInfoExpanded = false` and `expandedBlockKey = null`.
3. **Format → Default Sets**: Selecting `format` updates `setsCount = defaultSetsForFormat(format)` (`1` for `AMRAP`/`For Time`/`Chipper`/`Death by`/`Time cap`; `8` for `Tabata`; `4` otherwise) and resizes every child exercise's `perSetValues`.
4. **Block Set-Count Sync (`oldSets -> newSets`)**:
   - `newSets > oldSets`: Appends `newSets - oldSets` copies of `perSetValues.lastOrNull() ?: fixedValue` (so `Set 5` copies `Set 4`, including `0`).
   - `newSets < oldSets`: `perSetValues.take(newSets)`.
5. **All-Zero Validation Gate**:
   - `canSave` is `true` iff `routineName.trim().isNotEmpty()`, `blocks.any { it.exercises.isNotEmpty() }`, and `blocks.all { b -> b.exercises.all { !it.isAllZeroInvalid } }`.
   - Tapping `Done ▲` on an exercise where `isAllZeroInvalid == true` sets `showValidationError = true` and keeps the exercise expanded.

---

## 4. Workout Logging Mapping (`RoutineWithBlocks` → `List<BlockState>`)

| Routine Block Configuration | Generated `BlockState` Entries in `SessionEditor` | `SetState` Pre-Population Rules |
| :--- | :--- | :--- |
| 1 Exercise (`REPS`, Fixed `8`, `4` sets) | 1 standalone `BlockState` (`exercise = ex`, `subBlock = blk.subBlock`) | 4 `SetState(reps = "8", value = "", group = "")` |
| 1 Exercise (`CALORIES`, Per-Set `[15, 15, 12, 0]`) | 1 standalone `BlockState` (`exercise = ex`) | 3 active `SetState`s (`reps = "1"`, `value = "15","15","12"`, `group = "1","2","3"`); round 4 (`0`) omitted |
| `M >= 2` Exercises (`Bench 8 reps`, `Bike 15-15-12-0 cal`, `Row 250-250-200-0m`, `4` sets `E3MOM`) | `M` `BlockState`s sharing `subBlock = blk.subBlock.ifBlank { blk.name.ifBlank { "Block ${idx+1}" } }`, `format = "E3MOM"`, `section = blk.section` (grouped by `groupEditorBlocks` into `SubBlockGroup`) | Bench: 4 sets (`reps="8"`, `value=""`, `group="1".."4"`); Bike: 3 sets (`reps="1"`, `value="15","15","12"`, `group="1","2","3"`); Row: 3 sets (`reps="1"`, `value="250","250","200"`, `group="1","2","3"`) |
