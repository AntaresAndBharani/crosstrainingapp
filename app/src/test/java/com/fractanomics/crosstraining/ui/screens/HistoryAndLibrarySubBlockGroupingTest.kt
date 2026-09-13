package com.fractanomics.crosstraining.ui.screens

import com.fractanomics.crosstraining.data.model.BlockKind
import com.fractanomics.crosstraining.data.model.BlockSet
import com.fractanomics.crosstraining.data.model.BlockWithSets
import com.fractanomics.crosstraining.data.model.RoutineBlock
import com.fractanomics.crosstraining.data.model.SessionBlock
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Unit tests for sub-block container grouping and asymmetric round reconciliation in HistoryScreen and LibraryScreen (Issue #541).
 *
 * Scenarios verified:
 * - Scenario 4: Sub-block hierarchical parity in HistoryScreen (grouped contiguous blocks sharing subBlock).
 * - Scenario 6: Fallback UX for empty or blank subBlock (rendered as Standalone without grouping containers).
 * - Scenario 7: Triset atomic grouping and maxOf(sets.size) round reconciliation across asymmetric logged sets.
 * - Routine parity: LibraryScreen routine blocks grouped by section and subBlock (with targetRepsScheme fallback).
 * - Contiguity invariant: Non-contiguous sub-blocks form distinct groups.
 */
class HistoryAndLibrarySubBlockGroupingTest {

    @Test
    fun `groupHistoryBlocks groups contiguous blocks sharing non-blank subBlock and section`() {
        val b1 = BlockWithSets(
            block = SessionBlock(id = 1, sessionId = 10, position = 0, name = "Clean Complex", format = "E3MOM", section = "Strength & Power", subBlock = "E3MOM Complex"),
            sets = listOf(BlockSet(id = 1, blockId = 1, position = 0, reps = 1, weight = 70.0))
        )
        val b2 = BlockWithSets(
            block = SessionBlock(id = 2, sessionId = 10, position = 1, name = "Front Squats", format = "E3MOM", section = "Strength & Power", subBlock = "E3MOM Front Squats"),
            sets = listOf(BlockSet(id = 2, blockId = 2, position = 0, reps = 4, weight = 80.0))
        )
        val b3 = BlockWithSets(
            block = SessionBlock(id = 3, sessionId = 10, position = 2, name = "RDL", format = "E3MOM", section = "Accessories", subBlock = "E3MOM Trisets"),
            sets = listOf(BlockSet(id = 3, blockId = 3, position = 0, reps = 8, weight = 60.0))
        )
        val b4 = BlockWithSets(
            block = SessionBlock(id = 4, sessionId = 10, position = 3, name = "Pullups", format = "E3MOM", section = "Accessories", subBlock = "E3MOM Trisets"),
            sets = listOf(BlockSet(id = 4, blockId = 4, position = 0, reps = 6))
        )
        val b5 = BlockWithSets(
            block = SessionBlock(id = 5, sessionId = 10, position = 4, name = "DB Twist Curl", format = "E3MOM", section = "Accessories", subBlock = "E3MOM Trisets"),
            sets = listOf(BlockSet(id = 5, blockId = 5, position = 0, reps = 12, weight = 14.0))
        )

        val items = groupHistoryBlocks(listOf(b1, b2, b3, b4, b5))

        assertEquals(3, items.size)
        assertTrue(items[0] is HistoryBlockItem.SubBlockGroup)
        assertEquals("E3MOM Complex", (items[0] as HistoryBlockItem.SubBlockGroup).subBlockName)
        assertEquals(1, (items[0] as HistoryBlockItem.SubBlockGroup).items.size)

        assertTrue(items[1] is HistoryBlockItem.SubBlockGroup)
        assertEquals("E3MOM Front Squats", (items[1] as HistoryBlockItem.SubBlockGroup).subBlockName)
        assertEquals(1, (items[1] as HistoryBlockItem.SubBlockGroup).items.size)

        assertTrue(items[2] is HistoryBlockItem.SubBlockGroup)
        val group3 = items[2] as HistoryBlockItem.SubBlockGroup
        assertEquals("E3MOM Trisets", group3.subBlockName)
        assertEquals(3, group3.items.size)
        assertEquals(listOf(2, 3, 4), group3.items.map { it.first })
    }

    @Test
    fun `groupHistoryBlocks falls back to Standalone when subBlock is blank`() {
        val b1 = BlockWithSets(
            block = SessionBlock(id = 1, sessionId = 10, position = 0, name = "Legacy 1", section = "Strength", subBlock = ""),
            sets = listOf(BlockSet(id = 1, blockId = 1, position = 0, reps = 5, weight = 100.0))
        )
        val b2 = BlockWithSets(
            block = SessionBlock(id = 2, sessionId = 10, position = 1, name = "Legacy 2", section = "Strength", subBlock = "   "),
            sets = listOf(BlockSet(id = 2, blockId = 2, position = 0, reps = 5, weight = 100.0))
        )
        val b3 = BlockWithSets(
            block = SessionBlock(id = 3, sessionId = 10, position = 2, name = "Triset A1", section = "Accessories", subBlock = "Triset A"),
            sets = listOf(BlockSet(id = 3, blockId = 3, position = 0, reps = 10))
        )
        val b4 = BlockWithSets(
            block = SessionBlock(id = 4, sessionId = 10, position = 3, name = "Triset A2", section = "Accessories", subBlock = "Triset A"),
            sets = listOf(BlockSet(id = 4, blockId = 4, position = 0, reps = 10))
        )

        val items = groupHistoryBlocks(listOf(b1, b2, b3, b4))

        assertEquals(3, items.size)
        assertTrue(items[0] is HistoryBlockItem.Standalone)
        assertEquals(0, (items[0] as HistoryBlockItem.Standalone).index)
        assertEquals("Legacy 1", (items[0] as HistoryBlockItem.Standalone).blockWithSets.block.name)

        assertTrue(items[1] is HistoryBlockItem.Standalone)
        assertEquals(1, (items[1] as HistoryBlockItem.Standalone).index)
        assertEquals("Legacy 2", (items[1] as HistoryBlockItem.Standalone).blockWithSets.block.name)

        assertTrue(items[2] is HistoryBlockItem.SubBlockGroup)
        assertEquals("Triset A", (items[2] as HistoryBlockItem.SubBlockGroup).subBlockName)
        assertEquals(2, (items[2] as HistoryBlockItem.SubBlockGroup).items.size)
    }

    @Test
    fun `groupHistoryBlocks reconciles asymmetric rounds using maxOf sets size`() {
        val b1 = BlockWithSets(
            block = SessionBlock(id = 1, sessionId = 10, position = 0, name = "RDL", section = "Accessories", subBlock = "E3MOM Trisets"),
            sets = listOf(
                BlockSet(id = 1, blockId = 1, position = 0, reps = 8, weight = 60.0),
                BlockSet(id = 2, blockId = 1, position = 1, reps = 8, weight = 60.0),
                BlockSet(id = 3, blockId = 1, position = 2, reps = 8, weight = 60.0),
                BlockSet(id = 4, blockId = 1, position = 3, reps = 8, weight = 60.0)
            ) // 4 rounds
        )
        val b2 = BlockWithSets(
            block = SessionBlock(id = 2, sessionId = 10, position = 1, name = "Pullups", section = "Accessories", subBlock = "E3MOM Trisets"),
            sets = listOf(
                BlockSet(id = 5, blockId = 2, position = 0, reps = 6),
                BlockSet(id = 6, blockId = 2, position = 1, reps = 5)
            ) // 2 rounds logged
        )
        val b3 = BlockWithSets(
            block = SessionBlock(id = 3, sessionId = 10, position = 2, name = "DB Twist Curl", section = "Accessories", subBlock = "E3MOM Trisets"),
            sets = listOf(
                BlockSet(id = 7, blockId = 3, position = 0, reps = 12, weight = 14.0),
                BlockSet(id = 8, blockId = 3, position = 1, reps = 12, weight = 14.0),
                BlockSet(id = 9, blockId = 3, position = 2, reps = 10, weight = 14.0)
            ) // 3 rounds logged
        )

        val items = groupHistoryBlocks(listOf(b1, b2, b3))
        assertEquals(1, items.size)
        assertTrue(items[0] is HistoryBlockItem.SubBlockGroup)
        val group = items[0] as HistoryBlockItem.SubBlockGroup
        assertEquals(3, group.items.size)
        assertEquals(4, group.totalRounds) // maxOf(4, 2, 3) = 4
    }

    @Test
    fun `broken triset in History leaves unmatched sibling movement as standalone card`() {
        val b1 = BlockWithSets(
            block = SessionBlock(id = 1, sessionId = 10, position = 0, name = "RDL", section = "Accessories", subBlock = "E3MOM Trisets"),
            sets = listOf(BlockSet(id = 1, blockId = 1, position = 0, reps = 8, weight = 60.0))
        )
        val b2 = BlockWithSets(
            block = SessionBlock(id = 2, sessionId = 10, position = 1, name = "Pullups", section = "Accessories", subBlock = ""), // Blank subBlock
            sets = listOf(BlockSet(id = 2, blockId = 2, position = 0, reps = 6))
        )
        val b3 = BlockWithSets(
            block = SessionBlock(id = 3, sessionId = 10, position = 2, name = "DB Twist Curl", section = "Accessories", subBlock = "E3MOM Trisets"),
            sets = listOf(BlockSet(id = 3, blockId = 3, position = 0, reps = 12, weight = 14.0))
        )

        val items = groupHistoryBlocks(listOf(b1, b2, b3))
        assertEquals(3, items.size)
        assertTrue(items[0] is HistoryBlockItem.SubBlockGroup)
        assertTrue(items[1] is HistoryBlockItem.Standalone)
        assertTrue(items[2] is HistoryBlockItem.SubBlockGroup)
    }

    @Test
    fun `groupRoutineBlocks groups routine blocks by subBlock or targetRepsScheme fallback`() {
        val r1 = RoutineBlock(
            id = 1, routineId = 1, position = 0, name = "Clean Complex", format = "E3MOM",
            setsCount = 4, targetRepsScheme = "4x1", section = "Strength & Power", subBlock = "E3MOM Complex"
        )
        val r2 = RoutineBlock(
            id = 2, routineId = 1, position = 1, name = "Front Squats", format = "E3MOM",
            setsCount = 4, targetRepsScheme = "4x4", section = "Strength & Power", subBlock = "E3MOM Front Squats"
        )
        val r3 = RoutineBlock(
            id = 3, routineId = 1, position = 2, name = "RDL", format = "E3MOM",
            setsCount = 4, targetRepsScheme = "TRISET_1", section = "Accessories", subBlock = "E3MOM Trisets"
        )
        val r4 = RoutineBlock(
            id = 4, routineId = 1, position = 3, name = "Pullups", format = "E3MOM",
            setsCount = 4, targetRepsScheme = "TRISET_1", section = "Accessories", subBlock = "E3MOM Trisets"
        )
        val r5 = RoutineBlock(
            id = 5, routineId = 1, position = 4, name = "DB Twist Curl", format = "E3MOM",
            setsCount = 4, targetRepsScheme = "TRISET_1", section = "Accessories", subBlock = "E3MOM Trisets"
        )

        val items = groupRoutineBlocks(listOf(r1, r2, r3, r4, r5))

        assertEquals(3, items.size)
        assertTrue(items[0] is RoutineBlockItem.SubBlockGroup)
        assertEquals("E3MOM Complex", (items[0] as RoutineBlockItem.SubBlockGroup).subBlockName)
        assertEquals(1, (items[0] as RoutineBlockItem.SubBlockGroup).items.size)

        assertTrue(items[1] is RoutineBlockItem.SubBlockGroup)
        assertEquals("E3MOM Front Squats", (items[1] as RoutineBlockItem.SubBlockGroup).subBlockName)
        assertEquals(1, (items[1] as RoutineBlockItem.SubBlockGroup).items.size)

        assertTrue(items[2] is RoutineBlockItem.SubBlockGroup)
        val g3 = items[2] as RoutineBlockItem.SubBlockGroup
        assertEquals("E3MOM Trisets", g3.subBlockName)
        assertEquals(3, g3.items.size)
        assertEquals(listOf(2, 3, 4), g3.items.map { it.first })
        assertEquals(4, g3.totalSetsCount)
    }

    @Test
    fun `groupRoutineBlocks falls back to targetRepsScheme cluster if subBlock is blank`() {
        val r1 = RoutineBlock(
            id = 1, routineId = 1, position = 0, name = "RDL", format = "E3MOM",
            setsCount = 4, targetRepsScheme = "TRISET_1", section = "Accessories", subBlock = ""
        )
        val r2 = RoutineBlock(
            id = 2, routineId = 1, position = 1, name = "Pullups", format = "E3MOM",
            setsCount = 4, targetRepsScheme = "TRISET_1", section = "Accessories", subBlock = ""
        )
        val r3 = RoutineBlock(
            id = 3, routineId = 1, position = 2, name = "DB Curl", format = "E3MOM",
            setsCount = 4, targetRepsScheme = "TRISET_1", section = "Accessories", subBlock = ""
        )
        val r4 = RoutineBlock(
            id = 4, routineId = 1, position = 3, name = "Standalone Accessory", format = "",
            setsCount = 3, targetRepsScheme = "3x10", section = "Accessories", subBlock = ""
        )

        val items = groupRoutineBlocks(listOf(r1, r2, r3, r4))

        assertEquals(2, items.size)
        assertTrue(items[0] is RoutineBlockItem.SubBlockGroup)
        val group = items[0] as RoutineBlockItem.SubBlockGroup
        assertEquals("TRISET_1", group.subBlockName)
        assertEquals(3, group.items.size)
        assertEquals(4, group.totalSetsCount)

        assertTrue(items[1] is RoutineBlockItem.Standalone)
        assertEquals("Standalone Accessory", (items[1] as RoutineBlockItem.Standalone).block.name)
    }
}
