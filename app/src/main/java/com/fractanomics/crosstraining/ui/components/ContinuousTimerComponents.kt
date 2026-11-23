package com.fractanomics.crosstraining.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Remove
import androidx.compose.material.icons.filled.SkipNext
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedCard
import androidx.compose.material3.SheetState
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.fractanomics.crosstraining.ui.theme.OchreContainerDark
import com.fractanomics.crosstraining.ui.theme.OchreContainerLight
import com.fractanomics.crosstraining.ui.theme.OchreOnContainerDark
import com.fractanomics.crosstraining.ui.theme.OchreOnContainerLight
import com.fractanomics.crosstraining.ui.theme.OchreWarningDark
import com.fractanomics.crosstraining.ui.theme.OchreWarningLight
import com.fractanomics.crosstraining.ui.theme.isAppDarkTheme
import com.fractanomics.crosstraining.ui.timer.ContinuousTimerUiResolver
import com.fractanomics.crosstraining.ui.timer.QueuedTimerStage
import com.fractanomics.crosstraining.ui.timer.TimerEngine
import com.fractanomics.crosstraining.ui.timer.TimerMode
import com.fractanomics.crosstraining.ui.timer.TimerPhase
import com.fractanomics.crosstraining.ui.timer.TimerSnapshot
import com.fractanomics.crosstraining.ui.timer.WorkoutTimerConfig
import java.util.Locale

@Composable
fun resolveTransitionAccentColor(): Color =
    if (isAppDarkTheme()) OchreWarningDark else OchreWarningLight

@Composable
fun resolveTransitionContainerColor(): Color =
    if (isAppDarkTheme()) OchreContainerDark else OchreContainerLight

@Composable
fun resolveTimerPhaseColor(phase: TimerPhase): Color = when (phase) {
    TimerPhase.PREP -> MaterialTheme.colorScheme.tertiary
    TimerPhase.WORK -> MaterialTheme.colorScheme.primary
    TimerPhase.REST -> MaterialTheme.colorScheme.secondary
    TimerPhase.TRANSITION -> resolveTransitionAccentColor()
    TimerPhase.FINISHED -> MaterialTheme.colorScheme.outline
    TimerPhase.IDLE -> MaterialTheme.colorScheme.primary
}

@Composable
fun resolveTimerPhaseBadgeContainer(phase: TimerPhase): Color = when (phase) {
    TimerPhase.PREP -> MaterialTheme.colorScheme.tertiaryContainer
    TimerPhase.WORK -> MaterialTheme.colorScheme.primaryContainer
    TimerPhase.REST -> MaterialTheme.colorScheme.secondaryContainer
    TimerPhase.TRANSITION -> resolveTransitionContainerColor()
    TimerPhase.FINISHED -> MaterialTheme.colorScheme.surfaceVariant
    TimerPhase.IDLE -> MaterialTheme.colorScheme.surfaceVariant
}

@Composable
fun resolveTimerPhaseBadgeContent(phase: TimerPhase): Color = when (phase) {
    TimerPhase.PREP -> MaterialTheme.colorScheme.onTertiaryContainer
    TimerPhase.WORK -> MaterialTheme.colorScheme.onPrimaryContainer
    TimerPhase.REST -> MaterialTheme.colorScheme.onSecondaryContainer
    TimerPhase.TRANSITION -> if (isAppDarkTheme()) OchreOnContainerDark else OchreOnContainerLight
    TimerPhase.FINISHED -> MaterialTheme.colorScheme.onSurfaceVariant
    TimerPhase.IDLE -> MaterialTheme.colorScheme.onSurfaceVariant
}

@Composable
private fun tabularStyle(base: TextStyle = LocalTextStyle.current): TextStyle =
    base.copy(fontFeatureSettings = "tnum")

