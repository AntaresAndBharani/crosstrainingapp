---

description: "Task list for Routine-Aware Auto-Extrapolated Workout Timer Queue (SPEC-031)"
---

# Tasks: Routine-Aware Auto-Extrapolated Workout Timer Queue

**Input**: Design documents from `/specs/031-routine-timer-queue/`
**Prerequisites**: `plan.md`, `spec.md`, `research.md`, `data-model.md`, `contracts/routine-timer-queue-contract.md`

## Phase 1: Setup (Shared Infrastructure)

- [X] T001 Verify feature branch `feat/031-routine-timer-queue`, `.specify/feature.json`, and design system reference `design-system/crosstraining/pages/routine-timer-queue.md`

---

## Phase 2: Foundational (Multi-Stage Timer Models & Engine Core)

**Purpose**: Upgrade `WorkoutTimer` domain models and `TimerEngine` so all user stories build on immutable multi-stage queues and pure extrapolation.

- [X] T002 [P] Upgrade `WorkoutTimerConfig` (`sourceBlockKey`), `QueuedTimerStage` (`id`), and `TimerSnapshot` (`@Immutable`, `queuedStages`, `queuedNextStage`, `currentStageIndex`, `totalPlannedStages`) in `app/src/main/java/com/fractanomics/crosstraining/ui/timer/WorkoutTimer.kt`
- [X] T003 [P] Create pure `SessionTimerBlockEntry`, `BuiltWorkoutTimerQueue`, and `WorkoutTimerQueueBuilder` (`E3MOM 180s × sets` + `60s` transition) in `app/src/main/java/com/fractanomics/crosstraining/ui/timer/WorkoutTimerQueueBuilder.kt`
- [X] T004 Upgrade `TimerEngine` with `configureQueue`, `replaceTimerQueue`, `setQueuedStages`, `updateQueuedStage`, `removeQueuedStage`, `appendQueuedStage`, head-popping promotion, mid-transition head mutation safety, and full-queue `calculateTotalSecondsRemaining` in `app/src/main/java/com/fractanomics/crosstraining/ui/timer/TimerEngine.kt`
- [X] T005 [P] Update `ContinuousTimerTest.kt` existing `TimerSnapshot(..., queuedNextStage = ...)` constructor sites to `queuedStages = listOf(...)` in `app/src/test/java/com/fractanomics/crosstraining/ui/timer/ContinuousTimerTest.kt`

**Checkpoint**: Foundational models, pure queue builder, and multi-stage engine compile cleanly with existing tests intact.

---

## Phase 3: User Story 1 - Extrapolate and Run Multi-Block Timer Queue From Any Block (Priority: P1)

**Goal**: Tapping any block `#k` builds an editable Stage 1 configurator plus `E3MOM 180s × sets` + `1:00` transitions for `#k+1 … #N`, running continuously end-to-end.

**Independent Test**: Unit tests in `WorkoutTimerQueueBuilderTest.kt` and `ContinuousTimerTest.kt` verify anchor extrapolation (`E3MOM 180s × sets` ignoring notes), `1:00` transitions, prep-only-on-Stage-1, and 3+/4+ block progression; UI launches anchored queue from any block card.

- [X] T006 [P] [US1] Create `WorkoutTimerQueueBuilderTest.kt` covering anchor indices (`0`, middle, last), strict `E3MOM 180s × sets` ignoring block notes/tokens, `60s` transitions, prep-only-on-Stage-1, set clamping, and `buildSessionTimerBlockEntries` in `app/src/test/java/com/fractanomics/crosstraining/ui/timer/WorkoutTimerQueueBuilderTest.kt`
- [X] T007 [P] [US1] Add multi-stage progression, early `TIME_CAP` `skipRound()`, and full-queue `totalSecondsRemaining` unit tests in `app/src/test/java/com/fractanomics/crosstraining/ui/timer/ContinuousTimerTest.kt`
- [X] T008 [US1] Implement `buildSessionTimerBlockEntries` and wire simplified complex/exercise and legacy block/sub-block `onLaunchTimer` callbacks in `app/src/main/java/com/fractanomics/crosstraining/ui/screens/SessionEditor.kt`
- [X] T009 [US1] Add `QueueTimelinePreview` (with Ochre `1:00 Transition` connectors, `tnum`, `48.dp` targets) in `app/src/main/java/com/fractanomics/crosstraining/ui/components/ContinuousTimerComponents.kt`

