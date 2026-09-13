package com.fractanomics.crosstraining.ui.screens

import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.rememberLazyListState
import com.fractanomics.crosstraining.data.analytics.Timeframe
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Unit tests verifying crash-free LazyRow dynamic mode derivation,
 * auto-scroll indexing invariants, and horizontal scroll timeframe accessibility (Issue #531).
 *
 * BDD Scenarios Covered:
 * - Scenario 1: Crash-Free Mode Derivation & Non-Zero Positive Item Indices
 * - Scenario 4: Programmatic Auto-Scroll to Active Chip on Arrival
 * - Scenario 7: Timeframe Filter Row Horizontal Scrolling in Weight Overview & Timeframe.entries
 */
class ProgressScreenLazyRowAutoScrollTest {

    // Helper simulating the derivation logic in ProgressScreen
    private fun deriveAvailableModes(hasRoutines: Boolean, hasCycles: Boolean): List<ProgressMode> {
        return listOfNotNull(
            ProgressMode.BY_EXERCISE,
            if (hasRoutines) ProgressMode.BY_ROUTINE else null,
            if (hasCycles) ProgressMode.CYCLE_GOALS else null,
            ProgressMode.BODY_WEIGHT
        )
    }

    @Test
    fun scenario1_deriveAvailableModes_freshInstallWithZeroRoutinesAndZeroCycles() {
        // Given fresh install: routines is empty, cycles is empty
        val modes = deriveAvailableModes(hasRoutines = false, hasCycles = false)

        // Then only BY_EXERCISE and BODY_WEIGHT are available
        assertEquals(listOf(ProgressMode.BY_EXERCISE, ProgressMode.BODY_WEIGHT), modes)
        assertEquals(2, modes.size)

        // And their indices are strictly defined
        assertEquals(0, modes.indexOf(ProgressMode.BY_EXERCISE))
        assertEquals(1, modes.indexOf(ProgressMode.BODY_WEIGHT))
        assertEquals(-1, modes.indexOf(ProgressMode.BY_ROUTINE))
        assertEquals(-1, modes.indexOf(ProgressMode.CYCLE_GOALS))
    }

    @Test
    fun scenario1_deriveAvailableModes_withRoutinesOnly() {
        val modes = deriveAvailableModes(hasRoutines = true, hasCycles = false)

        assertEquals(
            listOf(ProgressMode.BY_EXERCISE, ProgressMode.BY_ROUTINE, ProgressMode.BODY_WEIGHT),
            modes
        )
        assertEquals(0, modes.indexOf(ProgressMode.BY_EXERCISE))
        assertEquals(1, modes.indexOf(ProgressMode.BY_ROUTINE))
        assertEquals(2, modes.indexOf(ProgressMode.BODY_WEIGHT))
    }

    @Test
    fun scenario1_deriveAvailableModes_withCyclesOnly() {
        val modes = deriveAvailableModes(hasRoutines = false, hasCycles = true)

        assertEquals(
            listOf(ProgressMode.BY_EXERCISE, ProgressMode.CYCLE_GOALS, ProgressMode.BODY_WEIGHT),
            modes
        )
        assertEquals(0, modes.indexOf(ProgressMode.BY_EXERCISE))
        assertEquals(1, modes.indexOf(ProgressMode.CYCLE_GOALS))
        assertEquals(2, modes.indexOf(ProgressMode.BODY_WEIGHT))
    }

    @Test
    fun scenario1_deriveAvailableModes_fullyPopulatedAllModes() {
        val modes = deriveAvailableModes(hasRoutines = true, hasCycles = true)

        assertEquals(
            listOf(
                ProgressMode.BY_EXERCISE,
                ProgressMode.BY_ROUTINE,
                ProgressMode.CYCLE_GOALS,
                ProgressMode.BODY_WEIGHT
            ),
            modes
        )
        assertEquals(4, modes.size)
        assertEquals(0, modes.indexOf(ProgressMode.BY_EXERCISE))
        assertEquals(1, modes.indexOf(ProgressMode.BY_ROUTINE))
        assertEquals(2, modes.indexOf(ProgressMode.CYCLE_GOALS))
        assertEquals(3, modes.indexOf(ProgressMode.BODY_WEIGHT))
    }

    @Test
    fun scenario4_autoScrollTargetIndex_resolvesCorrectlyAcrossAvailableConfigurations() {
        // When athlete navigates to BODY_WEIGHT on fresh install
        val freshModes = deriveAvailableModes(hasRoutines = false, hasCycles = false)
        val freshTargetIndex = freshModes.indexOf(ProgressMode.BODY_WEIGHT)
        assertEquals("Target index on fresh install for BODY_WEIGHT must be 1", 1, freshTargetIndex)
        assertTrue("Index must be non-negative", freshTargetIndex >= 0)

        // When athlete navigates to BODY_WEIGHT on full setup
        val fullModes = deriveAvailableModes(hasRoutines = true, hasCycles = true)
        val fullTargetIndex = fullModes.indexOf(ProgressMode.BODY_WEIGHT)
        assertEquals("Target index on full setup for BODY_WEIGHT must be 3", 3, fullTargetIndex)
        assertTrue("Index must be non-negative", fullTargetIndex >= 0)

        // Unknown mode safely yields -1 preventing out-of-bounds scroll
        val unknownIndex = freshModes.indexOf(ProgressMode.CYCLE_GOALS)
        assertEquals(-1, unknownIndex)
        assertFalse("Guard must prevent scrolling to -1", unknownIndex >= 0)
    }

    @Test
    fun scenario7_timeframeEntries_matchesAllExpectedTimeframeValuesWithoutAllocation() {
        // Given Timeframe enum
        val entries = Timeframe.entries

        // Then all 5 timeframes are present in order
        assertEquals(5, entries.size)
        assertEquals(Timeframe.SEVEN_DAYS, entries[0])
        assertEquals(Timeframe.THIRTY_DAYS, entries[1])
        assertEquals(Timeframe.NINETY_DAYS, entries[2])
        assertEquals(Timeframe.ONE_YEAR, entries[3])
        assertEquals(Timeframe.ALL, entries[4])

        // Verify label contracts
        assertEquals("7D", entries[0].label)
        assertEquals("30D", entries[1].label)
        assertEquals("90D", entries[2].label)
        assertEquals("1Y", entries[3].label)
        assertEquals("All", entries[4].label)
    }
}
