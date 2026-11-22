package com.fractanomics.crosstraining.data.analytics

import androidx.compose.runtime.Immutable
import com.fractanomics.crosstraining.data.model.Cycle
import com.fractanomics.crosstraining.data.model.WeightEntry
import com.fractanomics.crosstraining.ui.BlockPerformance
import com.fractanomics.crosstraining.ui.DayPerformance
import com.fractanomics.crosstraining.ui.formatShort
import java.math.BigDecimal
import java.math.RoundingMode
import java.time.LocalDate
import java.time.temporal.ChronoUnit
import java.util.NavigableMap
import java.util.TreeMap

/** Resolution strategy used to determine an athlete's body weight for a target date or goal. */
enum class BodyWeightMatchKind {
    EXACT,
    ESTIMATED_PRIOR,
    TARGET_CYCLE_BW,
    LATEST_BW,
    UNAVAILABLE
}

/**
 * Resolved body weight metadata used as the denominator for `% BW` relative-strength calculations.
 */
@Immutable
data class ResolvedBodyWeight(
    val kind: BodyWeightMatchKind,
    val weightKg: Double?,
    val sourceDate: LocalDate?,
    val daysPrior: Int? = null
) {
    val isEstimated: Boolean
        get() = kind == BodyWeightMatchKind.ESTIMATED_PRIOR || kind == BodyWeightMatchKind.LATEST_BW

    val isAvailable: Boolean
        get() = weightKg != null && weightKg.isFinite() && weightKg > 0.0
}

/**
 * Highest relative `% BW` achievement across a filtered performance series, tracked independently
 * from the heaviest absolute raw lift.
 */
@Immutable
data class PeakRelativeStrength(
    val percentBw: Double,
    val rawWeight: Double,
    val date: LocalDate,
    val resolvedBodyWeight: ResolvedBodyWeight
)

/**
 * Per-day `% BW` relative-strength projection aligned with [DayPerformance].
 */
@Immutable
data class DayRelativeMetrics(
    val date: LocalDate,
    val bodyWeight: ResolvedBodyWeight,
    val topPercentBw: Double?,
    val averagePercentBw: Double?,
    val volumePercentBw: Double?
)

/**
 * Pure, deterministic analytics engine for calculating Body-Weight Percentage (`% BW`) indices,
 * body-weight fallback lookups, Peak `% BW`, and accessible/TalkBack descriptions with zero DB migration.
 */
object RelativeStrengthAnalytics {

    const val MAX_LOOKBACK_DAYS: Long = 7L

    private val PERCENT_SCALE_MULTIPLIER = BigDecimal.valueOf(100L)

    /** Builds a sorted index of active, physiologically valid [WeightEntry] rows keyed by [LocalDate]. */
    fun buildActiveWeightIndex(entries: List<WeightEntry>): NavigableMap<LocalDate, WeightEntry> {
        val map = TreeMap<LocalDate, WeightEntry>()
        for (entry in entries) {
            if (entry.deletedAtMillis == null && entry.weightKg.isFinite() && entry.weightKg > 0.0) {
                map[entry.date] = entry
            }
        }
        return map
    }

    /**
     * Resolves body weight for [targetDate]:
     * 1. Exact same-day active entry -> [BodyWeightMatchKind.EXACT]
     * 2. Latest active entry within `1..maxLookbackDays` days prior -> [BodyWeightMatchKind.ESTIMATED_PRIOR]
     * 3. Otherwise -> [BodyWeightMatchKind.UNAVAILABLE]
     */
    fun resolveBodyWeightForDate(
        targetDate: LocalDate,
        weightIndex: NavigableMap<LocalDate, WeightEntry>,
        maxLookbackDays: Long = MAX_LOOKBACK_DAYS
    ): ResolvedBodyWeight {
        if (weightIndex.isEmpty()) {
            return ResolvedBodyWeight(BodyWeightMatchKind.UNAVAILABLE, null, null, null)
        }
        val floor = weightIndex.floorEntry(targetDate)
            ?: return ResolvedBodyWeight(BodyWeightMatchKind.UNAVAILABLE, null, null, null)

        val daysBetween = ChronoUnit.DAYS.between(floor.key, targetDate)
        return when {
            daysBetween == 0L -> ResolvedBodyWeight(
                kind = BodyWeightMatchKind.EXACT,
                weightKg = floor.value.weightKg,
                sourceDate = floor.key,
                daysPrior = 0
            )
            daysBetween in 1L..maxLookbackDays -> ResolvedBodyWeight(
                kind = BodyWeightMatchKind.ESTIMATED_PRIOR,
                weightKg = floor.value.weightKg,
                sourceDate = floor.key,
                daysPrior = daysBetween.toInt()
            )
            else -> ResolvedBodyWeight(BodyWeightMatchKind.UNAVAILABLE, null, null, null)
        }
    }

