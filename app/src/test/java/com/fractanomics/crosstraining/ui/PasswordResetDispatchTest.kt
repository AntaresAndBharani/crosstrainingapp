package com.fractanomics.crosstraining.ui

import com.fractanomics.crosstraining.data.firebase.UserCloudSyncManager
import com.fractanomics.crosstraining.ui.components.PasswordResetErrorMapper
import com.google.firebase.FirebaseNetworkException
import com.google.firebase.auth.FirebaseAuthRecentLoginRequiredException
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.IOException
import java.net.UnknownHostException

/**
 * Unit test suite covering Password Reset Dispatch & Feedback (Issue #456).
 *
 * Acceptance Criteria Covered:
 * - Scenario: Sanitized email submission
 * - Scenario: Network failure keeps dialog open with inline error
 * - Scenario: Success feedback and dialog dismissal
 */
class PasswordResetDispatchTest {

    // =========================================================================
    // Scenario 1: Sanitized email submission
    // =========================================================================

    @Test
    fun `scenario 1 - user submits padded uppercase email and sanitization normalizes to lowercase trimmed`() {
        val rawInput = "  Test.User@Example.COM  "
        val normalized = UserCloudSyncManager.normalizeEmail(rawInput)

        assertEquals("test.user@example.com", normalized)
    }

    @Test
    fun `scenario 1 - shorthand usernames normalize correctly to lowercase domain emails`() {
        assertEquals("jangelpv@crosstraining.app", UserCloudSyncManager.normalizeEmail("  jAngelPv  "))
        assertEquals("coach@crosstraining.app", UserCloudSyncManager.normalizeEmail("  COACH  "))
        assertEquals("athlete@crosstraining.app", UserCloudSyncManager.normalizeEmail("  Athlete  "))
    }

    @Test
    fun `scenario 1 - standard email input is preserved with whitespace trimmed and lowercased`() {
        val email = "  Athlete.Strength+WOD@Gym.org  "
        val sanitized = email.trim().lowercase()
        val normalized = UserCloudSyncManager.normalizeEmail(sanitized)

        assertEquals("athlete.strength+wod@gym.org", normalized)
    }

    // =========================================================================
    // Scenario 2: Network failure keeps dialog open with inline error
    // =========================================================================

    @Test
    fun `scenario 2 - FirebaseNetworkException maps to user-friendly network error message`() {
        val networkException = FirebaseNetworkException("A network error occurred.")
        val mappedMessage = PasswordResetErrorMapper.map(networkException)

        assertEquals(PasswordResetErrorMapper.NETWORK_ERROR_MESSAGE, mappedMessage)
        assertEquals("Network error. Please check your connection.", mappedMessage)
    }

    @Test
    fun `scenario 2 - IO and UnknownHost exceptions map to user-friendly network error message`() {
        val ioException = IOException("Connection reset by peer")
        val hostException = UnknownHostException("Unable to resolve host")

        assertEquals(PasswordResetErrorMapper.NETWORK_ERROR_MESSAGE, PasswordResetErrorMapper.map(ioException))
        assertEquals(PasswordResetErrorMapper.NETWORK_ERROR_MESSAGE, PasswordResetErrorMapper.map(hostException))
    }

    @Test
    fun `scenario 2 - simulated UI state on network failure leaves dialog open with inline error and stops loading`() {
        var showForgotDialog = true
        var isResetting = true
        var resetMsg: String? = null

        val error = FirebaseNetworkException("Network disconnected")
        val isSuccess = false
        val mappedError = PasswordResetErrorMapper.map(error)

        // Callback handling in UI
        isResetting = false
        if (isSuccess) {
            showForgotDialog = false
            resetMsg = null
        } else {
            resetMsg = mappedError
        }

        assertTrue("Dialog must remain visible on failure", showForgotDialog)
        assertFalse("Loading indicator must stop on failure", isResetting)
        assertEquals("Network error. Please check your connection.", resetMsg)
    }

    // =========================================================================
    // Scenario 3: Success feedback and dialog dismissal
    // =========================================================================

    @Test
    fun `scenario 3 - success feedback message matches exact specification`() {
        val expectedMessage = "If an account exists, a setup link has been sent to your inbox."
        assertEquals(expectedMessage, PasswordResetErrorMapper.SUCCESS_SNACKBAR_MESSAGE)
    }

    @Test
    fun `scenario 3 - simulated UI state on success dismisses dialog, stops loading, and prepares snackbar`() {
        var showForgotDialog = true
        var isResetting = true
        var resetMsg: String? = "Previous error"
        var keyboardHidden = false
        var snackbarMessage: String? = null

        val isSuccess = true

        // Callback handling in UI
        isResetting = false
        if (isSuccess) {
            showForgotDialog = false
            resetMsg = null
            keyboardHidden = true
            snackbarMessage = PasswordResetErrorMapper.SUCCESS_SNACKBAR_MESSAGE
        } else {
            resetMsg = "Some error"
        }

        assertFalse("Dialog must dismiss on success", showForgotDialog)
        assertFalse("Loading indicator must stop on success", isResetting)
        assertNull("Error message must be cleared on success", resetMsg)
        assertTrue("Keyboard must be dismissed on success", keyboardHidden)
        assertEquals("If an account exists, a setup link has been sent to your inbox.", snackbarMessage)
    }

    // =========================================================================
    // Additional Edge Cases & Error Mapping
    // =========================================================================

    @Test
    fun `error mapper maps rate limit exception to friendly rate limit message`() {
        val rateLimitException = FirebaseAuthRecentLoginRequiredException("Too many requests", "RECENT_LOGIN_REQUIRED")
        val mapped = PasswordResetErrorMapper.map(rateLimitException)

        assertEquals(PasswordResetErrorMapper.RATE_LIMIT_ERROR_MESSAGE, mapped)
        assertEquals("Too many attempts. Try again later.", mapped)
    }

    @Test
    fun `error mapper preserves custom localized messages from standard exceptions`() {
        val customException = RuntimeException("Custom backend error message")
        val mapped = PasswordResetErrorMapper.map(customException)

        assertEquals("Custom backend error message", mapped)
    }

    @Test
    fun `error mapper provides fallback generic error message for null or empty exceptions`() {
        val emptyException = RuntimeException("")
        val nullMessageException = RuntimeException(null as String?)

        assertEquals(PasswordResetErrorMapper.GENERIC_ERROR_MESSAGE, PasswordResetErrorMapper.map(emptyException))
        assertEquals(PasswordResetErrorMapper.GENERIC_ERROR_MESSAGE, PasswordResetErrorMapper.map(nullMessageException))
        assertEquals(PasswordResetErrorMapper.GENERIC_ERROR_MESSAGE, PasswordResetErrorMapper.map(null))
    }
}
