package com.fractanomics.crosstraining.util

import com.fractanomics.crosstraining.data.model.BlockKind
import com.fractanomics.crosstraining.data.model.Exercise
import com.fractanomics.crosstraining.data.model.ExerciseCategory
import com.fractanomics.crosstraining.data.model.MetricType
import com.fractanomics.crosstraining.data.model.RoutineBlock
import com.fractanomics.crosstraining.ui.WORKOUT_FORMATS

data class TargetPreset(
    val label: String,
    val value: Int
)

/**
 * Per-exercise target measurement unit inside Daily Routine blocks (`SPEC-ROUTINE-BUILDER-02`).
 */
enum class ExerciseTargetMetric(
    val label: String,
    val compactUnit: String,
    val spokenUnitSingular: String,
    val spokenUnitPlural: String,
    val fixedStep: Int,
    val perSetStep: Int,
    val defaultFixedValue: Int,
    val presets: List<TargetPreset>
) {
    REPS(
        label = "Reps",
        compactUnit = "reps",
        spokenUnitSingular = "rep",
        spokenUnitPlural = "reps",
        fixedStep = 1,
        perSetStep = 1,
        defaultFixedValue = 8,
        presets = listOf(
            TargetPreset("3", 3),
            TargetPreset("5", 5),
            TargetPreset("8", 8),
            TargetPreset("10", 10),
            TargetPreset("12", 12),
            TargetPreset("15", 15)
        )
    ),
    CALORIES(
        label = "Calories",
        compactUnit = "cal",
        spokenUnitSingular = "calorie",
        spokenUnitPlural = "calories",
        fixedStep = 2,
        perSetStep = 1,
        defaultFixedValue = 15,
        presets = listOf(
            TargetPreset("10", 10),
            TargetPreset("12", 12),
            TargetPreset("15", 15),
            TargetPreset("20", 20),
            TargetPreset("25", 25),
            TargetPreset("30", 30)
        )
    ),
    TIME_SECONDS(
        label = "Time",
        compactUnit = "s",
        spokenUnitSingular = "second",
        spokenUnitPlural = "seconds",
        fixedStep = 15,
        perSetStep = 5,
        defaultFixedValue = 45,
        presets = listOf(
            TargetPreset("20s", 20),
            TargetPreset("30s", 30),
            TargetPreset("45s", 45),
            TargetPreset("60s", 60),
            TargetPreset("90s", 90),
            TargetPreset("2m", 120)
        )
    ),
    METERS(
        label = "Meters",
        compactUnit = "m",
        spokenUnitSingular = "meter",
        spokenUnitPlural = "meters",
        fixedStep = 50,
        perSetStep = 50,
        defaultFixedValue = 250,
        presets = listOf(
            TargetPreset("100m", 100),
            TargetPreset("200m", 200),
            TargetPreset("250m", 250),
            TargetPreset("400m", 400),
            TargetPreset("500m", 500),
            TargetPreset("1km", 1000)
        )
    );

    fun toLibraryMetricType(category: ExerciseCategory = ExerciseCategory.BARBELL): MetricType = when (this) {
        CALORIES -> MetricType.CALORIES
        TIME_SECONDS -> MetricType.TIME
        METERS -> MetricType.DISTANCE
        REPS -> if (category == ExerciseCategory.GYMNASTICS) MetricType.REPS else MetricType.WEIGHT
    }

    companion object {
        fun fromLibraryMetricType(metricType: MetricType): ExerciseTargetMetric = when (metricType) {
            MetricType.CALORIES -> CALORIES
            MetricType.TIME -> TIME_SECONDS
            MetricType.DISTANCE -> METERS
            MetricType.WEIGHT, MetricType.REPS -> REPS
        }

        fun fromTokenOrNull(token: String): ExerciseTargetMetric? = when (token.trim().uppercase()) {
            "REPS" -> REPS
            "CALORIES", "CAL" -> CALORIES
            "TIME_SECONDS", "TIME", "SECONDS", "SEC" -> TIME_SECONDS
            "METERS", "DISTANCE", "M" -> METERS
            else -> null
        }
    }
}

enum class TargetMode {
    FIXED,
    PER_SET;

