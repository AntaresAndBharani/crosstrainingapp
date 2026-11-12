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
  "GYMNASTICS",
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

export const RoutineTargetMetricValues = [
  "REPS",
  "CALORIES",
  "TIME_SECONDS",
  "METERS",
] as const;

export const RoutineTargetModeValues = [
  "FIXED",
  "PER_SET",
] as const;

export type RoutineTargetMetric = (typeof RoutineTargetMetricValues)[number];
export type RoutineTargetMode = (typeof RoutineTargetModeValues)[number];

export interface RoutineExerciseTargetSlot {
  metric: RoutineTargetMetric;
  mode: RoutineTargetMode;
  fixedValue: number;
  perSetValues: number[];
}

// Always return a fresh Zod schema instance from helper factories so `zod-to-json-schema`
// never deduplicates shared references into `"$ref": "#/properties/..."` (which Gemini rejects).
const exerciseCategorySchema = () =>
  z
    .enum(ExerciseCategoryValues)
    .describe("Movement classification: BARBELL, GYMNASTICS, MACHINE, ACCESSORY, or OTHER");

const metricTypeSchema = () =>
  z
    .enum(MetricTypeValues)
    .describe("Primary measurement type: WEIGHT, CALORIES, DISTANCE, TIME, or REPS");

const blockKindSchema = () =>
  z
    .enum(BlockKindValues)
    .describe("Block role within a workout or routine");

const goalMetricSchema = () =>
  z
    .enum(GoalMetricValues)
    .describe("Cycle goal metric: WEIGHT, CALORIES, or REPS");

const routineTargetMetricSchema = () =>
  z
    .enum(RoutineTargetMetricValues)
    .describe("Per-exercise target unit: REPS, CALORIES, TIME_SECONDS, or METERS");

const routineTargetModeSchema = () =>
  z
    .enum(RoutineTargetModeValues)
    .describe("FIXED applies fixedValue to every set; PER_SET uses perSetValues (0 = Skip in that set)");

/** True only for a real calendar date in strict `YYYY-MM-DD` form (rejects `2026-02-30`, `2026-1-5`). */
export function isValidIsoDate(value: string): boolean {
  if (!/^\d{4}-\d{2}-\d{2}$/.test(value)) return false;
  const ms = Date.parse(`${value}T00:00:00Z`);
  return !Number.isNaN(ms) && new Date(ms).toISOString().slice(0, 10) === value;
}

// Runtime validation uses `.refine()` instead of `.regex()`: refinements are enforced by Zod on
// every tools/call but emit no JSON Schema keyword, so the advertised tool schema stays a plain
// `{"type":"string"}` that every connector (Gemini, Claude, ChatGPT) accepts.
const isoDateSchema = () =>
  z
    .string()
    .refine(isValidIsoDate, "Expected a valid YYYY-MM-DD ISO date")
    .describe("Date in YYYY-MM-DD format");

export const ExerciseCategorySchema = exerciseCategorySchema();
export const MetricTypeSchema = metricTypeSchema();
export const BlockKindSchema = blockKindSchema();
export const GoalMetricSchema = goalMetricSchema();
export const IsoDateSchema = isoDateSchema();

// ============================================================================
// Tool Input Schemas (Gemini / Claude / ChatGPT Compatible — Zero $ref, Zero exclusiveMinimum)
// ============================================================================

export const ListExercisesInputSchema = {
  category: exerciseCategorySchema()
    .optional()
    .describe("Optional movement category filter (BARBELL, GYMNASTICS, MACHINE, ACCESSORY, OTHER)"),
  search: z
    .string()
    .max(200)
    .optional()
    .describe("Optional case-insensitive substring filter on exercise name"),
};

const makeExerciseUpsertItemSchema = () =>
  z.object({
    name: z.string().min(1).max(200).describe("Canonical exercise name, e.g. 'Back Squat' or 'Snatch'"),
    category: exerciseCategorySchema().optional().describe("Movement category (defaults to BARBELL)"),
    metricType: metricTypeSchema().optional().describe("Primary metric type (defaults to WEIGHT)"),
    unit: z.string().max(20).optional().describe("Display unit, e.g. 'kg', 'cal', 'm', 'sec', 'reps'"),
    tracksRepMax: z.boolean().optional().describe("Whether this movement tracks 1RM/3RM/5RM personal records"),
  });

export const UpsertExerciseInputSchema = {
  name: z.string().min(1).max(200).describe("Canonical exercise name, e.g. 'Back Squat' or 'Snatch'"),
  category: exerciseCategorySchema().optional().describe("Movement category (defaults to BARBELL)"),
  metricType: metricTypeSchema().optional().describe("Primary metric type (defaults to WEIGHT)"),
  unit: z.string().max(20).optional().describe("Display unit, e.g. 'kg', 'cal', 'm', 'sec', 'reps'"),
  tracksRepMax: z.boolean().optional().describe("Whether this movement tracks 1RM/3RM/5RM personal records"),
};

export const BatchUpsertExercisesInputSchema = {
  exercises: z
    .array(makeExerciseUpsertItemSchema())
    .min(1)
    .max(100)
    .describe("List of exercises to create or update in one atomic batch"),
};

const makeRoutineBlockExerciseInputSchema = () =>
  z.object({
    name: z
      .string()
      .min(1)
      .max(200)
      .describe(
        "Exercise name (may repeat across positions for sequence blocks like Shuttle Run -> Burpees -> Shuttle Run)"
      ),
    category: exerciseCategorySchema()
      .optional()
      .describe("Optional movement category when auto-creating a new exercise"),
    metric: routineTargetMetricSchema()
      .optional()
      .describe(
        "Target metric: REPS, CALORIES, TIME_SECONDS, or METERS (auto-inferred from exercise metricType when omitted)"
      ),
    mode: routineTargetModeSchema()
      .optional()
      .describe("FIXED (same target every set) or PER_SET (per-set array where 0 = Skip set)"),
    fixedValue: z
      .number()
      .int()
      .min(0)
      .max(100000)
      .optional()
      .describe("Fixed integer target per set when mode is FIXED (must be >= 1)"),
    perSetValues: z
      .array(z.number().int().min(0).max(100000))
      .max(100)
      .optional()
      .describe(
        "Per-set integer targets aligned to block setsCount (0 = Skip in that set; at least one set must be >= 1)"
      ),
  });

