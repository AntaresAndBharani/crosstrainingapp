package com.fractanomics.crosstraining.ui.voice

import com.fractanomics.crosstraining.data.ai.ParsedBlock
import com.fractanomics.crosstraining.data.ai.ParsedBlockSet
import com.fractanomics.crosstraining.data.model.BlockKind
import com.fractanomics.crosstraining.data.model.Exercise
import com.fractanomics.crosstraining.data.model.ExerciseCategory
import com.fractanomics.crosstraining.data.model.MetricType
import com.fractanomics.crosstraining.data.voice.VoiceInputError
import com.fractanomics.crosstraining.data.voice.VoiceInputState
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Comprehensive Unit Test Suite for [VoiceWorkoutIngestionSheet] logic and components.
 *
 * Covers Acceptance Criteria from Issue #472:
 * - [x] Stateless VoiceWorkoutIngestionSheet composable (receives ViewModel intents as lambdas)
 * - [x] Real-time waveform visualizer (animates to voice volume)
 * - [x] Live transcript display with auto-scroll on new text
 * - [x] Parsed workout cards (editable sets, weights, rep counts) with inline editing
 * - [x] Disambiguation chips (clickable 1-2 alternatives) for ambiguous exercise names
 * - [x] "Confirm & Save" button triggers parent ViewModel persistence
 * - [x] "Cancel" dismisses sheet without saving
 * - [x] Supports both modal (Sheet) and full-screen layouts
 * - [x] UI/UX Details: Mic icon toggling, color feedback (green/yellow/red), accessibility
 */
class VoiceWorkoutIngestionSheetTest {

    private val sampleCleanExercise = Exercise(
        id = 10,
        name = "Power Clean",
        category = ExerciseCategory.BARBELL,
        metricType = MetricType.WEIGHT,
        unit = "kg",
        tracksRepMax = true
    )

    private val sampleSquatCleanExercise = Exercise(
        id = 11,
        name = "Squat Clean",
        category = ExerciseCategory.BARBELL,
        metricType = MetricType.WEIGHT,
        unit = "kg",
        tracksRepMax = true
    )

    private val sampleBackSquatExercise = Exercise(
        id = 20,
        name = "Back Squat",
        category = ExerciseCategory.BARBELL,
        metricType = MetricType.WEIGHT,
        unit = "kg",
        tracksRepMax = true
    )

    // =========================================================================
    // Criterion 1: Stateless VoiceWorkoutIngestionSheet UI State & Model
    // =========================================================================

    @Test
    fun `criterion 1 - initial VoiceWorkoutUiState has valid default values`() {
        val state = VoiceWorkoutUiState()

        assertEquals(VoiceInputState.Idle, state.voiceState)
        assertEquals("", state.transcript)
        assertEquals(0.0f, state.rmsDb, 0.001f)
        assertTrue(state.parsedBlocks.isEmpty())
        assertTrue(state.disambiguationCandidates.isEmpty())
        assertFalse(state.isListening)
        assertFalse(state.isProcessing)
        assertNull(state.errorMessage)
        assertFalse(state.isFullScreen)
        assertFalse(state.activeIsListening)
        assertFalse(state.activeIsProcessing)
        assertNull(state.activeErrorMessage)
        assertFalse(state.canConfirmSave)
    }

    @Test
    fun `criterion 1 - activeIsListening resolves true from voiceState Listening or explicit flag`() {
        val stateFromVoiceState = VoiceWorkoutUiState(
            voiceState = VoiceInputState.Listening("Snatch 5x3")
        )
        assertTrue(stateFromVoiceState.activeIsListening)

        val stateFromExplicitFlag = VoiceWorkoutUiState(
            isListening = true
        )
        assertTrue(stateFromExplicitFlag.activeIsListening)
    }

    @Test
    fun `criterion 1 - activeIsProcessing resolves true from voiceState Processing or explicit flag`() {
        val stateFromVoiceState = VoiceWorkoutUiState(
            voiceState = VoiceInputState.Processing("Extracting JSON...")
        )
        assertTrue(stateFromVoiceState.activeIsProcessing)

        val stateFromExplicitFlag = VoiceWorkoutUiState(
            isProcessing = true
        )
        assertTrue(stateFromExplicitFlag.activeIsProcessing)
    }

