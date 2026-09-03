package com.fractanomics.crosstraining.data.firebase

import com.fractanomics.crosstraining.data.FakeSampleAppDatabase
import com.fractanomics.crosstraining.data.FakeTransactionRunner
import com.fractanomics.crosstraining.data.Repository
import com.fractanomics.crosstraining.data.model.BlockKind
import com.fractanomics.crosstraining.data.model.Routine
import com.fractanomics.crosstraining.data.model.RoutineBlock
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/**
 * Unit & integration tests for Issue #486:
 * - Scenario 1: Token-bound identity verification (fails closed when auth token is mismatched or absent).
 * - Scenario 2: Scoped cloud routine recovery (strictly scoped to userDoc(currentUserId)).
 * - Scenario 3: JVM test seam determinism without live Firebase Auth.
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
}
