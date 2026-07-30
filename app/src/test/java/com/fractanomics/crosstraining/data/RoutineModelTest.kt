package com.fractanomics.crosstraining.data

import com.fractanomics.crosstraining.data.model.BlockKind
import com.fractanomics.crosstraining.data.model.Routine
import com.fractanomics.crosstraining.data.model.RoutineBlock
import com.fractanomics.crosstraining.data.model.RoutineWithBlocks
import com.fractanomics.crosstraining.util.RepScheme
import org.junit.Assert.assertEquals
import org.junit.Test

class RoutineModelTest {

    @Test
    fun `test RoutineWithBlocks construction and ordering`() {
        val routine = Routine(id = 1, name = "Monday Workout", description = "Snatch & Metcon")
        val blocks = listOf(
            RoutineBlock(id = 10, routineId = 1, position = 0, name = "Weightlifting Block", kind = BlockKind.WEIGHTLIFTING, format = "EMOM 10", setsCount = 12, targetRepsScheme = RepScheme.WAVE_321),
            RoutineBlock(id = 11, routineId = 1, position = 1, name = "Hypertrophy Block", kind = BlockKind.HYPERTROPHY, format = "Rest 90s", setsCount = 4, targetRepsScheme = "8"),
            RoutineBlock(id = 12, routineId = 1, position = 2, name = "Metabolic WOD", kind = BlockKind.METABOLIC, format = "AMRAP 12", setsCount = 1)
        )

        val rwb = RoutineWithBlocks(routine, blocks)

        assertEquals("Monday Workout", rwb.routine.name)
        assertEquals(3, rwb.blocks.size)
        assertEquals(BlockKind.WEIGHTLIFTING, rwb.blocks[0].kind)
        assertEquals(RepScheme.WAVE_321, rwb.blocks[0].targetRepsScheme)
        assertEquals(BlockKind.HYPERTROPHY, rwb.blocks[1].kind)
        assertEquals(BlockKind.METABOLIC, rwb.blocks[2].kind)
        assertEquals("AMRAP 12", rwb.blocks[2].format)
    }

    @Test
    fun `test RepScheme parsing waves and fixed reps`() {
        val wave321Reps = RepScheme.parse(RepScheme.WAVE_321, 12)
        assertEquals(listOf(3, 2, 1, 3, 2, 1, 3, 2, 1, 1, 1, 1), wave321Reps)

        val wave221Reps = RepScheme.parse(RepScheme.WAVE_221, 12)
        assertEquals(listOf(2, 2, 1, 2, 2, 1, 2, 2, 1, 1, 1, 1), wave221Reps)

        val fixedReps = RepScheme.parse("5", 4)
        assertEquals(listOf(5, 5, 5, 5), fixedReps)
    }
}
