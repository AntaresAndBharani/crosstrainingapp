package com.fractanomics.crosstraining.ui.timer

/** Pure UI state projection for live round & single-round next-round adjustments. */
data class LiveAdjustmentUiState(
    val showRoundControls: Boolean = false,
    val canDecrementRounds: Boolean = false,
    val canIncrementRounds: Boolean = false,
    val canEditNextRound: Boolean = false,
    val isLastRound: Boolean = false,
    val stepSeconds: Int = 15,
    val effectiveNextWorkSeconds: Int = 60,
    val isNextWorkCustomized: Boolean = false,
    val showTabataNextRestRow: Boolean = false,
    val effectiveNextRestSeconds: Int = 0,
    val isNextRestCustomized: Boolean = false,
    val lastRoundHint: String? = null
)

/** Curated quick-start stage preset for [NextModeConfigSheet]. */
data class TimerPresetSpec(
    val title: String,
    val subtitle: String,
    val config: WorkoutTimerConfig
)

/**
 * Deterministic pure presentation helpers for continuous multi-stage timer cards, compact bars,
 * notifications, and TalkBack boundary announcements.
 */
object ContinuousTimerUiResolver {

    val QUICK_TRANSITION_SECONDS: List<Int> = listOf(0, 30, 60, 90, 120)

    val NEXT_STAGE_PRESETS: List<TimerPresetSpec> = listOf(
        TimerPresetSpec(
            title = "10m EMOM",
            subtitle = "10 × 60s",
            config = WorkoutTimerConfig(mode = TimerMode.EMOM, intervalSeconds = 60, totalRounds = 10, prepCountdownSeconds = 0)
        ),
        TimerPresetSpec(
            title = "10m AMRAP",
            subtitle = "10:00 Cap",
            config = WorkoutTimerConfig(mode = TimerMode.AMRAP, targetMinutes = 10, totalRounds = 1, prepCountdownSeconds = 0)
        ),
        TimerPresetSpec(
            title = "Tabata 4m",
            subtitle = "8 × 20s/10s",
            config = WorkoutTimerConfig(mode = TimerMode.TABATA, workSeconds = 20, restSeconds = 10, totalRounds = 8, prepCountdownSeconds = 0)
        ),
        TimerPresetSpec(
            title = "2m Rest",
            subtitle = "120s Recovery",
            config = WorkoutTimerConfig(mode = TimerMode.REST, restSeconds = 120, totalRounds = 1, prepCountdownSeconds = 0)
        ),
        TimerPresetSpec(
            title = "12m Time Cap",
            subtitle = "For Time",
            config = WorkoutTimerConfig(mode = TimerMode.TIME_CAP, targetMinutes = 12, totalRounds = 1, prepCountdownSeconds = 0)
        ),
        TimerPresetSpec(
            title = "Death By 15m",
            subtitle = "15 × 60s",
            config = WorkoutTimerConfig(mode = TimerMode.DEATH_BY, intervalSeconds = 60, totalRounds = 15, prepCountdownSeconds = 0)
        )
    )

    /** Formats seconds as `MM:SS` (or `H:MM:SS` when >= 1 hour). */
    fun formatClock(seconds: Int): String {
        val clamped = seconds.coerceAtLeast(0)
        val hours = clamped / 3600
        val minutes = (clamped % 3600) / 60
        val secs = clamped % 60
        return if (hours > 0) {
            "%d:%02d:%02d".format(hours, minutes, secs)
        } else {
            "%02d:%02d".format(minutes, secs)
        }
    }

    /** Formats compact editable duration labels (`0s`, `60s`, `75s`, `12:00`). */
    fun formatDurationChip(seconds: Int): String {
        val clamped = seconds.coerceAtLeast(0)
        return when {
            clamped == 0 -> "0s"
            clamped >= 120 && clamped % 60 == 0 -> formatClock(clamped)
            else -> "${clamped}s"
        }
    }

    /** Formats quick transition pills (`No wait`, `30s`, `60s`, `90s`, `2m`). */
    fun formatTransitionQuickOption(seconds: Int): String = when {
        seconds <= 0 -> "0s (Direct)"
        seconds % 60 == 0 && seconds >= 120 -> "${seconds / 60}m"
        else -> "${seconds}s"
    }

    /**
     * Formats the primary hero clock: counts up elapsed time during active `TIME_CAP` WORK,
     * and counts down remaining time in all other phases/modes.
     */
    fun formatPrimaryClock(snapshot: TimerSnapshot): String = when {
        snapshot.phase == TimerPhase.FINISHED -> "00:00"
        snapshot.phase == TimerPhase.WORK && snapshot.mode == TimerMode.TIME_CAP ->
            formatClock(snapshot.roundSecondsElapsed)
        else -> formatClock(snapshot.roundSecondsRemaining)
    }

    /** Optional contextual sub-line shown directly under the primary hero clock. */
    fun formatSecondaryClockHint(snapshot: TimerSnapshot): String? = when {
        snapshot.phase == TimerPhase.WORK && snapshot.mode == TimerMode.TIME_CAP ->
            "Cap ${formatClock(snapshot.roundTotalSeconds)} • Rem ${formatClock(snapshot.roundSecondsRemaining)}"
        snapshot.phase == TimerPhase.TRANSITION ->
            "Up Next: ${snapshot.queuedNextStage?.config?.mode?.label ?: "Next Stage"}"
        snapshot.phase == TimerPhase.WORK && snapshot.mode == TimerMode.DEATH_BY ->
            "Target: ${snapshot.targetRepsCurrentRound} reps"
        else -> null
    }

