# Research & Root-Cause Analysis: Reliable Multi-Order & Active-Cycle MCP Communication

**Feature**: `018-mcp-reliable-orders`
**Date**: 2026-11-10

## Root-Cause Evidence (`https://share.gemini.google/HdbxExwheHXz` + Cloud Run Logs)

Correlating the 7-turn Gemini conversation (`2026-11-09T14:27:09Z` – `14:52:32Z`) with Google Cloud Run request logs (`virgymia-c2cc7`, `crosstraining-mcp`) revealed four compounding failure mechanisms:

1. **Multi-Order Confirmation Chain Drop**:
   - In Turn 3, the user sent 6 goal orders in one prompt (`2RM -> 3RM Snatch @ 40 kg`, `3RM Incline Bench Press @ 70 kg`, `3RM Clean @ 60 kg`, `6RM Biceps Twist Curl @ 20 kg`, `6RM Cable Triceps Push Downs @ 30 kg`, `6RM Behind the Neck Cable Triceps @ 25 kg`).
   - Because `crosstraining-mcp` exposed only single-item write tools and declared no MCP `ToolAnnotations`, Gemini emitted sequential confirmation cards (`[Allow] [Deny]`) per tool invocation—even prompting on read-only `list_cycle_goals` during retry (`14:52:00Z`).
   - Cloud Run logs show Card #1 (`delete_cycle_goal` Snatch 2RM), Card #2 (`upsert_cycle_goal` Snatch 3RM), and Card #3 (`upsert_cycle_goal` Incline Bench Press 3RM at `14:34:11Z`) succeeded (`200 OK`), after which Gemini Web aborted the multi-step confirmation loop silently without executing orders #3–#6 or rendering a response.

2. **Stale Default Cycle (`cycleId ?? 1`) & Missing Active-Cycle Discovery**:
   - Turn 1 (`"Give me the active cycle"`) returned historical `Cycle 1` instead of `Cycle 4: Recomposition` because `schemas.ts` documented `cycleId` as *"defaults to 1 for active cycle"*, `store.ts` hardcoded `input.cycleId ?? 1`, and no `get_active_cycle` or `list_cycles` tool existed.

3. **Remote Deletion Resurrection & Parallel Array Write Clobbering**:
   - `deleteCycleGoal`, `deleteRoutine`, and `deleteSession` physically spliced entries out of Firestore `{ list: [...] }` without writing `deletedAtMillis` tombstones. During Android `Sync Now` (`downloadUserData(importUnmarkedRemoteRecords = false)` → `uploadUserData`), Android skipped missing remote rows and re-uploaded its local copy, resurrecting deleted goals like `2RM Snatch`.
   - Furthermore, whole-document `{ list: [...] }` reads/writes lacked per-user transactional locking, risking Last-Writer-Wins lost updates when an AI client fires parallel tool calls.

4. **Streamable HTTP & Shared-Proxy Rate-Limiting Friction**:
   - `GET /mcp` returned `405` on 100% of Gemini turns without an `Allow: POST, DELETE` header.
   - CORS configuration omitted `Mcp-Protocol-Version` in `allowedHeaders`/`exposedHeaders`.
   - Rate limiting (`60 req/min` by IP) shared quota across Google's `66.249.83.*` proxy egress pool where each single MCP turn generates 4–7 HTTP requests.

---

## Technical Decisions

### Decision 1: Atomic Batch Tools + Guidance Prompts + Explicit `ToolAnnotations`
- **Decision**:
  - Add `manage_cycle_goals` supporting simultaneous `upserts` (including optional `previousTargetReps` for 1-step RM conversions like `2RM -> 3RM`) and `deletes` in a single atomic transaction, plus `batch_upsert_exercises`, `batch_record_rep_maxes`, `batch_upsert_weight_entries`, and `batch_upsert_daily_logs`.
  - Update `McpServer` instructions and single-item tool descriptions to instruct LLMs: *"When the user requests multiple changes in one message, ALWAYS combine them into a single batch tool call (`manage_cycle_goals`, `batch_*`) instead of calling single-item tools repeatedly."*
  - Register every tool with explicit MCP `ToolAnnotations` (`readOnlyHint`, `destructiveHint`, `idempotentHint`, `openWorldHint: false`).
  - Construct every nested item Zod schema via fresh factory functions (`makeGoalUpsertItemSchema()`, etc.) so `zod-to-json-schema` never emits `$ref` pointers, keeping 100% compatibility with Gemini's strict validator.
- **Rationale**: Collapses a 6-order prompt (including RM scheme conversion) from 7 confirmation turns into 1 confirmation + 1 HTTP round-trip, and removes read-only confirmation friction.
- **Alternatives Considered**:
  - *Relying only on parallel single-item tool calls*: Rejected because Gemini Web still gates un-annotated write tools behind confirmation cards and caps consecutive confirmation turns.
  - *Reusing shared Zod sub-schema constants inside arrays/objects*: Rejected because `zod-to-json-schema` deduplicates shared references into `$ref`, which Gemini's MCP tool importer rejects (`commit 9a6b417`).

