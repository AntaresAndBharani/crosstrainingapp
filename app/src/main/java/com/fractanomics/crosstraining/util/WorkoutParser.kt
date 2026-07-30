package com.fractanomics.crosstraining.util

import com.fractanomics.crosstraining.data.model.BlockKind
import com.fractanomics.crosstraining.data.model.Exercise
import com.fractanomics.crosstraining.data.model.Routine
import kotlin.math.roundToInt

data class ParsedSet(
    val reps: Int,
    val weight: Double? = null,
    val isWarmup: Boolean = false
)

data class ParsedWorkout(
    val name: String = "",
    val kind: BlockKind = BlockKind.STRENGTH,
    val format: String = "",
    val scheme: String = "",
    val existingExerciseId: Long? = null,
    val newExerciseName: String? = null,
    val routineId: Long? = null,
    val description: String = "",
    val sets: List<ParsedSet> = emptyList()
)

object WorkoutParser {

    private val FORMAT_KEYWORDS = listOf(
        "E1MOM", "E2MOM", "E3MOM", "E4MOM", "E5MOM", "EMOM", "AMRAP",
        "FOR TIME", "FT", "CHIPPER", "TABATA", "REST BETWEEN SETS", "REST"
    )

    /**
     * Parses structured inputs into a ParsedWorkout.
     */
    fun parseStructured(
        exercise: Exercise?,
        newExerciseName: String,
        routine: Routine?,
        format: String,
        setsInput: String,
        weightInput: String
    ): ParsedWorkout {
        val exerciseName = exercise?.name ?: newExerciseName.trim().ifBlank { routine?.name ?: "Workout Block" }
        val (repsList, setNum) = parseRepsAndSets(setsInput)
        val weights = parseWeights(weightInput, setNum.coerceAtLeast(repsList.size))

        val count = maxOf(setNum, repsList.size, weights.size, 1)
        val parsedSets = (0 until count).map { i ->
            val rep = if (repsList.isNotEmpty()) repsList[i % repsList.size] else 1
            val w = if (i < weights.size) weights[i] else (weights.lastOrNull())
            ParsedSet(reps = rep, weight = w)
        }

        val schemeStr = if (repsList.size > 1) repsList.joinToString("-") else if (setNum > 1 && repsList.isNotEmpty()) "${setNum}x${repsList.first()}" else setsInput.trim()

        return ParsedWorkout(
            name = exerciseName,
            kind = if (format.contains("AMRAP", ignoreCase = true) || format.contains("FOR TIME", ignoreCase = true)) BlockKind.METCON else BlockKind.STRENGTH,
            format = format.trim(),
            scheme = schemeStr,
            existingExerciseId = exercise?.id,
            newExerciseName = if (exercise == null && newExerciseName.isNotBlank()) newExerciseName.trim() else null,
            routineId = routine?.id,
            sets = parsedSets
        )
    }

