# Phase 1 Data Model: Cycle Movement Goal Search Keyboard & Filtering

## 1. UI State Model: `MovementSelectorUiState` (`CyclesScreen.kt`)

Pure, immutable UI state representing the expandable movement search selector inside a Cycle Goal card:

| Field | Type | Default | Description |
|-------|------|---------|-------------|
| `expanded` | `Boolean` | `false` | Whether the inline movement search box and filtered movement list are currently open. |
| `searchQuery` | `String` | `""` | Current text entered by the user in `"Search movement…"`. |

### Computed Properties & Pure Transitions

- `hasSearchQuery: Boolean` (`searchQuery.isNotEmpty()`): Controls visibility of the trailing `"Clear search"` icon button (`Icons.Filled.Close`).
- `toggleExpanded(): MovementSelectorUiState`:
  - When `expanded == false` → `MovementSelectorUiState(expanded = true, searchQuery = "")`
  - When `expanded == true` → `MovementSelectorUiState(expanded = false, searchQuery = "")`
- `open(): MovementSelectorUiState`: `copy(expanded = true)`
- `dismiss(): MovementSelectorUiState`: `copy(expanded = false, searchQuery = "")`
- `onSearchQueryChanged(newQuery: String): MovementSelectorUiState`: `copy(expanded = true, searchQuery = newQuery)`
- `clearSearchQuery(): MovementSelectorUiState`: `copy(searchQuery = "")`
- `onExerciseSelected(): MovementSelectorUiState`: `copy(expanded = false, searchQuery = "")`
- `filterExercises(exercises: List<Exercise>): List<Exercise>`: Delegates to `filterMovementExercises(exercises, searchQuery)`.

## 2. Pure Filtering Function: `filterMovementExercises`

```kotlin
internal fun filterMovementExercises(
    exercises: List<Exercise>,
    searchQuery: String
): List<Exercise>
```

### Validation & Filtering Rules

1. If `searchQuery.isBlank()`, returns `exercises` unchanged in their original order.
2. Otherwise, trims leading and trailing whitespace (`val q = searchQuery.trim()`) and returns all exercises where `exercise.name.contains(q, ignoreCase = true)` is `true`.
3. Does not mutate the input list or affect any other goal card's `GoalDraftState` or `MovementSelectorUiState`.
