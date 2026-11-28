# Feature Specification: Persistent Active Workout Draft & Unified Live Session Dock

**Feature Branch**: `feat/032-persistent-workout-draft`

**Created**: 2026-11-28

**Status**: Complete

**Input**: User description: "When we start logging a workout, when we go to a different tab, the workout is lost. This should not happen. Help me refining this behaviour and the UX/UI part" + confirmed product decisions: (1) preserve drafts in memory and on local disk across app restarts/process kills, (2) implicit frictionless activation when meaningful input begins, (3) unified route-aware Live Session Dock above the bottom navigation bar with strict at-most-one-bar arbitration against the workout timer plus navigation indicators, and (4) Discard workout placed in the Log top-bar overflow menu with confirmation.

## User Scenarios & Testing *(mandatory)*

### User Story 1 - Lossless Workout Draft Preservation Across Navigation & Restarts (Priority: P1)

When an athlete begins logging a workout on the Log screen (by loading a daily routine, adding a complex or standalone exercise, quick-parsing notes, or typing weights, reps, durations, titles, or session notes), switching to any other bottom tab, opening any drawer destination, switching between Athlete and Coach roles, rotating the screen, or backgrounding/restarting the app MUST preserve every logged detail and scroll position without data loss.

**Why this priority**: Losing mid-workout logged sets when checking History, Progress, Library, or Timers (or when the OS closes the app in the background between sets) is the core bug and breaks gym-floor trust.

**Independent Test**: Start logging a workout with a routine, complexes, set weights/reps, PR overrides, notes, and a non-zero scroll position; navigate across History, Progress, Library, Cycles, Timer, and Profile, toggle Coach/Athlete role, and simulate an app restart; returning to Log restores 100% of entered values and scroll offset, while an untouched clean Log screen remains idle with no phantom draft.

**Acceptance Scenarios**:

1. **Given** the athlete is on a clean untouched Log screen, **When** they switch tabs without adding blocks or entering text, **Then** no active workout draft is created and no active indicators are shown.
2. **Given** the athlete loads a routine or enters sets, weights, reps, metric values, PR selections, or title/notes on Log, **When** they navigate to History, Progress, Cycles, Library, Timer, or Profile and return to Log, **Then** every entered field, expanded/collapsed state, routine-sync toggle, and scroll position is restored intact.
3. **Given** the athlete has an in-progress workout draft, **When** they switch to Coach mode (where Log is hidden from the bottom navigation bar) or background/restart the app, **Then** the in-progress draft remains preserved on local device storage and rehydrates automatically on return or next launch.

---

### User Story 2 - Unified Live Session Dock & Navigation Awareness (Priority: P1)

While a workout is in progress and the athlete is on any screen other than Log, a single compact **Live Session Dock** above the bottom navigation bar and subtle navigation indicators keep the active workout one tap away—without ever double-stacking a second bar alongside the active workout timer.

**Why this priority**: Athletes frequently check previous weights in History, browse movements in Library, or adjust timers while training; a clear, clash-free dock guarantees instant 1-tap return and solves Coach-mode reachability without cluttering small screens.

**Independent Test**: Verify the Single-Dock Arbitration rules across all combinations of `(Current Screen) × (Active Draft Present) × (Active Timer Present)` to confirm at most one docked bar is ever shown, tapping the workout zone returns to Log, and tapping timer controls operates in place without unintended navigation.

**Acceptance Scenarios**:

1. **Given** a workout draft is in progress and no timer is active, **When** the athlete is on History, Progress, Cycles, Library, Timer, Profile, or Edit/Copy Session, **Then** a single compact Workout-only dock appears above the bottom navigation bar showing the workout title, progress summary, and a tap-to-resume cue, plus a badge dot on the Log bottom-navigation tab and an "Active" pill on Log Workout in the navigation drawer.
2. **Given** both a workout draft and a workout timer are active, **When** the athlete is on Log, **Then** only the existing full in-session timer bar is shown (no duplicate workout dock); **When** the athlete is on Timer, **Then** only the Workout-only dock is shown (no duplicate timer controls); **When** the athlete is on History, Progress, Cycles, Library, Profile, or Edit/Copy Session, **Then** a single unified Workout + Timer dock is shown in one compact row (never two stacked bars).
3. **Given** no workout draft is active but a workout timer is active, **When** the athlete navigates to non-Log, non-Timer screens, **Then** a single compact Timer-only dock is shown so timer visibility persists across tabs; **When** the athlete is on Edit/Copy Session, the inner editor timer bar is suppressed so only the single global dock renders.
4. **Given** the unified Workout + Timer dock is visible, **When** the athlete taps the Play/Pause or Finished-Dismiss button, **Then** the timer pauses, resumes, or dismisses in place without navigating away from the current tab; **When** the athlete taps the workout info area, **Then** the app returns to Log; **When** the athlete taps the timer status pill, **Then** it opens the workout's timer sheet on Log (for workout-linked timers) or opens the Timer screen (for standalone timers).

