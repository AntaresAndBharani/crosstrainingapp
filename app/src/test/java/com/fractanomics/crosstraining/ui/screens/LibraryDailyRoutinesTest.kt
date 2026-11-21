package com.fractanomics.crosstraining.ui.screens

import com.fractanomics.crosstraining.data.model.Exercise
import com.fractanomics.crosstraining.data.model.ExerciseCategory
import com.fractanomics.crosstraining.data.model.MetricType
import com.fractanomics.crosstraining.util.AvailableBlock
import com.fractanomics.crosstraining.util.AvailableBlockExercise
import com.fractanomics.crosstraining.util.DailyRoutine
import com.fractanomics.crosstraining.util.DailyRoutineItem
import com.fractanomics.crosstraining.util.ExerciseTargetMetric
import com.fractanomics.crosstraining.util.RoutineExerciseTargetSpec
import com.fractanomics.crosstraining.util.TargetMode
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
    fun `formatLibraryTabLabels produces concise 3-tab labels in Routines Complexes Exercises order`() {
        val labels = formatLibraryTabLabels(routinesCount = 3, blocksCount = 2, exercisesCount = 36)
        assertEquals(listOf("Routines (3)", "Complexes (2)", "Exercises (36)"), labels)
        assertEquals("Routines, 3 items, Tab 1 of 3", formatLibraryTabContentDescription(0, 3))
        assertEquals("Complexes, 2 items, Tab 2 of 3", formatLibraryTabContentDescription(1, 2))
        assertEquals("Exercises, 36 items, Tab 3 of 3", formatLibraryTabContentDescription(2, 36))
    }

    @Test
    fun `responsive library helpers and complex editor validation enforce scaling and strict inputs`() {
        assertFalse(shouldUseScrollableLibraryTabs(1.0f))
        assertFalse(shouldUseScrollableLibraryTabs(1.19f))
        assertTrue(shouldUseScrollableLibraryTabs(1.2f))
        assertTrue(shouldUseScrollableLibraryTabs(1.5f))

        assertFalse(shouldStackCompactActions(1.3f))
        assertTrue(shouldStackCompactActions(1.4f))
        assertTrue(shouldStackCompactActions(2.0f))

        assertEquals("Search routines...", librarySearchPlaceholder(0))
        assertEquals("Search complexes...", librarySearchPlaceholder(1))
        assertEquals("Search exercises...", librarySearchPlaceholder(2))

        assertFalse(canSaveComplexEditor("   ", 0))
        assertFalse(canSaveComplexEditor("Clean Complex", 0))
        assertFalse(canSaveComplexEditor("   ", 2))
        assertTrue(canSaveComplexEditor("Clean Complex", 1))

        assertNull(complexEditorValidationError("", 0, showValidation = false))
        assertEquals(
            "Enter a complex name and select at least 1 exercise.",
            complexEditorValidationError("Clean Complex", 0, showValidation = true)
        )
        assertEquals(
            "Enter a complex name and select at least 1 exercise.",
            complexEditorValidationError("  ", 2, showValidation = true)
        )
        assertNull(complexEditorValidationError("Clean Complex", 1, showValidation = true))
    }

    @Test
    fun `DailyRoutineComposerState enforces title and non-empty items supports reorder notes and removal`() {
        val state = DailyRoutineComposerState()
        assertFalse(state.canSave)
        assertNull(state.validationErrorMessage)

        state.attemptedSubmit = true
        assertEquals(
            "Add a routine title and at least 1 complex or exercise.",
            state.validationErrorMessage
        )

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

        assertEquals("COMPLEX: Monday Clean Session", formatDailyRoutineItemChipLabel(items[0]))
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
        assertEquals("2 Complexes · 2 Exercises · Last: 14 Nov", formatDailyRoutineTelemetry(routine, "14 Nov"))
        assertEquals("2 Complexes · 2 Exercises · Not logged yet", formatDailyRoutineTelemetry(routine, null))
        val singleComplexRoutine = DailyRoutine(
            id = 2L,
            title = "Single Complex Day",
            notes = "",
            items = listOf(items[0], items[2])
        )
        assertEquals("1 Complex · 1 Exercise · Not logged yet", formatDailyRoutineTelemetry(singleComplexRoutine, null))
    }

    @Test
    fun `DailyRoutineComposerState supports optional multi-metric targets clear metric cache and all-zero validation`() {
        val airBike = Exercise(5L, "Air Bike", ExerciseCategory.MACHINE, MetricType.CALORIES, "cal", false)
        val state = DailyRoutineComposerState()
        state.title = "Conditioning + Strength"
        state.addBlock(cleanBlock)
        state.addExercise(airBike)

        val blockUid = state.items[0].uid
        val bikeUid = state.items[1].uid

        // Targets start unset and routine is saveable without configuring targets
        val initialItems = state.toDailyRoutineItems()
        assertFalse((initialItems[0] as DailyRoutineItem.BlockRef).hasConfiguredTargets)
        assertFalse((initialItems[1] as DailyRoutineItem.StandaloneExercise).hasConfiguredTarget)
        assertTrue(state.canSave)

        // Enable targets on Air Bike -> auto-defaults to Calories (15 cal, 4 sets)
        state.enableExerciseTargets(bikeUid)
        var bikeItem = state.items[1].item as DailyRoutineItem.StandaloneExercise
        assertTrue(bikeItem.hasConfiguredTarget)
        assertEquals(4, bikeItem.targetSetsCount)
        assertEquals(ExerciseTargetMetric.CALORIES, bikeItem.targetSpec!!.metric)
        assertEquals(15, bikeItem.targetSpec!!.fixedValue)

        // Switch to Per-Set with a 0 (Skip) round, then switch to Time and back to Calories -> preserves cache
        state.updateExerciseTargetMode(bikeUid, TargetMode.PER_SET)
        state.updateExercisePerSetValue(bikeUid, setIndex = 2, newValue = 12)
        state.updateExercisePerSetValue(bikeUid, setIndex = 3, newValue = 0)
        state.updateExerciseMetric(bikeUid, ExerciseTargetMetric.TIME_SECONDS)
        state.updateExerciseFixedValue(bikeUid, 45)
        state.updateExerciseMetric(bikeUid, ExerciseTargetMetric.CALORIES)
        bikeItem = state.items[1].item as DailyRoutineItem.StandaloneExercise
        assertEquals(listOf(15, 15, 12, 0), bikeItem.targetSpec!!.perSetValues)
        assertEquals("EX: Air Bike (15•15•12•0 cal)", formatDailyRoutineItemChipLabel(bikeItem))

        // Setting all sets to 0 invalidates save with inline guidance
        state.updateExercisePerSetValue(bikeUid, 0, 0)
        state.updateExercisePerSetValue(bikeUid, 1, 0)
        state.updateExercisePerSetValue(bikeUid, 2, 0)
        assertTrue(state.hasInvalidTargets)
        assertFalse(state.canSave)
        assertEquals(DAILY_ROUTINE_ALL_ZERO_TARGET_ERROR, state.validationErrorMessage)

        // Clearing targets restores unset saveable state
        state.clearExerciseTargets(bikeUid)
        bikeItem = state.items[1].item as DailyRoutineItem.StandaloneExercise
        assertFalse(bikeItem.hasConfiguredTarget)
        assertTrue(state.canSave)

        // BlockRef per-routine target configuration + shared sets resize
        state.enableBlockTargets(blockUid, mapOf(3L to Exercise(3L, "Clean", ExerciseCategory.BARBELL, MetricType.WEIGHT)))
        state.updateBlockSetsCount(blockUid, 5)
        state.updateBlockExerciseTargetMode(blockUid, 3L, "Clean", TargetMode.PER_SET)
        state.applyBlockExercisePattern(blockUid, 3L, "Clean", ExerciseSetPattern.ALTERNATE_ZEROS)
        val blockItem = state.items[0].item as DailyRoutineItem.BlockRef
        assertTrue(blockItem.hasConfiguredTargets)
        assertEquals(5, blockItem.targetSetsCount)
        assertEquals(listOf(8, 0, 8, 0, 8), blockItem.exerciseTargets[3L]!!.perSetValues)

        state.clearBlockTargets(blockUid)
        assertFalse((state.items[0].item as DailyRoutineItem.BlockRef).hasConfiguredTargets)
    }
}
