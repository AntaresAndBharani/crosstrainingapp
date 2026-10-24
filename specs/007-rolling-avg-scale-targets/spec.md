# Feature Specification: Multi-Window Rolling Average Scale Targets (7D, 14D, 21D, 28D) in Pace & Linear Targets

**Feature Branch**: `[feat/007-rolling-avg-scale-targets]`

**Created**: 2026-10-24

**Status**: Ready

**Input**: User description: "In pace and linear targets, I am not sure if Daily weight-in and 7D numbers are correct... Let's start by 7D Rolling average. We have to review how we calculate these numbers. Linear today is the 7D AVG for today to go on track. Expected today must be the weight expected today to have that weight (7d rolling average weight). Target tomorrow is the weight I have to weight tomorrow to follow the 7D average. Remove the separate DAILY WEIGH-IN row from PACE & LINEAR TARGETS since the 7D ROLLING AVERAGE row now directly gives the scale weights needed today and tomorrow. Then add the same than 7d for 14 d, 21 d and 28 d. Anchor 14D, 21D, and 28D Expected Today to N days ago (14, 21, 28 days ago) + N days of required pace instead of 7 days ago + 1 week of required pace."

## User Scenarios & Testing *(mandatory)*

### User Story 1 - Daily Scale Weights Needed Today & Tomorrow to Hit 7D Rolling Average Targets (Priority: P1)

As an athlete tracking a Fat Loss & Bodybuilding cycle with a defined target weight and cycle end date, I want the `7D ROLLING AVERAGE` row in `PACE & LINEAR TARGETS` to display:
1. **Linear Today**: The theoretical 7D rolling average for today on the straight-line trajectory from cycle start to cycle end, with a delta comparing my actual 7D rolling average against that linear target.
2. **Expected Today**: The exact **daily scale weight** I must weigh today (`t`) so that my 7-day rolling average over `[t - 6d, t]` equals today's required-pace 7D average (`7D Average at t - 7d + 7 × dailyRequiredRate`), with a delta comparing my actual today's weigh-in against this expected scale weight.
3. **Target Tomorrow**: The exact **daily scale weight** I must weigh tomorrow (`t + 1d`) so that tomorrow's 7-day rolling average over `[t - 5d, t + 1d]` equals tomorrow's required-pace 7D average (`7D Average at t + 1 × dailyRequiredRate`), with the daily required average step (`-X.XX <unit>/day`) as subtitle.

**Why this priority**: Previously, `Expected Today` and `Target Tomorrow` in the `7D ROLLING AVERAGE` row displayed target 7-day averages (`75.44 kg` and `75.45 kg`) rather than the actual scale weight needed today or tomorrow to achieve those averages. When an athlete's daily weight is `73.9 kg` and their 7D average is `75.51 kg` (due to heavier weigh-ins earlier in the week), showing `Target Tomorrow: 75.45 kg` as a target average looks contradictory compared to today's scale weight, and does not tell the athlete what scale reading today or tomorrow will produce the required 7-day average. Solving for the exact scale weight `(k + 1) × TargetAvg - sum(prior Window Entries)` answers what the athlete must weigh on the scale today and tomorrow.

