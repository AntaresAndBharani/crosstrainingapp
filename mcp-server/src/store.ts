import type { Firestore } from "firebase-admin/firestore";
import { encodeCycleGoalMetadata } from "./schemas.js";

export type AppEnv = "production" | "snapshot";

export type CollectionName =
  | "exercises"
  | "routines"
  | "sessions"
  | "cycle_goals"
  | "rep_maxes"
  | "weight_entries"
  | "daily_logs";

export interface UserContext {
  uid: string;
  email?: string;
  env: AppEnv;
}

export interface ExerciseRecord {
  id: number;
  name: string;
  category: string;
  metricType: string;
  unit: string;
  tracksRepMax: boolean;
}

export interface RoutineBlockRecord {
  id: number;
  routineId: number;
  position: number;
  name: string;
  kind: string;
  format: string;
  setsCount: number;
  targetRepsScheme: string;
  exerciseIdsCsv: string;
  exerciseNamesCsv?: string;
  notes: string;
  section?: string;
  subBlock?: string;
}

export interface RoutineWithBlocksRecord {
  routine: {
    id: number;
    name: string;
    description: string;
    mainExerciseId: number | null;
    mainExerciseName?: string | null;
    defaultFormat: string;
    updatedAtMillis?: number;
  };
  blocks: RoutineBlockRecord[];
}

export interface SessionSetRecord {
  id: number;
  blockId: number;
  position: number;
  groupIndex: number;
  reps: number;
  weight: number;
  metricValue: number;
  isWarmup: boolean;
  isFailed: boolean;
  notes: string;
}

export interface SessionBlockEntryRecord {
  block: {
    id: number;
    sessionId: number;
    position: number;
    name: string;
    kind: string;
    format: string;
    scheme: string;
    mainExerciseId: number | null;
    mainExerciseName?: string | null;
    routineId: number | null;
    description: string;
    resultText: string;
    resultValue: number | null;
    notes: string;
    section?: string;
    subBlock?: string;
    exerciseIdsCsv?: string;
    exerciseNamesCsv?: string;
    isCompleted: boolean;
  };
  sets: SessionSetRecord[];
}

export interface SessionWithBlocksRecord {
  session: {
    id: number;
    cycleId: number;
    cycleName?: string;
    date: string;
    title: string;
    notes: string;
    updatedAtMillis?: number;
  };
  blocks: SessionBlockEntryRecord[];
}

export interface CycleGoalRecord {
  id: number;
  cycleId: number;
  cycleName?: string;
  exerciseId: number;
  exerciseName?: string;
  targetReps: number;
  startWeight: number;
  targetWeight: number;
  notes: string;
  updatedAtMillis?: number;
}

export interface RepMaxRecord {
  id: number;
  exerciseId: number;
  exerciseName?: string;
  reps: number;
  weight: number;
  date: string;
  cycleId: number | null;
}

export interface WeightEntryRecord {
  date: number; // Epoch day (LocalDate.toEpochDay)
  weightKg: number;
  notes: string;
  updatedAtMillis: number;
  deletedAtMillis: number | null;
}

export interface DailyLogRecord {
  date: number; // Epoch day (LocalDate.toEpochDay)
  fastCompleted: boolean | null;
  isRestDay: boolean;
  caloriesKcal: number | null;
  proteinGrams: number | null;
  carbsGrams: number | null;
  fatGrams: number | null;
  notes: string;
  updatedAtMillis: number;
  deletedAtMillis: number | null;
}

export interface RawDocumentBackingStore {
  readCollection<T = Record<string, unknown>>(
    ctx: UserContext,
    collection: CollectionName
  ): Promise<T[]>;
  writeCollection<T = Record<string, unknown>>(
    ctx: UserContext,
    collection: CollectionName,
    list: T[]
  ): Promise<void>;
}

export function isoDateToEpochDay(isoDate: string): number {
  const ms = Date.parse(`${isoDate}T00:00:00Z`);
  if (Number.isNaN(ms)) {
    throw new Error(`Invalid ISO date: ${isoDate}`);
  }
  return Math.floor(ms / 86_400_000);
}

export function epochDayToIsoDate(epochDay: number): string {
  const d = new Date(epochDay * 86_400_000);
  return d.toISOString().slice(0, 10);
}

export function todayIsoDate(): string {
  return new Date().toISOString().slice(0, 10);
}

