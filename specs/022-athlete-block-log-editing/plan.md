# Implementation Plan: Unified Athlete Block Log Editing & PR Reconciliation

**Branch**: `feat/022-athlete-block-log-editing` | **Date**: 2026-11-15 | **Spec**: [spec.md](./spec.md)  
**Input**: Feature specification from `/specs/022-athlete-block-log-editing/spec.md`

## Summary

Deliver `SPEC-022` (`SPEC-BLOCK-LOG-EDIT-01` + `SPEC-MCP-BLOCKS-02`) by uniting **Log Session** and **History -> Edit** around a shared `SimplifiedBlockLogCard` architecture, live set-to-PR weight auto-sync protected by manual override tracking (`isCustomWeightOverride`), atomic Room `RepMax` reconciliation (in-place weight/date/cycle/block updates + deletion cleanup + parent `AvailableBlock` `[ab_prs=...]` inheritance), immediate cloud sync uploads on session mutations, and full MCP/Firestore parity (`update_block_session_log` + `RepMaxRecord.deletedAtMillis` tombstones).

## Technical Context

**Language/Version**: Kotlin (JVM 17, `compileSdk 35`, `minSdk 26`), TypeScript / Node.js 22 (`mcp-server`)  
**Primary Dependencies**: Jetpack Compose Material 3, Room SQLite (`v10`), Firebase Firestore/Auth, Express + `@modelcontextprotocol/sdk` + `zod` (`mcp-server`)  
**Storage**: Room SQLite (`v10`, no migration) + Firestore `/environments/{env}/users/{uid}/data/{collection}` (unchanged `firestore.rules`)  
**Testing**: JUnit4 + Robolectric / kotlinx-coroutines-test (`testDebugUnitTest`), PowerShell Pester (`Invoke-ScriptTests.ps1`), Android Lint (`lintDebug`), TypeScript compiler + Mocha (`npm run build && npm test`)  
**Target Platform**: Android mobile (360dp+ viewports up to `fontScale = 1.3f`) + Cloud Run Remote MCP Server  
**Project Type**: Mobile Android app + Remote MCP Node.js service  
**Constraints**: No emulator / Maestro E2E runs; Room stays `v10`; `firestore.rules` untouched; flat MCP schemas (`0` `$ref`, `0` `exclusiveMinimum`)

## Constitution Check

- **Principle I (MVVM & UDF)**: PASS — UI components remain stateless/state-holder scoped; `SessionDraft` carries commands through `AppViewModel` -> `Repository` -> Room/Firestore.
- **Principle II (Pure Analytics & Display-Unit Consistency)**: PASS — Hydration and PR comparison (`evaluateTrackedPrChip`, `bestStoredRepMaxWeight`) are pure functions in `AvailableBlockCodec`; weight comparisons normalize cleanly.
- **Principle III (Test-First & 100% CI Parity)**: PASS — Full unit, codec, repository, sync, UI state, lint, script, and MCP tests required before PR open/merge.
- **Principle IV (Responsive Mobile UX & Accessibility)**: PASS — `>= 48.dp` touch targets (`minimumInteractiveComponentSize()`), `4`-column / `2`-column responsive tile grid, `imePadding()`, `fontFeatureSettings = "tnum"`, `#0B6E3F`/`#2ECC71` contrast, merged TalkBack semantics.
- **Principle V (Offline-First, Idempotent Sync & Env Isolation)**: PASS — Natural-key `rep_maxes` tombstones prevent resurrection; Room remains `v10`; no machine-specific Gradle paths.
- **Principle VII (Reliable, Atomic & Client-Compatible MCP)**: PASS — `34` tools with flat schemas, `IDEMPOTENT_WRITE_ANNOTATIONS`, atomic whole-collection transactions, active cycle preservation.

## Project Structure

### Documentation (`specs/022-athlete-block-log-editing/`)

```text
specs/022-athlete-block-log-editing/
├── spec.md
├── research.md
├── data-model.md
├── quickstart.md
├── plan.md
├── contracts/
│   └── mcp-block-log-editing.md
├── checklists/
│   ├── requirements.md
│   └── ux-and-reconciliation.md
└── tasks.md
```

### Source Code Architecture (`app/` & `mcp-server/`)

1. **Codec & Draft Layer**:
   - `app/src/main/java/com/fractanomics/crosstraining/util/AvailableBlockCodec.kt`: `excludeSessionId` parameter + `hydrateSessionBlocksForEditor(...)`
   - `app/src/main/java/com/fractanomics/crosstraining/ui/SessionDraft.kt`: `reconcileRepMaxes` + `inheritedBlockTrackedPrs`
2. **Repository, ViewModel & Sync Layer**:
   - `app/src/main/java/com/fractanomics/crosstraining/data/Repository.kt`: `updateSession(..., reconcileRepMaxes)` atomic PR reconciliation
   - `app/src/main/java/com/fractanomics/crosstraining/ui/AppViewModel.kt`: pass `reconcileRepMaxes`, merge `inheritedBlockTrackedPrs` onto parent `AvailableBlock`, trigger `UserCloudSyncManager.uploadUserData`
   - `app/src/main/java/com/fractanomics/crosstraining/data/firebase/UserCloudSyncManager.kt`: Step 5 natural-key dedup & tombstone deletion (`repo.deleteRepMax`)
3. **UI Layer**:
   - `app/src/main/java/com/fractanomics/crosstraining/ui/screens/SimplifiedBlockLogComponents.kt`: extracted simplified block card, tiles, sheets, `isEditMode`, live set-to-PR auto-sync, override protection, accessibility & `[Undo]` Snackbar
   - `app/src/main/java/com/fractanomics/crosstraining/ui/screens/SessionEditor.kt`: edit hydration, `Load Block ▾`, `BackHandler(enabled = isDirty)` discard guard, Save reconciliation wiring
4. **MCP Server Layer**:
   - `mcp-server/src/schemas.ts`, `mcp-server/src/store.ts`, `mcp-server/src/mcp.ts`: `UpdateBlockSessionLogInputSchema`, `RepMaxRecord.deletedAtMillis` tombstones, `update_block_session_log` (#34)
5. **Verification Layer**:
   - Android tests (`AvailableBlockCodecTest.kt`, `AvailableBlockRepositoryAndSyncTest.kt`, `SessionEditorBlockLogEditTest.kt`)
   - MCP tests (`mcp-server/test/mcp-server.test.ts`)

## Complexity Tracking

No Constitution violations require waiver or exception.
