---

description: "Task list for Persistent Active Workout Draft & Unified Live Session Dock (SPEC-032)"
---

# Tasks: Persistent Active Workout Draft & Unified Live Session Dock

**Input**: Design documents from `/specs/032-persistent-workout-draft/`  
**Prerequisites**: `plan.md`, `spec.md`, `research.md`, `data-model.md`, `contracts/persistent-workout-draft-contract.md`, `quickstart.md`

## Phase 1: Setup (Shared Infrastructure)

- [X] T001 Verify feature branch `feat/032-persistent-workout-draft`, `.specify/feature.json`, and design system override `design-system/crosstraining/pages/persistent-workout-draft.md`

---

## Phase 2: Foundational (Immutable Draft Models, Pure JSON Codec & Per-Mode Storage)

**Purpose**: Establish pure-Kotlin draft snapshot models (`@Immutable`), zero-dependency JSON serialization, and per-mode disk storage primitives before any UI or ViewModel wiring.

- [X] T002 [P] Create `@Immutable` `ActiveWorkoutDraftSnapshot`, nested block/exercise/legacy snapshots, `isMeaningfullyActive()`, and pure `toSummary(referenceDate)` in `app/src/main/java/com/fractanomics/crosstraining/ui/draft/ActiveWorkoutDraft.kt`
- [X] T003 [P] Implement pure-Kotlin `ActiveWorkoutDraftCodec` (`encode` / `decode` with string escaping, `schemaVersion` guard, and corrupt-payload `null` safety, zero `org.json`) in `app/src/main/java/com/fractanomics/crosstraining/ui/draft/ActiveWorkoutDraftCodec.kt`
- [X] T004 [P] Add per-mode disk storage primitives (`readActiveWorkoutDraftJson`, `writeActiveWorkoutDraftJson`, `clearActiveWorkoutDraftJson` with in-memory fallback when `prefs == null`) in `app/src/main/java/com/fractanomics/crosstraining/data/DataModeManager.kt`

**Checkpoint**: Foundational models, codec, and storage compile cleanly with zero schema changes.

---

## Phase 3: User Story 1 - Lossless Workout Draft Preservation Across Navigation & Restarts (Priority: P1) 🎯 MVP

**Goal**: Hoist the active Log workout draft into `AppViewModel` with debounced + lifecycle-flushed disk persistence and lossless capture/rehydration in `LogSessionScreen` / `SessionEditorBody` / `SimplifiedBlockLogComponents`.

**Independent Test**: Unit tests verify `ActiveWorkoutDraftCodec` round-trips, `ActiveWorkoutDraftPersistenceTest` verifies debounce/flush/restart restoration, and `LogSessionScreen` restores exact inputs and scroll offset after tab switches and restarts.

- [X] T005 [P] [US1] Create `ActiveWorkoutDraftCodecTest` covering lossless round-trip (routine sync metadata, compact complexes, skipped rounds, mixed weights, PR overrides, metrics, legacy blocks, scroll offset, `uiKey`), corrupt JSON recovery, `isMeaningfullyActive()`, and `toSummary()` in `app/src/test/java/com/fractanomics/crosstraining/ui/draft/ActiveWorkoutDraftCodecTest.kt`
- [X] T006 [US1] Add `activeWorkoutDraft`, `activeWorkoutSummary`, `activeWorkoutDraftRevision`, `pendingOpenInSessionTimerSheet`, `isHistoryEditorDirty`, debounced `updateActiveWorkoutDraft`, `flushActiveWorkoutDraftNow`, `clearActiveWorkoutDraft`, and `updateActiveDraftDateToToday` in `app/src/main/java/com/fractanomics/crosstraining/ui/AppViewModel.kt`
- [X] T007 [P] [US1] Add `ActiveWorkoutDraftPersistenceTest` covering debounce, synchronous flush, simulated cold restart, blank-draft suppression, clear/reset, and date update in `app/src/test/java/com/fractanomics/crosstraining/ui/draft/ActiveWorkoutDraftPersistenceTest.kt`
- [X] T008 [US1] Add lossless snapshot capture/rehydration helpers (`toActiveLoadedBlockSnapshot`, `restoreLoadedAvailableBlockState`) preserving `uiKey`, `sourceRoutineItem`, metrics, skips, and PR overrides in `app/src/main/java/com/fractanomics/crosstraining/ui/screens/SimplifiedBlockLogComponents.kt`
- [X] T009 [US1] Update `SessionEditorBody` (`isActiveLogTab`, `persistedDraft`, `onDraftChanged`, `onFlushDraft`, scroll restore, continuous `snapshotFlow`, `DisposableEffect` flush with `skipFlushOnDispose`) and wire `LogSessionScreen` in `app/src/main/java/com/fractanomics/crosstraining/ui/screens/SessionEditor.kt` and `app/src/main/java/com/fractanomics/crosstraining/ui/screens/LogSessionScreen.kt`
- [X] T010 [US1] Register `LifecycleEventObserver` (`ON_STOP` immediate draft flush) in `app/src/main/java/com/fractanomics/crosstraining/ui/navigation/AppNavigation.kt`

