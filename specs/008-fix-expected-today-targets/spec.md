# Feature Specification: 1-Day Rolling Window Step for Expected Today Scale Targets (7D, 14D, 21D, 28D)

**Feature Branch**: `[feat/008-fix-expected-today-targets]`

**Created**: 2026-10-28

**Status**: Ready

**Input**: User description: "I want you to review the following screenshots because I think there is a bug. In pace and linear targets the expected today for 14,21 and 28 don't look good. Suddently in 14 we can see a weight so low (and for 21 and 28 a higher weight and that doesn't make sense). This impacts in the moving averages. Could you pleae review and then, fix it?"

## User Scenarios & Testing *(mandatory)*

### User Story 1 - 1-Day Rolling Window Step from Yesterday's ND Average for Expected Today across 7D, 14D, 21D, and 28D (Priority: P1)

As an athlete tracking a Fat Loss & Bodybuilding cycle across `7D`, `14D`, `21D`, and `28D` rolling average horizons in `PACE & LINEAR TARGETS`, I want `Expected Today` in each `ND ROLLING AVERAGE` row to compute the exact daily scale weight needed today (`t`) so that today's `ND` rolling average (`[t - (N - 1), t]`) steps by **1 day of required pace** (`+ 1 × dailyRequiredRate`) from **yesterday's `ND` rolling average** (`[t - N, t - 1]`), matching the exact 1-day rolling window logic of `Target Tomorrow` (`today's ND rolling average + 1 × dailyRequiredRate`), so that multi-week cumulative drift is never multiplied by `N` (`14`, `21`, `28`) to produce implausibly low (`71.50 kg`) or implausibly high (`78.30 kg`, `94.52 kg`) expected scale weights.

**Why this priority**: Previously, `Expected Today` for window `N` projected `targetAvgToday` from `N` days ago (`priorNdAvg` over `[t - 2N + 1, t - N]` + `N × dailyRequiredRate`) and then back-solved for a single day's scale weight (`(k_today + 1) × targetAvgToday - sum([t - (N - 1), t - 1])`). Because the `N - 1` days in `[t - (N - 1), t - 1]` are already locked in and disjoint from `[t - 2N + 1, t - N]`, forcing today's single weigh-in to absorb `N` days of cumulative deviation between actual and required pace multiplied the average gap by `N` (scaling error as $N^2$: $\times 14 \to 71.50\text{ kg}$, $\times 21 \to 78.30\text{ kg}$, $\times 28 \to 94.52\text{ kg}$). Stepping 1 day from yesterday's `ND` rolling average (`[t - N, t - 1]`) ensures the `N - 1` overlapping days `[t - (N - 1), t - 1]` cancel out cleanly—just like `Target Tomorrow`—producing realistic, consistent daily scale targets across all four horizons.

**Independent Test**: Given a Fat Loss & Bodybuilding cycle with 35+ days of realistic fluctuating daily weigh-ins where the athlete lost weight faster early in the cycle (`78.0 kg -> 75.7 kg`) and slightly slower over the past 14 days, verify that `Expected Today` across `7D`, `14D`, `21D`, and `28D` produces realistic daily scale weights consistent with `Target Tomorrow` (within normal scale range around `73.5–75.5 kg`) rather than diverging to `71.50 kg` (`14D`) or `94.52 kg` (`28D`), and that substituting `Expected Today` as today's weigh-in makes today's `ND` rolling average equal yesterday's `ND` rolling average plus `1 × dailyRequiredRate` to within `0.01` display units.

**Acceptance Scenarios**:

1. **Given** an active Fat Loss & Bodybuilding cycle with horizon `N` in `{7, 14, 21, 28}` where at least one non-deleted weigh-in exists in the horizon maturity lookback window `[t - 2N + 1, t - N]`, **When** `Expected Today` is computed for row `N`, **Then**:
   - Yesterday's `ND` rolling average `yesterdayNdAvg` is computed over `[t - N, t - 1]` (falling back to the lookback average over `[t - 2N + 1, t - N]` only if `[t - N, t - 1]` is empty).
   - Today's target `ND` rolling average is `targetAvgToday = yesterdayNdAvg + dailyRequiredRate`.
   - `Expected Today` scale weight is `round2((k_today + 1) × targetAvgToday - sum(entries in [t - (N - 1), t - 1]))`.
   - Substituting the unrounded `Expected Today` scale weight at date `t` into `[t - (N - 1), t]` yields an `ND` rolling average equal to `yesterdayNdAvg + dailyRequiredRate`.
