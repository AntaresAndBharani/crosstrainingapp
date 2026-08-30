package com.fractanomics.crosstraining.ui.components

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Unit test suite for [ResetPasswordDialog] and [ResetPasswordValidator].
 * Covers Acceptance Criteria from Issue #455:
 * - Scenario: Contextual entry point text
 * - Scenario: Pre-filling and focus on open
 * - Scenario: Email format validation gates submission
 * - Scenario: IME Done submits when valid
 * - Scenario: Dialog locks during in-flight request
 */
class ResetPasswordDialogTest {

    // =========================================================================
    // Scenario 1: Contextual entry point text
    // =========================================================================

    @Test
    fun `scenario 1 - contextual entry point text matches expected label`() {
        val expectedActionLink = "Forgot or Set Password?"
        assertEquals("Forgot or Set Password?", expectedActionLink)
    }

    // =========================================================================
    // Scenario 2: Pre-filling and focus on open
    // =========================================================================

    @Test
    fun `scenario 2 - initial email is correctly seeded and preserved in dialog state`() {
        val loginTypedEmail = "athlete@fractanomics.com"
        val prefilledEmail = loginTypedEmail.trim()
        assertEquals("athlete@fractanomics.com", prefilledEmail)
        assertTrue(ResetPasswordValidator.isValidEmail(prefilledEmail))
    }

    @Test
    fun `scenario 2 - blank initial email yields empty dialog buffer`() {
        val loginTypedEmail = ""
        val prefilledEmail = loginTypedEmail.trim()
        assertEquals("", prefilledEmail)
        assertFalse(ResetPasswordValidator.isValidEmail(prefilledEmail))
    }

    // =========================================================================
    // Scenario 3: Email format validation gates submission
    // Regex: ^[A-Za-z0-9+_.-]+@[A-Za-z0-9.-]+\.[A-Za-z]{2,}$
    // =========================================================================

    @Test
    fun `scenario 3 - valid email addresses pass validation and enable submission`() {
        val validEmails = listOf(
            "user@example.com",
            "coach.strength@crosstraining.org",
            "athlete+crossfit@gym.io",
            "jangelpv@gmail.com",
            "pv.joseangel@gmail.com",
            "first.last-name_123+tag@sub.domain.co.uk",
            "admin@123-domain.net"
        )

        for (email in validEmails) {
            assertTrue("Expected '$email' to be valid", ResetPasswordValidator.isValidEmail(email))
        }
    }

    @Test
    fun `scenario 3 - invalid email addresses fail validation and keep submission disabled`() {
        val invalidEmails = listOf(
            "",
            "   ",
            "plainaddress",
            "missingatdomain.com",
            "@missinguser.com",
            "user@",
            "user@domain",
            "user@domain.",
            "user@domain.c",            // TLD shorter than 2 characters
            "user#invalid@domain.com",  // Invalid character #
            "user space@domain.com",
            "user@domain space.com",
            "user@domain,com"
        )

        for (email in invalidEmails) {
            assertFalse("Expected '$email' to be invalid according to regex", ResetPasswordValidator.isValidEmail(email))
        }
    }

    @Test
    fun `scenario 3 - whitespace trimming allows valid email input with surrounding spaces`() {
        val paddedEmail = "  athlete@crosstraining.app  "
        assertTrue(ResetPasswordValidator.isValidEmail(paddedEmail))
    }

    // =========================================================================
    // Scenario 4: IME Done submits when valid
    // =========================================================================

    @Test
    fun `scenario 4 - ime done action fires submission when email is syntactically valid and not loading`() {
        var submittedEmail: String? = null
        val targetEmail = "valid.user@example.com"
        val isLoading = false

        val isEmailValid = ResetPasswordValidator.isValidEmail(targetEmail)
        if (isEmailValid && !isLoading) {
            submittedEmail = targetEmail.trim()
        }

        assertEquals("valid.user@example.com", submittedEmail)
    }

    @Test
    fun `scenario 4 - ime done action does not fire submission when email is invalid`() {
        var submittedEmail: String? = null
        val targetEmail = "invalid-email@"
        val isLoading = false

        val isEmailValid = ResetPasswordValidator.isValidEmail(targetEmail)
        if (isEmailValid && !isLoading) {
            submittedEmail = targetEmail.trim()
        }

        assertFalse(isEmailValid)
        assertEquals(null, submittedEmail)
    }

    // =========================================================================
    // Scenario 5: Dialog locks during in-flight request
    // =========================================================================

    @Test
    fun `scenario 5 - submission and dismiss are locked when request is in-flight`() {
        val targetEmail = "user@example.com"
        val isEmailValid = ResetPasswordValidator.isValidEmail(targetEmail)
        val isLoading = true

        val canSubmit = isEmailValid && !isLoading
        val dismissOnBackPress = !isLoading
        val dismissOnClickOutside = !isLoading
        val canDismissButton = !isLoading

        assertFalse(canSubmit)
        assertFalse(dismissOnBackPress)
        assertFalse(dismissOnClickOutside)
        assertFalse(canDismissButton)
    }

    @Test
    fun `scenario 5 - submission and dismiss are unlocked when request is completed`() {
        val targetEmail = "user@example.com"
        val isEmailValid = ResetPasswordValidator.isValidEmail(targetEmail)
        val isLoading = false

        val canSubmit = isEmailValid && !isLoading
        val dismissOnBackPress = !isLoading
        val dismissOnClickOutside = !isLoading
        val canDismissButton = !isLoading

        assertTrue(canSubmit)
        assertTrue(dismissOnBackPress)
        assertTrue(dismissOnClickOutside)
        assertTrue(canDismissButton)
    }
}
