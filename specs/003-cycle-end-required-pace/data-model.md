# Data Model: Cycle End-Date Required Pace in Goal Estimation Banner

**Feature**: `003-cycle-end-required-pace`
**Date**: 2026-10-22

## 1. Analytics Value Objects (`FatLossAnalytics.kt`)

### `RequiredPaceResult`
Represents the calculated weekly weight-loss pace required to reach the cycle's target weight by its scheduled `endDate`:

- `requiredRateInUnit: Double` — Signed weekly rate in the requested `weightUnit` (strictly negative, `<= -0.1`, e.g., `-0.6` for `-0.6 kg/wk` or `-1.5` for `-1.5 lbs/wk`).
- `remainingWeightInUnit: Double` — Positive 1-decimal rounded remaining weight to lose in the requested `weightUnit` (`diff1(round1(toDisplay(today7dAvgKg)), round1(toDisplay(targetWeightKg))) > 0.0`).
- `daysRemaining: Long` — Calendar days remaining from `today` (`referenceDate`) to `endDate` (`ChronoUnit.DAYS.between(today, endDate) > 0`).
- `weeksRemaining: Double` — Exact calendar weeks remaining (`daysRemaining / 7.0`).
- `endDate: LocalDate` — The cycle's scheduled end date.
- `weightUnit: String` — The active weight unit (`"kg"` or `"lbs"`).

**Formatting Methods on `RequiredPaceResult`**:
- `formatRate(): String` — Formats the rate with unit label (e.g., `"-0.6 kg/wk"` or `"-1.5 lbs/wk"`).
- `formatEndDate(): String` — Formats `endDate` using `DateTimeFormatter.ofPattern("d MMM yyyy", Locale.US)` (e.g., `"30 Nov 2026"`).
- `formatSubtitle(): String` — Formats the complete banner subtitle line: `"Required pace: ${formatRate()} (by ${formatEndDate()})"` (e.g., `"Required pace: -0.6 kg/wk (by 30 Nov 2026)"`).

**Validation & Guard Rules**:
- Returns `null` when:
  - `cycleEndDate == null`
  - `ChronoUnit.DAYS.between(today, cycleEndDate) <= 0L` (on or after `cycleEndDate`)
  - `today7dAvgKg == null || today7dAvgKg <= 0.0`
  - `targetWeightKg == null || targetWeightKg <= 0.0`
  - `startingWeightKg != null && startingWeightKg > 0.0 && targetWeightKg >= startingWeightKg`
  - `remainingWeightInUnit <= 0.0` (goal already reached at 1-decimal display precision)

---

## 2. UI Presentation Resolvers (`ProgressScreen.kt`)

### `resolveRequiredPaceSubtitle`
```kotlin
internal fun resolveRequiredPaceSubtitle(
    forecast: PaceForecast,
    today7dAvgKg: Double?,
    goalWeightKg: Double?,
    cycleEndDate: LocalDate?,
    referenceDate: LocalDate = LocalDate.now(),
    weightUnit: String = "kg",
    startWeightKg: Double? = null
): String?
```
- **State Gate**:
  - Allowed when `forecast` is `PaceForecast.Projected`, `PaceForecast.Stalled`, or `PaceForecast.NotEnoughData`.
  - Returns `null` when `forecast` is `PaceForecast.GoalReached` or `PaceForecast.CycleEnded`.
- **Calculation Delegation**:
  - Calls `FatLossAnalytics.computeRequiredPaceToEndDate(today7dAvgKg = today7dAvgKg, targetWeightKg = goalWeightKg, cycleEndDate = cycleEndDate, today = referenceDate, weightUnit = weightUnit, startingWeightKg = startWeightKg)?.formatSubtitle()`.
