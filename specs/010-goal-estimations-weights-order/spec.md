# Feature Specification: Goal Arrival Estimations Average Weights & Section Reordering

**Feature Branch**: `[feat/010-goal-estimations-weights-order]`

**Created**: 2026-11-01

**Status**: Ready

**Input**: User description: "I want to add the weights (weighted average, 7d,...) as the screenshot. I want also to move that section after the daily weight-in and before Pace & linear targets."

## User Scenarios & Testing *(mandatory)*

### User Story 1 - Display Rolling & Weighted Average Weights in Goal Arrival Estimations (Priority: P1)

As an athlete tracking a Fat Loss & Bodybuilding cycle on the Progress & Goals screen, I want to see the current rolling average body weight (`7D`, `14D`, `21D`, `28D`) alongside each horizon's weekly pace rate and the `4:3:2:1` weighted average body weight inside the **Weighted Average** summary line in the **Goal Arrival Estimations** card, while keeping projected weeks and arrival dates anchored to the current 7-day remaining weight to goal, so that I can see the exact smoothed weight level behind each lookback window and the blended headline projection at a glance.

**Why this priority**: Showing only the weekly rate (`-0.49 kg/wk`, `-0.35 kg/wk`, etc.) in Goal Arrival Estimations forces the athlete to mentally cross-reference what body weight average each lookback window (`7D`, `14D`, `21D`, `28D`) and the `4:3:2:1` Weighted Average currently sit at. Displaying the 1-decimal rolling average weight (e.g., `81.5 kg`, `82.1 kg`, `82.6 kg`, `83.1 kg`) and the `4:3:2:1` weighted average weight (e.g., `82.3 kg` / blended weight) directly inside each estimation row provides complete context in a single view.

**Independent Test**: View the Goal Arrival Estimations card for an active Fat Loss & Bodybuilding cycle with active horizons (`7D`, `14D`, `21D`, `28D`). Verify that:
- The `Weighted Average` summary displays `(Weighted pace of <pace> · <weightedWeight> · <weeks> wks · <date>)` (e.g., `(Weighted pace of -0.39 kg/wk · 82.3 kg · 7.9 wks · 26 Dec 2026)`).
- Each active historical horizon row displays `<N>D (<pace> · <rollingAvgWeight>)` on the left and `<weeks> wks · <date>` on the right (e.g., `7D (-0.49 kg/wk · 81.5 kg)` and `6.3 wks · 15 Dec 2026`).
- Projected weeks and arrival dates continue to be calculated from the cycle's current 7D remaining weight to goal divided by each row's 2-decimal deficit velocity.

**Acceptance Scenarios**:

1. **Given** an active Fat Loss & Bodybuilding cycle where `7D`, `14D`, `21D`, and `28D` horizons are available, **When** the `Goal Arrival Estimations` card computes each horizon row in the active display unit (`kg` or `lbs`), **Then** each horizon `N` (`7`, `14`, `21`, `28`) resolves its `N`-day rolling average weight over `[today - (N - 1), today]` rounded to 1 decimal place (`%.1f <unit>`) and formats its left label as `"${N}D (${paceDisplay2} · ${averageWeightDisplay})"` (e.g., `"7D (-0.49 kg/wk · 81.5 kg)"`).
2. **Given** an active Fat Loss & Bodybuilding cycle with 1 to 4 available pace horizons, **When** the `Weighted Average` row is computed, **Then** it calculates the `4:3:2:1` recency-weighted average of the available horizons' 1-decimal display-unit rolling average weights (`sum(w_i * avgWeight1_i) / sum(w_i)`, rounded to 1 decimal place in the active display unit) and formats the summary as `"(Weighted pace of ${paceDisplay2} · ${averageWeightDisplay} · ${rightValue})"` (e.g., `"(Weighted pace of -0.39 kg/wk · 82.1 kg · 7.9 wks · 26 Dec 2026)"`).
3. **Given** a horizon or `Weighted Average` row in the `Stalled` state (or `Projected` capped at `> 1 year`), **When** the row formats its text, **Then** it still includes the 1-decimal rolling/weighted average weight alongside the pace rate (e.g., `"14D (+0.10 kg/wk · 82.1 kg)"` with `"Pace stalled"`, and `"(Weighted pace of +0.05 kg/wk · 82.1 kg · Pace stalled)"`).
4. **Given** a historical horizon row in the `NotEnoughData` state (e.g., `28D` when the cycle only has 14 days of history), **When** the row formats its text, **Then** it displays `"${windowDays}D (—)"` on the left and `"Needs ${windowDays}+ days"` on the right without a weight value.

