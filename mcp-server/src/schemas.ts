import { z } from "zod";

export const ExerciseCategoryValues = [
  "BARBELL",
  "GYMNASTICS",
  "MACHINE",
  "ACCESSORY",
  "OTHER",
] as const;

export const MetricTypeValues = [
  "WEIGHT",
  "CALORIES",
  "DISTANCE",
  "TIME",
  "REPS",
] as const;

export const BlockKindValues = [
  "WEIGHTLIFTING",
  "COMPLEX",
  "SUPERSET",
  "HYPERTROPHY",
  "ACCESSORY",
  "METABOLIC",
  "CARDIO",
  "WARMUP",
  "STRENGTH",
  "METCON",
  "OTHER",
] as const;

export const CycleTypeValues = [
  "STRENGTH_WEIGHTLIFTING",
  "FAT_LOSS_BODYBUILDING",
] as const;

export const GoalMetricValues = [
  "WEIGHT",
  "CALORIES",
  "REPS",
] as const;

export const ExerciseCategorySchema = z
  .enum(ExerciseCategoryValues)
  .describe("Movement classification: BARBELL, GYMNASTICS, MACHINE, ACCESSORY, or OTHER");

export const MetricTypeSchema = z
  .enum(MetricTypeValues)
  .describe("Primary measurement type: WEIGHT, CALORIES, DISTANCE, TIME, or REPS");

export const BlockKindSchema = z
  .enum(BlockKindValues)
  .describe("Block role within a workout or routine");

export const CycleTypeSchema = z
  .enum(CycleTypeValues)
  .describe("Training cycle orientation: STRENGTH_WEIGHTLIFTING or FAT_LOSS_BODYBUILDING");

export const GoalMetricSchema = z
  .enum(GoalMetricValues)
  .describe("Cycle goal metric: WEIGHT, CALORIES, or REPS");

export const IsoDateSchema = z
  .string()
  .regex(/^\d{4}-\d{2}-\d{2}$/, "Expected YYYY-MM-DD ISO date format")
  .describe("Date in YYYY-MM-DD format");

// ============================================================================
// Tool Input Schemas (Gemini / Claude / ChatGPT Compatible)
// ============================================================================

export const UpsertExerciseInputSchema = {
  name: z.string().min(1).max(200).describe("Canonical exercise name, e.g. 'Back Squat' or 'Snatch'"),
  category: ExerciseCategorySchema.optional().describe("Movement category (defaults to BARBELL)"),
  metricType: MetricTypeSchema.optional().describe("Primary metric type (defaults to WEIGHT)"),
  unit: z.string().max(20).optional().describe("Display unit, e.g. 'kg', 'cal', 'm', 'sec', 'reps'"),
  tracksRepMax: z.boolean().optional().describe("Whether this movement tracks 1RM/3RM/5RM personal records"),
};

export const RoutineBlockInputSchema = z.object({
  name: z.string().min(1).max(300).describe("Block title, e.g. 'Snatch Waves' or 'Conditioning AMRAP'"),
  kind: BlockKindSchema.optional().describe("Block type (defaults to WEIGHTLIFTING)"),
  format: z.string().max(200).optional().describe("Format string, e.g. 'E2MOM', 'AMRAP 12', '4 Sets'"),
  setsCount: z.number().int().min(1).max(100).optional().describe("Number of sets in this block"),
  targetRepsScheme: z.string().max(200).optional().describe("Target rep scheme, e.g. '5-5-3-3-1' or '10'"),
  exerciseNames: z.array(z.string()).optional().describe("List of exercise names used in this block"),
  notes: z.string().max(2000).optional().describe("Coaching cues, percentages, or rest instructions"),
  section: z.string().max(100).optional().describe("Optional workout section header"),
  subBlock: z.string().max(100).optional().describe("Optional sub-block label (e.g. 'A1', 'B')"),
});

export const UpsertRoutineInputSchema = {
  name: z.string().min(1).max(300).describe("Unique routine template name"),
  description: z.string().max(2000).optional().describe("Overall routine overview or stimulus notes"),
  defaultFormat: z.string().max(200).optional().describe("Default workout format, e.g. 'Strength + Metcon'"),
  mainExerciseName: z.string().max(200).optional().describe("Optional primary exercise name for this routine"),
  blocks: z.array(RoutineBlockInputSchema).describe("Ordered list of blocks in this routine"),
};

