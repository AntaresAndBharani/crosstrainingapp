# Data Model & Domain Mapping: Rename Blocks to Complexes (`SPEC-027`)

**Feature Branch**: `feat/027-rename-blocks-to-complexes`  
**Date**: 2026-11-21

## 1. Persistence Stability (Zero Room / Firestore Migration)

| Concept | Persisted Room / Firestore Identifier (UNCHANGED) | UI & MCP External Terminology (UPDATED) |
| --- | --- | --- |
| Saved Complex template | `RoutineWithBlocks` (`defaultFormat = "AVAILABLE_BLOCK"`), `[ab_prs=...]` in first `RoutineBlock.notes` | **Complex** / **Complexes** (`Edit Complex`, `New Complex`, `Complexes ({count})`) |
| Daily Routine container | `RoutineWithBlocks` (`defaultFormat = "DAILY_ROUTINE"`) | **Daily Routine** (`Combine reusable Complexes and standalone Exercises.`) |
| Daily Routine complex ref | `RoutineBlock` (`format = "DR_BLOCK"`, `[dr_item=BLOCK:id=...:title=...]`, `[rb_v2=...]`) | `COMPLEX` badge, `COMPLEX: {Title}`, `Complex Sets (this routine)` |
| Daily Routine standalone exercise | `RoutineBlock` (`format = "DR_EXERCISE"`, `[dr_item=EXERCISE:...]`, `[rb_v2=...]`) | `EXERCISE` badge, `EX: {Name}` |
| Logged complex session block | `SessionBlock` (`routineId`, `subBlock`, `[log_metric=...]`) + `BlockSet` | `COMPLEX: {Title}`, `Complex history`, `In this complex` |
| Tracked Rep-Max PR link | `RepMax` (`sessionId`, `blockId`, `sourceBlockTitle`) | Tracked PR chips (`🔥 {N} tracked PR(s)`) |

## 2. Android Presentation State & Validation Rules

### Complex Editor (`QuickCreateBlockSheet`)
- **Fields**: `title: String` (`Complex Name`, wraps up to `maxLines = 2`), `instructions: String` (`Instructions`), `selectedIds: SnapshotStateList<Long>` (`Exercises ({N} selected)`).
- **Validation State Transitions**:
  - `canSaveComplexEditor(title, selectedIds.size) == title.trim().isNotEmpty() && selectedIds.isNotEmpty()`
  - When invalid and interacted/submitted: inline error `"Enter a complex name and select at least 1 exercise."`, error border on invalid controls, primary save CTA disabled until both conditions hold.

### Daily Routine Composer (`DailyRoutineComposerState` & `RoutineItemCard`)
- **Fields**: `title`, `notes`, `items: SnapshotStateList<ComposerRoutineItem>`.
- **Validation**: `canSave == title.trim().isNotEmpty() && items.isNotEmpty() && !hasInvalidTargets`. Empty/blank validation error copy: `"Add a routine title and at least 1 complex or exercise."`.
- **Card Row Structure**:
  - **Row 1 (Eyebrow)**: Position (`idx + 1`), Type Badge (`COMPLEX` or `EXERCISE`), Spacer, Reorder Up (`↑`), Reorder Down (`↓`), Remove (`✕`).
  - **Row 2 (Title)**: Full-width item title (`maxLines = 2`, `TextOverflow.Ellipsis`).

## 3. MCP Domain DTO Mapping & Normalization Rules

### Complex Normalization Precedence
For every MCP input accepting both Complex and Block aliases:
- **ID Resolution**: `complex_id ?? complexId ?? id ?? block_id ?? blockId`
- **Title Resolution**: `complex_title ?? complexTitle ?? title ?? block_title ?? blockTitle`
- **Notes Resolution**: `complex_notes ?? complexNotes ?? blockNotes`
- **Batch Array Resolution**: `complexes ?? blocks` (non-empty validation enforced at runtime)
- **Routine Item Type Resolution**: `"COMPLEX"` or `"BLOCK"` → internal `"BLOCK"` storage; `"EXERCISE"` → internal `"EXERCISE"` storage.

### MCP Response Records (Backward-Compatible Dual Keys)
- **Complex Record** (`list_complexes`, `upsert_complex`, `batch_upsert_complexes`):
  - Reuses `AvailableBlockRecord` shape (`id`, `title`, `instructions`, `exercises`, `trackedPrs`, `isAvailableBlockFormat`, `updatedAtMillis`, `lastSession`).
  - Wrapper payloads: `list_complexes` returns `{ count, complexes }`, `upsert_complex` returns `{ status: "ok", complex }`, `batch_upsert_complexes` returns `{ status: "ok", upsertedCount, complexes }`. Legacy tools retain `availableBlocks` / `availableBlock`.
- **Daily Routine Item & Progress Records**:
  - `DailyRoutineItemRecord`: `itemType: "COMPLEX" | "EXERCISE"`, `complexId: number | null`, `complexTitle: string | null`, plus legacy `blockId` and `blockTitle`.
  - `DailyRoutineRecord`: `complexCount: number`, `missingComplexCount: number`, plus legacy `blockCount` and `missingBlockCount`.
  - `DailyRoutineConstituentProgressRecord`: `itemType: "COMPLEX" | "EXERCISE"`, `complexId: number | null` (optional/when applicable), alongside `blockId` if present and existing metrics (`unit`, `allTimeTop`, `lastTop`, `chartPoints`, `recentSessions`).
- **Workout Summary (`get_workout_summary`)**:
  - Adds `complexesCount` matching `availableBlockCount`.
