import type { Firestore } from "firebase-admin/firestore";
import { encodeCycleGoalMetadata, parseCycleGoalMetadata } from "./schemas.js";

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
  /** Set on MCP writes; the Android manual sync only imports stamped records (see UserCloudSyncManager). */
  updatedAtMillis?: number;
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
    deletedAtMillis?: number | null;
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
    isActiveCycle?: boolean;
    date: string;
    title: string;
    notes: string;
    updatedAtMillis?: number;
    deletedAtMillis?: number | null;
  };
  blocks: SessionBlockEntryRecord[];
}

export interface CycleGoalRecord {
  id: number;
  cycleId: number;
  cycleName?: string;
  isActiveCycle?: boolean;
  exerciseId: number;
  exerciseName?: string;
  targetReps: number;
  startWeight: number;
  targetWeight: number;
  notes: string;
  updatedAtMillis?: number;
  deletedAtMillis?: number | null;
}

export interface RepMaxRecord {
  id: number;
  exerciseId: number;
  exerciseName?: string;
  reps: number;
  weight: number;
  date: string;
  cycleId: number | null;
  cycleName?: string;
  isActiveCycle?: boolean;
  updatedAtMillis?: number;
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

export interface CycleSummary {
  cycleId: number;
  cycleName: string;
  isActive: boolean;
  explicitActiveFlag?: boolean;
  goalsCount: number;
  sessionsCount: number;
  repMaxesCount: number;
  latestActivityDate: string | null;
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
  runAtomicMutation?<R>(
    ctx: UserContext,
    collections: CollectionName[],
    mutator: (txStore: RawDocumentBackingStore) => Promise<R>
  ): Promise<R>;
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

export function isDeletedTombstone(deletedAtMillis?: number | null): boolean {
  return typeof deletedAtMillis === "number" && deletedAtMillis > 0;
}

/**
 * True when a discovered cycle carries a real synced name (not the empty-account placeholder and not
 * the `Cycle {id}` fallback), so MCP writes must keep that name instead of a caller-supplied one.
 */
function hasKnownCycleName(cycle: CycleSummary): boolean {
  const hasData = cycle.goalsCount + cycle.sessionsCount + cycle.repMaxesCount > 0;
  return hasData && cycle.cycleName !== `Cycle ${cycle.cycleId}`;
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

  async runAtomicMutation<R>(
    ctx: UserContext,
    collections: CollectionName[],
    mutator: (txStore: RawDocumentBackingStore) => Promise<R>
  ): Promise<R> {
    const uniqueCollections = Array.from(new Set(collections));
    return this.db.runTransaction(async (tx) => {
      // Firestore requires all transaction reads before any writes.
      const snapshots = new Map<CollectionName, unknown[]>();
      const dirty = new Set<CollectionName>();

      for (const col of uniqueCollections) {
        const snap = await tx.get(this.docRef(ctx, col));
        const rawList = snap.exists && Array.isArray(snap.data()?.list) ? snap.data()!.list : [];
        snapshots.set(col, structuredClone(rawList));
      }

      const txStore: RawDocumentBackingStore = {
        readCollection: async <T = Record<string, unknown>>(
          _innerCtx: UserContext,
          col: CollectionName
        ): Promise<T[]> => {
          if (!snapshots.has(col)) {
            const snap = await tx.get(this.docRef(ctx, col));
            const rawList = snap.exists && Array.isArray(snap.data()?.list) ? snap.data()!.list : [];
            snapshots.set(col, structuredClone(rawList));
          }
          return structuredClone(snapshots.get(col) ?? []) as T[];
        },
        writeCollection: async <T = Record<string, unknown>>(
          _innerCtx: UserContext,
          col: CollectionName,
          list: T[]
        ): Promise<void> => {
          if (list.length > 10000) {
            throw new Error(`Collection ${col} exceeds Firestore rules limit of 10,000 items`);
          }
          snapshots.set(col, structuredClone(list));
          dirty.add(col);
        },
      };

      const result = await mutator(txStore);
      for (const col of dirty) {
        tx.set(this.docRef(ctx, col), { list: snapshots.get(col) ?? [] });
      }
      return result;
    });
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

  async runAtomicMutation<R>(
    ctx: UserContext,
    collections: CollectionName[],
    mutator: (txStore: RawDocumentBackingStore) => Promise<R>
  ): Promise<R> {
    const uniqueCollections = Array.from(new Set(collections));
    const working = new Map<CollectionName, unknown[]>();
    const dirty = new Set<CollectionName>();

    for (const col of uniqueCollections) {
      working.set(col, structuredClone(this.store.get(this.key(ctx, col)) ?? []));
    }

    const txStore: RawDocumentBackingStore = {
      readCollection: async <T = Record<string, unknown>>(
        _innerCtx: UserContext,
        col: CollectionName
      ): Promise<T[]> => {
        if (!working.has(col)) {
          working.set(col, structuredClone(this.store.get(this.key(ctx, col)) ?? []));
        }
        return structuredClone(working.get(col) ?? []) as T[];
      },
      writeCollection: async <T = Record<string, unknown>>(
        _innerCtx: UserContext,
        col: CollectionName,
        list: T[]
      ): Promise<void> => {
        if (list.length > 10000) {
          throw new Error(`Collection ${col} exceeds limit of 10,000 items`);
        }
        working.set(col, structuredClone(list));
        dirty.add(col);
      },
    };

    const result = await mutator(txStore);
    for (const col of dirty) {
      this.store.set(this.key(ctx, col), working.get(col) ?? []);
    }
    return result;
  }
}

export interface UpsertExerciseInput {
  name: string;
  category?: string;
  metricType?: string;
  unit?: string;
  tracksRepMax?: boolean;
}

export interface UpsertCycleGoalItemInput {
  exerciseName: string;
  goalMetric?: "WEIGHT" | "CALORIES" | "REPS";
  targetReps?: number;
  previousTargetReps?: number;
  timeWindowMinutes?: number;
  startWeight?: number;
  targetWeight: number;
  notes?: string;
}

export interface DeleteCycleGoalItemInput {
  exerciseName: string;
  targetReps?: number;
}

/**
 * High-level domain service mediating between MCP tools and the 7 Firestore collections.
 * Serializes mutations per user (`env:uid`) and executes multi-collection writes atomically.
 */
export class WorkoutDomainService {
  private readonly userLocks = new Map<string, Promise<void>>();

  constructor(private readonly backing: RawDocumentBackingStore) {}

  private async runWithUserMutationLock<R>(
    ctx: UserContext,
    collections: CollectionName[],
    mutator: (tx: RawDocumentBackingStore) => Promise<R>
  ): Promise<R> {
    const lockKey = `${ctx.env}:${ctx.uid}`;
    const previous = this.userLocks.get(lockKey) ?? Promise.resolve();
    let release!: () => void;
    const gate = new Promise<void>((resolve) => {
      release = resolve;
    });
    const tail = previous.then(() => gate, () => gate);
    this.userLocks.set(lockKey, tail);

    await previous.catch(() => undefined);
    try {
      if (this.backing.runAtomicMutation) {
        return await this.backing.runAtomicMutation(ctx, collections, mutator);
      }
      return await mutator(this.backing);
    } finally {
      release();
      if (this.userLocks.get(lockKey) === tail) {
        this.userLocks.delete(lockKey);
      }
    }
  }

  // ==========================================================================
  // Cycle Discovery & Dynamic Active-Cycle Resolution
  // ==========================================================================

  private computeCyclesFromRaw(
    rawGoals: CycleGoalRecord[],
    rawSessions: SessionWithBlocksRecord[],
    rawRepMaxes: RepMaxRecord[]
  ): CycleSummary[] {
    interface MutableCycleAgg {
      cycleId: number;
      cycleName?: string;
      cycleNameTs: number;
      explicitActiveFlag?: boolean;
      explicitFlagTs: number;
      goalsCount: number;
      sessionsCount: number;
      repMaxesCount: number;
      latestActivityDate: string | null;
      latestUpdatedAtMillis: number;
    }

    const byId = new Map<number, MutableCycleAgg>();
    const ensureAgg = (rawId: number | null | undefined): MutableCycleAgg | null => {
      const id = Number(rawId);
      if (!Number.isFinite(id) || id < 1) return null;
      let agg = byId.get(id);
      if (!agg) {
        agg = {
          cycleId: id,
          cycleNameTs: -1,
          explicitFlagTs: -1,
          goalsCount: 0,
          sessionsCount: 0,
          repMaxesCount: 0,
          latestActivityDate: null,
          latestUpdatedAtMillis: 0,
        };
        byId.set(id, agg);
      }
      return agg;
    };

    const recordMeta = (
      agg: MutableCycleAgg,
      cycleName: string | undefined,
      isActiveCycle: boolean | undefined,
      updatedAtMillis: number | undefined,
      isoDate?: string
    ) => {
      const ts = Number(updatedAtMillis) || 0;
      if (ts > agg.latestUpdatedAtMillis) {
        agg.latestUpdatedAtMillis = ts;
      }
      if (cycleName && cycleName.trim().length > 0 && ts >= agg.cycleNameTs) {
        agg.cycleName = cycleName.trim();
        agg.cycleNameTs = ts;
      }
      if (typeof isActiveCycle === "boolean" && ts >= agg.explicitFlagTs) {
        agg.explicitActiveFlag = isActiveCycle;
        agg.explicitFlagTs = ts;
      }
      if (isoDate && (!agg.latestActivityDate || isoDate > agg.latestActivityDate)) {
        agg.latestActivityDate = isoDate;
      }
    };

    for (const g of rawGoals) {
      if (isDeletedTombstone(g.deletedAtMillis)) continue;
      const agg = ensureAgg(g.cycleId);
      if (!agg) continue;
      agg.goalsCount += 1;
      recordMeta(agg, g.cycleName, g.isActiveCycle, g.updatedAtMillis);
    }

    for (const s of rawSessions) {
      if (isDeletedTombstone(s.session?.deletedAtMillis)) continue;
      const agg = ensureAgg(s.session?.cycleId);
      if (!agg) continue;
      agg.sessionsCount += 1;
      recordMeta(
        agg,
        s.session.cycleName,
        s.session.isActiveCycle,
        s.session.updatedAtMillis,
        s.session.date
      );
    }

    for (const rm of rawRepMaxes) {
      const agg = ensureAgg(rm.cycleId);
      if (!agg) continue;
      agg.repMaxesCount += 1;
      recordMeta(agg, rm.cycleName, rm.isActiveCycle, rm.updatedAtMillis, rm.date);
    }

    const entries = Array.from(byId.values());
    if (entries.length === 0) {
      return [
        {
          cycleId: 1,
          cycleName: "Active Cycle",
          isActive: true,
          goalsCount: 0,
          sessionsCount: 0,
          repMaxesCount: 0,
          latestActivityDate: null,
        },
      ];
    }

    // Precedence 1: Any cycle explicitly marked isActiveCycle === true on synced records
    const explicitActives = entries.filter((e) => e.explicitActiveFlag === true);
    let winningCycleId: number;
    if (explicitActives.length > 0) {
      explicitActives.sort((a, b) => {
        if (b.explicitFlagTs !== a.explicitFlagTs) return b.explicitFlagTs - a.explicitFlagTs;
        return b.cycleId - a.cycleId;
      });
      winningCycleId = explicitActives[0].cycleId;
    } else {
      // Precedence 2: Highest non-false cycleId (tie-breaking by latest activity/update)
      const candidates = entries.filter((e) => e.explicitActiveFlag !== false);
      const pool = candidates.length > 0 ? candidates : entries;
      pool.sort((a, b) => {
        if (b.cycleId !== a.cycleId) return b.cycleId - a.cycleId;
        const dateCmp = (b.latestActivityDate ?? "").localeCompare(a.latestActivityDate ?? "");
        if (dateCmp !== 0) return dateCmp;
        return b.latestUpdatedAtMillis - a.latestUpdatedAtMillis;
      });
      winningCycleId = pool[0].cycleId;
    }

    return entries
      .sort((a, b) => b.cycleId - a.cycleId)
      .map((e) => ({
        cycleId: e.cycleId,
        cycleName: e.cycleName ?? `Cycle ${e.cycleId}`,
        isActive: e.cycleId === winningCycleId,
        ...(e.explicitActiveFlag !== undefined ? { explicitActiveFlag: e.explicitActiveFlag } : {}),
        goalsCount: e.goalsCount,
        sessionsCount: e.sessionsCount,
        repMaxesCount: e.repMaxesCount,
        latestActivityDate: e.latestActivityDate,
      }));
  }

  private async resolveTargetCycleInStore(
    tx: RawDocumentBackingStore,
    ctx: UserContext,
    requestedCycleId?: number,
    requestedCycleName?: string
  ): Promise<{ cycleId: number; cycleName: string; isActive: boolean }> {
    const [goals, sessions, repMaxes] = await Promise.all([
      tx.readCollection<CycleGoalRecord>(ctx, "cycle_goals"),
      tx.readCollection<SessionWithBlocksRecord>(ctx, "sessions"),
      tx.readCollection<RepMaxRecord>(ctx, "rep_maxes"),
    ]);
    const cycles = this.computeCyclesFromRaw(goals, sessions, repMaxes);
    const active = cycles.find((c) => c.isActive) ?? cycles[0];

    if (requestedCycleId !== undefined && requestedCycleId >= 1) {
      const matched = cycles.find((c) => c.cycleId === requestedCycleId);
      // Prefer the known synced name so an MCP write can never silently rename an existing cycle
      // (cycles are owned by the Android app; Android resolves name-first on download).
      const knownName = matched && hasKnownCycleName(matched) ? matched.cycleName : undefined;
      return {
        cycleId: requestedCycleId,
        cycleName: knownName || requestedCycleName?.trim() || `Cycle ${requestedCycleId}`,
        isActive: requestedCycleId === active.cycleId,
      };
    }

    if (requestedCycleName && requestedCycleName.trim().length > 0) {
      const normalized = requestedCycleName.trim().toLowerCase();
      const matchedByName = cycles.find((c) => c.cycleName.trim().toLowerCase() === normalized);
      if (matchedByName) {
        return {
          cycleId: matchedByName.cycleId,
          cycleName: matchedByName.cycleName,
          isActive: matchedByName.isActive,
        };
      }
      // Unknown cycle name: target the active cycle. Only adopt the requested name when the account has
      // no synced cycle data yet (placeholder cycle); otherwise a typo would rename the active cycle.
      return {
        cycleId: active.cycleId,
        cycleName: hasKnownCycleName(active) ? active.cycleName : requestedCycleName.trim(),
        isActive: true,
      };
    }

    return {
      cycleId: active.cycleId,
      cycleName: active.cycleName,
      isActive: true,
    };
  }

  async listCycles(ctx: UserContext): Promise<{
    activeCycleId: number;
    activeCycleName: string;
    cycles: CycleSummary[];
  }> {
    const [goals, sessions, repMaxes] = await Promise.all([
      this.backing.readCollection<CycleGoalRecord>(ctx, "cycle_goals"),
      this.backing.readCollection<SessionWithBlocksRecord>(ctx, "sessions"),
      this.backing.readCollection<RepMaxRecord>(ctx, "rep_maxes"),
    ]);
    const cycles = this.computeCyclesFromRaw(goals, sessions, repMaxes);
    const active = cycles.find((c) => c.isActive) ?? cycles[0];
    return {
      activeCycleId: active.cycleId,
      activeCycleName: active.cycleName,
      cycles,
    };
  }

  async getActiveCycle(ctx: UserContext): Promise<{
    activeCycle: CycleSummary;
    goals: CycleGoalRecord[];
    recentSessions: SessionWithBlocksRecord[];
    allCycles: CycleSummary[];
  }> {
    const [rawGoals, rawSessions, rawRepMaxes, exercises] = await Promise.all([
      this.backing.readCollection<CycleGoalRecord>(ctx, "cycle_goals"),
      this.backing.readCollection<SessionWithBlocksRecord>(ctx, "sessions"),
      this.backing.readCollection<RepMaxRecord>(ctx, "rep_maxes"),
      this.backing.readCollection<ExerciseRecord>(ctx, "exercises"),
    ]);
    const cycles = this.computeCyclesFromRaw(rawGoals, rawSessions, rawRepMaxes);
    const active = cycles.find((c) => c.isActive) ?? cycles[0];
    const exById = new Map(exercises.map((e) => [e.id, e.name]));

    const goals = rawGoals
      .filter((g) => !isDeletedTombstone(g.deletedAtMillis) && g.cycleId === active.cycleId)
      .map((g) => ({
        ...g,
        cycleName: g.cycleName ?? active.cycleName,
        isActiveCycle: true,
        exerciseName: g.exerciseName ?? exById.get(g.exerciseId),
      }));

    const recentSessions = rawSessions
      .filter(
        (s) =>
          !isDeletedTombstone(s.session?.deletedAtMillis) &&
          s.session?.cycleId === active.cycleId
      )
      .sort((a, b) => (b.session.date ?? "").localeCompare(a.session.date ?? ""))
      .slice(0, 10);

    return {
      activeCycle: active,
      goals,
      recentSessions,
      allCycles: cycles,
    };
  }

  async getWorkoutSummary(ctx: UserContext) {
    const [activeInfo, routines, weights, dailyLogs, exercises, repMaxes] = await Promise.all([
      this.getActiveCycle(ctx),
      this.listRoutines(ctx),
      this.listWeightEntries(ctx, {}),
      this.listDailyLogs(ctx, {}),
      this.listExercises(ctx),
      this.listRepMaxes(ctx),
    ]);

    return {
      activeCycle: activeInfo.activeCycle,
      activeCycleGoalsCount: activeInfo.goals.length,
      activeCycleGoals: activeInfo.goals,
      recentActiveCycleSessions: activeInfo.recentSessions.slice(0, 5),
      cyclesCount: activeInfo.allCycles.length,
      exercisesCount: exercises.length,
      routinesCount: routines.length,
      repMaxesCount: repMaxes.length,
      latestWeightEntry: weights[0] ?? null,
      latestDailyLog: dailyLogs[0] ?? null,
    };
  }

  // ==========================================================================
  // Exercises
  // ==========================================================================

  async listExercises(
    ctx: UserContext,
    filter?: { category?: string; search?: string }
  ): Promise<ExerciseRecord[]> {
    const exercises = await this.backing.readCollection<ExerciseRecord>(ctx, "exercises");
    if (!filter?.category && !filter?.search) {
      return exercises;
    }
    const categoryUpper = filter.category?.trim().toUpperCase();
    const searchLower = filter.search?.trim().toLowerCase();
    return exercises.filter((ex) => {
      if (categoryUpper && (ex.category ?? "").toUpperCase() !== categoryUpper) return false;
      if (searchLower && !ex.name.toLowerCase().includes(searchLower)) return false;
      return true;
    });
  }

  private applyExerciseUpsertInMemory(
    exercises: ExerciseRecord[],
    input: UpsertExerciseInput,
    now: number
  ): ExerciseRecord {
    const trimmed = input.name.trim();
    const existingIndex = exercises.findIndex(
      (e) => e.name.trim().toLowerCase() === trimmed.toLowerCase()
    );

    if (existingIndex >= 0) {
      const current = exercises[existingIndex];
      const resolvedMetric = input.metricType ?? current.metricType ?? "WEIGHT";
      const updated: ExerciseRecord = {
        id: current.id,
        name: trimmed,
        category: input.category ?? current.category ?? "BARBELL",
        metricType: resolvedMetric,
        unit: input.unit ?? current.unit ?? defaultUnitForMetric(resolvedMetric),
        tracksRepMax:
          input.tracksRepMax ?? current.tracksRepMax ?? resolvedMetric === "WEIGHT",
        updatedAtMillis: now,
      };
      exercises[existingIndex] = updated;
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
      updatedAtMillis: now,
    };
    exercises.push(created);
    return created;
  }

  async getOrCreateExercise(
    ctx: UserContext,
    input: UpsertExerciseInput
  ): Promise<ExerciseRecord> {
    const results = await this.batchUpsertExercises(ctx, [input]);
    return results[0];
  }

  async batchUpsertExercises(
    ctx: UserContext,
    inputs: UpsertExerciseInput[]
  ): Promise<ExerciseRecord[]> {
    return this.runWithUserMutationLock(ctx, ["exercises"], async (tx) => {
      const exercises = await tx.readCollection<ExerciseRecord>(ctx, "exercises");
      const now = Date.now();
      const upserted = inputs.map((item) =>
        this.applyExerciseUpsertInMemory(exercises, item, now)
      );
      await tx.writeCollection(ctx, "exercises", exercises);
      return upserted;
    });
  }

  private async resolveExerciseMapInStore(
    tx: RawDocumentBackingStore,
    ctx: UserContext,
    requests: Array<{ name: string; metricType?: string; tracksRepMax?: boolean }>
  ): Promise<Map<string, ExerciseRecord>> {
    const exercises = await tx.readCollection<ExerciseRecord>(ctx, "exercises");
    let mutated = false;
    let maxId = exercises.reduce((max, ex) => Math.max(max, Number(ex.id) || 0), 0);
    const result = new Map<string, ExerciseRecord>();
    const now = Date.now();

    for (const req of requests) {
      const trimmed = req.name.trim();
      if (!trimmed) continue;
      const key = trimmed.toLowerCase();
      const existing =
        result.get(key) ??
        exercises.find((e) => e.name.trim().toLowerCase() === key);
      if (existing) {
        result.set(key, existing);
        continue;
      }
      maxId += 1;
      const metricType = req.metricType ?? "WEIGHT";
      const created: ExerciseRecord = {
        id: maxId,
        name: trimmed,
        category: "BARBELL",
        metricType,
        unit: defaultUnitForMetric(metricType),
        tracksRepMax: req.tracksRepMax ?? metricType === "WEIGHT",
        updatedAtMillis: now,
      };
      exercises.push(created);
      result.set(key, created);
      mutated = true;
    }

    if (mutated) {
      await tx.writeCollection(ctx, "exercises", exercises);
    }
    return result;
  }

  // ==========================================================================
  // Routines (Workout Templates)
  // ==========================================================================

  async listRoutines(ctx: UserContext): Promise<RoutineWithBlocksRecord[]> {
    const routines = await this.backing.readCollection<RoutineWithBlocksRecord>(ctx, "routines");
    return routines.filter((r) => !isDeletedTombstone(r.routine?.deletedAtMillis));
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
    return this.runWithUserMutationLock(ctx, ["exercises", "routines"], async (tx) => {
      const allExerciseReqs: Array<{ name: string }> = [];
      if (input.mainExerciseName) allExerciseReqs.push({ name: input.mainExerciseName });
      for (const b of input.blocks) {
        if (b.exerciseNames) {
          for (const n of b.exerciseNames) allExerciseReqs.push({ name: n });
        }
      }
      const exerciseMap = await this.resolveExerciseMapInStore(tx, ctx, allExerciseReqs);

      const routines = await tx.readCollection<RoutineWithBlocksRecord>(ctx, "routines");
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
          deletedAtMillis: null,
        },
        blocks,
      };

      if (existingIdx >= 0) {
        routines[existingIdx] = record;
      } else {
        routines.push(record);
      }
      await tx.writeCollection(ctx, "routines", routines);
      return record;
    });
  }