function defaultUnitForMetric(metricType: string): string {
  switch (metricType) {
    case "CALORIES":
      return "cal";
    case "DISTANCE":
      return "m";
    case "TIME":
      return "sec";
    case "REPS":
      return "reps";
    default:
      return "kg";
  }
}

export class FirestoreBackingStore implements RawDocumentBackingStore {
  constructor(private readonly db: Firestore) {}

  private docRef(ctx: UserContext, collection: CollectionName) {
    const cleanUid = ctx.uid.replace(/\//g, "_");
    return this.db
      .collection("environments")
      .doc(ctx.env)
      .collection("users")
      .doc(cleanUid)
      .collection("data")
      .doc(collection);
  }

  async readCollection<T = Record<string, unknown>>(
    ctx: UserContext,
    collection: CollectionName
  ): Promise<T[]> {
    const snap = await this.docRef(ctx, collection).get();
    if (!snap.exists) {
      return [];
    }
    const data = snap.data();
    if (!data || !Array.isArray(data.list)) {
      return [];
    }
    return data.list as T[];
  }

  async writeCollection<T = Record<string, unknown>>(
    ctx: UserContext,
    collection: CollectionName,
    list: T[]
  ): Promise<void> {
    if (list.length > 10000) {
      throw new Error(`Collection ${collection} exceeds Firestore rules limit of 10,000 items`);
    }
    await this.docRef(ctx, collection).set({ list });
  }
}

export class InMemoryBackingStore implements RawDocumentBackingStore {
  private readonly store = new Map<string, unknown[]>();

  private key(ctx: UserContext, collection: CollectionName): string {
    return `${ctx.env}:${ctx.uid}:${collection}`;
  }

  async readCollection<T = Record<string, unknown>>(
    ctx: UserContext,
    collection: CollectionName
  ): Promise<T[]> {
    const items = this.store.get(this.key(ctx, collection)) ?? [];
    return structuredClone(items) as T[];
  }

  async writeCollection<T = Record<string, unknown>>(
    ctx: UserContext,
    collection: CollectionName,
    list: T[]
  ): Promise<void> {
    if (list.length > 10000) {
      throw new Error(`Collection ${collection} exceeds limit of 10,000 items`);
    }
    this.store.set(this.key(ctx, collection), structuredClone(list));
  }
}

/**
 * High-level domain service mediating between MCP tools and the 7 Firestore collections.
 */
export class WorkoutDomainService {
  constructor(private readonly backing: RawDocumentBackingStore) {}

  // ==========================================================================
  // Exercises
  // ==========================================================================

  async listExercises(ctx: UserContext): Promise<ExerciseRecord[]> {
    return this.backing.readCollection<ExerciseRecord>(ctx, "exercises");
  }

  async getOrCreateExercise(
    ctx: UserContext,
    input: {
      name: string;
      category?: string;
      metricType?: string;
      unit?: string;
      tracksRepMax?: boolean;
    }
  ): Promise<ExerciseRecord> {
    const exercises = await this.listExercises(ctx);
    const trimmed = input.name.trim();
    const existingIndex = exercises.findIndex(
      (e) => e.name.trim().toLowerCase() === trimmed.toLowerCase()
    );

    if (existingIndex >= 0) {
      const current = exercises[existingIndex];
      const updated: ExerciseRecord = {
        id: current.id,
        name: trimmed,
        category: input.category ?? current.category ?? "BARBELL",
        metricType: input.metricType ?? current.metricType ?? "WEIGHT",
        unit: input.unit ?? current.unit ?? defaultUnitForMetric(input.metricType ?? current.metricType ?? "WEIGHT"),
        tracksRepMax:
          input.tracksRepMax ??
          current.tracksRepMax ??
          (input.metricType ?? current.metricType ?? "WEIGHT") === "WEIGHT",
      };
      exercises[existingIndex] = updated;
      await this.backing.writeCollection(ctx, "exercises", exercises);
      return updated;
    }

    const maxId = exercises.reduce((max, ex) => Math.max(max, Number(ex.id) || 0), 0);
    const metricType = input.metricType ?? "WEIGHT";
    const created: ExerciseRecord = {
      id: maxId + 1,
      name: trimmed,
      category: input.category ?? "BARBELL",
      metricType,
      unit: input.unit ?? defaultUnitForMetric(metricType),
      tracksRepMax: input.tracksRepMax ?? metricType === "WEIGHT",
    };
    exercises.push(created);
    await this.backing.writeCollection(ctx, "exercises", exercises);
    return created;
  }

