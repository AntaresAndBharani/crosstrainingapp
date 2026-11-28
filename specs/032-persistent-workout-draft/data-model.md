# Data Model: Persistent Active Workout Draft & Unified Live Session Dock

**Feature**: `SPEC-032` (`032-persistent-workout-draft`)  
**Date**: 2026-11-28

## 1. `ActiveWorkoutDraftSnapshot` (`ui/draft/ActiveWorkoutDraft.kt`)

Immutable, schema-versioned representation of the active unsubmitted workout on `LogSessionScreen`:

| Field | Type | Default / Constraints | Purpose |
|---|---|---|---|
| `schemaVersion` | `Int` | `1` (`CURRENT_SCHEMA_VERSION`) | Forward/backward-safe codec validation; unsupported versions decode to `null` |
| `selectedCycleId` | `Long?` | `null` | Selected training cycle override (`null` = active cycle) |
| `dateEpochDay` | `Long` | `LocalDate.now().toEpochDay()` | Session date; drives `isStale = sessionDate < today` |
| `title` | `String` | `""` | Optional session title |
| `notes` | `String` | `""` | Optional session notes |
| `showSessionMeta` | `Boolean` | `false` | Expanded state of Title & Notes section |
| `loadedRoutineId` | `Long?` | `null` | Loaded Daily Routine id (if any) |
| `loadedRoutineTitle` | `String` | `""` | Loaded Daily Routine display title |
| `initialRoutineSignature` | `String` | `""` | Routine structure signature for `"Also update routine template"` diff check |
| `syncRoutineTemplateChecked` | `Boolean` | `false` | Preserved checkbox state for routine template sync |
| `scrollOffsetPx` | `Int` | `0` (`>= 0`) | Exact vertical scroll position in `Log` |
| `loadedBlocks` | `List<ActiveLoadedBlockSnapshot>` | `emptyList()` | Ordered complexes & standalone exercises with `uiKey`, sets, skips, metrics, PRs, and routine item metadata |
| `legacyBlocks` | `List<ActiveLegacyBlockSnapshot>` | `emptyList()` | Ad-hoc / Quick-Add / Quick-Parse blocks & sets |
| `updatedAtEpochMillis` | `Long` | `0L` | Timestamp of last modification |

### Nested Snapshots
- **`ActiveLoadedBlockSnapshot`**: `uiKey` (preserved so `TimerEngine` `sourceBlockKey` link stays intact), `itemKind` (`"BLOCK"` | `"EXERCISE"`), `blockId`, `blockTitle`, `blockInstructions`, `blockExercises: List<ActiveBlockExerciseSnapshot>`, `rawFormat`, `rawScheme`, `rawSection`, `rawKind`, `rawIsCompleted`, `lastSessionDateFormatted`, `hasPreFilledHistory`, `trackedPrSlots`, `prWeightOverrides`, `customWeightOverrideFlags`, `explicitlyAppliedPrKeys`, `sourceRoutineItem: ActiveRoutineItemSnapshot?`, `exerciseSections: List<ActiveExerciseSectionSnapshot>`.
- **`ActiveExerciseSectionSnapshot`**: `exerciseId`, `exerciseName`, `lastSessionDateLabel`, `activeMetric`, `weights`, `perSetReps`, `repsInputs`, `metricInputs`, `groupIndices`, `skippedFlags`, `defaultReps`, `sourceBlockId`, `rawFormat`, `rawScheme`, `rawSection`, `rawKind`, `rawIsCompleted`, `subBlockTitle`, `targetBanner`, `rawNotes`, `libraryMetricType`.
- **`ActiveLegacyBlockSnapshot` & `ActiveLegacySetSnapshot`**: Full `BlockState` and `SetState` fields (`name`, `kind`, `format`, `scheme`, `exerciseId`, `newExerciseName`, `routineId`, `sequenceExerciseIds`, `description`, `resultText`, `resultValue`, `recordRm`, `rmReps`, `rmWeight`, `isExpanded`, `section`, `exerciseIdsCsv`, `subBlock`, `isCompleted`, `sets`).

### Derived State & Rules
- `isMeaningfullyActive()`: `loadedBlocks.isNotEmpty() || legacyBlocks.any { !it.isBlankPlaceholder() } || title.isNotBlank() || notes.isNotBlank() || loadedRoutineId != null`.
- `toSummary(referenceDate: LocalDate)`: produces immutable `ActiveWorkoutSummary(isActive, displayTitle, blockCount, loggedBlockCount, loggedSetCount, sessionDate, isStale, blockKeys)`.

## 2. `LiveDockUiState` (`ui/draft/LiveSessionDockResolver.kt`)

| Field | Type | Description |
|---|---|---|
| `mode` | `LiveDockMode` | `NONE`, `WORKOUT_ONLY`, `TIMER_ONLY`, or `WORKOUT_AND_TIMER` |
| `workoutTitle` | `String` | Display title from `ActiveWorkoutSummary` |
| `workoutSubtitle` | `String` | Concise progress counter (e.g. `"2/4 blocks · 7 sets"` or `"3 blocks ready"`) |
| `isTimerLinkedToWorkout` | `Boolean` | True when active timer `sourceBlockKey` belongs to the active workout draft |
| `showLogNavBadge` | `Boolean` | `true` iff `summary.isActive && currentRoute != "log"` |
| `showDrawerActiveBadge` | `Boolean` | `true` iff `summary.isActive` |
| `showInnerEditorTimerBar` | `Boolean` | `true` iff `currentRoute == "log" && timerActive` (strictly `false` on `sessionEditor/{id}/{copy}`) |
| `workoutAccessibilityLabel` | `String` | Merged TalkBack summary for atomic focus announcement |

## 3. State Transitions

```text
[Idle] ──(load routine / add block / type field)──> [In-Progress (Real or Demo)]
  ▲                                                        │
  │                                                        ├─(tab/drawer/role/rotation)─> [In-Progress (Preserved + Flushed)]
  │                                                        ├─(Demo toggle)───────────────> [Real Parked, Demo Active/Idle]
  ├──(Save Session)────────────────────────────────────────┤  (clears disk draft + stops finished/linked timer)
  └──(⋮ -> Discard workout + Confirm)──────────────────────┘  (clears disk draft + stops linked timer only)
```
