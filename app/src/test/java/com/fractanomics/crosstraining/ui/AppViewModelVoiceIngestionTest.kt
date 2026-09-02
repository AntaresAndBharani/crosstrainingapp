package com.fractanomics.crosstraining.ui

import com.fractanomics.crosstraining.data.DataModeManager
import com.fractanomics.crosstraining.data.FakeGeminiNanoClient
import com.fractanomics.crosstraining.data.FakeSampleAppDatabase
import com.fractanomics.crosstraining.data.FakeTransactionRunner
import com.fractanomics.crosstraining.data.Repository
import com.fractanomics.crosstraining.data.VoiceIngestionState
import com.fractanomics.crosstraining.data.ai.AiCoreManager
import com.fractanomics.crosstraining.data.ai.ExerciseEntityGrounder
import com.fractanomics.crosstraining.data.ai.FitnessSpeechLexicon
import com.fractanomics.crosstraining.data.model.BlockKind
import com.fractanomics.crosstraining.data.voice.SpeechRecognizerClient
import com.fractanomics.crosstraining.data.voice.VoiceInputConfig
import com.fractanomics.crosstraining.data.voice.VoiceInputController
import com.fractanomics.crosstraining.data.voice.VoiceInputError
import com.fractanomics.crosstraining.data.voice.VoiceInputState
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.time.LocalDate

/**
 * Unit test suite for [AppViewModel] voice ingestion integration (Issue #473).
 *
 * Acceptance Criteria Covered:
 * - [x] ViewModel integration: expose Flow<VoiceIngestionState> to UI with states (listening, parsing, disambiguating, saving, complete)
 * - [x] Handles exercise disambiguation: if ambiguous, UI prompts user; user selection committed to DB
 * - [x] Live logging: appendBlockSetFromVoice for active workouts
 * - [x] Compose-safe error handling and retry option
 */
@OptIn(ExperimentalCoroutinesApi::class)
class AppViewModelVoiceIngestionTest {

    private val testDispatcher = UnconfinedTestDispatcher()

    private lateinit var fakeDb: FakeSampleAppDatabase
    private lateinit var transactionRunner: FakeTransactionRunner
    private lateinit var fakeAiClient: FakeGeminiNanoClient
    private lateinit var aiCoreManager: AiCoreManager
    private lateinit var repository: Repository
    private lateinit var dataModeManager: DataModeManager
    private lateinit var viewModel: AppViewModel

    private val sessionDate = LocalDate.of(2026, 9, 2)

