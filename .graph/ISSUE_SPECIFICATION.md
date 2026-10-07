## 🎯 Final Decision Plan & User Story Specification

### Title: feat: 7-Day average weight for cycle progress and forecast estimation

### 📖 User Story
**As a** fat-loss athlete logging daily weigh-ins during a training cycle,  
**I want** the "Current" weight in the cycle progress card and the arrival forecast engine to use my 7-day rolling average weight,  
**So that** single-day water and sodium weight spikes do not distort my displayed goal deficit or the remaining-distance term of the forecast.

---

### 1. Architectural Design & Technical Formulas

#### 1.1 7-Day Rolling Average Weight Engine (`FatLossAnalytics.kt`)
In [`FatLossAnalytics.kt`](file:///C:/Users/rogal/workspaces/ws-gym/crosstrainingapp/app/src/main/java/com/fractanomics/crosstraining/data/analytics/FatLossAnalytics.kt), introduce pure domain functions:

```kotlin
/**
 * Computes the 7-day Simple Moving Average (SMA) of body weight over the closed
 * calendar interval [referenceDate - 6 days, referenceDate] within the active cycle.
 *
 * Invariants & Guarantees:
 * 1. Filtering: soft-deleted entries (deletedAtMillis != null), pre-cycle entries (date < cycleStartDate),
 *    and future entries (date > referenceDate) are strictly excluded.
 * 2. Uniqueness: WeightEntry.date is the natural @PrimaryKey; per-entry and per-day averages are mathematically identical.
 * 3. Window aggregation: entries whose date is in [referenceDate - 6 days, referenceDate].
 *    If N >= 1, returns the arithmetic mean (sum / count).
 * 4. Cold-start progression:
 *    - Day 1 (N = 1): returns the starting weigh-in exactly without null gaps.
 *    - Days 2–6 (N = 2..6): arithmetic mean over elapsed days in cycle.
 *    - Days 7+ (N >= 7): true 7-day rolling average over [referenceDate - 6, referenceDate].
 * 5. Domain safety fallback: if no entries exist in [referenceDate - 6, referenceDate] (e.g. logging gap > 7 days),
 *    falls back to activeEntries.last().weightKg. Returns null if active cycle entries are empty.
 */
fun computeCurrent7DayAverageWeight(
    entries: List<WeightEntry>,
    cycleStartDate: LocalDate,
    referenceDate: LocalDate = LocalDate.now()
): Double? {
    val activeEntries = entries
        .filter { it.deletedAtMillis == null && !it.date.isBefore(cycleStartDate) && !it.date.isAfter(referenceDate) }
        .sortedBy { it.date }

    if (activeEntries.isEmpty()) return null

    val windowStart = referenceDate.minusDays(6)
    val windowEntries = activeEntries.filter { !it.date.isBefore(windowStart) }

    return if (windowEntries.isNotEmpty()) {
        windowEntries.map { it.weightKg }.average()
    } else {
        activeEntries.last().weightKg
    }
}

fun computeCurrent7DayAverageWeight(
    entries: List<WeightEntry>,
    cycle: Cycle,
    referenceDate: LocalDate = LocalDate.now()
): Double? = computeCurrent7DayAverageWeight(
    entries = entries,
    cycleStartDate = cycle.startDate,
    referenceDate = referenceDate
)
```

#### 1.2 UI Integration in `ProgressScreen.kt` (`WeightProgressionCard`)
In [`ProgressScreen.kt:1073-1135`](file:///C:/Users/rogal/workspaces/ws-gym/crosstrainingapp/app/src/main/java/com/fractanomics/crosstraining/ui/screens/ProgressScreen.kt#L1073-L1135):
1. Compute the stabilized current weight:
   ```kotlin
   val latestEntry = activeEntries.lastOrNull()
   val current7DayAvgKg = remember(weightEntries, activeEntries, cycle, today) {
       FatLossAnalytics.computeCurrent7DayAverageWeight(
           entries = weightEntries,
           cycle = cycle,
           referenceDate = latestEntry?.date ?: today
       )
   }
   val currentKg = current7DayAvgKg ?: latestEntry?.weightKg
   ```
2. **Current KPI Card:**
   - Displays `currentDisplay` (e.g. `"75.2 kg"` or `"165.8 lbs"`).
   - Subtitle: `sub = { SubText("7d avg • ${latestEntry?.date?.formatShort() ?: "—"}") }` (e.g. `"7d avg • 7 Oct"`), giving clear visual transparency that the metric represents a rolling average.
3. **Goal KPI Card:**
   - Computes `val remaining = currentDisplay - targetDisplay` dynamically from `currentDisplay`, presenting a stable remaining deficit (e.g. `"8.2 kg left"` instead of fluctuating to `"9.5 kg left"`).

#### 1.3 Engine Integration in `FatLossAnalytics.computePaceForecast`
In [`FatLossAnalytics.computePaceForecast`](file:///C:/Users/rogal/workspaces/ws-gym/crosstrainingapp/app/src/main/java/com/fractanomics/crosstraining/data/analytics/FatLossAnalytics.kt#L1133-L1235):
1. **Stabilized Current Weight Resolution:**
   ```kotlin
   val newest = activeEntries.last()
   val currentWeight = computeCurrent7DayAverageWeight(
       entries = activeEntries,
       cycleStartDate = cycleStartDate,
       referenceDate = newest.date
   ) ?: newest.weightKg
   ```
2. **GoalReached Gating on 7-Day Average:**
   `if (targetWeightKg != null && targetWeightKg > 0.0 && currentWeight <= targetWeightKg)`
   Guarantees that `GoalReached` triggers when the athlete's 7-day average reaches the target, preventing false triggers on transient dehydration mornings.
3. **Velocity Basis Independence (Line 1218 Edit):**
   ```kotlin
   val vActive = if (anchor != null && elapsed > 0) {
       ((anchor.weightKg - newest.weightKg) / elapsed.toDouble()) * 7.0
   } else {
       0.0
   }
   ```
   Requires changing line 1218 from `anchor.weightKg - currentWeight` to `anchor.weightKg - newest.weightKg`. This guarantees $V_{\text{active}} = -R_{\text{pace}}$ holds strictly against the anchor weigh-in, preserving contract symmetry with the pace chips without introducing a divergent second velocity basis.
4. **Estimated Weeks to Goal:**
   $$\text{remainingKg} = \text{currentWeight} - \text{targetWeightKg}$$
   $$\text{rawWeeks} = \frac{\text{remainingKg}}{V_{\text{active}}}$$

---

### 2. Acceptance Criteria & Test Matrix (Given-When-Then)

#### Scenario 1: Daily Water Spike Smoothing (Synthetic Reproduction Fixture)
* **Given** a fat loss cycle starting on `2026-09-28` with target weight `67.0 kg`.
* **And** weight entries:
  - `2026-09-28`: `77.8 kg` (starting weigh-in, anchor)
  - `2026-10-01` to `2026-10-06` (6 days): daily weigh-ins at `75.0 kg`.
  - `2026-10-07`: daily weigh-in spike at `76.5 kg` (+1.5 kg water jump).
* **When** `computeCurrent7DayAverageWeight` and `computePaceForecast` are evaluated on `2026-10-07`.
* **Then**:
  - Span days = 9 ($\ge 7$, passing the sampling guard).
  - The 7-day average weight is $\frac{6 \times 75.0 + 76.5}{7} = \frac{526.5}{7} \approx 75.214\text{ kg}$ ($\pm 1e-4$).
  - The "Current" card displays `75.2 kg`.
  - The "Goal" card displays `8.2 kg left`.
  - $V_{\text{active}} \approx 1.011\text{ kg/wk}$, $\text{remainingKg} = 8.214\text{ kg}$, and `rawWeeks` $\approx 8.12\text{ weeks}$ ($\pm 0.05$).
  - `rawWeeks` (8.12) is strictly less than the unmitigated raw baseline ($\frac{76.5 - 67.0}{1.011} \approx 9.40\text{ weeks}$).

#### Scenario 2: Cold Start on Day 1 and Day 2
* **Given** a new cycle starting on `2026-10-01` with initial entry `80.0 kg` on `2026-10-01`.
* **When** `computeCurrent7DayAverageWeight` is evaluated on `2026-10-01`.
* **Then** returns `80.0 kg` (N = 1).
* **When** an entry `79.0 kg` is added on `2026-10-02`.
* **Then** returns `79.5 kg` ($\frac{80.0 + 79.0}{2}$, N = 2).

#### Scenario 3: Logging Inactivity (7-Day Window Gap Fallback)
* **Given** a cycle starting on `2026-09-01` with weigh-ins on `2026-09-01` (80.0 kg) and `2026-09-02` (79.8 kg).
* **And** no weigh-ins between `2026-09-03` and `2026-09-15`.
* **When** `computeCurrent7DayAverageWeight` is evaluated on `2026-09-15` (window `[2026-09-09, 2026-09-15]` is empty).
* **Then** returns `79.8 kg` (falls back to latest entry within cycle).

#### Scenario 4: Imperial Unit Conversion Consistency
* **Given** `weightUnit == "lbs"`, target weight `67.0 kg` ($147.7\text{ lbs}$), and metric average weight `75.214 kg`.
* **When** rendered in `WeightProgressionCard`.
* **Then** `currentDisplay` is converted via `WeightAnalytics.kgToLbs` ($165.8\text{ lbs}$) and remaining displays `"18.1 lbs left"`.

#### Scenario 5: Soft Deletions and Pre-Cycle Entries Ignored
* **Given** an entry on `2026-10-03` marked soft-deleted (`deletedAtMillis != null`) and an entry prior to `cycleStartDate`.
* **When** `computeCurrent7DayAverageWeight` is evaluated.
* **Then** both entries are completely excluded from the 7-day average calculation.

#### Scenario 6: GoalReached Gating on 7-Day Average
* **Given** a target weight of `67.0 kg`, six daily entries at `67.5 kg`, and a newest entry on day 7 at `66.8 kg` (7-day average = `67.4 kg` $> 67.0\text{ kg}$).
* **When** `computePaceForecast` is evaluated.
* **Then** the forecast does NOT return `GoalReached`, and the Goal card displays `"0.4 kg left"` rather than `"Achieved"`.
* **When** subsequent weigh-ins bring the 7-day average to $\le 67.0\text{ kg}$.
* **Then** `computePaceForecast` returns `PaceForecast.GoalReached` and the Goal card displays `"Achieved"`.

---

### 3. Implementation Tasks (Pattern A - Standalone Task)

* **Task 1 (7-Day Average Weight for Cycle Progress and Forecast Estimation):**
  - In [`FatLossAnalytics.kt`](file:///C:/Users/rogal/workspaces/ws-gym/crosstrainingapp/app/src/main/java/com/fractanomics/crosstraining/data/analytics/FatLossAnalytics.kt):
    - Implement `computeCurrent7DayAverageWeight` domain function over `[referenceDate - 6 days, referenceDate]` with `Cycle` overload.
    - Update `computePaceForecast` to resolve `currentWeight` via `computeCurrent7DayAverageWeight(activeEntries, cycleStartDate, newest.date) ?: newest.weightKg`.
    - Update line 1218 to use `anchor.weightKg - newest.weightKg` preserving raw-basis velocity against anchor.
  - In [`FatLossAnalyticsTest.kt`](file:///C:/Users/rogal/workspaces/ws-gym/crosstrainingapp/app/src/test/java/com/fractanomics/crosstraining/data/analytics/FatLossAnalyticsTest.kt):
    - Add unit tests for Scenarios 1–3, 5, and 6.
  - In [`ProgressScreen.kt`](file:///C:/Users/rogal/workspaces/ws-gym/crosstrainingapp/app/src/main/java/com/fractanomics/crosstraining/ui/screens/ProgressScreen.kt):
    - In `WeightProgressionCard`, compute `current7DayAvgKg` using `remember(weightEntries, activeEntries, cycle, today)`.
    - Set `currentKg = current7DayAvgKg ?: latestEntry?.weightKg`.
    - Format Current card subtitle as `"7d avg • ${latestEntry?.date?.formatShort() ?: "—"}"`.
  - In [`WeightProgressionCardTest.kt`](file:///C:/Users/rogal/workspaces/ws-gym/crosstrainingapp/app/src/test/java/com/fractanomics/crosstraining/ui/screens/WeightProgressionCardTest.kt):
    - Update/add UI logic unit tests verifying 7-day average rendering on Current card and Goal remaining text.
  - In [`CHANGELOG.md`](file:///C:/Users/rogal/workspaces/ws-gym/crosstrainingapp/CHANGELOG.md):
    - Add entry under `## [Unreleased]` describing 7-day average weight smoothing for progress and forecasting.