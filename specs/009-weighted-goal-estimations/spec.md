# Feature Specification: Weighted Average Pace Estimation & Goal Arrival Estimations Redesign

**Feature Branch**: `[feat/009-weighted-goal-estimations]`

**Created**: 2026-11-01

**Status**: Ready

**Input**: User description: "Redesign the GOAL ARRIVAL ESTIMATIONS section of the mobile app and add a Weighted Average estimation before the 7D average estimation, calculated as a weighted average of the paces (4:3:2:1 across 7D, 14D, 21D, 28D)."

## User Scenarios & Testing *(mandatory)*

### User Story 1 - Weighted Average Pace Estimation Above 7D–28D Horizons (Priority: P1)

As an athlete tracking a Fat Loss & Bodybuilding cycle on the Progress & Goals screen, I want to see a highlighted **Weighted Average** estimation row at the top of the **Goal Arrival Estimations** card—before the `7D`, `14D`, `21D`, and `28D` horizon rows—computed using linear recency weights (`4 : 3 : 2 : 1` corresponding to `40% 7D`, `30% 14D`, `20% 21D`, and `10% 28D`) dynamically re-normalized across available horizons, so that I can immediately understand my blended overall weight-loss trajectory before inspecting individual historical windows.

**Why this priority**: Individual rolling pace windows (`7D`, `14D`, `21D`, `28D`) each capture a single lookback slice: `7D` reacts quickly to recent changes, while `21D` and `28D` reflect longer-term consistency. Presenting a recency-weighted blend (`4:3:2:1`) at the top of the card gives the athlete a single authoritative headline projection that balances recent momentum with multi-week stability.

**Independent Test**: View the Goal Arrival Estimations card for an active Fat Loss & Bodybuilding cycle with `3.1 kg` remaining to goal and `8.1` weeks left (`Target: 28 Dec 2026 (8.1w left) · Required: -0.38 kg/wk`), where `7D` pace is `-0.49 kg/wk`, `14D` pace is `-0.35 kg/wk`, `21D` pace is `-0.31 kg/wk`, and `28D` pace is `-0.31 kg/wk`. Verify that the top `Weighted Average` row computes the `4:3:2:1` weighted pace (`-0.39 kg/wk`), displays the projected weeks and arrival date alongside a highlighted sufficiency progress bar and timing status badge, and is followed by a subtle divider and the 4 historical rows (`7D`, `14D`, `21D`, `28D`).

**Acceptance Scenarios**:

1. **Given** an active Fat Loss & Bodybuilding cycle where all 4 pace horizons are available (`7D = -0.49 kg/wk`, `14D = -0.35 kg/wk`, `21D = -0.31 kg/wk`, `28D = -0.31 kg/wk`), **When** the Weighted Average pace is computed in `kg`, **Then** it applies weights `4, 3, 2, 1` (`(-0.49*4 + -0.35*3 + -0.31*2 + -0.31*1) / 10 = -0.394 -> -0.39 kg/wk`) and projects estimated weeks, arrival date, sufficiency bar fraction, and ahead/late badge from that 2-decimal velocity (`0.39 kg/wk`).
2. **Given** a cycle with 24 calendar days of history where `7D = -0.28 kg/wk`, `14D = -0.22 kg/wk`, `21D = -0.34 kg/wk`, and `28D` is unavailable (`null`), **When** the Weighted Average pace is computed, **Then** it dynamically re-normalizes across the 3 available horizons with weights `4 : 3 : 2` (divisor `9`: `(-0.28*4 + -0.22*3 + -0.34*2) / 9 = -2.46 / 9 = -0.2733 -> -0.27 kg/wk`) and renders the `Weighted Average` projection without waiting for 28 days of history.
3. **Given** a cycle where `7D` pace briefly fluctuates to `+0.10 kg/wk` (gain) while `14D = -0.30 kg/wk`, `21D = -0.35 kg/wk`, and `28D = -0.40 kg/wk`, **When** the Weighted Average pace is computed using signed rates (`(+0.10*4 + -0.30*3 + -0.35*2 + -0.40*1) / 10 = -0.16 kg/wk`), **Then** the `Weighted Average` row still projects a valid arrival estimate (`-0.16 kg/wk`) while the individual `7D` row displays `"Pace stalled"`.
4. **Given** a cycle where the signed weighted average rate is non-negative or within the noise deadband (deficit velocity `<= 0.00` or signed rate `>= -0.05` in kg/wk equivalent), **When** the `Weighted Average` row renders, **Then** it displays its formatted signed pace, `"Pace stalled"`, a `0%` progress bar, and badge `"Stalled"`.