    /**
     * Parses a freeform text string (e.g., "Snatch 5x3 @ 60, 65, 70, 75, 80 kg E2MOM")
     * into a ParsedWorkout.
     */
    fun parseFreeform(
        input: String,
        exercises: List<Exercise>,
        routines: List<Routine>
    ): ParsedWorkout {
        val raw = input.trim()
        if (raw.isBlank()) return ParsedWorkout()

        var remaining = raw

        // Extract format if present (e.g. E2MOM, AMRAP 15, Rest 2 min)
        var detectedFormat = ""
        val formatRegex = Regex("""(?i)\b(E\d+MOM|EMOM(?:\s+\d+)?|AMRAP(?:\s+\d+)?|FOR TIME|FT|TABATA|REST(?:\s+\d+(?:\s*min|\s*sec|\s*s)?)?)\b""")
        val formatMatch = formatRegex.find(remaining)
        if (formatMatch != null) {
            detectedFormat = formatMatch.value
            remaining = remaining.removeRange(formatMatch.range).trim()
        }

        // Extract weight part if preceded by @ or 'at' or 'kg'
        var weightStr = ""
        val weightAtRegex = Regex("""(?i)(?:@|at)\s*([\d\s\.,\-\/]+)(?:kg|lbs)?""")
        val weightAtMatch = weightAtRegex.find(remaining)
        if (weightAtMatch != null) {
            weightStr = weightAtMatch.groupValues[1].trim()
            remaining = remaining.removeRange(weightAtMatch.range).trim()
        } else {
            // Check for trailing or inline weights like '60, 65, 70 kg' or '60-80kg'
            val weightKgRegex = Regex("""([\d\s\.,\-]+)\s*(?:kg|lbs)\b""", RegexOption.IGNORE_CASE)
            val weightKgMatch = weightKgRegex.find(remaining)
            if (weightKgMatch != null) {
                weightStr = weightKgMatch.groupValues[1].trim()
                remaining = remaining.removeRange(weightKgMatch.range).trim()
            }
        }

        // Extract set & rep schemes like "5x3", "3-2-1-3-2-1", "3-2-1", "5 sets of 3"
        var setsStr = ""
        val setsxRepsRegex = Regex("""\b(\d+)\s*[xX*]\s*(\d+)\b""")
        val waveRegex = Regex("""\b(\d+(?:-\d+)+)\b""")
        val setsOfRepsRegex = Regex("""(?i)\b(\d+)\s*sets(?:\s*of\s*(\d+))?\b""")

        val setsxRepsMatch = setsxRepsRegex.find(remaining)
        val waveMatch = waveRegex.find(remaining)
        val setsOfRepsMatch = setsOfRepsRegex.find(remaining)

        if (setsxRepsMatch != null) {
            setsStr = setsxRepsMatch.value
            remaining = remaining.removeRange(setsxRepsMatch.range).trim()
        } else if (waveMatch != null) {
            setsStr = waveMatch.value
            remaining = remaining.removeRange(waveMatch.range).trim()
        } else if (setsOfRepsMatch != null) {
            setsStr = setsOfRepsMatch.value
            remaining = remaining.removeRange(setsOfRepsMatch.range).trim()
        }

        // Clean up remaining text to resolve exercise / routine / block name
        val nameCandidate = remaining
            .replace(Regex("""(?i)\b(kg|lbs|reps|sets|rounds)\b"""), "")
            .replace(Regex("""[,\(\):]"""), " ")
            .replace(Regex("""\s+"""), " ")
            .trim()

        // Match against routines first, then exercises
        val matchedRoutine = routines.firstOrNull { r ->
            nameCandidate.equals(r.name, ignoreCase = true) || nameCandidate.contains(r.name, ignoreCase = true)
        }

        val matchedExercise = if (matchedRoutine != null) {
            exercises.firstOrNull { it.id == matchedRoutine.mainExerciseId }
        } else {
            exercises.firstOrNull { e ->
                nameCandidate.equals(e.name, ignoreCase = true) ||
                        nameCandidate.contains(e.name, ignoreCase = true) ||
                        e.name.contains(nameCandidate, ignoreCase = true)
            }
        }

        val blockName = when {
            matchedRoutine != null -> matchedRoutine.name
            matchedExercise != null -> matchedExercise.name
            nameCandidate.isNotBlank() -> nameCandidate.capitalizeWords()
            else -> "Workout Block"
        }

        val newExName = if (matchedExercise == null && matchedRoutine == null && nameCandidate.isNotBlank()) {
            nameCandidate.capitalizeWords()
        } else null

        // Default sets input if setsStr was empty but weightStr has list
        val effectiveSetsStr = if (setsStr.isBlank() && weightStr.isNotBlank()) {
            val weightsCount = parseWeights(weightStr, 1).size
            if (weightsCount > 1) "${weightsCount}x1" else "1x1"
        } else if (setsStr.isBlank()) {
            "3x5"
        } else setsStr

        val kind = when {
            detectedFormat.contains("AMRAP", ignoreCase = true) ||
                    detectedFormat.contains("FOR TIME", ignoreCase = true) ||
                    detectedFormat.contains("FT", ignoreCase = true) ||
                    detectedFormat.contains("TABATA", ignoreCase = true) -> BlockKind.METCON
            else -> BlockKind.STRENGTH
        }

        return parseStructured(
            exercise = matchedExercise,
            newExerciseName = newExName ?: "",
            routine = matchedRoutine,
            format = if (detectedFormat.isNotBlank()) detectedFormat else matchedRoutine?.defaultFormat ?: "",
            setsInput = effectiveSetsStr,
            weightInput = weightStr
        ).copy(name = blockName, kind = kind)
    }