  async deleteRoutine(ctx: UserContext, name: string): Promise<boolean> {
    return this.runWithUserMutationLock(ctx, ["routines"], async (tx) => {
      const routines = await tx.readCollection<RoutineWithBlocksRecord>(ctx, "routines");
      const trimmed = name.trim().toLowerCase();
      const now = Date.now();
      let deleted = false;
      for (let i = 0; i < routines.length; i++) {
        const r = routines[i];
        if (
          !isDeletedTombstone(r.routine?.deletedAtMillis) &&
          r.routine.name.trim().toLowerCase() === trimmed
        ) {
          routines[i] = {
            ...r,
            routine: {
              ...r.routine,
              updatedAtMillis: now,
              deletedAtMillis: now,
            },
          };
          deleted = true;
        }
      }
      if (deleted) {
        await tx.writeCollection(ctx, "routines", routines);
      }
      return deleted;
    });
  }

  // ==========================================================================
  // Workout Sessions
  // ==========================================================================

  async listSessions(
    ctx: UserContext,
    filter?: {
      startDate?: string;
      endDate?: string;
      cycleId?: number;
      activeCycleOnly?: boolean;
      limit?: number;
    }
  ): Promise<SessionWithBlocksRecord[]> {
    let targetCycleId = filter?.cycleId;
    if (targetCycleId === undefined && filter?.activeCycleOnly) {
      const cycleInfo = await this.listCycles(ctx);
      targetCycleId = cycleInfo.activeCycleId;
    }

    const sessions = await this.backing.readCollection<SessionWithBlocksRecord>(
      ctx,
      "sessions"
    );
    const filtered = sessions.filter((s) => {
      if (isDeletedTombstone(s.session?.deletedAtMillis)) return false;
      const d = s.session?.date ?? "";
      if (filter?.startDate && d < filter.startDate) return false;
      if (filter?.endDate && d > filter.endDate) return false;
      if (targetCycleId !== undefined && s.session?.cycleId !== targetCycleId) return false;
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
    return this.runWithUserMutationLock(
      ctx,
      ["exercises", "sessions", "cycle_goals", "rep_maxes"],
      async (tx) => {
        const targetCycle = await this.resolveTargetCycleInStore(
          tx,
          ctx,
          input.cycleId,
          input.cycleName
        );

        const exReqs: Array<{ name: string }> = [];
        for (const b of input.blocks) {
          if (b.mainExerciseName) exReqs.push({ name: b.mainExerciseName });
          if (b.exerciseNames) {
            for (const n of b.exerciseNames) exReqs.push({ name: n });
          }
        }
        const exerciseMap = await this.resolveExerciseMapInStore(tx, ctx, exReqs);

        const sessions = await tx.readCollection<SessionWithBlocksRecord>(ctx, "sessions");
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
            cycleId: targetCycle.cycleId,
            cycleName: targetCycle.cycleName,
            isActiveCycle: targetCycle.isActive,
            date: input.date,
            title: trimmedTitle,
            notes: input.notes ?? "",
            updatedAtMillis: Date.now(),
            deletedAtMillis: null,
          },
          blocks,
        };

        if (existingIdx >= 0) {
          sessions[existingIdx] = record;
        } else {
          sessions.push(record);
        }
        await tx.writeCollection(ctx, "sessions", sessions);
        return record;
      }
    );
  }

