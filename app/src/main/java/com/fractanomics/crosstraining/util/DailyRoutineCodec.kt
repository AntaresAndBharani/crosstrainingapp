package com.fractanomics.crosstraining.util

import com.fractanomics.crosstraining.data.model.BlockKind
import com.fractanomics.crosstraining.data.model.Exercise
import com.fractanomics.crosstraining.data.model.RepMax
import com.fractanomics.crosstraining.data.model.Routine
import com.fractanomics.crosstraining.data.model.RoutineBlock
import com.fractanomics.crosstraining.data.model.RoutineWithBlocks
import com.fractanomics.crosstraining.data.model.SessionBlock
import com.fractanomics.crosstraining.data.model.SessionWithBlocks
import com.fractanomics.crosstraining.ui.BlockPerformance
import com.fractanomics.crosstraining.ui.DayPerformance
import com.fractanomics.crosstraining.ui.byDay
import com.fractanomics.crosstraining.ui.formatShort
import com.fractanomics.crosstraining.ui.trimmed
import java.math.BigDecimal
import java.math.RoundingMode
import java.net.URLDecoder
import java.net.URLEncoder
import java.nio.charset.StandardCharsets

/**
 * Persisted `Routine.defaultFormat` discriminator identifying a composite Daily Routine
 * (`SPEC-025`) composed of ordered [AvailableBlock] references and standalone [Exercise] items.
 */
const val DAILY_ROUTINE_FORMAT = "DAILY_ROUTINE"
const val DAILY_ROUTINE_BLOCK_ITEM_FORMAT = "DR_BLOCK"
const val DAILY_ROUTINE_EXERCISE_ITEM_FORMAT = "DR_EXERCISE"
const val STANDALONE_EXERCISE_SESSION_FORMAT = "STANDALONE_EXERCISE"

enum class DailyRoutineItemType {
    BLOCK,
    EXERCISE
}

enum class ConstituentTrendDirection {
    UP,
    FLAT,
    DOWN,
    NONE
}

sealed class DailyRoutineItem {
    abstract val itemType: DailyRoutineItemType

    data class BlockRef(
        val blockId: Long,
        val blockTitle: String,
        val resolvedBlock: AvailableBlock? = null
    ) : DailyRoutineItem() {
        override val itemType: DailyRoutineItemType = DailyRoutineItemType.BLOCK
    }

    data class StandaloneExercise(
        val exerciseId: Long,
        val exerciseName: String,
        val note: String = "",
        val defaultTrackedPrs: List<TrackedPrSlot> = emptyList(),
        val resolvedExercise: Exercise? = null
    ) : DailyRoutineItem() {
        override val itemType: DailyRoutineItemType = DailyRoutineItemType.EXERCISE
    }
}

data class DailyRoutine(
    val id: Long,
    val title: String,
    val notes: String,
    val items: List<DailyRoutineItem>,
    val missingBlockCount: Int = 0,
    val rawRoutine: RoutineWithBlocks? = null
) {
    val blockCount: Int
        get() = items.count { it is DailyRoutineItem.BlockRef }

    val exerciseCount: Int
        get() = items.count { it is DailyRoutineItem.StandaloneExercise }

    val totalTrackedPrCount: Int
        get() {
            val allSlots = items.flatMap { item ->
                when (item) {
                    is DailyRoutineItem.BlockRef -> item.resolvedBlock?.trackedPrs.orEmpty()
                    is DailyRoutineItem.StandaloneExercise -> item.defaultTrackedPrs
                }
            }
            return AvailableBlockCodec.normalizeTrackedPrSlots(allSlots).size
        }
}

data class LastExerciseSessionSummary(
    val dateIso: String,
    val dateFormatted: String,
    val weights: List<Double?>,
    val topWeight: Double?,
    val averageWeight: Double?,
    val setWeightsSummary: String
)

data class ConstituentProgressSummary(
    val itemType: DailyRoutineItemType,
    val key: String,
    val title: String,
    val subtitle: String,
    val allTimeTop: Double?,
    val lastTop: Double?,
    val lastTopDateFormatted: String?,
    val lastAverage: Double?,
    val lastVolume: Double?,
    val topDelta: Double?,
    val trendDirection: ConstituentTrendDirection,
    val lastSetsSummary: String,
    val bestPrBadges: List<String>,
    val unit: String = "kg",
    val trackedPrSlots: List<TrackedPrSlot> = emptyList(),
    val performances: List<BlockPerformance> = emptyList(),
    val dayPerformances: List<DayPerformance> = emptyList()
)

