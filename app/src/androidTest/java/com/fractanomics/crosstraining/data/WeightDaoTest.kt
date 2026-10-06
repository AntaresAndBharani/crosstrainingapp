package com.fractanomics.crosstraining.data

import androidx.room.Room
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.fractanomics.crosstraining.data.dao.WeightDao
import com.fractanomics.crosstraining.data.model.WeightEntry
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.io.IOException
import java.time.LocalDate

/**
 * Instrumented Room database tests for [WeightDao] verifying real SQLite query execution
 * on Android runtime (Issue #575, Parent Story #574).
 */
@RunWith(AndroidJUnit4::class)
class WeightDaoTest {

    private lateinit var db: AppDatabase
    private lateinit var weightDao: WeightDao

    @Before
    fun createDb() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        db = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java).build()
        weightDao = db.weightDao()
    }

    @After
    @Throws(IOException::class)
    fun closeDb() {
        db.close()
    }

    @Test
    fun getLatestOnOrBefore_queriesRealSqliteCorrectly() = runBlocking {
        val targetDate = LocalDate.of(2026, 10, 10)
        val minDate = targetDate.minusDays(7)

        weightDao.upsert(WeightEntry(date = LocalDate.of(2026, 10, 7), weightKg = 81.0, updatedAtMillis = 1000L))
        weightDao.upsert(WeightEntry(date = targetDate, weightKg = 80.0, updatedAtMillis = 1000L))

        val result = weightDao.getLatestOnOrBefore(targetDate, minDate)
        assertNotNull(result)
        assertEquals(targetDate, result?.date)
        assertEquals(80.0, result?.weightKg ?: 0.0, 0.001)
    }

    @Test
    fun getLatestOnOrBefore_excludesTombstonesInRealSqlite() = runBlocking {
        val targetDate = LocalDate.of(2026, 10, 10)
        val minDate = targetDate.minusDays(7)

        weightDao.upsert(WeightEntry(date = LocalDate.of(2026, 10, 7), weightKg = 81.0, updatedAtMillis = 1000L))
        weightDao.upsert(WeightEntry(date = LocalDate.of(2026, 10, 9), weightKg = 80.5, updatedAtMillis = 1000L, deletedAtMillis = 2000L))

        val result = weightDao.getLatestOnOrBefore(targetDate, minDate)
        assertNotNull(result)
        assertEquals(LocalDate.of(2026, 10, 7), result?.date)
        assertEquals(81.0, result?.weightKg ?: 0.0, 0.001)
    }

    @Test
    fun getLatestOnOrBefore_outsideWindow_returnsNull() = runBlocking {
        val targetDate = LocalDate.of(2026, 10, 10)
        val minDate = targetDate.minusDays(7)

        weightDao.upsert(WeightEntry(date = LocalDate.of(2026, 10, 1), weightKg = 82.0, updatedAtMillis = 1000L))

        val result = weightDao.getLatestOnOrBefore(targetDate, minDate)
        assertNull(result)
    }
}
