# Design System Master: CrossTraining

> **Hierarchical Retrieval Rule (`ui-ux-pro-max`):**
> This `MASTER.md` file is the global UI/UX source of truth for CrossTraining.
> Before designing or implementing any screen, read this file **and** check `design-system/crosstraining/pages/<page>.md`.
> If a page override file exists, its rules **override** `MASTER.md` for that surface; otherwise follow `MASTER.md` exclusively.

---

## 1. Project Profile & Design Dials

- **Product:** CrossTraining Mobile App (`com.fractanomics.crosstraining`)
- **Stack (`ui-ux-pro-max`):** `--stack jetpack-compose`
  - Android (`minSdk = 26`, `compileSdk = 35`, `targetSdk = 35`), Kotlin (JVM 17)
  - Jetpack Compose + Material 3 (`androidx.material3`), Navigation Compose, Material Icons Extended
- **Design Dials:**
  - **Variance (`5/10`):** Balanced / Modern — structured Material 3 cards, consistent grids, clear hierarchy
  - **Motion (`3/10`):** Subtle — purposeful state transitions (`AnimatedVisibility`, press feedback) without distracting flourishes
  - **Density (`7/10`):** Compact Analytics Standard — high information clarity per viewport without cramped touch targets

### Stack Reconciliation Note (`ui-ux-pro-max`)
Per the `ui-ux-pro-max` Query Contract, generic web `--design-system` suggestions (CSS variables, `#F97316` energy-orange palette, GSAP web `ScrollTrigger` snippets, `cursor-pointer` / hover-only patterns, and web font CSS imports) were **intentionally excluded**. All visual rules below reconcile verified `ui-ux-pro-max` outputs (`--stack jetpack-compose`, `--domain web` app-interface guidelines, `--domain chart`, `--domain ux`) with CrossTraining's existing native Material 3 tokens (`Color.kt`, `Theme.kt`, `Type.kt`), `.agents/rules/03_compose_ui_standards.md`, and `.specify/memory/constitution.md`.

---

## 2. Color System (Material 3 Semantic Tokens)

