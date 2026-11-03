# Implementation Plan: Modal Card Auto-Scroll & Non-Blocking Accordion Actions

**Branch**: `feat/014-fix-modal-card-scroll-actions` | **Date**: 2026-11-03 | **Spec**: [spec.md](./spec.md)
**Input**: Feature specification from `/specs/014-fix-modal-card-scroll-actions/spec.md`

## Summary

Fix the unresponsive button state in `CycleEditorDialog` after adding two movement goals—and prevent similar off-screen append issues in scrollable modals—by:
1. Updating `expandGoalDraftAt` and `GoalDraftState` so expanding an existing collapsed goal card is **never blocked** by an empty open draft (auto-discarding unsaved empty drafts and restoring `lastValidTargetWeight` on previously valid goals when switching).
2. Adding frame-synchronized viewport auto-scrolling (`resolveGoalCardScrollOffset` + `Modifier.onGloballyPositioned` + `scrollState.animateScrollTo`) in `CycleEditorDialog` whenever a movement goal card is added (`+ Add Movement`), expanded (`▾`), or flagged with an inline target error (`Done ▲` / `+ Add Movement`).
3. Separating trailing action touch targets (`Arrangement.spacedBy(4.dp)`) and preserving independent button semantics on `CollapsedGoalSummaryCard`.
4. Adding automatic scroll-to-new-block behavior in `RoutineEditorDialog` (`LibraryScreen.kt`) when tapping `Add Block to Routine`.

## Technical Context

**Language/Version**: Kotlin 2.0.21 (JVM 17)
**Primary Dependencies**: Jetpack Compose (BOM `2024.10.01`), Material 3, AndroidX Lifecycle & Activity Compose
**Storage**: Room SQLite (`Cycle`, `CycleGoal`, `Exercise`, `Routine`, `RoutineBlock`) — no schema changes required
**Testing**: JUnit 4 (`testDebugUnitTest`), Compose UI / E2E Maestro flows (`run-e2e-tests.ps1`), PowerShell Pester-style script tests (`Invoke-ScriptTests.ps1`)
**Target Platform**: Android (`minSdk 26`, `compileSdk 35`, `targetSdk 35`)
**Project Type**: Mobile application (Android Jetpack Compose MVVM)
**Performance Goals**: Immediate 60fps UI responsiveness; auto-scroll animation starts within 1 frame (`withFrameNanos`) of card addition/expansion
**Constraints**: Must work cleanly on `360dp`-wide viewports up to `fontScale = 1.3f` without layout overflow or clipped controls; 100% backward compatible with existing `CycleGoal` persistence
**Scale/Scope**: `CyclesScreen.kt`, `LibraryScreen.kt`, and unit tests in `CyclesScreenTest.kt`

## Constitution Check

*GATE: Must pass before Phase 0 research. Re-check after Phase 1 design.*

- **I. MVVM & Unidirectional Data Flow (UDF)**: **PASS** — `GoalDraftState`, `expandGoalDraftAt`, `appendMovementGoalDraft`, and `resolveGoalCardScrollOffset` are pure deterministic state/layout functions; `CycleEditorDialog` and `RoutineEditorDialog` manage transient dialog scroll state cleanly without side effects on domain repositories.
- **II. Pure Deterministic Analytics & Display-Unit Consistency**: **PASS** — Target stepping and baseline calculations remain unchanged and deterministic.
- **III. Test-First & 100% CI Parity (NON-NEGOTIABLE)**: **PASS** — All new accordion transition rules, empty-draft auto-discard, target restoration, and viewport scroll offset calculations will be covered by deterministic unit tests in `CyclesScreenTest.kt`.
- **IV. Responsive Mobile UX & Accessibility**: **PASS** — Auto-scrolling ensures newly added and expanded cards are never stranded off-screen on compact mobile viewports, and `CollapsedGoalSummaryCard` trailing icon spacing (`4.dp`) + semantics prevent accidental delete taps and merged-semantics action loss.
- **V. Offline-First & Environment Isolation**: **PASS** — No database schema migrations or environment config changes required.
- **VI. Agentic Collaboration, Automated PR Review & Self-Healing Governance**: **PASS** — Follows standard PR workflow and CI checks.

## Project Structure

### Documentation (this feature)

```text
specs/014-fix-modal-card-scroll-actions/
├── plan.md              # This file (/speckit-plan command output)
├── research.md          # Phase 0 output (/speckit-plan command)
├── data-model.md        # Phase 1 output (/speckit-plan command)
├── quickstart.md        # Phase 1 output (/speckit-plan command)
├── contracts/           # Phase 1 output (/speckit-plan command)
│   └── ui-modal-scroll-and-accordion-contract.md
└── tasks.md             # Phase 2 output (/speckit-tasks command)
```

### Source Code (repository root)

```text
app/src/main/java/com/fractanomics/crosstraining/ui/screens/
├── CyclesScreen.kt      # GoalDraftState, expandGoalDraftAt, appendMovementGoalDraft, resolveGoalCardScrollOffset, CollapsedGoalSummaryCard, CycleEditorDialog
└── LibraryScreen.kt     # RoutineEditorDialog auto-scroll when adding a routine block

app/src/test/java/com/fractanomics/crosstraining/ui/screens/
└── CyclesScreenTest.kt  # Unit tests for non-blocking accordion switching, auto-discard of empty drafts, target restoration, and scroll offset calculation
```

**Structure Decision**: Single-module Android application (`app/`), modifying existing screen composables and their corresponding unit test suite.

## Complexity Tracking

No constitution violations or extra complexity required.
