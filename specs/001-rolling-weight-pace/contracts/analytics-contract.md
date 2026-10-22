# Analytics & UI Contract: Rolling Window Average Pacing & Goal Forecast

**Feature**: `001-rolling-weight-pace`
**Date**: 2026-10-22

## 1. `FatLossAnalytics` Domain Functions

### 1.1 `computeRollingWeightHero`
```kotlin
fun computeRollingWeightHero(
    entries: List<WeightEntry>,
    cycleStartDate: LocalDate,
    referenceDate: LocalDate = LocalDate.now()
): RollingWeightHeroState
```
**Contract & Invariants**:
- **In-Cycle Gate**: Must verify whether at least one non-deleted entry exists in `[cycleStartDate, referenceDate]`. If none exists, all `Double?` fields in `RollingWeightHeroState` return `null` and `loggedDaysCount14d` returns `0`.
- **Pre-Cycle Lookback Inclusion**: Once at least one in-cycle entry exists, window averages are computed over all non-deleted entries with `!date.isAfter(referenceDate)` (including entries where `date < cycleStartDate`):
  - `today7dAvgKg`: mean of `[referenceDate - 6d, referenceDate]`
  - `lastWeek7dAvgKg`: mean of `[referenceDate - 13d, referenceDate - 7d]`
  - `delta7dKg`: `today7dAvgKg - lastWeek7dAvgKg` (if both non-null)
  - `today14dAvgKg`: mean of `[referenceDate - 13d, referenceDate]`
  - `lastWeek14dAvgKg`: mean of `[referenceDate - 20d, referenceDate - 7d]`
  - `delta14dKg`: `today14dAvgKg - lastWeek14dAvgKg` (if both non-null)
  - `loggedDaysCount14d`: count of non-deleted entries in `[referenceDate - 13d, referenceDate]`

---

### 1.2 `computePaceRate`, `compute7DayPace`, `compute14DayPace`
```kotlin
fun computePaceRate(
    entries: List<WeightEntry>,
    windowDays: Int,
    cycleStartDate: LocalDate,
    today: LocalDate = LocalDate.now(),
    startingWeightKg: Double? = null
): PaceRateResult?

fun compute7DayPace(
    entries: List<WeightEntry>,
    cycleStartDate: LocalDate,
    today: LocalDate = LocalDate.now(),
    startingWeightKg: Double? = null
): PaceRateResult?

fun compute14DayPace(
    entries: List<WeightEntry>,
    cycleStartDate: LocalDate,
    today: LocalDate = LocalDate.now(),
    startingWeightKg: Double? = null
): PaceRateResult?
```
**Contract & Invariants**:
1. **Anchor Date $t$**: Resolves $t = \text{inCycleEntries.last().date}$ where `inCycleEntries` are non-deleted entries in `[cycleStartDate, today]`. If `inCycleEntries` is empty, returns `null`.
2. **Historical Pool**: `allActiveEntries` = non-deleted entries with `!date.isAfter(t)` sorted by `date` ascending (includes pre-cycle entries).
3. **Minimum Span (implicit)**: There is no separate span or entry-count guard. Every anchor below is dated on or before `t - windowDays`, so a non-null result always spans `>= windowDays`. One in-cycle entry plus a valid `startingWeightKg` is sufficient.
4. **Current 7D Window Average $\overline{W}_{7\text{d}}(t)$**:
   - Mean of `allActiveEntries` in `[t - 6d, t]`.