  private async resolveExerciseMap(
    ctx: UserContext,
    names: string[]
  ): Promise<Map<string, ExerciseRecord>> {
    const uniqueNames = Array.from(
      new Set(names.map((n) => n.trim()).filter((n) => n.length > 0))
    );
    const exercises = await this.listExercises(ctx);
    let mutated = false;
    let maxId = exercises.reduce((max, ex) => Math.max(max, Number(ex.id) || 0), 0);
    const result = new Map<string, ExerciseRecord>();

    for (const rawName of uniqueNames) {
      let found = exercises.find(
        (e) => e.name.trim().toLowerCase() === rawName.toLowerCase()
      );
      if (!found) {
        maxId += 1;
        found = {
          id: maxId,
          name: rawName,
          category: "BARBELL",
          metricType: "WEIGHT",
          unit: "kg",
          tracksRepMax: true,
        };
        exercises.push(found);
        mutated = true;
      }
      result.set(rawName.toLowerCase(), found);
    }

    if (mutated) {
      await this.backing.writeCollection(ctx, "exercises", exercises);
    }
    return result;
  }

  // ==========================================================================
  // Routines (Workout Templates)
  // ==========================================================================

  async listRoutines(ctx: UserContext): Promise<RoutineWithBlocksRecord[]> {
    return this.backing.readCollection<RoutineWithBlocksRecord>(ctx, "routines");
  }

  async upsertRoutine(
    ctx: UserContext,
    input: {
      name: string;
      description?: string;
      defaultFormat?: string;
      mainExerciseName?: string;
      blocks: Array<{
        name: string;
        kind?: string;
        format?: string;
        setsCount?: number;
        targetRepsScheme?: string;
        exerciseNames?: string[];
        notes?: string;
        section?: string;
        subBlock?: string;
      }>;
    }
  ): Promise<RoutineWithBlocksRecord> {
    const allExerciseNames: string[] = [];
    if (input.mainExerciseName) allExerciseNames.push(input.mainExerciseName);
    for (const b of input.blocks) {
      if (b.exerciseNames) allExerciseNames.push(...b.exerciseNames);
    }
    const exerciseMap = await this.resolveExerciseMap(ctx, allExerciseNames);

    const routines = await this.listRoutines(ctx);
    const trimmedName = input.name.trim();
    const existingIdx = routines.findIndex(
      (r) => r.routine.name.trim().toLowerCase() === trimmedName.toLowerCase()
    );

    const maxRoutineId = routines.reduce(
      (max, r) => Math.max(max, Number(r.routine?.id) || 0),
      0
    );
    const routineId =
      existingIdx >= 0 ? routines[existingIdx].routine.id : maxRoutineId + 1;

    const mainEx = input.mainExerciseName
      ? exerciseMap.get(input.mainExerciseName.trim().toLowerCase()) ?? null
      : null;

    const blocks: RoutineBlockRecord[] = input.blocks.map((b, idx) => {
      const exRecords = (b.exerciseNames ?? [])
        .map((n) => exerciseMap.get(n.trim().toLowerCase()))
        .filter((e): e is ExerciseRecord => Boolean(e));
      return {
        id: idx + 1,
        routineId,
        position: idx,
        name: b.name.trim(),
        kind: b.kind ?? "WEIGHTLIFTING",
        format: b.format ?? "",
        setsCount: b.setsCount ?? 1,
        targetRepsScheme: b.targetRepsScheme ?? "",
        exerciseIdsCsv: exRecords.map((e) => e.id).join(","),
        exerciseNamesCsv: exRecords.map((e) => e.name).join(","),
        notes: b.notes ?? "",
        section: b.section ?? "",
        subBlock: b.subBlock ?? "",
      };
    });

    const record: RoutineWithBlocksRecord = {
      routine: {
        id: routineId,
        name: trimmedName,
        description: input.description ?? "",
        mainExerciseId: mainEx ? mainEx.id : null,
        mainExerciseName: mainEx ? mainEx.name : null,
        defaultFormat: input.defaultFormat ?? "",
        updatedAtMillis: Date.now(),
      },
      blocks,
    };

    if (existingIdx >= 0) {
      routines[existingIdx] = record;
    } else {
      routines.push(record);
    }
    await this.backing.writeCollection(ctx, "routines", routines);
    return record;
  }

