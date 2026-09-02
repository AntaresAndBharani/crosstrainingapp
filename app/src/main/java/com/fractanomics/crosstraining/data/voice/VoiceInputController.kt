package com.fractanomics.crosstraining.data.voice

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Bundle
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import androidx.core.content.ContextCompat
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.util.Locale

/**
 * Standardized voice input error categories with descriptive user-facing error messages.
 */
enum class VoiceInputError(val code: Int, val userMessage: String) {
    MIC_PERMISSION_DENIED(
        code = SpeechRecognizer.ERROR_INSUFFICIENT_PERMISSIONS,
        userMessage = "Microphone permission is required for voice workout logging."
    ),
    NO_MATCH(
        code = SpeechRecognizer.ERROR_NO_MATCH,
        userMessage = "No speech was recognized. Please speak clearly into the microphone."
    ),
    SPEECH_TIMEOUT(
        code = SpeechRecognizer.ERROR_SPEECH_TIMEOUT,
        userMessage = "No speech input detected within the timeout window."
    ),
    NETWORK_ERROR(
        code = SpeechRecognizer.ERROR_NETWORK,
        userMessage = "Network error during speech recognition."
    ),
    NETWORK_TIMEOUT(
        code = SpeechRecognizer.ERROR_NETWORK_TIMEOUT,
        userMessage = "Network connection timed out during speech recognition."
    ),
    AUDIO_ERROR(
        code = SpeechRecognizer.ERROR_AUDIO,
        userMessage = "Audio recording hardware error. Please check your microphone."
    ),
    RECOGNIZER_BUSY(
        code = SpeechRecognizer.ERROR_RECOGNIZER_BUSY,
        userMessage = "Speech recognizer is busy. Please try again."
    ),
    SERVER_ERROR(
        code = SpeechRecognizer.ERROR_SERVER,
        userMessage = "Speech recognition server error."
    ),
    CLIENT_ERROR(
        code = SpeechRecognizer.ERROR_CLIENT,
        userMessage = "Speech recognition client error."
    ),
    NOT_AVAILABLE(
        code = -2,
        userMessage = "Speech recognition is not available on this device."
    ),
    UNKNOWN(
        code = -1,
        userMessage = "An unexpected error occurred during speech recognition."
    );

    companion object {
        fun fromErrorCode(errorCode: Int): VoiceInputError {
            return when (errorCode) {
                SpeechRecognizer.ERROR_NETWORK_TIMEOUT -> NETWORK_TIMEOUT
                SpeechRecognizer.ERROR_NETWORK -> NETWORK_ERROR
                SpeechRecognizer.ERROR_AUDIO -> AUDIO_ERROR
                SpeechRecognizer.ERROR_SERVER -> SERVER_ERROR
                SpeechRecognizer.ERROR_CLIENT -> CLIENT_ERROR
                SpeechRecognizer.ERROR_SPEECH_TIMEOUT -> SPEECH_TIMEOUT
                SpeechRecognizer.ERROR_NO_MATCH -> NO_MATCH
                SpeechRecognizer.ERROR_RECOGNIZER_BUSY -> RECOGNIZER_BUSY
                SpeechRecognizer.ERROR_INSUFFICIENT_PERMISSIONS -> MIC_PERMISSION_DENIED
                else -> UNKNOWN
            }
        }
    }
}

/**
 * Lifecycle state representation for the voice input engine.
 */
sealed class VoiceInputState {
    data object Idle : VoiceInputState()
    data object Initializing : VoiceInputState()
    data class Listening(val partialTranscript: String = "") : VoiceInputState()
    data class Processing(val partialTranscript: String = "") : VoiceInputState()
    data class Success(val transcript: String, val confidence: Float) : VoiceInputState()
    data class Error(val error: VoiceInputError, val message: String) : VoiceInputState()
}

/**
 * Configuration options for acoustic noise suppression and speech recognition parameters.
 */