/**
 * Shared 6-mode configuration form supporting unified `Total Rounds`, presets, and numeric fields.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun TimerModeSetupForm(
    config: WorkoutTimerConfig,
    onConfigChange: (WorkoutTimerConfig) -> Unit,
    modifier: Modifier = Modifier,
    showPrepAndAlertOptions: Boolean = true
) {
    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Text(
            text = config.mode.label,
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold
        )
        Text(
            text = config.mode.description,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )

        // Mode-specific Quick Presets
        Text("Quick Presets:", style = MaterialTheme.typography.labelMedium)
        FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            when (config.mode) {
                TimerMode.EMOM -> {
                    FilterChip(
                        selected = config.intervalSeconds == 60 && config.totalRounds == 10 && config.targetDurationSeconds == null,
                        onClick = { onConfigChange(config.copy(intervalSeconds = 60, totalRounds = 10, targetDurationSeconds = null)) },
                        label = { Text("EMOM 10m") }
                    )
                    FilterChip(
                        selected = config.intervalSeconds == 120 && config.totalRounds == 6 && config.targetDurationSeconds == null,
                        onClick = { onConfigChange(config.copy(intervalSeconds = 120, totalRounds = 6, targetDurationSeconds = null)) },
                        label = { Text("E2MOM 12m") }
                    )
                    FilterChip(
                        selected = config.intervalSeconds == 180 && config.totalRounds == 5 && config.targetDurationSeconds == null,
                        onClick = { onConfigChange(config.copy(intervalSeconds = 180, totalRounds = 5, targetDurationSeconds = null)) },
                        label = { Text("E3MOM 15m") }
                    )
                    FilterChip(
                        selected = config.intervalSeconds == 60 && config.totalRounds == 20 && config.targetDurationSeconds == null,
                        onClick = { onConfigChange(config.copy(intervalSeconds = 60, totalRounds = 20, targetDurationSeconds = null)) },
                        label = { Text("EMOM 20m") }
                    )
                }
                TimerMode.AMRAP -> {
                    listOf(8, 12, 20).forEach { mins ->
                        FilterChip(
                            selected = config.targetMinutes == mins && config.targetDurationSeconds == null,
                            onClick = { onConfigChange(config.copy(targetMinutes = mins, targetDurationSeconds = null)) },
                            label = { Text("AMRAP ${mins}m") }
                        )
                    }
                }
                TimerMode.DEATH_BY -> {
                    listOf(10, 15, 20).forEach { rounds ->
                        FilterChip(
                            selected = config.totalRounds == rounds && config.intervalSeconds == 60,
                            onClick = { onConfigChange(config.copy(totalRounds = rounds, intervalSeconds = 60, targetDurationSeconds = null)) },
                            label = { Text("$rounds Rounds") }
                        )
                    }
                }
                TimerMode.TIME_CAP -> {
                    listOf(10, 15, 25).forEach { mins ->
                        FilterChip(
                            selected = config.targetMinutes == mins && config.targetDurationSeconds == null,
                            onClick = { onConfigChange(config.copy(targetMinutes = mins, targetDurationSeconds = null)) },
                            label = { Text("$mins min Cap") }
                        )
                    }
                }
                TimerMode.TABATA -> {
                    FilterChip(
                        selected = config.workSeconds == 20 && config.restSeconds == 10 && config.totalRounds == 8,
                        onClick = { onConfigChange(config.copy(workSeconds = 20, restSeconds = 10, totalRounds = 8, targetDurationSeconds = null)) },
                        label = { Text("Classic 8× (20s/10s)") }
                    )
                    FilterChip(
                        selected = config.workSeconds == 30 && config.restSeconds == 15 && config.totalRounds == 10,
                        onClick = { onConfigChange(config.copy(workSeconds = 30, restSeconds = 15, totalRounds = 10, targetDurationSeconds = null)) },
                        label = { Text("10× (30s/15s)") }
                    )
                    FilterChip(
                        selected = config.workSeconds == 40 && config.restSeconds == 20 && config.totalRounds == 10,
                        onClick = { onConfigChange(config.copy(workSeconds = 40, restSeconds = 20, totalRounds = 10, targetDurationSeconds = null)) },
                        label = { Text("10× (40s/20s)") }
                    )
                }
                TimerMode.REST -> {
                    listOf(30 to "30 sec", 60 to "60 sec", 90 to "90 sec", 120 to "2 min", 180 to "3 min").forEach { (secs, title) ->
                        FilterChip(
                            selected = config.restSeconds == secs && config.targetDurationSeconds == null,
                            onClick = { onConfigChange(config.copy(restSeconds = secs, targetDurationSeconds = null)) },
                            label = { Text(title) }
                        )
                    }
                }
            }
        }

        // Unified Parameters across ALL 6 modes (every mode includes Total Rounds)
        when (config.mode) {
            TimerMode.EMOM -> {
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    AppNumericTextField(
                        value = config.intervalSeconds,
                        onValueChange = { onConfigChange(config.copy(intervalSeconds = it, targetDurationSeconds = null)) },
                        label = { Text("Interval (s)") },
                        minValue = 5,
                        maxValue = 3600,
                        modifier = Modifier.weight(1f)
                    )
                    AppNumericTextField(
                        value = config.totalRounds,
                        onValueChange = { onConfigChange(config.copy(totalRounds = it)) },
                        label = { Text("Total Rounds") },
                        minValue = 1,
                        maxValue = 999,
                        modifier = Modifier.weight(1f)
                    )
                }
            }
            TimerMode.AMRAP, TimerMode.TIME_CAP -> {
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    AppNumericTextField(
                        value = config.targetMinutes,
                        onValueChange = { onConfigChange(config.copy(targetMinutes = it, targetDurationSeconds = null)) },
                        label = { Text("Duration (min)") },
                        minValue = 1,
                        maxValue = 1440,
                        modifier = Modifier.weight(1f)
                    )
                    AppNumericTextField(
                        value = config.totalRounds,
                        onValueChange = { onConfigChange(config.copy(totalRounds = it)) },
                        label = { Text("Total Rounds") },
                        minValue = 1,
                        maxValue = 999,
                        modifier = Modifier.weight(1f)
                    )
                }
            }
            TimerMode.DEATH_BY -> {
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    AppNumericTextField(
                        value = config.intervalSeconds,
                        onValueChange = { onConfigChange(config.copy(intervalSeconds = it, targetDurationSeconds = null)) },
                        label = { Text("Round (s)") },
                        minValue = 5,
                        maxValue = 3600,
                        modifier = Modifier.weight(1f)
                    )
                    AppNumericTextField(
                        value = config.totalRounds,
                        onValueChange = { onConfigChange(config.copy(totalRounds = it)) },
                        label = { Text("Total Rounds") },
                        minValue = 1,
                        maxValue = 999,
                        modifier = Modifier.weight(1f)
                    )
                }
            }
            TimerMode.TABATA -> {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    AppNumericTextField(
                        value = config.workSeconds,
                        onValueChange = { onConfigChange(config.copy(workSeconds = it, targetDurationSeconds = null)) },
                        label = { Text("Work (s)") },
                        minValue = 5,
                        maxValue = 3600,
                        modifier = Modifier.weight(1f)
                    )
                    AppNumericTextField(
                        value = config.restSeconds,
                        onValueChange = { onConfigChange(config.copy(restSeconds = it)) },
                        label = { Text("Rest (s)") },
                        minValue = 0,
                        maxValue = 3600,
                        modifier = Modifier.weight(1f)
                    )
                    AppNumericTextField(
                        value = config.totalRounds,
                        onValueChange = { onConfigChange(config.copy(totalRounds = it)) },
                        label = { Text("Rounds") },
                        minValue = 1,
                        maxValue = 999,
                        modifier = Modifier.weight(1f)
                    )
                }
            }
            TimerMode.REST -> {
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    AppNumericTextField(
                        value = config.restSeconds,
                        onValueChange = { onConfigChange(config.copy(restSeconds = it, targetDurationSeconds = null)) },
                        label = { Text("Rest (s)") },
                        minValue = 5,
                        maxValue = 3600,
                        modifier = Modifier.weight(1f)
                    )
                    AppNumericTextField(
                        value = config.totalRounds,
                        onValueChange = { onConfigChange(config.copy(totalRounds = it)) },
                        label = { Text("Total Rounds") },
                        minValue = 1,
                        maxValue = 999,
                        modifier = Modifier.weight(1f)
                    )
                }
            }
        }

        if (showPrepAndAlertOptions) {
            Text("3-2-1 Prep Countdown:", style = MaterialTheme.typography.labelMedium)
            FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                listOf(0, 3, 5, 10).forEach { prep ->
                    FilterChip(
                        selected = config.prepCountdownSeconds == prep,
                        onClick = { onConfigChange(config.copy(prepCountdownSeconds = prep)) },
                        label = { Text(if (prep == 0) "No prep" else "${prep}s prep") }
                    )
                }
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text("Audio Beeps", style = MaterialTheme.typography.bodyMedium)
                Switch(
                    checked = config.soundEnabled,
                    onCheckedChange = { onConfigChange(config.copy(soundEnabled = it)) }
                )
            }
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text("Vibration", style = MaterialTheme.typography.bodyMedium)
                Switch(
                    checked = config.vibrationEnabled,
                    onCheckedChange = { onConfigChange(config.copy(vibrationEnabled = it)) }
                )
            }
        }
    }
}

/**
 * Single-slot Up Next queue banner usable on Setup, active WORK/REST/PREP, and TRANSITION views.
 */
