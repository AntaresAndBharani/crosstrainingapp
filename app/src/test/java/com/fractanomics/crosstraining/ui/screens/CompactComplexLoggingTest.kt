package com.fractanomics.crosstraining.ui.screens

import com.fractanomics.crosstraining.data.model.BlockKind
import com.fractanomics.crosstraining.data.model.BlockSet
import com.fractanomics.crosstraining.data.model.BlockWithSets
import com.fractanomics.crosstraining.data.model.Exercise
import com.fractanomics.crosstraining.data.model.ExerciseCategory
import com.fractanomics.crosstraining.data.model.MetricType
import com.fractanomics.crosstraining.data.model.RepMax
import com.fractanomics.crosstraining.data.model.Session
import com.fractanomics.crosstraining.data.model.SessionBlock
import com.fractanomics.crosstraining.data.model.SessionWithBlocks
import com.fractanomics.crosstraining.util.AvailableBlock
import com.fractanomics.crosstraining.util.AvailableBlockCodec
import com.fractanomics.crosstraining.util.AvailableBlockExercise
import com.fractanomics.crosstraining.util.ExerciseTargetMetric
import com.fractanomics.crosstraining.util.RoutineExerciseTargetSpec
import com.fractanomics.crosstraining.util.TargetMode
import com.fractanomics.crosstraining.util.TrackedPrSlot
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate

class CompactComplexLoggingTest {

    private val snatch = Exercise(
        id = 1L,
        name = "Snatch",
        category = ExerciseCategory.BARBELL,
        metricType = MetricType.WEIGHT,
        unit = "kg",
        tracksRepMax = true
    )
    private val hangSnatch = Exercise(
        id = 2L,
        name = "Hang Snatch",
        category = ExerciseCategory.BARBELL,
        metricType = MetricType.WEIGHT,
        unit = "kg",
        tracksRepMax = true
    )
    private val snatchBalance = Exercise(
        id = 3L,
        name = "Snatch Balance",
        category = ExerciseCategory.BARBELL,
        metricType = MetricType.WEIGHT,
        unit = "kg",
        tracksRepMax = true
    )
    private val airBike = Exercise(
        id = 4L,
        name = "Air Bike",
        category = ExerciseCategory.MACHINE,
        metricType = MetricType.CALORIES,
        unit = "cal",
        tracksRepMax = false
    )
    private val pullUps = Exercise(
        id = 5L,
        name = "Pull-ups",
        category = ExerciseCategory.GYMNASTICS,
        metricType = MetricType.REPS,
        unit = "reps",
        tracksRepMax = false
    )
    private val allExercises = listOf(snatch, hangSnatch, snatchBalance, airBike, pullUps)

    private fun snatchComplexBlock() = AvailableBlock(
        id = 100L,
        title = "Snatch + Hang Snatch + Snatch Balance",
        instructions = "E4MOM · 1-2-3-1-2-3",
        exercises = listOf(
            AvailableBlockExercise(1L, "Snatch", true),
            AvailableBlockExercise(2L, "Hang Snatch", true),
            AvailableBlockExercise(3L, "Snatch Balance", true)
        ),
        trackedPrs = listOf(
            TrackedPrSlot(1L, "Snatch", 1),
            TrackedPrSlot(2L, "Hang Snatch", 1)
        )
    )

    private fun targets123(): Map<Long, RoutineExerciseTargetSpec> = mapOf(
        1L to RoutineExerciseTargetSpec(
            metric = ExerciseTargetMetric.REPS,
            mode = TargetMode.FIXED,
            fixedValue = 1,
            perSetValues = listOf(1, 1, 1, 1, 1, 1)
        ),
        2L to RoutineExerciseTargetSpec(
            metric = ExerciseTargetMetric.REPS,
            mode = TargetMode.PER_SET,
            fixedValue = 1,
            perSetValues = listOf(0, 1, 1, 0, 1, 1)
        ),
        3L to RoutineExerciseTargetSpec(
            metric = ExerciseTargetMetric.REPS,
            mode = TargetMode.PER_SET,
            fixedValue = 1,
            perSetValues = listOf(0, 0, 1, 0, 0, 1)
        )
    )