---

### User Story 3 - Safe Lifecycle Controls: Overflow Discard, Save Completion & Stale Draft Recovery (Priority: P2)

Because leaving the Log tab no longer discards a workout, athletes have an intentional, accident-resistant **Discard workout** action in the Log top-bar overflow menu, clean automatic draft completion on Save, and a helpful recovery banner if an unsubmitted draft from a previous calendar day is found.

**Why this priority**: Persistent drafts require explicit abandonment controls (protected against sweaty-finger mid-set mis-taps) and clear handling when returning the next day to an unsubmitted workout.

**Independent Test**: Verify the top-bar overflow menu hides Discard when Log is clean, shows Discard when active with a confirmation dialog that stops workout-linked timers while preserving standalone timers, clears the draft cleanly upon Save Session, and surfaces a non-blocking recovery banner when a prior-day draft is opened.

**Acceptance Scenarios**:

1. **Given** the Log screen is clean/idle, **When** the athlete views the top bar, **Then** the Discard workout action is hidden or unavailable.
2. **Given** an active workout draft is in progress, **When** the athlete opens the Log top-bar overflow menu and selects **Discard workout**, **Then** a confirmation dialog appears before any data is erased; confirming clears the active draft from memory and local storage, resets Log to clean, removes all dock/navigation badges, and stops any timer linked to a block in that draft while keeping standalone timers running.
3. **Given** an active workout draft is saved via **Save Session**, **Then** the session is persisted, the active draft is cleared from memory and local storage, finished or workout-linked timers are cleared, Log resets cleanly, and the Live Session Dock and badges disappear.
4. **Given** a persisted draft from an earlier calendar date (`draftDate < today`) is present on Log, **When** the athlete views Log, **Then** a compact non-blocking banner notifies them of the resumed prior-day draft with one-tap actions to **Use Today** (updating the draft date to today) or **Discard**.

---

### User Story 4 - Cross-Screen Isolation & Routine Overwrite Protection (Priority: P2)

Editing or copying a past session, browsing or switching Demo Data mode, and triggering a routine or complex from Library must never silently destroy an active workout draft.

**Why this priority**: Once a workout draft persists across tabs, cross-tab triggers (`Library -> Log Routine`, `History -> Edit Session`, `Demo Mode` toggle) would cause surprise data loss if they overwrote or leaked into the active draft.

**Independent Test**: With an active Real-Data workout draft containing logged sets, verify (a) Edit/Copy a past session does not alter the active draft, (b) toggling Demo Mode isolates Demo drafts and restores the exact Real draft upon return, (c) `Library -> Log Complex` appends without wiping existing blocks, and (d) loading a Daily Routine while a draft has content prompts Replace / Append / Cancel.

**Acceptance Scenarios**:

1. **Given** an active workout draft exists on Log, **When** the athlete opens a past session from History to Edit or Copy, **Then** the past session editor operates in complete isolation without modifying the active Log draft; if the athlete has unsaved edits in Edit Session and taps the Live Session Dock to return to Log, an unsaved-edits confirmation prevents accidental loss of Edit Session changes.
2. **Given** an active Real-Data workout draft exists, **When** the athlete enables Demo Data mode, **Then** the Real draft is safely parked and Demo mode starts with its own isolated draft space; **When** Demo Data mode is disabled, the exact Real workout draft and dock state are restored untouched.
3. **Given** an active workout draft already contains blocks or logged content, **When** the athlete taps **Log Routine** in Library or **Load Daily Routine** on Log, **Then** a prompt offers **Replace current**, **Append blocks**, or **Cancel** instead of silently overwriting the active workout.
4. **Given** an active workout draft already contains blocks, **When** the athlete taps **Log Complex** in Library, **Then** the complex is appended to the active draft (or scrolled to if already present) with a brief confirmation message, navigating to Log without losing existing logged sets.

---

### User Story 5 - Mobile Ergonomics, Multi-Theme Contrast & Accessibility (Priority: P3)

The Live Session Dock, navigation badges, overflow controls, and stale-draft banner must remain crisp, legible, and comfortable to tap one-handed on compact phone screens across all four themes and large system font scales.

