package com.fractanomics.crosstraining.ui.screens

import com.fractanomics.crosstraining.data.model.BlockKind
import com.fractanomics.crosstraining.data.model.BlockSet
import com.fractanomics.crosstraining.data.model.BlockWithSets
import com.fractanomics.crosstraining.data.model.Exercise
import com.fractanomics.crosstraining.data.model.ExerciseCategory
import com.fractanomics.crosstraining.data.model.MetricType
import com.fractanomics.crosstraining.data.model.RepMax
import com.fractanomics.crosstraining.data.model.Session
import com.fractanomics.crosstraining.data.model.SessionBlock
import com.fractanomics.crosstraining.data.model.SessionWithBlocks
import com.fractanomics.crosstraining.util.AvailableBlock
import com.fractanomics.crosstraining.util.AvailableBlockCodec
import com.fractanomics.crosstraining.util.AvailableBlockExercise
import com.fractanomics.crosstraining.util.TrackedPrSlot
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate

class SessionEditorBlockLogEditTest {

    private val snatch = Exercise(
        id = 1L,
        name = "Snatch",
        category = ExerciseCategory.BARBELL,
        metricType = MetricType.WEIGHT,
        unit = "kg",
        tracksRepMax = true
    )
    private val clean = Exercise(
        id = 2L,
        name = "Clean",
        category = ExerciseCategory.BARBELL,
        metricType = MetricType.WEIGHT,
        unit = "kg",
        tracksRepMax = true
    )
    private val exercises = listOf(snatch, clean)

    private fun snatchWaveBlock(): AvailableBlock = AvailableBlock(
        id = 10L,
        title = "Snatch Wave",
        instructions = "Wave loading 3RM",
        exercises = listOf(AvailableBlockExercise(1L, "Snatch", true)),
        trackedPrs = listOf(TrackedPrSlot(1L, "Snatch", 3))
    )

    private fun snatchSession(sessionId: Long = 20L, weight: Double = 35.0): SessionWithBlocks =
        SessionWithBlocks(
            session = Session(id = sessionId, cycleId = 1L, date = LocalDate.of(2026, 11, 14), title = "Snatch Wave"),
            blocks = listOf(
                BlockWithSets(
                    block = SessionBlock(
                        id = 201L,
                        sessionId = sessionId,
                        position = 0,
                        name = "Snatch",
                        kind = BlockKind.STRENGTH,
                        mainExerciseId = 1L,
                        routineId = 10L,
                        subBlock = "Snatch Wave"
                    ),
                    sets = listOf(
                        BlockSet(blockId = 201L, position = 0, reps = 3, weight = weight - 2.5),
                        BlockSet(blockId = 201L, position = 1, reps = 3, weight = weight)
                    )
                )
            )
        )

    @Test
    fun `buildHydratedBlockState hides pre-filled banner and excludes edited session from PR check`() {
        val date = LocalDate.of(2026, 11, 14)
        val historicalRm = RepMax(id = 1L, exerciseId = 1L, reps = 3, weight = 32.5, date = date.minusDays(7), sessionId = 15L)
        val editedRm = RepMax(id = 2L, exerciseId = 1L, reps = 3, weight = 35.0, date = date, sessionId = 20L, blockId = 201L)
        val repMaxes = listOf(historicalRm, editedRm)

        val hydration = AvailableBlockCodec.hydrateSessionBlocksForEditor(
            session = snatchSession(sessionId = 20L, weight = 35.0),
            availableBlocks = listOf(snatchWaveBlock()),
            exercises = exercises,
            sessionRepMaxes = listOf(editedRm)
        )
        val state = buildHydratedBlockState(
            seed = hydration.simplifiedBlocks[0],
            exercises = exercises,
            editingSessionId = 20L
        )

        assertTrue(state.isEditMode)
        assertNull(state.exerciseSections[0].lastSessionDateLabel)
        val evaluated = state.evaluatePrChips(exercises, repMaxes)
        assertEquals(1, evaluated.size)
        // Stored best excludes session 20 (35.0), so candidate 35.0 > 32.5 is still recognized as a PR!
        assertEquals(32.5, evaluated[0].second.storedBestWeight, 0.001)
        assertTrue(evaluated[0].second.isNewPr)
        assertEquals(35.0, evaluated[0].second.candidateWeight, 0.001)
        assertFalse(state.isCustomWeightOverride(prSlotKey(1L, "Snatch", 3)))
    }

