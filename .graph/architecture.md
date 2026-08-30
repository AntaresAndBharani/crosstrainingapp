# CrossTraining App — System Architecture & Standards

## System Overview & Technology Stack

**CrossTraining** (`com.fractanomics.crosstraining`) is an offline-first Android application engineered for CrossFit athletes, strength trainees, and strength & conditioning coaches. The platform enables comprehensive tracking of strength progressions, complex barbell routines, periodized training cycles, monostructural conditioning metrics, and workout interval timers.

```mermaid
graph TD
    UI["Presentation Layer (Jetpack Compose / Material 3)"]
    VM["ViewModel (AppViewModel & StateFlow)"]
    DM["DataModeManager (Mode Routing & Persistent Preferences)"]
    Repo["Repository (Single Source of Truth)"]
    Room[("Room Database (SQLite Local Persistence)")]
    FB[("Firebase (Auth, Firestore & Cloud Sync)")]
    Timer["Timer Subsystem (TimerEngine & Foreground Service)"]

    UI -->|User Events & Intent Actions| VM
    VM -->|Collects StateFlow| UI
    VM -->|Data Operations| Repo
    VM -->|Mode & Role Management| DM
    DM -->|Routes Active DB Instance| Repo
    Repo -->|Atomic SQL Transactions| Room
    Repo -->|Bi-Directional Cloud Sync| FB
    UI <-->|Observes & Controls| Timer
    Timer -.->|Foreground Media Notification & Deep Links| UI
```

### Technology Stack & Framework Specifications

| Tier / Subsystem | Technology | Specification / Version | Architectural Role |
|---|---|---|---|
| **Language & Runtime** | Kotlin | 2.0.21 / JVM 17 (`compileOptions`, `kotlinOptions`) | Strongly-typed functional & object-oriented application core |
| **Android SDK Target** | Android SDK | `minSdk 26`, `targetSdk 35`, `compileSdk 35` | Modern Android 15 platform compliance with Android 8.0+ backwards compatibility |
| **UI Toolkit** | Jetpack Compose | Compose BOM `2024.10.01`, Material 3 | Declarative, reactive, component-driven user interface |
| **Navigation** | Navigation Compose | `2.8.4` (`androidx.navigation.compose`) | Single-Activity declarative navigation host and deep-linking |
| **Persistence** | AndroidX Room | `2.6.1` with KSP `2.0.21-1.0.28` | Local relational SQLite database with typed DAOs and schema migrations |
| **Asynchronous & Flow** | Kotlin Coroutines & Flow | `1.9.0` (`StateFlow`, `SharedFlow`, `Flow`) | Structured concurrency, reactive data streaming, non-blocking I/O |
| **Lifecycle Integration** | AndroidX Lifecycle | `2.8.7` (`lifecycle-runtime-compose`, `viewmodel-compose`) | Lifecycle-aware UI state collection (`collectAsStateWithLifecycle`) |
| **Identity & Cloud Sync** | Firebase & Google ID | Firebase BoM `33.9.0`, Play Services Auth `21.3.0`, Credential Manager `1.3.0` | Multi-tenant cloud synchronization, user authentication, and workout sharing |
| **Media & Peripherals** | AndroidX Media & Audio | `androidx.media:1.7.0`, `ToneGenerator`, `VibratorManager` | Foreground MediaStyle notifications, audio interval cues, and haptics |
| **Build & Tooling** | Gradle Kotlin DSL | AGP `8.7.2`, Gradle Wrapper | Automated reproducible build pipelines, signing, and asset packaging |

### Multi-Environment Packaging & Build Variants

The build system defines three distinct build types:
- **`debug`**: Local development build configured with `APP_ENV="snapshot"` and signed with the standard debug keystore.
- **`snapshot`**: Pre-release CI verification build inheriting from `debug` configuration with matching fallbacks, compiled for automated E2E and device testing.
- **`release`**: Production-optimized build configured with `APP_ENV="production"`, integrating ProGuard/R8 optimizations (`proguard-rules.pro`), and signed using secure environment keystore credentials (`RELEASE_STORE_FILE`, `RELEASE_KEY_ALIAS`).

---

## Layer Boundaries & Clean Architecture (Domain, Data, Presentation/UI separation of concerns)

