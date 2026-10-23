# 📋 Implementation Plan & Requirement Specification: 21D & 28D Pace Cards and Visual Multi-Horizon Goal Estimations (7D, 14D, 21D, 28D)

## 📝 Context & Problem Statement

Based on Product Owner design review (`/grill-me`) of the `WeightProgressionHeroCard` on the `Progress & Goals` screen:

1. **Missing Medium- and Long-Term Rolling Pace Horizons (`21-Day Pace` & `28-Day Pace`)**:
   - Currently, the pace section only displays `7-Day Pace` and `14-Day Pace`.
   - Athletes tracking multi-month fat loss cycles need **21-Day Pace** and **28-Day Pace** alongside 7D and 14D in a **2×2 grid** to observe 3-week and 4-week smoothed velocity trends (which filter out bi-weekly water retention and refeed fluctuations).
2. **1-Decimal Rounding Collapses Multi-Horizon Pace Differences**:
   - Over 14D, 21D, and 28D windows, weekly pace rates frequently differ by less than `0.1 kg/wk` (e.g., `-0.24 kg/wk`, `-0.19 kg/wk`, `-0.16 kg/wk`), which all round to `-0.2 kg/wk` at 1 decimal place and produce identical week projections (`18.0 weeks`).
   - **Pace Cards, Required Pace, and Estimations** must use **2-decimal precision** (e.g., `-0.28 kg/wk`, `-0.24 kg/wk`, `-0.19 kg/wk`) so that 7D, 14D, 21D, and 28D paces and their arrival projections reflect their true distinct trajectories.
3. **Text-Only Single-Horizon `ForecastBanner`**:
   - Currently, the bottom `ForecastBanner` is a plain text callout showing only a single pace tier (`Estimated: 18.0 weeks`, `Projected arrival: Fri, 26 Feb 2027`, `Based on 14-day pace (-0.2 kg/wk)`, `Required pace: -0.4 kg/wk (by 28 Dec 2026)`).
   - It must be redrawn into a **visual Multi-Horizon Goal Estimations Card** featuring:
     - A **Target Benchmark Header** displaying the cycle's Target End Date, remaining weeks, and 2-decimal Required Pace.
     - **4 Comparative Visual Horizon Rows (`7D`, `14D`, `21D`, `28D`)**, where each row displays:
       - **Line 1**: Horizon label & 2-decimal pace on the left (`"7D (-0.30 kg/wk)"`), and estimated weeks + projected arrival date on the right (`"12.3 wks · 17 Jan 2027"`, or `"Pace stalled"` / `"Needs 21+ days"`).
       - **Line 2**: A **color-coded proportional Pace-to-Required Sufficiency Bar** paired with an **ahead/late weeks delta badge** (`"On time (-1.2w)"` or `"+2.9w late"`).

---

## 🎯 Final Decision Plan & User Story Specification

### Title: feat(ui,analytics): 21D/28D Pace Grid & Visual Multi-Horizon Goal Estimations Card (7D, 14D, 21D, 28D)

### User Story
**As an** athlete tracking a Fat Loss / Bodybuilding cycle on the Progress & Goals screen
**I want** a 2×2 grid of 2-decimal **7-Day, 14-Day, 21-Day, and 28-Day Pace cards** and a redesigned **Visual Goal Estimations card** comparing all 4 pace horizons (`7D`, `14D`, `21D`, `28D`) with proportional pace-sufficiency bars, estimated weeks, projected arrival dates, and ahead/late badges against my cycle target date and required pace
**So that** I can visually compare my short-, medium-, and monthly-smoothed weight loss trajectories and immediately see which pace horizons are on track to hit my goal on time.

---

## 📐 Mathematical Model & Domain Invariants (`FatLossAnalytics.kt`)

