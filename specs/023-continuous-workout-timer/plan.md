# Implementation Plan: Continuous Workout Timer & Live Stage Queue

**Branch**: `023-continuous-workout-timer` (`feat/023-continuous-workout-timer`) | **Date**: 2026-11-15 | **Spec**: [spec.md](file:///c:/Users/rogal/workspaces/ws-gym/crosstrainingapp/specs/023-continuous-workout-timer/spec.md)
**Input**: Feature specification from `/specs/023-continuous-workout-timer/spec.md`

## Summary

Implement the Continuous Workout Timer & Live Stage Queue (`SPEC-023`) enabling (1) single-slot `Current + 1 Next Slot` mode chaining before start, mid-workout, during dedicated amber `TRANSITION` countdowns, and from `FINISHED`; (2) unified multi-round execution across all 6 timer modes with live `Total Rounds` controls and single-round `Next Round` overrides (`±15s` / `±30s` / `±5s` + tap-to-type via `AppNumericTextField`); (3) focused active workout cards with non-wrapping horizontal transport pills, `Reset` confirmation when queued/multi-stage, foreground notification updates during `TRANSITION`, and full parity across `TimerScreen`, `InSessionTimerSheet`, `InSessionTimerBar`, and `SubBlockInlineTimer`.

## Technical Context

**Language/Version**: Kotlin (JVM 17), Jetpack Compose (Material 3)
**Primary Dependencies**: AndroidX Lifecycle Compose (`collectAsStateWithLifecycle`), Coroutines (`StateFlow`, `kotlinx-coroutines-test`), existing `AppNumericTextField` (`ui/components/CommonUi.kt`), existing `TimerEngineProvider` / `TimerService`
**Storage**: N/A (In-memory `StateFlow<TimerSnapshot>` + Foreground `TimerService` — zero Room DB changes)
**Testing**: JUnit 4 + `kotlinx-coroutines-test` pure JVM unit tests (NO Robolectric, NO emulator/Maestro E2E tests)
**Target Platform**: Android SDK 35 (minSdk 26)
**Project Type**: Mobile (Single Android Gradle module `app/`)
**Performance Goals**: 1Hz tick loop with zero blocking I/O; TalkBack polite live-region announcements strictly on phase/round transitions (zero per-second speech churn)
**Constraints**: Narrow `360dp` screen support up to `fontScale = 1.3f` without vertical text wrapping; `>= 48.dp` touch targets; tabular digits (`tnum`); preserve backward compatibility with all existing timer unit tests
**Scale/Scope**: 11 main source files (2 new, 9 updated) + 1 comprehensive JVM test file (`ContinuousTimerTest.kt`)

## Constitution Check

*GATE: Must pass before Phase 0 research. Re-check after Phase 1 design.*

- **I. MVVM & Unidirectional Data Flow**: `TimerEngine` continues exposing `StateFlow<TimerSnapshot>` observed via `collectAsStateWithLifecycle()` across `TimerScreen`, `InSessionTimerSheet`, `InSessionTimerBar`, and `SubBlockInlineTimer`; shared UI components accept immutable state + event lambdas. **PASS**.
- **II. Deterministic Pure Logic**: `WorkoutTimerConfig` duration calculations, `TimerEngine.calculateTotalSecondsRemaining(...)`, `TimerNotificationFormatter`, and `ContinuousTimerUiResolver` are pure deterministic functions testable on JVM without Android framework mocks. **PASS**.
- **III. Testing & CI Parity**: All legacy unit tests preserved; comprehensive new JVM test suite (`ContinuousTimerTest.kt`) added; full `testDebugUnitTest`, `lintDebug`, `assembleDebug`, and `Invoke-ScriptTests.ps1` gated before push. **PASS**.
- **IV. Responsive UI & Accessibility**: Compact `52.dp` horizontal transport pills (`maxLines = 1`, `softWrap = false`, responsive icon hide at `fontScale >= 1.25f`), `>= 48.dp` touch targets, `fontFeatureSettings = "tnum"`, `Modifier.imePadding()` on bottom sheets, `LiveRegionMode.Polite` boundary announcements only, semantic descriptions via `continuousComponents` & `ContentDescription`. **PASS**.
- **V. Offline-First & Safe Persistence**: Zero Room DB changes; zero machine-specific `gradle.properties` changes. **PASS**.
- **VI. Code Quality & PR Governance**: Clean single-responsibility helpers (`ContinuousTimerUiResolver.kt`, `ContinuousTimerComponents.kt`), changelog entry, feature branch PR + green remote CI watch. **PASS**.
- **VII. MCP Tooling Parity**: N/A (mobile feature). **PASS**.

## Project Structure

### Documentation (this feature)

```text
specs/023-continuous-workout-timer/
├── spec.md                                     # Clarified functional specification
├── plan.md                                     # This file
├── research.md                                 # Compatibility & technical decisions
├── data-model.md                               # Domain models & state invariants
├── quickstart.md                               # File index & verification commands
├── contracts/
│   └── timer-engine-and-ui-contract.md         # Engine, resolver & notification contracts
├── checklists/
│   ├── requirements.md                         # Spec completeness checklist
│   └── ux-and-timer-engine.md                  # Domain/UX readiness checklist
└── tasks.md                                    # Ordered execution task breakdown
```

### Source Code (repository root)

```text
app/src/main/java/com/fractanomics/crosstraining/ui/
├── timer/
│   ├── WorkoutTimer.kt                         # TimerMode defaults, TRANSITION phase, QueuedTimerStage, extended snapshot
│   ├── TimerEngine.kt                          # Live overrides, queue promotion, TRANSITION phase, continueFromFinished, reset variants
│   ├── ContinuousTimerUiResolver.kt            # [NEW] Pure formatting, skip labels, reset confirmation, live-adjustment & a11y helpers
│   ├── TimerTeardownController.kt              # Keep foreground service alive during TimerPhase.TRANSITION
│   ├── TimerNotificationFormatter.kt           # Format TRANSITION notification title with Up Next mode
│   └── TimerNotificationSpec.kt                # Format TRANSITION notification spec & "Start Now" action label
├── components/
│   ├── ContinuousTimerComponents.kt            # [NEW] ActiveContinuousTimerCard, UpNextQueueCard, NextModeConfigSheet, Dialogs, SetupForm
│   ├── InSessionTimerSheet.kt                  # Parity setup/queue when IDLE, ActiveContinuousTimerCard when active
│   ├── InSessionTimerBar.kt                    # Exhaustive TRANSITION handling, tnum clock, skip affordance
│   └── SubBlockInlineTimer.kt                  # Exhaustive TRANSITION handling, tnum clock, skip affordance
└── screens/
    └── TimerScreen.kt                          # Collapse setup on non-IDLE, UpNextQueueCard, ActiveContinuousTimerCard

app/src/test/java/com/fractanomics/crosstraining/ui/timer/
└── ContinuousTimerTest.kt                      # [NEW] 8 test groups covering queue, overrides, all 6 modes, resolver, notifications & teardown
```

## Complexity Tracking

No constitution violations requiring justification.