  async deleteRoutine(ctx: UserContext, name: string): Promise<boolean> {
    const routines = await this.listRoutines(ctx);
    const trimmed = name.trim().toLowerCase();
    const filtered = routines.filter(
      (r) => r.routine.name.trim().toLowerCase() !== trimmed
    );
    if (filtered.length === routines.length) {
      return false;
    }
    await this.backing.writeCollection(ctx, "routines", filtered);
    return true;
  }

  // ==========================================================================
  // Workout Sessions
  // ==========================================================================

  async listSessions(
    ctx: UserContext,
    filter?: { startDate?: string; endDate?: string; limit?: number }
  ): Promise<SessionWithBlocksRecord[]> {
    const sessions = await this.backing.readCollection<SessionWithBlocksRecord>(
      ctx,
      "sessions"
    );
    const filtered = sessions.filter((s) => {
      const d = s.session?.date ?? "";
      if (filter?.startDate && d < filter.startDate) return false;
      if (filter?.endDate && d > filter.endDate) return false;
      return true;
    });
    filtered.sort((a, b) => (b.session.date ?? "").localeCompare(a.session.date ?? ""));
    const limit = filter?.limit ?? 30;
    return filtered.slice(0, limit);
  }

  async upsertSession(
    ctx: UserContext,
    input: {
      date: string;
      title: string;
      notes?: string;
      cycleId?: number;
      cycleName?: string;
      blocks: Array<{
        name: string;
        kind?: string;
        format?: string;
        scheme?: string;
        mainExerciseName?: string;
        exerciseNames?: string[];
        description?: string;
        resultText?: string;
        resultValue?: number;
        notes?: string;
        section?: string;
        subBlock?: string;
        isCompleted?: boolean;
        sets?: Array<{
          reps: number;
          weight?: number;
          metricValue?: number;
          groupIndex?: number;
          isWarmup?: boolean;
          isFailed?: boolean;
          notes?: string;
        }>;
      }>;
    }
  ): Promise<SessionWithBlocksRecord> {
    const exNames: string[] = [];
    for (const b of input.blocks) {
      if (b.mainExerciseName) exNames.push(b.mainExerciseName);
      if (b.exerciseNames) exNames.push(...b.exerciseNames);
    }
    const exerciseMap = await this.resolveExerciseMap(ctx, exNames);

    const sessions = await this.backing.readCollection<SessionWithBlocksRecord>(
      ctx,
      "sessions"
    );
    const trimmedTitle = input.title.trim();
    const existingIdx = sessions.findIndex(
      (s) =>
        s.session.date === input.date &&
        s.session.title.trim().toLowerCase() === trimmedTitle.toLowerCase()
    );

    const maxSessionId = sessions.reduce(
      (max, s) => Math.max(max, Number(s.session?.id) || 0),
      0
    );
    const sessionId =
      existingIdx >= 0 ? sessions[existingIdx].session.id : maxSessionId + 1;

    const blocks: SessionBlockEntryRecord[] = input.blocks.map((b, bIdx) => {
      const blockId = bIdx + 1;
      const mainEx = b.mainExerciseName
        ? exerciseMap.get(b.mainExerciseName.trim().toLowerCase()) ?? null
        : null;
      const extraEx = (b.exerciseNames ?? [])
        .map((n) => exerciseMap.get(n.trim().toLowerCase()))
        .filter((e): e is ExerciseRecord => Boolean(e));

      const sets: SessionSetRecord[] = (b.sets ?? []).map((st, sIdx) => ({
        id: sIdx + 1,
        blockId,
        position: sIdx,
        groupIndex: st.groupIndex ?? 0,
        reps: st.reps,
        weight: st.weight ?? 0.0,
        metricValue: st.metricValue ?? 0.0,
        isWarmup: st.isWarmup ?? false,
        isFailed: st.isFailed ?? false,
        notes: st.notes ?? "",
      }));

      return {
        block: {
          id: blockId,
          sessionId,
          position: bIdx,
          name: b.name.trim(),
          kind: b.kind ?? "WEIGHTLIFTING",
          format: b.format ?? "",
          scheme: b.scheme ?? "",
          mainExerciseId: mainEx ? mainEx.id : null,
          mainExerciseName: mainEx ? mainEx.name : null,
          routineId: null,
          description: b.description ?? "",
          resultText: b.resultText ?? "",
          resultValue: b.resultValue ?? null,
          notes: b.notes ?? "",
          section: b.section ?? "",
          subBlock: b.subBlock ?? "",
          exerciseIdsCsv: extraEx.map((e) => e.id).join(","),
          exerciseNamesCsv: extraEx.map((e) => e.name).join(","),
          isCompleted: b.isCompleted ?? true,
        },
        sets,
      };
    });

    const record: SessionWithBlocksRecord = {
      session: {
        id: sessionId,
        cycleId: input.cycleId ?? 1,
        cycleName: input.cycleName,
        date: input.date,
        title: trimmedTitle,
        notes: input.notes ?? "",
        updatedAtMillis: Date.now(),
      },
      blocks,
    };

    if (existingIdx >= 0) {
      sessions[existingIdx] = record;
    } else {
      sessions.push(record);
    }
    await this.backing.writeCollection(ctx, "sessions", sessions);
    return record;
  }

