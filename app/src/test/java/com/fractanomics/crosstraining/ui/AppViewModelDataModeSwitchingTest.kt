package com.fractanomics.crosstraining.ui

import com.fractanomics.crosstraining.data.DataModeManager
import com.fractanomics.crosstraining.data.FakeSampleAppDatabase
import com.fractanomics.crosstraining.data.FakeTransactionRunner
import com.fractanomics.crosstraining.data.Repository
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
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/**
 * Unit tests verifying presentation-layer data mode switching via AppViewModel (Issue #484 / Subtask #481.3).
 *
 * Acceptance Criteria (Scenario 1):
 * - Given any user on the Profile Screen or in the Navigation Drawer
 * - When they locate the Data Mode section
 * - Then they see a switch clearly indicating "Real Data (Default)" is active
 * - When the user flips the switch to "Demo Data"
 * - Then the UI instantly re-binds to crosstraining-demo.db and demoMode becomes true
 * - When the user flips the switch back to "Real Data"
 * - Then demoMode becomes false and their personal real database is restored
 */
@OptIn(ExperimentalCoroutinesApi::class)
class AppViewModelDataModeSwitchingTest {

    private val testDispatcher = StandardTestDispatcher()
    private lateinit var realDb: FakeSampleAppDatabase
    private lateinit var demoDb: FakeSampleAppDatabase
    private lateinit var realRepository: Repository
    private lateinit var demoRepository: Repository
    private lateinit var dataModeManager: DataModeManager
    private lateinit var viewModel: AppViewModel

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

        viewModel = AppViewModel(dataModeManager)
        testDispatcher.scheduler.advanceUntilIdle()
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun initialState_defaultsToRealData() {
        // Given fresh launch
        // Then demoMode is false and repository points to real database
        assertFalse("demoMode must default to false", viewModel.demoMode.value)
        assertEquals("active repository must be realRepository", realRepository, dataModeManager.current)
    }

    @Test
    fun setDemoMode_true_switchesToDemoData() = runTest {
        // When user toggles switch to Demo Data
        viewModel.setDemoMode(true)
        advanceUntilIdle()

        // Then demoMode is true and active repository re-binds to demoRepository
        assertTrue("demoMode must be true", viewModel.demoMode.value)
        assertEquals("active repository must be demoRepository", demoRepository, dataModeManager.current)
    }

    @Test
    fun setDemoMode_false_restoresRealData() = runTest {
        // Given demo mode was active
        viewModel.setDemoMode(true)
        advanceUntilIdle()
        assertTrue(viewModel.demoMode.value)

        // When user toggles switch back to Real Data
        viewModel.setDemoMode(false)
        advanceUntilIdle()

        // Then demoMode is false and personal real repository is restored
        assertFalse("demoMode must be false", viewModel.demoMode.value)
        assertEquals("active repository must be realRepository", realRepository, dataModeManager.current)
    }

    @Test
    fun resetDemoData_executesWithoutError() = runTest {
        // When resetDemoData is called
        viewModel.resetDemoData()
        advanceUntilIdle()

        // Then execution completes cleanly
        assertEquals("realRepository remains untouched", realRepository, dataModeManager.realRepository)
    }
}
