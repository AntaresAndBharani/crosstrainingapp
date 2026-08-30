package com.fractanomics.crosstraining.ui

import com.fractanomics.crosstraining.data.DataModeManager
import com.fractanomics.crosstraining.data.firebase.UserCloudSyncManager
import com.fractanomics.crosstraining.ui.components.PasswordResetErrorMapper
import com.google.firebase.FirebaseNetworkException
import com.google.firebase.auth.FirebaseAuthRecentLoginRequiredException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.io.IOException
import java.net.UnknownHostException

/**
 * Unit test suite for [AppViewModel] password reset flow (Issue #458).
 *
 * Feature: Password Reset Automated Test Suite
 *
 * Acceptance Criteria Covered:
 * - Scenario: ViewModel sanitizes email inputs
 *   When AppViewModel.sendPasswordReset is called with whitespace or uppercase
 *   Then the sanitized email is passed to the underlying sync manager
 *
 * - Scenario: Error exception mapping
 *   When an exception occurs during password reset dispatch
 *   Then the mapped human-readable error is returned in onResult
 *
 * - Scenario: Success feedback and snackbar triggering
 *   When sendPasswordReset succeeds
 *   Then onResult(true, null) is emitted and UI triggers standard success snackbar
 */
@OptIn(ExperimentalCoroutinesApi::class)
class AppViewModelPasswordResetTest {

    private val testDispatcher = UnconfinedTestDispatcher()
    private lateinit var dataModeManager: DataModeManager
    private lateinit var viewModel: AppViewModel