    @Test
    fun `1-2-3-1-2-3 routine targets align canonical rounds and pre-fill by groupIndex and fallback ordinal`() {
        val priorDate = LocalDate.of(2026, 11, 14)
        // Session WITH explicit groupIndex sparse rounds
        val priorWithGroup = SessionWithBlocks(
            session = Session(id = 10L, cycleId = 1L, date = priorDate, title = "Oly Complex"),
            blocks = listOf(
                BlockWithSets(
                    block = SessionBlock(
                        id = 101L, sessionId = 10L, position = 0, name = "Snatch",
                        mainExerciseId = 1L, routineId = 100L, subBlock = snatchComplexBlock().title
                    ),
                    sets = listOf(
                        BlockSet(blockId = 101L, position = 0, groupIndex = 1, reps = 1, weight = 40.0),
                        BlockSet(blockId = 101L, position = 1, groupIndex = 2, reps = 1, weight = 42.5),
                        BlockSet(blockId = 101L, position = 2, groupIndex = 3, reps = 1, weight = 45.0),
                        BlockSet(blockId = 101L, position = 3, groupIndex = 4, reps = 1, weight = 47.5),
                        BlockSet(blockId = 101L, position = 4, groupIndex = 5, reps = 1, weight = 47.5),
                        BlockSet(blockId = 101L, position = 5, groupIndex = 6, reps = 1, weight = 50.0)
                    )
                ),
                BlockWithSets(
                    block = SessionBlock(
                        id = 102L, sessionId = 10L, position = 1, name = "Hang Snatch",
                        mainExerciseId = 2L, routineId = 100L, subBlock = snatchComplexBlock().title
                    ),
                    sets = listOf(
                        BlockSet(blockId = 102L, position = 0, groupIndex = 2, reps = 1, weight = 42.5),
                        BlockSet(blockId = 102L, position = 1, groupIndex = 3, reps = 1, weight = 45.0),
                        BlockSet(blockId = 102L, position = 2, groupIndex = 5, reps = 1, weight = 47.5),
                        BlockSet(blockId = 102L, position = 3, groupIndex = 6, reps = 1, weight = 50.0)
                    )
                ),
                BlockWithSets(
                    block = SessionBlock(
                        id = 103L, sessionId = 10L, position = 2, name = "Snatch Balance",
                        mainExerciseId = 3L, routineId = 100L, subBlock = snatchComplexBlock().title
                    ),
                    sets = listOf(
                        BlockSet(blockId = 103L, position = 0, groupIndex = 3, reps = 1, weight = 45.0),
                        BlockSet(blockId = 103L, position = 1, groupIndex = 6, reps = 1, weight = 50.0)
                    )
                )
            )
        )

        val state = buildLoadedAvailableBlockState(
            block = snatchComplexBlock(),
            sessions = listOf(priorWithGroup),
            exercises = allExercises,
            weightUnit = "kg",
            routineTargetSetsCount = 6,
            routineExerciseTargets = targets123()
        )

        assertTrue(state.isCompactComplex)
        assertEquals(6, state.complexSetCount)
        assertTrue(state.hasPreFilledHistory)
        assertNotNull(state.lastSessionDateFormatted)

        assertEquals(listOf(false, false, false, false, false, false), state.exerciseSections[0].skippedFlags.toList())
        assertEquals(listOf(true, false, false, true, false, false), state.exerciseSections[1].skippedFlags.toList())
        assertEquals(listOf(true, true, false, true, true, false), state.exerciseSections[2].skippedFlags.toList())

        val expectedWeights = listOf("40", "42.5", "45", "47.5", "47.5", "50")
        for (i in 0 until 6) {
            val summary = state.resolveSharedSetWeight(i)
            assertEquals(SharedSetWeightStateKind.LINKED, summary.kind)
            assertEquals(expectedWeights[i], summary.uniformWeightText)
        }
        // Skipped rounds remain blank on Hang Snatch and Snatch Balance
        assertEquals("", state.exerciseSections[1].weights[0])
        assertEquals("", state.exerciseSections[1].weights[3])
        assertEquals("", state.exerciseSections[2].weights[0])
        assertEquals("", state.exerciseSections[2].weights[4])

        // Also test 3-1-3-1-3-1 pattern WITHOUT groupIndex (ordinal fallback alignment)
        val priorNoGroup = SessionWithBlocks(
            session = Session(id = 11L, cycleId = 1L, date = priorDate, title = "Legacy Sparse"),
            blocks = listOf(
                BlockWithSets(
                    block = SessionBlock(
                        id = 111L, sessionId = 11L, position = 0, name = "Snatch",
                        mainExerciseId = 1L, routineId = 100L, subBlock = snatchComplexBlock().title
                    ),
                    sets = (0 until 6).map { BlockSet(blockId = 111L, position = it, reps = 1, weight = 40.0 + it * 2.5) }
                ),
                BlockWithSets(
                    block = SessionBlock(
                        id = 112L, sessionId = 11L, position = 1, name = "Hang Snatch",
                        mainExerciseId = 2L, routineId = 100L, subBlock = snatchComplexBlock().title
                    ),
                    // Only 3 logged sets without groupIndex, mapped sequentially to active rounds 1, 3, 5
                    sets = listOf(
                        BlockSet(blockId = 112L, position = 0, reps = 1, weight = 40.0),
                        BlockSet(blockId = 112L, position = 1, reps = 1, weight = 45.0),
                        BlockSet(blockId = 112L, position = 2, reps = 1, weight = 50.0)
                    )
                )
            )
        )
        val targets313 = mapOf(
            1L to RoutineExerciseTargetSpec(ExerciseTargetMetric.REPS, TargetMode.FIXED, 1, List(6) { 1 }),
            2L to RoutineExerciseTargetSpec(ExerciseTargetMetric.REPS, TargetMode.PER_SET, 1, listOf(1, 0, 1, 0, 1, 0)),
            3L to RoutineExerciseTargetSpec(ExerciseTargetMetric.REPS, TargetMode.PER_SET, 1, listOf(1, 0, 1, 0, 1, 0))
        )
        val ordinalState = buildLoadedAvailableBlockState(
            block = snatchComplexBlock(),
            sessions = listOf(priorNoGroup),
            exercises = allExercises,
            weightUnit = "kg",
            routineTargetSetsCount = 6,
            routineExerciseTargets = targets313
        )
        assertEquals(listOf("40", "", "45", "", "50", ""), ordinalState.exerciseSections[1].weights.toList())
    }

