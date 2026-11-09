# Data Model: Universal Cycle Weight Tracking & Momentum Engine

**Feature**: `016-cycle-weight-momentum`
**Date**: 2026-11-09

## 1. `WeightSubcycleType` (`com.fractanomics.crosstraining.data.model.WeightSubcycleType`)

Represents the active body composition velocity regime for any training cycle.

| Enum Value | Display Label | Min Weekly `%` (`minWeeklyPct`) | Max Weekly `%` (`maxWeeklyPct`) | Light Pill Fill / Text | Dark Pill Fill / Text |
|------------|---------------|---------------------------------|---------------------------------|------------------------|-----------------------|
| `WEIGHT_LOSS` | `"Weight Loss"` | `-1.50` | `-0.50` | `#EBF3FA` / `#2B6CB0` | `#172A45` / `#63B3ED` |
| `RECOMPOSITION` | `"Recomposition"` | `-0.50` | `+0.25` | `#E3F5EC` / `#0B6E3F` | `#133826` / `#2ECC71` |
| `WEIGHT_GAIN` | `"Weight Gain"` | `+0.25` | `+1.50` | `#F3E8FF` / `#6B21A8` | `#2E1647` / `#C084FC` |

### `Cycle` Entity Extensions (`Cycle.kt`)
- `Cycle.goal` supports optional leading metadata tag `[subcycle=WEIGHT_LOSS|RECOMPOSITION|WEIGHT_GAIN]`.
- `@get:Ignore val subcycleType: WeightSubcycleType`:
  - Returns the explicitly encoded `WeightSubcycleType` if present in `goal`.
  - Otherwise infers default:
    - If `startingWeightKg != null && targetWeightKg != null`:
      - `targetWeightKg < startingWeightKg` $\rightarrow$ `WEIGHT_LOSS`
      - `targetWeightKg > startingWeightKg` $\rightarrow$ `WEIGHT_GAIN`
      - `targetWeightKg == startingWeightKg` $\rightarrow$ `RECOMPOSITION`
    - Else if `type == CycleType.FAT_LOSS_BODYBUILDING` $\rightarrow$ `WEIGHT_LOSS`
    - Else $\rightarrow$ `RECOMPOSITION`
- `@get:Ignore val hasExplicitSubcycleType: Boolean`
- `@get:Ignore val cleanGoal: String`: User-visible cycle focus/notes with any `[subcycle=...]` prefix stripped.
- `fun withSubcycleType(subcycleType: WeightSubcycleType): Cycle`: Returns a copy of `Cycle` with `goal` updated to encode `subcycleType` while preserving `cleanGoal`.

---

## 2. Daily Weight Timeline & Interpolation (`DailyWeightPoint`)

| Field | Type | Description |
|-------|------|-------------|
| `date` | `LocalDate` | Calendar date in the continuous sequence $[d_{\text{first}}, d_{\text{end}}]$. |
| `weightKg` | `Double` | Scalar weight in kg (exact logged value for `REAL`, linearly interpolated value for `THEORETICAL`). |
| `source` | `WeightMeasureSource` | `REAL` (direct scale log in `weight_entries`) or `THEORETICAL` (linearly interpolated between surrounding `REAL` logs). |
| `notes` | `String` | Original notes for `REAL` entries, or `"Interpolated"` for `THEORETICAL` entries. |

### Interpolation Rules
- Given active (`deletedAtMillis == null`) `WeightEntry` records sorted ascending by `date` (deduplicated by date):
  - For each consecutive pair $(E_a, E_b)$ with $\Delta t = \text{DAYS.between}(E_a.\text{date}, E_b.\text{date}) > 1$:
    - For $k \in 1 until \Delta t$:
      $$W(E_a.\text{date} + k) = E_a.\text{weightKg} + k \cdot \frac{E_b.\text{weightKg} - E_a.\text{weightKg}}{\Delta t}$$
      tagged with `source = WeightMeasureSource.THEORETICAL`.
  - If `referenceDate != null && referenceDate > lastRealEntry.date`:
    - Trailing unlogged days $(lastRealEntry.date + 1 .. referenceDate)$ hold `lastRealEntry.weightKg` with `source = WeightMeasureSource.THEORETICAL` until a newer `REAL` entry is logged.

---

## 3. Multi-Horizon & Composite Momentum Models (`FatLossAnalytics.kt`)

### `DailyHorizonSnapshot`
Computed for every calendar day $t$ in the continuous timeline (where $K \ge 1$ is the 1-based day count from the first `REAL` entry):
- `date: LocalDate`
- `dayIndex: Int` ($K$)
- `dailyWeightKg: Double`
- `source: WeightMeasureSource`
- `avg7dKg: Double` — mean of $\min(K, 7)$ days ending at $t$
- `avg14dKg: Double` — mean of $\min(K, 14)$ days ending at $t$
- `avg21dKg: Double` — mean of $\min(K, 21)$ days ending at $t$
- `avg28dKg: Double` — mean of $\min(K, 28)$ days ending at $t$
- `weeklyBodyWeightKg: Double` — $\frac{3 \cdot \text{avg7dKg} + 4 \cdot \text{avg14dKg} + 2 \cdot \text{avg21dKg} + 1 \cdot \text{avg28dKg}}{10.0}$

