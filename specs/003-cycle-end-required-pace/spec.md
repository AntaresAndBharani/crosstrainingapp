# Feature Specification: Cycle End-Date Required Pace in Goal Estimation Banner

**Feature Branch**: `[003-cycle-end-required-pace]`

**Created**: 2026-10-22

**Status**: Ready

**Input**: User description: "If in the recomposition cycle we defined an end date, then in the estimated part we must see the pace we have to have."

## User Scenarios & Testing *(mandatory)*

### User Story 1 - Required Weekly Pace to Reach Target Weight by Cycle End Date (Priority: P1)

As an athlete in a Fat Loss & Bodybuilding (Recomposition) cycle who has configured both a target body weight and a cycle end date, I want the Goal Estimation banner at the bottom of the Weight Progression card to display the weekly weight-loss pace required to reach my target weight by the cycle end date, alongside the existing pace projection, so that I can compare my current weekly loss pace directly against the pace required to finish on schedule.

**Why this priority**: Currently, the Goal Estimation banner shows how many weeks it will take to reach the target weight at the current 14-day or 7-day pace (`Estimated: 18.5 weeks`, `Projected arrival: Mon, 1 Mar 2027`, `Based on 14-day pace (-0.2 kg/wk)`), but when a cycle has a scheduled end date, the athlete has no direct visibility into what weekly pace (`-X.X kg/wk` or `-X.X lbs/wk`) is needed to hit the goal weight before the cycle ends.

**Independent Test**: View the Weight Progression card during an active Recomposition cycle with a defined target weight (`72.0 kg`), a scheduled future end date (`30 Nov 2026`, `42` days / `6.0` weeks away), and a 7-day rolling average weight of `75.7 kg` (`3.7 kg` remaining), and verify that the Goal Estimation banner appends `Required pace: -0.6 kg/wk (by 30 Nov 2026)` below the existing forecast lines.

**Acceptance Scenarios**:

1. **Given** an active Fat Loss & Bodybuilding cycle with a target weight (`72.0 kg`), a future cycle end date (`30 Nov 2026`, `39` days / `5.571...` weeks remaining from `22 Oct 2026`), and a current 7-day rolling average of `75.7 kg` (`3.7 kg` remaining to lose), **When** the Goal Estimation banner is displayed in the `Projected` state, **Then**:
   - All existing projection lines (`Estimated: X.X weeks`, `Projected arrival: ...`, `Based on <14|7>-day pace (...)`) remain unchanged.
   - An additional subtitle line is displayed at the bottom of the banner reading `Required pace: -0.7 kg/wk (by 30 Nov 2026)` (`-3.7 / (39 / 7.0) = -0.664 -> -0.7 kg/wk`).
2. **Given** an athlete has switched their preferred weight unit to pounds (`lbs`), **When** the Goal Estimation banner displays the required pace line, **Then** the required weekly pace is calculated from the displayed 1-decimal remaining pounds to goal and formatted in `lbs/wk` (e.g., `Required pace: -1.5 lbs/wk (by 30 Nov 2026)`).

---

### User Story 2 - Required Pace Visibility During Stalled or Early-Cycle Check-In States (Priority: P2)

As an athlete whose current weight-loss pace is stalled (`Stalled`) or who is in the first week of a cycle before 7 days of history have accumulated (`NotEnoughData`), I still want to see the required weekly pace to reach my target weight by the cycle end date as soon as at least one in-cycle weigh-in exists, so that I know the weekly target rate I need to establish or recover.

**Why this priority**: When an athlete's current pace is stalled (`0.0 kg/wk` or gaining) or when they are in Days 1–6 of a new cycle, the banner cannot project an arrival date from current velocity, making the deadline-based required pace even more actionable.

**Independent Test**: View the Weight Progression card when the forecast state is `Stalled` or `NotEnoughData` (with at least 1 in-cycle weigh-in logged, a valid target weight not yet reached, and a future cycle end date), and verify that the `Required pace: -X.X <unit>/wk (by <d MMM yyyy>)` line is displayed inside the banner.

**Acceptance Scenarios**:

1. **Given** an active Fat Loss & Bodybuilding cycle with a future end date (`30 Nov 2026`), `3.0 kg` remaining to target, and a stalled current pace (`Pace stalled — insufficient deficit to project`), **When** the Goal Estimation banner renders, **Then** it displays the stalled headline, the `"Based on <14|7>-day pace (...)"` attribution line, and the `"Required pace: -X.X kg/wk (by 30 Nov 2026)"` line.
2. **Given** an active Fat Loss & Bodybuilding cycle in its first 3 days (`Logging check-ins for 7 days enables pace projections`) with at least 1 in-cycle weigh-in, `3.0 kg` remaining to target, and a future end date (`30 Nov 2026`), **When** the Goal Estimation banner renders, **Then** it displays the not-enough-data headline together with the `"Required pace: -X.X kg/wk (by 30 Nov 2026)"` line.

---

### Edge Cases

