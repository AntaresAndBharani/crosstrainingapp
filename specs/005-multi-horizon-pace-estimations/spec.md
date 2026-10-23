# Feature Specification: 21D/28D Pace Grid & Visual Multi-Horizon Goal Estimations Card (7D, 14D, 21D, 28D)

**Feature Branch**: `[feat/005-multi-horizon-pace-estimations]`

**Created**: 2026-10-23

**Status**: Ready

**Input**: User description: "I want to add 21-D pace and 28-D pace. Estimated part must be redraw to be more visual as well and add the estimations for 7D,14,21 and 28 D paces."

## User Scenarios & Testing *(mandatory)*

### User Story 1 - 2×2 Rolling Pace Grid with 7D, 14D, 21D, and 28D Paces at 2-Decimal Precision (Priority: P1)

As an athlete tracking a Fat Loss & Bodybuilding cycle on the Progress & Goals screen, I want to see my **7-Day Pace**, **14-Day Pace**, **21-Day Pace**, and **28-Day Pace** arranged in a **2×2 grid** with **2-decimal weekly rate precision** (e.g., `▼ -0.28 kg/wk`, `▼ -0.24 kg/wk`, `▼ -0.19 kg/wk`, `▼ -0.16 kg/wk`) and week-over-week comparison subtitles, so that I can compare my short-, medium-, and monthly-smoothed weight loss velocities without 1-decimal rounding collapsing distinct horizons into the same number.

**Why this priority**: Previously, the Weight Progression card only showed 7-Day and 14-Day Pace rounded to 1 decimal place (`-0.2 kg/wk`). Over 14, 21, and 28 days, weekly pace rates frequently differ by less than `0.1 kg/wk` (such as `-0.24`, `-0.19`, and `-0.16 kg/wk`). Adding 21-Day and 28-Day Pace in a 2×2 grid at 2-decimal precision gives the athlete complete visibility into 1-, 2-, 3-, and 4-week rolling trends.

**Independent Test**: View the Weight Progression card for an active Fat Loss & Bodybuilding cycle with 30 days of logged weigh-ins where the 7D pace is `-0.284 kg/wk`, 14D pace is `-0.238 kg/wk`, 21D pace is `-0.192 kg/wk`, and 28D pace is `-0.161 kg/wk`. Verify that the 2×2 grid renders `7-Day Pace` (`▼ -0.28 kg/wk`), `14-Day Pace` (`▼ -0.24 kg/wk`), `21-Day Pace` (`▼ -0.19 kg/wk`), and `28-Day Pace` (`▼ -0.16 kg/wk`), each with its corresponding week-over-week comparison subtitle or maturity fallback.

**Acceptance Scenarios**:

1. **Given** an active Fat Loss & Bodybuilding cycle with 30 days of weigh-ins where 7D pace is `-0.284 kg/wk`, 14D pace is `-0.238 kg/wk`, 21D pace is `-0.192 kg/wk`, and 28D pace is `-0.161 kg/wk`, **When** the Pace Cards section renders in `kg`, **Then** it displays a 2×2 grid:
   - Row 1: `7-Day Pace` (`"▼ -0.28 kg/wk"`) and `14-Day Pace` (`"▼ -0.24 kg/wk"`)
   - Row 2: `21-Day Pace` (`"▼ -0.19 kg/wk"`) and `28-Day Pace` (`"▼ -0.16 kg/wk"`)
2. **Given** a cycle where 18 calendar days have elapsed (with no pre-cycle history), **When** the 2×2 Pace Grid renders, **Then** `7-Day Pace` and `14-Day Pace` display their 2-decimal weekly rates, while `21-Day Pace` displays `"—"` with subtitle `"Needs weigh-in 21+ days ago"` and `28-Day Pace` displays `"—"` with subtitle `"Needs weigh-in 28+ days ago"`.
3. **Given** a cycle where 23 calendar days have elapsed (so 21-Day Pace is computable today at `t`, but was not computable 7 days ago at `t - 7d`), **When** the `21-Day Pace` card renders, **Then** its subtitle displays `"Smoothed trend"`.

---

### User Story 2 - Redrawn Visual Multi-Horizon Goal Estimations Card (7D, 14D, 21D, 28D) (Priority: P1)