    /**
     * Resolves the denominator body weight for a Cycle Goal's target weight:
     * prefers [Cycle.targetWeightKg] (`TARGET_CYCLE_BW`), falling back to the latest active
     * [WeightEntry] (`LATEST_BW`), or `UNAVAILABLE`.
     */
    fun resolveCycleGoalTargetBodyWeight(
        cycle: Cycle?,
        weightIndex: NavigableMap<LocalDate, WeightEntry>
    ): ResolvedBodyWeight {
        val targetBw = cycle?.targetWeightKg?.takeIf { it.isFinite() && it > 0.0 }
        if (targetBw != null) {
            return ResolvedBodyWeight(
                kind = BodyWeightMatchKind.TARGET_CYCLE_BW,
                weightKg = targetBw,
                sourceDate = null,
                daysPrior = null
            )
        }
        val latest = weightIndex.lastEntry()?.value
        if (latest != null) {
            return ResolvedBodyWeight(
                kind = BodyWeightMatchKind.LATEST_BW,
                weightKg = latest.weightKg,
                sourceDate = latest.date,
                daysPrior = null
            )
        }
        return ResolvedBodyWeight(BodyWeightMatchKind.UNAVAILABLE, null, null, null)
    }

    /** True only for mass units (`kg`, `lb`, `lbs`), excluding conditioning/rep-only units. */
    fun isWeightedUnit(unit: String?): Boolean {
        val normalized = unit?.trim()?.lowercase().orEmpty()
        return normalized == "kg" || normalized == "kgs" || normalized == "lb" || normalized == "lbs"
    }

    /** True if [unit] is a mass unit and [blocks] contains at least one weighted working set (or is empty). */
    fun hasWeightedSets(blocks: List<BlockPerformance>, unit: String?): Boolean {
        if (!isWeightedUnit(unit)) return false
        if (blocks.isEmpty()) return true
        return blocks.any { bp -> bp.workingSets.any { (it.weight ?: 0.0) > 0.0 } }
    }

    /** Converts [value] from [unit] (`kg`, `lb`, `lbs`) into kilograms. */
    fun toWeightKg(value: Double, unit: String?): Double {
        val normalized = unit?.trim()?.lowercase().orEmpty()
        return if (normalized == "lb" || normalized == "lbs") {
            WeightAnalytics.lbsToKg(value)
        } else {
            value
        }
    }

    /**
     * Computes `(liftedWeightKg / bodyWeightKg) * 100` rounded `HALF_UP` to 1 decimal place.
     * Returns `null` if inputs are non-weighted, non-positive, non-finite, or body weight is unavailable.
     */
    fun calculatePercentBw(
        liftedWeight: Double?,
        liftedUnit: String?,
        bodyWeight: ResolvedBodyWeight
    ): Double? {
        if (!isWeightedUnit(liftedUnit)) return null
        val lifted = liftedWeight?.takeIf { it.isFinite() && it > 0.0 } ?: return null
        val bwKg = bodyWeight.weightKg?.takeIf { it.isFinite() && it > 0.0 } ?: return null
        val liftedKg = toWeightKg(lifted, liftedUnit)
        if (!liftedKg.isFinite() || liftedKg <= 0.0) return null

        return BigDecimal.valueOf(liftedKg)
            .multiply(PERCENT_SCALE_MULTIPLIER)
            .divide(BigDecimal.valueOf(bwKg), 1, RoundingMode.HALF_UP)
            .toDouble()
    }

    /** Computes reconciled delta (`latestPct - previousPct`) rounded `HALF_UP` to 1 decimal place. */
    fun computePercentBwDelta(latestPct: Double?, previousPct: Double?): Double? {
        if (latestPct == null || previousPct == null) return null
        if (!latestPct.isFinite() || !previousPct.isFinite()) return null
        return BigDecimal.valueOf(latestPct)
            .subtract(BigDecimal.valueOf(previousPct))
            .setScale(1, RoundingMode.HALF_UP)
            .toDouble()
    }

    /** Formats a numeric value to 1 decimal place trimmed when whole, using deterministic `.` separator. */
    fun formatScaled1Trimmed(value: Double): String {
        if (!value.isFinite()) return "—"
        val scaled = BigDecimal.valueOf(value).setScale(1, RoundingMode.HALF_UP)
        val normalized = if (scaled.compareTo(BigDecimal.ZERO) == 0) {
            BigDecimal.ZERO
        } else {
            scaled.stripTrailingZeros()
        }
        return normalized.toPlainString()
    }

    /** Formats `% BW` badge text (e.g. `"142.5% BW"`, `"140% BW"`, `"~142.5% BW"`, or `"—"`). */
    fun formatPercentBw(percentBw: Double?, isEstimated: Boolean = false): String {
        if (percentBw == null || !percentBw.isFinite()) return "—"
        val prefix = if (isEstimated) "~" else ""
        return "$prefix${formatScaled1Trimmed(percentBw)}% BW"
    }

    /** Formats compact `%` suffix for dense table columns (e.g. `"142.5%"`, `"~140%"`, or `"—"`). */
    fun formatCompactPercent(percentBw: Double?, isEstimated: Boolean = false): String {
        if (percentBw == null || !percentBw.isFinite()) return "—"
        val prefix = if (isEstimated) "~" else ""
        return "$prefix${formatScaled1Trimmed(percentBw)}%"
    }

