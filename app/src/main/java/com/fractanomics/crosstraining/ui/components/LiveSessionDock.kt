package com.fractanomics.crosstraining.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.FitnessCenter
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Timer
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.fractanomics.crosstraining.ui.draft.LiveDockMode
import com.fractanomics.crosstraining.ui.draft.LiveDockUiState
import com.fractanomics.crosstraining.ui.timer.ContinuousTimerUiResolver
import com.fractanomics.crosstraining.ui.timer.TimerEngine
import com.fractanomics.crosstraining.ui.timer.TimerPhase

/**
 * Global route-aware Live Session Dock rendered above `PrimaryNavigationBar`.
 *
 * 1Hz recomposition isolation: only the leaf [LiveDockTimerChipAndControls] composable
 * collects [TimerEngine.snapshot] so parent navigation scaffolds never recompose every second.
 */
@Composable
fun LiveSessionDock(
    uiState: LiveDockUiState,
    timerEngine: TimerEngine,
    onResumeWorkout: () -> Unit,
    onOpenTimerTarget: (isLinkedToWorkout: Boolean) -> Unit,
    onStartTimerService: () -> Unit,
    modifier: Modifier = Modifier
) {
    AnimatedVisibility(
        visible = uiState.mode != LiveDockMode.NONE,
        enter = expandVertically(animationSpec = tween(220)) + fadeIn(animationSpec = tween(220)),
        exit = shrinkVertically(animationSpec = tween(150)) + fadeOut(animationSpec = tween(150)),
        modifier = modifier
    ) {
        val fontScale = LocalDensity.current.fontScale
        val isLargeFont = fontScale >= 1.3f

        Box(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 6.dp)
        ) {
            Surface(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(12.dp),
                color = MaterialTheme.colorScheme.surfaceContainerHigh,
                tonalElevation = 4.dp,
                shadowElevation = 4.dp,
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.55f))
            ) {
                when (uiState.mode) {
                    LiveDockMode.NONE -> Unit
                    LiveDockMode.WORKOUT_ONLY -> {
                        WorkoutOnlyDockRow(
                            title = uiState.workoutTitle,
                            subtitle = uiState.workoutSubtitle,
                            accessibilityLabel = uiState.workoutAccessibilityLabel,
                            isLargeFont = isLargeFont,
                            onResumeWorkout = onResumeWorkout
                        )
                    }
                    LiveDockMode.TIMER_ONLY -> {
                        LiveDockTimerOnlyRow(
                            timerEngine = timerEngine,
                            onOpenTimer = { onOpenTimerTarget(false) },
                            onStartTimerService = onStartTimerService
                        )
                    }
                    LiveDockMode.WORKOUT_AND_TIMER -> {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 10.dp, vertical = 6.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            WorkoutInfoZone(
                                title = uiState.workoutTitle,
                                subtitle = uiState.workoutSubtitle,
                                accessibilityLabel = uiState.workoutAccessibilityLabel,
                                showSubtitle = !isLargeFont && uiState.workoutSubtitle.isNotBlank(),
                                onClick = onResumeWorkout,
                                modifier = Modifier.weight(1f)
                            )
                            LiveDockTimerChipAndControls(
                                timerEngine = timerEngine,
                                onOpenTimerTarget = { onOpenTimerTarget(uiState.isTimerLinkedToWorkout) },
                                onStartTimerService = onStartTimerService
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun WorkoutOnlyDockRow(
    title: String,
    subtitle: String,
    accessibilityLabel: String,
    isLargeFont: Boolean,
    onResumeWorkout: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .defaultMinSize(minHeight = 56.dp)
            .clickable(role = Role.Button, onClick = onResumeWorkout)
            .semantics(mergeDescendants = true) {
                if (accessibilityLabel.isNotBlank()) {
                    contentDescription = accessibilityLabel
                }
            }
            .padding(horizontal = 12.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        Box(
            modifier = Modifier
                .size(36.dp)
                .clip(RoundedCornerShape(8.dp))
                .background(MaterialTheme.colorScheme.primaryContainer),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = Icons.Filled.FitnessCenter,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onPrimaryContainer,
                modifier = Modifier.size(18.dp)
            )
        }

        Column(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.Center
        ) {
            Text(
                text = title,
                style = MaterialTheme.typography.labelLarge,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            if (subtitle.isNotBlank() && !isLargeFont) {
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.labelSmall.copy(fontFeatureSettings = "tnum"),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }

        Surface(
            color = MaterialTheme.colorScheme.primaryContainer,
            shape = RoundedCornerShape(999.dp)
        ) {
            Row(
                modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(2.dp)
            ) {
                Text(
                    text = "Resume",
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onPrimaryContainer,
                    maxLines = 1
                )
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onPrimaryContainer,
                    modifier = Modifier.size(16.dp)
                )
            }
        }
    }
}

@Composable
private fun WorkoutInfoZone(
    title: String,
    subtitle: String,
    accessibilityLabel: String,
    showSubtitle: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier
            .defaultMinSize(minHeight = 48.dp)
            .clip(RoundedCornerShape(8.dp))
            .clickable(role = Role.Button, onClick = onClick)
            .semantics(mergeDescendants = true) {
                if (accessibilityLabel.isNotBlank()) {
                    contentDescription = accessibilityLabel
                }
            }
            .padding(horizontal = 4.dp, vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Box(
            modifier = Modifier
                .size(32.dp)
                .clip(RoundedCornerShape(8.dp))
                .background(MaterialTheme.colorScheme.primaryContainer),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = Icons.Filled.FitnessCenter,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onPrimaryContainer,
                modifier = Modifier.size(16.dp)
            )
        }
        Column(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.Center
        ) {
            Text(
                text = title,
                style = MaterialTheme.typography.labelLarge,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            if (showSubtitle) {
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.labelSmall.copy(fontFeatureSettings = "tnum"),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
    }
}

@Composable
private fun LiveDockTimerChipAndControls(
    timerEngine: TimerEngine,
    onOpenTimerTarget: () -> Unit,
    onStartTimerService: () -> Unit
) {
    val snapshot by timerEngine.snapshot.collectAsStateWithLifecycle()
    if (snapshot.phase == TimerPhase.IDLE) return

    val nonFocusInteractionSource = remember { MutableInteractionSource() }
    val phaseBadgeColor = resolveTimerPhaseBadgeContainer(snapshot.phase)
    val phaseTextColor = resolveTimerPhaseBadgeContent(snapshot.phase)
    val countdownText = ContinuousTimerUiResolver.formatPrimaryClock(snapshot)
    val phaseShort = when (snapshot.phase) {
        TimerPhase.PREP -> "PREP"
        TimerPhase.WORK -> "WORK"
        TimerPhase.REST -> "REST"
        TimerPhase.TRANSITION -> "NEXT"
        TimerPhase.FINISHED -> "DONE"
        TimerPhase.IDLE -> ""
    }

    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Surface(
            color = phaseBadgeColor,
            shape = RoundedCornerShape(8.dp),
            modifier = Modifier
                .defaultMinSize(minHeight = 48.dp)
                .clip(RoundedCornerShape(8.dp))
                .clickable(
                    interactionSource = nonFocusInteractionSource,
                    indication = null,
                    role = Role.Button,
                    onClick = onOpenTimerTarget
                )
                .semantics(mergeDescendants = true) {
                    contentDescription = "Active timer, ${snapshot.phase.label}, $countdownText. Double tap to open timer."
                }
        ) {
            Row(
                modifier = Modifier.padding(horizontal = 8.dp, vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                Icon(
                    imageVector = Icons.Filled.Timer,
                    contentDescription = null,
                    tint = phaseTextColor,
                    modifier = Modifier.size(14.dp)
                )
                Column(horizontalAlignment = Alignment.End) {
                    Text(
                        text = countdownText,
                        style = MaterialTheme.typography.labelLarge.copy(fontFeatureSettings = "tnum"),
                        fontWeight = FontWeight.Bold,
                        color = phaseTextColor,
                        maxLines = 1
                    )
                    Text(
                        text = phaseShort,
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.SemiBold,
                        color = phaseTextColor,
                        maxLines = 1
                    )
                }
            }
        }

        if (snapshot.phase == TimerPhase.FINISHED) {
            IconButton(
                onClick = { timerEngine.stopAndClearAll() },
                modifier = Modifier
                    .defaultMinSize(minWidth = 48.dp, minHeight = 48.dp)
                    .focusProperties { canFocus = false },
                interactionSource = nonFocusInteractionSource
            ) {
                Icon(
                    imageVector = Icons.Filled.Close,
                    contentDescription = "Dismiss timer",
                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        } else {
            IconButton(
                onClick = {
                    if (snapshot.isRunning) {
                        timerEngine.pause()
                    } else {
                        timerEngine.start()
                        onStartTimerService()
                    }
                },
                modifier = Modifier
                    .defaultMinSize(minWidth = 48.dp, minHeight = 48.dp)
                    .focusProperties { canFocus = false },
                interactionSource = nonFocusInteractionSource
            ) {
                Icon(
                    imageVector = if (snapshot.isRunning) Icons.Filled.Pause else Icons.Filled.PlayArrow,
                    contentDescription = if (snapshot.isRunning) "Pause timer" else "Resume timer",
                    tint = MaterialTheme.colorScheme.primary
                )
            }
        }
    }
}

@Composable
private fun LiveDockTimerOnlyRow(
    timerEngine: TimerEngine,
    onOpenTimer: () -> Unit,
    onStartTimerService: () -> Unit
) {
    val snapshot by timerEngine.snapshot.collectAsStateWithLifecycle()
    if (snapshot.phase == TimerPhase.IDLE) return

    val nonFocusInteractionSource = remember { MutableInteractionSource() }
    val phaseBadgeColor = resolveTimerPhaseBadgeContainer(snapshot.phase)
    val phaseTextColor = resolveTimerPhaseBadgeContent(snapshot.phase)
    val countdownText = ContinuousTimerUiResolver.formatPrimaryClock(snapshot)
    val subtitleText = ContinuousTimerUiResolver.formatInSessionBarSubtitle(snapshot)

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .defaultMinSize(minHeight = 56.dp)
            .clickable(
                interactionSource = nonFocusInteractionSource,
                indication = null,
                role = Role.Button,
                onClick = onOpenTimer
            )
            .padding(horizontal = 12.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Row(
            modifier = Modifier.weight(1f),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Surface(
                color = phaseBadgeColor,
                shape = RoundedCornerShape(6.dp)
            ) {
                Text(
                    text = snapshot.phase.label,
                    color = phaseTextColor,
                    style = MaterialTheme.typography.labelSmall,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 3.dp)
                )
            }

            Text(
                text = countdownText,
                style = MaterialTheme.typography.titleMedium.copy(fontFeatureSettings = "tnum"),
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 1
            )

            Column(modifier = Modifier.weight(1f)) {
                if (snapshot.workoutLabel.isNotBlank()) {
                    Text(
                        text = snapshot.workoutLabel,
                        style = MaterialTheme.typography.bodySmall,
                        fontWeight = FontWeight.SemiBold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                }
                Text(
                    text = subtitleText,
                    style = MaterialTheme.typography.labelSmall.copy(fontFeatureSettings = "tnum"),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }

        if (snapshot.phase == TimerPhase.FINISHED) {
            IconButton(
                onClick = { timerEngine.stopAndClearAll() },
                modifier = Modifier
                    .defaultMinSize(minWidth = 48.dp, minHeight = 48.dp)
                    .focusProperties { canFocus = false },
                interactionSource = nonFocusInteractionSource
            ) {
                Icon(
                    imageVector = Icons.Filled.Close,
                    contentDescription = "Dismiss timer",
                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        } else {
            IconButton(
                onClick = {
                    if (snapshot.isRunning) {
                        timerEngine.pause()
                    } else {
                        timerEngine.start()
                        onStartTimerService()
                    }
                },
                modifier = Modifier
                    .defaultMinSize(minWidth = 48.dp, minHeight = 48.dp)
                    .focusProperties { canFocus = false },
                interactionSource = nonFocusInteractionSource
            ) {
                Icon(
                    imageVector = if (snapshot.isRunning) Icons.Filled.Pause else Icons.Filled.PlayArrow,
                    contentDescription = if (snapshot.isRunning) "Pause timer" else "Resume timer",
                    tint = MaterialTheme.colorScheme.primary
                )
            }
        }
    }
}
