# Research & Technical Decisions: Routine-Aware Auto-Extrapolated Workout Timer Queue

**Feature**: `031-routine-timer-queue`
**Date**: 2026-11-28

## 1. Multi-Stage Queue Evolution (`TimerSnapshot` & `TimerEngine`)

- **Current State**: `WorkoutTimer.kt` defines `QueuedTimerStage(config, transitionSeconds = 60)` and `TimerSnapshot` stores a single `queuedNextStage: QueuedTimerStage? = null`. Once Stage 1 finishes and Stage 2 promotes, `queuedNextStage` becomes `null`.
- **Decision**: Upgrade `TimerSnapshot` to store an immutable `val queuedStages: List<QueuedTimerStage> = emptyList()` and expose computed backward-compatible properties:
  - `val queuedNextStage: QueuedTimerStage? get() = queuedStages.firstOrNull()`
  - `val currentStageIndex: Int get() = completedStagesCount + 1`
  - `val totalPlannedStages: Int get() = completedStagesCount + 1 + queuedStages.size`
- **Rationale**: Existing unit tests (`ContinuousTimerTest`, `TimerServiceTest`, `TimerNotificationActionTest`, `TimerTeardownTest`) and callers (`TimerScreen`, `SubBlockInlineTimer`) read `snapshot.queuedNextStage` and construct `TimerSnapshot(...)`. Keeping `queuedNextStage` as a computed property preserves 100% behavioral compatibility while unlocking arbitrary-length workout queues.
- **Note on `TimerSnapshot` test call sites**: `ContinuousTimerTest.kt` passes `queuedNextStage = ...` as a named constructor parameter in 2 places (lines 389 and 430); those 2 lines will be updated to `queuedStages = listOf(...)`.

## 2. Deterministic Block Extrapolation (`WorkoutTimerQueueBuilder`)

- **Current State**: `SessionEditor.kt:1569-1572` passes hardcoded `roundCount = 1` to `launchTimingFromTokenOrOpenSheet` and ignores sibling blocks.
- **Decision**: Introduce a pure Kotlin `WorkoutTimerQueueBuilder` operating on immutable `SessionTimerBlockEntry(blockKey, positionIndex, title, defaultSetCount)` snapshots:
  - Anchor block (`entries[safeAnchor]`): initializes `E3MOM` (`intervalSeconds = 180`), `totalRounds = defaultSetCount.coerceAtLeast(1)`, `workoutLabel = entry.title.trim()`, `sourceBlockKey = entry.blockKey`, inheriting `prepCountdownSeconds`, `soundEnabled`, and `vibrationEnabled` from `baseConfig`.
  - Remaining blocks (`entries.drop(safeAnchor + 1)`): **ALWAYS** assign `E3MOM` (`intervalSeconds = 180`), `totalRounds = entry.defaultSetCount.coerceAtLeast(1)`, `prepCountdownSeconds = 0`, `transitionSeconds = 60`, ignoring block instructions/notes/embedded timing tokens.
- **Rationale**: Matches the locked Product Owner rule (`docs/draft-requisites/routine-timer-queue.md`), guarantees zero Android/Compose dependencies in domain logic, and makes extrapolation 100% deterministic and unit-testable.

## 3. Set-Count Resolution Across Simplified & Legacy Session Cards

- **Current State**:
  - `LoadedAvailableBlockState` already tracks `complexSetCount` (resolved from `DailyRoutine` `targetSetsCount` → prior session sets → `defaultEmptyTileCount` of `3` for complexes and `4` for standalone exercises).
  - Legacy `EditorBlockItem.SubBlockGroup` exposes `totalRounds`, and `BlockState` exposes `sets.size`.
- **Decision**: Implement `internal fun buildSessionTimerBlockEntries(loadedAvailableBlocks, legacyBlocks)` in `SessionEditor.kt` projecting visible non-blank items in exact UI display order with `defaultSetCount = ...coerceAtLeast(1)`.

## 4. `NextModeConfigSheet` Label & Identity Preservation

- **Current State**: In `ContinuousTimerComponents.kt:564-586`, switching `TimerMode` or tapping a preset inside `NextModeConfigSheet` constructs `defaultConfigForMode(...)`, which resets `workoutLabel` to `""` and does not carry `sourceBlockKey` or `existingStage.id`.
- **Decision**: Preserve `existingStage?.id`, `existingStage?.config?.workoutLabel`, and `existingStage?.config?.sourceBlockKey` across preset/mode changes inside `NextModeConfigSheet`, and display `"Edit: <Label>"` when editing a named block stage.

## 5. Foreground Notification & TalkBack Backward Compatibility

- **Current State**: `TimerServiceTest` and `TimerNotificationActionTest` assert exact notification title strings (`"Work - Round 3/8"`, `"Rest - Round 2/5"`, `"Get Ready! - Round 1/10"`, `"Transition - Up Next: AMRAP"`) when `workoutLabel` is blank.
- **Decision**:
  - In `TimerNotificationFormatter.formatTitle(...)` and `createTimerNotificationSpec(...)`: when `workoutLabel` is blank, emit the exact legacy format (`"${phase.label} - Round X/Y"` / `"Transition - Up Next: ${mode.label}"`); when `workoutLabel` (or next stage `workoutLabel`) is non-blank, include the block title (`"$workoutLabel • ${phase.label} - Round X/Y"` / `"Transition - Up Next: $nextTargetLabel"`).
  - In `ContinuousTimerUiResolver.resolvePoliteBoundaryAnnouncement`: fire only on phase/round/mode/stage/workoutLabel boundary changes, including `workoutLabel` when non-blank while keeping blank-label announcements matching existing tests.

## 6. Non-Focus-Stealing Docked Bar & Active Card Sync

- **Current State**: `InSessionTimerBar` already applies `Modifier.focusProperties { canFocus = false }` and a shared `MutableInteractionSource`, but its buttons use `size(36.dp)` and it lacks queue position (`1/4`) or transition block label.
- **Decision**: Keep `focusProperties { canFocus = false }`, upgrade interactive buttons (`Play/Pause`, `Skip`, `Dismiss`) to `48.dp` (`defaultMinSize(minWidth = 48.dp, minHeight = 48.dp)`) with `8.dp` spacing (`pages/routine-timer-queue.md`), keep card tap for expand (removing the redundant 4th expand `IconButton` so `360dp` width stays spacious), and match `snapshot.activeConfig.sourceBlockKey` / `snapshot.queuedNextStage?.config?.sourceBlockKey` to highlight the active/up-next card in `LogSessionScreen`.
