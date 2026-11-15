# Feature Specification: Unified Add Block Workflow in Edit & Log Session (`SPEC-024`)

**Feature Branch**: `feat/024-session-add-block`  
**Created**: 2026-11-15  
**Status**: Clarified & Approved  
**Input**: User description: "When I want to edit a workout adding a new block, the application is not doing anything. I should be able to add more blocks" (with screenshot of `Edit Session` showing `SimplifiedBlockLogCard` and the unresponsive `+ Add Block` button).

## Clarifications

### Session 2026-11-15

- Q: When tapping `+ Add Block` in Edit Session (and Log Session), how should adding a new block work? → A: Open an "Add Block" sheet (`AddBlockToSessionSheet`) to either pick saved Available Block(s) or add a custom exercise block as a simplified weight-tile card (`SimplifiedBlockLogCard`).
- Q: When creating a custom exercise block from the `+ Add Block` sheet, should it also be saved to your Available Blocks library? → A: Session-only by default, with an optional `"Save to Available Blocks library"` toggle (`Switch`, OFF by default).
- Q: When you add a saved Available Block while editing an existing workout ("Edit Session"), how should its initial set weights populate? → A: Pre-fill from its last session (and show the `"Pre-filled from last session ({Date})"` hint on the newly added block), while keeping `"Pre-filled from last session"` hidden on blocks that were already logged in the edited session.

---

## User Scenarios & Testing *(mandatory)*

### User Story 1 - Add Saved Available Blocks in Edit & Log Session with Reliable Visual Feedback (Priority: P1)

As an athlete editing an existing workout in **Edit Session** (or logging a new workout in **Log Session** / **Copy Session**), when I tap **+ Add Block** or **Load Block ▾**, I want a unified **Add Block** sheet to open where I can see which saved Available Blocks are already in the workout, select one or more additional saved blocks, see their weights pre-filled from their last logged session (with the `"Pre-filled from last session ({Date})"` hint on the newly added block), and have the workout scroll immediately to show the newly appended block card.

**Why this priority**: Currently tapping **+ Add Block** in **Edit Session** (or in **Log Session** once any block is loaded) adds a hidden blank legacy placeholder (`isBlankLegacyPlaceholder() == true` suppressed by `showLegacyBlocks == false`), so the UI visibly does nothing when the athlete wants to add more blocks to a workout. In addition, the prior **Load Available Blocks** sheet pre-selected already-loaded blocks in its selection count, causing its primary button to look active (`Load (N)`) while adding nothing when tapped.

**Independent Test**: Open `Edit Session` on a logged workout containing `BLOCK: Romanian Deadlift`, tap **+ Add Block**, verify the unified Add Block sheet opens on `Saved Blocks` when unloaded saved blocks exist, shows `Romanian Deadlift` marked `"Already in session"` (not pre-selected), lets the user pick another block (e.g., `Bench Press`), appends `BLOCK: Bench Press` pre-filled from its last historical session (excluding the edited session itself) with `"Pre-filled from last session ({Date})"` visible on `Bench Press` (while `Romanian Deadlift` keeps it hidden), marks the session dirty for Back guard, and scrolls to bring `BLOCK: Bench Press` into view.

**Acceptance Scenarios**:

1. **Given** an athlete is in `Edit Session`, `Log Session`, or `Copy Session`, **When** they tap **+ Add Block** (bottom action bar) and at least one unloaded saved Available Block exists, **Then** the unified **Add Block** modal sheet opens on the **Saved Blocks** tab; when they tap **Load Block ▾** (header card), it opens on **Saved Blocks**; and when no unloaded saved blocks exist, **+ Add Block** opens directly on the **Custom Block** tab.
2. **Given** the **Saved Blocks** tab is open, **When** viewing the list of saved Available Blocks, **Then** blocks already present in the current session are visibly marked `"Already in session"` and are non-selectable (not pre-counted in the selection), while selecting `N` unloaded blocks updates the CTA to `"Add 1 Block"` / `"Add N Blocks"` (disabled until `N >= 1`).
3. **Given** the athlete confirms `N >= 1` saved blocks in **Edit Session**, **When** the sheet closes, **Then** each newly added block renders as a `SimplifiedBlockLogCard` with weights pre-filled from its most recent prior session (`excludeSessionId = editingSessionId`) and displays `"Pre-filled from last session ({Date})"` (or 3 empty weight tiles `Set 1..3` if unlogged), existing originally hydrated blocks continue hiding `"Pre-filled from last session"`, `isDirty` becomes `true`, and the editor smoothly scrolls down to bring the newly added block card into view.

---

