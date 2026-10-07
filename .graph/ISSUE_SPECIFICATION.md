## 🎯 Final Decision Plan & User Story Specification

### Title: feat: display last week baseline weights in 7-day pace targets

### 📖 User Story
**As an** athlete tracking a fat loss or recomposition cycle,  
**I want** to see last week's anchor weigh-in under both Expected Today and Target Tomorrow in the 7-Day Pace Targets card,  
**So that** I understand the historical baseline from which today's expected benchmark and tomorrow's target weigh-in were calculated.

### Scope Boundary
Display-only enhancement to [`TheoreticalPaceTargetsBox`](file:///c:/Users/rogal/workspaces/ws-gym/crosstrainingapp/app/src/main/java/com/fractanomics/crosstraining/ui/screens/ProgressScreen.kt#L1235) in `ProgressScreen.kt` and domain models in [`FatLossAnalytics.kt`](file:///c:/Users/rogal/workspaces/ws-gym/crosstrainingapp/app/src/main/java/com/fractanomics/crosstraining/data/analytics/FatLossAnalytics.kt). No changes to Room database schema, migrations, DAO queries, or core pacing/target mathematical formulas.

---

### 1. Architectural Decisions & Domain Contracts

#### 1.1 Pure Analytics Engine ([`FatLossAnalytics.kt`](file:///c:/Users/rogal/workspaces/ws-gym/crosstrainingapp/app/src/main/java/com/fractanomics/crosstraining/data/analytics/FatLossAnalytics.kt))
1. **Extend Sealed Interfaces with Trailing Parameter:**
   ```kotlin
   sealed interface ColumnState {
       data class Value(
           val targetWeightDisplay: Double,
           val deltaDisplay: Double?,
           val status: PaceTargetStatus? = null,
           val unitLabel: String,
           val baselineWeightDisplay: Double? = null
       ) : ColumnState

       data class AlreadyBelowTarget(
           val targetWeightDisplay: Double,
           val marginDisplay: Double,
           val unitLabel: String,
           val baselineWeightDisplay: Double? = null
       ) : ColumnState

       data class NeedsLog(
           val requiredDate: LocalDate
       ) : ColumnState

       data object InsufficientHistory : ColumnState
   }
   ```
2. **Anchor Assignment & Binding Invariants:**
   - **Interpretation 1 Ratified:** "Last week today's weight" is strictly $W_{t-7\text{d}}$ (`entryTMinus7.weightKg`). "Last week target tomorrow" is strictly $W_{t-6\text{d}}$ (`entryTMinus6.weightKg`). Interpretations 2 (synthetic recomputations) and 3 (day-name labels) are rejected.
   - **Today Column Baseline:** Whenever Today is `ColumnState.Value` (both when today is logged or unlogged), `baselineWeightDisplay` is mapped to `entryTMinus7.weightKg` (converted via `round1(WeightAnalytics.kgToLbs(...))` in imperial mode).
   - **Tomorrow Column Baseline (iff rule):** Tomorrow's `baselineWeightDisplay` is non-null **if and only if** `entryTMinus6 != null`.
     - When `entryTMinus6 != null`: `baselineWeightDisplay` is mapped to `entryTMinus6.weightKg` (converted if imperial) on both `ColumnState.Value` and `ColumnState.AlreadyBelowTarget`.
     - When `entryTMinus6 == null` (fallback path): Tomorrow emits `ColumnState.Value` with `baselineWeightDisplay = null`. The baseline UI line is strictly hidden.
   - **Non-Value States:** `ColumnState.NeedsLog`, `ColumnState.InsufficientHistory`, `PaceTargetsState.Stalled`, and `PaceTargetsState.Unavailable` carry no baseline.
   - **Non-Goals:** Pacing calculations, deltas, status tokens, and precedence rules remain unchanged. No algebraic identity between visible rounded numbers and theoretical benchmarks is guaranteed.

#### 1.2 Presentation Layer & Accessibility ([`ProgressScreen.kt`](file:///c:/Users/rogal/workspaces/ws-gym/crosstrainingapp/app/src/main/java/com/fractanomics/crosstraining/ui/screens/ProgressScreen.kt))
1. **Visual Micro-Label:**
   - Add a single `Text` line rendered beneath the subtitle in both columns of `TheoreticalPaceTargetsBox`:
     `Text("Last week: ${column.baselineWeightDisplay.trimmed()} ${column.unitLabel}", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)`
   - When `baselineWeightDisplay == null`, the text row is omitted (no `—` placeholder).
   - Layout certified safe for accessibility font scaling `fontScale >= 1.25f` without clipping or column divider overlap.
2. **TalkBack Accessibility Resolvers:**
   - Extend `resolveExpectedTodayContentDescription` and `resolveTargetTomorrowContentDescription`:
     - When `baselineWeightDisplay != null`:
       - Expected Today: append `", Last week, same day: ${column.baselineWeightDisplay.trimmed()} ${spokenUnit}"`
       - Target Tomorrow: append `", Last week, same day as tomorrow: ${column.baselineWeightDisplay.trimmed()} ${spokenUnit}"`
       *(where spokenUnit is `"kilograms"` for `"kg"` and `"lbs"` for `"lbs"`).*
     - When `baselineWeightDisplay == null`: output remains **byte-identical** to pre-existing string outputs, guaranteeing zero regression on existing tests.

---

### 2. Given-When-Then Acceptance Test Matrix

- **Scenario (a): Normal active day with both entries logged**  
  *Given* an active cycle with $W_{t-7\text{d}} = 77.3\text{ kg}$, $W_{t} = 76.5\text{ kg}$, and $P_{7\text{d}}(t-1\text{d}) = -0.9\text{ kg/wk}$.  
  *When* `compute7DayPaceTargets` is evaluated for reference date $t$ and `TheoreticalPaceTargetsBox` renders the result.  
  *Then* Expected Today is `ColumnState.Value(76.4, deltaDisplay = 0.1, status = OFF_PACE, unitLabel = "kg", baselineWeightDisplay = 77.3)` rendering `"Last week: 77.3 kg"`.

- **Scenario (b): Today unlogged**  
  *Given* an active cycle with $W_{t-7\text{d}} = 77.3\text{ kg}$ and day $t$ unlogged.  
  *When* `compute7DayPaceTargets` is evaluated for reference date $t$ and `TheoreticalPaceTargetsBox` renders the result.  
  *Then* Expected Today is `ColumnState.Value(76.4, deltaDisplay = null, status = null, unitLabel = "kg", baselineWeightDisplay = 77.3)` rendering `"Last week: 77.3 kg"`.

- **Scenario (c): Tomorrow on anchor path**  
  *Given* an active cycle with $W_{t-6\text{d}} = 75.3\text{ kg}$, $P_{7\text{d}}(t) = -0.8\text{ kg/wk}$, and $W_{t} = 76.5\text{ kg}$.  
  *When* `compute7DayPaceTargets` is evaluated for reference date $t$ and `TheoreticalPaceTargetsBox` renders the result.  
  *Then* Target Tomorrow is `ColumnState.Value(74.5, deltaDisplay = -2.0, status = null, unitLabel = "kg", baselineWeightDisplay = 75.3)` rendering `"Last week: 75.3 kg"`.

- **Scenario (c2): Tomorrow on anchor path with today unlogged**  
  *Given* an active cycle with $W_{t-6\text{d}} = 75.3\text{ kg}$, $P_{7\text{d}}(t) = -0.8\text{ kg/wk}$, and day $t$ unlogged.  
  *When* `compute7DayPaceTargets` is evaluated for reference date $t$ and `TheoreticalPaceTargetsBox` renders the result.  
  *Then* Target Tomorrow is `ColumnState.Value(74.5, deltaDisplay = null, status = null, unitLabel = "kg", baselineWeightDisplay = 75.3)` rendering `"Last week: 75.3 kg"`.

- **Scenario (d): Tomorrow AlreadyBelowTarget on anchor path**  
  *Given* an active cycle with $W_{t-6\text{d}} = 75.3\text{ kg}$, $P_{7\text{d}}(t) = -0.8\text{ kg/wk}$, and $W_{t} = 74.0\text{ kg}$ ($74.0 < 74.5$).  
  *When* `compute7DayPaceTargets` is evaluated for reference date $t$ and `TheoreticalPaceTargetsBox` renders the result.  
  *Then* Target Tomorrow is `ColumnState.AlreadyBelowTarget(targetWeightDisplay = 74.5, marginDisplay = 0.5, unitLabel = "kg", baselineWeightDisplay = 75.3)` rendering `"Last week: 75.3 kg"`.

- **Scenario (e): Tomorrow fallback calculation path ($t-6\text{d}$ unlogged)**  
  *Given* an active cycle with $t-6\text{d}$ unlogged, $W_{t} = 76.5\text{ kg}$, and $P_{7\text{d}}(t) = -0.8\text{ kg/wk}$.  
  *When* `compute7DayPaceTargets` is evaluated for reference date $t$ and `TheoreticalPaceTargetsBox` renders the result.  
  *Then* Target Tomorrow is `ColumnState.Value(76.4, deltaDisplay = -0.1, status = null, unitLabel = "kg", baselineWeightDisplay = null)`. The "Last week" line is strictly hidden.

- **Scenario (e2): Tomorrow fallback calculation path with zero rounded delta ($t-6\text{d}$ unlogged)**  
  *Given* an active cycle with $t-6\text{d}$ unlogged, $W_{t} = 76.5\text{ kg}$, and $P_{7\text{d}}(t) = -0.3\text{ kg/wk}$.  
  *When* `compute7DayPaceTargets` is evaluated for reference date $t$ and `TheoreticalPaceTargetsBox` renders the result.  
  *Then* Target Tomorrow is `ColumnState.Value(76.5, deltaDisplay = null, status = null, unitLabel = "kg", baselineWeightDisplay = null)`. The "Last week" line is strictly hidden.

- **Scenario (f): Imperial unit localization**  
  *Given* $W_{t-7\text{d}} = 77.3\text{ kg}$ and user unit preference `weightUnit == "lbs"`.  
  *When* `compute7DayPaceTargets` is evaluated for reference date $t$ and `TheoreticalPaceTargetsBox` renders the result.  
  *Then* baseline displays as `"Last week: 170.4 lbs"`.

- **Scenario (g): Non-value states display no baseline**  
  *Given* `NeedsLog`, `InsufficientHistory`, `PaceTargetsState.Stalled`, or `PaceTargetsState.Unavailable`.  
  *When* `compute7DayPaceTargets` is evaluated and `TheoreticalPaceTargetsBox` renders.  
  *Then* no baseline line is rendered.

- **Scenario (h): Backwards compatibility for existing tests**  
  *Given* any `ColumnState` where `baselineWeightDisplay == null`.  
  *When* content description or subtitle resolvers are invoked.  
  *Then* returned strings are byte-identical to existing baseline assertions in `WeightProgressionCardTest.kt`.

- **Scenario (i): Typography, ellipsis, and font-scale resilience**  
  *Given* dynamic font scaling at $1.3\times$ (`fontScale >= 1.25f`).  
  *When* `TheoreticalPaceTargetsBox` renders with non-null baselines.  
  *Then* baseline text is single-line ellipsized if needed and does not clip or overflow column dividers.

---

### 3. Implementation Tasks (Pattern A - Standalone Task)

* **Task 1 (7-Day Pace Targets Last Week Baseline Weights):**
  - In [`FatLossAnalytics.kt`](file:///c:/Users/rogal/workspaces/ws-gym/crosstrainingapp/app/src/main/java/com/fractanomics/crosstraining/data/analytics/FatLossAnalytics.kt):
    - Add trailing `baselineWeightDisplay: Double? = null` parameter to `ColumnState.Value` and `ColumnState.AlreadyBelowTarget`.
    - In `compute7DayPaceTargets`, assign `entryTMinus7.weightKg` (converted if imperial) to Expected Today's `baselineWeightDisplay`.
    - In `compute7DayPaceTargets`, assign `entryTMinus6.weightKg` (converted if imperial) to Target Tomorrow's `baselineWeightDisplay` when `entryTMinus6 != null`; set to `null` on fallback.
  - In [`ProgressScreen.kt`](file:///c:/Users/rogal/workspaces/ws-gym/crosstrainingapp/app/src/main/java/com/fractanomics/crosstraining/ui/screens/ProgressScreen.kt):
    - Update `TheoreticalPaceTargetsBox` to render the "Last week: [weight] [unit]" micro-label when `baselineWeightDisplay != null`, formatted with `trimmed()`.
    - Update `resolveExpectedTodayContentDescription` and `resolveTargetTomorrowContentDescription` with column-specific TalkBack phrases when baseline is present, keeping byte-identical output when null.
  - In [`FatLossAnalyticsTest.kt`](file:///c:/Users/rogal/workspaces/ws-gym/crosstrainingapp/app/src/test/java/com/fractanomics/crosstraining/data/analytics/FatLossAnalyticsTest.kt):
    - Add unit tests verifying scenarios (a)–(g) including anchor vs fallback and imperial conversions.
  - In [`WeightProgressionCardTest.kt`](file:///c:/Users/rogal/workspaces/ws-gym/crosstrainingapp/app/src/test/java/com/fractanomics/crosstraining/ui/screens/WeightProgressionCardTest.kt):
    - Verify content description extensions and ensure existing backwards-compatibility tests pass unchanged.
  - In [`CHANGELOG.md`](file:///c:/Users/rogal/workspaces/ws-gym/crosstrainingapp/CHANGELOG.md):
    - Document the addition of last week baseline weights in 7-Day Pace Targets under `## [Unreleased]`.