2. **Given** a 35-day weigh-in history matching the reported screenshot scenario (where early rapid loss followed by a 14-day slowdown previously produced `14D Expected Today = 71.50 kg`, `21D Expected Today = 78.30 kg`, and `28D Expected Today = 94.52 kg`), **When** `compute7DayPaceTargets` is evaluated, **Then** `Expected Today` for `7D`, `14D`, `21D`, and `28D` all remain within realistic daily scale bounds (`73.00 kg .. 76.00 kg`) and progress smoothly alongside `Target Tomorrow`.

---

### User Story 2 - Preservation of Horizon Maturity & Missing-Log Fallbacks (Priority: P2)

As an athlete early in a cycle or with sparse logging history, I want each `ND` row (`7D`, `14D`, `21D`, `28D`) to continue requiring horizon maturity at `t - N` (via `[t - 2N + 1, t - N]`) before displaying `Expected Today`, so that young cycles (e.g. Day 5 or Day 10) still display `"Needs 14 days history"`, `"Needs 21 days history"`, `"Needs 28 days history"`, or `"Needs log from <d MMM>"` instead of collapsing all four horizons onto the same short-term entries.

**Why this priority**: Without checking the `N`-day lookback window `[t - 2N + 1, t - N]`, querying `[t - N, t - 1]` on Day 3 of a cycle would find the 2 logs in `[t - 2, t - 1]` for all four windows (`7D`, `14D`, `21D`, `28D`) and show identical numbers instead of indicating that 14, 21, and 28 days of history have not been reached.

**Independent Test**: Evaluate a cycle on Day 10 (`cycleStartDate = t - 9d`) with daily weigh-ins from `t - 9d` to `t`, and verify that `7D ROLLING AVERAGE` resolves `Expected Today` as a numeric `ColumnState.Value`, while `14D`, `21D`, and `28D` emit `ColumnState.InsufficientHistory` (`"Needs 14 days history"`, `"Needs 21 days history"`, `"Needs 28 days history"`).

**Acceptance Scenarios**:

1. **Given** a cycle where `t - N < cycleStartDate` and no non-deleted weigh-ins exist in `[t - 2N + 1, t - N]`, **When** `Expected Today` is evaluated for row `N`, **Then** it emits `ColumnState.InsufficientHistory` (`"Needs N days history"`).
2. **Given** a cycle where `t - N >= cycleStartDate` and no non-deleted weigh-ins exist in `[t - 2N + 1, t - N]`, **When** `Expected Today` is evaluated for row `N`, **Then** it emits `ColumnState.NeedsLog(requiredDate = t - N)` (`"Needs log from <d MMM>"`).

---

### Edge Cases

- **Dense Window (`k_today == N - 1` and `t - N` logged)**: Yesterday's window `[t - N, t - 1]` has `N` entries (`W_{t - N}` plus the `N - 1` entries in `[t - (N - 1), t - 1]`), so `(k_today + 1) × (yesterdayNdAvg + dailyRequiredRate) - sumPriorToday` simplifies algebraically to `W_{t - N} + N × dailyRequiredRate`, matching `Target Tomorrow` (`W_{t - (N - 1)} + N × dailyRequiredRate`).
- **Skipped Log on Exact Anchor Day `t - N` (while earlier logs exist in `[t - 2N + 1, t - N - 1]` and logs exist in `[t - (N - 1), t - 1]`)**: `[t - N, t - 1]` averages the `k_today` entries in `[t - (N - 1), t - 1]`, so `(k_today + 1) × (yesterdayNdAvg + dailyRequiredRate) - sumPriorToday` evaluates to `yesterdayNdAvg + (k_today + 1) × dailyRequiredRate`, which still reproduces `yesterdayNdAvg + dailyRequiredRate` when averaged with the `k_today` prior entries.
- **No Logs in `[t - N, t - 1]` (while a log exists in `[t - 2N + 1, t - N - 1]`)**: `yesterdayNdAvg` falls back to `priorLookbackAvg` over `[t - 2N + 1, t - N]`, and since `k_today = 0` and `sumPriorToday = 0.0`, `Expected Today` evaluates cleanly to `round2(priorLookbackAvg + dailyRequiredRate)`.
- **Unlogged Today (`entryToday == null`)**: `Expected Today` still displays the 2-decimal target scale weight needed today, with `deltaDisplay = null` and `status = null`.
- **Imperial Unit (`lbs`) Conversion**: All entry weights, window averages, required rates, target averages, and solved scale weights are evaluated in the active display unit (`kg` or `lbs`) before 2-decimal rounding (`round2`) and `diff2` subtraction.

## Requirements *(mandatory)*

### Functional Requirements