### User Story 2 - Create & Append Custom Exercise Blocks as Simplified Weight-Tile Cards (Priority: P1)

As an athlete adding an ad-hoc movement or superset mid-workout or while reviewing a past session, I want to switch to the **Custom Block** tab inside **Add Block**, pick one or more movements (or create a new movement inline), optionally enter a block name and instructions, optionally toggle **Save to Available Blocks library** (OFF by default), and append it directly to the workout as a clean weight-tile block card (`SimplifiedBlockLogCard`).

**Why this priority**: Athletes frequently add an unplanned accessory exercise or extra movement block while logging or editing a workout without wanting to leave the editor to create a template in Library first.

**Independent Test**: Open **Add Block** -> switch to **Custom Block** (or tap **+ Add Block** when all saved blocks are loaded or none exist) -> select `Hip Thrust` + `Cable Crunch` (or enter `"Hip Thrust"` with 0 chips selected), leave `"Save to Available Blocks library"` OFF -> tap `"Add Block"` -> verify `SimplifiedBlockLogCard` (`BLOCK: Hip Thrust + Cable Crunch` or `BLOCK: Hip Thrust`) is appended with 3 empty weight tiles (`Set 1..3`), `+ Add Set`, and `+ Record PR 🔥`, `routines` table gains zero new rows, and toggling `"Save to Available Blocks library"` ON persists the block as a reusable `AvailableBlock` (`defaultFormat = "AVAILABLE_BLOCK"`).

**Acceptance Scenarios**:

1. **Given** the **Custom Block** tab is open, **When** the athlete selects 1+ movements via searchable `FilterChip`s (or creates one inline via `"New movement name"` + `+ Add`), leaves **Block Name** blank, and taps **Add Block**, **Then** the block title defaults cleanly to the joined movement names (e.g., `"Clean + Front Squat"`), and a `SimplifiedBlockLogCard` with 3 empty weight tiles (`Set 1..3`) per movement is appended and scrolled into view.
2. **Given** the athlete types a **Block Name** (e.g., `"Hip Thrust"`) with 0 movement chips selected, **When** they tap **Add Block**, **Then** the system resolves or creates a `WEIGHT`-tracked exercise matching `"Hip Thrust"` so the appended `SimplifiedBlockLogCard` contains a movement section with 3 empty weight tiles (`Set 1..3`) and supports `+ Record PR 🔥` with a valid movement ID.
3. **Given** the `"Save to Available Blocks library"` toggle (`Switch`) is left in its default `OFF` state, **When** the athlete taps **Add Block**, **Then** the custom block is session-only (no `AvailableBlock` / `Routine` row is created in Library); when toggled `ON`, the block is persisted to the Available Blocks library (and queued for cloud upload when signed in) and appended to the session.

---

### User Story 3 - Mixed-Session Coexistence, Dirty Navigation Guard & Lossless Save/PR Reconciliation (Priority: P2)

As an athlete editing a workout that already contains simplified blocks and/or residual legacy blocks (e.g., timed/metcon scores), I want newly added blocks, existing blocks, and residual legacy blocks to coexist cleanly, protect my additions from accidental Back navigation, let me remove an unwanted residual legacy block when other blocks remain, and persist everything—including newly recorded PRs—when I tap **Update session** / **Save Session**.

**Why this priority**: Prevents regressions in edit-mode re-hydration, back-navigation protection, mixed metcon/strength editing, and atomic `RepMax` reconciliation introduced in `SPEC-022`.

**Independent Test**: In `Edit Session` for a workout with 1 hydrated block (`Romanian Deadlift`) and 1 residual metcon block, tap `+ Add Block` and append a custom block (`Hip Thrust`) with weights and a `3RM` PR: verify `CompactBlockEditor` allows removing the single residual legacy block (`canRemove == true` when `loadedAvailableBlocks.isNotEmpty()`), pressing Back triggers `"Discard unsaved changes?"`, live `availableBlocks` updates from saving a library block while editing do not wipe unsaved form state, and tapping `Update session` saves all remaining blocks and reconciles PRs cleanly without duplicate `RepMax` rows.

**Acceptance Scenarios**:

1. **Given** an athlete is in `Edit Session` with `isDirty == false` and opens the **Add Block** sheet (or appends any block), **When** `availableBlocks` or `exercises` flows emit updates (for instance after creating an inline movement or saving a block to Library), **Then** the edit-mode re-hydration effect does not reset or overwrite user inputs or open sheet state.
2. **Given** a session contains at least one simplified block (`loadedAvailableBlocks.isNotEmpty()`) alongside a single residual legacy block (`blocks.size == 1`), **When** the athlete views the residual legacy block's remove icon (`🗑`), **Then** removal is enabled so the residual block can be removed without leaving the session empty.
3. **Given** an athlete adds a saved or custom block in `Edit Session`, logs weights and optionally records a PR, and taps **Update session**, **When** `Repository.updateSession(..., reconcileRepMaxes = true)` executes, **Then** original blocks, newly added blocks, and PRs persist transactionally, and parent template PR inheritance only runs for blocks linked to a persisted Available Block (`id > 0L`).