The codebase strictly adheres to **Clean Architecture** principles and **Unidirectional Data Flow (UDF)**. Dependencies strictly point inward toward domain models and business logic.

```mermaid
graph RL
    subgraph Presentation ["Presentation Layer (ui)"]
        UI_Screens["Compose Screens (ui.screens)"]
        UI_Components["Reusable Components (ui.components)"]
        UI_VM["AppViewModel"]
        UI_Nav["Navigation & Intent Handling (ui.navigation)"]
        UI_Timer["Timer Engine & Service (ui.timer)"]
    end

    subgraph Data ["Data Layer (data)"]
        D_Repo["Repository (Single Source of Truth)"]
        D_Mode["DataModeManager (Sandbox Routing)"]
        D_DAO["Room DAOs (data.dao)"]
        D_DB["AppDatabase (SQLite & Migrations)"]
        D_Cloud["UserCloudSyncManager & FirebaseSyncManager (data.firebase)"]
        D_Backup["Backup (CSV Engine)"]
    end

    subgraph Domain ["Domain & Utility Layer (util & data.model)"]
        DOM_Models["Entities & Value Objects (Cycle, Session, Routine, etc.)"]
        DOM_Enums["Domain Enums (BlockKind, MetricType, UserRole, etc.)"]
        DOM_Utils["Pure Algorithms (WorkoutParser, RepScheme)"]
    end

    Presentation --> Data
    Presentation --> Domain
    Data --> Domain
```

### 1. Domain & Utility Layer (`com.fractanomics.crosstraining.util` & `data.model`)
- **Responsibilities:**
  - Contains core business entities (`Cycle`, `Session`, `Routine`, `Exercise`, `RepMax`, `BlockSet`, `RoutineBlock`, `SessionBlock`, `CycleGoal`), domain value objects, and enums (`BlockKind`, `MetricType`, `ExerciseCategory`, `UserRole`, `TimerMode`, `TimerPhase`).
  - Encapsulates pure domain algorithms: `WorkoutParser` (free-text workout syntax parsing, rep-scheme extraction, movement extraction) and `RepScheme` (wave-loading validation and rep-count decomposition).
- **Architectural Invariants:**
  - **Zero Platform Dependencies:** Must not import Android framework classes (`android.*`, `Context`, `View`, `Bundle`, Compose UI tokens).
  - **Deterministic & Pure:** All functions must be deterministic, free of side-effects, and 100% unit-testable without Android mocks or Robolectric runners.

### 2. Data Layer (`com.fractanomics.crosstraining.data`)
- **Responsibilities:**
  - **Room Database & DAOs (`data.dao`):** Provides strongly-typed SQL mapping, foreign key constraints, cascading deletes, indexes, and reactive queries via Kotlin `Flow`.
  - **Single Source of Truth (`Repository`):** Coordinates multi-entity transactional persistence using `db.withTransaction { ... }`. Encapsulates write-time business logic such as auto-creating exercises (`getOrCreateExercise`), synchronizing routine blocks, discovering new rep-maxes, and deduplicating routines.
  - **Dual-Sandbox Routing (`DataModeManager`):** Seamlessly redirects repository bindings between the live database (`crosstraining.db`) and the disposable sample database (`crosstraining-demo.db`), guaranteeing that demo sessions cannot corrupt athlete history.
  - **Cloud Synchronization (`data.firebase`):** Handles background synchronization of user data against partitioned Firestore documents (`UserCloudSyncManager`) and global community workout publishing via alphanumeric share codes (`FirebaseSyncManager`).
  - **Backup & Migration Engine (`BackupCsv`, `AppDatabase.MIGRATION_*`):** Manages relational CSV serialization/deserialization and SQLite schema migrations (v1 through v5).
- **Architectural Invariants:**
  - DAOs must remain package-private or accessible exclusively through `Repository`.
  - All database writes, file I/O, and network operations must execute on background coroutine dispatchers (`Dispatchers.IO`).

