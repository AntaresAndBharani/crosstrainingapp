package com.fractanomics.crosstraining.data.dao

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Update
import com.fractanomics.crosstraining.data.model.Session
import com.fractanomics.crosstraining.data.model.SessionWithBlocks
import kotlinx.coroutines.flow.Flow

@Dao
interface SessionDao {
    @Insert
    suspend fun insertSession(session: Session): Long

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertSessions(sessions: List<Session>)

    @Update
    suspend fun updateSession(session: Session)

    @Delete
    suspend fun deleteSession(session: Session)

    @Transaction
    @Query("SELECT * FROM sessions WHERE cycleId = :cycleId ORDER BY date DESC, id DESC")
    fun observeByCycle(cycleId: Long): Flow<List<SessionWithBlocks>>

    @Transaction
    @Query("SELECT * FROM sessions ORDER BY date DESC, id DESC")
    fun observeAll(): Flow<List<SessionWithBlocks>>

    @Transaction
    @Query("SELECT * FROM sessions WHERE id = :id")
    suspend fun getByIdOnce(id: Long): SessionWithBlocks?

    @Query("SELECT * FROM sessions")
    suspend fun getAllSessionsOnce(): List<Session>

    @Query("DELETE FROM sessions")
    suspend fun deleteAllSessions()
}
