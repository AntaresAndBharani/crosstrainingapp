package com.fractanomics.crosstraining.util

import com.fractanomics.crosstraining.data.model.BlockKind
import com.fractanomics.crosstraining.data.model.BlockWithSets
import com.fractanomics.crosstraining.data.model.Exercise
import com.fractanomics.crosstraining.data.model.RepMax
import com.fractanomics.crosstraining.data.model.Routine
import com.fractanomics.crosstraining.data.model.RoutineBlock
import com.fractanomics.crosstraining.data.model.RoutineWithBlocks
import com.fractanomics.crosstraining.data.model.SessionBlock
import com.fractanomics.crosstraining.data.model.SessionWithBlocks
import com.fractanomics.crosstraining.ui.formatShort
import com.fractanomics.crosstraining.ui.trimmed
import java.net.URLDecoder
import java.net.URLEncoder
import java.nio.charset.StandardCharsets

/**
 * Persisted `Routine.defaultFormat` discriminator identifying a streamlined Available Block
 * (`SPEC-021`) stored inside `RoutineWithBlocks` without requiring Room schema or Firestore rule changes.
 */
const val AVAILABLE_BLOCK_FORMAT = "AVAILABLE_BLOCK"

/** Quick RM selector pills offered in `MultiMovementPrPickerSheet`. */
val STANDARD_RM_REPS = listOf(1, 2, 3, 5, 10)

/**
 * One Basic Movement PR link tracked on an [AvailableBlock] and auto-inherited across sessions.
 */
data class TrackedPrSlot(
    val movementId: Long,
    val movementName: String,
    val rmReps: Int
)

/** Ordered movement summary linked to an [AvailableBlock]. */
data class AvailableBlockExercise(
    val id: Long,
    val name: String,
    val tracksRepMax: Boolean
)

/** Domain representation of a reusable Available Block card/session template. */
data class AvailableBlock(
    val id: Long,
    val title: String,
    val instructions: String,
    val exercises: List<AvailableBlockExercise>,
    val trackedPrs: List<TrackedPrSlot>,
    val rawRoutine: RoutineWithBlocks? = null
) {
    val isExplicitAvailableBlock: Boolean
        get() = rawRoutine?.routine?.defaultFormat.equals(AVAILABLE_BLOCK_FORMAT, ignoreCase = true)
}

/** Pre-fill snapshot resolved from the most recent historical session for a block. */
data class LastBlockSessionSummary(
    val dateIso: String,
    val dateFormatted: String,
    val exerciseWeightsById: Map<Long, List<Double?>>,
    val exerciseWeightsByName: Map<String, List<Double?>>,
    val topWeight: Double?,
    val averageWeight: Double?,
    val setWeightsSummary: String
)

/** Live-evaluated Basic Movement PR chip state inside Log Session. */
data class EvaluatedPrChip(
    val movementId: Long,
    val movementName: String,
    val rmReps: Int,
    val candidateWeight: Double,
    val storedBestWeight: Double,
    val isNewPr: Boolean
)

data class HydratedBlockExerciseSeed(
    val sourceBlockId: Long? = null,
    val movementId: Long,
    val movementName: String,
    val weights: List<Double>,
    val perSetReps: List<Int>,
    val defaultReps: Int = 1,
    val rawFormat: String = "",
    val rawScheme: String = "",
    val rawSection: String = "",
    val rawKind: BlockKind = BlockKind.STRENGTH,
    val rawIsCompleted: Boolean = true,
    val subBlockTitle: String = ""
)

data class HydratedAvailableBlockSeed(
    val resolvedAvailableBlock: AvailableBlock?,
    val blockTitle: String,
    val cleanNotes: String,
    val sourceBlockIds: List<Long>,
    val rawRoutineId: Long? = null,
    val rawFormat: String = "",
    val rawScheme: String = "",
    val rawSection: String = "",
    val rawKind: BlockKind = BlockKind.STRENGTH,
    val rawIsCompleted: Boolean = true,
    val exercises: List<HydratedBlockExerciseSeed>,
    val persistedRepMaxes: List<RepMax>
)

