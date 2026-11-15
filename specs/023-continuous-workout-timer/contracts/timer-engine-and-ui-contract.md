# Contracts: Continuous Timer Engine & Cross-Surface UI

## 1. `TimerEngine` Public API Contract

```kotlin
fun configure(newConfig: WorkoutTimerConfig, queuedNext: QueuedTimerStage? = null)
fun replaceTimer(newConfig: WorkoutTimerConfig)
fun start()
fun pause()
fun reset()
fun skipRound()
fun previousRound()

// Live Round & Single-Round Overrides (FR-008, FR-009)
fun adjustTotalRounds(delta: Int)
fun setTotalRounds(newTotal: Int)
fun adjustNextRoundDuration(deltaSeconds: Int)
fun setNextRoundDuration(newSeconds: Int)
fun adjustNextRoundRestDuration(deltaSeconds: Int)
fun setNextRoundRestDuration(newSeconds: Int)
fun clearNextRoundOverrides()

// Continuous Stage Queue & Transition Controls (FR-010, FR-012)
fun setQueuedNextStage(nextStage: QueuedTimerStage?)
fun updateQueuedNextConfig(config: WorkoutTimerConfig)
fun adjustTransitionRemaining(deltaSeconds: Int)
fun setTransitionRemaining(newSeconds: Int)
fun continueFromFinished(nextConfig: WorkoutTimerConfig, transitionSeconds: Int = 0)
fun restartCurrentStage()
fun stopAndClearAll()
```

### Invariants
1. `totalRounds >= currentRound` whenever phase is active (`PREP`, `WORK`, `REST`); during `TRANSITION`, rounds belong to finished stage or transition resets upon promotion. Calling `setTotalRounds(n)` with `n <= currentRound` sets `totalRounds = currentRound` and clears `nextRoundOverrideSeconds` & `nextRoundRestOverrideSeconds` to `null`.
2. `nextRoundOverrideSeconds` is always clamped `>= 5`; `nextRoundRestOverrideSeconds` is always clamped `>= 0`. Both remain `null` whenever `currentRound >= totalRounds` or phase is `IDLE`, `TRANSITION`, or `FINISHED`.
3. Consuming `currentRound -> currentRound + 1`: sets `roundTotalSeconds = nextRoundOverrideSeconds ?: activeConfig.baseWorkDurationSeconds()`, `activeRoundRestSeconds = nextRoundRestOverrideSeconds ?: activeConfig.baseRestDurationSeconds()`, and clears `nextRoundOverrideSeconds = null` & `nextRoundRestOverrideSeconds = null`.
4. Stage completion (`currentRound >= totalRounds` after final `WORK` or final `REST` when `activeRoundRestSeconds > 0`, or via `skipRound()`):
   - If `queuedNextStage == null`: enters `TimerPhase.FINISHED`, `isRunning = false`, keeps `completedStagesCount` incremented/preserved appropriately (finished single/final stage leaves `completedStagesCount` reflecting stages completed prior + UI sees `FINISHED`), clears overrides.
   - If `queuedNextStage != null` and `queuedNextStage.transitionSeconds > 0`: enters `TimerPhase.TRANSITION` with `roundSecondsRemaining = transitionSeconds`, `roundTotalSeconds = transitionSeconds`, `roundSecondsElapsed = 0`, `isRunning = true`, preserving `totalSecondsElapsed`.
   - If `queuedNextStage != null` and `queuedNextStage.transitionSeconds == 0` (OR `TRANSITION` hits `0`, OR `skipRound()` called during `TRANSITION`): promotes `queuedNextStage.config` -> `activeConfig`, sets `queuedNextStage = null`, increments `completedStagesCount`, starts Round 1 `TimerPhase.WORK` immediately with `isRunning = true` (playing start audio/haptic cue if enabled).
5. `continueFromFinished(nextConfig, transitionSeconds)`: usable only from `TimerPhase.FINISHED`; if `transitionSeconds > 0`, sets `queuedNextStage = QueuedTimerStage(nextConfig, transitionSeconds)` and enters `TimerPhase.TRANSITION` running; if `transitionSeconds == 0`, promotes immediately to Round 1 `WORK` (`completedStagesCount + 1`, `queuedNextStage = null`).
6. `restartCurrentStage()`: preserves `queuedNextStage` and `completedStagesCount`, clears single-round overrides, restarts `activeConfig` (`runPrepPhase` or `startMainTimer` / `reset`? Wait: resets/restarts Stage current R1 cleanly; if invoked from Reset dialog while running, restarts current stage from `IDLE` paused or immediately? Wait — "Restart Current Mode (Keep Next Mode)": resetting current mode to initial state `IDLE` or running R1? Putting it into `IDLE` paused (same as `reset()`) OR running? Wait: if the dialog says "Restart Current Mode (Keep Next Mode)" under a `[ Reset ]` action, `reset()` stops and resets to `IDLE` with current config! Calling `restartCurrentStage()` sets `phase = IDLE`, `isRunning = false`, `currentRound = 1`, clears single-round overrides, resets `totalSecondsElapsed = 0` or stage elapsed, and PRESERVES `queuedNextStage` and `completedStagesCount` so tapping Start immediately re-runs Stage current and flows into `queuedNextStage`!).

## 2. Pure UI Resolver Contract (`ui/timer/ContinuousTimerUiResolver.kt`)

```kotlin
object ContinuousTimerUiResolver {
    fun formatClock(seconds: Int): String
    fun formatPrimaryClock(snapshot: TimerSnapshot): String
    fun formatSecondaryClockHint(snapshot: TimerSnapshot): String?
    fun formatCompactModeSummary(config: WorkoutTimerConfig): String
    fun resolveSkipActionLabel(snapshot: TimerSnapshot): String
    fun shouldConfirmReset(snapshot: TimerSnapshot): Boolean
    fun resolveLiveAdjustmentUiState(snapshot: TimerSnapshot): LiveAdjustmentUiState
    fun resolvePoliteBoundaryAnnouncement(previous: TimerSnapshot?, current: TimerSnapshot): String?
}
```

## 3. Notification & Teardown Contract
- `TimerTeardownController.onSnapshotUpdated`: returns `false` on `PREP`, `WORK`, `REST`, `TRANSITION`; performs teardown (`true`) only on `IDLE`, `FINISHED`.
- `TimerNotificationFormatter.formatTitle`:
  - `TimerPhase.TRANSITION`: `"Transition - Up Next: ${nextModeLabel}"` (or `"Transition"` when null)
  - Other phases: unchanged `"${phase.label} - Round $currentRound/$totalRounds"`
- `createTimerNotificationSpec`:
  - `TimerPhase.TRANSITION`: `title = "Transition - Up Next: ..."`, `nextAction.title = "Start Now"`, `nextAction` included.
  - Other phases: unchanged.
