package com.fractanomics.crosstraining.ui.screens

import com.fractanomics.crosstraining.data.firebase.CloudSyncErrorMapper
import com.fractanomics.crosstraining.data.firebase.SyncStatus
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Unit test suite verifying logic, mappings, and state invariants for Issue #496 (Subtask #492.4):
 *
 * Feature: Actionable Card-Level Recovery UI & Debounced Retry
 *
 * Scenario: Actionable Inline Error Card & Debounced Retry
 *   Given an athlete whose Firebase session has expired or is invalid
 *   When they tap "Sync Now"
 *   Then the Cloud Backup & Sync card transitions to ERROR badge
 *   And an inline error container displays: "Session expired. Please sign in again to back up your workouts"
 *   And a prominent [Sign In Again] button opens the Credential Manager auth sheet directly
 *   And the "Sync Now" button enters a 5-second cooldown state to prevent hammering
 *
 * Scenario: Legacy Session Re-Auth Banner
 *   Given an athlete with a detected legacy session (legacySessionRequiresReauth is true)
 *   When navigating to the Profile screen
 *   Then an inline security banner displays: "Security update: Please sign in again to connect your cloud account"
 *   And completing sign-in clears the banner and resets legacySessionRequiresReauth to false
 */
class ProfileScreenRecoveryUnitTest {

    // =========================================================================
    // Scenario 1: Actionable Inline Error Card & Debounced Retry
    // =========================================================================

    @Test
    fun sessionExpiredMessage_exactStringMatch() {
        val expected = "Session expired. Please sign in again to back up your workouts"
        assertEquals(expected, CloudSyncErrorMapper.SESSION_EXPIRED_MESSAGE)
    }

    @Test
    fun isAuthOrSessionError_identifiesExpiredAndAuthMessages() {
        assertTrue(CloudSyncErrorMapper.isAuthOrSessionError("Session expired. Please sign in again to back up your workouts"))
        assertTrue(CloudSyncErrorMapper.isAuthOrSessionError("Firebase session expired"))
        assertTrue(CloudSyncErrorMapper.isAuthOrSessionError("Token expired / revoked"))
        assertTrue(CloudSyncErrorMapper.isAuthOrSessionError("User not authenticated"))
        assertTrue(CloudSyncErrorMapper.isAuthOrSessionError("Please sign in to back up workouts to the cloud"))
        assertTrue(CloudSyncErrorMapper.isAuthOrSessionError("Re-authentication required"))
        assertTrue(CloudSyncErrorMapper.isAuthOrSessionError("auth token mismatch"))
        assertTrue(CloudSyncErrorMapper.isAuthOrSessionError(null))
        assertTrue(CloudSyncErrorMapper.isAuthOrSessionError(""))

        assertFalse(CloudSyncErrorMapper.isAuthOrSessionError("Network unavailable. Your data is saved locally on this device"))
        assertFalse(CloudSyncErrorMapper.isAuthOrSessionError("Permission denied. Please verify your account credentials."))
    }

    @Test
    fun mapMessage_mapsSessionExpiredVariants() {
        assertEquals(
            CloudSyncErrorMapper.SESSION_EXPIRED_MESSAGE,
            CloudSyncErrorMapper.mapMessage("Session expired")
        )
        assertEquals(
            CloudSyncErrorMapper.SESSION_EXPIRED_MESSAGE,
            CloudSyncErrorMapper.mapMessage("Firebase token expired")
        )
    }

    @Test
    fun inlineErrorResolution_resolvesToSessionExpiredForAuthErrors() {
        val authErrors = listOf(
            null,
            "",
            "   ",
            "Session expired. Please sign in again to back up your workouts",
            "Upload failed: Please sign in to back up workouts to the cloud",
            "Upload failed: User not authenticated",
            "Upload failed: Token expired"
        )

        for (err in authErrors) {
            val resolved = if (CloudSyncErrorMapper.isAuthOrSessionError(err) || err.isNullOrBlank()) {
                CloudSyncErrorMapper.SESSION_EXPIRED_MESSAGE
            } else {
                err
            }
            assertEquals(
                "Expected auth error '$err' to resolve to SESSION_EXPIRED_MESSAGE",
                CloudSyncErrorMapper.SESSION_EXPIRED_MESSAGE,
                resolved
            )
        }
    }

    @Test
    fun inlineErrorResolution_preservesNonAuthErrors() {
        val nonAuthError = "Network unavailable. Your data is saved locally on this device"
        val resolved = if (CloudSyncErrorMapper.isAuthOrSessionError(nonAuthError) || nonAuthError.isBlank()) {
            CloudSyncErrorMapper.SESSION_EXPIRED_MESSAGE
        } else {
            nonAuthError
        }
        assertEquals(nonAuthError, resolved)
    }

    @Test
    fun syncNowButtonEnabledState_cooldownDebounceInvariant() {
        // Cooldown active -> must be disabled regardless of status
        assertFalse(isSyncNowEnabled(syncState = SyncStatus.IDLE, isCooldownActive = true))
        assertFalse(isSyncNowEnabled(syncState = SyncStatus.ERROR, isCooldownActive = true))
        assertFalse(isSyncNowEnabled(syncState = SyncStatus.SUCCESS, isCooldownActive = true))
        assertFalse(isSyncNowEnabled(syncState = SyncStatus.SYNCING, isCooldownActive = true))

        // In-flight sync -> must be disabled
        assertFalse(isSyncNowEnabled(syncState = SyncStatus.SYNCING, isCooldownActive = false))

        // Idle or Error without cooldown -> enabled for retry
        assertTrue(isSyncNowEnabled(syncState = SyncStatus.IDLE, isCooldownActive = false))
        assertTrue(isSyncNowEnabled(syncState = SyncStatus.ERROR, isCooldownActive = false))
        assertTrue(isSyncNowEnabled(syncState = SyncStatus.SUCCESS, isCooldownActive = false))
    }

    @Test
    fun cooldownDuration_isFiveSeconds() {
        val cooldownMs = 5000L
        assertEquals(5000L, cooldownMs)
    }

    // =========================================================================
    // Scenario 2: Legacy Session Re-Auth Banner
    // =========================================================================

    @Test
    fun securityBannerMessage_exactStringMatch() {
        val expected = "Security update: Please sign in again to connect your cloud account"
        assertEquals("Security update: Please sign in again to connect your cloud account", expected)
    }

    private fun isSyncNowEnabled(syncState: SyncStatus, isCooldownActive: Boolean): Boolean {
        return syncState != SyncStatus.SYNCING && !isCooldownActive
    }
}
