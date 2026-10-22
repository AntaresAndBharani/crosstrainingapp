# Feature Specification: Rolling Window Average Pacing & Goal Forecast Alignment

**Feature Branch**: `[001-rolling-weight-pace]`

**Created**: 2026-10-22

**Status**: Ready

**Input**: User description: "c:\Users\rogal\.gemini\antigravity\brain\6580e34d-3cc0-4795-876e-6034608ae56f\weight_pace_and_forecast_plan.md"

## User Scenarios & Testing *(mandatory)*

### User Story 1 - Smoothed 7-Day and 14-Day Pace Rates with Pre-Cycle Continuity (Priority: P1)

As an athlete tracking a fat loss or body recomposition cycle, I want the 7-Day Pace and 14-Day Pace indicators to be calculated from 7-day rolling window averages (including pre-cycle weight entries when lookback windows extend before the cycle start date) rather than single-day raw weigh-ins, so that daily water weight fluctuations or an isolated low reading 1–2 weeks ago do not distort my weekly rate of loss.

**Why this priority**: The 7-Day and 14-Day Pace rates are the core velocity indicators on the Weight Progression card and directly drive user confidence and goal projections. Currently, comparing raw single-day weigh-ins causes contradictory figures on the same card (e.g., showing a `-0.3 kg` 7-day average drop in the top row while reporting `-0.1 kg/wk` in the pace cards).

**Independent Test**: Can be fully tested by logging 3 weeks of daily weigh-ins (including a single-day outlier 14 days ago and entries straddling the cycle start date) and verifying that the 7-Day Pace matches the difference between Today's 7D Average and Last Week's 7D Average, and the 14-Day Pace matches half the difference between Today's 7D Average and the 7D Average from two weeks ago.

**Acceptance Scenarios**:

1. **Given** an athlete has logged weight entries across three weeks where the 7-day average for `[t-20, t-14]` is `76.5 kg`, `[t-13, t-7]` is `75.9 kg`, and `[t-6, t]` is `75.7 kg`, **When** the athlete views the Weight Progression card on date `t`, **Then** the 7-Day Pace displays `-0.2 kg/wk` (`75.7 - 75.9`) and the 14-Day Pace displays `-0.4 kg/wk` (`(75.7 - 76.5) / 2.0`), unaffected by any single-day low outlier on `t-14`.
2. **Given** a cycle started 15 days ago with `77.8 kg` starting weight and the athlete has logged pre-cycle weight entries during the week prior to the cycle start, **When** the 14-Day Pace and 14D AVG pillar are evaluated today, **Then** the lookback windows (`[t-20, t-14]` and `[t-20, t-7]`) include the pre-cycle entries so that valid 14-day smoothed pace and deltas are displayed immediately without waiting 21+ in-cycle days.
3. **Given** both 7-Day Pace and 14-Day Pace are computed using smoothed 7-day averages, **When** the pace comparison subtitles ("vs last <Day>" or "Pace unchanged") are displayed, **Then** they compare the current smoothed pace at `t` against last week's smoothed pace at `t-7` using the same rolling-average formula.

---

### User Story 2 - Accurate Goal Forecast Driven by Tiered Smoothed Pace (Priority: P1)

As an athlete working toward a target body weight, I want the "Estimated: X.X weeks" and projected arrival date forecast to divide my remaining weight to goal by my smoothed deficit pace (falling back from 14-Day Smoothed Pace to 7-Day Smoothed Pace, and then to Overall Cycle Pace), so that my projected arrival time reflects my true trend rather than projecting an unrealistic 36.6 weeks due to a single-day anchor spike.

**Why this priority**: An inaccurate 36.6-week projection when the athlete is only 2.7–3.7 kg away from a 72.0 kg goal and actively losing weight each Thursday undermines trust in the coaching feedback loop.

**Independent Test**: Can be tested by setting a cycle goal weight of `72.0 kg`, a current 7D average of `75.7 kg` (`3.7 kg` remaining), and verifying that the forecast divides `3.7 kg` by the active smoothed deficit velocity (e.g., `0.4 kg/wk` -> `9.3 weeks`), and falls back cleanly when a tier is unavailable or stalled.

**Acceptance Scenarios**:

1. **Given** an athlete has a target weight of `72.0 kg`, a current 7D average weight of `75.7 kg` (`3.7 kg` remaining), and an active 14-Day Smoothed Pace of `-0.4 kg/wk`, **When** the Goal Pace Forecast is evaluated, **Then** the banner displays `Estimated: 9.3 weeks` (`3.7 / 0.4 = 9.25`) with the corresponding projected arrival date.
2. **Given** an athlete's 14-Day Smoothed Pace is unavailable or stalled (`>= 0.0 kg/wk`) while their 7-Day Smoothed Pace is in an active deficit (`-0.3 kg/wk`), **When** the Goal Pace Forecast is evaluated, **Then** the forecast falls back to the 7-Day Smoothed Pace and projects `12.3 weeks` (`3.7 / 0.3`).
3. **Given** both the 14-Day and 7-Day Smoothed Paces are unavailable or stalled (`>= 0.0 kg/wk`), but the athlete's current 7D average (`75.7 kg`) is lower than their cycle Starting Weight (`77.8 kg`) over at least 7 elapsed days, **When** the Goal Pace Forecast is evaluated, **Then** the forecast falls back to the overall cycle pace (`(Start Weight - Current 7D Avg) / elapsedWeeks`) to project remaining weeks.