data class SessionBlockHydrationResult(
    val simplifiedBlocks: List<HydratedAvailableBlockSeed>,
    val residualLegacyBlocks: List<BlockWithSets>
)

/**
 * Pure deterministic codec, parser, search filter, and PR evaluator for Available Blocks (`SPEC-021`).
 */
object AvailableBlockCodec {

    private const val HEADER_PREFIX = "[ab_prs="
    private val HEADER_REGEX = Regex("""\[ab_prs=([^\]]*)]""", RegexOption.IGNORE_CASE)

    fun hasAbPrsHeader(notes: String?): Boolean {
        if (notes.isNullOrBlank()) return false
        return HEADER_REGEX.containsMatchIn(notes)
    }

    fun extractCleanInstructions(notes: String?): String =
        RoutineTargetCodec.extractCleanNotes(notes)

    /**
     * Serializes [trackedPrs] into a single `[ab_prs=name%201:3;name%202:1]` header line.
     */
    fun encodeTrackedPrHeader(trackedPrs: List<TrackedPrSlot>): String {
        val normalized = normalizeTrackedPrSlots(trackedPrs)
        if (normalized.isEmpty()) return ""
        val entries = normalized.joinToString(";") { slot ->
            val encodedName = URLEncoder.encode(slot.movementName.trim(), StandardCharsets.UTF_8.name())
            "$encodedName:${slot.rmReps}"
        }
        return "$HEADER_PREFIX$entries]"
    }

    /**
     * Prepends `[ab_prs=...]` to [cleanInstructions] when [trackedPrs] is non-empty.
     */
    fun encodeNotesWithTrackedPrs(
        cleanInstructions: String,
        trackedPrs: List<TrackedPrSlot>
    ): String {
        val clean = extractCleanInstructions(cleanInstructions)
        val header = encodeTrackedPrHeader(trackedPrs)
        return when {
            header.isBlank() -> clean
            clean.isBlank() -> header
            else -> "$header\n$clean"
        }
    }

    /**
     * Removes every `[ab_prs=...]` header from [notes] while preserving all other content,
     * including a leading `[rb_v2=...]` structured target header.
     */
    fun stripTrackedPrHeaders(notes: String?): String {
        if (notes.isNullOrEmpty()) return ""
        return notes.lines()
            .mapNotNull { line ->
                if (!HEADER_REGEX.containsMatchIn(line)) return@mapNotNull line
                // Drop lines that only carried the header; keep any trailing text on the same line.
                line.replace(HEADER_REGEX, "").takeIf { it.isNotBlank() }
            }
            .joinToString("\n")
            .trim()
    }

    /**
     * Replaces the `[ab_prs=...]` header inside [existingNotes] with [trackedPrs] without touching
     * the rest of the notes. A leading `[rb_v2=...]` header stays on the first line so
     * [RoutineTargetCodec.hasStructuredHeader] / `decodeHeaderSlots` keep working for legacy routines.
     */
    fun replaceTrackedPrHeader(existingNotes: String?, trackedPrs: List<TrackedPrSlot>): String {
        val withoutHeader = stripTrackedPrHeaders(existingNotes)
        val header = encodeTrackedPrHeader(trackedPrs)
        if (header.isBlank()) return withoutHeader
        if (withoutHeader.isBlank()) return header
        return if (RoutineTargetCodec.hasStructuredHeader(withoutHeader)) {
            val firstBreak = withoutHeader.indexOf('\n')
            if (firstBreak < 0) {
                "$withoutHeader\n$header"
            } else {
                withoutHeader.substring(0, firstBreak) + "\n" + header + "\n" +
                    withoutHeader.substring(firstBreak + 1)
            }
        } else {
            "$header\n$withoutHeader"
        }
    }

