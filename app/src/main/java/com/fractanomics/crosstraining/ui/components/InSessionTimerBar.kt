package com.fractanomics.crosstraining.ui.components

import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.SkipNext
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ElevatedCard
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
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.fractanomics.crosstraining.ui.timer.ContinuousTimerUiResolver
import com.fractanomics.crosstraining.ui.timer.TimerEngine
import com.fractanomics.crosstraining.ui.timer.TimerPhase

/**
 * Compact docked in-session timer bar.
 *
 * Leaf composable that directly collects the 1Hz [TimerEngine.snapshot] StateFlow
 * to avoid triggering recomposition of parent editor forms or set tables.
 *
 * Features:
 * - Phase badge: PREP (tertiaryContainer / "Get Ready!"), WORK, REST, FINISHED.
 * - Round countdown display (e.g. "02:45" / "03:00").
 * - Round counter (e.g. "Round 2 of 7") and workout/sub-block label.
 * - Non-focus-stealing inline controls (Play/Pause, Skip) to preserve virtual keyboard focus.
 * - Tap anywhere on the card to trigger [onExpand].
 * - Inline dismiss (X) button visible when timer phase is FINISHED to reset/stop the engine.
 */
@Composable
fun InSessionTimerBar(
    timerEngine: TimerEngine,
    onExpand: () -> Unit,
    modifier: Modifier = Modifier,
    onStartService: () -> Unit = {}
) {
    val snapshot by timerEngine.snapshot.collectAsStateWithLifecycle()

    if (snapshot.phase == TimerPhase.IDLE) {
        return
    }

    val nonFocusInteractionSource = remember { MutableInteractionSource() }

    val phaseBadgeColor = resolveTimerPhaseBadgeContainer(snapshot.phase)
    val phaseTextColor = resolveTimerPhaseBadgeContent(snapshot.phase)

    val phaseBadgeText = when (snapshot.phase) {
        TimerPhase.PREP -> "Get Ready!"
        TimerPhase.WORK -> "Work"
        TimerPhase.REST -> "Rest"
        TimerPhase.TRANSITION -> "Transition"
        TimerPhase.FINISHED -> "Finished!"
        TimerPhase.IDLE -> "Idle"
    }

    val countdownText = ContinuousTimerUiResolver.formatPrimaryClock(snapshot)

    ElevatedCard(
        modifier = modifier
            .fillMaxWidth()
            .clickable(
                interactionSource = nonFocusInteractionSource,
                indication = null,
                onClick = onExpand
            ),
        shape = RoundedCornerShape(12.dp),
        elevation = CardDefaults.elevatedCardElevation(defaultElevation = 6.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            // Left Column: Phase badge, countdown clock & round / label info
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp),
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
                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 3.dp)
                    )
                }

                // Countdown time
                Text(
                    text = countdownText,
                    style = MaterialTheme.typography.titleLarge.copy(fontFeatureSettings = "tnum"),
                    fontWeight = FontWeight.Bold,
                    fontSize = 20.sp,
                    color = MaterialTheme.colorScheme.onSurface
                )

                // Round & Label descriptor
                Column(verticalArrangement = Arrangement.Center) {
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

                    val roundInfo = ContinuousTimerUiResolver.formatInSessionBarSubtitle(snapshot)
                    Text(
                        text = roundInfo,
                        style = MaterialTheme.typography.labelSmall.copy(fontFeatureSettings = "tnum"),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            // Right Row: Non-focus-stealing 48dp action controls
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                if (snapshot.phase == TimerPhase.FINISHED) {
                    // Inline dismiss button to reset engine to IDLE
                    IconButton(
                        onClick = { timerEngine.stopAndClearAll() },
                        modifier = Modifier
                            .defaultMinSize(minWidth = 48.dp, minHeight = 48.dp)
                            .focusProperties { canFocus = false },
                        interactionSource = nonFocusInteractionSource
                    ) {
                        Icon(
                            Icons.Filled.Close,
                            contentDescription = "Dismiss timer",
                            tint = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                } else {
                    // Play / Pause toggle
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

                    // Skip round / Start Now / Next Stage button
                    if (snapshot.totalRounds > 1 ||
                        snapshot.queuedNextStage != null ||
                        snapshot.phase == TimerPhase.TRANSITION ||
                        snapshot.phase == TimerPhase.PREP
                    ) {
                        IconButton(
                            onClick = {
                                timerEngine.skipRound()
                                if (timerEngine.snapshot.value.isRunning) {
                                    onStartService()
                                }
                            },
                            modifier = Modifier
                                .defaultMinSize(minWidth = 48.dp, minHeight = 48.dp)
                                .focusProperties { canFocus = false },
                            interactionSource = nonFocusInteractionSource
                        ) {
                            Icon(
                                Icons.Filled.SkipNext,
                                contentDescription = ContinuousTimerUiResolver.resolveSkipActionLabel(snapshot),
                                tint = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }

                    Icon(
                        Icons.Filled.ExpandLess,
                        contentDescription = "Tap bar to expand timer",
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(20.dp)
                    )
                }
            }
        }
    }
}
