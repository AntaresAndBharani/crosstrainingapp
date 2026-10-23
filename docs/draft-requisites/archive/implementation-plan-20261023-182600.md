# 📋 Implementation Plan & Requirement Specification: Required-Pace & Linear Cycle Weight Targets (7D Average & Daily)

## 📝 Context & Problem Statement

Based on Product Owner design review (`/grill-me`) of the `WeightProgressionHeroCard` on the `Progress & Goals` screen:

1. **Flawed Historical Extrapolation in `7-DAY PACE TARGETS` (`compute7DayPaceTargets`)**:
   - Currently, `compute7DayPaceTargets` computes `Expected Today` and `Target Tomorrow` by extrapolating the historical rate from `startKg` to `t - 7d` and `t - 6d` (`entryTMinus6.weightKg + ((entryTMinus6.weightKg - startKg) / dCycle)`).
   - This produces counterintuitive and misleading targets:
     - When `t - 6d` happened to be a high single-day fluctuation (e.g., `76.1 kg`), `Target Tomorrow` displays `76 kg` (`"Already below target..."`) even when the athlete weighs `74.7 kg` today and needs to lose weight to reach `72.0 kg`.
     - If `entryTMinus7 >= startKg`, the entire container locks into a `Pace Stalled` state, hiding targets even when the athlete has an active cycle goal and end date.
2. **Missing Required-Pace Anchoring for Today & Tomorrow**:
   - The athlete wants **Expected Today** and **Target Tomorrow** to be driven by the **Required Pace** needed to reach the cycle's target weight by the end of the cycle (`cycle.endDate`):
     - **Expected Today**: Where my weight should be today compared to **7 days ago** (`W_{t-7d} + RequiredPace/wk`, e.g., if required pace is `-0.70 kg/wk`, I should weigh `0.70 kg` less than 7 days ago).
     - **Target Tomorrow**: Where my weight should be tomorrow based on **my real weight of today** (`W_today + RequiredPace/wk ÷ 7`, e.g., `-0.10 kg` vs today when required pace is `-0.70 kg/wk`).
3. **Missing Dual-Track Coverage (`7D Rolling Average` & `Daily Weight`)**:
   - Currently, pace targets only evaluate single-day weigh-ins. The athlete needs these targets computed and displayed for **both** the **7-Day Rolling Average (`7D AVG`)** and the **Daily Weigh-In (`TODAY`)**.
4. **Missing Theoretical Linear Cycle Weight (`Linear Today`) & Key Measure Comparisons**:
   - The athlete wants to see their **Theoretical Linear Weight** for today assuming purely linear weight loss from the beginning of the cycle (`startDate`, `startingWeightKg`) to the end of the cycle (`endDate`, `targetWeightKg`), and compare that theoretical weight directly against their two key measures: **Today's 7D Rolling Average (`7D AVG`)** and **Today's Daily Weigh-In (`TODAY`)**.
5. **Sub-0.1 Daily Step Precision**:
   - When the required weekly pace is between `-0.1` and `-0.4 kg/wk`, the daily required step (`RequiredPace ÷ 7`) is between `-0.01` and `-0.06 kg/day`. Rounding target weights to 1 decimal place hides sub-`0.1` daily steps, so **Expected Today**, **Target Tomorrow**, and **Linear Today** (and their deltas/steps) must be displayed to **2 decimal places** (`%.2f`).

---

## 🎯 Final Decision Plan & User Story Specification

### Title: feat(ui,analytics): Required-Pace & Linear Cycle Weight Targets for 7D Average and Daily Weight

