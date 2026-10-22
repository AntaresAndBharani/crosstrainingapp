# Phase 0 Research: Weight Progression Table Clarity & Forecast Pace Attribution

**Feature**: `002-weight-card-clarity-and-forecast-pace`
**Date**: 2026-10-22

## 1. Visual & Textual Disambiguation of Rolling Averages vs. Daily Weigh-In Tables

### Decision
Add explicit section headers (`"ROLLING AVERAGES"` and `"DAILY WEIGH-IN (<DAY>)"`) separated by a subtle `HorizontalDivider`, and rename the column headers to:
- **Top Table (`ROLLING AVERAGES`)**: `LAST WK 7D` | `7D AVG` | `14D AVG`
- **Bottom Table (`DAILY WEIGH-IN (<DAY>)`)**: `LAST WEEK` | `TODAY` | `2 WKS AGO`

### Rationale
- In the current implementation of `WeightProgressionHeroCard` (`ProgressScreen.kt`), the top 3-pillar row (`computeRollingWeightHero`) renders `LAST WEEK (<DAY>)` | `TODAY` | `14D AVG`, while the bottom 3-column row (`SameDayWeightTrioRow` / `computeSameDayWeightTrio`) renders `LAST WEEK` | `CURRENT` | `2 WKS AGO`.
- Calling the 7-day rolling average `TODAY` in the top row and calling today's raw single-day weight `CURRENT` in the bottom row makes it impossible for an athlete to tell which row is the daily weight and which is the rolling average.
- Furthermore, `LAST WEEK (<DAY>)` (15 characters with `1.sp` letter spacing inside a `1.0f` weight column) truncates to `LAST WEEK (...` on `360dp` viewports, and inaccurately implies a single weekday when the pillar actually shows a 7-day rolling window (`[t-13d, t-7d]`).
- Moving the `<DAY>` indicator to the bottom section title (`DAILY WEIGH-IN (THU)`) and using `LAST WK 7D | 7D AVG | 14D AVG` for the top table eliminates truncation and makes the semantics of all six columns unmistakable.

### Alternatives Considered
- **Adding section headers only without renaming column headers**: Rejected because `TODAY` in the Rolling Averages row would still mislead users into thinking it is today's raw weigh-in, and `LAST WEEK (THU)` would still truncate.
- **Renaming column headers only without section headers**: Rejected because two stacked 3-column grids without section titles still lack visual grouping and hierarchy.

---

## 2. Forecast Banner Pace Attribution (`Based on <14|7>-day pace (<rate>)`)

### Decision
Render a dedicated attribution subtitle line `"Based on ${windowDays}-day pace (${formattedRate})"` inside `ForecastBanner` for both `PaceForecast.Projected` and `PaceForecast.Stalled` states, resolved from Tier 1 (`14-day pace` when `pace14 != null`) else Tier 2 (`7-day pace` when `pace7 != null`), localized to `kg/wk` or `lbs/wk`.

### Rationale
- `computePaceForecast` selects the active velocity tier by availability (`pace14` as Tier 1, falling back to `pace7` as Tier 2).
- Because both `7-Day Pace` (`-0.3 kg/wk`) and `14-Day Pace` (`-0.2 kg/wk`) cards appear directly above `ForecastBanner`, athletes cannot tell which rate produced `Estimated: X.X weeks` or `Pace stalled`.
- Preserving `PaceForecast.Stalled` as a singleton `object` while adding optional default-valued properties (`paceWindowDays: Int = 14`, `paceRateKgPerWeek: Double? = null`) to `PaceForecast.Projected` and resolving the subtitle via `resolveForecastPaceSubtitle(forecast, pace14, pace7, weightUnit)` maintains 100% backward compatibility with existing `assertEquals(PaceForecast.Stalled, forecast)` unit tests.

### Alternatives Considered
- **Appending the pace inline to the primary title (`Estimated: 18.5 weeks (-0.2 kg/wk · 14D)`)**: Rejected because it overflows onto multiple lines on narrow `360dp` screens or at `fontScale = 1.3f`.
- **Converting `PaceForecast.Stalled` from `object` to `data class`**: Rejected because existing unit tests check `assertEquals(PaceForecast.Stalled, forecast)`, which would break if `Stalled` required constructor parameters.

---

## 3. 1-Decimal Display Arithmetic Consistency Across the Card

### Decision
Enforce the **Display-Unit Arithmetic Consistency Invariant** (`round1` / `diff1`) across:
1. `computePaceForecast`: `remainingKg = diff1(round1(currentWeight), round1(targetWeightKg))` divided by `vActiveRounded = round1(vActive + 1e-5)`.
2. `computeRollingWeightHero`: `delta7dKg = diff1(round1(today7dAvg), round1(lastWeek7dAvg))` and `delta14dKg = diff1(round1(today14dAvg), round1(lastWeek14dAvg))`.
3. `WeightProgressionHeroCard` Top Reference Track: compute `progressFraction` (`% to goal`) and `remainingKg` (`-X.X kg left`) from `round1(resolvedToday7dAvgKg)`, `round1(startWeightKg)`, and `round1(goalWeightKg)`.

### Rationale
- In the screenshot reported by the user:
  - `LAST WK 7D` displays `75.9 kg` and `7D AVG` displays `75.7 kg`, yet the delta under `75.9 kg` displays `-0.3 kg` (`75.66 - 75.92 = -0.26 -> -0.3`).
  - `START 77.8 kg`, `7D AVG 75.7 kg`, and `GOAL 72.0 kg (-3.7 kg left)` display `36.9% to goal` instead of `(77.8 - 75.7) / (77.8 - 72.0) = 36.2% to goal`.
  - `GOAL (-3.7 kg left)` and `14-Day Pace (-0.2 kg/wk)` display `Estimated: 19.7 weeks` (`3.66 / 0.188`) instead of `3.7 / 0.2 = 18.5 weeks`.
- Applying `round1` and `diff1` (already standard in `computeSameDayWeightTrio` and `compute7DayPaceTargets`) ensures every number visible on the card reconciles when checked by hand.

### Alternatives Considered
- **Displaying 2 decimal places (`-0.19 kg/wk`, `75.66 kg`)**: Rejected because scale weigh-ins and Material 3 card typography are standardized on 0.1 increments (`%.1f`).
