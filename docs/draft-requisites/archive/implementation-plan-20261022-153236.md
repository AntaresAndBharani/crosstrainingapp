# 📋 Implementation Plan & Requirement Specification: Weight Progression Table Clarity & Forecast Pace Attribution

## 📝 Context & Problem Statement

Based on athlete and Product Owner review of the `WeightProgressionHeroCard` on the `Progress & Goals` screen:

1. **Ambiguous & Indistinguishable Weight Tables (`ROLLING AVERAGES` vs `DAILY WEIGH-IN`)**:
   - The card renders two 3-column weight tables stacked vertically without section titles:
     - Top row (`computeRollingWeightHero`): `LAST WEEK (...` | `TODAY` | `14D AVG` — which actually displays the **7-Day Rolling Average** (`75.7 kg`), **Last Week's 7-Day Rolling Average** (`75.9 kg`), and **14-Day Rolling Average** (`75.8 kg`).
     - Bottom row (`SameDayWeightTrioRow`): `LAST WEEK` | `CURRENT` | `2 WKS AGO` — which actually displays the **raw single-day weigh-in** for today (`74.7 kg`), 1 week ago (`74.8 kg`), and 2 weeks ago (`74.9 kg`).
   - Because the top row calls the 7-day rolling average `TODAY` while the bottom row calls today's raw weigh-in `CURRENT`, and neither row has a section title, the user cannot tell which table is the daily weight and which is the rolling average. Additionally, `LAST WEEK (THU)` truncates to `LAST WEEK (...` on narrow viewports.
2. **Missing Pace Attribution & Unrounded Math Discrepancy in Goal Estimation (`ForecastBanner`)**:
   - The `ForecastBanner` displays `Estimated: 19.7 weeks` and `Projected arrival: Tue, 9 Mar 2027` without indicating whether the projection uses the **14-Day Pace** (`-0.2 kg/wk`) or the **7-Day Pace** (`-0.3 kg/wk`).
   - Furthermore, with `-3.7 kg left` (`75.7 kg` − `72.0 kg`) and a displayed 14-day pace of `-0.2 kg/wk`, dividing the displayed numbers gives `3.7 / 0.2 = 18.5 weeks`, whereas `computePaceForecast` divides by the unrounded velocity (`~0.188 kg/wk`) to produce `19.7 weeks`. Similarly, `75.7 kg` vs `75.9 kg` in the top row displays a `-0.3 kg` delta instead of `-0.2 kg` due to unrounded subtraction.

---

## 🎯 Final Decision Plan & User Story Specification

### Title: feat(ui,analytics): Weight Progression Table Clarity & Forecast Pace Attribution

### User Story
**As an** athlete tracking a Fat Loss / Recomposition cycle on the Progress & Goals screen
**I want** clear section and column headers distinguishing the Rolling Averages table from the Daily Weigh-In table, along with explicit pace attribution (`Based on 14-day pace (-0.2 kg/wk)`) and display-consistent math in the goal estimation banner
**So that** I can immediately tell my daily weight apart from my rolling averages and verify how my estimated weeks to goal are calculated.

---

### Architecture & Design Decisions

#### 1. Visual & Textual Distinction of the Two 3-Column Weight Tables (`ProgressScreen.kt`)
- **Top Table — Rolling Averages**:
  - Add section header `"ROLLING AVERAGES"` (`MaterialTheme.typography.labelSmall`, `letterSpacing = 1.sp`, `FontWeight.SemiBold`, `MaterialTheme.colorScheme.onSurfaceVariant`).
  - Update column headers:
    - Left pillar (`resolveLeftPillarHeader`): `"LAST WK 7D"` (replaces `"LAST WEEK (<DAY>)"`, preventing truncation and accurately describing the prior 7-day window `[t-13d, t-7d]`).
    - Center pillar (`resolveCenterPillarHeader`): `"7D AVG"` (replaces `"TODAY"` / `"LATEST"`).
    - Right pillar (`resolveRightPillarHeader`): `"14D AVG"` (or `"${window}D AVG"` when progressive average is supplied).
- **Visual Divider**:
  - Render a subtle `HorizontalDivider` between the Rolling Averages row and `SameDayWeightTrioRow` when `sameDayTrio != null`.
- **Bottom Table — Daily Weigh-In (`SameDayWeightTrioRow`)**:
  - Add section header `resolveDailyWeighInSectionHeader(referenceDate)` rendering `"DAILY WEIGH-IN (<DAY>)"` (e.g., `"DAILY WEIGH-IN (THU)"` using `referenceDate.dayOfWeek.getDisplayName(TextStyle.SHORT, Locale.US).uppercase()`).
  - Update column headers:
    - Left column: `"LAST WEEK"` (`t - 7d` raw weigh-in)
    - Center column: `"TODAY"` (replaces `"CURRENT"`, showing `t` raw weigh-in)
    - Right column: `"2 WKS AGO"` (`t - 14d` raw weigh-in)

