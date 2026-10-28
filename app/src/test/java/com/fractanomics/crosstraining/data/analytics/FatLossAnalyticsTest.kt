package com.fractanomics.crosstraining.data.analytics

import com.fractanomics.crosstraining.data.model.BlockKind
import com.fractanomics.crosstraining.data.model.BlockSet
import com.fractanomics.crosstraining.data.model.BlockWithSets
import com.fractanomics.crosstraining.data.model.Cycle
import com.fractanomics.crosstraining.data.model.CycleType
import com.fractanomics.crosstraining.data.model.DailyLog
import com.fractanomics.crosstraining.data.model.Exercise
import com.fractanomics.crosstraining.data.model.ExerciseCategory
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
import java.util.Locale

/**
 * Unit test suite for [FatLossAnalytics].
 *
 * Verifies all BDD Acceptance Criteria from Issue #563 / #565:
 * - Scenario 1: Multiple Sessions on the Same Date Aggregate Daily Cardio & Blocks
 * - Scenario 2: Explicitly Skipped Blocks Are Not Overridden by Sets or Notes
 * - Scenario 3: Untouched Routine-Imported Block Defaults to Not Completed
 * - Scenario 4: User Literal Example Day Evaluation
 * - Scenario 5: Cardio Duration Extraction & Fallback Contract
 * - Scenario 6: Cardio Day-of-Week Average Calculation Across In-Progress Cycle
 * - Scenario 7: Fasting Day Adherence Bounded by Elapsed Days and Pending Today
 * - Scenario 8: Scheduled Rest Days & Zero-Block Days Display "n/a"
 * - Boundary conditions: Day 1 of cycle, missing weeks, zero denominators, bitmask encoding.
 */
class FatLossAnalyticsTest {

    private val baseDate = LocalDate.of(2026, 10, 1) // Thursday

    // =========================================================================
    // Scenario 1: Multiple Sessions on the Same Date Aggregate Daily Cardio & Blocks
    // =========================================================================

    @Test
    fun `scenario 1 - multiple sessions on same date aggregate daily cardio and block adherence`() {
        // Given an active Fat Loss cycle
        // And an athlete logs Session 1 on Oct 1 with 3 strength blocks (isCompleted = true)
        // and 1 cardio block of 20 minutes in resultValue (isCompleted = true)
        val session1 = Session(id = 1L, cycleId = 10L, date = baseDate, title = "Morning Push")
        val s1Blocks = listOf(
            BlockWithSets(
                block = SessionBlock(id = 101L, sessionId = 1L, position = 1, kind = BlockKind.STRENGTH, isCompleted = true),
                sets = emptyList()
            ),
            BlockWithSets(
                block = SessionBlock(id = 102L, sessionId = 1L, position = 2, kind = BlockKind.STRENGTH, isCompleted = true),
                sets = emptyList()
            ),
            BlockWithSets(
                block = SessionBlock(id = 103L, sessionId = 1L, position = 3, kind = BlockKind.STRENGTH, isCompleted = true),
                sets = emptyList()
            ),
            BlockWithSets(
                block = SessionBlock(
                    id = 104L, sessionId = 1L, position = 4, kind = BlockKind.CARDIO,
                    resultValue = 20.0, isCompleted = true
                ),
                sets = emptyList()
            )
        )
        val sessionWithBlocks1 = SessionWithBlocks(session = session1, blocks = s1Blocks)

        // And the athlete logs Session 2 on Oct 1 with 1 cardio block of 25 minutes in resultValue (isCompleted = true)
        val session2 = Session(id = 2L, cycleId = 10L, date = baseDate, title = "Evening Cardio")
        val s2Blocks = listOf(
            BlockWithSets(
                block = SessionBlock(
                    id = 201L, sessionId = 2L, position = 1, kind = BlockKind.CARDIO,
                    resultValue = 25.0, isCompleted = true
                ),
                sets = emptyList()
            )
        )
        val sessionWithBlocks2 = SessionWithBlocks(session = session2, blocks = s2Blocks)

        // When evaluating daily adherence and cardio duration
        val adherence = FatLossAnalytics.computeDailyBlockAdherenceFromSessions(
            listOf(sessionWithBlocks1, sessionWithBlocks2)
        )
        val dailyCardio = FatLossAnalytics.computeDailyCardioMinutes(
            listOf(sessionWithBlocks1, sessionWithBlocks2)
        )

        // Then daily block completion rate is 100% (5 out of 5 blocks completed)
        assertEquals(5, adherence.totalBlocks)
        assertEquals(5, adherence.completedBlocks)
        assertNotNull(adherence.percentage)
        assertEquals(100.0, adherence.percentage!!, 0.001)
        assertEquals("100%", adherence.displayText)

        // And total cardio duration for Oct 1 is 45 minutes (20m + 25m)
        assertEquals(45.0, dailyCardio[baseDate] ?: 0.0, 0.001)
    }

    // =========================================================================
    // Scenario 2: Explicitly Skipped Blocks Are Not Overridden by Sets or Notes
    // =========================================================================

    @Test
    fun `scenario 2 - explicitly skipped block with sets and notes evaluates to not completed`() {
        // Given an athlete imports a planned routine with 4 blocks into a new session
        // And Block 4 has pre-populated sets and notes from the routine template
        // When the athlete explicitly sets isCompleted = false on Block 4
        // And marks Blocks 1, 2, and 3 as isCompleted = true
        val blocks = listOf(
            SessionBlock(id = 1L, sessionId = 1L, position = 1, kind = BlockKind.STRENGTH, isCompleted = true),
            SessionBlock(id = 2L, sessionId = 1L, position = 2, kind = BlockKind.STRENGTH, isCompleted = true),
            SessionBlock(id = 3L, sessionId = 1L, position = 3, kind = BlockKind.ACCESSORY, isCompleted = true),
            SessionBlock(
                id = 4L, sessionId = 1L, position = 4, kind = BlockKind.CARDIO,
                isCompleted = false, notes = "Ran out of time", resultText = "Planned 15 min"
            )
        )

        val adherence = FatLossAnalytics.computeDailyBlockAdherence(blocks)

        // Then Block 4 has isCompleted = false
        assertFalse(blocks[3].isCompleted)
        // And daily block completion rate evaluates to 75% (3/4 blocks completed)
        assertEquals(4, adherence.totalBlocks)
        assertEquals(3, adherence.completedBlocks)
        assertNotNull(adherence.percentage)
        assertEquals(75.0, adherence.percentage!!, 0.001)
        assertEquals("75%", adherence.displayText)
    }

    // =========================================================================
    // Scenario 3: Untouched Routine-Imported Block Defaults to Not Completed
    // =========================================================================

    @Test
    fun `scenario 3 - untouched routine imported block defaults to not completed in fat loss cycle`() {
        // Given an active Fat Loss cycle
        // When an athlete imports a routine with 3 blocks and saves without checking off Block 3
        val blocks = listOf(
            SessionBlock(id = 1L, sessionId = 1L, position = 1, isCompleted = true),
            SessionBlock(id = 2L, sessionId = 1L, position = 2, isCompleted = true),
            SessionBlock(id = 3L, sessionId = 1L, position = 3, isCompleted = false)
        )

        val adherence = FatLossAnalytics.computeDailyBlockAdherence(blocks)

        // Then daily block completion rate evaluates to 66.7% (2/3 blocks completed)
        assertEquals(3, adherence.totalBlocks)
        assertEquals(2, adherence.completedBlocks)
        assertNotNull(adherence.percentage)
        assertEquals(66.6666, adherence.percentage!!, 0.01)
        assertEquals("66.7%", adherence.displayText)
    }

    // =========================================================================
    // Scenario 4: User Literal Example Day Evaluation
    // =========================================================================

    @Test
    fun `scenario 4 - user literal example day evaluation with two sessions and ate something fast day`() {
        val testDate = LocalDate.of(2026, 10, 1) // Thursday

        // Given an active Fat Loss cycle with scheduled fast days and two workouts planned for Oct 1
        // Session 1: Push Day + 20m Cardio, all blocks completed
        val s1 = SessionWithBlocks(
            session = Session(id = 1L, cycleId = 1L, date = testDate, title = "Push + Cardio"),
            blocks = listOf(
                BlockWithSets(
                    block = SessionBlock(id = 1L, sessionId = 1L, position = 1, kind = BlockKind.STRENGTH, isCompleted = true),
                    sets = emptyList()
                ),
                BlockWithSets(
                    block = SessionBlock(id = 2L, sessionId = 1L, position = 2, kind = BlockKind.CARDIO, resultValue = 20.0, isCompleted = true),
                    sets = emptyList()
                )
            )
        )

        // Session 2: 20m Cardio, block completed
        val s2 = SessionWithBlocks(
            session = Session(id = 2L, cycleId = 1L, date = testDate, title = "Evening Cardio"),
            blocks = listOf(
                BlockWithSets(
                    block = SessionBlock(id = 3L, sessionId = 2L, position = 1, kind = BlockKind.CARDIO, resultValue = 20.0, isCompleted = true),
                    sets = emptyList()
                )
            )
        )

        // And the athlete reports Fast Day as ate something (fastCompleted = false)
        val dailyLog = DailyLog(
            date = testDate,
            fastCompleted = false,
            updatedAtMillis = System.currentTimeMillis()
        )
        val dailyLogs = mapOf(testDate to dailyLog)

        // When viewing dashboard for Oct 1
        val adherence = FatLossAnalytics.computeDailyBlockAdherenceFromSessions(listOf(s1, s2))
        val dailyCardio = FatLossAnalytics.computeDailyCardioMinutes(listOf(s1, s2))
        val fastDaysMask = FatLossAnalytics.createDayOfWeekMask(listOf(DayOfWeek.THURSDAY))
        val fastingAdherence = FatLossAnalytics.computeFastingAdherence(
            cycleStartDate = testDate,
            fastDaysOfWeek = fastDaysMask,
            dailyLogs = dailyLogs,
            referenceDate = testDate
        )
        val fastingStatus = FatLossAnalytics.getDailyFastingStatus(
            date = testDate,
            fastDaysOfWeekMask = fastDaysMask,
            dailyLog = dailyLog,
            referenceDate = testDate
        )

        // Then Daily Block Completion displays 100% (3 out of 3 blocks completed)
        assertEquals(3, adherence.totalBlocks)
        assertEquals(3, adherence.completedBlocks)
        assertEquals(100.0, adherence.percentage!!, 0.001)

        // And Total Cardio displays 40 minutes (20m + 20m)
        assertEquals(40.0, dailyCardio[testDate] ?: 0.0, 0.001)

        // And Fasting Consistency marks Oct 1 as Not Done (broken)
        assertEquals(FastingDayStatus.BROKEN, fastingStatus)
        assertEquals("Not Done", fastingStatus.label)
        assertEquals(1, fastingAdherence.totalEligibleFastDays)
        assertEquals(0, fastingAdherence.completedFastDays)
        assertEquals(0.0, fastingAdherence.percentage!!, 0.001)
    }

    // =========================================================================
    // Scenario 5: Cardio Duration Extraction & Fallback Contract
    // =========================================================================

    @Test
    fun `scenario 5 - cardio duration extraction and fallback contract`() {
        // Given an athlete completes a workout with:
        // | Kind   | Name        | resultValue | Sets MetricType | Sets metricValue |
        // | CARDIO | Treadmill   | 20.0        | TIME            | 1200.0           |
        // | CARDIO | Rower       | null        | TIME            | 900.0            |
        // | CARDIO | Air Bike    | null        | CALORIES        | 150.0            |
        // | METCON | AMRAP 20    | 20.0        | REPS            | 100.0            |
        val treadmillExercise = Exercise(id = 1L, name = "Treadmill", metricType = MetricType.TIME, category = ExerciseCategory.MACHINE)
        val rowerExercise = Exercise(id = 2L, name = "Rower", metricType = MetricType.TIME, category = ExerciseCategory.MACHINE)
        val airBikeExercise = Exercise(id = 3L, name = "Air Bike", metricType = MetricType.CALORIES, category = ExerciseCategory.MACHINE)
        val metconExercise = Exercise(id = 4L, name = "AMRAP 20", metricType = MetricType.REPS, category = ExerciseCategory.OTHER)

        val exerciseMap = mapOf(
            1L to treadmillExercise,
            2L to rowerExercise,
            3L to airBikeExercise,
            4L to metconExercise
        )

        val treadmillBlock = BlockWithSets(
            block = SessionBlock(id = 1L, sessionId = 1L, position = 1, kind = BlockKind.CARDIO, mainExerciseId = 1L, resultValue = 20.0),
            sets = listOf(BlockSet(id = 1L, blockId = 1L, position = 1, reps = 1, metricValue = 1200.0))
        )
        val rowerBlock = BlockWithSets(
            block = SessionBlock(id = 2L, sessionId = 1L, position = 2, kind = BlockKind.CARDIO, mainExerciseId = 2L, resultValue = null),
            sets = listOf(BlockSet(id = 2L, blockId = 2L, position = 1, reps = 1, metricValue = 900.0))
        )
        val airBikeBlock = BlockWithSets(
            block = SessionBlock(id = 3L, sessionId = 1L, position = 3, kind = BlockKind.CARDIO, mainExerciseId = 3L, resultValue = null),
            sets = listOf(BlockSet(id = 3L, blockId = 3L, position = 1, reps = 1, metricValue = 150.0))
        )
        val metconBlock = BlockWithSets(
            block = SessionBlock(id = 4L, sessionId = 1L, position = 4, kind = BlockKind.METCON, mainExerciseId = 4L, resultValue = 20.0),
            sets = listOf(BlockSet(id = 4L, blockId = 4L, position = 1, reps = 100, metricValue = 100.0))
        )

        // When FatLossAnalytics aggregates cardio duration
        val treadmillMinutes = FatLossAnalytics.computeBlockWithSetsCardioMinutes(treadmillBlock, exerciseMap)
        val rowerMinutes = FatLossAnalytics.computeBlockWithSetsCardioMinutes(rowerBlock, exerciseMap)
        val airBikeMinutes = FatLossAnalytics.computeBlockWithSetsCardioMinutes(airBikeBlock, exerciseMap)
        val metconMinutes = FatLossAnalytics.computeBlockWithSetsCardioMinutes(metconBlock, exerciseMap)

        val totalMinutes = FatLossAnalytics.computeTotalCardioMinutes(
            listOf(treadmillBlock, rowerBlock, airBikeBlock, metconBlock),
            exerciseMap
        )

        // Then Treadmill contributes 20.0 minutes (from resultValue)
        assertEquals(20.0, treadmillMinutes, 0.001)

        // And Rower contributes 15.0 minutes (900 seconds / 60)
        assertEquals(15.0, rowerMinutes, 0.001)

        // And Air Bike contributes 0.0 minutes (non-TIME metric ignored)
        assertEquals(0.0, airBikeMinutes, 0.001)

        // And METCON AMRAP 20 contributes 0.0 minutes (non-CARDIO kind ignored)
        assertEquals(0.0, metconMinutes, 0.001)

        // And total cardio duration evaluates to 35.0 minutes
        assertEquals(35.0, totalMinutes, 0.001)
    }

    // =========================================================================
    // Scenario 6: Cardio Day-of-Week Average Calculation Across In-Progress Cycle
    // =========================================================================

    @Test
    fun `scenario 6 - cardio day-of-week average calculation across 4-week cycle and mid-week 1`() {
        // Given an athlete in a 4-week Fat Loss cycle where Monday cardio is logged:
        // Week 1 Mon (Oct 5): 30 min
        // Week 2 Mon (Oct 12): 30 min
        // Week 3 Mon (Oct 19): 0 min
        // Week 4 Mon (Oct 26): 40 min
        val w1Mon = LocalDate.of(2026, 10, 5)
        val w2Mon = LocalDate.of(2026, 10, 12)
        val w3Mon = LocalDate.of(2026, 10, 19)
        val w4Mon = LocalDate.of(2026, 10, 26)
        val cycleStart = w1Mon

        val dailyCardio = mapOf(
            w1Mon to 30.0,
            w2Mon to 30.0,
            w3Mon to 0.0,
            w4Mon to 40.0
        )

        // When viewing after Week 4 on Sunday Nov 1
        val endOfW4 = LocalDate.of(2026, 11, 1)
        val fullCycleAverages = FatLossAnalytics.computeDayOfWeekCardioAverages(
            cycleStartDate = cycleStart,
            referenceDate = endOfW4,
            dailyCardioMinutes = dailyCardio
        )

        // Then Monday average cardio displays 25.0 minutes (100 min / 4 elapsed Mondays)
        val mondayStats = fullCycleAverages.getValue(DayOfWeek.MONDAY)
        assertEquals(4, mondayStats.elapsedOccurrences)
        assertEquals(100.0, mondayStats.totalMinutes, 0.001)
        assertEquals(25.0, mondayStats.averageMinutes, 0.001)

        // And when viewing midway through Week 1 on Wednesday Oct 7 (Monday elapsed = 1, Tuesday elapsed = 1)
        val midW1Wednesday = LocalDate.of(2026, 10, 7)
        val midW1Averages = FatLossAnalytics.computeDayOfWeekCardioAverages(
            cycleStartDate = cycleStart,
            referenceDate = midW1Wednesday,
            dailyCardioMinutes = dailyCardio
        )

        // Then Monday average cardio displays 30.0 minutes (30 min / 1)
        val midMonStats = midW1Averages.getValue(DayOfWeek.MONDAY)
        assertEquals(1, midMonStats.elapsedOccurrences)
        assertEquals(30.0, midMonStats.averageMinutes, 0.001)

        // And Tuesday average cardio displays 0.0 minutes (0 min / 1)
        val midTueStats = midW1Averages.getValue(DayOfWeek.TUESDAY)
        assertEquals(1, midTueStats.elapsedOccurrences)
        assertEquals(0.0, midTueStats.averageMinutes, 0.001)

        // And Thursday (not yet reached) has elapsed = 0, denominator = max(1, 0) = 1, average = 0.0
        val midThuStats = midW1Averages.getValue(DayOfWeek.THURSDAY)
        assertEquals(0, midThuStats.elapsedOccurrences)
        assertEquals(0.0, midThuStats.averageMinutes, 0.001)
    }

    // =========================================================================
    // Scenario 7: Fasting Day Adherence Bounded by Elapsed Days and Pending Today
    // =========================================================================

    @Test
    fun `scenario 7 - fasting day adherence bounded by elapsed days and excludes future days`() {
        // Given an active Fat Loss cycle spanning 4 weeks with Tuesday and Friday scheduled as fast days (8 total in cycle)
        // Cycle starts on Monday Sep 28, 2026
        // Scheduled fast days:
        // W1 Tue (Sep 29), W1 Fri (Oct 2)
        // W2 Tue (Oct 6), W2 Fri (Oct 9)
        // W3 Tue (Oct 13), W3 Fri (Oct 16)
        // W4 Tue (Oct 20), W4 Fri (Oct 23)
        val cycleStart = LocalDate.of(2026, 9, 28)
        val w1Tue = LocalDate.of(2026, 9, 29)
        val w1Fri = LocalDate.of(2026, 10, 2)
        val w2Tue = LocalDate.of(2026, 10, 6)
        val todayWed = LocalDate.of(2026, 10, 7) // Wednesday of Week 2

        val fastDaysMask = FatLossAnalytics.createDayOfWeekMask(listOf(DayOfWeek.TUESDAY, DayOfWeek.FRIDAY))

        // When the athlete logged 2 fast days as completed (fastCompleted = true) and 1 as broken (fastCompleted = false)
        val dailyLogs = mapOf(
            w1Tue to DailyLog(date = w1Tue, fastCompleted = true, updatedAtMillis = 1L),
            w1Fri to DailyLog(date = w1Fri, fastCompleted = false, updatedAtMillis = 2L),
            w2Tue to DailyLog(date = w2Tue, fastCompleted = true, updatedAtMillis = 3L)
        )

        // And today (Wednesday) is not a scheduled fast day
        assertFalse(FatLossAnalytics.isScheduledFastDay(todayWed, fastDaysMask))

        // When computing fasting adherence on Wednesday of Week 2
        val result = FatLossAnalytics.computeFastingAdherence(
            cycleStartDate = cycleStart,
            fastDaysOfWeek = fastDaysMask,
            dailyLogs = dailyLogs,
            referenceDate = todayWed
        )

        // Then fasting adherence evaluates to 66.7% (2 / 3 elapsed fast days)
        assertEquals(3, result.totalEligibleFastDays)
        assertEquals(2, result.completedFastDays)
        assertNotNull(result.percentage)
        assertEquals(66.6666, result.percentage!!, 0.01)
        assertEquals("66.7%", result.displayText)
    }

    @Test
    fun `scenario 7 boundary - today is scheduled fast day but pending reports are excluded from denominator`() {
        val cycleStart = LocalDate.of(2026, 10, 1) // Thursday
        val fastDaysMask = FatLossAnalytics.createDayOfWeekMask(listOf(DayOfWeek.THURSDAY))

        // Today is Thursday Oct 1 (Day 1 of cycle), but athlete has not reported yet (pending)
        val resultPending = FatLossAnalytics.computeFastingAdherence(
            cycleStartDate = cycleStart,
            fastDaysOfWeek = fastDaysMask,
            dailyLogs = emptyMap(),
            referenceDate = cycleStart
        )

        // 0 denominator evaluates to "n/a" (null percentage)
        assertEquals(0, resultPending.totalEligibleFastDays)
        assertEquals(0, resultPending.completedFastDays)
        assertNull(resultPending.percentage)
        assertEquals("n/a", resultPending.displayText)

        // When athlete reports fast completed = true for today
        val reportedLogs = mapOf(cycleStart to DailyLog(date = cycleStart, fastCompleted = true, updatedAtMillis = 1L))
        val resultReported = FatLossAnalytics.computeFastingAdherence(
            cycleStartDate = cycleStart,
            fastDaysOfWeek = fastDaysMask,
            dailyLogs = reportedLogs,
            referenceDate = cycleStart
        )

        assertEquals(1, resultReported.totalEligibleFastDays)
        assertEquals(1, resultReported.completedFastDays)
        assertEquals(100.0, resultReported.percentage!!, 0.001)
        assertEquals("100%", resultReported.displayText)
    }

    // =========================================================================
    // Scenario 8: Scheduled Rest Days & Zero-Block Days Display "n/a"
    // =========================================================================

    @Test
    fun `scenario 8 - scheduled rest days and zero block days return na and are excluded from cycle average`() {
        // Given an athlete has Thursday scheduled as a Rest Day in the active cycle
        // When the athlete views the dashboard for Thursday with 0 logged workout blocks
        val adherenceResult = FatLossAnalytics.computeDailyBlockAdherence(
            blocks = emptyList(),
            isRestDay = true
        )

        // Then workout block adherence displays "n/a"
        assertEquals(0, adherenceResult.totalBlocks)
        assertEquals(0, adherenceResult.completedBlocks)
        assertNull(adherenceResult.percentage)
        assertEquals("n/a", adherenceResult.displayText)

        // And Thursday is excluded from the cycle block adherence average:
        // Day 1 (Mon): 4/4 completed = 100%
        // Day 2 (Tue): 2/4 completed = 50%
        // Day 3 (Thu - Rest Day): null ("n/a")
        val dailyPercentages = listOf(100.0, 50.0, null)
        val cycleAverage = FatLossAnalytics.computeCycleBlockAdherence(dailyPercentages)

        assertNotNull(cycleAverage)
        assertEquals(75.0, cycleAverage!!, 0.001) // (100 + 50) / 2
    }

    // =========================================================================
    // Boundary Cases: Day 1, Missing Weeks, Past Unlogged Fast Days
    // =========================================================================

    @Test
    fun `boundary - past unlogged fast days count as not done`() {
        // Cycle starts on Monday Oct 5. Fast days are Monday and Thursday.
        // Today is Friday Oct 9.
        // Monday Oct 5 was NOT logged.
        // Thursday Oct 8 was logged as completed.
        val cycleStart = LocalDate.of(2026, 10, 5)
        val fastDaysMask = FatLossAnalytics.createDayOfWeekMask(listOf(DayOfWeek.MONDAY, DayOfWeek.THURSDAY))
        val dailyLogs = mapOf(
            LocalDate.of(2026, 10, 8) to DailyLog(date = LocalDate.of(2026, 10, 8), fastCompleted = true, updatedAtMillis = 1L)
        )

        val result = FatLossAnalytics.computeFastingAdherence(
            cycleStartDate = cycleStart,
            fastDaysOfWeek = fastDaysMask,
            dailyLogs = dailyLogs,
            referenceDate = LocalDate.of(2026, 10, 9)
        )

        // 2 fast days elapsed (Monday unlogged -> not done, Thursday logged -> done)
        assertEquals(2, result.totalEligibleFastDays)
        assertEquals(1, result.completedFastDays)
        assertEquals(50.0, result.percentage!!, 0.001)
    }

    @Test
    fun `boundary - missing week in 4-week cycle maintains correct day-of-week cardio averages`() {
        val cycleStart = LocalDate.of(2026, 10, 5) // Monday
        // Week 1 Mon: 40 min
        // Week 2 Mon: 20 min
        // Week 3 Mon: completely unlogged / gap (0 min)
        // Week 4 Mon: 60 min
        val dailyCardio = mapOf(
            LocalDate.of(2026, 10, 5) to 40.0,
            LocalDate.of(2026, 10, 12) to 20.0,
            LocalDate.of(2026, 10, 26) to 60.0
        )

        val averages = FatLossAnalytics.computeDayOfWeekCardioAverages(
            cycleStartDate = cycleStart,
            referenceDate = LocalDate.of(2026, 11, 1), // Sunday at end of W4
            dailyCardioMinutes = dailyCardio
        )

        val monday = averages.getValue(DayOfWeek.MONDAY)
        assertEquals(4, monday.elapsedOccurrences)
        assertEquals(120.0, monday.totalMinutes, 0.001)
        assertEquals(30.0, monday.averageMinutes, 0.001) // 120 / 4 = 30.0
    }

    @Test
    fun `boundary - Day 1 of cycle has max denominator of 1 preventing divide-by-zero`() {
        val cycleStart = LocalDate.of(2026, 10, 5) // Monday
        val averages = FatLossAnalytics.computeDayOfWeekCardioAverages(
            cycleStartDate = cycleStart,
            referenceDate = cycleStart,
            dailyCardioMinutes = emptyMap()
        )

        val monday = averages.getValue(DayOfWeek.MONDAY)
        assertEquals(1, monday.elapsedOccurrences)
        assertEquals(0.0, monday.averageMinutes, 0.001)

        val tuesday = averages.getValue(DayOfWeek.TUESDAY)
        assertEquals(0, tuesday.elapsedOccurrences)
        assertEquals(0.0, tuesday.averageMinutes, 0.001) // 0 / max(1, 0) = 0.0
    }

    @Test
    fun `boundary - bitmask round trip encodes and decodes all days of week`() {
        val allDays = DayOfWeek.values().toSet()
        val mask = FatLossAnalytics.createDayOfWeekMask(allDays)
        val decoded = FatLossAnalytics.maskToDayOfWeekSet(mask)
        assertEquals(allDays, decoded)

        // Tue + Thu: bit 1 (2) + bit 3 (8) = 10
        val tueThuMask = FatLossAnalytics.createDayOfWeekMask(listOf(DayOfWeek.TUESDAY, DayOfWeek.THURSDAY))
        assertEquals(10, tueThuMask)
        assertTrue(FatLossAnalytics.isDayInMask(DayOfWeek.TUESDAY, tueThuMask))
        assertTrue(FatLossAnalytics.isDayInMask(DayOfWeek.THURSDAY, tueThuMask))
        assertFalse(FatLossAnalytics.isDayInMask(DayOfWeek.WEDNESDAY, tueThuMask))
    }

    @Test
    fun `boundary - cardio duration with multiple TIME sets sums accurately`() {
        val block = SessionBlock(
            id = 1L, sessionId = 1L, position = 1, kind = BlockKind.CARDIO,
            resultValue = null
        )
        // 3 sets of 300, 450, 450 seconds = 1200 seconds = 20.0 minutes
        val sets = listOf(
            BlockSet(id = 1L, blockId = 1L, position = 1, reps = 1, metricValue = 300.0),
            BlockSet(id = 2L, blockId = 1L, position = 2, reps = 1, metricValue = 450.0),
            BlockSet(id = 3L, blockId = 1L, position = 3, reps = 1, metricValue = 450.0)
        )
        val minutes = FatLossAnalytics.computeBlockCardioMinutes(
            block = block,
            sets = sets,
            metricType = MetricType.TIME
        )
        assertEquals(20.0, minutes, 0.001)
    }

    @Test
    fun `boundary - weekly cardio minutes grouping correctly aggregates across weeks`() {
        val cycleStart = LocalDate.of(2026, 10, 5) // Monday Week 1
        val dailyCardio = mapOf(
            LocalDate.of(2026, 10, 5) to 20.0, // W1 Mon
            LocalDate.of(2026, 10, 7) to 30.0, // W1 Wed
            LocalDate.of(2026, 10, 12) to 40.0 // W2 Mon
        )

        val weekly = FatLossAnalytics.computeWeeklyCardioMinutes(cycleStart, dailyCardio)
        assertEquals(50.0, weekly[1] ?: 0.0, 0.001)
        assertEquals(40.0, weekly[2] ?: 0.0, 0.001)
    }

    // =========================================================================
    // Issue #574 / #576 - Scenario 3: Modifier Chips and 0% Reset with 0.5 Rounding
    // =========================================================================

