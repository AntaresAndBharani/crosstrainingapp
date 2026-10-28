# Analytics & UI Contract: Pace & Linear Targets (`Expected Today` & `Target Tomorrow`)

## 1. Pure Domain Function Contract

### Signature
```kotlin
internal fun computeRollingWindowTargetRow(
    horizon: TargetHorizon,
    allActiveEntries: List<DailyWeightPoint>,
    referenceDate: LocalDate,
    dailyPaceRateKg: Double?,
    dailyLinearRateKg: Double?,
    weightUnit: WeightUnit
): RollingWindowTargetRow
```

### Preconditions & Guards
- `allActiveEntries` contains active, non-deleted daily weight points sorted by date.
- `horizon.days` $N \in \{7, 14, 21, 28\}$.
- If `allActiveEntries` is empty or `allActiveEntries.minOf { it.date } > referenceDate.minusDays(N)` $\implies$ both `paceStatus` and `linearStatus` are `WindowCalculationStatus.InsufficientHistory`.
- If `allActiveEntries.minOf { it.date } <= referenceDate.minusDays(N)` and no entry exists in $[t - 2N + 1, t - N]$ $\implies$ both `paceStatus` and `linearStatus` are `WindowCalculationStatus.NeedsLog(requiredDate = referenceDate.minusDays(N))`.

### Postconditions (`WindowCalculationStatus.Ready`)
For each mode (`Pace` with `dailyPaceRateKg`, `Linear` with `dailyLinearRateKg`):
1. `yesterdayNdAvgKg = computeWindowAverageKg(allActiveEntries, referenceDate.minusDays(N), referenceDate.minusDays(1)) ?: priorLookbackAvgKg`
2. `yesterdayNdInUnit = toUnit(yesterdayNdAvgKg, weightUnit)`
3. `dailyRequiredRateInUnit = toUnit(dailyRequiredRateKg, weightUnit)`
4. `targetAvgTodayInUnit = yesterdayNdInUnit + dailyRequiredRateInUnit`
5. Let `priorTodayEntries` be entries in $[t - (N - 1), t - 1]$, $k_{\text{today}} = \text{priorTodayEntries.size}$, and $\text{sumPriorTodayInUnit} = \sum \operatorname{toUnit}(w, \text{weightUnit})$.
6. `expectedScaleTodayDisplay = round2((kToday + 1) * targetAvgTodayInUnit - sumPriorTodayInUnit)`
7. `baselineNdDisplay = round2(yesterdayNdInUnit)`
8. `targetScaleTomorrowDisplay` remains `round2((kTomorrow + 1) * (currentNdInUnit + dailyRequiredRateInUnit) - sumPriorTomorrowInUnit)` where `currentNdInUnit` and `priorTomorrowEntries` span $[t - (N - 1), t]$.

## 2. UI Rendering Contract (`ProgressScreen.kt`)

- The **Pace & Linear Targets** card (`pace_and_linear_targets_container`) renders 4 horizon rows (`7D`, `14D`, `21D`, `28D`) in a `2x2` grid (`pace_and_linear_targets_row_7`, `pace_and_linear_targets_row_14`, `pace_and_linear_targets_row_21`, `pace_and_linear_targets_row_28`).
- Each cell displays:
  - `Expected Today`: formatted to 2 decimal places with unit (`%.2f %s`) from `Ready.expectedScaleTodayDisplay`.
  - `Target Tomorrow`: formatted to 2 decimal places with unit (`%.2f %s`) from `Ready.targetScaleTomorrowDisplay`.
- No layout, typography, or test-tag changes are made to `ProgressScreen.kt`.