### 3. Presentation / UI Layer (`com.fractanomics.crosstraining.ui`)
- **Responsibilities:**
  - **State Orchestration (`AppViewModel`):** Serves as the central state holder for the UI. Binds reactive streams from `DataModeManager.repositoryFlow`, exposes lifecycle-safe `StateFlow<T>`, and receives user intents to trigger coroutine executions on `viewModelScope`.
  - **Declarative Navigation (`ui.navigation`):** Defines top-level navigation routes (`BottomDestination`, `DrawerItem`), handles dynamic bottom bars based on user role (Athlete vs Coach), and processes external navigation intents via `NavigationIntentHandler`.
  - **Stateful Screens & Stateless Content (`ui.screens`):** Implements clean separation between stateful container composables (which inject the ViewModel) and stateless presentation composables (which receive pure data classes and emit lambdas).
  - **Design System & Components (`ui.components`, `ui.theme`):** Encapsulates Material 3 theme definitions, typography, dynamic color palettes, and robust UI primitives such as `AppNumericTextField` and `LineChart`.
  - **Foreground Timer Subsystem (`ui.timer`):** Hoists `TimerEngine` to application scope, running tick loops independent of activity lifecycle, and binds to `TimerService` for MediaStyle notifications and hardware cues (audio/vibrations).
- **Architectural Invariants:**
  - UI components must never instantiate or interact with Room DAOs or Firebase SDKs directly.
  - State collection in Composables must always utilize `collectAsStateWithLifecycle()` to prevent background resource leaks.

---

## Directory & Package Structure Guidelines

The directory structure enforces strict modular separation by technical concern and domain responsibility:

```
crosstrainingapp/
├── app/
│   ├── build.gradle.kts                          # App build configuration, dependencies, and signing configs
│   ├── proguard-rules.pro                        # Proguard / R8 optimization rules
│   └── src/
│       ├── main/
│       │   ├── AndroidManifest.xml               # App manifest, permissions, service & activity declarations
│       │   └── java/com/fractanomics/crosstraining/
│       │       ├── CrossTrainingApp.kt           # Application class & Composition Root (DataModeManager, TimerEngine)
│       │       ├── MainActivity.kt               # Single Activity host with Edge-to-Edge & NavigationIntentHandler
│       │       ├── data/
│       │       │   ├── AppDatabase.kt            # Room database definition, type converters, & migrations (v1-v5)
│       │       │   ├── Repository.kt             # Single Source of Truth repository with atomic transaction writes
│       │       │   ├── DataModeManager.kt        # Dual-database routing (Live vs Demo) & persistent preferences
│       │       │   ├── Backup.kt                 # Relational CSV export and import serialization engine
│       │       │   ├── Converters.kt             # Room type converters (LocalDate, enums, primitives)
│       │       │   ├── SeedData.kt               # Starter database seeding (exercises, routines, sample cycles)
│       │       │   ├── DemoData.kt               # Isolated comprehensive demo dataset generator
│       │       │   ├── dao/                      # Room Data Access Objects
│       │       │   │   ├── BlockDao.kt           # Session blocks and block sets DAO
│       │       │   │   ├── CycleDao.kt           # Training cycles DAO
│       │       │   │   ├── CycleGoalDao.kt       # Periodization cycle goals DAO
│       │       │   │   ├── ExerciseDao.kt        # Movement and exercise catalog DAO
│       │       │   │   ├── RepMaxDao.kt          # Personal records & rep-max history DAO
│       │       │   │   ├── RoutineDao.kt         # Routines and routine blocks DAO
│       │       │   │   └── SessionDao.kt         # Logged workouts & session history DAO
│       │       │   ├── firebase/                 # Cloud synchronization and remote identity
│       │       │   │   ├── UserCloudSyncManager.kt # Firestore user data sync & Firebase Auth integration
│       │       │   │   └── FirebaseSyncManager.kt  # Community workout sharing & lookup by share code
│       │       │   └── model/                    # Relational Room entities, relations, & domain enums
│       │       │       ├── BlockSet.kt           # Set-level entity (reps, weight, metricValue, warmup, failed)
│       │       │       ├── Cycle.kt              # Periodized training cycle entity
│       │       │       ├── CycleGoal.kt          # Target lift and rep-max goal entity
│       │       │       ├── Enums.kt              # Domain enums (BlockKind, MetricType, ExerciseCategory)
│       │       │       ├── Exercise.kt           # Exercise entity
│       │       │       ├── Relations.kt          # Room 1-to-N relation models (CycleWithGoals, RoutineWithBlocks, SessionWithBlocks)
│       │       │       ├── RepMax.kt             # Rep-max record entity
│       │       │       ├── Routine.kt            # Routine template entity
│       │       │       ├── RoutineBlock.kt       # Routine block template entity
│       │       │       ├── Session.kt            # Logged training session entity
│       │       │       ├── SessionBlock.kt       # Session block entity
│       │       │       └── UserRole.kt           # User persona model (ATHLETE vs COACH)
│       │       ├── ui/
│       │       │   ├── AppViewModel.kt           # Unified UI ViewModel exposing StateFlows and dispatching actions
│       │       │   ├── Format.kt                 # UI display formatting helpers (dates, weights, times, scores)
│       │       │   ├── ProgressAnalytics.kt      # Rep-max calculation, volume progression, and PR charting models
│       │       │   ├── SessionDraft.kt           # Ephemeral UI editing models for workout logging & editing
│       │       │   ├── components/               # Reusable Jetpack Compose UI components
│       │       │   │   ├── CommonUi.kt           # Shared UI buttons, headers, cards, AppNumericTextField, modal sheets
│       │       │   │   ├── DateField.kt          # Date picker field with Material 3 integration
│       │       │   │   ├── Dropdown.kt           # Form dropdown selector
│       │       │   │   ├── LineChart.kt          # Custom Canvas-rendered strength progression line chart
│       │       │   │   └── QuickAddWorkoutDialog.kt # Modal dialog for quick workout insertion
│       │       │   ├── navigation/               # Navigation topology & routing
│       │       │   │   ├── AppNavigation.kt      # NavHost, BottomNavigationBar, and ModalNavigationDrawer
│       │       │   │   └── NavigationIntentHandler.kt # Deep-link and notification intent routing handler
│       │       │   ├── screens/                  # Top-level screen composables
│       │       │   │   ├── CyclesScreen.kt       # Training cycle management & periodization planning
│       │       │   │   ├── HistoryScreen.kt      # Historical training session log & search
│       │       │   │   ├── LibraryScreen.kt      # Movement catalog & routine builder
│       │       │   │   ├── LoginWelcomeScreen.kt # Authentication, Google Sign-In, & Guest mode gate
│       │       │   │   ├── LogSessionScreen.kt   # Daily workout logging screen
│       │       │   │   ├── ProfileScreen.kt      # User account, theme toggle, and CSV backup/restore
│       │       │   │   ├── ProgressScreen.kt     # Personal record analytics & progression charts
│       │       │   │   ├── SessionEditor.kt      # Comprehensive session editor with set spreadsheet
│       │       │   │   └── TimerScreen.kt        # Workout interval timer configuration & active display
│       │       │   ├── theme/                    # Material Design 3 theme tokens
│       │       │   │   ├── Color.kt              # App color palettes
│       │       │   │   ├── Theme.kt              # CrossTrainingTheme wrapper with light/dark/system support
│       │       │   │   └── Type.kt               # Typography specifications
│       │       │   └── timer/                    # Foreground Timer Subsystem
│       │       │       ├── NotificationPermissionHelper.kt # Runtime notification permission check & launch helper
│       │       │       ├── TimerEngine.kt        # State machine, countdown loop, audio tones, & vibrations
│       │       │       ├── TimerEngineProvider.kt# Application-scoped singleton provider for TimerEngine
│       │       │       ├── TimerNotificationActionDispatcher.kt # Dispatches notification intent actions to TimerEngine
│       │       │       ├── TimerNotificationFormatter.kt # Dynamic title & content string formatter for notifications
│       │       │       ├── TimerNotificationSpec.kt # Notification action and metadata builder
│       │       │       ├── TimerService.kt       # Foreground service hosting ongoing MediaStyle notification
│       │       │       ├── TimerTeardownController.kt # Graceful service termination and resource release
│       │       │       └── WorkoutTimer.kt       # Timer data contracts (TimerMode, TimerPhase, WorkoutTimerConfig, TimerSnapshot)
│       │       └── util/
│       │           ├── RepScheme.kt              # Rep scheme pattern parsing & wave validation
│       │           └── WorkoutParser.kt          # Free-text WOD and complex routine parsing algorithms
│       └── test/java/com/fractanomics/crosstraining/ # Unit and integration test suites
│           ├── data/RoutineModelTest.kt
│           ├── ui/components/AppNumericTextFieldTest.kt
│           ├── ui/components/QuickAddWorkoutDialogNumericMigrationTest.kt
│           ├── ui/screens/SessionEditorNumericMigrationTest.kt
│           ├── ui/screens/TimerScreenNumericMigrationTest.kt
│           ├── ui/theme/ThemeModeTest.kt
│           ├── ui/timer/NotificationPermissionTest.kt
│           ├── ui/timer/NotificationTapNavigationTest.kt
│           ├── ui/timer/SharedTimerStateTest.kt
│           ├── ui/timer/TimerEngineTest.kt
│           ├── ui/timer/TimerNotificationActionTest.kt
│           ├── ui/timer/TimerServiceTest.kt
│           ├── ui/timer/TimerTeardownTest.kt
│           └── util/WorkoutParserTest.kt
├── docs/                                         # Technical documentation & testing runbooks
│   └── local-testing.md                          # Comprehensive local testing guide & CI status check registry
├── e2e/                                          # Automated Maestro E2E test flows
│   ├── flow-mapping.json                         # Mapping of E2E test flows to functional domains
│   └── flows/                                    # Maestro YAML scenario scripts (01-06)
└── scripts/                                      # Automation scripts & CI test harnesses
    ├── lib/                                      # Reusable PowerShell modules (GitHubArtifactHelper, PrComment)
    └── tests/                                    # Pester test suites verifying CI pipelines and scripts
```

