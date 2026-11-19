package com.fractanomics.crosstraining.util

import com.fractanomics.crosstraining.data.model.BlockKind
import com.fractanomics.crosstraining.data.model.BlockSet
import com.fractanomics.crosstraining.data.model.BlockWithSets
import com.fractanomics.crosstraining.data.model.Exercise
import com.fractanomics.crosstraining.data.model.ExerciseCategory
import com.fractanomics.crosstraining.data.model.MetricType
import com.fractanomics.crosstraining.data.model.RepMax
import com.fractanomics.crosstraining.data.model.Routine
import com.fractanomics.crosstraining.data.model.RoutineBlock
import com.fractanomics.crosstraining.data.model.RoutineWithBlocks
import com.fractanomics.crosstraining.data.model.Session
import com.fractanomics.crosstraining.data.model.SessionBlock
import com.fractanomics.crosstraining.data.model.SessionWithBlocks
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate

class DailyRoutineCodecTest {

    private val snatch = Exercise(1L, "Snatch", ExerciseCategory.BARBELL, MetricType.WEIGHT, "kg", true)
    private val backSquat = Exercise(2L, "Back Squat", ExerciseCategory.BARBELL, MetricType.WEIGHT, "kg", true)
    private val pullUps = Exercise(3L, "Pull-ups", ExerciseCategory.GYMNASTICS, MetricType.REPS, "reps", false)

    private val snatchComplexBlock = AvailableBlock(
        id = 10L,
        title = "Snatch Balance Complex",
        instructions = "E4MOM: Snatch Balance + Snatch + Hang Snatch",
        exercises = listOf(AvailableBlockExercise(snatch.id, snatch.name, true)),
        trackedPrs = listOf(TrackedPrSlot(snatch.id, snatch.name, 2))
    )

    @Test
    fun `toRoutineWithBlocks and fromRoutineWithBlocks round-trip mixed Blocks and Exercises with notes and PRs`() {
        val routine = DailyRoutine(
            id = 55L,
            title = "Monday Full Session",
            notes = "Heavy snatch complex + squat strength & pull",
            items = listOf(
                DailyRoutineItem.BlockRef(
                    blockId = snatchComplexBlock.id,
                    blockTitle = snatchComplexBlock.title,
                    resolvedBlock = snatchComplexBlock
                ),
                DailyRoutineItem.StandaloneExercise(
                    exerciseId = backSquat.id,
                    exerciseName = backSquat.name,
                    note = "4x8 @ RPE 8",
                    defaultTrackedPrs = listOf(TrackedPrSlot(backSquat.id, backSquat.name, 5)),
                    resolvedExercise = backSquat
                ),
                DailyRoutineItem.StandaloneExercise(
                    exerciseId = pullUps.id,
                    exerciseName = pullUps.name,
                    note = "4xMax weighted",
                    defaultTrackedPrs = emptyList(),
                    resolvedExercise = pullUps
                )
            )
        )

        val rwb = DailyRoutineCodec.toRoutineWithBlocks(routine)
        assertEquals(DAILY_ROUTINE_FORMAT, rwb.routine.defaultFormat)
        assertTrue(DailyRoutineCodec.isDailyRoutine(rwb))
        assertEquals(3, rwb.blocks.size)
        assertEquals(DAILY_ROUTINE_BLOCK_ITEM_FORMAT, rwb.blocks[0].format)
        assertEquals(DAILY_ROUTINE_EXERCISE_ITEM_FORMAT, rwb.blocks[1].format)
        assertNull(RoutineTargetCodec.decodeHeaderSlots(rwb.blocks[1].notes, 1))
        assertEquals("4x8 @ RPE 8", RoutineTargetCodec.extractCleanNotes(rwb.blocks[1].notes))

        val decoded = DailyRoutineCodec.fromRoutineWithBlocks(
            rwb = rwb,
            availableBlocks = listOf(snatchComplexBlock),
            exercises = listOf(snatch, backSquat, pullUps)
        )
        assertEquals(55L, decoded.id)
        assertEquals("Monday Full Session", decoded.title)
        assertEquals("Heavy snatch complex + squat strength & pull", decoded.notes)
        assertEquals(0, decoded.missingBlockCount)
        assertEquals(1, decoded.blockCount)
        assertEquals(2, decoded.exerciseCount)
        assertEquals(2, decoded.totalTrackedPrCount)

        val item0 = decoded.items[0] as DailyRoutineItem.BlockRef
        assertEquals(10L, item0.blockId)
        assertEquals("Snatch Balance Complex", item0.blockTitle)

        val item1 = decoded.items[1] as DailyRoutineItem.StandaloneExercise
        assertEquals(backSquat.id, item1.exerciseId)
        assertEquals("Back Squat", item1.exerciseName)
        assertEquals("4x8 @ RPE 8", item1.note)
        assertEquals(listOf(5), item1.defaultTrackedPrs.map { it.rmReps })
    }

