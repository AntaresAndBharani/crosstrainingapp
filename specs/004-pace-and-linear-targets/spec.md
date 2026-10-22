# Feature Specification: Required-Pace & Linear Cycle Weight Targets for 7D Average and Daily Weight

**Feature Branch**: `[feat/004-pace-and-linear-targets]`

**Created**: 2026-10-22

**Status**: Ready

**Input**: User description: "Based on the pace we have to have to the weight wanted by the end of the cycle, I want to estimate my current weight for today and my weight for tomorrow (based on my real weight of today). This is valid for averages and daily weight. Imagine, if the pace is 0.7 kilos per week, that means I have to weigh 0.7 kilos less than the same day 7 days ago (and 0.1 kilos less tomorrow than today, similar for the averages). This will give us where I would be. I want to see as well my theoretical weight if my weight loss were linear (from the beginning of the cycle to the end). I want also compare this with the key measures we have."

## User Scenarios & Testing *(mandatory)*

### User Story 1 - Required-Pace Expected Today & Target Tomorrow for 7D Average and Daily Weight (Priority: P1)

As an athlete tracking a Fat Loss & Bodybuilding cycle with a defined target weight and cycle end date, I want to see my 2-decimal **Expected Today** weight (anchored to 7 days ago plus one week of exact Required Pace) and **Target Tomorrow** weight (anchored to today's actual weight plus one day of exact Required Pace) for both my **7-Day Rolling Average** and my **Daily Weight**, so that I know the exact target scale reading and rolling average needed today and tomorrow to finish my cycle on schedule.

**Why this priority**: Previously, the `7-DAY PACE TARGETS` panel extrapolated past weight changes from `t - 7d` and `t - 6d`, which produced misleading targets (such as a `76 kg` target tomorrow when `t - 6d` was a high fluctuation day, or locking into a `Stalled` state after a maintenance week) and only evaluated single-day weights. Anchoring Expected Today (`W_{t-7d} + RequiredPace/wk`) and Target Tomorrow (`W_today + RequiredPace/wk ÷ 7`) to the cycle's Required Pace across both 7D Average and Daily Weight directly answers what the athlete needs to weigh today and tomorrow.

**Independent Test**: View the Weight Progression card during an active Fat Loss & Bodybuilding cycle with a defined start weight (`78.00 kg`), target weight (`71.00 kg`), start date (`1 Oct 2026`), end date (`10 Dec 2026`), and evaluation date (`22 Oct 2026`, `49` days / `7.0` weeks remaining) where today's 7D Average is `75.90 kg` (`-0.70 kg/wk` required pace, `-0.10 kg/day`), last week's 7D Average is `76.50 kg`, last week's daily weight is `76.20 kg`, and today's daily weight is `75.60 kg`. Verify that the `PACE & LINEAR TARGETS` container displays 2-decimal Expected Today and Target Tomorrow values and subtitles for both the `7D ROLLING AVERAGE` row and the `DAILY WEIGH-IN` row.

**Acceptance Scenarios**:

1. **Given** an active Fat Loss & Bodybuilding cycle with `49` days (`7.0` weeks) remaining to `endDate`, a current 7D Rolling Average of `75.90 kg`, and a target weight of `71.00 kg` (exact required pace = `-0.70 kg/wk` / `-0.10 kg/day`), **When** the `PACE & LINEAR TARGETS` container renders for the `7D ROLLING AVERAGE` row (with last week's 7D average at `76.50 kg`), **Then**:
   - `Expected Today` displays `"75.80 kg"` (`76.50 - 0.70`) with subtitle `"+0.10 kg off pace"` (`75.90 - 75.80`).
   - `Target Tomorrow` displays `"75.80 kg"` (`75.90 - 0.10`) with subtitle `"-0.10 kg/day"`.
2. **Given** the same cycle where last week's raw weigh-in (`t - 7d`) was `76.20 kg` and today's raw weigh-in (`t`) is `75.60 kg`, **When** the `PACE & LINEAR TARGETS` container renders for the `DAILY WEIGH-IN` row, **Then**:
   - `Expected Today` displays `"75.50 kg"` (`76.20 - 0.70`) with subtitle `"+0.10 kg off pace"` (`75.60 - 75.50`).
   - `Target Tomorrow` displays `"75.50 kg"` (`75.60 - 0.10`) with subtitle `"-0.10 kg/day"`.
3. **Given** a cycle where the required weekly pace is `-0.39 kg/wk` (`-0.0552 kg/day`, which is smaller than `0.1 kg/day`), **When** `Expected Today` and `Target Tomorrow` are displayed, **Then** all weights and deltas are formatted to 2 decimal places (`%.2f`, e.g., `"74.41 kg"`, `"74.64 kg"`, `"-0.06 kg/day"`) so sub-`0.1` daily steps are clearly visible and never rounded away to zero.

---

### User Story 2 - Theoretical Linear Cycle Weight & Comparison with Key Measures (Priority: P1)

As an athlete tracking a Fat Loss & Bodybuilding cycle from a starting weight on `startDate` to a target weight on `endDate`, I want to see my **Theoretical Linear Weight** (`Linear Today`) assuming a straight-line weight loss across the entire cycle duration, and compare it against both my **7D Rolling Average** and my **Daily Weight**, so that I can tell whether my current average and daily weight are ahead of or behind the ideal start-to-finish linear trajectory.

**Why this priority**: While `Expected Today` measures short-term adherence over the past 7 days at the current required pace, `Linear Today` provides the cumulative benchmark from Day 1 of the cycle (`W_start + (W_target - W_start) × elapsedDays / totalCycleDays`). Comparing `Linear Today` against both `7D AVG` and `TODAY` daily weight gives the athlete complete visibility into their overall cycle position.

**Independent Test**: On day `21` of a `70`-day cycle (`30%` elapsed) from `78.00 kg` to `71.00 kg`, verify that `Linear Today` displays `"75.90 kg"` in both the `7D ROLLING AVERAGE` row and the `DAILY WEIGH-IN` row, with row-specific 2-decimal delta subtitles comparing `75.90 kg` against today's `7D AVG` (`"On pace"`) and today's raw weigh-in `75.60 kg` (`"-0.30 kg ahead"`).

**Acceptance Scenarios**:

1. **Given** a cycle starting on `1 Oct 2026` at `78.00 kg` and ending on `10 Dec 2026` (`70` total calendar days) with target weight `71.00 kg`, evaluated on `22 Oct 2026` (`21` elapsed calendar days), **When** `Linear Today` is calculated, **Then** the theoretical linear weight is `78.00 + (71.00 - 78.00) × (21 / 70) = 75.90 kg`.
2. **Given** `Linear Today` is `75.90 kg`, today's 7D Rolling Average is `76.15 kg`, and today's Daily Weigh-In is `75.60 kg`, **When** the `PACE & LINEAR TARGETS` container is displayed, **Then**:
   - The `7D ROLLING AVERAGE` row shows `Linear Today` = `"75.90 kg"` with subtitle `"+0.25 kg off pace"`.
   - The `DAILY WEIGH-IN` row shows `Linear Today` = `"75.90 kg"` with subtitle `"-0.30 kg ahead"`.

---

### User Story 3 - Graceful Degradation for Unlogged Days & Cycle Boundary States (Priority: P2)

As an athlete who checks the Progress & Goals screen before logging today's weigh-in, after missing a weigh-in 7 days ago, or after a week where my weight slightly increased, I want the `PACE & LINEAR TARGETS` container to remain available whenever a valid cycle goal and future end date exist, and to degrade gracefully per cell when specific daily logs are missing.

**Why this priority**: Athletes frequently open the Progress & Goals screen in the morning before stepping on the scale, or after a fluctuating week. The container must never lock into a false `Stalled` state while weight remains to be lost before `endDate`, and must still show all computable targets when today's or last week's single-day log is absent.

**Independent Test**: Evaluate the `PACE & LINEAR TARGETS` container when today (`t`) is unlogged (with prior logs present in the last 7 and 14 days), and verify that the `7D ROLLING AVERAGE` row displays all 3 columns with deltas, while the `DAILY WEIGH-IN` row displays 2-decimal target weights for `Linear Today` and `Expected Today` (without delta subtitles) and `"—"` (`"Needs today's log"`) for `Target Tomorrow`.

**Acceptance Scenarios**:

1. **Given** an athlete has logged weigh-ins over the past 14 days including `t - 7d`, but has NOT logged a weigh-in on `today` (`t`), **When** the `PACE & LINEAR TARGETS` container renders, **Then**:
   - The `7D ROLLING AVERAGE` row displays `Linear Today`, `Expected Today`, and `Target Tomorrow` with their 2-decimal weights and subtitles.
   - The `DAILY WEIGH-IN` row displays the 2-decimal target weights for `Linear Today` and `Expected Today` with no delta subtitle, and displays `"—"` with subtitle `"Needs today's log"` under `Target Tomorrow`.
2. **Given** an athlete whose weight increased over the past 7 days (`entryTMinus7 >= startKg` or 7-day pace `>= 0.0`), but whose current 7D Average (`76.50 kg`) is still above `targetWeightKg` (`72.00 kg`) prior to `cycle.endDate`, **When** the container renders, **Then** it does NOT show a `Stalled` blocker; instead it renders `PaceTargetsState.Available` using the required pace to `cycle.endDate`.
3. **Given** a cycle where `targetWeightKg` or `cycle.endDate` is unset (`null`), **When** the container renders, **Then** it displays `"Pace & Linear Targets Unavailable: Set cycle goal weight and end date"`.

---

### Edge Cases

- **Missing Cycle Goal Weight or End Date**: When `targetWeightKg == null`, `targetWeightKg <= 0.0`, `targetWeightKg >= startWeightKg`, or `cycle.endDate == null` (`totalCycleDays <= 0`), the container transitions to `PaceTargetsState.Unavailable` with a clear instructional message.
- **Goal Already Reached (`round1InUnit(today7dAvgKg) <= round1InUnit(targetWeightKg)`)**: The container transitions to `PaceTargetsState.GoalReached` (`"Goal reached! 🎉"`), matching the `ForecastBanner` state.
- **On or After Cycle End Date (`today >= cycle.endDate` while above goal)**: When `daysRemaining <= 0`, required pace cannot divide by zero or negative remaining days; the container transitions to `PaceTargetsState.CycleEnded` (`"Cycle ended"`).
- **Missing `t - 7d` Raw Weigh-In on Daily Row**: When `referenceDate.minusDays(7)` has no non-deleted weigh-in, Daily `Expected Today` emits `ColumnState.InsufficientHistory` (`"Needs 7 days history"`) if `t - 7d < cycleStartDate`, or `ColumnState.NeedsLog(t - 7d)` (`"Needs log from <d MMM>"`) if `t - 7d >= cycleStartDate`.
- **Missing Prior 7-Day Window `[t - 13d, t - 7d]` on 7D Average Row**: When no non-deleted weigh-ins exist in `[t - 13d, t - 7d]`, 7D Average `Expected Today` emits `ColumnState.InsufficientHistory` (`"Needs 7 days history"`) if `t - 7d < cycleStartDate`, or `ColumnState.NeedsLog(t - 7d)` if `t - 7d >= cycleStartDate`.
- **Imperial Unit (`lbs`) Conversion**: All target weights, deltas, and daily steps are converted to `lbs` (`1 kg = 2.20462262185 lbs`) before 2-decimal rounding (`round2`) and `diff2` subtraction so displayed values and deltas reconcile to `0.01 lbs`.

## Requirements *(mandatory)*

### Functional Requirements

- **FR-001**: The system MUST compute the exact unrounded daily and weekly Required Pace in the active display unit (`kg` or `lbs`) from the current 7-day rolling average (`today7dAvgKg`), the cycle target weight (`targetWeightKg`), and the remaining calendar days until `cycle.endDate` (`daysRemaining = ChronoUnit.DAYS.between(today, cycleEndDate)`):
  - `dailyRequiredRateInUnit = -(toUnit(today7dAvgKg) - toUnit(targetWeightKg)) / daysRemaining`
  - `weeklyRequiredRateInUnit = dailyRequiredRateInUnit * 7.0`
- **FR-002**: The system MUST compute the 2-decimal **Theoretical Linear Weight** (`Linear Today`) for the evaluation date (`today`) using linear interpolation between `(cycleStartDate, startWeightKg)` and `(cycleEndDate, targetWeightKg)`:
  - `W_linear_display = round2(startInUnit + (targetInUnit - startInUnit) * (elapsedDays.toDouble() / totalCycleDays.toDouble()))`
- **FR-003**: The system MUST compute a **7D Rolling Average Target Row** (`TargetRowState`) containing:
  - `linearToday`: `W_linear_display` with delta `diff2(round2(today7dAvgInUnit), W_linear_display)` and status (`AHEAD` when `< 0.00`, `OFF_PACE` when `> 0.00`, `ON_PACE` when `== 0.00`).
  - `expectedToday`: `round2(lastWeek7dAvgInUnit + weeklyRequiredRateInUnit)` with delta `diff2(round2(today7dAvgInUnit), expectedTodayDisplay)` and status (`AHEAD`, `OFF_PACE`, `ON_PACE`), or `InsufficientHistory` / `NeedsLog(t - 7d)` when `lastWeek7dAvgKg` is unavailable.
  - `targetTomorrow`: `round2(today7dAvgInUnit + dailyRequiredRateInUnit)` with daily step `round2(dailyRequiredRateInUnit)` formatted as `"-X.XX <unit>/day"`.
- **FR-004**: The system MUST compute a **Daily Weight Target Row** (`TargetRowState`) containing:
  - `linearToday`: `W_linear_display` with delta `diff2(round2(todayDailyInUnit), W_linear_display)` and status when today's raw weigh-in is logged, or `deltaDisplay = null` and `status = null` when today is unlogged.
  - `expectedToday`: `round2(tMinus7DailyInUnit + weeklyRequiredRateInUnit)` with delta against today's raw weigh-in when logged (or `null` delta when today is unlogged), or `InsufficientHistory` / `NeedsLog(t - 7d)` when `t - 7d` is unlogged.
  - `targetTomorrow`: `round2(todayDailyInUnit + dailyRequiredRateInUnit)` with daily step `round2(dailyRequiredRateInUnit)` formatted as `"-X.XX <unit>/day"` when today is logged, or `ColumnState.NeedsTodayLog` (`"—"`, `"Needs today's log"`) when today is unlogged.
- **FR-005**: The UI MUST render a unified `"PACE & LINEAR TARGETS"` container on the `WeightProgressionHeroCard` displaying the `7D ROLLING AVERAGE` row and the `DAILY WEIGH-IN` row, each structured into 3 columns (`Linear Today`, `Expected Today`, `Target Tomorrow`) with 2-decimal formatted weights and status/step subtitles.
- **FR-006**: The system MUST remove the legacy `PaceTargetsState.Stalled` blocker and evaluate container lifecycle states in strict precedence order: `Unavailable` -> `GoalReached` -> `CycleEnded` -> `Available`.

### Key Entities

- **PaceTargetsState**: Sealed hierarchy representing the lifecycle state of the Pace & Linear Targets container (`Unavailable`, `GoalReached`, `CycleEnded`, `Available(sevenDayAvgRow, dailyWeightRow)`).
- **TargetRowState**: Data holder grouping the three 2-decimal target columns (`linearToday`, `expectedToday`, `targetTomorrow`) for a specific measurement track (`7D Rolling Average` or `Daily Weight`).
- **ColumnState**: Sealed hierarchy representing an individual target cell (`Value`, `NeedsLog`, `NeedsTodayLog`, `InsufficientHistory`).

## Success Criteria *(mandatory)*

### Measurable Outcomes

- **SC-001**: 100% of displayed target weights (`Linear Today`, `Expected Today`, `Target Tomorrow`) and their deltas/daily steps reconcile to `0.01` precision (`2` decimal places) in both `kg` and `lbs`.
- **SC-002**: Sub-`0.1` daily required steps (such as `-0.06 kg/day` when weekly required pace is `-0.4 kg/wk`) are 100% visible in `Target Tomorrow` without rounding to zero change.
- **SC-003**: Both rows (`7D ROLLING AVERAGE` and `DAILY WEIGH-IN`) and all 3 columns (`Linear Today`, `Expected Today`, `Target Tomorrow`) render with zero text clipping or layout overflow on `360dp`-wide viewports up to `130%` accessibility font scale.

## Assumptions

- When `cycle.startingWeightKg` is unset, `startWeightKg` falls back to the earliest active in-cycle weigh-in, matching the existing `WeightProgressionCard` convention.
- Historical non-deleted weigh-ins prior to `cycle.startDate` are eligible for the `t - 7d` lookback window once at least one active in-cycle weigh-in exists, consistent with `computeRollingWeightHero` and `computeCurrent7DayAverageWeight`.
