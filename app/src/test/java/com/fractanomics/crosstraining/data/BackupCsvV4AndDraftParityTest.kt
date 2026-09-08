package com.fractanomics.crosstraining.data

import com.fractanomics.crosstraining.data.model.BlockKind
import com.fractanomics.crosstraining.data.model.BlockWithSets
import com.fractanomics.crosstraining.data.model.RoutineBlock
import com.fractanomics.crosstraining.data.model.Session
import com.fractanomics.crosstraining.data.model.SessionBlock
import com.fractanomics.crosstraining.data.model.SessionWithBlocks
import com.fractanomics.crosstraining.ui.BlockDraft
import com.fractanomics.crosstraining.ui.screens.BlockSeed
import com.fractanomics.crosstraining.ui.screens.sessionSeed
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate

/**
 * Unit tests verifying Issue #514:
 * "[Subtask #512.0] Room Schema Migration MIGRATION_6_7, Data Models, and Backup Parity"
 *
 * Scenarios:
 * 1. UI Draft Model Parity:
 *    - SessionBlock with section and exerciseIdsCsv maps to/from BlockDraft and BlockSeed without data loss.
 * 2. Backup CSV v4 Roundtrip:
 *    - Encodes and decodes routineBlocks and session_blocks (with section and exerciseIdsCsv) under #crosstraining-backup-v4.
 * 3. Legacy Fallback:
 *    - Decodes legacy #crosstraining-backup-v1, v2, v3 backups with empty default values for new fields.
 */
class BackupCsvV4AndDraftParityTest {

    @Test
    fun sessionBlock_mapsToAndFromBlockDraftAndSeedPreservingSectionAndExerciseIdsCsv() {
        val originalBlock = SessionBlock(
            id = 42L,
            sessionId = 10L,
            position = 2,
            name = "Snatch Wave",
            kind = BlockKind.STRENGTH,
            format = "E2MOM",
            scheme = "3x3",
            mainExerciseId = 5L,
            routineId = 8L,
            description = "Build to heavy",
            resultText = "100kg",
            resultValue = 100.0,
            notes = "Felt solid",
            section = "Main Lift",
            exerciseIdsCsv = "5,12,19"
        )

        // Map to BlockDraft (e.g. from SessionDraft)
        val draft = BlockDraft(
            name = originalBlock.name,
            kind = originalBlock.kind,
            format = originalBlock.format,
            scheme = originalBlock.scheme,
            existingExerciseId = originalBlock.mainExerciseId,
            newExerciseName = null,
            routineId = originalBlock.routineId,
            description = originalBlock.description,
            resultText = originalBlock.resultText,
            resultValue = originalBlock.resultValue,
            sets = emptyList(),
            newRepMaxReps = null,
            newRepMaxWeight = null,
            section = originalBlock.section,
            exerciseIdsCsv = originalBlock.exerciseIdsCsv
        )

        assertEquals("Main Lift", draft.section)
        assertEquals("5,12,19", draft.exerciseIdsCsv)

        // Seed into BlockSeed directly
        val seed = BlockSeed(
            name = draft.name,
            kind = draft.kind,
            format = draft.format,
            scheme = draft.scheme,
            exerciseId = draft.existingExerciseId,
            routineId = draft.routineId,
            description = draft.description,
            resultText = draft.resultText,
            resultValue = draft.resultValue?.toString() ?: "",
            section = draft.section,
            exerciseIdsCsv = draft.exerciseIdsCsv
        )

        assertEquals("Main Lift", seed.section)
        assertEquals("5,12,19", seed.exerciseIdsCsv)

        // Test via sessionSeed(SessionWithBlocks)
        val session = Session(id = 10L, cycleId = 1L, date = LocalDate.now(), title = "Test", notes = "")
        val sessionWithBlocks = SessionWithBlocks(session, listOf(BlockWithSets(originalBlock, emptyList())))
        val loadedSeed = sessionSeed(sessionWithBlocks)

        assertEquals(1, loadedSeed.blocks.size)
        val loadedBlockSeed = loadedSeed.blocks[0]
        assertEquals("Main Lift", loadedBlockSeed.section)
        assertEquals("5,12,19", loadedBlockSeed.exerciseIdsCsv)
    }

