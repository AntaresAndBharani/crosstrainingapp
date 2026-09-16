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

    // =========================================================================
    // Issue #558 / #559: Historical Lookback SMA-7 Domain Engine Acceptance Tests
    // =========================================================================

    @Test
    fun `historical lookback scenario 1 - pre-period historical data populates moving average on day 1 of 7D timeframe`() {
        // Given an athlete has logged daily weight entries from Sep 1 to Sep 14
        val entries = (0 until 14).map { day ->
            createEntry(baseDate.plusDays(day.toLong()), 80.0 + day * 0.5)
        }

        // When the athlete views the "7D" timeframe anchored on Sep 14 (cutoff Sep 8)
        val anchor = baseDate.plusDays(13) // Sep 14
        val series = WeightAnalytics.prepareChartSeries(
            entries = entries,
            timeframe = Timeframe.SEVEN_DAYS,
            referenceDate = anchor
        )

        // Then the chart series contains 7 daily points from Sep 8 to Sep 14
        assertEquals(7, series.size)
        val cutoff = baseDate.plusDays(7) // Sep 8
        assertEquals(cutoff, series.first().date)
        assertEquals(anchor, series.last().date)

        // And every single point including Sep 8 and Sep 9 has a non-null 7D moving average computed from [date - 6 days, date]
        series.forEach { point ->
            assertNotNull("Point on ${point.date} must have non-null SMA-7", point.smaValue)
        }

        // And the 7D average line on Sep 8 is computed using entries from Sep 2 to Sep 8.
        // Entries Sep 2 to Sep 8: days 1..7 -> weights: 80.5, 81.0, 81.5, 82.0, 82.5, 83.0, 83.5
        val expectedSep8Sma = (1..7).map { 80.0 + it * 0.5 }.average()
        assertEquals(expectedSep8Sma, series.first().smaValue!!, 0.001)
    }

    @Test
    fun `historical lookback scenario 2 - leading-edge cold start emits null when prior history does not exist`() {
        // Given an athlete who started logging on Day 1 (no prior entries exist)
        // And logs entries on Day 1, Day 2, and Day 3
        val entries = listOf(
            createEntry(baseDate, 80.0), // Day 1: Sep 1
            createEntry(baseDate.plusDays(1), 80.5), // Day 2: Sep 2
            createEntry(baseDate.plusDays(2), 81.0)  // Day 3: Sep 3
        )

        // When the athlete views the weight trend chart
        val series = WeightAnalytics.prepareChartSeries(entries, Timeframe.ALL)

        // Then Day 1 has smaValue = null (N=1 < 3)
        assertEquals(3, series.size)
        assertNull("Day 1 has N=1 < 3, smaValue must be null", series[0].smaValue)

        // And Day 2 has smaValue = null (N=2 < 3)
        assertNull("Day 2 has N=2 < 3, smaValue must be null", series[1].smaValue)

        // And Day 3 has a valid non-null smaValue if an entry exists on Day 3 (N=3 >= 3).
        assertNotNull("Day 3 has N=3 >= 3, smaValue must not be null", series[2].smaValue)
        assertEquals((80.0 + 80.5 + 81.0) / 3.0, series[2].smaValue!!, 0.001)
    }

    @Test
    fun `historical lookback scenario 3a - sparse pre-period history meeting N greater than or equal 3 threshold across boundary`() {
        // Given an athlete logged weights on Sep 4 and Sep 6 (prior to 7D cutoff Sep 8)
        // And logs a weight on Sep 8
        // baseDate = Sep 1 -> Sep 4 is baseDate + 3, Sep 6 is baseDate + 5, Sep 8 is baseDate + 7, Sep 14 is baseDate + 13
        val entries = listOf(
            createEntry(baseDate.plusDays(3), 80.0), // Sep 4
            createEntry(baseDate.plusDays(5), 81.0), // Sep 6
            createEntry(baseDate.plusDays(7), 82.0)  // Sep 8
        )

        // When the 7D chart series is generated anchored on Sep 14 (cutoff Sep 8)
        val anchor = baseDate.plusDays(13) // Sep 14
        val series = WeightAnalytics.prepareChartSeries(
            entries = entries,
            timeframe = Timeframe.SEVEN_DAYS,
            referenceDate = anchor
        )

        // Then entries within [Sep 2, Sep 8] include Sep 4, Sep 6, Sep 8 (N=3)
        // Within display window [Sep 8, Sep 14], only Sep 8 has an entry logged
        assertEquals(1, series.size)
        assertEquals(baseDate.plusDays(7), series[0].date)

        // And Sep 8 emits a valid 7D average equal to (weight[Sep 4] + weight[Sep 6] + weight[Sep 8]) / 3.
        assertNotNull("Sep 8 has N=3 across boundary, smaValue must not be null", series[0].smaValue)
        val expectedAvg = (80.0 + 81.0 + 82.0) / 3.0
        assertEquals(expectedAvg, series[0].smaValue!!, 0.001)
    }

    @Test
    fun `historical lookback scenario 3b - sparse pre-period history below N less than 3 threshold across boundary`() {
        // Given an athlete logged only one weight on Sep 6 prior to the 7D cutoff Sep 8
        // And logs a weight on Sep 8
        val entries = listOf(
            createEntry(baseDate.plusDays(5), 81.0), // Sep 6
            createEntry(baseDate.plusDays(7), 82.0)  // Sep 8
        )

        // When the 7D chart series is generated anchored on Sep 14 (cutoff Sep 8)
        val anchor = baseDate.plusDays(13) // Sep 14
        val series = WeightAnalytics.prepareChartSeries(
            entries = entries,
            timeframe = Timeframe.SEVEN_DAYS,
            referenceDate = anchor
        )

        // Then entries within [Sep 2, Sep 8] include only Sep 6 and Sep 8 (N=2 < 3)
        // Within display window [Sep 8, Sep 14], only Sep 8 has an entry logged
        assertEquals(1, series.size)
        assertEquals(baseDate.plusDays(7), series[0].date)

        // And Sep 8 emits smaValue = null.
        assertNull("Sep 8 has N=2 < 3, smaValue must be null", series[0].smaValue)
    }

    @Test
    fun `historical lookback scenario 4 - soft-deleted entries in lookback window are excluded`() {
        // Given historical entries preceding the timeframe boundary where one entry has deletedAtMillis set
        // Entries on Sep 4 (soft-deleted), Sep 6 (active), Sep 7 (active), Sep 8 (active)
        val entries = listOf(
            createEntry(baseDate.plusDays(3), 80.0, deletedAt = 12345678L), // Sep 4 (deleted)
            createEntry(baseDate.plusDays(5), 81.0),                         // Sep 6 (active)
            createEntry(baseDate.plusDays(6), 82.0),                         // Sep 7 (active)
            createEntry(baseDate.plusDays(7), 83.0)                          // Sep 8 (active)
        )

        // When computing the moving average for dates in the active timeframe (anchored on Sep 14, cutoff Sep 8)
        val anchor = baseDate.plusDays(13) // Sep 14
        val series = WeightAnalytics.prepareChartSeries(
            entries = entries,
            timeframe = Timeframe.SEVEN_DAYS,
            referenceDate = anchor
        )

        // Then soft-deleted entries are excluded from both count and sum calculations.
        // For Sep 8, window [Sep 2, Sep 8] has active entries: Sep 6, Sep 7, Sep 8 (N=3).
        // Sep 4 is soft-deleted so count is 3 (not 4) and sum does not include 80.0.
        assertEquals(1, series.size)
        assertEquals(baseDate.plusDays(7), series[0].date)
        assertNotNull(series[0].smaValue)
        val expectedAvg = (81.0 + 82.0 + 83.0) / 3.0
        assertEquals(expectedAvg, series[0].smaValue!!, 0.001)
    }

    @Test
    fun `historical lookback scenario 5 - Timeframe ALL invariant and custom past reference date lookback`() {
        // Given an athlete has 30 daily entries from Sep 1 to Sep 30
        val entries = (0 until 30).map { day ->
            createEntry(baseDate.plusDays(day.toLong()), 75.0 + day * 0.2)
        }

        // When the athlete selects Timeframe.ALL
        val seriesAll = WeightAnalytics.prepareChartSeries(entries, Timeframe.ALL)

        // Then 30 points are generated matching activeSorted, with SMA computed identically across full history
        assertEquals(30, seriesAll.size)
        for (i in 0 until 30) {
            val date = baseDate.plusDays(i.toLong())
            assertEquals(date, seriesAll[i].date)
            val windowStart = date.minusDays(6)
            val windowEntries = entries.filter { !it.date.isBefore(windowStart) && !it.date.isAfter(date) }
            val expectedSma = if (windowEntries.size >= 3) windowEntries.map { it.weightKg }.average() else null
            if (expectedSma == null) {
                assertNull("SMA for $date should be null", seriesAll[i].smaValue)
            } else {
                assertNotNull("SMA for $date should not be null", seriesAll[i].smaValue)
                assertEquals(expectedSma, seriesAll[i].smaValue!!, 0.001)
            }
        }

        // And when the athlete selects Timeframe.SEVEN_DAYS with custom referenceDate Sep 15
        val refDate = baseDate.plusDays(14) // Sep 15
        val series7D = WeightAnalytics.prepareChartSeries(
            entries = entries,
            timeframe = Timeframe.SEVEN_DAYS,
            referenceDate = refDate
        )

        // Then the output series spans strictly Sep 9 to Sep 15 (7 points)
        assertEquals(7, series7D.size)
        val expectedStart = refDate.minusDays(6) // Sep 9
        assertEquals(expectedStart, series7D.first().date)
        assertEquals(refDate, series7D.last().date)

        // And Sep 9 computes its 7D average factoring in entries back to Sep 3.
        // Entries Sep 3 to Sep 9: days 2..8 -> 7 entries, all present in entries
        val sep9WindowEntries = entries.filter { !it.date.isBefore(expectedStart.minusDays(6)) && !it.date.isAfter(expectedStart) }
        assertEquals(7, sep9WindowEntries.size)
        val expectedSep9Sma = sep9WindowEntries.map { it.weightKg }.average()
        assertNotNull(series7D.first().smaValue)
        assertEquals(expectedSep9Sma, series7D.first().smaValue!!, 0.001)
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
