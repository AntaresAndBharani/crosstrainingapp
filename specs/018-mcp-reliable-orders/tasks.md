---
description: "Task list for Reliable Multi-Order & Active-Cycle Workout Assistant Communication"
---

# Tasks: Reliable Multi-Order & Active-Cycle Workout Assistant Communication

**Input**: Design documents from `/specs/018-mcp-reliable-orders/`

**Prerequisites**: `plan.md` (required), `spec.md` (required), `research.md`, `data-model.md`, `contracts/mcp-tools.md`, `quickstart.md`

**Tests**: Deterministic Vitest/Supertest MCP tests and Android JUnit sync tests are REQUIRED per Constitution Principle III & VII (`FR-001`..`FR-014`, `SC-001`..`SC-006`).

**Organization**: Tasks are grouped by user story (`US1`..`US4`) to enable independent implementation and verification of each story.

## Format: `[ID] [P?] [Story] Description`

- **[P]**: Can run in parallel (different files, no unmet dependencies)
- **[Story]**: Which user story this task belongs to (`[US1]`, `[US2]`, `[US3]`, `[US4]`)
- Exact repository file paths are included in every task description

---

## Phase 1: Setup (Shared Infrastructure)

**Purpose**: Verify MCP server & Android ignore rules and inspect build/CI script boundaries

- [X] T001 Verify `.gitignore` and `mcp-server/.dockerignore` ignore patterns (`node_modules/`, `dist/`, `.env*`, `coverage/`) in `mcp-server/.dockerignore`
- [X] T002 [P] Inspect `scripts/tests/` workflow contract assertions to confirm safe CI integration boundaries in `scripts/tests/Invoke-ScriptTests.ps1`

---

## Phase 2: Foundational (Blocking Prerequisites)

**Purpose**: Core transactional `BackingStore`, per-user async mutex, tombstone record fields, and flat Gemini-safe Zod schema factories required by all user stories

**⚠️ CRITICAL**: Complete before User Story implementation phases

- [X] T003 Extend domain record types (`CycleSummary`, `isActiveCycle`, `deletedAtMillis`) and transactional `updateCollections` / per-user mutex locking on `BackingStore`, `FirestoreBackingStore`, and `InMemoryUserDataStore` in `mcp-server/src/store.ts`
- [X] T004 [P] Define flat Gemini-compatible Zod factory schemas (zero `$ref`, zero `exclusiveMinimum`) for cycle filters, `previousTargetReps` goal replacement, `manage_cycle_goals`, `batch_upsert_exercises`, `batch_record_rep_maxes`, `batch_upsert_weight_entries`, and `batch_upsert_daily_logs` in `mcp-server/src/schemas.ts`

**Checkpoint**: Transactional store foundation and flat schema factories ready

---

## Phase 3: User Story 1 - Multi-Order Batch Goal & Workout Updates in a Single Confirmation (Priority: P1) 🎯 MVP

**Goal**: Consolidate multi-item user prompts (such as the 6-order cycle-goal conversion & creation prompt) into a single atomic tool call with complete `ToolAnnotations` and zero concurrent write clobbering (`FR-001`, `FR-002`, `FR-003`, `FR-004`, `FR-011`, `SC-001`, `SC-003`, `SC-004`).

**Independent Test**: Execute the exact 6-order Gemini prompt (`2RM -> 3RM Snatch` + 5 new RM goals) in 1 `manage_cycle_goals` call, verify `tools/list` annotations & flat schema invariants across all tools, and fire 10 parallel mutations against one user with zero lost updates.

### Tests for User Story 1

- [X] T005 [P] [US1] Add Vitest tests for `ToolAnnotations` matrix, flat JSON Schema invariants (zero `$ref`/`exclusiveMinimum`), 6-order `manage_cycle_goals` batch execution (with `previousTargetReps` conversion + deduplicated new exercises), batch exercises/PRs/weights/daily-logs, and 10-way concurrent mutation safety in `mcp-server/test/mcp-server.test.ts`

### Implementation for User Story 1

- [X] T006 [US1] Implement atomic `manageCycleGoals` (supporting `upserts`, `previousTargetReps` RM replacement, and `deletes` with single-pass `resolveExerciseMap`), `batchUpsertExercises`, `batchRecordRepMaxes`, `batchUpsertWeightEntries`, and `batchUpsertDailyLogs` in `mcp-server/src/store.ts`
- [X] T007 [US1] Register all batch tools, attach explicit MCP `ToolAnnotations` (`readOnlyHint`, `destructiveHint`, `idempotentHint`, `openWorldHint: false`) to every tool, and add multi-order batching instructions to `McpServer` and tool descriptions in `mcp-server/src/mcp.ts`

**Checkpoint**: User Story 1 is independently functional and tested (`npm test` green for US1)

---

## Phase 4: User Story 2 - Accurate Active Cycle Discovery & Default Cycle Targeting (Priority: P1)

