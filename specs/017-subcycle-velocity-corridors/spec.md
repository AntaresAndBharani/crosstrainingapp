# Feature Specification: Subcycle Taxonomy & Velocity Corridors (`SPEC-WEIGHT-SUBTYPE-SHEET-02` & `SPEC-WEIGHT-CARD-02`)

**Feature Branch**: `feat/017-subcycle-velocity-corridors`

**Created**: 2026-11-09

**Status**: Approved

**Input**: User description: "Implement Subcycle Taxonomy and Velocity Corridors (`SPEC-WEIGHT-SUBTYPE-SHEET-02` and `SPEC-WEIGHT-CARD-02`) as described in https://share.gemini.google/r5VfNfYNi3yy"

## User Scenarios & Testing *(mandatory)*

### User Story 1 - Updated Subcycle Taxonomy & Tightened Physiological Velocity Corridors (`SPEC-WEIGHT-SUBTYPE-SHEET-02`) (Priority: P1)

Athletes and coaches configuring or switching a training cycle's subcycle regime use tissue-composition-aligned subcycle names and tightened physiological weekly velocity corridors: **Fat Loss** (`-1.00%` to `-0.25%/wk`), **Recomposition** (`-0.25%` to `+0.25%/wk`), and **Muscle Gain** (`+0.25%` to `+0.75%/wk`). In the `Select Subcycle Type` modal bottom sheet, each option card displays the updated subcycle title, a single-line non-wrapping target percentage pill badge (`-1.00% to -0.25%/wk`, `-0.25% to +0.25%/wk`, `+0.25% to +0.75%/wk`), description, and dynamically compounded projected finish weight window (`Min – Max`).

**Why this priority**: Establishes the core physiological velocity corridors and taxonomy (`Fat Loss`, `Recomposition`, `Muscle Gain`) that govern all finish weight projections, diagnostic evaluations, and user selections across the application.

**Independent Test**: Can be independently tested by opening the `Select Subcycle Type` bottom sheet or cycle editor, verifying the three subcycle options (`Fat Loss` at `-1.00% to -0.25%/wk`, `Recomposition` at `-0.25% to +0.25%/wk`, `Muscle Gain` at `+0.25% to +0.75%/wk`), confirming that corridor badges render on a single line without wrapping `wk` onto a second line, and verifying compounded finish weight projections.

**Acceptance Scenarios**:

1. **Given** an athlete opens the `Select Subcycle Type` bottom sheet for an active cycle with $\text{BW}_{\text{weekly}} = 74.9\text{ kg}$ and $7.0$ weeks remaining (`Ends 28 Dec`), **When** the three option cards are rendered, **Then** they display:
   - `Fat Loss` with single-line badge `-1.00% to -0.25%/wk` and `Projected Finish (28 Dec): 69.8 kg – 73.6 kg`
   - `Recomposition` with single-line badge `-0.25% to +0.25%/wk` and `Projected Finish (28 Dec): 73.6 kg – 76.2 kg`
   - `Muscle Gain` with single-line badge `+0.25% to +0.75%/wk` and `Projected Finish (28 Dec): 76.2 kg – 78.9 kg`
2. **Given** the `Recomposition` option card has a longer title than the other options, **When** rendered on standard mobile viewports (`360dp` width), **Then** the corridor pill badge (`-0.25% to +0.25%/wk`) enforces single-line rendering (`maxLines = 1`, `softWrap = false`, compact `8dp` horizontal padding, `12sp` `tabular-nums`) so `/wk` never wraps onto a second line, and stacks cleanly if system font scaling is $\ge 1.5\times$.
3. **Given** existing cycles stored with legacy `[subcycle=WEIGHT_LOSS]` or `[subcycle=WEIGHT_GAIN]` metadata tags, **When** loaded by the application, **Then** they map seamlessly to `Fat Loss` (`FAT_LOSS`) and `Muscle Gain` (`MUSCLE_GAIN`) without data loss or migration errors.

