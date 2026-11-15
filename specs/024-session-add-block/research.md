# Research & Design Decisions: Unified Add Block Workflow (`SPEC-024`)

**Feature**: `024-session-add-block`  
**Date**: 2026-11-15

## 1. Root Cause of Unresponsive `+ Add Block` in Edit Session (and Loaded Log Session)

- **Observation**:
  - In `SessionEditor.kt` (`SessionEditorBody`, line 1445), tapping `+ Add Block` executed `blocks.add(BlockState(isCompleted = !isFatLoss))` — appending an empty legacy `BlockState` where `isBlankLegacyPlaceholder() == true`.
  - Meanwhile, line 1255 computed `val showLegacyBlocks = loadedAvailableBlocks.isEmpty() || blocks.any { !it.isBlankLegacyPlaceholder() }`.
  - Whenever at least one simplified block is present (`loadedAvailableBlocks.isNotEmpty()`, which holds for every hydrated strength/Available Block workout in `Edit Session` as well as any session with a loaded block in `Log Session`), adding a blank `BlockState` leaves `blocks.any { !it.isBlankLegacyPlaceholder() } == false`, so `showLegacyBlocks` evaluates to `false` and the UI displays nothing at all.
- **Secondary Defects**:
  - `LoadAvailableBlockSheet` (`SimplifiedBlockLogComponents.kt`, line 953) initialized `selectedIds` with `alreadyLoadedIds`. If all library blocks were already in the session or the user tapped `Load (N)` without toggling a new block, nothing changed; furthermore, `LoadAvailableBlockSheet` offered no way to create a custom exercise block as a simplified weight-tile card.
  - `CompactBlockEditor` computed `canRemove = blocks.size > 1` (`SessionEditor.kt` lines 1295 & 1421), preventing removal of the sole residual legacy block even when `loadedAvailableBlocks.isNotEmpty()`.
  - Edit-mode re-hydration `LaunchedEffect(isEditMode, editingSessionTarget, availableBlocks, exercises, repMaxes)` did not guard against an open Add Block sheet (`activeAddBlockSheetMode != null`), risking mid-sheet resets if `availableBlocks` emitted while `isDirty` was still `false`.

## 2. Unified `AddBlockToSessionSheet` vs Separate Triggers

- **Decision**: Unify `+ Add Block` (bottom action bar) and `Load Block ▾` (header card) onto a single `AddBlockToSessionSheet` modal bottom sheet with two tabs (`Saved Blocks`, `Custom Block`), driven by `AddBlockSheetOpenMode` (`PREFER_SAVED` vs `SMART_DEFAULT`) and `resolveInitialAddBlockTab(...)`.
- **Rationale**: Keeps both entry points consistent, prevents users from hitting a dead-end empty picker when no saved blocks remain unloaded (`SMART_DEFAULT` opens `Custom Block` directly), and guarantees newly appended blocks always render as simplified `SimplifiedBlockLogCard`s (`Set 1..3`, `.`/`,` decimals, `+ Add Set`, `+ Record PR 🔥`).
- **Alternatives Considered**:
  - *Appending a blank legacy `CompactBlockEditor`*: Rejected — causes UX fragmentation between `SimplifiedBlockLogCard` and legacy spreadsheet rows (`SPEC-022` already unified editing around `SimplifiedBlockLogCard`).
  - *Forcing Library round-trip*: Rejected — slows down in-gym ad-hoc additions.

## 3. Session-Only Default vs Library Persistence for Custom Blocks

- **Decision**: Add `Repository.prepareCustomAvailableBlock(rawTitle, instructions, selectedExerciseIds, saveToLibrary)` and `AppViewModel.prepareCustomSessionBlock(...)`. When `saveToLibrary == false` (default), build a transient `AvailableBlock(id = 0L, ...)` without inserting a `Routine` row; when `saveToLibrary == true`, call `saveAvailableBlock(...)` (`id > 0L`) and trigger `UserCloudSyncManager.uploadUserData`.
- **Rationale**: Respects user intent (no library clutter for one-off accessories while giving 1-tap reuse when desired) and preserves Room schema `v10` and `firestore.rules`.
- **Title-only edge case**: If the athlete enters a **Block Name** (e.g., `"Hip Thrust"`) with 0 movement chips selected, `prepareCustomAvailableBlock` resolves or creates a `MetricType.WEIGHT` (`tracksRepMax = true`) exercise matching that title so every appended `SimplifiedBlockLogCard` has an active movement section with 3 weight tiles (`Set 1..3`) and a valid `exerciseId > 0L` for `+ Record PR 🔥`.

## 4. Edit-Mode Pre-Fill Distinction (Hydrated Existing Blocks vs Newly Added Saved Blocks)

- **Decision**: Keep `lastSessionDateLabel = null` inside `buildHydratedBlockState` (blocks already logged in the edited session), update `buildLoadedAvailableBlockState` to accept `isEditMode: Boolean = false, editingSessionId: Long? = null` (passing `excludeSessionId = editingSessionId` to `AvailableBlockCodec.resolveLastBlockSession`), and change `SimplifiedBlockLogCard` to render `exState.lastSessionDateLabel` whenever `!exState.lastSessionDateLabel.isNullOrBlank()`.
- **Rationale**: Existing logged blocks in `Edit Session` keep their clean edited-session appearance without misleading `"Pre-filled from last session"` banners, whereas a newly appended Saved Block in `Edit Session` displays pre-filled weights from its latest prior workout alongside `"Pre-filled from last session ({Date})"`.

## 5. Scroll-to-New-Block Bounds Tracking

- **Decision**: Reuse existing `CardLayoutBounds` and `resolveGoalCardScrollOffset` (`CyclesScreen.kt` / `RoutineBuilderSheet.kt`) inside `SessionEditorBody` with a stable `uiKey` on `LoadedAvailableBlockState`, `Modifier.onGloballyPositioned`, and frame-synchronized `LaunchedEffect(pendingScrollTargetKey)` (`repeat(2) { withFrameNanos { } }` + `scrollState.animateScrollTo`).
- **Rationale**: Reuses proven repo layout-bounds auto-scrolling (`SPEC-014`) with zero custom scroll math duplication.