    @Test
    fun `deleted AvailableBlock is omitted from active DailyRoutine items and increments missingBlockCount`() {
        val rwb = DailyRoutineCodec.toRoutineWithBlocks(
            DailyRoutine(
                id = 70L,
                title = "Missing Block Check",
                notes = "",
                items = listOf(
                    DailyRoutineItem.BlockRef(999L, "Deleted Block", null),
                    DailyRoutineItem.StandaloneExercise(backSquat.id, backSquat.name, "3x5", emptyList(), backSquat)
                )
            )
        )

        val decoded = DailyRoutineCodec.fromRoutineWithBlocks(
            rwb = rwb,
            availableBlocks = listOf(snatchComplexBlock),
            exercises = listOf(backSquat)
        )
        assertEquals(1, decoded.missingBlockCount)
        assertEquals(1, decoded.items.size)
        assertTrue(decoded.items[0] is DailyRoutineItem.StandaloneExercise)
    }

    @Test
    fun `routineNaturalKey namespaces DailyRoutine separately from AvailableBlock even with identical title`() {
        val drKey = DailyRoutineCodec.routineNaturalKey("Monday Clean", DAILY_ROUTINE_FORMAT)
        val abKey = DailyRoutineCodec.routineNaturalKey("Monday Clean", AVAILABLE_BLOCK_FORMAT)
        assertNotEquals(drKey, abKey)
        assertEquals("dr:monday clean", drKey)
        assertEquals("ab:monday clean", abKey)
    }

    @Test
    fun `resolveLastExerciseSession unifies history across routine and standalone logs and excludes edited session`() {
        val earlier = SessionWithBlocks(
            session = Session(id = 1L, cycleId = 1L, date = LocalDate.of(2026, 11, 10), title = "Ad-hoc Squat"),
            blocks = listOf(
                BlockWithSets(
                    block = SessionBlock(
                        id = 10L, sessionId = 1L, position = 0, name = "Back Squat",
                        kind = BlockKind.STRENGTH, format = STANDALONE_EXERCISE_SESSION_FORMAT,
                        mainExerciseId = backSquat.id
                    ),
                    sets = listOf(
                        BlockSet(blockId = 10L, position = 0, reps = 1, weight = 90.0),
                        BlockSet(blockId = 10L, position = 1, reps = 1, weight = 95.0)
                    )
                )
            )
        )
        val newerEdited = SessionWithBlocks(
            session = Session(id = 2L, cycleId = 1L, date = LocalDate.of(2026, 11, 14), title = "Monday Full Session"),
            blocks = listOf(
                BlockWithSets(
                    block = SessionBlock(
                        id = 20L, sessionId = 2L, position = 0, name = "Back Squat",
                        kind = BlockKind.STRENGTH, format = STANDALONE_EXERCISE_SESSION_FORMAT,
                        mainExerciseId = backSquat.id
                    ),
                    sets = listOf(
                        BlockSet(blockId = 20L, position = 0, reps = 1, weight = 100.0)
                    )
                )
            )
        )

        val latest = DailyRoutineCodec.resolveLastExerciseSession(
            exercise = backSquat,
            sessions = listOf(earlier, newerEdited),
            weightUnit = "kg"
        )!!
        assertEquals("2026-11-14", latest.dateIso)
        assertEquals(listOf(100.0), latest.weights)

        val excluded = DailyRoutineCodec.resolveLastExerciseSession(
            exercise = backSquat,
            sessions = listOf(earlier, newerEdited),
            weightUnit = "kg",
            excludeSessionId = 2L
        )!!
        assertEquals("2026-11-10", excluded.dateIso)
        assertEquals(listOf(90.0, 95.0), excluded.weights)
    }

