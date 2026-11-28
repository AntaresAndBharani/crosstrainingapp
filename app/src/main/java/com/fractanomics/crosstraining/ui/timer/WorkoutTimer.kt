package com.fractanomics.crosstraining.ui.timer

import androidx.compose.runtime.Immutable
import java.util.UUID

/** Supported timer modes in CrossTraining. */
enum class TimerMode(
    val label: String,
    val description: String,
    val defaultRounds: Int,
    val liveStepSeconds: Int
) {
    EMOM("EMOM / ExMOM", "Every X minutes/seconds for N rounds", defaultRounds = 10, liveStepSeconds = 15),
    AMRAP("AMRAP", "As Many Rounds As Possible in set time", defaultRounds = 1, liveStepSeconds = 30),
    DEATH_BY("Death By", "Increasing reps every minute until failure", defaultRounds = 15, liveStepSeconds = 15),
    TIME_CAP("Time Cap / For Time", "Count up to time limit or count down", defaultRounds = 1, liveStepSeconds = 30),
    TABATA("Tabata / Intervals", "Alternating Work & Rest intervals", defaultRounds = 8, liveStepSeconds = 5),
    REST("Rest Timer", "Rest timer between workout sets", defaultRounds = 1, liveStepSeconds = 15)
}

/** Phases during an active timer execution. */
enum class TimerPhase(val label: String) {
    IDLE("Idle"),
    PREP("Get Ready!"),
    WORK("Work"),
    REST("Rest"),
    TRANSITION("Transition"),
    FINISHED("Done!")
}

/** User configuration for a workout timer session. */
@Immutable
data class WorkoutTimerConfig(
    val mode: TimerMode = TimerMode.EMOM,
    val intervalSeconds: Int = 60,      // E.g. 60 for EMOM, 120 for E2MOM, 180 for E3MOM
    val workSeconds: Int = 20,          // For Tabata / Work-Rest intervals
    val restSeconds: Int = 10,          // For Tabata / Work-Rest / Rest timer
    val totalRounds: Int = mode.defaultRounds, // Total rounds (mode-specific default)
    val targetMinutes: Int = 12,        // Target duration in minutes for AMRAP / Time Cap
    val prepCountdownSeconds: Int = 10, // 3-2-1 GO prep countdown
    val soundEnabled: Boolean = true,
    val vibrationEnabled: Boolean = true,
    val workoutLabel: String = "",
    val targetDurationSeconds: Int? = null, // Optional exact seconds override for setup/presets
    val sourceBlockKey: String? = null // Originating Log Session block/exercise key for sync
) {
    /** Base duration of the active work/countdown segment for a single round (in seconds). */
    fun baseWorkDurationSeconds(): Int = when (mode) {
        TimerMode.EMOM -> (targetDurationSeconds ?: intervalSeconds).coerceAtLeast(0)
        TimerMode.DEATH_BY -> (targetDurationSeconds ?: intervalSeconds).coerceAtLeast(0)
        TimerMode.AMRAP, TimerMode.TIME_CAP -> (targetDurationSeconds ?: (targetMinutes * 60)).coerceAtLeast(0)
        TimerMode.TABATA -> (targetDurationSeconds ?: workSeconds).coerceAtLeast(0)
        TimerMode.REST -> (targetDurationSeconds ?: restSeconds).coerceAtLeast(0)
    }

    /** Base duration of the rest segment per round (in seconds, >0 for Tabata when configured). */
    fun baseRestDurationSeconds(): Int = when (mode) {
        TimerMode.TABATA -> restSeconds.coerceAtLeast(0)
        else -> 0
    }

    /** Total duration of one unmodified round (work + rest). */
    fun singleRoundTotalSeconds(): Int = baseWorkDurationSeconds() + baseRestDurationSeconds()

    /** Total duration of this full stage without live overrides or prep countdown. */
    fun stageTotalSeconds(): Int = singleRoundTotalSeconds() * totalRounds.coerceAtLeast(1)
}

/** Upcoming stage configuration for continuous multi-stage workout chaining. */
@Immutable
data class QueuedTimerStage(
    val config: WorkoutTimerConfig,
    val transitionSeconds: Int = 60,
    val id: String = UUID.randomUUID().toString()
)

/** Dynamic snapshot state for the active timer engine. */
@Immutable
data class TimerSnapshot(
    val phase: TimerPhase = TimerPhase.IDLE,
    val isRunning: Boolean = false,
    val currentRound: Int = 1,
    val totalRounds: Int = 10,
    val roundSecondsRemaining: Int = 0,
    val roundSecondsElapsed: Int = 0,
    val roundTotalSeconds: Int = 60,
    val totalSecondsElapsed: Int = 0,
    val totalSecondsRemaining: Int = 0,
    val targetRepsCurrentRound: Int = 1, // For Death By mode
    val workoutLabel: String = "",
    val mode: TimerMode = TimerMode.EMOM,
    val activeConfig: WorkoutTimerConfig = WorkoutTimerConfig(),
    val activeRoundRestSeconds: Int = 0,
    val nextRoundOverrideSeconds: Int? = null,
    val nextRoundRestOverrideSeconds: Int? = null,
    val queuedStages: List<QueuedTimerStage> = emptyList(),
    val completedStagesCount: Int = 0
) {
    /** Backward-compatible accessor for the immediate next stage in [queuedStages]. */
    val queuedNextStage: QueuedTimerStage?
        get() = queuedStages.firstOrNull()

    /** 1-based position of the currently executing stage within the planned session queue. */
    val currentStageIndex: Int
        get() = completedStagesCount + 1

    /** Total planned stages across completed, active, and remaining queued stages. */
    val totalPlannedStages: Int
        get() = completedStagesCount + 1 + queuedStages.size
}
