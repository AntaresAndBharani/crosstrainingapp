package com.fractanomics.crosstraining.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
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
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.fractanomics.crosstraining.ui.timer.TimerEngine
import com.fractanomics.crosstraining.ui.timer.TimerMode
import com.fractanomics.crosstraining.ui.timer.TimerPhase

/**
 * In-session modal bottom sheet presenting the continuous workout timer, live adjustments,
 * stage queueing, and 6-mode setup fallback with full feature parity to [TimerScreen].
 */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun InSessionTimerSheet(
    timerEngine: TimerEngine,
    onDismiss: () -> Unit,
    sheetState: SheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
    prefillTitle: String = "",
    prefillRounds: Int = 1,
    onStartService: () -> Unit = {}
) {
    val snapshot by timerEngine.snapshot.collectAsStateWithLifecycle()
    val isEngineActive = snapshot.phase != TimerPhase.IDLE
    var showNextModeSheet by remember { mutableStateOf(false) }
    var continueFromFinishedMode by remember { mutableStateOf(false) }

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
                Text(
                    text = if (snapshot.workoutLabel.isNotBlank()) {
                        snapshot.workoutLabel
                    } else {
                        prefillTitle.ifBlank { "Workout Timer" }
                    },
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.weight(1f)
                )

                IconButton(
                    onClick = onDismiss,
                    modifier = Modifier.size(48.dp)
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
                        continueFromFinishedMode = fromFinished
                        showNextModeSheet = true
                    }
                )
            } else {
                // Full 6-mode Setup + Up Next Stage Chaining Parity
                val baseConfig = timerEngine.currentConfig
                var draftConfig by remember(prefillTitle, prefillRounds) {
                    mutableStateOf(
                        defaultConfigForMode(
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

                Column(
                    modifier = Modifier.fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Text(
                        text = "Timer Setup",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )

                    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
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

                    UpNextQueueCard(
                        queuedStage = snapshot.queuedNextStage,
                        onOpenPicker = {
                            continueFromFinishedMode = false
                            showNextModeSheet = true
                        },
                        onClearQueue = { timerEngine.setQueuedNextStage(null) }
                    )

                    Button(
                        onClick = {
                            timerEngine.configure(
                                draftConfig.copy(workoutLabel = prefillTitle),
                                timerEngine.snapshot.value.queuedNextStage
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

    if (showNextModeSheet) {
        NextModeConfigSheet(
            existingStage = if (continueFromFinishedMode) null else snapshot.queuedNextStage,
            baseSoundEnabled = snapshot.activeConfig.soundEnabled,
            baseVibrationEnabled = snapshot.activeConfig.vibrationEnabled,
            isContinueFromFinished = continueFromFinishedMode,
            onDismiss = { showNextModeSheet = false },
            onSaveStage = { stage ->
                if (continueFromFinishedMode && snapshot.phase == TimerPhase.FINISHED) {
                    timerEngine.continueFromFinished(stage.config, stage.transitionSeconds)
                    onStartService()
                } else {
                    timerEngine.setQueuedNextStage(stage)
                }
            },
            onClearStage = { timerEngine.setQueuedNextStage(null) }
        )
    }
}