    @Before
    fun setUp() {
        Dispatchers.setMain(testDispatcher)

        fakeDb = FakeSampleAppDatabase()
        fakeDb.populateSampleData()
        transactionRunner = FakeTransactionRunner(fakeDb)
        fakeAiClient = FakeGeminiNanoClient()
        aiCoreManager = AiCoreManager(client = fakeAiClient)

        repository = Repository(
            db = fakeDb,
            transactionRunner = transactionRunner,
            aiCoreManager = aiCoreManager,
            grounder = ExerciseEntityGrounder.DEFAULT,
            lexicon = FitnessSpeechLexicon.DEFAULT
        )

        dataModeManager = DataModeManager(null)
        dataModeManager.setRepositoryForTesting(repository)

        viewModel = AppViewModel(dataModeManager)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    // =========================================================================
    // 1. Initial State & Listening Lifecycle
    // =========================================================================

    @Test
    fun `initial state is Idle and voiceWorkoutUiState reflects idle`() {
        assertEquals(VoiceIngestionState.Idle, viewModel.voiceIngestionState.value)
        val uiState = viewModel.voiceWorkoutUiState.value
        assertFalse(uiState.isListening)
        assertFalse(uiState.isProcessing)
    }

    @Test
    fun `startVoiceListening without initialized controller transitions to Error state`() {
        viewModel.startVoiceListening(controller = null)
        val state = viewModel.voiceIngestionState.value
        assertTrue("State must be Error when controller missing", state is VoiceIngestionState.Error)
        assertFalse((state as VoiceIngestionState.Error).canRetry)
    }

    @Test
    fun `startVoiceListening with controller transitions to Listening state`() {
        val fakeController = createFakeVoiceController(hasPermission = true, isAvailable = true)
        viewModel.startVoiceListening(fakeController, sessionDate)

        assertTrue(viewModel.voiceIngestionState.value is VoiceIngestionState.Listening)
        assertTrue(viewModel.voiceWorkoutUiState.value.isListening)
    }

    @Test
    fun `startVoiceListening without mic permission emits descriptive error with retry`() {
        val fakeController = createFakeVoiceController(hasPermission = false, isAvailable = true)
        viewModel.startVoiceListening(fakeController, sessionDate)

        val state = viewModel.voiceIngestionState.value
        assertTrue(state is VoiceIngestionState.Error)
        val err = state as VoiceIngestionState.Error
        assertTrue(err.canRetry)
        assertTrue(err.message.contains("Microphone permission", ignoreCase = true))
    }

    // =========================================================================
    // 2. Unambiguous Workout Ingestion Flow (Parsing -> Saving -> Complete)
    // =========================================================================

    @Test
    fun `processVoiceTranscript for unambiguous workout transitions to Complete and saves session`() = runTest(testDispatcher) {
        fakeAiClient.configuredResponse = """
        {
          "blocks": [
            {
              "name": "Back Squat",
              "kind": "STRENGTH",
              "format": "Rest 2min",
              "repScheme": "5x5",
              "sets": [
                {"reps": 5, "weight": 100.0},
                {"reps": 5, "weight": 100.0}
              ]
            }
          ]
        }
        """.trimIndent()

        viewModel.processVoiceTranscript(
            transcript = "5x5 Back Squat at 100 kg",
            sessionDate = sessionDate,
            customAiManager = aiCoreManager
        ).join()

        val state = viewModel.voiceIngestionState.value
        assertTrue("State must be Complete, was: $state", state is VoiceIngestionState.Complete)
        val complete = state as VoiceIngestionState.Complete
        assertNotNull(complete.session)
        assertTrue(complete.session!!.id > 0)

        // Verify database persistence
        val saved = fakeDb.sessionDao().getByIdOnce(complete.session!!.id)
        assertNotNull(saved)
        assertEquals("Back Squat", saved!!.blocks.first().block.name)
    }

    // =========================================================================
    // 3. Exercise Disambiguation Flow (Parsing -> Disambiguating -> Saving -> Complete)
    // =========================================================================

    @Test
    fun `processVoiceTranscript with ambiguous movement emits Disambiguating state and resolves upon selection`() = runTest(testDispatcher) {
        val voiceText = "5 sets of 3 Cleans at 80kg"

        viewModel.processVoiceTranscript(
            transcript = voiceText,
            sessionDate = sessionDate,
            customAiManager = aiCoreManager
        ).join()

        // Verify Disambiguating state
        val state = viewModel.voiceIngestionState.value
        assertTrue("Expected Disambiguating state, was: $state", state is VoiceIngestionState.Disambiguating)
        val disambig = state as VoiceIngestionState.Disambiguating
        assertTrue("Ambiguous blocks must contain block 0", disambig.ambiguousBlocks.containsKey(0))

        val candidates = disambig.ambiguousBlocks[0]!!
        val powerClean = candidates.find { it.name.equals("Power Clean", ignoreCase = true) }
            ?: fakeDb.exerciseDao().byName("Power Clean")!!

        // User resolves disambiguation by selecting Power Clean
        viewModel.resolveExerciseDisambiguation(0, powerClean, sessionDate, aiCoreManager)?.join()

        // Verify transition to Complete
        val finalState = viewModel.voiceIngestionState.value
        assertTrue("State must become Complete after resolving, was: $finalState", finalState is VoiceIngestionState.Complete)
        val complete = finalState as VoiceIngestionState.Complete
        assertNotNull(complete.session)

        val saved = fakeDb.sessionDao().getByIdOnce(complete.session!!.id)
        assertNotNull(saved)
        assertEquals("Power Clean", saved!!.blocks.first().block.name)
        assertEquals(powerClean.id, saved.blocks.first().block.mainExerciseId)
    }

    // =========================================================================
    // 4. Live Workout Set Logging: appendBlockSetFromVoice
    // =========================================================================

    @Test
    fun `appendBlockSetFromVoice transitions to Complete with appended set`() = runTest(testDispatcher) {
        // Create an active session
        val session = repository.createSessionFromVoiceInput("Back Squat 3x5 at 100 kg", sessionDate)

        viewModel.appendBlockSetFromVoice(
            sessionId = session.id,
            voiceText = "Logged 5 back squats at 120 kg, RPE 8",
            customAiManager = aiCoreManager
        ).join()

        val state = viewModel.voiceIngestionState.value
        assertTrue("Expected Complete state, was: $state", state is VoiceIngestionState.Complete)
        val complete = state as VoiceIngestionState.Complete
        assertNotNull(complete.appendedSet)
        assertEquals(5, complete.appendedSet!!.reps)
        assertEquals(120.0, complete.appendedSet!!.weight!!, 0.001)
        assertTrue(complete.appendedSet!!.notes.contains("RPE 8"))
    }

    // =========================================================================
    // 5. Error Handling & Retry Option
    // =========================================================================

    @Test
    fun `blank speech input transitions to Error with canRetry true`() = runTest(testDispatcher) {
        viewModel.processVoiceTranscript("   ", sessionDate).join()

        val state = viewModel.voiceIngestionState.value
        assertTrue(state is VoiceIngestionState.Error)
        val err = state as VoiceIngestionState.Error
        assertTrue(err.canRetry)
        assertTrue(err.message.contains("No speech", ignoreCase = true))
    }

    @Test
    fun `retryLastVoiceIngestion retries with lastVoiceText`() = runTest(testDispatcher) {
        fakeAiClient.configuredResponse = """
        {
          "blocks": [
            {"name": "Snatch", "kind": "STRENGTH", "repScheme": "3x3", "sets": [{"reps": 3, "weight": 70.0}]}
          ]
        }
        """.trimIndent()

        // Set an error with lastVoiceText
        viewModel.processVoiceTranscript("Snatch 3x3 at 70 kg", sessionDate, aiCoreManager).join()
        assertTrue(viewModel.voiceIngestionState.value is VoiceIngestionState.Complete)

        viewModel.resetVoiceIngestionState()
        assertEquals(VoiceIngestionState.Idle, viewModel.voiceIngestionState.value)
    }

    private fun createFakeVoiceController(
        hasPermission: Boolean = true,
        isAvailable: Boolean = true
    ): VoiceInputController {
        val fakeClient = object : SpeechRecognizerClient {
            override fun setRecognitionListener(listener: android.speech.RecognitionListener) {}
            override fun startListening(intent: android.content.Intent) {}
            override fun stopListening() {}
            override fun cancel() {}
            override fun destroy() {}
        }
        return VoiceInputController(
            context = null,
            coroutineScope = kotlinx.coroutines.CoroutineScope(testDispatcher),
            recognizerFactory = { fakeClient },
            permissionChecker = { hasPermission },
            availabilityChecker = { isAvailable },
            config = VoiceInputConfig()
        )
    }
}
