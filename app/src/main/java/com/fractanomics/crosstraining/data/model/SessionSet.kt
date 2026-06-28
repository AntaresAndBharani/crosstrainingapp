package com.fractanomics.crosstraining.data.model

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * One set/round within a session. [weight] is used for loaded movements while
 * [metricValue] holds calories/distance/time for machine-based work. Both are
 * nullable so a set can carry whichever metric is relevant.
 */
@Entity(
    tableName = "session_sets",
    foreignKeys = [
        ForeignKey(
            entity = Session::class,
            parentColumns = ["id"],
            childColumns = ["sessionId"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [Index("sessionId")]
)
data class SessionSet(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val sessionId: Long,
    val position: Int,
    val reps: Int,
    val weight: Double? = null,
    val metricValue: Double? = null,
    val notes: String = ""
)
