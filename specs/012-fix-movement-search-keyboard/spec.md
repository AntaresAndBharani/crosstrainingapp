# Feature Specification: Cycle Movement Goal Search Keyboard & Filtering

**Feature Branch**: `feat/012-fix-movement-search-keyboard`

**Created**: 2026-11-02

**Status**: Approved

**Input**: User description: "when I add a goal to the cycle and type in the box to search, the keyboard doesn't appear so I cannot type and make the search more accurate."

## User Scenarios & Testing *(mandatory)*

### User Story 1 - On-Screen Keyboard Appearance & Accurate Movement Search in Cycle Goal Selector (Priority: P1)

When adding or editing a Basic Movement Goal inside the New/Edit Cycle modal, a coach or athlete opens the `Movement (Basic)` selector and taps (or focuses) the `"Search movement…"` input box so that the device's on-screen keyboard immediately appears, allowing them to type characters to filter the movement list and select the desired movement.

**Why this priority**: Without the on-screen keyboard appearing when focusing the search box inside the cycle editor modal, users cannot type characters to filter a long list of movements and must manually scroll through the entire library.

**Independent Test**: Can be fully tested by opening the New or Edit Cycle dialog, tapping `+ Add Movement`, expanding the `Movement (Basic)` selector (`Change ▾`), verifying that the `"Search movement…"` input field receives focus and triggers the on-screen keyboard, typing a query such as `"Squat"` or `"Clean"`, verifying the movement list filters in real time to matching movements, and tapping a result to select it and collapse the picker.

**Acceptance Scenarios**:

1. **Given** the user is in the New or Edit Cycle modal and has at least one Basic Movement Goal card visible, **When** the user taps the `Movement (Basic)` selector (`Change ▾`) to open the movement search picker, **Then** the search input box (`"Search movement…"`) is displayed, receives input focus, and triggers the on-screen keyboard so the user can immediately type a search query.
2. **Given** the `Movement (Basic)` search panel is expanded, **When** the user taps inside the `"Search movement…"` input box and types text (e.g., `"squat"`), **Then** the on-screen keyboard remains active, the typed characters appear in the search box, and the list of movements below immediately filters case-insensitively to movements whose names contain the search query (e.g., `"Air Squat"`, `"Back Squat"`, `"Front Squat"`).
3. **Given** the user has filtered the movement list by typing in `"Search movement…"`, **When** the user taps one of the filtered movements, **Then** that movement becomes the selected movement for the goal, the search query resets to empty, the movement search panel closes, and the on-screen keyboard is dismissed.

---

### User Story 2 - Search Query Clearing, Toggle Collapse & Empty Filter Feedback (Priority: P2)

When searching for a movement in a Cycle Goal card, the user wants to easily clear a mistyped search query, see clear feedback when no movement matches their query, or collapse the movement search panel without losing their currently selected movement.

**Why this priority**: Enhances usability while typing with the on-screen keyboard open inside the cycle modal by providing one-tap query clearing, non-destructive collapse, and explicit empty-state feedback.

**Independent Test**: Can be tested by opening the `Movement (Basic)` selector, typing a non-matching query (`"zzzz"`), verifying the `"No matching movements found"` message appears, tapping the clear (`Clear search`) button to restore the full list, and tapping `Change ▴` to collapse the picker while keeping the previously selected movement unchanged.

**Acceptance Scenarios**:

1. **Given** the `Movement (Basic)` search box contains non-empty text, **When** the user taps the clear action inside the search box, **Then** the search query is cleared to empty and the full unfiltered movement list is restored while keeping the search panel open.
2. **Given** the user types a query in `"Search movement…"` that matches no movements in the library, **When** the filtered list is empty, **Then** the panel displays a clear `"No matching movements found"` empty-state message instead of a blank area.
3. **Given** the `Movement (Basic)` search panel is expanded, **When** the user taps the `Movement (Basic)` header row (`Change ▴`) again without selecting a new movement, **Then** the search panel collapses, any partial search query is cleared, and the previously selected movement remains intact.