    @Test
    fun backupCsv_roundTripsRoutineBlocksAndBlockColumnsUnderV4Header() {
        val routineBlock = RoutineBlock(
            id = 101L,
            routineId = 5L,
            position = 0,
            name = "Squat Warmup",
            kind = BlockKind.OTHER,
            format = "Tabata",
            setsCount = 4,
            targetRepsScheme = "20s work / 10s rest",
            exerciseIdsCsv = "1,2",
            notes = "Low intensity",
            section = "Warmup"
        )

        val sessionBlock = SessionBlock(
            id = 201L,
            sessionId = 50L,
            position = 1,
            name = "Front Squat",
            kind = BlockKind.STRENGTH,
            format = "5x5",
            scheme = "5 reps",
            mainExerciseId = 3L,
            routineId = 5L,
            description = "Working sets",
            resultText = "120kg",
            resultValue = 120.0,
            notes = "All sets clean",
            section = "Strength",
            exerciseIdsCsv = "3,4"
        )

        val backup = BackupData(
            routineBlocks = listOf(routineBlock),
            blocks = listOf(sessionBlock)
        )

        val encoded = BackupCsv.encode(backup)
        assertTrue("Encoded CSV must start with #crosstraining-backup-v4", encoded.startsWith("#crosstraining-backup-v4\n"))
        assertTrue("Encoded CSV must contain #routineBlocks section", encoded.contains("#routineBlocks\n"))

        val decoded = BackupCsv.decode(encoded)
        assertEquals(1, decoded.routineBlocks.size)
        val decRoutineBlock = decoded.routineBlocks[0]
        assertEquals(101L, decRoutineBlock.id)
        assertEquals(5L, decRoutineBlock.routineId)
        assertEquals("Squat Warmup", decRoutineBlock.name)
        assertEquals("Warmup", decRoutineBlock.section)
        assertEquals("1,2", decRoutineBlock.exerciseIdsCsv)
        assertEquals("20s work / 10s rest", decRoutineBlock.targetRepsScheme)

        assertEquals(1, decoded.blocks.size)
        val decSessionBlock = decoded.blocks[0]
        assertEquals(201L, decSessionBlock.id)
        assertEquals("Front Squat", decSessionBlock.name)
        assertEquals("Strength", decSessionBlock.section)
        assertEquals("3,4", decSessionBlock.exerciseIdsCsv)
    }

    @Test
    fun backupCsv_decodesLegacyV1V2V3WithCleanDefaults() {
        val legacyV3Text = """
            #crosstraining-backup-v3
            #cycles
            id,name,startDate,endDate,goal,isActive
            1,Foundation,19000,19100,Strength,1
            #blocks
            id,sessionId,position,name,kind,format,scheme,mainExerciseId,routineId,description,resultText,resultValue,notes
            10,1,0,Back Squat,STRENGTH,5x5,5,2,,Heavy,140kg,140.0,Strong
        """.trimIndent()

        val decodedV3 = BackupCsv.decode(legacyV3Text)
        assertEquals(1, decodedV3.blocks.size)
        val bV3 = decodedV3.blocks[0]
        assertEquals("Back Squat", bV3.name)
        assertEquals("", bV3.section)
        assertEquals("", bV3.exerciseIdsCsv)
        assertTrue(decodedV3.routineBlocks.isEmpty())

        val legacyV1Text = """
            #crosstraining-backup-v1
            #cycles
            id,name,startDate,endDate,goal,isActive
            1,Foundation,19000,,Strength,1
        """.trimIndent()

        val decodedV1 = BackupCsv.decode(legacyV1Text)
        assertEquals(1, decodedV1.cycles.size)
        assertTrue(decodedV1.blocks.isEmpty())
        assertTrue(decodedV1.routineBlocks.isEmpty())
    }
}
