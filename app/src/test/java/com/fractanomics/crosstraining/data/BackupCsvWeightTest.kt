package com.fractanomics.crosstraining.data

import com.fractanomics.crosstraining.data.model.WeightEntry
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate

/**
 * Unit tests verifying BackupData, BackupCsv v2/v3, and DemoData weight history.
 *
 * Scenarios:
 * 1. BackupCsv encodes and decodes #weightEntries section under #crosstraining-backup-v3 without data loss.
 * 2. BackupCsv v2 backwards compatibility: decodes legacy v2 backup text without weightEntries cleanly.
 * 3. DemoData.SEED_VERSION is bumped to 4 and generates 30 days of realistic weight data.
 */
class BackupCsvWeightTest {

    @Test
    fun backupCsv_roundTripsWeightEntriesWithV3Header() {
        val today = LocalDate.of(2026, 9, 7)
        val activeEntry = WeightEntry(
            date = today,
            weightKg = 78.4,
            notes = "Morning weigh-in",
            updatedAtMillis = 1725700000000L,
            deletedAtMillis = null
        )
        val tombstoneEntry = WeightEntry(
            date = today.minusDays(1),
            weightKg = 78.9,
            notes = "Deleted yesterday",
            updatedAtMillis = 1725600000000L,
            deletedAtMillis = 1725610000000L
        )

        val backup = BackupData(weightEntries = listOf(activeEntry, tombstoneEntry))
        val encoded = BackupCsv.encode(backup)

        assertTrue("Encoded CSV must have v3 header", encoded.startsWith("#crosstraining-backup-v3"))
        assertTrue("Encoded CSV must contain #weightEntries section", encoded.contains("#weightEntries\n"))

        val decoded = BackupCsv.decode(encoded)
        assertEquals("Must decode exactly 2 weight entries", 2, decoded.weightEntries.size)

        val decodedActive = decoded.weightEntries.first { it.date == today }
        assertEquals(78.4, decodedActive.weightKg, 0.001)
        assertEquals("Morning weigh-in", decodedActive.notes)
        assertEquals(1725700000000L, decodedActive.updatedAtMillis)
        assertNull(decodedActive.deletedAtMillis)

        val decodedTombstone = decoded.weightEntries.first { it.date == today.minusDays(1) }
        assertEquals(78.9, decodedTombstone.weightKg, 0.001)
        assertEquals("Deleted yesterday", decodedTombstone.notes)
        assertEquals(1725600000000L, decodedTombstone.updatedAtMillis)
        assertEquals(1725610000000L, decodedTombstone.deletedAtMillis)
    }

    @Test
    fun backupCsv_decodesLegacyV2TextWithoutWeightEntriesCleanly() {
        val legacyV2Text = """
            #crosstraining-backup-v2
            #cycles
            id,name,startDate,endDate,goal,isActive
            1,Foundation,19000,19100,Strength,1
            #exercises
            id,name,category,metricType,unit,tracksRepMax,notes
            1,Snatch,BARBELL,WEIGHT,kg,1,Olympic lift
        """.trimIndent()

        val decoded = BackupCsv.decode(legacyV2Text)
        assertEquals(1, decoded.cycles.size)
        assertEquals("Foundation", decoded.cycles[0].name)
        assertEquals(1, decoded.exercises.size)
        assertEquals("Snatch", decoded.exercises[0].name)
        assertTrue("Weight entries should be empty on legacy v2 backups", decoded.weightEntries.isEmpty())
    }

    @Test
    fun demoData_hasSeedVersion4AndPopulates30DaysOfWeightHistory() {
        assertEquals("DemoData.SEED_VERSION must be 4", 4, DemoData.SEED_VERSION)

        val today = LocalDate.of(2026, 9, 7)
        val snapshot = DemoData.snapshot(today)

        assertNotNull(snapshot.weightEntries)
        assertEquals("Demo snapshot must contain 30 daily weight entries", 30, snapshot.weightEntries.size)

        // Verify sorted chronological range
        val sorted = snapshot.weightEntries.sortedBy { it.date }
        assertEquals(today.minusDays(29), sorted.first().date)
        assertEquals(today, sorted.last().date)

        // Check realistic weight range: between 75kg and 85kg
        sorted.forEach { entry ->
            assertTrue("Weight ${entry.weightKg} must be realistic human weight", entry.weightKg in 70.0..90.0)
            assertNull("Demo entries should not be tombstones", entry.deletedAtMillis)
        }
    }
}
