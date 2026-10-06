package com.fractanomics.crosstraining.ui.screens

import com.fractanomics.crosstraining.data.analytics.FatLossAnalytics
import com.fractanomics.crosstraining.data.model.Cycle
import com.fractanomics.crosstraining.data.model.CycleGoal
import com.fractanomics.crosstraining.data.model.CycleType
import com.fractanomics.crosstraining.data.model.Exercise
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate

/**
 * Unit tests verifying CyclesScreen Goal Dialog RM Auto-Fill, Modifier Chips,
 * Invariants & Historical Resolution (Issue #577 / Pattern B - Slice 3).
 *
 * BDD Acceptance Criteria Covered:
 * - Scenario 1: RM Auto-Fill on Exercise Selection (1RM = 100kg -> startWeight = 100, targetWeight = 100)
 * - Scenario 2: Reps Change Re-Fetches RM Without Interpolation (5RM = 85kg, 8RM = null -> cleared)
 * - Scenario 3: Modifier Chips (+2.5%, +5%, +10%) and 0% Reset with 0.5 Half-Up Rounding & Disabled on Empty
 * - Scenario 4: Reopening Existing Cycle Preserves Stored Values (no overwrite on reopen)
 * - Scenario 8: Non-Positive & Invalid Target Weight Handling (reject <= 0, validate target >= start)
 */
class CyclesScreenTest {

    private val backSquat = Exercise(id = 1L, name = "Back Squat", unit = "kg")
    private val benchPress = Exercise(id = 2L, name = "Bench Press", unit = "kg")

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
        // Given a starting weight of 80.0 kg in Fat Loss mode
        // When the user enters 0 or a negative weight
        // Then the input is rejected inline and the field remains empty.
        assertEquals("", FatLossValidation.sanitizeInlineWeightInput("0"))
        assertEquals("", FatLossValidation.sanitizeInlineWeightInput("0.0"))
        assertEquals("", FatLossValidation.sanitizeInlineWeightInput("-1"))
        assertEquals("", FatLossValidation.sanitizeInlineWeightInput("-80.0"))

        // Positive weights are accepted intact
        assertEquals("80.0", FatLossValidation.sanitizeInlineWeightInput("80.0"))
        assertEquals("75.5", FatLossValidation.sanitizeInlineWeightInput("75.5"))
    }

    @Test
    fun scenario8_targetWeightGreaterOrEqualToStartWeightTriggersValidationError() {
        // Given starting weight 80.0 kg
        val startWeight = 80.0

        // When the user enters a target weight of 82.0 kg (>= 80.0 kg)
        // Then the form indicates an inline validation error.
        assertTrue(
            "Target weight 82.0 >= 80.0 must be flagged as invalid",
            FatLossValidation.isTargetWeightInvalid(startWeight, 82.0)
        )
        assertTrue(
            "Target weight equal to start weight (80.0) must be flagged as invalid",
            FatLossValidation.isTargetWeightInvalid(startWeight, 80.0)
        )

        // Valid target weight (< 80.0 kg)
        assertFalse(
            "Target weight 75.0 < 80.0 is valid",
            FatLossValidation.isTargetWeightInvalid(startWeight, 75.0)
        )

        // When FatLossAnalytics evaluates progress with W_start = 80.0 and W_target = 82.0
        // Then percent-to-target returns "n/a" (null)
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
}
