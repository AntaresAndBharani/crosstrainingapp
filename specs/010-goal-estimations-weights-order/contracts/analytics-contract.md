# Analytics & UI Contract: Goal Arrival Estimations Average Weights & Section Reordering

**Feature**: `010-goal-estimations-weights-order`
**Date**: 2026-11-01

## 1. Domain Contract (`com.fractanomics.crosstraining.data.analytics`)

### `HorizonRowState.Projected` & `HorizonRowState.Stalled`
- Both state classes expose `val averageWeightDisplay: String? = null`.
- When populated by `FatLossAnalytics.computeMultiHorizonForecast`:
  - For each non-null horizon `w` in `{7, 14, 21, 28}`: `averageWeightDisplay` equals `"%.1f %s"` of the `w`-day rolling average weight in the active display unit (`kg` or `lbs`).
  - For `weightedAverageRow`: `averageWeightDisplay` equals `"%.1f %s"` of the `4:3:2:1` recency-weighted average of the available horizons' 1-decimal display-unit rolling average weights.

### `HorizonEstimationRow`
- `formatLeftLabel()`:
  - When `isWeightedAverage == true`: `"Weighted Average"`
  - When `isWeightedAverage == false` and `state` is `Projected` or `Stalled`:
    - If `averageWeightDisplay != null`: `"${windowDays}D (${state.paceDisplay2} · ${state.averageWeightDisplay})"` (e.g., `"7D (-0.49 kg/wk · 81.5 kg)"`)
    - If `averageWeightDisplay == null`: `"${windowDays}D (${state.paceDisplay2})"`
  - When `state` is `NotEnoughData`: `"${windowDays}D (—)"`
- `formatWeightedSummary()`:
  - `Projected`: `"(Weighted pace of ${state.paceDisplay2} · ${state.averageWeightDisplay} · ${formatRightValue()})"` (or without ` · ${state.averageWeightDisplay}` when `null`)
  - `Stalled`: `"(Weighted pace of ${state.paceDisplay2} · ${state.averageWeightDisplay} · Pace stalled)"` (or without ` · ${state.averageWeightDisplay}` when `null`)
  - `NotEnoughData`: `"(Needs 7+ days)"`

## 2. UI Section Order Contract (`WeightProgressionHeroCard` in `ProgressScreen.kt`)

Inside `WeightProgressionHeroCard`, child sections render in the following top-to-bottom sequence:
1. **Top Reference Track** (`START`, `% to goal` progress bar, `GOAL`)
2. **Hero Centerpiece** (`ROLLING AVERAGES` 3-pillar row, `DAILY WEIGH-IN (<DAY>)` 3-pillar row, and optional `📉 X-day streak` pill)
3. **Goal Arrival Estimations** (`ForecastBanner` -> `MultiHorizonGoalEstimationsBanner`)
4. **Pace & Linear Targets** (`TheoreticalPaceTargetsBox`)
5. **Pace Metrics 2x2 Grid** (`7-Day Pace`, `14-Day Pace`, `21-Day Pace`, `28-Day Pace`)
6. **CTA Row** (`Log weigh-in` button)
