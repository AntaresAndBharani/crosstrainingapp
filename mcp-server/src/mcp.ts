import { McpServer } from "@modelcontextprotocol/sdk/server/mcp.js";
import type { ToolAnnotations } from "@modelcontextprotocol/sdk/types.js";
import { z } from "zod";
import {
  BatchRecordRepMaxesInputSchema,
  BatchUpsertAvailableBlocksInputSchema,
  BatchUpsertComplexesInputSchema,
  BatchUpsertDailyLogsInputSchema,
  BatchUpsertExercisesInputSchema,
  BatchUpsertWeightEntriesInputSchema,
  computeWeightMomentumAnalytics,
  DeleteAvailableBlockInputSchema,
  DeleteComplexInputSchema,
  DeleteCycleGoalInputSchema,
  DeleteDailyLogInputSchema,
  DeleteDailyRoutineInputSchema,
  DeleteRoutineInputSchema,
  DeleteSessionInputSchema,
  DeleteWeightEntryInputSchema,
  GetDailyRoutineProgressInputSchema,
  GetWeightMomentumAnalyticsInputSchema,
  ListAvailableBlocksInputSchema,
  ListComplexesInputSchema,
  ListCycleGoalsInputSchema,
  ListDailyRoutinesInputSchema,
  ListExercisesInputSchema,
  ListSessionsInputSchema,
  LogBlockSessionInputSchema,
  LogComplexSessionInputSchema,
  LogDailyRoutineSessionInputSchema,
  makeListDateRangeInputSchema,
  ManageCycleGoalsInputSchema,
  RecordRepMaxInputSchema,
  UpdateBlockSessionLogInputSchema,
  UpdateComplexSessionLogInputSchema,
  UpsertAvailableBlockInputSchema,
  UpsertComplexInputSchema,
  UpsertCycleGoalInputSchema,
  UpsertDailyLogInputSchema,
  UpsertDailyRoutineInputSchema,
  UpsertExerciseInputSchema,
  UpsertRoutineInputSchema,
  UpsertSessionInputSchema,
  UpsertWeightEntryInputSchema,
} from "./schemas.js";
import type { UserContext, WorkoutDomainService } from "./store.js";

const READ_ONLY_ANNOTATIONS: ToolAnnotations = {
  readOnlyHint: true,
  destructiveHint: false,
  idempotentHint: true,
  openWorldHint: false,
};

const IDEMPOTENT_WRITE_ANNOTATIONS: ToolAnnotations = {
  readOnlyHint: false,
  destructiveHint: false,
  idempotentHint: true,
  openWorldHint: false,
};

const DELETE_ANNOTATIONS: ToolAnnotations = {
  readOnlyHint: false,
  destructiveHint: true,
  idempotentHint: true,
  openWorldHint: false,
};

export const CROSSTRAINING_MCP_INSTRUCTIONS = [
  "CrossTraining Workout Assistant Connector rules:",
  "1. ACTIVE CYCLE: Never assume Cycle 1 is the active cycle. Call `get_active_cycle` (or `get_workout_summary`) when the user asks for their current/active cycle or goals. Whenever `cycleId` is omitted on goal, session, or rep-max mutations, the server automatically targets the user's resolved active cycle.",
  "2. BATCH MULTI-ORDER PROMPTS IN ONE CALL: When a user message contains multiple orders (e.g. changing 2RM Snatch to 3RM Snatch AND adding several new goals, or logging multiple exercises, PRs, weigh-ins, or daily logs), ALWAYS combine them into a single batch tool call (`manage_cycle_goals`, `batch_upsert_complexes`, `batch_upsert_exercises`, `batch_record_rep_maxes`, `batch_upsert_weight_entries`, `batch_upsert_daily_logs`) instead of chaining multiple single-item tool calls. In `manage_cycle_goals`, set `previousTargetReps` on an upsert item to convert an RM goal (such as 2RM -> 3RM) in one atomic step.",
  "3. COMPLEXES & MULTI-METRIC SESSION LOGGING: Prefer `list_complexes`, `upsert_complex`, `batch_upsert_complexes`, and `delete_complex` when managing standalone Complex cards (legacy `*_available_block` aliases remain supported; `[ab_prs=...]` tracked PR slots and free-form instructions), use `log_complex_session` (legacy `log_block_session` alias) when logging complex sets (`shared_set_weights_kg` with optional `exercise_targets` skip patterns, per-exercise `weights`, or multi-metric `metric` + `sets` with `reps`/`weight_kg`/`metric_value`/`group_index`; pass `weights: []` for unloaded/bodyweight movements) along with optional `recordedPrs` that auto-link to the session/complex and persist on the complex template, and use `update_complex_session_log` (legacy `update_block_session_log` alias) when editing an existing logged complex session (updating shared or per-exercise set weights/metrics/reps/counts, notes, date, or tracked PRs while reconciling/tombstoning replaced or removed session PRs).",
  "4. COMPOSITE DAILY ROUTINES (COMPLEXES + STANDALONE EXERCISES): Use `list_daily_routines`, `upsert_daily_routine`, and `delete_daily_routine` to compose reusable workout-day templates (`defaultFormat: 'DAILY_ROUTINE'`) made of ordered Saved Complexes (`item_type: 'COMPLEX'`, legacy `'BLOCK'` alias, with optional per-routine `sets_count` + `exercise_targets`) and standalone Exercises (`item_type: 'EXERCISE'`, with optional `note`, `default_tracked_prs`, and optional `sets_count`/`target_metric`/`target_mode`/`fixed_value`/`per_set_values`). Use `log_daily_routine_session` to log a mixed Complex + standalone Exercise (`STANDALONE_EXERCISE`) workout with `shared_set_weights_kg` (honoring saved `exercise_targets` 0-skip rounds), `set_weights_kg`/`weights`, or multi-metric `metric` + `sets` (pass `update_routine_template: true` if the user adds/reorders items and wants the template updated), and use `get_daily_routine_progress` for unified constituent trends (`kg`/`reps`/`cal`/`s`/`m`), PRs, charts, and set history across all historical sessions.",
].join("\n");

