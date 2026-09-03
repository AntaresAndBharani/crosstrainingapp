package com.fractanomics.crosstraining.ui

import com.fractanomics.crosstraining.data.DataModeManager
import com.fractanomics.crosstraining.data.FakeSampleAppDatabase
import com.fractanomics.crosstraining.data.FakeTransactionRunner
import com.fractanomics.crosstraining.data.Repository
import com.fractanomics.crosstraining.data.firebase.UserCloudSyncManager
import com.fractanomics.crosstraining.data.model.Cycle
import com.fractanomics.crosstraining.data.model.CycleGoal
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.time.LocalDate

/**
 * Unit tests verifying structural isolation of background cycle synchronization (Issue #482 / Parent #481).
 *
 * Acceptance Criteria (Scenario 2):
 * - Given the application is running in any data mode
 * - When saveCycle, saveCycleWithGoals, or deleteCycleGoal executes in AppViewModel
 * - Then the background sync task passes data.realRepository to UserCloudSyncManager.uploadUserData
 * - And demo fixtures are structurally prevented from reaching Cloud Firestore
 */
@OptIn(ExperimentalCoroutinesApi::class)
class AppViewModelCycleSyncIsolationTest {

    private val testDispatcher = StandardTestDispatcher()
    private lateinit var realDb: FakeSampleAppDatabase
    private lateinit var demoDb: FakeSampleAppDatabase
    private lateinit var realRepository: Repository
    private lateinit var demoRepository: Repository
    private lateinit var dataModeManager: DataModeManager
    private lateinit var viewModel: AppViewModel

    private val uploadedRepositories = mutableListOf<Repository>()

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
        UserCloudSyncManager.uploadUserDataHandler = { repo ->
            uploadedRepositories.add(repo)
            Result.success(Unit)
        }

        viewModel = AppViewModel(dataModeManager)
        testDispatcher.scheduler.advanceUntilIdle()
    }

    @After
    fun tearDown() {
        UserCloudSyncManager.uploadUserDataHandler = null
        Dispatchers.resetMain()
    }

    @Test
    fun saveCycle_passesRealRepository_whenInDemoMode() = runTest {
        // Given demo mode is active
        dataModeManager.setDemoMode(true)
        assertEquals("current repo must be demoRepository in demo mode", demoRepository, dataModeManager.current)
        assertEquals("realRepository must be realRepository", realRepository, dataModeManager.realRepository)

        val cycle = Cycle(name = "Demo Cycle", startDate = LocalDate.now(), endDate = LocalDate.now().plusDays(30))

        // When saveCycle executes
        viewModel.saveCycle(cycle)
        advanceUntilIdle()

        // Then uploadUserData received realRepository unconditionally
        assertTrue("uploadUserData must have been called", uploadedRepositories.isNotEmpty())
        assertEquals("uploadUserData must receive realRepository even in demo mode", realRepository, uploadedRepositories.last())
        assertNotEquals("demoRepository must never be passed to cloud sync", demoRepository, uploadedRepositories.last())
    }

    @Test
    fun saveCycleWithGoals_passesRealRepository_whenInDemoMode() = runTest {
        // Given demo mode is active
        dataModeManager.setDemoMode(true)
        val cycle = Cycle(name = "Demo Cycle With Goals", startDate = LocalDate.now(), endDate = LocalDate.now().plusDays(30))
        val goals = listOf(CycleGoal(cycleId = 0, exerciseId = 1, targetWeight = 150.0))

        // When saveCycleWithGoals executes
        viewModel.saveCycleWithGoals(cycle, goals)
        advanceUntilIdle()

        // Then uploadUserData received realRepository unconditionally
        assertTrue("uploadUserData must have been called", uploadedRepositories.isNotEmpty())
        assertEquals("uploadUserData must receive realRepository even in demo mode", realRepository, uploadedRepositories.last())
        assertNotEquals("demoRepository must never be passed to cloud sync", demoRepository, uploadedRepositories.last())
    }

    @Test
    fun deleteCycleGoal_passesRealRepository_whenInDemoMode() = runTest {
        // Given demo mode is active
        dataModeManager.setDemoMode(true)
        val goal = CycleGoal(id = 1, cycleId = 1, exerciseId = 2, targetWeight = 120.0)

        // When deleteCycleGoal executes
        viewModel.deleteCycleGoal(goal)
        advanceUntilIdle()

        // Then uploadUserData received realRepository unconditionally
        assertTrue("uploadUserData must have been called", uploadedRepositories.isNotEmpty())
        assertEquals("uploadUserData must receive realRepository even in demo mode", realRepository, uploadedRepositories.last())
        assertNotEquals("demoRepository must never be passed to cloud sync", demoRepository, uploadedRepositories.last())
    }

    @Test
    fun saveCycle_passesRealRepository_whenInRealMode() = runTest {
        // Given real mode is active (default)
        assertEquals(false, dataModeManager.demoMode.value)
        val cycle = Cycle(name = "Real Cycle", startDate = LocalDate.now(), endDate = LocalDate.now().plusDays(30))

        // When saveCycle executes
        viewModel.saveCycle(cycle)
        advanceUntilIdle()

        // Then uploadUserData received realRepository
        assertTrue("uploadUserData must have been called", uploadedRepositories.isNotEmpty())
        assertEquals("uploadUserData must receive realRepository in real mode", realRepository, uploadedRepositories.last())
    }
}