### 1. 21-Day & 28-Day Rolling Pace Computation
- Uses the existing `computePaceRate(entries, windowDays, cycleStartDate, today, startingWeightKg)` engine for `windowDays = 21` and `windowDays = 28`:
  - `compute21DayPace(entries, cycleStartDate, today, startingWeightKg)` and `compute21DayPace(entries, cycle, today)`
  - `compute28DayPace(entries, cycleStartDate, today, startingWeightKg)` and `compute28DayPace(entries, cycle, today)`
- **Same-Weekday Pace Comparison vs Last Week**:
  - Uses `computePaceComparisonVsLastWeek(entries, cycle, windowDays = 21, referenceDate = today)` and `windowDays = 28`.
- **Subtitle Formatting (`formatPaceSubtitle`, `format21DayPaceSubtitle`, `format28DayPaceSubtitle`)**:
  - When `comparison == null`:
    - If `windowDays == 7`: `"Baseline 7d pace"`
    - If `windowDays > 7` (`14`, `21`, `28`):
      - When `pace == null`: `"Needs weigh-in ${windowDays}+ days ago"` (e.g., `"Needs weigh-in 14+ days ago"`, `"Needs weigh-in 21+ days ago"`, `"Needs weigh-in 28+ days ago"`)
      - When `pace != null`: `"Smoothed trend"`
  - When `comparison != null`:
    - If `abs(comparison.deltaRateKgPerWeek) < PACE_NOISE_THRESHOLD_KG_PER_WEEK`: `"Pace unchanged"`
    - If loss-to-loss (`percentChange != null`): `"$pct% ${faster|slower} loss $vsPart"`
    - Otherwise (rate delta): formatted to **2 decimal places** on the 2×2 Pace Cards (e.g., `"-0.12 kg/wk vs last Fri"`, `"+0.15 kg/wk vs last Fri"`).

### 2. 2-Decimal Pace Rate & Required Pace Formatting
- **`PaceRateResult.formatDisplay2(unit: String = "kg"): String`**:
  - Converts `rateKgPerWeek` to `unit` (`kg` or `lbs`) and rounds to 2 decimals (`round2(rateInUnit)`):
    - When `rateInUnit < -0.05`: `String.format(Locale.US, "%.2f %s", round2(rateInUnit), unitLabel)` (e.g., `"-0.28 kg/wk"`)
    - When `rateInUnit > 0.05`: `String.format(Locale.US, "+%.2f %s", round2(rateInUnit), unitLabel)` (e.g., `"+0.14 kg/wk"`)
    - Deadband `[-0.05, +0.05]`: `String.format(Locale.US, "0.00 %s", unitLabel)` (e.g., `"0.00 kg/wk"`)
- **Required Pace 2-Decimal Precision (`RequiredPaceResult`)**:
  - In addition to existing properties, exposes exact/2-decimal rate in active unit:
    - `exactRequiredRateInUnit: Double = -remainingWeightInUnit / weeksRemaining`
    - `requiredRate2InUnit: Double = -maxOf(0.01, round2(remainingWeightInUnit / weeksRemaining))`
    - `formatRate2(): String` -> `String.format(Locale.US, "%.2f %s", requiredRate2InUnit, unitLabel)` (e.g., `"-0.39 kg/wk"`).
    - `formatBenchmarkHeader(): String`:
      - E.g., `"Target: 28 Dec 2026 (9.4w left) · Required: -0.39 kg/wk"` (plus `" · ⚠️ >1% BW/wk"` if `exceedsSustainableRate`).

### 3. Multi-Horizon Forecast Engine (`computeMultiHorizonForecast`)
Evaluates the 4 standard horizons `HORIZON_WINDOWS = listOf(7, 14, 21, 28)`:

#### Container Precedence (`MultiHorizonForecastState`):
1. **`GoalReached`**:
   - When `targetWeightKg != null && targetWeightKg > 0.0 && round1InUnit(current7dAvgKg, weightUnit) <= round1InUnit(targetWeightKg, weightUnit)`.
2. **`CycleEnded`**:
   - When `cycleEndDate != null && today.isAfter(cycleEndDate)` (and goal not yet reached).
3. **`Unavailable`** (`null`):
   - When `targetWeightKg == null || targetWeightKg <= 0.0 || targetWeightKg >= effectiveStartWeightKg`.
