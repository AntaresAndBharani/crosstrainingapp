package com.fractanomics.crosstraining.ui.components

import androidx.compose.ui.graphics.Color
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * Unit test suite verifying [ChartPoint] model widening and gap-aware handling.
 *
 * Covers Issue #504 Acceptance Criteria:
 * - ChartPoint.value widened to Float? = null
 * - Backwards-compatibility of non-null construction
 * - Null-value representation for gap handling in series
 * - Safe min/max filtering via mapNotNull preventing NaN errors
 */
class LineChartGapAwareTest {

    @Test
    fun `chartPoint default value is null`() {
        val point = ChartPoint(label = "7 Sep")
        assertEquals("7 Sep", point.label)
        assertNull(point.value)
    }

    @Test
    fun `chartPoint backward compatibility with explicit non-null float`() {
        val point = ChartPoint(label = "7 Sep", value = 82.5f)
        assertEquals("7 Sep", point.label)
        assertEquals(82.5f, point.value)
    }

    @Test
    fun `mapNotNull on series points correctly skips null gap values preventing NaN errors`() {
        val series = listOf(
            ChartSeries(
                name = "Raw",
                points = listOf(
                    ChartPoint("1 Sep", 80f),
                    ChartPoint("2 Sep", 81f),
                    ChartPoint("3 Sep", 82f)
                ),
                color = Color.Blue
            ),
            ChartSeries(
                name = "SMA-7",
                points = listOf(
                    ChartPoint("1 Sep", null), // Warmup null
                    ChartPoint("2 Sep", null), // Warmup null
                    ChartPoint("3 Sep", 81f)   // Valid computed SMA
                ),
                color = Color.Red
            )
        )

        val values = series.flatMap { s -> s.points.mapNotNull { it.value } }

        // All null values filtered out cleanly
        assertEquals(listOf(80f, 81f, 82f, 81f), values)
        val minV = values.min()
        val maxV = values.max()
        assertEquals(80f, minV)
        assertEquals(82f, maxV)
        val range = maxV - minV
        assertEquals(2f, range)
    }

    @Test
    fun `series with only null values produces empty values list without throwing or creating NaN`() {
        val series = listOf(
            ChartSeries(
                name = "Empty SMA",
                points = listOf(
                    ChartPoint("1 Sep", null),
                    ChartPoint("2 Sep", null)
                ),
                color = Color.Red
            )
        )

        val values = series.flatMap { s -> s.points.mapNotNull { it.value } }
        assertEquals(0, values.size)
    }
}