---

### User Story 2 - 5-Zone Universal Velocity Spectrum & Strict Inequality Diagnostic Engine (`SPEC-WEIGHT-CARD-02`) (Priority: P1)

When evaluating the athlete's calibrated Weekly Body Weight composite momentum ($\text{Mom}$), the system applies a deterministic 5-Zone Universal Velocity Spectrum with strict mathematical inequalities so shared boundaries (`-0.25%` and `+0.25%`) are inclusive for the active subcycle, extreme outer zones (`< -1.00%/wk` and `> +0.75%/wk`) always trigger critical physiological warnings (`Possible muscle loss` and `Suboptimal muscle gain`), Recomposition drift into `[-1.00%, -0.25%)` or `(+0.25%, +0.75%]` warns that the athlete is in a `Fat loss phase` or `Muscle gain phase`, and under-pace / wrong-direction states in `Fat Loss` and `Muscle Gain` distinguish between slow progress (`Slow fat loss pace` / `Slow muscle gain pace`) and off-goal direction (`Out of fat loss range` / `Out of muscle gain range`).

**Why this priority**: Directly implements the stakeholder's diagnostic rules and phase-drift warnings with zero ambiguity at shared boundary values (`-1.00%`, `-0.25%`, `0.00%`, `+0.25%`, `+0.75%`).

**Independent Test**: Can be independently tested by evaluating every boundary and interior value (`-1.25%`, `-1.00%`, `-0.50%`, `-0.25%`, `-0.15%`, `0.00%`, `+0.15%`, `+0.25%`, `+0.50%`, `+0.75%`, `+0.92%`) across all three active subcycle types (`Fat Loss`, `Recomposition`, `Muscle Gain`) and verifying the exact badge label, diagnostic code, severity tier (`OPTIMAL_GREEN`, `WARNING_AMBER`, `CRITICAL_RED`), and inline helper subtitle.

**Acceptance Scenarios**:

1. **Given** any active subcycle (`Fat Loss`, `Recomposition`, or `Muscle Gain`), **When** weekly composite momentum is $< -1.00\%/\text{wk}$, **Then** the diagnostic status is `Possible muscle loss` (`CRITICAL_RED`) with helper message `"Losing faster than -1.00%/wk. High risk of lean muscle loss."`
2. **Given** any active subcycle (`Fat Loss`, `Recomposition`, or `Muscle Gain`), **When** weekly composite momentum is $> +0.75\%/\text{wk}$, **Then** the diagnostic status is `Suboptimal muscle gain` (`CRITICAL_RED`) with helper message `"Gaining faster than +0.75%/wk. Surplus exceeds optimal muscle synthesis rate."`
3. **Given** active subcycle `Recomposition` (`[-0.25%, +0.25%]`), **When** momentum is in `[-1.00%, -0.25%)` (e.g., `-0.45%/wk`), **Then** the badge displays `"Warning: Fat loss phase"` (`WARNING_AMBER`) with helper message `"Pace is below -0.25%/wk. Warning: You are currently in a fat loss phase."`; and **When** momentum is in `(+0.25%, +0.75%]` (e.g., `+0.45%/wk`), **Then** the badge displays `"Warning: Muscle gain phase"` (`WARNING_AMBER`) with helper message `"Pace exceeds +0.25%/wk. Warning: You are currently in a muscle gain phase."`
4. **Given** active subcycle `Fat Loss` (`[-1.00%, -0.25%]`), **When** momentum is in `[-1.00%, -0.25%]`, **Then** status is `Optimal` (`OPTIMAL_GREEN`) with no warning subtitle; **When** momentum is in `(-0.25%, 0.00%)` (e.g., `-0.15%/wk`), **Then** status is `"Slow fat loss pace"` (`WARNING_AMBER`) with helper `"Deficit is below -0.25%/wk. Increase deficit slightly to hit target range."`; and **When** momentum is in `[0.00%, +0.75%]`, **Then** status is `"Out of fat loss range"` (`CRITICAL_RED`) with helper `"Weight is stable or increasing. You are outside a caloric deficit."`
5. **Given** active subcycle `Muscle Gain` (`[+0.25%, +0.75%]`), **When** momentum is in `[+0.25%, +0.75%]`, **Then** status is `Optimal` (`OPTIMAL_GREEN`) with no warning subtitle; **When** momentum is in `(0.00%, +0.25%)` (e.g., `+0.15%/wk`), **Then** status is `"Slow muscle gain pace"` (`WARNING_AMBER`) with helper `"Surplus is below +0.25%/wk. Increase intake slightly to optimize growth."`; and **When** momentum is in `[-1.00%, 0.00%]`, **Then** status is `"Out of muscle gain range"` (`CRITICAL_RED`) with helper `"Weight is stable or dropping. Caloric surplus required for muscle gain."`

