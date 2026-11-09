# Interface Contracts: Subcycle Taxonomy & Velocity Corridors

**Feature**: `017-subcycle-velocity-corridors` | **Date**: 2026-11-09

## 1. UI Component Contract: `SPEC-WEIGHT-SUBTYPE-SHEET-02` (`SubcycleTypePickerBottomSheet`)

- **Header**:
  - Title: `"Select Subcycle Type"`
  - Subtitle: `"Changes target velocity corridors and projected finish goal."`
- **Option Cards (x3)**:
  1. **Fat Loss (`FAT_LOSS`)**:
     - Corridor Pill: `"-1.00% to -0.25%/wk"` (`#EBF3FA` fill / `#2B6CB0` text in Light; `#172A45` fill / `#63B3ED` text in Dark)
     - Description: `"Controlled caloric deficit for sustainable fat loss while preserving lean tissue."`
     - Projection: `"Projected Finish ({finishDate}): {min} {unit} – {max} {unit}"` (or `"Projected Finish: Log first weigh-in to calculate range"` when `previews.isEmpty()`)
  2. **Recomposition (`RECOMPOSITION`)**:
     - Corridor Pill: `"-0.25% to +0.25%/wk"` (`#E3F5EC` fill / `#0B6E3F` text in Light; `#133826` fill / `#2ECC71` text in Dark)
     - Description: `"Near-maintenance velocity for simultaneous fat loss and lean muscle retention/gain."`
     - Projection: `"Projected Finish ({finishDate}): {min} {unit} – {max} {unit}"`
  3. **Muscle Gain (`MUSCLE_GAIN`)**:
     - Corridor Pill: `"+0.25% to +0.75%/wk"` (`#F3E8FF` fill / `#6B21A8` text in Light; `#2E1647` fill / `#C084FC` text in Dark)
     - Description: `"Controlled lean mass accrual with bounded weekly surplus."`
     - Projection: `"Projected Finish ({finishDate}): {min} {unit} – {max} {unit}"`
- **Single-Line Corridor Pill Invariant**:
  - Enforces `maxLines = 1`, `softWrap = false`, `fontSize = 12.sp`, `fontFeatureSettings = "tnum"`, padding `horizontal = 8.dp, vertical = 4.dp`.
  - When `fontScale >= 1.5f`, card title and corridor pill stack vertically (`Column`) so neither clips.

## 2. UI Component Contract: `SPEC-WEIGHT-CARD-02` (`CycleMomentumEstimationsCard`)

- **Header**:
  - Superheader: `"GOAL ARRIVAL ESTIMATIONS"` + interactive `[Fat Loss ▾]` / `[Recomposition ▾]` / `[Muscle Gain ▾]` pill.
  - Line 1: `"Current Cycle: {cycleName} ({countdown})"`
  - Line 2: `"Optimal Range: {minPct} to {maxPct}/wk ({minRate} to {maxRate} {unit}/wk)"`
  - Line 3: `"Projected Finish Weight: {min} {unit} – {max} {unit}"`
- **Primary Composite Row (`✦ Weekly Body Weight`)**:
  - Displays composite weight (`✦ Weekly Body Weight: 74.8 kg`), velocity summary (`Pace: -0.11 kg/wk · Momentum: -0.15%/wk`), semantic status pill badge (`Optimal`, `Possible muscle loss`, `Suboptimal muscle gain`, `Warning: Fat loss phase`, `Warning: Muscle gain phase`, `Slow fat loss pace`, `Out of fat loss range`, `Slow muscle gain pace`, `Out of muscle gain range`), 5-Segment Corridor Gauge (`15% | 22.5% | 25% | 22.5% | 15%`), and conditional inline coaching subtitle (`↳ {diagnosticMessage}`) when a non-optimal or calibrating message is present.
- **Individual Horizon Rows (`7D Avg`, `14D Avg`, `21D Avg`, `28D Avg`)**:
  - Display scalar weight, weekly pace, and weekly percentage variation cleanly on a single row (`7D Avg: 74.7 kg (-0.15 kg/wk · -0.20%/wk)`) without per-row warning badges or gauge bars.

## 3. Coaching MCP Server Contract (`get_weight_momentum_analytics`)

- **Input `subcycleType`**: Accepts `"FAT_LOSS"`, `"RECOMPOSITION"`, `"MUSCLE_GAIN"` (and legacy aliases `"WEIGHT_LOSS"`, `"WEIGHT_GAIN"`).
- **Output Payload Additions & Updates**:
  ```json
  {
    "cycle_metadata": {
      "cycle_name": "Autumn Cut",
      "subcycle_type": "FAT_LOSS",
      "subcycle_display_name": "Fat Loss",
      "start_date": "2026-10-01",
      "finish_date": "2026-12-28",
      "weeks_remaining": 7.0,
      "calibration_complete": true,
      "calibration_day": 8,
      "total_timeline_days": 15
    },
    "target_corridor": {
      "min_weekly_pct": -1.0,
      "max_weekly_pct": -0.25,
      "min_pct_per_week": -1.0,
      "max_pct_per_week": -0.25,
      "inclusive_bounds": true,
      "min_weekly_kg": -0.75,
      "max_weekly_kg": -0.19,
      "min_pace_kg_per_week": -0.75,
      "max_pace_kg_per_week": -0.19
    },
    "global_safety_bounds": {
      "possible_muscle_loss_below_pct": -1.0,
      "suboptimal_muscle_gain_above_pct": 0.75
    },
    "projected_finish_weight": {
      "finish_date": "2026-12-28",
      "min_kg": 69.8,
      "max_kg": 73.6
    },
    "weekly_body_weight_composite": {
      "formula_weights": { "7d": 3, "14d": 4, "21d": 2, "28d": 1 },
      "current_kg": 74.8,
      "weight_kg": 74.8,
      "previous_week_kg": 74.91,
      "weekly_rate_kg": -0.11,
      "pace_kg_per_week": -0.11,
      "weekly_momentum_pct": -0.15,
      "momentum_pct_per_week": -0.15,
      "severity": "WARNING_AMBER",
      "status": "SLOW_FAT_LOSS_PACE",
      "diagnostic_code": "SLOW_FAT_LOSS_PACE",
      "diagnostic_badge": "Slow fat loss pace",
      "diagnostic_message": "Deficit is below -0.25%/wk. Increase deficit slightly to hit target range.",
      "zone": "RECOMPOSITION"
    }
  }
  ```
