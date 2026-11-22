package com.fractanomics.crosstraining

import com.fractanomics.crosstraining.data.analytics.BodyWeightMatchKind
import com.fractanomics.crosstraining.data.analytics.RelativeStrengthAnalytics
import com.fractanomics.crosstraining.data.model.BlockKind
import com.fractanomics.crosstraining.data.model.BlockSet
import com.fractanomics.crosstraining.data.model.BlockWithSets
import com.fractanomics.crosstraining.data.model.Cycle
import com.fractanomics.crosstraining.data.model.CycleGoal
import com.fractanomics.crosstraining.data.model.Exercise
import com.fractanomics.crosstraining.data.model.ExerciseCategory
import com.fractanomics.crosstraining.data.model.MetricType
import com.fractanomics.crosstraining.data.model.RepMax
import com.fractanomics.crosstraining.data.model.Session
import com.fractanomics.crosstraining.data.model.SessionBlock
import com.fractanomics.crosstraining.data.model.SessionWithBlocks
import com.fractanomics.crosstraining.data.model.WeightEntry
import com.fractanomics.crosstraining.ui.BlockPerformance
import com.fractanomics.crosstraining.ui.byDay
import com.fractanomics.crosstraining.ui.screens.evaluateCycleGoalProgress
import com.fractanomics.crosstraining.util.DAILY_ROUTINE_FORMAT
import com.fractanomics.crosstraining.util.DailyRoutine
import com.fractanomics.crosstraining.util.DailyRoutineCodec
import com.fractanomics.crosstraining.util.DailyRoutineItem
import com.fractanomics.crosstraining.util.STANDALONE_EXERCISE_SESSION_FORMAT
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate

class RelativeStrengthAnalyticsTest {

    private val squatSnatch = Exercise(
        id = 1L,
        name = "Squat Snatch",
        category = ExerciseCategory.BARBELL,
        metricType = MetricType.WEIGHT,
        unit = "kg",
        tracksRepMax = true
    )
    private val airBike = Exercise(
        id = 2L,
        name = "Air Bike",
        category = ExerciseCategory.MACHINE,
        metricType = MetricType.CALORIES,
        unit = "cal",
        tracksRepMax = false
    )

    private fun weight(date: LocalDate, kg: Double, deletedAtMillis: Long? = null) = WeightEntry(
        date = date,
        weightKg = kg,
        updatedAtMillis = 1_000L,
        deletedAtMillis = deletedAtMillis
    )

    @Test
    fun `resolveBodyWeightForDate prioritizes exact match then 7-day prior fallback then unavailable`() {
        val index = RelativeStrengthAnalytics.buildActiveWeightIndex(
            listOf(
                weight(LocalDate.of(2026, 5, 1), 82.0),
                weight(LocalDate.of(2026, 5, 10), 80.0),
                // Soft-deleted tombstone on May 14 must be ignored
                weight(LocalDate.of(2026, 5, 14), 70.0, deletedAtMillis = 9_999L),
                // Non-positive weight must be ignored
                weight(LocalDate.of(2026, 5, 15), 0.0)
            )
        )

        // Exact match on May 10
        val exact = RelativeStrengthAnalytics.resolveBodyWeightForDate(LocalDate.of(2026, 5, 10), index)
        assertEquals(BodyWeightMatchKind.EXACT, exact.kind)
        assertEquals(80.0, exact.weightKg!!, 1e-6)
        assertEquals(0, exact.daysPrior)
        assertFalse(exact.isEstimated)

        // May 14 has tombstone -> falls back to May 10 (4 days prior)
        val est4 = RelativeStrengthAnalytics.resolveBodyWeightForDate(LocalDate.of(2026, 5, 14), index)
        assertEquals(BodyWeightMatchKind.ESTIMATED_PRIOR, est4.kind)
        assertEquals(80.0, est4.weightKg!!, 1e-6)
        assertEquals(LocalDate.of(2026, 5, 10), est4.sourceDate)
        assertEquals(4, est4.daysPrior)
        assertTrue(est4.isEstimated)

        // Exactly 7 days prior (May 17 -> May 10) is within lookback boundary
        val est7 = RelativeStrengthAnalytics.resolveBodyWeightForDate(LocalDate.of(2026, 5, 17), index)
        assertEquals(BodyWeightMatchKind.ESTIMATED_PRIOR, est7.kind)
        assertEquals(7, est7.daysPrior)

        // 8 days prior (May 18 -> May 10) -> UNAVAILABLE
        val unavail = RelativeStrengthAnalytics.resolveBodyWeightForDate(LocalDate.of(2026, 5, 18), index)
        assertEquals(BodyWeightMatchKind.UNAVAILABLE, unavail.kind)
        assertNull(unavail.weightKg)
        assertFalse(unavail.isAvailable)
    }