---

## Phase 4: User Story 2 - Anchor From Top-Bar Timer or Re-Anchor Before Starting (Priority: P1)

**Goal**: Top-Bar Timer in `Log Session` opens `InSessionTimerSheet` anchored at Block 1 (when blocks exist) with a horizontal Block Anchor Strip for re-anchoring before Start, falling back to `TimerScreen` when 0 blocks exist.

**Independent Test**: Tapping Top-Bar Timer with `>= 1` block opens `InSessionTimerSheet` anchored at Block 1; tapping Block 2 pill re-anchors queue from Block 2; tapping Top-Bar Timer with `0` blocks routes to standalone `TimerScreen`.

- [X] T010 [US2] Upgrade `InSessionTimerSheet` with `sessionBlocks`, `initialAnchorIndex`, horizontal Block Anchor Strip (`LazyRow`, `48.dp` chips), 6-mode starting configurator preserving anchor sets, timeline preview, and `START QUEUE` launch in `app/src/main/java/com/fractanomics/crosstraining/ui/components/InSessionTimerSheet.kt`
- [X] T011 [US2] Route `Log Session` Top-Bar Timer icon to `InSessionTimerSheet` anchored at Block 1 when `sessionTimerEntries.isNotEmpty()` and to `onOpenTimer` when empty in `app/src/main/java/com/fractanomics/crosstraining/ui/screens/SessionEditor.kt`

---

## Phase 5: User Story 3 - Customize, Edit, or Remove Any Queued Block Pre-Start and Live (Priority: P2)

**Goal**: Athletes can edit any queued block's mode/duration/rounds/transition, remove any block from the queue, or append additional stages both before starting and mid-workout.

**Independent Test**: Unit tests verify `updateQueuedStage`, `removeQueuedStage` (including head removal mid-`TRANSITION`), and `appendQueuedStage`; UI allows live editing/removal from both pre-start timeline and active `Remaining Queue` drawer.

- [X] T012 [P] [US3] Add unit tests for `updateQueuedStage`, mid-transition `removeQueuedStage` (remaining tail vs finishing empty), and `appendQueuedStage` in `app/src/test/java/com/fractanomics/crosstraining/ui/timer/ContinuousTimerTest.kt`
- [X] T013 [US3] Update `NextModeConfigSheet` to preserve `existingStage.id`, `workoutLabel`, and `sourceBlockKey` across preset/mode switches and add expandable `ActiveTimerQueueSection` (`rememberSaveable`, `key = stage.id`) in `app/src/main/java/com/fractanomics/crosstraining/ui/components/ContinuousTimerComponents.kt`
- [X] T014 [US3] Wire pre-start and live queue edit/remove/append sheet flows across `InSessionTimerSheet.kt` and `TimerScreen.kt` in `app/src/main/java/com/fractanomics/crosstraining/ui/components/InSessionTimerSheet.kt` and `app/src/main/java/com/fractanomics/crosstraining/ui/screens/TimerScreen.kt`

---

## Phase 6: User Story 4 - In-Session Context Sync Without Stealing Keyboard Focus (Priority: P2)

**Goal**: Docked timer bar, active/up-next cards in `Log Session`, foreground notification, and boundary TalkBack announcements display block context while preserving keyboard focus.

**Independent Test**: Unit tests verify `ContinuousTimerUiResolver` block headers/bar subtitles/boundary TalkBack and `TimerNotificationFormatter` block titles (with blank-label regression parity); UI verifies `focusProperties { canFocus = false }`, `48.dp` controls, and card highlight pills.

