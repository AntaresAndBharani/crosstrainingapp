# Theme & Accessibility Quality Checklist: Graphite, Titanium & Warm Bronze

**Purpose**: Validate completeness, consistency, and WCAG accessibility readiness of the theme specification and plan before implementation.  
**Created**: 2026-11-23  
**Feature**: [spec.md](file:///c:/Users/rogal/workspaces/ws-gym/crosstrainingapp/specs/030-graphite-bronze-theme/spec.md)

## Palette & Contrast Quality

- [x] CHK001 All four `AppThemeMode` palettes (`DARK`, `LIGHT`, `DARK_HIGH_CONTRAST`, `LIGHT_HIGH_CONTRAST`) define complete Material 3 roles without missing container or surface tokens (`spec.md` FR-001, `data-model.md`).
- [x] CHK002 Standard `DARK` and `LIGHT` palettes verify WCAG AA contrast (`>= 4.5:1` for text/accent pairs, `>= 3.0:1` for outlines), and High-Contrast modes verify WCAG AAA (`>= 7.0:1`) across all pairs (`spec.md` FR-005, `research.md`).
- [x] CHK003 Auxiliary semantic pairs (Burnished Ochre warm-up/transition/warning and Weight Subcycle Fat Loss / Recomposition / Muscle Gain pills) specify explicit light and dark variants meeting `>= 4.5:1` (`spec.md` FR-006, `data-model.md`).

## Persistence & Upgrade Compatibility

- [x] CHK004 Fresh installs default to `AppThemeMode.DARK`, while persisted `SharedPreferences` values for existing users remain valid across upgrades (`spec.md` FR-002, FR-003).
- [x] CHK005 Profile `ThemeOptionCard` previews read directly from `LightColors`, `DarkColors`, `LightHighContrastColors`, and `DarkHighContrastColors` to eliminate preview/live theme drift (`spec.md` FR-008).

## Cross-Screen Cohesion & OS Independence

- [x] CHK006 All 8 screen/component files containing hardcoded `Color(0x...)` literals are explicitly mapped to centralized theme tokens (`spec.md` FR-007, `plan.md`).
- [x] CHK007 Screen helpers replace `isSystemInDarkTheme()` with an in-app theme luminance check (`isAppDarkTheme()`) so OS theme mismatches never corrupt in-app styling (`spec.md` Edge Cases, `research.md`).
- [x] CHK008 Existing test symbols (`WaveformVisualizerHelper` properties) and non-color redundancy indicators (`▲`, `▼`, `=`, badges) are explicitly preserved (`spec.md` Edge Cases, `research.md`).

## Launch Chrome, Resources & Verification

- [x] CHK009 Cold-launch window, status bar, and adaptive launcher icon assets replace all `#00E676` neon green references with `#101012` Graphite, `#E4E4E7` Titanium, and `#D49B6A` Warm Bronze (`spec.md` FR-009, FR-010).
- [x] CHK010 Dynamic edge-to-edge `SystemBarStyle` synchronization tracks `themeMode.isDark` for legible status/navigation icons across runtime theme switches (`spec.md` FR-009, `plan.md`).
- [x] CHK011 Automated tests (`ThemeModeTest`, `WeightProgressionCardTest`) cover default mode, WCAG AA/AAA contrast math, and updated badge resolvers (`spec.md` FR-012, `quickstart.md`).
- [x] CHK012 `design-system/crosstraining/MASTER.md` and `CHANGELOG.md` are explicitly scheduled for synchronized documentation updates (`spec.md` FR-011, `plan.md`).

## Notes

- All 12 items reviewed against `spec.md`, `plan.md`, `research.md`, and `data-model.md` with zero unresolved gaps.
