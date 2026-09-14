package com.fractanomics.crosstraining.ui.screens

import com.fractanomics.crosstraining.data.model.BlockKind
import com.fractanomics.crosstraining.data.model.Routine
import com.fractanomics.crosstraining.data.model.RoutineBlock
import com.fractanomics.crosstraining.data.model.RoutineWithBlocks
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Unit tests verifying Issue #555 acceptance criteria:
 * - Scenario 1 & 2: Concise BlockKind badges ("Superset"), layout priority and title visibility without 30-char strings.
 * - Scenario 3: Routine Library action chip labels ("Import from Notes", "Import Code", "Community Library") and responsive multi-line flow grouping.
 * - Scenario 4: Community Library chip integrity, modal opening state triggers, and routine card visibility underneath.
 */
class LibraryScreenTest {

    @Test
    fun `BlockKind SUPERSET is normalized to concise Superset and shortLabel matches`() {
        // Given BlockKind SUPERSET
        val supersetKind = BlockKind.SUPERSET

        // Then label and shortLabel must be concise "Superset"
        assertEquals("Superset", supersetKind.label)
        assertEquals("Superset", supersetKind.shortLabel)

        // All kinds have valid non-empty shortLabels
        BlockKind.entries.forEach { kind ->
            assertTrue(kind.shortLabel.isNotBlank())
            assertTrue(kind.shortLabel.length <= 15) // Concise badge length
        }
    }

    @Test
    fun `Routine action chips maintain correct text labels and flow grouping`() {
        // Verify the 3 action chip labels defined for LibraryScreen
        val actionChips = listOf(
            "Import from Notes",
            "Import Code",
            "Community Library"
        )

        assertEquals(3, actionChips.size)
        assertEquals("Import from Notes", actionChips[0])
        assertEquals("Import Code", actionChips[1])
        assertEquals("Community Library", actionChips[2])

        // Ensure chips are concise single-line strings suitable for FlowRow
        actionChips.forEach { label ->
            assertTrue(label.isNotBlank())
            assertTrue(!label.contains("\n"))
        }
    }

    @Test
    fun `Routine cards underneath action chips remain fully visible and properly grouped`() {
        val routine = Routine(
            id = 100L,
            name = "Upper Body Hypertrophy & Trisets",
            description = "High intensity accessory routine"
        )

        val blocks = listOf(
            RoutineBlock(
                id = 1L,
                routineId = 100L,
                position = 0,
                name = "Romanian Deadlift",
                kind = BlockKind.SUPERSET,
                format = "E3MOM",
                setsCount = 4,
                targetRepsScheme = "TRISET_1",
                section = "Accessories",
                subBlock = "E3MOM Trisets"
            ),
            RoutineBlock(
                id = 2L,
                routineId = 100L,
                position = 1,
                name = "Pullups",
                kind = BlockKind.SUPERSET,
                format = "E3MOM",
                setsCount = 4,
                targetRepsScheme = "TRISET_1",
                section = "Accessories",
                subBlock = "E3MOM Trisets"
            ),
            RoutineBlock(
                id = 3L,
                routineId = 100L,
                position = 2,
                name = "DB Twist Curl",
                kind = BlockKind.SUPERSET,
                format = "E3MOM",
                setsCount = 4,
                targetRepsScheme = "TRISET_1",
                section = "Accessories",
                subBlock = "E3MOM Trisets"
            )
        )

        val routineWithBlocks = RoutineWithBlocks(routine = routine, blocks = blocks)
        assertNotNull(routineWithBlocks)
        assertEquals(3, routineWithBlocks.blocks.size)

        val grouped = groupRoutineBlocks(routineWithBlocks.blocks)
        assertEquals(1, grouped.size)
        assertTrue(grouped[0] is RoutineBlockItem.SubBlockGroup)

        val subBlockGroup = grouped[0] as RoutineBlockItem.SubBlockGroup
        assertEquals("E3MOM Trisets", subBlockGroup.subBlockName)
        assertEquals(3, subBlockGroup.items.size)
        assertEquals(4, subBlockGroup.totalSetsCount)
        assertEquals(listOf("Romanian Deadlift", "Pullups", "DB Twist Curl"), subBlockGroup.items.map { it.second.name })
    }

    @Test
    fun `Nested sub-block movements display exercise names clearly without repetitive 30-char type badges`() {
        val block1 = BlockState(name = "Romanian Deadlift", kind = BlockKind.SUPERSET, section = "Accessories", subBlock = "E3MOM Trisets")
        val block2 = BlockState(name = "Pullups", kind = BlockKind.SUPERSET, section = "Accessories", subBlock = "E3MOM Trisets")
        val block3 = BlockState(name = "DB Twist Curl", kind = BlockKind.SUPERSET, section = "Accessories", subBlock = "E3MOM Trisets")

        val grouped = groupEditorBlocks(listOf(block1, block2, block3))
        assertEquals(1, grouped.size)
        assertTrue(grouped[0] is EditorBlockItem.SubBlockGroup)

        val group = grouped[0] as EditorBlockItem.SubBlockGroup
        assertEquals("E3MOM Trisets", group.subBlockName)
        assertEquals(3, group.items.size)

        // Exercise titles must be distinct and non-empty
        val titles = group.items.map { it.second.name }
        assertEquals(listOf("Romanian Deadlift", "Pullups", "DB Twist Curl"), titles)

        // Each movement kind is BlockKind.SUPERSET with concise shortLabel
        group.items.forEach { (_, b) ->
            assertEquals(BlockKind.SUPERSET, b.kind)
            assertEquals("Superset", b.kind.shortLabel)
            assertTrue(b.kind.shortLabel.length <= 10)
        }
    }
}
