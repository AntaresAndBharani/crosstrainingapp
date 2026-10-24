# Analytics & UI Contract: Multi-Window Rolling Average Scale Targets

## 1. Domain Calculation Contract (`FatLossAnalytics.compute7DayPaceTargets`)

### Inputs
- `entries: List<WeightEntry>`
- `cycleStartDate: LocalDate`
- `referenceDate: LocalDate` (`t`)
- `weightUnit: String` (`"kg"` or `"lbs"`)
- `startingWeightKg: Double?`
- `targetWeightKg: Double?`
- `cycleEndDate: LocalDate?`
- `requireCycleTargetAndEnd: Boolean`

### Container State Precedence
1. `PaceTargetsState.Unavailable`: `activeEntries.isEmpty()`, `referenceDate < cycleStartDate`, `targetWeightKg == null || targetWeightKg <= 0.0 || targetWeightKg >= startKg`, `cycleEndDate == null`, or `totalCycleDays <= 0`.
2. `PaceTargetsState.GoalReached`: `round1InUnit(today7dAvgKg, weightUnit) <= round1InUnit(targetWeightKg, weightUnit)`.
3. `PaceTargetsState.CycleEnded`: `daysRemaining <= 0` (`referenceDate >= cycleEndDate`).
4. `PaceTargetsState.Available`: Evaluates `TargetRowState` for each `windowDays` $N \in \{7, 14, 21, 28\}$.

### Per-Horizon Row Formula ($N \in \{7, 14, 21, 28\}$)
1. **Current $N\text{D}$ Average**:
   - `todayNdAvgKg = computeWindowAverageKg(allActiveEntries, referenceDate.minusDays(N - 1L), referenceDate) ?: activeEntries.last().weightKg`
   - `currentNdInUnit = toUnit(todayNdAvgKg, weightUnit)`
   - `currentNdDisplay = round2(currentNdInUnit)`
2. **Horizon Required Pace**:
   - `remainingInUnit = currentNdInUnit - targetInUnit`
   - `dailyRequiredRateInUnit = -remainingInUnit / daysRemaining.toDouble()`
   - `windowRequiredRateInUnit = dailyRequiredRateInUnit * N.toDouble()`
   - `dailyStepDisplay = -abs(round2(dailyRequiredRateInUnit))`
3. **`linearToday` Cell**:
   - `deltaNdLinear = diff2(currentNdDisplay, wLinearDisplay)`
   - `ColumnState.Value(targetWeightDisplay = wLinearDisplay, deltaDisplay = deltaNdLinear, status = paceStatusFor(deltaNdLinear), unitLabel = unitLabel, useTwoDecimals = true)`
4. **`expectedToday` Cell**:
   - `priorNdAvgKg = computeWindowAverageKg(allActiveEntries, referenceDate.minusDays(2L * N - 1L), referenceDate.minusDays(N.toLong()))`
   - If `priorNdAvgKg == null`:
     - If `referenceDate.minusDays(N.toLong()).isBefore(cycleStartDate)` -> `ColumnState.InsufficientHistory`
     - Else -> `ColumnState.NeedsLog(requiredDate = referenceDate.minusDays(N.toLong()))`
   - Else:
     - `priorNdInUnit = toUnit(priorNdAvgKg, weightUnit)`
     - `targetAvgTodayInUnit = priorNdInUnit + windowRequiredRateInUnit`
     - `priorTodayEntries = allActiveEntries.filter { !it.date.isBefore(referenceDate.minusDays(N - 1L)) && !it.date.isAfter(referenceDate.minusDays(1L)) }`
     - `kToday = priorTodayEntries.size`
     - `sumPriorTodayInUnit = priorTodayEntries.sumOf { toUnit(it.weightKg, weightUnit) }`
     - `expectedScaleTodayDisplay = round2((kToday + 1) * targetAvgTodayInUnit - sumPriorTodayInUnit)`
     - If `entryToday != null`:
       - `todayDailyDisplay = round2(toUnit(entryToday.weightKg, weightUnit))`
       - `deltaExpected = diff2(todayDailyDisplay, expectedScaleTodayDisplay)`
       - `status = paceStatusFor(deltaExpected)`
     - Else:
       - `deltaExpected = null`, `status = null`
5. **`targetTomorrow` Cell**:
   - `targetAvgTomorrowInUnit = currentNdInUnit + dailyRequiredRateInUnit`
   - `priorTomorrowEntries = allActiveEntries.filter { !it.date.isBefore(referenceDate.minusDays(N - 2L)) && !it.date.isAfter(referenceDate) }`
   - `kTomorrow = priorTomorrowEntries.size`
   - `sumPriorTomorrowInUnit = priorTomorrowEntries.sumOf { toUnit(it.weightKg, weightUnit) }`
   - `targetScaleTomorrowDisplay = round2((kTomorrow + 1) * targetAvgTomorrowInUnit - sumPriorTomorrowInUnit)`
   - `ColumnState.Value(targetWeightDisplay = targetScaleTomorrowDisplay, deltaDisplay = dailyStepDisplay, status = null, unitLabel = unitLabel, baselineWeightDisplay = currentNdDisplay, isDailyStep = true, useTwoDecimals = true)`

## 2. UI Presentation Contract (`ProgressScreen.kt`)

- Section Header: `"PACE & LINEAR TARGETS"`
- Row Headers (in order):
  - `"7D ROLLING AVERAGE"` (`TARGET_ROW_7D_AVG_HEADER`)
  - `"14D ROLLING AVERAGE"` (`TARGET_ROW_14D_AVG_HEADER`)
  - `"21D ROLLING AVERAGE"` (`TARGET_ROW_21D_AVG_HEADER`)
  - `"28D ROLLING AVERAGE"` (`TARGET_ROW_28D_AVG_HEADER`)
- Removed from `TheoreticalPaceTargetsBox`: `"DAILY WEIGH-IN"` row.
- `resolveExpectedTodaySubtitle(column: ColumnState, windowDays: Int = 7)` and `resolveTargetTomorrowSubtitle(column: ColumnState, windowDays: Int = 7)` return `"Needs ${windowDays} days history"` when `column is ColumnState.InsufficientHistory`.
