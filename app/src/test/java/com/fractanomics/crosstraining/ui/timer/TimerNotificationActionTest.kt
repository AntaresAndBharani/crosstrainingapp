package com.fractanomics.crosstraining.ui.timer

import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/**
 * Unit & Integration tests for Issue #420:
 * [Subtask] Wire Play/Pause and Next action buttons in the timer notification
 *
 * Covers all Gherkin Acceptance Criteria:
 * - Play/Pause toggle reflects running state
 * - Resuming from the notification
 * - Next button advances the round
 */
@OptIn(ExperimentalCoroutinesApi::class)
class TimerNotificationActionTest {

    private val testDispatcher = UnconfinedTestDispatcher()
    private lateinit var timerEngine: TimerEngine
    private lateinit var teardownController: TimerTeardownController
    private lateinit var actionDispatcher: TimerNotificationActionDispatcher

    private var stopForegroundCalled = false
    private var dismissNotificationCalled = false
    private var releaseMediaSessionCalled = false
    private var stopServiceCalled = false
    private var startObservingCalled = false

    @Before
    fun setUp() {
        stopForegroundCalled = false
        dismissNotificationCalled = false
        releaseMediaSessionCalled = false
        stopServiceCalled = false
        startObservingCalled = false

        timerEngine = TimerEngine(context = null, coroutineDispatcher = testDispatcher)
        teardownController = TimerTeardownController(
            onStopForeground = { stopForegroundCalled = true },
            onDismissNotification = { dismissNotificationCalled = true },
            onReleaseMediaSession = { releaseMediaSessionCalled = true },
            onStopService = { stopServiceCalled = true }
        )
        actionDispatcher = TimerNotificationActionDispatcher(
            timerEngine = timerEngine,
            teardownController = teardownController,
            onStartObserving = { startObservingCalled = true }
        )
    }

    // =========================================================================
    // Acceptance Criteria Scenario 1: Play/Pause toggle reflects running state
    // =========================================================================

    @Test
    fun `Scenario 1 - Play Pause toggle reflects running state and invokes pause via ACTION_PAUSE`() = runTest(testDispatcher) {
        // Given the timer notification is visible and the timer is running
        timerEngine.configure(WorkoutTimerConfig(mode = TimerMode.EMOM, intervalSeconds = 60, totalRounds = 5, prepCountdownSeconds = 0))
        actionDispatcher.handleAction(TimerService.ACTION_START)

        val runningSnapshot = timerEngine.snapshot.value
        assertTrue("Timer should be running", runningSnapshot.isRunning)
        val runningNotificationSpec = createTimerNotificationSpec(runningSnapshot)

        // Then the action button shows "Pause"
        assertEquals("Pause", runningNotificationSpec.playPauseAction.title)
        assertEquals(TimerService.ACTION_PAUSE, runningNotificationSpec.playPauseAction.action)
        assertEquals(1, runningNotificationSpec.playPauseAction.requestCode)
        assertEquals(android.R.drawable.ic_media_pause, runningNotificationSpec.playPauseAction.iconRes)
        assertTrue("Notification should be ongoing while running", runningNotificationSpec.isOngoing)

        // When the user taps it (ACTION_PAUSE PendingIntent dispatched)
        actionDispatcher.handleAction(TimerService.ACTION_PAUSE)

        // Then TimerEngine.pause() is invoked
        val pausedSnapshot = timerEngine.snapshot.value
        assertFalse("Timer should be paused", pausedSnapshot.isRunning)
        assertEquals(TimerPhase.WORK, pausedSnapshot.phase)

        // And the button now shows "Play"
        val pausedNotificationSpec = createTimerNotificationSpec(pausedSnapshot)
        assertEquals("Play", pausedNotificationSpec.playPauseAction.title)
        assertEquals(TimerService.ACTION_START, pausedNotificationSpec.playPauseAction.action)
        assertEquals(2, pausedNotificationSpec.playPauseAction.requestCode)
        assertEquals(android.R.drawable.ic_media_play, pausedNotificationSpec.playPauseAction.iconRes)
        assertFalse("Notification should not be ongoing while paused", pausedNotificationSpec.isOngoing)
    }

