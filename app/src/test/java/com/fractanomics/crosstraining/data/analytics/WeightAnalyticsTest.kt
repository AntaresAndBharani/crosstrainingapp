package com.fractanomics.crosstraining.data.analytics

import com.fractanomics.crosstraining.data.model.WeightEntry
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate

/**
 * Unit test suite for [WeightAnalytics].
 *
 * Covers Acceptance Criteria from Issue #504:
 * - Scenario: Gap-Aware Index-Parallel SMA-7 Calculation
 * - Scenario: Synchronized Timeframe Decimation
 * - Scenario: Canonical Weight Range Validation
 * - Leading-edge warm-up (N < 3 emits null, N >= 3 emits average)
 * - Sparse logging across calendar gaps
 * - Post-decimation raw.size == sma.size invariant
 */
class WeightAnalyticsTest {

    private val baseDate = LocalDate.of(2026, 9, 1)

    // =========================================================================
    // Scenario 1: Gap-Aware Index-Parallel SMA-7 Calculation
    // =========================================================================

    @Test
    fun `scenario 1 - sparse logging produces index-parallel points with null where N less than 3 in closed 7-day interval`() {
        // Given weight entries with missing calendar days:
        // Day 1 (Sep 1): 80.0 kg -> only 1 entry in [Sep 1 - 6 days, Sep 1] (N=1) => sma = null
        // Day 2 (Sep 2): 80.5 kg -> 2 entries in [Sep 2 - 6 days, Sep 2] (N=2) => sma = null
        // Day 5 (Sep 5): 81.0 kg -> 3 entries in [Sep 5 - 6 days, Sep 5] (N=3: Sep 1, 2, 5) => sma = (80.0+80.5+81.0)/3 = 80.5
        // Day 6 (Sep 6): 81.5 kg -> 4 entries in [Sep 6 - 6 days, Sep 6] (N=4: Sep 1, 2, 5, 6) => sma = (80.0+80.5+81.0+81.5)/4 = 80.75
        // Day 15 (Sep 15): 82.0 kg -> previous entries are outside [Sep 9, Sep 15] (N=1) => sma = null
        val entries = listOf(
            createEntry(baseDate, 80.0),
            createEntry(baseDate.plusDays(1), 80.5),
            createEntry(baseDate.plusDays(4), 81.0),
            createEntry(baseDate.plusDays(5), 81.5),
            createEntry(baseDate.plusDays(14), 82.0)
        )

        // When
        val series = WeightAnalytics.prepareChartSeries(entries, Timeframe.ALL)

        // Then
        assertEquals(5, series.size)
        // raw.size == sma.size holds inherently by data structure WeightSeriesPoint
        assertEquals(80.0, series[0].rawValue, 0.001)
        assertNull("Day 1 has N=1 in 7d window, must be null", series[0].smaValue)

        assertEquals(80.5, series[1].rawValue, 0.001)
        assertNull("Day 2 has N=2 in 7d window, must be null", series[1].smaValue)

        assertEquals(81.0, series[2].rawValue, 0.001)
        assertNotNull("Day 5 has N=3 in 7d window, must emit SMA", series[2].smaValue)
        assertEquals(80.5, series[2].smaValue!!, 0.001)

        assertEquals(81.5, series[3].rawValue, 0.001)
        assertNotNull("Day 6 has N=4 in 7d window, must emit SMA", series[3].smaValue)
        assertEquals(80.75, series[3].smaValue!!, 0.001)

        assertEquals(82.0, series[4].rawValue, 0.001)
        assertNull("Day 15 has N=1 in [Sep 9, Sep 15], must be null", series[4].smaValue)
    }

