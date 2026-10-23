# Phase 0 Research: 21D/28D Pace Grid & Visual Multi-Horizon Goal Estimations Card

## Decision 1: 21-Day and 28-Day Rolling Pace Calculation Engine
- **Decision**: Reuse `FatLossAnalytics.computePaceRate(entries, windowDays, cycleStartDate, today, startingWeightKg)` with `windowDays = 21` (`compute21DayPace`) and `windowDays = 28` (`compute28DayPace`), and `computePaceComparisonVsLastWeek` with `windowDays = 21` and `windowDays = 28`.
- **Rationale**: `computePaceRate` is already parameterized by `windowDays`, comparing the current 7-day average `[t - 6d, t]` against the prior 7-day average `[t - windowDays - 6d, t - windowDays]` normalized to a 7-day weekly rate (`* 7.0 / elapsedDays`), with deterministic cold-start fallback to `startingWeightKg` when `cycleStartDate <= t - windowDays`.
- **Alternatives Considered**:
  - Single-day point-to-point differences (`W_t - W_{t-21}`): Rejected because single-day water fluctuations distort 3-week and 4-week trends; 7-day window averages at both endpoints match the 7D and 14D pace engine.

## Decision 2: 2-Decimal Precision for Pace Cards, Required Pace, and Multi-Horizon Estimations
- **Decision**: Add `PaceRateResult.formatDisplay2(unit)` (`%.2f kg/wk` / `%.2f lbs/wk`), `useTwoDecimals: Boolean = false` in `formatPaceSubtitle`, `requiredRate2InUnit` / `formatRate2()` in `RequiredPaceResult`, and 2-decimal rounded velocity `v2 = round2(toUnit(pace.deficitVelocityKgPerWeek, weightUnit))` in `computeMultiHorizonForecast`, while preserving 1-decimal `PaceRateResult.formatDisplay(unit)` and `computePaceForecast` for backward compatibility with existing unit tests.
- **Rationale**: Over 14D, 21D, and 28D windows, weekly rates frequently differ by `0.02–0.05 kg/wk` (e.g., `-0.24`, `-0.19`, `-0.16 kg/wk`), which all collapse to `-0.2 kg/wk` and identical week counts under 1-decimal rounding. Keeping `formatDisplay()` at 1-decimal default while adding `formatDisplay2()` (and `resolvePaceChipValue(..., useTwoDecimals = true)` for the 2×2 grid) ensures zero breakage of legacy tests while delivering full 2-decimal precision in the UI and new multi-horizon forecast.
- **Alternatives Considered**:
  - Keeping 1-decimal pace rates everywhere: Rejected during `/grill-me` because 14D, 21D, and 28D cards and projections would frequently display identical numbers.

## Decision 3: Visual Design of Multi-Horizon Goal Estimations Card
- **Decision**: Redraw `ForecastBanner` into a **Target Benchmark Header + 4-Row Comparative Visual Sufficiency Bar Card** (`GOAL ARRIVAL ESTIMATIONS`), where each of the 4 rows (`7D`, `14D`, `21D`, `28D`) uses a two-line layout:
  - **Line 1**: Left = `"${windowDays}D (${formattedPace2})"` (e.g., `"7D (-0.28 kg/wk)"`); Right = `"${weeks} wks · ${arrivalDate}"` (e.g., `"13.2 wks · 23 Jan 2027"`, `"Pace stalled"`, or `"Needs 28+ days"`).
  - **Line 2**: Left = proportional `LinearProgressIndicator` (`v2 / abs(requiredRate2InUnit)` clamped to `[0.05f, 1.0f]` when projected, `0f` when stalled/unavailable; colored `primary` when on track and `tertiary` when late); Right = ahead/late badge (`"On time (-0.6w)"`, `"On time"`, `"+3.8w late"`, `"> 1 yr late"`, or `"Stalled"`).
- **Rationale**: The two-line per-horizon structure prevents text truncation on `360dp` screens up to `130%` font scale while giving the athlete an immediate visual bar comparison across all 4 horizons against the cycle target date.
- **Alternatives Considered**:
  - Single-line 4-column table per row: Rejected because fitting horizon label, pace rate, bar, weeks, arrival date, and delta badge on a single horizontal line clips on `360dp` screens.
