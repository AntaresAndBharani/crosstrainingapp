# Phase 0 Research: Cycle Movement Goal Search Keyboard & Filtering

## 1. Root Cause of Soft Keyboard Suppression in `MovementGoalSelector`

- **Finding**:
  - In `CyclesScreen.kt`, `MovementGoalSelector` was implemented using `ExposedDropdownMenuBox` with a `Surface` anchored via `.menuAnchor(MenuAnchorType.PrimaryNotEditable)` and an `OutlinedTextField` (`"Search movement…"`) placed **inside** `ExposedDropdownMenu { ... }`.
  - In Jetpack Compose Material 3 (`androidx.compose.material3:material3:1.3.0`), `ExposedDropdownMenu` renders inside a separate Android `PopupLayout` window. When the anchor is `MenuAnchorType.PrimaryNotEditable`, `ExposedDropdownMenu` configures `PopupProperties` with `WindowManager.LayoutParams.FLAG_ALT_FOCUSABLE_IM` (combined with `focusable = true`).
  - Under Android's `WindowManager` specification, combining `focusable = true` (no `FLAG_NOT_FOCUSABLE`) with `FLAG_ALT_FOCUSABLE_IM` inverts IME focusability: the popup window receives Compose touch/focus events—causing the `OutlinedTextField` to render its green focused border and blinking text cursor (`|Search movement…`)—while **explicitly forbidding Android's `InputMethodManager` from attaching or showing the soft keyboard for that window**.
- **Decision**:
  - Replace the nested `ExposedDropdownMenuBox` / `ExposedDropdownMenu` popup in `MovementGoalSelector` with an **inline expandable search surface** rendered directly inside the `CycleEditorDialog` (`AlertDialog`) layout hierarchy beneath the `Movement (Basic)` trigger row.
  - Attach a `FocusRequester` to the `"Search movement…"` `OutlinedTextField` and invoke `focusRequester.requestFocus()` + `LocalSoftwareKeyboardController.current?.show()` via `LaunchedEffect(state.expanded)` when expanded, while also allowing direct tap focus at any time.
- **Rationale**:
  - Because `CycleEditorDialog` is an `AlertDialog` whose window is already input-method focusable (used by `Cycle Name`, `Cycle Focus / Notes`, `Start`, and `Target` fields), an inline `OutlinedTextField` inside the dialog's normal view tree receives native Android IME focus immediately and reliably without popup window flag conflicts or detached popup positioning when the soft keyboard resizes the dialog.
- **Alternatives Considered**:
  - *Alternative A: Keeping `ExposedDropdownMenu` and switching to `MenuAnchorType.PrimaryEditable`*: Rejected because `ExposedDropdownMenu` expects the anchor itself (not an interior child of the popup) to be the editable text field, and stacking a popup window over an `AlertDialog` while the soft keyboard resizes the dialog window causes viewport clipping and premature popup dismissal on Android.
  - *Alternative B: Opening a second nested `AlertDialog` for movement selection*: Rejected because it adds modal-on-modal visual friction compared to the clean in-card expandable search box shown in the cycle goal card design.

## 2. Deterministic State & Filtering Model (`MovementSelectorUiState`)

- **Decision**:
  - Extract a pure, deterministic state holder `MovementSelectorUiState(val expanded: Boolean = false, val searchQuery: String = "")` and pure filtering function `filterMovementExercises(exercises: List<Exercise>, searchQuery: String): List<Exercise>` in `CyclesScreen.kt`.
  - State transitions (`toggleExpanded()`, `open()`, `dismiss()`, `onSearchQueryChanged(query)`, `clearSearchQuery()`, `onExerciseSelected()`) are pure functions testable in standard JUnit 4 unit tests (`CyclesScreenTest.kt`).
- **Rationale**:
  - Satisfies Constitution Principle I (UDF) and Principle III (Test-First & 100% CI Parity) by making all selector state transitions and filtering edge cases (case insensitivity, whitespace trimming, clear query, toggle collapse, selection reset) deterministically verifiable in `testDebugUnitTest`.
- **Alternatives Considered**:
  - *Keeping anonymous local `var expanded` and `var searchQuery` inline inside the composable without a testable state model*: Rejected because extracting `MovementSelectorUiState` and `filterMovementExercises` enables direct unit testing of all state transitions alongside the UI component.

## 3. Search UX Refinements (Clear Action, Empty State, Bounded Scroll)

- **Decision**:
  - Add a trailing `"Clear search"` `IconButton` (`Icons.Filled.Close`) inside the `"Search movement…"` `OutlinedTextField` whenever `searchQuery.isNotEmpty()`, which resets `searchQuery` to `""` and keeps focus/keyboard active.
  - Display `"No matching movements found"` when `filteredExercises.isEmpty()`.
  - Bound the results container with `Modifier.heightIn(max = 220.dp).verticalScroll(rememberScrollState())` so the list shrinks to wrap when few movements match and scrolls cleanly when many movements match while the soft keyboard is visible.
  - Configure `KeyboardOptions(imeAction = ImeAction.Search)` and `KeyboardActions(onSearch = ...)` so pressing the keyboard's Search action selects the top matching movement (if any) and closes the picker.
- **Rationale**:
  - Prevents blank result areas, eliminates manual backspacing of mistyped queries, and guarantees that the movement list remains scrollable inside the dialog when the soft keyboard occupies the bottom half of the screen.