    @Test
    fun `updateSharedSetWeight writes only active weighted exercises and keeps trailing decimal linked`() {
        val state = buildLoadedAvailableBlockState(
            block = snatchComplexBlock(),
            sessions = emptyList(),
            exercises = allExercises,
            weightUnit = "kg",
            routineTargetSetsCount = 6,
            routineExerciseTargets = targets123()
        )

        // Set 1 (index 0): only Snatch active -> Hang Snatch & Snatch Balance stay blank
        val returned = state.updateSharedSetWeight(0, "42,")
        assertEquals("42.", returned)
        assertEquals("42.", state.exerciseSections[0].weights[0])
        assertEquals("", state.exerciseSections[1].weights[0])
        assertEquals("", state.exerciseSections[2].weights[0])
        assertEquals(SharedSetWeightStateKind.LINKED, state.resolveSharedSetWeight(0).kind)
        assertEquals("42.", state.resolveSharedSetWeight(0).uniformWeightText)

        // Finish typing Set 3 (index 2) where all 3 are active
        state.updateSharedSetWeight(2, "52,5")
        assertEquals("52.5", state.exerciseSections[0].weights[2])
        assertEquals("52.5", state.exerciseSections[1].weights[2])
        assertEquals("52.5", state.exerciseSections[2].weights[2])
        assertEquals("52.5", state.resolveSharedSetWeight(2).uniformWeightText)
    }