    companion object {
        fun fromTokenOrNull(token: String): TargetMode? = when (token.trim().uppercase()) {
            "FIXED" -> FIXED
            "PER_SET", "PERSET", "VARIABLE" -> PER_SET
            else -> null
        }
    }
}

data class RoutineExerciseTargetSpec(
    val metric: ExerciseTargetMetric,
    val mode: TargetMode,
    val fixedValue: Int,
    val perSetValues: List<Int>
) {
    val isAllZeroInvalid: Boolean
        get() = when (mode) {
            TargetMode.FIXED -> fixedValue <= 0
            TargetMode.PER_SET -> perSetValues.isEmpty() || perSetValues.all { it <= 0 }
        }

    fun effectiveValues(setsCount: Int): List<Int> {
        val count = setsCount.coerceAtLeast(1)
        return when (mode) {
            TargetMode.FIXED -> List(count) { fixedValue.coerceAtLeast(0) }
            TargetMode.PER_SET -> RoutineTargetCodec.resizePerSetValues(perSetValues, fixedValue, count)
        }
    }
}

/**
 * Pure deterministic codec and helper rules for Daily Routine per-exercise targets (`[rb_v2=...]`),
 * contextual block formats, format-driven set defaults, and legacy `RoutineBlock` fallback parsing.
 */
object RoutineTargetCodec {

    private const val HEADER_PREFIX = "[rb_v2="
    private val HEADER_LINE_REGEX = Regex("""^\[rb_v2=([^\]]*)]\s*\r?\n?""", RegexOption.IGNORE_CASE)

    val PRIMARY_BLOCK_KINDS: List<BlockKind> = listOf(
        BlockKind.WEIGHTLIFTING,
        BlockKind.METCON,
        BlockKind.GYMNASTICS,
        BlockKind.ACCESSORY
    )

    val OVERFLOW_BLOCK_KINDS: List<BlockKind> = BlockKind.entries.filterNot { it in PRIMARY_BLOCK_KINDS }

    val BLOCK_SET_QUICK_PILLS: List<Int> = listOf(1, 3, 4, 5, 6, 8)

    private val SINGLE_EFFORT_FORMATS = setOf(
        "amrap",
        "for time",
        "chipper",
        "death by",
        "time cap"
    )

    fun hasStructuredHeader(notes: String?): Boolean {
        if (notes.isNullOrBlank()) return false
        return notes.trimStart().startsWith(HEADER_PREFIX, ignoreCase = true)
    }

    fun extractCleanNotes(notes: String?): String {
        if (notes.isNullOrBlank()) return ""
        val withoutV2 = notes.trimStart().replaceFirst(HEADER_LINE_REGEX, "")
        // Also strip legacy auto-injected "Score: TIME" header if Work/Rest are empty
        val lines = withoutV2.lines()
        val firstLine = lines.firstOrNull()?.trim().orEmpty()
        if (firstLine.equals("Score: TIME", ignoreCase = true) ||
            firstLine.equals("Score: REPS", ignoreCase = true) ||
            firstLine.equals("Score: CALORIES", ignoreCase = true) ||
            firstLine.equals("Score: DISTANCE", ignoreCase = true) ||
            firstLine.equals("Score: WEIGHT", ignoreCase = true)
        ) {
            return lines.drop(1).joinToString("\n").trim()
        }
        return withoutV2.trim()
    }

    fun encodeNotesWithTargets(
        cleanNotes: String,
        slots: List<RoutineExerciseTargetSpec>,
        setsCount: Int
    ): String {
        val sanitizedNotes = extractCleanNotes(cleanNotes)
        if (slots.isEmpty()) return sanitizedNotes
        val normalizedSets = setsCount.coerceAtLeast(1)
        val encodedSlots = slots.joinToString(";") { slot ->
            val cleanFixed = slot.fixedValue.coerceAtLeast(0)
            val values = when (slot.mode) {
                TargetMode.FIXED -> List(normalizedSets) { cleanFixed }
                TargetMode.PER_SET -> resizePerSetValues(slot.perSetValues, cleanFixed, normalizedSets)
            }
            "${slot.metric.name}:${slot.mode.name}:$cleanFixed:${values.joinToString(".")}"
        }
        val header = "[rb_v2=$encodedSlots]"
        return if (sanitizedNotes.isBlank()) header else "$header\n$sanitizedNotes"
    }

