package com.fractanomics.crosstraining.util

import com.fractanomics.crosstraining.data.model.Exercise
import com.fractanomics.crosstraining.data.model.ExerciseCategory
import com.fractanomics.crosstraining.data.model.MetricType
import com.fractanomics.crosstraining.data.model.Routine
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Test

class WorkoutParserTest {

    private val sampleExercises = listOf(
        Exercise(id = 1, name = "Snatch", category = ExerciseCategory.BARBELL, metricType = MetricType.WEIGHT, unit = "kg", tracksRepMax = true),
        Exercise(id = 2, name = "Clean & Jerk", category = ExerciseCategory.BARBELL, metricType = MetricType.WEIGHT, unit = "kg", tracksRepMax = true),
        Exercise(id = 3, name = "Back Squat", category = ExerciseCategory.BARBELL, metricType = MetricType.WEIGHT, unit = "kg", tracksRepMax = true)
    )

    private val sampleRoutines = listOf(
        Routine(id = 10, name = "3-Position Snatch", mainExerciseId = 1, defaultFormat = "E2MOM")
    )

    @Test
    fun `test parseRepsAndSets sets x reps`() {
        val (reps, count) = WorkoutParser.parseRepsAndSets("5x3")
        assertEquals(5, count)
        assertEquals(listOf(3, 3, 3, 3, 3), reps)
    }

    @Test
    fun `test parseRepsAndSets wave scheme`() {
        val (reps, count) = WorkoutParser.parseRepsAndSets("3-2-1-3-2-1")
        assertEquals(6, count)
        assertEquals(listOf(3, 2, 1, 3, 2, 1), reps)
    }

    @Test
    fun `test parseWeights list`() {
        val weights = WorkoutParser.parseWeights("60, 65, 70, 75, 80", 5)
        assertEquals(listOf(60.0, 65.0, 70.0, 75.0, 80.0), weights)
    }

    @Test
    fun `test parseWeights range interpolation`() {
        val weights = WorkoutParser.parseWeights("60-80", 5)
        assertEquals(listOf(60.0, 65.0, 70.0, 75.0, 80.0), weights)
    }

    @Test
    fun `test parseFreeform complete string`() {
        val parsed = WorkoutParser.parseFreeform("Snatch 5x3 @ 60, 65, 70, 75, 80 kg E2MOM", sampleExercises, sampleRoutines)
        assertEquals("Snatch", parsed.name)
        assertEquals(1L, parsed.existingExerciseId)
        assertEquals("E2MOM", parsed.format)
        assertEquals(5, parsed.sets.size)
        assertEquals(60.0, parsed.sets[0].weight)
        assertEquals(80.0, parsed.sets[4].weight)
        assertEquals(3, parsed.sets[0].reps)
    }

    @Test
    fun `test parseFreeform wave snatch routine`() {
        val parsed = WorkoutParser.parseFreeform("3-Position Snatch 3-2-1 @ 70, 75, 80kg", sampleExercises, sampleRoutines)
        assertEquals("3-Position Snatch", parsed.name)
        assertEquals(10L, parsed.routineId)
        assertEquals(1L, parsed.existingExerciseId)
        assertEquals(3, parsed.sets.size)
        assertEquals(listOf(3, 2, 1), parsed.sets.map { it.reps })
    }

    @Test
    fun `test parseStructured input`() {
        val parsed = WorkoutParser.parseStructured(
            exercise = sampleExercises[2],
            newExerciseName = "",
            routine = null,
            format = "Rest 2 min",
            setsInput = "4x5",
            weightInput = "100-120"
        )
        assertEquals("Back Squat", parsed.name)
        assertEquals("Rest 2 min", parsed.format)
        assertEquals(4, parsed.sets.size)
        assertEquals(100.0, parsed.sets[0].weight)
        assertEquals(120.0, parsed.sets[3].weight)
        assertEquals(5, parsed.sets[0].reps)
    }
}