@Composable
fun UpNextQueueCard(
    queuedStage: QueuedTimerStage?,
    onOpenPicker: () -> Unit,
    onClearQueue: () -> Unit,
    modifier: Modifier = Modifier
) {
    if (queuedStage == null) {
        OutlinedButton(
            onClick = onOpenPicker,
            modifier = modifier
                .fillMaxWidth()
                .heightIn(min = 48.dp),
            shape = RoundedCornerShape(12.dp)
        ) {
            Icon(Icons.Filled.Add, contentDescription = null, modifier = Modifier.size(18.dp))
            Spacer(Modifier.width(6.dp))
            Text(
                text = "Choose Next Mode & Transition Time",
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                fontWeight = FontWeight.SemiBold
            )
        }
    } else {
        OutlinedCard(
            modifier = modifier.fillMaxWidth(),
            shape = RoundedCornerShape(12.dp),
            border = BorderStroke(1.dp, resolveTransitionAccentColor().copy(alpha = 0.6f))
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 12.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    val transitionDesc = if (queuedStage.transitionSeconds <= 0) {
                        "Up Next (Direct 0s transition)"
                    } else {
                        "Up Next (after ${ContinuousTimerUiResolver.formatDurationChip(queuedStage.transitionSeconds)} transition)"
                    }
                    Text(
                        text = transitionDesc,
                        style = MaterialTheme.typography.labelSmall,
                        color = resolveTransitionAccentColor(),
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = ContinuousTimerUiResolver.formatCompactModeSummary(queuedStage.config),
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.SemiBold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }

                Row(verticalAlignment = Alignment.CenterVertically) {
                    IconButton(
                        onClick = onOpenPicker,
                        modifier = Modifier.size(48.dp)
                    ) {
                        Icon(Icons.Filled.Edit, contentDescription = "Edit upcoming mode")
                    }
                    IconButton(
                        onClick = onClearQueue,
                        modifier = Modifier.size(48.dp)
                    ) {
                        Icon(Icons.Filled.Close, contentDescription = "Clear upcoming mode")
                    }
                }
            }
        }
    }
}

