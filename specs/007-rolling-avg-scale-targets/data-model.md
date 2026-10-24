# Data Model: Multi-Window Rolling Average Scale Targets (7D, 14D, 21D, 28D)

## Domain Models (`com.fractanomics.crosstraining.data.analytics`)

### 1. `TargetRowState`
Represents a 3-column target row for a specific rolling average window $N \in \{7, 14, 21, 28\}$.

```kotlin
data class TargetRowState(
    val linearToday: ColumnState,
    val expectedToday: ColumnState,
    val targetTomorrow: ColumnState,
    val windowDays: Int = 7
) {
    val rowHeader: String
        get() = "${windowDays}D ROLLING AVERAGE"
}
```

### 2. `PaceTargetsState.Available`
Represents the active state of the `PACE & LINEAR TARGETS` container across all 4 rolling windows (`7D`, `14D`, `21D`, `28D`).

```kotlin
sealed interface PaceTargetsState {
    data object Unavailable : PaceTargetsState
    data object Stalled : PaceTargetsState
    data object GoalReached : PaceTargetsState
    data object CycleEnded : PaceTargetsState
    data class Available(
        val today: ColumnState = ColumnState.InsufficientHistory,
        val tomorrow: ColumnState = ColumnState.InsufficientHistory,
        val sevenDayAvgRow: TargetRowState = TargetRowState(
            linearToday = ColumnState.InsufficientHistory,
            expectedToday = today,
            targetTomorrow = tomorrow,
            windowDays = 7
        ),
        val fourteenDayAvgRow: TargetRowState = TargetRowState(
            linearToday = ColumnState.InsufficientHistory,
            expectedToday = ColumnState.InsufficientHistory,
            targetTomorrow = ColumnState.InsufficientHistory,
            windowDays = 14
        ),
        val twentyOneDayAvgRow: TargetRowState = TargetRowState(
            linearToday = ColumnState.InsufficientHistory,
            expectedToday = ColumnState.InsufficientHistory,
            targetTomorrow = ColumnState.InsufficientHistory,
            windowDays = 21
        ),
        val twentyEightDayAvgRow: TargetRowState = TargetRowState(
            linearToday = ColumnState.InsufficientHistory,
            expectedToday = ColumnState.InsufficientHistory,
            targetTomorrow = ColumnState.InsufficientHistory,
            windowDays = 28
        )
    ) : PaceTargetsState {
        val rows: List<TargetRowState>
            get() = listOf(
                sevenDayAvgRow,
                fourteenDayAvgRow,
                twentyOneDayAvgRow,
                twentyEightDayAvgRow
            )
    }
}
```

### 3. `ColumnState`
Represents an individual target cell (`Linear Today`, `Expected Today`, or `Target Tomorrow`).

- `ColumnState.Value`:
  - `targetWeightDisplay: Double` — 2-decimal rounded target weight in display unit (`kg` or `lbs`).
    - For `linearToday`: 2-decimal linear cycle target weight (`W_linear_display`).
    - For `expectedToday`: 2-decimal daily scale weight needed today so today's `ND` average equals `targetAvgToday`.
    - For `targetTomorrow`: 2-decimal daily scale weight needed tomorrow so tomorrow's `ND` average equals `targetAvgTomorrow`.
  - `deltaDisplay: Double?` — 2-decimal signed delta:
    - For `linearToday`: `diff2(round2(todayNdAvgInUnit), W_linear_display)`.
    - For `expectedToday`: `diff2(round2(todayDailyInUnit), expectedScaleTodayDisplay)` when today (`t`) is logged, or `null` when today is unlogged.
    - For `targetTomorrow`: `dailyStepDisplay = -abs(round2(dailyRequiredRateInUnit))`.
  - `status: PaceTargetStatus?` — `AHEAD` (`< 0.00`), `ON_PACE` (`== 0.00`), `OFF_PACE` (`> 0.00`), or `null` for `targetTomorrow` / unlogged `expectedToday`.
  - `unitLabel: String` — `"kg"` or `"lbs"`.
  - `baselineWeightDisplay: Double?` — 2-decimal prior `ND` average (`for expectedToday`) or current `ND` average (`for targetTomorrow`).
  - `isDailyStep: Boolean` — `true` for `targetTomorrow`.
  - `useTwoDecimals: Boolean` — `true` for all cells in the cycle-target path.
- `ColumnState.NeedsLog(requiredDate: LocalDate)` — Emitted when `t - N >= cycleStartDate` and `[t - 2N + 1, t - N]` has no entries.
- `ColumnState.InsufficientHistory` — Emitted when `t - N < cycleStartDate` and `[t - 2N + 1, t - N]` has no entries; formatted with `rowState.windowDays` as `"Needs ${windowDays} days history"`.
