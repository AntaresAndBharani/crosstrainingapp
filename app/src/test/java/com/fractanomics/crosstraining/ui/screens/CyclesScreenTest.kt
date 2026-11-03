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
import org.junit.Before
import org.junit.Test
import java.time.LocalDate
import java.util.Locale

/**
 * Unit tests verifying CyclesScreen Goal Dialog RM Auto-Fill, Modifier Chips,
 * Adaptive Goal Metrics (Weight, Calories, Reps), Time Windows, and Backward-Compatible
 * Progress Evaluation.
 */
class CyclesScreenTest {

    @Before
    fun setUp() {
        Locale.setDefault(Locale.US)
    }

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

        // Then targetWeight is pre-filled with 100.0 kg; the hidden Start stays empty (SPEC-CYCLE-GOAL-04)
        assertEquals("", updatedDraft.startWeight)
        assertEquals("100", updatedDraft.targetWeight)
        assertFalse(updatedDraft.hasStartWeight)

        // Target-only goals persist startWeight = 0.0 so ProgressScreen derives the baseline
        val goalEntity = updatedDraft.toCycleGoal(cycleId = 42L)
        assertNotNull(goalEntity)
        assertEquals(42L, goalEntity!!.cycleId)
        assertEquals(backSquat.id, goalEntity.exerciseId)
        assertEquals(1, goalEntity.targetReps)
        assertEquals(0.0, goalEntity.startWeight, 0.001)
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
        assertEquals("", draft.startWeight)
        assertEquals("100", draft.targetWeight)

        // When Reps is changed from 1 to 5
        val fiveRm = rmLookup[backSquat.id to 5]
        draft = draft.onRepsChanged("5", fiveRm)

        // Then targetWeight updates to 85.0 kg and startWeight stays empty (persisted as 0.0)
        assertEquals("", draft.startWeight)
        assertEquals("85", draft.targetWeight)
        assertEquals(0.0, draft.toCycleGoal(1L)!!.startWeight, 0.001)
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
        assertEquals("", draft.startWeight)
        assertEquals("160", draft.targetWeight)

        // Stepping down at 1 RM clamps at 1 RM and preserves state
        assertEquals(1, draft.nextSteppedReps(-1))
        draft = draft.stepReps(-1)
        assertEquals("1", draft.reps)
        assertEquals("160", draft.targetWeight)

        // Stepping up (+1) to 2 RM updates reps and Target from lookup
        val nextUp = draft.nextSteppedReps(1)
        assertEquals(2, nextUp)
        draft = draft.stepReps(1, rmLookup[backSquat.id to nextUp])
        assertEquals("2", draft.reps)
        assertEquals("", draft.startWeight)
        assertEquals("150", draft.targetWeight)

        // Selecting preset 5 RM (from CycleGoal.RM_PRESETS = [1, 3, 5, 10])
        assertEquals(listOf(1, 3, 5, 10), CycleGoal.RM_PRESETS)
        draft = draft.onRepsChanged("5", rmLookup[backSquat.id to 5])
        assertEquals("5", draft.reps)
        assertEquals("", draft.startWeight)
        assertEquals("140", draft.targetWeight)

        // Quick target stepper +5 kg on 140 kg -> 145 kg
        draft = draft.stepTarget(5.0)
        assertEquals("145", draft.targetWeight)
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
        assertEquals("", draft.startWeight)
        assertEquals("40", draft.targetWeight)

        // Selecting Pull-ups defaults goalMetric to REPS and unitLabel to "reps"
        draft = draft.onExerciseChanged(pullUps, bestWeight = 12.0)
        assertEquals(MetricType.REPS, draft.goalMetric)
        assertEquals("reps", draft.unitLabel)
        assertEquals("", draft.startWeight)
        assertEquals("12", draft.targetWeight)