---

### User Story 3 - Composite-Only Warning Architecture & Clean Multi-Horizon Display (`SPEC-WEIGHT-CARD-02`) (Priority: P2)

In the `GOAL ARRIVAL ESTIMATIONS` card, diagnostic status badges, 5-zone corridor gauge visualization, and inline coaching helper subtitles trigger **exclusively** on the primary `✦ Weekly Body Weight` (`3:4:2:1` composite) row. The individual rolling horizon rows (`7D Avg`, `14D Avg`, `21D Avg`, `28D Avg`) display their average weight, weekly pace (`kg/wk` or `lbs/wk`), and weekly variation (`%/wk`) cleanly without redundant per-row warning badges or gauge bars.

**Why this priority**: Eliminates visual clutter on mobile screens (`< 360dp`) and prevents false acute alarms from single-week water fluctuations while keeping the composite diagnostic signal prominent and actionable.

**Independent Test**: Can be independently tested by rendering the `GOAL ARRIVAL ESTIMATIONS` card in both optimal and warning states and verifying that the `✦ Weekly Body Weight` composite row renders the 5-zone gauge, semantic status pill badge, and contextual helper subtitle (`↳ ...`), while the `7D`, `14D`, `21D`, and `28D` rows render clean single-line metric summaries (`7D Avg: 74.7 kg (-0.15 kg/wk · -0.20%/wk)`).

**Acceptance Scenarios**:

1. **Given** an active cycle where the `✦ Weekly Body Weight` composite triggers a non-optimal diagnostic state (e.g., `Warning: Fat loss phase` or `Slow fat loss pace`), **When** the `GOAL ARRIVAL ESTIMATIONS` card is displayed, **Then** the composite row shows the semantic pill badge (Amber `#FEF3C7`/`#D97706` or Red `#FEE2E2`/`#DC2626`), the 5-segment corridor gauge, and the inline helper message directly below the composite gauge.
2. **Given** an active cycle where the `✦ Weekly Body Weight` composite is `Optimal` (`OPTIMAL_GREEN`), **When** the card is displayed, **Then** the composite row shows the green `Optimal` pill badge and gauge while hiding the warning subtitle.
3. **Given** any calibrated cycle state, **When** the `7D Avg`, `14D Avg`, `21D Avg`, and `28D Avg` rows are rendered below the composite row, **Then** each row displays its weight, weekly pace, and weekly percentage variation without per-row warning badges or per-row gauge bars.

---

### User Story 4 - Coaching MCP Server Alignment with Updated Subcycle Taxonomy & Diagnostic Schema (Priority: P3)