const makeRoutineBlockInputSchema = () =>
  z.object({
    name: z.string().min(1).max(300).describe("Block title, e.g. 'Snatch Waves' or 'Conditioning AMRAP'"),
    kind: blockKindSchema().optional().describe("Block type (defaults to WEIGHTLIFTING)"),
    format: z.string().max(200).optional().describe("Format string, e.g. 'E2MOM', 'AMRAP', 'Sets x Reps'"),
    setsCount: z.number().int().min(1).max(100).optional().describe("Number of sets/rounds in this block (>= 1)"),
    targetRepsScheme: z
      .string()
      .max(200)
      .optional()
      .describe("Legacy/summary target scheme, e.g. '5-5-3-3-1' or '15 cal + 12-10-8-0 reps'"),
    exerciseNames: z
      .array(z.string())
      .optional()
      .describe("Legacy list of exercise names (prefer structured `exercises` for mixed metrics or per-set/0-skip targets)"),
    exercises: z
      .array(makeRoutineBlockExerciseInputSchema())
      .max(50)
      .optional()
      .describe(
        "Structured per-exercise targets supporting REPS, CALORIES, TIME_SECONDS, METERS, FIXED or PER_SET mode, 0 (Skip) sets, and repeated sequence exercises"
      ),
    notes: z.string().max(2000).optional().describe("Coaching cues, percentages, or rest instructions"),
    section: z.string().max(100).optional().describe("Optional workout section header"),
    subBlock: z.string().max(100).optional().describe("Optional sub-block label (e.g. 'A1', 'B')"),
  });

export const RoutineBlockInputSchema = makeRoutineBlockInputSchema();

export const UpsertRoutineInputSchema = {
  name: z.string().min(1).max(300).describe("Unique routine template name"),
  description: z.string().max(2000).optional().describe("Overall routine overview or stimulus notes"),
  defaultFormat: z.string().max(200).optional().describe("Default workout format, e.g. 'Strength + Metcon'"),
  mainExerciseName: z.string().max(200).optional().describe("Optional primary exercise name for this routine"),
  blocks: z.array(makeRoutineBlockInputSchema()).describe("Ordered list of blocks in this routine"),
};

export const DeleteRoutineInputSchema = {
  name: z.string().min(1).max(300).describe("Exact name of the routine template to delete"),
};

const makeSessionSetInputSchema = () =>
  z.object({
    reps: z.number().int().min(0).max(10000).describe("Completed or target repetitions"),
    weight: z.number().min(0).max(2000).optional().describe("Load in kg (0 for bodyweight)"),
    metricValue: z.number().min(0).max(100000).optional().describe("Secondary metric value (calories, meters, or seconds)"),
    groupIndex: z.number().int().min(0).max(100).optional().describe("Wave or superset group index (default 0)"),
    isWarmup: z.boolean().optional().describe("True if this set is a warm-up set"),
    isFailed: z.boolean().optional().describe("True if the lift/attempt failed"),
    notes: z.string().max(500).optional().describe("Set-specific notes (e.g. 'RPE 8', 'beltless')"),
  });

const makeSessionBlockInputSchema = () =>
  z.object({
    name: z.string().min(1).max(300).describe("Block title, e.g. 'Back Squat 5x5' or 'Fran'"),
    kind: blockKindSchema().optional().describe("Block kind (defaults to WEIGHTLIFTING)"),
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
    sets: z.array(makeSessionSetInputSchema()).optional().describe("Ordered list of sets within this block"),
  });

export const SessionSetInputSchema = makeSessionSetInputSchema();
export const SessionBlockInputSchema = makeSessionBlockInputSchema();

export const UpsertSessionInputSchema = {
  date: isoDateSchema().describe("Workout date in YYYY-MM-DD format"),
  title: z.string().min(1).max(300).describe("Session title, e.g. 'Lower Body Strength' or 'Monday Session'"),
  notes: z.string().max(2000).optional().describe("Overall session notes, readiness, or feedback"),
  cycleId: z
    .number()
    .int()
    .min(1)
    .optional()
    .describe("Optional training cycle ID (omit to automatically use the active cycle)"),
  cycleName: z.string().max(200).optional().describe("Optional training cycle name"),
  blocks: z.array(makeSessionBlockInputSchema()).describe("Ordered blocks and sets for the workout session"),
};

export const ListSessionsInputSchema = {
  startDate: isoDateSchema().optional().describe("Optional start date filter (YYYY-MM-DD, inclusive)"),
  endDate: isoDateSchema().optional().describe("Optional end date filter (YYYY-MM-DD, inclusive)"),
  cycleId: z.number().int().min(1).optional().describe("Optional cycle ID filter"),
  activeCycleOnly: z
    .boolean()
    .optional()
    .describe("When true, returns only sessions belonging to the user's active cycle"),
  limit: z.number().int().min(1).max(200).optional().describe("Max sessions to return (default 30)"),
};

export const DeleteSessionInputSchema = {
  date: isoDateSchema().describe("Workout date in YYYY-MM-DD format"),
  title: z.string().min(1).max(300).describe("Exact title of the workout session to delete"),
};

export const ListCycleGoalsInputSchema = {
  cycleId: z.number().int().min(1).optional().describe("Optional cycle ID filter"),
  activeCycleOnly: z
    .boolean()
    .optional()
    .describe("When true, returns only goals belonging to the user's active cycle"),
};

