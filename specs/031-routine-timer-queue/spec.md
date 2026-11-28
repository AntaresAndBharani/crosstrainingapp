# Feature Specification: Routine-Aware Auto-Extrapolated Workout Timer Queue

**Feature Branch**: `feat/031-routine-timer-queue`

**Created**: 2026-11-28

**Status**: Draft

**Input**: User description: "Implement the requirement discussed on c:\Users\rogal\workspaces\ws-gym\crosstrainingapp\docs\draft-requisites\routine-timer-queue.md"

## User Scenarios & Testing *(mandatory)*

### User Story 1 - Extrapolate and Run Multi-Block Timer Queue From Any Block (Priority: P1)

When an athlete is logging a workout in Log Session (either loaded from a Daily Routine or built ad-hoc with blocks and exercises), they can tap the timer control on any block `#k`, configure or confirm that starting block's timer mode (such as `E3MOM`, `EMOM`, `Time Cap`, `AMRAP`, `Tabata`, `Death By`, or `Rest`), and launch a continuous workout timer queue. The timer automatically extrapolates every subsequent block `#k+1` through `#N` as `E3MOM` (3-minute interval) for that block's routine-defined or current default number of sets, placing a `1:00` (`60`-second) transition countdown between consecutive blocks.

**Why this priority**: This is the core gym-floor value of the feature—eliminating manual timer restarts between blocks across an entire multi-block training session.

**Independent Test**: Can be fully tested by building a 4-block session, anchoring the queue at Block 2, configuring Block 2 as a `Time Cap`, starting the queue, and verifying that Block 2 runs and transitions through `1:00` transitions into Blocks 3 and 4 as `E3MOM` for their respective set counts until completion.

**Acceptance Scenarios**:

1. **Given** a session containing 4 ordered blocks (`[A: 4 sets, B: 3 sets, C: 5 sets, D: 2 sets]`), **When** the athlete opens the timer from Block 2 (`B`), **Then** Block 2 is pre-populated as `E3MOM × 3` (freely editable to any supported mode), Block 1 (`A`) is omitted, and Blocks `C` and `D` are automatically queued after `1:00` transitions as `E3MOM × 5` and `E3MOM × 2`.
2. **Given** a remaining block `C` whose notes or instructions contain timing tokens such as `"AMRAP 12"` or `"Rest 90s"`, **When** the queue extrapolates remaining blocks after the anchor, **Then** those notes are ignored and Block `C` is always queued as `E3MOM (180s) × {sets}` after a `1:00` transition.
3. **Given** an active multi-block queue with Stage 1 configured with a `10`-second prep countdown and `Time Cap`, **When** Stage 1 starts and completes (naturally or via `Next Stage`), **Then** the prep countdown runs only on Stage 1, and Stage 1 enters a `1:00` transition directly into Stage 2 (`E3MOM`) with no extra prep countdown, continuing block-by-block until the final block finishes.

---

### User Story 2 - Anchor From Top-Bar Timer or Re-Anchor Before Starting (Priority: P1)

When an athlete taps the Top-Bar Timer action while inside Log Session with one or more blocks present, the in-session timer setup sheet opens immediately with a horizontal Block Anchor Strip defaulting to Block 1 (or the currently active block if a timer is already running), allowing them to switch the starting anchor block before pressing Start. When no blocks exist in Log Session, the Top-Bar Timer action opens the standalone timer screen as before.

**Why this priority**: Many athletes tap the main Log Session timer button first rather than a specific card's timer icon; connecting it directly to the workout blocks prevents dead-end navigation.

**Independent Test**: Can be verified by opening Log Session with 0 blocks (routes to standalone timer), then loading a 3-block routine and tapping the Top-Bar Timer icon (opens in-session queue anchored at Block 1), then tapping Block 2 in the anchor strip (re-anchors queue to Blocks 2 and 3).

**Acceptance Scenarios**:

