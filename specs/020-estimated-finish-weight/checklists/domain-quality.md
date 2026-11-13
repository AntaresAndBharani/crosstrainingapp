# Domain & UX Quality Checklist: End-of-Cycle Estimated Body Weight (`SPEC-020`)

**Purpose**: Reviewer-owned requirements-quality gate (`Depth: Standard`, `Audience: Reviewer (PR)`, `Focus: Analytics Consistency & Mobile/MCP Parity`)
**Created**: 2026-11-13
**Feature**: [spec.md](../spec.md)

## Mathematical & Display-Unit Consistency

- [x] CHK001 Does `spec.md` (`FR-001`) define compounded Momentum finish weight using the displayed 2-decimal weekly Momentum `%` (`displayedMomentumPct`) and non-negative base clamping `max(0, 1 + displayedMomentumPct / 100)^weeksRemaining`?
- [x] CHK002 Does `spec.md` (`FR-002`) define linear Pace finish weight using the displayed 2-decimal weekly Pace in the active display unit (`max(0, BW_weekly_unit + round2(pace_unit) * weeksRemaining)`) so displayed numbers never drift?
- [x] CHK003 Does `spec.md` (`SC-001`) guarantee that any `Optimal` weekly momentum inside a subcycle corridor falls inside the inclusive `[Projected Min, Projected Max]` finish range in both `kg` and `lbs`?
- [x] CHK004 Does `spec.md` (`FR-007`) specify deterministic behavior for completed cycles (`daysRemaining <= 0` → `weeksRemaining = 0.0`, converging to current `Weekly Body Weight`) and open-ended cycles (`cycleStartDate + 8 weeks`)?

## Calibration Gate, Horizon Scope & UI Placement

- [x] CHK005 Does `spec.md` (`FR-004`) require both finish estimates to be `null` and the UI line omitted during the 8-day calibration period (`Days 1–7`)?
- [x] CHK006 Does `spec.md` (`FR-003`) explicitly restrict end-of-cycle finish estimates to the primary `✦ Weekly Body Weight` composite row, leaving `7D`, `14D`, `21D`, and `28D` rows free of finish estimates?
- [x] CHK007 Does `spec.md` (`FR-005`) and `plan.md` pin exact placement (directly below `"Pace: ... · Momentum: ..."` and above `CorridorRangeGauge`), exact copy (`"Estimated Finish Weight: <X.X> <unit> (Momentum) · <Y.Y> <unit> (Pace)"`), and status-color alignment (`badgeText`)?

## Responsive Layout, Accessibility & MCP Parity

- [x] CHK008 Does `spec.md` (`FR-006`) require natural text wrapping without `maxLines = 1` / `softWrap = false` truncation on `360dp` viewports up to `1.3x` font scale?
- [x] CHK009 Does `spec.md` (`FR-006`, `SC-004`) require the calibrated `✦ Weekly Body Weight` merged accessibility description to announce `Estimated Finish Weight` in logical order while preserving exact calibrating announcements?
- [x] CHK010 Does `plan.md` preserve backward compatibility on `HorizonMomentumRow` (default `= null` values for all 4 new properties) and Room schema stability (`v10`, zero migrations)?
- [x] CHK011 Does `spec.md` (`FR-008`) and `contracts/estimated-finish-weight-contract.md` specify `estimated_finish_weight_momentum_kg` and `estimated_finish_weight_pace_kg` (`0.1 kg` rounding, or `null` during calibration) on `weekly_body_weight_composite` without breaking flat MCP input schemas (`0` `$ref`, `0` `exclusiveMinimum`)?
- [x] CHK012 Are all local & pre-PR verification commands (`testDebugUnitTest`, `Invoke-ScriptTests.ps1`, `lintDebug`, `mcp-server` build/test, `assembleDebug` → `local_test\latest.apk`) captured in `plan.md` and `quickstart.md`?

## Notes

- All 12 items audited against `spec.md`, `plan.md`, `data-model.md`, and `contracts/estimated-finish-weight-contract.md` and marked `[x]`.
