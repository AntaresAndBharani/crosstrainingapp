package com.fractanomics.crosstraining.ui.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.isSystemInDarkTheme
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
import com.fractanomics.crosstraining.data.model.RepMax
import com.fractanomics.crosstraining.data.model.SessionWithBlocks
import com.fractanomics.crosstraining.ui.BlockDraft
import com.fractanomics.crosstraining.ui.RepMaxDraft
import com.fractanomics.crosstraining.ui.SetDraft
import com.fractanomics.crosstraining.ui.trimmed
import com.fractanomics.crosstraining.util.AvailableBlock
import com.fractanomics.crosstraining.util.AvailableBlockCodec
import com.fractanomics.crosstraining.util.AvailableBlockExercise
import com.fractanomics.crosstraining.util.EvaluatedPrChip
import com.fractanomics.crosstraining.util.HydratedAvailableBlockSeed
import com.fractanomics.crosstraining.util.STANDARD_RM_REPS
import com.fractanomics.crosstraining.util.TrackedPrSlot
import java.math.BigDecimal
import java.math.RoundingMode
import java.util.UUID

private val PrEmeraldDark = Color(0xFF0B6E3F)
private val PrEmeraldAccent = Color(0xFF2ECC71)
private val PrEmeraldOnDarkContainer = Color(0xFFA7F3D0)

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
    val subBlockTitle: String = ""
) {
    val weights: SnapshotStateList<String> = initialWeights.ifEmpty { listOf("") }.toMutableStateList()
    val perSetReps: SnapshotStateList<Int> = List(this.weights.size) { idx ->
        initialPerSetReps.getOrNull(idx)?.coerceAtLeast(1) ?: defaultReps.coerceAtLeast(1)
    }.toMutableStateList()
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
    val uiKey: String = UUID.randomUUID().toString()
) {
    val exerciseSections: SnapshotStateList<LoadedAvailableBlockExerciseState> = exerciseStates.toMutableStateList()
    val trackedPrSlots: SnapshotStateList<TrackedPrSlot> = initialTrackedPrs.toMutableStateList()
    val prWeightOverrides: SnapshotStateMap<String, String> = mutableStateMapOf()
    val customWeightOverrideFlags: SnapshotStateMap<String, Boolean> = mutableStateMapOf()
    val persistedSessionPrKeys: SnapshotStateMap<String, Boolean> = mutableStateMapOf()
    val explicitlyAppliedPrKeys: SnapshotStateMap<String, Boolean> = mutableStateMapOf()
    var mutationCount by mutableIntStateOf(0)

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
            .flatMap { it.weights.asSequence() }
            .mapNotNull { AvailableBlockCodec.normalizedPositiveWeight(it) }
            .maxOrNull() ?: 0.0

    fun maxSetWeightFor(movementId: Long): Double {
        if (movementId > 0L) {
            val matchSections = exerciseSections.filter { it.exerciseId == movementId }
            if (matchSections.isNotEmpty()) {
                return matchSections.asSequence()
                    .flatMap { it.weights.asSequence() }
                    .mapNotNull { AvailableBlockCodec.normalizedPositiveWeight(it) }
                    .maxOrNull() ?: 0.0
            }
        }
        return blockMaxSetWeight()
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

    fun addSet(exState: LoadedAvailableBlockExerciseState) {
        exState.weights.add("")
        exState.perSetReps.add(exState.perSetReps.lastOrNull() ?: exState.defaultReps.coerceAtLeast(1))
        syncNonOverriddenPrWeightsFor(exState.exerciseId, exState.exerciseName)
        mutationCount += 1
    }

    fun removeSet(exState: LoadedAvailableBlockExerciseState, setIndex: Int) {
        if (exState.weights.size <= 1 || setIndex !in exState.weights.indices) return
        exState.weights.removeAt(setIndex)
        if (setIndex in exState.perSetReps.indices) {
            exState.perSetReps.removeAt(setIndex)
        }
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

        if (exerciseSections.isEmpty()) {
            return listOf(
                BlockDraft(
                    name = block.title,
                    kind = rawKind,
                    format = rawFormat,
                    scheme = rawScheme,
                    existingExerciseId = activeSessionPrs.firstOrNull()?.movementId,
                    newExerciseName = null,
                    routineId = block.id.takeIf { it > 0L },
                    description = block.instructions,
                    resultText = "",
                    resultValue = null,
                    sets = listOf(SetDraft(reps = 1, weight = null)),
                    newRepMaxReps = null,
                    newRepMaxWeight = null,
                    section = rawSection,
                    exerciseIdsCsv = "",
                    subBlock = block.title,
                    isCompleted = rawIsCompleted,
                    newRepMaxes = activeSessionPrs.map { RepMaxDraft(it.movementId, it.rmReps, it.candidateWeight) }
                )
            )
        }

        return exerciseSections.mapIndexed { idx, exState ->
            val mappedSets = mutableListOf<SetDraft>()
            exState.weights.forEachIndexed { setIdx, raw ->
                val weight = AvailableBlockCodec.normalizedPositiveWeight(raw)
                if (weight != null) {
                    val reps = exState.perSetReps.getOrNull(setIdx)?.coerceAtLeast(1)
                        ?: exState.defaultReps.coerceAtLeast(1)
                    mappedSets += SetDraft(reps = reps, weight = weight)
                }
            }
            val sets = if (mappedSets.isNotEmpty()) {
                mappedSets
            } else {
                listOf(SetDraft(reps = exState.defaultReps.coerceAtLeast(1), weight = null))
            }
            val prsForThis = activeSessionPrs.filter { it.movementId == exState.exerciseId } +
                if (idx == 0) unmatchedPrs else emptyList()

            BlockDraft(
                name = exState.exerciseName,
                kind = exState.rawKind,
                format = exState.rawFormat,
                scheme = exState.rawScheme,
                existingExerciseId = exState.exerciseId.takeIf { it > 0L },
                newExerciseName = if (exState.exerciseId <= 0L) exState.exerciseName else null,
                routineId = block.id.takeIf { it > 0L },
                description = block.instructions,
                resultText = "",
                resultValue = null,
                sets = sets,
                newRepMaxReps = null,
                newRepMaxWeight = null,
                section = exState.rawSection,
                exerciseIdsCsv = exState.exerciseId.takeIf { it > 0L }?.toString().orEmpty(),
                subBlock = block.title.ifBlank { exState.subBlockTitle },
                isCompleted = exState.rawIsCompleted,
                newRepMaxes = prsForThis.map { RepMaxDraft(it.movementId, it.rmReps, it.candidateWeight) }
            )
        }
    }

    fun inheritedTrackedPrSlotsForParent(exercises: List<Exercise>): Pair<Long, List<TrackedPrSlot>>? {
        if (block.id <= 0L || trackedPrSlots.isEmpty()) return null
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

internal fun buildLoadedAvailableBlockState(
    block: AvailableBlock,
    sessions: List<SessionWithBlocks>,
    exercises: List<Exercise>,
    weightUnit: String,
    isEditMode: Boolean = false,
    editingSessionId: Long? = null
): LoadedAvailableBlockState {
    val lastSession = AvailableBlockCodec.resolveLastBlockSession(
        block = block,
        sessions = sessions,
        exercises = exercises,
        weightUnit = weightUnit,
        excludeSessionId = editingSessionId
    )
    val exStates = block.exercises.map { ex ->
        val prevWeights = lastSession?.exerciseWeightsById?.get(ex.id)
            ?: lastSession?.exerciseWeightsByName?.get(ex.name.trim().lowercase())
        val initialWeights = if (!prevWeights.isNullOrEmpty()) {
            prevWeights.map { w -> w?.takeIf { it > 0.0 }?.trimmed() ?: "" }
        } else {
            listOf("", "", "")
        }
        val dateBanner = if (!prevWeights.isNullOrEmpty() && lastSession != null) {
            "Pre-filled from last session (${lastSession.dateFormatted})"
        } else {
            null
        }
        LoadedAvailableBlockExerciseState(
            exerciseId = ex.id,
            exerciseName = ex.name,
            lastSessionDateLabel = dateBanner,
            initialWeights = initialWeights
        )
    }
    val byLowerName = exercises.associateBy { it.name.trim().lowercase() }
    val resolvedSlots = AvailableBlockCodec.normalizeTrackedPrSlots(
        block.trackedPrs.map { slot ->
            if (slot.movementId > 0L) slot
            else slot.copy(movementId = byLowerName[slot.movementName.trim().lowercase()]?.id ?: 0L)
        }
    )
    return LoadedAvailableBlockState(
        block = block,
        exerciseStates = exStates,
        initialTrackedPrs = resolvedSlots,
        isEditMode = isEditMode,
        editingSessionId = editingSessionId
    )
}

internal fun buildCustomAvailableBlockState(
    block: AvailableBlock,
    isEditMode: Boolean = false,
    editingSessionId: Long? = null
): LoadedAvailableBlockState {
    val exStates = block.exercises.map { ex ->
        LoadedAvailableBlockExerciseState(
            exerciseId = ex.id,
            exerciseName = ex.name,
            lastSessionDateLabel = null,
            initialWeights = listOf("", "", "")
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

    val exStates = seed.exercises.map { exSeed ->
        val initialWeights = exSeed.weights.map { w -> if (w > 0.0) w.trimmed() else "" }
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
            subBlockTitle = exSeed.subBlockTitle
        )
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
        rawIsCompleted = seed.rawIsCompleted
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
    modifier: Modifier = Modifier
) {
    val evaluatedChips = blockState.evaluatePrChips(exercises, repMaxes)
    val fontScale = LocalConfiguration.current.fontScale
    val columns = if (fontScale >= 1.4f) 2 else 4
    val isDark = isSystemInDarkTheme()

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
            // Card Header: BLOCK: {Title} + Timer + Remove
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "BLOCK: ${blockState.block.title.ifBlank { "Untitled Block" }}",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary,
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
                            contentDescription = "Start timer for ${blockState.block.title}",
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(20.dp)
                        )
                    }
                    IconButton(
                        onClick = onRemoveBlock,
                        modifier = Modifier.minimumInteractiveComponentSize()
                    ) {
                        Icon(
                            Icons.Filled.Delete,
                            contentDescription = "Remove block ${blockState.block.title}",
                            tint = MaterialTheme.colorScheme.error,
                            modifier = Modifier.size(20.dp)
                        )
                    }
                }
            }

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
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Text(
                        text = exState.exerciseName,
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.SemiBold
                    )
                    if (!exState.lastSessionDateLabel.isNullOrBlank()) {
                        Text(
                            text = exState.lastSessionDateLabel,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    BoxWithConstraints(modifier = Modifier.fillMaxWidth()) {
                        val spacing = 8.dp
                        val tileWidth = (maxWidth - spacing * (columns - 1)) / columns
                        FlowRow(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(spacing),
                            verticalArrangement = Arrangement.spacedBy(spacing),
                            maxItemsInEachRow = columns
                        ) {
                            exState.weights.forEachIndexed { idx, w ->
                                WeightOnlySetTile(
                                    setNumber = idx + 1,
                                    value = w,
                                    unit = weightUnit,
                                    canRemove = exState.weights.size > 1,
                                    onValueChange = { updated -> blockState.updateSetWeight(exState, idx, updated) },
                                    onRemove = { blockState.removeSet(exState, idx) },
                                    modifier = Modifier.width(tileWidth)
                                )
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

            // Live PR Chips + "+ Record PR 🔥"
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
                        isHighlightedPr && isDark -> PrEmeraldDark.copy(alpha = 0.28f)
                        isHighlightedPr -> PrEmeraldAccent.copy(alpha = 0.16f)
                        else -> MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.7f)
                    }
                    val contentColor = when {
                        isHighlightedPr && isDark -> PrEmeraldOnDarkContainer
                        isHighlightedPr -> PrEmeraldDark
                        else -> MaterialTheme.colorScheme.onSurfaceVariant
                    }
                    val borderColor = when {
                        chip.isNewPr -> PrEmeraldAccent
                        isPersistedOrExplicitPr -> PrEmeraldDark.copy(alpha = 0.75f)
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
    val hasWeight = AvailableBlockCodec.normalizedPositiveWeight(value) != null
    val tileSemantic = if (hasWeight) {
        "Set $setNumber, $value $unit"
    } else {
        "Set $setNumber, empty weight"
    }
    Surface(
        modifier = modifier
            .heightIn(min = 76.dp)
            .semantics(mergeDescendants = true) {
                contentDescription = tileSemantic
            },
        shape = RoundedCornerShape(12.dp),
        color = if (hasWeight) {
            MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.35f)
        } else {
            MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f)
        },
        border = BorderStroke(
            width = 1.dp,
            color = if (hasWeight) MaterialTheme.colorScheme.primary.copy(alpha = 0.5f) else MaterialTheme.colorScheme.outlineVariant
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
                        contentDescription = "Remove set $setNumber",
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
                    text = "Set $setNumber",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.Center,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    BasicTextField(
                        value = value,
                        onValueChange = { raw ->
                            val filtered = raw.filter { it.isDigit() || it == '.' || it == ',' }.take(8)
                            onValueChange(filtered)
                        },
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(
                            keyboardType = KeyboardType.Decimal,
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
                    Spacer(Modifier.width(2.dp))
                    Text(
                        text = unit,
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
    }
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
    selectedCount <= 0 -> "Select Block(s)"
    selectedCount == 1 -> "Add 1 Block"
    else -> "Add $selectedCount Blocks"
}

/**
 * Unified modal sheet for adding blocks to the active session from either the saved
 * Available Blocks library or a custom exercise block definition.
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
    // an in-progress Custom Block form) whenever a background sync or library save emits mid-sheet.
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
                text = "Add Block to Session",
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
                            text = "Saved Blocks (${availableBlocks.size})",
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
                            text = "Custom Block",
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
        placeholder = { Text("Search available blocks...") },
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
                text = "No saved available blocks yet. Build a custom block right here or save one for future sessions.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            OutlinedButton(
                onClick = onSwitchToCustom,
                modifier = Modifier.heightIn(min = 48.dp),
                shape = RoundedCornerShape(10.dp)
            ) {
                Text("Switch to Custom Block")
            }
        }
    } else if (filtered.isEmpty()) {
        Text(
            text = "No blocks matching \"$query\".",
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
            onValueChange = { rawTitle = it },
            label = { Text("Block Name (optional)") },
            placeholder = { Text(placeholderTitle) },
            singleLine = true,
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
                    "No movements yet — add one below or enter a Block Name above."
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
                    text = "Save to Available Blocks library",
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.SemiBold
                )
                Text(
                    text = "Reuse this block in future workouts",
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
            Text("Add Block")
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
                                                text = "In this block",
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
                                                    color = if (isSystemInDarkTheme()) PrEmeraldOnDarkContainer else PrEmeraldDark
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