/**
 * Bottom sheet for selecting or editing the single-slot Next Mode and transition wait seconds,
 * or appending another mode from [TimerPhase.FINISHED].
 */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun NextModeConfigSheet(
    existingStage: QueuedTimerStage?,
    baseSoundEnabled: Boolean,
    baseVibrationEnabled: Boolean,
    isContinueFromFinished: Boolean,
    onDismiss: () -> Unit,
    onSaveStage: (QueuedTimerStage) -> Unit,
    onClearStage: () -> Unit,
    sheetState: SheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
) {
    val initialConfig = existingStage?.config ?: WorkoutTimerConfig(
        mode = TimerMode.AMRAP,
        targetMinutes = 10,
        totalRounds = 1,
        prepCountdownSeconds = 0,
        soundEnabled = baseSoundEnabled,
        vibrationEnabled = baseVibrationEnabled
    )
    var draftConfig by remember(existingStage) { mutableStateOf(initialConfig) }
    var transitionSeconds by remember(existingStage) {
        mutableIntStateOf(existingStage?.transitionSeconds ?: if (isContinueFromFinished) 0 else 60)
    }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .imePadding()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = if (isContinueFromFinished) "Add Another Mode" else "Up Next Mode & Transition",
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.weight(1f)
                )
                IconButton(
                    onClick = onDismiss,
                    modifier = Modifier.size(48.dp)
                ) {
                    Icon(Icons.Filled.Close, contentDescription = "Close next mode sheet")
                }
            }

            Text("Transition Time Before Next Mode:", style = MaterialTheme.typography.labelMedium)
            FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                ContinuousTimerUiResolver.QUICK_TRANSITION_SECONDS.forEach { secs ->
                    FilterChip(
                        selected = transitionSeconds == secs,
                        onClick = { transitionSeconds = secs },
                        label = { Text(ContinuousTimerUiResolver.formatTransitionQuickOption(secs)) }
                    )
                }
            }
            AppNumericTextField(
                value = transitionSeconds,
                onValueChange = { transitionSeconds = it },
                label = { Text("Custom Transition (seconds, 0 = instant)") },
                minValue = 0,
                maxValue = 3600,
                modifier = Modifier.fillMaxWidth()
            )

            HorizontalDivider()

            Text("One-Tap Finisher / Stage Presets:", style = MaterialTheme.typography.labelMedium)
            FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                ContinuousTimerUiResolver.NEXT_STAGE_PRESETS.forEach { preset ->
                    FilterChip(
                        selected = draftConfig.mode == preset.config.mode &&
                            draftConfig.baseWorkDurationSeconds() == preset.config.baseWorkDurationSeconds() &&
                            draftConfig.totalRounds == preset.config.totalRounds,
                        onClick = {
                            draftConfig = preset.config.copy(
                                soundEnabled = baseSoundEnabled,
                                vibrationEnabled = baseVibrationEnabled
                            )
                        },
                        label = { Text("${preset.title} (${preset.subtitle})") }
                    )
                }
            }

            HorizontalDivider()

            Text("Timer Mode:", style = MaterialTheme.typography.labelMedium)
            FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                TimerMode.entries.forEach { mode ->
                    FilterChip(
                        selected = draftConfig.mode == mode,
                        onClick = {
                            draftConfig = defaultConfigForMode(
                                mode = mode,
                                soundEnabled = baseSoundEnabled,
                                vibrationEnabled = baseVibrationEnabled
                            )
                        },
                        label = { Text(mode.label) }
                    )
                }
            }

            TimerModeSetupForm(
                config = draftConfig,
                onConfigChange = { draftConfig = it },
                showPrepAndAlertOptions = false
            )

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 4.dp, bottom = 12.dp),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                if (existingStage != null && !isContinueFromFinished) {
                    OutlinedButton(
                        onClick = {
                            onClearStage()
                            onDismiss()
                        },
                        modifier = Modifier
                            .weight(1f)
                            .height(52.dp),
                        shape = RoundedCornerShape(999.dp)
                    ) {
                        Text("Clear Queue", maxLines = 1, softWrap = false)
                    }
                }

                Button(
                    onClick = {
                        val sanitizedConfig = draftConfig.copy(
                            prepCountdownSeconds = 0,
                            soundEnabled = baseSoundEnabled,
                            vibrationEnabled = baseVibrationEnabled
                        )
                        onSaveStage(QueuedTimerStage(sanitizedConfig, transitionSeconds.coerceAtLeast(0)))
                        onDismiss()
                    },
                    modifier = Modifier
                        .weight(1.3f)
                        .height(52.dp),
                    shape = RoundedCornerShape(999.dp)
                ) {
                    Text(
                        text = if (isContinueFromFinished) "Start Next Mode" else "Save Next Mode",
                        fontWeight = FontWeight.Bold,
                        maxLines = 1,
                        softWrap = false
                    )
                }
            }
        }
    }
}

fun defaultConfigForMode(
    mode: TimerMode,
    soundEnabled: Boolean = true,
    vibrationEnabled: Boolean = true
): WorkoutTimerConfig = when (mode) {
    TimerMode.EMOM -> WorkoutTimerConfig(
        mode = TimerMode.EMOM,
        intervalSeconds = 60,
        totalRounds = 10,
        soundEnabled = soundEnabled,
        vibrationEnabled = vibrationEnabled
    )
    TimerMode.AMRAP -> WorkoutTimerConfig(
        mode = TimerMode.AMRAP,
        targetMinutes = 12,
        totalRounds = 1,
        soundEnabled = soundEnabled,
        vibrationEnabled = vibrationEnabled
    )
    TimerMode.DEATH_BY -> WorkoutTimerConfig(
        mode = TimerMode.DEATH_BY,
        intervalSeconds = 60,
        totalRounds = 15,
        soundEnabled = soundEnabled,
        vibrationEnabled = vibrationEnabled
    )
    TimerMode.TIME_CAP -> WorkoutTimerConfig(
        mode = TimerMode.TIME_CAP,
        targetMinutes = 15,
        totalRounds = 1,
        soundEnabled = soundEnabled,
        vibrationEnabled = vibrationEnabled
    )
    TimerMode.TABATA -> WorkoutTimerConfig(
        mode = TimerMode.TABATA,
        workSeconds = 20,
        restSeconds = 10,
        totalRounds = 8,
        soundEnabled = soundEnabled,
        vibrationEnabled = vibrationEnabled
    )
    TimerMode.REST -> WorkoutTimerConfig(
        mode = TimerMode.REST,
        restSeconds = 90,
        totalRounds = 1,
        soundEnabled = soundEnabled,
        vibrationEnabled = vibrationEnabled
    )
}

/**
 * Tap-to-type numeric dialog enforcing domain minimum & maximum seconds cleanly.
 */
