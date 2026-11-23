# Feature Specification: Exclusive Graphite, Titanium & Warm Bronze Theme (`030-graphite-bronze-theme`)

**Feature Branch**: `feat/030-graphite-bronze-theme`

**Created**: 2026-11-23

**Status**: Approved for Implementation

**Input**: User design decisions locked during interactive `/ui-ux-pro-max` review and approved via `/e2e-sdlc --full --prompt Implement the change.`

## User Scenarios & Testing *(mandatory)*

### User Story 1 - Exclusive Graphite, Titanium & Warm Bronze Palette & Dark-First Default (Priority: P1)

As an athlete opening the CrossTraining mobile application, I want the interface to use an exclusive Graphite, Titanium & Warm Bronze visual identity with Dark Graphite as the default experience, while still supporting Light Ivory and WCAG AAA High-Contrast modes in Appearance settings.

**Why this priority**: Replacing the bright sporty green/cyan palette with restrained warm neutrals and metallic bronze transforms the perceived brand exclusivity on every screen immediately.

**Independent Test**: Can be verified by launching with cleared preferences (confirming Dark Graphite is selected by default), switching among all four theme modes in Profile settings, and verifying preview cards and surface/accent contrast ratios meet WCAG AA (`>= 4.5:1`) for standard modes and WCAG AAA (`>= 7.0:1`) for high-contrast modes.

**Acceptance Scenarios**:

1. **Given** a fresh application state with no saved theme preference, **When** the app opens, **Then** the Dark Graphite & Warm Bronze theme (`DARK`) is active across all surfaces and listed as the default option in Profile Appearance settings.
2. **Given** a returning user who previously selected Light, Dark, Light High Contrast, or Dark High Contrast, **When** the app updates and launches, **Then** their saved mode identifier continues to load cleanly without reset or error, styled in the updated exclusive palette.
3. **Given** a user inspecting Profile Appearance settings, **When** viewing each theme card, **Then** its swatch colors match the live theme colors for that mode and switching modes updates the entire interface immediately.

---

### User Story 2 - Cohesive Cross-Screen Color System Independent of Device OS Mode (Priority: P1)

As an athlete logging workouts, checking analytics, browsing routines, running timers, or using voice logging, I want every badge, chip, chart, set row, and warm-up/PR indicator to reflect the active in-app theme cleanly without bright green or sky-blue overrides or OS-theme mismatches.

**Why this priority**: Hardcoded colors in workout cards, analytics badges, library tags, and timer transitions break brand cohesion and cause visual clashes when the in-app theme differs from the device's OS light/dark setting.

**Independent Test**: Can be tested by switching the app between Light and Dark modes while the device OS theme is set to the opposite mode, and verifying that complex workout cards, progress analytics pills, routine badges, timer phases, history warm-up tags, and voice indicators adapt cleanly to the in-app theme.

**Acceptance Scenarios**:

1. **Given** the device OS is in light mode while the user chooses Dark Graphite inside the app (or vice versa), **When** navigating across Log Session, Progress, Library, History, Timer, and Voice Logging, **Then** every card surface, complex header, PR chip, and trend badge follows the active in-app theme rather than the OS theme.
2. **Given** positive PR/trend indicators, warning/warm-up states, and negative/error deltas, **When** displayed on Progress charts, workout logs, or timer transitions, **Then** positive states use Muted Sage, warm-up/transition warnings use Burnished Ochre, and errors/negative states use Muted Crimson alongside non-color directional symbols or labels.
3. **Given** weight subcycle badges (Fat Loss, Recomposition, Muscle Gain) and momentum severity badges, **When** rendered in either Light or Dark mode, **Then** each badge maintains readable contrast (`>= 4.5:1`) without neon saturation.

---

### User Story 3 - Seamless Cold-Launch Chrome & Graphite/Bronze App Launcher Icon (Priority: P2)

As an athlete launching the app from my phone's home screen, I want the app icon, initial launch window, and system status/navigation bar icons to match the Graphite, Titanium & Warm Bronze identity without a bright green flash on startup.

**Why this priority**: Cold-launch window chrome and the home-screen launcher icon form the user's very first impression before interactive content renders.

**Independent Test**: Can be verified by inspecting launcher icon color definitions, cold-launch window/status bar theme resources, and verifying system bar icon light/dark appearance tracks theme switches.

**Acceptance Scenarios**:

1. **Given** the application cold-starts, **When** the system draws the initial window before UI hydration, **Then** the launch background and status bar render in Deep Graphite rather than bright neon green.
2. **Given** the user toggles between a dark theme mode and a light theme mode, **When** the theme updates, **Then** system status and navigation bar foreground icons automatically adapt for high legibility against the active background.
3. **Given** the home-screen barbell emblem, **When** rendered on the device launcher, **Then** it displays Warm Bronze outer plates and Brushed Titanium bar/inner plates over a Deep Graphite background.

---

### User Story 4 - Living Design System Alignment & Automated Contrast Verification (Priority: P2)

As a maintainer, I want the project's design system documentation and automated test suite updated to lock in the Graphite, Titanium & Warm Bronze tokens and enforce WCAG contrast thresholds automatically.

**Why this priority**: Automated contrast checks and synchronized design documentation prevent accidental regressions or unverified color edits in future features.

**Independent Test**: Can be tested by running the unit test suite, script tests, and lint checks and confirming all theme, badge, voice, and contrast assertions pass.

**Acceptance Scenarios**:

1. **Given** all four theme palettes and auxiliary warning/subcycle token pairs, **When** automated theme tests execute, **Then** every standard pair passes WCAG AA (`>= 4.5:1` text, `>= 3.0:1` outlines) and every high-contrast pair passes WCAG AAA (`>= 7.0:1`).
2. **Given** future UI contributors consulting the design system master guide, **When** reading the color system section, **Then** the documented hex values, default mode, and theme-resolution rules match the active application tokens.

