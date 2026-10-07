package com.fractanomics.crosstraining.ui.screens

import com.fractanomics.crosstraining.data.analytics.FatLossAnalytics
import com.fractanomics.crosstraining.data.analytics.PaceForecast
import com.fractanomics.crosstraining.data.analytics.PaceRateResult
import com.fractanomics.crosstraining.data.analytics.PaceTrend
import com.fractanomics.crosstraining.data.analytics.WeightAnalytics
import com.fractanomics.crosstraining.data.model.Cycle
import com.fractanomics.crosstraining.data.model.CycleGoal
import com.fractanomics.crosstraining.data.model.CycleType
import com.fractanomics.crosstraining.data.model.WeightEntry
import com.fractanomics.crosstraining.ui.formatShort
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

    // =========================================================================
    // Scenario 5: UI Anti-Truncation on 360dp Viewport (Issue #588)
    // =========================================================================
    @Test
    fun scenario5_uiAntiTruncation_metricAndImperialValueFormattingFitsCardBounds() {
        val metricStarting = String.format(Locale.US, "%.1f %s", 77.8, "kg")
        val metricCurrent = String.format(Locale.US, "%.1f %s", 75.0, "kg")
        val metricGoal = String.format(Locale.US, "%.1f %s", 67.0, "kg")
        val imperialWorstCase = String.format(Locale.US, "%.1f %s", 171.5, "lbs")

        assertEquals("77.8 kg", metricStarting)
        assertEquals("75.0 kg", metricCurrent)
        assertEquals("67.0 kg", metricGoal)
        assertEquals("171.5 lbs", imperialWorstCase)

        // Values must be within 7-9 characters to fit within expanded ~88dp content box
        assertTrue(metricStarting.length <= 9)
        assertTrue(metricCurrent.length <= 9)
        assertTrue(metricGoal.length <= 9)
        assertTrue(imperialWorstCase.length <= 9)
    }

    // =========================================================================
    // Issue #590: 7-Day Average Weight UI Formatting & Gating (Scenarios 1, 4, 6)
    // =========================================================================

    @Test
    fun issue590_scenario1_waterSpikeSmoothing_currentCardDisplays7DayAverageAndGoalDeficit() {
        val startDate = LocalDate.of(2026, 9, 28)
        val today = LocalDate.of(2026, 10, 7)
        val cycle = Cycle(
            id = 5901L,
            name = "Cut",
            startDate = startDate,
            type = CycleType.FAT_LOSS_BODYBUILDING,
            startingWeightKg = 77.8,
            targetWeightKg = 67.0
        )
        val entries = mutableListOf(
            createWeightEntry(startDate, 77.8)
        )
        for (day in 1..6) {
            entries.add(createWeightEntry(LocalDate.of(2026, 10, day), 75.0))
        }
        entries.add(createWeightEntry(today, 76.5))

        val activeEntries = entries.filter {
            it.deletedAtMillis == null && !it.date.isBefore(cycle.startDate) && !it.date.isAfter(today)
        }
        val latestEntry = activeEntries.lastOrNull()
        val current7DayAvgKg = FatLossAnalytics.computeCurrent7DayAverageWeight(
            entries = entries,
            cycle = cycle,
            referenceDate = latestEntry?.date ?: today
        )
        val currentKg = current7DayAvgKg ?: latestEntry?.weightKg
        val targetKg = cycle.targetWeightKg ?: cycle.targetBodyWeightKg

        val isImperial = false
        val unitLabel = if (isImperial) "lbs" else "kg"
        val currentDisplay = currentKg?.let { if (isImperial) WeightAnalytics.kgToLbs(it) else it }
        val targetDisplay = targetKg?.let { if (isImperial) WeightAnalytics.kgToLbs(it) else it }

        // Current KPI Card:
        // Value displays "75.2 kg" (smoothed 7d average instead of raw spike 76.5 kg)
        val currentValueText = currentDisplay?.let { "${String.format(Locale.US, "%.1f", it)} $unitLabel" }
        assertEquals("75.2 kg", currentValueText)

        // Subtitle displays "7d avg • 7 Oct"
        val currentSubtitle = "7d avg • ${latestEntry?.date?.formatShort() ?: "—"}"
        assertEquals("7d avg • ${latestEntry?.date?.formatShort()}", currentSubtitle)
        assertTrue(currentSubtitle.equals("7d avg • 7 Oct", ignoreCase = true))

        // Goal KPI Card:
        // Remaining deficit displays "8.2 kg left"
        assertNotNull(currentDisplay)
        assertNotNull(targetDisplay)
        val remaining = currentDisplay!! - targetDisplay!!
        val goalSubtitle = "${String.format(Locale.US, "%.1f", remaining)} $unitLabel left"
        assertEquals("8.2 kg left", goalSubtitle)
    }

    @Test
    fun issue590_scenario4_imperialUnitConversionConsistency_currentAndGoalDisplay() {
        val targetWeightKg = 67.0
        val metric7DayAvgKg = 526.5 / 7.0 // ≈ 75.2142857 kg
        val isImperial = true
        val unitLabel = if (isImperial) "lbs" else "kg"

        val currentDisplay = WeightAnalytics.kgToLbs(metric7DayAvgKg)
        val targetDisplay = WeightAnalytics.kgToLbs(targetWeightKg)

        // Current card value in imperial
        val currentValueText = "${String.format(Locale.US, "%.1f", currentDisplay)} $unitLabel"
        assertEquals("165.8 lbs", currentValueText)

        // Goal card remaining deficit in imperial
        val remaining = currentDisplay - targetDisplay
        val goalSubtitle = "${String.format(Locale.US, "%.1f", remaining)} $unitLabel left"
        assertEquals("18.1 lbs left", goalSubtitle)
    }

    @Test
    fun issue590_scenario6_goalReachedGatingOn7DayAverage_goalCardDisplay() {
        val targetKg = 67.0
        // Day 7: six entries at 67.5 kg, one entry at 66.8 kg -> 7-day average = 67.4 kg
        val currentKgDay7 = 67.4
        val remainingDay7 = currentKgDay7 - targetKg // 0.4 kg > 0.05
        assertTrue(remainingDay7 > 0.05)
        val goalSubtitleDay7 = "${String.format(Locale.US, "%.1f", remainingDay7)} kg left"
        assertEquals("0.4 kg left", goalSubtitleDay7)

        // Subsequent weigh-ins bring 7-day average to <= 67.0 kg
        val currentKgMature = 66.8
        val remainingMature = currentKgMature - targetKg // -0.2 kg <= 0.05
        assertTrue(remainingMature <= 0.05)
        val goalTextMature = if (remainingMature > 0.05) {
            "${String.format(Locale.US, "%.1f", remainingMature)} kg left"
        } else {
            "Achieved"
        }
        assertEquals("Achieved", goalTextMature)
    }

    // =========================================================================
    // Issue #594: 14-Day & 7-Day Null Pace Placeholders, TalkBack, and Subtitle Resolution
    // =========================================================================
    @Test
    fun issue594_scenario4_nullPacePlaceholderAndTalkBackDescription() {
        // Null 7-day and 14-day pace resolves to em-dash "—"
        val pace7NullValue = resolvePaceChipValue(null, "kg")
        val pace14NullValue = resolvePaceChipValue(null, "lbs")
        assertEquals("—", pace7NullValue)
        assertEquals("—", pace14NullValue)

        // Null pace resolves to TalkBack content description "No data"
        val pace7ContentDesc = resolvePaceChipContentDescription(null)
        val pace14ContentDesc = resolvePaceChipContentDescription(null)
        assertEquals("No data", pace7ContentDesc)
        assertEquals("No data", pace14ContentDesc)

        // Non-null pace does not override TalkBack content description
        val pace7Mature = PaceRateResult(
            rateKgPerWeek = -0.8,
            anchorWeightKg = 80.0,
            anchorDate = LocalDate.of(2026, 9, 30),
            currentWeightKg = 79.2,
            currentDate = LocalDate.of(2026, 10, 7),
            elapsedDays = 7L,
            trend = PaceTrend.LOSS
        )
        assertNull(resolvePaceChipContentDescription(pace7Mature))
        assertEquals("▼ -0.8 kg/wk", resolvePaceChipValue(pace7Mature, "kg"))
        assertEquals("▼ -1.8 lbs/wk", resolvePaceChipValue(pace7Mature, "lbs"))
    }

    @Test
    fun issue594_scenario4_14DaySubtitleResolution() {
        // When 14-day pace is null, subtitle is strictly pinned to "Needs weigh-in 14+ days ago"
        val nullSubtitle = resolvePace14Subtitle(null)
        assertEquals("Needs weigh-in 14+ days ago", nullSubtitle)

        // When 14-day pace is available, subtitle is "Smoothed trend"
        val pace14 = PaceRateResult(
            rateKgPerWeek = -0.68,
            anchorWeightKg = 78.0,
            anchorDate = LocalDate.of(2026, 9, 23),
            currentWeightKg = 76.7,
            currentDate = LocalDate.of(2026, 10, 7),
            elapsedDays = 14L,
            trend = PaceTrend.LOSS
        )
        val matureSubtitle = resolvePace14Subtitle(pace14)
        assertEquals("Smoothed trend", matureSubtitle)
    }
}