    @Test
    fun `calculatePercentBw applies BigDecimal HALF_UP rounding, trims whole zero, and formats cleanly`() {
        val exactBw = RelativeStrengthAnalytics.resolveBodyWeightForDate(
            LocalDate.of(2026, 5, 10),
            RelativeStrengthAnalytics.buildActiveWeightIndex(listOf(weight(LocalDate.of(2026, 5, 10), 80.0)))
        )
        // 114 / 80 * 100 = 142.5
        val pctDecimal = RelativeStrengthAnalytics.calculatePercentBw(114.0, "kg", exactBw)
        assertEquals(142.5, pctDecimal!!, 1e-6)
        assertEquals("142.5% BW", RelativeStrengthAnalytics.formatPercentBw(pctDecimal, exactBw.isEstimated))

        // 112 / 80 * 100 = 140.0 -> trimmed to "140% BW"
        val pctWhole = RelativeStrengthAnalytics.calculatePercentBw(112.0, "kg", exactBw)
        assertEquals(140.0, pctWhole!!, 1e-6)
        assertEquals("140% BW", RelativeStrengthAnalytics.formatPercentBw(pctWhole, false))

        // Estimated fallback prefix
        assertEquals("~142.5% BW", RelativeStrengthAnalytics.formatPercentBw(pctDecimal, isEstimated = true))
        // Unavailable
        assertEquals("—", RelativeStrengthAnalytics.formatPercentBw(null, isEstimated = false))
    }

    @Test
    fun `calculatePercentBw converts lbs lifted loads to kg and excludes non-weighted units`() {
        val index = RelativeStrengthAnalytics.buildActiveWeightIndex(
            listOf(weight(LocalDate.of(2026, 5, 10), 80.0))
        )
        val bw = RelativeStrengthAnalytics.resolveBodyWeightForDate(LocalDate.of(2026, 5, 10), index)

        // 220.462262185 lbs == 100 kg; 100 / 80 * 100 = 125% BW
        val lbsPct = RelativeStrengthAnalytics.calculatePercentBw(220.462262185, "lbs", bw)
        assertEquals(125.0, lbsPct!!, 1e-6)
        assertEquals("125% BW", RelativeStrengthAnalytics.formatPercentBw(lbsPct, false))

        // Non-weighted metrics yield null
        assertNull(RelativeStrengthAnalytics.calculatePercentBw(50.0, "cal", bw))
        assertNull(RelativeStrengthAnalytics.calculatePercentBw(120.0, "s", bw))
        assertNull(RelativeStrengthAnalytics.calculatePercentBw(400.0, "m", bw))
        assertNull(RelativeStrengthAnalytics.calculatePercentBw(20.0, "reps", bw))
    }

    @Test
    fun `resolvePeakRelativeStrength tracks highest percent BW independently from heaviest raw lift and ignores warmup or failed sets`() {
        val dJan = LocalDate.of(2026, 1, 10)
        val dMay = LocalDate.of(2026, 5, 10)
        val index = RelativeStrengthAnalytics.buildActiveWeightIndex(
            listOf(
                weight(dJan, 85.0),
                weight(dMay, 75.0)
            )
        )

        val blocks = listOf(
            BlockPerformance(
                date = dJan,
                sessionId = 1L,
                sessionTitle = "Heavy Day",
                block = SessionBlock(id = 10L, sessionId = 1L, position = 0, mainExerciseId = 1L),
                sets = listOf(
                    BlockSet(id = 1L, blockId = 10L, position = 0, reps = 1, weight = 100.0),
                    // Failed 110 kg attempt must NOT count toward Peak % BW
                    BlockSet(id = 2L, blockId = 10L, position = 1, reps = 1, weight = 110.0, isFailed = true)
                )
            ),
            BlockPerformance(
                date = dMay,
                sessionId = 2L,
                sessionTitle = "Cut Peak Day",
                block = SessionBlock(id = 11L, sessionId = 2L, position = 0, mainExerciseId = 1L),
                sets = listOf(
                    BlockSet(id = 3L, blockId = 11L, position = 0, reps = 1, weight = 95.0),
                    BlockSet(id = 4L, blockId = 11L, position = 1, reps = 1, weight = 105.0, isWarmup = true)
                )
            )
        )

        // Heaviest raw is Jan 10 (100 kg @ 85 kg = 117.6% BW), Peak % BW is May 10 (95 kg @ 75 kg = 126.7% BW)
        val peak = RelativeStrengthAnalytics.resolvePeakRelativeStrength(blocks, "kg", index)
        assertNotNull(peak)
        assertEquals(dMay, peak!!.date)
        assertEquals(95.0, peak.rawWeight, 1e-6)
        assertEquals(126.7, peak.percentBw, 1e-6)

        // DayRelativeMetrics verifies Volume / BW and deltas
        val days = blocks.byDay()
        val relDays = RelativeStrengthAnalytics.resolveDayRelativeMetrics(days, "kg", index)
        assertEquals(2, relDays.size)
        assertEquals(117.6, relDays[0].topPercentBw!!, 1e-6)
        assertEquals(126.7, relDays[1].topPercentBw!!, 1e-6)
        assertEquals(9.1, RelativeStrengthAnalytics.computePercentBwDelta(relDays[1].topPercentBw, relDays[0].topPercentBw)!!, 1e-6)
    }