AI coaching clients querying the Coaching MCP Server (`get_weight_momentum_analytics`) receive the updated subcycle taxonomy (`FAT_LOSS`, `RECOMPOSITION`, `MUSCLE_GAIN`, while continuing to accept legacy `WEIGHT_LOSS` and `WEIGHT_GAIN` inputs), tightened target corridors (`[-1.00%, -0.25%]`, `[-0.25%, +0.25%]`, `[+0.25%, +0.75%]`), global safety bounds (`possible_muscle_loss_below_pct: -1.00`, `suboptimal_muscle_gain_above_pct: 0.75`), and composite diagnostic fields (`severity`, `diagnostic_code`, `diagnostic_badge`, `diagnostic_message`).

**Why this priority**: Ensures parity between the mobile app and AI coaching integrations so external coaching assistants apply identical physiological thresholds and messaging.

**Independent Test**: Can be independently tested by calling `computeWeightMomentumAnalytics` in the MCP server test suite across all subcycle types and momentum zones and verifying the returned corridor bounds, diagnostic codes, badges, messages, and backward compatibility with legacy subcycle enum strings.

**Acceptance Scenarios**:

1. **Given** an MCP client requests weight momentum analytics for `FAT_LOSS` (or legacy `WEIGHT_LOSS`) with composite momentum `-0.15%/wk`, **When** the MCP tool computes the payload, **Then** `target_corridor` reports `[-1.00%, -0.25%]`, `global_safety_bounds` reports `{-1.00, 0.75}`, and `weekly_body_weight_composite` includes `severity: "WARNING_AMBER"`, `diagnostic_code: "SLOW_FAT_LOSS_PACE"`, `diagnostic_badge: "Slow fat loss pace"`, and `diagnostic_message: "Deficit is below -0.25%/wk. Increase deficit slightly to hit target range."`

---

### Edge Cases

- **Shared Boundary Inclusivity (`-1.00%`, `-0.25%`, `0.00%`, `+0.25%`, `+0.75%`)**:
  - At `-1.00%`: Inclusive for `Fat Loss` (`Optimal`) and `Recomposition` (`Warning: Fat loss phase`) and `Muscle Gain` (`Out of muscle gain range`); only strictly `< -1.00%` triggers `Possible muscle loss`.
  - At `-0.25%`: Inclusive for `Fat Loss` (`Optimal`) and `Recomposition` (`Optimal`); only strictly `> -0.25%` triggers `Slow fat loss pace` in `Fat Loss`, and only strictly `< -0.25%` triggers `Warning: Fat loss phase` in `Recomposition`.
  - At `0.00%`: In `Fat Loss`, `0.00%` triggers `Out of fat loss range` (`>= 0.00%`). In `Muscle Gain`, `0.00%` triggers `Out of muscle gain range` (`<= 0.00%`). In `Recomposition`, `0.00%` is `Optimal`.
  - At `+0.25%`: Inclusive for `Recomposition` (`Optimal`) and `Muscle Gain` (`Optimal`); only strictly `> +0.25%` triggers `Warning: Muscle gain phase` in `Recomposition`, and only strictly `< +0.25%` triggers `Slow muscle gain pace` in `Muscle Gain`.
  - At `+0.75%`: Inclusive for `Muscle Gain` (`Optimal`), `Recomposition` (`Warning: Muscle gain phase`), and `Fat Loss` (`Out of fat loss range`); only strictly `> +0.75%` triggers `Suboptimal muscle gain`.
- **Cold Start Calibration (`Day K < 8`)**: Suppresses out-of-corridor warnings and displays `Calibrating (Day {K}/8)` with helper message `"Collecting initial 8-day baseline to calculate weekly momentum."`
- **Legacy Metadata & Enum Compatibility**: Existing cycles with `[subcycle=WEIGHT_LOSS]` or `[subcycle=WEIGHT_GAIN]` in `Cycle.goal` decode cleanly to `Fat Loss` and `Muscle Gain`.
- **Single-Line Corridor Pill on Narrow Viewports & High Font Scale**: Corridor pills in `Select Subcycle Type` never wrap `/wk` onto a second line on standard viewports; when system font scale is $\ge 1.5\times$, the card header stacks the title and corridor pill vertically so neither clips.

