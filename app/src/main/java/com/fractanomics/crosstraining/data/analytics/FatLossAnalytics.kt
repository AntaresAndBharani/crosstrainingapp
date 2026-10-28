package com.fractanomics.crosstraining.data.analytics

import com.fractanomics.crosstraining.data.model.BlockKind
import com.fractanomics.crosstraining.data.model.BlockSet
import com.fractanomics.crosstraining.data.model.BlockWithSets
import com.fractanomics.crosstraining.data.model.Cycle
import com.fractanomics.crosstraining.data.model.CycleType
import com.fractanomics.crosstraining.data.model.DailyLog
import com.fractanomics.crosstraining.data.model.Exercise
import com.fractanomics.crosstraining.data.model.MetricType
import com.fractanomics.crosstraining.data.model.SessionBlock
import com.fractanomics.crosstraining.data.model.SessionWithBlocks
import com.fractanomics.crosstraining.data.model.WeightEntry
import java.math.BigDecimal
import java.math.RoundingMode
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.time.format.TextStyle
import java.time.temporal.ChronoUnit
import java.util.Locale

/**
 * Result representation for workout block adherence.
 *
 * @property completedBlocks Number of blocks marked completed.
 * @property totalBlocks Total number of session blocks evaluated.
 * @property percentage Completion percentage in [0.0, 100.0], or null when undefined / "n/a".
 */
data class BlockAdherenceResult(
    val completedBlocks: Int,
    val totalBlocks: Int,
    val percentage: Double?
) {
    val displayText: String
        get() = if (percentage != null) {
            if (percentage % 1.0 == 0.0) "${percentage.toInt()}%"
            else String.format(Locale.US, "%.1f%%", percentage)
        } else {
            "n/a"
        }
}

/**
 * Aggregated cardio metrics for a single day of the week across an elapsed training cycle.
 *
 * @property dayOfWeek The day of the week (Monday through Sunday).
 * @property totalMinutes Total accumulated cardio minutes on this weekday in the elapsed window.
 * @property elapsedOccurrences Number of times this weekday occurred in the elapsed cycle window.
 * @property averageMinutes Average cardio minutes: totalMinutes / max(1, elapsedOccurrences).
 */
data class DayOfWeekCardio(
    val dayOfWeek: DayOfWeek,
    val totalMinutes: Double,
    val elapsedOccurrences: Int,
    val averageMinutes: Double
)

/**
 * Result representation for scheduled intermittent fasting adherence.
 *
 * @property completedFastDays Number of eligible fast days successfully completed.
 * @property totalEligibleFastDays Number of scheduled fast days evaluated in the elapsed window.
 * @property percentage Adherence percentage in [0.0, 100.0], or null when denominator is 0 ("n/a").
 */
data class FastingAdherenceResult(
    val completedFastDays: Int,
    val totalEligibleFastDays: Int,
    val percentage: Double?
) {
    val displayText: String
        get() = if (percentage != null) {
            if (percentage % 1.0 == 0.0) "${percentage.toInt()}%"
            else String.format(Locale.US, "%.1f%%", percentage)
        } else {
            "n/a"
        }
}

/**
 * Result representation for cycle weight progress evaluation in Fat Loss mode.
 *
 * @property startingWeightKg Starting baseline weight of the cycle, or null if unset.
 * @property targetWeightKg Target goal weight of the cycle, or null if unset.
 * @property currentWeightKg Current or latest weigh-in log, or null if unset.
 * @property deltaKg Weight lost so far (startingWeightKg - currentWeightKg), or null if undefined.
 * @property percentToTarget Completion percentage in [0.0, 100.0] (or beyond if exceeded),
 *                           or null when target >= start, non-positive, or any required weight is null ("n/a").
 */
data class WeightProgressResult(
    val startingWeightKg: Double?,
    val targetWeightKg: Double?,
    val currentWeightKg: Double?,
    val deltaKg: Double?,
    val percentToTarget: Double?
) {
    val displayText: String
        get() = percentToTargetDisplayText

    val percentToTargetDisplayText: String
        get() = if (percentToTarget != null) {
            val roundedInt = kotlin.math.round(percentToTarget).toInt()
            if (kotlin.math.abs(percentToTarget - roundedInt) < 1e-6) "${roundedInt}%"
            else String.format(Locale.US, "%.1f%%", percentToTarget)
        } else {
            "n/a"
        }

    val deltaDisplayText: String
        get() = if (deltaKg != null) {
            String.format(Locale.US, "%.1f kg", deltaKg)
        } else {
            "n/a"
        }
}

/**
 * Fasting evaluation status for an individual calendar day.
 */
enum class FastingDayStatus(val label: String) {
    NOT_SCHEDULED("Not Scheduled"),
    COMPLETED("Done"),
    BROKEN("Not Done"),
    PENDING("Pending"),
    MISSED("Missed"),
    UPCOMING("Upcoming")
}

/**
 * Directional trend classification for weekly body weight rate of change.
 *
 * Mapped to UI iconography:
 * - [LOSS]: Weekly change < -0.05 (tertiary / downward arrow ▼)
 * - [GAIN]: Weekly change > +0.05 (error / upward arrow ▲)
 * - [NEUTRAL]: Weekly change in [-0.05, +0.05] (neutral)
 */
enum class PaceTrend {
    LOSS,
    GAIN,
    NEUTRAL
}

/**
 * Result representation for signed weekly pace rate of change.
 *
 * @property rateKgPerWeek Signed rate in kg/wk: (W_current - W_anchor) / elapsedDays * 7 (loss is negative).
 * @property anchorWeightKg Weight at the resolved anchor date in kg.
 * @property anchorDate Date of the resolved anchor entry.
 * @property currentWeightKg Current or latest weight in kg.
 * @property currentDate Date of the current weight entry.
 * @property elapsedDays Actual elapsed calendar days between anchor and current entry.
 * @property trend [PaceTrend] categorization for UI iconography and coloring.
 */
data class PaceRateResult(
    val rateKgPerWeek: Double,
    val anchorWeightKg: Double,
    val anchorDate: LocalDate,
    val currentWeightKg: Double,
    val currentDate: LocalDate,
    val elapsedDays: Long,
    val trend: PaceTrend
) {
    /**
     * Positive deficit velocity in kg/wk: -rateKgPerWeek.
     */
    val deficitVelocityKgPerWeek: Double
        get() = -rateKgPerWeek

    /**
     * Formats the weekly rate for UI chip display with directional sign and localized unit.
     * E.g., "-0.5 kg/wk", "+0.4 kg/wk", "0.0 kg/wk", or "-1.1 lbs/wk".
     */
    fun formatDisplay(unit: String = "kg"): String {
        val rateInUnit = if (unit.equals("lbs", ignoreCase = true)) {
            WeightAnalytics.kgToLbs(rateKgPerWeek)
        } else {
            rateKgPerWeek
        }
        val unitLabel = if (unit.equals("lbs", ignoreCase = true)) "lbs/wk" else "kg/wk"
        return when {
            rateInUnit < -0.05 -> String.format(Locale.US, "%.1f %s", rateInUnit, unitLabel)
            rateInUnit > 0.05 -> String.format(Locale.US, "+%.1f %s", rateInUnit, unitLabel)
            else -> String.format(Locale.US, "0.0 %s", unitLabel)
        }
    }

    /**
     * Formats the weekly rate to 2 decimal places for UI chip and multi-horizon display.
     * E.g., "-0.28 kg/wk", "+0.15 kg/wk", "0.00 kg/wk", or "-0.62 lbs/wk".
     */
    fun formatDisplay2(unit: String = "kg"): String {
        val rateInUnit = if (unit.equals("lbs", ignoreCase = true)) {
            WeightAnalytics.kgToLbs(rateKgPerWeek)
        } else {
            rateKgPerWeek
        }
        val rounded2 = FatLossAnalytics.round2(rateInUnit)
        val unitLabel = if (unit.equals("lbs", ignoreCase = true)) "lbs/wk" else "kg/wk"
        return when {
            rateInUnit < -0.05 -> String.format(Locale.US, "%.2f %s", rounded2, unitLabel)
            rateInUnit > 0.05 -> String.format(Locale.US, "+%.2f %s", rounded2, unitLabel)
            else -> String.format(Locale.US, "0.00 %s", unitLabel)
        }
    }
}

/**
 * Sealed hierarchy representing the goal pace forecast state.
 *
 * Follows strict precedence order:
 * 1. [GoalReached]: W_current <= W_target (when W_target is set)
 * 2. [CycleEnded]: today > cycle.endDate (when endDate is set)
 * 3. [NotEnoughData]: < 2 entries or span < 7 calendar days
 * 4. [Stalled]: Active deficit velocity <= 0.0 (rounded to 0.1)
 * 5. [Projected]: Positive deficit velocity with projected weeks and target calendar date (capped at 52w)
 */
sealed class PaceForecast {
    abstract val displayText: String

    /**
     * Target goal weight has been achieved or surpassed.
     */
    object GoalReached : PaceForecast() {
        override val displayText: String = "Goal reached! 🎉"
    }

    /**
     * Cycle has concluded and athlete is beyond the scheduled end date.
     */
    object CycleEnded : PaceForecast() {
        override val displayText: String = "Cycle ended"
    }

    /**
     * Insufficient sampling history (< 2 entries or entries span < 7 calendar days).
     */
    object NotEnoughData : PaceForecast() {
        override val displayText: String = "Logging check-ins for 7 days enables pace projections"
    }

    /**
     * Weight loss pace is stalled or gaining (deficit velocity <= 0.0). Projections suppressed.
     */
    object Stalled : PaceForecast() {
        override val displayText: String = "Pace stalled — insufficient deficit to project"
    }

    /**
     * Valid positive deficit projection toward target weight.
     *
     * @property weeks Estimated weeks to reach goal, capped at 52.0.
     * @property targetDate Projected calendar date of goal arrival, or null if capped (> 52w).
     * @property rawWeeks Uncapped calculated weeks.
     * @property paceWindowDays Window (14 or 7) of the pace tier that drove the projection, or null when unknown.
     * @property paceRateKgPerWeek Signed weekly rate of that pace tier in kg/wk, or null when unknown.
     */
    data class Projected(
        val weeks: Double,
        val targetDate: LocalDate? = null,
        val rawWeeks: Double = weeks,
        val paceWindowDays: Int? = null,
        val paceRateKgPerWeek: Double? = null
    ) : PaceForecast() {
        val isCapped: Boolean
            get() = targetDate == null || rawWeeks > 52.0

        override val displayText: String
            get() = if (isCapped) {
                "Estimated: > 1 year"
            } else {
                String.format(Locale.US, "Estimated: %.1f weeks", weeks)
            }
    }
}

/**
 * Result of computing the required weekly weight-loss pace to reach the cycle's target weight
 * on or before the cycle's configured [endDate].
 *
 * @property requiredRateInUnit Signed weekly weight-loss pace in [weightUnit] per week (always <= -0.1).
 * @property remainingWeightInUnit Positive 1-decimal rounded remaining weight to goal in [weightUnit].
 * @property daysRemaining Strict positive calendar days between evaluation date and [endDate].
 * @property weeksRemaining Exact calendar weeks remaining (`daysRemaining / 7.0`).
 * @property endDate Cycle target end date.
 * @property weightUnit Active display unit ("kg" or "lbs").
 * @property exceedsSustainableRate True when the required weekly loss is above
 *   [FatLossAnalytics.MAX_SUSTAINABLE_WEEKLY_LOSS_FRACTION] of the current body weight.
 */
data class RequiredPaceResult(
    val requiredRateInUnit: Double,
    val remainingWeightInUnit: Double,
    val daysRemaining: Long,
    val weeksRemaining: Double,
    val endDate: LocalDate,
    val weightUnit: String,
    val exceedsSustainableRate: Boolean = false
) {
    val requiredRate2InUnit: Double
        get() = -maxOf(0.01, FatLossAnalytics.round2(remainingWeightInUnit / weeksRemaining))

    fun formatRate(): String {
        val unitLabel = if (weightUnit.equals("lbs", ignoreCase = true)) "lbs/wk" else "kg/wk"
        return String.format(Locale.US, "%.1f %s", requiredRateInUnit, unitLabel)
    }

    fun formatRate2(): String {
        val unitLabel = if (weightUnit.equals("lbs", ignoreCase = true)) "lbs/wk" else "kg/wk"
        return String.format(Locale.US, "%.2f %s", requiredRate2InUnit, unitLabel)
    }

    fun formatEndDate(): String =
        endDate.format(DateTimeFormatter.ofPattern("d MMM yyyy", Locale.US))

    fun formatSubtitle(): String {
        val base = "Required pace: ${formatRate()} (by ${formatEndDate()})"
        return if (exceedsSustainableRate) "$base — above 1% bodyweight/wk" else base
    }

    fun formatBenchmarkHeader(): String {
        val weeksStr = String.format(Locale.US, "%.1f", weeksRemaining)
        val base = "Target: ${formatEndDate()} (${weeksStr}w left) · Required: ${formatRate2()}"
        return if (exceedsSustainableRate) "$base · ⚠️ >1% BW/wk" else base
    }
}

/**
 * Sealed hierarchy representing the visual Multi-Horizon Goal Estimations card state (7D, 14D, 21D, 28D).
 */
sealed class MultiHorizonForecastState {
    abstract val displayText: String

    data object GoalReached : MultiHorizonForecastState() {
        override val displayText: String = "Goal reached! 🎉"
    }

    data object CycleEnded : MultiHorizonForecastState() {
        override val displayText: String = "Cycle ended"
    }

    data object NotEnoughData : MultiHorizonForecastState() {
        override val displayText: String = "Logging check-ins for 7 days enables pace projections"
    }

    data class Active(
        val remainingWeightInUnit: Double,
        val targetWeightInUnit: Double,
        val weightUnit: String,
        val requiredPace: RequiredPaceResult?,
        val rows: List<HorizonEstimationRow>
    ) : MultiHorizonForecastState() {
        override val displayText: String = "GOAL ARRIVAL ESTIMATIONS"

        val benchmarkHeader: String
            get() = formatHeaderSubtitle()

        fun formatHeaderSubtitle(): String {
            if (requiredPace != null) {
                return requiredPace.formatBenchmarkHeader()
            }
            val unitLabel = if (weightUnit.equals("lbs", ignoreCase = true)) "lbs" else "kg"
            return String.format(
                Locale.US,
                "Goal: %.1f %s (-%.1f %s left)",
                targetWeightInUnit,
                unitLabel,
                remainingWeightInUnit,
                unitLabel
            )
        }
    }
}

/**
 * Individual horizon estimation row for 7D, 14D, 21D, or 28D pace.
 */
data class HorizonEstimationRow(
    val windowDays: Int,
    val state: HorizonRowState
) {
    fun formatLeftLabel(): String = when (state) {
        is HorizonRowState.Projected -> "${windowDays}D (${state.paceDisplay2})"
        is HorizonRowState.Stalled -> "${windowDays}D (${state.paceDisplay2})"
        is HorizonRowState.NotEnoughData -> "${windowDays}D (—)"
    }

    fun formatRightValue(): String = when (state) {
        is HorizonRowState.Projected -> if (state.isCapped || state.targetDate == null) {
            "> 1 year"
        } else {
            val dateStr = state.targetDate.format(DateTimeFormatter.ofPattern("d MMM yyyy", Locale.US))
            String.format(Locale.US, "%.1f wks · %s", state.weeks, dateStr)
        }
        is HorizonRowState.Stalled -> "Pace stalled"
        is HorizonRowState.NotEnoughData -> "Needs ${windowDays}+ days"
    }
}

