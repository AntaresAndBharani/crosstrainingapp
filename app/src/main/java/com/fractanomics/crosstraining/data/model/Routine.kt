package com.fractanomics.crosstraining.data.model

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * A complex / routine performed to improve a main lift, e.g.
 * "Clean + Hang Clean + Push Jerk" targeting Clean & Jerk. The weight used in
 * this routine over time is the routine's own progression, tracked through the
 * sessions that reference it.
 */
@Entity(
    tableName = "routines",
    foreignKeys = [
        ForeignKey(
            entity = Exercise::class,
            parentColumns = ["id"],
            childColumns = ["mainExerciseId"],
            onDelete = ForeignKey.SET_NULL
        )
    ],
    indices = [Index("mainExerciseId")]
)
data class Routine(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val mainExerciseId: Long? = null,
    val description: String = "",
    val defaultFormat: String = ""
)