## Requirements *(mandatory)*

### Functional Requirements

- **FR-001**: System MUST define three Weight Subcycle Types across all training cycles:
  - `FAT_LOSS` (Display label: `"Fat Loss"`, legacy alias `WEIGHT_LOSS`): Optimal weekly momentum corridor `-1.00%` to `-0.25%/wk` (`[-1.00%, -0.25%]` inclusive).
  - `RECOMPOSITION` (Display label: `"Recomposition"`): Optimal weekly momentum corridor `-0.25%` to `+0.25%/wk` (`[-0.25%, +0.25%]` inclusive).
  - `MUSCLE_GAIN` (Display label: `"Muscle Gain"`, legacy alias `WEIGHT_GAIN`): Optimal weekly momentum corridor `+0.25%` to `+0.75%/wk` (`[+0.25%, +0.75%]` inclusive).
- **FR-002**: System MUST maintain 100% backward compatibility when parsing persisted `[subcycle=...]` metadata in `Cycle.goal`, recognizing `FAT_LOSS`, `WEIGHT_LOSS`, `RECOMPOSITION`, `MUSCLE_GAIN`, and `WEIGHT_GAIN`.
- **FR-003**: System MUST evaluate calibrated weekly momentum ($\text{Mom}$) against global safety bounds first across all subcycles:
  - $\text{Mom} < -1.00\% \implies$ `POSSIBLE_MUSCLE_LOSS`, severity `CRITICAL_RED`, badge `"Possible muscle loss"`, subtitle `"Losing faster than -1.00%/wk. High risk of lean muscle loss."`
  - $\text{Mom} > +0.75\% \implies$ `SUBOPTIMAL_MUSCLE_GAIN`, severity `CRITICAL_RED`, badge `"Suboptimal muscle gain"`, subtitle `"Gaining faster than +0.75%/wk. Surplus exceeds optimal muscle synthesis rate."`
- **FR-004**: When $-1.00\% \le \text{Mom} \le +0.75\%$, System MUST evaluate strict subcycle-specific inequality rules:
  - **Active Subcycle = `FAT_LOSS`**:
    - $-1.00\% \le \text{Mom} \le -0.25\% \implies$ `ON_TARGET` (`OPTIMAL`), severity `OPTIMAL_GREEN`, badge `"Optimal"`, subtitle `null` (hidden).
    - $-0.25\% < \text{Mom} < 0.00\% \implies$ `SLOW_FAT_LOSS_PACE`, severity `WARNING_AMBER`, badge `"Slow fat loss pace"`, subtitle `"Deficit is below -0.25%/wk. Increase deficit slightly to hit target range."`
    - $0.00\% \le \text{Mom} \le +0.75\% \implies$ `OUT_OF_FAT_LOSS_RANGE`, severity `CRITICAL_RED`, badge `"Out of fat loss range"`, subtitle `"Weight is stable or increasing. You are outside a caloric deficit."`
  - **Active Subcycle = `RECOMPOSITION`**:
    - $-1.00\% \le \text{Mom} < -0.25\% \implies$ `WARNING_FAT_LOSS_PHASE`, severity `WARNING_AMBER`, badge `"Warning: Fat loss phase"`, subtitle `"Pace is below -0.25%/wk. Warning: You are currently in a fat loss phase."`
    - $-0.25\% \le \text{Mom} \le +0.25\% \implies$ `ON_TARGET` (`OPTIMAL`), severity `OPTIMAL_GREEN`, badge `"Optimal"`, subtitle `null` (hidden).
    - $+0.25\% < \text{Mom} \le +0.75\% \implies$ `WARNING_MUSCLE_GAIN_PHASE`, severity `WARNING_AMBER`, badge `"Warning: Muscle gain phase"`, subtitle `"Pace exceeds +0.25%/wk. Warning: You are currently in a muscle gain phase."`
  - **Active Subcycle = `MUSCLE_GAIN`**:
    - $-1.00\% \le \text{Mom} \le 0.00\% \implies$ `OUT_OF_MUSCLE_GAIN_RANGE`, severity `CRITICAL_RED`, badge `"Out of muscle gain range"`, subtitle `"Weight is stable or dropping. Caloric surplus required for muscle gain."`
    - $0.00\% < \text{Mom} < +0.25\% \implies$ `SLOW_MUSCLE_GAIN_PACE`, severity `WARNING_AMBER`, badge `"Slow muscle gain pace"`, subtitle `"Surplus is below +0.25%/wk. Increase intake slightly to optimize growth."`
    - $+0.25\% \le \text{Mom} \le +0.75\% \implies$ `ON_TARGET` (`OPTIMAL`), severity `OPTIMAL_GREEN`, badge `"Optimal"`, subtitle `null` (hidden).
