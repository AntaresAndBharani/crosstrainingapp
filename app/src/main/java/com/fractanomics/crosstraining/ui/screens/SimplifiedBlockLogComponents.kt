package com.fractanomics.crosstraining.ui.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.sizeIn
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Timer
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedCard
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshots.SnapshotStateList
import androidx.compose.runtime.snapshots.SnapshotStateMap
import androidx.compose.runtime.toMutableStateList
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.fractanomics.crosstraining.data.model.BlockKind
import com.fractanomics.crosstraining.data.model.Exercise
import com.fractanomics.crosstraining.data.model.ExerciseCategory
import com.fractanomics.crosstraining.data.model.MetricType
import com.fractanomics.crosstraining.data.model.RepMax
import com.fractanomics.crosstraining.data.model.SessionWithBlocks
import com.fractanomics.crosstraining.ui.BlockDraft
import com.fractanomics.crosstraining.ui.RepMaxDraft
import com.fractanomics.crosstraining.ui.SetDraft
import com.fractanomics.crosstraining.ui.trimmed
import com.fractanomics.crosstraining.util.AvailableBlock
import com.fractanomics.crosstraining.ui.theme.isAppDarkTheme
import com.fractanomics.crosstraining.util.AvailableBlockCodec
import com.fractanomics.crosstraining.util.AvailableBlockExercise
import com.fractanomics.crosstraining.util.DailyRoutine
import com.fractanomics.crosstraining.util.DailyRoutineCodec
import com.fractanomics.crosstraining.util.DailyRoutineItem
import com.fractanomics.crosstraining.util.EvaluatedPrChip
import com.fractanomics.crosstraining.util.ExerciseTargetMetric
import com.fractanomics.crosstraining.util.HydratedAvailableBlockSeed
import com.fractanomics.crosstraining.util.RoutineExerciseTargetSpec
import com.fractanomics.crosstraining.util.RoutineTargetCodec
import com.fractanomics.crosstraining.util.STANDALONE_EXERCISE_SESSION_FORMAT
import com.fractanomics.crosstraining.util.STANDARD_RM_REPS
import com.fractanomics.crosstraining.util.TrackedPrSlot
import java.math.BigDecimal
import java.math.RoundingMode
import java.util.UUID

internal enum class SessionItemKind {
    BLOCK,
    EXERCISE
}

internal fun normalizeWeight2dp(value: Double): BigDecimal =
    BigDecimal.valueOf(value).setScale(2, RoundingMode.HALF_UP)

internal fun prSlotKey(movementId: Long, movementName: String, rmReps: Int): String =
    if (movementId > 0L) "id:$movementId:$rmReps" else "name:${movementName.trim().lowercase()}:$rmReps"

internal data class DismissedPrSnapshot(
    val slot: TrackedPrSlot,
    val key: String,
    val overrideText: String?,
    val wasCustomOverride: Boolean,
    val wasPersisted: Boolean,
    val wasExplicit: Boolean,
    val slotIndex: Int
)

internal enum class SharedSetWeightStateKind {
    LINKED,
    MIXED,
    ALL_SKIPPED,
    NON_WEIGHTED_ONLY
}

internal data class SharedSetWeightSummary(
    val kind: SharedSetWeightStateKind,
    val uniformWeightText: String = "",
    val minWeight: Double? = null,
    val maxWeight: Double? = null,
    val majorityWeightText: String = "",
    val divergentExerciseIndices: Set<Int> = emptySet()
)

internal data class ComplexSetWeightsSnapshot(
    val setIndex: Int,
    val previousWeightsByExercise: List<String>
)

internal data class RemovedExerciseSetSlice(
    val weight: String,
    val perSetRep: Int,
    val repsInput: String,
    val metricInput: String,
    val groupIndex: Int?,
    val skipped: Boolean
)

internal data class RemovedComplexSetSnapshot(
    val setIndex: Int,
    val perExerciseSlices: List<RemovedExerciseSetSlice>
)

private data class CachedExerciseMetricInputs(
    val repsInputs: List<String>,
    val weights: List<String>,
    val metricInputs: List<String>
)

internal class LoadedAvailableBlockExerciseState(
    val exerciseId: Long,
    val exerciseName: String,
    val lastSessionDateLabel: String?,
    initialWeights: List<String>,
    initialPerSetReps: List<Int> = List(initialWeights.size.coerceAtLeast(1)) { 1 },
    val defaultReps: Int = 1,
    val sourceBlockId: Long? = null,
    val rawFormat: String = "",
    val rawScheme: String = "",
    val rawSection: String = "",
    val rawKind: BlockKind = BlockKind.STRENGTH,
    val rawIsCompleted: Boolean = true,
    val subBlockTitle: String = "",
    initialMetric: ExerciseTargetMetric = ExerciseTargetMetric.REPS,
    initialRepsInputs: List<String>? = null,
    initialMetricInputs: List<String>? = null,
    initialGroupIndices: List<Int?> = List(initialWeights.size.coerceAtLeast(1)) { null },
    val targetBanner: String? = null,
    val rawNotes: String = "",
    val libraryMetricType: MetricType = MetricType.WEIGHT,
    initialSkippedFlags: List<Boolean> = List(initialWeights.size.coerceAtLeast(1)) { false }
) {
    var activeMetric by mutableStateOf(initialMetric)
    val weights: SnapshotStateList<String> = initialWeights.ifEmpty { listOf("") }.toMutableStateList()
    val perSetReps: SnapshotStateList<Int> = List(this.weights.size) { idx ->
        initialPerSetReps.getOrNull(idx)?.coerceAtLeast(1) ?: defaultReps.coerceAtLeast(1)
    }.toMutableStateList()
    val repsInputs: SnapshotStateList<String> = List(this.weights.size) { idx ->
        initialRepsInputs?.getOrNull(idx)
            ?: initialPerSetReps.getOrNull(idx)?.takeIf { it > 1 }?.toString()
            ?: ""
    }.toMutableStateList()
    val metricInputs: SnapshotStateList<String> = List(this.weights.size) { idx ->
        initialMetricInputs?.getOrNull(idx).orEmpty()
    }.toMutableStateList()
    val groupIndices: SnapshotStateList<Int?> = List(this.weights.size) { idx ->
        initialGroupIndices.getOrNull(idx)?.takeIf { it > 0 }
    }.toMutableStateList()
    val skippedFlags: SnapshotStateList<Boolean> = List(this.weights.size) { idx ->
        initialSkippedFlags.getOrNull(idx) ?: false
    }.toMutableStateList()

    val supportsSharedWeight: Boolean
        get() = activeMetric == ExerciseTargetMetric.REPS && libraryMetricType == MetricType.WEIGHT

    /** True when [setIndex] would persist a [SetDraft] (positive weight/reps or metric value). */
    internal fun hasLoggedValueAt(setIndex: Int): Boolean =
        if (activeMetric == ExerciseTargetMetric.REPS) {
            AvailableBlockCodec.normalizedPositiveWeight(weights.getOrNull(setIndex).orEmpty()) != null ||
                (repsInputs.getOrNull(setIndex)?.trim()?.toIntOrNull() ?: 0) > 0
        } else {
            AvailableBlockCodec.normalizedPositiveWeight(metricInputs.getOrNull(setIndex).orEmpty()) != null
        }

    private val metricInputCache = mutableMapOf<ExerciseTargetMetric, CachedExerciseMetricInputs>()

    internal fun switchActiveMetric(newMetric: ExerciseTargetMetric) {
        if (activeMetric == newMetric) return
        val currentCount = weights.size.coerceAtLeast(1)
        metricInputCache[activeMetric] = CachedExerciseMetricInputs(
            repsInputs = repsInputs.toList(),
            weights = weights.toList(),
            metricInputs = metricInputs.toList()
        )
        activeMetric = newMetric
        val cached = metricInputCache[newMetric]
        if (cached != null) {
            for (i in 0 until currentCount) {
                if (skippedFlags.getOrElse(i) { false }) {
                    repsInputs[i] = ""
                    weights[i] = ""
                    metricInputs[i] = ""
                    perSetReps[i] = defaultReps.coerceAtLeast(1)
                } else {
                    repsInputs[i] = cached.repsInputs.getOrNull(i).orEmpty()
                    weights[i] = cached.weights.getOrNull(i).orEmpty()
                    metricInputs[i] = cached.metricInputs.getOrNull(i).orEmpty()
                    perSetReps[i] = repsInputs[i].toIntOrNull()?.coerceAtLeast(1) ?: defaultReps.coerceAtLeast(1)
                }
            }
        } else {
            for (i in 0 until currentCount) {
                metricInputs[i] = ""
            }
        }
    }
}

