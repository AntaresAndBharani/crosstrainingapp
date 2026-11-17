# Feature Specification: Daily Routines Library, Unified Block & Exercise Tracking, Routine Progress & MCP Parity (`SPEC-025`)

**Feature Branch**: `feat/025-daily-routines-blocks-exercises`  
**Created**: 2026-11-17  
**Status**: Approved  
**Input**: Stakeholder refinement conversation (`Refine Daily Routines Library Specifications` — `SPEC-DAILY-ROUTINES-LIB-01`, `SPEC-ROUTINE-SESSION-TRACK-01`, `SPEC-ROUTINE-PROGRESS-01`, `SPEC-MCP-DAILY-ROUTINES-01`)

## User Scenarios & Testing *(mandatory)*

### User Story 1 - 3-Section Library & Fast Daily Routine Composer (Priority: P1)

As a coach or athlete organizing training in the Library, I want a third **Routines** section alongside **Blocks** and **Exercises** so I can compose reusable Daily Routines from existing Available Blocks and standalone Exercises, browse them clearly on a mobile viewport without tab wrapping, and start logging an entire Daily Routine in one tap.

**Why this priority**: Daily Routines are the primary organizing container uniting reusable complex blocks and standalone exercises; without creating and viewing them in the Library, downstream session tracking and routine-level progress cannot function.

**Independent Test**: Open the Library screen, verify the three top tabs (`Routines (N)`, `Blocks (N)`, `Exercises (N)`) render on a single line without wrapping, create a Daily Routine combining two Available Blocks and two standalone Exercises (with optional exercise notes and reordering), verify its card displays ordered `BLOCK:` and `EX:` chips plus telemetry, duplicate/edit/delete+undo it, and tap `Log Routine ▸`.

**Acceptance Scenarios**:

1. **Given** the Library screen on a compact `360dp` mobile screen, **When** viewing the top tabs, **Then** three single-line tabs appear in order: `Routines ({count})`, `Blocks ({count})`, and `Exercises ({count})` with search filtering scoped cleanly to the active tab.
2. **Given** the `Routines` tab is active and the user taps `+` (or `Edit` from a routine card's `⋮` menu), **When** the Fast Daily Routine Composer opens, **Then** the user can enter a required Routine Title, optional Routine Notes, add blocks via `+ Add Block` (picking from existing Available Blocks), add exercises via `+ Add Exercise` (picking from Exercises or creating a new movement inline, with an optional per-exercise note such as `4x8 @ RPE 8`), reorder (`↑`/`↓`) or remove (`✕`) items, and save only when the title is non-blank and at least 1 block or exercise is included.
3. **Given** a Daily Routine exists with constituent Blocks and Exercises, **When** viewing its card in `Library -> Routines`, **Then** it displays the title, optional notes, ordered constituent chips (`BLOCK: {BlockTitle}` visually distinct from `EX: {ExerciseName}` or `EX: {ExerciseName} ({note})`, collapsing beyond 6 items behind an expandable `+N more` pill), footer telemetry (`{B} Blocks · {E} Exercises · Last: {Date}` and `🔥 {N} tracked PR(s)`), a `Log Routine ▸` button, and a `⋮` menu (`Edit`, `Duplicate`, `Delete` with 4-second `[Undo]` snackbar).
4. **Given** a referenced Available Block is deleted from `Blocks`, **When** viewing a Daily Routine that referenced it, **Then** the routine safely omits the missing block from its active list and surfaces a removable warning badge (`"{N} removed block(s) hidden"`).

---

### User Story 2 - Unified Block & Standalone Exercise Tracking in Log & Edit Session (Priority: P1)

As an athlete logging or editing a workout, I want to load an entire Daily Routine (or add individual Blocks and Exercises on the fly) and track both Blocks and standalone Exercises using the exact same weight-tile and PR-tracking interface, sharing weight history and PRs across the whole app, with the option to sync mid-workout structural additions/removals back to the Daily Routine template.

**Why this priority**: Tracking both complex blocks and standalone exercises consistently during workouts—without reverting to legacy spreadsheet tables—is explicitly highlighted as critical by the stakeholder.

**Independent Test**: Tap `Log Routine ▸` (or select `Load Daily Routine ▾` in `Log Session`), verify all Blocks (`BLOCK: ...`) and standalone Exercises (`EXERCISE: ...`) render with simplified 4-column `Set 1..N (kg)` tiles and `+ Record PR 🔥` chips, confirm pre-fill from prior standalone or routine logs (or 4 empty tiles with `"First time logging — enter set weights"`), add/remove a standalone exercise via `+ Add Exercise`, toggle `[ ] Also update "{Routine Name}" routine template`, save, and re-open in `Edit Session` to confirm standalone exercises hydrate as `EXERCISE: ...` simplified cards.

**Acceptance Scenarios**:

1. **Given** the user taps `Log Routine ▸` on a Daily Routine or selects a routine via `Load Daily Routine ▾` in `Log Session`, **When** the session loads, **Then** all constituent Blocks (`BLOCK: {Title}`) and standalone Exercises (`EXERCISE: {Name}`) appear in routine order using the unified simplified tracking card (4-column `Set 1..N (kg)` tiles, `+ Add Set`, optional `Routine Note` banner, and `Tracked Basic Movement PRs` + `+ Record PR 🔥`).
2. **Given** a fresh `Log Session` screen with no loaded items, **When** the screen opens, **Then** the legacy blank `1 Move...` `SET | REPS | WEIGHT | FLAGS (W / F)` spreadsheet table is NOT shown; the user can tap `Load Daily Routine ▾` at the top or `+ Add Block` / `+ Add Exercise` at the bottom (and the `Rest` chip on `Daily Check-In` renders cleanly on one horizontal line without vertical wrapping).
3. **Given** a Block or standalone Exercise was previously logged anywhere in the app (inside the same Daily Routine, a different Daily Routine, or standalone), **When** added to a new session (or appended in `Edit Session`), **Then** its `Set 1..N` tiles pre-fill from the latest prior session (`"Pre-filled from last session ({Date})"`), whereas an unlogged item starts with 4 empty `Set 1..4` tiles (`"First time logging — enter set weights"`).
4. **Given** a standalone Exercise with `tracksRepMax = true` (or previously recorded PRs) is added to a session, **When** the user enters set weights, **Then** tracked PR chips auto-attach and evaluate live against historical bests (`max(setWeightsKg)`), updating or recording PRs on save and preserving `EXERCISE: {Name}` simplified cards when reopening the session in `Edit Session`.
5. **Given** a Daily Routine was loaded into `Log Session` and the athlete adds or removes a Block or Exercise mid-session, **When** the session item list differs structurally from the loaded routine template, **Then** an unchecked checkbox `[ ] Also update "{Routine Name}" routine template` appears above `+ Add Block` / `+ Add Exercise`; leaving it unchecked keeps the change session-only, while checking it updates the saved Daily Routine template upon saving the session.

---

### User Story 3 - Progress & Goals: By Daily Routine Overview & Drill-Down (Priority: P2)

As an athlete reviewing training progress, I want a `By routine` filter in `Progress & Goals` where I can select any Daily Routine, view a summary card for every Block and Exercise inside that routine, and tap any item card or sub-chip to inspect its full KPIs, Weight Evolution Chart, PR badges, and expandable Set History.

**Why this priority**: Routine-level progress aggregation closes the feedback loop between structured Daily Routines, individual workouts, and progressive overload across both complex blocks and standalone lifts.

**Independent Test**: Open `Progress & Goals`, verify `By routine` is the first chip in the horizontally scrollable mode bar, verify the 0-routines empty state links to `Library -> Routines`, select a populated Daily Routine, verify `All Items ({N})` overview displays summary cards for every constituent Block and Exercise (reflecting unified standalone + routine history, trend delta badges, last sets, PRs, and unlogged states), and tap a card/sub-chip to open its full 2x2 KPI grid, evolution chart, PR list, and expandable set history plus `[ ⬅ All Items ]`.

**Acceptance Scenarios**:

1. **Given** the `Progress & Goals` screen, **When** rendering the top filter rail, **Then** `By routine` appears first in the scrollable filter row alongside `By block`, `By exercise`, `Cycle goals`, `Fat loss`, and `Body weight`.
2. **Given** `By routine` is active and 0 Daily Routines exist, **When** viewing the content area, **Then** an empty state explains `"No daily routines created yet. Create a Daily Routine in Library to track its progress here."` with an action button to navigate directly to `Library -> Routines`.
3. **Given** a Daily Routine is selected in `By routine`, **When** `All Items ({N})` is selected (default), **Then** each constituent Block and Exercise renders a `RoutineConstituentProgressCard` showing `[BLOCK]` or `[EXERCISE]` badge, directional trend delta (`↗ +X kg`, `→ +0.0 kg`, `↘ -X kg`, or `No logs yet`), All-Time Top, Last Top with date, Last Sets summary, Tracked PR pills, and `View Chart ▸` (or `-- kg` placeholders with `"Not logged yet — tap Log Routine in Library to record sets"` if unlogged).
4. **Given** the user taps any constituent card or its sub-chip (`[ BLOCK: ... ]` or `[ EX: ... ]`), **When** transitioning to drill-down mode, **Then** it shows `[ ⬅ All Items ]`, optional routine note/instructions, the 2x2 KPI Grid (`All-time top`, `Last top`, `Last average`, `Last volume`), Weight Evolution Chart(s), Tracked PR badges, and expandable Set History table using unified history across the entire app.

---

### User Story 4 - Remote MCP & Cloud Sync Parity for Daily Routines (Priority: P2)

As an athlete or coach managing programming via AI assistants (Claude, Gemini) and syncing across devices, I want Remote MCP tools to list, create/update, delete, log sessions for, and inspect progress of Daily Routines—and clean Android/Firestore cloud synchronization without collisions against Available Blocks.

**Why this priority**: Dual-client parity (Android + Remote MCP) and reliable cloud synchronization are core architectural pillars of the product.

**Independent Test**: Query MCP `tools/list` to confirm 39 tools with zero `$ref` and zero `exclusiveMinimum`, invoke `upsert_daily_routine`, `list_daily_routines`, `log_daily_routine_session` (including `achieved_prs` and `update_routine_template`), `get_daily_routine_progress`, and `delete_daily_routine` over Streamable HTTP/domain store, verify `list_available_blocks` excludes Daily Routines, verify `get_workout_summary` reports `dailyRoutineCount`, and test Android `UserCloudSyncManager` upload/download round-tripping both a Daily Routine and an Available Block even when sharing a title.

**Acceptance Scenarios**:

1. **Given** an MCP client connects to `/mcp`, **When** listing tools, **Then** `list_daily_routines`, `upsert_daily_routine`, `delete_daily_routine`, `log_daily_routine_session`, and `get_daily_routine_progress` are registered with accurate `ToolAnnotations` and flat Gemini-compatible JSON Schemas (`0` `$ref`, `0` `exclusiveMinimum`).
2. **Given** `upsert_daily_routine` is called with ordered `BLOCK` and `EXERCISE` items, **When** persisted, **Then** `list_daily_routines` returns the ordered items without leaking into `list_available_blocks`, `get_workout_summary` exposes `dailyRoutineCount`, and `Available Block` natural keys remain isolated from `Daily Routine` natural keys even if names match.
3. **Given** `log_daily_routine_session` logs per-item `set_weights_kg`, standalone exercise logs, and `achieved_prs` (optionally with `update_routine_template: true`), **When** executed, **Then** session blocks (`STANDALONE_EXERCISE` for standalone exercises, linked blocks for `BLOCK` items), `rep_maxes`, and any routine template updates persist atomically, and `get_daily_routine_progress` returns unified-history KPIs, daily chart series, last sets, and PRs for every constituent Block and Exercise.
4. **Given** Android Cloud Sync (`uploadUserData` / `downloadUserData`), **When** syncing routines, sessions, exercises, and rep maxes, **Then** Daily Routines (`DAILY_ROUTINE`, `DR_BLOCK`, `DR_EXERCISE`), standalone exercise session blocks (`STANDALONE_EXERCISE`), tombstones, and referenced exercises sync idempotently across devices without resurrecting deletions or collapsing Available Blocks and Daily Routines that share a name.

---

### Edge Cases

- What happens when a user enters only whitespace in a Daily Routine title or creates a Daily Routine with 0 blocks/exercises? Save is disabled with inline helper text (`"Add a routine title and at least 1 block or exercise."`).
- What happens when an Available Block referenced inside a Daily Routine is deleted from `Library -> Blocks`? The Daily Routine omits the deleted block from active rendering and `Log Routine` expansion while displaying a removable `"1 removed block hidden"` (or plural) warning chip.
- What happens when a Daily Routine contains 8 or more Blocks/Exercises? Its `DailyRoutineCard` shows the first 6 chips plus an interactive `+N more` pill to expand all items in-place, while `Log Routine` always loads every active item.
- What happens when a Daily Routine and an Available Block happen to have the exact same title? Natural keys are namespaced (`dr:{title}` vs `ab:{title}`) in local Repository deduplication, Cloud Sync, and MCP store so neither overwrites the other.
- What happens when an Available Block or standalone Exercise inside a Daily Routine has never been logged before? `Log Session` initializes 4 empty weight tiles (`Set 1..4`) with `"First time logging — enter set weights"`, and `Progress -> By routine` displays `-- kg` and `"Not logged yet — tap Log Routine in Library to record sets"`.
- What happens when an athlete adds/removes items during a session loaded from a Daily Routine, saves without checking `"Also update ... routine template"`, vs checking it? Leaving it unchecked keeps the routine template unchanged; checking it updates the saved Daily Routine's ordered item list to match the session's active Blocks and standalone Exercises.
- What happens when a user edits a historical session containing warm-up, failed, grouped, or metcon blocks alongside standalone exercises? Legacy/metcon blocks remain safely editable on `CompactBlockEditor` without data loss, while weight-tracked Available Blocks and `STANDALONE_EXERCISE` blocks render on unified simplified cards.

## Requirements *(mandatory)*

### Functional Requirements

- **FR-001**: `LibraryScreen` MUST expose 3 single-line tabs in order: `Routines ({count})`, `Blocks ({count})`, and `Exercises ({count})`, with tab-specific search filtering and `+` FAB actions for creating a Daily Routine, Available Block, or Exercise respectively.
- **FR-002**: A `DailyRoutine` MUST store a non-blank title, optional notes, and an ordered list of constituent items where each item is either a reference to an existing `AvailableBlock` (`BLOCK`) or a standalone `Exercise` (`EXERCISE`) with an optional short note (e.g., `"4x8 @ RPE 8"`) and optional `defaultTrackedPrs`.
- **FR-003**: Each `DailyRoutineCard` MUST display distinct visual badges for `BLOCK:` vs `EX:` chips, overflow handling (`+N more` beyond 6 items), missing-block warning badges if a referenced block was deleted, footer telemetry (`{B} Blocks · {E} Exercises · Last: {Date}` and `🔥 {N} tracked PR(s)`), `Log Routine ▸` CTA, and `⋮` menu (`Edit`, `Duplicate`, `Delete` with `[Undo]` Snackbar).
- **FR-004**: `DailyRoutineComposerSheet` MUST provide Title, Notes, ordered item rows with `↑`/`↓` reorder, `✕` remove, inline optional Note input on standalone `EXERCISE` items, `+ Add Block` searchable picker, `+ Add Exercise` searchable multi-select picker with inline weighted exercise creation, `imePadding()`, and validation rejecting blank titles or empty item lists.
- **FR-005**: Tapping `Log Routine ▸` or choosing a routine from `Load Daily Routine ▾` at the top of `Log Session` / `Edit Session` MUST load all active constituent Blocks (`BLOCK: {Title}`) and standalone Exercises (`EXERCISE: {Name}`) into the session in routine order.
- **FR-006**: `Log Session` and `Edit Session` MUST render dual bottom buttons `[ + Add Block ]` and `[ + Add Exercise ]`, suppress the legacy blank spreadsheet block on fresh sessions, and render standalone Exercises via `SimplifiedExerciseLogCard` with the same 4/2-column `Set 1..N (kg)` tile grid, `+ Add Set`, optional `Routine Note` box, and `+ Record PR 🔥` interface as `SimplifiedBlockLogCard`.
- **FR-007**: `DailyCheckInCard` MUST format the `Rest` chip on a single horizontal line (`maxLines = 1, softWrap = false`) without vertical letter wrapping on `360dp` viewports.
- **FR-008**: Pre-fill and progress history MUST be unified across the app for both Blocks and standalone Exercises: adding a Block or Exercise pulls `Set 1..N` weights from the latest prior session across any routine or standalone log (`"Pre-filled from last session ({Date})"`), or initializes 4 empty `Set 1..4` tiles (`"First time logging — enter set weights"`) when never logged; `Edit Session` hides the pre-fill subtitle on originally hydrated cards and preserves `EXERCISE: {Name}` styling for `STANDALONE_EXERCISE` blocks.
- **FR-009**: Standalone Exercises with `tracksRepMax = true` or stored RM history MUST auto-attach tracked PR chips (`defaultTrackedPrs`, existing `1..20` RM slots, or default `1RM`), live-sync non-overridden PR chip weights to `max(setWeightsKg)`, and record/reconcile `RepMax` rows on save/update.
- **FR-010**: When a session was loaded from a Daily Routine and its active Block/Exercise items diverge structurally from that routine template, `SessionEditorBody` MUST display `[ ] Also update "{Routine Title}" routine template` (unchecked by default) and persist the updated routine item list to Room and Cloud Sync on save only when checked.
- **FR-011**: `ProgressScreen` MUST include `ProgressMode.BY_DAILY_ROUTINE` (`"By routine"`) as the first chip in the horizontally scrollable mode filter row, while preserving `ProgressMode.BY_ROUTINE` (`"By block"`, excluding Daily Routines) and keeping `AppViewModel.progressMode` defaulting to `ProgressMode.BY_EXERCISE`.
- **FR-012**: In `By routine`, when 0 Daily Routines exist, `ProgressScreen` MUST render an empty state with a button navigating to `Library -> Routines`; when populated, it MUST render a `Select Daily Routine` dropdown, secondary constituent item sub-chips (`All Items ({count})`, `BLOCK: ...`, `EX: ...`), summary cards (`RoutineConstituentProgressCard`) with directional trend badges (`↗`/`→`/`↘`/`No logs yet`), and an interactive drill-down view (`[ ⬅ All Items ]`, 2x2 KPI Grid, `EvolutionCards`, Tracked PR chips, and expandable `BlockHistoryCard`).
- **FR-013**: All Daily Routine, Block, and standalone Exercise data MUST persist inside the existing Room v10 schema (`routines`, `routine_blocks`, `session_blocks`, `block_sets`, `rep_maxes`) without a database schema migration, using namespace-isolated natural keys (`dr:` vs `ab:`) across `Repository`, `UserCloudSyncManager`, and `mcp-server`.
- **FR-014**: `mcp-server` MUST expose 5 flat, Gemini-compatible (`0` `$ref`, `0` `exclusiveMinimum`) MCP tools: `list_daily_routines`, `upsert_daily_routine`, `delete_daily_routine`, `log_daily_routine_session`, and `get_daily_routine_progress`, exclude `DAILY_ROUTINE` entries from `list_available_blocks` / `availableBlockCount`, and expose `dailyRoutineCount` in `get_workout_summary`.

### Key Entities

- **DailyRoutine**: Named composite workout template (`defaultFormat = "DAILY_ROUTINE"`) stored in `routines`/`routine_blocks` containing optional notes, ordered constituent items (`BlockRef` and `StandaloneExercise`), and missing-block count.
- **DailyRoutineItem**: Either `BlockRef(blockId, blockTitle)` referencing an `AvailableBlock` (`DR_BLOCK` / `[dr_item=BLOCK:...]`) or `StandaloneExercise(exerciseId, exerciseName, note, defaultTrackedPrs)` referencing an `Exercise` (`DR_EXERCISE` / `[dr_item=EXERCISE:...]` + `[ab_prs=...]`).
- **StandaloneExerciseSessionBlock**: Logged `SessionBlock` with `format = "STANDALONE_EXERCISE"`, `mainExerciseId` set, `subBlock = ""`, `description = routineNote`, and weight-only `BlockSet` rows.
- **ConstituentProgressSummary**: Pure analytics projection for one item inside a `DailyRoutine`, containing `itemType`, `title`, `subtitle`, `allTimeTop`, `lastTop`, `lastTopDateLabel`, `lastAverage`, `lastVolume`, `topDelta`, `trendDirection`, `lastSetsSummary`, `bestPrBadges`, `performances`, and `dayPerformances`.

## Success Criteria *(mandatory)*

### Measurable Outcomes

- **SC-001**: All 3 Library tabs (`Routines (N)`, `Blocks (N)`, `Exercises (N)`), `DailyCheckInCard` (`Rest` chip), `DailyRoutineCard`, `SimplifiedExerciseLogCard`, and `ProgressScreen` filter bars render without text clipping or vertical letter wrapping on `360dp` viewports up to `fontScale = 1.3f` with `>= 48.dp` interactive touch targets.
- **SC-002**: 100% of Daily Routine round-trip codec rules (`[dr_item=...]`, `[ab_prs=...]`, deleted block omission, namespace isolation vs `AvailableBlock`), unified cross-app pre-fill lookups, routine template sync diff detection, and `DailyRoutine` analytics projections pass deterministic JVM unit tests (`testDebugUnitTest`).
- **SC-003**: 100% of Remote MCP contracts (`tools/list` reporting 39 tools with `0` `$ref` and `0` `exclusiveMinimum`, `list_daily_routines`, `upsert_daily_routine`, `delete_daily_routine`, `log_daily_routine_session`, `get_daily_routine_progress`, and isolation from `list_available_blocks`) pass automated Vitest/Supertest checks (`npm test`).
- **SC-004**: Full pre-PR verification suite (`testDebugUnitTest`, `Invoke-ScriptTests.ps1`, `lintDebug`, `mcp-server` `npm run build && npm test`) and remote PR CI checks pass at 100%.

## Assumptions

- Users maintain weight-based tracking (`kg` or active `weightUnit`) on the simplified set tiles for both Blocks and standalone Exercises, with blank/0 tiles omitted cleanly on save just like `SimplifiedBlockLogCard`.
- Room schema remains at v10; zero SQLite migration is required because existing columns (`Routine.defaultFormat`, `RoutineBlock.format`, `RoutineBlock.notes`, `SessionBlock.format`) carry the non-breaking Daily Routine markers cleanly.
- Existing `DemoData` seed version (`SEED_VERSION = 4`) remains untouched to preserve compatibility with existing backup/demo tests.
