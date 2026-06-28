package com.fractanomics.crosstraining.data.dao

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.Query
import com.fractanomics.crosstraining.data.model.RepMax
import kotlinx.coroutines.flow.Flow

@Dao
interface RepMaxDao {
    @Insert
    suspend fun insert(repMax: RepMax): Long

    @Delete
    suspend fun delete(repMax: RepMax)

    @Query("SELECT * FROM rep_maxes WHERE exerciseId = :exerciseId ORDER BY reps ASC, date ASC")
    fun observeForExercise(exerciseId: Long): Flow<List<RepMax>>

    @Query("SELECT * FROM rep_maxes ORDER BY date DESC, id DESC")
    fun observeAll(): Flow<List<RepMax>>

    @Query("SELECT MAX(weight) FROM rep_maxes WHERE exerciseId = :exerciseId AND reps = :reps")
    suspend fun bestWeight(exerciseId: Long, reps: Int): Double?
}