### User Story
**As an** athlete tracking a Fat Loss / Bodybuilding cycle on the Progress & Goals screen
**I want** a unified **Pace & Linear Targets** panel showing my 2-decimal **Linear Today**, **Expected Today** (from 7 days ago at Required Pace), and **Target Tomorrow** (from today's actual weight at Required Pace) across both my **7D Rolling Average** and **Daily Weight**, along with deltas showing whether I am ahead or off pace
**So that** I can immediately see where my average and daily weight should be today and tomorrow to hit my cycle goal on time, and how both compare against a linear start-to-finish trajectory.

---

## 📐 Mathematical Model & Domain Invariants (`FatLossAnalytics.kt`)

### 1. Precision & Unit Conversion Helpers
- All internal domain physics are calculated in `kg` and converted to the active display unit (`"kg"` or `"lbs"`, where `1 kg = 2.20462262185 lbs`) prior to 2-decimal display rounding:
  - `round2(value: Double): Double` — `BigDecimal.valueOf(value).setScale(2, RoundingMode.HALF_UP).toDouble()`
  - `round2InUnit(weightKg: Double, weightUnit: String): Double` — `round2(toUnit(weightKg, weightUnit))`
  - `diff2(a: Double, b: Double): Double` — exact 2-decimal `BigDecimal` subtraction `round2(a) - round2(b)`
  - `formatWeight2(valueInUnit: Double, unitLabel: String): String` — `String.format(Locale.US, "%.2f %s", valueInUnit, unitLabel)`

### 2. Unified Exact Required Pace Authority
Given:
- `startWeightKg`: `cycle.startingWeightKg ?: firstInCycleEntry.weightKg`
- `targetWeightKg`: `cycle.targetWeightKg ?: cycle.targetBodyWeightKg`
- `today7dAvgKg`: `computeCurrent7DayAverageWeight(entries, cycleStartDate, today) ?: latestInCycleEntry.weightKg`
- `daysRemaining`: `ChronoUnit.DAYS.between(today, cycleEndDate)` (`> 0`)
- `weeksRemaining`: `daysRemaining / 7.0`

In the active display unit (`weightUnit`):
- `current7dAvgInUnit = toUnit(today7dAvgKg, weightUnit)`
- `targetInUnit = toUnit(targetWeightKg, weightUnit)`
- `remainingInUnit = current7dAvgInUnit - targetInUnit`
- **Exact Daily Required Rate** (signed, negative for weight loss):
  $$\text{dailyRequiredRateInUnit} = -\frac{\text{remainingInUnit}}{\text{daysRemaining}}$$
- **Exact Weekly Required Rate** (signed, negative for weight loss):
  $$\text{weeklyRequiredRateInUnit} = \text{dailyRequiredRateInUnit} \times 7.0 = -\frac{\text{remainingInUnit}}{\text{weeksRemaining}}$$

### 3. Column 1 — Theoretical Linear Weight (`Linear Today`)
Given `totalCycleDays = ChronoUnit.DAYS.between(cycleStartDate, cycleEndDate)` (`> 0`) and `elapsedDays = ChronoUnit.DAYS.between(cycleStartDate, today).coerceIn(0L, totalCycleDays)`:
- **Linear Weight Formula (in display unit)**:
  $$W_{\text{linear}}(t) = \text{startInUnit} + (\text{targetInUnit} - \text{startInUnit}) \times \frac{\text{elapsedDays}}{\text{totalCycleDays}}$$
  $$W_{\text{linear, display}} = \text{round2}(W_{\text{linear}}(t))$$
- **7D Average Comparison**:
  - $\Delta_{\text{linear, 7d}} = \text{diff2}(\text{round2}(\text{today7dAvgInUnit}), W_{\text{linear, display}})$
  - Status:
    - $\Delta_{\text{linear, 7d}} < 0.00 \rightarrow \text{AHEAD}$ (`"-X.XX kg ahead"`)
    - $\Delta_{\text{linear, 7d}} > 0.00 \rightarrow \text{OFF\_PACE}$ (`"+X.XX kg off pace"`)
    - $\Delta_{\text{linear, 7d}} == 0.00 \rightarrow \text{ON\_PACE}$ (`"On pace"`)
- **Daily Weight Comparison**:
  - If today's raw weigh-in $W_{\text{daily}}(t)$ is logged:
    - $\Delta_{\text{linear, daily}} = \text{diff2}(\text{round2}(W_{\text{daily, inUnit}}(t)), W_{\text{linear, display}})$
    - Status evaluated identically (`AHEAD`, `OFF_PACE`, `ON_PACE`).
  - If today's raw weigh-in is unlogged:
    - Target weight $W_{\text{linear, display}}$ is still shown; `deltaDisplay = null` and `status = null`.

### 4. Column 2 — Required-Pace Expected Weight (`Expected Today`)
Anchored to **7 days ago (`t - 7d`)** plus 1 full week of exact Required Pace ($\text{weeklyRequiredRateInUnit}$):
- **7D Average Row**:
  - Baseline anchor: `lastWeek7dAvgKg` = 7D rolling average over `[t - 13d, t - 7d]`.
  - If `t - 7d < cycleStartDate` and no entries exist in `[t - 13d, t - 7d]`: emits `ColumnState.InsufficientHistory` (`"—"`, `"Needs 7 days history"`).
  - If `lastWeek7dAvgKg == null`: emits `ColumnState.NeedsLog(t - 7d)` (`"—"`, `"Needs log from <d MMM>"`).
  - Otherwise:
    $$W_{\text{expected, 7d}}(t) = \text{round2}(\text{toUnit}(\text{lastWeek7dAvgKg}, \text{unit}) + \text{weeklyRequiredRateInUnit})$$
    $$\Delta_{\text{expected, 7d}} = \text{diff2}(\text{round2}(\text{today7dAvgInUnit}), W_{\text{expected, 7d}}(t))$$
    Status: `AHEAD` ($< 0.00$), `OFF_PACE` ($> 0.00$), `ON_PACE` ($== 0.00$).
- **Daily Weight Row**:
  - Baseline anchor: exact raw weigh-in on `t - 7d` (`entryTMinus7`, non-deleted entry at `referenceDate.minusDays(7)`).
  - Note: Historical lookback into pre-cycle entries at `t - 7d` is allowed if `t - 7d` has a valid non-deleted entry; if `t - 7d < cycleStartDate` and unlogged, emits `ColumnState.InsufficientHistory`; if `t - 7d >= cycleStartDate` and unlogged, emits `ColumnState.NeedsLog(t - 7d)`.
  - When `entryTMinus7 != null`:
    $$W_{\text{expected, daily}}(t) = \text{round2}(\text{toUnit}(\text{entryTMinus7.weightKg}, \text{unit}) + \text{weeklyRequiredRateInUnit})$$
  - If today's raw weigh-in $W_{\text{daily}}(t)$ is logged:
    $$\Delta_{\text{expected, daily}} = \text{diff2}(\text{round2}(\text{toUnit}(W_{\text{daily}}(t), \text{unit})), W_{\text{expected, daily}}(t))$$
    Status: `AHEAD` ($< 0.00$), `OFF_PACE` ($> 0.00$), `ON_PACE` ($== 0.00$).
  - If today's raw weigh-in is unlogged:
    - Target weight $W_{\text{expected, daily}}(t)$ is shown; `deltaDisplay = null` and `status = null`.

### 5. Column 3 — Required-Pace Tomorrow Target (`Target Tomorrow`)
Anchored to **today's actual weight (`t`)** plus 1 day of exact Required Pace ($\text{dailyRequiredRateInUnit} = \text{weeklyRequiredRateInUnit} / 7.0$):
- **Daily Step Display**:
  - $\text{dailyStepDisplay} = \text{round2}(\text{dailyRequiredRateInUnit})$ (e.g., `-0.06` for `-0.06 kg/day`).
- **7D Average Row**:
  - Uses `today7dAvgInUnit`:
    $$W_{\text{tomorrow, 7d}} = \text{round2}(\text{today7dAvgInUnit} + \text{dailyRequiredRateInUnit})$$
  - Subtitle: `"-X.XX <unit>/day"` (e.g., `"-0.06 kg/day"`).
- **Daily Weight Row**:
  - Requires today's raw weigh-in (`entryToday` at `referenceDate`):
  - If `entryToday == null`:
    - Emits `ColumnState.NeedsTodayLog` (`"—"`, subtitle `"Needs today's log"`).
  - If `entryToday != null`:
    $$W_{\text{tomorrow, daily}} = \text{round2}(\text{toUnit}(\text{entryToday.weightKg}, \text{unit}) + \text{dailyRequiredRateInUnit})$$
    - Subtitle: `"-X.XX <unit>/day"` (e.g., `"-0.06 kg/day"`).

---

## 🏗️ Container Lifecycle & State Hierarchy (`PaceTargetsState`)

The container state in `FatLossAnalytics.kt` follows a strict precedence hierarchy without the legacy `Stalled` state:

1. **`PaceTargetsState.Unavailable(reason: String)`**:
   - Returned when:
     - Active in-cycle entries are empty, OR
     - `startWeightKg == null || targetWeightKg == null || targetWeightKg <= 0.0 || targetWeightKg >= startWeightKg`, OR
     - `cycleEndDate == null` or `ChronoUnit.DAYS.between(cycleStartDate, cycleEndDate) <= 0`.
   - Default message: `"Pace & Linear Targets Unavailable: Set cycle goal weight and end date"`.
2. **`PaceTargetsState.GoalReached`**:
   - Returned when `round1InUnit(today7dAvgKg, weightUnit) <= round1InUnit(targetWeightKg, weightUnit)`.
   - Display text: `"Goal reached! 🎉"`.
3. **`PaceTargetsState.CycleEnded`**:
   - Returned when `!today.isBefore(cycleEndDate)` (i.e. `today >= cycleEndDate` and goal not yet reached).
   - Display text: `"Cycle ended"`.
4. **`PaceTargetsState.Available(sevenDayAvgRow: TargetRowState, dailyWeightRow: TargetRowState)`**:
   - Each `TargetRowState` contains:
     - `linearToday: ColumnState`
     - `expectedToday: ColumnState`
     - `targetTomorrow: ColumnState`
   - Backward-compatible convenience properties `val today: ColumnState get() = dailyWeightRow.expectedToday` and `val tomorrow: ColumnState get() = dailyWeightRow.targetTomorrow` can be preserved if needed by legacy callers while migrating tests.

---

## 🎨 UI Layout & Visual Design (`ProgressScreen.kt`)

Inside `WeightProgressionHeroCard`, `TheoreticalPaceTargetsBox` is updated as follows:

```
┌──────────────────────────────────────────────────────────────────────┐
│ PACE & LINEAR TARGETS                                                │
│                                                                      │
│ 7D ROLLING AVERAGE                                                   │
│ Linear Today          │ Expected Today        │ Target Tomorrow      │
│ 74.85 kg              │ 75.51 kg              │ 75.64 kg             │
│ +0.85 kg off pace     │ +0.19 kg off pace     │ -0.06 kg/day         │
│ ──────────────────────┴───────────────────────┴───────────────────── │
│ DAILY WEIGH-IN                                                       │
│ Linear Today          │ Expected Today        │ Target Tomorrow      │
│ 74.85 kg              │ 74.41 kg              │ 74.64 kg             │
│ -0.15 kg ahead        │ +0.29 kg off pace     │ -0.06 kg/day         │
└──────────────────────────────────────────────────────────────────────┘
```

### Typography, Color & Accessibility Specifications
- **Section Title**: `"PACE & LINEAR TARGETS"` (`MaterialTheme.typography.labelSmall`, `FontWeight.SemiBold`, `letterSpacing = 1.sp`, `onSurfaceVariant`).
- **Row Headers**: `"7D ROLLING AVERAGE"` and `"DAILY WEIGH-IN"` (`MaterialTheme.typography.labelSmall`, `FontWeight.SemiBold`, `color = MaterialTheme.colorScheme.primary`).
- **3 Columns per Row** (`Linear Today`, `Expected Today`, `Target Tomorrow`):
  - Equal width (`Modifier.weight(1f)`) separated by `1.dp` vertical dividers (`outlineVariant`).
  - Column Header: `MaterialTheme.typography.labelSmall`, `maxLines = 1`, `overflow = TextOverflow.Ellipsis`.
  - Target Weight Value: formatted to **2 decimal places** (`"74.85 kg"`), `titleSmall` (or `bodyLarge` when `fontScale >= 1.25f` to prevent clipping on `360dp` viewports), `FontWeight.Bold`, `maxLines = 1`.
  - Subtitle:
    - For `Linear Today` and `Expected Today`:
      - `PaceTargetStatus.AHEAD`: `"-0.15 kg ahead"` (`MaterialTheme.colorScheme.primary`)
      - `PaceTargetStatus.OFF_PACE`: `"+0.29 kg off pace"` (`MaterialTheme.colorScheme.tertiary`)
      - `PaceTargetStatus.ON_PACE`: `"On pace"` (`MaterialTheme.colorScheme.onSurfaceVariant`)
    - For `Target Tomorrow`:
      - `"-0.06 kg/day"` (`MaterialTheme.colorScheme.onSurfaceVariant`) or `"Needs today's log"` when today is unlogged.
- **Horizontal Divider**: `HorizontalDivider` (`outlineVariant.copy(alpha = 0.5f)`) separating the `7D ROLLING AVERAGE` target row and the `DAILY WEIGH-IN` target row.
- **Screen Reader Semantics**: Merged semantics per cell announcing the row context (`"7D Average"` vs `"Daily Weight"`), column title (`"Linear Today"`, `"Expected Today"`, `"Target Tomorrow"`), 2-decimal weight, and status/step subtitle.

---

## 🧪 BDD Acceptance Criteria

### Scenario 1: Full Data — 7D Average and Daily Weight Targets with 0.70 kg/wk Required Pace
```gherkin
Given a Fat Loss cycle starting 2026-10-01 at 78.00 kg with target 71.00 kg by 2026-12-10 (70 total days)
And today is 2026-10-22 (21 days elapsed, 49 days / 7.0 weeks remaining)
And today's 7D Rolling Average is 75.90 kg (remaining = 4.90 kg -> Required Pace = -0.70 kg/wk, -0.10 kg/day)
And last week's 7D Rolling Average (t-7d) was 76.50 kg
And last week's raw weigh-in (t-7d) was 76.20 kg, and today's raw weigh-in (t) is 75.60 kg
When computePaceTargets is evaluated for today in "kg"
Then Linear Today is 78.00 + (71.00 - 78.00) * (21 / 70) = 75.90 kg
And for the 7D Rolling Average row:
  - Linear Today displays "75.90 kg" with status "On pace" (0.00 kg)
  - Expected Today displays "75.80 kg" (76.50 - 0.70) with subtitle "+0.10 kg off pace" (75.90 - 75.80)
  - Target Tomorrow displays "75.80 kg" (75.90 - 0.10) with subtitle "-0.10 kg/day"
And for the Daily Weight row:
  - Linear Today displays "75.90 kg" with subtitle "-0.30 kg ahead" (75.60 - 75.90)
  - Expected Today displays "75.50 kg" (76.20 - 0.70) with subtitle "+0.10 kg off pace" (75.60 - 75.50)
  - Target Tomorrow displays "75.50 kg" (75.60 - 0.10) with subtitle "-0.10 kg/day"
```

### Scenario 2: Sub-0.1 Daily Step Visibility (2-Decimal Precision)
```gherkin
Given remaining weight is 3.70 kg with 67 days (9.5714 weeks) remaining
When Required Pace is computed (-0.3866 kg/wk, -0.0552 kg/day)
And today's raw weight is 74.70 kg and t-7d raw weight is 74.80 kg
Then Daily Expected Today displays "74.41 kg" (74.80 - 0.3866) and subtitle "+0.29 kg off pace"
And Daily Target Tomorrow displays "74.64 kg" (74.70 - 0.0552) and subtitle "-0.06 kg/day"
```

### Scenario 3: Today Unlogged — Daily Row Graceful Degradation
```gherkin
Given the athlete has logged weigh-ins over the past 14 days including t-7d, but has NOT logged today (t)
When computePaceTargets is evaluated at referenceDate = t
Then the 7D Rolling Average row computes and displays all 3 columns (Linear Today, Expected Today, Target Tomorrow) with full deltas
And the Daily Weight row displays:
  - Linear Today: 2-decimal target weight with null delta subtitle
  - Expected Today: 2-decimal target weight (from t-7d) with null delta subtitle
  - Target Tomorrow: "—" with subtitle "Needs today's log"
```

### Scenario 4: Missing t-7d Log on Daily Row
```gherkin
Given the athlete logged today (t) and earlier days, but did not log on t-7d (within cycle)
When computePaceTargets is evaluated at referenceDate = t
Then Daily Expected Today emits ColumnState.NeedsLog(t - 7d) rendering "—" and "Needs log from <date>"
While Daily Linear Today and Daily Target Tomorrow still render their 2-decimal values and subtitles.
```

### Scenario 5: No Stalled Blocker on Weight Gain Week & Cycle Boundary States
```gherkin
Given an athlete whose weight increased over the past 7 days, but who is still above targetWeightKg before cycleEndDate
When computePaceTargets is evaluated
Then the container remains Available (not Stalled) and computes Required-Pace targets toward cycleEndDate
And when round1InUnit(today7dAvgKg) <= round1InUnit(targetWeightKg), the container emits PaceTargetsState.GoalReached
And when today >= cycleEndDate (and above goal), the container emits PaceTargetsState.CycleEnded
And when cycleEndDate == null or targetWeightKg == null, the container emits PaceTargetsState.Unavailable.
```

---

## 📂 Implementation Slice & File Touchpoints

| File | Responsibility |
| :--- | :--- |
| [`FatLossAnalytics.kt`](file:///c:/Users/rogal/workspaces/ws-gym/crosstrainingapp/app/src/main/java/com/fractanomics/crosstraining/data/analytics/FatLossAnalytics.kt) | Add `round2`, `round2InUnit`, `diff2`; update `PaceTargetsState` (`Unavailable`, `GoalReached`, `CycleEnded`, `Available(sevenDayAvgRow, dailyWeightRow)`), `TargetRowState`, and `ColumnState` (`Value`, `NeedsLog`, `NeedsTodayLog`, `InsufficientHistory`); update `compute7DayPaceTargets` to compute 2-decimal `Linear Today`, `Expected Today`, and `Target Tomorrow` using exact Required Pace for both 7D Average and Daily Weight. |
| [`ProgressScreen.kt`](file:///c:/Users/rogal/workspaces/ws-gym/crosstrainingapp/app/src/main/java/com/fractanomics/crosstraining/ui/screens/ProgressScreen.kt) | Update `TheoreticalPaceTargetsBox` and string/accessibility resolvers to render the `"PACE & LINEAR TARGETS"` 3-column × 2-row layout (`7D ROLLING AVERAGE` and `DAILY WEIGH-IN` rows, each with `Linear Today`, `Expected Today`, `Target Tomorrow` formatted to 2 decimal places). |
| [`FatLossAnalyticsTest.kt`](file:///c:/Users/rogal/workspaces/ws-gym/crosstrainingapp/app/src/test/java/com/fractanomics/crosstraining/data/analytics/FatLossAnalyticsTest.kt) | Update and expand unit tests for `compute7DayPaceTargets` covering Scenarios 1–5, 2-decimal precision, `kg` and `lbs` localization, and boundary states (`Unavailable`, `GoalReached`, `CycleEnded`). |
| [`WeightProgressionCardTest.kt`](file:///c:/Users/rogal/workspaces/ws-gym/crosstrainingapp/app/src/test/java/com/fractanomics/crosstraining/ui/screens/WeightProgressionCardTest.kt) | Update UI resolver unit tests for 2-decimal formatting, `Linear Today`, `Expected Today`, `Target Tomorrow` subtitles (`-X.XX kg/day`, `ahead`, `off pace`, `On pace`, `Needs today's log`), and accessibility content descriptions. |
| [`WeightProgressionCardComposeTest.kt`](file:///c:/Users/rogal/workspaces/ws-gym/crosstrainingapp/app/src/androidTest/java/com/fractanomics/crosstraining/ui/screens/WeightProgressionCardComposeTest.kt) | Update Compose UI tests for `"PACE & LINEAR TARGETS"`, 3-column × 2-row rendering, and 360dp / `fontScale = 1.3f` layout assertions. |

---

## ✅ Verification & Acceptance Gates
1. `.\gradlew.bat testDebugUnitTest --no-daemon` — 100% passing unit tests.
2. `.\scripts\tests\Invoke-ScriptTests.ps1` — 100% passing script regression suite.
3. `.\gradlew.bat lintDebug --no-daemon` — Clean Android lint check.
