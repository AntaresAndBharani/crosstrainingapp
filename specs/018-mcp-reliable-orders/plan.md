# Implementation Plan: Reliable Multi-Order & Active-Cycle Workout Assistant Communication

**Branch**: `feat/018-mcp-reliable-orders` | **Date**: 2026-11-10 | **Spec**: [spec.md](./spec.md)

**Input**: Feature specification from `/specs/018-mcp-reliable-orders/spec.md`

## Summary

Eliminate unanswered multi-order prompts, wrong active-cycle responses, resurrected goal deletions, parallel write race conditions, and Streamable HTTP / shared-proxy friction in the CrossTraining remote MCP connector (`mcp-server/`) and Android Cloud Sync (`UserCloudSyncManager.kt`). Specifically:
1. Introduce atomic batch tools (`manage_cycle_goals` with 1-step `previousTargetReps` RM conversion + `upserts` + `deletes`, `batch_upsert_exercises`, `batch_record_rep_maxes`, `batch_upsert_weight_entries`, `batch_upsert_daily_logs`), LLM batching instructions, and complete MCP `ToolAnnotations` (`readOnlyHint`, `destructiveHint`, `idempotentHint`, `openWorldHint`) with flat Gemini-compatible JSON schemas.
2. Add `get_active_cycle` and `list_cycles` tools, stamp `isActiveCycle` on Android cloud uploads, and replace hardcoded `cycleId ?? 1` defaults with dynamic active-cycle resolution.
3. Persist `deletedAtMillis` tombstones on remote MCP deletions of `cycle_goals`, `routines`, and `sessions` and honor them during Android `downloadUserData` so `Sync Now` deletes local copies rather than resurrecting them.
4. Serialize per-user Firestore collection updates via async mutex + `Firestore.runTransaction`, harden Streamable HTTP headers (`Allow: POST, DELETE`, `Mcp-Protocol-Version`, `Accept` normalization), scope rate limits per authenticated user identity, and redeploy `crosstraining-mcp` to Google Cloud Run (`virgymia-c2cc7`).

## Technical Context

**Language/Version**: TypeScript 5.8 / Node.js 22 (`mcp-server/`) + Kotlin 2.0 / JVM 17 (`app/`)

**Primary Dependencies**: `@modelcontextprotocol/sdk` (`^1.12.0`), `express` (`^5.1.0`), `firebase-admin` (`^13.4.0`), `zod` (`^3.25.0`), Firebase Firestore Android SDK, Room SQLite (`2.6.x`)

**Storage**: Cloud Firestore (`/environments/{env}/users/{uid}/data/{collection}` `{ list: [...] }`) + Android Room SQLite

**Testing**: Vitest + Supertest (`mcp-server/test/mcp-server.test.ts`), JUnit4 + Coroutines Test (`app/src/test/.../McpRemoteSyncTest.kt`), Pester/PowerShell (`scripts/tests/Invoke-ScriptTests.ps1`)

**Target Platform**: Google Cloud Run (`europe-west1`, `virgymia-c2cc7`) + Android (`minSdk 26`, `compileSdk 35`)

**Project Type**: Mobile app (`app/`) + Remote MCP Cloud Connector (`mcp-server/`)

**Performance Goals**: `< 3s` warm latency for 6+ item batch mutations (`<= 3` Firestore round-trips per batch vs `4N` sequential); zero lost updates under 10-way concurrent tool calls

**Constraints**: Zero `$ref` and zero `exclusiveMinimum` in MCP `tools/list` JSON schemas; tool names `^[a-z0-9_]{1,60}$`; zero changes to `firestore.rules` allowed document IDs or Room SQLite schema migrations; `Cycle` schedule bitmasks (`fastDaysOfWeek`, `restDaysOfWeek`) remain strictly local-only

**Scale/Scope**: 28 MCP tools (8 new batch/cycle/delete tools + 20 enhanced tools), `mcp-server/src/{schemas,store,mcp,index}.ts`, `UserCloudSyncManager.kt`, unit/integration suites, and Cloud Run deployment