1. **Given** Log Session has 3 blocks loaded and no timer is active, **When** the athlete taps the Top-Bar Timer icon, **Then** the in-session timer sheet opens with Block 1 selected in the anchor strip and Blocks 2 and 3 auto-extrapolated in the queue preview.
2. **Given** the pre-start setup sheet is open at Block 1, **When** the athlete taps Block 2 in the Block Anchor Strip, **Then** the starting configurator switches to Block 2 (`E3MOM × {sets}`) and the queue preview updates to Block 3 onward.
3. **Given** Log Session is empty (`0` non-blank blocks or exercises), **When** the athlete taps the Top-Bar Timer icon, **Then** the app opens the standalone workout timer screen.

---

### User Story 3 - Customize, Edit, or Remove Any Queued Block Pre-Start and Live (Priority: P2)

Before starting—or while a multi-block queue is actively running or transitioning—the athlete can inspect the upcoming queue timeline, edit any queued block's timer mode, duration/interval, round count, or preceding transition wait, remove any block from the queue without deleting it from their workout log, or append additional stages.

**Why this priority**: Gym conditions change mid-workout (shared racks, fatigue, shorter rest); live mutability keeps the queue useful instead of forcing an athlete to cancel the whole timer.

**Independent Test**: Can be verified by creating a 3-block queue, editing Stage 3's mode to `AMRAP 8` and transition to `30s`, removing Stage 2 during execution/transition, and confirming the remaining queue and total time remaining update immediately while the workout log cards stay intact.

**Acceptance Scenarios**:

1. **Given** a 3-block queue in the pre-start setup view or active timer sheet, **When** the athlete taps Edit on queued Block 3 and changes it from `E3MOM` to `AMRAP 10` with a `45s` transition, **Then** only Block 3 updates in place, Block 2 remains unchanged, and total estimated remaining time recalculates accurately.
2. **Given** a queued block in the pre-start setup or active queue list, **When** the athlete taps Remove on that queued block, **Then** it is removed from the timer queue without altering or removing the corresponding card in Log Session.
3. **Given** the timer is currently in a `TRANSITION` phase waiting for the immediate next block, **When** the athlete removes that immediate next block and another block follows behind it, **Then** the transition updates its Up Next target to the following block; if no blocks remain, the timer completes cleanly into `Finished`.

---

### User Story 4 - In-Session Context Sync Without Stealing Keyboard Focus (Priority: P2)

While a queued workout timer runs, the docked in-session timer bar, the active workout card in Log Session, the lock-screen notification, and boundary TalkBack announcements clearly surface which block and queue position are currently active without stealing keyboard focus while typing weights or reps.

**Why this priority**: Athletes log sets between intervals; losing keyboard focus or forgetting which block is currently timed slows down data entry on the gym floor.

**Independent Test**: Can be verified by running a 3-block queue while editing set weights on a card in Log Session, confirming keyboard focus stays intact on timer bar taps, `"Timer Active"` / `"Up Next"` indicators highlight the matching card, and notifications/TalkBack report block names at boundaries.

**Acceptance Scenarios**:

1. **Given** Block 2 of 4 (`Clean Complex`) is actively running, **When** the athlete minimizes the timer sheet to log sets, **Then** the docked bar shows the countdown, `Clean Complex`, current round, and queue position (`2/4`), and tapping Pause/Skip on the bar never dismisses or steals keyboard focus from numeric inputs.
2. **Given** Log Session displays multiple cards and Block 2 is active (or up next during `TRANSITION`), **When** viewing Log Session, **Then** Block 2's card highlights with an active indicator pill (`Timer Active` during work/prep/rest, `Up Next` during transition).
3. **Given** a foreground notification or TalkBack boundary event fires on a block transition, **When** a round/phase/block changes, **Then** the notification and boundary announcement state the active block title (or `Up Next` block title) without per-second announcement spam.

---

### User Story 5 - Safe Active-Timer Conflict Resolution & Reset Confirmation (Priority: P3)

When a timer is already running, tapping the already-active block's timer icon opens the active timer directly, tapping a different block prompts before replacing and rebuilding the queue from that block, and tapping Reset offers to restart only the current block (preserving the queue) or clear the entire queue.

**Why this priority**: Prevents accidental loss of workout timer progress from an unintended tap while still enabling fast mid-session re-anchoring when needed.

