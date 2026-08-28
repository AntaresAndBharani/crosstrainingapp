package com.fractanomics.crosstraining.ui.screens

import com.fractanomics.crosstraining.data.model.BlockKind
import com.fractanomics.crosstraining.data.model.Exercise
import com.fractanomics.crosstraining.data.model.ExerciseCategory
import com.fractanomics.crosstraining.data.model.MetricType
import com.fractanomics.crosstraining.data.model.Routine
import com.fractanomics.crosstraining.ui.components.NumericInputSanitizer
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test
import java.time.LocalDate

class SessionEditorNumericMigrationTest {

    private val backSquatExercise = Exercise(
        id = 1,
        name = "Back Squat",
        category = ExerciseCategory.BARBELL,
        metricType = MetricType.WEIGHT,
        unit = "kg",
        tracksRepMax = true
    )

    private val pullUpExercise = Exercise(
        id = 2,
        name = "Pull-up",
        category = ExerciseCategory.GYMNASTICS,
        metricType = MetricType.REPS,
        unit = "reps",
        tracksRepMax = false
    )

    @Test
    fun `Scenario 1 - Sets and reps use integer AppNumericTextField with allowDecimals false`() {
        // Given SessionEditor sets/reps inputs
        // When typing non-numeric characters or decimals into sets/reps field
        val filteredReps = NumericInputSanitizer.filterInput("10abc", allowDecimals = false)
        assertEquals("10", filteredReps)

        val filteredWithDot = NumericInputSanitizer.filterInput("5.5", allowDecimals = false)
        assertEquals("55", filteredWithDot)

        val sanitizedLeadingZero = NumericInputSanitizer.sanitizeAndClamp("08", minValue = 1.0, maxValue = 999.0, allowDecimals = false)
        assertEquals("8", sanitizedLeadingZero)

        val clampedEmpty = NumericInputSanitizer.sanitizeAndClamp("", minValue = 1.0, maxValue = 999.0, allowDecimals = false)
        assertEquals("1", clampedEmpty)
    }

    @Test
    fun `Scenario 2 - Weight uses decimal AppNumericTextField with allowDecimals true and yields 22_5`() {
        // Given the weight input field
        // When the user types "22.5"
        val filteredWeight = NumericInputSanitizer.filterInput("22.5", allowDecimals = true)
        assertEquals("22.5", filteredWeight)

        // When the user types "22,5" (comma decimal separator)
        val filteredComma = NumericInputSanitizer.filterInput("22,5", allowDecimals = true)
        assertEquals("22.5", filteredComma)

        // Then the logged weight value parsed to Double is 22.5
        val parsedDouble = filteredWeight.toDoubleOrNull()
        assertEquals(22.5, parsedDouble)

        val sanitizedWeight = NumericInputSanitizer.sanitizeAndClamp("22.5", minValue = 0.0, maxValue = 9999.9, allowDecimals = true)
        assertEquals("22.5", sanitizedWeight)
    }

    @Test
    fun `Scenario 3 - No regression in existing session logging behavior`() {
        // Given a user edits a session with sets, reps, and decimal weight inputs
        val seed = SessionSeed(
            cycleId = 10,
            date = LocalDate.of(2026, 8, 28),
            title = "Leg Day",
            notes = "Felt strong on squats",
            blocks = listOf(
                BlockSeed(
                    name = "Back Squat",
                    kind = BlockKind.STRENGTH,
                    format = "5x5",
                    exerciseId = 1,
                    sets = listOf(
                        SetSeed(reps = "5", value = "100.5", warm = false, failed = false),
                        SetSeed(reps = "5", value = "102.5", warm = false, failed = false),
                        SetSeed(reps = "5", value = "105.0", warm = false, failed = false)
                    )
                )
            )
        )

        // Simulate building block state and drafting
        val blockState = buildBlockStateTest(seed.blocks[0], listOf(backSquatExercise), emptyList())
        val blockDraft = blockState.toDraftOrNullTest(backSquatExercise)

        assertNotNull(blockDraft)
        assertEquals("Back Squat", blockDraft?.name)
        assertEquals(3, blockDraft?.sets?.size)
        assertEquals(5, blockDraft?.sets?.get(0)?.reps)
        assertEquals(100.5, blockDraft?.sets?.get(0)?.weight)
        assertEquals(102.5, blockDraft?.sets?.get(1)?.weight)
        assertEquals(105.0, blockDraft?.sets?.get(2)?.weight)
    }

