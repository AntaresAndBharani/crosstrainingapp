## 🎯 Final Decision Plan & User Story Specification

### Title: feat: 3-pillar current weight row and same-weekday pace comparisons

### 📖 User Story
**As an** athlete tracking a recomposition or fat loss cycle,  
**I want** to see a unified 3-pillar current weight row (same weekday last week, latest/today weight, progressive 14-day moving average) and have my 7-day and 14-day paces compared against the same weekday of the previous week,  
**So that** I can accurately assess body composition progress without day-of-week cyclicality noise or daily fluid weight fluctuations.

### Scope Boundary & Non-Goals
- **Display & Domain Analytics Enhancement:** Scoped strictly to [`FatLossAnalytics.kt`](file:///c:/Users/rogal/workspaces/ws-gym/crosstrainingapp/app/src/main/java/com/fractanomics/crosstraining/data/analytics/FatLossAnalytics.kt), [`ProgressScreen.kt`](file:///c:/Users/rogal/workspaces/ws-gym/crosstrainingapp/app/src/main/java/com/fractanomics/crosstraining/ui/screens/ProgressScreen.kt), and accompanying unit/compose tests.
- **Zero Schema Mutations:** No Room database migrations, entity alterations, or DAO query changes.
- **Pace Targets Box Untouched:** `TheoreticalPaceTargetsBox` and `compute7DayPaceTargets` remain untouched; the unrequested `Last week:` rows from the draft mockup are formally removed.
- **Empty State Invariant:** The card's existing empty state when no active in-cycle entries exist remains unchanged.
- **Supersedes Draft Sections:** This specification strictly overrides lines 1–121 (§§2.1, 2.2, 3.1–3.3, 4) in the initial draft.

---

### 1. Architectural Decisions & Domain Contracts

#### 1.1 Anchor Date (`t`) & Center Pillar
- **Anchor Date ($t$):** Defined as `activeEntries.last().date` (the latest in-cycle weigh-in date on or before `referenceDate`).
- **Center Hero Header:** Displays `TODAY` when `t == referenceDate`, and `LATEST` when `t < referenceDate`.
- **Center Hero Value:** `activeEntries.last().weightKg` formatted in `displaySmall` (36sp bold) or `headlineMedium` (28sp) under high font scaling (`fontScale >= 1.25f`).
- **Subtitles & Badges:** Preserves the existing day-over-day delta pill (`formatCurrentWeightSubtitle`, which includes the stale-date prefix when `t < referenceDate`) and the drop-streak pill (`dropStreakDays`).

#### 1.2 Left Reference Pillar (`LAST WEEK (DAY)`)
- **Lookup:** Queries for an exact active in-cycle weigh-in on `t.minusDays(7)`.
- **Header:** `LAST WEEK (${t.minusDays(7).dayOfWeek.getDisplayName(TextStyle.SHORT, Locale.US).uppercase()})` (e.g. `LAST WEEK (TUE)`).
- **Present State:** Displays logged weight in `titleMedium` (18sp semi-bold) with subtitle `(d MMM)` (e.g. `(6 Oct)`).
- **Missing / Out-of-Cycle State:** When unlogged on $t-7\text{d}$ or when $t-7\text{d} < \text{cycleStartDate}$, displays `—` with subtitle `No log`.

#### 1.3 Right Reference Pillar (`{N}D AVG`)
- **Domain Return Type:**
  ```kotlin
  data class ProgressiveAverageResult(
      val averageKg: Double,
      val windowSpanDays: Int,
      val loggedDaysCount: Int
  )
  ```
- **Analytics Function:**
  ```kotlin
  fun computeProgressiveAverage(
      entries: List<WeightEntry>,
      cycleStartDate: LocalDate,
      referenceDate: LocalDate,
      maxWindowDays: Int = 14
  ): ProgressiveAverageResult?
  ```
- **Window & Calculation:** Evaluates active in-cycle entries in `[max(cycleStartDate, t.minusDays(maxWindowDays - 1)), t]`.
  - Let $K = \text{windowEntries.size}$. Returns `null` if $K == 0$.
  - Arithmetic mean: $\bar{W}_N = \frac{1}{K}\sum_{i=1}^K W_i$.
  - Window span: $N = \min(\text{ChronoUnit.DAYS.between}(\text{cycleStartDate}, t).\text{toInt}() + 1, \text{maxWindowDays})$.
- **UI Header & Subtitle:**
  - Header: `{N}D AVG` (e.g. `1D AVG`, `5D AVG`, `14D AVG`).
  - Subtitle: `({K} logs)` (using plural handling so $K = 1$ renders `(1 log)`).

#### 1.4 Same-Weekday Pace Comparisons vs. Last Week
- **Analytics Function:**
  ```kotlin
  fun computePaceComparisonVsLastWeek(
      entries: List<WeightEntry>,
      cycleStartDate: LocalDate,
      windowDays: Int,
      referenceDate: LocalDate
  ): PaceComparisonResult?
  ```
  Evaluates `computePaceRate` at anchor $t$ and at $t.minusDays(7)$ using existing `resolveAnchorEntry` on-or-before tolerance.
  - Sets `isExactDayMatch = (priorPace.currentDate == t.minusDays(7))`.
  - Deprecates and removes both overloads of `compute7DayPaceComparison`.
- **Shared Subtitle Formatter:**
  Applied to both 7-day and 14-day pace columns following [`format7DayPaceSubtitle`](file:///c:/Users/rogal/workspaces/ws-gym/crosstrainingapp/app/src/main/java/com/fractanomics/crosstraining/data/analytics/FatLossAnalytics.kt#L1783-L1812) precedence without directional glyphs:
  1. **Null Comparison:**
     - 7-Day Pace: `"Baseline 7d pace"`
     - 14-Day Pace: if `pace14 == null` $\rightarrow$ `"Needs weigh-in 14+ days ago"`; if `pace14 != null && comparison14 == null` $\rightarrow$ `"Smoothed trend"`.
  2. **Deadband:** $\text{abs}(\text{deltaRate}) < 0.05\text{ kg/wk}$ (`PACE_NOISE_THRESHOLD_KG_PER_WEEK`) $\rightarrow$ `"Pace unchanged"`.
  3. **Loss-to-Loss Percentage:** If `currentRate < 0 && priorRate < 0 && abs(priorRate) >= 0.1`:
     $\rightarrow$ `"$pct% ${if (isAcceleratingDeficit) "faster" else "slower"} loss $vsPart"` (unsigned `$pct%`, e.g. `"15% faster loss vs last Tue"`).
  4. **Signed Rate Delta:**
     $\rightarrow$ `"$sign%.1f $unitLabel $vsPart"` (e.g. `"+2.4 kg/wk vs last Tue"`, `"-0.2 kg/wk vs 5 Oct"`).
  - **Prior Date Descriptor (`vsPart`):**
    - When `isExactDayMatch == true`: `"vs last EEE"` (Locale.US, e.g. `"vs last Tue"`).
    - When resolved within on-or-before tolerance: `"vs d MMM"` (via `shortDateFormatter`, e.g. `"vs 5 Oct"`).

#### 1.5 UI Presentation & Responsiveness ([`ProgressScreen.kt`](file:///c:/Users/rogal/workspaces/ws-gym/crosstrainingapp/app/src/main/java/com/fractanomics/crosstraining/ui/screens/ProgressScreen.kt))
- **3-Pillar Row:** `WeightProgressionHeroCard` renders the three pillars horizontally using equal column weights (`Modifier.weight(1f)`).
- **Pills Layout:** The day-over-day delta pill and drop streak pill render in a centered row below the 3-pillar row to avoid squeezing the center column.
- **Default Parameters:** New parameters on `WeightProgressionHeroCard` (`lastWeekEntry: WeightEntry? = null`, `progressiveAverage: ProgressiveAverageResult? = null`, `paceComparison14d: PaceComparisonResult? = null`) provide default arguments for backward compatibility with existing previews and callers.
- **Responsive Sizing:** Tested and verified on `360dp` viewports at `fontScale = 1.3f` without text clipping or horizontal truncation.

---

### 2. Migration Scope & Affected Files

1. **Production Callers:**
   - [`ProgressScreen.kt` L1105](file:///c:/Users/rogal/workspaces/ws-gym/crosstrainingapp/app/src/main/java/com/fractanomics/crosstraining/ui/screens/ProgressScreen.kt#L1105): Migrate from `compute7DayPaceComparison` to `computePaceComparisonVsLastWeek(entries, cycleStartDate, 7, today)` and add `computePaceComparisonVsLastWeek(entries, cycleStartDate, 14, today)`. Wire 3-pillar data.
2. **Domain Analytics:**
   - [`FatLossAnalytics.kt`](file:///c:/Users/rogal/workspaces/ws-gym/crosstrainingapp/app/src/main/java/com/fractanomics/crosstraining/data/analytics/FatLossAnalytics.kt): Add `ProgressiveAverageResult`, `computeProgressiveAverage`, `computePaceComparisonVsLastWeek`, and shared pace subtitle formatter; remove legacy `compute7DayPaceComparison` overloads.
3. **Unit & Instrumented Test Suites:**
   - [`FatLossAnalyticsTest.kt`](file:///c:/Users/rogal/workspaces/ws-gym/crosstrainingapp/app/src/test/java/com/fractanomics/crosstraining/data/analytics/FatLossAnalyticsTest.kt): Update legacy pace comparison assertions and add unit tests for `computeProgressiveAverage` and same-weekday comparisons.
   - [`WeightProgressionCardTest.kt`](file:///c:/Users/rogal/workspaces/ws-gym/crosstrainingapp/app/src/test/java/com/fractanomics/crosstraining/ui/screens/WeightProgressionCardTest.kt): Add unit tests for 3-pillar subtitle and label resolvers.
   - [`WeightProgressionCardComposeTest.kt`](file:///c:/Users/rogal/workspaces/ws-gym/crosstrainingapp/app/src/androidTest/java/com/fractanomics/crosstraining/ui/screens/WeightProgressionCardComposeTest.kt): Migrate fixture setups to `computePaceComparisonVsLastWeek` and verify 3-pillar rendering at 360dp / fontScale 1.3.

---

### 3. Given-When-Then Acceptance Test Matrix

- **Scenario 1: Cycle Day 1 Start**  
  *Given* an active cycle started today with single weigh-in $W_t = 77.8\text{ kg}$.  
  *When* `computeProgressiveAverage` and pace comparisons are evaluated.  
  *Then* Left Pillar is `—` with `"No log"`, Right Pillar is `1D AVG` with value `77.8 kg` and subtitle `"(1 log)"`, 7-Day Pace subtitle is `"Baseline 7d pace"`, and 14-Day Pace subtitle is `"Needs weigh-in 14+ days ago"`.

- **Scenario 2: Young Cycle Day 5 (Sparse Check-ins)**  
  *Given* an active cycle started 5 days ago with 3 logged entries ($78.0, 77.5, 77.0\text{ kg}$).  
  *When* `computeProgressiveAverage` is evaluated.  
  *Then* returns `ProgressiveAverageResult(averageKg = 77.5, windowSpanDays = 5, loggedDaysCount = 3)`, rendering header `"5D AVG"` and subtitle `"(3 logs)"`.

- **Scenario 3: Mature Cycle Day 21+ with Gaps**  
  *Given* an active cycle with 9 logged entries within the last 14 calendar days $[t-13\text{d}, t]$.  
  *When* `computeProgressiveAverage` is evaluated.  
  *Then* returns `windowSpanDays = 14`, `loggedDaysCount = 9`, rendering header `"14D AVG"` and subtitle `"(9 logs)"`.

- **Scenario 4: Pre-Cycle Entries Exclusion**  
  *Given* weigh-in entries dated prior to `cycleStartDate`.  
  *When* `computeProgressiveAverage` is evaluated.  
  *Then* pre-cycle entries are strictly excluded from both the window span and the logged entries divisor.

- **Scenario 5: Left Pillar Exact Match**  
  *Given* an active entry on $t-7\text{d}$ at $75.0\text{ kg}$ where $t$ is Tuesday.  
  *When* Left Pillar is rendered.  
  *Then* header displays `"LAST WEEK (TUE)"`, value `"75.0 kg"`, and subtitle `"(${t.minusDays(7).format("d MMM")})"`.

- **Scenario 6: Left Pillar Missing**  
  *Given* no weigh-in logged on $t-7\text{d}$.  
  *When* Left Pillar is rendered.  
  *Then* displays `"—"` with subtitle `"No log"`.

- **Scenario 7: Accelerating Deficit (Loss-to-Loss Percentage)**  
  *Given* $P_{7\text{d}}(t) = -0.9\text{ kg/wk}$ and $P_{7\text{d}}(t-7\text{d}) = -0.8\text{ kg/wk}$ with exact Tuesday match.  
  *When* pace comparison subtitle is formatted.  
  *Then* returns `"13% faster loss vs last Tue"` (unsigned, no arrow glyph).

- **Scenario 8: Decelerating Deficit (Loss-to-Loss Percentage)**  
  *Given* $P_{7\text{d}}(t) = -0.7\text{ kg/wk}$ and $P_{7\text{d}}(t-7\text{d}) = -0.8\text{ kg/wk}$ with exact Tuesday match.  
  *When* pace comparison subtitle is formatted.  
  *Then* returns `"13% slower loss vs last Tue"` (unsigned, no arrow glyph).

- **Scenario 9: Rate Delta (Sign Flip / Non-Deficit)**  
  *Given* $P_{7\text{d}}(t) = +0.5\text{ kg/wk}$ and $P_{7\text{d}}(t-7\text{d}) = -0.2\text{ kg/wk}$ with exact Tuesday match.  
  *When* pace comparison subtitle is formatted.  
  *Then* returns `"+0.7 kg/wk vs last Tue"`.

- **Scenario 10: Pace Noise Deadband**  
  *Given* $|P_{7\text{d}}(t) - P_{7\text{d}}(t-7\text{d})| = 0.03\text{ kg/wk} < 0.05\text{ kg/wk}$.  
  *When* pace comparison subtitle is formatted.  
  *Then* returns `"Pace unchanged"`.

- **Scenario 11: Tolerance Lookup Date Formatting**  
  *Given* prior pace resolved to $t-8\text{d}$ (5 Oct) instead of $t-7\text{d}$.  
  *When* pace comparison subtitle is formatted.  
  *Then* subtitle appends `"vs 5 Oct"` (via `shortDateFormatter`), strictly suppressing `"vs last EEE"`.

- **Scenario 12: 14-Day Pace Cadence States**  
  *Given* an active cycle progressing through maturity:  
  *When* 14-day pace card is evaluated:  
  *Then* displays `"Needs weigh-in 14+ days ago"` when $t - \text{cycleStartDate} < 14$; displays `"Smoothed trend"` when $14 \le t - \text{cycleStartDate} < 21$ and prior pace is null; displays formatted same-weekday comparison (e.g. `"-0.2 kg/wk vs last Tue"`) on day 21+.

- **Scenario 13: Stale Weigh-in Header**  
  *Given* latest entry $t < \text{referenceDate}$.  
  *When* Center Pillar is rendered.  
  *Then* header displays `"LATEST"` and current weight subtitle retains existing stale-date prefix.

- **Scenario 14: Imperial Localization**  
  *Given* `weightUnit == "lbs"`.  
  *When* 3 pillars and pace deltas are formatted.  
  *Then* weights format in `lbs`, pace rates format in `lbs/wk`, and percentage changes remain unit-invariant.

- **Scenario 15: Responsive Layout (360dp / fontScale = 1.3f)**  
  *Given* Compose rendering on a 360dp viewport at `fontScale = 1.3f`.  
  *When* `WeightProgressionHeroCard` renders.  
  *Then* all 3 pillars render visibly without horizontal text truncation, clipping, or overlapping.

---

### 4. Implementation Tasks (Pattern A - Standalone Task)

* **Task 1 (3-Pillar Weight Progression Hero Card & Same-Weekday Pace Comparisons):**
  - In [`FatLossAnalytics.kt`](file:///c:/Users/rogal/workspaces/ws-gym/crosstrainingapp/app/src/main/java/com/fractanomics/crosstraining/data/analytics/FatLossAnalytics.kt):
    - Implement `ProgressiveAverageResult` and `computeProgressiveAverage`.
    - Implement `computePaceComparisonVsLastWeek` using `resolveAnchorEntry` tolerance.
    - Remove both legacy `compute7DayPaceComparison` overloads.
    - Implement shared pace subtitle formatter honoring the exact string specifications and fallback states.
  - In [`ProgressScreen.kt`](file:///c:/Users/rogal/workspaces/ws-gym/crosstrainingapp/app/src/main/java/com/fractanomics/crosstraining/ui/screens/ProgressScreen.kt):
    - Refactor `WeightProgressionHeroCard` to render the 3-pillar row (`LAST WEEK (DAY)`, `TODAY`/`LATEST`, `{N}D AVG`).
    - Position day-over-day delta pill and drop-streak pill in a centered sub-row.
    - Connect 7-day and 14-day pace comparisons using `computePaceComparisonVsLastWeek`.
    - Provide default arguments for new composable parameters.
  - In [`FatLossAnalyticsTest.kt`](file:///c:/Users/rogal/workspaces/ws-gym/crosstrainingapp/app/src/test/java/com/fractanomics/crosstraining/data/analytics/FatLossAnalyticsTest.kt):
    - Add comprehensive unit tests covering Scenarios 1–14.
  - In [`WeightProgressionCardTest.kt`](file:///c:/Users/rogal/workspaces/ws-gym/crosstrainingapp/app/src/test/java/com/fractanomics/crosstraining/ui/screens/WeightProgressionCardTest.kt):
    - Add unit tests for 3-pillar label and subtitle resolvers.
  - In [`WeightProgressionCardComposeTest.kt`](file:///c:/Users/rogal/workspaces/ws-gym/crosstrainingapp/app/src/androidTest/java/com/fractanomics/crosstraining/ui/screens/WeightProgressionCardComposeTest.kt):
    - Migrate fixtures and verify Scenario 15 layout on 360dp / fontScale 1.3f.
  - In [`CHANGELOG.md`](file:///c:/Users/rogal/workspaces/ws-gym/crosstrainingapp/CHANGELOG.md):
    - Document the 3-pillar weight row and same-weekday pace comparisons under `## [Unreleased]`.