- **Cycle Without an End Date (`endDate == null`)**: When the Recomposition cycle is open-ended (`endDate` is not set), no `"Required pace: ..."` line is displayed, and the Goal Estimation banner behaves identically to its current design.
- **On or After Cycle End Date (`daysRemaining <= 0`)**: When the evaluation date (`today`) is on the cycle end date (`daysRemaining == 0`) or after the cycle end date (`CycleEnded`), the `"Required pace: ..."` line is suppressed to prevent division by zero or meaningless post-deadline rates.
- **Goal Already Reached (`GoalReached` or `remainingDisplay <= 0.0`)**: When the athlete's 1-decimal rounded 7-day rolling average has reached or dropped below the 1-decimal rounded target weight, the banner displays `Goal reached! 🎉` and suppresses the `"Required pace: ..."` line.
- **Small Remaining Weight Over a Long Horizon (`0 < requiredLoss < 0.05` per week)**: When the remaining weight is positive (`> 0.0`) but dividing by remaining weeks yields a weekly loss rate smaller than `0.05` in the display unit (which standard rounding would round to `0.0`), the required pace is clamped to `-0.1 kg/wk` (or `-0.1 lbs/wk`) so the banner never instructs an athlete who still has weight to lose that their required pace is `0.0`.
- **Invalid or Missing Target Weight**: When `targetWeightKg` is unset (`null`), non-positive (`<= 0.0`), or greater than or equal to the cycle's starting weight (`targetWeightKg >= effectiveStartWeight`), no required pace is calculated or displayed.

## Requirements *(mandatory)*

### Functional Requirements

- **FR-001**: When a Fat Loss & Bodybuilding cycle defines a future `endDate` (`ChronoUnit.DAYS.between(today, endDate) > 0`) and a valid `targetWeightKg` (`0.0 < targetWeightKg < effectiveStartWeight`), the system MUST calculate the required weekly pace to reach the target weight by `endDate`.
- **FR-002**: The system MUST compute the required weekly pace using:
  - **Numerator (Remaining Weight)**: The 1-decimal rounded 7-day rolling average weight minus the 1-decimal rounded target weight in the active display unit (`remainingDisplay`), matching the `(-X.X <unit> left)` badge in the top progress track.
  - **Denominator (Remaining Weeks)**: Exact remaining calendar weeks calculated as `daysRemaining / 7.0`, where `daysRemaining` is the number of calendar days from the evaluation date (`today`) to `endDate`.
  - **Signed Weekly Rate**: `requiredRate = -(remainingDisplay / weeksRemaining)`, rounded to 1 decimal place and clamped to at most `-0.1` (`-0.1 kg/wk` or `-0.1 lbs/wk`) whenever `remainingDisplay > 0.0`.
- **FR-003**: The system MUST append the required pace as a subtitle line at the bottom of the Goal Estimation banner formatted as `"Required pace: -X.X <kg/wk|lbs/wk> (by <d MMM yyyy>)"` (using English `d MMM yyyy` date formatting, e.g., `"Required pace: -0.6 kg/wk (by 30 Nov 2026)"`), while preserving all existing banner lines (`Estimated: X.X weeks`, `Projected arrival: ...`, `Based on <14|7>-day pace (...)`).
- **FR-004**: The system MUST display the required pace subtitle line when the Goal Estimation banner is in the `Projected`, `Stalled`, or `NotEnoughData` state (provided at least one active in-cycle weigh-in exists and `remainingDisplay > 0.0`).
- **FR-005**: The system MUST suppress the required pace subtitle line when `endDate` is unset, when `today >= endDate` (`daysRemaining <= 0`), when the goal weight has been reached (`GoalReached` or `remainingDisplay <= 0.0`), when no active in-cycle weigh-ins exist, or when `targetWeightKg` is unset or invalid.

### Key Entities

- **Required Cycle-End Pace**: The deadline-driven weekly weight-loss rate (`kg/wk` or `lbs/wk`) and formatted target deadline (`d MMM yyyy`) derived from the athlete's remaining weight to goal (`7D AVG − GOAL`) divided by the exact calendar weeks remaining until the cycle's scheduled end date.
- **Goal Estimation Banner**: The summary status container at the bottom of the Weight Progression card that presents the athlete's projected time to goal, projected arrival date, current pace tier attribution, and (when a cycle end date is defined) the required pace to finish by the cycle end date.

## Success Criteria *(mandatory)*

### Measurable Outcomes

- **SC-001**: 100% arithmetic consistency between the top progress track's remaining weight badge (`-X.X <unit> left`), the calendar days remaining until `cycle.endDate` (`daysRemaining / 7.0` weeks), and the displayed `"Required pace: -Y.Y <unit>/wk (by <Date>)"` line in both `kg` and `lbs`.
- **SC-002**: 100% of active Recomposition cycles with a future `endDate`, unreached valid `targetWeightKg`, and at least 1 in-cycle weigh-in display the required pace line across `Projected`, `Stalled`, and `NotEnoughData` banner states, while 0% of open-ended cycles (`endDate == null`), completed goals (`GoalReached`), or ended cycles (`today >= endDate`) display it.
- **SC-003**: The Goal Estimation banner with all four lines (`Estimated`, `Projected arrival`, `Based on ...`, and `Required pace: ...`) renders cleanly with zero text clipping or layout overflow on `360dp`-wide screens up to `130%` accessibility font scale.

## Assumptions

- The evaluation reference date defaults to the current calendar day (`today`).
- Remaining weight to goal uses the exact same 7-day rolling average baseline (`7D AVG`) as the top progress track and the existing `Estimated: X.X weeks` calculation so all numbers on the Weight Progression card reconcile.
- The cycle end date is formatted as `d MMM yyyy` in `Locale.US` (e.g., `30 Nov 2026`), matching the short date conventions used across the Weight Progression card.
