package com.fractanomics.crosstraining.ui.timer

import com.fractanomics.crosstraining.ui.navigation.DrawerItem
import com.fractanomics.crosstraining.ui.navigation.NavigationIntentHandler
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/**
 * Unit & Integration tests for Issue #421:
 * "[Subtask] Notification tap launches/resumes MainActivity into active TimerScreen"
 *
 * Covers Gherkin Acceptance Criteria:
 * - Scenario: App is backgrounded (MainActivity resumes into active TimerScreen)
 * - Scenario: App process was killed (MainActivity launches into active TimerScreen state)
 */
@OptIn(ExperimentalCoroutinesApi::class)
class NotificationTapNavigationTest {

    private val testDispatcher = UnconfinedTestDispatcher()

    @Before
    fun setUp() {
        TimerEngineProvider.setInstanceForTesting(null)
    }

    @Test
    fun `Given notification navigation constants, then intent extras align with Timer destination`() {
        assertEquals("navigate_to", TimerService.EXTRA_NAVIGATE_TO)
        assertEquals("timer", TimerService.DESTINATION_TIMER)
        assertEquals(DrawerItem.TIMER.route, TimerService.DESTINATION_TIMER)
    }

    @Test
    fun `Given app is backgrounded with running timer, when notification tap delivers timer destination, then navigation handler routes to timer and preserves timer state`() = runTest(testDispatcher) {
        // Given: Timer is running in background with active state
        val engine = TimerEngine(context = null, coroutineDispatcher = testDispatcher)
        TimerEngineProvider.setInstanceForTesting(engine)

        engine.configure(
            WorkoutTimerConfig(
                mode = TimerMode.TABATA,
                workSeconds = 20,
                restSeconds = 10,
                totalRounds = 8,
                prepCountdownSeconds = 0
            )
        )
        engine.start()

        val activeSnapshot = engine.snapshot.value
        assertEquals(TimerPhase.WORK, activeSnapshot.phase)
        assertTrue(activeSnapshot.isRunning)
        assertEquals(1, activeSnapshot.currentRound)

        // When: User taps notification body while app is backgrounded (resuming activity with destination extra)
        val handler = NavigationIntentHandler()
        assertNull("Initially pending destination should be null", handler.pendingDestination.value)

        handler.handleDestination(TimerService.DESTINATION_TIMER)

        // Then: Navigation destination is set to TimerScreen
        assertEquals("timer", handler.pendingDestination.value)
        assertEquals(DrawerItem.TIMER.route, handler.pendingDestination.value)

        // And: Active timer engine state remains preserved
        assertEquals(TimerPhase.WORK, engine.snapshot.value.phase)
        assertTrue(engine.snapshot.value.isRunning)
        assertEquals(1, engine.snapshot.value.currentRound)
    }

    @Test
    fun `Given app process was killed, when launch initializes with timer destination, then navigation handler routes directly to TimerScreen`() = runTest(testDispatcher) {
        // Given: Process was killed, cold launch handler initialized with navigation destination
        val handler = NavigationIntentHandler(initialDestination = TimerService.DESTINATION_TIMER)

        // Then: Destination is immediately resolved as timer
        assertEquals(TimerService.DESTINATION_TIMER, handler.pendingDestination.value)
        assertEquals("timer", handler.pendingDestination.value)

        // When: Route is handled by AppNavigation
        handler.onDestinationHandled()

        // Then: Pending destination is cleared
        assertNull(handler.pendingDestination.value)
    }

    @Test
    fun `Given regular app launch without destination, then pendingDestination remains null`() {
        val handler = NavigationIntentHandler()
        assertNull(handler.pendingDestination.value)

        handler.handleDestination(null)
        assertNull(handler.pendingDestination.value)
    }

    @Test
    fun `Given active timer across rounds, when notification tap occurs, active timer snapshot is consistently observed`() = runTest(testDispatcher) {
        val engine = TimerEngine(context = null, coroutineDispatcher = testDispatcher)
        TimerEngineProvider.setInstanceForTesting(engine)

        engine.configure(
            WorkoutTimerConfig(
                mode = TimerMode.EMOM,
                intervalSeconds = 60,
                totalRounds = 5,
                prepCountdownSeconds = 0
            )
        )
        engine.start()
        engine.skipRound() // advance to round 2

        assertEquals(2, engine.snapshot.value.currentRound)
        assertEquals(TimerPhase.WORK, engine.snapshot.value.phase)

        val handler = NavigationIntentHandler()
        handler.handleDestination(TimerService.DESTINATION_TIMER)

        assertEquals("timer", handler.pendingDestination.value)
        assertEquals(2, engine.snapshot.value.currentRound)
        assertTrue(engine.snapshot.value.isRunning)
    }

    @Test
    fun `Given multiple consecutive navigation events, handler transitions and resets correctly`() {
        val handler = NavigationIntentHandler()

        // First notification tap
        handler.handleDestination(TimerService.DESTINATION_TIMER)
        assertEquals(TimerService.DESTINATION_TIMER, handler.pendingDestination.value)

        handler.onDestinationHandled()
        assertNull(handler.pendingDestination.value)

        // Second notification tap
        handler.handleDestination(TimerService.DESTINATION_TIMER)
        assertEquals(TimerService.DESTINATION_TIMER, handler.pendingDestination.value)

        handler.onDestinationHandled()
        assertNull(handler.pendingDestination.value)
    }
}