**Checkpoint**: User Story 1 is complete — drafts survive tab switches, drawer visits, role switches, rotations, and app restarts.

---

## Phase 4: User Story 2 - Unified Live Session Dock & Navigation Awareness (Priority: P1)

**Goal**: Build the deterministic Single-Dock Arbitration engine and recomposition-isolated `LiveSessionDock` above `PrimaryNavigationBar` plus `Log` nav badge and Drawer `"Active"` pill with zero double-stacked bars.

**Independent Test**: `LiveSessionDockResolverTest` verifies every `(route × draft × timer)` combination; `LiveSessionDock` renders at most one bar above bottom nav with in-place Play/Pause/Dismiss and 1-tap Resume.

- [X] T011 [P] [US2] Create pure `LiveSessionDockResolver` (`LiveDockMode`, `LiveDockUiState`, route × draft × timer arbitration, Edit-Session inner bar suppression, atomic TalkBack descriptions, and `shouldStopTimerOnDraftReset`) in `app/src/main/java/com/fractanomics/crosstraining/ui/draft/LiveSessionDockResolver.kt`
- [X] T012 [P] [US2] Create `LiveSessionDockResolverTest` exhaustively testing every cell of the route × draft × timer arbitration matrix, `shouldStopTimerOnDraftReset` rules, and accessibility label formatting in `app/src/test/java/com/fractanomics/crosstraining/ui/draft/LiveSessionDockResolverTest.kt`
- [X] T013 [US2] Create recomposition-isolated `LiveSessionDock` composable (`48.dp` `focusProperties { canFocus = false }` controls, `tnum`, vector icons, `360dp`/`1.3f` layout safety, 4-theme M3 tokens, leaf timer snapshot collection) in `app/src/main/java/com/fractanomics/crosstraining/ui/components/LiveSessionDock.kt`
- [X] T014 [US2] Host `LiveSessionDock` in root `Scaffold.bottomBar`, add `Log` `BadgedBox` dot & contextual description in `PrimaryNavigationBar`, add `"Active"` pill on `DrawerItem.LOG` in `AppDrawerContent`, and wire low-frequency timer dock metadata in `app/src/main/java/com/fractanomics/crosstraining/ui/navigation/AppNavigation.kt`
- [X] T015 [US2] Restrict inner `InSessionTimerBar` in `SessionEditorBody` to `isActiveLogTab && isTimerActive` and handle `pendingOpenInSessionTimerSheet` consumption in `app/src/main/java/com/fractanomics/crosstraining/ui/screens/SessionEditor.kt`

---

## Phase 5: User Story 3 - Safe Lifecycle Controls: Overflow Discard, Save Completion & Stale Draft Recovery (Priority: P2)

**Goal**: Add `⋮ -> Discard workout` with confirmation & linked-timer cleanup, Save Session draft/timer completion cleanup, and next-day Stale Draft recovery banner (`Use Today` | `Discard`).

