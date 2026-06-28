package com.fractanomics.crosstraining.data.model

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import java.time.LocalDate

/**
 * A rep-max record for an exercise: e.g. a new 1RM / 2RM / 3RM / 5RM. Stored as
 * history (every entry kept) so progression can be charted; the current best
 * for a given rep count is the max [weight] across rows.
 */
@Entity(
    tableName = "rep_maxes",
    foreignKeys = [
        ForeignKey(
            entity = Exercise::class,
            parentColumns = ["id"],
            childColumns = ["exerciseId"],
            onDelete = ForeignKey.CASCADE
        ),
        ForeignKey(
            entity = Cycle::class,
            parentColumns = ["id"],
            childColumns = ["cycleId"],
            onDelete = ForeignKey.SET_NULL
        ),
        ForeignKey(
            entity = Session::class,
            parentColumns = ["id"],
            childColumns = ["sessionId"],
            onDelete = ForeignKey.SET_NULL
        ),
        ForeignKey(
            entity = SessionBlock::class,
            parentColumns = ["id"],
            childColumns = ["blockId"],
            onDelete = ForeignKey.SET_NULL
        )
    ],
    indices = [Index("exerciseId"), Index("cycleId"), Index("sessionId"), Index("blockId")]
)
data class RepMax(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val exerciseId: Long,
    val reps: Int,
    val weight: Double,
    val date: LocalDate,
    val cycleId: Long? = null,
    val sessionId: Long? = null,
    val blockId: Long? = null
)