data class DecodedDrItemHeader(
    val itemType: DailyRoutineItemType,
    val refId: Long,
    val refName: String
)

/**
 * Pure deterministic codec, pre-fill resolver, structural diff checker, and progress aggregator
 * for composite Daily Routines (`SPEC-025`).
 */
object DailyRoutineCodec {

    private val DR_ITEM_REGEX = Regex("""\[dr_item=([^\]]*)]""", RegexOption.IGNORE_CASE)

    fun isDailyRoutine(routine: Routine): Boolean =
        routine.defaultFormat.equals(DAILY_ROUTINE_FORMAT, ignoreCase = true)

    fun isDailyRoutine(rwb: RoutineWithBlocks): Boolean =
        isDailyRoutine(rwb.routine)

    /**
     * Namespace-isolated natural key preventing Daily Routines (`dr:`) and Available Blocks (`ab:`)
     * with identical names from colliding or overwriting one another in Room or Cloud Sync.
     */
    fun routineNaturalKey(name: String, defaultFormat: String?): String {
        val prefix = if (defaultFormat.equals(DAILY_ROUTINE_FORMAT, ignoreCase = true)) "dr" else "ab"
        return "$prefix:${name.trim().lowercase()}"
    }

    fun routineNaturalKey(routine: Routine): String =
        routineNaturalKey(routine.name, routine.defaultFormat)

    fun routineNaturalKey(rwb: RoutineWithBlocks): String =
        routineNaturalKey(rwb.routine)

    fun encodeBlockItemHeader(blockId: Long, blockTitle: String): String {
        val encoded = URLEncoder.encode(blockTitle.trim(), StandardCharsets.UTF_8.name())
        return "[dr_item=BLOCK:id=${blockId.coerceAtLeast(0L)}:title=$encoded]"
    }

    fun encodeExerciseItemHeader(exerciseId: Long, exerciseName: String): String {
        val encoded = URLEncoder.encode(exerciseName.trim(), StandardCharsets.UTF_8.name())
        return "[dr_item=EXERCISE:id=${exerciseId.coerceAtLeast(0L)}:name=$encoded]"
    }

    fun decodeItemHeader(notes: String?): DecodedDrItemHeader? {
        if (notes.isNullOrBlank()) return null
        val match = DR_ITEM_REGEX.find(notes) ?: return null
        val payload = match.groupValues.getOrNull(1).orEmpty().trim()
        if (payload.isEmpty()) return null
        val parts = payload.split(":")
        val typeToken = parts.firstOrNull()?.trim()?.uppercase() ?: return null
        val itemType = when (typeToken) {
            "BLOCK" -> DailyRoutineItemType.BLOCK
            "EXERCISE" -> DailyRoutineItemType.EXERCISE
            else -> return null
        }
        var refId = 0L
        var rawName = ""
        parts.drop(1).forEach { part ->
            val eqIdx = part.indexOf('=')
            if (eqIdx <= 0) return@forEach
            val key = part.substring(0, eqIdx).trim().lowercase()
            val value = part.substring(eqIdx + 1).trim()
            when (key) {
                "id" -> refId = value.toLongOrNull()?.coerceAtLeast(0L) ?: 0L
                "title", "name" -> {
                    rawName = runCatching {
                        URLDecoder.decode(value, StandardCharsets.UTF_8.name())
                    }.getOrDefault(value).trim()
                }
            }
        }
        return DecodedDrItemHeader(
            itemType = itemType,
            refId = refId,
            refName = rawName
        )
    }