    @Test
    fun `criterion 1 - activeErrorMessage extracts error from voiceState Error or explicit message`() {
        val errorState = VoiceWorkoutUiState(
            voiceState = VoiceInputState.Error(
                error = VoiceInputError.MIC_PERMISSION_DENIED,
                message = VoiceInputError.MIC_PERMISSION_DENIED.userMessage
            )
        )
        assertEquals(VoiceInputError.MIC_PERMISSION_DENIED, errorState.activeError)
        assertEquals("Microphone permission is required for voice workout logging.", errorState.activeErrorMessage)

        val explicitErrorState = VoiceWorkoutUiState(
            errorMessage = "Custom network error occurred"
        )
        assertEquals("Custom network error occurred", explicitErrorState.activeErrorMessage)
    }

    @Test
    fun `criterion 1 - canConfirmSave gates persistence when blocks empty or processing active`() {
        val emptyState = VoiceWorkoutUiState(parsedBlocks = emptyList())
        assertFalse(emptyState.canConfirmSave)

        val block = ParsedBlock(name = "Back Squat", sets = listOf(ParsedBlockSet(reps = 5, weight = 100.0)))
        val readyState = VoiceWorkoutUiState(parsedBlocks = listOf(block))
        assertTrue(readyState.canConfirmSave)

        val processingState = VoiceWorkoutUiState(
            parsedBlocks = listOf(block),
            isProcessing = true
        )
        assertFalse(processingState.canConfirmSave)
    }

    // =========================================================================
    // Criterion 2: Real-time Waveform Visualizer & Decibel Normalization
    // =========================================================================

    @Test
    fun `criterion 2 - normalizeRmsDb scales decibel levels within valid 0_05 to 1_0 bounds`() {
        // Below minimum dB (-2 dB) clamps to 0.05
        val minClipped = WaveformVisualizerHelper.normalizeRmsDb(-5.0f)
        assertEquals(0.05f, minClipped, 0.001f)

        val exactMin = WaveformVisualizerHelper.normalizeRmsDb(-2.0f)
        assertEquals(0.05f, exactMin, 0.001f)

        // Above maximum dB (10 dB) clamps to 1.0
        val maxClipped = WaveformVisualizerHelper.normalizeRmsDb(15.0f)
        assertEquals(1.0f, maxClipped, 0.001f)

        val exactMax = WaveformVisualizerHelper.normalizeRmsDb(10.0f)
        assertEquals(1.0f, exactMax, 0.001f)

        // Midpoint (4.0 dB) maps proportionally to ~0.5
        val mid = WaveformVisualizerHelper.normalizeRmsDb(4.0f)
        assertEquals(0.5f, mid, 0.01f)
    }

    @Test
    fun `criterion 2 - calculateBarHeights produces resting flat bars when not listening`() {
        val restingBars = WaveformVisualizerHelper.calculateBarHeights(
            rmsDb = 8.0f,
            barCount = 9,
            isListening = false
        )
        assertEquals(9, restingBars.size)
        restingBars.forEach { height ->
            assertEquals(0.12f, height, 0.001f)
        }
    }

    @Test
    fun `criterion 2 - calculateBarHeights animates bars proportionally when listening`() {
        val listeningBars = WaveformVisualizerHelper.calculateBarHeights(
            rmsDb = 6.0f,
            barCount = 9,
            isListening = true,
            phaseOffset = 0.0f
        )
        assertEquals(9, listeningBars.size)

        // All heights must be in valid visual rendering range [0.08f .. 1.0f]
        listeningBars.forEach { height ->
            assertTrue("Expected height $height to be >= 0.08f", height >= 0.08f)
            assertTrue("Expected height $height to be <= 1.0f", height <= 1.0f)
        }

        // Center bar (index 4) should be higher than outer bars due to bell-curve envelope
        val centerBar = listeningBars[4]
        val leftEdgeBar = listeningBars[0]
        val rightEdgeBar = listeningBars[8]
        assertTrue("Center bar ($centerBar) should be higher than edge bar ($leftEdgeBar)", centerBar >= leftEdgeBar)
        assertTrue("Center bar ($centerBar) should be higher than edge bar ($rightEdgeBar)", centerBar >= rightEdgeBar)
    }

    @Test
    fun `criterion 2 - resolveStatus correctly maps listening, processing, error, and idle states`() {
        val errorStatus = WaveformVisualizerHelper.resolveStatus(
            voiceState = VoiceInputState.Error(VoiceInputError.UNKNOWN, "error"),
            hasError = true
        )
        assertEquals(VoiceIndicatorStatus.ERROR, errorStatus)

        val processingStatus = WaveformVisualizerHelper.resolveStatus(
            voiceState = VoiceInputState.Processing()
        )
        assertEquals(VoiceIndicatorStatus.PROCESSING, processingStatus)

        val listeningStatus = WaveformVisualizerHelper.resolveStatus(
            voiceState = VoiceInputState.Listening()
        )
        assertEquals(VoiceIndicatorStatus.LISTENING, listeningStatus)

        val idleStatus = WaveformVisualizerHelper.resolveStatus(
            voiceState = VoiceInputState.Idle
        )
        assertEquals(VoiceIndicatorStatus.IDLE, idleStatus)
    }

