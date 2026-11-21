# Feature Specification: Rename Blocks to Complexes

**Feature Branch**: `feat/027-rename-blocks-to-complexes`

**Created**: 2026-11-21

**Status**: Draft

**Input**: User description: "Rename Blocks to Complex/Complexes app-wide across Library, Complex Editor, Daily Routines, Log/Edit Session, Record/Track PRs, Progress & Goals, and MCP tool/schema aliases while preserving zero-migration local/cloud data compatibility."

## User Scenarios & Testing *(mandatory)*

### User Story 1 - Library Browsing & Complex Creation/Editing (Priority: P1)

Athletes and coaches browse saved multi-movement training complexes in the Library under a dedicated `Complexes ({count})` tab alongside `Routines ({count})` and `Exercises ({count})`, and create or edit a Complex using clean `New Complex` / `Edit Complex` forms without `"Available"` prefixes, clipped names, or cramped tabs on compact screens.

**Why this priority**: The Library and Complex editor are where athletes define and launch multi-movement complexes; establishing clear, non-truncating `Complex` / `Complexes` naming here removes the core conceptual confusion between single exercises and composed complexes.

**Independent Test**: Open Library on a compact `360dp` phone viewport at default and scaled text sizes, inspect all 3 tabs and search bar, create/edit a Complex with a long multi-movement name, verify save validation when name or exercises are missing, duplicate/delete/undo a Complex, and tap `Log Complex ▸`.

**Acceptance Scenarios**:

1. **Given** the Library screen is open on a `360dp` device, **When** viewing the top tabs at standard text scale, **Then** all 3 tabs (`Routines ({count})`, `Complexes ({count})`, `Exercises ({count})`) render on a single line with tabular counts and zero wrapping; **And When** font scale is `1.2x` or higher, the tab row switches cleanly to a scrollable tab layout without clipping text.
2. **Given** the user switches between Library tabs, **When** viewing the search bar and empty states, **Then** placeholders read `"Search routines..."`, `"Search complexes..."`, and `"Search exercises..."` without right-edge truncation, and empty Complex states read `"No complexes yet. Sync via MCP or tap + to add one."` or `"No complexes match '{query}'."`.
3. **Given** the user opens `New Complex` or `Edit Complex`, **When** entering a long title such as `"Clean + Hang Clean + Front Squat Wave"`, **Then** the `Complex Name` field expands up to 2 lines without horizontal clipping, the filter placeholder reads `"Filter exercises..."`, and the user cannot save until both a non-blank Complex Name and at least 1 selected exercise are provided (with inline error `"Enter a complex name and select at least 1 exercise."`).
4. **Given** a saved Complex card in the Library, **When** the user views, logs, duplicates, or deletes it, **Then** the primary CTA reads `"Log Complex ▸"`, overflow accessibility labels reference Complex options, and deleting displays `"Complex removed"` with an `"Undo"` action.

---

### User Story 2 - Composite Daily Routine Composer & Cards (Priority: P1)

Athletes and coaches compose Daily Routines combining reusable Complexes and standalone Exercises in the Daily Routine composer and view them on Routine cards, with full multi-line Complex titles that never truncate next to item reorder/delete controls.

**Why this priority**: Daily Routines directly embed saved Complexes alongside standalone Exercises; fixing both terminology and the crowded single-row title truncation directly resolves the primary usability defect in routine authoring.

**Independent Test**: Open Library -> Routines, create or edit a Daily Routine containing a long-named Complex (e.g., `"Snatch + Hang Snatch + Snatch Balance"`) and a standalone Exercise, verify the eyebrow layout displays the full title on row 2, verify `+ Add Complex` picker flow and targets labeling, and inspect Routine card chips and telemetry counts.

**Acceptance Scenarios**:

1. **Given** a Daily Routine card on the Routines tab, **When** it contains Complexes and standalone Exercises, **Then** constituent chips use `"COMPLEX: {Title}"` (retaining blue differentiation from `"EX: {Name}"`), accessibility labels announce `"Complex: {Title}"`, and footer telemetry formats `"0 Complexes"`, `"1 Complex"`, or `"{N} Complexes"`.
2. **Given** the `New Daily Routine` or `Edit Daily Routine` sheet is open, **When** viewing the subtitle, empty state, and constituent cards, **Then** the subtitle reads `"Combine reusable Complexes and standalone Exercises."`, the empty item placeholder reads `"No items yet. Tap + Add Complex or + Add Exercise to build this routine."`, and each Complex item places `{index} [ COMPLEX ]` plus `↑`, `↓`, `✕` controls on Row 1 (eyebrow) while displaying the full Complex title up to 2 lines across the full card width on Row 2.
3. **Given** the bottom action buttons inside the Daily Routine composer, **When** viewing at normal scale or `fontScale >= 1.4f`, **Then** buttons read `"Add Complex"` and `"Add Exercise"`, stacking vertically at `fontScale >= 1.4f` to prevent label truncation.
4. **Given** the user taps `"Add Complex"` or configures per-routine targets on a Complex item, **When** picking complexes or editing sets, **Then** the picker title reads `"Add Complexes to Routine"`, empty/filter copy references Complexes, confirmation reads `"Add 1 Complex"` / `"Add {N} Complexes"`, and target stepper reads `"Complex Sets (this routine)"`.

---

### User Story 3 - Workout Session Logging, Editing & PR Tracking (Priority: P2)

Athletes logging or editing a workout session and selecting PRs see consistent `Complex` terminology across session cards, the Add Complex modal sheet, Routine loader, and Record / Track PRs sheet without losing any historical session or PR data.

**Why this priority**: Athletes transition seamlessly from Library and Daily Routines into Log Session and Edit Session; consistent Complex terminology prevents mid-workout cognitive friction while keeping all saved sessions intact.

**Independent Test**: Start a workout in Log Session or History -> Edit Session, open `Add Complex to Session`, inspect `Saved Complexes` and `Custom Complex` tabs, load a Daily Routine, open `Record / Track PRs`, and confirm existing historical logs hydrate identically.

**Acceptance Scenarios**:

1. **Given** a session card representing a Complex in Log Session or Edit Session, **When** displayed, **Then** its header reads `"COMPLEX: {Title}"` (or `"COMPLEX: Untitled Complex"` if blank) in the blue complex accent and remove button semantics read `"Remove complex {Title}"`.
2. **Given** the user taps `"Add Complex"` in Log Session or Edit Session, **When** the modal sheet opens, **Then** the header reads `"Add Complex to Session"`, tabs read `"Saved Complexes ({count})"` and `"Custom Complex"`, search reads `"Search complexes..."`, selection confirms with `"Select Complex(es)"` / `"Add 1 Complex"` / `"Add {N} Complexes"`, and the custom form labels `"Complex Name (optional)"`, `"Save to Complexes library"`, `"Reuse this complex in future workouts"`, and `"Add Complex"`.
3. **Given** the user opens `Record / Track PRs` for a Complex card, **When** viewing exercises included in that complex, **Then** the badge beneath matching exercises reads `"In this complex"`.
4. **Given** the user opens `Load Daily Routine` in a session, **When** browsing routines, **Then** the search placeholder reads `"Search routines..."` (zero clipping), subtitles format `"{N} Complex"` / `"{N} Complexes"`, and preview chips show `"COMPLEX: {Title}"`.

---

### User Story 4 - Progress & Goals Analytics Views (Priority: P2)

Athletes inspecting workout history and progression in Progress & Goals see `By complex`, `Complex` filters, `Complex history`, and `[COMPLEX]` badges consistently across Daily Routine drill-downs, Complex progress, and Exercise progress filters.

**Why this priority**: Progress & Goals closes the feedback loop from logging back to performance analysis; keeping `Complex` consistent across mode chips, dropdowns, breakdown cards, and history tables ensures end-to-end terminology alignment.

**Independent Test**: Open Progress & Goals with saved Complexes, Daily Routines, and Exercises; switch across `By routine`, `By complex`, and `By exercise`; inspect dropdowns, filter chips, breakdown sections, and history tables while confirming Fat Loss `"Block Completion"` remains unchanged.

**Acceptance Scenarios**:

1. **Given** the Progress & Goals mode chip row, **When** Complexes or legacy workouts exist, **Then** the mode chip reads `"By complex"`.
2. **Given** `By complex` mode is active, **When** viewing controls and cards, **Then** the selector dropdown reads `"Complex"`, filter controls read `"Filter Performance by Complex:"` and `"All Complexes (Global)"`, breakdown card reads `"Per-Complex Routine Breakdown"`, empty states reference complexes, and the history table reads `"Complex history"` with column `"Complex"`.
3. **Given** `By routine` mode is active, **When** viewing empty state, constituent chips, overview cards, and drill-down headers, **Then** chips show `"COMPLEX: {Title}"`, badges read `"COMPLEX"`, and empty guidance refers to saved Complexes.
4. **Given** `By exercise` mode is active with multiple named complexes for an exercise, **When** viewing filter chips, **Then** the reset chip reads `"All complexes"`.

---

### User Story 5 - Remote MCP Complex-First Tools & Backward-Compatible Aliases (Priority: P2)

AI assistants connected via the Remote MCP Server can create, list, update, delete, and log Complexes and Daily Routines using primary `complex` tool names and parameters (`item_type: "COMPLEX"`, `complex_id`, `complex_title`), while existing integrations calling legacy `block` tool names and parameters (`item_type: "BLOCK"`, `block_id`, `block_title`) continue working seamlessly with identical persistence.

**Why this priority**: Athletes create and log Complexes and Daily Routines via AI assistants in addition to the Android app; adding Complex-first MCP tools and aliases while preserving existing `block` contracts ensures seamless AI workflows without breaking existing client prompts or stored records.

**Independent Test**: Call all 6 primary Complex MCP tools (`list_complexes`, `upsert_complex`, `batch_upsert_complexes`, `delete_complex`, `log_complex_session`, `update_complex_session_log`), call all 6 legacy Block aliases, verify Daily Routine tools with both `item_type: "COMPLEX"` and `item_type: "BLOCK"`, and verify precedence when both `complex_*` and `block_*` fields are sent.

**Acceptance Scenarios**:

1. **Given** an MCP client discovers available tools, **When** listing tools, **Then** the 6 Complex-first tools (`list_complexes`, `upsert_complex`, `batch_upsert_complexes`, `delete_complex`, `log_complex_session`, `update_complex_session_log`) are exposed alongside all 6 legacy Block tools, with flat JSON schemas (zero `$ref` and zero `exclusiveMinimum`), valid tool annotations, and snake_case names at most 60 characters long.
2. **Given** an MCP client calls `upsert_daily_routine` or `log_daily_routine_session`, **When** passing `item_type: "COMPLEX"` with `complex_id` / `complex_title`, **Then** the server persists the routine/session cleanly using the existing storage format and returns `itemType: "COMPLEX"`, `complexId`, `complexTitle`, `complexCount` alongside legacy compatibility keys (`blockId`, `blockTitle`, `blockCount`).
3. **Given** an MCP client passes both `complex_id` and `block_id` (or both `complex_title` and `block_title`), **When** resolving the referenced Complex, **Then** `complex_id` / `complex_title` takes precedence over `block_id` / `block_title`.
4. **Given** an existing MCP client calls legacy `*_available_block` / `*_block_session*` tools or sends `item_type: "BLOCK"`, **When** processed, **Then** every operation succeeds identically and updates the exact same underlying workout records.

---

### Edge Cases

- What happens if existing user data in local storage or cloud sync was created under previous `"Block"` versions? Persisted database tables, format markers, routine item markers, and PR link markers MUST remain unchanged so zero data migration is required and every historical workout, routine, and PR loads seamlessly.
- What happens when a user removes a referenced Complex from the Library while a Daily Routine still links to it? The Daily Routine card displays `"1 removed complex hidden"` (or `"{N} removed complexes hidden"`) with accessible dismiss notice `"Dismiss removed complex notice"`.
- What happens on `360dp` narrow screens or large accessibility font scales (`1.2x` to `2.0x`)? Library tabs switch to horizontal scrolling at `fontScale >= 1.2f`; dual action buttons (`+ Add Complex` / `+ Add Exercise`) stack vertically at `fontScale >= 1.4f`; Complex cards stack `Log Complex ▸` below the title at `fontScale >= 1.4f`; Routine composer Complex titles move to Row 2 across full card width.
- What happens if an athlete submits `New Complex` / `Edit Complex` with a blank name or zero selected exercises? Saving is blocked until both a non-blank name and at least 1 exercise are present, displaying `"Enter a complex name and select at least 1 exercise."`.
- What happens if an existing saved Complex loaded in `Edit Complex` happens to have 0 exercises from legacy creation? Opening it in `Edit Complex` prompts the user to select at least 1 exercise before saving edits, while still allowing it to be viewed and logged from the Library.
- What happens when an MCP client calls `batch_upsert_complexes` or `batch_upsert_available_blocks` with neither `complexes` nor `blocks` populated? The server rejects the call with a clear validation error while accepting either `complexes` or `blocks` (preferring `complexes` if both are present).