---

### User Story 2 - Move Goal Arrival Estimations After Daily Weigh-In and Before Pace & Linear Targets (Priority: P1)

As an athlete viewing the **Weight Progression** card on the Progress & Goals screen, I want the **Goal Arrival Estimations** section positioned immediately after the **Daily Weigh-In** section (and any active drop-streak badge) and directly before the **Pace & Linear Targets** section, so that I see my current rolling/daily weights and overall goal arrival projections first before drilling into daily scale targets (`Linear Today`, `Expected Today`, `Target Tomorrow`) and the 2x2 pace breakdown cards.

**Why this priority**: The athlete's natural reading flow starts with current status (`Start/Goal` progress bar, `Rolling Averages`, `Daily Weigh-In`), immediately answers *"When will I reach my goal at my current weighted/rolling pace?"* (`Goal Arrival Estimations`), and then answers *"What exact weight do I need on the scale today and tomorrow?"* (`Pace & Linear Targets`) followed by the detailed 2x2 pace comparison cards.

**Independent Test**: Render `WeightProgressionHeroCard` with active weigh-in data, `sameDayTrio`, `multiHorizonForecast`, and `paceTargets`. Verify that the vertical order of sections inside the card is:
1. Top Reference Track (`START`, `% to goal`, `GOAL`)
2. Hero Centerpiece (`ROLLING AVERAGES` 3-pillar row, `DAILY WEIGH-IN` 3-pillar row, and optional streak pill)
3. **Goal Arrival Estimations** (`ForecastBanner` / `MultiHorizonGoalEstimationsBanner`)
4. **Pace & Linear Targets** (`TheoreticalPaceTargetsBox`)
5. 2x2 Pace Metrics Grid (`7-Day Pace`, `14-Day Pace`, `21-Day Pace`, `28-Day Pace`)
6. `Log weigh-in` action button

**Acceptance Scenarios**:

1. **Given** a `WeightProgressionHeroCard` with both `multiHorizonForecast` (or `forecast`) and `paceTargets` non-null, **When** the card renders, **Then** `ForecastBanner` (`GOAL ARRIVAL ESTIMATIONS`) appears immediately after the Hero Centerpiece (`ROLLING AVERAGES` + `DAILY WEIGH-IN` + streak pill) and immediately above `TheoreticalPaceTargetsBox` (`PACE & LINEAR TARGETS`).
2. **Given** a `360dp`-wide viewport at both `1.0f` and `1.3f` font scales, **When** `WeightProgressionHeroCard` renders with the updated section order and average weights in `GOAL ARRIVAL ESTIMATIONS`, **Then** no text clipping, overlap, or layout overflow occurs across any section.

---

### User Story 3 - Accessibility Semantics & Display-Unit Consistency (Priority: P2)

As an athlete using TalkBack or switching between `kg` and `lbs`, I want the newly added rolling/weighted average weights in **Goal Arrival Estimations** to convert accurately into my selected unit and be included naturally in TalkBack screen-reader announcements.

**Why this priority**: Maintaining unit consistency (`kg` vs `lbs`) and merged accessibility descriptions ensures the card remains reliable and accessible for all users.

**Independent Test**: Verify in unit and Compose UI tests that switching `weightUnit` to `"lbs"` converts and rounds each horizon's rolling average weight and the `4:3:2:1` weighted average weight in `lbs`, and that each row's merged `contentDescription` includes the formatted weight.

