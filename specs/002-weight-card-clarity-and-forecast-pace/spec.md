# Feature Specification: Weight Progression Table Clarity & Forecast Pace Attribution

**Feature Branch**: `[002-weight-card-clarity-and-forecast-pace]`

**Created**: 2026-10-22

**Status**: Ready

**Input**: User description: "c:\Users\rogal\workspaces\ws-gym\crosstrainingapp\docs\draft-requisites\implementation-plan.md"

## User Scenarios & Testing *(mandatory)*

### User Story 1 - Clear Separation Between Rolling Averages and Daily Weigh-In Tables (Priority: P1)

As an athlete viewing the Weight Progression card on the Progress & Goals screen, I want explicit section titles and unambiguous, non-truncated column headers for the Rolling Averages table and the Daily Weigh-In table, so that I immediately know which table shows my smoothed 7-day and 14-day rolling averages and which table compares my single-day weigh-ins on the same day of the week.

**Why this priority**: Currently, two 3-column weight tables are stacked without section titles and use nearly identical labels (`LAST WEEK (...) | TODAY | 14D AVG` above `LAST WEEK | CURRENT | 2 WKS AGO`). Worse, the top table labels the 7-day rolling average as `TODAY` and truncates the prior week's 7-day average header into `LAST WEEK (...`, while the bottom table labels today's actual single-day weigh-in as `CURRENT`. Athletes cannot distinguish which row is their daily weight and which is their rolling average.

**Independent Test**: View the Weight Progression card on a standard or compact phone screen (including large accessibility text sizes) during an active Fat Loss / Recomposition cycle on a Thursday and verify that both section headers (`ROLLING AVERAGES` and `DAILY WEIGH-IN (THU)`) and all six column headers (`LAST WK 7D`, `7D AVG`, `14D AVG` and `LAST WEEK`, `TODAY`, `2 WKS AGO`) are clearly displayed on a single line without ellipsis truncation, separated by a visual divider.

**Acceptance Scenarios**:

1. **Given** an active Fat Loss or Recomposition cycle with logged weight entries evaluated on a Thursday, **When** the athlete views the Weight Progression card, **Then**:
   - The first 3-column table displays the section header `ROLLING AVERAGES` above columns `LAST WK 7D` (left), `7D AVG` (center), and `14D AVG` (right).
   - A visual divider separates the first and second 3-column tables.
   - The second 3-column table displays the section header `DAILY WEIGH-IN (THU)` above columns `LAST WEEK` (left), `TODAY` (center), and `2 WKS AGO` (right).
2. **Given** a compact mobile screen (`360dp` width) with enlarged accessibility text (`130%` font scale), **When** both 3-column weight tables are displayed, **Then** every section header and column header fits on a single line with zero text truncation (`...`).
3. **Given** the current 7-day rolling average displays as `75.7 kg` and last week's 7-day rolling average displays as `75.9 kg`, **When** the weekly change subtitle under `LAST WK 7D` is shown, **Then** it displays `-0.2 kg` (`75.7 - 75.9`), matching the displayed 1-decimal values on screen.

---

### User Story 2 - Forecast Banner Pace Attribution & Display-Consistent Estimation Math (Priority: P1)

As an athlete viewing the goal estimation banner (`Estimated: X.X weeks`) at the bottom of the Weight Progression card, I want to see which pace (`14-day pace` or `7-day pace`) and weekly rate (`-X.X kg/wk` or `-X.X lbs/wk`) are used for the forecast, and I want the estimated weeks to be calculated from the displayed 1-decimal remaining weight and displayed 1-decimal pace, so that I understand how the forecast is derived and the numbers on screen reconcile when checked by hand.

**Why this priority**: When the card shows `GOAL 72.0 kg (-3.7 kg left)`, `7-Day Pace: ▼ -0.3 kg/wk`, `14-Day Pace: ▼ -0.2 kg/wk`, and `Estimated: 19.7 weeks`, the athlete cannot tell whether the 7-day or 14-day pace was used, and dividing `3.7 kg` by `0.2 kg/wk` gives `18.5 weeks` rather than `19.7 weeks` (which was caused by dividing by an unrounded internal rate of `-0.188 kg/wk`).

