package com.fractanomics.crosstraining.ui.screens

import com.fractanomics.crosstraining.data.model.BlockKind
import com.fractanomics.crosstraining.data.model.BlockWithSets
import com.fractanomics.crosstraining.data.model.Exercise
import com.fractanomics.crosstraining.data.model.ExerciseCategory
import com.fractanomics.crosstraining.data.model.MetricType
import com.fractanomics.crosstraining.data.model.Routine
import com.fractanomics.crosstraining.data.model.Session
import com.fractanomics.crosstraining.data.model.SessionBlock
import com.fractanomics.crosstraining.data.model.SessionWithBlocks
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate

/**
 * Unit tests for SessionEditor section grouping and preservation (Issue #517):
 * - Scenario 3: Macro-block section parity and visual grouping
 * - Section & exerciseIdsCsv preservation through BlockSeed and BlockDraft on edit & save
 */
class SessionEditorSectionParityTest {

    private val cleanExercise = Exercise(
        id = 10,
        name = "Clean",
        category = ExerciseCategory.BARBELL,
        metricType = MetricType.WEIGHT,
        unit = "kg",
        tracksRepMax = true
    )

    private val rdlExercise = Exercise(
        id = 20,
        name = "Romanian Deadlift",
        category = ExerciseCategory.BARBELL,
        metricType = MetricType.WEIGHT,
        unit = "kg",
        tracksRepMax = true
    )

    private val pullupsExercise = Exercise(
        id = 30,
        name = "Pullups",
        category = ExerciseCategory.GYMNASTICS,
        metricType = MetricType.REPS,
        unit = "reps",
        tracksRepMax = false
    )

    @Test
    fun `sessionSeed extracts section and exerciseIdsCsv from SessionWithBlocks correctly`() {
        val session = Session(
            id = 100L,
            cycleId = 1L,
            date = LocalDate.of(2026, 9, 8),
            title = "Monday: Strength & Accessories",
            notes = "Great workout"
        )

        val block1 = SessionBlock(
            id = 201L,
            sessionId = 100L,
            position = 0,
            name = "Clean Complex",
            kind = BlockKind.COMPLEX,
            format = "E3MOM",
            scheme = "4x1",
            mainExerciseId = 10L,
            section = "Strengh & Power block",
            exerciseIdsCsv = "10,11,12"
        )

        val block2 = SessionBlock(
            id = 202L,
            sessionId = 100L,
            position = 1,
            name = "Romanian Deadlift",
            kind = BlockKind.SUPERSET,
            format = "E3MOM",
            scheme = "TRISET_1",
            mainExerciseId = 20L,
            section = "Accessories block",
            exerciseIdsCsv = ""
        )

        val sessionWithBlocks = SessionWithBlocks(
            session = session,
            blocks = listOf(
                BlockWithSets(block = block1, sets = emptyList()),
                BlockWithSets(block = block2, sets = emptyList())
            )
        )

        val seed = sessionSeed(sessionWithBlocks)

        assertEquals(2, seed.blocks.size)
        assertEquals("Strengh & Power block", seed.blocks[0].section)
        assertEquals("10,11,12", seed.blocks[0].exerciseIdsCsv)
        assertEquals("Accessories block", seed.blocks[1].section)
        assertEquals("", seed.blocks[1].exerciseIdsCsv)
    }

    @Test
    fun `BlockState and toDraftOrNull preserve section and exerciseIdsCsv without metadata eviction`() {
        val seedBlock = BlockSeed(
            name = "Clean Complex",
            kind = BlockKind.COMPLEX,
            format = "E3MOM",
            scheme = "4x1",
            exerciseId = 10L,
            section = "Strengh & Power block",
            exerciseIdsCsv = "10,11,12",
            sets = listOf(
                SetSeed(reps = "1", value = "55.0", warm = false, failed = false)
            )
        )

        val exercises = listOf(cleanExercise, rdlExercise, pullupsExercise)
        val routines = emptyList<Routine>()

        // Simulate building block state
        val blockState = buildBlockState(seedBlock, exercises, routines)

        assertEquals("Strengh & Power block", blockState.section)
        assertEquals("10,11,12", blockState.exerciseIdsCsv)

        // Mutate some values in editor
        blockState.format = "E2.5MOM"

        // Map to draft for saving
        val draft = blockState.toDraftOrNull()
        assertNotNull(draft)
        assertEquals("Clean Complex", draft?.name)
        assertEquals("E2.5MOM", draft?.format)
        assertEquals("Strengh & Power block", draft?.section)
        assertEquals("10,11,12", draft?.exerciseIdsCsv)
    }

    @Test
    fun `grouping blocks by section maintains section boundaries for UI rendering`() {
        val blocks = listOf(
            BlockSeed(name = "Clean", section = "Strengh & Power block"),
            BlockSeed(name = "Front Squat", section = "Strengh & Power block"),
            BlockSeed(name = "RDL", section = "Accessories block"),
            BlockSeed(name = "Pullups", section = "Accessories block"),
            BlockSeed(name = "Core Work", section = "")
        )

        // Grouping logic used by SessionEditor UI
        val grouped = blocks.groupBy { it.section }

        assertEquals(3, grouped.size)
        assertTrue(grouped.containsKey("Strengh & Power block"))
        assertTrue(grouped.containsKey("Accessories block"))
        assertTrue(grouped.containsKey(""))

        assertEquals(2, grouped["Strengh & Power block"]?.size)
        assertEquals(2, grouped["Accessories block"]?.size)
        assertEquals(1, grouped[""]?.size)
    }
}
