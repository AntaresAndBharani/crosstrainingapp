# Phase 0 Research: Unified Athlete Block Log Editing & PR Reconciliation

**Feature**: `SPEC-022` (`022-athlete-block-log-editing`)  
**Date**: 2026-11-15

## Decisions & Rationale

### 1. Shared Simplified Block Log UI & Hydration Partitioning
- **Decision**: Extract `LoadedAvailableBlockState`, `SimplifiedBlockLogCard` (preserving `LoadedAvailableBlockCard` ergonomics), `WeightOnlySetTile`, `LoadAvailableBlockSheet`, and `RecordTrackPrsBottomSheet` (Multi-Movement PR picker) into `ui/screens/SimplifiedBlockLogComponents.kt`, shared between **Log Session** and **SessionEditorScreen** (`isEditMode = true`).
- **Root Cause Addressed**: Previously `SessionEditorScreen` only populated `LoadedAvailableBlockState` via explicit `Load Block ▾` picks and routed all persisted `SessionWithBlocks` into `sessionSeed` -> `CompactBlockEditor`.
- **Safeguard**: `AvailableBlockCodec.hydrateSessionBlocksForEditor` partitions blocks cleanly: any block linked to an `AvailableBlock` OR predominantly weight-based (`sets.any { (it.weight ?: 0.0) > 0.0 }` or empty-set strength block without non-weight score fields) hydrates into `HydratedAvailableBlockSeed`; non-weight score/time/metric legacy blocks (`resultText.isNotBlank()`, `resultValue != null`, or `metricValue > 0 && (weight ?: 0.0) <= 0.0`) remain in `residualLegacyBlocks` rendered by `CompactBlockEditor` so no score fields are lost.

### 2. Backward-Compatible `Repository.updateSession` PR Reconciliation
- **Decision**: Add `reconcileRepMaxes: Boolean = false` to `Repository.updateSession(session: Session, blocks: List<BlockInsert>, reconcileRepMaxes: Boolean = false)`.
- **Rationale**: Existing callers such as `UserCloudSyncManager` Step 4 and legacy session edits pass empty `repMaxes` on `BlockInsert` and expect `priorSessionRepMaxes` to be relinked (`reconcileRepMaxes == false`). When `reconcileRepMaxes == true` (athlete editing in `SessionEditorScreen`), `updateSession` reconciles desired PRs vs `priorSessionRepMaxes` inside `withDatabaseTransaction`:
  - Matches each desired `(exerciseId, reps)` against `priorSessionRepMaxes` (by `(exerciseId, reps)` or original `priorBlockId` -> new `blockId`):
    - Existing match -> updates `RepMax` **in place** via `repMaxDao.updateRepMax(...)`, preserving `RepMax.id`, updating `weight`, `date`, `cycleId`, `blockId`.
    - No existing match -> inserts via `upsertRepMaxInternal`.
    - Remaining unmatched `priorSessionRepMaxes` -> deletes via `repMaxDao.deleteRepMax(...)`.
  - Refreshes `CycleGoal` / `Progress` analytics immediately because Room Flows re-emit on commit.

### 3. Session-Excluded PR Comparison & Live Auto-Sync
- **Decision**: Add `excludeSessionId: Long? = null` to `AvailableBlockCodec.bestStoredRepMaxWeight` and `resolveLastBlockSession`. Add `isCustomWeightOverride` per `(movementId, rmReps)` slot in `LoadedAvailableBlockState`.
- **Rationale**: Without `excludeSessionId`, editing a session holding today's PR compares `35 > 35 == false` instead of `35 > 32.5 == true`. Meanwhile, auto-syncing non-overridden PR slots ensures editing a set tile updates the linked PR chip weight in real time, whereas loading a historical PR with a different weight than `maxSetWeight` or manually typing a custom PR weight marks `isCustomWeightOverride = true` so set edits never clobber intentional overrides.

### 4. Parent `AvailableBlock` Template PR Inheritance
- **Decision**: Persist newly tracked `(movementId, movementName, rmReps)` slots onto the parent `AvailableBlock` (`[ab_prs=...]`) when saving an edited or logged block session (`SessionDraft.inheritedBlockTrackedPrs` -> `AppViewModel.applyInheritedTrackedPrs`).
- **Rationale**: `AvailableBlock.upsertTrackedPrSlots(...)` appends new slots cleanly without duplicating existing `(movementId, rmReps)` pairs or overwriting `[rb_v2=...]` routine markers, keeping template inheritance non-destructive for unlinked ad-hoc blocks (`resolvedAvailableBlockId == null`).

### 5. Natural-Keyed `rep_maxes` Cloud Sync & MCP Tombstones (`SPEC-MCP-BLOCKS-02`)
- **Decision**: Add `deletedAtMillis?: number | null` to `RepMaxRecord` in `mcp-server/src/store.ts`, filter tombstones in `computeCyclesFromRaw` & `listRepMaxes`, tombstone replaced/removed session PRs inside `updateBlockSessionLog`, and enhance Android `UserCloudSyncManager.downloadUserDataInternal` Step 5 to group remote `rep_maxes` by `(exerciseId, reps, normalizeWeight(weight), date)` (keeping max `effectiveRemoteTs`) and delete matching local rows when `isRemoteDeleted(map)` is true.
- **Rationale**: Firestore `rep_maxes` sync matches records by natural key `(exerciseId, reps, weight, date)`. Changing a PR weight remotely leaves the old weight present unless tombstoned (`deletedAtMillis: now`), which otherwise resurrects the stale weight on Android sync.

### 6. MCP Tool Schema Constraints
- **Decision**: Define `UpdateBlockSessionLogInputSchema` with inline object factory helpers (`makeUpdateBlockExerciseInputSchema()`, `makeTrackedPrInputSchema()`) and `.min(0)` / `.min(1)` (never `.positive()`, never shared schema object reuse) matching SPEC-021's flat schema invariant (`0` `$ref`, `0` `exclusiveMinimum`), registering `update_block_session_log` as tool `#34` with `IDEMPOTENT_WRITE_ANNOTATIONS`.