As an athlete tracking a Fat Loss & Bodybuilding cycle toward a target weight and cycle end date, I want the Forecast / Estimations section redrawn into a **visual comparative card (`GOAL ARRIVAL ESTIMATIONS`)** featuring a **Target Benchmark Header** (Target Date, remaining weeks, and 2-decimal Required Pace) and **4 visual horizon rows (`7D`, `14D`, `21D`, `28D`)**—each with estimated weeks, projected arrival date, a proportional color-coded **Pace-to-Required Sufficiency Bar**, and an **ahead/late weeks badge**—so that I can visually compare when I will reach my goal under each of the 4 pace horizons.

**Why this priority**: Previously, the `ForecastBanner` was a 4-line text box showing only a single pace tier (`Based on 14-day pace`). Redrawing it into a visual 4-row comparative card lets the athlete immediately see how all 4 pace horizons (`7D`, `14D`, `21D`, `28D`) compare against the required pace and target end date.

**Independent Test**: On `23 Oct 2026` during a cycle ending `28 Dec 2026` (`66` days / `9.4` weeks left) with `3.7 kg` remaining to goal (`Required: -0.39 kg/wk`), where 7D pace is `-0.28 kg/wk`, 14D pace is `-0.24 kg/wk`, 21D pace is `-0.42 kg/wk`, and 28D pace is unavailable (`null`), verify that the `GOAL ARRIVAL ESTIMATIONS` card renders the benchmark header and all 4 horizon rows with their 2-decimal pace labels, estimated weeks, projected arrival dates, proportional sufficiency bars, and ahead/late badges.

**Acceptance Scenarios**:

1. **Given** a cycle with `targetWeightKg = 72.0 kg`, `endDate = 28 Dec 2026` (`66` days / `9.43` weeks remaining), and today's 7D Average at `75.7 kg` (`3.7 kg` remaining, 2-decimal Required Pace = `-0.39 kg/wk`), **When** the `GOAL ARRIVAL ESTIMATIONS` card renders, **Then** the header displays `"Target: 28 Dec 2026 (9.4w left) · Required: -0.39 kg/wk"`.
2. **Given** the same cycle where 7D pace is `-0.28 kg/wk`, 14D pace is `-0.24 kg/wk`, and 21D pace is `-0.42 kg/wk`, **When** the horizon rows render, **Then**:
   - The `7D` row displays `"7D (-0.28 kg/wk)"` and `"13.2 wks · 23 Jan 2027"` on Line 1, and a `72%` (`0.28 / 0.39`) amber/tertiary sufficiency bar with badge `"+3.8w late"` on Line 2.
   - The `14D` row displays `"14D (-0.24 kg/wk)"` and `"15.4 wks · 8 Feb 2027"` on Line 1, and a `62%` (`0.24 / 0.39`) amber/tertiary sufficiency bar with badge `"+6.0w late"` on Line 2.
   - The `21D` row displays `"21D (-0.42 kg/wk)"` and `"8.8 wks · 23 Dec 2026"` on Line 1, and a `100%` primary/green sufficiency bar with badge `"On time (-0.6w)"` on Line 2.
3. **Given** an open-ended Fat Loss cycle where `targetWeightKg` is set (`72.0 kg`) but `cycle.endDate` is `null`, **When** the `GOAL ARRIVAL ESTIMATIONS` card renders, **Then** the header displays the remaining weight to goal (`"Goal: 72.0 kg (-3.7 kg left)"`), and each active horizon row displays its estimated weeks, projected arrival date, and sufficiency bar scaled relative to the fastest active horizon without an ahead/late date badge.

---

### User Story 3 - Per-Horizon Graceful States for Insufficient History, Stalled Paces & Terminal Cycle States (Priority: P2)

As an athlete early in a cycle (under 21 or 28 days) or experiencing a single refeed/maintenance week on the 7D horizon while 14D/21D/28D remain in deficit, I want all 4 rows (`7D`, `14D`, `21D`, `28D`) to always remain visible in the `GOAL ARRIVAL ESTIMATIONS` card with clear per-row status indicators (`"Needs 21+ days"`, `"Needs 28+ days"`, `"Pace stalled"`, `"> 1 year"`), so that a single stalled or immature horizon never hides valid projections from the other horizons.

**Why this priority**: In real-world fat loss cycles, short-term 7D pace may briefly stall or gain after a high-sodium meal or refeed day even while 14D, 21D, and 28D paces are steadily losing weight. Evaluating each horizon independently ensures continuous visibility across all 4 timeframes.

**Independent Test**: Evaluate the `GOAL ARRIVAL ESTIMATIONS` card when 7D pace is `+0.15 kg/wk` (gaining), 14D pace is `-0.25 kg/wk`, 21D pace is `-0.30 kg/wk`, and 28D pace is `null`. Verify that the 7D row shows `"Pace stalled"` (`0%` bar, `"Stalled"` badge), 14D and 21D show their full week/date projections and bars, and 28D shows `"Needs 28+ days"` (`0%` bar).

