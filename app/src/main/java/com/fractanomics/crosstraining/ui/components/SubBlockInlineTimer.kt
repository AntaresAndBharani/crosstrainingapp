package com.fractanomics.crosstraining.ui.components

import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.SkipNext
import androidx.compose.material.icons.filled.SkipPrevious
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.fractanomics.crosstraining.ui.timer.TimerEngine
import com.fractanomics.crosstraining.ui.timer.TimerPhase
import java.util.Locale

/**
 * Isolated leaf composable rendering an inline workout timer directly inside a Sub-Block header.
 *
 * Observes [TimerEngine.snapshot] via [collectAsStateWithLifecycle] to prevent recomposition of
 * parent forms, set tables, or sibling blocks.
 *
 * Features:
 * - Material 3 styling with ElevatedCard container.
 * - Phase status badge: PREP ("Get Ready!"), WORK ("Work"), REST ("Rest"), FINISHED ("Finished! 🎉").
 * - Live countdown clock (MM:SS) and dynamic round indicator ("Round X of Y").
 * - Smooth progress bar displaying interval or round completion.
 * - Non-focus-stealing controls (focusProperties { canFocus = false }) ensuring virtual keyboard
 *   and cursor focus remain intact in set inputs:
 *     * Rewind (⏮) - decrements round via [TimerEngine.previousRound], disabled on Round 1 or single-round modes.
 *     * Play / Pause (⏯) - toggles timer execution.
 *     * Next (⏭) - advances to next round/phase via [TimerEngine.skipRound].
 *     * Close (✕) - stops timer and collapses widget via [TimerEngine.stop].
 */
