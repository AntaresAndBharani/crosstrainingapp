package com.fractanomics.crosstraining.ui.timer

import androidx.compose.runtime.Immutable

/**
 * Immutable descriptor for an ordered workout block or exercise inside `Log Session`,
 * decoupling pure timer queue extrapolation from Compose form state holders.
 */
@Immutable
data class SessionTimerBlockEntry(
    val blockKey: String,
    val positionIndex: Int,
    val title: String,
    val defaultSetCount: Int
)

/**
 * Deterministic result of anchoring a workout timer queue at [anchorIndex].
 */
@Immutable
data class BuiltWorkoutTimerQueue(
    val anchorIndex: Int,
    val startingConfig: WorkoutTimerConfig,
    val queuedStages: List<QueuedTimerStage>
)

/**
 * Pure deterministic builder that extrapolates a continuous multi-block timer queue
 * from an ordered list of [SessionTimerBlockEntry] items starting at any anchor block `#k`.
 *
 * Contract:
 * - Starting block `#k` defaults to `E3MOM (180s) × defaultSetCount` (and may be freely
 *   customized by the athlete to any supported [TimerMode] prior to starting).
 * - Every remaining block `#k+1 .. #N` is ALWAYS assigned `E3MOM (180s) × defaultSetCount`
 *   preceded by a `60s` (`1:00`) transition, ignoring block notes or instructions.
 * - Prep countdown runs strictly on Stage 1; remaining stages use `prepCountdownSeconds = 0`.
 */
object WorkoutTimerQueueBuilder {

    const val DEFAULT_EXMOM_INTERVAL_SECONDS = 180
    const val DEFAULT_TRANSITION_SECONDS = 60

    fun buildFromAnchor(
        entries: List<SessionTimerBlockEntry>,
        anchorIndex: Int,
        baseConfig: WorkoutTimerConfig = WorkoutTimerConfig()
    ): BuiltWorkoutTimerQueue? {
        if (entries.isEmpty()) return null

        val safeAnchor = anchorIndex.coerceIn(0, entries.lastIndex)
        val anchorEntry = entries[safeAnchor]
        val startingConfig = WorkoutTimerConfig(
            mode = TimerMode.EMOM,
            intervalSeconds = DEFAULT_EXMOM_INTERVAL_SECONDS,
            totalRounds = anchorEntry.defaultSetCount.coerceAtLeast(1),
            prepCountdownSeconds = baseConfig.prepCountdownSeconds.coerceAtLeast(0),
            soundEnabled = baseConfig.soundEnabled,
            vibrationEnabled = baseConfig.vibrationEnabled,
            workoutLabel = anchorEntry.title.trim(),
            sourceBlockKey = anchorEntry.blockKey
        )

        val queuedStages = entries.drop(safeAnchor + 1).map { entry ->
            QueuedTimerStage(
                config = WorkoutTimerConfig(
                    mode = TimerMode.EMOM,
                    intervalSeconds = DEFAULT_EXMOM_INTERVAL_SECONDS,
                    totalRounds = entry.defaultSetCount.coerceAtLeast(1),
                    prepCountdownSeconds = 0,
                    soundEnabled = baseConfig.soundEnabled,
                    vibrationEnabled = baseConfig.vibrationEnabled,
                    workoutLabel = entry.title.trim(),
                    sourceBlockKey = entry.blockKey
                ),
                transitionSeconds = DEFAULT_TRANSITION_SECONDS
            )
        }

        return BuiltWorkoutTimerQueue(
            anchorIndex = safeAnchor,
            startingConfig = startingConfig,
            queuedStages = queuedStages
        )
    }

    /** Computes total planned duration (seconds) across starting stage + all transitions + queued stages. */
    fun calculateQueueTotalSeconds(
        startingConfig: WorkoutTimerConfig,
        queuedStages: List<QueuedTimerStage>
    ): Int {
        val tailSeconds = queuedStages.sumOf { stage ->
            stage.transitionSeconds.coerceAtLeast(0) + stage.config.stageTotalSeconds()
        }
        return startingConfig.stageTotalSeconds() + tailSeconds
    }
}
