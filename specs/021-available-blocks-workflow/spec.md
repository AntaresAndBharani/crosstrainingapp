# Feature Specification: Available Blocks Workflow (`SPEC-021`)

**Feature Branch**: `feat/021-available-blocks-workflow`
**Created**: 2026-11-14
**Status**: Approved
**Input**: User description: "Let's implement what is described here: https://share.gemini.google/Z6GPjKaXAkTQ (`SPEC-021`: `SPEC-AVAILABLE-BLOCKS-01`, `SPEC-BLOCK-LOGGING-01`, `SPEC-BLOCK-PROGRESS-01`, `SPEC-MCP-BLOCKS-01`)"

## User Scenarios & Testing *(mandatory)*

### User Story 1 - Available Blocks Library & Quick Management (Priority: P1)

As an athlete whose coach/AI syncs training blocks to the app via MCP (or who wants to create/tweak a block quickly on Android), I want the Library's first tab to show **Available Blocks** instead of multi-step Daily Routines, with instant search, one-tap `Log Block ▸` launch, expand/collapse instructions, tracked PR counts, overflow actions (`Edit`, `Duplicate`, `Delete` + `Undo` Snackbar), and a simple 3-field quick-create sheet.

**Why this priority**: Replacing `Daily Routines` with `Available Blocks` is the primary entry point of the simplified workflow across MCP authoring and gym logging.

**Independent Test**: Open Library → verify Tab 1 reads `Available Blocks ({count})`, filter via search, tap `+` to create a 3-field block, tap `Duplicate`/`Delete`+`Undo`, and tap `Log Block ▸` to jump directly into Log Session with that block pre-loaded.

**Acceptance Scenarios**:

1. **Given** the Library screen is open, **When** viewing the tabs, **Then** the first tab is labeled `Available Blocks ({count})` alongside `Exercises ({count})`, and the search input placeholder reads `"Search available blocks or exercises..."`, filtering cards live by block title, instructions, or linked exercise names.
2. **Given** an `AvailableBlockCard`, **When** rendered, **Then** it displays the title, `Log Block ▸` pill button (`>= 48.dp` touch target height, triggers haptic `CLOCK_TICK`), `⋮` overflow menu (`Edit`, `Duplicate`, `Delete`), expandable prescription `instructions` (`maxLines = 4` collapsed with `Show more`/`Show less` toggle), `FlowRow` exercise pills, footer `"Last: ..."` weights formatted in `tnum` (or `"Not logged yet"`), and `🔥 {N} tracked PR(s)` badge when `trackedPrs` is non-empty.
3. **Given** the user taps `+` on `Available Blocks` (or `Edit` on a card), **When** `QuickCreateBlockSheet` opens, **Then** it presents 3 focused inputs: **Block Name**, **Instructions**, and **Exercises** multi-select chip list (plus inline new exercise creation) without legacy `Score: TIME`, Work/Rest, or multi-block routine hierarchy scaffolding.
4. **Given** the user deletes a block from the `⋮` overflow menu, **When** deletion runs, **Then** the block disappears immediately and a 4-second `"Block removed"` Snackbar with an `[Undo]` action restores it if tapped.

---

### User Story 2 - Weight-Only Gym Session Logging & Auto-Inherited Multi-Movement PRs (Priority: P1)

As an athlete in the gym, I want to load one or multiple Available Blocks (via `Log Block ▸` or `Load Block ▾`), see pre-filled weights from my last session of that block, adjust weights in fast 4-column/2-column weight-only set tiles (including leaving bodyweight/cardio tiles blank), and automatically track/record 1 or more basic movement PRs inherited from previous logs of that block.

**Why this priority**: In-gym frictionless weight entry and multi-movement PR tracking eliminate verbose reps/W/F tables while preserving training progression telemetry.

**Independent Test**: Tap `Log Block ▸` on a block with prior history and linked PRs → verify pre-filled weights, banner date, 4-column weight-only tiles accepting `.` and `,` decimals, live evaluation of `🔥 NEW PR` vs `Best:` reference pill, `+ Record PR 🔥` multi-movement picker (`1RM | 2RM | 3RM | 5RM | 10RM`), `[✕]` persistence on PR link removal, and `"Save Session ({N} New PRs)"` persistence.