---

### Edge Cases

- **Multiple Goal Cards in the Same Cycle Modal**: Opening the `Movement (Basic)` search panel on one goal card operates independently of other goal cards in the same cycle modal without leaking search text between cards.
- **Whitespace-Only or Mixed-Case Queries**: Leading/trailing whitespace is ignored and matching is case-insensitive (`"  bEnCh  "` matches `"Bench Press"`).
- **Keyboard Visibility Inside Modal Dialog**: Because the movement search box is rendered inside the cycle modal dialog, the search input field must belong to an input-method-focusable window hierarchy (rather than a non-IME-focusable popup overlay) so the operating system's soft keyboard reliably opens and stays attached while typing.
- **Scrollable Results List With Keyboard Open**: The filtered movement list inside the expanded selector has a bounded maximum height and independent vertical scrolling so that both the search field and multiple candidate movements remain visible when the on-screen keyboard is up.

## Requirements *(mandatory)*

### Functional Requirements

- **FR-001**: Expanding the `Movement (Basic)` selector on a cycle goal card MUST render the `"Search movement…"` input field and candidate movement list within an input-method-focusable surface inside the cycle dialog so that the device's on-screen keyboard appears whenever the search box is focused or tapped.
- **FR-002**: When the user expands the `Movement (Basic)` selector, the `"Search movement…"` input field MUST automatically request input focus and invoke the on-screen keyboard so the user can begin typing immediately.
- **FR-003**: Typing in the `"Search movement…"` field MUST filter the displayed movement options in real time using a case-insensitive, trimmed substring match against each movement's name.
- **FR-004**: When `"Search movement…"` contains one or more characters, a trailing clear action (`"Clear search"`) MUST be displayed that clears the search text in a single tap without closing the selector.
- **FR-005**: When a non-blank search query matches zero movements, the selector MUST display a `"No matching movements found"` empty-state message inside the results container.
- **FR-006**: Selecting a movement from the filtered list MUST update the goal card's selected movement, trigger the existing metric and baseline auto-fill behavior for that movement, reset the search query, hide the on-screen keyboard, and collapse the selector panel.
- **FR-007**: Tapping the `Movement (Basic)` selector trigger row while the panel is already expanded MUST toggle the panel closed, reset the search query to empty, and preserve the currently selected movement.

### Key Entities

- **MovementGoalSelector State**: Represents the UI state of a goal card's movement picker (`expanded` boolean, `searchQuery` string, and `filteredExercises` list derived deterministically from `exercises` and `searchQuery`).
- **Exercise**: A movement in the training library with an `id`, `name`, `category`, `metricType`, and `unit`.

## Success Criteria *(mandatory)*

### Measurable Outcomes

- **SC-001**: 100% of interactions that expand the `Movement (Basic)` selector or tap `"Search movement…"` inside the New/Edit Cycle modal successfully focus the search input and request the on-screen keyboard without being blocked by non-IME popup window flags.
- **SC-002**: Users can narrow down the movement list to a specific exercise by typing 2–4 characters and select it in under 5 seconds.
- **SC-003**: 100% of unit and UI tests for movement search filtering, query clearing, empty-state display, and cycle goal configuration pass with zero regressions.

## Assumptions

- Rendering the expanded `"Search movement…"` box and scrollable movement list inline within the `Movement (Basic)` selector card (directly beneath the `[ 🔍 <Movement>   Change ▾/▴ ]` trigger row) preserves the exact visual layout shown in the cycle editor while eliminating the nested non-IME-focusable popup window that prevented the Android soft keyboard from appearing.
- All existing behavior when a movement is selected (inferring default `Goal Metric`, auto-filling `Start` and `Target` from historical records, and keeping `By exercise`, `By routine`, and `Cycle goals` backward compatible) remains unchanged.
