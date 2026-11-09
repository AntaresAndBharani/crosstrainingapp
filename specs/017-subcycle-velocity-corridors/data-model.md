# Data Model: Subcycle Taxonomy & Velocity Corridors (`SPEC-WEIGHT-SUBTYPE-SHEET-02` & `SPEC-WEIGHT-CARD-02`)

**Feature**: `017-subcycle-velocity-corridors` | **Date**: 2026-11-09

## 1. Enumerations & Value Types

### `WeightSubcycleType` (`app/.../data/model/Enums.kt`)

| Enum Constant | Legacy Alias | Display `label` | `minWeeklyPct` | `maxWeeklyPct` | `description` |
|---|---|---|---|---|---|
| `FAT_LOSS` | `WEIGHT_LOSS` | `"Fat Loss"` | `-1.00` | `-0.25` | `"Controlled caloric deficit for sustainable fat loss while preserving lean tissue."` |
| `RECOMPOSITION` | — | `"Recomposition"` | `-0.25` | `+0.25` | `"Near-maintenance velocity for simultaneous fat loss and lean muscle retention/gain."` |
| `MUSCLE_GAIN` | `WEIGHT_GAIN` | `"Muscle Gain"` | `+0.25` | `+0.75` | `"Controlled lean mass accrual with bounded weekly surplus."` |

### `MomentumZone` (`app/.../data/analytics/FatLossAnalytics.kt`)

Universal 5-zone physiological classification of weekly momentum ($\text{Mom}$):
- `CALIBRATING("Calibrating")`: $\text{Mom} == \text{null}$ ($K < 8$ days)
- `POSSIBLE_MUSCLE_LOSS("Possible muscle loss")`: $\text{Mom} < -1.00\%$
- `FAT_LOSS("Fat Loss")`: $-1.00\% \le \text{Mom} < -0.25\%$ (with companion alias `WEIGHT_LOSS = FAT_LOSS`)
- `RECOMPOSITION("Recomposition")`: $-0.25\% \le \text{Mom} \le +0.25\%$
- `MUSCLE_GAIN("Muscle Gain")`: $+0.25\% < \text{Mom} \le +0.75\%$ (with companion alias `WEIGHT_GAIN = MUSCLE_GAIN`)
- `SUBOPTIMAL_MUSCLE_GAIN("Suboptimal muscle gain")`: $\text{Mom} > +0.75\%$ (with companion alias `EXCESSIVE_WEIGHT_GAIN = SUBOPTIMAL_MUSCLE_GAIN`)

### `MomentumSeverity` (`app/.../data/analytics/FatLossAnalytics.kt`)

Semantic severity tier driving UI badge fill, text color, and gauge bar styling:
- `OPTIMAL_GREEN`: Optimal in-corridor velocity (`#E3F5EC` fill / `#0B6E3F` text in Light, `#133826` fill / `#2ECC71` text in Dark)
- `WARNING_AMBER`: Phase drift or slow pace (`#FEF3C7` fill / `#D97706` text in Light, `#3B2A14` fill / `#FBBF24` text in Dark)
- `CRITICAL_RED`: Tissue risk (`< -1.00%` or `> +0.75%`) or wrong-direction velocity (`#FEE2E2` fill / `#DC2626` text in Light, `#3B1818` fill / `#F87171` text in Dark)
- `NEUTRAL`: Cold-start calibration (`< 8` days)

### `CorridorComplianceStatus` (`app/.../data/analytics/FatLossAnalytics.kt`)

| Status Constant | Severity | Default Badge Label | Default Diagnostic Message |
|---|---|---|---|
| `CALIBRATING` | `NEUTRAL` | `"Calibrating"` | `"Collecting initial 8-day baseline to calculate weekly momentum."` |
| `ON_TARGET` | `OPTIMAL_GREEN` | `"Optimal"` | `null` |
| `POSSIBLE_MUSCLE_LOSS` | `CRITICAL_RED` | `"Possible muscle loss"` | `"Losing faster than -1.00%/wk. High risk of lean muscle loss."` |
| `SUBOPTIMAL_MUSCLE_GAIN` | `CRITICAL_RED` | `"Suboptimal muscle gain"` | `"Gaining faster than +0.75%/wk. Surplus exceeds optimal muscle synthesis rate."` |
| `WARNING_FAT_LOSS_PHASE` | `WARNING_AMBER` | `"Warning: Fat loss phase"` | `"Pace is below -0.25%/wk. Warning: You are currently in a fat loss phase."` |
| `WARNING_MUSCLE_GAIN_PHASE` | `WARNING_AMBER` | `"Warning: Muscle gain phase"` | `"Pace exceeds +0.25%/wk. Warning: You are currently in a muscle gain phase."` |
| `SLOW_FAT_LOSS_PACE` | `WARNING_AMBER` | `"Slow fat loss pace"` | `"Deficit is below -0.25%/wk. Increase deficit slightly to hit target range."` |
| `OUT_OF_FAT_LOSS_RANGE` | `CRITICAL_RED` | `"Out of fat loss range"` | `"Weight is stable or increasing. You are outside a caloric deficit."` |
| `SLOW_MUSCLE_GAIN_PACE` | `WARNING_AMBER` | `"Slow muscle gain pace"` | `"Surplus is below +0.25%/wk. Increase intake slightly to optimize growth."` |
| `OUT_OF_MUSCLE_GAIN_RANGE` | `CRITICAL_RED` | `"Out of muscle gain range"` | `"Weight is stable or dropping. Caloric surplus required for muscle gain."` |

*(Legacy aliases `EXCESSIVE_WEIGHT_GAIN = SUBOPTIMAL_MUSCLE_GAIN`, `BELOW_CORRIDOR = WARNING_FAT_LOSS_PHASE`, `ABOVE_CORRIDOR = WARNING_MUSCLE_GAIN_PHASE` provided on companion object for backward compatibility).*

## 2. Domain Data Classes

### `CorridorDiagnosticResult` (`app/.../data/analytics/FatLossAnalytics.kt`)

- `status: CorridorComplianceStatus`
- `severity: MomentumSeverity`
- `badgeLabel: String`
- `diagnosticMessage: String?`

### `HorizonMomentumRow` (`app/.../data/analytics/FatLossAnalytics.kt`)

- `windowDays: Int` (`0` for primary `3:4:2:1` composite, `7`/`14`/`21`/`28` for individual horizons)
- `isPrimaryComposite: Boolean`
- `effectiveWindowDays: Int`
- `currentWeightKg: Double`
- `currentWeightInUnit: Double`
- `anchorWeightKg: Double?`
- `weeklyPaceKg: Double?`
- `weeklyPaceInUnit: Double?`
- `weeklyMomentumPct: Double?`
- `zone: MomentumZone`
- `complianceStatus: CorridorComplianceStatus`
- `severity: MomentumSeverity`
- `statusLabel: String`
- `diagnosticMessage: String?`
- `gaugePosition: Float` (`[0.0f, 1.0f]` across the 5-segment gauge)
