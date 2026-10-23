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

### VI. Agentic Collaboration, Automated PR Review & Self-Healing Governance
GitHub collaboration and Pull Request governance MUST integrate deterministic AI review and self-healing workflow automation without redundant build overhead or self-cancellation races.
- Interactive `@claude` mentions on Issues, Issue Comments, Pull Request Reviews, and PR Review Comments (`.github/workflows/claude.yml`) and automated Pull Request reviews (`.github/workflows/pr-review.yml`) MUST execute Claude Opus 5.5 (`--model claude-opus-5-5 --effort high`) on lightweight `ubuntu-latest` runners without duplicating cold JDK/Gradle setup inside the Claude workflows.
- `.github/workflows/pr-review.yml` MUST own the unified review-to-merge lifecycle: diff/source review, in-place `[skip pr-review]` auto-fix commit/push, idempotent `gh label create --force` self-healing label provisioning (`review:approved`, `review:changes-requested`, `needs-refactor`, `status:needs-po-input`, `status:ready`, `status:done`, `dev-implemented`, `tech-debt`), follow-up backlog issue creation, strict `--commit "$CURRENT_SHA"` polling against `PR CI & Test Verification` (`build.yml`) with an explicit `CI_PASSED="true"` gate, squash-merge, and post-merge parent-story closure.
- Concurrency on `pr-review-<PR>` MUST set `cancel-in-progress: false` so pushing an `[skip pr-review]` auto-fix commit queues behind the active run rather than cancelling it, and `steps.meta` MUST provide a zero-token `merge_only` fast-path when an open PR's `HEAD` commit is `[skip pr-review]` accompanied by an `APPROVED` verdict comment.

## Technical Stack & Architectural Constraints

- **Platform & Language**: Android (`compileSdk 35`, `minSdk 26`), Kotlin (JVM 17), Gradle Kotlin DSL (`.\gradlew.bat`).
- **UI Toolkit**: Jetpack Compose with Material 3 design tokens (`MaterialTheme.colorScheme` and `MaterialTheme.typography`).
- **Persistence**: Room SQLite (`Cycle`, `CycleGoal`, `WeightEntry`, `DailyLog`, `SessionWithBlocks`).
- **CI/CD & Agentic Workflows**: GitHub Actions (`build.yml`, `release.yml`, `claude.yml`, `pr-review.yml`) with PowerShell (`pwsh`) static and behavioral regression tests (`scripts/tests/Invoke-ScriptTests.ps1`).

## Development Workflow & Quality Gates

1. **Specification & Planning**: Features are specified in `specs/<NNN>-<short-name>/spec.md`, validated via quality checklists, planned in `plan.md`, and decomposed into actionable tasks in `tasks.md`.
2. **Pre-PR Verification Suite**: Developers MUST execute `.\gradlew.bat testDebugUnitTest --no-daemon`, `.\scripts\tests\Invoke-ScriptTests.ps1`, and `.\gradlew.bat lintDebug --no-daemon` locally before pushing.
3. **Artifact Sync & Changelog**: Debug APK builds MUST sync to `local_test\latest.apk`, and user-facing or CI/CD governance changes MUST be documented under `## [Unreleased]` in `CHANGELOG.md`.
4. **Interactive `@claude` Collaboration & Automated PR Review / Auto-Merge Gate**:
   - Pull Requests targeting `main` are verified by both `PR CI & Test Verification` (`.github/workflows/build.yml`) and `PR Review` (`.github/workflows/pr-review.yml`).
   - All GitHub Actions workflow contracts MUST be covered by deterministic PowerShell static tests in `scripts/tests/*.Tests.ps1` executed on both `ubuntu-latest` and `windows-latest`.

## Governance

This Constitution supersedes ad-hoc conventions across specifications, implementation plans, and code reviews. Amendments require updating `.specify/memory/constitution.md` with an incremented semantic version (`MAJOR.MINOR.PATCH`), an updated `Last Amended` ISO date, and verification that active templates and plans comply with all Core Principles.

**Version**: 1.1.0 | **Ratified**: 2026-10-22 | **Last Amended**: 2026-10-23