**Acceptance Scenarios**:

1. **Given** 7D pace is `+0.15 kg/wk` (gaining), 14D pace is `-0.25 kg/wk`, 21D pace is `-0.30 kg/wk`, and 28D pace is `null`, **When** the `GOAL ARRIVAL ESTIMATIONS` card renders, **Then**:
   - `7D` row displays `"7D (+0.15 kg/wk)"`, `"Pace stalled"`, `0%` bar fill, and badge `"Stalled"`.
   - `14D` and `21D` rows display their estimated weeks, arrival dates, proportional sufficiency bars, and badges.
   - `28D` row displays `"28D (—)"`, `"Needs 28+ days"`, and `0%` bar fill.
2. **Given** today's 7D Rolling Average reaches or drops below `targetWeightKg` (at 1-decimal display precision), **When** the Estimations section renders, **Then** it displays the celebratory `"Goal reached! 🎉"` banner.
3. **Given** `today > cycle.endDate` while today's 7D Rolling Average is still above `targetWeightKg`, **When** the Estimations section renders, **Then** it displays the `"Cycle ended"` banner.

---

### Edge Cases

- **Projection Exceeding 52 Weeks (`rawWeeks > 52.0`)**: When a horizon's positive deficit velocity is very small (e.g., `-0.06 kg/wk` with `4.0 kg` remaining = `66.7 weeks`), that horizon row caps at `52.0 weeks`, displays `"> 1 year"` on Line 1 Right, renders its low sufficiency bar fill, and shows badge `"> 1 yr late"` when a cycle end date is set.
- **Cold Start (`< 7 days` of history, all 4 paces `null`)**: All 4 rows (`7D`, `14D`, `21D`, `28D`) render in their `Needs X+ days` state (or if `activeEntries` is empty, `NotEnoughData`), while the Target Benchmark Header still displays the cycle's Required Pace and Target Date if configured.
- **Imperial Unit (`lbs`) Conversion**: All 4 pace cards (`7D`, `14D`, `21D`, `28D`), the Required Pace header, and all 4 horizon estimation rows convert internal `kg` values to `lbs` (`1 kg = 2.20462262185 lbs`) before 2-decimal rounding (`round2`) so displayed rates and week projections reconcile in `lbs/wk`.
- **Small Viewport (`360dp`) & Large Font Scale (`1.3f`)**: The two-line per-horizon row layout ensures the pace label (`"28D (-0.16 kg/wk)"`) and arrival projection (`"15.4 wks · 8 Feb 2027"`) on Line 1 and the bar + delta badge on Line 2 never clip or overlap.

## Requirements *(mandatory)*

### Functional Requirements

- **FR-001**: The system MUST compute 21-day (`compute21DayPace`) and 28-day (`compute28DayPace`) smoothed weekly pace rates using the same rolling 7-day window average engine (`computePaceRate` with `windowDays = 21` and `windowDays = 28`) and same-weekday comparison engine (`computePaceComparisonVsLastWeek` with `windowDays = 21` and `windowDays = 28`) as 7-day and 14-day pace.
- **FR-002**: The system MUST provide 2-decimal weekly pace formatting (`formatDisplay2`, e.g., `"-0.28 kg/wk"`, `"+0.15 kg/wk"`, `"0.00 kg/wk"`) and 2-decimal comparison delta subtitles (`formatPaceSubtitle` with `useTwoDecimals = true`), plus maturity fallback subtitles `"Needs weigh-in 21+ days ago"` and `"Needs weigh-in 28+ days ago"` when `pace == null` and `"Smoothed trend"` when `pace != null && comparison == null`.
- **FR-003**: The UI MUST render a **2×2 Pace Cards Grid** inside `WeightProgressionHeroCard` (Row 1: `7-Day Pace` and `14-Day Pace`; Row 2: `21-Day Pace` and `28-Day Pace`), displaying 2-decimal weekly pace rates and 2-decimal comparison subtitles.
- **FR-004**: The system MUST compute a 2-decimal Required Pace (`requiredRate2InUnit = -maxOf(0.01, round2(remainingWeightInUnit / weeksRemaining))`) in `RequiredPaceResult` (with `formatRate2()` returning `"-0.39 kg/wk"` / `"-0.86 lbs/wk"`) so Required Pace and Pace Cards compare at identical 2-decimal precision.
- **FR-005**: The system MUST provide a multi-horizon forecast calculation (`computeMultiHorizonForecast`) evaluating all 4 horizons (`7`, `14`, `21`, `28` days) and returning a `MultiHorizonForecastState`:
  - `GoalReached`: when `round1InUnit(current7dAvgKg, weightUnit) <= round1InUnit(targetWeightKg, weightUnit)`.
  - `CycleEnded`: when `cycleEndDate != null && today.isAfter(cycleEndDate)`.
  - `NotEnoughData`: when no active in-cycle entries exist.
  - `Active`: containing the target benchmark metadata (`remainingWeightInUnit`, `targetWeightInUnit`, `requiredPace`) and 4 ordered `HorizonEstimationRow` items (`7D`, `14D`, `21D`, `28D`), each in one of `Projected`, `Stalled`, or `NotEnoughData`.