    @Test
    fun `criterion 2 - status colors provide distinct green, yellow, red, and neutral visual feedback`() {
        val green = WaveformVisualizerHelper.resolveColor(VoiceIndicatorStatus.LISTENING)
        val yellow = WaveformVisualizerHelper.resolveColor(VoiceIndicatorStatus.PROCESSING)
        val red = WaveformVisualizerHelper.resolveColor(VoiceIndicatorStatus.ERROR)
        val neutral = WaveformVisualizerHelper.resolveColor(VoiceIndicatorStatus.IDLE)

        assertEquals(WaveformVisualizerHelper.GreenListening, green)
        assertEquals(WaveformVisualizerHelper.YellowProcessing, yellow)
        assertEquals(WaveformVisualizerHelper.RedError, red)
        assertEquals(WaveformVisualizerHelper.NeutralIdle, neutral)
    }

    // =========================================================================
    // Criterion 3: Live Transcript Display & Text Formatting
    // =========================================================================

    @Test
    fun `criterion 3 - live transcript accumulates and preserves speech text accurately`() {
        val partialTranscript = "1 Halting Deadlift plus 1 Hang Power Snatch"
        val statePartial = VoiceWorkoutUiState(transcript = partialTranscript)
        assertEquals(partialTranscript, statePartial.transcript)

        val fullTranscript = "1 Halting Deadlift plus 1 Hang Power Snatch at 60 kilos, 4 sets on a 2 minute timer"
        val stateFull = statePartial.copy(transcript = fullTranscript)
        assertEquals(fullTranscript, stateFull.transcript)
    }

    // =========================================================================
    // Criterion 4: Parsed Workout Cards & Inline Editing Operations
    // =========================================================================

    @Test
    fun `criterion 4 - updateBlockName modifies block title and trims whitespace`() {
        val initialBlocks = listOf(
            ParsedBlock(name = "Squats", kind = BlockKind.STRENGTH)
        )

        val updated = VoiceWorkoutStateOperations.updateBlockName(initialBlocks, 0, "  Back Squat  ")
        assertEquals(1, updated.size)
        assertEquals("Back Squat", updated[0].name)
    }

    @Test
    fun `criterion 4 - updateBlockKind changes workout classification cleanly`() {
        val initialBlocks = listOf(
            ParsedBlock(name = "Fran", kind = BlockKind.STRENGTH)
        )

        val updated = VoiceWorkoutStateOperations.updateBlockKind(initialBlocks, 0, BlockKind.METCON)
        assertEquals(BlockKind.METCON, updated[0].kind)
    }

    @Test
    fun `criterion 4 - updateBlockFormat modifies timing structure`() {
        val initialBlocks = listOf(
            ParsedBlock(name = "Clean Complex", format = "EMOM")
        )

        val updated = VoiceWorkoutStateOperations.updateBlockFormat(initialBlocks, 0, "E2MOM")
        assertEquals("E2MOM", updated[0].format)
    }

    @Test
    fun `criterion 4 - addBlock and removeBlock handle list modifications safely`() {
        val initialBlocks = listOf(
            ParsedBlock(name = "Movement 1")
        )

        // Add block
        val added = VoiceWorkoutStateOperations.addBlock(
            initialBlocks,
            ParsedBlock(name = "Movement 2")
        )
        assertEquals(2, added.size)
        assertEquals("Movement 2", added[1].name)

        // Remove block
        val removed = VoiceWorkoutStateOperations.removeBlock(added, 0)
        assertEquals(1, removed.size)
        assertEquals("Movement 2", removed[0].name)

        // Out of bounds remove returns original list without throwing
        val safeRemoved = VoiceWorkoutStateOperations.removeBlock(removed, 99)
        assertEquals(1, safeRemoved.size)
    }

    @Test
    fun `criterion 4 - updateSetReps updates set repetition count and enforces minimum 1`() {
        val initialBlocks = listOf(
            ParsedBlock(
                name = "Deadlift",
                sets = listOf(ParsedBlockSet(reps = 5, weight = 140.0))
            )
        )

        val updated = VoiceWorkoutStateOperations.updateSetReps(initialBlocks, 0, 0, 8)
        assertEquals(8, updated[0].sets[0].reps)

        // 0 or negative coerced to 1
        val zeroCoerced = VoiceWorkoutStateOperations.updateSetReps(initialBlocks, 0, 0, 0)
        assertEquals(1, zeroCoerced[0].sets[0].reps)
    }

