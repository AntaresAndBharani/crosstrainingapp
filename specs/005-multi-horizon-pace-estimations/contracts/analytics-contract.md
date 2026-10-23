# Analytics & UI Contract: 21D/28D Pace Grid & Visual Multi-Horizon Goal Estimations

## 1. Domain Analytics Functions (`FatLossAnalytics.kt`)

```kotlin
fun compute21DayPace(
    entries: List<WeightEntry>,
    cycleStartDate: LocalDate,
    today: LocalDate = LocalDate.now(),
    startingWeightKg: Double? = null
): PaceRateResult?

fun compute21DayPace(
    entries: List<WeightEntry>,
    cycle: Cycle,
    today: LocalDate = LocalDate.now()
): PaceRateResult?

fun compute28DayPace(
    entries: List<WeightEntry>,
    cycleStartDate: LocalDate,
    today: LocalDate = LocalDate.now(),
    startingWeightKg: Double? = null
): PaceRateResult?

fun compute28DayPace(
    entries: List<WeightEntry>,
    cycle: Cycle,
    today: LocalDate = LocalDate.now()
): PaceRateResult?

fun format21DayPaceSubtitle(
    comparison: PaceComparisonResult?,
    pace21: PaceRateResult?,
    weightUnit: String,
    referenceDate: LocalDate = LocalDate.now(),
    useTwoDecimals: Boolean = false
): String

fun format28DayPaceSubtitle(
    comparison: PaceComparisonResult?,
    pace28: PaceRateResult?,
    weightUnit: String,
    referenceDate: LocalDate = LocalDate.now(),
    useTwoDecimals: Boolean = false
): String

fun computeMultiHorizonForecast(
    entries: List<WeightEntry>,
    cycleStartDate: LocalDate,
    cycleEndDate: LocalDate? = null,
    targetWeightKg: Double?,
    startingWeightKg: Double? = null,
    today: LocalDate = LocalDate.now(),
    weightUnit: String = "kg"
): MultiHorizonForecastState?

fun computeMultiHorizonForecast(
    entries: List<WeightEntry>,
    cycle: Cycle,
    today: LocalDate = LocalDate.now(),
    targetWeightKg: Double? = cycle.targetWeightKg ?: cycle.targetBodyWeightKg,
    weightUnit: String = "kg"
): MultiHorizonForecastState?
```

## 2. UI Resolver & Component Contracts (`ProgressScreen.kt`)

- `resolvePaceChipValue(pace: PaceRateResult?, weightUnit: String, useTwoDecimals: Boolean = false): String`
- `resolveHorizonRowLeftLabel(row: HorizonEstimationRow): String` — returns `"7D (-0.28 kg/wk)"` or `"28D (—)"`.
- `resolveHorizonRowRightValue(row: HorizonEstimationRow): String` — returns `"13.2 wks · 23 Jan 2027"`, `"> 1 year"`, `"Pace stalled"`, or `"Needs 28+ days"`.
- `resolveHorizonRowBadgeText(row: HorizonEstimationRow): String?` — returns `"On time (-0.6w)"`, `"On time"`, `"+3.8w late"`, `"> 1 yr late"`, `"Stalled"`, or `null`.
- `resolveHorizonRowContentDescription(row: HorizonEstimationRow): String` — merged accessibility description for TalkBack.
- `ForecastBanner` updated to accept `multiHorizonForecast: MultiHorizonForecastState? = null` (rendering the visual 4-row `GOAL ARRIVAL ESTIMATIONS` card when `multiHorizonForecast is MultiHorizonForecastState.Active`, while preserving fallback to single-tier `forecast: PaceForecast` when `multiHorizonForecast == null` for legacy test callers).
