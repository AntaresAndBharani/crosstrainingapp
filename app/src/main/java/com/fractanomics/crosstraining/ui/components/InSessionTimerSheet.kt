package com.fractanomics.crosstraining.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.SkipNext
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.SheetState
import androidx.compose.material3.Surface
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
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.fractanomics.crosstraining.ui.timer.TimerEngine
import com.fractanomics.crosstraining.ui.timer.TimerMode
import com.fractanomics.crosstraining.ui.timer.TimerPhase
import com.fractanomics.crosstraining.ui.timer.WorkoutTimerConfig
import java.util.Locale

/**
 * In-session modal bottom sheet presenting the expanded circular countdown timer
 * and controls without NavController screen hops, preserving live session draft state.
 *
 * Can also serve as a quick timer configuration sheet for untimed blocks.
 */
@OptIn(ExperimentalMaterial3Api::class)
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

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 24.dp, vertical = 12.dp)
                .verticalScroll(rememberScrollState()),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // Sheet Header Row
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = if (snapshot.workoutLabel.isNotBlank()) snapshot.workoutLabel else prefillTitle.ifBlank { "Workout Timer" },
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface
                )

                IconButton(onClick = onDismiss) {
                    Icon(Icons.Filled.Close, contentDescription = "Close Sheet")
                }
            }

            if (isEngineActive) {
                // ACTIVE TIMER EXPANDED VIEW
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
                            .padding(20.dp),
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

                        // Circular Progress Indicator Gauge
                        Box(
                            contentAlignment = Alignment.Center,
                            modifier = Modifier.size(220.dp)
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
                                    fontSize = 48.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.onSurface
                                )

                                if (snapshot.totalRounds > 1) {
                                    Text(
                                        text = "Round ${snapshot.currentRound} of ${snapshot.totalRounds}",
                                        style = MaterialTheme.typography.titleSmall,
                                        fontWeight = FontWeight.SemiBold
                                    )
                                } else {
                                    Text(
                                        text = snapshot.mode.label,
                                        style = MaterialTheme.typography.titleSmall,
                                        fontWeight = FontWeight.SemiBold
                                    )
                                }
                            }
                        }

                        // Total stats
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

                        // Action Controls
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(12.dp, Alignment.CenterHorizontally)
                        ) {
                            Button(
                                onClick = {
                                    if (snapshot.isRunning) {
                                        timerEngine.pause()
                                    } else {
                                        timerEngine.start()
                                        onStartService()
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
            } else {
                // CONFIGURATION FALLBACK FOR UNTIMED BLOCKS
                var selectedMode by remember { mutableStateOf(TimerMode.EMOM) }
                var intervalSecs by remember { mutableIntStateOf(60) }
                var totalRounds by remember { mutableIntStateOf(prefillRounds.coerceAtLeast(1)) }
                var restSecs by remember { mutableIntStateOf(60) }

                Column(
                    modifier = Modifier.fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Text(
                        text = "Quick Timer Setup",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Surface(
                            selected = selectedMode == TimerMode.EMOM,
                            onClick = { selectedMode = TimerMode.EMOM },
                            shape = RoundedCornerShape(8.dp),
                            color = if (selectedMode == TimerMode.EMOM) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceVariant,
                            modifier = Modifier.weight(1f)
                        ) {
                            Text(
                                "EMOM",
                                modifier = Modifier.padding(vertical = 10.dp),
                                textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                                fontWeight = FontWeight.SemiBold
                            )
                        }
                        Surface(
                            selected = selectedMode == TimerMode.REST,
                            onClick = { selectedMode = TimerMode.REST },
                            shape = RoundedCornerShape(8.dp),
                            color = if (selectedMode == TimerMode.REST) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceVariant,
                            modifier = Modifier.weight(1f)
                        ) {
                            Text(
                                "Rest Timer",
                                modifier = Modifier.padding(vertical = 10.dp),
                                textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                                fontWeight = FontWeight.SemiBold
                            )
                        }
                    }

                    if (selectedMode == TimerMode.EMOM) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text("Interval Seconds: $intervalSecs s", style = MaterialTheme.typography.bodyMedium)
                            Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                                OutlinedButton(onClick = { intervalSecs = (intervalSecs - 15).coerceAtLeast(15) }) { Text("-15") }
                                OutlinedButton(onClick = { intervalSecs += 15 }) { Text("+15") }
                            }
                        }
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text("Total Rounds: $totalRounds", style = MaterialTheme.typography.bodyMedium)
                            Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                                OutlinedButton(onClick = { totalRounds = (totalRounds - 1).coerceAtLeast(1) }) { Text("-1") }
                                OutlinedButton(onClick = { totalRounds += 1 }) { Text("+1") }
                            }
                        }
                    } else {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text("Rest Seconds: $restSecs s", style = MaterialTheme.typography.bodyMedium)
                            Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                                OutlinedButton(onClick = { restSecs = (restSecs - 15).coerceAtLeast(15) }) { Text("-15") }
                                OutlinedButton(onClick = { restSecs += 15 }) { Text("+15") }
                            }
                        }
                    }

                    Spacer(Modifier.height(8.dp))

                    Button(
                        onClick = {
                            val base = timerEngine.currentConfig
                            val config = if (selectedMode == TimerMode.EMOM) {
                                base.copy(
                                    mode = TimerMode.EMOM,
                                    intervalSeconds = intervalSecs,
                                    totalRounds = totalRounds,
                                    workoutLabel = prefillTitle
                                )
                            } else {
                                base.copy(
                                    mode = TimerMode.REST,
                                    restSeconds = restSecs,
                                    totalRounds = 1,
                                    workoutLabel = prefillTitle
                                )
                            }
                            timerEngine.configure(config)
                            timerEngine.start()
                            onStartService()
                        },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(48.dp)
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
}

private fun formatClockTime(seconds: Int): String {
    val mins = seconds / 60
    val secs = seconds % 60
    return String.format(Locale.getDefault(), "%02d:%02d", mins, secs)
}
