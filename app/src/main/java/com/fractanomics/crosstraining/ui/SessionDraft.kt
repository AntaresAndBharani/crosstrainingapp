package com.fractanomics.crosstraining.ui

import com.fractanomics.crosstraining.data.model.BlockKind
import java.time.LocalDate

/** UI-side draft of one set being logged. */
data class SetDraft(
    val reps: Int,
    val weight: Double? = null,
    val metricValue: Double? = null,
    val groupIndex: Int? = null,
    val isWarmup: Boolean = false,
    val isFailed: Boolean = false
)

/**
 * UI-side draft of one block. The main exercise is given either as
 * [existingExerciseId] or a [newExerciseName] to be created on save.
 */
data class BlockDraft(
    val name: String,
    val kind: BlockKind,
    val format: String,
    val scheme: String,
    val existingExerciseId: Long?,
    val newExerciseName: String?,
    val routineId: Long?,
    val description: String,
    val resultText: String,
    val resultValue: Double?,
    val sets: List<SetDraft>,
    val newRepMaxReps: Int?,
    val newRepMaxWeight: Double?
)

/** UI-side draft of a whole session, passed from the Log screen to the ViewModel. */
data class SessionDraft(
    val cycleId: Long,
    val date: LocalDate,
    val title: String,
    val notes: String,
    val blocks: List<BlockDraft>
)
