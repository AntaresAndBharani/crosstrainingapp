# Feature Specification: Continuous Workout Timer & Live Stage Queue

**Feature Branch**: `feat/023-continuous-workout-timer`
**Created**: 2026-11-15
**Status**: Clarified
**Spec ID**: SPEC-023 (`SPEC-CONTINUOUS-TIMER-01`)
**Source**: Continuous Timer Refinement Roadmap (`https://share.gemini.google/ouNpL4bIUUfI`)

## Clarifications

### Session 2026-11-15 (Locked Decisions from Shared Roadmap Turns 1–4)

- **Q1 (Mode Chaining Workflow)**: Can users queue a subsequent mode both before starting and while a timer is already active, and what is the queue depth?
  - **A**: Pre-Plan & Live Chaining (`IDLE`, `PREP`, `WORK`, `REST`, `TRANSITION`, and `FINISHED`) with a `Current + 1 Next Slot` depth (`activeConfig` + `queuedNextStage`). As soon as `queuedNextStage` promotes to active, the Next slot clears to `null`, allowing another subsequent mode/block to be queued cleanly. Full parity applies across both the standalone Workout Timers screen and the In-Session Timer Modal Sheet, plus synchronized compact bars and foreground notifications.
- **Q2 (Live Round & Next-Round Modification Scope)**: Do live `Next Round` duration edits persist for all future rounds or only one round, and which modes allow round and time adjustments?
  - **A**: Single-round override scope: editing `Next Round (R{currentRound+1})` applies strictly to round `currentRound + 1`. Upon entering `currentRound + 1`, the override is consumed (`nextRoundOverrideSeconds` / `nextRoundRestOverrideSeconds` reset to `null`) so `currentRound + 2` onward revert to the mode's base duration. All 6 timer modes (`EMOM`, `AMRAP`, `DEATH_BY`, `TIME_CAP`, `TABATA`, `REST`) expose `Total Rounds` (setup & live) and `Next Round` duration adjustments (`±15s` for `EMOM`/`DEATH_BY`/`REST`; `±30s` for `AMRAP`/`TIME_CAP`; separate `±5s` `Next Work` and `Next Rest` rows for `TABATA`) via both quick steppers and a tap-to-type numeric dialog enforcing `>= 5s` (`>= 0s` for Tabata rest).
- **Q3 (Last-Round Guardrail, Transition Phase & Transport Controls)**: How do live adjustments behave on the final round, how does Transition work, and how are Skip/Reset/Finish handled?
  - **A**: On the final round (`currentRound >= totalRounds`), `Rounds [-]` is disabled (`totalRounds` cannot drop below `currentRound`) and the `Next Round` row displays `"Last round — tap [ + ] on Rounds to add R{totalRounds + 1}"`; tapping `[ + ]` unlocks `R{c+1}` at base duration, while tapping `[ - ]` back to `currentRound` clears any pending `R{c+1}` override. Between queued stages, when `transitionSeconds > 0`, the engine enters `TimerPhase.TRANSITION` (amber badge/arc `#D97706` light / `#F59E0B` dark, 3-2-1 cue + 0 beep/vibrate, `[-15s] [t ✎] [+15s]` steppers, `Up Next` preview with `[ Edit ✎ ]`, and middle `[ ⏭ Start Now ]` button that starts Stage 2 immediately even while paused). `SKIP` advances 1 granular step (`PREP -> R1 WORK`, `TABATA WORK -> REST` if rest > 0, `c < totalRounds -> c+1`, final round -> `TRANSITION` or direct Stage 2 or `FINISHED`). `RESET` calls `reset()` directly when single-mode or opens a 2-option confirmation dialog (`"Restart Current Mode (Keep Next Mode)"` vs `"Stop & Clear All"`) when queued/multi-stage/transitioning. All 3 transport buttons use compact horizontal non-wrapping pills (`52.dp` height, `maxLines = 1`, `softWrap = false`, hide icon at `fontScale >= 1.25f`).

---

## User Scenarios & Testing *(mandatory)*

### User Story 1 - Mode Chaining & Dedicated Transition Phase (Priority: P1)

