package com.fractanomics.crosstraining.ui.draft

import androidx.compose.runtime.Immutable
import com.fractanomics.crosstraining.data.model.BlockKind
import com.fractanomics.crosstraining.data.model.MetricType
import com.fractanomics.crosstraining.util.ExerciseTargetMetric
import java.time.LocalDate

@Immutable
data class ActiveTrackedPrSlotSnapshot(
    val movementId: Long,
    val movementName: String,
    val rmReps: Int
)

@Immutable
data class ActiveBlockExerciseSnapshot(
    val id: Long,
    val name: String,
    val tracksRepMax: Boolean
)

@Immutable
data class ActiveRoutineTargetSpecSnapshot(
    val metric: String,
    val mode: String,
    val fixedValue: Int,
    val perSetValues: List<Int>
)

@Immutable
data class ActiveRoutineItemSnapshot(
    val kind: String, // "BLOCK" or "EXERCISE"
    val blockId: Long = 0L,
    val blockTitle: String = "",
    val exerciseId: Long = 0L,
    val exerciseName: String = "",
    val note: String = "",
    val defaultTrackedPrs: List<ActiveTrackedPrSlotSnapshot> = emptyList(),
    val targetSetsCount: Int? = null,
    val targetSpec: ActiveRoutineTargetSpecSnapshot? = null,
    val exerciseTargets: Map<Long, ActiveRoutineTargetSpecSnapshot> = emptyMap(),
    val exerciseTargetsByName: Map<String, ActiveRoutineTargetSpecSnapshot> = emptyMap()
)

@Immutable
data class ActiveExerciseSectionSnapshot(
    val exerciseId: Long,
    val exerciseName: String,
    val lastSessionDateLabel: String? = null,
    val activeMetric: String = ExerciseTargetMetric.REPS.name,
    val weights: List<String> = emptyList(),
    val perSetReps: List<Int> = emptyList(),
    val repsInputs: List<String> = emptyList(),
    val metricInputs: List<String> = emptyList(),
    val groupIndices: List<Int?> = emptyList(),
    val skippedFlags: List<Boolean> = emptyList(),
    val defaultReps: Int = 1,
    val sourceBlockId: Long? = null,
    val rawFormat: String = "",
    val rawScheme: String = "",
    val rawSection: String = "",
    val rawKind: String = BlockKind.STRENGTH.name,
    val rawIsCompleted: Boolean = true,
    val subBlockTitle: String = "",
    val targetBanner: String? = null,
    val rawNotes: String = "",
    val libraryMetricType: String = MetricType.WEIGHT.name
) {
    fun hasLoggedValueAt(setIndex: Int): Boolean {
        if (skippedFlags.getOrElse(setIndex) { false }) return false
        return if (activeMetric == ExerciseTargetMetric.REPS.name) {
            weights.getOrNull(setIndex)?.trim().orEmpty().isNotEmpty() ||
                repsInputs.getOrNull(setIndex)?.trim().orEmpty().isNotEmpty()
        } else {
            metricInputs.getOrNull(setIndex)?.trim().orEmpty().isNotEmpty()
        }
    }
}

@Immutable
data class ActiveLoadedBlockSnapshot(
    val uiKey: String,
    val itemKind: String = "BLOCK", // "BLOCK" or "EXERCISE"
    val blockId: Long = 0L,
    val blockTitle: String = "",
    val blockInstructions: String = "",
    val blockExercises: List<ActiveBlockExerciseSnapshot> = emptyList(),
    val rawFormat: String = "",
    val rawScheme: String = "",
    val rawSection: String = "",
    val rawKind: String = BlockKind.STRENGTH.name,
    val rawIsCompleted: Boolean = true,
    val lastSessionDateFormatted: String? = null,
    val hasPreFilledHistory: Boolean = false,
    val trackedPrSlots: List<ActiveTrackedPrSlotSnapshot> = emptyList(),
    val prWeightOverrides: Map<String, String> = emptyMap(),
    val customWeightOverrideFlags: Map<String, Boolean> = emptyMap(),
    val explicitlyAppliedPrKeys: Map<String, Boolean> = emptyMap(),
    val sourceRoutineItem: ActiveRoutineItemSnapshot? = null,
    val exerciseSections: List<ActiveExerciseSectionSnapshot> = emptyList()
) {
    fun countLoggedSets(): Int {
        if (exerciseSections.isEmpty()) return 0
        val isCompact = itemKind == "BLOCK" && exerciseSections.size >= 2
        if (isCompact) {
            val maxRounds = exerciseSections.maxOfOrNull {
                maxOf(it.weights.size, it.repsInputs.size, it.metricInputs.size)
            } ?: 0
            return (0 until maxRounds).count { roundIdx ->
                exerciseSections.any { sec -> sec.hasLoggedValueAt(roundIdx) }
            }
        }
        val sec = exerciseSections.first()
        val count = maxOf(sec.weights.size, sec.repsInputs.size, sec.metricInputs.size)
        return (0 until count).count { setIdx -> sec.hasLoggedValueAt(setIdx) }
    }
}

@Immutable
data class ActiveLegacySetSnapshot(
    val reps: String = "",
    val value: String = "",
    val group: String = "",
    val isWarmup: Boolean = false,
    val isFailed: Boolean = false
)