  async deleteSession(ctx: UserContext, date: string, title: string): Promise<boolean> {
    return this.runWithUserMutationLock(ctx, ["sessions"], async (tx) => {
      const sessions = await tx.readCollection<SessionWithBlocksRecord>(ctx, "sessions");
      const trimmed = title.trim().toLowerCase();
      const now = Date.now();
      let deleted = false;

      for (let i = 0; i < sessions.length; i++) {
        const s = sessions[i];
        if (
          !isDeletedTombstone(s.session?.deletedAtMillis) &&
          s.session.date === date &&
          s.session.title.trim().toLowerCase() === trimmed
        ) {
          sessions[i] = {
            ...s,
            session: {
              ...s.session,
              updatedAtMillis: now,
              deletedAtMillis: now,
            },
          };
          deleted = true;
        }
      }

      if (deleted) {
        await tx.writeCollection(ctx, "sessions", sessions);
      }
      return deleted;
    });
  }

  // ==========================================================================
  // Cycle Goals (Single & Atomic Multi-Order Batch Management)
  // ==========================================================================

  async listCycleGoals(
    ctx: UserContext,
    filter?: { cycleId?: number; activeCycleOnly?: boolean }
  ): Promise<{
    activeCycleId: number;
    activeCycleName: string;
    cycleGoals: CycleGoalRecord[];
  }> {
    const [rawGoals, rawSessions, rawRepMaxes, exercises] = await Promise.all([
      this.backing.readCollection<CycleGoalRecord>(ctx, "cycle_goals"),
      this.backing.readCollection<SessionWithBlocksRecord>(ctx, "sessions"),
      this.backing.readCollection<RepMaxRecord>(ctx, "rep_maxes"),
      this.backing.readCollection<ExerciseRecord>(ctx, "exercises"),
    ]);
    const cycles = this.computeCyclesFromRaw(rawGoals, rawSessions, rawRepMaxes);
    const active = cycles.find((c) => c.isActive) ?? cycles[0];
    const cycleNameById = new Map(cycles.map((c) => [c.cycleId, c.cycleName]));
    const exById = new Map(exercises.map((e) => [e.id, e.name]));

    const targetCycleId =
      filter?.cycleId !== undefined
        ? filter.cycleId
        : filter?.activeCycleOnly
          ? active.cycleId
          : undefined;

    const cycleGoals = rawGoals
      .filter((g) => {
        if (isDeletedTombstone(g.deletedAtMillis)) return false;
        if (targetCycleId !== undefined && g.cycleId !== targetCycleId) return false;
        return true;
      })
      .map((g) => ({
        ...g,
        cycleName: g.cycleName ?? cycleNameById.get(g.cycleId),
        isActiveCycle: g.cycleId === active.cycleId,
        exerciseName: g.exerciseName ?? exById.get(g.exerciseId),
      }));

    return {
      activeCycleId: active.cycleId,
      activeCycleName: active.cycleName,
      cycleGoals,
    };
  }

