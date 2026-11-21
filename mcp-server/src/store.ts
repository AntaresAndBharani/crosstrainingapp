import type { Firestore } from "firebase-admin/firestore";
import {
  AVAILABLE_BLOCK_FORMAT,
  DAILY_ROUTINE_BLOCK_ITEM_FORMAT,
  DAILY_ROUTINE_EXERCISE_ITEM_FORMAT,
  DAILY_ROUTINE_FORMAT,
  STANDALONE_EXERCISE_SESSION_FORMAT,
  type RoutineExerciseTargetSlot,
  type RoutineTargetMetric,
  type RoutineTargetMode,
  type TrackedPrSlotSpec,
  decodeAvailableBlockTrackedPrs,
  decodeDailyRoutineItemHeader,
  decodeLegacyRoutineSlots,
  decodeLogMetricHeader,
  decodeRoutineHeaderSlots,
  defaultFixedForRoutineMetric,
  defaultSetsForRoutineFormat,
  encodeAvailableBlockTrackedPrs,
  encodeCycleGoalMetadata,
  encodeDailyRoutineBlockHeader,
  encodeDailyRoutineExerciseHeader,
  encodeNotesWithLogMetric,
  encodeRoutineBlockTargets,
  extractCleanRoutineNotes,
  formatRoutineBlockTargetSummary,
  formatRoutineExerciseBadge,
  inferLibraryMetricTypeFromRoutineTarget,
  inferRoutineTargetMetricFromLibraryMetric,
  isDailyRoutineFormat,
  normalizeTrackedPrSlotsSpec,
  parseCycleGoalMetadata,
  replaceAvailableBlockTrackedPrHeader,
  resizeRoutinePerSetValues,
  resolveSessionBlockMetric,
  routineMetricCompactUnit,
  stripAvailableBlockTrackedPrHeaders,
} from "./schemas.js";

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

export interface DecodedRoutineBlockExercise {
  exerciseId: number;
  name: string;
  metric: RoutineTargetMetric;
  mode: RoutineTargetMode;
  fixedValue: number;
  perSetValues: number[];
  summary: string;
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
  cleanNotes?: string;
  exercises?: DecodedRoutineBlockExercise[];
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
    routineName?: string | null;
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
  sessionId?: number | null;
  blockId?: number | null;
  sessionTitle?: string;
  sourceBlockTitle?: string;
  updatedAtMillis?: number;
  deletedAtMillis?: number | null;
}

export interface UpdateBlockSessionLogInput {
  sessionId?: number;
  date?: string;
  complex_id?: number;
  complexId?: number;
  blockId?: number;
  block_id?: number;
  complex_title?: string;
  complexTitle?: string;
  blockTitle?: string;
  block_title?: string;
  session_complex_id?: number;
  session_block_id?: number;
  title?: string;
  notes?: string;
  complex_notes?: string;
  complexNotes?: string;
  blockNotes?: string;
  sync_parent_complex_default_prs?: boolean;
  sync_parent_block_default_prs?: boolean;
  cycleId?: number;
  shared_set_weights_kg?: number[];
  exercise_targets?: Array<{
    exercise_name: string;
    target_metric: RoutineTargetMetric;
    target_mode?: RoutineTargetMode;
    fixed_value?: number;
    per_set_values?: number[];
  }>;
  exercises?: Array<{
    exerciseId?: number;
    exerciseName: string;
    weights?: number[];
    metric?: RoutineTargetMetric;
    sets?: Array<{
      weight?: number;
      weight_kg?: number;
      reps?: number;
      metric_value?: number;
      group_index?: number;
    }>;
  }>;
  trackedPrs?: Array<{
    exerciseId?: number;
    exerciseName?: string;
    movementName?: string;
    rmReps: number;
    weight?: number;
    remove?: boolean;
  }>;
  reconcilePrs?: boolean;
}

export interface TrackedPrSlotRecord {
  movementId: number;
  movementName: string;
  rmReps: number;
}

export interface AvailableBlockExerciseRecord {
  id: number;
  name: string;
  category: string;
  metricType: string;
  unit: string;
  tracksRepMax: boolean;
}

export interface AvailableBlockLastSessionRecord {
  date: string;
  sessionTitle: string;
  setWeightsSummary: string;
  topWeight: number | null;
  averageWeight: number | null;
  exerciseWeights: Record<string, number[]>;
}

export interface AvailableBlockRecord {
  id: number;
  title: string;
  instructions: string;
  exercises: AvailableBlockExerciseRecord[];
  trackedPrs: TrackedPrSlotRecord[];
  isAvailableBlockFormat: boolean;
  updatedAtMillis: number | null;
  lastSession: AvailableBlockLastSessionRecord | null;
}

export interface UpsertAvailableBlockInput {
  id?: number;
  complex_id?: number;
  complexId?: number;
  block_id?: number;
  blockId?: number;
  title: string;
  instructions?: string;
  exercises?: string[];
  trackedPrs?: TrackedPrSlotSpec[];
}

export interface LogBlockSessionInput {
  date: string;
  sessionTitle?: string;
  complex_id?: number;
  complexId?: number;
  blockId?: number;
  block_id?: number;
  complex_title?: string;
  complexTitle?: string;
  blockTitle?: string;
  block_title?: string;
  cycleId?: number;
  notes?: string;
  shared_set_weights_kg?: number[];
  exercise_targets?: Array<{
    exercise_name: string;
    target_metric: RoutineTargetMetric;
    target_mode?: RoutineTargetMode;
    fixed_value?: number;
    per_set_values?: number[];
  }>;
  exerciseLogs?: Array<{
    exerciseName: string;
    weights?: number[];
    metric?: RoutineTargetMetric;
    sets?: Array<{
      reps?: number;
      weight_kg?: number;
      weight?: number;
      metric_value?: number;
      group_index?: number;
    }>;
    setCountForEmpty?: number;
  }>;
  recordedPrs?: Array<{
    movementName: string;
    rmReps: number;
    weight: number;
  }>;
  updateTrackedPrLinks?: boolean;
}

export interface DailyRoutineBlockExerciseTargetRecord {
  exerciseId: number | null;
  exerciseName: string;
  target: RoutineExerciseTargetSlot;
}

export interface DailyRoutineItemRecord {
  itemType: "COMPLEX" | "EXERCISE";
  position: number;
  complexId: number | null;
  complexTitle: string | null;
  blockId: number | null;
  blockTitle: string | null;
  instructions: string | null;
  exerciseId: number | null;
  exerciseName: string | null;
  note: string;
  defaultTrackedPrs: TrackedPrSlotRecord[];
  setsCount: number | null;
  targetSpec: RoutineExerciseTargetSlot | null;
  exerciseTargets: DailyRoutineBlockExerciseTargetRecord[];
  missingBlock?: boolean;
}

export interface DailyRoutineRecord {
  id: number;
  title: string;
  notes: string;
  complexCount: number;
  missingComplexCount: number;
  blockCount: number;
  exerciseCount: number;
  missingBlockCount: number;
  totalTrackedPrCount: number;
  lastSessionDate: string | null;
  updatedAtMillis: number | null;
  items: DailyRoutineItemRecord[];
}

export interface UpsertDailyRoutineInput {
  routine_id?: number;
  title: string;
  notes?: string;
  items: Array<{
    item_type: "COMPLEX" | "BLOCK" | "EXERCISE";
    complex_id?: number;
    complex_title?: string;
    block_id?: number;
    block_title?: string;
    exercise_name?: string;
    note?: string;
    default_tracked_prs?: Array<{
      movement_name?: string;
      movementName?: string;
      rm_reps?: number;
      rmReps?: number;
    }>;
    sets_count?: number;
    target_metric?: RoutineTargetMetric;
    target_mode?: RoutineTargetMode;
    fixed_value?: number;
    per_set_values?: number[];
    exercise_targets?: Array<{
      exercise_name: string;
      target_metric: RoutineTargetMetric;
      target_mode?: RoutineTargetMode;
      fixed_value?: number;
      per_set_values?: number[];
    }>;
  }>;
}

export interface LogDailyRoutineSessionInput {
  date: string;
  routine_id?: number;
  routine_title?: string;
  session_title?: string;
  notes?: string;
  cycle_id?: number;
  update_routine_template?: boolean;
  logged_items: Array<{
    item_type: "COMPLEX" | "BLOCK" | "EXERCISE";
    complex_id?: number;
    complex_title?: string;
    block_id?: number;
    block_title?: string;
    exercise_name?: string;
    note?: string;
    set_weights_kg?: number[];
    shared_set_weights_kg?: number[];
    metric?: RoutineTargetMetric;
    sets?: Array<{
      reps?: number;
      weight_kg?: number;
      weight?: number;
      metric_value?: number;
      group_index?: number;
    }>;
    exercise_logs?: Array<{
      exercise_name?: string;
      exerciseName?: string;
      weights?: number[];
      metric?: RoutineTargetMetric;
      sets?: Array<{
        reps?: number;
        weight_kg?: number;
        weight?: number;
        metric_value?: number;
        group_index?: number;
      }>;
      set_count_for_empty?: number;
      setCountForEmpty?: number;
    }>;
    achieved_prs?: Array<{
      movement_name: string;
      rm_reps: number;
      weight_kg: number;
    }>;
  }>;
}

export interface DailyRoutineConstituentProgressRecord {
  itemType: "COMPLEX" | "EXERCISE";
  complexId?: number | null;
  blockId?: number | null;
  title: string;
  subtitle: string;
  unit: "kg" | "reps" | "cal" | "s" | "m";
  allTimeTop: number | null;
  lastTop: number | null;
  lastTopDate: string | null;
  lastAverage: number | null;
  lastVolume: number | null;
  topDelta: number | null;
  lastSets: number[];
  allTimeTopKg: number | null;
  lastTopKg: number | null;
  lastAverageKg: number | null;
  lastVolumeKg: number | null;
  topDeltaKg: number | null;
  lastSetsKg: number[];
  trackedPrs: Array<{
    movementId: number;
    movementName: string;
    rmReps: number;
    bestWeightKg: number | null;
  }>;
  chartPoints: Array<{
    date: string;
    top: number;
    average: number;
    volume: number;
    topKg: number;
    averageKg: number;
    volumeKg: number;
  }>;
  recentSessions: Array<{
    sessionId: number;
    date: string;
    sessionTitle: string;
    unit: "kg" | "reps" | "cal" | "s" | "m";
    top: number | null;
    values: number[];
    topKg: number | null;
    weightsKg: number[];
  }>;
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
      if (isDeletedTombstone(rm.deletedAtMillis)) continue;
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
    const [activeInfo, rawRoutines, weights, dailyLogs, exercises, repMaxes] = await Promise.all([
      this.getActiveCycle(ctx),
      this.backing.readCollection<RoutineWithBlocksRecord>(ctx, "routines"),
      this.listWeightEntries(ctx, {}),
      this.listDailyLogs(ctx, {}),
      this.listExercises(ctx),
      this.listRepMaxes(ctx),
    ]);

    const activeRoutines = rawRoutines.filter((r) => !isDeletedTombstone(r.routine?.deletedAtMillis));
    const nonDailyRoutines = activeRoutines.filter(
      (r) => !isDailyRoutineFormat(r.routine?.defaultFormat)
    );
    const dailyRoutinesCount = activeRoutines.filter((r) =>
      isDailyRoutineFormat(r.routine?.defaultFormat)
    ).length;

