package com.fractanomics.crosstraining.ui.timer

import com.fractanomics.crosstraining.data.model.BlockKind
import com.fractanomics.crosstraining.data.model.Exercise
import com.fractanomics.crosstraining.data.model.ExerciseCategory
import com.fractanomics.crosstraining.data.model.MetricType
import com.fractanomics.crosstraining.ui.screens.BlockState
import com.fractanomics.crosstraining.ui.screens.SetState
import com.fractanomics.crosstraining.ui.screens.buildLoadedAvailableBlockState
import com.fractanomics.crosstraining.ui.screens.buildLoadedStandaloneExerciseState
import com.fractanomics.crosstraining.ui.screens.buildSessionTimerBlockEntries
import com.fractanomics.crosstraining.util.AvailableBlock
import com.fractanomics.crosstraining.util.AvailableBlockExercise
import com.fractanomics.crosstraining.util.ExerciseTargetMetric
import com.fractanomics.crosstraining.util.RoutineExerciseTargetSpec
import com.fractanomics.crosstraining.util.TargetMode
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class WorkoutTimerQueueBuilderTest {

    private fun sampleEntries(): List<SessionTimerBlockEntry> = listOf(
        SessionTimerBlockEntry("k1", 0, "Back Squat", 4),
        SessionTimerBlockEntry("k2", 1, "Clean Complex", 3),
        SessionTimerBlockEntry("k3", 2, "AMRAP 12 Metcon", 5),
        SessionTimerBlockEntry("k4", 3, "Rest 90s Core", 2)
    )

    @Test
    fun `buildFromAnchor at first block extrapolates all remaining blocks as E3MOM with 60s transitions`() {
        val base = WorkoutTimerConfig(prepCountdownSeconds = 10, soundEnabled = false, vibrationEnabled = true)
        val built = WorkoutTimerQueueBuilder.buildFromAnchor(sampleEntries(), anchorIndex = 0, baseConfig = base)

        assertNotNull(built)
        assertEquals(0, built!!.anchorIndex)
        assertEquals(TimerMode.EMOM, built.startingConfig.mode)
        assertEquals(180, built.startingConfig.intervalSeconds)
        assertEquals(4, built.startingConfig.totalRounds)
        assertEquals(10, built.startingConfig.prepCountdownSeconds)
        assertEquals(false, built.startingConfig.soundEnabled)
        assertEquals(true, built.startingConfig.vibrationEnabled)
        assertEquals("Back Squat", built.startingConfig.workoutLabel)
        assertEquals("k1", built.startingConfig.sourceBlockKey)

        assertEquals(3, built.queuedStages.size)
        val expectedTitles = listOf("Clean Complex", "AMRAP 12 Metcon", "Rest 90s Core")
        val expectedRounds = listOf(3, 5, 2)
        val expectedKeys = listOf("k2", "k3", "k4")
        built.queuedStages.forEachIndexed { idx, stage ->
            assertEquals(60, stage.transitionSeconds)
            assertEquals(TimerMode.EMOM, stage.config.mode)
            assertEquals(180, stage.config.intervalSeconds)
            assertEquals(expectedRounds[idx], stage.config.totalRounds)
            assertEquals(0, stage.config.prepCountdownSeconds)
            assertEquals(false, stage.config.soundEnabled)
            assertEquals(true, stage.config.vibrationEnabled)
            assertEquals(expectedTitles[idx], stage.config.workoutLabel)
            assertEquals(expectedKeys[idx], stage.config.sourceBlockKey)
        }
        assertEquals(3, built.queuedStages.map { it.id }.distinct().size)

        val expectedTotal = (4 * 180) + (60 + 3 * 180) + (60 + 5 * 180) + (60 + 2 * 180)
        assertEquals(expectedTotal, WorkoutTimerQueueBuilder.calculateQueueTotalSeconds(built.startingConfig, built.queuedStages))
    }

    @Test
    fun `buildFromAnchor at middle block excludes earlier blocks`() {
        val built = WorkoutTimerQueueBuilder.buildFromAnchor(sampleEntries(), anchorIndex = 1)!!
        assertEquals(1, built.anchorIndex)
        assertEquals("Clean Complex", built.startingConfig.workoutLabel)
        assertEquals(3, built.startingConfig.totalRounds)
        assertEquals(listOf("AMRAP 12 Metcon", "Rest 90s Core"), built.queuedStages.map { it.config.workoutLabel })
    }

    @Test
    fun `buildFromAnchor at final block produces empty queue and no trailing transition`() {
        val built = WorkoutTimerQueueBuilder.buildFromAnchor(sampleEntries(), anchorIndex = 3)!!
        assertEquals(3, built.anchorIndex)
        assertEquals("Rest 90s Core", built.startingConfig.workoutLabel)
        assertEquals(2, built.startingConfig.totalRounds)
        assertTrue(built.queuedStages.isEmpty())
        assertEquals(360, WorkoutTimerQueueBuilder.calculateQueueTotalSeconds(built.startingConfig, built.queuedStages))
    }

    @Test
    fun `buildFromAnchor handles empty list, out-of-bounds anchor, and zero set counts safely`() {
        assertNull(WorkoutTimerQueueBuilder.buildFromAnchor(emptyList(), 0))

        val edgeEntries = listOf(
            SessionTimerBlockEntry("a", 0, "  Squat  ", 0),
            SessionTimerBlockEntry("b", 1, "Deadlift", -2)
        )
        val built = WorkoutTimerQueueBuilder.buildFromAnchor(edgeEntries, anchorIndex = 99)!!
        assertEquals(1, built.anchorIndex)
        assertEquals("Deadlift", built.startingConfig.workoutLabel)
        assertEquals(1, built.startingConfig.totalRounds)

        val builtFirst = WorkoutTimerQueueBuilder.buildFromAnchor(edgeEntries, anchorIndex = -5)!!
        assertEquals(0, builtFirst.anchorIndex)
        assertEquals("Squat", builtFirst.startingConfig.workoutLabel)
        assertEquals(1, builtFirst.startingConfig.totalRounds)
        assertEquals(1, builtFirst.queuedStages.single().config.totalRounds)
        assertNotEquals(built.startingConfig.sourceBlockKey, builtFirst.startingConfig.sourceBlockKey)
    }

    @Test
    fun `buildSessionTimerBlockEntries preserves order and set counts across complexes, exercises, and legacy blocks`() {
        val ex1 = Exercise(id = 1L, name = "Snatch", category = ExerciseCategory.BARBELL, metricType = MetricType.WEIGHT)
        val ex2 = Exercise(id = 2L, name = "Back Squat", category = ExerciseCategory.BARBELL, metricType = MetricType.WEIGHT)
        val complex = AvailableBlock(
            id = 10L,
            title = "Oly Complex",
            instructions = "AMRAP 12",
            exercises = listOf(AvailableBlockExercise(1L, "Snatch", true)),
            trackedPrs = emptyList()
        )
        val targetSpec = RoutineExerciseTargetSpec(
            metric = ExerciseTargetMetric.REPS,
            mode = TargetMode.FIXED,
            fixedValue = 3,
            perSetValues = emptyList()
        )
        val complexState = buildLoadedAvailableBlockState(
            block = complex,
            sessions = emptyList(),
            exercises = listOf(ex1),
            weightUnit = "kg",
            routineTargetSetsCount = 5,
            routineExerciseTargets = mapOf(1L to targetSpec)
        )
        val exerciseState = buildLoadedStandaloneExerciseState(
            exercise = ex2,
            note = "Rest 2 min",
            defaultTrackedPrs = emptyList(),
            sessions = emptyList(),
            repMaxes = emptyList(),
            weightUnit = "kg",
            targetSetsCount = 4,
            targetSpec = targetSpec
        )
        val blankLegacy = BlockState()
        val legacyStandalone = BlockState(
            name = "Farmer Carry",
            kind = BlockKind.ACCESSORY,
            sets = listOf(SetState("1"), SetState("1"), SetState("1"))
        )
        val legacySubA = BlockState(
            name = "Pull-up",
            subBlock = "Finisher",
            sets = listOf(SetState("10", group = "1"), SetState("10", group = "2"))
        )

        val entries = buildSessionTimerBlockEntries(
            loadedAvailableBlocks = listOf(complexState, exerciseState),
            legacyBlocks = listOf(blankLegacy, legacyStandalone, legacySubA)
        )

        assertEquals(4, entries.size)
        assertEquals("Oly Complex", entries[0].title)
        assertEquals(5, entries[0].defaultSetCount)
        assertEquals("Back Squat", entries[1].title)
        assertEquals(4, entries[1].defaultSetCount)
        assertEquals("Farmer Carry", entries[2].title)
        assertEquals(3, entries[2].defaultSetCount)
        assertEquals("Finisher", entries[3].title)
        assertEquals(2, entries[3].defaultSetCount)
        assertEquals(listOf(0, 1, 2, 3), entries.map { it.positionIndex })
        assertEquals(4, entries.map { it.blockKey }.distinct().size)
    }
}
