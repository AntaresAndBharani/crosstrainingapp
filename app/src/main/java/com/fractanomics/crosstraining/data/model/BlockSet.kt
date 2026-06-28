package com.fractanomics.crosstraining.data.model

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * One set/round within a [SessionBlock]. [weight] is used for loaded movements,
 * [metricValue] for calories/distance/time. [groupIndex] is an optional wave or
 * round tag so sets can be visually chunked (e.g. the lines of a 3-2-1 wave).
 * [isWarmup] marks ramp-up sets (e.g. empty bar / "pica") and [isFailed] marks a
 * missed/failed attempt ("fallo").
 */
@Entity(
    tableName = "block_sets",
    foreignKeys = [
        ForeignKey(
            entity = SessionBlock::class,
            parentColumns = ["id"],
            childColumns = ["blockId"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [Index("blockId")]
)
data class BlockSet(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val blockId: Long,
    val position: Int,
    val groupIndex: Int? = null,
    val reps: Int,
    val weight: Double? = null,
    val metricValue: Double? = null,
    val isWarmup: Boolean = false,
    val isFailed: Boolean = false,
    val notes: String = ""
)
