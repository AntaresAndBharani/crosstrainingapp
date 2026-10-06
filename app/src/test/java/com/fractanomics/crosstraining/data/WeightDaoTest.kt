package com.fractanomics.crosstraining.data

import com.fractanomics.crosstraining.data.model.WeightEntry
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test
import java.time.LocalDate

/**
 * Unit & Integration tests verifying [com.fractanomics.crosstraining.data.dao.WeightDao.getLatestOnOrBefore]
 * query specification and boundary conditions (Issue #575, Parent Story #574).
 *
 * Acceptance Scenarios Covered:
 * - Scenario 5: Automatic 7-Day Lookback Baseline Resolution
 *   - When entry exists on target date, it returns target date entry.
 *   - When no entry on target date but entry exists within window, returns latest entry.
 *   - When nearest entry is outside the window (< minDate), returns null.
 * - Scenario 6: Soft-Deleted Entries Exclusion
 *   - Soft-deleted entries (deletedAtMillis != null) are excluded even if closer in date.
 * - Scenario 7: Tombstone Exclusion in Baseline Lookups
 *   - When all entries in window are tombstones, returns null.
 * - Boundary Invariants:
 *   - Inclusive boundary at date and minDate.
 *   - Future entries relative to date are excluded.
 */
class WeightDaoTest {

    private lateinit var fakeDb: FakeSampleAppDatabase
    private lateinit var repository: Repository

    @Before
    fun setUp() {
        fakeDb = FakeSampleAppDatabase()
        repository = Repository(db = fakeDb)
    }

    @Test
    fun getLatestOnOrBefore_exactDateMatch_returnsTargetDateEntry() = runTest {
        val targetDate = LocalDate.of(2026, 10, 10)
        val minDate = targetDate.minusDays(7) // 2026-10-03

        // Given weight entry exists on October 10 (80.0 kg) and October 7 (81.0 kg)
        repository.saveWeightEntry(weightKg = 81.0, date = LocalDate.of(2026, 10, 7))
        repository.saveWeightEntry(weightKg = 80.0, date = targetDate)

        // When querying for latest on or before October 10 with 7-day lookback
        val result = repository.getLatestWeightOnOrBefore(date = targetDate, minDate = minDate)

        // Then it returns October 10 entry (80.0 kg)
        assertNotNull(result)
        assertEquals(targetDate, result?.date)
        assertEquals(80.0, result?.weightKg ?: 0.0, 0.001)
    }

    @Test
    fun getLatestOnOrBefore_noEntryOnTargetDate_returnsNearestWithinWindow() = runTest {
        val targetDate = LocalDate.of(2026, 10, 10)
        val minDate = targetDate.minusDays(7) // 2026-10-03

        // Given no entry on Oct 10, but an entry on Oct 7 (81.0 kg) and Oct 5 (82.0 kg)
        repository.saveWeightEntry(weightKg = 82.0, date = LocalDate.of(2026, 10, 5))
        repository.saveWeightEntry(weightKg = 81.0, date = LocalDate.of(2026, 10, 7))

        // When querying for latest on or before October 10
        val result = repository.getLatestWeightOnOrBefore(date = targetDate, minDate = minDate)

        // Then it returns the nearest entry: October 7 (81.0 kg)
        assertNotNull(result)
        assertEquals(LocalDate.of(2026, 10, 7), result?.date)
        assertEquals(81.0, result?.weightKg ?: 0.0, 0.001)
    }

    @Test
    fun getLatestOnOrBefore_nearestEntryOutsideWindow_returnsNull() = runTest {
        val targetDate = LocalDate.of(2026, 10, 10)
        val minDate = targetDate.minusDays(7) // 2026-10-03

        // Given nearest entry is October 1 (9 days prior, outside the 7-day window)
        repository.saveWeightEntry(weightKg = 83.0, date = LocalDate.of(2026, 10, 1))

        // When querying with minDate = Oct 3
        val result = repository.getLatestWeightOnOrBefore(date = targetDate, minDate = minDate)

        // Then initial weight remains null
        assertNull("Entry outside 7-day window must not be returned", result)
    }

