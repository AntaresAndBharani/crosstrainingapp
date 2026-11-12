package com.fractanomics.crosstraining.util

import com.fractanomics.crosstraining.data.BackupCsv
import com.fractanomics.crosstraining.data.BackupData
import com.fractanomics.crosstraining.data.model.BlockKind
import com.fractanomics.crosstraining.data.model.Exercise
import com.fractanomics.crosstraining.data.model.ExerciseCategory
import com.fractanomics.crosstraining.data.model.MetricType
import com.fractanomics.crosstraining.data.model.Routine
import com.fractanomics.crosstraining.data.model.RoutineBlock
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class RoutineTargetCodecTest {

    private val bench = Exercise(id = 10L, name = "Bench Press", category = ExerciseCategory.BARBELL, metricType = MetricType.WEIGHT)
    private val bike = Exercise(id = 11L, name = "Air Bike", category = ExerciseCategory.MACHINE, metricType = MetricType.CALORIES, unit = "cal", tracksRepMax = false)
    private val row = Exercise(id = 12L, name = "Concept2 Row", category = ExerciseCategory.MACHINE, metricType = MetricType.DISTANCE, unit = "m", tracksRepMax = false)
    private val plank = Exercise(id = 13L, name = "Plank Hold", category = ExerciseCategory.GYMNASTICS, metricType = MetricType.TIME, unit = "sec", tracksRepMax = false)
    private val exercisesById = listOf(bench, bike, row, plank).associateBy { it.id }

    @Test
    fun `encode and decode mixed-metric block with 0 Skip per-set slots round-trips losslessly`() {
        val slots = listOf(
            RoutineExerciseTargetSpec(
                metric = ExerciseTargetMetric.REPS,
                mode = TargetMode.FIXED,
                fixedValue = 8,
                perSetValues = listOf(8, 8, 8, 8)
            ),
            RoutineExerciseTargetSpec(
                metric = ExerciseTargetMetric.CALORIES,
                mode = TargetMode.PER_SET,
                fixedValue = 15,
                perSetValues = listOf(15, 15, 12, 0)
            ),
            RoutineExerciseTargetSpec(
                metric = ExerciseTargetMetric.METERS,
                mode = TargetMode.PER_SET,
                fixedValue = 250,
                perSetValues = listOf(250, 250, 200, 0)
            )
        )

        val encodedNotes = RoutineTargetCodec.encodeNotesWithTargets(
            cleanNotes = "Keep transitions crisp",
            slots = slots,
            setsCount = 4
        )
        assertTrue(RoutineTargetCodec.hasStructuredHeader(encodedNotes))
        assertEquals("Keep transitions crisp", RoutineTargetCodec.extractCleanNotes(encodedNotes))

        val summary = RoutineTargetCodec.formatBlockTargetSummary(slots, setsCount = 4)
        assertEquals("8 reps + 15-15-12-0 cal + 250-250-200-0m", summary)

        val block = RoutineBlock(
            id = 1L,
            routineId = 5L,
            position = 0,
            name = "Mixed Conditioning",
            kind = BlockKind.METCON,
            format = "E3MOM",
            setsCount = 4,
            targetRepsScheme = summary,
            exerciseIdsCsv = "10,11,12",
            notes = encodedNotes
        )

        val decoded = RoutineTargetCodec.decodeBlockExercises(block, exercisesById)
        assertEquals(3, decoded.size)
        assertEquals(10L to slots[0], decoded[0])
        assertEquals(11L to slots[1], decoded[1])
        assertEquals(12L to slots[2], decoded[2])

        assertEquals("8 reps / set", RoutineTargetCodec.formatExerciseTargetBadge(decoded[0].second, 4))
        assertEquals("15 • 15 • 12 • 0 cal", RoutineTargetCodec.formatExerciseTargetBadge(decoded[1].second, 4))
        assertEquals("250 • 250 • 200 • 0m", RoutineTargetCodec.formatExerciseTargetBadge(decoded[2].second, 4))
    }

    @Test
    fun `format-driven default sets match specification rules`() {
        listOf("AMRAP", "For Time", "Chipper", "Death by", "Time cap").forEach { fmt ->
            assertEquals("Expected 1 set for $fmt", 1, RoutineTargetCodec.defaultSetsForFormat(fmt))
            assertTrue(RoutineTargetCodec.isSingleEffortFormat(fmt))
        }
        assertEquals(8, RoutineTargetCodec.defaultSetsForFormat("Tabata"))
        listOf("Sets x Reps", "EMOM", "E2MOM", "E3MOM", "Rounds for Time", "Work/Rest Intervals", "Calorie Sprints", "Distance Sprints", "Wave", "Tempo").forEach { fmt ->
            assertEquals("Expected 4 sets for $fmt", 4, RoutineTargetCodec.defaultSetsForFormat(fmt))
            assertFalse(RoutineTargetCodec.isSingleEffortFormat(fmt))
        }
    }

    @Test
    fun `resizePerSetValues copies last set including 0 when increasing and trims when decreasing`() {
        val fourWithSkip = listOf(12, 10, 8, 0)
        // 4 -> 5 copies Set 4 (0)
        assertEquals(listOf(12, 10, 8, 0, 0), RoutineTargetCodec.resizePerSetValues(fourWithSkip, 12, 5))
        // 4 -> 3 trims to first 3
        assertEquals(listOf(12, 10, 8), RoutineTargetCodec.resizePerSetValues(fourWithSkip, 12, 3))

        val fourActive = listOf(15, 15, 12, 10)
        assertEquals(listOf(15, 15, 12, 10, 10, 10), RoutineTargetCodec.resizePerSetValues(fourActive, 15, 6))
    }

    @Test
    fun `pattern helpers generate expected per-set vectors and all-zero validation detects invalid specs`() {
        assertEquals(
            listOf(15, 15, 15, 15),
            RoutineTargetCodec.copyFirstSetToAll(listOf(15, 12, 10, 0), setsCount = 4, fallbackFixed = 15)
        )
        assertEquals(
            listOf(12, 10, 8, 6),
            RoutineTargetCodec.applyDescendingPattern(listOf(12, 12, 12, 12), ExerciseTargetMetric.REPS, setsCount = 4, fallbackFixed = 12)
        )
        assertEquals(
            listOf(15, 0, 15, 0),
            RoutineTargetCodec.applyAlternatingZerosPattern(listOf(15, 15, 15, 15), setsCount = 4, fallbackFixed = 15, zeroEvenSets = true)
        )

        val validSkip = RoutineExerciseTargetSpec(ExerciseTargetMetric.METERS, TargetMode.PER_SET, 250, listOf(250, 0, 250, 0))
        assertFalse(validSkip.isAllZeroInvalid)

        val invalidPerSet = RoutineExerciseTargetSpec(ExerciseTargetMetric.METERS, TargetMode.PER_SET, 250, listOf(0, 0, 0, 0))
        assertTrue(invalidPerSet.isAllZeroInvalid)

        val invalidFixed = RoutineExerciseTargetSpec(ExerciseTargetMetric.REPS, TargetMode.FIXED, 0, listOf(0, 0))
        assertTrue(invalidFixed.isAllZeroInvalid)
    }

    @Test
    fun `legacy RoutineBlock schemes decode non-destructively with inferred library metrics`() {
        // 1. Variable rep scheme "21-15-9"
        val franBlock = RoutineBlock(
            setsCount = 3,
            targetRepsScheme = "21-15-9",
            exerciseIdsCsv = "10"
        )
        val franDecoded = RoutineTargetCodec.decodeBlockExercises(franBlock, exercisesById)
        assertEquals(1, franDecoded.size)
        assertEquals(TargetMode.PER_SET, franDecoded[0].second.mode)
        assertEquals(listOf(21, 15, 9), franDecoded[0].second.perSetValues)

        // 2. Complex sequence "1 + 2"
        val complexBlock = RoutineBlock(
            setsCount = 5,
            targetRepsScheme = "1 + 2",
            exerciseIdsCsv = "10,13"
        )
        val complexDecoded = RoutineTargetCodec.decodeBlockExercises(complexBlock, exercisesById)
        assertEquals(2, complexDecoded.size)
        assertEquals(1, complexDecoded[0].second.fixedValue)
        assertEquals(2, complexDecoded[1].second.fixedValue)
        assertEquals(ExerciseTargetMetric.TIME_SECONDS, complexDecoded[1].second.metric)

        // 3. Sentinel scheme "TRISET_1" falls back cleanly to metric default
        val trisetBlock = RoutineBlock(
            setsCount = 4,
            targetRepsScheme = "TRISET_1",
            exerciseIdsCsv = "11"
        )
        val trisetDecoded = RoutineTargetCodec.decodeBlockExercises(trisetBlock, exercisesById)
        assertEquals(ExerciseTargetMetric.CALORIES, trisetDecoded[0].second.metric)
        assertEquals(15, trisetDecoded[0].second.fixedValue)
    }

    @Test
    fun `CSV backup round-trips structured rb_v2 routine blocks and repeated sequence IDs losslessly`() {
        val slots = listOf(
            RoutineExerciseTargetSpec(ExerciseTargetMetric.METERS, TargetMode.FIXED, 200, listOf(200, 200, 200)),
            RoutineExerciseTargetSpec(ExerciseTargetMetric.REPS, TargetMode.PER_SET, 15, listOf(15, 12, 9)),
            RoutineExerciseTargetSpec(ExerciseTargetMetric.METERS, TargetMode.PER_SET, 200, listOf(200, 200, 0))
        )
        val encodedNotes = RoutineTargetCodec.encodeNotesWithTargets("Shuttle-Burpee-Shuttle", slots, setsCount = 3)
        val routine = Routine(id = 1L, name = "Repeat Sequence WOD", description = "Test")
        val block = RoutineBlock(
            id = 10L,
            routineId = 1L,
            position = 0,
            name = "Shuttle Sandwich",
            kind = BlockKind.GYMNASTICS,
            format = "Rounds for Time",
            setsCount = 3,
            targetRepsScheme = RoutineTargetCodec.formatBlockTargetSummary(slots, 3),
            exerciseIdsCsv = "12,10,12", // Intentional repeated exercise ID!
            notes = encodedNotes
        )

        val csv = BackupCsv.encode(
            BackupData(
                exercises = listOf(bench, row),
                routines = listOf(routine),
                routineBlocks = listOf(block)
            )
        )
        val restored = BackupCsv.decode(csv)
        assertEquals(1, restored.routineBlocks.size)
        val restoredBlock = restored.routineBlocks.first()
        assertEquals(BlockKind.GYMNASTICS, restoredBlock.kind)
        assertEquals("12,10,12", restoredBlock.exerciseIdsCsv)
        assertEquals("Shuttle-Burpee-Shuttle", RoutineTargetCodec.extractCleanNotes(restoredBlock.notes))

        val decodedSlots = RoutineTargetCodec.decodeBlockExercises(restoredBlock, exercisesById)
        assertEquals(3, decodedSlots.size)
        assertEquals(12L to slots[0], decodedSlots[0])
        assertEquals(10L to slots[1], decodedSlots[1])
        assertEquals(12L to slots[2], decodedSlots[2])
    }
}