internal class LoadedAvailableBlockState(
    val block: AvailableBlock,
    exerciseStates: List<LoadedAvailableBlockExerciseState>,
    initialTrackedPrs: List<TrackedPrSlot>,
    val isEditMode: Boolean = false,
    val editingSessionId: Long? = null,
    initialPersistedRepMaxes: List<RepMax> = emptyList(),
    val rawFormat: String = "",
    val rawScheme: String = "",
    val rawSection: String = "",
    val rawKind: BlockKind = BlockKind.STRENGTH,
    val rawIsCompleted: Boolean = true,
    val uiKey: String = UUID.randomUUID().toString(),
    val itemKind: SessionItemKind = SessionItemKind.BLOCK,
    val sourceRoutineItem: DailyRoutineItem? = null,
    val lastSessionDateFormatted: String? = null,
    val hasPreFilledHistory: Boolean = false
) {
    val exerciseSections: SnapshotStateList<LoadedAvailableBlockExerciseState> = exerciseStates.toMutableStateList()
    val trackedPrSlots: SnapshotStateList<TrackedPrSlot> = initialTrackedPrs.toMutableStateList()
    val prWeightOverrides: SnapshotStateMap<String, String> = mutableStateMapOf()
    val customWeightOverrideFlags: SnapshotStateMap<String, Boolean> = mutableStateMapOf()
    val persistedSessionPrKeys: SnapshotStateMap<String, Boolean> = mutableStateMapOf()
    val explicitlyAppliedPrKeys: SnapshotStateMap<String, Boolean> = mutableStateMapOf()
    var mutationCount by mutableIntStateOf(0)

    val isCompactComplex: Boolean
        get() = itemKind == SessionItemKind.BLOCK && exerciseSections.size >= 2

    val complexSetCount: Int
        get() = exerciseSections.maxOfOrNull { it.weights.size }?.coerceAtLeast(1) ?: 1

    init {
        initialPersistedRepMaxes.forEach { rm ->
            if (rm.exerciseId <= 0L || rm.weight <= 0.0) return@forEach
            val matchedSlot = trackedPrSlots.firstOrNull { it.movementId == rm.exerciseId && it.rmReps == rm.reps }
            val movementName = matchedSlot?.movementName
                ?: exerciseSections.firstOrNull { it.exerciseId == rm.exerciseId }?.exerciseName
                ?: ""
            val key = prSlotKey(rm.exerciseId, movementName, rm.reps)
            persistedSessionPrKeys[key] = true
            prWeightOverrides[key] = rm.weight.trimmed()
            val setMax = maxSetWeightFor(rm.exerciseId)
            val isCustom = setMax <= 0.0 || normalizeWeight2dp(rm.weight) != normalizeWeight2dp(setMax)
            customWeightOverrideFlags[key] = isCustom
        }
    }

    fun isCustomWeightOverride(key: String): Boolean = customWeightOverrideFlags[key] == true

    fun isTrackedSessionPrKey(key: String): Boolean =
        persistedSessionPrKeys[key] == true || explicitlyAppliedPrKeys[key] == true

    fun blockMaxSetWeight(): Double =
        exerciseSections.asSequence()
            .filter { it.activeMetric == ExerciseTargetMetric.REPS }
            .flatMap { section ->
                section.weights.asSequence().filterIndexed { idx, _ ->
                    !section.skippedFlags.getOrElse(idx) { false }
                }
            }
            .mapNotNull { AvailableBlockCodec.normalizedPositiveWeight(it) }
            .maxOrNull() ?: 0.0

    fun maxSetWeightFor(movementId: Long): Double {
        if (movementId > 0L) {
            val matchSections = exerciseSections.filter { it.exerciseId == movementId }
            if (matchSections.isNotEmpty()) {
                return matchSections.asSequence()
                    .filter { it.activeMetric == ExerciseTargetMetric.REPS }
                    .flatMap { section ->
                        section.weights.asSequence().filterIndexed { idx, _ ->
                            !section.skippedFlags.getOrElse(idx) { false }
                        }
                    }
                    .mapNotNull { AvailableBlockCodec.normalizedPositiveWeight(it) }
                    .maxOrNull() ?: 0.0
            }
        }
        return blockMaxSetWeight()
    }

    fun resolveSharedSetWeight(setIndex: Int): SharedSetWeightSummary {
        val activeIndices = exerciseSections.indices.filter { idx ->
            !exerciseSections[idx].skippedFlags.getOrElse(setIndex) { false }
        }
        if (activeIndices.isEmpty()) {
            return SharedSetWeightSummary(kind = SharedSetWeightStateKind.ALL_SKIPPED)
        }

        val weightedActive = activeIndices.filter { exerciseSections[it].supportsSharedWeight }
        val explicitlyLoadedActive = activeIndices.filter { idx ->
            val sec = exerciseSections[idx]
            sec.activeMetric == ExerciseTargetMetric.REPS &&
                (AvailableBlockCodec.normalizedPositiveWeight(sec.weights.getOrNull(setIndex).orEmpty()) ?: 0.0) > 0.0
        }
        val targetIndices = (weightedActive + explicitlyLoadedActive).distinct()
        if (targetIndices.isEmpty()) {
            return SharedSetWeightSummary(kind = SharedSetWeightStateKind.NON_WEIGHTED_ONLY)
        }

        val rawTexts = targetIndices.map { exerciseSections[it].weights.getOrNull(setIndex).orEmpty() }
        if (rawTexts.distinct().size == 1) {
            return SharedSetWeightSummary(
                kind = SharedSetWeightStateKind.LINKED,
                uniformWeightText = rawTexts.first()
            )
        }

        val parsedWeights = rawTexts.map { AvailableBlockCodec.normalizedPositiveWeight(it) }
        val allPositive = parsedWeights.all { it != null }
        if (allPositive) {
            val scaledDistinct = parsedWeights.map { normalizeWeight2dp(checkNotNull(it)) }.distinct()
            if (scaledDistinct.size == 1) {
                return SharedSetWeightSummary(
                    kind = SharedSetWeightStateKind.LINKED,
                    uniformWeightText = checkNotNull(parsedWeights.first()).trimmed()
                )
            }
        }

        val nonNullWeights = parsedWeights.filterNotNull()
        val minWeight = nonNullWeights.minOrNull()
        val maxWeight = nonNullWeights.maxOrNull()

        var majorityBd: BigDecimal? = null
        var majorityWeightValue: Double? = null
        if (nonNullWeights.isNotEmpty()) {
            val groupedCounts = linkedMapOf<BigDecimal, Pair<Int, Double>>()
            parsedWeights.forEach { w ->
                if (w != null) {
                    val bd = normalizeWeight2dp(w)
                    val prev = groupedCounts[bd]
                    if (prev == null) {
                        groupedCounts[bd] = 1 to w
                    } else {
                        groupedCounts[bd] = (prev.first + 1) to prev.second
                    }
                }
            }
            val bestEntry = groupedCounts.entries.maxByOrNull { it.value.first }
            if (bestEntry != null) {
                majorityBd = bestEntry.key
                majorityWeightValue = bestEntry.value.second
            }
        }

        val divergent = mutableSetOf<Int>()
        targetIndices.forEachIndexed { pos, exIdx ->
            val w = parsedWeights[pos]
            val matchesMajority = w != null && majorityBd != null && normalizeWeight2dp(w) == majorityBd
            if (!matchesMajority) {
                divergent += exIdx
            }
        }

        return SharedSetWeightSummary(
            kind = SharedSetWeightStateKind.MIXED,
            uniformWeightText = "",
            minWeight = minWeight,
            maxWeight = maxWeight,
            majorityWeightText = majorityWeightValue?.trimmed().orEmpty(),
            divergentExerciseIndices = divergent
        )
    }

    fun captureComplexSetWeights(setIndex: Int): ComplexSetWeightsSnapshot =
        ComplexSetWeightsSnapshot(
            setIndex = setIndex,
            previousWeightsByExercise = exerciseSections.map { it.weights.getOrNull(setIndex).orEmpty() }
        )

    fun updateSharedSetWeight(setIndex: Int, rawValue: String): String {
        val sanitized = sanitizeCompactWeightInput(rawValue)
        val activeIndices = exerciseSections.indices.filter { idx ->
            !exerciseSections[idx].skippedFlags.getOrElse(setIndex) { false }
        }
        val targets = activeIndices.filter { idx ->
            val sec = exerciseSections[idx]
            sec.supportsSharedWeight ||
                (sec.activeMetric == ExerciseTargetMetric.REPS &&
                    (AvailableBlockCodec.normalizedPositiveWeight(sec.weights.getOrNull(setIndex).orEmpty()) ?: 0.0) > 0.0)
        }
        var changed = false
        targets.forEach { idx ->
            val sec = exerciseSections[idx]
            if (setIndex in sec.weights.indices && sec.weights[setIndex] != sanitized) {
                sec.weights[setIndex] = sanitized
                syncNonOverriddenPrWeightsFor(sec.exerciseId, sec.exerciseName)
                changed = true
            }
        }
        if (changed) {
            mutationCount += 1
        }
        return sanitized
    }

    fun relinkComplexSetWeights(setIndex: Int, targetWeight: String): ComplexSetWeightsSnapshot? {
        val sanitized = sanitizeCompactWeightInput(targetWeight)
        val snapshot = captureComplexSetWeights(setIndex)
        updateSharedSetWeight(setIndex, sanitized)
        val after = exerciseSections.map { it.weights.getOrNull(setIndex).orEmpty() }
        return if (after != snapshot.previousWeightsByExercise) snapshot else null
    }

    fun restoreComplexSetWeights(snapshot: ComplexSetWeightsSnapshot) {
        val setIdx = snapshot.setIndex
        exerciseSections.forEachIndexed { idx, sec ->
            val prev = snapshot.previousWeightsByExercise.getOrNull(idx) ?: return@forEachIndexed
            if (setIdx in sec.weights.indices) {
                sec.weights[setIdx] = prev
                syncNonOverriddenPrWeightsFor(sec.exerciseId, sec.exerciseName)
            }
        }
        mutationCount += 1
    }

    fun toggleComplexExerciseSkipped(
        exState: LoadedAvailableBlockExerciseState,
        setIndex: Int
    ) {
        if (setIndex !in exState.skippedFlags.indices) return
        val currentlySkipped = exState.skippedFlags[setIndex]
        if (!currentlySkipped) {
            exState.skippedFlags[setIndex] = true
            if (setIndex in exState.weights.indices) exState.weights[setIndex] = ""
            if (setIndex in exState.metricInputs.indices) exState.metricInputs[setIndex] = ""
            syncNonOverriddenPrWeightsFor(exState.exerciseId, exState.exerciseName)
            mutationCount += 1
            return
        }

        val preToggleSummary = resolveSharedSetWeight(setIndex)
        exState.skippedFlags[setIndex] = false
        if (exState.activeMetric == ExerciseTargetMetric.REPS) {
            val priorActiveRep = exState.perSetReps.indices.lastOrNull { i ->
                i != setIndex && !exState.skippedFlags.getOrElse(i) { false }
            }?.let { exState.perSetReps[it] }
            val restoredRep = exState.repsInputs.getOrNull(setIndex)?.toIntOrNull()?.takeIf { it > 0 }
                ?: exState.perSetReps.getOrNull(setIndex)?.takeIf { it > 0 }
                ?: priorActiveRep
                ?: exState.defaultReps.coerceAtLeast(1)
            if (setIndex in exState.perSetReps.indices) {
                exState.perSetReps[setIndex] = restoredRep
            }
            if (setIndex in exState.repsInputs.indices) {
                exState.repsInputs[setIndex] = restoredRep.toString()
            }
            if (exState.supportsSharedWeight &&
                preToggleSummary.kind == SharedSetWeightStateKind.LINKED &&
                preToggleSummary.uniformWeightText.isNotBlank() &&
                setIndex in exState.weights.indices
            ) {
                exState.weights[setIndex] = preToggleSummary.uniformWeightText
            }
        } else {
            val priorActiveMetric = exState.metricInputs.indices.lastOrNull { i ->
                i != setIndex && !exState.skippedFlags.getOrElse(i) { false } && exState.metricInputs[i].isNotBlank()
            }?.let { exState.metricInputs[it] }
            val restoredMetric = exState.metricInputs.getOrNull(setIndex)?.takeIf { it.isNotBlank() }
                ?: priorActiveMetric
                ?: exState.activeMetric.defaultFixedValue.toString()
            if (setIndex in exState.metricInputs.indices) {
                exState.metricInputs[setIndex] = restoredMetric
            }
        }
        syncNonOverriddenPrWeightsFor(exState.exerciseId, exState.exerciseName)
        mutationCount += 1
    }

    fun addComplexSet() {
        val currentCount = complexSetCount
        val lastIdx = (currentCount - 1).coerceAtLeast(0)
        val newCount = currentCount + 1
        exerciseSections.forEach { sec ->
            while (sec.weights.size < currentCount) sec.weights.add("")
            while (sec.perSetReps.size < currentCount) sec.perSetReps.add(sec.defaultReps.coerceAtLeast(1))
            while (sec.repsInputs.size < currentCount) sec.repsInputs.add("")
            while (sec.metricInputs.size < currentCount) sec.metricInputs.add("")
            while (sec.groupIndices.size < currentCount) sec.groupIndices.add(sec.groupIndices.size + 1)
            while (sec.skippedFlags.size < currentCount) sec.skippedFlags.add(false)

            val lastSkipped = sec.skippedFlags.getOrNull(lastIdx) ?: false
            sec.skippedFlags.add(lastSkipped)
            sec.weights.add(if (lastSkipped) "" else sec.weights.getOrNull(lastIdx).orEmpty())
            sec.perSetReps.add(sec.perSetReps.getOrNull(lastIdx) ?: sec.defaultReps.coerceAtLeast(1))
            sec.repsInputs.add(if (lastSkipped) "" else sec.repsInputs.getOrNull(lastIdx).orEmpty())
            sec.metricInputs.add(if (lastSkipped) "" else sec.metricInputs.getOrNull(lastIdx).orEmpty())
            sec.groupIndices.add(newCount)
            syncNonOverriddenPrWeightsFor(sec.exerciseId, sec.exerciseName)
        }
        mutationCount += 1
    }

    fun removeComplexSet(setIndex: Int): RemovedComplexSetSnapshot? {
        val currentCount = complexSetCount
        if (currentCount <= 1 || setIndex !in 0 until currentCount) return null
        val slices = exerciseSections.map { sec ->
            RemovedExerciseSetSlice(
                weight = sec.weights.getOrNull(setIndex).orEmpty(),
                perSetRep = sec.perSetReps.getOrNull(setIndex) ?: sec.defaultReps.coerceAtLeast(1),
                repsInput = sec.repsInputs.getOrNull(setIndex).orEmpty(),
                metricInput = sec.metricInputs.getOrNull(setIndex).orEmpty(),
                groupIndex = sec.groupIndices.getOrNull(setIndex),
                skipped = sec.skippedFlags.getOrNull(setIndex) ?: false
            )
        }
        exerciseSections.forEach { sec ->
            if (setIndex in sec.weights.indices) sec.weights.removeAt(setIndex)
            if (setIndex in sec.perSetReps.indices) sec.perSetReps.removeAt(setIndex)
            if (setIndex in sec.repsInputs.indices) sec.repsInputs.removeAt(setIndex)
            if (setIndex in sec.metricInputs.indices) sec.metricInputs.removeAt(setIndex)
            if (setIndex in sec.groupIndices.indices) sec.groupIndices.removeAt(setIndex)
            if (setIndex in sec.skippedFlags.indices) sec.skippedFlags.removeAt(setIndex)
            for (i in sec.groupIndices.indices) {
                sec.groupIndices[i] = i + 1
            }
            syncNonOverriddenPrWeightsFor(sec.exerciseId, sec.exerciseName)
        }
        mutationCount += 1
        return RemovedComplexSetSnapshot(setIndex = setIndex, perExerciseSlices = slices)
    }

    fun restoreRemovedComplexSet(snapshot: RemovedComplexSetSnapshot) {
        exerciseSections.forEachIndexed { idx, sec ->
            val slice = snapshot.perExerciseSlices.getOrNull(idx) ?: return@forEachIndexed
            val insertAt = snapshot.setIndex.coerceIn(0, sec.weights.size)
            sec.weights.add(insertAt, slice.weight)
            sec.perSetReps.add(insertAt.coerceIn(0, sec.perSetReps.size), slice.perSetRep)
            sec.repsInputs.add(insertAt.coerceIn(0, sec.repsInputs.size), slice.repsInput)
            sec.metricInputs.add(insertAt.coerceIn(0, sec.metricInputs.size), slice.metricInput)
            sec.groupIndices.add(insertAt.coerceIn(0, sec.groupIndices.size), slice.groupIndex)
            sec.skippedFlags.add(insertAt.coerceIn(0, sec.skippedFlags.size), slice.skipped)
            for (i in sec.groupIndices.indices) {
                sec.groupIndices[i] = i + 1
            }
            syncNonOverriddenPrWeightsFor(sec.exerciseId, sec.exerciseName)
        }
        mutationCount += 1
    }

    fun updateSetWeight(
        exState: LoadedAvailableBlockExerciseState,
        setIndex: Int,
        rawValue: String
    ) {
        if (setIndex !in exState.weights.indices) return
        if (exState.weights[setIndex] == rawValue) return
        exState.weights[setIndex] = rawValue
        syncNonOverriddenPrWeightsFor(exState.exerciseId, exState.exerciseName)
        mutationCount += 1
    }

    fun updateSetReps(
        exState: LoadedAvailableBlockExerciseState,
        setIndex: Int,
        rawValue: String
    ) {
        if (setIndex !in exState.repsInputs.indices) return
        val filtered = rawValue.filter { it.isDigit() }.take(4)
        if (exState.repsInputs[setIndex] == filtered) return
        exState.repsInputs[setIndex] = filtered
        if (setIndex in exState.perSetReps.indices) {
            exState.perSetReps[setIndex] = filtered.toIntOrNull()?.coerceAtLeast(1)
                ?: exState.defaultReps.coerceAtLeast(1)
        }
        mutationCount += 1
    }

    fun updateSetMetricValue(
        exState: LoadedAvailableBlockExerciseState,
        setIndex: Int,
        rawValue: String
    ) {
        if (setIndex !in exState.metricInputs.indices) return
        val filtered = rawValue.filter { it.isDigit() || it == '.' || it == ',' }.take(8)
        if (exState.metricInputs[setIndex] == filtered) return
        exState.metricInputs[setIndex] = filtered
        mutationCount += 1
    }

    fun switchExerciseMetric(
        exState: LoadedAvailableBlockExerciseState,
        newMetric: ExerciseTargetMetric
    ) {
        if (exState.activeMetric == newMetric) return
        exState.switchActiveMetric(newMetric)
        syncNonOverriddenPrWeightsFor(exState.exerciseId, exState.exerciseName)
        mutationCount += 1
    }

    fun addSet(exState: LoadedAvailableBlockExerciseState) {
        exState.weights.add("")
        exState.perSetReps.add(exState.perSetReps.lastOrNull() ?: exState.defaultReps.coerceAtLeast(1))
        exState.repsInputs.add("")
        exState.metricInputs.add("")
        val hasGroupLabels = exState.groupIndices.any { it != null }
        val nextGroup = if (hasGroupLabels) {
            (exState.groupIndices.filterNotNull().maxOrNull() ?: exState.weights.size) + 1
        } else {
            null
        }
        exState.groupIndices.add(nextGroup)
        syncNonOverriddenPrWeightsFor(exState.exerciseId, exState.exerciseName)
        mutationCount += 1
    }

    fun removeSet(exState: LoadedAvailableBlockExerciseState, setIndex: Int) {
        if (exState.weights.size <= 1 || setIndex !in exState.weights.indices) return
        exState.weights.removeAt(setIndex)
        if (setIndex in exState.perSetReps.indices) exState.perSetReps.removeAt(setIndex)
        if (setIndex in exState.repsInputs.indices) exState.repsInputs.removeAt(setIndex)
        if (setIndex in exState.metricInputs.indices) exState.metricInputs.removeAt(setIndex)
        if (setIndex in exState.groupIndices.indices) exState.groupIndices.removeAt(setIndex)
        syncNonOverriddenPrWeightsFor(exState.exerciseId, exState.exerciseName)
        mutationCount += 1
    }

    fun syncNonOverriddenPrWeightsFor(movementId: Long, movementName: String = "") {
        val normalizedName = movementName.trim().lowercase()
        val maxWeight = if (movementId > 0L) maxSetWeightFor(movementId) else blockMaxSetWeight()
        trackedPrSlots.forEach { slot ->
            val matchesSlot = (movementId > 0L && slot.movementId == movementId) ||
                (slot.movementId <= 0L && normalizedName.isNotEmpty() && slot.movementName.trim().lowercase() == normalizedName) ||
                (exerciseSections.size <= 1)
            if (!matchesSlot) return@forEach
            val key = prSlotKey(slot.movementId, slot.movementName, slot.rmReps)
            if (!isCustomWeightOverride(key)) {
                if (maxWeight > 0.0) {
                    prWeightOverrides[key] = maxWeight.trimmed()
                } else {
                    prWeightOverrides.remove(key)
                }
            }
        }
    }

    fun applyPrPickerChanges(
        updatedSlots: List<TrackedPrSlot>,
        enteredWeightsByKey: Map<String, String>,
        exercises: List<Exercise> = emptyList(),
        repMaxes: List<RepMax> = emptyList()
    ) {
        val byLowerName = exercises.associateBy { it.name.trim().lowercase() }
        val priorKeys = trackedPrSlots.map { prSlotKey(it.movementId, it.movementName, it.rmReps) }.toSet()
        val normalizedUpdated = AvailableBlockCodec.normalizeTrackedPrSlots(
            updatedSlots.map { slot ->
                if (slot.movementId > 0L) slot
                else slot.copy(movementId = byLowerName[slot.movementName.trim().lowercase()]?.id ?: 0L)
            }
        )
        val newKeys = normalizedUpdated.map { prSlotKey(it.movementId, it.movementName, it.rmReps) }.toSet()
        val removedKeys = priorKeys - newKeys
        removedKeys.forEach { key ->
            persistedSessionPrKeys.remove(key)
            explicitlyAppliedPrKeys.remove(key)
            prWeightOverrides.remove(key)
            customWeightOverrideFlags.remove(key)
        }

        trackedPrSlots.clear()
        trackedPrSlots.addAll(normalizedUpdated)

        normalizedUpdated.forEach { slot ->
            val key = prSlotKey(slot.movementId, slot.movementName, slot.rmReps)
            val enteredRaw = enteredWeightsByKey[key]?.trim().orEmpty()
            val parsedEntered = AvailableBlockCodec.normalizedPositiveWeight(enteredRaw)
            val liveMax = maxSetWeightFor(slot.movementId)
            val isManualDiff = parsedEntered != null &&
                (liveMax <= 0.0 || normalizeWeight2dp(parsedEntered) != normalizeWeight2dp(liveMax))

            customWeightOverrideFlags[key] = isManualDiff
            if (isManualDiff) {
                prWeightOverrides[key] = checkNotNull(parsedEntered).trimmed()
                explicitlyAppliedPrKeys[key] = true
            } else {
                if (liveMax > 0.0) {
                    prWeightOverrides[key] = liveMax.trimmed()
                } else {
                    prWeightOverrides.remove(key)
                }
                if (isEditMode) {
                    val effectiveCandidate = parsedEntered ?: liveMax
                    val storedBest = AvailableBlockCodec.bestStoredRepMaxWeight(
                        repMaxes = repMaxes,
                        movementId = slot.movementId,
                        rmReps = slot.rmReps,
                        excludeSessionId = editingSessionId
                    )
                    if (effectiveCandidate > 0.0 &&
                        (key in persistedSessionPrKeys || key !in priorKeys || effectiveCandidate > storedBest)
                    ) {
                        explicitlyAppliedPrKeys[key] = true
                    }
                }
            }
        }
        mutationCount += 1
    }

    fun removeTrackedPrSlot(slot: TrackedPrSlot): DismissedPrSnapshot? {
        val index = trackedPrSlots.indexOfFirst {
            it.movementId == slot.movementId &&
                it.movementName.equals(slot.movementName, ignoreCase = true) &&
                it.rmReps == slot.rmReps
        }
        if (index < 0) return null
        val removedSlot = trackedPrSlots.removeAt(index)
        val key = prSlotKey(removedSlot.movementId, removedSlot.movementName, removedSlot.rmReps)
        val snapshot = DismissedPrSnapshot(
            slot = removedSlot,
            key = key,
            overrideText = prWeightOverrides.remove(key),
            wasCustomOverride = customWeightOverrideFlags.remove(key) == true,
            wasPersisted = persistedSessionPrKeys.remove(key) == true,
            wasExplicit = explicitlyAppliedPrKeys.remove(key) == true,
            slotIndex = index
        )
        mutationCount += 1
        return snapshot
    }

    fun restoreDismissedPr(snapshot: DismissedPrSnapshot) {
        val alreadyPresent = trackedPrSlots.any {
            prSlotKey(it.movementId, it.movementName, it.rmReps) == snapshot.key
        }
        if (!alreadyPresent) {
            val insertAt = snapshot.slotIndex.coerceIn(0, trackedPrSlots.size)
            trackedPrSlots.add(insertAt, snapshot.slot)
        }
        if (snapshot.overrideText != null) {
            prWeightOverrides[snapshot.key] = snapshot.overrideText
        }
        if (snapshot.wasCustomOverride) {
            customWeightOverrideFlags[snapshot.key] = true
        }
        if (snapshot.wasPersisted) {
            persistedSessionPrKeys[snapshot.key] = true
        }
        if (snapshot.wasExplicit) {
            explicitlyAppliedPrKeys[snapshot.key] = true
        }
        if (!snapshot.wasCustomOverride) {
            syncNonOverriddenPrWeightsFor(snapshot.slot.movementId, snapshot.slot.movementName)
        }
        mutationCount += 1
    }

    fun evaluatePrChips(
        exercises: List<Exercise>,
        repMaxes: List<RepMax>
    ): List<Pair<TrackedPrSlot, EvaluatedPrChip>> {
        val byLowerName = exercises.associateBy { it.name.trim().lowercase() }
        val excludeSession = if (isEditMode) editingSessionId else null
        return trackedPrSlots.map { slot ->
            val resolvedId = if (slot.movementId > 0L) {
                slot.movementId
            } else {
                byLowerName[slot.movementName.trim().lowercase()]?.id ?: 0L
            }
            val resolvedSlot = if (resolvedId != slot.movementId) slot.copy(movementId = resolvedId) else slot
            val key = prSlotKey(resolvedId, resolvedSlot.movementName, resolvedSlot.rmReps)
            val candidateWeight = if (isCustomWeightOverride(key)) {
                prWeightOverrides[key]?.let { AvailableBlockCodec.normalizedPositiveWeight(it) } ?: 0.0
            } else {
                maxSetWeightFor(resolvedId).takeIf { it > 0.0 }
                    ?: prWeightOverrides[key]?.let { AvailableBlockCodec.normalizedPositiveWeight(it) }
                    ?: 0.0
            }
            val storedBest = AvailableBlockCodec.bestStoredRepMaxWeight(
                repMaxes = repMaxes,
                movementId = resolvedId,
                rmReps = resolvedSlot.rmReps,
                excludeSessionId = excludeSession
            )
            resolvedSlot to AvailableBlockCodec.evaluateTrackedPrChip(resolvedSlot, candidateWeight, storedBest)
        }
    }

    fun toBlockDrafts(
        exercises: List<Exercise>,
        repMaxes: List<RepMax>
    ): List<BlockDraft> {
        val evaluated = evaluatePrChips(exercises, repMaxes)
        val activeSessionPrs = evaluated.mapNotNull { (slot, chip) ->
            val key = prSlotKey(chip.movementId, slot.movementName, chip.rmReps)
            val shouldSubmit = chip.movementId > 0L &&
                chip.candidateWeight > 0.0 &&
                (chip.isNewPr || persistedSessionPrKeys[key] == true || explicitlyAppliedPrKeys[key] == true)
            if (shouldSubmit) chip else null
        }
        val matchedExerciseIds = exerciseSections.map { it.exerciseId }.filter { it > 0L }.toSet()
        val unmatchedPrs = activeSessionPrs.filter { it.movementId !in matchedExerciseIds }

        val isStandaloneEx = itemKind == SessionItemKind.EXERCISE

        if (exerciseSections.isEmpty()) {
            return listOf(
                BlockDraft(
                    name = block.title,
                    kind = rawKind,
                    format = if (isStandaloneEx) STANDALONE_EXERCISE_SESSION_FORMAT else rawFormat,
                    scheme = rawScheme,
                    existingExerciseId = activeSessionPrs.firstOrNull()?.movementId,
                    newExerciseName = null,
                    routineId = if (isStandaloneEx) null else block.id.takeIf { it > 0L },
                    description = block.instructions,
                    resultText = "",
                    resultValue = null,
                    sets = listOf(SetDraft(reps = 1, weight = null)),
                    newRepMaxReps = null,
                    newRepMaxWeight = null,
                    section = rawSection,
                    exerciseIdsCsv = "",
                    subBlock = if (isStandaloneEx) "" else block.title,
                    isCompleted = rawIsCompleted,
                    newRepMaxes = activeSessionPrs.map { RepMaxDraft(it.movementId, it.rmReps, it.candidateWeight) }
                )
            )
        }

        val isCompact = isCompactComplex
        val anySkipInComplex = isCompact && exerciseSections.any { sec -> sec.skippedFlags.any { it } }
        // Compact complexes persist canonical round numbers (`groupIndex = S#`) whenever any
        // movement omits a round — either via an explicit Skip or because an active round was
        // left blank and is therefore dropped — so later rounds never shift down on rehydration.
        val writeCompactGroupIndices = isCompact && (
            anySkipInComplex || exerciseSections.any { sec ->
                val count = maxOf(sec.weights.size, sec.repsInputs.size, sec.metricInputs.size)
                val loggedRounds = (0 until count).count { sIdx -> sec.hasLoggedValueAt(sIdx) }
                loggedRounds in 1 until count
            }
        )

        return exerciseSections.mapIndexed { idx, exState ->
            val mappedSets = mutableListOf<SetDraft>()
            val setCount = maxOf(exState.weights.size, exState.repsInputs.size, exState.metricInputs.size)
            for (setIdx in 0 until setCount) {
                if (exState.skippedFlags.getOrElse(setIdx) { false }) continue
                val groupIdx = if (isCompact) {
                    if (writeCompactGroupIndices) setIdx + 1 else null
                } else {
                    exState.groupIndices.getOrNull(setIdx)?.takeIf { it > 0 }
                }
                if (exState.activeMetric == ExerciseTargetMetric.REPS) {
                    val weight = AvailableBlockCodec.normalizedPositiveWeight(
                        exState.weights.getOrNull(setIdx).orEmpty()
                    )
                    val parsedReps = exState.repsInputs.getOrNull(setIdx)
                        ?.trim()
                        ?.toIntOrNull()
                        ?.takeIf { it > 0 }
                    if (weight != null || parsedReps != null) {
                        val finalReps = parsedReps
                            ?: exState.perSetReps.getOrNull(setIdx)?.coerceAtLeast(1)
                            ?: exState.defaultReps.coerceAtLeast(1)
                        mappedSets += SetDraft(
                            reps = finalReps,
                            weight = weight,
                            metricValue = null,
                            groupIndex = groupIdx
                        )
                    }
                } else {
                    val metricVal = AvailableBlockCodec.normalizedPositiveWeight(
                        exState.metricInputs.getOrNull(setIdx).orEmpty()
                    )
                    if (metricVal != null) {
                        mappedSets += SetDraft(
                            reps = 1,
                            weight = null,
                            metricValue = metricVal,
                            groupIndex = groupIdx
                        )
                    }
                }
            }
            val allSkipped = exState.skippedFlags.isNotEmpty() && exState.skippedFlags.all { it }
            val sets = when {
                mappedSets.isNotEmpty() -> mappedSets
                isCompact && allSkipped -> emptyList()
                isCompact && writeCompactGroupIndices -> {
                    val activeIndices = (0 until setCount).filter { !exState.skippedFlags.getOrElse(it) { false } }
                    if (activeIndices.isEmpty()) {
                        emptyList()
                    } else {
                        activeIndices.map { sIdx ->
                            SetDraft(
                                reps = if (exState.activeMetric == ExerciseTargetMetric.REPS) {
                                    exState.perSetReps.getOrNull(sIdx)?.coerceAtLeast(1)
                                        ?: exState.defaultReps.coerceAtLeast(1)
                                } else {
                                    1
                                },
                                weight = null,
                                metricValue = null,
                                groupIndex = sIdx + 1
                            )
                        }
                    }
                }
                else -> listOf(SetDraft(reps = exState.defaultReps.coerceAtLeast(1), weight = null))
            }
            val prsForThis = activeSessionPrs.filter { it.movementId == exState.exerciseId } +
                if (idx == 0) unmatchedPrs else emptyList()
            val cleanRawNotes = RoutineTargetCodec.extractCleanNotes(exState.rawNotes)
            val encodedNotes = RoutineTargetCodec.encodeNotesWithLogMetric(cleanRawNotes, exState.activeMetric)

            BlockDraft(
                name = exState.exerciseName,
                kind = exState.rawKind,
                format = if (isStandaloneEx) STANDALONE_EXERCISE_SESSION_FORMAT else exState.rawFormat,
                scheme = exState.rawScheme,
                existingExerciseId = exState.exerciseId.takeIf { it > 0L },
                newExerciseName = if (exState.exerciseId <= 0L) exState.exerciseName else null,
                routineId = if (isStandaloneEx) null else block.id.takeIf { it > 0L },
                description = block.instructions,
                resultText = "",
                resultValue = null,
                sets = sets,
                newRepMaxReps = null,
                newRepMaxWeight = null,
                section = exState.rawSection,
                exerciseIdsCsv = exState.exerciseId.takeIf { it > 0L }?.toString().orEmpty(),
                subBlock = if (isStandaloneEx) "" else block.title.ifBlank { exState.subBlockTitle },
                isCompleted = exState.rawIsCompleted,
                newRepMaxes = prsForThis.map { RepMaxDraft(it.movementId, it.rmReps, it.candidateWeight) },
                notes = encodedNotes
            )
        }
    }

    fun inheritedTrackedPrSlotsForParent(exercises: List<Exercise>): Pair<Long, List<TrackedPrSlot>>? {
        if (itemKind == SessionItemKind.EXERCISE || block.id <= 0L || trackedPrSlots.isEmpty()) return null
        val byLowerName = exercises.associateBy { it.name.trim().lowercase() }
        val resolved = AvailableBlockCodec.normalizeTrackedPrSlots(
            trackedPrSlots.map { slot ->
                if (slot.movementId > 0L) slot
                else slot.copy(movementId = byLowerName[slot.movementName.trim().lowercase()]?.id ?: 0L)
            }
        )
        return if (resolved.isNotEmpty()) block.id to resolved else null
    }
}