export const UpsertCycleGoalInputSchema = {
  exerciseName: z.string().min(1).max(200).describe("Target exercise name, e.g. 'Snatch', 'Back Squat', 'Air Bike'"),
  goalMetric: goalMetricSchema().optional().describe("Goal metric: WEIGHT (default), CALORIES, or REPS"),
  targetReps: z
    .number()
    .int()
    .min(1)
    .max(100)
    .optional()
    .describe("Rep max target when goalMetric is WEIGHT (e.g. 1 for 1RM, 3 for 3RM, 6 for 6RM)"),
  previousTargetReps: z
    .number()
    .int()
    .min(0)
    .max(100)
    .optional()
    .describe(
      "Optional previous targetReps to atomically replace when converting an exercise goal's RM scheme (e.g. previousTargetReps=2 and targetReps=3 converts 2RM Snatch into 3RM Snatch)"
    ),
  timeWindowMinutes: z
    .number()
    .int()
    .min(1)
    .max(60)
    .optional()
    .describe("Time window in minutes (1, 3, or 5) for CALORIES/REPS goals; omit for Open"),
  startWeight: z.number().min(0).max(2000).optional().describe("Starting baseline value (kg, cal, or reps)"),
  targetWeight: z.number().min(0).max(2000).describe("Target goal value (kg, cal, or reps)"),
  notes: z.string().max(1000).optional().describe("Goal notes or progression strategy"),
  cycleId: z
    .number()
    .int()
    .min(1)
    .optional()
    .describe("Optional target cycle ID (omit to automatically target the active cycle)"),
  cycleName: z.string().max(200).optional().describe("Optional cycle name to associate with this goal"),
};

export const DeleteCycleGoalInputSchema = {
  exerciseName: z.string().min(1).max(200).describe("Exercise name of the cycle goal to remove"),
  targetReps: z.number().int().min(0).max(100).optional().describe("Optional targetReps disambiguator (e.g. 2 for 2RM)"),
  cycleId: z
    .number()
    .int()
    .min(1)
    .optional()
    .describe("Optional cycle ID (omit to delete from the active cycle)"),
};

const makeCycleGoalUpsertItemSchema = () =>
  z.object({
    exerciseName: z.string().min(1).max(200).describe("Target exercise name, e.g. 'Snatch', 'Incline Bench Press'"),
    goalMetric: goalMetricSchema().optional().describe("Goal metric: WEIGHT (default), CALORIES, or REPS"),
    targetReps: z
      .number()
      .int()
      .min(1)
      .max(100)
      .optional()
      .describe("Rep max target when goalMetric is WEIGHT (e.g. 1 for 1RM, 3 for 3RM, 6 for 6RM)"),
    previousTargetReps: z
      .number()
      .int()
      .min(0)
      .max(100)
      .optional()
      .describe(
        "Optional previous targetReps to atomically replace when converting an RM goal (e.g. previousTargetReps=2, targetReps=3 converts 2RM -> 3RM in one step)"
      ),
    timeWindowMinutes: z
      .number()
      .int()
      .min(1)
      .max(60)
      .optional()
      .describe("Time window in minutes for CALORIES/REPS goals; omit for Open"),
    startWeight: z.number().min(0).max(2000).optional().describe("Starting baseline value (kg, cal, or reps)"),
    targetWeight: z.number().min(0).max(2000).describe("Target goal value (kg, cal, or reps)"),
    notes: z.string().max(1000).optional().describe("Optional goal notes or progression strategy"),
  });

const makeCycleGoalDeleteItemSchema = () =>
  z.object({
    exerciseName: z.string().min(1).max(200).describe("Exercise name of the goal to delete"),
    targetReps: z
      .number()
      .int()
      .min(0)
      .max(100)
      .optional()
      .describe("Optional targetReps disambiguator (omit to delete all goals for that exercise in the target cycle)"),
  });

export const ManageCycleGoalsInputSchema = {
  cycleId: z
    .number()
    .int()
    .min(1)
    .optional()
    .describe("Optional target cycle ID (omit to automatically target the user's active cycle)"),
  cycleName: z.string().max(200).optional().describe("Optional target cycle name"),
  upserts: z
    .array(makeCycleGoalUpsertItemSchema())
    .max(100)
    .optional()
    .describe("Goals to create, update, or RM-convert in one atomic call"),
  deletes: z
    .array(makeCycleGoalDeleteItemSchema())
    .max(100)
    .optional()
    .describe("Goals to remove in the same atomic call"),
};

export const RecordRepMaxInputSchema = {
  exerciseName: z.string().min(1).max(200).describe("Exercise name, e.g. 'Clean & Jerk'"),
  reps: z.number().int().min(1).max(100).describe("Repetition count (e.g. 1 for 1RM, 3 for 3RM, 5 for 5RM)"),
  weight: z.number().min(0.1).max(2000).describe("Weight lifted in kg"),
  date: isoDateSchema().optional().describe("Date achieved in YYYY-MM-DD format (defaults to today)"),
  cycleId: z
    .number()
    .int()
    .min(1)
    .optional()
    .describe("Optional training cycle ID (omit to associate with the active cycle)"),
};

const makeRepMaxItemSchema = () =>
  z.object({
    exerciseName: z.string().min(1).max(200).describe("Exercise name, e.g. 'Clean & Jerk'"),
    reps: z.number().int().min(1).max(100).describe("Repetition count (e.g. 1 for 1RM, 3 for 3RM, 5 for 5RM)"),
    weight: z.number().min(0.1).max(2000).describe("Weight lifted in kg"),
    date: isoDateSchema().optional().describe("Date achieved in YYYY-MM-DD format (defaults to today)"),
    cycleId: z
      .number()
      .int()
      .min(1)
      .optional()
      .describe("Optional training cycle ID (omit to associate with the active cycle)"),
  });

export const BatchRecordRepMaxesInputSchema = {
  repMaxes: z
    .array(makeRepMaxItemSchema())
    .min(1)
    .max(100)
    .describe("Personal records (Rep Maxes) to record in one atomic batch"),
};

export const UpsertWeightEntryInputSchema = {
  date: isoDateSchema().optional().describe("Date in YYYY-MM-DD format (defaults to today)"),
  weightKg: z.number().min(0.1).max(500).describe("Body weight in kilograms"),
  notes: z.string().max(500).optional().describe("Optional weigh-in notes"),
};

