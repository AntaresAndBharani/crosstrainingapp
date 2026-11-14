# MCP Contract: Available Blocks Tools (`SPEC-MCP-BLOCKS-01`)

Total server tools increase from `28` to **`33`**. All schemas MUST serialize without `$ref` or `exclusiveMinimum` and declare explicit `ToolAnnotations`.

## 1. `list_available_blocks`
- **Annotations**: `readOnlyHint: true`, `destructiveHint: false`, `idempotentHint: true`, `openWorldHint: false`
- **Input**: `{ query?: string, includeLastSession?: boolean, limit?: integer (>= 1) }`
- **Behavior**: Filters `routines` where `defaultFormat === 'AVAILABLE_BLOCK'` (plus optional query match across `title`, `instructions`, and exercise names); decodes `trackedPrs` via `[ab_prs=...]` and optionally attaches `lastSession` weight summary from `sessions`.

## 2. `upsert_available_block`
- **Annotations**: `readOnlyHint: false`, `destructiveHint: false`, `idempotentHint: true`, `openWorldHint: false`
- **Input**: `{ id?: integer (>= 1), title: string, instructions?: string, exercises?: string[], trackedPrs?: [{ movementName: string, rmReps: integer (>= 1) }] }`
- **Behavior**: Idempotently resolves or creates named exercises in `exercises`, normalizes `trackedPrs`, writes `Routine` (`defaultFormat: 'AVAILABLE_BLOCK'`) + `RoutineBlock` entries with `[ab_prs=...]` metadata inside transactional Firestore update.

## 3. `batch_upsert_available_blocks`
- **Annotations**: `readOnlyHint: false`, `destructiveHint: false`, `idempotentHint: true`, `openWorldHint: false`
- **Input**: `{ blocks: [{ id?: integer (>= 1), title: string, instructions?: string, exercises?: string[], trackedPrs?: [...] }] (min 1) }`
- **Behavior**: Atomically creates missing exercises and upserts all supplied Available Blocks in a single Firestore transaction.

## 4. `delete_available_block`
- **Annotations**: `readOnlyHint: false`, `destructiveHint: true`, `idempotentHint: true`, `openWorldHint: false`
- **Input**: `{ id?: integer (>= 1), title?: string }`
- **Behavior**: Resolves matching `AVAILABLE_BLOCK` (or routine) by `id` or `title`, tombstones it idempotently in `routines`, and preserves existing historical `sessions`.

## 5. `log_block_session`
- **Annotations**: `readOnlyHint: false`, `destructiveHint: false`, `idempotentHint: true`, `openWorldHint: false`
- **Input**:
  - `date: string (YYYY-MM-DD)`
  - `sessionTitle?: string`
  - `blockId?: integer (>= 1)`
  - `blockTitle?: string`
  - `cycleId?: integer (>= 1)` (auto-resolves active cycle when omitted)
  - `notes?: string`
  - `exerciseLogs: [{ exerciseName: string, weights: number[], setCountForEmpty?: integer (>= 1) }] (min 1)`
  - `recordedPrs?: [{ movementName: string, rmReps: integer (>= 1), weight: number }]`
  - `updateTrackedPrLinks?: boolean`
- **Behavior**: Resolves/creates exercises & block, creates/upserts `Session` + `SessionBlock` + weight-only `WorkoutSet` entries (`reps: 1`, positive numeric weight or `0` for unloaded bodyweight/cardio), records `RepMax` rows linked to `sessionId`, `blockId`, `cycleId`, `sourceBlockTitle`, `sessionTitle`, and appends any new `(movementName, rmReps)` links to the block's `trackedPrs` when `updateTrackedPrLinks !== false`.
