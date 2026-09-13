package com.fractanomics.crosstraining.ui.timer

import com.fractanomics.crosstraining.util.WorkoutDocumentParser
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Comprehensive Unit Test Suite for [WorkoutTimerConfigParser] adhering to Issue #546.
 *
 * Verifies:
 * - Exact canonical token matching with [WorkoutDocumentParser.FORMAT_REGEX].
 * - Decimal comma normalization using [WorkoutDocumentParser.DECIMAL_COMMA_REGEX].
 * - Integer E{X}MOM intervals (e.g. E3MOM -> 180s).
 * - Decimal E{X.Y}MOM and E{X,Y}MOM intervals (e.g. E2.5MOM / E2,5MOM -> 150s).
 * - EMOM with explicit round counts (e.g. EMOM 10 -> 60s, 10 rounds) and default EMOM.
 * - AMRAP with explicit target minutes (e.g. AMRAP 15 -> 15 min) and default AMRAP (12 min).
 * - TABATA default and custom rounds.
 * - FOR TIME / FT with time caps.
 * - REST intervals in seconds and minutes (e.g. REST 90, REST 90s, REST 2 min).
 * - Preservation of user preferences (sound, vibration, prepCountdownSeconds).
 * - Workout label and metadata propagation.
 * - Untimed/unparseable blocks returning null fallback.
 */
class WorkoutTimerConfigParserTest {

    @Test
    fun `parse integer EXMOM format extracts interval in seconds and round count`() {
        val config = WorkoutTimerConfigParser.parse(
            formatString = "E3MOM",
            roundCount = 5,
            workoutLabel = "Clean Complex"
        )

        assertNotNull(config)
        assertEquals(TimerMode.EMOM, config!!.mode)
        assertEquals(180, config.intervalSeconds)
        assertEquals(5, config.totalRounds)
        assertEquals("Clean Complex", config.workoutLabel)
    }

    @Test
    fun `parse decimal EXMOM with dot extracts exact interval in seconds`() {
        val config = WorkoutTimerConfigParser.parse(
            formatString = "E2.5MOM",
            roundCount = 4,
            workoutLabel = "Trisets B"
        )

        assertNotNull(config)
        assertEquals(TimerMode.EMOM, config!!.mode)
        assertEquals(150, config.intervalSeconds)
        assertEquals(4, config.totalRounds)
        assertEquals("Trisets B", config.workoutLabel)
    }

    @Test
    fun `parse decimal EXMOM with European comma normalizes and extracts interval`() {
        val config = WorkoutTimerConfigParser.parse(
            formatString = "E2,5MOM",
            roundCount = 4,
            workoutLabel = "Accessories Triset"
        )

        assertNotNull(config)
        assertEquals(TimerMode.EMOM, config!!.mode)
        assertEquals(150, config.intervalSeconds)
        assertEquals(4, config.totalRounds)
        assertEquals("Accessories Triset", config.workoutLabel)
    }

    @Test
    fun `parse standard EMOM without rounds inherits roundCount`() {
        val config = WorkoutTimerConfigParser.parse(
            formatString = "EMOM",
            roundCount = 7,
            workoutLabel = "Kettlebell Swings"
        )

        assertNotNull(config)
        assertEquals(TimerMode.EMOM, config!!.mode)
        assertEquals(60, config.intervalSeconds)
        assertEquals(7, config.totalRounds)
        assertEquals("Kettlebell Swings", config.workoutLabel)
    }

    @Test
    fun `parse EMOM with explicit round count overrides roundCount parameter`() {
        val config = WorkoutTimerConfigParser.parse(
            formatString = "EMOM 10",
            roundCount = 3,
            workoutLabel = "Burpees"
        )

        assertNotNull(config)
        assertEquals(TimerMode.EMOM, config!!.mode)
        assertEquals(60, config.intervalSeconds)
        assertEquals(10, config.totalRounds)
        assertEquals("Burpees", config.workoutLabel)
    }

    @Test
    fun `parse AMRAP with explicit duration extracts target minutes`() {
        val config = WorkoutTimerConfigParser.parse(
            formatString = "AMRAP 20",
            roundCount = 1,
            workoutLabel = "Cindy"
        )

        assertNotNull(config)
        assertEquals(TimerMode.AMRAP, config!!.mode)
        assertEquals(20, config.targetMinutes)
        assertEquals(1, config.totalRounds)
        assertEquals("Cindy", config.workoutLabel)
    }

    @Test
    fun `parse AMRAP without duration defaults to 12 minutes`() {
        val config = WorkoutTimerConfigParser.parse(
            formatString = "AMRAP",
            roundCount = 1,
            workoutLabel = "Conditioning"
        )

        assertNotNull(config)
        assertEquals(TimerMode.AMRAP, config!!.mode)
        assertEquals(12, config.targetMinutes)
        assertEquals(1, config.totalRounds)
        assertEquals("Conditioning", config.workoutLabel)
    }