    /** Formats a signed delta suffix for `DeltaText` (e.g. `"+3.5% BW"` or `"-1.2% BW"`). */
    fun formatSignedDeltaPercentBw(deltaPct: Double): String {
        val sign = if (deltaPct > 0.0) "+" else ""
        return "$sign${formatScaled1Trimmed(deltaPct)}% BW"
    }

    /** Formats human-readable body-weight source disclosure for expanded history & goal cards. */
    fun formatBodyWeightAnchor(
        resolved: ResolvedBodyWeight,
        displayUnit: String = "kg"
    ): String {
        val bwKg = resolved.weightKg
        if (bwKg == null || !bwKg.isFinite() || bwKg <= 0.0) {
            return "No body weight logged within 7 days"
        }
        val useLbs = displayUnit.trim().equals("lbs", ignoreCase = true) ||
            displayUnit.trim().equals("lb", ignoreCase = true)
        val displayVal = if (useLbs) WeightAnalytics.kgToLbs(bwKg) else bwKg
        val unitLabel = if (useLbs) "lbs" else "kg"
        val formattedVal = "${formatScaled1Trimmed(displayVal)} $unitLabel"
        return when (resolved.kind) {
            BodyWeightMatchKind.EXACT ->
                "BW: $formattedVal (${resolved.sourceDate?.formatShort() ?: ""} · exact)"
            BodyWeightMatchKind.ESTIMATED_PRIOR -> {
                val days = resolved.daysPrior ?: 0
                "BW: $formattedVal (${resolved.sourceDate?.formatShort() ?: ""} · est. ${days}d prior)"
            }
            BodyWeightMatchKind.TARGET_CYCLE_BW ->
                "Goal anchor: Target BW $formattedVal"
            BodyWeightMatchKind.LATEST_BW ->
                "Goal anchor: Latest BW $formattedVal (${resolved.sourceDate?.formatShort() ?: ""})"
            BodyWeightMatchKind.UNAVAILABLE ->
                "No body weight logged within 7 days"
        }
    }

    /** Formats TalkBack-friendly `% BW` phrasing free of `~` or `—` symbols. */
    fun formatTalkBackPercentBw(
        percentBw: Double?,
        resolved: ResolvedBodyWeight
    ): String {
        if (percentBw == null || !percentBw.isFinite()) {
            return "no body weight logged within 7 days"
        }
        val numText = formatScaled1Trimmed(percentBw)
        return when (resolved.kind) {
            BodyWeightMatchKind.EXACT -> "$numText percent body weight"
            BodyWeightMatchKind.ESTIMATED_PRIOR -> {
                val datePart = resolved.sourceDate?.formatShort()?.let { " from $it" }.orEmpty()
                "$numText percent body weight, estimated$datePart"
            }
            BodyWeightMatchKind.TARGET_CYCLE_BW -> "$numText percent of target body weight"
            BodyWeightMatchKind.LATEST_BW -> {
                val datePart = resolved.sourceDate?.formatShort()?.let { " from $it" }.orEmpty()
                "$numText percent body weight, estimated from latest weight$datePart"
            }
            BodyWeightMatchKind.UNAVAILABLE -> "no body weight logged within 7 days"
        }
    }

    /**
     * Evaluates the highest `% BW` across weighted [blocks] independently of heaviest raw weight.
     */
    fun resolvePeakRelativeStrength(
        blocks: List<BlockPerformance>,
        unit: String?,
        weightIndex: NavigableMap<LocalDate, WeightEntry>
    ): PeakRelativeStrength? {
        if (!isWeightedUnit(unit) || weightIndex.isEmpty()) return null
        var best: PeakRelativeStrength? = null
        for (bp in blocks) {
            val topWeight = bp.workingSets
                .mapNotNull { it.weight?.takeIf { w -> w.isFinite() && w > 0.0 } }
                .maxOrNull() ?: continue
            val resolvedBw = resolveBodyWeightForDate(bp.date, weightIndex)
            val pct = calculatePercentBw(topWeight, unit, resolvedBw) ?: continue
            if (best == null || pct > best.percentBw || (pct == best.percentBw && bp.date >= best.date)) {
                best = PeakRelativeStrength(
                    percentBw = pct,
                    rawWeight = topWeight,
                    date = bp.date,
                    resolvedBodyWeight = resolvedBw
                )
            }
        }
        return best
    }

    /**
     * Projects each [DayPerformance] into a [DayRelativeMetrics] entry aligned 1:1 by index/date.
     */
    fun resolveDayRelativeMetrics(
        days: List<DayPerformance>,
        unit: String?,
        weightIndex: NavigableMap<LocalDate, WeightEntry>
    ): List<DayRelativeMetrics> {
        if (!isWeightedUnit(unit)) return emptyList()
        return days.map { day ->
            val bw = resolveBodyWeightForDate(day.date, weightIndex)
            DayRelativeMetrics(
                date = day.date,
                bodyWeight = bw,
                topPercentBw = calculatePercentBw(day.top, unit, bw),
                averagePercentBw = calculatePercentBw(day.average, unit, bw),
                volumePercentBw = calculatePercentBw(day.volume, unit, bw)
            )
        }
    }
}