    // =========================================================================
    // Acceptance Criteria Scenario 2: Resuming from the notification
    // =========================================================================

    @Test
    fun `Scenario 2 - Resuming from the notification invokes start via ACTION_START and updates button to Pause`() = runTest(testDispatcher) {
        // Given the timer is paused and the notification shows "Play"
        timerEngine.configure(WorkoutTimerConfig(mode = TimerMode.EMOM, intervalSeconds = 60, totalRounds = 5, prepCountdownSeconds = 0))
        actionDispatcher.handleAction(TimerService.ACTION_START)
        actionDispatcher.handleAction(TimerService.ACTION_PAUSE)

        val pausedSnapshot = timerEngine.snapshot.value
        assertFalse(pausedSnapshot.isRunning)
        val pausedNotificationSpec = createTimerNotificationSpec(pausedSnapshot)
        assertEquals("Play", pausedNotificationSpec.playPauseAction.title)

        // When the user taps it (ACTION_START PendingIntent dispatched)
        actionDispatcher.handleAction(TimerService.ACTION_START)

        // Then TimerEngine.start() is invoked and the button updates to "Pause"
        val resumedSnapshot = timerEngine.snapshot.value
        assertTrue("Timer should be running again", resumedSnapshot.isRunning)
        assertEquals(TimerPhase.WORK, resumedSnapshot.phase)

        val resumedNotificationSpec = createTimerNotificationSpec(resumedSnapshot)
        assertEquals("Pause", resumedNotificationSpec.playPauseAction.title)
        assertEquals(TimerService.ACTION_PAUSE, resumedNotificationSpec.playPauseAction.action)
        assertTrue(resumedNotificationSpec.isOngoing)
    }

    // =========================================================================
    // Acceptance Criteria Scenario 3: Next button advances the round
    // =========================================================================

    @Test
    fun `Scenario 3 - Next button advances the round via ACTION_NEXT and updates notification title`() = runTest(testDispatcher) {
        // Given the timer notification is visible during an active round
        timerEngine.configure(WorkoutTimerConfig(mode = TimerMode.EMOM, intervalSeconds = 60, totalRounds = 5, prepCountdownSeconds = 0))
        actionDispatcher.handleAction(TimerService.ACTION_START)

        val initialSnapshot = timerEngine.snapshot.value
        assertEquals(1, initialSnapshot.currentRound)
        val initialSpec = createTimerNotificationSpec(initialSnapshot)
        assertEquals("Work - Round 1/5", initialSpec.title)
        assertEquals("Next", initialSpec.nextAction.title)
        assertEquals(TimerService.ACTION_NEXT, initialSpec.nextAction.action)
        assertEquals(3, initialSpec.nextAction.requestCode)
        assertEquals(android.R.drawable.ic_media_next, initialSpec.nextAction.iconRes)

        // When the user taps "Next" (ACTION_NEXT PendingIntent dispatched)
        actionDispatcher.handleAction(TimerService.ACTION_NEXT)

        // Then TimerEngine.skipRound() is invoked and notification updates to reflect the new phase/round
        val nextSnapshot = timerEngine.snapshot.value
        assertEquals(2, nextSnapshot.currentRound)
        assertEquals(TimerPhase.WORK, nextSnapshot.phase)
        assertEquals(60, nextSnapshot.roundSecondsRemaining)
        assertEquals(240, nextSnapshot.totalSecondsRemaining)

        val nextSpec = createTimerNotificationSpec(nextSnapshot)
        assertEquals("Work - Round 2/5", nextSpec.title)
        assertTrue(nextSpec.contentText.contains("Remaining: 01:00"))
        assertTrue(nextSpec.contentText.contains("Total Left: 04:00"))
    }

    // =========================================================================
    // Tabata Mode: Next transitions between WORK and REST
    // =========================================================================

