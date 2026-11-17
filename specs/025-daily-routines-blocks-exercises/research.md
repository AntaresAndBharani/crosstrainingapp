# Research & Architectural Decisions: Daily Routines (`SPEC-025`)

## Decision 1: Zero-Migration Persistence Encoding in Existing Room v10 Schema

- **Decision**: Persist Daily Routines in the existing `routines` + `routine_blocks` tables using `Routine.defaultFormat = "DAILY_ROUTINE"`, `RoutineBlock.format = "DR_BLOCK"` (with `[dr_item=BLOCK:id=<id>:title=<urlEncoded>]`), and `RoutineBlock.format = "DR_EXERCISE"` (with `[dr_item=EXERCISE:id=<id>:name=<urlEncoded>]`, optional `[ab_prs=...]`, and clean note). Persist standalone exercise session logs in `session_blocks` with `SessionBlock.format = "STANDALONE_EXERCISE"`, `subBlock = ""`, and `mainExerciseId = exerciseId`.
- **Rationale**: Preserves Room schema v10 without requiring a SQLite migration or Firestore security-rule changes, maintains full Backup CSV and Firebase Share Code compatibility, and keeps `RoutineTargetCodec.extractCleanNotes` stripping `[dr_item=...]` cleanly from user-visible text.
- **Alternatives Considered**: Creating new `daily_routines` and `daily_routine_items` Room tables and Firestore documents. Rejected due to Constitution Principle V ("features that can be derived from existing entities MUST avoid unnecessary schema changes") and migration/backup surface expansion.

## Decision 2: Namespace-Isolated Natural Keys (`dr:` vs `ab:`)

- **Decision**: Scope routine natural-key deduplication and lookup across `Repository`, `UserCloudSyncManager`, and `mcp-server` using `"${if (isDailyRoutine) "dr" else "ab"}:${name.trim().lowercase()}"`.
- **Rationale**: Both Daily Routines and Available Blocks share the `routines` table/collection. Without namespacing, creating a Daily Routine named `"Monday Clean"` when an Available Block named `"Monday Clean"` already exists would overwrite the Available Block.
- **Alternatives Considered**: Forbidding duplicate names across tabs. Rejected because coaches commonly name a single-block day routine identically to its core block.

## Decision 3: Unified Cross-App History & Pre-Fill for Blocks and Standalone Exercises

- **Decision**: Resolve previous working weights and Progress analytics across ALL sessions (`SessionWithBlocks`) regardless of whether the Block or standalone Exercise was logged via a Daily Routine, another Daily Routine, or standalone (`+ Add Block` / `+ Add Exercise`).
- **Rationale**: Directly implements stakeholder decision Step 2, Option 2A ("Unified History Across the App") so athletes always see their true latest working weights and true progression charts.
- **Alternatives Considered**: Isolating history per Daily Routine ID (`sessionBlock.routineId == routineId`). Rejected per stakeholder Step 2 selection.

## Decision 4: Flat, Gemini-Compatible MCP Tool Schemas (`0` `$ref`, `0` `exclusiveMinimum`)

- **Decision**: Construct all 5 new MCP input schemas (`list_daily_routines`, `upsert_daily_routine`, `delete_daily_routine`, `log_daily_routine_session`, `get_daily_routine_progress`) using per-field Zod factory functions (`make...Schema()`) and `.min(0.1)` / `.min(1)` bounds instead of `.positive()` / `.gt()` or shared object references.
- **Rationale**: Constitution Principle VII and `mcp-server/test/mcp-server.test.ts` strictly assert zero `"$ref"` and zero `"exclusiveMinimum"` across `tools/list`.
- **Alternatives Considered**: Reusing shared Zod schema constants across array fields. Rejected because Zod-to-JSON-Schema emits `$ref` pointers on reused instances, which Gemini FunctionDeclaration rejects.

## Decision 5: Preserve Existing Test Invariants & Navigation Defaults

- **Decision**:
  - Keep `AppViewModel.progressMode` defaulting to `ProgressMode.BY_EXERCISE` and keep `ProgressMode.BY_ROUTINE` (`"By block"`), adding `ProgressMode.BY_DAILY_ROUTINE` (`"By routine"`) as the first chip in `ProgressScreen`.
  - Keep `DemoData.SEED_VERSION = 4` unchanged (`BackupCsvWeightTest` invariant).
  - Keep `buildCustomAvailableBlockState` at 3 empty weight tiles and `lastSessionDateLabel = null` (`SessionEditorAddBlockTest` invariant), while initializing `buildLoadedStandaloneExerciseState` and unlogged `buildLoadedAvailableBlockState` with `"First time logging — enter set weights"` (and 4 empty tiles for standalone exercises per `SPEC-ROUTINE-SESSION-TRACK-01`).
- **Rationale**: Guarantees 100% compatibility with existing regression tests while delivering every `SPEC-025` requirement.