    @Test
    fun `live set weight auto-sync updates PR chip across edit add and remove when not overridden`() {
        val date = LocalDate.of(2026, 11, 14)
        val priorRm = RepMax(id = 1L, exerciseId = 1L, reps = 3, weight = 32.5, date = date.minusDays(7), sessionId = 15L)
        val editedRm = RepMax(id = 2L, exerciseId = 1L, reps = 3, weight = 35.0, date = date, sessionId = 20L, blockId = 201L)
        val repMaxes = listOf(priorRm, editedRm)

        val hydration = AvailableBlockCodec.hydrateSessionBlocksForEditor(
            session = snatchSession(sessionId = 20L, weight = 35.0),
            availableBlocks = listOf(snatchWaveBlock()),
            exercises = exercises,
            sessionRepMaxes = listOf(editedRm)
        )
        val state = buildHydratedBlockState(hydration.simplifiedBlocks[0], exercises, editingSessionId = 20L)
        val exState = state.exerciseSections[0]

        // 1. Edit existing top set weight 35 -> 37.5
        state.updateSetWeight(exState, setIndex = 1, rawValue = "37.5")
        assertEquals(37.5, state.evaluatePrChips(exercises, repMaxes)[0].second.candidateWeight, 0.001)
        assertEquals(37.5, state.toBlockDrafts(exercises, repMaxes)[0].newRepMaxes[0].weight, 0.001)

        // 2. Add a heavier set (+ Add Set -> 40)
        state.addSet(exState)
        state.updateSetWeight(exState, setIndex = 2, rawValue = "40")
        assertEquals(40.0, state.evaluatePrChips(exercises, repMaxes)[0].second.candidateWeight, 0.001)

        // 3. Remove heaviest set -> PR chip drops back down to 37.5 live
        state.removeSet(exState, setIndex = 2)
        assertEquals(37.5, state.evaluatePrChips(exercises, repMaxes)[0].second.candidateWeight, 0.001)
    }

    @Test
    fun `manual picker override and diverged persisted PR protect custom PR weight from set edits`() {
        val date = LocalDate.of(2026, 11, 14)
        val divergedRm = RepMax(id = 2L, exerciseId = 1L, reps = 3, weight = 38.0, date = date, sessionId = 20L, blockId = 201L)
        val hydration = AvailableBlockCodec.hydrateSessionBlocksForEditor(
            session = snatchSession(sessionId = 20L, weight = 35.0),
            availableBlocks = listOf(snatchWaveBlock()),
            exercises = exercises,
            sessionRepMaxes = listOf(divergedRm)
        )
        val state = buildHydratedBlockState(hydration.simplifiedBlocks[0], exercises, editingSessionId = 20L)
        val key3 = prSlotKey(1L, "Snatch", 3)

        // Hydrated diverged PR (38 vs set max 35) sets custom override flag automatically
        assertTrue(state.isCustomWeightOverride(key3))
        state.updateSetWeight(state.exerciseSections[0], setIndex = 1, rawValue = "36")
        assertEquals(38.0, state.evaluatePrChips(exercises, listOf(divergedRm))[0].second.candidateWeight, 0.001)

        // Also test applying a manual 1RM override via Record / Track PRs sheet
        val key1 = prSlotKey(1L, "Snatch", 1)
        state.applyPrPickerChanges(
            updatedSlots = listOf(
                TrackedPrSlot(1L, "Snatch", 3),
                TrackedPrSlot(1L, "Snatch", 1)
            ),
            enteredWeightsByKey = mapOf(
                key3 to "36",
                key1 to "42.5"
            ),
            exercises = exercises,
            repMaxes = emptyList()
        )
        assertFalse(state.isCustomWeightOverride(key3))
        assertTrue(state.isCustomWeightOverride(key1))

        state.updateSetWeight(state.exerciseSections[0], setIndex = 1, rawValue = "39")
        val chips = state.evaluatePrChips(exercises, emptyList()).associateBy { it.first.rmReps }
        assertEquals(39.0, chips[3]!!.second.candidateWeight, 0.001)
        assertEquals(42.5, chips[1]!!.second.candidateWeight, 0.001)
    }