**Goal**: Accurately identify the user's active cycle (`Cycle 4: Recomposition` instead of hardcoded `Cycle 1`), expose `get_active_cycle` and `list_cycles`, enrich `list_cycle_goals` and `get_workout_summary`, default omitted `cycleId` mutations to the active cycle, and stamp `isActiveCycle` on Android cloud uploads (`FR-005`, `FR-006`, `FR-007`, `FR-008`, `FR-009`, `SC-002`).

**Independent Test**: Populate `Cycle 1`..`Cycle 4` (both legacy unstamped and mobile `isActiveCycle` stamped), verify `get_active_cycle` and `list_cycles` return `Cycle 4`, verify omitted `cycleId` writes land on `Cycle 4`, and verify Android `uploadUserData` stamps `isActiveCycle`.

### Tests for User Story 2

- [X] T008 [P] [US2] Add Vitest tests for `get_active_cycle`, `list_cycles`, `list_cycle_goals` (`activeCycleOnly` / `cycleId` filter), `isActiveCycle` override precedence, empty-account fallback to Cycle `1`, and omitted-`cycleId` targeting in `mcp-server/test/mcp-server.test.ts`
- [X] T009 [P] [US2] Add JUnit tests verifying `uploadUserData` stamps `isActiveCycle` (`true` for active cycle, `false` for inactive cycles) across `cycle_goals`, `sessions`, and `rep_maxes` in `app/src/test/java/com/fractanomics/crosstraining/data/firebase/McpRemoteSyncTest.kt`

### Implementation for User Story 2

- [X] T010 [US2] Implement `listCycles`, `getActiveCycle`, `resolveTargetCycle`, active-cycle enrichment in `listCycleGoals` & `getWorkoutSummary`, and dynamic active-cycle defaults across `upsertCycleGoal`, `deleteCycleGoal`, `manageCycleGoals`, `upsertSession`, `recordRepMax`, and `batchRecordRepMaxes` in `mcp-server/src/store.ts`
- [X] T011 [US2] Register `get_active_cycle` and `list_cycles` tools with `readOnlyHint: true` annotations and wire `cycleId` / `activeCycleOnly` filters on `list_cycle_goals` and `list_sessions` in `mcp-server/src/mcp.ts`
- [X] T012 [P] [US2] Stamp `isActiveCycle` on uploaded `cycle_goals`, `sessions`, and `rep_maxes` maps in `app/src/main/java/com/fractanomics/crosstraining/data/firebase/UserCloudSyncManager.kt`

**Checkpoint**: User Stories 1 and 2 are both independently functional and tested

---

## Phase 5: User Story 3 - Durable Goal, Routine & Session Deletions Across Mobile Synchronization (Priority: P2)

**Goal**: Persist `deletedAtMillis` + `updatedAtMillis` tombstones on MCP deletions (`cycle_goals`, `routines`, `sessions`), filter them out of MCP reads, and delete matching local Room records during Android `downloadUserData` so mobile `Sync Now` never resurrects MCP-deleted items (`FR-010`, `SC-005`).

**Independent Test**: Delete `2RM Snatch`, a routine, and a session via MCP, verify MCP reads omit them immediately, run Android `downloadUserData(importUnmarkedRemoteRecords = false)` followed by `uploadUserData`, and verify `0` resurrected items in Room and Firestore.

### Tests for User Story 3

- [X] T013 [P] [US3] Add Vitest tests verifying `delete_cycle_goal`, `manage_cycle_goals` deletes/replacements, `delete_routine`, and `delete_session` write `deletedAtMillis` tombstones, hide deleted records from all read tools, and revive cleanly on re-upsert in `mcp-server/test/mcp-server.test.ts`
- [X] T014 [P] [US3] Add JUnit tests verifying remote `deletedAtMillis` tombstones on `cycle_goals`, `routines`, and `sessions` delete matching local Room entities during `downloadUserData` (both login and manual sync modes) without resurrection in `app/src/test/java/com/fractanomics/crosstraining/data/firebase/McpRemoteSyncTest.kt`

### Implementation for User Story 3

- [X] T015 [US3] Update `deleteCycleGoal`, `manageCycleGoals`, `deleteRoutine`, `deleteSession`, and read/upsert methods to use `deletedAtMillis` tombstones in `mcp-server/src/store.ts`
- [X] T016 [P] [US3] Update `downloadUserDataInternal` to consume `deletedAtMillis` tombstones on `routines`, `cycle_goals`, and `sessions` by deleting matching local entities via `Repository` in `app/src/main/java/com/fractanomics/crosstraining/data/firebase/UserCloudSyncManager.kt`

**Checkpoint**: Bidirectional MCP + Android deletion durability verified end-to-end

---

## Phase 6: User Story 4 - Resilient Remote Connector Transport, Rate-Limiting & Live Service Availability (Priority: P2)

**Goal**: Harden MCP Streamable HTTP headers (`Allow: POST, DELETE`, `Mcp-Protocol-Version` CORS, JSON `Accept` header normalization), isolate rate limits per authenticated user identity behind shared Google proxy IPs, add CI verification for `mcp-server/`, and deploy the verified connector to Cloud Run (`FR-012`, `FR-013`, `FR-014`, `SC-006`).