4. **`NotEnoughData`**:
   - When `activeEntries.isEmpty()`.
5. **`Active(remainingWeightInUnit, targetWeightInUnit, requiredPace, rows: List<HorizonEstimationRow>)`**:
   - Always contains **4 `HorizonEstimationRow` items** in order: `7D`, `14D`, `21D`, `28D`.

#### Per-Horizon Row Calculation (`HorizonEstimationRow` for `windowDays` in `[7, 14, 21, 28]`):
Given `remainingInUnit = diff1(toUnit(current7dAvgKg, weightUnit), toUnit(targetWeightKg, weightUnit))` (`> 0.0`):
1. **`HorizonRowState.NotEnoughData(windowDays: Int)`**:
   - Emitted when `pace == null` (insufficient history for `windowDays`).
   - Line 1 Left: `"${windowDays}D (—)"`
   - Line 1 Right: `"Needs ${windowDays}+ days"`
   - Line 2 Bar: `0f` fill (`outlineVariant` track)
   - Line 2 Badge: `null`
2. **`HorizonRowState.Stalled(windowDays: Int, formattedPace2: String)`**:
   - Emitted when `pace != null` and either `pace.trend != PaceTrend.LOSS` or `round2(toUnit(pace.deficitVelocityKgPerWeek, weightUnit)) <= 0.00`.
   - Line 1 Left: `"${windowDays}D ($formattedPace2)"` (e.g., `"7D (+0.10 kg/wk)"` or `"7D (0.00 kg/wk)"`)
   - Line 1 Right: `"Pace stalled"`
   - Line 2 Bar: `0f` fill
   - Line 2 Badge: `"Stalled"`
3. **`HorizonRowState.Projected(...)`**:
   - Emitted when `pace != null`, `pace.trend == PaceTrend.LOSS`, and `v2 = round2(toUnit(pace.deficitVelocityKgPerWeek, weightUnit)) > 0.00`:
   - **Estimated Weeks**:
     $$\text{rawWeeks} = \frac{\text{remainingInUnit}}{v_2}$$
     - Capped at `52.0` weeks (`isCapped = rawWeeks > 52.0`).
     - When `!isCapped`:
       - `weeksDisplay = String.format(Locale.US, "%.1f wks", rawWeeks)`
       - `daysToGoal = kotlin.math.round(rawWeeks * 7.0).toLong()`
       - `targetDate = today.plusDays(daysToGoal)`
       - Line 1 Right: `"${weeksDisplay} · ${targetDate.format(shortDateWithYearFormatter)}"` (e.g., `"12.3 wks · 17 Jan 2027"`)
     - When `isCapped` (`> 52.0 weeks`):
       - Line 1 Right: `"> 1 year"`
   - **Proportional Pace-to-Required Sufficiency Bar (`barFraction: Float`)**:
     - When `requiredPace != null`:
       - Let `reqV2 = kotlin.math.abs(requiredPace.requiredRate2InUnit)`.
       - `barFraction = (v2 / reqV2).toFloat().coerceIn(0.05f, 1.0f)`
     - When `requiredPace == null` (open-ended cycle with no `endDate`):
       - Let `maxV2` = maximum `v2` among the active projected rows (or `0.01 * round1InUnit(current7dAvgKg, weightUnit)` fallback).
       - `barFraction = (v2 / maxV2).toFloat().coerceIn(0.05f, 1.0f)`
   - **Target Date Comparison & Weeks Delta Badge (`deltaBadgeText: String?`, `isOnTrack: Boolean`)**:
     - When `requiredPace != null`:
       - `weeksDelta = round1(rawWeeks - requiredPace.weeksRemaining)`
       - `isOnTrack = !isCapped && weeksDelta <= 0.0` (or `v2 >= reqV2`)
       - Badge text:
         - If `isCapped`: `"> 1 yr late"` (`isOnTrack = false`)
         - Else if `weeksDelta < -0.05`: `"On time (${String.format(Locale.US, "%.1f", weeksDelta)}w)"` (e.g., `"On time (-1.2w)"`, `isOnTrack = true`)
         - Else if `weeksDelta <= 0.05`: `"On time"` (`isOnTrack = true`)
         - Else (`weeksDelta > 0.05`): `"+${String.format(Locale.US, "%.1f", weeksDelta)}w late"` (e.g., `"+2.9w late"`, `isOnTrack = false`)
     - When `requiredPace == null`:
       - `deltaBadgeText = null`, `isOnTrack = true`.

