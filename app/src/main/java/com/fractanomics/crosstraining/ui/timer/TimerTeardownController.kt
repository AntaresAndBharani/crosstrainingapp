package com.fractanomics.crosstraining.ui.timer

/**
 * Controller responsible for orchestrating the graceful teardown of [TimerService],
 * dismissing notifications, and releasing media resources ([android.support.v4.media.session.MediaSessionCompat])
 * without memory or notification leaks when the timer stops, resets, or finishes.
 */
class TimerTeardownController(
    private val onStopForeground: (removeNotification: Boolean) -> Unit,
    private val onDismissNotification: () -> Unit,
    private val onReleaseMediaSession: () -> Unit,
    private val onStopService: () -> Unit
) {
    var isServiceActive: Boolean = false
        private set

    var isMediaSessionReleased: Boolean = false
        private set

    var isNotificationDismissed: Boolean = false
        private set

    fun onServiceStarted() {
        isServiceActive = true
        isMediaSessionReleased = false
        isNotificationDismissed = false
    }

    /**
     * Evaluates a [TimerSnapshot] and triggers graceful teardown when the timer is no longer actively running
     * (i.e. has finished or reset to idle).
     *
     * @return `true` if teardown was triggered, `false` otherwise.
     */
    fun onSnapshotUpdated(snapshot: TimerSnapshot): Boolean {
        if (!isServiceActive) return false

        return when (snapshot.phase) {
            TimerPhase.IDLE, TimerPhase.FINISHED -> {
                performGracefulTeardown()
                true
            }
            TimerPhase.PREP, TimerPhase.WORK, TimerPhase.REST -> {
                false
            }
        }
    }

    /**
     * Handles ACTION_STOP command by stopping the timer engine and executing graceful teardown.
     */
    fun handleStopAction(timerEngine: TimerEngine) {
        timerEngine.stop()
        performGracefulTeardown()
    }

    /**
     * Executes the complete graceful teardown lifecycle:
     * 1. Stops foreground state and removes notification.
     * 2. Dismisses the notification from the system notification manager.
     * 3. Releases MediaSessionCompat to avoid resource leaks.
     * 4. Stops the foreground service.
     */
    fun performGracefulTeardown() {
        if (!isServiceActive && isMediaSessionReleased && isNotificationDismissed) return

        isServiceActive = false
        onStopForeground(true)
        onDismissNotification()
        isNotificationDismissed = true
        onReleaseMediaSession()
        isMediaSessionReleased = true
        onStopService()
    }
}
