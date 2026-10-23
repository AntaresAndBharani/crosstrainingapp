# Phase 1 Data Model: 21D/28D Pace Grid & Visual Multi-Horizon Goal Estimations Card

## Domain Models (`com.fractanomics.crosstraining.data.analytics`)

### 1. `PaceRateResult` (Extended)
Existing properties preserved (`rateKgPerWeek`, `anchorWeightKg`, `anchorDate`, `currentWeightKg`, `currentDate`, `elapsedDays`, `trend`, `deficitVelocityKgPerWeek`, `formatDisplay(unit)`).
- **New Method**:
  - `formatDisplay2(unit: String = "kg"): String`:
    - Converts `rateKgPerWeek` to `unit` (`kg` or `lbs`) and rounds via `FatLossAnalytics.round2`.
    - Returns `String.format(Locale.US, "%.2f %s", rounded2, unitLabel)` when `rateInUnit < -0.05`, `String.format(Locale.US, "+%.2f %s", rounded2, unitLabel)` when `rateInUnit > 0.05`, and `String.format(Locale.US, "0.00 %s", unitLabel)` otherwise.

### 2. `RequiredPaceResult` (Extended)
Existing properties preserved (`requiredRateInUnit`, `remainingWeightInUnit`, `daysRemaining`, `weeksRemaining`, `endDate`, `weightUnit`, `exceedsSustainableRate`).
- **New Properties & Methods**:
  - `requiredRate2InUnit: Double`: Signed 2-decimal weekly required pace in `weightUnit` (`-maxOf(0.01, FatLossAnalytics.round2(remainingWeightInUnit / weeksRemaining))`).
  - `formatRate2(): String`: Formats `requiredRate2InUnit` to 2 decimal places (`"-0.39 kg/wk"` or `"-0.86 lbs/wk"`).
  - `formatBenchmarkHeader(): String`: Formats `"Target: ${formatEndDate()} (${String.format(Locale.US, "%.1f", weeksRemaining)}w left) · Required: ${formatRate2()}"` (plus `" · ⚠️ >1% BW/wk"` when `exceedsSustainableRate`).

### 3. `MultiHorizonForecastState`
Sealed hierarchy representing the state of the redrawn Goal Estimations card:
- **`MultiHorizonForecastState.GoalReached`**:
  - `displayText = "Goal reached! 🎉"`
- **`MultiHorizonForecastState.CycleEnded`**:
  - `displayText = "Cycle ended"`
- **`MultiHorizonForecastState.NotEnoughData`**:
  - `displayText = "Logging check-ins for 7 days enables pace projections"`
- **`MultiHorizonForecastState.Active`**:
  - `remainingWeightInUnit: Double` — 1-decimal remaining weight to goal in `weightUnit`.
  - `targetWeightInUnit: Double` — 1-decimal target weight in `weightUnit`.
  - `weightUnit: String` — `"kg"` or `"lbs"`.
  - `requiredPace: RequiredPaceResult?` — Non-null when `cycle.endDate` is in the future (`daysRemaining > 0`).
  - `rows: List<HorizonEstimationRow>` — Ordered list of 4 rows for `windowDays` in `[7, 14, 21, 28]`.

### 4. `HorizonEstimationRow` & `HorizonRowState`
- **`HorizonEstimationRow`**:
  - `windowDays: Int` (`7`, `14`, `21`, or `28`)
  - `state: HorizonRowState`
- **`HorizonRowState`** (Sealed interface):
  - **`HorizonRowState.NotEnoughData(val windowDays: Int)`**:
    - Emitted when `pace == null`.
  - **`HorizonRowState.Stalled(val windowDays: Int, val paceDisplay2: String)`**:
    - Emitted when `pace != null` and `pace.trend != PaceTrend.LOSS` or `round2(toUnit(pace.deficitVelocityKgPerWeek, weightUnit)) <= 0.00`.
  - **`HorizonRowState.Projected`**:
    - `windowDays: Int`
    - `paceDisplay2: String` (e.g., `"-0.28 kg/wk"`)
    - `velocity2InUnit: Double` (positive 2-decimal deficit velocity in `weightUnit`)
    - `weeks: Double` (capped at `52.0`)
    - `rawWeeks: Double` (`remainingWeightInUnit / velocity2InUnit`)
    - `targetDate: LocalDate?` (`null` when `isCapped`)
    - `barFraction: Float` (`[0.05f, 1.0f]`)
    - `weeksDeltaVsTarget: Double?` (`round1(rawWeeks - requiredPace.weeksRemaining)` when `requiredPace != null`, else `null`)
    - `isOnTrack: Boolean` (`!isCapped && (weeksDeltaVsTarget == null || weeksDeltaVsTarget <= 0.05)`)
    - `deltaBadgeText: String?` (`"On time (-X.Xw)"`, `"On time"`, `"+X.Xw late"`, `"> 1 yr late"`, or `null`)
