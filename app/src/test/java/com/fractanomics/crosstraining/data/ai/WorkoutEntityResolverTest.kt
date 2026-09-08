package com.fractanomics.crosstraining.data.ai

import com.fractanomics.crosstraining.data.model.BlockKind
import com.fractanomics.crosstraining.data.model.Exercise
import com.fractanomics.crosstraining.data.model.ExerciseCategory
import com.fractanomics.crosstraining.data.model.MetricType
import com.fractanomics.crosstraining.util.ParsedDocumentBlock
import com.fractanomics.crosstraining.util.ParsedDocumentSet
import com.fractanomics.crosstraining.util.ParsedWorkoutDocument
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/**
 * Unit test suite verifying [WorkoutEntityResolver] adhering to Issue #516 acceptance criteria:
 * - Scenario 1: Missing Exercise Grounding, Classification and In-Transaction Deduplication
 * - Scenario 2: Barbell Complex Composite Isolation
 * - Category and Metric inference heuristics
 */
class WorkoutEntityResolverTest {

    private lateinit var resolver: WorkoutEntityResolver
    private lateinit var existingLibrary: List<Exercise>

    @Before
    fun setUp() {
        resolver = WorkoutEntityResolver.DEFAULT
        existingLibrary = listOf(
            Exercise(id = 1L, name = "Clean", category = ExerciseCategory.BARBELL, metricType = MetricType.WEIGHT),
            Exercise(id = 2L, name = "Front Squat", category = ExerciseCategory.BARBELL, metricType = MetricType.WEIGHT),
            Exercise(id = 3L, name = "Pullups", category = ExerciseCategory.GYMNASTICS, metricType = MetricType.REPS)
        )
    }

    @Test
    fun `Scenario 1 - Missing Exercise Grounding, Classification and In-Transaction Deduplication`() {
        // Given parsed movements "Clean", "Front Squat", "Romanian Deadlift", "DB Twist Curl", and "SkiErg"
        val movements = listOf("Clean", "Front Squat", "Romanian Deadlift", "DB Twist Curl", "SkiErg", "Romanian Deadlift")
        // And "Clean" and "Front Squat" exist in Room, while the others are new

        // When WorkoutEntityResolver resolves the movements
        val result = resolver.resolveMovements(movements, existingLibrary)

        // Then existing exercises are matched from Library
        assertEquals(2, result.matchedExisting.size)
        assertEquals(1L, result.matchedExisting["Clean"]?.id)
        assertEquals(2L, result.matchedExisting["Front Squat"]?.id)

        // And new exercises are inferred: Romanian Deadlift (BARBELL, WEIGHT), DB Twist Curl (ACCESSORY, WEIGHT), SkiErg (MACHINE, CALORIES)
        assertEquals(3, result.missingExercises.size)

        val rdl = result.missingExercises.find { it.name == "Romanian Deadlift" }
        assertNotNull(rdl)
        assertEquals(ExerciseCategory.BARBELL, rdl!!.category)
        assertEquals(MetricType.WEIGHT, rdl.metricType)

        val curl = result.missingExercises.find { it.name == "DB Twist Curl" }
        assertNotNull(curl)
        assertEquals(ExerciseCategory.ACCESSORY, curl!!.category)
        assertEquals(MetricType.WEIGHT, curl.metricType)

        val skiErg = result.missingExercises.find { it.name == "SkiErg" }
        assertNotNull(skiErg)
        assertEquals(ExerciseCategory.MACHINE, skiErg!!.category)
        assertEquals(MetricType.CALORIES, skiErg.metricType)

        // And duplicate exercise names across multiple blocks are deduplicated to a single creation record
        val rdlCount = result.missingExercises.count { it.name.equals("Romanian Deadlift", ignoreCase = true) }
        assertEquals(1, rdlCount)
    }