/**
 * Per-horizon estimation row state inside [MultiHorizonForecastState.Active].
 */
sealed interface HorizonRowState {
    val windowDays: Int

    data class NotEnoughData(
        override val windowDays: Int
    ) : HorizonRowState

    data class Stalled(
        override val windowDays: Int,
        val paceDisplay2: String
    ) : HorizonRowState

    data class Projected(
        override val windowDays: Int,
        val paceDisplay2: String,
        val velocity2InUnit: Double,
        val weeks: Double,
        val rawWeeks: Double = weeks,
        val targetDate: LocalDate? = null,
        val barFraction: Float,
        val weeksDeltaVsTarget: Double? = null,
        val isOnTrack: Boolean = true,
        val deltaBadgeText: String? = null
    ) : HorizonRowState {
        val isCapped: Boolean
            get() = targetDate == null || rawWeeks > 52.0

        val arrivalDate: LocalDate?
            get() = targetDate

        val isOverOneYear: Boolean
            get() = isCapped

        val sufficiencyFraction: Float
            get() = barFraction

        val badgeText: String?
            get() = deltaBadgeText
    }
}

/**
 * Sealed hierarchy representing the state of the pace & linear targets container.
 */
sealed interface PaceTargetsState {
    data object Unavailable : PaceTargetsState
    data object Stalled : PaceTargetsState
    data object GoalReached : PaceTargetsState
    data object CycleEnded : PaceTargetsState
    data class Available(
        val today: ColumnState = ColumnState.InsufficientHistory,
        val tomorrow: ColumnState = ColumnState.InsufficientHistory,
        val sevenDayAvgRow: TargetRowState = TargetRowState(
            linearToday = ColumnState.InsufficientHistory,
            expectedToday = today,
            targetTomorrow = tomorrow,
            windowDays = 7
        ),
        val fourteenDayAvgRow: TargetRowState = TargetRowState(
            linearToday = ColumnState.InsufficientHistory,
            expectedToday = ColumnState.InsufficientHistory,
            targetTomorrow = ColumnState.InsufficientHistory,
            windowDays = 14
        ),
        val twentyOneDayAvgRow: TargetRowState = TargetRowState(
            linearToday = ColumnState.InsufficientHistory,
            expectedToday = ColumnState.InsufficientHistory,
            targetTomorrow = ColumnState.InsufficientHistory,
            windowDays = 21
        ),
        val twentyEightDayAvgRow: TargetRowState = TargetRowState(
            linearToday = ColumnState.InsufficientHistory,
            expectedToday = ColumnState.InsufficientHistory,
            targetTomorrow = ColumnState.InsufficientHistory,
            windowDays = 28
        )
    ) : PaceTargetsState {
        val rows: List<TargetRowState>
            get() = listOf(
                sevenDayAvgRow,
                fourteenDayAvgRow,
                twentyOneDayAvgRow,
                twentyEightDayAvgRow
            )
    }
}

/**
 * 3-column target row state for a specific rolling average window (7D, 14D, 21D, or 28D).
 */
data class TargetRowState(
    val linearToday: ColumnState,
    val expectedToday: ColumnState,
    val targetTomorrow: ColumnState,
    val windowDays: Int = 7
) {
    val rowHeader: String
        get() = "${windowDays}D ROLLING AVERAGE"
}

/**
 * Sealed hierarchy representing the state of an individual pace or linear target column.
 */
sealed interface ColumnState {
    data class Value(
        val targetWeightDisplay: Double,
        val deltaDisplay: Double?,
        val status: PaceTargetStatus? = null,
        val unitLabel: String,
        val baselineWeightDisplay: Double? = null,
        val isDailyStep: Boolean = false,
        val useTwoDecimals: Boolean = false
    ) : ColumnState

    data class AlreadyBelowTarget(
        val targetWeightDisplay: Double,
        val marginDisplay: Double,
        val unitLabel: String,
        val baselineWeightDisplay: Double? = null
    ) : ColumnState

    data class NeedsLog(
        val requiredDate: LocalDate
    ) : ColumnState

    data object NeedsTodayLog : ColumnState

    data object InsufficientHistory : ColumnState
}

/**
 * Pace target tracking status for Expected Today.
 */
enum class PaceTargetStatus {
    AHEAD, ON_PACE, OFF_PACE
}

/**
 * Result representation for progressive moving average of body weight.
 *
 * @property averageKg Arithmetic mean weight across active in-cycle entries in window.
 * @property windowSpanDays Window span in calendar days: min(elapsedDaysFromStart + 1, maxWindowDays).
 * @property loggedDaysCount Number of logged weigh-in entries within the window.
 */
data class ProgressiveAverageResult(
    val averageKg: Double,
    val windowSpanDays: Int,
    val loggedDaysCount: Int
)

/**
 * Result representation for rolling window averages and weekly deltas in WeightProgressionHeroCard.
 */
data class RollingWeightHeroState(
    val today7dAvgKg: Double?,
    val lastWeek7dAvgKg: Double?,
    val delta7dKg: Double?,
    val today14dAvgKg: Double?,
    val delta14dKg: Double?,
    val loggedDaysCount14d: Int
)

/**
 * Result representation for the secondary 3-column raw same-day weigh-in comparison row.
 * Compares raw weigh-ins on identical weekdays across t, t-7d, and t-14d.
 *
 * @property currentDailyWeightKg Raw weigh-in at referenceDate (t), or null if unlogged.
 * @property lastWeekSameDayWeightKg Raw weigh-in at t-7d within cycle, or null if missing/pre-cycle.
 * @property deltaCurrentVsLastWeekSameDayKg diff1(W_t, W_{t-7d}), or null if either is null.
 * @property twoWeeksAgoSameDayWeightKg Raw weigh-in at t-14d within cycle, or null if missing/pre-cycle.
 * @property deltaCurrentVsTwoWeeksAgoSameDayKg diff1(W_t, W_{t-14d}), or null if either is null.
 */
data class SameDayWeightTrioState(
    val currentDailyWeightKg: Double?,
    val lastWeekSameDayWeightKg: Double?,
    val deltaCurrentVsLastWeekSameDayKg: Double?,
    val twoWeeksAgoSameDayWeightKg: Double?,
    val deltaCurrentVsTwoWeeksAgoSameDayKg: Double?
)

typealias DayOverDayDelta = FatLossAnalytics.DayOverDayDelta
typealias PaceComparisonResult = FatLossAnalytics.PaceComparisonResult

/**
 * Pure Kotlin domain analytics engine for Fat Loss & Bodybuilding cycles.
 *
 * Responsibilities:
 * 1. Calculate Daily & Cycle Block Adherence (%) across [SessionBlock] entities.
 *    Rest days with 0 blocks and empty workout days evaluate to "n/a" (null) to avoid unfair 0% penalties.
 * 2. Calculate Cardio Minutes strictly for [BlockKind.CARDIO] blocks, using [SessionBlock.resultValue]
 *    as the primary source of truth, with fallback to [MetricType.TIME] sets (seconds / 60.0).
 * 3. Calculate Day-of-Week (Mon–Sun) Cardio Averages using sum / max(1, ElapsedWeekdayCount(w)) across
 *    the elapsed cycle window, eliminating division-by-zero on Day 1 or unreached days.
 * 4. Calculate Intermittent Fasting Adherence across scheduled fast days in [start, yesterday] plus today
 *    once reported. Past unlogged fast days count as broken/not done. Zero denominator evaluates to "n/a".
 */
object FatLossAnalytics {

    /**
     * Upper bound of a sustainable weekly loss as a fraction of current body weight (~1%/wk, the common
     * guideline for preserving lean mass during a cut). Required paces above it are flagged, not capped.
     */
    const val MAX_SUSTAINABLE_WEEKLY_LOSS_FRACTION = 0.01

    // =========================================================================
    // Noise Deadband Constants & Presentation Models
    // =========================================================================

    const val NOISE_THRESHOLD_KG = 0.05
    const val PACE_NOISE_THRESHOLD_KG_PER_WEEK = 0.05
    const val NO_DATA_PLACEHOLDER = "—"

    private val shortDateFormatter: DateTimeFormatter =
        DateTimeFormatter.ofPattern("d MMM", Locale.US)

    data class DayOverDayDelta(
        val deltaKg: Double,
        val priorDate: LocalDate,
        val currentDate: LocalDate,
        val isConsecutive: Boolean
    )

    data class PaceComparisonResult(
        val currentRateKgPerWeek: Double,
        val priorRateKgPerWeek: Double,
        val deltaRateKgPerWeek: Double,
        val percentChange: Double?, // null when not loss-to-loss or |priorRate| < 0.1
        val isAcceleratingDeficit: Boolean,
        val priorDate: LocalDate,
        val isConsecutive: Boolean = false,
        val isExactDayMatch: Boolean = false
    )

    // =========================================================================
    // Bitmask Helpers for Days of Week
    // =========================================================================

    /**
     * Converts a [DayOfWeek] to its corresponding 0-indexed bit value (Monday = bit 0 -> 1, Sunday = bit 6 -> 64).
     */
    fun dayOfWeekToBit(dayOfWeek: DayOfWeek): Int = 1 shl (dayOfWeek.value - 1)

    /**
     * Encodes an iterable of [DayOfWeek] into an integer bitmask.
     */
    fun createDayOfWeekMask(days: Iterable<DayOfWeek>): Int =
        days.fold(0) { mask, d -> mask or dayOfWeekToBit(d) }

    /**
     * Decodes an integer bitmask into a set of [DayOfWeek].
     */
    fun maskToDayOfWeekSet(mask: Int): Set<DayOfWeek> =
        DayOfWeek.values().filter { isDayInMask(it, mask) }.toSet()

    /**
     * Checks if a [DayOfWeek] is present in the specified bitmask.
     */
    fun isDayInMask(dayOfWeek: DayOfWeek, mask: Int): Boolean =
        (mask and dayOfWeekToBit(dayOfWeek)) != 0

    /**
     * Checks if a given calendar date corresponds to a scheduled fast day in the bitmask.
     */
    fun isScheduledFastDay(date: LocalDate, fastDaysOfWeekMask: Int): Boolean =
        isDayInMask(date.dayOfWeek, fastDaysOfWeekMask)

    /**
     * Checks if a given calendar date corresponds to a scheduled rest day in the bitmask.
     */
    fun isScheduledRestDay(date: LocalDate, restDaysOfWeekMask: Int): Boolean =
        isDayInMask(date.dayOfWeek, restDaysOfWeekMask)

    // =========================================================================
    // 1. Daily Block Adherence
    // =========================================================================

    /**
     * Computes daily block adherence for a collection of [SessionBlock] entities.
     *
     * Invariants:
     * - Formula: completedBlocks / totalBlocks * 100.0
     * - Only [SessionBlock.isCompleted] is the authority.
     * - If [blocks] is empty or if [isRestDay] is true with 0 blocks, returns null ("n/a").
     *
     * @param blocks All [SessionBlock] items logged across all sessions on this calendar day.
     * @param isRestDay Whether this calendar day is marked/scheduled as a rest day.
     * @return [BlockAdherenceResult] with completed count, total count, and adherence percentage or null ("n/a").
     */
    fun computeDailyBlockAdherence(
        blocks: List<SessionBlock>,
        isRestDay: Boolean = false
    ): BlockAdherenceResult {
        if (blocks.isEmpty() || (isRestDay && blocks.isEmpty())) {
            return BlockAdherenceResult(completedBlocks = 0, totalBlocks = 0, percentage = null)
        }
        val completed = blocks.count { it.isCompleted }
        val pct = (completed.toDouble() / blocks.size.toDouble()) * 100.0
        return BlockAdherenceResult(
            completedBlocks = completed,
            totalBlocks = blocks.size,
            percentage = pct
        )
    }

    /**
     * Computes daily block adherence percentage directly, returning null for "n/a".
     */
    fun computeDailyBlockAdherencePercentage(
        blocks: List<SessionBlock>,
        isRestDay: Boolean = false
    ): Double? = computeDailyBlockAdherence(blocks, isRestDay).percentage

    /**
     * Computes daily block adherence from full [SessionWithBlocks] sessions on that date.
     */
    fun computeDailyBlockAdherenceFromSessions(
        sessions: List<SessionWithBlocks>,
        isRestDay: Boolean = false
    ): BlockAdherenceResult {
        val allBlocks = sessions.flatMap { it.blocks.map { bws -> bws.block } }
        return computeDailyBlockAdherence(allBlocks, isRestDay)
    }

    /**
     * Computes cycle-level block adherence by averaging non-null daily percentages.
     * Rest days and zero-block days (evaluating to null / "n/a") are excluded from the average.
     */
    fun computeCycleBlockAdherence(dailyAdherences: Collection<Double?>): Double? {
        val validDays = dailyAdherences.filterNotNull()
        if (validDays.isEmpty()) return null
        return validDays.average()
    }

    /**
     * Computes overall block adherence across all [SessionBlock] entities directly.
     */
    fun computeOverallBlockAdherence(blocks: List<SessionBlock>): Double? {
        if (blocks.isEmpty()) return null
        val completed = blocks.count { it.isCompleted }
        return (completed.toDouble() / blocks.size.toDouble()) * 100.0
    }

    // =========================================================================
    // 2. Cardio Minutes
    // =========================================================================

    /**
     * Computes cardio duration in minutes for an individual workout block.
     *
     * Invariants:
     * - Only [BlockKind.CARDIO] blocks contribute minutes; all other kinds evaluate to 0.0.
     * - [SessionBlock.resultValue] is the primary duration source (in minutes).
     * - If [resultValue] is null (or <= 0.0), falls back to sets where metric is [MetricType.TIME],
     *   summing metricValue in seconds and converting to minutes (/ 60.0).
     * - Sets with non-TIME metric types (Calories, Distance, Reps, Weight) contribute 0.0 minutes.
     *
     * @param block The [SessionBlock] to evaluate.
     * @param sets Associated [BlockSet] entries for this block.
     * @param metricType The [MetricType] associated with the exercise, if known.
     * @param onlyCompleted If true, blocks with isCompleted == false are ignored. Defaults to false.
     * @return Duration in minutes (>= 0.0).
     */
    fun computeBlockCardioMinutes(
        block: SessionBlock,
        sets: List<BlockSet> = emptyList(),
        metricType: MetricType? = null,
        onlyCompleted: Boolean = false
    ): Double {
        if (block.kind != BlockKind.CARDIO) {
            return 0.0
        }
        if (onlyCompleted && !block.isCompleted) {
            return 0.0
        }

        // 1. Primary: resultValue in minutes
        if (block.resultValue != null && block.resultValue > 0.0) {
            return block.resultValue
        }

        // 2. Fallback: MetricType.TIME sets in seconds -> minutes
        if (metricType == MetricType.TIME) {
            val totalSeconds = sets.sumOf { set ->
                val v = set.metricValue
                if (v != null && v > 0.0) v else 0.0
            }
            return totalSeconds / 60.0
        }

        return block.resultValue?.coerceAtLeast(0.0) ?: 0.0
    }

    /**
     * Computes cardio minutes for a [BlockWithSets] relation using an exercise metric lookup.
     */
    fun computeBlockWithSetsCardioMinutes(
        blockWithSets: BlockWithSets,
        exerciseMetricLookup: (Long) -> MetricType? = { null },
        onlyCompleted: Boolean = false
    ): Double {
        val metricType = blockWithSets.block.mainExerciseId?.let { exerciseMetricLookup(it) }
        return computeBlockCardioMinutes(
            block = blockWithSets.block,
            sets = blockWithSets.sets,
            metricType = metricType,
            onlyCompleted = onlyCompleted
        )
    }

