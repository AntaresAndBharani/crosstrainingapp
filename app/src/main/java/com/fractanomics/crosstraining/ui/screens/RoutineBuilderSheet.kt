package com.fractanomics.crosstraining.ui.screens

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ArrowDownward
import androidx.compose.material.icons.filled.ArrowUpward
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.Remove
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.UnfoldLess
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedCard
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInParent
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.semantics.CustomAccessibilityAction
import androidx.compose.ui.semantics.collapse
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.customActions
import androidx.compose.ui.semantics.expand
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.fractanomics.crosstraining.data.model.BlockKind
import com.fractanomics.crosstraining.data.model.Exercise
import com.fractanomics.crosstraining.data.model.ExerciseCategory
import com.fractanomics.crosstraining.data.model.MetricType
import com.fractanomics.crosstraining.data.model.Routine
import com.fractanomics.crosstraining.data.model.RoutineBlock
import com.fractanomics.crosstraining.data.model.RoutineWithBlocks
import com.fractanomics.crosstraining.util.ExerciseTargetMetric
import com.fractanomics.crosstraining.util.RoutineExerciseTargetSpec
import com.fractanomics.crosstraining.util.RoutineTargetCodec
import com.fractanomics.crosstraining.util.TargetMode
import kotlinx.coroutines.launch

// ============================================================================
// Pure Immutable Routine Builder UI State & Reducers (UDF / Testable)
// ============================================================================

const val ALL_ZERO_EXERCISE_ERROR_MESSAGE =
    "Set a target above 0 on at least one set, or remove this exercise"

enum class ExerciseSetPattern(val label: String) {
    COPY_FIRST("Copy Set 1 to All"),
    DESCENDING("Descending"),
    ALTERNATE_ZEROS("Alternate 0s")
}

data class MetricValueCache(
    val fixedValue: Int,
    val perSetValues: List<Int>
)

data class RoutineExerciseUi(
    val uiKey: String,
    val exerciseId: Long,
    val exerciseName: String,
    val category: ExerciseCategory = ExerciseCategory.BARBELL,
    val targetMetric: ExerciseTargetMetric = ExerciseTargetMetric.REPS,
    val targetMode: TargetMode = TargetMode.FIXED,
    val fixedValue: Int = targetMetric.defaultFixedValue,
    val perSetValues: List<Int> = listOf(targetMetric.defaultFixedValue),
    val cachedMetricValues: Map<ExerciseTargetMetric, MetricValueCache> = emptyMap(),
    val showValidationError: Boolean = false
) {
    fun toTargetSpec(setsCount: Int): RoutineExerciseTargetSpec = RoutineExerciseTargetSpec(
        metric = targetMetric,
        mode = targetMode,
        fixedValue = fixedValue.coerceAtLeast(0),
        perSetValues = RoutineTargetCodec.resizePerSetValues(perSetValues, fixedValue, setsCount)
    )

    val isAllZeroInvalid: Boolean
        get() = when (targetMode) {
            TargetMode.FIXED -> fixedValue <= 0
            TargetMode.PER_SET -> perSetValues.isEmpty() || perSetValues.all { it <= 0 }
        }

    fun summaryBadge(setsCount: Int): String =
        RoutineTargetCodec.formatExerciseTargetBadge(toTargetSpec(setsCount), setsCount)
}

data class RoutineBlockUi(
    val uiKey: String,
    val name: String = "",
    val kind: BlockKind = BlockKind.WEIGHTLIFTING,
    val format: String = "Sets x Reps",
    val setsCount: Int = 4,
    val formatDefaultHint: String? = null,
    val isSetupExpanded: Boolean = true,
    val isExercisesExpanded: Boolean = true,
    val expandedExerciseKey: String? = null,
    val exercises: List<RoutineExerciseUi> = emptyList(),
    val notes: String = "",
    val section: String = "",
    val subBlock: String = ""
)

sealed class DeletedRoutineItem {
    abstract val label: String

    data class BlockItem(
        val index: Int,
        val block: RoutineBlockUi
    ) : DeletedRoutineItem() {
        override val label: String
            get() = block.name.ifBlank { "Block ${index + 1}" }
    }

    data class ExerciseItem(
        val blockKey: String,
        val index: Int,
        val exercise: RoutineExerciseUi
    ) : DeletedRoutineItem() {
        override val label: String
            get() = exercise.exerciseName
    }
}

sealed class RoutineSaveAttempt {
    data class Valid(
        val routine: Routine,
        val blocks: List<RoutineBlock>
    ) : RoutineSaveAttempt()

    data class ValidationFailed(
        val updatedState: RoutineBuilderUiState
    ) : RoutineSaveAttempt()
}