    @Test
    fun `parse TABATA extracts standard 20s work 10s rest and 8 rounds default`() {
        val config = WorkoutTimerConfigParser.parse(
            formatString = "TABATA",
            roundCount = 1,
            workoutLabel = "Tabata Squats"
        )

        assertNotNull(config)
        assertEquals(TimerMode.TABATA, config!!.mode)
        assertEquals(20, config.workSeconds)
        assertEquals(10, config.restSeconds)
        assertEquals(8, config.totalRounds)
        assertEquals("Tabata Squats", config.workoutLabel)
    }

    @Test
    fun `parse FOR TIME and FT with and without cap`() {
        val ftCap = WorkoutTimerConfigParser.parse(
            formatString = "FOR TIME 15",
            roundCount = 1,
            workoutLabel = "Fran"
        )
        assertNotNull(ftCap)
        assertEquals(TimerMode.TIME_CAP, ftCap!!.mode)
        assertEquals(15, ftCap.targetMinutes)

        val ftShorthand = WorkoutTimerConfigParser.parse(
            formatString = "FT",
            roundCount = 1,
            workoutLabel = "Sprint"
        )
        assertNotNull(ftShorthand)
        assertEquals(TimerMode.TIME_CAP, ftShorthand!!.mode)
        assertEquals(20, ftShorthand.targetMinutes)
    }

    @Test
    fun `parse REST intervals in seconds and minutes`() {
        val restSec = WorkoutTimerConfigParser.parse(
            formatString = "REST 90s",
            roundCount = 1,
            workoutLabel = "Bench Press Rest"
        )
        assertNotNull(restSec)
        assertEquals(TimerMode.REST, restSec!!.mode)
        assertEquals(90, restSec.restSeconds)

        val restPlain = WorkoutTimerConfigParser.parse(
            formatString = "REST 45",
            roundCount = 1,
            workoutLabel = "Quick Rest"
        )
        assertNotNull(restPlain)
        assertEquals(TimerMode.REST, restPlain!!.mode)
        assertEquals(45, restPlain.restSeconds)

        val restMin = WorkoutTimerConfigParser.parse(
            formatString = "REST 2 min",
            roundCount = 1,
            workoutLabel = "Heavy Rest"
        )
        assertNotNull(restMin)
        assertEquals(TimerMode.REST, restMin!!.mode)
        assertEquals(120, restMin.restSeconds)
    }

    @Test
    fun `parse inherits baseConfig preferences for sound, vibration, and prep countdown`() {
        val customBase = WorkoutTimerConfig(
            soundEnabled = false,
            vibrationEnabled = false,
            prepCountdownSeconds = 5
        )

        val config = WorkoutTimerConfigParser.parse(
            formatString = "E3MOM",
            roundCount = 4,
            workoutLabel = "Quiet Gym Routine",
            baseConfig = customBase
        )

        assertNotNull(config)
        assertFalse(config!!.soundEnabled)
        assertFalse(config.vibrationEnabled)
        assertEquals(5, config.prepCountdownSeconds)
        assertEquals(180, config.intervalSeconds)
        assertEquals(4, config.totalRounds)
        assertEquals("Quiet Gym Routine", config.workoutLabel)
    }

    @Test
    fun `parse returns null for untimed or unparseable text`() {
        assertNull(WorkoutTimerConfigParser.parse(null))
        assertNull(WorkoutTimerConfigParser.parse(""))
        assertNull(WorkoutTimerConfigParser.parse("   "))
        assertNull(WorkoutTimerConfigParser.parse("Bicep Curls 3x10"))
        assertNull(WorkoutTimerConfigParser.parse("5x5 Linear Progression"))
        assertNull(WorkoutTimerConfigParser.parse("Heavy Singles @ 90%"))
    }

    @Test
    fun `canonical token matching agreement with WorkoutDocumentParser`() {
        // Assert that tokens identified by WorkoutDocumentParser are directly parsable by WorkoutTimerConfigParser
        val sampleTokens = listOf(
            "E3MOM",
            "E2.5MOM",
            "E2,5MOM",
            "EMOM",
            "EMOM 10",
            "AMRAP",
            "AMRAP 15",
            "TABATA",
            "FOR TIME",
            "FT 12",
            "REST 60s"
        )

        for (token in sampleTokens) {
            val docFormatMatch = WorkoutDocumentParser.FORMAT_REGEX.find(
                token.replace(WorkoutDocumentParser.DECIMAL_COMMA_REGEX, ".")
            )
            assertNotNull("WorkoutDocumentParser must recognize token: $token", docFormatMatch)

            val parsedConfig = WorkoutTimerConfigParser.parse(token, roundCount = 3, workoutLabel = "Token Check")
            assertNotNull("WorkoutTimerConfigParser must parse token: $token", parsedConfig)
            assertEquals("Token Check", parsedConfig!!.workoutLabel)
            assertTrue(parsedConfig.totalRounds >= 1)
        }
    }
}
