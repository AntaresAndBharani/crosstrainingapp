# Research & Architectural Decisions: Universal Cycle Weight Tracking & Momentum Engine

**Feature**: `016-cycle-weight-momentum`
**Date**: 2026-11-09

## Decision 1: Backward-Compatible Persistence of `WeightSubcycleType` on `Cycle`

- **Decision**: Encode the cycle's `WeightSubcycleType` (`WEIGHT_LOSS`, `RECOMPOSITION`, `WEIGHT_GAIN`) inside `Cycle.goal` using the established metadata header pattern (`[subcycle=WEIGHT_LOSS|RECOMPOSITION|WEIGHT_GAIN]`) with `@Ignore` properties (`subcycleType`, `cleanGoal`, `withSubcycleType(...)`) on `Cycle`, identical to `CycleGoal.encodeMetadata` / `CycleGoal.parseMetadata` (`[metric=...,window=...]`).
- **Rationale**:
  - Constitution Principle V ("Simplicity & Backward-Compatible Persistence") mandates: *"Database schema modifications require explicit migrations; features that can be derived from existing entities MUST avoid unnecessary schema changes."*
  - `RoomSchemaExportTest` explicitly asserts `AppDatabase` is at `version = 10` (`appDatabaseSource_hasExportSchemaEnabledAtVersion10`).
  - Storing `[subcycle=...]` in `Cycle.goal` requires zero Room schema changes, preserves 100% compatibility with existing Room backups and `10.json` schema verification, and provides smart fallback inference when no explicit header is present (`WEIGHT_LOSS` when `targetWeightKg != null && startingWeightKg != null && targetWeightKg < startingWeightKg` or when `type == CycleType.FAT_LOSS_BODYBUILDING` without a gain target; `WEIGHT_GAIN` when `targetWeightKg != null && startingWeightKg != null && targetWeightKg > startingWeightKg`; and `RECOMPOSITION` otherwise).
- **Alternatives Considered**:
  - *Bumping Room `AppDatabase` to version 11 with a new `subcycleType` column*: Rejected because it violates Constitution Principle V and would require modifying `RoomSchemaExportTest`'s version 10 baseline assertion for a single enum tag that fits cleanly in `Cycle.goal` metadata.

## Decision 2: Deterministic Domain Derivation of `REAL` vs. `THEORETICAL` Daily Weights

- **Decision**: Persist athlete-logged scale measurements as `WeightEntry` rows (`REAL` measurements) in `weight_entries`, and deterministically generate the continuous daily timeline (`List<DailyWeightPoint>`) in the domain analytics layer (`FatLossAnalytics` / `WeightAnalytics`). Editing any `THEORETICAL` day in the UI invokes `saveWeightEntry(weightKg, date)`, inserting a `REAL` `WeightEntry` on that date and automatically triggering reactive re-interpolation between the nearest surrounding `REAL` boundaries.
- **Rationale**:
  - Guarantees O(N) reactive recalculation without risking stale materialized `THEORETICAL` rows in SQLite or polluting Cloud Firestore `weight_entries` sync payloads with synthetic rows.
  - Deleting a `REAL` weight entry soft-deletes the row in `weight_entries`, which automatically causes the domain timeline builder to re-interpolate across the wider `(d_prev, d_next)` span or truncate an unanchored boundary.
  - Both the Android app and the TypeScript Coaching MCP Server (`mcp-server/`) run the exact same deterministic interpolation algorithm over active `weight_entries`.
- **Alternatives Considered**:
  - *Materializing `THEORETICAL` rows into `weight_entries` SQLite table*: Rejected because every edit/delete would require multi-row transactional deletes and re-inserts, and would sync synthetic rows to Firestore where LWW timestamps could conflict with real scale logs.

## Decision 3: Multi-Horizon Moving Averages (`7D`, `14D`, `21D`, `28D`), `3:4:2:1` Composite, and Momentum Engine

- **Decision**: Add pure domain functions in `FatLossAnalytics` (`interpolateDailyWeightTimeline`, `computeDailyHorizonSnapshot`, `computeCycleMomentumState`) while preserving all existing functions (`computeMultiHorizonForecast`, `compute7DayPace`, etc.) untouched so existing unit tests have zero regressions.
- **Rationale**:
  - The new specification defines:
    - Continuous daily timeline $W(d)$ with linear interpolation between `REAL` boundaries (and flat hold from the last `REAL` entry up to `referenceDate` if `referenceDate > lastRealDate`).
    - Cumulative cold-start / historical lookback averages: on day $t$ at index $K$ (1-based from the earliest `REAL` entry), $\text{Avg}_{ND}(t) = \frac{1}{\min(K, N)} \sum_{i=0}^{\min(K, N)-1} W(t - i)$ for $N \in \{7, 14, 21, 28\}$.
    - Weekly Body Weight composite (`3:4:2:1`):
      $$\text{BW}_{\text{weekly}}(t) = \frac{3 \cdot \text{Avg}_{7D}(t) + 4 \cdot \text{Avg}_{14D}(t) + 2 \cdot \text{Avg}_{21D}(t) + 1 \cdot \text{Avg}_{28D}(t)}{10}$$
    - Weekly Momentum (`%/wk`) and Weekly Pace (`kg/wk` or `lbs/wk`) comparing $t$ vs $t - 7$ (requiring $K \ge 8$ calendar days; when $K < 8$, `isCalibrating = true` with `calibrationDay = K` of `8`).
    - Compounded finish weight range over remaining weeks $W_{\text{left}} = \max(0.0, \text{daysRemaining} / 7.0)$:
      $$\text{BW}_{\text{target\_min}} = \text{BW}_{\text{weekly}} \times \left(1 + \frac{\text{Min}\%}{100}\right)^{W_{\text{left}}}, \quad \text{BW}_{\text{target\_max}} = \text{BW}_{\text{weekly}} \times \left(1 + \frac{\text{Max}\%}{100}\right)^{W_{\text{left}}}$$
  - Keeping legacy `computeMultiHorizonForecast` (which uses `4:3:2:1` pace blending for legacy tests) alongside the new `computeCycleMomentumState` (`3:4:2:1` Weekly Body Weight & Corridor Engine) satisfies Constitution Principle III (Test-First & Zero CI Regression).

## Decision 4: 3-Zone Corridor Range Gauge Normalization (`SPEC-WEIGHT-CARD-01`)

- **Decision**: Map any weekly percentage momentum $m$ (`%/wk`) onto the 3-zone segmented gauge track (`[0.0f, 1.0f]`):
  - Zone 1 (`[0.00f, 0.25f]`): Below Target Corridor (`m < Min%`, anchored down to `Min% - corridorWidth` or `-2.00%`).
  - Zone 2 (`[0.25f, 0.75f]`): Active Subtype Target Corridor (`Min% <= m <= Max%`, linearly mapped to `0.25f..0.75f`).
  - Zone 3 (`[0.75f, 1.00f]`): Above Target Corridor (`m > Max%`, anchored up to `Max% + corridorWidth` or `+2.00%`).
- **Rationale**: Matches `SPEC-WEIGHT-CARD-01` Section 3.3 (`25%` Left Zone / `50%` Center Optimal Corridor / `25%` Right Zone) and provides an intuitive, bounded visual indicator dot/bar across all three subcycle types.