data class RoutineBuilderUiState(
    val routineId: Long = 0L,
    val mainExerciseId: Long? = null,
    val defaultFormat: String = "",
    val routineName: String = "",
    val description: String = "",
    val isRoutineInfoExpanded: Boolean = true,
    val expandedBlockKey: String? = null,
    val blocks: List<RoutineBlockUi> = emptyList(),
    val nextKeyCounter: Int = 10,
    val initialSignature: String = "",
    val undoBuffer: DeletedRoutineItem? = null
) {
    val hasAtLeastOneExercise: Boolean
        get() = blocks.any { it.exercises.isNotEmpty() }

    val hasAnyAllZeroExercise: Boolean
        get() = blocks.any { b -> b.exercises.any { it.isAllZeroInvalid } }

    val canSave: Boolean
        get() = routineName.trim().isNotEmpty() && hasAtLeastOneExercise && !hasAnyAllZeroExercise

    val isDirty: Boolean
        get() = computeContentSignature() != initialSignature

    fun computeContentSignature(): String = buildString {
        append(routineName.trim()).append('|')
        append(description.trim()).append('|')
        blocks.forEach { b ->
            append(b.name.trim()).append(',')
            append(b.kind.name).append(',')
            append(b.format.trim()).append(',')
            append(b.setsCount).append(',')
            append(b.notes.trim()).append('[')
            b.exercises.forEach { ex ->
                append(ex.exerciseId).append(':')
                append(ex.targetMetric.name).append(':')
                append(ex.targetMode.name).append(':')
                append(ex.fixedValue).append(':')
                append(ex.perSetValues.joinToString(".")).append(';')
            }
            append(']')
        }
    }

    fun updateRoutineName(newName: String): RoutineBuilderUiState =
        copy(routineName = newName)

    fun updateDescription(newDescription: String): RoutineBuilderUiState =
        copy(description = newDescription)

    fun toggleRoutineInfo(expanded: Boolean? = null): RoutineBuilderUiState =
        copy(isRoutineInfoExpanded = expanded ?: !isRoutineInfoExpanded)

    fun collapseAll(): RoutineBuilderUiState =
        copy(
            isRoutineInfoExpanded = false,
            expandedBlockKey = null,
            blocks = blocks.map { it.copy(expandedExerciseKey = null) }
        )

    fun toggleBlockExpanded(blockKey: String): RoutineBuilderUiState {
        val willExpand = expandedBlockKey != blockKey
        val autoCollapseInfo = if (willExpand && routineName.isNotBlank()) false else isRoutineInfoExpanded
        return copy(
            isRoutineInfoExpanded = autoCollapseInfo,
            expandedBlockKey = if (willExpand) blockKey else null
        )
    }

    fun addBlock(): RoutineBuilderUiState {
        val newKey = "block_$nextKeyCounter"
        val blockNumber = blocks.size + 1
        val newBlock = RoutineBlockUi(
            uiKey = newKey,
            name = "Block $blockNumber",
            kind = BlockKind.WEIGHTLIFTING,
            format = "Sets x Reps",
            setsCount = 4,
            isSetupExpanded = true,
            isExercisesExpanded = true
        )
        return copy(
            isRoutineInfoExpanded = if (routineName.isNotBlank()) false else isRoutineInfoExpanded,
            expandedBlockKey = newKey,
            blocks = blocks + newBlock,
            nextKeyCounter = nextKeyCounter + 1
        )
    }

    fun updateBlockName(blockKey: String, newName: String): RoutineBuilderUiState =
        mapBlock(blockKey) { it.copy(name = newName) }

    fun selectBlockKind(blockKey: String, newKind: BlockKind): RoutineBuilderUiState =
        mapBlock(blockKey) { blk ->
            val primaries = RoutineTargetCodec.primaryFormatsForKind(newKind)
            val keepFormat = blk.format.isNotBlank()
            val resolvedFormat = if (keepFormat) blk.format else (primaries.firstOrNull() ?: "Sets x Reps")
            blk.copy(kind = newKind, format = resolvedFormat)
        }

    fun selectBlockFormat(blockKey: String, newFormat: String): RoutineBuilderUiState =
        mapBlock(blockKey) { blk ->
            val newSets = RoutineTargetCodec.defaultSetsForFormat(newFormat)
            val hint = if (RoutineTargetCodec.isSingleEffortFormat(newFormat)) {
                "Defaults to 1 set for $newFormat — tap + for multiple sets"
            } else {
                null
            }
            blk.copy(
                format = newFormat,
                setsCount = newSets,
                formatDefaultHint = hint,
                exercises = blk.exercises.map { ex ->
                    ex.copy(
                        perSetValues = RoutineTargetCodec.resizePerSetValues(
                            ex.perSetValues,
                            ex.fixedValue,
                            newSets
                        )
                    )
                }
            )
        }

    fun updateBlockSetsCount(blockKey: String, requestedSets: Int): RoutineBuilderUiState =
        mapBlock(blockKey) { blk ->
            val clamped = requestedSets.coerceIn(1, 100)
            blk.copy(
                setsCount = clamped,
                formatDefaultHint = null,
                exercises = blk.exercises.map { ex ->
                    ex.copy(
                        perSetValues = RoutineTargetCodec.resizePerSetValues(
                            ex.perSetValues,
                            ex.fixedValue,
                            clamped
                        )
                    )
                }
            )
        }

    fun toggleBlockSetup(blockKey: String, expanded: Boolean? = null): RoutineBuilderUiState =
        mapBlock(blockKey) { it.copy(isSetupExpanded = expanded ?: !it.isSetupExpanded) }

    fun toggleBlockExercises(blockKey: String, expanded: Boolean? = null): RoutineBuilderUiState =
        mapBlock(blockKey) { it.copy(isExercisesExpanded = expanded ?: !it.isExercisesExpanded) }

    fun updateBlockNotes(blockKey: String, notes: String): RoutineBuilderUiState =
        mapBlock(blockKey) { it.copy(notes = notes) }

    fun moveBlock(blockKey: String, delta: Int): RoutineBuilderUiState {
        val idx = blocks.indexOfFirst { it.uiKey == blockKey }
        if (idx < 0) return this
        val targetIdx = (idx + delta).coerceIn(0, blocks.lastIndex)
        if (targetIdx == idx) return this
        val mutable = blocks.toMutableList()
        val item = mutable.removeAt(idx)
        mutable.add(targetIdx, item)
        return copy(blocks = mutable)
    }

    fun removeBlock(blockKey: String): RoutineBuilderUiState {
        val idx = blocks.indexOfFirst { it.uiKey == blockKey }
        if (idx < 0) return this
        val removed = blocks[idx]
        val remaining = blocks.toMutableList().also { it.removeAt(idx) }
        val nextExpanded = if (expandedBlockKey == blockKey) {
            remaining.getOrNull(idx.coerceAtMost((remaining.size - 1).coerceAtLeast(0)))?.uiKey
        } else {
            expandedBlockKey
        }
        return copy(
            blocks = remaining,
            expandedBlockKey = nextExpanded,
            undoBuffer = DeletedRoutineItem.BlockItem(index = idx, block = removed)
        )
    }

    fun addExerciseToBlock(
        blockKey: String,
        exercise: Exercise,
        overrideMetric: ExerciseTargetMetric? = null
    ): RoutineBuilderUiState {
        val exKey = "ex_$nextKeyCounter"
        return copy(
            isRoutineInfoExpanded = if (routineName.isNotBlank()) false else isRoutineInfoExpanded,
            expandedBlockKey = blockKey,
            nextKeyCounter = nextKeyCounter + 1,
            blocks = blocks.map { blk ->
                if (blk.uiKey != blockKey) return@map blk
                val metric = overrideMetric ?: ExerciseTargetMetric.fromLibraryMetricType(exercise.metricType)
                val defVal = metric.defaultFixedValue
                val newEx = RoutineExerciseUi(
                    uiKey = exKey,
                    exerciseId = exercise.id,
                    exerciseName = exercise.name,
                    category = exercise.category,
                    targetMetric = metric,
                    targetMode = TargetMode.FIXED,
                    fixedValue = defVal,
                    perSetValues = List(blk.setsCount.coerceAtLeast(1)) { defVal }
                )
                blk.copy(
                    isSetupExpanded = false,
                    isExercisesExpanded = true,
                    expandedExerciseKey = exKey,
                    exercises = blk.exercises + newEx
                )
            }
        )
    }

    fun toggleExerciseExpanded(
        blockKey: String,
        exerciseKey: String,
        wantExpanded: Boolean? = null
    ): RoutineBuilderUiState = mapBlock(blockKey) { blk ->
        val currentlyExpanded = blk.expandedExerciseKey == exerciseKey
        val targetExpanded = wantExpanded ?: !currentlyExpanded
        if (!targetExpanded) {
            val ex = blk.exercises.firstOrNull { it.uiKey == exerciseKey }
            if (ex != null && ex.isAllZeroInvalid) {
                // Keep card open and show inline error guidance
                return@mapBlock blk.copy(
                    expandedExerciseKey = exerciseKey,
                    exercises = blk.exercises.map {
                        if (it.uiKey == exerciseKey) it.copy(showValidationError = true) else it
                    }
                )
            }
            blk.copy(expandedExerciseKey = if (currentlyExpanded) null else blk.expandedExerciseKey)
        } else {
            blk.copy(
                isSetupExpanded = false,
                isExercisesExpanded = true,
                expandedExerciseKey = exerciseKey
            )
        }
    }

    fun selectExerciseMetric(
        blockKey: String,
        exerciseKey: String,
        newMetric: ExerciseTargetMetric
    ): RoutineBuilderUiState = mapBlock(blockKey) { blk ->
        blk.copy(
            exercises = blk.exercises.map { ex ->
                if (ex.uiKey != exerciseKey || ex.targetMetric == newMetric) return@map ex
                val updatedCache = ex.cachedMetricValues + (
                    ex.targetMetric to MetricValueCache(
                        fixedValue = ex.fixedValue,
                        perSetValues = ex.perSetValues
                    )
                )
                val cached = updatedCache[newMetric]
                val nextFixed = cached?.fixedValue ?: newMetric.defaultFixedValue
                val nextPerSet = if (cached != null) {
                    RoutineTargetCodec.resizePerSetValues(cached.perSetValues, nextFixed, blk.setsCount)
                } else {
                    List(blk.setsCount.coerceAtLeast(1)) { nextFixed }
                }
                ex.copy(
                    targetMetric = newMetric,
                    fixedValue = nextFixed,
                    perSetValues = nextPerSet,
                    cachedMetricValues = updatedCache,
                    showValidationError = false
                )
            }
        )
    }

    fun selectExerciseTargetMode(
        blockKey: String,
        exerciseKey: String,
        newMode: TargetMode
    ): RoutineBuilderUiState = mapBlock(blockKey) { blk ->
        blk.copy(
            exercises = blk.exercises.map { ex ->
                if (ex.uiKey != exerciseKey || ex.targetMode == newMode) return@map ex
                val nextPerSet = if (newMode == TargetMode.PER_SET) {
                    val resized = RoutineTargetCodec.resizePerSetValues(ex.perSetValues, ex.fixedValue, blk.setsCount)
                    if (ex.perSetValues.isEmpty()) List(blk.setsCount.coerceAtLeast(1)) { ex.fixedValue } else resized
                } else {
                    ex.perSetValues
                }
                ex.copy(
                    targetMode = newMode,
                    perSetValues = nextPerSet,
                    showValidationError = false
                )
            }
        )
    }

    fun updateExerciseFixedValue(
        blockKey: String,
        exerciseKey: String,
        newValue: Int
    ): RoutineBuilderUiState = mapBlock(blockKey) { blk ->
        val clamped = newValue.coerceIn(0, 100_000)
        blk.copy(
            exercises = blk.exercises.map { ex ->
                if (ex.uiKey != exerciseKey) return@map ex
                ex.copy(
                    fixedValue = clamped,
                    perSetValues = List(blk.setsCount.coerceAtLeast(1)) { clamped },
                    showValidationError = if (clamped > 0) false else ex.showValidationError
                )
            }
        )
    }

    fun updateExerciseSetValue(
        blockKey: String,
        exerciseKey: String,
        setIndex: Int,
        newValue: Int
    ): RoutineBuilderUiState = mapBlock(blockKey) { blk ->
        val clamped = newValue.coerceIn(0, 100_000)
        blk.copy(
            exercises = blk.exercises.map { ex ->
                if (ex.uiKey != exerciseKey) return@map ex
                val normalized = RoutineTargetCodec.resizePerSetValues(ex.perSetValues, ex.fixedValue, blk.setsCount)
                    .toMutableList()
                if (setIndex in normalized.indices) {
                    normalized[setIndex] = clamped
                }
                val clearedError = if (normalized.any { it > 0 }) false else ex.showValidationError
                ex.copy(
                    perSetValues = normalized,
                    showValidationError = clearedError
                )
            }
        )
    }

    fun applyExerciseSetPattern(
        blockKey: String,
        exerciseKey: String,
        pattern: ExerciseSetPattern
    ): RoutineBuilderUiState = mapBlock(blockKey) { blk ->
        blk.copy(
            exercises = blk.exercises.map { ex ->
                if (ex.uiKey != exerciseKey) return@map ex
                val updatedValues = when (pattern) {
                    ExerciseSetPattern.COPY_FIRST -> RoutineTargetCodec.copyFirstSetToAll(
                        ex.perSetValues,
                        blk.setsCount,
                        ex.fixedValue
                    )
                    ExerciseSetPattern.DESCENDING -> RoutineTargetCodec.applyDescendingPattern(
                        ex.perSetValues,
                        ex.targetMetric,
                        blk.setsCount,
                        ex.fixedValue
                    )
                    ExerciseSetPattern.ALTERNATE_ZEROS -> {
                        val evenAlreadyZero = ex.perSetValues.size >= 2 &&
                            ex.perSetValues.filterIndexed { i, _ -> (i + 1) % 2 == 0 }.all { it == 0 }
                        RoutineTargetCodec.applyAlternatingZerosPattern(
                            values = ex.perSetValues,
                            setsCount = blk.setsCount,
                            fallbackFixed = ex.fixedValue,
                            zeroEvenSets = !evenAlreadyZero
                        )
                    }
                }
                ex.copy(
                    targetMode = TargetMode.PER_SET,
                    perSetValues = updatedValues,
                    showValidationError = false
                )
            }
        )
    }

    fun moveExercise(blockKey: String, exerciseKey: String, delta: Int): RoutineBuilderUiState =
        mapBlock(blockKey) { blk ->
            val idx = blk.exercises.indexOfFirst { it.uiKey == exerciseKey }
            if (idx < 0) return@mapBlock blk
            val targetIdx = (idx + delta).coerceIn(0, blk.exercises.lastIndex)
            if (targetIdx == idx) return@mapBlock blk
            val mutable = blk.exercises.toMutableList()
            val item = mutable.removeAt(idx)
            mutable.add(targetIdx, item)
            blk.copy(exercises = mutable)
        }

    fun removeExercise(blockKey: String, exerciseKey: String): RoutineBuilderUiState {
        var deleted: DeletedRoutineItem? = null
        val updatedBlocks = blocks.map { blk ->
            if (blk.uiKey != blockKey) return@map blk
            val idx = blk.exercises.indexOfFirst { it.uiKey == exerciseKey }
            if (idx < 0) return@map blk
            val removed = blk.exercises[idx]
            deleted = DeletedRoutineItem.ExerciseItem(blockKey = blockKey, index = idx, exercise = removed)
            val remaining = blk.exercises.toMutableList().also { it.removeAt(idx) }
            blk.copy(
                exercises = remaining,
                expandedExerciseKey = if (blk.expandedExerciseKey == exerciseKey) null else blk.expandedExerciseKey
            )
        }
        return copy(blocks = updatedBlocks, undoBuffer = deleted ?: undoBuffer)
    }

    fun undoLastRemoval(): RoutineBuilderUiState {
        val pending = undoBuffer ?: return this
        return when (pending) {
            is DeletedRoutineItem.BlockItem -> {
                val mutable = blocks.toMutableList()
                val insertAt = pending.index.coerceIn(0, mutable.size)
                mutable.add(insertAt, pending.block)
                copy(
                    blocks = mutable,
                    expandedBlockKey = pending.block.uiKey,
                    undoBuffer = null
                )
            }
            is DeletedRoutineItem.ExerciseItem -> {
                copy(
                    expandedBlockKey = pending.blockKey,
                    blocks = blocks.map { blk ->
                        if (blk.uiKey != pending.blockKey) return@map blk
                        val mutable = blk.exercises.toMutableList()
                        val insertAt = pending.index.coerceIn(0, mutable.size)
                        val resized = pending.exercise.copy(
                            perSetValues = RoutineTargetCodec.resizePerSetValues(
                                pending.exercise.perSetValues,
                                pending.exercise.fixedValue,
                                blk.setsCount
                            )
                        )
                        mutable.add(insertAt, resized)
                        blk.copy(
                            isExercisesExpanded = true,
                            expandedExerciseKey = resized.uiKey,
                            exercises = mutable
                        )
                    },
                    undoBuffer = null
                )
            }
        }
    }

    fun attemptSave(): RoutineSaveAttempt {
        val offendingBlock = blocks.firstOrNull { b -> b.exercises.any { it.isAllZeroInvalid } }
        if (offendingBlock != null) {
            val offendingEx = offendingBlock.exercises.first { it.isAllZeroInvalid }
            val flaggedState = copy(
                expandedBlockKey = offendingBlock.uiKey,
                blocks = blocks.map { blk ->
                    if (blk.uiKey != offendingBlock.uiKey) blk
                    else blk.copy(
                        isExercisesExpanded = true,
                        expandedExerciseKey = offendingEx.uiKey,
                        exercises = blk.exercises.map { ex ->
                            if (ex.isAllZeroInvalid) ex.copy(showValidationError = true) else ex
                        }
                    )
                }
            )
            return RoutineSaveAttempt.ValidationFailed(flaggedState)
        }

        if (!canSave) {
            return RoutineSaveAttempt.ValidationFailed(this)
        }

        val nonEmptyBlocks = blocks.filter { it.exercises.isNotEmpty() }.ifEmpty { blocks }
        val resolvedMainExId = mainExerciseId
            ?: nonEmptyBlocks.firstOrNull()?.exercises?.firstOrNull()?.exerciseId

        val routineEntity = Routine(
            id = routineId,
            name = routineName.trim(),
            description = description.trim(),
            mainExerciseId = resolvedMainExId,
            defaultFormat = defaultFormat.ifBlank { nonEmptyBlocks.firstOrNull()?.format.orEmpty() }
        )

        val blockEntities = nonEmptyBlocks.mapIndexed { idx, b ->
            val sets = b.setsCount.coerceAtLeast(1)
            val specs = b.exercises.map { it.toTargetSpec(sets) }
            RoutineBlock(
                id = 0L,
                routineId = routineId,
                position = idx,
                name = b.name.trim().ifBlank { "Block ${idx + 1}" },
                kind = b.kind,
                format = b.format.trim(),
                setsCount = sets,
                targetRepsScheme = RoutineTargetCodec.formatBlockTargetSummary(specs, sets),
                exerciseIdsCsv = b.exercises.joinToString(",") { it.exerciseId.toString() },
                notes = RoutineTargetCodec.encodeNotesWithTargets(b.notes, specs, sets),
                section = b.section,
                subBlock = b.subBlock
            )
        }

        return RoutineSaveAttempt.Valid(routineEntity, blockEntities)
    }

    private fun mapBlock(
        blockKey: String,
        transform: (RoutineBlockUi) -> RoutineBlockUi
    ): RoutineBuilderUiState = copy(
        blocks = blocks.map { if (it.uiKey == blockKey) transform(it) else it }
    )

    companion object {
        fun fromRoutine(
            original: RoutineWithBlocks?,
            exercises: List<Exercise>
        ): RoutineBuilderUiState {
            val exById = exercises.associateBy { it.id }
            var keySeq = 1

            if (original == null) {
                val firstKey = "block_${keySeq++}"
                val starter = RoutineBuilderUiState(
                    isRoutineInfoExpanded = true,
                    expandedBlockKey = firstKey,
                    blocks = listOf(
                        RoutineBlockUi(
                            uiKey = firstKey,
                            name = "Block 1",
                            kind = BlockKind.WEIGHTLIFTING,
                            format = "Sets x Reps",
                            setsCount = 4,
                            isSetupExpanded = true,
                            isExercisesExpanded = true
                        )
                    ),
                    nextKeyCounter = keySeq
                )
                return starter.copy(initialSignature = starter.computeContentSignature())
            }

            val sortedBlocks = original.blocks.sortedBy { it.position }
            val uiBlocks = if (sortedBlocks.isEmpty()) {
                val firstKey = "block_${keySeq++}"
                listOf(
                    RoutineBlockUi(
                        uiKey = firstKey,
                        name = "Block 1",
                        kind = BlockKind.WEIGHTLIFTING,
                        format = "Sets x Reps",
                        setsCount = 4
                    )
                )
            } else {
                sortedBlocks.mapIndexed { idx, blk ->
                    val bKey = "block_${keySeq++}"
                    val decoded = RoutineTargetCodec.decodeBlockExercises(blk, exById)
                    val resolvedSets = blk.setsCount.coerceAtLeast(
                        decoded.maxOfOrNull { it.second.perSetValues.size } ?: 1
                    )
                    val exItems = decoded.map { (exId, spec) ->
                        val exObj = exById[exId]
                        RoutineExerciseUi(
                            uiKey = "ex_${keySeq++}",
                            exerciseId = exId,
                            exerciseName = exObj?.name ?: "Exercise #$exId",
                            category = exObj?.category ?: ExerciseCategory.BARBELL,
                            targetMetric = spec.metric,
                            targetMode = spec.mode,
                            fixedValue = spec.fixedValue,
                            perSetValues = RoutineTargetCodec.resizePerSetValues(
                                spec.perSetValues,
                                spec.fixedValue,
                                resolvedSets
                            )
                        )
                    }
                    RoutineBlockUi(
                        uiKey = bKey,
                        name = blk.name.ifBlank { "Block ${idx + 1}" },
                        kind = blk.kind,
                        format = blk.format.ifBlank { "Sets x Reps" },
                        setsCount = resolvedSets,
                        isSetupExpanded = exItems.isEmpty(),
                        isExercisesExpanded = true,
                        exercises = exItems,
                        notes = RoutineTargetCodec.extractCleanNotes(blk.notes),
                        section = blk.section,
                        subBlock = blk.subBlock
                    )
                }
            }

            val state = RoutineBuilderUiState(
                routineId = original.routine.id,
                mainExerciseId = original.routine.mainExerciseId,
                defaultFormat = original.routine.defaultFormat,
                routineName = original.routine.name,
                description = original.routine.description,
                isRoutineInfoExpanded = original.routine.name.isBlank(),
                expandedBlockKey = uiBlocks.firstOrNull()?.uiKey,
                blocks = uiBlocks,
                nextKeyCounter = keySeq
            )
            return state.copy(initialSignature = state.computeContentSignature())
        }
    }
}