    @Test
    fun `evaluateCycleGoalProgress tracks achievement dates and resolveCycleGoalTargetBodyWeight prefers targetWeightKg`() {
        val cycleStart = LocalDate.of(2026, 1, 10)
        val prDate = LocalDate.of(2026, 5, 10)
        val cycleWithTarget = Cycle(
            id = 1L,
            name = "Spring Cut",
            startDate = cycleStart,
            endDate = LocalDate.of(2026, 6, 1),
            isActive = true,
            targetWeightKg = 74.0
        )
        val cycleWithoutTarget = cycleWithTarget.copy(targetWeightKg = null)

        val goal = CycleGoal(
            id = 1L,
            cycleId = 1L,
            exerciseId = 1L,
            targetReps = 1,
            startWeight = 0.0,
            targetWeight = 100.0
        )
        val repMaxes = listOf(
            RepMax(id = 1L, exerciseId = 1L, reps = 1, weight = 90.0, date = cycleStart, cycleId = 1L),
            RepMax(id = 2L, exerciseId = 1L, reps = 1, weight = 95.0, date = prDate, cycleId = 1L)
        )

        val eval = evaluateCycleGoalProgress(goal, cycleWithTarget, repMaxes, emptyList())
        assertEquals(90.0, eval.baselineStartWeight, 1e-6)
        assertEquals(cycleStart, eval.baselineStartDate)
        assertEquals(95.0, eval.currentBest, 1e-6)
        assertEquals(prDate, eval.currentBestDate)

        val index = RelativeStrengthAnalytics.buildActiveWeightIndex(
            listOf(
                weight(cycleStart, 85.0),
                weight(prDate, 75.0)
            )
        )

        val goalTargetBw = RelativeStrengthAnalytics.resolveCycleGoalTargetBodyWeight(cycleWithTarget, index)
        assertEquals(BodyWeightMatchKind.TARGET_CYCLE_BW, goalTargetBw.kind)
        assertEquals(74.0, goalTargetBw.weightKg!!, 1e-6)
        assertEquals(135.1, RelativeStrengthAnalytics.calculatePercentBw(100.0, "kg", goalTargetBw)!!, 1e-6)

        val fallbackGoalBw = RelativeStrengthAnalytics.resolveCycleGoalTargetBodyWeight(cycleWithoutTarget, index)
        assertEquals(BodyWeightMatchKind.LATEST_BW, fallbackGoalBw.kind)
        assertEquals(75.0, fallbackGoalBw.weightKg!!, 1e-6)
        assertTrue(fallbackGoalBw.isEstimated)
    }

