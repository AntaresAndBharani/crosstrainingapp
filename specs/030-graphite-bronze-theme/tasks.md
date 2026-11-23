---
description: "Task list for Exclusive Graphite, Titanium & Warm Bronze Theme implementation"
---

# Tasks: Exclusive Graphite, Titanium & Warm Bronze Theme (`030-graphite-bronze-theme`)

**Input**: Design documents from `/specs/030-graphite-bronze-theme/`  
**Prerequisites**: `plan.md` (required), `spec.md` (required), `research.md`, `data-model.md`, `quickstart.md`

**Tests**: Unit tests and WCAG contrast assertions are explicitly required by `spec.md` (`FR-012`) and Principle III of `.specify/memory/constitution.md`.

## Phase 1: Setup (Shared Infrastructure)

**Purpose**: Ensure branch and Spec-Kit artifacts are active before modifying source files.

- [X] T001 Verify feature branch `feat/030-graphite-bronze-theme` and `.specify/feature.json` active context

---

## Phase 2: Foundational (Core Theme Tokens & In-App Dark Mode Helper)

**Purpose**: Centralize all Graphite, Titanium, Warm Bronze, Muted Sage, Muted Crimson, Burnished Ochre, and Weight Subcycle color tokens and expose `isAppDarkTheme()` before screen updates.

- [X] T002 Replace all four palette definitions (`LIGHT`, `DARK`, `LIGHT_HIGH_CONTRAST`, `DARK_HIGH_CONTRAST`) and add centralized `Ochre*`, `Subcycle*`, `Timer*`, and `Chart*` semantic tokens in `app/src/main/java/com/fractanomics/crosstraining/ui/theme/Color.kt`
- [X] T003 Map `LightColors`, `DarkColors`, `LightHighContrastColors`, and `DarkHighContrastColors`, update `AppThemeMode` descriptions, change `CrossTrainingTheme` default to `AppThemeMode.DARK`, and add `isAppDarkTheme()` in `app/src/main/java/com/fractanomics/crosstraining/ui/theme/Theme.kt` (depends on T002)

**Checkpoint**: Centralized theme system and `isAppDarkTheme()` ready for all screens and tests.

---

## Phase 3: User Story 1 - Dark-First Exclusive Palette & Theme Picker Parity (Priority: P1) 🎯 MVP

**Goal**: Make Dark Graphite (`AppThemeMode.DARK`) the fresh-install default while keeping saved preferences compatible and aligning Profile theme previews directly with `ColorScheme` objects.

**Independent Test**: Run `ThemeModeTest` verifying default mode `DARK`, enum round-trip parsing, and live `ThemeOptionCard` swatches without hardcoded hex drift.

- [X] T004 [P] [US1] Switch fresh-install `themeMode` fallback from `AppThemeMode.LIGHT` to `AppThemeMode.DARK` in `app/src/main/java/com/fractanomics/crosstraining/data/DataModeManager.kt`
- [X] T005 [P] [US1] Order `AppThemeMode` cards with `DARK` first, mark `DARK` as `"DEFAULT"`, and drive `ThemeOptionCard` swatches directly from `DarkColors`, `LightColors`, `DarkHighContrastColors`, and `LightHighContrastColors` in `app/src/main/java/com/fractanomics/crosstraining/ui/screens/ProfileScreen.kt`

**Checkpoint**: Core exclusive theme + Dark Graphite default + Profile Theme Picker functional and consistent.

---

## Phase 4: User Story 2 - Cohesive Cross-Screen Color System (Priority: P1)

**Goal**: Remove all hardcoded per-screen `Color(0x...)` literals and `isSystemInDarkTheme()` checks across the 8 UI files so every screen matches the active in-app theme cleanly.

**Independent Test**: Verify zero `Color(0x` outside `Color.kt` and zero `isSystemInDarkTheme(` in `app/src/main/java`, and verify `WeightProgressionCardTest` and `VoiceWorkoutIngestionSheetTest` pass.

- [X] T006 [P] [US2] Replace 38 `Complex*` hardcoded color constants and `isSystemInDarkTheme()` with `MaterialTheme.colorScheme.*`, `isAppDarkTheme()`, and centralized `Ochre*` tokens in `app/src/main/java/com/fractanomics/crosstraining/ui/screens/CompactComplexLogComponents.kt`
- [X] T007 [P] [US2] Update `resolveSubcyclePillColors`, `resolveMomentumBadgeColors`, `ConstituentTypeBadge`, `DailyRoutineConstituentOverviewCard`, horizon gradient brush, and `isSystemInDarkTheme()` calls to centralized theme tokens and `isAppDarkTheme()` in `app/src/main/java/com/fractanomics/crosstraining/ui/screens/ProgressScreen.kt`
- [X] T008 [P] [US2] Replace routine/composer chip and badge hardcoded colors and `isSystemInDarkTheme()` with `MaterialTheme.colorScheme.*` container/onContainer tokens in `app/src/main/java/com/fractanomics/crosstraining/ui/screens/LibraryScreen.kt`
- [X] T009 [P] [US2] Remove `PrEmerald*` and `BlockSky*` hardcoded hex literals and `isSystemInDarkTheme()`, mapping block headers and PR badges to `MaterialTheme.colorScheme.primary` / `tertiary` / `tertiaryContainer` in `app/src/main/java/com/fractanomics/crosstraining/ui/screens/SimplifiedBlockLogComponents.kt`
- [X] T010 [P] [US2] Replace private `AmberTransition*` / `AmberContainer*` and transition text hexes with centralized `Ochre*` tokens and `isAppDarkTheme()` in `app/src/main/java/com/fractanomics/crosstraining/ui/components/ContinuousTimerComponents.kt`
- [X] T011 [P] [US2] Replace hardcoded warm-up pill amber colors with centralized `Ochre*` tokens and `isAppDarkTheme()` in `app/src/main/java/com/fractanomics/crosstraining/ui/screens/HistoryScreen.kt`
- [X] T012 [P] [US2] Replace hardcoded `[W]` warm-up pill `Color(0xFFF59E0B)` / `Color.White` colors with centralized `Ochre*` tokens and `isAppDarkTheme()` in `app/src/main/java/com/fractanomics/crosstraining/ui/screens/SessionEditor.kt`
- [X] T013 [P] [US2] Map `WaveformVisualizerHelper` status properties (`GreenListening`, `GreenListeningLight`, `YellowProcessing`, `RedError`, `NeutralIdle`) to centralized `Color.kt` tokens while keeping existing test API compatibility in `app/src/main/java/com/fractanomics/crosstraining/ui/voice/VoiceWorkoutIngestionSheet.kt`

