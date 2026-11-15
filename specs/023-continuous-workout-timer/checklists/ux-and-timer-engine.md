# Domain & UX Readiness Checklist: Continuous Workout Timer & Live Stage Queue

**Purpose**: Verify requirement completeness, clarity, responsiveness, accessibility, and compatibility guardrails before code generation
**Created**: 2026-11-15
**Feature**: [spec.md](file:///c:/Users/rogal/workspaces/ws-gym/crosstrainingapp/specs/023-continuous-workout-timer/spec.md)

## Engine & State Determinism

- [x] CHK001 `TimerMode` defines exact per-mode `defaultRounds` (`EMOM=10`, `AMRAP=1`, `DEATH_BY=15`, `TIME_CAP=1`, `TABATA=8`, `REST=1`) preserving legacy single-round `AMRAP`/`TIME_CAP`/`REST` test defaults
- [x] CHK002 `TimerMode` defines exact per-mode `liveStepSeconds` (`EMOM=15`, `AMRAP=30`, `DEATH_BY=15`, `TIME_CAP=30`, `TABATA=5`, `REST=15`)
- [x] CHK003 `TimerPhase` defines `TRANSITION("Transition")` and all existing exhaustive `when (phase)` sites are accounted for
- [x] CHK004 `WorkoutTimerConfig.configure(newConfig, queuedNext = null)` preserves 1-argument compatibility for `SessionEditor` and existing tests
- [x] CHK005 Single-round overrides (`nextRoundOverrideSeconds`, `nextRoundRestOverrideSeconds`) apply strictly to `currentRound + 1` and clear immediately upon entering `currentRound + 1`
- [x] CHK006 Decreasing `totalRounds` down to `currentRound` clears pending single-round overrides and clamps `totalRounds >= currentRound`
- [x] CHK007 Next-round work floor (`>= 5s`), Tabata rest floor (`>= 0s`), and Transition floor (`>= 0s`) are enforced cleanly on both steppers and tap-to-type numeric entry
- [x] CHK008 `PREP` total remaining calculation excludes `prepCountdownSeconds`, preserving existing `TimerEngineTest` and `TimerServiceTest` assertions
- [x] CHK009 `previousRound()` stays a safe no-op on `IDLE`, `PREP`, `TRANSITION`, and 1-round `AMRAP` while preserving existing multi-round/`FINISHED` rewind behavior

## Stage Queue & Foreground Service Continuity

- [x] CHK010 `Current + 1 Next Slot` depth (`queuedNextStage`) clears to `null` immediately upon stage promotion, freeing the slot for Stage 3
- [x] CHK011 Zero-second transition (`transitionSeconds == 0`) promotes directly into Stage 2 `WORK` Round 1 without rendering `TRANSITION`
- [x] CHK012 Non-zero transition (`transitionSeconds > 0`) counts down with 3-2-1 warning cues and 0 promotion cue; `[ ⏭ Start Now ]` promotes even while paused
- [x] CHK013 `TimerTeardownController.onSnapshotUpdated` returns `false` on `TimerPhase.TRANSITION` so `TimerService` stays alive between stages
- [x] CHK014 `TimerNotificationFormatter` & `TimerNotificationSpec` customize `TimerPhase.TRANSITION` (`"Transition - Up Next: ..."` and `"Start Now"`) without breaking existing `IDLE`/`PREP`/`WORK`/`REST`/`FINISHED` notification contracts

## UI, Accessibility & Cross-Surface Parity

- [x] CHK015 Both `TimerScreen` and `InSessionTimerSheet` hide setup forms while `phase != IDLE` and expose unified `ActiveContinuousTimerCard` + `UpNextQueueCard`
- [x] CHK016 `InSessionTimerBar` and `SubBlockInlineTimer` handle `TimerPhase.TRANSITION` exhaustively, display `tnum` clocks, and expose Skip during `TRANSITION` or queued chains
- [x] CHK017 Transport pill row uses `52.dp` height, `RoundedCornerShape(999.dp)`, `maxLines = 1`, `softWrap = false`, and hides leading icons at `fontScale >= 1.25f` to prevent vertical text wrapping on `360dp` devices
- [x] CHK018 Interactive controls meet `>= 48.dp` touch target minimums, numeric clocks/steppers use `fontFeatureSettings = "tnum"`, and `NextModeConfigSheet` applies `Modifier.imePadding()` with `AppNumericTextField`
- [x] CHK019 `Reset` confirmation dialog triggers only when queued, `completedStagesCount > 0`, or `phase == TRANSITION`, offering `"Restart Current Mode (Keep Next Mode)"` vs `"Stop & Clear All"`
- [x] CHK020 TalkBack polite live-region announcements trigger solely at phase or round boundary transitions and never emit per-second countdown speech
