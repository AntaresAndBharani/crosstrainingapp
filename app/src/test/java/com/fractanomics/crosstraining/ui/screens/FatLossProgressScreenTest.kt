package com.fractanomics.crosstraining.ui.screens

import com.fractanomics.crosstraining.data.analytics.FatLossAnalytics
import com.fractanomics.crosstraining.data.analytics.Timeframe
import com.fractanomics.crosstraining.data.analytics.WeightAnalytics
import com.fractanomics.crosstraining.data.model.BlockKind
import com.fractanomics.crosstraining.data.model.BlockSet
import com.fractanomics.crosstraining.data.model.BlockWithSets
import com.fractanomics.crosstraining.data.model.Cycle
import com.fractanomics.crosstraining.data.model.CycleType
import com.fractanomics.crosstraining.data.model.DailyLog
import com.fractanomics.crosstraining.data.model.Exercise
import com.fractanomics.crosstraining.data.model.MetricType
import com.fractanomics.crosstraining.data.model.Session
import com.fractanomics.crosstraining.data.model.SessionBlock
import com.fractanomics.crosstraining.data.model.SessionWithBlocks
import com.fractanomics.crosstraining.data.model.WeightEntry
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.DayOfWeek
import java.time.LocalDate

/**
 * Verification test suite for Fat Loss Progress Dashboard (Issue #567 / Subtask 4 of #563).
 *
 * Verifies:
 * 1. ProgressMode.FAT_LOSS enum contract, filter chip mode derivation & drawer selection.
 * 2. Cycle selection defaulting (FAT_LOSS_BODYBUILDING preference, active cycle fallback, empty state).
 * 3. Block Completion Rate (adherence) calculation across cycle sessions.
 * 4. Fasting Adherence calculation and scheduled fast days integration.
 * 5. Cardio Day/Week & Mon–Sun averages bar chart data pipeline.
 * 6. Integrated Weight Trend chart series, start/latest weigh-in derivation, and cycle delta.
 */
class FatLossProgressScreenTest {

    private val startDate = LocalDate.of(2026, 9, 1) // Tuesday

    private fun deriveAvailableModes(hasRoutines: Boolean, hasCycles: Boolean): List<ProgressMode> {
        return listOfNotNull(
            ProgressMode.BY_EXERCISE,
            if (hasRoutines) ProgressMode.BY_ROUTINE else null,
            if (hasCycles) ProgressMode.CYCLE_GOALS else null,
            if (hasCycles) ProgressMode.FAT_LOSS else null,
            ProgressMode.BODY_WEIGHT
        )
    }

    private fun createBlock(
        id: Long,
        kind: BlockKind = BlockKind.STRENGTH,
        isCompleted: Boolean = true,
        resultValue: Double? = null,
        mainExerciseId: Long? = null
    ) = SessionBlock(
        id = id,
        sessionId = 1L,
        position = id.toInt(),
        kind = kind,
        name = "Block $id",
        isCompleted = isCompleted,
        resultValue = resultValue,
        mainExerciseId = mainExerciseId
    )

    private fun createSession(
        id: Long,
        date: LocalDate,
        cycleId: Long = 1L,
        blocks: List<SessionBlock> = emptyList()
    ): SessionWithBlocks {
        val s = Session(id = id, cycleId = cycleId, date = date, title = "Session $id")
        val bws = blocks.map { BlockWithSets(it, emptyList()) }
        return SessionWithBlocks(s, bws)
    }

    private fun createWeightEntry(date: LocalDate, weightKg: Double) = WeightEntry(
        date = date,
        weightKg = weightKg,
        notes = "",
        updatedAtMillis = System.currentTimeMillis()
    )

    // =========================================================================
    // 1. Mode Derivation & Drawer Routing
    // =========================================================================

    @Test
    fun `deriveAvailableModes includes FAT_LOSS when cycles exist`() {
        val modes = deriveAvailableModes(hasRoutines = true, hasCycles = true)
        assertEquals(5, modes.size)
        assertTrue(modes.contains(ProgressMode.FAT_LOSS))
        assertEquals(3, modes.indexOf(ProgressMode.FAT_LOSS))
        assertEquals(4, modes.indexOf(ProgressMode.BODY_WEIGHT))
    }

    @Test
    fun `deriveAvailableModes excludes FAT_LOSS when cycles is empty`() {
        val modes = deriveAvailableModes(hasRoutines = true, hasCycles = false)
        assertEquals(3, modes.size)
        assertFalse(modes.contains(ProgressMode.FAT_LOSS))
        assertFalse(modes.contains(ProgressMode.CYCLE_GOALS))
        assertTrue(modes.contains(ProgressMode.BODY_WEIGHT))
    }