internal fun firstTimeSubtitleForMetric(
    metric: ExerciseTargetMetric,
    libraryMetricType: MetricType = MetricType.WEIGHT
): String = when (metric) {
    ExerciseTargetMetric.CALORIES -> "First time logging — enter calories"
    ExerciseTargetMetric.TIME_SECONDS -> "First time logging — enter time (sec)"
    ExerciseTargetMetric.METERS -> "First time logging — enter meters"
    ExerciseTargetMetric.REPS -> if (libraryMetricType == MetricType.REPS) {
        "First time logging — enter reps & optional weight"
    } else {
        "First time logging — enter set weights"
    }
}

private fun buildTargetOrPrefillExerciseState(
    exerciseId: Long,
    exerciseName: String,
    libraryMetricType: MetricType,
    targetSetsCount: Int?,
    targetSpec: RoutineExerciseTargetSpec?,
    lastWeights: List<Double?>?,
    lastReps: List<Int>?,
    lastMetricValues: List<Double?>?,
    lastLoggedMetric: ExerciseTargetMetric?,
    lastDateFormatted: String?,
    defaultEmptyTileCount: Int,
    rawFormat: String = ""
): LoadedAvailableBlockExerciseState {
    if (targetSetsCount != null && targetSpec != null) {
        val cleanSets = targetSetsCount.coerceAtLeast(1)
        val effectiveValues = targetSpec.effectiveValues(cleanSets)
        val hasSkipped = effectiveValues.any { it <= 0 }
        val activeRoundIndices = effectiveValues.indices.filter { effectiveValues[it] > 0 }
            .ifEmpty { listOf(0) }
        val activeMetric = targetSpec.metric
        val hasPriorWeights = activeMetric == ExerciseTargetMetric.REPS &&
            lastWeights?.any { (it ?: 0.0) > 0.0 } == true &&
            lastDateFormatted != null

        val initialWeights = activeRoundIndices.mapIndexed { activeIdx, _ ->
            if (activeMetric == ExerciseTargetMetric.REPS) {
                lastWeights?.getOrNull(activeIdx)?.takeIf { it > 0.0 }?.trimmed().orEmpty()
            } else {
                ""
            }
        }
        val initialRepsInputs = activeRoundIndices.map { roundIdx ->
            if (activeMetric == ExerciseTargetMetric.REPS) {
                effectiveValues[roundIdx].takeIf { it > 0 }?.toString().orEmpty()
            } else {
                ""
            }
        }
        val initialPerSetReps = activeRoundIndices.map { roundIdx ->
            if (activeMetric == ExerciseTargetMetric.REPS) {
                effectiveValues[roundIdx].coerceAtLeast(1)
            } else {
                1
            }
        }
        val initialMetricInputs = activeRoundIndices.map { roundIdx ->
            if (activeMetric != ExerciseTargetMetric.REPS) {
                effectiveValues[roundIdx].takeIf { it > 0 }?.toString().orEmpty()
            } else {
                ""
            }
        }
        val initialGroups = activeRoundIndices.map { roundIdx ->
            if (hasSkipped) roundIdx + 1 else null
        }
        val banner = "Target: ${formatDailyRoutineTargetSummary(cleanSets, targetSpec)}"
        val dateLabel = if (hasPriorWeights) {
            "Pre-filled weights from last session ($lastDateFormatted)"
        } else {
            null
        }
        return LoadedAvailableBlockExerciseState(
            exerciseId = exerciseId,
            exerciseName = exerciseName,
            lastSessionDateLabel = dateLabel,
            initialWeights = initialWeights,
            initialPerSetReps = initialPerSetReps,
            rawFormat = rawFormat,
            initialMetric = activeMetric,
            initialRepsInputs = initialRepsInputs,
            initialMetricInputs = initialMetricInputs,
            initialGroupIndices = initialGroups,
            targetBanner = banner
        )
    }

    val hasPriorWeights = lastWeights?.any { (it ?: 0.0) > 0.0 } == true
    val hasPriorMetrics = lastMetricValues?.any { (it ?: 0.0) > 0.0 } == true
    val hasPriorReps = lastReps != null && (lastReps.size > 1 || lastReps.any { it > 1 })
    val hasPriorData = (hasPriorWeights || hasPriorMetrics || hasPriorReps) && lastDateFormatted != null

    if (hasPriorData) {
        val activeMetric = lastLoggedMetric ?: ExerciseTargetMetric.fromLibraryMetricType(libraryMetricType)
        val count = maxOf(
            lastWeights?.size ?: 0,
            lastMetricValues?.size ?: 0,
            lastReps?.size ?: 0
        ).coerceAtLeast(1)
        val initialWeights = List(count) { idx ->
            if (activeMetric == ExerciseTargetMetric.REPS) {
                lastWeights?.getOrNull(idx)?.takeIf { it > 0.0 }?.trimmed().orEmpty()
            } else {
                ""
            }
        }
        val initialRepsInputs = List(count) { idx ->
            if (activeMetric == ExerciseTargetMetric.REPS) {
                val r = lastReps?.getOrNull(idx) ?: 0
                val w = lastWeights?.getOrNull(idx) ?: 0.0
                if (r > 1 || (w <= 0.0 && r > 0)) r.toString() else ""
            } else {
                ""
            }
        }
        val initialPerSetReps = List(count) { idx ->
            lastReps?.getOrNull(idx)?.coerceAtLeast(1) ?: 1
        }
        val initialMetricInputs = List(count) { idx ->
            if (activeMetric != ExerciseTargetMetric.REPS) {
                lastMetricValues?.getOrNull(idx)?.takeIf { it > 0.0 }?.trimmed().orEmpty()
            } else {
                ""
            }
        }
        return LoadedAvailableBlockExerciseState(
            exerciseId = exerciseId,
            exerciseName = exerciseName,
            lastSessionDateLabel = "Pre-filled from last session ($lastDateFormatted)",
            initialWeights = initialWeights,
            initialPerSetReps = initialPerSetReps,
            rawFormat = rawFormat,
            initialMetric = activeMetric,
            initialRepsInputs = initialRepsInputs,
            initialMetricInputs = initialMetricInputs
        )
    }

    val defaultMetric = ExerciseTargetMetric.fromLibraryMetricType(libraryMetricType)
    val emptyTiles = List(defaultEmptyTileCount.coerceAtLeast(1)) { "" }
    return LoadedAvailableBlockExerciseState(
        exerciseId = exerciseId,
        exerciseName = exerciseName,
        lastSessionDateLabel = firstTimeSubtitleForMetric(defaultMetric, libraryMetricType),
        initialWeights = emptyTiles,
        rawFormat = rawFormat,
        initialMetric = defaultMetric,
        initialRepsInputs = emptyTiles,
        initialMetricInputs = emptyTiles
    )
}