data class VoiceInputConfig(
    val language: String = Locale.getDefault().toLanguageTag(),
    val preferOffline: Boolean = true,
    val enablePartialResults: Boolean = true,
    val maxResults: Int = 5,
    val completeSilenceLengthMillis: Long = 1500L,
    val possiblyCompleteSilenceLengthMillis: Long = 1500L,
    val minimumLengthMillis: Long = 1000L,
    val enableNoiseSuppression: Boolean = true,
    val enableAcousticEchoCancellation: Boolean = true,
    val enableDictationMode: Boolean = true
) {
    /**
     * Returns a map representing the acoustic noise suppression and speech recognition extras.
     */
    fun toExtrasMap(): Map<String, Any> = buildMap {
        put(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
        put(RecognizerIntent.EXTRA_LANGUAGE, language)
        put(RecognizerIntent.EXTRA_PARTIAL_RESULTS, enablePartialResults)
        put(RecognizerIntent.EXTRA_MAX_RESULTS, maxResults)
        put(RecognizerIntent.EXTRA_PREFER_OFFLINE, preferOffline)
        put(RecognizerIntent.EXTRA_SPEECH_INPUT_COMPLETE_SILENCE_LENGTH_MILLIS, completeSilenceLengthMillis)
        put(RecognizerIntent.EXTRA_SPEECH_INPUT_POSSIBLY_COMPLETE_SILENCE_LENGTH_MILLIS, possiblyCompleteSilenceLengthMillis)
        put(RecognizerIntent.EXTRA_SPEECH_INPUT_MINIMUM_LENGTH_MILLIS, minimumLengthMillis)
        if (enableNoiseSuppression) {
            put("android.speech.extra.NOISE_SUPPRESSION", true)
        }
        if (enableAcousticEchoCancellation) {
            put("android.speech.extra.ENABLE_ACOUSTIC_ECHO_CANCELLATION", true)
        }
        if (enableDictationMode) {
            put("android.speech.extra.DICTATION_MODE", true)
        }
    }

    /**
     * Builds an [Intent] populated with acoustic noise suppression and speech recognition extras.
     */
    fun createRecognizerIntent(): Intent {
        return Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
            toExtrasMap().forEach { (key, value) ->
                when (value) {
                    is String -> putExtra(key, value)
                    is Boolean -> putExtra(key, value)
                    is Int -> putExtra(key, value)
                    is Long -> putExtra(key, value)
                    is Float -> putExtra(key, value)
                    is Double -> putExtra(key, value)
                }
            }
        }
    }
}

/**
 * Interface abstraction wrapping Android [SpeechRecognizer] to enable deterministic unit testing
 * without requiring the Android OS runtime or Robolectric runner.
 */
interface SpeechRecognizerClient {
    fun setRecognitionListener(listener: RecognitionListener)
    fun startListening(intent: Intent)
    fun stopListening()
    fun cancel()
    fun destroy()
}

/**
 * Standard Android implementation delegating directly to [SpeechRecognizer].
 */
class AndroidSpeechRecognizerClient(private val recognizer: SpeechRecognizer) : SpeechRecognizerClient {
    override fun setRecognitionListener(listener: RecognitionListener) {
        recognizer.setRecognitionListener(listener)
    }

    override fun startListening(intent: Intent) {
        recognizer.startListening(intent)
    }

    override fun stopListening() {
        recognizer.stopListening()
    }

    override fun cancel() {
        recognizer.cancel()
    }

    override fun destroy() {
        recognizer.destroy()
    }
}

/**
 * Factory for creating [SpeechRecognizerClient] instances.
 */
fun interface SpeechRecognizerFactory {
    fun create(context: Context?): SpeechRecognizerClient
}

/**
 * Default factory creating platform [SpeechRecognizer] instances.
 */
object DefaultSpeechRecognizerFactory : SpeechRecognizerFactory {
    override fun create(context: Context?): SpeechRecognizerClient {
        requireNotNull(context) { "Context must not be null for DefaultSpeechRecognizerFactory" }
        val recognizer = SpeechRecognizer.createSpeechRecognizer(context)
        return AndroidSpeechRecognizerClient(recognizer)
    }
}

/**
 * Interface for checking microphone permissions.
 */
fun interface MicrophonePermissionChecker {
    fun hasPermission(context: Context?): Boolean
}

/**
 * Default microphone permission checker querying [ContextCompat.checkSelfPermission].
 */
object DefaultMicrophonePermissionChecker : MicrophonePermissionChecker {
    override fun hasPermission(context: Context?): Boolean {
        if (context == null) return false
        return ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.RECORD_AUDIO
        ) == PackageManager.PERMISSION_GRANTED
    }
}

/**
 * Interface for verifying device speech recognition availability.
 */
