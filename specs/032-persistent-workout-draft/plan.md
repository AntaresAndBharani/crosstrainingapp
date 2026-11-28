# Implementation Plan: Persistent Active Workout Draft & Unified Live Session Dock

**Branch**: `feat/032-persistent-workout-draft` | **Date**: 2026-11-28 | **Spec**: [spec.md](file:///c:/Users/rogal/workspaces/ws-gym/crosstrainingapp/specs/032-persistent-workout-draft/spec.md)

**Input**: Feature specification from `/specs/032-persistent-workout-draft/spec.md` and UI/UX override [`design-system/crosstraining/pages/persistent-workout-draft.md`](file:///c:/Users/rogal/workspaces/ws-gym/crosstrainingapp/design-system/crosstraining/pages/persistent-workout-draft.md).

## Summary

Eliminate workout draft loss across tab switches, drawer visits, Coach/Athlete role switches, rotations, and app restarts by hoisting the active `LogSessionScreen` draft into `AppViewModel` backed by per-mode (`real` / `demo`) debounced + lifecycle-flushed disk persistence in `DataModeManager` using a pure-Kotlin JSON codec (`ActiveWorkoutDraftCodec`). Pair this with a pure `LiveSessionDockResolver` and a recomposition-isolated `LiveSessionDock` inside `AppNavigation`'s root `Scaffold.bottomBar` enforcing an at-most-one-bar rule alongside `InSessionTimerBar` and `TimerScreen`, plus `Log` navigation badges, top-bar overflow `Discard workout`, routine Replace/Append protection, Edit Session isolation, and a next-day Stale Draft recovery banner.

## Technical Context

**Language/Version**: Kotlin (JVM 17), Gradle Kotlin DSL (`.\gradlew.bat`)  
**Primary Dependencies**: Jetpack Compose + Material 3 (`androidx.material3`), Lifecycle Compose (`collectAsStateWithLifecycle`, `LifecycleEventObserver`), Navigation Compose, Coroutines  
**Storage**: `SharedPreferences` (`crosstraining-prefs` in `DataModeManager`, with in-memory fallback for pure-JVM tests) storing zero-dependency JSON snapshots per data mode (`active_workout_draft_real` / `active_workout_draft_demo`). Zero Room SQLite schema migrations (`AppDatabase` remains v10).  
**Testing**: JUnit 4 + `kotlinx-coroutines-test` (`.\gradlew.bat testDebugUnitTest --no-daemon`), PowerShell Pester script tests (`.\scripts\tests\Invoke-ScriptTests.ps1`), Android Lint (`.\gradlew.bat lintDebug --no-daemon`)  
**Target Platform**: Android (`minSdk = 26`, `compileSdk = 35`, `targetSdk = 35`)  
**Project Type**: Native Android Mobile App (`com.fractanomics.crosstraining`)  
**Performance Goals**: Zero 1Hz recomposition leakage on `AppNavigation` / `NavHost` / active tabs (leaf `StateFlow` collection in `LiveSessionDock`), debounced (500ms) disk auto-save with immediate `ON_STOP` / `onDispose` flush, `@Immutable` draft and dock models  
**Constraints**: `>= 48.dp` touch targets with `>= 8.dp` spacing, `focusProperties { canFocus = false }` on dock timer controls, `fontFeatureSettings = "tnum"` on numeric clocks/counters, vector icons only, `360dp` + `fontScale = 1.3f` layout safety, 4-theme contrast parity (`DARK`, `LIGHT`, `DARK_HIGH_CONTRAST`, `LIGHT_HIGH_CONTRAST`), zero `org.json` usage in pure-JVM tested code  
**Scale/Scope**: 4 new source files (`ui/draft/{ActiveWorkoutDraft,ActiveWorkoutDraftCodec,LiveSessionDockResolver}.kt`, `ui/components/LiveSessionDock.kt`), 6 targeted updates (`DataModeManager.kt`, `AppViewModel.kt`, `LogSessionScreen.kt`, `SessionEditor.kt`, `SimplifiedBlockLogComponents.kt`, `AppNavigation.kt`), and 3 new unit test suites (`app/src/test/.../ui/draft/`).

## Constitution Check

*GATE: Must pass before Phase 0 research. Re-check after Phase 1 design.*

- **I. MVVM & Unidirectional Data Flow (UDF)**: PASS — `AppViewModel` owns immutable `StateFlow<ActiveWorkoutDraftSnapshot?>` and `StateFlow<ActiveWorkoutSummary>` collected via `collectAsStateWithLifecycle()`; `LiveSessionDockResolver` and `ActiveWorkoutDraftCodec` are pure stateless Kotlin components.
- **II. Pure Deterministic Analytics & Consistency**: PASS — `ActiveWorkoutDraftSnapshot.toSummary(referenceDate)` and `LiveSessionDockResolver.resolve(...)` are pure deterministic functions with explicit `LocalDate` injection for testability.
- **III. Test-First & 100% CI Parity (NON-NEGOTIABLE)**: PASS — Dedicated JVM unit tests (`ActiveWorkoutDraftCodecTest`, `ActiveWorkoutDraftPersistenceTest`, `LiveSessionDockResolverTest`) cover codec round-trips, persistence/mode isolation, and the complete dock arbitration matrix; full pre-PR suite (`testDebugUnitTest`, `Invoke-ScriptTests.ps1`, `lintDebug`, `assembleDebug`) runs before commit/PR.
- **IV. Responsive Mobile UX & Accessibility**: PASS — Enforced via `design-system/crosstraining/MASTER.md` and `pages/persistent-workout-draft.md` (`48.dp` targets, `tnum`, `360dp`/`1.3f` reflow, merged TalkBack semantics, no 1Hz liveRegion spam, 4-theme parity).
- **V. Offline-First & Zero Unnecessary Schema Churn**: PASS — Zero Room DB schema migrations, zero Firestore rule/collection changes, zero machine-specific paths in `gradle.properties`.
- **VI. Agentic Collaboration & CI Governance**: PASS — Implemented on `feat/032-persistent-workout-draft` branched from `origin/main`, `CHANGELOG.md` updated under `## [Unreleased]`, PR opened and remote CI verified 100% green.
- **VII. Reliable MCP Communication**: N/A — Android client-side persistence & UX feature with zero `mcp-server/` changes.

## Project Structure

### Documentation (this feature)

```text
specs/032-persistent-workout-draft/
├── spec.md
├── plan.md
├── research.md
├── data-model.md
├── quickstart.md
├── tasks.md
├── checklists/
│   ├── requirements.md
│   └── draft-persistence-and-dock-ux.md
└── contracts/
    └── persistent-workout-draft-contract.md
```

### Source Code (repository root)

```text
app/src/main/java/com/fractanomics/crosstraining/
├── data/
│   └── DataModeManager.kt                               # Per-mode active draft storage helpers
├── ui/
│   ├── AppViewModel.kt                                  # Active draft StateFlows, debounce, flush, clear, Demo isolation
│   ├── draft/
│   │   ├── ActiveWorkoutDraft.kt                        # [NEW] @Immutable draft & summary models + stale/active rules
│   │   ├── ActiveWorkoutDraftCodec.kt                   # [NEW] Pure-Kotlin JSON encoder/decoder (zero org.json)
│   │   └── LiveSessionDockResolver.kt                   # [NEW] Pure single-dock arbitration + timer reset resolver
│   ├── components/
│   │   └── LiveSessionDock.kt                           # [NEW] Route-aware docked bar with 1Hz leaf timer isolation
│   ├── navigation/
│   │   └── AppNavigation.kt                             # Root dock host, ON_STOP flush, nav/drawer badges, Edit dirty guard
│   └── screens/
│       ├── LogSessionScreen.kt                          # Wire activeWorkoutDraft + revision + callbacks
│       ├── SessionEditor.kt                             # Snapshot capture/rehydration, overflow Discard, Replace/Append, Stale banner, inner bar suppression
│       └── SimplifiedBlockLogComponents.kt              # Hydration/snapshot helpers for LoadedAvailableBlockState

app/src/test/java/com/fractanomics/crosstraining/ui/draft/
├── ActiveWorkoutDraftCodecTest.kt                       # [NEW] Round-trip, corrupt input, summary & stale date tests
├── ActiveWorkoutDraftPersistenceTest.kt                 # [NEW] ViewModel debounce, flush, restart & Demo isolation tests
└── LiveSessionDockResolverTest.kt                       # [NEW] Full route × draft × timer matrix & reset rule tests
```

**Structure Decision**: Follow the existing single-module MVVM + Jetpack Compose package layout, introducing `ui/draft/` alongside `ui/timer/` for pure domain models/codecs/resolvers and keeping all Compose views in `ui/components/`, `ui/navigation/`, and `ui/screens/`.

## Complexity Tracking

No Constitution violations or complexity exceptions required.
