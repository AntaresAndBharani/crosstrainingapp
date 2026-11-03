# Feature Specification: Modal Card Auto-Scroll & Non-Blocking Accordion Actions

**Feature Branch**: `feat/014-fix-modal-card-scroll-actions`

**Created**: 2026-11-03

**Status**: Approved

**Input**: User description: "After adding two goals to the cycle, the buttons don't work (I cannot expand the goal, I cannot add a new goal...). This could happen to more buttons."

## Clarifications

### Session 2026-11-03

- Q: How should card expansion, validation, and scrolling behave across dialogs when adding or expanding cards near or below the scroll fold? → A: Auto-scroll to newly added, expanded, or error-flagged cards across scrollable dialogs (`CycleEditorDialog`, `RoutineEditorDialog`) AND never block expanding an existing collapsed goal card (automatically discard any untouched empty draft when the user switches to expand an existing goal).

## User Scenarios & Testing *(mandatory)*

### User Story 1 - Non-Blocking Goal Expansion & Auto-Scroll in Cycle Editor (Priority: P1)

When configuring multiple Basic Movement Goals in the New/Edit Cycle modal, a coach or athlete wants every tap on `+ Add Movement` or on a collapsed goal card's expand action (`▾` / card body) to immediately bring the target card into view and never become unresponsive after two or more goals have been added.

**Why this priority**: Directly resolves the critical bug where adding a third goal after two collapsed goals placed an empty expanded card off-screen below the modal scroll boundary, silently locking both the `+ Add Movement` button and the expand buttons on existing goals.

**Independent Test**: Can be fully tested by opening `Edit cycle` (in either Strength or Fat Loss & BB mode), adding two movement goals and collapsing them, tapping `+ Add Movement` to add a third goal (verifying the dialog automatically scrolls down so `Goal 3` is visible), and tapping `▾` on `Goal 1` or `Goal 2` while `Goal 3` is still an untouched empty draft (verifying the untouched empty `Goal 3` is automatically discarded and the tapped goal immediately expands and scrolls into view).

**Acceptance Scenarios**:

1. **Given** the New/Edit Cycle modal has two (or more) collapsed movement goals near the bottom of the visible viewport, **When** the user taps `+ Add Movement`, **Then** the newly created expanded goal card is appended and the modal automatically scrolls so the newly added card is clearly visible inside the viewport.
2. **Given** a newly added movement goal card is open with an empty or untouched target value (for example, when the default movement has no prior record), **When** the user taps a collapsed goal card (`Goal 1` or `Goal 2`) or its expand chevron (`▾`), **Then** the untouched empty draft is automatically discarded, the tapped goal card immediately expands, and the modal scrolls the expanded card into view—never locking or ignoring the user's tap.
3. **Given** an expanded goal card has been manually edited by the user to have an empty or zero target (`Target <= 0`) and the user taps `Done ▲`, `+ Add Movement`, or another goal card, **Then** the inline target validation error (`"Please enter a target value greater than 0"`) is shown on that card AND the modal automatically scrolls to that card so the user immediately sees why input is required.
4. **Given** a collapsed movement goal card positioned near the bottom edge of the modal viewport, **When** the user taps the card or its expand (`▾`) button, **Then** the card expands and the modal automatically scrolls so the expanded form controls (movement selector, metric pills, target field, and quick-stepper pills) are visible without requiring a manual blind scroll.

---

### User Story 2 - Distinct Action Hit Targets & Semantics on Collapsed Goal Cards (Priority: P1)

When interacting with collapsed movement goal cards in the New/Edit Cycle modal, the user wants the delete (`🗑`) and expand (`▾`) actions to have clear spacing and independent touch/accessibility targets so tapping expand never accidentally triggers delete and screen readers/automation tools can address each action reliably.

**Why this priority**: Prevents mis-taps between adjacent trailing action icons on compact summary cards and ensures assistive technologies and UI test harnesses can invoke both `Expand goal` and `Remove goal` independently.

**Independent Test**: Can be tested by rendering a collapsed goal summary card and verifying that tapping the card body or the `Expand goal` button expands the card, tapping the `Remove goal` button deletes only that card, and both buttons expose distinct clickable semantics nodes.

**Acceptance Scenarios**:

1. **Given** a collapsed movement goal card in the New/Edit Cycle modal, **When** the user taps the card body or the `Expand goal` (`▾`) icon button, **Then** the card expands into its edit form without affecting adjacent cards.
2. **Given** a collapsed movement goal card in the New/Edit Cycle modal, **When** the user taps the `Remove goal` (`🗑`) icon button, **Then** only that goal is removed and remaining goals re-index sequentially.

---

### User Story 3 - Auto-Scroll When Appending Blocks in Scrollable Workout/Routine Modals (Priority: P2)

When adding new blocks or items inside other scrollable editor modals (such as tapping `Add Block to Routine` in the New/Edit Daily Routine modal), the user wants the modal to automatically scroll to the newly added block so the button never appears unresponsive when the new block is appended below the visible scroll fold.

**Why this priority**: Prevents the same "button appears dead because new content was added off-screen below the modal scroll fold" issue in other multi-item scrollable modals across the app.

**Independent Test**: Can be tested by opening the New or Edit Daily Routine modal, tapping `Add Block to Routine` when existing blocks fill the viewport, and verifying the modal scrolls down to reveal the newly added `Block N` card.

**Acceptance Scenarios**:

1. **Given** the New/Edit Daily Routine modal contains one or more routine blocks that fill the visible modal viewport, **When** the user taps `Add Block to Routine`, **Then** a new block card (`Block N`) is appended and the modal automatically scrolls down so the newly added block is visible in the viewport.

---

### Edge Cases

- **Tapping `+ Add Movement` when the default first movement in the library has no historical best record**: The newly appended goal draft initializes with an empty target (`targetWeight = ""`, `isManuallyEdited = false`) and scrolls into view immediately. If the user decides not to fill it and instead taps `Goal 1` or `Goal 2` to expand that existing goal, the untouched empty draft is cleanly removed so `Goal 1` or `Goal 2` expands without obstruction.
- **Tapping `+ Add Movement` repeatedly while an open card still has an empty target**: The open card flags `showTargetError = true` (`"Please enter a target value greater than 0"`) and the modal scrolls directly to that open card so the user sees the inline error message even if they had scrolled away.
- **Changing movement, metric, or RM on a newly added goal where no historical record exists**: If the user actively interacted with the card (changed movement, changed metric, changed RM, or typed in Target) and left Target empty (`""`), attempting to collapse via `Done ▲` or add another goal via `+ Add Movement` highlights the inline error and scrolls the card into view; however, if the user taps another existing collapsed goal card (`Goal 1` / `Goal 2`) while the current card has no valid target:
  - If the current open card is an unsaved draft (`id == 0`) with an empty target (`!hasValidTarget`), it is discarded or collapsed only if valid; specifically, an untouched empty draft (`id == 0 && !isManuallyEdited && !hasValidTarget`) is auto-discarded, and if a draft was edited to have an empty target, switching to another goal either discards an unsaved empty draft (`id == 0 && !hasValidTarget`) or restores/keeps valid state so expanding an existing saved goal is **never blocked**.
- **Collapsing an expanded goal card when the modal is scrolled all the way to the bottom**: The scroll state adjusts smoothly without leaving dead/unclickable buttons or stranded scroll offsets.

## Requirements *(mandatory)*

### Functional Requirements

- **FR-001**: In the New/Edit Cycle modal (`CycleEditorDialog`), tapping `+ Add Movement` MUST automatically scroll the modal's vertical scroll container (or bring the target card into view) so the newly appended expanded goal card is visible within the modal viewport.
- **FR-002**: In the New/Edit Cycle modal (`CycleEditorDialog`), tapping a collapsed goal card or its `Expand goal` (`▾`) button MUST **never** be silently blocked by another open empty draft:
  - Any open unsaved draft (`id == 0`) that has no valid target (`!hasValidTarget`) MUST be automatically discarded when the user taps to expand an existing collapsed goal card (`Goal 1`, `Goal 2`, etc.), and if an existing saved goal (`id != 0`) was open with its target cleared, its target error is shown or it is collapsed/reverted as appropriate—Specifically, expanding a collapsed goal card MUST always succeed when the open card is an empty unsaved draft (`id == 0 && !hasValidTarget`).
  - Even simpler and more predictable: whenever the user taps to expand a collapsed goal card at `targetIndex`, if another open card has `!hasValidTarget` and is an unsaved draft (`id == 0 && !isManuallyEdited`), it is automatically removed (and `targetIndex` adjusted if needed) so the requested card always expands! Furthermore, if an unsaved draft (`id == 0`) has `!hasValidTarget` (even if the user changed its movement dropdown or RM preset without entering a target yet), tapping to expand another collapsed goal card (`Goal 1` or `Goal 2`) MUST discard that incomplete unsaved draft (`id == 0 && !hasValidTarget`) and expand the tapped goal card so the user is never trapped!
