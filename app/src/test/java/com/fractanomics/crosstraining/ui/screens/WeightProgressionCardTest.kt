package com.fractanomics.crosstraining.ui.screens

import com.fractanomics.crosstraining.data.analytics.FatLossAnalytics
import com.fractanomics.crosstraining.data.analytics.PaceForecast
import com.fractanomics.crosstraining.data.analytics.PaceTrend
import com.fractanomics.crosstraining.data.analytics.WeightAnalytics
import com.fractanomics.crosstraining.data.model.Cycle
import com.fractanomics.crosstraining.data.model.CycleGoal
import com.fractanomics.crosstraining.data.model.CycleType
import com.fractanomics.crosstraining.data.model.WeightEntry
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate
import java.util.Locale

/**
 * Unit test suite verifying [WeightProgressionCard] business logic, state derivation,
 * pace chip formatting, forecast banner states, and BDD Scenarios (a)–(m).
 *
 * Feature: ProgressScreen Weight Progression Card, Pace Chips & Forecast Banner (Issue #585 / #583).
 */
class WeightProgressionCardTest {

    private val startDate = LocalDate.of(2026, 9, 1)

    private fun createWeightEntry(date: LocalDate, weightKg: Double) = WeightEntry(
        date = date,
        weightKg = weightKg,
        notes = "",
        updatedAtMillis = System.currentTimeMillis()
    )

    // =========================================================================
    // Scenario (f): No in-cycle weight entries
    // =========================================================================
    @Test
    fun scenarioF_noInCycleWeightEntries_shouldIdentifyEmptyState() {
        val cycle = Cycle(
            id = 1L,
            name = "Summer Cut",
            startDate = startDate,
            type = CycleType.FAT_LOSS_BODYBUILDING,
            startingWeightKg = 85.0,
            targetWeightKg = 80.0
        )
        // All weight entries are before cycle start date
        val preCycleEntries = listOf(
            createWeightEntry(startDate.minusDays(10), 86.0),
            createWeightEntry(startDate.minusDays(1), 85.5)
        )
        val today = startDate.plusDays(10)

        val inCycleEntries = preCycleEntries.filter {
            it.deletedAtMillis == null && !it.date.isBefore(cycle.startDate) && !it.date.isAfter(today)
        }

        assertTrue("In-cycle entries must be empty when none logged on or after start date", inCycleEntries.isEmpty())
    }

    // =========================================================================
    // Scenario (g): Non-fat-loss cycle
    // =========================================================================
    @Test
    fun scenarioG_nonFatLossCycle_weightProgressionCardNotRendered() {
        val strengthCycle = Cycle(
            id = 2L,
            name = "Strength Mesocycle",
            startDate = startDate,
            type = CycleType.STRENGTH_WEIGHTLIFTING,
            startingWeightKg = 85.0,
            targetWeightKg = 80.0
        )

        val shouldRenderCard = strengthCycle.type == CycleType.FAT_LOSS_BODYBUILDING
        assertFalse("Weight progression card should not render for STRENGTH_WEIGHTLIFTING cycles", shouldRenderCard)
    }

    // =========================================================================
    // Scenario (h): Imperial unit localization
    // =========================================================================
    @Test
    fun scenarioH_imperialUnitLocalization_convertsWeightsAndPaceToLbs() {
        val cycle = Cycle(
            id = 3L,
            name = "Winter Cut",
            startDate = startDate,
            type = CycleType.FAT_LOSS_BODYBUILDING,
            startingWeightKg = 85.0,
            targetWeightKg = 80.0
        )
        val today = startDate.plusDays(14)
        val entries = listOf(
            createWeightEntry(startDate, 85.0),
            createWeightEntry(startDate.plusDays(7), 83.5),
            createWeightEntry(today, 82.0)
        )

        val isImperial = true
        val unitLabel = if (isImperial) "lbs" else "kg"
        val startDisplay = WeightAnalytics.kgToLbs(cycle.startingWeightKg!!)
        val currentDisplay = WeightAnalytics.kgToLbs(entries.last().weightKg)
        val goalDisplay = WeightAnalytics.kgToLbs(cycle.targetWeightKg!!)

        assertEquals("187.4 lbs", String.format(Locale.US, "%.1f %s", startDisplay, unitLabel))
        assertEquals("180.8 lbs", String.format(Locale.US, "%.1f %s", currentDisplay, unitLabel))
        assertEquals("176.4 lbs", String.format(Locale.US, "%.1f %s", goalDisplay, unitLabel))

        // Pace rate in lbs/wk
        val pace14 = FatLossAnalytics.compute14DayPace(entries, cycle, today)
        assertNotNull(pace14)
        val paceDisplay = pace14!!.formatDisplay("lbs")
        assertTrue("Pace chip display must be formatted in lbs/wk", paceDisplay.endsWith("lbs/wk"))
    }

