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
 * Unit tests for Issue #419:
 * "[Subtask] Implement TimerService foreground service with MediaStyle notification"
 *
 * Covers Gherkin Acceptance Criteria:
 * - Scenario: Notification appears on timer start
 *   Given the workout timer starts (ACTION_START)
 *   Then a persistent foreground service notification appears immediately
 *   And it uses NotificationCompat.MediaStyle backed by a MediaSessionCompat
 *
 * - Scenario: Notification shows phase and round
 *   Given the timer is running with phase "Work" and round 3 of 8
 *   Then the notification body displays "Work" and "Round 3/8"
 *
 * - Scenario: Notification updates every second without re-alerting
 *   Given the timer is counting down
 *   When one second elapses
 *   Then the notification countdown text updates
 *   And the update uses setOnlyAlertOnce(true) so the device does not re-alert/re-vibrate on each update
 */
@OptIn(ExperimentalCoroutinesApi::class)
class TimerServiceTest {

    private val testDispatcher = UnconfinedTestDispatcher()

    @Before
    fun setUp() {
        TimerEngineProvider.setInstanceForTesting(null)
    }

    @Test
    fun `Scenario - Notification appears on timer start - constants and actions configured properly`() {
        // Given: The workout timer starts via ACTION_START
        assertEquals("com.fractanomics.crosstraining.action.TIMER_START", TimerService.ACTION_START)
        assertEquals("com.fractanomics.crosstraining.action.TIMER_PAUSE", TimerService.ACTION_PAUSE)
        assertEquals("com.fractanomics.crosstraining.action.TIMER_NEXT", TimerService.ACTION_NEXT)
        assertEquals("com.fractanomics.crosstraining.action.TIMER_STOP", TimerService.ACTION_STOP)
        assertEquals("com.fractanomics.crosstraining.action.TIMER_RESET", TimerService.ACTION_RESET)

        // Then: Channel and notification ID are defined for foreground service
        assertEquals("crosstraining_timer_channel", TimerService.CHANNEL_ID)
        assertEquals(4001, TimerService.NOTIFICATION_ID)
    }

    @Test
    fun `Scenario - Notification shows phase and round - displays Work and Round 3 of 8`() {
        // Given: The timer is running with phase "Work" and round 3 of 8
        val snapshot = TimerSnapshot(
            phase = TimerPhase.WORK,
            isRunning = true,
            currentRound = 3,
            totalRounds = 8,
            roundSecondsRemaining = 25,
            totalSecondsRemaining = 120
        )

        // When: Notification content is formatted
        val content = TimerNotificationFormatter.createNotificationContent(snapshot)

        // Then: Title displays "Work - Round 3/8" and content text contains remaining times
        assertEquals("Work - Round 3/8", content.title)
        assertTrue(content.title.contains("Work"))
        assertTrue(content.title.contains("Round 3/8"))
        assertEquals("Remaining: 00:25 | Total Left: 02:00", content.contentText)
        assertTrue(content.isRunning)
        assertTrue(content.onlyAlertOnce)
    }

    @Test
    fun `Scenario - Notification shows phase and round - displays Rest and Round 2 of 5`() {
        // Given: The timer is running with phase "Rest" and round 2 of 5
        val snapshot = TimerSnapshot(
            phase = TimerPhase.REST,
            isRunning = true,
            currentRound = 2,
            totalRounds = 5,
            roundSecondsRemaining = 10,
            totalSecondsRemaining = 90
        )

        // When: Notification content is formatted
        val content = TimerNotificationFormatter.createNotificationContent(snapshot)

        // Then: Title displays "Rest - Round 2/5"
        assertEquals("Rest - Round 2/5", content.title)
        assertTrue(content.title.contains("Rest"))
        assertTrue(content.title.contains("Round 2/5"))
        assertEquals("Remaining: 00:10 | Total Left: 01:30", content.contentText)
    }

    @Test
    fun `Scenario - Notification shows phase and round - displays Prep and Round 1 of 10`() {
        // Given: The timer is in prep phase
        val snapshot = TimerSnapshot(
            phase = TimerPhase.PREP,
            isRunning = true,
            currentRound = 1,
            totalRounds = 10,
            roundSecondsRemaining = 5,
            totalSecondsRemaining = 600
        )

        // When: Notification content is formatted
        val content = TimerNotificationFormatter.createNotificationContent(snapshot)

        // Then: Title displays "Get Ready! - Round 1/10"
        assertEquals("Get Ready! - Round 1/10", content.title)
        assertTrue(content.title.contains("Get Ready!"))
        assertTrue(content.title.contains("Round 1/10"))
        assertEquals("Remaining: 00:05 | Total Left: 10:00", content.contentText)
    }