---

## Design Patterns, State Management & Dependency Injection

### 1. Unidirectional Data Flow (UDF) & Reactive StateFlow Architecture
The presentation layer strictly follows the UDF pattern:
- **UI State**: ViewModels expose immutable `StateFlow<T>` objects created via `stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), initial)`. The 5-second `WhileSubscribed` timeout ensures that upstream database queries pause when the app is backgrounded, while surviving brief configuration changes (e.g. screen rotations).
- **User Intent**: Composables capture user interactions and dispatch discrete events (e.g. `viewModel.saveSession(draft)`) to the ViewModel.
- **State Collection**: Composables collect state using `collectAsStateWithLifecycle()`, guaranteeing automatic subscription binding and unbinding aligned with the Android lifecycle.

```kotlin
// ViewModel state declaration pattern
val sessions: StateFlow<List<SessionWithBlocks>> =
    data.repositoryFlow
        .flatMapLatest { it.allSessions }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5_000),
            initialValue = emptyList()
        )
```

### 2. Dual-Sandbox Routing Pattern (`DataModeManager`)
To provide a seamless, non-destructive trial experience ("Continue as Guest" / "Explore Demo"):
- `DataModeManager` maintains two distinct `Repository` instances backed by separate SQLite database files:
  1. `crosstraining.db` (Athletes' permanent real training history)
  2. `crosstraining-demo.db` (Generated sample training history)
- All ViewModel data streams subscribe to `data.repositoryFlow`, allowing live, instantaneous UI switching between real and demo databases with zero memory leaks and zero risk of cross-contamination.

### 3. Stateful Container vs Stateless Presentation Composables
To maximize UI testability, previewability, and separation of concerns, all screens follow the container/content separation pattern:
- **Stateful Route (`*Screen`)**: Responsible for collecting `StateFlow`s via `collectAsStateWithLifecycle()`, resolving navigation callbacks, and forwarding parameters.
- **Stateless Content (`*Content`)**: Pure composable accepting data models and emitting lambda events. Does not reference `ViewModel`, enabling rapid rendering in `@Preview` and isolated UI tests.

```kotlin
// 1. Stateful Container
@Composable
fun CyclesScreen(
    viewModel: AppViewModel,
    outerPadding: PaddingValues,
    onOpenDrawer: () -> Unit = {},
    onOpenTimer: () -> Unit = {}
) {
    val cycles by viewModel.cycles.collectAsStateWithLifecycle()
    val userRole by viewModel.userRole.collectAsStateWithLifecycle()
    
    CyclesContent(
        cycles = cycles,
        userRole = userRole,
        outerPadding = outerPadding,
        onOpenDrawer = onOpenDrawer,
        onActivateCycle = { id -> viewModel.activateCycle(id) },
        onDeleteCycle = { cycle -> viewModel.deleteCycle(cycle) }
    )
}

// 2. Stateless Content
@Composable
fun CyclesContent(
    cycles: List<Cycle>,
    userRole: UserRole,
    outerPadding: PaddingValues,
    onOpenDrawer: () -> Unit,
    onActivateCycle: (Long) -> Unit,
    onDeleteCycle: (Cycle) -> Unit
) {
    // Pure rendering logic
}
```

### 4. Dependency Injection via Composition Root
The project utilizes a lightweight, compile-time **Manual Dependency Injection / Composition Root** architecture:
- `CrossTrainingApp` instantiates shared application singletons (`DataModeManager`, `TimerEngine`) lazily.
- `AppViewModel.factory(dataModes)` implements `ViewModelProvider.Factory` to inject dependencies directly into ViewModels without requiring reflection or heavy DI containers.
- `TimerEngineProvider` provides safe, thread-safe access to the singleton `TimerEngine` across `MainActivity` and `TimerService`.

### 5. Application-Scoped Foreground Timer Subsystem
Workouts require continuous countdown tracking even when the screen is locked or the application is placed in the background:
- **`TimerEngine`**: Thread-safe state machine managing countdown ticks, phase transitions (`PREP` -> `WORK` -> `REST` -> `FINISHED`), round advancement, and hardware peripherals (`ToneGenerator`, `VibratorManager`).
- **`TimerService`**: Foreground service hosting an ongoing `NotificationCompat.MediaStyle` notification with active countdown progress and interactive notification action buttons (`Play`, `Pause`, `Next`, `Stop`).
- **`TimerNotificationActionDispatcher`**: Decouples incoming notification intent actions from direct timer execution.
- **`TimerTeardownController`**: Manages graceful service shutdown, dismisses notifications, and releases `MediaSessionCompat` resources when the timer stops or completes.
- **`NavigationIntentHandler`**: Captures notification click deep links and routes the Compose `NavHost` directly into the active `TimerScreen`.

### 6. Localized Ephemeral Buffer & Deferred Commit Pattern (`AppNumericTextField`)
For high-frequency numeric inputs (reps, sets, weights, interval seconds, round counts):
- Maintains an ephemeral `TextFieldValue` buffer inside the Composable using `remember(value) { mutableStateOf(TextFieldValue(value.toString())) }`.
- Automatically selects all text upon gaining focus (`TextRange(0, text.length)`), enabling instant single-digit replacement without digit concatenation bugs.
- Immediately filters out invalid characters (non-digits, redundant decimals).
- Defers final parsing, leading-zero sanitization, and bounds clamping (`minValue`..`maxValue`) until **focus loss** or **IME Done/Next** action.

```mermaid
sequenceDiagram
    participant User
    participant Composable as AppNumericTextField
    participant VM as AppViewModel / Draft

    User->>Composable: Taps input field (Focus Gained)
    Composable->>Composable: Select all text (TextRange(0, len))
    User->>Composable: Types single digit "5"
    Composable->>Composable: Replaces selection instantly (Buffer = "5")
    User->>Composable: Submits IME Done or unfocuses
    Composable->>Composable: Sanitizes & clamps to [minValue..maxValue]
    Composable->>VM: onValueChange(5)
```

### 7. Atomic Relational Transaction Pattern (`db.withTransaction`)
Multi-table relational writes must maintain complete atomicity:
- Saving complex entities (e.g. a `Session` with its associated `SessionBlock`s and `BlockSet`s, or a `Routine` with its `RoutineBlock`s) executes within `db.withTransaction { ... }`.
- If any stage fails, the entire transaction is rolled back, preventing orphaned records and foreign-key corruption.

### 8. SHA-Cached Main-Branch Artifact Retrieval (`GitHubArtifactHelper`)
To support deterministic local E2E testing without redundant compilation:
- Resolves the target `main` branch HEAD commit SHA via `git rev-parse origin/main`.
- Checks the local artifact cache at `app/build/outputs/apk/ci-main/<sha>.apk`.
- On cache miss, downloads the official CI build artifact or release asset via `gh run download` / `gh release download` and caches it locally.

---

## Architectural Constraints & Anti-Patterns

### Strict Architectural Constraints

1. **Inward-Only Dependency Rule**:
   - The UI layer (`com.fractanomics.crosstraining.ui`) must never directly query Room DAOs, `AppDatabase`, or Firebase SDKs. All operations must flow through `AppViewModel`.
   - Domain utilities (`com.fractanomics.crosstraining.util`) and models (`data.model`) must remain pure Kotlin with zero Android framework imports.
2. **Lifecycle-Safe Reactive Collection**:
   - UI Composables must always use `collectAsStateWithLifecycle()` to collect `StateFlow`s. Raw `collectAsState()` is prohibited because it continues collecting when the application is backgrounded.
3. **Structured Non-Blocking Coroutines**:
   - All database transactions, CSV parsing, and network synchronizations must execute on `Dispatchers.IO`.
   - `GlobalScope.launch` and `runBlocking` are strictly prohibited in production code. Use `viewModelScope` in ViewModels and `rememberCoroutineScope` in Composables for UI-only effects.
4. **Relational Atomic Integrity**:
   - Multi-entity writes (e.g. saving a `Session` with its `SessionBlock`s and `BlockSet`s) must be wrapped in `db.withTransaction { ... }`.
5. **Peripheral & Hardware Resilience**:
   - Audio (`ToneGenerator`) and Haptic (`VibratorManager` / `Vibrator`) invocations must be safely wrapped with fallback exception handling to support varying Android API levels, emulator environments, and headless test runners.
6. **Zero Environment Configuration Leaks**:
   - Never commit developer-specific JVM paths (e.g. `org.gradle.java.home`) to repository `gradle.properties`. Keystore secrets and environment tokens must be injected via Gradle properties or environment variables.
7. **Strict Acyclic Package Graph**:
   - Dependencies must strictly follow: `util` → `model` → `dao` → `data` → `ui`. Circular dependencies between packages or components are forbidden.

### Architectural Anti-Patterns & Solutions

| Anti-Pattern | Violation | Required Architectural Solution |
|---|---|---|
| **Direct DAO Access in UI** | Calling `exerciseDao.insert()` directly inside a `@Composable` button click. | Dispatch user intent to `AppViewModel.saveExercise()`, delegating to `Repository`. |
| **Blocking the Main Thread** | Performing CSV file export or database queries synchronously on `Dispatchers.Main`. | Dispatch file and database I/O via `withContext(Dispatchers.IO)`. |
| **Raw Hardcoded UI Values** | Hardcoding raw hex colors (`#FF0000`) or raw pixel sizes in Composables. | Use Material 3 tokens: `MaterialTheme.colorScheme.*`, `MaterialTheme.typography.*`, and `dp`/`sp` units. |
| **Cross-Sandbox State Contamination** | Directing demo data modifications into the live SQLite database file. | Route all data access through `DataModeManager.repositoryFlow`, ensuring clean physical database separation. |
| **Orphaned Background Timers** | Starting an unbound coroutine timer loop that leaks memory when the screen is destroyed. | Encapsulate timer state within the singleton `TimerEngine` and bind background execution to `TimerService`. |
| **Over-Hoisting Transient State** | Storing temporary text field typing buffers or dropdown expansion booleans in `AppViewModel`. | Keep transient UI state local to the Composable using `remember { mutableStateOf(...) }`. |
| **UI Logic in Domain Layer** | Importing Android UI widgets, formatters, or `Context` into `WorkoutParser` or `RepScheme`. | Keep domain algorithms 100% platform-agnostic pure Kotlin functions. |
| **Unsafe String Navigation Routing** | Concatenating unescaped argument strings in Compose navigation calls. | Use type-safe sealed destinations with structured argument encoding. |

---

## Definition of Done (DoD) for Architecture Updates

When modifying system architecture or implementing new features:
1. **Automated Verification**: All local unit tests pass (`.\gradlew.bat testDebugUnitTest --no-daemon`).
2. **Architecture Compliance**: New features must strictly adhere to the layered structure (`data/model`, `data/dao`, `data/firebase`, `ui/screens`, `ui/components`, `ui/timer`, `util`).
3. **Living Documentation Sync**: Any structural modifications, new layers, or data flow updates must be synchronized with `.graph/architecture.md` and `.agents/rules/`.
4. **Changelog Maintenance**: Add a descriptive entry under `## [Unreleased]` in `CHANGELOG.md` following the Keep a Changelog standard.
5. **Remote CI Gate**: Remote GitHub Actions CI workflows (`build.yml`, `release.yml`) must report 100% green status prior to PR merge.
