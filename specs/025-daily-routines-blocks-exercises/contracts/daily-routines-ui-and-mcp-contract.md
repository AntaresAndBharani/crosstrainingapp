# UI & MCP Interface Contracts: Daily Routines (`SPEC-025`)

## 1. Android UI & Navigation Contracts

### `LibraryScreen` (`SPEC-DAILY-ROUTINES-LIB-01`)
- **Top Tabs (`TabRow`)**:
  - Tab 0: `Routines (${dailyRoutines.size})`
  - Tab 1: `Blocks (${availableBlocks.size})`
  - Tab 2: `Exercises (${exercises.size})`
  - Single-line labels (`maxLines = 1`, `overflow = TextOverflow.Ellipsis`) fitting `360dp` screens cleanly.
- **`DailyRoutineCard`**:
  - Title + `Log Routine ▸` pill (`HapticFeedbackConstants.CLOCK_TICK`, `heightIn(min = 48.dp)`) + `⋮` (`Edit`, `Duplicate`, `Delete`).
  - Constituent `FlowRow` chips: `BLOCK: {BlockTitle}` (`#E0F2FE` / `#0369A1` light, `#1E3A5F` / `#7DD3FC` dark) and `EX: {ExerciseName}` or `EX: {ExerciseName} ({note})` (`#EAE6F0` / `#4A356B` light, `#2E2540` / `#D6BCFA` dark); collapses beyond 6 chips with `+N more` pill; optional warning chip `"{N} removed block(s) hidden"` with remove/clean action.
  - Footer telemetry: `"{B} Blocks · {E} Exercises · Last: {Date}"` (or `"Not logged yet"` when unlogged) + `"🔥 {N} tracked PR(s)"`.
- **`DailyRoutineComposerSheet`**:
  - `ModalBottomSheet(skipPartiallyExpanded = true)` + `Modifier.imePadding()`.
  - Fields: `Routine Title *`, `Routine Notes (optional)`, `ROUTINE ITEMS ({N})` (`↑`, `↓`, `✕`, inline `Note (optional)` for `EXERCISE`), dual buttons `[ + Add Block ]` & `[ + Add Exercise ]`, `[ Save Daily Routine ]`.

### `LogSessionScreen` / `SessionEditor` (`SPEC-ROUTINE-SESSION-TRACK-01`)
- **Top Routine Bar**: `[ 📋 Load Daily Routine: {Title or Select...} ▾ ]` + optional `[ ✕ ]` clear button.
- **Session Item Cards**:
  - `SimplifiedBlockLogCard`: `{idx + 1}  BLOCK: {Title}` (`#0369A1`/`#7DD3FC`), prescription banner, 4/2-col `Set 1..N` tiles (`"Pre-filled from last session ({Date})"` or `"First time logging — enter set weights"`), `+ Add Set`, `Tracked Basic Movement PRs` + `+ Record PR 🔥`.
  - `SimplifiedExerciseLogCard`: `{idx + 1}  EXERCISE: {Name}` (`#0B6E3F`/`#2ECC71`), optional `Routine Note` box, 4/2-col `Set 1..N` tiles (`"Pre-filled from last session ({Date})"` or `"First time logging — enter set weights"`), `+ Add Set`, `Tracked Basic Movement PRs` + `+ Record PR 🔥`.
- **Conditional Routine Sync**: `[ ] Also update "{Routine Title}" routine template` shown above bottom actions only when `loadedDailyRoutine != null && hasStructuralDiffFromRoutine`.
- **Bottom Action Row**: `[ + Add Block ]` + `[ + Add Exercise ]`, `[ ✨ Quick Parse Workout ]`, `[ Save Session ({N} New PRs) ]`.

### `ProgressScreen` (`SPEC-ROUTINE-PROGRESS-01`)
- **Top Filter Rail**: `By routine` | `By block` | `By exercise` | `Cycle goals` | `Fat loss` | `Body weight`.
- **Sub-Chips (`By routine`)**: `All Items ({count})` | `BLOCK: {Title}` | `EX: {Name}`.
- **Summary Card (`All Items`)**: `[BLOCK]`/`[EXERCISE]` badge, trend pill (`↗ +X kg` / `→ +0.0 kg` / `↘ -X kg` / `No logs yet`), All-Time Top, Last Top (`Date`), Last Sets, Tracked PR pills, `View Chart ▸`.
- **Drill-Down View**: `[ ⬅ All Items ]`, item header & note, 2x2 `KpiRows`, `EvolutionCards`, Tracked PR pills, `BlockHistoryCard`.

## 2. Remote MCP Tool Contracts (`SPEC-MCP-DAILY-ROUTINES-01`)

All 5 tools emit flat JSON schemas (`0` `$ref`, `0` `exclusiveMinimum`) and declare explicit `ToolAnnotations`:

1. **`list_daily_routines`** (`readOnlyHint: true`, `destructiveHint: false`, `idempotentHint: true`, `openWorldHint: false`)
   - Input: `{ query?: string, limit?: int }`
   - Output: `{ count: number, dailyRoutines: DailyRoutineRecord[] }`
2. **`upsert_daily_routine`** (`readOnlyHint: false`, `destructiveHint: false`, `idempotentHint: true`, `openWorldHint: false`)
   - Input: `{ routine_id?: int, title: string, notes?: string, items: Array<{ item_type: "BLOCK"|"EXERCISE", block_id?: int, block_title?: string, exercise_name?: string, note?: string, default_tracked_prs?: Array<{ movement_name: string, rm_reps: int }> }> }`
   - Output: `{ status: "ok", dailyRoutine: DailyRoutineRecord }`
3. **`delete_daily_routine`** (`readOnlyHint: false`, `destructiveHint: true`, `idempotentHint: true`, `openWorldHint: false`)
   - Input: `{ routine_id?: int, title?: string }`
   - Output: `{ status: "deleted"|"not_found", deletedCount: number, deletedTitles: string[] }`
4. **`log_daily_routine_session`** (`readOnlyHint: false`, `destructiveHint: false`, `idempotentHint: true`, `openWorldHint: false`)
   - Input: `{ date: "YYYY-MM-DD", routine_id?: int, routine_title?: string, session_title?: string, notes?: string, cycle_id?: int, update_routine_template?: boolean, logged_items: Array<{ item_type: "BLOCK"|"EXERCISE", block_id?: int, block_title?: string, exercise_name?: string, note?: string, set_weights_kg?: number[], exercise_logs?: Array<{ exercise_name: string, weights: number[], set_count_for_empty?: int }>, achieved_prs?: Array<{ movement_name: string, rm_reps: int, weight_kg: number }> }> }`
   - Output: `{ status: "ok", session: SessionWithBlocksRecord, recordedPrsCount: number, recordedPrs: RepMaxRecord[], updatedRoutineTemplate: DailyRoutineRecord | null }`
5. **`get_daily_routine_progress`** (`readOnlyHint: true`, `destructiveHint: false`, `idempotentHint: true`, `openWorldHint: false`)
   - Input: `{ routine_id?: int, routine_title?: string }`
   - Output: `{ dailyRoutine: DailyRoutineRecord, items: Array<{ itemType, title, subtitle, allTimeTopKg, lastTopKg, lastTopDate, lastAverageKg, lastVolumeKg, topDeltaKg, lastSetsKg, trackedPrs, chartPoints, recentSessions }> }`
