import { McpServer } from "@modelcontextprotocol/sdk/server/mcp.js";
import { z } from "zod";
import {
  DeleteCycleGoalInputSchema,
  DeleteRoutineInputSchema,
  DeleteSessionInputSchema,
  DeleteWeightEntryInputSchema,
  GetWeightMomentumAnalyticsInputSchema,
  computeWeightMomentumAnalytics,
  ListDateRangeInputSchema,
  ListSessionsInputSchema,
  RecordRepMaxInputSchema,
  UpsertCycleGoalInputSchema,
  UpsertDailyLogInputSchema,
  UpsertExerciseInputSchema,
  UpsertRoutineInputSchema,
  UpsertSessionInputSchema,
  UpsertWeightEntryInputSchema,
} from "./schemas.js";
import type { UserContext, WorkoutDomainService } from "./store.js";

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
  const server = new McpServer({
    name: "crosstraining-workout-mcp",
    version: "1.0.0",
  });

  // ==========================================================================
  // 1. Exercises
  // ==========================================================================

  server.tool(
    "list_exercises",
    "List all exercises in the user's CrossTraining catalog",
    {},
    async () => {
      const exercises = await domain.listExercises(getUserContext());
      return jsonResult({ count: exercises.length, exercises });
    }
  );

  server.tool(
    "upsert_exercise",
    "Create or update an exercise in the user's CrossTraining catalog",
    UpsertExerciseInputSchema,
    async (args) => {
      const ex = await domain.getOrCreateExercise(getUserContext(), args);
      return jsonResult({ status: "ok", exercise: ex });
    }
  );

  // ==========================================================================
  // 2. Routines (Workout Templates)
  // ==========================================================================

  server.tool(
    "list_routines",
    "List all saved workout routine templates and their blocks",
    {},
    async () => {
      const routines = await domain.listRoutines(getUserContext());
      return jsonResult({ count: routines.length, routines });
    }
  );

  server.tool(
    "upsert_routine",
    "Create or update a workout routine template with ordered blocks",
    UpsertRoutineInputSchema,
    async (args) => {
      const routine = await domain.upsertRoutine(getUserContext(), args);
      return jsonResult({ status: "ok", routine });
    }
  );

  server.tool(
    "delete_routine",
    "Delete a workout routine template by name",
    DeleteRoutineInputSchema,
    async ({ name }) => {
      const deleted = await domain.deleteRoutine(getUserContext(), name);
      return jsonResult({ status: deleted ? "deleted" : "not_found", name });
    }
  );

  // ==========================================================================
  // 3. Workout Sessions
  // ==========================================================================

  server.tool(
    "list_sessions",
    "List logged or scheduled workout sessions with blocks and sets",
    ListSessionsInputSchema,
    async (args) => {
      const sessions = await domain.listSessions(getUserContext(), args);
      return jsonResult({ count: sessions.length, sessions });
    }
  );

  server.tool(
    "upsert_session",
    "Add or update a workout session for a specific date with blocks and sets",
    UpsertSessionInputSchema,
    async (args) => {
      const session = await domain.upsertSession(getUserContext(), args);
      return jsonResult({ status: "ok", session });
    }
  );

  server.tool(
    "delete_session",
    "Delete a workout session by date and title",
    DeleteSessionInputSchema,
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
  // 4. Cycle Goals
  // ==========================================================================

  server.tool(
    "list_cycle_goals",
    "List training cycle goals (weight RM targets, calorie window goals, or rep goals)",
    {},
    async () => {
      const goals = await domain.listCycleGoals(getUserContext());
      return jsonResult({ count: goals.length, cycleGoals: goals });
    }
  );

  server.tool(
    "upsert_cycle_goal",
    "Create or update a training cycle goal for an exercise",
    UpsertCycleGoalInputSchema,
    async (args) => {
      const goal = await domain.upsertCycleGoal(getUserContext(), args);
      return jsonResult({ status: "ok", cycleGoal: goal });
    }
  );

  server.tool(
    "delete_cycle_goal",
    "Delete a training cycle goal for an exercise",
    DeleteCycleGoalInputSchema,
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

  server.tool(
    "list_rep_maxes",
    "List personal record (PR / Rep Max) history, optionally filtered by exerciseName",
    {
      exerciseName: z
        .string()
        .max(200)
        .optional()
        .describe("Optional exercise name to filter PR records"),
    },
    async ({ exerciseName }) => {
      const repMaxes = await domain.listRepMaxes(getUserContext(), exerciseName);
      return jsonResult({ count: repMaxes.length, repMaxes });
    }
  );

  server.tool(
    "record_rep_max",
    "Record a new personal record (Rep Max, e.g. 1RM, 3RM, 5RM) for an exercise",
    RecordRepMaxInputSchema,
    async (args) => {
      const repMax = await domain.recordRepMax(getUserContext(), args);
      return jsonResult({ status: "ok", repMax });
    }
  );

  // ==========================================================================
  // 6. Body Weight Tracking
  // ==========================================================================

  server.tool(
    "get_weight_momentum_analytics",
    "Compute multi-horizon (7D/14D/21D/28D) moving averages, 3:4:2:1 Weekly Body Weight composite, weekly momentum %, subcycle corridor status, and projected finish weight window",
    GetWeightMomentumAnalyticsInputSchema,
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
  // ==========================================================================

  server.tool(
    "list_weight_entries",
    "List body weight entries (kg) within an optional date range",
    ListDateRangeInputSchema,
    async (args) => {
      const entries = await domain.listWeightEntries(getUserContext(), args);
      return jsonResult({ count: entries.length, weightEntries: entries });
    }
  );

  server.tool(
    "upsert_weight_entry",
    "Log or update a body weight entry (in kg) for a date",
    UpsertWeightEntryInputSchema,
    async (args) => {
      const entry = await domain.upsertWeightEntry(getUserContext(), args);
      return jsonResult({ status: "ok", weightEntry: entry });
    }
  );

  server.tool(
    "delete_weight_entry",
    "Soft-delete a body weight entry for a date",
    DeleteWeightEntryInputSchema,
    async ({ date }) => {
      const deleted = await domain.deleteWeightEntry(getUserContext(), date);
      return jsonResult({ status: deleted ? "deleted" : "not_found", date });
    }
  );

  // ==========================================================================
  // 7. Daily Logs (Fasting, Rest Days, Calories & Macros)
  // ==========================================================================

  server.tool(
    "list_daily_logs",
    "List daily nutrition, fasting, and rest-day logs within an optional date range",
    ListDateRangeInputSchema,
    async (args) => {
      const logs = await domain.listDailyLogs(getUserContext(), args);
      return jsonResult({ count: logs.length, dailyLogs: logs });
    }
  );

  server.tool(
    "upsert_daily_log",
    "Log or update daily nutrition macros, calories, intermittent fasting completion, or rest day status",
    UpsertDailyLogInputSchema,
    async (args) => {
      const log = await domain.upsertDailyLog(getUserContext(), args);
      return jsonResult({ status: "ok", dailyLog: log });
    }
  );

  return server;
}
