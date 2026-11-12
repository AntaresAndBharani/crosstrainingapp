package com.fractanomics.crosstraining.ui.screens

import com.fractanomics.crosstraining.data.model.BlockKind
import com.fractanomics.crosstraining.data.model.Exercise
import com.fractanomics.crosstraining.data.model.ExerciseCategory
import com.fractanomics.crosstraining.data.model.MetricType
import com.fractanomics.crosstraining.data.model.RoutineWithBlocks
import com.fractanomics.crosstraining.util.ExerciseTargetMetric
import com.fractanomics.crosstraining.util.RoutineTargetCodec
import com.fractanomics.crosstraining.util.TargetMode
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class RoutineBuilderUiStateTest {

    private val bench = Exercise(id = 1L, name = "Bench Press", category = ExerciseCategory.BARBELL, metricType = MetricType.WEIGHT)
    private val bike = Exercise(id = 2L, name = "Air Bike", category = ExerciseCategory.MACHINE, metricType = MetricType.CALORIES, unit = "cal", tracksRepMax = false)
    private val row = Exercise(id = 3L, name = "Concept2 Row", category = ExerciseCategory.MACHINE, metricType = MetricType.DISTANCE, unit = "m", tracksRepMax = false)
    private val plank = Exercise(id = 4L, name = "Plank", category = ExerciseCategory.GYMNASTICS, metricType = MetricType.TIME, unit = "sec", tracksRepMax = false)
    private val exercises = listOf(bench, bike, row, plank)

    @Test
    fun `new routine starts clean with Routine Info and Block 1 expanded and Save disabled`() {
        val state = RoutineBuilderUiState.fromRoutine(original = null, exercises = exercises)

        assertTrue(state.isRoutineInfoExpanded)
        assertEquals(1, state.blocks.size)
        assertEquals(state.blocks.first().uiKey, state.expandedBlockKey)
        assertEquals(BlockKind.WEIGHTLIFTING, state.blocks.first().kind)
        assertEquals("Sets x Reps", state.blocks.first().format)
        assertEquals(4, state.blocks.first().setsCount)
        assertFalse(state.isDirty)
        assertFalse(state.canSave)
    }

    @Test
    fun `exclusive block expansion collapses previous block and auto-collapses named Routine Info`() {
        var state = RoutineBuilderUiState.fromRoutine(original = null, exercises = exercises)
            .updateRoutineName("Monday Conditioning")

        assertTrue(state.isDirty)
        val block1Key = state.blocks[0].uiKey

        // Adding Block 2 collapses Block 1 and auto-collapses Routine Info
        state = state.addBlock()
        val block2Key = state.blocks[1].uiKey
        assertFalse(state.isRoutineInfoExpanded)
        assertEquals(block2Key, state.expandedBlockKey)

        // Expanding Block 1 collapses Block 2
        state = state.toggleBlockExpanded(block1Key)
        assertEquals(block1Key, state.expandedBlockKey)

        // Collapse All collapses Routine Info and all blocks
        state = state.collapseAll()
        assertFalse(state.isRoutineInfoExpanded)
        assertNull(state.expandedBlockKey)
    }

    @Test
    fun `selecting execution format applies default sets while allowing manual override`() {
        var state = RoutineBuilderUiState.fromRoutine(original = null, exercises = exercises)
        val bKey = state.blocks.first().uiKey

        state = state.selectBlockFormat(bKey, "AMRAP")
        assertEquals(1, state.blocks.first().setsCount)
        assertNotNull(state.blocks.first().formatDefaultHint)

        // Athlete overrides AMRAP to 3 bouts (3 x 4-min AMRAP)
        state = state.updateBlockSetsCount(bKey, 3)
        assertEquals(3, state.blocks.first().setsCount)
        assertNull(state.blocks.first().formatDefaultHint)

        // Tabata defaults to 8 sets
        state = state.selectBlockFormat(bKey, "Tabata")
        assertEquals(8, state.blocks.first().setsCount)

        // E3MOM defaults to 4 sets
        state = state.selectBlockFormat(bKey, "E3MOM")
        assertEquals(4, state.blocks.first().setsCount)
    }

    @Test
    fun `mixed-metric exercises auto-default from library, cache values across metric switches, and sync Per-Set counts`() {
        var state = RoutineBuilderUiState.fromRoutine(original = null, exercises = exercises)
            .updateRoutineName("Mixed E3MOM")
        val bKey = state.blocks.first().uiKey
        state = state.selectBlockKind(bKey, BlockKind.METCON)
            .selectBlockFormat(bKey, "E3MOM") // 4 sets

        // Add Bench Press (REPS), Air Bike (CALORIES), Concept2 Row (METERS)
        state = state.addExerciseToBlock(bKey, bench)
            .addExerciseToBlock(bKey, bike)
            .addExerciseToBlock(bKey, row)

        val exBench = state.blocks.first().exercises[0]
        val exBike = state.blocks.first().exercises[1]
        val exRow = state.blocks.first().exercises[2]

        assertEquals(ExerciseTargetMetric.REPS, exBench.targetMetric)
        assertEquals(ExerciseTargetMetric.CALORIES, exBike.targetMetric)
        assertEquals(ExerciseTargetMetric.METERS, exRow.targetMetric)

        // Configure Air Bike Per-Set [15, 15, 12, 0]
        state = state.selectExerciseTargetMode(bKey, exBike.uiKey, TargetMode.PER_SET)
            .updateExerciseSetValue(bKey, exBike.uiKey, 2, 12)
            .updateExerciseSetValue(bKey, exBike.uiKey, 3, 0)

        assertEquals(listOf(15, 15, 12, 0), state.blocks.first().exercises[1].perSetValues)
        assertEquals("15 • 15 • 12 • 0 cal", state.blocks.first().exercises[1].summaryBadge(4))

        // Switch Air Bike temporarily to METERS and back to CALORIES -> cached [15, 15, 12, 0] restored!
        state = state.selectExerciseMetric(bKey, exBike.uiKey, ExerciseTargetMetric.METERS)
        assertEquals(250, state.blocks.first().exercises[1].fixedValue)
        state = state.selectExerciseMetric(bKey, exBike.uiKey, ExerciseTargetMetric.CALORIES)
        assertEquals(listOf(15, 15, 12, 0), state.blocks.first().exercises[1].perSetValues)

        // Increase Block Sets 4 -> 5: Set 5 copies Set 4 (0)
        state = state.updateBlockSetsCount(bKey, 5)
        assertEquals(listOf(15, 15, 12, 0, 0), state.blocks.first().exercises[1].perSetValues)

        // Decrease Block Sets 5 -> 3: trims to [15, 15, 12]
        state = state.updateBlockSetsCount(bKey, 3)
        assertEquals(listOf(15, 15, 12), state.blocks.first().exercises[1].perSetValues)

        // Restore to 4 sets and set Set 4 = 0, Row Per-Set [250, 250, 200, 0]
        state = state.updateBlockSetsCount(bKey, 4)
            .updateExerciseSetValue(bKey, exBike.uiKey, 3, 0)
            .selectExerciseTargetMode(bKey, exRow.uiKey, TargetMode.PER_SET)
            .updateExerciseSetValue(bKey, exRow.uiKey, 2, 200)
            .updateExerciseSetValue(bKey, exRow.uiKey, 3, 0)

        val saveAttempt = state.attemptSave()
        assertTrue(saveAttempt is RoutineSaveAttempt.Valid)
        val saved = saveAttempt as RoutineSaveAttempt.Valid

        // Reopen saved routine in editor and verify 100% lossless round-trip
        val reopened = RoutineBuilderUiState.fromRoutine(
            RoutineWithBlocks(saved.routine, saved.blocks),
            exercises
        )
        assertFalse(reopened.isRoutineInfoExpanded) // collapsed on edit when named
        assertFalse(reopened.isDirty)
        val reopenedBlock = reopened.blocks.first()
        assertEquals(4, reopenedBlock.setsCount)
        assertEquals(3, reopenedBlock.exercises.size)
        assertEquals(listOf(15, 15, 12, 0), reopenedBlock.exercises[1].perSetValues)
        assertEquals(listOf(250, 250, 200, 0), reopenedBlock.exercises[2].perSetValues)
        assertEquals("", RoutineTargetCodec.extractCleanNotes(saved.blocks.first().notes))
    }

    @Test
    fun `all-zero exercise blocks collapse and blocks Save Routine with inline error flag`() {
        var state = RoutineBuilderUiState.fromRoutine(original = null, exercises = exercises)
            .updateRoutineName("Zero Guard Test")
        val bKey = state.blocks.first().uiKey
        state = state.addExerciseToBlock(bKey, plank)
        val exKey = state.blocks.first().exercises.first().uiKey

        // Set all 4 per-set values to 0
        state = state.selectExerciseTargetMode(bKey, exKey, TargetMode.PER_SET)
            .updateExerciseSetValue(bKey, exKey, 0, 0)
            .updateExerciseSetValue(bKey, exKey, 1, 0)
            .updateExerciseSetValue(bKey, exKey, 2, 0)
            .updateExerciseSetValue(bKey, exKey, 3, 0)

        assertFalse(state.canSave)

        // Attempting Done ▲ keeps exercise expanded and turns on showValidationError
        state = state.toggleExerciseExpanded(bKey, exKey, wantExpanded = false)
        assertEquals(exKey, state.blocks.first().expandedExerciseKey)
        assertTrue(state.blocks.first().exercises.first().showValidationError)

        // Attempting Save fails validation
        assertTrue(state.attemptSave() is RoutineSaveAttempt.ValidationFailed)

        // Setting Set 1 = 30s clears error and allows collapse & save
        state = state.updateExerciseSetValue(bKey, exKey, 0, 30)
        assertFalse(state.blocks.first().exercises.first().showValidationError)
        assertTrue(state.canSave)
    }

    @Test
    fun `repeated sequence exercises reorder independently and Undo restores deleted items`() {
        var state = RoutineBuilderUiState.fromRoutine(original = null, exercises = exercises)
            .updateRoutineName("Shuttle Sandwich")
        val bKey = state.blocks.first().uiKey

        // Shuttle (row) -> Bench -> Shuttle (row)
        state = state.addExerciseToBlock(bKey, row)
            .addExerciseToBlock(bKey, bench)
            .addExerciseToBlock(bKey, row)

        assertEquals(3, state.blocks.first().exercises.size)
        val firstRowKey = state.blocks.first().exercises[0].uiKey
        val secondRowKey = state.blocks.first().exercises[2].uiKey
        assertTrue(firstRowKey != secondRowKey)

        // Delete middle exercise and Undo
        val benchKey = state.blocks.first().exercises[1].uiKey
        state = state.removeExercise(bKey, benchKey)
        assertEquals(2, state.blocks.first().exercises.size)
        state = state.undoLastRemoval()
        assertEquals(3, state.blocks.first().exercises.size)
        assertEquals("Bench Press", state.blocks.first().exercises[1].exerciseName)
    }

    @Test
    fun `resolvePerSetGridColumns adapts across 360dp, tablet, and large OS font scales`() {
        assertEquals(4, resolvePerSetGridColumns(widthDp = 360, fontScale = 1.0f))
        assertEquals(4, resolvePerSetGridColumns(widthDp = 360, fontScale = 1.3f))
        assertEquals(2, resolvePerSetGridColumns(widthDp = 360, fontScale = 1.4f))
        assertEquals(2, resolvePerSetGridColumns(widthDp = 412, fontScale = 2.0f))
        assertEquals(6, resolvePerSetGridColumns(widthDp = 600, fontScale = 1.0f))
    }
}
