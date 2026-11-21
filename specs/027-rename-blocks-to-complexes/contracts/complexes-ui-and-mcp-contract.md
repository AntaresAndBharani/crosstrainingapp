# Contracts: Android UI Copy & Remote MCP Complex Aliases (`SPEC-027`)

## 1. Android UI Surface Copy Contract

| Surface | Context / Element | Old Copy | New Copy |
| --- | --- | --- | --- |
| **Library Tabs** | Top Tab 2 (`LibraryScreen.kt`) | `Blocks ({count})` | `Complexes ({count})` (`13.sp`, `ScrollableTabRow` if `fontScale >= 1.2f`) |
| **Library Tabs** | Tab 1 / 2 / 3 TalkBack | Default text | `"Routines, {n} items, Tab 1 of 3"` / `"Complexes, {n} items, Tab 2 of 3"` / `"Exercises, {n} items, Tab 3 of 3"` |
| **Library Search** | Tab 0 / 1 / 2 placeholder | `"Search routines, blocks, or exercises..."` / `"Search available blocks or exercises..."` | `"Search routines..."` / `"Search complexes..."` / `"Search exercises..."` |
| **Library Empty** | Routines tab | `"...combine your Blocks and Exercises..."` | `"...combine your Complexes and Exercises..."` |
| **Library Empty** | Complexes tab | `"No available blocks match..."` / `"No available blocks yet..."` | `"No complexes match '$blockSearch'."` / `"No complexes yet. Sync via MCP or tap + to add one."` |
| **Library Snackbar** | Delete Complex | `"Block removed"` | `"Complex removed"` (with `"Undo"`) |
| **Library Card** | Complex card CTA & menu | `"Log Block ▸"` / `"Block options for ${block.title}"` | `"Log Complex ▸"` / `"Complex options for ${block.title}"` |
| **Complex Editor** | Sheet header (`QuickCreateBlockSheet`) | `"New Available Block"` / `"Edit Available Block"` | `"New Complex"` / `"Edit Complex"` |
| **Complex Editor** | Name field | `"Block Name"` (`singleLine = true`) | `"Complex Name"` (`maxLines = 2`) |
| **Complex Editor** | Filter & Validation | `"Filter movements..."` (no exercise guard) | `"Filter exercises..."` + `"Enter a complex name and select at least 1 exercise."` |
| **Complex Editor** | Save CTA | `"Create Block"` / `"Save Block"` | `"Create Complex"` / `"Save Complex"` |
| **Routine Card** | Constituent chip & TalkBack | `"BLOCK: {title}"` / `"Block: {title}"` | `"COMPLEX: {title}"` / `"Complex: {title}"` |
| **Routine Card** | Telemetry & Missing notice | `"1 Block"` / `"{N} Blocks"` / `"1 removed block hidden"` | `"1 Complex"` / `"{N} Complexes"` / `"1 removed complex hidden"` / `"{N} removed complexes hidden"` / `"Dismiss removed complex notice"` |
| **Routine Composer** | Subtitle & Empty | `"Combine reusable Blocks..."` / `"Add at least one Block or Exercise below."` | `"Combine reusable Complexes and standalone Exercises."` / `"No items yet. Tap + Add Complex or + Add Exercise to build this routine."` |
| **Routine Composer** | Item validation | `"Add a routine title and at least 1 block or exercise."` | `"Add a routine title and at least 1 complex or exercise."` |
| **Routine Composer** | Eyebrow badge & CTA | `"BLOCK"` inline / `"Add Block"` | `"COMPLEX"` on eyebrow row above title / `"Add Complex"` (stacked if `fontScale >= 1.4f`) |
| **Routine Composer** | Picker sheet | `"Add Blocks to Routine"` / `"Search available blocks..."` / `"No Available Blocks yet..."` / `"Add 1 Block"` | `"Add Complexes to Routine"` / `"Search complexes..."` / `"No complexes yet. Create complexes in Library → Complexes first."` / `"Add 1 Complex"` / `"Add {N} Complexes"` |
| **Routine Targets** | Stepper label | `"Block Sets (this routine)"` | `"Complex Sets (this routine)"` |
| **Log / Edit Session** | Action bar & Snackbar | `"Add Block"` / `"Add at least one block with content."` | `"Add Complex"` / `"Add at least one complex or exercise with content."` |
| **Log / Edit Session** | Complex card | `"BLOCK: {title}"` / `"Untitled Block"` / `"Remove block {title}"` | `"COMPLEX: {title}"` / `"Untitled Complex"` / `"Remove complex {title}"` |
| **Add Complex Sheet** | Header & Tabs | `"Add Block to Session"` / `"Saved Blocks ({n})"` / `"Custom Block"` | `"Add Complex to Session"` / `"Saved Complexes ({n})"` / `"Custom Complex"` |
| **Add Complex Sheet** | Saved tab & Confirm | `"Search available blocks..."` / `"No saved available blocks yet..."` / `"Switch to Custom Block"` / `"Select Block(s)"` / `"Add 1 Block"` / `"Add {N} Blocks"` | `"Search complexes..."` / `"No saved complexes yet. Build a custom complex right here or save one for future sessions."` / `"Switch to Custom Complex"` / `"Select Complex(es)"` / `"Add 1 Complex"` / `"Add {N} Complexes"` |
| **Add Complex Sheet** | Custom tab | `"Block Name (optional)"` / `"Save to Available Blocks library"` / `"Reuse this block..."` / `"Add Block"` | `"Complex Name (optional)"` / `"Save to Complexes library"` / `"Reuse this complex in future workouts"` / `"Add Complex"` |
| **Load Routine Sheet** | Search & Items | `"Search routines, blocks, or exercises..."` / `"Block(s)"` / `"BLOCK: {title}"` | `"Search routines..."` / `"Complex"` / `"Complexes"` / `"COMPLEX: {title}"` |
| **Record / Track PRs** | Matching exercise tag | `"In this block"` | `"In this complex"` |
| **Progress & Goals** | Mode, Routine, Complex, Exercise views | `"By block"`, `"All blocks"`, `"Available block"`, `"Filter Performance by Block:"`, `"All Blocks (Global)"`, `"Per-Block Routine Breakdown"`, `"Block history"`, `"Block"` | `"By complex"`, `"All complexes"`, `"Complex"`, `"Filter Performance by Complex:"`, `"All Complexes (Global)"`, `"Per-Complex Routine Breakdown"`, `"Complex history"`, `"Complex"`, `"COMPLEX: {title}"`, `"COMPLEX"` |

