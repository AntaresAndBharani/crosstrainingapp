# Phase 0 Research: Exclusive Graphite, Titanium & Warm Bronze Theme

## Decision 1: Palette Selection & Dark-First Default
- **Decision**: Adopt the **Graphite, Titanium & Warm Bronze** color system across all four `AppThemeMode` palettes and make `AppThemeMode.DARK` (Dark Graphite) the default mode for fresh installations in `DataModeManager` and `CrossTrainingTheme`.
- **Rationale**: Warm obsidian/graphite neutrals (`#101012`, `#18181B`) paired with desaturated metallic accents (`#D49B6A` Warm Bronze, `#B8BCC2` Brushed Titanium) deliver a luxury precision-hardware feel without the mainstream sporty look of Volt Green `#00E676` and Electric Cyan `#38BDF8`. Keeping the existing `AppThemeMode` enum constant names (`DARK`, `LIGHT`, `DARK_HIGH_CONTRAST`, `LIGHT_HIGH_CONTRAST`) ensures 100% backward compatibility with persisted `SharedPreferences` (`KEY_THEME_MODE`).
- **Alternatives Considered**:
  - Obsidian & Champagne Gold (higher contrast gold, slightly warmer club look).
  - Heritage Racing Green & Gold (boutique athletic club aesthetic).
  - Adding separate exclusive enum modes alongside Volt Green (rejected to avoid dual-theme maintenance drift).

## Decision 2: Semantic Accents & WCAG Contrast Verification
- **Decision**: Use **Muted Sage / Alpine Emerald** (`#5DB894` dark / `#1E5C45` light) for `tertiary` (PRs, positive trends `▲`, `PREP` timer phase, 3rd chart series) and **Muted Crimson** (`#E57373` dark / `#A62C2C` light) for `error` (validation errors, negative trends `▼`, failed sets), plus centralized **Burnished Ochre** (`#E2B866` dark / `#8A5D0A` light) for warm-up sets, `TRANSITION` timer phase, and `WARNING_AMBER` momentum badges.
- **Rationale**: Automated relative luminance checks confirmed every standard `DARK` and `LIGHT` pair exceeds WCAG AA (`>= 4.5:1` for text/accents on surfaces and containers, `>= 3.0:1` for `outline` borders), and every `DARK_HIGH_CONTRAST` and `LIGHT_HIGH_CONTRAST` pair exceeds WCAG AAA (`>= 7.0:1`), including `outline` (`7.72:1` dark HC, `21.00:1` light HC).

## Decision 3: In-App Theme Detection (`isAppDarkTheme()`)
- **Decision**: Introduce `@Composable fun isAppDarkTheme(): Boolean = MaterialTheme.colorScheme.background.luminance() < 0.5f` in `Theme.kt` and replace all `isSystemInDarkTheme()` usages in `CompactComplexLogComponents.kt`, `LibraryScreen.kt`, `ProgressScreen.kt`, and `SimplifiedBlockLogComponents.kt`.
- **Rationale**: Previously, those screens checked the Android OS dark theme rather than the active `MaterialTheme.colorScheme`, causing dark badges or light surfaces to clash whenever a user picked an in-app `AppThemeMode` that differed from the OS setting. Checking `MaterialTheme.colorScheme.background.luminance()` is already used in `ContinuousTimerComponents.kt` and guarantees 100% alignment with all four theme modes.

## Decision 4: Test Contract Compatibility (`VoiceWorkoutIngestionSheetTest` & `WeightProgressionCardTest`)
- **Decision**:
  - Preserve `WaveformVisualizerHelper`'s public color properties (`GreenListening`, `GreenListeningLight`, `YellowProcessing`, `RedError`, `NeutralIdle`) and `resolveColor(status)` signature in `VoiceWorkoutIngestionSheet.kt`, pointing their values to the centralized tokens in `Color.kt`.
  - Update `WeightProgressionCardTest.kt` and `ThemeModeTest.kt` to assert the updated palette tokens and add automated WCAG AA/AAA contrast verification in `ThemeModeTest.kt`.
- **Rationale**: Preserves existing voice-visualizer unit test contracts while centralizing every hex literal in `Color.kt`.

## Decision 5: Cold-Launch Chrome & Launcher Icon
- **Decision**: Update `colors.xml`, `themes.xml`, `ic_launcher_background.xml`, and `ic_launcher_foreground.xml` to Graphite `#101012`, Brushed Titanium `#E4E4E7`, and Warm Bronze `#D49B6A`; remove `@color/brand_primary` from `android:statusBarColor` in `themes.xml`; and update `MainActivity.kt` with `DisposableEffect(themeMode)` calling `enableEdgeToEdge` with `SystemBarStyle.dark` / `SystemBarStyle.light` matched to `themeMode.isDark`.
- **Rationale**: Eliminates the legacy `#00E676` launch flash and ensures status bar / navigation bar icons adapt instantaneously when users switch between Light and Dark theme modes.
