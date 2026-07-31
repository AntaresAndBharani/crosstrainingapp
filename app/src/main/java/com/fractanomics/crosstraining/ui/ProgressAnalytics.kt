package com.fractanomics.crosstraining.ui

import com.fractanomics.crosstraining.data.model.BlockSet
import com.fractanomics.crosstraining.data.model.Routine
import com.fractanomics.crosstraining.data.model.SessionBlock
import com.fractanomics.crosstraining.data.model.SessionWithBlocks
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
    private val values: List<Double> = workingSets.mapNotNull { it.weight ?: it.metricValue }

    /** Heaviest working set (or best metric value). */
    val top: Double? = values.maxOrNull()

    /** Mean over working sets. */
    val average: Double? = values.takeIf { it.isNotEmpty() }?.average()

    /** Total load moved (Σ weight × reps) over working sets; null for unloaded blocks. */
    val volume: Double? = workingSets
        .mapNotNull { s -> s.weight?.let { it * s.reps } }
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
            (routine != null && routine.mainExerciseId != null && b.mainExerciseId == routine.mainExerciseId) ||
            (routine != null && routine.name.isNotBlank() && (b.name.equals(routine.name, ignoreCase = true)))
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
            val values = blocks.flatMap { b ->
                b.workingSets.mapNotNull { it.weight ?: it.metricValue }
            }
            if (values.isEmpty()) return@mapNotNull null
            DayPerformance(
                date = date,
                top = values.max(),
                average = values.average(),
                volume = blocks.mapNotNull { it.volume }.takeIf { it.isNotEmpty() }?.sum()
            )
        }
        .sortedBy { it.date }