**Independent Test**: View the Weight Progression card when `75.7 kg` 7D average and `72.0 kg` goal (`3.7 kg` remaining) are paired with a displayed 14-Day Pace of `-0.2 kg/wk` (unrounded `-0.188 kg/wk`), and verify that the forecast banner displays `Estimated: 18.5 weeks`, the corresponding projected arrival date, and the subtitle `Based on 14-day pace (-0.2 kg/wk)`.

**Acceptance Scenarios**:

1. **Given** an athlete has `3.7 kg` remaining to goal (`75.7 kg` 7D average vs `72.0 kg` goal) and an active 14-Day Pace that displays as `-0.2 kg/wk`, **When** the Goal Estimation banner is displayed, **Then**:
   - The primary line displays `Estimated: 18.5 weeks` (`3.7 / 0.2 = 18.5`).
   - The arrival line displays `Projected arrival: <Date>` (`130` days from today, i.e., `round(18.5 * 7)`).
   - The attribution subtitle displays `Based on 14-day pace (-0.2 kg/wk)`.
2. **Given** the 14-Day Pace is not yet available (fewer than 14 days of span) and the 7-Day Pace is active at `-0.3 kg/wk` with `3.7 kg` remaining, **When** the Goal Estimation banner is displayed, **Then** it displays `Estimated: 12.3 weeks` (`3.7 / 0.3 = 12.33...`) and the attribution subtitle `Based on 7-day pace (-0.3 kg/wk)`.
3. **Given** the active pace tier (14-Day Pace if available, else 7-Day Pace) is stalled or gaining (`0.0 kg/wk` or `+0.2 kg/wk`), **When** the Goal Estimation banner displays the stalled state, **Then** it shows `Pace stalled — insufficient deficit to project` along with the attribution subtitle `Based on 14-day pace (0.0 kg/wk)` (or `(+0.2 kg/wk)`) so the athlete knows which pace tier triggered the stalled warning.
4. **Given** the athlete switches the display unit to pounds (`lbs`), **When** the Goal Estimation banner is displayed, **Then** the pace attribution subtitle converts and formats the weekly rate in `lbs/wk` (e.g., `Based on 14-day pace (-0.4 lbs/wk)`).

---

### User Story 3 - Top Progress Track 1-Decimal Reconciliation (Priority: P2)

As an athlete viewing the top progress bar (`% to goal`) and goal remaining distance (`-X.X kg left`) on the Weight Progression card, I want those figures calculated from the 1-decimal rounded start, 7D average, and goal weights shown on screen, so that `(START - 7D AVG) / (START - GOAL)` matches the displayed percentage and remaining distance with zero rounding discrepancy.

**Why this priority**: In the screenshot, `START 77.8 kg`, `7D AVG 75.7 kg`, and `GOAL 72.0 kg (-3.7 kg left)` are displayed alongside `36.9% to goal`, whereas `(77.8 - 75.7) / (77.8 - 72.0) = 2.1 / 5.8 = 36.2%`. Using the 1-decimal rounded `75.7 kg` value makes the top progress bar reconcile with the displayed pillar value.

**Independent Test**: Render the Weight Progression card with `START = 77.8 kg`, `GOAL = 72.0 kg`, and an unrounded 7D average of `75.66 kg` (displayed as `75.7 kg`), and verify that the top progress track displays `36.2% to goal` and `(-3.7 kg left)`.

**Acceptance Scenarios**:

1. **Given** `START` is `77.8 kg`, `GOAL` is `72.0 kg`, and the 7-day rolling average is `75.66 kg` (displayed as `75.7 kg`), **When** the top progress track is displayed, **Then** it shows `36.2% to goal` and `(-3.7 kg left)`.

---

### Edge Cases

- **Long-Horizon Projections (`> 52.0 weeks`)**: When the estimated duration exceeds 52.0 weeks, the banner displays `Estimated: > 1 year`, omits the specific arrival date line, and still displays the pace attribution subtitle (e.g., `Based on 14-day pace (-0.1 kg/wk)`).
- **Unlogged Today in Daily Weigh-In Table**: When no weigh-in has been logged for today (`referenceDate`), the `TODAY` center column under `DAILY WEIGH-IN (<DAY>)` displays `—` (announced as `"No data"` by screen readers) and both same-day deltas display `—`, while `7D AVG` under `ROLLING AVERAGES` continues to display the 7-day rolling average as of the latest weigh-in.
- **Goal Reached, Cycle Ended, or Insufficient History**: When the forecast banner is in the `GoalReached`, `CycleEnded`, or `NotEnoughData` state, no `"Based on ..."` pace attribution subtitle is displayed.
- **Short Early-Cycle Window in Rolling Averages**: When fewer than 14 days have elapsed in a cycle without pre-cycle history and a shorter progressive window is passed to the right pillar, the right column header under `ROLLING AVERAGES` reflects the window span (`<N>D AVG`, defaulting to `14D AVG`).

