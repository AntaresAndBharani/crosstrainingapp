# Implementation Plan: Optional Daily Routine Multi-Metric Targets & Metric-Aware Workout Logging (`SPEC-026`)

**Branch**: `feat/026-daily-routine-metric-targets` | **Date**: 2026-11-19 | **Spec**: [spec.md](file:///c:/Users/rogal/workspaces/ws-gym/crosstrainingapp/specs/026-daily-routine-metric-targets/spec.md)

**Input**: Feature specification from `/specs/026-daily-routine-metric-targets/spec.md`

## Summary

Restore per-exercise target programming (`Sets`, `Metric`: Reps / Calories / Time / Meters, `Fixed` vs `Per-Set` with `0 / Skip`) on Daily Routine items (`StandaloneExercise` and per-routine `BlockRef` overrides) as strictly opt-in/unset-by-default controls inside `DailyRoutineComposerSheet` (keeping `Library -> Blocks` and `Library -> Exercises` lightweight and instructions-only), and make both `SimplifiedExerciseLogCard` and `SimplifiedBlockLogCard` in `Log Session` and `Edit Session` fully metric-aware so athletes can log and edit Reps (+ optional Weight), Calories, Time, and Meters end-to-end across History, Progress, Cloud Sync, and Remote MCP without any Room schema migration.

## Technical Context

**Language/Version**: Kotlin (JVM 17) on Android (`compileSdk 35`, `minSdk 26`) + TypeScript (Node.js 22) for `mcp-server/`  
**Primary Dependencies**: Jetpack Compose + Material 3, Room SQLite (v10), Firebase Cloud Sync (`UserCloudSyncManager`), Express + `@modelcontextprotocol/sdk` + Zod  
**Storage**: Room SQLite (`Routine`, `RoutineBlock`, `Session`, `SessionBlock`, `BlockSet`, `Exercise`, `RepMax`) + Cloud Firestore `/environments/{env}/users/{uid}/data/{collection}` (Zero DB schema migrations)  
**Testing**: JUnit 4 (`.\gradlew.bat testDebugUnitTest --no-daemon`), PowerShell Pester (`.\scripts\tests\Invoke-ScriptTests.ps1`), Android Lint (`.\gradlew.bat lintDebug --no-daemon`), Vitest (`mcp-server`: `npm run build && npm test`)  
**Target Platform**: Android mobile (`360dp`+ viewports up to `fontScale >= 1.5f`) & Cloud Run Remote MCP Server  
**Project Type**: Mobile app + Remote MCP Connector (`app/` + `mcp-server/`)  
**Performance Goals**: Instantaneous Compose state updates & pure deterministic analytics calculations with zero main-thread I/O  
**Constraints**: Zero Room DB migrations; zero `$ref` / `exclusiveMinimum` in MCP JSON schemas; `>= 48.dp` touch targets; no emulator / Maestro tests  
**Scale/Scope**: Touches codecs (`RoutineTargetCodec`, `DailyRoutineCodec`, `AvailableBlockCodec`), analytics (`ProgressAnalytics`), UI (`LibraryScreen`, `RoutineItemTargetsEditor`, `SimplifiedBlockLogComponents`, `SessionEditor`, `HistoryScreen`, `ProgressScreen`), `AppViewModel`/`SessionDraft`, and `mcp-server/` (`schemas.ts`, `store.ts`, tests).

## Constitution Check

*GATE: Must pass before Phase 0 research. Re-check after Phase 1 design.*

- **I. MVVM & Unidirectional Data Flow (UDF)**: PASS — Target reducers in `DailyRoutineComposerState`, session logging state in `LoadedAvailableBlockState`, and ViewModel `buildBlockInserts` strictly follow UDF; domain codecs remain pure deterministic Kotlin functions.
- **II. Pure Deterministic Analytics & Display-Unit Consistency**: PASS — `BlockPerformance`, `DailyRoutineCodec.computeDailyRoutineProgress`, and `resolveLastExerciseSession` stay pure and deterministic with consistent unit resolution (`kg`, `lbs`, `reps`, `cal`, `s`, `m`).
- **III. Test-First & 100% CI Parity (NON-NEGOTIABLE)**: PASS — Full unit test coverage across Kotlin codecs, state holders, analytics, and `mcp-server` contracts; all four verification suites (`testDebugUnitTest`, `Invoke-ScriptTests.ps1`, `lintDebug`, `npm run build && npm test`) gated prior to PR.
- **IV. Responsive Mobile UX & Accessibility**: PASS — Single-line metric chips (`maxLines = 1, softWrap = false`), responsive grid column counts (2/1/4 for Reps+Weight, 3/2/4 for metric-value tiles), `>= 48.dp` touch targets, and merged TalkBack semantics.
- **V. Offline-First, Idempotent Synchronization & Environment Isolation**: PASS — Room v10 schema untouched; `[rb_v2=...]` and `[log_metric=...]` travel cleanly inside existing synced `notes` fields; no machine-specific paths in `gradle.properties`.
- **VI. Agentic Collaboration & Governance**: PASS — Branch follows `feat/<NNN-short-name>`, `CHANGELOG.md` updated, and PR CI checks monitored to green.
- **VII. Reliable, Atomic & Client-Compatible MCP Communication**: PASS — Exactly 39 MCP tools with accurate `ToolAnnotations`, flat schemas (`0` `$ref`, `0` `exclusiveMinimum`), and whole-collection transactional writes.

*Post-Phase-1 Re-check*: All 7 Constitutional principles verified PASS after completing `data-model.md`, `contracts/`, and `quickstart.md`.

## Project Structure

### Documentation (this feature)

```text
specs/026-daily-routine-metric-targets/
├── plan.md
├── research.md
├── data-model.md
├── quickstart.md
├── contracts/
│   └── routine-targets-and-logging-contract.md
├── checklists/
│   ├── requirements.md
│   └── targets-and-logging.md
└── tasks.md
```

### Source Code (repository root)

```text
app/src/main/java/com/fractanomics/crosstraining/
├── util/
│   ├── RoutineTargetCodec.kt               # Multi-line header scanner + [log_metric=...] codec
│   ├── DailyRoutineCodec.kt                # Optional item targets + multi-metric pre-fill & progress
│   └── AvailableBlockCodec.kt              # Multi-metric block pre-fill & Edit Session hydration
├── ui/
│   ├── SessionDraft.kt                     # BlockDraft.notes for persisting [log_metric=...]
│   ├── AppViewModel.kt                     # Pass bd.notes into SessionBlock
│   ├── ProgressAnalytics.kt                # Bodyweight reps & metricValue performance extraction
│   └── screens/
│       ├── RoutineItemTargetsEditor.kt     # NEW: Reusable inline target editor & formatters
│       ├── LibraryScreen.kt                # DailyRoutineComposerState & DailyRoutineComposerSheet/Card
│       ├── SimplifiedBlockLogComponents.kt # Metric switcher, Reps+Weight & metric-value tiles
│       ├── SessionEditor.kt                # Routine expansion + template sync target preservation
│       ├── HistoryScreen.kt                # Multi-metric setToken rendering
│       └── ProgressScreen.kt               # Constituent item.unit drill-down & set pill rendering

app/src/test/java/com/fractanomics/crosstraining/
├── util/
│   ├── RoutineTargetCodecTest.kt
│   ├── DailyRoutineCodecTest.kt
│   └── AvailableBlockCodecTest.kt
└── ui/screens/
    ├── LibraryDailyRoutinesTest.kt
    └── SessionEditorDailyRoutineAndExerciseTest.kt

mcp-server/
├── src/
│   ├── schemas.ts                          # [log_metric=...] helpers & flat Zod schemas
│   └── store.ts                            # Optional routine targets & multi-metric session logging/progress
└── test/
    └── mcp-server.test.ts                  # Contract, schema flatness, and multi-metric E2E tests
```

**Structure Decision**: Reuse the dual-project Android (`app/`) + Remote MCP Connector (`mcp-server/`) structure with a focused new UI helper file `RoutineItemTargetsEditor.kt` to keep `LibraryScreen.kt` modular and maintainable.

## Complexity Tracking

No Constitution violations — zero schema migrations, zero new dependencies, zero extra projects.