    @Test
    fun `multi-movement shared RM isolates auto-sync and remove with undo`() {
        val block = AvailableBlock(
            id = 11L,
            title = "Snatch + Clean",
            instructions = "Heavy triples",
            exercises = listOf(
                AvailableBlockExercise(1L, "Snatch", true),
                AvailableBlockExercise(2L, "Clean", true)
            ),
            trackedPrs = listOf(
                TrackedPrSlot(1L, "Snatch", 3),
                TrackedPrSlot(2L, "Clean", 3)
            )
        )
        val date = LocalDate.of(2026, 11, 14)
        val session = SessionWithBlocks(
            session = Session(id = 30L, cycleId = 1L, date = date, title = "Snatch + Clean"),
            blocks = listOf(
                BlockWithSets(
                    block = SessionBlock(
                        id = 301L, sessionId = 30L, position = 0, name = "Snatch",
                        kind = BlockKind.STRENGTH, mainExerciseId = 1L, routineId = 11L, subBlock = "Snatch + Clean"
                    ),
                    sets = listOf(BlockSet(blockId = 301L, position = 0, reps = 3, weight = 35.0))
                ),
                BlockWithSets(
                    block = SessionBlock(
                        id = 302L, sessionId = 30L, position = 1, name = "Clean",
                        kind = BlockKind.STRENGTH, mainExerciseId = 2L, routineId = 11L, subBlock = "Snatch + Clean"
                    ),
                    sets = listOf(BlockSet(blockId = 302L, position = 0, reps = 3, weight = 60.0))
                )
            )
        )
        val rms = listOf(
            RepMax(id = 10L, exerciseId = 1L, reps = 3, weight = 35.0, date = date, sessionId = 30L, blockId = 301L),
            RepMax(id = 11L, exerciseId = 2L, reps = 3, weight = 60.0, date = date, sessionId = 30L, blockId = 302L)
        )
        val hydration = AvailableBlockCodec.hydrateSessionBlocksForEditor(session, listOf(block), exercises, rms)
        val state = buildHydratedBlockState(hydration.simplifiedBlocks[0], exercises, editingSessionId = 30L)

        // Updating Snatch does NOT alter Clean
        state.updateSetWeight(state.exerciseSections[0], 0, "37.5")
        val afterUpdate = state.evaluatePrChips(exercises, rms)
        assertEquals(37.5, afterUpdate.first { it.first.movementId == 1L }.second.candidateWeight, 0.001)
        assertEquals(60.0, afterUpdate.first { it.first.movementId == 2L }.second.candidateWeight, 0.001)

        // Removing Snatch 3RM removes only Snatch 3RM from drafts, and Undo restores it cleanly
        val countBeforeRemove = state.mutationCount
        val snapshot = state.removeTrackedPrSlot(TrackedPrSlot(1L, "Snatch", 3))
        assertNotNull(snapshot)
        assertEquals(countBeforeRemove + 1, state.mutationCount)
        assertEquals(1, state.trackedPrSlots.size)
        assertTrue(state.toBlockDrafts(exercises, rms)[0].newRepMaxes.isEmpty())
        assertEquals(1, state.toBlockDrafts(exercises, rms)[1].newRepMaxes.size)

        state.restoreDismissedPr(snapshot!!)
        assertEquals(2, state.trackedPrSlots.size)
        assertEquals(37.5, state.toBlockDrafts(exercises, rms)[0].newRepMaxes[0].weight, 0.001)
    }
}
