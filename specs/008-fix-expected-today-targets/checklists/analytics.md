# Analytics Requirements Quality Checklist: Fix Expected Today Pace & Linear Targets (7D–28D)

**Purpose**: Validate the completeness, mathematical clarity, consistency, and edge-case coverage of the rolling window target requirements in `spec.md` and `plan.md`.
**Created**: 2026-10-28
**Feature**: [spec.md](file:///c:/Users/rogal/workspaces/ws-gym/crosstrainingapp/specs/008-fix-expected-today-targets/spec.md)

**Note**: Items marked `[x]` have passed the autonomous requirements-quality review against `spec.md` and `plan.md`.

## Requirement Completeness

- [x] CHK001 - Are all four rolling window horizons (`7D`, `14D`, `21D`, `28D`) explicitly listed in the scope of the `Expected Today` calculation requirements? [Completeness, Spec §FR-001]
- [x] CHK002 - Are both rate modes (`Pace` and `Linear`) explicitly covered with identical rolling window anchor rules? [Completeness, Spec §FR-003]

## Requirement Clarity

- [x] CHK003 - Is the exact date interval $[t - N, t - 1]$ for yesterday's $N$-day baseline rolling average unambiguously defined? [Clarity, Spec §FR-001]
- [x] CHK004 - Is the exact algebraic formula for back-solving today's expected scale weight (`round2((kToday + 1) * targetAvgTodayInUnit - sumPriorTodayInUnit)`) explicitly specified? [Clarity, Spec §FR-002]

## Requirement Consistency

- [x] CHK005 - Does the 1-day step for `Expected Today` ($[t - N, t - 1] + 1 \times \text{dailyRequiredRateInUnit}$) align symmetrically with the unchanged `Target Tomorrow` 1-day step ($[t - (N - 1), t] + 1 \times \text{dailyRequiredRateInUnit}$)? [Consistency, Spec §FR-002, §FR-005]
- [x] CHK006 - Is `Ready.baselineNdDisplay` consistently defined as `round2(yesterdayNdInUnit)` across `spec.md` and `data-model.md`? [Consistency, Spec §FR-002]

## Scenario & Edge Case Coverage

- [x] CHK007 - Are the boundary conditions for `WindowCalculationStatus.InsufficientHistory` (`earliestDate > t - N`) and `WindowCalculationStatus.NeedsLog(requiredDate = t - N)` (zero entries in $[t - 2N + 1, t - N]$) explicitly preserved? [Coverage, Spec §FR-004]
- [x] CHK008 - Is fallback behavior specified when $[t - N, t - 1]$ contains zero weigh-ins ($k_{\text{today}} = 0$ and no entry on $t - N$) while $[t - 2N + 1, t - N - 1]$ has entries? [Edge Case, Spec §Edge Cases]
- [x] CHK009 - Are unit conversion and rounding rules (`KG` vs `LB` prior to `round2`) explicitly specified to prevent precision drift? [Coverage, Spec §FR-006]

## Acceptance Criteria Quality

- [x] CHK010 - Are measurable verification criteria defined for the 35-day multi-horizon regression scenario and existing unit/UI test suites? [Measurability, Spec §SC-001, §SC-003]
