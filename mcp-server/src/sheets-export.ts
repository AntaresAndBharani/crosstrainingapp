import {
  AVAILABLE_BLOCK_FORMAT,
  type CanonicalWeightSubcycleType,
  computeDailyWeightSeries,
  computeWeightMomentumAnalytics,
  type DailyWeightAnalyticsRow,
  decodeLogMetricHeader,
  isDailyRoutineFormat,
  normalizeWeightSubcycleType,
  parseCycleGoalMetadata,
  resolveSessionBlockMetric,
  routineMetricCompactUnit,
  STANDALONE_EXERCISE_SESSION_FORMAT,
  SUBCYCLE_CORRIDORS,
  type WeightSubcycleType,
} from "./schemas.js";
import {
  type AppEnv,
  computeCyclesFromRaw,
  type CycleGoalRecord,
  type DailyLogRecord,
  epochDayToIsoDate,
  type ExerciseRecord,
  isDeletedTombstone,
  type RawDocumentBackingStore,
  type RepMaxRecord,
  type RoutineWithBlocksRecord,
  type SessionWithBlocksRecord,
  type UserContext,
  type WeightEntryRecord,
} from "./store.js";

export interface SheetsExportOptions {
  subcycleType?: WeightSubcycleType;
  cycleStartDate?: string;
  cycleFinishDate?: string;
  asOfDate?: string;
}

export interface SheetsActiveCycleSummary {
  cycle_id: number;
  cycle_name: string;
  subcycle_type: CanonicalWeightSubcycleType;
  subcycle_label: string;
  start_date: string;
  finish_date: string;
  target_min_pct_wk: number;
  target_max_pct_wk: number;
  current_composite_kg: number | null;
  weekly_momentum_pct: number | null;
  status: string;
  estimated_finish_momentum_kg: number | null;
  estimated_finish_pace_kg: number | null;
}

export interface SheetsSessionSetRow {
  date: string;
  session_id: number;
  session_title: string;
  routine_title: string;
  item_type: "COMPLEX" | "EXERCISE";
  item_title: string;
  exercise_name: string;
  set_index: number;
  reps: number;
  weight_kg: number | null;
  metric_value: number | null;
  unit: "kg" | "reps" | "cal" | "s" | "m";
  is_warmup: boolean;
  is_failed: boolean;
  cycle_id: number;
  cycle_name: string;
}

export interface SheetsPrRow {
  date: string;
  movement_name: string;
  rm_reps: number;
  weight_kg: number;
  source_complex_title: string;
  session_title: string;
  cycle_id: number | null;
  cycle_name: string;
}

export interface SheetsCycleRow {
  cycle_id: number;
  cycle_name: string;
  is_active: boolean;
  subcycle_type: CanonicalWeightSubcycleType;
  start_date: string | null;
  finish_date: string | null;
  target_min_pct_wk: number;
  target_max_pct_wk: number;
  goals_count: number;
  sessions_count: number;
  rep_maxes_count: number;
  latest_activity_date: string | null;
}

export interface SheetsCycleGoalRow {
  goal_id: number;
  cycle_id: number;
  cycle_name: string;
  is_active_cycle: boolean;
  exercise_name: string;
  goal_metric: "WEIGHT" | "CALORIES" | "REPS";
  target_reps: number;
  time_window_minutes: number | null;
  start_value: number;
  target_value: number;
  current_best_value: number | null;
  progress_pct: number | null;
  notes: string;
}

export interface SheetsDailyLogRow {
  date: string;
  fast_completed: boolean | null;
  is_rest_day: boolean;
  calories_kcal: number | null;
  protein_g: number | null;
  carbs_g: number | null;
  fat_g: number | null;
  notes: string;
}

export interface SheetsExportPayload {
  generated_at: string;
  env: AppEnv;
  active_cycle: SheetsActiveCycleSummary;
  weights: DailyWeightAnalyticsRow[];
  sessions_sets: SheetsSessionSetRow[];
  prs: SheetsPrRow[];
  cycles: SheetsCycleRow[];
  cycle_goals: SheetsCycleGoalRow[];
  daily_logs: SheetsDailyLogRow[];
}

function round2(val: number): number {
  return Math.round(val * 100) / 100;
}