    @Test
    fun `Tabata Mode - Next button advances WORK to REST and REST to next round WORK`() = runTest(testDispatcher) {
        timerEngine.configure(WorkoutTimerConfig(
            mode = TimerMode.TABATA,
            workSeconds = 20,
            restSeconds = 10,
            totalRounds = 3,
            prepCountdownSeconds = 0
        ))
        actionDispatcher.handleAction(TimerService.ACTION_START)

        // Round 1 WORK
        val round1Work = timerEngine.snapshot.value
        assertEquals(1, round1Work.currentRound)
        assertEquals(TimerPhase.WORK, round1Work.phase)
        assertEquals("Work - Round 1/3", createTimerNotificationSpec(round1Work).title)

        // Tap Next -> Round 1 REST
        actionDispatcher.handleAction(TimerService.ACTION_NEXT)
        val round1Rest = timerEngine.snapshot.value
        assertEquals(1, round1Rest.currentRound)
        assertEquals(TimerPhase.REST, round1Rest.phase)
        assertEquals(10, round1Rest.roundSecondsRemaining)
        assertEquals("Rest - Round 1/3", createTimerNotificationSpec(round1Rest).title)

        // Tap Next -> Round 2 WORK
        actionDispatcher.handleAction(TimerService.ACTION_NEXT)
        val round2Work = timerEngine.snapshot.value
        assertEquals(2, round2Work.currentRound)
        assertEquals(TimerPhase.WORK, round2Work.phase)
        assertEquals(20, round2Work.roundSecondsRemaining)
        assertEquals("Work - Round 2/3", createTimerNotificationSpec(round2Work).title)
    }

    // =========================================================================
    // Death By Mode: Next increments target reps
    // =========================================================================

    @Test
    fun `Death By Mode - Next button advances round and increments target reps`() = runTest(testDispatcher) {
        timerEngine.configure(WorkoutTimerConfig(mode = TimerMode.DEATH_BY, totalRounds = 10, prepCountdownSeconds = 0))
        actionDispatcher.handleAction(TimerService.ACTION_START)

        assertEquals(1, timerEngine.snapshot.value.currentRound)
        assertEquals(1, timerEngine.snapshot.value.targetRepsCurrentRound)

        actionDispatcher.handleAction(TimerService.ACTION_NEXT)
        assertEquals(2, timerEngine.snapshot.value.currentRound)
        assertEquals(2, timerEngine.snapshot.value.targetRepsCurrentRound)

        actionDispatcher.handleAction(TimerService.ACTION_NEXT)
        assertEquals(3, timerEngine.snapshot.value.currentRound)
        assertEquals(3, timerEngine.snapshot.value.targetRepsCurrentRound)
    }

    // =========================================================================
    // Next button on Final Round finishes timer and triggers teardown
    // =========================================================================

    @Test
    fun `Next button on final round completes workout and triggers graceful teardown`() = runTest(testDispatcher) {
        timerEngine.configure(WorkoutTimerConfig(mode = TimerMode.EMOM, intervalSeconds = 60, totalRounds = 2, prepCountdownSeconds = 0))
        actionDispatcher.handleAction(TimerService.ACTION_START)

        // Advance to round 2
        actionDispatcher.handleAction(TimerService.ACTION_NEXT)
        assertEquals(2, timerEngine.snapshot.value.currentRound)

        // Advance past round 2 (final round)
        actionDispatcher.handleAction(TimerService.ACTION_NEXT)
        val finishedSnapshot = timerEngine.snapshot.value
        assertEquals(TimerPhase.FINISHED, finishedSnapshot.phase)
        assertFalse(finishedSnapshot.isRunning)

        // Teardown should trigger
        val terminated = teardownController.onSnapshotUpdated(finishedSnapshot)
        assertTrue("Teardown should be triggered on FINISHED", terminated)
        assertTrue(stopForegroundCalled)
        assertTrue(dismissNotificationCalled)
        assertTrue(releaseMediaSessionCalled)
        assertTrue(stopServiceCalled)
    }

