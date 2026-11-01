# Data Model: Goal Arrival Estimations Average Weights & Section Reordering

**Feature**: `010-goal-estimations-weights-order`
**Date**: 2026-11-01

## 1. Extended Domain Models (`FatLossAnalytics.kt`)

### `HorizonRowState`

Both `HorizonRowState.Projected` and `HorizonRowState.Stalled` gain an optional `averageWeightDisplay: String? = null` property (defaulting to `null` for backward compatibility with direct test constructors):

```kotlin
sealed interface HorizonRowState {
    val windowDays: Int

    data class NotEnoughData(
        override val windowDays: Int
    ) : HorizonRowState

    data class Stalled(
        override val windowDays: Int,
        val paceDisplay2: String,
        val averageWeightDisplay: String? = null
    ) : HorizonRowState

    data class Projected(
        override val windowDays: Int,
        val paceDisplay2: String,
        val velocity2InUnit: Double,
        val weeks: Double,
        val rawWeeks: Double = weeks,
        val targetDate: LocalDate? = null,
        val barFraction: Float,
        val weeksDeltaVsTarget: Double? = null,
        val isOnTrack: Boolean = true,
        val deltaBadgeText: String? = null,
        val averageWeightDisplay: String? = null
    ) : HorizonRowState {
        // existing computed properties preserved
    }
}
```

### `HorizonEstimationRow` Formatting Rules

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
            is HorizonRowState.Projected -> {
                val weightPart = state.averageWeightDisplay?.let { " · $it" } ?: ""
                "${windowDays}D (${state.paceDisplay2}$weightPart)"
            }
            is HorizonRowState.Stalled -> {
                val weightPart = state.averageWeightDisplay?.let { " · $it" } ?: ""
                "${windowDays}D (${state.paceDisplay2}$weightPart)"
            }
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

    fun formatWeightedSummary(): String = when (state) {
        is HorizonRowState.Projected -> {
            val weightPart = state.averageWeightDisplay?.let { " · $it" } ?: ""
            "(Weighted pace of ${state.paceDisplay2}$weightPart · ${formatRightValue()})"
        }
        is HorizonRowState.Stalled -> {
            val weightPart = state.averageWeightDisplay?.let { " · $it" } ?: ""
            "(Weighted pace of ${state.paceDisplay2}$weightPart · Pace stalled)"
        }
        is HorizonRowState.NotEnoughData -> "(Needs 7+ days)"
    }
}
```

## 2. Rolling & Weighted Average Weight Computation Rules

1. **Per-Horizon Rolling Average Weight (`w` in `{7, 14, 21, 28}`)**:
   - `allActiveEntries = getAllHistoricalWeightEntries(entries, today)`
   - `avgKg = computeWindowAverageKg(allActiveEntries, today.minusDays(w - 1L), today) ?: computeWindowAverageKg(allActiveEntries, newest.date.minusDays(w - 1L), newest.date) ?: currentWeightKg`
   - `avg1InUnit = round1InUnit(avgKg, normalizedUnit)`
   - `averageWeightDisplay = String.format(Locale.US, "%.1f %s", avg1InUnit, normalizedUnit)` (e.g., `"81.5 kg"` or `"179.7 lbs"`).
2. **`4:3:2:1` Weighted Average Weight**:
   - Across `availableHorizons` (`pacesByWindow[w] != null`):
   - `weightedAvgWeightSum = availableHorizons.sumOf { (w, _) -> (HORIZON_WEIGHTS[w] ?: 1).toDouble() * horizonAvg1InUnit.getValue(w) }`
   - `weightedAvgWeight1InUnit = round1(weightedAvgWeightSum / totalWeight.toDouble())`
   - `weightedAverageWeightDisplay = String.format(Locale.US, "%.1f %s", weightedAvgWeight1InUnit, normalizedUnit)`

## 3. Database Schema Impact

- **None**. All values are computed deterministically in memory from existing `WeightEntry` and `Cycle` entities.
