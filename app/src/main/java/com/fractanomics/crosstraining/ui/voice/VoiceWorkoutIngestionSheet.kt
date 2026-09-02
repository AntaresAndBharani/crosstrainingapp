package com.fractanomics.crosstraining.ui.voice

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.MicOff
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.AssistChip
import androidx.compose.material3.AssistChipDefaults
import androidx.compose.material3.BottomSheetDefaults
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedCard
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.SheetState
import androidx.compose.material3.SuggestionChip
import androidx.compose.material3.SuggestionChipDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.fractanomics.crosstraining.data.ai.ParsedBlock
import com.fractanomics.crosstraining.data.ai.ParsedBlockSet
import com.fractanomics.crosstraining.data.model.BlockKind
import com.fractanomics.crosstraining.data.model.Exercise
import com.fractanomics.crosstraining.data.voice.VoiceInputError
import com.fractanomics.crosstraining.data.voice.VoiceInputState
import com.fractanomics.crosstraining.ui.components.AppNumericTextField
import com.fractanomics.crosstraining.ui.components.NumericInputSanitizer
import kotlin.math.abs
import kotlin.math.sin

// =============================================================================
// State & Domain Models
// =============================================================================

/**
 * Visual indicator status representing microphone and speech processing state.
 */
enum class VoiceIndicatorStatus {
    IDLE,
    LISTENING,   // Green feedback
    PROCESSING,  // Yellow / Amber feedback
    ERROR        // Red error feedback
}

/**
 * Immutable UI State bundle for the Voice Workout Ingestion Sheet.
 *
 * @property voiceState Underlying speech recognition engine state.
 * @property transcript Current speech-to-text transcript (partial or final).
 * @property rmsDb Current microphone sound amplitude in decibels.
 * @property parsedBlocks Structured workout blocks extracted by AI.
 * @property disambiguationCandidates Mapping from block index to candidate exercises when ambiguous.
 * @property isListening Explicit override or mirror of listening status.
 * @property isProcessing Explicit override or mirror of processing status.
 * @property errorMessage User-facing error message if in error state.
 * @property isFullScreen True to layout in full-screen mode, false for bottom modal sheet layout.
 */
data class VoiceWorkoutUiState(
    val voiceState: VoiceInputState = VoiceInputState.Idle,
    val transcript: String = "",
    val rmsDb: Float = 0.0f,
    val parsedBlocks: List<ParsedBlock> = emptyList(),
    val disambiguationCandidates: Map<Int, List<Exercise>> = emptyMap(),
    val isListening: Boolean = false,
    val isProcessing: Boolean = false,
    val errorMessage: String? = null,
    val isFullScreen: Boolean = false
) {
    val activeIsListening: Boolean
        get() = isListening || voiceState is VoiceInputState.Listening

    val activeIsProcessing: Boolean
        get() = isProcessing || voiceState is VoiceInputState.Processing

    val activeErrorMessage: String?
        get() = errorMessage ?: (voiceState as? VoiceInputState.Error)?.message

    val activeError: VoiceInputError?
        get() = (voiceState as? VoiceInputState.Error)?.error

    val canConfirmSave: Boolean
        get() = parsedBlocks.isNotEmpty() && !activeIsProcessing
}

// =============================================================================
// Pure State Operations & Helper Functions
// =============================================================================

/**
 * Pure functions providing immutable state transformations and calculations for workout ingestion.
 */
object VoiceWorkoutStateOperations {

    fun updateBlock(
        blocks: List<ParsedBlock>,
        index: Int,
        updated: ParsedBlock
    ): List<ParsedBlock> {
        if (index !in blocks.indices) return blocks
        return blocks.toMutableList().apply { set(index, updated) }
    }

    fun updateBlockName(
        blocks: List<ParsedBlock>,
        index: Int,
        newName: String
    ): List<ParsedBlock> {
        if (index !in blocks.indices) return blocks
        val current = blocks[index]
        return updateBlock(blocks, index, current.copy(name = newName.trim()))
    }

    fun updateBlockKind(
        blocks: List<ParsedBlock>,
        index: Int,
        newKind: BlockKind
    ): List<ParsedBlock> {
        if (index !in blocks.indices) return blocks
        val current = blocks[index]
        return updateBlock(blocks, index, current.copy(kind = newKind))
    }

    fun updateBlockFormat(
        blocks: List<ParsedBlock>,
        index: Int,
        newFormat: String
    ): List<ParsedBlock> {
        if (index !in blocks.indices) return blocks
        val current = blocks[index]
        return updateBlock(blocks, index, current.copy(format = newFormat.trim()))
    }

    fun addBlock(
        blocks: List<ParsedBlock>,
        newBlock: ParsedBlock = ParsedBlock(
            name = "New Exercise",
            kind = BlockKind.STRENGTH,
            sets = listOf(ParsedBlockSet(reps = 5, weight = null))
        )
    ): List<ParsedBlock> {
        return blocks + newBlock
    }

    fun removeBlock(
        blocks: List<ParsedBlock>,
        index: Int
    ): List<ParsedBlock> {
        if (index !in blocks.indices) return blocks
        return blocks.toMutableList().apply { removeAt(index) }
    }

    fun updateSet(
        blocks: List<ParsedBlock>,
        blockIndex: Int,
        setIndex: Int,
        updatedSet: ParsedBlockSet
    ): List<ParsedBlock> {
        if (blockIndex !in blocks.indices) return blocks
        val block = blocks[blockIndex]
        if (setIndex !in block.sets.indices) return blocks
        val newSets = block.sets.toMutableList().apply { set(setIndex, updatedSet) }
        return updateBlock(blocks, blockIndex, block.copy(sets = newSets))
    }

