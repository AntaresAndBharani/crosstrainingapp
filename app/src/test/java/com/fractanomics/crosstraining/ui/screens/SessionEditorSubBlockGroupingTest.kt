package com.fractanomics.crosstraining.ui.screens

import com.fractanomics.crosstraining.data.model.BlockKind
import com.fractanomics.crosstraining.data.model.Exercise
import com.fractanomics.crosstraining.data.model.ExerciseCategory
import com.fractanomics.crosstraining.data.model.MetricType
import com.fractanomics.crosstraining.data.model.Routine
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Unit tests for Sub-Block Container grouping, fallback, and round reconciliation in SessionEditor (Issue #540).
 *
 * Scenarios verified:
 * - Scenario 1: Sub-Block Container Grouping in SessionEditor (contiguous blocks sharing non-blank subBlock grouped)
 * - Scenario 6: Fallback UX for Empty or Blank Sub-Block (rendered as Standalone)
 * - Scenario 7: Triset Atomic Grouping and Broken Triset Resilience with maxOf(sets.size) round reconciliation
 * - Contiguity invariant: Non-contiguous blocks with same subBlock form separate groups
 * - Preservation: Saving / toDraftOrNull preserves section and subBlock while keeping position contiguous
 */
class SessionEditorSubBlockGroupingTest {

    private val barbellExercise = Exercise(
        id = 10,
        name = "Clean",
        category = ExerciseCategory.BARBELL,
        metricType = MetricType.WEIGHT,
        unit = "kg",
        tracksRepMax = true
    )

    @Test
    fun `groupEditorBlocks groups contiguous blocks sharing non-blank subBlock and section`() {
        val block1 = BlockState(name = "Clean Complex", format = "E3MOM", section = "Strength & Power", subBlock = "E3MOM Complex")
        val block2 = BlockState(name = "Front Squats", format = "E3MOM", section = "Strength & Power", subBlock = "E3MOM Front Squats")
        val block3 = BlockState(name = "RDL", format = "E3MOM", section = "Accessories", subBlock = "E3MOM Trisets")
        val block4 = BlockState(name = "Pullups", format = "E3MOM", section = "Accessories", subBlock = "E3MOM Trisets")
        val block5 = BlockState(name = "DB Twist Curl", format = "E3MOM", section = "Accessories", subBlock = "E3MOM Trisets")

        val items = groupEditorBlocks(listOf(block1, block2, block3, block4, block5))

        assertEquals(3, items.size)
        assertTrue(items[0] is EditorBlockItem.SubBlockGroup)
        val g1 = items[0] as EditorBlockItem.SubBlockGroup
        assertEquals("E3MOM Complex", g1.subBlockName)
        assertEquals(1, g1.items.size)

        assertTrue(items[1] is EditorBlockItem.SubBlockGroup)
        val g2 = items[1] as EditorBlockItem.SubBlockGroup
        assertEquals("E3MOM Front Squats", g2.subBlockName)
        assertEquals(1, g2.items.size)

        assertTrue(items[2] is EditorBlockItem.SubBlockGroup)
        val g3 = items[2] as EditorBlockItem.SubBlockGroup
        assertEquals("E3MOM Trisets", g3.subBlockName)
        assertEquals(3, g3.items.size)
        assertEquals(listOf(2, 3, 4), g3.items.map { it.first })
    }

    @Test
    fun `groupEditorBlocks falls back to Standalone when subBlock is blank`() {
        val block1 = BlockState(name = "Legacy Block 1", section = "Strength", subBlock = "")
        val block2 = BlockState(name = "Legacy Block 2", section = "Strength", subBlock = "   ")
        val block3 = BlockState(name = "Triset 1", section = "Accessories", subBlock = "Triset A")
        val block4 = BlockState(name = "Triset 2", section = "Accessories", subBlock = "Triset A")

        val items = groupEditorBlocks(listOf(block1, block2, block3, block4))

        assertEquals(3, items.size)
        assertTrue(items[0] is EditorBlockItem.Standalone)
        assertEquals(0, (items[0] as EditorBlockItem.Standalone).index)
        assertEquals("Legacy Block 1", (items[0] as EditorBlockItem.Standalone).block.name)

        assertTrue(items[1] is EditorBlockItem.Standalone)
        assertEquals(1, (items[1] as EditorBlockItem.Standalone).index)
        assertEquals("Legacy Block 2", (items[1] as EditorBlockItem.Standalone).block.name)

        assertTrue(items[2] is EditorBlockItem.SubBlockGroup)
        val group = items[2] as EditorBlockItem.SubBlockGroup
        assertEquals("Triset A", group.subBlockName)
        assertEquals(2, group.items.size)
    }

    @Test
    fun `groupEditorBlocks reconciles asymmetric rounds using maxOf sets size`() {
        val block1 = BlockState(
            name = "RDL",
            section = "Accessories",
            subBlock = "E3MOM Trisets",
            sets = listOf(SetState("8", "60"), SetState("8", "60"), SetState("8", "60"), SetState("8", "60"))
        )
        val block2 = BlockState(
            name = "Pullups",
            section = "Accessories",
            subBlock = "E3MOM Trisets",
            sets = listOf(SetState("6", "0"), SetState("5", "0")) // 2 sets logged
        )
        val block3 = BlockState(
            name = "DB Twist Curl",
            section = "Accessories",
            subBlock = "E3MOM Trisets",
            sets = listOf(SetState("12", "14"), SetState("12", "14"), SetState("10", "14")) // 3 sets logged
        )

        val items = groupEditorBlocks(listOf(block1, block2, block3))
        assertEquals(1, items.size)
        val group = items[0] as EditorBlockItem.SubBlockGroup
        assertEquals(3, group.items.size)
        assertEquals(4, group.totalRounds) // maxOf(4, 2, 3) = 4
    }

    @Test
    fun `broken triset resilience leaves unmatched sibling movement as standalone card`() {
        val block1 = BlockState(name = "RDL", section = "Accessories", subBlock = "E3MOM Trisets")
        val block2 = BlockState(name = "Pullups", section = "Accessories", subBlock = "") // Blank or unmatched
        val block3 = BlockState(name = "DB Twist Curl", section = "Accessories", subBlock = "E3MOM Trisets")

        val items = groupEditorBlocks(listOf(block1, block2, block3))
        // Due to contiguity invariant:
        // item 0: SubBlockGroup("E3MOM Trisets") with block 0
        // item 1: Standalone with block 1
        // item 2: SubBlockGroup("E3MOM Trisets") with block 2
        assertEquals(3, items.size)
        assertTrue(items[0] is EditorBlockItem.SubBlockGroup)
        assertTrue(items[1] is EditorBlockItem.Standalone)
        assertTrue(items[2] is EditorBlockItem.SubBlockGroup)
    }

    @Test
    fun `toDraftOrNull and buildBlockState preserve subBlock and section on persistence path`() {
        val seed = BlockSeed(
            name = "Front Squats",
            kind = BlockKind.STRENGTH,
            format = "E3MOM",
            scheme = "4x4",
            section = "Strength & Power",
            subBlock = "E3MOM Front Squats",
            sets = listOf(SetSeed(reps = "4", value = "75.0"))
        )

        val state = buildBlockState(seed, listOf(barbellExercise), emptyList<Routine>())
        assertEquals("Strength & Power", state.section)
        assertEquals("E3MOM Front Squats", state.subBlock)

        val draft = state.toDraftOrNull()
        org.junit.Assert.assertNotNull(draft)
        assertEquals("Strength & Power", draft!!.section)
        assertEquals("E3MOM Front Squats", draft.subBlock)
    }
}
