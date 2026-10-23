# CrossTraining App Constitution

## Core Principles

### I. MVVM & Unidirectional Data Flow (UDF)
All presentation and state management MUST follow Model-View-ViewModel (MVVM) with strict Unidirectional Data Flow (`Composable → ViewModel → Repository → Room/Firebase`). ViewModels MUST expose immutable `StateFlow<UiState>` streams collected via `collectAsStateWithLifecycle()` in Jetpack Compose screens. UI components MUST remain stateless and side-effect-free with respect to domain calculations.

### II. Pure Deterministic Analytics & Display-Unit Consistency
All domain calculations (weight trends, rolling averages, pace velocities, required-pace targets, and multi-horizon forecasts) MUST be implemented as pure, deterministic Kotlin functions with explicit date injection (`referenceDate` / `today`). Displayed metrics, deltas, and projections MUST satisfy Display-Unit Arithmetic Consistency (`BigDecimal` `HALF_UP` rounding in the active display unit `kg` or `lbs`) so that displayed numbers reconcile without hidden floating-point drift.

### III. Test-First & 100% CI Parity (NON-NEGOTIABLE)
Every domain calculation, state transition, and UI string/state resolver MUST be covered by deterministic unit tests (`testDebugUnitTest`) and Compose UI tests where applicable. No pull request may be opened or merged unless `.\gradlew.bat testDebugUnitTest --no-daemon`, `.\scripts\tests\Invoke-ScriptTests.ps1`, and `.\gradlew.bat lintDebug --no-daemon` pass at 100%.

### IV. Responsive Mobile UX & Accessibility
All Jetpack Compose cards, grids, and progress visualizations MUST render without text clipping, overlap, or layout overflow on `360dp`-wide viewports up to `fontScale = 1.3f`. Composite metric rows and cells MUST provide merged TalkBack accessibility semantics (`Modifier.semantics(mergeDescendants = true)`) announcing context, value, and status clearly.

### V. Offline-First & Environment Isolation
Local Room SQLite persistence is the primary source of truth, with asynchronous cloud synchronization where configured. Repository `gradle.properties` MUST NEVER contain machine-specific paths (such as `org.gradle.java.home`). Database schema modifications require explicit migrations; features that can be derived from existing entities MUST avoid unnecessary schema changes.

## Technical Stack & Architectural Constraints

- **Platform & Language**: Android (`compileSdk 35`, `minSdk 26`), Kotlin (JVM 17), Gradle Kotlin DSL (`.\gradlew.bat`).
- **UI Toolkit**: Jetpack Compose with Material 3 design tokens (`MaterialTheme.colorScheme` and `MaterialTheme.typography`).
- **Persistence**: Room SQLite (`Cycle`, `CycleGoal`, `WeightEntry`, `DailyLog`, `SessionWithBlocks`).

## Development Workflow & Quality Gates

1. **Specification & Planning**: Features are specified in `specs/<NNN>-<short-name>/spec.md`, validated via quality checklists, planned in `plan.md`, and decomposed into actionable tasks in `tasks.md`.
2. **Pre-PR Verification Suite**: Developers MUST execute `.\gradlew.bat testDebugUnitTest --no-daemon`, `.\scripts\tests\Invoke-ScriptTests.ps1`, and `.\gradlew.bat lintDebug --no-daemon` locally before pushing.
3. **Artifact Sync & Changelog**: Debug APK builds MUST sync to `local_test\latest.apk`, and user-facing changes MUST be documented under `## [Unreleased]` in `CHANGELOG.md`.

## Governance

This Constitution supersedes ad-hoc conventions across specifications, implementation plans, and code reviews. Amendments require updating `.specify/memory/constitution.md` with an incremented semantic version (`MAJOR.MINOR.PATCH`), an updated `Last Amended` ISO date, and verification that active templates and plans comply with all Core Principles.

**Version**: 1.0.0 | **Ratified**: 2026-10-22 | **Last Amended**: 2026-10-23