Source of truth: [`Color.kt`](file:///c:/Users/rogal/workspaces/ws-gym/crosstrainingapp/app/src/main/java/com/fractanomics/crosstraining/ui/theme/Color.kt) and [`Theme.kt`](file:///c:/Users/rogal/workspaces/ws-gym/crosstrainingapp/app/src/main/java/com/fractanomics/crosstraining/ui/theme/Theme.kt).

### Supported Theme Modes (`AppThemeMode`)
Every UI surface MUST work cleanly across all 4 built-in theme modes (Graphite, Titanium & Warm Bronze design system):
1. `DARK` — Graphite & Warm Bronze precision mode (**Default**)
2. `LIGHT` — Porcelain Ivory & Deep Bronze daylight mode
3. `DARK_HIGH_CONTRAST` — OLED pitch black with high-contrast bronze & sage (WCAG AAA)
4. `LIGHT_HIGH_CONTRAST` — Pure white daylight high contrast (WCAG AAA)

### Semantic Role Mapping
| Material 3 Token (`MaterialTheme.colorScheme.*`) | Brand Role | Dark (Default) | Light | Dark HC (AAA) | Light HC (AAA) |
|---|---|---|---|---|---|
| `primary` / `onPrimary` | Warm Bronze (Primary CTA, active selection, focus accent) | `#D49B6A` / `#120C06` | `#855228` / `#FFFFFF` | `#F0BA88` / `#000000` | `#663B16` / `#FFFFFF` |
| `primaryContainer` / `onPrimaryContainer` | Tinted bronze badges, active pills, date boxes | `#332417` / `#F5D9BC` | `#F3E5D7` / `#331D0B` | `#422810` / `#FFE2C7` | `#F5DFC9` / `#291505` |
| `secondary` / `onSecondary` | Brushed Titanium (Secondary metrics, rest timer, comparison series) | `#B8BCC2` / `#111214` | `#3F434A` / `#FFFFFF` | `#E4E6EA` / `#000000` | `#1F2328` / `#FFFFFF` |
| `secondaryContainer` / `onSecondaryContainer` | Tinted titanium chips & secondary exercise badges | `#26282C` / `#E4E6EA` | `#E5E7EB` / `#181A1F` | `#2C2E33` / `#FFFFFF` | `#DCE0E5` / `#000000` |
| `tertiary` / `onTertiary` | Muted Sage (PR highlights, positive trend deltas, prep timer) | `#5DB894` / `#06140E` | `#1E5C45` / `#FFFFFF` | `#7EE2B8` / `#000000` | `#0F4531` / `#FFFFFF` |
| `tertiaryContainer` / `onTertiaryContainer` | PR chips, weight-linked states, positive summary pills | `#132E24` / `#B7E8D4` | `#D9EDE5` / `#0B2B1F` | `#093B28` / `#C8F7E3` | `#C8E8DB` / `#042116` |
| `error` / `onError` / `errorContainer` | Muted Crimson (Negative trend, validation error, destructive action) | `#E57373` / `#1A0707` / `#3B1517` | `#A62C2C` / `#FFFFFF` / `#F9DEDE` | `#FF8080` / `#000000` / `#520E0E` | `#821414` / `#FFFFFF` / `#FACACA` |
| `background` / `surface` / `surfaceVariant` | Graphite / Porcelain surface hierarchy | `#101012` / `#18181B` / `#222226` | `#F8F7F5` / `#FFFFFF` / `#EFECE7` | `#000000` / `#0A0A0B` / `#151518` | `#FFFFFF` / `#FFFFFF` / `#E8E6E1` |
| `surfaceContainer` / `surfaceContainerHigh` | Grouped containers & elevated drawers | `#1C1C20` / `#27272C` | `#F4F2EE` / `#E6E2DC` | `#0F0F12` / `#1B1B20` | `#F4F2EE` / `#D6D3CD` |
| `onSurface` / `onSurfaceVariant` | Primary & secondary readable text | `#F4F4F5` / `#A1A1AA` | `#151517` / `#52525A` | `#FFFFFF` / `#E4E4E7` | `#000000` / `#18181B` |
| `outline` / `outlineVariant` | Borders, card strokes, and dividers | `#76767E` / `#333339` | `#71717A` / `#DCDAD5` | `#A1A1AA` / `#71717A` | `#000000` / `#52525B` |

### Auxiliary, Chart & Timer Semantic Tokens
Available in `Color.kt`:
- **Warm-up / Transition / Warning (Burnished Ochre):** `OchreWarningDark` (`#E2B866`) / `OchreContainerDark` (`#332712`) / `OchreOnContainerDark` (`#F6DFB0`), and `OchreWarningLight` (`#8A5D0A`) / `OchreContainerLight` (`#F7ECD6`) / `OchreOnContainerLight` (`#422B04`).
- **Weight Subcycles:** `SubcycleFatLossContainer*` / `SubcycleFatLossOn*` (Muted Slate Steel) and `SubcycleGainContainer*` / `SubcycleGainOn*` (Muted Plum), with `RECOMPOSITION` mapped to Muted Sage (`SageContainer*` / `SageTertiary*`).
- **Charts & Timers:** `ChartBronzePrimary` / `TimerWorkColor`, `ChartTitaniumSecondary` / `TimerRestColor`, `ChartSageTertiary` / `TimerPrepColor`.

### Strict Color Rules
- **DO:** Always use `MaterialTheme.colorScheme.*` tokens or centralized `Color.kt` semantic tokens paired with `isAppDarkTheme()` in Composables.
- **DON'T:** Hardcode per-screen hex colors (`Color(0xFF...)`), call `isSystemInDarkTheme()` directly inside screens (which bypasses the user's in-app `AppThemeMode`), or infer dark-mode / high-contrast legibility from light-mode values.
- **DON'T:** Rely on color alone to communicate state, series identity, or delta direction.

---

## 3. Typography & Numeric Formatting

