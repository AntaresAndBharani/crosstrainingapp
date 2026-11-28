package com.fractanomics.crosstraining.ui.draft

import com.fractanomics.crosstraining.data.model.BlockKind
import com.fractanomics.crosstraining.data.model.MetricType
import com.fractanomics.crosstraining.util.ExerciseTargetMetric
import com.fractanomics.crosstraining.util.TargetMode
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate

class ActiveWorkoutDraftCodecTest {

    @Test
    fun `encode and decode round-trips complete workout draft losslessly`() {
        val date = LocalDate.of(2026, 11, 27)
        val original = ActiveWorkoutDraftSnapshot(
            selectedCycleId = 7L,
            dateEpochDay = date.toEpochDay(),
            title = "Upper \"Heavy\" & Metcon\nBlock A",
            notes = "Felt strong — elbows warm\tPR attempt!",
            showSessionMeta = true,
            loadedRoutineId = 12L,
            loadedRoutineTitle = "Friday Push/Pull",
            initialRoutineSignature = "sig-v1",
            syncRoutineTemplateChecked = true,
            scrollOffsetPx = 412,
            updatedAtEpochMillis = 1_764_300_000_000L,
            loadedBlocks = listOf(
                ActiveLoadedBlockSnapshot(
                    uiKey = "block-uuid-1",
                    itemKind = "BLOCK",
                    blockId = 3L,
                    blockTitle = "Snatch Complex",
                    blockInstructions = "Every 2:30 x 4",
                    blockExercises = listOf(
                        ActiveBlockExerciseSnapshot(101L, "Power Snatch", true),
                        ActiveBlockExerciseSnapshot(102L, "Overhead Squat", true)
                    ),
                    rawFormat = "E2.5MOM",
                    rawScheme = "1+2",
                    rawSection = "Olympic",
                    rawKind = BlockKind.COMPLEX.name,
                    rawIsCompleted = true,
                    lastSessionDateFormatted = "Nov 20",
                    hasPreFilledHistory = true,
                    trackedPrSlots = listOf(ActiveTrackedPrSlotSnapshot(101L, "Power Snatch", 1)),
                    prWeightOverrides = mapOf("id:101:1" to "82.5"),
                    customWeightOverrideFlags = mapOf("id:101:1" to true),
                    explicitlyAppliedPrKeys = mapOf("id:101:1" to true),
                    sourceRoutineItem = ActiveRoutineItemSnapshot(
                        kind = "BLOCK",
                        blockId = 3L,
                        blockTitle = "Snatch Complex",
                        targetSetsCount = 4,
                        exerciseTargets = mapOf(
                            101L to ActiveRoutineTargetSpecSnapshot(
                                metric = ExerciseTargetMetric.REPS.name,
                                mode = TargetMode.FIXED.name,
                                fixedValue = 1,
                                perSetValues = emptyList()
                            )
                        ),
                        exerciseTargetsByName = mapOf(
                            "overhead squat" to ActiveRoutineTargetSpecSnapshot(
                                metric = ExerciseTargetMetric.REPS.name,
                                mode = TargetMode.PER_SET.name,
                                fixedValue = 2,
                                perSetValues = listOf(2, 2, 0, 2)
                            )
                        )
                    ),
                    exerciseSections = listOf(
                        ActiveExerciseSectionSnapshot(
                            exerciseId = 101L,
                            exerciseName = "Power Snatch",
                            lastSessionDateLabel = "Pre-filled from last session (Nov 20)",
                            activeMetric = ExerciseTargetMetric.REPS.name,
                            weights = listOf("75", "80", "82.5"),
                            perSetReps = listOf(1, 1, 1),
                            repsInputs = listOf("1", "1", "1"),
                            metricInputs = listOf("", "", ""),
                            groupIndices = listOf(1, 2, 3),
                            skippedFlags = listOf(false, false, false),
                            defaultReps = 1,
                            libraryMetricType = MetricType.WEIGHT.name
                        ),
                        ActiveExerciseSectionSnapshot(
                            exerciseId = 102L,
                            exerciseName = "Overhead Squat",
                            activeMetric = ExerciseTargetMetric.REPS.name,
                            weights = listOf("75", "80", ""),
                            perSetReps = listOf(2, 2, 2),
                            repsInputs = listOf("2", "2", ""),
                            metricInputs = listOf("", "", ""),
                            groupIndices = listOf(1, 2, 3),
                            skippedFlags = listOf(false, false, true),
                            defaultReps = 2,
                            libraryMetricType = MetricType.WEIGHT.name
                        )
                    )
                ),
                ActiveLoadedBlockSnapshot(
                    uiKey = "ex-uuid-2",
                    itemKind = "EXERCISE",
                    blockId = 0L,
                    blockTitle = "SkiErg",
                    blockInstructions = "Pace 1:50/500m",
                    exerciseSections = listOf(
                        ActiveExerciseSectionSnapshot(
                            exerciseId = 205L,
                            exerciseName = "SkiErg",
                            activeMetric = ExerciseTargetMetric.CALORIES.name,
                            weights = listOf("", ""),
                            perSetReps = listOf(1, 1),
                            repsInputs = listOf("", ""),
                            metricInputs = listOf("20", "22"),
                            groupIndices = listOf(null, null),
                            skippedFlags = listOf(false, false),
                            libraryMetricType = MetricType.CALORIES.name
                        )
                    )
                )
            ),
            legacyBlocks = listOf(
                ActiveLegacyBlockSnapshot(
                    name = "Accessory",
                    kind = BlockKind.ACCESSORY.name,
                    format = "3x12",
                    exerciseId = 301L,
                    recordRm = true,
                    rmReps = "5",
                    rmWeight = "60",
                    isExpanded = true,
                    subBlock = "Superset A",
                    sets = listOf(
                        ActiveLegacySetSnapshot(reps = "12", value = "55", group = "1", isWarmup = true, isFailed = false),
                        ActiveLegacySetSnapshot(reps = "10", value = "60", group = "2", isWarmup = false, isFailed = true)
                    )
                )
            )
        )

        val encoded = ActiveWorkoutDraftCodec.encode(original)
        val decoded = ActiveWorkoutDraftCodec.decode(encoded)

        assertNotNull(decoded)
        assertEquals(original, decoded)
        assertEquals("block-uuid-1", decoded!!.loadedBlocks[0].uiKey)
        assertEquals(412, decoded.scrollOffsetPx)
        assertTrue(decoded.syncRoutineTemplateChecked)
    }