As an athlete performing a multi-part WOD (e.g., E3MOM strength followed by a 10-minute AMRAP finisher), I want to configure or modify an upcoming workout mode and optional rest transition before or while the current timer is running, so my workout flows continuously without stopping to set up the next timer.

**Why this priority**: Eliminates mid-workout dead time and phone fumbling between consecutive WOD segments; forms the core multi-stage capability of SPEC-023.

**Independent Test**: Can be fully tested in JVM engine tests and Compose UI by queuing an AMRAP after an EMOM (with 60s or 0s transition), observing promotion into `TRANSITION` and then Stage 2 `WORK` (with `completedStagesCount` incrementing and `queuedNextStage` clearing to `null`), and verifying `continueFromFinished()` starts a new mode from `FINISHED`.

**Acceptance Scenarios**:

1. **Given** the timer is `IDLE`, **When** the athlete opens `+ Choose Next Mode & Transition Time`, picks a 10-minute AMRAP with a 60s transition, and starts the timer, **Then** the engine completes all rounds of Stage 1 and automatically enters `TimerPhase.TRANSITION` counting down 60s with a preview of the AMRAP and `[ ⏭ Start Now ]` available.
2. **Given** a workout is actively running in Stage 1 (`WORK` or `REST`) or in `TRANSITION` or `FINISHED`, **When** the athlete taps `+ Choose Next Mode & Transition Time` or `[ Edit ✎ ]`, modifies the queued mode or transition seconds (including `0s` immediate switch), **Then** the current active clock keeps ticking uninterrupted and the updated queue applies at the boundary.
3. **Given** `TimerPhase.TRANSITION` is active (running or paused) with a queued Stage 2 mode, **When** `transitionSeconds` reaches `0` (with 3-2-1 warnings and completion cue) OR the athlete taps `[ ⏭ Start Now ]`, **Then** Stage 2 starts immediately in `TimerPhase.WORK` at Round 1, `completedStagesCount` increments by 1, `queuedNextStage` resets to `null`, and the Up Next slot becomes free for a subsequent stage.
4. **Given** Stage 1 finishes on the final round with `queuedNextStage.transitionSeconds == 0`, **When** the timer advances past the final second or the athlete taps `SKIP`, **Then** Stage 2 promotes immediately to `TimerPhase.WORK` Round 1 without showing a `TRANSITION` countdown.
5. **Given** a multi-stage or `FINISHED` workout ends in `DONE!`, **When** the athlete taps `[ + Add Another Mode ]`, chooses a new mode and transition duration, and starts it, **Then** `completedStagesCount` continues accumulating across the workout session.

---

### User Story 2 - Unified Rounds & Single-Round Live Modification (Priority: P1)

As an athlete already inside an active timer session, I want to add or remove total rounds and tweak the duration of only the upcoming round (`Next Round`) across any of the 6 timer modes without changing subsequent rounds or restarting my current round.

**Why this priority**: Athletes frequently add an extra round mid-EMOM or tweak rest/work on the very next interval on the fly without corrupting later rounds.

**Independent Test**: Can be verified in pure JVM `TimerEngineTest` / `ContinuousTimerTest` cases across all 6 modes (`EMOM`, `AMRAP`, `DEATH_BY`, `TIME_CAP`, `TABATA`, `REST`) checking `adjustTotalRounds`, `adjustNextRoundDuration`, `adjustNextRoundRestDuration`, single-round override consumption upon entering `currentRound + 1`, and reversion to base duration on `currentRound + 2`.

**Acceptance Scenarios**:

1. **Given** an athlete configures any of the 6 modes (`EMOM`, `AMRAP`, `DEATH_BY`, `TIME_CAP`, `TABATA`, `REST`), **When** setting up the mode, **Then** a `Rounds` selector is available (`EMOM` default 10, `TABATA` default 8, `DEATH_BY` default 15, `AMRAP`/`TIME_CAP`/`REST` default 1) and multi-round execution runs `Round 1..N` sequentially for every mode.
2. **Given** the timer is in Round 2 of a 5-round EMOM (base 60s), **When** the athlete taps `[ +15s ]` twice on `Next Round (R3)` (making R3 = 90s), **Then** Round 2 continues uninterrupted at 60s, Round 3 executes with 90s, Round 3 consumes the override, and Round 4 starts at the base 60s again.
3. **Given** an active `TABATA` timer in Round 2 of 8, **When** the athlete adjusts `Next Work (R3)` (`±5s`, min 5s) or `Next Rest (R3)` (`±5s`, min 0s) or taps a value chip (`[ {val} ✎ ]`) to enter an exact integer value, **Then** `WORK` in R3 uses the overridden work seconds, `REST` in R3 uses the overridden rest seconds (or skips rest directly into R4 if set to `0s`), and R4 reverts to base `workSeconds` and `restSeconds`.
4. **Given** the timer is on the final round (`currentRound == totalRounds`), **When** viewing the live card, **Then** `Rounds [-]` is disabled and `Next Round` displays `"Last round — tap [ + ] on Rounds to add R{totalRounds + 1}"`; tapping `[ + ]` increases `totalRounds` and unlocks editing for `R{c+1}`, whereas tapping `[ - ]` back down to `currentRound` clears any pending `R{c+1}` override.

---

### User Story 3 - Active Timer Layout, Anti-Wrap Transport Controls & Cross-Surface Parity (Priority: P1)

As an athlete viewing the timer on a narrow phone (`360dp`) or with enlarged accessibility text (`fontScale = 1.3f`), or monitoring a workout from inside the Session Editor bar, Sub-Block inline card, or Lock-Screen notification, I want a focused non-wrapping transport bar and consistent continuous timer state everywhere.

**Why this priority**: Prevents unreadable vertically wrapped button text (like letter-by-letter wrapping on `SKIP`/`RESET`/`RESUME`), ensures safe `RESET` confirmation when chained, and prevents state drift between standalone Timer, Session Editor sheet, docked bar, and background `TimerService`.

**Independent Test**: Verified via `ContinuousTimerUiResolver` unit tests (verifying labels, confirmation rules, `TIME_CAP` count-up formatting, and polite announcements), `TimerNotificationSpec`/`TimerNotificationFormatter` tests for `TRANSITION`, `TimerTeardownController` tests for keeping `TRANSITION` alive, and Compose compilation/layout constraints.

**Acceptance Scenarios**:

1. **Given** the athlete starts a timer on either `TimerScreen` or `InSessionTimerSheet`, **When** `phase != IDLE`, **Then** the mode configuration form collapses/hides and renders the unified `ActiveContinuousTimerCard` with phase badge/ring, tabular (`tnum`) countdown/count-up clock, live `Rounds` & `Next Round` controls, `Up Next` queue card, and 3-pill horizontal transport row (`[ Reset ]` `[ Pause/Resume/Start Now ]` `[ Skip ]`) that never wraps labels vertically (`maxLines = 1`, `softWrap = false`).
2. **Given** a timer has `queuedNextStage != null`, `completedStagesCount > 0`, or is in `TimerPhase.TRANSITION`, **When** the athlete taps `Reset`, **Then** a confirmation dialog appears offering `"Restart Current Mode (Keep Next Mode)"` (restarts Stage current R1 while preserving queue/completed count) and `"Stop & Clear All"` (clears queue, overrides, and `completedStagesCount`); when none of those hold, `Reset` resets immediately without a dialog.
3. **Given** the timer enters `TimerPhase.TRANSITION`, **When** `TimerService` and `TimerTeardownController` observe the snapshot, **Then** the foreground service remains active (`onSnapshotUpdated` returns `false`), the notification title reads `"Transition - Up Next: <NextMode>"`, and its Next button reads `"Start Now"`.

### Edge Cases

