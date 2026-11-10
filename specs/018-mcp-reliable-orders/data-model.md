# Data Model: Reliable Multi-Order & Active-Cycle MCP Communication

**Feature**: `018-mcp-reliable-orders`
**Date**: 2026-11-10

## Cloud Firestore Document Layout (Unchanged Paths, Enriched Item Fields)

All user collections continue to reside at `/environments/{env}/users/{uid}/data/{collectionName}` (with legacy read fallback `/users/{uid}/data/{collectionName}`) storing `{ "list": [...] }`, preserving 100% compatibility with `firestore.rules`.

---

## Entities & Records

### 1. `CycleSummary` (Derived Domain View in MCP)

Computed across non-deleted `cycle_goals`, `sessions`, and `rep_maxes` for a user.

| Field | Type | Description |
|-------|------|-------------|
| `cycleId` | `number` | Positive integer cycle identifier (`>= 1`). |
| `cycleName` | `string` | Human-readable cycle name (e.g., `"Recomposition"`), or `"Cycle <id>"` fallback. |
| `isActive` | `boolean` | Exactly one cycle per user is `true`. |
| `explicitActiveFlag` | `boolean \| undefined` | `true`/`false` when synced from mobile `isActiveCycle` marker; `undefined` on legacy snapshots. |
| `goalsCount` | `number` | Count of active (non-deleted) `CycleGoalRecord` items in this cycle. |
| `sessionsCount` | `number` | Count of active (non-deleted) `SessionWithBlocksRecord` items in this cycle. |
| `repMaxesCount` | `number` | Count of `RepMaxRecord` items associated with this cycle. |
| `latestActivityDate` | `string \| null` | Latest `YYYY-MM-DD` across sessions/rep-maxes in this cycle, if any. |

**Active-Cycle Resolution Precedence**:
1. Among cycles with an explicit `isActiveCycle === true` stamp on their latest synced records, pick the one with the newest `updatedAtMillis` (tie-breaking by highest `cycleId`).
2. Otherwise, among all discovered cycles with non-deleted records (excluding any cycle explicitly stamped `isActiveCycle === false` when un-marked cycles exist), pick the highest `cycleId` (tie-breaking by `latestActivityDate` and `updatedAtMillis`).
3. If no cycles exist yet, return `{ cycleId: 1, cycleName: "Active Cycle", isActive: true, goalsCount: 0, sessionsCount: 0, repMaxesCount: 0, latestActivityDate: null }`.

---

### 2. `CycleGoalRecord` (`cycle_goals.list[]`)

| Field | Type | Constraints / Notes |
|-------|------|---------------------|
| `id` | `number` | Positive integer within user snapshot. |
| `cycleId` | `number` | Resolved target `cycleId` (defaults to active cycle when omitted). |
| `cycleName` | `string \| undefined` | Preserved/stamped from resolved cycle so Android maps by name + ID. |
| `isActiveCycle` | `boolean \| undefined` | Stamped by Android upload (`true` if parent local `Cycle.isActive`, else `false`) and preserved by MCP writes. |
| `exerciseId` | `number` | Linked `ExerciseRecord.id`. |
| `exerciseName` | `string` | Canonical exercise name. |
| `targetReps` | `number` | Target rep scheme (`1` for 1RM, `3` for 3RM, `6` for 6RM; `timeWindowMinutes` for non-weight metrics). |
| `startWeight` | `number` | Starting baseline value (`>= 0`, defaults to `0`). |
| `targetWeight` | `number` | Target value (`> 0`). |
| `notes` | `string` | Encoded metadata + user notes (`CycleGoal.encodeMetadata` compatible). |
| `updatedAtMillis` | `number \| undefined` | Epoch ms set on all MCP writes/deletes; triggers Android incremental import. |
| `deletedAtMillis` | `number \| null \| undefined` | Epoch ms set when deleted via MCP; filtered out by MCP reads and deleted from local Room on Android `downloadUserData`. |

**Natural Key**: `(cycleId, normalizedExerciseName / exerciseId, goalMetric, targetReps)`.

---

### 3. `RoutineWithBlocksRecord` (`routines.list[]`) & `SessionWithBlocksRecord` (`sessions.list[]`)

Extended with soft-delete tombstone support for remote MCP deletions:

- `routine.deletedAtMillis?: number | null` (alongside `routine.updatedAtMillis?: number`)
- `session.isActiveCycle?: boolean` (stamped on Android upload and MCP active-cycle session writes)
- `session.deletedAtMillis?: number | null` (alongside `session.updatedAtMillis?: number`)

**State Transitions for Remote Tombstones**:
1. **Active**: `deletedAtMillis == null` → visible in MCP `list_*` / `get_workout_summary`; upserted on Android `downloadUserData`.
2. **Remotely Deleted via MCP**: `deletedAtMillis = now`, `updatedAtMillis = now` → hidden from MCP read tools; when Android runs `downloadUserData`, matching local entity (`CycleGoal` by `(cycleId, exerciseId, goalMetric, targetReps)`, `Routine` by case-insensitive `name`, `Session` by `(date, case-insensitive title)`) is deleted from Room.
3. **Compacted on Android Upload**: Subsequent Android `uploadUserData` writes local active rows only, compacting consumed tombstones out of Firestore.
4. **Re-Created via MCP Before Sync**: Upserting the same natural key clears `deletedAtMillis` (`null`/`undefined`) and updates `updatedAtMillis = now`.

---

### 4. `BatchCycleGoalOperation` (Input Payload for `manage_cycle_goals`)

| Field | Type | Description |
|-------|------|-------------|
| `cycleId` | `number \| undefined` | Optional target cycle ID; defaults to resolved active cycle. |
| `cycleName` | `string \| undefined` | Optional target cycle name override/hint. |
| `upserts` | `GoalUpsertItem[] \| undefined` | Goals to create or update; each item optionally accepts `previousTargetReps` to atomically remove an old RM goal (e.g. `previousTargetReps: 2`, `targetReps: 3` converts `2RM -> 3RM`). |
| `deletes` | `GoalDeleteItem[] \| undefined` | Goals to soft-delete by `exerciseName` and optional `targetReps`. |
