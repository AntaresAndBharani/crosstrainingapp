# Feature Specification: Daily Routine Builder Redesign & Multi-Metric Per-Set Targets

**Feature Branch**: `feat/019-routine-builder-redesign`

**Created**: 2026-11-12

**Status**: Draft

**Input**: User description: "Implement SPEC-ROUTINE-BUILDER-02 from https://share.gemini.google/va7fVhE7cupO — friendly visual Daily Routine Builder with 4-tier collapsible sections, fixed integer block sets with format-driven defaults, per-exercise mixed metrics (Reps, Calories, Time, Meters), fixed or variable per-set targets (including 0 / Skip), sub-block expansion in workout logging, and remote assistant / MCP parity."

## User Scenarios & Testing *(mandatory)*

### User Story 1 - Build & Edit Collapsible Multi-Block Routines with Visual Controls (Priority: P1)

An athlete or coach opens the Daily Routine Builder to create or edit a multi-block workout without scrolling through tall walls of plain text boxes. Every level of the builder (Routine Info, each Routine Block, Block Setup, and the Exercises list / individual Exercise cards) can be collapsed into compact single-row badge summaries and re-expanded at any time. Block configuration uses tactile visual chips and a fixed integer set stepper rather than raw text inputs or block-level repetition fields.

**Why this priority**: Replacing the uncollapsible text-box wall with friendly visual pickers and a 4-tier collapsible hierarchy is the foundational user experience improvement requested for building daily routines.

**Independent Test**: Open "New Daily Routine" from the Daily Routines library, enter a routine name, configure Block 1's type, execution format, and fixed sets using visual chips and steppers, add a second block, verify Block 1 collapses automatically into a summary badge row, tap "Collapse All", re-expand any section, and save the routine.

**Acceptance Scenarios**:

1. **Given** the user taps the action to create a new daily routine, **When** the Routine Builder opens, **Then** Routine Info and Block 1 open expanded (defaulting to Weightlifting, Sets x Reps, 4 Sets), and "Save Routine" remains disabled until a non-blank routine name and at least one valid exercise exist.
2. **Given** Routine Info has a non-blank workout name, **When** the user taps "Done ▲" on Routine Info or interacts inside a Routine Block, **Then** Routine Info collapses into a single-row summary card showing the routine name and a expand indicator.
3. **Given** Block 1 is expanded, **When** the user adds Block 2 or expands another collapsed block, **Then** the previously expanded block automatically collapses into a single-row summary displaying its block icon, block name, format badge, set count badge, and exercise count badge.
4. **Given** multiple sections or blocks are expanded, **When** the user taps "Collapse All", **Then** Routine Info and all Routine Blocks collapse into their single-row summary cards.
5. **Given** the user is configuring Block Setup, **When** the user selects a single-effort execution format (`AMRAP`, `For Time`, `Chipper`, `Death by`, or `Time cap`), **Then** the block's fixed Sets stepper automatically defaults to `1 Set` (while remaining unlocked so the user can still step up to 2 or more sets); selecting `Tabata` defaults to `8 Sets`; and selecting structured interval/strength formats (`Sets x Reps`, `EMOM`, `E2MOM`, `E3MOM`, `Rounds for Time`, `Work/Rest Intervals`, `Calorie Sprints`, `Distance Sprints`, `Wave`, `Tempo`) defaults to `4 Sets`.
6. **Given** the user has made unsaved edits in the Routine Builder, **When** the user taps "Cancel" or triggers system back navigation, **Then** a confirmation prompt ("Discard unsaved routine?") prevents accidental loss of work.

---

### User Story 2 - Configure Per-Exercise Mixed Metrics & Variable Per-Set Targets Including 0 / Skip (Priority: P1)

Inside any Routine Block, an athlete or coach adds one or more movements via a unified inline search-and-create bar and configures each exercise's own target metric (`Reps`, `Calories`, `Time`, or `Meters`) and target mode (`Fixed` across all sets or `Per-Set` variable values across `Set 1 … Set N`, including `0` to skip that exercise on specific sets). No repetition scheme lives on the block container itself.

**Why this priority**: Moving fixed vs. variable targets (including `0 / Skip`) and multi-metric support (`Reps`, `Calories`, `Time`, `Meters`) from the block level into each individual exercise is the core domain model change enabling real mixed-modality conditioning and wave/complex programming.

