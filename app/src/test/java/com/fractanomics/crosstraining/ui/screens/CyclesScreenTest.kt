package com.fractanomics.crosstraining.ui.screens

import com.fractanomics.crosstraining.data.analytics.FatLossAnalytics
import com.fractanomics.crosstraining.data.model.BlockSet
import com.fractanomics.crosstraining.data.model.BlockWithSets
import com.fractanomics.crosstraining.data.model.Cycle
import com.fractanomics.crosstraining.data.model.CycleGoal
import com.fractanomics.crosstraining.data.model.CycleType
import com.fractanomics.crosstraining.data.model.Exercise
import com.fractanomics.crosstraining.data.model.ExerciseCategory
import com.fractanomics.crosstraining.data.model.MetricType
import com.fractanomics.crosstraining.data.model.RepMax
import com.fractanomics.crosstraining.data.model.Session
import com.fractanomics.crosstraining.data.model.SessionBlock
import com.fractanomics.crosstraining.data.model.SessionWithBlocks
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate

/**
 * Unit tests verifying CyclesScreen Goal Dialog RM Auto-Fill, Modifier Chips,
 * Adaptive Goal Metrics (Weight, Calories, Reps), Time Windows, and Backward-Compatible
 * Progress Evaluation.
 */
class CyclesScreenTest {

    private val backSquat = Exercise(
        id = 1L,
        name = "Back Squat",
        unit = "kg"
    )
    private val benchPress = Exercise(
        id = 2L,
        name = "Bench Press",
        unit = "kg"
    )
    private val airBike = Exercise(
        id = 3L,
        name = "Air Bike",
        metricType = MetricType.CALORIES,
        unit = "cal",
        tracksRepMax = false
    )
    private val pullUps = Exercise(
        id = 4L,
        name = "Pull-ups",
        category = ExerciseCategory.GYMNASTICS,
        metricType = MetricType.WEIGHT,
        unit = "kg",
        tracksRepMax = false
    )

    // =========================================================================
    // Scenario 1: RM Auto-Fill on Exercise Selection
    // =========================================================================

    @Test
    fun scenario1_rmAutoFillOnExerciseSelection_populatesStartWeightAndTargetWeight() {
        // Given an exercise "Back Squat" with a 1RM of 100.0 kg in RepMaxDao
        val rmLookup: Map<Pair<Long, Int>, Double> = mapOf((backSquat.id to 1) to 100.0)

        // When "Back Squat" is selected with Reps = 1 in the cycle goal dialog
        val initialDraft = GoalDraftState(exercise = null, reps = "1")
        val bestRm = rmLookup[backSquat.id to 1]
        val updatedDraft = initialDraft.onExerciseChanged(backSquat, bestRm)

        // Then CycleGoal.startWeight is populated with 100.0 kg and targetWeight displays 100.0 kg
        assertEquals("100", updatedDraft.startWeight)
        assertEquals("100", updatedDraft.targetWeight)
        assertTrue(updatedDraft.hasStartWeight)

        // Verify conversion to CycleGoal entity fixes startWeight = 0.0 defect
        val goalEntity = updatedDraft.toCycleGoal(cycleId = 42L)
        assertNotNull(goalEntity)
        assertEquals(42L, goalEntity!!.cycleId)
        assertEquals(backSquat.id, goalEntity.exerciseId)
        assertEquals(1, goalEntity.targetReps)
        assertEquals(100.0, goalEntity.startWeight, 0.001)
        assertEquals(100.0, goalEntity.targetWeight, 0.001)
    }

    // =========================================================================
    // Scenario 2: Reps Change Re-Fetches RM Without Interpolation
    // =========================================================================

