package com.fractanomics.crosstraining.data.dao

import androidx.room.Dao
import androidx.room.Query
import androidx.room.Upsert
import com.fractanomics.crosstraining.data.model.DailyLog
import kotlinx.coroutines.flow.Flow
import java.time.LocalDate

@Dao
interface DailyLogDao {

    @Upsert
    suspend fun upsert(dailyLog: DailyLog): Long

    @Upsert
    suspend fun upsertAll(dailyLogs: List<DailyLog>)

    @Query("SELECT * FROM daily_logs WHERE deletedAtMillis IS NULL ORDER BY date DESC")
    fun observeAllActive(): Flow<List<DailyLog>>

    @Query("SELECT * FROM daily_logs WHERE deletedAtMillis IS NULL ORDER BY date DESC")
    suspend fun getAllActiveOnce(): List<DailyLog>

    @Query("SELECT * FROM daily_logs ORDER BY date DESC")
    suspend fun getAllIncludingTombstones(): List<DailyLog>

    @Query("SELECT * FROM daily_logs WHERE date = :date LIMIT 1")
    suspend fun getEntryByDate(date: LocalDate): DailyLog?

    @Query("UPDATE daily_logs SET deletedAtMillis = :deletedAt, updatedAtMillis = :deletedAt WHERE date = :date")
    suspend fun markDeleted(date: LocalDate, deletedAt: Long)

    @Query("DELETE FROM daily_logs WHERE deletedAtMillis IS NOT NULL AND deletedAtMillis < :cutoffMillis")
    suspend fun purgeOldTombstones(cutoffMillis: Long)

    @Query("DELETE FROM daily_logs")
    suspend fun deleteAll()
}