---

### User Story 2 - Redesigned Goal Arrival Estimations Card Hierarchy & Visual Polish (Priority: P1)

As an athlete viewing the Progress & Goals screen, I want the **Goal Arrival Estimations** card redesigned with a clearer visual hierarchy—featuring the uppercase `GOAL ARRIVAL ESTIMATIONS` label, a prominent **`Current Goal & Target`** sub-header above the benchmark line (`Target: 28 Dec 2026 (8.1w left) · Required: -0.38 kg/wk`), a highlighted primary **`Weighted Average`** section with a trend/sparkle indicator icon (`✦` / trend icon) and distinct teal/green-accented sufficiency bar when on track, a subtle horizontal separator before the 4 historical breakdown rows (`7D`, `14D`, `21D`, `28D`), and refined pill-shaped progress tracks—so that I can scan my primary projection and historical breakdowns without visual clutter.

**Why this priority**: Adding a 5th estimation row (`Weighted Average`) into the existing card requires clear visual separation between the headline blended estimate and the 4 historical lookback rows (`7D`, `14D`, `21D`, `28D`), as well as a dedicated `Current Goal & Target` sub-header so the benchmark target date and required pace stand out clearly.

**Independent Test**: Render the `MultiHorizonGoalEstimationsBanner` in `Active` state on a `360dp`-wide viewport at both `1.0f` and `1.3f` font scales. Verify that:
- The header displays `GOAL ARRIVAL ESTIMATIONS`, `Current Goal & Target`, and the benchmark line (`Target: ... · Required: ...`).
- The primary `Weighted Average` row renders above the historical rows with its trend/sparkle indicator, formatted summary (`Weighted pace of -X.XX kg/wk · Y.Y wks · D MMM YYYY` or status), and pill-shaped progress track with status badge.
- A subtle horizontal divider separates the `Weighted Average` primary row from the 4 historical horizon rows (`7D`, `14D`, `21D`, `28D`).
- Zero text clipping or layout overflow occurs at `360dp` width and `1.3f` font scale.

**Acceptance Scenarios**:

1. **Given** an `Active` multi-horizon forecast state, **When** the `GOAL ARRIVAL ESTIMATIONS` card renders, **Then** the header section displays the uppercase `GOAL ARRIVAL ESTIMATIONS` overline, the bold `Current Goal & Target` sub-header, and the benchmark subtitle (`Target: <Date> (<W>w left) · Required: <Rate>` or `Goal: <Weight> (<Remaining> left)`), followed by a horizontal divider.
2. **Given** an `Active` multi-horizon forecast state with a projected `Weighted Average`, **When** the primary estimation row renders above `7D`, **Then** it displays the `Weighted Average` title with its leading trend/sparkle indicator, the weighted pace and arrival details (`Weighted pace of -0.40 kg/wk · 7.7 wks · 25 Dec 2026`), a highlighted pill-shaped sufficiency bar, a timing status badge (`On time (-0.3w)` or `+X.Xw late`), and a subtle horizontal divider separating it from the `7D`, `14D`, `21D`, and `28D` rows below.
3. **Given** an athlete using `lbs` as their active display unit, **When** the card renders, **Then** the `Weighted Average` row and all 4 historical horizon rows convert and round velocities in `lbs/wk` using 2-decimal precision (`round2`) so that displayed weighted pace, estimated weeks, and arrival dates reconcile in `lbs`.

---

### User Story 3 - Terminal States, Accessibility & Responsive Layout Guarantees (Priority: P2)

As an athlete using TalkBack screen reader or large accessibility font sizes (`1.3f` on a `360dp` viewport), or viewing a cycle in `GoalReached`, `CycleEnded`, or `NotEnoughData` states, I want the redesigned card to preserve existing terminal state banners, announce the `Weighted Average` row and each historical horizon row as a single coherent accessibility node, and wrap or scale cleanly without text truncation or overlap.

**Why this priority**: Mobile fitness dashboards are frequently viewed on compact screens or with enlarged system fonts; preserving semantic accessibility announcements and existing terminal states ensures zero regression across user devices.

