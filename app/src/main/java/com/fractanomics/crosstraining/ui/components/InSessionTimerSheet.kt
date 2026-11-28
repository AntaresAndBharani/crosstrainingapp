package com.fractanomics.crosstraining.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.SheetState
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.fractanomics.crosstraining.ui.timer.ContinuousTimerUiResolver
import com.fractanomics.crosstraining.ui.timer.QueuedTimerStage
import com.fractanomics.crosstraining.ui.timer.SessionTimerBlockEntry
import com.fractanomics.crosstraining.ui.timer.TimerEngine
import com.fractanomics.crosstraining.ui.timer.TimerMode
import com.fractanomics.crosstraining.ui.timer.TimerPhase
import com.fractanomics.crosstraining.ui.timer.WorkoutTimerQueueBuilder

private sealed class QueueSheetEditTarget {
    data class EditPreStart(val stage: QueuedTimerStage) : QueueSheetEditTarget()
    data object AddPreStart : QueueSheetEditTarget()
    data class EditLive(val stage: QueuedTimerStage) : QueueSheetEditTarget()
    data object AddLive : QueueSheetEditTarget()
    data object ContinueFromFinished : QueueSheetEditTarget()
}

/**
 * In-session modal bottom sheet presenting:
 * - Routine-aware pre-start queue setup with Block Anchor Strip, 6-mode starting configurator
 *   (defaulting to E3MOM × sets), and editable auto-extrapolated remaining queue (`1:00` transitions);
 * - Or active multi-stage continuous workout timer execution & live queue editing.
 */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun InSessionTimerSheet(
    timerEngine: TimerEngine,
    onDismiss: () -> Unit,
    sheetState: SheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
    prefillTitle: String = "",
    prefillRounds: Int = 1,
    sessionBlocks: List<SessionTimerBlockEntry> = emptyList(),
    initialAnchorIndex: Int = 0,
    onStartService: () -> Unit = {}
) {
    val snapshot by timerEngine.snapshot.collectAsStateWithLifecycle()
    val isEngineActive = snapshot.phase != TimerPhase.IDLE
    var editTarget by remember { mutableStateOf<QueueSheetEditTarget?>(null) }

    val baseConfig = timerEngine.currentConfig
    val hasSessionBlocks = sessionBlocks.isNotEmpty()
    val safeInitialAnchor = if (hasSessionBlocks) initialAnchorIndex.coerceIn(0, sessionBlocks.lastIndex) else 0

    var selectedAnchorIndex by remember(sessionBlocks, safeInitialAnchor) {
        mutableIntStateOf(safeInitialAnchor)
    }

    val initialBuiltQueue = remember(sessionBlocks, safeInitialAnchor) {
        if (hasSessionBlocks) {
            WorkoutTimerQueueBuilder.buildFromAnchor(
                entries = sessionBlocks,
                anchorIndex = safeInitialAnchor,
                baseConfig = baseConfig
            )
        } else {
            null
        }
    }

    var draftConfig by remember(sessionBlocks, safeInitialAnchor, prefillTitle, prefillRounds) {
        mutableStateOf(
            initialBuiltQueue?.startingConfig ?: defaultConfigForMode(
                mode = baseConfig.mode,
                soundEnabled = baseConfig.soundEnabled,
                vibrationEnabled = baseConfig.vibrationEnabled
            ).copy(
                totalRounds = if (prefillRounds > 1) prefillRounds else baseConfig.mode.defaultRounds,
                prepCountdownSeconds = baseConfig.prepCountdownSeconds,
                workoutLabel = prefillTitle
            )
        )
    }

    var draftQueuedStages by remember(sessionBlocks, safeInitialAnchor) {
        mutableStateOf(initialBuiltQueue?.queuedStages ?: snapshot.queuedStages)
    }
    var hasCustomizedQueue by remember(sessionBlocks, safeInitialAnchor) {
        mutableStateOf(false)
    }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .imePadding()
                .padding(horizontal = 20.dp, vertical = 12.dp)
                .verticalScroll(rememberScrollState()),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            // Sheet Header Row
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                val sheetTitle = when {
                    isEngineActive && snapshot.workoutLabel.isNotBlank() -> snapshot.workoutLabel
                    !isEngineActive && hasSessionBlocks ->
                        sessionBlocks.getOrNull(selectedAnchorIndex)?.title?.ifBlank { "Workout Timer Queue" }
                            ?: "Workout Timer Queue"
                    snapshot.workoutLabel.isNotBlank() -> snapshot.workoutLabel
                    else -> prefillTitle.ifBlank { "Workout Timer" }
                }
                Text(
                    text = sheetTitle,
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f)
                )

                IconButton(
                    onClick = onDismiss,
                    modifier = Modifier.defaultMinSize(minWidth = 48.dp, minHeight = 48.dp)
                ) {
                    Icon(Icons.Filled.Close, contentDescription = "Close Sheet")
                }
            }

            if (isEngineActive) {
                ActiveContinuousTimerCard(
                    snapshot = snapshot,
                    timerEngine = timerEngine,
                    onStartService = onStartService,
                    onOpenQueueSheet = { fromFinished ->
                        editTarget = if (fromFinished) {
                            QueueSheetEditTarget.ContinueFromFinished
                        } else {
                            snapshot.queuedNextStage?.let { QueueSheetEditTarget.EditLive(it) }
                                ?: QueueSheetEditTarget.AddLive
                        }
                    },
                    onEditQueuedStage = { stage ->
                        editTarget = QueueSheetEditTarget.EditLive(stage)
                    },
                    onRemoveQueuedStage = { stageId ->
                        timerEngine.removeQueuedStage(stageId)
                    },
                    onAddQueuedStage = { fromFinished ->
                        editTarget = if (fromFinished) {
                            QueueSheetEditTarget.ContinueFromFinished
                        } else {
                            QueueSheetEditTarget.AddLive
                        }
                    }
                )
            } else if (hasSessionBlocks) {
                // Routine-aware Block Anchor Strip + Starting Configurator + Extrapolated Queue Timeline
                val reAnchorTo: (Int) -> Unit = { newIndex ->
                    val rebuilt = WorkoutTimerQueueBuilder.buildFromAnchor(
                        entries = sessionBlocks,
                        anchorIndex = newIndex,
                        baseConfig = draftConfig
                    )
                    if (rebuilt != null) {
                        selectedAnchorIndex = rebuilt.anchorIndex
                        draftConfig = rebuilt.startingConfig
                        draftQueuedStages = rebuilt.queuedStages
                        hasCustomizedQueue = false
                    }
                }

                Column(
                    modifier = Modifier.fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Text(
                        text = "Start Queue From Block:",
                        style = MaterialTheme.typography.labelLarge,
                        fontWeight = FontWeight.Bold
                    )

                    LazyRow(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        itemsIndexed(
                            items = sessionBlocks,
                            key = { _, entry -> entry.blockKey }
                        ) { idx, entry ->
                            FilterChip(
                                selected = idx == selectedAnchorIndex,
                                onClick = { reAnchorTo(idx) },
                                modifier = Modifier.heightIn(min = 48.dp),
                                label = {
                                    Text(
                                        text = "${entry.positionIndex + 1}. ${entry.title}",
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                }
                            )
                        }
                    }

                    HorizontalDivider()

                    Text(
                        text = "Starting Block Mode",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )

                    FlowRow(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        val anchoredEntry = sessionBlocks.getOrNull(selectedAnchorIndex)
                        TimerMode.entries.forEach { mode ->
                            FilterChip(
                                selected = draftConfig.mode == mode,
                                onClick = {
                                    val anchorRounds = (anchoredEntry?.defaultSetCount ?: draftConfig.totalRounds).coerceAtLeast(1)
                                    val modeDefaults = defaultConfigForMode(
                                        mode = mode,
                                        soundEnabled = draftConfig.soundEnabled,
                                        vibrationEnabled = draftConfig.vibrationEnabled
                                    )
                                    draftConfig = modeDefaults.copy(
                                        intervalSeconds = if (mode == TimerMode.EMOM) {
                                            WorkoutTimerQueueBuilder.DEFAULT_EXMOM_INTERVAL_SECONDS
                                        } else {
                                            modeDefaults.intervalSeconds
                                        },
                                        totalRounds = if (mode == TimerMode.EMOM) anchorRounds else modeDefaults.totalRounds,
                                        prepCountdownSeconds = draftConfig.prepCountdownSeconds,
                                        workoutLabel = anchoredEntry?.title?.trim() ?: draftConfig.workoutLabel,
                                        sourceBlockKey = anchoredEntry?.blockKey ?: draftConfig.sourceBlockKey
                                    )
                                },
                                label = { Text(mode.label) }
                            )
                        }
                    }

                    TimerModeSetupForm(
                        config = draftConfig,
                        onConfigChange = { draftConfig = it },
                        showPrepAndAlertOptions = true
                    )

                    HorizontalDivider()

                    WorkoutQueueTimelinePreview(
                        startingConfig = draftConfig,
                        queuedStages = draftQueuedStages,
                        onEditStage = { stage -> editTarget = QueueSheetEditTarget.EditPreStart(stage) },
                        onRemoveStage = { stageId ->
                            draftQueuedStages = draftQueuedStages.filterNot { it.id == stageId }
                            hasCustomizedQueue = true
                        },
                        onAddStage = { editTarget = QueueSheetEditTarget.AddPreStart },
                        showResetDefaults = hasCustomizedQueue,
                        onResetDefaults = { reAnchorTo(selectedAnchorIndex) }
                    )

                    val totalBlocksCount = 1 + draftQueuedStages.size
                    val totalQueueSecs = WorkoutTimerQueueBuilder.calculateQueueTotalSeconds(
                        startingConfig = draftConfig,
                        queuedStages = draftQueuedStages
                    )
                    val startLabel = if (totalBlocksCount > 1) {
                        "START QUEUE ($totalBlocksCount Blocks • ${ContinuousTimerUiResolver.formatClock(totalQueueSecs)})"
                    } else {
                        "START TIMER"
                    }

                    Button(
                        onClick = {
                            timerEngine.configureQueue(draftConfig, draftQueuedStages)
                            timerEngine.start()
                            onStartService()
                        },
                        shape = RoundedCornerShape(999.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(52.dp)
                    ) {
                        Icon(Icons.Filled.PlayArrow, contentDescription = null)
                        Spacer(Modifier.width(6.dp))
                        Text(
                            text = startLabel,
                            fontWeight = FontWeight.Bold,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }
            } else {
                // Fallback when opened without session block context
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Text(
                        text = "Timer Setup",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )

                    FlowRow(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        TimerMode.entries.forEach { mode ->
                            FilterChip(
                                selected = draftConfig.mode == mode,
                                onClick = {
                                    draftConfig = defaultConfigForMode(
                                        mode = mode,
                                        soundEnabled = draftConfig.soundEnabled,
                                        vibrationEnabled = draftConfig.vibrationEnabled
                                    ).copy(
                                        prepCountdownSeconds = draftConfig.prepCountdownSeconds,
                                        workoutLabel = prefillTitle
                                    )
                                },
                                label = { Text(mode.label) }
                            )
                        }
                    }

                    TimerModeSetupForm(
                        config = draftConfig,
                        onConfigChange = { draftConfig = it },
                        showPrepAndAlertOptions = true
                    )

                    HorizontalDivider()

                    WorkoutQueueTimelinePreview(
                        startingConfig = draftConfig,
                        queuedStages = draftQueuedStages,
                        onEditStage = { stage -> editTarget = QueueSheetEditTarget.EditPreStart(stage) },
                        onRemoveStage = { stageId ->
                            draftQueuedStages = draftQueuedStages.filterNot { it.id == stageId }
                            timerEngine.setQueuedStages(draftQueuedStages)
                        },
                        onAddStage = { editTarget = QueueSheetEditTarget.AddPreStart }
                    )

                    Button(
                        onClick = {
                            timerEngine.configureQueue(
                                draftConfig.copy(workoutLabel = prefillTitle),
                                draftQueuedStages
                            )
                            timerEngine.start()
                            onStartService()
                        },
                        shape = RoundedCornerShape(999.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(52.dp)
                    ) {
                        Icon(Icons.Filled.PlayArrow, contentDescription = null)
                        Spacer(Modifier.width(6.dp))
                        Text("START TIMER", fontWeight = FontWeight.Bold)
                    }
                }
            }

            Spacer(Modifier.height(16.dp))
        }
    }

    val activeEditTarget = editTarget
    if (activeEditTarget != null) {
        val existingStage = when (activeEditTarget) {
            is QueueSheetEditTarget.EditPreStart -> activeEditTarget.stage
            is QueueSheetEditTarget.EditLive -> activeEditTarget.stage
            QueueSheetEditTarget.AddPreStart,
            QueueSheetEditTarget.AddLive,
            QueueSheetEditTarget.ContinueFromFinished -> null
        }
        val isContinue = activeEditTarget is QueueSheetEditTarget.ContinueFromFinished
        val soundEnabled = if (isEngineActive) snapshot.activeConfig.soundEnabled else draftConfig.soundEnabled
        val vibrationEnabled = if (isEngineActive) snapshot.activeConfig.vibrationEnabled else draftConfig.vibrationEnabled

        NextModeConfigSheet(
            existingStage = existingStage,
            baseSoundEnabled = soundEnabled,
            baseVibrationEnabled = vibrationEnabled,
            isContinueFromFinished = isContinue,
            onDismiss = { editTarget = null },
            onSaveStage = { saved ->
                when (activeEditTarget) {
                    is QueueSheetEditTarget.EditPreStart -> {
                        draftQueuedStages = draftQueuedStages.map {
                            if (it.id == activeEditTarget.stage.id) saved.copy(id = it.id) else it
                        }
                        if (!hasSessionBlocks) {
                            timerEngine.setQueuedStages(draftQueuedStages)
                        }
                        hasCustomizedQueue = true
                    }
                    QueueSheetEditTarget.AddPreStart -> {
                        draftQueuedStages = draftQueuedStages + saved
                        if (!hasSessionBlocks) {
                            timerEngine.setQueuedStages(draftQueuedStages)
                        }
                        hasCustomizedQueue = true
                    }
                    is QueueSheetEditTarget.EditLive -> {
                        timerEngine.updateQueuedStage(activeEditTarget.stage.id, saved)
                    }
                    QueueSheetEditTarget.AddLive -> {
                        timerEngine.appendQueuedStage(saved)
                    }
                    QueueSheetEditTarget.ContinueFromFinished -> {
                        if (snapshot.phase == TimerPhase.FINISHED) {
                            timerEngine.continueFromFinished(saved.config, saved.transitionSeconds)
                            onStartService()
                        } else {
                            timerEngine.appendQueuedStage(saved)
                        }
                    }
                }
            },
            onClearStage = {
                when (activeEditTarget) {
                    is QueueSheetEditTarget.EditPreStart -> {
                        draftQueuedStages = draftQueuedStages.filterNot { it.id == activeEditTarget.stage.id }
                        if (!hasSessionBlocks) {
                            timerEngine.setQueuedStages(draftQueuedStages)
                        }
                        hasCustomizedQueue = true
                    }
                    is QueueSheetEditTarget.EditLive -> {
                        timerEngine.removeQueuedStage(activeEditTarget.stage.id)
                    }
                    else -> {}
                }
            }
        )
    }
}