    return {
      activeCycle: activeInfo.activeCycle,
      activeCycleGoalsCount: activeInfo.goals.length,
      activeCycleGoals: activeInfo.goals,
      recentActiveCycleSessions: activeInfo.recentSessions.slice(0, 5),
      cyclesCount: activeInfo.allCycles.length,
      exercisesCount: exercises.length,
      routinesCount: nonDailyRoutines.length,
      complexesCount: nonDailyRoutines.length,
      availableBlockCount: nonDailyRoutines.length,
      dailyRoutineCount: dailyRoutinesCount,
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
    requests: Array<{
      name: string;
      category?: string;
      metricType?: string;
      tracksRepMax?: boolean;
    }>
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
      const defaultCat =
        metricType === "CALORIES" || metricType === "DISTANCE"
          ? "MACHINE"
          : metricType === "REPS"
            ? "GYMNASTICS"
            : "BARBELL";
      const created: ExerciseRecord = {
        id: maxId,
        name: trimmed,
        category: req.category ?? defaultCat,
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

  private enrichRoutineBlock(
    blk: RoutineBlockRecord,
    exercisesById: Map<number, ExerciseRecord>
  ): RoutineBlockRecord {
    const setsCount = Math.max(1, Math.trunc(blk.setsCount || 1));
    const cleanNotes = extractCleanRoutineNotes(blk.notes);
    const ids = (blk.exerciseIdsCsv ?? "")
      .split(",")
      .map((s) => Number.parseInt(s.trim(), 10))
      .filter((n) => Number.isFinite(n) && n > 0);
    const rawNames = (blk.exerciseNamesCsv ?? "")
      .split(",")
      .map((s) => s.trim())
      .filter((s) => s.length > 0);

    const count = Math.max(ids.length, rawNames.length);
    const headerSlots = decodeRoutineHeaderSlots(blk.notes, setsCount);
    const decodedExercises: DecodedRoutineBlockExercise[] = [];

    if (count > 0) {
      const slotMetrics: RoutineTargetMetric[] = [];
      const libExercises: Array<ExerciseRecord | undefined> = [];
      for (let i = 0; i < count; i++) {
        const exId = ids[i] ?? 0;
        const libEx = exId > 0 ? exercisesById.get(exId) : undefined;
        libExercises.push(libEx);
        slotMetrics.push(inferRoutineTargetMetricFromLibraryMetric(libEx?.metricType));
      }
      // Mirrors Android `RoutineTargetCodec.decodeBlockExercises`: once a `[rb_v2=...]` header is
      // present, missing positional slots fall back to the exercise's default metric target rather
      // than re-parsing the human-readable `targetRepsScheme` summary.
      const legacySlots =
        headerSlots === null ? decodeLegacyRoutineSlots(blk.targetRepsScheme, setsCount, slotMetrics) : null;

      for (let i = 0; i < count; i++) {
        const exId = ids[i] ?? 0;
        const name = rawNames[i] ?? libExercises[i]?.name ?? `Exercise #${exId}`;
        const defaultMetric = slotMetrics[i];
        const defaultFixed = defaultFixedForRoutineMetric(defaultMetric);
        const slot: RoutineExerciseTargetSlot =
          (headerSlots !== null ? headerSlots[i] : legacySlots?.[i]) ?? {
            metric: defaultMetric,
            mode: "FIXED",
            fixedValue: defaultFixed,
            perSetValues: Array.from({ length: setsCount }, () => defaultFixed),
          };

        decodedExercises.push({
          exerciseId: exId,
          name,
          metric: slot.metric,
          mode: slot.mode,
          fixedValue: slot.fixedValue,
          perSetValues: slot.perSetValues,
          summary: formatRoutineExerciseBadge(slot, Math.max(setsCount, slot.perSetValues.length)),
        });
      }
    }

    return {
      ...blk,
      cleanNotes,
      exercises: decodedExercises,
    };
  }

  async listRoutines(ctx: UserContext): Promise<RoutineWithBlocksRecord[]> {
    const [routines, exercises] = await Promise.all([
      this.backing.readCollection<RoutineWithBlocksRecord>(ctx, "routines"),
      this.backing.readCollection<ExerciseRecord>(ctx, "exercises"),
    ]);
    const exercisesById = new Map<number, ExerciseRecord>(
      exercises.map((e) => [Number(e.id), e])
    );
    return routines
      .filter(
        (r) =>
          !isDeletedTombstone(r.routine?.deletedAtMillis) &&
          !isDailyRoutineFormat(r.routine?.defaultFormat)
      )
      .map((r) => ({
        ...r,
        blocks: (r.blocks ?? []).map((blk) => this.enrichRoutineBlock(blk, exercisesById)),
      }));
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
        exercises?: Array<{
          name: string;
          category?: string;
          metric?: RoutineTargetMetric;
          mode?: RoutineTargetMode;
          fixedValue?: number;
          perSetValues?: number[];
        }>;
        notes?: string;
        section?: string;
        subBlock?: string;
      }>;
    }
  ): Promise<RoutineWithBlocksRecord> {
    return this.runWithUserMutationLock(ctx, ["exercises", "routines"], async (tx) => {
      const allExerciseReqs: Array<{
        name: string;
        category?: string;
        metricType?: string;
      }> = [];
      if (input.mainExerciseName) {
        allExerciseReqs.push({ name: input.mainExerciseName });
      }
      for (const b of input.blocks) {
        if (b.exercises && b.exercises.length > 0) {
          for (const exItem of b.exercises) {
            allExerciseReqs.push({
              name: exItem.name,
              category: exItem.category,
              metricType: exItem.metric
                ? inferLibraryMetricTypeFromRoutineTarget(exItem.metric, exItem.category)
                : undefined,
            });
          }
        } else if (b.exerciseNames) {
          for (const n of b.exerciseNames) {
            allExerciseReqs.push({ name: n });
          }
        }
      }
      const exerciseMap = await this.resolveExerciseMapInStore(tx, ctx, allExerciseReqs);
      const allExercises = await tx.readCollection<ExerciseRecord>(ctx, "exercises");
      const exercisesById = new Map<number, ExerciseRecord>(
        allExercises.map((e) => [Number(e.id), e])
      );

      const routines = await tx.readCollection<RoutineWithBlocksRecord>(ctx, "routines");
      const trimmedName = input.name.trim();
      const existingIdx = routines.findIndex(
        (r) =>
          !isDailyRoutineFormat(r.routine?.defaultFormat) &&
          r.routine.name.trim().toLowerCase() === trimmedName.toLowerCase()
      );

      const maxRoutineId = routines.reduce(
        (max, r) => Math.max(max, Number(r.routine?.id) || 0),
        0
      );
      const routineId =
        existingIdx >= 0 ? routines[existingIdx].routine.id : maxRoutineId + 1;

      const structuredFirstEx =
        input.blocks
          .flatMap((b) => b.exercises ?? [])
          .map((e) => exerciseMap.get(e.name.trim().toLowerCase()))
          .find((e): e is ExerciseRecord => Boolean(e)) ?? null;

      const mainEx = input.mainExerciseName
        ? exerciseMap.get(input.mainExerciseName.trim().toLowerCase()) ?? null
        : structuredFirstEx;

      const storedBlocks: RoutineBlockRecord[] = input.blocks.map((b, idx) => {
        const hasStructuredExercises = Array.isArray(b.exercises) && b.exercises.length > 0;
        const maxPerSetLen = hasStructuredExercises
          ? Math.max(0, ...b.exercises!.map((e) => e.perSetValues?.length ?? 0))
          : 0;
        const resolvedSetsCount =
          b.setsCount !== undefined
            ? Math.max(1, Math.trunc(b.setsCount))
            : maxPerSetLen > 0
              ? maxPerSetLen
              : hasStructuredExercises
                ? defaultSetsForRoutineFormat(b.format)
                : 1;

        if (hasStructuredExercises) {
          const exRecords: ExerciseRecord[] = [];
          const slots: RoutineExerciseTargetSlot[] = [];

          for (const exIn of b.exercises!) {
            const exRec = exerciseMap.get(exIn.name.trim().toLowerCase());
            if (!exRec) continue;
            exRecords.push(exRec);

            const metric: RoutineTargetMetric =
              exIn.metric ?? inferRoutineTargetMetricFromLibraryMetric(exRec.metricType);
            const mode: RoutineTargetMode =
              exIn.mode ??
              (exIn.perSetValues && exIn.perSetValues.length > 0 ? "PER_SET" : "FIXED");

            if (mode === "FIXED") {
              const fixedValue =
                exIn.fixedValue !== undefined
                  ? Math.trunc(exIn.fixedValue)
                  : defaultFixedForRoutineMetric(metric);
              if (fixedValue <= 0) {
                throw new Error(
                  `Exercise '${exIn.name.trim()}' in block '${b.name.trim()}' must have fixedValue >= 1 (got ${fixedValue}). Remove the exercise instead of setting all sets to 0.`
                );
              }
              slots.push({
                metric,
                mode: "FIXED",
                fixedValue,
                perSetValues: Array.from({ length: resolvedSetsCount }, () => fixedValue),
              });
            } else {
              const rawVals = exIn.perSetValues ?? [];
              const firstPositive =
                rawVals.find((v) => v > 0) ??
                (exIn.fixedValue !== undefined && exIn.fixedValue > 0
                  ? Math.trunc(exIn.fixedValue)
                  : defaultFixedForRoutineMetric(metric));
              const perSetValues =
                rawVals.length > 0
                  ? resizeRoutinePerSetValues(rawVals, firstPositive, resolvedSetsCount)
                  : Array.from({ length: resolvedSetsCount }, () =>
                      exIn.fixedValue !== undefined ? Math.trunc(exIn.fixedValue) : firstPositive
                    );
              if (!perSetValues.some((v) => v > 0)) {
                throw new Error(
                  `Exercise '${exIn.name.trim()}' in block '${b.name.trim()}' cannot have 0 (Skip) on every set. Set at least 1 active round (>= 1) or remove the exercise.`
                );
              }
              const fixedValue =
                exIn.fixedValue !== undefined && exIn.fixedValue > 0
                  ? Math.trunc(exIn.fixedValue)
                  : perSetValues.find((v) => v > 0) ?? defaultFixedForRoutineMetric(metric);
              slots.push({
                metric,
                mode: "PER_SET",
                fixedValue,
                perSetValues,
              });
            }
          }

          const encodedNotes = encodeRoutineBlockTargets(b.notes ?? "", slots, resolvedSetsCount);
          const computedSummary =
            b.targetRepsScheme && b.targetRepsScheme.trim().length > 0
              ? b.targetRepsScheme.trim()
              : formatRoutineBlockTargetSummary(slots, resolvedSetsCount);

          return {
            id: idx + 1,
            routineId,
            position: idx,
            name: b.name.trim(),
            kind: b.kind ?? "WEIGHTLIFTING",
            format: b.format ?? "",
            setsCount: resolvedSetsCount,
            targetRepsScheme: computedSummary,
            exerciseIdsCsv: exRecords.map((e) => e.id).join(","),
            exerciseNamesCsv: exRecords.map((e) => e.name).join(","),
            notes: encodedNotes,
            section: b.section ?? "",
            subBlock: b.subBlock ?? "",
          };
        }

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
          setsCount: resolvedSetsCount,
          targetRepsScheme: b.targetRepsScheme ?? "",
          exerciseIdsCsv: exRecords.map((e) => e.id).join(","),
          exerciseNamesCsv: exRecords.map((e) => e.name).join(","),
          notes: b.notes ?? "",
          section: b.section ?? "",
          subBlock: b.subBlock ?? "",
        };
      });

      const storedRecord: RoutineWithBlocksRecord = {
        routine: {
          id: routineId,
          name: trimmedName,
          description: input.description ?? "",
          mainExerciseId: mainEx ? mainEx.id : null,
          mainExerciseName: mainEx ? mainEx.name : null,
          defaultFormat:
            input.defaultFormat ??
            input.blocks.find((b) => (b.format ?? "").trim().length > 0)?.format?.trim() ??
            "",
          updatedAtMillis: Date.now(),
          deletedAtMillis: null,
        },
        blocks: storedBlocks,
      };

      if (existingIdx >= 0) {
        routines[existingIdx] = storedRecord;
      } else {
        routines.push(storedRecord);
      }
      await tx.writeCollection(ctx, "routines", routines);

      return {
        ...storedRecord,
        blocks: storedBlocks.map((blk) => this.enrichRoutineBlock(blk, exercisesById)),
      };
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
          !isDailyRoutineFormat(r.routine?.defaultFormat) &&
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
  // Available Blocks (Streamlined Block Cards & Block-Centric Logging)
  // ==========================================================================

  private buildAvailableBlockRecord(
    rwb: RoutineWithBlocksRecord,
    exercisesById: Map<number, ExerciseRecord>,
    exercisesByLowerName: Map<string, ExerciseRecord>,
    sortedSessions: SessionWithBlocksRecord[],
    includeLastSession: boolean
  ): AvailableBlockRecord {
    const sortedBlocks = [...(rwb.blocks ?? [])].sort((a, b) => a.position - b.position);

    const seenExerciseIds = new Set<number>();
    const resolvedExercises: AvailableBlockExerciseRecord[] = [];
    const appendExercise = (ex?: ExerciseRecord | null) => {
      if (!ex || seenExerciseIds.has(ex.id)) return;
      seenExerciseIds.add(ex.id);
      resolvedExercises.push({
        id: ex.id,
        name: ex.name,
        category: ex.category ?? "BARBELL",
        metricType: ex.metricType ?? "WEIGHT",
        unit: ex.unit ?? defaultUnitForMetric(ex.metricType ?? "WEIGHT"),
        tracksRepMax: ex.tracksRepMax ?? true,
      });
    };

    for (const blk of sortedBlocks) {
      const ids = (blk.exerciseIdsCsv ?? "")
        .split(",")
        .map((s) => Number.parseInt(s.trim(), 10))
        .filter((n) => Number.isFinite(n) && n > 0);
      if (ids.length > 0) {
        for (const id of ids) {
          appendExercise(exercisesById.get(id));
        }
      } else {
        const names = (blk.exerciseNamesCsv ?? "")
          .split(",")
          .map((s) => s.trim())
          .filter((s) => s.length > 0);
        if (names.length > 0) {
          for (const name of names) {
            appendExercise(exercisesByLowerName.get(name.toLowerCase()));
          }
        } else {
          appendExercise(exercisesByLowerName.get((blk.name ?? "").trim().toLowerCase()));
        }
      }
    }
    if (resolvedExercises.length === 0 && rwb.routine.mainExerciseId) {
      appendExercise(exercisesById.get(rwb.routine.mainExerciseId));
    }

    const rawDesc = rwb.routine.description ?? "";
    const firstNotes = sortedBlocks[0]?.notes ?? "";
    const cleanInstructions =
      extractCleanRoutineNotes(rawDesc) || extractCleanRoutineNotes(firstNotes);

    const combinedPrText = [rawDesc, ...sortedBlocks.map((b) => b.notes ?? "")].join("\n");
    const decodedPrs = decodeAvailableBlockTrackedPrs(combinedPrText).map((slot) => {
      const matched = exercisesByLowerName.get(slot.movementName.trim().toLowerCase());
      return {
        movementId: matched?.id ?? 0,
        movementName: matched?.name ?? slot.movementName,
        rmReps: slot.rmReps,
      };
    });

    let lastSession: AvailableBlockLastSessionRecord | null = null;
    if (includeLastSession && sortedSessions.length > 0) {
      const targetTitleLower = rwb.routine.name.trim().toLowerCase();
      for (const s of sortedSessions) {
        const matchingBlocks = (s.blocks ?? []).filter((entry) => {
          const blk = entry.block;
          return (
            blk.routineId === rwb.routine.id ||
            (blk.routineName ?? "").trim().toLowerCase() === targetTitleLower ||
            (blk.subBlock ?? "").trim().toLowerCase() === targetTitleLower
          );
        });
        if (matchingBlocks.length === 0) continue;

        const exerciseWeights: Record<string, number[]> = {};
        const allPositiveWeights: number[] = [];
        for (const entry of matchingBlocks) {
          const exName =
            (entry.block.mainExerciseId
              ? exercisesById.get(entry.block.mainExerciseId)?.name
              : undefined) ??
            entry.block.mainExerciseName ??
            entry.block.name;
          const posWeights = (entry.sets ?? [])
            .map((st) => Number(st.weight))
            .filter((w) => Number.isFinite(w) && w > 0);
          if (exName) {
            exerciseWeights[exName] = posWeights;
          }
          allPositiveWeights.push(...posWeights);
        }

        const topWeight =
          allPositiveWeights.length > 0 ? Math.max(...allPositiveWeights) : null;
        const averageWeight =
          allPositiveWeights.length > 0
            ? Math.round(
                (allPositiveWeights.reduce((sum, w) => sum + w, 0) /
                  allPositiveWeights.length) *
                  100
              ) / 100
            : null;
        const setWeightsSummary =
          allPositiveWeights.length > 0
            ? allPositiveWeights
                .slice(0, 6)
                .map((w) => `${Number.isInteger(w) ? w : w.toFixed(1)} kg`)
                .join(" / ")
            : "Logged";

        lastSession = {
          date: s.session.date,
          sessionTitle: s.session.title,
          setWeightsSummary,
          topWeight,
          averageWeight,
          exerciseWeights,
        };
        break;
      }
    }

    return {
      id: rwb.routine.id,
      title: rwb.routine.name,
      instructions: cleanInstructions,
      exercises: resolvedExercises,
      trackedPrs: decodedPrs,
      isAvailableBlockFormat:
        (rwb.routine.defaultFormat ?? "").toUpperCase() === AVAILABLE_BLOCK_FORMAT,
      updatedAtMillis: rwb.routine.updatedAtMillis ?? null,
      lastSession,
    };
  }

  async listAvailableBlocks(
    ctx: UserContext,
    filter?: { query?: string; includeLastSession?: boolean; limit?: number }
  ): Promise<AvailableBlockRecord[]> {
    const includeLastSession = filter?.includeLastSession !== false;
    const [routines, exercises, sessions] = await Promise.all([
      this.backing.readCollection<RoutineWithBlocksRecord>(ctx, "routines"),
      this.backing.readCollection<ExerciseRecord>(ctx, "exercises"),
      includeLastSession
        ? this.backing.readCollection<SessionWithBlocksRecord>(ctx, "sessions")
        : Promise.resolve([] as SessionWithBlocksRecord[]),
    ]);

    const exercisesById = new Map<number, ExerciseRecord>(
      exercises.map((e) => [Number(e.id), e])
    );
    const exercisesByLowerName = new Map<string, ExerciseRecord>(
      exercises.map((e) => [e.name.trim().toLowerCase(), e])
    );

    const activeSessions = sessions
      .filter((s) => !isDeletedTombstone(s.session?.deletedAtMillis))
      .sort((a, b) => {
        const cmp = (b.session?.date ?? "").localeCompare(a.session?.date ?? "");
        return cmp !== 0 ? cmp : (Number(b.session?.id) || 0) - (Number(a.session?.id) || 0);
      });

    let blocks = routines
      .filter(
        (r) =>
          !isDeletedTombstone(r.routine?.deletedAtMillis) &&
          !isDailyRoutineFormat(r.routine?.defaultFormat)
      )
      .map((r) =>
        this.buildAvailableBlockRecord(
          r,
          exercisesById,
          exercisesByLowerName,
          activeSessions,
          includeLastSession
        )
      );

    const q = filter?.query?.trim().toLowerCase();
    if (q) {
      blocks = blocks.filter(
        (b) =>
          b.title.toLowerCase().includes(q) ||
          b.instructions.toLowerCase().includes(q) ||
          b.exercises.some((e) => e.name.toLowerCase().includes(q)) ||
          b.trackedPrs.some((p) => p.movementName.toLowerCase().includes(q))
      );
    }

    if (filter?.limit !== undefined && filter.limit > 0) {
      blocks = blocks.slice(0, filter.limit);
    }
    return blocks;
  }

  async upsertAvailableBlock(
    ctx: UserContext,
    input: UpsertAvailableBlockInput
  ): Promise<AvailableBlockRecord> {
    const results = await this.batchUpsertAvailableBlocks(ctx, [input]);
    return results[0];
  }

  async batchUpsertAvailableBlocks(
    ctx: UserContext,
    inputs: UpsertAvailableBlockInput[]
  ): Promise<AvailableBlockRecord[]> {
    return this.runWithUserMutationLock(ctx, ["exercises", "routines"], async (tx) => {
      const exerciseReqs: Array<{ name: string; metricType?: string; tracksRepMax?: boolean }> = [];
      for (const item of inputs) {
        for (const exName of item.exercises ?? []) {
          const clean = exName.trim();
          if (clean) exerciseReqs.push({ name: clean });
        }
        for (const slot of item.trackedPrs ?? []) {
          const clean = slot.movementName.trim();
          if (clean) {
            exerciseReqs.push({ name: clean, metricType: "WEIGHT", tracksRepMax: true });
          }
        }
      }

      const exerciseMap = await this.resolveExerciseMapInStore(tx, ctx, exerciseReqs);
      const allExercises = await tx.readCollection<ExerciseRecord>(ctx, "exercises");
      const exercisesById = new Map<number, ExerciseRecord>(
        allExercises.map((e) => [Number(e.id), e])
      );
      const exercisesByLowerName = new Map<string, ExerciseRecord>(
        allExercises.map((e) => [e.name.trim().toLowerCase(), e])
      );

      const routines = await tx.readCollection<RoutineWithBlocksRecord>(ctx, "routines");
      let maxRoutineId = routines.reduce(
        (max, r) => Math.max(max, Number(r.routine?.id) || 0),
        0
      );
      let maxRoutineBlockId = routines.reduce(
        (max, r) =>
          Math.max(
            max,
            ...(r.blocks ?? []).map((b) => Number(b.id) || 0)
          ),
        0
      );

      const now = Date.now();
      const upserted: AvailableBlockRecord[] = [];

      for (const item of inputs) {
        const cleanTitle = item.title.trim();
        if (!cleanTitle) {
          throw new Error("Complex title cannot be blank.");
        }
        const resolvedInputId =
          item.complex_id ?? item.complexId ?? item.id ?? item.block_id ?? item.blockId;
        const existingIdx =
          resolvedInputId !== undefined
            ? routines.findIndex(
                (r) =>
                  !isDailyRoutineFormat(r.routine?.defaultFormat) &&
                  r.routine?.id === resolvedInputId
              )
            : routines.findIndex(
                (r) =>
                  !isDailyRoutineFormat(r.routine?.defaultFormat) &&
                  r.routine?.name.trim().toLowerCase() === cleanTitle.toLowerCase()
              );
        const existing = existingIdx >= 0 ? routines[existingIdx] : undefined;
        const routineId = existing ? existing.routine.id : ++maxRoutineId;

        const existingDecoded = existing
          ? this.buildAvailableBlockRecord(
              existing,
              exercisesById,
              exercisesByLowerName,
              [],
              false
            )
          : undefined;

        const cleanInstructions =
          item.instructions !== undefined
            ? extractCleanRoutineNotes(item.instructions)
            : existingDecoded?.instructions ?? "";

        const resolvedExerciseRecords: ExerciseRecord[] =
          item.exercises !== undefined
            ? Array.from(
                new Map(
                  item.exercises
                    .map((n) => exerciseMap.get(n.trim().toLowerCase()))
                    .filter((e): e is ExerciseRecord => Boolean(e))
                    .map((e) => [e.id, e])
                ).values()
              )
            : (existingDecoded?.exercises ?? [])
                .map((e) => exercisesById.get(e.id))
                .filter((e): e is ExerciseRecord => Boolean(e));

        const trackedPrSpecs: TrackedPrSlotSpec[] =
          item.trackedPrs !== undefined
            ? normalizeTrackedPrSlotsSpec(
                item.trackedPrs.map((s) => {
                  const canonical = exerciseMap.get(s.movementName.trim().toLowerCase());
                  return {
                    movementName: canonical?.name ?? s.movementName.trim(),
                    rmReps: s.rmReps,
                  };
                })
              )
            : (existingDecoded?.trackedPrs ?? []).map((p) => ({
                movementName: p.movementName,
                rmReps: p.rmReps,
              }));

        const encodedNotes = encodeAvailableBlockTrackedPrs(cleanInstructions, trackedPrSpecs);

        const storedBlocks: RoutineBlockRecord[] =
          resolvedExerciseRecords.length === 0
            ? [
                {
                  id: existing?.blocks?.[0]?.id ?? ++maxRoutineBlockId,
                  routineId,
                  position: 0,
                  name: cleanTitle,
                  kind: "STRENGTH",
                  format: "",
                  setsCount: 3,
                  targetRepsScheme: "",
                  exerciseIdsCsv: "",
                  exerciseNamesCsv: "",
                  notes: encodedNotes,
                  section: "",
                  subBlock: cleanTitle,
                },
              ]
            : resolvedExerciseRecords.map((ex, idx) => ({
                id: existing?.blocks?.[idx]?.id ?? ++maxRoutineBlockId,
                routineId,
                position: idx,
                name: ex.name,
                kind: "STRENGTH",
                format: "",
                setsCount: 3,
                targetRepsScheme: "",
                exerciseIdsCsv: String(ex.id),
                exerciseNamesCsv: ex.name,
                notes: idx === 0 ? encodedNotes : cleanInstructions,
                section: "",
                subBlock: cleanTitle,
              }));

        const record: RoutineWithBlocksRecord = {
          routine: {
            id: routineId,
            name: cleanTitle,
            description: cleanInstructions,
            mainExerciseId: resolvedExerciseRecords[0]?.id ?? null,
            mainExerciseName: resolvedExerciseRecords[0]?.name ?? null,
            defaultFormat: AVAILABLE_BLOCK_FORMAT,
            updatedAtMillis: now,
            deletedAtMillis: null,
          },
          blocks: storedBlocks,
        };

        if (existingIdx >= 0) {
          routines[existingIdx] = record;
        } else {
          routines.push(record);
        }

        upserted.push(
          this.buildAvailableBlockRecord(
            record,
            exercisesById,
            exercisesByLowerName,
            [],
            false
          )
        );
      }

      await tx.writeCollection(ctx, "routines", routines);
      return upserted;
    });
  }

  async deleteAvailableBlock(
    ctx: UserContext,
    input: {
      id?: number;
      complex_id?: number;
      complexId?: number;
      block_id?: number;
      blockId?: number;
      title?: string;
      complex_title?: string;
      complexTitle?: string;
    }
  ): Promise<{ deleted: boolean; deletedCount: number; deletedTitles: string[] }> {
    const resolvedId =
      input.complex_id ?? input.complexId ?? input.id ?? input.block_id ?? input.blockId;
    const cleanTitle = (input.complex_title ?? input.complexTitle ?? input.title ?? "")
      .trim()
      .toLowerCase();
    if (resolvedId === undefined && !cleanTitle) {
      throw new Error("Provide either 'complex_id'/'id' or 'title' to delete a Complex.");
    }
    return this.runWithUserMutationLock(ctx, ["routines"], async (tx) => {
      const routines = await tx.readCollection<RoutineWithBlocksRecord>(ctx, "routines");
      const now = Date.now();
      const deletedTitles: string[] = [];

      for (let i = 0; i < routines.length; i++) {
        const r = routines[i];
        if (isDeletedTombstone(r.routine?.deletedAtMillis)) continue;
        if (isDailyRoutineFormat(r.routine?.defaultFormat)) continue;
        const matchesId = resolvedId !== undefined && r.routine.id === resolvedId;
        const matchesTitle =
          Boolean(cleanTitle) && r.routine.name.trim().toLowerCase() === cleanTitle;
        if (matchesId || matchesTitle) {
          deletedTitles.push(r.routine.name);
          routines[i] = {
            ...r,
            routine: {
              ...r.routine,
              updatedAtMillis: now,
              deletedAtMillis: now,
            },
          };
        }
      }

      if (deletedTitles.length > 0) {
        await tx.writeCollection(ctx, "routines", routines);
      }
      return {
        deleted: deletedTitles.length > 0,
        deletedCount: deletedTitles.length,
        deletedTitles,
      };
    });
  }

  private async mergeAvailableBlockTrackedPrsInTx(
    tx: RawDocumentBackingStore,
    ctx: UserContext,
    routines: RoutineWithBlocksRecord[],
    matchedRoutineIdx: number,
    matchedAvailableBlock: AvailableBlockRecord | undefined,
    fallbackInstructions: string,
    additionalSpecs: Array<{ movementName: string; rmReps: number }>,
    exercisesByLowerName: Map<string, ExerciseRecord>,
    now: number
  ): Promise<TrackedPrSlotRecord[] | null> {
    if (matchedRoutineIdx < 0) return null;
    const matchedRoutine = routines[matchedRoutineIdx];
    const validAdditions = additionalSpecs.filter((s) => s.rmReps >= 1 && s.rmReps <= 20);
    if (validAdditions.length === 0) return null;

    const existingSlots = matchedAvailableBlock?.trackedPrs ?? [];
    const mergedSpecs = normalizeTrackedPrSlotsSpec([
      ...existingSlots.map((s) => ({
        movementName: s.movementName,
        rmReps: s.rmReps,
      })),
      ...validAdditions,
    ]);
    const sortedRoutineBlocks = [...(matchedRoutine.blocks ?? [])].sort(
      (a, b) => a.position - b.position
    );
    // Only swap the `[ab_prs=...]` header: legacy daily routines keep their own block notes and
    // `[rb_v2=...]` targets, and secondary blocks drop stale headers so removals stick.
    const updatedRoutineBlocks: RoutineBlockRecord[] =
      sortedRoutineBlocks.length > 0
        ? sortedRoutineBlocks.map((rb, idx) => ({
            ...rb,
            notes:
              idx === 0
                ? replaceAvailableBlockTrackedPrHeader(rb.notes, mergedSpecs)
                : stripAvailableBlockTrackedPrHeaders(rb.notes),
          }))
        : [
            {
              id:
                routines.reduce(
                  (max, r) =>
                    Math.max(max, ...(r.blocks ?? []).map((b) => Number(b.id) || 0)),
                  0
                ) + 1,
              routineId: matchedRoutine.routine.id,
              position: 0,
              name: matchedRoutine.routine.name,
              kind: "STRENGTH",
              format: "",
              setsCount: 3,
              targetRepsScheme: "",
              exerciseIdsCsv: "",
              exerciseNamesCsv: "",
              notes: encodeAvailableBlockTrackedPrs(fallbackInstructions, mergedSpecs),
              section: "",
              subBlock: matchedRoutine.routine.name,
            },
          ];
    routines[matchedRoutineIdx] = {
      ...matchedRoutine,
      routine: {
        ...matchedRoutine.routine,
        updatedAtMillis: now,
      },
      blocks: updatedRoutineBlocks,
    };
    await tx.writeCollection(ctx, "routines", routines);
    return mergedSpecs.map((spec) => {
      const matchedEx = exercisesByLowerName.get(spec.movementName.toLowerCase());
      return {
        movementId: matchedEx?.id ?? 0,
        movementName: matchedEx?.name ?? spec.movementName,
        rmReps: spec.rmReps,
      };
    });
  }

  private buildTargetSlotMapFromInputs(
    targets:
      | Array<{
          exercise_name: string;
          target_metric: RoutineTargetMetric;
          target_mode?: RoutineTargetMode;
          fixed_value?: number;
          per_set_values?: number[];
        }>
      | undefined,
    roundCount: number
  ): Map<string, RoutineExerciseTargetSlot> {
    const map = new Map<string, RoutineExerciseTargetSlot>();
    for (const t of targets ?? []) {
      const cleanName = t.exercise_name.trim().toLowerCase();
      if (!cleanName) continue;
      const mode: RoutineTargetMode =
        t.target_mode ?? (t.per_set_values && t.per_set_values.length > 0 ? "PER_SET" : "FIXED");
      const fixedValue = Math.max(0, Math.trunc(t.fixed_value ?? 1));
      const perSetValues =
        mode === "FIXED"
          ? Array.from({ length: roundCount }, () => fixedValue)
          : resizeRoutinePerSetValues(t.per_set_values ?? [], fixedValue, roundCount);
      map.set(cleanName, {
        metric: t.target_metric,
        mode,
        fixedValue,
        perSetValues,
      });
    }
    return map;
  }

  private expandSharedComplexSetWeights(params: {
    sharedSetWeightsKg: number[];
    orderedExerciseNames: string[];
    exercisesByLowerName: Map<string, ExerciseRecord>;
    targetsByLowerName: Map<string, RoutineExerciseTargetSlot>;
    priorActiveRoundMasksByLowerName?: Map<string, Set<number>>;
    explicitOverridesByLowerName?: Map<
      string,
      {
        exerciseId?: number;
        exerciseName: string;
        weights?: number[];
        metric?: RoutineTargetMetric;
        sets?: Array<{
          reps?: number;
          weight_kg?: number;
          weight?: number;
          metric_value?: number;
          group_index?: number;
        }>;
        setCountForEmpty?: number;
      }
    >;
  }): Array<{
    exerciseId?: number;
    exerciseName: string;
    weights?: number[];
    metric?: RoutineTargetMetric;
    sets?: Array<{
      reps?: number;
      weight_kg?: number;
      weight?: number;
      metric_value?: number;
      group_index?: number;
    }>;
    setCountForEmpty?: number;
  }> {
    const roundCount = params.sharedSetWeightsKg.length;
    const seenLower = new Set<string>();
    const combinedOrder: string[] = [];
    for (const rawName of params.orderedExerciseNames) {
      const clean = rawName.trim();
      const lower = clean.toLowerCase();
      if (clean && !seenLower.has(lower)) {
        seenLower.add(lower);
        combinedOrder.push(clean);
      }
    }
    for (const [lower, ov] of params.explicitOverridesByLowerName?.entries() ?? []) {
      if (!seenLower.has(lower)) {
        seenLower.add(lower);
        combinedOrder.push(ov.exerciseName);
      }
    }

    const valuesByLower = new Map<string, number[]>();
    for (const name of combinedOrder) {
      const lower = name.toLowerCase();
      if (params.explicitOverridesByLowerName?.has(lower)) continue;
      const slot = params.targetsByLowerName.get(lower);
      const priorMask = params.priorActiveRoundMasksByLowerName?.get(lower);
      if (slot) {
        const vals =
          slot.mode === "FIXED"
            ? Array.from({ length: roundCount }, () => Math.max(0, slot.fixedValue))
            : resizeRoutinePerSetValues(slot.perSetValues, slot.fixedValue, roundCount);
        valuesByLower.set(lower, vals);
      } else if (priorMask) {
        valuesByLower.set(
          lower,
          Array.from({ length: roundCount }, (_, rIdx) => (priorMask.has(rIdx + 1) ? 1 : 0))
        );
      } else {
        valuesByLower.set(
          lower,
          Array.from({ length: roundCount }, () => 1)
        );
      }
    }

    let anySkipInComplex = false;
    for (const vals of valuesByLower.values()) {
      if (vals.some((v) => v <= 0)) {
        anySkipInComplex = true;
        break;
      }
    }
    if (!anySkipInComplex) {
      for (const ov of params.explicitOverridesByLowerName?.values() ?? []) {
        const count = ov.sets?.length ?? ov.weights?.length ?? 0;
        if (
          (ov.sets ?? []).some((s) => Number(s.group_index ?? 0) > 0) ||
          (count > 0 && count !== roundCount)
        ) {
          anySkipInComplex = true;
          break;
        }
      }
    }

    return combinedOrder.map((name) => {
      const lower = name.toLowerCase();
      const override = params.explicitOverridesByLowerName?.get(lower);
      if (override) {
        return override;
      }
      const exRec = params.exercisesByLowerName.get(lower);
      const slot = params.targetsByLowerName.get(lower);
      const metric: RoutineTargetMetric =
        slot?.metric ?? inferRoutineTargetMetricFromLibraryMetric(exRec?.metricType);
      const isWeighted = metric === "REPS" && (!exRec || exRec.metricType === "WEIGHT");
      const vals = valuesByLower.get(lower) ?? Array.from({ length: roundCount }, () => 1);
      const sets: Array<{
        reps?: number;
        weight_kg?: number;
        metric_value?: number;
        group_index?: number;
      }> = [];

      for (let rIdx = 0; rIdx < roundCount; rIdx++) {
        const targetVal = vals[rIdx] ?? 0;
        if (targetVal <= 0) continue;
        const groupIndex = anySkipInComplex ? rIdx + 1 : undefined;
        if (metric === "REPS") {
          const wKg = isWeighted ? Number(params.sharedSetWeightsKg[rIdx] ?? 0) : 0;
          sets.push({
            reps: targetVal,
            weight_kg: wKg,
            ...(groupIndex !== undefined ? { group_index: groupIndex } : {}),
          });
        } else {
          sets.push({
            reps: 1,
            metric_value: targetVal,
            ...(groupIndex !== undefined ? { group_index: groupIndex } : {}),
          });
        }
      }

      if (sets.length === 0) {
        return {
          exerciseName: exRec?.name ?? name,
          metric,
          sets: [],
          setCountForEmpty: 0,
        };
      }
      return {
        exerciseName: exRec?.name ?? name,
        metric,
        sets,
      };
    });
  }

  async logBlockSession(
    ctx: UserContext,
    input: LogBlockSessionInput
  ): Promise<{
    session: SessionWithBlocksRecord;
    recordedPrs: RepMaxRecord[];
    updatedTrackedPrs: TrackedPrSlotRecord[] | null;
  }> {
    const hasExplicitLogs = (input.exerciseLogs?.length ?? 0) > 0;
    const hasSharedWeights = (input.shared_set_weights_kg?.length ?? 0) > 0;
    if (!hasExplicitLogs && !hasSharedWeights) {
      throw new Error("Provide non-empty `exerciseLogs` or `shared_set_weights_kg`.");
    }

    return this.runWithUserMutationLock(
      ctx,
      ["exercises", "routines", "sessions", "cycle_goals", "rep_maxes"],
      async (tx) => {
        const targetCycle = await this.resolveTargetCycleInStore(tx, ctx, input.cycleId);

        const routines = await tx.readCollection<RoutineWithBlocksRecord>(ctx, "routines");
        const resolvedComplexId =
          input.complex_id ?? input.complexId ?? input.blockId ?? input.block_id;
        const cleanBlockTitleInput =
          (
            input.complex_title ??
            input.complexTitle ??
            input.blockTitle ??
            input.block_title ??
            ""
          ).trim();
        const matchedRoutineIdx =
          resolvedComplexId !== undefined
            ? routines.findIndex(
                (r) =>
                  !isDeletedTombstone(r.routine?.deletedAtMillis) &&
                  !isDailyRoutineFormat(r.routine?.defaultFormat) &&
                  r.routine.id === resolvedComplexId
              )
            : cleanBlockTitleInput
              ? routines.findIndex(
                  (r) =>
                    !isDeletedTombstone(r.routine?.deletedAtMillis) &&
                    !isDailyRoutineFormat(r.routine?.defaultFormat) &&
                    r.routine.name.trim().toLowerCase() ===
                      cleanBlockTitleInput.toLowerCase()
                )
              : -1;
        const matchedRoutine =
          matchedRoutineIdx >= 0 ? routines[matchedRoutineIdx] : undefined;

        const existingExercises = await tx.readCollection<ExerciseRecord>(ctx, "exercises");
        const existingExById = new Map<number, ExerciseRecord>(
          existingExercises.map((e) => [Number(e.id), e])
        );
        const existingExByLower = new Map<string, ExerciseRecord>(
          existingExercises.map((e) => [e.name.trim().toLowerCase(), e])
        );
        const matchedInitialBlock = matchedRoutine
          ? this.buildAvailableBlockRecord(
              matchedRoutine,
              existingExById,
              existingExByLower,
              [],
              false
            )
          : undefined;

        const resolvedBlockTitle =
          cleanBlockTitleInput ||
          matchedRoutine?.routine.name ||
          input.exerciseLogs?.[0]?.exerciseName.trim() ||
          input.exercise_targets?.[0]?.exercise_name.trim() ||
          "Training Complex";
        const resolvedSessionTitle = input.sessionTitle?.trim() || resolvedBlockTitle;

        const exReqs: Array<{
          name: string;
          metricType?: string;
          tracksRepMax?: boolean;
        }> = [];
        for (const ex of matchedInitialBlock?.exercises ?? []) {
          exReqs.push({ name: ex.name });
        }
        for (const t of input.exercise_targets ?? []) {
          if (t.exercise_name.trim()) {
            exReqs.push({
              name: t.exercise_name.trim(),
              metricType: inferLibraryMetricTypeFromRoutineTarget(t.target_metric),
            });
          }
        }
        for (const log of input.exerciseLogs ?? []) {
          exReqs.push({ name: log.exerciseName });
        }
        for (const pr of input.recordedPrs ?? []) {
          exReqs.push({
            name: pr.movementName,
            metricType: "WEIGHT",
            tracksRepMax: true,
          });
        }
        const exerciseMap = await this.resolveExerciseMapInStore(tx, ctx, exReqs);
        const allExercises = await tx.readCollection<ExerciseRecord>(ctx, "exercises");
        const exercisesById = new Map<number, ExerciseRecord>(
          allExercises.map((e) => [Number(e.id), e])
        );
        const exercisesByLowerName = new Map<string, ExerciseRecord>(
          allExercises.map((e) => [e.name.trim().toLowerCase(), e])
        );

        const matchedAvailableBlock = matchedRoutine
          ? this.buildAvailableBlockRecord(
              matchedRoutine,
              exercisesById,
              exercisesByLowerName,
              [],
              false
            )
          : undefined;
        const blockInstructions = matchedAvailableBlock?.instructions ?? "";

        const resolvedMovementLogs = hasSharedWeights
          ? (() => {
              const sharedWeights = input.shared_set_weights_kg!;
              const orderedNames =
                matchedAvailableBlock && matchedAvailableBlock.exercises.length > 0
                  ? matchedAvailableBlock.exercises.map((e) => e.name)
                  : [
                      ...(input.exercise_targets ?? []).map((t) => t.exercise_name.trim()),
                      ...(input.exerciseLogs ?? []).map((l) => l.exerciseName.trim()),
                    ].filter(Boolean);
              const fallbackNames =
                orderedNames.length > 0 ? orderedNames : [resolvedBlockTitle];
              const targetsByLowerName = this.buildTargetSlotMapFromInputs(
                input.exercise_targets,
                sharedWeights.length
              );
              const explicitMap = new Map<
                string,
                NonNullable<LogBlockSessionInput["exerciseLogs"]>[number]
              >();
              for (const log of input.exerciseLogs ?? []) {
                const clean = log.exerciseName.trim();
                if (clean) explicitMap.set(clean.toLowerCase(), log);
              }
              return this.expandSharedComplexSetWeights({
                sharedSetWeightsKg: sharedWeights,
                orderedExerciseNames: fallbackNames,
                exercisesByLowerName,
                targetsByLowerName,
                explicitOverridesByLowerName: explicitMap,
              });
            })()
          : (input.exerciseLogs ?? []);

        const sessions = await tx.readCollection<SessionWithBlocksRecord>(ctx, "sessions");
        const existingSessionIdx = sessions.findIndex(
          (s) =>
            s.session.date === input.date &&
            s.session.title.trim().toLowerCase() === resolvedSessionTitle.toLowerCase()
        );
        const maxSessionId = sessions.reduce(
          (max, s) => Math.max(max, Number(s.session?.id) || 0),
          0
        );
        let maxBlockId = sessions.reduce(
          (max, s) =>
            Math.max(
              max,
              ...(s.blocks ?? []).map((b) => Number(b.block?.id) || 0)
            ),
          0
        );
        let maxSetId = sessions.reduce(
          (max, s) =>
            Math.max(
              max,
              ...(s.blocks ?? []).flatMap((b) => (b.sets ?? []).map((st) => Number(st.id) || 0))
            ),
          0
        );

        const sessionId =
          existingSessionIdx >= 0 ? sessions[existingSessionIdx].session.id : maxSessionId + 1;
        const now = Date.now();

        const blocks: SessionBlockEntryRecord[] = resolvedMovementLogs.map((log, bIdx) => {
          const ex = exerciseMap.get(log.exerciseName.trim().toLowerCase()) ?? null;
          const blockId = ++maxBlockId;
          const { sets, notes: blockNotes } = this.buildLoggedSessionSetsAndNotes({
            blockId,
            nextSetIdFn: () => ++maxSetId,
            metric: log.metric,
            sets: log.sets,
            weights: log.weights,
            setCountForEmpty: log.setCountForEmpty ?? 1,
            exerciseMetricType: ex?.metricType,
          });

          return {
            block: {
              id: blockId,
              sessionId,
              position: bIdx,
              name: ex?.name ?? log.exerciseName.trim(),
              kind: "STRENGTH",
              format: "",
              scheme: "",
              mainExerciseId: ex ? ex.id : null,
              mainExerciseName: ex ? ex.name : null,
              routineId: matchedRoutine ? matchedRoutine.routine.id : null,
              routineName: matchedRoutine ? matchedRoutine.routine.name : resolvedBlockTitle,
              description: blockInstructions,
              resultText: "",
              resultValue: null,
              notes: blockNotes,
              section: "",
              subBlock: resolvedBlockTitle,
              exerciseIdsCsv: ex ? String(ex.id) : "",
              exerciseNamesCsv: ex ? ex.name : log.exerciseName.trim(),
              isCompleted: true,
            },
            sets,
          };
        });

        const sessionRecord: SessionWithBlocksRecord = {
          session: {
            id: sessionId,
            cycleId: targetCycle.cycleId,
            cycleName: targetCycle.cycleName,
            isActiveCycle: targetCycle.isActive,
            date: input.date,
            title: resolvedSessionTitle,
            notes: input.notes ?? "",
            updatedAtMillis: now,
            deletedAtMillis: null,
          },
          blocks,
        };

        if (existingSessionIdx >= 0) {
          sessions[existingSessionIdx] = sessionRecord;
        } else {
          sessions.push(sessionRecord);
        }
        await tx.writeCollection(ctx, "sessions", sessions);

        const repMaxes = await tx.readCollection<RepMaxRecord>(ctx, "rep_maxes");
        let maxRepMaxId = repMaxes.reduce(
          (max, rm) => Math.max(max, Number(rm.id) || 0),
          0
        );
        const recordedPrs: RepMaxRecord[] = [];
        let repMaxMutated = false;

        for (const pr of input.recordedPrs ?? []) {
          const ex = exerciseMap.get(pr.movementName.trim().toLowerCase());
          if (!ex) {
            throw new Error(`Failed to resolve PR movement '${pr.movementName}'`);
          }
          const matchingBlock =
            blocks.find((b) => b.block.mainExerciseId === ex.id) ?? blocks[0] ?? null;
          const targetBlockId = matchingBlock ? matchingBlock.block.id : null;

          const existingRmIdx = repMaxes.findIndex(
            (rm) =>
              rm.exerciseId === ex.id &&
              rm.reps === pr.rmReps &&
              Math.round(Number(rm.weight) * 100) === Math.round(pr.weight * 100) &&
              rm.date === input.date
          );

          if (existingRmIdx >= 0) {
            const existingRm = repMaxes[existingRmIdx];
            const updatedRm: RepMaxRecord = {
              ...existingRm,
              exerciseName: ex.name,
              sessionId,
              blockId: targetBlockId,
              sessionTitle: resolvedSessionTitle,
              sourceBlockTitle: resolvedBlockTitle,
              updatedAtMillis: now,
              deletedAtMillis: null,
            };
            repMaxes[existingRmIdx] = updatedRm;
            repMaxMutated = true;
            recordedPrs.push(updatedRm);
          } else {
            const createdRm: RepMaxRecord = {
              id: ++maxRepMaxId,
              exerciseId: ex.id,
              exerciseName: ex.name,
              reps: pr.rmReps,
              weight: pr.weight,
              date: input.date,
              cycleId: targetCycle.cycleId,
              cycleName: targetCycle.cycleName,
              isActiveCycle: targetCycle.isActive,
              sessionId,
              blockId: targetBlockId,
              sessionTitle: resolvedSessionTitle,
              sourceBlockTitle: resolvedBlockTitle,
              updatedAtMillis: now,
              deletedAtMillis: null,
            };
            repMaxes.push(createdRm);
            repMaxMutated = true;
            recordedPrs.push(createdRm);
          }
        }

        if (repMaxMutated) {
          await tx.writeCollection(ctx, "rep_maxes", repMaxes);
        }

        let updatedTrackedPrs: TrackedPrSlotRecord[] | null = null;
        if (
          matchedRoutine &&
          matchedRoutineIdx >= 0 &&
          (input.recordedPrs?.length ?? 0) > 0 &&
          input.updateTrackedPrLinks !== false
        ) {
          // Tracked PR slots are limited to 1RM-20RM (Android `[ab_prs=...]` decoder bounds);
          // higher-rep PRs are still recorded as rep_maxes but are not linked onto the template.
          const specs = (input.recordedPrs ?? [])
            .filter((p) => p.rmReps >= 1 && p.rmReps <= 20)
            .map((p) => {
              const resolvedEx = exerciseMap.get(p.movementName.trim().toLowerCase());
              return {
                movementName: resolvedEx?.name ?? p.movementName.trim(),
                rmReps: p.rmReps,
              };
            });
          updatedTrackedPrs = await this.mergeAvailableBlockTrackedPrsInTx(
            tx,
            ctx,
            routines,
            matchedRoutineIdx,
            matchedAvailableBlock,
            blockInstructions,
            specs,
            exercisesByLowerName,
            now
          );
        }

        return {
          session: sessionRecord,
          recordedPrs,
          updatedTrackedPrs,
        };
      }
    );
  }

  async updateBlockSessionLog(
    ctx: UserContext,
    input: UpdateBlockSessionLogInput
  ): Promise<{
    session: SessionWithBlocksRecord;
    reconciledPrs: RepMaxRecord[];
    tombstonedRepMaxIds: number[];
    updatedTrackedPrs: TrackedPrSlotRecord[] | null;
  }> {
    if (input.sessionId === undefined && !input.date) {
      throw new Error("Provide `sessionId` or `date` to locate the session log to update.");
    }

    const resolvedComplexId =
      input.complex_id ?? input.complexId ?? input.blockId ?? input.block_id;
    const resolvedComplexTitle = (
      input.complex_title ??
      input.complexTitle ??
      input.blockTitle ??
      input.block_title ??
      ""
    ).trim();
    const resolvedSessionComplexId = input.session_complex_id ?? input.session_block_id;
    const resolvedComplexNotes =
      input.complex_notes ?? input.complexNotes ?? input.blockNotes;
    const shouldSyncParentDefaultPrs =
      (input.sync_parent_complex_default_prs ?? input.sync_parent_block_default_prs) !== false;

    return this.runWithUserMutationLock(
      ctx,
      ["exercises", "routines", "sessions", "cycle_goals", "rep_maxes"],
      async (tx) => {
        const sessions = await tx.readCollection<SessionWithBlocksRecord>(ctx, "sessions");

        let existingSessionIdx = -1;
        if (input.sessionId !== undefined) {
          existingSessionIdx = sessions.findIndex(
            (s) =>
              !isDeletedTombstone(s.session?.deletedAtMillis) &&
              s.session.id === input.sessionId
          );
          if (existingSessionIdx < 0) {
            throw new Error(`Active session #${input.sessionId} not found.`);
          }
        } else {
          const onDate = sessions
            .map((s, idx) => ({ s, idx }))
            .filter(
              ({ s }) =>
                !isDeletedTombstone(s.session?.deletedAtMillis) &&
                s.session.date === input.date
            );
          if (onDate.length === 0) {
            throw new Error(`No active workout session found on ${input.date}.`);
          }

          let narrowed = onDate;
          if (resolvedSessionComplexId !== undefined) {
            const bySessionBlockId = narrowed.filter(({ s }) =>
              (s.blocks ?? []).some((b) => b.block.id === resolvedSessionComplexId)
            );
            if (bySessionBlockId.length > 0) narrowed = bySessionBlockId;
            else {
              throw new Error(
                `No active session on ${input.date} contains session complex #${resolvedSessionComplexId}.`
              );
            }
          } else if (resolvedComplexId !== undefined) {
            const byBlockId = narrowed.filter(({ s }) =>
              (s.blocks ?? []).some((b) => b.block.routineId === resolvedComplexId)
            );
            if (byBlockId.length > 0) narrowed = byBlockId;
            else {
              throw new Error(
                `No active session on ${input.date} contains complex #${resolvedComplexId}.`
              );
            }
          } else if (resolvedComplexTitle) {
            const cleanTitle = resolvedComplexTitle.toLowerCase();
            const byTitle = narrowed.filter(
              ({ s }) =>
                s.session.title.trim().toLowerCase() === cleanTitle ||
                (s.blocks ?? []).some(
                  (b) =>
                    (b.block.subBlock ?? "").trim().toLowerCase() === cleanTitle ||
                    (b.block.routineName ?? "").trim().toLowerCase() === cleanTitle ||
                    b.block.name.trim().toLowerCase() === cleanTitle
                )
            );
            if (byTitle.length > 0) narrowed = byTitle;
            else {
              throw new Error(
                `No active session on ${input.date} matches complexTitle '${resolvedComplexTitle}'.`
              );
            }
          } else if (input.title?.trim()) {
            const cleanSessTitle = input.title.trim().toLowerCase();
            const bySessTitle = narrowed.filter(
              ({ s }) => s.session.title.trim().toLowerCase() === cleanSessTitle
            );
            if (bySessTitle.length > 0) narrowed = bySessTitle;
            else {
              throw new Error(
                `No active session on ${input.date} matches title '${input.title.trim()}'.`
              );
            }
          }

          if (narrowed.length > 1) {
            throw new Error(
              `Multiple sessions (${narrowed.length}) exist on ${input.date}; provide sessionId or complex_id/complex_title to disambiguate.`
            );
          }
          existingSessionIdx = narrowed[0].idx;
        }

        const existingSession = sessions[existingSessionIdx];
        const sessionId = existingSession.session.id;
        const existingBlocks = existingSession.blocks ?? [];
        const cleanBlockTitleInput = resolvedComplexTitle;

        let targetBlockIndices: number[] = [];
        if (resolvedSessionComplexId !== undefined) {
          targetBlockIndices = existingBlocks
            .map((b, idx) => (b.block.id === resolvedSessionComplexId ? idx : -1))
            .filter((idx) => idx >= 0);
        }
        if (targetBlockIndices.length === 0 && resolvedComplexId !== undefined) {
          targetBlockIndices = existingBlocks
            .map((b, idx) => (b.block.routineId === resolvedComplexId ? idx : -1))
            .filter((idx) => idx >= 0);
        }
        if (targetBlockIndices.length === 0 && cleanBlockTitleInput) {
          const titleLower = cleanBlockTitleInput.toLowerCase();
          targetBlockIndices = existingBlocks
            .map((b, idx) =>
              (b.block.subBlock ?? "").trim().toLowerCase() === titleLower ||
              (b.block.routineName ?? "").trim().toLowerCase() === titleLower ||
              b.block.name.trim().toLowerCase() === titleLower
                ? idx
                : -1
            )
            .filter((idx) => idx >= 0);
        }
        if (targetBlockIndices.length === 0 && existingBlocks.length > 0) {
          const first = existingBlocks[0];
          const firstRoutineId = first.block.routineId;
          const firstSubBlock = (first.block.subBlock ?? "").trim().toLowerCase();
          if (firstRoutineId != null && firstRoutineId > 0) {
            targetBlockIndices = existingBlocks
              .map((b, idx) => (b.block.routineId === firstRoutineId ? idx : -1))
              .filter((idx) => idx >= 0);
          } else if (firstSubBlock) {
            targetBlockIndices = existingBlocks
              .map((b, idx) =>
                (b.block.subBlock ?? "").trim().toLowerCase() === firstSubBlock ? idx : -1
              )
              .filter((idx) => idx >= 0);
          } else {
            targetBlockIndices = [0];
          }
        }

        const targetBlocks = targetBlockIndices.map((idx) => existingBlocks[idx]);
        const oldTargetBlockIds = new Set(targetBlocks.map((b) => b.block.id));
        const priorRoutineId =
          targetBlocks.find((b) => b.block.routineId != null)?.block.routineId ?? null;
        const priorBlockTitle =
          targetBlocks[0]?.block.subBlock?.trim() ||
          targetBlocks[0]?.block.routineName?.trim() ||
          existingSession.session.title.trim();

        const routines = await tx.readCollection<RoutineWithBlocksRecord>(ctx, "routines");
        const lookupRoutineId = resolvedComplexId ?? priorRoutineId;
        const lookupRoutineTitle = cleanBlockTitleInput || priorBlockTitle;
        let matchedRoutineIdx =
          lookupRoutineId !== null && lookupRoutineId !== undefined
            ? routines.findIndex(
                (r) =>
                  !isDeletedTombstone(r.routine?.deletedAtMillis) &&
                  r.routine.id === lookupRoutineId
              )
            : -1;
        if (matchedRoutineIdx < 0 && lookupRoutineTitle) {
          const lower = lookupRoutineTitle.toLowerCase();
          matchedRoutineIdx = routines.findIndex(
            (r) =>
              !isDeletedTombstone(r.routine?.deletedAtMillis) &&
              r.routine.name.trim().toLowerCase() === lower
          );
        }
        const matchedRoutine =
          matchedRoutineIdx >= 0 ? routines[matchedRoutineIdx] : undefined;

        const exReqs: Array<{
          name: string;
          metricType?: string;
          tracksRepMax?: boolean;
        }> = [];
        for (const t of input.exercise_targets ?? []) {
          if (t.exercise_name.trim()) {
            exReqs.push({
              name: t.exercise_name.trim(),
              metricType: inferLibraryMetricTypeFromRoutineTarget(t.target_metric),
            });
          }
        }
        for (const log of input.exercises ?? []) {
          if (log.exerciseName.trim()) {
            exReqs.push({ name: log.exerciseName.trim() });
          }
        }
        for (const pr of input.trackedPrs ?? []) {
          const prName = (pr.exerciseName ?? pr.movementName ?? "").trim();
          if (prName) {
            exReqs.push({
              name: prName,
              metricType: "WEIGHT",
              tracksRepMax: true,
            });
          }
        }
        const exerciseMap = await this.resolveExerciseMapInStore(tx, ctx, exReqs);
        const allExercises = await tx.readCollection<ExerciseRecord>(ctx, "exercises");
        const exercisesById = new Map<number, ExerciseRecord>(
          allExercises.map((e) => [Number(e.id), e])
        );
        const exercisesByLowerName = new Map<string, ExerciseRecord>(
          allExercises.map((e) => [e.name.trim().toLowerCase(), e])
        );

        const matchedAvailableBlock = matchedRoutine
          ? this.buildAvailableBlockRecord(
              matchedRoutine,
              exercisesById,
              exercisesByLowerName,
              [],
              false
            )
          : undefined;

        const targetCycle =
          input.cycleId !== undefined
            ? await this.resolveTargetCycleInStore(tx, ctx, input.cycleId)
            : await this.resolveTargetCycleInStore(
                tx,
                ctx,
                existingSession.session.cycleId ?? undefined,
                existingSession.session.cycleName
              );

        const now = Date.now();
        const updatedDate = input.date ?? existingSession.session.date;
        const resolvedBlockTitle =
          cleanBlockTitleInput ||
          priorBlockTitle ||
          matchedRoutine?.routine.name ||
          input.exercises?.[0]?.exerciseName.trim() ||
          "Training Complex";
        const updatedSessionTitle =
          input.title !== undefined
            ? input.title.trim() || resolvedBlockTitle
            : cleanBlockTitleInput &&
                existingSession.session.title.trim().toLowerCase() ===
                  priorBlockTitle.trim().toLowerCase()
              ? resolvedBlockTitle
              : existingSession.session.title;
        const updatedSessionNotes =
          input.notes !== undefined ? input.notes : existingSession.session.notes;
        const resolvedDescription =
          resolvedComplexNotes !== undefined
            ? resolvedComplexNotes
            : targetBlocks[0]?.block.description ??
              matchedAvailableBlock?.instructions ??
              "";

        let maxBlockId = sessions.reduce(
          (max, s) =>
            Math.max(
              max,
              ...(s.blocks ?? []).map((b) => Number(b.block?.id) || 0)
            ),
          0
        );
        let maxSetId = sessions.reduce(
          (max, s) =>
            Math.max(
              max,
              ...(s.blocks ?? []).flatMap((b) =>
                (b.sets ?? []).map((st) => Number(st.id) || 0)
              )
            ),
          0
        );

        const resolvedRoutineId = matchedRoutine?.routine.id ?? priorRoutineId;
        const resolvedRoutineName = matchedRoutine?.routine.name ?? resolvedBlockTitle;

        let activeTargetBlocks: SessionBlockEntryRecord[] = targetBlocks;
        let nextBlocks: SessionBlockEntryRecord[];
        const hasSharedWeights = (input.shared_set_weights_kg?.length ?? 0) > 0;

        if (input.exercises !== undefined || hasSharedWeights) {
          const replacementInputs: Array<{
            exerciseId?: number;
            exerciseName: string;
            weights?: number[];
            metric?: RoutineTargetMetric;
            sets?: Array<{
              reps?: number;
              weight_kg?: number;
              weight?: number;
              metric_value?: number;
              group_index?: number;
            }>;
            setCountForEmpty?: number;
          }> = hasSharedWeights
            ? (() => {
                const sharedWeights = input.shared_set_weights_kg!;
                const orderedNames =
                  targetBlocks.length > 0
                    ? targetBlocks
                        .map((tb) => (tb.block.mainExerciseName || tb.block.name).trim())
                        .filter(Boolean)
                    : (matchedAvailableBlock?.exercises.map((e) => e.name) ?? []);
                const fallbackNames =
                  orderedNames.length > 0
                    ? orderedNames
                    : (input.exercises ?? []).map((e) => e.exerciseName.trim()).filter(Boolean);
                const targetsByLowerName = this.buildTargetSlotMapFromInputs(
                  input.exercise_targets,
                  sharedWeights.length
                );

                let priorMasks: Map<string, Set<number>> | undefined;
                if ((input.exercise_targets?.length ?? 0) === 0 && targetBlocks.length > 0) {
                  const firstLen = (targetBlocks[0]?.sets ?? []).length;
                  const anyTargetSkip = targetBlocks.some(
                    (tb) =>
                      (tb.sets ?? []).length === 0 ||
                      (tb.sets ?? []).some((st) => Number(st.groupIndex) > 0) ||
                      (tb.sets ?? []).length !== firstLen
                  );
                  if (anyTargetSkip) {
                    priorMasks = new Map<string, Set<number>>();
                    for (const tb of targetBlocks) {
                      const exNameLower = (tb.block.mainExerciseName || tb.block.name)
                        .trim()
                        .toLowerCase();
                      if (!exNameLower) continue;
                      const tbSets = tb.sets ?? [];
                      const hasExplicitG = tbSets.some((st) => Number(st.groupIndex) > 0);
                      const mask = new Set<number>();
                      tbSets.forEach((st, idx) => {
                        const rNum = hasExplicitG
                          ? Number(st.groupIndex) > 0
                            ? Number(st.groupIndex)
                            : idx + 1
                          : idx + 1;
                        mask.add(rNum);
                      });
                      priorMasks.set(exNameLower, mask);
                    }
                  }
                }

                const explicitMap = new Map<
                  string,
                  {
                    exerciseId?: number;
                    exerciseName: string;
                    weights?: number[];
                    metric?: RoutineTargetMetric;
                    sets?: Array<{
                      reps?: number;
                      weight_kg?: number;
                      weight?: number;
                      metric_value?: number;
                      group_index?: number;
                    }>;
                    setCountForEmpty?: number;
                  }
                >();
                for (const log of input.exercises ?? []) {
                  const clean = log.exerciseName.trim();
                  if (clean) explicitMap.set(clean.toLowerCase(), log);
                }

                return this.expandSharedComplexSetWeights({
                  sharedSetWeightsKg: sharedWeights,
                  orderedExerciseNames: fallbackNames,
                  exercisesByLowerName,
                  targetsByLowerName,
                  priorActiveRoundMasksByLowerName: priorMasks,
                  explicitOverridesByLowerName: explicitMap,
                });
              })()
            : (input.exercises ?? []);

          const replacementBlocks: SessionBlockEntryRecord[] = replacementInputs.map(
            (log, bIdx) => {
              const ex =
                (log.exerciseId !== undefined
                  ? exercisesById.get(log.exerciseId)
                  : undefined) ??
                exerciseMap.get(log.exerciseName.trim().toLowerCase()) ??
                exercisesByLowerName.get(log.exerciseName.trim().toLowerCase()) ??
                null;
              const priorMatchingBlock = targetBlocks.find(
                (tb) =>
                  (ex != null && tb.block.mainExerciseId === ex.id) ||
                  tb.block.name.trim().toLowerCase() ===
                    log.exerciseName.trim().toLowerCase()
              );
              const blockId = ++maxBlockId;
              const { sets, notes: blockNotes } = this.buildLoggedSessionSetsAndNotes({
                blockId,
                nextSetIdFn: () => ++maxSetId,
                metric: log.metric,
                sets: log.sets,
                weights: log.weights,
                setCountForEmpty: log.setCountForEmpty ?? 1,
                exerciseMetricType: ex?.metricType,
                priorNotes: priorMatchingBlock?.block.notes ?? "",
              });

              return {
                block: {
                  id: blockId,
                  sessionId,
                  position: bIdx,
                  name: ex?.name ?? log.exerciseName.trim(),
                  kind: priorMatchingBlock?.block.kind ?? "STRENGTH",
                  format: priorMatchingBlock?.block.format ?? "",
                  scheme: priorMatchingBlock?.block.scheme ?? "",
                  mainExerciseId: ex ? ex.id : null,
                  mainExerciseName: ex ? ex.name : null,
                  routineId: resolvedRoutineId,
                  routineName: resolvedRoutineName,
                  description: resolvedDescription,
                  resultText: priorMatchingBlock?.block.resultText ?? "",
                  resultValue: priorMatchingBlock?.block.resultValue ?? null,
                  notes: blockNotes,
                  section: priorMatchingBlock?.block.section ?? "",
                  subBlock: resolvedBlockTitle,
                  exerciseIdsCsv: ex ? String(ex.id) : "",
                  exerciseNamesCsv: ex ? ex.name : log.exerciseName.trim(),
                  isCompleted: true,
                },
                sets,
              };
            }
          );

          const insertAt =
            targetBlockIndices.length > 0 ? targetBlockIndices[0] : existingBlocks.length;
          const targetIdxSet = new Set(targetBlockIndices);
          const rebuilt: SessionBlockEntryRecord[] = [];
          let inserted = false;
          for (let i = 0; i < existingBlocks.length; i++) {
            if (i === insertAt) {
              rebuilt.push(...replacementBlocks);
              inserted = true;
            }
            if (!targetIdxSet.has(i)) {
              rebuilt.push(existingBlocks[i]);
            }
          }
          if (!inserted) {
            rebuilt.push(...replacementBlocks);
          }
          nextBlocks = rebuilt.map((entry, idx) => ({
            ...entry,
            block: { ...entry.block, position: idx },
          }));
          activeTargetBlocks = replacementBlocks;
        } else {
          const targetIdxSet = new Set(targetBlockIndices);
          nextBlocks = existingBlocks.map((entry, idx) =>
            targetIdxSet.has(idx)
              ? {
                  ...entry,
                  block: {
                    ...entry.block,
                    routineId: resolvedRoutineId,
                    routineName: resolvedRoutineName,
                    subBlock: resolvedBlockTitle,
                    description: resolvedDescription,
                  },
                }
              : entry
          );
          activeTargetBlocks = targetBlockIndices.map((idx) => nextBlocks[idx]);
        }

        const updatedSessionRecord: SessionWithBlocksRecord = {
          session: {
            ...existingSession.session,
            id: sessionId,
            cycleId: targetCycle.cycleId,
            cycleName: targetCycle.cycleName,
            isActiveCycle: targetCycle.isActive,
            date: updatedDate,
            title: updatedSessionTitle,
            notes: updatedSessionNotes,
            updatedAtMillis: now,
            deletedAtMillis: null,
          },
          blocks: nextBlocks,
        };
        sessions[existingSessionIdx] = updatedSessionRecord;
        await tx.writeCollection(ctx, "sessions", sessions);

        const maxSetWeightByExerciseId = new Map<number, number>();
        for (const entry of activeTargetBlocks) {
          const exId = entry.block.mainExerciseId;
          if (!exId) continue;
          let maxW = maxSetWeightByExerciseId.get(exId) ?? 0;
          for (const st of entry.sets ?? []) {
            if (!st.isWarmup && !st.isFailed && Number(st.weight) > maxW) {
              maxW = Number(st.weight);
            }
          }
          if (maxW > 0) {
            maxSetWeightByExerciseId.set(exId, maxW);
          }
        }

        const repMaxes = await tx.readCollection<RepMaxRecord>(ctx, "rep_maxes");
        let maxRepMaxId = repMaxes.reduce(
          (max, rm) => Math.max(max, Number(rm.id) || 0),
          0
        );
        const reconciledPrs: RepMaxRecord[] = [];
        const tombstonedRepMaxIds: number[] = [];
        let repMaxMutated = false;

        const shouldReconcilePrs =
          input.reconcilePrs !== false &&
          (input.trackedPrs !== undefined ||
            input.exercises !== undefined ||
            hasSharedWeights ||
            input.date !== undefined ||
            input.cycleId !== undefined);

        const resolvedTrackedPrInputs: Array<{
          ex: ExerciseRecord;
          rmReps: number;
          weight: number;
          remove: boolean;
        }> = [];

        if (input.trackedPrs !== undefined) {
          for (const pr of input.trackedPrs) {
            const rawName = (pr.exerciseName ?? pr.movementName ?? "").trim();
            const ex =
              (pr.exerciseId !== undefined
                ? exercisesById.get(pr.exerciseId)
                : undefined) ??
              (rawName ? exerciseMap.get(rawName.toLowerCase()) : undefined) ??
              (rawName ? exercisesByLowerName.get(rawName.toLowerCase()) : undefined);
            if (!ex) {
              throw new Error(
                `Failed to resolve PR movement '${rawName || String(pr.exerciseId ?? "")}'.`
              );
            }
            const resolvedWeight =
              pr.weight !== undefined
                ? Number(pr.weight)
                : (maxSetWeightByExerciseId.get(ex.id) ?? 0);
            resolvedTrackedPrInputs.push({
              ex,
              rmReps: pr.rmReps,
              weight: resolvedWeight,
              remove: pr.remove === true,
            });
          }
        }

        if (shouldReconcilePrs) {
          const priorBlockTitleLower = priorBlockTitle.trim().toLowerCase();
          const priorSessionIndices: number[] = [];
          for (let i = 0; i < repMaxes.length; i++) {
            const rm = repMaxes[i];
            if (isDeletedTombstone(rm.deletedAtMillis)) continue;
            if (rm.sessionId !== sessionId) continue;
            const belongsToTargetBlock =
              oldTargetBlockIds.size === 0 ||
              rm.blockId == null ||
              oldTargetBlockIds.has(rm.blockId) ||
              (priorBlockTitleLower.length > 0 &&
                (rm.sourceBlockTitle ?? "").trim().toLowerCase() === priorBlockTitleLower);
            if (belongsToTargetBlock) {
              priorSessionIndices.push(i);
            }
          }

          if (input.trackedPrs !== undefined) {
            const explicitRemovals = new Set<string>();
            for (const item of resolvedTrackedPrInputs) {
              if (item.remove) {
                explicitRemovals.add(`${item.ex.id}#${item.rmReps}`);
              }
            }
            const desiredActivePrs = resolvedTrackedPrInputs.filter(
              (item) => !item.remove && item.weight > 0
            );
            const desiredKeys = new Set<string>(
              desiredActivePrs.map(
                (item) =>
                  `${item.ex.id}#${item.rmReps}#${Math.round(item.weight * 100)}#${updatedDate}`
              )
            );

            for (const idx of priorSessionIndices) {
              const priorRm = repMaxes[idx];
              const rmKey = `${priorRm.exerciseId}#${priorRm.reps}#${Math.round(Number(priorRm.weight) * 100)}#${priorRm.date}`;
              const slotKey = `${priorRm.exerciseId}#${priorRm.reps}`;
              if (explicitRemovals.has(slotKey) || !desiredKeys.has(rmKey)) {
                repMaxes[idx] = {
                  ...priorRm,
                  updatedAtMillis: now,
                  deletedAtMillis: now,
                };
                tombstonedRepMaxIds.push(priorRm.id);
                repMaxMutated = true;
              }
            }

            for (const item of desiredActivePrs) {
              const matchingBlock =
                activeTargetBlocks.find((b) => b.block.mainExerciseId === item.ex.id) ??
                activeTargetBlocks[0] ??
                null;
              const targetBlockId = matchingBlock ? matchingBlock.block.id : null;
              const existingRmIdx = repMaxes.findIndex(
                (rm) =>
                  rm.exerciseId === item.ex.id &&
                  rm.reps === item.rmReps &&
                  Math.round(Number(rm.weight) * 100) === Math.round(item.weight * 100) &&
                  rm.date === updatedDate
              );
              if (existingRmIdx >= 0) {
                const updatedRm: RepMaxRecord = {
                  ...repMaxes[existingRmIdx],
                  exerciseName: item.ex.name,
                  cycleId: targetCycle.cycleId,
                  cycleName: targetCycle.cycleName,
                  isActiveCycle: targetCycle.isActive,
                  sessionId,
                  blockId: targetBlockId,
                  sessionTitle: updatedSessionTitle,
                  sourceBlockTitle: resolvedBlockTitle,
                  updatedAtMillis: now,
                  deletedAtMillis: null,
                };
                repMaxes[existingRmIdx] = updatedRm;
                reconciledPrs.push(updatedRm);
                repMaxMutated = true;
              } else {
                const createdRm: RepMaxRecord = {
                  id: ++maxRepMaxId,
                  exerciseId: item.ex.id,
                  exerciseName: item.ex.name,
                  reps: item.rmReps,
                  weight: item.weight,
                  date: updatedDate,
                  cycleId: targetCycle.cycleId,
                  cycleName: targetCycle.cycleName,
                  isActiveCycle: targetCycle.isActive,
                  sessionId,
                  blockId: targetBlockId,
                  sessionTitle: updatedSessionTitle,
                  sourceBlockTitle: resolvedBlockTitle,
                  updatedAtMillis: now,
                  deletedAtMillis: null,
                };
                repMaxes.push(createdRm);
                reconciledPrs.push(createdRm);
                repMaxMutated = true;
              }
            }
          } else {
            // Exercises/date/cycleId changed without explicit trackedPrs list: re-link or re-date prior session PRs.
            for (const idx of priorSessionIndices) {
              const priorRm = repMaxes[idx];
              const matchingBlock =
                activeTargetBlocks.find((b) => b.block.mainExerciseId === priorRm.exerciseId) ??
                activeTargetBlocks[0] ??
                null;
              const targetBlockId = matchingBlock ? matchingBlock.block.id : null;

              if (updatedDate !== priorRm.date) {
                repMaxes[idx] = {
                  ...priorRm,
                  updatedAtMillis: now,
                  deletedAtMillis: now,
                };
                tombstonedRepMaxIds.push(priorRm.id);
                repMaxMutated = true;

                const exName =
                  exercisesById.get(priorRm.exerciseId)?.name ??
                  priorRm.exerciseName ??
                  `Exercise #${priorRm.exerciseId}`;
                const naturalIdx = repMaxes.findIndex(
                  (rm) =>
                    rm.exerciseId === priorRm.exerciseId &&
                    rm.reps === priorRm.reps &&
                    Math.round(Number(rm.weight) * 100) ===
                      Math.round(Number(priorRm.weight) * 100) &&
                    rm.date === updatedDate
                );
                if (naturalIdx >= 0) {
                  const revived: RepMaxRecord = {
                    ...repMaxes[naturalIdx],
                    exerciseName: exName,
                    cycleId: targetCycle.cycleId,
                    cycleName: targetCycle.cycleName,
                    isActiveCycle: targetCycle.isActive,
                    sessionId,
                    blockId: targetBlockId,
                    sessionTitle: updatedSessionTitle,
                    sourceBlockTitle: resolvedBlockTitle,
                    updatedAtMillis: now,
                    deletedAtMillis: null,
                  };
                  repMaxes[naturalIdx] = revived;
                  reconciledPrs.push(revived);
                } else {
                  const createdRm: RepMaxRecord = {
                    id: ++maxRepMaxId,
                    exerciseId: priorRm.exerciseId,
                    exerciseName: exName,
                    reps: priorRm.reps,
                    weight: priorRm.weight,
                    date: updatedDate,
                    cycleId: targetCycle.cycleId,
                    cycleName: targetCycle.cycleName,
                    isActiveCycle: targetCycle.isActive,
                    sessionId,
                    blockId: targetBlockId,
                    sessionTitle: updatedSessionTitle,
                    sourceBlockTitle: resolvedBlockTitle,
                    updatedAtMillis: now,
                    deletedAtMillis: null,
                  };
                  repMaxes.push(createdRm);
                  reconciledPrs.push(createdRm);
                }
              } else {
                const updatedRm: RepMaxRecord = {
                  ...priorRm,
                  exerciseName:
                    exercisesById.get(priorRm.exerciseId)?.name ?? priorRm.exerciseName,
                  cycleId: targetCycle.cycleId,
                  cycleName: targetCycle.cycleName,
                  isActiveCycle: targetCycle.isActive,
                  blockId: targetBlockId,
                  sessionTitle: updatedSessionTitle,
                  sourceBlockTitle: resolvedBlockTitle,
                  updatedAtMillis: now,
                  deletedAtMillis: null,
                };
                repMaxes[idx] = updatedRm;
                reconciledPrs.push(updatedRm);
                repMaxMutated = true;
              }
            }
          }

          if (repMaxMutated) {
            await tx.writeCollection(ctx, "rep_maxes", repMaxes);
          }
        }

        let updatedTrackedPrs: TrackedPrSlotRecord[] | null = null;
        const activeTemplatePrSpecs = resolvedTrackedPrInputs
          .filter((item) => !item.remove && item.rmReps >= 1 && item.rmReps <= 20)
          .map((item) => ({
            movementName: item.ex.name,
            rmReps: item.rmReps,
          }));
        if (
          shouldSyncParentDefaultPrs &&
          matchedRoutine &&
          matchedRoutineIdx >= 0 &&
          activeTemplatePrSpecs.length > 0
        ) {
          updatedTrackedPrs = await this.mergeAvailableBlockTrackedPrsInTx(
            tx,
            ctx,
            routines,
            matchedRoutineIdx,
            matchedAvailableBlock,
            resolvedDescription,
            activeTemplatePrSpecs,
            exercisesByLowerName,
            now
          );
        }

        return {
          session: updatedSessionRecord,
          reconciledPrs,
          tombstonedRepMaxIds,
          updatedTrackedPrs,
        };
      }
    );
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
    const enriched = repMaxes
      .filter((rm) => !isDeletedTombstone(rm.deletedAtMillis))
      .map((rm) => ({
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
          const existingIdx = repMaxes.findIndex(
            (rm) =>
              rm.exerciseId === ex.id &&
              rm.reps === input.reps &&
              Math.round(Number(rm.weight) * 100) === Math.round(input.weight * 100) &&
              rm.date === date
          );
          if (existingIdx >= 0) {
            const existing = repMaxes[existingIdx];
            if (isDeletedTombstone(existing.deletedAtMillis)) {
              const revived: RepMaxRecord = {
                ...existing,
                exerciseName: ex.name,
                updatedAtMillis: now,
                deletedAtMillis: null,
              };
              repMaxes[existingIdx] = revived;
              mutated = true;
              results.push(revived);
            } else {
              results.push({ ...existing, exerciseName: ex.name });
            }
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
            deletedAtMillis: null,
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

  // ==========================================================================
  // Composite Daily Routines (`defaultFormat: 'DAILY_ROUTINE'`, SPEC-025)
  // ==========================================================================

  private round2dp(value: number): number {
    return Math.round(value * 100) / 100;
  }

  private parseAndValidateTargetSlot(
    raw: {
      target_metric?: RoutineTargetMetric;
      target_mode?: RoutineTargetMode;
      fixed_value?: number;
      per_set_values?: number[];
    },
    setsCount: number,
    label: string
  ): RoutineExerciseTargetSlot {
    const normalizedSets = Math.max(1, Math.min(100, Math.trunc(setsCount)));
    const metric: RoutineTargetMetric = raw.target_metric ?? "REPS";
    const mode: RoutineTargetMode =
      raw.target_mode ??
      (raw.per_set_values && raw.per_set_values.length > 0 ? "PER_SET" : "FIXED");

    if (mode === "FIXED") {
      const fixedValue =
        raw.fixed_value !== undefined
          ? Math.trunc(raw.fixed_value)
          : defaultFixedForRoutineMetric(metric);
      if (fixedValue <= 0) {
        throw new Error(
          `Invalid target for '${label}': fixed_value must be greater than 0.`
        );
      }
      return {
        metric,
        mode: "FIXED",
        fixedValue,
        perSetValues: Array.from({ length: normalizedSets }, () => fixedValue),
      };
    }

    const fallback =
      raw.fixed_value !== undefined && Math.trunc(raw.fixed_value) > 0
        ? Math.trunc(raw.fixed_value)
        : defaultFixedForRoutineMetric(metric);
    const explicitPerSet = raw.per_set_values;
    if (explicitPerSet !== undefined) {
      const cleanRaw = explicitPerSet.map((v) => Math.max(0, Math.trunc(v)));
      const normalized = resizeRoutinePerSetValues(
        cleanRaw.length > 0 ? cleanRaw : [0],
        0,
        normalizedSets
      );
      if (!normalized.some((v) => v > 0)) {
        throw new Error(
          `Invalid target for '${label}': at least one per-set value must be greater than 0.`
        );
      }
      return {
        metric,
        mode: "PER_SET",
        fixedValue: normalized.find((v) => v > 0) ?? fallback,
        perSetValues: normalized,
      };
    }

    return {
      metric,
      mode: "PER_SET",
      fixedValue: fallback,
      perSetValues: Array.from({ length: normalizedSets }, () => fallback),
    };
  }

  private buildLoggedSessionSetsAndNotes(params: {
    blockId: number;
    nextSetIdFn: () => number;
    metric?: RoutineTargetMetric;
    sets?: Array<{
      reps?: number;
      weight_kg?: number;
      weight?: number;
      metric_value?: number;
      group_index?: number;
    }>;
    weights?: number[];
    setCountForEmpty?: number;
    exerciseMetricType?: string;
    priorNotes?: string;
  }): { sets: SessionSetRecord[]; notes: string } {
    const cleanPrior = (params.priorNotes ?? "")
      .replace(/\[log_metric=[^\]]*\](?:\r?\n)?/gi, "")
      .trim();
    const explicitSets = params.sets ?? [];

    if (explicitSets.length > 0) {
      const mappedSets: SessionSetRecord[] = explicitSets.map((st, sIdx) => {
        const repsVal =
          st.reps !== undefined && Number.isFinite(Number(st.reps)) && Number(st.reps) >= 1
            ? Math.trunc(Number(st.reps))
            : 1;
        const rawWeight = st.weight_kg ?? st.weight ?? 0;
        const weightVal =
          Number.isFinite(Number(rawWeight)) && Number(rawWeight) > 0
            ? Number(rawWeight)
            : 0.0;
        const metricVal =
          st.metric_value !== undefined &&
          Number.isFinite(Number(st.metric_value)) &&
          Number(st.metric_value) > 0
            ? Number(st.metric_value)
            : 0.0;
        const groupIdxVal =
          st.group_index !== undefined &&
          Number.isFinite(Number(st.group_index)) &&
          Number(st.group_index) > 0
            ? Math.trunc(Number(st.group_index))
            : 0;
        return {
          id: params.nextSetIdFn(),
          blockId: params.blockId,
          position: sIdx,
          groupIndex: groupIdxVal,
          reps: repsVal,
          weight: weightVal,
          metricValue: metricVal,
          isWarmup: false,
          isFailed: false,
          notes: "",
        };
      });

      const hasPositiveMetricValue = mappedSets.some((s) => s.metricValue > 0);
      const hasBodyweightReps = mappedSets.some(
        (s, idx) =>
          s.weight <= 0 &&
          s.metricValue <= 0 &&
          (explicitSets[idx]?.reps !== undefined || s.reps > 1)
      );
      let metricToEncode: RoutineTargetMetric | null = params.metric ?? null;
      if (!metricToEncode && hasPositiveMetricValue) {
        const inferred = inferRoutineTargetMetricFromLibraryMetric(params.exerciseMetricType);
        metricToEncode = inferred === "REPS" ? "CALORIES" : inferred;
      } else if (!metricToEncode && hasBodyweightReps) {
        metricToEncode = "REPS";
      }

      const notes = metricToEncode
        ? encodeNotesWithLogMetric(cleanPrior, metricToEncode)
        : cleanPrior;
      return { sets: mappedSets, notes };
    }

    const weightsList = params.weights ?? [];
      const sets: SessionSetRecord[] =
        weightsList.length > 0
          ? weightsList.map((w, sIdx) => ({
              id: params.nextSetIdFn(),
              blockId: params.blockId,
              position: sIdx,
              groupIndex: 0,
              reps: 1,
              weight: Number(w) > 0 ? Number(w) : 0.0,
              metricValue: 0.0,
              isWarmup: false,
              isFailed: false,
              notes: "",
            }))
          : Array.from(
              { length: Math.max(0, Math.trunc(params.setCountForEmpty ?? 1)) },
              (_, sIdx) => ({
              id: params.nextSetIdFn(),
              blockId: params.blockId,
              position: sIdx,
              groupIndex: 0,
              reps: 1,
              weight: 0.0,
              metricValue: 0.0,
              isWarmup: false,
              isFailed: false,
              notes: "",
            })
          );

    const notes = params.metric
      ? encodeNotesWithLogMetric(cleanPrior, params.metric)
      : cleanPrior;
    return { sets, notes };
  }

  private buildDailyRoutineRecord(
    rwb: RoutineWithBlocksRecord,
    availableBlocksById: Map<number, AvailableBlockRecord>,
    availableBlocksByLowerTitle: Map<string, AvailableBlockRecord>,
    exercisesById: Map<number, ExerciseRecord>,
    exercisesByLowerName: Map<string, ExerciseRecord>,
    sortedSessions: SessionWithBlocksRecord[]
  ): DailyRoutineRecord {
    const sortedBlocks = [...(rwb.blocks ?? [])].sort((a, b) => a.position - b.position);
    const items: DailyRoutineItemRecord[] = [];
    let missingBlockCount = 0;

    for (let idx = 0; idx < sortedBlocks.length; idx++) {
      const rb = sortedBlocks[idx];
      const decoded = decodeDailyRoutineItemHeader(rb.notes);
      const effectiveType: "BLOCK" | "EXERCISE" =
        decoded?.itemType ??
        ((rb.format ?? "").trim().toUpperCase() === DAILY_ROUTINE_BLOCK_ITEM_FORMAT
          ? "BLOCK"
          : "EXERCISE");
      const rbSetsCount = Math.max(1, Math.trunc(rb.setsCount || 1));
      const headerSlots = decodeRoutineHeaderSlots(rb.notes, rbSetsCount);

      if (effectiveType === "BLOCK") {
        const refId = decoded?.refId ?? 0;
        const refTitle = (decoded?.refName || rb.name || "").trim();
        const byId = refId > 0 ? availableBlocksById.get(refId) : undefined;
        const resolvedBlock =
          byId && (!refTitle || byId.title.trim().toLowerCase() === refTitle.toLowerCase())
            ? byId
            : (refTitle ? availableBlocksByLowerTitle.get(refTitle.toLowerCase()) : undefined) ??
              byId;

        if (resolvedBlock) {
          const csvIds = (rb.exerciseIdsCsv ?? "")
            .split(",")
            .map((s) => Number.parseInt(s.trim(), 10))
            .filter((n) => Number.isFinite(n) && n > 0);
          const exerciseTargets: DailyRoutineBlockExerciseTargetRecord[] = [];
          if (headerSlots && headerSlots.length > 0) {
            const alignedIds =
              csvIds.length > 0 ? csvIds : resolvedBlock.exercises.map((e) => e.id);
            headerSlots.forEach((slot, slotIdx) => {
              const exId = alignedIds[slotIdx];
              const abEx =
                (exId !== undefined
                  ? resolvedBlock.exercises.find((e) => e.id === exId)
                  : undefined) ?? resolvedBlock.exercises[slotIdx];
              const finalId = exId && exId > 0 ? exId : abEx?.id ?? null;
              const finalName =
                abEx?.name ??
                (finalId ? exercisesById.get(finalId)?.name : undefined) ??
                `Exercise ${slotIdx + 1}`;
              exerciseTargets.push({
                exerciseId: finalId,
                exerciseName: finalName,
                target: slot,
              });
            });
          }
          items.push({
            itemType: "COMPLEX",
            position: idx,
            complexId: resolvedBlock.id,
            complexTitle: resolvedBlock.title,
            blockId: resolvedBlock.id,
            blockTitle: resolvedBlock.title,
            instructions: resolvedBlock.instructions,
            exerciseId: null,
            exerciseName: null,
            note: "",
            defaultTrackedPrs: resolvedBlock.trackedPrs,
            setsCount: exerciseTargets.length > 0 ? rbSetsCount : null,
            targetSpec: null,
            exerciseTargets,
            missingBlock: false,
          });
        } else {
          missingBlockCount += 1;
        }
      } else {
        const csvIds = (rb.exerciseIdsCsv ?? "")
          .split(",")
          .map((s) => Number.parseInt(s.trim(), 10))
          .filter((n) => Number.isFinite(n) && n > 0);
        const refId = (decoded?.refId ?? 0) > 0 ? decoded!.refId : (csvIds[0] ?? 0);
        const refName = (decoded?.refName || rb.name || "").trim();
        const byId = refId > 0 ? exercisesById.get(refId) : undefined;
        const resolvedEx =
          byId && (!refName || byId.name.trim().toLowerCase() === refName.toLowerCase())
            ? byId
            : (refName ? exercisesByLowerName.get(refName.toLowerCase()) : undefined) ?? byId;
        const cleanNote = extractCleanRoutineNotes(rb.notes);
        const decodedPrs = decodeAvailableBlockTrackedPrs(rb.notes).map((slot) => {
          const matched = exercisesByLowerName.get(slot.movementName.trim().toLowerCase());
          return {
            movementId: matched?.id ?? resolvedEx?.id ?? 0,
            movementName: matched?.name ?? slot.movementName,
            rmReps: slot.rmReps,
          };
        });
        const targetSpec = headerSlots && headerSlots.length > 0 ? headerSlots[0] : null;
        items.push({
          itemType: "EXERCISE",
          position: idx,
          complexId: null,
          complexTitle: null,
          blockId: null,
          blockTitle: null,
          instructions: null,
          exerciseId: resolvedEx?.id ?? (refId > 0 ? refId : null),
          exerciseName: resolvedEx?.name ?? (refName || "Exercise"),
          note: cleanNote,
          defaultTrackedPrs: decodedPrs,
          setsCount: targetSpec ? rbSetsCount : null,
          targetSpec,
          exerciseTargets: [],
          missingBlock: false,
        });
      }
    }

    const routineTitleLower = rwb.routine.name.trim().toLowerCase();
    const lastSession = sortedSessions.find((sw) => {
      const titleMatch =
        routineTitleLower.length > 0 &&
        (sw.session.title ?? "").trim().toLowerCase() === routineTitleLower;
      const idMatch = (sw.blocks ?? []).some((b) => b.block.routineId === rwb.routine.id);
      return titleMatch || idMatch;
    });

    const dedupPrSpecs = normalizeTrackedPrSlotsSpec(
      items.flatMap((it) =>
        it.defaultTrackedPrs.map((s) => ({
          movementName: s.movementName,
          rmReps: s.rmReps,
        }))
      )
    );

    const complexCount = items.filter((i) => i.itemType === "COMPLEX").length;
    return {
      id: rwb.routine.id,
      title: rwb.routine.name,
      notes: extractCleanRoutineNotes(rwb.routine.description),
      complexCount,
      missingComplexCount: missingBlockCount,
      blockCount: complexCount,
      exerciseCount: items.filter((i) => i.itemType === "EXERCISE").length,
      missingBlockCount,
      totalTrackedPrCount: dedupPrSpecs.length,
      lastSessionDate: lastSession?.session.date ?? null,
      updatedAtMillis: rwb.routine.updatedAtMillis ?? null,
      items,
    };
  }

  async listDailyRoutines(
    ctx: UserContext,
    filter?: { query?: string; limit?: number }
  ): Promise<DailyRoutineRecord[]> {
    const [routines, exercises, sessions] = await Promise.all([
      this.backing.readCollection<RoutineWithBlocksRecord>(ctx, "routines"),
      this.backing.readCollection<ExerciseRecord>(ctx, "exercises"),
      this.backing.readCollection<SessionWithBlocksRecord>(ctx, "sessions"),
    ]);

    const exercisesById = new Map<number, ExerciseRecord>(
      exercises.map((e) => [Number(e.id), e])
    );
    const exercisesByLowerName = new Map<string, ExerciseRecord>(
      exercises.map((e) => [e.name.trim().toLowerCase(), e])
    );
    const activeSessions = sessions
      .filter((s) => !isDeletedTombstone(s.session?.deletedAtMillis))
      .sort((a, b) => {
        const cmp = (b.session?.date ?? "").localeCompare(a.session?.date ?? "");
        return cmp !== 0 ? cmp : (Number(b.session?.id) || 0) - (Number(a.session?.id) || 0);
      });

    const activeAvailableBlocks = routines
      .filter(
        (r) =>
          !isDeletedTombstone(r.routine?.deletedAtMillis) &&
          !isDailyRoutineFormat(r.routine?.defaultFormat)
      )
      .map((r) =>
        this.buildAvailableBlockRecord(
          r,
          exercisesById,
          exercisesByLowerName,
          [],
          false
        )
      );
    const abById = new Map<number, AvailableBlockRecord>(
      activeAvailableBlocks.map((b) => [b.id, b])
    );
    const abByLowerTitle = new Map<string, AvailableBlockRecord>(
      activeAvailableBlocks.map((b) => [b.title.trim().toLowerCase(), b])
    );

    let list = routines
      .filter(
        (r) =>
          !isDeletedTombstone(r.routine?.deletedAtMillis) &&
          isDailyRoutineFormat(r.routine?.defaultFormat)
      )
      .map((r) =>
        this.buildDailyRoutineRecord(
          r,
          abById,
          abByLowerTitle,
          exercisesById,
          exercisesByLowerName,
          activeSessions
        )
      );

    const q = filter?.query?.trim().toLowerCase();
    if (q) {
      list = list.filter(
        (dr) =>
          dr.title.toLowerCase().includes(q) ||
          dr.notes.toLowerCase().includes(q) ||
          dr.items.some(
            (it) =>
              (it.complexTitle ?? it.blockTitle ?? "").toLowerCase().includes(q) ||
              (it.exerciseName ?? "").toLowerCase().includes(q) ||
              it.note.toLowerCase().includes(q)
          )
      );
    }

    if (filter?.limit !== undefined && filter.limit > 0) {
      list = list.slice(0, filter.limit);
    }
    return list;
  }

  private buildDailyRoutineStoredBlocksInTx(
    routineId: number,
    items: Array<{
      itemType: "BLOCK" | "EXERCISE";
      resolvedBlock?: RoutineWithBlocksRecord;
      resolvedExercise?: ExerciseRecord;
      note?: string;
      defaultTrackedPrs?: TrackedPrSlotSpec[];
      targetSetsCount?: number;
      targetSpec?: RoutineExerciseTargetSlot | null;
      blockExerciseTargets?: Array<{
        exerciseId: number;
        exerciseName: string;
        slot: RoutineExerciseTargetSlot;
      }>;
    }>,
    nextBlockIdFn: () => number
  ): RoutineBlockRecord[] {
    return items.map((item, idx) => {
      if (item.itemType === "BLOCK") {
        const blk = item.resolvedBlock!;
        const blkExIds = Array.from(
          new Set(
            (blk.blocks ?? [])
              .flatMap((b) =>
                (b.exerciseIdsCsv ?? "")
                  .split(",")
                  .map((s) => Number.parseInt(s.trim(), 10))
                  .filter((n) => Number.isFinite(n) && n > 0)
              )
          )
        );
        const drHeader = encodeDailyRoutineBlockHeader(blk.routine.id, blk.routine.name);
        const blockTargets = item.blockExerciseTargets ?? [];
        if (blockTargets.length > 0) {
          const setsCount = Math.max(1, Math.trunc(item.targetSetsCount ?? 4));
          const slots = blockTargets.map((t) => t.slot);
          const rbHeader = encodeRoutineBlockTargets("", slots, setsCount);
          const summary = formatRoutineBlockTargetSummary(slots, setsCount);
          const targetCsv =
            blockTargets
              .map((t) => t.exerciseId)
              .filter((id) => id > 0)
              .join(",") || blkExIds.join(",");
          return {
            id: nextBlockIdFn(),
            routineId,
            position: idx,
            name: blk.routine.name,
            kind: "COMPLEX",
            format: DAILY_ROUTINE_BLOCK_ITEM_FORMAT,
            setsCount,
            targetRepsScheme: summary,
            exerciseIdsCsv: targetCsv,
            exerciseNamesCsv: "",
            notes: rbHeader ? `${rbHeader}\n${drHeader}` : drHeader,
            section: "",
            subBlock: blk.routine.name,
          };
        }
        return {
          id: nextBlockIdFn(),
          routineId,
          position: idx,
          name: blk.routine.name,
          kind: "COMPLEX",
          format: DAILY_ROUTINE_BLOCK_ITEM_FORMAT,
          setsCount: 1,
          targetRepsScheme: "",
          exerciseIdsCsv: blkExIds.join(","),
          exerciseNamesCsv: "",
          notes: drHeader,
          section: "",
          subBlock: blk.routine.name,
        };
      }

      const ex = item.resolvedExercise!;
      const drHeader = encodeDailyRoutineExerciseHeader(ex.id, ex.name);
      const noteAndPrs = encodeAvailableBlockTrackedPrs(
        (item.note ?? "").trim(),
        item.defaultTrackedPrs ?? []
      );
      if (item.targetSpec) {
        const setsCount = Math.max(1, Math.trunc(item.targetSetsCount ?? 4));
        const rbHeader = encodeRoutineBlockTargets("", [item.targetSpec], setsCount);
        const summary = formatRoutineBlockTargetSummary([item.targetSpec], setsCount);
        const fullNotes = [rbHeader, drHeader, noteAndPrs]
          .filter((part) => part.length > 0)
          .join("\n");
        return {
          id: nextBlockIdFn(),
          routineId,
          position: idx,
          name: ex.name,
          kind: "STRENGTH",
          format: DAILY_ROUTINE_EXERCISE_ITEM_FORMAT,
          setsCount,
          targetRepsScheme: summary,
          exerciseIdsCsv: String(ex.id),
          exerciseNamesCsv: ex.name,
          notes: fullNotes,
          section: "",
          subBlock: "",
        };
      }
      const fullNotes = noteAndPrs ? `${drHeader}\n${noteAndPrs}` : drHeader;
      return {
        id: nextBlockIdFn(),
        routineId,
        position: idx,
        name: ex.name,
        kind: "STRENGTH",
        format: DAILY_ROUTINE_EXERCISE_ITEM_FORMAT,
        setsCount: 1,
        targetRepsScheme: "",
        exerciseIdsCsv: String(ex.id),
        exerciseNamesCsv: ex.name,
        notes: fullNotes,
        section: "",
        subBlock: "",
      };
    });
  }

  async upsertDailyRoutine(
    ctx: UserContext,
    input: UpsertDailyRoutineInput
  ): Promise<DailyRoutineRecord> {
    const cleanTitle = input.title.trim();
    if (!cleanTitle) {
      throw new Error("Daily Routine title cannot be blank.");
    }
    if (!input.items || input.items.length === 0) {
      throw new Error("Daily Routine must contain at least one Complex or Exercise item.");
    }

    return this.runWithUserMutationLock(ctx, ["exercises", "routines"], async (tx) => {
      const exerciseReqs: Array<{ name: string; metricType?: string; tracksRepMax?: boolean }> = [];
      for (const item of input.items) {
        if (item.item_type === "EXERCISE") {
          const cleanEx = (item.exercise_name ?? "").trim();
          if (!cleanEx) {
            throw new Error("Each item with item_type='EXERCISE' must specify exercise_name.");
          }
          exerciseReqs.push({
            name: cleanEx,
            metricType: item.target_metric
              ? inferLibraryMetricTypeFromRoutineTarget(item.target_metric)
              : "WEIGHT",
            tracksRepMax: !item.target_metric || item.target_metric === "REPS",
          });
          for (const pr of item.default_tracked_prs ?? []) {
            const mvName = (pr.movement_name ?? pr.movementName ?? "").trim();
            if (mvName) {
              exerciseReqs.push({
                name: mvName,
                metricType: "WEIGHT",
                tracksRepMax: true,
              });
            }
          }
        } else {
          for (const bt of item.exercise_targets ?? []) {
            const btName = (bt.exercise_name ?? "").trim();
            if (btName) {
              exerciseReqs.push({
                name: btName,
                metricType: inferLibraryMetricTypeFromRoutineTarget(bt.target_metric),
                tracksRepMax: bt.target_metric === "REPS",
              });
            }
          }
        }
      }

      const exerciseMap = await this.resolveExerciseMapInStore(tx, ctx, exerciseReqs);
      const allExercises = await tx.readCollection<ExerciseRecord>(ctx, "exercises");
      const exercisesById = new Map<number, ExerciseRecord>(
        allExercises.map((e) => [Number(e.id), e])
      );
      const exercisesByLowerName = new Map<string, ExerciseRecord>(
        allExercises.map((e) => [e.name.trim().toLowerCase(), e])
      );

      const routines = await tx.readCollection<RoutineWithBlocksRecord>(ctx, "routines");
      const activeAvailableRoutines = routines.filter(
        (r) =>
          !isDeletedTombstone(r.routine?.deletedAtMillis) &&
          !isDailyRoutineFormat(r.routine?.defaultFormat)
      );

      const resolvedItems: Array<{
        itemType: "BLOCK" | "EXERCISE";
        resolvedBlock?: RoutineWithBlocksRecord;
        resolvedExercise?: ExerciseRecord;
        note?: string;
        defaultTrackedPrs?: TrackedPrSlotSpec[];
        targetSetsCount?: number;
        targetSpec?: RoutineExerciseTargetSlot | null;
        blockExerciseTargets?: Array<{
          exerciseId: number;
          exerciseName: string;
          slot: RoutineExerciseTargetSlot;
        }>;
      }> = [];

      for (const item of input.items) {
        const normalizedType = item.item_type === "EXERCISE" ? "EXERCISE" : "BLOCK";
        if (normalizedType === "BLOCK") {
          const resolvedRefId = item.complex_id ?? item.block_id;
          const resolvedRefTitle = (item.complex_title ?? item.block_title ?? "").trim();
          const matchedBlock =
            resolvedRefId !== undefined
              ? activeAvailableRoutines.find((r) => r.routine.id === resolvedRefId)
              : resolvedRefTitle
                ? activeAvailableRoutines.find(
                    (r) =>
                      r.routine.name.trim().toLowerCase() ===
                      resolvedRefTitle.toLowerCase()
                  )
                : undefined;
          if (!matchedBlock) {
            throw new Error(
              `Referenced Complex not found (${resolvedRefId ?? (resolvedRefTitle || "unspecified")}).`
            );
          }

          let targetSetsCount: number | undefined;
          let blockExerciseTargets:
            | Array<{
                exerciseId: number;
                exerciseName: string;
                slot: RoutineExerciseTargetSlot;
              }>
            | undefined;

          const decodedAb = this.buildAvailableBlockRecord(
            matchedBlock,
            exercisesById,
            exercisesByLowerName,
            [],
            false
          );
          const rawTargets = item.exercise_targets ?? [];
          if (rawTargets.length > 0 || item.sets_count !== undefined) {
            targetSetsCount = Math.max(1, Math.min(100, Math.trunc(item.sets_count ?? 4)));
            if (rawTargets.length > 0) {
              const validatedByLower = new Map<
                string,
                { exerciseId: number; exerciseName: string; slot: RoutineExerciseTargetSlot }
              >();
              for (const bt of rawTargets) {
                const btName = bt.exercise_name.trim();
                const resolvedBtEx =
                  exerciseMap.get(btName.toLowerCase()) ??
                  exercisesByLowerName.get(btName.toLowerCase());
                const slot = this.parseAndValidateTargetSlot(
                  bt,
                  targetSetsCount,
                  `${matchedBlock.routine.name} · ${btName}`
                );
                validatedByLower.set(btName.toLowerCase(), {
                  exerciseId: resolvedBtEx?.id ?? 0,
                  exerciseName: resolvedBtEx?.name ?? btName,
                  slot,
                });
              }

              const orderedTargets: Array<{
                exerciseId: number;
                exerciseName: string;
                slot: RoutineExerciseTargetSlot;
              }> = [];
              const consumedLower = new Set<string>();
              for (const abEx of decodedAb.exercises) {
                const lower = abEx.name.trim().toLowerCase();
                const explicitTarget = validatedByLower.get(lower);
                if (explicitTarget) {
                  orderedTargets.push({
                    exerciseId: abEx.id || explicitTarget.exerciseId,
                    exerciseName: abEx.name,
                    slot: explicitTarget.slot,
                  });
                  consumedLower.add(lower);
                } else {
                  const defaultMetric = inferRoutineTargetMetricFromLibraryMetric(
                    abEx.metricType
                  );
                  const defaultVal = defaultFixedForRoutineMetric(defaultMetric);
                  orderedTargets.push({
                    exerciseId: abEx.id,
                    exerciseName: abEx.name,
                    slot: {
                      metric: defaultMetric,
                      mode: "FIXED",
                      fixedValue: defaultVal,
                      perSetValues: Array.from(
                        { length: targetSetsCount },
                        () => defaultVal
                      ),
                    },
                  });
                }
              }
              for (const [lower, val] of validatedByLower.entries()) {
                if (!consumedLower.has(lower)) {
                  orderedTargets.push(val);
                }
              }
              blockExerciseTargets = orderedTargets;
            } else if (decodedAb.exercises.length > 0) {
              blockExerciseTargets = decodedAb.exercises.map((abEx) => {
                const metric = inferRoutineTargetMetricFromLibraryMetric(abEx.metricType);
                const fixedVal = defaultFixedForRoutineMetric(metric);
                return {
                  exerciseId: abEx.id,
                  exerciseName: abEx.name,
                  slot: {
                    metric,
                    mode: "FIXED",
                    fixedValue: fixedVal,
                    perSetValues: Array.from(
                      { length: targetSetsCount! },
                      () => fixedVal
                    ),
                  },
                };
              });
            }
          }

          resolvedItems.push({
            itemType: "BLOCK",
            resolvedBlock: matchedBlock,
            targetSetsCount,
            blockExerciseTargets,
          });
        } else {
          const cleanName = (item.exercise_name ?? "").trim();
          const ex =
            exerciseMap.get(cleanName.toLowerCase()) ??
            exercisesByLowerName.get(cleanName.toLowerCase());
          if (!ex) {
            throw new Error(`Failed to resolve standalone exercise '${cleanName}'.`);
          }
          const normalizedPrs = normalizeTrackedPrSlotsSpec(
            (item.default_tracked_prs ?? []).map((s) => {
              const mv = (s.movement_name ?? s.movementName ?? "").trim();
              const reps = Number(s.rm_reps ?? s.rmReps ?? 1);
              return {
                movementName:
                  exerciseMap.get(mv.toLowerCase())?.name ?? mv,
                rmReps: reps,
              };
            })
          );

          let targetSetsCount: number | undefined;
          let targetSpec: RoutineExerciseTargetSlot | null = null;
          const hasAnyTargetInput =
            item.sets_count !== undefined ||
            item.target_metric !== undefined ||
            item.target_mode !== undefined ||
            item.fixed_value !== undefined ||
            item.per_set_values !== undefined;
          if (hasAnyTargetInput) {
            targetSetsCount = Math.max(1, Math.min(100, Math.trunc(item.sets_count ?? 4)));
            const resolvedMetric =
              item.target_metric ??
              inferRoutineTargetMetricFromLibraryMetric(ex.metricType);
            targetSpec = this.parseAndValidateTargetSlot(
              {
                target_metric: resolvedMetric,
                target_mode: item.target_mode,
                fixed_value: item.fixed_value,
                per_set_values: item.per_set_values,
              },
              targetSetsCount,
              ex.name
            );
          }

          resolvedItems.push({
            itemType: "EXERCISE",
            resolvedExercise: ex,
            note: extractCleanRoutineNotes(item.note ?? ""),
            defaultTrackedPrs: normalizedPrs,
            targetSetsCount,
            targetSpec,
          });
        }
      }

      const existingIdx =
        input.routine_id !== undefined
          ? routines.findIndex(
              (r) =>
                isDailyRoutineFormat(r.routine?.defaultFormat) &&
                r.routine?.id === input.routine_id
            )
          : routines.findIndex(
              (r) =>
                isDailyRoutineFormat(r.routine?.defaultFormat) &&
                r.routine?.name.trim().toLowerCase() === cleanTitle.toLowerCase()
            );

      const maxRoutineId = routines.reduce(
        (max, r) => Math.max(max, Number(r.routine?.id) || 0),
        0
      );
      let maxRoutineBlockId = routines.reduce(
        (max, r) =>
          Math.max(max, ...(r.blocks ?? []).map((b) => Number(b.id) || 0)),
        0
      );
      const routineId =
        existingIdx >= 0 ? routines[existingIdx].routine.id : maxRoutineId + 1;

      const firstExId =
        resolvedItems.find((it) => it.itemType === "EXERCISE")?.resolvedExercise?.id ??
        resolvedItems.find((it) => it.itemType === "BLOCK")?.resolvedBlock?.routine
          .mainExerciseId ??
        null;

      const storedBlocks = this.buildDailyRoutineStoredBlocksInTx(
        routineId,
        resolvedItems,
        () => ++maxRoutineBlockId
      );

      const now = Date.now();
      const record: RoutineWithBlocksRecord = {
        routine: {
          id: routineId,
          name: cleanTitle,
          description: extractCleanRoutineNotes(input.notes ?? ""),
          mainExerciseId: firstExId,
          mainExerciseName: firstExId ? exercisesById.get(firstExId)?.name ?? null : null,
          defaultFormat: DAILY_ROUTINE_FORMAT,
          updatedAtMillis: now,
          deletedAtMillis: null,
        },
        blocks: storedBlocks,
      };

      if (existingIdx >= 0) {
        routines[existingIdx] = record;
      } else {
        routines.push(record);
      }
      await tx.writeCollection(ctx, "routines", routines);

      const activeAbRecords = routines
        .filter(
          (r) =>
            !isDeletedTombstone(r.routine?.deletedAtMillis) &&
            !isDailyRoutineFormat(r.routine?.defaultFormat)
        )
        .map((r) =>
          this.buildAvailableBlockRecord(
            r,
            exercisesById,
            exercisesByLowerName,
            [],
            false
          )
        );
      return this.buildDailyRoutineRecord(
        record,
        new Map(activeAbRecords.map((b) => [b.id, b])),
        new Map(activeAbRecords.map((b) => [b.title.trim().toLowerCase(), b])),
        exercisesById,
        exercisesByLowerName,
        []
      );
    });
  }

  async deleteDailyRoutine(
    ctx: UserContext,
    input: { routine_id?: number; title?: string }
  ): Promise<{ deleted: boolean; deletedCount: number; deletedTitles: string[] }> {
    const cleanTitle = input.title?.trim().toLowerCase();
    if (input.routine_id === undefined && !cleanTitle) {
      throw new Error("Provide either 'routine_id' or 'title' to delete a Daily Routine.");
    }
    return this.runWithUserMutationLock(ctx, ["routines"], async (tx) => {
      const routines = await tx.readCollection<RoutineWithBlocksRecord>(ctx, "routines");
      const now = Date.now();
      const deletedTitles: string[] = [];

      for (let i = 0; i < routines.length; i++) {
        const r = routines[i];
        if (isDeletedTombstone(r.routine?.deletedAtMillis)) continue;
        if (!isDailyRoutineFormat(r.routine?.defaultFormat)) continue;
        const matchesId =
          input.routine_id !== undefined && r.routine.id === input.routine_id;
        const matchesTitle =
          Boolean(cleanTitle) && r.routine.name.trim().toLowerCase() === cleanTitle;
        if (matchesId || matchesTitle) {
          deletedTitles.push(r.routine.name);
          routines[i] = {
            ...r,
            routine: {
              ...r.routine,
              updatedAtMillis: now,
              deletedAtMillis: now,
            },
          };
        }
      }

      if (deletedTitles.length > 0) {
        await tx.writeCollection(ctx, "routines", routines);
      }
      return {
        deleted: deletedTitles.length > 0,
        deletedCount: deletedTitles.length,
        deletedTitles,
      };
    });
  }

  async logDailyRoutineSession(
    ctx: UserContext,
    input: LogDailyRoutineSessionInput
  ): Promise<{
    session: SessionWithBlocksRecord;
    recordedPrs: RepMaxRecord[];
    updatedRoutineTemplate: DailyRoutineRecord | null;
  }> {
    return this.runWithUserMutationLock(
      ctx,
      ["exercises", "routines", "sessions", "cycle_goals", "rep_maxes"],
      async (tx) => {
        const targetCycle = await this.resolveTargetCycleInStore(
          tx,
          ctx,
          input.cycle_id
        );
        const routines = await tx.readCollection<RoutineWithBlocksRecord>(ctx, "routines");
        const cleanRoutineTitle = input.routine_title?.trim() ?? "";

        const matchedDrIdx =
          input.routine_id !== undefined
            ? routines.findIndex(
                (r) =>
                  !isDeletedTombstone(r.routine?.deletedAtMillis) &&
                  isDailyRoutineFormat(r.routine?.defaultFormat) &&
                  r.routine.id === input.routine_id
              )
            : cleanRoutineTitle
              ? routines.findIndex(
                  (r) =>
                    !isDeletedTombstone(r.routine?.deletedAtMillis) &&
                    isDailyRoutineFormat(r.routine?.defaultFormat) &&
                    r.routine.name.trim().toLowerCase() ===
                      cleanRoutineTitle.toLowerCase()
                )
              : -1;
        const matchedDr = matchedDrIdx >= 0 ? routines[matchedDrIdx] : undefined;

        const nonDailyRoutines = routines.filter(
          (r) =>
            !isDeletedTombstone(r.routine?.deletedAtMillis) &&
            !isDailyRoutineFormat(r.routine?.defaultFormat)
        );

        const exReqs: Array<{ name: string; metricType?: string; tracksRepMax?: boolean }> =
          [];
        for (const item of input.logged_items) {
          if (item.exercise_name?.trim()) {
            exReqs.push({
              name: item.exercise_name.trim(),
              metricType: item.metric
                ? inferLibraryMetricTypeFromRoutineTarget(item.metric)
                : "WEIGHT",
              tracksRepMax: !item.metric || item.metric === "REPS",
            });
          }
          for (const log of item.exercise_logs ?? []) {
            const logName = (log.exercise_name ?? log.exerciseName ?? "").trim();
            if (logName) {
              exReqs.push({
                name: logName,
                metricType: log.metric
                  ? inferLibraryMetricTypeFromRoutineTarget(log.metric)
                  : "WEIGHT",
                tracksRepMax: !log.metric || log.metric === "REPS",
              });
            }
          }
          for (const pr of item.achieved_prs ?? []) {
            if (pr.movement_name?.trim()) {
              exReqs.push({
                name: pr.movement_name.trim(),
                metricType: "WEIGHT",
                tracksRepMax: true,
              });
            }
          }
        }
        const exerciseMap = await this.resolveExerciseMapInStore(tx, ctx, exReqs);
        const allExercises = await tx.readCollection<ExerciseRecord>(ctx, "exercises");
        const exercisesById = new Map<number, ExerciseRecord>(
          allExercises.map((e) => [Number(e.id), e])
        );
        const exercisesByLowerName = new Map<string, ExerciseRecord>(
          allExercises.map((e) => [e.name.trim().toLowerCase(), e])
        );

        const nonDailyAbRecords = nonDailyRoutines.map((r) =>
          this.buildAvailableBlockRecord(
            r,
            exercisesById,
            exercisesByLowerName,
            [],
            false
          )
        );
        const nonDailyAbById = new Map<number, AvailableBlockRecord>(
          nonDailyAbRecords.map((b) => [b.id, b])
        );
        const nonDailyAbByLowerTitle = new Map<string, AvailableBlockRecord>(
          nonDailyAbRecords.map((b) => [b.title.trim().toLowerCase(), b])
        );
        const priorDailyRoutine = matchedDr
          ? this.buildDailyRoutineRecord(
              matchedDr,
              nonDailyAbById,
              nonDailyAbByLowerTitle,
              exercisesById,
              exercisesByLowerName,
              []
            )
          : null;

        const resolvedSessionTitle =
          input.session_title?.trim() ||
          matchedDr?.routine.name ||
          cleanRoutineTitle ||
          input.logged_items[0]?.complex_title?.trim() ||
          input.logged_items[0]?.block_title?.trim() ||
          input.logged_items[0]?.exercise_name?.trim() ||
          "Daily Routine Session";

        const sessions = await tx.readCollection<SessionWithBlocksRecord>(ctx, "sessions");
        const existingSessionIdx = sessions.findIndex(
          (s) =>
            !isDeletedTombstone(s.session?.deletedAtMillis) &&
            s.session.date === input.date &&
            s.session.title.trim().toLowerCase() === resolvedSessionTitle.toLowerCase()
        );
        const maxSessionId = sessions.reduce(
          (max, s) => Math.max(max, Number(s.session?.id) || 0),
          0
        );
        let maxBlockId = sessions.reduce(
          (max, s) =>
            Math.max(max, ...(s.blocks ?? []).map((b) => Number(b.block?.id) || 0)),
          0
        );
        let maxSetId = sessions.reduce(
          (max, s) =>
            Math.max(
              max,
              ...(s.blocks ?? []).flatMap((b) =>
                (b.sets ?? []).map((st) => Number(st.id) || 0)
              )
            ),
          0
        );

        const sessionId =
          existingSessionIdx >= 0
            ? sessions[existingSessionIdx].session.id
            : maxSessionId + 1;
        const now = Date.now();

        const createdBlocks: SessionBlockEntryRecord[] = [];
        const pendingItemPrs: Array<{
          pr: { movement_name: string; rm_reps: number; weight_kg: number };
          targetBlockId: number;
          sourceBlockTitle: string;
        }> = [];
        const templateItemsForUpdate: Array<{
          itemType: "BLOCK" | "EXERCISE";
          resolvedBlock?: RoutineWithBlocksRecord;
          resolvedExercise?: ExerciseRecord;
          note?: string;
          defaultTrackedPrs?: TrackedPrSlotSpec[];
          targetSetsCount?: number;
          targetSpec?: RoutineExerciseTargetSlot | null;
          blockExerciseTargets?: Array<{
            exerciseId: number;
            exerciseName: string;
            slot: RoutineExerciseTargetSlot;
          }>;
        }> = [];

        for (const item of input.logged_items) {
          const normalizedType = item.item_type === "EXERCISE" ? "EXERCISE" : "BLOCK";
          if (normalizedType === "BLOCK") {
            const resolvedRefId = item.complex_id ?? item.block_id;
            const cleanTitle = (item.complex_title ?? item.block_title ?? "").trim();
            const matchedAb =
              resolvedRefId !== undefined
                ? nonDailyRoutines.find((r) => r.routine.id === resolvedRefId)
                : cleanTitle
                  ? nonDailyRoutines.find(
                      (r) =>
                        r.routine.name.trim().toLowerCase() === cleanTitle.toLowerCase()
                    )
                  : undefined;
            const decodedAb = matchedAb
              ? this.buildAvailableBlockRecord(
                  matchedAb,
                  exercisesById,
                  exercisesByLowerName,
                  [],
                  false
                )
              : undefined;
            const resolvedBlockTitle =
              matchedAb?.routine.name || cleanTitle || "Training Complex";
            const instructions = decodedAb?.instructions ?? "";
            const priorBlockItem = matchedAb
              ? priorDailyRoutine?.items.find(
                  (pi) =>
                    pi.itemType === "COMPLEX" &&
                    ((pi.complexId ?? pi.blockId) === matchedAb.routine.id ||
                      (pi.complexTitle ?? pi.blockTitle ?? "").trim().toLowerCase() ===
                        matchedAb.routine.name.trim().toLowerCase())
                )
              : undefined;

            const movementLogs: Array<{
              exerciseName: string;
              weights?: number[];
              metric?: RoutineTargetMetric;
              sets?: Array<{
                reps?: number;
                weight_kg?: number;
                weight?: number;
                metric_value?: number;
                group_index?: number;
              }>;
              setCountForEmpty?: number;
            }> =
              item.shared_set_weights_kg && item.shared_set_weights_kg.length > 0
                ? (() => {
                    const orderedNames =
                      decodedAb && decodedAb.exercises.length > 0
                        ? decodedAb.exercises.map((e) => e.name)
                        : (item.exercise_logs ?? [])
                            .map((l) => (l.exercise_name ?? l.exerciseName ?? "").trim())
                            .filter(Boolean);
                    const fallbackNames =
                      orderedNames.length > 0 ? orderedNames : [resolvedBlockTitle];
                    const targetsByLowerName = new Map<string, RoutineExerciseTargetSlot>();
                    for (const et of priorBlockItem?.exerciseTargets ?? []) {
                      if (et.exerciseName.trim()) {
                        targetsByLowerName.set(et.exerciseName.trim().toLowerCase(), et.target);
                      }
                    }
                    const explicitOverrides = new Map<
                      string,
                      {
                        exerciseName: string;
                        weights?: number[];
                        metric?: RoutineTargetMetric;
                        sets?: Array<{
                          reps?: number;
                          weight_kg?: number;
                          weight?: number;
                          metric_value?: number;
                          group_index?: number;
                        }>;
                        setCountForEmpty?: number;
                      }
                    >();
                    for (const l of item.exercise_logs ?? []) {
                      const clean = (l.exercise_name ?? l.exerciseName ?? "").trim();
                      if (clean) {
                        explicitOverrides.set(clean.toLowerCase(), {
                          exerciseName: clean,
                          weights: l.weights,
                          metric: l.metric,
                          sets: l.sets,
                          setCountForEmpty: l.set_count_for_empty ?? l.setCountForEmpty,
                        });
                      }
                    }
                    return this.expandSharedComplexSetWeights({
                      sharedSetWeightsKg: item.shared_set_weights_kg,
                      orderedExerciseNames: fallbackNames,
                      exercisesByLowerName,
                      targetsByLowerName,
                      explicitOverridesByLowerName: explicitOverrides,
                    });
                  })()
                : item.exercise_logs && item.exercise_logs.length > 0
                  ? item.exercise_logs.map((l) => ({
                      exerciseName:
                        (l.exercise_name ?? l.exerciseName ?? "").trim() || resolvedBlockTitle,
                      weights: l.weights,
                      metric: l.metric,
                      sets: l.sets,
                      setCountForEmpty: l.set_count_for_empty ?? l.setCountForEmpty,
                    }))
                  : (item.set_weights_kg !== undefined || item.sets !== undefined) &&
                      (decodedAb?.exercises.length ?? 0) <= 1
                    ? [
                        {
                          exerciseName:
                            decodedAb?.exercises[0]?.name || resolvedBlockTitle,
                          weights: item.set_weights_kg,
                          metric: item.metric,
                          sets: item.sets,
                        },
                      ]
                    : decodedAb && decodedAb.exercises.length > 0
                      ? decodedAb.exercises.map((e) => ({
                          exerciseName: e.name,
                          weights: item.set_weights_kg ?? [],
                          metric: item.metric,
                          setCountForEmpty: 3,
                        }))
                      : [
                          {
                            exerciseName: resolvedBlockTitle,
                            weights: item.set_weights_kg ?? [],
                            metric: item.metric,
                            sets: item.sets,
                            setCountForEmpty: 1,
                          },
                        ];

            const blockIdsForItem: number[] = [];
            const exIdsForBlock: Array<{ exId: number; sessionBlockId: number }> = [];

            for (const mLog of movementLogs) {
              const ex =
                exerciseMap.get(mLog.exerciseName.trim().toLowerCase()) ??
                exercisesByLowerName.get(mLog.exerciseName.trim().toLowerCase()) ??
                null;
              const blockId = ++maxBlockId;
              blockIdsForItem.push(blockId);
              if (ex) {
                exIdsForBlock.push({ exId: ex.id, sessionBlockId: blockId });
              }
              const { sets, notes: blockNotes } = this.buildLoggedSessionSetsAndNotes({
                blockId,
                nextSetIdFn: () => ++maxSetId,
                metric: mLog.metric,
                sets: mLog.sets,
                weights: mLog.weights,
                setCountForEmpty: mLog.setCountForEmpty ?? 1,
                exerciseMetricType: ex?.metricType,
              });

              createdBlocks.push({
                block: {
                  id: blockId,
                  sessionId,
                  position: createdBlocks.length,
                  name: ex?.name ?? mLog.exerciseName.trim(),
                  kind: "STRENGTH",
                  format: "",
                  scheme: "",
                  mainExerciseId: ex ? ex.id : null,
                  mainExerciseName: ex ? ex.name : null,
                  routineId: matchedAb ? matchedAb.routine.id : null,
                  routineName: matchedAb ? matchedAb.routine.name : resolvedBlockTitle,
                  description: instructions,
                  resultText: "",
                  resultValue: null,
                  notes: blockNotes,
                  section: "",
                  subBlock: resolvedBlockTitle,
                  exerciseIdsCsv: ex ? String(ex.id) : "",
                  exerciseNamesCsv: ex ? ex.name : mLog.exerciseName.trim(),
                  isCompleted: true,
                },
                sets,
              });
            }

            if (matchedAb) {
              templateItemsForUpdate.push({
                itemType: "BLOCK",
                resolvedBlock: matchedAb,
                targetSetsCount: priorBlockItem?.setsCount ?? undefined,
                blockExerciseTargets:
                  priorBlockItem && priorBlockItem.exerciseTargets.length > 0
                    ? priorBlockItem.exerciseTargets.map((et) => ({
                        exerciseId: et.exerciseId ?? 0,
                        exerciseName: et.exerciseName,
                        slot: et.target,
                      }))
                    : undefined,
              });
            }

            for (const pr of item.achieved_prs ?? []) {
              const prEx = exerciseMap.get(pr.movement_name.trim().toLowerCase());
              const targetBlockId =
                (prEx
                  ? exIdsForBlock.find((x) => x.exId === prEx.id)?.sessionBlockId
                  : undefined) ?? blockIdsForItem[0];
              if (targetBlockId) {
                pendingItemPrs.push({
                  pr,
                  targetBlockId,
                  sourceBlockTitle: resolvedBlockTitle,
                });
              }
            }
          } else {
            const cleanExName = (item.exercise_name ?? "").trim();
            if (!cleanExName) {
              throw new Error(
                "Each logged item with item_type='EXERCISE' must provide exercise_name."
              );
            }
            const ex =
              exerciseMap.get(cleanExName.toLowerCase()) ??
              exercisesByLowerName.get(cleanExName.toLowerCase());
            if (!ex) {
              throw new Error(`Failed to resolve standalone exercise '${cleanExName}'.`);
            }
            const blockId = ++maxBlockId;
            const cleanNote = (item.note ?? "").trim();
            const firstLog = item.exercise_logs?.[0];
            const { sets, notes: blockNotes } = this.buildLoggedSessionSetsAndNotes({
              blockId,
              nextSetIdFn: () => ++maxSetId,
              metric: item.metric ?? firstLog?.metric,
              sets: item.sets ?? firstLog?.sets,
              weights: item.set_weights_kg ?? firstLog?.weights,
              setCountForEmpty: 4,
              exerciseMetricType: ex.metricType,
            });

            createdBlocks.push({
              block: {
                id: blockId,
                sessionId,
                position: createdBlocks.length,
                name: ex.name,
                kind: "STRENGTH",
                format: STANDALONE_EXERCISE_SESSION_FORMAT,
                scheme: "",
                mainExerciseId: ex.id,
                mainExerciseName: ex.name,
                routineId: null,
                routineName: null,
                description: cleanNote,
                resultText: "",
                resultValue: null,
                notes: blockNotes,
                section: "",
                subBlock: "",
                exerciseIdsCsv: String(ex.id),
                exerciseNamesCsv: ex.name,
                isCompleted: true,
              },
              sets,
            });

            const itemPrSlots = normalizeTrackedPrSlotsSpec(
              (item.achieved_prs ?? [])
                .filter((p) => p.rm_reps >= 1 && p.rm_reps <= 20)
                .map((p) => ({
                  movementName:
                    exerciseMap.get(p.movement_name.trim().toLowerCase())?.name ??
                    p.movement_name.trim(),
                  rmReps: p.rm_reps,
                }))
            );
            const priorExItem = priorDailyRoutine?.items.find(
              (pi) =>
                pi.itemType === "EXERCISE" &&
                (pi.exerciseId === ex.id ||
                  (pi.exerciseName ?? "").trim().toLowerCase() ===
                    ex.name.trim().toLowerCase())
            );
            templateItemsForUpdate.push({
              itemType: "EXERCISE",
              resolvedExercise: ex,
              note: cleanNote,
              defaultTrackedPrs:
                itemPrSlots.length > 0
                  ? itemPrSlots
                  : (priorExItem?.defaultTrackedPrs ?? []).map((p) => ({
                      movementName: p.movementName,
                      rmReps: p.rmReps,
                    })),
              targetSetsCount: priorExItem?.setsCount ?? undefined,
              targetSpec: priorExItem?.targetSpec ?? null,
            });

            for (const pr of item.achieved_prs ?? []) {
              pendingItemPrs.push({
                pr,
                targetBlockId: blockId,
                sourceBlockTitle: ex.name,
              });
            }
          }
        }

        const sessionRecord: SessionWithBlocksRecord = {
          session: {
            id: sessionId,
            cycleId: targetCycle.cycleId,
            cycleName: targetCycle.cycleName,
            isActiveCycle: targetCycle.isActive,
            date: input.date,
            title: resolvedSessionTitle,
            notes: input.notes ?? "",
            updatedAtMillis: now,
            deletedAtMillis: null,
          },
          blocks: createdBlocks,
        };

        if (existingSessionIdx >= 0) {
          sessions[existingSessionIdx] = sessionRecord;
        } else {
          sessions.push(sessionRecord);
        }
        await tx.writeCollection(ctx, "sessions", sessions);

        const repMaxes = await tx.readCollection<RepMaxRecord>(ctx, "rep_maxes");
        let maxRepMaxId = repMaxes.reduce(
          (max, rm) => Math.max(max, Number(rm.id) || 0),
          0
        );
        const recordedPrs: RepMaxRecord[] = [];
        let repMaxMutated = false;

        for (const pending of pendingItemPrs) {
          const ex =
            exerciseMap.get(pending.pr.movement_name.trim().toLowerCase()) ??
            exercisesByLowerName.get(pending.pr.movement_name.trim().toLowerCase());
          if (!ex) {
            throw new Error(
              `Failed to resolve PR movement '${pending.pr.movement_name}'.`
            );
          }
          const existingRmIdx = repMaxes.findIndex(
            (rm) =>
              rm.exerciseId === ex.id &&
              rm.reps === pending.pr.rm_reps &&
              Math.round(Number(rm.weight) * 100) ===
                Math.round(pending.pr.weight_kg * 100) &&
              rm.date === input.date
          );

          if (existingRmIdx >= 0) {
            const updatedRm: RepMaxRecord = {
              ...repMaxes[existingRmIdx],
              exerciseName: ex.name,
              sessionId,
              blockId: pending.targetBlockId,
              sessionTitle: resolvedSessionTitle,
              sourceBlockTitle: pending.sourceBlockTitle,
              updatedAtMillis: now,
              deletedAtMillis: null,
            };
            repMaxes[existingRmIdx] = updatedRm;
            repMaxMutated = true;
            recordedPrs.push(updatedRm);
          } else {
            const createdRm: RepMaxRecord = {
              id: ++maxRepMaxId,
              exerciseId: ex.id,
              exerciseName: ex.name,
              reps: pending.pr.rm_reps,
              weight: pending.pr.weight_kg,
              date: input.date,
              cycleId: targetCycle.cycleId,
              cycleName: targetCycle.cycleName,
              isActiveCycle: targetCycle.isActive,
              sessionId,
              blockId: pending.targetBlockId,
              sessionTitle: resolvedSessionTitle,
              sourceBlockTitle: pending.sourceBlockTitle,
              updatedAtMillis: now,
              deletedAtMillis: null,
            };
            repMaxes.push(createdRm);
            repMaxMutated = true;
            recordedPrs.push(createdRm);
          }
        }

        if (repMaxMutated) {
          await tx.writeCollection(ctx, "rep_maxes", repMaxes);
        }

        let updatedRoutineTemplate: DailyRoutineRecord | null = null;
        if (
          input.update_routine_template === true &&
          matchedDrIdx >= 0 &&
          matchedDr &&
          templateItemsForUpdate.length > 0
        ) {
          let maxRoutineBlockId = routines.reduce(
            (max, r) =>
              Math.max(max, ...(r.blocks ?? []).map((b) => Number(b.id) || 0)),
            0
          );
          const storedBlocks = this.buildDailyRoutineStoredBlocksInTx(
            matchedDr.routine.id,
            templateItemsForUpdate,
            () => ++maxRoutineBlockId
          );
          const firstExId =
            templateItemsForUpdate.find((t) => t.itemType === "EXERCISE")
              ?.resolvedExercise?.id ??
            templateItemsForUpdate.find((t) => t.itemType === "BLOCK")?.resolvedBlock
              ?.routine.mainExerciseId ??
            matchedDr.routine.mainExerciseId;

          const updatedDrRecord: RoutineWithBlocksRecord = {
            routine: {
              ...matchedDr.routine,
              mainExerciseId: firstExId,
              mainExerciseName: firstExId
                ? exercisesById.get(firstExId)?.name ?? null
                : null,
              defaultFormat: DAILY_ROUTINE_FORMAT,
              updatedAtMillis: now,
              deletedAtMillis: null,
            },
            blocks: storedBlocks,
          };
          routines[matchedDrIdx] = updatedDrRecord;
          await tx.writeCollection(ctx, "routines", routines);

          const activeAbRecords = routines
            .filter(
              (r) =>
                !isDeletedTombstone(r.routine?.deletedAtMillis) &&
                !isDailyRoutineFormat(r.routine?.defaultFormat)
            )
            .map((r) =>
              this.buildAvailableBlockRecord(
                r,
                exercisesById,
                exercisesByLowerName,
                [],
                false
              )
            );
          updatedRoutineTemplate = this.buildDailyRoutineRecord(
            updatedDrRecord,
            new Map(activeAbRecords.map((b) => [b.id, b])),
            new Map(activeAbRecords.map((b) => [b.title.trim().toLowerCase(), b])),
            exercisesById,
            exercisesByLowerName,
            [sessionRecord]
          );
        }

        return {
          session: sessionRecord,
          recordedPrs,
          updatedRoutineTemplate,
        };
      }
    );
  }

  async getDailyRoutineProgress(
    ctx: UserContext,
    input: { routine_id?: number; routine_title?: string }
  ): Promise<{
    dailyRoutine: DailyRoutineRecord;
    items: DailyRoutineConstituentProgressRecord[];
  }> {
    if (input.routine_id === undefined && !input.routine_title?.trim()) {
      throw new Error(
        "Provide 'routine_id' or 'routine_title' to query Daily Routine progress."
      );
    }
    const [routines, exercises, sessions, repMaxes] = await Promise.all([
      this.backing.readCollection<RoutineWithBlocksRecord>(ctx, "routines"),
      this.backing.readCollection<ExerciseRecord>(ctx, "exercises"),
      this.backing.readCollection<SessionWithBlocksRecord>(ctx, "sessions"),
      this.backing.readCollection<RepMaxRecord>(ctx, "rep_maxes"),
    ]);

    const cleanTitle = input.routine_title?.trim().toLowerCase();
    const targetRaw = routines.find(
      (r) =>
        !isDeletedTombstone(r.routine?.deletedAtMillis) &&
        isDailyRoutineFormat(r.routine?.defaultFormat) &&
        ((input.routine_id !== undefined && r.routine.id === input.routine_id) ||
          (cleanTitle !== undefined &&
            r.routine.name.trim().toLowerCase() === cleanTitle))
    );
    if (!targetRaw) {
      throw new Error(
        `Active Daily Routine not found (${input.routine_id ?? input.routine_title}).`
      );
    }

    const exercisesById = new Map<number, ExerciseRecord>(
      exercises.map((e) => [Number(e.id), e])
    );
    const exercisesByLowerName = new Map<string, ExerciseRecord>(
      exercises.map((e) => [e.name.trim().toLowerCase(), e])
    );
    const activeSessions = sessions
      .filter((s) => !isDeletedTombstone(s.session?.deletedAtMillis))
      .sort((a, b) => {
        const cmp = (b.session?.date ?? "").localeCompare(a.session?.date ?? "");
        return cmp !== 0
          ? cmp
          : (Number(b.session?.id) || 0) - (Number(a.session?.id) || 0);
      });

    const activeAvailableBlocks = routines
      .filter(
        (r) =>
          !isDeletedTombstone(r.routine?.deletedAtMillis) &&
          !isDailyRoutineFormat(r.routine?.defaultFormat)
      )
      .map((r) =>
        this.buildAvailableBlockRecord(
          r,
          exercisesById,
          exercisesByLowerName,
          [],
          false
        )
      );
    const abById = new Map<number, AvailableBlockRecord>(
      activeAvailableBlocks.map((b) => [b.id, b])
    );
    const abByLowerTitle = new Map<string, AvailableBlockRecord>(
      activeAvailableBlocks.map((b) => [b.title.trim().toLowerCase(), b])
    );

    const dailyRoutine = this.buildDailyRoutineRecord(
      targetRaw,
      abById,
      abByLowerTitle,
      exercisesById,
      exercisesByLowerName,
      activeSessions
    );
    const activeRms = repMaxes.filter(
      (rm) => !isDeletedTombstone(rm.deletedAtMillis) && Number(rm.weight) > 0
    );

    const items: DailyRoutineConstituentProgressRecord[] = dailyRoutine.items.map(
      (item) => {
        const isComplex = item.itemType === "COMPLEX";
        const resolvedComplexId = item.complexId ?? item.blockId;
        const itemTitle = isComplex
          ? item.complexTitle ?? item.blockTitle ?? "Complex"
          : item.exerciseName ?? "Exercise";
        const subtitle = isComplex ? item.instructions ?? "" : item.note ?? "";

        const matchedEntries: Array<{
          sessionId: number;
          date: string;
          sessionTitle: string;
          values: number[];
          weightsKg: number[];
          volume: number;
          volumeKg: number;
          hasAnyPositiveWeight: boolean;
          lastMetricBlock: SessionBlockEntryRecord | null;
        }> = [];

        const ascSessions = [...activeSessions].reverse();
        let anyPositiveWeightAllSessions = false;
        let lastMetricBlockOverall: SessionBlockEntryRecord | null = null;
        let lastValuesKind: "WEIGHT" | "METRIC" | "REPS" | null = null;

        for (const sw of ascSessions) {
          const matchingBlocks = (sw.blocks ?? []).filter((entry) => {
            const blk = entry.block;
            if (isComplex) {
              if (
                (blk.format ?? "").trim().toUpperCase() ===
                STANDALONE_EXERCISE_SESSION_FORMAT
              ) {
                return false;
              }
              if (resolvedComplexId && blk.routineId === resolvedComplexId) return true;
              const tLower = itemTitle.trim().toLowerCase();
              return (
                (blk.subBlock ?? "").trim().toLowerCase() === tLower ||
                blk.name.trim().toLowerCase() === tLower
              );
            } else {
              const exId = item.exerciseId ?? 0;
              if (exId > 0 && blk.mainExerciseId === exId) return true;
              const csvIds = (blk.exerciseIdsCsv ?? "")
                .split(",")
                .map((s) => Number.parseInt(s.trim(), 10))
                .filter((n) => Number.isFinite(n) && n > 0);
              if (exId > 0 && blk.mainExerciseId == null && csvIds.length === 1 && csvIds[0] === exId) {
                return true;
              }
              return blk.name.trim().toLowerCase() === itemTitle.trim().toLowerCase();
            }
          });

          if (matchingBlocks.length === 0) continue;

          const values: number[] = [];
          const weightsKg: number[] = [];
          let volume = 0;
          let volumeKg = 0;
          let entryHasWeight = false;
          let entryLastMetricBlock: SessionBlockEntryRecord | null = null;

          for (const entry of matchingBlocks) {
            const workingSets = (entry.sets ?? []).filter(
              (st) => !st.isWarmup && !st.isFailed
            );
            const targetSets =
              workingSets.length > 0 ? workingSets : entry.sets ?? [];
            if (targetSets.length === 0) continue;

            const hasWeight = targetSets.some((st) => Number(st.weight) > 0);
            const hasMetricVal = targetSets.some((st) => Number(st.metricValue) > 0);
            const explicitReps = decodeLogMetricHeader(entry.block.notes) === "REPS";
            const isSingleBlank =
              targetSets.length === 1 &&
              Number(targetSets[0].reps) <= 1 &&
              !explicitReps;

            if (hasWeight) {
              anyPositiveWeightAllSessions = true;
              entryHasWeight = true;
              lastValuesKind = "WEIGHT";
              for (const st of targetSets) {
                const w = Number(st.weight);
                if (Number.isFinite(w) && w > 0) {
                  values.push(w);
                  weightsKg.push(w);
                  const mult = Math.max(1, Number(st.reps) || 1);
                  volume += w * mult;
                  volumeKg += w * mult;
                }
              }
            } else if (hasMetricVal) {
              entryLastMetricBlock = entry;
              lastMetricBlockOverall = entry;
              lastValuesKind = "METRIC";
              for (const st of targetSets) {
                const mv = Number(st.metricValue);
                if (Number.isFinite(mv) && mv > 0) {
                  values.push(mv);
                  volume += mv;
                }
              }
            } else if (!isSingleBlank) {
              entryLastMetricBlock = entry;
              lastMetricBlockOverall = entry;
              lastValuesKind = "REPS";
              for (const st of targetSets) {
                const r = Math.trunc(Number(st.reps));
                if (Number.isFinite(r) && r > 0) {
                  values.push(r);
                  volume += r;
                }
              }
            }
          }

          if (values.length > 0) {
            matchedEntries.push({
              sessionId: sw.session.id,
              date: sw.session.date,
              sessionTitle: sw.session.title,
              values,
              weightsKg,
              volume: this.round2dp(volume),
              volumeKg: this.round2dp(volumeKg),
              hasAnyPositiveWeight: entryHasWeight,
              lastMetricBlock: entryLastMetricBlock,
            });
          }
        }

        const ab = resolvedComplexId ? abById.get(resolvedComplexId) : undefined;
        const fallbackMetric: RoutineTargetMetric | null = isComplex
          ? item.exerciseTargets[0]?.target.metric ?? null
          : item.targetSpec?.metric ?? null;
        const fallbackEx: ExerciseRecord | undefined = isComplex
          ? (ab?.exercises[0]?.id ? exercisesById.get(ab.exercises[0].id) : undefined)
          : (item.exerciseId ? exercisesById.get(item.exerciseId) : undefined) ??
            exercisesByLowerName.get(itemTitle.trim().toLowerCase());

        let unit: "kg" | "reps" | "cal" | "s" | "m" = "kg";
        if (anyPositiveWeightAllSessions) {
          unit = "kg";
        } else if (lastMetricBlockOverall) {
          const working = (lastMetricBlockOverall.sets ?? []).filter(
            (st) => !st.isWarmup && !st.isFailed
          );
          const target = working.length > 0 ? working : lastMetricBlockOverall.sets ?? [];
          const blkEx = lastMetricBlockOverall.block.mainExerciseId
            ? exercisesById.get(lastMetricBlockOverall.block.mainExerciseId)
            : fallbackEx;
          if (target.some((st) => Number(st.metricValue) > 0)) {
            const decoded = resolveSessionBlockMetric(
              lastMetricBlockOverall.block.notes,
              target,
              blkEx?.metricType
            );
            const eff =
              decoded === "REPS" && fallbackMetric && fallbackMetric !== "REPS"
                ? fallbackMetric
                : decoded;
            unit = routineMetricCompactUnit(eff);
          } else if (lastValuesKind === "REPS" || target.some((st) => Number(st.reps) > 0)) {
            unit = "reps";
          }
        } else if (fallbackMetric) {
          unit =
            fallbackMetric === "REPS"
              ? (fallbackEx?.metricType ?? "").toUpperCase() === "REPS"
                ? "reps"
                : "kg"
              : routineMetricCompactUnit(fallbackMetric);
        } else if (fallbackEx) {
          switch ((fallbackEx.metricType ?? "").toUpperCase()) {
            case "CALORIES":
              unit = "cal";
              break;
            case "TIME":
              unit = "s";
              break;
            case "DISTANCE":
              unit = "m";
              break;
            case "REPS":
              unit = "reps";
              break;
            default:
              unit = "kg";
              break;
          }
        }

        const isKgUnit = unit === "kg";
        const byDate = new Map<
          string,
          { values: number[]; weightsKg: number[]; volume: number; volumeKg: number }
        >();
        for (const m of matchedEntries) {
          const cur = byDate.get(m.date) ?? {
            values: [],
            weightsKg: [],
            volume: 0,
            volumeKg: 0,
          };
          cur.values.push(...m.values);
          cur.weightsKg.push(...m.weightsKg);
          cur.volume = this.round2dp(cur.volume + m.volume);
          cur.volumeKg = this.round2dp(cur.volumeKg + m.volumeKg);
          byDate.set(m.date, cur);
        }

        const chartPoints = Array.from(byDate.entries())
          .sort((a, b) => a[0].localeCompare(b[0]))
          .map(([date, agg]) => {
            const top = Math.max(...agg.values);
            const avg = agg.values.reduce((s, v) => s + v, 0) / agg.values.length;
            const topKg =
              agg.weightsKg.length > 0 ? this.round2dp(Math.max(...agg.weightsKg)) : 0;
            const avgKg =
              agg.weightsKg.length > 0
                ? this.round2dp(
                    agg.weightsKg.reduce((s, w) => s + w, 0) / agg.weightsKg.length
                  )
                : 0;
            return {
              date,
              top: this.round2dp(top),
              average: this.round2dp(avg),
              volume: agg.volume,
              topKg,
              averageKg: avgKg,
              volumeKg: agg.volumeKg,
            };
          });

        const allTimeTop =
          chartPoints.length > 0
            ? this.round2dp(Math.max(...chartPoints.map((c) => c.top)))
            : null;
        const latestDay =
          chartPoints.length > 0 ? chartPoints[chartPoints.length - 1] : null;
        const prevDay =
          chartPoints.length > 1 ? chartPoints[chartPoints.length - 2] : null;
        const topDelta =
          latestDay && prevDay
            ? this.round2dp(latestDay.top - prevDay.top)
            : null;
        const lastSets = latestDay
          ? byDate.get(latestDay.date)?.values ?? []
          : [];

        const allTimeTopKg = isKgUnit ? allTimeTop : null;
        const lastTopKg = isKgUnit ? (latestDay?.top ?? null) : null;
        const lastAverageKg = isKgUnit ? (latestDay?.average ?? null) : null;
        const lastVolumeKg = isKgUnit ? (latestDay?.volume ?? null) : null;
        const topDeltaKg = isKgUnit ? topDelta : null;
        const lastSetsKg = isKgUnit ? lastSets : [];

        const targetExIds = new Set<number>();
        if (isComplex && ab) {
          for (const ex of ab.exercises) targetExIds.add(ex.id);
          for (const p of ab.trackedPrs) {
            if (p.movementId > 0) targetExIds.add(p.movementId);
          }
        } else if (item.exerciseId && item.exerciseId > 0) {
          targetExIds.add(item.exerciseId);
        }

        const bestByKey = new Map<string, RepMaxRecord>();
        for (const rm of activeRms) {
          if (!targetExIds.has(rm.exerciseId)) continue;
          const k = `${rm.exerciseId}#${rm.reps}`;
          const prev = bestByKey.get(k);
          if (!prev || Number(rm.weight) > Number(prev.weight)) {
            bestByKey.set(k, rm);
          }
        }

        const trackedPrs = item.defaultTrackedPrs.map((slot) => {
          const exId =
            slot.movementId > 0
              ? slot.movementId
              : exercisesByLowerName.get(slot.movementName.trim().toLowerCase())?.id ??
                0;
          const matchedRm = bestByKey.get(`${exId}#${slot.rmReps}`);
          return {
            movementId: exId,
            movementName: slot.movementName,
            rmReps: slot.rmReps,
            bestWeightKg: matchedRm ? Number(matchedRm.weight) : null,
          };
        });

        const recentSessions = [...matchedEntries]
          .reverse()
          .slice(0, 10)
          .map((m) => ({
            sessionId: m.sessionId,
            date: m.date,
            sessionTitle: m.sessionTitle,
            unit,
            top:
              m.values.length > 0
                ? this.round2dp(Math.max(...m.values))
                : null,
            values: m.values,
            topKg:
              m.weightsKg.length > 0
                ? this.round2dp(Math.max(...m.weightsKg))
                : null,
            weightsKg: m.weightsKg,
          }));

        return {
          itemType: item.itemType,
          complexId: isComplex ? resolvedComplexId : null,
          blockId: isComplex ? resolvedComplexId : null,
          title: itemTitle,
          subtitle,
          unit,
          allTimeTop,
          lastTop: latestDay?.top ?? null,
          lastTopDate: latestDay?.date ?? null,
          lastAverage: latestDay?.average ?? null,
          lastVolume: latestDay?.volume ?? null,
          topDelta,
          lastSets,
          allTimeTopKg,
          lastTopKg,
          lastAverageKg,
          lastVolumeKg,
          topDeltaKg,
          lastSetsKg,
          trackedPrs,
          chartPoints,
          recentSessions,
        };
      }
    );

    return { dailyRoutine, items };
  }
}