    fun updateSetReps(
        blocks: List<ParsedBlock>,
        blockIndex: Int,
        setIndex: Int,
        reps: Int
    ): List<ParsedBlock> {
        if (blockIndex !in blocks.indices) return blocks
        val block = blocks[blockIndex]
        if (setIndex !in block.sets.indices) return blocks
        val target = block.sets[setIndex]
        return updateSet(blocks, blockIndex, setIndex, target.copy(reps = reps.coerceAtLeast(1)))
    }

    fun updateSetWeight(
        blocks: List<ParsedBlock>,
        blockIndex: Int,
        setIndex: Int,
        weight: Double?
    ): List<ParsedBlock> {
        if (blockIndex !in blocks.indices) return blocks
        val block = blocks[blockIndex]
        if (setIndex !in block.sets.indices) return blocks
        val target = block.sets[setIndex]
        return updateSet(blocks, blockIndex, setIndex, target.copy(weight = weight?.coerceAtLeast(0.0)))
    }

    fun toggleSetWarmup(
        blocks: List<ParsedBlock>,
        blockIndex: Int,
        setIndex: Int
    ): List<ParsedBlock> {
        if (blockIndex !in blocks.indices) return blocks
        val block = blocks[blockIndex]
        if (setIndex !in block.sets.indices) return blocks
        val target = block.sets[setIndex]
        return updateSet(blocks, blockIndex, setIndex, target.copy(isWarmup = !target.isWarmup))
    }

    fun addSet(
        blocks: List<ParsedBlock>,
        blockIndex: Int,
        newSet: ParsedBlockSet? = null
    ): List<ParsedBlock> {
        if (blockIndex !in blocks.indices) return blocks
        val block = blocks[blockIndex]
        val template = newSet ?: block.sets.lastOrNull()?.copy() ?: ParsedBlockSet(reps = 5, weight = null)
        val newSets = block.sets + template
        return updateBlock(blocks, blockIndex, block.copy(sets = newSets))
    }

    fun removeSet(
        blocks: List<ParsedBlock>,
        blockIndex: Int,
        setIndex: Int
    ): List<ParsedBlock> {
        if (blockIndex !in blocks.indices) return blocks
        val block = blocks[blockIndex]
        if (setIndex !in block.sets.indices) return blocks
        // Keep at least 1 set
        if (block.sets.size <= 1) return blocks
        val newSets = block.sets.toMutableList().apply { removeAt(setIndex) }
        return updateBlock(blocks, blockIndex, block.copy(sets = newSets))
    }

    fun applyDisambiguation(
        blocks: List<ParsedBlock>,
        candidatesMap: Map<Int, List<Exercise>>,
        blockIndex: Int,
        selectedExercise: Exercise
    ): Pair<List<ParsedBlock>, Map<Int, List<Exercise>>> {
        val updatedBlocks = updateBlockName(blocks, blockIndex, selectedExercise.name)
        val updatedCandidates = candidatesMap.toMutableMap().apply { remove(blockIndex) }
        return Pair(updatedBlocks, updatedCandidates)
    }

    fun totalSetCount(blocks: List<ParsedBlock>): Int {
        return blocks.sumOf { it.sets.size }
    }

    fun formatWeight(weight: Double?): String {
        if (weight == null) return ""
        return if (weight % 1.0 == 0.0) weight.toLong().toString() else weight.toString()
    }

    fun parseWeight(text: String): Double? {
        val clean = text.trim()
        if (clean.isBlank()) return null
        return clean.toDoubleOrNull()
    }

    fun parseReps(text: String): Int {
        val clean = text.trim()
        return clean.toIntOrNull()?.coerceAtLeast(1) ?: 1
    }
}

// =============================================================================
// Waveform Visualizer Logic & Helpers
// =============================================================================

object WaveformVisualizerHelper {
    const val DEFAULT_BAR_COUNT = 9
    const val MIN_RMS_DB = -2.0f
    const val MAX_RMS_DB = 10.0f

    /**
     * Normalizes decibel input to [0.05f .. 1.0f] visual height scale.
     */
    fun normalizeRmsDb(
        rmsDb: Float,
        minDb: Float = MIN_RMS_DB,
        maxDb: Float = MAX_RMS_DB
    ): Float {
        if (rmsDb <= minDb) return 0.05f
        if (rmsDb >= maxDb) return 1.0f
        return ((rmsDb - minDb) / (maxDb - minDb)).coerceIn(0.05f, 1.0f)
    }

    /**
     * Computes bar heights array normalized between 0.05 and 1.0 for waveform drawing.
     */
    fun calculateBarHeights(
        rmsDb: Float,
        barCount: Int = DEFAULT_BAR_COUNT,
        isListening: Boolean = true,
        phaseOffset: Float = 0.0f
    ): List<Float> {
        if (!isListening) {
            return List(barCount) { 0.12f }
        }
        val normalized = normalizeRmsDb(rmsDb)
        val center = (barCount - 1) / 2.0f

        return List(barCount) { index ->
            val distanceFromCenter = abs(index - center) / center
            val bellCurve = 1.0f - (distanceFromCenter * 0.45f)
            val sinusoidal = (sin((index.toDouble() + phaseOffset) * Math.PI / 2.5).toFloat() * 0.15f)
            (normalized * bellCurve + sinusoidal).coerceIn(0.08f, 1.0f)
        }
    }

    /**
     * Determines the status color category based on active voice state flags.
     */
    fun resolveStatus(
        voiceState: VoiceInputState,
        isListening: Boolean = false,
        isProcessing: Boolean = false,
        hasError: Boolean = false
    ): VoiceIndicatorStatus {
        return when {
            hasError || voiceState is VoiceInputState.Error -> VoiceIndicatorStatus.ERROR
            isProcessing || voiceState is VoiceInputState.Processing -> VoiceIndicatorStatus.PROCESSING
            isListening || voiceState is VoiceInputState.Listening -> VoiceIndicatorStatus.LISTENING
            else -> VoiceIndicatorStatus.IDLE
        }
    }

