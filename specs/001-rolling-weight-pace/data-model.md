# Data Model: Rolling Window Average Pacing & Goal Forecast Alignment

**Feature**: `001-rolling-weight-pace`
**Date**: 2026-10-22

## 1. Domain Entities (Existing — Unchanged Schema)

No Room database schema changes or migrations are required. The feature operates entirely on existing immutable domain models:

### `WeightEntry`
- `date: LocalDate` (`@PrimaryKey`, natural daily uniqueness invariant)
- `weightKg: Double` (canonical weight in kilograms, validated in `[20.0, 350.0]`)
- `notes: String`
- `updatedAtMillis: Long`
- `deletedAtMillis: Long?` (soft-delete tombstone; non-null entries are excluded from all analytics)

### `Cycle`
- `id: Long`
- `name: String`
- `startDate: LocalDate`
- `endDate: LocalDate?`
- `type: CycleType` (`FAT_LOSS_BODYBUILDING`, `STRENGTH_WEIGHTLIFTING`)
- `startingWeightKg: Double?`
- `targetWeightKg: Double?` / `targetBodyWeightKg: Double?`

---

## 2. Analytics Value Objects (`FatLossAnalytics.kt`)

### `RollingWeightHeroState`
Represents the 3-pillar rolling window summary displayed in the hero section of `WeightProgressionHeroCard`:
- `today7dAvgKg: Double?` — Arithmetic mean of non-deleted `WeightEntry` records in `[referenceDate - 6d, referenceDate]`.
- `lastWeek7dAvgKg: Double?` — Arithmetic mean of non-deleted `WeightEntry` records in `[referenceDate - 13d, referenceDate - 7d]` (including pre-cycle entries).
- `delta7dKg: Double?` — `today7dAvgKg - lastWeek7dAvgKg` when both windows are non-null; `null` otherwise.
- `today14dAvgKg: Double?` — Arithmetic mean of non-deleted `WeightEntry` records in `[referenceDate - 13d, referenceDate]` (including pre-cycle entries).
- `delta14dKg: Double?` — `today14dAvgKg - lastWeek14dAvgKg` where `lastWeek14dAvgKg` is the arithmetic mean over `[referenceDate - 20d, referenceDate - 7d]` (including pre-cycle entries).
- `loggedDaysCount14d: Int` — Count of non-deleted entries in `[referenceDate - 13d, referenceDate]`.

### `PaceRateResult`
Represents a signed weekly body weight change rate for a given rolling window (`windowDays = 7` or `14`):
- `rateKgPerWeek: Double` — Signed weekly change in `kg/wk` (negative indicates weight loss):
  - For 7-Day Pace ($w = 7$): $\overline{W}_{7\text{d}}(t) - \overline{W}_{7\text{d}}(t - 7\text{d})$
  - For 14-Day Pace ($w = 14$): $\frac{\overline{W}_{7\text{d}}(t) - \overline{W}_{7\text{d}}(t - 14\text{d})}{2.0}$
- `anchorWeightKg: Double` — Resolved prior 7D window average $\overline{W}_{7\text{d}}(t - w)$ (or fallback baseline weight).
- `anchorDate: LocalDate` — Anchor date representing the prior window cutoff ($t - w$, or fallback anchor date).
- `currentWeightKg: Double` — Resolved current 7D window average $\overline{W}_{7\text{d}}(t)$.
- `currentDate: LocalDate` — Latest active in-cycle weigh-in date $t \le \text{today}$.
- `elapsedDays: Long` — Effective span in days between the prior window anchor and $t$ ($7$ for 7D window average, $14$ for 14D window average, or actual elapsed days when a sparse fallback anchor is used).
- `trend: PaceTrend` — `LOSS` (`< -0.05`), `GAIN` (`> +0.05`), or `NEUTRAL` (`[-0.05, +0.05]`).
- `deficitVelocityKgPerWeek: Double` — Computed property `-rateKgPerWeek` (positive for weight loss).

### `PaceComparisonResult`
Represents week-over-week comparison of a smoothed pace rate at $t$ against the smoothed pace rate at $t - 7\text{d}$:
- `currentRateKgPerWeek: Double` — $P_w(t)$.
- `priorRateKgPerWeek: Double` — $P_w(t - 7\text{d})$.
- `deltaRateKgPerWeek: Double` — $P_w(t) - P_w(t - 7\text{d})$.
- `percentChange: Double?` — Percentage change when both current and prior rates are strictly negative (`< 0.0`) and `abs(priorRateKgPerWeek) >= 0.1`; `null` otherwise.
- `isAcceleratingDeficit: Boolean` — `true` when both rates are `< 0.0` and `-currentRate > -priorRate`.
- `priorDate: LocalDate` — `priorPace.currentDate`.
- `isConsecutive: Boolean` — `priorDate == t.minusDays(1)`.
- `isExactDayMatch: Boolean` — `priorDate == t.minusDays(7)`.

### `PaceForecast` (Sealed Hierarchy)
State machine for goal arrival projection:
1. `GoalReached` — `currentWeight <= targetWeightKg` (where `currentWeight` is $\overline{W}_{7\text{d}}(t)$).
2. `CycleEnded` — `today > cycleEndDate` (when goal not yet reached).
3. `NotEnoughData` — No active in-cycle entries, or neither the 14D nor the 7D Smoothed Pace is resolvable.
4. `Stalled` — The first available velocity tier (Tier 1: 14D Smoothed Pace, else Tier 2: 7D Smoothed Pace) is $\le 0.0\text{ kg/wk}$ when rounded to 1 decimal place.
5. `Projected(weeks: Double, targetDate: LocalDate?, rawWeeks: Double)` — Positive active deficit velocity $v_{\text{active}} > 0.0$, where `rawWeeks = (currentWeight - targetWeightKg) / vActive`, capped at `52.0` weeks (`"Estimated: > 1 year"`).