**Independent Test**: Verify TalkBack `contentDescription` on the `Weighted Average` row and `7D`–`28D` rows, verify `GoalReached`, `CycleEnded`, and `NotEnoughData` banners remain unchanged, and verify zero text overflow on `360dp` width at `fontScale = 1.3f`.

**Acceptance Scenarios**:

1. **Given** the `GOAL ARRIVAL ESTIMATIONS` card in `Active` state, **When** inspected by an accessibility service (TalkBack), **Then** the `Weighted Average` row exposes a merged `contentDescription` announcing `"Weighted Average (Weighted pace of -0.39 kg/wk · 7.9 wks · 26 Dec 2026), On time (-0.2w)"` (or its stalled/capped equivalent) in logical top-to-bottom order before the `7D` row.
2. **Given** a `360dp`-wide screen with `fontScale = 1.3f`, **When** the redesigned `GOAL ARRIVAL ESTIMATIONS` card renders with `Current Goal & Target`, `Weighted Average`, and `7D`–`28D` rows, **Then** the `Weighted Average` summary text wraps cleanly up to 2 lines without clipping, and all horizon rows and badges remain legible without overflow.

---

### Edge Cases

- **Partial Horizon Availability (Cold/Early Cycle, 7–27 Days)**: When only a subset of horizons is available (e.g., only `7D`, or `7D` and `14D`), the `Weighted Average` pace dynamically re-normalizes using the base weights (`7D = 4`, `14D = 3`, `21D = 2`, `28D = 1`) of the non-null horizons (`sum(w_i * rate_i) / sum(w_i)`).
- **Mixed Gain and Loss Across Horizons**: Signed 2-decimal display-unit rates (`round2(toUnit(pace.rateKgPerWeek, unit))`) of all available horizons are included in the weighted sum. If the resulting weighted rate is `< -0.05` (in `kg/wk` equivalent, i.e., `weightedVelocity2InUnit > 0.00` and below the noise threshold), it produces a `Projected` state; otherwise it produces a `Stalled` state (`"Pace stalled"`, `0%` bar, `"Stalled"` badge).
- **Projection Exceeding 52 Weeks (`rawWeeks > 52.0`)**: When the weighted average deficit velocity is positive but very small such that `round1(remainingWeightInUnit / weightedVelocity2InUnit) > 52.0`, the `Weighted Average` row caps at `52.0` weeks, displays `"Weighted pace of -X.XX kg/wk · > 1 year"`, and shows badge `"> 1 yr late"` when a cycle end date is set.
- **Open-Ended Cycle (`cycle.endDate == null`)**: When no target end date is configured, `Current Goal & Target` displays `"Goal: <target> (<remaining> left)"`, and the sufficiency bar benchmark scales against the maximum active deficit velocity across the weighted average and the 4 horizons, with no ahead/late badge.
- **Backward Compatibility with Existing Horizon List**: Existing callers inspecting the 4 historical rows (`active.rows` for `7D`, `14D`, `21D`, `28D`) continue to see the 4 horizon items in order, while `active.weightedAverageRow` exposes the primary weighted average estimation.

## Requirements *(mandatory)*

### Functional Requirements

- **FR-001**: The system MUST define linear recency weights for the 4 pace horizons: `7D -> 4`, `14D -> 3`, `21D -> 2`, `28D -> 1` (representing `40%`, `30%`, `20%`, `10%` when all 4 horizons are available).
- **FR-002**: In `computeMultiHorizonForecast`, when at least one horizon (`7D`, `14D`, `21D`, `28D`) has a non-null `PaceRateResult`, the system MUST compute the **Weighted Average** signed weekly rate in the active display unit by taking the weighted mean of each available horizon's 2-decimal signed rate (`r_i = round2(toUnit(pace_i.rateKgPerWeek, normalizedUnit))`) using the re-normalized weights `w_i / sum_available(w_i)` and rounding to 2 decimal places (`round2`).
- **FR-003**: The system MUST evaluate the **Weighted Average** estimation row (`weightedAverageRow: HorizonEstimationRow`) using the same deterministic projection rules as individual horizons:
  - If the weighted signed rate is below the loss threshold (`weightedRateKgPerWeekEquivalent < -0.05` and `weightedVelocity2InUnit > 0.00`), it enters `HorizonRowState.Projected` with `rawWeeks = remainingWeightInUnit / weightedVelocity2InUnit`, `weeks = min(52.0, round1(rawWeeks))`, `targetDate = today.plusDays(round(roundedWeeks * 7.0))` (when `<= 52.0` weeks), proportional `barFraction`, and `deltaBadgeText` (`"On time (-X.Xw)"`, `"On time"`, `"+X.Xw late"`, or `"> 1 yr late"`).
  - Otherwise, it enters `HorizonRowState.Stalled` with `0%` bar fill and `"Stalled"` badge.
