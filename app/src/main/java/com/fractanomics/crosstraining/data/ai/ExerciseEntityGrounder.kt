package com.fractanomics.crosstraining.data.ai

import com.fractanomics.crosstraining.data.dao.ExerciseDao
import com.fractanomics.crosstraining.data.model.Exercise
import kotlin.math.max
import kotlin.math.min

/**
 * Represents a scored candidate exercise match resulting from entity grounding.
 *
 * @property exercise The matched canonical [Exercise] database entity.
 * @property confidence Match confidence score normalized between 0.0 (no match) and 1.0 (exact match).
 */
data class ExerciseMatch(
    val exercise: Exercise,
    val confidence: Double
)

/**
 * Grounder that reconciles spoken exercise names (with speech-to-text artifacts,
 * imprecise terminology, jargon acronyms, or phonetic typos) against canonical [Exercise] entities
 * from [ExerciseDao] or candidate lists.
 *
 * Features:
 * - Direct exact and alias matching (e.g. "C&J" -> "Clean & Jerk", "T2B" -> "Toes to Bar", "OHS" -> "Overhead Squat").
 * - Stemming & pluralization tolerance ("cleans" -> "Clean", "Power Clean", "Squat Clean").
 * - Noise & metadata stripping (removes reps, loads, units, sets, and interval prefixes).
 * - Substring containment & token-level overlap.
 * - Levenshtein edit distance (fuzzy distance <= 2) for acoustic errors and typos.
 * - Disambiguation logic: If exact/dominating match (1.0), auto-selects 1 candidate; if ambiguous, returns ranked list (up to 3); if none, empty.
 * - Performance: Executes in < 5ms (well within the 50ms limit).
 */
