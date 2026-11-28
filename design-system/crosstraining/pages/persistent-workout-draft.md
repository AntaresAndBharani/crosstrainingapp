# Page Override: Persistent Workout Draft & Live Session Dock (`persistent-workout-draft`)

> **IMPORTANT:** Rules in this file **override** [`MASTER.md`](file:///c:/Users/rogal/workspaces/ws-gym/crosstrainingapp/design-system/crosstraining/MASTER.md) for CrossTraining's Live Session Dock, Log overflow Discard, and Stale Draft banner surfaces.
> For all global color palettes, typography, and architecture standards, refer to `MASTER.md`.

---

## 1. Surface Inventory

- [`LiveSessionDock.kt`](file:///c:/Users/rogal/workspaces/ws-gym/crosstrainingapp/app/src/main/java/com/fractanomics/crosstraining/ui/components/LiveSessionDock.kt): Single global docked status bar above `PrimaryNavigationBar` on non-`Log` routes.
- [`AppNavigation.kt`](file:///c:/Users/rogal/workspaces/ws-gym/crosstrainingapp/app/src/main/java/com/fractanomics/crosstraining/ui/navigation/AppNavigation.kt): Bottom-nav `Log` `BadgedBox` dot and Drawer `LOG` `"Active"` pill.
- [`SessionEditor.kt`](file:///c:/Users/rogal/workspaces/ws-gym/crosstrainingapp/app/src/main/java/com/fractanomics/crosstraining/ui/screens/SessionEditor.kt): Top-bar overflow `⋮ -> Discard workout` menu/dialog, `Routine` Replace/Append prompt, and prior-day Stale Draft banner.

---

## 2. Single-Dock Layout & Visual Rules

- **Surface & Border:** `MaterialTheme.colorScheme.surfaceContainerHigh` with `RoundedCornerShape(12.dp)` and a `1.dp` border (`MaterialTheme.colorScheme.primary.copy(alpha = 0.55f)` in standard themes; full `MaterialTheme.colorScheme.primary` in high contrast).
- **Vector Icons Only:** Use `Icons.Filled.FitnessCenter`, `Icons.AutoMirrored.Filled.KeyboardArrowRight`, `Icons.Filled.PlayArrow`, `Icons.Filled.Pause`, `Icons.Filled.Close`, `Icons.Filled.MoreVert`, `Icons.Filled.DeleteOutline`, `Icons.Filled.History`, and `Icons.AutoMirrored.Filled.ListAlt`. Zero structural emojis.
- **Touch Targets & Focus Safety:** All buttons (`Play/Pause`, `Dismiss`, `Overflow`, `Use Today`, `Discard`) enforce `>= 48.dp` minimum interactive bounds with `>= 8.dp` spacing. Docked timer buttons set `Modifier.focusProperties { canFocus = false }`.
- **Tabular Numerals & Overflow Safety:** Use `fontFeatureSettings = "tnum"` on countdown clocks and block/set counters. Titles use `maxLines = 1, overflow = TextOverflow.Ellipsis`; at `fontScale >= 1.3f` in `WORKOUT_AND_TIMER` mode, omit the secondary subtitle line so the timer chip and `48.dp` action button never wrap or clip on `360dp` screens.
- **Accessibility:** Apply `Modifier.semantics(mergeDescendants = true)` on the workout resume zone with an atomic description (*"Workout in progress: ..., Double tap to resume"*). Never attach `liveRegion` to 1Hz ticking text.
- **Stale Draft Banner:** Uses `resolveTransitionContainerColor()` (Burnished Ochre container) with `OchreOnContainer*` / `onSurface` text (`>= 4.5:1`), `Icons.Filled.History`, and clear `Use Today` + `Discard` actions.
