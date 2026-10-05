package com.fractanomics.crosstraining.data

import com.fractanomics.crosstraining.data.model.Cycle
import com.fractanomics.crosstraining.data.model.CycleType
import com.fractanomics.crosstraining.data.model.DailyLog
import com.fractanomics.crosstraining.data.model.SessionBlock
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
 * Unit & Integration tests verifying DailyLog Room DAO and Repository integration,
 * as well as entity defaults for Cycle and SessionBlock.
 */
class RepositoryDailyLogTest {

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
    fun cycleEntity_hasExpectedDefaults() {
        val cycle = Cycle(
            name = "Hypertrophy Phase",
            startDate = LocalDate.of(2026, 10, 1)
        )
        assertEquals(CycleType.STRENGTH_WEIGHTLIFTING, cycle.type)
        assertEquals(0, cycle.fastDaysOfWeek)
        assertEquals(0, cycle.restDaysOfWeek)
    }

    @Test
    fun sessionBlockEntity_hasIsCompletedDefaultTrue() {
        val block = SessionBlock(
            sessionId = 1L,
            position = 0,
            name = "Back Squats"
        )
        assertTrue(block.isCompleted)
    }

    @Test
    fun saveDailyLog_upsertsAndRetrievesByDate() = runTest {
        val date = LocalDate.of(2026, 10, 5)
        val log = DailyLog(
            date = date,
            fastCompleted = true,
            isRestDay = false,
            caloriesKcal = 2400,
            proteinGrams = 190,
            carbsGrams = 220,
            fatGrams = 70,
            notes = "Felt great today",
            updatedAtMillis = 1728100000000L
        )

        repository.saveDailyLog(log)

        val retrieved = repository.getDailyLogByDate(date)
        assertNotNull(retrieved)
        assertEquals(date, retrieved?.date)
        assertEquals(true, retrieved?.fastCompleted)
        assertEquals(false, retrieved?.isRestDay)
        assertEquals(2400, retrieved?.caloriesKcal)
        assertEquals(190, retrieved?.proteinGrams)
        assertEquals(220, retrieved?.carbsGrams)
        assertEquals(70, retrieved?.fatGrams)
        assertEquals("Felt great today", retrieved?.notes)

        // Test in-place upsert update
        val updated = log.copy(caloriesKcal = 2500, notes = "Updated notes")
        repository.saveDailyLog(updated)

        val afterUpdate = repository.getDailyLogByDate(date)
        assertEquals(2500, afterUpdate?.caloriesKcal)
        assertEquals("Updated notes", afterUpdate?.notes)
    }

    @Test
    fun dailyLogsFlow_observesOnlyActiveLogsExcludingTombstones() = runTest {
        val date1 = LocalDate.of(2026, 10, 1)
        val date2 = LocalDate.of(2026, 10, 2)

        repository.saveDailyLog(
            DailyLog(date = date1, fastCompleted = true, isRestDay = false, updatedAtMillis = 1000L)
        )
        repository.saveDailyLog(
            DailyLog(date = date2, fastCompleted = false, isRestDay = true, updatedAtMillis = 2000L)
        )

        val active = repository.dailyLogs.first()
        assertEquals(2, active.size)
        assertEquals(date2, active[0].date)
        assertEquals(date1, active[1].date)

        // Mark date1 as deleted
        repository.deleteDailyLog(date1, deletedAt = 3000L)

        val activeAfterDelete = repository.dailyLogs.first()
        assertEquals(1, activeAfterDelete.size)
        assertEquals(date2, activeAfterDelete[0].date)

        val allWithTombstones = repository.getAllDailyLogsIncludingTombstones()
        assertEquals(2, allWithTombstones.size)
        val tombstone = allWithTombstones.find { it.date == date1 }
        assertNotNull(tombstone)
        assertEquals(3000L, tombstone?.deletedAtMillis)
    }

    @Test
    fun purgeOldDailyLogTombstones_removesTombstonesOlderThanCutoff() = runTest {
        val date1 = LocalDate.of(2026, 10, 1)
        val date2 = LocalDate.of(2026, 10, 2)

        repository.saveDailyLog(DailyLog(date = date1, updatedAtMillis = 1000L))
        repository.saveDailyLog(DailyLog(date = date2, updatedAtMillis = 2000L))

        repository.deleteDailyLog(date1, deletedAt = 1000L)
        repository.deleteDailyLog(date2, deletedAt = 5000L)

        // Purge tombstones older than 3000L
        repository.purgeOldDailyLogTombstones(cutoffMillis = 3000L)

        val remaining = repository.getAllDailyLogsIncludingTombstones()
        assertEquals(1, remaining.size)
        assertEquals(date2, remaining[0].date)
    }

    @Test
    fun importDailyLogs_bulkInsertsEntries() = runTest {
        val logs = listOf(
            DailyLog(date = LocalDate.of(2026, 10, 1), fastCompleted = true, updatedAtMillis = 1000L),
            DailyLog(date = LocalDate.of(2026, 10, 2), fastCompleted = false, updatedAtMillis = 2000L),
            DailyLog(date = LocalDate.of(2026, 10, 3), isRestDay = true, updatedAtMillis = 3000L)
        )

        repository.importDailyLogs(logs)

        val active = repository.getActiveDailyLogsOnce()
        assertEquals(3, active.size)
    }
}
