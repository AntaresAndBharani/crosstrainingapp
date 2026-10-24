# Phase 0 Research: Multi-Window Rolling Average Scale Targets (7D, 14D, 21D, 28D)

## Decision 1: Solving for Daily Scale Weight from Target Rolling Average (`Expected Today` & `Target Tomorrow`)

- **Decision**:
  For each rolling horizon $N \in \{7, 14, 21, 28\}$:
  - **Today's Target $N\text{D}$ Average**:
    $$\text{TargetAvg}_{ND}(t) = \text{toUnit}(\text{AVG}_{ND}(t - N)) + N \times r_{\text{daily}, ND}$$
    where $\text{AVG}_{ND}(t - N)$ is the arithmetic mean of non-deleted weigh-ins in $[t - 2N + 1, t - N]$ and $r_{\text{daily}, ND} = -\frac{\text{toUnit}(\text{AVG}_{ND}(t)) - \text{toUnit}(W_{\text{target}})}{\text{daysRemaining}}$.
  - **Expected Today Scale Weight**:
    Let $E_{\text{today}}$ be the non-deleted weigh-ins in the prior $(N - 1)$ days of today's window $[t - (N - 1), t - 1]$, with count $k_{\text{today}} = |E_{\text{today}}|$ and display-unit sum $S_{\text{today}} = \sum_{e \in E_{\text{today}}} \text{toUnit}(W_e)$.
    $$W_{\text{expected\_today}, ND} = \text{round2}\Big((k_{\text{today}} + 1) \times \text{TargetAvg}_{ND}(t) - S_{\text{today}}\Big)$$
  - **Tomorrow's Target $N\text{D}$ Average**:
    $$\text{TargetAvg}_{ND}(t + 1) = \text{toUnit}(\text{AVG}_{ND}(t)) + r_{\text{daily}, ND}$$
  - **Target Tomorrow Scale Weight**:
    Let $E_{\text{tomorrow}}$ be the non-deleted weigh-ins in the prior $(N - 1)$ days of tomorrow's window $[t - (N - 2), t]$, with count $k_{\text{tomorrow}} = |E_{\text{tomorrow}}|$ and display-unit sum $S_{\text{tomorrow}} = \sum_{e \in E_{\text{tomorrow}}} \text{toUnit}(W_e)$.
    $$W_{\text{target\_tomorrow}, ND} = \text{round2}\Big((k_{\text{tomorrow}} + 1) \times \text{TargetAvg}_{ND}(t + 1) - S_{\text{tomorrow}}\Big)$$
- **Rationale**:
  By solving the exact window mean equation $\frac{S + W_{\text{scale}}}{k + 1} = \text{TargetAvg}$, substituting $W_{\text{scale}}$ back into the window reproduces $\text{TargetAvg}$ identically, whether all $N$ days are logged ($k = N - 1 \implies k + 1 = N$) or some days in the window were skipped ($0 \le k < N - 1$).
- **Alternatives Considered**:
  - Displaying $\text{TargetAvg}_{ND}(t)$ and $\text{TargetAvg}_{ND}(t + 1)$ directly as `Expected Today` and `Target Tomorrow`: Rejected because the athlete steps on the scale to measure a daily weight, not a rolling average; showing a rolling average (`75.45 kg`) when today's weight is `73.9 kg` was misleading.
  - Assuming $k = N - 1$ unconditionally ($N \times \text{TargetAvg} - S$): Rejected because if a weigh-in was missed inside the window, $N \times \text{TargetAvg} - S$ would overestimate $W_{\text{scale}}$ by $(N - 1 - k) \times \text{TargetAvg}$.

## Decision 2: Replacing the Redundant `DAILY WEIGH-IN` Target Row with `7D`, `14D`, `21D`, and `28D` Rolling Average Rows

- **Decision**:
  Remove the `DAILY WEIGH-IN` row from `TheoreticalPaceTargetsBox` and render four horizon rows (`7D ROLLING AVERAGE`, `14D ROLLING AVERAGE`, `21D ROLLING AVERAGE`, `28D ROLLING AVERAGE`) driven by `HORIZON_WINDOWS = listOf(7, 14, 21, 28)`.
- **Rationale**:
  Because `Expected Today` and `Target Tomorrow` in each rolling average row now directly provide the daily scale weights required today and tomorrow to keep that rolling average on pace, a separate `DAILY WEIGH-IN` row inside `PACE & LINEAR TARGETS` is redundant. Expanding to `7D`, `14D`, `21D`, and `28D` aligns the container with the 4-horizon pace grid and `GOAL ARRIVAL ESTIMATIONS`.
- **Alternatives Considered**:
  - Keeping a 5th `DAILY WEIGH-IN` row: Rejected by the user in favor of replacing it with the `14D`, `21D`, and `28D` rolling average rows.

## Decision 3: Backward-Compatible `InsufficientHistory` & Horizon-Specific Subtitles

- **Decision**:
  Keep `ColumnState.InsufficientHistory` as a `data object` on `ColumnState`, add `val windowDays: Int = 7` to `TargetRowState`, and add `windowDays: Int = 7` (defaulting to `7`) to `resolveExpectedTodaySubtitle`, `resolveExpectedTodayContentDescription`, `resolveTargetTomorrowSubtitle`, and `resolveTargetTomorrowContentDescription`.
- **Rationale**:
  Preserves 100% source and binary compatibility with existing unit tests that reference `ColumnState.InsufficientHistory` as an object while rendering `"Needs 7 days history"`, `"Needs 14 days history"`, `"Needs 21 days history"`, and `"Needs 28 days history"` for each horizon row.
- **Alternatives Considered**:
  - Converting `ColumnState.InsufficientHistory` from `data object` to `data class`: Would require changing legacy test assertions that use `ColumnState.InsufficientHistory` without constructor parentheses.
