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
    fun `scenario b - sparse anchor selection selects closest entry on or before cutoff`() {
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

        // Then the 7-day window selects the t-8 entry as the anchor (normalized over 8 days)
        assertNotNull(pace7d)
        assertEquals(t.minusDays(8), pace7d!!.anchorDate)
        assertEquals(8L, pace7d.elapsedDays)
        // Rate: (83.0 - 84.0) / 8 * 7 = -0.875 kg/wk
        assertEquals(-0.875, pace7d.rateKgPerWeek, 0.001)

        // And the 14-day window selects the t-16 entry (normalized over 16 days)
        assertNotNull(pace14d)
        assertEquals(t.minusDays(16), pace14d!!.anchorDate)
        assertEquals(16L, pace14d.elapsedDays)
        // Rate: (83.0 - 85.0) / 16 * 7 = -0.875 kg/wk
        assertEquals(-0.875, pace14d.rateKgPerWeek, 0.001)
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

        // Cycle velocity: (85.0 - 84.0) / 10 * 7 = 0.7 kg/wk
        // Remaining: 84.0 - 80.0 = 4.0 kg. Weeks: 4.0 / 0.7 = 5.7142857...
        assertEquals(4.0 / 0.7, projected.weeks, 0.001)
        assertEquals("Estimated: 5.7 weeks", projected.displayText)
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
            WeightEntry(date = LocalDate.of(2026, 10, 1), weightKg = 85.0, updatedAtMillis = 1L, deletedAtMillis = 999L), // soft deleted
            WeightEntry(date = today, weightKg = 83.0, updatedAtMillis = 2L)
        )

        // Only 1 non-deleted entry exists -> NotEnoughData
        assertNull(FatLossAnalytics.compute7DayPace(entries, cycle, today))
        assertEquals(PaceForecast.NotEnoughData, FatLossAnalytics.computePaceForecast(entries, cycle, today))
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
}