    @Test
    fun `issue 576 - scenario 3 - modifier chips and 0 percent reset with 0_5 half-up rounding`() {
        // Given a base RM of 73.0 kg populated in startWeight
        val baseRm = 73.0

        // When the +2.5% chip is selected
        // Then targetWeight becomes 75.0 kg (73.0 * 1.025 = 74.825 -> 75.0 via half-up rounding)
        val target2_5 = FatLossAnalytics.scaleRepMax(baseRm, 2.5)
        assertEquals(75.0, target2_5, 0.001)

        // When the +5% chip is selected
        // Then targetWeight becomes 76.5 kg (73.0 * 1.05 = 76.65 -> 76.5 non-compounding)
        val target5_0 = FatLossAnalytics.scaleRepMax(baseRm, 5.0)
        assertEquals(76.5, target5_0, 0.001)

        // When the +10% chip is selected
        // Then targetWeight becomes 80.5 kg (73.0 * 1.10 = 80.3 -> 80.5)
        val target10_0 = FatLossAnalytics.scaleRepMax(baseRm, 10.0)
        assertEquals(80.5, target10_0, 0.001)

        // When 0% is tapped
        // Then targetWeight resets to 73.0 kg
        val target0 = FatLossAnalytics.scaleRepMax(baseRm, 0.0)
        assertEquals(73.0, target0, 0.001)

        // Verify aliases work identically
        assertEquals(75.0, FatLossAnalytics.scaleRepMaxWeight(baseRm, 2.5), 0.001)
        assertEquals(76.5, FatLossAnalytics.scaleRm(baseRm, 5.0), 0.001)
        assertEquals(73.0, FatLossAnalytics.scaleRmWeight(baseRm, 0.0), 0.001)
    }

    @Test
    fun `issue 576 - scenario 3 boundary - half-up rounding to nearest 0_5 increment precision`() {
        // Half-step boundary cases:
        // .25 -> .50 (half-up)
        assertEquals(70.5, FatLossAnalytics.roundToNearestHalf(70.25), 0.001)
        // .75 -> next whole (half-up)
        assertEquals(71.0, FatLossAnalytics.roundToNearestHalf(70.75), 0.001)
        // .24 -> .00
        assertEquals(70.0, FatLossAnalytics.roundToNearestHalf(70.24), 0.001)
        // .26 -> .50
        assertEquals(70.5, FatLossAnalytics.roundToNearestHalf(70.26), 0.001)
        // .74 -> .50
        assertEquals(70.5, FatLossAnalytics.roundToNearestHalf(70.74), 0.001)
        // .76 -> 71.0
        assertEquals(71.0, FatLossAnalytics.roundToNearestHalf(70.76), 0.001)

        // Alias verification
        assertEquals(75.0, FatLossAnalytics.roundToHalfStep(74.825), 0.001)
        assertEquals(76.5, FatLossAnalytics.roundToHalfStep(76.65), 0.001)
    }

    @Test
    fun `issue 576 - scenario 3 boundary - non-positive and nullable startWeight handling`() {
        // 0.0 startWeight returns 0.0
        assertEquals(0.0, FatLossAnalytics.scaleRepMax(0.0, 5.0), 0.001)
        // Negative startWeight returns 0.0
        assertEquals(0.0, FatLossAnalytics.scaleRepMax(-50.0, 5.0), 0.001)

        // Nullable overload
        val nullWeight: Double? = null
        assertNull(FatLossAnalytics.scaleRepMax(nullWeight, 5.0))
        assertNull(FatLossAnalytics.scaleRepMax(-10.0 as Double?, 5.0))
        assertEquals(105.0, FatLossAnalytics.scaleRepMax(100.0 as Double?, 5.0) ?: 0.0, 0.001)
    }

    // =========================================================================
    // Issue #574 / #576 - Scenario 8: Non-Positive & Invalid Target Weight Handling
    // =========================================================================

    @Test
    fun `issue 576 - scenario 8 - invalid target weight greater than or equal to start weight returns na`() {
        // Given a starting weight of 80.0 kg in Fat Loss mode
        val startWeight = 80.0
        // When the user enters a target weight of 82.0 kg (>= 80.0 kg)
        val targetWeightGreater = 82.0
        val currentWeight = 79.0

        // When FatLossAnalytics evaluates progress with W_start = 80.0 and W_target = 82.0
        val resultGreater = FatLossAnalytics.evaluateProgress(
            startingWeightKg = startWeight,
            targetWeightKg = targetWeightGreater,
            currentWeightKg = currentWeight
        )

        // Then percent-to-target returns "n/a" (null numeric percentage, "n/a" displayText)
        assertNull(resultGreater.percentToTarget)
        assertEquals("n/a", resultGreater.displayText)
        assertEquals("n/a", resultGreater.percentToTargetDisplayText)
        // And delta is still validly computed (80.0 - 79.0 = 1.0 kg)
        assertEquals(1.0, resultGreater.deltaKg ?: 0.0, 0.001)

        // When target weight is equal to start weight (80.0 == 80.0)
        val resultEqual = FatLossAnalytics.evaluateProgress(
            startingWeightKg = startWeight,
            targetWeightKg = 80.0,
            currentWeightKg = currentWeight
        )
        assertNull(resultEqual.percentToTarget)
        assertEquals("n/a", resultEqual.displayText)

        // Direct computePercentToTarget calculation also returns null
        assertNull(FatLossAnalytics.computePercentToTarget(startWeight, targetWeightGreater, currentWeight))
        assertNull(FatLossAnalytics.computePercentToTargetRatio(startWeight, targetWeightGreater, currentWeight))
        assertEquals("n/a", FatLossAnalytics.computePercentToTargetDisplayText(startWeight, targetWeightGreater, currentWeight))
    }

    @Test
    fun `issue 576 - scenario 8 - non-positive weights return na and null progress`() {
        // Zero or negative start weight
        val zeroStart = FatLossAnalytics.evaluateProgress(startingWeightKg = 0.0, targetWeightKg = 75.0, currentWeightKg = 78.0)
        assertNull(zeroStart.percentToTarget)
        assertNull(zeroStart.deltaKg)
        assertEquals("n/a", zeroStart.displayText)

        val negStart = FatLossAnalytics.evaluateProgress(startingWeightKg = -80.0, targetWeightKg = 75.0, currentWeightKg = 78.0)
        assertNull(negStart.percentToTarget)
        assertNull(negStart.deltaKg)
        assertEquals("n/a", negStart.displayText)

        // Zero or negative target weight
        val zeroTarget = FatLossAnalytics.evaluateProgress(startingWeightKg = 80.0, targetWeightKg = 0.0, currentWeightKg = 78.0)
        assertNull(zeroTarget.percentToTarget)
        assertEquals("n/a", zeroTarget.displayText)

        // Zero or negative current weight
        val zeroCurrent = FatLossAnalytics.evaluateProgress(startingWeightKg = 80.0, targetWeightKg = 75.0, currentWeightKg = 0.0)
        assertNull(zeroCurrent.percentToTarget)
        assertNull(zeroCurrent.deltaKg)
        assertEquals("n/a", zeroCurrent.displayText)
    }

    @Test
    fun `issue 576 - scenario 8 - null weights evaluate to na`() {
        // Null startWeight
        val nullStart = FatLossAnalytics.evaluateProgress(startingWeightKg = null, targetWeightKg = 75.0, currentWeightKg = 78.0)
        assertNull(nullStart.percentToTarget)
        assertNull(nullStart.deltaKg)
        assertEquals("n/a", nullStart.displayText)

        // Null targetWeight
        val nullTarget = FatLossAnalytics.evaluateProgress(startingWeightKg = 80.0, targetWeightKg = null, currentWeightKg = 78.0)
        assertNull(nullTarget.percentToTarget)
        assertEquals(2.0, nullTarget.deltaKg ?: 0.0, 0.001) // delta still works if both start and current exist
        assertEquals("n/a", nullTarget.displayText)

        // Null currentWeight (e.g. evaluating cycle baseline and target before first weigh-in)
        val nullCurrent = FatLossAnalytics.evaluateProgress(startingWeightKg = 80.0, targetWeightKg = 75.0, currentWeightKg = null)
        assertNull(nullCurrent.percentToTarget)
        assertNull(nullCurrent.deltaKg)
        assertEquals("n/a", nullCurrent.displayText)

        // Direct helper null safety
        assertNull(FatLossAnalytics.computeWeightDelta(null, 75.0))
        assertNull(FatLossAnalytics.computeWeightDelta(80.0, null))
        assertNull(FatLossAnalytics.computePercentToTarget(null, 75.0, 78.0))
        assertNull(FatLossAnalytics.computePercentToTarget(80.0, null, 78.0))
        assertNull(FatLossAnalytics.computePercentToTarget(80.0, 75.0, null))
    }

    @Test
    fun `issue 576 - valid fat loss progression computes weight delta and percent-to-target accurately`() {
        val start = 80.0
        val target = 75.0
        // Total required loss = 5.0 kg

        // 1. Day 0 (current = start = 80.0 kg): 0 kg lost, 0% complete
        val day0 = FatLossAnalytics.evaluateProgress(start, target, 80.0)
        assertEquals(0.0, day0.deltaKg ?: 0.0, 0.001)
        assertEquals(0.0, day0.percentToTarget ?: 0.0, 0.001)
        assertEquals("0%", day0.displayText)

        // 2. Halfway (current = 77.5 kg): 2.5 kg lost, 50% complete
        val halfway = FatLossAnalytics.evaluateProgress(start, target, 77.5)
        assertEquals(2.5, halfway.deltaKg ?: 0.0, 0.001)
        assertEquals(50.0, halfway.percentToTarget ?: 0.0, 0.001)
        assertEquals("50%", halfway.displayText)

        // 3. Goal reached (current = target = 75.0 kg): 5.0 kg lost, 100% complete
        val reached = FatLossAnalytics.evaluateProgress(start, target, 75.0)
        assertEquals(5.0, reached.deltaKg ?: 0.0, 0.001)
        assertEquals(100.0, reached.percentToTarget ?: 0.0, 0.001)
        assertEquals("100%", reached.displayText)

        // 4. Goal exceeded (current = 74.0 kg): 6.0 kg lost, 120% complete
        val exceeded = FatLossAnalytics.evaluateProgress(start, target, 74.0)
        assertEquals(6.0, exceeded.deltaKg ?: 0.0, 0.001)
        assertEquals(120.0, exceeded.percentToTarget ?: 0.0, 0.001)
        assertEquals("120%", exceeded.displayText)

        // 5. Weight gained (current = 81.0 kg): -1.0 kg lost, -20%
        val gained = FatLossAnalytics.evaluateProgress(start, target, 81.0)
        assertEquals(-1.0, gained.deltaKg ?: 0.0, 0.001)
        assertEquals(-20.0, gained.percentToTarget ?: 0.0, 0.001)
        assertEquals("-20%", gained.displayText)

        // 6. Decimal percentage (current = 78.2 kg): 1.8 kg lost, 1.8 / 5.0 = 36%
        val decimalPct = FatLossAnalytics.evaluateProgress(start, target, 78.2)
        assertEquals(1.8, decimalPct.deltaKg ?: 0.0, 0.001)
        assertEquals(36.0, decimalPct.percentToTarget ?: 0.0, 0.001)
        assertEquals("36%", decimalPct.displayText)

        // 7. Non-integer decimal percentage (current = 78.175 kg): 1.825 kg lost, 1.825 / 5.0 = 36.5%
        val nonIntPct = FatLossAnalytics.evaluateProgress(start, target, 78.175)
        assertEquals(1.825, nonIntPct.deltaKg ?: 0.0, 0.001)
        assertEquals(36.5, nonIntPct.percentToTarget ?: 0.0, 0.001)
        assertEquals("36.5%", nonIntPct.displayText)
    }

    @Test
    fun `issue 576 - cycle entity integration evaluates weight progress`() {
        val cycle = Cycle(
            id = 1L,
            name = "Summer Shred",
            startDate = LocalDate.of(2026, 10, 1),
            goal = "Cut to 78kg",
            startingWeightKg = 85.0,
            targetWeightKg = 78.0,
            isBaselineAutoDerived = true
        )

        val progress = FatLossAnalytics.evaluateProgress(cycle, currentWeightKg = 81.5)
        assertEquals(3.5, progress.deltaKg ?: 0.0, 0.001)
        assertEquals(50.0, progress.percentToTarget ?: 0.0, 0.001) // 3.5 / 7.0 = 50%
        assertEquals("50%", progress.displayText)
        assertEquals("3.5 kg", progress.deltaDisplayText)

        val pct = FatLossAnalytics.computePercentToTarget(cycle, currentWeightKg = 81.5)
        assertEquals(50.0, pct ?: 0.0, 0.001)
    }

    // =========================================================================
    // Issue #583 / #584 - Rolling Velocity & Pacing Engine (Scenarios a–m)
    // =========================================================================

    @Test
    fun `scenario a - normal pace and forecast with 14-day anchor`() {
        // Given an active Fat Loss cycle with start weight 85.0 kg, target 80.0 kg, current weight 82.0 kg,
        // and a 14-day anchor of 83.0 kg over 14 days (R_14d = -0.5 kg/wk, V_14d = 0.5 kg/wk)
        val today = LocalDate.of(2026, 10, 15)
        val cycle = Cycle(
            id = 1L,
            name = "Cut Cycle",
            startDate = LocalDate.of(2026, 10, 1),
            type = CycleType.FAT_LOSS_BODYBUILDING,
            startingWeightKg = 85.0,
            targetWeightKg = 80.0,
            isActive = true
        )
        val entries = listOf(
            WeightEntry(date = LocalDate.of(2026, 10, 1), weightKg = 83.0, updatedAtMillis = 1L),
            WeightEntry(date = today, weightKg = 82.0, updatedAtMillis = 2L)
        )

        // When evaluating 14-day pace and forecast
        val paceResult = FatLossAnalytics.compute14DayPace(entries, cycle, today)
        assertNotNull(paceResult)
        assertEquals(-0.5, paceResult!!.rateKgPerWeek, 0.001)
        assertEquals(0.5, paceResult.deficitVelocityKgPerWeek, 0.001)
        assertEquals(PaceTrend.LOSS, paceResult.trend)
        assertEquals("-0.5 kg/wk", paceResult.formatDisplay())
        assertEquals("-0.5 kg/wk", FatLossAnalytics.formatPaceChip(paceResult))

        val forecast = FatLossAnalytics.computePaceForecast(entries, cycle, today)
        assertNotNull(forecast)
        assertTrue(forecast is PaceForecast.Projected)
        val projected = forecast as PaceForecast.Projected
        // Remaining = 82.0 - 80.0 = 2.0 kg. Weeks = 2.0 / 0.5 = 4.0 weeks.
        assertEquals(4.0, projected.weeks, 0.001)
        assertEquals(today.plusDays(28), projected.targetDate)
        assertFalse(projected.isCapped)
        assertEquals("Estimated: 4.0 weeks", projected.displayText)
    }

    @Test
    fun `scenario b - window average and sparse fallback selection`() {
        // Given weigh-ins on day t, t-8, and t-16 where t-16 >= cycle.startDate
        val cycleStart = LocalDate.of(2026, 9, 1)
        val t = LocalDate.of(2026, 10, 16)
        val entries = listOf(
            WeightEntry(date = t.minusDays(16), weightKg = 85.0, updatedAtMillis = 1L),
            WeightEntry(date = t.minusDays(8), weightKg = 84.0, updatedAtMillis = 2L),
            WeightEntry(date = t, weightKg = 83.0, updatedAtMillis = 3L)
        )

        // When computing 7-day and 14-day pace
        val pace7d = FatLossAnalytics.compute7DayPace(entries, cycleStart, t)
        val pace14d = FatLossAnalytics.compute14DayPace(entries, cycleStart, t)

        // Then the 7-day window [t-13, t-7] averages the t-8 entry (84.0 kg) vs [t-6, t] (83.0 kg)
        assertNotNull(pace7d)
        assertEquals(t.minusDays(8), pace7d!!.anchorDate)
        assertEquals(7L, pace7d.elapsedDays)
        assertEquals(-1.0, pace7d.rateKgPerWeek, 0.001)

        // And the 14-day window [t-20, t-14] averages the t-16 entry (85.0 kg) vs [t-6, t] (83.0 kg) over 2 weeks
        assertNotNull(pace14d)
        assertEquals(t.minusDays(16), pace14d!!.anchorDate)
        assertEquals(14L, pace14d.elapsedDays)
        assertEquals(-1.0, pace14d.rateKgPerWeek, 0.001)

        // Sparse fallback when [t-13, t-7] has no entries: falls back to t-16 normalized over 16 days
        val sparseEntries = listOf(
            WeightEntry(date = t.minusDays(16), weightKg = 85.0, updatedAtMillis = 1L),
            WeightEntry(date = t, weightKg = 83.0, updatedAtMillis = 3L)
        )
        val sparsePace7d = FatLossAnalytics.compute7DayPace(sparseEntries, cycleStart, t)
        assertNotNull(sparsePace7d)
        assertEquals(t.minusDays(16), sparsePace7d!!.anchorDate)
        assertEquals(16L, sparsePace7d.elapsedDays)
        assertEquals(-0.875, sparsePace7d.rateKgPerWeek, 0.001)
    }

    @Test
    fun `scenario c - cold start under 7 days or single entry returns not enough data`() {
        val today = LocalDate.of(2026, 10, 5)
        val cycle = Cycle(
            id = 1L,
            name = "Cut",
            startDate = LocalDate.of(2026, 10, 1),
            type = CycleType.FAT_LOSS_BODYBUILDING,
            startingWeightKg = 85.0,
            targetWeightKg = 80.0
        )

        // Case 1: Single weight entry
        val singleEntry = listOf(
            WeightEntry(date = LocalDate.of(2026, 10, 1), weightKg = 85.0, updatedAtMillis = 1L)
        )
        assertNull(FatLossAnalytics.compute7DayPace(singleEntry, cycle, today))
        assertNull(FatLossAnalytics.compute14DayPace(singleEntry, cycle, today))
        assertEquals("Not enough data yet", FatLossAnalytics.formatPaceRate(null))
        val forecastSingle = FatLossAnalytics.computePaceForecast(singleEntry, cycle, today)
        assertEquals(PaceForecast.NotEnoughData, forecastSingle)
        assertEquals("Logging check-ins for 7 days enables pace projections", forecastSingle?.displayText)

        // Case 2: 2 entries spanning < 7 calendar days (e.g. 6 days: Oct 1 to Oct 7)
        val shortSpanEntries = listOf(
            WeightEntry(date = LocalDate.of(2026, 10, 1), weightKg = 85.0, updatedAtMillis = 1L),
            WeightEntry(date = LocalDate.of(2026, 10, 7), weightKg = 84.5, updatedAtMillis = 2L)
        )
        assertNull(FatLossAnalytics.compute7DayPace(shortSpanEntries, cycle, today = LocalDate.of(2026, 10, 7)))
        assertNull(FatLossAnalytics.compute14DayPace(shortSpanEntries, cycle, today = LocalDate.of(2026, 10, 7)))
        val forecastShort = FatLossAnalytics.computePaceForecast(shortSpanEntries, cycle, today = LocalDate.of(2026, 10, 7))
        assertEquals(PaceForecast.NotEnoughData, forecastShort)
    }

    @Test
    fun `scenario d - plateau or weight gain returns stalled forecast and positive rate`() {
        // Given start weight 83.0 kg, current weight 83.4 kg, and anchor weight 83.0 kg over 7 days (W_current > W_anchor)
        val today = LocalDate.of(2026, 10, 8)
        val cycle = Cycle(
            id = 1L,
            name = "Cut",
            startDate = LocalDate.of(2026, 10, 1),
            type = CycleType.FAT_LOSS_BODYBUILDING,
            startingWeightKg = 83.0,
            targetWeightKg = 78.0
        )
        val entries = listOf(
            WeightEntry(date = LocalDate.of(2026, 10, 1), weightKg = 83.0, updatedAtMillis = 1L),
            WeightEntry(date = today, weightKg = 83.4, updatedAtMillis = 2L)
        )

        // When evaluating pace and forecast
        val pace7d = FatLossAnalytics.compute7DayPace(entries, cycle, today)
        assertNotNull(pace7d)
        // Rate: (83.4 - 83.0) / 7 * 7 = +0.4 kg/wk
        assertEquals(0.4, pace7d!!.rateKgPerWeek, 0.001)
        assertEquals(PaceTrend.GAIN, pace7d.trend)
        assertEquals("+0.4 kg/wk", pace7d.formatDisplay())

        val forecast = FatLossAnalytics.computePaceForecast(entries, cycle, today)
        assertEquals(PaceForecast.Stalled, forecast)
        assertEquals("Pace stalled — insufficient deficit to project", forecast?.displayText)
    }

    @Test
    fun `scenario e - goal reached returns GoalReached state`() {
        // Given current weight 79.5 kg with target weight 80.0 kg (W_current <= W_target)
        val today = LocalDate.of(2026, 10, 15)
        val cycle = Cycle(
            id = 1L,
            name = "Cut",
            startDate = LocalDate.of(2026, 10, 1),
            type = CycleType.FAT_LOSS_BODYBUILDING,
            startingWeightKg = 85.0,
            targetWeightKg = 80.0
        )
        val entries = listOf(
            WeightEntry(date = LocalDate.of(2026, 10, 1), weightKg = 85.0, updatedAtMillis = 1L),
            WeightEntry(date = today, weightKg = 79.5, updatedAtMillis = 2L)
        )

        val forecast = FatLossAnalytics.computePaceForecast(entries, cycle, today)
        assertEquals(PaceForecast.GoalReached, forecast)
        assertEquals("Goal reached! 🎉", forecast?.displayText)
    }

    @Test
    fun `scenario f - no in-cycle weight entries returns NotEnoughData`() {
        val today = LocalDate.of(2026, 10, 15)
        val cycle = Cycle(
            id = 1L,
            name = "Cut",
            startDate = LocalDate.of(2026, 10, 1),
            type = CycleType.FAT_LOSS_BODYBUILDING,
            startingWeightKg = 85.0,
            targetWeightKg = 80.0
        )

        val forecast = FatLossAnalytics.computePaceForecast(emptyList(), cycle, today)
        assertEquals(PaceForecast.NotEnoughData, forecast)
    }

    @Test
    fun `scenario g - non-fat-loss cycle eligibility check`() {
        val nonFatLossCycle = Cycle(
            id = 1L,
            name = "Strength Block",
            startDate = LocalDate.of(2026, 10, 1),
            type = CycleType.STRENGTH_WEIGHTLIFTING
        )
        val fatLossCycle = Cycle(
            id = 2L,
            name = "Bodybuilding Cut",
            startDate = LocalDate.of(2026, 10, 1),
            type = CycleType.FAT_LOSS_BODYBUILDING
        )

        assertFalse(FatLossAnalytics.isFatLossCycle(nonFatLossCycle))
        assertTrue(FatLossAnalytics.isFatLossCycle(fatLossCycle))
    }

    @Test
    fun `scenario h - imperial unit localization converts pace chips and formats in lbs`() {
        // Rate -0.5 kg/wk -> lbs
        val rateResult = PaceRateResult(
            rateKgPerWeek = -0.5,
            anchorWeightKg = 83.0,
            anchorDate = LocalDate.of(2026, 10, 1),
            currentWeightKg = 82.0,
            currentDate = LocalDate.of(2026, 10, 15),
            elapsedDays = 14,
            trend = PaceTrend.LOSS
        )

        // kg display: -0.5 kg/wk
        assertEquals("-0.5 kg/wk", rateResult.formatDisplay("kg"))
        // lbs display: -0.5 * 2.20462262185 = -1.1023 -> -1.1 lbs/wk
        assertEquals("-1.1 lbs/wk", rateResult.formatDisplay("lbs"))
        assertEquals("-1.1 lbs/wk", FatLossAnalytics.formatPaceRate(-0.5, "lbs"))

        // Positive rate in lbs
        assertEquals("+0.9 lbs/wk", FatLossAnalytics.formatPaceRate(0.4, "lbs"))
        // Neutral rate in lbs
        assertEquals("0.0 lbs/wk", FatLossAnalytics.formatPaceRate(0.0, "lbs"))
    }

    @Test
    fun `scenario i - zero movement goals present evaluates weight pacing independently`() {
        // Pacing engine operates purely on weight entries and cycle metadata without requiring movement goals
        val today = LocalDate.of(2026, 10, 15)
        val cycleWithoutGoals = Cycle(
            id = 1L,
            name = "Pure Fat Loss",
            startDate = LocalDate.of(2026, 10, 1),
            type = CycleType.FAT_LOSS_BODYBUILDING,
            startingWeightKg = 85.0,
            targetWeightKg = 80.0
        )
        val entries = listOf(
            WeightEntry(date = LocalDate.of(2026, 10, 1), weightKg = 83.0, updatedAtMillis = 1L),
            WeightEntry(date = today, weightKg = 82.0, updatedAtMillis = 2L)
        )

        val pace = FatLossAnalytics.compute14DayPace(entries, cycleWithoutGoals, today)
        val forecast = FatLossAnalytics.computePaceForecast(entries, cycleWithoutGoals, today)

        assertNotNull(pace)
        assertEquals(-0.5, pace!!.rateKgPerWeek, 0.001)
        assertTrue(forecast is PaceForecast.Projected)
    }

    @Test
    fun `scenario j - past cycle ended returns CycleEnded`() {
        // Given a completed cycle where today > cycle.endDate and W_current > W_target (or target is null)
        val today = LocalDate.of(2026, 11, 1)
        val endedCycle = Cycle(
            id = 1L,
            name = "Cut",
            startDate = LocalDate.of(2026, 10, 1),
            endDate = LocalDate.of(2026, 10, 25),
            type = CycleType.FAT_LOSS_BODYBUILDING,
            startingWeightKg = 85.0,
            targetWeightKg = 80.0
        )
        val entries = listOf(
            WeightEntry(date = LocalDate.of(2026, 10, 1), weightKg = 85.0, updatedAtMillis = 1L),
            WeightEntry(date = LocalDate.of(2026, 10, 20), weightKg = 82.0, updatedAtMillis = 2L)
        )

        val forecast = FatLossAnalytics.computePaceForecast(entries, endedCycle, today)
        assertEquals(PaceForecast.CycleEnded, forecast)
        assertEquals("Cycle ended", forecast?.displayText)

        // Target null on ended cycle also returns CycleEnded
        val endedCycleNoTarget = endedCycle.copy(targetWeightKg = null)
        val forecastNoTarget = FatLossAnalytics.computePaceForecast(entries, endedCycleNoTarget, today)
        assertEquals(PaceForecast.CycleEnded, forecastNoTarget)
    }

    @Test
    fun `scenario k - cold start fallback to cycle pace when 14-day anchor absent`() {
        // Given 2 weight entries spanning 10 days (W_start = 85.0 kg, W_current = 84.0 kg), target 80.0 kg, but no 14-day anchor
        val today = LocalDate.of(2026, 10, 11)
        val cycle = Cycle(
            id = 1L,
            name = "Cut",
            startDate = LocalDate.of(2026, 10, 1),
            type = CycleType.FAT_LOSS_BODYBUILDING,
            startingWeightKg = 85.0,
            targetWeightKg = 80.0
        )
        val entries = listOf(
            WeightEntry(date = LocalDate.of(2026, 10, 1), weightKg = 85.0, updatedAtMillis = 1L),
            WeightEntry(date = today, weightKg = 84.0, updatedAtMillis = 2L)
        )

        // When evaluating pace
        val pace14d = FatLossAnalytics.compute14DayPace(entries, cycle, today)
        assertNull(pace14d) // No 14d anchor -> "Not enough data yet"
        assertEquals("Not enough data yet", FatLossAnalytics.formatPaceChip(pace14d))

        // When evaluating forecast
        val forecast = FatLossAnalytics.computePaceForecast(entries, cycle, today)
        assertNotNull(forecast)
        assertTrue(forecast is PaceForecast.Projected)
        val projected = forecast as PaceForecast.Projected

        // Last Week 7D window [Oct 5, Oct 11] vs [Sep 28, Oct 4] (85.0 kg) gives 7-day smoothed velocity = 1.0 kg/wk (Tier 2)
        // Remaining: 84.0 - 80.0 = 4.0 kg. Weeks: 4.0 / 1.0 = 4.0 weeks.
        assertEquals(4.0, projected.weeks, 0.001)
        assertEquals("Estimated: 4.0 weeks", projected.displayText)
    }

    @Test
    fun `scenario l - 14-day chip weekly rate matches forecast basis`() {
        // Given a 14-day anchor indicating 1.0 kg lost over 14 days
        val today = LocalDate.of(2026, 10, 15)
        val cycle = Cycle(
            id = 1L,
            name = "Cut",
            startDate = LocalDate.of(2026, 10, 1),
            type = CycleType.FAT_LOSS_BODYBUILDING,
            startingWeightKg = 85.0,
            targetWeightKg = 80.0
        )
        val entries = listOf(
            WeightEntry(date = LocalDate.of(2026, 10, 1), weightKg = 83.0, updatedAtMillis = 1L),
            WeightEntry(date = today, weightKg = 82.0, updatedAtMillis = 2L)
        )

        val pace14d = FatLossAnalytics.compute14DayPace(entries, cycle, today)
        assertNotNull(pace14d)
        assertEquals(-0.5, pace14d!!.rateKgPerWeek, 0.001)
        assertEquals("-0.5 kg/wk", pace14d.formatDisplay())

        val forecast = FatLossAnalytics.computePaceForecast(entries, cycle, today)
        assertNotNull(forecast)
        assertTrue(forecast is PaceForecast.Projected)
        val projected = forecast as PaceForecast.Projected

        // Forecast divides remaining 2.0 kg by 0.5 kg/wk = 4.0 weeks
        assertEquals(2.0 / pace14d.deficitVelocityKgPerWeek, projected.weeks, 0.001)
    }

    @Test
    fun `scenario m - open-ended cycle without end date never returns CycleEnded`() {
        // Given an active Fat Loss cycle with cycle.endDate == null
        val today = LocalDate.of(2028, 1, 1) // Way in the future
        val cycle = Cycle(
            id = 1L,
            name = "Long Term Cut",
            startDate = LocalDate.of(2026, 10, 1),
            endDate = null,
            type = CycleType.FAT_LOSS_BODYBUILDING,
            startingWeightKg = 90.0,
            targetWeightKg = 80.0
        )
        val entries = listOf(
            WeightEntry(date = LocalDate.of(2026, 10, 1), weightKg = 90.0, updatedAtMillis = 1L),
            WeightEntry(date = LocalDate.of(2026, 10, 15), weightKg = 88.0, updatedAtMillis = 2L)
        )

        val forecast = FatLossAnalytics.computePaceForecast(entries, cycle, today)
        // Never returns CycleEnded
        assertTrue(forecast != PaceForecast.CycleEnded)
    }

    // =========================================================================
    // Boundary Cases: 52-Week Cap, Unset Targets, Soft Deletes, Future Dates
    // =========================================================================

