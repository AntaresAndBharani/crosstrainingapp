# Phase 0 Research: Fix Expected Today Pace & Linear Targets Across Rolling Windows (7D–28D)

## Decision 1: Anchor `Expected Today` to a 1-Day Step from Yesterday's `ND` Rolling Average

- **Decision**: Compute `targetAvgTodayInUnit` for horizon $N \in \{7, 14, 21, 28\}$ as `yesterdayNdInUnit + dailyRequiredRateInUnit`, where `yesterdayNdAvgKg = computeWindowAverageKg(allActiveEntries, anchorDate, referenceDate.minusDays(1L)) ?: priorLookbackAvgKg` over $[t - N, t - 1]$ (with `anchorDate = referenceDate.minusDays(windowDays.toLong())`). Then back-solve for today's expected scale weight using the entries in $[t - (N - 1), t - 1]$:
  $$\text{expectedScaleTodayDisplay} = \operatorname{round2}\!\left((k_{\text{today}} + 1) \cdot \text{targetAvgTodayInUnit} - \text{sumPriorTodayInUnit}\right)$$
- **Rationale**:
  - Previously, `Expected Today` anchored `targetAvgTodayInUnit` to `priorNdInUnit + N * dailyRequiredRateInUnit`, where `priorNdInUnit` was the mean of the disjoint prior $N$-day window $[t - 2N + 1, t - N]$, while `sumPriorTodayInUnit` summed entries in $[t - (N - 1), t - 1]$.
  - Because those two intervals share zero entries, any actual weight-loss pace difference $\Delta$ between $[t - 2N + 1, t - N]$ and $[t - (N - 1), t - 1]$ was multiplied by $k_{\text{today}} + 1 \approx N$ and forced onto a single day's scale weigh-in. In the reported user screenshots, this produced severe $O(N^2)$ oscillations (`71.50 kg` at `14D`, `78.30 kg` at `21D`, and `94.52 kg` at `28D`).
  - Anchoring to yesterday's `ND` rolling average $[t - N, t - 1]$ plus $1 \times \text{dailyRequiredRateInUnit}$ shares $[t - (N - 1), t - 1]$ between yesterday's window and today's window, matching the exact 1-day rolling window transition already used by `Target Tomorrow` ($[t - (N - 1), t] + 1 \times \text{dailyRequiredRateInUnit}$).
  - Under sparse logging where $[t - N + 1, t - 1]$ has no entries ($k_{\text{today}} = 0$) and only $[t - 2N + 1, t - N]$ has entries, `computeWindowAverageKg(allActiveEntries, anchorDate, referenceDate.minusDays(1L))` gracefully falls back to `priorLookbackAvgKg` when `anchorDate` has no entry, ensuring non-null arithmetic when `priorLookbackAvgKg != null`.
- **Alternatives Considered**:
  - *Alternative A (Keep $[t - 2N + 1, t - N]$ anchor and display target rolling average instead of back-solved scale weight)*: Rejected by user preference during Phase 1 discovery (`ask_question`) and because `Expected Today` and `Target Tomorrow` are explicitly designed to show actionable scale weights rather than abstract moving averages.
  - *Alternative B (Linearly interpolate daily scale weight from $[t - 2N + 1, t - N]$ without solving against $[t - (N - 1), t - 1]$)*: Rejected because it ignores the actual weigh-ins recorded in the current window $[t - (N - 1), t - 1]$ and breaks parity with `Target Tomorrow`.

## Decision 2: Preserve `[t - 2N + 1, t - N]` Prior Window Maturity Guard

- **Decision**: Retain the existing check on `priorLookbackAvgKg = computeWindowAverageKg(allActiveEntries, priorWindowStart, anchorDate)` over $[t - 2N + 1, t - N]$ (and `allActiveEntries.minOf { it.date }`) before computing `Ready` targets.
- **Rationale**:
  - A rolling window of horizon $N$ (`14D`, `21D`, `28D`) requires historical data reaching back into $[t - 2N + 1, t - N]$ to be considered a distinct, mature $N$-day horizon rather than duplicating a shorter window's sparse history.
  - Keeping this maturity guard preserves all existing `InsufficientHistory` (when earliest log $> t - N$) and `NeedsLog(requiredDate = t - N)` (when earliest log $\le t - N$ but $[t - 2N + 1, t - N]$ has zero entries) behaviors without regression.
- **Alternatives Considered**:
  - *Remove $[t - 2N + 1, t - N]$ maturity check*: Rejected because users with only 10 days of logs would see `Ready` targets for `14D`, `21D`, and `28D` even though they lack the historical span for those horizons.

## Decision 3: Update Unit & Compose Instrumentation Test Expectations

- **Decision**: Update unit tests in `FatLossAnalyticsTest.kt` and the Compose instrumentation test in `WeightProgressionCardComposeTest.kt` to reflect the 1-day step formula, and add a new multi-horizon regression test reproducing the 35-day screenshot scenario.
- **Rationale**:
  - Existing tests asserted the old `N * dailyRequiredRateInUnit` jump from `[t - 2N + 1, t - N]`. Updating them to assert the 1-day step from yesterday's `ND` window (`[t - N, t - 1] + dailyRequiredRateInUnit`) plus adding a dedicated 4-horizon (`7D`, `14D`, `21D`, `28D`) regression test locks in the fix and prevents future regressions.
- **Alternatives Considered**: None.