    @Test
    fun scenario2_repsChangeReFetchesRmWithoutInterpolation_andClearsWhenNoRecord() {
        // Given "Back Squat" has a 1RM of 100.0 kg and a 5RM of 85.0 kg
        val rmLookup: Map<Pair<Long, Int>, Double> = mapOf(
            (backSquat.id to 1) to 100.0,
            (backSquat.id to 5) to 85.0
        )

        var draft = GoalDraftState(exercise = backSquat, reps = "1")
            .onExerciseChanged(backSquat, rmLookup[backSquat.id to 1])
        assertEquals("100", draft.startWeight)
        assertEquals("100", draft.targetWeight)

        // When Reps is changed from 1 to 5
        val fiveRm = rmLookup[backSquat.id to 5]
        draft = draft.onRepsChanged("5", fiveRm)

        // Then CycleGoal.startWeight updates to 85.0 kg and targetWeight updates to 85.0 kg
        assertEquals("85", draft.startWeight)
        assertEquals("85", draft.targetWeight)
        assertEquals(85.0, draft.toCycleGoal(1L)!!.startWeight, 0.001)
        assertEquals(85.0, draft.toCycleGoal(1L)!!.targetWeight, 0.001)

        // When Reps is changed to 8 (which has no RM record)
        val eightRm = rmLookup[backSquat.id to 8]
        assertNull("No RM record exists for 8 reps", eightRm)
        draft = draft.onRepsChanged("8", eightRm)

        // Then the fields are cleared and remain editable
        assertEquals("", draft.startWeight)
        assertEquals("", draft.targetWeight)
        assertFalse(draft.hasStartWeight)
        assertNull(draft.selectedModifier)

        // Remains editable: user can manually type in a custom target
        draft = draft.onTargetWeightChanged("70.0")
        assertEquals("70.0", draft.targetWeight)
        assertTrue(draft.isManuallyEdited)
    }

    // =========================================================================
    // Scenario 3: Modifier Chips and 0% Reset with 0.5 Rounding
    // =========================================================================

    @Test
    fun scenario3_modifierChipsAndZeroPercentResetWithHalfUpRounding() {
        // Given a base RM of 73.0 kg populated in startWeight
        var draft = GoalDraftState(
            exercise = backSquat,
            reps = "1",
            startWeight = "73.0",
            targetWeight = "73.0"
        )
        assertTrue("Chips must be enabled when startWeight is non-empty", draft.hasStartWeight)

        // When the +2.5% chip is selected
        // 73.0 * 1.025 = 74.825 -> 75.0 via half-up rounding to nearest 0.5
        draft = draft.applyModifier(2.5)
        assertEquals("75", draft.targetWeight)
        assertEquals(2.5, draft.selectedModifier)

        // When the +5% chip is selected (non-compounding, calculated from 73.0)
        // 73.0 * 1.05 = 76.65 -> 76.5 via half-up rounding
        draft = draft.applyModifier(5.0)
        assertEquals("76.5", draft.targetWeight)
        assertEquals(5.0, draft.selectedModifier)

        // When the +10% chip is selected (non-compounding)
        // 73.0 * 1.10 = 80.3 -> 80.5 via half-up rounding
        draft = draft.applyModifier(10.0)
        assertEquals("80.5", draft.targetWeight)
        assertEquals(10.0, draft.selectedModifier)

        // When 0% is tapped
        draft = draft.resetModifier()
        assertEquals("73", draft.targetWeight)
        assertEquals(0.0, draft.selectedModifier)
    }

    @Test
    fun scenario3_modifierChipsDisabledWhenStartWeightIsEmptyOrZero() {
        // Given empty startWeight
        val emptyDraft = GoalDraftState(exercise = backSquat, reps = "1", startWeight = "")
        assertFalse("Chips must be disabled when startWeight is empty", emptyDraft.hasStartWeight)

        // Given zero startWeight
        val zeroDraft = GoalDraftState(exercise = backSquat, reps = "1", startWeight = "0.0")
        assertFalse("Chips must be disabled when startWeight is 0.0", zeroDraft.hasStartWeight)

        // Given negative startWeight
        val negDraft = GoalDraftState(exercise = backSquat, reps = "1", startWeight = "-5.0")
        assertFalse("Chips must be disabled when startWeight is negative", negDraft.hasStartWeight)

        // Applying modifier to empty draft is a no-op
        val afterMod = emptyDraft.applyModifier(5.0)
        assertEquals("", afterMod.targetWeight)
        assertNull(afterMod.selectedModifier)
    }

    // =========================================================================
    // Scenario 4: Reopening Existing Cycle Preserves Stored Values
    // =========================================================================