## Requirements *(mandatory)*

### Functional Requirements

- **FR-001**: System MUST rename user-facing `"Available Block"` / `"Block"` / `"Blocks"` terminology to `"Complex"` / `"Complexes"` (dropping the `"Available"` prefix) across the 6 primary Android surfaces: Library, Complex Editor (`New Complex` / `Edit Complex`), Daily Routine Composer & Cards, Log/Edit Session, Record/Track PRs, and Progress & Goals.
- **FR-002**: Library top navigation tabs MUST display `"Routines ({count})"`, `"Complexes ({count})"`, and `"Exercises ({count})"` on a single line with tabular numeric formatting, switching automatically to horizontally scrollable tabs when `fontScale >= 1.2f`, and exposing TalkBack announcements `"Complexes, {count} items, Tab 2 of 3"`.
- **FR-003**: Library search bars MUST display non-truncating tab-scoped placeholders (`"Search routines..."`, `"Search complexes..."`, `"Search exercises..."`), and Daily Routine search in sessions MUST display `"Search routines..."`.
- **FR-004**: `New Complex` and `Edit Complex` MUST allow `Complex Name` to wrap up to 2 lines without horizontal clipping, display `"Filter exercises..."` in the exercise filter, and require both a non-blank name and at least 1 selected exercise before saving, showing `"Enter a complex name and select at least 1 exercise."` on invalid input.
- **FR-005**: Library Complex cards MUST display `"Log Complex ▸"` as the primary CTA (stacking cleanly when `fontScale >= 1.4f`), expose `"Complex options for {title}"` for overflow accessibility, and emit `"Complex removed"` with `"Undo"` on delete.
- **FR-006**: Daily Routine cards MUST render constituent Complex chips as `"COMPLEX: {Title}"` with blue visual styling (`#E0F2FE` / `#0369A1` light, `#1E3A5F` / `#7DD3FC` dark), announce `"Complex: {Title}"` to screen readers, display singular/plural telemetry (`"0 Complexes"`, `"1 Complex"`, `"{N} Complexes"`), and show `"1 removed complex hidden"` / `"{N} removed complexes hidden"` for deleted references.
- **FR-007**: Daily Routine composer MUST display `"Combine reusable Complexes and standalone Exercises."`, use an eyebrow row layout (Row 1: index + `COMPLEX`/`EXERCISE` badge + reorder/remove icons; Row 2: full-width up-to-2-line item title), label the Complex sets stepper `"Complex Sets (this routine)"`, provide `"Add Complex"` / `"Add Exercise"` buttons (stacking vertically at `fontScale >= 1.4f`), and update the picker header/empty/confirm copy to Complexes.
- **FR-008**: Log Session and Edit Session MUST display Complex session headers as `"COMPLEX: {Title}"` (fallback `"COMPLEX: Untitled Complex"`), update remove accessibility descriptions to `"Remove complex {Title}"`, display `"Add Complex"` in the bottom action bar, and update the modal sheet to `"Add Complex to Session"` with `"Saved Complexes ({count})"` and `"Custom Complex"` tabs, `"Complex Name (optional)"`, `"Save to Complexes library"`, and `"Reuse this complex in future workouts"`.
- **FR-009**: Record / Track PRs sheet MUST display `"In this complex"` for exercises belonging to the active Complex.
- **FR-010**: Progress & Goals MUST display `"By complex"` in the mode chip row, `"Complex"` dropdown label, `"Filter Performance by Complex:"` and `"All Complexes (Global)"` filters, `"Per-Complex Routine Breakdown"`, `"Complex history"` with `"Complex"` column header, `"All complexes"` in exercise mode, and `"COMPLEX: {Title}"` / `"COMPLEX"` badges in Daily Routine progress.
- **FR-011**: Persisted local database schemas, table/column names, navigation route arguments, cloud collection names, and embedded serialization markers MUST remain unchanged for zero-migration backward compatibility.
- **FR-012**: Secondary and legacy surfaces explicitly excluded from this feature—specifically Training Cycles periodization blocks, Fat Loss `"Block Completion"` KPI, Quick Parse / Quick Add dialog, Workout Journey Assistant, Voice Workout Ingestion, and legacy Routine Builder—MUST remain unchanged.
- **FR-013**: Remote MCP server MUST expose the 6 primary Complex-first tools (`list_complexes`, `upsert_complex`, `batch_upsert_complexes`, `delete_complex`, `log_complex_session`, `update_complex_session_log`) with flat client-compatible JSON schemas and accurate read/write/delete tool annotations while keeping all 6 legacy `block` tools operational as backward-compatible aliases.
- **FR-014**: Remote MCP Daily Routine tools (`upsert_daily_routine`, `log_daily_routine_session`, `get_daily_routine_progress`, `list_daily_routines`) MUST accept `item_type: "COMPLEX"` (preferring `complex_id` and `complex_title`) as well as deprecated `item_type: "BLOCK"` / `block_id` / `block_title`, apply precedence to `complex_*` fields when both are supplied, normalize to existing stored markers, and return Complex-first response fields (`itemType: "COMPLEX"`, `complexId`, `complexTitle`, `complexCount`, `complexesCount`) alongside legacy fields.