    // =========================================================================
    // Prep Phase: Next button skips prep and starts Round 1 WORK immediately
    // =========================================================================

    @Test
    fun `Next button during PREP phase skips countdown and enters Round 1 WORK`() = runTest(testDispatcher) {
        timerEngine.configure(WorkoutTimerConfig(mode = TimerMode.EMOM, intervalSeconds = 60, totalRounds = 5, prepCountdownSeconds = 10))
        actionDispatcher.handleAction(TimerService.ACTION_START)

        val prepSnapshot = timerEngine.snapshot.value
        assertEquals(TimerPhase.PREP, prepSnapshot.phase)
        assertTrue(prepSnapshot.isRunning)

        // Skip prep
        actionDispatcher.handleAction(TimerService.ACTION_NEXT)
        val workSnapshot = timerEngine.snapshot.value
        assertEquals(TimerPhase.WORK, workSnapshot.phase)
        assertEquals(1, workSnapshot.currentRound)
        assertEquals(60, workSnapshot.roundSecondsRemaining)
        assertTrue(workSnapshot.isRunning)
    }

    // =========================================================================
    // Notification Spec Action Structure & MediaStyle Compact View
    // =========================================================================

    @Test
    fun `MediaStyle notification structure includes compact actions for Play-Pause and Next`() {
        val snapshot = TimerSnapshot(
            phase = TimerPhase.WORK,
            isRunning = true,
            currentRound = 3,
            totalRounds = 8,
            roundSecondsRemaining = 45,
            totalSecondsRemaining = 300
        )
        val spec = createTimerNotificationSpec(snapshot)

        // Compact actions are indices 0 (Play/Pause) and 1 (Next)
        assertEquals(2, spec.compactActionIndices.size)
        assertEquals(0, spec.compactActionIndices[0])
        assertEquals(1, spec.compactActionIndices[1])

        // Action specs
        assertEquals("Pause", spec.playPauseAction.title)
        assertEquals(TimerService.ACTION_PAUSE, spec.playPauseAction.action)

        assertEquals("Next", spec.nextAction.title)
        assertEquals(TimerService.ACTION_NEXT, spec.nextAction.action)

        assertEquals("Stop", spec.stopAction.title)
        assertEquals(TimerService.ACTION_STOP, spec.stopAction.action)

        assertEquals("Work - Round 3/8", spec.title)
        assertEquals("Remaining: 00:45 | Total Left: 05:00", spec.contentText)
    }

    // =========================================================================
    // Action Dispatcher: STOP and RESET
    // =========================================================================

    @Test
    fun `Action dispatcher handles ACTION_STOP and ACTION_RESET with teardown`() {
        timerEngine.configure(WorkoutTimerConfig(mode = TimerMode.EMOM, intervalSeconds = 60, totalRounds = 5, prepCountdownSeconds = 0))
        actionDispatcher.handleAction(TimerService.ACTION_START)
        assertTrue(teardownController.isServiceActive)

        actionDispatcher.handleAction(TimerService.ACTION_STOP)
        assertFalse(teardownController.isServiceActive)
        assertEquals(TimerPhase.IDLE, timerEngine.snapshot.value.phase)
        assertTrue(stopServiceCalled)

        // Re-start and test RESET
        actionDispatcher.handleAction(TimerService.ACTION_START)
        assertTrue(teardownController.isServiceActive)

        actionDispatcher.handleAction(TimerService.ACTION_RESET)
        assertFalse(teardownController.isServiceActive)
        assertEquals(TimerPhase.IDLE, timerEngine.snapshot.value.phase)
    }

    // =========================================================================
    // Time Formatter
    // =========================================================================

    @Test
    fun `formatTimerNotificationTime formats seconds to MM SS`() {
        assertEquals("00:00", formatTimerNotificationTime(0))
        assertEquals("00:09", formatTimerNotificationTime(9))
        assertEquals("01:05", formatTimerNotificationTime(65))
        assertEquals("10:00", formatTimerNotificationTime(600))
        assertEquals("65:30", formatTimerNotificationTime(3930))
    }
}