const makeWeightEntryItemSchema = () =>
  z.object({
    date: isoDateSchema().optional().describe("Date in YYYY-MM-DD format (defaults to today)"),
    weightKg: z.number().min(0.1).max(500).describe("Body weight in kilograms"),
    notes: z.string().max(500).optional().describe("Optional weigh-in notes"),
  });

export const BatchUpsertWeightEntriesInputSchema = {
  entries: z
    .array(makeWeightEntryItemSchema())
    .min(1)
    .max(100)
    .describe("Body weight entries to log or update in one atomic batch"),
};

export const DeleteWeightEntryInputSchema = {
  date: isoDateSchema().describe("Date of the body weight entry to delete (YYYY-MM-DD)"),
};

export const UpsertDailyLogInputSchema = {
  date: isoDateSchema().optional().describe("Date in YYYY-MM-DD format (defaults to today)"),
  fastCompleted: z.boolean().optional().describe("Whether scheduled intermittent fast was completed"),
  isRestDay: z.boolean().optional().describe("Whether this day is a rest/recovery day"),
  caloriesKcal: z.number().int().min(0).max(20000).optional().describe("Total daily energy intake in kcal"),
  proteinGrams: z.number().int().min(0).max(1000).optional().describe("Daily protein intake in grams"),
  carbsGrams: z.number().int().min(0).max(2000).optional().describe("Daily carbohydrate intake in grams"),
  fatGrams: z.number().int().min(0).max(1000).optional().describe("Daily fat intake in grams"),
  notes: z.string().max(1000).optional().describe("Nutrition, recovery, or sleep notes"),
};

const makeDailyLogItemSchema = () =>
  z.object({
    date: isoDateSchema().optional().describe("Date in YYYY-MM-DD format (defaults to today)"),
    fastCompleted: z.boolean().optional().describe("Whether scheduled intermittent fast was completed"),
    isRestDay: z.boolean().optional().describe("Whether this day is a rest/recovery day"),
    caloriesKcal: z.number().int().min(0).max(20000).optional().describe("Total daily energy intake in kcal"),
    proteinGrams: z.number().int().min(0).max(1000).optional().describe("Daily protein intake in grams"),
    carbsGrams: z.number().int().min(0).max(2000).optional().describe("Daily carbohydrate intake in grams"),
    fatGrams: z.number().int().min(0).max(1000).optional().describe("Daily fat intake in grams"),
    notes: z.string().max(1000).optional().describe("Nutrition, recovery, or sleep notes"),
  });

export const BatchUpsertDailyLogsInputSchema = {
  logs: z
    .array(makeDailyLogItemSchema())
    .min(1)
    .max(100)
    .describe("Daily nutrition/fasting/rest logs to create or update in one atomic batch"),
};

export const DeleteDailyLogInputSchema = {
  date: isoDateSchema().describe("Date of the daily log entry to delete (YYYY-MM-DD)"),
};

export const WeightSubcycleTypeValues = [
  "FAT_LOSS",
  "RECOMPOSITION",
  "MUSCLE_GAIN",
  "WEIGHT_LOSS",
  "WEIGHT_GAIN",
] as const;

export type WeightSubcycleType = (typeof WeightSubcycleTypeValues)[number];
export type CanonicalWeightSubcycleType = "FAT_LOSS" | "RECOMPOSITION" | "MUSCLE_GAIN";

export function normalizeWeightSubcycleType(
  subcycleType?: WeightSubcycleType
): CanonicalWeightSubcycleType {
  switch (subcycleType) {
    case "FAT_LOSS":
    case "WEIGHT_LOSS":
      return "FAT_LOSS";
    case "MUSCLE_GAIN":
    case "WEIGHT_GAIN":
      return "MUSCLE_GAIN";
    case "RECOMPOSITION":
    default:
      return "RECOMPOSITION";
  }
}

const weightSubcycleTypeSchema = () =>
  z
    .enum(WeightSubcycleTypeValues)
    .describe(
      "Active subcycle regime: FAT_LOSS (-1.00% to -0.25%/wk), RECOMPOSITION (-0.25% to +0.25%/wk), or MUSCLE_GAIN (+0.25% to +0.75%/wk). Legacy aliases WEIGHT_LOSS and WEIGHT_GAIN are also accepted."
    );

export const GetWeightMomentumAnalyticsInputSchema = {
  subcycleType: weightSubcycleTypeSchema().optional().describe("Subcycle regime (defaults to RECOMPOSITION)"),
  cycleName: z.string().max(200).optional().describe("Optional cycle name (defaults to 'Active Cycle')"),
  cycleStartDate: isoDateSchema().optional().describe("Optional cycle start date in YYYY-MM-DD"),
  cycleFinishDate: isoDateSchema().optional().describe("Optional cycle finish date in YYYY-MM-DD"),
  asOfDate: isoDateSchema().optional().describe("Optional evaluation anchor date in YYYY-MM-DD (weigh-ins after it are ignored; defaults to the latest weigh-in date)"),
  includeDailyTimeline: z.boolean().optional().describe("Whether to include the interpolated daily REAL/THEORETICAL timeline in the response"),
};

export interface DailyInterpolatedWeightPoint {
  date: string;
  weightKg: number;
  source: "REAL" | "THEORETICAL";
  dayIndexFromFirstReal: number;
}

const SUBCYCLE_CORRIDORS: Record<
  CanonicalWeightSubcycleType,
  { minPct: number; maxPct: number; label: string }
> = {
  FAT_LOSS: { minPct: -1.0, maxPct: -0.25, label: "Fat Loss" },
  RECOMPOSITION: { minPct: -0.25, maxPct: 0.25, label: "Recomposition" },
  MUSCLE_GAIN: { minPct: 0.25, maxPct: 0.75, label: "Muscle Gain" },
};

function addDaysIso(isoDate: string, days: number): string {
  const ms = Date.parse(`${isoDate}T00:00:00Z`) + days * 86_400_000;
  return new Date(ms).toISOString().slice(0, 10);
}

