package com.fractanomics.crosstraining.ui.components

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.assertTextEquals
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performImeAction
import androidx.compose.ui.test.performTextClearance
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.test.performTextReplacement
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Compose UI test suite for [AppNumericTextField].
 * Verifies Compose UI interactions and Acceptance Criteria for Issue #430:
 * - Scenario: No digit concatenation on replace
 * - Scenario: Decimal parsing accuracy
 * - Scenario: Clamp on commit
 */
@RunWith(AndroidJUnit4::class)
class AppNumericTextFieldComposeTest {

    @get:Rule
    val composeTestRule = createComposeRule()

    @Test
    fun noDigitConcatenationOnReplace() {
        var state by mutableStateOf("10")

        composeTestRule.setContent {
            AppNumericTextField(
                value = state,
                onValueChange = { state = it },
                modifier = Modifier.testTag("numeric_field")
            )
        }

        val node = composeTestRule.onNodeWithTag("numeric_field")
        node.assertTextEquals("10")

        // User clears it and types "6"
        node.performTextClearance()
        node.performTextInput("6")

        // Resulting value is "6", not "610"
        assertEquals("6", state)
        node.assertTextEquals("6")
    }

    @Test
    fun decimalParsingAccuracy() {
        var state by mutableStateOf("")

        composeTestRule.setContent {
            AppNumericTextField(
                value = state,
                onValueChange = { state = it },
                allowDecimals = true,
                modifier = Modifier.testTag("decimal_field")
            )
        }

        val node = composeTestRule.onNodeWithTag("decimal_field")
        node.performTextInput("22.5")

        assertEquals("22.5", state)
        node.assertTextEquals("22.5")
        assertEquals(22.5, state.toDoubleOrNull() ?: 0.0, 0.0001)
    }

    @Test
    fun clampOnCommitWhenEmptyAndImeDonePressed() {
        var state by mutableStateOf("20")

        composeTestRule.setContent {
            AppNumericTextField(
                value = state,
                onValueChange = { state = it },
                minValue = 5.0,
                maxValue = 100.0,
                modifier = Modifier.testTag("clamped_field")
            )
        }

        val node = composeTestRule.onNodeWithTag("clamped_field")
        node.performTextClearance()
        node.performImeAction()

        // When the field is left empty and IME Done is pressed, committed value equals minValue ("5")
        assertEquals("5", state)
        node.assertTextEquals("5")
    }

    @Test
    fun basicVariantInputAndClamping() {
        var state by mutableStateOf("15")

        composeTestRule.setContent {
            AppNumericTextField(
                value = state,
                onValueChange = { state = it },
                minValue = 1.0,
                maxValue = 50.0,
                isBasic = true,
                modifier = Modifier.testTag("basic_numeric_field")
            )
        }

        val node = composeTestRule.onNodeWithTag("basic_numeric_field")
        node.performTextReplacement("35")
        assertEquals("35", state)

        node.performTextClearance()
        node.performImeAction()
        assertEquals("1", state)
    }

    @Test
    fun intOverloadInputAndClamping() {
        var intState by mutableStateOf(10)

        composeTestRule.setContent {
            AppNumericTextField(
                value = intState,
                onValueChange = { intState = it },
                minValue = 1,
                maxValue = 100,
                modifier = Modifier.testTag("int_field")
            )
        }

        val node = composeTestRule.onNodeWithTag("int_field")
        node.performTextReplacement("25")
        assertEquals(25, intState)

        node.performTextClearance()
        node.performImeAction()
        assertEquals(1, intState)
    }

    @Test
    fun doubleOverloadDecimalInputAndClamping() {
        var doubleState by mutableStateOf<Double?>(10.0)

        composeTestRule.setContent {
            AppNumericTextField(
                value = doubleState,
                onValueChange = { doubleState = it },
                minValue = 0.5,
                maxValue = 500.0,
                modifier = Modifier.testTag("double_field")
            )
        }

        val node = composeTestRule.onNodeWithTag("double_field")
        node.performTextReplacement("45.5")
        assertEquals(45.5, doubleState ?: 0.0, 0.0001)

        node.performTextClearance()
        node.performImeAction()
        assertEquals(0.5, doubleState ?: 0.0, 0.0001)
    }
}
