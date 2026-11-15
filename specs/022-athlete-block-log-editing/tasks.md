---
description: "Task list for SPEC-022 Unified Athlete Block Log Editing & PR Reconciliation"
---

# Tasks: Unified Athlete Block Log Editing & PR Reconciliation

**Input**: Design documents from `/specs/022-athlete-block-log-editing/`  
**Prerequisites**: `plan.md`, `spec.md`, `research.md`, `data-model.md`, `contracts/mcp-block-log-editing.md`

## Phase 1: Setup & Specification Baseline

- [x] T001 Initialize `feat/022-athlete-block-log-editing` branch and `specs/022-athlete-block-log-editing/` Spec-Kit structure
- [x] T002 Write `spec.md`, `checklists/requirements.md`, `research.md`, `data-model.md`, `contracts/mcp-block-log-editing.md`, `quickstart.md`, `plan.md`, `checklists/ux-and-reconciliation.md`

---

## Phase 2: Foundational Codec, Draft & Persistence Infrastructure

**Purpose**: Pure codec hydration, draft fields, atomic DB reconciliation, ViewModel hooks, and cloud sync tombstone support

- [x] T003 Update `bestStoredRepMaxWeight` and `resolveLastBlockSession` with `excludeSessionId: Long? = null` and implement `HydratedBlockExerciseSeed`, `HydratedAvailableBlockSeed`, `SessionBlockHydrationResult`, and `hydrateSessionBlocksForEditor` in `app/src/main/java/com/fractanomics/crosstraining/util/AvailableBlockCodec.kt`
- [x] T004 [P] Extend `SessionDraft` with `reconcileRepMaxes: Boolean = false` and `inheritedBlockTrackedPrs: Map<Long, List<TrackedPrSlot>> = emptyMap()` in `app/src/main/java/com/fractanomics/crosstraining/ui/SessionDraft.kt`
- [x] T005 Implement atomic PR reconciliation in `Repository.updateSession(session, blocks, reconcileRepMaxes = false)` preserving `RepMax.id` on in-place weight/date/cycle/block updates and deleting removed rows inside `withDatabaseTransaction` in `app/src/main/java/com/fractanomics/crosstraining/data/Repository.kt`
- [x] T006 Wire `reconcileRepMaxes`, `applyInheritedTrackedPrs`, and `UserCloudSyncManager.uploadUserData(data.realRepository)` on `saveSession`, `updateSession`, and `deleteSession` in `app/src/main/java/com/fractanomics/crosstraining/ui/AppViewModel.kt`
- [x] T007 Update `UserCloudSyncManager.downloadUserDataInternal` Step 5 (`rep_maxes`) to group remote entries by `(exerciseId, reps, normalizeWeight(weight), date)` keeping max `effectiveRemoteTs` and call `repo.deleteRepMax` for tombstoned records in `app/src/main/java/com/fractanomics/crosstraining/data/firebase/UserCloudSyncManager.kt`

---

## Phase 3: User Stories 1, 2 & 3 - Shared Simplified Block Log UI & History Edit Parity

**Purpose**: Shared UI components, auto-sync with manual override protection, Undo Snackbar, accessibility polish, and `SessionEditorScreen` integration

- [x] T008 Extract `LoadedAvailableBlockState`, `SimplifiedBlockLogCard`, `WeightOnlySetTile`, `LoadAvailableBlockSheet`, and `RecordTrackPrsBottomSheet` into `app/src/main/java/com/fractanomics/crosstraining/ui/screens/SimplifiedBlockLogComponents.kt` with `isEditMode`, `editingSessionId`, `persistedSessionPrKeys`, `explicitlyAppliedPrKeys`, `customWeightOverrides`, live set-to-PR auto-sync, `48.dp` min touch targets (`minimumInteractiveComponentSize()`), `Modifier.imePadding()`, `#0B6E3F`/`#2ECC71` contrast, `fontFeatureSettings = "tnum"`, TalkBack semantics, and pre-save `[Undo]` Snackbar callback
- [x] T009 Integrate `hydrateSessionBlocksForEditor`, simplified block state management, `Load Block ▾` appending, hidden `"Pre-filled from last session"` in edit mode, `BackHandler(enabled = isDirty)` discard confirmation dialog, `reconcileRepMaxes = isEditMode`, and `inheritedBlockTrackedPrs` in `app/src/main/java/com/fractanomics/crosstraining/ui/screens/SessionEditor.kt`