- **Countdown floor validation**: Tap-to-type numeric entry and decrement buttons must clamp `nextRoundOverrideSeconds` to `>= 5` seconds (`1..3600` capped sensibly, minimum 5s), `nextRoundRestOverrideSeconds` for `TABATA` to `>= 0` seconds, and `transitionSeconds` to `>= 0` seconds.
- **`0s` Tabata Rest Override or Base Rest**: When `activeRoundRestSeconds == 0` at the end of a `TABATA` `WORK` phase (or when skipping `WORK`), the engine must skip the `REST` phase and proceed directly to `R{c+1} WORK` (or queue transition / `FINISHED` on the final round).
- **`totalRounds` Lower Bound & Override Purge**: Calling `adjustTotalRounds(-1)` or `setTotalRounds(n)` must clamp `n >= currentRound`. Whenever `totalRounds` shrinks down to `== currentRound`, any pending `nextRoundOverrideSeconds` and `nextRoundRestOverrideSeconds` must be cleared immediately.
- **Total Remaining Time Calculation with Overrides**: `calculateTotalSecondsRemaining` must factor in remaining time of the active round, overridden duration if `currentRound < totalRounds`, plus base durations of remaining rounds after `currentRound + 1`, updating deterministically upon every live edit without stalling or negative values.
- **`TIME_CAP` Clock Display vs Remaining Time**: While internal countdown tracking uses `roundSecondsRemaining`, during `TimerPhase.WORK` in `TimerMode.TIME_CAP` the primary display formats `roundSecondsElapsed` as count-up elapsed time alongside the remaining cap label (`Cap MM:SS`).
- **`previousRound()` Safety**: Rewinding (`previousRound()`) during `IDLE`, `PREP`, `TRANSITION`, or single-round `currentRound == 1` stays a no-op (preserving all existing multi-round rewind behaviors and `FINISHED` rewind behavior).
- **Audio/Haptic Cues During `TRANSITION` and `0s` Jump**: During `TRANSITION`, 3-2-1 remaining triggers short beep + short vibration, reaching `0` triggers high beep + long vibration; promoting immediately when `transitionSeconds == 0` also cues Stage 2 start appropriately.
- **Session Editor Sheet Re-Opening**: Launching `InSessionTimerSheet` with an idle timer presents the full 6-mode setup + `Up Next` queue configuration; if an engine is already running or transitioning, opening the sheet immediately attaches to the live `ActiveContinuousTimerCard` without clobbering active state unless explicitly replaced via the conflict dialog.

---

## Requirements *(mandatory)*

### Functional Requirements