function diffDaysIso(fromIso: string, toIso: string): number {
  const fromMs = Date.parse(`${fromIso}T00:00:00Z`);
  const toMs = Date.parse(`${toIso}T00:00:00Z`);
  return Math.round((toMs - fromMs) / 86_400_000);
}

function roundTo(val: number, decimals: number): number {
  const factor = Math.pow(10, decimals);
  return Math.round(val * factor) / factor;
}

function classifyZone(momentumPct: number): string {
  if (momentumPct < -1.0) return "POSSIBLE_MUSCLE_LOSS";
  if (momentumPct < -0.25) return "FAT_LOSS";
  if (momentumPct <= 0.25) return "RECOMPOSITION";
  if (momentumPct <= 0.75) return "MUSCLE_GAIN";
  return "SUBOPTIMAL_MUSCLE_GAIN";
}

function evaluateMomentumDiagnostic(
  momentumPct: number | null,
  subcycleType: CanonicalWeightSubcycleType,
  calibrationDay: number = 1
): {
  status: string;
  severity: "OPTIMAL_GREEN" | "WARNING_AMBER" | "CRITICAL_RED" | "NEUTRAL";
  badge: string;
  message: string | null;
} {
  if (momentumPct === null) {
    const clampedDay = Math.min(Math.max(calibrationDay, 1), 8);
    return {
      status: "CALIBRATING",
      severity: "NEUTRAL",
      badge: `Calibrating (Day ${clampedDay}/8)`,
      message: "Collecting initial 8-day baseline to calculate weekly momentum.",
    };
  }
  if (momentumPct < -1.0) {
    return {
      status: "POSSIBLE_MUSCLE_LOSS",
      severity: "CRITICAL_RED",
      badge: "Possible muscle loss",
      message: "Losing faster than -1.00%/wk. High risk of lean muscle loss.",
    };
  }
  if (momentumPct > 0.75) {
    return {
      status: "SUBOPTIMAL_MUSCLE_GAIN",
      severity: "CRITICAL_RED",
      badge: "Suboptimal muscle gain",
      message: "Gaining faster than +0.75%/wk. Surplus exceeds optimal muscle synthesis rate.",
    };
  }
  switch (subcycleType) {
    case "FAT_LOSS":
      if (momentumPct <= -0.25) {
        return {
          status: "ON_TARGET",
          severity: "OPTIMAL_GREEN",
          badge: "Optimal",
          message: null,
        };
      }
      if (momentumPct < 0.0) {
        return {
          status: "SLOW_FAT_LOSS_PACE",
          severity: "WARNING_AMBER",
          badge: "Slow fat loss pace",
          message: "Deficit is below -0.25%/wk. Increase deficit slightly to hit target range.",
        };
      }
      return {
        status: "OUT_OF_FAT_LOSS_RANGE",
        severity: "CRITICAL_RED",
        badge: "Out of fat loss range",
        message: "Weight is stable or increasing. You are outside a caloric deficit.",
      };
    case "RECOMPOSITION":
      if (momentumPct < -0.25) {
        return {
          status: "WARNING_FAT_LOSS_PHASE",
          severity: "WARNING_AMBER",
          badge: "Warning: Fat loss phase",
          message: "Pace is below -0.25%/wk. Warning: You are currently in a fat loss phase.",
        };
      }
      if (momentumPct <= 0.25) {
        return {
          status: "ON_TARGET",
          severity: "OPTIMAL_GREEN",
          badge: "Optimal",
          message: null,
        };
      }
      return {
        status: "WARNING_MUSCLE_GAIN_PHASE",
        severity: "WARNING_AMBER",
        badge: "Warning: Muscle gain phase",
        message: "Pace exceeds +0.25%/wk. Warning: You are currently in a muscle gain phase.",
      };
    case "MUSCLE_GAIN":
      if (momentumPct <= 0.0) {
        return {
          status: "OUT_OF_MUSCLE_GAIN_RANGE",
          severity: "CRITICAL_RED",
          badge: "Out of muscle gain range",
          message: "Weight is stable or dropping. Caloric surplus required for muscle gain.",
        };
      }
      if (momentumPct < 0.25) {
        return {
          status: "SLOW_MUSCLE_GAIN_PACE",
          severity: "WARNING_AMBER",
          badge: "Slow muscle gain pace",
          message: "Surplus is below +0.25%/wk. Increase intake slightly to optimize growth.",
        };
      }
      return {
        status: "ON_TARGET",
        severity: "OPTIMAL_GREEN",
        badge: "Optimal",
        message: null,
      };
  }
}

function classifyStatus(
  momentumPct: number | null,
  subcycleType: CanonicalWeightSubcycleType,
  calibrationDay: number = 1
): string {
  return evaluateMomentumDiagnostic(momentumPct, subcycleType, calibrationDay).status;
}

/**
 * Pure deterministic multi-horizon (7D/14D/21D/28D) and 3:4:2:1 Weekly Body Weight momentum
 * analytics engine matching Android FatLossAnalytics.computeCycleMomentumState().
 */
