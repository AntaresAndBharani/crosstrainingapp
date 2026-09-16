package com.fractanomics.crosstraining.ui.screens

import com.fractanomics.crosstraining.data.analytics.Timeframe
import com.fractanomics.crosstraining.data.analytics.WeightAnalytics
import com.fractanomics.crosstraining.data.model.WeightEntry
import com.fractanomics.crosstraining.ui.components.ChartPoint
import com.fractanomics.crosstraining.ui.components.ChartSeries
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate

/**
 * Verification test suite for ProgressScreen weight trend chart rendering (Issue #560 / Subtask 2 of #558).
 *
 * Verifies that the chart pipeline feeding MultiLineChart on ProgressScreen across 7D, 30D, and 90D views
 * accurately models:
 * 1. Continuous 7D average line without leading-edge gaps when historical weigh-ins exist.
 * 2. Gap-aware polyline continuity invariants where null SMA points emit null and valid averages emit Float.
 * 3. Unit-switching synchronization between raw values and 7D average values.
 * 4. Realistic 30-day athlete data (matching DemoData / active weigh-in telemetry).
 */
class ProgressScreenTrendVerificationTest {

    private val baseDate = LocalDate.of(2026, 9, 1)

    /**
     * Simulates the exact chart points derivation pipeline executed inside ProgressScreen.kt (lines 367-380):
     * ```kotlin
     * val isImperial = unit.equals("lbs", ignoreCase = true)
     * val chartPoints = remember(activeEntries, selectedTimeframe, isImperial) {
     *     val seriesPoints = WeightAnalytics.prepareChartSeries(activeEntries, selectedTimeframe)
     *     val rawChart = seriesPoints.map { p ->
     *         val displayVal = if (isImperial) WeightAnalytics.kgToLbs(p.rawValue) else p.rawValue
     *         ChartPoint(p.label, displayVal.toFloat())
     *     }
     *     val smaChart = seriesPoints.map { p ->
     *         val displayVal = p.smaValue?.let {
     *             if (isImperial) WeightAnalytics.kgToLbs(it) else it
     *         }
     *         ChartPoint(p.label, displayVal?.toFloat())
     *     }
     *     Pair(rawChart, smaChart)
     * }
     * ```
     */
    private fun deriveProgressScreenChartSeries(
        activeEntries: List<WeightEntry>,
        selectedTimeframe: Timeframe,
        unit: String = "kg",
        referenceDate: LocalDate? = null
    ): Pair<List<ChartPoint>, List<ChartPoint>> {
        val isImperial = unit.equals("lbs", ignoreCase = true)
        val seriesPoints = WeightAnalytics.prepareChartSeries(activeEntries, selectedTimeframe, referenceDate = referenceDate)
        val rawChart = seriesPoints.map { p ->
            val displayVal = if (isImperial) WeightAnalytics.kgToLbs(p.rawValue) else p.rawValue
            ChartPoint(p.label, displayVal.toFloat())
        }
        val smaChart = seriesPoints.map { p ->
            val displayVal = p.smaValue?.let {
                if (isImperial) WeightAnalytics.kgToLbs(it) else it
            }
            ChartPoint(p.label, displayVal?.toFloat())
        }
        return Pair(rawChart, smaChart)
    }

    private fun createEntry(date: LocalDate, weightKg: Double, deleted: Boolean = false): WeightEntry {
        return WeightEntry(
            date = date,
            weightKg = weightKg,
            notes = "",
            updatedAtMillis = System.currentTimeMillis(),
            deletedAtMillis = if (deleted) System.currentTimeMillis() else null
        )
    }

    @Test
    fun `verify 7D view renders continuous 7D average line across all 7 display days without leading-edge gaps`() {
        // Given an athlete has logged daily weight entries from Sep 1 to Sep 14 (14 consecutive days)
        val entries = (0 until 14).map { day ->
            createEntry(baseDate.plusDays(day.toLong()), 80.0 + (day * 0.1))
        }
        val anchor = baseDate.plusDays(13) // Sep 14

        // When viewing the 7D timeframe
        val (rawPoints, smaPoints) = deriveProgressScreenChartSeries(
            activeEntries = entries,
            selectedTimeframe = Timeframe.SEVEN_DAYS,
            unit = "kg",
            referenceDate = anchor
        )

        // Then both raw and SMA series contain exactly 7 points (Sep 8 to Sep 14)
        assertEquals(7, rawPoints.size)
        assertEquals(7, smaPoints.size)

        // And every single point in the 7D average line has a non-null float value
        // guaranteeing a continuous unbroken line from index 0 to index 6
        for (i in 0 until 7) {
            val smaPoint = smaPoints[i]
            assertNotNull("Day index $i (${smaPoint.label}) must have non-null SMA value", smaPoint.value)
            assertTrue("Day index $i must be greater than zero", smaPoint.value!! > 0f)
        }

        // Leading edge (Day 0, Sep 8) incorporates history back to Sep 2 (7 days: Sep 2..Sep 8)
        // Values: 80.1, 80.2, 80.3, 80.4, 80.5, 80.6, 80.7 -> sum = 562.8 / 7 = 80.4
        assertEquals(80.4f, smaPoints[0].value!!, 0.01f)
    }