**Acceptance Scenarios**:

1. **Given** the Log Session screen, **When** launched via `Log Block ▸` or via `Load Block ▾`, **Then** the selected block(s) load as `BLOCK: {Title}` cards with `[🗑]` remove and rest-timer action, prescription banner (`instructions`), and per-exercise sections.
2. **Given** an exercise in a loaded block has prior history on that block, **When** loaded, **Then** it displays `"Pre-filled from last session ({Date})"` and initializes set tiles from that previous session's set weights; when no history exists, it shows 3 empty weight tiles (`Set 1..3`).
3. **Given** the set grid for an exercise, **When** typing set weights, **Then** tiles are arranged in a 4-column `FlowRow` (2 columns when `LocalDensity.current.fontScale >= 1.4f`) with `minHeight = 64.dp`, accept both `.` and `,` decimal separators, allow blank/empty weights for bodyweight/cardio work without validation errors, and display NO `REPS`, `W`, or `F` columns; tapping `+ Add Set` appends a tile copying the previous non-zero weight (if any).
4. **Given** the block has saved or inherited `(movementId, rmReps)` tracked PR links, **When** set weights change, **Then** each link evaluates live against the athlete's stored best `RepMax`: showing an active green `🔥 NEW PR: {rm}RM {Movement} · {weight} {unit}` chip when `maxWeight > storedBest && maxWeight > 0`, or a muted reference pill `{rm}RM {Movement} · Best: {storedBest} {unit}` otherwise; tapping `[✕]` removes the link from the session AND removes it from the Available Block's persisted `trackedPrs`; tapping `+ Record PR 🔥` opens `MultiMovementPrPickerSheet` with searchable `tracksRepMax` movements, multi-select checkboxes, `1RM | 2RM | 3RM | 5RM | 10RM` selector, weight field pre-filled with `maxSetWeight`, and `"Add {N} PR(s)"` CTA; and the primary save button displays `"Save Session ({N} New PRs)"` when `N > 0` or `"Save Session"` otherwise.

---

### User Story 3 - Progress & Goals Propagation (`By block` & `By exercise` PR Attribution) (Priority: P2)

As an athlete reviewing progress, I want my Available Block sessions and multi-movement PRs to automatically feed `Progress → By block` KPIs, evolution charts, expandable block history, `By exercise` Personal Records with block attribution, and `Cycle goals`.

**Why this priority**: Closes the feedback loop between gym logging / MCP logging and visual performance tracking without duplicating analytics pipelines.

**Independent Test**: Save a session for Block A containing weights and a `3RM` PR for Clean & Jerk, then open Progress → verify `By block` chip shows KPIs, chart, and expandable `Block history` with set weights + PR badge, and `By exercise` shows the `3RM` pill attributed to `"Achieved in {Block A} · {Date}"`.

**Acceptance Scenarios**:

1. **Given** Progress screen filter chips, **When** viewing mode selectors, **Then** the routine filter chip label reads `"By block"` (while preserving internal `ProgressMode.BY_ROUTINE` enum compatibility).
2. **Given** `"By block"` mode, **When** selecting an Available Block, **Then** its instructions/movements header, 2×2 KPI grid (`All-time top`, `Last top`, `Last average`, `Last volume`), Top/Avg evolution chart, and expandable `Block history` rows (displaying per-set weights and achieved PR badges) render cleanly without bleeding sessions from other blocks that merely happen to share a movement.
3. **Given** `"By exercise"` mode for a movement with PRs logged via an Available Block, **When** viewing its `"Personal records"` section, **Then** each RM pill shows the best weight and source block attribution (`"Achieved in {Block} · {Date}"`), and `Cycle goals` updates automatically when PR weights meet or surpass target goals.

---

### User Story 4 - Remote MCP Available Blocks & Block Session Logging (Priority: P1)

As an AI training coach or user via Claude/Gemini MCP, I want dedicated tools to list, upsert, batch-upsert, delete Available Blocks and log weight-only block sessions (with multi-movement PR recording), updating `trackedPrs` and `get_workout_summary` cleanly.

**Why this priority**: Enables MCP-first block authoring so blocks authored in chat appear immediately in Library → Available Blocks on Android.