export function computeWeightMomentumAnalytics(
  entries: Array<{ date?: string | number; isoDate?: string; weightKg: number; isDeleted?: boolean }>,
  options: {
    subcycleType?: WeightSubcycleType;
    cycleName?: string;
    cycleStartDate?: string;
    cycleFinishDate?: string;
    asOfDate?: string;
    includeDailyTimeline?: boolean;
  } = {}
) {
  const subcycleType: CanonicalWeightSubcycleType = normalizeWeightSubcycleType(options.subcycleType);
  const cycleName = options.cycleName ?? "Active Cycle";
  const corridor = SUBCYCLE_CORRIDORS[subcycleType];

  const validMap = new Map<string, number>();
  for (const e of entries) {
    const resolvedDate = typeof e.isoDate === "string" ? e.isoDate : String(e.date ?? "");
    // Parity with Android interpolateDailyWeightTimeline(referenceDate): weigh-ins after asOfDate are excluded.
    const isAfterAsOf = options.asOfDate !== undefined && resolvedDate > options.asOfDate;
    if (!e.isDeleted && !isAfterAsOf && e.weightKg > 0 && isValidIsoDate(resolvedDate)) {
      validMap.set(resolvedDate, e.weightKg);
    }
  }
  const realPoints = Array.from(validMap.entries())
    .sort((a, b) => a[0].localeCompare(b[0]))
    .map(([date, weightKg]) => ({ date, weightKg }));

  const firstRealDate = realPoints[0]?.date ?? options.asOfDate ?? new Date().toISOString().slice(0, 10);
  const lastRealDate = realPoints[realPoints.length - 1]?.date ?? firstRealDate;
  const effectiveAsOf = options.asOfDate ?? lastRealDate;

  const dailyPoints: DailyInterpolatedWeightPoint[] = [];
  if (realPoints.length > 0) {
    let dayIndex = 1;
    for (let i = 0; i < realPoints.length; i++) {
      const a = realPoints[i];
      dailyPoints.push({
        date: a.date,
        weightKg: a.weightKg,
        source: "REAL",
        dayIndexFromFirstReal: dayIndex++,
      });
      if (i < realPoints.length - 1) {
        const b = realPoints[i + 1];
        const deltaT = diffDaysIso(a.date, b.date);
        for (let k = 1; k < deltaT; k++) {
          const interpWeight = a.weightKg + (k * (b.weightKg - a.weightKg)) / deltaT;
          dailyPoints.push({
            date: addDaysIso(a.date, k),
            weightKg: interpWeight,
            source: "THEORETICAL",
            dayIndexFromFirstReal: dayIndex++,
          });
        }
      }
    }
    const lastReal = realPoints[realPoints.length - 1];
    const trailingDays = diffDaysIso(lastReal.date, effectiveAsOf);
    for (let k = 1; k <= trailingDays; k++) {
      dailyPoints.push({
        date: addDaysIso(lastReal.date, k),
        weightKg: lastReal.weightKg,
        source: "THEORETICAL",
        dayIndexFromFirstReal: dayIndex++,
      });
    }
  }

  const snapshots = dailyPoints.map((pt, idx) => {
    const windowAvg = (n: number) => {
      const count = Math.min(idx + 1, n);
      let sum = 0;
      for (let j = idx - count + 1; j <= idx; j++) {
        sum += dailyPoints[j].weightKg;
      }
      return sum / count;
    };
    const avg7d = windowAvg(7);
    const avg14d = windowAvg(14);
    const avg21d = windowAvg(21);
    const avg28d = windowAvg(28);
    const weeklyComposite = (3 * avg7d + 4 * avg14d + 2 * avg21d + 1 * avg28d) / 10;
    return {
      ...pt,
      avg7d,
      avg14d,
      avg21d,
      avg28d,
      weeklyComposite,
    };
  });

  const totalTimelineDays = snapshots.length;
  const calibrationDay = Math.min(totalTimelineDays, 8);
  const calibrationComplete = totalTimelineDays >= 8;

  const startDate = options.cycleStartDate ?? firstRealDate;
  // Parity with Android: open-ended cycles default to cycle start + 8 weeks.
  const finishDate = options.cycleFinishDate ?? addDaysIso(options.cycleStartDate ?? effectiveAsOf, 56);
  const daysRemaining = Math.max(0, diffDaysIso(effectiveAsOf, finishDate));
  const weeksRemaining = roundTo(daysRemaining / 7, 2);

  const latest = snapshots[snapshots.length - 1];
  const prev7 = snapshots.length >= 8 ? snapshots[snapshots.length - 8] : undefined;

  const currentCompositeKg = latest ? roundTo(latest.weeklyComposite, 2) : null;
  const prevCompositeKg = prev7 ? roundTo(prev7.weeklyComposite, 2) : null;
  const compositeRateKg =
    latest && prev7 ? roundTo(latest.weeklyComposite - prev7.weeklyComposite, 2) : null;
  const compositeMomentumPct =
    latest && prev7 && prev7.weeklyComposite > 0
      ? roundTo(((latest.weeklyComposite - prev7.weeklyComposite) / prev7.weeklyComposite) * 100, 2)
      : null;

  const minWeeklyKg =
    latest ? roundTo(latest.weeklyComposite * (corridor.minPct / 100), 2) : null;
  const maxWeeklyKg =
    latest ? roundTo(latest.weeklyComposite * (corridor.maxPct / 100), 2) : null;

  const projectedMinKg =
    latest
      ? roundTo(latest.weeklyComposite * Math.pow(1 + corridor.minPct / 100, daysRemaining / 7), 1)
      : null;
  const projectedMaxKg =
    latest
      ? roundTo(latest.weeklyComposite * Math.pow(1 + corridor.maxPct / 100, daysRemaining / 7), 1)
      : null;

  function buildHorizon(
    currVal: number | undefined,
    prevVal: number | undefined
  ) {
    if (currVal === undefined) {
      return {
        avg_kg: null,
        rate_kg_wk: null,
        momentum_pct_wk: null,
        status: "CALIBRATING",
      };
    }
    const rate = prevVal !== undefined ? roundTo(currVal - prevVal, 2) : null;
    const pct =
      prevVal !== undefined && prevVal > 0
        ? roundTo(((currVal - prevVal) / prevVal) * 100, 2)
        : null;
    return {
      avg_kg: roundTo(currVal, 2),
      rate_kg_wk: rate,
      momentum_pct_wk: pct,
      status: classifyStatus(pct, subcycleType, calibrationDay),
    };
  }

  const compositeDiagnostic = evaluateMomentumDiagnostic(
    compositeMomentumPct,
    subcycleType,
    calibrationDay
  );

  return {
    cycle_metadata: {
      cycle_name: cycleName,
      subcycle_type: subcycleType,
      subcycle_label: corridor.label,
      start_date: startDate,
      finish_date: finishDate,
      weeks_remaining: weeksRemaining,
      calibration_complete: calibrationComplete,
      calibration_day: calibrationDay,
      total_timeline_days: totalTimelineDays,
    },
    target_corridor: {
      min_weekly_pct: corridor.minPct,
      max_weekly_pct: corridor.maxPct,
      min_weekly_kg: minWeeklyKg,
      max_weekly_kg: maxWeeklyKg,
    },
    global_safety_bounds: {
      possible_muscle_loss_below_pct: -1.0,
      suboptimal_muscle_gain_above_pct: 0.75,
    },
    projected_finish_weight: {
      min_kg: projectedMinKg,
      max_kg: projectedMaxKg,
    },
    weekly_body_weight_composite: {
      formula_weights: { "7d": 3, "14d": 4, "21d": 2, "28d": 1 },
      current_kg: currentCompositeKg,
      previous_week_kg: prevCompositeKg,
      weekly_rate_kg: compositeRateKg,
      weekly_momentum_pct: compositeMomentumPct,
      status: compositeDiagnostic.status,
      diagnostic_code: compositeDiagnostic.status,
      severity: compositeDiagnostic.severity,
      diagnostic_badge: compositeDiagnostic.badge,
      diagnostic_message: compositeDiagnostic.message,
      zone: compositeMomentumPct !== null ? classifyZone(compositeMomentumPct) : null,
    },
    horizons: {
      "7d": buildHorizon(latest?.avg7d, prev7?.avg7d),
      "14d": buildHorizon(latest?.avg14d, prev7?.avg14d),
      "21d": buildHorizon(latest?.avg21d, prev7?.avg21d),
      "28d": buildHorizon(latest?.avg28d, prev7?.avg28d),
    },
    ...(options.includeDailyTimeline
      ? {
          daily_timeline: dailyPoints.map((pt) => ({
            date: pt.date,
            weight_kg: roundTo(pt.weightKg, 2),
            source: pt.source,
            day_index: pt.dayIndexFromFirstReal,
          })),
        }
      : {}),
  };
}

