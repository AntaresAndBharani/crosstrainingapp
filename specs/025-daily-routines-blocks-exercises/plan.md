# Implementation Plan: Daily Routines Library, Unified Block & Exercise Tracking, Routine Progress & MCP Parity (`SPEC-025`)

**Branch**: `feat/025-daily-routines-blocks-exercises` | **Date**: 2026-11-17 | **Spec**: [spec.md](spec.md)  
**Input**: Feature specification from `/specs/025-daily-routines-blocks-exercises/spec.md`

## Summary

Restore **Daily Routines** as the third top-level Library section (`Routines ({count}) | Blocks ({count}) | Exercises ({count})`) backed by a fast composer sheet combining existing **Available Blocks** and standalone **Exercises**; unify **Log Session** and **Edit Session** (`Load Daily Routine ▾`, `+ Add Block`, `+ Add Exercise`) so standalone Exercises track weights and PRs using the exact same simplified set-tile card (`SimplifiedExerciseLogCard`) as Blocks with unified cross-app history, optional mid-session routine template sync, and a single-line `Rest` chip fix in `DailyCheckInCard`; add a **`By routine`** filter chip to **Progress & Goals** displaying an `All Items Overview` of a routine's constituent Blocks and Exercises plus 1-tap drill-down into KPIs, Weight Evolution charts, PRs, and Set History; and provide full **Remote MCP** and **Cloud Sync** parity (`list_daily_routines`, `upsert_daily_routine`, `delete_daily_routine`, `log_daily_routine_session`, `get_daily_routine_progress`) without requiring a Room database migration.

## Technical Context

**Language/Version**: Kotlin (JVM 17, Android Gradle Kotlin DSL) + TypeScript (Node.js 22, ESM)  
**Primary Dependencies**: Jetpack Compose Material 3, Navigation Compose, Room SQLite (schema v10), Firebase Auth/Firestore, `@modelcontextprotocol/sdk`, Zod, Express  
**Storage**: Room SQLite (`routines`, `routine_blocks`, `session_blocks`, `block_sets`, `rep_maxes`) + Cloud Firestore `/environments/{env}/users/{uid}/data/{collection}`  
**Testing**: JUnit 4 + Kotlin Coroutines Test (`.\gradlew.bat testDebugUnitTest --no-daemon`), PowerShell Pester/static suite (`.\scripts\tests\Invoke-ScriptTests.ps1`), Android Lint (`.\gradlew.bat lintDebug --no-daemon`), Vitest (`mcp-server` `npm test`)  
**Target Platform**: Android (`compileSdk 35`, `minSdk 26`) + Node.js Cloud Run MCP Server  
**Project Type**: Mobile App + Remote MCP API (`app/` + `mcp-server/`)  
**Performance Goals**: Pure deterministic in-memory codecs and progress aggregations computed in O(sessions × blocks) with Compose `remember` memoization; zero UI-thread blocking  
**Constraints**: Zero Room schema migration (remain at v10); zero `$ref` and zero `exclusiveMinimum` in MCP JSON Schemas; `360dp` compact viewport support up to `fontScale = 1.3f` with `48.dp` touch targets  
**Scale/Scope**: 4 feature modules (`DailyRoutineCodec`, `LibraryScreen`, `SessionEditor` + `SimplifiedBlockLogComponents` + `DailyCheckInCard`, `ProgressScreen`, and `mcp-server/{schemas,store,mcp}.ts`)

## Constitution Check

*GATE: Must pass before Phase 0 research. Re-check after Phase 1 design.*