    @Test
    fun `boundary - 52-week cap suppresses targetDate and displays greater than 1 year`() {
        // Target requires 10.0 kg loss at slow 0.1 kg/wk deficit -> 100 weeks > 52 weeks
        val today = LocalDate.of(2026, 10, 15)
        val cycle = Cycle(
            id = 1L,
            name = "Slow Cut",
            startDate = LocalDate.of(2026, 10, 1),
            type = CycleType.FAT_LOSS_BODYBUILDING,
            startingWeightKg = 90.0,
            targetWeightKg = 80.0
        )
        val entries = listOf(
            // 90.2 -> 90.0 over 14 days = 0.1 kg/wk
            WeightEntry(date = LocalDate.of(2026, 10, 1), weightKg = 90.2, updatedAtMillis = 1L),
            WeightEntry(date = today, weightKg = 90.0, updatedAtMillis = 2L)
        )

        val forecast = FatLossAnalytics.computePaceForecast(entries, cycle, today)
        assertNotNull(forecast)
        assertTrue(forecast is PaceForecast.Projected)
        val projected = forecast as PaceForecast.Projected

        assertEquals(52.0, projected.weeks, 0.001)
        assertNull(projected.targetDate)
        assertTrue(projected.isCapped)
        assertEquals("Estimated: > 1 year", projected.displayText)
    }

    @Test
    fun `boundary - unset target weight skips forecast calculation and returns null`() {
        val today = LocalDate.of(2026, 10, 15)
        val cycleNoTarget = Cycle(
            id = 1L,
            name = "No Target Cut",
            startDate = LocalDate.of(2026, 10, 1),
            type = CycleType.FAT_LOSS_BODYBUILDING,
            startingWeightKg = 85.0,
            targetWeightKg = null
        )
        val entries = listOf(
            WeightEntry(date = LocalDate.of(2026, 10, 1), weightKg = 85.0, updatedAtMillis = 1L),
            WeightEntry(date = today, weightKg = 83.0, updatedAtMillis = 2L)
        )

        assertNull(FatLossAnalytics.computePaceForecast(entries, cycleNoTarget, today))
    }

    @Test
    fun `boundary - soft deleted weight entries are excluded from pace and forecast`() {
        val today = LocalDate.of(2026, 10, 15)
        val cycle = Cycle(
            id = 1L,
            name = "Cut",
            startDate = LocalDate.of(2026, 10, 1),
            type = CycleType.FAT_LOSS_BODYBUILDING,
            startingWeightKg = 85.0,
            targetWeightKg = 80.0
        )
        val entries = listOf(
            // soft deleted, inside the 7-day prior window [Oct 2, Oct 8]; would anchor at 90.0 if not excluded
            WeightEntry(date = LocalDate.of(2026, 10, 8), weightKg = 90.0, updatedAtMillis = 1L, deletedAtMillis = 999L),
            WeightEntry(date = today, weightKg = 83.0, updatedAtMillis = 2L)
        )

        // Deleted entry is ignored: the prior window is empty, so the anchor falls back to startingWeightKg at cycle start
        val pace7 = FatLossAnalytics.compute7DayPace(entries, cycle, today)
        assertNotNull(pace7)
        assertEquals(85.0, pace7!!.anchorWeightKg, 1e-9)
        assertEquals(cycle.startDate, pace7.anchorDate)
        assertEquals(-1.0, pace7.rateKgPerWeek, 1e-9)

        // Without a starting weight, the single non-deleted entry yields no pace and NotEnoughData
        val cycleNoStart = cycle.copy(startingWeightKg = null)
        assertNull(FatLossAnalytics.compute7DayPace(entries, cycleNoStart, today))
        assertEquals(PaceForecast.NotEnoughData, FatLossAnalytics.computePaceForecast(entries, cycleNoStart, today))
    }

    @Test
    fun `boundary - future weight entries beyond today are excluded`() {
        val today = LocalDate.of(2026, 10, 10)
        val cycle = Cycle(
            id = 1L,
            name = "Cut",
            startDate = LocalDate.of(2026, 10, 1),
            type = CycleType.FAT_LOSS_BODYBUILDING,
            startingWeightKg = 85.0,
            targetWeightKg = 80.0
        )
        val entries = listOf(
            WeightEntry(date = LocalDate.of(2026, 10, 1), weightKg = 85.0, updatedAtMillis = 1L),
            WeightEntry(date = LocalDate.of(2026, 10, 10), weightKg = 84.0, updatedAtMillis = 2L),
            WeightEntry(date = LocalDate.of(2026, 10, 20), weightKg = 79.0, updatedAtMillis = 3L) // future!
        )

        // Evaluating at Oct 10 must ignore Oct 20 entry
        val forecast = FatLossAnalytics.computePaceForecast(entries, cycle, today)
        assertTrue(forecast is PaceForecast.Projected)
        // If Oct 20 had been evaluated, it would have returned GoalReached (79.0 <= 80.0)
        assertFalse(forecast is PaceForecast.GoalReached)
    }

    // =========================================================================
    // Issue #588: 3-Tier Velocity Resolution Hierarchy Scenarios 1–4
    // =========================================================================

    @Test
    fun `scenario 1 - early cycle water drop selects anchor7 matching 7-day pace chip`() {
        // Given a fat loss cycle starting on 2026-09-28 with target weight 67.0 kg
        val cycle = Cycle(
            id = 101L,
            name = "Early Cut",
            startDate = LocalDate.of(2026, 9, 28),
            type = CycleType.FAT_LOSS_BODYBUILDING,
            startingWeightKg = 77.8,
            targetWeightKg = 67.0
        )
        // And weight entries:
        // - 2026-09-28: 77.8 kg (starting weigh-in)
        // - 2026-09-29: 75.8 kg (initial 2.0 kg water loss)
        // - 2026-10-06: 75.0 kg (current weigh-in)
        val today = LocalDate.of(2026, 10, 6)
        val entries = listOf(
            WeightEntry(date = LocalDate.of(2026, 9, 28), weightKg = 77.8, updatedAtMillis = 1L),
            WeightEntry(date = LocalDate.of(2026, 9, 29), weightKg = 75.8, updatedAtMillis = 2L),
            WeightEntry(date = today, weightKg = 75.0, updatedAtMillis = 3L)
        )

        // When computePaceForecast is evaluated on 2026-10-06
        val pace14 = FatLossAnalytics.compute14DayPace(entries, cycle, today)
        val pace7 = FatLossAnalytics.compute7DayPace(entries, cycle, today)
        val forecast = FatLossAnalytics.computePaceForecast(entries, cycle, today)

        // Then:
        // - compute14DayPace returns null ("Not enough data yet")
        assertNull(pace14)
        // - compute7DayPace compares Today 7D avg [Sep 30, Oct 6] (75.0 kg) vs Last Week 7D avg [Sep 23, Sep 29] ((77.8 + 75.8)/2 = 76.8 kg) -> -1.8 kg/wk
        assertNotNull(pace7)
        assertEquals(-1.8, pace7!!.rateKgPerWeek, 1e-6)
        assertEquals(1.8, pace7.deficitVelocityKgPerWeek, 1e-6)
        // - The forecast uses Tier 2 (7D smoothed pace V_active = 1.8 kg/wk)
        // - Remaining = 75.0 - 67.0 = 8.0 kg -> weeks = 8.0 / 1.8 = 4.444444 weeks (31 days -> 2026-11-06)
        assertTrue(forecast is PaceForecast.Projected)
        val projected = forecast as PaceForecast.Projected
        assertEquals(8.0 / 1.8, projected.weeks, 1e-6)
        assertEquals(LocalDate.of(2026, 11, 6), projected.targetDate)
        // - displayText is "Estimated: 4.4 weeks"
        assertEquals("Estimated: 4.4 weeks", projected.displayText)
    }

    @Test
    fun `scenario 2 - mature cycle with 14-day priority selects anchor14 over anchor7`() {
        // Given a fat loss cycle with target weight 74.0 kg and weight entries:
        // - 2026-09-15: 80.0 kg
        // - 2026-09-22: 79.0 kg (14-day anchor, Δdays = 14)
        // - 2026-09-29: 78.2 kg (7-day anchor, Δdays = 7)
        // - 2026-10-06: 78.0 kg (current weigh-in)
        val today = LocalDate.of(2026, 10, 6)
        val cycle = Cycle(
            id = 102L,
            name = "Mature Cut",
            startDate = LocalDate.of(2026, 9, 15),
            type = CycleType.FAT_LOSS_BODYBUILDING,
            startingWeightKg = 80.0,
            targetWeightKg = 74.0
        )
        val entries = listOf(
            WeightEntry(date = LocalDate.of(2026, 9, 15), weightKg = 80.0, updatedAtMillis = 1L),
            WeightEntry(date = LocalDate.of(2026, 9, 22), weightKg = 79.0, updatedAtMillis = 2L),
            WeightEntry(date = LocalDate.of(2026, 9, 29), weightKg = 78.2, updatedAtMillis = 3L),
            WeightEntry(date = today, weightKg = 78.0, updatedAtMillis = 4L)
        )

        // When computePaceForecast is evaluated on 2026-10-06
        val pace14 = FatLossAnalytics.compute14DayPace(entries, cycle, today)
        val pace7 = FatLossAnalytics.compute7DayPace(entries, cycle, today)
        val forecast = FatLossAnalytics.computePaceForecast(entries, cycle, today)

        // Then:
        // - The forecast selects anchor14 (V_14d = 0.5 kg/wk) rather than anchor7 (V_7d = 0.2 kg/wk)
        assertNotNull(pace14)
        assertEquals(-0.5, pace14!!.rateKgPerWeek, 1e-6)
        assertEquals(0.5, pace14.deficitVelocityKgPerWeek, 1e-6)

        assertNotNull(pace7)
        assertEquals(-0.2, pace7!!.rateKgPerWeek, 1e-6)
        assertEquals(0.2, pace7.deficitVelocityKgPerWeek, 1e-6)

        assertTrue(forecast is PaceForecast.Projected)
        val projected = forecast as PaceForecast.Projected
        assertEquals(8.0, projected.weeks, 1e-6)
        assertEquals(LocalDate.of(2026, 12, 1), projected.targetDate)
        assertEquals("Estimated: 8.0 weeks", projected.displayText)
    }

    @Test
    fun `scenario 3 - tier 2 boundary rate stagnation returns Stalled`() {
        // Given a fat loss cycle on day 8 with anchor14 == null and 7-day rate R_7d >= 0.0 (V_7d <= 0.0, rounded to 0.1)
        val startDate = LocalDate.of(2026, 10, 1)
        val today = LocalDate.of(2026, 10, 9)
        val cycle = Cycle(
            id = 103L,
            name = "Stalled Cut",
            startDate = startDate,
            type = CycleType.FAT_LOSS_BODYBUILDING,
            startingWeightKg = 85.0,
            targetWeightKg = 80.0
        )
        val entries = listOf(
            WeightEntry(date = startDate, weightKg = 85.0, updatedAtMillis = 1L),
            WeightEntry(date = today.minusDays(7), weightKg = 84.8, updatedAtMillis = 2L),
            WeightEntry(date = today, weightKg = 85.0, updatedAtMillis = 3L)
        )

        assertNull(FatLossAnalytics.compute14DayPace(entries, cycle, today))

        // When computePaceForecast is evaluated
        val forecast = FatLossAnalytics.computePaceForecast(entries, cycle, today)

        // Then the engine returns PaceForecast.Stalled
        assertEquals(PaceForecast.Stalled, forecast)
    }

    @Test
    fun `scenario 4 - tier 2 boundary 52-week cap with shallow deficit`() {
        // Given a fat loss cycle on day 8 with anchor14 == null and an extremely shallow deficit (V_7d = 0.05 kg/wk)
        // with 10.0 kg remaining (rawWeeks = 100.0 with 1-decimal rounded velocity 0.1 kg/wk)
        val startDate = LocalDate.of(2026, 10, 1)
        val today = LocalDate.of(2026, 10, 9)
        val cycle = Cycle(
            id = 104L,
            name = "Slow Cut Tier 2",
            startDate = startDate,
            type = CycleType.FAT_LOSS_BODYBUILDING,
            startingWeightKg = 90.0,
            targetWeightKg = 80.0
        )
        val entries = listOf(
            WeightEntry(date = startDate, weightKg = 90.05, updatedAtMillis = 1L),
            WeightEntry(date = today.minusDays(7), weightKg = 90.05, updatedAtMillis = 2L),
            WeightEntry(date = today, weightKg = 90.00, updatedAtMillis = 3L)
        )

        assertNull(FatLossAnalytics.compute14DayPace(entries, cycle, today))

        // When computePaceForecast is evaluated
        val forecast = FatLossAnalytics.computePaceForecast(entries, cycle, today)

        // Then the engine returns PaceForecast.Projected(weeks = 52.0, targetDate = null, rawWeeks = 100.0)
        assertNotNull(forecast)
        assertTrue(forecast is PaceForecast.Projected)
        val projected = forecast as PaceForecast.Projected
        assertEquals(52.0, projected.weeks, 1e-6)
        assertNull(projected.targetDate)
        assertEquals(100.0, projected.rawWeeks, 1e-6)
        // And isCapped is true and displayText is "Estimated: > 1 year"
        assertTrue(projected.isCapped)
        assertEquals("Estimated: > 1 year", projected.displayText)
    }

    // =========================================================================
    // Issue #590: 7-Day Average Weight for Cycle Progress and Forecast Estimation
    // =========================================================================

    @Test
    fun `issue 590 - scenario 1 - daily water spike smoothing synthetic reproduction fixture`() {
        // Given a fat loss cycle starting on 2026-09-28 with target weight 67.0 kg
        val startDate = LocalDate.of(2026, 9, 28)
        val today = LocalDate.of(2026, 10, 7)
        val cycle = Cycle(
            id = 5901L,
            name = "Cut Cycle",
            startDate = startDate,
            type = CycleType.FAT_LOSS_BODYBUILDING,
            startingWeightKg = 77.8,
            targetWeightKg = 67.0
        )
        // And weight entries:
        // - 2026-09-28: 77.8 kg (starting weigh-in, anchor)
        // - 2026-10-01 to 2026-10-06 (6 days): daily weigh-ins at 75.0 kg
        // - 2026-10-07: daily weigh-in spike at 76.5 kg (+1.5 kg water jump)
        val entries = mutableListOf(
            WeightEntry(date = startDate, weightKg = 77.8, updatedAtMillis = 1L)
        )
        for (day in 1..6) {
            entries.add(
                WeightEntry(date = LocalDate.of(2026, 10, day), weightKg = 75.0, updatedAtMillis = (day + 1).toLong())
            )
        }
        entries.add(
            WeightEntry(date = today, weightKg = 76.5, updatedAtMillis = 8L)
        )

        // When computeCurrent7DayAverageWeight and computePaceForecast are evaluated on 2026-10-07
        val avg7d = FatLossAnalytics.computeCurrent7DayAverageWeight(entries, cycle, today)
        val forecast = FatLossAnalytics.computePaceForecast(entries, cycle, today)

        // Then:
        // - Span days = 9 (>= 7, passing the sampling guard)
        val oldest = entries.first()
        val newest = entries.last()
        val spanDays = java.time.temporal.ChronoUnit.DAYS.between(oldest.date, newest.date)
        assertEquals(9L, spanDays)

        // - The 7-day average weight is (6 * 75.0 + 76.5) / 7 = 526.5 / 7 ≈ 75.214 kg (± 1e-4)
        assertNotNull(avg7d)
        assertEquals(526.5 / 7.0, avg7d!!, 1e-4)

        // - V_active uses 7-day smoothed deficit velocity: 77.8 - (526.5 / 7.0) = 2.5857 kg/wk, remainingKg = 8.214 kg, and rawWeeks ≈ 3.18 weeks
        // - rawWeeks is strictly less than the unmitigated raw baseline ((76.5 - 67.0) / 1.011 ≈ 9.40 weeks)
        assertTrue(forecast is PaceForecast.Projected)
        val projected = forecast as PaceForecast.Projected
        val expectedVActive = 77.8 - (526.5 / 7.0)         // ≈ 2.585714...
        val expectedRemaining = (526.5 / 7.0) - 67.0       // ≈ 8.2142857...
        val expectedRawWeeks = expectedRemaining / expectedVActive // ≈ 3.1768...
        assertEquals(expectedRawWeeks, projected.rawWeeks, 0.05)
        assertEquals(3.18, projected.weeks, 0.05)

        val unmitigatedRawWeeks = (76.5 - 67.0) / (((77.8 - 76.5) / 9.0) * 7.0)
        assertTrue("rawWeeks must be strictly less than unmitigated raw baseline", projected.rawWeeks < unmitigatedRawWeeks)
    }

    @Test
    fun `issue 590 - scenario 2 - cold start on day 1 and day 2`() {
        // Given a new cycle starting on 2026-10-01 with initial entry 80.0 kg on 2026-10-01
        val startDate = LocalDate.of(2026, 10, 1)
        val entryDay1 = WeightEntry(date = startDate, weightKg = 80.0, updatedAtMillis = 1L)
        val entries = mutableListOf(entryDay1)

        // When computeCurrent7DayAverageWeight is evaluated on 2026-10-01
        val avgDay1 = FatLossAnalytics.computeCurrent7DayAverageWeight(entries, startDate, startDate)

        // Then returns 80.0 kg (N = 1)
        assertNotNull(avgDay1)
        assertEquals(80.0, avgDay1!!, 1e-6)

        // When an entry 79.0 kg is added on 2026-10-02
        val day2 = LocalDate.of(2026, 10, 2)
        entries.add(WeightEntry(date = day2, weightKg = 79.0, updatedAtMillis = 2L))
        val avgDay2 = FatLossAnalytics.computeCurrent7DayAverageWeight(entries, startDate, day2)

        // Then returns 79.5 kg ((80.0 + 79.0) / 2, N = 2)
        assertNotNull(avgDay2)
        assertEquals(79.5, avgDay2!!, 1e-6)
    }

    @Test
    fun `issue 590 - scenario 3 - logging inactivity 7-day window gap fallback`() {
        // Given a cycle starting on 2026-09-01 with weigh-ins on 2026-09-01 (80.0 kg) and 2026-09-02 (79.8 kg)
        // And no weigh-ins between 2026-09-03 and 2026-09-15
        val startDate = LocalDate.of(2026, 9, 1)
        val entries = listOf(
            WeightEntry(date = startDate, weightKg = 80.0, updatedAtMillis = 1L),
            WeightEntry(date = LocalDate.of(2026, 9, 2), weightKg = 79.8, updatedAtMillis = 2L)
        )
        val referenceDate = LocalDate.of(2026, 9, 15)

        // When computeCurrent7DayAverageWeight is evaluated on 2026-09-15 (window [2026-09-09, 2026-09-15] is empty)
        val avg = FatLossAnalytics.computeCurrent7DayAverageWeight(entries, startDate, referenceDate)

        // Then returns 79.8 kg (falls back to latest entry within cycle)
        assertNotNull(avg)
        assertEquals(79.8, avg!!, 1e-6)
    }

    @Test
    fun `issue 590 - scenario 5 - soft deletions and pre-cycle entries ignored`() {
        // Given an entry on 2026-10-03 marked soft-deleted (deletedAtMillis != null) and an entry prior to cycleStartDate
        val startDate = LocalDate.of(2026, 10, 1)
        val referenceDate = LocalDate.of(2026, 10, 5)
        val entries = listOf(
            WeightEntry(date = LocalDate.of(2026, 9, 25), weightKg = 90.0, updatedAtMillis = 1L), // Pre-cycle
            WeightEntry(date = LocalDate.of(2026, 10, 1), weightKg = 80.0, updatedAtMillis = 2L),
            WeightEntry(date = LocalDate.of(2026, 10, 3), weightKg = 70.0, updatedAtMillis = 3L, deletedAtMillis = 123456L), // Soft-deleted
            WeightEntry(date = LocalDate.of(2026, 10, 5), weightKg = 78.0, updatedAtMillis = 4L),
            WeightEntry(date = LocalDate.of(2026, 10, 10), weightKg = 76.0, updatedAtMillis = 5L) // Future entry
        )

        // When computeCurrent7DayAverageWeight is evaluated
        val avg = FatLossAnalytics.computeCurrent7DayAverageWeight(entries, startDate, referenceDate)

        // Then both pre-cycle, soft-deleted, and future entries are completely excluded from the 7-day average calculation
        // Window [2026-09-29, 2026-10-05] only includes 2026-10-01 (80.0) and 2026-10-05 (78.0)
        assertNotNull(avg)
        assertEquals(79.0, avg!!, 1e-6)
    }

    @Test
    fun `issue 590 - scenario 6 - goal reached gating on 7-day average`() {
        // Given a target weight of 67.0 kg, an anchor entry on 2026-09-30 at 67.5 kg,
        // six daily entries at 67.5 kg, and a newest entry on day 7 at 66.8 kg
        // (7-day window [10-01, 10-07] average = (6 * 67.5 + 66.8) / 7 = 67.4 kg > 67.0 kg)
        val startDate = LocalDate.of(2026, 9, 30)
        val day7 = LocalDate.of(2026, 10, 7)
        val cycle = Cycle(
            id = 5906L,
            name = "Cut Gating",
            startDate = startDate,
            type = CycleType.FAT_LOSS_BODYBUILDING,
            startingWeightKg = 70.0,
            targetWeightKg = 67.0
        )
        val entries = mutableListOf<WeightEntry>(
            WeightEntry(date = startDate, weightKg = 67.5, updatedAtMillis = 0L)
        )
        for (day in 1..6) {
            entries.add(
                WeightEntry(date = LocalDate.of(2026, 10, day), weightKg = 67.5, updatedAtMillis = day.toLong())
            )
        }
        entries.add(
            WeightEntry(date = day7, weightKg = 66.8, updatedAtMillis = 7L)
        )

        val avg7d = FatLossAnalytics.computeCurrent7DayAverageWeight(entries, cycle, day7)
        assertNotNull(avg7d)
        assertEquals(67.4, avg7d!!, 1e-4)

        // When computePaceForecast is evaluated on day 7
        val forecastDay7 = FatLossAnalytics.computePaceForecast(entries, cycle, day7)

        // Then the forecast does NOT return GoalReached (since 7-day average 67.4 kg > 67.0 kg)
        assertFalse("Forecast must not be GoalReached while 7-day average > target", forecastDay7 is PaceForecast.GoalReached)
        assertTrue("Forecast should be Projected when velocity > 0 and 7d avg > target", forecastDay7 is PaceForecast.Projected)

        // When subsequent weigh-ins bring the 7-day average to <= 67.0 kg
        val matureEntries = (8..14).map { day ->
            WeightEntry(date = LocalDate.of(2026, 10, day), weightKg = 66.8, updatedAtMillis = day.toLong())
        }
        val allEntries = entries + matureEntries

        val avg7dDay14 = FatLossAnalytics.computeCurrent7DayAverageWeight(allEntries, cycle, LocalDate.of(2026, 10, 14))
        assertNotNull(avg7dDay14)
        assertTrue("7-day average must be <= 67.0 kg", avg7dDay14!! <= 67.0)

        // Then computePaceForecast returns PaceForecast.GoalReached
        val forecastDay14 = FatLossAnalytics.computePaceForecast(allEntries, cycle, LocalDate.of(2026, 10, 14))
        assertEquals(PaceForecast.GoalReached, forecastDay14)
    }

    // =========================================================================
    // Issue #592: Weight Progression Hero Redesign & Pace Analytics (Scenarios 2–15)
    // =========================================================================

    @Test
    fun `issue 592 - scenario 2 - consecutive daily weight drop and drop streak`() {
        Locale.setDefault(Locale.US)
        val cycleStartDate = LocalDate.of(2026, 10, 1)
        val referenceDate = LocalDate.of(2026, 10, 7)
        val entries = listOf(
            WeightEntry(date = LocalDate.of(2026, 10, 4), weightKg = 76.8, updatedAtMillis = 1L),
            WeightEntry(date = LocalDate.of(2026, 10, 5), weightKg = 76.5, updatedAtMillis = 2L),
            WeightEntry(date = LocalDate.of(2026, 10, 6), weightKg = 76.2, updatedAtMillis = 3L),
            WeightEntry(date = LocalDate.of(2026, 10, 7), weightKg = 76.0, updatedAtMillis = 4L)
        )

        val delta = FatLossAnalytics.computeDayOverDayDelta(entries, cycleStartDate, referenceDate)
        assertNotNull(delta)
        assertEquals(-0.2, delta!!.deltaKg, 1e-4)
        assertTrue(delta.isConsecutive)

        val streak = FatLossAnalytics.computeWeightDecreasingStreak(entries, cycleStartDate, referenceDate)
        assertEquals(3, streak)

        val subtitle = FatLossAnalytics.formatCurrentWeightSubtitle(delta, "kg", referenceDate)
        assertEquals("▼ -0.2 kg vs yesterday", subtitle)
    }

    @Test
    fun `issue 592 - scenario 3 - weight gain resets decreasing streak`() {
        Locale.setDefault(Locale.US)
        val cycleStartDate = LocalDate.of(2026, 10, 1)
        val referenceDate = LocalDate.of(2026, 10, 7)
        val entries = listOf(
            WeightEntry(date = LocalDate.of(2026, 10, 6), weightKg = 75.0, updatedAtMillis = 1L),
            WeightEntry(date = LocalDate.of(2026, 10, 7), weightKg = 76.5, updatedAtMillis = 2L)
        )

        val delta = FatLossAnalytics.computeDayOverDayDelta(entries, cycleStartDate, referenceDate)
        assertNotNull(delta)
        assertEquals(1.5, delta!!.deltaKg, 1e-4)
        assertTrue(delta.isConsecutive)

        val streak = FatLossAnalytics.computeWeightDecreasingStreak(entries, cycleStartDate, referenceDate)
        assertEquals(0, streak)

        val subtitle = FatLossAnalytics.formatCurrentWeightSubtitle(delta, "kg", referenceDate)
        assertEquals("▲ +1.5 kg vs yesterday", subtitle)
    }

    @Test
    fun `issue 592 - scenario 4 - non-consecutive weigh-in logging gap`() {
        Locale.setDefault(Locale.US)
        val cycleStartDate = LocalDate.of(2026, 10, 1)
        val referenceDate = LocalDate.of(2026, 10, 7)
        val entries = listOf(
            WeightEntry(date = LocalDate.of(2026, 10, 3), weightKg = 76.6, updatedAtMillis = 1L),
            WeightEntry(date = LocalDate.of(2026, 10, 7), weightKg = 76.0, updatedAtMillis = 2L)
        )

        val delta = FatLossAnalytics.computeDayOverDayDelta(entries, cycleStartDate, referenceDate)
        assertNotNull(delta)
        assertEquals(-0.6, delta!!.deltaKg, 1e-4)
        assertFalse(delta.isConsecutive)

        val streak = FatLossAnalytics.computeWeightDecreasingStreak(entries, cycleStartDate, referenceDate)
        assertEquals(0, streak)

        val subtitle = FatLossAnalytics.formatCurrentWeightSubtitle(delta, "kg", referenceDate)
        assertEquals("▼ -0.6 kg vs 3 Oct", subtitle)
    }

    @Test
    fun `issue 592 - scenario 5 - stale weigh-in date preservation`() {
        Locale.setDefault(Locale.US)
        val cycleStartDate = LocalDate.of(2026, 10, 1)
        val referenceDate = LocalDate.of(2026, 10, 7)
        val entries = listOf(
            WeightEntry(date = LocalDate.of(2026, 10, 4), weightKg = 76.5, updatedAtMillis = 1L),
            WeightEntry(date = LocalDate.of(2026, 10, 5), weightKg = 76.2, updatedAtMillis = 2L)
        )

        val delta = FatLossAnalytics.computeDayOverDayDelta(entries, cycleStartDate, referenceDate)
        assertNotNull(delta)
        assertEquals(-0.3, delta!!.deltaKg, 1e-4)

        val subtitle = FatLossAnalytics.formatCurrentWeightSubtitle(delta, "kg", referenceDate)
        assertEquals("5 Oct · ▼ -0.3 kg vs 4 Oct", subtitle)
    }

    @Test
    fun `issue 592 - scenario 6 - neutral delta within noise deadband`() {
        Locale.setDefault(Locale.US)
        val cycleStartDate = LocalDate.of(2026, 10, 1)
        val referenceDate = LocalDate.of(2026, 10, 7)
        val entries = listOf(
            WeightEntry(date = LocalDate.of(2026, 10, 6), weightKg = 76.02, updatedAtMillis = 1L),
            WeightEntry(date = LocalDate.of(2026, 10, 7), weightKg = 76.00, updatedAtMillis = 2L)
        )

        val streak = FatLossAnalytics.computeWeightDecreasingStreak(entries, cycleStartDate, referenceDate)
        assertEquals(0, streak)

        val delta = FatLossAnalytics.computeDayOverDayDelta(entries, cycleStartDate, referenceDate)
        val subtitle = FatLossAnalytics.formatCurrentWeightSubtitle(delta, "kg", referenceDate)
        assertEquals("0.0 kg vs yesterday", subtitle)
    }

    @Test
    fun `issue 592 - scenario 7 - 7-day pace accelerating deficit`() {
        Locale.setDefault(Locale.US)
        val comparison = FatLossAnalytics.PaceComparisonResult(
            currentRateKgPerWeek = -0.8,
            priorRateKgPerWeek = -0.7,
            deltaRateKgPerWeek = -0.1,
            percentChange = 14.2857,
            isAcceleratingDeficit = true,
            priorDate = LocalDate.of(2026, 10, 6),
            isConsecutive = true
        )
        assertEquals(14.3, comparison.percentChange!!, 0.1)
        assertTrue(comparison.isAcceleratingDeficit)

        val subtitle = FatLossAnalytics.format7DayPaceSubtitle(comparison, "kg", LocalDate.of(2026, 10, 7))
        assertEquals("14% faster loss vs yesterday", subtitle)
    }

