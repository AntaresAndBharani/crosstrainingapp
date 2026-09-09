package com.fractanomics.crosstraining.util

import com.fractanomics.crosstraining.data.model.BlockKind
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Unit test suite verifying [WorkoutDocumentParser] adhering to Issue #515 acceptance criteria:
 * - Scenario 1: Digit-Bounded European Decimal Normalization
 * - Scenario 2: Shorthand Reps and Failure Grammar Parsing
 * - Scenario 3: Macro-Block and Triset Cluster Parsing
 * - Edge Cases: Comma in notes/lists, complex decompositions, and real-world workout note formats.
 */
class WorkoutDocumentParserTest {

    @Test
    fun `Scenario 1 - Digit-Bounded European Decimal Normalization`() {
        // Given raw workout text with "52,5 55 57,5" and note "Squat, Clean & Jerk: 50,5 kg, felt heavy"
        val rawInput = "52,5 55 57,5\nSquat, Clean & Jerk: 50,5 kg, felt heavy"

        // When WorkoutDocumentParser tokenizes and normalizes the input
        val normalized = WorkoutDocumentParser.normalizeDecimalCommas(rawInput)

        // Then textual commas in notes and lists are completely preserved
        assertTrue(normalized.contains("Squat, Clean & Jerk:"))
        assertTrue(normalized.contains("kg, felt heavy"))

        // And the loads are parsed as 52.5, 55.0, 57.5, and 50.5
        val sets1 = WorkoutDocumentParser.parseSetsString("52,5 55 57,5")
        assertEquals(3, sets1.size)
        assertEquals(52.5, sets1[0].weight ?: 0.0, 0.001)
        assertEquals(55.0, sets1[1].weight ?: 0.0, 0.001)
        assertEquals(57.5, sets1[2].weight ?: 0.0, 0.001)

        val sets2 = WorkoutDocumentParser.parseSetsString("50,5")
        assertEquals(1, sets2.size)
        assertEquals(50.5, sets2[0].weight ?: 0.0, 0.001)
    }

    @Test
    fun `Scenario 2 - Shorthand Reps and Failure Grammar Parsing`() {
        // Given a block with set string "57,5 60 60(1 rep) 60(fail) not_done"
        val setString = "57,5 60 60(1 rep) 60(fail) not_done"

        // When WorkoutDocumentParser parses the sets (target reps = 4)
        val sets = WorkoutDocumentParser.parseSetsString(setString, defaultTargetReps = 4)

        // Then Set 1 is 57.5 kg with target reps
        assertEquals(4, sets.size)
        assertEquals(57.5, sets[0].weight ?: 0.0, 0.001)
        assertEquals(4, sets[0].reps)
        assertFalse(sets[0].isFailed)

        // And Set 2 is 60.0 kg with target reps
        assertEquals(60.0, sets[1].weight ?: 0.0, 0.001)
        assertEquals(4, sets[1].reps)
        assertFalse(sets[1].isFailed)

        // And Set 3 is 60.0 kg with reps = 1 and notes = "Partial (target 4)"
        assertEquals(60.0, sets[2].weight ?: 0.0, 0.001)
        assertEquals(1, sets[2].reps)
        assertEquals("Partial (target 4)", sets[2].notes)
        assertFalse(sets[2].isFailed)

        // And Set 4 is 60.0 kg with reps = 0 and isFailed = true
        assertEquals(60.0, sets[3].weight ?: 0.0, 0.001)
        assertEquals(0, sets[3].reps)
        assertTrue(sets[3].isFailed)

        // And "not_done" is skipped entirely resulting in exactly 4 sets
    }

    @Test
    fun `shorthand reps with zero weight notation`() {
        // E.g. athlete logs bodyweight pullups or dips with 0kg: "0(5 reps) 0(4) 0(4) 0(4)"
        val sets = WorkoutDocumentParser.parseSetsString("0(5 reps) 0(4) 0(4) 0(4)", defaultTargetReps = 5)
        assertEquals(4, sets.size)

        assertEquals(0.0, sets[0].weight ?: -1.0, 0.001)
        assertEquals(5, sets[0].reps)

        assertEquals(0.0, sets[1].weight ?: -1.0, 0.001)
        assertEquals(4, sets[1].reps)
        assertEquals("Partial (target 5)", sets[1].notes)

        assertEquals(0.0, sets[2].weight ?: -1.0, 0.001)
        assertEquals(4, sets[2].reps)

        assertEquals(0.0, sets[3].weight ?: -1.0, 0.001)
        assertEquals(4, sets[3].reps)
    }