#### 2. Forecast Banner Pace Attribution (`FatLossAnalytics.kt` & `ProgressScreen.kt`)
- Extend `PaceForecast.Projected` with optional default properties (`paceWindowDays: Int = 14`, `paceRateKgPerWeek: Double? = null`) to preserve backward compatibility with all existing callers and tests, while keeping `PaceForecast.Stalled` as a singleton `object`.
- Add pure resolver `resolveForecastPaceSubtitle(forecast: PaceForecast, pace14: PaceRateResult?, pace7: PaceRateResult?, weightUnit: String): String?`:
  - Returns `"Based on 14-day pace (${formattedRate})"` when `pace14 != null`, or `"Based on 7-day pace (${formattedRate})"` when falling back to `pace7` (or from `Projected.paceWindowDays` / `paceRateKgPerWeek` when rendered standalone).
  - Rendered inside `ForecastBanner` as a `MaterialTheme.typography.bodySmall` subtitle line for both `PaceForecast.Projected` and `PaceForecast.Stalled`.
  - Localized to `kg/wk` or `lbs/wk` according to `weightUnit`.

#### 3. 1-Decimal Display Arithmetic Reconciliation (`FatLossAnalytics.kt` & `ProgressScreen.kt`)
- **`computePaceForecast`**:
  - Compute remaining distance from 1-decimal rounded weights: `remainingKg = diff1(round1(currentWeight), round1(targetWeightKg))`.
  - Compute active deficit velocity rounded to 1 decimal place: `vActiveRounded = round1(vActive + 1e-5)`.
  - Compute `rawWeeks = remainingKg / vActiveRounded` (so `3.7 kg / 0.2 kg/wk = 18.5 weeks`).
- **`computeRollingWeightHero` & Top Reference Track**:
  - Compute `delta7dKg = diff1(round1(today7dAvg), round1(lastWeek7dAvg))` and `delta14dKg = diff1(round1(today14dAvg), round1(lastWeek14dAvg))` so `75.7 kg` vs `75.9 kg` always displays `-0.2 kg`.
  - Compute Top Reference Track (`% to goal` and `-X.X kg left`) using `round1(resolvedToday7dAvgKg)` so `(77.8 - 75.7) / (77.8 - 72.0) = 36.2% to goal` and `(-3.7 kg left)` reconcile with zero discrepancy.

---

### Implementation Slice & File Touchpoints

| File | Responsibility |
| :--- | :--- |
| [`FatLossAnalytics.kt`](file:///c:/Users/rogal/workspaces/ws-gym/crosstrainingapp/app/src/main/java/com/fractanomics/crosstraining/data/analytics/FatLossAnalytics.kt) | Update `PaceForecast.Projected` with `paceWindowDays` and `paceRateKgPerWeek`; update `computePaceForecast` and `computeRollingWeightHero` to use 1-decimal rounded `round1`/`diff1` reconciliation. |
| [`ProgressScreen.kt`](file:///c:/Users/rogal/workspaces/ws-gym/crosstrainingapp/app/src/main/java/com/fractanomics/crosstraining/ui/screens/ProgressScreen.kt) | Add `"ROLLING AVERAGES"` and `"DAILY WEIGH-IN (<DAY>)"` section headers, update pillar header resolvers (`"LAST WK 7D"`, `"7D AVG"`, `"TODAY"`), round Top Reference Track inputs, and render `"Based on X-day pace (...)"` in `ForecastBanner`. |
| [`FatLossAnalyticsTest.kt`](file:///c:/Users/rogal/workspaces/ws-gym/crosstrainingapp/app/src/test/java/com/fractanomics/crosstraining/data/analytics/FatLossAnalyticsTest.kt) | Verify 1-decimal forecast reconciliation (`3.7 / 0.2 = 18.5 weeks`), pace window attribution (`14` vs `7`), and `RollingWeightHeroState` rounded delta consistency. |
| [`WeightProgressionCardTest.kt`](file:///c:/Users/rogal/workspaces/ws-gym/crosstrainingapp/app/src/test/java/com/fractanomics/crosstraining/ui/screens/WeightProgressionCardTest.kt) | Update pillar header resolvers (`"LAST WK 7D"`, `"7D AVG"`), add daily weigh-in section header resolver tests (`"DAILY WEIGH-IN (THU)"`), and test `resolveForecastPaceSubtitle` across `Projected`, `Stalled`, `14D`, `7D`, `kg`, and `lbs`. |
| [`WeightProgressionCardComposeTest.kt`](file:///c:/Users/rogal/workspaces/ws-gym/crosstrainingapp/app/src/androidTest/java/com/fractanomics/crosstraining/ui/screens/WeightProgressionCardComposeTest.kt) | Update Compose assertions for `"LAST WK 7D"`, `"7D AVG"`, `"ROLLING AVERAGES"`, `"DAILY WEIGH-IN"`, and no-overflow layout gates at `360dp` / `fontScale = 1.3f`. |

---

### Verification & Acceptance Gates
1. `.\gradlew.bat testDebugUnitTest --no-daemon` — 100% passing unit tests.
2. `.\scripts\tests\Invoke-ScriptTests.ps1` — 100% passing script regression suite.
3. `.\gradlew.bat lintDebug --no-daemon` — Clean Android lint check.