---

### Edge Cases

- What happens when a user upgrades from an earlier version with a persisted `"LIGHT"` or `"DARK_HIGH_CONTRAST"` preference? The stored preference name remains valid (`LIGHT`, `DARK`, `LIGHT_HIGH_CONTRAST`, `DARK_HIGH_CONTRAST`), so the user's mode stays intact while inheriting the refined exclusive palette.
- What happens when an unrecognized or corrupted theme preference value exists in storage? The system safely falls back to the new default Dark Graphite mode (`DARK`).
- What happens when the device OS dark/light setting disagrees with the user's chosen in-app theme mode? UI surfaces derive light/dark status from the active in-app theme palette rather than the OS setting, preventing unreadable low-contrast cards.
- What happens under large text scaling (`fontScale >= 1.3f`) or `360dp` narrow screens? Color changes preserve existing padding, borders, and non-color indicators (`▲`, `▼`, `=`, explicit labels) without altering layout bounds.

## Requirements *(mandatory)*

### Functional Requirements

- **FR-001**: The application MUST replace all four built-in theme palettes (`DARK`, `LIGHT`, `DARK_HIGH_CONTRAST`, `LIGHT_HIGH_CONTRAST`) with the Graphite, Titanium & Warm Bronze color system.
- **FR-002**: Dark Graphite (`DARK`) MUST serve as the default theme mode when no user preference has been saved or when a stored value cannot be parsed.
- **FR-003**: All four theme palettes MUST preserve existing theme mode identifiers so persisted user preferences upgrade seamlessly without data loss.
- **FR-004**: `tertiary` semantic tokens MUST use Muted Sage (`#5DB894` dark / `#1E5C45` light / AAA variants in HC) for personal records, positive trend highlights, prep timer state, and secondary positive analytics, while `error` MUST use Muted Crimson (`#E57373` dark / `#A62C2C` light / AAA variants in HC).
- **FR-005**: Standard `DARK` and `LIGHT` palettes MUST satisfy WCAG AA minimum contrast (`>= 4.5:1` for text/accent-on-surface pairs, `>= 3.0:1` for outlines), and `DARK_HIGH_CONTRAST` / `LIGHT_HIGH_CONTRAST` MUST satisfy WCAG AAA (`>= 7.0:1`) across all verified pairs.
- **FR-006**: Warm-up, transition timer, and warning indicators MUST consume centralized Burnished Ochre tokens (`#E2B866` dark / `#8A5D0A` light and matching containers) instead of per-screen hardcoded amber values.
- **FR-007**: All screen-level workout logging, progress analytics, routine library, session history, timer, and voice ingestion components MUST derive colors from centralized theme tokens rather than ad-hoc per-screen hex literals or OS-level dark-mode checks.
- **FR-008**: Theme selection cards in Profile settings MUST display preview swatches derived directly from the active theme palettes and mark Dark Graphite as `DEFAULT`.
- **FR-009**: Cold-launch window and status bar styling MUST use Deep Graphite (`#101012`) instead of bright green, and active system status/navigation bar icon styles MUST adapt dynamically when the user switches between dark and light themes.
- **FR-010**: The adaptive launcher icon MUST render Brushed Titanium (`#E4E4E7`) barbell bar and inner plates with Warm Bronze (`#D49B6A`) outer plates over a Deep Graphite (`#101012`) background.
- **FR-011**: The design system master specification (`design-system/crosstraining/MASTER.md`) and `CHANGELOG.md` MUST be updated to reflect the Graphite, Titanium & Warm Bronze system and Dark Graphite default.
- **FR-012**: Automated unit tests MUST verify default mode selection, backward-compatible mode parsing, updated badge/voice/theme resolvers, and WCAG AA/AAA contrast ratios across all four modes.

### Key Entities

- **Theme Mode**: Represents the four user-selectable visual themes (`DARK`, `LIGHT`, `DARK_HIGH_CONTRAST`, `LIGHT_HIGH_CONTRAST`) along with display titles, descriptions, and light/dark classification.
- **Color Palette & Auxiliary Semantic Tokens**: Centralized color definitions mapping Material roles (`primary`, `secondary`, `tertiary`, `error`, containers, surfaces, outlines) plus domain accents (Burnished Ochre warm-up/transition/warning, Weight Subcycle pills, Timer/Chart series).

## Success Criteria *(mandatory)*

### Measurable Outcomes

- **SC-001**: 100% of verified color pairs in `DARK` and `LIGHT` modes achieve `>= 4.5:1` contrast for text (`>= 3.0:1` for borders), and 100% of verified color pairs in `DARK_HIGH_CONTRAST` and `LIGHT_HIGH_CONTRAST` achieve `>= 7.0:1` contrast (verified by automated unit tests).
- **SC-002**: Zero hardcoded per-screen `Color(0x...)` literals remain outside the centralized theme color registry in main application UI code, and zero main UI screens rely on OS-level dark-mode checks instead of the in-app theme state.
- **SC-003**: Zero occurrences of the legacy bright neon `#00E676` brand color remain in Android resource files (`res/`).
- **SC-004**: 100% of existing and newly added unit tests, PowerShell script tests, and Android lint checks pass without error or regression.

## Assumptions

- Users keep their existing saved theme preference if one was explicitly chosen prior to this update; only new or unconfigured installations default to `DARK`.
- Existing font family (`AppTypography`) and tabular numeral (`tnum`) conventions remain unchanged; this feature focuses exclusively on color identity, theme wiring, launch chrome, and launcher icon alignment.
- Non-color redundancy (`▲`, `▼`, `=`, text badges, icons) already present across charts and deltas remains preserved.