    /**
     * Computes cardio minutes for a [BlockWithSets] relation using an Exercise map.
     */
    fun computeBlockWithSetsCardioMinutes(
        blockWithSets: BlockWithSets,
        exercises: Map<Long, Exercise>,
        onlyCompleted: Boolean = false
    ): Double = computeBlockWithSetsCardioMinutes(
        blockWithSets = blockWithSets,
        exerciseMetricLookup = { id -> exercises[id]?.metricType },
        onlyCompleted = onlyCompleted
    )

    /**
     * Computes total cardio minutes across a list of [BlockWithSets].
     */
    fun computeTotalCardioMinutes(
        blocksWithSets: List<BlockWithSets>,
        exerciseMetricLookup: (Long) -> MetricType? = { null },
        onlyCompleted: Boolean = false
    ): Double {
        return blocksWithSets.sumOf {
            computeBlockWithSetsCardioMinutes(it, exerciseMetricLookup, onlyCompleted)
        }
    }

    /**
     * Computes total cardio minutes across a list of [BlockWithSets] with an Exercise map.
     */
    fun computeTotalCardioMinutes(
        blocksWithSets: List<BlockWithSets>,
        exercises: Map<Long, Exercise>,
        onlyCompleted: Boolean = false
    ): Double = computeTotalCardioMinutes(
        blocksWithSets = blocksWithSets,
        exerciseMetricLookup = { id -> exercises[id]?.metricType },
        onlyCompleted = onlyCompleted
    )

    /**
     * Groups sessions by date and computes daily cardio minutes.
     */
    fun computeDailyCardioMinutes(
        sessions: List<SessionWithBlocks>,
        exercises: Map<Long, Exercise> = emptyMap(),
        onlyCompleted: Boolean = false
    ): Map<LocalDate, Double> {
        return sessions
            .groupBy { it.session.date }
            .mapValues { (_, daySessions) ->
                daySessions.sumOf { s ->
                    computeTotalCardioMinutes(
                        blocksWithSets = s.blocks,
                        exercises = exercises,
                        onlyCompleted = onlyCompleted
                    )
                }
            }
    }

    // =========================================================================
    // 3. Day-of-Week Cardio Averages
    // =========================================================================

    /**
     * Computes Day-of-Week cardio averages (Monday through Sunday) across an elapsed cycle window.
     *
     * Invariants:
     * - Formula: sum(minutes on weekday w) / max(1, ElapsedWeekdayCount(w))
     * - Elapsed window spans [cycleStartDate, min(referenceDate, cycleEndDate ?: referenceDate)].
     * - If referenceDate < cycleStartDate, elapsed occurrences are 0 and denominator is max(1, 0) = 1.
     *
     * @param cycleStartDate Start date of the cycle.
     * @param referenceDate Current evaluation date (anchor, typically today).
     * @param cycleEndDate Optional completion date if the cycle has ended.
     * @param dailyCardioMinutes Map of calendar dates to total cardio minutes logged on that date.
     * @return Map of [DayOfWeek] to [DayOfWeekCardio] aggregated statistics.
     */
    fun computeDayOfWeekCardioAverages(
        cycleStartDate: LocalDate,
        referenceDate: LocalDate = LocalDate.now(),
        cycleEndDate: LocalDate? = null,
        dailyCardioMinutes: Map<LocalDate, Double>
    ): Map<DayOfWeek, DayOfWeekCardio> {
        val windowEnd = if (cycleEndDate != null && cycleEndDate.isBefore(referenceDate)) {
            cycleEndDate
        } else {
            referenceDate
        }

        // Count elapsed occurrences for each weekday in [cycleStartDate, windowEnd]
        val occurrencesMap = mutableMapOf<DayOfWeek, Int>().apply {
            DayOfWeek.values().forEach { put(it, 0) }
        }

        if (!windowEnd.isBefore(cycleStartDate)) {
            var curr = cycleStartDate
            while (!curr.isAfter(windowEnd)) {
                occurrencesMap[curr.dayOfWeek] = (occurrencesMap[curr.dayOfWeek] ?: 0) + 1
                curr = curr.plusDays(1)
            }
        }

        // Sum cardio minutes for each weekday within the cycle window
        val minutesMap = mutableMapOf<DayOfWeek, Double>().apply {
            DayOfWeek.values().forEach { put(it, 0.0) }
        }

        dailyCardioMinutes.forEach { (date, minutes) ->
            if (!date.isBefore(cycleStartDate) && !date.isAfter(windowEnd)) {
                minutesMap[date.dayOfWeek] = (minutesMap[date.dayOfWeek] ?: 0.0) + minutes
            }
        }

        return DayOfWeek.values().associateWith { dayOfWeek ->
            val total = minutesMap[dayOfWeek] ?: 0.0
            val occurrences = occurrencesMap[dayOfWeek] ?: 0
            val denominator = maxOf(1, occurrences)
            DayOfWeekCardio(
                dayOfWeek = dayOfWeek,
                totalMinutes = total,
                elapsedOccurrences = occurrences,
                averageMinutes = total / denominator
            )
        }
    }

    /**
     * Returns an ordered list (Monday through Sunday) of [DayOfWeekCardio] statistics.
     */
    fun computeDayOfWeekCardioList(
        cycleStartDate: LocalDate,
        referenceDate: LocalDate = LocalDate.now(),
        cycleEndDate: LocalDate? = null,
        dailyCardioMinutes: Map<LocalDate, Double>
    ): List<DayOfWeekCardio> {
        val averagesMap = computeDayOfWeekCardioAverages(
            cycleStartDate = cycleStartDate,
            referenceDate = referenceDate,
            cycleEndDate = cycleEndDate,
            dailyCardioMinutes = dailyCardioMinutes
        )
        return DayOfWeek.values().map { averagesMap.getValue(it) }
    }

    /**
     * Computes weekly total cardio minutes indexed by cycle week (Week 1, Week 2, etc.).
     */
    fun computeWeeklyCardioMinutes(
        cycleStartDate: LocalDate,
        dailyCardioMinutes: Map<LocalDate, Double>
    ): Map<Int, Double> {
        val result = mutableMapOf<Int, Double>()
        dailyCardioMinutes.forEach { (date, minutes) ->
            if (!date.isBefore(cycleStartDate)) {
                val daysBetween = ChronoUnit.DAYS.between(cycleStartDate, date)
                val weekNumber = (daysBetween / 7).toInt() + 1
                result[weekNumber] = (result[weekNumber] ?: 0.0) + minutes
            }
        }
        return result
    }

    // =========================================================================
    // 4. Fasting Adherence
    // =========================================================================

    /**
     * Computes intermittent fasting adherence over scheduled fast days.
     *
     * Invariants:
     * - Scheduled fast days in [start, yesterday] are evaluated. Past unlogged fast days count as not done.
     * - Today (referenceDate) is only included in denominator and numerator once reported (fastCompleted != null).
     *   If today is pending / unlogged, it is excluded so athlete is not penalized mid-day.
     * - Future scheduled fast days are excluded.
     * - If denominator is 0 (no scheduled fast days elapsed or reported yet), evaluates to null ("n/a").
     *
     * @param cycleStartDate Cycle start date.
     * @param fastDaysOfWeek Integer bitmask representing scheduled fast days of the week.
     * @param dailyLogs Athlete daily logs mapped by calendar date.
     * @param referenceDate Evaluation anchor (defaults to today).
     * @param cycleEndDate Optional completion date if the cycle has ended.
     * @return [FastingAdherenceResult] with completed count, total eligible count, and adherence percentage.
     */
    fun computeFastingAdherence(
        cycleStartDate: LocalDate,
        fastDaysOfWeek: Int,
        dailyLogs: Map<LocalDate, DailyLog>,
        referenceDate: LocalDate = LocalDate.now(),
        cycleEndDate: LocalDate? = null
    ): FastingAdherenceResult {
        val scheduledDays = maskToDayOfWeekSet(fastDaysOfWeek)
        return computeFastingAdherence(
            cycleStartDate = cycleStartDate,
            scheduledFastDays = scheduledDays,
            dailyLogs = dailyLogs,
            referenceDate = referenceDate,
            cycleEndDate = cycleEndDate
        )
    }

    /**
     * Overload accepting an explicit [Set<DayOfWeek>] for scheduled fast days.
     */
    fun computeFastingAdherence(
        cycleStartDate: LocalDate,
        scheduledFastDays: Set<DayOfWeek>,
        dailyLogs: Map<LocalDate, DailyLog>,
        referenceDate: LocalDate = LocalDate.now(),
        cycleEndDate: LocalDate? = null
    ): FastingAdherenceResult {
        if (scheduledFastDays.isEmpty() || referenceDate.isBefore(cycleStartDate)) {
            return FastingAdherenceResult(completedFastDays = 0, totalEligibleFastDays = 0, percentage = null)
        }

        val effectiveEndDate = if (cycleEndDate != null && cycleEndDate.isBefore(referenceDate)) {
            cycleEndDate
        } else {
            referenceDate
        }

        if (effectiveEndDate.isBefore(cycleStartDate)) {
            return FastingAdherenceResult(completedFastDays = 0, totalEligibleFastDays = 0, percentage = null)
        }

        var completedCount = 0
        var eligibleCount = 0

        // 1. Past days in [cycleStartDate, minOf(yesterday, effectiveEndDate)]
        val yesterday = referenceDate.minusDays(1)
        val pastEndDate = if (effectiveEndDate.isBefore(yesterday)) effectiveEndDate else yesterday

        if (!pastEndDate.isBefore(cycleStartDate)) {
            var curr = cycleStartDate
            while (!curr.isAfter(pastEndDate)) {
                if (scheduledFastDays.contains(curr.dayOfWeek)) {
                    eligibleCount++
                    val log = dailyLogs[curr]
                    // Past unlogged = not done
                    if (log?.fastCompleted == true) {
                        completedCount++
                    }
                }
                curr = curr.plusDays(1)
            }
        }

        // 2. Today (referenceDate) if within cycle and not after effectiveEndDate
        if (!referenceDate.isAfter(effectiveEndDate) && scheduledFastDays.contains(referenceDate.dayOfWeek)) {
            val todayLog = dailyLogs[referenceDate]
            // "plus today once reported"
            if (todayLog?.fastCompleted != null) {
                eligibleCount++
                if (todayLog.fastCompleted == true) {
                    completedCount++
                }
            }
        }

        val percentage = if (eligibleCount > 0) {
            (completedCount.toDouble() / eligibleCount.toDouble()) * 100.0
        } else {
            null
        }

        return FastingAdherenceResult(
            completedFastDays = completedCount,
            totalEligibleFastDays = eligibleCount,
            percentage = percentage
        )
    }

    /**
     * Determines the [FastingDayStatus] of a specific calendar day.
     */
    fun getDailyFastingStatus(
        date: LocalDate,
        fastDaysOfWeekMask: Int,
        dailyLog: DailyLog?,
        referenceDate: LocalDate = LocalDate.now()
    ): FastingDayStatus {
        val isScheduled = isScheduledFastDay(date, fastDaysOfWeekMask)
        if (!isScheduled) {
            return FastingDayStatus.NOT_SCHEDULED
        }
        return when {
            dailyLog?.fastCompleted == true -> FastingDayStatus.COMPLETED
            dailyLog?.fastCompleted == false -> FastingDayStatus.BROKEN
            date.isBefore(referenceDate) -> FastingDayStatus.MISSED
            date == referenceDate -> FastingDayStatus.PENDING
            else -> FastingDayStatus.UPCOMING
        }
    }

    // =========================================================================
    // 5. Rep-Max (RM) Percentage Scaling & Rounding Math
    // =========================================================================

    /**
     * Rounds a numeric value to the nearest 0.5 increment using standard half-up rounding.
     *
     * Example:
     * - 74.825 / 0.5 = 149.65 -> 150 -> 75.0
     * - 76.65 / 0.5 = 153.3 -> 153 -> 76.5
     * - 73.25 / 0.5 = 146.5 -> 147 -> 73.5
     */
    fun roundToNearestHalf(value: Double): Double {
        val doubled = BigDecimal.valueOf(value).multiply(BigDecimal.valueOf(2))
        val rounded = doubled.setScale(0, RoundingMode.HALF_UP)
        return rounded.divide(BigDecimal.valueOf(2)).toDouble()
    }

    /**
     * Alias for [roundToNearestHalf].
     */
    fun roundToHalfStep(value: Double): Double = roundToNearestHalf(value)

    /**
     * Scales a baseline Rep-Max weight by a percentage modifier [percentage] (+2.5, +5, +10, 0)
     * using the pure formula: round(startWeight * (1 + p / 100)) with half-up rounding to the
     * nearest 0.5 step increment.
     *
     * Invariants:
     * - Scaling is non-compounding and always calculated against [startWeight].
     * - Percentage of 0.0 returns [startWeight] rounded to nearest 0.5 step.
     * - Non-positive [startWeight] (<= 0.0) returns 0.0.
     *
     * @param startWeight The baseline RM load in the exercise's native unit (kg/lbs).
     * @param percentage The percentage modifier (e.g., 2.5 for +2.5%, 5.0 for +5%, 0.0 for 0%).
     * @return Scaled target load rounded half-up to nearest 0.5 increment.
     */
    fun scaleRepMax(startWeight: Double, percentage: Double): Double {
        if (startWeight <= 0.0) return 0.0
        val raw = startWeight * (1.0 + (percentage / 100.0))
        return roundToNearestHalf(raw)
    }

    fun scaleRepMax(startWeight: Double?, percentage: Double): Double? {
        if (startWeight == null || startWeight <= 0.0) return null
        return scaleRepMax(startWeight, percentage)
    }

    fun scaleRepMaxWeight(startWeight: Double, percentage: Double): Double =
        scaleRepMax(startWeight, percentage)

    fun scaleRepMaxWeight(startWeight: Double?, percentage: Double): Double? =
        scaleRepMax(startWeight, percentage)

    fun scaleRm(startWeight: Double, percentage: Double): Double =
        scaleRepMax(startWeight, percentage)

    fun scaleRm(startWeight: Double?, percentage: Double): Double? =
        scaleRepMax(startWeight, percentage)

    fun scaleRmWeight(startWeight: Double, percentage: Double): Double =
        scaleRepMax(startWeight, percentage)

    fun scaleRmWeight(startWeight: Double?, percentage: Double): Double? =
        scaleRepMax(startWeight, percentage)

    // =========================================================================
    // 6. Body Weight Delta & Percent-to-Target Analytics
    // =========================================================================

    /**
     * Computes body weight delta: W_start - W_current.
     *
     * Invariants:
     * - Returns null ("n/a") if either [startingWeightKg] or [currentWeightKg] is null.
     * - Returns null if either weight is non-positive (<= 0.0).
     *
     * @param startingWeightKg The baseline starting body weight in kg.
     * @param currentWeightKg The athlete's current / latest weigh-in log in kg.
     * @return Body weight delta in kg (positive indicates weight lost), or null when undefined.
     */
    fun computeWeightDelta(
        startingWeightKg: Double?,
        currentWeightKg: Double?
    ): Double? {
        if (startingWeightKg == null || currentWeightKg == null) return null
        if (startingWeightKg <= 0.0 || currentWeightKg <= 0.0) return null
        return startingWeightKg - currentWeightKg
    }

