package com.fractanomics.crosstraining.data.analytics

import com.fractanomics.crosstraining.data.model.WeightEntry
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Locale

/**
 * Filter timeframes for athlete weight history display.
 */
enum class Timeframe(val label: String, val days: Long?) {
    SEVEN_DAYS("7D", 7L),
    THIRTY_DAYS("30D", 30L),
    NINETY_DAYS("90D", 90L),
    ONE_YEAR("1Y", 365L),
    ALL("All", null)
}

/**
 * Unified index-parallel data point combining the raw logged weight and the 7-day Simple Moving Average.
 *
 * @property date The calendar date of this weigh-in.
 * @property label Formatted date string for chart X-axis labels (e.g., "7 Sep").
 * @property rawValue The athlete's actual logged weight in kg.
 * @property smaValue The 7-day Simple Moving Average in kg computed over [t-6, t], or null if N < 3.
 */
data class WeightSeriesPoint(
    val date: LocalDate,
    val label: String,
    val rawValue: Double,
    val smaValue: Double?
)

/**
 * Pure Kotlin domain analytics engine for athlete body weight trends.
 *
 * Responsibilities:
 * 1. Filter weight entries by selected [Timeframe].
 * 2. Calculate index-parallel 7-day Simple Moving Average (SMA-7) over the closed calendar interval [t-6, t].
 *    Emits an SMA point if N >= 3 logged entries exist in [t-6, t], emitting null otherwise.
 * 3. Enforce paired decimation (down to <= 120 points) using identical stride indices across raw and SMA series.
 * 4. Strictly validate canonical weight ranges (20.0 kg to 350.0 kg, and imperial equivalents).
 */
object WeightAnalytics {

    const val MIN_WEIGHT_KG: Double = 20.0
    const val MAX_WEIGHT_KG: Double = 350.0

    const val LBS_PER_KG: Double = 2.20462262185

    // Derived imperial bounds: 20.0 * 2.20462262185 ~ 44.09245 lbs, 350.0 * 2.20462262185 ~ 771.6179 lbs
    const val MIN_WEIGHT_LBS: Double = MIN_WEIGHT_KG * LBS_PER_KG
    const val MAX_WEIGHT_LBS: Double = MAX_WEIGHT_KG * LBS_PER_KG

    private val chartDateFormatter: DateTimeFormatter =
        DateTimeFormatter.ofPattern("d MMM", Locale.getDefault())

    /**
     * Checks if a given weight in kg falls within the canonical physiological range [20.0 kg, 350.0 kg].
     */
    fun isValidWeightKg(weightKg: Double): Boolean {
        return weightKg in MIN_WEIGHT_KG..MAX_WEIGHT_KG
    }

    /**
     * Checks if a given weight in lbs falls within the canonical physiological range derived from kg bounds.
     */
    fun isValidWeightLbs(weightLbs: Double): Boolean {
        return weightLbs in MIN_WEIGHT_LBS..MAX_WEIGHT_LBS
    }

    /**
     * Validates input weight against the specified unit ("kg" or "lbs").
     */
    fun isValidWeight(weight: Double, unit: String = "kg"): Boolean {
        return if (unit.equals("lbs", ignoreCase = true)) {
            isValidWeightLbs(weight)
        } else {
            isValidWeightKg(weight)
        }
    }

    /**
     * Converts kilograms to pounds.
     */
    fun kgToLbs(kg: Double): Double = kg * LBS_PER_KG

    /**
     * Converts pounds to kilograms.
     */
    fun lbsToKg(lbs: Double): Double = lbs / LBS_PER_KG

    /**
     * Prepares index-parallel chart series points for the selected [timeframe].
     *
     * Pipeline:
     * 1. Filter out soft-deleted entries, sort chronologically by date ascending.
     * 2. Filter by [timeframe] window relative to the latest available entry (or today if empty/relative).
     * 3. Compute 7-day Simple Moving Average (SMA-7) across the closed calendar window [t-6, t].
     *    Emits an SMA value when N >= 3 entries exist in [t-6, t], emitting null otherwise.
     * 4. If total points exceed [maxPoints] (default 120), perform paired index decimation using
     *    a uniform stride to guarantee raw.size == sma.size post-decimation while always preserving
     *    the first and last points.
     *
     * @param entries Raw list of [WeightEntry] records.
     * @param timeframe The user-selected date filter timeframe.
     * @param referenceDate Optional anchor date for timeframe window filtering (defaults to latest entry date or today).
     * @param maxPoints Maximum number of points after decimation (default 120).
     * @return List of [WeightSeriesPoint] where raw and SMA values are structurally paired.
     */
    fun prepareChartSeries(
        entries: List<WeightEntry>,
        timeframe: Timeframe,
        referenceDate: LocalDate? = null,
        maxPoints: Int = 120
    ): List<WeightSeriesPoint> {
        val activeSorted = entries
            .filter { it.deletedAtMillis == null }
            .sortedBy { it.date }

        if (activeSorted.isEmpty()) {
            return emptyList()
        }

        // Window filtering
        val windowed = if (timeframe.days != null) {
            val anchor = referenceDate ?: activeSorted.last().date
            val cutoff = anchor.minusDays(timeframe.days - 1)
            activeSorted.filter { !it.date.isBefore(cutoff) && !it.date.isAfter(anchor) }
        } else {
            activeSorted
        }

        if (windowed.isEmpty()) {
            return emptyList()
        }

        // Full-window SMA-7 calculation over windowed points
        // For each entry t, find all entries in windowed where date in [t - 6 days, t].
        // If count >= 3, sma = average of those entries. Otherwise null.
        val fullPoints = windowed.map { entry ->
            val t = entry.date
            val windowStart = t.minusDays(6)
            val inWindow = windowed.filter { !it.date.isBefore(windowStart) && !it.date.isAfter(t) }
            val sma = if (inWindow.size >= 3) {
                inWindow.map { it.weightKg }.average()
            } else {
                null
            }
            WeightSeriesPoint(
                date = entry.date,
                label = entry.date.format(chartDateFormatter),
                rawValue = entry.weightKg,
                smaValue = sma
            )
        }

        // Synchronized paired decimation if points exceed maxPoints
        return if (fullPoints.size > maxPoints && maxPoints >= 2) {
            decimatePairedSeries(fullPoints, maxPoints)
        } else {
            fullPoints
        }
    }

    /**
     * Decimates a paired series of [WeightSeriesPoint] down to at most [maxPoints] while
     * keeping raw and SMA values perfectly synchronized at identical indices.
     * Always retains the first (oldest) and last (newest) points.
     */
    fun decimatePairedSeries(
        points: List<WeightSeriesPoint>,
        maxPoints: Int
    ): List<WeightSeriesPoint> {
        if (points.size <= maxPoints) return points
        if (maxPoints <= 1) return listOf(points.last())

        val result = ArrayList<WeightSeriesPoint>(maxPoints)
        val totalCount = points.size
        // Uniform stride step
        val step = (totalCount - 1).toDouble() / (maxPoints - 1)

        for (i in 0 until maxPoints) {
            val index = Math.round(i * step).toInt().coerceIn(0, totalCount - 1)
            if (result.isEmpty() || result.last() != points[index]) {
                result.add(points[index])
            }
        }

        // Ensure the last element is always the true newest point
        if (result.last() != points.last()) {
            if (result.size >= maxPoints) {
                result[result.size - 1] = points.last()
            } else {
                result.add(points.last())
            }
        }

        return result
    }
}