    @Test
    fun `splitting a weight transitions to MIXED and relink with Undo restores mixed weights`() {
        val state = buildLoadedAvailableBlockState(
            block = snatchComplexBlock(),
            sessions = emptyList(),
            exercises = allExercises,
            weightUnit = "kg",
            routineTargetSetsCount = 6,
            routineExerciseTargets = targets123()
        )
        state.updateSharedSetWeight(2, "45")
        assertEquals(SharedSetWeightStateKind.LINKED, state.resolveSharedSetWeight(2).kind)

        // Override Snatch Balance on Set 3 (index 2) to 40 kg
        state.updateSetWeight(state.exerciseSections[2], setIndex = 2, rawValue = "40")
        val mixedSummary = state.resolveSharedSetWeight(2)
        assertEquals(SharedSetWeightStateKind.MIXED, mixedSummary.kind)
        assertEquals(40.0, mixedSummary.minWeight!!, 0.001)
        assertEquals(45.0, mixedSummary.maxWeight!!, 0.001)
        assertEquals("45", mixedSummary.majorityWeightText)
        assertEquals(setOf(2), mixedSummary.divergentExerciseIndices)

        // Re-link all active weighted exercises to 45 and verify Undo restores 45/45/40
        val snapshot = state.relinkComplexSetWeights(2, mixedSummary.majorityWeightText)
        assertNotNull(snapshot)
        assertEquals(SharedSetWeightStateKind.LINKED, state.resolveSharedSetWeight(2).kind)
        assertEquals("45", state.exerciseSections[2].weights[2])

        state.restoreComplexSetWeights(snapshot!!)
        assertEquals(SharedSetWeightStateKind.MIXED, state.resolveSharedSetWeight(2).kind)
        assertEquals("40", state.exerciseSections[2].weights[2])
    }

    @Test
    fun `toggleComplexExerciseSkipped inherits linked weight clears on skip and handles ALL_SKIPPED`() {
        val state = buildLoadedAvailableBlockState(
            block = snatchComplexBlock(),
            sessions = emptyList(),
            exercises = allExercises,
            weightUnit = "kg",
            routineTargetSetsCount = 6,
            routineExerciseTargets = targets123()
        )
        // Set 1 (index 0): Snatch active @ 40 kg, Hang Snatch & Snatch Balance skipped
        state.updateSharedSetWeight(0, "40")
        assertTrue(state.exerciseSections[1].skippedFlags[0])

        // Enable Hang Snatch on Set 1 -> should inherit 40 kg and target 1 rep
        state.toggleComplexExerciseSkipped(state.exerciseSections[1], 0)
        assertFalse(state.exerciseSections[1].skippedFlags[0])
        assertEquals("40", state.exerciseSections[1].weights[0])
        assertEquals("1", state.exerciseSections[1].repsInputs[0])

        // Disable both Snatch & Hang Snatch on Set 1 -> ALL_SKIPPED
        state.toggleComplexExerciseSkipped(state.exerciseSections[0], 0)
        state.toggleComplexExerciseSkipped(state.exerciseSections[1], 0)
        assertEquals(SharedSetWeightStateKind.ALL_SKIPPED, state.resolveSharedSetWeight(0).kind)
        assertEquals("", state.exerciseSections[0].weights[0])
        assertEquals("", state.exerciseSections[1].weights[0])
    }

    @Test
    fun `addComplexSet copies Set N weight and mask while removeComplexSet supports Undo`() {
        val state = buildLoadedAvailableBlockState(
            block = snatchComplexBlock(),
            sessions = emptyList(),
            exercises = allExercises,
            weightUnit = "kg",
            routineTargetSetsCount = 6,
            routineExerciseTargets = targets123()
        )
        state.updateSharedSetWeight(5, "50") // Set 6: all 3 active @ 50 kg
        state.toggleComplexExerciseSkipped(state.exerciseSections[2], 5) // Set 6: skip Snatch Balance

        state.addComplexSet()
        assertEquals(7, state.complexSetCount)
        assertFalse(state.exerciseSections[0].skippedFlags[6])
        assertFalse(state.exerciseSections[1].skippedFlags[6])
        assertTrue(state.exerciseSections[2].skippedFlags[6])
        assertEquals("50", state.exerciseSections[0].weights[6])
        assertEquals("50", state.exerciseSections[1].weights[6])
        assertEquals("", state.exerciseSections[2].weights[6])

        val snapshot = state.removeComplexSet(6)
        assertNotNull(snapshot)
        assertEquals(6, state.complexSetCount)

        state.restoreRemovedComplexSet(snapshot!!)
        assertEquals(7, state.complexSetCount)
        assertEquals("50", state.exerciseSections[1].weights[6])
        assertTrue(state.exerciseSections[2].skippedFlags[6])

        // Cannot remove down below 1 set
        val singleSetState = buildCustomAvailableBlockState(snatchComplexBlock(), exercises = allExercises)
        assertNotNull(singleSetState.removeComplexSet(2))
        assertNotNull(singleSetState.removeComplexSet(1))
        assertEquals(1, singleSetState.complexSetCount)
        assertNull(singleSetState.removeComplexSet(0))
    }

