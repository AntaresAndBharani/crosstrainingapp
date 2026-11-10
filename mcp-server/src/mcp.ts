import { McpServer } from "@modelcontextprotocol/sdk/server/mcp.js";
import type { ToolAnnotations } from "@modelcontextprotocol/sdk/types.js";
import { z } from "zod";
import {
  BatchRecordRepMaxesInputSchema,
  BatchUpsertDailyLogsInputSchema,
  BatchUpsertExercisesInputSchema,
  BatchUpsertWeightEntriesInputSchema,
  computeWeightMomentumAnalytics,
  DeleteCycleGoalInputSchema,
  DeleteDailyLogInputSchema,
  DeleteRoutineInputSchema,
  DeleteSessionInputSchema,
  DeleteWeightEntryInputSchema,
  GetWeightMomentumAnalyticsInputSchema,
  ListCycleGoalsInputSchema,
  ListExercisesInputSchema,
  ListSessionsInputSchema,
  makeListDateRangeInputSchema,
  ManageCycleGoalsInputSchema,
  RecordRepMaxInputSchema,
  UpsertCycleGoalInputSchema,
  UpsertDailyLogInputSchema,
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
  "2. BATCH MULTI-ORDER PROMPTS IN ONE CALL: When a user message contains multiple orders (e.g. changing 2RM Snatch to 3RM Snatch AND adding several new goals, or logging multiple exercises, PRs, weigh-ins, or daily logs), ALWAYS combine them into a single batch tool call (`manage_cycle_goals`, `batch_upsert_exercises`, `batch_record_rep_maxes`, `batch_upsert_weight_entries`, `batch_upsert_daily_logs`) instead of chaining multiple single-item tool calls. In `manage_cycle_goals`, set `previousTargetReps` on an upsert item to convert an RM goal (such as 2RM -> 3RM) in one atomic step.",
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
      description: "List all saved workout routine templates and their ordered blocks.",
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
      description: "Create or update a workout routine template with ordered blocks.",
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
        "Compute multi-horizon (7D/14D/21D/28D) moving averages, 3:4:2:1 Weekly Body Weight composite, weekly momentum %, subcycle corridor status, and projected finish weight window.",
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

  return server;
}