internal fun buildLoadedAvailableBlockState(
    block: AvailableBlock,
    sessions: List<SessionWithBlocks>,
    exercises: List<Exercise>,
    weightUnit: String,
    isEditMode: Boolean = false,
    editingSessionId: Long? = null,
    routineTargetSetsCount: Int? = null,
    routineExerciseTargets: Map<Long, RoutineExerciseTargetSpec> = emptyMap(),
    routineExerciseTargetsByName: Map<String, RoutineExerciseTargetSpec> = emptyMap(),
    sourceRoutineItem: DailyRoutineItem? = null
): LoadedAvailableBlockState {
    val exercisesById = exercises.associateBy { it.id }
    val exercisesByLowerName = exercises.associateBy { it.name.trim().lowercase() }
    val lastSession = AvailableBlockCodec.resolveLastBlockSession(
        block = block,
        sessions = sessions,
        exercises = exercises,
        weightUnit = weightUnit,
        excludeSessionId = editingSessionId
    )
    val resolvedSlots = AvailableBlockCodec.normalizeTrackedPrSlots(
        block.trackedPrs.map { slot ->
            if (slot.movementId > 0L) slot
            else slot.copy(movementId = exercisesByLowerName[slot.movementName.trim().lowercase()]?.id ?: 0L)
        }
    )

    if (block.exercises.size <= 1) {
        val exStates = block.exercises.map { ex ->
            val exObj = exercisesById[ex.id] ?: exercisesByLowerName[ex.name.trim().lowercase()]
            val lowerName = ex.name.trim().lowercase()
            val targetSpec = resolveBlockExerciseTargetSpec(ex, routineExerciseTargets, routineExerciseTargetsByName)
            val prevWeights = lastSession?.exerciseWeightsById?.get(ex.id)
                ?: lastSession?.exerciseWeightsByName?.get(lowerName)
            val prevReps = lastSession?.exerciseRepsById?.get(ex.id)
                ?: lastSession?.exerciseRepsByName?.get(lowerName)
            val prevMetrics = lastSession?.exerciseMetricValuesById?.get(ex.id)
                ?: lastSession?.exerciseMetricValuesByName?.get(lowerName)
            val prevLoggedMetric = lastSession?.exerciseLoggedMetricById?.get(ex.id)
                ?: lastSession?.exerciseLoggedMetricByName?.get(lowerName)

            val built = buildTargetOrPrefillExerciseState(
                exerciseId = ex.id,
                exerciseName = ex.name,
                libraryMetricType = exObj?.metricType ?: MetricType.WEIGHT,
                targetSetsCount = if (targetSpec != null) routineTargetSetsCount else null,
                targetSpec = targetSpec,
                lastWeights = prevWeights,
                lastReps = prevReps,
                lastMetricValues = prevMetrics,
                lastLoggedMetric = prevLoggedMetric,
                lastDateFormatted = lastSession?.dateFormatted,
                defaultEmptyTileCount = 3
            )
            LoadedAvailableBlockExerciseState(
                exerciseId = built.exerciseId,
                exerciseName = built.exerciseName,
                lastSessionDateLabel = built.lastSessionDateLabel,
                initialWeights = built.weights.toList(),
                initialPerSetReps = built.perSetReps.toList(),
                defaultReps = built.defaultReps,
                sourceBlockId = built.sourceBlockId,
                rawFormat = built.rawFormat,
                rawScheme = built.rawScheme,
                rawSection = built.rawSection,
                rawKind = built.rawKind,
                rawIsCompleted = built.rawIsCompleted,
                subBlockTitle = built.subBlockTitle,
                initialMetric = built.activeMetric,
                initialRepsInputs = built.repsInputs.toList(),
                initialMetricInputs = built.metricInputs.toList(),
                initialGroupIndices = built.groupIndices.toList(),
                targetBanner = built.targetBanner,
                rawNotes = built.rawNotes,
                libraryMetricType = exObj?.metricType ?: MetricType.WEIGHT,
                initialSkippedFlags = built.skippedFlags.toList()
            )
        }
        return LoadedAvailableBlockState(
            block = block,
            exerciseStates = exStates,
            initialTrackedPrs = resolvedSlots,
            isEditMode = isEditMode,
            editingSessionId = editingSessionId,
            sourceRoutineItem = sourceRoutineItem
        )
    }

    val specsByIndex = block.exercises.map { ex ->
        resolveBlockExerciseTargetSpec(ex, routineExerciseTargets, routineExerciseTargetsByName)
    }
    val hasRoutineTargets = routineTargetSetsCount != null && specsByIndex.any { it != null }

    data class LastExerciseSlice(
        val exObj: Exercise?,
        val prevWeights: List<Double?>?,
        val prevReps: List<Int>?,
        val prevMetrics: List<Double?>?,
        val prevLoggedMetric: ExerciseTargetMetric?,
        val prevGroupIndices: List<Int?>?,
        val hasMeaningfulData: Boolean,
        val hasHistoricalWeights: Boolean
    )

    val lastSlices = block.exercises.map { ex ->
        val exObj = exercisesById[ex.id] ?: exercisesByLowerName[ex.name.trim().lowercase()]
        val lowerName = ex.name.trim().lowercase()
        val prevWeights = lastSession?.exerciseWeightsById?.get(ex.id)
            ?: lastSession?.exerciseWeightsByName?.get(lowerName)
        val prevReps = lastSession?.exerciseRepsById?.get(ex.id)
            ?: lastSession?.exerciseRepsByName?.get(lowerName)
        val prevMetrics = lastSession?.exerciseMetricValuesById?.get(ex.id)
            ?: lastSession?.exerciseMetricValuesByName?.get(lowerName)
        val prevLoggedMetric = lastSession?.exerciseLoggedMetricById?.get(ex.id)
            ?: lastSession?.exerciseLoggedMetricByName?.get(lowerName)
        val prevGroups = lastSession?.exerciseGroupIndicesById?.get(ex.id)
            ?: lastSession?.exerciseGroupIndicesByName?.get(lowerName)

        val hasPriorW = prevWeights?.any { (it ?: 0.0) > 0.0 } == true
        val hasPriorM = prevMetrics?.any { (it ?: 0.0) > 0.0 } == true
        val hasPriorR = prevReps != null && (prevReps.size > 1 || prevReps.any { it > 1 })
        val hasPriorG = prevGroups?.any { it != null } == true
        LastExerciseSlice(
            exObj = exObj,
            prevWeights = prevWeights,
            prevReps = prevReps,
            prevMetrics = prevMetrics,
            prevLoggedMetric = prevLoggedMetric,
            prevGroupIndices = prevGroups,
            hasMeaningfulData = hasPriorW || hasPriorM || hasPriorR || hasPriorG,
            hasHistoricalWeights = hasPriorW
        )
    }

    val anyHistoryData = lastSession?.dateFormatted != null && lastSlices.any { it.hasMeaningfulData }
    val totalRounds = when {
        hasRoutineTargets -> checkNotNull(routineTargetSetsCount).coerceAtLeast(1)
        anyHistoryData -> lastSlices.maxOfOrNull { slice ->
            val maxG = slice.prevGroupIndices?.filterNotNull()?.maxOrNull() ?: 0
            val maxLen = maxOf(
                slice.prevWeights?.size ?: 0,
                slice.prevMetrics?.size ?: 0,
                slice.prevReps?.size ?: 0
            )
            maxOf(maxG, maxLen)
        }?.coerceAtLeast(1) ?: 3
        else -> 3
    }

    var anyPreFilledWeightsOrHistory = false
    val exStates = block.exercises.mapIndexed { idx, ex ->
        val slice = lastSlices[idx]
        val libMetric = slice.exObj?.metricType ?: MetricType.WEIGHT
        val targetSpec = specsByIndex[idx]
        val activeMetric = targetSpec?.metric
            ?: slice.prevLoggedMetric
            ?: ExerciseTargetMetric.fromLibraryMetricType(libMetric)

        val weightsArr = MutableList(totalRounds) { "" }
        val repsInputsArr = MutableList(totalRounds) { "" }
        val perSetRepsArr = MutableList(totalRounds) { 1 }
        val metricInputsArr = MutableList(totalRounds) { "" }
        val skippedArr = MutableList(totalRounds) { false }
        val groupArr = List(totalRounds) { it + 1 }

        if (hasRoutineTargets && targetSpec == null) {
            // Movement without its own routine target: keep every canonical round active and
            // pre-fill from history (by groupIndex, else ordinal) instead of fabricating a
            // `1` rep / `1` cal / `1` s target value.
            val hasExplicitGroups = slice.prevGroupIndices?.any { it != null } == true
            for (r in 0 until totalRounds) {
                val histIdx = if (hasExplicitGroups) {
                    slice.prevGroupIndices?.indexOfFirst { it == r + 1 }?.takeIf { it >= 0 }
                } else {
                    r
                }
                if (activeMetric == ExerciseTargetMetric.REPS) {
                    val w = histIdx?.let { slice.prevWeights?.getOrNull(it) }?.takeIf { it > 0.0 }
                    if (w != null) {
                        weightsArr[r] = w.trimmed()
                        anyPreFilledWeightsOrHistory = true
                    }
                    val rVal = histIdx?.let { slice.prevReps?.getOrNull(it) }?.takeIf { it > 0 }
                    if (rVal != null) {
                        perSetRepsArr[r] = rVal
                        if (rVal > 1 || w == null) repsInputsArr[r] = rVal.toString()
                    }
                } else {
                    val mv = histIdx?.let { slice.prevMetrics?.getOrNull(it) }?.takeIf { it > 0.0 }
                    if (mv != null) {
                        metricInputsArr[r] = mv.trimmed()
                        anyPreFilledWeightsOrHistory = true
                    }
                }
            }
        } else if (hasRoutineTargets) {
            val effectiveTargets = checkNotNull(targetSpec).effectiveValues(totalRounds)
            val hasExplicitGroups = slice.prevGroupIndices?.any { it != null } == true
            var activeOrdinal = 0
            for (r in 0 until totalRounds) {
                val targetVal = effectiveTargets.getOrNull(r) ?: 1
                if (targetVal <= 0) {
                    skippedArr[r] = true
                } else {
                    skippedArr[r] = false
                    val histIdx = if (hasExplicitGroups) {
                        slice.prevGroupIndices?.indexOfFirst { it == r + 1 }?.takeIf { it >= 0 }
                    } else {
                        activeOrdinal
                    }
                    activeOrdinal += 1
                    if (activeMetric == ExerciseTargetMetric.REPS) {
                        val w = histIdx?.let { slice.prevWeights?.getOrNull(it) }?.takeIf { it > 0.0 }
                        if (w != null) {
                            weightsArr[r] = w.trimmed()
                            anyPreFilledWeightsOrHistory = true
                        }
                        perSetRepsArr[r] = targetVal
                        repsInputsArr[r] = targetVal.toString()
                    } else {
                        metricInputsArr[r] = targetVal.toString()
                    }
                }
            }
        } else if (anyHistoryData) {
            val exWasInLast = slice.prevWeights != null || slice.prevReps != null || slice.prevMetrics != null
            val priorCount = maxOf(
                slice.prevWeights?.size ?: 0,
                slice.prevReps?.size ?: 0,
                slice.prevMetrics?.size ?: 0
            )
            when {
                !exWasInLast -> {
                    // New movement added to template: keep active across canonical rounds
                    for (r in 0 until totalRounds) skippedArr[r] = false
                }
                priorCount == 0 -> {
                    // Explicitly skipped across every round in last session
                    for (r in 0 until totalRounds) skippedArr[r] = true
                }
                else -> {
                    for (r in 0 until totalRounds) skippedArr[r] = true
                    val hasExplicitGroups = slice.prevGroupIndices?.any { it != null } == true
                    for (sIdx in 0 until priorCount) {
                        val rIdx = (if (hasExplicitGroups) {
                            slice.prevGroupIndices?.getOrNull(sIdx) ?: (sIdx + 1)
                        } else {
                            sIdx + 1
                        }) - 1
                        if (rIdx in 0 until totalRounds) {
                            skippedArr[rIdx] = false
                            if (activeMetric == ExerciseTargetMetric.REPS) {
                                val w = slice.prevWeights?.getOrNull(sIdx)?.takeIf { it > 0.0 }
                                if (w != null) {
                                    weightsArr[rIdx] = w.trimmed()
                                    anyPreFilledWeightsOrHistory = true
                                }
                                val rVal = slice.prevReps?.getOrNull(sIdx)?.coerceAtLeast(1) ?: 1
                                perSetRepsArr[rIdx] = rVal
                                if (rVal > 1 || (w == null && rVal > 0) || hasExplicitGroups) {
                                    repsInputsArr[rIdx] = rVal.toString()
                                }
                                if (rVal > 1) anyPreFilledWeightsOrHistory = true
                            } else {
                                val mv = slice.prevMetrics?.getOrNull(sIdx)?.takeIf { it > 0.0 }
                                if (mv != null) {
                                    metricInputsArr[rIdx] = mv.trimmed()
                                    anyPreFilledWeightsOrHistory = true
                                }
                            }
                        }
                    }
                }
            }
        }

        val banner = targetSpec?.let { "Target: ${formatDailyRoutineTargetSummary(totalRounds, it)}" }
        LoadedAvailableBlockExerciseState(
            exerciseId = ex.id,
            exerciseName = ex.name,
            lastSessionDateLabel = null,
            initialWeights = weightsArr,
            initialPerSetReps = perSetRepsArr,
            initialMetric = activeMetric,
            initialRepsInputs = repsInputsArr,
            initialMetricInputs = metricInputsArr,
            initialGroupIndices = groupArr,
            targetBanner = banner,
            libraryMetricType = libMetric,
            initialSkippedFlags = skippedArr
        )
    }

    val showPreFill = !isEditMode && anyPreFilledWeightsOrHistory && lastSession?.dateFormatted != null
    return LoadedAvailableBlockState(
        block = block,
        exerciseStates = exStates,
        initialTrackedPrs = resolvedSlots,
        isEditMode = isEditMode,
        editingSessionId = editingSessionId,
        sourceRoutineItem = sourceRoutineItem,
        lastSessionDateFormatted = if (showPreFill) lastSession?.dateFormatted else null,
        hasPreFilledHistory = showPreFill
    )
}

