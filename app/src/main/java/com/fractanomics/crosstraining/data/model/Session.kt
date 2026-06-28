package com.fractanomics.crosstraining.data.model

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import java.time.LocalDate

/**
 * A single logged workout entry within a cycle. It captures the structured
 * prescription: [format] (E3MOM, EMOM, AMRAP...), the [repScheme]
 * ("3-2-1-3-2-1...") and links to the routine/complex and the main exercise.
 * Per-set detail lives in [SessionSet].
 */
@Entity(
    tableName = "sessions",
    foreignKeys = [
        ForeignKey(
            entity = Cycle::class,
            parentColumns = ["id"],
            childColumns = ["cycleId"],
            onDelete = ForeignKey.CASCADE
        ),
        ForeignKey(
            entity = Routine::class,
            parentColumns = ["id"],
            childColumns = ["routineId"],
            onDelete = ForeignKey.SET_NULL
        ),
        ForeignKey(
            entity = Exercise::class,
            parentColumns = ["id"],
            childColumns = ["mainExerciseId"],
            onDelete = ForeignKey.SET_NULL
        )
    ],
    indices = [Index("cycleId"), Index("routineId"), Index("mainExerciseId")]
)
data class Session(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val cycleId: Long,
    val routineId: Long? = null,
    val mainExerciseId: Long? = null,
    val date: LocalDate,
    val format: String = "",
    val repScheme: String = "",
    val notes: String = ""
)