    @Test
    fun `issue 594 - scenario 2 - 7-day pace decelerating deficit reproduction`() {
        Locale.setDefault(Locale.US)
        val comparison = FatLossAnalytics.PaceComparisonResult(
            currentRateKgPerWeek = -0.80,
            priorRateKgPerWeek = -0.91,
            deltaRateKgPerWeek = 0.11,
            percentChange = -12.08,
            isAcceleratingDeficit = false,
            priorDate = LocalDate.of(2026, 10, 6),
            isConsecutive = true
        )
        val subtitle = FatLossAnalytics.format7DayPaceSubtitle(comparison, "kg", LocalDate.of(2026, 10, 7))
        assertEquals("12% slower loss vs yesterday", subtitle)
    }

    @Test
    fun `issue 594 - scenario 3 - 7-day pace accelerating deficit`() {
        Locale.setDefault(Locale.US)
        val comparison = FatLossAnalytics.PaceComparisonResult(
            currentRateKgPerWeek = -0.80,
            priorRateKgPerWeek = -0.70,
            deltaRateKgPerWeek = -0.10,
            percentChange = 14.28,
            isAcceleratingDeficit = true,
            priorDate = LocalDate.of(2026, 10, 6),
            isConsecutive = true
        )
        val subtitle = FatLossAnalytics.format7DayPaceSubtitle(comparison, "kg", LocalDate.of(2026, 10, 7))
        assertEquals("14% faster loss vs yesterday", subtitle)
    }

    @Test
    fun `issue 592 - scenario 8 - 7-day pace decelerating deficit`() {
        Locale.setDefault(Locale.US)
        val comparison = FatLossAnalytics.PaceComparisonResult(
            currentRateKgPerWeek = -0.6,
            priorRateKgPerWeek = -0.8,
            deltaRateKgPerWeek = 0.2,
            percentChange = -25.0,
            isAcceleratingDeficit = false,
            priorDate = LocalDate.of(2026, 10, 6),
            isConsecutive = true
        )
        assertEquals(-25.0, comparison.percentChange!!, 0.1)
        assertFalse(comparison.isAcceleratingDeficit)

        val subtitle = FatLossAnalytics.format7DayPaceSubtitle(comparison, "kg", LocalDate.of(2026, 10, 7))
        assertEquals("25% slower loss vs yesterday", subtitle)
    }

    @Test
    fun `issue 592 - scenario 9a - 7-day pace unchanged within noise threshold`() {
        Locale.setDefault(Locale.US)
        val comparison = FatLossAnalytics.PaceComparisonResult(
            currentRateKgPerWeek = -0.72,
            priorRateKgPerWeek = -0.70,
            deltaRateKgPerWeek = -0.02,
            percentChange = 2.857,
            isAcceleratingDeficit = true,
            priorDate = LocalDate.of(2026, 10, 6),
            isConsecutive = true
        )
        val subtitle = FatLossAnalytics.format7DayPaceSubtitle(comparison, "kg", LocalDate.of(2026, 10, 7))
        assertEquals("Pace unchanged", subtitle)
    }

    @Test
    fun `issue 592 - scenario 9b - 7-day pace with stale weigh-in date`() {
        Locale.setDefault(Locale.US)
        val comparison = FatLossAnalytics.PaceComparisonResult(
            currentRateKgPerWeek = -0.8,
            priorRateKgPerWeek = -0.6,
            deltaRateKgPerWeek = -0.2,
            percentChange = 33.333,
            isAcceleratingDeficit = true,
            priorDate = LocalDate.of(2026, 10, 5),
            isConsecutive = true
        )
        assertEquals(33.3, comparison.percentChange!!, 0.1)
        assertTrue(comparison.isAcceleratingDeficit)

        val subtitle = FatLossAnalytics.format7DayPaceSubtitle(comparison, "kg", LocalDate.of(2026, 10, 7))
        assertEquals("33% faster loss vs 5 Oct", subtitle)
    }

    @Test
    fun `issue 592 - scenario 10 - 7-day pace near-zero baseline guardrail`() {
        Locale.setDefault(Locale.US)
        val comparison = FatLossAnalytics.PaceComparisonResult(
            currentRateKgPerWeek = -0.25,
            priorRateKgPerWeek = -0.05,
            deltaRateKgPerWeek = -0.20,
            percentChange = null,
            isAcceleratingDeficit = true,
            priorDate = LocalDate.of(2026, 10, 6),
            isConsecutive = true
        )
        assertNull(comparison.percentChange)

        val subtitle = FatLossAnalytics.format7DayPaceSubtitle(comparison, "kg", LocalDate.of(2026, 10, 7))
        assertEquals("-0.2 kg/wk vs yesterday", subtitle)
    }

    @Test
    fun `issue 592 - scenario 11 - 7-day pace weight gain trajectory`() {
        Locale.setDefault(Locale.US)
        val comparison = FatLossAnalytics.PaceComparisonResult(
            currentRateKgPerWeek = 0.8,
            priorRateKgPerWeek = 0.5,
            deltaRateKgPerWeek = 0.3,
            percentChange = null,
            isAcceleratingDeficit = false,
            priorDate = LocalDate.of(2026, 10, 6),
            isConsecutive = true
        )
        assertNull(comparison.percentChange)
        assertFalse(comparison.isAcceleratingDeficit)

        val subtitle = FatLossAnalytics.format7DayPaceSubtitle(comparison, "kg", LocalDate.of(2026, 10, 7))
        assertEquals("+0.3 kg/wk vs yesterday", subtitle)
    }

    @Test
    fun `issue 592 - scenario 12a - single weigh-in cold start`() {
        Locale.setDefault(Locale.US)
        val cycleStartDate = LocalDate.of(2026, 10, 1)
        val entries = listOf(
            WeightEntry(date = LocalDate.of(2026, 10, 1), weightKg = 78.0, updatedAtMillis = 1L)
        )

        val delta = FatLossAnalytics.computeDayOverDayDelta(entries, cycleStartDate)
        assertNull(delta)

        val streak = FatLossAnalytics.computeWeightDecreasingStreak(entries, cycleStartDate)
        assertEquals(0, streak)

        val currentSubtitle = FatLossAnalytics.formatCurrentWeightSubtitle(delta, "kg")
        assertEquals("First weigh-in", currentSubtitle)

        val paceComparison = FatLossAnalytics.computePaceComparisonVsLastWeek(entries, cycleStartDate, 7)
        assertNull(paceComparison)

        val paceSubtitle = FatLossAnalytics.format7DayPaceSubtitle(paceComparison, "kg")
        assertEquals("Baseline 7d pace", paceSubtitle)
    }

    @Test
    fun `issue 592 - scenario 12b - initial week elapsed history under 7 days`() {
        Locale.setDefault(Locale.US)
        val cycleStartDate = LocalDate.of(2026, 10, 1)
        val day2 = LocalDate.of(2026, 10, 2)
        val entries = listOf(
            WeightEntry(date = LocalDate.of(2026, 10, 1), weightKg = 76.5, updatedAtMillis = 1L),
            WeightEntry(date = day2, weightKg = 76.2, updatedAtMillis = 2L)
        )

        val delta = FatLossAnalytics.computeDayOverDayDelta(entries, cycleStartDate, day2)
        assertNotNull(delta)
        assertEquals(-0.3, delta!!.deltaKg, 1e-4)
        assertTrue(delta.isConsecutive)

        val currentSubtitle = FatLossAnalytics.formatCurrentWeightSubtitle(delta, "kg", day2)
        assertEquals("▼ -0.3 kg vs yesterday", currentSubtitle)

        val paceComparison = FatLossAnalytics.computePaceComparisonVsLastWeek(entries, cycleStartDate, 7, day2)
        assertNull("Pace comparison is null because 7d rolling pace requires >= 7 elapsed days", paceComparison)

        val paceSubtitle = FatLossAnalytics.format7DayPaceSubtitle(paceComparison, "kg", day2)
        assertEquals("Baseline 7d pace", paceSubtitle)
    }

    @Test
    fun `issue 592 - scenario 13 - stale drop streak reset`() {
        Locale.setDefault(Locale.US)
        val cycleStartDate = LocalDate.of(2026, 10, 1)
        val entries = listOf(
            WeightEntry(date = LocalDate.of(2026, 10, 3), weightKg = 76.8, updatedAtMillis = 1L),
            WeightEntry(date = LocalDate.of(2026, 10, 4), weightKg = 76.5, updatedAtMillis = 2L),
            WeightEntry(date = LocalDate.of(2026, 10, 5), weightKg = 76.2, updatedAtMillis = 3L)
        )

        // Evaluated on 2026-10-07: stale because latest entry (10-05) is older than yesterday (10-06)
        val streakOct7 = FatLossAnalytics.computeWeightDecreasingStreak(entries, cycleStartDate, LocalDate.of(2026, 10, 7))
        assertEquals(0, streakOct7)

        // Evaluated on 2026-10-06: active because latest entry (10-05) is yesterday relative to 10-06
        val streakOct6 = FatLossAnalytics.computeWeightDecreasingStreak(entries, cycleStartDate, LocalDate.of(2026, 10, 6))
        assertEquals(2, streakOct6)
    }

    @Test
    fun `issue 592 - scenario 14 - slowing weight gain deficit acceleration guard`() {
        Locale.setDefault(Locale.US)
        val comparison = FatLossAnalytics.PaceComparisonResult(
            currentRateKgPerWeek = 0.2,
            priorRateKgPerWeek = 0.5,
            deltaRateKgPerWeek = -0.3,
            percentChange = null,
            isAcceleratingDeficit = false,
            priorDate = LocalDate.of(2026, 10, 6),
            isConsecutive = true
        )
        assertNull(comparison.percentChange)
        assertFalse("Slowing gain is not accelerating deficit", comparison.isAcceleratingDeficit)

        val subtitle = FatLossAnalytics.format7DayPaceSubtitle(comparison, "kg", LocalDate.of(2026, 10, 7))
        assertEquals("-0.3 kg/wk vs yesterday", subtitle)
    }

    @Test
    fun `issue 592 - scenario 15 - 14-day pace anchor window boundary`() {
        val cycleStartDate = LocalDate.of(2026, 10, 1)
        // Daily weigh-ins logged from Oct 1 to Oct 14 (14 entries spanning 13 days)
        val entries14 = (1..14).map { day ->
            WeightEntry(
                date = LocalDate.of(2026, 10, day),
                weightKg = 80.0 - (day * 0.1),
                updatedAtMillis = day.toLong()
            )
        }

        // On Oct 14: cutoff is Oct 14 - 14 = Sep 30, which is pre-cycle -> null anchor
        val paceOct14 = FatLossAnalytics.compute14DayPace(entries14, cycleStartDate, LocalDate.of(2026, 10, 14))
        assertNull("compute14DayPace must return null when anchor 14 days ago is before cycleStartDate", paceOct14)

        // On Oct 15: 15th entry spanning 14 days from Oct 1
        val entries15 = entries14 + WeightEntry(
            date = LocalDate.of(2026, 10, 15),
            weightKg = 78.5,
            updatedAtMillis = 15L
        )
        val paceOct15 = FatLossAnalytics.compute14DayPace(entries15, cycleStartDate, LocalDate.of(2026, 10, 15))
        assertNotNull("compute14DayPace returns PaceRateResult when span >= 14 days", paceOct15)
        assertTrue(paceOct15!!.formatDisplay("kg").endsWith("kg/wk"))
    }

    @Test
    fun `issue 592 - boundary test - drop streak 1 increments streak while pill hidden in UI`() {
        val cycleStartDate = LocalDate.of(2026, 10, 1)
        val referenceDate = LocalDate.of(2026, 10, 7)
        val entries = listOf(
            WeightEntry(date = LocalDate.of(2026, 10, 6), weightKg = 76.5, updatedAtMillis = 1L),
            WeightEntry(date = LocalDate.of(2026, 10, 7), weightKg = 76.2, updatedAtMillis = 2L)
        )

        val streak = FatLossAnalytics.computeWeightDecreasingStreak(entries, cycleStartDate, referenceDate)
        assertEquals(1, streak)
        // In UI, streak pill condition requires dropStreakDays >= 2, so streak == 1 is correctly hidden
        assertFalse(streak >= 2)
    }

    @Test
    fun `issue 592 - end-to-end compute7DayPaceComparison with entries`() {
        val cycleStartDate = LocalDate.of(2026, 9, 22)
        val entries = listOf(
            WeightEntry(date = LocalDate.of(2026, 9, 23), weightKg = 80.7, updatedAtMillis = 1L),
            WeightEntry(date = LocalDate.of(2026, 9, 30), weightKg = 80.0, updatedAtMillis = 2L),
            WeightEntry(date = LocalDate.of(2026, 10, 6), weightKg = 79.3, updatedAtMillis = 3L),
            WeightEntry(date = LocalDate.of(2026, 10, 7), weightKg = 79.1, updatedAtMillis = 4L)
        )

        val comp = FatLossAnalytics.computePaceComparisonVsLastWeek(entries, cycleStartDate, 7, LocalDate.of(2026, 10, 7))
        assertNotNull(comp)
        assertEquals(-0.8, comp!!.currentRateKgPerWeek, 1e-4)
        assertEquals(-0.7, comp.priorRateKgPerWeek, 1e-4)
        assertEquals(-0.1, comp.deltaRateKgPerWeek, 1e-4)
        assertEquals(14.3, comp.percentChange!!, 0.1)
        assertTrue(comp.isAcceleratingDeficit)
        assertTrue(comp.isExactDayMatch)

        val subtitle = FatLossAnalytics.format7DayPaceSubtitle(comp, "kg", LocalDate.of(2026, 10, 7))
        assertEquals("14% faster loss vs last Wed", subtitle)
    }

    // =========================================================================
    // Issue #596: 7-Day Pace Targets for Daily Expected Weight & Tomorrow Target
    // =========================================================================

    @Test
    fun `issue 596 - scenario 1 - normal active progress weigh-in-only fixture`() {
        val cycleStartDate = LocalDate.of(2026, 9, 28)
        val t = LocalDate.of(2026, 10, 7)
        // D(t) = DAYS.between(Sep 28, Oct 7) = 9
        // W_start = 78.5 kg
        // W_(t-7d) = 77.3 kg (2026-09-30)
        // W_(t-6d) = 76.9 kg (2026-10-01)
        // W_today = 76.5 kg
        // W_expected,today = 77.3 + (77.3 - 78.5)/9 = 77.3 - 0.1333... = 77.1667 -> 77.2 kg
        // Delta expected = 76.5 - 77.2 = -0.7 kg (actual < expected -> AHEAD)
        // W_target,tomorrow = 76.9 + (76.9 - 78.5)/9 = 76.9 - 0.1777... = 76.7222 -> 76.7 kg
        // Actual today (76.5) <= Target tomorrow (76.7) -> AlreadyBelowTarget (margin = 76.7 - 76.5 = 0.2 kg)
        val entries = listOf(
            WeightEntry(date = LocalDate.of(2026, 9, 29), weightKg = 75.91, updatedAtMillis = 1L),
            WeightEntry(date = LocalDate.of(2026, 9, 30), weightKg = 77.3, updatedAtMillis = 2L),
            WeightEntry(date = LocalDate.of(2026, 10, 1), weightKg = 76.9, updatedAtMillis = 3L),
            WeightEntry(date = LocalDate.of(2026, 10, 6), weightKg = 75.0, updatedAtMillis = 4L),
            WeightEntry(date = LocalDate.of(2026, 10, 7), weightKg = 76.5, updatedAtMillis = 5L)
        )

        val state = FatLossAnalytics.compute7DayPaceTargets(entries, cycleStartDate, t, "kg", startingWeightKg = 78.5)
        assertTrue("State must be Available", state is PaceTargetsState.Available)
        val avail = state as PaceTargetsState.Available

        assertTrue("today must be Value", avail.today is ColumnState.Value)
        val todayVal = avail.today as ColumnState.Value
        assertEquals(77.2, todayVal.targetWeightDisplay, 1e-4)
        assertEquals(-0.7, todayVal.deltaDisplay!!, 1e-4)
        assertEquals(PaceTargetStatus.AHEAD, todayVal.status)
        assertEquals("kg", todayVal.unitLabel)

        assertTrue("tomorrow must be AlreadyBelowTarget", avail.tomorrow is ColumnState.AlreadyBelowTarget)
        val tomorrowVal = avail.tomorrow as ColumnState.AlreadyBelowTarget
        assertEquals(76.7, tomorrowVal.targetWeightDisplay, 1e-4)
        assertEquals(0.2, tomorrowVal.marginDisplay, 1e-4)
        assertEquals("kg", tomorrowVal.unitLabel)
    }

    @Test
    fun `issue 596 - scenario 2 - missing t minus 7d baseline log`() {
        val cycleStartDate = LocalDate.of(2026, 9, 28)
        val t = LocalDate.of(2026, 10, 7)
        val entries = listOf(
            WeightEntry(date = LocalDate.of(2026, 9, 29), weightKg = 77.4, updatedAtMillis = 1L),
            WeightEntry(date = LocalDate.of(2026, 10, 1), weightKg = 76.9, updatedAtMillis = 2L),
            WeightEntry(date = LocalDate.of(2026, 10, 6), weightKg = 75.0, updatedAtMillis = 3L),
            WeightEntry(date = LocalDate.of(2026, 10, 7), weightKg = 76.5, updatedAtMillis = 4L)
        )

        val state = FatLossAnalytics.compute7DayPaceTargets(entries, cycleStartDate, t, "kg", startingWeightKg = 78.5)
        assertTrue("State must be Available", state is PaceTargetsState.Available)
        val avail = state as PaceTargetsState.Available

        assertTrue("today must be NeedsLog", avail.today is ColumnState.NeedsLog)
        val todayNeeds = avail.today as ColumnState.NeedsLog
        assertEquals(LocalDate.of(2026, 9, 30), todayNeeds.requiredDate)

        assertTrue("tomorrow must be AlreadyBelowTarget", avail.tomorrow is ColumnState.AlreadyBelowTarget)
        val tomorrowVal = avail.tomorrow as ColumnState.AlreadyBelowTarget
        assertEquals(76.7, tomorrowVal.targetWeightDisplay, 1e-4)
        assertEquals(0.2, tomorrowVal.marginDisplay, 1e-4)
        assertEquals("kg", tomorrowVal.unitLabel)
    }

    @Test
    fun `issue 596 - scenario 3 - missing t minus 6d log triggers tomorrow needs log`() {
        val cycleStartDate = LocalDate.of(2026, 9, 28)
        val t = LocalDate.of(2026, 10, 7)
        val entries = listOf(
            WeightEntry(date = LocalDate.of(2026, 9, 29), weightKg = 75.91, updatedAtMillis = 1L),
            WeightEntry(date = LocalDate.of(2026, 9, 30), weightKg = 77.3, updatedAtMillis = 2L),
            WeightEntry(date = LocalDate.of(2026, 10, 6), weightKg = 75.0, updatedAtMillis = 4L),
            WeightEntry(date = LocalDate.of(2026, 10, 7), weightKg = 76.5, updatedAtMillis = 5L)
        )

        val state = FatLossAnalytics.compute7DayPaceTargets(entries, cycleStartDate, t, "kg", startingWeightKg = 78.5)
        assertTrue("State must be Available", state is PaceTargetsState.Available)
        val avail = state as PaceTargetsState.Available

        assertTrue("tomorrow must be NeedsLog when t-6d is unlogged", avail.tomorrow is ColumnState.NeedsLog)
        val tomorrowNeeds = avail.tomorrow as ColumnState.NeedsLog
        assertEquals(LocalDate.of(2026, 10, 1), tomorrowNeeds.requiredDate)
    }

    @Test
    fun `issue 596 - scenario 4 - early cycle with under 7 days history returns insufficient history columns`() {
        val cycleStartDate = LocalDate.of(2026, 10, 1)
        val t = LocalDate.of(2026, 10, 4)
        val entries = (1..4).map { day ->
            WeightEntry(date = LocalDate.of(2026, 10, day), weightKg = 80.0 - day * 0.2, updatedAtMillis = day.toLong())
        }

        val state = FatLossAnalytics.compute7DayPaceTargets(entries, cycleStartDate, t, "kg", startingWeightKg = 80.0)
        assertTrue("State must be Available", state is PaceTargetsState.Available)
        val avail = state as PaceTargetsState.Available
        assertEquals(ColumnState.InsufficientHistory, avail.today)
        assertEquals(ColumnState.InsufficientHistory, avail.tomorrow)
    }

    @Test
    fun `issue 596 - scenario 5 - stalled or positive pace returns stalled`() {
        val cycleStartDate = LocalDate.of(2026, 9, 28)
        val t = LocalDate.of(2026, 10, 7)
        val entries = listOf(
            WeightEntry(date = LocalDate.of(2026, 9, 30), weightKg = 78.5, updatedAtMillis = 1L),
            WeightEntry(date = LocalDate.of(2026, 10, 7), weightKg = 78.6, updatedAtMillis = 2L)
        )

        // W_(t-7d) = 78.5 >= W_start (78.0) -> Stalled
        val state = FatLossAnalytics.compute7DayPaceTargets(entries, cycleStartDate, t, "kg", startingWeightKg = 78.0)
        assertEquals(PaceTargetsState.Stalled, state)
    }

    @Test
    fun `issue 596 - scenario 6 - no weigh-in logged today`() {
        val cycleStartDate = LocalDate.of(2026, 9, 28)
        val t = LocalDate.of(2026, 10, 7)
        val entries = listOf(
            WeightEntry(date = LocalDate.of(2026, 9, 29), weightKg = 75.91, updatedAtMillis = 1L),
            WeightEntry(date = LocalDate.of(2026, 9, 30), weightKg = 77.3, updatedAtMillis = 2L),
            WeightEntry(date = LocalDate.of(2026, 10, 1), weightKg = 76.9, updatedAtMillis = 3L),
            WeightEntry(date = LocalDate.of(2026, 10, 6), weightKg = 75.0, updatedAtMillis = 4L)
        )

        val state = FatLossAnalytics.compute7DayPaceTargets(entries, cycleStartDate, t, "kg", startingWeightKg = 78.5)
        assertTrue("State must be Available", state is PaceTargetsState.Available)
        val avail = state as PaceTargetsState.Available

        assertTrue(avail.today is ColumnState.Value)
        val todayVal = avail.today as ColumnState.Value
        assertEquals(77.2, todayVal.targetWeightDisplay, 1e-4)
        assertNull(todayVal.deltaDisplay)
        assertNull(todayVal.status)

        assertTrue(avail.tomorrow is ColumnState.Value)
        val tomorrowVal = avail.tomorrow as ColumnState.Value
        assertNull(tomorrowVal.deltaDisplay)

        val entriesWithoutTMinus6 = entries.filterNot { it.date == LocalDate.of(2026, 10, 1) }
        val stateWithoutTMinus6 = FatLossAnalytics.compute7DayPaceTargets(entriesWithoutTMinus6, cycleStartDate, t, "kg", startingWeightKg = 78.5)
        assertTrue(stateWithoutTMinus6 is PaceTargetsState.Available)
        val avail2 = stateWithoutTMinus6 as PaceTargetsState.Available
        assertTrue(avail2.tomorrow is ColumnState.NeedsLog)
        assertEquals(LocalDate.of(2026, 10, 1), (avail2.tomorrow as ColumnState.NeedsLog).requiredDate)
    }

    @Test
    fun `issue 596 - scenario 7 - boundary attainment actual less than target tomorrow`() {
        val cycleStartDate = LocalDate.of(2026, 9, 28)
        val t = LocalDate.of(2026, 10, 7)
        // W_start = 78.5
        // W_(t-6d) = 77.6 kg (2026-10-01)
        // W_target,tomorrow = 77.6 + (77.6 - 78.5)/9 = 77.6 - 0.1 = 77.5 kg
        // Actual today = 75.8 kg <= 77.5 kg -> AlreadyBelowTarget
        val entries = listOf(
            WeightEntry(date = LocalDate.of(2026, 9, 30), weightKg = 77.3, updatedAtMillis = 1L),
            WeightEntry(date = LocalDate.of(2026, 10, 1), weightKg = 77.6, updatedAtMillis = 2L),
            WeightEntry(date = LocalDate.of(2026, 10, 7), weightKg = 75.8, updatedAtMillis = 3L)
        )

        val state = FatLossAnalytics.compute7DayPaceTargets(entries, cycleStartDate, t, "kg", startingWeightKg = 78.5)
        assertTrue("State must be Available", state is PaceTargetsState.Available)
        val avail = state as PaceTargetsState.Available

        assertTrue("tomorrow must be AlreadyBelowTarget", avail.tomorrow is ColumnState.AlreadyBelowTarget)
        val tomorrowBelow = avail.tomorrow as ColumnState.AlreadyBelowTarget
        assertEquals(77.5, tomorrowBelow.targetWeightDisplay, 1e-4)
        assertEquals(1.7, tomorrowBelow.marginDisplay, 1e-4)
        assertEquals("kg", tomorrowBelow.unitLabel)
    }

    @Test
    fun `issue 596 - scenario 7b - rounded equality at target tomorrow overnight label hidden`() {
        val cycleStartDate = LocalDate.of(2026, 9, 28)
        val t = LocalDate.of(2026, 10, 7)
        // W_start = 78.5
        // W_(t-6d) = 76.6 kg -> W_target,tomorrow = 76.6 + (76.6 - 78.5)/9 = 76.6 - 0.211 = 76.388 -> 76.4 kg
        // W_today = 76.4 kg. actual == target -> Value with null deltaDisplay
        val entries = listOf(
            WeightEntry(date = LocalDate.of(2026, 9, 29), weightKg = 75.91, updatedAtMillis = 1L),
            WeightEntry(date = LocalDate.of(2026, 9, 30), weightKg = 77.3, updatedAtMillis = 2L),
            WeightEntry(date = LocalDate.of(2026, 10, 1), weightKg = 76.6, updatedAtMillis = 3L),
            WeightEntry(date = LocalDate.of(2026, 10, 6), weightKg = 75.0, updatedAtMillis = 4L),
            WeightEntry(date = LocalDate.of(2026, 10, 7), weightKg = 76.4, updatedAtMillis = 5L)
        )

        val state = FatLossAnalytics.compute7DayPaceTargets(entries, cycleStartDate, t, "kg", startingWeightKg = 78.5)
        assertTrue(state is PaceTargetsState.Available)
        val avail = state as PaceTargetsState.Available

        assertTrue(avail.tomorrow is ColumnState.Value)
        val tomorrowVal = avail.tomorrow as ColumnState.Value
        assertEquals(76.4, tomorrowVal.targetWeightDisplay, 1e-4)
        assertNull(tomorrowVal.deltaDisplay)
    }

    @Test
    fun `issue 596 - scenario 8 - exactly on pace rounded equality`() {
        val cycleStartDate = LocalDate.of(2026, 9, 28)
        val t = LocalDate.of(2026, 10, 7)
        // W_start = 78.5
        // W_(t-7d) = 77.3 kg -> W_expected,today = 77.3 + (77.3 - 78.5)/9 = 77.1667 -> 77.2 kg
        // W_today = 77.2 kg -> delta = 0.0 -> ON_PACE
        val entries = listOf(
            WeightEntry(date = LocalDate.of(2026, 9, 29), weightKg = 75.91, updatedAtMillis = 1L),
            WeightEntry(date = LocalDate.of(2026, 9, 30), weightKg = 77.3, updatedAtMillis = 2L),
            WeightEntry(date = LocalDate.of(2026, 10, 1), weightKg = 76.9, updatedAtMillis = 3L),
            WeightEntry(date = LocalDate.of(2026, 10, 6), weightKg = 75.0, updatedAtMillis = 4L),
            WeightEntry(date = LocalDate.of(2026, 10, 7), weightKg = 77.2, updatedAtMillis = 5L)
        )

        val state = FatLossAnalytics.compute7DayPaceTargets(entries, cycleStartDate, t, "kg", startingWeightKg = 78.5)
        assertTrue(state is PaceTargetsState.Available)
        val avail = state as PaceTargetsState.Available

        assertTrue(avail.today is ColumnState.Value)
        val todayVal = avail.today as ColumnState.Value
        assertEquals(77.2, todayVal.targetWeightDisplay, 1e-4)
        assertEquals(0.0, todayVal.deltaDisplay!!, 1e-4)
        assertEquals(PaceTargetStatus.ON_PACE, todayVal.status)
    }

    @Test
    fun `issue 596 - scenario 9 - imperial unit conversion lbs`() {
        val cycleStartDate = LocalDate.of(2026, 9, 28)
        val t = LocalDate.of(2026, 10, 7)
        val entries = listOf(
            WeightEntry(date = LocalDate.of(2026, 9, 29), weightKg = 75.91, updatedAtMillis = 1L),
            WeightEntry(date = LocalDate.of(2026, 9, 30), weightKg = 77.3, updatedAtMillis = 2L),
            WeightEntry(date = LocalDate.of(2026, 10, 1), weightKg = 76.9, updatedAtMillis = 3L),
            WeightEntry(date = LocalDate.of(2026, 10, 6), weightKg = 75.0, updatedAtMillis = 4L),
            WeightEntry(date = LocalDate.of(2026, 10, 7), weightKg = 76.5, updatedAtMillis = 5L)
        )

        val state = FatLossAnalytics.compute7DayPaceTargets(entries, cycleStartDate, t, "lbs", startingWeightKg = 78.5)
        assertTrue(state is PaceTargetsState.Available)
        val avail = state as PaceTargetsState.Available

        assertTrue(avail.today is ColumnState.Value)
        val todayVal = avail.today as ColumnState.Value
        assertEquals("lbs", todayVal.unitLabel)

        assertTrue(avail.tomorrow is ColumnState.AlreadyBelowTarget)
        val tomorrowVal = avail.tomorrow as ColumnState.AlreadyBelowTarget
        assertEquals("lbs", tomorrowVal.unitLabel)
    }