    /**
     * Rewrites only the `[ab_prs=...]` metadata across [blocks] (header placed on the first block by
     * position, stripped from all others) so legacy daily routines keep their formats, set counts,
     * rep schemes, and `[rb_v2=...]` targets when their tracked PR links change.
     */
    fun applyTrackedPrsToRoutineBlocks(
        blocks: List<RoutineBlock>,
        trackedPrs: List<TrackedPrSlot>
    ): List<RoutineBlock> {
        val sorted = blocks.sortedBy { it.position }
        if (sorted.isEmpty()) return sorted
        return sorted.mapIndexed { idx, rb ->
            rb.copy(
                notes = if (idx == 0) {
                    replaceTrackedPrHeader(rb.notes, trackedPrs)
                } else {
                    stripTrackedPrHeaders(rb.notes)
                }
            )
        }
    }

    /**
     * Decodes `[ab_prs=...]` metadata inside [notes], resolving local exercise IDs and names
     * case-insensitively where available.
     */
    fun decodeTrackedPrHeader(
        notes: String?,
        exercisesById: Map<Long, Exercise> = emptyMap(),
        exercisesByLowerName: Map<String, Exercise> = emptyMap()
    ): List<TrackedPrSlot> {
        if (notes.isNullOrBlank()) return emptyList()
        val slots = mutableListOf<TrackedPrSlot>()
        HEADER_REGEX.findAll(notes).forEach { match ->
            val payload = match.groupValues.getOrNull(1).orEmpty().trim()
            if (payload.isEmpty()) return@forEach
            payload.split(";").forEach { rawEntry ->
                val trimmed = rawEntry.trim()
                if (trimmed.isEmpty()) return@forEach
                val sepIdx = trimmed.lastIndexOf(':')
                if (sepIdx <= 0 || sepIdx >= trimmed.lastIndex) return@forEach
                val encodedName = trimmed.substring(0, sepIdx)
                val rmReps = trimmed.substring(sepIdx + 1).trim().toIntOrNull()
                    ?.takeIf { it in 1..20 } ?: return@forEach
                val decodedName = runCatching {
                    URLDecoder.decode(encodedName, StandardCharsets.UTF_8.name())
                }.getOrDefault(encodedName).trim()
                if (decodedName.isEmpty()) return@forEach
                val matched = exercisesByLowerName[decodedName.lowercase()]
                    ?: exercisesById.values.firstOrNull { it.name.equals(decodedName, ignoreCase = true) }
                slots += TrackedPrSlot(
                    movementId = matched?.id ?: 0L,
                    movementName = matched?.name ?: decodedName,
                    rmReps = rmReps
                )
            }
        }
        return normalizeTrackedPrSlots(slots)
    }

    fun normalizeTrackedPrSlots(slots: List<TrackedPrSlot>): List<TrackedPrSlot> {
        val seen = LinkedHashMap<String, TrackedPrSlot>()
        slots.forEach { slot ->
            val cleanName = slot.movementName.trim()
            if (cleanName.isEmpty() || slot.rmReps <= 0) return@forEach
            val key = if (slot.movementId > 0L) {
                "id:${slot.movementId}:${slot.rmReps}"
            } else {
                "name:${cleanName.lowercase()}:${slot.rmReps}"
            }
            seen.putIfAbsent(
                key,
                slot.copy(movementName = cleanName, rmReps = slot.rmReps.coerceIn(1, 20))
            )
        }
        return seen.values.toList()
    }