---

### User Story 3 - Cohesive Top Progress Track & Remaining Goal Distance (Priority: P2)

As an athlete viewing the Weight Progression card, I want the top reference track (`% to goal` and `-X.X kg left` under `GOAL`) to be calculated from the same Current 7D Average weight displayed in the center `TODAY` hero pillar, so that `START`, `TODAY`, `GOAL (-X.X kg left)`, `% to goal`, and `Estimated weeks` all reconcile mathematically on screen.

**Why this priority**: Previously, the top track used the single-day raw weight (e.g., `74.7 kg` -> `-2.7 kg left` and `54.3% to goal`) while the center hero right below it displayed `TODAY 75.7 kg` (the 7D average, which is `3.7 kg` above `72.0 kg`), creating a visible 1.0 kg arithmetic discrepancy.

**Independent Test**: Can be tested by rendering the Weight Progression card when today's raw weight differs from today's 7D average weight and verifying that the remaining weight to goal and completion percentage match the displayed `TODAY` 7D average weight.

**Acceptance Scenarios**:

1. **Given** `START` is `77.8 kg`, `GOAL` is `72.0 kg`, today's single-day raw weigh-in is `74.7 kg`, and the displayed `TODAY` 7D average is `75.7 kg`, **When** the Weight Progression card is displayed, **Then** the goal subtitle shows `(-3.7 kg left)` (`75.7 - 72.0`) and the progress bar shows `36.2% to goal` (`(77.8 - 75.7) / (77.8 - 72.0)`).

---

### Edge Cases

- **No Pre-Cycle Entries During Early Cycle Days**: If an athlete starts a new cycle with no prior weight logs before the cycle start date, lookback windows `[t-13, t-7]` or `[t-20, t-14]` that have no logged entries fall back to the cycle's `startingWeightKg` if `cycleStartDate` falls on or before the lookback anchor date; if the cycle started less than 7 days ago and no pre-cycle history exists, pace and forecast report insufficient data (`NotEnoughData`).
- **Sparse Logging / Missing Days in a 7-Day Window**: If an athlete logs only 1–6 times within a 7-day window `[d-6, d]`, the 7-day window average is the arithmetic mean of the available non-deleted entries in that 7-day interval.
- **All Pace Tiers Stalled or Gaining**: If the 14-Day Smoothed Pace, 7-Day Smoothed Pace, and Overall Cycle Pace are all non-negative (weight maintenance or gain, deficit velocity `<= 0.0` after rounding to 0.1), the forecast banner displays the stalled state (`Pace stalled — insufficient deficit to project`).
- **Goal Reached or Cycle Ended**: Precedence rules remain strictly ordered: `GoalReached` (when Current 7D Average `<=` Goal Weight) takes highest priority, followed by `CycleEnded` (when today is past the cycle end date), `NotEnoughData`, `Stalled`, and `Projected` (capped at 52.0 weeks / `> 1 year`).
- **Unit Localization (`kg` vs `lbs`)**: All underlying window averages and velocities are computed in canonical kilograms and converted to pounds (`lbs`) for display when imperial units are selected, preserving identical weeks-to-goal and percentage outcomes across unit toggles.

## Requirements *(mandatory)*

### Functional Requirements

