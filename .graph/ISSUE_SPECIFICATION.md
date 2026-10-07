## 🎯 Final Decision Plan & User Story Specification

### Title: feat: weight progression hero card redesign with daily weight delta pill, drop streak, and weekly pace acceleration tracking

### 📖 User Story
**As a** cross-training athlete tracking a fat-loss bodybuilding cycle,  
**I want** a unified Weight Progression Hero Card featuring my current weight as the focal centerpiece with daily weight change and drop streak pills, flanked by start and goal context anchors and weekly pace comparison metrics,  
**So that** I have immediate, unambiguous visual clarity on my daily weight progress and rate of loss without uneven card wrapping or misleading date comparisons.

---

### 1. Architectural & Presentation Specification

#### 1.1 Container Architecture & Shipped Contract Retention (`ProgressScreen.kt`)
`WeightProgressionCard` is retained as the outer container composable, preserving all established container contracts, lifecycle behaviors, and regression test suites:
1. **Cycle Type Guard:** Exits early if `cycle.cycleType != CycleType.FAT_LOSS_BODYBUILDING`.
2. **Zero-Entry Empty State:** Renders the existing empty card state when `activeEntries.isEmpty()` (satisfying Scenario F).
3. **Dynamic Titling:** Passes dynamic cycle name `"Weight Progression ($cycleName)"` to the hero card (satisfying Scenario I).
4. **Anchor & Metric Resolution with Fallbacks:**
   ```kotlin
   val startWeightKg: Double? = cycle.startingWeightKg ?: activeEntries.firstOrNull()?.weightKg
   val goalWeightKg: Double? = cycle.targetWeightKg ?: cycle.targetBodyWeightKg
   val currentWeightKg: Double = activeEntries.last().weightKg
   val startDateFormatted: String = if (cycle.startingWeightKg != null) {
       cycle.startDate.formatShort()
   } else {
       activeEntries.firstOrNull()?.date?.formatShort() ?: "No log"
   }
   ```
5. **Domain Analytics Wiring:**
   ```kotlin
   val dayOverDayDelta = FatLossAnalytics.computeDayOverDayDelta(activeEntries, cycle.startDate, today)
   val dropStreakDays = FatLossAnalytics.computeWeightDecreasingStreak(activeEntries, cycle.startDate, today)
   val pace7 = FatLossAnalytics.compute7DayPace(weightEntries, cycle, today)
   val pace14 = FatLossAnalytics.compute14DayPace(weightEntries, cycle, today)
   val paceComparison7d = FatLossAnalytics.compute7DayPaceComparison(activeEntries, cycle.startDate, today)
   val forecast = FatLossAnalytics.computePaceForecast(weightEntries, cycle, today)
   ```
6. **Delegation:** Renders `WeightProgressionHeroCard(...)` presenter composable.

#### 1.2 Presenter Composable & Visual Hierarchy: `WeightProgressionHeroCard`
1. **Top Reference Track (Start & Goal Anchors):**
   - Restructured into two fixed vertical lines per anchor column (`Column(modifier = Modifier.weight(1f, fill = false), horizontalAlignment = ...)`):
     * **Start Anchor (Left):** Line 1 = `"START 171.5 lbs"` (`labelSmall`, `maxLines = 1`, `overflow = TextOverflow.Ellipsis`), Line 2 = `"(28 Sep)"` (`bodySmall`, `maxLines = 1`, `overflow = TextOverflow.Ellipsis`). Falls back to `"START -- $weightUnit"` if start weight is null.
     * **Goal Anchor (Right):** Line 1 = `"GOAL 154.3 lbs"` (`labelSmall`, `maxLines = 1`, `overflow = TextOverflow.Ellipsis`), Line 2 = `"(-14.8 lbs left)"` or `"Goal reached"` (`bodySmall`, `maxLines = 1`, `overflow = TextOverflow.Ellipsis`). Falls back to `"GOAL: No target"` if goal weight is null.
   - `LinearProgressIndicator` (6dp height, rounded) flanked between anchors with centered percentage string `String.format(Locale.US, "%.1f%% to goal", progressFraction * 100f)`. Hidden when goal or start is null.
