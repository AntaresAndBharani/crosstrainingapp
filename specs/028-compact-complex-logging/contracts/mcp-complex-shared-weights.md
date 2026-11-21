# MCP Contract: Shared Complex Set Weights (`SPEC-028`)

## Affected Tools (`mcp-server`)
All 45 registered tools retain flat JSON Schemas (`0` `$ref`, `0` `exclusiveMinimum`):
1. `log_complex_session` (and legacy alias `log_block_session`)
2. `update_complex_session_log` (and legacy alias `update_block_session_log`)
3. `log_daily_routine_session`

## New Optional Input Fields

### 1. `log_complex_session` / `log_block_session`
- `shared_set_weights_kg?: number[]` (`min(0)`, `max(2000)`, length `1..50`): Ordered shared loads per canonical round (`S1..SN`). Applied to active weighted constituent movements of the matched Complex (or movements in `exercise_targets`/`exerciseLogs`).
- `exercise_targets?: Array<{ exercise_name, target_metric, target_mode?, fixed_value?, per_set_values? }>`: Optional per-exercise target pattern (`0` = Skip round) guiding how `shared_set_weights_kg` maps onto each movement (`group_index` preserved whenever any round skips).
- `exerciseLogs` becomes optional when `shared_set_weights_kg` is supplied (providing both merges explicit `exerciseLogs` overrides on top of `shared_set_weights_kg` for mixed-weight sets).

### 2. `update_complex_session_log` / `update_block_session_log`
- `shared_set_weights_kg?: number[]`
- `exercise_targets?: Array<{ exercise_name, target_metric, target_mode?, fixed_value?, per_set_values? }>`
- When `shared_set_weights_kg` is supplied without `exercise_targets`, each existing constituent block preserves its currently active canonical `groupIndex` mask (falling back to dense `1..K` rounds when no `groupIndex` existed). Explicit `exercises` entries override specific movements when both are passed.

### 3. `log_daily_routine_session` (`logged_items[i]` where `item_type` is `"COMPLEX"` / `"BLOCK"`)
- `shared_set_weights_kg?: number[]`: Expands shared loads across the Complex's movements using the resolved Daily Routine item's saved `exerciseTargets` (`0` = Skip) and preserves `exercise_logs` as per-movement overrides.

## Expansion Invariants
- Weighted movements (`WEIGHT` / `REPS` with `metricType === "WEIGHT"` or unspecified) receive `{ reps, weight_kg: sharedWeight, group_index }`.
- Non-weighted movements (`CALORIES`, `TIME_SECONDS`, `METERS`, or `metricType === "REPS"`) receive `{ reps / metric_value, group_index }` without writing `weight_kg`.
- Rounds where a movement's target is `0` emit NO `BlockSet` row (`groupIndex` on active rows preserves round alignment).
