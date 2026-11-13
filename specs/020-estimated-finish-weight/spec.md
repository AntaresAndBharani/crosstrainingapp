# Feature Specification: End-of-Cycle Estimated Body Weight on Weekly Body Weight

**Feature Branch**: `feat/020-estimated-finish-weight`

**Created**: 2026-11-13

**Status**: Draft

**Input**: User description: "Similar to the projected finish weight, I want to see based on the weekly body weight the estimated body weight at the end of the cycle."

## User Scenarios & Testing *(mandatory)*

### User Story 1 - Dual End-of-Cycle Body Weight Estimation from Weekly Body Weight (Priority: P1)

As an athlete tracking body weight during any active training cycle, I want to see my estimated body weight at the end of the cycle calculated directly from my current Weekly Body Weight and current weekly trend—using both compounded weekly momentum percentage and linear weekly pace—so I can immediately compare my actual trajectory against the cycle's Projected Finish Weight range.

**Why this priority**: The `GOAL ARRIVAL ESTIMATIONS` card currently shows where the target subcycle corridor would land (`Projected Finish Weight`) alongside the athlete's current `Weekly Body Weight`, `Pace`, and `Momentum`, forcing the athlete to calculate manually whether their current trend will overshoot, undershoot, or land inside that target finish range.

**Independent Test**: Can be independently verified by evaluating the analytics engine against calibrated weigh-in histories across `Fat Loss`, `Recomposition`, and `Muscle Gain` subcycles in both `kg` and `lbs` and confirming both compounded Momentum and linear Pace finish weights match expected mathematical formulas and reconcile with the displayed `Weekly Body Weight`, `Pace`, and `Momentum`.

**Acceptance Scenarios**:

1. **Given** a calibrated training cycle (`>= 8` days on the daily weight timeline) with `Weekly Body Weight` = `74.7 kg`, `Pace` = `-0.36 kg/wk`, `Momentum` = `-0.47%/wk`, and `6.4` weeks remaining (`45` days), **When** end-of-cycle finish estimations are computed, **Then** the compounded Momentum finish estimate evaluates to `72.5 kg` and the linear Pace finish estimate evaluates to `72.4 kg`.
2. **Given** a calibrated training cycle whose displayed weekly Momentum percentage equals an exact subcycle corridor boundary (e.g., `-1.00%/wk` or `-0.25%/wk` in `Fat Loss`, or `+0.75%/wk` in `Muscle Gain`), **When** end-of-cycle finish estimations are computed in `kg` or `lbs`, **Then** the compounded Momentum finish estimate equals the corresponding `Projected Finish Weight` corridor bound to 1-decimal display precision, and any `Optimal` momentum lands inside the inclusive `[Projected Min, Projected Max]` display range.
3. **Given** a calibrated training cycle with near-zero weekly velocity whose displayed `Pace` is `0.00 kg/wk` (`0.00 lbs/wk`) and displayed `Momentum` is `0.00%/wk`, **When** end-of-cycle finish estimations are projected across multiple remaining weeks, **Then** both Momentum and Pace finish estimates reconcile cleanly to the displayed `Weekly Body Weight` without hidden floating-point drift.

---

### User Story 2 - Contextual Card Presentation, Semantic Status Coloring & Calibration Gate (Priority: P2)

As an athlete viewing the `GOAL ARRIVAL ESTIMATIONS` card on the Progress screen, I want the estimated finish weight line to appear right below `Pace · Momentum` and above the 5-segment corridor gauge inside the primary `✦ Weekly Body Weight` section—colored with the row's status color, hidden while still calibrating during Days 1–7, absent from the `7D`–`28D` horizon rows, readable without truncation on compact `360dp` screens at enlarged font sizes, and announced clearly by screen readers.

**Why this priority**: Placing the dual estimate directly beneath the velocity telemetry and above the visual gauge gives immediate trajectory context without cluttering the `7D`–`28D` rows or violating the 8-day calibration gate.

**Independent Test**: Can be independently verified by resolving the presentation text and accessibility announcement helpers for calibrated, calibrating, `kg`, `lbs`, and `7D`–`28D` rows and verifying exact formatting, color alignment, non-truncation layout behavior, and screen-reader output.

**Acceptance Scenarios**:

1. **Given** a calibrated cycle in `kg` (`Weekly Body Weight: 74.7 kg`, Momentum finish `72.5 kg`, Pace finish `72.4 kg`), **When** the `GOAL ARRIVAL ESTIMATIONS` card renders the `✦ Weekly Body Weight` section, **Then** the line `"Estimated Finish Weight: 72.5 kg (Momentum) · 72.4 kg (Pace)"` appears directly beneath `"Pace: -0.36 kg/wk · Momentum: -0.47%/wk"` and above the corridor gauge bar, styled in tabular numerals with the row's semantic status color (`Optimal` green, `Warning` amber, or `Critical` red).
2. **Given** a cycle in the initial 8-day calibration period (`Days 1–7`, where `Pace` and `Momentum` are unavailable), **When** the `GOAL ARRIVAL ESTIMATIONS` card renders, **Then** no `Estimated Finish Weight` line is shown inside `✦ Weekly Body Weight` and the existing `"Calibrating (Day K/8)"` status and helper subtitle remain cleanly displayed.
3. **Given** a calibrated cycle, **When** the `7D Avg`, `14D Avg`, `21D Avg`, and `28D Avg` rolling horizon rows render below the divider, **Then** those rows remain clean single-line scalar summaries without finish weight estimates, badges, or gauges.
4. **Given** a screen reader inspects the `✦ Weekly Body Weight` composite row when calibrated, **Then** the merged accessibility description announces the `Weekly Body Weight`, `Pace · Momentum`, `Estimated Finish Weight: <val1> <unit> (Momentum) · <val2> <unit> (Pace)`, status badge label, and any diagnostic helper message in logical reading order.

---

### User Story 3 - Connected Assistant & Remote Analytics Parity (Priority: P3)

As an athlete asking my connected AI fitness assistant about my cycle weight progress, I want the remote weight momentum analytics response to include the exact same Momentum-compounded and Pace-linear end-of-cycle weight estimates on the `Weekly Body Weight` composite summary as the mobile app.

**Why this priority**: Athletes using connected assistants and the mobile app together expect identical finish weight numbers when reviewing their weekly body weight trend.

**Independent Test**: Can be independently tested by invoking the remote weight momentum analytics calculation with calibrating, calibrated, and completed-cycle weigh-in fixtures and asserting exact parity with the mobile analytics engine.

**Acceptance Scenarios**:

1. **Given** a calibrated weigh-in series queried through the remote weight momentum analytics tool, **When** the analytics payload is returned, **Then** the `weekly_body_weight_composite` object includes `estimated_finish_weight_momentum_kg` and `estimated_finish_weight_pace_kg` rounded to 1 decimal place (`0.1 kg`), matching the mobile compounded Momentum and linear Pace results.
2. **Given** a weigh-in series in the `< 8` day calibration period, **When** the remote weight momentum analytics tool is invoked, **Then** both `estimated_finish_weight_momentum_kg` and `estimated_finish_weight_pace_kg` are `null`.

---

### Edge Cases

- **Calibration Period (`Days 1–7`)**: Because weekly `Pace` and `Momentum` require an 8-day baseline (`Day t` vs. `Day t - 7`), both finish estimates remain `null` and the UI line is omitted on Days 1–7.
- **Completed or Past-Due Cycles (`daysRemaining <= 0`)**: Remaining weeks clamp to `0.0`, so both compounded Momentum and linear Pace finish estimates equal the current `Weekly Body Weight` (matching `Projected Finish Weight`).
- **Open-Ended Cycles (no explicit end date)**: Uses the standard 8-week horizon from cycle start date already used for `Projected Finish Weight`.
- **Extreme Negative Velocity**: Compounding base `1 + Momentum% / 100` and linear finish weight are clamped at a minimum of `0.0` so pathological rate inputs can never produce negative or undefined finish weights.
- **Unit Switching (`kg` vs. `lbs`)**: Display-unit rounding (`2` decimals on displayed `Pace` and `Momentum%`, `1` decimal on displayed finish weights) is applied consistently in the active display unit so displayed telemetry and finish projections never contradict each other.
- **Compact Viewports & Enlarged Typography**: On `360dp`-wide screens at `1.3x` font scale, the `Estimated Finish Weight` text wraps naturally onto a second line rather than clipping or ellipsizing.

## Requirements *(mandatory)*

### Functional Requirements

