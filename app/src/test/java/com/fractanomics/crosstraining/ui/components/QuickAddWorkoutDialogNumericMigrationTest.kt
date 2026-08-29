package com.fractanomics.crosstraining.ui.components

import com.fractanomics.crosstraining.data.model.Exercise
import com.fractanomics.crosstraining.data.model.ExerciseCategory
import com.fractanomics.crosstraining.data.model.MetricType
import com.fractanomics.crosstraining.util.WorkoutParser
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Test

class QuickAddWorkoutDialogNumericMigrationTest {

    private val snatchExercise = Exercise(
        id = 1,
        name = "Snatch",
        category = ExerciseCategory.BARBELL,
        metricType = MetricType.WEIGHT,
        unit = "kg",
        tracksRepMax = true
    )

    @Test
    fun `setsScheme domain clamping and filtering accepts valid schemes and digits`() {
        // Given valid scheme input
        val filtered = NumericInputSanitizer.filterInput(
            text = "5x3",
            allowScheme = true
        )
        assertEquals("5x3", filtered)

        // Given scheme with wave dashes
        val waveFiltered = NumericInputSanitizer.filterInput(
            text = "3-2-1",
            allowScheme = true
        )
        assertEquals("3-2-1", waveFiltered)

        // Given invalid characters
        val invalidFiltered = NumericInputSanitizer.filterInput(
            text = "5x3abc#",
            allowScheme = true
        )
        assertEquals("5x3", invalidFiltered)

        // Clamping single number within domain [1, 999]
        val clampedWithin = NumericInputSanitizer.sanitizeAndClamp(
            text = "5",
            minValue = 1.0,
            maxValue = 999.0,
            allowScheme = true
        )
        assertEquals("5", clampedWithin)

        val clampedEmpty = NumericInputSanitizer.sanitizeAndClamp(
            text = "",
            minValue = 1.0,
            maxValue = 999.0,
            allowScheme = true
        )
        assertEquals("1", clampedEmpty)
    }

    @Test
    fun `weightInput decimal support and domain clamping with lists and ranges`() {
        // Given decimal input
        val decimalFiltered = NumericInputSanitizer.filterInput(
            text = "22.5",
            allowDecimals = true,
            allowRangeOrList = true
        )
        assertEquals("22.5", decimalFiltered)

        // Given comma list of weights
        val listFiltered = NumericInputSanitizer.filterInput(
            text = "60, 65, 70, 75, 80",
            allowDecimals = true,
            allowRangeOrList = true
        )
        assertEquals("60, 65, 70, 75, 80", listFiltered)

        // Given range of weights
        val rangeFiltered = NumericInputSanitizer.filterInput(
            text = "60-80",
            allowDecimals = true,
            allowRangeOrList = true
        )
        assertEquals("60-80", rangeFiltered)

        // Sanitizing single decimal and sanitizing leading zeros
        val sanitizedLeadingZero = NumericInputSanitizer.sanitizeAndClamp(
            text = "05.5",
            minValue = 0.0,
            maxValue = 999.9,
            allowDecimals = true,
            allowRangeOrList = true
        )
        assertEquals("5.5", sanitizedLeadingZero)

        // Clamping upper bound
        val clampedMax = NumericInputSanitizer.sanitizeAndClamp(
            text = "1500.0",
            minValue = 0.0,
            maxValue = 999.9,
            allowDecimals = true,
            allowRangeOrList = true
        )
        assertEquals("999.9", clampedMax)
    }

    @Test
    fun `no regression in quick add workout creation with structured inputs`() {
        // Given a user creates a workout with setsScheme and weights
        val setsInput = "5x3"
        val weightInput = "60, 65, 70, 75, 80"

        val sanitizedSets = NumericInputSanitizer.sanitizeAndClamp(
            text = setsInput,
            minValue = 1.0,
            maxValue = 999.0,
            allowScheme = true
        )
        val sanitizedWeights = NumericInputSanitizer.sanitizeAndClamp(
            text = weightInput,
            minValue = 0.0,
            maxValue = 999.9,
            allowDecimals = true,
            allowRangeOrList = true
        )

        val parsed = WorkoutParser.parseStructured(
            exercise = snatchExercise,
            newExerciseName = "",
            routine = null,
            format = "E2MOM",
            setsInput = sanitizedSets,
            weightInput = sanitizedWeights
        )

        assertNotNull(parsed)
        assertEquals("Snatch", parsed.name)
        assertEquals("E2MOM", parsed.format)
        assertEquals(5, parsed.sets.size)
        assertEquals(listOf(3, 3, 3, 3, 3), parsed.sets.map { it.reps })
        assertEquals(listOf(60.0, 65.0, 70.0, 75.0, 80.0), parsed.sets.map { it.weight })
    }

    @Test
    fun `no regression in quick add workout creation with decimal weights and wave scheme`() {
        val setsInput = "3-2-1"
        val weightInput = "22.5, 25.0, 27.5"

        val sanitizedSets = NumericInputSanitizer.sanitizeAndClamp(
            text = setsInput,
            minValue = 1.0,
            maxValue = 999.0,
            allowScheme = true
        )
        val sanitizedWeights = NumericInputSanitizer.sanitizeAndClamp(
            text = weightInput,
            minValue = 0.0,
            maxValue = 999.9,
            allowDecimals = true,
            allowRangeOrList = true
        )

        val parsed = WorkoutParser.parseStructured(
            exercise = snatchExercise,
            newExerciseName = "",
            routine = null,
            format = "Rest 90s",
            setsInput = sanitizedSets,
            weightInput = sanitizedWeights
        )

        assertNotNull(parsed)
        assertEquals(3, parsed.sets.size)
        assertEquals(listOf(3, 2, 1), parsed.sets.map { it.reps })
        assertEquals(listOf(22.5, 25.0, 27.5), parsed.sets.map { it.weight })
    }
}
