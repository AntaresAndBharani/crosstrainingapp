# Research: Subcycle Taxonomy & Velocity Corridors (`SPEC-WEIGHT-SUBTYPE-SHEET-02` & `SPEC-WEIGHT-CARD-02`)

**Feature**: `017-subcycle-velocity-corridors` | **Date**: 2026-11-09

## Decision 1: `WeightSubcycleType` Taxonomy & Backward-Compatible Enum/Metadata Aliases

- **Decision**:
  Update `WeightSubcycleType` in `Enums.kt` to define the three physiological tissue-composition subcycles:
  - `FAT_LOSS(label = "Fat Loss", minWeeklyPct = -1.00, maxWeeklyPct = -0.25, description = "Controlled caloric deficit for sustainable fat loss while preserving lean tissue.")`
  - `RECOMPOSITION(label = "Recomposition", minWeeklyPct = -0.25, maxWeeklyPct = 0.25, description = "Near-maintenance velocity for simultaneous fat loss and lean muscle retention/gain.")`
  - `MUSCLE_GAIN(label = "Muscle Gain", minWeeklyPct = 0.25, maxWeeklyPct = 0.75, description = "Controlled lean mass accrual with bounded weekly surplus.")`
  Include companion aliases `val WEIGHT_LOSS = FAT_LOSS` and `val WEIGHT_GAIN = MUSCLE_GAIN`, plus a `fromToken(token: String)` parser that maps `"FAT_LOSS"` / `"WEIGHT_LOSS"` $\rightarrow$ `FAT_LOSS`, `"RECOMPOSITION"` $\rightarrow$ `RECOMPOSITION`, and `"MUSCLE_GAIN"` / `"WEIGHT_GAIN"` $\rightarrow$ `MUSCLE_GAIN`. Update `Cycle.SUBCYCLE_METADATA_REGEX` to match `FAT_LOSS|WEIGHT_LOSS|RECOMPOSITION|MUSCLE_GAIN|WEIGHT_GAIN`.
- **Rationale**:
  Aligns all user-visible card titles, selector pills, and diagnostic messages around tissue composition (`Fat Loss`, `Recomposition`, `Muscle Gain`) while ensuring 100% backward compatibility with existing Room SQLite rows containing `[subcycle=WEIGHT_LOSS]` or `[subcycle=WEIGHT_GAIN]` and with existing Kotlin/TypeScript callers.
- **Alternatives Considered**:
  - Keeping enum constant names `WEIGHT_LOSS`/`WEIGHT_GAIN` only: rejected because `SPEC-WEIGHT-SUBTYPE-SHEET-02` and the MCP payload schema specify `FAT_LOSS` and `MUSCLE_GAIN` as canonical enum codes.
  - Database migration rewriting `Cycle.goal`: unnecessary because `parseSubcycleMetadata` transparently normalizes legacy tags at read time.

## Decision 2: 5-Zone Universal Velocity Spectrum & Strict Inequality Diagnostic Engine