    @Test
    fun scenario4_reopeningExistingCyclePreservesStoredValuesWithoutOverwriting() {
        // Given an existing cycle with saved CycleGoal(startWeight = 90.0, targetWeight = 95.0)
        val existingGoal = CycleGoal(
            id = 10L,
            cycleId = 1L,
            exerciseId = backSquat.id,
            targetReps = 3,
            startWeight = 90.0,
            targetWeight = 95.0
        )

        // When the cycle is opened in CyclesScreen (reconstituted into GoalDraftState)
        val reopenedDraft = GoalDraftState(
            id = existingGoal.id,
            exercise = backSquat,
            reps = existingGoal.targetReps.toString(),
            startWeight = existingGoal.startWeight.toString(),
            targetWeight = existingGoal.targetWeight.toString(),
            isManuallyEdited = true
        )

        // Then stored values 90.0 kg and 95.0 kg are rendered without auto-fill re-querying or overwriting them
        assertEquals("90.0", reopenedDraft.startWeight)
        assertEquals("95.0", reopenedDraft.targetWeight)
        assertEquals("3", reopenedDraft.reps)
        assertTrue(reopenedDraft.isManuallyEdited)

        val preservedGoal = reopenedDraft.toCycleGoal(cycleId = 1L)
        assertNotNull(preservedGoal)
        assertEquals(90.0, preservedGoal!!.startWeight, 0.001)
        assertEquals(95.0, preservedGoal.targetWeight, 0.001)
    }

    // =========================================================================
    // Scenario 8: Non-Positive & Invalid Target Weight Handling
    // =========================================================================

    @Test
    fun scenario8_nonPositiveWeightsRejectedInlineAndFieldRemainsEmpty() {
        assertEquals("", FatLossValidation.sanitizeInlineWeightInput("0"))
        assertEquals("", FatLossValidation.sanitizeInlineWeightInput("0.0"))
        assertEquals("", FatLossValidation.sanitizeInlineWeightInput("-1"))
        assertEquals("", FatLossValidation.sanitizeInlineWeightInput("-80.0"))

        assertEquals("80.0", FatLossValidation.sanitizeInlineWeightInput("80.0"))
        assertEquals("75.5", FatLossValidation.sanitizeInlineWeightInput("75.5"))
    }

    @Test
    fun scenario8_targetWeightGreaterOrEqualToStartWeightTriggersValidationError() {
        val startWeight = 80.0

        assertTrue(
            "Target weight 82.0 >= 80.0 must be flagged as invalid",
            FatLossValidation.isTargetWeightInvalid(startWeight, 82.0)
        )
        assertTrue(
            "Target weight equal to start weight (80.0) must be flagged as invalid",
            FatLossValidation.isTargetWeightInvalid(startWeight, 80.0)
        )
        assertFalse(
            "Target weight 75.0 < 80.0 is valid",
            FatLossValidation.isTargetWeightInvalid(startWeight, 75.0)
        )

        val progress = FatLossAnalytics.computeWeightProgress(
            startingWeightKg = 80.0,
            targetWeightKg = 82.0,
            currentWeightKg = 80.0
        )
        assertNull("Percent to target must evaluate to null / 'n/a' when target >= start", progress.percentToTarget)
        assertEquals("n/a", progress.displayText)
    }

    @Test
    fun cycleGoalEntity_savesStartWeightCorrectly() {
        val draft = GoalDraftState(
            exercise = benchPress,
            reps = "5",
            startWeight = "82.5",
            targetWeight = "87.5"
        )
        val goal = draft.toCycleGoal(cycleId = 99L)
        assertNotNull(goal)
        assertEquals(82.5, goal!!.startWeight, 0.001)
        assertEquals(87.5, goal.targetWeight, 0.001)
    }

    // =========================================================================
    // T004 [US1]: Weight-Based RM Stepper, Presets, and Percentage Scaling
    // =========================================================================

