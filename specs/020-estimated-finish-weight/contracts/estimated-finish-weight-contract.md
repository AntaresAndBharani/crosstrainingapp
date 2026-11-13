# Contracts: End-of-Cycle Estimated Body Weight (`SPEC-020`)

## 1. Android UI & Accessibility Contract (`SPEC-WEIGHT-CARD-03`)

### Presentation (`CycleMomentumEstimationsCard` in `ProgressScreen.kt`)
- **Section**: Inside the `✦ Weekly Body Weight` primary composite `Column`, rendered **immediately below** `resolveWeeklyBodyWeightVelocitySummary(...)` (`"Pace: ... · Momentum: ..."`) and **immediately above** `CorridorRangeGauge(...)`.
- **Visibility**:
  - Shown **only** when `row.isPrimaryComposite == true` and both `row.estimatedFinishMomentumInUnit` and `row.estimatedFinishPaceInUnit` are non-null (i.e., `Day 8+` calibrated).
  - Hidden (`null`) during Days 1–7 calibration (`isCalibrating == true`) and never rendered on `7D Avg`, `14D Avg`, `21D Avg`, or `28D Avg` rows.
- **Exact String Format**:
  `"Estimated Finish Weight: %.1f %s (Momentum) · %.1f %s (Pace)"` (with `FatLossAnalytics.round1(...)`, `Locale.US`, e.g., `"Estimated Finish Weight: 72.5 kg (Momentum) · 72.4 kg (Pace)"` or `"Estimated Finish Weight: 159.8 lbs (Momentum) · 159.6 lbs (Pace)"`).
- **Typography & Color**:
  - `style = MaterialTheme.typography.bodySmall.copy(fontFeatureSettings = "tnum")`
  - `fontWeight = FontWeight.SemiBold`
  - `color = badgeText` (resolved via `resolveMomentumBadgeColors(primaryRow.severity, isDark)`)
  - No `maxLines = 1` or `softWrap = false` restriction so text wraps cleanly at `360dp` width and `1.3x`+ font scale.

### Accessibility (`resolveHorizonMomentumContentDescription`)
- When calibrated composite (`estimatedFinishText != null`):
  `"<Title>, <Pace/Momentum summary>, Estimated Finish Weight: <X.X> <unit> (Momentum) · <Y.Y> <unit> (Pace), <StatusLabel>[, <DiagnosticMessage>]"`
- When calibrating (`estimatedFinishText == null`):
  `"<Title>, Calibrating (Day K/8), Calibrating (Day K/8), Collecting initial 8-day baseline to calculate weekly momentum."` (unchanged from `SPEC-017`).

## 2. Remote MCP Tool Contract (`get_weight_momentum_analytics`)

- **Input Schema**: Unchanged (zero `$ref`, zero `exclusiveMinimum`).
- **Output (`weekly_body_weight_composite`)**:
  ```json
  {
    "weekly_body_weight_composite": {
      "formula_weights": { "7d": 3, "14d": 4, "21d": 2, "28d": 1 },
      "current_kg": 74.7,
      "previous_week_kg": 75.06,
      "weekly_rate_kg": -0.36,
      "weekly_momentum_pct": -0.47,
      "estimated_finish_weight_momentum_kg": 72.5,
      "estimated_finish_weight_pace_kg": 72.4,
      "status": "ON_TARGET",
      "diagnostic_code": "ON_TARGET",
      "severity": "OPTIMAL_GREEN",
      "diagnostic_badge": "Optimal",
      "diagnostic_message": null,
      "zone": "FAT_LOSS"
    }
  }
  ```
- During `< 8` day calibration, `estimated_finish_weight_momentum_kg` and `estimated_finish_weight_pace_kg` are `null`.