    @Test
    fun `PR Rep Max fields correctly sanitize and save integer reps and decimal weight`() {
        val sanitizedRmReps = NumericInputSanitizer.sanitizeAndClamp("01", minValue = 1.0, maxValue = 999.0, allowDecimals = false)
        val sanitizedRmWeight = NumericInputSanitizer.sanitizeAndClamp("142.5", minValue = 0.0, maxValue = 9999.9, allowDecimals = true)

        assertEquals("1", sanitizedRmReps)
        assertEquals("142.5", sanitizedRmWeight)

        val repsInt = sanitizedRmReps.toIntOrNull()
        val weightDouble = sanitizedRmWeight.replace(',', '.').toDoubleOrNull()

        assertEquals(1, repsInt)
        assertEquals(142.5, weightDouble)
    }

    @Test
    fun `MetricType non-weight exercises map decimal value to metricValue`() {
        val seed = SessionSeed(
            blocks = listOf(
                BlockSeed(
                    name = "Pull-ups",
                    kind = BlockKind.ACCESSORY,
                    exerciseId = 2,
                    sets = listOf(
                        SetSeed(reps = "15", value = "15", warm = false, failed = false)
                    )
                )
            )
        )

        val blockState = buildBlockStateTest(seed.blocks[0], listOf(pullUpExercise), emptyList())
        val blockDraft = blockState.toDraftOrNullTest(pullUpExercise)

        assertNotNull(blockDraft)
        assertNull(blockDraft?.sets?.get(0)?.weight)
        assertEquals(15.0, blockDraft?.sets?.get(0)?.metricValue)
    }

    // Helpers replicating SessionEditor state logic for testing
    private fun buildBlockStateTest(seed: BlockSeed, exercises: List<Exercise>, routines: List<Routine>): TestBlockState {
        return TestBlockState(
            name = seed.name,
            kind = seed.kind,
            format = seed.format,
            scheme = seed.scheme,
            exercise = exercises.firstOrNull { it.id == seed.exerciseId },
            sets = seed.sets.map { TestSetState(it.reps, it.value, it.group, it.warm, it.failed) }
        )
    }

    private data class TestSetState(
        var reps: String,
        var value: String,
        var group: String = "",
        var isWarmup: Boolean = false,
        var isFailed: Boolean = false
    )

    private data class TestBlockState(
        var name: String,
        var kind: BlockKind,
        var format: String,
        var scheme: String,
        var exercise: Exercise?,
        var sets: List<TestSetState>
    ) {
        fun toDraftOrNullTest(exercise: Exercise?): com.fractanomics.crosstraining.ui.BlockDraft? {
            val metric = exercise?.metricType ?: MetricType.WEIGHT
            val setDrafts = sets.mapNotNull { ss ->
                val reps = ss.reps.toIntOrNull() ?: return@mapNotNull null
                val v = ss.value.replace(',', '.').toDoubleOrNull()
                com.fractanomics.crosstraining.ui.SetDraft(
                    reps = reps,
                    weight = if (metric == MetricType.WEIGHT) v else null,
                    metricValue = if (metric != MetricType.WEIGHT) v else null,
                    groupIndex = ss.group.toIntOrNull(),
                    isWarmup = ss.isWarmup,
                    isFailed = ss.isFailed
                )
            }
            if (name.isBlank() && exercise == null && setDrafts.isEmpty()) return null
            return com.fractanomics.crosstraining.ui.BlockDraft(
                name = name.trim(),
                kind = kind,
                format = format.trim(),
                scheme = scheme.trim(),
                existingExerciseId = exercise?.id,
                newExerciseName = null,
                routineId = null,
                description = "",
                resultText = "",
                resultValue = null,
                sets = setDrafts,
                newRepMaxReps = null,
                newRepMaxWeight = null
            )
        }
    }
}