  async manageCycleGoals(
    ctx: UserContext,
    input: {
      cycleId?: number;
      cycleName?: string;
      upserts?: UpsertCycleGoalItemInput[];
      deletes?: DeleteCycleGoalItemInput[];
    }
  ): Promise<{
    cycle: { cycleId: number; cycleName: string; isActive: boolean };
    upserted: CycleGoalRecord[];
    deleted: Array<{ exerciseName: string; targetReps?: number; deleted: boolean }>;
  }> {
    return this.runWithUserMutationLock(
      ctx,
      ["exercises", "cycle_goals", "sessions", "rep_maxes"],
      async (tx) => {
        const targetCycle = await this.resolveTargetCycleInStore(
          tx,
          ctx,
          input.cycleId,
          input.cycleName
        );

        const upsertItems = input.upserts ?? [];
        const deleteItems = input.deletes ?? [];

        const exerciseMap = await this.resolveExerciseMapInStore(
          tx,
          ctx,
          upsertItems.map((u) => {
            const metric = u.goalMetric ?? "WEIGHT";
            return {
              name: u.exerciseName,
              metricType:
                metric === "CALORIES" ? "CALORIES" : metric === "REPS" ? "REPS" : "WEIGHT",
              tracksRepMax: metric === "WEIGHT",
            };
          })
        );

        const allExercises = await tx.readCollection<ExerciseRecord>(ctx, "exercises");
        const goals = await tx.readCollection<CycleGoalRecord>(ctx, "cycle_goals");
        const now = Date.now();
        let mutated = false;

        const markMatchingGoalsDeleted = (
          exerciseName: string,
          targetReps?: number,
          goalMetricFilter?: "WEIGHT" | "CALORIES" | "REPS"
        ): boolean => {
          const targetLower = exerciseName.trim().toLowerCase();
          const matchingExIds = new Set(
            allExercises
              .filter((e) => e.name.trim().toLowerCase() === targetLower)
              .map((e) => e.id)
          );
          let anyDeleted = false;
          for (let i = 0; i < goals.length; i++) {
            const g = goals[i];
            if (isDeletedTombstone(g.deletedAtMillis)) continue;
            if (g.cycleId !== targetCycle.cycleId) continue;
            const sameEx =
              matchingExIds.has(g.exerciseId) ||
              (g.exerciseName ?? "").trim().toLowerCase() === targetLower;
            if (!sameEx) continue;
            if (targetReps !== undefined && g.targetReps !== targetReps) continue;
            if (
              goalMetricFilter !== undefined &&
              parseCycleGoalMetadata(g.notes).goalMetric !== goalMetricFilter
            ) {
              continue;
            }
            goals[i] = {
              ...g,
              updatedAtMillis: now,
              deletedAtMillis: now,
            };
            anyDeleted = true;
            mutated = true;
          }
          return anyDeleted;
        };

        const deletedResults: Array<{
          exerciseName: string;
          targetReps?: number;
          deleted: boolean;
        }> = [];

        // 1. Apply explicit deletes first (so delete + upsert in same call works cleanly in any order)
        for (const del of deleteItems) {
          const removed = markMatchingGoalsDeleted(del.exerciseName, del.targetReps);
          deletedResults.push({
            exerciseName: del.exerciseName.trim(),
            ...(del.targetReps !== undefined ? { targetReps: del.targetReps } : {}),
            deleted: removed,
          });
        }

        // 2. Apply upserts (handling optional previousTargetReps conversion atomically)
        let maxId = goals.reduce((max, g) => Math.max(max, Number(g.id) || 0), 0);
        const upsertedResults: CycleGoalRecord[] = [];

        for (const item of upsertItems) {
          const metric = item.goalMetric ?? "WEIGHT";
          const ex = exerciseMap.get(item.exerciseName.trim().toLowerCase());
          if (!ex) {
            throw new Error(`Failed to resolve exercise '${item.exerciseName}'`);
          }
          const effectiveTargetReps =
            metric === "WEIGHT"
              ? Math.max(1, item.targetReps ?? 1)
              : item.timeWindowMinutes ?? 0;

          if (
            item.previousTargetReps !== undefined &&
            item.previousTargetReps !== effectiveTargetReps
          ) {
            const replacedOld = markMatchingGoalsDeleted(
              ex.name,
              item.previousTargetReps,
              metric
            );
            deletedResults.push({
              exerciseName: ex.name,
              targetReps: item.previousTargetReps,
              deleted: replacedOld,
            });
          }

          const encodedNotes = encodeCycleGoalMetadata(
            item.notes ?? "",
            metric,
            item.timeWindowMinutes
          );

          const existingIdx = goals.findIndex(
            (g) =>
              g.cycleId === targetCycle.cycleId &&
              (g.exerciseId === ex.id ||
                (g.exerciseName ?? "").trim().toLowerCase() === ex.name.toLowerCase()) &&
              parseCycleGoalMetadata(g.notes).goalMetric === metric &&
              g.targetReps === effectiveTargetReps
          );

          const goalId = existingIdx >= 0 ? goals[existingIdx].id : ++maxId;
          const record: CycleGoalRecord = {
            id: goalId,
            cycleId: targetCycle.cycleId,
            cycleName: targetCycle.cycleName,
            isActiveCycle: targetCycle.isActive,
            exerciseId: ex.id,
            exerciseName: ex.name,
            targetReps: effectiveTargetReps,
            startWeight:
              item.startWeight !== undefined
                ? item.startWeight
                : existingIdx >= 0 && !isDeletedTombstone(goals[existingIdx].deletedAtMillis)
                  ? goals[existingIdx].startWeight
                  : 0.0,
            targetWeight: item.targetWeight,
            notes: encodedNotes,
            updatedAtMillis: now,
            deletedAtMillis: null,
          };

          if (existingIdx >= 0) {
            goals[existingIdx] = record;
          } else {
            goals.push(record);
          }
          mutated = true;
          upsertedResults.push(record);
        }

        if (mutated) {
          await tx.writeCollection(ctx, "cycle_goals", goals);
        }

        return {
          cycle: targetCycle,
          upserted: upsertedResults,
          deleted: deletedResults,
        };
      }
    );
  }

