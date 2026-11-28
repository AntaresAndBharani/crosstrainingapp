# Page Override: Routine-Aware Workout Timer Queue (`routine-timer-queue`)

> **IMPORTANT:** Rules in this file **override** [`MASTER.md`](file:///c:/Users/rogal/workspaces/ws-gym/crosstrainingapp/design-system/crosstraining/MASTER.md) for CrossTraining's in-session workout timer queue surfaces.
> Only deviations and timer-queue-specific guidelines are documented here. For all global tokens, themes, and architecture standards, refer to `MASTER.md`.

---

## 1. Timer Queue Surface Inventory

Primary files covered by this override:
- [`SessionEditor.kt`](file:///c:/Users/rogal/workspaces/ws-gym/crosstrainingapp/app/src/main/java/com/fractanomics/crosstraining/ui/screens/SessionEditor.kt)
  - Top-bar Timer trigger, per-block/per-exercise/sub-block `onLaunchTimer` handlers, and active block card highlight wiring.
- [`SimplifiedBlockLogComponents.kt`](file:///c:/Users/rogal/workspaces/ws-gym/crosstrainingapp/app/src/main/java/com/fractanomics/crosstraining/ui/screens/SimplifiedBlockLogComponents.kt)
  - `SimplifiedBlockLogCard`, `SimplifiedExerciseLogCard`, and active `"Timer Active"` card indicator / border accent.
- [`InSessionTimerSheet.kt`](file:///c:/Users/rogal/workspaces/ws-gym/crosstrainingapp/app/src/main/java/com/fractanomics/crosstraining/ui/components/InSessionTimerSheet.kt)
  - Modal bottom sheet hosting the pre-start **Block Anchor Strip**, **Starting Block Configurator**, **Extrapolated Queue Timeline Preview**, and active multi-stage execution view.
- [`InSessionTimerBar.kt`](file:///c:/Users/rogal/workspaces/ws-gym/crosstrainingapp/app/src/main/java/com/fractanomics/crosstraining/ui/components/InSessionTimerBar.kt)
  - Compact non-focus-stealing docked bar above the bottom navigation / IME showing active block progress and transition preview.
- [`ContinuousTimerComponents.kt`](file:///c:/Users/rogal/workspaces/ws-gym/crosstrainingapp/app/src/main/java/com/fractanomics/crosstraining/ui/components/ContinuousTimerComponents.kt)
  - `TimerModeSetupForm`, `ActiveContinuousTimerCard`, `NextModeConfigSheet`, `UpNextQueueCard` / expandable queue drawer, `LiveStepperRow`, and transport pills.
- [`WorkoutTimer.kt`](file:///c:/Users/rogal/workspaces/ws-gym/crosstrainingapp/app/src/main/java/com/fractanomics/crosstraining/ui/timer/WorkoutTimer.kt)
  - `WorkoutTimerConfig`, `QueuedTimerStage`, and `TimerSnapshot` immutable multi-stage state models.
- [`TimerEngine.kt`](file:///c:/Users/rogal/workspaces/ws-gym/crosstrainingapp/app/src/main/java/com/fractanomics/crosstraining/ui/timer/TimerEngine.kt)
  - Multi-stage queue orchestration, transitions, live overrides, and remaining-time calculations.
- [`ContinuousTimerUiResolver.kt`](file:///c:/Users/rogal/workspaces/ws-gym/crosstrainingapp/app/src/main/java/com/fractanomics/crosstraining/ui/timer/ContinuousTimerUiResolver.kt)
  - Pure presentation formatting helpers, action labels, and boundary-only TalkBack announcements.
- [`TimerNotificationFormatter.kt`](file:///c:/Users/rogal/workspaces/ws-gym/crosstrainingapp/app/src/main/java/com/fractanomics/crosstraining/ui/timer/TimerNotificationFormatter.kt)
  - Foreground `TimerService` notification titles and body text including block context and transition previews.

---

## 2. Gym Ergonomics & Density Overrides (`Density: 6/10` — High-Legibility In-Gym Mode)

Because athletes interact with the timer mid-workout (often fatigued, with chalk or sweaty hands, glancing from a bench or rig), timer queue controls prioritize generous hit targets and crisp numeric legibility over dense analytics packing:

- **Minimum Touch Targets (`>= 48.dp x 48.dp`):** Every anchor pill (`FilterChip` / pill), queued-stage Edit (`Icons.Filled.Edit`) and Remove (`Icons.Filled.Close`) icon button, `+ Add Stage` button, live stepper button (`-15s` / `+15s` / `-1` / `+1`), and transport pill MUST provide at least `48.dp x 48.dp` clickable area.
- **Adjacent Target Spacing (`>= 8.dp`):** Maintain at least `8.dp` horizontal and vertical spacing between adjacent tappable chips, stepper buttons, and queue row icon actions to prevent mis-taps.
- **Sticky Primary CTA (`52.dp` Pill):** Primary actions (`START QUEUE (N Blocks • MM:SS)`, `Pause` / `Resume`, `Start Now`) use a full-width or weighted `52.dp`-tall `RoundedCornerShape(999.dp)` pill button.
- **4/8dp Spacing Rhythm:** Use `4.dp` / `8.dp` / `12.dp` / `14.dp` / `16.dp` spacing increments consistently across the sheet (`20.dp` horizontal sheet padding, `12.dp`–`14.dp` section rhythm, `8.dp` connector spacing).
- **Tabular Numerals (`tnum`):** Apply `fontFeatureSettings = "tnum"` on every hero clock, secondary time hint, round counter (`Round 2 of 4`), transition duration (`1:00`), queued stage duration (`12:00`), and total estimated queue duration so digits remain rock-steady during 1Hz ticks.
- **Vector Icons Only (Zero Structural Emojis):** Use Material vector icons exclusively (`Icons.Filled.Timer`, `Icons.Filled.Edit`, `Icons.Filled.Close`, `Icons.Filled.PlayArrow`, `Icons.Filled.Pause`, `Icons.Filled.SkipNext`, `Icons.Filled.ExpandMore`, `Icons.Filled.ExpandLess`, `Icons.Filled.Add`) with `>= 3:1` contrast. Never use emojis as structural icons or status markers.

---

## 3. Phase Color Tokens & Non-Color Redundancy

All timer phases MUST reuse CrossTraining's Material 3 and [`Color.kt`](file:///c:/Users/rogal/workspaces/ws-gym/crosstrainingapp/app/src/main/java/com/fractanomics/crosstraining/ui/theme/Color.kt) semantic tokens across all 4 `AppThemeMode`s (`DARK`, `LIGHT`, `DARK_HIGH_CONTRAST`, `LIGHT_HIGH_CONTRAST`) via `isAppDarkTheme()`:

| Timer Phase / Element | Accent / Progress Token | Badge Container / Content Token | Required Non-Color Pairing |
|---|---|---|---|
| **Work (`WORK`)** | `MaterialTheme.colorScheme.primary` | `primaryContainer` / `onPrimaryContainer` | Uppercase `"WORK"` badge + active block title + round counter |
| **Prep (`PREP`)** | `MaterialTheme.colorScheme.tertiary` | `tertiaryContainer` / `onTertiaryContainer` | Uppercase `"GET READY!"` badge + countdown |
| **Rest (`REST`)** | `MaterialTheme.colorScheme.secondary` | `secondaryContainer` / `onSecondaryContainer` | Uppercase `"REST"` badge + round counter |
| **Transition & Connectors (`TRANSITION`)** | `resolveTransitionAccentColor()` (Burnished Ochre) | `resolveTransitionContainerColor()` / `OchreOnContainer*` | Uppercase `"TRANSITION"` badge + `"Up Next: <Block>"` or `"1:00 Transition"` text |
| **Finished (`FINISHED`)** | `MaterialTheme.colorScheme.outline` | `surfaceVariant` / `onSurfaceVariant` | Uppercase `"DONE!"` badge + `"Stage(s) completed"` summary |

- **Strict Rule:** Never rely on color alone to communicate phase, active block status, or transition connectors. Always pair color with explicit badge labels and Material vector icons.
- **High-Contrast Parity:** Verify `>= 4.5:1` text contrast and `>= 3:1` border/icon contrast independently in all 4 `AppThemeMode`s; never hardcode hex colors inside composables.

---

## 4. Progressive Disclosure & Sheet Layout

### 4.1 Pre-Start Setup View (`InSessionTimerSheet` when `TimerPhase.IDLE`)

1. **Horizontal Scrollable Block Anchor Strip:**
   - Displays numbered selectable pills (`LazyRow` with `8.dp` spacing, e.g., `1. Back Squat`, `2. Clean Complex`, `3. Metcon`) representing all ordered blocks/exercises in the current `Log Session` workout.
   - The currently anchored block `#k` is highlighted using `primaryContainer` / `onPrimaryContainer` (with `primary` border/selection state).
   - Tapping any pill re-anchors the starting block to `#m` and recomputes the auto-extrapolated queue (`#m` configurable + `#m+1 … #N` with `1:00` transitions).
2. **Starting Block Configurator:**
   - Reuses the shared `TimerModeSetupForm` supporting all 6 modes (`EMOM / ExMOM`, `Time Cap / For Time`, `AMRAP`, `Tabata / Intervals`, `Death By`, `Rest Timer`).
   - Pre-fills `E3MOM (180s) × {sets}` by default using the anchored block's resolved set count, plus `3-2-1 Prep Countdown` chips (`No prep`, `3s`, `5s`, `10s`) and `Audio Beeps` / `Vibration` switches.
3. **Extrapolated Queue Timeline Preview:**
   - Rendered below a `HorizontalDivider()` as a compact vertical timeline:
     - Preceding each queued block `#j` (`j > anchor`), render an inline **Burnished Ochre `1:00 Transition`** connector chip/row (`resolveTransitionAccentColor()` / `resolveTransitionContainerColor()`).
     - Each queued block row (`OutlinedCard` or tonal container) shows:
       - Block index & title (e.g., `2. Bench Press`)
       - Mode summary (`E3MOM 03:00 × 4`) in `tnum`
       - `48.dp` **Edit** (`Icons.Filled.Edit`, `"Edit stage <title>"`) button opening `NextModeConfigSheet` (to change mode, interval/cap, rounds, or preceding transition seconds)
       - `48.dp` **Remove** (`Icons.Filled.Close`, `"Remove stage <title> from queue"`) button
     - Footer row inside the timeline section provides **`+ Add Stage`** (`Icons.Filled.Add`) and **Total Estimated Queue Time** (`tnum`, e.g., `Total Est. 34:00`).
4. **Sticky Primary CTA:**
   - Full-width `52.dp` pill button: **`START QUEUE (N Blocks • MM:SS)`** (or `START TIMER` when only 1 stage is in the queue).

### 4.2 Active Execution View (`InSessionTimerSheet` when `WORK` / `REST` / `PREP` / `TRANSITION`)

1. **Block-Aware Stage Header:**
   - Top row displays the phase badge (`WORK`, `TRANSITION`, etc.) on the left and **`Block X of Y • <BlockTitle>`** on the right (`maxLines = 1, overflow = TextOverflow.Ellipsis`), refactoring cleanly onto two lines if needed on `360dp` phones at `fontScale >= 1.3f`.
2. **Hero Circular Gauge & Live Adjustment Controls:**
   - Preserves the `216.dp` circular progress gauge and `tnum` primary clock (`48.sp` style token), round indicator (`Round 2 of 4`), and live round / next-round duration steppers (`LiveStepperRow`), or **Transition Clock Steppers** (`-15s` / `+15s` / tap-to-type) during `TimerPhase.TRANSITION`.
3. **Upgraded Up Next & Expandable Remaining Queue Section:**
   - Always shows the **Immediate Up Next** card (transition wait + next block title + mode summary + Edit/Remove `48.dp` controls).
   - When `queuedStages.size > 1`, displays an expandable **`Remaining Queue (+K more)`** toggle header (`Icons.Filled.ExpandMore` / `Icons.Filled.ExpandLess`, `>= 48.dp` hit target, `rememberSaveable` expansion state).
   - Expanded rows use stable keys (`key = stage.id`) and show each remaining stage's transition connector, block title, mode summary, Edit button (`48.dp`), and Remove button (`48.dp`), plus `+ Add Stage` at the bottom.
4. **Anti-Wrap 3-Pill Transport Row:**
   - Retains the `360dp` + `fontScale = 1.3f` safe 3-pill horizontal transport row (`Reset` | `Pause / Resume` or `Start Now` | `Skip / Next Stage`).

---

## 5. In-Session Context Sync & Non-Focus-Stealing Rules

- **Docked `InSessionTimerBar`:**
  - All interactive controls (`Play/Pause`, `Skip/Next Stage`, `Expand`, `Dismiss`) MUST keep `Modifier.focusProperties { canFocus = false }` and a shared `MutableInteractionSource` so tapping timer controls never dismisses the soft keyboard or steals cursor focus from weight/rep fields.
  - Displays block context concisely without truncation glitches:
    - During `WORK` / `PREP` / `REST`: `Work • 02:14 • Back Squat (R2/4) • 1/4`
    - During `TRANSITION`: `Transition • 00:42 • Next: Bench Press`
- **Active Block Card Highlight in `LogSessionScreen`:**
  - When `snapshot.phase != TimerPhase.IDLE` and `snapshot.activeConfig.sourceBlockKey` matches a card (`availState.uiKey` or legacy card key):
    - Highlight the card border subtly (`1.5.dp` `MaterialTheme.colorScheme.primary` during Work/Prep/Rest; `resolveTransitionAccentColor()` when transitioning away or into the next block).
    - Render a compact `"Timer Active"` status pill (`primaryContainer` / `onPrimaryContainer`) with `Icons.Filled.Timer` inside the card header so scrolling the session immediately identifies the active block.
- **Foreground Notification (`TimerNotificationFormatter`):**
  - Displays block context in the notification title/subtext (`Back Squat — Round 2/4` during work, `Transition — Up Next: Bench Press` during transition) while preserving `onlyAlertOnce = true`.

---

## 6. Jetpack Compose State, Performance & Accessibility Guardrails (`--stack jetpack-compose`)

1. **Immutable State Models:**
   - Store queued stages in `TimerSnapshot` as an immutable `List<QueuedTimerStage>` (never `MutableList`).
   - Annotate UI projection classes (`TimerQueuePreviewUiState`, `SessionTimerBlockEntry`, `LiveAdjustmentUiState`) with `@Immutable`.
2. **List Keys & Configuration Retention:**
   - Use `LazyColumn` / keyed loops with stable `key = stage.id` for queue rows so editing or removing a stage never causes item state jumps.
   - Persist UI-only expansion toggles (e.g., `isRemainingQueueExpanded`) with `rememberSaveable`.
   - Keep `TimerEngine` state in the singleton `TimerEngineProvider` so active queues survive rotation, navigation, and backgrounding.
3. **TalkBack Semantics & Boundary Announcements:**
   - Apply `Modifier.semantics(mergeDescendants = true)` on queued stage timeline rows so TalkBack reads full stage context in one focus stop (e.g., `"Block 2, Bench Press, after 60 seconds transition, EMOM 3 minutes times 4 rounds"`).
   - Emit `LiveRegionMode.Polite` announcements **strictly on phase/round/stage boundaries** via `ContinuousTimerUiResolver.resolvePoliteBoundaryAnnouncement`, incorporating block title and stage position (never on 1Hz countdown ticks).
4. **Responsive & Dynamic Type Verification:**
   - Verify zero horizontal clipping, overlapping controls, or wrapped button labels on `360dp`-wide viewports up to `fontScale = 1.3f`, with clean multi-line reflow up to `2.0f`, across all 4 `AppThemeMode`s.