- **FR-001**: `TimerMode` MUST define mode-specific `defaultRounds` (`EMOM=10`, `AMRAP=1`, `DEATH_BY=15`, `TIME_CAP=1`, `TABATA=8`, `REST=1`) and `liveStepSeconds` (`EMOM=15`, `AMRAP=30`, `DEATH_BY=15`, `TIME_CAP=30`, `TABATA=5`, `REST=15`).
- **FR-002**: `TimerPhase` MUST include `TRANSITION("Transition")` between `IDLE`, `PREP`, `WORK`, `REST`, and `FINISHED`, handled exhaustively across engine, teardown controller, notifications, and all UI surfaces.
- **FR-003**: `WorkoutTimerConfig` MUST preserve backward-compatible defaults (`totalRounds: Int = mode.defaultRounds`, optional `targetDurationSeconds: Int? = null`) and provide pure helpers `baseWorkDurationSeconds()`, `baseRestDurationSeconds()`, `singleRoundTotalSeconds()`, and `stageTotalSeconds()`.
- **FR-004**: `QueuedTimerStage(config: WorkoutTimerConfig, transitionSeconds: Int = 60)` MUST represent the single-slot upcoming stage (`Current + 1 Next Slot`), supporting `transitionSeconds >= 0` (`0` = direct promotion).
- **FR-005**: `TimerSnapshot` MUST expose `activeConfig`, `activeRoundRestSeconds`, `nextRoundOverrideSeconds`, `nextRoundRestOverrideSeconds`, `queuedNextStage`, and `completedStagesCount` with backward-compatible defaults.
- **FR-006**: `TimerEngine.configure(newConfig: WorkoutTimerConfig, queuedNext: QueuedTimerStage? = null)` MUST remain callable with 1 argument (`SessionEditor` parity) and optionally configure `queuedNextStage` before start when not actively running.
- **FR-007**: `TimerEngine` MUST support multi-round execution (`1..totalRounds`) across ALL 6 modes (`EMOM`, `AMRAP`, `DEATH_BY`, `TIME_CAP`, `TABATA`, `REST`), honoring `activeConfig.baseWorkDurationSeconds()` / `baseRestDurationSeconds()` and `targetDurationSeconds` overrides.
- **FR-008**: `TimerEngine` MUST provide live round adjustment methods (`adjustTotalRounds(delta)`, `setTotalRounds(newTotal)`) enforcing `>= currentRound` and clearing `nextRoundOverrideSeconds` / `nextRoundRestOverrideSeconds` when `newTotal <= currentRound`.
- **FR-009**: `TimerEngine` MUST provide single-round next-round override methods (`adjustNextRoundDuration(delta)`, `setNextRoundDuration(seconds)`, `adjustNextRoundRestDuration(delta)`, `setNextRoundRestDuration(seconds)`, `clearNextRoundOverrides()`) restricted to `currentRound < totalRounds` and non-`TRANSITION`/`IDLE`/`FINISHED` phases, clamping work/round duration `>= 5s` and Tabata rest `>= 0s`, and consuming/clearing the override once `currentRound + 1` begins.
- **FR-010**: `TimerEngine` MUST provide live queue and transition methods (`setQueuedNextStage(nextStage)`, `updateQueuedNextConfig(config)`, `adjustTransitionRemaining(delta)`, `setTransitionRemaining(seconds)` enforcing `>= 0s`), usable while `IDLE`, `PREP`, `WORK`, `REST`, `TRANSITION`, or `FINISHED`.
- **FR-011**: On completing the final round (`currentRound >= totalRounds`) or skipping from it, if `queuedNextStage != null`: when `transitionSeconds > 0`, the engine MUST enter `TimerPhase.TRANSITION` counting down `transitionSeconds`; when `transitionSeconds == 0` (or when `TRANSITION` reaches `0`, or on `skipRound()` during `TRANSITION`), the engine MUST promote `queuedNextStage.config` to `activeConfig`, reset `queuedNextStage = null`, increment `completedStagesCount`, clear single-round overrides, and immediately start Round 1 `TimerPhase.WORK` (`isRunning = true`).
- **FR-012**: `TimerEngine` MUST provide `continueFromFinished(nextConfig: WorkoutTimerConfig, transitionSeconds: Int = 0)`, `restartCurrentStage()` (preserves `queuedNextStage` and `completedStagesCount`), and `stopAndClearAll()` (clears `queuedNextStage`, overrides, and `completedStagesCount = 0`).
- **FR-013**: `TimerTeardownController.onSnapshotUpdated` MUST return `false` during `TimerPhase.TRANSITION` so the foreground service stays alive between queued stages.
- **FR-014**: `TimerNotificationFormatter` and `TimerNotificationSpec` MUST format `TimerPhase.TRANSITION` notifications with title `"Transition - Up Next: <NextMode>"` (or `"Transition"` fallback) and Next action title `"Start Now"`, while preserving existing formatting for `IDLE`, `PREP`, `WORK`, `REST`, and `FINISHED`.
- **FR-015**: Shared pure resolver `ContinuousTimerUiResolver` MUST provide deterministic clock formatting (count-up elapsed for active `TIME_CAP`), next-stage labels, skip button labels (`"Start Now"` / `"Next Stage"` / `"Skip"`), `shouldConfirmReset(snapshot)`, `resolveLiveAdjustmentUiState(snapshot)`, and polite accessibility boundary announcements (never firing on ordinary 1Hz ticks).
- **FR-016**: Shared Compose components (`ContinuousTimerComponents.kt`) MUST provide `ActiveContinuousTimerCard`, `UpNextQueueCard`, `NextModeConfigSheet` (with `Modifier.imePadding()` and `AppNumericTextField`), `DurationTapToTypeDialog`, `ResetTimerConfirmDialog`, and unified `TimerModeSetupForm`, featuring amber `#D97706`/`#F59E0B` transition accents, `fontFeatureSettings = "tnum"` on numeric clocks/steppers, minimum `48.dp` interactive targets, and non-wrapping (`maxLines = 1`, `softWrap = false`, `52.dp` height, icon omitted at `fontScale >= 1.25f`) transport pills.
- **FR-017**: `TimerScreen.kt` and `InSessionTimerSheet.kt` MUST hide setup configuration while `phase != TimerPhase.IDLE`, render `ActiveContinuousTimerCard` and `UpNextQueueCard`, and wire `NextModeConfigSheet` / `ResetTimerConfirmDialog` / `continueFromFinished`.
- **FR-018**: `InSessionTimerBar.kt` and `SubBlockInlineTimer.kt` MUST handle `TimerPhase.TRANSITION` exhaustively, show up-next/skip affordances when `queuedNextStage != null` or `phase in {PREP, TRANSITION}`, and apply `tnum` tabular figures.