## Requirements *(mandatory)*

### Functional Requirements

- **FR-001**: System MUST display a section header `"ROLLING AVERAGES"` above the 3-pillar rolling average row and a section header `"DAILY WEIGH-IN (<DAY>)"` (where `<DAY>` is the 3-letter uppercase English weekday of the evaluation date, e.g., `THU`) above the 3-column daily weigh-in comparison row, separated by a horizontal visual divider.
- **FR-002**: System MUST label the three columns of the `ROLLING AVERAGES` table as `"LAST WK 7D"` (left), `"7D AVG"` (center), and `"14D AVG"` (right, or `"<N>D AVG"` when a shorter progressive window is explicitly provided).
- **FR-003**: System MUST label the three columns of the `DAILY WEIGH-IN (<DAY>)` table as `"LAST WEEK"` (left), `"TODAY"` (center), and `"2 WKS AGO"` (right).
- **FR-004**: System MUST compute the weekly deltas in the `ROLLING AVERAGES` table (`LAST WK 7D` delta and `14D AVG` delta) as the difference between the 1-decimal rounded current window average and the 1-decimal rounded prior window average, and MUST compute the top progress track (`% to goal` and `-X.X <unit> left`) using the 1-decimal rounded 7-day average weight.
- **FR-005**: System MUST calculate the goal forecast duration (`Estimated: X.X weeks`) by dividing the 1-decimal rounded remaining weight to goal by the 1-decimal rounded active deficit velocity (`14-Day Pace` if available, else `7-Day Pace`).
- **FR-006**: System MUST display a dedicated pace attribution subtitle `"Based on <14|7>-day pace (<formatted rate>)"` inside the Goal Estimation banner for both active projections (`Projected`) and plateau/gain states (`Stalled`), formatted in the user's selected weight unit (`kg/wk` or `lbs/wk`).

### Key Entities

- **Rolling Averages Summary**: The 3-column summary table (`LAST WK 7D`, `7D AVG`, `14D AVG`) presenting smoothed multi-day window averages and their week-over-week 1-decimal deltas.
- **Daily Weigh-In Comparison**: The 3-column summary table (`LAST WEEK`, `TODAY`, `2 WKS AGO`) presenting single-day weigh-ins recorded on the exact same weekday across two weeks.
- **Goal Pace Forecast**: The goal arrival estimation state containing the projected weeks to goal, projected arrival calendar date, active pace tier (`14-day` or `7-day`), and active weekly rate of change.

## Success Criteria *(mandatory)*

### Measurable Outcomes

- **SC-001**: 100% of section headers (`ROLLING AVERAGES`, `DAILY WEIGH-IN (<DAY>)`) and column headers (`LAST WK 7D`, `7D AVG`, `14D AVG`, `LAST WEEK`, `TODAY`, `2 WKS AGO`) render on a single line with zero text truncation on `360dp`-wide screens up to `130%` font scale.
- **SC-002**: 100% arithmetic consistency across all visible numbers on the Weight Progression card when checked by hand:
  - `7D AVG − LAST WK 7D` equals the delta displayed under `LAST WK 7D`.
  - `(START − 7D AVG) / (START − GOAL)` equals the displayed `% to goal`, and `7D AVG − GOAL` equals the displayed remaining weight under `GOAL`.
  - `Remaining weight / Displayed forecast pace rate` equals the displayed `Estimated: X.X weeks`.
- **SC-003**: Users viewing either an active or stalled forecast banner can identify the exact pace tier (`14-day pace` or `7-day pace`) and rate (`kg/wk` or `lbs/wk`) driving the forecast without leaving the card.

## Assumptions

- The evaluation reference date defaults to the current calendar day (`today`).
- All underlying weight records are stored in canonical kilograms (`kg`) and converted to pounds (`lbs`) when imperial display mode is active.
- Existing precedence rules for the forecast banner (`GoalReached > CycleEnded > NotEnoughData > Stalled > Projected`) and the 52-week (`> 1 year`) display cap remain unchanged.
