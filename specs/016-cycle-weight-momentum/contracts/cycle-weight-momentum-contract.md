# Interface & UI Contracts: Universal Cycle Weight Tracking & Momentum Engine

**Feature**: `016-cycle-weight-momentum`
**Date**: 2026-11-09

## 1. UI Contract: `SPEC-WEIGHT-CARD-01` (Goal Arrival Estimations Card)

### Header Section
- **Superheader Row**:
  - Left: `"GOAL ARRIVAL ESTIMATIONS"`
  - Right: Interactive Subcycle Selector Pill (`"[{SubcycleType.label} ▾]"`), minimum `48dp` touch target, triggers `HapticFeedbackType` / `CLOCK_TICK` and opens `SubcycleTypePickerBottomSheet` (`SPEC-WEIGHT-SUBTYPE-SHEET-01`).
  - Accessibility `contentDescription`: `"Current subcycle type: {SubcycleType.label}. Double tap to change subcycle type."`
- **Line 1 (Cycle & Countdown)**:
  - Standard ($\ge 1.0\text{w}$ left): `"Current Cycle: {cycleName} ({W_left}w left · Ends {finishDate})"`
  - Final Week ($0 < \text{daysRemaining} < 7$): `"Current Cycle: {cycleName} (Final Week · Ends in {N} days)"`
  - Completed ($\text{daysRemaining} \le 0$): `"Current Cycle: {cycleName} (Cycle Complete · Ended {finishDate})"`
- **Line 2 (Subtype Target Corridor)**:
  - Format: `"Subtype Target: {Min%}% to {Max%}%/wk ({MinRate} to {MaxRate} {unit}/wk)"`
  - Example: `"Subtype Target: -0.50% to +0.25%/wk (-0.39 to +0.20 kg/wk)"`
- **Line 3 (Projected Finish Weight Window)**:
  - Calibrated ($K \ge 8$) or previewable: `"Projected Finish Weight: {MinWeight} – {MaxWeight} {unit}"` (or `"Calibrating (Day {K}/8) — unlocks on Day 8"` when $K < 8$).

### Primary Composite Metric Row (`✦ Weekly Body Weight`)
- **Title & Weight**: `"✦ Weekly Body Weight: {weight} {unit}"` (`3:4:2:1` weighted average of `7D`, `14D`, `21D`, `28D`).
- **Velocity Summary**: `"Pace: {pace} {unit}/wk · Momentum: {momentum}%/wk"` (or `"Calibrating (Day {K}/8)"`).
- **3-Zone Corridor Range Gauge**:
  - Left 25%: Below Target Corridor
  - Center 50%: Active Subtype Target Corridor (`[Min%, Max%]`)
  - Right 25%: Above Target Corridor
  - Marker / Fill Color: Optimal Green (`#0B6E3F` / `#2ECC71`) when in Zone 2; Warning Amber (`#D97706` / `#F59E0B`) when in mild deviation (`-1.50% <= m < Min%` or `Max% < m <= +1.50%`); Alert Red (`#DC2626` / `#EF4444`) when `< -1.50%` (Possible Muscle Loss) or `> +1.50%` (Excessive Weight Gain).

### Multi-Horizon Rows (`7D`, `14D`, `21D`, `28D`)
- **Left Label**: `"{N}D Avg: {weight} {unit} ({pace} {unit}/wk · {momentum}%/wk)"` (or `"{N}D Avg: {weight} {unit} (Calibrating Day {K}/8)"`).
- **Right Status Label**: `"On Target (Optimal)"`, `"Slight Deficit Slip"`, `"Above Corridor"`, `"Possible Muscle Loss"`, `"Excessive Weight Gain"`, or `"Calibrating (Day {K}/8)"`.
- **Corridor Range Gauge**: 3-zone track with marker aligned to the horizon's weekly `%` momentum.

---

## 2. UI Contract: `SPEC-WEIGHT-SUBTYPE-SHEET-01` (Subcycle Picker Bottom Sheet)

- **Title**: `"Select Subcycle Type"`
- **Subtitle**: `"Changes target velocity corridors and projected finish goal."`
- **Radio Cards**:
  1. `Weight Loss` (`-1.50% to -0.50%/wk`) + live `"Projected Finish ({finishDate}): {Min} – {Max} {unit}"`
  2. `Recomposition` (`-0.50% to +0.25%/wk`) + live `"Projected Finish ({finishDate}): {Min} – {Max} {unit}"`
  3. `Weight Gain` (`+0.25% to +1.50%/wk`) + live `"Projected Finish ({finishDate}): {Min} – {Max} {unit}"`
- **Primary Action Button**: `"[ Apply Subcycle Type ]"`
  - Persists `cycle.withSubcycleType(selectedType)` via `AppViewModel.updateCycleSubcycleType(cycle, selectedType)`.
  - Dismisses bottom sheet and triggers snackbar: `"Subcycle updated to {selectedType.label}. Target corridor refreshed."`

---

## 3. MCP Server Contract (`mcp-server/src/mcp.ts`)

### Tool: `get_weight_momentum_analytics` (and enhanced `list_weight_entries`)
- **Input Parameters**:
  - `subcycleType` (optional: `"WEIGHT_LOSS" | "RECOMPOSITION" | "WEIGHT_GAIN"`, defaults to `"RECOMPOSITION"`)
  - `cycleName` (optional: string, defaults to `"Active Cycle"`)
  - `cycleStartDate` (optional: `YYYY-MM-DD`)
  - `cycleFinishDate` (optional: `YYYY-MM-DD`)
  - `asOfDate` (optional: `YYYY-MM-DD`)
  - `includeDailyTimeline` (optional: boolean, defaults to `false`)
- **Output Payload**:
```json
{
  "cycle_metadata": {
    "cycle_name": "Recomposition",
    "subcycle_type": "RECOMPOSITION",
    "start_date": "2026-10-01",
    "finish_date": "2026-12-26",
    "weeks_remaining": 8.0,
    "calibration_complete": true,
    "calibration_day": 8,
    "total_timeline_days": 35
  },
  "target_corridor": {
    "min_weekly_pct": -0.50,
    "max_weekly_pct": 0.25,
    "min_weekly_kg": -0.39,
    "max_weekly_kg": 0.20
  },
  "projected_finish_weight": {
    "min_kg": 75.0,
    "max_kg": 79.6
  },
  "weekly_body_weight_composite": {
    "formula_weights": { "7d": 3, "14d": 4, "21d": 2, "28d": 1 },
    "current_kg": 78.0,
    "previous_week_kg": 78.3,
    "weekly_rate_kg": -0.30,
    "weekly_momentum_pct": -0.38,
    "status": "ON_TARGET",
    "zone": "RECOMPOSITION"
  },
  "horizons": {
    "7d":  { "avg_kg": 77.8, "rate_kg_wk": -0.49, "momentum_pct_wk": -0.63, "status": "BELOW_CORRIDOR" },
    "14d": { "avg_kg": 78.0, "rate_kg_wk": -0.31, "momentum_pct_wk": -0.40, "status": "ON_TARGET" },
    "21d": { "avg_kg": 78.1, "rate_kg_wk": -0.23, "momentum_pct_wk": -0.29, "status": "ON_TARGET" },
    "28d": { "avg_kg": 78.3, "rate_kg_wk": -0.20, "momentum_pct_wk": -0.25, "status": "ON_TARGET" }
  }
}
```
