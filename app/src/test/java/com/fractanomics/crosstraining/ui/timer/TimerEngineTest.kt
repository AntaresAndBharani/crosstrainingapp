package com.fractanomics.crosstraining.ui.timer

import kotlinx.coroutines.ExperimentalCoroutinesApi
import org.junit.Assert.assertEquals
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class TimerEngineTest {

    @Test
    fun `test WorkoutTimerConfig EMOM defaults`() {
        val config = WorkoutTimerConfig(
            mode = TimerMode.EMOM,
            intervalSeconds = 60,
            totalRounds = 10
        )

        assertEquals(TimerMode.EMOM, config.mode)
        assertEquals(60, config.intervalSeconds)
        assertEquals(10, config.totalRounds)
    }

    @Test
    fun `test WorkoutTimerConfig Tabata defaults`() {
        val config = WorkoutTimerConfig(
            mode = TimerMode.TABATA,
            workSeconds = 20,
            restSeconds = 10,
            totalRounds = 8
        )

        assertEquals(TimerMode.TABATA, config.mode)
        assertEquals(20, config.workSeconds)
        assertEquals(10, config.restSeconds)
        assertEquals(8, config.totalRounds)
    }

    @Test
    fun `test WorkoutTimerConfig Death By defaults`() {
        val config = WorkoutTimerConfig(
            mode = TimerMode.DEATH_BY,
            totalRounds = 15
        )

        assertEquals(TimerMode.DEATH_BY, config.mode)
        assertEquals(15, config.totalRounds)
    }

    @Test
    fun `test TimerEngine exposes currentConfig and updates on configure`() {
        val engine = TimerEngine(context = null)
        assertEquals(TimerMode.EMOM, engine.currentConfig.mode)

        val newConfig = WorkoutTimerConfig(
            mode = TimerMode.TABATA,
            workoutLabel = "Tabata Core",
            soundEnabled = false
        )
        engine.configure(newConfig)

        assertEquals(newConfig, engine.currentConfig)
        assertEquals("Tabata Core", engine.snapshot.value.workoutLabel)
        assertEquals(TimerMode.TABATA, engine.snapshot.value.mode)
    }

    @Test
    fun `test TimerEngine replaceTimer immediately transitions without transient IDLE`() = kotlinx.coroutines.test.runTest {
        val testDispatcher = kotlinx.coroutines.test.UnconfinedTestDispatcher(testScheduler)
        val engine = TimerEngine(context = null, coroutineDispatcher = testDispatcher)

        val initialConfig = WorkoutTimerConfig(
            mode = TimerMode.AMRAP,
            targetMinutes = 20,
            prepCountdownSeconds = 0,
            workoutLabel = "Initial AMRAP"
        )
        engine.configure(initialConfig)
        engine.start()

        org.junit.Assert.assertTrue(engine.snapshot.value.isRunning)
        assertEquals(TimerPhase.WORK, engine.snapshot.value.phase)
        assertEquals("Initial AMRAP", engine.snapshot.value.workoutLabel)
        assertEquals(TimerMode.AMRAP, engine.snapshot.value.mode)

        // Replace active timer atomically
        val replacementConfig = WorkoutTimerConfig(
            mode = TimerMode.EMOM,
            intervalSeconds = 180,
            totalRounds = 5,
            prepCountdownSeconds = 5,
            workoutLabel = "E3MOM Complex"
        )
        engine.replaceTimer(replacementConfig)

        val snapshot = engine.snapshot.value
        org.junit.Assert.assertTrue("Snapshot must be running after replaceTimer", snapshot.isRunning)
        assertEquals("Snapshot phase must be PREP when prepCountdownSeconds > 0", TimerPhase.PREP, snapshot.phase)
        assertEquals("E3MOM Complex", snapshot.workoutLabel)
        assertEquals(TimerMode.EMOM, snapshot.mode)
        assertEquals(5, snapshot.totalRounds)
        assertEquals(180 * 5, snapshot.totalSecondsRemaining)
        assertEquals(replacementConfig, engine.currentConfig)
        engine.stop()
    }
}
