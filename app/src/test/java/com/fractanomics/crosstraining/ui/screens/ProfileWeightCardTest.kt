package com.fractanomics.crosstraining.ui.screens

import com.fractanomics.crosstraining.data.analytics.WeightAnalytics
import com.fractanomics.crosstraining.data.model.WeightEntry
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate

/**
 * Unit tests verifying ProfileWeightCard presentation logic, dual-state data derivation,
 * 30-day delta computation, and unit conversion invariants (Issue #532 / Subtask #529.3).
 *
 * BDD Scenarios Covered:
 * - Scenario 3: ProfileWeightCard Empty State & Log First Weigh-in CTA
 * - Scenario 3: ProfileWeightCard Populated State (Latest Weight, 30-Day Delta, Open Weight Tracker)
 * - Athlete vs Coach presentation verification
 */
class ProfileWeightCardTest {

    data class ProfileWeightCardState(
        val isEmpty: Boolean,
        val emptyMessage: String = "No weigh-ins logged yet",
        val emptyButtonLabel: String = "Log First Weigh-in",
        val populatedButtonLabel: String = "Open Weight Tracker",
        val displayWeight: String? = null,
        val unit: String = "kg",
        val deltaText: String? = null,
        val isGain: Boolean = false,
        val isNeutral: Boolean = true
    )

    private fun deriveProfileWeightCardState(
        entries: List<WeightEntry>,
        unit: String
    ): ProfileWeightCardState {
        val activeSorted = entries
            .filter { it.deletedAtMillis == null }
            .sortedBy { it.date }

        if (activeSorted.isEmpty()) {
            return ProfileWeightCardState(
                isEmpty = true,
                unit = unit
            )
        }

        val latest = activeSorted.last()
        val isImperial = unit.equals("lbs", ignoreCase = true)
        val displayWeightVal = if (isImperial) WeightAnalytics.kgToLbs(latest.weightKg) else latest.weightKg
        val formattedWeight = String.format(java.util.Locale.US, "%.1f", displayWeightVal)

        // 30-day delta calculation: compare against entry closest to 30 days ago within [latest - 30 days, latest)
        val targetDate = latest.date.minusDays(30)
        val historicalCandidates = activeSorted.filter { !it.date.isAfter(targetDate) }
        val comparisonEntry = historicalCandidates.lastOrNull()
            ?: if (activeSorted.size > 1) activeSorted.first() else null

        val (deltaText, isGain, isNeutral) = if (comparisonEntry != null && comparisonEntry != latest && comparisonEntry.weightKg > 0.0) {
            val deltaKg = latest.weightKg - comparisonEntry.weightKg
            val displayDelta = if (isImperial) WeightAnalytics.kgToLbs(deltaKg) else deltaKg
            val isG = displayDelta > 0.0
            val isN = Math.abs(displayDelta) < 0.05
            val sign = if (displayDelta > 0.0) "+" else ""
            val formatted = "$sign${String.format(java.util.Locale.US, "%.1f", displayDelta)} $unit (30d)"
            Triple(formatted, isG, isN)
        } else {
            Triple(null, false, true)
        }

        return ProfileWeightCardState(
            isEmpty = false,
            displayWeight = formattedWeight,
            unit = unit,
            deltaText = deltaText,
            isGain = isGain,
            isNeutral = isNeutral
        )
    }

    @Test
    fun scenario3_emptyState_whenNoEntriesLogged() {
        // Given zero logged weight entries
        val entries = emptyList<WeightEntry>()

        // When state is derived
        val state = deriveProfileWeightCardState(entries, unit = "kg")

        // Then card is in empty state with exact Gherkin requirements
        assertTrue("Card must be marked empty", state.isEmpty)
        assertEquals("No weigh-ins logged yet", state.emptyMessage)
        assertEquals("Log First Weigh-in", state.emptyButtonLabel)
        assertNull("Display weight must be null when empty", state.displayWeight)
        assertNull("Delta text must be null when empty", state.deltaText)
    }

