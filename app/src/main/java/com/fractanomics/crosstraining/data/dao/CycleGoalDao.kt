package com.fractanomics.crosstraining.data.dao

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import com.fractanomics.crosstraining.data.model.CycleGoal
import kotlinx.coroutines.flow.Flow

@Dao
interface CycleGoalDao {
    @Query("SELECT * FROM cycle_goals WHERE cycleId = :cycleId")
    fun byCycle(cycleId: Long): Flow<List<CycleGoal>>

    @Query("SELECT * FROM cycle_goals")
    fun all(): Flow<List<CycleGoal>>

    @Query("SELECT * FROM cycle_goals")
    suspend fun snapshot(): List<CycleGoal>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(goal: CycleGoal): Long

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(goals: List<CycleGoal>)

    @Update
    suspend fun update(goal: CycleGoal)

    @Delete
    suspend fun delete(goal: CycleGoal)

    @Query("DELETE FROM cycle_goals WHERE cycleId = :cycleId")
    suspend fun deleteByCycle(cycleId: Long)

    @Query("DELETE FROM cycle_goals")
    suspend fun deleteAll()
}
