# MCP Contract: Block Session Log Editing (`SPEC-MCP-BLOCKS-02`)

**Tool Name**: `update_block_session_log`  
**Total Registered Tools**: `34`  
**Annotations**: `IDEMPOTENT_WRITE_ANNOTATIONS` (`readOnlyHint: false`, `destructiveHint: false`, `idempotentHint: true`, `openWorldHint: false`)  
**Schema Compliance**: Flat JSON Schema (`0` `$ref`, `0` `exclusiveMinimum` keywords)

## Input Parameters (`UpdateBlockSessionLogInputSchema`)

| Field | Type | Required | Constraints / Notes |
|---|---|---|---|
| `sessionId` | `number` (integer) | No* | Target logged session ID (*provide `sessionId`, or `date` + `blockId`) |
| `date` | `string` | No* | ISO `YYYY-MM-DD`; updates date if `sessionId` is provided, or resolves session with `blockId` |
| `blockId` | `number` (integer) | No* | Optional parent `AvailableBlock` / target block ID |
| `title` | `string` | No | Optional updated session title |
| `notes` | `string` | No | Optional updated session notes |
| `blockTitle` | `string` | No | Optional updated block title (when updating/locating single block) |
| `blockNotes` | `string` | No | Optional updated block prescription/notes (preserves `[ab_...]` / `[rb_v2=...]` markers when omitted) |
| `exercises` | `array<object>` | No | Replacement movement/set groups; each item has `exerciseName` (`min(1)`), optional `exerciseId`, optional `weights` (`array<number>` `.min(0)`), optional `sets` (`array<{ weight: number (.min(0)), reps?: number (.min(1)) }>`) |
| `trackedPrs` | `array<object>` | No | Reconciled PR list for this block; each item has `exerciseName`, optional `exerciseId`, `rmReps` (`.int().min(1)`), optional `weight` (`.min(0)`), optional `remove` (`boolean`) |
| `reconcilePrs` | `boolean` | No | Defaults `true` when `trackedPrs` or `exercises` changes; removes/tombstones stale session PRs not present in `trackedPrs` |

## Behavior Guarantees

1. **Atomic Single-Transaction Write**: Updates the matching `SessionRecord` + `SessionBlock`s + `ExerciseSet`s inside `updateUserData` transaction, leaving sibling sessions untouched.
2. **Tombstoned `RepMaxRecord` Reconciliation**:
   - Any prior `RepMaxRecord` attached to `(sessionId, blockId)` (or `sessionId`) whose `(exerciseId, reps, weight)` is no longer present or is flagged `remove: true` is marked `{ deletedAtMillis: now, updatedAtMillis: now }`.
   - Any added or reweighted PR checks for a matching `(exerciseId, reps, normalizeWeight(weight), date)` tombstone first and revives it (`deletedAtMillis: null, updatedAtMillis: now`), otherwise inserts a fresh active `RepMaxRecord`.
   - `computeCyclesFromRaw` and `listRepMaxes` skip all `RepMaxRecord` items with `deletedAtMillis != null`.
3. **Parent Template Inheritance**:
   - Newly tracked `(movementId, movementName, rmReps)` entries not yet stored on the parent `AvailableBlock`'s `[ab_prs=...]` tag are merged cleanly onto the parent `AvailableBlock` without clobbering existing slots or `[rb_v2=...]` tags.
4. **Android Bidirectional Sync Convergence**:
   - Android `UserCloudSyncManager.downloadUserDataInternal` Step 5 deduplicates incoming `rep_maxes` by `(exerciseId, reps, normalizeWeight(weight), date)` selecting the entry with highest `effectiveRemoteTs`, skips creating deleted entries, and deletes any matching local `RepMax` when `isRemoteDeleted(map)` is true.
