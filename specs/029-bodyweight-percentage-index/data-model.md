# Data Model: Body-Weight Percentage Index (`% BW`)

*Zero Room database schema changes (`AppDatabase` stays at `v10`). All structures below are pure in-memory analytics projections or additive optional MCP JSON fields.*

## 1. Android Domain Models (`RelativeStrengthAnalytics.kt`)

```kotlin
enum class BodyWeightMatchKind {
    EXACT,
    ESTIMATED_PRIOR,
    TARGET_CYCLE_BW,
    LATEST_BW,
    UNAVAILABLE
}

@Immutable
data class ResolvedBodyWeight(
    val kind: BodyWeightMatchKind,
    val weightKg: Double?,
    val sourceDate: LocalDate?,
    val daysPrior: Int? = null
) {
    val isEstimated: Boolean
        get() = kind == BodyWeightMatchKind.ESTIMATED_PRIOR || kind == BodyWeightMatchKind.LATEST_BW

    val isAvailable: Boolean
        get() = weightKg != null && weightKg.isFinite() && weightKg > 0.0
}

@Immutable
data class PeakRelativeStrength(
    val percentBw: Double,
    val rawWeight: Double,
    val date: LocalDate,
    val resolvedBodyWeight: ResolvedBodyWeight
)

@Immutable
data class DayRelativeMetrics(
    val date: LocalDate,
    val bodyWeight: ResolvedBodyWeight,
    val topPercentBw: Double?,
    val averagePercentBw: Double?,
    val volumePercentBw: Double?
)
```

## 2. Android Existing Projection Extensions

### `DailyRoutineCodec.kt` (`ConstituentProgressSummary`)
Additive default-valued fields:
- `val isWeightedMetric: Boolean = false`
- `val allTimeTopPercentBw: Double? = null`
- `val allTimeTopPercentBwEstimated: Boolean = false`
- `val peakTopPercentBw: PeakRelativeStrength? = null`
- `val lastTopPercentBw: Double? = null`
- `val lastTopPercentBwEstimated: Boolean = false`
- `val topPercentBwDelta: Double? = null`

### `ProgressScreen.kt` (`EvaluatedCycleGoalProgress`)
Additive default-null fields:
- `val baselineStartDate: LocalDate? = null`
- `val currentBestDate: LocalDate? = null`

## 3. Remote MCP Types (`mcp-server/src/relative-strength.ts` & `store.ts`)

```ts
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
```

### Additive Optional Fields on Existing MCP Response Records
- **`list_sessions` / `get_workout_summary` (`recentActiveCycleSessions`):**
  - On `session`: `bodyWeightKg?: number | null`, `bodyWeightDate?: string | null`, `isBodyWeightEstimated?: boolean`
  - On each block entry (`blocks[i]`): `topWeightKg?: number | null`, `topPercentBw?: number | null`, `avgPercentBw?: number | null`, `volumePercentBw?: number | null`
- **`list_rep_maxes` (`RepMaxRecord`):**
  - `percentBw?: number | null`, `bodyWeightKg?: number | null`, `bodyWeightDate?: string | null`, `isBodyWeightEstimated?: boolean`
- **`list_cycle_goals` / `get_workout_summary` (`activeCycleGoals`):**
  - For `WEIGHT` goals: `startPercentBw?: number | null`, `currentBestWeightKg?: number | null`, `currentBestPercentBw?: number | null`, `goalPercentBw?: number | null`, `goalBodyWeightKg?: number | null`, `goalBodyWeightBasis?: BodyWeightMatchKind`
- **`get_daily_routine_progress` (`DailyRoutineConstituentProgressRecord`):**
  - Constituent summary: `allTimeTopPercentBw?: number | null`, `allTimeTopPercentBwEstimated?: boolean`, `peakTopPercentBw?: number | null`, `peakTopPercentBwDate?: string | null`, `peakTopPercentBwRawKg?: number | null`, `lastTopPercentBw?: number | null`, `lastTopPercentBwEstimated?: boolean`, `lastAveragePercentBw?: number | null`, `lastVolumePercentBw?: number | null`, `topPercentBwDelta?: number | null`, `bodyWeightCoverage?: string | null`
  - `chartPoints[]`: `topPercentBw?: number | null`, `averagePercentBw?: number | null`, `volumePercentBw?: number | null`, `bodyWeightKg?: number | null`, `bodyWeightDate?: string | null`, `isBodyWeightEstimated?: boolean`
  - `recentSessions[]`: `topPercentBw?: number | null`, `bodyWeightKg?: number | null`, `bodyWeightDate?: string | null`, `isBodyWeightEstimated?: boolean`
  - `trackedPrs[]`: `bestPercentBw?: number | null`, `bestDate?: string | null`, `isBodyWeightEstimated?: boolean`
