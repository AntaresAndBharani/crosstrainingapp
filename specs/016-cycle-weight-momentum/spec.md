# Feature Specification: Universal Cycle Weight Tracking & Momentum Engine

**Feature Branch**: `feat/016-cycle-weight-momentum`

**Created**: 2026-11-09

**Status**: Approved

**Input**: User description: "Implement Universal Cycle Weight Tracking & Momentum Engine (`SPEC-WEIGHT-CARD-01` and `SPEC-WEIGHT-SUBTYPE-SHEET-01`) as described in https://share.gemini.google/5Lj5UGx6MWAP"

## User Scenarios & Testing *(mandatory)*

### User Story 1 - Daily Weight Timeline with Real vs. Theoretical Linear Interpolation & Cumulative Multi-Horizon Averages (Priority: P1)

An athlete logs scale weigh-ins at arbitrary intervals (daily, every few days, or weekly). The system builds a continuous daily weight timeline where measured days are tagged as `Real` and unlogged days between two `Real` measurements are automatically populated with `Theoretical` linearly interpolated weights. Over this continuous daily series, the system calculates 7-day, 14-day, 21-day, and 28-day moving averages, seamlessly incorporating pre-cycle history when available or building cumulatively day by day during a cold start, and computes a `3:4:2:1` weighted Weekly Body Weight composite and weekly percentage Momentum.

**Why this priority**: The continuous daily timeline, linear interpolation, cumulative/lookback multi-horizon averages (`7D`, `14D`, `21D`, `28D`), `3:4:2:1` Weekly Body Weight composite, and weekly percentage Momentum are the mathematical foundation for all goal projections, corridor diagnostics, UI displays, and coaching analytics.

**Independent Test**: Can be independently tested by feeding sparse and dense weigh-in sequences (such as weekly weigh-ins of `80.0 kg` on Friday 1 and `76.5 kg` on Friday 2, cold-start sequences from Day 1 to Day 28, and boundary edits/deletions) and verifying exact daily interpolated values, rolling horizon averages, `3:4:2:1` composite weight, and Momentum percentage variation.

**Acceptance Scenarios**:

1. **Given** an athlete logs `80.0 kg` (`Real`) on Friday Day 1 and `76.5 kg` (`Real`) on Friday Day 8 (7 days later, `-3.5 kg` change), **When** the daily weight timeline is evaluated, **Then** Saturday through Thursday (Days 2–7) are populated with `Theoretical` weights decreasing by `0.5 kg/day` (`79.5`, `79.0`, `78.5`, `78.0`, `77.5`, `77.0 kg`).
2. **Given** an existing interpolated timeline between two `Real` weights on Day 1 (`80.0 kg`) and Day 8 (`76.5 kg`), **When** the athlete logs or edits a weight on Wednesday Day 6 to `77.0 kg` (`Real`) or deletes a `Real` weight on an intermediate day, **Then** the edited day becomes `Real` (or reverts to `Theoretical` upon deletion) and linear interpolation automatically recalculates across the affected left and right `Real` boundaries.
3. **Given** an athlete starts tracking weight with no prior history (Cold Start), **When** Day 1 (`80.0 kg`) is logged, **Then** `7D`, `14D`, `21D`, and `28D` averages all equal `80.0 kg`; through Day 7 all four horizons equal the cumulative mean of Days 1..K; on Day 8 the `7D` average uses Days 2..8 while `14D`, `21D`, and `28D` use the cumulative mean of Days 1..8, continuing until each horizon reaches its full $N$-day window (while pre-cycle historical entries, if present, are used immediately on Day 1 without artificial cold start).
4. **Given** daily horizon averages $\text{Avg}_{7D}(t)$, $\text{Avg}_{14D}(t)$, $\text{Avg}_{21D}(t)$, and $\text{Avg}_{28D}(t)$ on day $t$, **When** the Weekly Body Weight composite and Momentum are calculated, **Then** $\text{BW}_{\text{weekly}}(t) = \frac{3 \cdot \text{Avg}_{7D}(t) + 4 \cdot \text{Avg}_{14D}(t) + 2 \cdot \text{Avg}_{21D}(t) + 1 \cdot \text{Avg}_{28D}(t)}{10}$ and $\text{Momentum}(t) = \left(\frac{\text{BW}_{\text{weekly}}(t) - \text{BW}_{\text{weekly}}(t-7)}{\text{BW}_{\text{weekly}}(t-7)}\right) \times 100\%$.

