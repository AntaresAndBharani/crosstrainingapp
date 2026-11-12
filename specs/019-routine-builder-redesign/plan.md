# Implementation Plan: Daily Routine Builder Redesign & Multi-Metric Per-Set Targets

**Branch**: `feat/019-routine-builder-redesign` | **Date**: 2026-11-12 | **Spec**: [spec.md](./spec.md)

**Input**: Feature specification from `/specs/019-routine-builder-redesign/spec.md`

## Summary

Redesign the **New / Edit Daily Routine** experience in the CrossTraining Android app from a non-collapsible wall of text inputs into a fast, tactile **4-tier collapsible visual builder** (`Routine Info` → `Routine Block` with exclusive block expansion → `Block Setup` with visual type/format chips and format-driven fixed integer `Sets` → `Exercises` with unified inline search/create and per-exercise `Reps | Calories | Time | Meters` Fixed or `Per-Set` targets including `0 / Skip`). Maintain 100% lossless zero-migration compatibility across Room SQLite (`v10`), Library Routine Cards, Workout Logging (`Load Routine...` expanding multi-exercise blocks into `SubBlockGroup` entries with active sets and preserved 1-based round indices), CSV Backups, Cloud Sync, Workout Share Codes, and the Remote MCP Server (`list_routines` / `upsert_routine` with flat `$ref`-free Zod schemas).

## Technical Context

**Language/Version**: Kotlin (JVM 17) for Android app; TypeScript 5.7 (Node.js >= 20) for `mcp-server/`

**Primary Dependencies**: Jetpack Compose, Material 3 (`ModalBottomSheet`, `FilterChip`, `AnimatedVisibility`, `FlowRow`), AndroidX Lifecycle (`collectAsStateWithLifecycle`), Room SQLite (`v10`), Firebase Firestore, `@modelcontextprotocol/sdk` ^1.11.0, `zod` ^3.24.2

**Storage**: Room SQLite (`routines`, `routine_blocks`, `exercises` at schema `v10` — zero database migration required via lossless `[rb_v2=...]` header in `RoutineBlock.notes` + human-readable `targetRepsScheme`) synchronized with Cloud Firestore `/environments/{env}/users/{uid}/data/{routines,exercises}`

**Testing**: JUnit 4 + Kotlin Coroutines Test (`.\gradlew.bat testDebugUnitTest --no-daemon`), PowerShell Pester/script suite (`.\scripts\tests\Invoke-ScriptTests.ps1`), Android Lint (`.\gradlew.bat lintDebug --no-daemon`), Node.js test runner (`npm run build && npm test` in `mcp-server/`)

**Target Platform**: Android (`minSdk 26`, `compileSdk 35`) + Google Cloud Run Remote MCP Server

**Project Type**: Mobile Application (Android Jetpack Compose) + Remote MCP Connector (`mcp-server/`)

**Performance Goals**: 60 fps expand/collapse animations (`AnimatedVisibility`), instant (`<16ms`) pure in-memory reducer updates for steppers and `Per-Set` grids up to 20 sets, zero main-thread Room I/O

**Constraints**: Zero Room schema version bump (`version = 10` preserved); `48.dp` minimum touch targets; zero text clipping/mid-word wrapping on `360dp` viewports up to `2.0x` font scale; non-color visual indicators + TalkBack semantics for `0 (Skip)` sets; zero `$ref` and zero `exclusiveMinimum` in MCP JSON Schemas

**Scale/Scope**: Touches Routine domain codec, Routine Builder sheet & Library cards, `SessionEditor` routine loader, `Repository` / `UserCloudSyncManager` sequence-preserving exercise CSV handling, and `mcp-server` routine schemas/store/tools

## Constitution Check

*GATE: Must pass before Phase 0 research. Re-check after Phase 1 design.*