    @Test
    fun `issue 596 - scenario 10 - modifying todays logged weigh-in single entry invariant`() {
        val cycleStartDate = LocalDate.of(2026, 9, 28)
        val t = LocalDate.of(2026, 10, 7)
        val baseEntries = listOf(
            WeightEntry(date = LocalDate.of(2026, 9, 29), weightKg = 75.91, updatedAtMillis = 1L),
            WeightEntry(date = LocalDate.of(2026, 9, 30), weightKg = 77.3, updatedAtMillis = 2L),
            WeightEntry(date = LocalDate.of(2026, 10, 1), weightKg = 76.9, updatedAtMillis = 3L),
            WeightEntry(date = LocalDate.of(2026, 10, 6), weightKg = 75.0, updatedAtMillis = 4L)
        )

        val entriesV1 = baseEntries + WeightEntry(date = t, weightKg = 77.2, updatedAtMillis = 5L)
        val stateV1 = FatLossAnalytics.compute7DayPaceTargets(entriesV1, cycleStartDate, t, "kg", startingWeightKg = 78.5) as PaceTargetsState.Available
        val todayV1 = stateV1.today as ColumnState.Value
        assertEquals(77.2, todayV1.targetWeightDisplay, 1e-4)
        assertEquals(0.0, todayV1.deltaDisplay!!, 1e-4)
        assertEquals(PaceTargetStatus.ON_PACE, todayV1.status)

        val entriesV2 = baseEntries + WeightEntry(date = t, weightKg = 77.5, updatedAtMillis = 6L)
        val stateV2 = FatLossAnalytics.compute7DayPaceTargets(entriesV2, cycleStartDate, t, "kg", startingWeightKg = 78.5) as PaceTargetsState.Available
        val todayV2 = stateV2.today as ColumnState.Value
        assertEquals(77.2, todayV2.targetWeightDisplay, 1e-4)
        assertEquals(0.3, todayV2.deltaDisplay!!, 1e-4)
        assertEquals(PaceTargetStatus.OFF_PACE, todayV2.status)
    }

    @Test
    fun `issue 596 - scenario 11 - day 7 of cycle declining entries expected today insufficient history`() {
        val cycleStartDate = LocalDate.of(2026, 10, 1)
        val t = LocalDate.of(2026, 10, 7) // Day 6 after start (start is day 0)
        // Ensure startKg is strictly greater than entries so isStalled is false
        val entries = (0..6).map { offset ->
            WeightEntry(
                date = cycleStartDate.plusDays(offset.toLong()),
                weightKg = 80.0 - (offset * 0.2),
                updatedAtMillis = offset.toLong()
            )
        }

        // On Oct 7:
        // t - 7d = Sep 30 < cycleStartDate (Oct 1) -> InsufficientHistory
        // t - 6d = Oct 1 = cycleStartDate -> Value (entry at Oct 1 has 80.0 kg < 81.0 kg startingWeightKg)
        val state = FatLossAnalytics.compute7DayPaceTargets(entries, cycleStartDate, t, "kg", startingWeightKg = 81.0)
        assertTrue(state is PaceTargetsState.Available)
        val avail = state as PaceTargetsState.Available

        assertEquals(ColumnState.InsufficientHistory, avail.today)
        assertTrue(avail.tomorrow is ColumnState.AlreadyBelowTarget)
    }

    // =========================================================================
    // Issue #598: Display Last Week Baseline Weights in 7-Day Pace Targets
    // =========================================================================

    @Test
    fun `issue 598 - scenario a - normal active day with both entries logged`() {
        val cycleStartDate = LocalDate.of(2026, 9, 28)
        val t = LocalDate.of(2026, 10, 7)
        val entries = listOf(
            WeightEntry(date = LocalDate.of(2026, 9, 29), weightKg = 75.9, updatedAtMillis = 1L),
            WeightEntry(date = LocalDate.of(2026, 9, 30), weightKg = 77.3, updatedAtMillis = 2L),
            WeightEntry(date = LocalDate.of(2026, 10, 1), weightKg = 75.3, updatedAtMillis = 3L),
            WeightEntry(date = LocalDate.of(2026, 10, 6), weightKg = 75.0, updatedAtMillis = 4L),
            WeightEntry(date = LocalDate.of(2026, 10, 7), weightKg = 76.5, updatedAtMillis = 5L)
        )

        val state = FatLossAnalytics.compute7DayPaceTargets(entries, cycleStartDate, t, "kg", startingWeightKg = 78.5)
        assertTrue("State must be Available", state is PaceTargetsState.Available)
        val avail = state as PaceTargetsState.Available

        assertTrue("today must be Value", avail.today is ColumnState.Value)
        val todayVal = avail.today as ColumnState.Value
        assertEquals(77.2, todayVal.targetWeightDisplay, 1e-4)
        assertEquals(-0.7, todayVal.deltaDisplay!!, 1e-4)
        assertEquals(PaceTargetStatus.AHEAD, todayVal.status)
        assertEquals("kg", todayVal.unitLabel)
        assertEquals(77.3, todayVal.baselineWeightDisplay!!, 1e-4)

        assertTrue("tomorrow must be Value", avail.tomorrow is ColumnState.Value)
        val tomorrowVal = avail.tomorrow as ColumnState.Value
        // W_(t-6d) = 75.3. W_target,tomorrow = 75.3 + (75.3 - 78.5)/9 = 75.3 - 0.355... = 74.944 -> 74.9 kg
        assertEquals(74.9, tomorrowVal.targetWeightDisplay, 1e-4)
        // delta overnight = 74.9 - 76.5 = -1.6 kg
        assertEquals(-1.6, tomorrowVal.deltaDisplay!!, 1e-4)
        assertNull(tomorrowVal.status)
        assertEquals("kg", tomorrowVal.unitLabel)
        assertEquals(75.3, tomorrowVal.baselineWeightDisplay!!, 1e-4)
    }

    @Test
    fun `issue 598 - scenario b - today unlogged retains today baseline`() {
        val cycleStartDate = LocalDate.of(2026, 9, 28)
        val t = LocalDate.of(2026, 10, 7)
        val entries = listOf(
            WeightEntry(date = LocalDate.of(2026, 9, 29), weightKg = 75.9, updatedAtMillis = 1L),
            WeightEntry(date = LocalDate.of(2026, 9, 30), weightKg = 77.3, updatedAtMillis = 2L),
            WeightEntry(date = LocalDate.of(2026, 10, 1), weightKg = 75.3, updatedAtMillis = 3L),
            WeightEntry(date = LocalDate.of(2026, 10, 6), weightKg = 75.0, updatedAtMillis = 4L)
        )

        val state = FatLossAnalytics.compute7DayPaceTargets(entries, cycleStartDate, t, "kg", startingWeightKg = 78.5)
        assertTrue(state is PaceTargetsState.Available)
        val avail = state as PaceTargetsState.Available

        assertTrue(avail.today is ColumnState.Value)
        val todayVal = avail.today as ColumnState.Value
        assertEquals(77.2, todayVal.targetWeightDisplay, 1e-4)
        assertNull(todayVal.deltaDisplay)
        assertNull(todayVal.status)
        assertEquals("kg", todayVal.unitLabel)
        assertEquals(77.3, todayVal.baselineWeightDisplay!!, 1e-4)
    }

    @Test
    fun `issue 598 - scenario c and c2 - tomorrow on anchor path with and without today logged`() {
        val cycleStartDate = LocalDate.of(2026, 9, 28)
        val t = LocalDate.of(2026, 10, 7)
        val entriesWithToday = listOf(
            WeightEntry(date = LocalDate.of(2026, 9, 29), weightKg = 75.9, updatedAtMillis = 1L),
            WeightEntry(date = LocalDate.of(2026, 9, 30), weightKg = 77.3, updatedAtMillis = 2L),
            WeightEntry(date = LocalDate.of(2026, 10, 1), weightKg = 75.3, updatedAtMillis = 3L),
            WeightEntry(date = LocalDate.of(2026, 10, 6), weightKg = 75.1, updatedAtMillis = 4L),
            WeightEntry(date = LocalDate.of(2026, 10, 7), weightKg = 76.5, updatedAtMillis = 5L)
        )
        val stateC = FatLossAnalytics.compute7DayPaceTargets(entriesWithToday, cycleStartDate, t, "kg", startingWeightKg = 78.5) as PaceTargetsState.Available
        val tomorrowC = stateC.tomorrow as ColumnState.Value
        assertEquals(74.9, tomorrowC.targetWeightDisplay, 1e-4)
        assertEquals(-1.6, tomorrowC.deltaDisplay!!, 1e-4)
        assertEquals("kg", tomorrowC.unitLabel)
        assertEquals(75.3, tomorrowC.baselineWeightDisplay!!, 1e-4)

        val entriesWithoutToday = entriesWithToday.filterNot { it.date == t }
        val stateC2 = FatLossAnalytics.compute7DayPaceTargets(entriesWithoutToday, cycleStartDate, t, "kg", startingWeightKg = 78.5) as PaceTargetsState.Available
        val tomorrowC2 = stateC2.tomorrow as ColumnState.Value
        assertEquals(74.9, tomorrowC2.targetWeightDisplay, 1e-4)
        assertNull(tomorrowC2.deltaDisplay)
        assertNull(tomorrowC2.status)
        assertEquals("kg", tomorrowC2.unitLabel)
        assertEquals(75.3, tomorrowC2.baselineWeightDisplay!!, 1e-4)
    }

    @Test
    fun `issue 598 - scenario d - tomorrow AlreadyBelowTarget on anchor path retains baseline`() {
        val cycleStartDate = LocalDate.of(2026, 9, 28)
        val t = LocalDate.of(2026, 10, 7)
        // W_target,tomorrow = 74.944 -> 74.9 kg
        // Actual 74.0 <= 74.9 -> AlreadyBelowTarget
        val entries = listOf(
            WeightEntry(date = LocalDate.of(2026, 9, 29), weightKg = 75.9, updatedAtMillis = 1L),
            WeightEntry(date = LocalDate.of(2026, 9, 30), weightKg = 77.3, updatedAtMillis = 2L),
            WeightEntry(date = LocalDate.of(2026, 10, 1), weightKg = 75.3, updatedAtMillis = 3L),
            WeightEntry(date = LocalDate.of(2026, 10, 6), weightKg = 75.0, updatedAtMillis = 4L),
            WeightEntry(date = LocalDate.of(2026, 10, 7), weightKg = 74.0, updatedAtMillis = 5L)
        )

        val state = FatLossAnalytics.compute7DayPaceTargets(entries, cycleStartDate, t, "kg", startingWeightKg = 78.5)
        assertTrue(state is PaceTargetsState.Available)
        val avail = state as PaceTargetsState.Available

        assertTrue("tomorrow must be AlreadyBelowTarget", avail.tomorrow is ColumnState.AlreadyBelowTarget)
        val tomorrowBelow = avail.tomorrow as ColumnState.AlreadyBelowTarget
        assertEquals(74.9, tomorrowBelow.targetWeightDisplay, 1e-4)
        assertEquals(0.9, tomorrowBelow.marginDisplay, 1e-4)
        assertEquals("kg", tomorrowBelow.unitLabel)
        assertEquals(75.3, tomorrowBelow.baselineWeightDisplay!!, 1e-4)
    }

    @Test
    fun `issue 598 - scenario e - missing t minus 6d returns needs log`() {
        val cycleStartDate = LocalDate.of(2026, 9, 28)
        val t = LocalDate.of(2026, 10, 7)
        val entries = listOf(
            WeightEntry(date = LocalDate.of(2026, 9, 29), weightKg = 75.9, updatedAtMillis = 1L),
            WeightEntry(date = LocalDate.of(2026, 9, 30), weightKg = 77.3, updatedAtMillis = 2L),
            // 2026-10-01 omitted
            WeightEntry(date = LocalDate.of(2026, 10, 6), weightKg = 75.0, updatedAtMillis = 4L),
            WeightEntry(date = LocalDate.of(2026, 10, 7), weightKg = 76.5, updatedAtMillis = 5L)
        )

        val stateE = FatLossAnalytics.compute7DayPaceTargets(entries, cycleStartDate, t, "kg", startingWeightKg = 78.5) as PaceTargetsState.Available
        assertTrue(stateE.tomorrow is ColumnState.NeedsLog)
        assertEquals(LocalDate.of(2026, 10, 1), (stateE.tomorrow as ColumnState.NeedsLog).requiredDate)
    }

    @Test
    fun `issue 598 - scenario f - imperial unit localization converts baseline to lbs`() {
        val cycleStartDate = LocalDate.of(2026, 9, 28)
        val t = LocalDate.of(2026, 10, 7)
        val entries = listOf(
            WeightEntry(date = LocalDate.of(2026, 9, 29), weightKg = 75.9, updatedAtMillis = 1L),
            WeightEntry(date = LocalDate.of(2026, 9, 30), weightKg = 77.3, updatedAtMillis = 2L),
            WeightEntry(date = LocalDate.of(2026, 10, 1), weightKg = 75.3, updatedAtMillis = 3L),
            WeightEntry(date = LocalDate.of(2026, 10, 6), weightKg = 75.0, updatedAtMillis = 4L),
            WeightEntry(date = LocalDate.of(2026, 10, 7), weightKg = 76.5, updatedAtMillis = 5L)
        )

        val state = FatLossAnalytics.compute7DayPaceTargets(entries, cycleStartDate, t, "lbs", startingWeightKg = 78.5) as PaceTargetsState.Available
        val todayVal = state.today as ColumnState.Value
        val tomorrowVal = state.tomorrow as ColumnState.Value

        assertEquals(170.4, todayVal.baselineWeightDisplay!!, 1e-4)
        assertEquals("lbs", todayVal.unitLabel)
        assertEquals(166.0, tomorrowVal.baselineWeightDisplay!!, 1e-4)
        assertEquals("lbs", tomorrowVal.unitLabel)
    }

    @Test
    fun `issue 598 - scenario g - non-value states carry no baseline`() {
        val cycleStartDate = LocalDate.of(2026, 10, 1)
        val t = LocalDate.of(2026, 10, 4)
        val emptyEntries = emptyList<WeightEntry>()
        val unavail = FatLossAnalytics.compute7DayPaceTargets(emptyEntries, cycleStartDate, t, "kg")
        assertEquals(PaceTargetsState.Unavailable, unavail)

        val entriesStalled = listOf(
            WeightEntry(date = LocalDate.of(2026, 9, 30), weightKg = 78.5, updatedAtMillis = 1L),
            WeightEntry(date = LocalDate.of(2026, 10, 7), weightKg = 78.2, updatedAtMillis = 2L)
        )
        val stalled = FatLossAnalytics.compute7DayPaceTargets(entriesStalled, LocalDate.of(2026, 9, 28), LocalDate.of(2026, 10, 7), "kg", startingWeightKg = 78.0)
        assertEquals(PaceTargetsState.Stalled, stalled)

        val reqDate = LocalDate.of(2026, 9, 30)
        val needsLogState = ColumnState.NeedsLog(requiredDate = reqDate)
        assertEquals(reqDate, needsLogState.requiredDate)
        assertEquals(ColumnState.InsufficientHistory, ColumnState.InsufficientHistory)
    }

    // =========================================================================
    // Issue #602: Responsive Weight Progression Hero & Monotonic Cycle Pace Targets
    // =========================================================================

    @Test
    fun `issue 602 - rolling weight hero computes 4 windows and weekly deltas`() {
        val cycleStartDate = LocalDate.of(2026, 9, 28)
        val t = LocalDate.of(2026, 10, 14) // Day 16
        // Today 7D: [Oct 8, Oct 14]
        // Last Week 7D: [Oct 1, Oct 7]
        // Today 14D: [Oct 1, Oct 14]
        // Last Week 14D: [Sep 24, Oct 7] -> in-cycle entries are from [Sep 28, Oct 7]
        val entries = (0..16).map { offset ->
            val date = cycleStartDate.plusDays(offset.toLong())
            WeightEntry(date = date, weightKg = 80.0 - offset * 0.1, updatedAtMillis = offset.toLong())
        }

        val hero = FatLossAnalytics.computeRollingWeightHero(entries, cycleStartDate, t)
        assertNotNull(hero.today7dAvgKg)
        assertNotNull(hero.lastWeek7dAvgKg)
        assertNotNull(hero.today14dAvgKg)
        assertNotNull(hero.delta7dKg)
        assertNotNull(hero.delta14dKg)
        assertEquals(14, hero.loggedDaysCount14d)

        // Monotonic decline: weekly deltas must be negative (weight loss)
        assertTrue(hero.delta7dKg!! < 0.0)
        assertTrue(hero.delta14dKg!! < 0.0)
    }

    @Test
    fun `issue 602 - monotonic pace targets prevent short-term inversion`() {
        val cycleStartDate = LocalDate.of(2026, 9, 28)
        val t = LocalDate.of(2026, 10, 7) // Day 9
        // Athlete is on track around 76.5 kg from 78.5 kg start.
        // Even with a recent short-term spike, target expected today should be monotonic relative to cycle start.
        val entries = listOf(
            WeightEntry(date = LocalDate.of(2026, 9, 30), weightKg = 77.3, updatedAtMillis = 1L),
            WeightEntry(date = LocalDate.of(2026, 10, 1), weightKg = 76.9, updatedAtMillis = 2L),
            WeightEntry(date = LocalDate.of(2026, 10, 7), weightKg = 76.5, updatedAtMillis = 3L)
        )

        val state = FatLossAnalytics.compute7DayPaceTargets(entries, cycleStartDate, t, "kg", startingWeightKg = 78.5)
        assertTrue(state is PaceTargetsState.Available)
        val avail = state as PaceTargetsState.Available
        val todayVal = avail.today as ColumnState.Value
        // Expected target should be lower than baseline (77.3)
        assertTrue(todayVal.targetWeightDisplay < 77.3)
        assertEquals(77.2, todayVal.targetWeightDisplay, 1e-4)
    }

    // =========================================================================
    // Issue #600: 3-Pillar Current Weight Row & Same-Weekday Pace Comparisons
    // =========================================================================

    @Test
    fun `issue 600 - scenario 1 - cycle day 1 start progressive average and pace subtitle baseline`() {
        Locale.setDefault(Locale.US)
        val today = LocalDate.of(2026, 10, 13) // Tuesday
        val cycleStartDate = today
        val entries = listOf(
            WeightEntry(date = today, weightKg = 77.8, updatedAtMillis = 1L)
        )

        // computeProgressiveAverage on day 1
        val progAvg = FatLossAnalytics.computeProgressiveAverage(entries, cycleStartDate, today)
        assertNotNull(progAvg)
        assertEquals(77.8, progAvg!!.averageKg, 1e-4)
        assertEquals(1, progAvg.windowSpanDays)
        assertEquals(1, progAvg.loggedDaysCount)

        // Pace comparisons on day 1
        val comp7 = FatLossAnalytics.computePaceComparisonVsLastWeek(entries, cycleStartDate, 7, today)
        val comp14 = FatLossAnalytics.computePaceComparisonVsLastWeek(entries, cycleStartDate, 14, today)
        assertNull(comp7)
        assertNull(comp14)

        val sub7 = FatLossAnalytics.format7DayPaceSubtitle(comp7, "kg", today)
        assertEquals("Baseline 7d pace", sub7)

        val pace14 = FatLossAnalytics.compute14DayPace(entries, cycleStartDate, today)
        val sub14 = FatLossAnalytics.format14DayPaceSubtitle(comp14, pace14, "kg", today)
        assertEquals("Needs weigh-in 14+ days ago", sub14)
    }

    @Test
    fun `issue 600 - scenario 2 - young cycle day 5 sparse check-ins`() {
        val cycleStartDate = LocalDate.of(2026, 10, 1)
        val today = LocalDate.of(2026, 10, 5) // Day 5
        val entries = listOf(
            WeightEntry(date = LocalDate.of(2026, 10, 1), weightKg = 78.0, updatedAtMillis = 1L),
            WeightEntry(date = LocalDate.of(2026, 10, 3), weightKg = 77.5, updatedAtMillis = 2L),
            WeightEntry(date = today, weightKg = 77.0, updatedAtMillis = 3L)
        )

        val progAvg = FatLossAnalytics.computeProgressiveAverage(entries, cycleStartDate, today)
        assertNotNull(progAvg)
        assertEquals(77.5, progAvg!!.averageKg, 1e-4)
        assertEquals(5, progAvg.windowSpanDays)
        assertEquals(3, progAvg.loggedDaysCount)
    }

    @Test
    fun `issue 600 - scenario 3 - mature cycle day 21 plus with gaps`() {
        val cycleStartDate = LocalDate.of(2026, 9, 20)
        val t = LocalDate.of(2026, 10, 15) // Day 26
        // Within [t - 13d, t] = [2026-10-02, 2026-10-15], provide 9 entries
        val windowEntries = listOf(2, 3, 5, 6, 8, 9, 11, 13, 15).map { day ->
            WeightEntry(date = LocalDate.of(2026, 10, day), weightKg = 75.0, updatedAtMillis = day.toLong())
        }
        // Also provide an earlier entry in cycle
        val allEntries = listOf(
            WeightEntry(date = LocalDate.of(2026, 9, 21), weightKg = 77.0, updatedAtMillis = 100L)
        ) + windowEntries

        val progAvg = FatLossAnalytics.computeProgressiveAverage(allEntries, cycleStartDate, t)
        assertNotNull(progAvg)
        assertEquals(14, progAvg!!.windowSpanDays)
        assertEquals(9, progAvg.loggedDaysCount)
        assertEquals(75.0, progAvg.averageKg, 1e-4)
    }

    @Test
    fun `issue 600 - scenario 4 - pre-cycle entries exclusion`() {
        val cycleStartDate = LocalDate.of(2026, 10, 1)
        val today = LocalDate.of(2026, 10, 5)
        val preCycleEntries = listOf(
            WeightEntry(date = LocalDate.of(2026, 9, 25), weightKg = 82.0, updatedAtMillis = 1L),
            WeightEntry(date = LocalDate.of(2026, 9, 30), weightKg = 81.0, updatedAtMillis = 2L)
        )
        val inCycleEntries = listOf(
            WeightEntry(date = LocalDate.of(2026, 10, 1), weightKg = 80.0, updatedAtMillis = 3L),
            WeightEntry(date = today, weightKg = 79.0, updatedAtMillis = 4L)
        )
        val allEntries = preCycleEntries + inCycleEntries

        val progAvg = FatLossAnalytics.computeProgressiveAverage(allEntries, cycleStartDate, today)
        assertNotNull(progAvg)
        assertEquals(5, progAvg!!.windowSpanDays)
        assertEquals(2, progAvg.loggedDaysCount)
        assertEquals(79.5, progAvg.averageKg, 1e-4)
    }

    @Test
    fun `issue 600 - scenario 7 - accelerating deficit loss-to-loss percentage`() {
        Locale.setDefault(Locale.US)
        val comparison = FatLossAnalytics.PaceComparisonResult(
            currentRateKgPerWeek = -0.9,
            priorRateKgPerWeek = -0.8,
            deltaRateKgPerWeek = -0.1,
            percentChange = 12.5,
            isAcceleratingDeficit = true,
            priorDate = LocalDate.of(2026, 10, 6), // Tuesday
            isExactDayMatch = true
        )

        val subtitle = FatLossAnalytics.format7DayPaceSubtitle(comparison, "kg", LocalDate.of(2026, 10, 13))
        assertEquals("13% faster loss vs last Tue", subtitle)
    }

    @Test
    fun `issue 600 - scenario 8 - decelerating deficit loss-to-loss percentage`() {
        Locale.setDefault(Locale.US)
        val comparison = FatLossAnalytics.PaceComparisonResult(
            currentRateKgPerWeek = -0.7,
            priorRateKgPerWeek = -0.8,
            deltaRateKgPerWeek = 0.1,
            percentChange = -12.5,
            isAcceleratingDeficit = false,
            priorDate = LocalDate.of(2026, 10, 6), // Tuesday
            isExactDayMatch = true
        )

        val subtitle = FatLossAnalytics.format7DayPaceSubtitle(comparison, "kg", LocalDate.of(2026, 10, 13))
        assertEquals("13% slower loss vs last Tue", subtitle)
    }

    @Test
    fun `issue 600 - scenario 9 - rate delta sign flip non-deficit`() {
        Locale.setDefault(Locale.US)
        val comparison = FatLossAnalytics.PaceComparisonResult(
            currentRateKgPerWeek = 0.5,
            priorRateKgPerWeek = -0.2,
            deltaRateKgPerWeek = 0.7,
            percentChange = null,
            isAcceleratingDeficit = false,
            priorDate = LocalDate.of(2026, 10, 6), // Tuesday
            isExactDayMatch = true
        )

        val subtitle = FatLossAnalytics.format7DayPaceSubtitle(comparison, "kg", LocalDate.of(2026, 10, 13))
        assertEquals("+0.7 kg/wk vs last Tue", subtitle)
    }

    @Test
    fun `issue 600 - scenario 10 - pace noise deadband`() {
        Locale.setDefault(Locale.US)
        val comparison = FatLossAnalytics.PaceComparisonResult(
            currentRateKgPerWeek = -0.83,
            priorRateKgPerWeek = -0.80,
            deltaRateKgPerWeek = -0.03,
            percentChange = 3.75,
            isAcceleratingDeficit = true,
            priorDate = LocalDate.of(2026, 10, 6),
            isExactDayMatch = true
        )

        val subtitle = FatLossAnalytics.format7DayPaceSubtitle(comparison, "kg", LocalDate.of(2026, 10, 13))
        assertEquals("Pace unchanged", subtitle)
    }

    @Test
    fun `issue 600 - scenario 11 - tolerance lookup date formatting vs d MMM`() {
        Locale.setDefault(Locale.US)
        val comparison = FatLossAnalytics.PaceComparisonResult(
            currentRateKgPerWeek = -0.8,
            priorRateKgPerWeek = -0.6,
            deltaRateKgPerWeek = -0.2,
            percentChange = null,
            isAcceleratingDeficit = true,
            priorDate = LocalDate.of(2026, 10, 5), // 5 Oct (Monday, not Tuesday)
            isExactDayMatch = false
        )

        val subtitle = FatLossAnalytics.format7DayPaceSubtitle(comparison, "kg", LocalDate.of(2026, 10, 13))
        assertEquals("-0.2 kg/wk vs 5 Oct", subtitle)
    }

    @Test
    fun `issue 600 - scenario 12 - 14-day pace cadence states`() {
        Locale.setDefault(Locale.US)
        val cycleStartDate = LocalDate.of(2026, 9, 22)
        val weightUnit = "kg"

        // State 1: < 14 days elapsed (e.g. Day 10)
        val dateDay10 = LocalDate.of(2026, 10, 2)
        val entriesDay10 = (0..10).map { day ->
            WeightEntry(date = cycleStartDate.plusDays(day.toLong()), weightKg = 80.0 - day * 0.1, updatedAtMillis = day.toLong())
        }
        val pace14Day10 = FatLossAnalytics.compute14DayPace(entriesDay10, cycleStartDate, dateDay10)
        assertNull(pace14Day10)
        val comp14Day10 = FatLossAnalytics.computePaceComparisonVsLastWeek(entriesDay10, cycleStartDate, 14, dateDay10)
        assertNull(comp14Day10)
        assertEquals("Needs weigh-in 14+ days ago", FatLossAnalytics.format14DayPaceSubtitle(comp14Day10, pace14Day10, weightUnit, dateDay10))

        // State 2: 14 <= elapsed < 21 days (e.g. Day 16)
        val dateDay16 = LocalDate.of(2026, 10, 8)
        val entriesDay16 = (0..16).map { day ->
            WeightEntry(date = cycleStartDate.plusDays(day.toLong()), weightKg = 80.0 - day * 0.1, updatedAtMillis = day.toLong())
        }
        val pace14Day16 = FatLossAnalytics.compute14DayPace(entriesDay16, cycleStartDate, dateDay16)
        assertNotNull(pace14Day16)
        val comp14Day16 = FatLossAnalytics.computePaceComparisonVsLastWeek(entriesDay16, cycleStartDate, 14, dateDay16)
        // At day 16, t - 7d is day 9, which is < 14 days from cycle start -> prior 14d pace is null -> comp14 is null
        assertNull(comp14Day16)
        assertEquals("Smoothed trend", FatLossAnalytics.format14DayPaceSubtitle(comp14Day16, pace14Day16, weightUnit, dateDay16))

        // State 3: Day 21+ with exact same-weekday comparison (full 7d windows at both t and t-7d)
        val dateDay21 = LocalDate.of(2026, 10, 13) // Tuesday (21 days from Sep 22 Tuesday)
        val entriesDay21 = (-6..21).map { day ->
            WeightEntry(date = cycleStartDate.plusDays(day.toLong()), weightKg = 80.0 - day * 0.1, updatedAtMillis = (day + 10).toLong())
        }
        val pace14Day21 = FatLossAnalytics.compute14DayPace(entriesDay21, cycleStartDate, dateDay21)
        assertNotNull(pace14Day21)
        val comp14Day21 = FatLossAnalytics.computePaceComparisonVsLastWeek(entriesDay21, cycleStartDate, 14, dateDay21)
        assertNotNull(comp14Day21)
        assertTrue(comp14Day21!!.isExactDayMatch)
        val sub14Day21 = FatLossAnalytics.format14DayPaceSubtitle(comp14Day21, pace14Day21, weightUnit, dateDay21)
        // Rate is constant (-0.7 kg/wk vs -0.7 kg/wk) -> abs(delta) < 0.05 -> "Pace unchanged"
        assertEquals("Pace unchanged", sub14Day21)
    }

    @Test
    fun `issue 600 - scenario 14 - imperial localization for pace deltas and progressive average`() {
        Locale.setDefault(Locale.US)
        val comparison = FatLossAnalytics.PaceComparisonResult(
            currentRateKgPerWeek = 0.5,
            priorRateKgPerWeek = -0.2,
            deltaRateKgPerWeek = 0.7,
            percentChange = null,
            isAcceleratingDeficit = false,
            priorDate = LocalDate.of(2026, 10, 6),
            isExactDayMatch = true
        )

        // Delta in lbs: 0.7 kg * 2.20462 = 1.543 -> +1.5 lbs/wk
        val subtitleImperial = FatLossAnalytics.format7DayPaceSubtitle(comparison, "lbs", LocalDate.of(2026, 10, 13))
        assertEquals("+1.5 lbs/wk vs last Tue", subtitleImperial)

        // Percentage change remains unit-invariant
        val percentComparison = FatLossAnalytics.PaceComparisonResult(
            currentRateKgPerWeek = -0.9,
            priorRateKgPerWeek = -0.8,
            deltaRateKgPerWeek = -0.1,
            percentChange = 12.5,
            isAcceleratingDeficit = true,
            priorDate = LocalDate.of(2026, 10, 6),
            isExactDayMatch = true
        )
        val percentSubtitleImperial = FatLossAnalytics.format7DayPaceSubtitle(percentComparison, "lbs", LocalDate.of(2026, 10, 13))
        assertEquals("13% faster loss vs last Tue", percentSubtitleImperial)
    }