- **FR-001**: System MUST compute a compounded Momentum end-of-cycle estimated weight from the calibrated `Weekly Body Weight` composite using the displayed 2-decimal weekly Momentum percentage (`BW_weekly * max(0, 1 + displayedMomentumPct / 100)^weeksRemaining`), evaluated in both `kg` and the active display unit (`kg` or `lbs`).
- **FR-002**: System MUST compute a linear Pace end-of-cycle estimated weight from the calibrated `Weekly Body Weight` composite using the displayed 2-decimal weekly Pace in the active display unit (`max(0, BW_weekly_unit + displayedPace_unit * weeksRemaining)`), alongside the corresponding `kg` value.
- **FR-003**: System MUST apply end-of-cycle estimated finish weight calculations exclusively to the primary `✦ Weekly Body Weight` (`3:4:2:1` composite) row; individual rolling horizon rows (`7D Avg`, `14D Avg`, `21D Avg`, `28D Avg`) MUST NOT compute or display end-of-cycle finish weights.
- **FR-004**: During the 8-day warm-up calibration gate (`Days 1–7`, when weekly pace and momentum are unavailable), end-of-cycle estimated finish weights MUST remain `null` and the `Estimated Finish Weight` UI line MUST NOT render.
- **FR-005**: When calibrated (`Day 8+`), the `✦ Weekly Body Weight` section of the `GOAL ARRIVAL ESTIMATIONS` card MUST render `"Estimated Finish Weight: <X.X> <unit> (Momentum) · <Y.Y> <unit> (Pace)"` directly beneath `"Pace: ... · Momentum: ..."` and above the 5-segment corridor gauge bar, using tabular numerals, semi-bold weight, and the composite row's semantic status color (`Optimal` green, `Warning` amber, or `Critical` red).
- **FR-006**: The `Estimated Finish Weight` text MUST wrap cleanly without single-line truncation or horizontal clipping on `360dp`-wide mobile screens up to `1.3x` font scale, and MUST be included in the merged screen-reader accessibility announcement of the calibrated `✦ Weekly Body Weight` row.
- **FR-007**: For completed cycles (`daysRemaining <= 0`), remaining weeks MUST clamp to `0.0` so both finish estimates converge to the current `Weekly Body Weight`; for open-ended cycles (no configured end date), projections MUST use the same default 8-week horizon from cycle start date as `Projected Finish Weight`.
- **FR-008**: The remote weight momentum analytics output MUST expose `estimated_finish_weight_momentum_kg` and `estimated_finish_weight_pace_kg` (`0.1 kg` precision, or `null` during calibration) on `weekly_body_weight_composite`, matching mobile calculations.

### Key Entities

- **Weekly Body Weight Composite Row**: Represents the `3:4:2:1` weighted moving-average body weight, 7-day pace, weekly percentage momentum, semantic corridor status, gauge position, and (when calibrated) the dual compounded-Momentum and linear-Pace end-of-cycle finish weight estimates.
- **Estimated Finish Weights**: Derived projection pair containing both the compounded Momentum finish weight and linear Pace finish weight in `kg` and the active display unit (`kg` or `lbs`), remaining `null` until 8-day calibration completes.

## Success Criteria *(mandatory)*

### Measurable Outcomes

- **SC-001**: 100% of calibrated cycles with `Optimal` weekly momentum produce a compounded Momentum finish weight inside the inclusive displayed `[Projected Min, Projected Max]` finish weight range in both `kg` and `lbs`.
- **SC-002**: 100% of calibrated `kg` and `lbs` scenarios reconcile displayed `Weekly Body Weight`, `Pace`, `Momentum`, and `Estimated Finish Weight` without hidden floating-point drift (including `0.00` velocity horizons and completed cycles).
- **SC-003**: 0 `Estimated Finish Weight` lines or values appear during the 8-day calibration window (`Days 1–7`) or on `7D`, `14D`, `21D`, and `28D` horizon rows.
- **SC-004**: 100% of calibrated composite accessibility announcements include the formatted `Estimated Finish Weight` string alongside velocity, status, and diagnostic guidance.
- **SC-005**: Remote analytics unit tests and mobile unit tests verify identical rounded `kg` Momentum and Pace finish estimates across calibrated, calibrating, and completed-cycle fixtures.

## Assumptions

- End-of-cycle projections continue to use the existing calendar-day remaining horizon (`daysRemaining / 7.0`, defaulting open-ended cycles to `cycleStartDate + 8 weeks` and clamping completed cycles at `0.0`).
- Local persistence requires no database schema changes because `Estimated Finish Weight` values are deterministically derived from existing weight entries and active cycle dates.
- Remote analytics (`kg` output) uses `kg` directly because display-unit conversion (`kg` vs. `lbs`) is a mobile presentation setting.
