package com.fractanomics.crosstraining.data.ai

import com.fractanomics.crosstraining.data.dao.ExerciseDao
import com.fractanomics.crosstraining.data.model.BlockKind
import com.fractanomics.crosstraining.data.model.Exercise
import com.fractanomics.crosstraining.data.model.ExerciseCategory
import com.fractanomics.crosstraining.data.model.MetricType
import com.fractanomics.crosstraining.util.ParsedDocumentBlock
import com.fractanomics.crosstraining.util.ParsedWorkoutDocument

/**
 * Result of resolving exercises from a parsed workout document or movement list against the library.
 *
 * @property matchedExisting Map from movement name to already-existing library [Exercise].
 * @property missingExercises List of distinct new [Exercise] proposals to be cataloged.
 * @property complexCompositeExercises Map from complex block name to newly proposed or matched composite [Exercise].
 * @property blockResolutions Resolved metadata for each block in the document.
 */
data class WorkoutEntityResolutionResult(
    val matchedExisting: Map<String, Exercise> = emptyMap(),
    val missingExercises: List<Exercise> = emptyList(),
    val complexCompositeExercises: Map<String, Exercise> = emptyMap(),
    val blockResolutions: List<ResolvedBlockEntity> = emptyList()
)

/**
 * Resolved entity metadata for an individual block.
 *
 * @property block The original [ParsedDocumentBlock].
 * @property mainExercise The resolved primary or composite [Exercise].
 * @property componentExercises The component exercises for complexes or supersets.
 * @property isNewExercise Whether the main exercise is newly inferred and needs cataloging.
 */
data class ResolvedBlockEntity(
    val block: ParsedDocumentBlock,
    val mainExercise: Exercise,
    val componentExercises: List<Exercise> = emptyList(),
    val isNewExercise: Boolean = false
)

/**
 * Resolver that performs in-transaction case-insensitive entity grounding, movement deduplication,
 * category & metric inference, and barbell complex composite isolation.
 */