    @Test
    fun `Scenario 3 - Macro-Block and Triset Cluster Parsing`() {
        val documentText = """
            Mondays --- repeateble

            Strengh & Power block:
            E3MOM COMPLEX: Clean + Hang Clean + Front Squat + Push to OverHead
            52,5 55 55 55 55 55 57,5

            Accessories block:
            E3MOM Trisets
            Romanian Deadlift
            70 75 80 80
            Pullups
            0(5 reps) 0(4) 0(4) 0(4)
            DB Twist Curl
            12,5 15 15 15
        """.trimIndent()

        // When WorkoutDocumentParser parses the document
        val parsedDoc = WorkoutDocumentParser.parseDocument(documentText)

        // Then blocks are tagged with their corresponding section
        assertEquals("Mondays", parsedDoc.routineTitle)
        assertTrue(parsedDoc.isRepeatable)
        assertEquals(listOf("Strengh & Power block", "Accessories block"), parsedDoc.sections)

        assertEquals(4, parsedDoc.blocks.size)

        val complexBlock = parsedDoc.blocks[0]
        assertEquals("Clean + Hang Clean + Front Squat + Push to OverHead", complexBlock.name)
        assertEquals("Strengh & Power block", complexBlock.section)
        assertEquals(BlockKind.COMPLEX, complexBlock.kind)
        assertEquals("E3MOM", complexBlock.format)
        assertEquals(7, complexBlock.sets.size)
        assertEquals(52.5, complexBlock.sets[0].weight ?: 0.0, 0.001)
        assertEquals(57.5, complexBlock.sets[6].weight ?: 0.0, 0.001)
        assertEquals(listOf("Clean", "Hang Clean", "Front Squat", "Push to OverHead"), complexBlock.movements)

        // And triset exercises are linked via cluster scheme "TRISET_1" and share format "E3MOM"
        val trisetBlocks = parsedDoc.blocks.subList(1, 4)
        for (b in trisetBlocks) {
            assertEquals("Accessories block", b.section)
            assertEquals("TRISET_1", b.scheme)
            assertEquals("E3MOM", b.format)
            assertEquals(BlockKind.SUPERSET, b.kind)
            assertEquals(4, b.sets.size)
        }

        assertEquals("Romanian Deadlift", trisetBlocks[0].name)
        assertEquals(70.0, trisetBlocks[0].sets[0].weight ?: 0.0, 0.001)
        assertEquals(80.0, trisetBlocks[0].sets[3].weight ?: 0.0, 0.001)

        assertEquals("Pullups", trisetBlocks[1].name)
        assertEquals(5, trisetBlocks[1].sets[0].reps)
        assertEquals(4, trisetBlocks[1].sets[1].reps)

        assertEquals("DB Twist Curl", trisetBlocks[2].name)
        assertEquals(12.5, trisetBlocks[2].sets[0].weight ?: 0.0, 0.001)
        assertEquals(15.0, trisetBlocks[2].sets[1].weight ?: 0.0, 0.001)
    }

    @Test
    fun `triset round count inheritance for unnumbered accessories`() {
        val documentText = """
            Accessories block:
            E2,5MOM Trisets
            Barbell Calves Raises x15:
            60 70 70 70
            10 Cal SkiErg (tracking not needed)
            Banded Reverse Flys x15 (weight track not needed)
        """.trimIndent()

        val parsedDoc = WorkoutDocumentParser.parseDocument(documentText)
        assertEquals(3, parsedDoc.blocks.size)

        val anchorBlock = parsedDoc.blocks[0]
        assertEquals("Barbell Calves Raises", anchorBlock.name)
        assertEquals("E2.5MOM", anchorBlock.format)
        assertEquals(4, anchorBlock.sets.size)
        assertEquals(15, anchorBlock.targetReps)

        val skiErgBlock = parsedDoc.blocks[1]
        assertEquals("SkiErg", skiErgBlock.name)
        assertEquals("E2.5MOM", skiErgBlock.format)
        assertEquals("TRISET_1", skiErgBlock.scheme)
        // Unnumbered accessories inherit round count from anchor movement (4 rounds)
        assertEquals(4, skiErgBlock.sets.size)
        assertEquals(10.0, skiErgBlock.sets[0].metricValue ?: 0.0, 0.001)

        val flysBlock = parsedDoc.blocks[2]
        assertEquals("Banded Reverse Flys", flysBlock.name)
        assertEquals("E2.5MOM", flysBlock.format)
        assertEquals("TRISET_1", flysBlock.scheme)
        assertEquals(4, flysBlock.sets.size)
        assertEquals(15, flysBlock.sets[0].reps)
    }

