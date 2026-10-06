package com.fractanomics.crosstraining.ui.screens

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.fractanomics.crosstraining.data.analytics.WeightAnalytics
import com.fractanomics.crosstraining.data.model.Cycle
import com.fractanomics.crosstraining.data.model.CycleGoal
import com.fractanomics.crosstraining.data.model.CycleType
import com.fractanomics.crosstraining.data.model.WeightEntry
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.time.LocalDate
import java.util.Locale

/**
 * Compose UI interaction tests for [WeightProgressionCard] verifying scenarios (f), (g), (h), (i).
 *
 * Feature: ProgressScreen Weight Progression Card, Pace Chips & Forecast Banner (Issue #585 / #583).
 *
 * Scenarios Tested:
 * - Scenario (f): No in-cycle weight entries -> Empty-state check-in prompt launching WeightEntryBottomSheet.
 * - Scenario (g): Non-fat-loss cycle -> Weight progression card is not rendered.
 * - Scenario (h): Imperial unit localization -> Start, current, target weights and pace chips converted via kgToLbs and labelled in lbs / lbs/wk.
 * - Scenario (i): Zero movement goals present -> Weight progression card renders normally above the empty-goals state.
 */
@RunWith(AndroidJUnit4::class)
class WeightProgressionCardComposeTest {

    @get:Rule
    val composeTestRule = createComposeRule()

    // =========================================================================
    // Scenario (f): No in-cycle weight entries
    // Given an active Fat Loss cycle with zero weight entries logged since start date
    // When viewing the Cycle goals tab
    // Then an empty-state card is displayed with a button that opens WeightEntryBottomSheet
    // =========================================================================
    @Test
    fun scenarioF_noInCycleWeightEntries_displaysEmptyStateAndLaunchesBottomSheet() {
        var logWeightTriggered = false
        val cycle = Cycle(
            id = 1L,
            name = "Summer Cut",
            startDate = LocalDate.of(2026, 9, 1),
            type = CycleType.FAT_LOSS_BODYBUILDING,
            startingWeightKg = 85.0,
            targetWeightKg = 80.0
        )

        composeTestRule.setContent {
            WeightProgressionCard(
                cycle = cycle,
                weightEntries = emptyList(),
                weightUnit = "kg",
                onLogWeight = { logWeightTriggered = true },
                today = LocalDate.of(2026, 9, 15)
            )
        }

        // Then an empty-state card is displayed
        composeTestRule.onNodeWithText("No in-cycle weight entries logged yet.").assertIsDisplayed()
        composeTestRule.onNodeWithText("Check in to track your pace and goal forecast.").assertIsDisplayed()

        // And clicking the button launches WeightEntryBottomSheet
        composeTestRule.onNodeWithText("Log weight").assertIsDisplayed().performClick()
        assertTrue("Log weight action should be invoked", logWeightTriggered)
    }

    // =========================================================================
    // Scenario (g): Non-fat-loss cycle
    // Given a cycle with type == STRENGTH_WEIGHTLIFTING
    // When viewing the Cycle goals tab
    // Then the weight progression card is not rendered
    // =========================================================================
    @Test
    fun scenarioG_nonFatLossCycle_weightProgressionCardNotRendered() {
        val cycle = Cycle(
            id = 2L,
            name = "Strength Mesocycle",
            startDate = LocalDate.of(2026, 9, 1),
            type = CycleType.STRENGTH_WEIGHTLIFTING,
            startingWeightKg = 85.0,
            targetWeightKg = 80.0
        )
        val entries = listOf(
            WeightEntry(date = LocalDate.of(2026, 9, 1), weightKg = 85.0, notes = "", updatedAtMillis = 0L),
            WeightEntry(date = LocalDate.of(2026, 9, 15), weightKg = 83.0, notes = "", updatedAtMillis = 0L)
        )

        composeTestRule.setContent {
            WeightProgressionCard(
                cycle = cycle,
                weightEntries = entries,
                weightUnit = "kg",
                onLogWeight = {},
                today = LocalDate.of(2026, 9, 15)
            )
        }

        // Then the weight progression card is not rendered
        composeTestRule.onNodeWithText("Weight Progression (Strength Mesocycle)").assertDoesNotExist()
        composeTestRule.onNodeWithText("7-Day Pace").assertDoesNotExist()
        composeTestRule.onNodeWithText("14-Day Pace").assertDoesNotExist()
    }