- **FR-004**: `MultiHorizonForecastState.Active` MUST expose `weightedAverageRow: HorizonEstimationRow` alongside the 4 historical `rows: List<HorizonEstimationRow>` (`7D`, `14D`, `21D`, `28D`) and a header title constant `CURRENT_GOAL_AND_TARGET_HEADER = "Current Goal & Target"`.
- **FR-005**: For the `Weighted Average` row, formatting helpers MUST produce:
  - Primary title: `"Weighted Average"`
  - Detail text when `Projected` (uncapped): `"Weighted pace of <paceDisplay2> · <weeks> wks · <d MMM yyyy>"` (e.g., `"Weighted pace of -0.39 kg/wk · 7.9 wks · 26 Dec 2026"`).
  - Detail text when `Projected` (capped `> 52w`): `"Weighted pace of <paceDisplay2> · > 1 year"`.
  - Detail text when `Stalled`: `"Weighted pace of <paceDisplay2> · Pace stalled"`.
  - Merged TalkBack `contentDescription`: `"Weighted Average (<detail text>), <badge>"` (or without badge when `badge == null`).
- **FR-006**: The UI in `MultiHorizonGoalEstimationsBanner` (`Active` state) MUST render:
  1. The uppercase overline `GOAL ARRIVAL ESTIMATIONS`.
  2. The bold sub-header `Current Goal & Target` and the benchmark target/required-pace text (`state.benchmarkHeader`), followed by a subtle horizontal divider.
  3. The highlighted **Weighted Average** primary section with a leading trend/sparkle indicator, bold `"Weighted Average"` label, detail summary, a highlighted pill-shaped sufficiency progress bar (using a distinct teal/green on-track accent or amber/warning late color), and the status badge.
  4. A subtle horizontal divider separating the primary `Weighted Average` estimation from the 4 historical horizon rows (`7D`, `14D`, `21D`, `28D`).
  5. The 4 historical horizon rows (`7D`, `14D`, `21D`, `28D`) with refined vertical spacing and pill-shaped progress tracks.

### Key Entities

- **MultiHorizonForecastState.Active**: Holds `remainingWeightInUnit`, `targetWeightInUnit`, `weightUnit`, `requiredPace`, `rows` (the 4 historical horizons `7D`, `14D`, `21D`, `28D`), and `weightedAverageRow` (the primary `4:3:2:1` weighted average estimation).
- **HorizonEstimationRow**: Represents either a specific lookback horizon (`windowDays` in `{7, 14, 21, 28}`) or the primary Weighted Average estimation (`isWeightedAverage = true`), with formatting helpers for title, detail/right text, and accessibility semantics.

## Success Criteria *(mandatory)*

### Measurable Outcomes

- **SC-001**: Given any combination of 1 to 4 available pace horizons (`7D`, `14D`, `21D`, `28D`), the `Weighted Average` row computes the exact `4:3:2:1` re-normalized 2-decimal weekly pace in both `kg` and `lbs`, and its projected weeks (`remainingWeight / velocity`) and arrival date reconcile at displayed precision.
- **SC-002**: The redesigned `GOAL ARRIVAL ESTIMATIONS` card displays the `Current Goal & Target` sub-header, the highlighted `Weighted Average` primary row above a divider, and the 4 historical rows (`7D`, `14D`, `21D`, `28D`) below it.
- **SC-003**: All elements in the redesigned `GOAL ARRIVAL ESTIMATIONS` card render with zero text clipping or layout overflow on a `360dp`-wide viewport at both `1.0f` and `1.3f` font scales, and all unit, script, and lint checks pass at 100%.

## Assumptions

- The `GOAL ARRIVAL ESTIMATIONS` card remains a display-only visual component within `WeightProgressionHeroCard` using the app's existing Material 3 theming and terminal state banners (`GoalReached`, `CycleEnded`, `NotEnoughData`).
- Linear recency weights `4 : 3 : 2 : 1` apply to `7D`, `14D`, `21D`, and `28D` respectively, and re-normalize over whichever horizons are non-null so athletes with 7–27 days of weigh-in history still benefit from the Weighted Average row.
