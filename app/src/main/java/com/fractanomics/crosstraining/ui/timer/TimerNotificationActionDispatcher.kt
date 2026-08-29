package com.fractanomics.crosstraining.ui.timer

/**
 * Dispatches notification action intent strings to the corresponding [TimerEngine]
 * and [TimerTeardownController] operations.
 */
class TimerNotificationActionDispatcher(
    private val timerEngine: TimerEngine,
    private val teardownController: TimerTeardownController,
    private val onStartObserving: () -> Unit = {}
) {
    /**
     * Handles an intent action string received by [TimerService].
     */
    fun handleAction(action: String?) {
        when (action) {
            TimerService.ACTION_START -> {
                teardownController.onServiceStarted()
                timerEngine.start()
                onStartObserving()
            }
            TimerService.ACTION_PAUSE -> {
                timerEngine.pause()
                onStartObserving()
            }
            TimerService.ACTION_NEXT -> {
                timerEngine.skipRound()
                onStartObserving()
            }
            TimerService.ACTION_STOP -> {
                teardownController.handleStopAction(timerEngine)
            }
            TimerService.ACTION_RESET -> {
                timerEngine.reset()
                teardownController.performGracefulTeardown()
            }
            else -> {
                if (!teardownController.isServiceActive) {
                    teardownController.onServiceStarted()
                    onStartObserving()
                }
            }
        }
    }
}