---

## Phase 4: User Story 4 - Remote MCP Tool `update_block_session_log` & Tombstone Parity (`SPEC-MCP-BLOCKS-02`)

**Purpose**: Flat Zod schema, tombstoned `RepMaxRecord` persistence, tool registration (#34), and MCP instructions update

- [x] T010 [P] Add `UpdateBlockSessionLogInputSchema` using inline factory helpers and `.min(0)`/`.min(1)` (0 `$ref`, 0 `exclusiveMinimum`) in `mcp-server/src/schemas.ts`
- [x] T011 Add `deletedAtMillis?: number | null` to `RepMaxRecord`, filter tombstones in `computeCyclesFromRaw` & `listRepMaxes`, revive tombstones in write paths, and implement `updateBlockSessionLog` with PR reconciliation & parent `AvailableBlock` inheritance in `mcp-server/src/store.ts`
- [x] T012 Register `update_block_session_log` (`34` tools total) with `IDEMPOTENT_WRITE_ANNOTATIONS` and update `CROSSTRAINING_MCP_INSTRUCTIONS` in `mcp-server/src/mcp.ts`

---

## Phase 5: Automated Unit, Sync & Contract Verification

**Purpose**: Deterministic unit and contract tests covering codec, repository reconciliation, cloud tombstones, UI state auto-sync, and MCP tool behavior

- [x] T013 [P] Add `excludeSessionId`, `hydrateSessionBlocksForEditor` partitioning (strength/AvailableBlock vs legacy metcon), and lossless metadata preservation tests in `app/src/test/java/com/fractanomics/crosstraining/util/AvailableBlockCodecTest.kt`
- [x] T014 [P] Add `Repository.updateSession(reconcileRepMaxes = true/false)` in-place PR update/delete/insert, parent block `[ab_prs=...]` inheritance, and `UserCloudSyncManager` Step 5 tombstone deletion tests in `app/src/test/java/com/fractanomics/crosstraining/data/AvailableBlockRepositoryAndSyncTest.kt`
- [x] T015 [P] Create `app/src/test/java/com/fractanomics/crosstraining/ui/screens/SessionEditorBlockLogEditTest.kt` covering simplified block edit parity, hidden pre-fill subtitle, live set-to-PR auto-sync, `isCustomWeightOverride` insulation, multi-movement shared `rmReps` isolation, `Undo` removal, and `isDirty` guard state
- [x] T016 [P] Update tool count assertion (`34`), add schema flatness checks, `update_block_session_log` PR tombstone + template inheritance + `list_rep_maxes`/`get_active_cycle` tombstone exclusion tests in `mcp-server/test/mcp-server.test.ts`

---

## Phase 6: Polish, Convergence, Local Build Gates & Remote CI

- [x] T017 Update `CHANGELOG.md` under `## [Unreleased]` with `SPEC-022` / `SPEC-BLOCK-LOG-EDIT-01` / `SPEC-MCP-BLOCKS-02` summary
- [x] T018 Run full Pre-PR Verification Suite: `.\gradlew.bat testDebugUnitTest --no-daemon`, `.\scripts\tests\Invoke-ScriptTests.ps1`, `.\gradlew.bat lintDebug --no-daemon`, `mcp-server` `npm run build && npm test`, and `.\gradlew.bat assembleDebug --no-daemon` + copy APK to `local_test\latest.apk`
- [ ] T019 Commit all scoped changes on `feat/022-athlete-block-log-editing` (leaving `firestore.rules` & `weight-export-pv.joseangel.csv` untouched), load `Set-GhToken-Antares.ps1`, push branch, open PR with Mission Plan, and monitor `gh pr checks --watch` until 100% Green