@Composable
fun DurationTapToTypeDialog(
    title: String,
    initialSeconds: Int,
    minSeconds: Int,
    maxSeconds: Int = 3600,
    onDismiss: () -> Unit,
    onConfirm: (Int) -> Unit
) {
    var typedSeconds by remember(initialSeconds) { mutableIntStateOf(initialSeconds.coerceIn(minSeconds, maxSeconds)) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title, fontWeight = FontWeight.Bold) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    text = "Enter duration in seconds (minimum ${minSeconds}s).",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                AppNumericTextField(
                    value = typedSeconds,
                    onValueChange = { typedSeconds = it },
                    label = { Text("Seconds") },
                    minValue = minSeconds,
                    maxValue = maxSeconds,
                    modifier = Modifier.fillMaxWidth()
                )
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    onConfirm(typedSeconds.coerceIn(minSeconds, maxSeconds))
                    onDismiss()
                },
                modifier = Modifier.heightIn(min = 48.dp)
            ) {
                Text("Apply")
            }
        },
        dismissButton = {
            TextButton(
                onClick = onDismiss,
                modifier = Modifier.heightIn(min = 48.dp)
            ) {
                Text("Cancel")
            }
        }
    )
}

/**
 * Confirmation dialog shown when tapping Reset while a Next Mode is queued,
 * a multi-stage chain is active, or [TimerPhase.TRANSITION] is running.
 */
@Composable
fun ResetTimerConfirmDialog(
    hasQueuedNext: Boolean,
    onDismiss: () -> Unit,
    onRestartCurrentOnly: () -> Unit,
    onStopAndClearAll: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Reset Timer Session?", fontWeight = FontWeight.Bold) },
        text = {
            Text(
                text = if (hasQueuedNext) {
                    "You have a queued Next Mode configured. Choose whether to restart only the current mode or stop and clear everything."
                } else {
                    "You have completed prior stages in this continuous session. Restart the active mode or stop and clear the full session?"
                },
                style = MaterialTheme.typography.bodyMedium
            )
        },
        confirmButton = {
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Button(
                    onClick = {
                        onRestartCurrentOnly()
                        onDismiss()
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(min = 48.dp)
                ) {
                    Text("Restart Current Mode (Keep Next Mode)")
                }
                OutlinedButton(
                    onClick = {
                        onStopAndClearAll()
                        onDismiss()
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(min = 48.dp)
                ) {
                    Text("Stop & Clear All")
                }
                TextButton(
                    onClick = onDismiss,
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(min = 48.dp)
                ) {
                    Text("Cancel")
                }
            }
        }
    )
}

private data class NumericDialogTarget(
    val title: String,
    val initialSeconds: Int,
    val minSeconds: Int,
    val onConfirm: (Int) -> Unit
)

/**
 * Unified focused workout timer card rendered across [TimerScreen] and [InSessionTimerSheet]
 * whenever `snapshot.phase != TimerPhase.IDLE`.
 */