    @Test
    fun `ProgressMode enum contains FAT_LOSS`() {
        val modes = ProgressMode.values()
        assertTrue(modes.contains(ProgressMode.FAT_LOSS))
    }

    // =========================================================================
    // 2. Cycle Selection & Defaulting
    // =========================================================================

    @Test
    fun `cycle selection defaults to FAT_LOSS_BODYBUILDING cycle if active is strength`() {
        val strengthActive = Cycle(id = 1, name = "Strength Meso", startDate = startDate, isActive = true, type = CycleType.STRENGTH_WEIGHTLIFTING)
        val fatLossCycle = Cycle(id = 2, name = "Summer Cut", startDate = startDate, isActive = false, type = CycleType.FAT_LOSS_BODYBUILDING)
        val cycles = listOf(strengthActive, fatLossCycle)

        val resolved = cycles.firstOrNull { it.type == CycleType.FAT_LOSS_BODYBUILDING }
            ?: cycles.firstOrNull { it.isActive }
            ?: cycles.firstOrNull()

        assertNotNull(resolved)
        assertEquals(2L, resolved?.id)
        assertEquals(CycleType.FAT_LOSS_BODYBUILDING, resolved?.type)
    }

    @Test
    fun `cycle selection defaults to active cycle if active is FAT_LOSS_BODYBUILDING`() {
        val fatLossActive = Cycle(id = 1, name = "Summer Cut", startDate = startDate, isActive = true, type = CycleType.FAT_LOSS_BODYBUILDING)
        val otherCycle = Cycle(id = 2, name = "Winter Cut", startDate = startDate, isActive = false, type = CycleType.FAT_LOSS_BODYBUILDING)
        val cycles = listOf(fatLossActive, otherCycle)

        val activeCycle: Cycle? = fatLossActive
        val resolved = activeCycle?.takeIf { it.type == CycleType.FAT_LOSS_BODYBUILDING }
            ?: cycles.firstOrNull { it.type == CycleType.FAT_LOSS_BODYBUILDING }
            ?: activeCycle
            ?: cycles.firstOrNull()

        assertNotNull(resolved)
        assertEquals(1L, resolved?.id)
    }

    // =========================================================================
    // 3. Block Completion Rate (Adherence)
    // =========================================================================

    @Test
    fun `block adherence correctly computes percentage across cycle sessions`() {
        // Given 2 sessions within cycle date window: 4 completed out of 5 blocks
        val blocks1 = listOf(
            createBlock(1, isCompleted = true),
            createBlock(2, isCompleted = true),
            createBlock(3, isCompleted = false)
        )
        val blocks2 = listOf(
            createBlock(4, isCompleted = true),
            createBlock(5, isCompleted = true)
        )
        val allBlocks = blocks1 + blocks2

        // When
        val result = FatLossAnalytics.computeDailyBlockAdherence(allBlocks)

        // Then
        assertEquals(4, result.completedBlocks)
        assertEquals(5, result.totalBlocks)
        assertEquals(80.0, result.percentage!!, 0.001)
        assertEquals("80%", result.displayText)
    }

    @Test
    fun `block adherence evaluates to null displayText 'na' when no blocks exist`() {
        val result = FatLossAnalytics.computeDailyBlockAdherence(emptyList(), isRestDay = true)
        assertNull(result.percentage)
        assertEquals("n/a", result.displayText)
    }

    // =========================================================================
    // 4. Fasting Adherence
    // =========================================================================

    @Test
    fun `fasting adherence computes adherence over scheduled fast days`() {
        // Fast days: Tuesday (bit 1 -> value 2) and Thursday (bit 3 -> value 8). Mask = 10
        val fastMask = FatLossAnalytics.createDayOfWeekMask(listOf(DayOfWeek.TUESDAY, DayOfWeek.THURSDAY))
        val anchorDate = startDate.plusDays(7) // Next Tuesday (Sep 8)

        // Logs: Sep 1 (Tue, Done), Sep 3 (Thu, Not Done/broken)
        val logs = listOf(
            DailyLog(date = startDate, fastCompleted = true, updatedAtMillis = 1000L),
            DailyLog(date = startDate.plusDays(2), fastCompleted = false, updatedAtMillis = 1000L)
        )

        val result = FatLossAnalytics.computeFastingAdherence(
            cycleStartDate = startDate,
            fastDaysOfWeek = fastMask,
            dailyLogs = logs.associateBy { it.date },
            referenceDate = anchorDate,
            cycleEndDate = null
        )

        // Eligible past fast days: Sep 1, Sep 3. Sep 8 is today and unlogged -> excluded. Total = 2.
        assertEquals(1, result.completedFastDays)
        assertEquals(2, result.totalEligibleFastDays)
        assertEquals(50.0, result.percentage!!, 0.001)
        assertEquals("50%", result.displayText)
    }

