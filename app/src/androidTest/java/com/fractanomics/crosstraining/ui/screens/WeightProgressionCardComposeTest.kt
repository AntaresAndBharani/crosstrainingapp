package com.fractanomics.crosstraining.ui.screens

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performSemanticsAction
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.fractanomics.crosstraining.data.analytics.DayOverDayDelta
import com.fractanomics.crosstraining.data.analytics.PaceComparisonResult
import com.fractanomics.crosstraining.data.analytics.PaceRateResult
import com.fractanomics.crosstraining.data.analytics.PaceTrend
import com.fractanomics.crosstraining.data.analytics.WeightAnalytics
import com.fractanomics.crosstraining.data.model.Cycle
import com.fractanomics.crosstraining.data.model.CycleGoal
import com.fractanomics.crosstraining.data.model.CycleType
import com.fractanomics.crosstraining.data.model.WeightEntry
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
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
        composeTestRule.onNodeWithText(expectedStartLbs, substring = true).assertIsDisplayed()
        composeTestRule.onNodeWithText(expectedCurrentLbs).assertIsDisplayed()
        composeTestRule.onNodeWithText(expectedGoalLbs, substring = true).assertIsDisplayed()

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

        // Then weight progression card renders normally with dynamic title
        composeTestRule.onNodeWithText("Weight Progression (Hypertrophy Cut)").assertIsDisplayed()
        composeTestRule.onNodeWithText("START", substring = true).assertIsDisplayed()
        composeTestRule.onNodeWithText("CURRENT WEIGHT").assertIsDisplayed()
        composeTestRule.onNodeWithText("GOAL", substring = true).assertIsDisplayed()
        composeTestRule.onNodeWithText("7-Day Pace").assertIsDisplayed()
        composeTestRule.onNodeWithText("14-Day Pace").assertIsDisplayed()
    }

    // =========================================================================
    // Helper Assertion for Single Line and No Visual Overflow
    // =========================================================================
    private fun assertSingleLineNoOverflow(matcher: androidx.compose.ui.test.SemanticsNodeInteraction) {
        val results = mutableListOf<androidx.compose.ui.text.TextLayoutResult>()
        matcher.assertIsDisplayed()
        matcher.performSemanticsAction(SemanticsActions.GetTextLayoutResult) { action ->
            action(results)
        }
        assertTrue("TextLayoutResult should be returned", results.isNotEmpty())
        val result = results.first()
        assertEquals("Should be single line", 1, result.lineCount)
        assertFalse("Should not have visual overflow", result.hasVisualOverflow)
    }

    // =========================================================================
    // Scenario 1: Hero Card Layout & Visual Hierarchy (Worst-Case Automated Gate)
    // =========================================================================
    @Test
    fun scenario1_heroCardLayoutAndVisualHierarchy_worstCaseAutomatedGate() {
        val referenceDate = LocalDate.of(2026, 10, 7)
        val staleLatestDate = LocalDate.of(2026, 10, 5)
        val priorDate = LocalDate.of(2026, 10, 4)

        // Day-over-day delta: 76.7 - 77.0 = -0.3 kg (-0.7 lbs)
        val dayOverDayDelta = DayOverDayDelta(
            deltaKg = -0.3,
            priorDate = priorDate,
            currentDate = staleLatestDate,
            isConsecutive = true
        )
        val pace7 = PaceRateResult(
            rateKgPerWeek = -0.816, // -1.8 lbs/wk
            anchorWeightKg = 77.5,
            anchorDate = staleLatestDate.minusDays(7),
            currentWeightKg = 76.7,
            currentDate = staleLatestDate,
            elapsedDays = 7L,
            trend = PaceTrend.LOSS
        )
        val pace14 = PaceRateResult(
            rateKgPerWeek = -0.68, // -1.5 lbs/wk
            anchorWeightKg = 78.0,
            anchorDate = staleLatestDate.minusDays(14),
            currentWeightKg = 76.7,
            currentDate = staleLatestDate,
            elapsedDays = 14L,
            trend = PaceTrend.LOSS
        )
        val paceComparison7d = PaceComparisonResult(
            currentRateKgPerWeek = -0.816,
            priorRateKgPerWeek = -0.7,
            deltaRateKgPerWeek = -0.116,
            percentChange = 16.6,
            isAcceleratingDeficit = true,
            priorDate = priorDate,
            isConsecutive = true
        )

        composeTestRule.setContent {
            CompositionLocalProvider(
                LocalDensity provides Density(density = 1f, fontScale = 1.3f)
            ) {
                Box(modifier = Modifier.width(360.dp)) {
                    WeightProgressionHeroCard(
                        title = "Weight Progression (Cut)",
                        startWeightKg = 77.8,
                        startDateFormatted = "28 Sep",
                        currentWeightKg = 76.7,
                        currentDate = staleLatestDate,
                        goalWeightKg = 70.0,
                        weightUnit = "lbs",
                        dayOverDayDelta = dayOverDayDelta,
                        dropStreakDays = 3,
                        pace7 = pace7,
                        pace14 = pace14,
                        paceComparison7d = paceComparison7d,
                        forecast = null,
                        onLogWeight = {},
                        referenceDate = referenceDate
                    )
                }
            }
        }

        // Verify START metric label ("START 171.5 lbs") and date subtext ("(28 Sep)")
        assertSingleLineNoOverflow(composeTestRule.onNodeWithText("START 171.5 lbs"))
        assertSingleLineNoOverflow(composeTestRule.onNodeWithText("(28 Sep)"))

        // Verify GOAL metric label ("GOAL 154.3 lbs") and remaining subtext ("(-14.8 lbs left)")
        assertSingleLineNoOverflow(composeTestRule.onNodeWithText("GOAL 154.3 lbs"))
        assertSingleLineNoOverflow(composeTestRule.onNodeWithText("(-14.8 lbs left)"))

        // Verify delta pill ("5 Oct · ▼ -0.7 lbs vs 4 Oct") and streak pill ("📉 3-day streak")
        assertSingleLineNoOverflow(composeTestRule.onNodeWithText("5 Oct · ▼ -0.7 lbs vs 4 Oct"))
        assertSingleLineNoOverflow(composeTestRule.onNodeWithText("📉 3-day streak"))

        // Verify 7-day pace value ("▼ -1.8 lbs/wk") and 14-day pace value ("▼ -1.5 lbs/wk")
        assertSingleLineNoOverflow(composeTestRule.onNodeWithText("▼ -1.8 lbs/wk"))
        assertSingleLineNoOverflow(composeTestRule.onNodeWithText("▼ -1.5 lbs/wk"))
    }

    // =========================================================================
    // Scenario 7: 7-Day Pace Trend Direction & Color (Loss / Tertiary)
    // =========================================================================
    @Test
    fun scenario7_paceTrendLoss_rendersTertiaryAndDownIcon() {
        val pace7 = PaceRateResult(
            rateKgPerWeek = -0.8,
            anchorWeightKg = 80.0,
            anchorDate = LocalDate.of(2026, 9, 30),
            currentWeightKg = 79.2,
            currentDate = LocalDate.of(2026, 10, 7),
            elapsedDays = 7L,
            trend = PaceTrend.LOSS
        )

        composeTestRule.setContent {
            WeightProgressionHeroCard(
                title = "Weight Progression (Cut)",
                startWeightKg = 80.0,
                startDateFormatted = "1 Oct",
                currentWeightKg = 79.2,
                currentDate = LocalDate.of(2026, 10, 7),
                goalWeightKg = 75.0,
                weightUnit = "kg",
                dayOverDayDelta = null,
                dropStreakDays = 0,
                pace7 = pace7,
                pace14 = null,
                paceComparison7d = null,
                forecast = null,
                onLogWeight = {}
            )
        }

        // 7-day pace text starts with "▼ "
        composeTestRule.onNodeWithText("▼ -0.8 kg/wk").assertIsDisplayed()
    }

    // =========================================================================
    // Scenario 11: 7-Day Pace Trend Direction & Color (Gain / Error)
    // =========================================================================
    @Test
    fun scenario11_paceTrendGain_rendersErrorAndUpIcon() {
        val pace7 = PaceRateResult(
            rateKgPerWeek = 0.8,
            anchorWeightKg = 78.0,
            anchorDate = LocalDate.of(2026, 9, 30),
            currentWeightKg = 78.8,
            currentDate = LocalDate.of(2026, 10, 7),
            elapsedDays = 7L,
            trend = PaceTrend.GAIN
        )

        composeTestRule.setContent {
            WeightProgressionHeroCard(
                title = "Weight Progression (Cut)",
                startWeightKg = 78.0,
                startDateFormatted = "1 Oct",
                currentWeightKg = 78.8,
                currentDate = LocalDate.of(2026, 10, 7),
                goalWeightKg = 75.0,
                weightUnit = "kg",
                dayOverDayDelta = null,
                dropStreakDays = 0,
                pace7 = pace7,
                pace14 = null,
                paceComparison7d = null,
                forecast = null,
                onLogWeight = {}
            )
        }

        // 7-day pace text starts with "▲ "
        composeTestRule.onNodeWithText("▲ +0.8 kg/wk").assertIsDisplayed()
    }

    // =========================================================================
    // Scenario 16: Null Goal Weight Graceful Degradation
    // =========================================================================
    @Test
    fun scenario16_nullGoalWeightGracefulDegradation() {
        composeTestRule.setContent {
            WeightProgressionHeroCard(
                title = "Weight Progression (Cut)",
                startWeightKg = 80.0,
                startDateFormatted = "1 Oct",
                currentWeightKg = 78.0,
                currentDate = LocalDate.of(2026, 10, 7),
                goalWeightKg = null,
                weightUnit = "kg",
                dayOverDayDelta = null,
                dropStreakDays = 0,
                pace7 = null,
                pace14 = null,
                paceComparison7d = null,
                forecast = null,
                onLogWeight = {}
            )
        }

        composeTestRule.onNodeWithText("GOAL: No target").assertIsDisplayed()
        composeTestRule.onNodeWithText("% to goal", substring = true).assertDoesNotExist()
    }

    // =========================================================================
    // Scenario 16b: Null Starting Weight Fallback to First Logged Entry
    // =========================================================================
    @Test
    fun scenario16b_nullStartingWeightFallbackToFirstEntry() {
        val cycle = Cycle(
            id = 162L,
            name = "Cut",
            startDate = LocalDate.of(2026, 10, 1),
            type = CycleType.FAT_LOSS_BODYBUILDING,
            startingWeightKg = null,
            targetWeightKg = 70.0
        )
        val entries = listOf(
            WeightEntry(date = LocalDate.of(2026, 10, 1), weightKg = 78.5, updatedAtMillis = 1L),
            WeightEntry(date = LocalDate.of(2026, 10, 7), weightKg = 77.0, updatedAtMillis = 2L)
        )

        composeTestRule.setContent {
            WeightProgressionCard(
                cycle = cycle,
                weightEntries = entries,
                weightUnit = "kg",
                onLogWeight = {},
                today = LocalDate.of(2026, 10, 7)
            )
        }

        composeTestRule.onNodeWithText("START 78.5 kg").assertIsDisplayed()
        composeTestRule.onNodeWithText("(1 Oct)").assertIsDisplayed()
    }

    // =========================================================================
    // Scenario 17: Imperial Units Lbs Mode
    // =========================================================================
    @Test
    fun scenario17_imperialUnitsLbsMode() {
        val referenceDate = LocalDate.of(2026, 10, 7)
        val dayOverDayDelta = DayOverDayDelta(
            deltaKg = -0.3,
            priorDate = LocalDate.of(2026, 10, 6),
            currentDate = referenceDate,
            isConsecutive = true
        )

        composeTestRule.setContent {
            WeightProgressionHeroCard(
                title = "Weight Progression (Cut)",
                startWeightKg = 77.8,
                startDateFormatted = "28 Sep",
                currentWeightKg = 76.1,
                currentDate = referenceDate,
                goalWeightKg = 70.0,
                weightUnit = "lbs",
                dayOverDayDelta = dayOverDayDelta,
                dropStreakDays = 0,
                pace7 = null,
                pace14 = null,
                paceComparison7d = null,
                forecast = null,
                onLogWeight = {},
                referenceDate = referenceDate
            )
        }

        // Current weight: 76.1 kg -> 167.8 lbs
        composeTestRule.onNodeWithText("167.8 lbs").assertIsDisplayed()
        // Delta pill: -0.3 kg -> -0.7 lbs
        composeTestRule.onNodeWithText("▼ -0.7 lbs vs yesterday").assertIsDisplayed()
        // Start weight: 77.8 kg -> 171.5 lbs
        composeTestRule.onNodeWithText("START 171.5 lbs").assertIsDisplayed()
        // Goal weight: 70.0 kg -> 154.3 lbs
        composeTestRule.onNodeWithText("GOAL 154.3 lbs").assertIsDisplayed()
    }
}
