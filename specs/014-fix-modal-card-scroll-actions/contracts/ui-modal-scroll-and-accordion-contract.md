# UI Contract: Modal Card Auto-Scroll & Non-Blocking Accordion Actions

## 1. `CycleEditorDialog` Accordion & Auto-Scroll Contract

1. **Adding a Movement Goal (`+ Add Movement`)**:
   - **When all open cards have a valid target (`> 0`)**:
     - Collapses any open card.
     - Appends the new `GoalDraftState` in `Expanded Edit State` (`isCollapsed = false`).
     - Triggers an automatic scroll request targeting `goalDrafts.lastIndex` after the next layout frame so the newly appended card is visible inside the `AlertDialog` scroll viewport.
   - **When an open card has an invalid/empty target (`<= 0`)**:
     - Sets `showTargetError = true` on the open card (`"Please enter a target value greater than 0"`).
     - Triggers an automatic scroll request targeting that open card's index so the user is scrolled directly to the card requiring attention.

2. **Expanding a Collapsed Goal Card (`CollapsedGoalSummaryCard` / `Expand goal`)**:
   - **Non-Blocking Guarantee**: Tapping a collapsed card or its `▾` button MUST ALWAYS expand that card.
   - **Handling Other Open Cards**:
     - Any other open card with `hasValidTarget == true` auto-collapses.
     - Any other open card with `hasValidTarget == false` that has no prior valid target (`id == 0L && !canRestoreValidTarget`) is automatically discarded (removed from `goalDrafts`).
     - Any other open card with `hasValidTarget == false` that previously had a valid target (`canRestoreValidTarget == true`) restores `targetWeight = lastValidTargetWeight` and auto-collapses.
     - Any other open persisted card (`id != 0L`) with `hasValidTarget == false` and no restorable target is never removed; it auto-collapses so the user can edit or delete it explicitly.
   - **Viewport Auto-Scroll**:
     - Triggers an automatic scroll request targeting the newly expanded card's index after the next layout frame so its form controls are visible inside the `AlertDialog` scroll viewport.

3. **Collapsing a Goal Card (`Done ▲` or IME `Done`)**:
   - If `hasValidTarget == true`, collapses the card and hides the software keyboard.
   - If `hasValidTarget == false`, sets `showTargetError = true` and scrolls the card into view.

4. **`CollapsedGoalSummaryCard` Action Separation**:
   - Trailing action row uses `Arrangement.spacedBy(4.dp)` between `Remove goal` (`🗑`) and `Expand goal` (`▾`).
   - Both `Remove goal` and `Expand goal` remain independently clickable in touch and accessibility semantics.

## 2. `RoutineEditorDialog` Auto-Scroll Contract (`LibraryScreen.kt`)

1. **Adding a Routine Block (`Add Block to Routine`)**:
   - Appends `RoutineBlockFormState` to `blockStates`.
   - Automatically scrolls the dialog's `ScrollState` after the next layout frame so the newly added `Block N` card is brought into view.