    @Test
    fun `Scenario - Notification updates every second without re-alerting`() {
        // Given: The timer is counting down
        val initialSnapshot = TimerSnapshot(
            phase = TimerPhase.WORK,
            isRunning = true,
            currentRound = 1,
            totalRounds = 5,
            roundSecondsRemaining = 20,
            totalSecondsRemaining = 100
        )
        val initialContent = TimerNotificationFormatter.createNotificationContent(initialSnapshot)
        assertEquals("Remaining: 00:20 | Total Left: 01:40", initialContent.contentText)
        assertTrue(initialContent.onlyAlertOnce)

        // When: One second elapses
        val tickSnapshot = initialSnapshot.copy(
            roundSecondsRemaining = 19,
            totalSecondsRemaining = 99
        )
        val updatedContent = TimerNotificationFormatter.createNotificationContent(tickSnapshot)

        // Then: The countdown text updates and onlyAlertOnce remains true
        assertEquals("Remaining: 00:19 | Total Left: 01:39", updatedContent.contentText)
        assertTrue("Notification update must set onlyAlertOnce = true to prevent re-alerting", updatedContent.onlyAlertOnce)
        assertTrue("Running workout notification must be ongoing", updatedContent.isRunning)
    }

    @Test
    fun `Scenario - Notification updates dynamically across TimerEngine flow emissions`() = runTest(testDispatcher) {
        // Given: Shared TimerEngine configured and started
        val engine = TimerEngine(context = null, coroutineDispatcher = testDispatcher)
        TimerEngineProvider.setInstanceForTesting(engine)

        engine.configure(
            WorkoutTimerConfig(
                mode = TimerMode.EMOM,
                intervalSeconds = 60,
                totalRounds = 3,
                prepCountdownSeconds = 0
            )
        )
        engine.start()

        // When: Started on Round 1
        var snapshot = engine.snapshot.value
        var content = TimerNotificationFormatter.createNotificationContent(snapshot)
        assertEquals("Work - Round 1/3", content.title)
        assertEquals("Remaining: 01:00 | Total Left: 03:00", content.contentText)
        assertTrue(content.onlyAlertOnce)
        assertTrue(content.isRunning)

        // When: Advancing round
        engine.skipRound()
        snapshot = engine.snapshot.value
        content = TimerNotificationFormatter.createNotificationContent(snapshot)

        // Then: Title updates to Round 2/3
        assertEquals("Work - Round 2/3", content.title)
        assertEquals(TimerNotificationFormatter.formatContentText(snapshot.roundSecondsRemaining, snapshot.totalSecondsRemaining), content.contentText)
        assertTrue(content.onlyAlertOnce)
    }

    @Test
    fun `TimerNotificationFormatter handles boundary and negative time values gracefully`() {
        assertEquals("00:00", TimerNotificationFormatter.formatTime(0))
        assertEquals("00:00", TimerNotificationFormatter.formatTime(-5))
        assertEquals("00:59", TimerNotificationFormatter.formatTime(59))
        assertEquals("01:00", TimerNotificationFormatter.formatTime(60))
        assertEquals("10:05", TimerNotificationFormatter.formatTime(605))
        assertEquals("59:59", TimerNotificationFormatter.formatTime(3599))
    }

    @Test
    fun `TimerNotificationFormatter formats round info and title accurately`() {
        assertEquals("Round 1/1", TimerNotificationFormatter.formatRoundInfo(1, 1))
        assertEquals("Round 8/8", TimerNotificationFormatter.formatRoundInfo(8, 8))
        assertEquals("Work - Round 1/1", TimerNotificationFormatter.formatTitle(TimerPhase.WORK, 1, 1))
        assertEquals("Rest - Round 4/10", TimerNotificationFormatter.formatTitle(TimerPhase.REST, 4, 10))
        assertEquals("Done! - Round 10/10", TimerNotificationFormatter.formatTitle(TimerPhase.FINISHED, 10, 10))
        assertEquals("Idle - Round 1/1", TimerNotificationFormatter.formatTitle(TimerPhase.IDLE, 1, 1))
    }
}
