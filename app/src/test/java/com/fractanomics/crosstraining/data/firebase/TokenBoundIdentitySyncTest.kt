package com.fractanomics.crosstraining.data.firebase

import com.fractanomics.crosstraining.data.FakeSampleAppDatabase
import com.fractanomics.crosstraining.data.FakeTransactionRunner
import com.fractanomics.crosstraining.data.Repository
import com.fractanomics.crosstraining.data.model.BlockKind
import com.fractanomics.crosstraining.data.model.Routine
import com.fractanomics.crosstraining.data.model.RoutineBlock
import android.content.SharedPreferences
import com.fractanomics.crosstraining.data.DataModeManager
import com.fractanomics.crosstraining.ui.AppViewModel
import com.fractanomics.crosstraining.ui.CloudSyncResult
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.delay
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
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

/**
 * Unit & integration tests for Issue #486 and Issue #493 ([Subtask #492.1]):
 * - Issue #486: Token-bound identity verification, scoped recovery, JVM test seam.
 * - Issue #493: Upstream UID sanitization in DataModeManager.saveAuthSession,
 *   cold-start legacy email UID detection and re-auth migration in AppViewModel,
 *   and handling of fabricated UIDs.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class TokenBoundIdentitySyncTest {

    private val testDispatcher = StandardTestDispatcher()
    private lateinit var db: FakeSampleAppDatabase
    private lateinit var repo: Repository

    private val writtenDocuments = mutableMapOf<String, Map<String, Any?>>()
    private var recoveredUserIdQueried: String? = null

    @Before
    fun setUp() {
        Dispatchers.setMain(testDispatcher)

        db = FakeSampleAppDatabase()
        repo = Repository(db, FakeTransactionRunner(db))
        writtenDocuments.clear()
        recoveredUserIdQueried = null

        UserCloudSyncManager.resetTestHandlers()

        // Configure test document writer so upload does not invoke live Firestore
        UserCloudSyncManager.documentWriterForTesting = { collectionName, data ->
            writtenDocuments[collectionName] = data
        }
    }

    @After
    fun tearDown() {
        UserCloudSyncManager.resetTestHandlers()
        UserCloudSyncManager.setAuthenticatedUser(null)
        Dispatchers.resetMain()
    }

    // =========================================================================
    // Scenario: Token-bound identity verification and error handling
    // =========================================================================

    @Test
    fun uploadUserData_succeeds_whenAuthTokenMatchesNonAnonymousUser() = runTest {
        // Given an authenticated user in Real Data mode with a matching Firebase token
        val user = AuthUser(uid = "valid_user_123", email = "athlete@example.com", isAnonymous = false)
        UserCloudSyncManager.setAuthenticatedUser(user)
        UserCloudSyncManager.authUidProviderForTesting = { "valid_user_123" }

        // When uploadUserData is executed against repo
        val result = UserCloudSyncManager.uploadUserData(repo)

        // Then it succeeds and reports SUCCESS
        assertTrue("Upload should succeed with matching token", result.isSuccess)
        assertEquals(SyncStatus.SUCCESS, UserCloudSyncManager.syncState.value)
    }

    @Test
    fun uploadUserData_failsClosed_whenAuthTokenIsMismatched() = runTest {
        // Given a non-anonymous user in userState
        val user = AuthUser(uid = "valid_user_123", email = "athlete@example.com", isAnonymous = false)
        UserCloudSyncManager.setAuthenticatedUser(user)

        // But authUidProviderForTesting simulates a token for a different user (mismatch)
        UserCloudSyncManager.authUidProviderForTesting = { "wrong_token_user_999" }

        // When uploadUserData is executed
        val result = UserCloudSyncManager.uploadUserData(repo)

        // Then it fails closed with re-authentication prompt
        assertTrue("Upload must fail closed when token is mismatched", result.isFailure)
        assertEquals(SyncStatus.ERROR, UserCloudSyncManager.syncState.value)
        val ex = result.exceptionOrNull()
        assertTrue(ex is IllegalStateException)
        assertEquals(CloudSyncErrorMapper.GUEST_AUTH_PROMPT, ex?.message)
    }

    @Test
    fun uploadUserData_failsClosed_whenAuthTokenIsNullForNonAnonymousUser() = runTest {
        // Given a non-anonymous user in userState
        val user = AuthUser(uid = "valid_user_123", email = "athlete@example.com", isAnonymous = false)
        UserCloudSyncManager.setAuthenticatedUser(user)

        // But authUidProviderForTesting returns null (token expired / session lost)
        UserCloudSyncManager.authUidProviderForTesting = { null }

        // When uploadUserData is executed
        val result = UserCloudSyncManager.uploadUserData(repo)

        // Then it fails closed prompting re-authentication
        assertTrue("Upload must fail closed when token is null", result.isFailure)
        assertEquals(SyncStatus.ERROR, UserCloudSyncManager.syncState.value)
        val ex = result.exceptionOrNull()
        assertEquals(CloudSyncErrorMapper.GUEST_AUTH_PROMPT, ex?.message)
    }

    @Test
    fun downloadUserData_failsClosed_whenAuthTokenIsMismatched() = runTest {
        val user = AuthUser(uid = "user_abc", email = "coach@example.com", isAnonymous = false)
        UserCloudSyncManager.setAuthenticatedUser(user)
        UserCloudSyncManager.authUidProviderForTesting = { "different_token_xyz" }

        val result = UserCloudSyncManager.downloadUserData(repo)

        assertTrue("Download must fail closed when token is mismatched", result.isFailure)
        assertEquals(SyncStatus.ERROR, UserCloudSyncManager.syncState.value)
        assertEquals(CloudSyncErrorMapper.GUEST_AUTH_PROMPT, result.exceptionOrNull()?.message)
    }

    // =========================================================================
    // Scenario: Scoped cloud routine recovery
    // =========================================================================

    @Test
    fun recoverAllCloudRoutines_isStrictlyScopedToCurrentUserPath() = runTest {
        // Given an authenticated user
        val user = AuthUser(uid = "athlete_scoped_uid", email = "athlete@example.com", isAnonymous = false)
        UserCloudSyncManager.setAuthenticatedUser(user)
        UserCloudSyncManager.authUidProviderForTesting = { "athlete_scoped_uid" }

        // Configure recovery reader seam to verify scoped query
        UserCloudSyncManager.recoverRoutinesReaderForTesting = { userId ->
            recoveredUserIdQueried = userId
            listOf(
                mapOf(
                    "routine" to mapOf(
                        "name" to "Scoped Murph",
                        "description" to "Strictly user-scoped routine",
                        "defaultFormat" to "For Time"
                    ),
                    "blocks" to listOf(
                        mapOf(
                            "name" to "Run 1 Mile",
                            "kind" to "CARDIO",
                            "format" to "For Time",
                            "setsCount" to 1
                        )
                    )
                )
            )
        }

        // When recoverAllCloudRoutines is invoked
        val result = UserCloudSyncManager.recoverAllCloudRoutines(repo)

        // Then query is strictly scoped to currentUserId
        assertTrue(result.isSuccess)
        assertEquals(1, result.getOrNull())
        assertEquals("athlete_scoped_uid", recoveredUserIdQueried)

        // And routine was saved locally in repo
        val savedRoutines = repo.getAllRoutinesWithBlocksOnce()
        assertTrue(savedRoutines.any { it.routine.name == "Scoped Murph" })
    }

    @Test
    fun recoverAllCloudRoutines_returnsZero_whenUserIsNotAuthenticated() = runTest {
        UserCloudSyncManager.setAuthenticatedUser(null)
        UserCloudSyncManager.authUidProviderForTesting = { null }

        val result = UserCloudSyncManager.recoverAllCloudRoutines(repo)

        assertTrue(result.isSuccess)
        assertEquals(0, result.getOrNull())
    }

    // =========================================================================
    // Scenario: JVM test seam determinism
    // =========================================================================

    @Test
    fun authUidProviderForTesting_providesDeterministicJvmUid() {
        UserCloudSyncManager.setAuthenticatedUser(null)
        UserCloudSyncManager.authUidProviderForTesting = { "mock_jvm_uid_999" }

        assertEquals("mock_jvm_uid_999", UserCloudSyncManager.currentUserId)

        // When provider returns null
        UserCloudSyncManager.authUidProviderForTesting = { null }
        assertEquals("", UserCloudSyncManager.currentUserId)
    }

    // =========================================================================
    // Issue #493 / Subtask #492.1: Upstream UID Sanitization
    // =========================================================================

    @Test
    fun saveAuthSession_rejectsUidContainingAtSign_gracefullyWithoutWriting() {
        val fakePrefs = FakeTrackingSharedPreferences()
        val manager = DataModeManager(context = null, sharedPreferences = fakePrefs)

        // When saveAuthSession is called with an email string as UID
        manager.saveAuthSession(email = "athlete@example.com", uid = "athlete@example.com")

        // Then it gracefully returns without writing the invalid UID to SharedPreferences
        assertNull("savedUserUid must not be written when uid contains @", fakePrefs.getString("savedUserUid", null))
        assertFalse("fakePrefs must not contain savedUserUid", fakePrefs.contains("savedUserUid"))
        assertNull("getPersistedAuthUser must return null", manager.getPersistedAuthUser())
    }

    @Test
    fun saveAuthSession_rejectsBlankOrEmptyUid_gracefullyWithoutWriting() {
        val fakePrefs = FakeTrackingSharedPreferences()
        val manager = DataModeManager(context = null, sharedPreferences = fakePrefs)

        manager.saveAuthSession(email = "athlete@example.com", uid = "   ")
        assertNull(fakePrefs.getString("savedUserUid", null))
        assertFalse(fakePrefs.contains("savedUserUid"))

        manager.saveAuthSession(email = "athlete@example.com", uid = "")
        assertNull(fakePrefs.getString("savedUserUid", null))
        assertFalse(fakePrefs.contains("savedUserUid"))
    }

    @Test
    fun saveAuthSession_preservesExistingUid_whenSubsequentCallHasInvalidUid() {
        val fakePrefs = FakeTrackingSharedPreferences()
        val manager = DataModeManager(context = null, sharedPreferences = fakePrefs)

        // Given a valid session was previously persisted
        manager.saveAuthSession(email = "athlete@example.com", uid = "valid_firebase_uid_123")
        assertEquals("valid_firebase_uid_123", fakePrefs.getString("savedUserUid", null))

        // When a call with malformed email UID is made
        manager.saveAuthSession(email = "athlete@example.com", uid = "malformed@example.com")

        // Then the invalid UID is rejected and the existing valid UID is not overwritten by it
        assertEquals("valid_firebase_uid_123", fakePrefs.getString("savedUserUid", null))
    }

    // =========================================================================
    // Issue #493 / Subtask #492.1: Clean Startup Migration for Legacy Email UIDs
    // =========================================================================

    @Test
    fun coldStart_detectsLegacyEmailUid_clearsCorruptedSession_emitsReauthRequirement_andVerifyTokenBindingSucceeds() = runTest {
        // Given a user installation from an earlier version with persisted user.uid containing "@" (email string)
        val legacyPrefs = FakeTrackingSharedPreferences(
            mapOf(
                "savedUserEmail" to "legacy_athlete@example.com",
                "savedUserUid" to "legacy_athlete@example.com",
                "savedUserIsAnon" to false,
                "rememberMe" to true
            )
        )
        val dataMode = DataModeManager(context = null, sharedPreferences = legacyPrefs)
        dataMode.setRepositoryForTesting(repo)

        // When the application completes cold launch
        val viewModel = AppViewModel(dataMode)
        advanceUntilIdle()

        // Then AppViewModel detects the legacy UID format, clears the malformed session from SharedPreferences
        assertNull("Malformed session must be cleared from SharedPreferences", legacyPrefs.getString("savedUserUid", null))
        assertNull("getPersistedAuthUser must return null after migration", dataMode.getPersistedAuthUser())
        assertNull("In-memory authUser must be cleared to null", UserCloudSyncManager.userState.value)

        // And legacySessionRequiresReauth emits true
        assertTrue("legacySessionRequiresReauth must emit true", viewModel.legacySessionRequiresReauth.value)

        // And verifyTokenBinding does not fail closed with unhandled exceptions
        val bindingResult = UserCloudSyncManager.verifyTokenBinding()
        assertTrue("verifyTokenBinding must succeed without failing closed on cleared session", bindingResult.isSuccess)
    }

    @Test
    fun coldStart_withValidNonEmailUid_preservesSession_andLegacyReauthRemainsFalse() = runTest {
        val validPrefs = FakeTrackingSharedPreferences(
            mapOf(
                "savedUserEmail" to "athlete@example.com",
                "savedUserUid" to "valid_firebase_uid_789",
                "savedUserIsAnon" to false,
                "rememberMe" to true
            )
        )
        val dataMode = DataModeManager(context = null, sharedPreferences = validPrefs)
        dataMode.setRepositoryForTesting(repo)

        val viewModel = AppViewModel(dataMode)
        advanceUntilIdle()

        // Session preserved
        assertEquals("valid_firebase_uid_789", validPrefs.getString("savedUserUid", null))
        assertEquals("valid_firebase_uid_789", UserCloudSyncManager.userState.value?.uid)
        assertFalse("legacySessionRequiresReauth must remain false for valid UIDs", viewModel.legacySessionRequiresReauth.value)
    }

    @Test
    fun legacySessionRequiresReauth_resetsToFalse_onSuccessfulLogin() = runTest {
        val legacyPrefs = FakeTrackingSharedPreferences(
            mapOf(
                "savedUserEmail" to "athlete@example.com",
                "savedUserUid" to "athlete@example.com",
                "rememberMe" to true
            )
        )
        val dataMode = DataModeManager(context = null, sharedPreferences = legacyPrefs)
        dataMode.setRepositoryForTesting(repo)

        val viewModel = AppViewModel(dataMode)
        advanceUntilIdle()
        assertTrue(viewModel.legacySessionRequiresReauth.value)

        // Configure test handler to simulate successful re-authentication
        UserCloudSyncManager.logInWithEmailHandler = { _, _ ->
            UserCloudSyncManager.setAuthenticatedUser(
                AuthUser(uid = "new_real_uid_999", email = "athlete@example.com", isAnonymous = false)
            )
            Result.success(Unit)
        }

        viewModel.logInWithEmail("athlete@example.com", "validPass123") { success, _ ->
            assertTrue(success)
        }
        advanceUntilIdle()

        // Then legacySessionRequiresReauth resets to false
        assertFalse("legacySessionRequiresReauth must reset to false upon successful login", viewModel.legacySessionRequiresReauth.value)
    }

    @Test
    fun legacySessionRequiresReauth_resetsToFalse_onSuccessfulSync() = runTest {
        val legacyPrefs = FakeTrackingSharedPreferences(
            mapOf(
                "savedUserEmail" to "athlete@example.com",
                "savedUserUid" to "athlete@example.com",
                "rememberMe" to true
            )
        )
        val dataMode = DataModeManager(context = null, sharedPreferences = legacyPrefs)
        dataMode.setRepositoryForTesting(repo)

        val viewModel = AppViewModel(dataMode)
        advanceUntilIdle()
        assertTrue(viewModel.legacySessionRequiresReauth.value)

        // When triggerCloudSync completes with successful upload and download
        UserCloudSyncManager.uploadUserDataHandler = { Result.success(Unit) }
        UserCloudSyncManager.downloadUserDataHandler = { Result.success(Unit) }
        viewModel.triggerCloudSync { result ->
            assertTrue(result.isSuccess)
        }
        advanceUntilIdle()

        // Then legacySessionRequiresReauth resets to false
        assertFalse("legacySessionRequiresReauth must reset to false upon successful sync", viewModel.legacySessionRequiresReauth.value)
    }

    @Test
    fun legacySessionRequiresReauth_resetsToFalse_onDismiss() = runTest {
        val legacyPrefs = FakeTrackingSharedPreferences(
            mapOf(
                "savedUserEmail" to "athlete@example.com",
                "savedUserUid" to "athlete@example.com",
                "rememberMe" to true
            )
        )
        val dataMode = DataModeManager(context = null, sharedPreferences = legacyPrefs)
        dataMode.setRepositoryForTesting(repo)

        val viewModel = AppViewModel(dataMode)
        advanceUntilIdle()
        assertTrue(viewModel.legacySessionRequiresReauth.value)

        viewModel.dismissLegacyReauthPrompt()

        assertFalse("legacySessionRequiresReauth must reset to false upon dismiss", viewModel.legacySessionRequiresReauth.value)
    }

    // =========================================================================
    // Scenario: Handling of Fabricated UIDs
    // =========================================================================

    @Test
    fun uploadUserData_failsClosed_whenUserHasFabricatedUidMismatchedWithToken() = runTest {
        // Given a user whose UID is a fabricated string derived from email (e.g. jangelpv_crosstraining_app)
        val fabricatedUser = AuthUser(
            uid = "jangelpv_crosstraining_app",
            email = "jangelpv@crosstraining.app",
            isAnonymous = false
        )
        UserCloudSyncManager.setAuthenticatedUser(fabricatedUser)

        // But Firebase Auth token has a real distinct UID (or null)
        UserCloudSyncManager.authUidProviderForTesting = { "actual_firebase_generated_uid_123" }

        // When upload is executed
        val result = UserCloudSyncManager.uploadUserData(repo)

        // Then verifyTokenBinding fails closed safely prompting re-auth rather than syncing mismatched state
        assertTrue("Upload must fail closed when token is not bound to fabricated UID", result.isFailure)
        assertEquals(SyncStatus.ERROR, UserCloudSyncManager.syncState.value)
        val ex = result.exceptionOrNull()
        assertTrue(ex is IllegalStateException)
        assertEquals(CloudSyncErrorMapper.GUEST_AUTH_PROMPT, ex?.message)
    }

    @Test
    fun uploadUserData_failsClosed_whenUserHasFabricatedUidAndTokenIsNull() = runTest {
        val fabricatedUser = AuthUser(
            uid = "athlete_crosstraining_app",
            email = "athlete@crosstraining.app",
            isAnonymous = false
        )
        UserCloudSyncManager.setAuthenticatedUser(fabricatedUser)
        UserCloudSyncManager.authUidProviderForTesting = { null }

        val result = UserCloudSyncManager.uploadUserData(repo)

        assertTrue(result.isFailure)
        assertEquals(SyncStatus.ERROR, UserCloudSyncManager.syncState.value)
        assertEquals(CloudSyncErrorMapper.GUEST_AUTH_PROMPT, result.exceptionOrNull()?.message)
    }

    // =========================================================================
    // Issue #494 / Subtask #492.2: Cold-Start Auth State Await
    // =========================================================================

    @Test
    fun awaitAuthState_resolvesAuthenticatedUser_andEmitsUserState() = runTest {
        // Given an authenticated user whose Firebase Auth token restoration is actively pending on cold start
        UserCloudSyncManager.setAuthenticatedUser(null)
        UserCloudSyncManager.asyncAuthUidProviderForTesting = {
            delay(50)
            "cold_start_resolved_uid_789"
        }

        // When awaitAuthState is called
        val user = UserCloudSyncManager.awaitAuthState(1000L)

        // Then it observes until a valid user resolves and userState emits the authenticated user
        assertEquals("cold_start_resolved_uid_789", UserCloudSyncManager.userState.value?.uid)
        assertFalse(UserCloudSyncManager.userState.value?.isAnonymous ?: true)
    }

    @Test
    fun awaitAuthState_timesOut_whenResolutionExceedsBudget() = runTest {
        UserCloudSyncManager.setAuthenticatedUser(null)
        UserCloudSyncManager.asyncAuthUidProviderForTesting = {
            delay(1000)
            "late_uid_never_made_it"
        }

        val user = UserCloudSyncManager.awaitAuthState(100L)

        assertNull("awaitAuthState must return null on timeout", user)
        assertNull("userState must remain null when awaitAuthState times out", UserCloudSyncManager.userState.value)
    }

    @Test
    fun coldStart_triggerCloudSync_awaitsPendingAuthState_andTransitionsToSuccess() = runTest {
        // Given an authenticated user whose Firebase Auth token restoration is actively pending on cold start
        UserCloudSyncManager.setAuthenticatedUser(null)
        UserCloudSyncManager.asyncAuthUidProviderForTesting = {
            delay(50)
            "async_cold_start_uid"
        }

        val dataMode = DataModeManager(context = null, sharedPreferences = FakeTrackingSharedPreferences())
        dataMode.setRepositoryForTesting(repo)
        val viewModel = AppViewModel(dataMode)

        // When the user triggers "Sync Now" immediately upon Profile screen entry
        var syncResult: CloudSyncResult? = null
        viewModel.triggerCloudSync { result ->
            syncResult = result
        }
        advanceUntilIdle()

        // Then awaitAuthState observes until non-anonymous user resolves and emits authUser
        assertEquals("async_cold_start_uid", viewModel.authUser.value?.uid)
        // And syncState transitions to SUCCESS
        assertEquals(SyncStatus.SUCCESS, UserCloudSyncManager.syncState.value)
        assertTrue(syncResult?.isSuccess == true)
    }

    @Test
    fun uploadUserData_failsClosed_whenAsyncTokenBindingFailsOrTimesOut() = runTest {
        val user = AuthUser(uid = "expected_uid_123", email = "athlete@example.com", isAnonymous = false)
        UserCloudSyncManager.setAuthenticatedUser(user)

        // async provider returns mismatched UID
        UserCloudSyncManager.asyncAuthUidProviderForTesting = {
            delay(20)
            "different_mismatched_uid"
        }

        val result = UserCloudSyncManager.uploadUserData(repo)

        assertTrue(result.isFailure)
        assertEquals(SyncStatus.ERROR, UserCloudSyncManager.syncState.value)
        assertEquals(CloudSyncErrorMapper.GUEST_AUTH_PROMPT, result.exceptionOrNull()?.message)
    }

    // =========================================================================
    // Issue #494 / Subtask #492.2: Dual-Read Legacy Cloud Data Migration
    // =========================================================================

    @Test
    fun downloadUserData_dualRead_queriesLegacyEmailPath_importsRoutinesAndSessions_andSyncsToNewUid() = runTest {
        // Given an existing user who previously backed up workouts under legacy path users/{email}
        val user = AuthUser(uid = "new_firebase_uid_555", email = "veteran@example.com", isAnonymous = false)
        UserCloudSyncManager.setAuthenticatedUser(user)
        UserCloudSyncManager.authUidProviderForTesting = { "new_firebase_uid_555" }

        val legacyRoutines = listOf(
            mapOf(
                "routine" to mapOf(
                    "id" to 101,
                    "name" to "Legacy Cindy",
                    "description" to "Historical 20-min AMRAP",
                    "defaultFormat" to "AMRAP"
                ),
                "blocks" to listOf(
                    mapOf(
                        "name" to "Pull-ups, Push-ups, Squats",
                        "kind" to "WEIGHTLIFTING",
                        "format" to "AMRAP",
                        "setsCount" to 1,
                        "targetRepsScheme" to "5-10-15"
                    )
                )
            )
        )

        val legacySessions = listOf(
            mapOf(
                "session" to mapOf(
                    "date" to "2026-08-15",
                    "title" to "Historical Cindy PR Session",
                    "notes" to "Migrated from legacy email doc",
                    "cycleId" to 0
                ),
                "blocks" to listOf(
                    mapOf(
                        "block" to mapOf(
                            "name" to "Cindy AMRAP",
                            "kind" to "WEIGHTLIFTING",
                            "format" to "AMRAP",
                            "resultText" to "22 rounds"
                        ),
                        "sets" to listOf(
                            mapOf(
                                "reps" to 22,
                                "weight" to 0.0,
                                "notes" to "Clean reps"
                            )
                        )
                    )
                )
            )
        )

        // When they sign in with a new Firebase UID and trigger cloud sync:
        // users/{new_firebase_uid_555} is empty, but users/{veteran@example.com} has data
        UserCloudSyncManager.documentReaderForTesting = { targetUid, collectionName ->
            when (targetUid) {
                "new_firebase_uid_555" -> emptyList() // newUid is empty
                "veteran@example.com" -> when (collectionName) {
                    "routines" -> legacyRoutines
                    "sessions" -> legacySessions
                    else -> emptyList()
                }
                else -> emptyList()
            }
        }

        val result = UserCloudSyncManager.downloadUserData(repo)

        // Then downloadUserData queries users/{newUid}, finds it empty, queries users/{email}
        assertTrue("downloadUserData must succeed via dual-read legacy fallback", result.isSuccess)
        assertEquals(SyncStatus.SUCCESS, UserCloudSyncManager.syncState.value)

        // And detects legacy routines/sessions, imports them into Room
        val savedRoutines = repo.getAllRoutinesWithBlocksOnce()
        assertTrue("Legacy routine must be imported into Room", savedRoutines.any { it.routine.name == "Legacy Cindy" })

        val savedSessions = repo.getAllSessionsWithBlocksOnce()
        assertTrue("Legacy session must be imported into Room", savedSessions.any { it.session.title == "Historical Cindy PR Session" })

        // And syncs them to users/{newUid} with zero data loss
        assertTrue("Imported routines must be re-uploaded to newUid", writtenDocuments.containsKey("routines"))
        assertTrue("Imported sessions must be re-uploaded to newUid", writtenDocuments.containsKey("sessions"))
    }

    @Test
    fun downloadUserData_doesNotQueryLegacy_whenNewUidAlreadyHasData() = runTest {
        val user = AuthUser(uid = "existing_uid_777", email = "athlete@example.com", isAnonymous = false)
        UserCloudSyncManager.setAuthenticatedUser(user)
        UserCloudSyncManager.authUidProviderForTesting = { "existing_uid_777" }

        var legacyQueried = false

        UserCloudSyncManager.documentReaderForTesting = { targetUid, collectionName ->
            when (targetUid) {
                "existing_uid_777" -> when (collectionName) {
                    "routines" -> listOf(
                        mapOf(
                            "routine" to mapOf("name" to "Modern Murph", "description" to "New UID routine"),
                            "blocks" to emptyList<Map<String, Any>>()
                        )
                    )
                    else -> emptyList()
                }
                "athlete@example.com" -> {
                    legacyQueried = true
                    emptyList()
                }
                else -> emptyList()
            }
        }

        val result = UserCloudSyncManager.downloadUserData(repo)

        assertTrue(result.isSuccess)
        assertFalse("Must NOT query legacy path when newUid is already populated", legacyQueried)
        val routines = repo.getAllRoutinesWithBlocksOnce()
        assertTrue(routines.any { it.routine.name == "Modern Murph" })
    }

    @Test
    fun recoverAllCloudRoutines_dualRead_queriesLegacyEmailPath_andSyncsToNewUid() = runTest {
        val user = AuthUser(uid = "fresh_firebase_uid_888", email = "legacy_coach@example.com", isAnonymous = false)
        UserCloudSyncManager.setAuthenticatedUser(user)
        UserCloudSyncManager.authUidProviderForTesting = { "fresh_firebase_uid_888" }

        UserCloudSyncManager.recoverRoutinesReaderForTesting = { targetUid ->
            when (targetUid) {
                "fresh_firebase_uid_888" -> emptyList()
                "legacy_coach@example.com" -> listOf(
                    mapOf(
                        "routine" to mapOf(
                            "name" to "Recovered Legacy Fran",
                            "description" to "Fran from legacy email doc",
                            "defaultFormat" to "21-15-9"
                        ),
                        "blocks" to listOf(
                            mapOf(
                                "name" to "Thrusters and Pull-ups",
                                "kind" to "WEIGHTLIFTING",
                                "setsCount" to 3
                            )
                        )
                    )
                )
                else -> emptyList()
            }
        }

        val result = UserCloudSyncManager.recoverAllCloudRoutines(repo)

        assertTrue(result.isSuccess)
        assertEquals(1, result.getOrNull())

        val routines = repo.getAllRoutinesWithBlocksOnce()
        assertTrue(routines.any { it.routine.name == "Recovered Legacy Fran" })

        // And re-uploaded to users/{newUid}
        assertTrue("Recovered routines must be re-uploaded to newUid", writtenDocuments.containsKey("routines"))
    }

    @Test
    fun recoverAllCloudRoutines_readsFromNewUidDirectly_whenPopulated() = runTest {
        val user = AuthUser(uid = "populated_uid_999", email = "coach@example.com", isAnonymous = false)
        UserCloudSyncManager.setAuthenticatedUser(user)
        UserCloudSyncManager.authUidProviderForTesting = { "populated_uid_999" }

        var legacyQueried = false

        UserCloudSyncManager.recoverRoutinesReaderForTesting = { targetUid ->
            when (targetUid) {
                "populated_uid_999" -> listOf(
                    mapOf(
                        "routine" to mapOf("name" to "Direct UID Routine"),
                        "blocks" to emptyList<Map<String, Any>>()
                    )
                )
                "coach@example.com" -> {
                    legacyQueried = true
                    emptyList()
                }
                else -> emptyList()
            }
        }

        val result = UserCloudSyncManager.recoverAllCloudRoutines(repo)

        assertTrue(result.isSuccess)
        assertEquals(1, result.getOrNull())
        assertFalse("Legacy path must not be queried when newUid is populated", legacyQueried)
        assertTrue(repo.getAllRoutinesWithBlocksOnce().any { it.routine.name == "Direct UID Routine" })
    }

    /**
     * In-memory SharedPreferences fake for isolated unit test verification.
     */
    class FakeTrackingSharedPreferences(initialValues: Map<String, Any> = emptyMap()) : SharedPreferences {
        val store = HashMap<String, Any?>(initialValues)
        val readKeys = mutableSetOf<String>()
        val writtenKeys = mutableSetOf<String>()

        override fun getAll(): MutableMap<String, *> = HashMap(store)

        override fun getString(key: String?, defValue: String?): String? {
            if (key != null) readKeys.add(key)
            return (store[key] as? String) ?: defValue
        }

        override fun getStringSet(key: String?, defValues: MutableSet<String>?): MutableSet<String>? {
            if (key != null) readKeys.add(key)
            @Suppress("UNCHECKED_CAST")
            return (store[key] as? MutableSet<String>) ?: defValues
        }

        override fun getInt(key: String?, defValue: Int): Int {
            if (key != null) readKeys.add(key)
            return (store[key] as? Int) ?: defValue
        }

        override fun getLong(key: String?, defValue: Long): Long {
            if (key != null) readKeys.add(key)
            return (store[key] as? Long) ?: defValue
        }

        override fun getFloat(key: String?, defValue: Float): Float {
            if (key != null) readKeys.add(key)
            return (store[key] as? Float) ?: defValue
        }

        override fun getBoolean(key: String?, defValue: Boolean): Boolean {
            if (key != null) readKeys.add(key)
            return (store[key] as? Boolean) ?: defValue
        }

        override fun contains(key: String?): Boolean = store.containsKey(key)

        override fun edit(): SharedPreferences.Editor = FakeEditor()

        override fun registerOnSharedPreferenceChangeListener(listener: SharedPreferences.OnSharedPreferenceChangeListener?) {}
        override fun unregisterOnSharedPreferenceChangeListener(listener: SharedPreferences.OnSharedPreferenceChangeListener?) {}

        inner class FakeEditor : SharedPreferences.Editor {
            override fun putString(key: String?, value: String?): SharedPreferences.Editor {
                if (key != null) {
                    writtenKeys.add(key)
                    store[key] = value
                }
                return this
            }

            override fun putStringSet(key: String?, values: MutableSet<String>?): SharedPreferences.Editor {
                if (key != null) {
                    writtenKeys.add(key)
                    store[key] = values
                }
                return this
            }

            override fun putInt(key: String?, value: Int): SharedPreferences.Editor {
                if (key != null) {
                    writtenKeys.add(key)
                    store[key] = value
                }
                return this
            }

            override fun putLong(key: String?, value: Long): SharedPreferences.Editor {
                if (key != null) {
                    writtenKeys.add(key)
                    store[key] = value
                }
                return this
            }

            override fun putFloat(key: String?, value: Float): SharedPreferences.Editor {
                if (key != null) {
                    writtenKeys.add(key)
                    store[key] = value
                }
                return this
            }

            override fun putBoolean(key: String?, value: Boolean): SharedPreferences.Editor {
                if (key != null) {
                    writtenKeys.add(key)
                    store[key] = value
                }
                return this
            }

            override fun remove(key: String?): SharedPreferences.Editor {
                if (key != null) {
                    store.remove(key)
                }
                return this
            }

            override fun clear(): SharedPreferences.Editor {
                store.clear()
                return this
            }

            override fun commit(): Boolean = true
            override fun apply() {}
        }
    }
}

