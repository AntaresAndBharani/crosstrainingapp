# Internal Module Contracts: Routine-Aware Workout Timer Queue

**Feature**: `031-routine-timer-queue`

## 1. `WorkoutTimerQueueBuilder` Contract

```kotlin
object WorkoutTimerQueueBuilder {
    const val DEFAULT_EXMOM_INTERVAL_SECONDS = 180
    const val DEFAULT_TRANSITION_SECONDS = 60

    fun buildFromAnchor(
        entries: List<SessionTimerBlockEntry>,
        anchorIndex: Int,
        baseConfig: WorkoutTimerConfig = WorkoutTimerConfig()
    ): BuiltWorkoutTimerQueue?

    fun calculateQueueTotalSeconds(
        startingConfig: WorkoutTimerConfig,
        queuedStages: List<QueuedTimerStage>
    ): Int
}
```

- **Invariants**:
  - `entries.isEmpty()` → returns `null`.
  - `anchorIndex` outside `0..entries.lastIndex` → clamped to `[0, entries.lastIndex]`.
  - Every `QueuedTimerStage` generated for `entries.drop(safeAnchor + 1)` uses `TimerMode.EMOM`, `intervalSeconds = 180`, `totalRounds = entry.defaultSetCount.coerceAtLeast(1)`, `prepCountdownSeconds = 0`, `transitionSeconds = 60`, regardless of block instructions/notes.
  - Each generated `QueuedTimerStage` has a distinct `id`.

## 2. `TimerEngine` Multi-Stage API Contract

- `configureQueue(newConfig: WorkoutTimerConfig, queuedStages: List<QueuedTimerStage> = emptyList())`: configures idle/non-running engine with an ordered immutable queue (sanitizing each stage's `transitionSeconds >= 0`).
- `configure(newConfig: WorkoutTimerConfig, queuedNext: QueuedTimerStage? = null)`: delegates to `configureQueue(newConfig, listOfNotNull(queuedNext))`.
- `replaceTimerQueue(newConfig: WorkoutTimerConfig, queuedStages: List<QueuedTimerStage> = emptyList())`: cancels active job and atomically launches `newConfig` + `queuedStages` with `completedStagesCount = 0` and zero transient `IDLE` emission.
- `replaceTimer(newConfig: WorkoutTimerConfig)`: delegates to `replaceTimerQueue(newConfig, emptyList())`.
- `setQueuedStages(stages: List<QueuedTimerStage>)`: replaces `queuedStages` live; if `phase == TimerPhase.TRANSITION` and sanitized `stages.isEmpty()`, finishes timer cleanly via `finishTimer()`.
- `updateQueuedStage(stageId: String, updated: QueuedTimerStage)`: updates stage with matching `id` in place (preserving `stageId`); if head stage's transition changes during `TRANSITION`, updates remaining transition seconds (or promotes immediately when `<= 0`).
- `removeQueuedStage(stageId: String)`: removes matching stage via `setQueuedStages`.
- `appendQueuedStage(stage: QueuedTimerStage)`: appends sanitized stage to `queuedStages`.
- `calculateTotalSecondsRemaining(...)`: deterministic sum across active round, future rounds (with single-round overrides), and all `queuedStages` (respecting `TRANSITION` head countdown already in progress).

## 3. UI & Notification Presentation Contracts

- `ContinuousTimerUiResolver.formatStagePositionHeader(snapshot)`:
  - Multi-stage (`totalPlannedStages > 1`) with non-blank `workoutLabel`: `"Block ${currentStageIndex} of ${totalPlannedStages} • ${workoutLabel}"`
  - Single-stage with non-blank `workoutLabel`: `"${workoutLabel} • ${mode.label}"`
  - Blank `workoutLabel`: preserves `"Stage X of Y • ${mode.label}"` / `mode.label`
- `ContinuousTimerUiResolver.formatInSessionBarSubtitle(snapshot)`:
  - Concise single-line subtitle combining transition/round info and optional `X/Y` queue position.
- `TimerNotificationFormatter.formatTitle(phase, currentRound, totalRounds, nextTargetLabel, workoutLabel)`:
  - Blank `workoutLabel` preserves exact `"Work - Round 3/8"` / `"Transition - Up Next: AMRAP"` outputs.
  - Non-blank `workoutLabel` prefixes `"$workoutLabel • "` on non-transition phases and shows next block title during `TRANSITION`.
