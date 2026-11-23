# Implementation Plan: Exclusive Graphite, Titanium & Warm Bronze Theme

**Branch**: `feat/030-graphite-bronze-theme` | **Date**: 2026-11-23 | **Spec**: [spec.md](file:///c:/Users/rogal/workspaces/ws-gym/crosstrainingapp/specs/030-graphite-bronze-theme/spec.md)

**Input**: Feature specification from `/specs/030-graphite-bronze-theme/spec.md`

## Summary

Replace the existing high-saturation Volt Green and Electric Cyan athletic theme across all four `AppThemeMode` palettes (`DARK`, `LIGHT`, `DARK_HIGH_CONTRAST`, `LIGHT_HIGH_CONTRAST`) with a cohesive **Graphite, Titanium & Warm Bronze** design system, switch the fresh-install default to **Dark Graphite (`AppThemeMode.DARK`)**, eliminate hardcoded `Color(0x...)` literals and `isSystemInDarkTheme()` leaks across 8 UI screen/component files via a centralized `isAppDarkTheme()` helper and semantic tokens, polish cold-launch Android window chrome and launcher icon assets, update `design-system/crosstraining/MASTER.md` and `CHANGELOG.md`, and verify everything with automated WCAG AA/AAA unit tests, script tests, and Android Lint.

## Technical Context

**Language/Version**: Kotlin (JVM 17), XML Android resources
**Primary Dependencies**: Jetpack Compose Material 3 (`androidx.compose.material3`), AndroidX Activity Compose (`enableEdgeToEdge`, `SystemBarStyle`)
**Storage**: `SharedPreferences` (`"crosstraining-prefs"`, `KEY_THEME_MODE`) via `DataModeManager` — zero Room or Firestore schema changes
**Testing**: JUnit 4 (`.\gradlew.bat testDebugUnitTest --no-daemon`), PowerShell Pester (`.\scripts\tests\Invoke-ScriptTests.ps1`), Android Lint (`.\gradlew.bat lintDebug --no-daemon`)
**Target Platform**: Android (`minSdk = 26`, `compileSdk = 35`, `targetSdk = 35`)
**Project Type**: Native Android mobile app (`app/`)
**Performance Goals**: Zero recomposition overhead (`isAppDarkTheme()` reads `MaterialTheme.colorScheme.background.luminance()`) and instant theme switching
**Constraints**: Preserve existing `AppThemeMode` enum constant names for backward-compatible persistence; preserve `WaveformVisualizerHelper` public API for existing tests; zero hardcoded `Color(0x...)` outside `ui/theme/Color.kt` in main code; WCAG AA (`>= 4.5:1` / `>= 3.0:1`) for standard modes and WCAG AAA (`>= 7.0:1`) for High-Contrast modes; no emulator or Maestro runs
**Scale/Scope**: 19 source/resource/test/doc files (`ui/theme/`, `data/`, `MainActivity.kt`, 8 UI screens/components, 4 XML resources, `MASTER.md`, `CHANGELOG.md`, 2 unit test files)

## Constitution Check

*GATE: Passed pre-design and post-design.*

1. **Principle I (MVVM & UDF)**: PASS — Theme mode flows reactively from `DataModeManager.themeMode` -> `AppViewModel.themeMode` -> `MainActivity` -> `CrossTrainingTheme`; UI composables remain stateless.
2. **Principle II (Deterministic Analytics & Display-Unit Consistency)**: N/A — Pure visual theme and resource refactor; no numeric analytics altered.
3. **Principle III (Test-First & 100% CI Parity)**: PASS — Existing `ThemeModeTest.kt`, `WeightProgressionCardTest.kt`, and `VoiceWorkoutIngestionSheetTest.kt` will be updated/expanded with explicit WCAG AA/AAA contrast assertions and verified with `testDebugUnitTest`, `Invoke-ScriptTests.ps1`, and `lintDebug`.
4. **Principle IV (Responsive Mobile UX & Accessibility)**: PASS — Enforces WCAG AA (`>= 4.5:1` text, `>= 3.0:1` borders) and WCAG AAA (`>= 7.0:1`) across all four modes without altering typography sizing, touch targets, or non-color indicators (`▲`/`▼`/`=`).
5. **Principle V (Offline-First & Environment Isolation)**: PASS — Zero database schema migrations, zero machine-specific `gradle.properties` changes, 100% backward-compatible `SharedPreferences` key handling.
6. **Principle VI (Agentic Collaboration & PR Governance)**: PASS — Standard `feat/030-graphite-bronze-theme` PR against `main` adhering to CI gates and `CHANGELOG.md`.
7. **Principle VII (MCP Compatibility)**: N/A — `mcp-server/` untouched.

## Project Structure

### Documentation (this feature)

```text
specs/030-graphite-bronze-theme/
├── spec.md                              # Feature specification (US1-US4, FR-001..FR-012)
├── plan.md                              # This file
├── research.md                          # Palette, WCAG, isAppDarkTheme & API compatibility decisions
├── data-model.md                        # AppThemeMode persistence & 4-mode token matrix
├── quickstart.md                        # Automated verification commands & manual walkthrough
├── tasks.md                             # Dependency-ordered implementation checklist
└── checklists/
    ├── requirements.md                  # Spec completeness checklist
    └── theme-accessibility.md           # Theme & accessibility quality gate checklist
```

### Source Code (repository root)

```text
app/src/main/
├── java/com/fractanomics/crosstraining/
│   ├── MainActivity.kt                                     # Dynamic SystemBarStyle edge-to-edge sync
│   ├── data/DataModeManager.kt                             # Default themeMode -> AppThemeMode.DARK
│   └── ui/
│       ├── theme/Color.kt                                  # Graphite, Titanium, Bronze, Sage, Crimson, Ochre, Subcycle tokens
│       ├── theme/Theme.kt                                  # 4 ColorSchemes, DARK default, isAppDarkTheme()
│       ├── components/ContinuousTimerComponents.kt         # Transition Ochre tokens via isAppDarkTheme()
│       ├── voice/VoiceWorkoutIngestionSheet.kt             # WaveformVisualizerHelper mapped to Color.kt tokens
│       └── screens/
│           ├── ProfileScreen.kt                            # ThemeOptionCard swatches from ColorSchemes; DARK badge "DEFAULT"
│           ├── CompactComplexLogComponents.kt              # Replace 38 Complex* literals with ColorScheme & Ochre
│           ├── ProgressScreen.kt                           # Subcycle/Momentum/Trend/Constituent tokens & isAppDarkTheme()
│           ├── LibraryScreen.kt                            # Block/Exercise/PR chip colors -> ColorScheme
│           ├── SimplifiedBlockLogComponents.kt             # Remove PrEmerald*/BlockSky* -> ColorScheme
│           ├── HistoryScreen.kt                            # Warm-up pill -> centralized Ochre tokens
│           └── SessionEditor.kt                            # [W] warm-up pill -> centralized Ochre tokens
└── res/
    ├── values/colors.xml                                   # #D49B6A primary, #101012 graphite surface/launcher bg
    ├── values/themes.xml                                   # statusBarColor -> @color/surface (#101012)
    └── drawable/
        ├── ic_launcher_background.xml                      # #101012 Deep Graphite
        └── ic_launcher_foreground.xml                      # #E4E4E7 Titanium bar + #D49B6A Bronze outer plates

app/src/test/java/com/fractanomics/crosstraining/ui/
├── theme/ThemeModeTest.kt                                  # Default DARK + WCAG AA/AAA automated checks
└── screens/WeightProgressionCardTest.kt                    # Updated resolveMomentumBadgeColors & subcycle checks

design-system/crosstraining/MASTER.md                       # Updated Section 2 token specification
CHANGELOG.md                                                # Entry under ## [Unreleased]
```

**Structure Decision**: Direct updates to existing Android `app/src/` theme, screen, resource, and test packages plus `design-system/crosstraining/MASTER.md` and `CHANGELOG.md`; no new Gradle modules or schema changes required.
