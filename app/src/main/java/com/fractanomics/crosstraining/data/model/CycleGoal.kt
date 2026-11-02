package com.fractanomics.crosstraining.data.model

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Ignore
import androidx.room.Index
import androidx.room.PrimaryKey
import com.fractanomics.crosstraining.ui.trimmed

/**
 * A target goal for a basic movement within a training cycle
 * (e.g. 1RM Snatch goal: 90kg -> 100kg during an 8-week cycle,
 * Air Bike 5 min calorie goal: 40 -> 50 cal, or Pull-ups Open reps goal: 12 -> 17 reps).
 */
@Entity(
    tableName = "cycle_goals",
    foreignKeys = [
        ForeignKey(
            entity = Cycle::class,
            parentColumns = ["id"],
            childColumns = ["cycleId"],
            onDelete = ForeignKey.CASCADE
        ),
        ForeignKey(
            entity = Exercise::class,
            parentColumns = ["id"],
            childColumns = ["exerciseId"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [
        Index("cycleId"),
        Index("exerciseId")
    ]
)
data class CycleGoal(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val cycleId: Long = 0,
    val exerciseId: Long = 0,
    val targetReps: Int = 1,
    val startWeight: Double = 0.0,
    val targetWeight: Double = 0.0,
    val notes: String = ""
) {
    /**
     * Secondary constructor for creating adaptive metric goals (`Weight`, `Calories`, `Reps`)
     * with an optional time window (`1`, `3`, `5` minutes, or `null` for `Open`).
     */
    @Ignore
    constructor(
        id: Long = 0,
        cycleId: Long = 0,
        exerciseId: Long = 0,
        targetReps: Int = 1,
        startWeight: Double = 0.0,
        targetWeight: Double = 0.0,
        notes: String = "",
        goalMetric: MetricType,
        timeWindowMinutes: Int? = null
    ) : this(
        id = id,
        cycleId = cycleId,
        exerciseId = exerciseId,
        targetReps = if (goalMetric == MetricType.WEIGHT) targetReps.coerceAtLeast(1) else (timeWindowMinutes ?: 0),
        startWeight = startWeight,
        targetWeight = targetWeight,
        notes = encodeMetadata(notes, goalMetric, timeWindowMinutes)
    )

    /**
     * Active goal metric (`WEIGHT`, `CALORIES`, or `REPS`).
     * Defaults to [MetricType.WEIGHT] for legacy/weight-based goals.
     */
    @get:Ignore
    val goalMetric: MetricType
        get() = parseMetadata(notes).first

    /**
     * Contextual time window in minutes (`1`, `3`, `5`), or `null` for `Open`
     * (and `null` when [goalMetric] is [MetricType.WEIGHT]).
     */
    @get:Ignore
    val timeWindowMinutes: Int?
        get() = parseMetadata(notes).second

    /**
     * User-visible notes with any leading adaptive-metric metadata header stripped.
     */
    @get:Ignore
    val cleanNotes: String
        get() = parseMetadata(notes).third

    /**
     * Display unit label corresponding to [goalMetric] (`"kg"`, `"cal"`, or `"reps"`).
     */
    @get:Ignore
    val unitLabel: String
        get() = unitLabelFor(goalMetric)

    /**
     * Contextual badge text (`"<N>RM"` for Weight; `"<N> min"` or `"Open"` for Calories/Reps).
     */
    @get:Ignore
    val contextBadge: String
        get() = when (goalMetric) {
            MetricType.WEIGHT -> "${targetReps.coerceAtLeast(1)}RM"
            else -> formatTimeWindowLabel(timeWindowMinutes)
        }

    /**
     * Formats the summary chip text displayed on [CycleCard].
     */
    fun formatSummaryChip(exerciseName: String, fallbackWeightUnit: String = "kg"): String {
        return when (goalMetric) {
            MetricType.WEIGHT -> {
                val unit = fallbackWeightUnit.ifBlank { "kg" }
                if (startWeight > 0.0) {
                    "$exerciseName (${targetReps}RM): ${startWeight.trimmed()} → ${targetWeight.trimmed()}$unit"
                } else {
                    "$exerciseName (${targetReps}RM): ${targetWeight.trimmed()}$unit"
                }
            }
            else -> {
                val badge = contextBadge
                val unit = unitLabel
                if (startWeight > 0.0) {
                    "$exerciseName ($badge): ${startWeight.trimmed()} → ${targetWeight.trimmed()} $unit"
                } else {
                    "$exerciseName ($badge): ${targetWeight.trimmed()} $unit"
                }
            }
        }
    }

    /**
     * Formats the goal header title displayed in `CycleGoalsProgress`.
     */
    fun formatProgressHeader(exerciseName: String): String {
        return "$exerciseName ($contextBadge Goal)"
    }

    companion object {
        val SUPPORTED_GOAL_METRICS: List<MetricType> = listOf(
            MetricType.WEIGHT,
            MetricType.CALORIES,
            MetricType.REPS
        )

        val RM_PRESETS: List<Int> = listOf(1, 3, 5, 10)

        /** `1 min`, `3 min`, `5 min`, and `null` (`Open`). */
        val TIME_WINDOW_OPTIONS: List<Int?> = listOf(1, 3, 5, null)

        private val METADATA_REGEX =
            Regex("""^\[metric=(WEIGHT|CALORIES|REPS),window=(OPEN|\d+)\]\s*""", RegexOption.IGNORE_CASE)

        fun defaultGoalMetricFor(exercise: Exercise?): MetricType {
            if (exercise == null) return MetricType.WEIGHT
            return when {
                exercise.metricType == MetricType.CALORIES -> MetricType.CALORIES
                exercise.metricType == MetricType.REPS || exercise.category == ExerciseCategory.GYMNASTICS -> MetricType.REPS
                else -> MetricType.WEIGHT
            }
        }

        fun unitLabelFor(metric: MetricType, exerciseUnit: String? = null): String = when (metric) {
            MetricType.CALORIES -> "cal"
            MetricType.REPS -> "reps"
            else -> exerciseUnit?.takeIf { it.isNotBlank() && it != "cal" && it != "reps" } ?: "kg"
        }

        fun formatTimeWindowLabel(minutes: Int?): String =
            if (minutes != null && minutes > 0) "$minutes min" else "Open"

        fun encodeMetadata(
            rawNotes: String,
            goalMetric: MetricType,
            timeWindowMinutes: Int?
        ): String {
            val stripped = rawNotes.replace(METADATA_REGEX, "").trim()
            if (goalMetric == MetricType.WEIGHT) {
                return stripped
            }
            val windowToken = if (timeWindowMinutes != null && timeWindowMinutes > 0) {
                timeWindowMinutes.toString()
            } else {
                "OPEN"
            }
            val header = "[metric=${goalMetric.name},window=$windowToken]"
            return if (stripped.isEmpty()) header else "$header $stripped"
        }

        fun parseMetadata(rawNotes: String): Triple<MetricType, Int?, String> {
            val match = METADATA_REGEX.find(rawNotes)
                ?: return Triple(MetricType.WEIGHT, null, rawNotes)
            val metricName = match.groupValues[1].uppercase()
            val windowToken = match.groupValues[2].uppercase()
            val metric = runCatching { MetricType.valueOf(metricName) }.getOrDefault(MetricType.WEIGHT)
            val windowMinutes = if (windowToken == "OPEN") null else windowToken.toIntOrNull()?.takeIf { it > 0 }
            val stripped = rawNotes.substring(match.range.last + 1).trim()
            return Triple(
                metric,
                if (metric == MetricType.WEIGHT) null else windowMinutes,
                stripped
            )
        }

        fun extractBestWorkingSetMetricValue(
            workingSets: List<BlockSet>,
            goalMetric: MetricType
        ): Double? {
            return when (goalMetric) {
                MetricType.CALORIES -> workingSets
                    .mapNotNull { (it.metricValue ?: it.weight)?.takeIf { v -> v > 0.0 } }
                    .maxOrNull()
                MetricType.REPS -> workingSets
                    .mapNotNull { set ->
                        set.metricValue?.takeIf { it > 0.0 }
                            ?: set.reps.toDouble().takeIf { it > 0.0 }
                    }
                    .maxOrNull()
                else -> workingSets
                    .mapNotNull { (it.weight ?: it.metricValue)?.takeIf { v -> v > 0.0 } }
                    .maxOrNull()
            }
        }
    }
}