Source of truth: [`Type.kt`](file:///c:/Users/rogal/workspaces/ws-gym/crosstrainingapp/app/src/main/java/com/fractanomics/crosstraining/ui/theme/Type.kt).

- **Type Scale:** Always use `MaterialTheme.typography` (`AppTypography`, Material 3 scale). Never hardcode fixed `sp` font sizes directly inside composables (`--stack jetpack-compose` High/Medium guideline).
- **Tabular Numerals (`tnum`):** Apply `fontFeatureSettings = "tnum"` to all weights, percentages, indices, ratios, deltas, timestamps, and aligned table columns so digits do not jitter across rows or updates.
- **Readability Floor & Dynamic Type:**
  - Maintain readable body sizes (`>= 14sp` for primary body content; never shrink critical labels below `12sp`).
  - Support Android system `fontScale` / Dynamic Type gracefully without text clipping or overlapping controls up to `fontScale = 1.3f` (and multi-line reflow up to `2.0f`) on `360dp`-wide viewports.

---

## 4. Layout, Spacing & Touch Targets

- **4/8dp Spacing Rhythm:** Use consistent `4.dp` / `8.dp` / `12.dp` / `14.dp` / `16.dp` increments for padding, gaps, and section rhythm (`16.dp` standard horizontal screen gutters; `12.dp`–`14.dp` vertical card rhythm).
- **Readable Measure on Large Devices:** Constrain wide analytics/form cards with `widthIn(max = 640.dp)` where appropriate so content does not stretch awkwardly edge-to-edge on foldables or tablets.
- **Touch Targets (`Android >= 48.dp`):**
  - Every interactive button, icon control, chip, or tappable row MUST provide at least `48.dp x 48.dp` touch area (expand hit bounds if the visual icon is smaller).
  - Maintain at least `8.dp` spacing between adjacent touchable targets to prevent accidental taps.
- **Safe-Area & Navigation Clearance:** Respect Scaffold `innerPadding` / `outerPadding` so scrollable lists and bottom actions are never obscured behind the bottom `NavigationBar`, system bars, or IME keyboard.
- **Flat Layout Trees:** Prefer flat `Row` / `Column` structures; avoid deep nested `Box > Column > Box > Row` hierarchies (`--stack jetpack-compose` layout guideline). Extract repeated card/row patterns into shared composables.

---

## 5. Icons, Motion & Interaction

- **Vector Icons Only for Structure:** Use Material vector icons (`Icons.Filled.*`, `Icons.AutoMirrored.*`) for navigation, controls, and status affordances. Do not use emojis as structural UI icons.
- **Icon Semantics & Sizing:**
  - Keep consistent token sizes (`24.dp` standard controls, `18.dp`–`20.dp` compact chip/badge icons) and `>= 3:1` contrast for meaningful icons.
  - Pass `contentDescription = null` for decorative icons paired with visible text; provide explicit `contentDescription` on standalone icon controls.
- **Subtle Motion (`3/10`):**
  - Keep Compose state transitions quick and purposeful (`150–300ms`, `AnimatedVisibility` for inline drawers/expansions).
  - Use Material ripple, color, or elevation feedback within `80–150ms` for taps—never scale/bounce animations that shift surrounding layout bounds.
  - Respect system reduced-motion settings where custom animations are added.
- **No Gesture Conflicts:** Avoid nested clickable/draggable containers that compete with scroll or system back-swipe gestures.

---

## 6. Architecture, State & Analytics Constraints

Aligned with [`.agents/rules/03_compose_ui_standards.md`](file:///c:/Users/rogal/workspaces/ws-gym/crosstrainingapp/.agents/rules/03_compose_ui_standards.md) and [`.specify/memory/constitution.md`](file:///c:/Users/rogal/workspaces/ws-gym/crosstrainingapp/.specify/memory/constitution.md):

1. **MVVM & Unidirectional Data Flow (UDF):** `Composable → ViewModel → Repository → Room/Firebase`. Keep screen composables stateless where practical (`uiState` in, `onAction` out), collecting `StateFlow` with `collectAsStateWithLifecycle()`.
2. **State Annotations & Memoization:** Mark UI state models `@Immutable` or `@Stable`. Use `remember` (keyed appropriately) for heavy derivations and `derivedStateOf` for rapidly changing states.
3. **Configuration Persistence:** Use `rememberSaveable` (or ViewModel `StateFlow`) for user-facing mode/filter toggles so UI selections survive rotation and configuration changes.
4. **Pure Deterministic Analytics & Rounding:** Implement domain calculations as pure, deterministic Kotlin functions with explicit date injection (`referenceDate` / `today`) and `BigDecimal` `HALF_UP` rounding in the active display unit (`kg` / `lbs`) so displayed numbers reconcile cleanly.
5. **Zero Unnecessary Schema Churn:** Derive new analytics and indices from existing Room entities (`SessionWithBlocks`, `BlockSet`, `WeightEntry`, `RepMax`) whenever possible instead of forcing database migrations.

---

## 7. Accessibility & Pre-Delivery Checklist

Verify every UI change against these checks across all 4 `AppThemeMode` palettes:

- [ ] **Color & Contrast:** Body/normal text `>= 4.5:1` and meaningful icons/borders `>= 3:1` in Light, Dark, Light HC, and Dark HC (verified independently per mode).
- [ ] **Non-Color Redundancy:** Status, deltas, and chart series pair color with symbols (`▲`/`▼`/`=`), labels, or distinct styles—never hue alone.
- [ ] **TalkBack Semantics:** Composite metric cards/rows use `Modifier.semantics(mergeDescendants = true)` with complete, clear phrasing (context + value + unit/status, no cryptic symbols).
- [ ] **Touch & Spacing:** All interactive controls `>= 48.dp` target size with `>= 8.dp` spacing; `4/8dp` spacing rhythm preserved.
- [ ] **Responsive & Dynamic Type:** Zero horizontal overflow, clipping, or overlapping text on `360dp` phones up to `fontScale = 1.3f` (and graceful reflow up to `2.0f`).
- [ ] **Safe Areas & Scroll:** Content clears top app bar, bottom `NavigationBar`, and IME keyboard without obstruction.
- [ ] **State & Performance:** Heavy computations live outside composable bodies or inside keyed `remember` / ViewModel flows; user toggles survive config changes (`rememberSaveable`).