- [X] T015 [P] [US4] Update `ContinuousTimerUiResolver` (`formatStagePositionHeader`, `formatInSessionBarSubtitle`, `formatQueueRowAccessibilityLabel`, block-aware `resolvePoliteBoundaryAnnouncement`) in `app/src/main/java/com/fractanomics/crosstraining/ui/timer/ContinuousTimerUiResolver.kt`
- [X] T016 [P] [US4] Update `TimerNotificationFormatter` and `TimerNotificationSpec` to include non-blank `workoutLabel` / Up Next block title while preserving legacy blank-label formats in `app/src/main/java/com/fractanomics/crosstraining/ui/timer/TimerNotificationFormatter.kt` and `app/src/main/java/com/fractanomics/crosstraining/ui/timer/TimerNotificationSpec.kt`
- [X] T017 [P] [US4] Add unit tests for `ContinuousTimerUiResolver` block headers/bar subtitles/TalkBack and `TimerNotificationFormatter` block titles in `app/src/test/java/com/fractanomics/crosstraining/ui/timer/ContinuousTimerTest.kt` and `app/src/test/java/com/fractanomics/crosstraining/ui/timer/TimerServiceTest.kt`
- [X] T018 [US4] Update `InSessionTimerBar` (`48.dp` focus-safe controls, queue position subtitle) and `SubBlockInlineTimer` (Up Next block label) in `app/src/main/java/com/fractanomics/crosstraining/ui/components/InSessionTimerBar.kt` and `app/src/main/java/com/fractanomics/crosstraining/ui/components/SubBlockInlineTimer.kt`
- [X] T019 [US4] Add `isTimerActive` / `isTimerUpNext` border accents and status pills (`Timer Active` / `Up Next`) in `SimplifiedBlockLogComponents.kt`, `CompactComplexLogComponents.kt`, and wire from `SessionEditor.kt`

---

## Phase 7: User Story 5 - Safe Active-Timer Conflict Resolution & Reset Confirmation (Priority: P3)

**Goal**: Tapping active block opens sheet directly; tapping a different block prompts `Rebuild Queue` vs `Cancel` using atomic `replaceTimerQueue`; Reset preserves `Restart Current Block (Keep Queue)` vs `Stop & Clear Full Queue`.

**Independent Test**: Unit tests verify `replaceTimerQueue` and `restartCurrentStage` preserving multi-stage queue; `SessionEditor.kt` conflict dialog rebuilds queue atomically and `ResetTimerConfirmDialog` shows queue-aware reset options.

- [X] T020 [P] [US5] Add unit tests for `replaceTimerQueue` (atomic without `IDLE`) and `restartCurrentStage` preserving multi-stage `queuedStages` in `app/src/test/java/com/fractanomics/crosstraining/ui/timer/ContinuousTimerTest.kt`
- [X] T021 [US5] Update `SessionEditor.kt` conflict dialog (`Rebuild Queue` from new anchor) and `ContinuousTimerComponents.kt` `ResetTimerConfirmDialog` queue-aware copy in `app/src/main/java/com/fractanomics/crosstraining/ui/screens/SessionEditor.kt` and `app/src/main/java/com/fractanomics/crosstraining/ui/components/ContinuousTimerComponents.kt`

---

## Phase 8: Polish, Verification & Convergence

- [X] T022 Run full pre-PR verification (`.\gradlew.bat testDebugUnitTest --no-daemon`, `.\scripts\tests\Invoke-ScriptTests.ps1`, `.\gradlew.bat lintDebug --no-daemon`, `.\gradlew.bat assembleDebug --no-daemon` + sync `local_test\latest.apk`)
- [X] T023 Update `CHANGELOG.md` under `## [Unreleased]` with SPEC-031 summary and run `speckit-converge` final audit