@Composable
fun ActiveContinuousTimerCard(
    snapshot: TimerSnapshot,
    timerEngine: TimerEngine,
    onStartService: () -> Unit,
    onOpenQueueSheet: (continueFromFinished: Boolean) -> Unit,
    modifier: Modifier = Modifier
) {
    var showResetConfirm by remember { mutableStateOf(false) }
    var dialogTarget by remember { mutableStateOf<NumericDialogTarget?>(null) }

    // Boundary-only TalkBack polite announcement (never triggered by 1Hz ticks)
    var previousSnapshot by remember { mutableStateOf<TimerSnapshot?>(null) }
    var liveAnnouncement by remember { mutableStateOf("") }
    LaunchedEffect(snapshot) {
        val boundaryMsg = ContinuousTimerUiResolver.resolvePoliteBoundaryAnnouncement(previousSnapshot, snapshot)
        if (boundaryMsg != null) {
            liveAnnouncement = boundaryMsg
        }
        previousSnapshot = snapshot
    }

    Box(
        modifier = Modifier
            .size(0.dp)
            .semantics {
                liveRegion = LiveRegionMode.Polite
                contentDescription = liveAnnouncement
            }
    )

    val phaseColor = resolveTimerPhaseColor(snapshot.phase)
    val badgeBg = resolveTimerPhaseBadgeContainer(snapshot.phase)
    val badgeFg = resolveTimerPhaseBadgeContent(snapshot.phase)
    val onPhaseBtnText = if (phaseColor.luminance() > 0.45f) Color.Black else Color.White

    ElevatedCard(modifier = modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            // Top Stage & Phase Header
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Surface(
                    color = badgeBg,
                    shape = RoundedCornerShape(8.dp)
                ) {
                    Text(
                        text = snapshot.phase.label.uppercase(Locale.getDefault()),
                        color = badgeFg,
                        fontWeight = FontWeight.Bold,
                        style = MaterialTheme.typography.titleSmall,
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp)
                    )
                }

                val stageLabel = when {
                    snapshot.phase == TimerPhase.FINISHED ->
                        "${snapshot.completedStagesCount + 1} stage${if (snapshot.completedStagesCount > 0) "s" else ""}"
                    snapshot.completedStagesCount > 0 || snapshot.queuedNextStage != null ->
                        "Stage ${snapshot.completedStagesCount + 1} • ${snapshot.mode.label}"
                    else -> snapshot.mode.label
                }
                Text(
                    text = stageLabel,
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }

            if (snapshot.phase == TimerPhase.FINISHED) {
                FinishedStageContent(
                    snapshot = snapshot,
                    onAddAnotherMode = { onOpenQueueSheet(true) },
                    onDoneReset = { timerEngine.stopAndClearAll() }
                )
            } else {
                // Hero Progress Gauge + Primary Tabular Clock
                Box(
                    contentAlignment = Alignment.Center,
                    modifier = Modifier.size(216.dp)
                ) {
                    val progress = if (snapshot.roundTotalSeconds > 0) {
                        (snapshot.roundSecondsElapsed.toFloat() / snapshot.roundTotalSeconds.toFloat()).coerceIn(0f, 1f)
                    } else 0f

                    CircularProgressIndicator(
                        progress = { progress },
                        modifier = Modifier.fillMaxSize(),
                        color = phaseColor,
                        strokeWidth = 12.dp,
                        trackColor = MaterialTheme.colorScheme.surfaceVariant
                    )

                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        modifier = Modifier.padding(horizontal = 18.dp)
                    ) {
                        Text(
                            text = ContinuousTimerUiResolver.formatPrimaryClock(snapshot),
                            style = tabularStyle(
                                TextStyle(
                                    fontSize = 48.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                            ),
                            maxLines = 1
                        )

                        if (snapshot.phase != TimerPhase.TRANSITION) {
                            Text(
                                text = "Round ${snapshot.currentRound} of ${snapshot.totalRounds}",
                                style = tabularStyle(MaterialTheme.typography.titleSmall),
                                fontWeight = FontWeight.SemiBold,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                        }

                        val hint = ContinuousTimerUiResolver.formatSecondaryClockHint(snapshot)
                        if (hint != null) {
                            Text(
                                text = hint,
                                style = tabularStyle(MaterialTheme.typography.labelMedium),
                                color = if (snapshot.phase == TimerPhase.TRANSITION) {
                                    phaseColor
                                } else {
                                    MaterialTheme.colorScheme.onSurfaceVariant
                                },
                                fontWeight = FontWeight.SemiBold,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    }
                }

                if (snapshot.phase == TimerPhase.TRANSITION) {
                    val queued = snapshot.queuedNextStage
                    if (queued != null) {
                        OutlinedCard(
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(12.dp),
                            border = BorderStroke(1.dp, phaseColor.copy(alpha = 0.6f))
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 12.dp, vertical = 10.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = "Up Next: ${queued.config.mode.label}",
                                        style = MaterialTheme.typography.titleSmall,
                                        fontWeight = FontWeight.Bold,
                                        color = phaseColor
                                    )
                                    Text(
                                        text = ContinuousTimerUiResolver.formatCompactModeSummary(queued.config),
                                        style = MaterialTheme.typography.bodyMedium,
                                        fontWeight = FontWeight.Medium,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                    Text(
                                        text = ContinuousTimerUiResolver.formatTransitionStartingLine(queued),
                                        style = tabularStyle(MaterialTheme.typography.bodySmall),
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                                IconButton(
                                    onClick = { onOpenQueueSheet(false) },
                                    modifier = Modifier.size(48.dp)
                                ) {
                                    Icon(Icons.Filled.Edit, contentDescription = "Edit upcoming stage")
                                }
                            }
                        }
                    }

                    // Live Transition Clock Steppers
                    LiveStepperRow(
                        label = "Transition Clock",
                        stepSeconds = 15,
                        currentSeconds = snapshot.roundSecondsRemaining,
                        isCustomized = false,
                        onDecrement = { timerEngine.adjustTransitionRemaining(-15) },
                        onIncrement = { timerEngine.adjustTransitionRemaining(15) },
                        onValueClick = {
                            dialogTarget = NumericDialogTarget(
                                title = "Edit Transition Remaining",
                                initialSeconds = snapshot.roundSecondsRemaining,
                                minSeconds = 0,
                                onConfirm = { secs -> timerEngine.setTransitionRemaining(secs) }
                            )
                        }
                    )
                } else {
                    // Live Rounds + Next-Round Single-Round Adjustment Panel
                    val liveState = ContinuousTimerUiResolver.resolveLiveAdjustmentUiState(snapshot)
                    if (liveState.showRoundControls) {
                        Column(
                            modifier = Modifier.fillMaxWidth(),
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Text(
                                    text = "Rounds",
                                    style = MaterialTheme.typography.labelLarge,
                                    fontWeight = FontWeight.SemiBold
                                )
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                                ) {
                                    FilledTonalButton(
                                        onClick = { timerEngine.adjustTotalRounds(-1) },
                                        enabled = liveState.canDecrementRounds,
                                        modifier = Modifier.defaultMinSize(minWidth = 48.dp, minHeight = 48.dp),
                                        contentPadding = PaddingValues(horizontal = 12.dp)
                                    ) {
                                        Icon(Icons.Filled.Remove, contentDescription = "Decrease total rounds")
                                    }

                                    Text(
                                        text = "${snapshot.currentRound} / ${snapshot.totalRounds}",
                                        style = tabularStyle(MaterialTheme.typography.titleMedium),
                                        fontWeight = FontWeight.Bold,
                                        modifier = Modifier.padding(horizontal = 6.dp)
                                    )

                                    FilledTonalButton(
                                        onClick = { timerEngine.adjustTotalRounds(1) },
                                        enabled = liveState.canIncrementRounds,
                                        modifier = Modifier.defaultMinSize(minWidth = 48.dp, minHeight = 48.dp),
                                        contentPadding = PaddingValues(horizontal = 12.dp)
                                    ) {
                                        Icon(Icons.Filled.Add, contentDescription = "Increase total rounds")
                                    }
                                }
                            }

                            if (liveState.lastRoundHint != null) {
                                Text(
                                    text = liveState.lastRoundHint,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            } else if (liveState.canEditNextRound) {
                                val nextRoundNum = snapshot.currentRound + 1
                                LiveStepperRow(
                                    label = if (liveState.showTabataNextRestRow) "Next Work (R$nextRoundNum)" else "Next Round (R$nextRoundNum)",
                                    stepSeconds = liveState.stepSeconds,
                                    currentSeconds = liveState.effectiveNextWorkSeconds,
                                    isCustomized = liveState.isNextWorkCustomized,
                                    onDecrement = { timerEngine.adjustNextRoundDuration(-liveState.stepSeconds) },
                                    onIncrement = { timerEngine.adjustNextRoundDuration(liveState.stepSeconds) },
                                    onValueClick = {
                                        dialogTarget = NumericDialogTarget(
                                            title = "Next Round R$nextRoundNum Duration",
                                            initialSeconds = liveState.effectiveNextWorkSeconds,
                                            minSeconds = 5,
                                            onConfirm = { secs -> timerEngine.setNextRoundDuration(secs) }
                                        )
                                    }
                                )

                                if (liveState.showTabataNextRestRow) {
                                    LiveStepperRow(
                                        label = "Next Rest (R$nextRoundNum)",
                                        stepSeconds = liveState.stepSeconds,
                                        currentSeconds = liveState.effectiveNextRestSeconds,
                                        isCustomized = liveState.isNextRestCustomized,
                                        onDecrement = { timerEngine.adjustNextRoundRestDuration(-liveState.stepSeconds) },
                                        onIncrement = { timerEngine.adjustNextRoundRestDuration(liveState.stepSeconds) },
                                        onValueClick = {
                                            dialogTarget = NumericDialogTarget(
                                                title = "Next Rest R$nextRoundNum Duration",
                                                initialSeconds = liveState.effectiveNextRestSeconds,
                                                minSeconds = 0,
                                                onConfirm = { secs -> timerEngine.setNextRoundRestDuration(secs) }
                                            )
                                        }
                                    )
                                }
                            }
                        }
                    }

                    // Live Single-Slot Up Next Picker
                    UpNextQueueCard(
                        queuedStage = snapshot.queuedNextStage,
                        onOpenPicker = { onOpenQueueSheet(false) },
                        onClearQueue = { timerEngine.setQueuedNextStage(null) }
                    )
                }

                // Total Elapsed & Remaining Row
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceEvenly
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text("Total Elapsed", style = MaterialTheme.typography.labelSmall)
                        Text(
                            text = ContinuousTimerUiResolver.formatClock(snapshot.totalSecondsElapsed),
                            style = tabularStyle(MaterialTheme.typography.titleMedium),
                            fontWeight = FontWeight.Bold
                        )
                    }
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text("Total Remaining", style = MaterialTheme.typography.labelSmall)
                        Text(
                            text = ContinuousTimerUiResolver.formatClock(snapshot.totalSecondsRemaining),
                            style = tabularStyle(MaterialTheme.typography.titleMedium),
                            fontWeight = FontWeight.Bold
                        )
                    }
                }

                HorizontalDivider()

                // Compact Anti-Wrap 3-Pill Horizontal Transport Row (360dp + fontScale 1.3x safe)
                val fontScale = LocalDensity.current.fontScale
                val showIcons = fontScale < 1.25f

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.CenterHorizontally),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    val onResetClick: () -> Unit = {
                        if (ContinuousTimerUiResolver.shouldConfirmReset(snapshot)) {
                            showResetConfirm = true
                        } else {
                            timerEngine.reset()
                        }
                    }

                    // 1. Left Pill: Reset
                    TransportOutlinedPill(
                        label = "Reset",
                        icon = Icons.Filled.Refresh,
                        showIcon = showIcons,
                        onClick = onResetClick,
                        modifier = Modifier.weight(0.92f)
                    )

                    if (snapshot.phase == TimerPhase.TRANSITION) {
                        // 2. Middle Prominent Pill: Start Now (promotes Stage 2 immediately even if paused)
                        TransportPrimaryPill(
                            label = "Start Now",
                            icon = Icons.Filled.SkipNext,
                            showIcon = showIcons,
                            containerColor = phaseColor,
                            contentColor = onPhaseBtnText,
                            onClick = {
                                timerEngine.skipRound()
                                onStartService()
                            },
                            modifier = Modifier.weight(1.18f)
                        )

                        // 3. Right Pill: Pause / Resume Transition
                        TransportOutlinedPill(
                            label = if (snapshot.isRunning) "Pause" else "Resume",
                            icon = if (snapshot.isRunning) Icons.Filled.Pause else Icons.Filled.PlayArrow,
                            showIcon = showIcons,
                            onClick = {
                                if (snapshot.isRunning) {
                                    timerEngine.pause()
                                } else {
                                    timerEngine.start()
                                    onStartService()
                                }
                            },
                            modifier = Modifier.weight(0.95f)
                        )
                    } else {
                        // 2. Middle Prominent Pill: Pause / Resume
                        TransportPrimaryPill(
                            label = if (snapshot.isRunning) "Pause" else "Resume",
                            icon = if (snapshot.isRunning) Icons.Filled.Pause else Icons.Filled.PlayArrow,
                            showIcon = showIcons,
                            containerColor = phaseColor,
                            contentColor = onPhaseBtnText,
                            onClick = {
                                if (snapshot.isRunning) {
                                    timerEngine.pause()
                                } else {
                                    timerEngine.start()
                                    onStartService()
                                }
                            },
                            modifier = Modifier.weight(1.08f)
                        )

                        // 3. Right Pill: Skip / Next Stage (always actionable in WORK/REST/PREP)
                        TransportOutlinedPill(
                            label = ContinuousTimerUiResolver.resolveSkipActionLabel(snapshot),
                            icon = Icons.Filled.SkipNext,
                            showIcon = showIcons,
                            onClick = {
                                timerEngine.skipRound()
                                if (timerEngine.snapshot.value.isRunning) {
                                    onStartService()
                                }
                            },
                            modifier = Modifier.weight(1.0f)
                        )
                    }
                }
            }
        }
    }

    dialogTarget?.let { target ->
        DurationTapToTypeDialog(
            title = target.title,
            initialSeconds = target.initialSeconds,
            minSeconds = target.minSeconds,
            onDismiss = { dialogTarget = null },
            onConfirm = target.onConfirm
        )
    }

    if (showResetConfirm) {
        ResetTimerConfirmDialog(
            hasQueuedNext = snapshot.queuedNextStage != null,
            onDismiss = { showResetConfirm = false },
            onRestartCurrentOnly = {
                timerEngine.restartCurrentStage()
                onStartService()
            },
            onStopAndClearAll = {
                timerEngine.stopAndClearAll()
            }
        )
    }
}

