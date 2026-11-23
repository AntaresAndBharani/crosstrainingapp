# Data & Token Model: Exclusive Graphite, Titanium & Warm Bronze Theme

No Room SQLite schema, Firestore collection structure, or MCP server tool schema changes are introduced by this feature. All persistence remains scoped to the existing `SharedPreferences` `themeMode` key (`AppThemeMode.name`).

## 1. `AppThemeMode` Persistence Contract

| Enum Name | Default? | `isDark` | Title | Description |
|---|---|---|---|---|
| `DARK` | Yes (new fallback) | `true` | `"Dark"` | `"Graphite & warm bronze precision mode (Default)"` |
| `LIGHT` | No | `false` | `"Light"` | `"Porcelain ivory & deep bronze daylight mode"` |
| `DARK_HIGH_CONTRAST` | No | `true` | `"Dark High Contrast"` | `"OLED pitch black with high-contrast bronze & sage"` |
| `LIGHT_HIGH_CONTRAST` | No | `false` | `"Light High Contrast"` | `"Pure white WCAG AAA daylight contrast"` |

- **Upgrade Compatibility**: `DataModeManager` reads `KEY_THEME_MODE` (`"themeMode"`). Any previously persisted enum name (`"LIGHT"`, `"DARK"`, `"LIGHT_HIGH_CONTRAST"`, `"DARK_HIGH_CONTRAST"`) continues to parse cleanly via `AppThemeMode.valueOf(...)`; missing or invalid values fall back to `AppThemeMode.DARK`.

## 2. Core Material 3 Palette Matrix (`Color.kt` & `Theme.kt`)

| Role | `DARK` (Default) | `LIGHT` | `DARK_HIGH_CONTRAST` (AAA) | `LIGHT_HIGH_CONTRAST` (AAA) |
|---|---|---|---|---|
| `primary` / `onPrimary` | `#D49B6A` / `#120C06` | `#855228` / `#FFFFFF` | `#F0BA88` / `#000000` | `#663B16` / `#FFFFFF` |
| `primaryContainer` / `onPrimaryContainer` | `#332417` / `#F5D9BC` | `#F3E5D7` / `#331D0B` | `#422810` / `#FFE2C7` | `#F5DFC9` / `#291505` |
| `secondary` / `onSecondary` | `#B8BCC2` / `#111214` | `#3F434A` / `#FFFFFF` | `#E4E6EA` / `#000000` | `#1F2328` / `#FFFFFF` |
| `secondaryContainer` / `onSecondaryContainer` | `#26282C` / `#E4E6EA` | `#E5E7EB` / `#181A1F` | `#2C2E33` / `#FFFFFF` | `#DCE0E5` / `#000000` |
| `tertiary` / `onTertiary` | `#5DB894` / `#06140E` | `#1E5C45` / `#FFFFFF` | `#7EE2B8` / `#000000` | `#0F4531` / `#FFFFFF` |
| `tertiaryContainer` / `onTertiaryContainer` | `#132E24` / `#B7E8D4` | `#D9EDE5` / `#0B2B1F` | `#093B28` / `#C8F7E3` | `#C8E8DB` / `#042116` |
| `error` / `onError` | `#E57373` / `#1A0707` | `#A62C2C` / `#FFFFFF` | `#FF8080` / `#000000` | `#821414` / `#FFFFFF` |
| `errorContainer` / `onErrorContainer` | `#3B1517` / `#FAD1D1` | `#F9DEDE` / `#410D0D` | `#520E0E` / `#FFD6D6` | `#FACACA` / `#330404` |
| `background` / `surface` / `surfaceVariant` | `#101012` / `#18181B` / `#222226` | `#F8F7F5` / `#FFFFFF` / `#EFECE7` | `#000000` / `#0A0A0B` / `#151518` | `#FFFFFF` / `#FFFFFF` / `#E8E6E1` |
| `surfaceContainer` / `surfaceContainerHigh` | `#1C1C20` / `#27272C` | `#F4F2EE` / `#E6E2DC` | `#0F0F12` / `#1B1B20` | `#F4F2EE` / `#D6D3CD` |
| `onBackground` / `onSurface` / `onSurfaceVariant` | `#F4F4F5` / `#F4F4F5` / `#A1A1AA` | `#151517` / `#151517` / `#52525A` | `#FFFFFF` / `#FFFFFF` / `#E4E4E7` | `#000000` / `#000000` / `#18181B` |
| `outline` / `outlineVariant` | `#76767E` / `#333339` | `#71717A` / `#DCDAD5` | `#A1A1AA` / `#71717A` | `#000000` / `#52525B` |

## 3. Auxiliary Semantic Tokens (`Color.kt`)

| Domain Role | Dark Mode (`bg / fg`) | Light Mode (`bg / fg`) | Consumers |
|---|---|---|---|
| **Burnished Ochre** (`Ochre*`) | `#332712` / `#F6DFB0` (accent `#E2B866`, onAccent `#120C06`) | `#F7ECD6` / `#422B04` (accent `#8A5D0A`, onAccent `#FFFFFF`) | `ContinuousTimerComponents` (`TRANSITION`), `CompactComplexLogComponents` (`Mixed` weight), `HistoryScreen` (warm-up), `SessionEditor` (`[W]`), `ProgressScreen` (`WARNING_AMBER`), `VoiceWorkoutIngestionSheet` (`PROCESSING`) |
| **Fat Loss Subcycle** (`SubcycleFatLoss*`) | `#1B2430` / `#8EB2D4` | `#E6EEF5` / `#2B5278` | `ProgressScreen.resolveSubcyclePillColors` |
| **Recomposition Subcycle** | `#132E24` / `#5DB894` | `#D9EDE5` / `#1E5C45` | `ProgressScreen.resolveSubcyclePillColors` |
| **Muscle Gain Subcycle** (`SubcycleGain*`) | `#281E33` / `#C4A5DE` | `#EFE9F5` / `#5C3A78` | `ProgressScreen.resolveSubcyclePillColors` |
| **Launcher Icon** (`ic_launcher_*`) | `#101012` bg, `#E4E4E7` bar, `#D49B6A` outer plates | Same | `colors.xml`, `themes.xml`, `ic_launcher_background.xml`, `ic_launcher_foreground.xml` |