**Independent Test**: Can be verified by starting a queue at Block 1, tapping Block 1's timer (opens active timer directly), tapping Block 3's timer (prompts `Rebuild Queue` vs `Cancel`), and tapping `Reset` (prompts `Restart Current Block (Keep Queue)` vs `Stop & Clear Full Queue`).

**Acceptance Scenarios**:

1. **Given** a timer queue is running for Block 1, **When** the athlete taps Block 1's timer icon, **Then** the active timer sheet opens immediately with no replacement warning.
2. **Given** a timer queue is running for Block 1, **When** the athlete taps Block 3's timer icon and confirms `Rebuild Queue`, **Then** the engine atomically replaces the running timer and rebuilds the queue starting at Block 3.
3. **Given** a multi-block queue is active and the athlete taps `Reset`, **When** the confirmation dialog appears, **Then** choosing `Restart Current Block (Keep Queue)` restarts the current block from Round 1 while retaining all queued stages, whereas `Stop & Clear Full Queue` clears the entire session back to idle.

---

### Edge Cases

- **Starting on the final block (`#N`)**: No subsequent blocks exist, so the queue holds 0 upcoming stages, no trailing `1:00` transition is added, and completing the block finishes the timer session.
- **Single-set block**: A block configured with `1` set (or `0` malformed sets) clamps to `1` round (`E3MOM 1 × 3:00`).
- **Ad-hoc blocks without routine targets or history**: Uses the card's current visible set count (`3` default tiles for complexes, `4` for standalone exercises, or the user-adjusted set count).
- **Repeated block or exercise names**: Each queued block and timeline item uses a unique stage/block identifier so duplicate exercise or complex titles never collide in UI highlights or queue edits.
- **Removing a block card from Log Session while timed**: The running timer continues safely without crashing; the active card highlight simply disappears because the card is no longer present.
- **Standalone Timer screen usage**: Launching Workout Timers from the navigation drawer continues supporting single-stage and multi-stage manual queueing with zero regressions.

## Requirements *(mandatory)*

### Functional Requirements

- **FR-001**: System MUST project all visible non-blank workout blocks and exercises in Log Session into an ordered list of timer entries preserving display order and each block's resolved set count (`>= 1`).
- **FR-002**: System MUST extrapolate a multi-stage timer queue starting from any chosen anchor index `#k` (`0 <= k < N`): Stage 1 (`#k`) defaults to `E3MOM` (`180s`) × `{sets}` and is freely configurable to any supported timer mode (`EMOM/ExMOM`, `AMRAP`, `Death By`, `Time Cap`, `Tabata`, `Rest`), while every subsequent stage (`#k+1 … #N`) ALWAYS defaults to `E3MOM` (`180s`) × `{sets}` preceded by a `60s` (`1:00`) transition, ignoring block notes or instructions.
- **FR-003**: System MUST apply `prepCountdownSeconds` only on Stage 1 of a queue and set `prepCountdownSeconds = 0` on all extrapolated subsequent stages (`2..N`) so stages transition directly from `TRANSITION` into `WORK`.
- **FR-004**: System MUST support multi-stage queue storage, automatic sequential promotion (`WORK → TRANSITION → WORK`), and live queue mutations (update stage, remove stage, append stage, replace full queue, and restart current stage preserving queue).
- **FR-005**: System MUST safely handle mutating or removing the immediate upcoming stage while `TimerPhase.TRANSITION` is active: updating the transition countdown/preview if changed, promoting immediately if transition becomes `0s`, updating Up Next to the new head if another stage remains, or finishing cleanly if the queue becomes empty.
- **FR-006**: System MUST calculate `totalSecondsRemaining` deterministically across the active stage, all remaining transitions, and all remaining queued stages in every timer phase.
- **FR-007**: System MUST open the in-session timer setup sheet anchored at Block `#k` when tapping Block `#k`'s timer icon in Log Session (including simplified complexes, simplified standalone exercises, and legacy blocks/sub-blocks).
- **FR-008**: System MUST open the in-session timer setup sheet anchored at Block 1 (or the active running view if a timer is already active) when tapping the Top-Bar Timer icon in Log Session while `>= 1` block is present, and route to the standalone timer screen when `0` blocks are present.
- **FR-009**: Pre-start setup inside the in-session timer sheet MUST present a selectable Block Anchor Strip allowing the athlete to switch the anchor block `#m` and regenerate the extrapolated queue before starting.
- **FR-010**: Both pre-start setup and active execution views MUST display queued stages with `1:00 Transition` connectors, allow tapping Edit on any queued stage to adjust its mode/duration/rounds/transition, allow removing any queued stage without affecting Log Session cards, and allow appending additional stages.
- **FR-011**: System MUST synchronize active block context across the active timer header (`Block X of Y • <Title>`), docked in-session timer bar (preserving `focusProperties { canFocus = false }`), Log Session card highlight (`Timer Active` / `Up Next`), foreground timer notification, and boundary-only TalkBack announcements.
- **FR-012**: System MUST handle active-timer conflicts gracefully: tapping the active block opens the running timer sheet directly, while tapping a different block prompts to rebuild the queue from the newly tapped block (`Rebuild Queue` vs `Cancel`).
- **FR-013**: System MUST preserve Reset confirmation choices (`Restart Current Block (Keep Queue)` vs `Stop & Clear Full Queue`) whenever a multi-stage queue or transition is active.
- **FR-014**: System MUST comply with the Graphite, Titanium & Warm Bronze Material 3 design system (`MASTER.md` and `pages/routine-timer-queue.md`), enforcing `>= 48.dp` touch targets, `>= 8.dp` target spacing, tabular numerals (`tnum`), vector-only icons, `360dp` / `fontScale = 1.3f` layout safety, and contrast parity across all 4 `AppThemeMode`s.
- **FR-015**: Feature MUST require zero Room SQLite schema migrations and zero Cloud Firestore rule/storage changes.

