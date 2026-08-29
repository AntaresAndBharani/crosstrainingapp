package com.fractanomics.crosstraining.ui.timer

import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/**
 * Unit & Integration tests for Issue #418:
 * "[Subtask] Hoist TimerEngine into a shared, app-scoped StateFlow<TimerSnapshot>"
 *
 * Covers Gherkin Acceptance Criteria:
 * - Scenario: Same engine instance observed by UI and service
 *   Given a workout timer has been started from TimerScreen
 *   When TimerService (or any other component) reads the shared TimerEngine's snapshot
 *   Then it observes the same StateFlow<TimerSnapshot> values as TimerScreen, without duplication or drift
 *
 * - Scenario: Timer survives TimerScreen recomposition/navigation away
 *   Given a workout timer is running
 *   When the user navigates away from TimerScreen and back
 *   Then the timer's state (round, phase, elapsed/remaining seconds) is unchanged and continuous
 *
 * - Scenario: Existing behavior preserved
 *   Given the existing TimerEngineTest suite
 *   Then all existing tests continue to pass after the refactor
 */
@OptIn(ExperimentalCoroutinesApi::class)
class SharedTimerStateTest {

    private val testDispatcher = UnconfinedTestDispatcher()

    @Before
    fun setUp() {
        TimerEngineProvider.setInstanceForTesting(null)
    }

    @Test
    fun `Given TimerEngineProvider, when requested via get or getInstance, then returns consistent shared instance`() {
        val testEngine = TimerEngine(context = null, coroutineDispatcher = testDispatcher)
        TimerEngineProvider.setInstanceForTesting(testEngine)

        val engineFromGet = TimerEngineProvider.get()
        val engineFromGetInstance = TimerEngineProvider.getInstance()

        assertSame("get() must return the hoisted singleton", testEngine, engineFromGet)
        assertSame("getInstance() must return the same singleton as get()", testEngine, engineFromGetInstance)
    }

    @Test
    fun `Given a workout timer has been started from TimerScreen, when TimerService reads the shared snapshot, then it observes the same StateFlow values without drift`() = runTest(testDispatcher) {
        val engine = TimerEngine(context = null, coroutineDispatcher = testDispatcher)
        TimerEngineProvider.setInstanceForTesting(engine)

        // Given: TimerScreen (UI) obtains shared engine and starts a Tabata workout
        val uiEngine = TimerEngineProvider.get()
        uiEngine.configure(
            WorkoutTimerConfig(
                mode = TimerMode.TABATA,
                workSeconds = 20,
                restSeconds = 10,
                totalRounds = 8,
                prepCountdownSeconds = 0
            )
        )
        uiEngine.start()

        // When: TimerService reads the shared TimerEngine instance and snapshot
        val serviceEngine = TimerEngineProvider.get()
        assertSame("UI and Service must share the exact same TimerEngine instance", uiEngine, serviceEngine)

        val uiSnapshot = uiEngine.snapshot.value
        val serviceSnapshot = serviceEngine.snapshot.value

        // Then: Service observes the same StateFlow<TimerSnapshot> values without duplication or drift
        assertEquals("Snapshots must match identically", uiSnapshot, serviceSnapshot)
        assertEquals(TimerPhase.WORK, serviceSnapshot.phase)
        assertTrue(serviceSnapshot.isRunning)
        assertEquals(1, serviceSnapshot.currentRound)
        assertEquals(8, serviceSnapshot.totalRounds)
        assertEquals(20, serviceSnapshot.roundSecondsRemaining)
        assertEquals(240, serviceSnapshot.totalSecondsRemaining)
    }

