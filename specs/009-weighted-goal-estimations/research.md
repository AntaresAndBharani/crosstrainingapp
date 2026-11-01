# Research: Weighted Average Pace Estimation & Goal Arrival Estimations Redesign

**Feature**: `009-weighted-goal-estimations`
**Date**: 2026-11-01

## 1. Linear Recency Weighting (`4:3:2:1`) & Partial Horizon Re-Normalization

- **Decision**: Assign linear recency weights `HORIZON_WEIGHTS = mapOf(7 to 4, 14 to 3, 21 to 2, 28 to 1)` (40% 7D, 30% 14D, 20% 21D, 10% 28D when all 4 horizons are available). When only a subset of horizons has non-null `PaceRateResult` values (e.g., 7–27 days into a cycle with no pre-cycle history), dynamically re-normalize over the available horizons:
  $$\text{weightedRate2InUnit} = \text{round2}\left(\frac{\sum_{w \in \text{available}} \text{weight}(w) \cdot \text{round2}(\text{toUnit}(\text{pace}_w.\text{rateKgPerWeek}, \text{unit}))}{\sum_{w \in \text{available}} \text{weight}(w)}\right)$$
- **Rationale**: Using the 2-decimal display-unit rate (`round2(toUnit(pace.rateKgPerWeek, unit))`) of each available horizon satisfies Constitution Principle II (Display-Unit Arithmetic Consistency) so the athlete can verify the weighted average directly from the displayed `7D`, `14D`, `21D`, and `28D` rates. Dynamic re-normalization ensures the primary `Weighted Average` row is active as soon as `7D` pace unlocks (whenever `MultiHorizonForecastState.Active` is returned), avoiding an empty placeholder row for the first 28 days of a cycle.
- **Alternatives Considered**:
  - *Inverse-window weights (`12:6:4:3`)*: Over-weights `7D` at 48%, making the headline estimate too sensitive to single-week water fluctuations.
  - *Requiring all 4 horizons (`28+ days`)*: Leaves the primary headline row disabled for the first 4 weeks of a cut.

## 2. Signed Blending & Stalled Threshold for Weighted Average

- **Decision**: Include all non-null horizons in the weighted sum using their signed weekly rates (negative for loss, positive for gain). Format `paceDisplay2` from the signed `weightedRate2InUnit` using the same noise deadband threshold (`0.05` in `kg/wk` equivalent, i.e., `abs(weightedRateKgPerWeek) <= 0.05` formats as `"0.00 kg/wk"`). If `weightedRateKgPerWeek < -0.05` and `weightedVelocity2InUnit = -weightedRate2InUnit > 0.00`, the `Weighted Average` row enters `HorizonRowState.Projected`; otherwise it enters `HorizonRowState.Stalled`.
- **Rationale**: In real-world weight tracking, a temporary 7D refeed/water spike (`+0.10 kg/wk`) alongside steady 14D/21D/28D deficits (`-0.35 kg/wk`) should dampen—not erase—the overall trajectory unless the weighted average itself stalls.
- **Alternatives Considered**:
  - *Zeroing out or dropping stalled horizons*: Would artificially inflate the deficit velocity during a week where the athlete actually gained weight.

## 3. Data Model Backward Compatibility (`MultiHorizonForecastState.Active`)

- **Decision**: Keep `val rows: List<HorizonEstimationRow>` on `MultiHorizonForecastState.Active` containing the 4 historical horizon rows (`7`, `14`, `21`, `28`), and add `val weightedAverageRow: HorizonEstimationRow` (with `windowDays = 0` and `isWeightedAverage = true`) as a first-class property on `MultiHorizonForecastState.Active` (with a default synthesized fallback in the constructor so direct test instantiations of `MultiHorizonForecastState.Active` remain source-compatible).
- **Rationale**: Existing unit tests in `FatLossAnalyticsTest` assert `assertEquals(4, active.rows.size)` and index `active.rows[0..3]` for `7D`, `14D`, `21D`, and `28D`. Separating `weightedAverageRow` as the primary headline model while keeping `rows` as the 4 historical breakdown rows matches both the visual hierarchy (primary Weighted Average section → divider → 4 historical breakdown rows) and 100% of existing unit test assertions.
- **Alternatives Considered**:
  - *Prepending Weighted Average into `active.rows` (making `rows.size == 5`)*: Rejected because it shifts indices `0..3` in existing unit tests and conflates the primary headline row with the historical lookback breakdown list.

## 4. Visual Redesign & Responsive Accessibility (`MultiHorizonGoalEstimationsBanner`)

- **Decision**: Update `MultiHorizonGoalEstimationsBanner` (`Active` branch) in `ProgressScreen.kt` to match the shared UI specification:
  1. Overline header: `GOAL ARRIVAL ESTIMATIONS` (`labelSmall`, uppercase, `letterSpacing = 1.sp`, muted `onSurfaceVariant`).
  2. Sub-header title: `Current Goal & Target` (`titleSmall`, `FontWeight.Bold`, `onSurface`) directly above the benchmark line (`Target: 28 Dec 2026 (8.1w left) · Required: -0.38 kg/wk` in `bodySmall`, `FontWeight.SemiBold`).
  3. Subtle `HorizontalDivider`.
  4. Primary `WeightedAverageEstimationRowItem(row = state.weightedAverageRow)` featuring:
     - A leading trend/sparkle icon (`✦` / `📈` vector or symbol) + bold `"Weighted Average"` title and inline/wrapped detail text (`"(Weighted pace of -0.39 kg/wk · 7.9 wks · 26 Dec 2026)"`), allowing up to 2 lines so `360dp` viewports at `1.3f` font scale never clip.
     - A sleek pill-shaped progress track (`height = 7.dp`, `RoundedCornerShape(50)`) with a glowing/distinct teal-green gradient/tint when on track (`Color(0xFF10B981)` / `Color(0xFF0D9488)`) or amber/orange-brown (`MaterialTheme.colorScheme.tertiary`) when late, paired with its right-aligned status badge (`"On time (-0.3w)"`, `"+0.8w late"`, `"Stalled"`).
  5. A subtle `HorizontalDivider` separating the primary `Weighted Average` row from the 4 historical breakdown rows (`7D`, `14D`, `21D`, `28D`).
  6. Refined `HorizonEstimationRowItem` rows (`7D`, `14D`, `21D`, `28D`) with pill-shaped progress tracks and high-contrast status labels.
- **Rationale**: Faithfully implements the visual redesign from the shared specification while respecting Material 3 light/dark themes and `360dp` / `1.3f` accessibility scaling.