    @Test
    fun us1_rmStepperClampedAtOneAndStepsUpAndDownWithRmLookup() {
        val rmLookup = mapOf(
            (backSquat.id to 1) to 160.0,
            (backSquat.id to 2) to 150.0,
            (backSquat.id to 3) to 142.5,
            (backSquat.id to 5) to 140.0
        )

        var draft = GoalDraftState(exercise = backSquat, reps = "1")
            .onExerciseChanged(backSquat, rmLookup[backSquat.id to 1])
        assertEquals(MetricType.WEIGHT, draft.goalMetric)
        assertEquals("1", draft.reps)
        assertEquals("160", draft.startWeight)

        // Stepping down at 1 RM clamps at 1 RM and preserves state
        assertEquals(1, draft.nextSteppedReps(-1))
        draft = draft.stepReps(-1)
        assertEquals("1", draft.reps)
        assertEquals("160", draft.startWeight)

        // Stepping up (+1) to 2 RM updates reps and Start/Target from lookup
        val nextUp = draft.nextSteppedReps(1)
        assertEquals(2, nextUp)
        draft = draft.stepReps(1, rmLookup[backSquat.id to nextUp])
        assertEquals("2", draft.reps)
        assertEquals("150", draft.startWeight)
        assertEquals("150", draft.targetWeight)

        // Selecting preset 5 RM (from CycleGoal.RM_PRESETS = [1, 3, 5, 10])
        assertEquals(listOf(1, 3, 5, 10), CycleGoal.RM_PRESETS)
        draft = draft.onRepsChanged("5", rmLookup[backSquat.id to 5])
        assertEquals("5", draft.reps)
        assertEquals("140", draft.startWeight)

        // Applying +5% on 140 kg -> 147 kg -> 147.0 ("147")
        draft = draft.applyModifier(5.0)
        assertEquals("147", draft.targetWeight)
    }

    // =========================================================================
    // T006 [US2]: Default Goal Metric Inference, Time Windows, Best Set Auto-Fill, Additive Scale
    // =========================================================================

    @Test
    fun us2_defaultGoalMetricInferenceAndManualMetricOverride() {
        assertEquals(MetricType.WEIGHT, CycleGoal.defaultGoalMetricFor(backSquat))
        assertEquals(MetricType.CALORIES, CycleGoal.defaultGoalMetricFor(airBike))
        assertEquals(MetricType.REPS, CycleGoal.defaultGoalMetricFor(pullUps))

        // Selecting Air Bike defaults goalMetric to CALORIES and unitLabel to "cal"
        var draft = GoalDraftState().onExerciseChanged(airBike, bestWeight = 40.0)
        assertEquals(MetricType.CALORIES, draft.goalMetric)
        assertEquals("cal", draft.unitLabel)
        assertEquals("40", draft.startWeight)
        assertEquals("40", draft.targetWeight)

        // Selecting Pull-ups defaults goalMetric to REPS and unitLabel to "reps"
        draft = draft.onExerciseChanged(pullUps, bestWeight = 12.0)
        assertEquals(MetricType.REPS, draft.goalMetric)
        assertEquals("reps", draft.unitLabel)
        assertEquals("12", draft.startWeight)
        assertEquals("12", draft.targetWeight)

        // Coach manually overrides Pull-ups goalMetric to WEIGHT (e.g. weighted pull-ups)
        draft = draft.onGoalMetricChanged(MetricType.WEIGHT, bestValue = 25.0)
        assertEquals(MetricType.WEIGHT, draft.goalMetric)
        assertEquals("kg", draft.unitLabel)
        assertNull(draft.timeWindowMinutes)
        assertEquals("25", draft.startWeight)
        assertEquals("25", draft.targetWeight)
    }

    @Test
    fun us2_caloriesGoalTimeWindowAndAdditiveScaleModifiers() {
        val workingSets = listOf(
            BlockSet(id = 1, blockId = 1, position = 0, reps = 1, metricValue = 32.0),
            BlockSet(id = 2, blockId = 1, position = 1, reps = 1, metricValue = 40.0),
            BlockSet(id = 3, blockId = 1, position = 2, reps = 1, metricValue = 55.0, isWarmup = true)
        )
        val bestCal = CycleGoal.extractBestWorkingSetMetricValue(
            workingSets.filter { !it.isWarmup && !it.isFailed },
            MetricType.CALORIES
        )
        assertEquals(40.0, bestCal ?: 0.0, 0.001)

        var draft = GoalDraftState().onExerciseChanged(airBike, bestCal)
        assertEquals(MetricType.CALORIES, draft.goalMetric)
        assertNull("Default Time Window must be Open (null)", draft.timeWindowMinutes)

        // Select "5 min" Time Window
        draft = draft.onTimeWindowChanged(5)
        assertEquals(5, draft.timeWindowMinutes)
        assertEquals("40", draft.startWeight)

        // Apply +5 cal, +10 cal, +20 cal, and 0 cal reset (non-compounding additive increments)
        draft = draft.applyModifier(5.0)
        assertEquals("45", draft.targetWeight)
        assertEquals(5.0, draft.selectedModifier)

        draft = draft.applyModifier(10.0)
        assertEquals("50", draft.targetWeight)
        assertEquals(10.0, draft.selectedModifier)

        draft = draft.applyModifier(20.0)
        assertEquals("60", draft.targetWeight)
        assertEquals(20.0, draft.selectedModifier)

        draft = draft.resetModifier()
        assertEquals("40", draft.targetWeight)
        assertEquals(0.0, draft.selectedModifier)
    }