    @Test
    fun `scenario 1 - closed calendar interval t minus 6 to t bounds SMA computation`() {
        // Entry on Day 1 (Sep 1) and Day 7 (Sep 7).
        // On Day 7, window is [Sep 1, Sep 7] -> 7 calendar days inclusive.
        // With an entry on Day 3 (Sep 3), count in [Sep 1, Sep 7] is 3 -> Day 7 emits average.
        // On Day 8 (Sep 8), window is [Sep 2, Sep 8], excluding Day 1.
        val entries = listOf(
            createEntry(baseDate, 80.0), // Sep 1
            createEntry(baseDate.plusDays(2), 81.0), // Sep 3
            createEntry(baseDate.plusDays(6), 82.0), // Sep 7
            createEntry(baseDate.plusDays(7), 83.0)  // Sep 8
        )

        val series = WeightAnalytics.prepareChartSeries(entries, Timeframe.ALL)
        assertEquals(4, series.size)

        // Sep 7 window: [Sep 1, Sep 7] contains Sep 1, Sep 3, Sep 7 (3 entries)
        assertNotNull(series[2].smaValue)
        assertEquals((80.0 + 81.0 + 82.0) / 3.0, series[2].smaValue!!, 0.001)

        // Sep 8 window: [Sep 2, Sep 8] contains Sep 3, Sep 7, Sep 8 (3 entries)
        assertNotNull(series[3].smaValue)
        assertEquals((81.0 + 82.0 + 83.0) / 3.0, series[3].smaValue!!, 0.001)
    }

    @Test
    fun `scenario 1 - soft-deleted tombstone entries are ignored in SMA computation`() {
        val entries = listOf(
            createEntry(baseDate, 80.0),
            createEntry(baseDate.plusDays(1), 80.5, deletedAt = 123456L), // Soft deleted
            createEntry(baseDate.plusDays(2), 81.0),
            createEntry(baseDate.plusDays(3), 81.5)
        )

        val series = WeightAnalytics.prepareChartSeries(entries, Timeframe.ALL)
        // Soft-deleted entry is excluded: 3 active entries remain
        assertEquals(3, series.size)
        // Entries on Sep 1, Sep 3, Sep 4: on Sep 4 count is 3 (Sep 1, 3, 4)
        assertEquals(baseDate, series[0].date)
        assertEquals(baseDate.plusDays(2), series[1].date)
        assertEquals(baseDate.plusDays(3), series[2].date)

        assertNull(series[0].smaValue)
        assertNull(series[1].smaValue)
        assertNotNull(series[2].smaValue)
        assertEquals((80.0 + 81.0 + 81.5) / 3.0, series[2].smaValue!!, 0.001)
    }

    // =========================================================================
    // Scenario 2: Synchronized Timeframe Decimation
    // =========================================================================

    @Test
    fun `scenario 2 - decimation caps output to 120 points while preserving index parity and boundary points`() {
        // Given 200 consecutive daily weight entries (spanning > 120 points)
        val entries = (0 until 200).map { day ->
            createEntry(baseDate.plusDays(day.toLong()), 75.0 + (day % 10) * 0.2)
        }

        // When
        val series = WeightAnalytics.prepareChartSeries(entries, Timeframe.ALL, maxPoints = 120)

        // Then
        assertEquals(120, series.size)
        // Oldest and newest points preserved
        assertEquals(entries.first().date, series.first().date)
        assertEquals(entries.last().date, series.last().date)
        assertEquals(entries.first().weightKg, series.first().rawValue, 0.001)
        assertEquals(entries.last().weightKg, series.last().rawValue, 0.001)

        // Verify index-parallel invariant holds: every point has a rawValue and matching smaValue
        series.forEach { point ->
            assertTrue(point.rawValue > 0.0)
            // Beyond the first few days, SMA is populated
            if (point.date >= baseDate.plusDays(2)) {
                assertNotNull("SMA should be computed for date ${point.date}", point.smaValue)
            }
        }
    }

    @Test
    fun `scenario 2 - series with fewer than maxPoints is not decimated`() {
        val entries = (0 until 50).map { day ->
            createEntry(baseDate.plusDays(day.toLong()), 75.0)
        }

        val series = WeightAnalytics.prepareChartSeries(entries, Timeframe.ALL, maxPoints = 120)
        assertEquals(50, series.size)
    }