export const DeleteRoutineInputSchema = {
  name: z.string().min(1).max(300).describe("Exact name of the routine template to delete"),
};

export const SessionSetInputSchema = z.object({
  reps: z.number().int().min(0).max(10000).describe("Completed or target repetitions"),
  weight: z.number().min(0).max(2000).optional().describe("Load in kg (0 for bodyweight)"),
  metricValue: z.number().min(0).max(100000).optional().describe("Secondary metric value (calories, meters, or seconds)"),
  groupIndex: z.number().int().min(0).max(100).optional().describe("Wave or superset group index (default 0)"),
  isWarmup: z.boolean().optional().describe("True if this set is a warm-up set"),
  isFailed: z.boolean().optional().describe("True if the lift/attempt failed"),
  notes: z.string().max(500).optional().describe("Set-specific notes (e.g. 'RPE 8', 'beltless')"),
});

export const SessionBlockInputSchema = z.object({
  name: z.string().min(1).max(300).describe("Block title, e.g. 'Back Squat 5x5' or 'Fran'"),
  kind: BlockKindSchema.optional().describe("Block kind (defaults to WEIGHTLIFTING)"),
  format: z.string().max(200).optional().describe("Format, e.g. 'E3MOM', 'For Time', 'AMRAP 15'"),
  scheme: z.string().max(200).optional().describe("Rep scheme, e.g. '5-5-5-5-5' or '21-15-9'"),
  mainExerciseName: z.string().max(200).optional().describe("Primary exercise name tracked in this block"),
  exerciseNames: z.array(z.string()).optional().describe("Additional exercise names in this block"),
  description: z.string().max(2000).optional().describe("Block movement description or WOD details"),
  resultText: z.string().max(200).optional().describe("Formatted WOD score, e.g. '8:45' or '5 rounds + 12 reps'"),
  resultValue: z.number().optional().describe("Numeric result score for sorting/analytics"),
  notes: z.string().max(2000).optional().describe("Block coaching or execution notes"),
  section: z.string().max(100).optional().describe("Optional workout section"),
  subBlock: z.string().max(100).optional().describe("Optional sub-block code (e.g. 'A', 'B1')"),
  isCompleted: z.boolean().optional().describe("Whether this block has been completed (defaults to true)"),
  sets: z.array(SessionSetInputSchema).optional().describe("Ordered list of sets within this block"),
});

export const UpsertSessionInputSchema = {
  date: IsoDateSchema.describe("Workout date in YYYY-MM-DD format"),
  title: z.string().min(1).max(300).describe("Session title, e.g. 'Lower Body Strength' or 'Monday Session'"),
  notes: z.string().max(2000).optional().describe("Overall session notes, readiness, or feedback"),
  cycleId: z.number().int().min(0).optional().describe("Cycle ID (defaults to 1 for active cycle)"),
  cycleName: z.string().max(200).optional().describe("Optional training cycle name"),
  blocks: z.array(SessionBlockInputSchema).describe("Ordered blocks and sets for the workout session"),
};

export const ListSessionsInputSchema = {
  startDate: IsoDateSchema.optional().describe("Optional start date filter (YYYY-MM-DD, inclusive)"),
  endDate: IsoDateSchema.optional().describe("Optional end date filter (YYYY-MM-DD, inclusive)"),
  limit: z.number().int().min(1).max(200).optional().describe("Max sessions to return (default 30)"),
};

export const DeleteSessionInputSchema = {
  date: IsoDateSchema.describe("Workout date in YYYY-MM-DD format"),
  title: z.string().min(1).max(300).describe("Exact title of the workout session to delete"),
};

