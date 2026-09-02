package com.fractanomics.crosstraining.data

import com.fractanomics.crosstraining.data.ai.ParsedBlock
import com.fractanomics.crosstraining.data.model.BlockSet
import com.fractanomics.crosstraining.data.model.Exercise
import com.fractanomics.crosstraining.data.model.Session

/**
 * State machine representing the end-to-end voice workout ingestion lifecycle.
 *
 * States:
 * - [Idle]: Waiting for user voice interaction.
 * - [Listening]: Voice capture active; capturing microphone audio and streaming partial transcripts.
 * - [Parsing]: Audio finalized; AI inference engine / speech lexicon parsing transcript into structured workout blocks.
 * - [Disambiguating]: Inexact movement names detected (e.g. "Clean" -> "Power Clean" vs "Squat Clean").
 *   UI prompts user with candidate chips; user selection committed to Room DB.
 * - [Saving]: Writing structured Session, SessionBlocks, and BlockSets to Room inside an atomic transaction.
 * - [Complete]: Workout session or live block set successfully persisted to Room SQLite.
 * - [Error]: Ingestion failed (permission, audio, AI, or DB error) with user-facing message and retry option.
 */
sealed class VoiceIngestionState {
    data object Idle : VoiceIngestionState()

    data class Listening(
        val partialTranscript: String = "",
        val rmsDb: Float = 0.0f
    ) : VoiceIngestionState()

    data class Parsing(
        val transcript: String
    ) : VoiceIngestionState()

    data class Disambiguating(
        val transcript: String,
        val parsedBlocks: List<ParsedBlock>,
        val ambiguousBlocks: Map<Int, List<Exercise>>,
        val resolvedExercises: Map<Int, Exercise> = emptyMap()
    ) : VoiceIngestionState()

    data class Saving(
        val transcript: String
    ) : VoiceIngestionState()

    data class Complete(
        val session: Session? = null,
        val appendedSet: BlockSet? = null,
        val message: String = "Workout persisted successfully"
    ) : VoiceIngestionState()

    data class Error(
        val message: String,
        val canRetry: Boolean = true,
        val lastVoiceText: String = ""
    ) : VoiceIngestionState()

    val isListening: Boolean get() = this is Listening
    val isParsing: Boolean get() = this is Parsing
    val isDisambiguating: Boolean get() = this is Disambiguating
    val isSaving: Boolean get() = this is Saving
    val isComplete: Boolean get() = this is Complete
    val isError: Boolean get() = this is Error
}

/**
 * Result of voice parsing before relational persistence.
 *
 * @property transcript The normalized voice transcript.
 * @property blocks The extracted structured workout blocks.
 * @property ambiguousExercises Mapping from block index to list of candidate exercises when inexact.
 */
data class VoiceParseResult(
    val transcript: String,
    val blocks: List<ParsedBlock>,
    val ambiguousExercises: Map<Int, List<Exercise>> = emptyMap()
)

/**
 * Interface abstraction for running database operations inside an atomic transaction.
 * Defaults to Room's native [androidx.room.withTransaction] in production, while enabling
 * deterministic transactional simulation and rollback assertions in unit/integration tests.
 */
interface TransactionRunner {
    suspend fun <R> runInTransaction(block: suspend () -> R): R
}
