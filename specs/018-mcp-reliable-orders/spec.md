# Feature Specification: Reliable Multi-Order & Active-Cycle Workout Assistant Communication

**Feature Branch**: `feat/018-mcp-reliable-orders`

**Created**: 2026-11-10

**Status**: Approved

**Input**: User description: "i want to improve the communication with the mcp. Here, in gemini chat, you can see ow some orders are not answered. Let's review why and fix it: https://share.gemini.google/HdbxExwheHXz"

## User Scenarios & Testing *(mandatory)*

### User Story 1 - Multi-Order Batch Goal & Workout Updates in a Single Confirmation (Priority: P1)

An athlete chatting with their AI workout assistant issues multiple training orders in a single message—such as converting an existing rep-max goal (`2RM Snatch` → `3RM Snatch @ 40 kg`) while simultaneously adding five new strength goals (`3RM Incline Bench Press`, `3RM Clean`, `6RM Biceps Twist Curl`, `6RM Cable Triceps Push Downs`, `6RM Behind the Neck Cable Triceps`), or logging multiple exercises, personal records, weight entries, or daily logs at once. Instead of forcing a fragile multi-step chain of six or seven consecutive interactive permission prompts that stalls mid-way and leaves orders unanswered, the assistant executes the entire multi-item request in a single atomic operation with at most one confirmation and returns a complete summary of every applied change.

**Why this priority**: Directly resolves the primary failure in the reported conversation where Gemini stalled after three sequential tool-confirmation cards and silently dropped four of the six user orders without answering.

**Independent Test**: Can be fully tested by sending a multi-order cycle-goal request (combining goal scheme replacements, updates, deletions, and newly created exercises) in one call and verifying that all requested goals are persisted atomically and returned in a structured per-item result summary.

**Acceptance Scenarios**:

1. **Given** an athlete has an existing `2RM Snatch` goal in their active cycle, **When** they ask the assistant in one prompt to change `2RM Snatch` to `3RM Snatch` (`40 kg`) and add five additional rep-max goals (`Incline Bench Press`, `Clean`, `Biceps Twist Curl`, `Cable Triceps Push Downs`, `Behind the Neck Cable Triceps`), **Then** all removals and additions complete in a single batch action, missing movements are created automatically, and every order is confirmed in the response.
2. **Given** an athlete asks a read-only question (such as listing active goals, exercises, routines, sessions, personal records, weight history, or daily logs), **When** the assistant inspects tool safety metadata, **Then** every read-only operation is clearly marked non-mutating and safe so compatible AI clients do not needlessly block read queries behind destructive-action confirmation prompts.
3. **Given** an AI assistant issues multiple independent write operations concurrently in the same turn, **When** those operations target the same user's cloud workout collections, **Then** all mutations are serialized safely so zero updates are lost or overwritten by concurrent requests.

---

### User Story 2 - Accurate Active Cycle Discovery & Default Cycle Targeting (Priority: P1)

An athlete asks the AI assistant *"Give me the active cycle"* or asks to add/modify goals, sessions, or personal records without manually specifying a cycle number. The assistant immediately identifies the athlete's true active training cycle (e.g., `Cycle 4: Recomposition`) rather than defaulting to historical `Cycle 1`, displays its goals and recent progress accurately, and targets that active cycle automatically whenever no explicit cycle is specified.

**Why this priority**: Directly fixes the first failure in the reported conversation where asking for the active cycle returned stale `Cycle 1` instead of `Cycle 4`, and prevents omitted cycle arguments from silently writing data into old cycles.

**Independent Test**: Can be fully tested by populating multiple historical cycles (`Cycle 1`..`Cycle 4`), querying the active cycle and cycle list without specifying an ID, and performing goal/session/PR mutations with omitted cycle IDs to verify they target `Cycle 4`.

**Acceptance Scenarios**:

1. **Given** an athlete has multiple training cycles in their history (`Cycle 1` through `Cycle 4`, with `Cycle 4: Recomposition` currently active), **When** they ask *"Give me the active cycle"*, **Then** the assistant returns `Cycle 4: Recomposition` along with its cycle goals, recent sessions, and summary metrics on the first attempt.
2. **Given** an athlete is on `Cycle 4: Recomposition`, **When** they add, replace, or delete cycle goals, log a workout session, or record a rep-max without naming a cycle number, **Then** the system resolves and applies the change to `Cycle 4: Recomposition` instead of falling back to `Cycle 1`.
3. **Given** an athlete syncs from the mobile app after switching their active cycle locally (even to an earlier cycle number), **When** the assistant queries the active cycle, **Then** the explicit active-cycle marker synced from the mobile app takes precedence over heuristic cycle-ID ordering.

---

### User Story 3 - Durable Goal, Routine & Session Deletions Across Mobile Synchronization (Priority: P2)

When an athlete asks the AI assistant to remove or replace a cycle goal (such as replacing `2RM Snatch` with `3RM Snatch`), delete a routine, or delete a logged session, and later triggers cloud synchronization on their mobile phone, the mobile app honors the remote deletion rather than re-uploading its local copy and resurrecting the deleted item.

