package com.fractanomics.crosstraining.data

import com.fractanomics.crosstraining.data.model.WeightEntry
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.time.LocalDate

/**
 * Unit & Integration tests verifying Repository weight operations and snapshot lifecycle:
 *
 * Scenarios:
 * 1. Scenario: In-Place Natural Primary Key Upsert
 *    Given a WeightEntry logged for date D with weight 78.4 kg
 *    When an athlete re-logs or updates date D with weight 78.1 kg
 *    Then WeightDao.upsert executes an in-place update keyed on date without surrogate key mutation
 *    And WeightDao.getAllActiveEntries returns the updated record 78.1 kg for date D
 *
 * 2. Scenario: Durable Soft Deletion and Tombstone Preservation
 *    Given an existing active WeightEntry for date D
 *    When WeightDao.markDeleted is called for date D with timestamp T
 *    Then deletedAtMillis and updatedAtMillis are both updated to T
 *    And the entry is excluded from WeightDao.getAllActiveEntries
 *    And WeightDao.getAllEntriesIncludingTombstones returns the entry with deletedAtMillis = T
 *
 * 3. Scenario: Snapshot and Backup CSV Lifecycle
 *    Given a BackupData snapshot containing active weight entries and tombstones
 *    When Repository.importSnapshot executes
 *    Then weight_entries table is cleared before inserting the snapshot entries
 *    And exportSnapshot produces identical records without data loss
 */
class RepositoryWeightSnapshotTest {

    private lateinit var fakeDb: FakeSampleAppDatabase
    private lateinit var transactionRunner: FakeTransactionRunner
    private lateinit var repository: Repository

    @Before
    fun setup() {
        fakeDb = FakeSampleAppDatabase()
        transactionRunner = FakeTransactionRunner(fakeDb)
        repository = Repository(
            db = fakeDb,
            transactionRunner = transactionRunner
        )
    }

    @Test
    fun saveWeightEntry_executesInPlaceUpsertKeyedOnNaturalDate() = runTest {
        val dateD = LocalDate.of(2026, 9, 7)

        // Given a WeightEntry logged for date D with weight 78.4 kg
        repository.saveWeightEntry(weightKg = 78.4, date = dateD, notes = "Morning")

        var activeEntries = repository.weightEntries.first()
        assertEquals(1, activeEntries.size)
        assertEquals(78.4, activeEntries[0].weightKg, 0.001)
        assertEquals("Morning", activeEntries[0].notes)

        // When an athlete re-logs or updates date D with weight 78.1 kg
        repository.saveWeightEntry(weightKg = 78.1, date = dateD, notes = "Evening update")

        // Then WeightDao.upsert executes an in-place update keyed on date without surrogate key mutation
        activeEntries = repository.weightEntries.first()
        assertEquals(1, activeEntries.size)
        assertEquals(dateD, activeEntries[0].date)
        assertEquals(78.1, activeEntries[0].weightKg, 0.001)
        assertEquals("Evening update", activeEntries[0].notes)
    }

    @Test
    fun deleteWeightEntry_marksDeletedAndPreservesTombstone() = runTest {
        val dateD = LocalDate.of(2026, 9, 7)
        repository.saveWeightEntry(weightKg = 78.4, date = dateD, notes = "Active")

        val timestampT = 1725712345000L
        // When WeightDao.markDeleted is called for date D with timestamp T
        repository.deleteWeightEntry(date = dateD, deletedAt = timestampT)

        // Then deletedAtMillis and updatedAtMillis are both updated to T
        // And the entry is excluded from WeightDao.getAllActiveEntries
        val activeEntries = repository.weightEntries.first()
        assertTrue("Active entries must exclude soft-deleted records", activeEntries.isEmpty())

        // And WeightDao.getAllEntriesIncludingTombstones returns the entry with deletedAtMillis = T
        val allEntries = repository.getAllWeightEntriesIncludingTombstones()
        assertEquals(1, allEntries.size)
        val tombstone = allEntries[0]
        assertEquals(dateD, tombstone.date)
        assertEquals(timestampT, tombstone.deletedAtMillis)
        assertEquals(timestampT, tombstone.updatedAtMillis)
    }

    @Test
    fun importSnapshot_clearsWeightEntriesBeforeInsertingSnapshotData() = runTest {
        val date1 = LocalDate.of(2026, 9, 5)
        val date2 = LocalDate.of(2026, 9, 6)
        val date3 = LocalDate.of(2026, 9, 7)

        // Pre-existing local weight entry
        repository.saveWeightEntry(weightKg = 80.0, date = date1)
        assertEquals(1, repository.weightEntries.first().size)

        // Snapshot to import has date2 (active) and date3 (tombstone)
        val snapshotEntries = listOf(
            WeightEntry(date = date2, weightKg = 78.5, notes = "Restored", updatedAtMillis = 1000L, deletedAtMillis = null),
            WeightEntry(date = date3, weightKg = 78.2, notes = "Tombstone", updatedAtMillis = 2000L, deletedAtMillis = 2000L)
        )
        val snapshot = BackupData(weightEntries = snapshotEntries)

        // When Repository.importSnapshot executes
        repository.importSnapshot(snapshot)

        // Then weight_entries table is cleared before inserting snapshot entries
        val activeEntries = repository.weightEntries.first()
        assertEquals(1, activeEntries.size)
        assertEquals(date2, activeEntries[0].date)
        assertEquals(78.5, activeEntries[0].weightKg, 0.001)

        // And exportSnapshot captures all imported records including tombstones
        val exported = repository.exportSnapshot()
        assertEquals(2, exported.weightEntries.size)
        assertTrue(exported.weightEntries.any { it.date == date2 && it.deletedAtMillis == null })
        assertTrue(exported.weightEntries.any { it.date == date3 && it.deletedAtMillis == 2000L })
    }
}