export const makeListDateRangeInputSchema = () => ({
  startDate: isoDateSchema().optional().describe("Start date filter (YYYY-MM-DD, inclusive)"),
  endDate: isoDateSchema().optional().describe("End date filter (YYYY-MM-DD, inclusive)"),
});

export const ListDateRangeInputSchema = makeListDateRangeInputSchema();

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

/**
 * Parses CycleGoal metadata header matching CycleGoal.parseMetadata() in Android.
 */
export function parseCycleGoalMetadata(rawNotes?: string): {
  goalMetric: "WEIGHT" | "CALORIES" | "REPS";
  timeWindowMinutes?: number;
  cleanNotes: string;
} {
  const input = rawNotes ?? "";
  const match = /^\[metric=(WEIGHT|CALORIES|REPS),window=(OPEN|\d+)\]\s*(.*)$/is.exec(input);
  if (!match) {
    return { goalMetric: "WEIGHT", cleanNotes: input.trim() };
  }
  const rawMetric = match[1].toUpperCase();
  const goalMetric: "WEIGHT" | "CALORIES" | "REPS" =
    rawMetric === "CALORIES" ? "CALORIES" : rawMetric === "REPS" ? "REPS" : "WEIGHT";
  const windowStr = match[2].toUpperCase();
  const timeWindowMinutes = windowStr === "OPEN" ? undefined : Number(windowStr);
  return {
    goalMetric,
    timeWindowMinutes,
    cleanNotes: (match[3] ?? "").trim(),
  };
}

// ============================================================================
// Routine Builder v2 (`[rb_v2=...]`) Codec — 100% Parity with Android RoutineTargetCodec.kt
// ============================================================================

const RB_V2_HEADER_LINE_REGEX = /^\[rb_v2=([^\r\n\]]*)\](?:\r?\n)?/i;
const SINGLE_EFFORT_ROUTINE_FORMATS = new Set(["amrap", "for time", "chipper", "max effort"]);

export function defaultFixedForRoutineMetric(metric: RoutineTargetMetric): number {
  switch (metric) {
    case "CALORIES":
      return 15;
    case "TIME_SECONDS":
      return 45;
    case "METERS":
      return 250;
    case "REPS":
    default:
      return 8;
  }
}

export function inferRoutineTargetMetricFromLibraryMetric(metricType?: string): RoutineTargetMetric {
  switch ((metricType ?? "").toUpperCase()) {
    case "CALORIES":
      return "CALORIES";
    case "TIME":
      return "TIME_SECONDS";
    case "DISTANCE":
      return "METERS";
    case "WEIGHT":
    case "REPS":
    default:
      return "REPS";
  }
}

export function inferLibraryMetricTypeFromRoutineTarget(
  metric?: RoutineTargetMetric,
  category?: string
): string {
  switch (metric) {
    case "CALORIES":
      return "CALORIES";
    case "TIME_SECONDS":
      return "TIME";
    case "METERS":
      return "DISTANCE";
    case "REPS":
    default:
      return (category ?? "").toUpperCase() === "GYMNASTICS" ? "REPS" : "WEIGHT";
  }
}

export function defaultSetsForRoutineFormat(format?: string): number {
  const normalized = (format ?? "").trim().toLowerCase();
  if (SINGLE_EFFORT_ROUTINE_FORMATS.has(normalized)) return 1;
  if (normalized === "tabata") return 8;
  return 4;
}

export function resizeRoutinePerSetValues(
  currentValues: number[],
  fallbackFixed: number,
  newSetsCount: number
): number[] {
  const targetCount = Math.max(1, Math.trunc(newSetsCount));
  if (currentValues.length === 0) {
    const fill = Math.max(0, Math.trunc(fallbackFixed));
    return Array.from({ length: targetCount }, () => fill);
  }
  const clean = currentValues.map((v) => Math.max(0, Math.trunc(v)));
  if (clean.length === targetCount) return clean;
  if (targetCount < clean.length) return clean.slice(0, targetCount);
  const lastVal = clean[clean.length - 1];
  return [...clean, ...Array.from({ length: targetCount - clean.length }, () => lastVal)];
}

export function hasStructuredRoutineHeader(notes?: string): boolean {
  if (!notes) return false;
  return RB_V2_HEADER_LINE_REGEX.test(notes.trimStart());
}