internal fun buildCustomAvailableBlockState(
    block: AvailableBlock,
    isEditMode: Boolean = false,
    editingSessionId: Long? = null,
    exercises: List<Exercise> = emptyList()
): LoadedAvailableBlockState {
    val byId = exercises.associateBy { it.id }
    val byLower = exercises.associateBy { it.name.trim().lowercase() }
    val isMulti = block.exercises.size >= 2
    val exStates = block.exercises.map { ex ->
        val exObj = byId[ex.id] ?: byLower[ex.name.trim().lowercase()]
        val libMetric = exObj?.metricType ?: MetricType.WEIGHT
        val defaultMetric = exObj?.let { ExerciseTargetMetric.fromLibraryMetricType(it.metricType) }
            ?: ExerciseTargetMetric.REPS
        LoadedAvailableBlockExerciseState(
            exerciseId = ex.id,
            exerciseName = ex.name,
            lastSessionDateLabel = null,
            initialWeights = listOf("", "", ""),
            initialMetric = defaultMetric,
            initialRepsInputs = listOf("", "", ""),
            initialMetricInputs = listOf("", "", ""),
            initialGroupIndices = if (isMulti) listOf(1, 2, 3) else listOf(null, null, null),
            libraryMetricType = libMetric,
            initialSkippedFlags = listOf(false, false, false)
        )
    }
    return LoadedAvailableBlockState(
        block = block,
        exerciseStates = exStates,
        initialTrackedPrs = emptyList(),
        isEditMode = isEditMode,
        editingSessionId = editingSessionId
    )
}

internal fun buildHydratedBlockState(
    seed: HydratedAvailableBlockSeed,
    exercises: List<Exercise>,
    editingSessionId: Long
): LoadedAvailableBlockState {
    val byId = exercises.associateBy { it.id }
    val byLowerName = exercises.associateBy { it.name.trim().lowercase() }
    val isMultiComplex = !seed.isStandaloneExercise && seed.exercises.size >= 2

    val exStates = if (!isMultiComplex) {
        seed.exercises.map { exSeed ->
            val initialWeights = exSeed.weights.map { w -> if (w > 0.0) w.trimmed() else "" }
            val explicitLogMetric = RoutineTargetCodec.decodeLogMetricHeader(exSeed.rawNotes)
            val exObj = byId[exSeed.movementId] ?: byLowerName[exSeed.movementName.trim().lowercase()]
            val libMetric = exObj?.metricType ?: MetricType.WEIGHT
            val initialMetric = exSeed.loggedMetric
                ?: explicitLogMetric
                ?: if (exSeed.metricValues.any { (it ?: 0.0) > 0.0 }) {
                    exObj?.let { ExerciseTargetMetric.fromLibraryMetricType(it.metricType) }
                        ?.takeIf { it != ExerciseTargetMetric.REPS }
                        ?: ExerciseTargetMetric.CALORIES
                } else {
                    ExerciseTargetMetric.REPS
                }
            val initialMetricInputs = List(initialWeights.size) { idx ->
                exSeed.metricValues.getOrNull(idx)?.takeIf { it > 0.0 }?.trimmed().orEmpty()
            }
            val initialRepsInputs = List(initialWeights.size) { idx ->
                val r = exSeed.perSetReps.getOrNull(idx) ?: exSeed.defaultReps
                val w = exSeed.weights.getOrNull(idx) ?: 0.0
                if (initialMetric == ExerciseTargetMetric.REPS &&
                    (r > 1 || (w <= 0.0 && r > 0 && explicitLogMetric == ExerciseTargetMetric.REPS))
                ) {
                    r.toString()
                } else {
                    ""
                }
            }
            LoadedAvailableBlockExerciseState(
                exerciseId = exSeed.movementId,
                exerciseName = exSeed.movementName,
                lastSessionDateLabel = null, // Hidden in edit mode (Option 2.1A)
                initialWeights = initialWeights,
                initialPerSetReps = exSeed.perSetReps,
                defaultReps = exSeed.defaultReps,
                sourceBlockId = exSeed.sourceBlockId,
                rawFormat = exSeed.rawFormat,
                rawScheme = exSeed.rawScheme,
                rawSection = exSeed.rawSection,
                rawKind = exSeed.rawKind,
                rawIsCompleted = exSeed.rawIsCompleted,
                subBlockTitle = exSeed.subBlockTitle,
                initialMetric = initialMetric,
                initialRepsInputs = initialRepsInputs,
                initialMetricInputs = initialMetricInputs,
                initialGroupIndices = exSeed.groupIndices,
                rawNotes = exSeed.rawNotes,
                libraryMetricType = libMetric
            )
        }
    } else {
        val nonEmptySeeds = seed.exercises.filter { !it.hasNoLoggedSets }
        val totalRounds = nonEmptySeeds.maxOfOrNull { exSeed ->
            maxOf(
                exSeed.weights.size,
                exSeed.groupIndices.filterNotNull().maxOrNull() ?: 0
            )
        }?.coerceAtLeast(1) ?: 1

        seed.exercises.map { exSeed ->
            val explicitLogMetric = RoutineTargetCodec.decodeLogMetricHeader(exSeed.rawNotes)
            val exObj = byId[exSeed.movementId] ?: byLowerName[exSeed.movementName.trim().lowercase()]
            val libMetric = exObj?.metricType ?: MetricType.WEIGHT
            val initialMetric = exSeed.loggedMetric
                ?: explicitLogMetric
                ?: if (exSeed.metricValues.any { (it ?: 0.0) > 0.0 }) {
                    exObj?.let { ExerciseTargetMetric.fromLibraryMetricType(it.metricType) }
                        ?.takeIf { it != ExerciseTargetMetric.REPS }
                        ?: ExerciseTargetMetric.CALORIES
                } else {
                    ExerciseTargetMetric.REPS
                }

            val weightsArr = MutableList(totalRounds) { "" }
            val perSetRepsArr = MutableList(totalRounds) { exSeed.defaultReps.coerceAtLeast(1) }
            val repsInputsArr = MutableList(totalRounds) { "" }
            val metricInputsArr = MutableList(totalRounds) { "" }
            val groupArr = List(totalRounds) { it + 1 }
            val skippedArr = MutableList(totalRounds) {
                if (nonEmptySeeds.isNotEmpty()) exSeed.hasNoLoggedSets else false
            }

            if (!exSeed.hasNoLoggedSets) {
                val hasExplicitGroups = exSeed.groupIndices.any { it != null }
                for (r in 0 until totalRounds) skippedArr[r] = true
                for (sIdx in 0 until exSeed.weights.size) {
                    val rIdx = (if (hasExplicitGroups) {
                        exSeed.groupIndices.getOrNull(sIdx) ?: (sIdx + 1)
                    } else {
                        sIdx + 1
                    }) - 1
                    if (rIdx in 0 until totalRounds) {
                        skippedArr[rIdx] = false
                        val w = exSeed.weights.getOrNull(sIdx) ?: 0.0
                        weightsArr[rIdx] = if (w > 0.0) w.trimmed() else ""
                        val r = exSeed.perSetReps.getOrNull(sIdx)?.coerceAtLeast(1) ?: exSeed.defaultReps.coerceAtLeast(1)
                        perSetRepsArr[rIdx] = r
                        metricInputsArr[rIdx] = if (initialMetric != ExerciseTargetMetric.REPS) {
                            exSeed.metricValues.getOrNull(sIdx)?.takeIf { it > 0.0 }?.trimmed().orEmpty()
                        } else {
                            ""
                        }
                        repsInputsArr[rIdx] = if (initialMetric == ExerciseTargetMetric.REPS &&
                            (r > 1 || (w <= 0.0 && r > 0 && explicitLogMetric == ExerciseTargetMetric.REPS) || hasExplicitGroups)
                        ) {
                            r.toString()
                        } else {
                            ""
                        }
                    }
                }
            }

            LoadedAvailableBlockExerciseState(
                exerciseId = exSeed.movementId,
                exerciseName = exSeed.movementName,
                lastSessionDateLabel = null,
                initialWeights = weightsArr,
                initialPerSetReps = perSetRepsArr,
                defaultReps = exSeed.defaultReps,
                sourceBlockId = exSeed.sourceBlockId,
                rawFormat = exSeed.rawFormat,
                rawScheme = exSeed.rawScheme,
                rawSection = exSeed.rawSection,
                rawKind = exSeed.rawKind,
                rawIsCompleted = exSeed.rawIsCompleted,
                subBlockTitle = exSeed.subBlockTitle,
                initialMetric = initialMetric,
                initialRepsInputs = repsInputsArr,
                initialMetricInputs = metricInputsArr,
                initialGroupIndices = groupArr,
                rawNotes = exSeed.rawNotes,
                libraryMetricType = libMetric,
                initialSkippedFlags = skippedArr
            )
        }
    }

    // Keep the session's own historical prescription (seed.cleanNotes falls back to the template
    // instructions when blank) so editing a log never rewrites it with the template's current text.
    val effectiveBlock = seed.resolvedAvailableBlock?.let { resolved ->
        resolved.copy(instructions = seed.cleanNotes.ifBlank { resolved.instructions })
    } ?: AvailableBlock(
        id = seed.rawRoutineId ?: 0L,
        title = seed.blockTitle,
        instructions = seed.cleanNotes,
        exercises = exStates
            .filter { it.exerciseId > 0L }
            .distinctBy { it.exerciseId }
            .map { AvailableBlockExercise(id = it.exerciseId, name = it.exerciseName, tracksRepMax = true) },
        trackedPrs = emptyList()
    )

    val templateSlots = seed.resolvedAvailableBlock?.trackedPrs.orEmpty()
    val persistedSlots = seed.persistedRepMaxes.mapNotNull { rm ->
        val exName = byId[rm.exerciseId]?.name
            ?: exStates.firstOrNull { it.exerciseId == rm.exerciseId }?.exerciseName
            ?: return@mapNotNull null
        TrackedPrSlot(
            movementId = rm.exerciseId,
            movementName = exName,
            rmReps = rm.reps
        )
    }
    val combinedSlots = AvailableBlockCodec.normalizeTrackedPrSlots(
        (templateSlots + persistedSlots).map { slot ->
            if (slot.movementId > 0L) slot
            else slot.copy(movementId = byLowerName[slot.movementName.trim().lowercase()]?.id ?: 0L)
        }
    )

    return LoadedAvailableBlockState(
        block = effectiveBlock,
        exerciseStates = exStates,
        initialTrackedPrs = combinedSlots,
        isEditMode = true,
        editingSessionId = editingSessionId,
        initialPersistedRepMaxes = seed.persistedRepMaxes,
        rawFormat = seed.rawFormat,
        rawScheme = seed.rawScheme,
        rawSection = seed.rawSection,
        rawKind = seed.rawKind,
        rawIsCompleted = seed.rawIsCompleted,
        itemKind = if (seed.isStandaloneExercise) SessionItemKind.EXERCISE else SessionItemKind.BLOCK
    )
}

internal fun buildLoadedStandaloneExerciseState(
    exercise: Exercise,
    note: String = "",
    defaultTrackedPrs: List<TrackedPrSlot> = emptyList(),
    sessions: List<SessionWithBlocks>,
    repMaxes: List<RepMax>,
    weightUnit: String = "kg",
    isEditMode: Boolean = false,
    editingSessionId: Long? = null,
    targetSetsCount: Int? = null,
    targetSpec: RoutineExerciseTargetSpec? = null,
    sourceRoutineItem: DailyRoutineItem? = null
): LoadedAvailableBlockState {
    val last = DailyRoutineCodec.resolveLastExerciseSession(
        exercise = exercise,
        sessions = sessions,
        weightUnit = weightUnit,
        excludeSessionId = editingSessionId
    )
    val meaningfulLast = last?.takeIf { it.topWeight != null }
    val exState = buildTargetOrPrefillExerciseState(
        exerciseId = exercise.id,
        exerciseName = exercise.name,
        libraryMetricType = exercise.metricType,
        targetSetsCount = if (targetSpec != null) targetSetsCount else null,
        targetSpec = targetSpec,
        lastWeights = meaningfulLast?.weights,
        lastReps = meaningfulLast?.reps,
        lastMetricValues = meaningfulLast?.metricValues,
        lastLoggedMetric = meaningfulLast?.loggedMetric,
        lastDateFormatted = meaningfulLast?.dateFormatted,
        defaultEmptyTileCount = 4,
        rawFormat = STANDALONE_EXERCISE_SESSION_FORMAT
    )
    val filteredRepMaxes = if (editingSessionId != null) {
        repMaxes.filter { it.sessionId != editingSessionId }
    } else {
        repMaxes
    }
    val resolvedPrSlots = DailyRoutineCodec.resolveDefaultTrackedPrSlotsForExercise(
        exercise = exercise,
        defaultTrackedPrs = defaultTrackedPrs,
        repMaxes = filteredRepMaxes
    )
    val backingBlock = AvailableBlock(
        id = 0L,
        title = exercise.name,
        instructions = note.trim(),
        exercises = listOf(
            AvailableBlockExercise(
                id = exercise.id,
                name = exercise.name,
                tracksRepMax = exercise.tracksRepMax || resolvedPrSlots.isNotEmpty()
            )
        ),
        trackedPrs = resolvedPrSlots
    )
    return LoadedAvailableBlockState(
        block = backingBlock,
        exerciseStates = listOf(exState),
        initialTrackedPrs = resolvedPrSlots,
        isEditMode = isEditMode,
        editingSessionId = editingSessionId,
        rawFormat = STANDALONE_EXERCISE_SESSION_FORMAT,
        itemKind = SessionItemKind.EXERCISE,
        sourceRoutineItem = sourceRoutineItem
    )
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun SimplifiedBlockLogCard(
    blockState: LoadedAvailableBlockState,
    exercises: List<Exercise>,
    repMaxes: List<RepMax>,
    weightUnit: String,
    onRemoveBlock: () -> Unit,
    onLaunchTimer: () -> Unit,
    onOpenPrPicker: () -> Unit,
    onRemoveTrackedPr: (TrackedPrSlot) -> Unit,
    itemNumber: Int? = null,
    onShowUndoSnackbar: (message: String, onUndo: () -> Unit) -> Unit = { _, _ -> },
    modifier: Modifier = Modifier
) {
    if (blockState.isCompactComplex) {
        CompactComplexLogCard(
            blockState = blockState,
            exercises = exercises,
            repMaxes = repMaxes,
            weightUnit = weightUnit,
            onRemoveBlock = onRemoveBlock,
            onLaunchTimer = onLaunchTimer,
            onOpenPrPicker = onOpenPrPicker,
            onRemoveTrackedPr = onRemoveTrackedPr,
            itemNumber = itemNumber,
            onShowUndoSnackbar = onShowUndoSnackbar,
            modifier = modifier
        )
        return
    }

    val evaluatedChips = blockState.evaluatePrChips(exercises, repMaxes)
    val isDark = isAppDarkTheme()
    val blockAccent = MaterialTheme.colorScheme.primary
    val headerText = buildString {
        if (itemNumber != null) append("$itemNumber  ")
        append("COMPLEX: ${blockState.block.title.ifBlank { "Untitled Complex" }}")
    }

    ElevatedCard(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            SessionItemHeaderRow(
                label = headerText,
                titleColor = blockAccent,
                timerContentDesc = "Start timer for ${blockState.block.title}",
                removeContentDesc = "Remove complex ${blockState.block.title}",
                onLaunchTimer = onLaunchTimer,
                onRemove = onRemoveBlock
            )

            // Prescription Banner
            if (blockState.block.instructions.isNotBlank()) {
                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.55f),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(
                        text = blockState.block.instructions,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp)
                    )
                }
            }

            // Per-movement Set Tiles
            blockState.exerciseSections.forEach { exState ->
                SessionExerciseSetTilesSection(
                    exState = exState,
                    blockState = blockState,
                    weightUnit = weightUnit,
                    showExerciseHeading = true
                )
            }

            TrackedPrChipsFlowSection(
                blockState = blockState,
                evaluatedChips = evaluatedChips,
                weightUnit = weightUnit,
                isDark = isDark,
                onOpenPrPicker = onOpenPrPicker,
                onRemoveTrackedPr = onRemoveTrackedPr
            )
        }
    }
}

