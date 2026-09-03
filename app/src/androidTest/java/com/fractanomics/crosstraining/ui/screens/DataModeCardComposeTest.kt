package com.fractanomics.crosstraining.ui.screens

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsOff
import androidx.compose.ui.test.assertIsOn
import androidx.compose.ui.test.isToggleable
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.fractanomics.crosstraining.ui.navigation.DataModeDrawerRow
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Compose UI test suite for [DataModeCard] and [DataModeDrawerRow].
 * Verifies Acceptance Criteria for Issue #484 (Subtask #481.3):
 *
 * Feature: Presentation Layer Data Mode Switching
 *
 * Scenario: Dynamic data mode toggle via Profile Screen and Navigation Drawer
 *   Given any user on the Profile Screen or in the Navigation Drawer
 *   When they locate the Data Mode section
 *   Then they see a switch clearly indicating "Real Data (Default)" is active
 *   When the user flips the switch to "Demo Data"
 *   Then the UI instantly re-binds to crosstraining-demo.db and the yellow demo banner appears
 *   When the user flips the switch back to "Real Data"
 *   Then the yellow banner disappears and their personal real database is restored
 *
 * Scenario: Stateless composable architecture and test coverage
 *   Given DataModeCard composable in ProfileScreen.kt
 *   When rendered in isolation
 *   Then it accepts (demoMode: Boolean, onToggle: (Boolean) -> Unit) without direct ViewModel coupling
 *   And is verified via an instrumented Compose test in app/src/androidTest
 */
@RunWith(AndroidJUnit4::class)
class DataModeCardComposeTest {

    @get:Rule
    val composeTestRule = createComposeRule()

    // =========================================================================
    // Scenario: Stateless Composable Architecture - DataModeCard in Isolation
    // =========================================================================

    @Test
    fun dataModeCard_whenRealDataActive_displaysRealDataDefaultAndAllowsToggle() {
        var toggledValue: Boolean? = null

        composeTestRule.setContent {
            DataModeCard(
                demoMode = false,
                onToggle = { toggledValue = it }
            )
        }

        // Given: DataModeCard rendered with demoMode = false (Real Data default)
        composeTestRule.onNodeWithText("Data Mode").assertIsDisplayed()
        composeTestRule.onNodeWithText("REAL DATA").assertIsDisplayed()
        composeTestRule.onNodeWithText("Real Data (Default)").assertIsDisplayed()

        // Then: Switch is currently off (indicating Real Data is active)
        val switchNode = composeTestRule.onNode(isToggleable())
        switchNode.assertIsDisplayed()
        switchNode.assertIsOff()

        // When: User flips the switch
        switchNode.performClick()

        // Then: onToggle is called with true to request Demo Data
        assertEquals(true, toggledValue)
    }

    @Test
    fun dataModeCard_whenDemoDataActive_displaysDemoDataAndAllowsToggle() {
        var toggledValue: Boolean? = null

        composeTestRule.setContent {
            DataModeCard(
                demoMode = true,
                onToggle = { toggledValue = it }
            )
        }

        // Given: DataModeCard rendered with demoMode = true
        composeTestRule.onNodeWithText("Data Mode").assertIsDisplayed()
        composeTestRule.onNodeWithText("DEMO DATA").assertIsDisplayed()
        composeTestRule.onNodeWithText("Demo Data").assertIsDisplayed()

        // Then: Switch is currently on
        val switchNode = composeTestRule.onNode(isToggleable())
        switchNode.assertIsDisplayed()
        switchNode.assertIsOn()

        // When: User flips the switch
        switchNode.performClick()

        // Then: onToggle is called with false to return to Real Data
        assertEquals(false, toggledValue)
    }