    fun toRoutineWithBlocks(routine: DailyRoutine): RoutineWithBlocks {
        val cleanTitle = routine.title.trim().ifEmpty { "Daily Routine" }
        val cleanNotes = RoutineTargetCodec.extractCleanNotes(routine.notes)
        val firstExId = routine.items.firstNotNullOfOrNull { item ->
            when (item) {
                is DailyRoutineItem.StandaloneExercise -> item.exerciseId.takeIf { it > 0L }
                is DailyRoutineItem.BlockRef -> item.resolvedBlock?.exercises?.firstOrNull()?.id?.takeIf { it > 0L }
            }
        }
        val persistedRoutine = Routine(
            id = routine.id,
            name = cleanTitle,
            mainExerciseId = firstExId,
            description = cleanNotes,
            defaultFormat = DAILY_ROUTINE_FORMAT
        )
        val blocks = routine.items.mapIndexed { idx, item ->
            when (item) {
                is DailyRoutineItem.BlockRef -> {
                    val cleanBlockTitle = item.blockTitle.trim().ifEmpty { item.resolvedBlock?.title ?: "Block" }
                    val resolvedId = item.blockId.takeIf { it > 0L } ?: item.resolvedBlock?.id ?: 0L
                    val csv = item.resolvedBlock?.exercises
                        ?.map { it.id }
                        ?.filter { it > 0L }
                        ?.distinct()
                        ?.joinToString(",")
                        .orEmpty()
                    RoutineBlock(
                        routineId = routine.id,
                        position = idx,
                        name = cleanBlockTitle,
                        kind = BlockKind.COMPLEX,
                        format = DAILY_ROUTINE_BLOCK_ITEM_FORMAT,
                        setsCount = 1,
                        targetRepsScheme = "",
                        exerciseIdsCsv = csv,
                        notes = encodeBlockItemHeader(resolvedId, cleanBlockTitle),
                        section = "",
                        subBlock = cleanBlockTitle
                    )
                }

                is DailyRoutineItem.StandaloneExercise -> {
                    val cleanExName = item.exerciseName.trim().ifEmpty { item.resolvedExercise?.name ?: "Exercise" }
                    val resolvedExId = item.exerciseId.takeIf { it > 0L } ?: item.resolvedExercise?.id ?: 0L
                    val drHeader = encodeExerciseItemHeader(resolvedExId, cleanExName)
                    val noteAndPrs = AvailableBlockCodec.encodeNotesWithTrackedPrs(
                        cleanInstructions = item.note,
                        trackedPrs = item.defaultTrackedPrs
                    )
                    val fullNotes = if (noteAndPrs.isBlank()) drHeader else "$drHeader\n$noteAndPrs"
                    RoutineBlock(
                        routineId = routine.id,
                        position = idx,
                        name = cleanExName,
                        kind = BlockKind.STRENGTH,
                        format = DAILY_ROUTINE_EXERCISE_ITEM_FORMAT,
                        setsCount = 1,
                        targetRepsScheme = "",
                        exerciseIdsCsv = if (resolvedExId > 0L) resolvedExId.toString() else "",
                        notes = fullNotes,
                        section = "",
                        subBlock = ""
                    )
                }
            }
        }
        return RoutineWithBlocks(routine = persistedRoutine, blocks = blocks)
    }