**Independent Test**: Call `upsert_available_block`, `batch_upsert_available_blocks`, `list_available_blocks`, `log_block_session`, and `delete_available_block` via MCP test suite; verify flat schemas (`0` `$ref`, `0` `exclusiveMinimum`), annotations, idempotency, summary count, and Firestore persistence.

**Acceptance Scenarios**:

1. **Given** the Remote MCP server (`33` total tools after adding `5` new tools), **When** calling `list_tools`, **Then** `list_available_blocks`, `upsert_available_block`, `batch_upsert_available_blocks`, `delete_available_block`, and `log_block_session` are registered with accurate `ToolAnnotations` and flat Gemini-compatible JSON schemas (`0` `$ref`, `0` `exclusiveMinimum`).
2. **Given** `upsert_available_block` or `batch_upsert_available_blocks`, **When** invoked with `title`, `instructions`, `exercises` (names/IDs), and optional `trackedPrs`, **Then** missing exercises are auto-created idempotently, the block is stored with `defaultFormat = "AVAILABLE_BLOCK"` + `[ab_prs=...]` encoding compatible with Android sync, and repeated calls with the same ID/title remain idempotent.
3. **Given** `log_block_session`, **When** invoked with `date`, `blockId` or `blockTitle`, per-exercise numeric weight arrays, and optional `recordedPrs` (`movementName`, `rmReps`, `weight`), **Then** weight-only sets (`reps = 1`, positive numeric weight or omitted) and `rep_maxes` entries (linked to `sessionId`, `blockId`, `cycleId`, `sourceBlockTitle`, `sessionTitle`) are persisted transactionally, and any newly logged `(movementId, rmReps)` links are inherited into the block's `trackedPrs`.

### Edge Cases

- **Decimal separators**: User enters `"82,5"` or `"82.5"` → both parse reliably to `82.5` without locale exceptions.
- **Blank bodyweight/cardio sets**: Leaving all set tiles blank or `0` on an exercise does not raise validation errors, does not pollute non-zero `average` weight calculations, and still saves the block/session presence.
- **Firestore rules & Room schema stability**: Because production `firestore.rules` strictly permits only the 7 existing `/data/{docId}` collections (`routines`, `sessions`, `rep_maxes`, etc.) and `RoomSchemaExportTest` pins Room `v10`, Available Blocks persist within `RoutineWithBlocks` (`defaultFormat = "AVAILABLE_BLOCK"`) using backward-compatible `[ab_prs=...]` metadata inside `RoutineBlock.notes` rather than adding a new Room table or Firestore document name.
- **Routine ID mismatch on cloud download / deleted block**: Cloud session download resolves remote `routineId` safely against local `routines` (setting `null` when absent so SQLite FK constraints never fail), while `routineName` / `subBlock` preserves `"By block"` attribution.
- **Updating an existing session preserves PR `blockId`**: Deleting/re-inserting `SessionBlock` rows in `Repository.updateSession` re-links surviving `RepMax` rows to the regenerated `SessionBlock.id` rather than leaving orphaned `blockId = null`.
- **Same movement in two different blocks**: `ProgressAnalytics.routineBlockPerformances` isolates each block's history (`b.routineId == routineId` or `b.subBlock == routine.name`) and only applies the legacy `b.mainExerciseId == routine.mainExerciseId` fallback to untitled legacy blocks (`b.routineId == null && b.subBlock.isBlank()`).

## Requirements *(mandatory)*

### Functional Requirements