    @Test
    fun `Scenario 2 - Barbell Complex Composite Isolation`() {
        // Given a complex block "Clean + Hang Clean + Front Squat + Push to OverHead"
        val complexName = "Clean + Hang Clean + Front Squat + Push to OverHead"
        val doc = ParsedWorkoutDocument(
            routineTitle = "Test Complex Workout",
            blocks = listOf(
                ParsedDocumentBlock(
                    name = complexName,
                    section = "Strengh & Power block",
                    kind = BlockKind.COMPLEX,
                    format = "E3MOM",
                    movements = listOf("Clean", "Hang Clean", "Front Squat", "Push to OverHead"),
                    sets = listOf(
                        ParsedDocumentSet(reps = 1, weight = 52.5),
                        ParsedDocumentSet(reps = 1, weight = 55.0)
                    )
                )
            )
        )

        // When WorkoutEntityResolver processes the block
        val result = resolver.resolveDocument(doc, existingLibrary)

        // Then a composite Exercise "Clean + Hang Clean + Front Squat + Push to OverHead" is cataloged
        assertEquals(1, result.blockResolutions.size)
        val res = result.blockResolutions.first()
        assertEquals(complexName, res.mainExercise.name)
        assertEquals(ExerciseCategory.BARBELL, res.mainExercise.category)
        assertEquals(MetricType.WEIGHT, res.mainExercise.metricType)
        assertTrue(res.isNewExercise)

        // And component exercises are resolved (Clean & Front Squat from library, Hang Clean & Push to OverHead as missing)
        assertEquals(4, res.componentExercises.size)
        assertEquals("Clean", res.componentExercises[0].name)
        assertEquals(1L, res.componentExercises[0].id)
        assertEquals("Hang Clean", res.componentExercises[1].name)
        assertEquals("Front Squat", res.componentExercises[2].name)
        assertEquals(2L, res.componentExercises[2].id)
        assertEquals("Push to OverHead", res.componentExercises[3].name)

        // And component Clean and Front Squat exist in library without modification
        assertEquals(1L, existingLibrary.find { it.name == "Clean" }?.id)
        assertEquals(2L, existingLibrary.find { it.name == "Front Squat" }?.id)
    }

    @Test
    fun `category and metric inference covers all modalities`() {
        assertEquals(Pair(ExerciseCategory.MACHINE, MetricType.CALORIES), WorkoutEntityResolver.inferCategoryAndMetric("10 Cal SkiErg"))
        assertEquals(Pair(ExerciseCategory.MACHINE, MetricType.CALORIES), WorkoutEntityResolver.inferCategoryAndMetric("Concept2 Rower"))
        assertEquals(Pair(ExerciseCategory.MACHINE, MetricType.CALORIES), WorkoutEntityResolver.inferCategoryAndMetric("Echo Bike"))
        assertEquals(Pair(ExerciseCategory.MACHINE, MetricType.DISTANCE), WorkoutEntityResolver.inferCategoryAndMetric("400m Run"))
        assertEquals(Pair(ExerciseCategory.GYMNASTICS, MetricType.REPS), WorkoutEntityResolver.inferCategoryAndMetric("Strict Pull-ups"))
        assertEquals(Pair(ExerciseCategory.GYMNASTICS, MetricType.REPS), WorkoutEntityResolver.inferCategoryAndMetric("Toes to Bar"))
        assertEquals(Pair(ExerciseCategory.ACCESSORY, MetricType.WEIGHT), WorkoutEntityResolver.inferCategoryAndMetric("Dumbbell Lateral Raise"))
        assertEquals(Pair(ExerciseCategory.ACCESSORY, MetricType.WEIGHT), WorkoutEntityResolver.inferCategoryAndMetric("Banded Reverse Flys"))
        assertEquals(Pair(ExerciseCategory.ACCESSORY, MetricType.WEIGHT), WorkoutEntityResolver.inferCategoryAndMetric("Calves Raises"))
        assertEquals(Pair(ExerciseCategory.BARBELL, MetricType.WEIGHT), WorkoutEntityResolver.inferCategoryAndMetric("Snatch"))
        assertEquals(Pair(ExerciseCategory.BARBELL, MetricType.WEIGHT), WorkoutEntityResolver.inferCategoryAndMetric("Back Squat"))
    }
}
