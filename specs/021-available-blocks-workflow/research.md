# Research: Available Blocks Workflow (`SPEC-021`)

## 1. Zero-Migration Persistence Strategy (`RoutineWithBlocks` + `[ab_prs=...]`)
- **Decision**: Persist `AvailableBlock` inside existing Room `routines` / `routine_blocks` (`defaultFormat = "AVAILABLE_BLOCK"`) and Cloud Firestore `routines` document, encoding `trackedPrs` in `RoutineBlock.notes` using a structured header prefix `[ab_prs=<urlEncodedMovement>:<rmReps>;...]`.
- **Rationale**:
  - `firestore.rules` restricts user data writes strictly to the 7 whitelisted document names (`exercises`, `routines`, `sessions`, `cycle_goals`, `rep_maxes`, `weight_entries`, `daily_logs`). Adding an eighth `available_blocks` collection in Firestore would fail with `PERMISSION_DENIED` unless server rules were manually deployed beforehand.
  - `RoomSchemaExportTest` enforces `AppDatabase` version `10` with frozen JSON schemas (`5.json`–`10.json`). Keeping Room at `v10` avoids schema churn and guarantees zero-loss Backup CSV v5 compatibility.
  - Extending `RoutineTargetCodec.extractCleanNotes` to strip `[ab_prs=...]` alongside `[rb_v2=...]` and legacy `Score: ...` headers keeps instructions pristine across Android cards, Log Session banners, Progress headers, and MCP responses.

## 2. Multi-PR Atomicity (`BlockInsert.newRepMaxes` & `updateSession` Re-Linking)
- **Decision**: Add `val newRepMaxes: List<RepMax> = emptyList()` to `BlockInsert` and `BlockDraft`, keeping deprecated/singular `newRepMax: RepMax?` as a fallback union during `Repository.saveSession` / `updateSession`.
- **Rationale**: A complex such as `Clean Pull + Clean + Jerk` requires recording PRs for multiple movements simultaneously in one block save. Furthermore, because `blockDao.deleteBlocksForSession` in `updateSession` fires SQLite `ON DELETE SET NULL` on existing `rep_maxes.blockId` rows, capturing prior `RepMax` IDs before deletion and matching them back to re-inserted blocks preserves `"Achieved in {Block}"` attribution across edits.

## 3. Weight-Only Logging & Telemetry Fidelity
- **Decision**: Weight-only set tiles write `WorkoutSet(reps = 1, weight = parsedWeight)` for positive weights and `WorkoutSet(reps = 1, weight = null)` for blank/bodyweight entries.
- **Rationale**: `BlockPerformance.average` and `BlockPerformance.volume` treat `weight > 0` meaningfully while ignoring `null` weights, preventing blank bodyweight sets from artificially pulling down weighted averages.

## 4. `By block` Progress Isolation
- **Decision**: Restrict the `mainExerciseId` fallback inside `ProgressAnalytics.routineBlockPerformances` to untitled legacy blocks (`b.routineId == null && b.subBlock.isBlank()`).
- **Rationale**: Without that guard, two distinct Available Blocks that start with the same primary movement (e.g., `"Clean Complex"` and `"Heavy Singles"`) cross-contaminate each other's KPIs, charts, and block history.

## 5. Flat MCP Schemas (Constitution Principle VII)
- **Decision**: Expose 5 new Zod schemas (`list_available_blocks`, `upsert_available_block`, `batch_upsert_available_blocks`, `delete_available_block`, `log_block_session`) using flat Zod types (`.min(1)` on integers instead of `.positive()`, schema-invisible `.refine()` for `YYYY-MM-DD`, no Zod `.default()`/`.transform()` leaking unsupported JSON schema keywords, zero `$ref`, zero `exclusiveMinimum`).
- **Rationale**: Satisfies Gemini/Claude/ChatGPT MCP schema validators verified by `mcp-server/test/mcp-server.test.ts`.