### Decision 2: Hybrid Active-Cycle Resolution (`isActiveCycle` Marker + Heuristic Fallback)
- **Decision**:
  - Stamp `isActiveCycle: Boolean` on `cycle_goals`, `sessions.session`, and `rep_maxes` entries during Android `UserCloudSyncManager.uploadUserData` (`cyclesById[cycleId]?.isActive == true`).
  - Add `resolveActiveCycle(ctx)` and `listCycles(ctx)` in `WorkoutDomainService`:
    1. Prefer the cycle where the newest stamped records declare `isActiveCycle === true` (and none newer declare `false`).
    2. For existing cloud snapshots uploaded prior to this release (where `isActiveCycle` is absent), select the highest `cycleId` present across non-deleted `cycle_goals`, `sessions`, and `rep_maxes` (tie-breaking by latest session/repMax/updatedAt timestamp).
    3. Default to `{ cycleId: 1, cycleName: "Active Cycle" }` only when the account has zero cycle-linked records.
  - Expose `get_active_cycle` and `list_cycles` read-only tools, enrich `list_cycle_goals` with `activeCycleId`, `activeCycleName`, and optional `cycleId` / `activeCycleOnly` filters, and replace all `input.cycleId ?? 1` fallbacks with `await this.resolveTargetCycle(ctx, input.cycleId, input.cycleName)`.
- **Rationale**: Works immediately on the user's live Firestore data (`Cycle 4: Recomposition`) even before they open the Android app, becomes 100% authoritative once Android syncs, keeps `Cycle` schedule bitmasks local-only, and requires zero `firestore.rules` or Room schema changes.
- **Alternatives Considered**:
  - *Adding a new `/data/cycles` Firestore document*: Rejected because `firestore.rules` strictly whitelists the 7 existing `/data/{docId}` names and `UserCloudSyncManager.kt` explicitly keeps `Cycle` entities local-only.

### Decision 3: Tombstone-Backed Remote Deletions for Goals, Routines & Sessions
- **Decision**:
  - When MCP deletes a `cycle_goal`, `routine`, or `session` (`delete_cycle_goal`, `manage_cycle_goals`, `delete_routine`, `delete_session`), stamp matching entries in Firestore with `deletedAtMillis = now` and `updatedAtMillis = now` instead of splicing them out of the `list` array.
  - Filter out entries with `deletedAtMillis != null && deletedAtMillis > 0` across all MCP read queries (`listCycleGoals`, `listRoutines`, `listSessions`, `getWorkoutSummary`, `listCycles`) and clear `deletedAtMillis` if the same natural key is later re-upserted via MCP.
  - Update Android `UserCloudSyncManager.downloadUserData` for `routines`, `cycle_goals`, and `sessions` so records carrying `deletedAtMillis > 0` delete the matching local Room entity (`repo.deleteRoutine`, `repo.deleteCycleGoal`, `repo.deleteSession`) instead of upserting it. Subsequent `uploadUserData` uploads the clean active snapshot, compacting consumed tombstones automatically.
- **Rationale**: Guarantees `delete_cycle_goal` (`2RM Snatch`) survives mobile `Sync Now` without needing a Room database migration on Android.
- **Alternatives Considered**:
  - *Full Room schema migration adding `deletedAtMillis` columns to `cycle_goals`, `routines`, and `sessions`*: Unnecessary for remote MCP deletions since local mobile deletions already work via unstamped-skip semantics (`importUnmarkedRemoteRecords = false`); tombstone consumption during `downloadUserData` achieves full bidirectional durability with zero SQLite migrations (Constitution Principle V).

### Decision 4: Per-User Async Mutex + Firestore Transactions for Collection Mutations
- **Decision**:
  - Add a per-`uid` in-process async mutex in `WorkoutDomainService` **plus** `Firestore.runTransaction` atomic read-modify-write support in `FirestoreBackingStore` (and `InMemoryUserDataStore` in tests).
  - Batch exercise resolution (`resolveExerciseMap`) reads and writes `exercises` at most once per batch call (skipping the write entirely when all exercises already exist).
- **Rationale**: Eliminates both intra-instance async interleaving and cross-instance Cloud Run race conditions while cutting Firestore round-trips per goal batch from $4N$ down to $\le 3$ total.
- **Alternatives Considered**:
  - *Unconditional `writeCollection` without transaction*: Vulnerable to Lost Update races whenever an LLM issues parallel `tools/call` HTTP requests.

### Decision 5: Streamable HTTP Header Compliance & Authenticated-User Rate Limiting
- **Decision**:
  - Add `Allow: POST, DELETE` header on `GET /mcp` `405` responses.
  - Add `Mcp-Protocol-Version` and `Last-Event-ID` to CORS `allowedHeaders` and `Mcp-Protocol-Version` to `exposedHeaders`.
  - Normalize `Accept` header on `POST /mcp` when a client sends `application/json` or `*/*` without `text/event-stream` so `@modelcontextprotocol/sdk`'s `StreamableHTTPServerTransport` accepts JSON-mode requests cleanly.
  - Split rate limiting into a high-ceiling IP guard (`300 req/min` per IP for unauthenticated/proxy traffic) plus a per-authenticated-identity limiter (`180 req/min` keyed by SHA-256 of Bearer token) with standard `Retry-After` headers.
- **Rationale**: Prevents shared Google proxy IPs (`66.249.83.*`) from hitting false `429` throttling during multi-turn Gemini sessions while maintaining strong abuse protection.