    @Test
    fun `resolveDefaultTrackedPrSlotsForExercise combines defaults history and tracksRepMax fallback`() {
        val historical = listOf(
            RepMax(id = 1L, exerciseId = backSquat.id, reps = 5, weight = 100.0, date = LocalDate.of(2026, 11, 1))
        )
        val withHistory = DailyRoutineCodec.resolveDefaultTrackedPrSlotsForExercise(
            exercise = backSquat,
            defaultTrackedPrs = emptyList(),
            repMaxes = historical
        )
        assertEquals(listOf(5), withHistory.map { it.rmReps })

        val freshTrackable = DailyRoutineCodec.resolveDefaultTrackedPrSlotsForExercise(
            exercise = snatch,
            defaultTrackedPrs = emptyList(),
            repMaxes = emptyList()
        )
        assertEquals(listOf(1), freshTrackable.map { it.rmReps })

        val nonPrExercise = DailyRoutineCodec.resolveDefaultTrackedPrSlotsForExercise(
            exercise = pullUps,
            defaultTrackedPrs = emptyList(),
            repMaxes = emptyList()
        )
        assertTrue(nonPrExercise.isEmpty())
    }

    @Test
    fun `computeDailyRoutineProgress aggregates unified Block and Exercise metrics with trends and PR badges`() {
        val routine = DailyRoutine(
            id = 55L,
            title = "Monday Full Session",
            notes = "",
            items = listOf(
                DailyRoutineItem.BlockRef(snatchComplexBlock.id, snatchComplexBlock.title, snatchComplexBlock),
                DailyRoutineItem.StandaloneExercise(backSquat.id, backSquat.name, "4x8", emptyList(), backSquat),
                DailyRoutineItem.StandaloneExercise(pullUps.id, pullUps.name, "4xMax", emptyList(), pullUps)
            )
        )
        val s1 = SessionWithBlocks(
            session = Session(id = 1L, cycleId = 1L, date = LocalDate.of(2026, 11, 10), title = "Standalone"),
            blocks = listOf(
                BlockWithSets(
                    block = SessionBlock(
                        id = 11L, sessionId = 1L, position = 0, name = "Snatch",
                        mainExerciseId = snatch.id, routineId = snatchComplexBlock.id, subBlock = snatchComplexBlock.title
                    ),
                    sets = listOf(BlockSet(blockId = 11L, position = 0, reps = 1, weight = 30.0))
                ),
                BlockWithSets(
                    block = SessionBlock(
                        id = 12L, sessionId = 1L, position = 1, name = "Back Squat",
                        format = STANDALONE_EXERCISE_SESSION_FORMAT, mainExerciseId = backSquat.id
                    ),
                    sets = listOf(BlockSet(blockId = 12L, position = 0, reps = 1, weight = 95.0))
                )
            )
        )
        val s2 = SessionWithBlocks(
            session = Session(id = 2L, cycleId = 1L, date = LocalDate.of(2026, 11, 14), title = "Monday Full Session"),
            blocks = listOf(
                BlockWithSets(
                    block = SessionBlock(
                        id = 21L, sessionId = 2L, position = 0, name = "Snatch",
                        mainExerciseId = snatch.id, routineId = snatchComplexBlock.id, subBlock = snatchComplexBlock.title
                    ),
                    sets = listOf(
                        BlockSet(blockId = 21L, position = 0, reps = 1, weight = 30.0),
                        BlockSet(blockId = 21L, position = 1, reps = 1, weight = 32.5)
                    )
                ),
                BlockWithSets(
                    block = SessionBlock(
                        id = 22L, sessionId = 2L, position = 1, name = "Back Squat",
                        format = STANDALONE_EXERCISE_SESSION_FORMAT, mainExerciseId = backSquat.id
                    ),
                    sets = listOf(
                        BlockSet(blockId = 22L, position = 0, reps = 1, weight = 90.0),
                        BlockSet(blockId = 22L, position = 1, reps = 1, weight = 100.0)
                    )
                )
            )
        )
        val rms = listOf(
            RepMax(id = 1L, exerciseId = snatch.id, reps = 2, weight = 35.0, date = LocalDate.of(2026, 11, 14)),
            RepMax(id = 2L, exerciseId = backSquat.id, reps = 5, weight = 100.0, date = LocalDate.of(2026, 11, 14))
        )

        val progress = DailyRoutineCodec.computeDailyRoutineProgress(
            routine = routine,
            sessions = listOf(s1, s2),
            repMaxes = rms,
            exercises = listOf(snatch, backSquat, pullUps),
            weightUnit = "kg"
        )
        assertEquals(3, progress.size)

        val snatchItem = progress[0]
        assertEquals(DailyRoutineItemType.BLOCK, snatchItem.itemType)
        assertEquals(32.5, snatchItem.lastTop!!, 0.001)
        assertEquals(2.5, snatchItem.topDelta!!, 0.001)
        assertEquals(ConstituentTrendDirection.UP, snatchItem.trendDirection)
        assertTrue(snatchItem.bestPrBadges.any { it.contains("2RM Snatch: 35 kg") })

        val squatItem = progress[1]
        assertEquals(DailyRoutineItemType.EXERCISE, squatItem.itemType)
        assertEquals(100.0, squatItem.allTimeTop!!, 0.001)
        assertEquals(5.0, squatItem.topDelta!!, 0.001)
        assertEquals(ConstituentTrendDirection.UP, squatItem.trendDirection)
        assertTrue(squatItem.bestPrBadges.any { it.contains("5RM Back Squat: 100 kg") })

        val unloggedPullups = progress[2]
        assertNull(unloggedPullups.allTimeTop)
        assertEquals(ConstituentTrendDirection.NONE, unloggedPullups.trendDirection)
        assertTrue(unloggedPullups.performances.isEmpty())
    }