**Why this priority**: Gym-floor logging occurs under bright/dim lighting, one-handed use, and assistive technologies; consistent visual polish and non-obscured scrolling prevent friction across every app screen.

**Independent Test**: Inspect layout, contrast, touch target sizing, and screen-reader announcements across `360dp` width, `fontScale >= 1.3f`, and all four theme modes (`Dark`, `Light`, `Dark High Contrast`, `Light High Contrast`).

**Acceptance Scenarios**:

1. **Given** the Live Session Dock is visible above the bottom navigation bar, **When** the athlete scrolls to the bottom of any tab screen, **Then** no list cards, buttons, or floating action buttons are obscured behind the dock.
2. **Given** a `360dp`-wide viewport with `fontScale = 1.3f` and all four theme modes, **When** the Live Session Dock renders in Workout-only, Timer-only, or Workout + Timer mode, **Then** all interactive targets meet at least `48dp × 48dp` with `>= 8dp` spacing, text and icons meet contrast thresholds (`>= 4.5:1` text, `>= 3:1` icons/borders), numeric clocks and counters use tabular numerals without jitter, and titles truncate cleanly without clipping controls.
3. **Given** a screen reader (TalkBack) user navigates tabs or taps the Live Session Dock, **Then** the Log tab announces active workout status contextually, the dock workout zone announces as a single atomic summary, and 1Hz timer ticks inside the dock do not trigger continuous live-region announcements.

---

### Edge Cases

- **App killed before debounce completes**: Leaving the Log screen or backgrounding the app flushes the active draft immediately so edits made right before switching apps are never lost.
- **Corrupt or outdated local draft payload**: If a stored local draft payload is malformed or from an unrecognized schema version, the app discards only the unreadable payload gracefully and opens a clean Log screen without crashing.
- **Clearing all content manually**: Removing every block/exercise and clearing title/notes returns the Log screen to Idle, removes the stored draft, and dismisses the Live Session Dock and badges.
- **Timer finishes while away from Log**: When a workout-linked timer reaches `FINISHED` while the athlete is on another tab, the unified dock remains usable with an inline Dismiss (`×`) control; saving the session clears both the draft and the finished timer.
- **System date rollback or future-dated workout**: Setting a workout date to today or a future date does not trigger the stale prior-day banner (`isStale` is true strictly when `sessionDate < today`).

## Requirements *(mandatory)*

### Functional Requirements

- **FR-001**: System MUST preserve an in-progress Log workout draft (selected cycle, session date, title, notes, details-expansion state, loaded routine metadata and template-sync toggle, all complexes and standalone exercises with sets/reps/weights/metric inputs/skipped flags/PR selections & overrides, legacy blocks, and scroll offset) across all bottom-tab switches, drawer navigation, Coach/Athlete role switches, and configuration changes.
- **FR-002**: System MUST automatically enter the **In-Progress** state without an extra "Start Workout" gate as soon as the athlete adds a block/exercise, loads a routine, quick-parses notes, or enters non-blank workout details; an untouched clean Log screen MUST remain **Idle**.
- **FR-003**: System MUST persist active drafts to local device storage (via debounced auto-save after edits plus immediate flush when leaving Log or backgrounding the app) so drafts survive process termination and app restarts, and MUST fail safely to an Idle state if stored draft data is corrupted or incompatible.
- **FR-004**: System MUST isolate persisted drafts by data mode (`Real Data` vs. `Demo Data`), flushing and parking the active mode's draft before switching modes and restoring the target mode's draft upon return.
- **FR-005**: System MUST render at most **one** docked status bar above the bottom navigation bar across the entire app according to the Single-Dock Arbitration rules:
  - On `Log`: show no global dock; show only the in-session timer bar when a timer is active.
  - On `Timer`: show the Workout-only dock when a workout draft is active, and no dock when no draft is active.
  - On `History`, `Progress`, `Cycles`, `Library`, `Profile`, and `Edit/Copy Session`: show `Workout-only` (draft only), `Timer-only` (timer only), `Workout + Timer` unified single-row dock (both active), or nothing (neither active), while suppressing any inner duplicate editor timer bar on `Edit/Copy Session`.
