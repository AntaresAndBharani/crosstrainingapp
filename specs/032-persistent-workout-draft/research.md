# Research & Technical Decisions: Persistent Active Workout Draft & Unified Live Session Dock

**Feature**: `SPEC-032` (`032-persistent-workout-draft`)  
**Date**: 2026-11-28

## 1. Draft State Hoisting & Disk Persistence

- **Decision**: Hoist the active `LogSessionScreen` draft into `AppViewModel` backed by per-mode (`real` / `demo`) disk persistence in `DataModeManager` (`SharedPreferences` `"crosstraining-prefs"`, with an in-memory map fallback when `prefs == null` in pure-JVM tests), serialized by a zero-dependency pure-Kotlin JSON codec (`ActiveWorkoutDraftCodec`).
- **Rationale**:
  - `LogSessionScreen` previously passed `remember { emptySessionSeed() }` to `SessionEditorBody`, and `SessionEditorBody` held all fields in `remember(key)` — causing disposal on every bottom-tab or drawer navigation, rotation, or background kill.
  - Room `AppDatabase` is at version `10` and Constitution Principle V forbids unnecessary schema migrations when persistence does not require relational querying. Storing one JSON payload per data mode (`active_workout_draft_real` / `active_workout_draft_demo`) keeps Room v10 untouched and supports instant synchronous startup hydration.
  - Avoiding `org.json.JSONObject` in `ActiveWorkoutDraftCodec` is mandatory because `app/build.gradle.kts` runs `testDebugUnitTest` with `isReturnDefaultValues = true` on the JVM without Robolectric; Android `org.json` stubs return empty defaults under unit tests. A pure-Kotlin JSON writer/recursive-descent parser guarantees 100% deterministic unit testability.
- **Alternatives Considered**:
  - Room `active_draft` entity + `MIGRATION_10_11` (rejected per Constitution V — unnecessary schema churn for a single-record UI draft).
  - Keeping `LogSessionScreen` alive in a hidden Compose `Box` (rejected — wastes memory/layout passes, fails process kill recovery, breaks Compose Navigation backstack semantics).

## 2. Debounced Auto-Save & Synchronous Lifecycle Flush

- **Decision**: Maintain live in-memory `StateFlow<ActiveWorkoutDraftSnapshot?>` in `AppViewModel` updated on every `snapshotFlow` change, paired with a 500ms debounced disk write and an immediate `flushActiveWorkoutDraftNow(...)` hook invoked on `Lifecycle.Event.ON_STOP` (from `AppNavigation`) and `DisposableEffect.onDispose` (from `SessionEditorBody`, skipped when saving/discarding via `skipFlushOnDispose`).
- **Rationale**:
  - Typing weights/reps never blocks the main thread on disk I/O.
  - Switching tabs, rotating, or backgrounding the phone flushes immediately so even a sub-500ms edit before switching apps survives OS process termination.
  - `activeWorkoutDraftRevision: StateFlow<Int>` increments ONLY on explicit reset/clear/date-update/demo-switch, never on keystrokes, preventing `SessionEditorBody` from re-hydrating and resetting cursor/IME focus while typing.

## 3. Single-Dock Arbitration & 1Hz Recomposition Isolation

- **Decision**: Introduce pure `LiveSessionDockResolver` driving a single root `LiveSessionDock` composable inside `AppNavigation`'s `Scaffold.bottomBar` (directly above `DemoBanner` and `PrimaryNavigationBar`), while restricting `SessionEditorBody`'s inner `InSessionTimerBar` strictly to `isActiveLogTab && isTimerActive`.
- **Rationale**:
  - `SessionEditorBody` is shared by `LogSessionScreen` (`log`) and `SessionEditorScreen` (`sessionEditor/{id}/{copy}`). Restricting the inner `InSessionTimerBar` to `isActiveLogTab` ensures Edit/Copy Session never double-stacks with the global `LiveSessionDock`.
  - `AppNavigation` observes only a low-frequency `TimerDockMeta(isActive, sourceBlockKey, isFinished)` mapped with `distinctUntilChanged()`, and `LiveSessionDock` isolates `timerEngine.snapshot.collectAsStateWithLifecycle()` inside a leaf timer composable — preventing 1Hz recomposition of `AppNavigation`, `NavHost`, or active tab screens.

## 4. Routine Conflict Guard & Edit Isolation

- **Decision**: Intercept both `LoadDailyRoutineSheet` selections inside `Log` and `pendingRoutineIdToLog` handoffs from `Library -> Log Routine` whenever `loadedAvailableBlocks.isNotEmpty() || blocks.any { !it.isBlankLegacyPlaceholder() }`, prompting `Replace current` | `Append blocks` | `Cancel`. Keep `SessionEditorScreen` (`History -> Edit/Copy`) completely decoupled from `activeWorkoutDraft`, and track `isHistoryEditorDirty` in `AppViewModel` so tapping `LiveSessionDock` while editing a dirty past session confirms before discarding Edit Session changes.
- **Rationale**: Protects both the active workout draft and in-flight edits to historical sessions without blocking normal browsing.
