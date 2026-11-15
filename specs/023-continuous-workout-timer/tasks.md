---

description: "Task breakdown for SPEC-023 Continuous Workout Timer & Live Stage Queue"
---

# Tasks: Continuous Workout Timer & Live Stage Queue

**Input**: Design documents from `/specs/023-continuous-workout-timer/`
**Prerequisites**: [plan.md](file:///c:/Users/rogal/workspaces/ws-gym/crosstrainingapp/specs/023-continuous-workout-timer/plan.md), [spec.md](file:///c:/Users/rogal/workspaces/ws-gym/crosstrainingapp/specs/023-continuous-workout-timer/spec.md), [data-model.md](file:///c:/Users/rogal/workspaces/ws-gym/crosstrainingapp/specs/023-continuous-workout-timer/data-model.md), [contracts/timer-engine-and-ui-contract.md](file:///c:/Users/rogal/workspaces/ws-gym/crosstrainingapp/specs/023-continuous-workout-timer/contracts/timer-engine-and-ui-contract.md)

## Format: `[ID] [P?] [Story] Description`

- **[P]**: Can run in parallel (different files, no dependencies)
- **[Story]**: Which user story this task belongs to (`US1`, `US2`, `US3`)
- Include exact file paths in descriptions

---

## Phase 1: Setup & Specification Baseline

- [x] T001 Verify feature branch `feat/023-continuous-workout-timer` and update `.specify/feature.json` pointing to `specs/023-continuous-workout-timer`
- [x] T002 Create specification, research, data-model, contract, and readiness checklist artifacts in `specs/023-continuous-workout-timer/`

---

## Phase 2: Foundational Domain Models & Engine Core

**Purpose**: Core `WorkoutTimer.kt` models and shared `TimerEngine.kt` state machine changes required by all stories

- [x] T003 Update `TimerMode` (`defaultRounds`, `liveStepSeconds`), add `TimerPhase.TRANSITION`, extend `WorkoutTimerConfig` (`targetDurationSeconds`, `baseWorkDurationSeconds`, `baseRestDurationSeconds`, `singleRoundTotalSeconds`, `stageTotalSeconds`), add `QueuedTimerStage`, and extend `TimerSnapshot` in `app/src/main/java/com/fractanomics/crosstraining/ui/timer/WorkoutTimer.kt`
- [x] T004 Implement companion `calculateTotalSecondsRemaining` and update `TimerEngine` configuration (`configure` 1-arg compat + optional `queuedNext`), `reset`, and stage promotion helpers in `app/src/main/java/com/fractanomics/crosstraining/ui/timer/TimerEngine.kt`

---

## Phase 3: User Story 1 - Mode Chaining & Dedicated Transition Phase (Priority: P1)

**Goal**: Enable `Current + 1 Next Slot` pre-plan and live mode queuing, amber `TRANSITION` countdown with `0s` immediate bypass, `[ ⏭ Start Now ]`, `continueFromFinished()`, and foreground notification continuity.

**Independent Test**: Verified by JVM unit tests running queued `EMOM -> TRANSITION -> AMRAP`, `0s` bypass, mid-transition edits, `skipRound()` during `TRANSITION`, `continueFromFinished()`, `TimerTeardownController`, and `TimerNotificationFormatter`/`Spec`.

- [x] T005 [US1] Implement `setQueuedNextStage`, `updateQueuedNextConfig`, `adjustTransitionRemaining`, `setTransitionRemaining`, `continueFromFinished`, `restartCurrentStage`, `stopAndClearAll`, `TRANSITION` loop cues, and stage promotion inside `advanceToNextPhaseOrRound` / `skipRound` in `app/src/main/java/com/fractanomics/crosstraining/ui/timer/TimerEngine.kt`
- [x] T006 [P] [US1] Update `TimerTeardownController.onSnapshotUpdated` to return `false` on `TimerPhase.TRANSITION` in `app/src/main/java/com/fractanomics/crosstraining/ui/timer/TimerTeardownController.kt`
- [x] T007 [P] [US1] Update `TimerNotificationFormatter.kt` to format `TimerPhase.TRANSITION` title with `"Transition - Up Next: <NextMode>"` while keeping existing phase formats intact in `app/src/main/java/com/fractanomics/crosstraining/ui/timer/TimerNotificationFormatter.kt`
- [x] T008 [P] [US1] Update `TimerNotificationSpec.kt` to use `"Transition - Up Next: ..."` and `"Start Now"` action title for `TimerPhase.TRANSITION` in `app/src/main/java/com/fractanomics/crosstraining/ui/timer/TimerNotificationSpec.kt`

---

## Phase 4: User Story 2 - Unified Rounds & Single-Round Live Modification (Priority: P1)

**Goal**: Enable unified multi-round setup/execution across all 6 modes (`EMOM`, `AMRAP`, `DEATH_BY`, `TIME_CAP`, `TABATA`, `REST`), live `Total Rounds` steppers, and single-round `Next Round` overrides (`±step` + tap-to-type).

**Independent Test**: Verified by pure JVM tests checking multi-round advancement in all 6 modes, `adjustNextRoundDuration` / `adjustNextRoundRestDuration` consumption on entering `currentRound + 1`, `currentRound + 2` reversion, `totalRounds` lower bound + override purge, and `0s` Tabata rest skip.

- [x] T009 [US2] Implement `adjustTotalRounds`, `setTotalRounds`, `adjustNextRoundDuration`, `setNextRoundDuration`, `adjustNextRoundRestDuration`, `setNextRoundRestDuration`, `clearNextRoundOverrides`, multi-round `AMRAP`/`TIME_CAP`/`REST` round advancement, single-round override consumption, and safe `previousRound()` handling in `app/src/main/java/com/fractanomics/crosstraining/ui/timer/TimerEngine.kt`
- [x] T010 [P] [US2] Create pure resolver `ContinuousTimerUiResolver.kt` with clock formatting (including active `TIME_CAP` count-up), live-adjustment availability state, skip labels, reset-confirmation logic, and polite boundary announcement helpers in `app/src/main/java/com/fractanomics/crosstraining/ui/timer/ContinuousTimerUiResolver.kt`

---

## Phase 5: User Story 3 - Active Timer Layout, Anti-Wrap Transport Controls & Cross-Surface Parity (Priority: P1)

**Goal**: Build shared responsive Compose timer components (`ActiveContinuousTimerCard`, `UpNextQueueCard`, `NextModeConfigSheet`, `DurationTapToTypeDialog`, `ResetTimerConfirmDialog`, `TimerModeSetupForm`) and wire full parity across `TimerScreen`, `InSessionTimerSheet`, `InSessionTimerBar`, and `SubBlockInlineTimer`.

**Independent Test**: Verified via JVM compilation/lint/resolver checks, anti-wrap `52.dp` transport pill constraints (`maxLines = 1`, `softWrap = false`, responsive icon hide at `fontScale >= 1.25f`), and `assembleDebug` build across all 4 UI surfaces.

- [x] T011 [P] [US3] Create `ContinuousTimerComponents.kt` containing amber transition color tokens, `TimerModeSetupForm` (all 6 modes + `Total Rounds`), `UpNextQueueCard`, `NextModeConfigSheet` (`imePadding()` + `AppNumericTextField` + mode/preset picker), `DurationTapToTypeDialog`, `ResetTimerConfirmDialog`, and `ActiveContinuousTimerCard` (`tnum`, `>= 48.dp` targets, non-wrapping `52.dp` transport pills, polite live-region announcements) in `app/src/main/java/com/fractanomics/crosstraining/ui/components/ContinuousTimerComponents.kt`
- [x] T012 [US3] Refactor `TimerScreen.kt` to hide mode setup when `phase != IDLE`, show pre-plan `UpNextQueueCard` when `IDLE`, and render `ActiveContinuousTimerCard` with `NextModeConfigSheet` & `ResetTimerConfirmDialog` when active/finished in `app/src/main/java/com/fractanomics/crosstraining/ui/screens/TimerScreen.kt`
- [x] T013 [US3] Refactor `InSessionTimerSheet.kt` for full 6-mode setup + `UpNextQueueCard` on `IDLE` and `ActiveContinuousTimerCard` parity when running/transitioning/finished in `app/src/main/java/com/fractanomics/crosstraining/ui/components/InSessionTimerSheet.kt`
- [x] T014 [P] [US3] Update `InSessionTimerBar.kt` to handle `TimerPhase.TRANSITION`, apply `tnum`, and enable Skip when `queuedNextStage != null` or `phase in {PREP, TRANSITION}` in `app/src/main/java/com/fractanomics/crosstraining/ui/components/InSessionTimerBar.kt`
- [x] T015 [P] [US3] Update `SubBlockInlineTimer.kt` to handle `TimerPhase.TRANSITION`, apply `tnum`, and enable Skip when `queuedNextStage != null` or `phase in {PREP, TRANSITION}` in `app/src/main/java/com/fractanomics/crosstraining/ui/components/SubBlockInlineTimer.kt`

---

## Phase 6: Automated Verification, Convergence & PR Delivery

- [x] T016 [US1][US2][US3] Write comprehensive pure JVM test suite `ContinuousTimerTest.kt` covering all 8 functional, override, 6-mode multi-round, transition, UI resolver, teardown, and notification test groups in `app/src/test/java/com/fractanomics/crosstraining/ui/timer/ContinuousTimerTest.kt`
- [x] T017 Run full pre-PR verification (`./gradlew.bat testDebugUnitTest lintDebug assembleDebug --no-daemon` and `./scripts/tests/Invoke-ScriptTests.ps1`), fix any issues, and sync APK to `local_test/latest.apk`
- [x] T018 Update `CHANGELOG.md` under `## [Unreleased]`, push feature branch `feat/023-continuous-workout-timer`, open PR, and monitor remote GitHub Actions CI checks until 100% passing

---

## Dependencies & Execution Order

- **Foundational (Phase 2: T003–T004)** blocks all user stories.
- **US1 Engine (T005)** & **US2 Engine (T009)** sequentially build `TimerEngine.kt`; **T006**, **T007**, **T008**, **T010** run in parallel once T003–T004 compile.
- **US3 UI Components (T011)** runs after T003–T010; **T012**, **T013**, **T014**, **T015** integrate after T011.
- **Verification (T016–T018)** runs after all source tasks complete.