    @Test
    fun `skip-aware PR auto-sync excludes skipped rounds from Hang Snatch PR evaluation`() {
        val state = buildLoadedAvailableBlockState(
            block = snatchComplexBlock(),
            sessions = emptyList(),
            exercises = allExercises,
            weightUnit = "kg",
            routineTargetSetsCount = 6,
            routineExerciseTargets = targets123()
        )
        // Set 4 (index 3): Snatch only @ 52.5 kg (Hang Snatch skipped!)
        // Set 5 (index 4): Snatch + Hang Snatch @ 47.5 kg
        state.updateSharedSetWeight(3, "52.5")
        state.updateSharedSetWeight(4, "47.5")

        val repMaxes = listOf(
            RepMax(id = 1L, exerciseId = 1L, reps = 1, weight = 50.0, date = LocalDate.of(2026, 11, 1), sessionId = 1L),
            RepMax(id = 2L, exerciseId = 2L, reps = 1, weight = 48.0, date = LocalDate.of(2026, 11, 1), sessionId = 1L)
        )
        val chips = state.evaluatePrChips(allExercises, repMaxes).associateBy { it.first.movementId }

        assertEquals(52.5, chips[1L]!!.second.candidateWeight, 0.001)
        assertTrue(chips[1L]!!.second.isNewPr)

        // Hang Snatch was skipped on Set 4, so max active weight for Hang Snatch stays 47.5 (NOT 52.5)
        assertEquals(47.5, chips[2L]!!.second.candidateWeight, 0.001)
        assertFalse(chips[2L]!!.second.isNewPr)
    }

    @Test
    fun `mixed barbell and non-weighted movements isolate shared kg writes`() {
        val mixedBlock = AvailableBlock(
            id = 200L,
            title = "Snatch + Pull-ups + Air Bike",
            instructions = "EMOM 6",
            exercises = listOf(
                AvailableBlockExercise(1L, "Snatch", true),
                AvailableBlockExercise(5L, "Pull-ups", false),
                AvailableBlockExercise(4L, "Air Bike", false)
            ),
            trackedPrs = listOf(TrackedPrSlot(1L, "Snatch", 1))
        )
        val targets = mapOf(
            1L to RoutineExerciseTargetSpec(ExerciseTargetMetric.REPS, TargetMode.PER_SET, 1, listOf(1, 0, 1)),
            5L to RoutineExerciseTargetSpec(ExerciseTargetMetric.REPS, TargetMode.FIXED, 8, listOf(8, 8, 8)),
            4L to RoutineExerciseTargetSpec(ExerciseTargetMetric.CALORIES, TargetMode.FIXED, 12, listOf(12, 12, 12))
        )
        val state = buildLoadedAvailableBlockState(
            block = mixedBlock,
            sessions = emptyList(),
            exercises = allExercises,
            weightUnit = "kg",
            routineTargetSetsCount = 3,
            routineExerciseTargets = targets
        )

        // Round 1 (idx 0): Snatch + Pull-ups + Air Bike active -> shared weight hits only Snatch
        state.updateSharedSetWeight(0, "60")
        assertEquals("60", state.exerciseSections[0].weights[0])
        assertEquals("", state.exerciseSections[1].weights[0])
        assertEquals("", state.exerciseSections[2].weights[0])
        assertEquals(SharedSetWeightStateKind.LINKED, state.resolveSharedSetWeight(0).kind)

        // Round 2 (idx 1): Snatch skipped, Pull-ups + Air Bike active -> NON_WEIGHTED_ONLY
        assertEquals(SharedSetWeightStateKind.NON_WEIGHTED_ONLY, state.resolveSharedSetWeight(1).kind)
    }