fun interface SpeechRecognitionAvailabilityChecker {
    fun isRecognitionAvailable(context: Context?): Boolean
}

/**
 * Default availability checker delegating to [SpeechRecognizer.isRecognitionAvailable].
 */
object DefaultSpeechRecognitionAvailabilityChecker : SpeechRecognitionAvailabilityChecker {
    override fun isRecognitionAvailable(context: Context?): Boolean {
        if (context == null) return false
        return SpeechRecognizer.isRecognitionAvailable(context)
    }
}

/**
 * Extractor interface for extracting speech recognition results from [Bundle] payloads.
 */
interface RecognitionResultExtractor {
    fun extractMatches(bundle: Bundle?): List<String>
    fun extractConfidenceScores(bundle: Bundle?): FloatArray?
}

/**
 * Default Android implementation extracting results via standard [SpeechRecognizer] keys.
 */
object DefaultRecognitionResultExtractor : RecognitionResultExtractor {
    override fun extractMatches(bundle: Bundle?): List<String> {
        return bundle?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION) ?: emptyList()
    }

    override fun extractConfidenceScores(bundle: Bundle?): FloatArray? {
        return bundle?.getFloatArray(SpeechRecognizer.CONFIDENCE_SCORES)
    }
}

/**
 * Manages voice audio capture, speech recognition, acoustic noise suppression configuration,
 * and lifecycle-safe coroutine StateFlow streams.
 *
 * @param context Android application context
 * @param coroutineScope Scope managing internal asynchronous operations and state flows
 * @param recognizerFactory Factory creating [SpeechRecognizerClient] instances
 * @param permissionChecker Verifier for [Manifest.permission.RECORD_AUDIO]
 * @param availabilityChecker Verifier for speech recognition service availability
 * @param resultExtractor Extractor for parsing recognition [Bundle] results
 * @param config Acoustic noise suppression and recognition parameters
 */