    fun decodeHeaderSlots(notes: String?, setsCount: Int): List<RoutineExerciseTargetSpec>? {
        if (notes.isNullOrBlank()) return null
        val match = HEADER_LINE_REGEX.find(notes.trimStart()) ?: return null
        val payload = match.groupValues[1].trim()
        if (payload.isEmpty()) return emptyList()
        val normalizedSets = setsCount.coerceAtLeast(1)

        val parsed = payload.split(";").mapNotNull { rawSlot ->
            val parts = rawSlot.trim().split(":")
            if (parts.size < 4) return@mapNotNull null
            val metric = ExerciseTargetMetric.fromTokenOrNull(parts[0]) ?: return@mapNotNull null
            val mode = TargetMode.fromTokenOrNull(parts[1]) ?: return@mapNotNull null
            val fixedVal = parts[2].trim().toIntOrNull()?.coerceAtLeast(0) ?: metric.defaultFixedValue
            val rawValues = parts[3].split(".")
                .mapNotNull { it.trim().toIntOrNull()?.coerceAtLeast(0) }
            val normalizedValues = if (mode == TargetMode.FIXED) {
                List(normalizedSets) { fixedVal }
            } else {
                resizePerSetValues(rawValues, fixedVal, normalizedSets)
            }
            RoutineExerciseTargetSpec(
                metric = metric,
                mode = mode,
                fixedValue = fixedVal,
                perSetValues = normalizedValues
            )
        }
        return parsed.takeIf { it.isNotEmpty() }
    }

    /**
     * Decodes a [RoutineBlock] (either structured `[rb_v2=...]` or legacy `targetRepsScheme`)
     * into ordered `(exerciseId, RoutineExerciseTargetSpec)` pairs aligned with `exerciseIdsCsv`.
     */
    fun decodeBlockExercises(
        block: RoutineBlock,
        exercisesById: Map<Long, Exercise>
    ): List<Pair<Long, RoutineExerciseTargetSpec>> {
        val ids = parseExerciseIdsPreservingOrder(block.exerciseIdsCsv)
        if (ids.isEmpty()) return emptyList()
        val setsCount = block.setsCount.coerceAtLeast(1)
        val headerSlots = decodeHeaderSlots(block.notes, setsCount)

        if (headerSlots != null) {
            return ids.mapIndexed { idx, exId ->
                val ex = exercisesById[exId]
                val defaultMetric = ex?.let { ExerciseTargetMetric.fromLibraryMetricType(it.metricType) }
                    ?: ExerciseTargetMetric.REPS
                val slot = headerSlots.getOrNull(idx) ?: RoutineExerciseTargetSpec(
                    metric = defaultMetric,
                    mode = TargetMode.FIXED,
                    fixedValue = defaultMetric.defaultFixedValue,
                    perSetValues = List(setsCount) { defaultMetric.defaultFixedValue }
                )
                exId to slot
            }
        }

        return decodeLegacyBlockExercises(
            ids = ids,
            setsCount = setsCount,
            targetRepsScheme = block.targetRepsScheme,
            exercisesById = exercisesById
        )
    }