    @Test
    fun `zero-migration save toBlockDrafts and Edit Session hydration round-trip sparse and all-skipped states`() {
        val state = buildLoadedAvailableBlockState(
            block = snatchComplexBlock(),
            sessions = emptyList(),
            exercises = allExercises,
            weightUnit = "kg",
            routineTargetSetsCount = 6,
            routineExerciseTargets = targets123()
        )
        state.updateSharedSetWeight(0, "40")
        state.updateSharedSetWeight(1, "42.5")
        state.updateSharedSetWeight(2, "45")
        state.updateSharedSetWeight(3, "47.5")
        state.updateSharedSetWeight(4, "47.5")
        state.updateSharedSetWeight(5, "50")
        // Split Snatch Balance on Set 3 to 40 kg AND skip Snatch Balance on Set 6 (making Set 3 active only = test sparse, or skip all of Snatch Balance to test hasNoLoggedSets)
        state.updateSetWeight(state.exerciseSections[2], 2, "40")
        state.toggleComplexExerciseSkipped(state.exerciseSections[2], 5)
        // Skip ALL rounds of Hang Snatch to verify all-skipped round-trip
        for (r in 0 until 6) {
            if (!state.exerciseSections[1].skippedFlags[r]) {
                state.toggleComplexExerciseSkipped(state.exerciseSections[1], r)
            }
        }

        val drafts = state.toBlockDrafts(allExercises, emptyList())
        assertEquals(3, drafts.size)
        assertEquals(listOf(1, 2, 3, 4, 5, 6), drafts[0].sets.map { it.groupIndex })
        assertTrue(drafts[1].sets.isEmpty()) // All-skipped Hang Snatch
        assertEquals(listOf(3), drafts[2].sets.map { it.groupIndex })
        assertEquals(listOf(40.0), drafts[2].sets.map { it.weight })

        val persistedSession = SessionWithBlocks(
            session = Session(id = 77L, cycleId = 1L, date = LocalDate.of(2026, 11, 21), title = "Complex"),
            blocks = drafts.mapIndexed { bIdx, d ->
                BlockWithSets(
                    block = SessionBlock(
                        id = 700L + bIdx,
                        sessionId = 77L,
                        position = bIdx,
                        name = d.name,
                        kind = d.kind,
                        format = d.format,
                        scheme = d.scheme,
                        mainExerciseId = d.existingExerciseId,
                        routineId = d.routineId,
                        description = d.description,
                        notes = d.notes,
                        exerciseIdsCsv = d.exerciseIdsCsv,
                        subBlock = d.subBlock,
                        isCompleted = d.isCompleted
                    ),
                    sets = d.sets.mapIndexed { sIdx, sd ->
                        BlockSet(
                            id = 8000L + bIdx * 10 + sIdx,
                            blockId = 700L + bIdx,
                            position = sIdx,
                            groupIndex = sd.groupIndex,
                            reps = sd.reps,
                            weight = sd.weight,
                            metricValue = sd.metricValue
                        )
                    }
                )
            }
        )

        val hydration = AvailableBlockCodec.hydrateSessionBlocksForEditor(
            session = persistedSession,
            availableBlocks = listOf(snatchComplexBlock()),
            exercises = allExercises,
            sessionRepMaxes = emptyList()
        )
        assertTrue(hydration.residualLegacyBlocks.isEmpty())
        val hydratedSeed = hydration.simplifiedBlocks.single()
        assertFalse(hydratedSeed.exercises[0].hasNoLoggedSets)
        assertTrue(hydratedSeed.exercises[1].hasNoLoggedSets)
        assertFalse(hydratedSeed.exercises[2].hasNoLoggedSets)

        val rehydrated = buildHydratedBlockState(hydratedSeed, allExercises, editingSessionId = 77L)
        assertTrue(rehydrated.isEditMode)
        assertFalse(rehydrated.hasPreFilledHistory)
        assertNull(rehydrated.lastSessionDateFormatted)
        assertEquals(6, rehydrated.complexSetCount)
        assertEquals(listOf(true, true, true, true, true, true), rehydrated.exerciseSections[1].skippedFlags.toList())
        assertEquals(listOf(true, true, false, true, true, true), rehydrated.exerciseSections[2].skippedFlags.toList())
        assertEquals(SharedSetWeightStateKind.MIXED, rehydrated.resolveSharedSetWeight(2).kind)
        assertEquals("45", rehydrated.exerciseSections[0].weights[2])
        assertEquals("40", rehydrated.exerciseSections[2].weights[2])
    }

