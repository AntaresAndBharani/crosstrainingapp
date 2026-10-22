# Phase 0 Research: Required-Pace & Linear Cycle Weight Targets

## 1. Unified Required Pace Authority vs. Historical Extrapolation
- **Decision**: Compute `Expected Today` and `Target Tomorrow` using the **exact unrounded Required Pace** from today's 7D Rolling Average (`today7dAvgKg`) to `targetWeightKg` over the remaining calendar days until `cycle.endDate` (`dailyRequiredRate = -(today7dAvg - targetWeight) / daysRemaining`, `weeklyRequiredRate = dailyRequiredRate * 7.0`).
- **Rationale**: The legacy `compute7DayPaceTargets` extrapolated the historical rate between `startKg` and `t - 7d` / `t - 6d`, which caused `Target Tomorrow` to jump above today's weight whenever `t - 6d` was a high fluctuation day, and locked into `Stalled` whenever `t - 7d >= startKg`. Using the unified Required Pace ensures `Expected Today` (`W_{t-7d} + weeklyRequiredRate`) and `Target Tomorrow` (`W_today + dailyRequiredRate`) always reflect the pace needed to reach the goal weight by `endDate`.
- **Alternatives Considered**:
  - Computing separate required paces for Daily Weight and 7D Average: Rejected because the athlete wants all targets to reconcile with the unified Required Pace concept displayed in the Forecast Banner.
  - Using the 1-decimal rounded banner rate (`-0.4 kg/wk`): Rejected in favor of the exact unrounded required rate rounded at the final 2-decimal target weight so daily steps (`remaining / daysRemaining`) do not accumulate intermediate rounding error.

## 2. Two-Decimal Display Precision (`%.2f`)
- **Decision**: Round and format all values, deltas, and daily steps inside the `PACE & LINEAR TARGETS` container to **2 decimal places** (`round2`, `diff2`, `%.2f`).
- **Rationale**: A typical weekly required pace of `-0.1` to `-0.4 kg/wk` corresponds to a daily step of `-0.014` to `-0.057 kg/day`. At 1-decimal precision, `round1(74.7 - 0.043)` rounds back to `74.7 kg` (`0.0 kg` change). Displaying 2 decimal places (`74.64 kg`, `-0.06 kg/day`) makes every daily step visible.
- **Alternatives Considered**:
  - Enforcing an artificial `-0.1 kg` minimum daily step at 1-decimal precision: Rejected by the user during `/grill-me` in favor of true 2-decimal precision.

## 3. Dual-Track 3-Column × 2-Row Container Layout
- **Decision**: Replace the 2-column `7-DAY PACE TARGETS` box with a unified `"PACE & LINEAR TARGETS"` container featuring two rows (`"7D ROLLING AVERAGE"` and `"DAILY WEIGH-IN"`) and three columns (`Linear Today`, `Expected Today`, `Target Tomorrow`).
- **Rationale**: Allows the athlete to see side-by-side how both their smoothed 7-day rolling average and their daily scale weight compare against the start-to-end linear trajectory (`Linear Today`), the 7-day required-pace benchmark (`Expected Today`), and tomorrow's required step (`Target Tomorrow`).
- **Alternatives Considered**:
  - Separate cards for Linear Target and Pace Targets: Rejected to keep the `WeightProgressionHeroCard` compact and scannable in a single cohesive target matrix.