**Independent Test**: Add Bench Press, Air Bike, and Concept2 Row to a 4-set E3MOM block; verify auto-defaulted metrics (`Reps`, `Calories`, `Meters`), set Air Bike to Per-Set `[15, 15, 12, 0]` cal and Row to Per-Set `[250, 250, 200, 0]` m, increment block sets from 4 to 5 and verify Set 5 copies Set 4's value, save, and verify the Library Routine Card and reopened editor preserve every value.

**Acceptance Scenarios**:

1. **Given** a block has `N = 4` fixed sets, **When** the user searches and selects library exercises (e.g., Bench Press, Air Bike, Plank, Concept2 Row), **Then** each exercise initializes its target metric automatically from its library measurement type (`Reps`, `Calories`, `Time`, or `Meters`), and the user can override the metric in one tap while preserving previously entered values per metric in editor state.
2. **Given** the user types an exercise name in the inline search bar that does not match an existing movement, **When** the user taps `+ Create & Add "{query}"`, **Then** the custom exercise is created, appended to the block defaulting to `Reps`, and its exercise card opens immediately for target configuration.
3. **Given** an exercise is switched from `Fixed` mode to `Per-Set` mode inside a block with `N` sets, **When** the `Per-Set` grid renders, **Then** `N` interactive set cards (`Set 1` … `Set N`) are initialized with the exercise's current Fixed value so the user only adjusts sets that differ.
4. **Given** an exercise is in `Per-Set` mode, **When** a set's value is stepped down or edited to `0`, **Then** that set card renders in a distinct skipped visual state (`0 (Skip)`, `0 cal (Skip)`, `0s (Skip)`, or `0m (Skip)`) with non-color indicators (dashed border and explicit `(Skip)` text), and collapsed exercise badges display `15 • 15 • 12 • 0 cal` (or `8 reps / set` in Fixed mode).
5. **Given** an exercise in `Per-Set` mode has values `[12, 10, 8, 0]` for 4 sets, **When** the user increases the block's fixed Sets count from `4` to `5`, **Then** `Set 5` is appended and automatically copies `Set 4`'s value (`0`); when decreased from `4` to `3`, active sets trim to `[12, 10, 8]`.
6. **Given** an exercise has `0` across every set (`Fixed = 0` or `Per-Set = [0, 0, ..., 0]`), **When** the user taps `Done ▲` on the exercise or taps `Save Routine`, **Then** the exercise card stays expanded with an error highlight and inline guidance ("Set a target above 0 on at least one set, or remove this exercise"), blocking save until corrected or removed.
7. **Given** a block or exercise is deleted via its trash action, **When** removal occurs, **Then** remaining items re-number cleanly and an Undo notification allows restoring the deleted block or exercise within 4 seconds; exercises and blocks also support reordering up/down without deleting and re-adding.

---

### User Story 3 - Load Multi-Exercise Mixed-Metric Routines into Workout Logging (Priority: P2)

When an athlete starts logging a workout session and loads a saved Daily Routine from the routine picker, blocks containing multiple exercises with mixed metrics and/or `0 (Skip)` sets expand cleanly into a grouped Sub-Block in the session logger—giving each exercise its own trackable movement card pre-populated with its active (non-skipped) sets, round numbers, and appropriate metric fields.

**Why this priority**: A routine template only delivers end-to-end training value if loading it into a live workout session preserves every movement's metric, per-set targets, and skipped-set schedule.

**Independent Test**: Save a routine containing a 4-set E3MOM block with Bench Press (`8 reps / set`), Air Bike (`15 • 15 • 12 • 0 cal`), and Concept2 Row (`250 • 250 • 200 • 0m`), open Log Session, select the routine in "Load Routine...", and verify a grouped Sub-Block is created with 3 movement cards where Air Bike and Row each pre-populate 3 active sets (rounds 1–3, omitting skipped round 4) with their target calorie/meter values.

**Acceptance Scenarios**:

1. **Given** a saved routine block has multiple exercises configured in the Routine Builder, **When** the athlete loads that routine in Log Session, **Then** the block expands into a grouped Sub-Block container (titled with the block's name, format badge, and total round count) containing one movement entry per exercise in sequence order.
2. **Given** an exercise in a loaded routine block has `Per-Set` targets `[15, 15, 12, 0]` in `Calories` (or `Meters` / `Time`), **When** loaded into Log Session, **Then** the `0 (Skip)` set on round 4 is omitted, rounds 1–3 are created with round numbers `1`, `2`, `3` preserved, and each set's target value (`15`, `15`, `12`) is pre-populated in the movement's metric value column (while `Reps` exercises pre-populate the `reps` column and leave working weight ready for athlete input).
3. **Given** a saved routine block has a single exercise (with Fixed or Per-Set targets including `0 / Skip`), **When** loaded into Log Session, **Then** a standalone movement block is created with non-zero sets pre-populated and round indices preserved whenever any set was skipped.

---

### User Story 4 - Remote AI Assistant (MCP), Cloud Sync, Backup & Workout Sharing Parity (Priority: P2)

An athlete or coach creating, listing, or updating daily routines through the remote AI assistant connector—or syncing across devices, exporting/importing backups, or sharing workout codes—can read and write structured per-exercise mixed-metric Fixed and Per-Set targets (including `0 / Skip`) without losing fidelity or breaking backward compatibility with existing routines.

**Why this priority**: Athletes use both the mobile app and connected AI assistants (Gemini, Claude, ChatGPT) to program daily routines; both channels must speak the exact same per-exercise routine model.

**Independent Test**: Create a mixed-metric per-set routine via the remote assistant `upsert_routine` tool, run cloud sync on mobile, open the routine in the redesigned Routine Builder and verify all per-exercise metrics and per-set `0 (Skip)` slots appear accurately; edit and save on mobile, sync back, and verify `list_routines` returns the updated structured per-exercise targets.

**Acceptance Scenarios**:

1. **Given** an AI client calls `upsert_routine` with block exercises specifying per-exercise `metric` (`REPS`, `CALORIES`, `TIME_SECONDS`, `METERS`), `mode` (`FIXED` or `PER_SET`), `fixedValue`, or `perSetValues` (including `0`), **When** the routine is stored and synced to the mobile app, **Then** missing exercises are automatically created with matching measurement types, block set counts and per-exercise targets round-trip losslessly into the mobile Routine Builder, and all-zero exercises are rejected with a clear validation message.
2. **Given** a routine with mixed-metric per-set exercises is saved on mobile and synced to the cloud (or exported to backup / shared via workout code), **When** `list_routines` is called on the remote assistant (or the backup/share code is imported), **Then** every block exposes both its structured per-exercise target array and a clean human-readable summary scheme without leaking raw internal metadata headers into user-facing notes.
3. **Given** legacy routines exist (using single block-level rep schemes such as `5`, `21-15-9`, `3-2-1-3-2-1-3-2-1-1-1-1`, or `1 + 1`), **When** opened in the redesigned Routine Builder, listed via the remote assistant, or loaded into Log Session, **Then** they parse non-destructively into equivalent Fixed or Per-Set exercise configurations without migration errors or data loss.

---

### Edge Cases

- **Compact 360dp viewports and large OS font scaling (>= 140% up to 200%)**: The 4-option Target Metric selector (`Reps`, `Calories`, `Time`, `Meters`) must never wrap awkwardly mid-word on 360dp screens (scrolling horizontally at large font scales), and the Per-Set grid must automatically step down from 4 columns to 2 columns at font scales `>= 1.4x` (and expand to 6 columns on tablet/unfolded widths `>= 600dp`).
- **High set counts in Per-Set mode (e.g., 8-set Tabata or 12-set EMOM wave)**: Set cards wrap cleanly across rows of 4 without horizontal clipping, and quick pattern helpers (`Copy Set 1 to All`, `Descending`, `Alternate 0s`) allow configuring multi-set waves or alternating EMOM minutes without tapping 12 individual cards.
- **Repeated exercise in the same block sequence**: A complex or conditioning sequence such as `Shuttle Run -> Burpees -> Shuttle Run` allows adding the same library exercise more than once in the same block, with each sequence entry maintaining its own independent target state and unique slot identity.
- **Switching an exercise's metric back and forth**: If a user configures `250m` under `Meters`, taps `Calories` to inspect options, and taps back to `Meters`, the previously entered `250m` Fixed/Per-Set values are restored from in-editor per-metric cache rather than wiped.
- **Deleting the last remaining block**: If only 1 block exists in the editor and the user deletes it (or deletes all exercises), the editor either keeps an empty starter block or allows adding a block while keeping `Save Routine` disabled until at least 1 block with 1 valid exercise exists, with full Undo support.
- **Keyboard / IME occlusion inside nested cards**: Focusing the inline exercise search input or tapping a Fixed/Per-Set numeric value brings the active card above the on-screen keyboard while keeping the sticky footer accessible.

## Requirements *(mandatory)*

### Functional Requirements

- **FR-001**: The Daily Routine Builder MUST present a full-height modal editor with a sticky header, scrollable body, sticky bottom action bar (`Cancel`, `Save Routine`), and dirty-state dismissal protection (`"Discard unsaved routine?"`) on both `Cancel` and system back navigation whenever unsaved changes exist.
- **FR-002**: The Routine Builder MUST implement a 4-tier collapsible accordion hierarchy (`Routine Info`, `Routine Block`, `Block Setup`, and `Exercises` / `Exercise Card`) with smooth expand/collapse transitions, explicit `Done ▲` collapse buttons on expanded cards, single-row summary badges when collapsed, and a `Collapse All` action above the blocks list.
- **FR-003**: Routine Blocks MUST enforce exclusive block-level expansion by default so that expanding one block or adding a new block automatically collapses any previously expanded block into its single-row summary (`{Icon} {Block Name}` + `[Format]` + `[{N} Set(s)]` + `[{M} Ex]`).
- **FR-004**: `Block Setup` MUST replace legacy dropdowns, duplicate format text fields, and block-level repetition inputs with: (a) visual Block Type chips (with primary athletic types plus a `More` overflow for all supported block kinds), (b) contextual Execution Format chips filtered by Block Type plus `More`, and (c) a Fixed Integer Block Sets stepper (`Sets >= 1`) with quick-select pills (`1`, `3`, `4`, `5`, `6`, `8`).
- **FR-005**: Selecting an Execution Format MUST apply format-driven default block sets: `1` set for single-effort formats (`AMRAP`, `For Time`, `Chipper`, `Death by`, `Time cap`), `8` sets for `Tabata`, and `4` sets for structured strength/interval formats (`Sets x Reps`, `EMOM`, `E2MOM`, `E3MOM`, `Rounds for Time`, `Work/Rest Intervals`, `Calorie Sprints`, `Distance Sprints`, `Wave`, `Tempo`), while keeping the Sets stepper unlocked for manual override.
- **FR-006**: Each Exercise Card inside a block MUST provide a 4-option Target Metric selector (`Reps`, `Calories`, `Time`, `Meters`) that auto-defaults from the selected exercise's library measurement type, supports manual per-exercise override (allowing mixed metrics inside the same block), and caches Fixed/Per-Set values per metric during the editing session.
- **FR-007**: Each Exercise Card MUST provide a `Fixed` vs. `Per-Set` target mode toggle:
  - `Fixed` mode provides a metric-aware integer stepper (`±1` reps, `±2` cal, `±15s` time, `±50m` meters), direct integer keypad entry, and metric-specific quick preset pills (`Reps`: `3, 5, 8, 10, 12, 15`; `Calories`: `10, 12, 15, 20, 25, 30`; `Time`: `20s, 30s, 45s, 60s, 90s, 2m`; `Meters`: `100m, 200m, 250m, 400m, 500m, 1km`).
  - `Per-Set` mode renders `N` interactive set cards (`Set 1 … Set N`, bound to `Block Sets = N`) with micro-steppers (`±1` reps, `±1` cal, `±5s` time, `±50m` meters), direct integer entry (`>= 0`), one-tap `0 (Skip)` support, and pattern helpers (`Copy Set 1 to All`, `Descending`, `Alternate 0s`).
- **FR-008**: Switching an exercise from `Fixed` to `Per-Set` MUST initialize all `N` set slots with the current `Fixed` value (unless already populated for that metric); mutating `Block Sets` from `oldCount` to `newCount` MUST copy the last set's value (`Set N` copies `Set N-1`, including `0`) when increasing sets and trim active slots to `newCount` when decreasing sets.
- **FR-009**: Sets with a target value of `0` MUST render with distinct non-color visual indicators (muted fill, dashed border, and explicit `0 (Skip)`, `0 cal (Skip)`, `0s (Skip)`, or `0m (Skip)` label) and accessible screen-reader descriptions announcing that the exercise is skipped in that set.
- **FR-010**: The Routine Builder MUST prevent collapsing or saving an all-zero exercise (`Fixed = 0` or every `Per-Set` slot equal to `0`), highlighting the offending Exercise Card in error styling with the message `"Set a target above 0 on at least one set, or remove this exercise"`, and enabling `Save Routine` only when `Routine Name` is non-blank and at least one block contains at least one valid exercise (and zero invalid exercises).
- **FR-011**: Each Routine Block MUST provide a unified inline Exercise Search & Custom Creation combobox with category filter chips and a one-tap `+ Create & Add "{query}"` action when no exact library match exists, plus reordering controls (up/down) and deletion with a 4-second `[Undo]` notification for both exercises and blocks.
- **FR-012**: Saved routines MUST persist per-exercise metrics, Fixed/Per-Set modes, per-set values (including `0`), block set counts, and user notes losslessly across local storage, Library Routine Card summaries, CSV Backup export/import, Cloud Sync, Workout Sharing codes, and legacy routine loading without destructive database migrations.
- **FR-013**: Loading a saved Daily Routine into Workout Logging (`Load Routine...`) MUST expand multi-exercise blocks into a grouped Sub-Block (one trackable movement entry per exercise in sequence) and omit `0 (Skip)` sets while preserving original 1-based round numbers on active sets and pre-populating metric targets (`cal`, `s`, `m`, or `reps`).
- **FR-014**: The remote AI assistant (`list_routines`, `upsert_routine`) MUST accept and return structured per-exercise routine block targets (`metric`, `mode`, `fixedValue`, `perSetValues` supporting `0`) using flat connector-compatible schemas while remaining backward-compatible with legacy `exerciseNames` and `targetRepsScheme` inputs.

### Key Entities

- **Daily Routine**: Named workout template with an optional goal/description and an ordered sequence of Routine Blocks.
- **Routine Block**: A structural workout segment defined by a name, athletic Block Type, Execution Format, and a fixed integer `Sets` count (`N >= 1`), containing an ordered sequence of Routine Exercise Targets.
- **Routine Exercise Target**: A movement entry within a Routine Block specifying the linked Exercise, Target Metric (`Reps`, `Calories`, `Time`, `Meters`), Target Mode (`Fixed` or `Per-Set`), Fixed integer value, and `N` Per-Set integer values (`>= 0`, where `0` denotes skipping the movement on that set).

## Success Criteria *(mandatory)*

### Measurable Outcomes

- **SC-001**: Users can configure a 2-block mixed-metric routine (including an exercise with variable per-set targets and a skipped `0` set) in under 90 seconds, with collapsed blocks fitting within a single mobile screen without multi-screen vertical scrolling.
- **SC-002**: 100% of routine configurations—including mixed metrics (`Reps`, `Calories`, `Time`, `Meters`), variable per-set arrays containing `0 (Skip)`, and legacy single-scheme routines—round-trip with zero data loss across local save/edit, CSV backup export/import, cloud sync, workout sharing, and remote AI assistant `upsert_routine` / `list_routines` operations.
- **SC-003**: 100% of interactive controls in the Routine Builder maintain minimum `48dp x 48dp` touch targets and render without text clipping, unintended mid-word wrapping, or horizontal overflow on `360dp`-wide screens from `1.0x` up to `2.0x` system font scale.
- **SC-004**: Loading a multi-exercise mixed-metric routine into Workout Logging creates 100% of constituent movement entries grouped under their Sub-Block with accurate active set counts, preserved round indices across skipped `0` sets, and pre-filled target values.

## Assumptions

- Scope of the visual Routine Builder UI is Android mobile/tablet; web and iOS interfaces remain out of scope for this release, while the remote AI assistant connector and cloud sync layer are included to maintain cross-channel parity.
- Existing routines saved prior to this redesign use block-level `targetRepsScheme` strings and `exerciseIdsCsv`; these are automatically interpreted into Fixed or Per-Set exercise targets when viewed, edited, synced, or loaded into a session, requiring no manual user migration.
- Exercise targets inside Routine Builder use non-negative whole integers (`reps`, `cal`, `seconds` for Time, and `meters` for Meters, where presets like `2m` and `1km` map cleanly to `120s` and `1000m`), matching integer keypad entry rules.