        // Coach manually overrides Pull-ups goalMetric to WEIGHT (e.g. weighted pull-ups)
        draft = draft.onGoalMetricChanged(MetricType.WEIGHT, bestValue = 25.0)
        assertEquals(MetricType.WEIGHT, draft.goalMetric)
        assertEquals("kg", draft.unitLabel)
        assertNull(draft.timeWindowMinutes)
        assertEquals("", draft.startWeight)
        assertEquals("25", draft.targetWeight)
    }

    @Test
    fun us2_reselectingActiveGoalMetricPreservesManualStartAndTarget() {
        val edited = GoalDraftState()
            .onExerciseChanged(airBike, bestWeight = 40.0)
            .onTimeWindowChanged(5)
            .onStartWeightChanged("42")
            .onTargetWeightChanged("55")

        // Tapping the already-selected Calories pill (even with a fresh lookup) is a no-op
        val reselected = edited.onGoalMetricChanged(MetricType.CALORIES, bestValue = 40.0)
        assertEquals(edited, reselected)
        assertEquals("42", reselected.startWeight)
        assertEquals("55", reselected.targetWeight)
        assertEquals(5, reselected.timeWindowMinutes)
        assertTrue(reselected.isManuallyEdited)
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

        // Legacy additive Scale helpers still operate on an explicit (legacy) Start value
        var draft = GoalDraftState().onExerciseChanged(airBike, bestCal).copy(startWeight = "40")
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

        // Legacy additive Scale helpers still operate on an explicit (legacy) Start value
        var draft = GoalDraftState().onExerciseChanged(pullUps, bestReps).copy(startWeight = "12")
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

    // =========================================================================
    // T003 [US1]: MovementSelectorUiState Expansion, Search Filtering & Selection
    // =========================================================================

    @Test
    fun us1_movementSelectorExpandsFiltersCaseInsensitivelyAndResetsOnSelection() {
        val airSquat = Exercise(id = 5L, name = "Air Squat", category = ExerciseCategory.GYMNASTICS)
        val frontSquat = Exercise(id = 6L, name = "Front Squat")
        val cleanAndJerk = Exercise(id = 7L, name = "Clean & Jerk")
        val catalog = listOf(airBike, airSquat, backSquat, benchPress, cleanAndJerk, frontSquat, pullUps)

        var selectorState = MovementSelectorUiState()
        assertFalse(selectorState.expanded)
        assertEquals("", selectorState.searchQuery)
        assertFalse(selectorState.hasSearchQuery)

        // Expanding the Movement (Basic) selector opens the inline search box with all movements visible
        selectorState = selectorState.toggleExpanded()
        assertTrue(selectorState.expanded)
        assertEquals(catalog, selectorState.filterExercises(catalog))

        // Typing in "Search movement…" filters movements in real time (case-insensitive & trimmed)
        selectorState = selectorState.onSearchQueryChanged("  sQuAt ")
        assertTrue(selectorState.expanded)
        assertTrue(selectorState.hasSearchQuery)
        val squatMatches = selectorState.filterExercises(catalog)
        assertEquals(listOf(airSquat, backSquat, frontSquat), squatMatches)

        // Selecting a movement from the filtered list updates GoalDraftState and collapses/resets the selector
        var goalDraft = GoalDraftState(exercise = airBike, goalMetric = MetricType.CALORIES)
        val chosen = squatMatches[1] // Back Squat
        goalDraft = goalDraft.onExerciseChanged(chosen, bestWeight = 140.0)
        selectorState = selectorState.onExerciseSelected()

        assertEquals(backSquat, goalDraft.exercise)
        assertEquals(MetricType.WEIGHT, goalDraft.goalMetric)
        assertEquals("", goalDraft.startWeight)
        assertEquals("140", goalDraft.targetWeight)
        assertFalse(selectorState.expanded)
        assertEquals("", selectorState.searchQuery)
        assertFalse(selectorState.hasSearchQuery)
    }

    // =========================================================================
    // T005 [US2]: Clear Search Action, Empty Filter Feedback & Toggle Collapse
    // =========================================================================

    @Test
    fun us2_movementSelectorClearSearchEmptyFilterAndToggleCollapsePreservesSelection() {
        val catalog = listOf(airBike, backSquat, benchPress, pullUps)

        // Given Goal 1 has Air Bike selected and the user opens the movement selector
        val goalDraft = GoalDraftState(exercise = airBike, goalMetric = MetricType.CALORIES)
        var selectorState = MovementSelectorUiState().open()
        assertTrue(selectorState.expanded)

        // When the user types a non-matching query, filteredExercises is empty (triggers empty-state message)
        selectorState = selectorState.onSearchQueryChanged("snatch")
        assertTrue(selectorState.hasSearchQuery)
        assertTrue(selectorState.filterExercises(catalog).isEmpty())

        // When the user taps "Clear search", the query resets to empty while keeping the selector open
        selectorState = selectorState.clearSearchQuery()
        assertTrue("Selector must stay expanded after clearing search", selectorState.expanded)
        assertEquals("", selectorState.searchQuery)
        assertFalse(selectorState.hasSearchQuery)
        assertEquals(catalog, selectorState.filterExercises(catalog))

        // When the user types a partial query and then taps "Change ▴" to toggle collapse without selecting
        selectorState = selectorState.onSearchQueryChanged("bench")
        assertEquals(listOf(benchPress), selectorState.filterExercises(catalog))
        selectorState = selectorState.toggleExpanded()

        // Then the selector collapses, clears the partial search query, and preserves the existing selection
        assertFalse(selectorState.expanded)
        assertEquals("", selectorState.searchQuery)
        assertEquals(airBike, goalDraft.exercise)
    }

    @Test
    fun us2_movementSelectorBackPressDismissCollapsesAndResetsWithoutSelecting() {
        val catalog = listOf(airBike, backSquat, benchPress, pullUps)
        val goalDraft = GoalDraftState(exercise = airBike, goalMetric = MetricType.CALORIES)

        // Given the selector is open with a partial query
        var selectorState = MovementSelectorUiState()
            .toggleExpanded()
            .onSearchQueryChanged("pull")
        assertTrue(selectorState.expanded)
        assertEquals(listOf(pullUps), selectorState.filterExercises(catalog))

        // When system back is pressed (BackHandler -> dismiss())
        selectorState = selectorState.dismiss()

        // Then the selector collapses, the query resets, and the existing selection is preserved
        assertFalse(selectorState.expanded)
        assertEquals("", selectorState.searchQuery)
        assertFalse(selectorState.hasSearchQuery)
        assertEquals(airBike, goalDraft.exercise)

        // Re-opening after dismiss starts from a clean, unfiltered list
        selectorState = selectorState.toggleExpanded()
        assertTrue(selectorState.expanded)
        assertEquals(catalog, selectorState.filterExercises(catalog))
    }

    // =========================================================================
    // SPEC-CYCLE-GOAL-04 (013): Target-Only Input & Quick Target Stepper Pills (1A)
    // =========================================================================

    @Test
    fun spec013_us1_targetStepperOptionsAndInPlaceSteppingWithZeroFloorClamping() {
        val overheadSquat = Exercise(id = 8L, name = "Overhead Squat", unit = "kg")

        // 1. Weight Goal Steppers: -2.5 kg, +2.5 kg, +5 kg
        var weightDraft = GoalDraftState(
            exercise = overheadSquat,
            reps = "3",
            targetWeight = "60",
            goalMetric = MetricType.WEIGHT
        )
        assertEquals(
            listOf(-2.5 to "-2.5 kg", 2.5 to "+2.5 kg", 5.0 to "+5 kg"),
            weightDraft.targetStepperOptions
        )

        weightDraft = weightDraft.stepTarget(2.5)
        assertEquals("62.5", weightDraft.targetWeight)
        assertEquals(2.5, weightDraft.selectedModifier)

        weightDraft = weightDraft.stepTarget(5.0)
        assertEquals("67.5", weightDraft.targetWeight)
        assertEquals(5.0, weightDraft.selectedModifier)

        weightDraft = weightDraft.stepTarget(-2.5)
        assertEquals("65", weightDraft.targetWeight)
        assertEquals(-2.5, weightDraft.selectedModifier)

        // 2. Empty or 0 Target initializes from 0.0 and clamps at 0 floor
        var emptyDraft = GoalDraftState(
            exercise = overheadSquat,
            reps = "1",
            targetWeight = "",
            goalMetric = MetricType.WEIGHT,
            showTargetError = true
        )
        emptyDraft = emptyDraft.stepTarget(-2.5)
        assertEquals("0", emptyDraft.targetWeight)
        assertTrue("Stepping to 0 keeps showTargetError active", emptyDraft.showTargetError)

        emptyDraft = emptyDraft.stepTarget(5.0)
        assertEquals("5", emptyDraft.targetWeight)
        assertFalse("Stepping above 0 clears showTargetError", emptyDraft.showTargetError)

        // Stepping down from 2.0 by -2.5 clamps at 0
        val clampedDraft = emptyDraft.onTargetWeightChanged("2").stepTarget(-2.5)
        assertEquals("0", clampedDraft.targetWeight)

        // 3. Calories Goal Steppers: -5 cal, +5 cal, +10 cal
        var calDraft = GoalDraftState(
            exercise = airBike,
            targetWeight = "40",
            goalMetric = MetricType.CALORIES,
            timeWindowMinutes = 5
        )
        assertEquals(
            listOf(-5.0 to "-5 cal", 5.0 to "+5 cal", 10.0 to "+10 cal"),
            calDraft.targetStepperOptions
        )
        calDraft = calDraft.stepTarget(10.0)
        assertEquals("50", calDraft.targetWeight)
        calDraft = calDraft.stepTarget(-5.0)
        assertEquals("45", calDraft.targetWeight)

        // 4. Reps Goal Steppers: -1 rep, +1 rep, +5 reps
        var repsDraft = GoalDraftState(
            exercise = pullUps,
            targetWeight = "10",
            goalMetric = MetricType.REPS
        )
        assertEquals(
            listOf(-1.0 to "-1 rep", 1.0 to "+1 rep", 5.0 to "+5 reps"),
            repsDraft.targetStepperOptions
        )
        repsDraft = repsDraft.stepTarget(5.0)
        assertEquals("15", repsDraft.targetWeight)
        repsDraft = repsDraft.stepTarget(-1.0)
        assertEquals("14", repsDraft.targetWeight)
        repsDraft = repsDraft.stepTarget(1.0)
        assertEquals("15", repsDraft.targetWeight)
    }

    // =========================================================================
    // SPEC-CYCLE-GOAL-04 (013): Collapsible Summary Card & Accordion Transitions (Option 2)
    // =========================================================================

    @Test
    fun spec013_us2_existingSavedGoalsInitializeCollapsedAndFormatBadgesAndA11y() {
        val overheadSquat = Exercise(id = 8L, name = "Overhead Squat", unit = "kg")
        val savedWeightGoal = CycleGoal(
            id = 11L,
            cycleId = 1L,
            exerciseId = overheadSquat.id,
            targetReps = 3,
            startWeight = 0.0,
            targetWeight = 65.0,
            goalMetric = MetricType.WEIGHT
        )
        val savedCalGoal = CycleGoal(
            id = 12L,
            cycleId = 1L,
            exerciseId = airBike.id,
            targetReps = 1,
            startWeight = 0.0,
            targetWeight = 45.0,
            goalMetric = MetricType.CALORIES,
            timeWindowMinutes = 5
        )
        val savedRepsGoal = CycleGoal(
            id = 13L,
            cycleId = 1L,
            exerciseId = pullUps.id,
            targetReps = 1,
            startWeight = 0.0,
            targetWeight = 15.0,
            goalMetric = MetricType.REPS,
            timeWindowMinutes = null
        )

        val draft1 = GoalDraftState.fromCycleGoal(savedWeightGoal, overheadSquat)
        val draft2 = GoalDraftState.fromCycleGoal(savedCalGoal, airBike)
        val draft3 = GoalDraftState.fromCycleGoal(savedRepsGoal, pullUps)

        // All saved goals with targetWeight > 0 load in Collapsed Summary State by default
        assertTrue(draft1.isCollapsed)
        assertTrue(draft2.isCollapsed)
        assertTrue(draft3.isCollapsed)

        // Verify Collapsed Summary titles, context badges, target badges, and accessibility strings
        assertEquals("Goal 1: Overhead Squat", draft1.formatCollapsedSummaryTitle(0))
        assertEquals("3RM", draft1.contextBadgeText)
        assertEquals("Target: 65 kg", draft1.targetBadgeText)
        assertEquals(
            "Goal 1: Overhead Squat, 3RM, Target 65 kg. Collapsed. Double-tap to edit goal.",
            draft1.collapsedAccessibilityDescription(0)
        )

        assertEquals("Goal 2: Air Bike", draft2.formatCollapsedSummaryTitle(1))
        assertEquals("5 min", draft2.contextBadgeText)
        assertEquals("Target: 45 cal", draft2.targetBadgeText)

        assertEquals("Goal 3: Pull-ups", draft3.formatCollapsedSummaryTitle(2))
        assertEquals("Open", draft3.contextBadgeText)
        assertEquals("Target: 15 reps", draft3.targetBadgeText)
    }

    @Test
    fun spec013_us2_exclusiveSingleExpandAccordionAndValidationOnCollapseAndAppend() {
        val overheadSquat = Exercise(id = 8L, name = "Overhead Squat", unit = "kg")
        val g1 = GoalDraftState(
            id = 1L,
            exercise = overheadSquat,
            reps = "3",
            targetWeight = "65",
            goalMetric = MetricType.WEIGHT,
            isCollapsed = true
        )
        val g2 = GoalDraftState(
            id = 2L,
            exercise = airBike,
            targetWeight = "45",
            goalMetric = MetricType.CALORIES,
            timeWindowMinutes = 5,
            isCollapsed = true
        )

        var drafts = listOf(g1, g2)

        // 1. Tapping collapsed Goal 1 expands it
        drafts = expandGoalDraftAt(drafts, 0)
        assertFalse(drafts[0].isCollapsed)
        assertTrue(drafts[1].isCollapsed)

        // 2. Tapping collapsed Goal 2 while Goal 1 is valid auto-collapses Goal 1 and expands Goal 2
        drafts = expandGoalDraftAt(drafts, 1)
        assertTrue("Goal 1 must auto-collapse when Goal 2 is expanded", drafts[0].isCollapsed)
        assertFalse("Goal 2 must expand", drafts[1].isCollapsed)

        // 3. Clearing Goal 2's target and attempting to tap Done ▲ or + Movement blocks collapse/append
        drafts = drafts.toMutableList().also {
            it[1] = it[1].onTargetWeightChanged("")
        }
        drafts = collapseGoalDraftAt(drafts, 1)
        assertFalse("Invalid card must remain expanded when Done ▲ is tapped", drafts[1].isCollapsed)
        assertTrue("Invalid card must display inline target error", drafts[1].showTargetError)

        // Attempting to tap + Movement while Goal 2 has invalid target is also blocked
        val newDraft = GoalDraftState(exercise = pullUps, goalMetric = MetricType.REPS)
        val (blockedList, wasAppendedBlocked) = appendMovementGoalDraft(drafts, newDraft)
        assertFalse("Adding a new movement goal must be blocked when open card has invalid target", wasAppendedBlocked)
        assertEquals(2, blockedList.size)
        assertTrue(blockedList[1].showTargetError)

        // Expanding Goal 1 while Goal 2 was cleared never deadlocks: Goal 2 restores its last valid target ("45") and collapses
        val switchedToGoal1 = expandGoalDraftAt(blockedList, 0)
        assertFalse("Goal 1 must expand without deadlocking", switchedToGoal1[0].isCollapsed)
        assertTrue("Goal 2 must collapse cleanly with restored valid target", switchedToGoal1[1].isCollapsed)
        assertEquals("45", switchedToGoal1[1].targetWeight)
        assertFalse(switchedToGoal1[1].showTargetError)

        // 4. Fixing Goal 2's target clears the error and allows + Movement to auto-collapse Goal 2 and append Goal 3 expanded
        drafts = blockedList.toMutableList().also {
            it[1] = it[1].onTargetWeightChanged("50")
        }
        assertFalse("Entering valid target > 0 clears showTargetError", drafts[1].showTargetError)

        val (appendedList, wasAppended) = appendMovementGoalDraft(drafts, newDraft)
        assertTrue(wasAppended)
        assertEquals(3, appendedList.size)
        assertTrue("Goal 1 remains collapsed", appendedList[0].isCollapsed)
        assertTrue("Goal 2 auto-collapses on + Movement", appendedList[1].isCollapsed)
        assertFalse("Newly added Goal 3 opens in Expanded Edit State", appendedList[2].isCollapsed)
    }

    // =========================================================================
    // SPEC-CYCLE-GOAL-04 (013): Target-Only Persistence & Progress Baseline Derivation
    // =========================================================================

    @Test
    fun spec013_us3_targetOnlyGoalsPersistZeroStartWeightAndDeriveHistoricalBaseline() {
        val overheadSquat = Exercise(id = 8L, name = "Overhead Squat", unit = "kg")

        // 1. Target-only GoalDraftState (startWeight = "") persists startWeight = 0.0
        val weightDraft = GoalDraftState(
            exercise = overheadSquat,
            reps = "3",
            startWeight = "",
            targetWeight = "65",
            goalMetric = MetricType.WEIGHT
        )
        val savedWeightGoal = weightDraft.toCycleGoal(cycleId = 10L)!!
        assertEquals(0.0, savedWeightGoal.startWeight, 0.001)
        assertEquals(65.0, savedWeightGoal.targetWeight, 0.001)
        assertEquals("Overhead Squat (3RM): 65kg", savedWeightGoal.formatSummaryChip("Overhead Squat", "kg"))

        val calGoal = GoalDraftState(
            exercise = airBike,
            startWeight = "",
            targetWeight = "50",
            goalMetric = MetricType.CALORIES,
            timeWindowMinutes = 5
        ).toCycleGoal(cycleId = 10L)!!
        assertEquals(0.0, calGoal.startWeight, 0.001)
        assertEquals("Air Bike (5 min): 50 cal", calGoal.formatSummaryChip("Air Bike", "cal"))

        val repsGoal = GoalDraftState(
            exercise = pullUps,
            startWeight = "",
            targetWeight = "20",
            goalMetric = MetricType.REPS
        ).toCycleGoal(cycleId = 10L)!!
        assertEquals(0.0, repsGoal.startWeight, 0.001)
        assertEquals("Pull-ups (Open): 20 reps", repsGoal.formatSummaryChip("Pull-ups", "kg"))

        // 2. evaluateCycleGoalProgress automatically derives baselineStartWeight when startWeight == 0.0
        val cycleStart = LocalDate.of(2026, 11, 1)
        val cycleEnd = LocalDate.of(2026, 12, 27)
        val cycle = Cycle(id = 10L, name = "Winter Peak", startDate = cycleStart, endDate = cycleEnd, isActive = true)

        val priorDate = LocalDate.of(2026, 10, 20)
        val inCycleDate = LocalDate.of(2026, 11, 15)

        val repMaxes = listOf(
            RepMax(id = 1L, exerciseId = overheadSquat.id, reps = 3, weight = 55.0, date = priorDate),
            RepMax(id = 2L, exerciseId = overheadSquat.id, reps = 3, weight = 60.0, date = inCycleDate)
        )

        val priorSession = Session(id = 1L, cycleId = 9L, date = priorDate, title = "Prior Baseline")
        val inCycleSession = Session(id = 2L, cycleId = 10L, date = inCycleDate, title = "Cycle Test")

        val sessions = listOf(
            SessionWithBlocks(
                session = priorSession,
                blocks = listOf(
                    BlockWithSets(
                        block = SessionBlock(id = 10L, sessionId = 1L, position = 0, mainExerciseId = airBike.id),
                        sets = listOf(BlockSet(id = 1L, blockId = 10L, position = 0, reps = 1, metricValue = 40.0))
                    ),
                    BlockWithSets(
                        block = SessionBlock(id = 11L, sessionId = 1L, position = 1, mainExerciseId = pullUps.id),
                        sets = listOf(BlockSet(id = 2L, blockId = 11L, position = 0, reps = 10))
                    )
                )
            ),
            SessionWithBlocks(
                session = inCycleSession,
                blocks = listOf(
                    BlockWithSets(
                        block = SessionBlock(id = 20L, sessionId = 2L, position = 0, mainExerciseId = airBike.id),
                        sets = listOf(BlockSet(id = 3L, blockId = 20L, position = 0, reps = 1, metricValue = 45.0))
                    ),
                    BlockWithSets(
                        block = SessionBlock(id = 21L, sessionId = 2L, position = 1, mainExerciseId = pullUps.id),
                        sets = listOf(BlockSet(id = 4L, blockId = 21L, position = 0, reps = 15))
                    )
                )
            )
        )

        val weightEval = evaluateCycleGoalProgress(savedWeightGoal, cycle, repMaxes, sessions)
        assertEquals(55.0, weightEval.baselineStartWeight, 0.001)
        assertEquals(60.0, weightEval.currentBest, 0.001)
        assertEquals(50, weightEval.progressPercent)

        val calEval = evaluateCycleGoalProgress(calGoal, cycle, repMaxes, sessions)
        assertEquals(40.0, calEval.baselineStartWeight, 0.001)
        assertEquals(45.0, calEval.currentBest, 0.001)
        assertEquals(50, calEval.progressPercent)

        val repsEval = evaluateCycleGoalProgress(repsGoal, cycle, repMaxes, sessions)
        assertEquals(10.0, repsEval.baselineStartWeight, 0.001)
        assertEquals(15.0, repsEval.currentBest, 0.001)
        assertEquals(50, repsEval.progressPercent)
    }

    // =========================================================================
    // SPEC-014 (T004 & T005): Non-Blocking Goal Expansion & Viewport Auto-Scroll
    // =========================================================================

    @Test
    fun spec014_us1_expandGoalDraftAtAutoDiscardsOpenEmptyDraftAndNeverBlocksAfterTwoGoals() {
        val frontSquat = Exercise(id = 6L, name = "Front Squat", unit = "kg")
        val snatch = Exercise(id = 9L, name = "Snatch", unit = "kg")

        // Reproduce the exact scenario from the bug report:
        // Goal 1 (Front Squat, 3RM, Target 65 kg) and Goal 2 (Snatch, 2RM, Target 40 kg) are collapsed
        val goal1 = GoalDraftState(
            id = 0L,
            exercise = frontSquat,
            reps = "3",
            targetWeight = "65",
            goalMetric = MetricType.WEIGHT,
            isCollapsed = true
        )
        val goal2 = GoalDraftState(
            id = 0L,
            exercise = snatch,
            reps = "2",
            targetWeight = "40",
            goalMetric = MetricType.WEIGHT,
            isCollapsed = true
        )

        // User taps "+ Add Movement", appending Goal 3 (Air Bike) with no historical record -> targetWeight = ""
        val emptyNewDraft = GoalDraftState(
            id = 0L,
            exercise = airBike,
            reps = "1",
            goalMetric = MetricType.CALORIES,
            isCollapsed = false
        ).onExerciseChanged(airBike, bestWeight = null)
        assertTrue(emptyNewDraft.isDiscardableEmptyDraft)

        val (withGoal3, wasAppended) = appendMovementGoalDraft(listOf(goal1, goal2), emptyNewDraft)
        assertTrue(wasAppended)
        assertEquals(3, withGoal3.size)
        assertFalse(withGoal3[2].hasValidTarget)
        assertFalse(withGoal3[2].canRestoreValidTarget)

        // When the user taps "▾" on Goal 1 (index 0), the untouched empty Goal 3 is auto-discarded
        // and Goal 1 expands immediately instead of deadlocking!
        val expandedGoal1 = expandGoalDraftAt(withGoal3, 0)
        assertEquals(2, expandedGoal1.size)
        assertFalse("Goal 1 must expand immediately", expandedGoal1[0].isCollapsed)
        assertEquals(frontSquat, expandedGoal1[0].exercise)
        assertTrue("Goal 2 remains collapsed", expandedGoal1[1].isCollapsed)
        assertEquals(snatch, expandedGoal1[1].exercise)

        // Similarly, if an untouched empty draft is at index 0 and the user expands Goal 2 at index 1,
        // index 0 is auto-discarded and Goal 2 becomes the expanded item at index 0.
        val emptyFirstList = listOf(emptyNewDraft, goal1, goal2)
        val expandedAfterLeadingDiscard = expandGoalDraftAt(emptyFirstList, 2)
        assertEquals(2, expandedAfterLeadingDiscard.size)
        assertTrue("Goal 1 stays collapsed at new index 0", expandedAfterLeadingDiscard[0].isCollapsed)
        assertFalse("Goal 2 expands at new index 1", expandedAfterLeadingDiscard[1].isCollapsed)
        assertEquals(1, expandedAfterLeadingDiscard.indexOfFirst { !it.isCollapsed })
    }

    @Test
    fun spec014_us1_expandGoalDraftAtRestoresLastValidTargetWeightWhenSwitchingAwayFromClearedCard() {
        val frontSquat = Exercise(id = 6L, name = "Front Squat", unit = "kg")
        val snatch = Exercise(id = 9L, name = "Snatch", unit = "kg")

        val g1 = GoalDraftState(
            id = 1L,
            exercise = frontSquat,
            reps = "3",
            targetWeight = "65",
            goalMetric = MetricType.WEIGHT,
            isCollapsed = true
        )
        // Goal 2 starts empty, then gets a valid target via stepper / RM lookup, then user clears it to ""
        val g2 = GoalDraftState(
            id = 0L,
            exercise = snatch,
            reps = "2",
            targetWeight = "",
            goalMetric = MetricType.WEIGHT,
            isCollapsed = false
        )
            .stepTarget(40.0)
            .onTargetWeightChanged("")

        assertFalse(g2.hasValidTarget)
        assertTrue(g2.canRestoreValidTarget)
        assertEquals("40", g2.lastValidTargetWeight)
        assertFalse(g2.isDiscardableEmptyDraft)

        // Tapping Goal 1 restores Goal 2's last valid target ("40"), collapses Goal 2, and expands Goal 1
        val result = expandGoalDraftAt(listOf(g1, g2), 0)
        assertEquals(2, result.size)
        assertFalse("Goal 1 expands", result[0].isCollapsed)
        assertTrue("Goal 2 collapses with restored target", result[1].isCollapsed)
        assertEquals("40", result[1].targetWeight)
        assertFalse(result[1].showTargetError)
    }

    @Test
    fun spec014_us1_resolveGoalCardScrollOffsetScrollsClippedCardsIntoViewAndPreservesVisibleOnes() {
        // 1. Newly added Goal 3 below Goal 1 & Goal 2 (clipped below the bottom of a 900px viewport)
        // Viewport = 900px, currentScroll = 0, maxScroll = 800, cardTop = 920, cardHeight = 420 (bottom = 1340)
        val offsetForGoal3 = resolveGoalCardScrollOffset(
            cardTopPx = 920,
            cardHeightPx = 420,
            viewportHeightPx = 900,
            currentScrollPx = 0,
            maxScrollPx = 800,
            topPaddingPx = 24
        )
        // alignBottomOffset = 1340 - 900 + 24 = 464 (keeps card bottom + 24px margin inside 900px viewport)
        assertEquals(464, offsetForGoal3)

        // 2. Card already fully visible inside viewport -> no scroll jump
        val offsetWhenAlreadyVisible = resolveGoalCardScrollOffset(
            cardTopPx = 500,
            cardHeightPx = 180,
            viewportHeightPx = 900,
            currentScrollPx = 400,
            maxScrollPx = 800,
            topPaddingPx = 24
        )
        assertEquals(400, offsetWhenAlreadyVisible)

        // 3. Card clipped above the top of the viewport (e.g. scrolling back up to an expanded/error Goal 1)
        val offsetWhenClippedAbove = resolveGoalCardScrollOffset(
            cardTopPx = 200,
            cardHeightPx = 380,
            viewportHeightPx = 900,
            currentScrollPx = 450,
            maxScrollPx = 800,
            topPaddingPx = 24
        )
        assertEquals(176, offsetWhenClippedAbove)

        // 4. Card taller than the viewport -> aligns top edge (cardTop - topPadding)
        val offsetWhenTallerThanViewport = resolveGoalCardScrollOffset(
            cardTopPx = 600,
            cardHeightPx = 950,
            viewportHeightPx = 800,
            currentScrollPx = 100,
            maxScrollPx = 1000,
            topPaddingPx = 24
        )
        assertEquals(576, offsetWhenTallerThanViewport)
    }
}
