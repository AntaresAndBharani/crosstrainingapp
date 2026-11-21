# Implementation Plan: Compact Complex Session Logging (`SPEC-028` / `SPEC-COMPLEX-LOGGING-02`)

**Branch**: `feat/028-compact-complex-logging` | **Date**: 2026-11-21 | **Spec**: [spec.md](file:///c:/Users/rogal/workspaces/ws-gym/crosstrainingapp/specs/028-compact-complex-logging/spec.md)

**Input**: Feature specification from `/specs/028-compact-complex-logging/spec.md`

## Summary

Replace the tall per-exercise stacked set cards and repeated metric switcher bars inside multi-exercise Complex cards (`SimplifiedBlockLogCard` when `itemKind == SessionItemKind.BLOCK && exerciseSections.size >= 2`) with `CompactComplexLogCard`: a high-density unified `S1..SN` set table displaying skip-aware movement status micro-badges (`[① 1]`, `[② —]`) and linked shared weight inputs, an inline `SetExerciseOverrideDrawer` (`AnimatedVisibility`) for per-set `Active`/`Skip` toggles, rep/metric target edits, and per-exercise weight splitting (`Mixed` + `"Re-link all"` + 4s `[Undo]`), plus a collapsible secondary `By ex. ▸` view hosting metric unit selection (`3A`). Backed by zero-migration canonical round alignment (`groupIndex` + `hasNoLoggedSets`) in `AvailableBlockCodec.kt` and `shared_set_weights_kg` parity in `mcp-server`.

## Technical Context

**Language/Version**: Kotlin (JVM 17, Android compileSdk 35 / minSdk 26) & TypeScript (Node.js 22, `mcp-server/`)  
**Primary Dependencies**: Jetpack Compose, Material 3, Room SQLite, Cloud Firestore, Zod + `@modelcontextprotocol/sdk`  
**Storage**: Room SQLite v10 (`SessionBlock`, `BlockSet`, `Routine`, `RoutineBlock`, `RepMax`) synced with Cloud Firestore (`/environments/{env}/users/{uid}/data/{collection}`) — zero schema changes  
**Testing**: JUnit 4 (`.\gradlew.bat testDebugUnitTest --no-daemon`), Android Lint (`.\gradlew.bat lintDebug --no-daemon`), Pester (`.\scripts\tests\Invoke-ScriptTests.ps1`), Node test runner (`npm run build && npm test` in `mcp-server/`)  
**Target Platform**: Android phones (`360dp`–`599dp`), Android foldables/tablets (`600dp+`), and Remote MCP clients (Claude, Gemini, ChatGPT)  
**Project Type**: Mobile Android app + TypeScript MCP backend  
**Performance Goals**: Single-screen rendering of a 3-exercise, 6-set Complex (`~520dp`), 1-action shared weight update across active movements, instant IME traversal (`S1..SN`)  
**Constraints**: Zero Room migrations, `>= 48.dp` touch targets, WCAG AA Light/Dark contrast, `fontScale` support up to `2.0f`, `0` `$ref` / `0` `exclusiveMinimum` across all `45` MCP tools  
**Scale/Scope**: Touches Android codec (`AvailableBlockCodec.kt`), session state & UI (`SimplifiedBlockLogComponents.kt`, `CompactComplexLogComponents.kt`, `SessionEditor.kt`, `strings.xml`), `mcp-server` (`schemas.ts`, `store.ts`, `mcp.ts`), and unit/integration tests

## Constitution Check

*GATE: Must pass before Phase 0 research. Re-check after Phase 1 design.*

- **I. MVVM & Unidirectional Data Flow (UDF)**: PASS — `CompactComplexLogCard` reads immutable snapshot state/derived `SharedSetWeightSummary` and emits actions back through `LoadedAvailableBlockState` and `SessionEditorBody` callbacks.
- **II. Pure Deterministic Analytics & Display-Unit Consistency**: PASS — Weight equality and `Mixed` detection use `normalizeWeight2dp` (`BigDecimal.setScale(2, RoundingMode.HALF_UP)`); PR candidate resolution ignores skipped rounds deterministically.
- **III. Test-First & 100% CI Parity**: PASS — All pure state transitions, hydration round-trips, accessibility/layout helpers, and MCP `shared_set_weights_kg` logic are covered by `CompactComplexLoggingTest.kt` and `mcp-server/test/mcp-server.test.ts` alongside full `testDebugUnitTest`, `lintDebug`, `Invoke-ScriptTests.ps1`, and `npm test`.
- **IV. Responsive Mobile UX & Accessibility**: PASS — `360dp` column budget (`32dp` + `weight(1f)` + `94dp` + `40dp`), `widthIn(max = 640.dp)`, `fontScale >= 1.4f` 2-line reflow, `>= 48.dp` touch targets, `Role.Switch`, merged TalkBack `contentDescription` labels, and WCAG AA Light/Dark token contrast verified.
- **V. Offline-First, Idempotent Synchronization & Environment Isolation**: PASS — Zero Room schema changes (`v10`); skipped sets omit `BlockSet` rows and preserve 1-based `groupIndex` round tags across Room and Firestore.
- **VI. Agentic Collaboration & Governance**: PASS — Follows `feat/028-*` feature branch, `CHANGELOG.md`, and CI parity requirements.
- **VII. Reliable, Atomic & Client-Compatible MCP Communication**: PASS — New `shared_set_weights_kg` and `exercise_targets` schemas use fresh Zod schema builders with `0` `$ref` and `0` `exclusiveMinimum`, preserving exact `45` registered tools.

## Project Structure

### Documentation (this feature)

```text
specs/028-compact-complex-logging/
├── plan.md
├── research.md
├── data-model.md
├── quickstart.md
├── contracts/
│   └── mcp-complex-shared-weights.md
├── checklists/
│   ├── requirements.md
│   └── compact-complex-logging.md
└── tasks.md
```

### Source Code (repository root)

```text
app/src/main/java/com/fractanomics/crosstraining/
├── util/AvailableBlockCodec.kt
└── ui/screens/
    ├── SimplifiedBlockLogComponents.kt
    ├── CompactComplexLogComponents.kt
    └── SessionEditor.kt
app/src/main/res/values/strings.xml
app/src/test/java/com/fractanomics/crosstraining/ui/screens/
└── CompactComplexLoggingTest.kt
mcp-server/src/
├── schemas.ts
├── store.ts
└── mcp.ts
mcp-server/test/
└── mcp-server.test.ts
```

**Structure Decision**: Extends the existing Android MVVM/UI package (`ui/screens`) and `util/AvailableBlockCodec.kt` by extracting `CompactComplexLogComponents.kt` alongside `SimplifiedBlockLogComponents.kt` to keep components focused and single-purpose while integrating cleanly into `SessionEditor.kt` and `mcp-server/`.

## Complexity Tracking

No Constitution violations — zero new dependencies, zero Room schema migrations, and zero breaking MCP schema changes.