### `MomentumZone`
Classification of weekly percentage momentum $m = \frac{W(t) - W(t-7)}{W(t-7)} \times 100\%$:
- `CALIBRATING`: $K < 8$ (`"Calibrating (Day {K}/8)"`)
- `POSSIBLE_MUSCLE_LOSS`: $m < -1.50\%$ (`"Possible Muscle Loss"`)
- `WEIGHT_LOSS`: $-1.50\% \le m < -0.50\%$ (`"Weight Loss"`)
- `RECOMPOSITION`: $-0.50\% \le m \le +0.25\%$ (`"Recomposition"`)
- `WEIGHT_GAIN`: $+0.25\% < m \le +1.50\%$ (`"Controlled Lean Gain"`)
- `EXCESSIVE_WEIGHT_GAIN`: $m > +1.50\%$ (`"Excessive Weight Gain"`)

### `CorridorComplianceStatus`
Evaluates a calibrated momentum $m$ against the active `WeightSubcycleType` target corridor $[\text{Min}\%, \text{Max}\%]$:
- `CALIBRATING` (`"Calibrating (Day {K}/8)"`)
- `ON_TARGET` (`"On Target (Optimal)"`): $\text{Min}\% \le m \le \text{Max}\%$
- `POSSIBLE_MUSCLE_LOSS` (`"Possible Muscle Loss"`): $m < -1.50\%$
- `EXCESSIVE_WEIGHT_GAIN` (`"Excessive Weight Gain"`): $m > +1.50\%$
- `BELOW_CORRIDOR` (`"Slight Deficit Slip"` / `"Below Target Corridor"`): $-1.50\% \le m < \text{Min}\%$
- `ABOVE_CORRIDOR` (`"Above Corridor"` / `"Slight Surplus"`): $\text{Max}\% < m \le +1.50\%$

### `HorizonMomentumRow`
Represents either the primary `✦ Weekly Body Weight` composite (`windowDays = 0, isPrimaryComposite = true`) or one of the 4 horizons (`windowDays` $\in \{7, 14, 21, 28\}$):
- `windowDays: Int` (`0` for Weekly Body Weight composite; `7`, `14`, `21`, `28` for horizons)
- `isPrimaryComposite: Boolean`
- `effectiveWindowDays: Int` ($\min(K, \text{windowDays})$ for horizons; $\min(K, 28)$ for composite)
- `currentWeightKg: Double`
- `currentWeightInUnit: Double`
- `anchorWeightKg: Double?` (at $t - 7$ when $K \ge 8$)
- `weeklyPaceKg: Double?` ($W(t) - W(t-7)$)
- `weeklyPaceInUnit: Double?`
- `weeklyMomentumPct: Double?` ($\frac{W(t) - W(t-7)}{W(t-7)} \times 100\%$)
- `zone: MomentumZone`
- `complianceStatus: CorridorComplianceStatus`
- `statusLabel: String`
- `gaugePosition: Float` (`0.0f..1.0f`, where `0.25f..0.75f` is the active subtype target corridor)

### `SubcyclePreviewProjection`
Live projection for each of the 3 options in `SPEC-WEIGHT-SUBTYPE-SHEET-01`:
- `subcycleType: WeightSubcycleType`
- `minWeeklyPct: Double`
- `maxWeeklyPct: Double`
- `minWeeklyRateInUnit: Double`
- `maxWeeklyRateInUnit: Double`
- `projectedMinWeightInUnit: Double`
- `projectedMaxWeightInUnit: Double`
- `corridorDisplayText: String` (e.g., `"-0.50% to +0.25%/wk (-0.39 to +0.20 kg/wk)"`)
- `projectedFinishRangeText: String` (e.g., `"76.5 kg – 81.2 kg"`)

### `CycleMomentumState`
Top-level state powering `SPEC-WEIGHT-CARD-01` and `SPEC-WEIGHT-SUBTYPE-SHEET-01`:
- `Empty`: Zero `REAL` weight entries exist.
- `Active`:
  - `cycleName: String`
  - `subcycleType: WeightSubcycleType`
  - `weightUnit: String` (`"kg"` or `"lbs"`)
  - `referenceDate: LocalDate`
  - `finishDate: LocalDate` (explicit `cycle.endDate` or default `cycle.startDate.plusWeeks(8)`)
  - `isOpenEndedCycle: Boolean`
  - `daysRemaining: Long`
  - `weeksRemaining: Double` ($\max(0.0, \text{daysRemaining} / 7.0)$)
  - `isCycleComplete: Boolean`
  - `isFinalWeek: Boolean` ($0 < \text{daysRemaining} < 7$)
  - `totalTimelineDays: Int` ($K$)
  - `isCalibrating: Boolean` ($K < 8$)
  - `calibrationDay: Int` ($\min(K, 8)$)
  - `weeklyBodyWeightRow: HorizonMomentumRow` (`3:4:2:1` composite)
  - `horizonRows: List<HorizonMomentumRow>` (`7D`, `14D`, `21D`, `28D`)
  - `activeProjection: SubcyclePreviewProjection`
  - `allSubcyclePreviews: List<SubcyclePreviewProjection>` (for `Weight Loss`, `Recomposition`, `Weight Gain`)
  - `cycleTimelineHeaderText: String`
  - `subtypeTargetHeaderText: String`
  - `projectedFinishHeaderText: String`
