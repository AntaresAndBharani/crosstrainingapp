# UI Contract: Cycle Movement Goal Search Selector (`MovementGoalSelector`)

## 1. Trigger Row (`Movement (Basic)`)

- **Section Label**: `Movement (Basic)` (`MaterialTheme.typography.labelSmall`)
- **Trigger Surface**:
  - Outlined rounded surface (`RoundedCornerShape(10.dp)`, `1.dp` `outlineVariant` border).
  - Left content:
    - Leading `Icons.Filled.Search` icon (`contentDescription = "Search movement"`).
    - Selected movement name (`selected?.name ?: "Select movement…"`), single-line with ellipsis.
  - Right content:
    - When collapsed (`expanded == false`): `"Change ▾"` (`MaterialTheme.colorScheme.primary`).
    - When expanded (`expanded == true`): `"Change ▴"` (`MaterialTheme.colorScheme.primary`).
  - Interaction:
    - Tapping the trigger surface toggles `expanded` (`state = state.toggleExpanded()`).
    - Collapsing via the trigger surface resets `searchQuery` to `""` and hides the soft keyboard while preserving `selected`.

## 2. Inline Expandable Search Panel (Rendered When `expanded == true`)

- **Container**:
  - Rendered **inline** inside `MovementGoalSelector`'s `Column` (within the `CycleEditorDialog` window hierarchy, NOT inside a `Popup` / `ExposedDropdownMenu` with `FLAG_ALT_FOCUSABLE_IM`).
  - Styled as an elevated/bordered `Surface` (`RoundedCornerShape(10.dp)`, `tonalElevation = 2.dp`, `shadowElevation = 4.dp`, `1.dp` `outlineVariant` border).
- **Search Input Field (`OutlinedTextField`)**:
  - Placeholder: `"Search movement…"`
  - Single-line (`singleLine = true`), `KeyboardOptions(imeAction = ImeAction.Search)`.
  - Leading icon: `Icons.Filled.Search`.
  - Trailing icon (when `searchQuery.isNotEmpty()`): `IconButton` with `Icons.Filled.Close` (`contentDescription = "Clear search"`) that clears `searchQuery` to `""` and maintains focus + soft keyboard visibility.
  - Focus & Soft Keyboard Contract:
    - Attached to a `FocusRequester`.
    - When `expanded` becomes `true`, `LaunchedEffect(Unit)` requests focus on the `OutlinedTextField` (`focusRequester.requestFocus()`) and explicitly invokes `LocalSoftwareKeyboardController.current?.show()`.
    - Tapping inside the `OutlinedTextField` at any time focuses the field within the dialog's IME-enabled window and shows the Android soft keyboard.
- **Filtered Movement List**:
  - Bounded scrollable container (`Modifier.fillMaxWidth().heightIn(max = 220.dp).verticalScroll(rememberScrollState())`).
  - When `filteredExercises.isEmpty()`:
    - Renders `"No matching movements found"` (`MaterialTheme.typography.bodySmall`, `onSurfaceVariant`).
  - When `filteredExercises.isNotEmpty()`:
    - Renders a `DropdownMenuItem` for each matching `Exercise`.
    - Visually highlights the currently selected movement (`FontWeight.Bold`, `MaterialTheme.colorScheme.primary`).
    - Tapping an item invokes `onSelect(option)`, resets `state` via `state.onExerciseSelected()` (`expanded = false`, `searchQuery = ""`), and hides the soft keyboard (`keyboardController?.hide()`).
