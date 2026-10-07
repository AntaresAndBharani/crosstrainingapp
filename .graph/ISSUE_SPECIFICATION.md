## 🎯 Final Decision Plan & User Story Specification

### Title: feat: 7-day pace targets for daily expected weight and tomorrow target

### 📖 User Story
**As a** cross-training athlete tracking a fat loss or recomposition cycle,  
**I want** a dedicated "7-Day Pace Targets" container displaying today's expected weight vs actual and tomorrow's target weigh-in with overnight drop,  
**So that** I have actionable daily targets derived directly from my weekly pace without guesswork.

### Scope Boundary
The `7-DAY PACE TARGETS` container (`TheoreticalPaceTargetsBox`) inside `WeightProgressionHeroCard` in [`ProgressScreen.kt`](file:///C:/Users/rogal/workspaces/ws-gym/crosstrainingapp/app/src/main/java/com/fractanomics/crosstraining/ui/screens/ProgressScreen.kt) is the only new UI addition. The surrounding progress bar, Current Weight hero, 14-Day pace card, and forecast banner pre-exist and remain unchanged.

---

### 1. Architectural Decisions & Changes

#### 1.1 Pure Calculation Engine & Sealed Domain Model (`FatLossAnalytics.kt`)
In [`FatLossAnalytics.kt`](file:///C:/Users/rogal/workspaces/ws-gym/crosstrainingapp/app/src/main/java/com/fractanomics/crosstraining/data/analytics/FatLossAnalytics.kt):
1. **Domain Sealed Hierarchy:**
   ```kotlin
   sealed interface PaceTargetsState {
       object Unavailable : PaceTargetsState // when P_7d(t) == null (< 7 elapsed days)
       object Stalled : PaceTargetsState     // when P_7d(t) >= 0.0 (surplus or maintenance)
       data class Available(
           val today: ColumnState,
           val tomorrow: ColumnState
       ) : PaceTargetsState
   }

   sealed interface ColumnState {
       data class Value(
           val targetWeightDisplay: Double,
           val deltaDisplay: Double?,
           val status: PaceTargetStatus? = null, // AHEAD, ON_PACE, OFF_PACE (null for Target Tomorrow or unlogged today)
           val unitLabel: String
       ) : ColumnState

       data class AlreadyBelowTarget(
           val targetWeightDisplay: Double,
           val marginDisplay: Double,
           val unitLabel: String
       ) : ColumnState

       data class NeedsLog(
           val requiredDate: LocalDate
       ) : ColumnState

       object InsufficientHistory : ColumnState // e.g. Day 7 of cycle for Expected Today
   }

   enum class PaceTargetStatus {
       AHEAD, ON_PACE, OFF_PACE
   }
   ```
2. **Benchmark Mathematical Rules:**
   - **Expected Today ($W_{\text{theo, today}}$):**
     $$W_{\text{theo, today}} = W_{t - 7\text{d}} + P_{7\text{d}}(t - 1\text{d})$$
     where $P_{7\text{d}}(t - 1\text{d}) = \text{compute7DayPace}(entries, cycleStartDate, referenceDate.minusDays(1))$.
     - *Anchoring Invariant:* Exact-day calendar matching applies strictly to baseline $W$ terms ($W_{t-7\text{d}}$, $W_{t-6\text{d}}$); pace $P_{7\text{d}}$ retains `resolveAnchorEntry` on-or-before lookup tolerance.
     - *Delta vs Actual:* $\Delta W_{\text{pace}} = W_{\text{actual, today}} - W_{\text{theo, today}}$.
   - **Target Tomorrow ($W_{\text{theo, tomorrow}}$):**
     $$W_{\text{theo, tomorrow}} = W_{t - 6\text{d}} + P_{7\text{d}}(t)$$
     Fallback when $t - 6\text{d}$ is unlogged:
     $$W_{\text{theo, tomorrow}} = W_{\text{actual, today}} + \frac{P_{7\text{d}}(t)}{7}$$
     - *Tomorrow Sign Convention:* For Target Tomorrow, `deltaDisplay = round1(target) - round1(actual)`.
   - **1-Decimal Display Equality & Sign Rules:**
     - For Expected Today: $\Delta_{\text{display}} = \text{round1}(W_{\text{actual, display}}) - \text{round1}(W_{\text{target, display}})$.
       - $\Delta_{\text{display}} == 0.0 \rightarrow \text{ON\_PACE}$ (`"On pace"`).
       - $\Delta_{\text{display}} < 0.0 \rightarrow \text{AHEAD}$ (`"-$X unit ahead"` in `primary`).
       - $\Delta_{\text{display}} > 0.0 \rightarrow \text{OFF\_PACE}$ (`"+$X unit off pace"` in `tertiary` warning token).
     - For Target Tomorrow:
       - If $\text{round1}(W_{\text{actual, display}}) > \text{round1}(W_{\text{target, display}})$: Renders `"-$X unit overnight"`.
       - If $\text{round1}(W_{\text{actual, display}}) == \text{round1}(W_{\text{target, display}})$: Renders `ColumnState.Value` with overnight micro-label hidden (target hit, no `"-0.0"`).
       - If $\text{round1}(W_{\text{actual, display}}) < \text{round1}(W_{\text{target, display}})$: Renders `ColumnState.AlreadyBelowTarget` with micro-label `"Already below target (-$X unit)"` formatted via `abs()`.
       - If no weigh-in today: Overnight micro-label is hidden (only benchmark $W_{\text{theo, tomorrow}}$ is shown).
3. **State Precedence Rules:**
   - Container Level: `PaceTargetsState.Stalled` > `PaceTargetsState.Unavailable` > `PaceTargetsState.Available`.
   - Column Level (within `Available`): `ColumnState.InsufficientHistory` > `ColumnState.NeedsLog` > `ColumnState.AlreadyBelowTarget` > `ColumnState.Value`.

#### 1.2 UI Presentation (`ProgressScreen.kt`)
In [`ProgressScreen.kt:1169-1335`](file:///C:/Users/rogal/workspaces/ws-gym/crosstrainingapp/app/src/main/java/com/fractanomics/crosstraining/ui/screens/ProgressScreen.kt#L1169-L1335):
- Add `TheoreticalPaceTargetsBox` composable immediately beneath the Current Weight hero and its day-over-day delta pill inside `WeightProgressionHeroCard`.
- Renders rounded corners (12.dp) with `surfaceContainerHighest` fill.
- Left column displays Expected Today ($W_{\text{theo, today}}$); right column displays Target Tomorrow ($W_{\text{theo, tomorrow}}$) separated by a 1dp divider.
- Full localization support: converts to `lbs` via `WeightAnalytics.kgToLbs` and formats with `Double.trimmed()`.

---

### 2. Acceptance Criteria & Test Matrix (Given-When-Then)

#### Scenario 1: Normal Active Progress (Weigh-in-Only Fixture)
* **Given** an active cycle starting on `2026-09-28` with weigh-ins on:
  - `t - 8d` (`2026-09-29`): `75.91 kg`
  - `t - 7d` (`2026-09-30`): `77.3 kg`
  - `t - 6d` (`2026-10-01`): `76.9 kg`
  - `t - 1d` (`2026-10-06`): `75.0 kg`
  - `t` (`2026-10-07`): `76.5 kg`
* **When** `compute7DayPaceTargets` is evaluated on day `t` (`2026-10-07`).
* **Then**:
  - $P_{7\text{d}}(t-1\text{d}) = -0.91\text{ kg/wk}$ anchoring to $t-8\text{d}$, yielding Expected Today $W_{\text{theo, today}} = 76.39 \approx 76.4\text{ kg}$.
  - $P_{7\text{d}}(t) = -0.80\text{ kg/wk}$ anchoring to $t-7\text{d}$, yielding Target Tomorrow $W_{\text{theo, tomorrow}} = 76.1\text{ kg}$.
  - `Expected Today` returns `ColumnState.Value` with `targetWeight = 76.4`, `delta = +0.1`, `status = OFF_PACE` (`"+0.1 kg off pace"`).
  - `Target Tomorrow` returns `ColumnState.Value` with `targetWeight = 76.1`, `delta = -0.4`, `status = null` (`"-0.4 kg overnight"`).

#### Scenario 2: Missing $t-7\text{d}$ Baseline Log
* **Given** an active cycle starting on `2026-09-28` with weigh-ins on `t - 8d` (`77.4 kg`), `t - 6d` (`76.9 kg`), `t - 1d` (`75.0 kg`), and `t` (`76.5 kg`), with day `t - 7d` missing.
* **When** `compute7DayPaceTargets` is evaluated on day $t$.
* **Then**:
  - $P_{7\text{d}}(t)$ anchors to $t-8\text{d}$ (8 elapsed days), yielding $-0.7875\text{ kg/wk} < 0$, keeping state `PaceTargetsState.Available`.
  - `Expected Today` returns `ColumnState.NeedsLog(requiredDate = t - 7d)` (`"Expected Today: Needs log from [Date]"`).
  - `Target Tomorrow` returns `ColumnState.Value` with target $76.9 - 0.7875 = 76.11 \approx 76.1\text{ kg}$ and micro-label `"-0.4 kg overnight"`.

#### Scenario 3: Missing $t-6\text{d}$ Log (Tomorrow Fallback Triggered)
* **Given** the Scenario 1 fixture with day `t - 6d` missing.
* **When** `compute7DayPaceTargets` is evaluated on day $t$.
* **Then**:
  - `Target Tomorrow` evaluates fallback $76.5 + \frac{-0.80}{7} = 76.386 \approx 76.4\text{ kg}$.
  - Overnight needed drop renders `"-0.1 kg overnight"`.

#### Scenario 4: Early Cycle (< 7 Days History, Null Pace)
* **Given** a new cycle with only 4 days of weigh-in logs ($P_{7\text{d}}(t) == \text{null}$).
* **When** `compute7DayPaceTargets` is evaluated.
* **Then**:
  - State returns `PaceTargetsState.Unavailable`.
  - Container displays `"Pace Targets Unavailable: Needs 7 days of weigh-in history"`.

#### Scenario 5: Stalled or Positive Pace ($P_{7\text{d}}(t) \ge 0$)
* **Given** an active cycle with weigh-ins on $t-7\text{d} = 77.0\text{ kg}$ and $t = 77.2\text{ kg}$ ($P_{7\text{d}}(t) = +0.2\text{ kg/wk} \ge 0$).
* **When** `compute7DayPaceTargets` is evaluated.
* **Then**:
  - State returns `PaceTargetsState.Stalled`.
  - Container displays `"Pace Stalled: Surplus/maintenance pace detected"`.

#### Scenario 6: No Weigh-in Logged Today
* **Given** day $t-7\text{d}$ and $t-6\text{d}$ exist, but the athlete has not logged a weigh-in for day $t$.
* **When** `compute7DayPaceTargets` is evaluated ($P_{7\text{d}}(t)$ resolved from newest logged entry in history).
* **Then**:
  - `Expected Today` displays target weight without actual-based delta.
  - `Target Tomorrow` displays target weight without overnight delta.
  - If $t-6\text{d}$ is unlogged, `Target Tomorrow` transitions to `NeedsLog(t - 6d)`.

#### Scenario 7: Boundary Attainment (Actual < Target Tomorrow)
* **Given** an active cycle with weigh-ins on $t-7\text{d} = 77.3\text{ kg}$, $t-6\text{d} = 77.6\text{ kg}$, and $t = 75.8\text{ kg}$.
* **When** `compute7DayPaceTargets` is evaluated on day $t$.
* **Then**:
  - $P_{7\text{d}}(t) = -1.5\text{ kg/wk}$, Target Tomorrow is $76.1\text{ kg}$.
  - Actual weight $75.8\text{ kg} < 76.1\text{ kg}$ (margin $0.3\text{ kg}$).
  - `Target Tomorrow` returns `ColumnState.AlreadyBelowTarget(targetWeightDisplay = 76.1, marginDisplay = 0.3, unitLabel = "kg")`.
  - Subtitle renders `"Already below target (-0.3 kg)"` with `abs()` formatting (no `"--"`).
  - `Expected Today` returns `ColumnState.InsufficientHistory`.

#### Scenario 7b: Rounded Equality at Target Tomorrow (Overnight Label Hidden)
* **Given** the Scenario 1 fixture with $t-6\text{d} = 77.3\text{ kg}$ (so Target Tomorrow is $77.3 - 0.80 = 76.5\text{ kg}$, equal to actual $76.5\text{ kg}$).
* **When** `compute7DayPaceTargets` is evaluated on day $t$.
* **Then**:
  - Rounded actual equals rounded target ($76.5\text{ kg}$).
  - `Target Tomorrow` returns `ColumnState.Value(76.5, deltaDisplay = null, status = null, unitLabel = "kg")` with overnight micro-label hidden.

#### Scenario 8: Exactly On Pace (1-Decimal Rounded Equality)
* **Given** the Scenario 1 fixture with today's actual weight $t = 76.4\text{ kg}$.
* **When** `compute7DayPaceTargets` is evaluated on day $t$.
* **Then**:
  - Expected Today is $76.39 \approx 76.4\text{ kg}$.
  - Rounded display delta is $0.0$.
  - `Expected Today` status is `ON_PACE` and micro-label renders `"On pace"`.
  - Target Tomorrow returns $76.0\text{ kg}$.

#### Scenario 9: Imperial Unit Conversion (`lbs`)
* **Given** the Scenario 1 fixture with user preference `weightUnit == "lbs"`.
* **When** `compute7DayPaceTargets` is evaluated.
* **Then**:
  - Expected Today target $76.39\text{ kg} \rightarrow 168.4\text{ lbs}$, delta $+0.1\text{ kg} \rightarrow +0.2\text{ lbs off pace}$.
  - Target Tomorrow target $76.1\text{ kg} \rightarrow 167.8\text{ lbs}$, delta $-0.4\text{ kg} \rightarrow -0.9\text{ lbs overnight}$.
  - All values display with `"lbs"` suffix.

#### Scenario 10: Modifying Today's Logged Weigh-in (Single Entry Invariant)
* **Given** an athlete has logged day $t$ at `76.4 kg` and subsequently updates today's entry to `76.8 kg` (Room single-entry-per-day `@PrimaryKey date` invariant).
* **When** `compute7DayPaceTargets` is evaluated.
* **Then**:
  - $P_{\text{ref}}$ for Expected Today is evaluated strictly at `today.minusDays(1)`, so Expected Today's benchmark ($76.4\text{ kg}$) remains unchanged.
  - Only the actual-based delta updates to reflect the modified weigh-in ($76.8\text{ kg} - 76.4\text{ kg} = +0.4\text{ kg off pace}$).

#### Scenario 11: Day 7 of Cycle (Declining Entries, Expected Today Insufficient History)
* **Given** day 7 of a cycle with daily declining weigh-ins from day 0 to day 7 ($80.0\text{ kg}$ down to $78.6\text{ kg}$, $P_{7\text{d}}(t) < 0$), where $P_{7\text{d}}(t)$ exists (7 elapsed days), but $P_{7\text{d}}(t-1\text{d})$ spans only 6 days and returns `null`.
* **When** `compute7DayPaceTargets` is evaluated.
* **Then**:
  - `Expected Today` returns `ColumnState.InsufficientHistory` (`"Expected Today: Needs 7 days history"`).
  - `Target Tomorrow` returns `ColumnState.Value` with target $W_{t-6\text{d}} + P_{7\text{d}}(t)$ and overnight delta.

---

### 3. Implementation Tasks (Pattern A - Standalone Task)

* **Task 1 (7-Day Pace Targets Container & Pacing Engine):**
  - In [`FatLossAnalytics.kt`](file:///C:/Users/rogal/workspaces/ws-gym/crosstrainingapp/app/src/main/java/com/fractanomics/crosstraining/data/analytics/FatLossAnalytics.kt):
    - Implement sealed interfaces `PaceTargetsState` (`Unavailable`, `Stalled`, `Available`) and `ColumnState` (`Value`, `AlreadyBelowTarget`, `NeedsLog`, `InsufficientHistory`).
    - Implement pure domain function `compute7DayPaceTargets(entries, cycleStartDate, referenceDate, weightUnit)` enforcing exact-day baseline terms, `resolveAnchorEntry` tolerance for pace, 1-decimal rounded display equality, and state precedence rules.
  - In [`ProgressScreen.kt`](file:///C:/Users/rogal/workspaces/ws-gym/crosstrainingapp/app/src/main/java/com/fractanomics/crosstraining/ui/screens/ProgressScreen.kt):
    - Implement `TheoreticalPaceTargetsBox` composable rendered inside `WeightProgressionHeroCard` immediately below the Current Weight day-over-day delta pill.
    - Support unit conversion, state transitions (`Available`, `NeedsLog`, `Stalled`, `Unavailable`, `InsufficientHistory`, `AlreadyBelowTarget`), and accessibility semantics.
  - In [`FatLossAnalyticsTest.kt`](file:///C:/Users/rogal/workspaces/ws-gym/crosstrainingapp/app/src/test/java/com/fractanomics/crosstraining/data/analytics/FatLossAnalyticsTest.kt):
    - Implement unit test methods covering Scenarios 1–11 (including Scenario 7b) with deterministic assertions.
  - In [`WeightProgressionCardTest.kt`](file:///C:/Users/rogal/workspaces/ws-gym/crosstrainingapp/app/src/test/java/com/fractanomics/crosstraining/ui/screens/WeightProgressionCardTest.kt):
    - Add JVM unit tests verifying composable text resolution and state rendering for CI verification parity.
  - In [`CHANGELOG.md`](file:///C:/Users/rogal/workspaces/ws-gym/crosstrainingapp/CHANGELOG.md):
    - Add entry under `## [Unreleased]` describing the 7-Day Pace Targets container.