    @Test
    fun `parse block with target reps prefix e g 4 Front Squats`() {
        val block = WorkoutDocumentParser.parseSingleBlock(
            blockLine = "E3MOM 4 Front Squats",
            setsLine = "57,5 60 60(1 rep) 60(fail) not_done",
            section = "Strength block"
        )

        assertEquals("Front Squats", block.name)
        assertEquals(4, block.targetReps)
        assertEquals("E3MOM", block.format)
        assertEquals("Strength block", block.section)
        assertEquals(4, block.sets.size)
        assertEquals(57.5, block.sets[0].weight ?: 0.0, 0.001)
        assertEquals(4, block.sets[0].reps)
        assertEquals(1, block.sets[2].reps)
        assertEquals("Partial (target 4)", block.sets[2].notes)
        assertTrue(block.sets[3].isFailed)
    }

    @Test
    fun `Issue 523 Scenario 1 - Multi-Line Sets Line Binding & Colon-Demarcated Non-Label Protection`() {
        val documentText = """
            E3MOM 4 Front Squats

            Sets(5) & Weight per set: 57,5 60 60(1 rep) 60(fail) not_done

            Back Squats: 100 100 100 100
        """.trimIndent()

        val parsedDoc = WorkoutDocumentParser.parseDocument(documentText)

        // Then exactly 2 distinct blocks are created: "Front Squats" and "Back Squats"
        assertEquals(2, parsedDoc.blocks.size)

        val frontSquats = parsedDoc.blocks[0]
        assertEquals("Front Squats", frontSquats.name)
        assertEquals(4, frontSquats.targetReps)
        assertEquals("E3MOM", frontSquats.format)
        assertEquals(4, frontSquats.sets.size)
        assertEquals(57.5, frontSquats.sets[0].weight ?: 0.0, 0.001)
        assertEquals(60.0, frontSquats.sets[1].weight ?: 0.0, 0.001)
        assertEquals(1, frontSquats.sets[2].reps)
        assertTrue(frontSquats.sets[3].isFailed)

        val backSquats = parsedDoc.blocks[1]
        assertEquals("Back Squats", backSquats.name)
        assertEquals(4, backSquats.sets.size)
        assertEquals(100.0, backSquats.sets[0].weight ?: 0.0, 0.001)
        assertEquals(100.0, backSquats.sets[3].weight ?: 0.0, 0.001)
    }

    @Test
    fun `Issue 523 Scenario 2 - Triset Cluster Boundary Enforcement`() {
        val documentText = """
            Accessories block:
            E3MOM Trisets:
                1- Romanian Deadlift x12 reps. Weights per set: 60 60 60 60
                2- Pullups x8 reps. Extra Weight per set: 0(5 reps) 0(4) 0(4) 0(4)
                3- DB Twist Curl x12 reps. Weight per set: 12,5 12,5 12,5 12,5

            Barbell Calves Raises x15: 60 60 60 60
        """.trimIndent()

        val parsedDoc = WorkoutDocumentParser.parseDocument(documentText)

        // Exactly 4 blocks: 3 in triset, 1 standalone
        assertEquals(4, parsedDoc.blocks.size)

        val rdl = parsedDoc.blocks[0]
        assertEquals("Romanian Deadlift", rdl.name)
        assertEquals(12, rdl.targetReps)
        assertEquals("TRISET_1", rdl.scheme)
        assertEquals("E3MOM", rdl.format)
        assertEquals(BlockKind.SUPERSET, rdl.kind)

        val pullups = parsedDoc.blocks[1]
        assertEquals("Pullups", pullups.name)
        assertEquals(8, pullups.targetReps)
        assertEquals("TRISET_1", pullups.scheme)
        assertEquals("E3MOM", pullups.format)
        assertEquals(BlockKind.SUPERSET, pullups.kind)

        val dbCurl = parsedDoc.blocks[2]
        assertEquals("DB Twist Curl", dbCurl.name)
        assertEquals(12, dbCurl.targetReps)
        assertEquals("TRISET_1", dbCurl.scheme)
        assertEquals("E3MOM", dbCurl.format)
        assertEquals(BlockKind.SUPERSET, dbCurl.kind)

        val calves = parsedDoc.blocks[3]
        assertEquals("Barbell Calves Raises", calves.name)
        assertEquals(15, calves.targetReps)
        assertEquals(BlockKind.ACCESSORY, calves.kind)
        assertEquals("", calves.format)
        assertFalse(calves.scheme.startsWith("TRISET"))
        assertEquals(4, calves.sets.size)
        assertEquals(60.0, calves.sets[0].weight ?: 0.0, 0.001)
    }

