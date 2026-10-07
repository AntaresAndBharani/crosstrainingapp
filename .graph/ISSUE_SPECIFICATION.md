## 🎯 Final Decision Plan & User Story Specification

### Title: fix: weight progression pace card text truncation and velocity acceleration semantics

### 📖 User Story
**As a** cross-training athlete tracking a fat-loss or body recomposition cycle,  
**I want** the 7-Day and 14-Day Pace cards in the Weight Progression card to display their full metrics and contextual subtitles without horizontal text truncation, and to provide unambiguous, mathematically consistent pace acceleration indicators,  
**So that** I have immediate visual clarity on whether my rate of weight loss is accelerating or decelerating without truncated messages like `"12% slower vs yest..."` or `"Not enough d..."`.

---

### 1. Architectural Decisions & Changes

#### 1.1 UI Layout & Multi-Line Subtitles in `ProgressScreen.kt`
In [`ProgressScreen.kt:1338-1422`](file:///C:/Users/rogal/workspaces/ws-gym/crosstrainingapp/app/src/main/java/com/fractanomics/crosstraining/ui/screens/ProgressScreen.kt#L1338-L1422):
1. **Dynamic 2-Line Subtitle Container (`minLines = 2, maxLines = 2`):**
   - For both the 7-Day and 14-Day Pace cards, update the subtitle `Text` composable:
     - Set `minLines = 2`
     - Set `maxLines = 2`
     - Set `overflow = TextOverflow.Ellipsis`
   - Retain the enclosing `Row(modifier = Modifier.fillMaxWidth().height(IntrinsicSize.Max), horizontalArrangement = Arrangement.spacedBy(12.dp))` with `fillMaxHeight()` on both half-width cards. This equalizes height dynamically across devices and ensures neither card clips vertically under 1.3× font scaling.
2. **Symmetric Inline Null Placeholder (`"—"`):**
   - In `ProgressScreen.kt:1363, 1403`, replace `"Not enough data yet"` in the primary metric value slot with `"—"` for both 7-Day and 14-Day cards when pace is `null`.
   - Add TalkBack accessibility semantics: `modifier = Modifier.semantics { contentDescription = "No data" }`.
   - Preserve existing contract stability: [`FatLossAnalytics.formatPaceChip`](file:///C:/Users/rogal/workspaces/ws-gym/crosstrainingapp/app/src/main/java/com/fractanomics/crosstraining/data/analytics/FatLossAnalytics.kt#L1578) remains untouched to safeguard existing unit test assertions.
3. **Deterministic 14-Day Null Subtitle:**
   - In `ProgressScreen.kt:1413`, when `pace14 == null`, pin the subtitle strictly to `"Needs weigh-in 14+ days ago"`.
4. **Testable UI Logic Helpers for PR CI Parity:**
   - Extract pure helper functions for null value fallback and 14-day subtitle resolution (e.g. `resolvePaceChipValue(paceRate, weightUnit)` and `resolvePace14Subtitle(pace14)`), making UI text mapping directly testable via standard JVM unit tests in [`WeightProgressionCardTest.kt`](file:///C:/Users/rogal/workspaces/ws-gym/crosstrainingapp/app/src/test/java/com/fractanomics/crosstraining/ui/screens/WeightProgressionCardTest.kt) to ensure PR CI verification without requiring device instrumentation.

#### 1.2 Domain Velocity Acceleration Qualifier (Option A) in `FatLossAnalytics.kt`
In [`FatLossAnalytics.kt:1590-1594`](file:///C:/Users/rogal/workspaces/ws-gym/crosstrainingapp/app/src/main/java/com/fractanomics/crosstraining/data/analytics/FatLossAnalytics.kt#L1590-L1594):
Update the percentage subtitle template:
```kotlin
if (comparison.percentChange != null) {
    val pct = kotlin.math.round(kotlin.math.abs(comparison.percentChange)).toInt()
    val descriptor = if (comparison.isAcceleratingDeficit) "faster" else "slower"
    return "$pct% $descriptor loss $vsPart"
}
```
* **Production Evidence Case:** Produces `"12% slower loss vs yesterday"` (and `"14% faster loss vs yesterday"`, `"33% faster loss vs 5 Oct"`).
* **Domain Integrity:** Because this code branch executes *strictly* under `isLossToLoss` (both `currentRate < 0.0` and `priorRate < 0.0`), appending `"loss"` is always factually and semantically correct. It resolves athlete ambiguity regarding signed values versus loss magnitudes without altering underlying math.
* **Deferred Follow-Up Context:** Displaying the athlete's prior baseline rate (e.g. `"was -0.9 kg/wk"`) is explicitly deferred to a future user story to prevent exceeding the 2-line visual budget under 1.3× font scale.

#### 1.3 Acceleration Engine Precedence & Contract (Documentation Only, No Code Change)
This documents runtime precedence rules in [`FatLossAnalytics.kt:1580-1602`](file:///C:/Users/rogal/workspaces/ws-gym/crosstrainingapp/app/src/main/java/com/fractanomics/crosstraining/data/analytics/FatLossAnalytics.kt#L1580-L1602):

| Precedence | Condition | Mathematical Definition | Output Template (after §3.1.2) |
|---|---|---|---|
| **1. Noise Deadband** | Absolute delta rate $< 0.05\text{ kg/wk}$ | $\|R_t - R_{t-1}\| < 0.05$ | `"Pace unchanged"` |
| **2. Accelerating Loss** | Both rates negative & magnitude increased | $R_t < 0 \land R_{t-1} < 0 \land \|R_t\| > \|R_{t-1}\|$ | `"$pct% faster loss $vsPart"` |
| **3. Decelerating Loss** | Both rates negative & magnitude decreased | $R_t < 0 \land R_{t-1} < 0 \land \|R_t\| < \|R_{t-1}\|$ | `"$pct% slower loss $vsPart"` |
| **4. Near-Zero Baseline Guard** | Prior rate magnitude $< 0.1\text{ kg/wk}$ | $R_t < 0 \land R_{t-1} < 0 \land \|R_{t-1}\| < 0.1$ | `"±Δ unit/wk $vsPart"` (suppresses explosive %) |
| **5. Mixed-Sign / Non-Deficit** | At least one rate $\ge 0$ | $R_t \ge 0 \lor R_{t-1} \ge 0$ | `"±Δ unit/wk $vsPart"` (signed difference) |
| **6. Cold Start (< 7d)** | Prior comparison is `null` | N/A | `"Baseline 7d pace"` |

*(Note: Subtitle text color remains `MaterialTheme.colorScheme.onSurfaceVariant`; no color branching is added).*

---

### 2. Acceptance Criteria & Test Matrix (Given-When-Then)

#### Scenario 1: No-Truncation Gate for Half-Width Pace Cards (Parameterised Subtitle Gate)
* **Given** a 360dp mobile viewport with font scale set to 1.3× in `WeightProgressionCardComposeTest.kt`, imperial units (`weightUnit = "lbs"`), and 14-day pace `null`.
* **When** `WeightProgressionHeroCard` is composed and evaluated across each parameterised 7-day subtitle variant:
  1. Percentage decelerating loss: `"12% slower loss vs yesterday"`
  2. Non-consecutive date percentage accelerating loss: `"33% faster loss vs 5 Oct"`
  3. Imperial delta rate fallback: `"-1.1 lbs/wk vs yesterday"`
* **Then**:
  - The 7-day pace primary value (`"▼ -1.8 lbs/wk"`) reports `lineCount == 1` and `!hasVisualOverflow`.
  - The 7-day pace subtitle in all 3 variants reports `lineCount <= 2` and `!hasVisualOverflow` (zero ellipsis).
  - The 14-day pace primary value reports `lineCount == 1` and `!hasVisualOverflow` displaying `"—"` (with `contentDescription = "No data"`).
  - The 14-day pace subtitle (`"Needs weigh-in 14+ days ago"`) reports `lineCount <= 2` and `!hasVisualOverflow` (zero ellipsis).
  - Both 7-Day and 14-Day cards share equal measured height via `IntrinsicSize.Max`.

#### Scenario 1b: Dual-Null Cold-Start Gate
* **Given** a new cycle with $< 7$ days of data where both `pace7` and `pace14` are `null`.
* **When** `WeightProgressionHeroCard` is rendered at 360dp viewport with 1.3× font scale.
* **Then**:
  - 7-day pace value displays `"—"` with `lineCount == 1` and `!hasVisualOverflow` (`contentDescription = "No data"`).
  - 7-day pace subtitle displays `"Baseline 7d pace"` with `lineCount <= 2` and `!hasVisualOverflow`.
  - 14-day pace value displays `"—"` with `lineCount == 1` and `!hasVisualOverflow` (`contentDescription = "No data"`).
  - 14-day pace subtitle displays `"Needs weigh-in 14+ days ago"` with `lineCount <= 2` and `!hasVisualOverflow`.

#### Scenario 2: 7-Day Pace Decelerating Deficit Reproduction (User Evidence Case)
* **Given** a `PaceComparisonResult` constructed with:
  - `currentRateKgPerWeek = -0.80`
  - `priorRateKgPerWeek = -0.91`
  - `percentChange = -12.08`
  - `isAcceleratingDeficit = false`
  - `priorDate = 2026-10-06`
  - `isConsecutive = true`
* **When** `format7DayPaceSubtitle` is evaluated on `2026-10-07` with `Locale.US` and `weightUnit = "kg"`.
* **Then**:
  - `format7DayPaceSubtitle` returns `"12% slower loss vs yesterday"`.

#### Scenario 3: 7-Day Pace Accelerating Deficit
* **Given** a `PaceComparisonResult` constructed with:
  - `currentRateKgPerWeek = -0.80`
  - `priorRateKgPerWeek = -0.70`
  - `percentChange = 14.28`
  - `isAcceleratingDeficit = true`
  - `priorDate = 2026-10-06`
  - `isConsecutive = true`
* **When** `format7DayPaceSubtitle` is evaluated on `2026-10-07` with `Locale.US` and `weightUnit = "kg"`.
* **Then**:
  - `format7DayPaceSubtitle` returns `"14% faster loss vs yesterday"`.

#### Scenario 4: 14-Day Null Pace Placeholder & Subtitle Formatting (JVM CI Parity)
* **Given** a cycle where 14-day pace is `null`.
* **When** evaluated in `WeightProgressionCardTest` and rendered in Compose at 1.3× font scale.
* **Then**:
  - 14-day pace primary value displays `"—"` with TalkBack `contentDescription = "No data"`.
  - 14-day pace subtitle displays `"Needs weigh-in 14+ days ago"`.
  - Subtitle occupies $\le 2$ lines without ellipsis or horizontal truncation.

---

### 3. Implementation Tasks (Pattern A - Standalone Task)

* **Task 1 (Pace Card Multi-Line Layout & Acceleration Semantics):**
  - In [`ProgressScreen.kt`](file:///C:/Users/rogal/workspaces/ws-gym/crosstrainingapp/app/src/main/java/com/fractanomics/crosstraining/ui/screens/ProgressScreen.kt):
    - Extract pure testable helpers (e.g. `resolvePaceChipValue`, `resolvePace14Subtitle`) to facilitate JVM unit test coverage.
    - Set `minLines = 2, maxLines = 2, overflow = TextOverflow.Ellipsis` on subtitle `Text` composables for both 7-Day and 14-Day pace cards.
    - Render `"—"` with `semantics { contentDescription = "No data" }` for null `pace7Text` and `pace14Text`.
    - Pin 14-day null subtitle strictly to `"Needs weigh-in 14+ days ago"`.
  - In [`FatLossAnalytics.kt`](file:///C:/Users/rogal/workspaces/ws-gym/crosstrainingapp/app/src/main/java/com/fractanomics/crosstraining/data/analytics/FatLossAnalytics.kt):
    - Update percentage subtitle template at line 1593 to `"$pct% $descriptor loss $vsPart"`.
    - Leave `formatPaceChip` untouched.
  - In [`FatLossAnalyticsTest.kt`](file:///C:/Users/rogal/workspaces/ws-gym/crosstrainingapp/app/src/test/java/com/fractanomics/crosstraining/data/analytics/FatLossAnalyticsTest.kt):
    - Update the 4 existing test assertions at lines 1758, 1777, 1812, and 2001 to expect `"loss"` (e.g. `"14% faster loss vs yesterday"`, `"25% slower loss vs yesterday"`).
    - Add unit test for Scenario 2 reproduction case (`"12% slower loss vs yesterday"`).
  - In [`WeightProgressionCardTest.kt`](file:///C:/Users/rogal/workspaces/ws-gym/crosstrainingapp/app/src/test/java/com/fractanomics/crosstraining/ui/screens/WeightProgressionCardTest.kt):
    - Add pure JVM unit tests verifying null placeholder `"—"`, TalkBack description, and 14-day subtitle string resolution for PR CI verification.
  - In [`WeightProgressionCardComposeTest.kt`](file:///C:/Users/rogal/workspaces/ws-gym/crosstrainingapp/app/src/androidTest/java/com/fractanomics/crosstraining/ui/screens/WeightProgressionCardComposeTest.kt):
    - Update Scenario 1 layout test asserting `lineCount <= 2 && !hasVisualOverflow` across the parameterised subtitle set under 360dp / 1.3× font scale in lbs mode.
    - Add Scenario 1b dual-null cold start test.
  - In [`CHANGELOG.md`](file:///C:/Users/rogal/workspaces/ws-gym/crosstrainingapp/CHANGELOG.md):
    - Document pace card multi-line layout fix, em-dash placeholder, and Option A `"loss"` qualifier under `## [Unreleased]`.