/**
 * Pure responsive column count resolver for the `Per-Set` target grid (`SPEC-ROUTINE-BUILDER-02` §9).
 */
internal fun resolvePerSetGridColumns(widthDp: Int, fontScale: Float): Int = when {
    fontScale >= 1.4f -> 2
    widthDp >= 600 -> 6
    else -> 4
}

internal fun blockKindEmoji(kind: BlockKind): String = when (kind) {
    BlockKind.WEIGHTLIFTING, BlockKind.STRENGTH -> "🏋️"
    BlockKind.METCON, BlockKind.METABOLIC -> "🔥"
    BlockKind.GYMNASTICS -> "🤸"
    BlockKind.ACCESSORY, BlockKind.HYPERTROPHY, BlockKind.SUPERSET -> "💪"
    BlockKind.CARDIO -> "🚣"
    BlockKind.COMPLEX -> "⛓️"
    BlockKind.WARMUP -> "⚡"
    BlockKind.OTHER -> "📋"
}

// ============================================================================
// Full-Height 4-Tier Collapsible Daily Routine Builder Modal Sheet
// ============================================================================

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun RoutineBuilderSheet(
    original: RoutineWithBlocks?,
    exercises: List<Exercise>,
    onDismiss: () -> Unit,
    onCreateAndAddExercise: (name: String, category: ExerciseCategory, onCreated: (Exercise) -> Unit) -> Unit,
    onSave: (Routine, List<RoutineBlock>) -> Unit
) {
    var uiState by remember(original) {
        mutableStateOf(RoutineBuilderUiState.fromRoutine(original, exercises))
    }
    var showDiscardDialog by remember { mutableStateOf(false) }

    val snackbarHostState = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    val haptics = LocalHapticFeedback.current
    val scrollState = rememberScrollState()
    var viewportHeightPx by remember { mutableIntStateOf(0) }
    val blockBoundsMap = remember { mutableMapOf<String, CardLayoutBounds>() }

    // Keep exercise names fresh if a newly created exercise flows into `exercises`
    LaunchedEffect(exercises) {
        val byId = exercises.associateBy { it.id }
        uiState = uiState.copy(
            blocks = uiState.blocks.map { blk ->
                blk.copy(
                    exercises = blk.exercises.map { ex ->
                        val lib = byId[ex.exerciseId]
                        if (lib != null && lib.name != ex.exerciseName) {
                            ex.copy(exerciseName = lib.name, category = lib.category)
                        } else ex
                    }
                )
            }
        )
    }

    var hasHandledInitialBlockFocus by remember(original) { mutableStateOf(false) }

    LaunchedEffect(uiState.expandedBlockKey) {
        if (!hasHandledInitialBlockFocus) {
            hasHandledInitialBlockFocus = true
            return@LaunchedEffect
        }
        val activeKey = uiState.expandedBlockKey ?: return@LaunchedEffect
        repeat(2) { withFrameNanos { } }
        val bounds = blockBoundsMap[activeKey] ?: return@LaunchedEffect
        val targetOffset = resolveGoalCardScrollOffset(
            cardTopPx = bounds.topPx,
            cardHeightPx = bounds.heightPx,
            viewportHeightPx = viewportHeightPx,
            currentScrollPx = scrollState.value,
            maxScrollPx = scrollState.maxValue
        )
        if (targetOffset != scrollState.value) {
            scrollState.animateScrollTo(targetOffset)
        }
    }

    val requestClose: () -> Unit = {
        if (uiState.isDirty) {
            showDiscardDialog = true
        } else {
            onDismiss()
        }
    }

    val showUndoForRemoval: (String) -> Unit = { removedLabel ->
        scope.launch {
            snackbarHostState.currentSnackbarData?.dismiss()
            val result = snackbarHostState.showSnackbar(
                message = "$removedLabel removed",
                actionLabel = "Undo",
                duration = SnackbarDuration.Short
            )
            if (result == SnackbarResult.ActionPerformed) {
                uiState = uiState.undoLastRemoval()
            }
        }
    }

    Dialog(
        onDismissRequest = requestClose,
        properties = DialogProperties(
            usePlatformDefaultWidth = false,
            decorFitsSystemWindows = true
        )
    ) {
        BackHandler(enabled = uiState.isDirty) {
            showDiscardDialog = true
        }

        Scaffold(
            modifier = Modifier
                .fillMaxSize()
                .imePadding(),
            contentWindowInsets = WindowInsets(0, 0, 0, 0),
            snackbarHost = { SnackbarHost(snackbarHostState) },
            topBar = {
                Surface(tonalElevation = 2.dp) {
                    Column {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 12.dp, vertical = 8.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text(
                                text = if (original == null) "New Daily Routine" else "Edit Daily Routine",
                                style = MaterialTheme.typography.titleLarge,
                                fontWeight = FontWeight.Bold
                            )
                            IconButton(
                                onClick = requestClose,
                                modifier = Modifier.size(48.dp)
                            ) {
                                Icon(Icons.Filled.Close, contentDescription = "Close routine builder")
                            }
                        }
                        HorizontalDivider()
                    }
                }
            },
            bottomBar = {
                Surface(tonalElevation = 3.dp) {
                    Column {
                        HorizontalDivider()
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 16.dp, vertical = 10.dp),
                            horizontalArrangement = Arrangement.spacedBy(12.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            OutlinedButton(
                                onClick = requestClose,
                                modifier = Modifier
                                    .weight(1f)
                                    .defaultMinSize(minHeight = 48.dp)
                            ) {
                                Text("Cancel")
                            }
                            Button(
                                enabled = uiState.canSave,
                                onClick = {
                                    when (val attempt = uiState.attemptSave()) {
                                        is RoutineSaveAttempt.Valid -> onSave(attempt.routine, attempt.blocks)
                                        is RoutineSaveAttempt.ValidationFailed -> {
                                            uiState = attempt.updatedState
                                        }
                                    }
                                },
                                modifier = Modifier
                                    .weight(1.4f)
                                    .defaultMinSize(minHeight = 48.dp)
                            ) {
                                Text("Save Routine", fontWeight = FontWeight.Bold)
                            }
                        }
                    }
                }
            }
        ) { innerPadding ->
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding)
                    .onGloballyPositioned { viewportHeightPx = it.size.height }
                    .verticalScroll(scrollState)
                    .padding(horizontal = 16.dp, vertical = 12.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                // Tier 1: Collapsible Routine Info Card
                RoutineInfoAccordionCard(
                    routineName = uiState.routineName,
                    description = uiState.description,
                    isExpanded = uiState.isRoutineInfoExpanded,
                    onToggle = { uiState = uiState.toggleRoutineInfo() },
                    onNameChange = { uiState = uiState.updateRoutineName(it) },
                    onDescriptionChange = { uiState = uiState.updateDescription(it) }
                )

                // Routine Blocks Header + Collapse All Action
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "Routine Blocks (${uiState.blocks.size})",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                    TextButton(
                        onClick = {
                            haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                            uiState = uiState.collapseAll()
                        },
                        modifier = Modifier.defaultMinSize(minHeight = 48.dp)
                    ) {
                        Icon(Icons.Filled.UnfoldLess, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(4.dp))
                        Text("Collapse All")
                    }
                }

                // Tier 2: Collapsible Routine Blocks (Exclusive Expansion)
                uiState.blocks.forEachIndexed { index, block ->
                    val isBlockExpanded = uiState.expandedBlockKey == block.uiKey
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .onGloballyPositioned { coords ->
                                blockBoundsMap[block.uiKey] = CardLayoutBounds(
                                    topPx = coords.positionInParent().y.toInt(),
                                    heightPx = coords.size.height
                                )
                            }
                    ) {
                        RoutineBlockAccordionCard(
                            blockIndex = index,
                            totalBlocks = uiState.blocks.size,
                            block = block,
                            isExpanded = isBlockExpanded,
                            allExercises = exercises,
                            onToggleExpand = { uiState = uiState.toggleBlockExpanded(block.uiKey) },
                            onUpdateName = { uiState = uiState.updateBlockName(block.uiKey, it) },
                            onSelectKind = { kind ->
                                haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                uiState = uiState.selectBlockKind(block.uiKey, kind)
                            },
                            onSelectFormat = { fmt ->
                                haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                uiState = uiState.selectBlockFormat(block.uiKey, fmt)
                            },
                            onUpdateSetsCount = { count ->
                                haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                uiState = uiState.updateBlockSetsCount(block.uiKey, count)
                            },
                            onToggleSetup = { uiState = uiState.toggleBlockSetup(block.uiKey) },
                            onToggleExercises = { uiState = uiState.toggleBlockExercises(block.uiKey) },
                            onUpdateNotes = { uiState = uiState.updateBlockNotes(block.uiKey, it) },
                            onMoveBlock = { delta -> uiState = uiState.moveBlock(block.uiKey, delta) },
                            onDeleteBlock = {
                                val label = block.name.ifBlank { "Block ${index + 1}" }
                                uiState = uiState.removeBlock(block.uiKey)
                                showUndoForRemoval(label)
                            },
                            onAddExistingExercise = { ex ->
                                haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                uiState = uiState.addExerciseToBlock(block.uiKey, ex)
                            },
                            onCreateAndAddExercise = { query, cat ->
                                onCreateAndAddExercise(query, cat) { created ->
                                    uiState = uiState.addExerciseToBlock(
                                        blockKey = block.uiKey,
                                        exercise = created,
                                        overrideMetric = ExerciseTargetMetric.REPS
                                    )
                                }
                            },
                            onToggleExerciseExpanded = { exKey, wantExp ->
                                uiState = uiState.toggleExerciseExpanded(block.uiKey, exKey, wantExp)
                            },
                            onSelectExerciseMetric = { exKey, metric ->
                                haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                uiState = uiState.selectExerciseMetric(block.uiKey, exKey, metric)
                            },
                            onSelectExerciseMode = { exKey, mode ->
                                haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                uiState = uiState.selectExerciseTargetMode(block.uiKey, exKey, mode)
                            },
                            onUpdateExerciseFixed = { exKey, value ->
                                haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                uiState = uiState.updateExerciseFixedValue(block.uiKey, exKey, value)
                            },
                            onUpdateExerciseSet = { exKey, setIdx, value ->
                                haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                uiState = uiState.updateExerciseSetValue(block.uiKey, exKey, setIdx, value)
                            },
                            onApplyExercisePattern = { exKey, pattern ->
                                haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                uiState = uiState.applyExerciseSetPattern(block.uiKey, exKey, pattern)
                            },
                            onMoveExercise = { exKey, delta ->
                                uiState = uiState.moveExercise(block.uiKey, exKey, delta)
                            },
                            onDeleteExercise = { exKey, exName ->
                                uiState = uiState.removeExercise(block.uiKey, exKey)
                                showUndoForRemoval(exName)
                            }
                        )
                    }
                }

                OutlinedButton(
                    onClick = {
                        haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                        uiState = uiState.addBlock()
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .defaultMinSize(minHeight = 48.dp),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Icon(Icons.Filled.Add, contentDescription = null)
                    Spacer(Modifier.width(6.dp))
                    Text("Add Another Block", fontWeight = FontWeight.SemiBold)
                }

                Spacer(Modifier.height(24.dp))
            }
        }

        if (showDiscardDialog) {
            AlertDialog(
                onDismissRequest = { showDiscardDialog = false },
                title = { Text("Discard unsaved routine?") },
                text = { Text("Your unsaved routine changes will be lost if you close the builder now.") },
                confirmButton = {
                    TextButton(
                        onClick = {
                            showDiscardDialog = false
                            onDismiss()
                        }
                    ) {
                        Text("Discard", color = MaterialTheme.colorScheme.error, fontWeight = FontWeight.Bold)
                    }
                },
                dismissButton = {
                    TextButton(onClick = { showDiscardDialog = false }) {
                        Text("Keep Editing")
                    }
                }
            )
        }
    }
}

