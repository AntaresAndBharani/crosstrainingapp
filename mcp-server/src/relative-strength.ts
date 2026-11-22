import type { WeightEntryRecord } from "./store.js";

export const LBS_PER_KG = 2.20462262185;
export const MAX_LOOKBACK_DAYS = 7;

export type BodyWeightMatchKind =
  | "EXACT"
  | "ESTIMATED_PRIOR"
  | "TARGET_CYCLE_BW"
  | "LATEST_BW"
  | "UNAVAILABLE";

export interface BodyWeightResolution {
  kind: BodyWeightMatchKind;
  weightKg: number | null;
  sourceDate: string | null;
  daysPrior: number | null;
  isEstimated: boolean;
}

export interface ActiveWeightPoint {
  epochDay: number;
  isoDate: string;
  weightKg: number;
}

function localIsDeleted(deletedAtMillis?: number | null): boolean {
  return typeof deletedAtMillis === "number" && deletedAtMillis > 0;
}

function localIsoToEpochDay(isoDate: string): number | null {
  const ms = Date.parse(`${isoDate}T00:00:00Z`);
  if (Number.isNaN(ms)) return null;
  return Math.floor(ms / 86_400_000);
}

function localEpochDayToIso(epochDay: number): string {
  return new Date(epochDay * 86_400_000).toISOString().slice(0, 10);
}

/**
 * Rounds a finite numeric value `HALF_UP` to 1 decimal place matching Android `BigDecimal.HALF_UP`.
 */
export function roundHalfUp1(value: number): number {
  if (!Number.isFinite(value)) return 0;
  const sign = value < 0 ? -1 : 1;
  const absScaled = Math.floor(Math.abs(value) * 10 + 0.5 + 1e-12);
  const res = (sign * absScaled) / 10;
  return res === 0 ? 0 : res;
}

/**
 * True only for mass units (`kg`, `kgs`, `lb`, `lbs`).
 */
export function isWeightedUnit(unit?: string | null): boolean {
  const norm = (unit ?? "").trim().toLowerCase();
  return norm === "kg" || norm === "kgs" || norm === "lb" || norm === "lbs";
}

/**
 * Converts a lifted weight from `unit` (`kg`, `lb`, `lbs`) into kilograms.
 */
export function toWeightKg(value: number, unit?: string | null): number {
  const norm = (unit ?? "kg").trim().toLowerCase();
  if (norm === "lb" || norm === "lbs") {
    return value / LBS_PER_KG;
  }
  return value;
}

/**
 * Filters active (`!isDeleted` and `weightKg > 0`) weight entries sorted ascending by epoch day.
 */
export function buildSortedActiveWeightEntries(entries: WeightEntryRecord[]): ActiveWeightPoint[] {
  const byDay = new Map<number, number>();
  for (const e of entries) {
    if (localIsDeleted(e.deletedAtMillis)) continue;
    const day = Number(e.date);
    const kg = Number(e.weightKg);
    if (!Number.isFinite(day) || !Number.isFinite(kg) || kg <= 0) continue;
    byDay.set(day, kg);
  }
  return Array.from(byDay.entries())
    .sort((a, b) => a[0] - b[0])
    .map(([epochDay, weightKg]) => ({
      epochDay,
      isoDate: localEpochDayToIso(epochDay),
      weightKg,
    }));
}

/**
 * Resolves the athlete's body weight for `isoDate`:
 * 1) Exact match on `isoDate` -> `EXACT`
 * 2) Latest active entry within `1..maxLookbackDays` prior -> `ESTIMATED_PRIOR`
 * 3) Otherwise -> `UNAVAILABLE`
 */
export function resolveBodyWeightForIsoDate(
  isoDate: string,
  sortedWeights: ActiveWeightPoint[],
  maxLookbackDays: number = MAX_LOOKBACK_DAYS
): BodyWeightResolution {
  if (!isoDate || sortedWeights.length === 0) {
    return { kind: "UNAVAILABLE", weightKg: null, sourceDate: null, daysPrior: null, isEstimated: false };
  }
  const targetDay = localIsoToEpochDay(isoDate);
  if (targetDay === null) {
    return { kind: "UNAVAILABLE", weightKg: null, sourceDate: null, daysPrior: null, isEstimated: false };
  }

  let best: ActiveWeightPoint | null = null;
  for (const entry of sortedWeights) {
    if (entry.epochDay > targetDay) break;
    best = entry;
  }

  if (!best) {
    return { kind: "UNAVAILABLE", weightKg: null, sourceDate: null, daysPrior: null, isEstimated: false };
  }
  const diff = targetDay - best.epochDay;
  if (diff === 0) {
    return {
      kind: "EXACT",
      weightKg: best.weightKg,
      sourceDate: best.isoDate,
      daysPrior: 0,
      isEstimated: false,
    };
  }
  if (diff >= 1 && diff <= maxLookbackDays) {
    return {
      kind: "ESTIMATED_PRIOR",
      weightKg: best.weightKg,
      sourceDate: best.isoDate,
      daysPrior: diff,
      isEstimated: true,
    };
  }
  return { kind: "UNAVAILABLE", weightKg: null, sourceDate: null, daysPrior: null, isEstimated: false };
}

/**
 * Resolves the latest active logged body weight across all entries (`LATEST_BW` or `UNAVAILABLE`).
 */
export function resolveLatestActiveBodyWeight(sortedWeights: ActiveWeightPoint[]): BodyWeightResolution {
  if (sortedWeights.length === 0) {
    return { kind: "UNAVAILABLE", weightKg: null, sourceDate: null, daysPrior: null, isEstimated: false };
  }
  const latest = sortedWeights[sortedWeights.length - 1];
  return {
    kind: "LATEST_BW",
    weightKg: latest.weightKg,
    sourceDate: latest.isoDate,
    daysPrior: null,
    isEstimated: true,
  };
}

/**
 * Computes `(liftedWeightKg / bodyWeightKg) * 100` rounded `HALF_UP` to 1 decimal place.
 */
export function computePercentBw(
  liftedWeight: number | null | undefined,
  unit: string | null | undefined,
  bodyWeightKg: number | null | undefined
): number | null {
  if (!isWeightedUnit(unit)) return null;
  if (liftedWeight == null || !Number.isFinite(liftedWeight) || liftedWeight <= 0) return null;
  if (bodyWeightKg == null || !Number.isFinite(bodyWeightKg) || bodyWeightKg <= 0) return null;
  const liftedKg = toWeightKg(liftedWeight, unit);
  if (!Number.isFinite(liftedKg) || liftedKg <= 0) return null;
  return roundHalfUp1((liftedKg * 100) / bodyWeightKg);
}

/**
 * Computes reconciled 1-decimal delta `latestPct - prevPct` when both values are present.
 */
export function computePercentBwDelta(
  latestPct: number | null | undefined,
  prevPct: number | null | undefined
): number | null {
  if (latestPct == null || prevPct == null) return null;
  if (!Number.isFinite(latestPct) || !Number.isFinite(prevPct)) return null;
  return roundHalfUp1(latestPct - prevPct);
}
