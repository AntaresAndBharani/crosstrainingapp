package com.fractanomics.crosstraining.ui.draft

import androidx.compose.runtime.Immutable
import com.fractanomics.crosstraining.ui.navigation.BottomDestination
import com.fractanomics.crosstraining.ui.navigation.DrawerItem
import com.fractanomics.crosstraining.ui.timer.TimerPhase
import com.fractanomics.crosstraining.ui.timer.TimerSnapshot

enum class LiveDockMode {
    NONE,
    WORKOUT_ONLY,
    TIMER_ONLY,
    WORKOUT_AND_TIMER
}

@Immutable
data class LiveDockUiState(
    val mode: LiveDockMode = LiveDockMode.NONE,
    val workoutTitle: String = "",
    val workoutSubtitle: String = "",
    val isTimerLinkedToWorkout: Boolean = false,
    val showLogNavBadge: Boolean = false,
    val showDrawerActiveBadge: Boolean = false,
    val showInnerEditorTimerBar: Boolean = false,
    val workoutAccessibilityLabel: String = ""
)

/**
 * Pure deterministic arbitration resolver enforcing at most ONE docked bar
 * above the bottom navigation bar across every route, workout draft state,
 * and timer phase.
 */
object LiveSessionDockResolver {

    fun resolve(
        currentRoute: String?,
        summary: ActiveWorkoutSummary,
        timerSnapshot: TimerSnapshot
    ): LiveDockUiState = resolve(
        currentRoute = currentRoute,
        summary = summary,
        isTimerActive = timerSnapshot.phase != TimerPhase.IDLE,
        timerSourceBlockKey = timerSnapshot.activeConfig.sourceBlockKey
    )

    fun resolve(
        currentRoute: String?,
        summary: ActiveWorkoutSummary,
        isTimerActive: Boolean,
        timerSourceBlockKey: String?
    ): LiveDockUiState {
        val isOnLog = currentRoute == BottomDestination.LOG.route
        val isOnTimer = currentRoute == DrawerItem.TIMER.route
        val isWorkoutActive = summary.isActive

        val isTimerLinkedToWorkout = isTimerActive &&
            isWorkoutActive &&
            !timerSourceBlockKey.isNullOrBlank() &&
            (summary.blockKeys.isEmpty() || timerSourceBlockKey in summary.blockKeys)

        val mode = when {
            isOnLog -> LiveDockMode.NONE
            isOnTimer -> if (isWorkoutActive) LiveDockMode.WORKOUT_ONLY else LiveDockMode.NONE
            isWorkoutActive && isTimerActive -> LiveDockMode.WORKOUT_AND_TIMER
            isWorkoutActive -> LiveDockMode.WORKOUT_ONLY
            isTimerActive -> LiveDockMode.TIMER_ONLY
            else -> LiveDockMode.NONE
        }

        val subtitle = formatWorkoutSubtitle(summary)
        val a11yLabel = if (isWorkoutActive) {
            "Workout in progress: ${summary.displayTitle}, $subtitle. Double tap to resume."
        } else {
            ""
        }

        return LiveDockUiState(
            mode = mode,
            workoutTitle = summary.displayTitle,
            workoutSubtitle = subtitle,
            isTimerLinkedToWorkout = isTimerLinkedToWorkout,
            showLogNavBadge = isWorkoutActive && !isOnLog,
            showDrawerActiveBadge = isWorkoutActive,
            showInnerEditorTimerBar = isOnLog && isTimerActive,
            workoutAccessibilityLabel = a11yLabel
        )
    }

    fun formatWorkoutSubtitle(summary: ActiveWorkoutSummary): String {
        if (!summary.isActive) return ""
        val blocks = summary.blockCount
        if (blocks <= 0) return "Draft in progress"
        val blockWord = if (blocks == 1) "block" else "blocks"
        return if (summary.loggedSetCount > 0) {
            val setWord = if (summary.loggedSetCount == 1) "set" else "sets"
            "${summary.loggedBlockCount}/$blocks $blockWord · ${summary.loggedSetCount} $setWord"
        } else {
            "$blocks $blockWord ready"
        }
    }

    /**
     * Determines whether discarding or saving the active Log workout draft should
     * also stop the active timer engine.
     *
     * - On Discard (`isSaveCompletion = false`): stops ONLY timers linked to a block in
     *   this draft; standalone timers (`sourceBlockKey == null`) stay running.
     * - On Save (`isSaveCompletion = true`): clears finished timers or timers linked to
     *   a block in this draft.
     */
    fun shouldStopTimerOnDraftReset(
        timerSnapshot: TimerSnapshot,
        draftBlockKeys: Set<String>,
        isSaveCompletion: Boolean
    ): Boolean {
        if (timerSnapshot.phase == TimerPhase.IDLE) return false
        if (isSaveCompletion && timerSnapshot.phase == TimerPhase.FINISHED) return true
        val sourceKey = timerSnapshot.activeConfig.sourceBlockKey
        if (sourceKey.isNullOrBlank()) return false
        return draftBlockKeys.isEmpty() || sourceKey in draftBlockKeys
    }
}