@Composable
internal fun SimplifiedExerciseLogCard(
    blockState: LoadedAvailableBlockState,
    exercises: List<Exercise>,
    repMaxes: List<RepMax>,
    weightUnit: String,
    onRemoveBlock: () -> Unit,
    onLaunchTimer: () -> Unit,
    onOpenPrPicker: () -> Unit,
    onRemoveTrackedPr: (TrackedPrSlot) -> Unit,
    itemNumber: Int? = null,
    modifier: Modifier = Modifier
) {
    val evaluatedChips = blockState.evaluatePrChips(exercises, repMaxes)
    val isDark = isAppDarkTheme()
    val emeraldAccent = MaterialTheme.colorScheme.tertiary
    val exSection = blockState.exerciseSections.firstOrNull()
    val exerciseTitle = exSection?.exerciseName?.ifBlank { blockState.block.title }
        ?: blockState.block.title.ifBlank { "Untitled Exercise" }
    val headerText = buildString {
        if (itemNumber != null) append("$itemNumber  ")
        append("EXERCISE: $exerciseTitle")
    }

    ElevatedCard(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            SessionItemHeaderRow(
                label = headerText,
                titleColor = emeraldAccent,
                timerContentDesc = "Start timer for $exerciseTitle",
                removeContentDesc = "Remove exercise $exerciseTitle",
                onLaunchTimer = onLaunchTimer,
                onRemove = onRemoveBlock
            )

            // Optional Routine Note banner
            if (blockState.block.instructions.isNotBlank()) {
                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = emeraldAccent.copy(alpha = if (isDark) 0.14f else 0.08f),
                    border = BorderStroke(1.dp, emeraldAccent.copy(alpha = 0.25f)),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(
                        text = "Routine Note: ${blockState.block.instructions}",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp)
                    )
                }
            }

            blockState.exerciseSections.forEach { section ->
                SessionExerciseSetTilesSection(
                    exState = section,
                    blockState = blockState,
                    weightUnit = weightUnit,
                    showExerciseHeading = false
                )
            }

            TrackedPrChipsFlowSection(
                blockState = blockState,
                evaluatedChips = evaluatedChips,
                weightUnit = weightUnit,
                isDark = isDark,
                onOpenPrPicker = onOpenPrPicker,
                onRemoveTrackedPr = onRemoveTrackedPr
            )
        }
    }
}

@Composable
internal fun SessionItemHeaderRow(
    label: String,
    titleColor: Color,
    timerContentDesc: String,
    removeContentDesc: String,
    onLaunchTimer: () -> Unit,
    onRemove: () -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.Bold,
            color = titleColor,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f)
        )
        Row(verticalAlignment = Alignment.CenterVertically) {
            IconButton(
                onClick = onLaunchTimer,
                modifier = Modifier.minimumInteractiveComponentSize()
            ) {
                Icon(
                    Icons.Filled.Timer,
                    contentDescription = timerContentDesc,
                    tint = titleColor,
                    modifier = Modifier.size(20.dp)
                )
            }
            IconButton(
                onClick = onRemove,
                modifier = Modifier.minimumInteractiveComponentSize()
            ) {
                Icon(
                    Icons.Filled.Delete,
                    contentDescription = removeContentDesc,
                    tint = MaterialTheme.colorScheme.error,
                    modifier = Modifier.size(20.dp)
                )
            }
        }
    }
}

internal fun resolveRepsWeightSetColumns(widthDp: Int, fontScale: Float): Int = when {
    fontScale >= 1.5f -> 1
    widthDp >= 600 -> 4
    else -> 2
}

internal fun resolveMetricOnlySetColumns(widthDp: Int, fontScale: Float): Int = when {
    fontScale >= 1.3f -> 2
    widthDp >= 600 -> 4
    else -> 3
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun SessionExerciseSetTilesSection(
    exState: LoadedAvailableBlockExerciseState,
    blockState: LoadedAvailableBlockState,
    weightUnit: String,
    showExerciseHeading: Boolean
) {
    val fontScale = LocalConfiguration.current.fontScale
    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        if (showExerciseHeading) {
            Text(
                text = exState.exerciseName,
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.SemiBold
            )
        }
        if (!exState.targetBanner.isNullOrBlank()) {
            Text(
                text = exState.targetBanner,
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.primary
            )
        }
        if (!exState.lastSessionDateLabel.isNullOrBlank()) {
            Text(
                text = exState.lastSessionDateLabel,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        LazyRow(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            items(ExerciseTargetMetric.entries) { metric ->
                val chipLabel = if (metric == ExerciseTargetMetric.CALORIES) "Cal" else metric.label
                FilterChip(
                    selected = exState.activeMetric == metric,
                    onClick = { blockState.switchExerciseMetric(exState, metric) },
                    label = {
                        Text(
                            text = chipLabel,
                            maxLines = 1,
                            softWrap = false
                        )
                    },
                    modifier = Modifier.heightIn(min = 48.dp)
                )
            }
        }
        BoxWithConstraints(modifier = Modifier.fillMaxWidth()) {
            val spacing = 8.dp
            val columns = if (exState.activeMetric == ExerciseTargetMetric.REPS) {
                resolveRepsWeightSetColumns(maxWidth.value.toInt(), fontScale)
            } else {
                resolveMetricOnlySetColumns(maxWidth.value.toInt(), fontScale)
            }
            val tileWidth = (maxWidth - spacing * (columns - 1)) / columns
            FlowRow(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(spacing),
                verticalArrangement = Arrangement.spacedBy(spacing),
                maxItemsInEachRow = columns
            ) {
                val setCount = exState.weights.size
                for (idx in 0 until setCount) {
                    val groupIdx = exState.groupIndices.getOrNull(idx)?.takeIf { it > 0 }
                    val setLabel = if (groupIdx != null) "Rd $groupIdx" else "Set ${idx + 1}"
                    val canRemove = setCount > 1
                    if (exState.activeMetric == ExerciseTargetMetric.REPS) {
                        RepsWeightSetTile(
                            setLabel = setLabel,
                            repsValue = exState.repsInputs.getOrNull(idx).orEmpty(),
                            weightValue = exState.weights.getOrNull(idx).orEmpty(),
                            weightUnit = weightUnit,
                            canRemove = canRemove,
                            onRepsChange = { updated -> blockState.updateSetReps(exState, idx, updated) },
                            onWeightChange = { updated -> blockState.updateSetWeight(exState, idx, updated) },
                            onRemove = { blockState.removeSet(exState, idx) },
                            modifier = Modifier.width(tileWidth)
                        )
                    } else {
                        SingleMetricSetTile(
                            setLabel = setLabel,
                            value = exState.metricInputs.getOrNull(idx).orEmpty(),
                            unit = exState.activeMetric.compactUnit,
                            canRemove = canRemove,
                            onValueChange = { updated -> blockState.updateSetMetricValue(exState, idx, updated) },
                            onRemove = { blockState.removeSet(exState, idx) },
                            modifier = Modifier.width(tileWidth)
                        )
                    }
                }
            }
        }
        TextButton(
            onClick = { blockState.addSet(exState) },
            contentPadding = PaddingValues(horizontal = 10.dp, vertical = 6.dp),
            modifier = Modifier
                .heightIn(min = 48.dp)
                .minimumInteractiveComponentSize()
        ) {
            Icon(Icons.Filled.Add, contentDescription = null, modifier = Modifier.size(16.dp))
            Spacer(Modifier.width(4.dp))
            Text("Add Set", style = MaterialTheme.typography.labelLarge)
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun TrackedPrChipsFlowSection(
    blockState: LoadedAvailableBlockState,
    evaluatedChips: List<Pair<TrackedPrSlot, EvaluatedPrChip>>,
    weightUnit: String,
    isDark: Boolean,
    onOpenPrPicker: () -> Unit,
    onRemoveTrackedPr: (TrackedPrSlot) -> Unit
) {
    HorizontalDivider()
    FlowRow(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        evaluatedChips.forEach { (slot, chip) ->
            val slotKey = prSlotKey(chip.movementId, slot.movementName, chip.rmReps)
            val isPersistedOrExplicitPr = blockState.isTrackedSessionPrKey(slotKey) && chip.candidateWeight > 0.0
            val labelText = when {
                chip.isNewPr ->
                    "🏆 NEW PR: ${chip.rmReps}RM ${chip.movementName} · ${chip.candidateWeight.trimmed()} $weightUnit"
                isPersistedOrExplicitPr ->
                    "🔥 ${chip.rmReps}RM ${chip.movementName} · ${chip.candidateWeight.trimmed()} $weightUnit"
                chip.storedBestWeight > 0.0 ->
                    "${chip.rmReps}RM ${chip.movementName} · Current Best: ${chip.storedBestWeight.trimmed()} $weightUnit"
                else ->
                    "${chip.rmReps}RM ${chip.movementName} · Best: —"
            }
            val semanticLabel = when {
                chip.isNewPr ->
                    "New personal record, ${chip.rmReps} rep max ${chip.movementName}, ${chip.candidateWeight.trimmed()} $weightUnit"
                isPersistedOrExplicitPr ->
                    "Session personal record, ${chip.rmReps} rep max ${chip.movementName}, ${chip.candidateWeight.trimmed()} $weightUnit"
                chip.storedBestWeight > 0.0 ->
                    "${chip.rmReps} rep max ${chip.movementName}, current best ${chip.storedBestWeight.trimmed()} $weightUnit"
                else ->
                    "${chip.rmReps} rep max ${chip.movementName}, no recorded best yet"
            }
            val isHighlightedPr = chip.isNewPr || isPersistedOrExplicitPr
            val containerColor = when {
                isHighlightedPr -> MaterialTheme.colorScheme.tertiaryContainer
                else -> MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.7f)
            }
            val contentColor = when {
                isHighlightedPr -> MaterialTheme.colorScheme.onTertiaryContainer
                else -> MaterialTheme.colorScheme.onSurfaceVariant
            }
            val borderColor = when {
                chip.isNewPr -> MaterialTheme.colorScheme.tertiary
                isPersistedOrExplicitPr -> MaterialTheme.colorScheme.tertiary.copy(alpha = 0.65f)
                else -> MaterialTheme.colorScheme.outlineVariant
            }
            Surface(
                shape = RoundedCornerShape(18.dp),
                color = containerColor,
                contentColor = contentColor,
                border = BorderStroke(1.dp, borderColor),
                modifier = Modifier
                    .heightIn(min = 48.dp)
                    .semantics(mergeDescendants = true) {
                        contentDescription = "$semanticLabel. Tap to edit tracked PR."
                    }
                    .clickable(onClick = onOpenPrPicker)
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.padding(start = 12.dp, end = 4.dp, top = 4.dp, bottom = 4.dp)
                ) {
                    Text(
                        text = labelText,
                        style = MaterialTheme.typography.labelMedium.copy(fontFeatureSettings = "tnum"),
                        fontWeight = if (isHighlightedPr) FontWeight.Bold else FontWeight.Medium,
                        modifier = Modifier.weight(1f, fill = false)
                    )
                    IconButton(
                        onClick = { onRemoveTrackedPr(slot) },
                        modifier = Modifier.sizeIn(minWidth = 48.dp, minHeight = 48.dp)
                    ) {
                        Icon(
                            Icons.Filled.Close,
                            contentDescription = "Remove tracked PR ${chip.rmReps}RM ${chip.movementName}",
                            modifier = Modifier.size(16.dp),
                            tint = contentColor
                        )
                    }
                }
            }
        }

        OutlinedButton(
            onClick = onOpenPrPicker,
            shape = RoundedCornerShape(18.dp),
            contentPadding = PaddingValues(horizontal = 14.dp, vertical = 8.dp),
            modifier = Modifier.heightIn(min = 48.dp)
        ) {
            Text("+ Record PR 🔥", style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.SemiBold)
        }
    }
}

@Composable
private fun CompactTileInputRow(
    value: String,
    unit: String,
    keyboardType: KeyboardType,
    onValueChange: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.Center,
        modifier = modifier.fillMaxWidth()
    ) {
        BasicTextField(
            value = value,
            onValueChange = onValueChange,
            singleLine = true,
            keyboardOptions = KeyboardOptions(
                keyboardType = keyboardType,
                imeAction = ImeAction.Next
            ),
            textStyle = MaterialTheme.typography.titleMedium.copy(
                color = MaterialTheme.colorScheme.onSurface,
                fontWeight = FontWeight.Bold,
                textAlign = TextAlign.Center,
                fontFeatureSettings = "tnum"
            ),
            cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
            modifier = Modifier.weight(1f, fill = false),
            decorationBox = { innerTextField ->
                Box(contentAlignment = Alignment.Center) {
                    if (value.isBlank()) {
                        Text(
                            text = "—",
                            style = MaterialTheme.typography.titleMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f)
                        )
                    }
                    innerTextField()
                }
            }
        )
        Spacer(Modifier.width(3.dp))
        Text(
            text = unit,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1,
            softWrap = false
        )
    }
}

@Composable
internal fun RepsWeightSetTile(
    setLabel: String,
    repsValue: String,
    weightValue: String,
    weightUnit: String,
    canRemove: Boolean,
    onRepsChange: (String) -> Unit,
    onWeightChange: (String) -> Unit,
    onRemove: () -> Unit,
    modifier: Modifier = Modifier
) {
    val hasReps = (repsValue.toIntOrNull() ?: 0) > 0
    val hasWeight = AvailableBlockCodec.normalizedPositiveWeight(weightValue) != null
    val isPopulated = hasReps || hasWeight
    val tileSemantic = buildString {
        append(setLabel)
        append(if (hasReps) ", $repsValue reps" else ", empty reps")
        append(if (hasWeight) ", $weightValue $weightUnit" else ", no weight")
    }
    Surface(
        modifier = modifier
            .heightIn(min = 98.dp)
            .semantics(mergeDescendants = true) {
                contentDescription = tileSemantic
            },
        shape = RoundedCornerShape(12.dp),
        color = if (isPopulated) {
            MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.35f)
        } else {
            MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f)
        },
        border = BorderStroke(
            width = 1.dp,
            color = if (isPopulated) MaterialTheme.colorScheme.primary.copy(alpha = 0.5f) else MaterialTheme.colorScheme.outlineVariant
        )
    ) {
        Box(modifier = Modifier.padding(horizontal = 8.dp, vertical = 6.dp)) {
            if (canRemove) {
                IconButton(
                    onClick = onRemove,
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .size(28.dp)
                        .minimumInteractiveComponentSize()
                ) {
                    Icon(
                        Icons.Filled.Close,
                        contentDescription = "Remove $setLabel",
                        tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.75f),
                        modifier = Modifier.size(15.dp)
                    )
                }
            }
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 8.dp, bottom = 2.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                Text(
                    text = setLabel,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1
                )
                CompactTileInputRow(
                    value = repsValue,
                    unit = "reps",
                    keyboardType = KeyboardType.Number,
                    onValueChange = { raw ->
                        onRepsChange(raw.filter { it.isDigit() }.take(4))
                    }
                )
                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.45f))
                CompactTileInputRow(
                    value = weightValue,
                    unit = weightUnit,
                    keyboardType = KeyboardType.Decimal,
                    onValueChange = { raw ->
                        onWeightChange(raw.filter { it.isDigit() || it == '.' || it == ',' }.take(8))
                    }
                )
            }
        }
    }
}

@Composable
internal fun SingleMetricSetTile(
    setLabel: String,
    value: String,
    unit: String,
    canRemove: Boolean,
    onValueChange: (String) -> Unit,
    onRemove: () -> Unit,
    modifier: Modifier = Modifier
) {
    val hasValue = AvailableBlockCodec.normalizedPositiveWeight(value) != null
    val tileSemantic = if (hasValue) {
        "$setLabel, $value $unit"
    } else {
        "$setLabel, empty value"
    }
    Surface(
        modifier = modifier
            .heightIn(min = 76.dp)
            .semantics(mergeDescendants = true) {
                contentDescription = tileSemantic
            },
        shape = RoundedCornerShape(12.dp),
        color = if (hasValue) {
            MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.35f)
        } else {
            MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f)
        },
        border = BorderStroke(
            width = 1.dp,
            color = if (hasValue) MaterialTheme.colorScheme.primary.copy(alpha = 0.5f) else MaterialTheme.colorScheme.outlineVariant
        )
    ) {
        Box(modifier = Modifier.padding(horizontal = 6.dp, vertical = 4.dp)) {
            if (canRemove) {
                IconButton(
                    onClick = onRemove,
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .size(28.dp)
                        .minimumInteractiveComponentSize()
                ) {
                    Icon(
                        Icons.Filled.Close,
                        contentDescription = "Remove $setLabel",
                        tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.75f),
                        modifier = Modifier.size(15.dp)
                    )
                }
            }
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .align(Alignment.Center)
                    .padding(top = 12.dp, bottom = 4.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                Text(
                    text = setLabel,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1
                )
                CompactTileInputRow(
                    value = value,
                    unit = unit,
                    keyboardType = KeyboardType.Decimal,
                    onValueChange = { raw ->
                        onValueChange(raw.filter { it.isDigit() || it == '.' || it == ',' }.take(8))
                    }
                )
            }
        }
    }
}