    @Test
    fun getLatestOnOrBefore_futureEntryAfterDate_isExcluded() = runTest {
        val targetDate = LocalDate.of(2026, 10, 10)
        val minDate = targetDate.minusDays(7)

        // Given an entry logged for October 12 (in the future relative to targetDate)
        repository.saveWeightEntry(weightKg = 79.0, date = LocalDate.of(2026, 10, 12))
        repository.saveWeightEntry(weightKg = 81.0, date = LocalDate.of(2026, 10, 6))

        // When querying for latest on or before October 10
        val result = repository.getLatestWeightOnOrBefore(date = targetDate, minDate = minDate)

        // Then it ignores October 12 and returns October 6
        assertNotNull(result)
        assertEquals(LocalDate.of(2026, 10, 6), result?.date)
        assertEquals(81.0, result?.weightKg ?: 0.0, 0.001)
    }

    @Test
    fun getLatestOnOrBefore_softDeletedEntryIgnored_fallsBackToEarlierActiveEntry() = runTest {
        val targetDate = LocalDate.of(2026, 10, 10)
        val minDate = targetDate.minusDays(7)

        // Given an active entry on October 7 (81.0 kg)
        repository.saveWeightEntry(weightKg = 81.0, date = LocalDate.of(2026, 10, 7))

        // And a weight entry on October 9 with deletedAtMillis != null (soft-deleted)
        repository.saveWeightEntry(weightKg = 80.5, date = LocalDate.of(2026, 10, 9))
        repository.deleteWeightEntry(date = LocalDate.of(2026, 10, 9), deletedAt = 1728500000000L)

        // When resolving baseline weight for October 10
        val result = repository.getLatestWeightOnOrBefore(date = targetDate, minDate = minDate)

        // Then the soft-deleted entry is ignored and October 7 entry is retrieved
        assertNotNull(result)
        assertEquals(LocalDate.of(2026, 10, 7), result?.date)
        assertEquals(81.0, result?.weightKg ?: 0.0, 0.001)
    }

    @Test
    fun getLatestOnOrBefore_allEntriesInWindowSoftDeleted_returnsNull() = runTest {
        val targetDate = LocalDate.of(2026, 10, 10)
        val minDate = targetDate.minusDays(7)

        // Given entries in the window but all soft-deleted
        repository.saveWeightEntry(weightKg = 81.0, date = LocalDate.of(2026, 10, 7))
        repository.deleteWeightEntry(date = LocalDate.of(2026, 10, 7), deletedAt = 1728400000000L)

        repository.saveWeightEntry(weightKg = 80.5, date = LocalDate.of(2026, 10, 9))
        repository.deleteWeightEntry(date = LocalDate.of(2026, 10, 9), deletedAt = 1728500000000L)

        // When resolving baseline weight
        val result = repository.getLatestWeightOnOrBefore(date = targetDate, minDate = minDate)

        // Then returns null
        assertNull("All soft-deleted entries must yield null", result)
    }

    @Test
    fun getLatestOnOrBefore_exactMinDateBoundary_isInclusive() = runTest {
        val targetDate = LocalDate.of(2026, 10, 10)
        val minDate = LocalDate.of(2026, 10, 3)

        // Given entry exactly on minDate (October 3)
        repository.saveWeightEntry(weightKg = 82.5, date = minDate)

        // When querying with minDate = October 3
        val result = repository.getLatestWeightOnOrBefore(date = targetDate, minDate = minDate)

        // Then it is returned
        assertNotNull(result)
        assertEquals(minDate, result?.date)
        assertEquals(82.5, result?.weightKg ?: 0.0, 0.001)
    }

    @Test
    fun getLatestOnOrBefore_singleDayWindow_returnsEntryIfMatches() = runTest {
        val date = LocalDate.of(2026, 10, 10)

        repository.saveWeightEntry(weightKg = 79.8, date = date)

        val result = repository.getLatestWeightOnOrBefore(date = date, minDate = date)

        assertNotNull(result)
        assertEquals(date, result?.date)
        assertEquals(79.8, result?.weightKg ?: 0.0, 0.001)
    }
}