    /**
     * Standard color tokens matching UI/UX requirements:
     * - Green for listening
     * - Yellow / Amber for processing
     * - Red for error
     */
    val GreenListening = Color(0xFF10B981)
    val GreenListeningLight = Color(0xFF059669)
    val YellowProcessing = Color(0xFFF59E0B)
    val RedError = Color(0xFFEF4444)
    val NeutralIdle = Color(0xFF64748B)

    fun resolveColor(status: VoiceIndicatorStatus): Color {
        return when (status) {
            VoiceIndicatorStatus.LISTENING -> GreenListening
            VoiceIndicatorStatus.PROCESSING -> YellowProcessing
            VoiceIndicatorStatus.ERROR -> RedError
            VoiceIndicatorStatus.IDLE -> NeutralIdle
        }
    }
}

// =============================================================================
// Top-Level Stateless Composable: VoiceWorkoutIngestionSheet
// =============================================================================

/**
 * Main stateless Compose sheet component for real-time voice-driven workout ingestion.
 *
 * Supports both modal sheet and full-screen layouts via [isFullScreen].
 * Receives all parent ViewModel intents as lambdas, adhering strictly to Unidirectional Data Flow.
 */
@Composable
fun VoiceWorkoutIngestionSheet(
    state: VoiceWorkoutUiState,
    onToggleListening: () -> Unit,
    onConfirmSave: (List<ParsedBlock>) -> Unit,
    onCancel: () -> Unit,
    modifier: Modifier = Modifier,
    isFullScreen: Boolean = state.isFullScreen,
    onBlockUpdated: (Int, ParsedBlock) -> Unit = { _, _ -> },
    onAddBlock: () -> Unit = {},
    onRemoveBlock: (Int) -> Unit = {},
    onSetUpdated: (Int, Int, ParsedBlockSet) -> Unit = { _, _, _ -> },
    onAddSet: (Int) -> Unit = {},
    onRemoveSet: (Int, Int) -> Unit = { _, _ -> },
    onDisambiguationSelected: (Int, Exercise) -> Unit = { _, _ -> },
    onTranscriptChange: (String) -> Unit = {}
) {
    val indicatorStatus = WaveformVisualizerHelper.resolveStatus(
        voiceState = state.voiceState,
        isListening = state.activeIsListening,
        isProcessing = state.activeIsProcessing,
        hasError = state.activeErrorMessage != null
    )

    Surface(
        modifier = modifier
            .fillMaxWidth()
            .then(if (isFullScreen) Modifier.fillMaxSize() else Modifier.heightIn(min = 420.dp, max = 740.dp))
            .semantics { contentDescription = "Voice Workout Ingestion Sheet" },
        color = MaterialTheme.colorScheme.surface,
        tonalElevation = 2.dp,
        shape = if (isFullScreen) RoundedCornerShape(0.dp) else RoundedCornerShape(topStart = 20.dp, topEnd = 20.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 16.dp, vertical = 12.dp)
        ) {
            // Drag Handle for Modal Layout
            if (!isFullScreen) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = 8.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Box(
                        modifier = Modifier
                            .width(40.dp)
                            .height(4.dp)
                            .clip(RoundedCornerShape(2.dp))
                            .background(MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f))
                    )
                }
            }

            // Top Header: Title, Status Badge, Close/Cancel Button
            VoiceSheetHeader(
                indicatorStatus = indicatorStatus,
                isFullScreen = isFullScreen,
                onCancel = onCancel
            )

            Spacer(Modifier.height(10.dp))

            // Microphone Visualizer & Listening Toggle
            VoiceWaveformVisualizer(
                rmsDb = state.rmsDb,
                isListening = state.activeIsListening,
                isProcessing = state.activeIsProcessing,
                indicatorStatus = indicatorStatus,
                onToggleListening = onToggleListening
            )

            Spacer(Modifier.height(12.dp))

            // Error Banner if present
            AnimatedVisibility(
                visible = state.activeErrorMessage != null,
                enter = fadeIn(),
                exit = fadeOut()
            ) {
                state.activeErrorMessage?.let { errMsg ->
                    VoiceErrorBanner(message = errMsg)
                    Spacer(Modifier.height(8.dp))
                }
            }

            // Live Transcript Display with Auto-Scroll
            VoiceTranscriptDisplay(
                transcript = state.transcript,
                isListening = state.activeIsListening,
                isProcessing = state.activeIsProcessing,
                onTranscriptChange = onTranscriptChange
            )

            Spacer(Modifier.height(12.dp))

            // Parsed Workout Cards List (Editable) with Disambiguation Chips
            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
            ) {
                ParsedWorkoutCardsList(
                    blocks = state.parsedBlocks,
                    disambiguationCandidates = state.disambiguationCandidates,
                    onBlockUpdated = onBlockUpdated,
                    onAddBlock = onAddBlock,
                    onRemoveBlock = onRemoveBlock,
                    onSetUpdated = onSetUpdated,
                    onAddSet = onAddSet,
                    onRemoveSet = onRemoveSet,
                    onDisambiguationSelected = onDisambiguationSelected
                )
            }

            Spacer(Modifier.height(12.dp))

            // Bottom Action Buttons: Confirm & Save vs Cancel
            VoiceActionButtonsBar(
                canConfirm = state.canConfirmSave,
                isProcessing = state.activeIsProcessing,
                blockCount = state.parsedBlocks.size,
                setCount = VoiceWorkoutStateOperations.totalSetCount(state.parsedBlocks),
                onConfirmSave = { onConfirmSave(state.parsedBlocks) },
                onCancel = onCancel
            )
        }
    }
}