- **FR-001**: System MUST compute the 7-day rolling window average weight $\overline{W}_{7\text{d}}(d)$ for any anchor date $d$ as the arithmetic mean of all non-deleted weight entries within the closed 7-day calendar window $[d - 6\text{ days}, d]$ (up to the evaluation reference date), including weight entries logged prior to the cycle start date.
- **FR-002**: When no logged weight entries exist within a historical lookback window $[d - 6\text{ days}, d]$, the system MUST fall back to the cycle's configured `startingWeightKg` if the cycle start date is on or before $d$; otherwise the window average evaluates to unavailable (`null`).
- **FR-003**: System MUST include non-deleted pre-cycle weight entries in the lookback windows of the 3-pillar hero metrics (`LAST WEEK` 7D average $[t-13, t-7]$, `14D AVG` $[t-13, t]$, and lagged 14D average $[t-20, t-7]$) whenever at least one active in-cycle weigh-in exists.
- **FR-004**: System MUST compute the 7-Day Pace rate ($P_{7\text{d}}(t)$) in `kg/wk` as the difference between Today's 7D Average and Last Week's 7D Average: $\overline{W}_{7\text{d}}(t) - \overline{W}_{7\text{d}}(t - 7\text{d})$, requiring at least 7 calendar days of span across available history/cycle start.
- **FR-005**: System MUST compute the 14-Day Pace rate ($P_{14\text{d}}(t)$) in `kg/wk` as half the difference between Today's 7D Average and the 7D Average from two weeks ago: $(\overline{W}_{7\text{d}}(t) - \overline{W}_{7\text{d}}(t - 14\text{d})) / 2.0$, requiring at least 14 calendar days of span across available history/cycle start.
- **FR-006**: System MUST compute the 7-Day and 14-Day Pace comparison subtitles by comparing the current smoothed pace at $t$ against the prior week's smoothed pace at $t - 7\text{d}$, applying the `0.05 kg/wk` noise deadband (`"Pace unchanged"`) and loss-to-loss acceleration/deceleration percentage formatting.
- **FR-007**: System MUST compute the Goal Pace Forecast remaining weight as $\overline{W}_{7\text{d}}(t) - W_{\text{target}}$ and resolve active deficit velocity using a 3-tier priority hierarchy:
  1. **Tier 1 (Primary)**: 14-Day Smoothed Pace deficit velocity ($-P_{14\text{d}}(t)$) if available and $> 0.0\text{ kg/wk}$ (rounded to 0.1).
  2. **Tier 2 (Fallback 1)**: 7-Day Smoothed Pace deficit velocity ($-P_{7\text{d}}(t)$) if Tier 1 is unavailable or $\le 0.0\text{ kg/wk}$ and Tier 2 is $> 0.0\text{ kg/wk}$ (rounded to 0.1).
  3. **Tier 3 (Fallback 2)**: Overall Cycle Pace deficit velocity from cycle start weight to $\overline{W}_{7\text{d}}(t)$ over elapsed cycle days (minimum 7 days) if Tiers 1 and 2 are unavailable or $\le 0.0\text{ kg/wk}$.
- **FR-008**: System MUST calculate the Weight Progression card's top reference track completion percentage (`% to goal`) and remaining distance (`-X.X kg left` or `Goal reached`) using the displayed `TODAY` 7D average weight ($\overline{W}_{7\text{d}}(t)$) so that all summary figures on the card are mutually consistent.

### Key Entities

- **Weight Entry**: An athlete's daily body weight record associated with a unique calendar date, weight value in kilograms, optional notes, and soft-delete status.
- **Training Cycle**: A bounded or ongoing training period with a start date, optional end date, cycle type (e.g., Fat Loss & Bodybuilding), optional starting baseline weight, and optional target goal weight.
- **Rolling Window Weight Summary**: Aggregated 7-day and 14-day moving average weights and their week-over-week deltas anchored to the latest weigh-in date.
- **Pace Rate & Forecast**: Weekly rate of body weight change (`kg/wk` or `lbs/wk`), trend direction (`LOSS`, `GAIN`, `NEUTRAL`), week-over-week pace comparison, and projected weeks/date to reach the cycle goal weight.

## Success Criteria *(mandatory)*

### Measurable Outcomes

- **SC-001**: 100% mathematical consistency across the Weight Progression card: the 7-Day Pace value matches the weekly delta shown under `LAST WEEK`, and `START` weight, `TODAY` 7D average weight, `GOAL (-X.X kg left)`, and `% to goal` reconcile with zero discrepancy.
- **SC-002**: A single-day weigh-in outlier 7 or 14 days prior alters the 7-Day or 14-Day Pace by at most $1/N$ of its magnitude (where $N \le 7$ is the number of logs in that week's window), eliminating artificial `> 30 week` forecast spikes when the multi-week rolling average is steadily decreasing.
- **SC-003**: Athletes starting a new cycle with existing historical weight logs receive accurate 7-Day and 14-Day Pace metrics and goal projections immediately from week 1 of the cycle without waiting 14–21 days for lookback windows to fill.
- **SC-004**: All automated unit tests and UI verification checks pass with 100% green status across both metric (`kg`) and imperial (`lbs`) display modes.

## Assumptions

- At least one non-deleted weight entry must be logged on or after the cycle start date for the Weight Progression hero card to exit its empty state.
- Pre-cycle weight entries are used strictly as historical lookback context for rolling averages and pace windows; the cycle's `START` anchor continues to represent the cycle's configured `startingWeightKg` (or first in-cycle weigh-in).
- The secondary "Same-Day Raw Weight Trio" row (`LAST WEEK`, `CURRENT`, `2 WKS AGO`) introduced for direct single-day weekday comparison remains raw by design, while the primary 3-pillar hero row, Pace cards, Top Reference Track, and Forecast Banner operate on smoothed rolling averages.
