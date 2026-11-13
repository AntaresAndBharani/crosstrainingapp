# Research: End-of-Cycle Estimated Body Weight on Weekly Body Weight

**Feature**: `020-estimated-finish-weight` (`SPEC-020` / `SPEC-WEIGHT-CARD-03`)
**Date**: 2026-11-13

## Decisions & Rationale

### 1. Dual Projection Formula (Compounded Momentum % + Linear Pace)
- **Decision**: Compute both:
  - **Compounded Momentum %**: `BW_weekly * max(0.0, 1.0 + displayedMomentumPct / 100.0)^weeksRemaining`
  - **Linear Pace**: `max(0.0, BW_weekly_unit + round2(pace_unit) * weeksRemaining)`
- **Rationale**:
  - Compounded Momentum uses the exact same exponential compounding model as `Projected Finish Weight` (`computeSubcyclePreviewProjection`), guaranteeing that any `Optimal` momentum reading within a subcycle's percentage corridor lands mathematically inside `[Projected Min, Projected Max]`.
  - Linear Pace gives the athlete an intuitive flat `kg/wk` (`lbs/wk`) extrapolation matching the `Pace:` value displayed right above it.
- **Alternatives Considered**: Showing only compounded Momentum or only linear Pace — rejected by explicit user preference during `/grill-me` discovery (`Show both — compounded Momentum % estimate with linear Pace estimate alongside it`).

### 2. Display-Unit Arithmetic Reconciliation (Constitution Principle II)
- **Decision**:
  - Evaluate compounded Momentum using `displayedMomentumPct(momentumPct)` (`round2(momentumPct)`), which is the exact 2-decimal percentage rendered in `"Momentum: X.XX%/wk"`.
  - Evaluate linear Pace using `round2(paceKg)` for `kg` and `round2(paceInUnit)` for the active display unit (`kg` or `lbs`), which is the exact 2-decimal pace rendered in `"Pace: X.XX <unit>/wk"`.
- **Rationale**: Prevents subtle UI contradictions where, e.g., a tiny positive drift of `+0.003 kg/wk` renders `"Pace: 0.00 kg/wk"` while drifting by `+0.1 kg` over 20 weeks, or where a `-0.251%` raw momentum renders `"-0.25%/wk"` (`Optimal`) yet projects slightly outside the `-0.25%` `Projected Finish Weight` bound.

### 3. Placement, Calibration Gate & Horizon Scope
- **Decision**:
  - Render `"Estimated Finish Weight: X.X <unit> (Momentum) · Y.Y <unit> (Pace)"` inside the `✦ Weekly Body Weight` composite section immediately below `"Pace: ... · Momentum: ..."` and above `CorridorRangeGauge`, styled in `bodySmall` with `tnum`, `FontWeight.SemiBold`, and the row's semantic `badgeText` color.
  - Omit the calculation (`null`) and UI line during Days 1–7 (`isCalibrating == true`) and on `7D`, `14D`, `21D`, `28D` horizon rows (`isPrimaryComposite == false`).
- **Rationale**: Preserves `SPEC-017` (`SPEC-WEIGHT-CARD-02`) clean scalar telemetry on the `7D`–`28D` rows, avoids displaying uncalibrated/empty placeholders during Days 1–7, and avoids wrapping/clipping on `360dp` screens at `1.3x` font scale by leaving `maxLines`/`softWrap` unrestricted.

### 4. Backward-Compatible Data Model Extension & MCP Parity
- **Decision**:
  - Add `EstimatedFinishWeights` data class, append 4 nullable properties with `= null` defaults on `HorizonMomentumRow`, and append `estimatedFinishWeightText: String? = null` on `CycleMomentumState.Active`.
  - Add `estimated_finish_weight_momentum_kg` and `estimated_finish_weight_pace_kg` to `weekly_body_weight_composite` in `mcp-server/src/schemas.ts` (`computeWeightMomentumAnalytics`).
- **Rationale**: Default `= null` parameters preserve 100% source compatibility with existing `HorizonMomentumRow(...)` test fixtures. Additive JSON fields in `weekly_body_weight_composite` preserve backward compatibility while ensuring Android/MCP parity (Constitution Principle VII). No Room schema migration (`v10`) is required.
