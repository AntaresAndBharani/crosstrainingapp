package com.fractanomics.crosstraining.ui.timer

import org.junit.Assert.assertEquals
import org.junit.Test

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
}
