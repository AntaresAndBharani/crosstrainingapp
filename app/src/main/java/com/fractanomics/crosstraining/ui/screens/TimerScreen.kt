package com.fractanomics.crosstraining.ui.screens

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.SkipNext
import androidx.compose.material.icons.filled.Timer
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import com.fractanomics.crosstraining.ui.components.AppNumericTextField
import com.fractanomics.crosstraining.ui.timer.NotificationPermissionHelper
import com.fractanomics.crosstraining.ui.timer.TimerEngine
import com.fractanomics.crosstraining.ui.timer.TimerEngineProvider
import com.fractanomics.crosstraining.ui.timer.TimerMode
import com.fractanomics.crosstraining.ui.timer.TimerPhase
import com.fractanomics.crosstraining.ui.timer.TimerService
import com.fractanomics.crosstraining.ui.timer.WorkoutTimerConfig
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun TimerScreen(
    outerPadding: PaddingValues,
    onOpenDrawer: () -> Unit = {}
) {
    val context = LocalContext.current
    val timerEngine = remember { TimerEngineProvider.get(context) }
    val snapshot by timerEngine.snapshot.collectAsStateWithLifecycle()

    var selectedMode by remember { mutableStateOf(TimerMode.EMOM) }
    var intervalSecs by remember { mutableIntStateOf(60) }
    var workSecs by remember { mutableIntStateOf(20) }
    var restSecs by remember { mutableIntStateOf(10) }
    var totalRounds by remember { mutableIntStateOf(10) }
    var targetMinutes by remember { mutableIntStateOf(12) }
    var prepCountdownSecs by remember { mutableIntStateOf(10) }
    var soundEnabled by remember { mutableStateOf(true) }
    var vibrationEnabled by remember { mutableStateOf(true) }

    val permissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        if (isGranted) {
            TimerService.startService(context)
        }
    }

    val startWorkoutTimer: () -> Unit = {
        timerEngine.start()
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

    fun buildConfig() = WorkoutTimerConfig(
        mode = selectedMode,
        intervalSeconds = intervalSecs,
        workSeconds = workSecs,
        restSeconds = restSecs,
        totalRounds = totalRounds,
        targetMinutes = targetMinutes,
        prepCountdownSeconds = prepCountdownSecs,
        soundEnabled = soundEnabled,
        vibrationEnabled = vibrationEnabled
    )

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
            // Mode selector chips
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                TimerMode.entries.forEach { mode ->
                    FilterChip(
                        selected = selectedMode == mode,
                        onClick = {
                            if (snapshot.phase == TimerPhase.IDLE || snapshot.phase == TimerPhase.FINISHED) {
                                selectedMode = mode
                                // Apply defaults
                                when (mode) {
                                    TimerMode.EMOM -> { intervalSecs = 60; totalRounds = 10 }
                                    TimerMode.AMRAP -> { targetMinutes = 12 }
                                    TimerMode.DEATH_BY -> { totalRounds = 15 }
                                    TimerMode.TIME_CAP -> { targetMinutes = 15 }
                                    TimerMode.TABATA -> { workSecs = 20; restSecs = 10; totalRounds = 8 }
                                    TimerMode.REST -> { restSecs = 90; totalRounds = 1 }
                                }
                                timerEngine.configure(buildConfig())
                            }
                        },
                        label = { Text(mode.label) }
                    )
                }
            }

            if (snapshot.phase == TimerPhase.IDLE || snapshot.phase == TimerPhase.FINISHED) {
                // CONFIGURATOR VIEW
                ElevatedCard(modifier = Modifier.fillMaxWidth()) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Text(
                            text = selectedMode.label,
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = selectedMode.description,
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )

                        HorizontalDivider()

                        // Preset Chips
                        Text("Quick Presets:", style = MaterialTheme.typography.labelMedium)
                        FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            when (selectedMode) {
                                TimerMode.EMOM -> {
                                    FilterChip(selected = intervalSecs == 60 && totalRounds == 10, onClick = { intervalSecs = 60; totalRounds = 10 }, label = { Text("EMOM 10m") })
                                    FilterChip(selected = intervalSecs == 120 && totalRounds == 6, onClick = { intervalSecs = 120; totalRounds = 6 }, label = { Text("E2MOM 12m") })
                                    FilterChip(selected = intervalSecs == 180 && totalRounds == 5, onClick = { intervalSecs = 180; totalRounds = 5 }, label = { Text("E3MOM 15m") })
                                    FilterChip(selected = intervalSecs == 60 && totalRounds == 20, onClick = { intervalSecs = 60; totalRounds = 20 }, label = { Text("EMOM 20m") })
                                }
                                TimerMode.AMRAP -> {
                                    FilterChip(selected = targetMinutes == 8, onClick = { targetMinutes = 8 }, label = { Text("AMRAP 8m") })
                                    FilterChip(selected = targetMinutes == 12, onClick = { targetMinutes = 12 }, label = { Text("AMRAP 12m") })
                                    FilterChip(selected = targetMinutes == 20, onClick = { targetMinutes = 20 }, label = { Text("AMRAP 20m") })
                                }
                                TimerMode.DEATH_BY -> {
                                    FilterChip(selected = totalRounds == 10, onClick = { totalRounds = 10 }, label = { Text("10 Rounds") })
                                    FilterChip(selected = totalRounds == 15, onClick = { totalRounds = 15 }, label = { Text("15 Rounds") })
                                    FilterChip(selected = totalRounds == 20, onClick = { totalRounds = 20 }, label = { Text("20 Rounds") })
                                }
                                TimerMode.TIME_CAP -> {
                                    FilterChip(selected = targetMinutes == 10, onClick = { targetMinutes = 10 }, label = { Text("10 min Cap") })
                                    FilterChip(selected = targetMinutes == 15, onClick = { targetMinutes = 15 }, label = { Text("15 min Cap") })
                                    FilterChip(selected = targetMinutes == 25, onClick = { targetMinutes = 25 }, label = { Text("25 min Cap") })
                                }
                                TimerMode.TABATA -> {
                                    FilterChip(selected = workSecs == 20 && restSecs == 10 && totalRounds == 8, onClick = { workSecs = 20; restSecs = 10; totalRounds = 8 }, label = { Text("Classic 8x (20s/10s)") })
                                    FilterChip(selected = workSecs == 30 && restSecs == 15 && totalRounds == 10, onClick = { workSecs = 30; restSecs = 15; totalRounds = 10 }, label = { Text("10x (30s/15s)") })
                                    FilterChip(selected = workSecs == 40 && restSecs == 20 && totalRounds == 10, onClick = { workSecs = 40; restSecs = 20; totalRounds = 10 }, label = { Text("10x (40s/20s)") })
                                }
                                TimerMode.REST -> {
                                    FilterChip(selected = restSecs == 30, onClick = { restSecs = 30 }, label = { Text("30 sec") })
                                    FilterChip(selected = restSecs == 60, onClick = { restSecs = 60 }, label = { Text("60 sec") })
                                    FilterChip(selected = restSecs == 90, onClick = { restSecs = 90 }, label = { Text("90 sec") })
                                    FilterChip(selected = restSecs == 120, onClick = { restSecs = 120 }, label = { Text("2 min") })
                                    FilterChip(selected = restSecs == 180, onClick = { restSecs = 180 }, label = { Text("3 min") })
                                }
                            }
                        }

                        // Parameter text inputs
                        when (selectedMode) {
                            TimerMode.EMOM -> {
                                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                                    AppNumericTextField(
                                        value = intervalSecs,
                                        onValueChange = { intervalSecs = it },
                                        label = { Text("Interval (seconds)") },
                                        minValue = 1,
                                        maxValue = 3600,
                                        modifier = Modifier.weight(1f)
                                    )
                                    AppNumericTextField(
                                        value = totalRounds,
                                        onValueChange = { totalRounds = it },
                                        label = { Text("Total Rounds") },
                                        minValue = 1,
                                        maxValue = 999,
                                        modifier = Modifier.weight(1f)
                                    )
                                }
                            }
                            TimerMode.AMRAP, TimerMode.TIME_CAP -> {
                                AppNumericTextField(
                                    value = targetMinutes,
                                    onValueChange = { targetMinutes = it },
                                    label = { Text("Target Duration (minutes)") },
                                    minValue = 1,
                                    maxValue = 1440,
                                    modifier = Modifier.fillMaxWidth()
                                )
                            }
                            TimerMode.DEATH_BY -> {
                                AppNumericTextField(
                                    value = totalRounds,
                                    onValueChange = { totalRounds = it },
                                    label = { Text("Maximum Rounds") },
                                    minValue = 1,
                                    maxValue = 999,
                                    modifier = Modifier.fillMaxWidth()
                                )
                            }
                            TimerMode.TABATA -> {
                                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                    AppNumericTextField(
                                        value = workSecs,
                                        onValueChange = { workSecs = it },
                                        label = { Text("Work (s)") },
                                        minValue = 1,
                                        maxValue = 3600,
                                        modifier = Modifier.weight(1f)
                                    )
                                    AppNumericTextField(
                                        value = restSecs,
                                        onValueChange = { restSecs = it },
                                        label = { Text("Rest (s)") },
                                        minValue = 0,
                                        maxValue = 3600,
                                        modifier = Modifier.weight(1f)
                                    )
                                    AppNumericTextField(
                                        value = totalRounds,
                                        onValueChange = { totalRounds = it },
                                        label = { Text("Rounds") },
                                        minValue = 1,
                                        maxValue = 999,
                                        modifier = Modifier.weight(1f)
                                    )
                                }
                            }
                            TimerMode.REST -> {
                                AppNumericTextField(
                                    value = restSecs,
                                    onValueChange = { restSecs = it },
                                    label = { Text("Rest Duration (seconds)") },
                                    minValue = 1,
                                    maxValue = 3600,
                                    modifier = Modifier.fillMaxWidth()
                                )
                            }
                        }

                        // Prep Countdown
                        Text("3-2-1 Prep Countdown:", style = MaterialTheme.typography.labelMedium)
                        FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            listOf(0, 3, 5, 10).forEach { prep ->
                                FilterChip(
                                    selected = prepCountdownSecs == prep,
                                    onClick = { prepCountdownSecs = prep },
                                    label = { Text(if (prep == 0) "No prep" else "${prep}s prep") }
                                )
                            }
                        }

                        // Sound & Vibration Toggles
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text("Audio Beeps", style = MaterialTheme.typography.bodyMedium)
                            Switch(checked = soundEnabled, onCheckedChange = { soundEnabled = it })
                        }
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text("Vibration", style = MaterialTheme.typography.bodyMedium)
                            Switch(checked = vibrationEnabled, onCheckedChange = { vibrationEnabled = it })
                        }

                        Spacer(Modifier.height(8.dp))

                        Button(
                            onClick = {
                                timerEngine.configure(buildConfig())
                                startWorkoutTimer()
                            },
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
                // ACTIVE TIMER RUNNING / PAUSED / PREP VIEW
                val phaseColor = when (snapshot.phase) {
                    TimerPhase.PREP -> MaterialTheme.colorScheme.tertiary
                    TimerPhase.WORK -> MaterialTheme.colorScheme.primary
                    TimerPhase.REST -> MaterialTheme.colorScheme.secondary
                    TimerPhase.FINISHED -> MaterialTheme.colorScheme.outline
                    TimerPhase.IDLE -> MaterialTheme.colorScheme.primary
                }

                ElevatedCard(modifier = Modifier.fillMaxWidth()) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(24.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(16.dp)
                    ) {
                        // Phase Tag
                        Box(
                            modifier = Modifier
                                .background(phaseColor, RoundedCornerShape(8.dp))
                                .padding(horizontal = 16.dp, vertical = 6.dp)
                        ) {
                            Text(
                                text = snapshot.phase.label.uppercase(Locale.getDefault()),
                                color = MaterialTheme.colorScheme.onPrimary,
                                fontWeight = FontWeight.Bold,
                                style = MaterialTheme.typography.titleMedium
                            )
                        }

                        // Clock Circle Display
                        Box(
                            contentAlignment = Alignment.Center,
                            modifier = Modifier.size(240.dp)
                        ) {
                            val progress = if (snapshot.roundTotalSeconds > 0) {
                                snapshot.roundSecondsElapsed.toFloat() / snapshot.roundTotalSeconds.toFloat()
                            } else 0f

                            CircularProgressIndicator(
                                progress = { progress },
                                modifier = Modifier.fillMaxSize(),
                                color = phaseColor,
                                strokeWidth = 12.dp,
                                trackColor = MaterialTheme.colorScheme.surfaceVariant
                            )

                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                val displayTime = formatClockTime(snapshot.roundSecondsRemaining)
                                Text(
                                    text = displayTime,
                                    fontSize = 54.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.onSurface
                                )

                                if (selectedMode == TimerMode.DEATH_BY) {
                                    Text(
                                        text = "Target: ${snapshot.targetRepsCurrentRound} reps",
                                        style = MaterialTheme.typography.titleSmall,
                                        fontWeight = FontWeight.SemiBold
                                    )
                                } else if (snapshot.totalRounds > 1) {
                                    Text(
                                        text = "Round ${snapshot.currentRound} of ${snapshot.totalRounds}",
                                        style = MaterialTheme.typography.titleSmall,
                                        fontWeight = FontWeight.SemiBold
                                    )
                                }
                            }
                        }

                        // Total Stats Summary
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceEvenly
                        ) {
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                Text("Total Elapsed", style = MaterialTheme.typography.labelSmall)
                                Text(
                                    formatClockTime(snapshot.totalSecondsElapsed),
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                Text("Total Remaining", style = MaterialTheme.typography.labelSmall)
                                Text(
                                    formatClockTime(snapshot.totalSecondsRemaining),
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                        }

                        HorizontalDivider()

                        // Action controls
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(12.dp, Alignment.CenterHorizontally)
                        ) {
                            Button(
                                onClick = {
                                    if (snapshot.isRunning) {
                                        timerEngine.pause()
                                    } else {
                                        startWorkoutTimer()
                                    }
                                },
                                colors = ButtonDefaults.buttonColors(containerColor = phaseColor),
                                modifier = Modifier.weight(1f)
                            ) {
                                Icon(
                                    if (snapshot.isRunning) Icons.Filled.Pause else Icons.Filled.PlayArrow,
                                    contentDescription = null
                                )
                                Spacer(Modifier.width(6.dp))
                                Text(if (snapshot.isRunning) "PAUSE" else "RESUME", fontWeight = FontWeight.Bold)
                            }

                            if (snapshot.totalRounds > 1) {
                                OutlinedButton(
                                    onClick = { timerEngine.skipRound() },
                                    modifier = Modifier.weight(1f)
                                ) {
                                    Icon(Icons.Filled.SkipNext, contentDescription = null)
                                    Spacer(Modifier.width(6.dp))
                                    Text("SKIP")
                                }
                            }

                            OutlinedButton(
                                onClick = { timerEngine.reset() },
                                modifier = Modifier.weight(1f)
                            ) {
                                Icon(Icons.Filled.Refresh, contentDescription = null)
                                Spacer(Modifier.width(6.dp))
                                Text("RESET")
                            }
                        }
                    }
                }
            }
        }
    }
}

private fun formatClockTime(seconds: Int): String {
    val mins = seconds / 60
    val secs = seconds % 60
    return String.format(Locale.getDefault(), "%02d:%02d", mins, secs)
}