  async deleteSession(ctx: UserContext, date: string, title: string): Promise<boolean> {
    const sessions = await this.backing.readCollection<SessionWithBlocksRecord>(
      ctx,
      "sessions"
    );
    const trimmed = title.trim().toLowerCase();
    const filtered = sessions.filter(
      (s) =>
        !(
          s.session.date === date &&
          s.session.title.trim().toLowerCase() === trimmed
        )
    );
    if (filtered.length === sessions.length) {
      return false;
    }
    await this.backing.writeCollection(ctx, "sessions", filtered);
    return true;
  }

  // ==========================================================================
  // Cycle Goals
  // ==========================================================================

  async listCycleGoals(ctx: UserContext): Promise<CycleGoalRecord[]> {
    const [goals, exercises] = await Promise.all([
      this.backing.readCollection<CycleGoalRecord>(ctx, "cycle_goals"),
      this.listExercises(ctx),
    ]);
    const exById = new Map(exercises.map((e) => [e.id, e.name]));
    return goals.map((g) => ({
      ...g,
      exerciseName: g.exerciseName ?? exById.get(g.exerciseId),
    }));
  }

  async upsertCycleGoal(
    ctx: UserContext,
    input: {
      exerciseName: string;
      goalMetric?: "WEIGHT" | "CALORIES" | "REPS";
      targetReps?: number;
      timeWindowMinutes?: number;
      startWeight?: number;
      targetWeight: number;
      notes?: string;
      cycleId?: number;
      cycleName?: string;
    }
  ): Promise<CycleGoalRecord> {
    const metric = input.goalMetric ?? "WEIGHT";
    const ex = await this.getOrCreateExercise(ctx, {
      name: input.exerciseName,
      metricType: metric === "CALORIES" ? "CALORIES" : metric === "REPS" ? "REPS" : "WEIGHT",
    });

    const goals = await this.backing.readCollection<CycleGoalRecord>(ctx, "cycle_goals");
    const cycleId = input.cycleId ?? 1;
    const effectiveTargetReps =
      metric === "WEIGHT"
        ? Math.max(1, input.targetReps ?? 1)
        : input.timeWindowMinutes ?? 0;

    const encodedNotes = encodeCycleGoalMetadata(
      input.notes ?? "",
      metric,
      input.timeWindowMinutes
    );

    const existingIdx = goals.findIndex(
      (g) =>
        g.cycleId === cycleId &&
        (g.exerciseId === ex.id ||
          (g.exerciseName ?? "").toLowerCase() === ex.name.toLowerCase()) &&
        g.targetReps === effectiveTargetReps
    );

    const maxId = goals.reduce((max, g) => Math.max(max, Number(g.id) || 0), 0);
    const record: CycleGoalRecord = {
      id: existingIdx >= 0 ? goals[existingIdx].id : maxId + 1,
      cycleId,
      cycleName: input.cycleName,
      exerciseId: ex.id,
      exerciseName: ex.name,
      targetReps: effectiveTargetReps,
      startWeight: input.startWeight ?? 0.0,
      targetWeight: input.targetWeight,
      notes: encodedNotes,
      updatedAtMillis: Date.now(),
    };

    if (existingIdx >= 0) {
      goals[existingIdx] = record;
    } else {
      goals.push(record);
    }
    await this.backing.writeCollection(ctx, "cycle_goals", goals);
    return record;
  }