- **FR-003**: What if an existing saved goal (`id != 0`, or a previously valid goal that had a target before being cleared) has its target manually cleared to `""` or `0`? If the user taps to expand another collapsed goal card, or taps `Done ▲` / `+ Add Movement`:
  - On `Done ▲` or `+ Add Movement`: if any open card has `!hasValidTarget`, `showTargetError = true` is set on that open card AND the modal MUST automatically scroll to bring that open card into view so the error message is visible on screen.
  - On expanding another collapsed goal card (`expandGoalDraftAt`): if the currently open card has `!hasValidTarget`:
    - If it is an unsaved draft (`id == 0`) that has not been given a valid target (or is untouched), it is automatically discarded so the tapped goal card immediately expands.
    - Even if an existing card had its target cleared, if the user taps another card to expand it, either auto-discard unsaved empty drafts (`id == 0 && !hasValidTarget`) or, if `id != 0` (or ` fallbackLastValidTarget`), restore/scroll to it so expanding an existing goal is never silently ignored. To guarantee **"never block expanding an existing goal"**, if any open card has `!hasValidTarget` when the user explicitly taps to expand another collapsed goal card:
      - If the open card has `id == 0` (newly added unsaved goal with no valid target), remove it and expand the tapped card.
      - If the open card has `id != 0` (existing saved goal whose target was cleared during editing), restore its last valid target (or keep it collapsed if positive, or remove if empty) so the tapped goal card ALWAYS expands!
- **FR-004**: Whenever a goal card transitions from collapsed to expanded in `CycleEditorDialog`, the modal MUST automatically bring the expanded card into the visible scroll viewport so its input controls are immediately accessible.
- **FR-005**: `CollapsedGoalSummaryCard` MUST provide proper touch target separation (`Arrangement.spacedBy(4.dp)` or greater) and non-swallowing accessibility semantics between the card body, `Remove goal` (`🗑`), and `Expand goal` (`▾`) actions.
- **FR-006**: In `RoutineEditorDialog` (`LibraryScreen.kt`), tapping `Add Block to Routine` MUST automatically scroll the modal's vertical scroll container to the newly added block so the user immediately sees the new block card.

### Key Entities

- **GoalDraftState**: UI state model for a movement goal in `CycleEditorDialog`, including `id`, `exercise`, `goalMetric`, `reps`, `timeWindowMinutes`, `targetWeight`, `lastValidTargetWeight` (to safely restore if an existing goal's target is cleared and the user switches to another card), `isCollapsed`, `isManuallyEdited`, and `showTargetError`.
- **CycleEditorDialog / RoutineEditorDialog Scroll State**: Scroll state orchestration that scrolls to newly added, newly expanded, or validation-flagged cards inside scrollable `AlertDialog` containers.

## Success Criteria *(mandatory)*

### Measurable Outcomes

- **SC-001**: 100% of taps on `+ Add Movement` in `CycleEditorDialog` (with 0, 1, 2, or 5+ existing goals) either append a new visible expanded card and scroll it into the viewport or scroll directly to an open incomplete card displaying its inline validation message.
- **SC-002**: 100% of taps on a collapsed goal card or its `Expand goal` (`▾`) button immediately expand that goal card and scroll it into view, with zero dead-button states even if a newly added empty goal draft was previously open.
- **SC-003**: 100% of taps on `Add Block to Routine` in `RoutineEditorDialog` append the new block and scroll the modal to bring the newly added block into view.
- **SC-004**: 100% of unit tests, lint checks, and script regression tests pass with zero regressions.

## Assumptions

- When a user taps `+ Add Movement` and then taps an existing collapsed goal card (`Goal 1`, `Goal 2`, etc.) without having entered a valid positive target on the newly added draft (`id == 0 && !hasValidTarget`), the user's intent is to inspect or edit the existing goal rather than stay trapped in the blank new draft; discarding the blank unsaved draft matches standard mobile form UX.
- When an existing saved goal (`id != 0` or a goal that previously had a valid positive target) has its target temporarily cleared and the user taps to expand a different collapsed goal card, restoring the card's previous valid target and collapsing it allows the newly tapped goal card to expand without data loss or blocking.