    @Test
    fun dataModeCard_whenDemoDataActive_showsResetDemoDataButtonAndTriggersAction() {
        var resetTriggered = false

        composeTestRule.setContent {
            DataModeCard(
                demoMode = true,
                onToggle = {},
                onResetDemo = { resetTriggered = true }
            )
        }

        // Given: demoMode = true and onResetDemo is provided
        val resetButton = composeTestRule.onNodeWithText("Reset Demo Data")
        resetButton.assertIsDisplayed()

        // When: User clicks Reset Demo Data
        resetButton.performClick()

        // Then: Reset callback is invoked
        assertTrue(resetTriggered)
    }

    @Test
    fun dataModeCard_whenRealDataActive_hidesResetDemoDataButton() {
        var resetTriggered = false

        composeTestRule.setContent {
            DataModeCard(
                demoMode = false,
                onToggle = {},
                onResetDemo = { resetTriggered = true }
            )
        }

        // Given: demoMode = false
        // Then: "Reset Demo Data" is not visible
        composeTestRule.onNodeWithText("Reset Demo Data").assertDoesNotExist()
        assertFalse(resetTriggered)
    }

    @Test
    fun dataModeCard_dynamicStateRecomposition_togglesBetweenModes() {
        composeTestRule.setContent {
            var demoMode by mutableStateOf(false)
            DataModeCard(
                demoMode = demoMode,
                onToggle = { demoMode = it }
            )
        }

        // Initially in Real Data
        composeTestRule.onNodeWithText("REAL DATA").assertIsDisplayed()
        composeTestRule.onNodeWithText("Real Data (Default)").assertIsDisplayed()
        composeTestRule.onNode(isToggleable()).assertIsOff()

        // Flip to Demo Data
        composeTestRule.onNode(isToggleable()).performClick()

        // Recomposes to Demo Data
        composeTestRule.onNodeWithText("DEMO DATA").assertIsDisplayed()
        composeTestRule.onNodeWithText("Demo Data").assertIsDisplayed()
        composeTestRule.onNode(isToggleable()).assertIsOn()

        // Flip back to Real Data
        composeTestRule.onNode(isToggleable()).performClick()

        // Recomposes back to Real Data
        composeTestRule.onNodeWithText("REAL DATA").assertIsDisplayed()
        composeTestRule.onNodeWithText("Real Data (Default)").assertIsDisplayed()
        composeTestRule.onNode(isToggleable()).assertIsOff()
    }

    // =========================================================================
    // Scenario: DataModeDrawerRow in Navigation Drawer
    // =========================================================================

    @Test
    fun dataModeDrawerRow_whenRealData_displaysRealDataDefaultAndAllowsToggle() {
        var toggledValue: Boolean? = null

        composeTestRule.setContent {
            DataModeDrawerRow(
                demoMode = false,
                onToggle = { toggledValue = it }
            )
        }

        // Given: DataModeDrawerRow in Real Data mode
        composeTestRule.onNodeWithText("Real Data (Default)").assertIsDisplayed()
        composeTestRule.onNodeWithText("crosstraining.db").assertIsDisplayed()

        val switchNode = composeTestRule.onNode(isToggleable())
        switchNode.assertIsDisplayed()
        switchNode.assertIsOff()

        // When: User flips the switch
        switchNode.performClick()

        // Then: Callback emits true
        assertEquals(true, toggledValue)
    }

    @Test
    fun dataModeDrawerRow_whenDemoData_displaysDemoDataAndAllowsToggle() {
        var toggledValue: Boolean? = null

        composeTestRule.setContent {
            DataModeDrawerRow(
                demoMode = true,
                onToggle = { toggledValue = it }
            )
        }

        // Given: DataModeDrawerRow in Demo Data mode
        composeTestRule.onNodeWithText("Demo Data").assertIsDisplayed()
        composeTestRule.onNodeWithText("crosstraining-demo.db").assertIsDisplayed()

        val switchNode = composeTestRule.onNode(isToggleable())
        switchNode.assertIsDisplayed()
        switchNode.assertIsOn()

        // When: User flips the switch
        switchNode.performClick()

        // Then: Callback emits false
        assertEquals(false, toggledValue)
    }
}