    /**
     * Converts an [AvailableBlock] into a Room/Firestore-compatible [RoutineWithBlocks]
     * with `defaultFormat = AVAILABLE_BLOCK` and `[ab_prs=...]` encoding.
     */
    fun toRoutineWithBlocks(block: AvailableBlock): RoutineWithBlocks {
        val cleanTitle = block.title.trim().ifEmpty { "Available Block" }
        val cleanInstructions = extractCleanInstructions(block.instructions)
        val distinctExercises = block.exercises.distinctBy { it.id }
        val routine = Routine(
            id = block.id,
            name = cleanTitle,
            mainExerciseId = distinctExercises.firstOrNull()?.id,
            description = cleanInstructions,
            defaultFormat = AVAILABLE_BLOCK_FORMAT
        )
        val routineBlocks = if (distinctExercises.isEmpty()) {
            listOf(
                RoutineBlock(
                    routineId = block.id,
                    position = 0,
                    name = cleanTitle,
                    kind = BlockKind.STRENGTH,
                    format = "",
                    setsCount = 3,
                    targetRepsScheme = "",
                    exerciseIdsCsv = "",
                    notes = encodeNotesWithTrackedPrs(cleanInstructions, block.trackedPrs),
                    section = "",
                    subBlock = cleanTitle
                )
            )
        } else {
            distinctExercises.mapIndexed { index, exercise ->
                RoutineBlock(
                    routineId = block.id,
                    position = index,
                    name = exercise.name,
                    kind = BlockKind.STRENGTH,
                    format = "",
                    setsCount = 3,
                    targetRepsScheme = "",
                    exerciseIdsCsv = exercise.id.toString(),
                    notes = if (index == 0) {
                        encodeNotesWithTrackedPrs(cleanInstructions, block.trackedPrs)
                    } else {
                        cleanInstructions
                    },
                    section = "",
                    subBlock = cleanTitle
                )
            }
        }
        return RoutineWithBlocks(routine = routine, blocks = routineBlocks)
    }

    /**
     * Decodes a persisted [RoutineWithBlocks] (either explicit `AVAILABLE_BLOCK` or legacy daily routine)
     * into the streamlined [AvailableBlock] UI/domain view.
     */
    fun fromRoutineWithBlocks(
        rw: RoutineWithBlocks,
        exercises: List<Exercise>
    ): AvailableBlock {
        val byId = exercises.associateBy { it.id }
        val byLowerName = exercises.associateBy { it.name.trim().lowercase() }
        val sortedBlocks = rw.blocks.sortedBy { it.position }

        val collectedExerciseIds = linkedSetOf<Long>()
        sortedBlocks.forEach { rb ->
            val csvIds = RoutineTargetCodec.parseExerciseIdsPreservingOrder(rb.exerciseIdsCsv)
            if (csvIds.isNotEmpty()) {
                collectedExerciseIds.addAll(csvIds)
            } else {
                byLowerName[rb.name.trim().lowercase()]?.let { collectedExerciseIds.add(it.id) }
            }
        }
        if (collectedExerciseIds.isEmpty()) {
            rw.routine.mainExerciseId?.takeIf { it > 0L }?.let { collectedExerciseIds.add(it) }
        }

        val resolvedExercises = collectedExerciseIds.mapNotNull { id ->
            byId[id]?.let { ex ->
                AvailableBlockExercise(
                    id = ex.id,
                    name = ex.name,
                    tracksRepMax = ex.tracksRepMax
                )
            }
        }

        val decodedTrackedPrs = normalizeTrackedPrSlots(
            sortedBlocks.flatMap { rb ->
                decodeTrackedPrHeader(
                    notes = rb.notes,
                    exercisesById = byId,
                    exercisesByLowerName = byLowerName
                )
            }
        )

        val cleanRoutineDesc = extractCleanInstructions(rw.routine.description)
        val resolvedInstructions = if (cleanRoutineDesc.isNotBlank()) {
            cleanRoutineDesc
        } else {
            sortedBlocks
                .map { extractCleanInstructions(it.notes) }
                .filter { it.isNotBlank() }
                .distinct()
                .joinToString("\n")
        }

        return AvailableBlock(
            id = rw.routine.id,
            title = rw.routine.name.ifBlank { "Untitled Block" },
            instructions = resolvedInstructions,
            exercises = resolvedExercises,
            trackedPrs = decodedTrackedPrs,
            rawRoutine = rw
        )
    }

    /**
     * Matches whether a logged [SessionBlock] belongs to [block], supporting both local `routineId`
     * matches and cross-device title/subBlock matches when local IDs differ after sync.
     */
    fun matchesAvailableBlock(
        sessionBlock: SessionBlock,
        block: AvailableBlock
    ): Boolean {
        if (block.id > 0L && sessionBlock.routineId == block.id) return true
        val title = block.title.trim()
        if (title.isEmpty()) return false
        return sessionBlock.subBlock.equals(title, ignoreCase = true) ||
            sessionBlock.name.equals(title, ignoreCase = true)
    }