    /**
     * Computes completion percentage toward target weight in Fat Loss mode:
     *   (W_start - W_current) / (W_start - W_target) * 100.0
     *
     * Invariants:
     * - Guard: Returns null ("n/a") when [targetWeightKg] >= [startingWeightKg].
     * - Guard: Returns null ("n/a") when any of [startingWeightKg], [targetWeightKg], or [currentWeightKg] is null.
     * - Guard: Returns null ("n/a") when any weight is non-positive (<= 0.0).
     * - Formula calculates completion percentage on a 0.0 to 100.0 scale (or beyond if exceeded).
     *
     * @param startingWeightKg Starting baseline weight in kg.
     * @param targetWeightKg Target goal weight in kg (must be strictly < startingWeightKg).
     * @param currentWeightKg Current weight in kg.
     * @return Completion percentage in [0.0, 100.0] (or > 100.0 if surpassed), or null if guarded / undefined.
     */
    fun computePercentToTarget(
        startingWeightKg: Double?,
        targetWeightKg: Double?,
        currentWeightKg: Double? = null
    ): Double? {
        if (startingWeightKg == null || targetWeightKg == null || currentWeightKg == null) return null
        if (startingWeightKg <= 0.0 || targetWeightKg <= 0.0 || currentWeightKg <= 0.0) return null
        if (targetWeightKg >= startingWeightKg) return null

        val totalLossRequired = startingWeightKg - targetWeightKg
        val currentLoss = startingWeightKg - currentWeightKg
        return (currentLoss / totalLossRequired) * 100.0
    }

    /**
     * Computes completion ratio toward target weight (fraction in 0.0 to 1.0, not multiplied by 100.0).
     * Returns null ("n/a") under the exact same guard conditions as [computePercentToTarget].
     */
    fun computePercentToTargetRatio(
        startingWeightKg: Double?,
        targetWeightKg: Double?,
        currentWeightKg: Double? = null
    ): Double? = computePercentToTarget(startingWeightKg, targetWeightKg, currentWeightKg)?.let { it / 100.0 }

    /**
     * Computes percent-to-target for an active [Cycle] entity.
     */
    fun computePercentToTarget(
        cycle: Cycle,
        currentWeightKg: Double?
    ): Double? = computePercentToTarget(
        startingWeightKg = cycle.startingWeightKg,
        targetWeightKg = cycle.targetWeightKg,
        currentWeightKg = currentWeightKg
    )

    /**
     * Returns formatted display text for percent-to-target (e.g., "50%", "36.5%", or "n/a").
     */
    fun computePercentToTargetDisplayText(
        startingWeightKg: Double?,
        targetWeightKg: Double?,
        currentWeightKg: Double? = null
    ): String {
        val pct = computePercentToTarget(startingWeightKg, targetWeightKg, currentWeightKg)
        return if (pct != null) {
            val roundedInt = kotlin.math.round(pct).toInt()
            if (kotlin.math.abs(pct - roundedInt) < 1e-6) "${roundedInt}%"
            else String.format(Locale.US, "%.1f%%", pct)
        } else {
            "n/a"
        }
    }

    /**
     * Evaluates comprehensive weight progress for a cycle, returning [WeightProgressResult]
     * with delta, percentToTarget, and user-facing display strings.
     *
     * Invariants:
     * - Returns null percentToTarget / displayText = "n/a" when targetWeightKg >= startingWeightKg
     *   or when either starting/target/current weight is null or non-positive.
     */
    fun evaluateProgress(
        startingWeightKg: Double?,
        targetWeightKg: Double?,
        currentWeightKg: Double? = null
    ): WeightProgressResult {
        val delta = computeWeightDelta(startingWeightKg, currentWeightKg)
        val pct = computePercentToTarget(startingWeightKg, targetWeightKg, currentWeightKg)
        return WeightProgressResult(
            startingWeightKg = startingWeightKg,
            targetWeightKg = targetWeightKg,
            currentWeightKg = currentWeightKg,
            deltaKg = delta,
            percentToTarget = pct
        )
    }

    /**
     * Evaluates weight progress for a given [Cycle] and current weigh-in.
     */
    fun evaluateProgress(
        cycle: Cycle,
        currentWeightKg: Double?
    ): WeightProgressResult = evaluateProgress(
        startingWeightKg = cycle.startingWeightKg,
        targetWeightKg = cycle.targetWeightKg,
        currentWeightKg = currentWeightKg
    )

    fun computeWeightProgress(
        startingWeightKg: Double?,
        targetWeightKg: Double?,
        currentWeightKg: Double? = null
    ): WeightProgressResult = evaluateProgress(startingWeightKg, targetWeightKg, currentWeightKg)

    fun computeWeightProgress(
        cycle: Cycle,
        currentWeightKg: Double?
    ): WeightProgressResult = evaluateProgress(cycle, currentWeightKg)

    /**
     * Filters all non-deleted weight entries up to [referenceDate] (including pre-cycle history),
     * sorted chronologically by date.
     */
    internal fun getAllHistoricalWeightEntries(
        entries: List<WeightEntry>,
        referenceDate: LocalDate = LocalDate.now()
    ): List<WeightEntry> = entries
        .filter { it.deletedAtMillis == null && !it.date.isAfter(referenceDate) }
        .sortedBy { it.date }

    /**
     * Computes the arithmetic mean of weightKg across entries within the closed calendar interval [start, end],
     * or null if no entries exist in that interval.
     */
    internal fun computeWindowAverageKg(
        sortedEntries: List<WeightEntry>,
        start: LocalDate,
        end: LocalDate
    ): Double? {
        val inWindow = sortedEntries.filter { !it.date.isBefore(start) && !it.date.isAfter(end) }
        return if (inWindow.isNotEmpty()) inWindow.map { it.weightKg }.average() else null
    }