    private fun decodeLegacyBlockExercises(
        ids: List<Long>,
        setsCount: Int,
        targetRepsScheme: String,
        exercisesById: Map<Long, Exercise>
    ): List<Pair<Long, RoutineExerciseTargetSpec>> {
        val scheme = targetRepsScheme.trim()

        // Multi-exercise sequence scheme like "1 + 2 + 1" or "10 / 15"
        if (ids.size > 1 && (scheme.contains("+") || scheme.contains("/"))) {
            val seqParts = scheme.split(Regex("[+/]")).mapNotNull { it.trim().toIntOrNull()?.takeIf { v -> v > 0 } }
            return ids.mapIndexed { idx, exId ->
                val metric = exercisesById[exId]?.let { ExerciseTargetMetric.fromLibraryMetricType(it.metricType) }
                    ?: ExerciseTargetMetric.REPS
                val fixed = seqParts.getOrNull(idx) ?: seqParts.lastOrNull() ?: metric.defaultFixedValue
                exId to RoutineExerciseTargetSpec(
                    metric = metric,
                    mode = TargetMode.FIXED,
                    fixedValue = fixed,
                    perSetValues = List(setsCount) { fixed }
                )
            }
        }

        // Single-exercise (or shared-scheme) hyphenated per-set scheme like "21-15-9" or "3-2-1-3-2-1"
        if (scheme.contains("-") && !scheme.startsWith("TRISET", ignoreCase = true) &&
            !scheme.startsWith("SUPERSET", ignoreCase = true) &&
            !scheme.startsWith("BISET", ignoreCase = true)
        ) {
            val dashValues = scheme.split("-").mapNotNull { it.trim().toIntOrNull()?.coerceAtLeast(0) }
            if (dashValues.size > 1 && dashValues.any { it > 0 }) {
                val effectiveCount = if (setsCount <= 1) dashValues.size else setsCount
                return ids.map { exId ->
                    val metric = exercisesById[exId]?.let { ExerciseTargetMetric.fromLibraryMetricType(it.metricType) }
                        ?: ExerciseTargetMetric.REPS
                    val firstPositive = dashValues.firstOrNull { it > 0 } ?: metric.defaultFixedValue
                    val normalized = resizePerSetValues(dashValues, firstPositive, effectiveCount)
                    val allIdentical = normalized.distinct().size == 1
                    exId to RoutineExerciseTargetSpec(
                        metric = metric,
                        mode = if (allIdentical) TargetMode.FIXED else TargetMode.PER_SET,
                        fixedValue = firstPositive,
                        perSetValues = normalized
                    )
                }
            }
        }

        // NxM format like "5x3"
        if (scheme.lowercase().contains("x")) {
            val xParts = scheme.lowercase().split("x")
            if (xParts.size == 2) {
                val reps = xParts[1].trim().toIntOrNull()?.takeIf { it > 0 }
                if (reps != null) {
                    return ids.map { exId ->
                        val metric = exercisesById[exId]?.let { ExerciseTargetMetric.fromLibraryMetricType(it.metricType) }
                            ?: ExerciseTargetMetric.REPS
                        exId to RoutineExerciseTargetSpec(
                            metric = metric,
                            mode = TargetMode.FIXED,
                            fixedValue = reps,
                            perSetValues = List(setsCount) { reps }
                        )
                    }
                }
            }
        }

        // Plain single integer like "5" or "12"
        val singleInt = scheme.toIntOrNull()?.takeIf { it > 0 }
        return ids.map { exId ->
            val metric = exercisesById[exId]?.let { ExerciseTargetMetric.fromLibraryMetricType(it.metricType) }
                ?: ExerciseTargetMetric.REPS
            val fixed = singleInt ?: metric.defaultFixedValue
            exId to RoutineExerciseTargetSpec(
                metric = metric,
                mode = TargetMode.FIXED,
                fixedValue = fixed,
                perSetValues = List(setsCount) { fixed }
            )
        }
    }

    fun parseExerciseIdsPreservingOrder(csv: String?): List<Long> {
        if (csv.isNullOrBlank()) return emptyList()
        return csv.split(",").mapNotNull { it.trim().toLongOrNull() }
    }

    fun resizePerSetValues(
        currentValues: List<Int>,
        fallbackFixed: Int,
        newSetsCount: Int
    ): List<Int> {
        val targetCount = newSetsCount.coerceAtLeast(1)
        if (currentValues.isEmpty()) {
            val fill = fallbackFixed.coerceAtLeast(0)
            return List(targetCount) { fill }
        }
        return when {
            currentValues.size == targetCount -> currentValues.map { it.coerceAtLeast(0) }
            targetCount < currentValues.size -> currentValues.take(targetCount).map { it.coerceAtLeast(0) }
            else -> {
                // Set N copies Set N-1 (including 0)
                val lastVal = currentValues.last().coerceAtLeast(0)
                currentValues.map { it.coerceAtLeast(0) } + List(targetCount - currentValues.size) { lastVal }
            }
        }
    }

    fun copyFirstSetToAll(values: List<Int>, setsCount: Int, fallbackFixed: Int): List<Int> {
        val count = setsCount.coerceAtLeast(1)
        val first = values.firstOrNull()?.coerceAtLeast(0) ?: fallbackFixed.coerceAtLeast(1)
        return List(count) { first }
    }