class WorkoutEntityResolver(
    private val grounder: ExerciseEntityGrounder = ExerciseEntityGrounder.DEFAULT,
    private val lexicon: FitnessSpeechLexicon = FitnessSpeechLexicon.DEFAULT
) {

    companion object {
        val DEFAULT = WorkoutEntityResolver()

        /**
         * Infers the [ExerciseCategory] and [MetricType] for an exercise name based on domain heuristics.
         */
        fun inferCategoryAndMetric(name: String): Pair<ExerciseCategory, MetricType> {
            val lower = name.trim().lowercase()

            // 1. Monostructural / Machine keywords
            if (lower.contains("skierg") ||
                lower.contains("rower") || lower.contains("rowing") || lower.contains("row") ||
                lower.contains("air bike") || lower.contains("echo bike") || lower.contains("assault bike") ||
                lower.contains("bike") || lower.contains("c2 bike") || lower.contains("concept2")
            ) {
                val metric = if (lower.contains("skierg") || lower.contains("bike") || lower.contains("cal")) {
                    MetricType.CALORIES
                } else if (lower.contains("run") || lower.contains("meter")) {
                    MetricType.DISTANCE
                } else {
                    MetricType.CALORIES
                }
                return Pair(ExerciseCategory.MACHINE, metric)
            }

            if (lower.contains("run") || lower.contains("sprint") || lower.contains("shuttle")) {
                return Pair(ExerciseCategory.MACHINE, MetricType.DISTANCE)
            }

            // 2. Gymnastics / Bodyweight keywords
            if (lower.contains("pullup") || lower.contains("pull-up") || lower.contains("pull up") ||
                lower.contains("chinup") || lower.contains("chin-up") ||
                lower.contains("pushup") || lower.contains("push-up") || lower.contains("push up") ||
                lower.contains("dip") || lower.contains("toes to bar") || lower.contains("t2b") ||
                lower.contains("muscle up") || lower.contains("muscle-up") || lower.contains("ring") ||
                lower.contains("hspu") || lower.contains("handstand") || lower.contains("rope climb") ||
                lower.contains("burpee") || lower.contains("box jump") || lower.contains("plank") ||
                lower.contains("situp") || lower.contains("sit-up") || lower.contains("air squat")
            ) {
                val metric = if (lower.contains("weighted")) MetricType.WEIGHT else MetricType.REPS
                return Pair(ExerciseCategory.GYMNASTICS, metric)
            }

            // 3. Accessory / Dumbbell / Kettlebell / Cable / Band / Body isolation
            if (lower.contains("db ") || lower.contains("dumbbell") || lower.contains("kb ") ||
                lower.contains("kettlebell") || lower.contains("curl") || lower.contains("fly") ||
                lower.contains("flys") || lower.contains("raise") || lower.contains("raises") ||
                lower.contains("banded") || lower.contains("cable") || lower.contains("lateral") ||
                lower.contains("triceps") || lower.contains("biceps") || lower.contains("extension") ||
                lower.contains("reverse fly") || lower.contains("ab wheel") || lower.contains("calves") ||
                lower.contains("calf") || lower.contains("hyperextension") || lower.contains("face pull") ||
                lower.contains("shrug") || lower.contains("lunge")
            ) {
                return Pair(ExerciseCategory.ACCESSORY, MetricType.WEIGHT)
            }

            // 4. Barbell / Olympic Weightlifting & Powerlifting keywords (default)
            return Pair(ExerciseCategory.BARBELL, MetricType.WEIGHT)
        }
    }

    /**
     * Resolves movements from a parsed document against the database via [ExerciseDao].
     */
    suspend fun resolveDocument(
        document: ParsedWorkoutDocument,
        exerciseDao: ExerciseDao
    ): WorkoutEntityResolutionResult {
        val existingLibrary = exerciseDao.getAllOnce()
        return resolveDocument(document, existingLibrary)
    }

    /**
     * Resolves movements from a list of movement names against the database via [ExerciseDao].
     */
    suspend fun resolveMovements(
        movementNames: List<String>,
        exerciseDao: ExerciseDao
    ): WorkoutEntityResolutionResult {
        val existingLibrary = exerciseDao.getAllOnce()
        return resolveMovements(movementNames, existingLibrary)
    }

    /**
     * In-memory pure resolution of movement names against existing [library].
     * Performs case-insensitive deduplication, separates existing from missing, and infers categories.
     */
    fun resolveMovements(
        movementNames: List<String>,
        library: List<Exercise>
    ): WorkoutEntityResolutionResult {
        val matchedExisting = mutableMapOf<String, Exercise>()
        val missingExercises = mutableListOf<Exercise>()
        val seenMissingNames = mutableSetOf<String>()

        val existingByName = library.associateBy { it.name.trim().lowercase() }

        for (rawName in movementNames) {
            val name = rawName.trim()
            if (name.isBlank()) continue

            val lower = name.lowercase()
            val exactMatch = existingByName[lower]
            if (exactMatch != null) {
                matchedExisting[name] = exactMatch
                continue
            }

            // Try entity grounder
            val grounded = grounder.resolveExerciseWithConfidence(name, library)
            if (grounded.isNotEmpty() && grounded.first().confidence >= 0.95) {
                matchedExisting[name] = grounded.first().exercise
                continue
            }

            // It's a new exercise: deduplicate in-memory across blocks
            if (!seenMissingNames.contains(lower)) {
                seenMissingNames.add(lower)
                val (category, metricType) = inferCategoryAndMetric(name)
                val newExercise = Exercise(
                    id = 0,
                    name = name,
                    category = category,
                    metricType = metricType,
                    unit = metricType.defaultUnit,
                    tracksRepMax = metricType.tracksRepMax
                )
                missingExercises.add(newExercise)
            }
        }

        return WorkoutEntityResolutionResult(
            matchedExisting = matchedExisting,
            missingExercises = missingExercises
        )
    }

    /**
     * Resolves an entire [ParsedWorkoutDocument], handling:
     * - In-transaction case-insensitive deduplication against existing library and across all blocks
     * - Grounding component exercises for complexes and supersets
     * - Barbell Complex composite isolation: registering a distinct composite [Exercise] for complexes
     *   to isolate 1RM progress curves without corrupting component lifts
     */
    fun resolveDocument(
        document: ParsedWorkoutDocument,
        library: List<Exercise>
    ): WorkoutEntityResolutionResult {
        val matchedExisting = mutableMapOf<String, Exercise>()
        val missingExercises = mutableListOf<Exercise>()
        val seenMissingNames = mutableSetOf<String>()
        val complexCompositeExercises = mutableMapOf<String, Exercise>()
        val blockResolutions = mutableListOf<ResolvedBlockEntity>()

        val existingByName = library.associateBy { it.name.trim().lowercase() }.toMutableMap()

        fun findOrProposeExercise(
            name: String,
            forceCategory: ExerciseCategory? = null,
            forceMetric: MetricType? = null
        ): Pair<Exercise, Boolean> {
            val trimmed = name.trim()
            val lower = trimmed.lowercase()

            val existing = existingByName[lower]
            if (existing != null) {
                matchedExisting[trimmed] = existing
                return Pair(existing, false)
            }

            val grounded = grounder.resolveExerciseWithConfidence(trimmed, existingByName.values.toList())
            if (grounded.isNotEmpty() && grounded.first().confidence >= 0.95) {
                val match = grounded.first().exercise
                matchedExisting[trimmed] = match
                return Pair(match, false)
            }

            val alreadyProposed = missingExercises.find { it.name.trim().equals(trimmed, ignoreCase = true) }
            if (alreadyProposed != null) {
                return Pair(alreadyProposed, true)
            }

            val (inferredCategory, inferredMetric) = inferCategoryAndMetric(trimmed)
            val category = forceCategory ?: inferredCategory
            val metric = forceMetric ?: inferredMetric
            val newEx = Exercise(
                id = 0,
                name = trimmed,
                category = category,
                metricType = metric,
                unit = metric.defaultUnit,
                tracksRepMax = metric.tracksRepMax
            )
            seenMissingNames.add(lower)
            missingExercises.add(newEx)
            return Pair(newEx, true)
        }

        for (block in document.blocks) {
            val isComplex = block.kind == BlockKind.COMPLEX || block.name.contains("+")

            if (isComplex) {
                val componentNames = if (block.movements.isNotEmpty()) {
                    block.movements
                } else if (block.name.contains("+")) {
                    block.name.split("+").map { it.trim() }.filter { it.isNotBlank() }
                } else {
                    listOf(block.name)
                }

                val resolvedComponents = componentNames.map { compName ->
                    val (compEx, isNew) = findOrProposeExercise(compName)
                    if (!isNew) {
                        matchedExisting[compName] = compEx
                    }
                    compEx
                }

                val compositeName = block.name.trim()
                val (compositeEx, isNewComposite) = findOrProposeExercise(
                    compositeName,
                    forceCategory = ExerciseCategory.BARBELL,
                    forceMetric = MetricType.WEIGHT
                )
                complexCompositeExercises[compositeName] = compositeEx

                blockResolutions.add(
                    ResolvedBlockEntity(
                        block = block,
                        mainExercise = compositeEx,
                        componentExercises = resolvedComponents,
                        isNewExercise = isNewComposite
                    )
                )
            } else {
                val (mainEx, isNew) = findOrProposeExercise(block.name)
                blockResolutions.add(
                    ResolvedBlockEntity(
                        block = block,
                        mainExercise = mainEx,
                        componentExercises = emptyList(),
                        isNewExercise = isNew
                    )
                )
            }
        }

        return WorkoutEntityResolutionResult(
            matchedExisting = matchedExisting,
            missingExercises = missingExercises,
            complexCompositeExercises = complexCompositeExercises,
            blockResolutions = blockResolutions
        )
    }
}
