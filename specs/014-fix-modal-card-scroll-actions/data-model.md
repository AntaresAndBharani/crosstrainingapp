# Phase 1 Data Model: Modal Card Auto-Scroll & Non-Blocking Accordion Actions

## 1. `GoalDraftState` Enhancements (`CyclesScreen.kt`)

| Field / Property | Type | Default | Description |
|------------------|------|---------|-------------|
| `id` | `Long` | `0L` | Database primary key (`0L` for newly added unsaved drafts). |
| `exercise` | `Exercise?` | `null` | Selected movement from the exercise library. |
| `reps` | `String` | `"1"` | Target repetition max string for `MetricType.WEIGHT`. |
| `startWeight` | `String` | `""` | Legacy start weight string (`""` for target-only goals). |
| `targetWeight` | `String` | `""` | Current target input string (`> 0` required to persist/collapse). |
| `lastValidTargetWeight` | `String` | `""` | Most recent valid positive target string (`> 0`), used to safely restore a previously valid goal if its target was cleared and the user switches to expand another goal. |
| `selectedModifier` | `Double?` | `null` | Last tapped quick-stepper delta (`-2.5`, `+2.5`, `+5.0`, etc.). |
| `isManuallyEdited` | `Boolean` | `false` | Whether the user has manually edited the card's target/fields. |
| `goalMetric` | `MetricType` | `CycleGoal.defaultGoalMetricFor(exercise)` | Active goal metric (`WEIGHT`, `CALORIES`, `REPS`). |
| `timeWindowMinutes` | `Int?` | `null` | Selected time window in minutes (`1`, `3`, `5`, or `null` for `Open`). |
| `notes` | `String` | `""` | User notes without encoded metric metadata. |
| `isCollapsed` | `Boolean` | `false` | `true` for Collapsed Summary State; `false` for Expanded Edit State. |
| `showTargetError` | `Boolean` | `false` | `true` when collapse or add was attempted with `targetWeight <= 0`. |

### Derived Properties & State Transitions

- **`hasValidTarget: Boolean`**: `targetWeight.replace(',', '.').toDoubleOrNull()?.let { it > 0.0 } == true`
- **`canRestoreValidTarget: Boolean`**: `lastValidTargetWeight.replace(',', '.').toDoubleOrNull()?.let { it > 0.0 } == true`
- **`isDiscardableEmptyDraft: Boolean`**: `!hasValidTarget && !canRestoreValidTarget && id == 0L`
- **`expandGoalDraftAt(drafts: List<GoalDraftState>, targetIndex: Int): List<GoalDraftState>`**:
  - Never blocks expanding `targetIndex`.
  - When expanding `targetIndex`, any other currently open card (`idx != targetIndex && !draft.isCollapsed`):
    1. If `draft.hasValidTarget`: collapses cleanly (`isCollapsed = true, showTargetError = false`).
    2. If `!draft.hasValidTarget && draft.canRestoreValidTarget`: restores `targetWeight = draft.lastValidTargetWeight` and collapses (`isCollapsed = true, showTargetError = false`).
    3. If `draft.isDiscardableEmptyDraft` (an unsaved `id == 0L` draft that has never had a valid positive target): is **removed (auto-discarded)** from the list, while the requested card (`targetIndex`) expands (`isCollapsed = false, showTargetError = false`).
    4. If `!draft.hasValidTarget && !draft.canRestoreValidTarget && draft.id != 0L` (a persisted goal, e.g. a legacy goal loaded with `targetWeight = 0.0`): is **never removed**; it collapses (`isCollapsed = true, showTargetError = false`) so the user can still edit or delete it explicitly.
  - `lastValidTargetWeight` is reset to `""` when `onExerciseChanged` / `onGoalMetricChanged` find no historical best, so a previous movement's or metric's target (possibly in a different unit) is never restored onto the new movement/metric. `onRepsChanged` keeps it (same movement and unit).
- **`appendMovementGoalDraft(drafts: List<GoalDraftState>, newDraft: GoalDraftState): Pair<List<GoalDraftState>, Boolean>`**:
  - If an open card has `!hasValidTarget`, flags `showTargetError = true` on that open card and returns `flagged to false` (and the caller scrolls directly to `invalidOpenIndex` so the error is visible).
  - Otherwise collapses all open cards, appends `newDraft.copy(isCollapsed = false, showTargetError = false)`, and returns `appended to true` (and the caller scrolls directly to `appended.lastIndex`).

## 2. Scroll Request & Viewport Offset Model

| Helper / State | Signature / Type | Description |
|----------------|------------------|-------------|
| `CardLayoutBounds` | `data class CardLayoutBounds(val topPx: Int, val heightPx: Int)` | Captures a card's vertical offset (`positionInParent().y`) and height (`size.height`) inside the scrollable `Column`. |
| `resolveGoalCardScrollOffset` | `(cardTopPx: Int, cardHeightPx: Int, viewportHeightPx: Int, currentScrollPx: Int, maxScrollPx: Int, topPaddingPx: Int = 24) -> Int` | Pure deterministic function calculating the exact `ScrollState` target offset to bring a newly added, expanded, or error-flagged card into full view. |
