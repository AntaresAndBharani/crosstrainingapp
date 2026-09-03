package com.fractanomics.crosstraining.ui.screens

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.fractanomics.crosstraining.data.firebase.CloudSyncErrorMapper
import com.fractanomics.crosstraining.data.firebase.SyncStatus
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Compose UI test suite for [CloudBackupSyncCard] and [LegacySessionReauthBanner].
 * Verifies Acceptance Criteria for Issue #496 (Subtask #492.4):
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
@RunWith(AndroidJUnit4::class)
class ProfileScreenSyncRecoveryTest {

    @get:Rule
    val composeTestRule = createComposeRule()

    // =========================================================================
    // Scenario 1: Actionable Inline Error Card & Debounced Retry
    // =========================================================================

    @Test
    fun cloudBackupSyncCard_whenSessionExpired_displaysErrorBadgeAndInlineMessageAndSignInButton() {
        var signInAgainClicked = false

        composeTestRule.setContent {
            CloudBackupSyncCard(
                syncState = SyncStatus.ERROR,
                lastSyncError = "Session expired. Please sign in again to back up your workouts",
                isCooldownActive = true,
                onSyncNow = {},
                onSignInAgain = { signInAgainClicked = true },
                onRecoverCloudRoutines = {},
                onReseedDefaults = {}
            )
        }

        // Then: Cloud Backup & Sync card transitions to ERROR badge
        composeTestRule.onNodeWithText("Cloud Backup & Sync").assertIsDisplayed()
        composeTestRule.onNodeWithText("ERROR").assertIsDisplayed()

        // And: inline error container displays "Session expired. Please sign in again to back up your workouts"
        composeTestRule.onNodeWithText("Session expired. Please sign in again to back up your workouts").assertIsDisplayed()

        // And: a prominent [Sign In Again] button is displayed
        val signInButton = composeTestRule.onNodeWithText("Sign In Again")
        signInButton.assertIsDisplayed()
        signInButton.performClick()
        assertTrue("Clicking Sign In Again should invoke callback", signInAgainClicked)

        // And: the "Sync Now" button is in cooldown (disabled)
        val syncNowButton = composeTestRule.onNodeWithText("Sync Now")
        syncNowButton.assertIsDisplayed()
        syncNowButton.assertIsNotEnabled()
    }

    @Test
    fun cloudBackupSyncCard_whenErrorWithGuestAuthPrompt_mapsToSessionExpiredMessage() {
        composeTestRule.setContent {
            CloudBackupSyncCard(
                syncState = SyncStatus.ERROR,
                lastSyncError = CloudSyncErrorMapper.GUEST_AUTH_PROMPT,
                isCooldownActive = false,
                onSyncNow = {},
                onSignInAgain = {},
                onRecoverCloudRoutines = {},
                onReseedDefaults = {}
            )
        }

        // Then: maps to session expired message
        composeTestRule.onNodeWithText("Session expired. Please sign in again to back up your workouts").assertIsDisplayed()
        composeTestRule.onNodeWithText("Sign In Again").assertIsDisplayed()
        composeTestRule.onNodeWithText("Sync Now").assertIsEnabled()
    }

    @Test
    fun cloudBackupSyncCard_whenErrorWithNullMessage_defaultsToSessionExpiredMessage() {
        composeTestRule.setContent {
            CloudBackupSyncCard(
                syncState = SyncStatus.ERROR,
                lastSyncError = null,
                isCooldownActive = false,
                onSyncNow = {},
                onSignInAgain = {},
                onRecoverCloudRoutines = {},
                onReseedDefaults = {}
            )
        }

        // Then: defaults to session expired message
        composeTestRule.onNodeWithText("Session expired. Please sign in again to back up your workouts").assertIsDisplayed()
        composeTestRule.onNodeWithText("Sign In Again").assertIsDisplayed()
    }