### Key Entities

- **Complex**: A reusable multi-movement workout template composed of ordered exercises, optional free-form coaching instructions, and optional default tracked rep-max PR slots (stored compatibly using the existing template record structure).
- **Daily Routine Item (Complex Reference)**: An ordered reference inside a Daily Routine pointing to a saved Complex, optionally overriding routine-level sets count and per-exercise targets without mutating the underlying Complex template.
- **Logged Complex Session Block**: One or more completed movement log entries recorded during a workout session attributed to a saved or session-only Complex, retaining sets, metrics, instructions, and linked rep-max PRs.

## Success Criteria *(mandatory)*

### Measurable Outcomes

- **SC-001**: 100% of user-facing labels, buttons, empty states, Snackbars, and accessibility labels across the 6 targeted Android surfaces (`Library`, `Complex Editor`, `Daily Routine Composer`, `Log/Edit Session`, `Record/Track PRs`, and `Progress & Goals`) use `"Complex"` / `"Complexes"` terminology with zero leftover `"Available Block"` or `"Block"` strings outside explicitly excluded surfaces.
- **SC-002**: On a `360dp`-wide viewport, all 3 Library tab titles and all Library/Routine search placeholders render without horizontal clipping or unintended wrapping, and Complex titles in `Edit Daily Routine` no longer truncate next to `↑` / `↓` / `✕` action buttons.
- **SC-003**: 100% of existing local database, cloud sync, codec hydration, and historical session/routine regression tests pass without any database migration or loss of historical records.
- **SC-004**: Remote MCP server exposes all 45 tools (including 6 primary Complex tools and 6 backward-compatible Block aliases) with zero schema incompatibility keywords (`$ref`, `exclusiveMinimum`), and 100% of automated Android, script, lint, and MCP verification checks pass.

## Assumptions

- Out-of-scope secondary surfaces (`CyclesScreen`, Fat Loss `Block Completion`, `QuickAddWorkoutDialog`, `WorkoutJourneyAssistantSheet`, `VoiceWorkoutIngestionSheet`, and unused `RoutineBuilderSheet`) are intentionally left unchanged per stakeholder confirmation.
- Internal Kotlin/TypeScript identifier names (`AvailableBlock`, `AvailableBlockCodec`, `blockId`) may remain unchanged where refactoring them would risk persistence compatibility or unnecessary churn, provided all user-facing UI strings and MCP external tool/schema contracts follow `Complex` / `Complexes`.
- Legacy multi-movement entries inside the `Exercises` catalog (e.g. `"Clean + Hang Clean + Front Squat + Push to OverHead"`) remain untouched and selectable inside Complexes so no existing workout logs break.
