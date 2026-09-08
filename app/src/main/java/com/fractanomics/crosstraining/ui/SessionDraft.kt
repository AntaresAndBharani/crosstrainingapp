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
    val newRepMaxWeight: Double?,
    val section: String = "",
    val exerciseIdsCsv: String = ""
)

data class SessionDraft(
    val cycleId: Long,
    val date: LocalDate,
    val title: String,
    val notes: String,
    val blocks: List<BlockDraft>
)

/**
 * UI-side state holder representing in-flight Workout Journey Assistant setup across 4 steps.
 */
data class WorkoutJourneyDraft(
    val document: com.fractanomics.crosstraining.util.ParsedWorkoutDocument,
    val resolutionResult: com.fractanomics.crosstraining.data.ai.WorkoutEntityResolutionResult,
    val currentStep: Int = 1,
    val routineTitle: String = "",
    val saveAsRoutine: Boolean = true,
    val sessionTitle: String = "",
    val logAsSession: Boolean = true,
    val cycleId: Long? = null,
    val sessionDate: LocalDate = LocalDate.now(),
    val missingExercises: List<com.fractanomics.crosstraining.data.model.Exercise> = emptyList()
)