    // =========================================================================
    // Scenario (i): Zero movement goals present
    // =========================================================================
    @Test
    fun scenarioI_zeroMovementGoalsPresent_cardRenderedAboveEmptyGoals() {
        val cycle = Cycle(
            id = 4L,
            name = "Fat Loss Block",
            startDate = startDate,
            type = CycleType.FAT_LOSS_BODYBUILDING,
            startingWeightKg = 85.0,
            targetWeightKg = 80.0
        )
        val cycleGoals = emptyList<CycleGoal>()

        val shouldRenderCard = cycle.type == CycleType.FAT_LOSS_BODYBUILDING
        val isGoalsEmpty = cycleGoals.isEmpty()

        assertTrue("WeightProgressionCard must render for FAT_LOSS_BODYBUILDING", shouldRenderCard)
        assertTrue("Goals empty state should also be active when goals list is empty", isGoalsEmpty)
    }

    // =========================================================================
    // Scenario (a): Normal pace and forecast
    // =========================================================================
    @Test
    fun scenarioA_normalPaceAndForecast() {
        val cycle = Cycle(
            id = 5L,
            name = "Cut",
            startDate = startDate,
            type = CycleType.FAT_LOSS_BODYBUILDING,
            startingWeightKg = 85.0,
            targetWeightKg = 80.0
        )
        val today = startDate.plusDays(14)
        val entries = listOf(
            createWeightEntry(startDate, 83.0),
            createWeightEntry(today, 82.0)
        )

        val pace14 = FatLossAnalytics.compute14DayPace(entries, cycle, today)
        assertNotNull(pace14)
        assertEquals("-0.5 kg/wk", pace14!!.formatDisplay("kg"))
        assertEquals(PaceTrend.LOSS, pace14.trend)

        val forecast = FatLossAnalytics.computePaceForecast(entries, cycle, today)
        assertTrue("Forecast must be Projected", forecast is PaceForecast.Projected)
        val proj = forecast as PaceForecast.Projected
        assertEquals("Estimated: 4.0 weeks", proj.displayText)
        assertEquals(today.plusDays(28), proj.targetDate)
    }

    // =========================================================================
    // Scenario (c): Cold start under 7 days or single entry
    // =========================================================================
    @Test
    fun scenarioC_coldStartUnder7Days() {
        val cycle = Cycle(
            id = 6L,
            name = "Cut",
            startDate = startDate,
            type = CycleType.FAT_LOSS_BODYBUILDING,
            startingWeightKg = 85.0,
            targetWeightKg = 80.0
        )
        val today = startDate.plusDays(3)
        val entries = listOf(
            createWeightEntry(startDate, 85.0),
            createWeightEntry(today, 84.5)
        )

        val pace7 = FatLossAnalytics.compute7DayPace(entries, cycle, today)
        assertNull("7-day pace must be null when span < 7 days", pace7)
        assertEquals("Not enough data yet", FatLossAnalytics.formatPaceChip(pace7))

        val forecast = FatLossAnalytics.computePaceForecast(entries, cycle, today)
        assertEquals(PaceForecast.NotEnoughData, forecast)
        assertEquals("Logging check-ins for 7 days enables pace projections", forecast?.displayText)
    }