    fun fromRoutineWithBlocks(
        rwb: RoutineWithBlocks,
        availableBlocks: List<AvailableBlock>,
        exercises: List<Exercise>
    ): DailyRoutine {
        val blocksById = availableBlocks.associateBy { it.id }
        val blocksByLowerTitle = availableBlocks.associateBy { it.title.trim().lowercase() }
        val exercisesById = exercises.associateBy { it.id }
        val exercisesByLowerName = exercises.associateBy { it.name.trim().lowercase() }

        val activeItems = mutableListOf<DailyRoutineItem>()
        var missingBlocks = 0

        rwb.blocks.sortedBy { it.position }.forEach { rb ->
            val decodedHeader = decodeItemHeader(rb.notes)
            val effectiveType = decodedHeader?.itemType ?: when {
                rb.format.equals(DAILY_ROUTINE_BLOCK_ITEM_FORMAT, ignoreCase = true) -> DailyRoutineItemType.BLOCK
                rb.format.equals(DAILY_ROUTINE_EXERCISE_ITEM_FORMAT, ignoreCase = true) -> DailyRoutineItemType.EXERCISE
                else -> DailyRoutineItemType.EXERCISE
            }

            when (effectiveType) {
                DailyRoutineItemType.BLOCK -> {
                    val refId = decodedHeader?.refId ?: 0L
                    val refTitle = decodedHeader?.refName?.ifBlank { rb.name.trim() } ?: rb.name.trim()
                    val matchedById = refId.takeIf { it > 0L }?.let { blocksById[it] }
                    val resolvedBlock = when {
                        matchedById != null && (refTitle.isBlank() || matchedById.title.equals(refTitle, ignoreCase = true)) ->
                            matchedById
                        refTitle.isNotBlank() -> blocksByLowerTitle[refTitle.lowercase()] ?: matchedById
                        else -> matchedById
                    }
                    if (resolvedBlock != null) {
                        activeItems += DailyRoutineItem.BlockRef(
                            blockId = resolvedBlock.id,
                            blockTitle = resolvedBlock.title,
                            resolvedBlock = resolvedBlock
                        )
                    } else {
                        missingBlocks += 1
                    }
                }

                DailyRoutineItemType.EXERCISE -> {
                    val csvFirstId = RoutineTargetCodec.parseExerciseIdsPreservingOrder(rb.exerciseIdsCsv).firstOrNull()
                    val refId = decodedHeader?.refId?.takeIf { it > 0L } ?: csvFirstId ?: 0L
                    val refName = decodedHeader?.refName?.ifBlank { rb.name.trim() } ?: rb.name.trim()
                    val matchedById = refId.takeIf { it > 0L }?.let { exercisesById[it] }
                    val resolvedExercise = when {
                        matchedById != null && (refName.isBlank() || matchedById.name.equals(refName, ignoreCase = true)) ->
                            matchedById
                        refName.isNotBlank() -> exercisesByLowerName[refName.lowercase()] ?: matchedById
                        else -> matchedById
                    }
                    val finalExId = resolvedExercise?.id ?: refId
                    val finalExName = resolvedExercise?.name ?: refName.ifBlank { "Exercise" }
                    val cleanNote = RoutineTargetCodec.extractCleanNotes(rb.notes)
                    val trackedPrs = AvailableBlockCodec.decodeTrackedPrHeader(
                        notes = rb.notes,
                        exercisesById = exercisesById,
                        exercisesByLowerName = exercisesByLowerName
                    )
                    activeItems += DailyRoutineItem.StandaloneExercise(
                        exerciseId = finalExId,
                        exerciseName = finalExName,
                        note = cleanNote,
                        defaultTrackedPrs = trackedPrs,
                        resolvedExercise = resolvedExercise
                    )
                }
            }
        }

        return DailyRoutine(
            id = rwb.routine.id,
            title = rwb.routine.name.trim().ifEmpty { "Untitled Routine" },
            notes = RoutineTargetCodec.extractCleanNotes(rwb.routine.description),
            items = activeItems,
            missingBlockCount = missingBlocks,
            rawRoutine = rwb
        )
    }

    fun filterDailyRoutines(routines: List<DailyRoutine>, query: String): List<DailyRoutine> {
        val needle = query.trim().lowercase()
        if (needle.isEmpty()) return routines
        return routines.filter { routine ->
            routine.title.lowercase().contains(needle) ||
                routine.notes.lowercase().contains(needle) ||
                routine.items.any { item ->
                    when (item) {
                        is DailyRoutineItem.BlockRef -> {
                            item.blockTitle.lowercase().contains(needle) ||
                                item.resolvedBlock?.exercises?.any { it.name.lowercase().contains(needle) } == true
                        }
                        is DailyRoutineItem.StandaloneExercise -> {
                            item.exerciseName.lowercase().contains(needle) ||
                                item.note.lowercase().contains(needle)
                        }
                    }
                }
        }
    }

    fun resolveLastDailyRoutineSessionDate(
        routine: DailyRoutine,
        sessions: List<SessionWithBlocks>
    ): String? {
        val sorted = sessions.sortedWith(
            compareByDescending<SessionWithBlocks> { it.session.date }
                .thenByDescending { it.session.id }
        )
        val routineTitleLower = routine.title.trim().lowercase()
        val match = sorted.firstOrNull { sw ->
            val titleMatch = routineTitleLower.isNotEmpty() &&
                sw.session.title.trim().lowercase() == routineTitleLower
            val routineIdMatch = routine.id > 0L &&
                sw.blocks.any { it.block.routineId == routine.id }
            titleMatch || routineIdMatch
        }
        return match?.session?.date?.formatShort()
    }