  async deleteCycleGoal(
    ctx: UserContext,
    input: { exerciseName: string; targetReps?: number; cycleId?: number }
  ): Promise<boolean> {
    const [goals, exercises] = await Promise.all([
      this.backing.readCollection<CycleGoalRecord>(ctx, "cycle_goals"),
      this.listExercises(ctx),
    ]);
    const targetName = input.exerciseName.trim().toLowerCase();
    const matchingExIds = new Set(
      exercises
        .filter((e) => e.name.trim().toLowerCase() === targetName)
        .map((e) => e.id)
    );
    const cycleId = input.cycleId ?? 1;

    const filtered = goals.filter((g) => {
      const sameCycle = g.cycleId === cycleId;
      const sameEx =
        matchingExIds.has(g.exerciseId) ||
        (g.exerciseName ?? "").trim().toLowerCase() === targetName;
      const sameReps =
        input.targetReps === undefined || g.targetReps === input.targetReps;
      return !(sameCycle && sameEx && sameReps);
    });

    if (filtered.length === goals.length) {
      return false;
    }
    await this.backing.writeCollection(ctx, "cycle_goals", filtered);
    return true;
  }

  // ==========================================================================
  // Rep Maxes (Personal Records)
  // ==========================================================================

  async listRepMaxes(ctx: UserContext, exerciseName?: string): Promise<RepMaxRecord[]> {
    const [repMaxes, exercises] = await Promise.all([
      this.backing.readCollection<RepMaxRecord>(ctx, "rep_maxes"),
      this.listExercises(ctx),
    ]);
    const exById = new Map(exercises.map((e) => [e.id, e.name]));
    const enriched = repMaxes.map((rm) => ({
      ...rm,
      exerciseName: rm.exerciseName ?? exById.get(rm.exerciseId),
    }));
    if (!exerciseName) {
      return enriched;
    }
    const target = exerciseName.trim().toLowerCase();
    return enriched.filter(
      (rm) => (rm.exerciseName ?? "").trim().toLowerCase() === target
    );
  }

  async recordRepMax(
    ctx: UserContext,
    input: {
      exerciseName: string;
      reps: number;
      weight: number;
      date?: string;
      cycleId?: number;
    }
  ): Promise<RepMaxRecord> {
    const ex = await this.getOrCreateExercise(ctx, {
      name: input.exerciseName,
      metricType: "WEIGHT",
      tracksRepMax: true,
    });
    const repMaxes = await this.backing.readCollection<RepMaxRecord>(ctx, "rep_maxes");
    const date = input.date ?? todayIsoDate();

    const existing = repMaxes.find(
      (rm) =>
        rm.exerciseId === ex.id &&
        rm.reps === input.reps &&
        rm.weight === input.weight &&
        rm.date === date
    );
    if (existing) {
      return { ...existing, exerciseName: ex.name };
    }

    const maxId = repMaxes.reduce((max, rm) => Math.max(max, Number(rm.id) || 0), 0);
    const created: RepMaxRecord = {
      id: maxId + 1,
      exerciseId: ex.id,
      exerciseName: ex.name,
      reps: input.reps,
      weight: input.weight,
      date,
      cycleId: input.cycleId ?? 1,
    };
    repMaxes.push(created);
    await this.backing.writeCollection(ctx, "rep_maxes", repMaxes);
    return created;
  }

  // ==========================================================================
  // Body Weight Entries (Tombstone-Aware LWW)
  // ==========================================================================

  async listWeightEntries(
    ctx: UserContext,
    filter?: { startDate?: string; endDate?: string }
  ): Promise<Array<WeightEntryRecord & { isoDate: string }>> {
    const entries = await this.backing.readCollection<WeightEntryRecord>(
      ctx,
      "weight_entries"
    );
    const active = entries
      .filter((e) => e.deletedAtMillis === null || e.deletedAtMillis === undefined)
      .map((e) => ({
        ...e,
        isoDate: epochDayToIsoDate(Number(e.date)),
      }))
      .filter((e) => {
        if (filter?.startDate && e.isoDate < filter.startDate) return false;
        if (filter?.endDate && e.isoDate > filter.endDate) return false;
        return true;
      });
    active.sort((a, b) => b.date - a.date);
    return active;
  }

