package com.fractanomics.crosstraining.ui.components

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.assertHasClickAction
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.assertTextEquals
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performImeAction
import androidx.compose.ui.test.performTextClearance
import androidx.compose.ui.test.performTextInput
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Compose UI test suite for [ResetPasswordDialog].
 * Verifies Compose UI interactions and Acceptance Criteria for Issue #458:
 *
 * Feature: Password Reset Automated Test Suite
 *
 * Scenario: Dialog UI state transitions
 *   Given ResetPasswordDialog with invalid email
 *   Then the "Send Link" button is disabled
 *   When valid email is entered
 *   Then the "Send Link" button becomes enabled
 *   When submit is triggered
 *   Then loading indicator is displayed and dismissal is disabled
 */
@RunWith(AndroidJUnit4::class)
class ResetPasswordDialogComposeTest {

    @get:Rule
    val composeTestRule = createComposeRule()

    // =========================================================================
    // Scenario 1: Dialog UI state transitions (Disabled -> Enabled -> Submit)
    // =========================================================================

    @Test
    fun dialogUiStateTransitions_buttonDisabledWithInvalidEmail_becomesEnabledWithValidEmail() {
        var submittedEmail: String? = null

        composeTestRule.setContent {
            ResetPasswordDialog(
                initialEmail = "invalid-email@",
                isLoading = false,
                message = null,
                onDismiss = {},
                onSendResetLink = { submittedEmail = it }
            )
        }

        // Given: ResetPasswordDialog with invalid email
        composeTestRule.onNodeWithText("Reset Password").assertIsDisplayed()
        val sendLinkButton = composeTestRule.onNodeWithText("Send Link")

        // Then: the "Send Link" button is disabled
        sendLinkButton.assertIsNotEnabled()

        // When: valid email is entered
        val emailInput = composeTestRule.onNode(hasSetTextAction())
        emailInput.performTextClearance()
        emailInput.performTextInput("athlete@example.com")

        // Then: the "Send Link" button becomes enabled
        sendLinkButton.assertIsEnabled()

        // When: submit is clicked
        sendLinkButton.performClick()

        // Then: submission callback receives trimmed valid email
        assertEquals("athlete@example.com", submittedEmail)
    }

    @Test
    fun dialogUiStateTransitions_loadingStateDisplaysProgressAndDisablesDismissal() {
        var dismissed = false

        composeTestRule.setContent {
            ResetPasswordDialog(
                initialEmail = "athlete@example.com",
                isLoading = true,
                message = null,
                onDismiss = { dismissed = true },
                onSendResetLink = {}
            )
        }

        // When: loading is true
        // Then: "Send Link" text is replaced by loading indicator (button not enabled for repeat clicks)
        composeTestRule.onNodeWithText("Send Link").assertDoesNotExist()

        // And: "Close" button is disabled during loading
        val closeButton = composeTestRule.onNodeWithText("Close")
        closeButton.assertIsNotEnabled()

        closeButton.performClick()
        assertFalse("Dismiss should not trigger while loading", dismissed)
    }

    // =========================================================================
    // Scenario 2: IME Done Action submission
    // =========================================================================

    @Test
    fun imeDoneAction_submitsWhenEmailIsValid() {
        var submittedEmail: String? = null

        composeTestRule.setContent {
            ResetPasswordDialog(
                initialEmail = "",
                isLoading = false,
                message = null,
                onDismiss = {},
                onSendResetLink = { submittedEmail = it }
            )
        }

        val emailInput = composeTestRule.onNode(hasSetTextAction())
        emailInput.performTextInput("coach.lead@crosstraining.app")
        emailInput.performImeAction()

        assertEquals("coach.lead@crosstraining.app", submittedEmail)
    }

    @Test
    fun imeDoneAction_doesNotSubmitWhenEmailIsInvalid() {
        var submittedEmail: String? = null

        composeTestRule.setContent {
            ResetPasswordDialog(
                initialEmail = "not-an-email",
                isLoading = false,
                message = null,
                onDismiss = {},
                onSendResetLink = { submittedEmail = it }
            )
        }

        val emailInput = composeTestRule.onNode(hasSetTextAction())
        emailInput.performImeAction()

        assertNull(submittedEmail)
    }

    // =========================================================================
    // Scenario 3: Inline Error Message Display
    // =========================================================================

    @Test
    fun inlineErrorMessage_isDisplayedWhenMessagePropIsProvided() {
        val errorMessage = "Network error. Please check your connection."

        composeTestRule.setContent {
            ResetPasswordDialog(
                initialEmail = "athlete@example.com",
                isLoading = false,
                message = errorMessage,
                onDismiss = {},
                onSendResetLink = {}
            )
        }

        composeTestRule.onNodeWithText(errorMessage).assertIsDisplayed()
    }

    // =========================================================================
    // Scenario 4: Dismiss / Close Button Interaction
    // =========================================================================

    @Test
    fun closeButton_triggersOnDismissWhenNotLoading() {
        var dismissed = false

        composeTestRule.setContent {
            ResetPasswordDialog(
                initialEmail = "",
                isLoading = false,
                message = null,
                onDismiss = { dismissed = true },
                onSendResetLink = {}
            )
        }

        val closeButton = composeTestRule.onNodeWithText("Close")
        closeButton.assertIsEnabled()
        closeButton.performClick()

        assertTrue(dismissed)
    }

    // =========================================================================
    // Scenario 5: Pre-filling Initial Email
    // =========================================================================

    @Test
    fun initialEmail_isPrePopulatedInInputField() {
        val prefilled = "prefilled.athlete@example.com"

        composeTestRule.setContent {
            ResetPasswordDialog(
                initialEmail = prefilled,
                isLoading = false,
                message = null,
                onDismiss = {},
                onSendResetLink = {}
            )
        }

        composeTestRule.onNodeWithText(prefilled).assertIsDisplayed()
        composeTestRule.onNodeWithText("Send Link").assertIsEnabled()
    }
}