    @Test
    fun `Issue 523 Scenario 3 - Movement Name Cleansing and Compound Word Safety`() {
        // "1- Romanian Deadlift x12 reps. Weights per set: 60 60 60 60"
        val block1 = WorkoutDocumentParser.parseSingleBlock(
            blockLine = "1- Romanian Deadlift x12 reps. Weights per set: 60 60 60 60"
        )
        assertEquals("Romanian Deadlift", block1.name)
        assertEquals(12, block1.targetReps)
        assertEquals(4, block1.sets.size)

        // Compound word safety: "Bodyweight Pullups x10" must not truncate "Bodyweight" to "Body"
        val block2 = WorkoutDocumentParser.parseSingleBlock(
            blockLine = "Bodyweight Pullups x10"
        )
        assertEquals("Bodyweight Pullups", block2.name)
        assertEquals(10, block2.targetReps)

        val block3 = WorkoutDocumentParser.parseSingleBlock(
            blockLine = "3. Free weight Squats x8: 80 80 80"
        )
        assertEquals("Free weight Squats", block3.name)
        assertEquals(8, block3.targetReps)
        assertEquals(3, block3.sets.size)
    }

    @Test
    fun `Issue 523 Scenario 4 - Reps and Suffix Sanitization`() {
        val block = WorkoutDocumentParser.parseSingleBlock(
            blockLine = "Romanian Deadlift x12 reps. Weights per set: 60 60 60 60"
        )
        assertEquals(12, block.targetReps)
        assertEquals("Romanian Deadlift", block.name)
        assertEquals(4, block.sets.size)
        assertEquals(60.0, block.sets[0].weight ?: 0.0, 0.001)
    }

    @Test
    fun `performance test - parseDocument executes in sub-15ms`() {
        val largeDoc = buildString {
            appendLine("# Monday Full Programming --- repeatable")
            appendLine("Strengh & Power block:")
            appendLine("E3MOM COMPLEX: Clean + Hang Clean + Front Squat + Push to OverHead")
            appendLine("52,5 55 55 55 55 55 57,5")
            appendLine("E3MOM 4 Front Squats")
            appendLine("57,5 60 60(1 rep) 60(fail) not_done")
            appendLine("Accessories block:")
            appendLine("E3MOM Trisets")
            appendLine("Romanian Deadlift: 70 75 80 80")
            appendLine("Pullups: 0(5 reps) 0(4) 0(4) 0(4)")
            appendLine("DB Twist Curl: 12,5 15 15 15")
            appendLine("E2,5MOM Trisets")
            appendLine("Barbell Calves Raises x15: 60 70 70 70")
            appendLine("10 Cal SkiErg (tracking not needed)")
            appendLine("Banded Reverse Flys x15 (weight track not needed)")
        }

        // Warm up JIT
        for (i in 0 until 10) {
            WorkoutDocumentParser.parseDocument(largeDoc)
        }

        val startTime = System.nanoTime()
        val result = WorkoutDocumentParser.parseDocument(largeDoc)
        val elapsedMillis = (System.nanoTime() - startTime) / 1_000_000.0

        assertNotNull(result)
        assertEquals(8, result.blocks.size)
        assertTrue("Parsing must execute in < 15ms locally, actual: ${elapsedMillis}ms", elapsedMillis < 15.0)
    }
}
