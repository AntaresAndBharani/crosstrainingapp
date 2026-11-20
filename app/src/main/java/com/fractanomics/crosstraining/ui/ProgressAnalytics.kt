package com.fractanomics.crosstraining.ui

import com.fractanomics.crosstraining.data.model.BlockSet
import com.fractanomics.crosstraining.data.model.Exercise
import com.fractanomics.crosstraining.data.model.RepMax
import com.fractanomics.crosstraining.data.model.Routine
import com.fractanomics.crosstraining.data.model.SessionBlock
import com.fractanomics.crosstraining.data.model.SessionWithBlocks
import com.fractanomics.crosstraining.util.ExerciseTargetMetric
import com.fractanomics.crosstraining.util.RoutineTargetCodec
import java.time.LocalDate

/**
 * KPIs for one occurrence of a block that targets a given exercise: the
 * weight of every set plus the derived average, top and moved volume
 * (tonnage). Warm-up and failed sets are excluded from the numbers but kept
 * in [sets] so the UI can still display them.
 */
data class BlockPerformance(
    val date: LocalDate,
    val sessionId: Long,
    val sessionTitle: String,
    val block: SessionBlock,
    val sets: List<BlockSet>
) {
    val workingSets: List<BlockSet> = sets.filter { !it.isWarmup && !it.isFailed }
    val workingSetValues: List<Double> = run {
        val primary = workingSets.mapNotNull { s ->
            s.weight?.takeIf { it > 0.0 } ?: s.metricValue?.takeIf { it > 0.0 }
        }
        if (primary.isNotEmpty()) {
            primary
        } else {
            val explicitRepsMetric = RoutineTargetCodec.decodeLogMetricHeader(block.notes) == ExerciseTargetMetric.REPS
            val isSingleBlankPlaceholder = workingSets.size == 1 && workingSets[0].reps <= 1 && !explicitRepsMetric
            if (isSingleBlankPlaceholder) emptyList()
            else workingSets.filter { it.reps > 0 }.map { it.reps.toDouble() }
        }
    }

    /** Heaviest working set (or best metric/rep value). */
    val top: Double? = workingSetValues.maxOrNull()

    /** Mean over working sets. */
    val average: Double? = workingSetValues.takeIf { it.isNotEmpty() }?.average()

    /** Total load moved (Σ weight × reps) over working sets; null for unloaded blocks. */
    val volume: Double? = workingSets
        .mapNotNull { s -> s.weight?.takeIf { it > 0.0 }?.let { it * s.reps } }
        .takeIf { it.isNotEmpty() }
        ?.sum()
}

/** One training day, aggregated over every (filtered) block on that date. */
data class DayPerformance(
    val date: LocalDate,
    val top: Double,
    val average: Double,
    val volume: Double?
)

/** All block occurrences targeting [exerciseId], oldest first. */
fun List<SessionWithBlocks>.blockPerformances(exerciseId: Long): List<BlockPerformance> =
    performances { it.mainExerciseId == exerciseId }

/** All block occurrences performing routine [routineId] or matching routine [routine], oldest first. */
fun List<SessionWithBlocks>.routineBlockPerformances(routineId: Long, routine: Routine? = null): List<BlockPerformance> =
    performances { b ->
        b.routineId == routineId ||
            (routine != null && routine.name.isNotBlank() && (
                b.subBlock.equals(routine.name, ignoreCase = true) ||
                    b.name.equals(routine.name, ignoreCase = true)
                )) ||
            (routine != null && routine.mainExerciseId != null &&
                b.routineId == null && b.subBlock.isBlank() &&
                b.mainExerciseId == routine.mainExerciseId)
    }

/** Attribution metadata connecting a [RepMax] record to the block and session where it was achieved. */
data class RepMaxAttribution(
    val blockTitle: String?,
    val formattedLabel: String
)

/**
 * Resolves a human-readable attribution label for [repMax], e.g.
 * `"Achieved in Clean Complex · 14 Nov"` when linked to a logged block/session or `"14 Nov"` otherwise.
 */
fun resolveRepMaxAttribution(
    repMax: RepMax,
    sessions: List<SessionWithBlocks>,
    routines: List<Routine>,
    exerciseName: String = ""
): RepMaxAttribution {
    val dateLabel = repMax.date.formatShort()
    val linkedSession = repMax.sessionId?.let { sid ->
        sessions.firstOrNull { it.session.id == sid }
    }
    val linkedBlock = repMax.blockId?.let { bid ->
        linkedSession?.blocks?.firstOrNull { it.block.id == bid }?.block
            ?: sessions.asSequence().flatMap { it.blocks.asSequence() }.firstOrNull { it.block.id == bid }?.block
    } ?: linkedSession?.blocks?.firstOrNull { bws ->
        bws.block.mainExerciseId == repMax.exerciseId ||
            RoutineTargetCodec.parseExerciseIdsPreservingOrder(bws.block.exerciseIdsCsv).contains(repMax.exerciseId)
    }?.block

    val blockTitle = linkedBlock?.let { sb ->
        sb.subBlock.trim().takeIf { it.isNotEmpty() }
            ?: sb.routineId?.let { rid -> routines.firstOrNull { it.id == rid }?.name?.trim() }?.takeIf { it.isNotEmpty() }
            ?: sb.name.trim().takeIf { it.isNotEmpty() && !it.equals(exerciseName.trim(), ignoreCase = true) }
    } ?: linkedSession?.session?.title?.trim()?.takeIf { it.isNotEmpty() }

    val formattedLabel = if (!blockTitle.isNullOrBlank()) {
        "Achieved in $blockTitle · $dateLabel"
    } else {
        dateLabel
    }
    return RepMaxAttribution(blockTitle = blockTitle, formattedLabel = formattedLabel)
}

private fun List<SessionWithBlocks>.performances(
    match: (SessionBlock) -> Boolean
): List<BlockPerformance> =
    flatMap { sw ->
        sw.blocks
            .filter { match(it.block) }
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

/** Collapse block performances into one point per training day, oldest first. */
fun List<BlockPerformance>.byDay(): List<DayPerformance> =
    groupBy { it.date }
        .mapNotNull { (date, blocks) ->
            val values = blocks.flatMap { it.workingSetValues }
            if (values.isEmpty()) return@mapNotNull null
            DayPerformance(
                date = date,
                top = values.max(),
                average = values.average(),
                volume = blocks.mapNotNull { it.volume }.takeIf { it.isNotEmpty() }?.sum()
            )
        }
        .sortedBy { it.date }

/** Formats a [BlockSet] badge token for History and Progress drill-down tables. */
internal fun formatBlockSetToken(
    set: BlockSet,
    blockNotes: String? = null,
    exercise: Exercise? = null
): String {
    val w = set.weight?.takeIf { it > 0.0 }
    val m = set.metricValue?.takeIf { it > 0.0 }
    val core = when {
        w != null -> "${w.trimmed()}×${set.reps}"
        m != null -> {
            val resolved = RoutineTargetCodec.resolveSessionBlockMetric(blockNotes, listOf(set), exercise)
            val unitSuffix = when (resolved) {
                ExerciseTargetMetric.CALORIES -> " cal"
                ExerciseTargetMetric.TIME_SECONDS -> "s"
                ExerciseTargetMetric.METERS -> "m"
                ExerciseTargetMetric.REPS -> ""
            }
            "${m.trimmed()}$unitSuffix"
        }
        else -> "${set.reps} reps"
    }
    val prefix = if (set.isWarmup) "wu " else ""
    val suffix = if (set.isFailed) " ✗" else ""
    return "$prefix$core$suffix"
}