    @Test
    fun us2_repsGoalTimeWindowAndAdditiveScaleModifiers() {
        val workingSets = listOf(
            BlockSet(id = 1, blockId = 1, position = 0, reps = 10),
            BlockSet(id = 2, blockId = 1, position = 1, reps = 12),
            BlockSet(id = 3, blockId = 1, position = 2, reps = 15, isFailed = true)
        )
        val bestReps = CycleGoal.extractBestWorkingSetMetricValue(
            workingSets.filter { !it.isWarmup && !it.isFailed },
            MetricType.REPS
        )
        assertEquals(12.0, bestReps ?: 0.0, 0.001)

        var draft = GoalDraftState().onExerciseChanged(pullUps, bestReps)
        assertEquals(MetricType.REPS, draft.goalMetric)
        assertNull(draft.timeWindowMinutes)

        // Apply +2 reps, +5 reps, +10 reps, and 0 reps reset
        draft = draft.applyModifier(2.0)
        assertEquals("14", draft.targetWeight)

        draft = draft.applyModifier(5.0)
        assertEquals("17", draft.targetWeight)

        draft = draft.applyModifier(10.0)
        assertEquals("22", draft.targetWeight)

        draft = draft.resetModifier()
        assertEquals("12", draft.targetWeight)
    }

    // =========================================================================
    // T008 [US3]: Metadata Round-Trip, Summary Chip & Progress Evaluation
    // =========================================================================

    @Test
    fun us3_cycleGoalMetadataRoundTripAndReconstitution() {
        // 1. Weight Goal -> clean notes without metadata prefix
        val weightDraft = GoalDraftState(
            id = 1L,
            exercise = backSquat,
            reps = "5",
            startWeight = "140",
            targetWeight = "147.5",
            goalMetric = MetricType.WEIGHT
        )
        val weightGoal = weightDraft.toCycleGoal(cycleId = 7L)!!
        assertEquals(MetricType.WEIGHT, weightGoal.goalMetric)
        assertNull(weightGoal.timeWindowMinutes)
        assertEquals("", weightGoal.notes)
        assertEquals("Back Squat (5RM): 140 → 147.5kg", weightGoal.formatSummaryChip("Back Squat", "kg"))
        assertEquals("Back Squat (5RM Goal)", weightGoal.formatProgressHeader("Back Squat"))

        // 2. Calorie Goal (5 min) -> metadata round-trip
        val calDraft = GoalDraftState(
            id = 2L,
            exercise = airBike,
            startWeight = "40",
            targetWeight = "50",
            goalMetric = MetricType.CALORIES,
            timeWindowMinutes = 5,
            notes = "Keep cadence > 65 RPM"
        )
        val calGoal = calDraft.toCycleGoal(cycleId = 7L)!!
        assertEquals(MetricType.CALORIES, calGoal.goalMetric)
        assertEquals(5, calGoal.timeWindowMinutes)
        assertEquals("Keep cadence > 65 RPM", calGoal.cleanNotes)
        assertEquals("Air Bike (5 min): 40 → 50 cal", calGoal.formatSummaryChip("Air Bike", "cal"))
        assertEquals("Air Bike (5 min Goal)", calGoal.formatProgressHeader("Air Bike"))

        val reopenedCalDraft = GoalDraftState.fromCycleGoal(calGoal, airBike)
        assertEquals(MetricType.CALORIES, reopenedCalDraft.goalMetric)
        assertEquals(5, reopenedCalDraft.timeWindowMinutes)
        assertEquals("40", reopenedCalDraft.startWeight)
        assertEquals("50", reopenedCalDraft.targetWeight)
        assertEquals("Keep cadence > 65 RPM", reopenedCalDraft.notes)

        // 3. Reps Goal (Open) -> metadata round-trip
        val repsDraft = GoalDraftState(
            id = 3L,
            exercise = pullUps,
            startWeight = "12",
            targetWeight = "17",
            goalMetric = MetricType.REPS,
            timeWindowMinutes = null
        )
        val repsGoal = repsDraft.toCycleGoal(cycleId = 7L)!!
        assertEquals(MetricType.REPS, repsGoal.goalMetric)
        assertNull(repsGoal.timeWindowMinutes)
        assertEquals("Pull-ups (Open): 12 → 17 reps", repsGoal.formatSummaryChip("Pull-ups", "kg"))
        assertEquals("Pull-ups (Open Goal)", repsGoal.formatProgressHeader("Pull-ups"))

        val reopenedRepsDraft = GoalDraftState.fromCycleGoal(repsGoal, pullUps)
        assertEquals(MetricType.REPS, reopenedRepsDraft.goalMetric)
        assertNull(reopenedRepsDraft.timeWindowMinutes)
        assertEquals("12", reopenedRepsDraft.startWeight)
        assertEquals("17", reopenedRepsDraft.targetWeight)
    }