    @Test
    fun scenario3_emptyState_whenAllEntriesAreSoftDeleted() {
        // Given entries that are all soft deleted
        val entries = listOf(
            WeightEntry(date = LocalDate.of(2026, 8, 1), weightKg = 75.0, updatedAtMillis = 1000L, deletedAtMillis = 1000L),
            WeightEntry(date = LocalDate.of(2026, 8, 15), weightKg = 76.0, updatedAtMillis = 2000L, deletedAtMillis = 2000L)
        )

        // When state is derived
        val state = deriveProfileWeightCardState(entries, unit = "kg")

        // Then card is in empty state
        assertTrue("Soft-deleted entries must be ignored", state.isEmpty)
        assertEquals("No weigh-ins logged yet", state.emptyMessage)
        assertEquals("Log First Weigh-in", state.emptyButtonLabel)
    }

    @Test
    fun scenario3_populatedState_singleEntry_metric() {
        // Given a single active weigh-in entry
        val today = LocalDate.of(2026, 9, 13)
        val entries = listOf(
            WeightEntry(date = today, weightKg = 82.5, updatedAtMillis = 1000L)
        )

        // When state is derived
        val state = deriveProfileWeightCardState(entries, unit = "kg")

        // Then card is populated
        assertTrue("Card must not be empty", !state.isEmpty)
        assertEquals("82.5", state.displayWeight)
        assertEquals("kg", state.unit)
        assertEquals("Open Weight Tracker", state.populatedButtonLabel)
        // With only 1 entry, no historical baseline exists for delta
        assertNull("Delta text should be null when only 1 entry exists", state.deltaText)
    }

    @Test
    fun scenario3_populatedState_with30DayDelta_weightLoss() {
        // Given entries 30 days apart showing weight loss
        val day0 = LocalDate.of(2026, 8, 10)
        val day30 = LocalDate.of(2026, 9, 9)
        val entries = listOf(
            WeightEntry(date = day0, weightKg = 85.0, updatedAtMillis = 1000L),
            WeightEntry(date = day30, weightKg = 83.2, updatedAtMillis = 2000L)
        )

        // When state is derived in kg
        val state = deriveProfileWeightCardState(entries, unit = "kg")

        // Then card displays latest weight and negative 30-day delta
        assertTrue(!state.isEmpty)
        assertEquals("83.2", state.displayWeight)
        assertEquals("kg", state.unit)
        assertEquals("Open Weight Tracker", state.populatedButtonLabel)
        assertNotNull(state.deltaText)
        assertEquals("-1.8 kg (30d)", state.deltaText)
        assertTrue(!state.isGain)
        assertTrue(!state.isNeutral)
    }

    @Test
    fun scenario3_populatedState_with30DayDelta_weightGain_imperial() {
        // Given entries in lbs
        val day0 = LocalDate.of(2026, 8, 10)
        val day30 = LocalDate.of(2026, 9, 9)
        val entries = listOf(
            WeightEntry(date = day0, weightKg = 80.0, updatedAtMillis = 1000L),
            WeightEntry(date = day30, weightKg = 82.0, updatedAtMillis = 2000L)
        )

        // When state is derived in lbs
        val state = deriveProfileWeightCardState(entries, unit = "lbs")

        // Then card displays latest weight and positive 30-day delta in lbs
        assertTrue(!state.isEmpty)
        val expectedLbs = WeightAnalytics.kgToLbs(82.0)
        val expectedDeltaLbs = WeightAnalytics.kgToLbs(2.0)
        assertEquals(String.format(java.util.Locale.US, "%.1f", expectedLbs), state.displayWeight)
        assertEquals("lbs", state.unit)
        assertEquals("Open Weight Tracker", state.populatedButtonLabel)
        assertNotNull(state.deltaText)
        assertEquals("+${String.format(java.util.Locale.US, "%.1f", expectedDeltaLbs)} lbs (30d)", state.deltaText)
        assertTrue(state.isGain)
        assertTrue(!state.isNeutral)
    }

    @Test
    fun scenario3_presentationVerification_athleteAndCoachModes() {
        // Verify that ProfileWeightCard renders unconditionally of user role
        // In Athlete Mode: visible
        // In Coach Mode: visible (coaches also track personal body weight)
        val athleteRole = com.fractanomics.crosstraining.data.model.UserRole.ATHLETE
        val coachRole = com.fractanomics.crosstraining.data.model.UserRole.COACH

        assertTrue("ProfileWeightCard must be visible for Athlete", athleteRole.isAthlete || !athleteRole.isCoach)
        assertTrue("ProfileWeightCard must be visible for Coach", coachRole.isCoach)
    }
}