- **FR-001**: For each horizon `N` in `HORIZON_WINDOWS = listOf(7, 14, 21, 28)`, the system MUST compute the current `ND` rolling average `todayNdAvgKg` over `[t - (N - 1), t]` (falling back to the latest active in-cycle weigh-in when empty) and the exact unrounded daily required pace in the active display unit:
  - `dailyRequiredRateInUnit = -(toUnit(todayNdAvgKg) - toUnit(targetWeightKg)) / daysRemaining`
- **FR-002**: For each horizon `N` in `{7, 14, 21, 28}`, the system MUST verify horizon maturity using `anchorDate = t - N` and `priorLookbackAvgKg = computeWindowAverageKg(allActiveEntries, t - 2N + 1, t - N)`:
  - If `priorLookbackAvgKg == null` and `anchorDate < cycleStartDate`, emit `ColumnState.InsufficientHistory`.
  - If `priorLookbackAvgKg == null` and `anchorDate >= cycleStartDate`, emit `ColumnState.NeedsLog(requiredDate = anchorDate)`.
- **FR-003**: When `priorLookbackAvgKg != null`, the system MUST compute `expectedToday` for horizon `N` using a **1-day rolling window step from yesterday's `ND` rolling average**:
  - Resolve yesterday's `ND` rolling average `yesterdayNdAvgKg = computeWindowAverageKg(allActiveEntries, t - N, t - 1) ?: priorLookbackAvgKg` and convert to the active display unit (`yesterdayNdInUnit = toUnit(yesterdayNdAvgKg, weightUnit)`).
  - Compute today's target `ND` rolling average as a single daily required-pace step from yesterday's `ND` rolling average:
    `targetAvgTodayInUnit = yesterdayNdInUnit + dailyRequiredRateInUnit`
  - Gather all non-deleted weigh-ins in the prior `(N - 1)` days of today's window `[t - (N - 1), t - 1]` (count `k_today`, sum in display unit `sumPriorTodayInUnit`), and solve for today's expected scale weight:
    `expectedScaleTodayDisplay = round2((k_today + 1) * targetAvgTodayInUnit - sumPriorTodayInUnit)`
  - Set `baselineWeightDisplay = round2(yesterdayNdInUnit)`.
  - When today (`t`) has a logged weigh-in (`entryToday != null`), compute `delta = diff2(round2(toUnit(entryToday.weightKg)), expectedScaleTodayDisplay)` and `status = paceStatusFor(delta)`; when today (`t`) is unlogged, set `deltaDisplay = null` and `status = null`.
- **FR-004**: For each horizon `N` in `{7, 14, 21, 28}`, `targetTomorrow` MUST remain symmetric with `expectedToday`, stepping 1 day from today's `ND` rolling average (`targetAvgTomorrowInUnit = currentNdInUnit + dailyRequiredRateInUnit`) and solving `round2((k_tomorrow + 1) * targetAvgTomorrowInUnit - sumPriorTomorrowInUnit)` over `[t - (N - 2), t]`.

### Key Entities

- **PaceTargetsState.Available**: Holds the four horizon `TargetRowState` rows (`7D`, `14D`, `21D`, `28D`).
- **TargetRowState**: Holds `windowDays`, `linearToday`, `expectedToday`, and `targetTomorrow`.
- **ColumnState.Value**: Holds the 2-decimal `targetWeightDisplay`, `deltaDisplay`, `status`, `unitLabel`, `baselineWeightDisplay`, `isDailyStep`, and `useTwoDecimals`.

## Success Criteria *(mandatory)*

### Measurable Outcomes

- **SC-001**: For every horizon `N` in `{7, 14, 21, 28}` with mature history, substituting the unrounded `Expected Today` scale weight at date `t` into today's `ND` window `[t - (N - 1), t]` produces an `ND` rolling average equal to `yesterdayNdAvg + dailyRequiredRate` to within `0.01` display units (`kg` or `lbs`).
- **SC-002**: On multi-week datasets where actual pace varied across weeks (such as the user-reported screenshot scenario), `Expected Today` for `14D`, `21D`, and `28D` exhibits zero $N^2$ cumulative-drift amplification (remaining within normal daily scale range alongside `Target Tomorrow` rather than jumping to `71.50 kg` or `94.52 kg`).
- **SC-003**: 100% of unit tests, script tests, and Compose UI tests pass with zero regressions.

## Assumptions

- Horizon maturity continues to require at least one non-deleted weigh-in in the lookback window `[t - 2N + 1, t - N]` so that `14D`, `21D`, and `28D` rows appropriately display `"Needs N days history"` or `"Needs log from <d MMM>"` until `N` days of lookback history exist.
- `Linear Today` and `Target Tomorrow` formulas remain unchanged, as `Target Tomorrow` already uses the 1-day rolling window step from today's `ND` average.