  async upsertCycleGoal(
    ctx: UserContext,
    input: UpsertCycleGoalItemInput & { cycleId?: number; cycleName?: string }
  ): Promise<CycleGoalRecord> {
    const res = await this.manageCycleGoals(ctx, {
      cycleId: input.cycleId,
      cycleName: input.cycleName,
      upserts: [input],
    });
    return res.upserted[0];
  }

  async deleteCycleGoal(
    ctx: UserContext,
    input: { exerciseName: string; targetReps?: number; cycleId?: number }
  ): Promise<boolean> {
    const res = await this.manageCycleGoals(ctx, {
      cycleId: input.cycleId,
      deletes: [{ exerciseName: input.exerciseName, targetReps: input.targetReps }],
    });
    return res.deleted[0]?.deleted ?? false;
  }

  // ==========================================================================
  // Rep Maxes (Personal Records)
  // ==========================================================================

  async listRepMaxes(ctx: UserContext, exerciseName?: string): Promise<RepMaxRecord[]> {
    const [repMaxes, exercises] = await Promise.all([
      this.backing.readCollection<RepMaxRecord>(ctx, "rep_maxes"),
      this.backing.readCollection<ExerciseRecord>(ctx, "exercises"),
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
    const results = await this.batchRecordRepMaxes(ctx, [input]);
    return results[0];
  }

  async batchRecordRepMaxes(
    ctx: UserContext,
    inputs: Array<{
      exerciseName: string;
      reps: number;
      weight: number;
      date?: string;
      cycleId?: number;
    }>
  ): Promise<RepMaxRecord[]> {
    return this.runWithUserMutationLock(
      ctx,
      ["exercises", "rep_maxes", "cycle_goals", "sessions"],
      async (tx) => {
        const defaultTargetCycle = await this.resolveTargetCycleInStore(tx, ctx);
        const explicitCycleCache = new Map<number, typeof defaultTargetCycle>();
        const exerciseMap = await this.resolveExerciseMapInStore(
          tx,
          ctx,
          inputs.map((item) => ({
            name: item.exerciseName,
            metricType: "WEIGHT",
            tracksRepMax: true,
          }))
        );

        const repMaxes = await tx.readCollection<RepMaxRecord>(ctx, "rep_maxes");
        let maxId = repMaxes.reduce((max, rm) => Math.max(max, Number(rm.id) || 0), 0);
        const now = Date.now();
        let mutated = false;
        const results: RepMaxRecord[] = [];

        for (const input of inputs) {
          const ex = exerciseMap.get(input.exerciseName.trim().toLowerCase());
          if (!ex) {
            throw new Error(`Failed to resolve exercise '${input.exerciseName}'`);
          }
          const date = input.date ?? todayIsoDate();
          const existing = repMaxes.find(
            (rm) =>
              rm.exerciseId === ex.id &&
              rm.reps === input.reps &&
              rm.weight === input.weight &&
              rm.date === date
          );
          if (existing) {
            results.push({ ...existing, exerciseName: ex.name });
            continue;
          }

          let targetCycle = defaultTargetCycle;
          if (input.cycleId !== undefined && input.cycleId !== defaultTargetCycle.cycleId) {
            const cached = explicitCycleCache.get(input.cycleId);
            targetCycle = cached ?? (await this.resolveTargetCycleInStore(tx, ctx, input.cycleId));
            explicitCycleCache.set(input.cycleId, targetCycle);
          }
          // Every field is always defined: the Firestore Admin SDK rejects `undefined` values on set().
          const created: RepMaxRecord = {
            id: ++maxId,
            exerciseId: ex.id,
            exerciseName: ex.name,
            reps: input.reps,
            weight: input.weight,
            date,
            cycleId: targetCycle.cycleId,
            cycleName: targetCycle.cycleName,
            isActiveCycle: targetCycle.isActive,
            updatedAtMillis: now,
          };
          repMaxes.push(created);
          mutated = true;
          results.push(created);
        }

        if (mutated) {
          await tx.writeCollection(ctx, "rep_maxes", repMaxes);
        }
        return results;
      }
    );
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
      .filter((e) => !isDeletedTombstone(e.deletedAtMillis))
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
    const results = await this.batchUpsertWeightEntries(ctx, [input]);
    return results[0];
  }

  async batchUpsertWeightEntries(
    ctx: UserContext,
    inputs: Array<{ date?: string; weightKg: number; notes?: string }>
  ): Promise<Array<WeightEntryRecord & { isoDate: string }>> {
    return this.runWithUserMutationLock(ctx, ["weight_entries"], async (tx) => {
      const entries = await tx.readCollection<WeightEntryRecord>(ctx, "weight_entries");
      const now = Date.now();
      const results: Array<WeightEntryRecord & { isoDate: string }> = [];

      for (const input of inputs) {
        const isoDate = input.date ?? todayIsoDate();
        const epochDay = isoDateToEpochDay(isoDate);
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
        results.push({ ...record, isoDate });
      }

      await tx.writeCollection(ctx, "weight_entries", entries);
      return results;
    });
  }

  async deleteWeightEntry(ctx: UserContext, isoDate: string): Promise<boolean> {
    return this.runWithUserMutationLock(ctx, ["weight_entries"], async (tx) => {
      const epochDay = isoDateToEpochDay(isoDate);
      const entries = await tx.readCollection<WeightEntryRecord>(ctx, "weight_entries");
      const existingIdx = entries.findIndex(
        (e) => Number(e.date) === epochDay && !isDeletedTombstone(e.deletedAtMillis)
      );
      if (existingIdx < 0) {
        return false;
      }
      const now = Date.now();
      entries[existingIdx] = {
        ...entries[existingIdx],
        updatedAtMillis: now,
        deletedAtMillis: now,
      };
      await tx.writeCollection(ctx, "weight_entries", entries);
      return true;
    });
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
      .filter((l) => !isDeletedTombstone(l.deletedAtMillis))
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
    const results = await this.batchUpsertDailyLogs(ctx, [input]);
    return results[0];
  }

  async batchUpsertDailyLogs(
    ctx: UserContext,
    inputs: Array<{
      date?: string;
      fastCompleted?: boolean;
      isRestDay?: boolean;
      caloriesKcal?: number;
      proteinGrams?: number;
      carbsGrams?: number;
      fatGrams?: number;
      notes?: string;
    }>
  ): Promise<Array<DailyLogRecord & { isoDate: string }>> {
    return this.runWithUserMutationLock(ctx, ["daily_logs"], async (tx) => {
      const logs = await tx.readCollection<DailyLogRecord>(ctx, "daily_logs");
      const now = Date.now();
      const results: Array<DailyLogRecord & { isoDate: string }> = [];

      for (const input of inputs) {
        const isoDate = input.date ?? todayIsoDate();
        const epochDay = isoDateToEpochDay(isoDate);
        const existingIdx = logs.findIndex((l) => Number(l.date) === epochDay);
        const prev =
          existingIdx >= 0 && !isDeletedTombstone(logs[existingIdx].deletedAtMillis)
            ? logs[existingIdx]
            : undefined;

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
        results.push({ ...record, isoDate });
      }

      await tx.writeCollection(ctx, "daily_logs", logs);
      return results;
    });
  }

  async deleteDailyLog(ctx: UserContext, isoDate: string): Promise<boolean> {
    return this.runWithUserMutationLock(ctx, ["daily_logs"], async (tx) => {
      const epochDay = isoDateToEpochDay(isoDate);
      const logs = await tx.readCollection<DailyLogRecord>(ctx, "daily_logs");
      const existingIdx = logs.findIndex(
        (l) => Number(l.date) === epochDay && !isDeletedTombstone(l.deletedAtMillis)
      );
      if (existingIdx < 0) {
        return false;
      }
      const now = Date.now();
      logs[existingIdx] = {
        ...logs[existingIdx],
        updatedAtMillis: now,
        deletedAtMillis: now,
      };
      await tx.writeCollection(ctx, "daily_logs", logs);
      return true;
    });
  }
}