- **FR-001**: System MUST store and decode `AvailableBlock` (`id`, `title`, `instructions`, `exercises: List<AvailableBlockExercise>`, `trackedPrs: List<TrackedPrSlot>`) inside `RoutineWithBlocks` (`defaultFormat = "AVAILABLE_BLOCK"`) with `[ab_prs=...]` metadata stripped cleanly from human-visible instructions.
- **FR-002**: `LibraryScreen` MUST replace `"Daily Routines ({count})"` with `"Available Blocks ({count})"`, support search across title/instructions/exercises, provide `AvailableBlockCard` with `Log Block ▸` CTA (`>= 48.dp` height, haptic tick), `⋮` overflow (`Edit`, `Duplicate`, `Delete` + 4s `"Block removed"` `[Undo]` Snackbar), expandable instructions, movement pills, `"Last: ..."` `tnum` weights, `🔥 {N} tracked PR(s)` badge, and `QuickCreateBlockSheet` (3 fields).
- **FR-003**: `LogSessionScreen` / `SessionEditor` MUST support loading blocks via `Log Block ▸` handoff or `Load Block ▾` multi-select picker, pre-filling per-exercise weights from the most recent logged session of that block (`"Pre-filled from last session ({Date})"`), and providing weight-only 4-column (2-column at `fontScale >= 1.4f`) `FlowRow` set tiles (`minHeight = 64.dp`, `.` and `,` decimal support, blank-safe bodyweight handling, `+ Add Set`).
- **FR-004**: System MUST auto-inherit saved `(movementId, rmReps)` links per Available Block, evaluate live PR status against stored best `RepMax` (`🔥 NEW PR` vs `Best:` reference pill), persist `[✕]` link removals onto the Available Block's `trackedPrs`, provide `MultiMovementPrPickerSheet` (`+ Record PR 🔥` with `1RM | 2RM | 3RM | 5RM | 10RM`), save multiple `RepMax` entries per block in a single transaction, and label the CTA `"Save Session ({N} New PRs)"` when active PRs are queued.
- **FR-005**: `ProgressScreen` MUST label the block mode chip `"By block"` (retaining `ProgressMode.BY_ROUTINE`), render clean block telemetry (`All-time top`, `Last top`, `Last average`, `Last volume`, Top/Avg chart, expandable `Block history` with set weights and PR badges), and display `"Personal records"` RM pills with source block attribution (`"Achieved in {Block} · {Date}"`) in `"By exercise"`.
- **FR-006**: `UserCloudSyncManager` MUST preserve `AVAILABLE_BLOCK` blocks and `[ab_prs=...]` movement references across upload/download, avoid FK failures on unknown remote `routineId`, and sync `sourceBlockTitle` / `sessionTitle` metadata on `rep_maxes`.
- **FR-007**: `mcp-server` MUST expose `list_available_blocks`, `upsert_available_block`, `batch_upsert_available_blocks`, `delete_available_block`, and `log_block_session` (33 total tools) using flat Zod schemas (`0` `$ref`, `0` `exclusiveMinimum`), accurate `ToolAnnotations`, transactional Firestore mutations, updated `get_workout_summary` block counts, and updated `CROSSTRAINING_MCP_INSTRUCTIONS`.

### Key Entities

- **AvailableBlock**: Reusable coaching prescription (`id`, `title`, `instructions`, `exercises`, `trackedPrs`) stored via `RoutineWithBlocks` (`defaultFormat = "AVAILABLE_BLOCK"`).
- **TrackedPrSlot**: A movement + RM target pair (`movementId`, `movementName`, `rmReps`) attached to an `AvailableBlock` for automatic inheritance across future gym sessions.
- **BlockInsert / BlockDraft**: Extended to carry `newRepMaxes: List<RepMax>` alongside legacy `newRepMax: RepMax?` so multiple PRs in a single complex/block save atomically.

## Success Criteria *(mandatory)*

### Measurable Outcomes

- **SC-001**: 100% of Android unit tests (`.\gradlew.bat testDebugUnitTest --no-daemon`), PowerShell static tests (`.\scripts\tests\Invoke-ScriptTests.ps1`), Android Lint (`.\gradlew.bat lintDebug --no-daemon`), and MCP build + tests (`npm run build && npm test`) pass without failures or skipped assertions.
- **SC-002**: Zero changes to Room database version (`v10`) or `firestore.rules` collections, preserving seamless compatibility with live Firestore environments and existing `BackupCsv` v5 exports.
- **SC-003**: MCP `list_tools` exposes all 33 tools with `0` `$ref` and `0` `exclusiveMinimum` violations across schemas.
- **SC-004**: Remote GitHub Actions PR checks (`build.yml` & `pr-review.yml`) pass 100% on the opened PR before completion.

## Assumptions

- `ProgressMode.BY_ROUTINE` enum symbol name is retained internally (display label updated to `"By block"`) so existing navigation/trend/scroll tests continue compiling cleanly.
- Existing non-`AVAILABLE_BLOCK` `Routine` entities remain readable for historical sessions and sync backward compatibility.
