# Analytics & UI Resolver Contract: Pace & Linear Targets

## 1. Domain Function Signatures (`FatLossAnalytics.kt`)

```kotlin
fun compute7DayPaceTargets(
    entries: List<WeightEntry>,
    cycleStartDate: LocalDate,
    referenceDate: LocalDate = LocalDate.now(),
    weightUnit: String = "kg",
    startingWeightKg: Double? = null,
    targetWeightKg: Double? = null,
    cycleEndDate: LocalDate? = null
): PaceTargetsState

fun compute7DayPaceTargets(
    entries: List<WeightEntry>,
    cycle: Cycle,
    referenceDate: LocalDate = LocalDate.now(),
    weightUnit: String = "kg"
): PaceTargetsState
```

### Mathematical Invariants
1. **Guard Conditions**:
   - Let `inCycleEntries = getActiveWeightEntries(entries, cycleStartDate, referenceDate)`.
   - Let `startKg = startingWeightKg ?: inCycleEntries.firstOrNull()?.weightKg`.
   - If `inCycleEntries.isEmpty()` or `startKg == null` or `targetWeightKg == null` or `targetWeightKg <= 0.0` or `targetWeightKg >= startKg` or `cycleEndDate == null`: return `PaceTargetsState.Unavailable`.
   - Let `totalCycleDays = ChronoUnit.DAYS.between(cycleStartDate, cycleEndDate)`. If `totalCycleDays <= 0L`: return `PaceTargetsState.Unavailable`.
   - Let `today7dAvgKg = computeCurrent7DayAverageWeight(entries, cycleStartDate, referenceDate) ?: inCycleEntries.last().weightKg`.
   - If `round1InUnit(today7dAvgKg, weightUnit) <= round1InUnit(targetWeightKg, weightUnit)`: return `PaceTargetsState.GoalReached`.
   - Let `daysRemaining = ChronoUnit.DAYS.between(referenceDate, cycleEndDate)`. If `daysRemaining <= 0L`: return `PaceTargetsState.CycleEnded`.
2. **Exact Required Rates (in `weightUnit`)**:
   - `today7dAvgInUnit = toUnit(today7dAvgKg, weightUnit)`
   - `targetInUnit = toUnit(targetWeightKg, weightUnit)`
   - `startInUnit = toUnit(startKg, weightUnit)`
   - `remainingInUnit = today7dAvgInUnit - targetInUnit`
   - `dailyRequiredRateInUnit = -(remainingInUnit / daysRemaining.toDouble())`
   - `weeklyRequiredRateInUnit = dailyRequiredRateInUnit * 7.0`
3. **Linear Today (`W_linear_display`)**:
   - `elapsedDays = ChronoUnit.DAYS.between(cycleStartDate, referenceDate).coerceIn(0L, totalCycleDays)`
   - `wLinearInUnit = startInUnit + (targetInUnit - startInUnit) * (elapsedDays.toDouble() / totalCycleDays.toDouble())`
   - `wLinearDisplay = round2(wLinearInUnit)`

## 2. UI Formatting Resolvers (`ProgressScreen.kt`)

- `formatWeight2Display(value: Double, unitLabel: String): String` -> `String.format(Locale.US, "%.2f %s", value, unitLabel)`
- `resolveTargetCellValue(column: ColumnState): String`:
  - `ColumnState.Value` -> `"%.2f <unit>"`
  - `ColumnState.NeedsLog`, `ColumnState.NeedsTodayLog`, `ColumnState.InsufficientHistory` -> `"—"`
- `resolveTargetCellSubtitle(column: ColumnState): String?`:
  - `ColumnState.InsufficientHistory` -> `"Needs 7 days history"`
  - `ColumnState.NeedsLog` -> `"Needs log from ${column.requiredDate.formatShort()}"`
  - `ColumnState.NeedsTodayLog` -> `"Needs today's log"`
  - `ColumnState.Value`:
    - When `column.isDailyStep && column.deltaDisplay != null`: `String.format(Locale.US, "-%.2f %s/day", kotlin.math.abs(column.deltaDisplay), column.unitLabel)`
    - When `!column.isDailyStep && column.deltaDisplay != null && column.status != null`:
      - `PaceTargetStatus.ON_PACE` -> `"On pace"`
      - `PaceTargetStatus.AHEAD` -> `String.format(Locale.US, "-%.2f %s ahead", kotlin.math.abs(column.deltaDisplay), column.unitLabel)`
      - `PaceTargetStatus.OFF_PACE` -> `String.format(Locale.US, "+%.2f %s off pace", kotlin.math.abs(column.deltaDisplay), column.unitLabel)`
    - Otherwise -> `null`
