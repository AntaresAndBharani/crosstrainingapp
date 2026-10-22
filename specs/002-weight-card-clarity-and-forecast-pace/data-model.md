# Data Model: Weight Progression Table Clarity & Forecast Pace Attribution

**Feature**: `002-weight-card-clarity-and-forecast-pace`
**Date**: 2026-10-22

## 1. Analytics Value Objects (`FatLossAnalytics.kt`)

### `PaceForecast` (Sealed Hierarchy)
Updated to carry optional pace attribution metadata on `Projected` (with backward-compatible default arguments) and a pure formatting helper for `ForecastBanner`:

- `PaceForecast.GoalReached` (singleton `object`)
- `PaceForecast.CycleEnded` (singleton `object`)
- `PaceForecast.NotEnoughData` (singleton `object`)
- `PaceForecast.Stalled` (singleton `object` — preserved as `object` for structural equality in existing unit tests)
- `PaceForecast.Projected`:
  - `weeks: Double` — Estimated weeks to reach goal, capped at `52.0`.
  - `targetDate: LocalDate? = null` — Projected calendar date of goal arrival, or `null` if capped (`> 52.0` weeks).
  - `rawWeeks: Double = weeks` — Uncapped calculated weeks (`remainingKgRounded / velocityKgPerWeekRounded`).
  - `paceWindowDays: Int = 14` — Window span in days of the pace tier used (`14` for Tier 1 14-day pace, `7` for Tier 2 7-day fallback pace).
  - `paceRateKgPerWeek: Double? = null` — Signed weekly rate (`kg/wk`, negative for loss) of the active pace tier used to compute the projection.

### `RollingWeightHeroState`
- `today7dAvgKg: Double?` — Arithmetic mean of non-deleted `WeightEntry` records in `[referenceDate - 6d, referenceDate]`.
- `lastWeek7dAvgKg: Double?` — Arithmetic mean of non-deleted `WeightEntry` records in `[referenceDate - 13d, referenceDate - 7d]`.
- `delta7dKg: Double?` — `diff1(round1(today7dAvgKg), round1(lastWeek7dAvgKg))` when both windows are non-null; `null` otherwise.
- `today14dAvgKg: Double?` — Arithmetic mean of non-deleted `WeightEntry` records in `[referenceDate - 13d, referenceDate]`.
- `delta14dKg: Double?` — `diff1(round1(today14dAvgKg), round1(lastWeek14dAvgKg))` when both windows are non-null; `null` otherwise.
- `loggedDaysCount14d: Int` — Count of non-deleted entries in `[referenceDate - 13d, referenceDate]`.

---

## 2. UI Presentation Resolvers (`ProgressScreen.kt`)

### Rolling Averages Section (Top 3-Column Row)
- **Section Header**: `ROLLING_AVERAGES_SECTION_HEADER = "ROLLING AVERAGES"`
- **Left Pillar Header**: `resolveLeftPillarHeader(currentDate: LocalDate): String = "LAST WK 7D"`
- **Center Pillar Header**: `resolveCenterPillarHeader(currentDate: LocalDate, referenceDate: LocalDate = LocalDate.now()): String = "7D AVG"`
- **Right Pillar Header**: `resolveRightPillarHeader(progressiveAverage: ProgressiveAverageResult? = null): String = "${progressiveAverage?.windowSpanDays ?: 14}D AVG"`

### Daily Weigh-In Section (Bottom 3-Column Row — `SameDayWeightTrioRow`)
- **Section Header**: `resolveDailyWeighInSectionHeader(referenceDate: LocalDate): String` -> `"DAILY WEIGH-IN ($dayName)"` where `dayName = referenceDate.dayOfWeek.getDisplayName(TextStyle.SHORT, Locale.US).uppercase()`.
- **Left Column Header**: `"LAST WEEK"`
- **Center Column Header**: `"TODAY"`
- **Right Column Header**: `"2 WKS AGO"`

### Forecast Banner Pace Attribution (`ForecastBanner`)
- `resolveForecastPaceSubtitle(forecast: PaceForecast, pace14: PaceRateResult? = null, pace7: PaceRateResult? = null, weightUnit: String = "kg"): String?`
  - Returns `null` when `forecast` is `GoalReached`, `CycleEnded`, or `NotEnoughData`.
  - When `forecast` is `Projected` or `Stalled`:
    - Resolves active tier `(windowDays, rateKgPerWeek)`:
      1. If `pace14 != null`, uses `(14, pace14.rateKgPerWeek)`
      2. Else if `pace7 != null`, uses `(7, pace7.rateKgPerWeek)`
      3. Else if `forecast is PaceForecast.Projected && forecast.paceRateKgPerWeek != null`, uses `(forecast.paceWindowDays, forecast.paceRateKgPerWeek)`
      4. Else returns `null`.
    - Formats rate via `FatLossAnalytics.formatPaceRate(rateKgPerWeek, weightUnit)` (e.g., `"-0.2 kg/wk"`, `"-0.4 lbs/wk"`, `"0.0 kg/wk"`, `"+0.2 kg/wk"`).
    - Emits `"Based on ${windowDays}-day pace ($formattedRate)"`.
