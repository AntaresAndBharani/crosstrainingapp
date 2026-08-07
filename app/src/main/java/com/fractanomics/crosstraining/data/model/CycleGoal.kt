package com.fractanomics.crosstraining.data.model

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * A target goal for a basic movement within a training cycle
 * (e.g. 1RM Snatch goal: 90kg -> 100kg during an 8-week cycle).
 */
@Entity(
    tableName = "cycle_goals",
    foreignKeys = [
        ForeignKey(
            entity = Cycle::class,
            parentColumns = ["id"],
            childColumns = ["cycleId"],
            onDelete = ForeignKey.CASCADE
        ),
        ForeignKey(
            entity = Exercise::class,
            parentColumns = ["id"],
            childColumns = ["exerciseId"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [
        Index("cycleId"),
        Index("exerciseId")
    ]
)
data class CycleGoal(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val cycleId: Long = 0,
    val exerciseId: Long = 0,
    val targetReps: Int = 1,
    val startWeight: Double = 0.0,
    val targetWeight: Double = 0.0,
    val notes: String = ""
)