- **FR-005**: During cold start ($K < 8$ calendar days), System MUST report `CALIBRATING` with badge `"Calibrating (Day {K}/8)"`, severity `NEUTRAL`, and helper subtitle `"Collecting initial 8-day baseline to calculate weekly momentum."`
- **FR-006**: System MUST classify universal physiological momentum zones using the updated thresholds:
  - `CALIBRATING` (`null`)
  - `POSSIBLE_MUSCLE_LOSS`: $\text{Mom} < -1.00\%$
  - `FAT_LOSS`: $-1.00\% \le \text{Mom} < -0.25\%$
  - `RECOMPOSITION`: $-0.25\% \le \text{Mom} \le +0.25\%$
  - `MUSCLE_GAIN`: $+0.25\% < \text{Mom} \le +0.75\%$
  - `SUBOPTIMAL_MUSCLE_GAIN`: $\text{Mom} > +0.75\%$
- **FR-007**: System MUST map momentum onto the 5-Segment Corridor Gauge (`[0.0f, 1.0f]`):
  - Zone 1 (`< -1.00%`, width `15%`, `[0.00f, 0.15f)`): Possible muscle loss zone.
  - Zone 2 (`-1.00% to -0.25%`, width `22.5%`, `[0.15f, 0.375f)`): Fat Loss zone.
  - Zone 3 (`-0.25% to +0.25%`, width `25%`, `[0.375f, 0.625f]`): Recomposition zone.
  - Zone 4 (`+0.25% to +0.75%`, width `22.5%`, `(0.625f, 0.85f]`): Muscle Gain zone.
  - Zone 5 (`> +0.75%`, width `15%`, `(0.85f, 1.00f]`): Suboptimal muscle gain zone.
- **FR-008**: System MUST update `SPEC-WEIGHT-CARD-02` (`CycleMomentumEstimationsCard`) so that:
  1. Header displays `Optimal Range: {minPct} to {maxPct}/wk ({minRate} to {maxRate} {unit}/wk)`.
  2. Diagnostic badge, 5-zone corridor gauge, and contextual helper subtitle (`↳ {diagnosticMessage}`) render **exclusively** on the `✦ Weekly Body Weight` composite row.
  3. Individual horizon rows (`7D Avg`, `14D Avg`, `21D Avg`, `28D Avg`) render their scalar weight, weekly pace, and weekly `%` variation cleanly without per-row warning badges or gauge bars.
  4. Semantic badge colors distinguish `OPTIMAL_GREEN` (`#E3F5EC`/`#0B6E3F` light, `#133826`/`#2ECC71` dark), `WARNING_AMBER` (`#FEF3C7`/`#D97706` light, `#3B2A14`/`#FBBF24` dark), and `CRITICAL_RED` (`#FEE2E2`/`#DC2626` light, `#3B1818`/`#F87171` dark).