**Checkpoint**: All 8 screen/component files unified under `Color.kt` and `Theme.kt`.

---

## Phase 5: User Story 3 - Cold-Launch Chrome, System Bars & Launcher Icon (Priority: P2)

**Goal**: Eliminate the bright green launch flash, synchronize status/navigation bar icon colors with `themeMode.isDark`, and recolor the adaptive launcher icon to Graphite, Titanium & Warm Bronze.

**Independent Test**: Confirm zero `#00E676` occurrences under `app/src/main/res/` and verify `MainActivity.kt` compiles and lints cleanly with dynamic `SystemBarStyle`.

- [X] T014 [P] [US3] Update `brand_primary` (`#D49B6A`), `brand_primary_dark` (`#101012`), `surface` (`#101012`), and `ic_launcher_background` (`#101012`) in `app/src/main/res/values/colors.xml`
- [X] T015 [P] [US3] Update `android:statusBarColor` from `@color/brand_primary` to `@color/surface` in `app/src/main/res/values/themes.xml`
- [X] T016 [P] [US3] Recolor adaptive launcher background to `#101012` in `app/src/main/res/drawable/ic_launcher_background.xml` and barbell emblem to Brushed Titanium `#E4E4E7` + Warm Bronze `#D49B6A` in `app/src/main/res/drawable/ic_launcher_foreground.xml`
- [X] T017 [US3] Synchronize `enableEdgeToEdge` `SystemBarStyle` dynamically with `themeMode.isDark` via `DisposableEffect(themeMode)` in `app/src/main/java/com/fractanomics/crosstraining/MainActivity.kt`

**Checkpoint**: Launch window, edge-to-edge bars, and adaptive launcher icon aligned with Graphite & Warm Bronze.

---

## Phase 6: User Story 4 - Living Design System & Automated Contrast Tests (Priority: P2)

**Goal**: Document the finalized 4-mode palette in `MASTER.md` and lock down WCAG AA/AAA contrast ratios and updated theme/badge tests.

**Independent Test**: Execute `.\gradlew.bat testDebugUnitTest --no-daemon` and verify all assertions pass with zero failures.

- [X] T018 [P] [US4] Update Section 2 color system tables, auxiliary token documentation, and `isAppDarkTheme()` guidance in `design-system/crosstraining/MASTER.md`
- [X] T019 [P] [US4] Update default mode test (`AppThemeMode.DARK`), verify `DataModeManager(null).themeMode.value`, and add automated WCAG AA (`>= 4.5:1` / `>= 3.0:1`) and WCAG AAA (`>= 7.0:1`) contrast assertions across all 4 `ColorScheme` palettes and auxiliary tokens in `app/src/test/java/com/fractanomics/crosstraining/ui/theme/ThemeModeTest.kt`
- [X] T020 [P] [US4] Update `resolveMomentumBadgeColors` expectations and add `resolveSubcyclePillColors` + `MomentumSeverity.NEUTRAL` assertions in `app/src/test/java/com/fractanomics/crosstraining/ui/screens/WeightProgressionCardTest.kt`

---

## Phase 7: Polish, Convergence & Full Pre-PR Verification

**Purpose**: Run static hygiene checks, full test/lint/build verification, sync local APK, update `CHANGELOG.md`, and prepare PR.

- [X] T021 Run static audits verifying zero `Color(0x` outside `Color.kt`, zero `isSystemInDarkTheme(` in `app/src/main/java`, and zero `#00E676` in `app/src/main/res`
- [X] T022 Update `CHANGELOG.md` under `## [Unreleased]` summarizing `SPEC-030`
- [X] T023 Execute full local verification suite (`.\gradlew.bat testDebugUnitTest --no-daemon`, `.\scripts\tests\Invoke-ScriptTests.ps1`, `.\gradlew.bat lintDebug --no-daemon`, `.\gradlew.bat assembleDebug --no-daemon`) and copy `app\build\outputs\apk\debug\app-debug.apk` to `local_test\latest.apk`

---

## Dependencies & Execution Order

- **Phase 1 (T001)** -> **Phase 2 (T002 -> T003)** -> unlocks **Phase 3 (T004, T005)**, **Phase 4 (T006–T013)**, **Phase 5 (T014–T017)**, and **Phase 6 (T018–T020)** in parallel.
- **Phase 7 (T021–T023)** runs after Phases 3–6 complete.