    fun applyDescendingPattern(
        values: List<Int>,
        metric: ExerciseTargetMetric,
        setsCount: Int,
        fallbackFixed: Int
    ): List<Int> {
        val count = setsCount.coerceAtLeast(1)
        val start = (values.firstOrNull()?.takeIf { it > 0 } ?: fallbackFixed.takeIf { it > 0 } ?: metric.defaultFixedValue)
        val step = when (metric) {
            ExerciseTargetMetric.REPS -> 2
            ExerciseTargetMetric.CALORIES -> 3
            ExerciseTargetMetric.TIME_SECONDS -> 10
            ExerciseTargetMetric.METERS -> 50
        }
        return List(count) { idx ->
            (start - idx * step).coerceAtLeast(metric.perSetStep.coerceAtLeast(1))
        }
    }

    fun applyAlternatingZerosPattern(
        values: List<Int>,
        setsCount: Int,
        fallbackFixed: Int,
        zeroEvenSets: Boolean = true
    ): List<Int> {
        val count = setsCount.coerceAtLeast(1)
        val activeBase = values.firstOrNull { it > 0 } ?: fallbackFixed.takeIf { it > 0 } ?: 1
        return List(count) { idx ->
            val isEvenSetNumber = (idx + 1) % 2 == 0
            val shouldZero = if (zeroEvenSets) isEvenSetNumber else !isEvenSetNumber
            if (shouldZero) 0 else (values.getOrNull(idx)?.takeIf { it > 0 } ?: activeBase)
        }
    }

    fun isSingleEffortFormat(format: String): Boolean =
        format.trim().lowercase() in SINGLE_EFFORT_FORMATS

    fun defaultSetsForFormat(format: String): Int {
        val normalized = format.trim().lowercase()
        return when {
            normalized in SINGLE_EFFORT_FORMATS -> 1
            normalized == "tabata" -> 8
            else -> 4
        }
    }

    fun primaryFormatsForKind(kind: BlockKind): List<String> = when (kind) {
        BlockKind.WEIGHTLIFTING, BlockKind.STRENGTH, BlockKind.COMPLEX -> listOf(
            "Sets x Reps", "E2MOM", "E3MOM", "EMOM", "Wave", "Tempo"
        )
        BlockKind.METCON, BlockKind.METABOLIC -> listOf(
            "AMRAP", "For Time", "EMOM", "E2MOM", "E3MOM", "Rounds for Time", "Tabata", "Chipper", "Death by"
        )
        BlockKind.CARDIO -> listOf(
            "Calorie Sprints", "Distance Sprints", "Work/Rest Intervals", "EMOM", "For Time", "AMRAP"
        )
        BlockKind.GYMNASTICS -> listOf(
            "EMOM", "E2MOM", "Sets x Reps", "AMRAP", "For Time", "Tabata"
        )
        BlockKind.ACCESSORY, BlockKind.HYPERTROPHY, BlockKind.SUPERSET -> listOf(
            "Sets x Reps", "Tempo", "Rest 90s", "Rest 2 min", "EMOM", "E2MOM"
        )
        BlockKind.WARMUP, BlockKind.OTHER -> listOf(
            "Sets x Reps", "EMOM", "AMRAP", "For Time", "Work/Rest Intervals"
        )
    }

    fun overflowFormatsForKind(kind: BlockKind): List<String> {
        val primary = primaryFormatsForKind(kind).toSet()
        return WORKOUT_FORMATS.filterNot { it in primary }
    }

    fun formatValueNumberAndUnit(metric: ExerciseTargetMetric, value: Int): String = when (metric) {
        ExerciseTargetMetric.REPS -> "$value reps"
        ExerciseTargetMetric.CALORIES -> "$value cal"
        ExerciseTargetMetric.TIME_SECONDS -> if (value >= 60 && value % 60 == 0) "${value / 60}m" else "${value}s"
        ExerciseTargetMetric.METERS -> if (value >= 1000 && value % 1000 == 0) "${value / 1000}km" else "${value}m"
    }