    @Test
    fun `decode returns null on blank corrupt or incompatible schemaVersion payloads`() {
        assertNull(ActiveWorkoutDraftCodec.decode(null))
        assertNull(ActiveWorkoutDraftCodec.decode(""))
        assertNull(ActiveWorkoutDraftCodec.decode("   "))
        assertNull(ActiveWorkoutDraftCodec.decode("{truncated"))
        assertNull(ActiveWorkoutDraftCodec.decode("not-json"))
        assertNull(ActiveWorkoutDraftCodec.decode("""{"schemaVersion":999,"dateEpochDay":20000}"""))
        assertNull(ActiveWorkoutDraftCodec.decode("""{"dateEpochDay":20000}"""))
    }

    @Test
    fun `isMeaningfullyActive and toSummary compute activity, counts, keys and stale flag accurately`() {
        val today = LocalDate.of(2026, 11, 28)
        val blankDraft = ActiveWorkoutDraftSnapshot(
            dateEpochDay = today.toEpochDay(),
            legacyBlocks = listOf(ActiveLegacyBlockSnapshot())
        )
        assertFalse(blankDraft.isMeaningfullyActive())
        assertFalse(blankDraft.toSummary(today).isActive)

        val yesterdayDraft = ActiveWorkoutDraftSnapshot(
            dateEpochDay = today.minusDays(1).toEpochDay(),
            loadedRoutineTitle = "Lower Strength",
            loadedBlocks = listOf(
                ActiveLoadedBlockSnapshot(
                    uiKey = "k1",
                    itemKind = "BLOCK",
                    blockTitle = "Back Squat",
                    exerciseSections = listOf(
                        ActiveExerciseSectionSnapshot(
                            exerciseId = 1L,
                            exerciseName = "Back Squat",
                            weights = listOf("100", "110", ""),
                            repsInputs = listOf("5", "5", ""),
                            metricInputs = listOf("", "", ""),
                            skippedFlags = listOf(false, false, false)
                        )
                    )
                )
            ),
            legacyBlocks = listOf(
                ActiveLegacyBlockSnapshot(
                    name = "Pull-Ups",
                    subBlock = "Finisher",
                    sets = listOf(ActiveLegacySetSnapshot(reps = "10", value = ""))
                )
            )
        )

        assertTrue(yesterdayDraft.isMeaningfullyActive())
        val summary = yesterdayDraft.toSummary(today)
        assertTrue(summary.isActive)
        assertTrue(summary.isStale)
        assertEquals("Lower Strength", summary.displayTitle)
        assertEquals(2, summary.blockCount)
        assertEquals(2, summary.loggedBlockCount)
        assertEquals(3, summary.loggedSetCount)
        assertEquals(setOf("k1", "legacy_sub_0_Finisher"), summary.blockKeys)

        val todaySummary = yesterdayDraft.copy(dateEpochDay = today.toEpochDay()).toSummary(today)
        assertFalse(todaySummary.isStale)
    }
}
