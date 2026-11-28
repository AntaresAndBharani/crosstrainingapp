package com.fractanomics.crosstraining.ui.screens

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.Button
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.fractanomics.crosstraining.ui.components.ActiveContinuousTimerCard
import com.fractanomics.crosstraining.ui.components.NextModeConfigSheet
import com.fractanomics.crosstraining.ui.components.TimerModeSetupForm
import com.fractanomics.crosstraining.ui.components.WorkoutQueueTimelinePreview
import com.fractanomics.crosstraining.ui.components.defaultConfigForMode
import com.fractanomics.crosstraining.ui.timer.NotificationPermissionHelper
import com.fractanomics.crosstraining.ui.timer.QueuedTimerStage
import com.fractanomics.crosstraining.ui.timer.TimerEngineProvider
import com.fractanomics.crosstraining.ui.timer.TimerMode
import com.fractanomics.crosstraining.ui.timer.TimerPhase
import com.fractanomics.crosstraining.ui.timer.TimerService

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun TimerScreen(
    outerPadding: PaddingValues,
    onOpenDrawer: () -> Unit = {}
) {
    val context = LocalContext.current
    val timerEngine = remember { TimerEngineProvider.get(context) }
    val snapshot by timerEngine.snapshot.collectAsStateWithLifecycle()

    var draftConfig by remember { mutableStateOf(timerEngine.currentConfig) }
    var editingStage by remember { mutableStateOf<QueuedTimerStage?>(null) }
    var showNextModeSheet by remember { mutableStateOf(false) }
    var continueFromFinishedMode by remember { mutableStateOf(false) }

    LaunchedEffect(snapshot.phase) {
        if (snapshot.phase == TimerPhase.IDLE) {
            draftConfig = timerEngine.currentConfig
        }
    }

    val permissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        if (isGranted) {
            TimerService.startService(context)
        }
    }

    val requestNotificationAndStartService: () -> Unit = {
        NotificationPermissionHelper.handleTimerStartWithPermission(
            context = context,
            onPermissionRequired = {
                permissionLauncher.launch(NotificationPermissionHelper.POST_NOTIFICATIONS)
            },
            onStartService = {
                TimerService.startService(context)
            }
        )
    }

    Scaffold(
        modifier = Modifier.padding(bottom = outerPadding.calculateBottomPadding()),
        topBar = {
            TopAppBar(
                title = { Text("Workout Timers") },
                navigationIcon = {
                    IconButton(onClick = onOpenDrawer) {
                        Icon(Icons.Filled.Menu, contentDescription = "Open Menu")
                    }
                }
            )
        }
    ) { pad ->
        Column(
            modifier = Modifier
                .padding(pad)
                .fillMaxSize()
                .padding(16.dp)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            if (snapshot.phase == TimerPhase.IDLE) {
                // Setup view shown strictly when IDLE; collapses completely once running/transitioning/finished
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    TimerMode.entries.forEach { mode ->
                        FilterChip(
                            selected = draftConfig.mode == mode,
                            onClick = {
                                val nextCfg = defaultConfigForMode(
                                    mode = mode,
                                    soundEnabled = draftConfig.soundEnabled,
                                    vibrationEnabled = draftConfig.vibrationEnabled
                                ).copy(prepCountdownSeconds = draftConfig.prepCountdownSeconds)
                                draftConfig = nextCfg
                                timerEngine.configureQueue(nextCfg, snapshot.queuedStages)
                            },
                            label = { Text(mode.label) }
                        )
                    }
                }

                ElevatedCard(modifier = Modifier.fillMaxWidth()) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(14.dp)
                    ) {
                        TimerModeSetupForm(
                            config = draftConfig,
                            onConfigChange = { updated ->
                                draftConfig = updated
                                timerEngine.configureQueue(updated, snapshot.queuedStages)
                            },
                            showPrepAndAlertOptions = true
                        )

                        HorizontalDivider()

                        WorkoutQueueTimelinePreview(
                            startingConfig = draftConfig,
                            queuedStages = snapshot.queuedStages,
                            onEditStage = { stage ->
                                editingStage = stage
                                continueFromFinishedMode = false
                                showNextModeSheet = true
                            },
                            onRemoveStage = { stageId ->
                                timerEngine.removeQueuedStage(stageId)
                            },
                            onAddStage = {
                                editingStage = null
                                continueFromFinishedMode = false
                                showNextModeSheet = true
                            }
                        )

                        Button(
                            onClick = {
                                timerEngine.configureQueue(draftConfig, timerEngine.snapshot.value.queuedStages)
                                timerEngine.start()
                                requestNotificationAndStartService()
                            },
                            shape = RoundedCornerShape(999.dp),
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(52.dp)
                        ) {
                            Icon(Icons.Filled.PlayArrow, contentDescription = null)
                            Spacer(Modifier.width(8.dp))
                            Text("START TIMER", fontWeight = FontWeight.Bold, fontSize = 16.sp)
                        }
                    }
                }
            } else {
                // Active / Transition / Finished unified card
                ActiveContinuousTimerCard(
                    snapshot = snapshot,
                    timerEngine = timerEngine,
                    onStartService = requestNotificationAndStartService,
                    onOpenQueueSheet = { fromFinished ->
                        editingStage = if (fromFinished) null else snapshot.queuedNextStage
                        continueFromFinishedMode = fromFinished
                        showNextModeSheet = true
                    },
                    onEditQueuedStage = { stage ->
                        editingStage = stage
                        continueFromFinishedMode = false
                        showNextModeSheet = true
                    },
                    onRemoveQueuedStage = { stageId ->
                        timerEngine.removeQueuedStage(stageId)
                    },
                    onAddQueuedStage = { fromFinished ->
                        editingStage = null
                        continueFromFinishedMode = fromFinished
                        showNextModeSheet = true
                    }
                )
            }
        }
    }

    if (showNextModeSheet) {
        val currentEditing = editingStage
        NextModeConfigSheet(
            existingStage = if (continueFromFinishedMode) null else currentEditing,
            baseSoundEnabled = snapshot.activeConfig.soundEnabled,
            baseVibrationEnabled = snapshot.activeConfig.vibrationEnabled,
            isContinueFromFinished = continueFromFinishedMode,
            onDismiss = {
                showNextModeSheet = false
                editingStage = null
            },
            onSaveStage = { stage ->
                when {
                    continueFromFinishedMode && snapshot.phase == TimerPhase.FINISHED -> {
                        timerEngine.continueFromFinished(stage.config, stage.transitionSeconds)
                        requestNotificationAndStartService()
                    }
                    currentEditing != null -> {
                        timerEngine.updateQueuedStage(currentEditing.id, stage)
                    }
                    else -> {
                        timerEngine.appendQueuedStage(stage)
                    }
                }
            },
            onClearStage = {
                if (currentEditing != null) {
                    timerEngine.removeQueuedStage(currentEditing.id)
                }
            }
        )
    }
}
