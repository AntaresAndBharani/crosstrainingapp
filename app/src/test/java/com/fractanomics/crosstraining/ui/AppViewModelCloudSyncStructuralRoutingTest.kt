package com.fractanomics.crosstraining.ui

import com.fractanomics.crosstraining.data.DataModeManager
import com.fractanomics.crosstraining.data.FakeSampleAppDatabase
import com.fractanomics.crosstraining.data.FakeTransactionRunner
import com.fractanomics.crosstraining.data.Repository
import com.fractanomics.crosstraining.data.firebase.SyncStatus
import com.fractanomics.crosstraining.data.firebase.UserCloudSyncManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/**
 * Unit tests verifying Issue #485 / Subtask #481.4:
 * 1. Scenario 1 (Cloud sync structural isolation):
 *    All cloud sync invocations in AppViewModel (signUpWithEmail, logInWithEmail, logInWithGoogle,
 *    logInWithGoogleAccount, triggerCloudSync, recoverCloudRoutines) route strictly against
 *    data.realRepository regardless of active DataMode.
 * 2. Scenario 3 (Disaggregated sync result reporting):
 *    triggerCloudSync yields CloudSyncResult with granular uploadSuccess and downloadSuccess,
 *    and failed uploads are not masked as SUCCESS.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class AppViewModelCloudSyncStructuralRoutingTest {

    private val testDispatcher = StandardTestDispatcher()
    private lateinit var realDb: FakeSampleAppDatabase
    private lateinit var demoDb: FakeSampleAppDatabase
    private lateinit var realRepository: Repository
    private lateinit var demoRepository: Repository
    private lateinit var dataModeManager: DataModeManager
    private lateinit var viewModel: AppViewModel

    private val uploadedRepositories = mutableListOf<Repository>()
    private val downloadedRepositories = mutableListOf<Repository>()
    private val recoveredRepositories = mutableListOf<Repository>()

    @Before
    fun setUp() {
        Dispatchers.setMain(testDispatcher)

        realDb = FakeSampleAppDatabase()
        demoDb = FakeSampleAppDatabase()
        realDb.populateSampleData()
        demoDb.populateSampleData()

        realRepository = Repository(realDb, FakeTransactionRunner(realDb))
        demoRepository = Repository(demoDb, FakeTransactionRunner(demoDb))

        dataModeManager = DataModeManager(context = null)
        dataModeManager.setRepositoryForTesting(repo = realRepository, demoRepo = demoRepository)

        uploadedRepositories.clear()
        downloadedRepositories.clear()
        recoveredRepositories.clear()

        UserCloudSyncManager.resetTestHandlers()

        UserCloudSyncManager.uploadUserDataHandler = { repo ->
            uploadedRepositories.add(repo)
            Result.success(Unit)
        }

        UserCloudSyncManager.downloadUserDataHandler = { repo ->
            downloadedRepositories.add(repo)
            Result.success(Unit)
        }

        UserCloudSyncManager.recoverAllCloudRoutinesHandler = { repo ->
            recoveredRepositories.add(repo)
            Result.success(3)
        }

        UserCloudSyncManager.signUpWithEmailHandler = { _, _ -> Result.success(Unit) }
        UserCloudSyncManager.logInWithEmailHandler = { _, _ -> Result.success(Unit) }
        UserCloudSyncManager.signInWithGoogleCredentialHandler = { _ -> Result.success(Unit) }
        UserCloudSyncManager.logInWithGoogleAccountHandler = { _, _ -> Result.success(Unit) }

        viewModel = AppViewModel(dataModeManager)
        testDispatcher.scheduler.advanceUntilIdle()
    }

    @After
    fun tearDown() {
        UserCloudSyncManager.resetTestHandlers()
        Dispatchers.resetMain()
    }

    @Test
    fun signUpWithEmail_passesRealRepository_whenInDemoMode() = runTest {
        dataModeManager.setDemoMode(true)

        var callbackSuccess = false
        viewModel.signUpWithEmail("test@example.com", "pass123") { ok, _ ->
            callbackSuccess = ok
        }
        advanceUntilIdle()

        assertTrue("Callback must report success", callbackSuccess)
        assertEquals("uploadUserData must be called once", 1, uploadedRepositories.size)
        assertEquals("uploadUserData must target realRepository", realRepository, uploadedRepositories.first())
        assertNotEquals("demoRepository must never be targeted for upload", demoRepository, uploadedRepositories.first())
    }

    @Test
    fun logInWithEmail_passesRealRepository_whenInDemoMode() = runTest {
        dataModeManager.setDemoMode(true)

        var callbackSuccess = false
        viewModel.logInWithEmail("test@example.com", "pass123") { ok, _ ->
            callbackSuccess = ok
        }
        advanceUntilIdle()

        assertTrue("Callback must report success", callbackSuccess)
        assertEquals("downloadUserData must be called once", 1, downloadedRepositories.size)
        assertEquals("downloadUserData must target realRepository", realRepository, downloadedRepositories.first())
        assertNotEquals("demoRepository must never be targeted for download", demoRepository, downloadedRepositories.first())
    }

    @Test
    fun logInWithGoogle_passesRealRepository_whenInDemoMode() = runTest {
        dataModeManager.setDemoMode(true)

        var callbackSuccess = false
        viewModel.logInWithGoogle("fake-id-token") { ok, _ ->
            callbackSuccess = ok
        }
        advanceUntilIdle()

        assertTrue("Callback must report success", callbackSuccess)
        assertEquals("downloadUserData must be called once", 1, downloadedRepositories.size)
        assertEquals("downloadUserData must target realRepository", realRepository, downloadedRepositories.first())
        assertNotEquals("demoRepository must never be targeted for download", demoRepository, downloadedRepositories.first())
    }

    @Test
    fun logInWithGoogleAccount_passesRealRepository_whenInDemoMode() = runTest {
        dataModeManager.setDemoMode(true)

        var callbackSuccess = false
        viewModel.logInWithGoogleAccount("user@google.com", "User Name") { ok, _ ->
            callbackSuccess = ok
        }
        advanceUntilIdle()

        assertTrue("Callback must report success", callbackSuccess)
        assertEquals("downloadUserData must be called once", 1, downloadedRepositories.size)
        assertEquals("downloadUserData must target realRepository", realRepository, downloadedRepositories.first())
        assertNotEquals("demoRepository must never be targeted for download", demoRepository, downloadedRepositories.first())
    }

    @Test
    fun triggerCloudSync_passesRealRepository_whenInDemoMode() = runTest {
        dataModeManager.setDemoMode(true)

        var syncResult: CloudSyncResult? = null
        viewModel.triggerCloudSync { result ->
            syncResult = result
        }
        advanceUntilIdle()

        assertNotNull("triggerCloudSync must produce a result", syncResult)
        assertTrue("Upload must succeed", syncResult!!.uploadSuccess)
        assertTrue("Download must succeed", syncResult!!.downloadSuccess)
        assertTrue("Overall sync isSuccess must be true", syncResult!!.isSuccess)

        assertEquals("uploadUserData must target realRepository", realRepository, uploadedRepositories.first())
        assertEquals("downloadUserData must target realRepository", realRepository, downloadedRepositories.first())
        assertNotEquals("demoRepository must not receive upload", demoRepository, uploadedRepositories.first())
        assertNotEquals("demoRepository must not receive download", demoRepository, downloadedRepositories.first())
    }

    @Test
    fun recoverCloudRoutines_passesRealRepository_whenInDemoMode() = runTest {
        dataModeManager.setDemoMode(true)

        var count = 0
        viewModel.recoverCloudRoutines { recoveredCount ->
            count = recoveredCount
        }
        advanceUntilIdle()

        assertEquals("Recovered count must be 3", 3, count)
        assertEquals("recoverAllCloudRoutines must target realRepository", realRepository, recoveredRepositories.first())
        assertNotEquals("demoRepository must not be passed to routine recovery", demoRepository, recoveredRepositories.first())
    }

    @Test
    fun triggerCloudSync_disaggregatesFailure_whenUploadFailsAndDownloadSucceeds() = runTest {
        UserCloudSyncManager.uploadUserDataHandler = { _ ->
            Result.failure(RuntimeException("Network upload timeout"))
        }
        UserCloudSyncManager.downloadUserDataHandler = { repo ->
            downloadedRepositories.add(repo)
            Result.success(Unit)
        }

        var syncResult: CloudSyncResult? = null
        viewModel.triggerCloudSync { result ->
            syncResult = result
        }
        advanceUntilIdle()

        assertNotNull(syncResult)
        assertFalse("uploadSuccess must be false", syncResult!!.uploadSuccess)
        assertTrue("downloadSuccess must be true", syncResult!!.downloadSuccess)
        assertFalse("isSuccess must be false when upload failed", syncResult!!.isSuccess)
        assertEquals("Network upload timeout", syncResult!!.uploadError)
        assertNull("downloadError must be null", syncResult!!.downloadError)
        assertEquals("Sync status must be ERROR to prevent masking failed upload", SyncStatus.ERROR, viewModel.syncState.value)
    }

    @Test
    fun triggerCloudSync_disaggregatesFailure_whenUploadSucceedsAndDownloadFails() = runTest {
        UserCloudSyncManager.uploadUserDataHandler = { repo ->
            uploadedRepositories.add(repo)
            Result.success(Unit)
        }
        UserCloudSyncManager.downloadUserDataHandler = { _ ->
            Result.failure(RuntimeException("Firestore download quota exceeded"))
        }

        var syncResult: CloudSyncResult? = null
        viewModel.triggerCloudSync { result ->
            syncResult = result
        }
        advanceUntilIdle()

        assertNotNull(syncResult)
        assertTrue("uploadSuccess must be true", syncResult!!.uploadSuccess)
        assertFalse("downloadSuccess must be false", syncResult!!.downloadSuccess)
        assertFalse("isSuccess must be false when download failed", syncResult!!.isSuccess)
        assertNull("uploadError must be null", syncResult!!.uploadError)
        assertEquals("Firestore download quota exceeded", syncResult!!.downloadError)
        assertEquals("Sync status must be ERROR", SyncStatus.ERROR, viewModel.syncState.value)
    }

    @Test
    fun triggerCloudSync_legacyCallback_reportsFailureAccurately() = runTest {
        UserCloudSyncManager.uploadUserDataHandler = { _ ->
            Result.failure(RuntimeException("Disk error"))
        }
        UserCloudSyncManager.downloadUserDataHandler = { _ ->
            Result.success(Unit)
        }

        var successReported: Boolean? = null
        var errorReported: String? = null
        viewModel.triggerCloudSync { ok, err ->
            successReported = ok
            errorReported = err
        }
        advanceUntilIdle()

        assertEquals("Legacy callback must report failure when upload fails", false, successReported)
        assertTrue("Error message must mention upload failure", errorReported?.contains("Upload failed") == true)
    }
}