    /**
     * Computes the 7-day Simple Moving Average (SMA) of body weight over the closed
     * calendar interval [referenceDate - 6 days, referenceDate] once at least one in-cycle entry exists.
     *
     * Invariants & Guarantees:
     * 1. In-cycle gate: returns null if no non-deleted entries exist in [cycleStartDate, referenceDate].
     * 2. Filtering: soft-deleted entries (deletedAtMillis != null) and future entries (date > referenceDate)
     *    are strictly excluded; non-deleted pre-cycle entries falling within [referenceDate - 6 days, referenceDate]
     *    are included for rolling continuity.
     * 3. Uniqueness: WeightEntry.date is the natural @PrimaryKey; per-entry and per-day averages are mathematically identical.
     * 4. Window aggregation: entries whose date is in [referenceDate - 6 days, referenceDate].
     *    If N >= 1, returns the arithmetic mean (sum / count).
     * 5. Domain safety fallback: if no entries exist in [referenceDate - 6, referenceDate] (e.g. logging gap > 7 days),
     *    falls back to inCycleEntries.last().weightKg.
     */
    fun computeCurrent7DayAverageWeight(
        entries: List<WeightEntry>,
        cycleStartDate: LocalDate,
        referenceDate: LocalDate = LocalDate.now()
    ): Double? {
        val inCycleEntries = getActiveWeightEntries(entries, cycleStartDate, referenceDate)
        if (inCycleEntries.isEmpty()) return null

        val allActiveEntries = getAllHistoricalWeightEntries(entries, referenceDate)
        val windowStart = referenceDate.minusDays(6)
        return computeWindowAverageKg(allActiveEntries, windowStart, referenceDate)
            ?: inCycleEntries.last().weightKg
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

    // =========================================================================
    // 7. Rolling Velocity & Pacing Engine
    // =========================================================================

    /**
     * Evaluates whether a cycle is a Fat Loss / Bodybuilding cycle eligible for pacing.
     */
    fun isFatLossCycle(cycle: Cycle): Boolean =
        cycle.type == CycleType.FAT_LOSS_BODYBUILDING

    /**
     * Classifies a signed weekly rate of change into [PaceTrend].
     *
     * - Rate < -0.05: [PaceTrend.LOSS] (tertiary / downward arrow ▼)
     * - Rate > +0.05: [PaceTrend.GAIN] (error / upward arrow ▲)
     * - In between: [PaceTrend.NEUTRAL] (neutral)
     */
    fun getPaceTrend(rateKgPerWeek: Double): PaceTrend = when {
        rateKgPerWeek < -0.05 -> PaceTrend.LOSS
        rateKgPerWeek > 0.05 -> PaceTrend.GAIN
        else -> PaceTrend.NEUTRAL
    }

    /**
     * Computes signed weekly rate: (W_current - W_anchor) / elapsedDays * 7.
     * Loss is negative (e.g. -0.5 kg/wk).
     */
    fun computeSignedWeeklyRate(
        currentWeightKg: Double,
        anchorWeightKg: Double,
        elapsedDays: Long
    ): Double? {
        if (elapsedDays <= 0) return null
        return ((currentWeightKg - anchorWeightKg) / elapsedDays.toDouble()) * 7.0
    }

    /**
     * Computes positive deficit velocity: (W_anchor - W_current) / elapsedDays * 7.
     * Loss is positive (e.g. 0.5 kg/wk).
     */
    fun computeDeficitVelocity(
        currentWeightKg: Double,
        anchorWeightKg: Double,
        elapsedDays: Long
    ): Double? {
        if (elapsedDays <= 0) return null
        return ((anchorWeightKg - currentWeightKg) / elapsedDays.toDouble()) * 7.0
    }

    /**
     * Formats a weekly pace rate for display in the given unit (kg or lbs).
     * Returns "Not enough data yet" when rate is null.
     */
    fun formatPaceRate(rateKgPerWeek: Double?, unit: String = "kg"): String {
        if (rateKgPerWeek == null) return "Not enough data yet"
        val rateInUnit = if (unit.equals("lbs", ignoreCase = true)) {
            WeightAnalytics.kgToLbs(rateKgPerWeek)
        } else {
            rateKgPerWeek
        }
        val unitLabel = if (unit.equals("lbs", ignoreCase = true)) "lbs/wk" else "kg/wk"
        return when {
            rateInUnit < -0.05 -> String.format(Locale.US, "%.1f %s", rateInUnit, unitLabel)
            rateInUnit > 0.05 -> String.format(Locale.US, "+%.1f %s", rateInUnit, unitLabel)
            else -> String.format(Locale.US, "0.0 %s", unitLabel)
        }
    }

    /**
     * Formats a [PaceRateResult] for UI chip display.
     */
    fun formatPaceChip(rateResult: PaceRateResult?, unit: String = "kg"): String {
        return rateResult?.formatDisplay(unit) ?: "Not enough data yet"
    }

    /**
     * Resolves the closest anchor entry on or before [targetCutoffDate] that is on or after [cycleStartDate].
     * Excludes soft-deleted records.
     */
    fun resolveAnchorEntry(
        entries: List<WeightEntry>,
        targetCutoffDate: LocalDate,
        cycleStartDate: LocalDate
    ): WeightEntry? {
        return entries
            .filter { it.deletedAtMillis == null && !it.date.isBefore(cycleStartDate) && !it.date.isAfter(targetCutoffDate) }
            .maxByOrNull { it.date }
    }

    /**
     * Overload of [resolveAnchorEntry] computing cutoff as [referenceDate] minus [windowDays].
     */
    fun resolveAnchorEntry(
        entries: List<WeightEntry>,
        referenceDate: LocalDate,
        windowDays: Int,
        cycleStartDate: LocalDate
    ): WeightEntry? = resolveAnchorEntry(
        entries = entries,
        targetCutoffDate = referenceDate.minusDays(windowDays.toLong()),
        cycleStartDate = cycleStartDate
    )

    /**
     * Computes the signed pace rate for a given rolling window (e.g. 7 or 14 days) using
     * 7-day rolling window averages (including pre-cycle lookback history when available).
     *
     * Invariants:
     * - In-cycle gate: requires >= 1 non-deleted entry in [cycleStartDate, today].
     * - Primary window math: compares current 7D average over [t - 6d, t] against prior 7D average
     *   over [t - windowDays - 6d, t - windowDays], normalized over [windowDays].
     * - Sparse / cold-start fallback: if no entries exist in [t - windowDays - 6d, t - windowDays],
     *   falls back to [startingWeightKg] at [cycleStartDate] when cycleStartDate <= t - windowDays,
     *   else to the most recent in-cycle entry on or before t - windowDays. Pre-cycle entries outside
     *   the prior window are never used as anchors, so stale history cannot drive the rate.
     * - Minimum span: every anchor is dated on or before t - windowDays, so a non-null result always
     *   spans >= [windowDays] days; a single in-cycle entry plus [startingWeightKg] is sufficient.
     */
    fun computePaceRate(
        entries: List<WeightEntry>,
        windowDays: Int,
        cycleStartDate: LocalDate,
        today: LocalDate = LocalDate.now(),
        startingWeightKg: Double? = null
    ): PaceRateResult? {
        if (windowDays <= 0) return null

        val inCycleEntries = getActiveWeightEntries(entries, cycleStartDate, today)
        if (inCycleEntries.isEmpty()) return null
        val newest = inCycleEntries.last()
        val t = newest.date

        val allActiveEntries = getAllHistoricalWeightEntries(entries, t)
        val hasValidStartBaseline = startingWeightKg != null && startingWeightKg > 0.0

        val currentAvgKg = computeWindowAverageKg(allActiveEntries, t.minusDays(6), t)
            ?: newest.weightKg

        val cutoffDate = t.minusDays(windowDays.toLong())
        val priorWindowStart = cutoffDate.minusDays(6)
        val priorWindowEntries = allActiveEntries.filter {
            !it.date.isBefore(priorWindowStart) && !it.date.isAfter(cutoffDate)
        }

        val anchorWeightKg: Double
        val anchorDate: LocalDate
        val elapsedDays: Long

        if (priorWindowEntries.isNotEmpty()) {
            anchorWeightKg = priorWindowEntries.map { it.weightKg }.average()
            anchorDate = priorWindowEntries.last().date
            elapsedDays = windowDays.toLong()
        } else if (hasValidStartBaseline && !cycleStartDate.isAfter(cutoffDate)) {
            anchorWeightKg = startingWeightKg!!
            anchorDate = cycleStartDate
            elapsedDays = ChronoUnit.DAYS.between(cycleStartDate, t)
        } else {
            val fallbackEntry = resolveAnchorEntry(inCycleEntries, cutoffDate, cycleStartDate) ?: return null
            anchorWeightKg = fallbackEntry.weightKg
            anchorDate = fallbackEntry.date
            elapsedDays = ChronoUnit.DAYS.between(fallbackEntry.date, t)
        }

        if (elapsedDays <= 0) return null

        val rate = ((currentAvgKg - anchorWeightKg) / elapsedDays.toDouble()) * 7.0
        val trend = getPaceTrend(rate)

        return PaceRateResult(
            rateKgPerWeek = rate,
            anchorWeightKg = anchorWeightKg,
            anchorDate = anchorDate,
            currentWeightKg = currentAvgKg,
            currentDate = t,
            elapsedDays = elapsedDays,
            trend = trend
        )
    }

    /**
     * Computes 7-day rolling pace rate using 7-day window averages.
     */
    fun compute7DayPace(
        entries: List<WeightEntry>,
        cycleStartDate: LocalDate,
        today: LocalDate = LocalDate.now(),
        startingWeightKg: Double? = null
    ): PaceRateResult? = computePaceRate(
        entries = entries,
        windowDays = 7,
        cycleStartDate = cycleStartDate,
        today = today,
        startingWeightKg = startingWeightKg
    )

    fun compute7DayPace(
        entries: List<WeightEntry>,
        cycle: Cycle,
        today: LocalDate = LocalDate.now()
    ): PaceRateResult? = compute7DayPace(
        entries = entries,
        cycleStartDate = cycle.startDate,
        today = today,
        startingWeightKg = cycle.startingWeightKg
    )

    internal fun round1(value: Double): Double =
        BigDecimal.valueOf(value).setScale(1, RoundingMode.HALF_UP).toDouble()

    internal fun round2(value: Double): Double =
        BigDecimal.valueOf(value).setScale(2, RoundingMode.HALF_UP).toDouble()

    /** Converts [weightKg] (or a kg/wk rate) into [weightUnit] ("kg" or "lbs"), unrounded. */
    internal fun toUnit(weightKg: Double, weightUnit: String): Double =
        if (weightUnit.equals("lbs", ignoreCase = true)) WeightAnalytics.kgToLbs(weightKg) else weightKg

    /** [weightKg] converted into [weightUnit] and rounded to the displayed 1-decimal precision. */
    internal fun round1InUnit(weightKg: Double, weightUnit: String): Double = round1(toUnit(weightKg, weightUnit))

    /** [weightKg] converted into [weightUnit] and rounded to the displayed 2-decimal precision. */
    internal fun round2InUnit(weightKg: Double, weightUnit: String): Double = round2(toUnit(weightKg, weightUnit))

    internal fun diff1(a: Double, b: Double): Double =
        BigDecimal.valueOf(a).setScale(1, RoundingMode.HALF_UP)
            .subtract(BigDecimal.valueOf(b).setScale(1, RoundingMode.HALF_UP))
            .toDouble()

    internal fun diff2(a: Double, b: Double): Double =
        BigDecimal.valueOf(a).setScale(2, RoundingMode.HALF_UP)
            .subtract(BigDecimal.valueOf(b).setScale(2, RoundingMode.HALF_UP))
            .toDouble()

    /** Maps a displayed actual-minus-target delta onto its pace status (0 = on pace, negative = ahead). */
    private fun paceStatusFor(delta: Double): PaceTargetStatus = when {
        delta == 0.0 -> PaceTargetStatus.ON_PACE
        delta < 0.0 -> PaceTargetStatus.AHEAD
        else -> PaceTargetStatus.OFF_PACE
    }

    /**
     * Computes the Required-Pace & Linear Cycle Weight Targets for the 7D, 14D, 21D, and 28D
     * Rolling Average rows (with a 5-argument fallback for legacy monotonic tests when neither
     * [targetWeightKg] nor [cycleEndDate] is provided).
     *
     * When [targetWeightKg] and [cycleEndDate] are evaluated (via [Cycle] or explicit parameters):
     * - Container Precedence: [PaceTargetsState.Unavailable] -> [PaceTargetsState.GoalReached] ->
     *   [PaceTargetsState.CycleEnded] -> [PaceTargetsState.Available].
     * - For each rolling window N in {7, 14, 21, 28}:
     *   - Linear Today: straight-line cycle interpolation compared against the current ND rolling average.
     *   - Expected Today: the exact daily scale weigh-in needed today so that today's ND rolling average
     *     equals the required-pace ND target average (compared against today's actual scale weigh-in when logged).
     *   - Target Tomorrow: the exact daily scale weigh-in needed tomorrow so that tomorrow's ND rolling average
     *     equals tomorrow's required-pace ND target average.
     * - All target weights, deltas, and daily steps inside the container use 2-decimal precision (`round2`, `diff2`).
     */
    fun compute7DayPaceTargets(
        entries: List<WeightEntry>,
        cycleStartDate: LocalDate,
        referenceDate: LocalDate = LocalDate.now(),
        weightUnit: String = "kg",
        startingWeightKg: Double? = null,
        targetWeightKg: Double? = null,
        cycleEndDate: LocalDate? = null,
        requireCycleTargetAndEnd: Boolean = false
    ): PaceTargetsState {
        if (targetWeightKg == null && cycleEndDate == null && !requireCycleTargetAndEnd) {
            return computeLegacyMonotonic7DayPaceTargets(
                entries = entries,
                cycleStartDate = cycleStartDate,
                referenceDate = referenceDate,
                weightUnit = weightUnit,
                startingWeightKg = startingWeightKg
            )
        }

        val activeEntries = getActiveWeightEntries(entries, cycleStartDate, referenceDate)
        if (activeEntries.isEmpty() || referenceDate.isBefore(cycleStartDate)) {
            return PaceTargetsState.Unavailable
        }

        val startKg = startingWeightKg ?: activeEntries.firstOrNull()?.weightKg ?: return PaceTargetsState.Unavailable
        if (targetWeightKg == null || targetWeightKg <= 0.0 || cycleEndDate == null) {
            return PaceTargetsState.Unavailable
        }
        if (targetWeightKg >= startKg) {
            return PaceTargetsState.Unavailable
        }

        val totalCycleDays = ChronoUnit.DAYS.between(cycleStartDate, cycleEndDate)
        if (totalCycleDays <= 0L) {
            return PaceTargetsState.Unavailable
        }

        val allActiveEntries = getAllHistoricalWeightEntries(entries, referenceDate)
        val fallbackWeightKg = activeEntries.last().weightKg
        val today7dAvgKg = computeWindowAverageKg(allActiveEntries, referenceDate.minusDays(6), referenceDate)
            ?: fallbackWeightKg

        // 2. GoalReached (1-decimal display reconciliation matching Top Track & ForecastBanner)
        if (round1InUnit(today7dAvgKg, weightUnit) <= round1InUnit(targetWeightKg, weightUnit)) {
            return PaceTargetsState.GoalReached
        }

        // 3. CycleEnded
        val daysRemaining = ChronoUnit.DAYS.between(referenceDate, cycleEndDate)
        if (daysRemaining <= 0L) {
            return PaceTargetsState.CycleEnded
        }

        val isImperial = weightUnit.equals("lbs", ignoreCase = true)
        val unitLabel = if (isImperial) "lbs" else "kg"

        val startInUnit = toUnit(startKg, weightUnit)
        val targetInUnit = toUnit(targetWeightKg, weightUnit)

        // Linear Today interpolation across [cycleStartDate, cycleEndDate]
        val elapsedDays = ChronoUnit.DAYS.between(cycleStartDate, referenceDate).coerceIn(0L, totalCycleDays)
        val wLinearInUnit = startInUnit + (targetInUnit - startInUnit) * (elapsedDays.toDouble() / totalCycleDays.toDouble())
        val wLinearDisplay = round2(wLinearInUnit)

        val entryToday = activeEntries.firstOrNull { it.date == referenceDate }

        val sevenDayAvgRow = computeRollingWindowTargetRow(
            windowDays = 7,
            allActiveEntries = allActiveEntries,
            fallbackWeightKg = fallbackWeightKg,
            entryToday = entryToday,
            cycleStartDate = cycleStartDate,
            referenceDate = referenceDate,
            daysRemaining = daysRemaining,
            targetInUnit = targetInUnit,
            wLinearDisplay = wLinearDisplay,
            weightUnit = weightUnit,
            unitLabel = unitLabel
        )
        val fourteenDayAvgRow = computeRollingWindowTargetRow(
            windowDays = 14,
            allActiveEntries = allActiveEntries,
            fallbackWeightKg = fallbackWeightKg,
            entryToday = entryToday,
            cycleStartDate = cycleStartDate,
            referenceDate = referenceDate,
            daysRemaining = daysRemaining,
            targetInUnit = targetInUnit,
            wLinearDisplay = wLinearDisplay,
            weightUnit = weightUnit,
            unitLabel = unitLabel
        )
        val twentyOneDayAvgRow = computeRollingWindowTargetRow(
            windowDays = 21,
            allActiveEntries = allActiveEntries,
            fallbackWeightKg = fallbackWeightKg,
            entryToday = entryToday,
            cycleStartDate = cycleStartDate,
            referenceDate = referenceDate,
            daysRemaining = daysRemaining,
            targetInUnit = targetInUnit,
            wLinearDisplay = wLinearDisplay,
            weightUnit = weightUnit,
            unitLabel = unitLabel
        )
        val twentyEightDayAvgRow = computeRollingWindowTargetRow(
            windowDays = 28,
            allActiveEntries = allActiveEntries,
            fallbackWeightKg = fallbackWeightKg,
            entryToday = entryToday,
            cycleStartDate = cycleStartDate,
            referenceDate = referenceDate,
            daysRemaining = daysRemaining,
            targetInUnit = targetInUnit,
            wLinearDisplay = wLinearDisplay,
            weightUnit = weightUnit,
            unitLabel = unitLabel
        )

        return PaceTargetsState.Available(
            today = sevenDayAvgRow.expectedToday,
            tomorrow = sevenDayAvgRow.targetTomorrow,
            sevenDayAvgRow = sevenDayAvgRow,
            fourteenDayAvgRow = fourteenDayAvgRow,
            twentyOneDayAvgRow = twentyOneDayAvgRow,
            twentyEightDayAvgRow = twentyEightDayAvgRow
        )
    }

    private fun computeRollingWindowTargetRow(
        windowDays: Int,
        allActiveEntries: List<WeightEntry>,
        fallbackWeightKg: Double,
        entryToday: WeightEntry?,
        cycleStartDate: LocalDate,
        referenceDate: LocalDate,
        daysRemaining: Long,
        targetInUnit: Double,
        wLinearDisplay: Double,
        weightUnit: String,
        unitLabel: String
    ): TargetRowState {
        val currentWindowStart = referenceDate.minusDays(windowDays - 1L)
        val todayNdAvgKg = computeWindowAverageKg(allActiveEntries, currentWindowStart, referenceDate)
            ?: fallbackWeightKg
        val currentNdInUnit = toUnit(todayNdAvgKg, weightUnit)
        val currentNdDisplay = round2(currentNdInUnit)

        // Exact unrounded required pace from current ND rolling average to targetWeight by cycleEndDate
        val remainingInUnit = currentNdInUnit - targetInUnit
        val dailyRequiredRateInUnit = -remainingInUnit / daysRemaining.toDouble()
        val dailyStepDisplay = -kotlin.math.abs(round2(dailyRequiredRateInUnit))

        // Column 1: Linear Today (current ND rolling average vs linear interpolation)
        val deltaNdLinear = diff2(currentNdDisplay, wLinearDisplay)
        val linearToday = ColumnState.Value(
            targetWeightDisplay = wLinearDisplay,
            deltaDisplay = deltaNdLinear,
            status = paceStatusFor(deltaNdLinear),
            unitLabel = unitLabel,
            useTwoDecimals = true
        )

        // Column 2: Expected Today (daily scale weight needed today so today's ND average steps 1 day from yesterday's ND average)
        val anchorDate = referenceDate.minusDays(windowDays.toLong())
        val priorWindowStart = referenceDate.minusDays(2L * windowDays - 1L)
        val priorNdAvgKg = computeWindowAverageKg(allActiveEntries, priorWindowStart, anchorDate)

        val expectedToday: ColumnState = when {
            anchorDate.isBefore(cycleStartDate) && priorNdAvgKg == null ->
                ColumnState.InsufficientHistory
            priorNdAvgKg == null ->
                ColumnState.NeedsLog(requiredDate = anchorDate)
            else -> {
                val yesterdayNdAvgKg = computeWindowAverageKg(
                    allActiveEntries,
                    anchorDate,
                    referenceDate.minusDays(1L)
                ) ?: priorNdAvgKg
                val yesterdayNdInUnit = toUnit(yesterdayNdAvgKg, weightUnit)
                val targetAvgTodayInUnit = yesterdayNdInUnit + dailyRequiredRateInUnit
                val priorTodayEntries = allActiveEntries.filter {
                    !it.date.isBefore(currentWindowStart) && !it.date.isAfter(referenceDate.minusDays(1L))
                }
                val kToday = priorTodayEntries.size
                val sumPriorTodayInUnit = priorTodayEntries.sumOf { toUnit(it.weightKg, weightUnit) }
                val expectedScaleTodayDisplay = round2((kToday + 1) * targetAvgTodayInUnit - sumPriorTodayInUnit)
                val baselineNdDisplay = round2(yesterdayNdInUnit)

                if (entryToday == null) {
                    ColumnState.Value(
                        targetWeightDisplay = expectedScaleTodayDisplay,
                        deltaDisplay = null,
                        status = null,
                        unitLabel = unitLabel,
                        baselineWeightDisplay = baselineNdDisplay,
                        useTwoDecimals = true
                    )
                } else {
                    val todayDailyDisplay = round2(toUnit(entryToday.weightKg, weightUnit))
                    val deltaExpected = diff2(todayDailyDisplay, expectedScaleTodayDisplay)
                    ColumnState.Value(
                        targetWeightDisplay = expectedScaleTodayDisplay,
                        deltaDisplay = deltaExpected,
                        status = paceStatusFor(deltaExpected),
                        unitLabel = unitLabel,
                        baselineWeightDisplay = baselineNdDisplay,
                        useTwoDecimals = true
                    )
                }
            }
        }

        // Column 3: Target Tomorrow (daily scale weight needed tomorrow so tomorrow's ND average = required-pace ND target)
        val targetAvgTomorrowInUnit = currentNdInUnit + dailyRequiredRateInUnit
        val tomorrowPriorWindowStart = referenceDate.minusDays(windowDays - 2L)
        val priorTomorrowEntries = allActiveEntries.filter {
            !it.date.isBefore(tomorrowPriorWindowStart) && !it.date.isAfter(referenceDate)
        }
        val kTomorrow = priorTomorrowEntries.size
        val sumPriorTomorrowInUnit = priorTomorrowEntries.sumOf { toUnit(it.weightKg, weightUnit) }
        val targetScaleTomorrowDisplay = round2((kTomorrow + 1) * targetAvgTomorrowInUnit - sumPriorTomorrowInUnit)

        val targetTomorrow = ColumnState.Value(
            targetWeightDisplay = targetScaleTomorrowDisplay,
            deltaDisplay = dailyStepDisplay,
            status = null,
            unitLabel = unitLabel,
            baselineWeightDisplay = currentNdDisplay,
            isDailyStep = true,
            useTwoDecimals = true
        )

        return TargetRowState(
            windowDays = windowDays,
            linearToday = linearToday,
            expectedToday = expectedToday,
            targetTomorrow = targetTomorrow
        )
    }

    private fun computeLegacyMonotonic7DayPaceTargets(
        entries: List<WeightEntry>,
        cycleStartDate: LocalDate,
        referenceDate: LocalDate = LocalDate.now(),
        weightUnit: String = "kg",
        startingWeightKg: Double? = null
    ): PaceTargetsState {
        val activeEntries = entries
            .filter { it.deletedAtMillis == null && !it.date.isBefore(cycleStartDate) && !it.date.isAfter(referenceDate) }
            .sortedBy { it.date }

        val startKg = startingWeightKg ?: activeEntries.firstOrNull()?.weightKg
        if (startKg == null) {
            return PaceTargetsState.Unavailable
        }

        val entryTMinus7 = activeEntries.firstOrNull { it.date == referenceDate.minusDays(7) }
        val entryTMinus6 = activeEntries.firstOrNull { it.date == referenceDate.minusDays(6) }

        val isStalled = when {
            entryTMinus7 != null -> (entryTMinus7.weightKg - startKg) >= 0.0
            entryTMinus6 != null -> (entryTMinus6.weightKg - startKg) >= 0.0
            else -> false
        }
        if (isStalled) {
            return PaceTargetsState.Stalled
        }

        val isImperial = weightUnit.equals("lbs", ignoreCase = true)
        val unitLabel = if (isImperial) "lbs" else "kg"
        val entryToday = activeEntries.firstOrNull { it.date == referenceDate }
        val dCycle = java.time.temporal.ChronoUnit.DAYS.between(cycleStartDate, referenceDate).toDouble().coerceAtLeast(1.0)

        // 1. Column: Expected Today
        val todayColumn: ColumnState = when {
            referenceDate.minusDays(7).isBefore(cycleStartDate) -> ColumnState.InsufficientHistory
            entryTMinus7 == null -> ColumnState.NeedsLog(requiredDate = referenceDate.minusDays(7))
            else -> {
                val wTheoTodayKg = entryTMinus7.weightKg + ((entryTMinus7.weightKg - startKg) / dCycle)
                val targetDisplay = if (isImperial) round1(WeightAnalytics.kgToLbs(wTheoTodayKg)) else round1(wTheoTodayKg)
                val baselineTodayDisplay = if (isImperial) round1(WeightAnalytics.kgToLbs(entryTMinus7.weightKg)) else round1(entryTMinus7.weightKg)

                if (entryToday == null) {
                    ColumnState.Value(
                        targetWeightDisplay = targetDisplay,
                        deltaDisplay = null,
                        status = null,
                        unitLabel = unitLabel,
                        baselineWeightDisplay = baselineTodayDisplay
                    )
                } else {
                    val deltaKg = diff1(round1(entryToday.weightKg), round1(wTheoTodayKg))
                    val deltaDisp = if (isImperial) round1(WeightAnalytics.kgToLbs(deltaKg)) else deltaKg
                    ColumnState.Value(
                        targetWeightDisplay = targetDisplay,
                        deltaDisplay = deltaDisp,
                        status = paceStatusFor(deltaDisp),
                        unitLabel = unitLabel,
                        baselineWeightDisplay = baselineTodayDisplay
                    )
                }
            }
        }

        // 2. Column: Target Tomorrow
        val tomorrowColumn: ColumnState = when {
            referenceDate.minusDays(6).isBefore(cycleStartDate) -> ColumnState.InsufficientHistory
            entryTMinus6 == null -> ColumnState.NeedsLog(requiredDate = referenceDate.minusDays(6))
            else -> {
                val wTheoTomorrowKg = entryTMinus6.weightKg + ((entryTMinus6.weightKg - startKg) / dCycle)
                val targetTomorrowDisplay = if (isImperial) round1(WeightAnalytics.kgToLbs(wTheoTomorrowKg)) else round1(wTheoTomorrowKg)
                val baselineTomorrowDisplay = if (isImperial) round1(WeightAnalytics.kgToLbs(entryTMinus6.weightKg)) else round1(entryTMinus6.weightKg)

                if (entryToday == null) {
                    ColumnState.Value(
                        targetWeightDisplay = targetTomorrowDisplay,
                        deltaDisplay = null,
                        status = null,
                        unitLabel = unitLabel,
                        baselineWeightDisplay = baselineTomorrowDisplay
                    )
                } else {
                    val actualTodayDisplay = if (isImperial) round1(WeightAnalytics.kgToLbs(entryToday.weightKg)) else round1(entryToday.weightKg)
                    when {
                        actualTodayDisplay > targetTomorrowDisplay -> {
                            val deltaKg = diff1(round1(wTheoTomorrowKg), round1(entryToday.weightKg))
                            val deltaDisp = if (isImperial) round1(WeightAnalytics.kgToLbs(deltaKg)) else deltaKg
                            ColumnState.Value(
                                targetWeightDisplay = targetTomorrowDisplay,
                                deltaDisplay = deltaDisp,
                                status = null,
                                unitLabel = unitLabel,
                                baselineWeightDisplay = baselineTomorrowDisplay
                            )
                        }
                        actualTodayDisplay == targetTomorrowDisplay -> {
                            ColumnState.Value(
                                targetWeightDisplay = targetTomorrowDisplay,
                                deltaDisplay = null,
                                status = null,
                                unitLabel = unitLabel,
                                baselineWeightDisplay = baselineTomorrowDisplay
                            )
                        }
                        else -> {
                            val marginKg = diff1(round1(wTheoTomorrowKg), round1(entryToday.weightKg))
                            val marginDisp = if (isImperial) round1(WeightAnalytics.kgToLbs(marginKg)) else marginKg
                            ColumnState.AlreadyBelowTarget(
                                targetWeightDisplay = targetTomorrowDisplay,
                                marginDisplay = marginDisp,
                                unitLabel = unitLabel,
                                baselineWeightDisplay = baselineTomorrowDisplay
                            )
                        }
                    }
                }
            }
        }

        return PaceTargetsState.Available(
            today = todayColumn,
            tomorrow = tomorrowColumn
        )
    }

    fun compute7DayPaceTargets(
        entries: List<WeightEntry>,
        cycle: Cycle,
        referenceDate: LocalDate = LocalDate.now(),
        weightUnit: String = "kg"
    ): PaceTargetsState = compute7DayPaceTargets(
        entries = entries,
        cycleStartDate = cycle.startDate,
        referenceDate = referenceDate,
        weightUnit = weightUnit,
        startingWeightKg = cycle.startingWeightKg,
        targetWeightKg = cycle.targetWeightKg ?: cycle.targetBodyWeightKg,
        cycleEndDate = cycle.endDate,
        requireCycleTargetAndEnd = true
    )

    /**
     * Computes the 7-day and 14-day rolling window averages and weekly deltas for the hero card.
     *
     * Windows:
     * - Today 7D: [referenceDate - 6d, referenceDate]
     * - Last Week 7D: [referenceDate - 13d, referenceDate - 7d]
     * - Today 14D: [referenceDate - 13d, referenceDate]
     * - Last Week 14D: [referenceDate - 20d, referenceDate - 7d]
     *
     * Requires at least 1 non-deleted in-cycle entry in [cycleStartDate, referenceDate].
     * Once at least 1 in-cycle entry exists, historical pre-cycle entries are included in lookback windows.
     */
    fun computeRollingWeightHero(
        entries: List<WeightEntry>,
        cycleStartDate: LocalDate,
        referenceDate: LocalDate = LocalDate.now()
    ): RollingWeightHeroState {
        val inCycleEntries = getActiveWeightEntries(entries, cycleStartDate, referenceDate)
        if (inCycleEntries.isEmpty()) {
            return RollingWeightHeroState(
                today7dAvgKg = null,
                lastWeek7dAvgKg = null,
                delta7dKg = null,
                today14dAvgKg = null,
                delta14dKg = null,
                loggedDaysCount14d = 0
            )
        }

        val allActiveEntries = getAllHistoricalWeightEntries(entries, referenceDate)

        val today7dAvg = computeWindowAverageKg(allActiveEntries, referenceDate.minusDays(6), referenceDate)
        val lastWeek7dAvg = computeWindowAverageKg(allActiveEntries, referenceDate.minusDays(13), referenceDate.minusDays(7))
        val delta7d = if (today7dAvg != null && lastWeek7dAvg != null) {
            diff1(round1(today7dAvg), round1(lastWeek7dAvg))
        } else {
            null
        }

        val today14dAvg = computeWindowAverageKg(allActiveEntries, referenceDate.minusDays(13), referenceDate)
        val lastWeek14dAvg = computeWindowAverageKg(allActiveEntries, referenceDate.minusDays(20), referenceDate.minusDays(7))
        val delta14d = if (today14dAvg != null && lastWeek14dAvg != null) {
            diff1(round1(today14dAvg), round1(lastWeek14dAvg))
        } else {
            null
        }

        val loggedDaysCount14d = allActiveEntries.count {
            !it.date.isBefore(referenceDate.minusDays(13)) && !it.date.isAfter(referenceDate)
        }

        return RollingWeightHeroState(
            today7dAvgKg = today7dAvg,
            lastWeek7dAvgKg = lastWeek7dAvg,
            delta7dKg = delta7d,
            today14dAvgKg = today14dAvg,
            delta14dKg = delta14d,
            loggedDaysCount14d = loggedDaysCount14d
        )
    }

    fun computeRollingWeightHero(
        entries: List<WeightEntry>,
        cycle: Cycle,
        referenceDate: LocalDate = LocalDate.now()
    ): RollingWeightHeroState = computeRollingWeightHero(
        entries = entries,
        cycleStartDate = cycle.startDate,
        referenceDate = referenceDate
    )

    /**
     * Computes 14-day rolling pace rate using 7-day window averages.
     */
    fun compute14DayPace(
        entries: List<WeightEntry>,
        cycleStartDate: LocalDate,
        today: LocalDate = LocalDate.now(),
        startingWeightKg: Double? = null
    ): PaceRateResult? = computePaceRate(
        entries = entries,
        windowDays = 14,
        cycleStartDate = cycleStartDate,
        today = today,
        startingWeightKg = startingWeightKg
    )

    fun compute14DayPace(
        entries: List<WeightEntry>,
        cycle: Cycle,
        today: LocalDate = LocalDate.now()
    ): PaceRateResult? = compute14DayPace(
        entries = entries,
        cycleStartDate = cycle.startDate,
        today = today,
        startingWeightKg = cycle.startingWeightKg
    )

    /**
     * Computes 21-day rolling pace rate using 7-day window averages.
     */
    fun compute21DayPace(
        entries: List<WeightEntry>,
        cycleStartDate: LocalDate,
        today: LocalDate = LocalDate.now(),
        startingWeightKg: Double? = null
    ): PaceRateResult? = computePaceRate(
        entries = entries,
        windowDays = 21,
        cycleStartDate = cycleStartDate,
        today = today,
        startingWeightKg = startingWeightKg
    )

    fun compute21DayPace(
        entries: List<WeightEntry>,
        cycle: Cycle,
        today: LocalDate = LocalDate.now()
    ): PaceRateResult? = compute21DayPace(
        entries = entries,
        cycleStartDate = cycle.startDate,
        today = today,
        startingWeightKg = cycle.startingWeightKg
    )

    /**
     * Computes 28-day rolling pace rate using 7-day window averages.
     */
    fun compute28DayPace(
        entries: List<WeightEntry>,
        cycleStartDate: LocalDate,
        today: LocalDate = LocalDate.now(),
        startingWeightKg: Double? = null
    ): PaceRateResult? = computePaceRate(
        entries = entries,
        windowDays = 28,
        cycleStartDate = cycleStartDate,
        today = today,
        startingWeightKg = startingWeightKg
    )

    fun compute28DayPace(
        entries: List<WeightEntry>,
        cycle: Cycle,
        today: LocalDate = LocalDate.now()
    ): PaceRateResult? = compute28DayPace(
        entries = entries,
        cycleStartDate = cycle.startDate,
        today = today,
        startingWeightKg = cycle.startingWeightKg
    )

    val HORIZON_WINDOWS: List<Int> = listOf(7, 14, 21, 28)

    /**
     * Computes the visual Multi-Horizon Goal Estimations state across 7D, 14D, 21D, and 28D pace windows
     * using 2-decimal pace velocity (`v2`), estimated weeks, projected arrival dates, proportional
     * pace-to-required sufficiency bars, and ahead/late week badges.
     */
    fun computeMultiHorizonForecast(
        entries: List<WeightEntry>,
        cycleStartDate: LocalDate,
        cycleEndDate: LocalDate? = null,
        targetWeightKg: Double?,
        startingWeightKg: Double? = null,
        today: LocalDate = LocalDate.now(),
        weightUnit: String = "kg"
    ): MultiHorizonForecastState? {
        val activeEntries = getActiveWeightEntries(entries, cycleStartDate, today)
        val isCycleEnded = cycleEndDate != null && today.isAfter(cycleEndDate)

        if (activeEntries.isEmpty()) {
            return if (isCycleEnded) MultiHorizonForecastState.CycleEnded else MultiHorizonForecastState.NotEnoughData
        }

        val newest = activeEntries.last()
        val currentWeightKg = computeCurrent7DayAverageWeight(
            entries = entries,
            cycleStartDate = cycleStartDate,
            referenceDate = today
        ) ?: newest.weightKg

        if (targetWeightKg != null && targetWeightKg > 0.0 &&
            round1InUnit(currentWeightKg, weightUnit) <= round1InUnit(targetWeightKg, weightUnit)
        ) {
            return MultiHorizonForecastState.GoalReached
        }

        if (isCycleEnded) {
            return MultiHorizonForecastState.CycleEnded
        }

        if (targetWeightKg == null || targetWeightKg <= 0.0) {
            return null
        }

        val effectiveStartWeight = startingWeightKg ?: activeEntries.first().weightKg
        if (targetWeightKg >= effectiveStartWeight) {
            return null
        }

        val isImperial = weightUnit.equals("lbs", ignoreCase = true)
        val normalizedUnit = if (isImperial) "lbs" else "kg"
        val current1InUnit = round1InUnit(currentWeightKg, normalizedUnit)
        val target1InUnit = round1InUnit(targetWeightKg, normalizedUnit)
        val remainingInUnit = diff1(current1InUnit, target1InUnit)
        if (remainingInUnit <= 0.0) {
            return MultiHorizonForecastState.GoalReached
        }

        val requiredPace = computeRequiredPaceToEndDate(
            today7dAvgKg = currentWeightKg,
            targetWeightKg = targetWeightKg,
            cycleEndDate = cycleEndDate,
            today = today,
            weightUnit = normalizedUnit,
            startWeightKg = effectiveStartWeight
        )

        val pacesByWindow: Map<Int, PaceRateResult?> = HORIZON_WINDOWS.associateWith { w ->
            computePaceRate(
                entries = entries,
                windowDays = w,
                cycleStartDate = cycleStartDate,
                today = newest.date,
                startingWeightKg = startingWeightKg
            )
        }

        if (pacesByWindow.values.all { it == null }) {
            return MultiHorizonForecastState.NotEnoughData
        }

        // Benchmark velocity for sufficiency bar: required pace magnitude when endDate is set,
        // otherwise max positive velocity across the 4 horizons (fallback to 1% bodyweight/wk).
        val maxActiveV2 = pacesByWindow.values
            .mapNotNull { p ->
                if (p != null && p.trend == PaceTrend.LOSS) {
                    round2(toUnit(p.deficitVelocityKgPerWeek, normalizedUnit)).takeIf { it > 0.0 }
                } else null
            }
            .maxOrNull()
        val benchmarkV2 = when {
            requiredPace != null -> kotlin.math.abs(requiredPace.requiredRate2InUnit).coerceAtLeast(0.01)
            maxActiveV2 != null && maxActiveV2 > 0.0 -> maxActiveV2
            else -> (current1InUnit * MAX_SUSTAINABLE_WEEKLY_LOSS_FRACTION).coerceAtLeast(0.1)
        }

        val rows = HORIZON_WINDOWS.map { windowDays ->
            val pace = pacesByWindow[windowDays]
            if (pace == null) {
                HorizonEstimationRow(
                    windowDays = windowDays,
                    state = HorizonRowState.NotEnoughData(windowDays = windowDays)
                )
            } else {
                val paceDisplay2 = pace.formatDisplay2(normalizedUnit)
                val v2 = round2(toUnit(pace.deficitVelocityKgPerWeek, normalizedUnit))
                if (pace.trend != PaceTrend.LOSS || v2 <= 0.0) {
                    HorizonEstimationRow(
                        windowDays = windowDays,
                        state = HorizonRowState.Stalled(
                            windowDays = windowDays,
                            paceDisplay2 = paceDisplay2
                        )
                    )
                } else {
                    val rawWeeks = remainingInUnit / v2
                    val roundedWeeks = round1(rawWeeks)
                    val isCapped = roundedWeeks > 52.0
                    val cappedWeeks = if (isCapped) 52.0 else roundedWeeks
                    val targetDate = if (isCapped) {
                        null
                    } else {
                        val daysToGoal = kotlin.math.round(roundedWeeks * 7.0).toLong()
                        today.plusDays(daysToGoal)
                    }
                    val barFraction = (v2 / benchmarkV2).toFloat().coerceIn(0.05f, 1.0f)

                    val weeksDelta: Double?
                    val isOnTrack: Boolean
                    val deltaBadgeText: String?

                    if (requiredPace != null) {
                        val rawDelta = diff1(roundedWeeks, round1(requiredPace.weeksRemaining))
                        weeksDelta = rawDelta
                        if (isCapped) {
                            isOnTrack = false
                            deltaBadgeText = "> 1 yr late"
                        } else if (rawDelta < -0.05) {
                            isOnTrack = true
                            deltaBadgeText = String.format(Locale.US, "On time (%.1fw)", rawDelta)
                        } else if (rawDelta <= 0.05) {
                            isOnTrack = true
                            deltaBadgeText = "On time"
                        } else {
                            isOnTrack = false
                            deltaBadgeText = String.format(Locale.US, "+%.1fw late", rawDelta)
                        }
                    } else {
                        weeksDelta = null
                        isOnTrack = !isCapped
                        deltaBadgeText = null
                    }

                    HorizonEstimationRow(
                        windowDays = windowDays,
                        state = HorizonRowState.Projected(
                            windowDays = windowDays,
                            paceDisplay2 = paceDisplay2,
                            velocity2InUnit = v2,
                            weeks = cappedWeeks,
                            rawWeeks = rawWeeks,
                            targetDate = targetDate,
                            barFraction = barFraction,
                            weeksDeltaVsTarget = weeksDelta,
                            isOnTrack = isOnTrack,
                            deltaBadgeText = deltaBadgeText
                        )
                    )
                }
            }
        }

        return MultiHorizonForecastState.Active(
            remainingWeightInUnit = remainingInUnit,
            targetWeightInUnit = target1InUnit,
            weightUnit = normalizedUnit,
            requiredPace = requiredPace,
            rows = rows
        )
    }

    fun computeMultiHorizonForecast(
        entries: List<WeightEntry>,
        cycle: Cycle,
        today: LocalDate = LocalDate.now(),
        targetWeightKg: Double? = cycle.targetWeightKg ?: cycle.targetBodyWeightKg,
        weightUnit: String = "kg"
    ): MultiHorizonForecastState? = computeMultiHorizonForecast(
        entries = entries,
        cycleStartDate = cycle.startDate,
        cycleEndDate = cycle.endDate,
        targetWeightKg = targetWeightKg,
        startingWeightKg = cycle.startingWeightKg,
        today = today,
        weightUnit = weightUnit
    )

    /**
     * Computes the goal pace forecast evaluating the strict precedence hierarchy:
     * 1. [PaceForecast.GoalReached]: W_current <= W_target
     * 2. [PaceForecast.CycleEnded]: today > cycleEndDate (and W_current > W_target or target is null)
     * 3. [PaceForecast.NotEnoughData]: no velocity tier below is resolvable
     * 4. [PaceForecast.Stalled]: Active deficit velocity <= 0.0 (rounded to 0.1)
     * 5. [PaceForecast.Projected]: Positive deficit velocity with projected arrival date (52w cap)
     *
     * Velocity Resolution Hierarchy (first non-null tier wins, regardless of sign):
     * - Tier 1 (Primary): 14-Day Smoothed Pace deficit velocity
     * - Tier 2 (Fallback): 7-Day Smoothed Pace deficit velocity
     *
     * Weights and velocities are compared at 1-decimal precision in [weightUnit], so GoalReached and the
     * estimated weeks agree with the remaining distance the card displays in that unit.
     */
    fun computePaceForecast(
        entries: List<WeightEntry>,
        cycleStartDate: LocalDate,
        cycleEndDate: LocalDate? = null,
        targetWeightKg: Double?,
        startingWeightKg: Double? = null,
        today: LocalDate = LocalDate.now(),
        weightUnit: String = "kg"
    ): PaceForecast? {
        val activeEntries = getActiveWeightEntries(entries, cycleStartDate, today)
        val isCycleEnded = cycleEndDate != null && today.isAfter(cycleEndDate)

        if (activeEntries.isEmpty()) {
            return if (isCycleEnded) PaceForecast.CycleEnded else PaceForecast.NotEnoughData
        }

        // Current weight is the 7D average over [today-6, today], the same window the card's 7D AVG pillar shows,
        // so the remaining distance reconciles even when today has no weigh-in.
        val newest = activeEntries.last()
        val currentWeight = computeCurrent7DayAverageWeight(
            entries = entries,
            cycleStartDate = cycleStartDate,
            referenceDate = today
        ) ?: newest.weightKg

        // 1. GoalReached (compared at the displayed 1-decimal precision in the display unit)
        if (targetWeightKg != null && targetWeightKg > 0.0 &&
            round1InUnit(currentWeight, weightUnit) <= round1InUnit(targetWeightKg, weightUnit)
        ) {
            return PaceForecast.GoalReached
        }

        // 2. CycleEnded
        if (isCycleEnded) {
            return PaceForecast.CycleEnded
        }

        // Unset or non-positive target weight -> skip forecast calculation
        if (targetWeightKg == null || targetWeightKg <= 0.0) {
            return null
        }

        // Guard: target weight cannot be >= start weight in Fat Loss mode
        val effectiveStartWeight = startingWeightKg ?: activeEntries.first().weightKg
        if (targetWeightKg >= effectiveStartWeight) {
            return null
        }

        // Tier 1: Primary 14-day smoothed pace deficit velocity
        val pace14 = compute14DayPace(
            entries = entries,
            cycleStartDate = cycleStartDate,
            today = newest.date,
            startingWeightKg = startingWeightKg
        )
        val v14 = pace14?.deficitVelocityKgPerWeek

        // Tier 2: Secondary 7-day smoothed pace deficit velocity
        val pace7 = compute7DayPace(
            entries = entries,
            cycleStartDate = cycleStartDate,
            today = newest.date,
            startingWeightKg = startingWeightKg
        )
        val v7 = pace7?.deficitVelocityKgPerWeek

        // 3. NotEnoughData: neither tier has a resolvable velocity.
        // Tiers are selected by availability (not sign) so the forecast agrees with the highest-priority pace shown.
        val (vActive, activeWindowDays, activeRateKgPerWeek) = when {
            v14 != null -> Triple(v14, 14, pace14.rateKgPerWeek)
            v7 != null -> Triple(v7, 7, pace7.rateKgPerWeek)
            else -> return PaceForecast.NotEnoughData
        }

        // 4. Stalled / 5. Projected: shared 1-decimal reconciliation math
        return computePaceForecast(
            currentWeightKg = currentWeight,
            targetWeightKg = targetWeightKg,
            velocityKgPerWeek = vActive,
            cycleEndDate = cycleEndDate,
            today = today,
            paceWindowDays = activeWindowDays,
            paceRateKgPerWeek = activeRateKgPerWeek,
            weightUnit = weightUnit
        )
    }

    /**
     * Overload of [computePaceForecast] taking a [Cycle] entity.
     */
    fun computePaceForecast(
        entries: List<WeightEntry>,
        cycle: Cycle,
        today: LocalDate = LocalDate.now(),
        targetWeightKg: Double? = cycle.targetWeightKg,
        weightUnit: String = "kg"
    ): PaceForecast? = computePaceForecast(
        entries = entries,
        cycleStartDate = cycle.startDate,
        cycleEndDate = cycle.endDate,
        targetWeightKg = targetWeightKg,
        startingWeightKg = cycle.startingWeightKg,
        today = today,
        weightUnit = weightUnit
    )

    /**
     * Direct mathematical calculation of [PaceForecast] given current weight, target weight, and velocity.
     */
    fun computePaceForecast(
        currentWeightKg: Double,
        targetWeightKg: Double?,
        velocityKgPerWeek: Double,
        cycleEndDate: LocalDate? = null,
        today: LocalDate = LocalDate.now(),
        paceWindowDays: Int? = null,
        paceRateKgPerWeek: Double? = null,
        weightUnit: String = "kg"
    ): PaceForecast? {
        val isCycleEnded = cycleEndDate != null && today.isAfter(cycleEndDate)

        if (targetWeightKg != null && targetWeightKg > 0.0 &&
            round1InUnit(currentWeightKg, weightUnit) <= round1InUnit(targetWeightKg, weightUnit)
        ) {
            return PaceForecast.GoalReached
        }

        if (isCycleEnded) {
            return PaceForecast.CycleEnded
        }

        if (targetWeightKg == null || targetWeightKg <= 0.0) {
            return null
        }

        val roundedV = round1(toUnit(velocityKgPerWeek, weightUnit) + 1e-5)
        if (roundedV <= 0.0) {
            return PaceForecast.Stalled
        }

        // Positive here: the rounded GoalReached check above already returned when this would be <= 0.
        val remaining = diff1(toUnit(currentWeightKg, weightUnit), toUnit(targetWeightKg, weightUnit))
        val rawWeeks = remaining / roundedV
        val resolvedRate = paceRateKgPerWeek ?: -velocityKgPerWeek

        return if (rawWeeks > 52.0) {
            PaceForecast.Projected(
                weeks = 52.0,
                targetDate = null,
                rawWeeks = rawWeeks,
                paceWindowDays = paceWindowDays,
                paceRateKgPerWeek = resolvedRate
            )
        } else {
            val daysRemaining = kotlin.math.round(rawWeeks * 7.0).toLong()
            val targetDate = today.plusDays(daysRemaining)
            PaceForecast.Projected(
                weeks = rawWeeks,
                targetDate = targetDate,
                rawWeeks = rawWeeks,
                paceWindowDays = paceWindowDays,
                paceRateKgPerWeek = resolvedRate
            )
        }
    }

    /**
     * Computes the required weekly weight-loss pace to reach [targetWeightKg] by [cycleEndDate],
     * using the 1-decimal rounded remaining weight in [weightUnit] and exact calendar weeks remaining.
     *
     * Returns null when:
     * - [today7dAvgKg], [targetWeightKg], or [cycleEndDate] is null or [targetWeightKg] <= 0.0
     * - [startWeightKg] is provided and `targetWeightKg >= startWeightKg` (non-deficit goal)
     * - `daysRemaining <= 0` (`today >= cycleEndDate`)
     * - 1-decimal rounded remaining weight in [weightUnit] is `<= 0.0` (goal already reached)
     *
     * The rate itself is never capped (a short horizon legitimately needs a steep pace); instead
     * [RequiredPaceResult.exceedsSustainableRate] flags paces above [MAX_SUSTAINABLE_WEEKLY_LOSS_FRACTION]
     * of the current weight so the UI can mark them as unrealistic.
     */
    fun computeRequiredPaceToEndDate(
        today7dAvgKg: Double?,
        targetWeightKg: Double?,
        cycleEndDate: LocalDate?,
        today: LocalDate = LocalDate.now(),
        weightUnit: String = "kg",
        startWeightKg: Double? = null
    ): RequiredPaceResult? {
        if (today7dAvgKg == null || targetWeightKg == null || targetWeightKg <= 0.0 || cycleEndDate == null) {
            return null
        }
        if (startWeightKg != null && targetWeightKg >= startWeightKg) {
            return null
        }

        val daysRemaining = ChronoUnit.DAYS.between(today, cycleEndDate)
        if (daysRemaining <= 0L) {
            return null
        }

        val isImperial = weightUnit.equals("lbs", ignoreCase = true)
        val currentDisplay = round1InUnit(today7dAvgKg, weightUnit)
        val remainingDisplay = diff1(currentDisplay, toUnit(targetWeightKg, weightUnit))
        if (remainingDisplay <= 0.0) {
            return null
        }

        val weeksRemaining = daysRemaining / 7.0
        val rawRequiredMagnitude = round1(remainingDisplay / weeksRemaining)
        val clampedMagnitude = maxOf(0.1, rawRequiredMagnitude)
        val normalizedUnit = if (isImperial) "lbs" else "kg"

        return RequiredPaceResult(
            requiredRateInUnit = -clampedMagnitude,
            remainingWeightInUnit = remainingDisplay,
            daysRemaining = daysRemaining,
            weeksRemaining = weeksRemaining,
            endDate = cycleEndDate,
            weightUnit = normalizedUnit,
            exceedsSustainableRate = clampedMagnitude > currentDisplay * MAX_SUSTAINABLE_WEEKLY_LOSS_FRACTION
        )
    }

    /**
     * Cycle-aware overload that resolves [today7dAvgKg] from [entries] within [cycle] (falling back to the
     * latest active in-cycle weigh-in when no weigh-in exists in the `[today - 6, today]` window) and
     * delegates to [computeRequiredPaceToEndDate].
     *
     * Only active for [CycleType.FAT_LOSS_BODYBUILDING] cycles with at least 1 active in-cycle weigh-in.
     */
    fun computeRequiredPaceToEndDate(
        entries: List<WeightEntry>,
        cycle: Cycle,
        today: LocalDate = LocalDate.now(),
        weightUnit: String = "kg"
    ): RequiredPaceResult? {
        if (cycle.type != CycleType.FAT_LOSS_BODYBUILDING) {
            return null
        }
        val active = getActiveWeightEntries(entries, cycle.startDate, today)
        if (active.isEmpty()) {
            return null
        }
        val hero = computeRollingWeightHero(entries, cycle.startDate, today)
        val currentAvgKg = hero.today7dAvgKg ?: active.last().weightKg
        val resolvedStartKg = cycle.startingWeightKg ?: active.first().weightKg
        return computeRequiredPaceToEndDate(
            today7dAvgKg = currentAvgKg,
            targetWeightKg = cycle.targetWeightKg,
            cycleEndDate = cycle.endDate,
            today = today,
            weightUnit = weightUnit,
            startWeightKg = resolvedStartKg
        )
    }

    // =========================================================================
    // Weight Progression Analytics & Pace Comparison
    // =========================================================================

    /**
     * Filters active weight entries within cycle boundaries up to [referenceDate],
     * excluding soft-deleted records and sorted chronologically.
     */
    fun getActiveWeightEntries(
        entries: List<WeightEntry>,
        cycleStartDate: LocalDate,
        referenceDate: LocalDate = LocalDate.now()
    ): List<WeightEntry> = entries
        .filter { it.deletedAtMillis == null && !it.date.isBefore(cycleStartDate) && !it.date.isAfter(referenceDate) }
        .sortedBy { it.date }

    /**
     * Computes day-over-day delta between the latest two in-cycle weigh-ins.
     * Returns null if fewer than 2 active entries exist.
     */
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

    fun computeDayOverDayDelta(
        entries: List<WeightEntry>,
        cycle: Cycle,
        referenceDate: LocalDate = LocalDate.now()
    ): DayOverDayDelta? = computeDayOverDayDelta(entries, cycle.startDate, referenceDate)

    /**
     * Computes the number of consecutive calendar days where body weight decreased
     * by more than [noiseThresholdKg].
     *
     * Stale recency guard: returns 0 if the latest entry is older than yesterday
     * (i.e. before referenceDate - 1 day).
     */
    fun computeWeightDecreasingStreak(
        entries: List<WeightEntry>,
        cycleStartDate: LocalDate,
        referenceDate: LocalDate = LocalDate.now(),
        noiseThresholdKg: Double = NOISE_THRESHOLD_KG
    ): Int {
        val active = getActiveWeightEntries(entries, cycleStartDate, referenceDate)
        if (active.size < 2) return 0
        if (active.last().date.isBefore(referenceDate.minusDays(1))) return 0 // Stale recency guard

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

    fun computeWeightDecreasingStreak(
        entries: List<WeightEntry>,
        cycle: Cycle,
        referenceDate: LocalDate = LocalDate.now(),
        noiseThresholdKg: Double = NOISE_THRESHOLD_KG
    ): Int = computeWeightDecreasingStreak(entries, cycle.startDate, referenceDate, noiseThresholdKg)

    /**
     * Computes the progressive moving average across active in-cycle entries in
     * [max(cycleStartDate, t.minusDays(maxWindowDays - 1)), t] where t is the latest active entry.
     *
     * Window span N = min(ChronoUnit.DAYS.between(cycleStartDate, t).toInt() + 1, maxWindowDays).
     * Returns null if no active entries exist or no entries exist in the window.
     */
    fun computeProgressiveAverage(
        entries: List<WeightEntry>,
        cycleStartDate: LocalDate,
        referenceDate: LocalDate = LocalDate.now(),
        maxWindowDays: Int = 14
    ): ProgressiveAverageResult? {
        val active = getActiveWeightEntries(entries, cycleStartDate, referenceDate)
        if (active.isEmpty()) return null
        val t = active.last().date

        val windowStart = if (t.minusDays((maxWindowDays - 1).toLong()).isBefore(cycleStartDate)) {
            cycleStartDate
        } else {
            t.minusDays((maxWindowDays - 1).toLong())
        }

        val windowEntries = active.filter { !it.date.isBefore(windowStart) && !it.date.isAfter(t) }
        val k = windowEntries.size
        if (k == 0) return null

        val avg = windowEntries.sumOf { it.weightKg } / k.toDouble()
        val daysFromStart = ChronoUnit.DAYS.between(cycleStartDate, t).toInt() + 1
        val n = kotlin.math.min(daysFromStart, maxWindowDays)

        return ProgressiveAverageResult(
            averageKg = avg,
            windowSpanDays = n,
            loggedDaysCount = k
        )
    }

    fun computeProgressiveAverage(
        entries: List<WeightEntry>,
        cycle: Cycle,
        referenceDate: LocalDate = LocalDate.now(),
        maxWindowDays: Int = 14
    ): ProgressiveAverageResult? = computeProgressiveAverage(
        entries = entries,
        cycleStartDate = cycle.startDate,
        referenceDate = referenceDate,
        maxWindowDays = maxWindowDays
    )

    /**
     * Compares the rolling pace rate (windowDays, e.g. 7 or 14) at anchor t against the prior pace at t - 7 days.
     * Prior pace is resolved using [computePaceRate] on or before t - 7 days.
     * Evaluates strict loss-to-loss conditions, acceleration/deceleration, and exact same-weekday matching.
     */
    fun computePaceComparisonVsLastWeek(
        entries: List<WeightEntry>,
        cycleStartDate: LocalDate,
        windowDays: Int,
        referenceDate: LocalDate = LocalDate.now(),
        minPercentageDenominatorKgPerWeek: Double = 0.1,
        startingWeightKg: Double? = null
    ): PaceComparisonResult? {
        val active = getActiveWeightEntries(entries, cycleStartDate, referenceDate)
        if (active.isEmpty()) return null
        val t = active.last().date

        val currentPace = computePaceRate(
            entries = entries,
            windowDays = windowDays,
            cycleStartDate = cycleStartDate,
            today = t,
            startingWeightKg = startingWeightKg
        ) ?: return null
        val priorPace = computePaceRate(
            entries = entries,
            windowDays = windowDays,
            cycleStartDate = cycleStartDate,
            today = t.minusDays(7),
            startingWeightKg = startingWeightKg
        ) ?: return null

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
            priorDate = priorPace.currentDate,
            isConsecutive = priorPace.currentDate == t.minusDays(1),
            isExactDayMatch = priorPace.currentDate == t.minusDays(7)
        )
    }

    fun computePaceComparisonVsLastWeek(
        entries: List<WeightEntry>,
        cycle: Cycle,
        windowDays: Int,
        referenceDate: LocalDate = LocalDate.now(),
        minPercentageDenominatorKgPerWeek: Double = 0.1
    ): PaceComparisonResult? = computePaceComparisonVsLastWeek(
        entries = entries,
        cycleStartDate = cycle.startDate,
        windowDays = windowDays,
        referenceDate = referenceDate,
        minPercentageDenominatorKgPerWeek = minPercentageDenominatorKgPerWeek,
        startingWeightKg = cycle.startingWeightKg
    )

    /**
     * Formats current weight subtitle / delta pill text.
     * Includes stale date prefix if latest entry is before referenceDate.
     */
    fun formatCurrentWeightSubtitle(
        delta: DayOverDayDelta?,
        weightUnit: String,
        referenceDate: LocalDate = LocalDate.now()
    ): String {
        if (delta == null) return "First weigh-in"

        val isImperial = weightUnit.equals("lbs", ignoreCase = true)
        val unitLabel = if (isImperial) "lbs" else "kg"
        val deltaInUnit = if (isImperial) WeightAnalytics.kgToLbs(delta.deltaKg) else delta.deltaKg

        val stalePrefix = if (delta.currentDate.isBefore(referenceDate)) {
            "${delta.currentDate.format(shortDateFormatter)} · "
        } else {
            ""
        }

        val deltaPart = when {
            delta.deltaKg < -NOISE_THRESHOLD_KG ->
                String.format(Locale.US, "▼ %.1f %s", deltaInUnit, unitLabel)
            delta.deltaKg > NOISE_THRESHOLD_KG ->
                String.format(Locale.US, "▲ +%.1f %s", deltaInUnit, unitLabel)
            else ->
                String.format(Locale.US, "0.0 %s", unitLabel)
        }

        val vsPart = if (delta.isConsecutive && delta.currentDate == referenceDate) {
            "vs yesterday"
        } else {
            "vs ${delta.priorDate.format(shortDateFormatter)}"
        }

        return "$stalePrefix$deltaPart $vsPart"
    }

    /**
     * Formats pace subtitle describing same-weekday comparison, acceleration, deceleration,
     * noise deadband, or absolute rate delta.
     */
    fun formatPaceSubtitle(
        comparison: PaceComparisonResult?,
        weightUnit: String,
        windowDays: Int,
        pace: PaceRateResult? = null,
        referenceDate: LocalDate = LocalDate.now(),
        useTwoDecimals: Boolean = false
    ): String {
        if (comparison == null) {
            return if (windowDays == 7) {
                "Baseline 7d pace"
            } else {
                if (pace == null) "Needs weigh-in ${windowDays}+ days ago" else "Smoothed trend"
            }
        }

        if (kotlin.math.abs(comparison.deltaRateKgPerWeek) < PACE_NOISE_THRESHOLD_KG_PER_WEEK) {
            return "Pace unchanged"
        }

        val vsPart = when {
            comparison.isExactDayMatch -> {
                val dayName = comparison.priorDate.dayOfWeek.getDisplayName(TextStyle.SHORT, Locale.US)
                "vs last $dayName"
            }
            comparison.isConsecutive && comparison.priorDate == referenceDate.minusDays(1) -> "vs yesterday"
            else -> "vs ${comparison.priorDate.format(shortDateFormatter)}"
        }

        if (comparison.percentChange != null) {
            val pct = java.math.BigDecimal.valueOf(kotlin.math.abs(comparison.percentChange))
                .setScale(0, java.math.RoundingMode.HALF_UP)
                .toInt()
            val descriptor = if (comparison.isAcceleratingDeficit) "faster" else "slower"
            return "$pct% $descriptor loss $vsPart"
        }

        val isImperial = weightUnit.equals("lbs", ignoreCase = true)
        val unitLabel = if (isImperial) "lbs/wk" else "kg/wk"
        val deltaInUnit = if (isImperial) WeightAnalytics.kgToLbs(comparison.deltaRateKgPerWeek) else comparison.deltaRateKgPerWeek

        val sign = if (deltaInUnit > 0.0) "+" else ""
        val formatPattern = if (useTwoDecimals) "%s%.2f %s %s" else "%s%.1f %s %s"
        val roundedDelta = if (useTwoDecimals) round2(deltaInUnit) else deltaInUnit
        return String.format(Locale.US, formatPattern, sign, roundedDelta, unitLabel, vsPart)
    }

    /**
     * Formats 7-day pace subtitle describing acceleration, deceleration, noise deadbands, or absolute rate delta.
     */
    fun format7DayPaceSubtitle(
        comparison: PaceComparisonResult?,
        weightUnit: String,
        referenceDate: LocalDate = LocalDate.now(),
        useTwoDecimals: Boolean = false
    ): String = formatPaceSubtitle(
        comparison = comparison,
        weightUnit = weightUnit,
        windowDays = 7,
        pace = null,
        referenceDate = referenceDate,
        useTwoDecimals = useTwoDecimals
    )

    /**
     * Formats 14-day pace subtitle describing same-weekday comparison or maturity fallback states.
     */
    fun format14DayPaceSubtitle(
        comparison: PaceComparisonResult?,
        pace14: PaceRateResult?,
        weightUnit: String,
        referenceDate: LocalDate = LocalDate.now(),
        useTwoDecimals: Boolean = false
    ): String = formatPaceSubtitle(
        comparison = comparison,
        weightUnit = weightUnit,
        windowDays = 14,
        pace = pace14,
        referenceDate = referenceDate,
        useTwoDecimals = useTwoDecimals
    )

    /**
     * Formats 21-day pace subtitle describing same-weekday comparison or maturity fallback states.
     */
    fun format21DayPaceSubtitle(
        comparison: PaceComparisonResult?,
        pace21: PaceRateResult?,
        weightUnit: String,
        referenceDate: LocalDate = LocalDate.now(),
        useTwoDecimals: Boolean = false
    ): String = formatPaceSubtitle(
        comparison = comparison,
        weightUnit = weightUnit,
        windowDays = 21,
        pace = pace21,
        referenceDate = referenceDate,
        useTwoDecimals = useTwoDecimals
    )

    /**
     * Formats 28-day pace subtitle describing same-weekday comparison or maturity fallback states.
     */
    fun format28DayPaceSubtitle(
        comparison: PaceComparisonResult?,
        pace28: PaceRateResult?,
        weightUnit: String,
        referenceDate: LocalDate = LocalDate.now(),
        useTwoDecimals: Boolean = false
    ): String = formatPaceSubtitle(
        comparison = comparison,
        weightUnit = weightUnit,
        windowDays = 28,
        pace = pace28,
        referenceDate = referenceDate,
        useTwoDecimals = useTwoDecimals
    )

    /**
     * Computes the secondary 3-column raw same-day weigh-in comparison state.
     * Compares raw weigh-ins on identical weekdays across t, t-7d, and t-14d.
     *
     * Anchor: t is strictly [referenceDate]. When t is unlogged, [SameDayWeightTrioState.currentDailyWeightKg]
     * is null and both deltas evaluate to null.
     * Eligibility Filter: deletedAtMillis == null && !date.isBefore(cycleStartDate) && !date.isAfter(referenceDate).
     * Exact-Date Lookup: firstOrNull exact date match. Missing or pre-cycle entries resolve to null.
     * State deltas are computed using diff1(W_t, W_prior).
     */
    fun computeSameDayWeightTrio(
        entries: List<WeightEntry>,
        cycleStartDate: LocalDate,
        referenceDate: LocalDate = LocalDate.now()
    ): SameDayWeightTrioState {
        val active = getActiveWeightEntries(entries, cycleStartDate, referenceDate)
        val currentEntry = active.firstOrNull { it.date == referenceDate }
        val lastWeekEntry = active.firstOrNull { it.date == referenceDate.minusDays(7) }
        val twoWeeksAgoEntry = active.firstOrNull { it.date == referenceDate.minusDays(14) }

        val currentDailyWeightKg = currentEntry?.weightKg
        val lastWeekSameDayWeightKg = lastWeekEntry?.weightKg
        val twoWeeksAgoSameDayWeightKg = twoWeeksAgoEntry?.weightKg

        val deltaCurrentVsLastWeek = if (currentDailyWeightKg != null && lastWeekSameDayWeightKg != null) {
            diff1(currentDailyWeightKg, lastWeekSameDayWeightKg)
        } else {
            null
        }

        val deltaCurrentVsTwoWeeksAgo = if (currentDailyWeightKg != null && twoWeeksAgoSameDayWeightKg != null) {
            diff1(currentDailyWeightKg, twoWeeksAgoSameDayWeightKg)
        } else {
            null
        }

        return SameDayWeightTrioState(
            currentDailyWeightKg = currentDailyWeightKg,
            lastWeekSameDayWeightKg = lastWeekSameDayWeightKg,
            deltaCurrentVsLastWeekSameDayKg = deltaCurrentVsLastWeek,
            twoWeeksAgoSameDayWeightKg = twoWeeksAgoSameDayWeightKg,
            deltaCurrentVsTwoWeeksAgoSameDayKg = deltaCurrentVsTwoWeeksAgo
        )
    }

    /**
     * Overload of [computeSameDayWeightTrio] taking a [Cycle] entity.
     */
    fun computeSameDayWeightTrio(
        entries: List<WeightEntry>,
        cycle: Cycle,
        referenceDate: LocalDate = LocalDate.now()
    ): SameDayWeightTrioState = computeSameDayWeightTrio(entries, cycle.startDate, referenceDate)

    /**
     * Evaluates displayed delta under the Display-Unit Arithmetic Consistency Invariant:
     * Δ_display = diff1(round1(toDisplay(W_t)), round1(toDisplay(W_prior)))
     */
    fun computeSameDayDisplayDelta(
        currentWeightKg: Double?,
        priorWeightKg: Double?,
        weightUnit: String
    ): Double? {
        if (currentWeightKg == null || priorWeightKg == null) return null
        val isImperial = weightUnit.equals("lbs", ignoreCase = true)
        val curDisplay = if (isImperial) WeightAnalytics.kgToLbs(currentWeightKg) else currentWeightKg
        val priorDisplay = if (isImperial) WeightAnalytics.kgToLbs(priorWeightKg) else priorWeightKg
        return diff1(round1(curDisplay), round1(priorDisplay))
    }

    /**
     * Formats the displayed delta string for same-day weight comparisons.
     * Evaluates deadband (|Δ_display| <= 0.05) to display "0.0 <unit>" without +/- sign.
     */
    fun formatSameDayDisplayDelta(
        deltaDisplay: Double?,
        weightUnit: String
    ): String {
        if (deltaDisplay == null) return NO_DATA_PLACEHOLDER
        return when {
            deltaDisplay < -NOISE_THRESHOLD_KG -> String.format(Locale.US, "%.1f %s", deltaDisplay, weightUnit)
            deltaDisplay > NOISE_THRESHOLD_KG -> String.format(Locale.US, "+%.1f %s", deltaDisplay, weightUnit)
            else -> String.format(Locale.US, "0.0 %s", weightUnit)
        }
    }

    /**
     * Convenience formatter taking raw kg values and localized weight unit.
     */
    fun formatSameDayWeightDelta(
        currentWeightKg: Double?,
        priorWeightKg: Double?,
        weightUnit: String
    ): String {
        val deltaDisplay = computeSameDayDisplayDelta(currentWeightKg, priorWeightKg, weightUnit)
            ?: return NO_DATA_PLACEHOLDER
        return formatSameDayDisplayDelta(deltaDisplay, weightUnit)
    }
}