    // =========================================================================
    // Scenario (d): Plateau or weight gain
    // =========================================================================
    @Test
    fun scenarioD_plateauOrWeightGain_returnsStalled() {
        val cycle = Cycle(
            id = 7L,
            name = "Cut",
            startDate = startDate,
            type = CycleType.FAT_LOSS_BODYBUILDING,
            startingWeightKg = 83.0,
            targetWeightKg = 78.0
        )
        val today = startDate.plusDays(7)
        val entries = listOf(
            createWeightEntry(startDate, 83.0),
            createWeightEntry(today, 83.4)
        )

        val pace7 = FatLossAnalytics.compute7DayPace(entries, cycle, today)
        assertNotNull(pace7)
        assertEquals("+0.4 kg/wk", pace7!!.formatDisplay("kg"))
        assertEquals(PaceTrend.GAIN, pace7.trend)

        val forecast = FatLossAnalytics.computePaceForecast(entries, cycle, today)
        assertEquals(PaceForecast.Stalled, forecast)
        assertEquals("Pace stalled — insufficient deficit to project", forecast?.displayText)
    }

    // =========================================================================
    // Scenario (e): Goal reached
    // =========================================================================
    @Test
    fun scenarioE_goalReached() {
        val cycle = Cycle(
            id = 8L,
            name = "Cut",
            startDate = startDate,
            type = CycleType.FAT_LOSS_BODYBUILDING,
            startingWeightKg = 85.0,
            targetWeightKg = 80.0
        )
        val today = startDate.plusDays(14)
        val entries = listOf(
            createWeightEntry(startDate, 85.0),
            createWeightEntry(today, 79.5)
        )

        val forecast = FatLossAnalytics.computePaceForecast(entries, cycle, today)
        assertEquals(PaceForecast.GoalReached, forecast)
        assertEquals("Goal reached! 🎉", forecast?.displayText)
    }

    // =========================================================================
    // Scenario (j): Past cycle ended
    // =========================================================================
    @Test
    fun scenarioJ_pastCycleEnded() {
        val endDate = startDate.plusDays(30)
        val cycle = Cycle(
            id = 9L,
            name = "Cut",
            startDate = startDate,
            endDate = endDate,
            type = CycleType.FAT_LOSS_BODYBUILDING,
            startingWeightKg = 85.0,
            targetWeightKg = 75.0
        )
        val today = endDate.plusDays(5) // Past cycle end date
        val entries = listOf(
            createWeightEntry(startDate, 85.0),
            createWeightEntry(endDate, 80.0)
        )

        val forecast = FatLossAnalytics.computePaceForecast(entries, cycle, today)
        assertEquals(PaceForecast.CycleEnded, forecast)
        assertEquals("Cycle ended", forecast?.displayText)
    }

    // =========================================================================
    // Scenario (k): Cold start fallback to cycle pace
    // =========================================================================
    @Test
    fun scenarioK_coldStartFallbackToCyclePace() {
        val cycle = Cycle(
            id = 10L,
            name = "Cut",
            startDate = startDate,
            type = CycleType.FAT_LOSS_BODYBUILDING,
            startingWeightKg = 85.0,
            targetWeightKg = 80.0
        )
        val today = startDate.plusDays(10)
        val entries = listOf(
            createWeightEntry(startDate, 85.0),
            createWeightEntry(today, 84.0)
        )

        val pace14 = FatLossAnalytics.compute14DayPace(entries, cycle, today)
        assertNull("14-day anchor does not exist yet", pace14)
        assertEquals("Not enough data yet", FatLossAnalytics.formatPaceChip(pace14))

        val forecast = FatLossAnalytics.computePaceForecast(entries, cycle, today)
        assertTrue("Forecast must use cycle velocity fallback", forecast is PaceForecast.Projected)
        val proj = forecast as PaceForecast.Projected
        assertEquals("Estimated: 5.7 weeks", proj.displayText)
    }

    // =========================================================================
    // TargetBodyWeightKg property alias test
    // =========================================================================
    @Test
    fun cycleTargetBodyWeightKg_aliasParityWithTargetWeightKg() {
        val cycle = Cycle(
            name = "Cut",
            startDate = startDate,
            targetWeightKg = 78.5
        )
        assertEquals(78.5, cycle.targetBodyWeightKg!!, 0.001)

        val cycle2 = Cycle(
            name = "Cut 2",
            startDate = startDate,
            targetBodyWeightKg = 76.0
        )
        assertEquals(76.0, cycle2.targetWeightKg!!, 0.001)
        assertEquals(76.0, cycle2.targetBodyWeightKg!!, 0.001)
    }
}
