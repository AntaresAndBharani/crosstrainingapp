# Data Model: Routine-Aware Auto-Extrapolated Workout Timer Queue

**Feature**: `031-routine-timer-queue`
**Storage Impact**: Zero Room SQLite schema migration; zero Firestore collection or security-rule changes.

## 1. Domain & Engine Models (`ui/timer/`)

### 1.1 `SessionTimerBlockEntry` (`WorkoutTimerQueueBuilder.kt`)
Immutable projection of one ordered workout block or exercise from `Log Session`:

| Field | Type | Constraints / Semantics |
|---|---|---|
| `blockKey` | `String` | Unique UI key (`availState.uiKey`, `"legacy_<idx>"`, or `"legacy_sub_<firstIdx>_<name>"`) |
| `positionIndex` | `Int` | 0-based display order in `Log Session` |
| `title` | `String` | Display label (`block.title`, exercise name, or `subBlockName`) |
| `defaultSetCount` | `Int` | Resolved set/round count, always `>= 1` |

### 1.2 `BuiltWorkoutTimerQueue` (`WorkoutTimerQueueBuilder.kt`)
Immutable output of extrapolating from an anchor index:

| Field | Type | Semantics |
|---|---|---|
| `anchorIndex` | `Int` | Clamped 0-based anchor index (`0 .. entries.lastIndex`) |
| `startingConfig` | `WorkoutTimerConfig` | Pre-filled `E3MOM` (`180s`) × `defaultSetCount` for anchor block, inheriting prep/sound/vibration |
| `queuedStages` | `List<QueuedTimerStage>` | Ordered remaining blocks (`entries.drop(anchorIndex + 1)`), each `E3MOM` (`180s`) × `defaultSetCount`, `prepCountdownSeconds = 0`, `transitionSeconds = 60` |

### 1.3 `WorkoutTimerConfig` (`WorkoutTimer.kt`) — Additive Field
- Existing fields preserved (`mode`, `intervalSeconds`, `workSeconds`, `restSeconds`, `totalRounds`, `targetMinutes`, `prepCountdownSeconds`, `soundEnabled`, `vibrationEnabled`, `workoutLabel`, `targetDurationSeconds`).
- **New**: `val sourceBlockKey: String? = null` — identifies originating `Log Session` card/sub-block for active card highlighting and same-block conflict checks.

### 1.4 `QueuedTimerStage` (`WorkoutTimer.kt`) — Additive Identity Field
- Existing fields preserved (`config: WorkoutTimerConfig`, `transitionSeconds: Int = 60`).
- **New**: `val id: String = UUID.randomUUID().toString()` — stable unique key for Compose `key = stage.id` rendering and targeted `updateQueuedStage` / `removeQueuedStage` mutations.

### 1.5 `TimerSnapshot` (`WorkoutTimer.kt`) — Multi-Stage Queue Upgrade
- Replaces single-slot stored `queuedNextStage: QueuedTimerStage?` with immutable `val queuedStages: List<QueuedTimerStage> = emptyList()`.
- Computed properties:
  - `val queuedNextStage: QueuedTimerStage? get() = queuedStages.firstOrNull()`
  - `val currentStageIndex: Int get() = completedStagesCount + 1`
  - `val totalPlannedStages: Int get() = completedStagesCount + 1 + queuedStages.size`

## 2. State Transitions

1. `IDLE → PREP (if Stage 1 prep > 0) → WORK`
2. `WORK (final round ends / skipRound) →`
   - if `queuedStages.isEmpty()` → `FINISHED`
   - if `queuedStages.first().transitionSeconds > 0` → `TRANSITION` (`roundSecondsRemaining = head.transitionSeconds`, `queuedStages` unchanged)
   - if `queuedStages.first().transitionSeconds == 0` → pop `head`, `queuedStages = drop(1)`, `completedStagesCount++`, enter `WORK`
3. `TRANSITION (countdown 0 / Start Now) →` pop `head`, `queuedStages = drop(1)`, `completedStagesCount++`, enter `WORK`
4. `TRANSITION (removeQueuedStage removes head)`:
   - if remaining `queuedStages` is non-empty → stay in `TRANSITION`, update Up Next preview to new head
   - if remaining `queuedStages` becomes empty → `FINISHED`