@Composable
fun SubBlockInlineTimer(
    timerEngine: TimerEngine,
    modifier: Modifier = Modifier,
    onStartService: () -> Unit = {}
) {
    val snapshot by timerEngine.snapshot.collectAsStateWithLifecycle()

    if (snapshot.phase == TimerPhase.IDLE) {
        return
    }

    val nonFocusInteractionSource = remember { MutableInteractionSource() }

    val phaseBadgeColor = when (snapshot.phase) {
        TimerPhase.PREP -> MaterialTheme.colorScheme.tertiaryContainer
        TimerPhase.WORK -> MaterialTheme.colorScheme.primaryContainer
        TimerPhase.REST -> MaterialTheme.colorScheme.secondaryContainer
        TimerPhase.FINISHED -> MaterialTheme.colorScheme.surfaceVariant
        TimerPhase.IDLE -> MaterialTheme.colorScheme.surfaceVariant
    }

    val phaseTextColor = when (snapshot.phase) {
        TimerPhase.PREP -> MaterialTheme.colorScheme.onTertiaryContainer
        TimerPhase.WORK -> MaterialTheme.colorScheme.onPrimaryContainer
        TimerPhase.REST -> MaterialTheme.colorScheme.onSecondaryContainer
        TimerPhase.FINISHED -> MaterialTheme.colorScheme.onSurfaceVariant
        TimerPhase.IDLE -> MaterialTheme.colorScheme.onSurfaceVariant
    }

    val phaseBadgeText = when (snapshot.phase) {
        TimerPhase.PREP -> "Get Ready!"
        TimerPhase.WORK -> "Work"
        TimerPhase.REST -> "Rest"
        TimerPhase.FINISHED -> "Finished! 🎉"
        TimerPhase.IDLE -> "Idle"
    }

    val countdownText = formatClockDisplay(snapshot.roundSecondsRemaining)

    val progress = when {
        snapshot.phase == TimerPhase.FINISHED -> 1f
        snapshot.roundTotalSeconds > 0 -> {
            (snapshot.roundSecondsElapsed.toFloat() / snapshot.roundTotalSeconds.toFloat()).coerceIn(0f, 1f)
        }
        else -> 0f
    }

    val canRewind = snapshot.totalRounds > 1 && (snapshot.currentRound > 1 || snapshot.phase == TimerPhase.FINISHED || snapshot.phase == TimerPhase.REST)

    ElevatedCard(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(10.dp),
        elevation = CardDefaults.elevatedCardElevation(defaultElevation = 3.dp),
        colors = CardDefaults.elevatedCardColors(
            containerColor = MaterialTheme.colorScheme.surface
        )
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 10.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                // Left side: Phase Badge + Countdown Clock + Round Indicator
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.weight(1f, fill = false)
                ) {
                    // Phase badge
                    Surface(
                        color = phaseBadgeColor,
                        shape = RoundedCornerShape(6.dp)
                    ) {
                        Text(
                            text = phaseBadgeText,
                            color = phaseTextColor,
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                        )
                    }

                    // Countdown clock
                    Text(
                        text = countdownText,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        fontSize = 18.sp,
                        color = MaterialTheme.colorScheme.onSurface
                    )

                    // Round indicator
                    val roundText = if (snapshot.totalRounds > 1) {
                        "Round ${snapshot.currentRound} of ${snapshot.totalRounds}"
                    } else {
                        snapshot.mode.label
                    }
                    Text(
                        text = roundText,
                        style = MaterialTheme.typography.bodySmall,
                        fontWeight = FontWeight.Medium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                // Right side: Controls (Rewind, Play/Pause, Next, Close)
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(2.dp)
                ) {
                    // Rewind button (⏮)
                    IconButton(
                        onClick = { timerEngine.previousRound() },
                        enabled = canRewind,
                        modifier = Modifier
                            .size(32.dp)
                            .focusProperties { canFocus = false },
                        interactionSource = nonFocusInteractionSource
                    ) {
                        Icon(
                            imageVector = Icons.Filled.SkipPrevious,
                            contentDescription = "Rewind round",
                            tint = if (canRewind) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant,
                            modifier = Modifier.size(18.dp)
                        )
                    }

                    // Play / Pause button (⏯)
                    IconButton(
                        onClick = {
                            if (snapshot.isRunning) {
                                timerEngine.pause()
                            } else {
                                timerEngine.start()
                                onStartService()
                            }
                        },
                        modifier = Modifier
                            .size(32.dp)
                            .focusProperties { canFocus = false },
                        interactionSource = nonFocusInteractionSource
                    ) {
                        Icon(
                            imageVector = if (snapshot.isRunning) Icons.Filled.Pause else Icons.Filled.PlayArrow,
                            contentDescription = if (snapshot.isRunning) "Pause timer" else "Resume timer",
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(18.dp)
                        )
                    }

                    // Next round button (⏭)
                    if (snapshot.totalRounds > 1) {
                        IconButton(
                            onClick = { timerEngine.skipRound() },
                            modifier = Modifier
                                .size(32.dp)
                                .focusProperties { canFocus = false },
                            interactionSource = nonFocusInteractionSource
                        ) {
                            Icon(
                                imageVector = Icons.Filled.SkipNext,
                                contentDescription = "Next round",
                                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.size(18.dp)
                            )
                        }
                    }

                    // Dismiss / Close button (✕)
                    IconButton(
                        onClick = { timerEngine.stop() },
                        modifier = Modifier
                            .size(32.dp)
                            .focusProperties { canFocus = false },
                        interactionSource = nonFocusInteractionSource
                    ) {
                        Icon(
                            imageVector = Icons.Filled.Close,
                            contentDescription = "Dismiss sub-block timer",
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(18.dp)
                        )
                    }
                }
            }

            // Live progress bar
            LinearProgressIndicator(
                progress = { progress },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(3.dp),
                color = when (snapshot.phase) {
                    TimerPhase.WORK -> MaterialTheme.colorScheme.primary
                    TimerPhase.REST -> MaterialTheme.colorScheme.secondary
                    TimerPhase.PREP -> MaterialTheme.colorScheme.tertiary
                    TimerPhase.FINISHED -> MaterialTheme.colorScheme.surfaceVariant
                    TimerPhase.IDLE -> MaterialTheme.colorScheme.surfaceVariant
                },
                trackColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
            )
        }
    }
}

private fun formatClockDisplay(seconds: Int): String {
    val mins = seconds / 60
    val secs = seconds % 60
    return String.format(Locale.getDefault(), "%02d:%02d", mins, secs)
}