export function extractCleanRoutineNotes(notes?: string): string {
  if (!notes) return "";
  const withoutV2 = notes.trimStart().replace(RB_V2_HEADER_LINE_REGEX, "");
  const lines = withoutV2.split(/\r?\n/);
  const firstLine = (lines[0] ?? "").trim();
  if (/^Score:\s*(TIME|REPS|CALORIES|DISTANCE|WEIGHT)$/i.test(firstLine)) {
    return lines.slice(1).join("\n").trim();
  }
  return withoutV2.trim();
}

export function encodeRoutineBlockTargets(
  cleanNotes: string,
  slots: RoutineExerciseTargetSlot[],
  setsCount: number
): string {
  const sanitizedNotes = extractCleanRoutineNotes(cleanNotes);
  if (slots.length === 0) return sanitizedNotes;
  const normalizedSets = Math.max(1, Math.trunc(setsCount));
  const encodedSlots = slots
    .map((slot) => {
      const cleanFixed = Math.max(0, Math.trunc(slot.fixedValue));
      const values =
        slot.mode === "FIXED"
          ? Array.from({ length: normalizedSets }, () => cleanFixed)
          : resizeRoutinePerSetValues(slot.perSetValues, cleanFixed, normalizedSets);
      return `${slot.metric}:${slot.mode}:${cleanFixed}:${values.join(".")}`;
    })
    .join(";");
  const header = `[rb_v2=${encodedSlots}]`;
  return sanitizedNotes.length === 0 ? header : `${header}\n${sanitizedNotes}`;
}

export function decodeRoutineHeaderSlots(
  notes: string | undefined,
  setsCount: number
): RoutineExerciseTargetSlot[] | null {
  if (!notes) return null;
  const match = RB_V2_HEADER_LINE_REGEX.exec(notes.trimStart());
  if (!match) return null;
  const payload = (match[1] ?? "").trim();
  if (!payload) return [];
  const normalizedSets = Math.max(1, Math.trunc(setsCount));

  const parsed: RoutineExerciseTargetSlot[] = [];
  for (const rawSlot of payload.split(";")) {
    const parts = rawSlot.trim().split(":");
    if (parts.length < 4) continue;
    const rawMetric = parts[0].trim().toUpperCase();
    const metric: RoutineTargetMetric | null =
      rawMetric === "REPS" ||
      rawMetric === "CALORIES" ||
      rawMetric === "TIME_SECONDS" ||
      rawMetric === "METERS"
        ? rawMetric
        : null;
    const rawMode = parts[1].trim().toUpperCase();
    const mode: RoutineTargetMode | null =
      rawMode === "FIXED" || rawMode === "PER_SET" ? rawMode : null;
    if (!metric || !mode) continue;
    const parsedFixed = Number.parseInt(parts[2].trim(), 10);
    const fixedValue = Number.isFinite(parsedFixed)
      ? Math.max(0, parsedFixed)
      : defaultFixedForRoutineMetric(metric);
    const rawValues = parts[3]
      .split(".")
      .map((s) => Number.parseInt(s.trim(), 10))
      .filter((n) => Number.isFinite(n))
      .map((n) => Math.max(0, n));
    const perSetValues =
      mode === "FIXED"
        ? Array.from({ length: normalizedSets }, () => fixedValue)
        : resizeRoutinePerSetValues(rawValues, fixedValue, normalizedSets);
    parsed.push({ metric, mode, fixedValue, perSetValues });
  }
  return parsed.length > 0 ? parsed : null;
}

function formatRoutineMetricValueAndUnit(metric: RoutineTargetMetric, value: number): string {
  switch (metric) {
    case "REPS":
      return `${value} ${value === 1 ? "rep" : "reps"}`;
    case "CALORIES":
      return `${value} cal`;
    case "TIME_SECONDS":
      return `${value}s`;
    case "METERS":
      return `${value}m`;
  }
}

export function formatRoutineExerciseBadge(
  slot: RoutineExerciseTargetSlot,
  setsCount: number
): string {
  if (slot.mode === "FIXED") {
    return `${formatRoutineMetricValueAndUnit(slot.metric, slot.fixedValue)} / set`;
  }
  const vals = resizeRoutinePerSetValues(slot.perSetValues, slot.fixedValue, setsCount);
  const joined = vals.join(" • ");
  switch (slot.metric) {
    case "REPS":
      return `${joined} reps`;
    case "CALORIES":
      return `${joined} cal`;
    case "TIME_SECONDS":
      return `${joined}s`;
    case "METERS":
      return `${joined}m`;
  }
}

export function formatRoutineBlockTargetSummary(
  slots: RoutineExerciseTargetSlot[],
  setsCount: number
): string {
  if (slots.length === 0) return "";
  if (slots.length === 1) {
    const single = slots[0];
    if (single.mode === "FIXED") {
      return single.metric === "REPS"
        ? String(single.fixedValue)
        : formatRoutineMetricValueAndUnit(single.metric, single.fixedValue);
    }
    const vals = resizeRoutinePerSetValues(single.perSetValues, single.fixedValue, setsCount).join("-");
    switch (single.metric) {
      case "REPS":
        return vals;
      case "CALORIES":
        return `${vals} cal`;
      case "TIME_SECONDS":
        return `${vals}s`;
      case "METERS":
        return `${vals}m`;
    }
  }
  const raw = slots
    .map((slot) => {
      if (slot.mode === "FIXED") {
        return formatRoutineMetricValueAndUnit(slot.metric, slot.fixedValue);
      }
      const vals = resizeRoutinePerSetValues(slot.perSetValues, slot.fixedValue, setsCount).join("-");
      switch (slot.metric) {
        case "REPS":
          return `${vals} reps`;
        case "CALORIES":
          return `${vals} cal`;
        case "TIME_SECONDS":
          return `${vals}s`;
        case "METERS":
          return `${vals}m`;
      }
    })
    .join(" + ");
  return raw.length <= 190 ? raw : `${raw.slice(0, 187)}...`;
}