1. **I. MVVM & UDF**: PASS — `AppViewModel` exposes immutable `StateFlow<List<DailyRoutine>>` and pending navigation triggers; UI screens remain stateless relative to pure analytics (`DailyRoutineCodec`, `ProgressAnalytics`).
2. **II. Pure Deterministic Analytics & Display-Unit Consistency**: PASS — `DailyRoutineCodec.computeDailyRoutineProgress` and `resolveLastExerciseSession` are pure Kotlin functions accepting explicit `sessions`, `repMaxes`, `exercises`, and `weightUnit`.
3. **III. Test-First & 100% CI Parity (NON-NEGOTIABLE)**: PASS — Dedicated JVM unit test suites (`DailyRoutineCodecTest`, `LibraryDailyRoutinesTest`, `SessionEditorDailyRoutineAndExerciseTest`, `DailyRoutineProgressTest`) plus Vitest MCP integration tests and full pre-PR verification gates planned.
4. **IV. Responsive Mobile UX & Accessibility**: PASS — Concise 3-tab `TabRow`, `FlowRow` on `DailyCheckInCard`, `imePadding()` on all bottom sheets, `fontFeatureSettings = "tnum"` on numeric readouts, `48.dp` minimum touch targets, non-color-redundant trend indicators (`↗`/`→`/`↘`), and merged TalkBack semantics.
5. **V. Offline-First, Idempotent Synchronization & Environment Isolation**: PASS — Zero Room schema changes at v10; namespace-scoped natural keys (`dr:` vs `ab:`) across `Repository`, `UserCloudSyncManager`, and `mcp-server`; tombstone deletions never resurrect.
6. **VI. Agentic Collaboration & CI Governance**: PASS — No workflow breakage; all static PowerShell workflow checks remain intact; `CHANGELOG.md` updated under `## [Unreleased]`.
7. **VII. Reliable, Atomic & Client-Compatible MCP Communication**: PASS — All 5 new MCP tools declare accurate `ToolAnnotations`, emit flat schemas free of `$ref` and `exclusiveMinimum`, execute atomically inside `runWithUserMutationLock`, and resolve active cycle dynamically.

*Post-Phase 1 Re-Check*: All 7 gates remain satisfied by [data-model.md](data-model.md) and [contracts/daily-routines-ui-and-mcp-contract.md](contracts/daily-routines-ui-and-mcp-contract.md).

## Project Structure

### Documentation (this feature)

```text
specs/025-daily-routines-blocks-exercises/
├── plan.md
├── research.md
├── data-model.md
├── quickstart.md
├── checklists/
│   ├── requirements.md
│   └── ux-data-and-mcp.md
├── contracts/
│   └── daily-routines-ui-and-mcp-contract.md
└── tasks.md
```

### Source Code (repository root)

```text
app/src/main/java/com/fractanomics/crosstraining/
├── data/
│   ├── Repository.kt
│   └── firebase/UserCloudSyncManager.kt
├── ui/
│   ├── AppViewModel.kt
│   ├── SessionDraft.kt
│   ├── components/DailyCheckInCard.kt
│   ├── navigation/AppNavigation.kt
│   └── screens/
│       ├── LibraryScreen.kt
│       ├── ProgressScreen.kt
│       ├── SessionEditor.kt
│       └── SimplifiedBlockLogComponents.kt
└── util/
    ├── AvailableBlockCodec.kt
    ├── DailyRoutineCodec.kt
    └── RoutineTargetCodec.kt

app/src/test/java/com/fractanomics/crosstraining/
├── util/DailyRoutineCodecTest.kt
├── data/DailyRoutineRepositoryAndSyncTest.kt
└── ui/screens/
    ├── LibraryDailyRoutinesTest.kt
    ├── SessionEditorDailyRoutineAndExerciseTest.kt
    ├── DailyRoutineProgressTest.kt
    └── ProgressScreenLazyRowAutoScrollTest.kt

mcp-server/
├── src/
│   ├── schemas.ts
│   ├── store.ts
│   └── mcp.ts
└── test/mcp-server.test.ts
```

**Structure Decision**: Multi-platform mobile + remote MCP architecture matching the repository's existing `app/` (Android Jetpack Compose + Room) and `mcp-server/` (Node.js/TypeScript MCP service) layout.

## Complexity Tracking

No Constitution violations require justification.