**Why this priority**: Ensures multi-order replacements (`delete old RM` + `create new RM`) remain permanent after the athlete opens the mobile app and syncs.

**Independent Test**: Can be fully tested by deleting a cycle goal, routine, or session via the remote assistant, running mobile cloud synchronization (`download` then `upload`), and verifying the deleted record is removed locally on the phone and does not reappear in cloud storage.

**Acceptance Scenarios**:

1. **Given** an athlete has `2RM Snatch` on both their mobile device and cloud account, **When** they delete or replace `2RM Snatch` via the AI assistant and then run **Sync Now** on the mobile app, **Then** `2RM Snatch` is removed from the mobile device and is not re-uploaded to cloud storage.
2. **Given** a cycle goal, routine, or session has been deleted via the AI assistant, **When** the athlete queries goals, routines, sessions, or workout summaries through the assistant before syncing their phone, **Then** deleted entries are immediately excluded from all assistant read responses.

---

### User Story 4 - Resilient Remote Connector Transport, Rate-Limiting & Live Service Availability (Priority: P2)

When an athlete interacts with the assistant over repeated turns from shared cloud AI proxy networks, protocol handshakes, unsupported-method probes, cross-origin headers, and burst requests complete cleanly without protocol header rejections or false rate-limit blocks, and the live remote connector is updated so improvements take effect immediately in chat.

**Why this priority**: Eliminates transport warnings and shared-proxy throttling during multi-turn assistant sessions and ensures the live connector reflects the verified fix.

**Independent Test**: Can be tested by replaying multi-request assistant handshake sequences (including `GET` probes, protocol-version headers, missing stream `Accept` tokens, and rapid authenticated bursts from the same proxy IP) and verifying compliant responses without false `429` errors.

**Acceptance Scenarios**:

1. **Given** an AI client probes the connector endpoint with `GET` or includes protocol-version headers during a conversation, **When** the connector responds, **Then** it includes compliant `Allow` and exposed protocol headers without breaking stateless request handling.
2. **Given** multiple conversation turns arrive from shared AI platform proxy addresses within one minute, **When** requests carry a valid user authorization token, **Then** rate limits are enforced per authenticated user identity with sufficient burst capacity for multi-step assistant turns rather than throttling shared proxy IPs prematurely.

---

### Edge Cases

- **Goal rep-scheme replacement in one order**: When a user says *"Change 2RM Snatch into 3RM Snatch at 40 kg"*, the batch operation removes `2RM Snatch` and upserts `3RM Snatch` atomically in the active cycle without leaving orphan goals or requiring two separate tool calls.
- **Brand-new exercises inside a batch order**: When a batch contains multiple unfamiliar exercise names (e.g., `Biceps Twist Curl`, `Behind the Neck Cable Triceps`), all missing exercises are created once (case-insensitively deduplicated even if the same new exercise appears twice in the same batch) without duplicate IDs.
- **Account with zero existing cycles or records**: When a brand-new user has no goals, sessions, or personal records yet and omits `cycleId`, the system safely initializes/targets Cycle `1` (`Active Cycle`) without failing.
- **Concurrent parallel tool calls from an AI client**: When an AI client dispatches 5 write calls simultaneously over separate HTTP connections for the same user, per-user transactional serialization guarantees all 5 writes persist with zero lost updates.
- **Deleting a non-existent goal, routine, or session**: Deleting an item that does not exist (or was already deleted) succeeds idempotently and reports `deleted: false` (or `0` removed) rather than failing the batch.
- **Clients omitting `text/event-stream` in `Accept` header**: Stateless JSON-mode requests that send `Accept: application/json` or `*/*` still receive valid responses instead of failing strict content-negotiation checks.

## Requirements *(mandatory)*

### Functional Requirements