### Key Entities

- **`TimerMode` / `TimerPhase`**: Enums encoding mode defaults (`defaultRounds`, `liveStepSeconds`) and lifecycle states including `TRANSITION`.
- **`WorkoutTimerConfig`**: Immutable configuration for one workout stage (`mode`, `intervalSeconds`, `workSeconds`, `restSeconds`, `totalRounds`, `targetMinutes`, `targetDurationSeconds`, `prepCountdownSeconds`, audio/haptic toggles, `workoutLabel`).
- **`QueuedTimerStage`**: Value object pairing `config: WorkoutTimerConfig` with `transitionSeconds: Int` (`0` = immediate transition, default `60`).
- **`TimerSnapshot`**: Immutable UI/service state projection carrying phase, clocks, active config, active round rest seconds, single-round override values (`nextRoundOverrideSeconds`, `nextRoundRestOverrideSeconds`), `queuedNextStage`, and `completedStagesCount`.

---

## Success Criteria *(mandatory)*

### Measurable Outcomes

- **SC-001**: `TimerEngine` executes queued multi-stage workflows seamlessly (`Stage 1 -> TRANSITION -> Stage 2 -> Up Next free for Stage 3`, plus `0s` direct transitions and `Start Now` mid-transition overrides) with 100% deterministic unit test pass rate in pure JVM tests.
- **SC-002**: Live round adjustments and single-round `Next Round` overrides work across all 6 modes, strictly consuming overrides upon entering `currentRound + 1` and reverting `currentRound + 2` to base duration, verified across all 6 modes in JVM tests.
- **SC-003**: Zero regressions in all pre-existing unit tests (`TimerEngineTest`, `SharedTimerStateTest`, `TimerNotificationActionTest`, `TimerServiceTest`, `TimerTeardownTest`, `WorkoutTimerConfigParserTest`, `NotificationPermissionTest`, `NotificationTapNavigationTest`, `TimerScreenNumericMigrationTest`, `SessionEditorInSessionTimerTest`).
- **SC-004**: `TimerScreen`, `InSessionTimerSheet`, `InSessionTimerBar`, `SubBlockInlineTimer`, `TimerService`, `TimerTeardownController`, and notification specs compile cleanly, handle all `TimerPhase` values exhaustively, and pass `./gradlew.bat testDebugUnitTest lintDebug assembleDebug --no-daemon`.
- **SC-005**: Cross-platform repository script checks (`./scripts/tests/Invoke-ScriptTests.ps1`) and Android debug APK build pass cleanly with `local_test/latest.apk` synced.
- **SC-006**: Transport pills fit comfortably without vertical character wrapping on a `360dp` screen up to `fontScale = 1.3f` via compact `52.dp` height, `maxLines = 1`, `softWrap = false`, and responsive icon hiding (`fontScale >= 1.25f`).

## Assumptions

- Timer audio (`ToneGenerator`) and vibration (`Vibrator`) implementations in `TimerEngine` are safely guarded against null/headless JVM environments (`isReturnDefaultValues = true` in `app/build.gradle.kts`), matching existing engine tests.
- Workout presets (`Presets`) on `NextModeConfigSheet` can reuse quick sensible presets (e.g. `EMOM 10m`, `AMRAP 10m`, `Tabata 4m`, `Rest 2m`, `Time Cap 12m`, `Death By`) cleanly alongside full 6-mode parameter selection without requiring Room database schema changes (per Constitution Principle V).