    @Test
    fun `Given a workout timer is running, when the user navigates away from TimerScreen and back, then timer state is continuous and unchanged`() = runTest(testDispatcher) {
        val engine = TimerEngine(context = null, coroutineDispatcher = testDispatcher)
        TimerEngineProvider.setInstanceForTesting(engine)

        // Given: Workout timer is running in EMOM mode on Round 2
        val initialScreenEngine = TimerEngineProvider.get()
        initialScreenEngine.configure(
            WorkoutTimerConfig(
                mode = TimerMode.EMOM,
                intervalSeconds = 60,
                totalRounds = 5,
                prepCountdownSeconds = 0
            )
        )
        initialScreenEngine.start()
        initialScreenEngine.skipRound() // Advance to Round 2

        val stateBeforeNavigation = initialScreenEngine.snapshot.value
        assertEquals(TimerPhase.WORK, stateBeforeNavigation.phase)
        assertEquals(2, stateBeforeNavigation.currentRound)
        assertTrue(stateBeforeNavigation.isRunning)

        // When: User navigates away from TimerScreen (composable disposed) and later returns
        // The newly entered TimerScreen queries TimerEngineProvider
        val returnedScreenEngine = TimerEngineProvider.get()
        assertSame(initialScreenEngine, returnedScreenEngine)

        // Then: The timer's state (round, phase, elapsed/remaining seconds) is unchanged and continuous
        val stateAfterNavigation = returnedScreenEngine.snapshot.value
        assertEquals(stateBeforeNavigation.phase, stateAfterNavigation.phase)
        assertEquals(stateBeforeNavigation.currentRound, stateAfterNavigation.currentRound)
        assertEquals(stateBeforeNavigation.totalRounds, stateAfterNavigation.totalRounds)
        assertEquals(stateBeforeNavigation.isRunning, stateAfterNavigation.isRunning)
        assertEquals(stateBeforeNavigation.roundSecondsRemaining, stateAfterNavigation.roundSecondsRemaining)
        assertEquals(stateBeforeNavigation.roundSecondsElapsed, stateAfterNavigation.roundSecondsElapsed)
        assertEquals(stateBeforeNavigation.totalSecondsRemaining, stateAfterNavigation.totalSecondsRemaining)
        assertEquals(stateBeforeNavigation.totalSecondsElapsed, stateAfterNavigation.totalSecondsElapsed)
    }

    @Test
    fun `Given timer control actions triggered from external service, when UI observes snapshot, then state transitions are immediately reflected`() = runTest(testDispatcher) {
        val engine = TimerEngine(context = null, coroutineDispatcher = testDispatcher)
        TimerEngineProvider.setInstanceForTesting(engine)

        val uiEngine = TimerEngineProvider.get()
        val serviceEngine = TimerEngineProvider.get()

        uiEngine.configure(
            WorkoutTimerConfig(
                mode = TimerMode.AMRAP,
                targetMinutes = 12,
                prepCountdownSeconds = 0
            )
        )
        uiEngine.start()
        assertTrue(uiEngine.snapshot.value.isRunning)

        // External notification/service pauses timer
        serviceEngine.pause()
        assertFalse("UI StateFlow must immediately observe pause", uiEngine.snapshot.value.isRunning)
        assertEquals(TimerPhase.WORK, uiEngine.snapshot.value.phase)

        // External notification/service resumes timer
        serviceEngine.start()
        assertTrue("UI StateFlow must immediately observe resume", uiEngine.snapshot.value.isRunning)

        // External service resets timer
        serviceEngine.reset()
        assertEquals(TimerPhase.IDLE, uiEngine.snapshot.value.phase)
        assertFalse(uiEngine.snapshot.value.isRunning)
        assertEquals(720, uiEngine.snapshot.value.totalSecondsRemaining) // 12 mins * 60 = 720
    }

    @Test
    fun `Given multiple collectors on shared StateFlow snapshot, all observers receive identical update stream`() = runTest(testDispatcher) {
        val engine = TimerEngine(context = null, coroutineDispatcher = testDispatcher)
        TimerEngineProvider.setInstanceForTesting(engine)

        val uiCollectorEvents = mutableListOf<TimerSnapshot>()
        val serviceCollectorEvents = mutableListOf<TimerSnapshot>()

        val uiJob = launch(testDispatcher) {
            TimerEngineProvider.get().snapshot.collect { uiCollectorEvents.add(it) }
        }
        val serviceJob = launch(testDispatcher) {
            TimerEngineProvider.get().snapshot.collect { serviceCollectorEvents.add(it) }
        }

        engine.configure(WorkoutTimerConfig(mode = TimerMode.DEATH_BY, totalRounds = 5, prepCountdownSeconds = 0))
        engine.start()
        engine.skipRound()
        engine.pause()
        engine.reset()

        assertEquals("UI and Service must collect identical stream of snapshots", uiCollectorEvents, serviceCollectorEvents)
        assertTrue("Collectors must receive snapshot emissions", uiCollectorEvents.size >= 4)

        uiJob.cancel()
        serviceJob.cancel()
    }

    @Test
    fun `Given setInstanceForTesting with null, then provider resets test instance cleanly`() {
        val customEngine = TimerEngine(context = null, coroutineDispatcher = testDispatcher)
        TimerEngineProvider.setInstanceForTesting(customEngine)
        assertSame(customEngine, TimerEngineProvider.get())

        TimerEngineProvider.setInstanceForTesting(null)
        val defaultInstance = TimerEngineProvider.get()
        assertFalse("New default instance must be created after resetting test instance", defaultInstance === customEngine)
    }
}