    /**
     * Extracts per-exercise set weights and a formatted footer summary from the most recent
     * logged [SessionWithBlocks] for [block].
     */
    fun resolveLastBlockSession(
        block: AvailableBlock,
        sessions: List<SessionWithBlocks>,
        exercises: List<Exercise>,
        weightUnit: String = "kg",
        excludeSessionId: Long? = null
    ): LastBlockSessionSummary? {
        val byId = exercises.associateBy { it.id }
        val sortedSessions = sessions
            .asSequence()
            .filter { excludeSessionId == null || it.session.id != excludeSessionId }
            .sortedWith(
                compareByDescending<SessionWithBlocks> { it.session.date }
                    .thenByDescending { it.session.id }
            )
            .toList()

        for (sw in sortedSessions) {
            val matching = sw.blocks
                .filter { bws -> matchesAvailableBlock(bws.block, block) }
                .sortedBy { it.block.position }
            if (matching.isEmpty()) continue

            val byExerciseId = linkedMapOf<Long, List<Double?>>()
            val byExerciseName = linkedMapOf<String, List<Double?>>()
            val summaryTokens = mutableListOf<String>()
            val allPositiveWeights = mutableListOf<Double>()

            matching.forEach { bws ->
                val orderedSets = bws.sets.sortedBy { it.position }
                val weights = orderedSets.map { s -> s.weight?.takeIf { it > 0.0 } }
                val positiveWeights = weights.filterNotNull()
                allPositiveWeights.addAll(positiveWeights)

                val parsedCsvIds = RoutineTargetCodec.parseExerciseIdsPreservingOrder(bws.block.exerciseIdsCsv)
                val targetExId = bws.block.mainExerciseId?.takeIf { it > 0L }
                    ?: parsedCsvIds.singleOrNull()
                    ?: parsedCsvIds.firstOrNull()
                val resolvedName = targetExId?.let { byId[it]?.name }
                    ?: bws.block.name.trim().ifEmpty { block.title }

                if (targetExId != null && targetExId > 0L) {
                    byExerciseId[targetExId] = weights
                }
                if (resolvedName.isNotBlank()) {
                    byExerciseName[resolvedName.lowercase()] = weights
                }

                if (positiveWeights.isNotEmpty()) {
                    if (matching.size == 1 && block.exercises.size <= 1) {
                        summaryTokens += positiveWeights.take(4).joinToString(" · ") { it.trimmed() } + " $weightUnit"
                    } else {
                        val peak = positiveWeights.max()
                        summaryTokens += "$resolvedName ${peak.trimmed()} $weightUnit"
                    }
                }
            }

            val topWeight = allPositiveWeights.maxOrNull()
            val averageWeight = allPositiveWeights.takeIf { it.isNotEmpty() }?.average()
            val summaryText = when {
                summaryTokens.isNotEmpty() -> summaryTokens.joinToString(" · ")
                else -> "Completed ${sw.session.date.formatShort()}"
            }

            return LastBlockSessionSummary(
                dateIso = sw.session.date.toString(),
                dateFormatted = sw.session.date.formatShort(),
                exerciseWeightsById = byExerciseId,
                exerciseWeightsByName = byExerciseName,
                topWeight = topWeight,
                averageWeight = averageWeight,
                setWeightsSummary = summaryText
            )
        }
        return null
    }

    /**
     * Parses athlete weight input accepting both `.` and `,` decimal separators.
     * Blank input returns `null` (allowed for bodyweight/unloaded cardio work). Negative or malformed strings return `null`.
     */
    fun parseDecimalWeightInput(raw: String): Double? {
        val cleaned = raw.trim().replace(',', '.')
        if (cleaned.isEmpty()) return null
        val parsed = cleaned.toDoubleOrNull() ?: return null
        if (parsed.isNaN() || parsed.isInfinite() || parsed < 0.0) return null
        return parsed
    }