class VoiceInputController(
    private val context: Context? = null,
    private val coroutineScope: CoroutineScope,
    private val recognizerFactory: SpeechRecognizerFactory = DefaultSpeechRecognizerFactory,
    private val permissionChecker: MicrophonePermissionChecker = DefaultMicrophonePermissionChecker,
    private val availabilityChecker: SpeechRecognitionAvailabilityChecker = DefaultSpeechRecognitionAvailabilityChecker,
    private val resultExtractor: RecognitionResultExtractor = DefaultRecognitionResultExtractor,
    val config: VoiceInputConfig = VoiceInputConfig()
) {
    private val _isListening = MutableStateFlow(false)
    val isListening: StateFlow<Boolean> = _isListening.asStateFlow()

    private val _transcript = MutableStateFlow("")
    val transcript: StateFlow<String> = _transcript.asStateFlow()

    private val _confidence = MutableStateFlow(0.0f)
    val confidence: StateFlow<Float> = _confidence.asStateFlow()

    private val _rmsDb = MutableStateFlow(0.0f)
    val rmsDb: StateFlow<Float> = _rmsDb.asStateFlow()

    private val _error = MutableStateFlow<VoiceInputError?>(null)
    val error: StateFlow<VoiceInputError?> = _error.asStateFlow()

    private val _state = MutableStateFlow<VoiceInputState>(VoiceInputState.Idle)
    val state: StateFlow<VoiceInputState> = _state.asStateFlow()

    private var speechClient: SpeechRecognizerClient? = null
    private var isDestroyed = false

    private val recognitionListener = object : RecognitionListener {
        override fun onReadyForSpeech(params: Bundle?) {
            _isListening.value = true
            _state.value = VoiceInputState.Listening(_transcript.value)
        }

        override fun onBeginningOfSpeech() {
            _isListening.value = true
            _state.value = VoiceInputState.Listening(_transcript.value)
        }

        override fun onRmsChanged(rmsdB: Float) {
            _rmsDb.value = rmsdB
        }

        override fun onBufferReceived(buffer: ByteArray?) {
            // No-op
        }

        override fun onEndOfSpeech() {
            _state.value = VoiceInputState.Processing(_transcript.value)
        }

        override fun onError(errorCode: Int) {
            _isListening.value = false
            _rmsDb.value = 0.0f
            val err = VoiceInputError.fromErrorCode(errorCode)
            _error.value = err
            _state.value = VoiceInputState.Error(err, err.userMessage)
        }

        override fun onResults(results: Bundle?) {
            _isListening.value = false
            _rmsDb.value = 0.0f

            val matches = resultExtractor.extractMatches(results)
            val scores = resultExtractor.extractConfidenceScores(results)

            val text = matches.firstOrNull()?.trim() ?: ""
            val score = scores?.firstOrNull()?.let {
                if (it < 0f) 1.0f else it.coerceIn(0.0f, 1.0f)
            } ?: if (text.isNotBlank()) 1.0f else 0.0f

            if (text.isBlank()) {
                val err = VoiceInputError.NO_MATCH
                _error.value = err
                _state.value = VoiceInputState.Error(err, err.userMessage)
            } else {
                _transcript.value = text
                _confidence.value = score
                _error.value = null
                _state.value = VoiceInputState.Success(text, score)
            }
        }

        override fun onPartialResults(partialResults: Bundle?) {
            val matches = resultExtractor.extractMatches(partialResults)
            val partialText = matches.firstOrNull()?.trim() ?: ""
            if (partialText.isNotBlank()) {
                _transcript.value = partialText
                _state.value = VoiceInputState.Listening(partialText)
            }
        }

        override fun onEvent(eventType: Int, params: Bundle?) {
            // Reserved for future recognition engine events
        }
    }

    /**
     * Initiates voice listening. Validates microphone permissions and speech recognition availability,
     * builds the noise suppression recognition intent, and begins audio capture.
     *
     * @return True if listening was successfully initiated, false if blocked by permission or unavailability.
     */
    fun startListening(): Boolean {
        if (isDestroyed) return false

        if (!permissionChecker.hasPermission(context)) {
            val err = VoiceInputError.MIC_PERMISSION_DENIED
            _error.value = err
            _state.value = VoiceInputState.Error(err, err.userMessage)
            return false
        }

        if (!availabilityChecker.isRecognitionAvailable(context)) {
            val err = VoiceInputError.NOT_AVAILABLE
            _error.value = err
            _state.value = VoiceInputState.Error(err, err.userMessage)
            return false
        }

        _error.value = null
        _transcript.value = ""
        _confidence.value = 0.0f
        _rmsDb.value = 0.0f
        _state.value = VoiceInputState.Initializing

        try {
            ensureClientInitialized()
            val intent = config.createRecognizerIntent()
            speechClient?.startListening(intent)
            _isListening.value = true
            return true
        } catch (e: Exception) {
            _isListening.value = false
            val err = VoiceInputError.CLIENT_ERROR
            _error.value = err
            _state.value = VoiceInputState.Error(err, e.message ?: err.userMessage)
            return false
        }
    }

    /**
     * Requests the speech recognizer to stop recording audio and begin finalizing the transcript.
     */
    fun stopListening() {
        if (isDestroyed) return
        try {
            speechClient?.stopListening()
            _isListening.value = false
            _state.value = VoiceInputState.Processing(_transcript.value)
        } catch (e: Exception) {
            // Ignore stop errors on dead clients
        }
    }

    /**
     * Cancels the active speech recognition session immediately without processing results.
     */
    fun cancel() {
        if (isDestroyed) return
        try {
            speechClient?.cancel()
        } catch (e: Exception) {
            // Ignore cancel errors
        } finally {
            _isListening.value = false
            _rmsDb.value = 0.0f
            _state.value = VoiceInputState.Idle
        }
    }

    /**
     * Resets all internal StateFlow streams to their initial idle values.
     */
    fun reset() {
        _isListening.value = false
        _transcript.value = ""
        _confidence.value = 0.0f
        _rmsDb.value = 0.0f
        _error.value = null
        _state.value = VoiceInputState.Idle
    }

    /**
     * Releases speech recognizer resources, destroys client bindings, and prevents further execution.
     * Safe to invoke from ViewModel onCleared() or Composable DisposableEffect.
     */
    fun destroy() {
        if (isDestroyed) return
        isDestroyed = true
        cancel()
        try {
            speechClient?.destroy()
        } catch (e: Exception) {
            // Ignore destroy errors
        } finally {
            speechClient = null
            reset()
        }
    }

    private fun ensureClientInitialized() {
        if (speechClient == null) {
            val client = recognizerFactory.create(context)
            client.setRecognitionListener(recognitionListener)
            speechClient = client
        }
    }
}