    /**
     * Parses rep and set scheme inputs:
     * - "5x3" -> reps = [3, 3, 3, 3, 3], totalSets = 5
     * - "3-2-1-3-2-1" -> reps = [3, 2, 1, 3, 2, 1], totalSets = 6
     * - "5" -> reps = [1], totalSets = 5
     * - "5 sets of 3" -> reps = [3, 3, 3, 3, 3], totalSets = 5
     */
    fun parseRepsAndSets(input: String): Pair<List<Int>, Int> {
        val trimmed = input.trim()
        if (trimmed.isBlank()) return Pair(listOf(1), 1)

        val setsxRepsRegex = Regex("""^(\d+)\s*[xX*]\s*(\d+)$""")
        val setsxRepsMatch = setsxRepsRegex.matchEntire(trimmed)
        if (setsxRepsMatch != null) {
            val sets = setsxRepsMatch.groupValues[1].toIntOrNull() ?: 1
            val reps = setsxRepsMatch.groupValues[2].toIntOrNull() ?: 1
            return Pair(List(sets) { reps }, sets)
        }

        val setsOfRepsRegex = Regex("""(?i)^(\d+)\s*sets(?:\s*of\s*(\d+))?$""")
        val setsOfRepsMatch = setsOfRepsRegex.matchEntire(trimmed)
        if (setsOfRepsMatch != null) {
            val sets = setsOfRepsMatch.groupValues[1].toIntOrNull() ?: 1
            val reps = setsOfRepsMatch.groupValues[2].toIntOrNull() ?: 1
            return Pair(List(sets) { reps }, sets)
        }

        if (trimmed.contains("-") || trimmed.contains(",")) {
            val parts = trimmed.split(Regex("""[,\-]""")).mapNotNull { it.trim().toIntOrNull() }
            if (parts.isNotEmpty()) {
                return Pair(parts, parts.size)
            }
        }

        val singleNum = trimmed.toIntOrNull()
        if (singleNum != null) {
            return Pair(List(singleNum) { 1 }, singleNum)
        }

        return Pair(listOf(1), 1)
    }

    /**
     * Parses weight input string:
     * - "60, 65, 70, 75, 80" -> [60.0, 65.0, 70.0, 75.0, 80.0]
     * - "60-80" (with setNum=5) -> [60.0, 65.0, 70.0, 75.0, 80.0]
     * - "80" -> [80.0]
     */
    fun parseWeights(input: String, setNum: Int): List<Double> {
        val trimmed = input.replace("kg", "", ignoreCase = true)
            .replace("lbs", "", ignoreCase = true)
            .trim()
        if (trimmed.isBlank()) return emptyList()

        // Check for range like "60-80"
        val rangeRegex = Regex("""^(\d+(?:\.\d+)?)\s*[\-–—]\s*(\d+(?:\.\d+)?)$""")
        val rangeMatch = rangeRegex.matchEntire(trimmed)
        if (rangeMatch != null) {
            val start = rangeMatch.groupValues[1].toDoubleOrNull() ?: return emptyList()
            val end = rangeMatch.groupValues[2].toDoubleOrNull() ?: return emptyList()
            val num = maxOf(setNum, 2)
            val step = (end - start) / (num - 1)
            return (0 until num).map { i ->
                val valUnrounded = start + i * step
                (valUnrounded * 2.0).roundToInt() / 2.0 // Round to nearest 0.5 kg
            }
        }

        // Comma or space separated numbers
        val parts = trimmed.split(Regex("""[\s,]+""")).mapNotNull {
            it.replace(',', '.').toDoubleOrNull()
        }
        if (parts.isNotEmpty()) return parts

        return emptyList()
    }

    private fun String.capitalizeWords(): String {
        return split(" ").joinToString(" ") { word ->
            word.lowercase().replaceFirstChar { if (it.isLowerCase()) it.titlecase() else it.toString() }
        }
    }
}
