# Implementation Plan: Unified Add Block Workflow in Edit & Log Session (`SPEC-024`)

**Branch**: `feat/024-session-add-block` | **Date**: 2026-11-15 | **Spec**: [spec.md](./spec.md)  
**Input**: Feature specification from `/specs/024-session-add-block/spec.md`

## Summary

Fix the unresponsive `+ Add Block` flow in `Edit Session` (and loaded `Log Session` / `Copy Session` states) and replace the split `LoadAvailableBlockSheet` + blank-placeholder add behavior with a unified two-tab **`AddBlockToSessionSheet`** (`Saved Blocks` and `Custom Block`). Saved blocks already in the session are visibly marked `"Already in session"` without pre-counting them in the CTA, newly added saved blocks pre-fill from their most recent prior session (`excludeSessionId = editingSessionId`) with `"Pre-filled from last session ({Date})"` shown even in `Edit Session`, custom blocks default to session-only (`Save to Available Blocks library` Switch OFF by default) and always append as simplified `SimplifiedBlockLogCard`s (`Set 1..3`), `SessionEditorBody` auto-scrolls smoothly to newly added cards via `CardLayoutBounds`, and `CompactBlockEditor` enables removal of residual legacy blocks when `loadedAvailableBlocks.isNotEmpty()`.

## Technical Context

**Language/Version**: Kotlin (JVM 17, `compileSdk 35`, `minSdk 26`)  
**Primary Dependencies**: Jetpack Compose Material 3, Room SQLite (`v10`), AndroidX Lifecycle / Coroutines  
**Storage**: Room SQLite (`v10`, zero schema migrations) + existing Firebase Firestore Cloud Sync (`firestore.rules` untouched)  
**Testing**: JUnit4 + Robolectric + Room in-memory DB (`testDebugUnitTest`), PowerShell Pester (`Invoke-ScriptTests.ps1`), Android Lint (`lintDebug`)  
**Target Platform**: Android mobile (`360dp`+ viewports up to `fontScale = 1.4f`)  
**Project Type**: Android mobile app (`app/`)  
**Performance Goals**: Zero perceptible frame lag on sheet open or auto-scroll (`repeat(2) { withFrameNanos { } }` + `animateScrollTo`)  
**Constraints**: No emulator or Maestro E2E runs (`GEMINI.md` Rule 4); Room stays `v10`; `firestore.rules` untouched; preserve all `SPEC-021`/`SPEC-022` PR reconciliation and `SPEC-023` timer functionality  
**Scale/Scope**: 4 main source files (`SimplifiedBlockLogComponents.kt`, `SessionEditor.kt`, `Repository.kt`, `AppViewModel.kt`) + 1 dedicated unit/repository test suite (`SessionEditorAddBlockTest.kt`)

## Constitution Check

*GATE: Must pass before Phase 0 research. Re-check after Phase 1 design.*

- **Principle I (MVVM & UDF)**: PASS — UI components stay declarative and delegate persistence/exercise resolution to `AppViewModel.prepareCustomSessionBlock` -> `Repository.prepareCustomAvailableBlock`.
- **Principle II (Pure Analytics & Display-Unit Consistency)**: PASS — `resolveInitialAddBlockTab`, `resolveCustomBlockTitle`, and `canSubmitCustomBlock` are pure functions; `resolveGoalCardScrollOffset` is reused without duplication.
- **Principle III (Test-First & 100% CI Parity)**: PASS — Full unit tests for helper routing, edit-mode pre-fill exclusion, custom block preparation (`saveToLibrary = false/true`), and edit-mode session update + PR reconciliation; verified with `testDebugUnitTest`, `Invoke-ScriptTests.ps1`, `lintDebug`, and `assembleDebug`.
- **Principle IV (Responsive Mobile UX & Accessibility)**: PASS — `ModalBottomSheet` applies `imePadding()`, `>= 48.dp` minimum touch targets (`minimumInteractiveComponentSize()`), responsive column/row wrapping up to `fontScale = 1.4f`, and merged TalkBack semantics on `"Save to Available Blocks library"`.
- **Principle V (Offline-First, Idempotent Sync & Env Isolation)**: PASS — Room stays `v10` (no migration), session-only custom blocks leave `routines` untouched, library-saved blocks reuse idempotent `saveAvailableBlock` + `uploadUserData`, and no machine-specific paths enter `gradle.properties`.
- **Principle VI (Agentic Collaboration & PR Governance)**: PASS — Standard feature branch `feat/024-session-add-block`, `CHANGELOG.md` updated under `## [Unreleased]`, PR opened with Mission Plan and remote CI verified.
- **Principle VII (Reliable MCP)**: N/A — No MCP server schemas or tools require modification (`mcp-server/` untouched).

## Project Structure

### Documentation (this feature)

```text
specs/024-session-add-block/
├── spec.md
├── research.md
├── data-model.md
├── quickstart.md
├── plan.md
├── contracts/
│   └── session-add-block-ui-contract.md
├── checklists/
│   ├── requirements.md
│   └── ux-and-session-editing.md
└── tasks.md
```

### Source Code (repository root)

```text
app/src/main/java/com/fractanomics/crosstraining/
├── data/
│   └── Repository.kt                                  # prepareCustomAvailableBlock(...)
├── ui/
│   ├── AppViewModel.kt                                # prepareCustomSessionBlock(...)
│   └── screens/
│       ├── SimplifiedBlockLogComponents.kt            # uiKey, AddBlockSheetTab, pure helpers, buildCustomAvailableBlockState, AddBlockToSessionSheet, SimplifiedBlockLogCard updates
│       └── SessionEditor.kt                           # AddBlockSheetOpenMode, + Add Block & Load Block triggers, auto-scroll via CardLayoutBounds, re-hydration guard, canRemove fix

app/src/test/java/com/fractanomics/crosstraining/
└── ui/screens/
    └── SessionEditorAddBlockTest.kt                   # Unit + Robolectric/Room tests for routing, pre-fill, custom blocks & edit reconciliation
```

**Structure Decision**: Reuse the existing `SimplifiedBlockLogComponents.kt` and `SessionEditor.kt` architecture alongside `CardLayoutBounds` / `resolveGoalCardScrollOffset` from `CyclesScreen.kt` (Kotlin `internal` in the same package `ui.screens`), keeping changes strictly scoped and modular.

## Complexity Tracking

No Constitution violations require waiver or exception.