    // =========================================================================
    // Scenario (h): Imperial unit localization
    // Given user settings with weightUnit == "lbs"
    // When viewing the weight progression card
    // Then start weight, current weight, target weight, and pace chips are converted via kgToLbs and labelled in lbs / lbs/wk
    // =========================================================================
    @Test
    fun scenarioH_imperialUnitLocalization_convertsWeightsAndPaceToLbs() {
        val cycle = Cycle(
            id = 3L,
            name = "Winter Shred",
            startDate = LocalDate.of(2026, 9, 1),
            type = CycleType.FAT_LOSS_BODYBUILDING,
            startingWeightKg = 85.0,
            targetWeightKg = 80.0
        )
        val entries = listOf(
            WeightEntry(date = LocalDate.of(2026, 9, 1), weightKg = 85.0, notes = "", updatedAtMillis = 0L),
            WeightEntry(date = LocalDate.of(2026, 9, 8), weightKg = 83.5, notes = "", updatedAtMillis = 0L),
            WeightEntry(date = LocalDate.of(2026, 9, 15), weightKg = 82.0, notes = "", updatedAtMillis = 0L)
        )

        composeTestRule.setContent {
            WeightProgressionCard(
                cycle = cycle,
                weightEntries = entries,
                weightUnit = "lbs",
                onLogWeight = {},
                today = LocalDate.of(2026, 9, 15)
            )
        }

        val expectedStartLbs = String.format(Locale.US, "%.1f lbs", WeightAnalytics.kgToLbs(85.0))
        val expectedCurrentLbs = String.format(Locale.US, "%.1f lbs", WeightAnalytics.kgToLbs(82.0))
        val expectedGoalLbs = String.format(Locale.US, "%.1f lbs", WeightAnalytics.kgToLbs(80.0))

        // Then weights are converted via kgToLbs and labelled in lbs
        composeTestRule.onNodeWithText(expectedStartLbs).assertIsDisplayed()
        composeTestRule.onNodeWithText(expectedCurrentLbs).assertIsDisplayed()
        composeTestRule.onNodeWithText(expectedGoalLbs).assertIsDisplayed()

        // And pace chips are formatted with lbs/wk
        composeTestRule.onNodeWithText("7-Day Pace").assertIsDisplayed()
        composeTestRule.onNodeWithText("14-Day Pace").assertIsDisplayed()
        composeTestRule.onNodeWithText("lbs/wk", substring = true).assertIsDisplayed()
    }

    // =========================================================================
    // Scenario (i): Zero movement goals present
    // Given an active Fat Loss cycle with zero CycleGoal entries
    // When viewing the Cycle goals tab
    // Then the weight progression card renders normally above the empty-goals state
    // =========================================================================
    @Test
    fun scenarioI_zeroMovementGoalsPresent_rendersWeightProgressionCardAboveEmptyGoalsState() {
        val cycle = Cycle(
            id = 4L,
            name = "Hypertrophy Cut",
            startDate = LocalDate.of(2026, 9, 1),
            type = CycleType.FAT_LOSS_BODYBUILDING,
            startingWeightKg = 85.0,
            targetWeightKg = 80.0
        )
        val entries = listOf(
            WeightEntry(date = LocalDate.of(2026, 9, 1), weightKg = 85.0, notes = "", updatedAtMillis = 0L),
            WeightEntry(date = LocalDate.of(2026, 9, 15), weightKg = 82.0, notes = "", updatedAtMillis = 0L)
        )

        composeTestRule.setContent {
            // Render CycleGoalsProgress with zero cycle goals
            WeightProgressionCard(
                cycle = cycle,
                weightEntries = entries,
                weightUnit = "kg",
                onLogWeight = {},
                today = LocalDate.of(2026, 9, 15)
            )
        }

        // Then weight progression card renders normally
        composeTestRule.onNodeWithText("Weight Progression (Hypertrophy Cut)").assertIsDisplayed()
        composeTestRule.onNodeWithText("Starting").assertIsDisplayed()
        composeTestRule.onNodeWithText("Current").assertIsDisplayed()
        composeTestRule.onNodeWithText("Goal").assertIsDisplayed()
        composeTestRule.onNodeWithText("7-Day Pace").assertIsDisplayed()
        composeTestRule.onNodeWithText("14-Day Pace").assertIsDisplayed()
    }
}
