package com.fractanomics.crosstraining.ui.timer

/** Supported timer modes in CrossTraining. */
enum class TimerMode(val label: String, val description: String) {
    EMOM("EMOM / ExMOM", "Every X minutes/seconds for N rounds"),
    AMRAP("AMRAP", "As Many Rounds As Possible in set time"),
    DEATH_BY("Death By", "Increasing reps every minute until failure"),
    TIME_CAP("Time Cap / For Time", "Count up to time limit or count down"),
    TABATA("Tabata / Intervals", "Alternating Work & Rest intervals"),
    REST("Rest Timer", "Rest timer between workout sets")
}

/** Phases during an active timer execution. */
enum class TimerPhase(val label: String) {
    IDLE("Idle"),
    PREP("Get Ready!"),
    WORK("Work"),
    REST("Rest"),
    FINISHED("Done!")
}

/** User configuration for a workout timer session. */
data class WorkoutTimerConfig(
    val mode: TimerMode = TimerMode.EMOM,
    val intervalSeconds: Int = 60,      // E.g. 60 for EMOM, 120 for E2MOM, 180 for E3MOM
    val workSeconds: Int = 20,          // For Tabata / Work-Rest intervals
    val restSeconds: Int = 10,          // For Tabata / Work-Rest / Rest timer
    val totalRounds: Int = 10,          // Total rounds
    val targetMinutes: Int = 12,        // Target duration in minutes for AMRAP / Time Cap
    val prepCountdownSeconds: Int = 10, // 3-2-1 GO prep countdown
    val soundEnabled: Boolean = true,
    val vibrationEnabled: Boolean = true
)

/** Dynamic snapshot state for the active timer engine. */
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
    val targetRepsCurrentRound: Int = 1 // For Death By mode
)
