package com.fractanomics.crosstraining.data.dao

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Update
import com.fractanomics.crosstraining.data.model.Session
import com.fractanomics.crosstraining.data.model.SessionSet
import com.fractanomics.crosstraining.data.model.SessionWithSets
import kotlinx.coroutines.flow.Flow

@Dao
interface SessionDao {
    @Insert
    suspend fun insertSession(session: Session): Long

    @Insert
    suspend fun insertSets(sets: List<SessionSet>)

    @Insert(onConflict = androidx.room.OnConflictStrategy.REPLACE)
    suspend fun insertSessions(sessions: List<Session>)

    @Query("SELECT * FROM sessions")
    suspend fun getAllSessionsOnce(): List<Session>

    @Query("SELECT * FROM session_sets")
    suspend fun getAllSetsOnce(): List<SessionSet>

    @Query("DELETE FROM session_sets")
    suspend fun deleteAllSets()

    @Query("DELETE FROM sessions")
    suspend fun deleteAllSessions()

    @Update
    suspend fun updateSession(session: Session)

    @Delete
    suspend fun deleteSession(session: Session)

    @Transaction
    @Query("SELECT * FROM sessions WHERE cycleId = :cycleId ORDER BY date DESC, id DESC")
    fun observeByCycle(cycleId: Long): Flow<List<SessionWithSets>>

    @Transaction
    @Query("SELECT * FROM sessions ORDER BY date DESC, id DESC")
    fun observeAll(): Flow<List<SessionWithSets>>

    @Transaction
    @Query("SELECT * FROM sessions WHERE routineId = :routineId ORDER BY date ASC, id ASC")
    fun observeByRoutine(routineId: Long): Flow<List<SessionWithSets>>

    @Transaction
    @Query("SELECT * FROM sessions WHERE mainExerciseId = :exerciseId ORDER BY date ASC, id ASC")
    fun observeByExercise(exerciseId: Long): Flow<List<SessionWithSets>>
}