---

### Edge Cases

- **No saved Available Blocks or all saved blocks already in session**: Tapping **+ Add Block** opens directly on the **Custom Block** tab instead of showing an empty/all-disabled picker; **Saved Blocks** empty state also provides a one-tap `"Switch to Custom Block"` button.
- **Custom block submission disabled when empty**: While both **Block Name** is blank and zero exercises are selected, the **Add Block** CTA stays disabled so empty placeholder cards cannot be appended.
- **Repeated Save-to-Library title collision**: If the athlete enters a **Block Name** matching an existing Available Block and enables `"Save to Available Blocks library"`, `Repository.saveRoutineWithBlocks` updates/reuses by normalized name safely without creating duplicate entries, and `SessionEditorBody` checks `alreadyLoadedIds` so an already-present library ID is not duplicated unexpectedly.
- **Quick Parse block addition**: Adding a block via `Quick Parse` (`QuickAddWorkoutDialog`) also records a scroll target so the editor scrolls smoothly to the newly parsed block below the fold.
- **Viewport & Accessibility (`360dp`, `fontScale >= 1.3f`, IME)**: Modal bottom sheet uses `imePadding()`, all interactive tabs, checkboxes, switches, chips, and buttons meet `>= 48.dp` touch targets, and the `"Save to Available Blocks library"` row merges TalkBack semantics (`label + description + toggle state`).

---

## Requirements *(mandatory)*

### Functional Requirements

- **FR-001 (Unified Add Block Sheet Trigger)**: Tapping **+ Add Block** (bottom action bar) or **Load Block ▾** (session header) in `Edit Session`, `Log Session`, or `Copy Session` MUST open the unified `AddBlockToSessionSheet` modal bottom sheet rather than appending an invisible blank legacy placeholder to `blocks`.
- **FR-002 (Smart Initial Tab Selection)**: Opening via **Load Block ▾** MUST default to **Saved Blocks** (when any saved block exists); opening via **+ Add Block** MUST default to **Saved Blocks** when at least one unloaded `AvailableBlock` exists (`availableBlocks.any { it.id !in alreadyLoadedIds }`), and MUST default to **Custom Block** otherwise.
- **FR-003 (Saved Blocks Picker Non-Preselection & Exclusion)**: Inside the **Saved Blocks** tab, blocks whose ID is already in the active session (`alreadyLoadedIds`) MUST be clearly labeled `"Already in session"`, MUST NOT be selectable, and MUST NOT be pre-selected in the newly-selected count; the confirm CTA MUST track only newly selected blocks and remain disabled until at least 1 new block is selected.
- **FR-004 (Edit-Mode Pre-Fill on Newly Appended Saved Blocks)**: Newly appended saved `AvailableBlock`s (whether in `Log Session`, `Copy Session`, or `Edit Session`) MUST resolve historical weights from their most recent prior session (`excludeSessionId = editingSessionId`) and MUST render `"Pre-filled from last session ({Date})"` when prior weights exist, while originally hydrated blocks in `Edit Session` (`buildHydratedBlockState`) MUST continue hiding that banner (`lastSessionDateLabel = null`).
- **FR-005 (Custom Block Creation as Simplified Card)**: The **Custom Block** tab MUST allow selecting 1+ movements via searchable chips (plus inline weighted movement creation), entering an optional Block Name (defaulting to joined movement names when blank) and optional Instructions, and appending the result as a `SimplifiedBlockLogCard` initialized with 3 empty weight tiles (`Set 1..3`) per movement.
- **FR-006 (Title-Only Custom Block Movement Resolution)**: If 0 movement chips are selected when submitting a non-blank **Block Name**, the system MUST resolve an existing exercise matching that title (case-insensitive) or create a `MetricType.WEIGHT` (`tracksRepMax = true`) exercise so every custom `SimplifiedBlockLogCard` includes an active movement section and supports `+ Record PR 🔥`.
- **FR-007 (Session-Only Default & Optional Library Persistence)**: The **Custom Block** tab MUST include a `"Save to Available Blocks library"` switch defaulting to `OFF` (`false`). When `OFF`, the custom block MUST stay session-only (`AvailableBlock.id = 0L`, zero `routines` rows written); when `ON`, the block MUST be persisted as an `AVAILABLE_BLOCK` in the library (and uploaded via cloud sync when authenticated).
- **FR-008 (Auto-Scroll to Newly Added Block)**: Appending any block (via `Saved Blocks`, `Custom Block`, or `Quick Parse`) MUST automatically scroll `SessionEditorBody`'s vertical scroll container to bring the newly added block card into the visible viewport once layout bounds settle.
- **FR-009 (Dirty State, Title Preservation & Re-hydration Guard)**: Appending any block via `AddBlockToSessionSheet` MUST mark `simplifiedStructuralDirty = true` (triggering the `BackHandler` `"Discard unsaved changes?"` prompt in `Edit Session`), MUST NOT overwrite an existing session title in `Edit Session`, and MUST prevent background `availableBlocks` / `exercises` emissions from re-hydrating and resetting form state while the Add Block sheet is open or dirty.
- **FR-010 (Residual Legacy Removal Parity)**: In `CompactBlockEditor`, removing a residual legacy block MUST be enabled whenever `loadedAvailableBlocks.isNotEmpty() || blocks.size > 1`.
- **FR-011 (Accessibility & Responsive Layout)**: `AddBlockToSessionSheet` MUST apply `Modifier.imePadding()`, enforce `>= 48.dp` minimum interactive target sizing on tabs, cards, switch rows, and action buttons, and preserve readable non-clipped layouts up to `fontScale = 1.4f`.
- **FR-012 (Schema Stability & Test Coverage)**: Room database version MUST remain `v10` (zero schema migrations) and `firestore.rules` MUST remain unchanged; all pure state helpers, repository persistence paths (`saveToLibrary = false` vs `true`), and edit-mode session updates MUST be covered by deterministic unit tests.

