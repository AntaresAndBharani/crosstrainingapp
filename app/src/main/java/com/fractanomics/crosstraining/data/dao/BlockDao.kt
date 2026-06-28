package com.fractanomics.crosstraining.data.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.fractanomics.crosstraining.data.model.BlockSet
import com.fractanomics.crosstraining.data.model.SessionBlock

@Dao
interface BlockDao {
    @Insert
    suspend fun insertBlock(block: SessionBlock): Long

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertBlocks(blocks: List<SessionBlock>)

    @Insert
    suspend fun insertSets(sets: List<BlockSet>)

    @Query("SELECT * FROM session_blocks")
    suspend fun getAllBlocksOnce(): List<SessionBlock>

    @Query("SELECT * FROM block_sets")
    suspend fun getAllSetsOnce(): List<BlockSet>

    @Query("DELETE FROM session_blocks")
    suspend fun deleteAllBlocks()

    @Query("DELETE FROM session_blocks WHERE sessionId = :sessionId")
    suspend fun deleteBlocksForSession(sessionId: Long)

    @Query("DELETE FROM block_sets")
    suspend fun deleteAllSets()
}