---

## 🎨 UI Layout & Visual Design (`ProgressScreen.kt`)

### 1. 2×2 Pace Cards Grid
In `WeightProgressionHeroCard`, the pace cards section is upgraded from a 1×2 row to a **2×2 grid**:

```
┌──────────────────────────────────┐  ┌──────────────────────────────────┐
│ 7-Day Pace                       │  │ 14-Day Pace                      │
│ ▼ -0.28 kg/wk                    │  │ ▼ -0.24 kg/wk                    │
│ -0.28 kg/wk vs last Fri          │  │ 40% slower loss vs last Fri      │
└──────────────────────────────────┘  └──────────────────────────────────┘
┌──────────────────────────────────┐  ┌──────────────────────────────────┐
│ 21-Day Pace                      │  │ 28-Day Pace                      │
│ ▼ -0.19 kg/wk                    │  │ ▼ -0.16 kg/wk                    │
│ Smoothed trend                   │  │ Needs weigh-in 28+ days ago      │
└──────────────────────────────────┘  └──────────────────────────────────┘
```

### 2. Visual Multi-Horizon Goal Estimations Card (`ForecastBanner`)
Below the 2×2 Pace Cards grid, `ForecastBanner` is redrawn into a visual comparative card:

```
┌────────────────────────────────────────────────────────────────────────┐
│ 🎯 GOAL ARRIVAL ESTIMATIONS                                            │
│ Target: 28 Dec 2026 (9.4w left) · Required: -0.39 kg/wk                │
│ ────────────────────────────────────────────────────────────────────── │
│ 7D (-0.28 kg/wk)                                 13.2 wks · 23 Jan 2027│
│ [████████████████████░░░░░░░░]                            +3.8w late   │
│                                                                        │
│ 14D (-0.24 kg/wk)                                15.4 wks · 07 Feb 2027│
│ [████████████████░░░░░░░░░░░░]                            +6.0w late   │
│                                                                        │
│ 21D (-0.42 kg/wk)                                 8.8 wks · 23 Dec 2026│
│ [████████████████████████████]                        On time (-0.6w)  │
│                                                                        │
│ 28D (—)                                                 Needs 28+ days │
│ [░░░░░░░░░░░░░░░░░░░░░░░░░░░░]                                         │
└────────────────────────────────────────────────────────────────────────┘
```

### Typography, Color & Accessibility Specifications
- **Card Container**:
  - `RoundedCornerShape(12.dp)`
  - When `GoalReached`: `primaryContainer` with `"🎉 Goal reached!"`
  - When `CycleEnded`: `surfaceVariant` with `"🏁 Cycle ended"`
  - When `Active`: `surfaceContainerHighest` (or `secondaryContainer.copy(alpha = 0.45f)`) with clean internal spacing (`12.dp` padding, `10.dp` vertical spacing).
- **Header Section**:
  - Title: `"🎯 GOAL ARRIVAL ESTIMATIONS"` (`labelSmall`, `FontWeight.SemiBold`, `letterSpacing = 1.sp`, `onSurfaceVariant`).
  - Benchmark Subtitle: `"Target: 28 Dec 2026 (9.4w left) · Required: -0.39 kg/wk"` (`bodySmall`, `FontWeight.Medium`, `onSurface`).