## 2. Remote MCP Tool & Schema Contract

### Tool Mapping (6 Primary Complex Tools + 6 Legacy Aliases = 45 Total Tools)

| Primary Complex Tool | Legacy Block Alias | Annotations (`readOnly`, `destructive`, `idempotent`, `openWorld`) | Primary Accepted Inputs |
| --- | --- | --- | --- |
| `list_complexes` | `list_available_blocks` | `true, false, true, false` | `query`, `includeLastSession`, `limit` |
| `upsert_complex` | `upsert_available_block` | `false, false, true, false` | `id`, `complex_id`, `complexId`, `block_id`, `title`, `instructions`, `exercises`, `trackedPrs` |
| `batch_upsert_complexes` | `batch_upsert_available_blocks` | `false, false, true, false` | `complexes` (preferred) or `blocks` |
| `delete_complex` | `delete_available_block` | `false, true, true, false` | `complex_id`, `complexId`, `id`, `block_id`, `complex_title`, `title` |
| `log_complex_session` | `log_block_session` | `false, false, true, false` | `date`, `sessionTitle`, `complex_id`, `complexId`, `block_id`, `blockId`, `complex_title`, `complexTitle`, `block_title`, `blockTitle`, `cycleId`, `notes`, `exerciseLogs`, `recordedPrs`, `updateTrackedPrLinks` |
| `update_complex_session_log` | `update_block_session_log` | `false, false, true, false` | `sessionId`, `date`, `complex_id`, `complexId`, `block_id`, `blockId`, `complex_title`, `complexTitle`, `block_title`, `blockTitle`, `session_complex_id`, `session_block_id`, `title`, `notes`, `complex_notes`, `complexNotes`, `blockNotes`, `sync_parent_complex_default_prs`, `sync_parent_block_default_prs`, `cycleId`, `exercises`, `trackedPrs`, `reconcilePrs` |

### Daily Routine Tools Compatibility
- `upsert_daily_routine` & `log_daily_routine_session`:
  - `item_type`: enum `["COMPLEX", "EXERCISE", "BLOCK"]` (normalizes `"COMPLEX"` and `"BLOCK"` identically).
  - Item ref fields: accepts `complex_id` / `block_id` and `complex_title` / `block_title`; `complex_*` wins if both are supplied.
- `list_daily_routines` & `get_daily_routine_progress`:
  - Emits `itemType: "COMPLEX" | "EXERCISE"`, `complexId`, `complexTitle`, `complexCount`, `missingComplexCount` alongside legacy `blockId`, `blockTitle`, `blockCount`, `missingBlockCount`.

### Schema Invariants
- Zero `$ref` anywhere in serialized `tools/list` output.
- Zero `exclusiveMinimum` anywhere in serialized `tools/list` output.
- All tool names match `/^[a-z0-9_]+$/` and length `<= 60`.
