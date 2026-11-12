package com.fractanomics.crosstraining.ui.screens

import com.fractanomics.crosstraining.data.model.BlockKind
import com.fractanomics.crosstraining.data.model.Exercise
import com.fractanomics.crosstraining.data.model.ExerciseCategory
import com.fractanomics.crosstraining.data.model.MetricType
import com.fractanomics.crosstraining.data.model.Routine
import com.fractanomics.crosstraining.data.model.RoutineBlock
import com.fractanomics.crosstraining.data.model.RoutineWithBlocks
import com.fractanomics.crosstraining.util.ExerciseTargetMetric
import com.fractanomics.crosstraining.util.RoutineExerciseTargetSpec
import com.fractanomics.crosstraining.util.RoutineTargetCodec
import com.fractanomics.crosstraining.util.TargetMode
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class RoutineSessionExpansionTest {

    private val backSquat = Exercise(
        id = 1L,
        name = "Back Squat",
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
        unit = "cal"
    )
    private val shuttleRun = Exercise(
        id = 3L,
        name = "Shuttle Run",
        category = ExerciseCategory.MACHINE,
        metricType = MetricType.DISTANCE,
        unit = "m"
    )
    private val plankHold = Exercise(
        id = 4L,
        name = "Plank Hold",
        category = ExerciseCategory.GYMNASTICS,
        metricType = MetricType.TIME,
        unit = "sec"
    )
    private val burpees = Exercise(
        id = 5L,
        name = "Burpees",
        category = ExerciseCategory.GYMNASTICS,
        metricType = MetricType.REPS,
        unit = "reps"
    )

    private val allExercises = listOf(backSquat, airBike, shuttleRun, plankHold, burpees)

    @Test
    fun `Multi-exercise mixed-metric block expands into SubBlockGroup omitting 0 Skip sets and preserving round indices`() {
        val notes = RoutineTargetCodec.encodeNotesWithTargets(
            cleanNotes = "Alternate efforts cleanly",
            slots = listOf(
                RoutineExerciseTargetSpec(
                    metric = ExerciseTargetMetric.CALORIES,
                    mode = TargetMode.PER_SET,
                    fixedValue = 15,
                    perSetValues = listOf(15, 0, 12, 0)
                ),
                RoutineExerciseTargetSpec(
                    metric = ExerciseTargetMetric.REPS,
                    mode = TargetMode.PER_SET,
                    fixedValue = 10,
                    perSetValues = listOf(0, 12, 0, 10)
                ),
                RoutineExerciseTargetSpec(
                    metric = ExerciseTargetMetric.TIME_SECONDS,
                    mode = TargetMode.FIXED,
                    fixedValue = 40,
                    perSetValues = listOf(40, 40, 40, 40)
                )
            ),
            setsCount = 4
        )

        val rwb = RoutineWithBlocks(
            routine = Routine(id = 10L, name = "Interval Engine"),
            blocks = listOf(
                RoutineBlock(
                    id = 101L,
                    routineId = 10L,
                    position = 0,
                    name = "Alternating Triplet",
                    kind = BlockKind.METCON,
                    format = "EMOM",
                    setsCount = 4,
                    targetRepsScheme = "15-0-12-0 cal + 0-12-0-10 reps + 40s",
                    exerciseIdsCsv = "2,5,4",
                    notes = notes
                )
            )
        )

        val sessionBlocks = expandRoutineIntoSessionBlocks(rwb, allExercises, defaultCompleted = true)
        assertEquals(3, sessionBlocks.size)

        val grouped = groupEditorBlocks(sessionBlocks)
        assertEquals(1, grouped.size)
        assertTrue(grouped[0] is EditorBlockItem.SubBlockGroup)

        val subGroup = grouped[0] as EditorBlockItem.SubBlockGroup
        assertEquals("Alternating Triplet", subGroup.subBlockName)
        assertEquals("EMOM", subGroup.format)
        assertEquals(4, subGroup.totalRounds)

        // 1. Air Bike: rounds 1 and 3 active (15 cal, 12 cal)
        val bikeBlock = sessionBlocks[0]
        assertEquals("Air Bike", bikeBlock.name)
        assertEquals("Alternate efforts cleanly", bikeBlock.description)
        assertFalse(bikeBlock.description.contains("[rb_v2="))
        assertEquals(2, bikeBlock.sets.size)
        assertEquals("1", bikeBlock.sets[0].reps)
        assertEquals("15", bikeBlock.sets[0].value)
        assertEquals("1", bikeBlock.sets[0].group)
        assertEquals("1", bikeBlock.sets[1].reps)
        assertEquals("12", bikeBlock.sets[1].value)
        assertEquals("3", bikeBlock.sets[1].group)

        val bikeDraft = bikeBlock.toDraftOrNull()
        assertNotNull(bikeDraft)
        assertEquals(listOf(15.0, 12.0), bikeDraft!!.sets.map { it.metricValue })
        assertEquals(listOf(1, 3), bikeDraft.sets.map { it.groupIndex })

        // 2. Burpees: rounds 2 and 4 active (12 reps, 10 reps)
        val burpeeBlock = sessionBlocks[1]
        assertEquals("Burpees", burpeeBlock.name)
        assertEquals(2, burpeeBlock.sets.size)
        assertEquals("12", burpeeBlock.sets[0].reps)
        assertEquals("", burpeeBlock.sets[0].value)
        assertEquals("2", burpeeBlock.sets[0].group)
        assertEquals("10", burpeeBlock.sets[1].reps)
        assertEquals("4", burpeeBlock.sets[1].group)

        val burpeeDraft = burpeeBlock.toDraftOrNull()
        assertNotNull(burpeeDraft)
        assertEquals(listOf(12, 10), burpeeDraft!!.sets.map { it.reps })
        assertEquals(listOf(2, 4), burpeeDraft.sets.map { it.groupIndex })

        // 3. Plank Hold: all 4 rounds active (40s)
        val plankBlock = sessionBlocks[2]
        assertEquals("Plank Hold", plankBlock.name)
        assertEquals(4, plankBlock.sets.size)
        assertEquals(listOf("40", "40", "40", "40"), plankBlock.sets.map { it.value })
        assertEquals(listOf("1", "2", "3", "4"), plankBlock.sets.map { it.group })
    }

    @Test
    fun `Repeated sequence exercises preserve positional order in SubBlockGroup`() {
        val notes = RoutineTargetCodec.encodeNotesWithTargets(
            cleanNotes = "",
            slots = listOf(
                RoutineExerciseTargetSpec(ExerciseTargetMetric.METERS, TargetMode.FIXED, 200, listOf(200, 200, 200)),
                RoutineExerciseTargetSpec(ExerciseTargetMetric.REPS, TargetMode.FIXED, 15, listOf(15, 15, 15)),
                RoutineExerciseTargetSpec(ExerciseTargetMetric.METERS, TargetMode.FIXED, 100, listOf(100, 100, 100))
            ),
            setsCount = 3
        )

        val rwb = RoutineWithBlocks(
            routine = Routine(id = 11L, name = "Sandwich Sprint"),
            blocks = listOf(
                RoutineBlock(
                    id = 102L,
                    routineId = 11L,
                    position = 0,
                    name = "Shuttle Sandwich",
                    kind = BlockKind.METCON,
                    format = "Rounds for Time",
                    setsCount = 3,
                    exerciseIdsCsv = "3,5,3",
                    notes = notes
                )
            )
        )

        val sessionBlocks = expandRoutineIntoSessionBlocks(rwb, allExercises)
        assertEquals(3, sessionBlocks.size)
        assertEquals(listOf("Shuttle Run", "Burpees", "Shuttle Run"), sessionBlocks.map { it.name })
        assertEquals("200", sessionBlocks[0].sets[0].value)
        assertEquals("15", sessionBlocks[1].sets[0].reps)
        assertEquals("100", sessionBlocks[2].sets[0].value)
    }

    @Test
    fun `Single-exercise block creates standalone BlockState and preserves legacy fallback`() {
        val v2SquatNotes = RoutineTargetCodec.encodeNotesWithTargets(
            cleanNotes = "Pause 2s in bottom",
            slots = listOf(
                RoutineExerciseTargetSpec(
                    metric = ExerciseTargetMetric.REPS,
                    mode = TargetMode.PER_SET,
                    fixedValue = 5,
                    perSetValues = listOf(5, 3, 0, 1)
                )
            ),
            setsCount = 4
        )

        val rwb = RoutineWithBlocks(
            routine = Routine(id = 12L, name = "Squat & Legacy", mainExerciseId = 1L),
            blocks = listOf(
                RoutineBlock(
                    id = 103L,
                    routineId = 12L,
                    position = 0,
                    name = "Heavy Back Squat",
                    kind = BlockKind.WEIGHTLIFTING,
                    format = "E3MOM",
                    setsCount = 4,
                    exerciseIdsCsv = "1",
                    notes = v2SquatNotes
                ),
                RoutineBlock(
                    id = 104L,
                    routineId = 12L,
                    position = 1,
                    name = "Legacy Finisher",
                    kind = BlockKind.METCON,
                    format = "For Time",
                    setsCount = 3,
                    targetRepsScheme = "21-15-9",
                    exerciseIdsCsv = ""
                )
            )
        )

        val sessionBlocks = expandRoutineIntoSessionBlocks(rwb, allExercises)
        assertEquals(2, sessionBlocks.size)

        val grouped = groupEditorBlocks(sessionBlocks)
        assertEquals(2, grouped.size)
        assertTrue(grouped.all { it is EditorBlockItem.Standalone })

        val squatBlock = sessionBlocks[0]
        assertEquals("Heavy Back Squat", squatBlock.name)
        assertEquals("Pause 2s in bottom", squatBlock.description)
        assertEquals(listOf("5", "3", "1"), squatBlock.sets.map { it.reps })
        assertEquals(listOf("1", "2", "4"), squatBlock.sets.map { it.group })
        val squatDraft = squatBlock.toDraftOrNull()!!
        assertNull(squatDraft.sets[0].weight)
        assertEquals(listOf(1, 2, 4), squatDraft.sets.map { it.groupIndex })

        val legacyBlock = sessionBlocks[1]
        assertEquals("Legacy Finisher", legacyBlock.name)
        assertEquals(backSquat, legacyBlock.exercise)
        assertEquals(listOf("21", "15", "9"), legacyBlock.sets.map { it.reps })
    }

    @Test
    fun `Legacy single-exercise blocks keep pre-redesign RepScheme set expansion`() {
        val rwb = RoutineWithBlocks(
            routine = Routine(id = 13L, name = "Legacy Strength"),
            blocks = listOf(
                RoutineBlock(id = 105L, routineId = 13L, position = 0, name = "Comma", setsCount = 3, targetRepsScheme = "5,5,3", exerciseIdsCsv = "1"),
                RoutineBlock(id = 106L, routineId = 13L, position = 1, name = "NxM", setsCount = 1, targetRepsScheme = "5x3", exerciseIdsCsv = "1"),
                RoutineBlock(id = 107L, routineId = 13L, position = 2, name = "Wave", setsCount = 4, targetRepsScheme = "3-2-1-3-2-1", exerciseIdsCsv = "1")
            )
        )

        val sessionBlocks = expandRoutineIntoSessionBlocks(rwb, allExercises)
        assertEquals(3, sessionBlocks.size)
        assertEquals(listOf("5", "5", "3"), sessionBlocks[0].sets.map { it.reps })
        assertEquals(listOf("3", "3", "3", "3", "3"), sessionBlocks[1].sets.map { it.reps })
        assertEquals(listOf("3", "2", "1", "3", "2", "1"), sessionBlocks[2].sets.map { it.reps })
        assertTrue(sessionBlocks.flatMap { it.sets }.all { it.value.isEmpty() })
    }

    @Test
    fun `Non-rep targets on weight-tracked exercises are never logged as weight`() {
        val notes = RoutineTargetCodec.encodeNotesWithTargets(
            cleanNotes = "",
            slots = listOf(
                RoutineExerciseTargetSpec(ExerciseTargetMetric.METERS, TargetMode.FIXED, 250, listOf(250, 250))
            ),
            setsCount = 2
        )
        val rwb = RoutineWithBlocks(
            routine = Routine(id = 14L, name = "Row Intervals"),
            blocks = listOf(
                RoutineBlock(id = 108L, routineId = 14L, position = 0, name = "Row", setsCount = 2, exerciseIdsCsv = "1", notes = notes)
            )
        )

        val block = expandRoutineIntoSessionBlocks(rwb, allExercises).single()
        assertEquals(MetricType.WEIGHT, block.exercise?.metricType)
        assertEquals(listOf("250", "250"), block.sets.map { it.reps })
        val draft = block.toDraftOrNull()!!
        assertTrue(draft.sets.all { it.weight == null })
    }
}
