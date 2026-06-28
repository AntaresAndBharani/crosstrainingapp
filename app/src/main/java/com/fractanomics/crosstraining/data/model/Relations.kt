package com.fractanomics.crosstraining.data.model

import androidx.room.Embedded
import androidx.room.Relation

/** A block together with all of its sets, ordered by [BlockSet.position]. */
data class BlockWithSets(
    @Embedded val block: SessionBlock,
    @Relation(parentColumn = "id", entityColumn = "blockId")
    val sets: List<BlockSet>
)

/** A session together with all of its blocks, each carrying its own sets. */
data class SessionWithBlocks(
    @Embedded val session: Session,
    @Relation(
        entity = SessionBlock::class,
        parentColumn = "id",
        entityColumn = "sessionId"
    )
    val blocks: List<BlockWithSets>
)