## Constitution Check

*GATE: Must pass before Phase 0 research. Re-check after Phase 1 design.*

- **I. MVVM & Unidirectional Data Flow (UDF)**: **PASS** — No UI or ViewModel state-flow violations; Android change is strictly inside `UserCloudSyncManager` repository sync layer.
- **II. Pure Deterministic Analytics & Display-Unit Consistency**: **PASS** — Active-cycle resolution and tombstone reconciliation are deterministic functions with explicit timestamp comparisons.
- **III. Test-First & 100% CI Parity**: **PASS** — Covered by deterministic Vitest/Supertest HTTP+MCP tests (`npm test`), Android JUnit tests (`McpRemoteSyncTest`), `Invoke-ScriptTests.ps1`, and `lintDebug`. Also adding an `mcp-server` verification job in `.github/workflows/build.yml` (verified against `scripts/tests/`) or keeping CI 100% green.
- **IV. Responsive Mobile UX & Accessibility**: **PASS** — Zero Jetpack Compose UI changes.
- **V. Offline-First, Idempotent Synchronization & Environment Isolation**: **PASS** — Natural-key idempotency preserved; MCP remote deletions propagate cleanly via `deletedAtMillis` tombstones without SQLite schema migrations or machine-specific paths.
- **VI. Agentic Collaboration, Automated PR Review & Self-Healing Governance**: **PASS** — Existing GitHub Actions governance contracts remain intact.
- **VII. Reliable, Atomic & Client-Compatible MCP Communication**: **PASS** — Directly implements `ToolAnnotations`, flat schemas, batch tools, transactional collection locking, and dynamic active-cycle resolution.

*Post-Phase-1 Design Re-Check*: **ALL GATES PASS** (zero violations).

## Project Structure

### Documentation (this feature)

```text
specs/018-mcp-reliable-orders/
├── plan.md              # Implementation plan
├── research.md          # Root-cause evidence & technical decisions
├── data-model.md        # Firestore item fields, CycleSummary & tombstone lifecycle
├── quickstart.md        # End-to-end build, test, sync & Cloud Run validation guide
├── contracts/
│   └── mcp-tools.md     # 28-tool catalog, annotations matrix & HTTP/rate-limit contract
├── checklists/
│   ├── requirements.md  # Spec quality validation checklist
│   └── mcp.md           # Domain requirements quality checklist
└── tasks.md             # Dependency-ordered implementation tasks
```

### Source Code (repository root)

```text
mcp-server/
├── src/
│   ├── index.ts         # Express Streamable HTTP transport, CORS, Accept normalization & user rate limiting
│   ├── mcp.ts           # McpServer instructions, ToolAnnotations & 28 tool registrations (incl. batch & cycle tools)
│   ├── schemas.ts       # Flat Gemini-safe Zod factory schemas (zero $ref / exclusiveMinimum)
│   └── store.ts         # Transactional BackingStore, per-user mutex, active-cycle resolver, batch & tombstone ops
├── test/
│   └── mcp-server.test.ts # Comprehensive MCP protocol, batch, active-cycle, concurrency & rate-limit tests
└── README.md            # Updated MCP tool catalog & multi-order documentation

app/
├── src/main/java/com/fractanomics/crosstraining/data/firebase/
│   └── UserCloudSyncManager.kt # Stamp isActiveCycle on upload; consume remote deletedAtMillis tombstones on download
└── src/test/java/com/fractanomics/crosstraining/data/firebase/
    └── McpRemoteSyncTest.kt    # Unit tests for isActiveCycle upload stamping & MCP tombstone deletion sync
```

**Structure Decision**: Existing dual-module layout (`mcp-server/` TypeScript Cloud Run connector + `app/` Android Kotlin client) is preserved cleanly with zero new packages or database migrations.

## Complexity Tracking

No Constitution violations—table intentionally omitted.
