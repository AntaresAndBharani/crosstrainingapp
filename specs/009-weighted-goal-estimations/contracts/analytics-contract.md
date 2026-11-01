# Analytics & UI Contract: Weighted Average Pace Estimation & Goal Arrival Estimations Redesign

**Feature**: `009-weighted-goal-estimations`
**Package**: `com.fractanomics.crosstraining.data.analytics` & `com.fractanomics.crosstraining.ui.screens`

## 1. Domain Analytics Contract (`FatLossAnalytics.kt`)

### `HORIZON_WEIGHTS`
```kotlin
val HORIZON_WEIGHTS: Map<Int, Int> = mapOf(
    7 to 4,
    14 to 3,
    21 to 2,
    28 to 1
)
```

### `computeMultiHorizonForecast`
- Computes `pacesByWindow` for `7, 14, 21, 28` days.
- Computes `weightedAverageRow: HorizonEstimationRow` using `HORIZON_WEIGHTS` across all non-null entries in `pacesByWindow`:
  - `r_i = round2(toUnit(pace.rateKgPerWeek, normalizedUnit))`
  - `weightedRateInUnit = sum(w_i * r_i) / sum(w_i)`
  - `weightedRate2InUnit = round2(weightedRateInUnit)`
  - Evaluates `HorizonRowState.Projected` or `HorizonRowState.Stalled` using the exact same projection helper (`buildHorizonEstimationRow`) as individual horizons (`7D`, `14D`, `21D`, `28D`).
- Returns `MultiHorizonForecastState.Active` with both `weightedAverageRow` and `rows` (the 4 historical horizons).

## 2. UI Resolver & Component Contract (`ProgressScreen.kt`)

### Constants & Resolvers
- `internal const val CURRENT_GOAL_AND_TARGET_HEADER = "Current Goal & Target"`
- `internal const val WEIGHTED_AVERAGE_ROW_TITLE = "Weighted Average"`
- `internal fun resolveHorizonRowLeftLabel(row: HorizonEstimationRow): String`
- `internal fun resolveHorizonRowRightValue(row: HorizonEstimationRow): String`
- `internal fun resolveWeightedRowSummaryText(row: HorizonEstimationRow): String` -> delegates to `row.formatWeightedSummary()`
- `internal fun resolveHorizonRowBadgeText(row: HorizonEstimationRow): String?`
- `internal fun resolveHorizonRowContentDescription(row: HorizonEstimationRow): String`:
  - When `row.isWeightedAverage`:
    - With badge: `"Weighted Average ${row.formatWeightedSummary()}, $badge"`
    - Without badge: `"Weighted Average ${row.formatWeightedSummary()}"`
  - When standard horizon (`7, 14, 21, 28`):
    - Preserves exact format: `"${row.windowDays}-day horizon ($left): $right, $badge"` (or without badge).

### `MultiHorizonGoalEstimationsBanner` Layout Structure (`Active` State)
1. Card container: `Surface(shape = RoundedCornerShape(16.dp), color = MaterialTheme.colorScheme.surfaceContainerHigh)` with `16.dp` internal padding (`12.dp` on compact `< 360dp` viewports).
2. Header Block:
   - `Text(text = GOAL_ARRIVAL_ESTIMATIONS_HEADER, style = MaterialTheme.typography.labelSmall, letterSpacing = 1.sp, color = onSurfaceVariant)`
   - `Text(text = CURRENT_GOAL_AND_TARGET_HEADER, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold, color = onSurface)`
   - `Text(text = state.benchmarkHeader, style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.SemiBold, color = onSurface)`
3. Top `HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))`
4. Primary Estimation Row (`WeightedAverageEstimationRowItem`):
   - Merged TalkBack semantics (`Modifier.semantics(mergeDescendants = true) { contentDescription = resolveHorizonRowContentDescription(row) }`).
   - Line 1: Leading trend/sparkle icon (`AutoMirrored.Filled.TrendingDown` / `✦`) + bold `"Weighted Average"` title and `resolveWeightedRowSummaryText(row)` (`maxLines = 2`, `overflow = TextOverflow.Ellipsis`).
   - Line 2: Sleek pill-shaped progress bar (`height = 7.dp`, `RoundedCornerShape(50)`) with teal/emerald accent gradient/fill when `isOnTrack` (`Brush.horizontalGradient` using primary/teal-green tones) or amber/tertiary fill when late, plus status badge (`On time (-X.Xw)`, `+X.Xw late`, `Stalled`).
5. Mid-card `HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.35f))` separating the headline `Weighted Average` row from the 4 historical breakdown rows.
6. Historical Breakdown List (`7D`, `14D`, `21D`, `28D`):
   - 4 `HorizonEstimationRowItem` entries with `10.dp` vertical spacing and pill-shaped progress bars (`height = 6.dp`, `RoundedCornerShape(50)`).