    @Test
    fun us3_evaluateCycleGoalProgressTracksWeightCaloriesAndRepsGoals() {
        val cycleStart = LocalDate.of(2026, 11, 1)
        val cycleEnd = LocalDate.of(2026, 12, 27)
        val cycle = Cycle(id = 1L, name = "Winter Block", startDate = cycleStart, endDate = cycleEnd, isActive = true)

        val sessionDate = LocalDate.of(2026, 11, 10)
        val session = Session(id = 100L, cycleId = 1L, date = sessionDate, title = "Mixed Conditioning")
        val bikeBlock = SessionBlock(id = 200L, sessionId = 100L, position = 0, mainExerciseId = airBike.id)
        val pullUpBlock = SessionBlock(id = 201L, sessionId = 100L, position = 1, mainExerciseId = pullUps.id)

        val sessions = listOf(
            SessionWithBlocks(
                session = session,
                blocks = listOf(
                    BlockWithSets(
                        block = bikeBlock,
                        sets = listOf(
                            BlockSet(id = 1, blockId = 200L, position = 0, reps = 1, metricValue = 45.0)
                        )
                    ),
                    BlockWithSets(
                        block = pullUpBlock,
                        sets = listOf(
                            BlockSet(id = 2, blockId = 201L, position = 0, reps = 15)
                        )
                    )
                )
            )
        )
        val repMaxes = listOf(
            RepMax(id = 1L, exerciseId = backSquat.id, reps = 5, weight = 145.0, date = sessionDate)
        )

        // 1. Weight goal (140 -> 150 kg, logged 145 kg -> 50%)
        val weightGoal = CycleGoal(
            id = 1L,
            cycleId = 1L,
            exerciseId = backSquat.id,
            targetReps = 5,
            startWeight = 140.0,
            targetWeight = 150.0,
            goalMetric = MetricType.WEIGHT
        )
        val weightEval = evaluateCycleGoalProgress(weightGoal, cycle, repMaxes, sessions)
        assertEquals(140.0, weightEval.baselineStartWeight, 0.001)
        assertEquals(145.0, weightEval.currentBest, 0.001)
        assertEquals(50, weightEval.progressPercent)

        // 2. Calorie goal (40 -> 50 cal, logged 45 cal -> 50%)
        val calGoal = CycleGoal(
            id = 2L,
            cycleId = 1L,
            exerciseId = airBike.id,
            startWeight = 40.0,
            targetWeight = 50.0,
            goalMetric = MetricType.CALORIES,
            timeWindowMinutes = 5
        )
        val calEval = evaluateCycleGoalProgress(calGoal, cycle, repMaxes, sessions)
        assertEquals(40.0, calEval.baselineStartWeight, 0.001)
        assertEquals(45.0, calEval.currentBest, 0.001)
        assertEquals(50, calEval.progressPercent)

        // 3. Reps goal (12 -> 18 reps, logged 15 reps -> 50%)
        val repsGoal = CycleGoal(
            id = 3L,
            cycleId = 1L,
            exerciseId = pullUps.id,
            startWeight = 12.0,
            targetWeight = 18.0,
            goalMetric = MetricType.REPS,
            timeWindowMinutes = null
        )
        val repsEval = evaluateCycleGoalProgress(repsGoal, cycle, repMaxes, sessions)
        assertEquals(12.0, repsEval.baselineStartWeight, 0.001)
        assertEquals(15.0, repsEval.currentBest, 0.001)
        assertEquals(50, repsEval.progressPercent)
    }
}