### Key Entities

- **Session Timer Block Entry**: Lightweight immutable representation of one ordered workout block or exercise in Log Session (`blockKey`, `positionIndex`, `title`, `defaultSetCount`).
- **Queued Timer Stage**: Immutable upcoming stage entry (`id`, `config`, `transitionSeconds`) representing a block or custom finisher scheduled after a preceding transition countdown.
- **Built Workout Timer Queue**: Deterministic output of anchoring a session's block entries at index `#k` (`anchorIndex`, `startingConfig`, `queuedStages`).
- **Timer Snapshot**: Immutable live execution snapshot exposing active phase, round/stage progress (`currentStageIndex`, `totalPlannedStages`), and immutable `queuedStages` list (with backward-compatible `queuedNextStage` projection).

## Success Criteria *(mandatory)*

### Measurable Outcomes

- **SC-001**: Athletes can launch a full multi-block workout timer queue from any block card or the Log Session top bar in at most 2 taps (tap block timer → confirm/select starting mode → tap `START QUEUE`), with zero manual reconfiguration required between blocks.
- **SC-002**: 100% of extrapolated remaining blocks default deterministically to `E3MOM (180s)` × routine/card set count with `60s` (`1:00`) transitions regardless of block notes or instructions.
- **SC-003**: Editing, removing, or appending any stage in a multi-block queue updates the remaining queue and total remaining duration immediately with zero transient `IDLE` flicker and zero crashes during `TRANSITION` edge cases.
- **SC-004**: Tapping docked timer controls while editing workout set inputs preserves soft keyboard and text input focus 100% of the time.
- **SC-005**: All new and existing Android unit tests (`testDebugUnitTest`), script tests (`Invoke-ScriptTests.ps1`), and Android lint (`lintDebug`) pass at 100% with zero regressions.
- **SC-006**: All timer queue UI surfaces render cleanly without clipping or overlap on `360dp`-wide screens at `fontScale = 1.3f` across all 4 built-in theme modes.

## Assumptions

- Daily Routines and ad-hoc session cards remain the source of truth for block order and set counts; custom per-block timer overrides apply to the active timer queue session and do not mutate saved routine templates.
- Existing `TimerMode` definitions (`EMOM`, `AMRAP`, `DEATH_BY`, `TIME_CAP`, `TABATA`, `REST`) and `WorkoutTimerConfig` timing fields cover all starting-mode and stage-editing needs.
- Standalone Timer screen (`TimerScreen`) remains accessible via the navigation drawer and empty-session top-bar fallback without changing its primary single-mode quick-start flow.
