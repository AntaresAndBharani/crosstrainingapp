package com.fractanomics.crosstraining.ui.screens

import com.fractanomics.crosstraining.data.model.BlockKind
import com.fractanomics.crosstraining.data.model.BlockSet
import com.fractanomics.crosstraining.data.model.BlockWithSets
import com.fractanomics.crosstraining.data.model.Exercise
import com.fractanomics.crosstraining.data.model.ExerciseCategory
import com.fractanomics.crosstraining.data.model.MetricType
import com.fractanomics.crosstraining.data.model.RepMax
import com.fractanomics.crosstraining.data.model.Routine
import com.fractanomics.crosstraining.data.model.Session
import com.fractanomics.crosstraining.data.model.SessionBlock
import com.fractanomics.crosstraining.data.model.SessionWithBlocks
import com.fractanomics.crosstraining.util.AVAILABLE_BLOCK_FORMAT
import com.fractanomics.crosstraining.util.AvailableBlock
import com.fractanomics.crosstraining.util.AvailableBlockExercise
import com.fractanomics.crosstraining.util.ConstituentTrendDirection
import com.fractanomics.crosstraining.util.DAILY_ROUTINE_FORMAT
import com.fractanomics.crosstraining.util.DailyRoutine
import com.fractanomics.crosstraining.util.DailyRoutineCodec
import com.fractanomics.crosstraining.util.DailyRoutineItem
import com.fractanomics.crosstraining.util.DailyRoutineItemType
import com.fractanomics.crosstraining.util.STANDALONE_EXERCISE_SESSION_FORMAT
import com.fractanomics.crosstraining.util.TrackedPrSlot
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate

class DailyRoutineProgressTest {

    private val backSquat = Exercise(
        id = 1L,
        name = "Back Squat",
        category = ExerciseCategory.BARBELL,
        metricType = MetricType.WEIGHT,
        unit = "kg",
        tracksRepMax = true
    )
    private val rdl = Exercise(
        id = 2L,
        name = "Romanian Deadlift",
        category = ExerciseCategory.BARBELL,
        metricType = MetricType.WEIGHT,
        unit = "kg",
        tracksRepMax = true
    )
    private val hipThrust = Exercise(
        id = 3L,
        name = "Hip Thrust",
        category = ExerciseCategory.ACCESSORY,
        metricType = MetricType.WEIGHT,
        unit = "kg",
        tracksRepMax = false
    )

    @Test
    fun `deriveAvailableProgressModes places BY_DAILY_ROUTINE first and excludes BY_ROUTINE when only daily routines exist`() {
        val dailyOnly = listOf(Routine(id = 1L, name = "Upper Push", defaultFormat = DAILY_ROUTINE_FORMAT))
        val nonDaily = dailyOnly.filterNot { DailyRoutineCodec.isDailyRoutine(it) }
        assertTrue(nonDaily.isEmpty())

        val modes = deriveAvailableProgressModes(hasBlocks = nonDaily.isNotEmpty(), hasCycles = false)
        assertEquals(
            listOf(ProgressMode.BY_DAILY_ROUTINE, ProgressMode.BY_EXERCISE, ProgressMode.BODY_WEIGHT),
            modes
        )

        val mixed = dailyOnly + Routine(id = 2L, name = "Squat Wave", defaultFormat = AVAILABLE_BLOCK_FORMAT)
        val nonDailyMixed = mixed.filterNot { DailyRoutineCodec.isDailyRoutine(it) }
        assertEquals(listOf("Squat Wave"), nonDailyMixed.map { it.name })

        val fullModes = deriveAvailableProgressModes(hasBlocks = true, hasCycles = true)
        assertEquals(ProgressMode.BY_DAILY_ROUTINE, fullModes.first())
        assertEquals(ProgressMode.BY_ROUTINE, fullModes[1])
        assertEquals(ProgressMode.BY_EXERCISE, fullModes[2])
    }

    @Test
    fun `resolveDefaultSelectedDailyRoutine prefers most recently logged routine`() {
        val routineA = DailyRoutine(id = 10L, title = "Day A", notes = "", items = emptyList())
        val routineB = DailyRoutine(id = 20L, title = "Day B", notes = "", items = emptyList())
        val sessions = listOf(
            SessionWithBlocks(
                session = Session(id = 1L, cycleId = 1L, date = LocalDate.of(2026, 11, 10), title = "Day A"),
                blocks = emptyList()
            ),
            SessionWithBlocks(
                session = Session(id = 2L, cycleId = 1L, date = LocalDate.of(2026, 11, 14), title = "Day B"),
                blocks = emptyList()
            )
        )

        val selected = resolveDefaultSelectedDailyRoutine(listOf(routineA, routineB), sessions)
        assertEquals(20L, selected?.id)

        val fallback = resolveDefaultSelectedDailyRoutine(listOf(routineA, routineB), emptyList())
        assertEquals(10L, fallback?.id)
    }