### Key Entities

- **`AddBlockSheetTab` / `AddBlockSheetOpenMode`**: UI routing state selecting `SAVED_BLOCKS` vs `CUSTOM_BLOCK` based on trigger context (`Load Block ▾` vs `+ Add Block`) and unloaded library availability.
- **`LoadedAvailableBlockState` (extended with `uiKey`)**: Stateful simplified card model carrying a stable `uiKey` for Compose keying and layout bounds auto-scrolling, supporting hydrated edit blocks (`lastSessionDateLabel = null`), newly added saved blocks (`lastSessionDateLabel = "Pre-filled from last session (...)"` even in edit mode), and custom ad-hoc/library-saved blocks.

## Success Criteria *(mandatory)*

### Measurable Outcomes

- **SC-001 (Zero Unresponsive `+ Add Block` Taps)**: 100% of taps on `+ Add Block` and `Load Block ▾` across `Edit Session`, `Log Session`, and `Copy Session` open `AddBlockToSessionSheet` with valid primary actions (selecting saved blocks or creating custom blocks) and append visible simplified block cards.
- **SC-002 (Automatic Visibility After Addition)**: 100% of appended blocks automatically scroll into the visible viewport without requiring manual user scrolling to verify the addition.
- **SC-003 (Zero Unintended Library Pollution)**: Custom blocks created with `"Save to Available Blocks library"` OFF create `0` `Routine` / `AvailableBlock` rows in persistence, while `ON` persists reusable blocks cleanly across local Room and sync.
- **SC-004 (100% Pre-PR Test & Lint Parity)**: `.\gradlew.bat testDebugUnitTest --no-daemon`, `.\scripts\tests\Invoke-ScriptTests.ps1`, `.\gradlew.bat lintDebug --no-daemon`, and `.\gradlew.bat assembleDebug --no-daemon` pass 100% with zero skipped assertions or Room schema changes.
- **SC-005 (Remote CI Verification)**: All GitHub Actions workflow checks on the opened PR (`PR CI & Test Verification`, `PR Review`) complete 100% green.

## Assumptions

- `Quick Parse` (`AI Quick Add / Parse`) in the top bar and bottom bar continues to use `QuickAddWorkoutDialog` for natural-language workout text parsing into `BlockState` items, while manual block addition via `+ Add Block` and `Load Block ▾` routes through `AddBlockToSessionSheet`.
- Custom blocks created from `AddBlockToSessionSheet` default newly added movements to `MetricType.WEIGHT` (`ExerciseCategory.BARBELL`, `tracksRepMax = true`), matching `LibraryScreen`'s `QuickCreateBlockSheet` convention.
- Existing `SimplifiedBlockLogCard` weight-tile editing and PR chip workflows (`SPEC-021` and `SPEC-022`) handle logging and PR recording for all newly added blocks without requiring changes to `Repository.updateSession` or MCP tool schemas.