- **Horizon Rows (`7D`, `14D`, `21D`, `28D`)**:
  - **Line 1**:
    - Left: `"7D (-0.28 kg/wk)"` (`labelMedium`, `FontWeight.SemiBold`, `onSurface`).
    - Right: `"13.2 wks · 23 Jan 2027"` (`labelMedium`, `FontWeight.Bold`, `onSurface` when projected; `onSurfaceVariant` when `"Pace stalled"` or `"Needs 28+ days"`).
  - **Line 2**:
    - Left (`Modifier.weight(1f)`): `LinearProgressIndicator` (`height = 6.dp`, `RoundedCornerShape(3.dp)`):
      - Color when `isOnTrack == true`: `MaterialTheme.colorScheme.primary`
      - Color when `isOnTrack == false` (late): `MaterialTheme.colorScheme.tertiary`
      - Track color: `MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f)`
    - Right (when badge present): Status text (`"On time (-0.6w)"` in `primary`, `"+3.8w late"` in `tertiary`, `"Stalled"` in `error`), `labelSmall`, `FontWeight.SemiBold`.
- **Screen Reader Semantics**:
  - Each horizon row merges semantics (`Modifier.semantics(mergeDescendants = true)`) so TalkBack announces the full horizon summary in one swipe (e.g., `"14D pace -0.24 kg/wk: Estimated 15.4 weeks, projected arrival 7 Feb 2027, +6.0 weeks late"`).

---

## 🧪 BDD Acceptance Criteria

### Scenario 1: 2×2 Pace Grid with 7D, 14D, 21D, and 28D Paces at 2-Decimal Precision
```gherkin
Given a Fat Loss cycle with 30 days of weigh-ins
And 7D pace is -0.284 kg/wk, 14D pace is -0.238 kg/wk, 21D pace is -0.192 kg/wk, and 28D pace is -0.161 kg/wk
When WeightProgressionHeroCard is rendered in "kg"
Then the 2×2 Pace Grid displays:
  - "7-Day Pace": "▼ -0.28 kg/wk"
  - "14-Day Pace": "▼ -0.24 kg/wk"
  - "21-Day Pace": "▼ -0.19 kg/wk"
  - "28-Day Pace": "▼ -0.16 kg/wk"
And each card displays its respective comparison subtitle against 7 days prior (or "Smoothed trend" if prior window is unavailable).
```

### Scenario 2: Multi-Horizon Goal Estimations Card with On-Time and Late Horizons
```gherkin
Given a Fat Loss cycle with targetWeightKg = 72.0 kg and endDate = 2026-12-28
And today is 2026-10-23 (66 days / 9.43 weeks remaining) and today's 7D average is 75.7 kg (3.7 kg remaining)
And Required Pace is -0.39 kg/wk
And 7D pace is -0.28 kg/wk, 14D pace is -0.24 kg/wk, 21D pace is -0.42 kg/wk, and 28D pace is null (only 22 days elapsed)
When computeMultiHorizonForecast is evaluated
Then the header displays "Target: 28 Dec 2026 (9.4w left) · Required: -0.39 kg/wk"
And the 4 horizon rows display:
  - 7D Row: "7D (-0.28 kg/wk)", "13.2 wks · 23 Jan 2027", bar fill = 0.28 / 0.39 (~72%), badge "+3.8w late" (tertiary)
  - 14D Row: "14D (-0.24 kg/wk)", "15.4 wks · 8 Feb 2027", bar fill = 0.24 / 0.39 (~62%), badge "+6.0w late" (tertiary)
  - 21D Row: "21D (-0.42 kg/wk)", "8.8 wks · 23 Dec 2026", bar fill = 100%, badge "On time (-0.6w)" (primary)
  - 28D Row: "28D (—)", "Needs 28+ days", bar fill = 0%, no badge.
```

### Scenario 3: Stalled Single Horizon Does Not Block Other Horizons
```gherkin
Given 7D pace is +0.15 kg/wk (gaining after a refeed week)
While 14D pace is -0.25 kg/wk, 21D pace is -0.30 kg/wk, and 28D pace is -0.32 kg/wk
When computeMultiHorizonForecast is evaluated
Then the 7D row displays "7D (+0.15 kg/wk)", "Pace stalled", 0% bar fill, and "Stalled" badge
While the 14D, 21D, and 28D rows still render their full estimated weeks, arrival dates, sufficiency bars, and badges.
```

