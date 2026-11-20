# Interface Contracts: Daily Routine Optional Multi-Metric Targets & Metric-Aware Logging (`SPEC-026`)

## 1. Codec Metadata Wire Contract (Android <-> Firestore <-> Remote MCP)

- **Metadata Header Tags** (all recognized at the leading boundary of `notes` in any line order and stripped from clean display notes):
  - `[rb_v2=<slot1>;<slot2>;...]` where `<slot>` is `<METRIC>:<MODE>:<fixedValue>:<v1>.<v2>...`
    - `<METRIC>`: `REPS | CALORIES | TIME_SECONDS | METERS`
    - `<MODE>`: `FIXED | PER_SET`
    - Emitted ONLY when targets are configured; omitted completely when targets are unset.
  - `[dr_item=BLOCK:id=<id>:title=<urlEncodedTitle>]` or `[dr_item=EXERCISE:id=<id>:name=<urlEncodedName>]`
  - `[ab_prs=<urlEncodedMovement>:<rmReps>;...]`
  - `[log_metric=<REPS|CALORIES|TIME_SECONDS|METERS>]` on `SessionBlock.notes`

## 2. Remote MCP Tool Contracts (`mcp-server`)

All 39 MCP tools preserve flat JSON Schemas (`0` `$ref`, `0` `exclusiveMinimum`).

### A. `upsert_daily_routine` & `list_daily_routines`
- **`DailyRoutineItem` (`item_type = "EXERCISE"`)**:
  - Optional fields (when omitted/null, item has NO configured targets):
    - `sets_count?: integer (1..100)`
    - `target_metric?: "REPS" | "CALORIES" | "TIME_SECONDS" | "METERS"`
    - `target_mode?: "FIXED" | "PER_SET"`
    - `fixed_value?: integer (>= 0)`
    - `per_set_values?: integer[] (>= 0)`
- **`DailyRoutineItem` (`item_type = "BLOCK"`)**:
  - Optional per-routine target overrides (never mutate underlying `AvailableBlock`):
    - `sets_count?: integer (1..100)`
    - `exercise_targets?: Array<{ exercise_name: string, target_metric: ..., target_mode: ..., fixed_value?: int, per_set_values?: int[] }>`
- **Validation**: When targets are provided on an item, if `mode === "FIXED"` and `fixed_value <= 0`, or `mode === "PER_SET"` and all values are `<= 0`, reject with a descriptive validation error.
- **`list_daily_routines` response**: Exposes decoded `setsCount`, `targetSpec` (for `EXERCISE`), and `exerciseTargets` (for `BLOCK`) alongside clean `note`, `instructions`, and `defaultTrackedPrs`.

### B. `log_daily_routine_session`, `log_block_session`, `update_block_session_log`
- Per-exercise log objects accept:
  - Legacy fields (preserved): `weights?: number[]`, `set_weights_kg?: number[]`, `set_count_for_empty?: integer`
  - Multi-metric fields (new, optional):
    - `metric?: "REPS" | "CALORIES" | "TIME_SECONDS" | "METERS"`
    - `sets?: Array<{ reps?: int (1..500), weight_kg?: number (>=0), weight?: number (>=0), metric_value?: number (>=0), group_index?: int (>=0) }>`
- **Persistence**:
  - Writes `reps`, `weight`, `metricValue`, `groupIndex` onto `SessionSetRecord` rows.
  - Encodes `[log_metric=...]` into `SessionBlock.notes` when `metric` is supplied or inferred from `sets` (`metric_value` / `reps`).

### C. `get_daily_routine_progress`
- Returns `unit: "kg" | "reps" | "cal" | "s" | "m"` per constituent item alongside `allTimeTop`, `lastTop`, `topDelta`, `lastSets`, `chartPoints`, and `recentSessions` supporting weighted reps, bodyweight reps, and conditioning `metricValue` logs.

## 3. UI Interaction Contract (`360dp` Mobile + `1.5x` Accessibility)
- **Daily Routine Composer**: Unset items show `+ Set targets (optional)`. Configured items show collapsible summary header (`{N} sets · {badge}`) + `Clear` action + inline target editor (`Sets`, `Metric`, `Fixed`/`Per-Set`, `0 (Skip)` cards, pattern helpers, `Done ▲`).
- **Session Log/Edit Cards (`SimplifiedExerciseLogCard` & `SimplifiedBlockLogCard`)**:
  - Single-line `LazyRow` metric chips (`Reps`, `Cal`, `Time`, `Meters`) with `heightIn(min = 48.dp)`.
  - `Reps` tile grid: 2 columns on `< 600dp` (1 col at `fontScale >= 1.5f`), 4 columns on `>= 600dp`, stacked integer `Reps` + optional decimal `Weight`.
  - Non-`Reps` tile grid: 3 columns on `< 600dp` (2 cols at `fontScale >= 1.3f`), 4 columns on `>= 600dp`, single numeric input + unit suffix (`cal`, `s`, `m`).
  - Skipped rounds omitted during routine expansion display `Rd {groupIndex}` on tiles.