**Independent Test**: Replay `GET /mcp`, CORS preflight with `Mcp-Protocol-Version`, JSON-only `Accept` `POST /mcp`, and multi-user traffic from a shared proxy IP (`66.249.83.10`) in Supertest without false `429` blocks.

### Tests for User Story 4

- [X] T017 [P] [US4] Add Supertest HTTP tests for `GET /mcp` `Allow: POST, DELETE`, `Mcp-Protocol-Version` CORS headers, `Accept: application/json` normalization, and per-user rate-limit isolation across shared proxy IPs in `mcp-server/test/mcp-server.test.ts`

### Implementation for User Story 4

- [X] T018 [US4] Implement `Allow: POST, DELETE` on `405 GET /mcp`, `Mcp-Protocol-Version` / `Retry-After` CORS headers, `Accept` header normalization middleware, and two-tier (IP + authenticated token identity) rate limiting in `mcp-server/src/index.ts`
- [X] T019 [P] [US4] Add an `mcp-server` build & unit test job (`npm ci && npm run build && npm test`) to `.github/workflows/build.yml` (and align workflow tests if needed in `scripts/tests/`)

**Checkpoint**: All 4 user stories implemented and verified locally

---

## Phase 7: Polish & Cross-Cutting Concerns

**Purpose**: Documentation, full pre-PR verification gate, APK sync, changelog, PR, remote CI check, and live Cloud Run deployment

- [X] T020 [P] Document new batch tools (`manage_cycle_goals`, `batch_*`), `get_active_cycle` / `list_cycles`, and `ToolAnnotations` in `mcp-server/README.md`
- [X] T021 Execute full local verification suite (`mcp-server` `npm run build && npm test`, `.\gradlew.bat testDebugUnitTest --no-daemon`, `.\scripts\tests\Invoke-ScriptTests.ps1`, `.\gradlew.bat lintDebug --no-daemon`, `.\gradlew.bat assembleDebug --no-daemon` + sync `local_test\latest.apk`) from `mcp-server/package.json` and `app/build.gradle.kts`
- [X] T022 Update `CHANGELOG.md` under `## [Unreleased]` summarizing reliable multi-order MCP batching, active-cycle resolution, tombstone deletion sync, and transport hardening in `CHANGELOG.md`
- [X] T023 Deploy verified `crosstraining-mcp` image to Google Cloud Run (`virgymia-c2cc7`, `europe-west1`) and verify `/healthz` per `specs/018-mcp-reliable-orders/quickstart.md`

---

## Dependencies & Execution Order

### Phase Dependencies

- **Phase 1 (Setup)**: Starts immediately.
- **Phase 2 (Foundational — `T003`, `T004`)**: Depends on Phase 1; blocks User Story phases (`US1`–`US4`).
- **Phase 3 (`US1`) & Phase 4 (`US2`)**: Both P1 stories; execute sequentially on shared `mcp-server/src/{store,mcp}.ts` while Android tasks (`T009`, `T012`) run independently in `app/`.
- **Phase 5 (`US3`) & Phase 6 (`US4`)**: Depend on Phase 2; `US3` extends `store.ts` + `UserCloudSyncManager.kt`, while `US4` updates `mcp-server/src/index.ts` and `.github/workflows/build.yml` independently.
- **Phase 7 (Polish & Live Deployment)**: Runs after all user story phases pass tests.

### Parallel Opportunities

- `T003` (`store.ts`) and `T004` (`schemas.ts`) can be authored in parallel.
- `T008` (`mcp-server.test.ts`) and `T009` (`McpRemoteSyncTest.kt`) run in parallel across TypeScript and Kotlin test suites.
- `T012` (`UserCloudSyncManager.kt`), `T018` (`mcp-server/src/index.ts`), and `T019` (`.github/workflows/build.yml`) touch completely disjoint files.

---

## Parallel Example: User Story 2 & User Story 3

```bash
# Run MCP TypeScript and Android Kotlin sync tests in parallel:
Task: "T008 [P] [US2] Add Vitest tests for get_active_cycle and list_cycles in mcp-server/test/mcp-server.test.ts"
Task: "T009 [P] [US2] Add JUnit tests verifying uploadUserData stamps isActiveCycle in app/src/test/java/com/fractanomics/crosstraining/data/firebase/McpRemoteSyncTest.kt"
```

---

## Implementation Strategy

### MVP First (User Story 1 + User Story 2)

1. Complete Phase 1 & Phase 2 (`store.ts` locking/transactions + `schemas.ts` flat batch schemas).
2. Implement `US1` (`manage_cycle_goals`, `batch_*`, `ToolAnnotations`) + `US2` (`get_active_cycle`, `list_cycles`, active-cycle defaults, `isActiveCycle` Android upload stamp).
3. Layer `US3` (bidirectional tombstone deletion durability) + `US4` (HTTP headers, shared-proxy user rate limiting, CI workflow job).
4. Run full verification gates, open PR, verify 100% green remote CI, and redeploy `crosstraining-mcp` to Cloud Run (`virgymia-c2cc7`).
