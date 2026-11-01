# Data Model: Weighted Average Pace Estimation & Goal Arrival Estimations Redesign

**Feature**: `009-weighted-goal-estimations`
**Date**: 2026-11-01

## 1. Constants (`FatLossAnalytics` & `ProgressScreen`)

- `FatLossAnalytics.HORIZON_WEIGHTS: Map<Int, Int> = mapOf(7 to 4, 14 to 3, 21 to 2, 28 to 1)`
- `ProgressScreen.CURRENT_GOAL_AND_TARGET_HEADER = "Current Goal & Target"`
- `ProgressScreen.WEIGHTED_AVERAGE_ROW_TITLE = "Weighted Average"`

## 2. Extended Domain Entities (`FatLossAnalytics.kt`)

### `HorizonEstimationRow`

Represents either one of the 4 historical lookback horizons (`windowDays` in `{7, 14, 21, 28}`, `isWeightedAverage = false`) or the primary Weighted Average estimation (`windowDays = 0`, `isWeightedAverage = true`).

```kotlin
data class HorizonEstimationRow(
    val windowDays: Int,
    val state: HorizonRowState,
    val isWeightedAverage: Boolean = windowDays == 0
) {
    fun formatLeftLabel(): String = if (isWeightedAverage) {
        "Weighted Average"
    } else {
        when (state) {
            is HorizonRowState.Projected -> "${windowDays}D (${state.paceDisplay2})"
            is HorizonRowState.Stalled -> "${windowDays}D (${state.paceDisplay2})"
            is HorizonRowState.NotEnoughData -> "${windowDays}D (—)"
        }
    }

    fun formatRightValue(): String = when (state) {
        is HorizonRowState.Projected -> if (state.isCapped || state.targetDate == null) {
            "> 1 year"
        } else {
            val dateStr = state.targetDate.format(DateTimeFormatter.ofPattern("d MMM yyyy", Locale.US))
            String.format(Locale.US, "%.1f wks · %s", state.weeks, dateStr)
        }
        is HorizonRowState.Stalled -> "Pace stalled"
        is HorizonRowState.NotEnoughData -> if (isWeightedAverage) "Needs 7+ days" else "Needs ${windowDays}+ days"
    }

    /**
     * Formats the parenthetical summary shown on the primary Weighted Average row:
     * - Projected (uncapped): "(Weighted pace of -0.40 kg/wk · 7.7 wks · 25 Dec 2026)"
     * - Projected (capped > 52w): "(Weighted pace of -0.05 kg/wk · > 1 year)"
     * - Stalled: "(Weighted pace of +0.10 kg/wk · Pace stalled)"
     * - NotEnoughData: "(Needs 7+ days)"
     */
    fun formatWeightedSummary(): String = when (state) {
        is HorizonRowState.Projected -> "(Weighted pace of ${state.paceDisplay2} · ${formatRightValue()})"
        is HorizonRowState.Stalled -> "(Weighted pace of ${state.paceDisplay2} · Pace stalled)"
        is HorizonRowState.NotEnoughData -> "(Needs 7+ days)"
    }
}
```

### `MultiHorizonForecastState.Active`

```kotlin
data class Active(
    val remainingWeightInUnit: Double,
    val targetWeightInUnit: Double,
    val weightUnit: String,
    val requiredPace: RequiredPaceResult?,
    val rows: List<HorizonEstimationRow>,
    val weightedAverageRow: HorizonEstimationRow = HorizonEstimationRow(
        windowDays = 0,
        state = HorizonRowState.NotEnoughData(windowDays = 0),
        isWeightedAverage = true
    )
) : MultiHorizonForecastState()
```

## 3. State Transitions & Validation Rules

1. **Horizon Weighting & Re-Normalization**:
   - Available horizons: all `w` in `HORIZON_WINDOWS` (`7, 14, 21, 28`) where `pacesByWindow[w] != null`.
   - Each available horizon contributes weight `w_i = HORIZON_WEIGHTS.getValue(w)` (`4, 3, 2, 1`) and 2-decimal signed display rate `r_i = round2(toUnit(pace.rateKgPerWeek, normalizedUnit))`.
   - `totalWeight = sum(w_i)`.
   - `weightedRateInUnit = sum(w_i * r_i) / totalWeight.toDouble()`.
   - `weightedRate2InUnit = round2(weightedRateInUnit)`.
   - Equivalent `kg/wk` rate for noise deadband check: `weightedRateKgPerWeek = if (isImperial) WeightAnalytics.lbsToKg(weightedRateInUnit) else weightedRateInUnit`.
2. **Weighted Average `Projected` vs `Stalled`**:
   - Formatted rate `weightedPaceDisplay2`:
     - `weightedRateKgPerWeek < -0.05` -> `String.format(Locale.US, "%.2f %s", weightedRate2InUnit, unitLabel)`
     - `weightedRateKgPerWeek > 0.05` -> `String.format(Locale.US, "+%.2f %s", weightedRate2InUnit, unitLabel)`
     - Otherwise -> `String.format(Locale.US, "0.00 %s", unitLabel)`
   - Deficit velocity `v2 = -weightedRate2InUnit`.
   - If `weightedRateKgPerWeek < -0.05 && v2 > 0.0`:
     - `rawWeeks = remainingInUnit / v2`
     - `roundedWeeks = round1(rawWeeks)`
     - `isCapped = roundedWeeks > 52.0`
     - `cappedWeeks = if (isCapped) 52.0 else roundedWeeks`
     - `targetDate = if (isCapped) null else today.plusDays(round(roundedWeeks * 7.0))`
     - `barFraction = (v2 / benchmarkV2).toFloat().coerceIn(0.05f, 1.0f)`
     - `deltaBadgeText`: `"On time (-X.Xw)"` (`rawDelta < -0.05`), `"On time"` (`abs(rawDelta) <= 0.05`), `"+X.Xw late"` (`rawDelta > 0.05`), or `"> 1 yr late"` (`isCapped`) when `requiredPace != null`; `null` when `requiredPace == null`.
   - Otherwise: `HorizonRowState.Stalled(windowDays = 0, paceDisplay2 = weightedPaceDisplay2)`.
3. **Database Schema Impact**:
   - **None**. Purely derived in-memory analytics from existing `WeightEntry` and `Cycle` entities.
