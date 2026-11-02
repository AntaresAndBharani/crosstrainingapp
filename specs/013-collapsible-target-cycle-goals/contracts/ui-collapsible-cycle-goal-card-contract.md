# UI Contract: Collapsible & Target-Only Movement Goal Card (`SPEC-CYCLE-GOAL-04`)

## 1. Section Header (`Basic Movement Goals`)
- Left title: `"Basic Movement Goals"` (`MaterialTheme.typography.titleSmall`, `FontWeight.Bold`).
- Right CTA button: `TextButton` with `Icons.Filled.Add` and label `"+ Movement"` (or `"Add Movement"` semantics).
- Empty state (when `goalDrafts.isEmpty()`): Outlined/surface placeholder displaying `"No movement goals in this cycle yet. Tap + Movement to add a target."`

## 2. Variant A: `CollapsedSummaryCard` (`draft.isCollapsed == true`)
- **Trigger Conditions**:
  - Default initial state for all existing valid saved goals (`targetWeight > 0`) when opening `Edit cycle`.
  - Entered when user taps `Done ▲` on a valid expanded card, presses IME `Done` on the `Target` input, taps `+ Movement` while the open card is valid, or expands another card.
- **Visual Layout**:
  - Compact rounded card (`RoundedCornerShape(14.dp)`), clickable across the entire card surface to expand (`onClick = { expandGoalDraftAt(index) }`).
  - Merged accessibility semantics (`Modifier.semantics(mergeDescendants = true)`) with `contentDescription = draft.collapsedAccessibilityDescription(index)`.
  - **Left/Center Summary Cluster** (`Modifier.weight(1f)`):
    - **Title**: `"Goal ${index + 1}: ${draft.exercise?.name ?: "Select movement…"}"` (`maxLines = 1`, `TextOverflow.Ellipsis`, `FontWeight.SemiBold`).
    - **Badges Row** (`FlowRow` or `Row` with `spacedBy(8.dp)`):
      - **Context Badge**: Soft lavender pill (`Color(0xFFEAE6F0)` in light mode / `MaterialTheme.colorScheme.secondaryContainer` adaptive, `RoundedCornerShape(6.dp)`) displaying `draft.contextBadgeText` (e.g., `"3RM"`, `"5 min"`, `"Open"`).
      - **Target Badge**: Soft brand-green pill (`Color(0xFFE3F5EC)` fill, `Color(0xFF0B6E3F)` text in light mode / `MaterialTheme.colorScheme.primaryContainer` adaptive, `RoundedCornerShape(6.dp)`) displaying `draft.targetBadgeText` (e.g., `"Target: 65 kg"`, `"Target: 45 cal"`, `"Target: 15 reps"`).
  - **Right Action Cluster**:
    - **Delete Button (`🗑`)**: `IconButton` (`Icons.Filled.Delete`, `contentDescription = "Remove goal"`), removes goal at `index` and re-indexes remaining goals.
    - **Expand Chevron (`▾`)**: `IconButton` or `Icon` (`Icons.Filled.KeyboardArrowDown` / `"▾"`), expands the card.

## 3. Variant B: `ExpandedEditCard` (`draft.isCollapsed == false`)
- **Card Header**:
  - Left: `"Goal ${index + 1}"` (`MaterialTheme.typography.titleSmall`, `FontWeight.SemiBold`).
  - Right:
    - **`Done ▲` Collapse Button**: Filled/tonal pill button (`"Done ▲"`, `contentDescription = "Collapse goal"`) that invokes `collapseGoalDraftAt(index)`.
    - **Delete Button (`🗑`)**: `IconButton` (`Icons.Filled.Delete`, `contentDescription = "Remove goal"`).
- **Movement Selector (`Movement (Basic)`)**:
  - Searchable inline exercise selector (`MovementGoalSelector`) preserving IME focus and filtering behavior from `012-fix-movement-search-keyboard`.
- **Single-Row `Goal Metric:` Selector**:
  - Label `"Goal Metric:"` followed by a single non-wrapping `Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp))` containing 3 `FilterChip` items (`Modifier.weight(1f)`):
    - `"Weight ($weightUnit)"`
    - `"Calories (cal)"`
    - `"Reps"`
- **Contextual Secondary Row**:
  - When `draft.goalMetric == MetricType.WEIGHT`: `Reps (RM)` stepper (`-`, `"<N> RM"`, `+`) + preset chips (`1`, `3`, `5`, `10`).
  - When `draft.goalMetric != MetricType.WEIGHT`: `Time Window` pills (`1 min`, `3 min`, `5 min`, `Open`).
- **Single Full-Width `Target (<unit>)` Input Field**:
  - Full-width `OutlinedTextField` (`Modifier.fillMaxWidth()`) labeled `"Target (${draft.unitLabel})"`.
  - No `Start (<unit>)` input field and no `"+ →"` text connector.
  - `isError = draft.showTargetError`.
  - `supportingText`: When `draft.showTargetError` is `true`, renders `"Please enter a target value greater than 0"`.
  - `KeyboardOptions(keyboardType = KeyboardType.Decimal, imeAction = ImeAction.Done)` and `KeyboardActions(onDone = { collapseGoalDraftAt(index) })`.
- **Quick Target Stepper Pills Row (`1A`)**:
  - Single horizontal `Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp))` directly below the `Target` input containing 3 equal-width (`Modifier.weight(1f)`) stepper chips from `draft.targetStepperOptions`:
    - `Weight`: `-2.5 kg` | `+2.5 kg` | `+5 kg`
    - `Calories`: `-5 cal` | `+5 cal` | `+10 cal`
    - `Reps`: `-1 rep` | `+1 rep` | `+5 reps`
  - Tapping any pill invokes `draft.stepTarget(delta)` and triggers light haptic feedback (`LocalHapticFeedback.current.performHapticFeedback(HapticFeedbackType.TextHandleMove)`).