    @Test
    fun `computeDailyRoutineProgress populates percent BW for weighted exercises and omits for CALORIES`() {
        val dJan = LocalDate.of(2026, 1, 10)
        val dMay = LocalDate.of(2026, 5, 10)
        val routine = DailyRoutine(
            id = 5L,
            title = "Day A",
            notes = "",
            items = listOf(
                DailyRoutineItem.StandaloneExercise(
                    exerciseId = 1L,
                    exerciseName = "Squat Snatch",
                    resolvedExercise = squatSnatch
                ),
                DailyRoutineItem.StandaloneExercise(
                    exerciseId = 2L,
                    exerciseName = "Air Bike",
                    resolvedExercise = airBike
                )
            )
        )
        val sessions = listOf(
            SessionWithBlocks(
                session = Session(id = 1L, cycleId = 1L, date = dJan, title = "Day A"),
                blocks = listOf(
                    BlockWithSets(
                        block = SessionBlock(
                            id = 10L,
                            sessionId = 1L,
                            position = 0,
                            name = "Squat Snatch",
                            format = STANDALONE_EXERCISE_SESSION_FORMAT,
                            mainExerciseId = 1L
                        ),
                        sets = listOf(BlockSet(id = 1L, blockId = 10L, position = 0, reps = 1, weight = 100.0))
                    ),
                    BlockWithSets(
                        block = SessionBlock(
                            id = 11L,
                            sessionId = 1L,
                            position = 1,
                            name = "Air Bike",
                            format = STANDALONE_EXERCISE_SESSION_FORMAT,
                            mainExerciseId = 2L
                        ),
                        sets = listOf(BlockSet(id = 2L, blockId = 11L, position = 0, reps = 1, metricValue = 45.0))
                    )
                )
            ),
            SessionWithBlocks(
                session = Session(id = 2L, cycleId = 1L, date = dMay, title = "Day A"),
                blocks = listOf(
                    BlockWithSets(
                        block = SessionBlock(
                            id = 20L,
                            sessionId = 2L,
                            position = 0,
                            name = "Squat Snatch",
                            format = STANDALONE_EXERCISE_SESSION_FORMAT,
                            mainExerciseId = 1L
                        ),
                        sets = listOf(BlockSet(id = 3L, blockId = 20L, position = 0, reps = 1, weight = 95.0))
                    ),
                    BlockWithSets(
                        block = SessionBlock(
                            id = 21L,
                            sessionId = 2L,
                            position = 1,
                            name = "Air Bike",
                            format = STANDALONE_EXERCISE_SESSION_FORMAT,
                            mainExerciseId = 2L
                        ),
                        sets = listOf(BlockSet(id = 4L, blockId = 21L, position = 0, reps = 1, metricValue = 52.0))
                    )
                )
            )
        )
        val weights = listOf(weight(dJan, 85.0), weight(dMay, 75.0))

        val summaries = DailyRoutineCodec.computeDailyRoutineProgress(
            routine = routine,
            sessions = sessions,
            repMaxes = emptyList(),
            exercises = listOf(squatSnatch, airBike),
            weightUnit = "kg",
            weightEntries = weights
        )

        assertEquals(2, summaries.size)
        val snatchSummary = summaries[0]
        assertTrue(snatchSummary.isWeightedMetric)
        assertEquals(100.0, snatchSummary.allTimeTop!!, 1e-6)
        assertEquals(117.6, snatchSummary.allTimeTopPercentBw!!, 1e-6)
        assertEquals(126.7, snatchSummary.peakTopPercentBw!!.percentBw, 1e-6)
        assertEquals(126.7, snatchSummary.lastTopPercentBw!!, 1e-6)
        assertEquals(9.1, snatchSummary.topPercentBwDelta!!, 1e-6)

        val bikeSummary = summaries[1]
        assertFalse(bikeSummary.isWeightedMetric)
        assertNull(bikeSummary.allTimeTopPercentBw)
        assertNull(bikeSummary.peakTopPercentBw)
        assertNull(bikeSummary.lastTopPercentBw)
        assertNull(bikeSummary.topPercentBwDelta)
    }

    @Test
    fun `formatTalkBackPercentBw and formatBodyWeightAnchor produce clear descriptions`() {
        val dSource = LocalDate.of(2026, 5, 10)
        val dTarget = LocalDate.of(2026, 5, 13)
        val index = RelativeStrengthAnalytics.buildActiveWeightIndex(listOf(weight(dSource, 80.0)))
        val est = RelativeStrengthAnalytics.resolveBodyWeightForDate(dTarget, index)

        val tb = RelativeStrengthAnalytics.formatTalkBackPercentBw(125.0, est)
        assertTrue(tb.contains("125 percent body weight, estimated"))
        assertFalse(tb.contains("~"))

        val anchor = RelativeStrengthAnalytics.formatBodyWeightAnchor(est, "kg")
        assertTrue(anchor.contains("80 kg"))
        assertTrue(anchor.contains("est. 3d prior"))

        val unavail = RelativeStrengthAnalytics.resolveBodyWeightForDate(LocalDate.of(2026, 6, 1), index)
        assertEquals("no body weight logged within 7 days", RelativeStrengthAnalytics.formatTalkBackPercentBw(null, unavail))
        assertEquals("No body weight logged within 7 days", RelativeStrengthAnalytics.formatBodyWeightAnchor(unavail, "kg"))
    }
}
