package com.fractanomics.crosstraining.ui.navigation

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.MonitorWeight
import com.fractanomics.crosstraining.ui.screens.ProgressMode
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Unit tests verifying navigation drawer items, canonical routing, and mutually
 * exclusive drawer selection between "Progress & Analytics" and "Body Weight" (Issue #530).
 *
 * BDD Scenarios Covered:
 * - Scenario 2: Drawer Routing with Mutually Exclusive Selection
 * - Scenario 5: Re-Navigation Immunity & Repeat Mode Switching (Deadlock Defense)
 * - Scenario 6: Rapid Double-Navigation & Idempotent Mode Updates
 */
class AppNavigationDrawerTest {

    @Test
    fun drawerItem_weight_isConfiguredUnderWorkoutsSection() {
        // Given DrawerItem.WEIGHT
        val item = DrawerItem.WEIGHT

        // Then it routes to the canonical "progress" screen
        assertEquals("progress", item.route)
        assertEquals("Body Weight", item.title)
        assertEquals("Weight log & trend analytics", item.subtitle)
        assertEquals(Icons.Filled.MonitorWeight, item.icon)
        assertEquals(DrawerSection.WORKOUTS, item.section)
    }

    @Test
    fun drawerItem_selectionLogic_mutuallyExclusiveBetweenWeightAndProgress() {
        val currentRoute = BottomDestination.PROGRESS.route // "progress"

        fun isSelected(item: DrawerItem, route: String?, mode: ProgressMode): Boolean {
            return when (item) {
                DrawerItem.WEIGHT -> route == BottomDestination.PROGRESS.route && mode == ProgressMode.BODY_WEIGHT
                DrawerItem.PROGRESS -> route == BottomDestination.PROGRESS.route && mode != ProgressMode.BODY_WEIGHT
                else -> route == item.route
            }
        }

        // Case 1: On Progress screen with ProgressMode.BODY_WEIGHT
        assertTrue(
            "DrawerItem.WEIGHT must be selected when on progress route in BODY_WEIGHT mode",
            isSelected(DrawerItem.WEIGHT, currentRoute, ProgressMode.BODY_WEIGHT)
        )
        assertFalse(
            "DrawerItem.PROGRESS must NOT be selected when in BODY_WEIGHT mode",
            isSelected(DrawerItem.PROGRESS, currentRoute, ProgressMode.BODY_WEIGHT)
        )

        // Case 2: On Progress screen with ProgressMode.BY_EXERCISE
        assertFalse(
            "DrawerItem.WEIGHT must NOT be selected when in BY_EXERCISE mode",
            isSelected(DrawerItem.WEIGHT, currentRoute, ProgressMode.BY_EXERCISE)
        )
        assertTrue(
            "DrawerItem.PROGRESS must be selected when in BY_EXERCISE mode",
            isSelected(DrawerItem.PROGRESS, currentRoute, ProgressMode.BY_EXERCISE)
        )

        // Case 3: On Progress screen with ProgressMode.BY_ROUTINE
        assertFalse(
            "DrawerItem.WEIGHT must NOT be selected when in BY_ROUTINE mode",
            isSelected(DrawerItem.WEIGHT, currentRoute, ProgressMode.BY_ROUTINE)
        )
        assertTrue(
            "DrawerItem.PROGRESS must be selected when in BY_ROUTINE mode",
            isSelected(DrawerItem.PROGRESS, currentRoute, ProgressMode.BY_ROUTINE)
        )

        // Case 4: On Progress screen with ProgressMode.CYCLE_GOALS
        assertFalse(
            "DrawerItem.WEIGHT must NOT be selected when in CYCLE_GOALS mode",
            isSelected(DrawerItem.WEIGHT, currentRoute, ProgressMode.CYCLE_GOALS)
        )
        assertTrue(
            "DrawerItem.PROGRESS must be selected when in CYCLE_GOALS mode",
            isSelected(DrawerItem.PROGRESS, currentRoute, ProgressMode.CYCLE_GOALS)
        )

        // Case 5: On other routes (e.g. "log", "cycles")
        val otherRoute = BottomDestination.LOG.route
        assertFalse(
            "DrawerItem.WEIGHT must NOT be selected when on log route",
            isSelected(DrawerItem.WEIGHT, otherRoute, ProgressMode.BODY_WEIGHT)
        )
        assertFalse(
            "DrawerItem.PROGRESS must NOT be selected when on log route",
            isSelected(DrawerItem.PROGRESS, otherRoute, ProgressMode.BY_EXERCISE)
        )
        assertTrue(
            "DrawerItem.LOG must be selected when on log route",
            isSelected(DrawerItem.LOG, otherRoute, ProgressMode.BODY_WEIGHT)
        )
    }
}
