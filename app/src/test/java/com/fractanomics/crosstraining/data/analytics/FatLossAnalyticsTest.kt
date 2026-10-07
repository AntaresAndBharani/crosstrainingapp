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
        // - compute7DayPace returns rate -0.8 kg/wk (± 1e-6)
        assertNotNull(pace7)
        assertEquals(-0.8, pace7!!.rateKgPerWeek, 1e-6)
        assertEquals(0.8, pace7.deficitVelocityKgPerWeek, 1e-6)
        // - The forecast selects anchor7 (2026-09-29, 75.8 kg) with Δdays = 7
        // - V_active = 0.8 kg/wk (± 1e-6)
        // - Forecast returns PaceForecast.Projected(weeks = 10.0, targetDate = 2026-12-15)
        assertTrue(forecast is PaceForecast.Projected)
        val projected = forecast as PaceForecast.Projected
        assertEquals(10.0, projected.weeks, 1e-6)
        assertEquals(LocalDate.of(2026, 12, 15), projected.targetDate)
        // - displayText is "Estimated: 10.0 weeks"
        assertEquals("Estimated: 10.0 weeks", projected.displayText)
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
        // with 10.0 kg remaining (rawWeeks = 200.0)
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
            WeightEntry(date = startDate, weightKg = 90.1, updatedAtMillis = 1L),
            WeightEntry(date = today.minusDays(7), weightKg = 90.05, updatedAtMillis = 2L),
            WeightEntry(date = today, weightKg = 90.00, updatedAtMillis = 3L)
        )

        assertNull(FatLossAnalytics.compute14DayPace(entries, cycle, today))

        // When computePaceForecast is evaluated
        val forecast = FatLossAnalytics.computePaceForecast(entries, cycle, today)

        // Then the engine returns PaceForecast.Projected(weeks = 52.0, targetDate = null, rawWeeks = 200.0)
        assertNotNull(forecast)
        assertTrue(forecast is PaceForecast.Projected)
        val projected = forecast as PaceForecast.Projected
        assertEquals(52.0, projected.weeks, 1e-6)
        assertNull(projected.targetDate)
        assertEquals(200.0, projected.rawWeeks, 1e-6)
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

        // - V_active ≈ 1.011 kg/wk, remainingKg = 8.214 kg, and rawWeeks ≈ 8.12 weeks (± 0.05)
        // - rawWeeks (8.12) is strictly less than the unmitigated raw baseline ((76.5 - 67.0) / 1.011 ≈ 9.40 weeks)
        assertTrue(forecast is PaceForecast.Projected)
        val projected = forecast as PaceForecast.Projected
        val expectedVActive = ((77.8 - 76.5) / 9.0) * 7.0 // ≈ 1.011111...
        val expectedRemaining = (526.5 / 7.0) - 67.0       // ≈ 8.2142857...
        val expectedRawWeeks = expectedRemaining / expectedVActive // ≈ 8.124...
        assertEquals(expectedRawWeeks, projected.rawWeeks, 0.05)
        assertEquals(8.12, projected.weeks, 0.05)

        val unmitigatedRawWeeks = (76.5 - 67.0) / expectedVActive
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

        val paceComparison = FatLossAnalytics.compute7DayPaceComparison(entries, cycleStartDate)
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

        val paceComparison = FatLossAnalytics.compute7DayPaceComparison(entries, cycleStartDate, day2)
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
        val cycleStartDate = LocalDate.of(2026, 9, 29)
        val entries = listOf(
            WeightEntry(date = LocalDate.of(2026, 9, 29), weightKg = 80.0, updatedAtMillis = 1L),
            WeightEntry(date = LocalDate.of(2026, 9, 30), weightKg = 80.0, updatedAtMillis = 2L),
            WeightEntry(date = LocalDate.of(2026, 10, 6), weightKg = 79.3, updatedAtMillis = 3L),
            WeightEntry(date = LocalDate.of(2026, 10, 7), weightKg = 79.2, updatedAtMillis = 4L)
        )

        val comp = FatLossAnalytics.compute7DayPaceComparison(entries, cycleStartDate, LocalDate.of(2026, 10, 7))
        assertNotNull(comp)
        assertEquals(-0.8, comp!!.currentRateKgPerWeek, 1e-4)
        assertEquals(-0.7, comp.priorRateKgPerWeek, 1e-4)
        assertEquals(-0.1, comp.deltaRateKgPerWeek, 1e-4)
        assertEquals(14.3, comp.percentChange!!, 0.1)
        assertTrue(comp.isAcceleratingDeficit)
        assertTrue(comp.isConsecutive)

        val subtitle = FatLossAnalytics.format7DayPaceSubtitle(comp, "kg", LocalDate.of(2026, 10, 7))
        assertEquals("14% faster loss vs yesterday", subtitle)
    }

    // =========================================================================
    // Issue #596: 7-Day Pace Targets for Daily Expected Weight & Tomorrow Target
    // =========================================================================

    @Test
    fun `issue 596 - scenario 1 - normal active progress weigh-in-only fixture`() {
        val cycleStartDate = LocalDate.of(2026, 9, 28)
        val t = LocalDate.of(2026, 10, 7)
        val entries = listOf(
            WeightEntry(date = LocalDate.of(2026, 9, 29), weightKg = 75.91, updatedAtMillis = 1L),
            WeightEntry(date = LocalDate.of(2026, 9, 30), weightKg = 77.3, updatedAtMillis = 2L),
            WeightEntry(date = LocalDate.of(2026, 10, 1), weightKg = 76.9, updatedAtMillis = 3L),
            WeightEntry(date = LocalDate.of(2026, 10, 6), weightKg = 75.0, updatedAtMillis = 4L),
            WeightEntry(date = LocalDate.of(2026, 10, 7), weightKg = 76.5, updatedAtMillis = 5L)
        )

        val state = FatLossAnalytics.compute7DayPaceTargets(entries, cycleStartDate, t, "kg")
        assertTrue("State must be Available", state is PaceTargetsState.Available)
        val avail = state as PaceTargetsState.Available

        assertTrue("today must be Value", avail.today is ColumnState.Value)
        val todayVal = avail.today as ColumnState.Value
        assertEquals(76.4, todayVal.targetWeightDisplay, 1e-4)
        assertEquals(0.1, todayVal.deltaDisplay!!, 1e-4)
        assertEquals(PaceTargetStatus.OFF_PACE, todayVal.status)
        assertEquals("kg", todayVal.unitLabel)

        assertTrue("tomorrow must be Value", avail.tomorrow is ColumnState.Value)
        val tomorrowVal = avail.tomorrow as ColumnState.Value
        assertEquals(76.1, tomorrowVal.targetWeightDisplay, 1e-4)
        assertEquals(-0.4, tomorrowVal.deltaDisplay!!, 1e-4)
        assertNull(tomorrowVal.status)
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

        val state = FatLossAnalytics.compute7DayPaceTargets(entries, cycleStartDate, t, "kg")
        assertTrue("State must be Available", state is PaceTargetsState.Available)
        val avail = state as PaceTargetsState.Available

        assertTrue("today must be NeedsLog", avail.today is ColumnState.NeedsLog)
        val todayNeeds = avail.today as ColumnState.NeedsLog
        assertEquals(LocalDate.of(2026, 9, 30), todayNeeds.requiredDate)

        assertTrue("tomorrow must be Value", avail.tomorrow is ColumnState.Value)
        val tomorrowVal = avail.tomorrow as ColumnState.Value
        assertEquals(76.1, tomorrowVal.targetWeightDisplay, 1e-4)
        assertEquals(-0.4, tomorrowVal.deltaDisplay!!, 1e-4)
        assertEquals("kg", tomorrowVal.unitLabel)
    }

    @Test
    fun `issue 596 - scenario 3 - missing t minus 6d log triggers tomorrow fallback`() {
        val cycleStartDate = LocalDate.of(2026, 9, 28)
        val t = LocalDate.of(2026, 10, 7)
        val entries = listOf(
            WeightEntry(date = LocalDate.of(2026, 9, 29), weightKg = 75.91, updatedAtMillis = 1L),
            WeightEntry(date = LocalDate.of(2026, 9, 30), weightKg = 77.3, updatedAtMillis = 2L),
            WeightEntry(date = LocalDate.of(2026, 10, 6), weightKg = 75.0, updatedAtMillis = 4L),
            WeightEntry(date = LocalDate.of(2026, 10, 7), weightKg = 76.5, updatedAtMillis = 5L)
        )

        val state = FatLossAnalytics.compute7DayPaceTargets(entries, cycleStartDate, t, "kg")
        assertTrue("State must be Available", state is PaceTargetsState.Available)
        val avail = state as PaceTargetsState.Available

        assertTrue("tomorrow must be Value", avail.tomorrow is ColumnState.Value)
        val tomorrowVal = avail.tomorrow as ColumnState.Value
        assertEquals(76.4, tomorrowVal.targetWeightDisplay, 1e-4)
        assertEquals(-0.1, tomorrowVal.deltaDisplay!!, 1e-4)
    }

    @Test
    fun `issue 596 - scenario 4 - early cycle with under 7 days history returns unavailable`() {
        val cycleStartDate = LocalDate.of(2026, 10, 1)
        val t = LocalDate.of(2026, 10, 4)
        val entries = (1..4).map { day ->
            WeightEntry(date = LocalDate.of(2026, 10, day), weightKg = 80.0 - day * 0.2, updatedAtMillis = day.toLong())
        }

        val state = FatLossAnalytics.compute7DayPaceTargets(entries, cycleStartDate, t, "kg")
        assertEquals(PaceTargetsState.Unavailable, state)
    }

    @Test
    fun `issue 596 - scenario 5 - stalled or positive pace returns stalled`() {
        val cycleStartDate = LocalDate.of(2026, 9, 28)
        val t = LocalDate.of(2026, 10, 7)
        val entries = listOf(
            WeightEntry(date = LocalDate.of(2026, 9, 30), weightKg = 77.0, updatedAtMillis = 1L),
            WeightEntry(date = LocalDate.of(2026, 10, 7), weightKg = 77.2, updatedAtMillis = 2L)
        )

        val state = FatLossAnalytics.compute7DayPaceTargets(entries, cycleStartDate, t, "kg")
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

        val state = FatLossAnalytics.compute7DayPaceTargets(entries, cycleStartDate, t, "kg")
        assertTrue("State must be Available", state is PaceTargetsState.Available)
        val avail = state as PaceTargetsState.Available

        assertTrue(avail.today is ColumnState.Value)
        val todayVal = avail.today as ColumnState.Value
        assertEquals(76.4, todayVal.targetWeightDisplay, 1e-4)
        assertNull(todayVal.deltaDisplay)
        assertNull(todayVal.status)

        assertTrue(avail.tomorrow is ColumnState.Value)
        val tomorrowVal = avail.tomorrow as ColumnState.Value
        assertNull(tomorrowVal.deltaDisplay)

        val entriesWithoutTMinus6 = entries.filterNot { it.date == LocalDate.of(2026, 10, 1) }
        val stateWithoutTMinus6 = FatLossAnalytics.compute7DayPaceTargets(entriesWithoutTMinus6, cycleStartDate, t, "kg")
        assertTrue(stateWithoutTMinus6 is PaceTargetsState.Available)
        val avail2 = stateWithoutTMinus6 as PaceTargetsState.Available
        assertTrue(avail2.tomorrow is ColumnState.NeedsLog)
        assertEquals(LocalDate.of(2026, 10, 1), (avail2.tomorrow as ColumnState.NeedsLog).requiredDate)
    }

    @Test
    fun `issue 596 - scenario 7 - boundary attainment actual less than target tomorrow`() {
        val cycleStartDate = LocalDate.of(2026, 9, 28)
        val t = LocalDate.of(2026, 10, 7)
        val entries = listOf(
            WeightEntry(date = LocalDate.of(2026, 9, 30), weightKg = 77.3, updatedAtMillis = 1L),
            WeightEntry(date = LocalDate.of(2026, 10, 1), weightKg = 77.6, updatedAtMillis = 2L),
            WeightEntry(date = LocalDate.of(2026, 10, 7), weightKg = 75.8, updatedAtMillis = 3L)
        )

        val state = FatLossAnalytics.compute7DayPaceTargets(entries, cycleStartDate, t, "kg")
        assertTrue("State must be Available", state is PaceTargetsState.Available)
        val avail = state as PaceTargetsState.Available

        assertTrue("tomorrow must be AlreadyBelowTarget", avail.tomorrow is ColumnState.AlreadyBelowTarget)
        val tomorrowBelow = avail.tomorrow as ColumnState.AlreadyBelowTarget
        assertEquals(76.1, tomorrowBelow.targetWeightDisplay, 1e-4)
        assertEquals(0.3, tomorrowBelow.marginDisplay, 1e-4)
        assertEquals("kg", tomorrowBelow.unitLabel)

        assertEquals(ColumnState.InsufficientHistory, avail.today)
    }

    @Test
    fun `issue 596 - scenario 7b - rounded equality at target tomorrow overnight label hidden`() {
        val cycleStartDate = LocalDate.of(2026, 9, 28)
        val t = LocalDate.of(2026, 10, 7)
        val entries = listOf(
            WeightEntry(date = LocalDate.of(2026, 9, 29), weightKg = 75.91, updatedAtMillis = 1L),
            WeightEntry(date = LocalDate.of(2026, 9, 30), weightKg = 77.3, updatedAtMillis = 2L),
            WeightEntry(date = LocalDate.of(2026, 10, 1), weightKg = 77.3, updatedAtMillis = 3L),
            WeightEntry(date = LocalDate.of(2026, 10, 6), weightKg = 75.0, updatedAtMillis = 4L),
            WeightEntry(date = LocalDate.of(2026, 10, 7), weightKg = 76.5, updatedAtMillis = 5L)
        )

        val state = FatLossAnalytics.compute7DayPaceTargets(entries, cycleStartDate, t, "kg")
        assertTrue(state is PaceTargetsState.Available)
        val avail = state as PaceTargetsState.Available

        assertTrue(avail.tomorrow is ColumnState.Value)
        val tomorrowVal = avail.tomorrow as ColumnState.Value
        assertEquals(76.5, tomorrowVal.targetWeightDisplay, 1e-4)
        assertNull(tomorrowVal.deltaDisplay)
        assertNull(tomorrowVal.status)
        assertEquals("kg", tomorrowVal.unitLabel)
    }

    @Test
    fun `issue 596 - scenario 8 - exactly on pace rounded equality`() {
        val cycleStartDate = LocalDate.of(2026, 9, 28)
        val t = LocalDate.of(2026, 10, 7)
        val entries = listOf(
            WeightEntry(date = LocalDate.of(2026, 9, 29), weightKg = 75.91, updatedAtMillis = 1L),
            WeightEntry(date = LocalDate.of(2026, 9, 30), weightKg = 77.3, updatedAtMillis = 2L),
            WeightEntry(date = LocalDate.of(2026, 10, 1), weightKg = 76.9, updatedAtMillis = 3L),
            WeightEntry(date = LocalDate.of(2026, 10, 6), weightKg = 75.0, updatedAtMillis = 4L),
            WeightEntry(date = LocalDate.of(2026, 10, 7), weightKg = 76.4, updatedAtMillis = 5L)
        )

        val state = FatLossAnalytics.compute7DayPaceTargets(entries, cycleStartDate, t, "kg")
        assertTrue(state is PaceTargetsState.Available)
        val avail = state as PaceTargetsState.Available

        assertTrue(avail.today is ColumnState.Value)
        val todayVal = avail.today as ColumnState.Value
        assertEquals(76.4, todayVal.targetWeightDisplay, 1e-4)
        assertEquals(0.0, todayVal.deltaDisplay!!, 1e-4)
        assertEquals(PaceTargetStatus.ON_PACE, todayVal.status)

        assertTrue(avail.tomorrow is ColumnState.Value)
        val tomorrowVal = avail.tomorrow as ColumnState.Value
        assertEquals(76.0, tomorrowVal.targetWeightDisplay, 1e-4)
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

        val state = FatLossAnalytics.compute7DayPaceTargets(entries, cycleStartDate, t, "lbs")
        assertTrue(state is PaceTargetsState.Available)
        val avail = state as PaceTargetsState.Available

        assertTrue(avail.today is ColumnState.Value)
        val todayVal = avail.today as ColumnState.Value
        assertEquals(168.4, todayVal.targetWeightDisplay, 1e-4)
        assertEquals(0.2, todayVal.deltaDisplay!!, 1e-4)
        assertEquals(PaceTargetStatus.OFF_PACE, todayVal.status)
        assertEquals("lbs", todayVal.unitLabel)

        assertTrue(avail.tomorrow is ColumnState.Value)
        val tomorrowVal = avail.tomorrow as ColumnState.Value
        assertEquals(167.8, tomorrowVal.targetWeightDisplay, 1e-4)
        assertEquals(-0.9, tomorrowVal.deltaDisplay!!, 1e-4)
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

        val entriesV1 = baseEntries + WeightEntry(date = t, weightKg = 76.4, updatedAtMillis = 5L)
        val stateV1 = FatLossAnalytics.compute7DayPaceTargets(entriesV1, cycleStartDate, t, "kg") as PaceTargetsState.Available
        val todayV1 = stateV1.today as ColumnState.Value
        assertEquals(76.4, todayV1.targetWeightDisplay, 1e-4)
        assertEquals(0.0, todayV1.deltaDisplay!!, 1e-4)
        assertEquals(PaceTargetStatus.ON_PACE, todayV1.status)

        val entriesV2 = baseEntries + WeightEntry(date = t, weightKg = 76.8, updatedAtMillis = 6L)
        val stateV2 = FatLossAnalytics.compute7DayPaceTargets(entriesV2, cycleStartDate, t, "kg") as PaceTargetsState.Available
        val todayV2 = stateV2.today as ColumnState.Value
        assertEquals(76.4, todayV2.targetWeightDisplay, 1e-4)
        assertEquals(0.4, todayV2.deltaDisplay!!, 1e-4)
        assertEquals(PaceTargetStatus.OFF_PACE, todayV2.status)
    }

    @Test
    fun `issue 596 - scenario 11 - day 7 of cycle declining entries expected today insufficient history`() {
        val cycleStartDate = LocalDate.of(2026, 10, 1)
        val t = LocalDate.of(2026, 10, 8)
        val entries = (0..7).map { offset ->
            WeightEntry(
                date = cycleStartDate.plusDays(offset.toLong()),
                weightKg = 80.0 - (offset * 0.2),
                updatedAtMillis = offset.toLong()
            )
        }

        val state = FatLossAnalytics.compute7DayPaceTargets(entries, cycleStartDate, t, "kg")
        assertTrue(state is PaceTargetsState.Available)
        val avail = state as PaceTargetsState.Available

        assertEquals(ColumnState.InsufficientHistory, avail.today)

        assertTrue(avail.tomorrow is ColumnState.Value)
        val tomorrowVal = avail.tomorrow as ColumnState.Value
        assertEquals(78.4, tomorrowVal.targetWeightDisplay, 1e-4)
        assertEquals(-0.2, tomorrowVal.deltaDisplay!!, 1e-4)
    }

    // =========================================================================
    // Issue #598: Display Last Week Baseline Weights in 7-Day Pace Targets
    // =========================================================================

    @Test
    fun `issue 598 - scenario a - normal active day with both entries logged`() {
        val cycleStartDate = LocalDate.of(2026, 9, 28)
        val t = LocalDate.of(2026, 10, 7)
        // W_(t-7d) = 77.3 kg on 2026-09-30
        // W_(t-8d) = 75.9 kg on 2026-09-29, W_(t-1d) = 75.0 kg on 2026-10-06 -> P_7d(t-1d) = -0.9 kg/wk
        // W_t = 76.5 kg on 2026-10-07
        // W_(t-6d) = 75.3 kg on 2026-10-01
        val entries = listOf(
            WeightEntry(date = LocalDate.of(2026, 9, 29), weightKg = 75.9, updatedAtMillis = 1L),
            WeightEntry(date = LocalDate.of(2026, 9, 30), weightKg = 77.3, updatedAtMillis = 2L),
            WeightEntry(date = LocalDate.of(2026, 10, 1), weightKg = 75.3, updatedAtMillis = 3L),
            WeightEntry(date = LocalDate.of(2026, 10, 6), weightKg = 75.0, updatedAtMillis = 4L),
            WeightEntry(date = LocalDate.of(2026, 10, 7), weightKg = 76.5, updatedAtMillis = 5L)
        )

        val state = FatLossAnalytics.compute7DayPaceTargets(entries, cycleStartDate, t, "kg")
        assertTrue("State must be Available", state is PaceTargetsState.Available)
        val avail = state as PaceTargetsState.Available

        assertTrue("today must be Value", avail.today is ColumnState.Value)
        val todayVal = avail.today as ColumnState.Value
        assertEquals(76.4, todayVal.targetWeightDisplay, 1e-4)
        assertEquals(0.1, todayVal.deltaDisplay!!, 1e-4)
        assertEquals(PaceTargetStatus.OFF_PACE, todayVal.status)
        assertEquals("kg", todayVal.unitLabel)
        assertEquals(77.3, todayVal.baselineWeightDisplay!!, 1e-4)

        assertTrue("tomorrow must be Value", avail.tomorrow is ColumnState.Value)
        val tomorrowVal = avail.tomorrow as ColumnState.Value
        assertEquals(74.5, tomorrowVal.targetWeightDisplay, 1e-4)
        assertEquals(-2.0, tomorrowVal.deltaDisplay!!, 1e-4)
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

        val state = FatLossAnalytics.compute7DayPaceTargets(entries, cycleStartDate, t, "kg")
        assertTrue(state is PaceTargetsState.Available)
        val avail = state as PaceTargetsState.Available

        assertTrue(avail.today is ColumnState.Value)
        val todayVal = avail.today as ColumnState.Value
        assertEquals(76.4, todayVal.targetWeightDisplay, 1e-4)
        assertNull(todayVal.deltaDisplay)
        assertNull(todayVal.status)
        assertEquals("kg", todayVal.unitLabel)
        assertEquals(77.3, todayVal.baselineWeightDisplay!!, 1e-4)
    }

    @Test
    fun `issue 598 - scenario c and c2 - tomorrow on anchor path with and without today logged`() {
        val cycleStartDate = LocalDate.of(2026, 9, 28)
        val t = LocalDate.of(2026, 10, 7)
        // With today logged (Scenario c):
        // P_7d(t) from (76.5 - 77.3) = -0.8 kg/wk
        // Target tomorrow = 75.3 + (-0.8) = 74.5 kg. Delta = 74.5 - 76.5 = -2.0 kg.
        // Baseline = 75.3 kg.
        val entriesWithToday = listOf(
            WeightEntry(date = LocalDate.of(2026, 9, 29), weightKg = 75.9, updatedAtMillis = 1L),
            WeightEntry(date = LocalDate.of(2026, 9, 30), weightKg = 77.3, updatedAtMillis = 2L),
            WeightEntry(date = LocalDate.of(2026, 10, 1), weightKg = 75.3, updatedAtMillis = 3L),
            WeightEntry(date = LocalDate.of(2026, 10, 6), weightKg = 75.1, updatedAtMillis = 4L), // 75.1 - 75.9 = -0.8 kg/wk
            WeightEntry(date = LocalDate.of(2026, 10, 7), weightKg = 76.5, updatedAtMillis = 5L)
        )
        val stateC = FatLossAnalytics.compute7DayPaceTargets(entriesWithToday, cycleStartDate, t, "kg") as PaceTargetsState.Available
        val tomorrowC = stateC.tomorrow as ColumnState.Value
        assertEquals(74.5, tomorrowC.targetWeightDisplay, 1e-4)
        assertEquals(-2.0, tomorrowC.deltaDisplay!!, 1e-4)
        assertEquals("kg", tomorrowC.unitLabel)
        assertEquals(75.3, tomorrowC.baselineWeightDisplay!!, 1e-4)

        // Without today logged (Scenario c2):
        // P_7d(t) evaluated on 2026-10-06 is (75.1 - 75.9) = -0.8 kg/wk
        // Target tomorrow = 75.3 + (-0.8) = 74.5 kg. Delta = null (today unlogged).
        // Baseline = 75.3 kg.
        val entriesWithoutToday = entriesWithToday.filterNot { it.date == t }
        val stateC2 = FatLossAnalytics.compute7DayPaceTargets(entriesWithoutToday, cycleStartDate, t, "kg") as PaceTargetsState.Available
        val tomorrowC2 = stateC2.tomorrow as ColumnState.Value
        assertEquals(74.5, tomorrowC2.targetWeightDisplay, 1e-4)
        assertNull(tomorrowC2.deltaDisplay)
        assertNull(tomorrowC2.status)
        assertEquals("kg", tomorrowC2.unitLabel)
        assertEquals(75.3, tomorrowC2.baselineWeightDisplay!!, 1e-4)
    }

    @Test
    fun `issue 598 - scenario d - tomorrow AlreadyBelowTarget on anchor path retains baseline`() {
        val cycleStartDate = LocalDate.of(2026, 9, 28)
        val t = LocalDate.of(2026, 10, 7)
        // W_t = 74.0 kg (< 74.5 kg target tomorrow)
        val entries = listOf(
            WeightEntry(date = LocalDate.of(2026, 9, 29), weightKg = 75.9, updatedAtMillis = 1L),
            WeightEntry(date = LocalDate.of(2026, 9, 30), weightKg = 74.8, updatedAtMillis = 2L),
            WeightEntry(date = LocalDate.of(2026, 10, 1), weightKg = 75.3, updatedAtMillis = 3L),
            WeightEntry(date = LocalDate.of(2026, 10, 6), weightKg = 75.0, updatedAtMillis = 4L),
            WeightEntry(date = LocalDate.of(2026, 10, 7), weightKg = 74.0, updatedAtMillis = 5L)
        )
        // P_7d(t) = (74.0 - 74.8) / 7 * 7 = -0.8 kg/wk
        // Target tomorrow = 75.3 + (-0.8) = 74.5 kg
        // Actual 74.0 < 74.5 -> AlreadyBelowTarget margin = 0.5 kg

        val state = FatLossAnalytics.compute7DayPaceTargets(entries, cycleStartDate, t, "kg")
        assertTrue(state is PaceTargetsState.Available)
        val avail = state as PaceTargetsState.Available

        assertTrue("tomorrow must be AlreadyBelowTarget", avail.tomorrow is ColumnState.AlreadyBelowTarget)
        val tomorrowBelow = avail.tomorrow as ColumnState.AlreadyBelowTarget
        assertEquals(74.5, tomorrowBelow.targetWeightDisplay, 1e-4)
        assertEquals(0.5, tomorrowBelow.marginDisplay, 1e-4)
        assertEquals("kg", tomorrowBelow.unitLabel)
        assertEquals(75.3, tomorrowBelow.baselineWeightDisplay!!, 1e-4)
    }

    @Test
    fun `issue 598 - scenario e and e2 - tomorrow fallback calculation path when t minus 6d unlogged has null baseline`() {
        val cycleStartDate = LocalDate.of(2026, 9, 28)
        val t = LocalDate.of(2026, 10, 7)
        // t-6d (2026-10-01) is unlogged!
        // P_7d(t) = (76.5 - 77.3) / 7 * 7 = -0.8 kg/wk
        // Fallback target tomorrow = 76.5 + (-0.8 / 7.0) = 76.3857... -> 76.4 kg. Delta = -0.1 kg.
        val entries = listOf(
            WeightEntry(date = LocalDate.of(2026, 9, 29), weightKg = 75.9, updatedAtMillis = 1L),
            WeightEntry(date = LocalDate.of(2026, 9, 30), weightKg = 77.3, updatedAtMillis = 2L),
            // 2026-10-01 omitted
            WeightEntry(date = LocalDate.of(2026, 10, 6), weightKg = 75.0, updatedAtMillis = 4L),
            WeightEntry(date = LocalDate.of(2026, 10, 7), weightKg = 76.5, updatedAtMillis = 5L)
        )

        val stateE = FatLossAnalytics.compute7DayPaceTargets(entries, cycleStartDate, t, "kg") as PaceTargetsState.Available
        val tomorrowE = stateE.tomorrow as ColumnState.Value
        assertEquals(76.4, tomorrowE.targetWeightDisplay, 1e-4)
        assertEquals(-0.1, tomorrowE.deltaDisplay!!, 1e-4)
        assertNull("Fallback path must emit null baseline", tomorrowE.baselineWeightDisplay)

        // Scenario e2: fallback with zero rounded delta: P_7d(t) = -0.3 kg/wk
        // 76.5 - 76.8 = -0.3 kg/wk
        // Fallback = 76.5 + (-0.3 / 7.0) = 76.457 -> 76.5 kg. Actual 76.5 == Target 76.5 -> deltaDisplay = null.
        val entriesE2 = listOf(
            WeightEntry(date = LocalDate.of(2026, 9, 29), weightKg = 75.9, updatedAtMillis = 1L),
            WeightEntry(date = LocalDate.of(2026, 9, 30), weightKg = 76.8, updatedAtMillis = 2L),
            // 2026-10-01 omitted
            WeightEntry(date = LocalDate.of(2026, 10, 6), weightKg = 75.0, updatedAtMillis = 4L),
            WeightEntry(date = LocalDate.of(2026, 10, 7), weightKg = 76.5, updatedAtMillis = 5L)
        )
        val stateE2 = FatLossAnalytics.compute7DayPaceTargets(entriesE2, cycleStartDate, t, "kg") as PaceTargetsState.Available
        val tomorrowE2 = stateE2.tomorrow as ColumnState.Value
        assertEquals(76.5, tomorrowE2.targetWeightDisplay, 1e-4)
        assertNull("Equal rounded target has null deltaDisplay", tomorrowE2.deltaDisplay)
        assertNull("Fallback path must emit null baseline", tomorrowE2.baselineWeightDisplay)
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

        val state = FatLossAnalytics.compute7DayPaceTargets(entries, cycleStartDate, t, "lbs") as PaceTargetsState.Available
        val todayVal = state.today as ColumnState.Value
        val tomorrowVal = state.tomorrow as ColumnState.Value

        // 77.3 kg * 2.20462262185 = 170.417... -> 170.4 lbs
        assertEquals(170.4, todayVal.baselineWeightDisplay!!, 1e-4)
        assertEquals("lbs", todayVal.unitLabel)

        // 75.3 kg * 2.20462262185 = 166.008... -> 166.0 lbs
        assertEquals(166.0, tomorrowVal.baselineWeightDisplay!!, 1e-4)
        assertEquals("lbs", tomorrowVal.unitLabel)
    }

    @Test
    fun `issue 598 - scenario g - non-value states carry no baseline`() {
        val cycleStartDate = LocalDate.of(2026, 10, 1)
        val t = LocalDate.of(2026, 10, 4)
        val entriesEarly = (1..4).map { day ->
            WeightEntry(date = LocalDate.of(2026, 10, day), weightKg = 80.0 - day * 0.2, updatedAtMillis = day.toLong())
        }
        val unavail = FatLossAnalytics.compute7DayPaceTargets(entriesEarly, cycleStartDate, t, "kg")
        assertEquals(PaceTargetsState.Unavailable, unavail)

        val entriesStalled = listOf(
            WeightEntry(date = LocalDate.of(2026, 9, 30), weightKg = 77.0, updatedAtMillis = 1L),
            WeightEntry(date = LocalDate.of(2026, 10, 7), weightKg = 77.2, updatedAtMillis = 2L)
        )
        val stalled = FatLossAnalytics.compute7DayPaceTargets(entriesStalled, LocalDate.of(2026, 9, 28), LocalDate.of(2026, 10, 7), "kg")
        assertEquals(PaceTargetsState.Stalled, stalled)

        val reqDate = LocalDate.of(2026, 9, 30)
        val needsLogState = ColumnState.NeedsLog(requiredDate = reqDate)
        assertEquals(reqDate, needsLogState.requiredDate)
        assertEquals(ColumnState.InsufficientHistory, ColumnState.InsufficientHistory)
    }
}