    // =========================================================================
    // Issue #604: Same-Day Raw Weight Trio in Weight Progression Card
    // =========================================================================

    @Test
    fun `issue 604 - scenario 1 - full same-day trio present`() {
        val cycleStartDate = LocalDate.of(2026, 9, 1)
        val referenceDate = LocalDate.of(2026, 10, 15) // Thursday
        val entries = listOf(
            WeightEntry(date = LocalDate.of(2026, 10, 1), weightKg = 76.5, updatedAtMillis = 1L), // t-14d
            WeightEntry(date = LocalDate.of(2026, 10, 8), weightKg = 75.8, updatedAtMillis = 2L), // t-7d
            WeightEntry(date = LocalDate.of(2026, 10, 15), weightKg = 75.0, updatedAtMillis = 3L) // t
        )

        val trio = FatLossAnalytics.computeSameDayWeightTrio(entries, cycleStartDate, referenceDate)
        assertEquals(75.0, trio.currentDailyWeightKg)
        assertEquals(75.8, trio.lastWeekSameDayWeightKg)
        assertEquals(-0.8, trio.deltaCurrentVsLastWeekSameDayKg)
        assertEquals(76.5, trio.twoWeeksAgoSameDayWeightKg)
        assertEquals(-1.5, trio.deltaCurrentVsTwoWeeksAgoSameDayKg)
    }

    @Test
    fun `issue 604 - scenario 2 - unlogged anchor t evaluates to null deltas`() {
        val cycleStartDate = LocalDate.of(2026, 9, 1)
        val referenceDate = LocalDate.of(2026, 10, 15)
        val entries = listOf(
            WeightEntry(date = LocalDate.of(2026, 10, 1), weightKg = 76.5, updatedAtMillis = 1L),
            WeightEntry(date = LocalDate.of(2026, 10, 8), weightKg = 75.8, updatedAtMillis = 2L)
        )

        val trio = FatLossAnalytics.computeSameDayWeightTrio(entries, cycleStartDate, referenceDate)
        assertNull(trio.currentDailyWeightKg)
        assertEquals(75.8, trio.lastWeekSameDayWeightKg)
        assertNull(trio.deltaCurrentVsLastWeekSameDayKg)
        assertEquals(76.5, trio.twoWeeksAgoSameDayWeightKg)
        assertNull(trio.deltaCurrentVsTwoWeeksAgoSameDayKg)
    }

    @Test
    fun `issue 604 - scenario 3 - missing t minus 7d resolves to null delta`() {
        val cycleStartDate = LocalDate.of(2026, 9, 1)
        val referenceDate = LocalDate.of(2026, 10, 15)
        val entries = listOf(
            WeightEntry(date = LocalDate.of(2026, 10, 1), weightKg = 76.5, updatedAtMillis = 1L),
            WeightEntry(date = LocalDate.of(2026, 10, 15), weightKg = 75.0, updatedAtMillis = 3L)
        )

        val trio = FatLossAnalytics.computeSameDayWeightTrio(entries, cycleStartDate, referenceDate)
        assertEquals(75.0, trio.currentDailyWeightKg)
        assertNull(trio.lastWeekSameDayWeightKg)
        assertNull(trio.deltaCurrentVsLastWeekSameDayKg)
        assertEquals(76.5, trio.twoWeeksAgoSameDayWeightKg)
        assertEquals(-1.5, trio.deltaCurrentVsTwoWeeksAgoSameDayKg)
    }

    @Test
    fun `issue 604 - scenario 4 - missing t minus 14d resolves to null delta`() {
        val cycleStartDate = LocalDate.of(2026, 9, 1)
        val referenceDate = LocalDate.of(2026, 10, 15)
        val entries = listOf(
            WeightEntry(date = LocalDate.of(2026, 10, 8), weightKg = 75.8, updatedAtMillis = 2L),
            WeightEntry(date = LocalDate.of(2026, 10, 15), weightKg = 75.0, updatedAtMillis = 3L)
        )

        val trio = FatLossAnalytics.computeSameDayWeightTrio(entries, cycleStartDate, referenceDate)
        assertEquals(75.0, trio.currentDailyWeightKg)
        assertEquals(75.8, trio.lastWeekSameDayWeightKg)
        assertEquals(-0.8, trio.deltaCurrentVsLastWeekSameDayKg)
        assertNull(trio.twoWeeksAgoSameDayWeightKg)
        assertNull(trio.deltaCurrentVsTwoWeeksAgoSameDayKg)
    }

    @Test
    fun `issue 604 - scenario 5 - pre-cycle entries excluded`() {
        val cycleStartDate = LocalDate.of(2026, 10, 5)
        val referenceDate = LocalDate.of(2026, 10, 15)
        val entries = listOf(
            WeightEntry(date = LocalDate.of(2026, 10, 1), weightKg = 77.0, updatedAtMillis = 1L), // Before cycleStartDate
            WeightEntry(date = LocalDate.of(2026, 10, 8), weightKg = 75.8, updatedAtMillis = 2L),
            WeightEntry(date = LocalDate.of(2026, 10, 15), weightKg = 75.0, updatedAtMillis = 3L)
        )

        val trio = FatLossAnalytics.computeSameDayWeightTrio(entries, cycleStartDate, referenceDate)
        assertEquals(75.0, trio.currentDailyWeightKg)
        assertEquals(75.8, trio.lastWeekSameDayWeightKg)
        assertEquals(-0.8, trio.deltaCurrentVsLastWeekSameDayKg)
        assertNull(trio.twoWeeksAgoSameDayWeightKg)
        assertNull(trio.deltaCurrentVsTwoWeeksAgoSameDayKg)
    }

    @Test
    fun `issue 604 - scenario 6 - soft-deleted and future entries excluded`() {
        val cycleStartDate = LocalDate.of(2026, 9, 1)
        val referenceDate = LocalDate.of(2026, 10, 15)
        val entries = listOf(
            WeightEntry(date = LocalDate.of(2026, 10, 1), weightKg = 76.5, updatedAtMillis = 1L),
            WeightEntry(date = LocalDate.of(2026, 10, 8), weightKg = 75.8, deletedAtMillis = 9999L, updatedAtMillis = 2L), // Soft-deleted
            WeightEntry(date = LocalDate.of(2026, 10, 15), weightKg = 75.0, updatedAtMillis = 3L),
            WeightEntry(date = LocalDate.of(2026, 10, 16), weightKg = 74.0, updatedAtMillis = 4L) // Future
        )

        val trio = FatLossAnalytics.computeSameDayWeightTrio(entries, cycleStartDate, referenceDate)
        assertEquals(75.0, trio.currentDailyWeightKg)
        assertNull(trio.lastWeekSameDayWeightKg)
        assertNull(trio.deltaCurrentVsLastWeekSameDayKg)
        assertEquals(76.5, trio.twoWeeksAgoSameDayWeightKg)
        assertEquals(-1.5, trio.deltaCurrentVsTwoWeeksAgoSameDayKg)
    }

    @Test
    fun `issue 604 - scenario 7 - cycle overload delegates to cycle startDate`() {
        val cycle = Cycle(
            id = 1L,
            name = "Cut",
            startDate = LocalDate.of(2026, 9, 1),
            type = CycleType.FAT_LOSS_BODYBUILDING,
            startingWeightKg = 80.0,
            targetWeightKg = 72.0
        )
        val referenceDate = LocalDate.of(2026, 10, 15)
        val entries = listOf(
            WeightEntry(date = LocalDate.of(2026, 10, 15), weightKg = 75.0, updatedAtMillis = 1L)
        )

        val trio = FatLossAnalytics.computeSameDayWeightTrio(entries, cycle, referenceDate)
        assertEquals(75.0, trio.currentDailyWeightKg)
    }

    @Test
    fun `issue 604 - scenario 8 - diff1 precision prevents IEEE-754 inaccuracy`() {
        val cycleStartDate = LocalDate.of(2026, 9, 1)
        val referenceDate = LocalDate.of(2026, 10, 15)
        val entries = listOf(
            WeightEntry(date = LocalDate.of(2026, 10, 8), weightKg = 75.2, updatedAtMillis = 1L),
            WeightEntry(date = LocalDate.of(2026, 10, 15), weightKg = 75.3, updatedAtMillis = 2L)
        )

        val trio = FatLossAnalytics.computeSameDayWeightTrio(entries, cycleStartDate, referenceDate)
        assertEquals(0.1, trio.deltaCurrentVsLastWeekSameDayKg)
    }

    @Test
    fun `issue 604 - scenario 9 - display-unit arithmetic consistency invariant`() {
        Locale.setDefault(Locale.US)
        val currentKg = 72.44
        val priorKg = 72.36

        // In lbs: 72.44 kg -> 159.7 lbs; 72.36 kg -> 159.5 lbs => +0.2 lbs
        val deltaLbs = FatLossAnalytics.computeSameDayDisplayDelta(currentKg, priorKg, "lbs")
        assertEquals(0.2, deltaLbs)
        val formattedLbs = FatLossAnalytics.formatSameDayWeightDelta(currentKg, priorKg, "lbs")
        assertEquals("+0.2 lbs", formattedLbs)

        // In kg: 72.44 kg -> 72.4 kg; 72.36 kg -> 72.4 kg => 0.0 kg
        val deltaKg = FatLossAnalytics.computeSameDayDisplayDelta(currentKg, priorKg, "kg")
        assertEquals(0.0, deltaKg)
        val formattedKg = FatLossAnalytics.formatSameDayWeightDelta(currentKg, priorKg, "kg")
        assertEquals("0.0 kg", formattedKg)
    }

    @Test
    fun `issue 604 - scenario 10 - deadband and null delta formatting`() {
        Locale.setDefault(Locale.US)
        assertEquals("0.0 kg", FatLossAnalytics.formatSameDayDisplayDelta(0.0, "kg"))
        assertEquals("0.0 lbs", FatLossAnalytics.formatSameDayDisplayDelta(0.0, "lbs"))
        assertEquals("+0.4 kg", FatLossAnalytics.formatSameDayDisplayDelta(0.4, "kg"))
        assertEquals("-0.5 kg", FatLossAnalytics.formatSameDayDisplayDelta(-0.5, "kg"))
        assertEquals("-1.1 lbs", FatLossAnalytics.formatSameDayDisplayDelta(-1.1, "lbs"))
        assertEquals("—", FatLossAnalytics.formatSameDayDisplayDelta(null, "kg"))
        assertEquals("—", FatLossAnalytics.formatSameDayWeightDelta(null, 75.0, "kg"))
        assertEquals("—", FatLossAnalytics.formatSameDayWeightDelta(75.0, null, "kg"))
    }

    // =========================================================================
    // Feature 001-rolling-weight-pace: Smoothed 7D/14D Pace, Pre-Cycle Continuity & Tiered Forecast
    // =========================================================================

    @Test
    fun `rolling weight pace US1 - computeRollingWeightHero includes pre-cycle entries when in-cycle log exists`() {
        val cycleStartDate = LocalDate.of(2026, 10, 7)
        val referenceDate = LocalDate.of(2026, 10, 22) // Thursday (15 days after Oct 7)

        // Pre-cycle entries in [Oct 2, Oct 6] (falling into [t-20, t-7] = [Oct 2, Oct 15])
        val preCycleEntries = (2..6).map { day ->
            WeightEntry(date = LocalDate.of(2026, 10, day), weightKg = 76.6, updatedAtMillis = day.toLong())
        }
        // In-cycle entries in [Oct 7, Oct 15] at 76.0 kg and [Oct 16, Oct 22] at 75.7 kg
        val week2Entries = (7..15).map { day ->
            WeightEntry(date = LocalDate.of(2026, 10, day), weightKg = 76.0, updatedAtMillis = day.toLong())
        }
        val week3Entries = (16..22).map { day ->
            WeightEntry(date = LocalDate.of(2026, 10, day), weightKg = 75.7, updatedAtMillis = day.toLong())
        }
        val allEntries = preCycleEntries + week2Entries + week3Entries

        // When no in-cycle entries exist, all hero averages must be null
        val emptyInCycleHero = FatLossAnalytics.computeRollingWeightHero(preCycleEntries, cycleStartDate, referenceDate)
        assertNull(emptyInCycleHero.today7dAvgKg)
        assertNull(emptyInCycleHero.lastWeek7dAvgKg)
        assertNull(emptyInCycleHero.today14dAvgKg)
        assertEquals(0, emptyInCycleHero.loggedDaysCount14d)

        // When in-cycle entries exist, pre-cycle entries are included in lookback windows
        val hero = FatLossAnalytics.computeRollingWeightHero(allEntries, cycleStartDate, referenceDate)
        assertEquals(75.7, hero.today7dAvgKg!!, 1e-4)
        assertEquals(76.0, hero.lastWeek7dAvgKg!!, 1e-4)
        assertEquals(-0.3, hero.delta7dKg!!, 1e-4)
        assertEquals(75.85, hero.today14dAvgKg!!, 1e-4)
        assertNotNull(hero.delta14dKg)
        assertEquals(14, hero.loggedDaysCount14d)
    }

    @Test
    fun `rolling weight pace US1 - smoothed 7d and 14d pace resist single-day outlier 14 days ago`() {
        val cycleStartDate = LocalDate.of(2026, 10, 7)
        val t = LocalDate.of(2026, 10, 22) // Thursday

        // Window [t-20, t-14] = [Oct 2, Oct 8]:
        // Suppose Oct 8 had a single-day low dehydration outlier (74.9 kg), while Oct 2..Oct 7 were 76.8 kg
        // Mean of [Oct 2, Oct 8] = (6 * 76.8 + 74.9) / 7 = 535.7 / 7 = 76.52857 kg
        val wTwoWeeksAgo = (2..7).map { day ->
            WeightEntry(date = LocalDate.of(2026, 10, day), weightKg = 76.8, updatedAtMillis = day.toLong())
        } + WeightEntry(date = LocalDate.of(2026, 10, 8), weightKg = 74.9, updatedAtMillis = 8L)

        // Window [t-13, t-7] = [Oct 9, Oct 15]: mean = 76.0 kg
        val wLastWeek = (9..15).map { day ->
            WeightEntry(date = LocalDate.of(2026, 10, day), weightKg = 76.0, updatedAtMillis = day.toLong())
        }

        // Window [t-6, t] = [Oct 16, Oct 22]: mean = 75.7 kg (with Thursday Oct 22 = 74.7 kg)
        val wThisWeek = (16..21).map { day ->
            WeightEntry(date = LocalDate.of(2026, 10, day), weightKg = 75.8666667, updatedAtMillis = day.toLong())
        } + WeightEntry(date = t, weightKg = 74.7, updatedAtMillis = 22L)

        val entries = wTwoWeeksAgo + wLastWeek + wThisWeek

        val hero = FatLossAnalytics.computeRollingWeightHero(entries, cycleStartDate, t)
        val pace7 = FatLossAnalytics.compute7DayPace(entries, cycleStartDate, t, startingWeightKg = 77.8)
        val pace14 = FatLossAnalytics.compute14DayPace(entries, cycleStartDate, t, startingWeightKg = 77.8)

        assertNotNull(pace7)
        assertNotNull(pace14)

        // 7-Day Pace strictly equals Today 7D Avg - Last Week 7D Avg (delta7dKg = -0.3 kg/wk)
        assertEquals(hero.delta7dKg!!, pace7!!.rateKgPerWeek, 1e-4)
        assertEquals(-0.3, pace7.rateKgPerWeek, 1e-3)
        assertEquals("-0.3 kg/wk", pace7.formatDisplay("kg"))

        // 14-Day Pace equals (Today 7D Avg - TwoWeeksAgo 7D Avg) / 2.0 ≈ (75.7 - 76.52857) / 2 = -0.414 kg/wk
        assertEquals((75.7 - (535.7 / 7.0)) / 2.0, pace14!!.rateKgPerWeek, 1e-3)
        assertEquals("-0.4 kg/wk", pace14.formatDisplay("kg"))
    }

    @Test
    fun `rolling weight pace US1 - startingWeightKg fallback when no pre-cycle logs exist in lookback window`() {
        val cycleStartDate = LocalDate.of(2026, 10, 7)
        val t = LocalDate.of(2026, 10, 21) // Exactly 14 days after Oct 7
        // Athlete only started logging on Oct 10 (no entries in [Oct 1, Oct 7]), but cycle has startingWeightKg = 77.8 on Oct 7
        val entries = (10..21).map { day ->
            WeightEntry(date = LocalDate.of(2026, 10, day), weightKg = 76.0, updatedAtMillis = day.toLong())
        }

        val pace14 = FatLossAnalytics.compute14DayPace(
            entries = entries,
            cycleStartDate = cycleStartDate,
            today = t,
            startingWeightKg = 77.8
        )
        assertNotNull("compute14DayPace should fall back to startingWeightKg when cycleStartDate <= cutoffDate", pace14)
        // Current 7D avg = 76.0, anchor = startingWeightKg (77.8) at Oct 7 (14 days elapsed) -> (76.0 - 77.8) / 14 * 7 = -0.9 kg/wk
        assertEquals(-0.9, pace14!!.rateKgPerWeek, 1e-4)
    }

    @Test
    fun `rolling weight pace US2 - computePaceForecast resolves 3-tier smoothed deficit velocity hierarchy`() {
        val cycleStartDate = LocalDate.of(2026, 10, 7)
        val t = LocalDate.of(2026, 10, 22)
        val cycle = Cycle(
            id = 900L,
            name = "Recomp",
            startDate = cycleStartDate,
            type = CycleType.FAT_LOSS_BODYBUILDING,
            startingWeightKg = 77.8,
            targetWeightKg = 72.0
        )

        // Case 1: Tier 1 active (14D smoothed pace = -0.4 kg/wk, current 7D avg = 75.7 kg, remaining = 3.7 kg)
        val tier1Entries = (2..8).map { d -> WeightEntry(date = LocalDate.of(2026, 10, d), weightKg = 76.5, updatedAtMillis = d.toLong()) } +
            (9..15).map { d -> WeightEntry(date = LocalDate.of(2026, 10, d), weightKg = 75.9, updatedAtMillis = d.toLong()) } +
            (16..22).map { d -> WeightEntry(date = LocalDate.of(2026, 10, d), weightKg = 75.7, updatedAtMillis = d.toLong()) }

        val forecastTier1 = FatLossAnalytics.computePaceForecast(tier1Entries, cycle, t)
        assertTrue(forecastTier1 is PaceForecast.Projected)
        val projTier1 = forecastTier1 as PaceForecast.Projected
        // 3.7 kg / 0.4 kg/wk = 9.25 weeks -> "Estimated: 9.3 weeks"
        assertEquals(9.25, projTier1.weeks, 1e-3)
        assertEquals("Estimated: 9.3 weeks", projTier1.displayText)

        // Case 2: Tier 1 available but flat (14D smoothed pace = 0.0 kg/wk because 2 wks ago was 75.7 kg) while the 7D pace shows -0.3 kg/wk.
        // Tiers are selected by availability, not sign: the forecast must agree with the 14D card and report Stalled.
        val tier1FlatEntries = (2..8).map { d -> WeightEntry(date = LocalDate.of(2026, 10, d), weightKg = 75.7, updatedAtMillis = d.toLong()) } +
            (9..15).map { d -> WeightEntry(date = LocalDate.of(2026, 10, d), weightKg = 76.0, updatedAtMillis = d.toLong()) } +
            (16..22).map { d -> WeightEntry(date = LocalDate.of(2026, 10, d), weightKg = 75.7, updatedAtMillis = d.toLong()) }

        assertTrue(FatLossAnalytics.compute7DayPace(tier1FlatEntries, cycle, t)!!.rateKgPerWeek < 0.0)
        assertEquals(PaceForecast.Stalled, FatLossAnalytics.computePaceForecast(tier1FlatEntries, cycle, t))

        // Case 3: Tier 1 null (cycle started Oct 12, no history on or before t-14 = Oct 8), Tier 2 active (Oct 12–15 = 76.0 kg -> 7D pace = -0.3 kg/wk)
        val lateCycle = cycle.copy(startDate = LocalDate.of(2026, 10, 12))
        val tier2Entries = (12..15).map { d -> WeightEntry(date = LocalDate.of(2026, 10, d), weightKg = 76.0, updatedAtMillis = d.toLong()) } +
            (16..22).map { d -> WeightEntry(date = LocalDate.of(2026, 10, d), weightKg = 75.7, updatedAtMillis = d.toLong()) }

        assertNull(FatLossAnalytics.compute14DayPace(tier2Entries, lateCycle, t))
        val forecastTier2 = FatLossAnalytics.computePaceForecast(tier2Entries, lateCycle, t)
        assertTrue("Forecast must fall back to Tier 2 (7D smoothed pace) when Tier 1 is unavailable", forecastTier2 is PaceForecast.Projected)
        val projTier2 = forecastTier2 as PaceForecast.Projected
        // 3.7 kg / 0.3 kg/wk = 12.333 weeks -> "Estimated: 12.3 weeks"
        assertEquals(3.7 / 0.3, projTier2.weeks, 1e-3)
        assertEquals("Estimated: 12.3 weeks", projTier2.displayText)
    }

    @Test
    fun `rolling weight pace review - gaining 14d pace yields Stalled despite overall cycle loss`() {
        val cycleStartDate = LocalDate.of(2026, 10, 1)
        val t = LocalDate.of(2026, 10, 22)
        val cycle = Cycle(
            id = 901L,
            name = "Cut",
            startDate = cycleStartDate,
            type = CycleType.FAT_LOSS_BODYBUILDING,
            startingWeightKg = 80.0,
            targetWeightKg = 72.0
        )
        // Big early drop to 75.0, then regaining: [t-20, t-14] = 75.0, [t-13, t-7] = 75.3, [t-6, t] = 75.6
        val entries = (2..8).map { d -> WeightEntry(date = LocalDate.of(2026, 10, d), weightKg = 75.0, updatedAtMillis = d.toLong()) } +
            (9..15).map { d -> WeightEntry(date = LocalDate.of(2026, 10, d), weightKg = 75.3, updatedAtMillis = d.toLong()) } +
            (16..22).map { d -> WeightEntry(date = LocalDate.of(2026, 10, d), weightKg = 75.6, updatedAtMillis = d.toLong()) }

        assertTrue(FatLossAnalytics.compute14DayPace(entries, cycle, t)!!.rateKgPerWeek > 0.0)
        assertTrue(FatLossAnalytics.compute7DayPace(entries, cycle, t)!!.rateKgPerWeek > 0.0)
        assertEquals(PaceForecast.Stalled, FatLossAnalytics.computePaceForecast(entries, cycle, t))
    }

    @Test
    fun `rolling weight pace review - empty prior window anchors to startingWeightKg not stale pre-cycle entry`() {
        val cycleStartDate = LocalDate.of(2026, 10, 1)
        val t = LocalDate.of(2026, 10, 17)
        val staleEntry = WeightEntry(date = LocalDate.of(2026, 3, 15), weightKg = 85.0, updatedAtMillis = 1L)
        val inCycle = (11..17).map { d -> WeightEntry(date = LocalDate.of(2026, 10, d), weightKg = 88.6, updatedAtMillis = d.toLong()) }

        // 7D prior window [Oct 4, Oct 10] is empty; cycle start (Oct 1) <= cutoff (Oct 10) -> anchor = startingWeightKg
        val pace7 = FatLossAnalytics.compute7DayPace(listOf(staleEntry) + inCycle, cycleStartDate, t, startingWeightKg = 90.0)

        assertNotNull(pace7)
        assertEquals(90.0, pace7!!.anchorWeightKg, 1e-9)
        assertEquals(cycleStartDate, pace7.anchorDate)
        assertEquals(16L, pace7.elapsedDays)
        assertEquals((88.6 - 90.0) / 16.0 * 7.0, pace7.rateKgPerWeek, 1e-9)
    }

    @Test
    fun `rolling weight pace review - year-old pre-cycle entry does not satisfy span on cycle day 1`() {
        val t = LocalDate.of(2026, 10, 22)
        val cycle = Cycle(
            id = 902L,
            name = "New Cut",
            startDate = t,
            type = CycleType.FAT_LOSS_BODYBUILDING,
            startingWeightKg = 80.0,
            targetWeightKg = 75.0
        )
        val entries = listOf(
            WeightEntry(date = t.minusYears(1), weightKg = 85.0, updatedAtMillis = 1L),
            WeightEntry(date = t, weightKg = 79.5, updatedAtMillis = 2L)
        )

        assertNull(FatLossAnalytics.compute7DayPace(entries, cycle, t))
        assertNull(FatLossAnalytics.compute14DayPace(entries, cycle, t))
        assertEquals(PaceForecast.NotEnoughData, FatLossAnalytics.computePaceForecast(entries, cycle, t))
    }

    @Test
    fun `rolling weight pace review - single in-cycle entry plus startingWeightKg resolves pace and forecast`() {
        val cycleStartDate = LocalDate.of(2026, 10, 1)
        val t = LocalDate.of(2026, 10, 15)
        val cycle = Cycle(
            id = 903L,
            name = "Cut",
            startDate = cycleStartDate,
            type = CycleType.FAT_LOSS_BODYBUILDING,
            startingWeightKg = 85.0,
            targetWeightKg = 80.0
        )
        val entries = listOf(WeightEntry(date = t, weightKg = 83.0, updatedAtMillis = 1L))

        // 14D: cutoff = Oct 1 = cycle start -> anchor startingWeightKg, (83 - 85) / 14 * 7 = -1.0 kg/wk
        val pace14 = FatLossAnalytics.compute14DayPace(entries, cycle, t)
        assertNotNull(pace14)
        assertEquals(-1.0, pace14!!.rateKgPerWeek, 1e-9)

        // Forecast: 3.0 kg remaining / 1.0 kg/wk = 3.0 weeks
        val forecast = FatLossAnalytics.computePaceForecast(entries, cycle, t)
        assertTrue(forecast is PaceForecast.Projected)
        assertEquals(3.0, (forecast as PaceForecast.Projected).weeks, 1e-9)
    }

    // =========================================================================
    // Feature 002-weight-card-clarity-and-forecast-pace Tests (US1 & US2)
    // =========================================================================

    @Test
    fun `weightCardClarity US1 - computeRollingWeightHero reconciles 1-decimal deltas without unrounded drift`() {
        val cycleStartDate = LocalDate.of(2026, 10, 1)
        val t = LocalDate.of(2026, 10, 22)
        // t-13..t-7: 7 days of 75.94 kg -> lastWeek7dAvg = 75.94 kg (rounds to 75.9 kg)
        // t-6..t: 7 days of 75.66 kg -> today7dAvg = 75.66 kg (rounds to 75.7 kg)
        // Unrounded difference: 75.66 - 75.94 = -0.28 kg (which formatted as %.1f would be -0.3 kg)
        // 1-decimal display difference: 75.7 - 75.9 = -0.2 kg
        val entries = (9..15).map { d ->
            WeightEntry(date = LocalDate.of(2026, 10, d), weightKg = 75.94, updatedAtMillis = d.toLong())
        } + (16..22).map { d ->
            WeightEntry(date = LocalDate.of(2026, 10, d), weightKg = 75.66, updatedAtMillis = d.toLong())
        }

        val hero = FatLossAnalytics.computeRollingWeightHero(entries, cycleStartDate, t)
        assertEquals(75.7, FatLossAnalytics.round1(hero.today7dAvgKg!!), 1e-6)
        assertEquals(75.9, FatLossAnalytics.round1(hero.lastWeek7dAvgKg!!), 1e-6)
        assertEquals(-0.2, hero.delta7dKg!!, 1e-6)
    }

    @Test
    fun `weightCardClarity US2 - computePaceForecast divides 1-decimal remaining weight by 1-decimal pace rate`() {
        val cycleStartDate = LocalDate.of(2026, 10, 1)
        val t = LocalDate.of(2026, 10, 22)
        val cycle = Cycle(
            id = 904L,
            name = "Reconcile Forecast",
            startDate = cycleStartDate,
            type = CycleType.FAT_LOSS_BODYBUILDING,
            startingWeightKg = 77.8,
            targetWeightKg = 72.0
        )

        // Construct 14-day history where 14D smoothed deficit velocity is 0.188 kg/wk
        // Rate is -0.188 kg/wk -> rounds to -0.2 kg/wk
        // Current 7D average is 75.7 kg, target is 72.0 kg -> remaining is 3.7 kg
        // Unrounded: 3.7 / 0.188 = 19.68 weeks -> "Estimated: 19.7 weeks"
        // Rounded: 3.7 / 0.2 = 18.5 weeks -> "Estimated: 18.5 weeks"
        // Anchor 14 days ago (t-14 = Oct 8): average 76.076 kg
        // Current window (t-6..t): average 75.700 kg
        // Delta = 75.700 - 76.076 = -0.376 over 14 days -> rate = (-0.376 / 14) * 7 = -0.188 kg/wk
        val entries = (2..8).map { d ->
            WeightEntry(date = LocalDate.of(2026, 10, d), weightKg = 76.076, updatedAtMillis = d.toLong())
        } + (9..15).map { d ->
            WeightEntry(date = LocalDate.of(2026, 10, d), weightKg = 75.9, updatedAtMillis = d.toLong())
        } + (16..22).map { d ->
            WeightEntry(date = LocalDate.of(2026, 10, d), weightKg = 75.7, updatedAtMillis = d.toLong())
        }

        val pace14 = FatLossAnalytics.compute14DayPace(entries, cycle, t)
        assertNotNull(pace14)
        assertEquals(-0.188, pace14!!.rateKgPerWeek, 1e-3)
        assertEquals("-0.2 kg/wk", pace14.formatDisplay())

        val forecast = FatLossAnalytics.computePaceForecast(entries, cycle, t)
        assertNotNull(forecast)
        assertTrue(forecast is PaceForecast.Projected)
        val projected = forecast as PaceForecast.Projected

        assertEquals(18.5, projected.weeks, 1e-3)
        assertEquals("Estimated: 18.5 weeks", projected.displayText)
        assertEquals(14, projected.paceWindowDays)
        assertEquals(-0.188, projected.paceRateKgPerWeek!!, 1e-3)
    }