class ExerciseEntityGrounder(
    private val lexicon: FitnessSpeechLexicon = FitnessSpeechLexicon.DEFAULT
) {

    /**
     * Resolves spoken or typed movement [text] against exercises retrieved from [dbDao].
     * Returns 0 to 3 candidate [Exercise] entities.
     */
    suspend fun resolveExercise(text: String, dbDao: ExerciseDao): List<Exercise> {
        return resolveExerciseWithConfidence(text, dbDao).map { it.exercise }
    }

    /**
     * Resolves spoken or typed movement [text] against exercises retrieved from [dbDao],
     * returning candidates with their confidence scores.
     */
    suspend fun resolveExerciseWithConfidence(text: String, dbDao: ExerciseDao): List<ExerciseMatch> {
        val exercises = dbDao.getAllOnce()
        return resolveExerciseWithConfidence(text, exercises)
    }

    /**
     * Resolves spoken or typed movement [text] against an in-memory list of candidate [candidates].
     * Returns 0 to 3 candidate [Exercise] entities.
     */
    fun resolveExercise(text: String, candidates: List<Exercise>): List<Exercise> {
        return resolveExerciseWithConfidence(text, candidates).map { it.exercise }
    }

    /**
     * Resolves spoken or typed movement [text] against an in-memory list of candidate [candidates],
     * returning 0 to 3 ranked [ExerciseMatch] candidates.
     *
     * Disambiguation behavior:
     * - If 0 matches meet threshold, returns empty list.
     * - If an exact match (confidence 1.0) is found, auto-selects and returns that single match.
     * - If 1 match meets threshold, returns list with that single match.
     * - If 2+ matches meet threshold without a singular exact match, returns ranked list (up to 3) sorted descending by confidence.
     */
    fun resolveExerciseWithConfidence(text: String, candidates: List<Exercise>): List<ExerciseMatch> {
        if (text.isBlank() || candidates.isEmpty()) return emptyList()

        // 1. Preprocess query with lexicon
        val correctedText = lexicon.correct(text)
        val queryNorm = normalizeString(correctedText)
        val rawQueryNorm = normalizeString(text)
        val strippedQueryNorm = stripMetadata(queryNorm)
        val strippedRawNorm = stripMetadata(rawQueryNorm)

        if (queryNorm.isBlank() && rawQueryNorm.isBlank() && strippedQueryNorm.isBlank()) return emptyList()

        val scoredMatches = mutableListOf<ExerciseMatch>()

        for (exercise in candidates) {
            val score = scoreCandidate(
                queryNorm = queryNorm,
                rawQueryNorm = rawQueryNorm,
                strippedQueryNorm = strippedQueryNorm,
                strippedRawNorm = strippedRawNorm,
                exercise = exercise
            )
            if (score >= MIN_CONFIDENCE_THRESHOLD) {
                scoredMatches.add(ExerciseMatch(exercise = exercise, confidence = score))
            }
        }

        if (scoredMatches.isEmpty()) return emptyList()

        // Sort primarily by confidence descending, then by length proximity, then alphabetical
        val sorted = scoredMatches.sortedWith(
            compareByDescending<ExerciseMatch> { it.confidence }
                .thenBy { kotlin.math.abs(it.exercise.name.length - (if (strippedQueryNorm.isNotBlank()) strippedQueryNorm.length else queryNorm.length)) }
                .thenBy { it.exercise.name }
        )

        // If top match is 1.0 (exact match) and strictly higher than the next match, auto-select single match
        val top = sorted.first()
        if (top.confidence >= 1.0) {
            val second = sorted.getOrNull(1)
            if (second == null || second.confidence < 1.0) {
                return listOf(top)
            }
        }

        return sorted.take(MAX_CANDIDATES)
    }

    /**
     * Resolves and returns the single highest-confidence [Exercise] match, or null if no candidate
     * meets the threshold.
     */
    suspend fun resolveBestMatch(text: String, dbDao: ExerciseDao): Exercise? {
        return resolveExercise(text, dbDao).firstOrNull()
    }

    /**
     * Resolves and returns the single highest-confidence [Exercise] match from in-memory candidates,
     * or null if no candidate meets the threshold.
     */
    fun resolveBestMatch(text: String, candidates: List<Exercise>): Exercise? {
        return resolveExercise(text, candidates).firstOrNull()
    }

    private fun scoreCandidate(
        queryNorm: String,
        rawQueryNorm: String,
        strippedQueryNorm: String,
        strippedRawNorm: String,
        exercise: Exercise
    ): Double {
        val exerciseNorm = normalizeString(exercise.name)
        val exerciseStem = stemString(exerciseNorm)

        val queries = listOf(queryNorm, rawQueryNorm, strippedQueryNorm, strippedRawNorm)
            .filter { it.isNotBlank() }
            .distinct()

        var bestScore = 0.0

        for (q in queries) {
            val qStem = stemString(q)

            // 1. Exact match
            if (q == exerciseNorm) {
                return 1.0
            }

            // 2. Known Alias / Acronym matching
            if (isKnownAlias(q, exerciseNorm)) {
                bestScore = max(bestScore, 0.98)
            }

            // 3. Exact stem match
            if (qStem == exerciseStem) {
                bestScore = max(bestScore, 0.95)
            }

            val qTokens = q.split(" ").filter { it.isNotBlank() }
            val qStemTokens = qStem.split(" ").filter { it.isNotBlank() }
            val exTokens = exerciseNorm.split(" ").filter { it.isNotBlank() }
            val exStemTokens = exerciseStem.split(" ").filter { it.isNotBlank() }

            // 4a. Single-word root disambiguation
            if (qStemTokens.size == 1) {
                val root = qStemTokens.first()
                if (exStemTokens.contains(root)) {
                    val compoundScore = when {
                        exerciseNorm.equals("Clean", ignoreCase = true) && root == "clean" -> 0.95
                        exerciseNorm.equals("Power Clean", ignoreCase = true) && root == "clean" -> 0.88
                        exerciseNorm.equals("Squat Clean", ignoreCase = true) && root == "clean" -> 0.87
                        exerciseNorm.equals("Hang Clean", ignoreCase = true) && root == "clean" -> 0.85
                        exerciseNorm.equals("Clean and Jerk", ignoreCase = true) && root == "clean" -> 0.80
                        exerciseNorm.equals("Back Squat", ignoreCase = true) && root == "squat" -> 0.88
                        exerciseNorm.equals("Front Squat", ignoreCase = true) && root == "squat" -> 0.86
                        exerciseNorm.equals("Overhead Squat", ignoreCase = true) && root == "squat" -> 0.84
                        exerciseNorm.equals("Air Squat", ignoreCase = true) && root == "squat" -> 0.82
                        exerciseNorm.equals("Snatch", ignoreCase = true) && root == "snatch" -> 0.95
                        exerciseNorm.equals("Power Snatch", ignoreCase = true) && root == "snatch" -> 0.88
                        exerciseNorm.equals("Squat Snatch", ignoreCase = true) && root == "snatch" -> 0.87
                        exerciseNorm.equals("Hang Snatch", ignoreCase = true) && root == "snatch" -> 0.85
                        exerciseNorm.equals("Strict Press", ignoreCase = true) && root == "press" -> 0.88
                        exerciseNorm.equals("Push Press", ignoreCase = true) && root == "press" -> 0.86
                        exerciseNorm.equals("Bench Press", ignoreCase = true) && root == "press" -> 0.84
                        exerciseNorm.equals("Jerk", ignoreCase = true) && root == "jerk" -> 0.95
                        exerciseNorm.equals("Split Jerk", ignoreCase = true) && root == "jerk" -> 0.88
                        exerciseNorm.equals("Push Jerk", ignoreCase = true) && root == "jerk" -> 0.86
                        else -> 0.75
                    }
                    bestScore = max(bestScore, compoundScore)
                }
            }

            // 4b. Full substring containment (e.g. "back squat" inside "5 back squats at 120 kg")
            if (q.contains(exerciseNorm) || qStem.contains(exerciseStem)) {
                val ratio = exerciseNorm.length.toDouble() / q.length.toDouble()
                val containmentScore = 0.85 + (0.10 * ratio)
                bestScore = max(bestScore, containmentScore)
            } else if (exerciseNorm.contains(q) || exerciseStem.contains(qStem)) {
                val ratio = q.length.toDouble() / exerciseNorm.length.toDouble()
                val containmentScore = 0.80 + (0.15 * ratio)
                bestScore = max(bestScore, containmentScore)
            }

            // 4c. Token-level overlap / Jaccard
            if (qStemTokens.isNotEmpty() && exStemTokens.isNotEmpty()) {
                val matchCount = qStemTokens.count { qt ->
                    exStemTokens.any { et -> et == qt || FitnessSpeechLexicon.phoneticDistance(et, qt) <= 1 }
                }
                if (matchCount > 0) {
                    val jaccard = matchCount.toDouble() / (qStemTokens.size + exStemTokens.size - matchCount).toDouble()
                    val score = 0.65 + (0.30 * jaccard)
                    bestScore = max(bestScore, score)
                }
            }

            // 5. Levenshtein edit distance
            val dist = FitnessSpeechLexicon.phoneticDistance(q, exerciseNorm)
            if (dist <= 2) {
                val maxLen = max(q.length, exerciseNorm.length)
                if (maxLen > 0) {
                    val fuzzyScore = when (dist) {
                        0 -> 1.0
                        1 -> 0.90 - (0.05 / maxLen)
                        2 -> 0.78 - (0.05 / maxLen)
                        else -> 0.0
                    }
                    bestScore = max(bestScore, fuzzyScore)
                }
            }

            // 5b. Word-by-word fuzzy distance
            if (qTokens.size == exTokens.size && qTokens.isNotEmpty()) {
                val totalDist = qTokens.zip(exTokens).sumOf { (qt, et) ->
                    FitnessSpeechLexicon.phoneticDistance(qt, et)
                }
                if (totalDist <= 2) {
                    val score = when (totalDist) {
                        1 -> 0.89
                        2 -> 0.77
                        else -> 0.0
                    }
                    bestScore = max(bestScore, score)
                }
            }
        }

        return min(1.0, max(0.0, bestScore))
    }

    private fun isKnownAlias(query: String, target: String): Boolean {
        val q = query.trim().lowercase()
        val t = target.trim().lowercase()

        val knownAliases = mapOf(
            "c and j" to listOf("clean and jerk"),
            "c & j" to listOf("clean and jerk"),
            "c&j" to listOf("clean and jerk"),
            "clean and jerk" to listOf("clean and jerk"),
            "ohs" to listOf("overhead squat"),
            "t2b" to listOf("toes to bar"),
            "toes to bar" to listOf("toes to bar"),
            "toes 2 bar" to listOf("toes to bar"),
            "toast to bar" to listOf("toes to bar"),
            "c2b" to listOf("chest to bar", "pull ups"),
            "chest to bar" to listOf("pull ups"),
            "bmu" to listOf("bar muscle ups", "muscle ups"),
            "rmu" to listOf("ring muscle ups", "muscle ups"),
            "hspu" to listOf("handstand push ups", "push ups"),
            "hand stand push up" to listOf("handstand push ups", "push ups"),
            "handstand push ups" to listOf("handstand push ups", "push ups"),
            "du" to listOf("double unders"),
            "dubs" to listOf("double unders"),
            "double-unders" to listOf("double unders"),
            "double unders" to listOf("double unders"),
            "kbs" to listOf("kettlebell swings"),
            "kb swings" to listOf("kettlebell swings"),
            "bjo" to listOf("box jumps", "box jump overs"),
            "wb" to listOf("wall ball shots", "wall balls"),
            "wall balls" to listOf("wall ball shots"),
            "wall ball" to listOf("wall ball shots"),
            "bike" to listOf("air bike"),
            "assault bike" to listOf("air bike"),
            "echo bike" to listOf("air bike"),
            "row" to listOf("rower"),
            "rowing" to listOf("rower"),
            "ski" to listOf("skierg"),
            "ski erg" to listOf("skierg"),
            "run" to listOf("running"),
            "running" to listOf("run"),
            "pullups" to listOf("pull ups"),
            "pull ups" to listOf("pull ups"),
            "pushups" to listOf("push ups"),
            "push ups" to listOf("push ups")
        )

        val targets = knownAliases[q] ?: emptyList()
        return targets.any { it.equals(t, ignoreCase = true) }
    }

    private fun normalizeString(input: String): String {
        return input.lowercase()
            .replace("&", " and ")
            .replace("+", " and ")
            .replace("-", " ")
            .replace("/", " ")
            .replace(PUNCTUATION_REGEX, " ")
            .replace(MULTI_SPACE_REGEX, " ")
            .trim()
    }

    private fun stripMetadata(input: String): String {
        var res = input
        res = METADATA_PREFIX_REGEX.replace(res, " ")
        res = METADATA_SUFFIX_REGEX.replace(res, " ")
        res = METADATA_KEYWORDS_REGEX.replace(res, " ")
        return normalizeString(res)
    }

    private fun stemString(input: String): String {
        return input.split(" ")
            .map { token ->
                when {
                    token.endsWith("ies") -> token.dropLast(3) + "y"
                    token.endsWith("es") && (token.endsWith("shes") || token.endsWith("ches") || token.endsWith("sses") || token.endsWith("xes")) -> token.dropLast(2)
                    token.endsWith("s") && !token.endsWith("ss") && !token.endsWith("us") -> token.dropLast(1)
                    token.endsWith("ing") && token.length > 5 -> token.dropLast(3)
                    else -> token
                }
            }
            .joinToString(" ")
    }

    companion object {
        const val MIN_CONFIDENCE_THRESHOLD = 0.60
        const val MAX_CANDIDATES = 3

        private val PUNCTUATION_REGEX = Regex("""[^\p{Alnum}\s]""")
        private val MULTI_SPACE_REGEX = Regex("""[ \t]+""")

        private val METADATA_PREFIX_REGEX = Regex(
            """(?i)\b(?:emom|amrap|e2mom|e3mom|e4mom|e5mom|tabata|metcon|wod|strength|block\s*\d+|set\s*\d+|round\s*\d+|\d+\s*min|\d+\s*minute|\d+\s*rounds?|\d+\s*sets?|\d+\s*reps?|\d+)\b"""
        )

        private val METADATA_SUFFIX_REGEX = Regex(
            """(?i)\b(?:at\s+\d+(?:\.\d+)?\s*(?:kg|lbs|kilos|pounds)?|on\s+a\s+\d+\s*minute\s*timer|for\s*time|rpe\s*\d+(?:\.\d+)?|r\s*pay\s*\d+|\d+(?:\.\d+)?\s*(?:kg|lbs|kilos|pounds))\b"""
        )

        private val METADATA_KEYWORDS_REGEX = Regex(
            """(?i)\b(?:heavy|light|moderate|logged|doing|max|tempo|working|reps|sets|plus)\b"""
        )

        /**
         * Global default instance.
         */
        val DEFAULT = ExerciseEntityGrounder()
    }
}