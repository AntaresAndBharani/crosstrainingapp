package com.fractanomics.crosstraining.data.voice

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/**
 * Test fake implementing [RecognitionResultExtractor] for deterministic JVM unit testing.
 */
class FakeRecognitionResultExtractor : RecognitionResultExtractor {
    var matches: List<String> = emptyList()
    var scores: FloatArray? = null

    fun setResults(text: String, score: Float? = null) {
        matches = if (text.isEmpty()) emptyList() else listOf(text)
        scores = score?.let { floatArrayOf(it) }
    }

    fun setEmpty() {
        matches = emptyList()
        scores = null
    }

    override fun extractMatches(bundle: Bundle?): List<String> = matches
    override fun extractConfidenceScores(bundle: Bundle?): FloatArray? = scores
}

/**
 * Test fake implementing [SpeechRecognizerClient] for deterministic unit testing.
 */
class FakeSpeechRecognizerClient(private val extractor: FakeRecognitionResultExtractor) : SpeechRecognizerClient {
    var listener: RecognitionListener? = null
    var lastIntent: Intent? = null
    var startListeningCallCount: Int = 0
    var stopListeningCallCount: Int = 0
    var cancelCallCount: Int = 0
    var destroyCallCount: Int = 0
    var shouldThrowOnStart: Boolean = false

    override fun setRecognitionListener(listener: RecognitionListener) {
        this.listener = listener
    }

    override fun startListening(intent: Intent) {
        if (shouldThrowOnStart) {
            throw RuntimeException("Simulated startListening failure")
        }
        startListeningCallCount++
        lastIntent = intent
    }

    override fun stopListening() {
        stopListeningCallCount++
    }

    override fun cancel() {
        cancelCallCount++
    }

    override fun destroy() {
        destroyCallCount++
    }

    // Helper simulation methods
    fun simulateReadyForSpeech(params: Bundle? = null) {
        listener?.onReadyForSpeech(params)
    }

    fun simulateBeginningOfSpeech() {
        listener?.onBeginningOfSpeech()
    }

    fun simulateRmsChanged(rmsDb: Float) {
        listener?.onRmsChanged(rmsDb)
    }

    fun simulatePartialResult(text: String) {
        extractor.setResults(text)
        listener?.onPartialResults(Bundle())
    }

    fun simulateEndOfSpeech() {
        listener?.onEndOfSpeech()
    }

    fun simulateResults(text: String, confidenceScore: Float? = null) {
        extractor.setResults(text, confidenceScore)
        listener?.onResults(Bundle())
    }

    fun simulateEmptyResults() {
        extractor.setEmpty()
        listener?.onResults(Bundle())
    }

    fun simulateError(errorCode: Int) {
        listener?.onError(errorCode)
    }
}