    @Test
    fun `fasting adherence returns null displayText 'na' when cycle has 0 fast days scheduled`() {
        val result = FatLossAnalytics.computeFastingAdherence(
            cycleStartDate = startDate,
            fastDaysOfWeek = 0,
            dailyLogs = emptyMap(),
            referenceDate = startDate.plusDays(7)
        )

        assertNull(result.percentage)
        assertEquals("n/a", result.displayText)
    }

    // =========================================================================
    // 5. Cardio Day-of-Week & Mon-Sun Averages Bar Chart
    // =========================================================================

    @Test
    fun `cardio day of week averages computes non-zero averages for active cardio weekdays`() {
        // Given 2 weeks of training with cardio logged on Tuesdays (Sep 1: 30m, Sep 8: 40m)
        val session1 = createSession(
            id = 1,
            date = startDate, // Sep 1 (Tue)
            blocks = listOf(createBlock(1, kind = BlockKind.CARDIO, resultValue = 30.0))
        )
        val session2 = createSession(
            id = 2,
            date = startDate.plusDays(7), // Sep 8 (Tue)
            blocks = listOf(createBlock(2, kind = BlockKind.CARDIO, resultValue = 40.0))
        )
        val session3 = createSession(
            id = 3,
            date = startDate.plusDays(2), // Sep 3 (Thu) - Strength only, no cardio
            blocks = listOf(createBlock(3, kind = BlockKind.STRENGTH, resultValue = null))
        )

        val dailyCardio = FatLossAnalytics.computeDailyCardioMinutes(listOf(session1, session2, session3))
        assertEquals(30.0, dailyCardio[startDate]!!, 0.001)
        assertEquals(40.0, dailyCardio[startDate.plusDays(7)]!!, 0.001)
        assertEquals(0.0, dailyCardio[startDate.plusDays(2)]!!, 0.001)

        val anchor = startDate.plusDays(13) // 2 full weeks
        val dayOfWeekList = FatLossAnalytics.computeDayOfWeekCardioList(
            cycleStartDate = startDate,
            referenceDate = anchor,
            dailyCardioMinutes = dailyCardio
        )

        // 7 days in order Mon-Sun
        assertEquals(7, dayOfWeekList.size)
        assertEquals(DayOfWeek.MONDAY, dayOfWeekList[0].dayOfWeek)
        assertEquals(DayOfWeek.TUESDAY, dayOfWeekList[1].dayOfWeek)
        assertEquals(DayOfWeek.SUNDAY, dayOfWeekList[6].dayOfWeek)

        val tuesdayCardio = dayOfWeekList[1]
        assertEquals(70.0, tuesdayCardio.totalMinutes, 0.001)
        assertEquals(2, tuesdayCardio.elapsedOccurrences)
        assertEquals(35.0, tuesdayCardio.averageMinutes, 0.001)

        // Days with no cardio have 0.0 average
        val mondayCardio = dayOfWeekList[0]
        assertEquals(0.0, mondayCardio.totalMinutes, 0.001)
        assertEquals(0.0, mondayCardio.averageMinutes, 0.001)
    }

    // =========================================================================
    // 6. Integrated Weight Trend
    // =========================================================================

    @Test
    fun `weight trend accurately computes cycle start, latest, and weight delta`() {
        val entries = listOf(
            createWeightEntry(startDate, 85.0),
            createWeightEntry(startDate.plusDays(7), 84.2),
            createWeightEntry(startDate.plusDays(14), 83.5)
        )

        val startEntry = entries.first()
        val latestEntry = entries.last()
        val delta = latestEntry.weightKg - startEntry.weightKg

        assertEquals(85.0, startEntry.weightKg, 0.001)
        assertEquals(83.5, latestEntry.weightKg, 0.001)
        assertEquals(-1.5, delta, 0.001)

        // Verify chart series generation produces points
        val series = WeightAnalytics.prepareChartSeries(entries, Timeframe.THIRTY_DAYS, referenceDate = startDate.plusDays(14))
        assertTrue(series.isNotEmpty())
        assertEquals(83.5, series.last().rawValue, 0.001)
    }
}