    fun formatCompactSetCellValue(metric: ExerciseTargetMetric, value: Int): String = when (metric) {
        ExerciseTargetMetric.REPS -> value.toString()
        ExerciseTargetMetric.CALORIES -> "$value cal"
        ExerciseTargetMetric.TIME_SECONDS -> if (value >= 60 && value % 60 == 0) "${value / 60}m" else "${value}s"
        ExerciseTargetMetric.METERS -> if (value >= 1000 && value % 1000 == 0) "${value / 1000}km" else "${value}m"
    }

    fun formatSkipLabel(metric: ExerciseTargetMetric): String = when (metric) {
        ExerciseTargetMetric.REPS -> "0 (Skip)"
        ExerciseTargetMetric.CALORIES -> "0 cal (Skip)"
        ExerciseTargetMetric.TIME_SECONDS -> "0s (Skip)"
        ExerciseTargetMetric.METERS -> "0m (Skip)"
    }

    fun formatSpokenSetDescription(setNumber: Int, metric: ExerciseTargetMetric, value: Int): String {
        val unitWord = if (value == 1) metric.spokenUnitSingular else metric.spokenUnitPlural
        return if (value <= 0) {
            "Set $setNumber, 0 ${metric.spokenUnitPlural}, skipped in this set"
        } else {
            "Set $setNumber, $value $unitWord"
        }
    }

    /**
     * Formats the collapsed Exercise Card badge, e.g.:
     * - Fixed mode: `"8 reps / set"`, `"15 cal / set"`, `"45s / set"`, `"250m / set"`
     * - Per-Set mode: `"12 • 10 • 8 • 0 reps"`, `"15 • 15 • 12 • 0 cal"`, `"250 • 250 • 200 • 0m"`
     */
    fun formatExerciseTargetBadge(spec: RoutineExerciseTargetSpec, setsCount: Int): String {
        return when (spec.mode) {
            TargetMode.FIXED -> "${formatValueNumberAndUnit(spec.metric, spec.fixedValue)} / set"
            TargetMode.PER_SET -> {
                val vals = resizePerSetValues(spec.perSetValues, spec.fixedValue, setsCount)
                val joined = vals.joinToString(" • ")
                when (spec.metric) {
                    ExerciseTargetMetric.REPS -> "$joined reps"
                    ExerciseTargetMetric.CALORIES -> "$joined cal"
                    ExerciseTargetMetric.TIME_SECONDS -> "${joined}s"
                    ExerciseTargetMetric.METERS -> "${joined}m"
                }
            }
        }
    }

    /**
     * Produces a clean human-readable `RoutineBlock.targetRepsScheme` string (max 190 chars)
     * for backwards-compatible display in legacy views, CSV exports, and MCP summaries.
     */
    fun formatBlockTargetSummary(slots: List<RoutineExerciseTargetSpec>, setsCount: Int): String {
        if (slots.isEmpty()) return ""
        if (slots.size == 1) {
            val single = slots.first()
            return when (single.mode) {
                TargetMode.FIXED -> {
                    if (single.metric == ExerciseTargetMetric.REPS) {
                        single.fixedValue.toString()
                    } else {
                        formatValueNumberAndUnit(single.metric, single.fixedValue)
                    }
                }
                TargetMode.PER_SET -> {
                    val vals = resizePerSetValues(single.perSetValues, single.fixedValue, setsCount).joinToString("-")
                    when (single.metric) {
                        ExerciseTargetMetric.REPS -> vals
                        ExerciseTargetMetric.CALORIES -> "$vals cal"
                        ExerciseTargetMetric.TIME_SECONDS -> "${vals}s"
                        ExerciseTargetMetric.METERS -> "${vals}m"
                    }
                }
            }
        }

        val raw = slots.joinToString(" + ") { slot ->
            when (slot.mode) {
                TargetMode.FIXED -> formatValueNumberAndUnit(slot.metric, slot.fixedValue)
                TargetMode.PER_SET -> {
                    val vals = resizePerSetValues(slot.perSetValues, slot.fixedValue, setsCount).joinToString("-")
                    when (slot.metric) {
                        ExerciseTargetMetric.REPS -> "$vals reps"
                        ExerciseTargetMetric.CALORIES -> "$vals cal"
                        ExerciseTargetMetric.TIME_SECONDS -> "${vals}s"
                        ExerciseTargetMetric.METERS -> "${vals}m"
                    }
                }
            }
        }
        return if (raw.length <= 190) raw else raw.take(187) + "..."
    }
}
