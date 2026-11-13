# Data Model: End-of-Cycle Estimated Body Weight on Weekly Body Weight

**Feature**: `020-estimated-finish-weight` (`SPEC-020`)
**Persistence Impact**: None (pure derived domain analytics; Room database remains at `v10`; no Firestore schema migration required).

## Android Domain Models (`FatLossAnalytics.kt`)

### 1. `EstimatedFinishWeights` (New)
Pure immutable value object encapsulating both end-of-cycle finish estimates computed from a calibrated `Weekly Body Weight` composite:

| Field | Type | Description |
|-------|------|-------------|
| `momentumWeightKg` | `Double` | Compounded finish estimate in `kg`: `BW_weekly_kg * max(0.0, 1.0 + displayedMomentumPct / 100.0)^effectiveWeeks` |
| `momentumWeightInUnit` | `Double` | Compounded finish estimate in active display unit (`kg` or `lbs`) |
| `paceWeightKg` | `Double` | Linear pace finish estimate in `kg`: `max(0.0, BW_weekly_kg + round2(paceKg) * effectiveWeeks)` |
| `paceWeightInUnit` | `Double` | Linear pace finish estimate in active display unit: `max(0.0, BW_weekly_unit + round2(paceInUnit) * effectiveWeeks)` |

### 2. `HorizonMomentumRow` (Extended)
All new fields default to `null` for backward compatibility and remain `null` when `isPrimaryComposite == false` or `isCalibrating == true`:

| Field | Type | Default | Description |
|-------|------|---------|-------------|
| `estimatedFinishMomentumKg` | `Double?` | `null` | Compounded Momentum finish estimate in `kg` |
| `estimatedFinishMomentumInUnit` | `Double?` | `null` | Compounded Momentum finish estimate in active display unit |
| `estimatedFinishPaceKg` | `Double?` | `null` | Linear Pace finish estimate in `kg` |
| `estimatedFinishPaceInUnit` | `Double?` | `null` | Linear Pace finish estimate in active display unit |

### 3. `CycleMomentumState.Active` (Extended)

| Field | Type | Default | Description |
|-------|------|---------|-------------|
| `estimatedFinishWeightText` | `String?` | `null` | Pre-formatted display string `"Estimated Finish Weight: %.1f %s (Momentum) · %.1f %s (Pace)"`, or `null` during calibration |

## Remote MCP Output Model (`mcp-server/src/schemas.ts`)

### `weekly_body_weight_composite` (Extended)
Returned by `computeWeightMomentumAnalytics` and exposed via `get_weight_momentum_analytics`:

| Field | Type | Description |
|-------|------|-------------|
| `estimated_finish_weight_momentum_kg` | `number \| null` | 1-decimal rounded compounded finish estimate: `roundTo(weeklyComposite * max(0, 1 + compositeMomentumPct / 100)^(daysRemaining / 7), 1)`, or `null` when calibrating |
| `estimated_finish_weight_pace_kg` | `number \| null` | 1-decimal rounded linear finish estimate: `roundTo(max(0, weeklyComposite + compositeRateKg * (daysRemaining / 7)), 1)`, or `null` when calibrating |