---

### User Story 2 - Universal Cycle Weight Tracking & Subcycle Corridor Goal Estimations Card (`SPEC-WEIGHT-CARD-01`) (Priority: P1)

Athletes and coaches viewing any training cycle (whether Strength & Weightlifting or Fat Loss & Bodybuilding) see the upgraded Goal Arrival Estimations & Weight Progression experience powered by the cycle's active Weight Subcycle Type (`Weight Loss`, `Recomposition`, or `Weight Gain`). The card displays the active subcycle pill, remaining cycle duration and finish date, target weekly corridor in both `%` and `kg/wk` (or `lbs/wk`), dynamically compounded finish weight window (`Min – Max`), the primary `✦ Weekly Body Weight` composite row with a 3-zone Corridor Range Gauge, and the `7D`, `14D`, `21D`, and `28D` horizon rows with average weight, weekly pace, weekly `%` momentum, diagnostic status label, and corridor gauge.

**Why this priority**: Delivers the primary user-facing value of the feature in the Progress & Goals screen across all cycle types, replacing static single-point fat-loss-only assumptions with subcycle-aware velocity corridors and dynamic finish weight projections.

**Independent Test**: Can be independently tested by rendering the Goal Arrival Estimations card for Strength and Fat Loss cycles across `Weight Loss`, `Recomposition`, and `Weight Gain` subcycle types, verifying the header lines, dynamic finish weight range, `✦ Weekly Body Weight` composite row, `7D`/`14D`/`21D`/`28D` rows, diagnostic status labels, and unit localization (`kg` and `lbs`).

**Acceptance Scenarios**:

1. **Given** an active cycle with Subcycle Type `Recomposition` (`-0.50%` to `+0.25%/wk`), $\text{BW}_{\text{weekly}} = 78.0\text{ kg}$, and $8.0$ weeks remaining until the cycle finish date, **When** the Goal Arrival Estimations section is rendered, **Then** it displays the `[Recomposition ▾]` pill, cycle countdown (`8.0w left`), subtype target corridor (`-0.50% to +0.25%/wk (-0.39 to +0.20 kg/wk)`), and dynamically projected finish weight range (`75.0 kg – 79.6 kg` via compounding $(1 + r)^{W_{\text{left}}}$).
2. **Given** an active `Strength & Weightlifting` cycle (not only `Fat Loss & Bodybuilding`), **When** the athlete views Cycle Goals or Weight Progression, **Then** bodyweight tracking and the Goal Arrival Estimations card are available and functional for that cycle.
3. **Given** at least 8 calendar days of weight timeline data exist, **When** the primary `✦ Weekly Body Weight` row and individual `7D`, `14D`, `21D`, and `28D` rows are evaluated, **Then** each row displays its average weight, weekly rate (`kg/wk` or `lbs/wk`), weekly percentage variation (`%/wk`), diagnostic status (`On Target (Optimal)`, `Slight Deficit Slip`, `Above Corridor`, `Possible Muscle Loss` when $< -1.50\%$, or `Excessive Weight Gain` when $> +1.50\%`), and a 3-zone Corridor Range Gauge positioning the current momentum relative to the active subcycle's target corridor.
4. **Given** fewer than 8 calendar days of total weight data exist (Cold Start Days 1–7), **When** the card is rendered, **Then** current rolling weights are displayed while Momentum and corridor status show `Calibrating (Day {K}/8)` and projected finish range awaits Day 8 calibration completion.

---

### User Story 3 - Interactive Subcycle Type Selection in Card Header & Cycle Editor (`SPEC-WEIGHT-SUBTYPE-SHEET-01`) (Priority: P2)

An athlete or coach can configure a cycle's Weight Subcycle Type (`Weight Loss`, `Recomposition`, or `Weight Gain`) when creating or editing any cycle in the Cycles screen, and can also switch the subcycle type mid-cycle directly from the Goal Arrival Estimations card header by tapping the `[Subtype ▾]` pill to open the `Select Subcycle Type` bottom sheet with live finish-weight range previews for all three options.

**Why this priority**: Enables mid-cycle nutritional phase adjustments (e.g., transitioning from `Weight Loss` to `Recomposition` or `Weight Gain`) with zero friction and immediate visual feedback on how each subcycle type changes the target corridor and projected finish weight range.

**Independent Test**: Can be independently tested by tapping the `[Subtype ▾]` pill on the Goal Arrival Estimations card, verifying that the bottom sheet displays all three subcycle radio cards with live projected finish weight windows, selecting a new subcycle type, tapping `Apply Subcycle Type`, and confirming the cycle persists the new subcycle type, updates the card immediately, and displays the confirmation message.

**Acceptance Scenarios**:

1. **Given** the Goal Arrival Estimations card is displayed for a cycle, **When** the user taps the `[Subtype ▾]` selector pill in the card header, **Then** a modal bottom sheet titled `"Select Subcycle Type"` opens displaying selectable cards for `Weight Loss` (`-1.50% to -0.50%/wk`), `Recomposition` (`-0.50% to +0.25%/wk`), and `Weight Gain` (`+0.25% to +1.50%/wk`), each pre-calculating the live projected finish weight range from current $\text{BW}_{\text{weekly}}$ and remaining cycle weeks.
2. **Given** the `Select Subcycle Type` bottom sheet is open with a new subcycle option selected, **When** the user taps `[ Apply Subcycle Type ]`, **Then** the cycle's subcycle type is persisted, the sheet dismisses, the card immediately updates its pill, target corridor, projected finish range, and gauge zones, and a snackbar confirms `"Subcycle updated to {New Subtype}. Target corridor refreshed."`
3. **Given** the user opens the New/Edit Cycle dialog in the Cycles tab for any cycle type (`Strength & Lifts` or `Fat Loss & BB`), **When** configuring the cycle, **Then** the user can select the Weight Subcycle Type (`Weight Loss`, `Recomposition`, or `Weight Gain`) and optional starting/target body weights.

---

### User Story 4 - Real vs. Theoretical Weight History Inspection & Coaching MCP Analytics (Priority: P3)

In the Body Weight history view, athletes can see their daily timeline including interpolated `Theoretical` days alongside logged `Real` weigh-ins, and can tap any `Theoretical` or `Real` day to edit its weight (which promotes a `Theoretical` day to a `Real` measurement and recalculates surrounding interpolations). Additionally, the Coaching MCP Server exposes the cycle momentum and multi-horizon analytics payload so AI coaching assistants can inspect `subcycle_type`, `target_corridor`, `projected_finish_weight`, `weekly_body_weight_composite`, and `7D`/`14D`/`21D`/`28D` horizons.

**Why this priority**: Completes the end-to-end loop for both manual correction of interpolated days by the athlete and structured telemetry inspection by AI coaching agents.

**Independent Test**: Can be independently tested by viewing the interpolated daily timeline in the Body Weight tab, editing a `Theoretical` date to promote it to `Real`, and invoking the MCP weight analytics tool to verify the structured JSON output matches the mathematical specification.

**Acceptance Scenarios**:

1. **Given** a multi-day gap between two `Real` weigh-ins, **When** the athlete inspects the daily weight timeline and edits a `Theoretical` day's weight, **Then** that date is saved as a `Real` weigh-in and neighboring `Theoretical` days are immediately re-interpolated.
2. **Given** weight entries exist for an athlete, **When** an AI coach queries the MCP server for weight momentum analytics, **Then** the MCP server returns the daily interpolated timeline summary, `3:4:2:1` `weekly_body_weight_composite`, `7D`/`14D`/`21D`/`28D` horizon averages and velocities, diagnostic momentum classification, and active subcycle corridor projections.

---

### Edge Cases

- **Same-Day Multiple Weigh-Ins**: Logging a weight on a date that already has a `Real` weight overwrites that date's weight (1 entry per calendar date) and triggers linear re-interpolation with the nearest preceding and succeeding `Real` dates.
- **Cold Start (< 8 Calendar Days)**: When the athlete has fewer than 8 total calendar days of weight history ($K < 8$), no $t-7$ anchor exists yet; the system displays rolling averages cumulatively ($1..K$) while Momentum displays `Calibrating (Day {K}/8)` and suppresses velocity alarm states.
- **Historical Lookback Across Cycle Start**: When the athlete starts a new cycle on Day 1 but already has historical weigh-ins prior to the cycle start date, the system interpolates across the cycle start boundary if needed and immediately populates `7D`, `14D`, `21D`, and `28D` windows using the prior history without forcing a cold start.
- **Unlogged Trailing Days After Last Real Weigh-In**: If today ($t$) is after the latest `Real` weigh-in date ($d_{\text{last}}$), no future `Real` anchor exists to interpolate toward; days after $d_{\text{last}}$ up to $t$ carry forward $W(d_{\text{last}})$ as `Theoretical` (flat hold) until a newer `Real` weigh-in is logged, at which point those days linearly re-interpolate between $d_{\text{last}}$ and the new `Real` weigh-in.
- **Final Week & Completed Cycle States**: When remaining cycle time is less than 1 week ($0 < \text{daysRemaining} < 7$), the timeline subtitle displays `Final Week · Ends in {N} days` and compounding uses the exact fractional week exponent ($W_{\text{left}} = \text{daysRemaining} / 7.0$). When $\text{today} \ge \text{finishDate}$, $W_{\text{left}}$ clamps to `0.0` and the header displays `Cycle Complete`. If a cycle is open-ended (`endDate == null`), the projection defaults to an 8-week reference horizon from `startDate` (or remaining weeks in the 8-week block, minimum `1.0` week rolling preview) and labels it clearly.
- **Narrow Viewports & Accessibility Font Scaling**: On narrow screens (`< 360dp`) or large system font scales (`1.3x`+), card header pills and horizon metrics wrap cleanly onto a second line without clipping or truncating numbers.

## Requirements *(mandatory)*

### Functional Requirements

- **FR-001**: System MUST support three Weight Subcycle Types across all training cycles (`STRENGTH_WEIGHTLIFTING` and `FAT_LOSS_BODYBUILDING`):
  - `WEIGHT_LOSS` ("Weight Loss"): Target weekly momentum corridor `-1.50%` to `-0.50%/wk`.
  - `RECOMPOSITION` ("Recomposition"): Target weekly momentum corridor `-0.50%` to `+0.25%/wk`.
  - `WEIGHT_GAIN` ("Weight Gain"): Target weekly momentum corridor `+0.25%` to `+1.50%/wk`.
- **FR-002**: System MUST construct a continuous daily weight timeline where logged dates have source `REAL` and unlogged calendar dates between consecutive `REAL` dates $d_A$ and $d_B$ ($\Delta t = d_B - d_A > 1$) have source `THEORETICAL` with linearly interpolated weight:
  $$W(d_A + k) = W(d_A) + k \cdot \frac{W(d_B) - W(d_A)}{\Delta t} \quad \text{for } k \in [1, \Delta t - 1]$$
- **FR-003**: System MUST reactively recalculate `THEORETICAL` weights whenever a `REAL` weight is added, edited, or deleted, or when a `THEORETICAL` date is edited by the user (which promotes that date to `REAL`).
- **FR-004**: System MUST compute 4 rolling horizon averages (`7D`, `14D`, `21D`, `28D`) over the continuous daily timeline. For each horizon $N \in \{7, 14, 21, 28\}$ on day $t$, if $K$ total calendar days are available from the earliest weight entry ($K \ge 1$), the window size is $\min(K, N)$:
  $$\text{Avg}_{ND}(t) = \frac{1}{\min(K, N)} \sum_{i=0}^{\min(K, N)-1} W(t - i)$$
- **FR-005**: System MUST use pre-cycle historical weight entries when available so a new cycle has full `7D`, `14D`, `21D`, and `28D` context on Day 1 without artificial cold-start reset.
- **FR-006**: System MUST compute the Weekly Body Weight composite ($\text{BW}_{\text{weekly}}(t)$) using the `3:4:2:1` weighting across the 4 horizons:
  $$\text{BW}_{\text{weekly}}(t) = \frac{3 \cdot \text{Avg}_{7D}(t) + 4 \cdot \text{Avg}_{14D}(t) + 2 \cdot \text{Avg}_{21D}(t) + 1 \cdot \text{Avg}_{28D}(t)}{10}$$
- **FR-007**: System MUST compute weekly Momentum percentage variation and absolute weekly pace by comparing day $t$ against day $t - 7$:
  $$\text{Momentum}(t) = \left(\frac{\text{BW}_{\text{weekly}}(t) - \text{BW}_{\text{weekly}}(t - 7)}{\text{BW}_{\text{weekly}}(t - 7)}\right) \times 100\%, \quad \text{Pace}(t) = \text{BW}_{\text{weekly}}(t) - \text{BW}_{\text{weekly}}(t - 7)$$
  and analogously for each individual horizon $N \in \{7, 14, 21, 28\}$ comparing $\text{Avg}_{ND}(t)$ against $\text{Avg}_{ND}(t - 7)$.
- **FR-008**: System MUST enforce an 8-calendar-day warm-up calibration gate: when fewer than 8 calendar days exist ($K < 8$), Momentum and horizon velocities MUST report calibration status `Calibrating (Day {K}/8)`.
- **FR-009**: System MUST classify calibrated weekly percentage Momentum into diagnostic zones:
  - `POSSIBLE_MUSCLE_LOSS` ("Possible Muscle Loss"): $\text{Momentum} < -1.50\%$
  - `WEIGHT_LOSS` ("Weight Loss"): $-1.50\% \le \text{Momentum} < -0.50\%$
  - `RECOMPOSITION` ("Recomposition"): $-0.50\% \le \text{Momentum} \le +0.25\%$
  - `WEIGHT_GAIN` ("Controlled Lean Gain" / "Weight Gain"): $+0.25\% < \text{Momentum} \le +1.50\%$
  - `EXCESSIVE_WEIGHT_GAIN` ("Excessive Weight Gain"): $\text{Momentum} > +1.50\%$
  and evaluate corridor compliance against the active subcycle's `[Min%, Max%]` target range (`On Target (Optimal)` when inside `[Min%, Max%]`, `Slight Deficit Slip` / `Below Corridor` when below `Min%` but $\ge -1.50\%$, `Above Corridor` / `Surplus Slip` when above `Max%` but $\le +1.50\%$, and critical alert when $< -1.50\%$ or $> +1.50\%$).
- **FR-010**: System MUST dynamically project the cycle's finish weight window (`[BW_target_min, BW_target_max]`) from current $\text{BW}_{\text{weekly}}$ and remaining cycle weeks $W_{\text{left}} = \max(0.0, \text{daysRemaining} / 7.0)$:
  $$\text{BW}_{\text{target\_min}} = \text{BW}_{\text{weekly}} \times \left(1 + \frac{\text{Min}\%}{100}\right)^{W_{\text{left}}}, \quad \text{BW}_{\text{target\_max}} = \text{BW}_{\text{weekly}} \times \left(1 + \frac{\text{Max}\%}{100}\right)^{W_{\text{left}}}$$
- **FR-011**: System MUST render the `GOAL ARRIVAL ESTIMATIONS` card (`SPEC-WEIGHT-CARD-01`) across all cycle types with:
  1. Top-right interactive Subcycle Type pill (`[Weight Loss ▾]`, `[Recomposition ▾]`, or `[Weight Gain ▾]`) with minimum `48dp` touch target.
  2. Header summary lines for Current Cycle & remaining weeks/end date, Subtype Target corridor (`%/wk` and localized `kg/wk` or `lbs/wk`), and Projected Finish Weight range (`Min – Max`).
  3. Highlighted `✦ Weekly Body Weight` primary composite row with current composite weight, weekly pace, weekly `%` momentum, status badge, and 3-zone Corridor Range Gauge.
  4. Individual `7D`, `14D`, `21D`, and `28D` horizon rows with average weight, weekly pace, weekly `%` momentum, status label, and 3-zone Corridor Range Gauge.
- **FR-012**: System MUST provide the `Select Subcycle Type` modal bottom sheet (`SPEC-WEIGHT-SUBTYPE-SHEET-01`) when tapping the `[Subtype ▾]` pill, showing selectable radio cards for `Weight Loss`, `Recomposition`, and `Weight Gain` with live projected finish weight windows, an `[ Apply Subcycle Type ]` confirmation button, persistence to the cycle, and snackbar feedback.
- **FR-013**: System MUST allow selecting the Weight Subcycle Type (`Weight Loss`, `Recomposition`, `Weight Gain`) and optional body weight baseline/targets when creating or editing any cycle in `CyclesScreen`.
- **FR-014**: System MUST allow viewing `REAL` and `THEORETICAL` daily weights in the Body Weight history section and editing any `THEORETICAL` day to promote it to a `REAL` weigh-in.
- **FR-015**: Coaching MCP Server MUST expose cycle weight momentum analytics (interpolated timeline metrics, `3:4:2:1` weekly composite, `7D`/`14D`/`21D`/`28D` horizons, momentum classification, and subcycle corridor projection) in its weight analytics tool output.

### Key Entities

- **WeightSubcycleType**: Enumeration of `WEIGHT_LOSS` (`-1.50%` to `-0.50%/wk`), `RECOMPOSITION` (`-0.50%` to `+0.25%/wk`), and `WEIGHT_GAIN` (`+0.25%` to `+1.50%/wk`), associated with every `Cycle`.
- **DailyWeightPoint**: A single calendar date entry in the continuous daily weight timeline with `date: LocalDate`, `weightKg: Double`, `source: WeightMeasureSource` (`REAL` or `THEORETICAL`), and `notes: String`.
- **HorizonMomentumMetric**: Rolling average weight, $t-7$ anchor average weight, weekly pace (`kg/wk`), weekly percentage momentum (`%/wk`), diagnostic zone, corridor status, and normalized gauge marker position (`0.0f..1.0f`) for a specific horizon (`7D`, `14D`, `21D`, `28D`) or the `3:4:2:1` Weekly Body Weight composite.
- **CycleMomentumState**: Complete domain state for `SPEC-WEIGHT-CARD-01` and `SPEC-WEIGHT-SUBTYPE-SHEET-01`, encapsulating the active `WeightSubcycleType`, calibration progress, remaining weeks, target corridor bounds, projected finish weight window (`min..max`), primary composite metric, 4 horizon metrics, and live preview projections for all 3 subcycle types.

## Success Criteria *(mandatory)*

### Measurable Outcomes

- **SC-001**: Linear interpolation between any two `REAL` weigh-ins separated by $\Delta t > 1$ days produces `THEORETICAL` daily values accurate to $10^{-6}\text{ kg}$ before display rounding (`0.1` for weights, `0.01` for weekly paces and percentages).
- **SC-002**: Cold-start cumulative windows (Days 1–28) and historical lookback windows produce exact `7D`, `14D`, `21D`, and `28D` moving averages and `3:4:2:1` Weekly Body Weight composite values across 100% of unit test scenarios.
- **SC-003**: Switching Subcycle Type via `SPEC-WEIGHT-SUBTYPE-SHEET-01` updates the persisted cycle, target corridor, projected finish weight range, and corridor gauges in a single user flow (2 taps: pill -> option -> Apply).
- **SC-004**: 100% of existing unit tests, new unit tests, PowerShell script tests, Android lint checks, and MCP server tests pass with zero regressions.

## Assumptions

- `Cycle` persistence encodes the selected `WeightSubcycleType` backward-compatibly (preserving Room database version `10` schema compatibility per Constitution Principle V) and defaults intelligently based on cycle configuration (`WEIGHT_LOSS` when a cycle has `targetWeightKg < startingWeightKg` or `type == FAT_LOSS_BODYBUILDING` without an explicit gain target, `WEIGHT_GAIN` when `targetWeightKg > startingWeightKg`, and `RECOMPOSITION` otherwise).
- `REAL` weigh-ins are stored in the `weight_entries` table and synchronized with Cloud Firestore; `THEORETICAL` daily weights are deterministically derived in the domain layer between consecutive `REAL` entries, and editing a `THEORETICAL` weight persists a new `REAL` entry for that date in `weight_entries`.