    @Before
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
        UserCloudSyncManager.setPasswordResetHandlerForTesting(null)
        UserCloudSyncManager.setAuthenticatedUser(null)
        dataModeManager = DataModeManager(null)
        viewModel = AppViewModel(dataModeManager)
    }

    @After
    fun tearDown() {
        UserCloudSyncManager.setPasswordResetHandlerForTesting(null)
        Dispatchers.resetMain()
    }

    // =========================================================================
    // Scenario 1: ViewModel sanitizes email inputs
    // =========================================================================

    @Test
    fun `scenario 1 - sendPasswordReset sanitizes whitespace and uppercase before dispatching to sync manager`() = runTest(testDispatcher) {
        var dispatchedEmail: String? = null
        UserCloudSyncManager.setPasswordResetHandlerForTesting { email ->
            dispatchedEmail = email
            Result.success(Unit)
        }

        var resultSuccess: Boolean? = null
        var resultError: String? = null

        viewModel.sendPasswordReset("   Athlete.Strength+WOD@Gym.ORG   ") { success, error ->
            resultSuccess = success
            resultError = error
        }

        assertEquals("athlete.strength+wod@gym.org", dispatchedEmail)
        assertTrue(resultSuccess == true)
        assertNull(resultError)
    }

    @Test
    fun `scenario 1 - sendPasswordReset normalizes shorthand usernames to standard domain emails`() = runTest(testDispatcher) {
        val cases = listOf(
            "  COACH  " to "coach@crosstraining.app",
            "  Athlete  " to "athlete@crosstraining.app",
            "  jAngelPv  " to "jangelpv@crosstraining.app"
        )

        for ((input, expected) in cases) {
            var dispatchedEmail: String? = null
            UserCloudSyncManager.setPasswordResetHandlerForTesting { email ->
                dispatchedEmail = email
                Result.success(Unit)
            }

            viewModel.sendPasswordReset(input) { _, _ -> }

            assertEquals("Expected '$input' to normalize to '$expected'", expected, dispatchedEmail)
        }
    }

    // =========================================================================
    // Scenario 2: Error exception mapping
    // =========================================================================

    @Test
    fun `scenario 2 - FirebaseNetworkException maps to user friendly network error message`() = runTest(testDispatcher) {
        UserCloudSyncManager.setPasswordResetHandlerForTesting {
            Result.failure(FirebaseNetworkException("Network disconnected"))
        }

        var resultSuccess: Boolean? = null
        var resultError: String? = null

        viewModel.sendPasswordReset("user@example.com") { success, error ->
            resultSuccess = success
            resultError = error
        }

        assertFalse(resultSuccess == true)
        assertEquals(PasswordResetErrorMapper.NETWORK_ERROR_MESSAGE, resultError)
        assertEquals("Network error. Please check your connection.", resultError)
    }

    @Test
    fun `scenario 2 - FirebaseAuthRecentLoginRequiredException maps to friendly rate limit message`() = runTest(testDispatcher) {
        UserCloudSyncManager.setPasswordResetHandlerForTesting {
            Result.failure(FirebaseAuthRecentLoginRequiredException("Too many attempts", "RECENT_LOGIN"))
        }

        var resultSuccess: Boolean? = null
        var resultError: String? = null

        viewModel.sendPasswordReset("user@example.com") { success, error ->
            resultSuccess = success
            resultError = error
        }

        assertFalse(resultSuccess == true)
        assertEquals(PasswordResetErrorMapper.RATE_LIMIT_ERROR_MESSAGE, resultError)
        assertEquals("Too many attempts. Try again later.", resultError)
    }

    @Test
    fun `scenario 2 - IOException and UnknownHostException map to network error message`() = runTest(testDispatcher) {
        val networkErrors = listOf(
            IOException("Connection aborted"),
            UnknownHostException("DNS failure")
        )

        for (exception in networkErrors) {
            UserCloudSyncManager.setPasswordResetHandlerForTesting {
                Result.failure(exception)
            }

            var resultSuccess: Boolean? = null
            var resultError: String? = null

            viewModel.sendPasswordReset("user@example.com") { success, error ->
                resultSuccess = success
                resultError = error
            }

            assertFalse(resultSuccess == true)
            assertEquals("Network error. Please check your connection.", resultError)
        }
    }

    @Test
    fun `scenario 2 - custom localized exception message is preserved in UI output`() = runTest(testDispatcher) {
        UserCloudSyncManager.setPasswordResetHandlerForTesting {
            Result.failure(IllegalStateException("Account disabled by administrator"))
        }

        var resultSuccess: Boolean? = null
        var resultError: String? = null

        viewModel.sendPasswordReset("user@example.com") { success, error ->
            resultSuccess = success
            resultError = error
        }

        assertFalse(resultSuccess == true)
        assertEquals("Account disabled by administrator", resultError)
    }

    @Test
    fun `scenario 2 - blank exception message maps to generic fallback error message`() = runTest(testDispatcher) {
        UserCloudSyncManager.setPasswordResetHandlerForTesting {
            Result.failure(RuntimeException(""))
        }

        var resultSuccess: Boolean? = null
        var resultError: String? = null

        viewModel.sendPasswordReset("user@example.com") { success, error ->
            resultSuccess = success
            resultError = error
        }

        assertFalse(resultSuccess == true)
        assertEquals(PasswordResetErrorMapper.GENERIC_ERROR_MESSAGE, resultError)
        assertEquals("An error occurred. Please try again.", resultError)
    }

    // =========================================================================
    // Scenario 3: Success feedback and snackbar triggering
    // =========================================================================

    @Test
    fun `scenario 3 - sendPasswordReset success emits onResult(true, null) for snackbar presentation`() = runTest(testDispatcher) {
        UserCloudSyncManager.setPasswordResetHandlerForTesting {
            Result.success(Unit)
        }

        var resultSuccess: Boolean? = null
        var resultError: String? = null

        viewModel.sendPasswordReset("athlete@fractanomics.com") { success, error ->
            resultSuccess = success
            resultError = error
        }

        assertTrue(resultSuccess == true)
        assertNull(resultError)
        assertEquals(
            "If an account exists, a setup link has been sent to your inbox.",
            PasswordResetErrorMapper.SUCCESS_SNACKBAR_MESSAGE
        )
    }
}
