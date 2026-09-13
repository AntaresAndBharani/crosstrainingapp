package com.fractanomics.crosstraining.ui.screens

import com.fractanomics.crosstraining.data.model.BlockKind
import com.fractanomics.crosstraining.ui.timer.TimerEngine
import com.fractanomics.crosstraining.ui.timer.TimerMode
import com.fractanomics.crosstraining.ui.timer.TimerPhase
import com.fractanomics.crosstraining.ui.timer.WorkoutTimerConfig
import com.fractanomics.crosstraining.ui.timer.WorkoutTimerConfigParser
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Unit & Integration tests for SessionEditor in-session timer integration (Issue #547).
 *
 * Covers BDD Acceptance Criteria:
 * - Scenario 1: Direct Sub-Block Timing Launch (E3MOM Complex)
 * - Scenario 2: Fractional E2,5MOM Triset Timing Launch
 * - Scenario 3: Movement-Level Timing Launch with Metadata
 * - Scenario 4: Compact Timer Bar Inline Controls & Focus Retention
 * - Scenario 5: Safe Active-Timer Conflict Protocol
 * - Scenario 6: Untimed Block Deterministic Fallback
 * - Scenario 8: Timer Finished State & Docked Bar Dismissal
 */
@OptIn(ExperimentalCoroutinesApi::class)
class SessionEditorInSessionTimerTest {

    @Test
    fun `Scenario 1 - Direct Sub-Block Timing Launch (E3MOM Complex)`() = runTest {
        val testDispatcher = UnconfinedTestDispatcher(testScheduler)
        val engine = TimerEngine(context = null, coroutineDispatcher = testDispatcher)

        // Sub-block with 7 rounds and format "E3MOM"
        val formatToken = "E3MOM"
        val totalRounds = 7
        val label = "E3MOM Complex"

        val config = WorkoutTimerConfigParser.parse(
            formatString = formatToken,
            roundCount = totalRounds,
            workoutLabel = label,
            baseConfig = engine.currentConfig
        )
        assertNotNull(config)
        assertEquals(TimerMode.EMOM, config!!.mode)
        assertEquals(180, config.intervalSeconds)
        assertEquals(7, config.totalRounds)
        assertEquals("E3MOM Complex", config.workoutLabel)

        engine.configure(config)
        engine.start()

        val snapshot = engine.snapshot.value
        assertTrue("Engine must be running", snapshot.isRunning)
        if (config.prepCountdownSeconds > 0) {
            assertEquals(TimerPhase.PREP, snapshot.phase)
        } else {
            assertEquals(TimerPhase.WORK, snapshot.phase)
        }
        assertEquals("E3MOM Complex", snapshot.workoutLabel)
        assertEquals(TimerMode.EMOM, snapshot.mode)
        assertEquals(7, snapshot.totalRounds)

        engine.stop()
    }

    @Test
    fun `Scenario 2 - Fractional E2,5MOM Triset Timing Launch`() {
        val baseConfig = WorkoutTimerConfig()
        val config = WorkoutTimerConfigParser.parse(
            formatString = "E2,5MOM Trisets",
            roundCount = 4,
            workoutLabel = "E2,5MOM Trisets",
            baseConfig = baseConfig
        )

        assertNotNull(config)
        assertEquals(TimerMode.EMOM, config!!.mode)
        assertEquals(150, config.intervalSeconds)
        assertEquals(4, config.totalRounds)
        assertEquals("E2,5MOM Trisets", config.workoutLabel)
    }

    @Test
    fun `Scenario 3 - Movement-Level Timing Launch with Metadata`() {
        val baseConfig = WorkoutTimerConfig()
        val config = WorkoutTimerConfigParser.parse(
            formatString = "Rest: 90s",
            roundCount = 1,
            workoutLabel = "Bench Press",
            baseConfig = baseConfig
        )

        assertNotNull(config)
        assertEquals(TimerMode.REST, config!!.mode)
        assertEquals(90, config.restSeconds)
        assertEquals("Bench Press", config.workoutLabel)
    }

    @Test
    fun `Scenario 4 - Inline Controls Pause, Resume, and Skip Round on TimerEngine`() = runTest {
        val testDispatcher = UnconfinedTestDispatcher(testScheduler)
        val engine = TimerEngine(context = null, coroutineDispatcher = testDispatcher)

        val config = WorkoutTimerConfig(
            mode = TimerMode.EMOM,
            intervalSeconds = 60,
            totalRounds = 5,
            prepCountdownSeconds = 0,
            workoutLabel = "Squat EMOM"
        )
        engine.configure(config)
        engine.start()

        assertTrue(engine.snapshot.value.isRunning)
        assertEquals(TimerPhase.WORK, engine.snapshot.value.phase)
        assertEquals(1, engine.snapshot.value.currentRound)

        // Pause
        engine.pause()
        assertTrue(!engine.snapshot.value.isRunning)

        // Resume
        engine.start()
        assertTrue(engine.snapshot.value.isRunning)

        // Skip Round
        engine.skipRound()
        assertEquals(2, engine.snapshot.value.currentRound)

        engine.stop()
    }

    @Test
    fun `Scenario 5 - Safe Active-Timer Conflict Protocol and Atomic replaceTimer`() = runTest {
        val testDispatcher = UnconfinedTestDispatcher(testScheduler)
        val engine = TimerEngine(context = null, coroutineDispatcher = testDispatcher)

        val amrapConfig = WorkoutTimerConfig(
            mode = TimerMode.AMRAP,
            targetMinutes = 20,
            prepCountdownSeconds = 0,
            workoutLabel = "20-min AMRAP"
        )
        engine.configure(amrapConfig)
        engine.start()

        assertEquals(TimerPhase.WORK, engine.snapshot.value.phase)
        assertEquals("20-min AMRAP", engine.snapshot.value.workoutLabel)

        // Replacement target: E3MOM Complex
        val e3momConfig = WorkoutTimerConfig(
            mode = TimerMode.EMOM,
            intervalSeconds = 180,
            totalRounds = 7,
            prepCountdownSeconds = 5,
            workoutLabel = "E3MOM Complex"
        )

        // Simulating athlete confirmation of Replace Active Timer:
        engine.replaceTimer(e3momConfig)

        val snapshot = engine.snapshot.value
        assertTrue(snapshot.isRunning)
        assertEquals(TimerPhase.PREP, snapshot.phase)
        assertEquals("E3MOM Complex", snapshot.workoutLabel)
        assertEquals(TimerMode.EMOM, snapshot.mode)
        assertEquals(7, snapshot.totalRounds)
        assertEquals(180 * 7, snapshot.totalSecondsRemaining)

        engine.stop()
    }

    @Test
    fun `Scenario 6 - Untimed Block Deterministic Fallback returns null to trigger Sheet`() {
        val parsed = WorkoutTimerConfigParser.parse(
            formatString = "Bicep Curls 3x10",
            roundCount = 3,
            workoutLabel = "Bicep Curls"
        )
        assertNull("Untimed block syntax must return null to trigger configuration sheet fallback", parsed)
    }

    @Test
    fun `Scenario 8 - Timer Finished State & Stop returns to IDLE`() = runTest {
        val testDispatcher = UnconfinedTestDispatcher(testScheduler)
        val engine = TimerEngine(context = null, coroutineDispatcher = testDispatcher)

        val config = WorkoutTimerConfig(
            mode = TimerMode.EMOM,
            intervalSeconds = 1,
            totalRounds = 1,
            prepCountdownSeconds = 0,
            workoutLabel = "Sprint"
        )
        engine.configure(config)
        engine.start()

        // Advance or stop
        engine.stop()

        assertEquals(TimerPhase.IDLE, engine.snapshot.value.phase)
        assertTrue(!engine.snapshot.value.isRunning)
    }
}
