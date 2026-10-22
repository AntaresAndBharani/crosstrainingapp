# Analytics & UI Contract: Cycle End-Date Required Pace in Goal Estimation Banner

**Feature**: `003-cycle-end-required-pace`
**Date**: 2026-10-22

## 1. `FatLossAnalytics` Domain Contracts (`FatLossAnalytics.kt`)

### 1.1 `computeRequiredPaceToEndDate` (Direct Value Overload)
```kotlin
fun computeRequiredPaceToEndDate(
    today7dAvgKg: Double?,
    targetWeightKg: Double?,
    cycleEndDate: LocalDate?,
    today: LocalDate = LocalDate.now(),
    weightUnit: String = "kg",
    startingWeightKg: Double? = null
): RequiredPaceResult?
```
- **Invariants**:
  1. Returns `null` if `cycleEndDate == null` or `ChronoUnit.DAYS.between(today, cycleEndDate) <= 0L`.
  2. Returns `null` if `today7dAvgKg == null || today7dAvgKg <= 0.0 || targetWeightKg == null || targetWeightKg <= 0.0`.
  3. Returns `null` if `startingWeightKg != null && startingWeightKg > 0.0 && targetWeightKg >= startingWeightKg`.
  4. Converts `today7dAvgKg` and `targetWeightKg` to `weightUnit` (`kg` or `lbs` via `WeightAnalytics.kgToLbs`) and computes `remainingDisplay = diff1(round1(currentDisplay), round1(targetDisplay))`.
  5. Returns `null` if `remainingDisplay <= 0.0`.
  6. Computes `weeksRemaining = daysRemaining.toDouble() / 7.0` and `requiredRateInUnit = -maxOf(0.1, round1(remainingDisplay / weeksRemaining))`.
  7. Returns `RequiredPaceResult` whose `formatSubtitle()` yields `"Required pace: -X.X <kg/wk|lbs/wk> (by <d MMM yyyy>)"`.

### 1.2 `computeRequiredPaceToEndDate` (Entries & Cycle Overload)
```kotlin
fun computeRequiredPaceToEndDate(
    entries: List<WeightEntry>,
    cycle: Cycle,
    today: LocalDate = LocalDate.now(),
    weightUnit: String = "kg"
): RequiredPaceResult?
```
- Resolves active in-cycle entries via `getActiveWeightEntries(entries, cycle.startDate, today)`. If empty, returns `null`.
- Resolves `today7dAvgKg = computeCurrent7DayAverageWeight(entries, cycle.startDate, today) ?: activeEntries.last().weightKg`.
- Resolves `effectiveStartWeight = cycle.startingWeightKg ?: activeEntries.first().weightKg`.
- Delegates to the direct-value overload of `computeRequiredPaceToEndDate`.

---

## 2. `ProgressScreen.kt` UI Contracts

### 2.1 `resolveRequiredPaceSubtitle`
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
- Returns `null` when `forecast` is `PaceForecast.GoalReached` or `PaceForecast.CycleEnded`.
- For `PaceForecast.Projected`, `PaceForecast.Stalled`, and `PaceForecast.NotEnoughData`, returns `FatLossAnalytics.computeRequiredPaceToEndDate(...)?.formatSubtitle()`.

### 2.2 `ForecastBanner` & `WeightProgressionHeroCard` Wiring
- `WeightProgressionCard` passes `cycleEndDate = cycle.endDate` to `WeightProgressionHeroCard`.
- `WeightProgressionHeroCard` passes `today7dAvgKg = resolvedToday7dAvgKg`, `goalWeightKg = goalWeightKg`, `startWeightKg = startWeightKg`, `cycleEndDate = cycleEndDate`, and `referenceDate = referenceDate` to `ForecastBanner`.
- `ForecastBanner` renders `resolveRequiredPaceSubtitle(...)` when non-null as a `MaterialTheme.typography.bodySmall` line (`color = contentColor.copy(alpha = 0.8f)`) below any existing subtitle lines.
