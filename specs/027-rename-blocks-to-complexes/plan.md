# Implementation Plan: Rename Blocks to Complexes (`SPEC-027`)

**Branch**: `feat/027-rename-blocks-to-complexes` | **Date**: 2026-11-21 | **Spec**: [spec.md](file:///c:/Users/rogal/workspaces/ws-gym/crosstrainingapp/specs/027-rename-blocks-to-complexes/spec.md)

**Input**: Feature specification from `/specs/027-rename-blocks-to-complexes/spec.md`

## Summary

Rename user-facing `Block` / `Available Block` terminology to `Complex` / `Complexes` across the 6 primary Android user surfaces (`LibraryScreen`, `QuickCreateBlockSheet`, `DailyRoutineComposerSheet` / `RoutineItemTargetsEditor`, `SessionEditor` / `SimplifiedBlockLogComponents`, `MultiMovementPrPickerSheet`, and `ProgressScreen` excluding Fat Loss KPI) while resolving compact `360dp` and high-`fontScale` clipping defects (`ScrollableTabRow` at `>= 1.2f`, tab-scoped search placeholders, `maxLines = 2` Complex Name inputs, eyebrow badge layout in `Edit Daily Routine`, vertical button stacking at `fontScale >= 1.4f`, and strict name + >=1 exercise validation in `New/Edit Complex`). Simultaneously expose 6 Complex-first MCP tools and schema/response aliases (`item_type: "COMPLEX"`, `complex_id`, `complex_title`, etc.) while keeping all persisted Room/Firestore schemas, format discriminators, and legacy Block MCP endpoints 100% intact for zero-migration backward compatibility.

## Technical Context

**Language/Version**: Kotlin (JVM 17) on Android (`compileSdk 35`, `minSdk 26`) + TypeScript (Node.js >= 20, ES Modules) for `mcp-server/`  
**Primary Dependencies**: Jetpack Compose (Material 3), Room (KSP), Navigation Compose, Firebase/Firestore Sync (`app/`), `@modelcontextprotocol/sdk`, `express`, `zod`, `firebase-admin` (`mcp-server/`)  
**Storage**: Room SQLite (`AppDatabase`) + Cloud Firestore (`/environments/{env}/users/{uid}/data/{collection}`); zero schema or marker changes  
**Testing**: JUnit4 / Kotlin Coroutines Test (`.\gradlew.bat testDebugUnitTest --no-daemon`), Android Lint (`.\gradlew.bat lintDebug --no-daemon`), PowerShell Pester/script tests (`.\scripts\tests\Invoke-ScriptTests.ps1`), Node test runner (`npm run build && npm test` in `mcp-server/`)  
**Target Platform**: Android mobile (`360dp+` compact phones to foldables, `fontScale` up to `2.0f`) & Remote MCP Connector (Gemini, Claude, ChatGPT)  
**Project Type**: Mobile Android application + Node.js/TypeScript Remote MCP Server  
**Performance Goals**: Zero runtime overhead; pure deterministic UI state/format helpers; atomic Firestore transactions preserved  
**Constraints**: Zero database/Firestore migrations; zero `$ref` or `exclusiveMinimum` in MCP JSON Schemas; no emulator or Maestro runs in verification; secondary/legacy surfaces explicitly preserved  
**Scale/Scope**: 6 primary Android UI files/functions, 3 MCP server TypeScript modules (`schemas.ts`, `store.ts`, `mcp.ts`), plus corresponding Android & MCP test suites

## Constitution Check

*GATE: Passed pre-research and post-design.*

1. **I. MVVM & Unidirectional Data Flow (UDF)**: PASS — All terminology and layout fixes live in presentation/UI composables and pure formatting/validation helpers; ViewModel `StateFlow` architecture and repository boundaries remain untouched.
2. **II. Pure Deterministic Analytics & Display-Unit Consistency**: PASS — No arithmetic or analytics formulas are modified; pure formatting/layout predicates (`formatLibraryTabLabels`, `shouldUseScrollableLibraryTabs`, `shouldStackCompactActions`, `canSaveComplexEditor`, `complexEditorValidationError`, `formatSavedBlocksConfirmLabel`) remain deterministic and unit-tested.
3. **III. Test-First & 100% CI Parity (NON-NEGOTIABLE)**: PASS — Android unit tests (`LibraryDailyRoutinesTest.kt`, `SessionEditorAddBlockTest.kt`), Android Lint (`lintDebug`), PowerShell script tests (`Invoke-ScriptTests.ps1`), and MCP TypeScript build + tests (`mcp-server.test.ts`) will all be updated and verified at 100% pass.
4. **IV. Responsive Mobile UX & Accessibility**: PASS — Explicitly resolves `360dp` tab & placeholder clipping, `fontScale >= 1.2f` `ScrollableTabRow` fallback, `fontScale >= 1.4f` action stacking, Row 1/Row 2 eyebrow layout for `Edit Daily Routine` titles, `maxLines = 2` name fields, and updated TalkBack `contentDescription` labels.
5. **V. Offline-First, Idempotent Synchronization & Environment Isolation**: PASS — Zero Room migrations, zero Firestore rule/marker modifications (`AVAILABLE_BLOCK`, `DR_BLOCK`, `[ab_prs=...]`, `[dr_item=BLOCK:...]`), preserving full sync and historical session idempotency.
6. **VI. Agentic Collaboration, Automated PR Review & Self-Healing Governance**: PASS — No CI workflow contracts broken; static workflow tests run via `Invoke-ScriptTests.ps1`.
7. **VII. Reliable, Atomic & Client-Compatible MCP Communication**: PASS — Every new Complex tool declares explicit `ToolAnnotations`, uses factory functions in `schemas.ts` guaranteeing zero `$ref` and zero `exclusiveMinimum`, keeps tool names snake_case `<= 60` chars, and preserves atomic Firestore transactions and backward compatibility.

## Project Structure

### Documentation (this feature)

```text
specs/027-rename-blocks-to-complexes/
├── spec.md                                      # Feature specification
├── checklists/
│   ├── requirements.md                          # Spec readiness checklist
│   └── complex-terminology-and-ux.md            # Domain quality checklist (Phase 6)
├── plan.md                                      # This file
├── research.md                                  # Phase 0 architectural decisions
├── data-model.md                                # UI & MCP DTO terminology mapping
├── contracts/
│   └── complexes-ui-and-mcp-contract.md         # Exact UI copy & MCP schema contracts
├── quickstart.md                                # Validation & test instructions
└── tasks.md                                     # Implementation checklist (Phase 7)
```

### Source Code (repository root)

```text
app/src/main/java/com/fractanomics/crosstraining/ui/
├── AppViewModel.kt                              # Duplicate fallback title ("Complex")
└── screens/
    ├── LibraryScreen.kt                         # Tabs, search, Complex cards, QuickCreateBlockSheet, DailyRoutineComposerSheet
    ├── RoutineItemTargetsEditor.kt              # "Complex Sets (this routine)" stepper label
    ├── SessionEditor.kt                         # "Add Complex" CTA, stacking, empty snackbar
    ├── SimplifiedBlockLogComponents.kt          # COMPLEX card headers, AddComplexToSessionSheet, Track PRs, LoadDailyRoutineSheet
    └── ProgressScreen.kt                        # By complex, Complex history, Daily Routine COMPLEX badges/chips

app/src/test/java/com/fractanomics/crosstraining/ui/screens/
├── LibraryDailyRoutinesTest.kt                  # Tab, chip, telemetry, responsive/validation helper tests
└── SessionEditorAddBlockTest.kt                 # Confirm label ("Select Complex(es)", "Add 1 Complex", "Add N Complexes")

mcp-server/
├── src/
│   ├── schemas.ts                               # Complex-first flat Zod schemas + Block compatibility fields
│   ├── store.ts                                 # Normalization, Complex>Block precedence, dual response fields
│   └── mcp.ts                                   # 6 Complex tools + 6 legacy Block aliases (45 total), instructions
└── test/
    └── mcp-server.test.ts                       # 45 tools schema/annotation checks, Complex CRUD/logging, precedence, legacy parity
```

**Structure Decision**: Reuse the existing single-module Android app (`app/`) and Remote MCP server (`mcp-server/`) structure without creating new modules or modifying database schemas.

## Complexity Tracking

No Constitution violations or complexity exceptions.
