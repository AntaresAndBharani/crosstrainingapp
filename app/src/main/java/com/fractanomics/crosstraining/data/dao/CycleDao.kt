package com.fractanomics.crosstraining.data.dao

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import com.fractanomics.crosstraining.data.model.Cycle
import kotlinx.coroutines.flow.Flow

@Dao
interface CycleDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(cycle: Cycle): Long

    @Update
    suspend fun update(cycle: Cycle)

    @Delete
    suspend fun delete(cycle: Cycle)

    @Query("SELECT * FROM cycles ORDER BY startDate DESC, id DESC")
    fun observeAll(): Flow<List<Cycle>>

    @Query("SELECT * FROM cycles WHERE isActive = 1 LIMIT 1")
    fun observeActive(): Flow<Cycle?>

    @Query("UPDATE cycles SET isActive = 0")
    suspend fun clearActive()

    @Query("UPDATE cycles SET isActive = 1 WHERE id = :id")
    suspend fun markActive(id: Long)

    @Query("SELECT * FROM cycles WHERE id = :id")
    suspend fun byId(id: Long): Cycle?
}
