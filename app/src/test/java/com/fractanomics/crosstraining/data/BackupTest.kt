package com.fractanomics.crosstraining.data

import com.fractanomics.crosstraining.data.model.Cycle
import com.fractanomics.crosstraining.data.model.CycleType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate

/**
 * Unit tests verifying backup export and restore parity for Issue #578:
 * [Subtask #574.4] Reactive Baseline Sync Engine & Backup/Restore Parity.
 *
 * Scenarios:
 * 1. Round-trip backup test verifying type == FAT_LOSS_BODYBUILDING, startingWeightKg,
 *    targetWeightKg, and isBaselineAutoDerived survive serialization without data loss.
 * 2. Legacy 6-column restore test verifying tolerant fallback where cycles restore with
 *    type = STRENGTH_WEIGHTLIFTING, startingWeightKg = null, targetWeightKg = null,
 *    and isBaselineAutoDerived = false.
 * 3. Tolerant parsing of FAT_LOSS alias in the type column restoring as FAT_LOSS_BODYBUILDING.
 * 4. Round-trip backup test verifying standard strength cycles with null weight fields.
 */
class BackupTest {

    @Test
    fun roundTrip_fatLossCycle_preservesTypeAndWeightFieldsAndAutoDerived() {
        val today = LocalDate.of(2026, 10, 10)
        val cycle = Cycle(
            id = 42L,
            name = "Winter Fat Loss",
            startDate = today,
            endDate = today.plusDays(60),
            goal = "Cut 7kg",
            isActive = true,
            type = CycleType.FAT_LOSS_BODYBUILDING,
            startingWeightKg = 85.0,
            targetWeightKg = 78.0,
            isBaselineAutoDerived = true
        )

        val backup = BackupData(cycles = listOf(cycle))
        val encoded = BackupCsv.encode(backup)

        assertTrue("Encoded CSV must contain #cycles section", encoded.contains("#cycles\n"))
        assertTrue("Encoded CSV header must contain new columns", encoded.contains("type,startingWeightKg,targetWeightKg,isBaselineAutoDerived"))

        val decoded = BackupCsv.decode(encoded)
        assertEquals("Must decode exactly 1 cycle", 1, decoded.cycles.size)

        val restored = decoded.cycles[0]
        assertEquals(42L, restored.id)
        assertEquals("Winter Fat Loss", restored.name)
        assertEquals(today, restored.startDate)
        assertEquals(today.plusDays(60), restored.endDate)
        assertEquals("Cut 7kg", restored.goal)
        assertTrue("isActive must be preserved", restored.isActive)
        assertEquals(CycleType.FAT_LOSS_BODYBUILDING, restored.type)
        assertEquals(85.0, restored.startingWeightKg!!, 0.001)
        assertEquals(78.0, restored.targetWeightKg!!, 0.001)
        assertTrue("isBaselineAutoDerived must be true", restored.isBaselineAutoDerived)
    }

    @Test
    fun restore_legacy6ColumnCycles_defaultsWeightFieldsAndStrengthTypeCleanly() {
        val legacyCsv = """
            #crosstraining-backup-v5
            #cycles
            id,name,startDate,endDate,goal,isActive
            1,Foundation Strength,19000,19060,Build Base,1
            2,Completed Strength,18900,18960,Old Cycle,0
        """.trimIndent()

        val decoded = BackupCsv.decode(legacyCsv)
        assertEquals("Must decode 2 cycles", 2, decoded.cycles.size)

        val cycle1 = decoded.cycles[0]
        assertEquals(1L, cycle1.id)
        assertEquals("Foundation Strength", cycle1.name)
        assertTrue(cycle1.isActive)
        assertEquals("Legacy cycles must default to STRENGTH_WEIGHTLIFTING", CycleType.STRENGTH_WEIGHTLIFTING, cycle1.type)
        assertNull("startingWeightKg must be null for legacy cycles", cycle1.startingWeightKg)
        assertNull("targetWeightKg must be null for legacy cycles", cycle1.targetWeightKg)
        assertFalse("isBaselineAutoDerived must be false for legacy cycles", cycle1.isBaselineAutoDerived)

        val cycle2 = decoded.cycles[1]
        assertEquals(2L, cycle2.id)
        assertEquals("Completed Strength", cycle2.name)
        assertFalse(cycle2.isActive)
        assertEquals(CycleType.STRENGTH_WEIGHTLIFTING, cycle2.type)
        assertNull(cycle2.startingWeightKg)
        assertNull(cycle2.targetWeightKg)
        assertFalse(cycle2.isBaselineAutoDerived)
    }

    @Test
    fun restore_fatLossAliasInTypeColumn_resolvesFatLossBodybuilding() {
        val csv = """
            #crosstraining-backup-v5
            #cycles
            id,name,startDate,endDate,goal,isActive,type,startingWeightKg,targetWeightKg,isBaselineAutoDerived
            10,Summer Cut,19000,19060,Cut,1,FAT_LOSS,82.5,76.0,1
        """.trimIndent()

        val decoded = BackupCsv.decode(csv)
        assertEquals(1, decoded.cycles.size)

        val cycle = decoded.cycles[0]
        assertEquals(10L, cycle.id)
        assertEquals("Summer Cut", cycle.name)
        assertEquals("FAT_LOSS alias must resolve to FAT_LOSS_BODYBUILDING", CycleType.FAT_LOSS_BODYBUILDING, cycle.type)
        assertEquals(82.5, cycle.startingWeightKg!!, 0.001)
        assertEquals(76.0, cycle.targetWeightKg!!, 0.001)
        assertTrue(cycle.isBaselineAutoDerived)
    }

    @Test
    fun roundTrip_strengthCycleWithNullWeightFields_preservesNullsCleanly() {
        val cycle = Cycle(
            id = 99L,
            name = "Powerlifting Block",
            startDate = LocalDate.of(2026, 8, 1),
            endDate = null,
            goal = "Max strength",
            isActive = false,
            type = CycleType.STRENGTH_WEIGHTLIFTING,
            startingWeightKg = null,
            targetWeightKg = null,
            isBaselineAutoDerived = false
        )

        val backup = BackupData(cycles = listOf(cycle))
        val encoded = BackupCsv.encode(backup)
        val decoded = BackupCsv.decode(encoded)

        assertEquals(1, decoded.cycles.size)
        val restored = decoded.cycles[0]
        assertEquals(CycleType.STRENGTH_WEIGHTLIFTING, restored.type)
        assertNull(restored.startingWeightKg)
        assertNull(restored.targetWeightKg)
        assertFalse(restored.isBaselineAutoDerived)
    }
}