@Composable
private fun FinishedStageContent(
    snapshot: TimerSnapshot,
    onAddAnotherMode: () -> Unit,
    onDoneReset: () -> Unit
) {
    Column(
        modifier = Modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Text(
            text = ContinuousTimerUiResolver.formatClock(snapshot.totalSecondsElapsed),
            style = tabularStyle(
                TextStyle(
                    fontSize = 46.sp,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface
                )
            )
        )
        Text(
            text = "Total Workout Time • ${snapshot.completedStagesCount + 1} stage${if (snapshot.completedStagesCount > 0) "s" else ""} completed",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )

        Spacer(Modifier.height(4.dp))

        Button(
            onClick = onAddAnotherMode,
            modifier = Modifier
                .fillMaxWidth()
                .height(52.dp),
            shape = RoundedCornerShape(999.dp)
        ) {
            Icon(Icons.Filled.Add, contentDescription = null)
            Spacer(Modifier.width(6.dp))
            Text(
                text = "Add Another Mode",
                fontWeight = FontWeight.Bold,
                maxLines = 1,
                softWrap = false
            )
        }

        OutlinedButton(
            onClick = onDoneReset,
            modifier = Modifier
                .fillMaxWidth()
                .height(52.dp),
            shape = RoundedCornerShape(999.dp)
        ) {
            Icon(Icons.Filled.Refresh, contentDescription = null)
            Spacer(Modifier.width(6.dp))
            Text(
                text = "Done / Reset",
                fontWeight = FontWeight.SemiBold,
                maxLines = 1,
                softWrap = false
            )
        }
    }
}