    @Test
    fun `weightCardClarity US2 - computePaceForecast tier 2 populates 7-day window metadata`() {
        val cycleStartDate = LocalDate.of(2026, 10, 12) // Cycle started recently (< 14 days)
        val t = LocalDate.of(2026, 10, 22)
        val cycle = Cycle(
            id = 905L,
            name = "Tier 2 Cut",
            startDate = cycleStartDate,
            type = CycleType.FAT_LOSS_BODYBUILDING,
            startingWeightKg = 77.8,
            targetWeightKg = 72.0
        )

        // 7D rate = -0.3 kg/wk, remaining = 3.7 kg -> 3.7 / 0.3 = 12.333 weeks -> "Estimated: 12.3 weeks"
        val entries = (12..15).map { d ->
            WeightEntry(date = LocalDate.of(2026, 10, d), weightKg = 76.0, updatedAtMillis = d.toLong())
        } + (16..22).map { d ->
            WeightEntry(date = LocalDate.of(2026, 10, d), weightKg = 75.7, updatedAtMillis = d.toLong())
        }

        assertNull(FatLossAnalytics.compute14DayPace(entries, cycle, t))
        val forecast = FatLossAnalytics.computePaceForecast(entries, cycle, t)
        assertTrue(forecast is PaceForecast.Projected)
        val projected = forecast as PaceForecast.Projected
        assertEquals(3.7 / 0.3, projected.weeks, 1e-3)
        assertEquals("Estimated: 12.3 weeks", projected.displayText)
        assertEquals(7, projected.paceWindowDays)
        assertEquals(-0.3, projected.paceRateKgPerWeek!!, 0.05)
    }

    @Test
    fun `weightCardClarity review - GoalReached uses displayed precision before Stalled and CycleEnded`() {
        val cycleStartDate = LocalDate.of(2026, 10, 1)
        val t = LocalDate.of(2026, 10, 22)
        // Flat 72.04 kg -> 7D average displays as 72.0 kg, equal to the 72.0 kg goal; pace is stalled (0.0 kg/wk)
        val entries = (2..22).map { d ->
            WeightEntry(date = LocalDate.of(2026, 10, d), weightKg = 72.04, updatedAtMillis = d.toLong())
        }

        val active = FatLossAnalytics.computePaceForecast(
            entries = entries,
            cycleStartDate = cycleStartDate,
            targetWeightKg = 72.0,
            startingWeightKg = 77.8,
            today = t
        )
        assertEquals(PaceForecast.GoalReached, active)

        val ended = FatLossAnalytics.computePaceForecast(
            entries = entries,
            cycleStartDate = cycleStartDate,
            cycleEndDate = LocalDate.of(2026, 10, 20),
            targetWeightKg = 72.0,
            startingWeightKg = 77.8,
            today = t
        )
        assertEquals(PaceForecast.GoalReached, ended)
    }

    @Test
    fun `weightCardClarity review - forecast remaining weight uses today's displayed 7D average when the last weigh-in is stale`() {
        val cycleStartDate = LocalDate.of(2026, 10, 1)
        val t = LocalDate.of(2026, 10, 22)
        val cycle = Cycle(
            id = 906L,
            name = "Stale Weigh-In Cut",
            startDate = cycleStartDate,
            type = CycleType.FAT_LOSS_BODYBUILDING,
            startingWeightKg = 77.8,
            targetWeightKg = 72.0
        )
        // Last weigh-in is Oct 19 (3 days before today): the 7D window ending today [16..22] averages 76.0 kg,
        // while the window ending Oct 19 [13..19] averages 76.214 kg (displayed 76.2 kg)
        val entries = (2..8).map { d ->
            WeightEntry(date = LocalDate.of(2026, 10, d), weightKg = 77.0, updatedAtMillis = d.toLong())
        } + (9..15).map { d ->
            WeightEntry(date = LocalDate.of(2026, 10, d), weightKg = 76.5, updatedAtMillis = d.toLong())
        } + (16..19).map { d ->
            WeightEntry(date = LocalDate.of(2026, 10, d), weightKg = 76.0, updatedAtMillis = d.toLong())
        }

        val hero = FatLossAnalytics.computeRollingWeightHero(entries, cycle, t)
        assertEquals(76.0, hero.today7dAvgKg!!, 1e-9)
        val displayedPace14 = FatLossAnalytics.compute14DayPace(entries, cycle, t)
        assertNotNull(displayedPace14)
        assertEquals("-0.4 kg/wk", displayedPace14!!.formatDisplay())

        val forecast = FatLossAnalytics.computePaceForecast(entries, cycle, t)
        assertTrue(forecast is PaceForecast.Projected)
        val projected = forecast as PaceForecast.Projected
        assertEquals(14, projected.paceWindowDays)
        assertEquals(displayedPace14.rateKgPerWeek, projected.paceRateKgPerWeek!!, 1e-9)
        // Displayed 7D AVG (76.0 kg) - goal (72.0 kg) = 4.0 kg at displayed pace 0.4 kg/wk -> 10.0 weeks
        // (the stale Oct 19 window would give 76.2 - 72.0 = 4.2 kg -> 10.5 weeks)
        assertEquals(10.0, projected.weeks, 1e-6)
    }

    @Test
    fun `weightCardClarity review - direct velocity forecast without window metadata leaves window unknown`() {
        val forecast = FatLossAnalytics.computePaceForecast(
            currentWeightKg = 80.0,
            targetWeightKg = 75.0,
            velocityKgPerWeek = 0.5,
            today = LocalDate.of(2026, 10, 22)
        )
        assertTrue(forecast is PaceForecast.Projected)
        val projected = forecast as PaceForecast.Projected
        assertNull(projected.paceWindowDays)
        assertEquals(-0.5, projected.paceRateKgPerWeek!!, 1e-9)
        assertEquals(10.0, projected.weeks, 1e-6)
    }

    // =========================================================================
    // Feature 003-cycle-end-required-pace Tests (US1 & US2)
    // =========================================================================

    @Test
    fun `requiredPace US1 - standard kg calculation reconciles with 1-decimal remaining weight and exact calendar weeks`() {
        val today = LocalDate.of(2026, 10, 22)
        val endDate = LocalDate.of(2026, 11, 30) // 39 days remaining -> 39 / 7.0 = 5.5714286 weeks
        // 75.66 kg rounds to 75.7 kg; goal 72.0 kg -> remaining = 3.7 kg
        // 3.7 / (39.0 / 7.0) = 0.6641025... -> rounds to 0.7 -> -0.7 kg/wk
        val result = FatLossAnalytics.computeRequiredPaceToEndDate(
            today7dAvgKg = 75.66,
            targetWeightKg = 72.0,
            cycleEndDate = endDate,
            today = today,
            weightUnit = "kg",
            startWeightKg = 77.8
        )
        assertNotNull(result)
        assertEquals(-0.7, result!!.requiredRateInUnit, 1e-6)
        assertEquals(3.7, result.remainingWeightInUnit, 1e-6)
        assertEquals(39L, result.daysRemaining)
        assertEquals(39.0 / 7.0, result.weeksRemaining, 1e-6)
        assertFalse(result.exceedsSustainableRate) // 0.7 <= 1% of 75.7
        assertEquals("Required pace: -0.7 kg/wk (by 30 Nov 2026)", result.formatSubtitle())
    }

    @Test
    fun `requiredPace US1 - imperial lbs calculation reconciles with 1-decimal lbs remaining weight`() {
        val today = LocalDate.of(2026, 10, 22)
        val endDate = LocalDate.of(2026, 11, 19) // 28 days remaining = 4.0 weeks
        // 75.66 kg -> 166.8 lbs; 72.0 kg -> 158.7 lbs; remaining = 8.1 lbs
        // 8.1 / 4.0 = 2.025 -> rounds to 2.0 -> -2.0 lbs/wk
        val result = FatLossAnalytics.computeRequiredPaceToEndDate(
            today7dAvgKg = 75.66,
            targetWeightKg = 72.0,
            cycleEndDate = endDate,
            today = today,
            weightUnit = "lbs",
            startWeightKg = 77.8
        )
        assertNotNull(result)
        assertEquals(8.1, result!!.remainingWeightInUnit, 1e-6)
        assertEquals(-2.0, result.requiredRateInUnit, 1e-6)
        assertTrue(result.exceedsSustainableRate) // 2.0 > 1% of 166.8
        assertEquals("Required pace: -2.0 lbs/wk (by 19 Nov 2026) — above 1% bodyweight/wk", result.formatSubtitle())
    }

    @Test
    fun `requiredPace US1 - clamps small positive required rate to -0_1 so positive remaining distance never rounds to zero`() {
        val today = LocalDate.of(2026, 10, 22)
        val endDate = today.plusDays(140) // 20 weeks remaining
        // 72.2 kg - 72.0 kg = 0.2 kg over 20 weeks = 0.01 kg/wk -> clamps to -0.1 kg/wk
        val result = FatLossAnalytics.computeRequiredPaceToEndDate(
            today7dAvgKg = 72.2,
            targetWeightKg = 72.0,
            cycleEndDate = endDate,
            today = today,
            weightUnit = "kg"
        )
        assertNotNull(result)
        assertEquals(-0.1, result!!.requiredRateInUnit, 1e-6)
        assertEquals("-0.1 kg/wk", result.formatRate())
    }

    @Test
    fun `requiredPace US1 - short horizon 1 day remaining scales to steep weekly pace`() {
        val today = LocalDate.of(2026, 10, 22)
        val endDate = today.plusDays(1) // 1 day = 1/7 week
        // 73.0 kg - 72.0 kg = 1.0 kg over 1/7 week = 7.0 kg/wk -> -7.0 kg/wk
        val result = FatLossAnalytics.computeRequiredPaceToEndDate(
            today7dAvgKg = 73.0,
            targetWeightKg = 72.0,
            cycleEndDate = endDate,
            today = today,
            weightUnit = "kg"
        )
        assertNotNull(result)
        assertEquals(1L, result!!.daysRemaining)
        assertEquals(-7.0, result.requiredRateInUnit, 1e-6)
        assertTrue(result.exceedsSustainableRate)
        assertEquals("Required pace: -7.0 kg/wk (by 23 Oct 2026) — above 1% bodyweight/wk", result.formatSubtitle())
    }

    @Test
    fun `requiredPace US1 - flags paces above 1 percent of current weight per week without capping them`() {
        val today = LocalDate.of(2026, 10, 22)
        // 80.0 kg -> sustainable limit 0.8 kg/wk. 3.2 kg over 4 weeks = 0.8 kg/wk -> at the limit, not flagged
        val atLimit = FatLossAnalytics.computeRequiredPaceToEndDate(
            today7dAvgKg = 80.0,
            targetWeightKg = 76.8,
            cycleEndDate = today.plusDays(28),
            today = today
        )
        assertNotNull(atLimit)
        assertEquals(-0.8, atLimit!!.requiredRateInUnit, 1e-6)
        assertFalse(atLimit.exceedsSustainableRate)
        assertEquals("Required pace: -0.8 kg/wk (by 19 Nov 2026)", atLimit.formatSubtitle())

        // 3.6 kg over 4 weeks = 0.9 kg/wk -> above the limit, flagged but the rate is not capped
        val aboveLimit = FatLossAnalytics.computeRequiredPaceToEndDate(
            today7dAvgKg = 80.0,
            targetWeightKg = 76.4,
            cycleEndDate = today.plusDays(28),
            today = today
        )
        assertNotNull(aboveLimit)
        assertEquals(-0.9, aboveLimit!!.requiredRateInUnit, 1e-6)
        assertTrue(aboveLimit.exceedsSustainableRate)
        assertEquals("Required pace: -0.9 kg/wk (by 19 Nov 2026) — above 1% bodyweight/wk", aboveLimit.formatSubtitle())
    }

    @Test
    fun `requiredPace US2 - returns null for open-ended cycle, missing goal, non-deficit goal, goal reached, or expired end date`() {
        val today = LocalDate.of(2026, 10, 22)
        val futureEnd = LocalDate.of(2026, 11, 30)

        // Open-ended cycle (null endDate)
        assertNull(
            FatLossAnalytics.computeRequiredPaceToEndDate(
                today7dAvgKg = 75.7,
                targetWeightKg = 72.0,
                cycleEndDate = null,
                today = today
            )
        )

        // Missing or non-positive goal
        assertNull(
            FatLossAnalytics.computeRequiredPaceToEndDate(
                today7dAvgKg = 75.7,
                targetWeightKg = null,
                cycleEndDate = futureEnd,
                today = today
            )
        )
        assertNull(
            FatLossAnalytics.computeRequiredPaceToEndDate(
                today7dAvgKg = 75.7,
                targetWeightKg = 0.0,
                cycleEndDate = futureEnd,
                today = today
            )
        )

        // Non-deficit goal (targetWeightKg >= startWeightKg)
        assertNull(
            FatLossAnalytics.computeRequiredPaceToEndDate(
                today7dAvgKg = 75.7,
                targetWeightKg = 78.0,
                cycleEndDate = futureEnd,
                today = today,
                startWeightKg = 77.8
            )
        )

        // Goal reached at 1-decimal display precision (72.04 rounds to 72.0 <= 72.0)
        assertNull(
            FatLossAnalytics.computeRequiredPaceToEndDate(
                today7dAvgKg = 72.04,
                targetWeightKg = 72.0,
                cycleEndDate = futureEnd,
                today = today
            )
        )

        // Same-day end date (daysRemaining == 0) and past end date (daysRemaining < 0)
        assertNull(
            FatLossAnalytics.computeRequiredPaceToEndDate(
                today7dAvgKg = 75.7,
                targetWeightKg = 72.0,
                cycleEndDate = today,
                today = today
            )
        )
        assertNull(
            FatLossAnalytics.computeRequiredPaceToEndDate(
                today7dAvgKg = 75.7,
                targetWeightKg = 72.0,
                cycleEndDate = today.minusDays(1),
                today = today
            )
        )
    }

    @Test
    fun `requiredPace US1 & US2 - cycle overload resolves 7D average and stale weigh-in fallback and rejects non-recomp cycles`() {
        val cycleStart = LocalDate.of(2026, 10, 1)
        val today = LocalDate.of(2026, 10, 22)
        val endDate = LocalDate.of(2026, 11, 19) // 28 days = 4.0 weeks

        val recompCycle = Cycle(
            id = 910L,
            name = "Recomp Cut",
            startDate = cycleStart,
            endDate = endDate,
            type = CycleType.FAT_LOSS_BODYBUILDING,
            startingWeightKg = 78.0,
            targetWeightKg = 72.0
        )

        // Single stale weigh-in 10 days ago (outside [today-6, today] window) -> falls back to latest active entry (76.0 kg)
        // Remaining = 76.0 - 72.0 = 4.0 kg over 4.0 weeks -> -1.0 kg/wk
        val staleEntries = listOf(
            WeightEntry(date = LocalDate.of(2026, 10, 5), weightKg = 76.0, updatedAtMillis = 1L)
        )
        val staleResult = FatLossAnalytics.computeRequiredPaceToEndDate(staleEntries, recompCycle, today, "kg")
        assertNotNull(staleResult)
        assertEquals(-1.0, staleResult!!.requiredRateInUnit, 1e-6)
        assertEquals(
            "Required pace: -1.0 kg/wk (by 19 Nov 2026) — above 1% bodyweight/wk",
            staleResult.formatSubtitle()
        )

        // Empty active entries -> null
        assertNull(FatLossAnalytics.computeRequiredPaceToEndDate(emptyList(), recompCycle, today, "kg"))

        // Non-recomposition cycle -> null
        val strengthCycle = recompCycle.copy(type = CycleType.STRENGTH_WEIGHTLIFTING)
        assertNull(FatLossAnalytics.computeRequiredPaceToEndDate(staleEntries, strengthCycle, today, "kg"))
    }

    @Test
    fun `paceForecast - GoalReached and estimated weeks are evaluated in the display unit`() {
        val today = LocalDate.of(2026, 10, 22)
        // 72.04 kg vs 72.0 kg: equal at 1 decimal in kg, but 158.8 lbs vs 158.7 lbs leaves 0.1 lbs
        assertEquals(
            PaceForecast.GoalReached,
            FatLossAnalytics.computePaceForecast(
                currentWeightKg = 72.04,
                targetWeightKg = 72.0,
                velocityKgPerWeek = 0.2,
                today = today,
                weightUnit = "kg"
            )
        )
        val imperial = FatLossAnalytics.computePaceForecast(
            currentWeightKg = 72.04,
            targetWeightKg = 72.0,
            velocityKgPerWeek = 0.2,
            today = today,
            weightUnit = "lbs"
        )
        // 0.1 lbs remaining / 0.4 lbs/wk (0.2 kg/wk = 0.441 lbs/wk) = 0.25 weeks
        assertTrue(imperial is PaceForecast.Projected)
        assertEquals(0.25, (imperial as PaceForecast.Projected).weeks, 1e-6)

        // The hero's "lbs left" and the required pace agree with the lbs forecast: not reached yet
        assertNotNull(
            FatLossAnalytics.computeRequiredPaceToEndDate(
                today7dAvgKg = 72.04,
                targetWeightKg = 72.0,
                cycleEndDate = today.plusDays(28),
                today = today,
                weightUnit = "lbs"
            )
        )
    }

    // =========================================================================
    // Feature 004 & 007: Multi-Window Rolling Average Scale Weight Targets (7D, 14D, 21D, 28D)
    // =========================================================================

    @Test
    fun `paceAndLinearTargets US1 & US2 - 2-decimal linear expected and tomorrow scale targets for 7D 14D 21D 28D in kg and lbs`() {
        val cycleStart = LocalDate.of(2026, 9, 28)
        val today = LocalDate.of(2026, 10, 22)      // elapsedDays = 24
        val cycleEnd = LocalDate.of(2026, 11, 30)   // totalCycleDays = 63, daysRemaining = 39
        val cycle = Cycle(
            id = 4001L,
            name = "Autumn Recomp",
            startDate = cycleStart,
            endDate = cycleEnd,
            type = CycleType.FAT_LOSS_BODYBUILDING,
            startingWeightKg = 77.8,
            targetWeightKg = 72.0
        )

        // Last week 7D window [Oct 9, Oct 15]: 1 entry on Oct 15 (t-7d) = 76.22 kg
        // Today 7D window [Oct 16, Oct 22]: Oct 18 = 76.62 kg, Oct 22 (today) = 74.70 kg -> avg = 75.66 kg
        val entries = listOf(
            WeightEntry(date = cycleStart, weightKg = 77.80, updatedAtMillis = 1L),
            WeightEntry(date = LocalDate.of(2026, 10, 15), weightKg = 76.22, updatedAtMillis = 2L),
            WeightEntry(date = LocalDate.of(2026, 10, 18), weightKg = 76.62, updatedAtMillis = 3L),
            WeightEntry(date = today, weightKg = 74.70, updatedAtMillis = 4L)
        )

        val stateKg = FatLossAnalytics.compute7DayPaceTargets(entries, cycle, today, "kg")
        assertTrue("State should be Available", stateKg is PaceTargetsState.Available)
        val availKg = stateKg as PaceTargetsState.Available

        assertEquals(4, availKg.rows.size)
        assertEquals(listOf(7, 14, 21, 28), availKg.rows.map { it.windowDays })
        assertEquals(
            listOf("7D ROLLING AVERAGE", "14D ROLLING AVERAGE", "21D ROLLING AVERAGE", "28D ROLLING AVERAGE"),
            availKg.rows.map { it.rowHeader }
        )

        // Math in kg for 7D row:
        // start = 77.8, target = 72.0, today7dAvg = 75.66, remaining = 3.66, daysRemaining = 39
        // dailyRequiredRate = -3.66 / 39 = -0.0938461538... -> dailyStepDisplay = -0.09
        // Linear Today: 77.8 + (72.0 - 77.8) * (24 / 63) = 75.590476 -> 75.59 kg, delta = 75.66 - 75.59 = +0.07 kg (OFF_PACE)
        // Expected Today (scale weight to achieve 1-day step from yesterday's 7D avg [Oct 15, Oct 21] = (76.22 + 76.62) / 2 = 76.42):
        //   targetAvgToday = 76.42 - 0.0938461538 = 76.3261538
        //   priorTodayEntries in [Oct 16, Oct 21] = [76.62] (kToday = 1)
        //   W_expected_today = round2(2 * 76.3261538 - 76.62) = round2(76.0323077) = 76.03 kg
        //   delta vs today's actual weigh-in (74.70) = 74.70 - 76.03 = -1.33 kg (AHEAD)
        // Target Tomorrow (scale weight to achieve targetAvgTomorrow = 75.66 - 0.0938461538 = 75.5661538):
        //   priorTomorrowEntries in [Oct 17, Oct 22] = [76.62, 74.70] (kTomorrow = 2, sum = 151.32)
        //   W_target_tomorrow = round2(3 * 75.5661538 - 151.32) = round2(75.3784615) = 75.38 kg
        val row7d = availKg.sevenDayAvgRow
        val linear7d = row7d.linearToday as ColumnState.Value
        assertEquals(75.59, linear7d.targetWeightDisplay, 1e-6)
        assertEquals(0.07, linear7d.deltaDisplay!!, 1e-6)
        assertEquals(PaceTargetStatus.OFF_PACE, linear7d.status)
        assertTrue(linear7d.useTwoDecimals)

        val expected7d = row7d.expectedToday as ColumnState.Value
        assertEquals(76.03, expected7d.targetWeightDisplay, 1e-6)
        assertEquals(-1.33, expected7d.deltaDisplay!!, 1e-6)
        assertEquals(PaceTargetStatus.AHEAD, expected7d.status)
        assertEquals(76.42, expected7d.baselineWeightDisplay!!, 1e-6)

        val tomorrow7d = row7d.targetTomorrow as ColumnState.Value
        assertEquals(75.38, tomorrow7d.targetWeightDisplay, 1e-6)
        assertEquals(-0.09, tomorrow7d.deltaDisplay!!, 1e-6)
        assertTrue(tomorrow7d.isDailyStep)

        // Verify 14D row:
        // Today 14D window [Oct 9, Oct 22]: [76.22, 76.62, 74.70] -> avg = 227.54 / 3 = 75.846666... -> 75.85 kg
        // Prior 14D window [Sep 25, Oct 8]: [Sep 28: 77.80] -> avg = 77.80 kg
        val row14d = availKg.fourteenDayAvgRow
        val linear14d = row14d.linearToday as ColumnState.Value
        assertEquals(75.59, linear14d.targetWeightDisplay, 1e-6)
        assertEquals(0.26, linear14d.deltaDisplay!!, 1e-6) // 75.85 - 75.59 = +0.26
        assertTrue(row14d.expectedToday is ColumnState.Value)
        assertTrue(row14d.targetTomorrow is ColumnState.Value)

        // Verify 21D and 28D rows when t - 21 (Oct 1) and t - 28 (Sep 24):
        // For 21D: prior window [Sep 11, Oct 1] contains Sep 28 (77.80) -> Value!
        assertTrue(availKg.twentyOneDayAvgRow.expectedToday is ColumnState.Value)
        // For 28D: anchorDate = Sep 24 < cycleStart (Sep 28), prior window [Aug 28, Sep 24] is empty -> InsufficientHistory!
        assertEquals(ColumnState.InsufficientHistory, availKg.twentyEightDayAvgRow.expectedToday)
        assertTrue(availKg.twentyEightDayAvgRow.targetTomorrow is ColumnState.Value)

        // Verify Imperial (lbs) localization across all 4 rows
        val stateLbs = FatLossAnalytics.compute7DayPaceTargets(entries, cycle, today, "lbs")
        assertTrue(stateLbs is PaceTargetsState.Available)
        val availLbs = stateLbs as PaceTargetsState.Available
        availLbs.rows.forEach { row ->
            assertEquals("lbs", (row.linearToday as ColumnState.Value).unitLabel)
            assertEquals("lbs", (row.targetTomorrow as ColumnState.Value).unitLabel)
        }

        // Imperial scale weight solving uses unrounded display-unit sums (7D row):
        // today7dAvg = 166.8017 lbs, yesterday7dAvg = 168.4773 lbs, target = 158.7328 lbs, dailyRequiredRate = -0.206895 lbs/day
        // Expected Today: round2(2 * (168.4773 - 0.206895) - 168.9182) = round2(167.622546) = 167.62 lbs
        //   delta vs today's actual weigh-in (164.69) = -2.93 lbs (AHEAD)
        // Target Tomorrow: round2(3 * (166.8017 - 0.206889) - (168.9182 + 164.6853)) = round2(166.181062) = 166.18 lbs
        val expected7dLbs = availLbs.sevenDayAvgRow.expectedToday as ColumnState.Value
        assertEquals(167.62, expected7dLbs.targetWeightDisplay, 1e-6)
        assertEquals(-2.93, expected7dLbs.deltaDisplay!!, 1e-6)
        assertEquals(PaceTargetStatus.AHEAD, expected7dLbs.status)
        assertEquals(168.48, expected7dLbs.baselineWeightDisplay!!, 1e-6)
        assertEquals(166.18, (availLbs.sevenDayAvgRow.targetTomorrow as ColumnState.Value).targetWeightDisplay, 1e-6)
    }

    @Test
    fun `paceAndLinearTargets US3 - empty prior window inside cycle yields horizon-specific NeedsLog anchor dates`() {
        val cycleStart = LocalDate.of(2026, 9, 1)
        val today = LocalDate.of(2026, 10, 22)
        val cycleEnd = LocalDate.of(2026, 11, 30) // daysRemaining = 39
        // Only Sep 1, Oct 20, and Oct 22 are logged:
        // 7D prior window [Oct 9, Oct 15], 14D [Sep 25, Oct 8], 21D [Sep 11, Oct 1] are empty -> NeedsLog(t - N)
        // 28D prior window [Aug 28, Sep 24] contains Sep 1 (77.80):
        //   current28dAvg = (76.00 + 75.50) / 2 = 75.75, dailyRequiredRate = -3.75 / 39 = -0.0961538
        //   yesterday28dAvg over [Sep 24, Oct 21] = 76.00
        //   Expected Today: round2(2 * (76.00 - 0.0961538) - 76.00) = round2(75.8076923) = 75.81 kg
        val entries = listOf(
            WeightEntry(date = cycleStart, weightKg = 77.80, updatedAtMillis = 1L),
            WeightEntry(date = LocalDate.of(2026, 10, 20), weightKg = 76.00, updatedAtMillis = 2L),
            WeightEntry(date = today, weightKg = 75.50, updatedAtMillis = 3L)
        )

        val state = FatLossAnalytics.compute7DayPaceTargets(
            entries = entries,
            cycleStartDate = cycleStart,
            referenceDate = today,
            weightUnit = "kg",
            startingWeightKg = 77.80,
            targetWeightKg = 72.0,
            cycleEndDate = cycleEnd
        ) as PaceTargetsState.Available

        assertEquals(ColumnState.NeedsLog(requiredDate = LocalDate.of(2026, 10, 15)), state.sevenDayAvgRow.expectedToday)
        assertEquals(ColumnState.NeedsLog(requiredDate = LocalDate.of(2026, 10, 8)), state.fourteenDayAvgRow.expectedToday)
        assertEquals(ColumnState.NeedsLog(requiredDate = LocalDate.of(2026, 10, 1)), state.twentyOneDayAvgRow.expectedToday)

        val expected28d = state.twentyEightDayAvgRow.expectedToday as ColumnState.Value
        assertEquals(75.81, expected28d.targetWeightDisplay, 1e-6)
        assertEquals(76.00, expected28d.baselineWeightDisplay!!, 1e-6)

        // Target Tomorrow never depends on the prior window, so every horizon still resolves a scale weight
        state.rows.forEach { row -> assertTrue(row.targetTomorrow is ColumnState.Value) }
    }

    @Test
    fun `paceAndLinearTargets US1 - sub 0_1 daily step and scale weight solving at 2 decimal precision`() {
        val cycleStart = LocalDate.of(2026, 10, 1)
        val today = LocalDate.of(2026, 10, 15)
        val cycleEnd = today.plusDays(50) // 50 days remaining
        // Remaining = 75.0 - 72.0 = 3.0 kg over 50 days -> dailyRequiredRate = -0.06 kg/day
        // Yesterday 7D window [Oct 8, Oct 14] has Oct 8 (75.42), and prior today window [Oct 9, Oct 14] has kToday = 0 entries,
        // so Expected Today scale weight = 1 * (75.42 - 0.06) = 75.36 kg.
        // Tomorrow prior window [Oct 10, Oct 15] has kTomorrow = 1 entry (75.00 on Oct 15),
        // so Target Tomorrow scale weight to bring 2-entry average to 74.94 is 2 * 74.94 - 75.00 = 74.88 kg!
        val entries = listOf(
            WeightEntry(date = cycleStart, weightKg = 77.0, updatedAtMillis = 1L),
            WeightEntry(date = LocalDate.of(2026, 10, 8), weightKg = 75.42, updatedAtMillis = 2L),
            WeightEntry(date = today, weightKg = 75.0, updatedAtMillis = 3L)
        )
        val state = FatLossAnalytics.compute7DayPaceTargets(
            entries = entries,
            cycleStartDate = cycleStart,
            referenceDate = today,
            weightUnit = "kg",
            startingWeightKg = 77.0,
            targetWeightKg = 72.0,
            cycleEndDate = cycleEnd
        ) as PaceTargetsState.Available

        val tomorrow7d = state.sevenDayAvgRow.targetTomorrow as ColumnState.Value
        assertEquals(74.88, tomorrow7d.targetWeightDisplay, 1e-6)
        assertEquals(-0.06, tomorrow7d.deltaDisplay!!, 1e-6)

        val expected7d = state.sevenDayAvgRow.expectedToday as ColumnState.Value
        assertEquals(75.36, expected7d.targetWeightDisplay, 1e-6)
        assertEquals(-0.36, expected7d.deltaDisplay!!, 1e-6)
        assertEquals(PaceTargetStatus.AHEAD, expected7d.status)
    }

