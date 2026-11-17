package com.fractanomics.crosstraining.ui.screens

import com.fractanomics.crosstraining.data.model.Exercise
import com.fractanomics.crosstraining.data.model.ExerciseCategory
import com.fractanomics.crosstraining.data.model.MetricType
import com.fractanomics.crosstraining.util.AvailableBlock
import com.fractanomics.crosstraining.util.AvailableBlockExercise
import com.fractanomics.crosstraining.util.DailyRoutine
import com.fractanomics.crosstraining.util.DailyRoutineItem
import com.fractanomics.crosstraining.util.TrackedPrSlot
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class LibraryDailyRoutinesTest {

    private val backSquat = Exercise(1L, "Back Squat", ExerciseCategory.BARBELL, MetricType.WEIGHT, "kg", true)
    private val pullUps = Exercise(2L, "Pull-ups", ExerciseCategory.GYMNASTICS, MetricType.REPS, "reps", false)
    private val cleanBlock = AvailableBlock(
        id = 10L,
        title = "Monday Clean Session",
        instructions = "Clean complex",
        exercises = listOf(AvailableBlockExercise(3L, "Clean", true)),
        trackedPrs = listOf(TrackedPrSlot(3L, "Clean", 1))
    )
    private val snatchBlock = AvailableBlock(
        id = 11L,
        title = "Snatch Balance Complex",
        instructions = "E4MOM Snatch Balance",
        exercises = listOf(AvailableBlockExercise(4L, "Snatch", true)),
        trackedPrs = listOf(TrackedPrSlot(4L, "Snatch", 2))
    )

    @Test
    fun `formatLibraryTabLabels produces concise 3-tab labels in Routines Blocks Exercises order`() {
        val labels = formatLibraryTabLabels(routinesCount = 3, blocksCount = 2, exercisesCount = 36)
        assertEquals(listOf("Routines (3)", "Blocks (2)", "Exercises (36)"), labels)
    }

    @Test
    fun `DailyRoutineComposerState enforces title and non-empty items supports reorder notes and removal`() {
        val state = DailyRoutineComposerState()
        assertFalse(state.canSave)
        assertNull(state.validationErrorMessage)

        state.attemptedSubmit = true
        assertNotNull(state.validationErrorMessage)

        state.title = "Monday Full Session"
        assertFalse(state.canSave)

        state.addBlock(cleanBlock)
        assertTrue(state.canSave)
        assertNull(state.validationErrorMessage)

        state.addExercise(backSquat)
        state.addExercise(pullUps)
        assertEquals(3, state.items.size)

        val squatUid = state.items[1].uid
        state.updateExerciseNote(squatUid, "4x8 @ RPE 8")

        // Move Pull-ups up before Back Squat
        state.moveUp(2)
        val reordered = state.toDailyRoutineItems()
        assertTrue(reordered[0] is DailyRoutineItem.BlockRef)
        assertEquals("Pull-ups", (reordered[1] as DailyRoutineItem.StandaloneExercise).exerciseName)
        assertEquals("Back Squat", (reordered[2] as DailyRoutineItem.StandaloneExercise).exerciseName)
        assertEquals("4x8 @ RPE 8", (reordered[2] as DailyRoutineItem.StandaloneExercise).note)

        // Move Pull-ups back down and remove
        state.moveDown(1)
        state.removeAt(2)
        assertEquals(2, state.items.size)
    }

    @Test
    fun `formatDailyRoutineItemChipLabel visibleRoutineChips and telemetry match specification`() {
        val items = listOf(
            DailyRoutineItem.BlockRef(cleanBlock.id, cleanBlock.title, cleanBlock),
            DailyRoutineItem.BlockRef(snatchBlock.id, snatchBlock.title, snatchBlock),
            DailyRoutineItem.StandaloneExercise(backSquat.id, backSquat.name, "4x8", emptyList(), backSquat),
            DailyRoutineItem.StandaloneExercise(pullUps.id, pullUps.name, "", emptyList(), pullUps),
            DailyRoutineItem.StandaloneExercise(10L, "Row", "500m"),
            DailyRoutineItem.StandaloneExercise(11L, "Plank", "3x60s"),
            DailyRoutineItem.StandaloneExercise(12L, "GHD Sit-up", "3x15"),
            DailyRoutineItem.StandaloneExercise(13L, "Calf Raise", "3x15")
        )

        assertEquals("BLOCK: Monday Clean Session", formatDailyRoutineItemChipLabel(items[0]))
        assertEquals("EX: Back Squat (4x8)", formatDailyRoutineItemChipLabel(items[2]))
        assertEquals("EX: Pull-ups", formatDailyRoutineItemChipLabel(items[3]))

        val (collapsed, hiddenCount) = visibleRoutineChips(items, expanded = false)
        assertEquals(6, collapsed.size)
        assertEquals(2, hiddenCount)

        val (expanded, expandedHidden) = visibleRoutineChips(items, expanded = true)
        assertEquals(8, expanded.size)
        assertEquals(0, expandedHidden)

        val routine = DailyRoutine(
            id = 1L,
            title = "Monday Full Session",
            notes = "",
            items = items.take(4)
        )
        assertEquals("2 Blocks · 2 Exercises · Last: 14 Nov", formatDailyRoutineTelemetry(routine, "14 Nov"))
        assertEquals("2 Blocks · 2 Exercises · Not logged yet", formatDailyRoutineTelemetry(routine, null))
    }
}