5. **Prior 7D Window Average $\overline{W}_{7\text{d}}(t - \text{windowDays})$**:
   - Let `cutoffDate = t.minusDays(windowDays.toLong())` and `priorWindowStart = cutoffDate.minusDays(6)`.
   - Let `priorWindowEntries = allActiveEntries.filter { !it.date.isBefore(priorWindowStart) && !it.date.isAfter(cutoffDate) }`.
   - **Primary Path (`priorWindowEntries.isNotEmpty()`)**:
     - `priorAvgKg = priorWindowEntries.map { it.weightKg }.average()`
     - `rateKgPerWeek = ((currentAvgKg - priorAvgKg) / windowDays.toDouble()) * 7.0`
     - `anchorDate = priorWindowEntries.last().date`
     - `elapsedDays = windowDays.toLong()`
   - **Sparse / Cold-Start Fallback Path (`priorWindowEntries.isEmpty()`)** (FR-002):
     - If `startingWeightKg != null && startingWeightKg > 0.0 && !cycleStartDate.isAfter(cutoffDate)`:
       - `elapsedDays = ChronoUnit.DAYS.between(cycleStartDate, t)`
       - `rateKgPerWeek = ((currentAvgKg - startingWeightKg) / elapsedDays.toDouble()) * 7.0`
       - `anchorWeightKg = startingWeightKg`, `anchorDate = cycleStartDate`
     - Else if `fallbackEntry = resolveAnchorEntry(inCycleEntries, cutoffDate, cycleStartDate)` (most recent **in-cycle** entry on or before `cutoffDate`) is non-null:
       - `elapsedDays = ChronoUnit.DAYS.between(fallbackEntry.date, t)`
       - `rateKgPerWeek = ((currentAvgKg - fallbackEntry.weightKg) / elapsedDays.toDouble()) * 7.0`
       - `anchorWeightKg = fallbackEntry.weightKg`, `anchorDate = fallbackEntry.date`
     - Else returns `null`. Pre-cycle entries outside the prior window are never used as anchors.

---

### 1.3 `computePaceComparisonVsLastWeek`
```kotlin
fun computePaceComparisonVsLastWeek(
    entries: List<WeightEntry>,
    cycleStartDate: LocalDate,
    windowDays: Int,
    referenceDate: LocalDate = LocalDate.now(),
    minPercentageDenominatorKgPerWeek: Double = 0.1,
    startingWeightKg: Double? = null
): PaceComparisonResult?
```
**Contract & Invariants**:
- Resolves `t = inCycleEntries.last().date`.
- Evaluates `currentPace = computePaceRate(entries, windowDays, cycleStartDate, t, startingWeightKg)`.
- Evaluates `priorPace` at `t.minusDays(7)` using the same smoothed `computePaceRate` logic across `allActiveEntries` up to `t.minusDays(7)`.
- Computes `deltaRateKgPerWeek = currentPace.rateKgPerWeek - priorPace.rateKgPerWeek` and loss-to-loss percentage change.

---

### 1.4 `computePaceForecast`
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
**Contract & Invariants**:
1. `currentWeight` = $\overline{W}_{7\text{d}}(t)$ via `computeCurrent7DayAverageWeight` (including pre-cycle entries in `[t-6, t]` when $t < \text{cycleStartDate} + 6\text{d}$).
2. Precedence 1 (`GoalReached`): `targetWeightKg != null && targetWeightKg > 0.0 && currentWeight <= targetWeightKg`.
3. Precedence 2 (`CycleEnded`): `cycleEndDate != null && today.isAfter(cycleEndDate)`.
4. Unset/Invalid Target Guard: Returns `null` if `targetWeightKg == null || targetWeightKg <= 0.0 || targetWeightKg >= effectiveStartWeight`.
5. **Tiered Deficit Velocity Resolution** (by availability, not sign):
   - **Tier 1**: `v14 = compute14DayPace(entries, cycleStartDate, t, startingWeightKg)?.deficitVelocityKgPerWeek`
   - **Tier 2**: `v7 = compute7DayPace(entries, cycleStartDate, t, startingWeightKg)?.deficitVelocityKgPerWeek`
   - `vActive = v14 ?: v7`
6. Precedence 3 (`NotEnoughData`): `vActive == null`.
7. Precedence 4 (`Stalled`): If `round1(vActive + 1e-5) <= 0.0`, returns `PaceForecast.Stalled`, so the forecast agrees with the highest-priority pace card shown.
8. Precedence 5 (`Projected`): `remainingKg = currentWeight - targetWeightKg`, `rawWeeks = remainingKg / vActive`, capped at `52.0` weeks.

---

## 2. UI Presentation Contract (`ProgressScreen.kt`)

### `WeightProgressionHeroCard` Top Reference Track
- `progressFraction` = `((startWeightKg - resolvedToday7dAvgKg) / (startWeightKg - goalWeightKg)).toFloat().coerceIn(0f, 1f)`
- `isGoalReached` = `resolvedToday7dAvgKg <= goalWeightKg`
- `remainingKg` = `resolvedToday7dAvgKg - goalWeightKg`
- Ensures the top reference bar (`% to goal` and `(-X.X kg left)`), the center hero pillar (`TODAY`), and the `ForecastBanner` (`Estimated: X.X weeks`) all share the exact same `resolvedToday7dAvgKg` current weight baseline.