- [X] T016 [US3] Implement `Log` TopAppBar `⋮` overflow menu (`Discard workout` visible only when `isActiveLogTab && isCurrentDraftActive`), confirmation `AlertDialog` stopping block-linked timers via `LiveSessionDockResolver.shouldStopTimerOnDraftReset` while preserving standalone timers, and Save Session draft/timer cleanup in `app/src/main/java/com/fractanomics/crosstraining/ui/screens/SessionEditor.kt`
- [X] T017 [US3] Add non-blocking Stale Draft recovery banner (`sessionDate < LocalDate.now()`) with `Icons.Filled.History`, `Use Today`, and `Discard` actions in `app/src/main/java/com/fractanomics/crosstraining/ui/screens/SessionEditor.kt`

---

## Phase 6: User Story 4 - Cross-Screen Isolation & Routine Overwrite Protection (Priority: P2)

**Goal**: Protect Real-Data drafts across Demo Mode toggles, isolate `History -> Edit/Copy Session` (with dirty-edit dock confirmation), append cleanly from `Library -> Log Complex`, and guard `Daily Routine` loads with `Replace current / Append blocks / Cancel`.

- [X] T018 [US4] Update `AppViewModel.setDemoMode` to flush current mode draft before switching and rehydrate target mode draft + increment revision, and verify isolation in `ActiveWorkoutDraftPersistenceTest` in `app/src/main/java/com/fractanomics/crosstraining/ui/AppViewModel.kt` and `app/src/test/java/com/fractanomics/crosstraining/ui/draft/ActiveWorkoutDraftPersistenceTest.kt`
- [X] T019 [US4] Implement `Replace current / Append blocks / Cancel` routine conflict dialog (`appendLoadedDailyRoutine` + `pendingRoutineIdToLog` + `LoadDailyRoutineSheet`) and `Library -> Log Complex` append snackbar feedback in `app/src/main/java/com/fractanomics/crosstraining/ui/screens/SessionEditor.kt`
- [X] T020 [US4] Wire `isHistoryEditorDirty` reporting in `SessionEditorBody` (`!isActiveLogTab && isEditMode`) and unsaved-edits confirmation dialog when tapping `LiveSessionDock` from `sessionEditor/...` in `app/src/main/java/com/fractanomics/crosstraining/ui/screens/SessionEditor.kt` and `app/src/main/java/com/fractanomics/crosstraining/ui/navigation/AppNavigation.kt`

---

## Phase 7: User Story 5 & Polish - Gym Ergonomics, Vector Icon Hygiene, Convergence & Release Verification (Priority: P3)

**Goal**: Replace literal `"📋"` emoji in the routine loader button with a Material vector icon, verify `360dp` / `fontScale >= 1.3f` / 4-theme parity, update `CHANGELOG.md`, and pass 100% of unit tests, script tests, lint, and debug APK build.

- [X] T021 [P] [US5] Replace `"📋"` emoji on the Daily Routine loader button in `SessionEditor.kt` with a Material vector icon (`Icons.AutoMirrored.Filled.ListAlt`) and verify `360dp` / `fontScale >= 1.3f` layout reflow across `LiveSessionDock` and Stale Draft banner in `app/src/main/java/com/fractanomics/crosstraining/ui/screens/SessionEditor.kt` and `app/src/main/java/com/fractanomics/crosstraining/ui/components/LiveSessionDock.kt`
- [X] T022 Update `CHANGELOG.md` under `## [Unreleased]` with a concise Keep-a-Changelog summary of `SPEC-032` in `CHANGELOG.md`
- [X] T023 Run full verification suite (`.\gradlew.bat testDebugUnitTest --no-daemon`, `.\scripts\tests\Invoke-ScriptTests.ps1`, `.\gradlew.bat lintDebug --no-daemon`, `.\gradlew.bat assembleDebug --no-daemon`, and sync `local_test\latest.apk`)

---

## Dependencies & Execution Order

- **Phase 1 -> Phase 2**: Must complete T001–T004 before starting User Stories.
- **Phase 3 (US1)**: Depends on Phase 2; unlocks persistent drafts across tabs/restarts.
- **Phase 4 (US2)**: Depends on `ActiveWorkoutSummary` from Phase 2 & `AppViewModel` from Phase 3.
- **Phase 5 (US3)** & **Phase 6 (US4)**: Depend on Phase 3 & Phase 4 foundations.
- **Phase 7 (US5 & Polish)**: Runs after all User Stories are wired and tested.
