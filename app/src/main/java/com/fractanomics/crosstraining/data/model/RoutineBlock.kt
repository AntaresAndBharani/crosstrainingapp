package com.fractanomics.crosstraining.data.model

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * One block within a daily [Routine] (e.g. Weightlifting block, Hypertrophy block,
 * Accessory block, Metabolic WOD, or Cardio block).
 *
 * A routine represents a full daily workout comprising multiple blocks.
 */
@Entity(
    tableName = "routine_blocks",
    foreignKeys = [
        ForeignKey(
            entity = Routine::class,
            parentColumns = ["id"],
            childColumns = ["routineId"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [Index("routineId")]
)
data class RoutineBlock(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val routineId: Long = 0,
    val position: Int = 0,
    val name: String = "",
    val kind: BlockKind = BlockKind.WEIGHTLIFTING,
    val format: String = "",
    val setsCount: Int = 1,
    val exerciseIdsCsv: String = "",
    val notes: String = ""
)