@Composable
internal fun WeightOnlySetTile(
    setNumber: Int,
    value: String,
    unit: String,
    canRemove: Boolean,
    onValueChange: (String) -> Unit,
    onRemove: () -> Unit,
    modifier: Modifier = Modifier
) {
    SingleMetricSetTile(
        setLabel = "Set $setNumber",
        value = value,
        unit = unit,
        canRemove = canRemove,
        onValueChange = onValueChange,
        onRemove = onRemove,
        modifier = modifier
    )
}

internal enum class AddBlockSheetTab {
    SAVED_BLOCKS,
    CUSTOM_BLOCK
}

internal fun resolveInitialAddBlockTab(
    preferSaved: Boolean,
    availableBlocks: List<AvailableBlock>,
    alreadyLoadedIds: Set<Long>
): AddBlockSheetTab {
    val hasTargetSaved = if (preferSaved) {
        availableBlocks.isNotEmpty()
    } else {
        availableBlocks.any { it.id !in alreadyLoadedIds }
    }
    return if (hasTargetSaved) AddBlockSheetTab.SAVED_BLOCKS else AddBlockSheetTab.CUSTOM_BLOCK
}

internal fun resolveCustomBlockTitle(
    rawTitle: String,
    selectedExercises: List<Exercise>
): String {
    val trimmed = rawTitle.trim()
    if (trimmed.isNotEmpty()) return trimmed
    if (selectedExercises.isEmpty()) return ""
    if (selectedExercises.size <= 2) return selectedExercises.joinToString(" + ") { it.name }
    return "${selectedExercises.take(2).joinToString(" + ") { it.name }} +${selectedExercises.size - 2} more"
}

internal fun canSubmitCustomBlock(
    rawTitle: String,
    selectedExerciseIds: Collection<Long>
): Boolean = rawTitle.trim().isNotEmpty() || selectedExerciseIds.isNotEmpty()

internal fun formatSavedBlocksConfirmLabel(selectedCount: Int): String = when {
    selectedCount <= 0 -> "Select Complex(es)"
    selectedCount == 1 -> "Add 1 Complex"
    else -> "Add $selectedCount Complexes"
}

/**
 * Unified modal sheet for adding complexes to the active session from either the saved
 * Complexes library or a custom exercise complex definition.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun AddBlockToSessionSheet(
    availableBlocks: List<AvailableBlock>,
    alreadyLoadedIds: Set<Long>,
    exercises: List<Exercise>,
    initialTab: AddBlockSheetTab,
    onDismiss: () -> Unit,
    onConfirmSavedBlocks: (List<AvailableBlock>) -> Unit,
    onCreateExercise: (name: String, onCreated: (Exercise) -> Unit) -> Unit,
    onConfirmCustomBlock: (
        rawTitle: String,
        instructions: String,
        selectedExerciseIds: List<Long>,
        saveToLibrary: Boolean
    ) -> Unit
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    // Resolve the initial tab once per sheet opening: `initialTab` is recomputed from live
    // `availableBlocks` / `alreadyLoadedIds`, so keying on it would reset the active tab (and discard
    // an in-progress Custom Complex form) whenever a background sync or library save emits mid-sheet.
    var selectedTab by remember { mutableStateOf(initialTab) }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .imePadding()
                .padding(horizontal = 16.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Text(
                text = "Add Complex to Session",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold
            )

            TabRow(selectedTabIndex = selectedTab.ordinal) {
                Tab(
                    selected = selectedTab == AddBlockSheetTab.SAVED_BLOCKS,
                    onClick = { selectedTab = AddBlockSheetTab.SAVED_BLOCKS },
                    modifier = Modifier.heightIn(min = 48.dp),
                    text = {
                        Text(
                            text = "Saved Complexes (${availableBlocks.size})",
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                )
                Tab(
                    selected = selectedTab == AddBlockSheetTab.CUSTOM_BLOCK,
                    onClick = { selectedTab = AddBlockSheetTab.CUSTOM_BLOCK },
                    modifier = Modifier.heightIn(min = 48.dp),
                    text = {
                        Text(
                            text = "Custom Complex",
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                )
            }

            when (selectedTab) {
                AddBlockSheetTab.SAVED_BLOCKS -> SavedBlocksTabBody(
                    availableBlocks = availableBlocks,
                    alreadyLoadedIds = alreadyLoadedIds,
                    onSwitchToCustom = { selectedTab = AddBlockSheetTab.CUSTOM_BLOCK },
                    onDismiss = onDismiss,
                    onConfirmSavedBlocks = onConfirmSavedBlocks
                )

                AddBlockSheetTab.CUSTOM_BLOCK -> CustomBlockTabBody(
                    exercises = exercises,
                    onDismiss = onDismiss,
                    onCreateExercise = onCreateExercise,
                    onConfirmCustomBlock = onConfirmCustomBlock
                )
            }
        }
    }
}

@Composable
private fun SavedBlocksTabBody(
    availableBlocks: List<AvailableBlock>,
    alreadyLoadedIds: Set<Long>,
    onSwitchToCustom: () -> Unit,
    onDismiss: () -> Unit,
    onConfirmSavedBlocks: (List<AvailableBlock>) -> Unit
) {
    var query by remember { mutableStateOf("") }
    val selectedNewIds = remember { mutableStateListOf<Long>() }
    val filtered = remember(availableBlocks, query) {
        AvailableBlockCodec.filterAvailableBlocks(availableBlocks, query)
    }

    OutlinedTextField(
        value = query,
        onValueChange = { query = it },
        placeholder = { Text("Search complexes...") },
        leadingIcon = { Icon(Icons.Filled.Search, contentDescription = null) },
        trailingIcon = if (query.isNotEmpty()) {
            {
                IconButton(onClick = { query = "" }) {
                    Icon(Icons.Filled.Close, contentDescription = "Clear search")
                }
            }
        } else {
            null
        },
        singleLine = true,
        shape = RoundedCornerShape(10.dp),
        modifier = Modifier.fillMaxWidth()
    )

    if (availableBlocks.isEmpty()) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Text(
                text = "No saved complexes yet. Build a custom complex right here or save one for future sessions.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            OutlinedButton(
                onClick = onSwitchToCustom,
                modifier = Modifier.heightIn(min = 48.dp),
                shape = RoundedCornerShape(10.dp)
            ) {
                Text("Switch to Custom Complex")
            }
        }
    } else if (filtered.isEmpty()) {
        Text(
            text = "No complexes matching \"$query\".",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(vertical = 12.dp)
        )
    } else {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(max = 360.dp)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            filtered.forEach { block ->
                val isAlreadyLoaded = block.id in alreadyLoadedIds
                val isSelectedNew = block.id in selectedNewIds
                val toggleSelection = {
                    if (!isAlreadyLoaded) {
                        if (isSelectedNew) selectedNewIds.remove(block.id) else selectedNewIds.add(block.id)
                    }
                }
                val cardModifier = Modifier
                    .fillMaxWidth()
                    .let { if (isAlreadyLoaded) it.alpha(0.65f) else it }
                val cardContent: @Composable () -> Unit = {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 12.dp, vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Checkbox(
                            checked = isAlreadyLoaded || isSelectedNew,
                            enabled = !isAlreadyLoaded,
                            onCheckedChange = if (isAlreadyLoaded) {
                                null
                            } else {
                                { checked ->
                                    if (checked) {
                                        if (block.id !in selectedNewIds) selectedNewIds.add(block.id)
                                    } else {
                                        selectedNewIds.remove(block.id)
                                    }
                                }
                            }
                        )
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = block.title,
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.SemiBold
                            )
                            val summary = buildString {
                                if (block.exercises.isNotEmpty()) {
                                    append(block.exercises.joinToString(", ") { it.name })
                                } else if (block.instructions.isNotBlank()) {
                                    append(block.instructions.lines().first())
                                }
                                if (block.trackedPrs.isNotEmpty()) {
                                    if (isNotEmpty()) append(" · ")
                                    append("🔥 ${block.trackedPrs.size} tracked PR(s)")
                                }
                            }
                            if (summary.isNotBlank()) {
                                Text(
                                    text = summary,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    maxLines = 2,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                            if (isAlreadyLoaded) {
                                Text(
                                    text = "Already in session",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.primary,
                                    fontWeight = FontWeight.Medium
                                )
                            }
                        }
                    }
                }
                if (isAlreadyLoaded) {
                    OutlinedCard(
                        shape = RoundedCornerShape(10.dp),
                        modifier = cardModifier
                    ) {
                        cardContent()
                    }
                } else {
                    OutlinedCard(
                        onClick = { toggleSelection() },
                        shape = RoundedCornerShape(10.dp),
                        modifier = cardModifier
                    ) {
                        cardContent()
                    }
                }
            }
        }
    }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(bottom = 16.dp),
        horizontalArrangement = Arrangement.End,
        verticalAlignment = Alignment.CenterVertically
    ) {
        TextButton(
            onClick = onDismiss,
            modifier = Modifier.heightIn(min = 48.dp)
        ) {
            Text("Cancel")
        }
        Spacer(Modifier.width(8.dp))
        val chosenList = availableBlocks.filter { it.id in selectedNewIds && it.id !in alreadyLoadedIds }
        Button(
            onClick = { onConfirmSavedBlocks(chosenList) },
            enabled = chosenList.isNotEmpty(),
            modifier = Modifier.heightIn(min = 48.dp)
        ) {
            Text(formatSavedBlocksConfirmLabel(chosenList.size))
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun CustomBlockTabBody(
    exercises: List<Exercise>,
    onDismiss: () -> Unit,
    onCreateExercise: (name: String, onCreated: (Exercise) -> Unit) -> Unit,
    onConfirmCustomBlock: (
        rawTitle: String,
        instructions: String,
        selectedExerciseIds: List<Long>,
        saveToLibrary: Boolean
    ) -> Unit
) {
    var rawTitle by remember { mutableStateOf("") }
    var instructions by remember { mutableStateOf("") }
    var exerciseQuery by remember { mutableStateOf("") }
    var newMovementName by remember { mutableStateOf("") }
    var isCreatingMovement by remember { mutableStateOf(false) }
    val selectedExerciseIds = remember { mutableStateListOf<Long>() }
    val locallyCreatedExercises = remember { mutableStateListOf<Exercise>() }
    var saveToLibrary by remember { mutableStateOf(false) }
    var isSubmitting by remember { mutableStateOf(false) }

    val allExercises = remember(exercises, locallyCreatedExercises.size) {
        (exercises + locallyCreatedExercises).distinctBy { it.id }
    }
    val selectedExercises = remember(allExercises, selectedExerciseIds.toList()) {
        selectedExerciseIds.mapNotNull { id -> allExercises.firstOrNull { it.id == id } }
    }
    val filteredExercises = remember(allExercises, exerciseQuery) {
        val needle = exerciseQuery.trim()
        if (needle.isBlank()) {
            allExercises
        } else {
            allExercises.filter { it.name.contains(needle, ignoreCase = true) }
        }
    }
    val placeholderTitle = resolveCustomBlockTitle("", selectedExercises)
        .ifBlank { "e.g., Front Squat + Lunges" }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(max = 420.dp)
            .verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        OutlinedTextField(
            value = rawTitle,
            onValueChange = { rawTitle = sanitizeComplexNameInput(it) },
            label = { Text("Complex Name (optional)") },
            placeholder = { Text(placeholderTitle) },
            singleLine = false,
            minLines = 1,
            maxLines = 2,
            shape = RoundedCornerShape(10.dp),
            modifier = Modifier.fillMaxWidth()
        )

        OutlinedTextField(
            value = instructions,
            onValueChange = { instructions = it },
            label = { Text("Instructions (optional)") },
            placeholder = { Text("e.g., 3x8 @ RPE 8, rest 2 min") },
            maxLines = 3,
            shape = RoundedCornerShape(10.dp),
            modifier = Modifier.fillMaxWidth()
        )

        HorizontalDivider()

        Text(
            text = "Exercises (${selectedExerciseIds.size} selected)",
            style = MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.SemiBold
        )

        OutlinedTextField(
            value = exerciseQuery,
            onValueChange = { exerciseQuery = it },
            placeholder = { Text("Search movements...") },
            leadingIcon = { Icon(Icons.Filled.Search, contentDescription = null) },
            trailingIcon = if (exerciseQuery.isNotEmpty()) {
                {
                    IconButton(onClick = { exerciseQuery = "" }) {
                        Icon(Icons.Filled.Close, contentDescription = "Clear movement filter")
                    }
                }
            } else {
                null
            },
            singleLine = true,
            shape = RoundedCornerShape(10.dp),
            modifier = Modifier.fillMaxWidth()
        )

        if (filteredExercises.isEmpty()) {
            Text(
                text = if (allExercises.isEmpty()) {
                    "No movements yet — add one below or enter a Complex Name above."
                } else {
                    "No movements matching \"$exerciseQuery\"."
                },
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        } else {
            FlowRow(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                filteredExercises.forEach { ex ->
                    val isSelected = ex.id in selectedExerciseIds
                    FilterChip(
                        selected = isSelected,
                        onClick = {
                            if (isSelected) selectedExerciseIds.remove(ex.id) else selectedExerciseIds.add(ex.id)
                        },
                        label = { Text(ex.name) }
                    )
                }
            }
        }

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            OutlinedTextField(
                value = newMovementName,
                onValueChange = { newMovementName = it },
                placeholder = { Text("New movement name") },
                singleLine = true,
                shape = RoundedCornerShape(10.dp),
                modifier = Modifier.weight(1f)
            )
            OutlinedButton(
                onClick = {
                    val trimmed = newMovementName.trim()
                    if (trimmed.isNotEmpty() && !isCreatingMovement) {
                        isCreatingMovement = true
                        onCreateExercise(trimmed) { created ->
                            if (locallyCreatedExercises.none { it.id == created.id }) {
                                locallyCreatedExercises.add(created)
                            }
                            if (created.id !in selectedExerciseIds) {
                                selectedExerciseIds.add(created.id)
                            }
                            newMovementName = ""
                            exerciseQuery = ""
                            isCreatingMovement = false
                        }
                    }
                },
                enabled = newMovementName.trim().isNotEmpty() && !isCreatingMovement,
                modifier = Modifier.heightIn(min = 48.dp),
                shape = RoundedCornerShape(10.dp)
            ) {
                Icon(Icons.Filled.Add, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(4.dp))
                Text("Add")
            }
        }

        HorizontalDivider()

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = 48.dp)
                .toggleable(
                    value = saveToLibrary,
                    role = Role.Switch,
                    onValueChange = { saveToLibrary = it }
                )
                .semantics(mergeDescendants = true) {}
                .padding(vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Column(
                modifier = Modifier
                    .weight(1f)
                    .padding(end = 12.dp)
            ) {
                Text(
                    text = "Save to Complexes library",
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.SemiBold
                )
                Text(
                    text = "Reuse this complex in future workouts",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Switch(
                checked = saveToLibrary,
                onCheckedChange = null
            )
        }
    }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(bottom = 16.dp),
        horizontalArrangement = Arrangement.End,
        verticalAlignment = Alignment.CenterVertically
    ) {
        TextButton(
            onClick = onDismiss,
            modifier = Modifier.heightIn(min = 48.dp)
        ) {
            Text("Cancel")
        }
        Spacer(Modifier.width(8.dp))
        Button(
            onClick = {
                if (!isSubmitting && canSubmitCustomBlock(rawTitle, selectedExerciseIds)) {
                    isSubmitting = true
                    onConfirmCustomBlock(
                        rawTitle,
                        instructions,
                        selectedExerciseIds.toList(),
                        saveToLibrary
                    )
                }
            },
            enabled = !isSubmitting && canSubmitCustomBlock(rawTitle, selectedExerciseIds),
            modifier = Modifier.heightIn(min = 48.dp)
        ) {
            Text("Add Complex")
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
internal fun MultiMovementPrPickerSheet(
    blockState: LoadedAvailableBlockState,
    exercises: List<Exercise>,
    repMaxes: List<RepMax>,
    weightUnit: String,
    onDismiss: () -> Unit,
    onApply: (List<TrackedPrSlot>, Map<String, String>) -> Unit
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    var query by remember { mutableStateOf("") }
    val excludeSessionId = if (blockState.isEditMode) blockState.editingSessionId else null

    val blockExerciseIds = remember(blockState) {
        blockState.exerciseSections.map { it.exerciseId }.filter { it > 0L }.toSet()
    }
    val initialTrackedIds = remember(blockState) {
        blockState.trackedPrSlots.map { it.movementId }.filter { it > 0L }.toSet()
    }

    val trackableExercises = remember(exercises, blockExerciseIds, initialTrackedIds) {
        exercises.filter { it.tracksRepMax || it.id in initialTrackedIds }
            .sortedWith(
                compareByDescending<Exercise> { it.id in initialTrackedIds }
                    .thenByDescending { it.id in blockExerciseIds }
                    .thenBy { it.name.lowercase() }
            )
    }

    val selectedRmsByExerciseId = remember(blockState) {
        mutableStateMapOf<Long, SnapshotStateList<Int>>().apply {
            blockState.trackedPrSlots.forEach { slot ->
                if (slot.movementId > 0L) {
                    val list = getOrPut(slot.movementId) { mutableStateListOf() }
                    if (slot.rmReps !in list) list.add(slot.rmReps)
                }
            }
        }
    }
    val weightInputs = remember(blockState) {
        mutableStateMapOf<String, String>().apply {
            trackableExercises.forEach { ex ->
                STANDARD_RM_REPS.forEach { rm ->
                    val k = prSlotKey(ex.id, ex.name, rm)
                    if (blockState.isCustomWeightOverride(k)) {
                        blockState.prWeightOverrides[k]?.let { put(k, it) }
                    } else {
                        val live = blockState.maxSetWeightFor(ex.id).takeIf { it > 0.0 }?.trimmed().orEmpty()
                        val existingOverride = blockState.prWeightOverrides[k]
                        val chosen = live.ifEmpty { existingOverride.orEmpty() }
                        if (chosen.isNotEmpty()) put(k, chosen)
                    }
                }
            }
        }
    }

    val filtered = remember(trackableExercises, query) {
        val q = query.trim().lowercase()
        if (q.isEmpty()) trackableExercises else trackableExercises.filter { it.name.lowercase().contains(q) }
    }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .imePadding()
                .padding(horizontal = 16.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Text(
                text = "Record / Track PRs",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold
            )
            Text(
                text = "Select movements & rep-maxes to track on \"${blockState.block.title}\".",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            OutlinedTextField(
                value = query,
                onValueChange = { query = it },
                placeholder = { Text("Search trackable movements...") },
                leadingIcon = { Icon(Icons.Filled.Search, contentDescription = null) },
                singleLine = true,
                shape = RoundedCornerShape(10.dp),
                modifier = Modifier.fillMaxWidth()
            )
            if (filtered.isEmpty()) {
                Text(
                    text = "No PR-trackable movements found.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(vertical = 12.dp)
                )
            } else {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(max = 380.dp)
                        .verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    filtered.forEach { ex ->
                        val rmList = selectedRmsByExerciseId[ex.id]
                        val isSelected = !rmList.isNullOrEmpty()
                        OutlinedCard(
                            shape = RoundedCornerShape(10.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(10.dp),
                                verticalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .heightIn(min = 48.dp)
                                        .clickable {
                                            if (isSelected) {
                                                selectedRmsByExerciseId.remove(ex.id)
                                            } else {
                                                selectedRmsByExerciseId[ex.id] = mutableStateListOf(1)
                                            }
                                        }
                                ) {
                                    Checkbox(
                                        checked = isSelected,
                                        onCheckedChange = { checked ->
                                            if (checked) {
                                                selectedRmsByExerciseId[ex.id] = mutableStateListOf(1)
                                            } else {
                                                selectedRmsByExerciseId.remove(ex.id)
                                            }
                                        }
                                    )
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(
                                            text = ex.name,
                                            style = MaterialTheme.typography.titleSmall,
                                            fontWeight = FontWeight.SemiBold
                                        )
                                        if (ex.id in blockExerciseIds) {
                                            Text(
                                                text = "In this complex",
                                                style = MaterialTheme.typography.labelSmall,
                                                color = MaterialTheme.colorScheme.primary
                                            )
                                        }
                                    }
                                }

                                if (!rmList.isNullOrEmpty()) {
                                    FlowRow(
                                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                                        verticalArrangement = Arrangement.spacedBy(6.dp)
                                    ) {
                                        STANDARD_RM_REPS.forEach { rm ->
                                            val rmActive = rm in rmList
                                            FilterChip(
                                                selected = rmActive,
                                                onClick = {
                                                    if (rmActive && rmList.size > 1) {
                                                        rmList.remove(rm)
                                                    } else if (!rmActive) {
                                                        rmList.add(rm)
                                                    }
                                                },
                                                label = { Text("${rm}RM") }
                                            )
                                        }
                                    }
                                    rmList.sorted().forEach { rm ->
                                        val k = prSlotKey(ex.id, ex.name, rm)
                                        val wText = weightInputs[k] ?: ""
                                        val storedBest = AvailableBlockCodec.bestStoredRepMaxWeight(
                                            repMaxes = repMaxes,
                                            movementId = ex.id,
                                            rmReps = rm,
                                            excludeSessionId = excludeSessionId
                                        )
                                        val candidate = AvailableBlockCodec.normalizedPositiveWeight(wText) ?: 0.0
                                        val beatsBest = candidate > 0.0 && candidate > storedBest
                                        Row(
                                            verticalAlignment = Alignment.CenterVertically,
                                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                                            modifier = Modifier.fillMaxWidth()
                                        ) {
                                            Text(
                                                text = "${rm}RM",
                                                style = MaterialTheme.typography.labelLarge,
                                                fontWeight = FontWeight.Bold,
                                                modifier = Modifier.width(44.dp)
                                            )
                                            OutlinedTextField(
                                                value = wText,
                                                onValueChange = { raw ->
                                                    weightInputs[k] = raw.filter { it.isDigit() || it == '.' || it == ',' }.take(8)
                                                },
                                                singleLine = true,
                                                textStyle = MaterialTheme.typography.bodyLarge.copy(fontFeatureSettings = "tnum"),
                                                label = {
                                                    Text(
                                                        if (storedBest > 0.0) {
                                                            "Best ${storedBest.trimmed()} $weightUnit"
                                                        } else {
                                                            "Weight ($weightUnit)"
                                                        }
                                                    )
                                                },
                                                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                                                modifier = Modifier.weight(1f)
                                            )
                                            if (beatsBest) {
                                                Text(
                                                    text = "🔥 PR",
                                                    style = MaterialTheme.typography.labelMedium,
                                                    fontWeight = FontWeight.Bold,
                                                    color = MaterialTheme.colorScheme.tertiary
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

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 16.dp),
                horizontalArrangement = Arrangement.End,
                verticalAlignment = Alignment.CenterVertically
            ) {
                TextButton(onClick = onDismiss) {
                    Text("Cancel")
                }
                Spacer(Modifier.width(8.dp))
                Button(
                    onClick = {
                        val updatedSlots = AvailableBlockCodec.normalizeTrackedPrSlots(
                            selectedRmsByExerciseId.entries.flatMap { (exId, rms) ->
                                val ex = exercises.firstOrNull { it.id == exId }
                                val name = ex?.name ?: return@flatMap emptyList()
                                rms.sorted().map { rm ->
                                    TrackedPrSlot(
                                        movementId = exId,
                                        movementName = name,
                                        rmReps = rm
                                    )
                                }
                            }
                        )
                        val overrides = buildMap {
                            updatedSlots.forEach { slot ->
                                val k = prSlotKey(slot.movementId, slot.movementName, slot.rmReps)
                                val entered = weightInputs[k]
                                if (!entered.isNullOrBlank()) {
                                    put(k, entered)
                                }
                            }
                        }
                        onApply(updatedSlots, overrides)
                    }
                ) {
                    Text("Apply PRs")
                }
            }
        }
    }
}

/**
 * Modal bottom sheet for selecting a saved composite [DailyRoutine] in Log/Edit Session.
 */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