| Principle | Pre-Research Gate | Post-Design Re-Check | Evidence / Enforcement |
| :--- | :--- | :--- | :--- |
| **I. MVVM & Unidirectional Data Flow** | PASS | PASS | Pure immutable `RoutineBuilderUiState` & reducer functions in `RoutineBuilderSheet.kt`; `AppViewModel` mediates `saveRoutineWithBlocks` and `getOrCreateExerciseForRoutine` via `Repository`. |
| **II. Pure Deterministic Analytics & Display-Unit Consistency** | PASS | PASS | `RoutineTargetCodec` and `expandRoutineIntoSessionBlocks` are 100% pure deterministic functions over integer target vectors (`reps`, `cal`, `s`, `m`) with zero floating-point drift. |
| **III. Test-First & 100% CI Parity** | PASS | PASS | Dedicated Android JUnit tests (`RoutineTargetCodecTest`, `RoutineBuilderUiStateTest`, `RoutineSessionExpansionTest`) + `mcp-server` unit & schema tests + `lintDebug` + `Invoke-ScriptTests.ps1`. |
| **IV. Responsive Mobile UX & Accessibility** | PASS | PASS | `360dp` non-wrapping metric selector (`LazyRow` fallback at `fontScale >= 1.4f`), responsive `2 / 4 / 6`-column `Per-Set` grid, `48.dp` touch targets, dashed border + `(Skip)` text, merged TalkBack semantics. |
| **V. Offline-First, Idempotent Sync & Environment Isolation** | PASS | PASS | Room `v10` remains primary source of truth with zero schema migration; `saveRoutineWithBlocks` & `deleteRoutine` trigger cloud upload; `remapExerciseIdsCsv` & `resolveExerciseCsv` preserve structured sequence repeats without breaking deduplication; `gradle.properties` untouched. |
| **VI. Agentic Collaboration & Automated PR Review** | PASS | PASS | Branch `feat/019-routine-builder-redesign`, Keep-a-Changelog update, clean PR description, and remote CI verification (`build.yml` + `pr-review.yml`). |
| **VII. Reliable, Atomic & Client-Compatible MCP Communication** | PASS | PASS | Fresh Zod schema factories (`makeRoutineBlockExerciseInputSchema`, `makeRoutineBlockInputSchema`) emit zero `$ref` and zero `exclusiveMinimum`; `upsertRoutine` runs inside `runWithUserMutationLock(["exercises", "routines"])`. |

## Project Structure

### Documentation (this feature)

```text
specs/019-routine-builder-redesign/
├── plan.md                          # This implementation plan
├── spec.md                          # Feature specification (US1-US4, FR-001..FR-014)
├── research.md                      # Phase 0 architectural & codec decisions
├── data-model.md                    # Phase 1 domain types, [rb_v2=...] grammar & state invariants
├── quickstart.md                    # Runnable verification & walkthrough guide
├── checklists/
│   ├── requirements.md              # Spec quality checklist
│   └── routine-builder.md           # Domain UX, sync & MCP review checklist
├── contracts/
│   ├── mcp-routine-tools.md         # MCP list_routines / upsert_routine contract
│   └── ui-state-contract.md         # 4-tier accordion, responsive & accessibility contract
└── tasks.md                         # Phase 2 dependency-ordered task breakdown (/speckit-tasks)
```

### Source Code (repository root)

```text
app/src/main/java/com/fractanomics/crosstraining/
├── data/
│   ├── Repository.kt                        # Preserve repeated sequence IDs for [rb_v2=...] blocks during dedup
│   ├── firebase/
│   │   └── UserCloudSyncManager.kt          # Preserve repeated sequence IDs for [rb_v2=...] blocks on sync download/recovery
│   └── model/
│       └── Enums.kt                         # Add GYMNASTICS("Gymnastics") to BlockKind
├── ui/
│   ├── AppViewModel.kt                      # Add cloud-synced routine save/delete helpers & one-tap getOrCreateExercise callback
│   └── screens/
│       ├── LibraryScreen.kt                 # Wire RoutineBuilderSheet & update RoutineCard / RoutineBlockCard badges + cleanNotes
│       ├── RoutineBuilderSheet.kt           # NEW: 4-tier collapsible Routine Builder UI + pure RoutineBuilderUiState reducers
│       └── SessionEditor.kt                 # Wire expandRoutineIntoSessionBlocks forSubBlockGroup + active non-zero sets
└── util/
    └── RoutineTargetCodec.kt                # NEW: Pure [rb_v2=...] codec, format defaults, target summaries & legacy parser

app/src/test/java/com/fractanomics/crosstraining/
├── util/
│   └── RoutineTargetCodecTest.kt            # NEW: Unit tests for codec, format defaults, legacy parsing & summaries
└── ui/screens/
    ├── RoutineBuilderUiStateTest.kt         # NEW: Unit tests for 4-tier accordion, exclusive expansion, set sync, cache & validation
    └── RoutineSessionExpansionTest.kt       # NEW: Unit tests for multi-exercise SubBlockGroup & 0 (Skip) round preservation

mcp-server/
├── src/
│   ├── schemas.ts                           # Add GYMNASTICS, RoutineTargetMetric/Mode factories & [rb_v2=...] TS codec
│   ├── store.ts                             # Enrich listRoutines & upsertRoutine with structured per-exercise targets
│   └── mcp.ts                               # Update tool descriptions for structured per-exercise routine targets
└── test/
    └── mcp-server.test.ts                   # Add routine mixed-metric, 0 (Skip), legacy fallback & schema flat-JSON tests
```

**Structure Decision**: Dual-target update across existing Android `app/` module and `mcp-server/` TypeScript package, isolating pure target encoding/decoding in `RoutineTargetCodec.kt` (mirrored in `mcp-server/src/schemas.ts`) and isolating the 4-tier visual builder in `RoutineBuilderSheet.kt` so `LibraryScreen.kt` shrinks and becomes cleaner.

## Complexity Tracking

> Zero Constitution Check violations — no complexity exemptions required.
