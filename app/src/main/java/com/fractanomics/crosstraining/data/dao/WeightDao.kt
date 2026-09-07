package com.fractanomics.crosstraining.data.dao

import androidx.room.Dao
import androidx.room.Query
import androidx.room.Upsert
import com.fractanomics.crosstraining.data.model.WeightEntry
import kotlinx.coroutines.flow.Flow
import java.time.LocalDate

@Dao
interface WeightDao {

    @Upsert
    suspend fun upsert(entry: WeightEntry): Long

    @Upsert
    suspend fun upsertAll(entries: List<WeightEntry>)

    @Query("SELECT * FROM weight_entries WHERE deletedAtMillis IS NULL ORDER BY date DESC")
    fun getAllActiveEntries(): Flow<List<WeightEntry>>

    @Query("SELECT * FROM weight_entries WHERE deletedAtMillis IS NULL ORDER BY date DESC")
    suspend fun getAllActiveEntriesOnce(): List<WeightEntry>

    @Query("SELECT * FROM weight_entries ORDER BY date DESC")
    suspend fun getAllEntriesIncludingTombstones(): List<WeightEntry>

    @Query("SELECT * FROM weight_entries WHERE date = :date LIMIT 1")
    suspend fun getEntryByDate(date: LocalDate): WeightEntry?

    @Query("UPDATE weight_entries SET deletedAtMillis = :deletedAt, updatedAtMillis = :deletedAt WHERE date = :date")
    suspend fun markDeleted(date: LocalDate, deletedAt: Long)

    @Query("DELETE FROM weight_entries WHERE deletedAtMillis IS NOT NULL AND deletedAtMillis < :cutoffMillis")
    suspend fun purgeOldTombstones(cutoffMillis: Long)

    @Query("DELETE FROM weight_entries")
    suspend fun deleteAll()
}