- **FR-001**: System MUST provide batch mutation capabilities for multi-item training orders—at minimum atomic batch cycle-goal management (supporting simultaneous goal upserts, rep-scheme replacements, and deletions in one call) as well as batch upserts for exercises, personal records (rep maxes), weight entries, and daily logs.
- **FR-002**: Tool descriptions and instructions MUST guide AI assistants to consolidate multi-item user prompts into a single batch tool invocation rather than chaining sequential single-item mutation calls.
- **FR-003**: Every assistant tool MUST declare accurate safety metadata (`readOnly`, `destructive`, `idempotent`, and `openWorld` hints) so AI clients distinguish safe read-only queries from idempotent writes and destructive deletions.
- **FR-004**: All tool input schemas MUST remain flat and strictly compatible with strict AI platform schema validators (zero `$ref` pointers, zero `exclusiveMinimum` keywords, and lowercase snake_case tool names within 60 characters).
- **FR-005**: System MUST expose dedicated cycle discovery operations (`get_active_cycle` and `list_cycles`) summarizing known cycles, active status, cycle name, goal counts, active goals, and recent session activity.
- **FR-006**: System MUST dynamically resolve the user's active cycle (preferring explicit active-cycle markers synced from the mobile app, falling back to the highest active non-deleted cycle with tie-breaking by most recent activity, and finally `1` for empty accounts) instead of hardcoding Cycle `1`.
- **FR-007**: Omitting `cycleId` on cycle-scoped mutations (`upsert_cycle_goal`, `delete_cycle_goal`, batch cycle-goal management, `upsert_session`, `record_rep_max`, and batch rep-max recording) MUST automatically target the resolved active cycle and stamp its resolved `cycleName` when known.
- **FR-008**: `list_cycle_goals` MUST support optional filtering by `cycleId` or `activeCycleOnly` and enrich responses with active-cycle metadata so assistants never confuse historical cycle goals with active cycle goals.
- **FR-009**: Mobile cloud upload MUST annotate synced cycle-linked records (`cycle_goals`, `sessions`, `rep_maxes`) with whether their parent local cycle is currently active (`isActiveCycle`), without syncing local-only cycle schedule bitmasks or requiring new cloud security-rule document types.
- **FR-010**: Remote assistant deletions of cycle goals, routines, and workout sessions MUST record timestamped deletion tombstones (`deletedAtMillis` and `updatedAtMillis`) that are immediately hidden from assistant read queries and honored by mobile cloud synchronization (`downloadUserData`) so deleted items are removed locally on the phone rather than resurrected on upload.
- **FR-011**: Concurrent cloud data mutations for the same user MUST be serialized transactionally so parallel assistant tool calls never overwrite each other's collection snapshots.
- **FR-012**: Connector HTTP transport MUST return compliant `Allow: POST, DELETE` headers on unsupported `GET` probes, include MCP protocol headers in cross-origin configuration, and tolerate standard JSON `Accept` headers in stateless response mode.
- **FR-013**: Connector rate limiting MUST scope authenticated traffic quotas per user identity with burst headroom sufficient for multi-turn protocol handshakes from shared AI proxy networks, while keeping unauthenticated endpoint protection per client IP.
- **FR-014**: Once automated local and remote CI checks pass, the updated connector service MUST be deployed to the live cloud environment and verified healthy.

### Key Entities *(include if feature involves data)*

- **Cycle Summary**: Derived view of a training cycle (`cycleId`, `cycleName`, `isActive`, `goalsCount`, `sessionsCount`, `repMaxesCount`, `latestActivityDate`) resolved across synced user collections without exposing local-only schedule bitmasks.
- **Cycle Goal Record**: A target performance objective (`id`, `cycleId`, `cycleName`, `isActiveCycle`, `exerciseId`, `exerciseName`, `targetReps`, `startWeight`, `targetWeight`, `notes`, `updatedAtMillis`, optional `deletedAtMillis`).
- **Batch Cycle Goal Operation**: A composite request combining zero or more goal upserts (including optional `previousTargetReps` replacement for seamless RM conversions like `2RM` → `3RM`) and zero or more goal deletions executed atomically within a single tool call.
- **Deletion Tombstone**: Soft-delete marker (`deletedAtMillis`, `updatedAtMillis`) persisted on remotely deleted goals, routines, and sessions until consumed and compacted by mobile synchronization.

## Success Criteria *(mandatory)*

### Measurable Outcomes

- **SC-001**: A multi-order prompt containing 6 or more cycle-goal additions and rep-scheme conversions executes in **1 mutation tool call** (down from 7 sequential confirmation-gated calls), completing in under 3 seconds warm latency with 100% of orders applied.
- **SC-002**: Asking *"Give me the active cycle"* on a multi-cycle account identifies the true active cycle with **100% accuracy on the first turn** without user correction.
- **SC-003**: **100% of read-only tools** advertise non-destructive read-only safety hints, and **100% of advertised tool schemas** pass strict validator checks with zero `$ref` or `exclusiveMinimum` violations.
- **SC-004**: Firing **10 concurrent mutation requests** against the same user account produces **zero lost updates** (100% of mutations preserved).
- **SC-005**: Deleting or replacing a cycle goal via the assistant followed by mobile **Sync Now** results in **0 resurrected goals** across both local mobile storage and cloud storage.
- **SC-006**: All automated test suites (MCP connector unit/integration tests, Android unit tests, lint checks, and repository script tests) pass at **100%**, and the live connector endpoint responds healthy after deployment.

## Assumptions

- Training cycle schedule bitmasks (`fastDaysOfWeek`, `restDaysOfWeek`) remain local-only on the Android device per existing architectural boundaries; syncing `isActiveCycle` and `cycleName` inside existing `cycle_goals`, `sessions`, and `rep_maxes` payloads provides exact active-cycle fidelity without altering Firestore security rules or Room database schemas.
- Existing OAuth 2.0 PKCE + Bearer token authentication between Gemini Chat and the remote connector remains unchanged so the user does not need to re-link or re-authorize their account unless new OAuth scopes were introduced (none are needed).
