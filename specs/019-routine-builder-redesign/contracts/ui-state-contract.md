# Contract: Daily Routine Builder UI & Session Loader

**Feature**: `019-routine-builder-redesign`  
**Screens**: `RoutineBuilderSheet.kt`, `LibraryScreen.kt`, `SessionEditor.kt`

## 1. 4-Tier Accordion Surface Contract

| Tier | Component | Expanded Presentation | Collapsed Presentation | Trigger Rules |
| :--- | :--- | :--- | :--- | :--- |
| **Tier 1** | `RoutineInfoAccordionCard` | `Routine / Workout Name` + `Description / Workout Goal` + `Done ▲` pill | Single-row (`minHeight = 56.dp`): `Routine Info: {name}` (or `"Routine Info: Tap to name workout"`) + `▾` | Expanded on `New Routine`; collapsed on `Edit Routine` when `name.isNotBlank()`; auto-collapses when `name.isNotBlank()` and user focuses/expands a block |
| **Tier 2** | `RoutineBlockAccordionCard` | Type icon + editable block name + move `↑`/`↓` + `Done ▲` + `🗑` (`48.dp`) + Tier 3A & Tier 3B | Single-row (`minHeight = 60.dp`): `{Icon} {BlockName}` + `[Format]` + `[{N} Set(s)]` + `[{M} Ex]` + `🗑` + `▾` | **Exclusive expansion**: expanding or adding Block `k` collapses any other expanded block; `Collapse All` collapses Tier 1 and all Tier 2 cards |
| **Tier 3A** | `BlockSetupSubAccordion` | Visual Block Type chips (`Weightlifting`, `WOD / Metcon`, `Gymnastics`, `Accessory`, `More ▾`), Contextual Execution Format chips + `More ▾`, Fixed `Sets` stepper (`-` / `{N} Set(s)` / `+` + pills `1, 3, 4, 5, 6, 8`), optional coaching notes toggle | Compact bar (`minHeight = 44.dp`): `Block Setup: {Type} • {Format} • {N} Set(s) ▾` | Toggled via header / `▴`, or auto-collapses when user adds/expands an exercise |
| **Tier 3B / 4** | `ExerciseAccordionCard` + Inline Search | Unified search & category filter chips + `+ Create & Add "{query}"` + Exercise cards with Row 1 (`Reps | Calories | Time | Meters`), Row 2 (`Fixed | Per-Set`), Row 3 (Fixed stepper+presets OR Per-Set `N`-card grid with `0 (Skip)` + pattern helpers) | Single-row (`minHeight = 52.dp`): `↑`/`↓` reorder + `{idx}. {Name}` + target summary badge (`8 reps / set` or `15 • 15 • 12 • 0 cal`) + `🗑` + `▾` | Tapping collapsed row expands that exercise within the block; tapping `Done ▲` collapses unless `isAllZeroInvalid` is true |

## 2. Responsive & Accessibility Contract

1. **Touch Targets**: All interactive steppers (`-`, `+`), `Done ▲` buttons, reorder actions, `0 (Skip)` actions, and `🗑` buttons enforce `48.dp` minimum touch target size.
2. **360dp & Font Scaling**:
   - Metric selector (`Reps | Calories | Time | Meters`) uses `maxLines = 1, softWrap = false`, switching to a horizontally scrollable `LazyRow` when `fontScale >= 1.4f`.
   - Per-Set grid columns: `2` when `fontScale >= 1.4f`, `6` when `widthDp >= 600`, `4` otherwise.
3. **TalkBack Semantics**:
   - Collapsed accordions merge descendants and expose state + action (`expand` / `collapse`).
   - Skipped set cards (`value == 0`) announce `"Set {k}, 0 {unit}, skipped in this set"` and render with non-color indicators (dashed border + `(Skip)` text).
4. **Undo Protection**:
   - Deleting an exercise or block displays a 4-second Snackbar `"{Name} removed"` with `[Undo]` restoring the item at its original index.
   - `Cancel` and system Back when `isDirty == true` show `"Discard unsaved routine?"` confirmation dialog.
