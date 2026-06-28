package com.fractanomics.crosstraining.data.model

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import java.time.LocalDate

/**
 * A training day. A session is a container that owns an ordered list of
 * [SessionBlock]s (warm-up, strength, accessory, WOD…). The structured
 * prescription (format, rep scheme, target lift, sets) lives on the blocks.
 */
@Entity(
    tableName = "sessions",
    foreignKeys = [
        ForeignKey(
            entity = Cycle::class,
            parentColumns = ["id"],
            childColumns = ["cycleId"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [Index("cycleId")]
)
data class Session(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val cycleId: Long,
    val date: LocalDate,
    val title: String = "",
    val notes: String = ""
)