    @Test
    fun `scenario 2 - timeframe window filtering respects 7D 30D 90D bounds`() {
        val reference = baseDate.plusDays(100)
        val entries = (0..100).map { day ->
            createEntry(baseDate.plusDays(day.toLong()), 80.0)
        }

        val series7D = WeightAnalytics.prepareChartSeries(entries, Timeframe.SEVEN_DAYS, referenceDate = reference)
        assertEquals(7, series7D.size)
        assertEquals(reference.minusDays(6), series7D.first().date)
        assertEquals(reference, series7D.last().date)

        val series30D = WeightAnalytics.prepareChartSeries(entries, Timeframe.THIRTY_DAYS, referenceDate = reference)
        assertEquals(30, series30D.size)
        assertEquals(reference.minusDays(29), series30D.first().date)
        assertEquals(reference, series30D.last().date)

        val series90D = WeightAnalytics.prepareChartSeries(entries, Timeframe.NINETY_DAYS, referenceDate = reference)
        assertEquals(90, series90D.size)
        assertEquals(reference.minusDays(89), series90D.first().date)
        assertEquals(reference, series90D.last().date)
    }

    // =========================================================================
    // Scenario 4: Canonical Weight Range Validation
    // =========================================================================

    @Test
    fun `scenario 4 - canonical metric range 20 to 350 kg is strictly enforced`() {
        assertTrue(WeightAnalytics.isValidWeightKg(20.0))
        assertTrue(WeightAnalytics.isValidWeightKg(75.5))
        assertTrue(WeightAnalytics.isValidWeightKg(350.0))

        assertFalse(WeightAnalytics.isValidWeightKg(19.99))
        assertFalse(WeightAnalytics.isValidWeightKg(0.0))
        assertFalse(WeightAnalytics.isValidWeightKg(-5.0))
        assertFalse(WeightAnalytics.isValidWeightKg(350.01))
        assertFalse(WeightAnalytics.isValidWeightKg(750.0))
    }

    @Test
    fun `scenario 4 - derived imperial range 44_1 to 771_6 lbs is strictly enforced`() {
        // 20 kg * 2.20462262185 = ~44.092 lbs
        // 350 kg * 2.20462262185 = ~771.618 lbs
        assertTrue(WeightAnalytics.isValidWeightLbs(44.1))
        assertTrue(WeightAnalytics.isValidWeightLbs(165.0))
        assertTrue(WeightAnalytics.isValidWeightLbs(771.6))

        assertFalse(WeightAnalytics.isValidWeightLbs(44.0))
        assertFalse(WeightAnalytics.isValidWeightLbs(10.0))
        assertFalse(WeightAnalytics.isValidWeightLbs(0.0))
        assertFalse(WeightAnalytics.isValidWeightLbs(772.0))
        assertFalse(WeightAnalytics.isValidWeightLbs(1200.0))
    }

    @Test
    fun `scenario 4 - unit switching validation with isValidWeight helper`() {
        assertTrue(WeightAnalytics.isValidWeight(80.0, "kg"))
        assertFalse(WeightAnalytics.isValidWeight(15.0, "kg"))

        assertTrue(WeightAnalytics.isValidWeight(175.0, "lbs"))
        assertFalse(WeightAnalytics.isValidWeight(30.0, "lbs"))
    }

    @Test
    fun `scenario 4 - unit conversions kg to lbs and lbs to kg`() {
        val kg = 100.0
        val lbs = WeightAnalytics.kgToLbs(kg)
        assertEquals(220.462, lbs, 0.01)

        val convertedKg = WeightAnalytics.lbsToKg(lbs)
        assertEquals(kg, convertedKg, 0.0001)
    }

    private fun createEntry(date: LocalDate, weightKg: Double, deletedAt: Long? = null): WeightEntry {
        return WeightEntry(
            date = date,
            weightKg = weightKg,
            notes = "",
            updatedAtMillis = 1000L,
            deletedAtMillis = deletedAt
        )
    }
}
