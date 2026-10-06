## 🎯 Final Decision Plan & User Story Specification

### Title: feat: Fix fat loss forecast pacing hierarchy and KPI card text truncation

### 📖 User Story
**As a** fat-loss athlete in the early weeks of a cut cycle,  
**I want** the arrival forecast banner to match the weekly pace chip displayed directly above it,  
**So that** my projected goal completion date is accurate, reliable, and mathematically consistent.

---

### 1. Architectural Design & Formulas

#### 1.1 3-Tier Velocity Resolution Hierarchy (`FatLossAnalytics.kt`)
In [`FatLossAnalytics.computePaceForecast`](file:///C:/Users/rogal/workspaces/ws-gym/crosstrainingapp/app/src/main/java/com/fractanomics/crosstraining/data/analytics/FatLossAnalytics.kt#L1133-L1235) (primary overload accepting `cycleStartDate`), update the velocity resolution logic to evaluate three sequential tiers:

```kotlin
// Tier 1: Primary 14-day rolling anchor
val anchor14 = resolveAnchorEntry(
    entries = activeEntries,
    targetCutoffDate = newest.date.minusDays(14),
    cycleStartDate = cycleStartDate
)

// Tier 2: Secondary 7-day rolling anchor (used during cycle days 7–13)
val anchor7 = if (anchor14 == null) {
    resolveAnchorEntry(
        entries = activeEntries,
        targetCutoffDate = newest.date.minusDays(7),
        cycleStartDate = cycleStartDate
    )
} else null

// Tier 3: Tertiary cycle-wide anchor (defensive fallback)
val (anchor, elapsed) = when {
    anchor14 != null -> {
        val d = ChronoUnit.DAYS.between(anchor14.date, newest.date)
        if (d > 0) anchor14 to d else null to 0L
    }
    anchor7 != null -> {
        val d = ChronoUnit.DAYS.between(anchor7.date, newest.date)
        if (d > 0) anchor7 to d else null to 0L
    }
    else -> {
        val d = ChronoUnit.DAYS.between(oldest.date, newest.date)
        if (d > 0) oldest to d else null to 0L
    }
}

val vActive = if (anchor != null && elapsed > 0) {
    ((anchor.weightKg - currentWeight) / elapsed.toDouble()) * 7.0
} else {
    0.0
}
```

* **Equivalence Invariant:** When Tier 2 ($V_{7\text{d}}$) is selected, $V_{\text{active}} = -R_{7\text{d}}$ (unrounded), ensuring direct mathematical identity between the velocity basis and the 7-day pace chip displayed to the athlete.
* **Defensive Tier 3 Status:** Once the sampling guard (`spanDays >= 7`) passes, `oldest.date <= newest.date.minusDays(7)` is mathematically guaranteed to be true, so `anchor7` will always resolve to an entry (at least `oldest`). Tier 3 is maintained strictly as an unreachable defensive null-coalescing guard with zero behavioral regression risk.
* **Overload Isolation:** Algorithmic modifications are strictly confined to the primary `cycleStartDate` overload at line 1133. The `Cycle` overload (line 1239) and direct velocity overload (line 1256) remain untouched.
* **Out-of-Scope Clarification:** From cycle day 14 onward, initial water shedding naturally enters the 14-day calculation window by design as `anchor14` resolves to starting weigh-ins; long-term projections intentionally track the 14-day pace chip.

#### 1.2 Scoped UI Anti-Truncation (`ProgressScreen.kt`)
1. In `RowScope.KpiCard` ([`ProgressScreen.kt:1905-1933`](file:///C:/Users/rogal/workspaces/ws-gym/crosstrainingapp/app/src/main/java/com/fractanomics/crosstraining/ui/screens/ProgressScreen.kt#L1905-L1933)), add optional parameters while strictly preserving existing defaults to protect all other 14 call sites:
   ```kotlin
   @Composable
   private fun RowScope.KpiCard(
       label: String,
       value: String,
       valueStyle: TextStyle = MaterialTheme.typography.titleLarge,
       contentPadding: PaddingValues = PaddingValues(12.dp),
       sub: @Composable () -> Unit
   )
   ```
2. In `WeightProgressionCard` ([`ProgressScreen.kt:1108-1122`](file:///C:/Users/rogal/workspaces/ws-gym/crosstrainingapp/app/src/main/java/com/fractanomics/crosstraining/ui/screens/ProgressScreen.kt#L1108-L1122)), pass the compact styling to the 3-column row (`Starting`, `Current`, `Goal`):
   ```kotlin
   valueStyle = MaterialTheme.typography.titleMedium,
   contentPadding = PaddingValues(horizontal = 8.dp, vertical = 10.dp)
   ```
   This expands available content width inside the ~98dp card from ~74dp to ~88dp, allowing 7–9 character strings (`"75.0 kg"`, `"171.5 lbs"`) to render cleanly without ellipsis clipping, while keeping `maxLines = 1, overflow = TextOverflow.Ellipsis` as a defensive boundary.

---

### 2. Acceptance Criteria (Given-When-Then)

#### Scenario 1: Early Cycle Water Drop (User Repro Case)
* **Given** a fat loss cycle starting on `2026-09-28` with target weight `67.0 kg`.
* **And** weight entries:
  - `2026-09-28`: `77.8 kg` (starting weigh-in)
  - `2026-09-29`: `75.8 kg` (initial 2.0 kg water loss)
  - `2026-10-06`: `75.0 kg` (current weigh-in)
* **When** `computePaceForecast` is evaluated on `2026-10-06`.
* **Then**:
  - `compute14DayPace` returns `null` ("Not enough data yet").
  - `compute7DayPace` returns rate `-0.8 kg/wk` ($\pm 1e-6$).
  - The forecast selects `anchor7` (`2026-09-29`, `75.8 kg`) with $\Delta\text{days} = 7$.
  - $V_{\text{active}} = 0.8\text{ kg/wk}$ ($\pm 1e-6$).
  - Forecast returns `PaceForecast.Projected(weeks = 10.0, targetDate = 2026-12-15)`.
  - `displayText` is `"Estimated: 10.0 weeks"`.

#### Scenario 2: Mature Cycle with 14-Day Priority (Discriminating Fixture)
* **Given** a fat loss cycle with target weight `74.0 kg` and weight entries:
  - `2026-09-15`: `80.0 kg`
  - `2026-09-22`: `79.0 kg` (14-day anchor, $\Delta\text{days} = 14$)
  - `2026-09-29`: `78.2 kg` (7-day anchor, $\Delta\text{days} = 7$)
  - `2026-10-06`: `78.0 kg` (current weigh-in)
* **When** `computePaceForecast` is evaluated on `2026-10-06`.
* **Then**:
  - The forecast selects `anchor14` ($V_{14\text{d}} = 0.5\text{ kg/wk}$) rather than `anchor7` ($V_{7\text{d}} = 0.2\text{ kg/wk}$).
  - Forecast returns `PaceForecast.Projected(weeks = 8.0, targetDate = 2026-12-01)`.
  - `displayText` is `"Estimated: 8.0 weeks"`.

#### Scenario 3: Tier 2 Boundary - Rate Stagnation
* **Given** a fat loss cycle on day 8 with `anchor14 == null` and 7-day rate $R_{7\text{d}} \ge 0.0$ ($V_{7\text{d}} \le 0.0$, rounded to 0.1).
* **When** `computePaceForecast` is evaluated.
* **Then** the engine returns `PaceForecast.Stalled`.

#### Scenario 4: Tier 2 Boundary - 52-Week Cap
* **Given** a fat loss cycle on day 8 with `anchor14 == null` and an extremely shallow deficit ($V_{7\text{d}} = 0.05\text{ kg/wk}$) with $10.0\text{ kg}$ remaining ($\text{rawWeeks} = 200.0$).
* **When** `computePaceForecast` is evaluated.
* **Then** the engine returns `PaceForecast.Projected(weeks = 52.0, targetDate = null, rawWeeks = 200.0)`.
* **And** `isCapped` is `true` and `displayText` is `"Estimated: > 1 year"`.

#### Scenario 5: UI Anti-Truncation on 360dp Viewport
* **Given** a device width of 360dp and `fontScale = 1.0`.
* **When** `WeightProgressionCard` renders metric values (`"77.8 kg"`, `"75.0 kg"`, `"67.0 kg"`) and worst-case imperial values (`"171.5 lbs"`).
* **Then** `hasVisualOverflow == false` and all characters render visibly without ellipsis truncation (`...`).

---

### 3. Implementation Tasks (Pattern A - Standalone Task)

* **Task 1 (Fat Loss Forecast Pacing Hierarchy & KPI Card Anti-Truncation):**
  - Update [`FatLossAnalytics.kt`](file:///C:/Users/rogal/workspaces/ws-gym/crosstrainingapp/app/src/main/java/com/fractanomics/crosstraining/data/analytics/FatLossAnalytics.kt#L1185-L1207) to implement Tier 2 (`anchor7`) resolution between Tier 1 (`anchor14`) and Tier 3 (`oldest`).
  - Update [`FatLossAnalyticsTest.kt`](file:///C:/Users/rogal/workspaces/ws-gym/crosstrainingapp/app/src/test/java/com/fractanomics/crosstraining/data/analytics/FatLossAnalyticsTest.kt) adding Scenarios 1–4 unit tests with floating-point tolerance $1e-6$.
  - Update [`ProgressScreen.kt`](file:///C:/Users/rogal/workspaces/ws-gym/crosstrainingapp/app/src/main/java/com/fractanomics/crosstraining/ui/screens/ProgressScreen.kt) to add `valueStyle` and `contentPadding` to `RowScope.KpiCard` preserving defaults, and pass `titleMedium` with `horizontal = 8.dp, vertical = 10.dp` to `WeightProgressionCard`.
  - Update `CHANGELOG.md` under `## [Unreleased]` describing the forecast pacing hierarchy fix and KPI card layout adjustment.