@Composable
private fun RoutineInfoAccordionCard(
    routineName: String,
    description: String,
    isExpanded: Boolean,
    onToggle: () -> Unit,
    onNameChange: (String) -> Unit,
    onDescriptionChange: (String) -> Unit
) {
    ElevatedCard(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(14.dp)
    ) {
        Column(modifier = Modifier.fillMaxWidth()) {
            if (!isExpanded) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .defaultMinSize(minHeight = 56.dp)
                        .semantics(mergeDescendants = true) {
                            expand { onToggle(); true }
                        }
                        .clickable(onClick = onToggle)
                        .padding(horizontal = 14.dp, vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = if (routineName.isBlank()) "Routine Info: Tap to name workout"
                            else "Routine Info: $routineName",
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.Bold,
                            color = if (routineName.isBlank()) MaterialTheme.colorScheme.error
                            else MaterialTheme.colorScheme.onSurface,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        if (description.isNotBlank()) {
                            Text(
                                text = description,
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    }
                    Icon(Icons.Filled.ExpandMore, contentDescription = "Expand routine info")
                }
            }

            AnimatedVisibility(
                visible = isExpanded,
                enter = expandVertically() + fadeIn(),
                exit = shrinkVertically() + fadeOut()
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(14.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "Routine Info",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold
                        )
                        TextButton(
                            onClick = onToggle,
                            modifier = Modifier.defaultMinSize(minHeight = 48.dp)
                        ) {
                            Text("Done ▲", fontWeight = FontWeight.SemiBold)
                        }
                    }

                    OutlinedTextField(
                        value = routineName,
                        onValueChange = onNameChange,
                        label = { Text("Routine / Workout Name") },
                        placeholder = { Text("e.g. Monday Snatch & Conditioning") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )

                    OutlinedTextField(
                        value = description,
                        onValueChange = onDescriptionChange,
                        label = { Text("Description / Workout Goal (optional)") },
                        placeholder = { Text("Stimulus, RPE target, or time domain") },
                        maxLines = 3,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun RoutineBlockAccordionCard(
    blockIndex: Int,
    totalBlocks: Int,
    block: RoutineBlockUi,
    isExpanded: Boolean,
    allExercises: List<Exercise>,
    onToggleExpand: () -> Unit,
    onUpdateName: (String) -> Unit,
    onSelectKind: (BlockKind) -> Unit,
    onSelectFormat: (String) -> Unit,
    onUpdateSetsCount: (Int) -> Unit,
    onToggleSetup: () -> Unit,
    onToggleExercises: () -> Unit,
    onUpdateNotes: (String) -> Unit,
    onMoveBlock: (Int) -> Unit,
    onDeleteBlock: () -> Unit,
    onAddExistingExercise: (Exercise) -> Unit,
    onCreateAndAddExercise: (String, ExerciseCategory) -> Unit,
    onToggleExerciseExpanded: (String, Boolean?) -> Unit,
    onSelectExerciseMetric: (String, ExerciseTargetMetric) -> Unit,
    onSelectExerciseMode: (String, TargetMode) -> Unit,
    onUpdateExerciseFixed: (String, Int) -> Unit,
    onUpdateExerciseSet: (String, Int, Int) -> Unit,
    onApplyExercisePattern: (String, ExerciseSetPattern) -> Unit,
    onMoveExercise: (String, Int) -> Unit,
    onDeleteExercise: (String, String) -> Unit
) {
    val displayTitle = block.name.ifBlank { "Block ${blockIndex + 1}" }
    val emoji = blockKindEmoji(block.kind)
    val setsBadge = "${block.setsCount} ${if (block.setsCount == 1) "Set" else "Sets"}"
    val exCountBadge = "${block.exercises.size} Ex"

    OutlinedCard(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(14.dp),
        border = BorderStroke(
            width = if (isExpanded) 1.5.dp else 1.dp,
            color = if (isExpanded) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant
        )
    ) {
        Column(modifier = Modifier.fillMaxWidth()) {
            if (!isExpanded) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .defaultMinSize(minHeight = 60.dp)
                        .semantics(mergeDescendants = true) {
                            expand { onToggleExpand(); true }
                            customActions = buildList {
                                if (blockIndex > 0) {
                                    add(CustomAccessibilityAction("Move block up") { onMoveBlock(-1); true })
                                }
                                if (blockIndex < totalBlocks - 1) {
                                    add(CustomAccessibilityAction("Move block down") { onMoveBlock(1); true })
                                }
                            }
                        }
                        .clickable(onClick = onToggleExpand)
                        .padding(start = 14.dp, end = 6.dp, top = 8.dp, bottom = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Column(
                        modifier = Modifier.weight(1f),
                        verticalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        Text(
                            text = "$emoji ${blockIndex + 1}. $displayTitle",
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.Bold,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        FlowRow(
                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                            verticalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            SummaryBadgePill(
                                text = block.format.ifBlank { block.kind.shortLabel },
                                containerColor = MaterialTheme.colorScheme.secondaryContainer,
                                contentColor = MaterialTheme.colorScheme.onSecondaryContainer
                            )
                            SummaryBadgePill(
                                text = setsBadge,
                                containerColor = MaterialTheme.colorScheme.tertiaryContainer,
                                contentColor = MaterialTheme.colorScheme.onTertiaryContainer
                            )
                            SummaryBadgePill(
                                text = exCountBadge,
                                containerColor = MaterialTheme.colorScheme.surfaceVariant,
                                contentColor = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }

                    Row(verticalAlignment = Alignment.CenterVertically) {
                        IconButton(
                            onClick = onDeleteBlock,
                            modifier = Modifier.size(48.dp)
                        ) {
                            Icon(
                                Icons.Filled.Delete,
                                contentDescription = "Delete $displayTitle",
                                tint = MaterialTheme.colorScheme.error
                            )
                        }
                        Icon(
                            Icons.Filled.ExpandMore,
                            contentDescription = "Expand $displayTitle",
                            modifier = Modifier.padding(end = 6.dp)
                        )
                    }
                }
            }

            AnimatedVisibility(
                visible = isExpanded,
                enter = expandVertically() + fadeIn(),
                exit = shrinkVertically() + fadeOut()
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(12.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    // Expanded Block Header
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(
                            text = "$emoji Block ${blockIndex + 1}",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.weight(1f)
                        )

                        Row(verticalAlignment = Alignment.CenterVertically) {
                            if (totalBlocks > 1) {
                                IconButton(
                                    onClick = { onMoveBlock(-1) },
                                    enabled = blockIndex > 0,
                                    modifier = Modifier.size(48.dp)
                                ) {
                                    Icon(Icons.Filled.ArrowUpward, contentDescription = "Move block up")
                                }
                                IconButton(
                                    onClick = { onMoveBlock(1) },
                                    enabled = blockIndex < totalBlocks - 1,
                                    modifier = Modifier.size(48.dp)
                                ) {
                                    Icon(Icons.Filled.ArrowDownward, contentDescription = "Move block down")
                                }
                            }
                            TextButton(
                                onClick = onToggleExpand,
                                modifier = Modifier.defaultMinSize(minHeight = 48.dp)
                            ) {
                                Text("Done ▲", fontWeight = FontWeight.SemiBold)
                            }
                            IconButton(
                                onClick = onDeleteBlock,
                                modifier = Modifier.size(48.dp)
                            ) {
                                Icon(
                                    Icons.Filled.Delete,
                                    contentDescription = "Delete $displayTitle",
                                    tint = MaterialTheme.colorScheme.error
                                )
                            }
                        }
                    }

                    OutlinedTextField(
                        value = block.name,
                        onValueChange = onUpdateName,
                        label = { Text("Block Name") },
                        placeholder = { Text("e.g. Mixed Conditioning or Snatch Waves") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )

                    // Tier 3A: Collapsible Block Setup Sub-Accordion
                    BlockSetupSubAccordion(
                        block = block,
                        onToggle = onToggleSetup,
                        onSelectKind = onSelectKind,
                        onSelectFormat = onSelectFormat,
                        onUpdateSetsCount = onUpdateSetsCount,
                        onUpdateNotes = onUpdateNotes
                    )

                    // Tier 3B & Tier 4: Collapsible Exercises Section + Exercise Accordion Cards
                    BlockExercisesSubAccordion(
                        block = block,
                        allExercises = allExercises,
                        onToggleSection = onToggleExercises,
                        onAddExistingExercise = onAddExistingExercise,
                        onCreateAndAddExercise = onCreateAndAddExercise,
                        onToggleExerciseExpanded = onToggleExerciseExpanded,
                        onSelectExerciseMetric = onSelectExerciseMetric,
                        onSelectExerciseMode = onSelectExerciseMode,
                        onUpdateExerciseFixed = onUpdateExerciseFixed,
                        onUpdateExerciseSet = onUpdateExerciseSet,
                        onApplyExercisePattern = onApplyExercisePattern,
                        onMoveExercise = onMoveExercise,
                        onDeleteExercise = onDeleteExercise
                    )
                }
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun BlockSetupSubAccordion(
    block: RoutineBlockUi,
    onToggle: () -> Unit,
    onSelectKind: (BlockKind) -> Unit,
    onSelectFormat: (String) -> Unit,
    onUpdateSetsCount: (Int) -> Unit,
    onUpdateNotes: (String) -> Unit
) {
    var showMoreKindsMenu by remember { mutableStateOf(false) }
    var showMoreFormatsMenu by remember { mutableStateOf(false) }
    var showNotesField by remember(block.uiKey) { mutableStateOf(block.notes.isNotBlank()) }

    val setsSummary = "${block.setsCount} ${if (block.setsCount == 1) "Set" else "Sets"}"

    Surface(
        shape = RoundedCornerShape(12.dp),
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.fillMaxWidth()) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .defaultMinSize(minHeight = 48.dp)
                    .clickable(onClick = onToggle)
                    .padding(horizontal = 12.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    text = if (block.isSetupExpanded) "Block Setup"
                    else "Block Setup: ${block.kind.shortLabel} • ${block.format} • $setsSummary",
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.weight(1f),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Icon(
                    if (block.isSetupExpanded) Icons.Filled.ExpandLess else Icons.Filled.ExpandMore,
                    contentDescription = if (block.isSetupExpanded) "Collapse block setup" else "Expand block setup"
                )
            }

            AnimatedVisibility(
                visible = block.isSetupExpanded,
                enter = expandVertically() + fadeIn(),
                exit = shrinkVertically() + fadeOut()
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(start = 12.dp, end = 12.dp, bottom = 12.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    // 1. Visual Block Type Chips
                    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Text(
                            text = "Block Type",
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = FontWeight.SemiBold,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        FlowRow(
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            RoutineTargetCodec.PRIMARY_BLOCK_KINDS.forEach { kind ->
                                FilterChip(
                                    selected = block.kind == kind,
                                    onClick = { onSelectKind(kind) },
                                    label = { Text("${blockKindEmoji(kind)} ${kind.shortLabel}") },
                                    modifier = Modifier.defaultMinSize(minHeight = 48.dp)
                                )
                            }
                            if (block.kind !in RoutineTargetCodec.PRIMARY_BLOCK_KINDS) {
                                FilterChip(
                                    selected = true,
                                    onClick = { showMoreKindsMenu = true },
                                    label = { Text("${blockKindEmoji(block.kind)} ${block.kind.shortLabel}") },
                                    modifier = Modifier.defaultMinSize(minHeight = 48.dp)
                                )
                            }
                            Box {
                                FilterChip(
                                    selected = false,
                                    onClick = { showMoreKindsMenu = true },
                                    label = { Text("More ▾") },
                                    modifier = Modifier.defaultMinSize(minHeight = 48.dp)
                                )
                                DropdownMenu(
                                    expanded = showMoreKindsMenu,
                                    onDismissRequest = { showMoreKindsMenu = false }
                                ) {
                                    RoutineTargetCodec.OVERFLOW_BLOCK_KINDS.forEach { overflowKind ->
                                        DropdownMenuItem(
                                            text = { Text("${blockKindEmoji(overflowKind)} ${overflowKind.label}") },
                                            onClick = {
                                                showMoreKindsMenu = false
                                                onSelectKind(overflowKind)
                                            }
                                        )
                                    }
                                }
                            }
                        }
                    }

                    // 2. Contextual Execution Format Chips
                    val primaryFormats = remember(block.kind) {
                        RoutineTargetCodec.primaryFormatsForKind(block.kind)
                    }
                    val overflowFormats = remember(block.kind) {
                        RoutineTargetCodec.overflowFormatsForKind(block.kind)
                    }

                    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Text(
                            text = "Execution Format",
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = FontWeight.SemiBold,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        FlowRow(
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            primaryFormats.forEach { fmt ->
                                FilterChip(
                                    selected = block.format.equals(fmt, ignoreCase = true),
                                    onClick = { onSelectFormat(fmt) },
                                    label = { Text(fmt) },
                                    modifier = Modifier.defaultMinSize(minHeight = 48.dp)
                                )
                            }
                            if (block.format.isNotBlank() && primaryFormats.none { it.equals(block.format, ignoreCase = true) }) {
                                FilterChip(
                                    selected = true,
                                    onClick = { showMoreFormatsMenu = true },
                                    label = { Text(block.format) },
                                    modifier = Modifier.defaultMinSize(minHeight = 48.dp)
                                )
                            }
                            if (overflowFormats.isNotEmpty()) {
                                Box {
                                    FilterChip(
                                        selected = false,
                                        onClick = { showMoreFormatsMenu = true },
                                        label = { Text("More ▾") },
                                        modifier = Modifier.defaultMinSize(minHeight = 48.dp)
                                    )
                                    DropdownMenu(
                                        expanded = showMoreFormatsMenu,
                                        onDismissRequest = { showMoreFormatsMenu = false }
                                    ) {
                                        overflowFormats.forEach { fmt ->
                                            DropdownMenuItem(
                                                text = { Text(fmt) },
                                                onClick = {
                                                    showMoreFormatsMenu = false
                                                    onSelectFormat(fmt)
                                                }
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }

                    // 3. Fixed Integer Block Sets Stepper + Quick-Select Pills
                    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Text(
                            text = "Block Sets (Fixed Integer)",
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = FontWeight.SemiBold,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            OutlinedButton(
                                onClick = { onUpdateSetsCount(block.setsCount - 1) },
                                enabled = block.setsCount > 1,
                                modifier = Modifier.size(48.dp),
                                contentPadding = PaddingValues(0.dp)
                            ) {
                                Icon(Icons.Filled.Remove, contentDescription = "Decrease block sets")
                            }

                            Text(
                                text = setsSummary,
                                style = MaterialTheme.typography.titleMedium.copy(fontFeatureSettings = "tnum"),
                                fontWeight = FontWeight.Bold,
                                textAlign = TextAlign.Center,
                                modifier = Modifier.weight(1f)
                            )

                            OutlinedButton(
                                onClick = { onUpdateSetsCount(block.setsCount + 1) },
                                modifier = Modifier.size(48.dp),
                                contentPadding = PaddingValues(0.dp)
                            ) {
                                Icon(Icons.Filled.Add, contentDescription = "Increase block sets")
                            }
                        }

                        FlowRow(
                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                            verticalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            RoutineTargetCodec.BLOCK_SET_QUICK_PILLS.forEach { presetCount ->
                                FilterChip(
                                    selected = block.setsCount == presetCount,
                                    onClick = { onUpdateSetsCount(presetCount) },
                                    label = { Text("$presetCount") },
                                    modifier = Modifier.defaultMinSize(minWidth = 48.dp, minHeight = 48.dp)
                                )
                            }
                        }

                        if (block.formatDefaultHint != null) {
                            Text(
                                text = block.formatDefaultHint,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.primary
                            )
                        }
                    }

                    // Optional Coaching Notes
                    if (!showNotesField && block.notes.isBlank()) {
                        TextButton(
                            onClick = { showNotesField = true },
                            modifier = Modifier.defaultMinSize(minHeight = 48.dp)
                        ) {
                            Icon(Icons.Filled.Add, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(Modifier.width(4.dp))
                            Text("Add Block Coaching Notes")
                        }
                    } else {
                        OutlinedTextField(
                            value = block.notes,
                            onValueChange = onUpdateNotes,
                            label = { Text("Coaching Notes (optional)") },
                            placeholder = { Text("Percentages, rest cues, or pacing instructions") },
                            maxLines = 3,
                            modifier = Modifier.fillMaxWidth()
                        )
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun BlockExercisesSubAccordion(
    block: RoutineBlockUi,
    allExercises: List<Exercise>,
    onToggleSection: () -> Unit,
    onAddExistingExercise: (Exercise) -> Unit,
    onCreateAndAddExercise: (String, ExerciseCategory) -> Unit,
    onToggleExerciseExpanded: (String, Boolean?) -> Unit,
    onSelectExerciseMetric: (String, ExerciseTargetMetric) -> Unit,
    onSelectExerciseMode: (String, TargetMode) -> Unit,
    onUpdateExerciseFixed: (String, Int) -> Unit,
    onUpdateExerciseSet: (String, Int, Int) -> Unit,
    onApplyExercisePattern: (String, ExerciseSetPattern) -> Unit,
    onMoveExercise: (String, Int) -> Unit,
    onDeleteExercise: (String, String) -> Unit
) {
    var searchQuery by remember(block.uiKey) { mutableStateOf("") }
    var selectedCategory by remember(block.uiKey) { mutableStateOf<ExerciseCategory?>(null) }

    val trimmedQuery = searchQuery.trim()
    val filteredLibrary = remember(allExercises, trimmedQuery, selectedCategory) {
        if (trimmedQuery.isEmpty() && selectedCategory == null) {
            emptyList()
        } else {
            allExercises.filter { ex ->
                val catOk = selectedCategory == null || ex.category == selectedCategory
                val queryOk = trimmedQuery.isEmpty() || ex.name.contains(trimmedQuery, ignoreCase = true)
                catOk && queryOk
            }.take(8)
        }
    }
    val exactMatchExists = remember(allExercises, trimmedQuery) {
        trimmedQuery.isNotEmpty() && allExercises.any { it.name.trim().equals(trimmedQuery, ignoreCase = true) }
    }

    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .defaultMinSize(minHeight = 48.dp)
                .clickable(onClick = onToggleSection),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text(
                text = "Exercises (${block.exercises.size})",
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.Bold
            )
            Icon(
                if (block.isExercisesExpanded) Icons.Filled.ExpandLess else Icons.Filled.ExpandMore,
                contentDescription = if (block.isExercisesExpanded) "Collapse exercises" else "Expand exercises"
            )
        }

        AnimatedVisibility(
            visible = block.isExercisesExpanded,
            enter = expandVertically() + fadeIn(),
            exit = shrinkVertically() + fadeOut()
        ) {
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                // Unified Inline Search & Custom Exercise Creation Combobox
                OutlinedTextField(
                    value = searchQuery,
                    onValueChange = { searchQuery = it },
                    label = { Text("Search or add exercise...") },
                    leadingIcon = { Icon(Icons.Filled.Search, contentDescription = null) },
                    trailingIcon = {
                        if (searchQuery.isNotEmpty()) {
                            IconButton(
                                onClick = { searchQuery = "" },
                                modifier = Modifier.size(48.dp)
                            ) {
                                Icon(Icons.Filled.Close, contentDescription = "Clear search")
                            }
                        }
                    },
                    singleLine = true,
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier.fillMaxWidth()
                )

                LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    item {
                        FilterChip(
                            selected = selectedCategory == null,
                            onClick = { selectedCategory = null },
                            label = { Text("All") },
                            modifier = Modifier.defaultMinSize(minHeight = 48.dp)
                        )
                    }
                    items(ExerciseCategory.entries) { cat ->
                        FilterChip(
                            selected = selectedCategory == cat,
                            onClick = {
                                selectedCategory = if (selectedCategory == cat) null else cat
                            },
                            label = { Text(cat.label) },
                            modifier = Modifier.defaultMinSize(minHeight = 48.dp)
                        )
                    }
                }

                if (trimmedQuery.isNotEmpty() && !exactMatchExists) {
                    OutlinedCard(
                        modifier = Modifier
                            .fillMaxWidth()
                            .defaultMinSize(minHeight = 48.dp)
                            .clickable {
                                val creationCat = selectedCategory ?: ExerciseCategory.BARBELL
                                val nameToCreate = trimmedQuery
                                searchQuery = ""
                                onCreateAndAddExercise(nameToCreate, creationCat)
                            },
                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary)
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 12.dp, vertical = 12.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Icon(
                                Icons.Filled.Add,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary
                            )
                            Text(
                                text = "Create & Add \"$trimmedQuery\"",
                                style = MaterialTheme.typography.bodyMedium,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.primary
                            )
                        }
                    }
                }

                if (filteredLibrary.isNotEmpty()) {
                    Surface(
                        shape = RoundedCornerShape(10.dp),
                        tonalElevation = 1.dp,
                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(modifier = Modifier.fillMaxWidth()) {
                            filteredLibrary.forEachIndexed { idx, candidate ->
                                if (idx > 0) HorizontalDivider()
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .defaultMinSize(minHeight = 48.dp)
                                        .clickable {
                                            searchQuery = ""
                                            onAddExistingExercise(candidate)
                                        }
                                        .padding(horizontal = 12.dp, vertical = 10.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(
                                            text = candidate.name,
                                            style = MaterialTheme.typography.bodyMedium,
                                            fontWeight = FontWeight.SemiBold
                                        )
                                        Text(
                                            text = "${candidate.category.label} · ${ExerciseTargetMetric.fromLibraryMetricType(candidate.metricType).label}",
                                            style = MaterialTheme.typography.labelSmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }
                                    Icon(
                                        Icons.Filled.Add,
                                        contentDescription = "Add ${candidate.name}",
                                        tint = MaterialTheme.colorScheme.primary
                                    )
                                }
                            }
                        }
                    }
                }

                if (block.exercises.isEmpty()) {
                    Surface(
                        shape = RoundedCornerShape(10.dp),
                        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(
                            text = "Search above or tap a category chip to add movements to this block.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(14.dp)
                        )
                    }
                } else {
                    block.exercises.forEachIndexed { exIdx, exerciseUi ->
                        ExerciseAccordionCard(
                            index = exIdx,
                            totalExercises = block.exercises.size,
                            setsCount = block.setsCount,
                            exercise = exerciseUi,
                            isExpanded = block.expandedExerciseKey == exerciseUi.uiKey,
                            onToggleExpand = { wantExp -> onToggleExerciseExpanded(exerciseUi.uiKey, wantExp) },
                            onSelectMetric = { metric -> onSelectExerciseMetric(exerciseUi.uiKey, metric) },
                            onSelectMode = { mode -> onSelectExerciseMode(exerciseUi.uiKey, mode) },
                            onUpdateFixed = { v -> onUpdateExerciseFixed(exerciseUi.uiKey, v) },
                            onUpdateSet = { setIdx, v -> onUpdateExerciseSet(exerciseUi.uiKey, setIdx, v) },
                            onApplyPattern = { pat -> onApplyExercisePattern(exerciseUi.uiKey, pat) },
                            onMove = { delta -> onMoveExercise(exerciseUi.uiKey, delta) },
                            onDelete = { onDeleteExercise(exerciseUi.uiKey, exerciseUi.exerciseName) }
                        )
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun ExerciseAccordionCard(
    index: Int,
    totalExercises: Int,
    setsCount: Int,
    exercise: RoutineExerciseUi,
    isExpanded: Boolean,
    onToggleExpand: (Boolean?) -> Unit,
    onSelectMetric: (ExerciseTargetMetric) -> Unit,
    onSelectMode: (TargetMode) -> Unit,
    onUpdateFixed: (Int) -> Unit,
    onUpdateSet: (Int, Int) -> Unit,
    onApplyPattern: (ExerciseSetPattern) -> Unit,
    onMove: (Int) -> Unit,
    onDelete: () -> Unit
) {
    val density = LocalDensity.current
    val fontScale = density.fontScale
    val hasError = exercise.showValidationError && exercise.isAllZeroInvalid
    val badgeText = exercise.summaryBadge(setsCount)

    var editingFixedDigits by remember(exercise.uiKey, exercise.targetMetric) { mutableStateOf(false) }
    var fixedInputText by remember(exercise.fixedValue) { mutableStateOf(exercise.fixedValue.toString()) }

    OutlinedCard(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
        border = BorderStroke(
            width = if (hasError || isExpanded) 1.5.dp else 1.dp,
            color = when {
                hasError -> MaterialTheme.colorScheme.error
                isExpanded -> MaterialTheme.colorScheme.primary
                else -> MaterialTheme.colorScheme.outlineVariant
            }
        ),
        colors = CardDefaults.outlinedCardColors(
            containerColor = if (hasError) {
                MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.2f)
            } else {
                MaterialTheme.colorScheme.surface
            }
        )
    ) {
        Column(modifier = Modifier.fillMaxWidth()) {
            if (!isExpanded) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .defaultMinSize(minHeight = 52.dp)
                        .semantics(mergeDescendants = true) {
                            expand { onToggleExpand(true); true }
                            customActions = buildList {
                                if (index > 0) {
                                    add(CustomAccessibilityAction("Move exercise up") { onMove(-1); true })
                                }
                                if (index < totalExercises - 1) {
                                    add(CustomAccessibilityAction("Move exercise down") { onMove(1); true })
                                }
                            }
                        }
                        .clickable { onToggleExpand(true) }
                        .padding(start = 12.dp, end = 4.dp, top = 6.dp, bottom = 6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Column(
                        modifier = Modifier.weight(1f),
                        verticalArrangement = Arrangement.spacedBy(3.dp)
                    ) {
                        Text(
                            text = "${index + 1}. ${exercise.exerciseName}",
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.Bold,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        SummaryBadgePill(
                            text = badgeText,
                            containerColor = if (exercise.isAllZeroInvalid) MaterialTheme.colorScheme.errorContainer
                            else MaterialTheme.colorScheme.primaryContainer,
                            contentColor = if (exercise.isAllZeroInvalid) MaterialTheme.colorScheme.onErrorContainer
                            else MaterialTheme.colorScheme.onPrimaryContainer
                        )
                    }

                    Row(verticalAlignment = Alignment.CenterVertically) {
                        if (totalExercises > 1) {
                            IconButton(
                                onClick = { onMove(-1) },
                                enabled = index > 0,
                                modifier = Modifier.size(48.dp)
                            ) {
                                Icon(Icons.Filled.ArrowUpward, contentDescription = "Move ${exercise.exerciseName} up", modifier = Modifier.size(18.dp))
                            }
                            IconButton(
                                onClick = { onMove(1) },
                                enabled = index < totalExercises - 1,
                                modifier = Modifier.size(48.dp)
                            ) {
                                Icon(Icons.Filled.ArrowDownward, contentDescription = "Move ${exercise.exerciseName} down", modifier = Modifier.size(18.dp))
                            }
                        }
                        IconButton(
                            onClick = onDelete,
                            modifier = Modifier.size(48.dp)
                        ) {
                            Icon(
                                Icons.Filled.Delete,
                                contentDescription = "Remove ${exercise.exerciseName}",
                                tint = MaterialTheme.colorScheme.error,
                                modifier = Modifier.size(18.dp)
                            )
                        }
                        Icon(
                            Icons.Filled.ExpandMore,
                            contentDescription = "Expand ${exercise.exerciseName}",
                            modifier = Modifier.padding(end = 6.dp)
                        )
                    }
                }
            }

            AnimatedVisibility(
                visible = isExpanded,
                enter = expandVertically() + fadeIn(),
                exit = shrinkVertically() + fadeOut()
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(12.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    // Header row
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(
                            text = "${index + 1}. ${exercise.exerciseName}",
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.weight(1f),
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis
                        )
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            if (totalExercises > 1) {
                                IconButton(
                                    onClick = { onMove(-1) },
                                    enabled = index > 0,
                                    modifier = Modifier.size(48.dp)
                                ) {
                                    Icon(Icons.Filled.ArrowUpward, contentDescription = "Move up")
                                }
                                IconButton(
                                    onClick = { onMove(1) },
                                    enabled = index < totalExercises - 1,
                                    modifier = Modifier.size(48.dp)
                                ) {
                                    Icon(Icons.Filled.ArrowDownward, contentDescription = "Move down")
                                }
                            }
                            TextButton(
                                onClick = { onToggleExpand(false) },
                                modifier = Modifier.defaultMinSize(minHeight = 48.dp)
                            ) {
                                Text("Done ▲", fontWeight = FontWeight.SemiBold)
                            }
                            IconButton(
                                onClick = onDelete,
                                modifier = Modifier.size(48.dp)
                            ) {
                                Icon(
                                    Icons.Filled.Delete,
                                    contentDescription = "Remove ${exercise.exerciseName}",
                                    tint = MaterialTheme.colorScheme.error
                                )
                            }
                        }
                    }

                    if (hasError) {
                        Surface(
                            shape = RoundedCornerShape(8.dp),
                            color = MaterialTheme.colorScheme.errorContainer,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text(
                                text = ALL_ZERO_EXERCISE_ERROR_MESSAGE,
                                style = MaterialTheme.typography.bodySmall,
                                fontWeight = FontWeight.SemiBold,
                                color = MaterialTheme.colorScheme.onErrorContainer,
                                modifier = Modifier.padding(10.dp)
                            )
                        }
                    }

                    // Row 1: 4-Segment Target Metric Selector (360dp non-wrapping; LazyRow at >= 1.4f fontScale)
                    if (fontScale >= 1.4f) {
                        LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            items(ExerciseTargetMetric.entries) { metric ->
                                FilterChip(
                                    selected = exercise.targetMetric == metric,
                                    onClick = { onSelectMetric(metric) },
                                    label = {
                                        Text(
                                            text = metric.label,
                                            maxLines = 1,
                                            softWrap = false
                                        )
                                    },
                                    modifier = Modifier.defaultMinSize(minHeight = 48.dp)
                                )
                            }
                        }
                    } else {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            ExerciseTargetMetric.entries.forEach { metric ->
                                val selected = exercise.targetMetric == metric
                                Surface(
                                    shape = RoundedCornerShape(10.dp),
                                    color = if (selected) MaterialTheme.colorScheme.primaryContainer
                                    else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                                    border = BorderStroke(
                                        1.dp,
                                        if (selected) MaterialTheme.colorScheme.primary
                                        else MaterialTheme.colorScheme.outlineVariant
                                    ),
                                    modifier = Modifier
                                        .weight(1f)
                                        .defaultMinSize(minHeight = 48.dp)
                                        .clip(RoundedCornerShape(10.dp))
                                        .clickable { onSelectMetric(metric) }
                                ) {
                                    Box(contentAlignment = Alignment.Center, modifier = Modifier.padding(horizontal = 4.dp)) {
                                        Text(
                                            text = metric.label,
                                            style = MaterialTheme.typography.labelMedium,
                                            fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium,
                                            color = if (selected) MaterialTheme.colorScheme.onPrimaryContainer
                                            else MaterialTheme.colorScheme.onSurfaceVariant,
                                            maxLines = 1,
                                            softWrap = false,
                                            overflow = TextOverflow.Ellipsis
                                        )
                                    }
                                }
                            }
                        }
                    }

                    // Row 2: Target Mode Segmented Toggle (Fixed vs Per-Set)
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        listOf(
                            TargetMode.FIXED to "Fixed",
                            TargetMode.PER_SET to "Per-Set ($setsCount)"
                        ).forEach { (mode, label) ->
                            val selected = exercise.targetMode == mode
                            Surface(
                                shape = RoundedCornerShape(10.dp),
                                color = if (selected) MaterialTheme.colorScheme.secondaryContainer
                                else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f),
                                border = BorderStroke(
                                    1.dp,
                                    if (selected) MaterialTheme.colorScheme.secondary
                                    else MaterialTheme.colorScheme.outlineVariant
                                ),
                                modifier = Modifier
                                    .weight(1f)
                                    .defaultMinSize(minHeight = 48.dp)
                                    .clip(RoundedCornerShape(10.dp))
                                    .clickable { onSelectMode(mode) }
                            ) {
                                Box(contentAlignment = Alignment.Center) {
                                    Text(
                                        text = label,
                                        style = MaterialTheme.typography.labelLarge,
                                        fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium,
                                        color = if (selected) MaterialTheme.colorScheme.onSecondaryContainer
                                        else MaterialTheme.colorScheme.onSurfaceVariant,
                                        maxLines = 1,
                                        softWrap = false
                                    )
                                }
                            }
                        }
                    }

                    // Row 3: Value Controls (Fixed Mode vs Per-Set Grid)
                    if (exercise.targetMode == TargetMode.FIXED) {
                        val step = exercise.targetMetric.fixedStep
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            OutlinedButton(
                                onClick = {
                                    editingFixedDigits = false
                                    onUpdateFixed((exercise.fixedValue - step).coerceAtLeast(0))
                                },
                                modifier = Modifier.size(48.dp),
                                contentPadding = PaddingValues(0.dp)
                            ) {
                                Icon(Icons.Filled.Remove, contentDescription = "Decrease ${exercise.targetMetric.label}")
                            }

                            if (editingFixedDigits) {
                                OutlinedTextField(
                                    value = fixedInputText,
                                    onValueChange = { raw ->
                                        val digits = raw.filter { it.isDigit() }.take(6)
                                        fixedInputText = digits
                                        digits.toIntOrNull()?.let { onUpdateFixed(it) }
                                    },
                                    singleLine = true,
                                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                                    suffix = { Text(exercise.targetMetric.compactUnit) },
                                    modifier = Modifier.weight(1f)
                                )
                            } else {
                                Surface(
                                    shape = RoundedCornerShape(10.dp),
                                    color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                                    modifier = Modifier
                                        .weight(1f)
                                        .defaultMinSize(minHeight = 48.dp)
                                        .clip(RoundedCornerShape(10.dp))
                                        .clickable {
                                            fixedInputText = exercise.fixedValue.toString()
                                            editingFixedDigits = true
                                        }
                                ) {
                                    Box(contentAlignment = Alignment.Center) {
                                        Text(
                                            text = "${RoutineTargetCodec.formatValueNumberAndUnit(exercise.targetMetric, exercise.fixedValue)} / set",
                                            style = MaterialTheme.typography.titleMedium.copy(fontFeatureSettings = "tnum"),
                                            fontWeight = FontWeight.Bold
                                        )
                                    }
                                }
                            }

                            OutlinedButton(
                                onClick = {
                                    editingFixedDigits = false
                                    onUpdateFixed(exercise.fixedValue + step)
                                },
                                modifier = Modifier.size(48.dp),
                                contentPadding = PaddingValues(0.dp)
                            ) {
                                Icon(Icons.Filled.Add, contentDescription = "Increase ${exercise.targetMetric.label}")
                            }
                        }

                        FlowRow(
                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                            verticalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            exercise.targetMetric.presets.forEach { preset ->
                                FilterChip(
                                    selected = exercise.fixedValue == preset.value,
                                    onClick = {
                                        editingFixedDigits = false
                                        onUpdateFixed(preset.value)
                                    },
                                    label = { Text(preset.label) },
                                    modifier = Modifier.defaultMinSize(minWidth = 48.dp, minHeight = 48.dp)
                                )
                            }
                        }
                    } else {
                        val normalizedSets = remember(exercise.perSetValues, exercise.fixedValue, setsCount) {
                            RoutineTargetCodec.resizePerSetValues(exercise.perSetValues, exercise.fixedValue, setsCount)
                        }

                        BoxWithConstraints(modifier = Modifier.fillMaxWidth()) {
                            val cols = resolvePerSetGridColumns(maxWidth.value.toInt(), fontScale)
                            val rows = normalizedSets.withIndex().chunked(cols)

                            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                rows.forEach { rowItems ->
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                                    ) {
                                        rowItems.forEach { (setIdx, setVal) ->
                                            PerSetValueCard(
                                                setNumber = setIdx + 1,
                                                metric = exercise.targetMetric,
                                                value = setVal,
                                                fallbackPositive = exercise.fixedValue.takeIf { it > 0 }
                                                    ?: exercise.targetMetric.defaultFixedValue,
                                                onValueChange = { updated -> onUpdateSet(setIdx, updated) },
                                                modifier = Modifier.weight(1f)
                                            )
                                        }
                                        repeat(cols - rowItems.size) {
                                            Spacer(modifier = Modifier.weight(1f))
                                        }
                                    }
                                }
                            }
                        }

                        if (setsCount > 1) {
                            FlowRow(
                                horizontalArrangement = Arrangement.spacedBy(6.dp),
                                verticalArrangement = Arrangement.spacedBy(4.dp)
                            ) {
                                ExerciseSetPattern.entries.forEach { pattern ->
                                    FilterChip(
                                        selected = false,
                                        onClick = { onApplyPattern(pattern) },
                                        label = { Text(pattern.label, style = MaterialTheme.typography.labelSmall) },
                                        modifier = Modifier.defaultMinSize(minHeight = 48.dp)
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun PerSetValueCard(
    setNumber: Int,
    metric: ExerciseTargetMetric,
    value: Int,
    fallbackPositive: Int,
    onValueChange: (Int) -> Unit,
    modifier: Modifier = Modifier
) {
    val isSkipped = value <= 0
    val step = metric.perSetStep
    val outlineColor = MaterialTheme.colorScheme.outline
    val spokenDesc = RoutineTargetCodec.formatSpokenSetDescription(setNumber, metric, value)
    var showKeypadDialog by remember { mutableStateOf(false) }
    var keypadDigits by remember(value) { mutableStateOf(value.toString()) }

    val cardModifier = if (isSkipped) {
        modifier
            .clip(RoundedCornerShape(10.dp))
            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f))
            .drawBehind {
                drawRoundRect(
                    color = outlineColor,
                    style = Stroke(
                        width = 1.dp.toPx(),
                        pathEffect = PathEffect.dashPathEffect(floatArrayOf(10f, 8f), 0f)
                    ),
                    cornerRadius = CornerRadius(10.dp.toPx(), 10.dp.toPx())
                )
            }
    } else {
        modifier
            .clip(RoundedCornerShape(10.dp))
            .background(MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.35f))
            .drawBehind {
                drawRoundRect(
                    color = outlineColor.copy(alpha = 0.6f),
                    style = Stroke(width = 1.dp.toPx()),
                    cornerRadius = CornerRadius(10.dp.toPx(), 10.dp.toPx())
                )
            }
    }

    Column(
        modifier = cardModifier
            .semantics(mergeDescendants = true) {
                contentDescription = spokenDesc
            }
            .padding(6.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        Text(
            text = "Set $setNumber",
            style = MaterialTheme.typography.labelSmall,
            fontWeight = FontWeight.SemiBold,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1
        )

        if (isSkipped) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .defaultMinSize(minHeight = 48.dp)
                    .clickable { onValueChange(fallbackPositive) },
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = RoutineTargetCodec.formatSkipLabel(metric),
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center
                )
            }
            TextButton(
                onClick = { onValueChange(fallbackPositive) },
                modifier = Modifier
                    .fillMaxWidth()
                    .defaultMinSize(minHeight = 48.dp),
                contentPadding = PaddingValues(0.dp)
            ) {
                Text("+ Active", style = MaterialTheme.typography.labelSmall)
            }
        } else {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .defaultMinSize(minHeight = 48.dp)
                    .clip(RoundedCornerShape(8.dp))
                    .background(MaterialTheme.colorScheme.surface)
                    .clickable {
                        keypadDigits = value.toString()
                        showKeypadDialog = true
                    },
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = RoutineTargetCodec.formatCompactSetCellValue(metric, value),
                    style = MaterialTheme.typography.titleSmall.copy(fontFeatureSettings = "tnum"),
                    fontWeight = FontWeight.Bold,
                    maxLines = 1
                )
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(
                    onClick = { onValueChange((value - step).coerceAtLeast(0)) },
                    modifier = Modifier.size(48.dp)
                ) {
                    Icon(Icons.Filled.Remove, contentDescription = "Decrease Set $setNumber", modifier = Modifier.size(16.dp))
                }
                IconButton(
                    onClick = { onValueChange(value + step) },
                    modifier = Modifier.size(48.dp)
                ) {
                    Icon(Icons.Filled.Add, contentDescription = "Increase Set $setNumber", modifier = Modifier.size(16.dp))
                }
            }

            TextButton(
                onClick = { onValueChange(0) },
                modifier = Modifier
                    .fillMaxWidth()
                    .defaultMinSize(minHeight = 48.dp),
                contentPadding = PaddingValues(0.dp)
            ) {
                Text("0 (Skip)", style = MaterialTheme.typography.labelSmall)
            }
        }
    }

    if (showKeypadDialog) {
        AlertDialog(
            onDismissRequest = { showKeypadDialog = false },
            title = { Text("Set $setNumber (${metric.label})") },
            text = {
                OutlinedTextField(
                    value = keypadDigits,
                    onValueChange = { raw -> keypadDigits = raw.filter { it.isDigit() }.take(6) },
                    label = { Text("Enter 0 to skip this set") },
                    suffix = { Text(metric.compactUnit) },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    modifier = Modifier.fillMaxWidth()
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        val parsed = keypadDigits.toIntOrNull()?.coerceAtLeast(0) ?: 0
                        onValueChange(parsed)
                        showKeypadDialog = false
                    }
                ) {
                    Text("Apply", fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(onClick = { showKeypadDialog = false }) {
                    Text("Cancel")
                }
            }
        )
    }
}

@Composable
internal fun SummaryBadgePill(
    text: String,
    containerColor: androidx.compose.ui.graphics.Color,
    contentColor: androidx.compose.ui.graphics.Color
) {
    Surface(
        shape = RoundedCornerShape(6.dp),
        color = containerColor
    ) {
        Text(
            text = text,
            style = MaterialTheme.typography.labelSmall,
            fontWeight = FontWeight.SemiBold,
            color = contentColor,
            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
}
