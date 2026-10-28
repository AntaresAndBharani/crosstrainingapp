# Phase 1 Data Model: Fix Expected Today Pace & Linear Targets Across Rolling Windows (7D–28D)

## Overview

This feature modifies only the pure analytical calculation inside `FatLossAnalytics.computeRollingWindowTargetRow` (`app/src/main/java/com/fractanomics/crosstraining/data/analytics/FatLossAnalytics.kt`). No Room database entities, schema versions, or UI state classes require structural field additions or removals.

## Domain Value Objects (Existing — Behavior Updated)

### 1. `RollingWindowTargetRow`
Located in `com.fractanomics.crosstraining.data.analytics.RollingWindowTargetRow`:
- `horizon: TargetHorizon` — One of `DAYS_7(7, "7D")`, `DAYS_14(14, "14D")`, `DAYS_21(21, "21D")`, `DAYS_28(28, "28D")`.
- `paceStatus: WindowCalculationStatus` — Status/values for the `Pace` mode column (`dailyPaceRateKg` = required daily rate from current `7D` rolling average to `goalTargetWeightKg` on `goalTargetDate`, or `planPaceRateKg` fallback).
- `linearStatus: WindowCalculationStatus` — Status/values for the `Linear` mode column (`dailyLinearRateKg` = fixed daily rate from `goalStartWeightKg` on `goalStartDate` to `goalTargetWeightKg` on `goalTargetDate`).

### 2. `WindowCalculationStatus`
Sealed interface in `com.fractanomics.crosstraining.data.analytics.WindowCalculationStatus`:
- `Ready`:
  - `expectedScaleTodayDisplay: Double` — **Updated calculation**:
    - Let $N = \text{horizon.days}$, $t = \text{referenceDate}$, $\text{anchorDate} = t - N$.
    - $\text{yesterdayNdAvgKg} = \text{mean}(\text{weights in } [t - N, t - 1]) \text{ ?: } \text{priorLookbackAvgKg}$.
    - $\text{yesterdayNdInUnit} = \operatorname{toUnit}(\text{yesterdayNdAvgKg}, \text{weightUnit})$.
    - $\text{targetAvgTodayInUnit} = \text{yesterdayNdInUnit} + \text{dailyRequiredRateInUnit}$.
    - Using $k_{\text{today}}$ entries in $[t - (N - 1), t - 1]$ with sum $\text{sumPriorTodayInUnit}$:
      $$\text{expectedScaleTodayDisplay} = \operatorname{round2}\!\left((k_{\text{today}} + 1) \cdot \text{targetAvgTodayInUnit} - \text{sumPriorTodayInUnit}\right)$$
  - `targetScaleTomorrowDisplay: Double` — **Unchanged calculation**:
    - Using $k_{\text{tomorrow}}$ entries in $[t - (N - 1), t]$ with sum $\text{sumPriorTomorrowInUnit}$ and current $N$-day average $\text{currentNdInUnit}$ over $[t - (N - 1), t]$:
      $$\text{targetAvgTomorrowInUnit} = \text{currentNdInUnit} + \text{dailyRequiredRateInUnit}$$
      $$\text{targetScaleTomorrowDisplay} = \operatorname{round2}\!\left((k_{\text{tomorrow}} + 1) \cdot \text{targetAvgTomorrowInUnit} - \text{sumPriorTomorrowInUnit}\right)$$
  - `baselineNdDisplay: Double` — **Updated**: $\operatorname{round2}(\text{yesterdayNdInUnit})$, representing the baseline $N$-day rolling average from which today's target steps.
  - `currentNdDisplay: Double` — **Unchanged**: $\operatorname{round2}(\text{currentNdInUnit})$.
  - `dailyRequiredRateDisplay: Double` — **Unchanged**: $\operatorname{round2}(\text{dailyRequiredRateInUnit})$.
- `NeedsLog(requiredDate: LocalDate)` — Emitted when `earliestDate <= anchorDate` (`t - N`) and `[t - 2N + 1, t - N]` contains zero active weigh-ins.
- `InsufficientHistory` — Emitted when `allActiveEntries` is empty, `earliestDate > anchorDate` (`t - N`), or `dailyRequiredRateKg == null`.

## State Transitions & Validation Invariants

1. **Maturity Guard Invariant**:
   - If `earliestDate > t - N` $\implies$ `InsufficientHistory`.
   - Else if `computeWindowAverageKg(allActiveEntries, t - 2N + 1, t - N) == null` $\implies$ `NeedsLog(requiredDate = t - N)`.
   - Else $\implies$ `Ready`.
2. **1-Day Continuity Invariant**:
   - In `Ready` state, both `Expected Today` and `Target Tomorrow` advance their respective baseline $N$-day rolling averages (`[t - N, t - 1]` and `[t - (N - 1), t]`) by `1 * dailyRequiredRateInUnit` before solving for the single day's scale weight.