    /** Summarizes a stage configuration concisely for previews and Up Next cards. */
    fun formatCompactModeSummary(config: WorkoutTimerConfig): String {
        val work = config.baseWorkDurationSeconds()
        val rounds = config.totalRounds.coerceAtLeast(1)
        val labelPrefix = if (config.workoutLabel.isNotBlank()) "${config.workoutLabel} • " else ""
        val core = when (config.mode) {
            TimerMode.EMOM -> "EMOM ${formatDurationChip(work)} × $rounds"
            TimerMode.AMRAP -> if (rounds > 1) "AMRAP ${formatClock(work)} × $rounds" else "AMRAP ${formatClock(work)}"
            TimerMode.DEATH_BY -> "Death By (${formatDurationChip(work)}) × $rounds"
            TimerMode.TIME_CAP -> if (rounds > 1) "For Time ${formatClock(work)} × $rounds" else "Cap ${formatClock(work)}"
            TimerMode.TABATA -> "Tabata ${work}s/${config.baseRestDurationSeconds()}s × $rounds"
            TimerMode.REST -> if (rounds > 1) "Rest ${formatDurationChip(work)} × $rounds" else "Rest ${formatDurationChip(work)}"
        }
        return "$labelPrefix$core"
    }

    /** Describes the starting state shown inside the `TRANSITION` Up Next preview card. */
    fun formatTransitionStartingLine(nextStage: QueuedTimerStage): String {
        val workFormatted = formatClock(nextStage.config.baseWorkDurationSeconds())
        val rounds = nextStage.config.totalRounds.coerceAtLeast(1)
        return "Starting Round 1 of $rounds ($workFormatted)"
    }

    /** Resolves concise non-wrapping label for the Skip transport button. */
    fun resolveSkipActionLabel(snapshot: TimerSnapshot): String = when {
        snapshot.phase == TimerPhase.TRANSITION -> "Start Now"
        snapshot.currentRound >= snapshot.totalRounds &&
            snapshot.queuedNextStage != null &&
            !(snapshot.mode == TimerMode.TABATA &&
                snapshot.phase == TimerPhase.WORK &&
                snapshot.activeRoundRestSeconds > 0) -> "Next Stage"
        else -> "Skip"
    }

    /**
     * Returns `true` when tapping Reset must confirm before clearing an active chain or multi-stage session.
     */
    fun shouldConfirmReset(snapshot: TimerSnapshot): Boolean =
        snapshot.queuedNextStage != null ||
            snapshot.completedStagesCount > 0 ||
            snapshot.phase == TimerPhase.TRANSITION

    /** Computes live round and single-round Next Round adjustment controls availability. */
    fun resolveLiveAdjustmentUiState(snapshot: TimerSnapshot): LiveAdjustmentUiState {
        val showRounds = snapshot.phase in setOf(TimerPhase.PREP, TimerPhase.WORK, TimerPhase.REST)
        if (!showRounds) {
            return LiveAdjustmentUiState(showRoundControls = false)
        }
        val activeCfg = snapshot.activeConfig
        val isLastRound = snapshot.currentRound >= snapshot.totalRounds
        val canEditNext = !isLastRound
        val nextWork = snapshot.nextRoundOverrideSeconds ?: activeCfg.baseWorkDurationSeconds()
        val nextRest = snapshot.nextRoundRestOverrideSeconds ?: activeCfg.baseRestDurationSeconds()
        val hint = if (isLastRound) {
            "Last round — tap + on Rounds to add R${snapshot.totalRounds + 1}"
        } else {
            null
        }
        return LiveAdjustmentUiState(
            showRoundControls = true,
            canDecrementRounds = snapshot.totalRounds > snapshot.currentRound,
            canIncrementRounds = snapshot.totalRounds < 999,
            canEditNextRound = canEditNext,
            isLastRound = isLastRound,
            stepSeconds = activeCfg.mode.liveStepSeconds,
            effectiveNextWorkSeconds = nextWork,
            isNextWorkCustomized = snapshot.nextRoundOverrideSeconds != null,
            showTabataNextRestRow = canEditNext && activeCfg.mode == TimerMode.TABATA,
            effectiveNextRestSeconds = nextRest,
            isNextRestCustomized = snapshot.nextRoundRestOverrideSeconds != null,
            lastRoundHint = hint
        )
    }

    /**
     * Emits polite TalkBack announcements strictly on phase or round boundaries; returns `null` on
     * normal 1Hz countdown ticks to prevent announcement spam.
     */
    fun resolvePoliteBoundaryAnnouncement(
        previous: TimerSnapshot?,
        current: TimerSnapshot
    ): String? {
        if (current.phase == TimerPhase.IDLE) return null
        val changedBoundary = previous == null ||
            previous.phase != current.phase ||
            previous.currentRound != current.currentRound ||
            previous.mode != current.mode ||
            previous.completedStagesCount != current.completedStagesCount

        if (!changedBoundary) return null

        return when (current.phase) {
            TimerPhase.PREP -> "Get ready, ${current.mode.label}."
            TimerPhase.TRANSITION -> {
                val nextLabel = current.queuedNextStage?.config?.mode?.label ?: "Next stage"
                "Transition started. Up next: $nextLabel."
            }
            TimerPhase.FINISHED -> {
                val totalCompleted = current.completedStagesCount + 1
                val suffix = if (totalCompleted == 1) "stage" else "stages"
                "Workout complete. $totalCompleted $suffix finished."
            }
            TimerPhase.WORK -> "Work, Round ${current.currentRound} of ${current.totalRounds}, ${current.mode.label}."
            TimerPhase.REST -> "Rest, Round ${current.currentRound} of ${current.totalRounds}."
            TimerPhase.IDLE -> null
        }
    }
}
