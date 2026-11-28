# Requirements Quality Checklist: Timer Queue Engine & UX (`SPEC-031`)

**Purpose**: Verify specification and design-system completeness for queue extrapolation, multi-stage engine state transitions, gym ergonomics, accessibility, and test parity prior to task breakdown.
**Created**: 2026-11-28
**Feature**: [spec.md](file:///c:/Users/rogal/workspaces/ws-gym/crosstrainingapp/specs/031-routine-timer-queue/spec.md) | [plan.md](file:///c:/Users/rogal/workspaces/ws-gym/crosstrainingapp/specs/031-routine-timer-queue/plan.md)

## Queue Extrapolation & Domain Rules

- [x] CHK001 Starting block `#k` pre-populates `E3MOM (180s) × {sets}` while allowing selection of all 6 timer modes (`EMOM/ExMOM`, `AMRAP`, `Death By`, `Time Cap`, `Tabata`, `Rest`) (FR-002).
- [x] CHK002 Remaining blocks `#k+1 … #N` always extrapolate to `E3MOM (180s) × {sets}` preceded by `60s` (`1:00`) transitions, ignoring block notes/instructions/timing tokens (FR-002).
- [x] CHK003 Set-count resolution uses routine `targetSetsCount` → prior session sets → card default tiles (`3` blocks, `4` standalone exercises) and clamps `>= 1` (FR-001).
- [x] CHK004 `prepCountdownSeconds` runs strictly on Stage 1; extrapolated Stages `2..N` configure `prepCountdownSeconds = 0` (FR-003).
- [x] CHK005 Repeated block/exercise names remain isolated by unique `blockKey` and `QueuedTimerStage.id` (Edge Cases).

## Multi-Stage Engine & State Machine Invariants

- [x] CHK006 `TimerSnapshot.queuedStages` is an immutable `List<QueuedTimerStage>` with computed backward-compatible `queuedNextStage` (FR-004, Data Model).
- [x] CHK007 Stage promotion pops `head` cleanly and retains `drop(1)` across 3+ consecutive blocks (FR-004).
- [x] CHK008 Mid-transition `removeQueuedStage` updates `Up Next` when tail stages remain or calls `finishTimer()` when empty (FR-005).
- [x] CHK009 Mid-transition `updateQueuedStage` on head updates `roundSecondsRemaining` or promotes immediately when transition set to `0s` (FR-005).
- [x] CHK010 `calculateTotalSecondsRemaining` accounts for active stage + all queued transitions and stages across every phase (FR-006).

## Entry Points, Conflict Resolution & Reset Semantics

- [x] CHK011 Top-Bar Timer in `Log Session` opens `InSessionTimerSheet` anchored at Block 1 (or active timer) when `>= 1` block exists, and falls back to `TimerScreen` when `0` blocks exist (FR-008).
- [x] CHK012 Tapping an already-active block's timer icon expands the running sheet directly; tapping a different block prompts `Rebuild Queue` vs `Cancel` (FR-012).
- [x] CHK013 `ResetTimerConfirmDialog` distinguishes `Restart Current Block (Keep Queue)` vs `Stop & Clear Full Queue` (FR-013).

## Gym UX, Design System & Accessibility (`MASTER.md` + `pages/routine-timer-queue.md`)

- [x] CHK014 All interactive controls in `InSessionTimerSheet`, `InSessionTimerBar`, and `ContinuousTimerComponents` meet `>= 48.dp` touch targets with `>= 8.dp` spacing and vector-only icons (FR-014).
- [x] CHK015 All numeric clocks, round counters, transition connectors, and queue totals use `fontFeatureSettings = "tnum"` across all 4 `AppThemeMode`s (FR-014).
- [x] CHK016 `InSessionTimerBar` preserves `focusProperties { canFocus = false }` so typing set weights/reps never loses keyboard focus (FR-011).
- [x] CHK017 Active and Up-Next cards highlight cleanly in `Log Session` without crashing if a card is removed mid-timer (FR-011, Edge Cases).
- [x] CHK018 `360dp` + `fontScale = 1.3f` layout safety, merged TalkBack semantics on queue rows, boundary-only polite announcements, and 100% JVM/lint/script test parity are covered in `plan.md` (FR-014, SC-005, SC-006).

## Notes

- All 18 requirements-quality checks verified against `spec.md`, `plan.md`, `data-model.md`, and `contracts/routine-timer-queue-contract.md`. Ready for `speckit-tasks`.