- **FR-009**: System MUST update `SPEC-WEIGHT-SUBTYPE-SHEET-02` (`SubcycleTypePickerBottomSheet`) so that:
  1. Option cards display `Fat Loss` (`-1.00% to -0.25%/wk`), `Recomposition` (`-0.25% to +0.25%/wk`), and `Muscle Gain` (`+0.25% to +0.75%/wk`).
  2. Corridor pill badges enforce single-line rendering (`maxLines = 1`, `softWrap = false`, `fontSize = 12.sp`, `tabular-nums`, `horizontal = 8.dp, vertical = 4.dp`) and stack vertically when `fontScale >= 1.5f`.
  3. Cold start state (`0` weigh-ins) displays `"Projected Finish: Log first weigh-in to calculate range"`.
- **FR-010**: Coaching MCP Server (`get_weight_momentum_analytics`) MUST use the updated corridors (`[-1.00%, -0.25%]`, `[-0.25%, +0.25%]`, `[+0.25%, +0.75%]`), accept both canonical (`FAT_LOSS`, `RECOMPOSITION`, `MUSCLE_GAIN`) and legacy (`WEIGHT_LOSS`, `WEIGHT_GAIN`) subcycle identifiers, and expose `global_safety_bounds`, `severity`, `diagnostic_code`, `diagnostic_badge`, and `diagnostic_message`.

### Key Entities

- **WeightSubcycleType**: Updated subcycle enumeration (`FAT_LOSS` `"Fat Loss"` `[-1.00%, -0.25%]`, `RECOMPOSITION` `"Recomposition"` `[-0.25%, +0.25%]`, `MUSCLE_GAIN` `"Muscle Gain"` `[+0.25%, +0.75%]`) with backward-compatible aliases for `WEIGHT_LOSS` and `WEIGHT_GAIN`.
- **MomentumSeverity**: Semantic severity tier (`OPTIMAL_GREEN`, `WARNING_AMBER`, `CRITICAL_RED`, `NEUTRAL`) driving badge fill, text/icon color, and gauge accent.
- **CorridorComplianceStatus**: Diagnostic state enum covering `CALIBRATING`, `ON_TARGET`, `POSSIBLE_MUSCLE_LOSS`, `SUBOPTIMAL_MUSCLE_GAIN`, `WARNING_FAT_LOSS_PHASE`, `WARNING_MUSCLE_GAIN_PHASE`, `SLOW_FAT_LOSS_PACE`, `OUT_OF_FAT_LOSS_RANGE`, `SLOW_MUSCLE_GAIN_PACE`, and `OUT_OF_MUSCLE_GAIN_RANGE`.

## Success Criteria *(mandatory)*

### Measurable Outcomes

- **SC-001**: 100% of boundary and interior momentum values across all 3 subcycles (`Fat Loss`, `Recomposition`, `Muscle Gain`) resolve to their exact specified badge copy, severity tier, and coaching helper message in deterministic unit tests.
- **SC-002**: Corridor pill badges in `Select Subcycle Type` render on a single line (`maxLines = 1`, `softWrap = false`) without line-wrapping `/wk` on `360dp` mobile screens.
- **SC-003**: `GOAL ARRIVAL ESTIMATIONS` card renders diagnostic badges, 5-zone corridor gauge, and helper messages solely on the `✦ Weekly Body Weight` composite row while keeping `7D`, `14D`, `21D`, and `28D` rows free of redundant badges and gauges.
- **SC-004**: 100% of Android unit tests, PowerShell script tests, Android lint checks, and MCP server unit tests pass with zero regressions.

## Assumptions

- Legacy `WeightSubcycleType.WEIGHT_LOSS` and `WeightSubcycleType.WEIGHT_GAIN` references in persisted cycle metadata (`[subcycle=WEIGHT_LOSS]`, `[subcycle=WEIGHT_GAIN]`) or external callers are transparently aliased to `FAT_LOSS` and `MUSCLE_GAIN` so no database migration is required on Room schema v10.
