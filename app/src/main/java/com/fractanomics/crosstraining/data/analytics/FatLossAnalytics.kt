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
     */
    data class Projected(
        val weeks: Double,
        val targetDate: LocalDate? = null,
        val rawWeeks: Double = weeks
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
     * Computes the signed pace rate for a given rolling window (e.g. 7 or 14 days).
     *
     * Invariants:
     * - Minimum sampling guard: requires >= 2 active entries spanning >= 7 calendar days.
     * - Anchor resolution: finds the most recent entry on or before t - windowDays (within cycle).
     * - Normalization: normalized by actual elapsed days between anchor and current weigh-in.
     * - Returns null if sampling guard fails, anchor cannot be resolved, or elapsed days <= 0.
     */
    fun computePaceRate(
        entries: List<WeightEntry>,
        windowDays: Int,
        cycleStartDate: LocalDate,
        today: LocalDate = LocalDate.now()
    ): PaceRateResult? {
        val activeEntries = entries
            .filter { it.deletedAtMillis == null && !it.date.isBefore(cycleStartDate) && !it.date.isAfter(today) }
            .sortedBy { it.date }

        if (activeEntries.size < 2) return null
        val oldest = activeEntries.first()
        val newest = activeEntries.last()

        val spanDays = ChronoUnit.DAYS.between(oldest.date, newest.date)
        if (spanDays < 7) return null

        val cutoffDate = newest.date.minusDays(windowDays.toLong())
        val anchor = resolveAnchorEntry(activeEntries, cutoffDate, cycleStartDate) ?: return null

        val elapsedDays = ChronoUnit.DAYS.between(anchor.date, newest.date)
        if (elapsedDays <= 0) return null

        val rate = ((newest.weightKg - anchor.weightKg) / elapsedDays.toDouble()) * 7.0
        val trend = getPaceTrend(rate)

        return PaceRateResult(
            rateKgPerWeek = rate,
            anchorWeightKg = anchor.weightKg,
            anchorDate = anchor.date,
            currentWeightKg = newest.weightKg,
            currentDate = newest.date,
            elapsedDays = elapsedDays,
            trend = trend
        )
    }

    /**
     * Computes 7-day rolling pace rate.
     */
    fun compute7DayPace(
        entries: List<WeightEntry>,
        cycleStartDate: LocalDate,
        today: LocalDate = LocalDate.now()
    ): PaceRateResult? = computePaceRate(entries, windowDays = 7, cycleStartDate = cycleStartDate, today = today)

    fun compute7DayPace(
        entries: List<WeightEntry>,
        cycle: Cycle,
        today: LocalDate = LocalDate.now()
    ): PaceRateResult? = compute7DayPace(entries, cycle.startDate, today)

    /**
     * Computes 14-day rolling pace rate.
     */
    fun compute14DayPace(
        entries: List<WeightEntry>,
        cycleStartDate: LocalDate,
        today: LocalDate = LocalDate.now()
    ): PaceRateResult? = computePaceRate(entries, windowDays = 14, cycleStartDate = cycleStartDate, today = today)

    fun compute14DayPace(
        entries: List<WeightEntry>,
        cycle: Cycle,
        today: LocalDate = LocalDate.now()
    ): PaceRateResult? = compute14DayPace(entries, cycle.startDate, today)

    /**
     * Computes the goal pace forecast evaluating the strict precedence hierarchy:
     * 1. [PaceForecast.GoalReached]: W_current <= W_target
     * 2. [PaceForecast.CycleEnded]: today > cycleEndDate (and W_current > W_target or target is null)
     * 3. [PaceForecast.NotEnoughData]: < 2 entries or span < 7 days
     * 4. [PaceForecast.Stalled]: Active deficit velocity <= 0.0 (rounded to 0.1)
     * 5. [PaceForecast.Projected]: Positive deficit velocity with projected arrival date (52w cap)
     *
     * Unset Target Behavior:
     * When targetWeightKg is null or non-positive, returns null (or CycleEnded if cycle has ended).
     */
    fun computePaceForecast(
        entries: List<WeightEntry>,
        cycleStartDate: LocalDate,
        cycleEndDate: LocalDate? = null,
        targetWeightKg: Double?,
        startingWeightKg: Double? = null,
        today: LocalDate = LocalDate.now()
    ): PaceForecast? {
        val activeEntries = entries
            .filter { it.deletedAtMillis == null && !it.date.isBefore(cycleStartDate) && !it.date.isAfter(today) }
            .sortedBy { it.date }

        val isCycleEnded = cycleEndDate != null && today.isAfter(cycleEndDate)

        if (activeEntries.isEmpty()) {
            return if (isCycleEnded) PaceForecast.CycleEnded else PaceForecast.NotEnoughData
        }

        val newest = activeEntries.last()
        val currentWeight = newest.weightKg

        // 1. GoalReached
        if (targetWeightKg != null && targetWeightKg > 0.0 && currentWeight <= targetWeightKg) {
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

        // 3. NotEnoughData
        if (activeEntries.size < 2) {
            return PaceForecast.NotEnoughData
        }
        val oldest = activeEntries.first()
        val spanDays = ChronoUnit.DAYS.between(oldest.date, newest.date)
        if (spanDays < 7) {
            return PaceForecast.NotEnoughData
        }

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

        // 4. Stalled
        val roundedV = kotlin.math.round((vActive + 1e-5) * 10.0) / 10.0
        if (roundedV <= 0.0) {
            return PaceForecast.Stalled
        }

        // 5. Projected
        val remainingKg = currentWeight - targetWeightKg
        val rawWeeks = remainingKg / vActive

        return if (rawWeeks > 52.0) {
            PaceForecast.Projected(
                weeks = 52.0,
                targetDate = null,
                rawWeeks = rawWeeks
            )
        } else {
            val daysRemaining = kotlin.math.round(rawWeeks * 7.0).toLong()
            val targetDate = today.plusDays(daysRemaining)
            PaceForecast.Projected(
                weeks = rawWeeks,
                targetDate = targetDate,
                rawWeeks = rawWeeks
            )
        }
    }

    /**
     * Overload of [computePaceForecast] taking a [Cycle] entity.
     */
    fun computePaceForecast(
        entries: List<WeightEntry>,
        cycle: Cycle,
        today: LocalDate = LocalDate.now(),
        targetWeightKg: Double? = cycle.targetWeightKg
    ): PaceForecast? = computePaceForecast(
        entries = entries,
        cycleStartDate = cycle.startDate,
        cycleEndDate = cycle.endDate,
        targetWeightKg = targetWeightKg,
        startingWeightKg = cycle.startingWeightKg,
        today = today
    )

    /**
     * Direct mathematical calculation of [PaceForecast] given current weight, target weight, and velocity.
     */
    fun computePaceForecast(
        currentWeightKg: Double,
        targetWeightKg: Double?,
        velocityKgPerWeek: Double,
        cycleEndDate: LocalDate? = null,
        today: LocalDate = LocalDate.now()
    ): PaceForecast? {
        val isCycleEnded = cycleEndDate != null && today.isAfter(cycleEndDate)

        if (targetWeightKg != null && targetWeightKg > 0.0 && currentWeightKg <= targetWeightKg) {
            return PaceForecast.GoalReached
        }

        if (isCycleEnded) {
            return PaceForecast.CycleEnded
        }

        if (targetWeightKg == null || targetWeightKg <= 0.0) {
            return null
        }

        val roundedV = kotlin.math.round(velocityKgPerWeek * 10.0) / 10.0
        if (roundedV <= 0.0) {
            return PaceForecast.Stalled
        }

        val remainingKg = currentWeightKg - targetWeightKg
        val rawWeeks = remainingKg / velocityKgPerWeek

        return if (rawWeeks > 52.0) {
            PaceForecast.Projected(
                weeks = 52.0,
                targetDate = null,
                rawWeeks = rawWeeks
            )
        } else {
            val daysRemaining = kotlin.math.round(rawWeeks * 7.0).toLong()
            val targetDate = today.plusDays(daysRemaining)
            PaceForecast.Projected(
                weeks = rawWeeks,
                targetDate = targetDate,
                rawWeeks = rawWeeks
            )
        }
    }
}

