# Requirements Quality Checklist: Persistent Active Workout Draft & Unified Live Session Dock

**Purpose**: Reviewer-owned quality audit of requirements completeness, clarity, and UX/UI arbitration across `spec.md` and `plan.md`.
**Created**: 2026-11-28
**Feature**: [spec.md](file:///c:/Users/rogal/workspaces/ws-gym/crosstrainingapp/specs/032-persistent-workout-draft/spec.md)

## Draft Persistence, Durability & Data Isolation

- [x] CHK001 Are all Log form fields (cycle, date, title, notes, expanded state, routine metadata, template sync flag, complexes, exercises, sets, skips, metrics, PR overrides, legacy blocks, scroll offset) explicitly enumerated in FR-001?
- [x] CHK002 Is the Idle vs. In-Progress activation boundary unambiguously defined without requiring a manual "Start Workout" button (FR-002)?
- [x] CHK003 Are both debounced auto-save and synchronous background/disposal flush behaviors specified for OS process-kill durability (FR-003, Edge Cases)?
- [x] CHK004 Is corrupt or incompatible stored draft recovery defined cleanly without crashing or blocking the user (FR-003, Edge Cases)?
- [x] CHK005 Is Real vs. Demo data-mode draft isolation explicitly specified so toggling Demo Mode never pollutes or deletes an active Real workout (FR-004)?
- [x] CHK006 Is `History -> Edit/Copy Session` isolation from the active Log draft explicitly bounded, including dirty-edit confirmation when tapping the Live Session Dock (FR-010)?

## Single-Dock Arbitration, Lifecycle Controls & Gym Ergonomics

- [x] CHK007 Does the Single-Dock Arbitration matrix cover every combination of `(Log, Timer, Edit/Copy, Other Tabs) × (Draft Active/Idle) × (Timer Active/Idle)` with zero double-stacked bars (FR-005)?
- [x] CHK008 Are tap behaviors for the workout region, timer status pill, and in-place Play/Pause & Dismiss controls unambiguously specified (US2 AC4, Assumptions)?
- [x] CHK009 Is 1Hz timer tick recomposition isolation specified so non-timer UI trees do not recompose every second (plan.md Technical Context & Structure)?
- [x] CHK010 Are Log bottom-navigation badge and Drawer `"Active"` pill visibility rules specified, including Coach-mode reachability (FR-006)?
- [x] CHK011 Is the `⋮ -> Discard workout` menu placement, Idle-state hiding, confirmation dialog, and linked-vs-standalone timer teardown rule explicitly defined (FR-007)?
- [x] CHK012 Are Save Session draft cleanup and finished/linked timer reset rules defined (FR-008)?
- [x] CHK013 Is Daily Routine loading protected with `Replace current / Append blocks / Cancel` when an active draft exists, while `Library -> Log Complex` appends cleanly (FR-009)?
- [x] CHK014 Is the prior-day Stale Draft recovery banner (`sessionDate < today`, `Use Today` | `Discard`) clearly specified (FR-011)?
- [x] CHK015 Are `48dp` non-focus-stealing touch targets, `>= 8dp` spacing, `tnum`, vector-only icons, `360dp`/`1.3f` layout safety, 4-theme contrast, and atomic TalkBack labels specified (FR-012, design-system override)?

## Notes

- Audited against `specs/032-persistent-workout-draft/spec.md`, `specs/032-persistent-workout-draft/plan.md`, and `design-system/crosstraining/pages/persistent-workout-draft.md`. All 15 items verified and satisfied (`[x]`).
