# UI & Persistence Contract: Unified Add Block Sheet (`SPEC-024`)

## 1. Entry Points (`SessionEditor.kt`)

| Control | Location | Open Mode | Initial Tab |
|---|---|---|---|
| `+ Add Block` (`OutlinedButton`) | Bottom action bar next to `Quick Parse` | `SMART_DEFAULT` | `SAVED_BLOCKS` if `availableBlocks.any { it.id !in alreadyLoadedIds }`, else `CUSTOM_BLOCK` |
| `Load Block ▾` (`OutlinedButton`) | Top Session Header card | `PREFER_SAVED` | `SAVED_BLOCKS` if `availableBlocks.isNotEmpty()`, else `CUSTOM_BLOCK` |

## 2. `AddBlockToSessionSheet` Contract (`SimplifiedBlockLogComponents.kt`)

- **Container**: `ModalBottomSheet(sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true))` with `Modifier.imePadding()`.
- **Header Tabs**:
  - `Saved Blocks ({availableBlocks.size})` (`>= 48.dp` min height)
  - `Custom Block` (`>= 48.dp` min height)
- **Saved Blocks Tab**:
  - Search input: `"Search available blocks..."` (`AvailableBlockCodec.filterAvailableBlocks`).
  - Selection state `selectedNewIds`: starts empty (`[]`), NEVER pre-populated with `alreadyLoadedIds`.
  - Cards where `block.id in alreadyLoadedIds`: visually muted (`alpha = 0.65f`), non-toggleable, display `"Already in session"` badge/subtitle.
  - Empty library state: displays guidance + `"Switch to Custom Block"` CTA.
  - Primary button: disabled when `selectedNewIds.isEmpty()`; label `"Select Block(s)"` / `"Add 1 Block"` / `"Add {N} Blocks"`.
- **Custom Block Tab**:
  - `Block Name` `OutlinedTextField` (optional when >= 1 exercise selected; dynamic placeholder shows defaulted joined name).
  - `Instructions (optional)` `OutlinedTextField`.
  - `Exercises ({N} selected)` searchable `FilterChip` grid + inline `"New movement name"` / `+ Add` (`MetricType.WEIGHT`).
  - `"Save to Available Blocks library"` row (`>= 48.dp`, `Switch`, default `false`, subtitle `"Reuse this block in future workouts"`, merged TalkBack semantics).
  - Primary button: `"Add Block"`, enabled iff `canSubmitCustomBlock(rawTitle, selectedExerciseIds)`.

## 3. Repository & ViewModel Contract

- `Repository.prepareCustomAvailableBlock(rawTitle, instructions, selectedExerciseIds, saveToLibrary): AvailableBlock?`
  - `saveToLibrary == false`: returns `AvailableBlock(id = 0L, ...)` without touching `routineDao`.
  - `saveToLibrary == true`: saves via `saveAvailableBlock(candidate)` and returns `candidate.copy(id = savedId)`.
- `AppViewModel.prepareCustomSessionBlock(...)`: launches coroutine on `viewModelScope`, invokes `onReady(block)` on main dispatcher, and triggers `UserCloudSyncManager.uploadUserData` when `saveToLibrary == true`.

## 4. Scroll & Persistence Contract

- Newly added block card `uiKey` (or `"legacy_${blocks.lastIndex}"` for `Quick Parse`) triggers frame-synchronized `scrollState.animateScrollTo(resolveGoalCardScrollOffset(...))` via `CardLayoutBounds`.
- Existing session title in `Edit Session` is preserved (`if (!isEditMode && title.isBlank()) title = chosen.title`).
- On Save / Update, `toBlockDrafts(exercises, repMaxes)` produces `BlockDraft`s for both saved (`routineId > 0L`) and session-only custom (`routineId == null`) cards; `inheritedBlockTrackedPrs` naturally ignores `id <= 0L`.