/**
 * Comprehensive unit test suite for [VoiceInputController], verifying acoustic noise suppression,
 * speech recognition lifecycle, partial result flow emissions, confidence score normalization,
 * error handling, and lifecycle-safe cleanup.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class VoiceInputControllerTest {

    private val testDispatcher = StandardTestDispatcher()
    private val testScope = TestScope(testDispatcher)

    private lateinit var fakeExtractor: FakeRecognitionResultExtractor
    private lateinit var fakeClient: FakeSpeechRecognizerClient
    private lateinit var controller: VoiceInputController

    private var permissionGranted: Boolean = true
    private var recognitionAvailable: Boolean = true

    @Before
    fun setUp() {
        permissionGranted = true
        recognitionAvailable = true
        fakeExtractor = FakeRecognitionResultExtractor()
        fakeClient = FakeSpeechRecognizerClient(fakeExtractor)

        controller = VoiceInputController(
            context = null,
            coroutineScope = testScope,
            recognizerFactory = { fakeClient },
            permissionChecker = { permissionGranted },
            availabilityChecker = { recognitionAvailable },
            resultExtractor = fakeExtractor,
            config = VoiceInputConfig(
                language = "en-US",
                preferOffline = true,
                enablePartialResults = true,
                enableNoiseSuppression = true,
                enableAcousticEchoCancellation = true,
                enableDictationMode = true
            )
        )
    }

    @Test
    fun `initial state is idle with default empty values`() = runTest(testDispatcher) {
        assertFalse(controller.isListening.value)
        assertEquals("", controller.transcript.value)
        assertEquals(0.0f, controller.confidence.value, 0.001f)
        assertEquals(0.0f, controller.rmsDb.value, 0.001f)
        assertNull(controller.error.value)
        assertTrue(controller.state.value is VoiceInputState.Idle)
    }

    @Test
    fun `startListening fails when microphone permission is denied`() = runTest(testDispatcher) {
        permissionGranted = false

        val started = controller.startListening()

        assertFalse(started)
        assertFalse(controller.isListening.value)
        assertEquals(VoiceInputError.MIC_PERMISSION_DENIED, controller.error.value)
        assertTrue(controller.state.value is VoiceInputState.Error)
        assertEquals(
            VoiceInputError.MIC_PERMISSION_DENIED,
            (controller.state.value as VoiceInputState.Error).error
        )
        assertEquals(0, fakeClient.startListeningCallCount)
    }

    @Test
    fun `startListening fails when speech recognition service is not available`() = runTest(testDispatcher) {
        recognitionAvailable = false

        val started = controller.startListening()

        assertFalse(started)
        assertFalse(controller.isListening.value)
        assertEquals(VoiceInputError.NOT_AVAILABLE, controller.error.value)
        assertTrue(controller.state.value is VoiceInputState.Error)
        assertEquals(0, fakeClient.startListeningCallCount)
    }

    @Test
    fun `startListening populates noise suppression and recognition intent extras correctly`() = runTest(testDispatcher) {
        val config = controller.config
        val extras = config.toExtrasMap()

        assertEquals(RecognizerIntent.LANGUAGE_MODEL_FREE_FORM, extras[RecognizerIntent.EXTRA_LANGUAGE_MODEL])
        assertEquals("en-US", extras[RecognizerIntent.EXTRA_LANGUAGE])
        assertEquals(true, extras[RecognizerIntent.EXTRA_PARTIAL_RESULTS])
        assertEquals(true, extras[RecognizerIntent.EXTRA_PREFER_OFFLINE])
        assertEquals(true, extras["android.speech.extra.NOISE_SUPPRESSION"])
        assertEquals(true, extras["android.speech.extra.ENABLE_ACOUSTIC_ECHO_CANCELLATION"])
        assertEquals(true, extras["android.speech.extra.DICTATION_MODE"])

        val started = controller.startListening()

        assertTrue(started)
        assertTrue(controller.isListening.value)
        assertEquals(1, fakeClient.startListeningCallCount)
        assertNotNull(fakeClient.lastIntent)
    }

    @Test
    fun `ready for speech and beginning of speech update state to listening`() = runTest(testDispatcher) {
        controller.startListening()

        fakeClient.simulateReadyForSpeech()
        assertTrue(controller.isListening.value)
        assertTrue(controller.state.value is VoiceInputState.Listening)

        fakeClient.simulateBeginningOfSpeech()
        assertTrue(controller.isListening.value)
        assertTrue(controller.state.value is VoiceInputState.Listening)
    }

    @Test
    fun `partial results emit real-time transcripts through StateFlow`() = runTest(testDispatcher) {
        controller.startListening()
        fakeClient.simulateReadyForSpeech()

        fakeClient.simulatePartialResult("5 back")
        assertEquals("5 back", controller.transcript.value)
        val state1 = controller.state.value
        assertTrue(state1 is VoiceInputState.Listening)
        assertEquals("5 back", (state1 as VoiceInputState.Listening).partialTranscript)

        fakeClient.simulatePartialResult("5 back squats at 100")
        assertEquals("5 back squats at 100", controller.transcript.value)
        val state2 = controller.state.value
        assertTrue(state2 is VoiceInputState.Listening)
        assertEquals("5 back squats at 100", (state2 as VoiceInputState.Listening).partialTranscript)
    }

    @Test
    fun `rms audio level updates rmsDb StateFlow and resets on completion`() = runTest(testDispatcher) {
        controller.startListening()
        fakeClient.simulateReadyForSpeech()

        fakeClient.simulateRmsChanged(8.5f)
        assertEquals(8.5f, controller.rmsDb.value, 0.001f)

        fakeClient.simulateRmsChanged(14.2f)
        assertEquals(14.2f, controller.rmsDb.value, 0.001f)

        fakeClient.simulateResults("Done set", 0.98f)
        assertEquals(0.0f, controller.rmsDb.value, 0.001f)
    }

    @Test
    fun `end of speech transitions state to processing`() = runTest(testDispatcher) {
        controller.startListening()
        fakeClient.simulateReadyForSpeech()
        fakeClient.simulatePartialResult("10 burpees")
        fakeClient.simulateEndOfSpeech()

        val state = controller.state.value
        assertTrue(state is VoiceInputState.Processing)
        assertEquals("10 burpees", (state as VoiceInputState.Processing).partialTranscript)
    }

    @Test
    fun `final results update transcript, confidence score, and success state`() = runTest(testDispatcher) {
        controller.startListening()
        fakeClient.simulateReadyForSpeech()
        fakeClient.simulatePartialResult("3 clean and jerk")
        fakeClient.simulateResults("3 Clean and Jerk at 90 kg", 0.94f)

        assertFalse(controller.isListening.value)
        assertEquals("3 Clean and Jerk at 90 kg", controller.transcript.value)
        assertEquals(0.94f, controller.confidence.value, 0.001f)
        assertNull(controller.error.value)

        val state = controller.state.value
        assertTrue(state is VoiceInputState.Success)
        val success = state as VoiceInputState.Success
        assertEquals("3 Clean and Jerk at 90 kg", success.transcript)
        assertEquals(0.94f, success.confidence, 0.001f)
    }

    @Test
    fun `final results normalize missing or invalid confidence scores`() = runTest(testDispatcher) {
        controller.startListening()
        fakeClient.simulateResults("20 Kettlebell Swings", null)

        assertEquals("20 Kettlebell Swings", controller.transcript.value)
        assertEquals(1.0f, controller.confidence.value, 0.001f)

        // Negative score fallback to 1.0f
        controller.startListening()
        fakeClient.simulateResults("Run 400m", -1.0f)
        assertEquals(1.0f, controller.confidence.value, 0.001f)
    }

    @Test
    fun `empty results transition to NO_MATCH error state`() = runTest(testDispatcher) {
        controller.startListening()
        fakeClient.simulateEmptyResults()

        assertFalse(controller.isListening.value)
        assertEquals(VoiceInputError.NO_MATCH, controller.error.value)
        assertTrue(controller.state.value is VoiceInputState.Error)
    }

    @Test
    fun `standard SpeechRecognizer error codes are mapped to appropriate VoiceInputError enum`() = runTest(testDispatcher) {
        val errorMappings = mapOf(
            SpeechRecognizer.ERROR_NETWORK to VoiceInputError.NETWORK_ERROR,
            SpeechRecognizer.ERROR_NETWORK_TIMEOUT to VoiceInputError.NETWORK_TIMEOUT,
            SpeechRecognizer.ERROR_AUDIO to VoiceInputError.AUDIO_ERROR,
            SpeechRecognizer.ERROR_SERVER to VoiceInputError.SERVER_ERROR,
            SpeechRecognizer.ERROR_CLIENT to VoiceInputError.CLIENT_ERROR,
            SpeechRecognizer.ERROR_SPEECH_TIMEOUT to VoiceInputError.SPEECH_TIMEOUT,
            SpeechRecognizer.ERROR_NO_MATCH to VoiceInputError.NO_MATCH,
            SpeechRecognizer.ERROR_RECOGNIZER_BUSY to VoiceInputError.RECOGNIZER_BUSY,
            SpeechRecognizer.ERROR_INSUFFICIENT_PERMISSIONS to VoiceInputError.MIC_PERMISSION_DENIED,
            999 to VoiceInputError.UNKNOWN
        )

        for ((code, expectedError) in errorMappings) {
            controller.startListening()
            fakeClient.simulateError(code)

            assertFalse(controller.isListening.value)
            assertEquals(0.0f, controller.rmsDb.value, 0.001f)
            assertEquals(expectedError, controller.error.value)
            assertTrue(controller.state.value is VoiceInputState.Error)
            assertEquals(expectedError, (controller.state.value as VoiceInputState.Error).error)
        }
    }

    @Test
    fun `stopListening delegates to recognizer client and transitions to processing`() = runTest(testDispatcher) {
        controller.startListening()
        fakeClient.simulatePartialResult("AMRAP 15")

        controller.stopListening()

        assertEquals(1, fakeClient.stopListeningCallCount)
        assertFalse(controller.isListening.value)
        assertTrue(controller.state.value is VoiceInputState.Processing)
    }

    @Test
    fun `cancel delegates to recognizer client and resets listening state`() = runTest(testDispatcher) {
        controller.startListening()
        fakeClient.simulateRmsChanged(10.0f)

        controller.cancel()

        assertEquals(1, fakeClient.cancelCallCount)
        assertFalse(controller.isListening.value)
        assertEquals(0.0f, controller.rmsDb.value, 0.001f)
        assertTrue(controller.state.value is VoiceInputState.Idle)
    }

    @Test
    fun `reset clears all state flows to initial values`() = runTest(testDispatcher) {
        controller.startListening()
        fakeClient.simulateResults("Fran 21-15-9", 0.99f)

        controller.reset()

        assertFalse(controller.isListening.value)
        assertEquals("", controller.transcript.value)
        assertEquals(0.0f, controller.confidence.value, 0.001f)
        assertEquals(0.0f, controller.rmsDb.value, 0.001f)
        assertNull(controller.error.value)
        assertTrue(controller.state.value is VoiceInputState.Idle)
    }

    @Test
    fun `destroy releases recognizer client and marks controller destroyed`() = runTest(testDispatcher) {
        controller.startListening()

        controller.destroy()

        assertEquals(1, fakeClient.destroyCallCount)
        assertFalse(controller.isListening.value)
        assertTrue(controller.state.value is VoiceInputState.Idle)

        // Subsequent start calls should be safely ignored
        val startedAfterDestroy = controller.startListening()
        assertFalse(startedAfterDestroy)
    }

    @Test
    fun `startListening catches client exception gracefully`() = runTest(testDispatcher) {
        fakeClient.shouldThrowOnStart = true

        val started = controller.startListening()

        assertFalse(started)
        assertFalse(controller.isListening.value)
        assertEquals(VoiceInputError.CLIENT_ERROR, controller.error.value)
        assertTrue(controller.state.value is VoiceInputState.Error)
    }

    @Test
    fun `default constructor instantiates controller with default scope and idle state`() {
        val defaultController = VoiceInputController()
        assertFalse(defaultController.isListening.value)
        assertEquals("", defaultController.transcript.value)
        assertEquals(0.0f, defaultController.confidence.value, 0.001f)
        assertEquals(0.0f, defaultController.rmsDb.value, 0.001f)
        assertNull(defaultController.error.value)
        assertTrue(defaultController.state.value is VoiceInputState.Idle)
        defaultController.destroy()
    }
}