    @Test
    fun `paceAndLinearTargets US1 - dense 7D window scale weight solving reconciles exact rolling average`() {
        val cycleStart = LocalDate.of(2026, 10, 1)
        val today = LocalDate.of(2026, 10, 15)
        val cycleEnd = today.plusDays(30) // 30 days remaining
        // Prior 7D [Oct 2, Oct 8]: all 76.00 kg -> prior7dAvg = 76.00
        // Current 7D [Oct 9, Oct 15]: Oct 9..14 (6 days) all 75.50 kg, Oct 15 (today) = 75.50 kg -> today7dAvg = 75.50
        // Yesterday 7D [Oct 8, Oct 14]: Oct 8 (76.00) + Oct 9..14 (6 * 75.50 = 453.00) -> sum = 529.00, yesterday7dAvg = 529.00 / 7 = 75.5714286 kg
        // target = 72.50 -> remaining = 3.00 kg over 30 days -> dailyRequiredRate = -0.10 kg/day
        // targetAvgToday = 529.00 / 7 - 0.10 = 75.4714286 kg
        // With 6 prior days at 75.50 (sum = 453.00), W_expected_today = 7 * (529.00 / 7 - 0.10) - 453.00 = 528.30 - 453.00 = 75.30 kg!
        // Check: (453.00 + 75.30) / 7 = 528.30 / 7 = 75.4714286 kg (exactly yesterday7dAvg - 0.10)!
        // targetAvgTomorrow = 75.50 - 0.10 = 75.40 kg
        // With 6 prior days in [Oct 10, Oct 15] at 75.50 (sum = 453.00), W_target_tomorrow = 7 * 75.40 - 453.00 = 527.80 - 453.00 = 74.80 kg!
        // Check: (453.00 + 74.80) / 7 = 527.80 / 7 = 75.40 kg!
        val entries = buildList {
            add(WeightEntry(date = cycleStart, weightKg = 76.50, updatedAtMillis = 1L))
            for (d in 2..8) {
                add(WeightEntry(date = LocalDate.of(2026, 10, d), weightKg = 76.00, updatedAtMillis = d.toLong()))
            }
            for (d in 9..15) {
                add(WeightEntry(date = LocalDate.of(2026, 10, d), weightKg = 75.50, updatedAtMillis = d.toLong()))
            }
        }

        val state = FatLossAnalytics.compute7DayPaceTargets(
            entries = entries,
            cycleStartDate = cycleStart,
            referenceDate = today,
            weightUnit = "kg",
            startingWeightKg = 76.50,
            targetWeightKg = 72.50,
            cycleEndDate = cycleEnd
        ) as PaceTargetsState.Available

        val expected7d = state.sevenDayAvgRow.expectedToday as ColumnState.Value
        assertEquals(75.30, expected7d.targetWeightDisplay, 1e-6)
        assertEquals(0.20, expected7d.deltaDisplay!!, 1e-6) // 75.50 - 75.30 = +0.20
        assertEquals(PaceTargetStatus.OFF_PACE, expected7d.status)

        val tomorrow7d = state.sevenDayAvgRow.targetTomorrow as ColumnState.Value
        assertEquals(74.80, tomorrow7d.targetWeightDisplay, 1e-6)
        assertEquals(-0.10, tomorrow7d.deltaDisplay!!, 1e-6)
    }

    @Test
    fun `paceAndLinearTargets US1 - multi-horizon 7D 14D 21D 28D expected today does not diverge`() {
        // Reproduces the 35+ day multi-horizon user screenshot scenario where prior-window N-day jumps
        // previously caused Expected Today to oscillate wildly (e.g. 71.50 kg at 14D vs 94.52 kg at 28D).
        val cycleStart = LocalDate.of(2026, 9, 10)
        val today = LocalDate.of(2026, 10, 28)
        val cycleEnd = LocalDate.of(2026, 12, 20)
        val entries = buildList {
            // 49 days of realistic daily weigh-ins (Sep 10..Oct 28) with mild weekly rate variations around ~75.6-78.0 kg
            for (i in 0..48) {
                val date = cycleStart.plusDays(i.toLong())
                val baseWeight = 78.0 - (i * 0.05)
                // Add realistic mid-cycle plateau / drop so [t-2N+1, t-N] and [t-N+1, t-1] differ in pace
                val wave = when {
                    i in 20..33 -> -0.35 // faster drop in weeks 3-4
                    i in 34..47 -> 0.25  // slower plateau in weeks 5-6
                    else -> 0.0
                }
                add(WeightEntry(date = date, weightKg = baseWeight + wave, updatedAtMillis = (i + 1).toLong()))
            }
        }

        val state = FatLossAnalytics.compute7DayPaceTargets(
            entries = entries,
            cycleStartDate = cycleStart,
            referenceDate = today,
            weightUnit = "kg",
            startingWeightKg = 78.0,
            targetWeightKg = 72.0,
            cycleEndDate = cycleEnd
        ) as PaceTargetsState.Available

        assertEquals(4, state.rows.size)
        state.rows.forEach { row ->
            val expectedToday = row.expectedToday as ColumnState.Value
            val targetTomorrow = row.targetTomorrow as ColumnState.Value
            // Every horizon's Expected Today must stay within a realistic scale weight band (74.0..77.0 kg)
            // and never diverge to 71.50 kg or 94.52 kg
            assertTrue(
                "Horizon ${row.windowDays}D expectedToday (${expectedToday.targetWeightDisplay}) out of realistic bounds",
                expectedToday.targetWeightDisplay in 74.0..77.0
            )
            assertTrue(
                "Horizon ${row.windowDays}D targetTomorrow (${targetTomorrow.targetWeightDisplay}) out of realistic bounds",
                targetTomorrow.targetWeightDisplay in 74.0..77.0
            )
        }

        // Dense daily history: W_expected_today = w(t - N) + N * dailyRequiredRate (dropped-off entry plus 1-day step),
        // so values stay near the scale weights logged N days ago instead of compounding N days of prior-window drift.
        // The previous prior-window anchoring yielded 74.63 / 62.48 / 58.78 / 47.80 kg for this same history.
        // 7D:  w(Oct 21) = 76.20 - 7 * (75.9642857 - 72) / 53 = 75.6764151 -> 75.68 kg (yesterday7dAvg = 76.05)
        // 14D: w(Oct 14) = 76.55 - 14 * (76.1571429 - 72) / 53 = 75.4518868 -> 75.45 kg (yesterday14dAvg = 76.225)
        // 21D: w(Oct 7)  = 76.30 - 21 * (76.1666667 - 72) / 53 = 74.6490566 -> 74.65 kg (yesterday21dAvg = 76.20)
        // 28D: w(Sep 30) = 76.65 - 28 * (76.2375 - 72) / 53 = 74.4113208 -> 74.41 kg (yesterday28dAvg = 76.275)
        assertEquals(
            listOf(75.68, 75.45, 74.65, 74.41),
            state.rows.map { (it.expectedToday as ColumnState.Value).targetWeightDisplay }
        )
        assertEquals(
            listOf(76.05, 76.23, 76.20, 76.27),
            state.rows.map { (it.expectedToday as ColumnState.Value).baselineWeightDisplay }
        )
    }

    @Test
    fun `paceAndLinearTargets US1 - empty yesterday window falls back to prior window average for expected today`() {
        val cycleStart = LocalDate.of(2026, 9, 28)
        val today = LocalDate.of(2026, 10, 15)
        val cycleEnd = today.plusDays(50) // 50 days remaining
        // Only Sep 28 (77.00) and today (75.00) are logged.
        // 14D: prior window [Sep 18, Oct 1] contains Sep 28 -> mature, but yesterday's 14D window [Oct 1, Oct 14] is empty,
        //   so the baseline falls back to priorNdAvg = 77.00.
        //   current14dAvg [Oct 2, Oct 15] = 75.00 -> dailyRequiredRate = -(75.00 - 72.00) / 50 = -0.06
        //   kToday = 0 in [Oct 2, Oct 14] -> W_expected_today = 1 * (77.00 - 0.06) - 0 = 76.94 kg
        //   delta vs today's actual weigh-in (75.00) = -1.94 kg (AHEAD)
        val entries = listOf(
            WeightEntry(date = cycleStart, weightKg = 77.00, updatedAtMillis = 1L),
            WeightEntry(date = today, weightKg = 75.00, updatedAtMillis = 2L)
        )

        val state = FatLossAnalytics.compute7DayPaceTargets(
            entries = entries,
            cycleStartDate = cycleStart,
            referenceDate = today,
            weightUnit = "kg",
            startingWeightKg = 77.00,
            targetWeightKg = 72.0,
            cycleEndDate = cycleEnd
        ) as PaceTargetsState.Available

        assertEquals(ColumnState.NeedsLog(requiredDate = LocalDate.of(2026, 10, 8)), state.sevenDayAvgRow.expectedToday)

        val expected14d = state.fourteenDayAvgRow.expectedToday as ColumnState.Value
        assertEquals(76.94, expected14d.targetWeightDisplay, 1e-6)
        assertEquals(77.00, expected14d.baselineWeightDisplay!!, 1e-6)
        assertEquals(-1.94, expected14d.deltaDisplay!!, 1e-6)
        assertEquals(PaceTargetStatus.AHEAD, expected14d.status)

        assertEquals(ColumnState.InsufficientHistory, state.twentyOneDayAvgRow.expectedToday)
        assertEquals(ColumnState.InsufficientHistory, state.twentyEightDayAvgRow.expectedToday)
    }

    @Test
    fun `paceAndLinearTargets US3 - unlogged today computes expected today with null delta and valid target tomorrow across rows`() {
        val cycleStart = LocalDate.of(2026, 9, 28)
        val today = LocalDate.of(2026, 10, 22)
        val cycleEnd = LocalDate.of(2026, 11, 30)
        // Today (Oct 22) has no weigh-in, but Oct 15 (t-7d) and Oct 21 (yesterday) are logged
        val entries = listOf(
            WeightEntry(date = cycleStart, weightKg = 77.8, updatedAtMillis = 1L),
            WeightEntry(date = LocalDate.of(2026, 10, 15), weightKg = 76.2, updatedAtMillis = 2L),
            WeightEntry(date = LocalDate.of(2026, 10, 21), weightKg = 75.6, updatedAtMillis = 3L)
        )

        val state = FatLossAnalytics.compute7DayPaceTargets(
            entries = entries,
            cycleStartDate = cycleStart,
            referenceDate = today,
            weightUnit = "kg",
            startingWeightKg = 77.8,
            targetWeightKg = 72.0,
            cycleEndDate = cycleEnd
        ) as PaceTargetsState.Available

        // 7D Average Row:
        // Linear Today has delta (from current 7D avg over [Oct 16, Oct 22] = 75.6)
        assertTrue(state.sevenDayAvgRow.linearToday is ColumnState.Value)
        assertNotNull((state.sevenDayAvgRow.linearToday as ColumnState.Value).deltaDisplay)

        // Expected Today has a valid scale weight target, but null deltaDisplay/status because today is unlogged
        val expected7d = state.sevenDayAvgRow.expectedToday as ColumnState.Value
        assertNull(expected7d.deltaDisplay)
        assertNull(expected7d.status)

        // Target Tomorrow is still a valid ColumnState.Value (using kTomorrow = 1 entry from Oct 21)
        assertTrue(state.sevenDayAvgRow.targetTomorrow is ColumnState.Value)
    }

    @Test
    fun `paceAndLinearTargets US3 - gain week never stalls and boundary states follow precedence`() {
        val cycleStart = LocalDate.of(2026, 9, 28)
        val today = LocalDate.of(2026, 10, 22)
        val cycleEnd = LocalDate.of(2026, 11, 30)

        // Gain week: weight increased from 76.0 last week to 76.8 today (above start 76.5? No, start = 78.0, goal = 72.0, or even above start!)
        val gainEntries = listOf(
            WeightEntry(date = LocalDate.of(2026, 10, 15), weightKg = 78.2, updatedAtMillis = 1L),
            WeightEntry(date = today, weightKg = 78.5, updatedAtMillis = 2L)
        )
        val gainState = FatLossAnalytics.compute7DayPaceTargets(
            entries = gainEntries,
            cycleStartDate = cycleStart,
            referenceDate = today,
            weightUnit = "kg",
            startingWeightKg = 78.0,
            targetWeightKg = 72.0,
            cycleEndDate = cycleEnd
        )
        assertTrue("Gain week must remain Available when target and endDate are set", gainState is PaceTargetsState.Available)

        // GoalReached precedence (even if today > cycleEndDate)
        val goalReachedEntries = listOf(
            WeightEntry(date = today, weightKg = 71.9, updatedAtMillis = 1L)
        )
        assertEquals(
            PaceTargetsState.GoalReached,
            FatLossAnalytics.compute7DayPaceTargets(
                entries = goalReachedEntries,
                cycleStartDate = cycleStart,
                referenceDate = today,
                weightUnit = "kg",
                startingWeightKg = 78.0,
                targetWeightKg = 72.0,
                cycleEndDate = today.minusDays(1)
            )
        )

        // CycleEnded when today >= cycleEndDate and goal not reached
        assertEquals(
            PaceTargetsState.CycleEnded,
            FatLossAnalytics.compute7DayPaceTargets(
                entries = gainEntries,
                cycleStartDate = cycleStart,
                referenceDate = cycleEnd,
                weightUnit = "kg",
                startingWeightKg = 78.0,
                targetWeightKg = 72.0,
                cycleEndDate = cycleEnd
            )
        )

        // Unavailable when targetWeightKg or cycleEndDate is missing on Cycle
        val incompleteCycle = Cycle(
            id = 4002L,
            name = "Open Cut",
            startDate = cycleStart,
            endDate = null,
            type = CycleType.FAT_LOSS_BODYBUILDING,
            startingWeightKg = 78.0,
            targetWeightKg = 72.0
        )
        assertEquals(
            PaceTargetsState.Unavailable,
            FatLossAnalytics.compute7DayPaceTargets(gainEntries, incompleteCycle, today, "kg")
        )
    }

    // =========================================================================
    // Feature 005-multi-horizon-pace-estimations: 21D & 28D Paces, 2-Decimal Precision & Multi-Horizon Estimations
    // =========================================================================

    @Test
    fun `feature 005 US1 - compute21DayPace and compute28DayPace smoothed rolling window calculation and 2-decimal formatting`() {
        val cycleStart = LocalDate.of(2026, 9, 24)
        val today = LocalDate.of(2026, 10, 22) // 28 days elapsed
        val cycle = Cycle(
            id = 5001L,
            name = "Recomp",
            startDate = cycleStart,
            endDate = LocalDate.of(2026, 12, 28),
            type = CycleType.FAT_LOSS_BODYBUILDING,
            startingWeightKg = 77.8,
            targetWeightKg = 72.0
        )

        // Populate entries in each 7-day window:
        // [t-28, t] -> t-28 = Sep 24 (77.80 kg)
        // [t-21, t-15] -> Oct 1 (76.68 kg)
        // [t-14, t-8] -> Oct 8 (76.10 kg)
        // [t-7, t-1] -> Oct 15 (75.94 kg)
        // [t-6, t] -> Oct 22 (75.66 kg)
        val entries = listOf(
            WeightEntry(date = LocalDate.of(2026, 9, 24), weightKg = 77.80, updatedAtMillis = 1L),
            WeightEntry(date = LocalDate.of(2026, 10, 1), weightKg = 76.68, updatedAtMillis = 2L),
            WeightEntry(date = LocalDate.of(2026, 10, 8), weightKg = 76.10, updatedAtMillis = 3L),
            WeightEntry(date = LocalDate.of(2026, 10, 15), weightKg = 75.94, updatedAtMillis = 4L),
            WeightEntry(date = today, weightKg = 75.66, updatedAtMillis = 5L)
        )

        val pace7 = FatLossAnalytics.compute7DayPace(entries, cycle, today)
        val pace14 = FatLossAnalytics.compute14DayPace(entries, cycle, today)
        val pace21 = FatLossAnalytics.compute21DayPace(entries, cycle, today)
        val pace28 = FatLossAnalytics.compute28DayPace(entries, cycle, today)

        assertNotNull(pace7)
        assertNotNull(pace14)
        assertNotNull(pace21)
        assertNotNull(pace28)

        // 7D: (75.66 - 75.94) / 1 = -0.28 kg/wk
        // 14D: (75.66 - 76.10) / 2 = -0.22 kg/wk
        // 21D: (75.66 - 76.68) / 3 = -0.34 kg/wk
        // 28D: (75.66 - 77.80) / 4 = -0.535 -> -0.54 kg/wk
        // Notice 7D (-0.28) and 14D (-0.22) both round to -0.3 / -0.2 at 1-decimal, or -0.24 & -0.21 both round to -0.2 at 1-decimal,
        // while formatDisplay2 distinguishes them cleanly!
        assertEquals("-0.28 kg/wk", pace7!!.formatDisplay2("kg"))
        assertEquals("-0.22 kg/wk", pace14!!.formatDisplay2("kg"))
        assertEquals("-0.34 kg/wk", pace21!!.formatDisplay2("kg"))
        assertEquals("-0.54 kg/wk", pace28!!.formatDisplay2("kg"))
        assertEquals(21L, pace21.elapsedDays)
        assertEquals(28L, pace28.elapsedDays)

        // Verify 2-decimal differentiation for -0.24 vs -0.21 kg/wk (which both collapse to -0.2 kg/wk at 1-decimal)
        val pA = pace7.copy(rateKgPerWeek = -0.24)
        val pB = pace14.copy(rateKgPerWeek = -0.21)
        assertEquals("-0.2 kg/wk", pA.formatDisplay("kg"))
        assertEquals("-0.2 kg/wk", pB.formatDisplay("kg"))
        assertEquals("-0.24 kg/wk", pA.formatDisplay2("kg"))
        assertEquals("-0.21 kg/wk", pB.formatDisplay2("kg"))

        // Neutral and gain 2-decimal formatting
        val pNeutral = pace7.copy(rateKgPerWeek = 0.0, trend = PaceTrend.NEUTRAL)
        val pGain = pace7.copy(rateKgPerWeek = 0.15, trend = PaceTrend.GAIN)
        assertEquals("0.00 kg/wk", pNeutral.formatDisplay2("kg"))
        assertEquals("+0.15 kg/wk", pGain.formatDisplay2("kg"))
    }

    @Test
    fun `feature 005 US1 - 21D and 28D cold-start nullability and subtitles`() {
        val cycleStart = LocalDate.of(2026, 10, 8)
        val today = LocalDate.of(2026, 10, 22) // Only 14 days elapsed since cycle start and no pre-cycle logs
        val cycle = Cycle(
            id = 5002L,
            name = "Recomp",
            startDate = cycleStart,
            type = CycleType.FAT_LOSS_BODYBUILDING,
            startingWeightKg = 77.8,
            targetWeightKg = 72.0
        )
        val entries = listOf(
            WeightEntry(date = cycleStart, weightKg = 77.8, updatedAtMillis = 1L),
            WeightEntry(date = LocalDate.of(2026, 10, 15), weightKg = 76.2, updatedAtMillis = 2L),
            WeightEntry(date = today, weightKg = 75.7, updatedAtMillis = 3L)
        )

        assertNotNull(FatLossAnalytics.compute7DayPace(entries, cycle, today))
        assertNotNull(FatLossAnalytics.compute14DayPace(entries, cycle, today))
        assertNull(FatLossAnalytics.compute21DayPace(entries, cycle, today))
        assertNull(FatLossAnalytics.compute28DayPace(entries, cycle, today))

        assertEquals(
            "Needs weigh-in 21+ days ago",
            FatLossAnalytics.format21DayPaceSubtitle(null, null, "kg", today, useTwoDecimals = true)
        )
        assertEquals(
            "Needs weigh-in 28+ days ago",
            FatLossAnalytics.format28DayPaceSubtitle(null, null, "kg", today, useTwoDecimals = true)
        )

        // When pace is non-null and comparison is null, returns "Smoothed trend"
        val pace21 = PaceRateResult(
            rateKgPerWeek = -0.34,
            anchorWeightKg = 76.68,
            anchorDate = today.minusDays(21),
            currentWeightKg = 75.66,
            currentDate = today,
            elapsedDays = 21L,
            trend = PaceTrend.LOSS
        )
        assertEquals(
            "Smoothed trend",
            FatLossAnalytics.format21DayPaceSubtitle(null, pace21, "kg", today, useTwoDecimals = true)
        )

        // When comparison is non-null and percentChange is null, useTwoDecimals = true formats 2-decimal delta
        val comp = PaceComparisonResult(
            currentRateKgPerWeek = -0.34,
            priorRateKgPerWeek = -0.26,
            deltaRateKgPerWeek = -0.08,
            percentChange = null,
            isAcceleratingDeficit = true,
            priorDate = today.minusDays(7),
            isConsecutive = false,
            isExactDayMatch = true
        )
        assertEquals(
            "-0.08 kg/wk vs last Thu",
            FatLossAnalytics.format21DayPaceSubtitle(comp, pace21, "kg", today, useTwoDecimals = true)
        )
    }

    @Test
    fun `feature 005 US2 - computeMultiHorizonForecast reconciles all 4 horizons with 2-decimal precision and required pace`() {
        val cycleStart = LocalDate.of(2026, 9, 28)
        val today = LocalDate.of(2026, 10, 22) // 24 days elapsed (7D, 14D, 21D available; 28D Needs 28+ days)
        val cycleEnd = LocalDate.of(2026, 12, 28) // 67 days = 9.5714 weeks remaining -> 9.6w left
        val cycle = Cycle(
            id = 5003L,
            name = "Recomp",
            startDate = cycleStart,
            endDate = cycleEnd,
            type = CycleType.FAT_LOSS_BODYBUILDING,
            startingWeightKg = 77.8,
            targetWeightKg = 72.0
        )

        // Remaining weight: today 7D avg = 75.66 kg -> rounds to 75.7 kg -> 75.7 - 72.0 = 3.7 kg left
        // Required pace: -3.7 / (67/7) = -0.38656 -> -0.39 kg/wk
        val entries = listOf(
            WeightEntry(date = LocalDate.of(2026, 10, 1), weightKg = 76.68, updatedAtMillis = 1L), // t-21 window
            WeightEntry(date = LocalDate.of(2026, 10, 8), weightKg = 76.10, updatedAtMillis = 2L), // t-14 window
            WeightEntry(date = LocalDate.of(2026, 10, 15), weightKg = 75.94, updatedAtMillis = 3L), // t-7 window
            WeightEntry(date = today, weightKg = 75.66, updatedAtMillis = 4L) // today window
        )

        val state = FatLossAnalytics.computeMultiHorizonForecast(entries, cycle, today, weightUnit = "kg")
        assertTrue(state is MultiHorizonForecastState.Active)
        val active = state as MultiHorizonForecastState.Active

        assertEquals(
            "Target: 28 Dec 2026 (9.6w left) · Required: -0.39 kg/wk",
            active.benchmarkHeader
        )
        assertEquals(4, active.rows.size)
        assertEquals(listOf(7, 14, 21, 28), active.rows.map { it.windowDays })

        // Row 0: 7D (-0.28 kg/wk) -> 3.7 / 0.28 = 13.214 -> 13.2 wks (late by 13.2 - 9.6 = +3.6w late)
        val row7 = active.rows[0]
        assertEquals("7D (-0.28 kg/wk)", row7.formatLeftLabel())
        assertTrue(row7.state is HorizonRowState.Projected)
        val proj7 = row7.state as HorizonRowState.Projected
        assertEquals(13.2, proj7.weeks, 1e-6)
        assertEquals(today.plusDays(kotlin.math.round(13.2 * 7.0).toLong()), proj7.arrivalDate)
        assertEquals("+3.6w late", proj7.badgeText)
        assertFalse(proj7.isOnTrack)
        assertEquals((0.28 / 0.39).toFloat(), proj7.sufficiencyFraction, 1e-3f)

        // Row 1: 14D (-0.22 kg/wk) -> 3.7 / 0.22 = 16.818 -> 16.8 wks (+7.2w late)
        val row14 = active.rows[1]
        assertEquals("14D (-0.22 kg/wk)", row14.formatLeftLabel())
        val proj14 = row14.state as HorizonRowState.Projected
        assertEquals(16.8, proj14.weeks, 1e-6)
        assertEquals("+7.2w late", proj14.badgeText)

        // Row 2: 21D (-0.34 kg/wk) -> 3.7 / 0.34 = 10.882 -> 10.9 wks (+1.3w late)
        val row21 = active.rows[2]
        assertEquals("21D (-0.34 kg/wk)", row21.formatLeftLabel())
        val proj21 = row21.state as HorizonRowState.Projected
        assertEquals(10.9, proj21.weeks, 1e-6)
        assertEquals("+1.3w late", proj21.badgeText)

        // Row 3: 28D (—) -> Needs 28+ days
        val row28 = active.rows[3]
        assertEquals("28D (—)", row28.formatLeftLabel())
        assertEquals("Needs 28+ days", row28.formatRightValue())
        assertTrue(row28.state is HorizonRowState.NotEnoughData)
    }

    @Test
    fun `feature 005 US2 - computeMultiHorizonForecast handles open-ended cycle, ahead pace, stalled, over 1 year, and boundary states`() {
        val cycleStart = LocalDate.of(2026, 9, 20)
        val today = LocalDate.of(2026, 10, 22)

        // 1. Open-ended cycle (endDate == null) with mixed row states:
        // 7D: fast loss (-0.50 kg/wk) -> 3.7 / 0.50 = 7.4 wks
        // 14D: stalled (+0.10 kg/wk) -> Pace stalled
        // 21D: very slow loss (-0.06 kg/wk) -> 3.7 / 0.06 = 61.7 wks -> "> 1 year"
        // 28D: moderate loss (-0.25 kg/wk) -> 3.7 / 0.25 = 14.8 wks
        val openCycle = Cycle(
            id = 5004L,
            name = "Open Cut",
            startDate = cycleStart,
            endDate = null,
            type = CycleType.FAT_LOSS_BODYBUILDING,
            startingWeightKg = 78.0,
            targetWeightKg = 72.0
        )
        val mixedEntries = listOf(
            WeightEntry(date = today.minusDays(28), weightKg = 76.66, updatedAtMillis = 1L), // 28D: (75.66 - 76.66)/4 = -0.25
            WeightEntry(date = today.minusDays(21), weightKg = 75.84, updatedAtMillis = 2L), // 21D: (75.66 - 75.84)/3 = -0.06
            WeightEntry(date = today.minusDays(14), weightKg = 75.46, updatedAtMillis = 3L), // 14D: (75.66 - 75.46)/2 = +0.10
            WeightEntry(date = today.minusDays(7), weightKg = 76.16, updatedAtMillis = 4L),  // 7D:  (75.66 - 76.16)/1 = -0.50
            WeightEntry(date = today, weightKg = 75.66, updatedAtMillis = 5L)
        )

        val openState = FatLossAnalytics.computeMultiHorizonForecast(mixedEntries, openCycle, today, weightUnit = "kg")
        assertTrue(openState is MultiHorizonForecastState.Active)
        val openActive = openState as MultiHorizonForecastState.Active
        assertEquals("Goal: 72.0 kg (-3.7 kg left)", openActive.benchmarkHeader)

        // 7D is fastest deficit (-0.50 kg/wk), so sufficiencyFraction = 1.0f, badgeText = null
        val r7 = openActive.rows[0]
        val p7 = r7.state as HorizonRowState.Projected
        assertEquals(7.4, p7.weeks, 1e-6)
        assertEquals(1.0f, p7.sufficiencyFraction, 1e-4f)
        assertNull(p7.badgeText)

        // 14D is Stalled (+0.10 kg/wk)
        val r14 = openActive.rows[1]
        assertEquals("14D (+0.10 kg/wk)", r14.formatLeftLabel())
        assertEquals("Pace stalled", r14.formatRightValue())
        assertEquals(HorizonRowState.Stalled(windowDays = 14, paceDisplay2 = "+0.10 kg/wk"), r14.state)

        // 21D is > 1 year (61.7 wks)
        val r21 = openActive.rows[2]
        assertEquals("21D (-0.06 kg/wk)", r21.formatLeftLabel())
        assertEquals("> 1 year", r21.formatRightValue())
        val p21 = r21.state as HorizonRowState.Projected
        assertTrue(p21.isOverOneYear)

        // 28D is 0.25 / 0.50 = 0.5f sufficiencyFraction
        val r28 = openActive.rows[3]
        val p28 = r28.state as HorizonRowState.Projected
        assertEquals(14.8, p28.weeks, 1e-6)
        assertEquals(0.5f, p28.sufficiencyFraction, 1e-4f)

        // 2. Ahead of target date ("On time (-2.2w)")
        val datedCycle = openCycle.copy(endDate = today.plusDays(67)) // 9.6w left
        val datedState = FatLossAnalytics.computeMultiHorizonForecast(mixedEntries, datedCycle, today, weightUnit = "kg") as MultiHorizonForecastState.Active
        val dated7 = datedState.rows[0].state as HorizonRowState.Projected
        assertTrue(dated7.isOnTrack)
        assertEquals(1.0f, dated7.sufficiencyFraction, 1e-4f)
        assertEquals("On time (-2.2w)", dated7.badgeText)

        // 21D (> 1 year) with target date shows "> 1 yr late"
        val dated21 = datedState.rows[2].state as HorizonRowState.Projected
        assertFalse(dated21.isOnTrack)
        assertEquals("> 1 yr late", dated21.badgeText)

        // 3. Boundary states: GoalReached, CycleEnded, NotEnoughData
        val goalEntries = listOf(WeightEntry(date = today, weightKg = 71.9, updatedAtMillis = 1L))
        assertEquals(
            MultiHorizonForecastState.GoalReached,
            FatLossAnalytics.computeMultiHorizonForecast(goalEntries, datedCycle, today, weightUnit = "kg")
        )

        val endedCycle = openCycle.copy(endDate = today.minusDays(1))
        assertEquals(
            MultiHorizonForecastState.CycleEnded,
            FatLossAnalytics.computeMultiHorizonForecast(mixedEntries, endedCycle, today, weightUnit = "kg")
        )

        val coldStartEntries = listOf(WeightEntry(date = today, weightKg = 75.7, updatedAtMillis = 1L))
        val coldCycle = openCycle.copy(startDate = today.minusDays(2))
        assertEquals(
            MultiHorizonForecastState.NotEnoughData,
            FatLossAnalytics.computeMultiHorizonForecast(coldStartEntries, coldCycle, today, weightUnit = "kg")
        )
    }
}
