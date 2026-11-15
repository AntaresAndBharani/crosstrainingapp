# Phase 1 Data Model: Unified Athlete Block Log Editing & PR Reconciliation

**Feature**: `SPEC-022` (`022-athlete-block-log-editing`)  
**Room Schema**: Remains at **v10** (no schema migration required)  
**Firestore Collections**: Existing 7-collection `/environments/{env}/users/{uid}/data/{collection}` layout unchanged

## 1. Kotlin Codec Models (`util/AvailableBlockCodec.kt`)

### `HydratedBlockExerciseSeed`
- `movementId: Long` (resolved exercise ID or `<= 0L` if unlinked custom name)
- `movementName: String`
- `weights: List<Double>` (positive logged set weights in kg; defaults to `[0.0]` when empty strength block)
- `perSetReps: List<Int>` (original `ExerciseSet.reps` aligned 1:1 with `weights` for lossless save)
- `defaultReps: Int` (fallback `>= 1` when adding sets)

### `HydratedAvailableBlockSeed`
- `originalBlockId: Long`
- `resolvedAvailableBlock: AvailableBlock?` (matched by `[rb_v2]` or block title)
- `blockTitle: String` (e.g., `"Block A"`)
- `cleanNotes: String` (user-facing prescription text without metadata markers)
- `rawFormat: String`, `rawScheme: String`, `rawSection: String`, `rawKind: String`, `rawIsCompleted: Boolean`
- `exercises: List<HydratedBlockExerciseSeed>`
- `persistedRepMaxes: List<RepMax>` (`RepMax` rows linked by `blockId == originalBlockId`, or `(sessionId == session.id && exerciseId in movementIds)` fallback)

### `SessionBlockHydrationResult`
- `simplifiedBlocks: List<HydratedAvailableBlockSeed>`
- `residualLegacyBlocks: List<SessionBlockWithSets>`

## 2. UI Draft & Stateful Holder Models

### `SessionDraft` (`ui/SessionDraft.kt`)
- Existing fields: `cycleId`, `date`, `title`, `notes`, `blocks`
- Added fields:
  - `reconcileRepMaxes: Boolean = false`
  - `inheritedBlockTrackedPrs: Map<Long, List<TrackedPrSlot>> = emptyMap()`

### `LoadedAvailableBlockState` (`ui/screens/SimplifiedBlockLogComponents.kt`)
- Preserves raw block metadata (`originalBlockId`, `rawFormat`, `rawScheme`, `rawSection`, `rawKind`, `rawIsCompleted`, per-set `perSetReps`).
- Tracks per `(movementId, rmReps)` PR slot:
  - `persistedSessionPrKeys: MutableSet<Pair<Long, Int>>` — slots backed by existing session `RepMax` rows
  - `explicitlyAppliedPrKeys: MutableSet<Pair<Long, Int>>` — slots newly recorded/edited by the user via `Record / Track PRs`
  - `customWeightOverrides: MutableMap<Pair<Long, Int>, Boolean>` — `true` if manually overridden in `Record / Track PRs` or loaded persisted `RepMax.weight != maxSetWeightFor(movementId)`
  - `lastDismissedPrSnapshot: DismissedPrSnapshot?` — enables `[Undo]` Snackbar in edit mode before Save
- `toBlockDrafts(availablePrs, editingSessionId, exercises)` emits `RepMaxDraft(movementId, rmReps, candidateWeight)` **only** when `movementId > 0L && candidateWeight > 0.0 && (chip.isNewPr || key in persistedSessionPrKeys || key in explicitlyAppliedPrKeys)`.

## 3. Repository Reconciliation (`data/Repository.kt`)

`updateSession(session: Session, blocks: List<BlockInsert>, reconcileRepMaxes: Boolean = false)`:
- **`reconcileRepMaxes == false`** (legacy / `UserCloudSyncManager`): Preserves existing relinking of `priorSessionRepMaxes` onto newly inserted `SessionBlock` rows without deleting removed PRs.
- **`reconcileRepMaxes == true`** (athlete block log edit):
  1. Updates `session`, replaces `session_blocks` and `exercise_sets`.
  2. Collects desired PRs `(exerciseId, reps, weight, newBlockId)` deduplicated by `(exerciseId, reps)` keeping max weight.
  3. Matches against `priorSessionRepMaxes` on `session.id` by `(exerciseId, reps)`:
     - Match found -> `repMaxDao.updateRepMax(existing.copy(weight = desired.weight, date = session.date, cycleId = session.cycleId, blockId = newBlockId))` (preserves `RepMax.id`).
     - No match -> `upsertRepMaxInternal(...)`.
     - Unmatched prior rows -> `repMaxDao.deleteRepMax(prior)`.

## 4. MCP & Firestore Data Model (`mcp-server/src/store.ts`)

### `RepMaxRecord` Extension
- Existing fields: `id`, `exerciseId`, `reps`, `weight`, `date`, `sessionId`, `blockId`, `cycleId`, `updatedAtMillis`
- Added field:
  - `deletedAtMillis?: number | null` — non-null timestamp marks soft deletion / replacement so Android Step 5 removes local duplicates rather than resurrecting old PR weights.
