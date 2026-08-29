package com.fractanomics.crosstraining.ui.screens

import com.fractanomics.crosstraining.ui.components.NumericInputSanitizer
import com.fractanomics.crosstraining.ui.timer.TimerMode
import com.fractanomics.crosstraining.ui.timer.WorkoutTimerConfig
import org.junit.Assert.assertEquals
import org.junit.Test

class TimerScreenNumericMigrationTest {

    @Test
    fun `Scenario 1 - EMOM interval and rounds sanitize, filter, and clamp to domain`() {
        // Given TimerScreen EMOM configuration inputs (intervalSeconds min=1, max=3600; totalRounds min=1, max=999)
        // When typing non-numeric characters or decimals
        val filteredInterval = NumericInputSanitizer.filterInput("60s", allowDecimals = false)
        assertEquals("60", filteredInterval)

        val filteredRounds = NumericInputSanitizer.filterInput("10.5", allowDecimals = false)
        assertEquals("105", filteredRounds)

        // Then leading zero is sanitized
        val sanitizedInterval = NumericInputSanitizer.sanitizeAndClamp("060", minValue = 1.0, maxValue = 3600.0, allowDecimals = false)
        assertEquals("60", sanitizedInterval)

        // Then empty or 0 is clamped to minValue 1
        val clampedEmptyInterval = NumericInputSanitizer.sanitizeAndClamp("", minValue = 1.0, maxValue = 3600.0, allowDecimals = false)
        assertEquals("1", clampedEmptyInterval)

        val clampedZeroRounds = NumericInputSanitizer.sanitizeAndClamp("0", minValue = 1.0, maxValue = 999.0, allowDecimals = false)
        assertEquals("1", clampedZeroRounds)

        // Then values exceeding maxValue are clamped to maxValue
        val clampedExceedingInterval = NumericInputSanitizer.sanitizeAndClamp("5000", minValue = 1.0, maxValue = 3600.0, allowDecimals = false)
        assertEquals("3600", clampedExceedingInterval)

        val clampedExceedingRounds = NumericInputSanitizer.sanitizeAndClamp("1000", minValue = 1.0, maxValue = 999.0, allowDecimals = false)
        assertEquals("999", clampedExceedingRounds)
    }

    @Test
    fun `Scenario 1 - AMRAP and TIME_CAP target minutes clamp to domain`() {
        // Given AMRAP / TIME_CAP targetMinutes (min=1, max=1440)
        val sanitizedMinutes = NumericInputSanitizer.sanitizeAndClamp("012", minValue = 1.0, maxValue = 1440.0, allowDecimals = false)
        assertEquals("12", sanitizedMinutes)

        val clampedZeroMinutes = NumericInputSanitizer.sanitizeAndClamp("0", minValue = 1.0, maxValue = 1440.0, allowDecimals = false)
        assertEquals("1", clampedZeroMinutes)

        val clampedExceedingMinutes = NumericInputSanitizer.sanitizeAndClamp("2000", minValue = 1.0, maxValue = 1440.0, allowDecimals = false)
        assertEquals("1440", clampedExceedingMinutes)
    }

    @Test
    fun `Scenario 1 - DEATH_BY maximum rounds clamp to domain`() {
        // Given DEATH_BY totalRounds (min=1, max=999)
        val sanitizedRounds = NumericInputSanitizer.sanitizeAndClamp("15", minValue = 1.0, maxValue = 999.0, allowDecimals = false)
        assertEquals("15", sanitizedRounds)

        val clampedZeroRounds = NumericInputSanitizer.sanitizeAndClamp("0", minValue = 1.0, maxValue = 999.0, allowDecimals = false)
        assertEquals("1", clampedZeroRounds)

        val clampedMaxRounds = NumericInputSanitizer.sanitizeAndClamp("9999", minValue = 1.0, maxValue = 999.0, allowDecimals = false)
        assertEquals("999", clampedMaxRounds)
    }

    @Test
    fun `Scenario 1 - TABATA work, rest, and rounds clamp to domain`() {
        // Given TABATA workSecs (min=1, max=3600), restSecs (min=0, max=3600), totalRounds (min=1, max=999)
        val sanitizedWork = NumericInputSanitizer.sanitizeAndClamp("20", minValue = 1.0, maxValue = 3600.0, allowDecimals = false)
        assertEquals("20", sanitizedWork)

        val clampedZeroWork = NumericInputSanitizer.sanitizeAndClamp("0", minValue = 1.0, maxValue = 3600.0, allowDecimals = false)
        assertEquals("1", clampedZeroWork)

        // Rest can be 0 seconds in Tabata
        val sanitizedZeroRest = NumericInputSanitizer.sanitizeAndClamp("0", minValue = 0.0, maxValue = 3600.0, allowDecimals = false)
        assertEquals("0", sanitizedZeroRest)

        val sanitizedRounds = NumericInputSanitizer.sanitizeAndClamp("8", minValue = 1.0, maxValue = 999.0, allowDecimals = false)
        assertEquals("8", sanitizedRounds)
    }

    @Test
    fun `Scenario 1 - REST duration clamps to domain`() {
        // Given REST restSecs (min=1, max=3600)
        val sanitizedRest = NumericInputSanitizer.sanitizeAndClamp("90", minValue = 1.0, maxValue = 3600.0, allowDecimals = false)
        assertEquals("90", sanitizedRest)

        val clampedZeroRest = NumericInputSanitizer.sanitizeAndClamp("0", minValue = 1.0, maxValue = 3600.0, allowDecimals = false)
        assertEquals("1", clampedZeroRest)

        val clampedMaxRest = NumericInputSanitizer.sanitizeAndClamp("5000", minValue = 1.0, maxValue = 3600.0, allowDecimals = false)
        assertEquals("3600", clampedMaxRest)
    }

    @Test
    fun `Scenario 2 - No regression in existing timer configuration behavior`() {
        // Given configured timer inputs
        val emomConfig = WorkoutTimerConfig(
            mode = TimerMode.EMOM,
            intervalSeconds = 60,
            totalRounds = 10,
            prepCountdownSeconds = 10,
            soundEnabled = true,
            vibrationEnabled = true
        )
        assertEquals(TimerMode.EMOM, emomConfig.mode)
        assertEquals(60, emomConfig.intervalSeconds)
        assertEquals(10, emomConfig.totalRounds)
        assertEquals(10, emomConfig.prepCountdownSeconds)

        val tabataConfig = WorkoutTimerConfig(
            mode = TimerMode.TABATA,
            workSeconds = 20,
            restSeconds = 10,
            totalRounds = 8,
            prepCountdownSeconds = 5,
            soundEnabled = false,
            vibrationEnabled = true
        )
        assertEquals(TimerMode.TABATA, tabataConfig.mode)
        assertEquals(20, tabataConfig.workSeconds)
        assertEquals(10, tabataConfig.restSeconds)
        assertEquals(8, tabataConfig.totalRounds)
        assertEquals(5, tabataConfig.prepCountdownSeconds)
    }
}
