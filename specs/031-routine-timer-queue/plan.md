# Implementation Plan: Routine-Aware Auto-Extrapolated Workout Timer Queue

**Branch**: `feat/031-routine-timer-queue` | **Date**: 2026-11-28 | **Spec**: [spec.md](file:///c:/Users/rogal/workspaces/ws-gym/crosstrainingapp/specs/031-routine-timer-queue/spec.md)

**Input**: Feature specification from `/specs/031-routine-timer-queue/spec.md` (grounded in [`docs/draft-requisites/routine-timer-queue.md`](file:///c:/Users/rogal/workspaces/ws-gym/crosstrainingapp/docs/draft-requisites/routine-timer-queue.md) and [`design-system/crosstraining/pages/routine-timer-queue.md`](file:///c:/Users/rogal/workspaces/ws-gym/crosstrainingapp/design-system/crosstraining/pages/routine-timer-queue.md)).

## Summary

Upgrade CrossTraining's continuous workout timer from a single-slot `queuedNextStage` into a routine-aware multi-stage queue (`queuedStages: List<QueuedTimerStage>`) driven by a pure `WorkoutTimerQueueBuilder`. From `Log Session` (either any block/exercise card's Timer icon or the Top-Bar Timer icon when blocks exist), `InSessionTimerSheet` opens anchored at the selected block `#k` with a Block Anchor Strip, a full 6-mode starting configurator (defaulting to `E3MOM 180s × {sets}`), and an editable auto-extrapolated timeline of all remaining blocks `#k+1 … #N` (always `E3MOM 180s × {sets}` preceded by `1:00` transitions), synchronized across the docked non-focus-stealing `InSessionTimerBar`, active/up-next `Log Session` card highlights, foreground `TimerService` notification, and boundary-only TalkBack announcements.

## Technical Context

**Language/Version**: Kotlin (JVM 17), Gradle Kotlin DSL
**Primary Dependencies**: Jetpack Compose + Material 3 (`androidx.material3`), Lifecycle Compose (`collectAsStateWithLifecycle`), Navigation Compose, Coroutines, Android Foreground `Service` (`MediaStyle` notification)
**Storage**: N/A (Zero Room SQLite schema migrations; zero Firestore rule/storage changes; runtime state in `TimerEngineProvider` singleton)
**Testing**: JUnit 4 + `kotlinx-coroutines-test` (`.\gradlew.bat testDebugUnitTest --no-daemon`), PowerShell Pester script tests (`.\scripts\tests\Invoke-ScriptTests.ps1`), Android Lint (`.\gradlew.bat lintDebug --no-daemon`)
**Target Platform**: Android (`minSdk = 26`, `compileSdk = 35`, `targetSdk = 35`)
**Project Type**: Native Android Mobile App (`com.fractanomics.crosstraining`)
**Performance Goals**: Zero recomposition leakage on 1Hz timer ticks (leaf `StateFlow` collection in `InSessionTimerBar` / `SubBlockInlineTimer` / `InSessionTimerSheet`), `@Immutable` state models, keyed queue rows (`key = stage.id`)
**Constraints**: `>= 48.dp` touch targets with `>= 8.dp` spacing, `fontFeatureSettings = "tnum"` on numeric clocks, vector icons only, `360dp` + `fontScale = 1.3f` layout safety, 4-theme contrast compliance (`DARK`, `LIGHT`, `DARK_HIGH_CONTRAST`, `LIGHT_HIGH_CONTRAST`), non-focus-stealing docked bar (`focusProperties { canFocus = false }`)
**Scale/Scope**: 11 Android source files (1 new pure builder + 10 targeted edits) + 2 unit test files

## Constitution Check

*GATE: Must pass before Phase 0 research. Re-check after Phase 1 design.*

- **I. MVVM & Unidirectional Data Flow (UDF)**: PASS — Queue extrapolation lives in pure `WorkoutTimerQueueBuilder`, execution state lives in `TimerEngine` exposing immutable `StateFlow<TimerSnapshot>`, and Compose components observe via `collectAsStateWithLifecycle()`.
- **II. Pure Deterministic Analytics & Consistency**: PASS — `WorkoutTimerQueueBuilder.buildFromAnchor` and `TimerEngine.calculateTotalSecondsRemaining` are pure deterministic Kotlin functions with exact integer second math.
- **III. Test-First & 100% CI Parity (NON-NEGOTIABLE)**: PASS — Dedicated unit tests cover `WorkoutTimerQueueBuilder`, `buildSessionTimerBlockEntries`, multi-stage `TimerEngine` progression/mutations, `ContinuousTimerUiResolver`, and `TimerNotificationFormatter`; full pre-PR suite (`testDebugUnitTest`, `Invoke-ScriptTests.ps1`, `lintDebug`) runs before commit/PR.
- **IV. Responsive Mobile UX & Accessibility**: PASS — Enforced via `design-system/crosstraining/MASTER.md` and `pages/routine-timer-queue.md` (`48.dp` targets, `tnum`, `360dp`/`1.3f` reflow, merged TalkBack semantics, boundary-only `LiveRegionMode.Polite`).
- **V. Offline-First & Zero Unnecessary Schema Churn**: PASS — Zero Room schema changes, zero `gradle.properties` machine-specific paths, zero Cloud Sync mutations.
- **VI. Agentic Collaboration & CI Governance**: PASS — No workflow regression; feature branch `feat/031-routine-timer-queue`, `CHANGELOG.md` updated under `## [Unreleased]`, PR opened and CI verified.
- **VII. Reliable MCP Communication**: N/A — Android UI/timer feature with no `mcp-server/` changes.

## Project Structure

### Documentation (this feature)

```text
specs/031-routine-timer-queue/
├── spec.md
├── plan.md
├── research.md
├── data-model.md
├── quickstart.md
├── checklists/
│   ├── requirements.md
│   └── timer-queue-ux-and-engine.md
└── contracts/
    └── routine-timer-queue-contract.md
```

### Source Code (repository root)

```text
app/src/main/java/com/fractanomics/crosstraining/ui/
├── timer/
│   ├── WorkoutTimer.kt                  # @Immutable models, sourceBlockKey, stage id, queuedStages list
│   ├── WorkoutTimerQueueBuilder.kt      # [NEW] SessionTimerBlockEntry, BuiltWorkoutTimerQueue, pure builder
│   ├── TimerEngine.kt                   # Multi-stage queue API, head-popping promotion, mid-transition safety
│   ├── ContinuousTimerUiResolver.kt     # Block X of Y headers, bar subtitles, TalkBack & queue labels
│   ├── TimerNotificationFormatter.kt    # Block-aware notification titles preserving blank-label format
│   └── TimerNotificationSpec.kt         # Pass workoutLabel + next block label into formatter
├── components/
│   ├── ContinuousTimerComponents.kt     # QueueTimelinePreview, ActiveTimerQueueSection, NextModeConfigSheet fix
│   ├── InSessionTimerSheet.kt           # Block Anchor Strip, starting configurator, timeline, live queue wiring
│   ├── InSessionTimerBar.kt             # 48dp focus-safe controls + block/queue position subtitle
│   └── SubBlockInlineTimer.kt           # Transition Up Next block label support
└── screens/
    ├── SessionEditor.kt                 # buildSessionTimerBlockEntries, Top-Bar + card anchoring, card sync, conflict dialog
    ├── SimplifiedBlockLogComponents.kt  # Active/Up Next border + badge pill on complex/exercise cards
    ├── CompactComplexLogComponents.kt   # Active/Up Next border + badge pill on compact complex card
    └── TimerScreen.kt                   # Wire multi-stage queue callbacks on standalone TimerScreen

app/src/test/java/com/fractanomics/crosstraining/ui/timer/
├── WorkoutTimerQueueBuilderTest.kt      # [NEW] Extrapolation, E3MOM enforcement, block entry mapping tests
└── ContinuousTimerTest.kt               # Multi-stage queue progression, mid-transition edits/removals, notifications
```

**Structure Decision**: Reuse the existing single-module Android Jetpack Compose architecture under `app/src/main/java/com/fractanomics/crosstraining/ui/{timer,components,screens}` with a pure Kotlin queue builder (`WorkoutTimerQueueBuilder.kt`) and matching JVM unit tests under `app/src/test/java/com/fractanomics/crosstraining/ui/timer/`.

## Complexity Tracking

No Constitution violations or complexity exceptions required.