**Acceptance Scenarios**:

1. **Given** `weightUnit = "lbs"`, **When** `computeMultiHorizonForecast` evaluates the `7D`–`28D` rows and `weightedAverageRow`, **Then** each row's `averageWeightDisplay` is formatted in `lbs` to 1 decimal place (e.g., `"179.7 lbs"`) and the `Weighted Average` weight reconciles with the `4:3:2:1` weighted mean of the available horizon weights in `lbs`.
2. **Given** the `Goal Arrival Estimations` card in `Active` state, **When** inspected by TalkBack, **Then** the `Weighted Average` row announces `"Weighted Average (Weighted pace of -0.39 kg/wk · 82.3 kg · 7.9 wks · 26 Dec 2026), On time (-0.2w)"` and each horizon row announces `"${windowDays}-day horizon (${leftLabel}): ${rightValue}, ${badge}"` including the weight in `leftLabel`.

---

### Edge Cases

- **Sparse Logging Within Lookback Window**: When computing the `N`-day rolling average weight over `[today - (N - 1), today]`, if no entry exists in `[today - (N - 1), today]` (e.g., the latest weigh-in `newest.date` is older than `today`), the window average falls back to `[newest.date - (N - 1), newest.date]` and then to `currentWeightKg` (`newest.weightKg`), ensuring every available pace horizon (`pace != null`) always has a valid non-null `averageWeightDisplay`.
- **Partial Horizon Availability (7–27 Days of History)**: When only a subset of horizons is available (e.g., `7D` and `14D` are available while `21D` and `28D` are `NotEnoughData`), the `Weighted Average` weight dynamically re-normalizes using the `4:3:2:1` weights of the available horizons (`(4 * w7 + 3 * w14) / 7`), matching the exact re-normalization used for the weighted pace rate.
- **Backward Compatibility for Direct `HorizonRowState` Construction**: `averageWeightDisplay: String? = null` defaults to `null` in `HorizonRowState.Projected` and `HorizonRowState.Stalled`. When `averageWeightDisplay` is `null`, `formatLeftLabel()` and `formatWeightedSummary()` omit the extra ` · <weight>` segment so existing unit tests constructing `HorizonRowState` directly without `averageWeightDisplay` continue to work seamlessly if needed, while `computeMultiHorizonForecast` always populates `averageWeightDisplay`.
- **Long Left Labels on Narrow Screens (`360dp` at `1.3f` Font Scale)**: Because `HorizonEstimationRowItem` now displays `"28D (-0.31 kg/wk · 83.1 kg)"` on the left and `"10.0 wks · 10 Jan 2027"` on the right, the row layout must gracefully accommodate both texts on compact viewports without clipping or colliding.

## Requirements *(mandatory)*

### Functional Requirements

