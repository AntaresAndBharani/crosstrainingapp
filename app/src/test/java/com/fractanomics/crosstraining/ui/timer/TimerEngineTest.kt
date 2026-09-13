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

    @Test
    fun `test previousRound on EMOM decrements round and resets interval countdown`() = kotlinx.coroutines.test.runTest {
        val testDispatcher = kotlinx.coroutines.test.UnconfinedTestDispatcher(testScheduler)
        val engine = TimerEngine(context = null, coroutineDispatcher = testDispatcher)

        val config = WorkoutTimerConfig(
            mode = TimerMode.EMOM,
            intervalSeconds = 180,
            totalRounds = 7,
            prepCountdownSeconds = 0,
            workoutLabel = "E3MOM Complex"
        )
        engine.configure(config)
        engine.start()

        // Advance to round 4
        engine.skipRound() // round 2
        engine.skipRound() // round 3
        engine.skipRound() // round 4
        assertEquals(4, engine.snapshot.value.currentRound)

        // Rewind to round 3
        engine.previousRound()
        val snap = engine.snapshot.value
        assertEquals(3, snap.currentRound)
        assertEquals(180, snap.roundSecondsRemaining)
        assertEquals(0, snap.roundSecondsElapsed)
        assertEquals(180, snap.roundTotalSeconds)
        assertEquals((7 - 3 + 1) * 180, snap.totalSecondsRemaining)
        assertEquals(TimerPhase.WORK, snap.phase)
        org.junit.Assert.assertTrue(snap.isRunning)

        engine.stop()
    }

    @Test
    fun `test previousRound on Round 1 is strict no-op`() = kotlinx.coroutines.test.runTest {
        val testDispatcher = kotlinx.coroutines.test.UnconfinedTestDispatcher(testScheduler)
        val engine = TimerEngine(context = null, coroutineDispatcher = testDispatcher)

        val config = WorkoutTimerConfig(
            mode = TimerMode.EMOM,
            intervalSeconds = 60,
            totalRounds = 5,
            prepCountdownSeconds = 0
        )
        engine.configure(config)
        engine.start()

        assertEquals(1, engine.snapshot.value.currentRound)
        val initialSecondsRemaining = engine.snapshot.value.roundSecondsRemaining

        // Calling previousRound at round 1 does nothing
        engine.previousRound()
        assertEquals(1, engine.snapshot.value.currentRound)
        assertEquals(initialSecondsRemaining, engine.snapshot.value.roundSecondsRemaining)
        assertEquals(TimerPhase.WORK, engine.snapshot.value.phase)

        engine.stop()
    }

    @Test
    fun `test previousRound on IDLE or PREP is strict no-op`() = kotlinx.coroutines.test.runTest {
        val testDispatcher = kotlinx.coroutines.test.UnconfinedTestDispatcher(testScheduler)
        val engine = TimerEngine(context = null, coroutineDispatcher = testDispatcher)

        val config = WorkoutTimerConfig(
            mode = TimerMode.EMOM,
            intervalSeconds = 60,
            totalRounds = 5,
            prepCountdownSeconds = 5
        )
        engine.configure(config)
        assertEquals(TimerPhase.IDLE, engine.snapshot.value.phase)

        // On IDLE
        engine.previousRound()
        assertEquals(TimerPhase.IDLE, engine.snapshot.value.phase)
        assertEquals(1, engine.snapshot.value.currentRound)

        // On PREP
        engine.start()
        assertEquals(TimerPhase.PREP, engine.snapshot.value.phase)
        engine.previousRound()
        assertEquals(TimerPhase.PREP, engine.snapshot.value.phase)
        assertEquals(1, engine.snapshot.value.currentRound)

        engine.stop()
    }

    @Test
    fun `test previousRound on Tabata REST reverts to WORK of same round`() = kotlinx.coroutines.test.runTest {
        val testDispatcher = kotlinx.coroutines.test.UnconfinedTestDispatcher(testScheduler)
        val engine = TimerEngine(context = null, coroutineDispatcher = testDispatcher)

        val config = WorkoutTimerConfig(
            mode = TimerMode.TABATA,
            workSeconds = 20,
            restSeconds = 10,
            totalRounds = 8,
            prepCountdownSeconds = 0
        )
        engine.configure(config)
        engine.start()

        // Round 1 WORK -> skip to REST
        engine.skipRound()
        assertEquals(TimerPhase.REST, engine.snapshot.value.phase)
        assertEquals(1, engine.snapshot.value.currentRound)

        // Advance to Round 3 REST
        engine.skipRound() // Round 2 WORK
        engine.skipRound() // Round 2 REST
        engine.skipRound() // Round 3 WORK
        engine.skipRound() // Round 3 REST
        assertEquals(TimerPhase.REST, engine.snapshot.value.phase)
        assertEquals(3, engine.snapshot.value.currentRound)

        // Rewind while in REST: reverts to WORK of Round 3 without prematurely jumping to Round 2
        engine.previousRound()
        val snap = engine.snapshot.value
        assertEquals(TimerPhase.WORK, snap.phase)
        assertEquals(3, snap.currentRound)
        assertEquals(20, snap.roundSecondsRemaining)
        assertEquals(20, snap.roundTotalSeconds)
        assertEquals(0, snap.roundSecondsElapsed)
        assertEquals((8 - 3 + 1) * (20 + 10), snap.totalSecondsRemaining)

        // Rewind while in WORK: decrements to WORK of Round 2
        engine.previousRound()
        val snap2 = engine.snapshot.value
        assertEquals(TimerPhase.WORK, snap2.phase)
        assertEquals(2, snap2.currentRound)
        assertEquals(20, snap2.roundSecondsRemaining)
        assertEquals(20, snap2.roundTotalSeconds)
        assertEquals((8 - 2 + 1) * (20 + 10), snap2.totalSecondsRemaining)

        engine.stop()
    }

    @Test
    fun `test previousRound on Death By decrements round and synchronizes target reps`() = kotlinx.coroutines.test.runTest {
        val testDispatcher = kotlinx.coroutines.test.UnconfinedTestDispatcher(testScheduler)
        val engine = TimerEngine(context = null, coroutineDispatcher = testDispatcher)

        val config = WorkoutTimerConfig(
            mode = TimerMode.DEATH_BY,
            totalRounds = 10,
            prepCountdownSeconds = 0
        )
        engine.configure(config)
        engine.start()

        // Advance to round 4
        engine.skipRound() // 2
        engine.skipRound() // 3
        engine.skipRound() // 4
        assertEquals(4, engine.snapshot.value.currentRound)
        assertEquals(4, engine.snapshot.value.targetRepsCurrentRound)

        // Rewind to round 3
        engine.previousRound()
        val snap = engine.snapshot.value
        assertEquals(3, snap.currentRound)
        assertEquals(3, snap.targetRepsCurrentRound)
        assertEquals(60, snap.roundSecondsRemaining)
        assertEquals(0, snap.roundSecondsElapsed)
        assertEquals((10 - 3 + 1) * 60, snap.totalSecondsRemaining)

        engine.stop()
    }

    @Test
    fun `test previousRound on single-round modes is strict no-op`() = kotlinx.coroutines.test.runTest {
        val testDispatcher = kotlinx.coroutines.test.UnconfinedTestDispatcher(testScheduler)
        val engine = TimerEngine(context = null, coroutineDispatcher = testDispatcher)

        val config = WorkoutTimerConfig(
            mode = TimerMode.AMRAP,
            targetMinutes = 15,
            prepCountdownSeconds = 0
        )
        engine.configure(config)
        engine.start()

        val snapBefore = engine.snapshot.value
        engine.previousRound()
        val snapAfter = engine.snapshot.value

        assertEquals(snapBefore.currentRound, snapAfter.currentRound)
        assertEquals(snapBefore.phase, snapAfter.phase)
        assertEquals(snapBefore.roundSecondsRemaining, snapAfter.roundSecondsRemaining)

        engine.stop()
    }

    @Test
    fun `test previousRound from FINISHED transitions to WORK in paused state and resumes smoothly on start`() = kotlinx.coroutines.test.runTest {
        val testDispatcher = kotlinx.coroutines.test.UnconfinedTestDispatcher(testScheduler)
        val engine = TimerEngine(context = null, coroutineDispatcher = testDispatcher)

        val config = WorkoutTimerConfig(
            mode = TimerMode.EMOM,
            intervalSeconds = 120,
            totalRounds = 3,
            prepCountdownSeconds = 0
        )
        engine.configure(config)
        engine.start()

        // Complete all rounds to FINISHED
        engine.skipRound() // round 2
        engine.skipRound() // round 3
        engine.skipRound() // FINISHED
        assertEquals(TimerPhase.FINISHED, engine.snapshot.value.phase)
        org.junit.Assert.assertFalse(engine.snapshot.value.isRunning)

        // Rewind from FINISHED: transitions to WORK of final round in paused state
        engine.previousRound()
        val snapRewound = engine.snapshot.value
        assertEquals(TimerPhase.WORK, snapRewound.phase)
        org.junit.Assert.assertFalse("Rewind from FINISHED must be in paused state", snapRewound.isRunning)
        assertEquals(3, snapRewound.currentRound)
        assertEquals(120, snapRewound.roundSecondsRemaining)
        assertEquals(120, snapRewound.totalSecondsRemaining)

        // Start resumes ticking
        engine.start()
        org.junit.Assert.assertTrue("start() must resume running the restored round", engine.snapshot.value.isRunning)
        assertEquals(TimerPhase.WORK, engine.snapshot.value.phase)

        engine.stop()
    }
}