2. **Hero Centerpiece (Current Weight & Intelligence Pills):**
   - Centered `CURRENT WEIGHT` label (`labelSmall`, letterSpacing = 1.sp).
   - Centered current weight numeric value: `String.format(Locale.US, "%.1f", displayCurrent) $weightUnit` in `displaySmall` bold (36sp).
   - `FlowRow` container for intelligent status pills:
     * **Day-Over-Day Delta Pill:** Evaluated against `NOISE_THRESHOLD_KG = 0.05`. Rendered in `tertiaryContainer` for drops ($< -0.05\text{ kg}$), `errorContainer` for gains ($> 0.05\text{ kg}$), and `surfaceContainerHighest` for neutral noise deadbands. Single-day consecutive logs display `"vs yesterday"`, non-consecutive logs display `"vs <date>"`, and stale logs prepend latest date (e.g. `"5 Oct · ▼ -0.3 kg vs 4 Oct"`). Suppressed when delta is null (e.g. single weigh-in).
     * **Drop Streak Pill:** Rendered in `primaryContainer` displaying `"📉 ${dropStreakDays}-day streak"` when `dropStreakDays >= 2` and `active.last().date >= referenceDate.minusDays(1)`. Suppressed when streak $< 2$ or stale.
3. **Bottom Pace Metrics Split (7-Day & 14-Day Cards):**
   - Two equal-height cards using `Row(modifier = Modifier.fillMaxWidth().height(IntrinsicSize.Max), horizontalArrangement = Arrangement.spacedBy(12.dp))` with `Surface(modifier = Modifier.weight(1f).fillMaxHeight(), shape = RoundedCornerShape(12.dp), color = surfaceContainerHighest)`.
   - **Pace Trend Direction & Color Mapping (`paceTrendStyle`):**
     ```kotlin
     internal data class PaceTrendStyle(val icon: String?, val color: Color)

     @Composable
     internal fun paceTrendStyle(trend: PaceTrend?): PaceTrendStyle = when (trend) {
         PaceTrend.LOSS -> PaceTrendStyle("▼ ", MaterialTheme.colorScheme.tertiary)
         PaceTrend.GAIN -> PaceTrendStyle("▲ ", MaterialTheme.colorScheme.error)
         PaceTrend.NEUTRAL -> PaceTrendStyle("— ", MaterialTheme.colorScheme.onSurfaceVariant)
         null -> PaceTrendStyle(null, MaterialTheme.colorScheme.onSurfaceVariant)
     }
     ```
   - **7-Day Pace Card:** Value prefixed with trend icon and formatted via `formatDisplay(weightUnit)` (`/wk`). Colored by `paceTrendStyle(trend).color` (or `titleSmall` fallback at 1.3× scale if necessary). Subtitle displays `format7DayPaceSubtitle` (`14% faster vs yesterday`, `25% slower vs yesterday`, `Pace unchanged`, `+0.3 kg/wk vs yesterday`, `Baseline 7d pace`). Empty copy standardized to `"Not enough data yet"`.
   - **14-Day Pace Card:** Value prefixed with trend icon and formatted via `formatDisplay(weightUnit)` (`/wk`). Colored by `paceTrendStyle(trend).color`. Subtitle displays `"Needs a weigh-in from 14+ days ago"` when pace is null, `"Smoothed trend"` when populated. Empty copy standardized to `"Not enough data yet"`.
4. **Forecast Banner & CTA:**
   - Preserves `ForecastBanner(forecast)` for all 5 `PaceForecast` states (`GoalReached`, `CycleEnded`, `NotEnoughData`, `Stalled`, `Projected`).
   - `+ Log weigh-in` TextButton aligned to card end.

---

### 2. Domain Analytics Algorithms (`object FatLossAnalytics` in `FatLossAnalytics.kt`)

