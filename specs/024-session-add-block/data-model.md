# Data & UI State Model: Unified Add Block Workflow (`SPEC-024`)

**Feature**: `024-session-add-block`  
**Date**: 2026-11-15

> Note: Zero Room database schema (`v10`) or Firestore collection changes are introduced. All new types live in the UI state-holder / helper layer (`SimplifiedBlockLogComponents.kt`, `SessionEditor.kt`) and existing domain models (`AvailableBlock`, `Exercise`).

## 1. `AddBlockSheetTab` & `AddBlockSheetOpenMode`

```kotlin
internal enum class AddBlockSheetTab {
    SAVED_BLOCKS,
    CUSTOM_BLOCK
}

internal enum class AddBlockSheetOpenMode {
    PREFER_SAVED,
    SMART_DEFAULT
}
```

- **Routing Rules (`resolveInitialAddBlockTab`)**:
  - `preferSavedTab == true && availableBlocks.isNotEmpty()` -> `AddBlockSheetTab.SAVED_BLOCKS`
  - `availableBlocks.any { it.id !in alreadyLoadedIds }` -> `AddBlockSheetTab.SAVED_BLOCKS`
  - Otherwise -> `AddBlockSheetTab.CUSTOM_BLOCK`

## 2. Custom Block Submission Rules

- `resolveCustomBlockTitle(rawTitle: String, selectedExercises: List<Exercise>): String`:
  - Returns `rawTitle.trim()` if non-empty; otherwise `selectedExercises.joinToString(" + ") { it.name.trim() }.trim()`.
- `canSubmitCustomBlock(rawTitle: String, selectedExerciseIds: Collection<Long>): Boolean`:
  - Returns `rawTitle.trim().isNotEmpty() || selectedExerciseIds.isNotEmpty()`.
- When `selectedExerciseIds` is empty and `rawTitle.trim()` is non-empty:
  - `Repository.prepareCustomAvailableBlock` resolves an existing `Exercise` matching `cleanTitle` case-insensitively or inserts a new `Exercise(name = cleanTitle, category = ExerciseCategory.BARBELL, metricType = MetricType.WEIGHT, unit = "kg", tracksRepMax = true)`.
- When `saveToLibrary == false` (default):
  - Returns `AvailableBlock(id = 0L, title = cleanTitle, instructions = cleanInstructions, exercises = resolvedExercises, trackedPrs = emptyList())` without inserting a `Routine` row.
- When `saveToLibrary == true`:
  - Persists via `Repository.saveAvailableBlock(candidate)` (`defaultFormat = AVAILABLE_BLOCK_FORMAT`) and returns `candidate.copy(id = savedId)`.

## 3. `LoadedAvailableBlockState` Extensions

- Added `val uiKey: String = UUID.randomUUID().toString()` for deterministic Compose `key(availState.uiKey)` identity and `CardLayoutBounds` lookup on `SessionEditorBody` auto-scroll.
- **Construction Variants**:
  1. `buildHydratedBlockState(seed, exercises, editingSessionId)` — Existing blocks inside `Edit Session`: `isEditMode = true`, `lastSessionDateLabel = null`.
  2. `buildLoadedAvailableBlockState(block, sessions, exercises, weightUnit, isEditMode, editingSessionId)` — Newly appended Saved Blocks (in `Log`, `Copy`, or `Edit`): queries `resolveLastBlockSession(..., excludeSessionId = editingSessionId)`, sets `lastSessionDateLabel = "Pre-filled from last session ({Date})"` when prior weights exist, and preserves `isEditMode` / `editingSessionId` for PR chip evaluation.
  3. `buildCustomAvailableBlockState(block, isEditMode, editingSessionId)` — Session-only Custom Blocks (`block.id == 0L`): initializes each movement with 3 empty weight tiles (`listOf("", "", "")`), `lastSessionDateLabel = null`, `defaultReps = 1`, `subBlockTitle = block.title`.

## 4. `SessionEditorBody` Dirty & Re-hydration State

- Appending any saved block, custom block, or Quick Parse block sets `simplifiedStructuralDirty = true` (activating `isDirty` and `BackHandler(enabled = isEditMode && isDirty)`).
- Re-hydration `LaunchedEffect(isEditMode, editingSessionTarget, availableBlocks, exercises, repMaxes)` exits early when `isDirty || activeAddBlockSheetMode != null`.
- `CompactBlockEditor` `canRemove` becomes `loadedAvailableBlocks.isNotEmpty() || blocks.size > 1`.
