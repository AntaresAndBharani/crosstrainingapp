package com.fractanomics.crosstraining.data

import com.fractanomics.crosstraining.data.model.BlockKind
import com.fractanomics.crosstraining.data.model.Routine
import com.fractanomics.crosstraining.data.model.RoutineBlock
import com.fractanomics.crosstraining.data.model.RoutineWithBlocks
import org.junit.Assert.assertEquals
import org.junit.Test

class RoutineModelTest {

    @Test
    fun `test RoutineWithBlocks construction and ordering`() {
        val routine = Routine(id = 1, name = "Monday Workout", description = "Snatch & Metcon")
        val blocks = listOf(
            RoutineBlock(id = 10, routineId = 1, position = 0, name = "Weightlifting Block", kind = BlockKind.WEIGHTLIFTING, format = "EMOM 10", setsCount = 5),
            RoutineBlock(id = 11, routineId = 1, position = 1, name = "Hypertrophy Block", kind = BlockKind.HYPERTROPHY, format = "Rest 90s", setsCount = 4),
            RoutineBlock(id = 12, routineId = 1, position = 2, name = "Metabolic WOD", kind = BlockKind.METABOLIC, format = "AMRAP 12", setsCount = 1)
        )

        val rwb = RoutineWithBlocks(routine, blocks)

        assertEquals("Monday Workout", rwb.routine.name)
        assertEquals(3, rwb.blocks.size)
        assertEquals(BlockKind.WEIGHTLIFTING, rwb.blocks[0].kind)
        assertEquals(BlockKind.HYPERTROPHY, rwb.blocks[1].kind)
        assertEquals(BlockKind.METABOLIC, rwb.blocks[2].kind)
        assertEquals("AMRAP 12", rwb.blocks[2].format)
    }
}