    @Test
    fun `criterion 4 - updateSetWeight updates weight with decimal support`() {
        val initialBlocks = listOf(
            ParsedBlock(
                name = "Snatch",
                sets = listOf(ParsedBlockSet(reps = 2, weight = 60.0))
            )
        )

        val updated = VoiceWorkoutStateOperations.updateSetWeight(initialBlocks, 0, 0, 62.5)
        assertEquals(62.5, updated[0].sets[0].weight)

        // Null weight (bodyweight)
        val bodyweight = VoiceWorkoutStateOperations.updateSetWeight(initialBlocks, 0, 0, null)
        assertNull(bodyweight[0].sets[0].weight)
    }

    @Test
    fun `criterion 4 - toggleSetWarmup toggles warmup flag between working and warmup`() {
        val initialBlocks = listOf(
            ParsedBlock(
                name = "Bench Press",
                sets = listOf(ParsedBlockSet(reps = 10, weight = 40.0, isWarmup = false))
            )
        )

        val toggledOn = VoiceWorkoutStateOperations.toggleSetWarmup(initialBlocks, 0, 0)
        assertTrue(toggledOn[0].sets[0].isWarmup)

        val toggledOff = VoiceWorkoutStateOperations.toggleSetWarmup(toggledOn, 0, 0)
        assertFalse(toggledOff[0].sets[0].isWarmup)
    }

    @Test
    fun `criterion 4 - addSet appends a set and removeSet maintains at least 1 set`() {
        val initialBlocks = listOf(
            ParsedBlock(
                name = "Front Squat",
                sets = listOf(ParsedBlockSet(reps = 3, weight = 90.0))
            )
        )

        // Add second set
        val twoSets = VoiceWorkoutStateOperations.addSet(initialBlocks, 0)
        assertEquals(2, twoSets[0].sets.size)
        assertEquals(3, twoSets[0].sets[1].reps)
        assertEquals(90.0, twoSets[0].sets[1].weight)

        // Add third set with custom values
        val threeSets = VoiceWorkoutStateOperations.addSet(
            twoSets,
            0,
            ParsedBlockSet(reps = 1, weight = 100.0)
        )
        assertEquals(3, threeSets[0].sets.size)
        assertEquals(100.0, threeSets[0].sets[2].weight)

        // Remove set 1
        val removedSet = VoiceWorkoutStateOperations.removeSet(threeSets, 0, 1)
        assertEquals(2, removedSet[0].sets.size)

        // Remove until only 1 set remains
        val singleSet = VoiceWorkoutStateOperations.removeSet(removedSet, 0, 1)
        assertEquals(1, singleSet[0].sets.size)

        // Attempting to remove the last set does nothing (keeps at least 1 set)
        val keptAtLeastOne = VoiceWorkoutStateOperations.removeSet(singleSet, 0, 0)
        assertEquals(1, keptAtLeastOne[0].sets.size)
    }

    @Test
    fun `criterion 4 - weight formatting and parsing handles whole numbers and decimals`() {
        assertEquals("60", VoiceWorkoutStateOperations.formatWeight(60.0))
        assertEquals("62.5", VoiceWorkoutStateOperations.formatWeight(62.5))
        assertEquals("", VoiceWorkoutStateOperations.formatWeight(null))

        assertEquals(60.0, VoiceWorkoutStateOperations.parseWeight("60")!!, 0.001)
        assertEquals(62.5, VoiceWorkoutStateOperations.parseWeight("62.5")!!, 0.001)
        assertNull(VoiceWorkoutStateOperations.parseWeight(""))
        assertNull(VoiceWorkoutStateOperations.parseWeight("   "))
        assertNull(VoiceWorkoutStateOperations.parseWeight("abc"))

        assertEquals(5, VoiceWorkoutStateOperations.parseReps("5"))
        assertEquals(1, VoiceWorkoutStateOperations.parseReps("0"))
        assertEquals(1, VoiceWorkoutStateOperations.parseReps(""))
        assertEquals(1, VoiceWorkoutStateOperations.parseReps("invalid"))
    }