- **FR-006**: For each `Projected` horizon row (`v2 = round2(toUnit(pace.deficitVelocityKgPerWeek, weightUnit)) > 0.00`), the system MUST compute:
  - `rawWeeks = remainingWeightInUnit / v2` (capped at `52.0` weeks when `rawWeeks > 52.0`).
  - `targetDate = today.plusDays(round(rawWeeks * 7.0))` when `rawWeeks <= 52.0`.
  - `barFraction = (v2 / abs(requiredPace.requiredRate2InUnit)).toFloat().coerceIn(0.05f, 1.0f)` when `requiredPace != null` (or scaled against the fastest active horizon velocity when `requiredPace == null`).
  - `weeksDelta = round1(rawWeeks - requiredPace.weeksRemaining)` and `isOnTrack = !isCapped && weeksDelta <= 0.0` (with badge `"On time (-X.Xw)"` when `weeksDelta < -0.05`, `"On time"` when `abs(weeksDelta) <= 0.05`, `"+X.Xw late"` when `weeksDelta > 0.05`, and `"> 1 yr late"` when `isCapped`).
- **FR-007**: The UI MUST redraw the `ForecastBanner` into the visual **Multi-Horizon Goal Estimations Card (`GOAL ARRIVAL ESTIMATIONS`)** displaying the Target Benchmark Header and the 4 two-line horizon rows (`7D`, `14D`, `21D`, `28D`) with color-coded sufficiency bars (`primary` when on track, `tertiary` when late, `0%` when stalled/unavailable) and merged TalkBack accessibility semantics per row.

### Key Entities

- **MultiHorizonForecastState**: Sealed hierarchy representing the state of the redrawn Goal Estimations card (`GoalReached`, `CycleEnded`, `NotEnoughData`, `Active`).
- **HorizonEstimationRow**: Data model for an individual pace horizon (`windowDays`: `7`, `14`, `21`, or `28`) holding its `HorizonRowState` (`Projected`, `Stalled`, `NotEnoughData`).
- **PaceRateResult**: Extended with `formatDisplay2(unit)` for 2-decimal weekly rate rendering.
- **RequiredPaceResult**: Extended with `requiredRate2InUnit`, `formatRate2()`, and `formatBenchmarkHeader()` for 2-decimal benchmark comparison.

## Success Criteria *(mandatory)*

### Measurable Outcomes

- **SC-001**: All 4 rolling pace cards (`7-Day Pace`, `14-Day Pace`, `21-Day Pace`, `28-Day Pace`) and their week-over-week deltas render at 2-decimal precision (`0.01 kg/wk` or `0.01 lbs/wk`) in a 2×2 grid.
- **SC-002**: All 4 horizon rows (`7D`, `14D`, `21D`, `28D`) in the `GOAL ARRIVAL ESTIMATIONS` card accurately compute and display estimated weeks (`remainingWeightInUnit / v2`), projected arrival dates, proportional sufficiency bars (`0%–100%`), and ahead/late week badges (`On time` vs `+X.Xw late`).
- **SC-003**: Both the 2×2 Pace Grid and the 4-Row Goal Estimations card render with zero text clipping or layout overflow on `360dp`-wide viewports up to `130%` accessibility font scale.

## Assumptions

- The existing `computePaceForecast` function and `PaceForecast` sealed class are preserved alongside `computeMultiHorizonForecast` so all existing unit tests for legacy single-tier callers continue to pass without regression.
- Historical non-deleted weigh-ins prior to `cycle.startDate` remain eligible for 7D, 14D, 21D, and 28D rolling window lookbacks once at least one active in-cycle weigh-in exists, consistent with `computePaceRate`.