@Composable
private fun LiveStepperRow(
    label: String,
    stepSeconds: Int,
    currentSeconds: Int,
    isCustomized: Boolean,
    onDecrement: () -> Unit,
    onIncrement: () -> Unit,
    onValueClick: () -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Column(modifier = Modifier.weight(1f).padding(end = 6.dp)) {
            Text(
                text = label,
                style = MaterialTheme.typography.labelLarge,
                fontWeight = FontWeight.SemiBold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            if (isCustomized) {
                Text(
                    text = "Custom single-round override",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.primary
                )
            }
        }

        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            FilledTonalButton(
                onClick = onDecrement,
                modifier = Modifier.defaultMinSize(minWidth = 48.dp, minHeight = 48.dp),
                contentPadding = PaddingValues(horizontal = 8.dp)
            ) {
                Text(
                    text = "-${stepSeconds}s",
                    style = tabularStyle(MaterialTheme.typography.labelMedium),
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    softWrap = false
                )
            }

            Surface(
                shape = RoundedCornerShape(8.dp),
                color = MaterialTheme.colorScheme.surfaceVariant,
                modifier = Modifier
                    .defaultMinSize(minWidth = 60.dp, minHeight = 48.dp)
                    .clickable(onClick = onValueClick)
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.Center
                ) {
                    Text(
                        text = ContinuousTimerUiResolver.formatDurationChip(currentSeconds),
                        style = tabularStyle(MaterialTheme.typography.labelLarge),
                        fontWeight = FontWeight.Bold,
                        maxLines = 1,
                        softWrap = false
                    )
                    Spacer(Modifier.width(3.dp))
                    Icon(
                        Icons.Filled.Edit,
                        contentDescription = "Edit $label duration",
                        modifier = Modifier.size(14.dp),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            FilledTonalButton(
                onClick = onIncrement,
                modifier = Modifier.defaultMinSize(minWidth = 48.dp, minHeight = 48.dp),
                contentPadding = PaddingValues(horizontal = 8.dp)
            ) {
                Text(
                    text = "+${stepSeconds}s",
                    style = tabularStyle(MaterialTheme.typography.labelMedium),
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    softWrap = false
                )
            }
        }
    }
}

@Composable
private fun TransportPrimaryPill(
    label: String,
    icon: ImageVector,
    showIcon: Boolean,
    containerColor: Color,
    contentColor: Color,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Button(
        onClick = onClick,
        colors = ButtonDefaults.buttonColors(
            containerColor = containerColor,
            contentColor = contentColor
        ),
        shape = RoundedCornerShape(999.dp),
        contentPadding = PaddingValues(horizontal = 8.dp, vertical = 0.dp),
        modifier = modifier.height(52.dp)
    ) {
        if (showIcon) {
            Icon(icon, contentDescription = null, modifier = Modifier.size(17.dp))
            Spacer(Modifier.width(4.dp))
        }
        Text(
            text = label,
            fontWeight = FontWeight.Bold,
            style = MaterialTheme.typography.labelLarge,
            maxLines = 1,
            softWrap = false,
            overflow = TextOverflow.Ellipsis
        )
    }
}

@Composable
private fun TransportOutlinedPill(
    label: String,
    icon: ImageVector,
    showIcon: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    OutlinedButton(
        onClick = onClick,
        shape = RoundedCornerShape(999.dp),
        contentPadding = PaddingValues(horizontal = 8.dp, vertical = 0.dp),
        modifier = modifier.height(52.dp)
    ) {
        if (showIcon) {
            Icon(icon, contentDescription = null, modifier = Modifier.size(17.dp))
            Spacer(Modifier.width(4.dp))
        }
        Text(
            text = label,
            fontWeight = FontWeight.SemiBold,
            style = MaterialTheme.typography.labelLarge,
            maxLines = 1,
            softWrap = false,
            overflow = TextOverflow.Ellipsis
        )
    }
}