internal fun LoadDailyRoutineSheet(
    dailyRoutines: List<DailyRoutine>,
    sessions: List<SessionWithBlocks>,
    onDismiss: () -> Unit,
    onSelectRoutine: (DailyRoutine) -> Unit
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    var query by remember { mutableStateOf("") }
    val filtered = remember(dailyRoutines, query) {
        DailyRoutineCodec.filterDailyRoutines(dailyRoutines, query)
    }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .imePadding()
                .padding(horizontal = 16.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Text(
                text = "Load Daily Routine",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold
            )

            OutlinedTextField(
                value = query,
                onValueChange = { query = it },
                placeholder = { Text("Search routines...") },
                leadingIcon = { Icon(Icons.Filled.Search, contentDescription = null) },
                trailingIcon = if (query.isNotEmpty()) {
                    {
                        IconButton(onClick = { query = "" }) {
                            Icon(Icons.Filled.Close, contentDescription = "Clear routine search")
                        }
                    }
                } else null,
                singleLine = true,
                shape = RoundedCornerShape(10.dp),
                modifier = Modifier.fillMaxWidth()
            )

            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(max = 380.dp)
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                if (filtered.isEmpty()) {
                    Text(
                        text = if (dailyRoutines.isEmpty()) {
                            "No Daily Routines yet — create one in Library → Routines."
                        } else {
                            "No Daily Routines matching \"$query\"."
                        },
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(vertical = 12.dp)
                    )
                } else {
                    filtered.forEach { routine ->
                        val lastDate = DailyRoutineCodec.resolveLastDailyRoutineSessionDate(routine, sessions)
                        val subtitle = buildString {
                            append("${routine.blockCount} ")
                            append(if (routine.blockCount == 1) "Complex" else "Complexes")
                            append(" · ${routine.exerciseCount} ")
                            append(if (routine.exerciseCount == 1) "Exercise" else "Exercises")
                            append(" · ")
                            append(if (lastDate != null) "Last: $lastDate" else "Not logged yet")
                        }
                        OutlinedCard(
                            onClick = { onSelectRoutine(routine) },
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(12.dp)
                        ) {
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(12.dp),
                                verticalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                Text(
                                    text = routine.title,
                                    style = MaterialTheme.typography.titleSmall,
                                    fontWeight = FontWeight.Bold
                                )
                                Text(
                                    text = subtitle,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                                if (routine.items.isNotEmpty()) {
                                    FlowRow(
                                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                                        verticalArrangement = Arrangement.spacedBy(4.dp)
                                    ) {
                                        routine.items.take(4).forEach { item ->
                                            val label = when (item) {
                                                is com.fractanomics.crosstraining.util.DailyRoutineItem.BlockRef ->
                                                    "COMPLEX: ${item.blockTitle}"
                                                is com.fractanomics.crosstraining.util.DailyRoutineItem.StandaloneExercise ->
                                                    "EX: ${item.exerciseName}"
                                            }
                                            Surface(
                                                shape = RoundedCornerShape(8.dp),
                                                color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.7f)
                                            ) {
                                                Text(
                                                    text = label,
                                                    style = MaterialTheme.typography.labelSmall,
                                                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                                                )
                                            }
                                        }
                                        if (routine.items.size > 4) {
                                            Text(
                                                text = "+${routine.items.size - 4} more",
                                                style = MaterialTheme.typography.labelSmall,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                                modifier = Modifier.align(Alignment.CenterVertically)
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 16.dp),
                horizontalArrangement = Arrangement.End
            ) {
                TextButton(
                    onClick = onDismiss,
                    modifier = Modifier.heightIn(min = 48.dp)
                ) {
                    Text("Close")
                }
            }
        }
    }
}

/**
 * Modal bottom sheet for adding a standalone [Exercise] card (`EXERCISE: ...`) to a session
 * with search, category filter, optional note, and inline weighted exercise quick-create.
 */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
internal fun AddExerciseToSessionSheet(
    exercises: List<Exercise>,
    onDismiss: () -> Unit,
    onCreateExercise: (name: String, onCreated: (Exercise) -> Unit) -> Unit,
    onSelectExercise: (exercise: Exercise, note: String) -> Unit
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    var query by remember { mutableStateOf("") }
    var selectedCategory by remember { mutableStateOf<ExerciseCategory?>(null) }
    var noteText by remember { mutableStateOf("") }
    var isCreating by remember { mutableStateOf(false) }

    val filtered = remember(exercises, query, selectedCategory) {
        val q = query.trim().lowercase()
        exercises
            .asSequence()
            .filter { selectedCategory == null || it.category == selectedCategory }
            .filter { q.isEmpty() || it.name.lowercase().contains(q) }
            .sortedWith(
                compareByDescending<Exercise> { it.metricType == MetricType.WEIGHT }
                    .thenBy { it.name.lowercase() }
            )
            .toList()
    }
    val exactMatch = remember(exercises, query) {
        val clean = query.trim().lowercase()
        clean.isNotEmpty() && exercises.any { it.name.trim().lowercase() == clean }
    }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .imePadding()
                .padding(horizontal = 16.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Text(
                text = "Add Exercise",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold
            )

            OutlinedTextField(
                value = query,
                onValueChange = { query = it },
                placeholder = { Text("Search or type new exercise name...") },
                leadingIcon = { Icon(Icons.Filled.Search, contentDescription = null) },
                trailingIcon = if (query.isNotEmpty()) {
                    {
                        IconButton(onClick = { query = "" }) {
                            Icon(Icons.Filled.Close, contentDescription = "Clear exercise search")
                        }
                    }
                } else null,
                singleLine = true,
                shape = RoundedCornerShape(10.dp),
                modifier = Modifier.fillMaxWidth()
            )

            FlowRow(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                FilterChip(
                    selected = selectedCategory == null,
                    onClick = { selectedCategory = null },
                    label = { Text("All", maxLines = 1, softWrap = false) }
                )
                ExerciseCategory.entries.forEach { cat ->
                    FilterChip(
                        selected = selectedCategory == cat,
                        onClick = { selectedCategory = if (selectedCategory == cat) null else cat },
                        label = { Text(cat.label, maxLines = 1, softWrap = false) }
                    )
                }
            }

            OutlinedTextField(
                value = noteText,
                onValueChange = { noteText = it },
                label = { Text("Note / cue (optional)") },
                placeholder = { Text("e.g. Tempo 3010 · RIR 2") },
                singleLine = true,
                shape = RoundedCornerShape(10.dp),
                modifier = Modifier.fillMaxWidth()
            )

            if (query.trim().isNotEmpty() && !exactMatch) {
                OutlinedButton(
                    onClick = {
                        val trimmed = query.trim()
                        if (trimmed.isNotEmpty() && !isCreating) {
                            isCreating = true
                            onCreateExercise(trimmed) { created ->
                                isCreating = false
                                onSelectExercise(created, noteText.trim())
                            }
                        }
                    },
                    enabled = !isCreating,
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(min = 48.dp),
                    shape = RoundedCornerShape(10.dp)
                ) {
                    Icon(Icons.Filled.Add, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(6.dp))
                    Text("Create \"${query.trim()}\" & Add")
                }
            }

            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(max = 280.dp)
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                if (filtered.isEmpty()) {
                    Text(
                        text = if (exercises.isEmpty()) {
                            "No exercises yet — type a name above to create one."
                        } else {
                            "No exercises matching \"$query\"."
                        },
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(vertical = 8.dp)
                    )
                } else {
                    filtered.forEach { ex ->
                        OutlinedCard(
                            onClick = { onSelectExercise(ex, noteText.trim()) },
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(10.dp)
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 12.dp, vertical = 10.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = ex.name,
                                    style = MaterialTheme.typography.bodyLarge,
                                    fontWeight = FontWeight.Medium,
                                    modifier = Modifier.weight(1f)
                                )
                                Text(
                                    text = ex.category.label,
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }
                }
            }

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 16.dp),
                horizontalArrangement = Arrangement.End
            ) {
                TextButton(
                    onClick = onDismiss,
                    modifier = Modifier.heightIn(min = 48.dp)
                ) {
                    Text("Cancel")
                }
            }
        }
    }
}