/**
 * Infers the weight-momentum subcycle corridor from a cycle's human-readable name when no
 * explicit `subcycleType` query/env override is supplied.
 */
export function inferSubcycleTypeFromCycleName(
  cycleName?: string
): CanonicalWeightSubcycleType {
  const lower = (cycleName ?? "").trim().toLowerCase();
  if (!lower) return "RECOMPOSITION";
  if (/(fat\s*loss|weight\s*loss|\bcut\b|deficit)/i.test(lower)) {
    return "FAT_LOSS";
  }
  if (/(muscle\s*gain|weight\s*gain|\bbulk\b|surplus|hypertrophy)/i.test(lower)) {
    return "MUSCLE_GAIN";
  }
  return "RECOMPOSITION";
}

/**
 * Strictly read-only aggregation across all 7 Firestore collections for Google Sheets
 * prototyping and Gemini in Sheets dashboarding. Never calls `writeCollection` or mutations.
 */
export async function buildSheetsExport(
  backingStore: RawDocumentBackingStore,
  ctx: UserContext,
  options: SheetsExportOptions = {}
): Promise<SheetsExportPayload> {
  const [
    rawExercises,
    rawRoutines,
    rawSessions,
    rawGoals,
    rawRepMaxes,
    rawWeights,
    rawDailyLogs,
  ] = await Promise.all([
    backingStore.readCollection<ExerciseRecord>(ctx, "exercises"),
    backingStore.readCollection<RoutineWithBlocksRecord>(ctx, "routines"),
    backingStore.readCollection<SessionWithBlocksRecord>(ctx, "sessions"),
    backingStore.readCollection<CycleGoalRecord>(ctx, "cycle_goals"),
    backingStore.readCollection<RepMaxRecord>(ctx, "rep_maxes"),
    backingStore.readCollection<WeightEntryRecord>(ctx, "weight_entries"),
    backingStore.readCollection<DailyLogRecord>(ctx, "daily_logs"),
  ]);

  const exercisesById = new Map<number, ExerciseRecord>(
    rawExercises.map((e) => [Number(e.id), e])
  );

  const activeRoutines = rawRoutines.filter(
    (r) => !isDeletedTombstone(r.routine?.deletedAtMillis)
  );
  const dailyRoutineTitlesByLower = new Map<string, string>();
  const complexById = new Map<number, { id: number; title: string }>();
  const complexByLowerTitle = new Map<string, { id: number; title: string }>();

  for (const r of activeRoutines) {
    const title = (r.routine?.name ?? "").trim();
    if (!title) continue;
    const fmt = (r.routine?.defaultFormat ?? "").trim().toUpperCase();
    if (isDailyRoutineFormat(fmt)) {
      dailyRoutineTitlesByLower.set(title.toLowerCase(), title);
    } else if (fmt === AVAILABLE_BLOCK_FORMAT) {
      const entry = { id: Number(r.routine.id), title };
      complexById.set(entry.id, entry);
      complexByLowerTitle.set(title.toLowerCase(), entry);
    }
  }

  const activeSessions = rawSessions
    .filter((s) => !isDeletedTombstone(s.session?.deletedAtMillis))
    .sort((a, b) => {
      const dateCmp = (a.session?.date ?? "").localeCompare(b.session?.date ?? "");
      return dateCmp !== 0
        ? dateCmp
        : (Number(a.session?.id) || 0) - (Number(b.session?.id) || 0);
    });

  const activeRepMaxes = rawRepMaxes.filter(
    (rm) => !isDeletedTombstone(rm.deletedAtMillis) && Number(rm.weight) > 0
  );

  const discoveredCycles = computeCyclesFromRaw(
    rawGoals,
    rawSessions,
    rawRepMaxes
  );
  const activeCycle =
    discoveredCycles.find((c) => c.isActive) ?? discoveredCycles[0];
  const cycleNameById = new Map<number, string>(
    discoveredCycles.map((c) => [c.cycleId, c.cycleName])
  );

  // Earliest & latest activity date per cycle from active sessions and rep maxes
  const cycleEarliestDate = new Map<number, string>();
  const cycleLatestDate = new Map<number, string>();
  const recordCycleDate = (cycleId: number | null | undefined, dateStr?: string) => {
    const id = Number(cycleId);
    if (!Number.isFinite(id) || id < 1 || !dateStr) return;
    const prevMin = cycleEarliestDate.get(id);
    if (!prevMin || dateStr < prevMin) cycleEarliestDate.set(id, dateStr);
    const prevMax = cycleLatestDate.get(id);
    if (!prevMax || dateStr > prevMax) cycleLatestDate.set(id, dateStr);
  };

  for (const sw of activeSessions) {
    recordCycleDate(sw.session?.cycleId, sw.session?.date);
  }
  for (const rm of activeRepMaxes) {
    recordCycleDate(rm.cycleId, rm.date);
  }

  // Weight analytics
  const activeWeights = rawWeights
    .filter(
      (w) =>
        !isDeletedTombstone(w.deletedAtMillis) &&
        Number.isFinite(Number(w.date)) &&
        Number(w.weightKg) > 0
    )
    .map((w) => {
      const iso = epochDayToIsoDate(Number(w.date));
      return {
        date: iso,
        isoDate: iso,
        weightKg: Number(w.weightKg),
        notes: typeof w.notes === "string" ? w.notes : "",
      };
    })
    .sort((a, b) => a.isoDate.localeCompare(b.isoDate));

  const effectiveSubcycleType: CanonicalWeightSubcycleType = options.subcycleType
    ? normalizeWeightSubcycleType(options.subcycleType)
    : inferSubcycleTypeFromCycleName(activeCycle.cycleName);

  const firstWeighInDate = activeWeights[0]?.isoDate;
  const activeCycleStartDate =
    options.cycleStartDate ??
    cycleEarliestDate.get(activeCycle.cycleId) ??
    firstWeighInDate;

  const momentumAnalytics = computeWeightMomentumAnalytics(activeWeights, {
    subcycleType: effectiveSubcycleType,
    cycleName: activeCycle.cycleName,
    cycleStartDate: activeCycleStartDate,
    cycleFinishDate: options.cycleFinishDate,
    asOfDate: options.asOfDate,
  });

  const weights = computeDailyWeightSeries(activeWeights, {
    subcycleType: effectiveSubcycleType,
    asOfDate: options.asOfDate,
  });

  const activeCycleCorridor = SUBCYCLE_CORRIDORS[effectiveSubcycleType];
  const active_cycle: SheetsActiveCycleSummary = {
    cycle_id: activeCycle.cycleId,
    cycle_name: activeCycle.cycleName,
    subcycle_type: effectiveSubcycleType,
    subcycle_label: activeCycleCorridor.label,
    start_date: momentumAnalytics.cycle_metadata.start_date,
    finish_date: momentumAnalytics.cycle_metadata.finish_date,
    target_min_pct_wk: activeCycleCorridor.minPct,
    target_max_pct_wk: activeCycleCorridor.maxPct,
    current_composite_kg:
      momentumAnalytics.weekly_body_weight_composite.current_kg,
    weekly_momentum_pct:
      momentumAnalytics.weekly_body_weight_composite.weekly_momentum_pct,
    status: momentumAnalytics.weekly_body_weight_composite.status,
    estimated_finish_momentum_kg:
      momentumAnalytics.weekly_body_weight_composite
        .estimated_finish_weight_momentum_kg,
    estimated_finish_pace_kg:
      momentumAnalytics.weekly_body_weight_composite
        .estimated_finish_weight_pace_kg,
  };

  // Flatten sessions & sets
  const sessionsById = new Map<number, SessionWithBlocksRecord>(
    activeSessions.map((s) => [Number(s.session.id), s])
  );
  const globalBlocksById = new Map<
    number,
    SessionWithBlocksRecord["blocks"][number]["block"]
  >();
  const sessions_sets: SheetsSessionSetRow[] = [];

  for (const sw of activeSessions) {
    const sess = sw.session;
    const sessionTitle = (sess.title ?? "").trim();
    const cycleId = Number(sess.cycleId) || 0;
    const cycleName =
      (sess.cycleName ?? "").trim() ||
      cycleNameById.get(cycleId) ||
      (cycleId > 0 ? `Cycle ${cycleId}` : "");

    const sortedBlocks = [...(sw.blocks ?? [])].sort(
      (a, b) => (Number(a.block?.position) || 0) - (Number(b.block?.position) || 0)
    );

    for (const entry of sortedBlocks) {
      const blk = entry.block;
      if (!blk) continue;
      globalBlocksById.set(Number(blk.id), blk);

      const blockFormatUpper = (blk.format ?? "").trim().toUpperCase();
      const matchedComplex =
        (blk.routineId ? complexById.get(Number(blk.routineId)) : undefined) ??
        (blk.subBlock?.trim()
          ? complexByLowerTitle.get(blk.subBlock.trim().toLowerCase())
          : undefined) ??
        (blk.routineName?.trim()
          ? complexByLowerTitle.get(blk.routineName.trim().toLowerCase())
          : undefined);

      const subBlockTrim = (blk.subBlock ?? "").trim();
      const blockNameTrim = (blk.name ?? "").trim();
      const isComplex =
        blockFormatUpper !== STANDALONE_EXERCISE_SESSION_FORMAT &&
        (Boolean(matchedComplex) ||
          (blk.kind ?? "").trim().toUpperCase() === "COMPLEX" ||
          (subBlockTrim.length > 0 &&
            subBlockTrim.toLowerCase() !== blockNameTrim.toLowerCase()));

      const itemType: "COMPLEX" | "EXERCISE" = isComplex ? "COMPLEX" : "EXERCISE";

      const dailyTitleFromSession = sessionTitle
        ? dailyRoutineTitlesByLower.get(sessionTitle.toLowerCase())
        : undefined;
      const rawBlockRoutine = (blk.routineName ?? "").trim();
      const blockRoutineIsComplex =
        rawBlockRoutine.length > 0 &&
        (complexByLowerTitle.has(rawBlockRoutine.toLowerCase()) ||
          (blk.routineId !== null &&
            blk.routineId !== undefined &&
            complexById.has(Number(blk.routineId))));

      const routineTitle =
        dailyTitleFromSession ??
        (rawBlockRoutine.length > 0 && !blockRoutineIsComplex
          ? dailyRoutineTitlesByLower.get(rawBlockRoutine.toLowerCase()) ??
            rawBlockRoutine
          : sessionTitle);

      const mainEx =
        blk.mainExerciseId !== null && blk.mainExerciseId !== undefined
          ? exercisesById.get(Number(blk.mainExerciseId))
          : undefined;
      const exerciseName =
        (blk.mainExerciseName ?? "").trim() ||
        mainEx?.name ||
        blockNameTrim ||
        "Exercise";

      const itemTitle =
        itemType === "COMPLEX"
          ? matchedComplex?.title ||
            subBlockTrim ||
            rawBlockRoutine ||
            blockNameTrim ||
            exerciseName
          : exerciseName;

      const orderedSets = [...(entry.sets ?? [])].sort(
        (a, b) => (Number(a.position) || 0) - (Number(b.position) || 0)
      );
      const isSingleBlankDefault =
        orderedSets.length === 1 &&
        Number(orderedSets[0].weight) <= 0 &&
        Number(orderedSets[0].metricValue) <= 0 &&
        Number(orderedSets[0].reps) <= 1 &&
        decodeLogMetricHeader(blk.notes) !== "REPS";

      if (isSingleBlankDefault) continue;

      for (let idx = 0; idx < orderedSets.length; idx++) {
        const st = orderedSets[idx];
        const rawWeight = Number(st.weight) || 0;
        const rawMetric = Number(st.metricValue) || 0;
        const rawReps = Math.trunc(Number(st.reps) || 0);
        if (rawWeight <= 0 && rawMetric <= 0 && rawReps <= 0) continue;

        const setIndex =
          Number(st.groupIndex) > 0 ? Math.trunc(Number(st.groupIndex)) : idx + 1;
        const weightKg = rawWeight > 0 ? round2(rawWeight) : null;
        const metricValue = rawMetric > 0 ? round2(rawMetric) : null;

        let unit: "kg" | "reps" | "cal" | "s" | "m" = "reps";
        if (weightKg !== null) {
          unit = "kg";
        } else if (metricValue !== null) {
          unit = routineMetricCompactUnit(
            resolveSessionBlockMetric(blk.notes, [st], mainEx?.metricType)
          );
        }

        sessions_sets.push({
          date: sess.date,
          session_id: Number(sess.id),
          session_title: sessionTitle,
          routine_title: routineTitle,
          item_type: itemType,
          item_title: itemTitle,
          exercise_name: exerciseName,
          set_index: setIndex,
          reps: Math.max(0, rawReps),
          weight_kg: weightKg,
          metric_value: metricValue,
          unit,
          is_warmup: Boolean(st.isWarmup),
          is_failed: Boolean(st.isFailed),
          cycle_id: cycleId,
          cycle_name: cycleName,
        });
      }
    }
  }

  // Flatten RepMax PRs
  const prs: SheetsPrRow[] = activeRepMaxes
    .map((rm) => {
      const movementName =
        (rm.exerciseName ?? "").trim() ||
        exercisesById.get(Number(rm.exerciseId))?.name ||
        "Unknown";
      const matchedSession = rm.sessionId
        ? sessionsById.get(Number(rm.sessionId))
        : undefined;
      const matchedBlock =
        matchedSession?.blocks?.find(
          (b) => Number(b.block?.id) === Number(rm.blockId)
        )?.block ??
        (rm.blockId ? globalBlocksById.get(Number(rm.blockId)) : undefined);

      const sourceComplexTitle =
        (rm.sourceBlockTitle ?? "").trim() ||
        (matchedBlock
          ? (matchedBlock.subBlock ?? "").trim() ||
            (matchedBlock.routineId
              ? complexById.get(Number(matchedBlock.routineId))?.title
              : undefined) ||
            (matchedBlock.routineName ?? "").trim() ||
            (matchedBlock.name ?? "").trim()
          : "");

      const sessionTitle =
        (rm.sessionTitle ?? "").trim() ||
        (matchedSession?.session.title ?? "").trim();

      const cid = rm.cycleId ?? null;
      const cname =
        (rm.cycleName ?? "").trim() ||
        (cid !== null ? cycleNameById.get(cid) ?? `Cycle ${cid}` : "");

      return {
        date: rm.date,
        movement_name: movementName,
        rm_reps: Math.trunc(Number(rm.reps) || 1),
        weight_kg: round2(Number(rm.weight)),
        source_complex_title: sourceComplexTitle,
        session_title: sessionTitle,
        cycle_id: cid,
        cycle_name: cname,
      };
    })
    .sort((a, b) => {
      const dCmp = a.date.localeCompare(b.date);
      if (dCmp !== 0) return dCmp;
      const nCmp = a.movement_name.localeCompare(b.movement_name);
      return nCmp !== 0 ? nCmp : a.rm_reps - b.rm_reps;
    });

  // Flatten Cycles
  const cycles: SheetsCycleRow[] = [...discoveredCycles]
    .sort((a, b) => a.cycleId - b.cycleId)
    .map((c) => {
      const isAct = c.cycleId === activeCycle.cycleId;
      const subType = isAct
        ? effectiveSubcycleType
        : inferSubcycleTypeFromCycleName(c.cycleName);
      const corridor = SUBCYCLE_CORRIDORS[subType];
      return {
        cycle_id: c.cycleId,
        cycle_name: c.cycleName,
        is_active: isAct,
        subcycle_type: subType,
        start_date: isAct
          ? momentumAnalytics.cycle_metadata.start_date
          : cycleEarliestDate.get(c.cycleId) ?? null,
        finish_date: isAct
          ? momentumAnalytics.cycle_metadata.finish_date
          : cycleLatestDate.get(c.cycleId) ?? c.latestActivityDate ?? null,
        target_min_pct_wk: corridor.minPct,
        target_max_pct_wk: corridor.maxPct,
        goals_count: c.goalsCount,
        sessions_count: c.sessionsCount,
        rep_maxes_count: c.repMaxesCount,
        latest_activity_date: c.latestActivityDate,
      };
    });

  // Flatten Cycle Goals with RepMax progress join
  const cycle_goals: SheetsCycleGoalRow[] = rawGoals
    .filter((g) => !isDeletedTombstone(g.deletedAtMillis))
    .map((g) => {
      const exName =
        (g.exerciseName ?? "").trim() ||
        exercisesById.get(Number(g.exerciseId))?.name ||
        "Unknown";
      const exNameLower = exName.toLowerCase();
      const parsed = parseCycleGoalMetadata(g.notes);
      const goalCycleId = Number(g.cycleId) || 0;
      const goalCycleName =
        (g.cycleName ?? "").trim() ||
        cycleNameById.get(goalCycleId) ||
        `Cycle ${goalCycleId}`;
      const isActiveGoalCycle = goalCycleId === activeCycle.cycleId;

      let currentBest: number | null = null;
      if (parsed.goalMetric === "WEIGHT") {
        // Parity with Android evaluateCycleGoalProgress(): only PRs logged within the goal's own
        // cycle count toward progress. PRs tagged with another cycleId never leak across cycles;
        // legacy unscoped PRs (cycleId null/0) are attributed by the goal cycle's date window.
        const goalCycleStart = isActiveGoalCycle
          ? active_cycle.start_date
          : cycleEarliestDate.get(goalCycleId);
        const goalCycleEnd = isActiveGoalCycle ? undefined : cycleLatestDate.get(goalCycleId);
        const belongsToGoalCycle = (rm: RepMaxRecord): boolean => {
          const rmCycleId = Number(rm.cycleId);
          if (Number.isFinite(rmCycleId) && rmCycleId >= 1) return rmCycleId === goalCycleId;
          if (!goalCycleStart || !rm.date) return false;
          return rm.date >= goalCycleStart && (!goalCycleEnd || rm.date <= goalCycleEnd);
        };
        const pool = activeRepMaxes.filter(
          (rm) =>
            Number(rm.reps) === Number(g.targetReps) &&
            (Number(rm.exerciseId) === Number(g.exerciseId) ||
              (rm.exerciseName ?? "").trim().toLowerCase() === exNameLower) &&
            belongsToGoalCycle(rm)
        );
        if (pool.length > 0) {
          currentBest = round2(Math.max(...pool.map((rm) => Number(rm.weight))));
        }
      }

      const startVal = round2(Number(g.startWeight) || 0);
      const targetVal = round2(Number(g.targetWeight) || 0);
      const progressPct =
        currentBest !== null && targetVal > startVal
          ? round2(((currentBest - startVal) / (targetVal - startVal)) * 100)
          : null;

      return {
        goal_id: Number(g.id),
        cycle_id: goalCycleId,
        cycle_name: goalCycleName,
        is_active_cycle: isActiveGoalCycle,
        exercise_name: exName,
        goal_metric: parsed.goalMetric,
        target_reps: Math.trunc(Number(g.targetReps) || 0),
        time_window_minutes: parsed.timeWindowMinutes ?? null,
        start_value: startVal,
        target_value: targetVal,
        current_best_value: currentBest,
        progress_pct: progressPct,
        notes: parsed.cleanNotes,
      };
    })
    .sort((a, b) => {
      const actCmp = Number(b.is_active_cycle) - Number(a.is_active_cycle);
      if (actCmp !== 0) return actCmp;
      if (b.cycle_id !== a.cycle_id) return b.cycle_id - a.cycle_id;
      const exCmp = a.exercise_name.localeCompare(b.exercise_name);
      return exCmp !== 0 ? exCmp : a.target_reps - b.target_reps;
    });

  // Flatten Daily Logs
  const daily_logs: SheetsDailyLogRow[] = rawDailyLogs
    .filter(
      (l) => !isDeletedTombstone(l.deletedAtMillis) && Number.isFinite(Number(l.date))
    )
    .map((l) => ({
      date: epochDayToIsoDate(Number(l.date)),
      fast_completed: l.fastCompleted ?? null,
      is_rest_day: Boolean(l.isRestDay),
      calories_kcal: l.caloriesKcal ?? null,
      protein_g: l.proteinGrams ?? null,
      carbs_g: l.carbsGrams ?? null,
      fat_g: l.fatGrams ?? null,
      notes: typeof l.notes === "string" ? l.notes : "",
    }))
    .sort((a, b) => a.date.localeCompare(b.date));

  return {
    generated_at: new Date().toISOString(),
    env: ctx.env,
    active_cycle,
    weights,
    sessions_sets,
    prs,
    cycles,
    cycle_goals,
    daily_logs,
  };
}