    /** Returns a weight usable for storage/analytics (`> 0.0`), or `null` when left empty or zero. */
    fun normalizedPositiveWeight(raw: String): Double? =
        parseDecimalWeightInput(raw)?.takeIf { it > 0.0 }

    /** Filters Available Blocks live by title, instructions, or linked exercise names. */
    fun filterAvailableBlocks(
        blocks: List<AvailableBlock>,
        query: String
    ): List<AvailableBlock> {
        val needle = query.trim().lowercase()
        if (needle.isEmpty()) return blocks
        return blocks.filter { block ->
            block.title.lowercase().contains(needle) ||
                block.instructions.lowercase().contains(needle) ||
                block.exercises.any { it.name.lowercase().contains(needle) } ||
                block.trackedPrs.any { it.movementName.lowercase().contains(needle) }
        }
    }

    /**
     * Looks up the stored best positive [RepMax] weight for `(movementId, rmReps)`,
     * optionally excluding records that belong to [excludeSessionId] (edit mode).
     */
    fun bestStoredRepMaxWeight(
        repMaxes: List<RepMax>,
        movementId: Long,
        rmReps: Int,
        excludeSessionId: Long? = null
    ): Double {
        if (movementId <= 0L) return 0.0
        return repMaxes
            .asSequence()
            .filter {
                it.exerciseId == movementId &&
                    it.reps == rmReps &&
                    (excludeSessionId == null || it.sessionId != excludeSessionId)
            }
            .map { it.weight }
            .maxOrNull() ?: 0.0
    }

    /** Evaluates whether [candidateWeight] beats [storedBestWeight] for [slot]. */
    fun evaluateTrackedPrChip(
        slot: TrackedPrSlot,
        candidateWeight: Double,
        storedBestWeight: Double
    ): EvaluatedPrChip = EvaluatedPrChip(
        movementId = slot.movementId,
        movementName = slot.movementName,
        rmReps = slot.rmReps,
        candidateWeight = candidateWeight.coerceAtLeast(0.0),
        storedBestWeight = storedBestWeight.coerceAtLeast(0.0),
        isNewPr = candidateWeight > 0.0 && candidateWeight > storedBestWeight
    )

    /**
     * Merges [additionalSlots] onto [existing] preserving slot order and deduplicating
     * by `(movementId, rmReps)` or case-insensitive movement name (`SPEC-022`).
     */
    fun mergeTrackedPrSlots(
        existing: AvailableBlock,
        additionalSlots: List<TrackedPrSlot>
    ): AvailableBlock {
        if (additionalSlots.isEmpty()) return existing
        val merged = normalizeTrackedPrSlots(existing.trackedPrs + additionalSlots)
        return existing.copy(trackedPrs = merged)
    }

    /**
     * Resolves an [AvailableBlock] matching [sessionBlock] by `routineId` first, then case-insensitive title.
     */
    fun resolveMatchingAvailableBlock(
        sessionBlock: SessionBlock,
        availableBlocks: List<AvailableBlock>
    ): AvailableBlock? {
        val routineId = sessionBlock.routineId
        if (routineId != null && routineId > 0L) {
            availableBlocks.firstOrNull { it.id == routineId }?.let { return it }
        }
        val sub = sessionBlock.subBlock.trim()
        if (sub.isNotEmpty()) {
            availableBlocks.firstOrNull { it.title.equals(sub, ignoreCase = true) }?.let { return it }
        }
        val name = sessionBlock.name.trim()
        if (name.isNotEmpty()) {
            availableBlocks.firstOrNull { it.title.equals(name, ignoreCase = true) }?.let { return it }
        }
        return null
    }

    private fun isResidualLegacyBlock(
        bws: BlockWithSets,
        matchedBlock: AvailableBlock?
    ): Boolean {
        if (matchedBlock != null) return false
        val blk = bws.block
        if (blk.resultText.isNotBlank() || blk.resultValue != null) return true
        val hasPositiveWeight = bws.sets.any { (it.weight ?: 0.0) > 0.0 }
        val hasNonWeightMetricOnly = bws.sets.any { (it.metricValue ?: 0.0) > 0.0 && (it.weight ?: 0.0) <= 0.0 }
        if (hasNonWeightMetricOnly) return true
        if (blk.kind == BlockKind.METCON && !hasPositiveWeight) return true
        return false
    }