    @Test
    fun `toRoutineWithBlocks and fromRoutineWithBlocks round-trip optional multi-metric targets on Exercise and Block items while keeping unset clean`() {
        val airBike = Exercise(4L, "Air Bike", ExerciseCategory.MACHINE, MetricType.CALORIES, "cal", false)
        val routine = DailyRoutine(
            id = 88L,
            title = "Engine + Snatch",
            notes = "Target routine",
            items = listOf(
                DailyRoutineItem.BlockRef(
                    blockId = snatchComplexBlock.id,
                    blockTitle = snatchComplexBlock.title,
                    resolvedBlock = snatchComplexBlock,
                    targetSetsCount = 5,
                    exerciseTargets = mapOf(
                        snatch.id to RoutineExerciseTargetSpec(
                            metric = ExerciseTargetMetric.REPS,
                            mode = TargetMode.FIXED,
                            fixedValue = 3,
                            perSetValues = listOf(3, 3, 3, 3, 3)
                        )
                    ),
                    exerciseTargetsByName = mapOf(
                        "snatch" to RoutineExerciseTargetSpec(
                            metric = ExerciseTargetMetric.REPS,
                            mode = TargetMode.FIXED,
                            fixedValue = 3,
                            perSetValues = listOf(3, 3, 3, 3, 3)
                        )
                    )
                ),
                DailyRoutineItem.StandaloneExercise(
                    exerciseId = airBike.id,
                    exerciseName = airBike.name,
                    note = "Hold steady wattage",
                    defaultTrackedPrs = emptyList(),
                    resolvedExercise = airBike,
                    targetSetsCount = 4,
                    targetSpec = RoutineExerciseTargetSpec(
                        metric = ExerciseTargetMetric.CALORIES,
                        mode = TargetMode.PER_SET,
                        fixedValue = 15,
                        perSetValues = listOf(15, 15, 12, 0)
                    )
                ),
                DailyRoutineItem.StandaloneExercise(
                    exerciseId = pullUps.id,
                    exerciseName = pullUps.name,
                    note = "Unset targets",
                    defaultTrackedPrs = emptyList(),
                    resolvedExercise = pullUps
                )
            )
        )

        val rwb = DailyRoutineCodec.toRoutineWithBlocks(routine)
        assertEquals(5, rwb.blocks[0].setsCount)
        assertEquals("3", rwb.blocks[0].targetRepsScheme)
        assertTrue(RoutineTargetCodec.hasStructuredHeader(rwb.blocks[0].notes))

        assertEquals(4, rwb.blocks[1].setsCount)
        assertEquals("15-15-12-0 cal", rwb.blocks[1].targetRepsScheme)
        assertTrue(RoutineTargetCodec.hasStructuredHeader(rwb.blocks[1].notes))
        assertEquals("Hold steady wattage", RoutineTargetCodec.extractCleanNotes(rwb.blocks[1].notes))

        // Unset item omits [rb_v2=...] completely
        assertEquals(1, rwb.blocks[2].setsCount)
        assertEquals("", rwb.blocks[2].targetRepsScheme)
        assertFalse(RoutineTargetCodec.hasStructuredHeader(rwb.blocks[2].notes))

        val decoded = DailyRoutineCodec.fromRoutineWithBlocks(
            rwb = rwb,
            availableBlocks = listOf(snatchComplexBlock),
            exercises = listOf(snatch, backSquat, pullUps, airBike)
        )
        val decBlock = decoded.items[0] as DailyRoutineItem.BlockRef
        assertTrue(decBlock.hasConfiguredTargets)
        assertEquals(5, decBlock.targetSetsCount)
        assertEquals(3, decBlock.exerciseTargets[snatch.id]!!.fixedValue)

        val decBike = decoded.items[1] as DailyRoutineItem.StandaloneExercise
        assertTrue(decBike.hasConfiguredTarget)
        assertEquals(4, decBike.targetSetsCount)
        assertEquals(ExerciseTargetMetric.CALORIES, decBike.targetSpec!!.metric)
        assertEquals(listOf(15, 15, 12, 0), decBike.targetSpec!!.perSetValues)
        assertEquals("Hold steady wattage", decBike.note)

        val decPullUps = decoded.items[2] as DailyRoutineItem.StandaloneExercise
        assertFalse(decPullUps.hasConfiguredTarget)
        assertNull(decPullUps.targetSetsCount)
        assertNull(decPullUps.targetSpec)
    }

