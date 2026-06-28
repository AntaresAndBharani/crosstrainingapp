package com.fractanomics.crosstraining.data.model

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * One block within a session — e.g. a warm-up complex, the main snatch waves, a
 * snatch-pull block, an accessory block or a WOD. Each block has its own
 * [format] (E3MOM, EMOM, AMRAP…) and [scheme] (e.g. "3-2-1-3-2-1…"), can target
 * a main lift via [mainExerciseId] (for progression / rep-maxes) and optionally
 * links to a saved [routineId]. Its sets live in [BlockSet].
 *
 * For a [BlockKind.METCON] block, [description] holds the movements/intervals and
 * [resultText]/[resultValue] hold the score (e.g. "2 rounds + 15 reps").
 */
@Entity(
    tableName = "session_blocks",
    foreignKeys = [
        ForeignKey(
            entity = Session::class,
            parentColumns = ["id"],
            childColumns = ["sessionId"],
            onDelete = ForeignKey.CASCADE
        ),
        ForeignKey(
            entity = Exercise::class,
            parentColumns = ["id"],
            childColumns = ["mainExerciseId"],
            onDelete = ForeignKey.SET_NULL
        ),
        ForeignKey(
            entity = Routine::class,
            parentColumns = ["id"],
            childColumns = ["routineId"],
            onDelete = ForeignKey.SET_NULL
        )
    ],
    indices = [Index("sessionId"), Index("mainExerciseId"), Index("routineId")]
)
data class SessionBlock(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val sessionId: Long,
    val position: Int,
    val name: String = "",
    val kind: BlockKind = BlockKind.STRENGTH,
    val format: String = "",
    val scheme: String = "",
    val mainExerciseId: Long? = null,
    val routineId: Long? = null,
    val description: String = "",
    val resultText: String = "",
    val resultValue: Double? = null,
    val notes: String = ""
)