All new constants, models, algorithms, and formatters are encapsulated inside `object FatLossAnalytics` in [`FatLossAnalytics.kt:265`](file:///C:/Users/rogal/workspaces/ws-gym/crosstrainingapp/app/src/main/java/com/fractanomics/crosstraining/data/analytics/FatLossAnalytics.kt#L265):

1. **Noise Deadband Constants:**
   ```kotlin
   const val NOISE_THRESHOLD_KG = 0.05
   const val PACE_NOISE_THRESHOLD_KG_PER_WEEK = 0.05
   ```
2. **Active Weight Entries Filter (Date Primary Key Integrity):**
   ```kotlin
   fun getActiveWeightEntries(
       entries: List<WeightEntry>,
       cycleStartDate: LocalDate,
       referenceDate: LocalDate = LocalDate.now()
   ): List<WeightEntry> = entries
       .filter { it.deletedAtMillis == null && !it.date.isBefore(cycleStartDate) && !it.date.isAfter(referenceDate) }
       .sortedBy { it.date }
   ```
3. **Day-Over-Day Delta Engine:**
   ```kotlin
   data class DayOverDayDelta(
       val deltaKg: Double,
       val priorDate: LocalDate,
       val currentDate: LocalDate,
       val isConsecutive: Boolean
   )

   fun computeDayOverDayDelta(
       entries: List<WeightEntry>,
       cycleStartDate: LocalDate,
       referenceDate: LocalDate = LocalDate.now()
   ): DayOverDayDelta? {
       val active = getActiveWeightEntries(entries, cycleStartDate, referenceDate)
       if (active.size < 2) return null
       val latest = active.last()
       val prior = active[active.size - 2]
       return DayOverDayDelta(
           deltaKg = latest.weightKg - prior.weightKg,
           priorDate = prior.date,
           currentDate = latest.date,
           isConsecutive = prior.date == latest.date.minusDays(1)
       )
   }
   ```
4. **Calendar-Strict Consecutive Drop Streak:**
   ```kotlin
   fun computeWeightDecreasingStreak(
       entries: List<WeightEntry>,
       cycleStartDate: LocalDate,
       referenceDate: LocalDate = LocalDate.now(),
       noiseThresholdKg: Double = NOISE_THRESHOLD_KG
   ): Int {
       val active = getActiveWeightEntries(entries, cycleStartDate, referenceDate)
       if (active.size < 2) return 0
       if (active.last().date < referenceDate.minusDays(1)) return 0 // Stale recency guard

       var streak = 0
       for (i in active.indices.reversed()) {
           if (i == 0) break
           val current = active[i]
           val previous = active[i - 1]
           val isConsecutiveDay = previous.date == current.date.minusDays(1)
           val isMeaningfulDrop = (previous.weightKg - current.weightKg) > noiseThresholdKg
           if (isConsecutiveDay && isMeaningfulDrop) {
               streak++
           } else {
               break
           }
       }
       return streak
   }
   ```
5. **7-Day Pace Trend Comparison Engine:**
   ```kotlin
   data class PaceComparisonResult(
       val currentRateKgPerWeek: Double,
       val priorRateKgPerWeek: Double,
       val deltaRateKgPerWeek: Double,
       val percentChange: Double?, // null when not loss-to-loss or |priorRate| < 0.1
       val isAcceleratingDeficit: Boolean,
       val priorDate: LocalDate,
       val isConsecutive: Boolean
   )

   fun compute7DayPaceComparison(
       entries: List<WeightEntry>,
       cycleStartDate: LocalDate,
       referenceDate: LocalDate = LocalDate.now(),
       minPercentageDenominatorKgPerWeek: Double = 0.1
   ): PaceComparisonResult? {
       val active = getActiveWeightEntries(entries, cycleStartDate, referenceDate)
       if (active.size < 2) return null
       val latest = active.last()
       val prior = active[active.size - 2]

       val currentPace = compute7DayPace(active, cycleStartDate, latest.date) ?: return null
       val priorPace = compute7DayPace(active, cycleStartDate, prior.date) ?: return null

       val currentRate = currentPace.rateKgPerWeek
       val priorRate = priorPace.rateKgPerWeek
       val deltaRate = currentRate - priorRate

       // Strict loss-to-loss condition: both rates must be negative (deficit)
       val isLossToLoss = currentRate < 0.0 && priorRate < 0.0
       val isAcceleratingDeficit = isLossToLoss && (-currentRate) > (-priorRate)

       val percentChange = if (isLossToLoss && kotlin.math.abs(priorRate) >= minPercentageDenominatorKgPerWeek) {
           ((kotlin.math.abs(currentRate) - kotlin.math.abs(priorRate)) / kotlin.math.abs(priorRate)) * 100.0
       } else {
           null
       }

       return PaceComparisonResult(
           currentRateKgPerWeek = currentRate,
           priorRateKgPerWeek = priorRate,
           deltaRateKgPerWeek = deltaRate,
           percentChange = percentChange,
           isAcceleratingDeficit = isAcceleratingDeficit,
           priorDate = prior.date,
           isConsecutive = prior.date == latest.date.minusDays(1)
       )
   }
   ```
6. **Pure Presentation Formatters:**
   - Standard domain units (`kg` and `kg/wk`) converted strictly inside formatters via `WeightAnalytics.kgToLbs` and `String.format(Locale.US, "%.1f", ...)`.
   - `formatCurrentWeightSubtitle(delta, weightUnit, referenceDate)` formats delta with date prefix if stale, `vs yesterday` or `vs <date>`, and `▼`/`▲`/`0.0`.
   - `format7DayPaceSubtitle(comparison, weightUnit, referenceDate)` formats acceleration percentage (`14% faster vs yesterday`), deceleration (`25% slower vs yesterday`), unchanged pace (`Pace unchanged` when $|\Delta| < \text{PACE\_NOISE\_THRESHOLD\_KG\_PER\_WEEK}$), absolute rate delta for gain/small denominator (`+0.3 kg/wk vs yesterday`), or baseline fallback (`Baseline 7d pace`).

---

### 3. Acceptance Criteria & Test Matrix (Given-When-Then)

#### Scenario 1: Hero Card Layout & Visual Hierarchy (Worst-Case Automated Gate)
* **Given** a 360dp narrow viewport with font scaling set to 1.3× in `WeightProgressionCardComposeTest.kt`, imperial units (`lbs`), `startWeightKg = 77.8`, `goalWeightKg = 70.0`, `currentWeightKg = 76.7`, stale latest weigh-in date prefix, and a 3-day drop streak pill visible.
* **When** `WeightProgressionHeroCard` is composed and evaluated via `SemanticsActions.GetTextLayoutResult`.
* **Then**:
  - The START metric label (`"START 171.5 lbs"`) and date subtext (`"(28 Sep)"`) each report `lineCount == 1` and `!hasVisualOverflow`.
  - The GOAL metric label (`"GOAL 154.3 lbs"`) and remaining subtext (`"(-14.8 lbs left)"`) each report `lineCount == 1` and `!hasVisualOverflow`.
  - The delta pill (`"5 Oct · ▼ -0.7 lbs vs 4 Oct"`) and streak pill (`"📉 3-day streak"`) each report `lineCount == 1` and `!hasVisualOverflow` (wrapping cleanly onto separate lines within `FlowRow` if necessary).
  - The 7-day pace value (`"▼ -1.8 lbs/wk"`) and 14-day pace value (`"▼ -1.5 lbs/wk"`) each report `lineCount == 1` and `!hasVisualOverflow` (stepping down to `titleSmall` if needed).
  - Both pace chips share equal measured height (`IntrinsicSize.Max`).

#### Scenario 2: Consecutive Daily Weight Drop & Drop Streak
* **Given** weigh-ins on `2026-10-04` (76.8 kg), `2026-10-05` (76.5 kg), `2026-10-06` (76.2 kg), and `2026-10-07` (76.0 kg).
* **When** evaluated on `2026-10-07` with `Locale.US`.
* **Then**:
  - `computeDayOverDayDelta` returns $\Delta = -0.2\text{ kg}$, `isConsecutive = true`.
  - `computeWeightDecreasingStreak` returns `3` (3 consecutive decreasing transitions).
  - `formatCurrentWeightSubtitle` returns `"▼ -0.2 kg vs yesterday"`.
  - Streak pill renders `"📉 3-day streak"`.

#### Scenario 3: Weight Gain Resets Decreasing Streak
* **Given** weigh-ins on `2026-10-06` (75.0 kg) and `2026-10-07` (76.5 kg, +1.5 kg scale jump).
* **When** evaluated on `2026-10-07` with `Locale.US`.
* **Then**:
  - `computeDayOverDayDelta` returns $\Delta = +1.5\text{ kg}$, `isConsecutive = true`.
  - `computeWeightDecreasingStreak` returns `0`.
  - `formatCurrentWeightSubtitle` returns `"▲ +1.5 kg vs yesterday"`.
  - Streak pill is hidden (`dropStreakDays < 2`).

#### Scenario 4: Non-Consecutive Weigh-in Logging Gap
* **Given** weigh-ins on `2026-10-03` (76.6 kg) and `2026-10-07` (76.0 kg) with no weigh-ins between Oct 4 and Oct 6.
* **When** evaluated on `2026-10-07` with `Locale.US`.
* **Then**:
  - `computeDayOverDayDelta` returns $\Delta = -0.6\text{ kg}$, `isConsecutive = false`.
  - `computeWeightDecreasingStreak` returns `0` (calendar gap breaks streak).
  - `formatCurrentWeightSubtitle` returns `"▼ -0.6 kg vs 3 Oct"`.

#### Scenario 5: Stale Weigh-in Date Preservation
* **Given** latest weigh-in on `2026-10-05` (76.2 kg) and prior on `2026-10-04` (76.5 kg).
* **When** evaluated on `2026-10-07` (athlete has not logged weight today) with `Locale.US`.
* **Then**:
  - `formatCurrentWeightSubtitle` returns `"5 Oct · ▼ -0.3 kg vs 4 Oct"`.

#### Scenario 6: Neutral Delta within Noise Deadband
* **Given** weigh-ins on `2026-10-06` (76.02 kg) and `2026-10-07` (76.00 kg, $\Delta = -0.02\text{ kg}$).
* **When** evaluated on `2026-10-07` with `Locale.US`.
* **Then**:
  - `computeWeightDecreasingStreak` returns `0` (noise fluctuation $\le 0.05\text{ kg}$ does not advance streak).
  - `formatCurrentWeightSubtitle` returns `"0.0 kg vs yesterday"`.

#### Scenario 7: 7-Day Pace Accelerating Deficit (Faster Loss)
* **Given** 7-day pace on Oct 6 of $-0.7\text{ kg/wk}$ and on Oct 7 of $-0.8\text{ kg/wk}$.
* **When** evaluated on `2026-10-07` with `Locale.US`.
* **Then**:
  - `percentChange` returns $+14.3\%$ ($\pm 0.1$), `isAcceleratingDeficit = true`.
  - `format7DayPaceSubtitle` returns `"14% faster vs yesterday"`.
  - 7-day pace text starts with `"▼ "` (e.g. `"▼ -0.8 kg/wk"`) and renders in `MaterialTheme.colorScheme.tertiary`.

#### Scenario 8: 7-Day Pace Decelerating Deficit (Slower Loss)
* **Given** 7-day pace on Oct 6 of $-0.8\text{ kg/wk}$ and on Oct 7 of $-0.6\text{ kg/wk}$.
* **When** evaluated on `2026-10-07` with `Locale.US`.
* **Then**:
  - `percentChange` returns $-25.0\%$ ($\pm 0.1$), `isAcceleratingDeficit = false`.
  - `format7DayPaceSubtitle` returns `"25% slower vs yesterday"`.

#### Scenario 9a: 7-Day Pace Unchanged
* **Given** prior 7-day pace of $-0.70\text{ kg/wk}$ and current of $-0.72\text{ kg/wk}$ ($|\Delta| < 0.05\text{ kg/wk}$) on consecutive days ending today.
* **When** evaluated with `Locale.US`.
* **Then**:
  - `format7DayPaceSubtitle` returns `"Pace unchanged"`.

#### Scenario 9b: 7-Day Pace with Stale Weigh-in Date
* **Given** latest weigh-in on Oct 6 (pace $-0.8\text{ kg/wk}$) and prior on Oct 5 (pace $-0.6\text{ kg/wk}$), evaluated on `2026-10-07`.
* **When** evaluated with `Locale.US`.
* **Then**:
  - `percentChange` returns $+33.3\%$ ($\pm 0.1$), `isAcceleratingDeficit = true`.
  - `format7DayPaceSubtitle` returns `"33% faster vs 5 Oct"`.

#### Scenario 10: 7-Day Pace Near-Zero Baseline Guardrail
* **Given** prior 7-day pace of $-0.05\text{ kg/wk}$ ($< 0.1\text{ kg/wk}$) and current of $-0.25\text{ kg/wk}$ on consecutive days ending today.
* **When** evaluated with `Locale.US`.
* **Then**:
  - `percentChange` is `null` (suppresses explosive $+400\%$ output).
  - `format7DayPaceSubtitle` displays absolute rate delta: `"-0.2 kg/wk vs yesterday"`.

#### Scenario 11: 7-Day Pace Weight Gain Trajectory
* **Given** prior 7-day pace of $+0.5\text{ kg/wk}$ and current of $+0.8\text{ kg/wk}$ (accelerating gain) on consecutive days ending today.
* **When** evaluated with `Locale.US`.
* **Then**:
  - `percentChange` is `null` (suppresses inverted negative deficit math).
  - `isAcceleratingDeficit` returns `false`.
  - `format7DayPaceSubtitle` displays absolute rate delta: `"+0.3 kg/wk vs yesterday"`.
  - 7-day pace text starts with `"▲ "` (e.g. `"▲ +0.8 kg/wk"`) and renders in `MaterialTheme.colorScheme.error`.

#### Scenario 12a: Single Weigh-in Cold Start
* **Given** a new cycle with only 1 weigh-in logged.
* **When** evaluated.
* **Then**:
  - `computeDayOverDayDelta` returns `null`.
  - `computeWeightDecreasingStreak` returns `0`.
  - `formatCurrentWeightSubtitle` returns `"First weigh-in"` (defensive formatter fallback; pill suppressed in UI).
  - `format7DayPaceSubtitle` returns `"Baseline 7d pace"`.

#### Scenario 12b: Initial Week Elapsed History (< 7 Days)
* **Given** a new cycle with 2 weigh-ins logged 1 day apart (e.g. Oct 1: 76.5 kg, Oct 2: 76.2 kg).
* **When** evaluated on Oct 2 with `Locale.US`.
* **Then**:
  - `computeDayOverDayDelta` returns $\Delta = -0.3\text{ kg}$, `isConsecutive = true`.
  - `formatCurrentWeightSubtitle` returns `"▼ -0.3 kg vs yesterday"`.
  - `format7DayPaceSubtitle` returns `"Baseline 7d pace"` (because rolling pace requires $\ge 7$ elapsed days).

#### Scenario 13: Stale Drop Streak Reset
* **Given** consecutive daily drops logged on `2026-10-03` (76.8 kg), `2026-10-04` (76.5 kg), and `2026-10-05` (76.2 kg), with no weigh-ins on Oct 6 or Oct 7.
* **When** evaluated on `2026-10-07` (`referenceDate`).
* **Then**:
  - `computeWeightDecreasingStreak` returns `0` (streak is stale because latest entry is older than yesterday), hiding the streak pill.
  - When evaluated on `2026-10-06`, it returns `2`.

#### Scenario 14: Slowing Weight Gain Deficit Acceleration Guard
* **Given** prior 7-day pace of $+0.5\text{ kg/wk}$ (gaining) and current 7-day pace of $+0.2\text{ kg/wk}$ (gaining more slowly).
* **When** evaluated on `2026-10-07` with `Locale.US`.
* **Then**:
  - `percentChange` returns `null`.
  - `isAcceleratingDeficit` returns `false` (slowing gain is NOT an accelerating deficit).
  - `format7DayPaceSubtitle` renders `"-0.3 kg/wk vs yesterday"` with error styling, not tertiary.

#### Scenario 15: 14-Day Pace Anchor Window Boundary
* **Given** daily weigh-ins logged from Oct 1 to Oct 14 (14 daily entries, spanning 13 elapsed days).
* **When** evaluated on Oct 14.
* **Then**:
  - `compute14DayPace` returns `null`.
  - 14-day subtitle displays `"Needs a weigh-in from 14+ days ago"`.
  - When a 15th weigh-in is added on Oct 15 (spanning 14 elapsed days), `compute14DayPace` returns a non-null `PaceRateResult` labelled `/wk`.

#### Scenario 16: Null Goal Weight Graceful Degradation
* **Given** a cycle with `startWeightKg = 80.0` and `goalWeightKg = null`.
* **When** `WeightProgressionHeroCard` is rendered.
* **Then**:
  - Goal label displays `"GOAL: No target"`.
  - `LinearProgressIndicator` track and percentage to goal are hidden.

#### Scenario 16b: Null Starting Weight Fallback to First Logged Entry
* **Given** a cycle with `cycle.startingWeightKg = null` and first logged weigh-in on `2026-10-01` of 78.5 kg (`activeEntries.firstOrNull()`).
* **When** `WeightProgressionHeroCard` is rendered.
* **Then**:
  - START metric label displays `"START 78.5 kg"`.
  - START date subtext displays `"(1 Oct)"`.
  - Progress track and percentage are calculated using 78.5 kg as starting baseline.

#### Scenario 17: Imperial Units Lbs Mode
* **Given** `weightUnit = "lbs"`, `startWeightKg = 77.8`, `goalWeightKg = 70.0`, `currentWeightKg = 76.1`, and `deltaKg = -0.3`.
* **When** rendered with `Locale.US`.
* **Then**:
  - Current weight displays `"167.8 lbs"`.
  - Delta pill displays `"▼ -0.7 lbs vs yesterday"`.
  - Start weight displays `"171.5 lbs"`.
  - Goal weight displays `"154.3 lbs"`.
  - Pace cards display rates in `lbs/wk`.

---

### 4. Implementation Tasks (Pattern A - Standalone Task)

* **Task 1 (Weight Progression Hero Card Redesign with Delta Pill, Drop Streak, and Weekly Pace Acceleration):**
  - In [`FatLossAnalytics.kt`](file:///C:/Users/rogal/workspaces/ws-gym/crosstrainingapp/app/src/main/java/com/fractanomics/crosstraining/data/analytics/FatLossAnalytics.kt):
    - Declare constants `NOISE_THRESHOLD_KG = 0.05` and `PACE_NOISE_THRESHOLD_KG_PER_WEEK = 0.05` inside `object FatLossAnalytics`.
    - Implement `getActiveWeightEntries`, `DayOverDayDelta`, `computeDayOverDayDelta`, `computeWeightDecreasingStreak`, `PaceComparisonResult`, `compute7DayPaceComparison`, `formatCurrentWeightSubtitle`, and `format7DayPaceSubtitle` inside `object FatLossAnalytics`.
  - In [`FatLossAnalyticsTest.kt`](file:///C:/Users/rogal/workspaces/ws-gym/crosstrainingapp/app/src/test/java/com/fractanomics/crosstraining/data/analytics/FatLossAnalyticsTest.kt):
    - Add unit tests covering Scenarios 2–15 with `Locale.setDefault(Locale.US)` invoking `FatLossAnalytics.<function>`.
    - Add boundary test for `dropStreakDays = 1` verifying streak counter increments while UI pill remains hidden.
  - In [`ProgressScreen.kt`](file:///C:/Users/rogal/workspaces/ws-gym/crosstrainingapp/app/src/main/java/com/fractanomics/crosstraining/ui/screens/ProgressScreen.kt):
    - Retain `WeightProgressionCard` container composable with `CycleType.FAT_LOSS_BODYBUILDING` guard, zero-entry empty state, dynamic title, and `ForecastBanner`.
    - Wire starting weight fallback (`val startWeightKg = cycle.startingWeightKg ?: activeEntries.firstOrNull()?.weightKg`) and date subtitle fallback.
    - Wire analytics via `FatLossAnalytics.<function>`.
    - Implement `WeightProgressionHeroCard` presenter composable with two-line START/GOAL anchor columns, `FlowRow` pill layout, and `paceTrendStyle` helper prefixing `formatDisplay` with `▼`/`▲`/`—` icons and mapping `PaceTrend` to semantic theme colors (`tertiary` for loss, `error` for gain, `onSurfaceVariant` for neutral/null).
    - Add `titleSmall` step-down fallback for pace chip values under 1.3× font scaling.
    - Delete or route obsolete private `PaceChip` through `paceTrendStyle`.
  - In [`WeightProgressionCardComposeTest.kt`](file:///C:/Users/rogal/workspaces/ws-gym/crosstrainingapp/app/src/androidTest/java/com/fractanomics/crosstraining/ui/screens/WeightProgressionCardComposeTest.kt):
    - Add Scenario 1 worst-case `GetTextLayoutResult` test asserting `lineCount == 1` and `!hasVisualOverflow` on START line, GOAL line, delta pill, streak pill, and both pace text values under 360dp viewport, 1.3× font scale, lbs, and stale date.
    - Add Scenarios 7 and 11 pace trend icon and color assertions.
    - Add Scenario 16 (null goal degradation), Scenario 16b (null start fallback), and Scenario 17 (imperial lbs conversion) rendering tests.
    - Preserve existing Scenarios F, G, H; update Scenario I for dynamic cycle title.
  - In [`scripts/run-e2e-tests.ps1`](file:///C:/Users/rogal/workspaces/ws-gym/crosstrainingapp/scripts/run-e2e-tests.ps1):
    - Capture updated visual artifacts of `WeightProgressionHeroCard` and sync to `docs/screenshots/`.
  - In [`CHANGELOG.md`](file:///C:/Users/rogal/workspaces/ws-gym/crosstrainingapp/CHANGELOG.md):
    - Document Weight Progression Hero card redesign, daily weight delta pill, drop streak, and weekly pace acceleration tracking under `## [Unreleased]`.