@Immutable
data class ActiveLegacyBlockSnapshot(
    val name: String = "",
    val kind: String = BlockKind.STRENGTH.name,
    val format: String = "",
    val scheme: String = "",
    val exerciseId: Long? = null,
    val newExerciseName: String = "",
    val routineId: Long? = null,
    val sequenceExerciseIds: List<Long> = emptyList(),
    val description: String = "",
    val resultText: String = "",
    val resultValue: String = "",
    val recordRm: Boolean = false,
    val rmReps: String = "1",
    val rmWeight: String = "",
    val isExpanded: Boolean = false,
    val section: String = "",
    val exerciseIdsCsv: String = "",
    val subBlock: String = "",
    val isCompleted: Boolean = true,
    val sets: List<ActiveLegacySetSnapshot> = listOf(ActiveLegacySetSnapshot())
) {
    fun isBlankPlaceholder(): Boolean =
        name.isBlank() &&
            exerciseId == null &&
            newExerciseName.isBlank() &&
            description.isBlank() &&
            resultText.isBlank() &&
            resultValue.isBlank() &&
            !recordRm &&
            sets.all { it.reps.isBlank() && it.value.isBlank() }

    fun countLoggedSets(): Int =
        sets.count { it.reps.isNotBlank() || it.value.isNotBlank() }
}

@Immutable
data class ActiveWorkoutDraftSnapshot(
    val schemaVersion: Int = CURRENT_SCHEMA_VERSION,
    val selectedCycleId: Long? = null,
    val dateEpochDay: Long = LocalDate.now().toEpochDay(),
    val title: String = "",
    val notes: String = "",
    val showSessionMeta: Boolean = false,
    val loadedRoutineId: Long? = null,
    val loadedRoutineTitle: String = "",
    val initialRoutineSignature: String = "",
    val syncRoutineTemplateChecked: Boolean = false,
    val scrollOffsetPx: Int = 0,
    val loadedBlocks: List<ActiveLoadedBlockSnapshot> = emptyList(),
    val legacyBlocks: List<ActiveLegacyBlockSnapshot> = emptyList(),
    val updatedAtEpochMillis: Long = 0L
) {
    fun sessionDate(): LocalDate = LocalDate.ofEpochDay(dateEpochDay)

    fun isMeaningfullyActive(): Boolean =
        loadedBlocks.isNotEmpty() ||
            legacyBlocks.any { !it.isBlankPlaceholder() } ||
            title.isNotBlank() ||
            notes.isNotBlank() ||
            loadedRoutineId != null

    fun resolveBlockKeys(): Set<String> {
        val keys = linkedSetOf<String>()
        loadedBlocks.forEach { block ->
            if (block.uiKey.isNotBlank()) keys += block.uiKey
        }
        val nonBlankLegacy = legacyBlocks.any { !it.isBlankPlaceholder() }
        if (nonBlankLegacy) {
            var i = 0
            while (i < legacyBlocks.size) {
                val blk = legacyBlocks[i]
                val trimmedSub = blk.subBlock.trim()
                val trimmedSection = blk.section.trim()
                if (trimmedSub.isBlank()) {
                    if (!blk.isBlankPlaceholder()) {
                        keys += "legacy_$i"
                    }
                    i++
                } else {
                    val startIndex = i
                    var j = i + 1
                    while (j < legacyBlocks.size) {
                        val next = legacyBlocks[j]
                        if (next.subBlock.trim().equals(trimmedSub, ignoreCase = true) &&
                            next.section.trim().equals(trimmedSection, ignoreCase = true)
                        ) {
                            j++
                        } else {
                            break
                        }
                    }
                    keys += "legacy_sub_${startIndex}_$trimmedSub"
                    i = j
                }
            }
        }
        return keys
    }

    fun toSummary(referenceDate: LocalDate = LocalDate.now()): ActiveWorkoutSummary {
        val active = isMeaningfullyActive()
        if (!active) {
            return ActiveWorkoutSummary(
                isActive = false,
                sessionDate = sessionDate()
            )
        }
        val activeLegacyBlocks = legacyBlocks.filter { !it.isBlankPlaceholder() }
        val totalBlocks = loadedBlocks.size + activeLegacyBlocks.size
        val loadedBlockSetCounts = loadedBlocks.map { it.countLoggedSets() }
        val legacyBlockSetCounts = activeLegacyBlocks.map { it.countLoggedSets() }
        val loggedSetCount = loadedBlockSetCounts.sum() + legacyBlockSetCounts.sum()
        val loggedBlockCount = loadedBlockSetCounts.count { it > 0 } + legacyBlockSetCounts.count { it > 0 }

        val firstBlockTitle = loadedBlocks.firstOrNull()?.blockTitle?.trim().orEmpty()
            .ifBlank { activeLegacyBlocks.firstOrNull()?.name?.trim().orEmpty() }
        val displayTitle = title.trim()
            .ifBlank { loadedRoutineTitle.trim() }
            .ifBlank { firstBlockTitle }
            .ifBlank { "Active workout" }

        val date = sessionDate()
        return ActiveWorkoutSummary(
            isActive = true,
            displayTitle = displayTitle,
            blockCount = totalBlocks,
            loggedBlockCount = loggedBlockCount,
            loggedSetCount = loggedSetCount,
            sessionDate = date,
            isStale = date.isBefore(referenceDate),
            blockKeys = resolveBlockKeys()
        )
    }

    companion object {
        const val CURRENT_SCHEMA_VERSION = 1
    }
}

@Immutable
data class ActiveWorkoutSummary(
    val isActive: Boolean = false,
    val displayTitle: String = "",
    val blockCount: Int = 0,
    val loggedBlockCount: Int = 0,
    val loggedSetCount: Int = 0,
    val sessionDate: LocalDate = LocalDate.now(),
    val isStale: Boolean = false,
    val blockKeys: Set<String> = emptySet()
)