- **Decision**:
  Implement deterministic evaluation of weekly momentum ($\text{Mom}$) in `FatLossAnalytics.classifyCorridorCompliance` and `classifyMomentumZone`:
  1. **Calibration Gate ($K < 8$ days, `momentumPct == null`)**:
     - Status: `CorridorComplianceStatus.CALIBRATING`
     - Severity: `MomentumSeverity.NEUTRAL`
     - Badge: `"Calibrating (Day {K}/8)"`
     - Subtitle: `"Collecting initial 8-day baseline to calculate weekly momentum."`
  2. **Global Outer Bounds (Evaluated First for All Subcycles)**:
     - $\text{Mom} < -1.00\%$: `POSSIBLE_MUSCLE_LOSS`, `CRITICAL_RED`, badge `"Possible muscle loss"`, subtitle `"Losing faster than -1.00%/wk. High risk of lean muscle loss."`
     - $\text{Mom} > +0.75\%$: `SUBOPTIMAL_MUSCLE_GAIN`, `CRITICAL_RED`, badge `"Suboptimal muscle gain"`, subtitle `"Gaining faster than +0.75%/wk. Surplus exceeds optimal muscle synthesis rate."`
  3. **Subcycle-Specific Rules ($-1.00\% \le \text{Mom} \le +0.75\%$)**:
     - **`FAT_LOSS`**:
       - $-1.00\% \le \text{Mom} \le -0.25\% \implies$ `ON_TARGET`, `OPTIMAL_GREEN`, badge `"Optimal"`, subtitle `null`
       - $-0.25\% < \text{Mom} < 0.00\% \implies$ `SLOW_FAT_LOSS_PACE`, `WARNING_AMBER`, badge `"Slow fat loss pace"`, subtitle `"Deficit is below -0.25%/wk. Increase deficit slightly to hit target range."`
       - $0.00\% \le \text{Mom} \le +0.75\% \implies$ `OUT_OF_FAT_LOSS_RANGE`, `CRITICAL_RED`, badge `"Out of fat loss range"`, subtitle `"Weight is stable or increasing. You are outside a caloric deficit."`
     - **`RECOMPOSITION`**:
       - $-1.00\% \le \text{Mom} < -0.25\% \implies$ `WARNING_FAT_LOSS_PHASE`, `WARNING_AMBER`, badge `"Warning: Fat loss phase"`, subtitle `"Pace is below -0.25%/wk. Warning: You are currently in a fat loss phase."`
       - $-0.25\% \le \text{Mom} \le +0.25\% \implies$ `ON_TARGET`, `OPTIMAL_GREEN`, badge `"Optimal"`, subtitle `null`
       - $+0.25\% < \text{Mom} \le +0.75\% \implies$ `WARNING_MUSCLE_GAIN_PHASE`, `WARNING_AMBER`, badge `"Warning: Muscle gain phase"`, subtitle `"Pace exceeds +0.25%/wk. Warning: You are currently in a muscle gain phase."`
     - **`MUSCLE_GAIN`**:
       - $-1.00\% \le \text{Mom} \le 0.00\% \implies$ `OUT_OF_MUSCLE_GAIN_RANGE`, `CRITICAL_RED`, badge `"Out of muscle gain range"`, subtitle `"Weight is stable or dropping. Caloric surplus required for muscle gain."`
       - $0.00\% < \text{Mom} < +0.25\% \implies$ `SLOW_MUSCLE_GAIN_PACE`, `WARNING_AMBER`, badge `"Slow muscle gain pace"`, subtitle `"Surplus is below +0.25%/wk. Increase intake slightly to optimize growth."`
       - $+0.25\% \le \text{Mom} \le +0.75\% \implies$ `ON_TARGET`, `OPTIMAL_GREEN`, badge `"Optimal"`, subtitle `null`
- **Rationale**:
  Satisfies every mathematical inequality and copy requirement from `SPEC-WEIGHT-CARD-02` with inclusive active-subcycle boundaries at `-1.00%`, `-0.25%`, `+0.25%`, and `+0.75%`.
- **Alternatives Considered**:
  - Two-tier Recomposition check without outer-bound precedence: rejected because `< -1.00%` or `> +0.75%` during Recomposition must escalate to `Possible muscle loss` / `Suboptimal muscle gain`.

## Decision 3: Composite-Only Warning Architecture & 5-Segment Corridor Gauge

- **Decision**:
  In `CycleMomentumEstimationsCard` (`ProgressScreen.kt`):
  - Render the 5-Segment Corridor Gauge (`15% | 22.5% | 25% | 22.5% | 15%`), semantic status pill badge, and optional `↳ {diagnosticMessage}` helper subtitle **only** on the `✦ Weekly Body Weight` composite row.
  - Render `7D Avg`, `14D Avg`, `21D Avg`, and `28D Avg` rows as clean single-line metric summaries (`7D Avg: 74.7 kg (-0.15 kg/wk · -0.20%/wk)`) without per-row warning badges or gauge bars.
  - In `SubcycleTypePickerBottomSheet`, enforce single-line corridor pill badges (`maxLines = 1`, `softWrap = false`, `fontSize = 12.sp`, `fontFeatureSettings = "tnum"`, `horizontal = 8.dp, vertical = 4.dp`) and responsive vertical stacking when `fontScale >= 1.5f`.
- **Rationale**:
  Matches `SPEC-WEIGHT-CARD-02` and `SPEC-WEIGHT-SUBTYPE-SHEET-02`, preventing pill wrapping on `Recomposition` and eliminating redundant per-horizon visual noise.
- **Alternatives Considered**:
  - Retaining per-row badges on `7D`–`28D`: rejected by explicit stakeholder direction (`trigger solely from the Weekly Body Weight Composite`).