- **FR-001**: `HorizonRowState.Projected` and `HorizonRowState.Stalled` MUST include an optional `averageWeightDisplay: String? = null` property holding the formatted 1-decimal rolling or weighted average weight in the active display unit (e.g., `"81.5 kg"` or `"179.7 lbs"`).
- **FR-002**: In `FatLossAnalytics.computeMultiHorizonForecast`, for each horizon `N` in `{7, 14, 21, 28}` that has a non-null `PaceRateResult`, the system MUST compute the `N`-day rolling average weight over `[today - (N - 1), today]` (falling back to `[newest.date - (N - 1), newest.date]` or `currentWeightKg` if sparse), convert and round it to 1 decimal place in the active display unit (`round1InUnit`), and format `averageWeightDisplay` as `"%.1f %s"` (e.g., `"81.5 kg"`).
- **FR-003**: In `FatLossAnalytics.computeMultiHorizonForecast`, when computing `weightedAverageRow`, the system MUST compute the `4:3:2:1` recency-weighted average of the available horizons' 1-decimal display-unit rolling average weights (`sum(w_i * avgWeight1InUnit_i) / sum(w_i)`), round to 1 decimal place (`round1`), and format `averageWeightDisplay` as `"%.1f %s"` (e.g., `"82.1 kg"`).
- **FR-004**: Projected weeks (`weeks`, `rawWeeks`) and projected arrival dates (`targetDate`) for both `weightedAverageRow` and the 4 historical rows (`7D`, `14D`, `21D`, `28D`) MUST remain anchored to `remainingInUnit` (derived from the current 7D average weight and target weight) divided by each row's 2-decimal deficit velocity (`v2`).
- **FR-005**: `HorizonEstimationRow.formatLeftLabel()` MUST format non-weighted rows with a non-null `averageWeightDisplay` as `"${windowDays}D (${state.paceDisplay2} · ${state.averageWeightDisplay})"` (and fall back to `"${windowDays}D (${state.paceDisplay2})"` when `averageWeightDisplay == null`, or `"${windowDays}D (—)"` for `NotEnoughData`).
- **FR-006**: `HorizonEstimationRow.formatWeightedSummary()` MUST format the `Weighted Average` row with a non-null `averageWeightDisplay` as:
  - `Projected`: `"(Weighted pace of ${state.paceDisplay2} · ${state.averageWeightDisplay} · ${formatRightValue()})"`
  - `Stalled`: `"(Weighted pace of ${state.paceDisplay2} · ${state.averageWeightDisplay} · Pace stalled)"`
  (falling back to omitting ` · ${state.averageWeightDisplay}` only when `averageWeightDisplay == null`).
- **FR-007**: In `WeightProgressionHeroCard`, `ForecastBanner` (which renders `MultiHorizonGoalEstimationsBanner` for `GOAL ARRIVAL ESTIMATIONS`) MUST be positioned immediately after the Hero Centerpiece section (`ROLLING AVERAGES`, `DAILY WEIGH-IN`, and optional drop-streak pill) and immediately before `TheoreticalPaceTargetsBox` (`PACE & LINEAR TARGETS`).

### Key Entities

- **HorizonRowState.Projected / HorizonRowState.Stalled**: Extended with `averageWeightDisplay: String? = null` representing the 1-decimal rolling average weight (for `7D`–`28D`) or `4:3:2:1` weighted average weight (for `Weighted Average`) in the active display unit.
- **HorizonEstimationRow**: Formats the left label (`7D (-0.49 kg/wk · 81.5 kg)`) and weighted summary (`(Weighted pace of -0.39 kg/wk · 82.3 kg · 7.9 wks · 26 Dec 2026)`) used by both visual rendering and TalkBack semantics.

## Success Criteria *(mandatory)*

### Measurable Outcomes

- **SC-001**: For any active Fat Loss & Bodybuilding cycle with 1 to 4 available pace horizons, `computeMultiHorizonForecast` populates `averageWeightDisplay` on `weightedAverageRow` and all available horizon rows (`7D`, `14D`, `21D`, `28D`) in both `kg` and `lbs`, reconciling at 1-decimal display precision.
- **SC-002**: In `WeightProgressionHeroCard`, the `GOAL ARRIVAL ESTIMATIONS` section renders directly below the `DAILY WEIGH-IN` section (and streak pill) and directly above the `PACE & LINEAR TARGETS` section.
- **SC-003**: All rows in `GOAL ARRIVAL ESTIMATIONS` and all sections in `WeightProgressionHeroCard` render without text clipping or layout overflow on a `360dp`-wide viewport at `1.0f` and `1.3f` font scales, with 100% passing unit tests, script tests, and lint checks.

## Assumptions

- The `N`-day rolling average weight for each horizon `N` (`7`, `14`, `21`, `28`) uses the same closed calendar window `[referenceDate - (N - 1), referenceDate]` and historical entry inclusion rules already established in `FatLossAnalytics.computeRollingWindowTargetRow` and `computeRollingWeightHero`.
- The `4:3:2:1` recency weights (`7D = 4`, `14D = 3`, `21D = 2`, `28D = 1`) used for the weighted average weight are identical to `FatLossAnalytics.HORIZON_WEIGHTS` used for the weighted average pace rate.