  async upsertWeightEntry(
    ctx: UserContext,
    input: { date?: string; weightKg: number; notes?: string }
  ): Promise<WeightEntryRecord & { isoDate: string }> {
    const isoDate = input.date ?? todayIsoDate();
    const epochDay = isoDateToEpochDay(isoDate);
    const entries = await this.backing.readCollection<WeightEntryRecord>(
      ctx,
      "weight_entries"
    );
    const now = Date.now();
    const existingIdx = entries.findIndex((e) => Number(e.date) === epochDay);

    const record: WeightEntryRecord = {
      date: epochDay,
      weightKg: input.weightKg,
      notes: input.notes ?? "",
      updatedAtMillis: now,
      deletedAtMillis: null,
    };

    if (existingIdx >= 0) {
      entries[existingIdx] = record;
    } else {
      entries.push(record);
    }
    await this.backing.writeCollection(ctx, "weight_entries", entries);
    return { ...record, isoDate };
  }

  async deleteWeightEntry(ctx: UserContext, isoDate: string): Promise<boolean> {
    const epochDay = isoDateToEpochDay(isoDate);
    const entries = await this.backing.readCollection<WeightEntryRecord>(
      ctx,
      "weight_entries"
    );
    const existingIdx = entries.findIndex((e) => Number(e.date) === epochDay);
    if (existingIdx < 0) {
      return false;
    }
    const now = Date.now();
    entries[existingIdx] = {
      ...entries[existingIdx],
      updatedAtMillis: now,
      deletedAtMillis: now,
    };
    await this.backing.writeCollection(ctx, "weight_entries", entries);
    return true;
  }

  // ==========================================================================
  // Daily Logs (Fasting, Rest Day, Calories & Macros — Tombstone-Aware LWW)
  // ==========================================================================

  async listDailyLogs(
    ctx: UserContext,
    filter?: { startDate?: string; endDate?: string }
  ): Promise<Array<DailyLogRecord & { isoDate: string }>> {
    const logs = await this.backing.readCollection<DailyLogRecord>(ctx, "daily_logs");
    const active = logs
      .filter((l) => l.deletedAtMillis === null || l.deletedAtMillis === undefined)
      .map((l) => ({
        ...l,
        isoDate: epochDayToIsoDate(Number(l.date)),
      }))
      .filter((l) => {
        if (filter?.startDate && l.isoDate < filter.startDate) return false;
        if (filter?.endDate && l.isoDate > filter.endDate) return false;
        return true;
      });
    active.sort((a, b) => b.date - a.date);
    return active;
  }

  async upsertDailyLog(
    ctx: UserContext,
    input: {
      date?: string;
      fastCompleted?: boolean;
      isRestDay?: boolean;
      caloriesKcal?: number;
      proteinGrams?: number;
      carbsGrams?: number;
      fatGrams?: number;
      notes?: string;
    }
  ): Promise<DailyLogRecord & { isoDate: string }> {
    const isoDate = input.date ?? todayIsoDate();
    const epochDay = isoDateToEpochDay(isoDate);
    const logs = await this.backing.readCollection<DailyLogRecord>(ctx, "daily_logs");
    const existingIdx = logs.findIndex((l) => Number(l.date) === epochDay);
    const prev = existingIdx >= 0 ? logs[existingIdx] : undefined;
    const now = Date.now();

    const record: DailyLogRecord = {
      date: epochDay,
      fastCompleted:
        input.fastCompleted !== undefined
          ? input.fastCompleted
          : prev?.fastCompleted ?? null,
      isRestDay:
        input.isRestDay !== undefined ? input.isRestDay : prev?.isRestDay ?? false,
      caloriesKcal:
        input.caloriesKcal !== undefined
          ? input.caloriesKcal
          : prev?.caloriesKcal ?? null,
      proteinGrams:
        input.proteinGrams !== undefined
          ? input.proteinGrams
          : prev?.proteinGrams ?? null,
      carbsGrams:
        input.carbsGrams !== undefined ? input.carbsGrams : prev?.carbsGrams ?? null,
      fatGrams:
        input.fatGrams !== undefined ? input.fatGrams : prev?.fatGrams ?? null,
      notes: input.notes !== undefined ? input.notes : prev?.notes ?? "",
      updatedAtMillis: now,
      deletedAtMillis: null,
    };

    if (existingIdx >= 0) {
      logs[existingIdx] = record;
    } else {
      logs.push(record);
    }
    await this.backing.writeCollection(ctx, "daily_logs", logs);
    return { ...record, isoDate };
  }
}