**Independent Test**: Given a 70-day cycle from `78.00 kg` (`1 Oct 2026`) to `71.00 kg` (`10 Dec 2026`), evaluated on `22 Oct 2026` (`21` elapsed days, `49` remaining days / `7.0` weeks) where the prior 7D window `[9 Oct, 15 Oct]` averaged `76.50 kg`, the 6 prior days `[16 Oct, 21 Oct]` each had `75.95 kg` (sum = `455.70 kg`), and today (`22 Oct`) has `75.60 kg` (making today's 7D average `75.90 kg`, required rate = `-0.10 kg/day` / `-0.70 kg/wk`), verify that:
- `Linear Today` displays `"75.90 kg"` with subtitle `"On pace"`.
- `Expected Today` solves for today's required-pace 7D average (`76.50 - 0.70 = 75.80 kg`): `7 × 75.80 - 455.70 = 74.90 kg`, displaying `"74.90 kg"` with subtitle `"+0.70 kg off pace"` (`75.60 - 74.90`).
- `Target Tomorrow` solves for tomorrow's required-pace 7D average (`75.90 - 0.10 = 75.80 kg`) given `[17 Oct, 22 Oct]` sum = `5 × 75.95 + 75.60 = 455.35 kg`: `7 × 75.80 - 455.35 = 75.25 kg`, displaying `"75.25 kg"` with subtitle `"-0.10 kg/day"`.

**Acceptance Scenarios**:

1. **Given** an active Fat Loss & Bodybuilding cycle where `today7dAvg` is `75.90 kg`, `targetWeight` is `71.00 kg`, `daysRemaining` is `49` (`dailyRequiredRate = -0.10 kg/day`, `7dRequiredRate = -0.70 kg/wk`), the prior 7D average over `[t - 13d, t - 7d]` is `76.50 kg` (`targetAvgToday = 75.80 kg`), the 6 logged entries in `[t - 6d, t - 1d]` sum to `455.70 kg`, and today's weigh-in at `t` is `75.60 kg`, **When** the `7D ROLLING AVERAGE` row is computed, **Then**:
   - `Expected Today` displays `"74.90 kg"` (`7 × 75.80 - 455.70`) with subtitle `"+0.70 kg off pace"` (`75.60 - 74.90`).
   - `Target Tomorrow` displays `"75.25 kg"` (`7 × (75.90 - 0.10) - 455.35`) with subtitle `"-0.10 kg/day"`.
2. **Given** the same cycle and prior logs where today (`t`) is unlogged (`entryToday == null`), **When** the `7D ROLLING AVERAGE` row is computed, **Then**:
   - `Expected Today` displays `"74.90 kg"` with no delta subtitle (`deltaDisplay = null`), showing the athlete the exact scale weight they need to log today.
   - `Target Tomorrow` computes the scale weight needed tomorrow using the `k` logged entries in `[t - 5d, t]` (`(k + 1) × targetAvgTomorrow - sum([t - 5d, t])`) with subtitle `"-X.XX kg/day"`.

---

### User Story 2 - Multi-Window Rolling Average Rows (14D, 21D, 28D) & Removal of Redundant Daily Weigh-In Row (Priority: P1)

As an athlete tracking short-, medium-, and long-term weight trends across `7D`, `14D`, `21D`, and `28D` horizons, I want `PACE & LINEAR TARGETS` to replace the redundant `DAILY WEIGH-IN` row with four rolling average rows (`7D ROLLING AVERAGE`, `14D ROLLING AVERAGE`, `21D ROLLING AVERAGE`, and `28D ROLLING AVERAGE`), where each `ND` row anchors its `Expected Today` target average to the `ND` average `N` days ago (`[t - 2N + 1, t - N]`) plus `N` days of required pace (`N × dailyRequiredRate`), and solves for the daily scale weight needed today and tomorrow to keep that `ND` rolling average on track.

**Why this priority**: Because each rolling average row now directly computes the scale weights needed today (`Expected Today`) and tomorrow (`Target Tomorrow`), a separate `DAILY WEIGH-IN` row is redundant. Expanding the container to `7D`, `14D`, `21D`, and `28D` rolling averages aligns `PACE & LINEAR TARGETS` with the 4-horizon pace grid and `GOAL ARRIVAL ESTIMATIONS` card (`7D`, `14D`, `21D`, `28D`).

**Independent Test**: Populate 56 days of weigh-in history in an active Fat Loss & Bodybuilding cycle and verify that the `PACE & LINEAR TARGETS` container renders four sections (`7D ROLLING AVERAGE`, `14D ROLLING AVERAGE`, `21D ROLLING AVERAGE`, `28D ROLLING AVERAGE`), does NOT render a `DAILY WEIGH-IN` section inside `PACE & LINEAR TARGETS`, and computes each `ND` row using its respective `N`-day window `[t - (N - 1), t]` and prior `N`-day anchor window `[t - 2N + 1, t - N]`.

**Acceptance Scenarios**:

1. **Given** an active Fat Loss & Bodybuilding cycle with sufficient history across 7D, 14D, 21D, and 28D windows, **When** `TheoreticalPaceTargetsBox` renders, **Then** it displays four rows titled `"7D ROLLING AVERAGE"`, `"14D ROLLING AVERAGE"`, `"21D ROLLING AVERAGE"`, and `"28D ROLLING AVERAGE"`, and does not display `"DAILY WEIGH-IN"` inside `TheoreticalPaceTargetsBox`.
2. **Given** any horizon `N` in `{7, 14, 21, 28}`, **When** `Expected Today` is calculated for row `N`, **Then**:
   - The current `ND` average `todayNdAvg` is computed over `[t - (N - 1), t]`.
   - The required daily rate for row `N` is `dailyRequiredRate = -(todayNdAvg - targetWeight) / daysRemaining`, and the `N`-day required change is `N × dailyRequiredRate`.
   - The prior `ND` average `priorNdAvg` is computed over `[t - 2N + 1, t - N]` (`N` days ago).
   - Today's target `ND` average is `targetAvgToday = priorNdAvg + N × dailyRequiredRate`.
   - `Expected Today` scale weight is `round2((k_today + 1) × targetAvgToday - sum(entries in [t - (N - 1), t - 1]))`.
3. **Given** any horizon `N` in `{7, 14, 21, 28}`, **When** `Target Tomorrow` is calculated for row `N`, **Then**:
   - Tomorrow's target `ND` average is `targetAvgTomorrow = todayNdAvg + dailyRequiredRate`.
   - `Target Tomorrow` scale weight is `round2((k_tomorrow + 1) × targetAvgTomorrow - sum(entries in [t - (N - 2), t]))`.

---

### User Story 3 - Horizon-Specific History Maturity & Missing-Log Fallbacks (Priority: P2)

As an athlete early in a cycle (e.g., with 10 days of logs, so 7D history is available but 14D, 21D, and 28D lookbacks are not yet reached), I want each `ND` row (`7D`, `14D`, `21D`, `28D`) to independently display its computable columns while showing clear horizon-specific history prompts (`"Needs 7 days history"`, `"Needs 14 days history"`, `"Needs 21 days history"`, `"Needs 28 days history"`) or missing-log prompts (`"Needs log from <d MMM>"`) when its `t - N` prior window is empty.

**Why this priority**: Athletes build up from 7 days of history to 14, 21, and 28 days over the first month of a cycle. Each horizon row must degrade gracefully without blocking the shorter horizons that already have sufficient data.

**Independent Test**: Evaluate `compute7DayPaceTargets` on day 10 of a cycle with daily weigh-ins from `cycleStartDate` (`t - 9d`) to `t`. Verify that `7D ROLLING AVERAGE` shows all 3 numeric columns (`Linear Today`, `Expected Today`, `Target Tomorrow`), while `14D`, `21D`, and `28D` rows show numeric values for `Linear Today` and `Target Tomorrow` and `"—"` with `"Needs 14 days history"`, `"Needs 21 days history"`, and `"Needs 28 days history"` respectively for `Expected Today`.

**Acceptance Scenarios**:

1. **Given** a cycle where `t - N` is before `cycleStartDate` and no weigh-ins exist in the prior `ND` window `[t - 2N + 1, t - N]`, **When** `Expected Today` is evaluated for row `N`, **Then** it emits `ColumnState.InsufficientHistory` (formatted with `TargetRowState.windowDays = N`) which displays value `"—"` and subtitle `"Needs N days history"` (e.g., `"Needs 14 days history"`).
2. **Given** a cycle where `t - N` is on or after `cycleStartDate` and no weigh-ins exist in the prior `ND` window `[t - 2N + 1, t - N]`, **When** `Expected Today` is evaluated for row `N`, **Then** it emits `ColumnState.NeedsLog(requiredDate = t - N)` which displays value `"—"` and subtitle `"Needs log from <d MMM>"`.

---

### Edge Cases

- **Missing Cycle Goal Weight or End Date**: When `targetWeightKg == null`, `targetWeightKg <= 0.0`, `targetWeightKg >= startWeightKg`, or `cycle.endDate == null` (`totalCycleDays <= 0`), the container transitions to `PaceTargetsState.Unavailable` (`"Pace & Linear Targets Unavailable: Set cycle goal weight and end date"`).
- **Goal Already Reached (`round1InUnit(today7dAvgKg) <= round1InUnit(targetWeightKg)`)**: The container transitions to `PaceTargetsState.GoalReached` (`"Goal reached! 🎉"`), matching the `ForecastBanner` state.
- **On or After Cycle End Date (`today >= cycle.endDate` while above goal)**: When `daysRemaining <= 0`, the container transitions to `PaceTargetsState.CycleEnded` (`"Cycle ended"`).
- **Single Log in Window (`k_today == 0` in `[t - (N - 1), t - 1]`)**: When no entries exist in `[t - (N - 1), t - 1]`, `k_today = 0` and `sum = 0.0`, so `(0 + 1) × targetAvgToday - 0.0 = targetAvgToday`, avoiding any division by zero.
- **Non-Physical Solved Scale Weight (Known Limitation)**: Because `Expected Today` solves for a single day's weigh-in that absorbs the full gap between the window's prior entries and the anchored target average, sparse/plateaued windows (especially `21D`/`28D` with many prior entries above the required trajectory) can yield implausibly low or even negative solved scale weights. The current release displays the exact solved value; presentation of unreachable targets is tracked as a follow-up.
- **Imperial Unit (`lbs`) Conversion**: All entry weights, window averages, required rates, target averages, and solved scale weights are evaluated in the active display unit (`kg` or `lbs`) before 2-decimal rounding (`round2`) and `diff2` subtraction.

## Requirements *(mandatory)*

### Functional Requirements

- **FR-001**: For each horizon `N` in `HORIZON_WINDOWS = listOf(7, 14, 21, 28)`, the system MUST compute the current `ND` rolling average `todayNdAvgKg` over `[t - (N - 1), t]` from non-deleted weigh-ins up to `t` (falling back to the latest active in-cycle weigh-in when `[t - (N - 1), t]` is empty), and compute the horizon's exact unrounded daily and `N`-day required pace in the active display unit:
  - `dailyRequiredRateInUnit = -(toUnit(todayNdAvgKg) - toUnit(targetWeightKg)) / daysRemaining`
  - `windowRequiredRateInUnit = dailyRequiredRateInUnit * N.toDouble()`
- **FR-002**: The system MUST compute the 2-decimal **Theoretical Linear Weight** (`Linear Today`) for the evaluation date (`t`) using linear interpolation between `(cycleStartDate, startWeightKg)` and `(cycleEndDate, targetWeightKg)`:
  - `W_linear_display = round2(startInUnit + (targetInUnit - startInUnit) * (elapsedDays.toDouble() / totalCycleDays.toDouble()))`
  - For each horizon row `N` (`7D`, `14D`, `21D`, `28D`), `linearToday` MUST compare `round2(toUnit(todayNdAvgKg))` against `W_linear_display` via `diff2(round2(toUnit(todayNdAvgKg)), W_linear_display)` and assign `PaceTargetStatus` (`AHEAD` when `< 0.00`, `OFF_PACE` when `> 0.00`, `ON_PACE` when `== 0.00`).
- **FR-003**: For each horizon row `N` (`7D`, `14D`, `21D`, `28D`), the system MUST compute `expectedToday` as the **daily scale weight needed today (`t`)** to achieve today's required-pace `ND` rolling average:
  - Resolve the prior `ND` average `priorNdAvgKg` over `[t - 2N + 1, t - N]`.
  - If `priorNdAvgKg == null`, emit `ColumnState.InsufficientHistory` (formatted with `TargetRowState.windowDays = N`) when `t - N < cycleStartDate`, or `ColumnState.NeedsLog(requiredDate = t - N)` when `t - N >= cycleStartDate`.
  - Otherwise, compute `targetAvgTodayInUnit = toUnit(priorNdAvgKg) + windowRequiredRateInUnit`, gather all non-deleted weigh-ins in the prior `(N - 1)` days of today's window `[t - (N - 1), t - 1]` (count `k_today`, sum in display unit `sumPriorTodayInUnit`), and compute:
    `expectedScaleTodayDisplay = round2((k_today + 1) * targetAvgTodayInUnit - sumPriorTodayInUnit)`
  - When today (`t`) has a logged weigh-in (`entryToday != null`), compute `delta = diff2(round2(toUnit(entryToday.weightKg)), expectedScaleTodayDisplay)` and `status = paceStatusFor(delta)`; when today (`t`) is unlogged, set `deltaDisplay = null` and `status = null`.
- **FR-004**: For each horizon row `N` (`7D`, `14D`, `21D`, `28D`), the system MUST compute `targetTomorrow` as the **daily scale weight needed tomorrow (`t + 1`)** to achieve tomorrow's required-pace `ND` rolling average:
  - Compute `targetAvgTomorrowInUnit = toUnit(todayNdAvgKg) + dailyRequiredRateInUnit`.
  - Gather all non-deleted weigh-ins in the prior `(N - 1)` days of tomorrow's window `[t - (N - 2), t]` (count `k_tomorrow`, sum in display unit `sumPriorTomorrowInUnit`), and compute:
    `targetScaleTomorrowDisplay = round2((k_tomorrow + 1) * targetAvgTomorrowInUnit - sumPriorTomorrowInUnit)`
  - Format the subtitle with the daily required average step `dailyStepDisplay = -abs(round2(dailyRequiredRateInUnit))` as `"-X.XX <unit>/day"`.
- **FR-005**: The UI (`TheoreticalPaceTargetsBox`) MUST remove the `DAILY WEIGH-IN` row and render four horizon rows in ascending order: `"7D ROLLING AVERAGE"`, `"14D ROLLING AVERAGE"`, `"21D ROLLING AVERAGE"`, and `"28D ROLLING AVERAGE"`, separated by subtle horizontal dividers.
- **FR-006**: `TargetRowState` MUST carry a `windowDays: Int = 7` property (keeping `ColumnState.InsufficientHistory` a `data object`, see `research.md` Decision 3), and `resolveExpectedTodaySubtitle` / `resolveTargetTomorrowSubtitle` MUST accept `windowDays: Int = 7` so that `ColumnState.InsufficientHistory` renders `"Needs ${windowDays} days history"` (e.g., `"Needs 7 days history"`, `"Needs 14 days history"`, `"Needs 21 days history"`, `"Needs 28 days history"`).

### Key Entities

- **PaceTargetsState.Available**: Holds the rolling average target rows across `7D`, `14D`, `21D`, and `28D` (`sevenDayAvgRow`, `fourteenDayAvgRow`, `twentyOneDayAvgRow`, `twentyEightDayAvgRow`, and an ordered `rows: List<TargetRowState>` list).
- **TargetRowState**: Holds `windowDays: Int`, `linearToday: ColumnState`, `expectedToday: ColumnState`, and `targetTomorrow: ColumnState`.
- **ColumnState.InsufficientHistory**: Remains a `data object`; horizon-specific history messaging is driven by `TargetRowState.windowDays`.

## Success Criteria *(mandatory)*

### Measurable Outcomes

- **SC-001**: For every horizon `N` in `{7, 14, 21, 28}`, substituting the unrounded solved scale weight into today's window `[t - (N - 1), t]` or tomorrow's window `[t - (N - 2), t + 1]` reproduces the target `ND` rolling average to within `0.01` display units (`kg` or `lbs`).
- **SC-002**: 100% of `PACE & LINEAR TARGETS` renders display the four rolling average rows (`7D`, `14D`, `21D`, `28D`) and zero instances of the removed `DAILY WEIGH-IN` row inside `TheoreticalPaceTargetsBox`.
- **SC-003**: All four rolling average rows and their 3 columns render with zero text clipping or layout overflow on `360dp`-wide viewports up to `130%` accessibility font scale.

## Assumptions

- Pre-cycle non-deleted weigh-ins are eligible for lookback windows (`[t - (N - 1), t]` and `[t - 2N + 1, t - N]`) once at least one active in-cycle weigh-in exists, consistent with `computeRollingWeightHero` and `computePaceRate`.
- The secondary raw same-day weigh-in comparison section (`DAILY WEIGH-IN (<DAY>)` / `SameDayWeightTrioRow` above the pace chips) remains unchanged; only the redundant `DAILY WEIGH-IN` row inside the `PACE & LINEAR TARGETS` container is removed.
