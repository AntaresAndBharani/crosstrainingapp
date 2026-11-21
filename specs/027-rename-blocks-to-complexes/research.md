# Research: Rename Blocks to Complexes (`SPEC-027`)

**Feature Branch**: `feat/027-rename-blocks-to-complexes`  
**Date**: 2026-11-21

## Research Summary & Architectural Decisions

### 1. Zero-Migration Local & Cloud Storage Compatibility
- **Decision**: Keep all Room entities, table/column names (`routines`, `routine_blocks`, `session_blocks`, `block_sets`), navigation route parameters (`blockId`), Firestore collection paths, and embedded text serialization discriminators (`AVAILABLE_BLOCK_FORMAT = "AVAILABLE_BLOCK"`, `DAILY_ROUTINE_FORMAT = "DAILY_ROUTINE"`, `DAILY_ROUTINE_BLOCK_ITEM_FORMAT = "DR_BLOCK"`, `[ab_prs=...]`, `[dr_item=BLOCK:...]`, `STANDALONE_EXERCISE_SESSION_FORMAT = "STANDALONE_EXERCISE"`) unchanged. Map `Block` → `Complex` strictly at the Android UI/presentation layer and MCP external contract layer.
- **Rationale**: Avoids Room database schema migrations (`AppDatabase.kt`), Firestore security rules edits, or breaking historical workout sync payloads across existing devices and remote MCP sessions.
- **Alternatives Considered**: Renaming `AVAILABLE_BLOCK` / `DR_BLOCK` / Room table names with a Room migration + dual-read Firestore fallback. Rejected per Constitution Principle V ("features that can be derived from existing entities MUST avoid unnecessary schema changes") and stakeholder Option A lock-in.

### 2. Responsive `360dp` Tab Row, Search Bars & Eyebrow Layout Fixes
- **Decision**:
  1. **Library 3-Tab Row**: Reduce tab label font size from `13.5.sp` to `13.sp` with `maxLines = 1`, `softWrap = false`, `fontFeatureSettings = "tnum"`, compact horizontal padding, and automatically upgrade from fixed `TabRow` to `ScrollableTabRow(edgePadding = 12.dp)` when `LocalDensity.current.fontScale >= 1.2f` (tested via pure helper `shouldUseScrollableLibraryTabs(fontScale)`).
  2. **Tab-Scoped Search Placeholders**: Replace `"Search routines, blocks, or exercises..."` and `"Search available blocks or exercises..."` with concise, non-truncating tab-scoped strings (`"Search routines..."`, `"Search complexes..."`, `"Search exercises..."`), and `"Search routines..."` in `LoadDailyRoutineSheet`.
  3. **Complex Name Wrapping**: Change `QuickCreateBlockSheet` `Complex Name` and `CustomBlockTabBody` `Complex Name (optional)` fields from `singleLine = true` to `singleLine = false, minLines = 1, maxLines = 2`, plus `Modifier.navigationBarsPadding().imePadding()` on `QuickCreateBlockSheet`.
  4. **Daily Routine Composer Eyebrow Layout**: Split the crowded single-row `RoutineItemCard` header into Row 1 (`{idx + 1} [ COMPLEX | EXERCISE ]` + `↑`, `↓`, `✕` icons) and Row 2 (full-width `maxLines = 2` item title) so long complex titles no longer truncate to `Snatch + ...`.
  5. **Large-Font Action Stacking (`fontScale >= 1.4f`)**: Use pure helper `shouldStackCompactActions(fontScale)` (`fontScale >= 1.4f`) to stack `[ + Add Complex ]` / `[ + Add Exercise ]` vertically in `DailyRoutineComposerSheet` and `SessionEditor`, and stack `Log Complex ▸` below the card title on `AvailableBlockCard`.
- **Rationale**: Ensures zero clipping or overlap across `360dp` screens up to `fontScale = 1.3f+` (and up to `2.0f`) per Constitution Principle IV.
- **Alternatives Considered**: Abbreviating badge to `CPX:`; rejected by stakeholder in favor of full `COMPLEX` badge + eyebrow layout fix.

### 3. Strict Validation in `New Complex` / `Edit Complex`
- **Decision**: Enforce both non-blank `Complex Name` and `selectedIds.isNotEmpty()` via `canSaveComplexEditor(title, selectedExerciseCount)` in `QuickCreateBlockSheet`, showing `"Enter a complex name and select at least 1 exercise."` via `complexEditorValidationError(...)` when invalid after interaction or submit attempt.
- **Rationale**: Stakeholder explicitly confirmed requiring at least 1 selected exercise when authoring saved Complexes in the UI, matching the Gemini specification matrix, while keeping ad-hoc session `Custom Complex` and MCP `upsert_complex` flexible for backward compatibility.
- **Alternatives Considered**: Keeping exercise selection optional in `QuickCreateBlockSheet`. Rejected per stakeholder clarification.

### 4. Secondary & Legacy Surface Boundary
- **Decision**: Scope UI updates strictly to the 6 primary surfaces (`LibraryScreen.kt`, `QuickCreateBlockSheet`, `DailyRoutineComposerSheet` + `RoutineItemTargetsEditor.kt`, `SessionEditor.kt` + `SimplifiedBlockLogComponents.kt`, `MultiMovementPrPickerSheet`, and `ProgressScreen.kt` outside Fat Loss) plus `AppViewModel.duplicateAvailableBlock` fallback `"Complex"`. Leave `CyclesScreen.kt`, `AppNavigation.kt` periodization label, Fat Loss `"Block Completion"` KPI (`ProgressScreen.kt:706-717`), `QuickAddWorkoutDialog.kt`, `WorkoutJourneyAssistantSheet.kt`, `VoiceWorkoutIngestionSheet.kt`, and `RoutineBuilderSheet.kt` untouched.
- **Rationale**: Stakeholder confirmed periodization/macro-blocks and legacy workout text/voice parsers represent distinct concepts or out-of-scope legacy flows.

### 5. MCP Complex-First Tool Aliases & Flat Gemini-Compatible Zod Schemas
- **Decision**: Register 6 primary Complex tools (`list_complexes`, `upsert_complex`, `batch_upsert_complexes`, `delete_complex`, `log_complex_session`, `update_complex_session_log`) alongside all 6 legacy Block tools (raising registered tool count from `39` to `45`). Extend Daily Routine and Complex schemas with flat optional properties (`complex_id`, `complex_title`, `complexes`, `complex_notes`, `session_complex_id`, `sync_parent_complex_default_prs`, `item_type: ["COMPLEX", "EXERCISE", "BLOCK"]`), ensuring zero `$ref` and zero `exclusiveMinimum` in generated JSON Schemas. Enforce precedence (`complex_*` > `block_*`) in `store.ts`, emit Complex-first response fields (`itemType: "COMPLEX"`, `complexId`, `complexTitle`, `complexCount`, `complexesCount`) while keeping legacy keys (`blockId`, `blockTitle`, `blockCount`, `availableBlockCount`) populated for existing clients.
- **Rationale**: Satisfies Constitution Principle VII (flat schemas, accurate `ToolAnnotations`, backward-compatible atomic mutations) and locked-in Option A.
- **Alternatives Considered**: Deprecating/removing legacy Block tools immediately; rejected because existing AI prompts and automated tests rely on backward compatibility.
