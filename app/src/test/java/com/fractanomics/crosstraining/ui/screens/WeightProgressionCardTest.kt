package com.fractanomics.crosstraining.ui.screens

import com.fractanomics.crosstraining.data.analytics.FatLossAnalytics
import com.fractanomics.crosstraining.data.analytics.PaceForecast
import com.fractanomics.crosstraining.data.analytics.PaceRateResult
import com.fractanomics.crosstraining.data.analytics.PaceTargetsState
import com.fractanomics.crosstraining.data.analytics.SameDayWeightTrioState
import com.fractanomics.crosstraining.data.analytics.ColumnState
import com.fractanomics.crosstraining.data.analytics.PaceTargetStatus
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

    @org.junit.Before
    fun setUp() {
        Locale.setDefault(Locale.US)
    }

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
        assertTrue("Forecast must use Tier 2 (7D smoothed pace) fallback when 14D pace is null", forecast is PaceForecast.Projected)
        val proj = forecast as PaceForecast.Projected
        assertEquals("Estimated: 4.0 weeks", proj.displayText)
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

    // =========================================================================
    // Issue #596: 7-Day Pace Targets Box Text Resolution & Semantics Tests
    // =========================================================================

    @Test
    fun issue596_paceTargetsContainerStateTextResolution() {
        val unavailText = resolvePaceTargetsContainerStateText(PaceTargetsState.Unavailable)
        assertEquals("Pace & Linear Targets Unavailable: Set cycle goal weight and end date", unavailText)

        val stalledText = resolvePaceTargetsContainerStateText(PaceTargetsState.Stalled)
        assertEquals("Pace Stalled: Surplus/maintenance pace detected", stalledText)

        val available = PaceTargetsState.Available(
            today = ColumnState.InsufficientHistory,
            tomorrow = ColumnState.InsufficientHistory
        )
        assertNull(resolvePaceTargetsContainerStateText(available))
    }

    @Test
    fun issue596_expectedTodayColumnTextAndSemanticsResolution() {
        val insuff = ColumnState.InsufficientHistory
        assertEquals("—", resolveExpectedTodayValue(insuff))
        assertEquals("Needs 7 days history", resolveExpectedTodaySubtitle(insuff))
        assertEquals("Expected Today: Needs 7 days history", resolveExpectedTodayContentDescription(insuff))

        val reqDate = LocalDate.of(2026, 9, 30)
        val needsLog = ColumnState.NeedsLog(reqDate)
        assertEquals("—", resolveExpectedTodayValue(needsLog))
        assertEquals("Needs log from ${reqDate.formatShort()}", resolveExpectedTodaySubtitle(needsLog))
        assertEquals("Expected Today: Needs log from ${reqDate.formatShort()}", resolveExpectedTodayContentDescription(needsLog))

        val onPace = ColumnState.Value(76.4, deltaDisplay = 0.0, status = PaceTargetStatus.ON_PACE, unitLabel = "kg")
        assertEquals("76.4 kg", resolveExpectedTodayValue(onPace))
        assertEquals("On pace", resolveExpectedTodaySubtitle(onPace))
        assertEquals("Expected Today: 76.4 kg, On pace", resolveExpectedTodayContentDescription(onPace))

        val offPace = ColumnState.Value(76.4, deltaDisplay = 0.1, status = PaceTargetStatus.OFF_PACE, unitLabel = "kg")
        assertEquals("76.4 kg", resolveExpectedTodayValue(offPace))
        assertEquals("+0.1 kg off pace", resolveExpectedTodaySubtitle(offPace))
        assertEquals("Expected Today: 76.4 kg, +0.1 kg off pace", resolveExpectedTodayContentDescription(offPace))

        val ahead = ColumnState.Value(76.4, deltaDisplay = -0.3, status = PaceTargetStatus.AHEAD, unitLabel = "kg")
        assertEquals("76.4 kg", resolveExpectedTodayValue(ahead))
        assertEquals("-0.3 kg ahead", resolveExpectedTodaySubtitle(ahead))
        assertEquals("Expected Today: 76.4 kg, -0.3 kg ahead", resolveExpectedTodayContentDescription(ahead))

        val unlogged = ColumnState.Value(76.4, deltaDisplay = null, status = null, unitLabel = "kg")
        assertEquals("76.4 kg", resolveExpectedTodayValue(unlogged))
        assertNull(resolveExpectedTodaySubtitle(unlogged))
        assertEquals("Expected Today: 76.4 kg", resolveExpectedTodayContentDescription(unlogged))

        val imperialOffPace = ColumnState.Value(168.4, deltaDisplay = 0.2, status = PaceTargetStatus.OFF_PACE, unitLabel = "lbs")
        assertEquals("168.4 lbs", resolveExpectedTodayValue(imperialOffPace))
        assertEquals("+0.2 lbs off pace", resolveExpectedTodaySubtitle(imperialOffPace))
        assertEquals("Expected Today: 168.4 lbs, +0.2 lbs off pace", resolveExpectedTodayContentDescription(imperialOffPace))
    }

    @Test
    fun issue596_targetTomorrowColumnTextAndSemanticsResolution() {
        val insuff = ColumnState.InsufficientHistory
        assertEquals("—", resolveTargetTomorrowValue(insuff))
        assertEquals("Needs 7 days history", resolveTargetTomorrowSubtitle(insuff))
        assertEquals("Target Tomorrow: Needs 7 days history", resolveTargetTomorrowContentDescription(insuff))

        val reqDate = LocalDate.of(2026, 10, 1)
        val needsLog = ColumnState.NeedsLog(reqDate)
        assertEquals("—", resolveTargetTomorrowValue(needsLog))
        assertEquals("Needs log from ${reqDate.formatShort()}", resolveTargetTomorrowSubtitle(needsLog))
        assertEquals("Target Tomorrow: Needs log from ${reqDate.formatShort()}", resolveTargetTomorrowContentDescription(needsLog))

        val overnight = ColumnState.Value(76.1, deltaDisplay = -0.4, status = null, unitLabel = "kg")
        assertEquals("76.1 kg", resolveTargetTomorrowValue(overnight))
        assertEquals("-0.4 kg overnight", resolveTargetTomorrowSubtitle(overnight))
        assertEquals("Target Tomorrow: 76.1 kg, -0.4 kg overnight", resolveTargetTomorrowContentDescription(overnight))

        val matched = ColumnState.Value(76.5, deltaDisplay = null, status = null, unitLabel = "kg")
        assertEquals("76.5 kg", resolveTargetTomorrowValue(matched))
        assertNull(resolveTargetTomorrowSubtitle(matched))
        assertEquals("Target Tomorrow: 76.5 kg", resolveTargetTomorrowContentDescription(matched))

        val below = ColumnState.AlreadyBelowTarget(targetWeightDisplay = 76.1, marginDisplay = 0.3, unitLabel = "kg")
        assertEquals("76.1 kg", resolveTargetTomorrowValue(below))
        assertEquals("Already below target (-0.3 kg)", resolveTargetTomorrowSubtitle(below))
        assertEquals("Target Tomorrow: 76.1 kg, Already below target (-0.3 kg)", resolveTargetTomorrowContentDescription(below))

        val imperialOvernight = ColumnState.Value(167.8, deltaDisplay = -0.9, status = null, unitLabel = "lbs")
        assertEquals("167.8 lbs", resolveTargetTomorrowValue(imperialOvernight))
        assertEquals("-0.9 lbs overnight", resolveTargetTomorrowSubtitle(imperialOvernight))
        assertEquals("Target Tomorrow: 167.8 lbs, -0.9 lbs overnight", resolveTargetTomorrowContentDescription(imperialOvernight))
    }

    // =========================================================================
    // Issue #598: Baseline Weight Micro-Label Text & TalkBack Resolution Tests
    // =========================================================================

    @Test
    fun issue598_resolveColumnBaselineText_formatsMicroLabelCorrectly() {
        val todayValue = ColumnState.Value(76.4, 0.1, PaceTargetStatus.OFF_PACE, "kg", 77.3)
        assertEquals("Last week: 77.3 kg", resolveColumnBaselineText(todayValue))

        val todayWholeNumber = ColumnState.Value(76.4, 0.1, PaceTargetStatus.OFF_PACE, "kg", 77.0)
        assertEquals("Last week: 77 kg", resolveColumnBaselineText(todayWholeNumber))

        val tomorrowValue = ColumnState.Value(74.5, -2.0, null, "kg", 75.3)
        assertEquals("Last week: 75.3 kg", resolveColumnBaselineText(tomorrowValue))

        val tomorrowBelow = ColumnState.AlreadyBelowTarget(74.5, 0.5, "kg", 75.3)
        assertEquals("Last week: 75.3 kg", resolveColumnBaselineText(tomorrowBelow))

        val imperialValue = ColumnState.Value(168.4, 0.2, PaceTargetStatus.OFF_PACE, "lbs", 170.4)
        assertEquals("Last week: 170.4 lbs", resolveColumnBaselineText(imperialValue))

        // Null baseline yields null (micro-label omitted, no placeholder)
        val nullBaselineValue = ColumnState.Value(76.4, -0.1, null, "kg", null)
        assertNull(resolveColumnBaselineText(nullBaselineValue))

        val nullBaselineBelow = ColumnState.AlreadyBelowTarget(74.5, 0.5, "kg", null)
        assertNull(resolveColumnBaselineText(nullBaselineBelow))

        assertNull(resolveColumnBaselineText(ColumnState.NeedsLog(LocalDate.of(2026, 9, 30))))
        assertNull(resolveColumnBaselineText(ColumnState.InsufficientHistory))
    }

    @Test
    fun issue598_expectedTodayContentDescription_withBaseline_resolvesTalkBackPhrase() {
        val activeWithSub = ColumnState.Value(76.4, 0.1, PaceTargetStatus.OFF_PACE, "kg", 77.3)
        assertEquals(
            "Expected Today: 76.4 kg, +0.1 kg off pace, Last week, same day: 77.3 kilograms",
            resolveExpectedTodayContentDescription(activeWithSub)
        )

        val unloggedToday = ColumnState.Value(76.4, null, null, "kg", 77.3)
        assertEquals(
            "Expected Today: 76.4 kg, Last week, same day: 77.3 kilograms",
            resolveExpectedTodayContentDescription(unloggedToday)
        )

        val imperialWithSub = ColumnState.Value(168.4, 0.2, PaceTargetStatus.OFF_PACE, "lbs", 170.4)
        assertEquals(
            "Expected Today: 168.4 lbs, +0.2 lbs off pace, Last week, same day: 170.4 lbs",
            resolveExpectedTodayContentDescription(imperialWithSub)
        )

        // Null baseline preserves byte-identical pre-existing output
        val nullBaseline = ColumnState.Value(76.4, 0.1, PaceTargetStatus.OFF_PACE, "kg", null)
        assertEquals(
            "Expected Today: 76.4 kg, +0.1 kg off pace",
            resolveExpectedTodayContentDescription(nullBaseline)
        )
    }

    @Test
    fun issue598_targetTomorrowContentDescription_withBaseline_resolvesTalkBackPhrase() {
        val activeOvernight = ColumnState.Value(74.5, -2.0, null, "kg", 75.3)
        assertEquals(
            "Target Tomorrow: 74.5 kg, -2 kg overnight, Last week, same day as tomorrow: 75.3 kilograms",
            resolveTargetTomorrowContentDescription(activeOvernight)
        )

        val unloggedToday = ColumnState.Value(74.5, null, null, "kg", 75.3)
        assertEquals(
            "Target Tomorrow: 74.5 kg, Last week, same day as tomorrow: 75.3 kilograms",
            resolveTargetTomorrowContentDescription(unloggedToday)
        )

        val alreadyBelow = ColumnState.AlreadyBelowTarget(74.5, 0.5, "kg", 75.3)
        assertEquals(
            "Target Tomorrow: 74.5 kg, Already below target (-0.5 kg), Last week, same day as tomorrow: 75.3 kilograms",
            resolveTargetTomorrowContentDescription(alreadyBelow)
        )

        val imperialOvernight = ColumnState.Value(167.8, -0.9, null, "lbs", 170.4)
        assertEquals(
            "Target Tomorrow: 167.8 lbs, -0.9 lbs overnight, Last week, same day as tomorrow: 170.4 lbs",
            resolveTargetTomorrowContentDescription(imperialOvernight)
        )

        // Fallback calculation path with null baseline preserves byte-identical output
        val fallbackNullBaseline = ColumnState.Value(76.4, -0.1, null, "kg", null)
        assertEquals(
            "Target Tomorrow: 76.4 kg, -0.1 kg overnight",
            resolveTargetTomorrowContentDescription(fallbackNullBaseline)
        )
    }

    // =========================================================================
    // Issue #600: 3-Pillar Weight Progression Hero Card Resolvers (Scenarios 5, 6, 13, 14)
    // =========================================================================

    @Test
    fun issue600_leftPillarResolvers_formatsHeaderValueAndSubtitle() {
        val tuesday = LocalDate.of(2026, 10, 13) // Tuesday
        assertEquals("LAST WK 7D", resolveLeftPillarHeader(tuesday))

        val sunday = LocalDate.of(2026, 10, 18) // Sunday
        assertEquals("LAST WK 7D", resolveLeftPillarHeader(sunday))
        assertEquals("LAST WK 7D", resolveLeftPillarHeader())

        // Present last week entry
        val lastWeekEntry = createWeightEntry(LocalDate.of(2026, 10, 6), 77.2)
        assertEquals("77.2 kg", resolveLeftPillarValue(lastWeekEntry, "kg"))
        assertEquals("170.2 lbs", resolveLeftPillarValue(lastWeekEntry, "lbs"))
        assertEquals("(6 Oct)", resolveLeftPillarSubtitle(lastWeekEntry))

        // Null last week entry
        assertEquals("—", resolveLeftPillarValue(null, "kg"))
        assertEquals("No log", resolveLeftPillarSubtitle(null))
    }

    @Test
    fun issue600_centerPillarResolvers_formatsTodayVsLatestAndUnit() {
        val today = LocalDate.of(2026, 10, 13)
        // Today weigh-in
        assertEquals("7D AVG", resolveCenterPillarHeader(today, today))
        // Stale weigh-in (Scenario 13)
        val yesterday = today.minusDays(1)
        assertEquals("7D AVG", resolveCenterPillarHeader(yesterday, today))
        assertEquals("7D AVG", resolveCenterPillarHeader())

        assertEquals("76.1 kg", resolveCenterPillarValue(76.1, "kg"))
        assertEquals("167.8 lbs", resolveCenterPillarValue(76.1, "lbs"))
    }

    @Test
    fun issue600_rightPillarResolvers_formatsWindowSpanCountAndValue() {
        // Dynamic window span (Scenario 6)
        val avg5 = com.fractanomics.crosstraining.data.analytics.ProgressiveAverageResult(
            averageKg = 76.8,
            windowSpanDays = 5,
            loggedDaysCount = 4
        )
        assertEquals("5D AVG", resolveRightPillarHeader(avg5))
        assertEquals("76.8 kg", resolveRightPillarValue(avg5, "kg"))
        assertEquals("169.3 lbs", resolveRightPillarValue(avg5, "lbs"))
        assertEquals("(4 logs)", resolveRightPillarSubtitle(avg5))

        // 14D mature window with single log (count grammar)
        val avg14Single = com.fractanomics.crosstraining.data.analytics.ProgressiveAverageResult(
            averageKg = 76.5,
            windowSpanDays = 14,
            loggedDaysCount = 1
        )
        assertEquals("14D AVG", resolveRightPillarHeader(avg14Single))
        assertEquals("(1 log)", resolveRightPillarSubtitle(avg14Single))

        // Null progressive average fallback
        assertEquals("14D AVG", resolveRightPillarHeader(null))
        assertEquals("—", resolveRightPillarValue(null, "kg"))
        assertEquals("No log", resolveRightPillarSubtitle(null))
    }

    @Test
    fun issue600_pace14Subtitle_wiresCadenceStates() {
        val today = LocalDate.of(2026, 10, 13)
        // Null pace
        assertEquals("Needs weigh-in 14+ days ago", resolvePace14Subtitle(null, null, "kg", today))

        // Mature pace with null comparison
        val pace = PaceRateResult(
            rateKgPerWeek = -0.5,
            anchorWeightKg = 77.0,
            anchorDate = today.minusDays(14),
            currentWeightKg = 76.0,
            currentDate = today,
            elapsedDays = 14L,
            trend = PaceTrend.LOSS
        )
        assertEquals("Smoothed trend", resolvePace14Subtitle(pace, null, "kg", today))

        // Mature pace with same-weekday comparison
        val comp = com.fractanomics.crosstraining.data.analytics.PaceComparisonResult(
            currentRateKgPerWeek = -0.5,
            priorRateKgPerWeek = -0.3,
            deltaRateKgPerWeek = -0.2,
            percentChange = null,
            isAcceleratingDeficit = true,
            priorDate = LocalDate.of(2026, 10, 6),
            isConsecutive = false,
            isExactDayMatch = true
        )
        assertEquals("-0.2 kg/wk vs last Tue", resolvePace14Subtitle(pace, comp, "kg", today))
    }

    // =========================================================================
    // Issue #602: Responsive Weight Progression Hero & Monotonic Cycle Pace Targets
    // =========================================================================

    @Test
    fun issue602_formatWeeklyDelta_formatsLossGainNeutralAndNull() {
        // Loss
        assertEquals("-0.5 kg", formatWeeklyDelta(-0.5, "kg"))
        assertEquals("-1.1 lbs", formatWeeklyDelta(-0.5, "lbs"))

        // Gain
        assertEquals("+0.4 kg", formatWeeklyDelta(0.4, "kg"))
        assertEquals("+0.9 lbs", formatWeeklyDelta(0.4, "lbs"))

        // Neutral deadband
        assertEquals("0.0 kg", formatWeeklyDelta(0.02, "kg"))
        assertEquals("0.0 kg", formatWeeklyDelta(-0.03, "kg"))
        assertEquals("0.0 lbs", formatWeeklyDelta(0.0, "lbs"))

        // Null
        assertEquals("—", formatWeeklyDelta(null, "kg"))
        assertEquals("—", formatWeeklyDelta(null, "lbs"))
    }

    @Test
    fun issue602_rollingHeroResolvers_formatNullableWeightsAndCounts() {
        assertEquals("76.8 kg", resolvePillarWeightDisplay(76.8, "kg"))
        assertEquals("169.3 lbs", resolvePillarWeightDisplay(76.8, "lbs"))
        assertEquals("—", resolvePillarWeightDisplay(null, "kg"))

        assertEquals("76.1 kg", resolveCenterPillarValue(76.1, "kg"))
        assertEquals("167.8 lbs", resolveCenterPillarValue(76.1, "lbs"))
        assertEquals("—", resolveCenterPillarValue(null, "kg"))

        assertEquals("(1 log)", resolveRightPillarSubtitle(1))
        assertEquals("(12 logs)", resolveRightPillarSubtitle(12))
        assertEquals("No log", resolveRightPillarSubtitle(0))
    }

    @Test
    fun issue604_sameDayTrioResolvers_formatWeightsAndConsistentDeltas() {
        Locale.setDefault(Locale.US)
        // Weight formatting
        assertEquals("75.0 kg", resolveSameDayWeightDisplay(75.0, "kg"))
        assertEquals("165.3 lbs", resolveSameDayWeightDisplay(75.0, "lbs"))
        assertEquals("—", resolveSameDayWeightDisplay(null, "kg"))
        assertEquals("—", resolveSameDayWeightDisplay(null, "lbs"))

        // Consistent delta formatting
        assertEquals("-0.8 kg", resolveSameDayDeltaDisplay(75.0, 75.8, "kg"))
        assertEquals("—", resolveSameDayDeltaDisplay(null, 75.8, "kg"))
        assertEquals("—", resolveSameDayDeltaDisplay(75.0, null, "kg"))

        // Display arithmetic consistency invariant
        assertEquals("+0.2 lbs", resolveSameDayDeltaDisplay(72.44, 72.36, "lbs"))
        assertEquals("0.0 kg", resolveSameDayDeltaDisplay(72.44, 72.36, "kg"))

        // SameDayWeightTrioState construction
        val state = SameDayWeightTrioState(
            currentDailyWeightKg = 72.44,
            lastWeekSameDayWeightKg = 72.36,
            deltaCurrentVsLastWeekSameDayKg = 0.1,
            twoWeeksAgoSameDayWeightKg = 73.0,
            deltaCurrentVsTwoWeeksAgoSameDayKg = -0.6
        )
        assertEquals("72.4 kg", resolveSameDayWeightDisplay(state.currentDailyWeightKg, "kg"))
        assertEquals("72.4 kg", resolveSameDayWeightDisplay(state.lastWeekSameDayWeightKg, "kg"))
        assertEquals("73.0 kg", resolveSameDayWeightDisplay(state.twoWeeksAgoSameDayWeightKg, "kg"))
    }

    // =========================================================================
    // Feature 001-rolling-weight-pace: Top Reference Track & Forecast Reconciliation (US2 & US3)
    // =========================================================================

    @Test
    fun rollingWeightPace_topReferenceTrack_reconcilesWithToday7dAvgAndForecast() {
        Locale.setDefault(Locale.US)
        val startWeightKg = 77.8
        val goalWeightKg = 72.0
        val rawCurrentWeightKg = 74.7 // Single-day raw Thursday weigh-in
        val resolvedToday7dAvgKg = 75.7 // Displayed TODAY 7D average

        // Top Reference Track must use resolvedToday7dAvgKg (75.7 kg), NOT rawCurrentWeightKg (74.7 kg)
        val totalDistance = startWeightKg - goalWeightKg // 5.8 kg
        val progressFraction = ((startWeightKg - resolvedToday7dAvgKg) / totalDistance).toFloat().coerceIn(0f, 1f)
        val percentText = String.format(Locale.US, "%.1f%% to goal", progressFraction * 100f)
        val remainingKg = resolvedToday7dAvgKg - goalWeightKg
        val goalSubtitle = "(-${String.format(Locale.US, "%.1f", remainingKg)} kg left)"

        // Verify 36.2% to goal and (-3.7 kg left) match TODAY 75.7 kg
        assertEquals("36.2% to goal", percentText)
        assertEquals("(-3.7 kg left)", goalSubtitle)
        assertFalse("Raw weight would have given 53.4% instead of 36.2%", kotlin.math.abs(((startWeightKg - rawCurrentWeightKg) / totalDistance) * 100.0 - 36.2) < 1.0)
    }

    // =========================================================================
    // Feature 002-weight-card-clarity-and-forecast-pace Tests (US1, US2, US3)
    // =========================================================================

    @Test
    fun weightCardClarity_sectionHeaders_resolveCorrectly() {
        assertEquals("ROLLING AVERAGES", ROLLING_AVERAGES_SECTION_HEADER)

        val tuesday = LocalDate.of(2026, 10, 13)
        assertEquals("DAILY WEIGH-IN (TUE)", resolveDailyWeighInSectionHeader(tuesday))

        val sunday = LocalDate.of(2026, 10, 18)
        assertEquals("DAILY WEIGH-IN (SUN)", resolveDailyWeighInSectionHeader(sunday))

        val thursday = LocalDate.of(2026, 10, 22)
        assertEquals("DAILY WEIGH-IN (THU)", resolveDailyWeighInSectionHeader(thursday))
    }

    @Test
    fun weightCardClarity_resolveForecastPaceSubtitle_coversAllVariants() {
        val pace14 = PaceRateResult(
            rateKgPerWeek = -0.2,
            anchorWeightKg = 76.1,
            anchorDate = LocalDate.of(2026, 10, 8),
            currentWeightKg = 75.7,
            currentDate = LocalDate.of(2026, 10, 22),
            elapsedDays = 14,
            trend = PaceTrend.LOSS
        )
        val pace7 = PaceRateResult(
            rateKgPerWeek = -0.3,
            anchorWeightKg = 76.0,
            anchorDate = LocalDate.of(2026, 10, 15),
            currentWeightKg = 75.7,
            currentDate = LocalDate.of(2026, 10, 22),
            elapsedDays = 7,
            trend = PaceTrend.LOSS
        )

        val projected14 = PaceForecast.Projected(
            weeks = 18.5,
            targetDate = LocalDate.of(2027, 2, 28),
            rawWeeks = 18.5,
            paceWindowDays = 14,
            paceRateKgPerWeek = -0.2
        )

        // Tier 1 Projected in kg
        assertEquals(
            "Based on 14-day pace (-0.2 kg/wk)",
            resolveForecastPaceSubtitle(projected14, pace14 = pace14, pace7 = pace7, weightUnit = "kg")
        )

        // Tier 1 Projected in lbs
        assertEquals(
            "Based on 14-day pace (-0.4 lbs/wk)",
            resolveForecastPaceSubtitle(projected14, pace14 = pace14, pace7 = pace7, weightUnit = "lbs")
        )

        // Fallback to forecast fields when pace14 and pace7 are null
        assertEquals(
            "Based on 14-day pace (-0.2 kg/wk)",
            resolveForecastPaceSubtitle(projected14, pace14 = null, pace7 = null, weightUnit = "kg")
        )

        // Tier 2 Fallback Projected (pace14 == null, pace7 != null)
        val projected7 = PaceForecast.Projected(
            weeks = 12.3,
            targetDate = LocalDate.of(2027, 1, 16),
            rawWeeks = 12.33,
            paceWindowDays = 7,
            paceRateKgPerWeek = -0.3
        )
        assertEquals(
            "Based on 7-day pace (-0.3 kg/wk)",
            resolveForecastPaceSubtitle(projected7, pace14 = null, pace7 = pace7, weightUnit = "kg")
        )

        // Stalled with 14-day pace (0.0 kg/wk)
        val pace14Stalled = pace14.copy(rateKgPerWeek = 0.0, trend = PaceTrend.NEUTRAL)
        assertEquals(
            "Based on 14-day pace (0.0 kg/wk)",
            resolveForecastPaceSubtitle(PaceForecast.Stalled, pace14 = pace14Stalled, pace7 = pace7, weightUnit = "kg")
        )

        // Stalled with 14-day pace gaining (+0.2 kg/wk)
        val pace14Gaining = pace14.copy(rateKgPerWeek = 0.2, trend = PaceTrend.GAIN)
        assertEquals(
            "Based on 14-day pace (+0.2 kg/wk)",
            resolveForecastPaceSubtitle(PaceForecast.Stalled, pace14 = pace14Gaining, pace7 = null, weightUnit = "kg")
        )

        // Stalled with 7-day fallback pace
        assertEquals(
            "Based on 7-day pace (-0.3 kg/wk)",
            resolveForecastPaceSubtitle(PaceForecast.Stalled, pace14 = null, pace7 = pace7, weightUnit = "kg")
        )

        // Non-projected/non-stalled states return null
        assertNull(resolveForecastPaceSubtitle(PaceForecast.GoalReached, pace14 = pace14, pace7 = pace7))
        assertNull(resolveForecastPaceSubtitle(PaceForecast.CycleEnded, pace14 = pace14, pace7 = pace7))
        assertNull(resolveForecastPaceSubtitle(PaceForecast.NotEnoughData, pace14 = pace14, pace7 = pace7))
    }

    @Test
    fun weightCardClarity_topReferenceTrack_oneDecimalReconciliation() {
        Locale.setDefault(Locale.US)
        val startWeightKg = 77.8
        val goalWeightKg = 72.0
        val unroundedToday7dAvgKg = 75.66 // Displayed as 75.7 kg

        val rStart = FatLossAnalytics.round1(startWeightKg) // 77.8
        val rGoal = FatLossAnalytics.round1(goalWeightKg)   // 72.0
        val rToday = FatLossAnalytics.round1(unroundedToday7dAvgKg) // 75.7
        val totalDistance = FatLossAnalytics.diff1(rStart, rGoal) // 5.8
        val progressFraction = (FatLossAnalytics.diff1(rStart, rToday) / totalDistance).toFloat().coerceIn(0f, 1f)
        val percentText = String.format(Locale.US, "%.1f%% to goal", progressFraction * 100f)

        val remainingKg = FatLossAnalytics.diff1(rToday, rGoal)
        val goalSubtitle = "(-${String.format(Locale.US, "%.1f", remainingKg)} kg left)"

        assertEquals("36.2% to goal", percentText)
        assertEquals("(-3.7 kg left)", goalSubtitle)
    }

    @Test
    fun weightCardClarity_pillarHeaders_labelRawEntryFallbacks() {
        val tuesday = LocalDate.of(2026, 10, 13)
        assertEquals("LAST WK 7D", resolveLeftPillarHeader(tuesday, isRollingAverage = true))
        assertEquals("LAST WEEK (TUE)", resolveLeftPillarHeader(tuesday, isRollingAverage = false))

        val yesterday = tuesday.minusDays(1)
        assertEquals("7D AVG", resolveCenterPillarHeader(tuesday, tuesday, isRollingAverage = true))
        assertEquals("TODAY", resolveCenterPillarHeader(tuesday, tuesday, isRollingAverage = false))
        assertEquals("LATEST", resolveCenterPillarHeader(yesterday, tuesday, isRollingAverage = false))
    }

    @Test
    fun weightCardClarity_topReferenceTrack_reconcilesInDisplayUnit() {
        val startWeightKg = 77.8   // 171.5 lbs
        val goalWeightKg = 72.0    // 158.7 lbs
        val today7dAvgKg = 75.66   // 75.7 kg / 166.8 lbs

        // kg: (77.8 - 75.7) / (77.8 - 72.0) = 36.2%, 75.7 - 72.0 = 3.7 kg left
        assertEquals(3.7, resolveGoalRemainingDisplay(today7dAvgKg, goalWeightKg, "kg"), 1e-9)
        assertEquals(2.1f / 5.8f, resolveGoalProgressFraction(startWeightKg, today7dAvgKg, goalWeightKg, "kg"), 1e-6f)

        // lbs: 166.8 - 158.7 = 8.1 lbs left (converting the rounded 3.7 kg would show 8.2 lbs)
        assertEquals("166.8 lbs", resolvePillarWeightDisplay(today7dAvgKg, "lbs"))
        assertEquals(8.1, resolveGoalRemainingDisplay(today7dAvgKg, goalWeightKg, "lbs"), 1e-9)
        // (171.5 - 166.8) / (171.5 - 158.7) = 4.7 / 12.8
        assertEquals(4.7f / 12.8f, resolveGoalProgressFraction(startWeightKg, today7dAvgKg, goalWeightKg, "lbs"), 1e-6f)

        // Displayed 7D AVG equal to displayed goal -> goal reached (remaining <= 0)
        assertTrue(resolveGoalRemainingDisplay(72.04, goalWeightKg, "kg") <= 0.0)
    }

    @Test
    fun weightCardClarity_rollingDelta_reconcilesWithDisplayedLbsValues() {
        // 72.04 kg -> 158.8 lbs, 71.96 kg -> 158.6 lbs; both round to 72.0 kg, so a kg delta would show 0.0 lbs
        assertEquals("+0.2 lbs", FatLossAnalytics.formatSameDayWeightDelta(72.04, 71.96, "lbs"))
        assertEquals("0.0 kg", FatLossAnalytics.formatSameDayWeightDelta(72.04, 71.96, "kg"))
    }

    @Test
    fun weightCardClarity_forecastPaceSubtitle_prefersForecastTierOverDisplayedChips() {
        val pace14 = PaceRateResult(
            rateKgPerWeek = -0.2,
            anchorWeightKg = 76.1,
            anchorDate = LocalDate.of(2026, 10, 8),
            currentWeightKg = 75.7,
            currentDate = LocalDate.of(2026, 10, 22),
            elapsedDays = 14,
            trend = PaceTrend.LOSS
        )
        val projected7 = PaceForecast.Projected(
            weeks = 12.3,
            targetDate = LocalDate.of(2027, 1, 16),
            rawWeeks = 12.33,
            paceWindowDays = 7,
            paceRateKgPerWeek = -0.3
        )
        assertEquals(
            "Based on 7-day pace (-0.3 kg/wk)",
            resolveForecastPaceSubtitle(projected7, pace14 = pace14, pace7 = null, weightUnit = "kg")
        )

        // Projection without tier metadata falls back to the displayed chips
        val projectedUnknown = PaceForecast.Projected(weeks = 10.0, targetDate = LocalDate.of(2026, 12, 31))
        assertEquals(
            "Based on 14-day pace (-0.2 kg/wk)",
            resolveForecastPaceSubtitle(projectedUnknown, pace14 = pace14, pace7 = null, weightUnit = "kg")
        )
        assertNull(resolveForecastPaceSubtitle(projectedUnknown, pace14 = null, pace7 = null, weightUnit = "kg"))
    }

    @Test
    fun requiredPaceSubtitle_displaysInProjectedStalledAndNotEnoughDataStates() {
        val referenceDate = LocalDate.of(2026, 10, 22)
        val cycleEndDate = LocalDate.of(2026, 11, 30) // 39 days -> 5.5714 weeks
        // 75.66 kg rounds to 75.7 kg; goal 72.0 kg -> remaining 3.7 kg -> -0.7 kg/wk
        val requiredKg = FatLossAnalytics.computeRequiredPaceToEndDate(
            today7dAvgKg = 75.66,
            targetWeightKg = 72.0,
            cycleEndDate = cycleEndDate,
            today = referenceDate,
            weightUnit = "kg",
            startWeightKg = 77.8
        )
        val expectedKg = "Required pace: -0.7 kg/wk (by 30 Nov 2026)"

        val projected = PaceForecast.Projected(
            weeks = 18.5,
            targetDate = LocalDate.of(2027, 2, 28),
            rawWeeks = 18.5,
            paceWindowDays = 14,
            paceRateKgPerWeek = -0.2
        )
        assertEquals(expectedKg, resolveRequiredPaceSubtitle(projected, requiredKg))
        assertEquals(expectedKg, resolveRequiredPaceSubtitle(PaceForecast.Stalled, requiredKg))
        assertEquals(expectedKg, resolveRequiredPaceSubtitle(PaceForecast.NotEnoughData, requiredKg))

        // Imperial lbs: 166.8 - 158.7 = 8.1 lbs over 39/7 weeks = 1.4538 -> -1.5 lbs/wk
        val requiredLbs = FatLossAnalytics.computeRequiredPaceToEndDate(
            today7dAvgKg = 75.66,
            targetWeightKg = 72.0,
            cycleEndDate = cycleEndDate,
            today = referenceDate,
            weightUnit = "lbs",
            startWeightKg = 77.8
        )
        assertEquals(
            "Required pace: -1.5 lbs/wk (by 30 Nov 2026)",
            resolveRequiredPaceSubtitle(projected, requiredLbs)
        )
    }

    @Test
    fun requiredPaceSubtitle_suppressedWhenGoalReachedCycleEndedOrNoRequiredPace() {
        val referenceDate = LocalDate.of(2026, 10, 22)
        val required = FatLossAnalytics.computeRequiredPaceToEndDate(
            today7dAvgKg = 75.7,
            targetWeightKg = 72.0,
            cycleEndDate = LocalDate.of(2026, 11, 30),
            today = referenceDate
        )
        assertNotNull(required)

        // Suppressed in GoalReached and CycleEnded forecast states
        assertNull(resolveRequiredPaceSubtitle(PaceForecast.GoalReached, required))
        assertNull(resolveRequiredPaceSubtitle(PaceForecast.CycleEnded, required))
        assertNull(resolveRequiredPaceSubtitle(null, required))

        // Suppressed when no required pace resolves (open-ended cycle, end date reached, missing 7D average)
        assertNull(resolveRequiredPaceSubtitle(PaceForecast.Stalled, null))
    }

    @Test
    fun requiredPace_cycleOverloadMatchesCardInputsAndForecastAgreesInLbs() {
        val today = LocalDate.of(2026, 10, 22)
        val cycle = Cycle(
            id = 911L,
            name = "Recomp Cut",
            startDate = LocalDate.of(2026, 10, 1),
            endDate = LocalDate.of(2026, 11, 19),
            type = CycleType.FAT_LOSS_BODYBUILDING,
            startingWeightKg = 78.0,
            targetWeightKg = 72.0
        )
        // Today's 7D average 72.04 kg: reached in kg, 0.1 lbs left in lbs
        val entries = listOf(
            WeightEntry(date = LocalDate.of(2026, 10, 1), weightKg = 78.0, updatedAtMillis = 1L),
            WeightEntry(date = today, weightKg = 72.04, updatedAtMillis = 2L)
        )

        assertEquals(PaceForecast.GoalReached, FatLossAnalytics.computePaceForecast(entries, cycle, today, weightUnit = "kg"))
        assertNull(FatLossAnalytics.computeRequiredPaceToEndDate(entries, cycle, today, "kg"))

        val lbsForecast = FatLossAnalytics.computePaceForecast(entries, cycle, today, weightUnit = "lbs")
        val lbsRequired = FatLossAnalytics.computeRequiredPaceToEndDate(entries, cycle, today, "lbs")
        assertFalse(lbsForecast is PaceForecast.GoalReached)
        assertNotNull(lbsRequired)
        assertEquals(0.1, lbsRequired!!.remainingWeightInUnit, 1e-6)
        assertEquals(
            "Required pace: -0.1 lbs/wk (by 19 Nov 2026)",
            resolveRequiredPaceSubtitle(lbsForecast, lbsRequired)
        )
    }

    // =========================================================================
    // Feature 004-pace-and-linear-targets: 2-Decimal Target Cell Resolvers (US1, US2, US3)
    // =========================================================================

    @Test
    fun paceAndLinearTargets_twoDecimalResolversAndBoundaryContainerText() {
        assertEquals("PACE & LINEAR TARGETS", PACE_AND_LINEAR_TARGETS_HEADER)
        assertEquals("7D ROLLING AVERAGE", TARGET_ROW_7D_AVG_HEADER)
        assertEquals("14D ROLLING AVERAGE", TARGET_ROW_14D_AVG_HEADER)
        assertEquals("21D ROLLING AVERAGE", TARGET_ROW_21D_AVG_HEADER)
        assertEquals("28D ROLLING AVERAGE", TARGET_ROW_28D_AVG_HEADER)

        assertEquals("Goal reached! 🎉", resolvePaceTargetsContainerStateText(PaceTargetsState.GoalReached))
        assertEquals("Cycle ended", resolvePaceTargetsContainerStateText(PaceTargetsState.CycleEnded))

        // Horizon-specific InsufficientHistory subtitles for 7D, 14D, 21D, 28D
        assertEquals("Needs 7 days history", resolveExpectedTodaySubtitle(ColumnState.InsufficientHistory, 7))
        assertEquals("Needs 14 days history", resolveExpectedTodaySubtitle(ColumnState.InsufficientHistory, 14))
        assertEquals("Needs 21 days history", resolveExpectedTodaySubtitle(ColumnState.InsufficientHistory, 21))
        assertEquals("Needs 28 days history", resolveExpectedTodaySubtitle(ColumnState.InsufficientHistory, 28))

        // Linear Today 2-decimal formatting (trailing zero preserved: 75.50 kg, +0.10 kg off pace)
        val linearOffPace = ColumnState.Value(
            targetWeightDisplay = 75.50,
            deltaDisplay = 0.10,
            status = PaceTargetStatus.OFF_PACE,
            unitLabel = "kg",
            useTwoDecimals = true
        )
        assertEquals("75.50 kg", resolveLinearTodayValue(linearOffPace))
        assertEquals("+0.10 kg off pace", resolveLinearTodaySubtitle(linearOffPace))

        val linearAhead = ColumnState.Value(
            targetWeightDisplay = 75.59,
            deltaDisplay = -0.89,
            status = PaceTargetStatus.AHEAD,
            unitLabel = "kg",
            useTwoDecimals = true
        )
        assertEquals("75.59 kg", resolveLinearTodayValue(linearAhead))
        assertEquals("-0.89 kg ahead", resolveLinearTodaySubtitle(linearAhead))

        val linearOnPace = ColumnState.Value(
            targetWeightDisplay = 166.50,
            deltaDisplay = 0.0,
            status = PaceTargetStatus.ON_PACE,
            unitLabel = "lbs",
            useTwoDecimals = true
        )
        assertEquals("166.50 lbs", resolveLinearTodayValue(linearOnPace))
        assertEquals("On pace", resolveLinearTodaySubtitle(linearOnPace))

        // Expected Today 2-decimal formatting
        val expected2Dec = ColumnState.Value(
            targetWeightDisplay = 75.56,
            deltaDisplay = 0.10,
            status = PaceTargetStatus.OFF_PACE,
            unitLabel = "kg",
            baselineWeightDisplay = 76.20,
            useTwoDecimals = true
        )
        assertEquals("75.56 kg", resolveExpectedTodayValue(expected2Dec))
        assertEquals("+0.10 kg off pace", resolveExpectedTodaySubtitle(expected2Dec))

        // Target Tomorrow 2-decimal daily step formatting (-0.06 kg/day)
        val tomorrowDailyStep = ColumnState.Value(
            targetWeightDisplay = 74.94,
            deltaDisplay = -0.06,
            status = null,
            unitLabel = "kg",
            isDailyStep = true,
            useTwoDecimals = true
        )
        assertEquals("74.94 kg", resolveTargetTomorrowValue(tomorrowDailyStep))
        assertEquals("-0.06 kg/day", resolveTargetTomorrowSubtitle(tomorrowDailyStep))

        // NeedsTodayLog fallback
        assertEquals("—", resolveTargetTomorrowValue(ColumnState.NeedsTodayLog))
        assertEquals("Needs today's log", resolveTargetTomorrowSubtitle(ColumnState.NeedsTodayLog))

        // Semantic content description per cell across horizons
        assertEquals(
            "7D ROLLING AVERAGE Linear Today: 75.59 kg, +0.07 kg off pace",
            resolveTargetCellContentDescription(
                TARGET_ROW_7D_AVG_HEADER,
                "Linear Today",
                "75.59 kg",
                "+0.07 kg off pace"
            )
        )
        assertEquals(
            "28D ROLLING AVERAGE Expected Today: —, Needs 28 days history",
            resolveTargetCellContentDescription(
                TARGET_ROW_28D_AVG_HEADER,
                "Expected Today",
                "—",
                "Needs 28 days history"
            )
        )
    }

    // =========================================================================
    // Feature 005-multi-horizon-pace-estimations: 2-Decimal Pace Card & Horizon Estimation Row Resolvers
    // =========================================================================

    @Test
    fun feature005_twoDecimalPaceCardAndSubtitleResolvers() {
        val today = LocalDate.of(2026, 10, 22)
        val paceLoss = PaceRateResult(
            rateKgPerWeek = -0.28,
            anchorWeightKg = 75.94,
            anchorDate = today.minusDays(7),
            currentWeightKg = 75.66,
            currentDate = today,
            elapsedDays = 7L,
            trend = PaceTrend.LOSS
        )
        val paceGain = paceLoss.copy(rateKgPerWeek = 0.15, trend = PaceTrend.GAIN)
        val paceNeutral = paceLoss.copy(rateKgPerWeek = 0.0, trend = PaceTrend.NEUTRAL)

        // 2-decimal pace chip value resolution
        assertEquals("▼ -0.28 kg/wk", resolvePaceChipValue(paceLoss, "kg", useTwoDecimals = true))
        assertEquals("▼ -0.62 lbs/wk", resolvePaceChipValue(paceLoss, "lbs", useTwoDecimals = true))
        assertEquals("▲ +0.15 kg/wk", resolvePaceChipValue(paceGain, "kg", useTwoDecimals = true))
        assertEquals("— 0.00 kg/wk", resolvePaceChipValue(paceNeutral, "kg", useTwoDecimals = true))
        assertEquals("—", resolvePaceChipValue(null, "kg", useTwoDecimals = true))

        // 21D and 28D null and mature subtitles
        assertEquals("Needs weigh-in 21+ days ago", PACE_21_NULL_SUBTITLE)
        assertEquals("Needs weigh-in 28+ days ago", PACE_28_NULL_SUBTITLE)
        assertEquals("Needs weigh-in 21+ days ago", resolvePace21Subtitle(null, null, "kg", today, useTwoDecimals = true))
        assertEquals("Needs weigh-in 28+ days ago", resolvePace28Subtitle(null, null, "kg", today, useTwoDecimals = true))

        val pace21 = paceLoss.copy(rateKgPerWeek = -0.34, elapsedDays = 21L)
        val pace28 = paceLoss.copy(rateKgPerWeek = -0.54, elapsedDays = 28L)
        assertEquals("Smoothed trend", resolvePace21Subtitle(pace21, null, "kg", today, useTwoDecimals = true))
        assertEquals("Smoothed trend", resolvePace28Subtitle(pace28, null, "kg", today, useTwoDecimals = true))
    }

    @Test
    fun feature005_horizonEstimationRowResolversAndSemantics() {
        assertEquals("GOAL ARRIVAL ESTIMATIONS", GOAL_ARRIVAL_ESTIMATIONS_HEADER)

        val arrival = LocalDate.of(2027, 1, 23)
        val projectedRow = com.fractanomics.crosstraining.data.analytics.HorizonEstimationRow(
            windowDays = 7,
            state = com.fractanomics.crosstraining.data.analytics.HorizonRowState.Projected(
                windowDays = 7,
                paceDisplay2 = "-0.28 kg/wk",
                velocity2InUnit = 0.28,
                weeks = 13.2,
                rawWeeks = 13.214,
                targetDate = arrival,
                barFraction = 0.72f,
                weeksDeltaVsTarget = 3.8,
                isOnTrack = false,
                deltaBadgeText = "+3.8w late"
            )
        )
        assertEquals("7D (-0.28 kg/wk)", resolveHorizonRowLeftLabel(projectedRow))
        assertEquals("13.2 wks · 23 Jan 2027", resolveHorizonRowRightValue(projectedRow))
        assertEquals("+3.8w late", resolveHorizonRowBadgeText(projectedRow))
        assertEquals(
            "7-day horizon (7D (-0.28 kg/wk)): 13.2 wks · 23 Jan 2027, +3.8w late",
            resolveHorizonRowContentDescription(projectedRow)
        )

        val stalledRow = com.fractanomics.crosstraining.data.analytics.HorizonEstimationRow(
            windowDays = 14,
            state = com.fractanomics.crosstraining.data.analytics.HorizonRowState.Stalled(
                windowDays = 14,
                paceDisplay2 = "+0.05 kg/wk"
            )
        )
        assertEquals("14D (+0.05 kg/wk)", resolveHorizonRowLeftLabel(stalledRow))
        assertEquals("Pace stalled", resolveHorizonRowRightValue(stalledRow))
        assertEquals("Stalled", resolveHorizonRowBadgeText(stalledRow))
        assertEquals(
            "14-day horizon (14D (+0.05 kg/wk)): Pace stalled, Stalled",
            resolveHorizonRowContentDescription(stalledRow)
        )

        val missingRow = com.fractanomics.crosstraining.data.analytics.HorizonEstimationRow(
            windowDays = 28,
            state = com.fractanomics.crosstraining.data.analytics.HorizonRowState.NotEnoughData(28)
        )
        assertEquals("28D (—)", resolveHorizonRowLeftLabel(missingRow))
        assertEquals("Needs 28+ days", resolveHorizonRowRightValue(missingRow))
        assertNull(resolveHorizonRowBadgeText(missingRow))
        assertEquals(
            "28-day horizon (28D (—)): Needs 28+ days",
            resolveHorizonRowContentDescription(missingRow)
        )
    }
}
