package com.fractanomics.crosstraining.data.analytics

import com.fractanomics.crosstraining.data.model.BlockKind
import com.fractanomics.crosstraining.data.model.BlockSet
import com.fractanomics.crosstraining.data.model.BlockWithSets
import com.fractanomics.crosstraining.data.model.DailyLog
import com.fractanomics.crosstraining.data.model.Exercise
import com.fractanomics.crosstraining.data.model.MetricType
import com.fractanomics.crosstraining.data.model.SessionBlock
import com.fractanomics.crosstraining.data.model.SessionWithBlocks
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
}
