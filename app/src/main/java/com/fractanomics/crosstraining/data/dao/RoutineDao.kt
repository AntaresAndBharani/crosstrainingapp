package com.fractanomics.crosstraining.data.dao

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import androidx.room.Transaction
import com.fractanomics.crosstraining.data.model.Routine
import com.fractanomics.crosstraining.data.model.RoutineBlock
import com.fractanomics.crosstraining.data.model.RoutineWithBlocks
import kotlinx.coroutines.flow.Flow

@Dao
interface RoutineDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(routine: Routine): Long

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(routines: List<Routine>)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertBlock(block: RoutineBlock): Long

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertBlocks(blocks: List<RoutineBlock>)

    @Query("DELETE FROM routine_blocks WHERE routineId = :routineId")
    suspend fun deleteBlocksForRoutine(routineId: Long)

    @Query("SELECT * FROM routines")
    suspend fun getAllOnce(): List<Routine>

    @Transaction
    @Query("SELECT * FROM routines")
    suspend fun getAllWithBlocksOnce(): List<RoutineWithBlocks>

    @Query("DELETE FROM routines")
    suspend fun deleteAll()

    @Update
    suspend fun update(routine: Routine)

    @Delete
    suspend fun delete(routine: Routine)

    @Query("SELECT * FROM routines ORDER BY name COLLATE NOCASE")
    fun observeAll(): Flow<List<Routine>>

    @Transaction
    @Query("SELECT * FROM routines ORDER BY name COLLATE NOCASE")
    fun observeWithBlocks(): Flow<List<RoutineWithBlocks>>

    @Query("SELECT * FROM routines WHERE id = :id")
    suspend fun byId(id: Long): Routine?
}