export const UpsertCycleGoalInputSchema = {
  exerciseName: z.string().min(1).max(200).describe("Target exercise name, e.g. 'Snatch', 'Back Squat', 'Air Bike'"),
  goalMetric: GoalMetricSchema.optional().describe("Goal metric: WEIGHT (default), CALORIES, or REPS"),
  targetReps: z.number().int().min(1).max(100).optional().describe("Rep max target when goalMetric is WEIGHT (e.g. 1 for 1RM, 5 for 5RM)"),
  timeWindowMinutes: z.number().int().min(1).max(60).optional().describe("Time window in minutes (1, 3, or 5) for CALORIES/REPS goals; omit for Open"),
  startWeight: z.number().min(0).max(2000).optional().describe("Starting baseline value (kg, cal, or reps)"),
  targetWeight: z.number().min(0).max(2000).describe("Target goal value (kg, cal, or reps)"),
  notes: z.string().max(1000).optional().describe("Goal notes or progression strategy"),
  cycleId: z.number().int().min(1).optional().describe("Target cycle ID (defaults to 1)"),
  cycleName: z.string().max(200).optional().describe("Optional cycle name to associate with this goal"),
};

export const DeleteCycleGoalInputSchema = {
  exerciseName: z.string().min(1).max(200).describe("Exercise name of the cycle goal to remove"),
  targetReps: z.number().int().min(0).max(100).optional().describe("Optional targetReps disambiguator"),
  cycleId: z.number().int().min(1).optional().describe("Cycle ID (defaults to 1)"),
};

export const RecordRepMaxInputSchema = {
  exerciseName: z.string().min(1).max(200).describe("Exercise name, e.g. 'Clean & Jerk'"),
  reps: z.number().int().min(1).max(100).describe("Repetition count (e.g. 1 for 1RM, 3 for 3RM, 5 for 5RM)"),
  weight: z.number().positive().max(2000).describe("Weight lifted in kg"),
  date: IsoDateSchema.optional().describe("Date achieved in YYYY-MM-DD format (defaults to today)"),
  cycleId: z.number().int().min(1).optional().describe("Optional training cycle ID"),
};

export const UpsertWeightEntryInputSchema = {
  date: IsoDateSchema.optional().describe("Date in YYYY-MM-DD format (defaults to today)"),
  weightKg: z.number().positive().max(500).describe("Body weight in kilograms"),
  notes: z.string().max(500).optional().describe("Optional weigh-in notes"),
};

export const DeleteWeightEntryInputSchema = {
  date: IsoDateSchema.describe("Date of the body weight entry to delete (YYYY-MM-DD)"),
};

export const UpsertDailyLogInputSchema = {
  date: IsoDateSchema.optional().describe("Date in YYYY-MM-DD format (defaults to today)"),
  fastCompleted: z.boolean().optional().describe("Whether scheduled intermittent fast was completed"),
  isRestDay: z.boolean().optional().describe("Whether this day is a rest/recovery day"),
  caloriesKcal: z.number().int().min(0).max(20000).optional().describe("Total daily energy intake in kcal"),
  proteinGrams: z.number().int().min(0).max(1000).optional().describe("Daily protein intake in grams"),
  carbsGrams: z.number().int().min(0).max(2000).optional().describe("Daily carbohydrate intake in grams"),
  fatGrams: z.number().int().min(0).max(1000).optional().describe("Daily fat intake in grams"),
  notes: z.string().max(1000).optional().describe("Nutrition, recovery, or sleep notes"),
};

export const ListDateRangeInputSchema = {
  startDate: IsoDateSchema.optional().describe("Start date filter (YYYY-MM-DD, inclusive)"),
  endDate: IsoDateSchema.optional().describe("End date filter (YYYY-MM-DD, inclusive)"),
};

/**
 * Formats adaptive CycleGoal metadata header matching CycleGoal.encodeMetadata() in Android.
 */
export function encodeCycleGoalMetadata(
  rawNotes: string,
  goalMetric: "WEIGHT" | "CALORIES" | "REPS" = "WEIGHT",
  timeWindowMinutes?: number
): string {
  const stripped = rawNotes
    .replace(/^\[metric=(WEIGHT|CALORIES|REPS),window=(OPEN|\d+)\]\s*/i, "")
    .trim();
  if (goalMetric === "WEIGHT") {
    return stripped;
  }
  const windowToken =
    timeWindowMinutes !== undefined && timeWindowMinutes > 0
      ? String(timeWindowMinutes)
      : "OPEN";
  const header = `[metric=${goalMetric},window=${windowToken}]`;
  return stripped.length === 0 ? header : `${header} ${stripped}`;
}
