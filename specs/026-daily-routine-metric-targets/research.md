# Phase 0 Research: Optional Daily Routine Multi-Metric Targets & Metric-Aware Workout Logging (`SPEC-026`)

## Research Findings & Technical Decisions

### Decision 1: Reusing `[rb_v2=...]` & `[log_metric=...]` Metadata Headers Without Room Schema Migration
- **Decision**: Persist optional per-exercise Daily Routine targets in existing `RoutineBlock` columns (`setsCount`, `targetRepsScheme`, `exerciseIdsCsv`, and `[rb_v2=...]` inside `notes` alongside `[dr_item=...]`), and persist the active session logging metric via `[log_metric=REPS|CALORIES|TIME_SECONDS|METERS]` inside `SessionBlock.notes` (passed via `BlockDraft.notes` in `AppViewModel.buildBlockInserts`).
- **Rationale**: Room DB stays at schema v10 (`Constitution Principle V`), Firestore cloud sync (`UserCloudSyncManager.kt`) already round-trips `RoutineBlock.setsCount`, `RoutineBlock.targetRepsScheme`, `RoutineBlock.notes`, `SessionBlock.notes`, and `BlockSet.reps/weight/metricValue/groupIndex` with zero rule or field changes.
- **Alternatives Considered**: Room v11 migration adding columns to `routine_blocks` and `session_blocks`. Rejected per Constitution Principle V ("features that can be derived from existing entities MUST avoid unnecessary schema changes").

### Decision 2: Header Order Independence in `RoutineTargetCodec`
- **Decision**: Update `RoutineTargetCodec.hasStructuredHeader` and `decodeHeaderSlots` to iterate through all leading bracketed metadata header lines (`[rb_v2=...]`, `[ab_prs=...]`, `[dr_item=...]`, `[log_metric=...]`), rather than assuming `[rb_v2=...]` is strictly on line 1.
- **Rationale**: Allows both `[rb_v2=...]\n[dr_item=...]` and `[dr_item=...]\n[rb_v2=...]` (or `[ab_prs=...]`) to decode seamlessly across Android and MCP. When targets are unset on a Daily Routine item, `[rb_v2=...]` is omitted completely so existing items remain byte-identical.
- **Alternatives Considered**: Strict single-line ordering; brittle across sync or legacy records.

### Decision 3: Metric-Aware Set Tiles & Input Caching in `SimplifiedBlockLogComponents`
- **Decision**: Keep the card-based UX (`SimplifiedExerciseLogCard` & `SimplifiedBlockLogCard`), adding single-line per-exercise metric chips (`Reps`, `Cal`, `Time`, `Meters`) with an in-editor per-metric cache (`Map<ExerciseTargetMetric, CachedSetInputs>`). `REPS` renders responsive 2-column (1 at `fontScale >= 1.5f`, 4 on `>= 600dp`) stacked `Reps` + optional `Weight` tiles; `CALORIES` / `TIME_SECONDS` / `METERS` render responsive 3-column (2 at `fontScale >= 1.3f`, 4 on `>= 600dp`) metric tiles.
- **Rationale**: Preserves the unified simplified card design (`SPEC-021`/`SPEC-025`) while restoring bodyweight reps, weighted reps, calories, time, and meters across both `EXERCISE:` and `BLOCK:` cards without overflowing `360dp` screens (`Constitution Principle IV`).
- **Alternatives Considered**: Reverting to legacy `CompactBlockEditor` tables (rejected by stakeholder in `SPEC-025`) or showing separate modal dialogs per set.

### Decision 4: Edit Session Hydration Boundary (`AvailableBlockCodec.isResidualLegacyBlock`)
- **Decision**: Hydrate unweighted rep-only sets, `metricValue` sets (`cal`/`s`/`m`), and `groupIndex` round-indexed sets into `SimplifiedBlockLogCard` / `SimplifiedExerciseLogCard`. Retain ONLY warm-up (`isWarmup`), failed (`isFailed`), and unmatched Metcon/score blocks (`resultText` / `resultValue` / `BlockKind.METCON` without logged sets/values) on `CompactBlockEditor`.
- **Rationale**: Previously, `isResidualLegacyBlock` rejected any block with `(it.weight ?: 0.0) <= 0.0` or `metricValue > 0.0` or `groupIndex > 0`, forcing edited bodyweight/cal/time/meters/skipped-round sessions back onto the legacy table. Updating `AvailableBlockCodecTest.kt` and `isResidualLegacyBlock` fixes round-trip Edit Session UX while protecting warm-ups, failed lifts, and Metcon WOD scores.
- **Alternatives Considered**: Rewriting `CompactBlockEditor` for everything; would break the simplified workout logging experience.

### Decision 5: Bodyweight & Metric-Value Analytics (`BlockPerformance`, `DailyRoutineCodec`, `HistoryScreen`, `ProgressScreen`)
- **Decision**: Compute `BlockPerformance.workingSetValues` cleanly across `weight`, `metricValue`, and multi-set/explicit-rep bodyweight sets (`reps > 0`), pass constituent `item.unit` (`kg`, `reps`, `cal`, `s`, `m`) into `DailyRoutineConstituentDrillDown`, and format set pills in `HistoryScreen` and `ProgressScreen` (`BlockHistoryCard`) cleanly without `×1` artifacts on `cal`/`s`/`m` sets.
- **Rationale**: Ensures bodyweight and conditioning exercises inside Daily Routines produce meaningful pre-fills, history tokens, KPI summaries, and trend charts (`Constitution Principle II`).

### Decision 6: MCP Flat Schemas & Backward Compatibility (`mcp-server/`)
- **Decision**: Extend `makeDailyRoutineItemInputSchema`, `makeDailyRoutineLoggedItemInputSchema`, `makeDailyRoutineBlockExerciseLogSchema`, `makeBlockExerciseLogInputSchema`, and `makeUpdateBlockExerciseInputSchema` using factory functions with optional fields (`sets_count`, `target_metric`, `target_mode`, `fixed_value`, `per_set_values`, `exercise_targets`, `metric`, `sets`). Keep `tools/list` at 39 tools with `0` `$ref` and `0` `exclusiveMinimum`.
- **Rationale**: Satisfies `Constitution Principle VII` and guarantees zero breaking changes for existing MCP callers passing `set_weights_kg` / `weights`.