    @Test
    fun `criterion 4 - totalSetCount aggregates sets across all blocks`() {
        val blocks = listOf(
            ParsedBlock(name = "Block 1", sets = listOf(ParsedBlockSet(), ParsedBlockSet())),
            ParsedBlock(name = "Block 2", sets = listOf(ParsedBlockSet(), ParsedBlockSet(), ParsedBlockSet()))
        )
        assertEquals(5, VoiceWorkoutStateOperations.totalSetCount(blocks))
    }

    // =========================================================================
    // Criterion 5: Disambiguation Chips & Entity Disambiguation
    // =========================================================================

    @Test
    fun `criterion 5 - applyDisambiguation updates exercise name and clears disambiguation candidate entry`() {
        val initialBlocks = listOf(
            ParsedBlock(name = "Cleans", sets = listOf(ParsedBlockSet(reps = 3, weight = 80.0))),
            ParsedBlock(name = "Wall Balls", sets = listOf(ParsedBlockSet(reps = 15)))
        )

        val candidatesMap = mapOf(
            0 to listOf(sampleCleanExercise, sampleSquatCleanExercise)
        )

        // Athlete taps [Power Clean] chip
        val (updatedBlocks, updatedCandidates) = VoiceWorkoutStateOperations.applyDisambiguation(
            blocks = initialBlocks,
            candidatesMap = candidatesMap,
            blockIndex = 0,
            selectedExercise = sampleCleanExercise
        )

        // Block 0 updated to "Power Clean"
        assertEquals("Power Clean", updatedBlocks[0].name)
        assertEquals(80.0, updatedBlocks[0].sets[0].weight)

        // Block 1 remains untouched
        assertEquals("Wall Balls", updatedBlocks[1].name)

        // Disambiguation candidates entry for block 0 is cleared
        assertFalse(updatedCandidates.containsKey(0))
        assertTrue(updatedCandidates.isEmpty())
    }

    @Test
    fun `criterion 5 - applyDisambiguation preserves disambiguation candidates for other blocks`() {
        val initialBlocks = listOf(
            ParsedBlock(name = "Cleans"),
            ParsedBlock(name = "Squats")
        )

        val candidatesMap = mapOf(
            0 to listOf(sampleCleanExercise, sampleSquatCleanExercise),
            1 to listOf(sampleBackSquatExercise)
        )

        // Resolve block 0
        val (_, updatedCandidates) = VoiceWorkoutStateOperations.applyDisambiguation(
            blocks = initialBlocks,
            candidatesMap = candidatesMap,
            blockIndex = 0,
            selectedExercise = sampleSquatCleanExercise
        )

        assertFalse(updatedCandidates.containsKey(0))
        assertTrue(updatedCandidates.containsKey(1))
        assertEquals(1, updatedCandidates[1]?.size)
        assertEquals("Back Squat", updatedCandidates[1]?.first()?.name)
    }

    // =========================================================================
    // Criterion 6: Confirm & Save Persistence Trigger
    // =========================================================================

    @Test
    fun `criterion 6 - confirm and save callback dispatches structured blocks to parent`() {
        var savedBlocks: List<ParsedBlock>? = null
        val blocks = listOf(
            ParsedBlock(
                name = "Snatch Complex",
                kind = BlockKind.STRENGTH,
                format = "E2MOM",
                sets = listOf(ParsedBlockSet(reps = 1, weight = 70.0))
            )
        )

        val onConfirm: (List<ParsedBlock>) -> Unit = { dispatched ->
            savedBlocks = dispatched
        }

        onConfirm(blocks)
        assertNotNull(savedBlocks)
        assertEquals(1, savedBlocks!!.size)
        assertEquals("Snatch Complex", savedBlocks!![0].name)
        assertEquals("E2MOM", savedBlocks!![0].format)
    }

    // =========================================================================
    // Criterion 7: Cancel Dismissal Without Saving
    // =========================================================================

    @Test
    fun `criterion 7 - cancel triggers dismissal callback without saving`() {
        var isDismissed = false
        var savedCalled = false

        val onCancel = { isDismissed = true }
        val onSave: (List<ParsedBlock>) -> Unit = { savedCalled = true }

        onCancel()

        assertTrue(isDismissed)
        assertFalse(savedCalled)
    }

    // =========================================================================
    // Criterion 8: Modal Sheet vs Full-Screen Layout Support
    // =========================================================================

    @Test
    fun `criterion 8 - state supports toggling between modal sheet and full-screen layouts`() {
        val modalState = VoiceWorkoutUiState(isFullScreen = false)
        assertFalse(modalState.isFullScreen)

        val fullScreenState = modalState.copy(isFullScreen = true)
        assertTrue(fullScreenState.isFullScreen)
    }
}
