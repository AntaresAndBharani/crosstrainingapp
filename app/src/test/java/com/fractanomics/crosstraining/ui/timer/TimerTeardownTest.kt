package com.fractanomics.crosstraining.ui.timer

import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class TimerTeardownTest {

    private var stopForegroundCalled = false
    private var dismissNotificationCalled = false
    private var releaseMediaSessionCalled = false
    private var stopServiceCalled = false

    private lateinit var controller: TimerTeardownController
    private val testDispatcher = UnconfinedTestDispatcher()

    @Before
    fun setUp() {
        stopForegroundCalled = false
        dismissNotificationCalled = false
        releaseMediaSessionCalled = false
        stopServiceCalled = false

        controller = TimerTeardownController(
            onStopForeground = { stopForegroundCalled = true },
            onDismissNotification = { dismissNotificationCalled = true },
            onReleaseMediaSession = { releaseMediaSessionCalled = true },
            onStopService = { stopServiceCalled = true }
        )
    }

    @Test
    fun `Given timer is running with notification visible, when stopped via ACTION_STOP, then service stops and notification dismissed`() {
        // Given
        val engine = TimerEngine(context = null, coroutineDispatcher = testDispatcher)
        controller.onServiceStarted()
        assertTrue(controller.isServiceActive)

        // When
        controller.handleStopAction(engine)

        // Then
        assertFalse(controller.isServiceActive)
        assertTrue("stopForeground must be called", stopForegroundCalled)
        assertTrue("notification must be dismissed", dismissNotificationCalled)
        assertTrue("media session must be released", releaseMediaSessionCalled)
        assertTrue("service must be stopped", stopServiceCalled)
        assertEquals(TimerPhase.IDLE, engine.snapshot.value.phase)
        assertFalse(engine.snapshot.value.isRunning)
    }

    @Test
    fun `Given timer is running, when TimerEngine reset is called, then service stops and notification is dismissed`() = runTest(testDispatcher) {
        // Given
        val engine = TimerEngine(context = null, coroutineDispatcher = testDispatcher)
        controller.onServiceStarted()

        // Active WORK phase snapshot
        val activeSnapshot = TimerSnapshot(
            phase = TimerPhase.WORK,
            isRunning = true,
            currentRound = 1,
            totalRounds = 5
        )
        val notTerminated = controller.onSnapshotUpdated(activeSnapshot)
        assertFalse("Active workout should not trigger teardown", notTerminated)
        assertTrue(controller.isServiceActive)

        // When
        engine.reset()
        val resetSnapshot = engine.snapshot.value
        val terminated = controller.onSnapshotUpdated(resetSnapshot)

        // Then
        assertTrue("Reset snapshot should trigger teardown", terminated)
        assertFalse(controller.isServiceActive)
        assertTrue("stopForeground must be called", stopForegroundCalled)
        assertTrue("notification must be dismissed", dismissNotificationCalled)
        assertTrue("media session must be released", releaseMediaSessionCalled)
        assertTrue("service must be stopped", stopServiceCalled)
    }

    @Test
    fun `Given timer reaches FINISHED, then service stops, notification is dismissed, and MediaSession is released`() = runTest(testDispatcher) {
        // Given
        controller.onServiceStarted()
        assertTrue(controller.isServiceActive)

        val finishedSnapshot = TimerSnapshot(
            phase = TimerPhase.FINISHED,
            isRunning = false,
            currentRound = 5,
            totalRounds = 5,
            roundSecondsRemaining = 0,
            totalSecondsRemaining = 0
        )

        // When
        val terminated = controller.onSnapshotUpdated(finishedSnapshot)

        // Then
        assertTrue(terminated)
        assertFalse(controller.isServiceActive)
        assertTrue("stopForeground must be called", stopForegroundCalled)
        assertTrue("notification must be dismissed", dismissNotificationCalled)
        assertTrue("media session must be released without leak", releaseMediaSessionCalled)
        assertTrue("service must be stopped", stopServiceCalled)
        assertTrue(controller.isMediaSessionReleased)
        assertTrue(controller.isNotificationDismissed)
    }

    @Test
    fun `Teardown is idempotent and does not throw if called multiple times`() {
        controller.onServiceStarted()
        controller.performGracefulTeardown()

        assertTrue(stopForegroundCalled)
        assertTrue(dismissNotificationCalled)
        assertTrue(releaseMediaSessionCalled)
        assertTrue(stopServiceCalled)

        // Reset tracking flags
        stopForegroundCalled = false
        dismissNotificationCalled = false
        releaseMediaSessionCalled = false
        stopServiceCalled = false

        // Call teardown again
        controller.performGracefulTeardown()

        // Should not call callbacks again
        assertFalse(stopForegroundCalled)
        assertFalse(dismissNotificationCalled)
        assertFalse(releaseMediaSessionCalled)
        assertFalse(stopServiceCalled)
    }

    @Test
    fun `Active phases PREP, WORK, REST do not trigger teardown`() {
        controller.onServiceStarted()

        val prepSnapshot = TimerSnapshot(phase = TimerPhase.PREP, isRunning = true)
        val workSnapshot = TimerSnapshot(phase = TimerPhase.WORK, isRunning = true)
        val restSnapshot = TimerSnapshot(phase = TimerPhase.REST, isRunning = true)

        assertFalse(controller.onSnapshotUpdated(prepSnapshot))
        assertFalse(controller.onSnapshotUpdated(workSnapshot))
        assertFalse(controller.onSnapshotUpdated(restSnapshot))
        assertTrue(controller.isServiceActive)
    }

    @Test
    fun `TimerEngine stop sets snapshot to IDLE and stops running`() {
        val engine = TimerEngine(context = null, coroutineDispatcher = testDispatcher)
        engine.configure(WorkoutTimerConfig(mode = TimerMode.EMOM, intervalSeconds = 30, totalRounds = 3))
        engine.stop()

        val snapshot = engine.snapshot.value
        assertEquals(TimerPhase.IDLE, snapshot.phase)
        assertFalse(snapshot.isRunning)
        assertEquals(30, snapshot.roundSecondsRemaining)
        assertEquals(90, snapshot.totalSecondsRemaining)
    }

    @Test
    fun `TimerEngineProvider returns consistent shared instance`() {
        val customEngine = TimerEngine(context = null, coroutineDispatcher = testDispatcher)
        TimerEngineProvider.setInstanceForTesting(customEngine)

        // Reset provider
        TimerEngineProvider.setInstanceForTesting(null)
    }

    @Test
    fun `Transition from PREP to IDLE on reset triggers teardown`() {
        controller.onServiceStarted()
        val prepSnapshot = TimerSnapshot(phase = TimerPhase.PREP, isRunning = true)
        assertFalse(controller.onSnapshotUpdated(prepSnapshot))

        val idleSnapshot = TimerSnapshot(phase = TimerPhase.IDLE, isRunning = false)
        assertTrue(controller.onSnapshotUpdated(idleSnapshot))
        assertFalse(controller.isServiceActive)
        assertTrue(stopForegroundCalled)
        assertTrue(dismissNotificationCalled)
        assertTrue(releaseMediaSessionCalled)
        assertTrue(stopServiceCalled)
    }

    @Test
    fun `Transition from REST to FINISHED triggers teardown and releases MediaSession`() {
        controller.onServiceStarted()
        val restSnapshot = TimerSnapshot(phase = TimerPhase.REST, isRunning = true)
        assertFalse(controller.onSnapshotUpdated(restSnapshot))

        val finishedSnapshot = TimerSnapshot(phase = TimerPhase.FINISHED, isRunning = false)
        assertTrue(controller.onSnapshotUpdated(finishedSnapshot))
        assertFalse(controller.isServiceActive)
        assertTrue(stopForegroundCalled)
        assertTrue(dismissNotificationCalled)
        assertTrue(releaseMediaSessionCalled)
        assertTrue(stopServiceCalled)
        assertTrue(controller.isMediaSessionReleased)
    }

    @Test
    fun `Restarting service resets lifecycle flags and allows teardown sequence again`() {
        // Initial run & teardown
        controller.onServiceStarted()
        controller.performGracefulTeardown()
        assertTrue(controller.isMediaSessionReleased)
        assertTrue(controller.isNotificationDismissed)

        // Re-start
        controller.onServiceStarted()
        assertTrue(controller.isServiceActive)
        assertFalse(controller.isMediaSessionReleased)
        assertFalse(controller.isNotificationDismissed)

        // Second teardown
        stopForegroundCalled = false
        dismissNotificationCalled = false
        releaseMediaSessionCalled = false
        stopServiceCalled = false

        val finishedSnapshot = TimerSnapshot(phase = TimerPhase.FINISHED, isRunning = false)
        assertTrue(controller.onSnapshotUpdated(finishedSnapshot))
        assertFalse(controller.isServiceActive)
        assertTrue(stopForegroundCalled)
        assertTrue(dismissNotificationCalled)
        assertTrue(releaseMediaSessionCalled)
        assertTrue(stopServiceCalled)
    }

    @Test
    fun `TimerService intent actions and channel constants are properly configured`() {
        assertEquals("crosstraining_timer_channel", TimerService.CHANNEL_ID)
        assertEquals(4001, TimerService.NOTIFICATION_ID)
        assertEquals("com.fractanomics.crosstraining.action.TIMER_START", TimerService.ACTION_START)
        assertEquals("com.fractanomics.crosstraining.action.TIMER_PAUSE", TimerService.ACTION_PAUSE)
        assertEquals("com.fractanomics.crosstraining.action.TIMER_NEXT", TimerService.ACTION_NEXT)
        assertEquals("com.fractanomics.crosstraining.action.TIMER_STOP", TimerService.ACTION_STOP)
        assertEquals("com.fractanomics.crosstraining.action.TIMER_RESET", TimerService.ACTION_RESET)
        assertEquals("navigate_to", TimerService.EXTRA_NAVIGATE_TO)
        assertEquals("timer", TimerService.DESTINATION_TIMER)
    }
}
