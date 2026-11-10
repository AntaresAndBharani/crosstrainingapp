# MCP Connector & HTTP Interface Contracts

**Feature**: `018-mcp-reliable-orders`
**Protocol**: MCP Streamable HTTP (`2025-03-26` / `2025-06-18`) at `/mcp`

## Schema & Tool Naming Invariants (Gemini & Cross-Client Compatibility)

1. Every tool name MUST match `^[a-z0-9_]{1,60}$`.
2. Serialized JSON Schema in `tools/list` MUST contain **zero** `"$ref"` keys and **zero** `"exclusiveMinimum"` keys.
3. Every tool MUST include `annotations: { readOnlyHint, destructiveHint, idempotentHint, openWorldHint: false }`.

---

## Tool Catalog & Annotations Matrix

| Tool Name | Category | `readOnlyHint` | `destructiveHint` | `idempotentHint` | Purpose |
|-----------|----------|----------------|-------------------|------------------|---------|
| `get_active_cycle` | Cycle Read | `true` | `false` | `true` | Returns the resolved active cycle summary, its active goals, and recent sessions. |
| `list_cycles` | Cycle Read | `true` | `false` | `true` | Lists all discovered cycles with `isActive`, counts, and latest activity date. |
| `get_workout_summary` | Summary Read | `true` | `false` | `true` | High-level snapshot including `activeCycle`, active cycle goals, and global counts. |
| `list_exercises` | Exercise Read | `true` | `false` | `true` | Lists movement library entries (optional category/search filter). |
| `upsert_exercise` | Exercise Write | `false` | `false` | `true` | Creates or updates a single exercise. |
| `batch_upsert_exercises` | Exercise Batch | `false` | `false` | `true` | Creates or updates up to 100 exercises in one atomic call. |
| `list_routines` | Routine Read | `true` | `false` | `true` | Lists saved non-deleted workout routines. |
| `upsert_routine` | Routine Write | `false` | `false` | `true` | Creates or replaces a routine template with blocks. |
| `delete_routine` | Routine Delete | `false` | `true` | `true` | Marks a routine deleted via tombstone. |
| `list_sessions` | Session Read | `true` | `false` | `true` | Lists non-deleted workout sessions (optional date/cycle filters). |
| `upsert_session` | Session Write | `false` | `false` | `true` | Logs or updates a workout session (defaults `cycleId` to active cycle). |
| `delete_session` | Session Delete | `false` | `true` | `true` | Marks a session deleted via tombstone. |
| `list_cycle_goals` | Goal Read | `true` | `false` | `true` | Lists non-deleted cycle goals (optional `cycleId` / `activeCycleOnly` filter) + `activeCycle` metadata. |
| `upsert_cycle_goal` | Goal Write | `false` | `false` | `true` | Creates/updates 1 goal (optional `previousTargetReps` replacement; defaults to active cycle). |
| `delete_cycle_goal` | Goal Delete | `false` | `true` | `true` | Marks matching goal(s) deleted via tombstone (defaults to active cycle). |
| `manage_cycle_goals` | Goal Batch | `false` | `false` | `true` | **Primary multi-order tool**: atomically applies `upserts` (with optional `previousTargetReps` RM conversion) and `deletes` in 1 call. |
| `list_rep_maxes` | PR Read | `true` | `false` | `true` | Lists recorded personal records. |
| `record_rep_max` | PR Write | `false` | `false` | `true` | Records a single rep-max PR (defaults `cycleId` to active cycle). |
| `batch_record_rep_maxes` | PR Batch | `false` | `false` | `true` | Records up to 100 rep-max PRs in one atomic call. |
| `list_weight_entries` | Weight Read | `true` | `false` | `true` | Lists active body-weight entries. |
| `upsert_weight_entry` | Weight Write | `false` | `false` | `true` | Logs/updates a single body-weight entry. |
| `batch_upsert_weight_entries` | Weight Batch | `false` | `false` | `true` | Logs/updates up to 100 body-weight entries in one atomic call. |
| `delete_weight_entry` | Weight Delete | `false` | `true` | `true` | Tombstone-deletes a weight entry by date. |
| `get_weight_momentum_analytics` | Weight Analytics Read | `true` | `false` | `true` | Computes 3:4:2:1 weekly body-weight momentum & 5-zone subcycle corridor diagnostics. |
| `list_daily_logs` | Daily Log Read | `true` | `false` | `true` | Lists active nutrition/fasting/rest daily logs. |
| `upsert_daily_log` | Daily Log Write | `false` | `false` | `true` | Logs/updates a single daily log. |
| `batch_upsert_daily_logs` | Daily Log Batch | `false` | `false` | `true` | Logs/updates up to 100 daily logs in one atomic call. |
| `delete_daily_log` | Daily Log Delete | `false` | `true` | `true` | Tombstone-deletes a daily log by date. |

---

## Primary Batch Contract: `manage_cycle_goals`

### Input
```json
{
  "cycleId": 4,
  "upserts": [
    {
      "exerciseName": "Snatch",
      "previousTargetReps": 2,
      "targetReps": 3,
      "targetWeight": 40
    },
    {
      "exerciseName": "Incline Bench Press",
      "targetReps": 3,
      "startWeight": 50,
      "targetWeight": 70
    },
    {
      "exerciseName": "Clean",
      "targetReps": 3,
      "startWeight": 55,
      "targetWeight": 60
    }
  ],
  "deletes": []
}
```
*(When `cycleId` is omitted, `manage_cycle_goals` resolves the active cycle automatically.)*

### Output (`structured` JSON inside `content[0].text`)
```json
{
  "status": "ok",
  "cycle": {
    "cycleId": 4,
    "cycleName": "Recomposition",
    "isActive": true
  },
  "upsertedCount": 3,
  "deletedCount": 1,
  "upserted": [ ... ],
  "deleted": [
    { "exerciseName": "Snatch", "targetReps": 2, "deleted": true }
  ]
}
```

---

## HTTP Transport & Rate-Limit Contract

- **`POST /mcp`**:
  - Requires valid OAuth Bearer token (`401` with `WWW-Authenticate` on missing/invalid token).
  - If client omits `text/event-stream` in `Accept` (e.g. `Accept: application/json` or `*/*`), middleware normalizes `Accept` to `application/json, text/event-stream` for stateless JSON response transport compatibility.
- **`GET /mcp` / `DELETE /mcp`**:
  - `GET /mcp` returns `405 Method Not Allowed` with `Allow: POST, DELETE` header.
  - `DELETE /mcp` returns `204 No Content`.
- **CORS Headers**:
  - `allowedHeaders`: `Authorization, Content-Type, Accept, Mcp-Session-Id, Mcp-Protocol-Version, Last-Event-ID`
  - `exposedHeaders`: `Mcp-Session-Id, Mcp-Protocol-Version, WWW-Authenticate, Retry-After`
- **Rate Limiting**:
  - Unauthenticated / IP guard: `300` requests/minute per IP (`Retry-After` header on `429`).
  - Authenticated `/mcp` user guard: `180` requests/minute per authenticated token/user identity (`Retry-After` header on `429`), isolating users behind shared Google proxy egress IPs (`66.249.83.*`).