    @Test
    fun `pure UI helpers format pills reflow thresholds inputs and TalkBack semantics accurately`() {
        assertEquals("①", circledMovementIndex(1))
        assertEquals("③", circledMovementIndex(3))
        assertEquals("21.", circledMovementIndex(21))

        assertFalse(shouldReflowComplexSetRow(1.3f))
        assertTrue(shouldReflowComplexSetRow(1.4f))
        assertFalse(shouldShowPillExerciseNames(519))
        assertTrue(shouldShowPillExerciseNames(520))

        assertEquals(
            "① 1",
            formatCompactMovementPillText(1, "Snatch", ExerciseTargetMetric.REPS, "1", isSkipped = false, showExerciseName = false)
        )
        assertEquals(
            "② —",
            formatCompactMovementPillText(2, "Hang Snatch", ExerciseTargetMetric.REPS, "1", isSkipped = true, showExerciseName = false)
        )
        assertEquals(
            "② 10c",
            formatCompactMovementPillText(2, "Air Bike", ExerciseTargetMetric.CALORIES, "10", isSkipped = false, showExerciseName = false)
        )
        assertEquals(
            "③ 30s",
            formatCompactMovementPillText(3, "Plank", ExerciseTargetMetric.TIME_SECONDS, "30", isSkipped = false, showExerciseName = false)
        )
        assertEquals(
            "③ 200m",
            formatCompactMovementPillText(3, "Row", ExerciseTargetMetric.METERS, "200", isSkipped = false, showExerciseName = false)
        )
        assertEquals(
            "① Snatch · 1",
            formatCompactMovementPillText(1, "Snatch", ExerciseTargetMetric.REPS, "1", isSkipped = false, showExerciseName = true)
        )

        assertEquals("42.5", sanitizeCompactWeightInput("42,5"))
        assertEquals("42.", sanitizeCompactWeightInput("42,"))
        assertEquals("1234", sanitizeCompactWeightInput("-12abc34"))
        assertEquals("9999", sanitizeCompactWeightInput("12000"))

        val state = buildLoadedAvailableBlockState(
            block = snatchComplexBlock(),
            sessions = emptyList(),
            exercises = allExercises,
            weightUnit = "kg",
            routineTargetSetsCount = 6,
            routineExerciseTargets = targets123()
        )
        val s1Desc = formatCompactSetAccessibilityLabel(
            setNumber = 1,
            blockState = state,
            summary = state.resolveSharedSetWeight(0),
            weightUnit = "kg",
            isExpanded = false
        )
        assertTrue(s1Desc.contains("Set 1: Snatch 1 rep. Hang Snatch skipped, Snatch Balance skipped."))
        assertTrue(s1Desc.contains("Shared weight empty, kilograms."))
        assertTrue(s1Desc.contains("Collapsed. Double-tap to customize individual exercises."))

        state.updateSharedSetWeight(2, "45")
        val s3Desc = formatCompactSetAccessibilityLabel(
            setNumber = 3,
            blockState = state,
            summary = state.resolveSharedSetWeight(2),
            weightUnit = "kg",
            isExpanded = true
        )
        assertTrue(s3Desc.contains("Snatch 1 rep, Hang Snatch 1 rep, Snatch Balance 1 rep."))
        assertTrue(s3Desc.contains("Shared weight 45 kilograms."))
        assertTrue(s3Desc.contains("Expanded. Double-tap to collapse."))
    }
}