    private sealed interface HydrationGroupKey {
        data class ByAvailableBlock(val availableBlockId: Long, val ordinalRun: Int) : HydrationGroupKey
        data class BySubBlock(val normalizedTitle: String, val ordinalRun: Int) : HydrationGroupKey
        data class Standalone(val blockIndex: Int) : HydrationGroupKey
    }

    /**
     * Partitions a persisted [SessionWithBlocks] into simplified block editor cards and
     * any residual legacy metcon/score blocks (`SPEC-BLOCK-LOG-EDIT-01`).
     */
    fun hydrateSessionBlocksForEditor(
        session: SessionWithBlocks,
        availableBlocks: List<AvailableBlock>,
        exercises: List<Exercise>,
        sessionRepMaxes: List<RepMax>
    ): SessionBlockHydrationResult {
        val exercisesById = exercises.associateBy { it.id }
        val exercisesByLowerName = exercises.associateBy { it.name.trim().lowercase() }
        val orderedBlocks = session.blocks.sortedBy { it.block.position }

        val residualLegacy = mutableListOf<BlockWithSets>()
        val simplifiedGroups = mutableListOf<Triple<HydrationGroupKey, AvailableBlock?, MutableList<BlockWithSets>>>()
        var currentRun = 0
        var lastSignature: String? = null

        orderedBlocks.forEachIndexed { idx, bws ->
            val matchedBlock = resolveMatchingAvailableBlock(bws.block, availableBlocks)
            if (isResidualLegacyBlock(bws, matchedBlock)) {
                residualLegacy += bws
                lastSignature = null
                return@forEachIndexed
            }

            val rawSub = bws.block.subBlock.trim()
            val signature = when {
                matchedBlock != null -> "ab:${matchedBlock.id}"
                rawSub.isNotEmpty() -> "sub:${rawSub.lowercase()}"
                else -> "single:$idx"
            }
            if (signature != lastSignature || signature.startsWith("single:")) {
                currentRun += 1
                lastSignature = signature
                val key = when {
                    matchedBlock != null -> HydrationGroupKey.ByAvailableBlock(matchedBlock.id, currentRun)
                    rawSub.isNotEmpty() -> HydrationGroupKey.BySubBlock(rawSub.lowercase(), currentRun)
                    else -> HydrationGroupKey.Standalone(idx)
                }
                simplifiedGroups += Triple(key, matchedBlock, mutableListOf(bws))
            } else {
                simplifiedGroups.last().third.add(bws)
            }
        }

        val allSessionBlockIds = orderedBlocks.map { it.block.id }.filter { it > 0L }.toSet()
        val claimedRepMaxIds = mutableSetOf<Long>()

        val hydratedBlocks = simplifiedGroups.map { (_, matchedBlock, groupBlocks) ->
            val firstBlock = groupBlocks.first().block
            val blockTitle = matchedBlock?.title
                ?.takeIf { it.isNotBlank() }
                ?: firstBlock.subBlock.trim().ifBlank { firstBlock.name.trim() }
                    .ifBlank { "Block" }

            val rawDesc = firstBlock.description.ifBlank { RoutineTargetCodec.extractCleanNotes(firstBlock.notes) }
            val cleanNotes = extractCleanInstructions(rawDesc)
                .ifBlank { matchedBlock?.instructions.orEmpty() }

            val exerciseSeeds = mutableListOf<HydratedBlockExerciseSeed>()
            groupBlocks.forEach { bws ->
                val blk = bws.block
                val orderedSets = bws.sets.sortedBy { it.position }
                val parsedCsvIds = RoutineTargetCodec.parseExerciseIdsPreservingOrder(blk.exerciseIdsCsv)

                val weights = if (orderedSets.isNotEmpty()) {
                    orderedSets.map { it.weight?.takeIf { w -> w > 0.0 } ?: 0.0 }
                } else {
                    listOf(0.0)
                }
                val perSetReps = if (orderedSets.isNotEmpty()) {
                    orderedSets.map { it.reps.coerceAtLeast(1) }
                } else {
                    listOf(1)
                }
                val defaultReps = orderedSets.firstOrNull { it.reps > 0 }?.reps ?: 1

                if (orderedSets.isEmpty() && parsedCsvIds.size > 1) {
                    parsedCsvIds.forEachIndexed { csvIdx, exId ->
                        val exName = exercisesById[exId]?.name
                            ?: blk.name.trim().ifBlank { matchedBlock?.title.orEmpty() }
                        exerciseSeeds += HydratedBlockExerciseSeed(
                            sourceBlockId = blk.id.takeIf { it > 0L && csvIdx == 0 },
                            movementId = exId,
                            movementName = exName,
                            weights = listOf(0.0),
                            perSetReps = listOf(1),
                            defaultReps = 1,
                            rawFormat = blk.format,
                            rawScheme = blk.scheme,
                            rawSection = blk.section,
                            rawKind = blk.kind,
                            rawIsCompleted = blk.isCompleted,
                            subBlockTitle = blk.subBlock
                        )
                    }
                } else {
                    var resolvedId = blk.mainExerciseId?.takeIf { it > 0L }
                        ?: parsedCsvIds.firstOrNull()?.takeIf { it > 0L }
                        ?: exercisesByLowerName[blk.name.trim().lowercase()]?.id
                        ?: 0L
                    if (resolvedId <= 0L && matchedBlock != null &&
                        blk.name.trim().equals(matchedBlock.title, ignoreCase = true) &&
                        matchedBlock.exercises.size == 1
                    ) {
                        resolvedId = matchedBlock.exercises.first().id
                    }
                    val resolvedName = (resolvedId.takeIf { it > 0L }?.let { exercisesById[it]?.name })
                        ?: blk.name.trim().ifBlank { matchedBlock?.title.orEmpty() }

                    exerciseSeeds += HydratedBlockExerciseSeed(
                        sourceBlockId = blk.id.takeIf { it > 0L },
                        movementId = resolvedId,
                        movementName = resolvedName.ifBlank { blockTitle },
                        weights = weights,
                        perSetReps = perSetReps,
                        defaultReps = defaultReps,
                        rawFormat = blk.format,
                        rawScheme = blk.scheme,
                        rawSection = blk.section,
                        rawKind = blk.kind,
                        rawIsCompleted = blk.isCompleted,
                        subBlockTitle = blk.subBlock
                    )
                }
            }

            val sourceIds = groupBlocks.map { it.block.id }.filter { it > 0L }.toSet()
            val movementIds = exerciseSeeds.map { it.movementId }.filter { it > 0L }.toSet()

            val persistedRms = sessionRepMaxes.filter { rm ->
                if (rm.id > 0L && rm.id in claimedRepMaxIds) return@filter false
                val matchesByBlock = rm.blockId != null && rm.blockId in sourceIds
                val isUnboundToSessionBlocks = rm.blockId == null || rm.blockId !in allSessionBlockIds
                val matchesByMovement = isUnboundToSessionBlocks && rm.exerciseId in movementIds
                matchesByBlock || matchesByMovement
            }.also { matched ->
                matched.forEach { if (it.id > 0L) claimedRepMaxIds += it.id }
            }

            HydratedAvailableBlockSeed(
                resolvedAvailableBlock = matchedBlock,
                blockTitle = blockTitle,
                cleanNotes = cleanNotes,
                sourceBlockIds = sourceIds.toList(),
                rawRoutineId = firstBlock.routineId,
                rawFormat = firstBlock.format,
                rawScheme = firstBlock.scheme,
                rawSection = firstBlock.section,
                rawKind = firstBlock.kind,
                rawIsCompleted = firstBlock.isCompleted,
                exercises = exerciseSeeds,
                persistedRepMaxes = persistedRms
            )
        }

        return SessionBlockHydrationResult(
            simplifiedBlocks = hydratedBlocks,
            residualLegacyBlocks = residualLegacy
        )
    }
}