    @Test
    fun `computeDailyRoutineProgress aggregates block and exercise trends PR badges and unlogged placeholders`() {
        val squatBlock = AvailableBlock(
            id = 5L,
            title = "Squat Wave",
            instructions = "3x5 heavy",
            exercises = listOf(AvailableBlockExercise(1L, "Back Squat", true)),
            trackedPrs = listOf(TrackedPrSlot(1L, "Back Squat", 5))
        )
        val routine = DailyRoutine(
            id = 30L,
            title = "Lower Day",
            notes = "",
            items = listOf(
                DailyRoutineItem.BlockRef(5L, "Squat Wave", squatBlock),
                DailyRoutineItem.StandaloneExercise(2L, "Romanian Deadlift", note = "3s eccentric"),
                DailyRoutineItem.StandaloneExercise(3L, "Hip Thrust", note = "")
            )
        )

        val day1 = LocalDate.of(2026, 11, 10)
        val day2 = LocalDate.of(2026, 11, 14)
        val sessions = listOf(
            SessionWithBlocks(
                session = Session(id = 101L, cycleId = 1L, date = day1, title = "Lower Day"),
                blocks = listOf(
                    BlockWithSets(
                        block = SessionBlock(
                            id = 1001L,
                            sessionId = 101L,
                            position = 0,
                            name = "Back Squat",
                            kind = BlockKind.STRENGTH,
                            mainExerciseId = 1L,
                            routineId = 5L,
                            subBlock = "Squat Wave"
                        ),
                        sets = listOf(BlockSet(blockId = 1001L, position = 0, reps = 5, weight = 100.0))
                    ),
                    // Ad-hoc standalone log outside Daily Routine still contributes to RDL trend!
                    BlockWithSets(
                        block = SessionBlock(
                            id = 1002L,
                            sessionId = 101L,
                            position = 1,
                            name = "Romanian Deadlift",
                            kind = BlockKind.STRENGTH,
                            format = STANDALONE_EXERCISE_SESSION_FORMAT,
                            mainExerciseId = 2L
                        ),
                        sets = listOf(BlockSet(blockId = 1002L, position = 0, reps = 8, weight = 80.0))
                    )
                )
            ),
            SessionWithBlocks(
                session = Session(id = 102L, cycleId = 1L, date = day2, title = "Lower Day"),
                blocks = listOf(
                    BlockWithSets(
                        block = SessionBlock(
                            id = 1003L,
                            sessionId = 102L,
                            position = 0,
                            name = "Back Squat",
                            kind = BlockKind.STRENGTH,
                            mainExerciseId = 1L,
                            routineId = 5L,
                            subBlock = "Squat Wave"
                        ),
                        sets = listOf(BlockSet(blockId = 1003L, position = 0, reps = 5, weight = 105.0))
                    ),
                    BlockWithSets(
                        block = SessionBlock(
                            id = 1004L,
                            sessionId = 102L,
                            position = 1,
                            name = "Romanian Deadlift",
                            kind = BlockKind.STRENGTH,
                            format = STANDALONE_EXERCISE_SESSION_FORMAT,
                            mainExerciseId = 2L
                        ),
                        sets = listOf(BlockSet(blockId = 1004L, position = 0, reps = 8, weight = 80.0))
                    )
                )
            )
        )
        val repMaxes = listOf(
            RepMax(id = 1L, exerciseId = 1L, reps = 5, weight = 105.0, date = day2, sessionId = 102L)
        )

        val summaries = DailyRoutineCodec.computeDailyRoutineProgress(
            routine = routine,
            sessions = sessions,
            repMaxes = repMaxes,
            exercises = listOf(backSquat, rdl, hipThrust),
            weightUnit = "kg"
        )

        assertEquals(3, summaries.size)

        // Squat block improved +5 kg
        assertEquals(DailyRoutineItemType.BLOCK, summaries[0].itemType)
        assertEquals(ConstituentTrendDirection.UP, summaries[0].trendDirection)
        assertEquals(5.0, summaries[0].topDelta ?: 0.0, 0.001)
        assertEquals(105.0, summaries[0].allTimeTop ?: 0.0, 0.001)
        assertTrue(summaries[0].bestPrBadges.any { it.contains("5RM Back Squat") })

        // RDL stayed flat 0 kg
        assertEquals(DailyRoutineItemType.EXERCISE, summaries[1].itemType)
        assertEquals(ConstituentTrendDirection.FLAT, summaries[1].trendDirection)
        assertEquals("3s eccentric", summaries[1].subtitle)

        // Hip Thrust never logged
        assertEquals(DailyRoutineItemType.EXERCISE, summaries[2].itemType)
        assertEquals(ConstituentTrendDirection.NONE, summaries[2].trendDirection)
        assertNull(summaries[2].lastTop)
    }
}
