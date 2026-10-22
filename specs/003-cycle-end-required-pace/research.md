# Phase 0 Research: Cycle End-Date Required Pace in Goal Estimation Banner

**Feature**: `003-cycle-end-required-pace`
**Date**: 2026-10-22

## 1. Display-Unit Arithmetic Reconciliation for Required Pace Numerator

### Decision
Compute the remaining weight numerator (`remainingDisplay`) in the active display unit (`kg` or `lbs`) as:
```kotlin
val remainingDisplay = diff1(round1(toDisplayWeight(today7dAvgKg, weightUnit)), round1(toDisplayWeight(targetWeightKg, weightUnit)))
```
where `today7dAvgKg` is the 7-day rolling average weight (`computeCurrent7DayAverageWeight` fallback to latest in-cycle weight) — identical to `resolveGoalRemainingDisplay` in `ProgressScreen.kt`.

### Rationale
- The Top Reference Track on `WeightProgressionHeroCard` displays `GOAL <target> <unit> (-<remainingDisplay> <unit> left)` using `resolveGoalRemainingDisplay(resolvedToday7dAvgKg, goalWeightKg, weightUnit)`.
- Using the exact same 1-decimal rounded display-unit remaining weight guarantees that when an athlete divides the `-X.X kg left` (or `-X.X lbs left`) figure shown at the top of the card by the remaining calendar weeks until `cycle.endDate`, the result matches the `Required pace: -Y.Y <unit>/wk (by <d MMM yyyy>)` line in `ForecastBanner` with zero rounding discrepancy.

### Alternatives Considered
- **Using raw latest daily weigh-in (`TODAY`) instead of `7D AVG`**: Rejected during `/grill-me` alignment because both the top progress track (`-X.X kg left`) and `Estimated: X.X weeks` are anchored to the 1-decimal 7-day rolling average (`7D AVG`).
- **Calculating required pace in `kg/wk` and converting the rounded `kg/wk` rate to `lbs/wk`**: Rejected because converting a pre-rounded `kg` delta to `lbs` can introduce a `0.1–0.2 lbs` discrepancy against the displayed `-X.X lbs left` badge (as seen in `002-weight-card-clarity-and-forecast-pace`).

---

## 2. Remaining Time Denominator & Boundary Handling at `cycle.endDate`

### Decision
Compute remaining time to `cycle.endDate` as exact calendar weeks:
```kotlin
val daysRemaining = ChronoUnit.DAYS.between(today, cycleEndDate)
if (daysRemaining <= 0L) return null
val weeksRemaining = daysRemaining.toDouble() / 7.0
```
and clamp the magnitude of the 1-decimal rounded weekly loss rate to at least `0.1` (`requiredRateInUnit = -maxOf(0.1, round1(remainingDisplay / weeksRemaining))`) whenever `remainingDisplay > 0.0`.

### Rationale
- `daysRemaining <= 0L` cleanly guards both the final day of the cycle (`today == cycleEndDate`, where `daysRemaining == 0` would otherwise divide by zero) and post-cycle dates (`today > cycleEndDate`, where `PaceForecast.CycleEnded` is active).
- Clamping the rounded loss magnitude to `>= 0.1` ensures that if an athlete has a small remaining distance (e.g., `0.2 kg` left over `10` weeks = `0.02 kg/wk`), the banner displays `Required pace: -0.1 kg/wk` rather than `0.0 kg/wk` while the goal is still unreached.

### Alternatives Considered
- **Rounding `weeksRemaining` to 1 decimal place before dividing**: Rejected during `/grill-me` alignment because dividing by exact calendar weeks (`daysRemaining / 7.0`) avoids compounding intermediate rounding error on short horizons.
- **Clamping `daysRemaining` to a minimum of 7 days in the final week**: Rejected during `/grill-me` alignment so the required rate accurately reflects the remaining calendar days until `today == cycleEndDate`.

---

## 3. Banner State Eligibility & Non-Intrusive UI Integration

### Decision
Render `"Required pace: -X.X <kg/wk|lbs/wk> (by <d MMM yyyy>)"` at the bottom of `ForecastBanner` whenever `forecast` is `PaceForecast.Projected`, `PaceForecast.Stalled`, or `PaceForecast.NotEnoughData` (with at least 1 active in-cycle weigh-in, valid unreached target weight, and `daysRemaining > 0`), without altering the sealed `PaceForecast` variants' equality semantics.

### Rationale
- Keeping `PaceForecast.Stalled` and `PaceForecast.NotEnoughData` as singleton `object` declarations preserves 100% backward compatibility with all existing unit tests (`assertEquals(PaceForecast.Stalled, forecast)` and `assertEquals(PaceForecast.NotEnoughData, forecast)`).
- Passing `today7dAvgKg`, `goalWeightKg`, `startWeightKg`, `cycleEndDate`, and `referenceDate` (with default `null` / `LocalDate.now()` parameters) to `ForecastBanner` and `resolveRequiredPaceSubtitle` allows `ForecastBanner` to render the required pace line across `Projected`, `Stalled`, and `NotEnoughData` while keeping all existing call sites source-compatible.

### Alternatives Considered
- **Converting `PaceForecast.Stalled` and `PaceForecast.NotEnoughData` from `object` to `data class`**: Rejected because it would break structural equality assertions across existing test suites.