### Scenario 4: Goal Reached and Cycle Ended Terminal States
```gherkin
Given today's 7D average is <= targetWeightKg (at 1-decimal display precision)
When the estimations section is rendered
Then it displays the celebratory "Goal reached! 🎉" state instead of the 4-row bars
And when today > cycleEndDate while still above targetWeightKg, it displays "Cycle ended".
```

---

## 📂 Implementation Slice & File Touchpoints

| File | Responsibility |
| :--- | :--- |
| [`FatLossAnalytics.kt`](file:///c:/Users/rogal/workspaces/ws-gym/crosstrainingapp/app/src/main/java/com/fractanomics/crosstraining/data/analytics/FatLossAnalytics.kt) | Add `compute21DayPace`, `compute28DayPace`, `format21DayPaceSubtitle`, `format28DayPaceSubtitle`, 2-decimal `PaceRateResult.formatDisplay2`, 2-decimal `RequiredPaceResult.formatRate2` / `requiredRate2InUnit`, and `computeMultiHorizonForecast` returning `MultiHorizonForecastState` (`GoalReached`, `CycleEnded`, `NotEnoughData`, `Active` with 4 `HorizonEstimationRow` items for `7D`, `14D`, `21D`, `28D`). |
| [`ProgressScreen.kt`](file:///c:/Users/rogal/workspaces/ws-gym/crosstrainingapp/app/src/main/java/com/fractanomics/crosstraining/ui/screens/ProgressScreen.kt) | Update `WeightProgressionCard` and `WeightProgressionHeroCard` to compute and pass `pace21`, `pace28`, `paceComparison21d`, `paceComparison28d`, and `multiHorizonForecast`; render the **2×2 Pace Cards Grid** (`7-Day Pace`, `14-Day Pace`, `21-Day Pace`, `28-Day Pace`) with 2-decimal rates; redraw `ForecastBanner` into the visual **Multi-Horizon Goal Estimations Card** with the target benchmark header and 4 comparative bar rows. |
| [`FatLossAnalyticsTest.kt`](file:///c:/Users/rogal/workspaces/ws-gym/crosstrainingapp/app/src/test/java/com/fractanomics/crosstraining/data/analytics/FatLossAnalyticsTest.kt) | Add unit tests for `compute21DayPace`, `compute28DayPace`, `format21DayPaceSubtitle`, `format28DayPaceSubtitle`, 2-decimal pace/required-pace formatting, and `computeMultiHorizonForecast` across Scenarios 1–4 and `kg`/`lbs` units. |
| [`WeightProgressionCardTest.kt`](file:///c:/Users/rogal/workspaces/ws-gym/crosstrainingapp/app/src/test/java/com/fractanomics/crosstraining/ui/screens/WeightProgressionCardTest.kt) | Add unit tests for 21D/28D pace card resolvers, 2-decimal pace chip formatting, and multi-horizon forecast row formatting. |
| [`WeightProgressionCardComposeTest.kt`](file:///c:/Users/rogal/workspaces/ws-gym/crosstrainingapp/app/src/androidTest/java/com/fractanomics/crosstraining/ui/screens/WeightProgressionCardComposeTest.kt) | Add Compose UI tests verifying the 2×2 Pace Cards (`7-Day Pace`, `14-Day Pace`, `21-Day Pace`, `28-Day Pace`) and the 4-row visual `GOAL ARRIVAL ESTIMATIONS` card. |

---

## ✅ Verification & Acceptance Gates
1. `.\gradlew.bat testDebugUnitTest --no-daemon` — 100% passing unit tests.
2. `.\scripts\tests\Invoke-ScriptTests.ps1` — 100% passing script regression suite.
3. `.\gradlew.bat lintDebug --no-daemon` — Clean Android lint check.
