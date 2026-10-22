# Phase 1 Data Model: Required-Pace & Linear Cycle Weight Targets

## Domain Value Objects (`FatLossAnalytics.kt`)

### 1. `PaceTargetsState`
Sealed interface representing the container-level state of the `PACE & LINEAR TARGETS` panel:
- **`Unavailable`**:
  - Emitted when active in-cycle weigh-ins are empty, `startWeightKg == null`, `targetWeightKg == null`, `targetWeightKg <= 0.0`, `targetWeightKg >= startWeightKg`, `cycleEndDate == null`, or `ChronoUnit.DAYS.between(cycleStartDate, cycleEndDate) <= 0`.
- **`GoalReached`**:
  - Emitted when `round1InUnit(today7dAvgKg, weightUnit) <= round1InUnit(targetWeightKg, weightUnit)`.
- **`CycleEnded`**:
  - Emitted when `!referenceDate.isBefore(cycleEndDate)` (i.e., `referenceDate >= cycleEndDate`) and goal is not yet reached.
- **`Available(val sevenDayAvgRow: TargetRowState, val dailyWeightRow: TargetRowState)`**:
  - Convenience properties for backward compatibility:
    - `val today: ColumnState get() = dailyWeightRow.expectedToday`
    - `val tomorrow: ColumnState get() = dailyWeightRow.targetTomorrow`

### 2. `TargetRowState`
Data class representing one horizontal measurement row (`7D ROLLING AVERAGE` or `DAILY WEIGH-IN`) across the 3 target columns:
- `linearToday: ColumnState` — Theoretical linear weight for `referenceDate` (`W_start + (W_target - W_start) * elapsedDays / totalCycleDays`) and delta vs today's actual value for that row.
- `expectedToday: ColumnState` — Required-pace expected weight for `referenceDate` anchored to `t - 7d` (`W_{t-7d} + weeklyRequiredRate`) and delta vs today's actual value for that row.
- `targetTomorrow: ColumnState` — Required-pace target weight for `referenceDate + 1d` anchored to today's actual weight (`W_today + dailyRequiredRate`) and daily step (`dailyRequiredRate`).

### 3. `ColumnState`
Sealed interface representing an individual target cell in `TargetRowState`:
- **`Value`**:
  - `targetWeightDisplay: Double` (rounded to 2 decimal places via `round2`)
  - `deltaDisplay: Double?` (for `Linear Today` and `Expected Today`: `diff2(round2(actualInUnit), targetWeightDisplay)`; for `Target Tomorrow`: `round2(dailyRequiredRateInUnit)`)
  - `status: PaceTargetStatus?` (`AHEAD` when `deltaDisplay < 0.00`, `OFF_PACE` when `deltaDisplay > 0.00`, `ON_PACE` when `deltaDisplay == 0.00`; `null` for `Target Tomorrow` or when today's actual daily weight is unlogged)
  - `unitLabel: String` (`"kg"` or `"lbs"`)
  - `baselineWeightDisplay: Double?` (optional 2-decimal baseline weight from `t - 7d` or `startWeight` for accessibility/context)
  - `isDailyStep: Boolean = false` (`true` for `Target Tomorrow` so the subtitle formats as `"-X.XX <unit>/day"`)
- **`NeedsLog(val requiredDate: LocalDate)`**:
  - Emitted for `Expected Today` when `referenceDate.minusDays(7)` is on or after `cycleStartDate` and no weigh-in exists for that anchor.
- **`NeedsTodayLog`**:
  - Emitted for Daily `Target Tomorrow` when `referenceDate` has no raw weigh-in logged yet (`"Needs today's log"`).
- **`InsufficientHistory`**:
  - Emitted for `Expected Today` when `referenceDate.minusDays(7)` is before `cycleStartDate` and no pre-cycle lookback weigh-in exists for that anchor (`"Needs 7 days history"`).

### 4. `PaceTargetStatus`
Enum representing comparison status against `Linear Today` or `Expected Today`:
- `AHEAD`: Actual weight is strictly below target (`deltaDisplay < 0.00`)
- `ON_PACE`: Actual weight equals target at 2-decimal precision (`deltaDisplay == 0.00`)
- `OFF_PACE`: Actual weight is strictly above target (`deltaDisplay > 0.00`)