    @Test
    fun `resolveLastExerciseSession and computeDailyRoutineProgress handle bodyweight reps and conditioning metrics`() {
        val airBike = Exercise(4L, "Air Bike", ExerciseCategory.MACHINE, MetricType.CALORIES, "cal", false)
        val routine = DailyRoutine(
            id = 99L,
            title = "Mixed Engine",
            notes = "",
            items = listOf(
                DailyRoutineItem.StandaloneExercise(pullUps.id, pullUps.name, resolvedExercise = pullUps),
                DailyRoutineItem.StandaloneExercise(airBike.id, airBike.name, resolvedExercise = airBike)
            )
        )

        val d1 = LocalDate.of(2026, 11, 10)
        val d2 = LocalDate.of(2026, 11, 14)
        val s1 = SessionWithBlocks(
            session = Session(id = 1L, cycleId = 1L, date = d1, title = "Day 1"),
            blocks = listOf(
                BlockWithSets(
                    block = SessionBlock(
                        id = 10L, sessionId = 1L, position = 0, name = "Pull-ups",
                        format = STANDALONE_EXERCISE_SESSION_FORMAT, mainExerciseId = pullUps.id,
                        notes = "[log_metric=REPS]"
                    ),
                    sets = listOf(
                        BlockSet(blockId = 10L, position = 0, reps = 10, weight = null),
                        BlockSet(blockId = 10L, position = 1, reps = 12, weight = null)
                    )
                ),
                BlockWithSets(
                    block = SessionBlock(
                        id = 11L, sessionId = 1L, position = 1, name = "Air Bike",
                        format = STANDALONE_EXERCISE_SESSION_FORMAT, mainExerciseId = airBike.id,
                        notes = "[log_metric=CALORIES]"
                    ),
                    sets = listOf(
                        BlockSet(blockId = 11L, position = 0, reps = 1, metricValue = 15.0)
                    )
                )
            )
        )
        val s2 = SessionWithBlocks(
            session = Session(id = 2L, cycleId = 1L, date = d2, title = "Day 2"),
            blocks = listOf(
                BlockWithSets(
                    block = SessionBlock(
                        id = 20L, sessionId = 2L, position = 0, name = "Pull-ups",
                        format = STANDALONE_EXERCISE_SESSION_FORMAT, mainExerciseId = pullUps.id,
                        notes = "[log_metric=REPS]"
                    ),
                    sets = listOf(
                        BlockSet(blockId = 20L, position = 0, reps = 14, weight = null),
                        BlockSet(blockId = 20L, position = 1, reps = 15, weight = null)
                    )
                ),
                BlockWithSets(
                    block = SessionBlock(
                        id = 21L, sessionId = 2L, position = 1, name = "Air Bike",
                        format = STANDALONE_EXERCISE_SESSION_FORMAT, mainExerciseId = airBike.id,
                        notes = "[log_metric=CALORIES]"
                    ),
                    sets = listOf(
                        BlockSet(blockId = 21L, position = 0, reps = 1, metricValue = 20.0),
                        BlockSet(blockId = 21L, position = 1, reps = 1, metricValue = 18.0)
                    )
                )
            )
        )

        val lastPullUps = DailyRoutineCodec.resolveLastExerciseSession(pullUps, listOf(s1, s2), "kg")!!
        assertEquals(ExerciseTargetMetric.REPS, lastPullUps.loggedMetric)
        assertEquals(listOf(14, 15), lastPullUps.reps)
        assertEquals(15.0, lastPullUps.topWeight!!, 0.001)
        assertEquals("14 · 15 reps", lastPullUps.setWeightsSummary)

        val lastBike = DailyRoutineCodec.resolveLastExerciseSession(airBike, listOf(s1, s2), "kg")!!
        assertEquals(ExerciseTargetMetric.CALORIES, lastBike.loggedMetric)
        assertEquals(listOf(20.0, 18.0), lastBike.metricValues)
        assertEquals(20.0, lastBike.topWeight!!, 0.001)
        assertEquals("20 · 18 cal", lastBike.setWeightsSummary)

        val summaries = DailyRoutineCodec.computeDailyRoutineProgress(
            routine = routine,
            sessions = listOf(s1, s2),
            repMaxes = emptyList(),
            exercises = listOf(pullUps, airBike),
            weightUnit = "kg"
        )
        assertEquals("reps", summaries[0].unit)
        assertEquals(15.0, summaries[0].lastTop!!, 0.001)
        assertEquals(3.0, summaries[0].topDelta!!, 0.001)
        assertEquals(ConstituentTrendDirection.UP, summaries[0].trendDirection)

        assertEquals("cal", summaries[1].unit)
        assertEquals(20.0, summaries[1].lastTop!!, 0.001)
        assertEquals(5.0, summaries[1].topDelta!!, 0.001)
        assertEquals(ConstituentTrendDirection.UP, summaries[1].trendDirection)
    }
}