    fun matchesStandaloneOrSingleExercise(
        sessionBlock: SessionBlock,
        exercise: Exercise
    ): Boolean {
        if (exercise.id > 0L && sessionBlock.mainExerciseId == exercise.id) return true
        val parsedCsvIds = RoutineTargetCodec.parseExerciseIdsPreservingOrder(sessionBlock.exerciseIdsCsv)
        if (exercise.id > 0L && sessionBlock.mainExerciseId == null && parsedCsvIds.singleOrNull() == exercise.id) {
            return true
        }
        val cleanBlockName = sessionBlock.name.trim()
        val cleanExerciseName = exercise.name.trim()
        if (cleanBlockName.isNotEmpty() && cleanBlockName.equals(cleanExerciseName, ignoreCase = true)) {
            if (sessionBlock.mainExerciseId == null || (exercise.id > 0L && sessionBlock.mainExerciseId == exercise.id)) {
                return true
            }
        }
        return false
    }

    fun resolveLastExerciseSession(
        exercise: Exercise,
        sessions: List<SessionWithBlocks>,
        weightUnit: String = "kg",
        excludeSessionId: Long? = null
    ): LastExerciseSessionSummary? {
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
                .filter { bws -> matchesStandaloneOrSingleExercise(bws.block, exercise) }
                .sortedBy { it.block.position }
            if (matching.isEmpty()) continue

            val candidateBlock = matching.firstOrNull { bws ->
                bws.sets.any { !it.isWarmup && !it.isFailed && (it.weight ?: 0.0) > 0.0 }
            } ?: matching.firstOrNull { it.sets.isNotEmpty() } ?: continue

            val orderedSets = candidateBlock.sets.sortedBy { it.position }
            val workingSets = orderedSets.filter { !it.isWarmup && !it.isFailed }.ifEmpty { orderedSets }
            val weights = workingSets.map { s -> s.weight?.takeIf { it > 0.0 } }
            val positiveWeights = weights.filterNotNull()
            val topWeight = positiveWeights.maxOrNull()
            val averageWeight = positiveWeights.takeIf { it.isNotEmpty() }?.average()
            val summaryText = if (positiveWeights.isNotEmpty()) {
                positiveWeights.take(4).joinToString(" · ") { it.trimmed() } + " $weightUnit"
            } else {
                "Completed ${sw.session.date.formatShort()}"
            }

            return LastExerciseSessionSummary(
                dateIso = sw.session.date.toString(),
                dateFormatted = sw.session.date.formatShort(),
                weights = weights,
                topWeight = topWeight,
                averageWeight = averageWeight,
                setWeightsSummary = summaryText
            )
        }
        return null
    }

    fun resolveDefaultTrackedPrSlotsForExercise(
        exercise: Exercise,
        defaultTrackedPrs: List<TrackedPrSlot>,
        repMaxes: List<RepMax>
    ): List<TrackedPrSlot> {
        val fromHistory = if (exercise.id > 0L) {
            repMaxes
                .filter { it.exerciseId == exercise.id && it.reps in 1..20 && it.weight > 0.0 }
                .map { it.reps }
                .distinct()
                .sorted()
                .map { rm -> TrackedPrSlot(exercise.id, exercise.name, rm) }
        } else {
            emptyList()
        }
        val combined = AvailableBlockCodec.normalizeTrackedPrSlots(defaultTrackedPrs + fromHistory)
        if (combined.isNotEmpty()) return combined
        return if (exercise.tracksRepMax) {
            listOf(TrackedPrSlot(exercise.id, exercise.name, 1))
        } else {
            emptyList()
        }
    }

    /**
     * Generates a structural signature representing the ordered Blocks and standalone Exercises
     * currently in a session draft vs a loaded [DailyRoutine], used to trigger the optional
     * `[ ] Also update "{Routine}" routine template` prompt (`SPEC-ROUTINE-SESSION-TRACK-01`).
     */
    fun routineItemsSignature(items: List<DailyRoutineItem>): String =
        items.joinToString("|") { item ->
            when (item) {
                is DailyRoutineItem.BlockRef ->
                    "B:${if (item.blockId > 0L) item.blockId.toString() else item.blockTitle.trim().lowercase()}"
                is DailyRoutineItem.StandaloneExercise ->
                    "E:${if (item.exerciseId > 0L) item.exerciseId.toString() else item.exerciseName.trim().lowercase()}"
            }
        }

    fun computeDailyRoutineProgress(
        routine: DailyRoutine,
        sessions: List<SessionWithBlocks>,
        repMaxes: List<RepMax>,
        exercises: List<Exercise>,
        weightUnit: String = "kg"
    ): List<ConstituentProgressSummary> {
        val exercisesById = exercises.associateBy { it.id }
        val exercisesByLowerName = exercises.associateBy { it.name.trim().lowercase() }
        val unit = weightUnit.ifBlank { "kg" }

        return routine.items.mapIndexed { idx, item ->
            when (item) {
                is DailyRoutineItem.BlockRef -> {
                    val block = item.resolvedBlock ?: AvailableBlock(
                        id = item.blockId,
                        title = item.blockTitle,
                        instructions = "",
                        exercises = emptyList(),
                        trackedPrs = emptyList()
                    )
                    val performances = sessions.flatMap { sw ->
                        sw.blocks
                            .filter { bws ->
                                !bws.block.format.equals(STANDALONE_EXERCISE_SESSION_FORMAT, ignoreCase = true) &&
                                    AvailableBlockCodec.matchesAvailableBlock(bws.block, block)
                            }
                            .map { bws ->
                                BlockPerformance(
                                    date = sw.session.date,
                                    sessionId = sw.session.id,
                                    sessionTitle = sw.session.title,
                                    block = bws.block,
                                    sets = bws.sets.sortedBy { it.position }
                                )
                            }
                    }.sortedWith(compareBy({ it.date }, { it.sessionId }, { it.block.position }))

                    val dayPerfs = performances.byDay()
                    val lastBlockSummary = AvailableBlockCodec.resolveLastBlockSession(
                        block = block,
                        sessions = sessions,
                        exercises = exercises,
                        weightUnit = unit
                    )
                    val targetExIds = (
                        block.exercises.map { it.id } +
                            block.trackedPrs.map { it.movementId } +
                            performances.mapNotNull { it.block.mainExerciseId }
                        ).filter { it > 0L }.toSet()

                    buildConstituentSummary(
                        itemType = DailyRoutineItemType.BLOCK,
                        key = "${idx}_block_${block.id}",
                        title = block.title,
                        subtitle = block.instructions,
                        performances = performances,
                        dayPerfs = dayPerfs,
                        lastSetsSummary = lastBlockSummary?.setWeightsSummary.orEmpty(),
                        targetExerciseIds = targetExIds,
                        preferredPrSlots = block.trackedPrs,
                        repMaxes = repMaxes,
                        exercisesById = exercisesById,
                        unit = unit
                    )
                }

                is DailyRoutineItem.StandaloneExercise -> {
                    val ex = item.resolvedExercise
                        ?: exercisesById[item.exerciseId]
                        ?: exercisesByLowerName[item.exerciseName.trim().lowercase()]
                        ?: Exercise(id = item.exerciseId, name = item.exerciseName)
                    val performances = sessions.flatMap { sw ->
                        sw.blocks
                            .filter { bws -> matchesStandaloneOrSingleExercise(bws.block, ex) }
                            .map { bws ->
                                BlockPerformance(
                                    date = sw.session.date,
                                    sessionId = sw.session.id,
                                    sessionTitle = sw.session.title,
                                    block = bws.block,
                                    sets = bws.sets.sortedBy { it.position }
                                )
                            }
                    }.sortedWith(compareBy({ it.date }, { it.sessionId }, { it.block.position }))

                    val dayPerfs = performances.byDay()
                    val lastExSummary = resolveLastExerciseSession(
                        exercise = ex,
                        sessions = sessions,
                        weightUnit = unit
                    )
                    val targetExIds = setOfNotNull(ex.id.takeIf { it > 0L })

                    buildConstituentSummary(
                        itemType = DailyRoutineItemType.EXERCISE,
                        key = "${idx}_ex_${ex.id}_${ex.name.lowercase()}",
                        title = ex.name,
                        subtitle = item.note,
                        performances = performances,
                        dayPerfs = dayPerfs,
                        lastSetsSummary = lastExSummary?.setWeightsSummary.orEmpty(),
                        targetExerciseIds = targetExIds,
                        preferredPrSlots = item.defaultTrackedPrs,
                        repMaxes = repMaxes,
                        exercisesById = exercisesById,
                        unit = unit
                    )
                }
            }
        }
    }

    private fun buildConstituentSummary(
        itemType: DailyRoutineItemType,
        key: String,
        title: String,
        subtitle: String,
        performances: List<BlockPerformance>,
        dayPerfs: List<DayPerformance>,
        lastSetsSummary: String,
        targetExerciseIds: Set<Long>,
        preferredPrSlots: List<TrackedPrSlot>,
        repMaxes: List<RepMax>,
        exercisesById: Map<Long, Exercise>,
        unit: String
    ): ConstituentProgressSummary {
        val allTimeTop = performances.mapNotNull { it.top }.maxOrNull()
        val latestDay = dayPerfs.lastOrNull()
        val prevDay = dayPerfs.getOrNull(dayPerfs.size - 2)
        val rawDelta = if (latestDay != null && prevDay != null) latestDay.top - prevDay.top else null
        val roundedDelta = rawDelta?.let {
            BigDecimal.valueOf(it).setScale(2, RoundingMode.HALF_UP).toDouble()
        }
        val trendDirection = when {
            roundedDelta == null -> ConstituentTrendDirection.NONE
            roundedDelta > 0.0 -> ConstituentTrendDirection.UP
            roundedDelta < 0.0 -> ConstituentTrendDirection.DOWN
            else -> ConstituentTrendDirection.FLAT
        }

        val matchingRms = repMaxes.filter { it.exerciseId in targetExerciseIds && it.weight > 0.0 }
        val bestByKey = matchingRms
            .groupBy { it.exerciseId to it.reps }
            .mapValues { (_, list) -> list.maxBy { it.weight } }
        val preferredOrder = preferredPrSlots.mapNotNull { slot ->
            val rm = bestByKey[slot.movementId to slot.rmReps]
            rm?.let { (slot.movementId to slot.rmReps) to it }
        }
        val remainingOrder = bestByKey.entries
            .filter { (pair, _) -> preferredOrder.none { it.first == pair } }
            .sortedWith(compareBy<Map.Entry<Pair<Long, Int>, RepMax>> { it.value.reps }.thenByDescending { it.value.weight })
            .map { it.key to it.value }

        val prBadges = (preferredOrder + remainingOrder)
            .take(3)
            .map { (_, rm) ->
                val exName = exercisesById[rm.exerciseId]?.name ?: title
                "🔥 ${rm.reps}RM $exName: ${rm.weight.trimmed()} $unit"
            }

        val resolvedLastSets = if (lastSetsSummary.isNotBlank()) {
            lastSetsSummary
        } else if (latestDay != null) {
            val lastDayWeights = performances
                .filter { it.date == latestDay.date }
                .flatMap { it.workingSets }
                .mapNotNull { it.weight ?: it.metricValue }
                .filter { it > 0.0 }
            if (lastDayWeights.isNotEmpty()) {
                lastDayWeights.take(4).joinToString(" · ") { it.trimmed() } + " $unit"
            } else {
                "Logged ${latestDay.date.formatShort()}"
            }
        } else {
            ""
        }

        return ConstituentProgressSummary(
            itemType = itemType,
            key = key,
            title = title,
            subtitle = subtitle,
            allTimeTop = allTimeTop,
            lastTop = latestDay?.top,
            lastTopDateFormatted = latestDay?.date?.formatShort(),
            lastAverage = latestDay?.average,
            lastVolume = latestDay?.volume,
            topDelta = rawDelta,
            trendDirection = trendDirection,
            lastSetsSummary = resolvedLastSets,
            bestPrBadges = prBadges,
            unit = unit,
            trackedPrSlots = preferredPrSlots,
            performances = performances,
            dayPerformances = dayPerfs
        )
    }
}
