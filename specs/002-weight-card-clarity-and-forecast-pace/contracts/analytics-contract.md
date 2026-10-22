# Analytics & UI Contract: Weight Progression Table Clarity & Forecast Pace Attribution

**Feature**: `002-weight-card-clarity-and-forecast-pace`
**Date**: 2026-10-22

## 1. `FatLossAnalytics` Domain Contracts

### 1.1 `computeRollingWeightHero` Display-Consistent Deltas
```kotlin
fun computeRollingWeightHero(
    entries: List<WeightEntry>,
    cycleStartDate: LocalDate,
    referenceDate: LocalDate = LocalDate.now()
): RollingWeightHeroState
```
- `delta7dKg`: `if (today7dAvg != null && lastWeek7dAvg != null) diff1(round1(today7dAvg), round1(lastWeek7dAvg)) else null`
- `delta14dKg`: `if (today14dAvg != null && lastWeek14dAvg != null) diff1(round1(today14dAvg), round1(lastWeek14dAvg)) else null`
- Guarantees that when `today7dAvg` displays as `75.7 kg` and `lastWeek7dAvg` displays as `75.9 kg`, the displayed weekly delta is `-0.2 kg` (eliminating `-0.3 kg` unrounded artifacts).

### 1.2 `computePaceForecast` Display-Rounded Reconciliation & Pace Attribution
```kotlin
fun computePaceForecast(
    entries: List<WeightEntry>,
    cycleStartDate: LocalDate,
    cycleEndDate: LocalDate? = null,
    targetWeightKg: Double?,
    startingWeightKg: Double? = null,
    today: LocalDate = LocalDate.now()
): PaceForecast?
```
- Resolves `pace14 = compute14DayPace(entries, cycleStartDate, newest.date, startingWeightKg)` and `pace7 = compute7DayPace(entries, cycleStartDate, newest.date, startingWeightKg)`.
- Selects active tier:
  - Tier 1: `pace14` (`paceWindowDays = 14`) if `pace14 != null`
  - Tier 2: `pace7` (`paceWindowDays = 7`) if `pace14 == null && pace7 != null`
  - Neither available: returns `PaceForecast.NotEnoughData`
- Evaluates `roundedVelocity = round1(activePace.deficitVelocityKgPerWeek + 1e-5)`:
  - If `roundedVelocity <= 0.0`, returns `PaceForecast.Stalled`.
- Evaluates `remainingKg = diff1(round1(currentWeight), round1(targetWeightKg))`:
  - If `remainingKg <= 0.0`, returns `PaceForecast.GoalReached`.
  - Computes `rawWeeks = remainingKg / roundedVelocity`.
  - Returns `PaceForecast.Projected(weeks = min(52.0, rawWeeks), targetDate = ..., rawWeeks = rawWeeks, paceWindowDays = activeWindowDays, paceRateKgPerWeek = activePace.rateKgPerWeek)`.

---

## 2. `ProgressScreen.kt` UI Contracts

### 2.1 Top Reference Track (`WeightProgressionHeroCard`)
- Uses `round1(resolvedToday7dAvgKg)`, `round1(startWeightKg)`, and `round1(goalWeightKg)` for `progressFraction` (`% to goal`) and `remainingKg` (`-X.X kg left`), ensuring exact reconciliation with the `7D AVG` center pillar and `ForecastBanner`.

### 2.2 Two 3-Column Weight Tables (`WeightProgressionHeroCard` & `SameDayWeightTrioRow`)
- **Table 1 (`ROLLING AVERAGES`)**:
  - Section title: `"ROLLING AVERAGES"` (`labelSmall`, `FontWeight.SemiBold`, `onSurfaceVariant`, `letterSpacing = 1.sp`)
  - Columns: `"LAST WK 7D"` | `"7D AVG"` | `"14D AVG"`
- **Divider**: `HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))` between Table 1 and Table 2 when `sameDayTrio != null`.
- **Table 2 (`DAILY WEIGH-IN (<DAY>)`)**:
  - Section title: `resolveDailyWeighInSectionHeader(referenceDate)` -> `"DAILY WEIGH-IN (THU)"`
  - Columns: `"LAST WEEK"` | `"TODAY"` | `"2 WKS AGO"`

### 2.3 `ForecastBanner` Pace Attribution Subtitle
- Signature:
  ```kotlin
  @Composable
  internal fun ForecastBanner(
      forecast: PaceForecast,
      pace14: PaceRateResult? = null,
      pace7: PaceRateResult? = null,
      weightUnit: String = "kg",
      modifier: Modifier = Modifier
  )
  ```
- Renders `resolveForecastPaceSubtitle(forecast, pace14, pace7, weightUnit)` when non-null as a `MaterialTheme.typography.bodySmall` line (`color = contentColor.copy(alpha = 0.8f)`):
  - For `PaceForecast.Projected`: rendered below `"Projected arrival: ..."` (or directly below `"Estimated: > 1 year"` when capped).
  - For `PaceForecast.Stalled`: rendered directly below `"Pace stalled — insufficient deficit to project"`.
