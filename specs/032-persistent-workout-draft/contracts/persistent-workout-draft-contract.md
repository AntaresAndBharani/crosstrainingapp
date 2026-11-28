# UI & Behavioral Contract: Persistent Active Workout Draft & Unified Live Session Dock

**Feature**: `SPEC-032` (`032-persistent-workout-draft`)

## 1. Single-Dock Arbitration Contract (`LiveSessionDockResolver`)

| Current Route | Workout Active? | Timer Active (`phase != IDLE`)? | Global Dock (`LiveSessionDock`) | Inner `SessionEditorBody` `InSessionTimerBar` | `Log` Bottom Tab Badge | Drawer `LOG` `"Active"` Pill |
|---|---|---|---|---|---|---|
| `log` | No | No | `NONE` | Hidden | Hidden | Hidden |
| `log` | Yes | No | `NONE` | Hidden | Hidden | Shown |
| `log` | No | Yes | `NONE` | Shown | Hidden | Hidden |
| `log` | Yes | Yes | `NONE` | Shown | Hidden | Shown |
| `timer` | No | Yes / No | `NONE` | N/A | Hidden | Hidden |
| `timer` | Yes | Yes / No | `WORKOUT_ONLY` | N/A | Shown | Shown |
| `history`, `progress`, `cycles`, `library`, `profile` | No | No | `NONE` | N/A | Hidden | Hidden |
| `history`, `progress`, `cycles`, `library`, `profile` | Yes | No | `WORKOUT_ONLY` | N/A | Shown | Shown |
| `history`, `progress`, `cycles`, `library`, `profile` | No | Yes | `TIMER_ONLY` | N/A | Hidden | Hidden |
| `history`, `progress`, `cycles`, `library`, `profile` | Yes | Yes | `WORKOUT_AND_TIMER` | N/A | Shown | Shown |
| `sessionEditor/{id}/{copy}` | Any | Any | Follows non-Log/non-Timer rules above | **ALWAYS Hidden** | Shown if active | Shown if active |

## 2. Dock Interaction & Navigation Contract

- **Workout Zone Tap** (`WORKOUT_ONLY` / `WORKOUT_AND_TIMER`): Navigates to `"log"` (`saveState = true`, `launchSingleTop = true`, `restoreState = true`). If currently on `"sessionEditor/..."` with `isHistoryEditorDirty == true`, opens unsaved-edits confirmation first.
- **Timer Status Pill Tap**:
  - When `isTimerLinkedToWorkout == true`: calls `viewModel.requestOpenInSessionTimerSheet()` and navigates to `"log"` (expanding `InSessionTimerSheet`).
  - When `isTimerLinkedToWorkout == false` (standalone timer): navigates to `"timer"`.
- **Inline Play/Pause & Finished Dismiss (`X`)**: Calls `timerEngine.pause()` / `timerEngine.start()` + `TimerService.startService(context)` / `timerEngine.stopAndClearAll()` **in place** without navigating away from the current tab.

## 3. Draft Lifecycle & Reset Contracts

- **Debounced Persistence**: Edits trigger a 500ms debounced disk write (`DataModeManager`); `Lifecycle.Event.ON_STOP` and `SessionEditorBody` disposal (when not saved/discarded) call `flushActiveWorkoutDraftNow()` synchronously into `DataModeManager`.
- **Discard Workout (`Log` Top-Bar Overflow `⋮`)**:
  - Menu item `"Discard workout"` is rendered ONLY when `isActiveLogTab && isCurrentDraftActive`.
  - Confirmation dialog warns if a block-linked timer will also stop (`shouldStopTimerOnDraftReset(timerSnapshot, blockKeys, isSaveCompletion = false)`).
  - Confirm stops block-linked timer only (preserving standalone timers where `sourceBlockKey == null`), clears draft memory + disk, increments `activeWorkoutDraftRevision`.
- **Save Session**:
  - Persists session, clears draft memory + disk, increments `activeWorkoutDraftRevision`, and stops timer iff `shouldStopTimerOnDraftReset(timerSnapshot, blockKeys, isSaveCompletion = true)` is true (`FINISHED` or block-linked).
- **Routine Conflict Guard**:
  - When loading a Daily Routine (from `LoadDailyRoutineSheet` or `Library -> Log Routine`) while `loadedAvailableBlocks.isNotEmpty() || blocks.any { !it.isBlankLegacyPlaceholder() }`, prompts: `Replace current` | `Append blocks` | `Cancel`.