// =============================================================================
// Modal Bottom Sheet Wrapper
// =============================================================================

/**
 * Standard Material 3 Modal Bottom Sheet wrapper for [VoiceWorkoutIngestionSheet].
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun VoiceWorkoutIngestionModalSheet(
    onDismissRequest: () -> Unit,
    state: VoiceWorkoutUiState,
    onToggleListening: () -> Unit,
    onConfirmSave: (List<ParsedBlock>) -> Unit,
    modifier: Modifier = Modifier,
    sheetState: SheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
    onBlockUpdated: (Int, ParsedBlock) -> Unit = { _, _ -> },
    onAddBlock: () -> Unit = {},
    onRemoveBlock: (Int) -> Unit = {},
    onSetUpdated: (Int, Int, ParsedBlockSet) -> Unit = { _, _, _ -> },
    onAddSet: (Int) -> Unit = {},
    onRemoveSet: (Int, Int) -> Unit = { _, _ -> },
    onDisambiguationSelected: (Int, Exercise) -> Unit = { _, _ -> },
    onTranscriptChange: (String) -> Unit = {}
) {
    ModalBottomSheet(
        onDismissRequest = onDismissRequest,
        sheetState = sheetState,
        dragHandle = null,
        modifier = modifier
    ) {
        VoiceWorkoutIngestionSheet(
            state = state,
            isFullScreen = false,
            onToggleListening = onToggleListening,
            onConfirmSave = { blocks ->
                onConfirmSave(blocks)
                onDismissRequest()
            },
            onCancel = onDismissRequest,
            onBlockUpdated = onBlockUpdated,
            onAddBlock = onAddBlock,
            onRemoveBlock = onRemoveBlock,
            onSetUpdated = onSetUpdated,
            onAddSet = onAddSet,
            onRemoveSet = onRemoveSet,
            onDisambiguationSelected = onDisambiguationSelected,
            onTranscriptChange = onTranscriptChange
        )
    }
}

// =============================================================================
// Sheet Header Subcomponent
// =============================================================================

@Composable
private fun VoiceSheetHeader(
    indicatorStatus: VoiceIndicatorStatus,
    isFullScreen: Boolean,
    onCancel: () -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Text(
                text = "Voice Workout Ingestion",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold
            )

            // Status Indicator Pill
            val statusColor by animateColorAsState(
                targetValue = WaveformVisualizerHelper.resolveColor(indicatorStatus),
                label = "statusColor"
            )
            val statusLabel = when (indicatorStatus) {
                VoiceIndicatorStatus.LISTENING -> "Listening"
                VoiceIndicatorStatus.PROCESSING -> "Processing"
                VoiceIndicatorStatus.ERROR -> "Error"
                VoiceIndicatorStatus.IDLE -> "Idle"
            }

            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(12.dp))
                    .background(statusColor.copy(alpha = 0.18f))
                    .border(1.dp, statusColor.copy(alpha = 0.5f), RoundedCornerShape(12.dp))
                    .padding(horizontal = 8.dp, vertical = 2.dp)
            ) {
                Text(
                    text = statusLabel,
                    style = MaterialTheme.typography.labelSmall,
                    fontWeight = FontWeight.SemiBold,
                    color = statusColor
                )
            }
        }

        // Close / Cancel Icon Button
        IconButton(
            onClick = onCancel,
            modifier = Modifier
                .size(36.dp)
                .testTag("voice_cancel_icon_button")
                .semantics {
                    contentDescription = "Cancel and close voice ingestion"
                    role = Role.Button
                }
        ) {
            Icon(
                imageVector = Icons.Default.Close,
                contentDescription = "Close",
                tint = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

// =============================================================================
// Real-time Waveform Visualizer & Microphone Toggle
// =============================================================================

@Composable
fun VoiceWaveformVisualizer(
    rmsDb: Float,
    isListening: Boolean,
    isProcessing: Boolean,
    indicatorStatus: VoiceIndicatorStatus,
    onToggleListening: () -> Unit,
    modifier: Modifier = Modifier,
    barCount: Int = WaveformVisualizerHelper.DEFAULT_BAR_COUNT
) {
    val statusColor by animateColorAsState(
        targetValue = WaveformVisualizerHelper.resolveColor(indicatorStatus),
        label = "waveformStatusColor"
    )

    // Sinusoidal phase animation for dynamic waveform movement
    val infiniteTransition = rememberInfiniteTransition(label = "waveTransition")
    val phaseOffset by infiniteTransition.animateFloat(
        initialValue = 0.0f,
        targetValue = 6.28f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 1200, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "phaseOffset"
    )

    val barHeights = WaveformVisualizerHelper.calculateBarHeights(
        rmsDb = rmsDb,
        barCount = barCount,
        isListening = isListening,
        phaseOffset = if (isListening) phaseOffset else 0.0f
    )

    ElevatedCard(
        modifier = modifier
            .fillMaxWidth()
            .testTag("voice_waveform_visualizer")
            .semantics {
                contentDescription = "Microphone waveform visualizer: status $indicatorStatus"
            },
        colors = CardDefaults.elevatedCardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f)
        ),
        shape = RoundedCornerShape(16.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            // Left Waveform Bars
            Row(
                modifier = Modifier
                    .weight(1f)
                    .height(44.dp),
                horizontalArrangement = Arrangement.SpaceEvenly,
                verticalAlignment = Alignment.CenterVertically
            ) {
                barHeights.take(barCount / 2).forEachIndexed { idx, targetHeightFraction ->
                    val animatedHeight by animateFloatAsState(
                        targetValue = targetHeightFraction,
                        animationSpec = tween(durationMillis = 80),
                        label = "leftBar_$idx"
                    )
                    WaveformBar(
                        heightFraction = animatedHeight,
                        color = statusColor
                    )
                }
            }

            // Central Interactive Microphone Button
            Box(
                modifier = Modifier.padding(horizontal = 12.dp),
                contentAlignment = Alignment.Center
            ) {
                // Outer Pulse Ring when listening
                if (isListening) {
                    val ringAlpha by infiniteTransition.animateFloat(
                        initialValue = 0.4f,
                        targetValue = 0.0f,
                        animationSpec = infiniteRepeatable(
                            animation = tween(1000, easing = FastOutSlowInEasing),
                            repeatMode = RepeatMode.Restart
                        ),
                        label = "ringAlpha"
                    )
                    Box(
                        modifier = Modifier
                            .size(62.dp)
                            .clip(CircleShape)
                            .background(statusColor.copy(alpha = ringAlpha))
                    )
                }

                Surface(
                    onClick = onToggleListening,
                    modifier = Modifier
                        .size(48.dp)
                        .testTag("voice_mic_toggle_button")
                        .semantics {
                            contentDescription = if (isListening) "Stop listening" else "Start listening"
                            role = Role.Button
                        },
                    shape = CircleShape,
                    color = statusColor,
                    tonalElevation = 4.dp
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        if (isProcessing) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(24.dp),
                                color = MaterialTheme.colorScheme.surface,
                                strokeWidth = 2.5.dp
                            )
                        } else {
                            Icon(
                                imageVector = if (isListening) Icons.Default.Mic else Icons.Default.MicOff,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.surface,
                                modifier = Modifier.size(26.dp)
                            )
                        }
                    }
                }
            }

            // Right Waveform Bars
            Row(
                modifier = Modifier
                    .weight(1f)
                    .height(44.dp),
                horizontalArrangement = Arrangement.SpaceEvenly,
                verticalAlignment = Alignment.CenterVertically
            ) {
                barHeights.drop(barCount / 2).forEachIndexed { idx, targetHeightFraction ->
                    val animatedHeight by animateFloatAsState(
                        targetValue = targetHeightFraction,
                        animationSpec = tween(durationMillis = 80),
                        label = "rightBar_$idx"
                    )
                    WaveformBar(
                        heightFraction = animatedHeight,
                        color = statusColor
                    )
                }
            }
        }
    }
}

@Composable
private fun WaveformBar(
    heightFraction: Float,
    color: Color,
    modifier: Modifier = Modifier
) {
    val barHeight = (heightFraction * 40.dp.value).coerceIn(4.0f, 40.0f).dp

    Box(
        modifier = modifier
            .width(4.dp)
            .height(barHeight)
            .clip(RoundedCornerShape(2.dp))
            .background(color)
    )
}

// =============================================================================
// Live Transcript Display with Auto-Scroll
// =============================================================================

@Composable
fun VoiceTranscriptDisplay(
    transcript: String,
    isListening: Boolean,
    isProcessing: Boolean,
    modifier: Modifier = Modifier,
    onTranscriptChange: (String) -> Unit = {}
) {
    val scrollState = rememberScrollState()

    // Auto-scroll to bottom as new speech transcript accumulates
    LaunchedEffect(transcript) {
        if (transcript.isNotBlank()) {
            scrollState.animateScrollTo(scrollState.maxValue)
        }
    }

    OutlinedCard(
        modifier = modifier
            .fillMaxWidth()
            .testTag("voice_transcript_container")
            .semantics {
                contentDescription = "Spoken workout transcript"
                liveRegion = LiveRegionMode.Polite
            },
        colors = CardDefaults.outlinedCardColors(
            containerColor = MaterialTheme.colorScheme.surface
        ),
        shape = RoundedCornerShape(12.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Live Transcript",
                    style = MaterialTheme.typography.labelSmall,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                if (isListening) {
                    Text(
                        text = "Auto-scrolling...",
                        style = MaterialTheme.typography.labelSmall,
                        color = WaveformVisualizerHelper.GreenListening
                    )
                }
            }

            Spacer(Modifier.height(4.dp))

            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = 40.dp, max = 84.dp)
                    .verticalScroll(scrollState)
            ) {
                if (transcript.isBlank()) {
                    Text(
                        text = if (isListening) {
                            "Listening... Speak exercises, sets, and weights (e.g. '12 min EMOM of 15 wall balls' or '5 sets of 3 Back Squats at 100 kg')"
                        } else {
                            "Tap the microphone above to dictate training sessions, complexes, or completed sets."
                        },
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.outlineVariant
                    )
                } else {
                    Text(
                        text = transcript,
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.Normal,
                        color = MaterialTheme.colorScheme.onSurface,
                        modifier = Modifier.testTag("voice_transcript_text")
                    )
                }
            }
        }
    }
}

// =============================================================================
// Error Banner
// =============================================================================

@Composable
private fun VoiceErrorBanner(
    message: String,
    modifier: Modifier = Modifier
) {
    Surface(
        modifier = modifier
            .fillMaxWidth()
            .testTag("voice_error_banner"),
        shape = RoundedCornerShape(8.dp),
        color = MaterialTheme.colorScheme.errorContainer
    ) {
        Row(
            modifier = Modifier.padding(10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Icon(
                imageVector = Icons.Default.Warning,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.error,
                modifier = Modifier.size(20.dp)
            )
            Text(
                text = message,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onErrorContainer
            )
        }
    }
}

// =============================================================================
// Parsed Workout Cards List (Editable) with Disambiguation Chips
// =============================================================================

@Composable
fun ParsedWorkoutCardsList(
    blocks: List<ParsedBlock>,
    disambiguationCandidates: Map<Int, List<Exercise>>,
    onBlockUpdated: (Int, ParsedBlock) -> Unit,
    onAddBlock: () -> Unit,
    onRemoveBlock: (Int) -> Unit,
    onSetUpdated: (Int, Int, ParsedBlockSet) -> Unit,
    onAddSet: (Int) -> Unit,
    onRemoveSet: (Int, Int) -> Unit,
    onDisambiguationSelected: (Int, Exercise) -> Unit,
    modifier: Modifier = Modifier
) {
    if (blocks.isEmpty()) {
        Box(
            modifier = modifier
                .fillMaxSize()
                .testTag("voice_empty_blocks_state"),
            contentAlignment = Alignment.Center
        ) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Text(
                    text = "No structured workout parsed yet",
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.Medium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Text(
                    text = "Speak into the microphone or manually add a block below",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.outlineVariant
                )
                Spacer(Modifier.height(4.dp))
                OutlinedButton(
                    onClick = onAddBlock,
                    modifier = Modifier.testTag("voice_empty_add_block_button")
                ) {
                    Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(6.dp))
                    Text("Add Block Manually")
                }
            }
        }
    } else {
        val listState = rememberLazyListState()

        LazyColumn(
            state = listState,
            modifier = modifier
                .fillMaxSize()
                .testTag("voice_parsed_blocks_list"),
            verticalArrangement = Arrangement.spacedBy(10.dp),
            contentPadding = PaddingValues(bottom = 8.dp)
        ) {
            itemsIndexed(blocks, key = { index, _ -> "block_$index" }) { index, block ->
                ParsedBlockCard(
                    blockIndex = index,
                    block = block,
                    candidates = disambiguationCandidates[index] ?: emptyList(),
                    onBlockUpdated = { updated -> onBlockUpdated(index, updated) },
                    onRemoveBlock = { onRemoveBlock(index) },
                    onSetUpdated = { setIndex, updatedSet -> onSetUpdated(index, setIndex, updatedSet) },
                    onAddSet = { onAddSet(index) },
                    onRemoveSet = { setIndex -> onRemoveSet(index, setIndex) },
                    onDisambiguationSelected = { candidate -> onDisambiguationSelected(index, candidate) }
                )
            }

            item(key = "add_block_button_footer") {
                OutlinedButton(
                    onClick = onAddBlock,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 4.dp)
                        .testTag("voice_add_block_button"),
                    shape = RoundedCornerShape(10.dp)
                ) {
                    Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(8.dp))
                    Text("Add Another Block")
                }
            }
        }
    }
}

// =============================================================================
// Individual Parsed Workout Card
// =============================================================================

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun ParsedBlockCard(
    blockIndex: Int,
    block: ParsedBlock,
    candidates: List<Exercise>,
    onBlockUpdated: (ParsedBlock) -> Unit,
    onRemoveBlock: () -> Unit,
    onSetUpdated: (Int, ParsedBlockSet) -> Unit,
    onAddSet: () -> Unit,
    onRemoveSet: (Int) -> Unit,
    onDisambiguationSelected: (Exercise) -> Unit,
    modifier: Modifier = Modifier
) {
    var isEditingName by remember { mutableStateOf(false) }
    var nameBuffer by remember(block.name) { mutableStateOf(block.name) }

    ElevatedCard(
        modifier = modifier
            .fillMaxWidth()
            .testTag("voice_block_card_$blockIndex")
            .semantics { contentDescription = "Workout Block ${blockIndex + 1}: ${block.name}" },
        shape = RoundedCornerShape(14.dp),
        colors = CardDefaults.elevatedCardColors(
            containerColor = MaterialTheme.colorScheme.surface
        )
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp)
        ) {
            // Card Header: Exercise Name, Kind Badge, Format, Delete
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    if (isEditingName) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            OutlinedTextField(
                                value = nameBuffer,
                                onValueChange = { nameBuffer = it },
                                singleLine = true,
                                modifier = Modifier
                                    .weight(1f)
                                    .testTag("voice_edit_name_field_$blockIndex"),
                                textStyle = MaterialTheme.typography.titleMedium
                            )
                            IconButton(
                                onClick = {
                                    onBlockUpdated(block.copy(name = nameBuffer.trim()))
                                    isEditingName = false
                                },
                                modifier = Modifier.size(32.dp)
                            ) {
                                Icon(Icons.Default.Check, contentDescription = "Save name")
                            }
                        }
                    } else {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            Text(
                                text = block.name.ifBlank { "Untitled Exercise" },
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                            IconButton(
                                onClick = {
                                    nameBuffer = block.name
                                    isEditingName = true
                                },
                                modifier = Modifier
                                    .size(28.dp)
                                    .testTag("voice_edit_name_button_$blockIndex")
                                    .semantics { contentDescription = "Edit exercise name for block ${blockIndex + 1}" }
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Edit,
                                    contentDescription = "Edit",
                                    tint = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.size(16.dp)
                                )
                            }
                        }
                    }

                    // Kind & Format Pills
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.padding(top = 2.dp)
                    ) {
                        // Kind Badge
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(6.dp))
                                .background(MaterialTheme.colorScheme.primaryContainer)
                                .padding(horizontal = 6.dp, vertical = 2.dp)
                        ) {
                            Text(
                                text = block.kind.name,
                                style = MaterialTheme.typography.labelSmall,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onPrimaryContainer
                            )
                        }

                        if (block.format.isNotBlank()) {
                            Box(
                                modifier = Modifier
                                    .clip(RoundedCornerShape(6.dp))
                                    .background(MaterialTheme.colorScheme.secondaryContainer)
                                    .padding(horizontal = 6.dp, vertical = 2.dp)
                            ) {
                                Text(
                                    text = block.format,
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSecondaryContainer
                                )
                            }
                        }

                        if (block.rpe != null) {
                            Text(
                                text = "@ RPE ${block.rpe}",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }

                // Delete Block Button
                IconButton(
                    onClick = onRemoveBlock,
                    modifier = Modifier
                        .size(36.dp)
                        .testTag("voice_delete_block_${blockIndex}")
                        .semantics {
                            contentDescription = "Delete block ${blockIndex + 1}"
                            role = Role.Button
                        }
                ) {
                    Icon(
                        imageVector = Icons.Default.DeleteOutline,
                        contentDescription = "Delete block",
                        tint = MaterialTheme.colorScheme.error
                    )
                }
            }

            // Disambiguation Chips (1-2 Alternatives)
            if (candidates.isNotEmpty()) {
                Spacer(Modifier.height(8.dp))
                DisambiguationChipsRow(
                    blockIndex = blockIndex,
                    candidates = candidates,
                    onDisambiguationSelected = onDisambiguationSelected
                )
            }

            Spacer(Modifier.height(10.dp))

            // Spreadsheet Sets Header & Rows
            SpreadsheetSetsSection(
                blockIndex = blockIndex,
                sets = block.sets,
                onSetUpdated = onSetUpdated,
                onAddSet = onAddSet,
                onRemoveSet = onRemoveSet
            )
        }
    }
}

// =============================================================================
// Disambiguation Chips Row
// =============================================================================

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun DisambiguationChipsRow(
    blockIndex: Int,
    candidates: List<Exercise>,
    onDisambiguationSelected: (Exercise) -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(8.dp))
            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
            .padding(8.dp)
            .testTag("voice_disambiguation_container_$blockIndex")
    ) {
        Text(
            text = "Did you mean:",
            style = MaterialTheme.typography.labelSmall,
            fontWeight = FontWeight.SemiBold,
            color = MaterialTheme.colorScheme.primary
        )

        Spacer(Modifier.height(4.dp))

        FlowRow(
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            candidates.take(3).forEach { exercise ->
                SuggestionChip(
                    onClick = { onDisambiguationSelected(exercise) },
                    label = { Text(exercise.name, style = MaterialTheme.typography.labelSmall) },
                    modifier = Modifier
                        .testTag("voice_disambiguation_chip_${blockIndex}_${exercise.id}")
                        .semantics {
                            contentDescription = "Disambiguation suggestion: ${exercise.name}"
                            role = Role.Button
                        },
                    colors = SuggestionChipDefaults.suggestionChipColors(
                        containerColor = MaterialTheme.colorScheme.surface
                    )
                )
            }
        }
    }
}

// =============================================================================
// Spreadsheet Sets Section & Individual Rows
// =============================================================================

@Composable
private fun SpreadsheetSetsSection(
    blockIndex: Int,
    sets: List<ParsedBlockSet>,
    onSetUpdated: (Int, ParsedBlockSet) -> Unit,
    onAddSet: () -> Unit,
    onRemoveSet: (Int) -> Unit
) {
    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        // Table Header
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "SET",
                style = MaterialTheme.typography.labelSmall,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.width(36.dp),
                textAlign = TextAlign.Center
            )
            Text(
                text = "REPS",
                style = MaterialTheme.typography.labelSmall,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.weight(1f),
                textAlign = TextAlign.Center
            )
            Text(
                text = "KG / LOAD",
                style = MaterialTheme.typography.labelSmall,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.weight(1.3f),
                textAlign = TextAlign.Center
            )
            Text(
                text = "WARM",
                style = MaterialTheme.typography.labelSmall,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.width(42.dp),
                textAlign = TextAlign.Center
            )
            Spacer(Modifier.width(32.dp)) // Reserve space for delete icon
        }

        // Sets Rows
        sets.forEachIndexed { setIndex, set ->
            SpreadsheetSetItemRow(
                blockIndex = blockIndex,
                setIndex = setIndex,
                set = set,
                canRemove = sets.size > 1,
                onRepsChanged = { reps ->
                    onSetUpdated(setIndex, set.copy(reps = reps))
                },
                onWeightChanged = { weight ->
                    onSetUpdated(setIndex, set.copy(weight = weight))
                },
                onWarmupToggled = {
                    onSetUpdated(setIndex, set.copy(isWarmup = !set.isWarmup))
                },
                onRemove = { onRemoveSet(setIndex) }
            )
        }

        // Add Set Button
        TextButton(
            onClick = onAddSet,
            modifier = Modifier
                .fillMaxWidth()
                .testTag("voice_add_set_button_$blockIndex")
                .semantics {
                    contentDescription = "Add set to block ${blockIndex + 1}"
                    role = Role.Button
                },
            shape = RoundedCornerShape(8.dp)
        ) {
            Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(16.dp))
            Spacer(Modifier.width(4.dp))
            Text("Add Set", style = MaterialTheme.typography.labelMedium)
        }
    }
}

@Composable
fun SpreadsheetSetItemRow(
    blockIndex: Int,
    setIndex: Int,
    set: ParsedBlockSet,
    canRemove: Boolean,
    onRepsChanged: (Int) -> Unit,
    onWeightChanged: (Double?) -> Unit,
    onWarmupToggled: () -> Unit,
    onRemove: () -> Unit,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .testTag("voice_set_row_${blockIndex}_${setIndex}"),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        // Set Number Badge
        Box(
            modifier = Modifier
                .width(36.dp)
                .height(36.dp)
                .clip(RoundedCornerShape(6.dp))
                .background(MaterialTheme.colorScheme.surfaceVariant),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = "${setIndex + 1}",
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }

        // Reps Input Box
        Box(
            modifier = Modifier
                .weight(1f)
                .height(36.dp)
                .clip(RoundedCornerShape(6.dp))
                .background(MaterialTheme.colorScheme.surface)
                .border(1.dp, MaterialTheme.colorScheme.outlineVariant, RoundedCornerShape(6.dp))
                .padding(horizontal = 4.dp),
            contentAlignment = Alignment.Center
        ) {
            AppNumericTextField(
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("voice_reps_${blockIndex}_${setIndex}")
                    .semantics { contentDescription = "Reps for set ${setIndex + 1}" },
                value = set.reps.toString(),
                onValueChange = { str ->
                    val reps = VoiceWorkoutStateOperations.parseReps(str)
                    onRepsChanged(reps)
                },
                allowDecimals = false,
                isBasic = true,
                minValue = 1.0,
                maxValue = 999.0,
                textStyle = TextStyle(
                    fontSize = 14.sp,
                    fontWeight = FontWeight.SemiBold,
                    textAlign = TextAlign.Center,
                    color = MaterialTheme.colorScheme.onSurface
                ),
                placeholder = {
                    Text(
                        "1",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.outlineVariant,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            )
        }

        // Weight Input Box
        Box(
            modifier = Modifier
                .weight(1.3f)
                .height(36.dp)
                .clip(RoundedCornerShape(6.dp))
                .background(MaterialTheme.colorScheme.surface)
                .border(1.dp, MaterialTheme.colorScheme.outlineVariant, RoundedCornerShape(6.dp))
                .padding(horizontal = 4.dp),
            contentAlignment = Alignment.Center
        ) {
            AppNumericTextField(
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("voice_weight_${blockIndex}_${setIndex}")
                    .semantics { contentDescription = "Weight for set ${setIndex + 1}" },
                value = VoiceWorkoutStateOperations.formatWeight(set.weight),
                onValueChange = { str ->
                    val weight = VoiceWorkoutStateOperations.parseWeight(str)
                    onWeightChanged(weight)
                },
                allowDecimals = true,
                isBasic = true,
                minValue = 0.0,
                maxValue = 999.5,
                textStyle = TextStyle(
                    fontSize = 14.sp,
                    fontWeight = FontWeight.SemiBold,
                    textAlign = TextAlign.Center,
                    color = MaterialTheme.colorScheme.onSurface
                ),
                placeholder = {
                    Text(
                        "0.0",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.outlineVariant,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            )
        }

        // Warmup [W] Toggle Pill
        Box(
            modifier = Modifier
                .size(42.dp, 36.dp)
                .clip(RoundedCornerShape(6.dp))
                .background(
                    if (set.isWarmup) WaveformVisualizerHelper.YellowProcessing.copy(alpha = 0.85f)
                    else MaterialTheme.colorScheme.surface
                )
                .border(
                    1.dp,
                    if (set.isWarmup) WaveformVisualizerHelper.YellowProcessing
                    else MaterialTheme.colorScheme.outlineVariant,
                    RoundedCornerShape(6.dp)
                )
                .clickable(onClick = onWarmupToggled)
                .testTag("voice_warmup_toggle_${blockIndex}_${setIndex}")
                .semantics {
                    contentDescription = if (set.isWarmup) "Mark set ${setIndex + 1} as working set" else "Mark set ${setIndex + 1} as warmup"
                    role = Role.Checkbox
                },
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = "W",
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.Bold,
                color = if (set.isWarmup) MaterialTheme.colorScheme.surface else MaterialTheme.colorScheme.onSurfaceVariant
            )
        }

        // Delete Set Button
        IconButton(
            onClick = onRemove,
            enabled = canRemove,
            modifier = Modifier
                .size(32.dp)
                .testTag("voice_remove_set_${blockIndex}_${setIndex}")
                .semantics {
                    contentDescription = "Remove set ${setIndex + 1}"
                    role = Role.Button
                }
        ) {
            Icon(
                imageVector = Icons.Default.Close,
                contentDescription = null,
                tint = if (canRemove) MaterialTheme.colorScheme.error.copy(alpha = 0.8f) else MaterialTheme.colorScheme.outlineVariant,
                modifier = Modifier.size(16.dp)
            )
        }
    }
}

// =============================================================================
// Bottom Action Buttons Bar
// =============================================================================

@Composable
fun VoiceActionButtonsBar(
    canConfirm: Boolean,
    isProcessing: Boolean,
    blockCount: Int,
    setCount: Int,
    onConfirmSave: () -> Unit,
    onCancel: () -> Unit,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        horizontalArrangement = Arrangement.spacedBy(10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        OutlinedButton(
            onClick = onCancel,
            modifier = Modifier
                .weight(1f)
                .height(48.dp)
                .testTag("voice_cancel_button")
                .semantics {
                    contentDescription = "Cancel without saving"
                    role = Role.Button
                },
            shape = RoundedCornerShape(12.dp)
        ) {
            Text("Cancel", style = MaterialTheme.typography.labelLarge)
        }

        Button(
            onClick = onConfirmSave,
            enabled = canConfirm,
            modifier = Modifier
                .weight(1.8f)
                .height(48.dp)
                .testTag("voice_confirm_save_button")
                .semantics {
                    contentDescription = "Confirm & Save $blockCount blocks with $setCount sets"
                    role = Role.Button
                },
            shape = RoundedCornerShape(12.dp),
            colors = ButtonDefaults.buttonColors(
                containerColor = WaveformVisualizerHelper.GreenListeningLight
            )
        ) {
            if (isProcessing) {
                CircularProgressIndicator(
                    modifier = Modifier.size(20.dp),
                    color = MaterialTheme.colorScheme.surface,
                    strokeWidth = 2.dp
                )
                Spacer(Modifier.width(8.dp))
                Text("Processing...")
            } else {
                Icon(
                    imageVector = Icons.Default.Check,
                    contentDescription = null,
                    modifier = Modifier.size(18.dp)
                )
                Spacer(Modifier.width(6.dp))
                Text(
                    text = if (blockCount > 0) "Confirm & Save ($blockCount)" else "Confirm & Save",
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.Bold
                )
            }
        }
    }
}
