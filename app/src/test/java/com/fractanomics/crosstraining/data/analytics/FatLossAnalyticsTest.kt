package com.fractanomics.crosstraining.data.analytics

import com.fractanomics.crosstraining.data.model.BlockKind
import com.fractanomics.crosstraining.data.model.BlockSet
import com.fractanomics.crosstraining.data.model.BlockWithSets
import com.fractanomics.crosstraining.data.model.DailyLog
import com.fractanomics.crosstraining.data.model.Exercise
import com.fractanomics.crosstraining.data.model.ExerciseCategory
import com.fractanomics.crosstraining.data.model.MetricType
import com.fractanomics.crosstraining.data.model.Session
import com.fractanomics.crosstraining.data.model.SessionBlock
import com.fractanomics.crosstraining.data.model.SessionWithBlocks
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.DayOfWeek
import java.time.LocalDate

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
}
