# Research & Technical Decisions: Goal Arrival Estimations Average Weights & Section Reordering

**Feature**: `010-goal-estimations-weights-order`
**Date**: 2026-11-01

## 1. Rolling Average Weight Per Horizon (`7D`, `14D`, `21D`, `28D`)

- **Decision**: Compute each horizon `w` (`7`, `14`, `21`, `28`) rolling average weight in `FatLossAnalytics.computeMultiHorizonForecast` using `computeWindowAverageKg(allActiveEntries, today.minusDays(w - 1L), today) ?: computeWindowAverageKg(allActiveEntries, newest.date.minusDays(w - 1L), newest.date) ?: currentWeightKg`, then convert and round to 1 decimal place in the active display unit (`round1InUnit(avgKg, normalizedUnit)`) and format as `String.format(Locale.US, "%.1f %s", avg1InUnit, normalizedUnit)`.
- **Rationale**: Reuses the exact `computeWindowAverageKg` and `allActiveEntries` window definition used across `computeRollingWeightHero` (`7D AVG`, `14D AVG`) and `computeRollingWindowTargetRow` (`7D..28D ROLLING AVERAGE`), while falling back to `newest.date` if `today` is more than `w` days after the last logged weigh-in so that any horizon with a non-null `PaceRateResult` is guaranteed a non-null `averageWeightDisplay`.
- **Alternatives Considered**:
  - Computing average only over in-cycle entries: Rejected because `computeCurrent7DayAverageWeight`, `computeRollingWeightHero`, and `compute7DayPaceTargets` all include non-deleted pre-cycle entries in lookback windows once at least 1 in-cycle weigh-in exists.

## 2. `4:3:2:1` Weighted Average Weight Calculation & Display-Unit Reconciliation

- **Decision**: Compute the Weighted Average weight from the 1-decimal display-unit rolling average weights (`avg1InUnit_w`) of the available horizons (`pacesByWindow[w] != null`) using `HORIZON_WEIGHTS` (`7 -> 4, 14 -> 3, 21 -> 2, 28 -> 1`):
  `weightedAvgWeight1InUnit = round1(availableHorizons.sumOf { (w, _) -> (HORIZON_WEIGHTS[w] ?: 1).toDouble() * horizonAvg1InUnit.getValue(w) } / totalWeight.toDouble())`, formatted as `String.format(Locale.US, "%.1f %s", weightedAvgWeight1InUnit, normalizedUnit)`.
- **Rationale**: Satisfies Constitution Principle II (Display-Unit Arithmetic Consistency) so the displayed `Weighted Average` weight reconciles directly with the `4:3:2:1` weighted mean of the displayed `7D`–`28D` horizon weights in both `kg` and `lbs`, and dynamically re-normalizes when only 1–3 horizons are available.
- **Alternatives Considered**:
  - Blending unrounded `kg` averages before converting to display unit: Rejected because rounding intermediate display values first (`round1InUnit`) prevents 0.1-unit discrepancies between the 4 horizon weights shown and their `4:3:2:1` blend.

## 3. Keeping Projected Weeks & Arrival Dates Anchored to `remainingInUnit`

- **Decision**: Keep `remainingInUnit = diff1(current1InUnit, target1InUnit)` (anchored to the current 7D average weight) as the numerator for `rawWeeks = remainingInUnit / v2` across `weightedAverageRow` and all 4 historical rows (`7D`, `14D`, `21D`, `28D`).
- **Rationale**: Explicitly confirmed with the user during requirement discovery (`/grill-me`) and matches the reference screenshot (`21D (-0.31 kg/wk · 82.6 kg)` and `28D (-0.31 kg/wk · 83.1 kg)` both project `10.0 wks · 10 Jan 2027` from `3.1 kg / 0.31 kg/wk`).
- **Alternatives Considered**:
  - Recalculating each row's remaining weight from its own rolling/weighted average weight: Rejected by user choice during clarification.

## 4. Section Reordering in `WeightProgressionHeroCard`

- **Decision**: Move the `if (multiHorizonForecast != null || forecast != null) { ForecastBanner(...) }` block in `WeightProgressionHeroCard` (`ProgressScreen.kt`) to appear immediately after the Hero Centerpiece `Column` (`ROLLING AVERAGES`, `DAILY WEIGH-IN`, and optional drop-streak pill) and immediately before `if (paceTargets != null) { TheoreticalPaceTargetsBox(...) }`.
- **Rationale**: Matches the user's explicit instruction: *"move that section after the daily weight-in and before Pace & linear targets."*
- **Alternatives Considered**:
  - Placing `ForecastBanner` inside the Hero Centerpiece `Column` before the drop-streak pill: Rejected because the drop-streak pill belongs to the daily/rolling weigh-in hero header, whereas `ForecastBanner` and `TheoreticalPaceTargetsBox` are top-level card sections separated by `16.dp` spacing.