- **FR-006**: System MUST show a badge indicator on the `Log` bottom-navigation item whenever a workout draft is In-Progress and the athlete is on another screen, and MUST show an `"Active"` badge on the `Log Workout` drawer item whenever a workout draft is In-Progress (including while in Coach view).
- **FR-007**: System MUST provide a **Discard workout** action inside the `Log` top-bar overflow menu (`⋮`), visible only when the draft is In-Progress, guarded by a confirmation dialog. Confirming Discard MUST clear the active draft from memory and local storage, reset `Log` to Idle, dismiss the dock and badges, and stop any active timer linked to a block in that draft while leaving standalone timers running.
- **FR-008**: System MUST clear the active draft from memory and local storage upon successful **Save Session**, dismiss the dock and badges, reset `Log` to Idle, and clear finished or workout-linked timers.
- **FR-009**: System MUST prompt the athlete with **Replace current**, **Append blocks**, or **Cancel** whenever loading a Daily Routine (from `Library -> Log Routine` or `Log -> Load Daily Routine`) while the active draft already has content, and MUST append cleanly (without overwriting existing blocks) when adding a complex via `Library -> Log Complex`.
- **FR-010**: System MUST keep `History -> Edit Session` and `Copy Session` isolated from the active `Log` draft, and MUST prompt before discarding unsaved `Edit Session` changes if the athlete taps the Live Session Dock while editing a dirty past session.
- **FR-011**: System MUST display a non-blocking **Stale Draft** recovery banner at the top of `Log` whenever a restored active draft has a session date earlier than today (`sessionDate < today`), offering **Use Today** (updating the draft date to today) and **Discard** actions.
- **FR-012**: System MUST integrate the global Live Session Dock with outer scaffold padding so no underlying screen content or action buttons are obscured, while enforcing `>= 48dp` non-focus-stealing touch targets, `>= 8dp` spacing, vector-only icons, tabular numerals (`tnum`), 4-theme contrast parity, `360dp` / `fontScale >= 1.3f` layout safety, and atomic TalkBack labels without 1Hz live-region chatter.

### Key Entities

- **Active Workout Draft**: The complete, serializable snapshot of an unsubmitted workout session on the Log screen (metadata, routine sync context, ordered complexes/exercises with per-set values and PR tracking, legacy blocks, scroll position, and last-updated timestamp), scoped separately per data mode (`Real` vs `Demo`).
- **Active Workout Summary**: Lightweight read-only summary derived from the Active Workout Draft (`isActive`, `displayTitle`, `blockCount`, `loggedBlockCount`, `loggedSetCount`, `sessionDate`, `isStale`, `blockKeys`) driving the Live Session Dock and navigation badges.
- **Live Dock State**: Deterministic presentation state resolved from `(currentRoute, ActiveWorkoutSummary, activeTimerState)` dictating dock mode (`NONE`, `WORKOUT_ONLY`, `TIMER_ONLY`, `WORKOUT_AND_TIMER`), badge visibility, inner editor bar suppression, and tap routing.

### Success Criteria *(measurable)*

- **SC-001**: 100% of logged workout draft fields (cycle, date, title, notes, routine sync state, blocks, exercises, sets, reps, weights, metrics, skips, PR overrides, legacy blocks, and scroll offset) survive switching across all 6 non-Log destinations, role toggles, screen rotations, and simulated cold process restarts.
- **SC-002**: Zero scenarios across all route × draft × timer combinations display two stacked bottom status bars simultaneously.
- **SC-003**: Tapping Play/Pause or Dismiss on the Live Session Dock completes the timer action in 1 tap without leaving the current screen, and tapping the workout region returns to the active Log workout in 1 tap (including from Coach view).
- **SC-004**: Active Real-Data drafts experience 0% data loss or contamination when toggling Demo Data mode, editing/copying past sessions in History, or triggering complexes/routines from Library.
- **SC-005**: All dock controls, overflow actions, and recovery banners render without clipping or overlap on `360dp` screens up to `fontScale = 1.3f` across all 4 built-in theme modes, and 100% of automated JVM unit tests, script tests, and lint checks pass.

## Assumptions

- **Timer-pill tap routing on unified dock**: When both a workout draft and a timer are active on a third tab (e.g., History), tapping the right-side timer status pill routes to `Log` and opens the in-session timer sheet if the timer is linked to a block in the active workout; if the timer is standalone (started from the Timer screen), it routes to the `Timer` screen. Play/Pause and Finished-Dismiss always act in place.
- **No database schema migration required**: Persisting the single active draft per data mode in local app preferences/storage keeps the Room SQLite schema unchanged and avoids unnecessary database migrations.
- **Single active draft per data mode**: Only one unsubmitted workout draft is active at a time per data mode (`Real` or `Demo`), matching the single `Log` workspace model.