    @Test
    fun cloudBackupSyncCard_whenCooldownActive_syncNowButtonIsDisabled() {
        var syncNowClicked = false

        composeTestRule.setContent {
            CloudBackupSyncCard(
                syncState = SyncStatus.IDLE,
                lastSyncError = null,
                isCooldownActive = true,
                onSyncNow = { syncNowClicked = true },
                onSignInAgain = {},
                onRecoverCloudRoutines = {},
                onReseedDefaults = {}
            )
        }

        val syncNowButton = composeTestRule.onNodeWithText("Sync Now")
        syncNowButton.assertIsDisplayed()
        syncNowButton.assertIsNotEnabled()
        assertFalse(syncNowClicked)
    }

    @Test
    fun cloudBackupSyncCard_whenCooldownInactiveAndIdle_syncNowButtonIsEnabled() {
        var syncNowClicked = false

        composeTestRule.setContent {
            CloudBackupSyncCard(
                syncState = SyncStatus.IDLE,
                lastSyncError = null,
                isCooldownActive = false,
                onSyncNow = { syncNowClicked = true },
                onSignInAgain = {},
                onRecoverCloudRoutines = {},
                onReseedDefaults = {}
            )
        }

        val syncNowButton = composeTestRule.onNodeWithText("Sync Now")
        syncNowButton.assertIsDisplayed()
        syncNowButton.assertIsEnabled()

        syncNowButton.performClick()
        assertTrue(syncNowClicked)
    }

    @Test
    fun cloudBackupSyncCard_dynamicCooldownTransition_togglesSyncNowEnabledState() {
        var cooldown by mutableStateOf(true)

        composeTestRule.setContent {
            CloudBackupSyncCard(
                syncState = SyncStatus.ERROR,
                lastSyncError = "Session expired. Please sign in again to back up your workouts",
                isCooldownActive = cooldown,
                onSyncNow = {},
                onSignInAgain = {},
                onRecoverCloudRoutines = {},
                onReseedDefaults = {}
            )
        }

        // Initially in cooldown following failure
        composeTestRule.onNodeWithText("Sync Now").assertIsNotEnabled()

        // After 5s cooldown expires
        cooldown = false
        composeTestRule.waitForIdle()

        composeTestRule.onNodeWithText("Sync Now").assertIsEnabled()
    }

    // =========================================================================
    // Scenario 2: Legacy Session Re-Auth Banner
    // =========================================================================

    @Test
    fun legacySessionReauthBanner_displaysSecurityUpdateMessageAndTriggersSignInAgain() {
        var signInAgainClicked = false
        var dismissed = false

        composeTestRule.setContent {
            LegacySessionReauthBanner(
                onSignInAgain = { signInAgainClicked = true },
                onDismiss = { dismissed = true }
            )
        }

        // Then: inline security banner displays expected text
        composeTestRule.onNodeWithText("Security update: Please sign in again to connect your cloud account").assertIsDisplayed()

        // And: [Sign In Again] button is displayed and clickable
        val signInButton = composeTestRule.onNodeWithText("Sign In Again")
        signInButton.assertIsDisplayed()
        signInButton.performClick()
        assertTrue(signInAgainClicked)
    }

    @Test
    fun legacySessionReauthBanner_clearedWhenLegacySessionRequiresReauthBecomesFalse() {
        var legacyRequiresReauth by mutableStateOf(true)

        composeTestRule.setContent {
            if (legacyRequiresReauth) {
                LegacySessionReauthBanner(
                    onSignInAgain = {
                        // Completing sign-in clears the banner
                        legacyRequiresReauth = false
                    }
                )
            }
        }

        // Given: legacySessionRequiresReauth is true
        composeTestRule.onNodeWithText("Security update: Please sign in again to connect your cloud account").assertIsDisplayed()

        // When: User taps "Sign In Again" and completes sign-in
        composeTestRule.onNodeWithText("Sign In Again").performClick()
        composeTestRule.waitForIdle()

        // Then: Banner is cleared and removed from the composition
        composeTestRule.onNodeWithText("Security update: Please sign in again to connect your cloud account").assertDoesNotExist()
        assertFalse(legacyRequiresReauth)
    }
}