    @Test
    fun `verify 30D view renders continuous 7D average line across all 30 display days without leading-edge gaps`() {
        // Given an athlete has 36 consecutive days of weigh-ins (Sep 1 to Oct 6)
        // providing at least 6 days of pre-period data prior to the 30-day window (Sep 7 to Oct 6)
        val entries = (0 until 36).map { day ->
            createEntry(baseDate.plusDays(day.toLong()), 78.0 + (day % 3) * 0.2)
        }
        val anchor = baseDate.plusDays(35) // Oct 6 (36th day)

        // When viewing the 30D timeframe
        val (rawPoints, smaPoints) = deriveProgressScreenChartSeries(
            activeEntries = entries,
            selectedTimeframe = Timeframe.THIRTY_DAYS,
            unit = "kg",
            referenceDate = anchor
        )

        // Then both series have 30 points
        assertEquals(30, rawPoints.size)
        assertEquals(30, smaPoints.size)

        // And every point on the 30D view, including the very first day (index 0), has a non-null 7D moving average
        smaPoints.forEachIndexed { idx, point ->
            assertNotNull("30D point at index $idx (${point.label}) must not have leading-edge null gap", point.value)
        }
    }

    @Test
    fun `verify 90D view renders continuous 7D average line across all 90 display days without leading-edge gaps`() {
        // Given an athlete with 100 consecutive days of weigh-ins
        // providing 10 days of pre-period data prior to the 90-day window
        val entries = (0 until 100).map { day ->
            createEntry(baseDate.plusDays(day.toLong()), 75.0 + (day % 5) * 0.1)
        }
        val anchor = baseDate.plusDays(99) // 100th day

        // When viewing the 90D timeframe
        val (rawPoints, smaPoints) = deriveProgressScreenChartSeries(
            activeEntries = entries,
            selectedTimeframe = Timeframe.NINETY_DAYS,
            unit = "kg",
            referenceDate = anchor
        )

        // Then both series have 90 points
        assertEquals(90, rawPoints.size)
        assertEquals(90, smaPoints.size)

        // And every single point has a valid non-null SMA-7 average
        smaPoints.forEachIndexed { idx, point ->
            assertNotNull("90D point at index $idx (${point.label}) must have valid average", point.value)
        }
    }

    @Test
    fun `verify cold start without prior history emits initial nulls and transitions to continuous line once N reaches 3`() {
        // Given an athlete starts logging with no pre-period history (Day 1, Day 2, Day 3, Day 4, Day 5, Day 6, Day 7)
        val entries = (0 until 7).map { day ->
            createEntry(baseDate.plusDays(day.toLong()), 82.0)
        }
        val anchor = baseDate.plusDays(6)

        val (rawPoints, smaPoints) = deriveProgressScreenChartSeries(
            activeEntries = entries,
            selectedTimeframe = Timeframe.SEVEN_DAYS,
            unit = "kg",
            referenceDate = anchor
        )

        // In true cold start:
        // Day 1 (index 0): N=1 < 3 -> null
        // Day 2 (index 1): N=2 < 3 -> null
        // Day 3..7 (index 2..6): N >= 3 -> non-null
        assertNull("Day 1 cold-start must be null", smaPoints[0].value)
        assertNull("Day 2 cold-start must be null", smaPoints[1].value)
        assertNotNull("Day 3 must render SMA line", smaPoints[2].value)
        assertNotNull("Day 4 must render SMA line", smaPoints[3].value)
        assertNotNull("Day 5 must render SMA line", smaPoints[4].value)
        assertNotNull("Day 6 must render SMA line", smaPoints[5].value)
        assertNotNull("Day 7 must render SMA line", smaPoints[6].value)
    }

    @Test
    fun `verify imperial unit conversion correctly scales both raw and 7D average chart points`() {
        val entries = (0 until 14).map { day ->
            createEntry(baseDate.plusDays(day.toLong()), 80.0)
        }
        val anchor = baseDate.plusDays(13)

        val (rawPoints, smaPoints) = deriveProgressScreenChartSeries(
            activeEntries = entries,
            selectedTimeframe = Timeframe.SEVEN_DAYS,
            unit = "lbs",
            referenceDate = anchor
        )

        // 80.0 kg * 2.20462262185 = ~176.37 lbs
        val expectedLbs = (80.0 * WeightAnalytics.LBS_PER_KG).toFloat()

        rawPoints.forEach { point ->
            assertEquals(expectedLbs, point.value!!, 0.05f)
        }
        smaPoints.forEach { point ->
            assertEquals(expectedLbs, point.value!!, 0.05f)
        }
    }
}