function jsonResult(data: unknown) {
  return {
    content: [
      {
        type: "text" as const,
        text: JSON.stringify(data, null, 2),
      },
    ],
  };
}

/**
 * Creates a configured McpServer instance scoped to a specific authenticated user context.
 */
export function createCrossTrainingMcpServer(
  domain: WorkoutDomainService,
  getUserContext: () => UserContext
): McpServer {
  const server = new McpServer(
    {
      name: "crosstraining-workout-mcp",
      version: "1.1.0",
    },
    {
      instructions: CROSSTRAINING_MCP_INSTRUCTIONS,
    }
  );

  // ==========================================================================
  // 0. Cycle Discovery & High-Level Summary
  // ==========================================================================

  server.registerTool(
    "get_active_cycle",
    {
      title: "Get Active Training Cycle",
      description:
        "Retrieve the user's currently active training cycle, including its cycleId, cycleName, all active cycle goals, recent workout sessions, and a summary of all known cycles.",
      inputSchema: {},
      annotations: READ_ONLY_ANNOTATIONS,
    },
    async () => {
      const info = await domain.getActiveCycle(getUserContext());
      return jsonResult(info);
    }
  );

  server.registerTool(
    "list_cycles",
    {
      title: "List Training Cycles",
      description:
        "List all training cycles discovered in the user's workout history, highlighting which cycle is currently active (`isActive: true`), goal counts, session counts, and latest activity dates.",
      inputSchema: {},
      annotations: READ_ONLY_ANNOTATIONS,
    },
    async () => {
      const res = await domain.listCycles(getUserContext());
      return jsonResult({
        activeCycleId: res.activeCycleId,
        activeCycleName: res.activeCycleName,
        count: res.cycles.length,
        cycles: res.cycles,
      });
    }
  );

  server.registerTool(
    "get_workout_summary",
    {
      title: "Get Workout Account Summary",
      description:
        "Retrieve a consolidated overview of the user's active training cycle, active cycle goals, recent sessions, catalog counts, latest body weight, and latest daily nutrition/fasting log.",
      inputSchema: {},
      annotations: READ_ONLY_ANNOTATIONS,
    },
    async () => {
      const summary = await domain.getWorkoutSummary(getUserContext());
      return jsonResult(summary);
    }
  );

  // ==========================================================================
  // 1. Exercises
  // ==========================================================================

  server.registerTool(
    "list_exercises",
    {
      title: "List Exercises",
      description:
        "List exercises in the user's CrossTraining movement catalog, optionally filtered by category or name substring.",
      inputSchema: ListExercisesInputSchema,
      annotations: READ_ONLY_ANNOTATIONS,
    },
    async (args) => {
      const exercises = await domain.listExercises(getUserContext(), args);
      return jsonResult({ count: exercises.length, exercises });
    }
  );

  server.registerTool(
    "upsert_exercise",
    {
      title: "Upsert Exercise",
      description:
        "Create or update a single exercise in the user's CrossTraining catalog. When creating/updating 2 or more exercises, prefer `batch_upsert_exercises`.",
      inputSchema: UpsertExerciseInputSchema,
      annotations: IDEMPOTENT_WRITE_ANNOTATIONS,
    },
    async (args) => {
      const ex = await domain.getOrCreateExercise(getUserContext(), args);
      return jsonResult({ status: "ok", exercise: ex });
    }
  );

  server.registerTool(
    "batch_upsert_exercises",
    {
      title: "Batch Upsert Exercises",
      description:
        "Create or update multiple exercises in the user's CrossTraining catalog in a single atomic call.",
      inputSchema: BatchUpsertExercisesInputSchema,
      annotations: IDEMPOTENT_WRITE_ANNOTATIONS,
    },
    async ({ exercises }) => {
      const upserted = await domain.batchUpsertExercises(getUserContext(), exercises);
      return jsonResult({
        status: "ok",
        upsertedCount: upserted.length,
        exercises: upserted,
      });
    }
  );

  // ==========================================================================
  // 2. Routines (Workout Templates)
  // ==========================================================================

  server.registerTool(
    "list_routines",
    {
      title: "List Workout Routines",
      description:
        "List all saved daily workout routine templates and their ordered blocks, enriched with structured per-exercise targets (`metric`, `mode`, `fixedValue`, `perSetValues` with `0` = Skip, `summary`) and `cleanNotes`.",
      inputSchema: {},
      annotations: READ_ONLY_ANNOTATIONS,
    },
    async () => {
      const routines = await domain.listRoutines(getUserContext());
      return jsonResult({ count: routines.length, routines });
    }
  );

  server.registerTool(
    "upsert_routine",
    {
      title: "Upsert Workout Routine",
      description:
        "Create or update a daily workout routine template with ordered blocks. Prefer `blocks[].exercises` to specify mixed metrics (`REPS`, `CALORIES`, `TIME_SECONDS`, `METERS`), `FIXED` or `PER_SET` targets (`0` = Skip set), and repeated sequence movements; legacy `exerciseNames` + `targetRepsScheme` remain supported.",
      inputSchema: UpsertRoutineInputSchema,
      annotations: IDEMPOTENT_WRITE_ANNOTATIONS,
    },
    async (args) => {
      const routine = await domain.upsertRoutine(getUserContext(), args);
      return jsonResult({ status: "ok", routine });
    }
  );

  server.registerTool(
    "delete_routine",
    {
      title: "Delete Workout Routine",
      description:
        "Delete a workout routine template by name (propagates cleanly to mobile sync via tombstone).",
      inputSchema: DeleteRoutineInputSchema,
      annotations: DELETE_ANNOTATIONS,
    },
    async ({ name }) => {
      const deleted = await domain.deleteRoutine(getUserContext(), name);
      return jsonResult({ status: deleted ? "deleted" : "not_found", name });
    }
  );

  // ==========================================================================
  // 3. Workout Sessions
  // ==========================================================================

  server.registerTool(
    "list_sessions",
    {
      title: "List Workout Sessions",
      description:
        "List logged or scheduled workout sessions with blocks and sets, optionally filtered by date range, cycleId, or activeCycleOnly.",
      inputSchema: ListSessionsInputSchema,
      annotations: READ_ONLY_ANNOTATIONS,
    },
    async (args) => {
      const sessions = await domain.listSessions(getUserContext(), args);
      return jsonResult({ count: sessions.length, sessions });
    }
  );

  server.registerTool(
    "upsert_session",
    {
      title: "Upsert Workout Session",
      description:
        "Log or update a workout session for a specific date with blocks and sets. Omit `cycleId` to automatically attach the session to the active cycle.",
      inputSchema: UpsertSessionInputSchema,
      annotations: IDEMPOTENT_WRITE_ANNOTATIONS,
    },
    async (args) => {
      const session = await domain.upsertSession(getUserContext(), args);
      return jsonResult({ status: "ok", session });
    }
  );

  server.registerTool(
    "delete_session",
    {
      title: "Delete Workout Session",
      description:
        "Delete a workout session by date and title (propagates cleanly to mobile sync via tombstone).",
      inputSchema: DeleteSessionInputSchema,
      annotations: DELETE_ANNOTATIONS,
    },
    async ({ date, title }) => {
      const deleted = await domain.deleteSession(getUserContext(), date, title);
      return jsonResult({
        status: deleted ? "deleted" : "not_found",
        date,
        title,
      });
    }
  );

  // ==========================================================================
  // 4. Cycle Goals (Single & Multi-Order Atomic Batch)
  // ==========================================================================

  server.registerTool(
    "list_cycle_goals",
    {
      title: "List Cycle Goals",
      description:
        "List training cycle goals (weight RM targets, calorie window goals, or rep goals), enriched with `activeCycleId` and `isActiveCycle`. Pass `activeCycleOnly: true` to return only the active cycle's goals.",
      inputSchema: ListCycleGoalsInputSchema,
      annotations: READ_ONLY_ANNOTATIONS,
    },
    async (args) => {
      const res = await domain.listCycleGoals(getUserContext(), args);
      return jsonResult({
        activeCycleId: res.activeCycleId,
        activeCycleName: res.activeCycleName,
        count: res.cycleGoals.length,
        cycleGoals: res.cycleGoals,
      });
    }
  );

  server.registerTool(
    "manage_cycle_goals",
    {
      title: "Batch Manage Cycle Goals",
      description:
        "PREFERRED FOR MULTI-ORDER GOAL REQUESTS: Atomically create, update, convert RM schemes (via `previousTargetReps`, e.g. 2RM -> 3RM), and delete any number of cycle goals in ONE tool call. Omit `cycleId` to automatically target the user's active cycle; missing exercises are created automatically.",
      inputSchema: ManageCycleGoalsInputSchema,
      annotations: IDEMPOTENT_WRITE_ANNOTATIONS,
    },
    async (args) => {
      const res = await domain.manageCycleGoals(getUserContext(), args);
      return jsonResult({
        status: "ok",
        cycle: res.cycle,
        upsertedCount: res.upserted.length,
        deletedCount: res.deleted.filter((d) => d.deleted).length,
        upserted: res.upserted,
        deleted: res.deleted,
      });
    }
  );

  server.registerTool(
    "upsert_cycle_goal",
    {
      title: "Upsert Single Cycle Goal",
      description:
        "Create, update, or RM-convert a single training cycle goal (defaults to the active cycle when `cycleId` is omitted). IMPORTANT: When the user asks to add, update, or convert 2 or more goals in one message, ALWAYS use `manage_cycle_goals` instead so all orders execute in one confirmation.",
      inputSchema: UpsertCycleGoalInputSchema,
      annotations: IDEMPOTENT_WRITE_ANNOTATIONS,
    },
    async (args) => {
      const goal = await domain.upsertCycleGoal(getUserContext(), args);
      return jsonResult({ status: "ok", cycleGoal: goal });
    }
  );

  server.registerTool(
    "delete_cycle_goal",
    {
      title: "Delete Cycle Goal",
      description:
        "Delete a training cycle goal for an exercise (defaults to the active cycle when `cycleId` is omitted). When deleting or converting goals alongside other goal changes, use `manage_cycle_goals` to perform everything in one call.",
      inputSchema: DeleteCycleGoalInputSchema,
      annotations: DELETE_ANNOTATIONS,
    },
    async (args) => {
      const deleted = await domain.deleteCycleGoal(getUserContext(), args);
      return jsonResult({
        status: deleted ? "deleted" : "not_found",
        exerciseName: args.exerciseName,
      });
    }
  );

  // ==========================================================================
  // 5. Rep Maxes (Personal Records)
  // ==========================================================================

  server.registerTool(
    "list_rep_maxes",
    {
      title: "List Rep Maxes",
      description:
        "List personal record (PR / Rep Max) history, optionally filtered by exerciseName.",
      inputSchema: {
        exerciseName: z
          .string()
          .max(200)
          .optional()
          .describe("Optional exercise name to filter PR records"),
      },
      annotations: READ_ONLY_ANNOTATIONS,
    },
    async ({ exerciseName }) => {
      const repMaxes = await domain.listRepMaxes(getUserContext(), exerciseName);
      return jsonResult({ count: repMaxes.length, repMaxes });
    }
  );

  server.registerTool(
    "record_rep_max",
    {
      title: "Record Rep Max",
      description:
        "Record a single personal record (Rep Max, e.g. 1RM, 3RM, 5RM) for an exercise (defaults to the active cycle when `cycleId` is omitted). When recording multiple PRs at once, prefer `batch_record_rep_maxes`.",
      inputSchema: RecordRepMaxInputSchema,
      annotations: IDEMPOTENT_WRITE_ANNOTATIONS,
    },
    async (args) => {
      const repMax = await domain.recordRepMax(getUserContext(), args);
      return jsonResult({ status: "ok", repMax });
    }
  );

  server.registerTool(
    "batch_record_rep_maxes",
    {
      title: "Batch Record Rep Maxes",
      description:
        "Record multiple personal records (Rep Maxes) across one or more exercises in a single atomic call.",
      inputSchema: BatchRecordRepMaxesInputSchema,
      annotations: IDEMPOTENT_WRITE_ANNOTATIONS,
    },
    async ({ repMaxes }) => {
      const recorded = await domain.batchRecordRepMaxes(getUserContext(), repMaxes);
      return jsonResult({
        status: "ok",
        recordedCount: recorded.length,
        repMaxes: recorded,
      });
    }
  );

  // ==========================================================================
  // 6. Body Weight Tracking & Momentum Analytics
  // ==========================================================================

  server.registerTool(
    "get_weight_momentum_analytics",
    {
      title: "Get Weight Momentum Analytics",
      description:
        "Compute multi-horizon (7D/14D/21D/28D) moving averages, 3:4:2:1 Weekly Body Weight composite, weekly momentum %, subcycle corridor status, projected finish weight window, and Momentum/Pace end-of-cycle estimated finish weights.",
      inputSchema: GetWeightMomentumAnalyticsInputSchema,
      annotations: READ_ONLY_ANNOTATIONS,
    },
    async (args) => {
      const entries = await domain.listWeightEntries(getUserContext(), {});
      const analytics = computeWeightMomentumAnalytics(entries, {
        subcycleType: args.subcycleType,
        cycleName: args.cycleName,
        cycleStartDate: args.cycleStartDate,
        cycleFinishDate: args.cycleFinishDate,
        asOfDate: args.asOfDate,
        includeDailyTimeline: args.includeDailyTimeline,
      });
      return jsonResult(analytics);
    }
  );

  server.registerTool(
    "list_weight_entries",
    {
      title: "List Weight Entries",
      description: "List body weight entries (kg) within an optional date range.",
      inputSchema: makeListDateRangeInputSchema(),
      annotations: READ_ONLY_ANNOTATIONS,
    },
    async (args) => {
      const entries = await domain.listWeightEntries(getUserContext(), args);
      return jsonResult({ count: entries.length, weightEntries: entries });
    }
  );

  server.registerTool(
    "upsert_weight_entry",
    {
      title: "Upsert Weight Entry",
      description:
        "Log or update a single body weight entry (in kg) for a date. When logging multiple weigh-ins, prefer `batch_upsert_weight_entries`.",
      inputSchema: UpsertWeightEntryInputSchema,
      annotations: IDEMPOTENT_WRITE_ANNOTATIONS,
    },
    async (args) => {
      const entry = await domain.upsertWeightEntry(getUserContext(), args);
      return jsonResult({ status: "ok", weightEntry: entry });
    }
  );

  server.registerTool(
    "batch_upsert_weight_entries",
    {
      title: "Batch Upsert Weight Entries",
      description:
        "Log or update multiple body weight entries (in kg) across dates in a single atomic call.",
      inputSchema: BatchUpsertWeightEntriesInputSchema,
      annotations: IDEMPOTENT_WRITE_ANNOTATIONS,
    },
    async ({ entries }) => {
      const upserted = await domain.batchUpsertWeightEntries(getUserContext(), entries);
      return jsonResult({
        status: "ok",
        upsertedCount: upserted.length,
        weightEntries: upserted,
      });
    }
  );

  server.registerTool(
    "delete_weight_entry",
    {
      title: "Delete Weight Entry",
      description: "Soft-delete a body weight entry for a date.",
      inputSchema: DeleteWeightEntryInputSchema,
      annotations: DELETE_ANNOTATIONS,
    },
    async ({ date }) => {
      const deleted = await domain.deleteWeightEntry(getUserContext(), date);
      return jsonResult({ status: deleted ? "deleted" : "not_found", date });
    }
  );

  // ==========================================================================
  // 7. Daily Logs (Fasting, Rest Days, Calories & Macros)
  // ==========================================================================

  server.registerTool(
    "list_daily_logs",
    {
      title: "List Daily Logs",
      description:
        "List daily nutrition, fasting, and rest-day logs within an optional date range.",
      inputSchema: makeListDateRangeInputSchema(),
      annotations: READ_ONLY_ANNOTATIONS,
    },
    async (args) => {
      const logs = await domain.listDailyLogs(getUserContext(), args);
      return jsonResult({ count: logs.length, dailyLogs: logs });
    }
  );

  server.registerTool(
    "upsert_daily_log",
    {
      title: "Upsert Daily Log",
      description:
        "Log or update daily nutrition macros, calories, intermittent fasting completion, or rest day status for a single date. When logging multiple dates, prefer `batch_upsert_daily_logs`.",
      inputSchema: UpsertDailyLogInputSchema,
      annotations: IDEMPOTENT_WRITE_ANNOTATIONS,
    },
    async (args) => {
      const log = await domain.upsertDailyLog(getUserContext(), args);
      return jsonResult({ status: "ok", dailyLog: log });
    }
  );

  server.registerTool(
    "batch_upsert_daily_logs",
    {
      title: "Batch Upsert Daily Logs",
      description:
        "Log or update multiple daily nutrition, fasting, and rest-day entries in a single atomic call.",
      inputSchema: BatchUpsertDailyLogsInputSchema,
      annotations: IDEMPOTENT_WRITE_ANNOTATIONS,
    },
    async ({ logs }) => {
      const upserted = await domain.batchUpsertDailyLogs(getUserContext(), logs);
      return jsonResult({
        status: "ok",
        upsertedCount: upserted.length,
        dailyLogs: upserted,
      });
    }
  );

  server.registerTool(
    "delete_daily_log",
    {
      title: "Delete Daily Log",
      description: "Soft-delete a daily nutrition/fasting/rest log entry for a date.",
      inputSchema: DeleteDailyLogInputSchema,
      annotations: DELETE_ANNOTATIONS,
    },
    async ({ date }) => {
      const deleted = await domain.deleteDailyLog(getUserContext(), date);
      return jsonResult({ status: deleted ? "deleted" : "not_found", date });
    }
  );

  // ==========================================================================
  // 8. Complexes (Primary) & Legacy Available Block Aliases
  // ==========================================================================

  server.registerTool(
    "list_complexes",
    {
      title: "List Complexes",
      description:
        "List streamlined Complex templates (title, clean instructions, ordered exercises, `[ab_prs=...]` tracked PR slots) with optional most recent historical session weight summary.",
      inputSchema: ListComplexesInputSchema,
      annotations: READ_ONLY_ANNOTATIONS,
    },
    async (args) => {
      const complexes = await domain.listAvailableBlocks(getUserContext(), args);
      return jsonResult({ count: complexes.length, complexes });
    }
  );

  server.registerTool(
    "upsert_complex",
    {
      title: "Upsert Complex",
      description:
        "Create or update a single Complex card (`defaultFormat: 'AVAILABLE_BLOCK'`) with title, instructions, ordered exercises, and auto-inherited `[ab_prs=...]` tracked PR slots. When upserting 2 or more complexes, prefer `batch_upsert_complexes`.",
      inputSchema: UpsertComplexInputSchema,
      annotations: IDEMPOTENT_WRITE_ANNOTATIONS,
    },
    async (args) => {
      const complex = await domain.upsertAvailableBlock(getUserContext(), args);
      return jsonResult({ status: "ok", complex });
    }
  );

  server.registerTool(
    "batch_upsert_complexes",
    {
      title: "Batch Upsert Complexes",
      description:
        "Create or update multiple Complex cards atomically in a single Firestore transaction.",
      inputSchema: BatchUpsertComplexesInputSchema,
      annotations: IDEMPOTENT_WRITE_ANNOTATIONS,
    },
    async (args) => {
      const items = args.complexes ?? args.blocks ?? [];
      if (items.length === 0) {
        throw new Error("Provide at least one complex in `complexes` (or legacy `blocks`).");
      }
      const upserted = await domain.batchUpsertAvailableBlocks(getUserContext(), items);
      return jsonResult({
        status: "ok",
        upsertedCount: upserted.length,
        complexes: upserted,
      });
    }
  );

  server.registerTool(
    "delete_complex",
    {
      title: "Delete Complex",
      description:
        "Tombstone/delete a Complex by numeric `complex_id`/`id` or exact `title`/`complex_title` while preserving all historical logged workout sessions and PRs.",
      inputSchema: DeleteComplexInputSchema,
      annotations: DELETE_ANNOTATIONS,
    },
    async (args) => {
      const res = await domain.deleteAvailableBlock(getUserContext(), args);
      return jsonResult({
        status: res.deleted ? "deleted" : "not_found",
        deletedCount: res.deletedCount,
        deletedTitles: res.deletedTitles,
      });
    }
  );

  server.registerTool(
    "log_complex_session",
    {
      title: "Log Complex Session",
      description:
        "Log a Complex session with shared per-set weights (`shared_set_weights_kg` + optional `exercise_targets`), weight-only `weights`, or multi-metric `metric` + `sets` (`reps`, `weight_kg`, `metric_value`, `group_index`) and optional `recordedPrs` linked to session/complex/cycle and auto-inherited onto the matched complex's tracked PRs.",
      inputSchema: LogComplexSessionInputSchema,
      annotations: IDEMPOTENT_WRITE_ANNOTATIONS,
    },
    async (args) => {
      const res = await domain.logBlockSession(getUserContext(), args);
      return jsonResult({
        status: "ok",
        session: res.session,
        recordedPrsCount: res.recordedPrs.length,
        recordedPrs: res.recordedPrs,
        updatedTrackedPrs: res.updatedTrackedPrs,
      });
    }
  );

  server.registerTool(
    "update_complex_session_log",
    {
      title: "Update Complex Session Log",
      description:
        "Edit an existing logged Complex session in place (`shared_set_weights_kg`, per-exercise `weights`, or multi-metric `metric` + `sets`, complex/session notes, date, cycle, or tracked PRs), tombstoning replaced or removed session `rep_maxes` and auto-inheriting newly tracked 1RM-20RM PR slots onto the parent Complex.",
      inputSchema: UpdateComplexSessionLogInputSchema,
      annotations: IDEMPOTENT_WRITE_ANNOTATIONS,
    },
    async (args) => {
      const res = await domain.updateBlockSessionLog(getUserContext(), args);
      return jsonResult({
        status: "ok",
        session: res.session,
        reconciledPrsCount: res.reconciledPrs.length,
        reconciledPrs: res.reconciledPrs,
        tombstonedRepMaxIds: res.tombstonedRepMaxIds,
        updatedTrackedPrs: res.updatedTrackedPrs,
      });
    }
  );

  server.registerTool(
    "list_available_blocks",
    {
      title: "List Available Blocks",
      description:
        "(Legacy alias for `list_complexes`) List saved Complex templates (`[ab_prs=...]` tracked PR slots) with optional most recent historical session weight summary.",
      inputSchema: ListAvailableBlocksInputSchema,
      annotations: READ_ONLY_ANNOTATIONS,
    },
    async (args) => {
      const availableBlocks = await domain.listAvailableBlocks(getUserContext(), args);
      return jsonResult({ count: availableBlocks.length, availableBlocks });
    }
  );

  server.registerTool(
    "upsert_available_block",
    {
      title: "Upsert Available Block",
      description:
        "(Legacy alias for `upsert_complex`) Create or update a single Complex card (`defaultFormat: 'AVAILABLE_BLOCK'`) with title, instructions, ordered exercises, and auto-inherited `[ab_prs=...]` tracked PR slots.",
      inputSchema: UpsertAvailableBlockInputSchema,
      annotations: IDEMPOTENT_WRITE_ANNOTATIONS,
    },
    async (args) => {
      const availableBlock = await domain.upsertAvailableBlock(getUserContext(), args);
      return jsonResult({ status: "ok", availableBlock });
    }
  );

  server.registerTool(
    "batch_upsert_available_blocks",
    {
      title: "Batch Upsert Available Blocks",
      description:
        "(Legacy alias for `batch_upsert_complexes`) Create or update multiple Complex cards atomically in a single Firestore transaction.",
      inputSchema: BatchUpsertAvailableBlocksInputSchema,
      annotations: IDEMPOTENT_WRITE_ANNOTATIONS,
    },
    async (args) => {
      const items = args.complexes ?? args.blocks ?? [];
      if (items.length === 0) {
        throw new Error("Provide at least one complex in `complexes` (or legacy `blocks`).");
      }
      const upserted = await domain.batchUpsertAvailableBlocks(getUserContext(), items);
      return jsonResult({
        status: "ok",
        upsertedCount: upserted.length,
        availableBlocks: upserted,
      });
    }
  );

  server.registerTool(
    "delete_available_block",
    {
      title: "Delete Available Block",
      description:
        "(Legacy alias for `delete_complex`) Tombstone/delete a Complex by numeric `id` or exact `title` while preserving all historical logged workout sessions and PRs.",
      inputSchema: DeleteAvailableBlockInputSchema,
      annotations: DELETE_ANNOTATIONS,
    },
    async (args) => {
      const res = await domain.deleteAvailableBlock(getUserContext(), args);
      return jsonResult({
        status: res.deleted ? "deleted" : "not_found",
        deletedCount: res.deletedCount,
        deletedTitles: res.deletedTitles,
      });
    }
  );

  server.registerTool(
    "log_block_session",
    {
      title: "Log Block Session",
      description:
        "(Legacy alias for `log_complex_session`) Log a Complex session with `shared_set_weights_kg`, weight-only `weights`, or multi-metric `metric` + `sets` and optional `recordedPrs` linked to session/complex/cycle.",
      inputSchema: LogBlockSessionInputSchema,
      annotations: IDEMPOTENT_WRITE_ANNOTATIONS,
    },
    async (args) => {
      const res = await domain.logBlockSession(getUserContext(), args);
      return jsonResult({
        status: "ok",
        session: res.session,
        recordedPrsCount: res.recordedPrs.length,
        recordedPrs: res.recordedPrs,
        updatedTrackedPrs: res.updatedTrackedPrs,
      });
    }
  );

  server.registerTool(
    "update_block_session_log",
    {
      title: "Update Block Session Log",
      description:
        "(Legacy alias for `update_complex_session_log`) Edit an existing logged Complex session in place (`shared_set_weights_kg` or explicit `exercises`), tombstoning replaced or removed session `rep_maxes` and auto-inheriting tracked PR slots.",
      inputSchema: UpdateBlockSessionLogInputSchema,
      annotations: IDEMPOTENT_WRITE_ANNOTATIONS,
    },
    async (args) => {
      const res = await domain.updateBlockSessionLog(getUserContext(), args);
      return jsonResult({
        status: "ok",
        session: res.session,
        reconciledPrsCount: res.reconciledPrs.length,
        reconciledPrs: res.reconciledPrs,
        tombstonedRepMaxIds: res.tombstonedRepMaxIds,
        updatedTrackedPrs: res.updatedTrackedPrs,
      });
    }
  );

  // ==========================================================================
  // 9. Composite Daily Routines (Complexes + Standalone Exercises)
  // ==========================================================================

  server.registerTool(
    "list_daily_routines",
    {
      title: "List Daily Routines",
      description:
        "List composite Daily Routines (`defaultFormat: 'DAILY_ROUTINE'`) made of ordered Saved Complexes and standalone Exercises, including constituent metadata, tracked PR counts, and last logged session date.",
      inputSchema: ListDailyRoutinesInputSchema,
      annotations: READ_ONLY_ANNOTATIONS,
    },
    async (args) => {
      const dailyRoutines = await domain.listDailyRoutines(getUserContext(), args);
      return jsonResult({ count: dailyRoutines.length, dailyRoutines });
    }
  );

  server.registerTool(
    "upsert_daily_routine",
    {
      title: "Upsert Daily Routine",
      description:
        "Create or update a composite Daily Routine (`defaultFormat: 'DAILY_ROUTINE'`) with ordered Saved Complexes (`item_type: 'COMPLEX'`, legacy `'BLOCK'` alias, optional `sets_count` + `exercise_targets`) and standalone Exercises (`item_type: 'EXERCISE'`, optional `note`, `default_tracked_prs`, and optional `sets_count`/`target_metric`/`target_mode`/`fixed_value`/`per_set_values`). Auto-creates missing exercises.",
      inputSchema: UpsertDailyRoutineInputSchema,
      annotations: IDEMPOTENT_WRITE_ANNOTATIONS,
    },
    async (args) => {
      const dailyRoutine = await domain.upsertDailyRoutine(getUserContext(), args);
      return jsonResult({ status: "ok", dailyRoutine });
    }
  );

  server.registerTool(
    "delete_daily_routine",
    {
      title: "Delete Daily Routine",
      description:
        "Tombstone/delete a composite Daily Routine by `routine_id` or exact `title` while preserving all historical logged workout sessions, Complexes, and PRs.",
      inputSchema: DeleteDailyRoutineInputSchema,
      annotations: DELETE_ANNOTATIONS,
    },
    async (args) => {
      const res = await domain.deleteDailyRoutine(getUserContext(), args);
      return jsonResult({
        status: res.deleted ? "deleted" : "not_found",
        deletedCount: res.deletedCount,
        deletedTitles: res.deletedTitles,
      });
    }
  );

  server.registerTool(
    "log_daily_routine_session",
    {
      title: "Log Daily Routine Session",
      description:
        "Log an ordered mixed workout of Saved Complexes (`item_type: 'COMPLEX'`, legacy `'BLOCK'`) and standalone Exercises (`item_type: 'EXERCISE'`, `STANDALONE_EXERCISE`) supporting `shared_set_weights_kg` (skip-aware via saved `exercise_targets`), weight-only, or multi-metric `metric` + `sets` (`reps`, `weight_kg`, `metric_value`, `group_index`), recording `achieved_prs` into `rep_maxes` and optionally updating the Daily Routine template (`update_routine_template: true`).",
      inputSchema: LogDailyRoutineSessionInputSchema,
      annotations: IDEMPOTENT_WRITE_ANNOTATIONS,
    },
    async (args) => {
      const res = await domain.logDailyRoutineSession(getUserContext(), args);
      return jsonResult({
        status: "ok",
        session: res.session,
        recordedPrsCount: res.recordedPrs.length,
        recordedPrs: res.recordedPrs,
        updatedRoutineTemplate: res.updatedRoutineTemplate,
      });
    }
  );

  server.registerTool(
    "get_daily_routine_progress",
    {
      title: "Get Daily Routine Progress",
      description:
        "Compute unified multi-metric constituent progress (`kg`/`reps`/`cal`/`s`/`m`, All-Time Top, Last Top, Last Average, Last Volume, Top Delta trend, Tracked PRs, chart points, and recent sessions) for every Complex and Exercise inside a Daily Routine.",
      inputSchema: GetDailyRoutineProgressInputSchema,
      annotations: READ_ONLY_ANNOTATIONS,
    },
    async (args) => {
      const progress = await domain.getDailyRoutineProgress(getUserContext(), args);
      return jsonResult(progress);
    }
  );

  return server;
}
