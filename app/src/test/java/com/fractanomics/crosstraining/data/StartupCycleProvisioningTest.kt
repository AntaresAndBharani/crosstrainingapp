package com.fractanomics.crosstraining.data

import com.fractanomics.crosstraining.data.model.BlockKind
import com.fractanomics.crosstraining.data.model.BlockSet
import com.fractanomics.crosstraining.data.model.Cycle
import com.fractanomics.crosstraining.data.model.Exercise
import com.fractanomics.crosstraining.data.model.Session
import com.fractanomics.crosstraining.data.model.SessionBlock
import com.fractanomics.crosstraining.ui.AppViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.time.LocalDate

/**
 * Unit and integration tests for Issue #483:
 * "[Subtask #481.2] Startup Training Cycle Provisioning for Fresh & Upgraded Production Installs"
 *
 * Gherkin Acceptance Scenarios:
 * Scenario: Automatic cycle provisioning on empty database
 *   Given a fresh or upgraded production install where crosstraining.db has no training cycles
 *   When the application initializes
 *   Then a startup verification checks cycleDao.getAllOnce().isEmpty()
 *   And provisions a default active training cycle named "General Training"
 *   And the athlete can immediately navigate to Log Session and save workouts without error
 */
@OptIn(ExperimentalCoroutinesApi::class)
class StartupCycleProvisioningTest {

    private val testDispatcher = StandardTestDispatcher()
    private lateinit var fakeDb: FakeSampleAppDatabase
    private lateinit var transactionRunner: FakeTransactionRunner
    private lateinit var repository: Repository

    @Before
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
        fakeDb = FakeSampleAppDatabase()
        transactionRunner = FakeTransactionRunner(fakeDb)
        repository = Repository(
            db = fakeDb,
            transactionRunner = transactionRunner
        )
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun provisionDefaultCycleIfNeeded_emptyDatabase_provisionsGeneralTrainingCycle() = runTest(testDispatcher) {
        // Given a fresh or upgraded production install where crosstraining.db has no training cycles
        val initialCycles = fakeDb.cycleDao().getAllOnce()
        assertTrue("Precondition: database must have no training cycles", initialCycles.isEmpty())

        // When startup verification checks cycleDao.getAllOnce().isEmpty()
        val provisioned = repository.provisionDefaultCycleIfNeeded()

        // Then provisions a default active training cycle named "General Training"
        assertNotNull("Must return the newly provisioned cycle", provisioned)
        assertEquals("General Training", provisioned?.name)
        assertTrue("Provisioned cycle must be active", provisioned?.isActive == true)
        assertEquals(LocalDate.now(), provisioned?.startDate)
        assertTrue("Provisioned cycle must have a valid generated id > 0", (provisioned?.id ?: 0L) > 0L)

        // And verify persistence in cycleDao
        val allAfter = fakeDb.cycleDao().getAllOnce()
        assertEquals("Database must now contain exactly 1 cycle", 1, allAfter.size)
        assertEquals("General Training", allAfter.first().name)
        assertTrue(allAfter.first().isActive)
    }

    @Test
    fun provisionDefaultCycleIfNeeded_alreadyHasCycles_isIdempotentAndDoesNotDuplicate() = runTest(testDispatcher) {
        // Given database already provisioned or contains existing cycles
        val firstProvision = repository.provisionDefaultCycleIfNeeded()
        assertNotNull(firstProvision)

        // When called again (e.g. subsequent startup or flow observation)
        val secondProvision = repository.provisionDefaultCycleIfNeeded()

        // Then returns null and does not duplicate
        assertNull("Subsequent checks must not re-provision when cycles exist", secondProvision)
        val allCycles = fakeDb.cycleDao().getAllOnce()
        assertEquals("Must still have exactly 1 cycle", 1, allCycles.size)
    }

    @Test
    fun provisionDefaultCycleIfNeeded_preservesExistingUserCycles() = runTest(testDispatcher) {
        // Given existing user cycles (e.g. athlete previously configured their own periodization block)
        val customCycle = Cycle(
            name = "Hypertrophy Wave 1",
            startDate = LocalDate.now().minusWeeks(2),
            endDate = LocalDate.now().plusWeeks(2),
            goal = "Leg volume",
            isActive = true
        )
        fakeDb.cycleDao().insert(customCycle)
        assertEquals(1, fakeDb.cycleDao().getAllOnce().size)

        // When startup verification runs
        val outcome = repository.provisionDefaultCycleIfNeeded()

        // Then no default cycle is created and custom cycle is preserved
        assertNull("Must not provision default cycle when user cycles exist", outcome)
        val cycles = fakeDb.cycleDao().getAllOnce()
        assertEquals(1, cycles.size)
        assertEquals("Hypertrophy Wave 1", cycles.first().name)
        assertTrue(cycles.first().isActive)
    }

    @Test
    fun ensureDefaultCycleProvisioned_returnsExistingActiveCycleOrProvisionsNew() = runTest(testDispatcher) {
        // Empty DB -> provisions General Training
        val cycle = repository.ensureDefaultCycleProvisioned()
        assertEquals("General Training", cycle.name)
        assertTrue(cycle.isActive)

        // Non-empty DB -> returns existing active cycle
        val existing = repository.ensureDefaultCycleProvisioned()
        assertEquals(cycle.id, existing.id)
        assertEquals("General Training", existing.name)
    }

    @Test
    fun activeCycleFlow_automaticallyProvisionsGeneralTrainingOnFirstCollection() = runTest(testDispatcher) {
        // Given an empty cycle database
        assertTrue(fakeDb.cycleDao().getAllOnce().isEmpty())

        // When collecting activeCycle Flow
        val active = repository.activeCycle.first()

        // Then it emits a default active training cycle named "General Training"
        assertNotNull("activeCycle must not be null for athlete", active)
        assertEquals("General Training", active?.name)
        assertTrue(active?.isActive == true)
    }

    @Test
    fun cyclesFlow_automaticallyProvisionsGeneralTrainingOnFirstCollection() = runTest(testDispatcher) {
        // Given an empty cycle database
        assertTrue(fakeDb.cycleDao().getAllOnce().isEmpty())

        // When collecting cycles Flow
        val cycles = repository.cycles.first()

        // Then it emits a list containing "General Training"
        assertEquals(1, cycles.size)
        assertEquals("General Training", cycles.first().name)
        assertTrue(cycles.first().isActive)
    }

    @Test
    fun appDatabaseCompanion_provisionDefaultCycleIfNeeded_provisionsCorrectly() = runTest(testDispatcher) {
        // Given empty DB
        assertTrue(fakeDb.cycleDao().getAllOnce().isEmpty())

        // When AppDatabase.provisionDefaultCycleIfNeeded is called
        val provisioned = AppDatabase.provisionDefaultCycleIfNeeded(fakeDb)

        assertNotNull(provisioned)
        assertEquals("General Training", provisioned?.name)
        assertTrue(provisioned?.isActive == true)
        assertEquals(1, fakeDb.cycleDao().getAllOnce().size)
    }

    @Test
    fun athleteCanImmediatelyLogAndSaveWorkoutWithoutError() = runTest(testDispatcher) {
        // Given fresh or upgraded install automatically provisioned with General Training
        val defaultCycle = repository.ensureDefaultCycleProvisioned()
        val backSquat = fakeDb.exerciseDao().byName("Back Squat")
            ?: Exercise(id = 1L, name = "Back Squat")

        // When athlete navigates to Log Session and saves workout using effective active cycle
        val session = Session(
            id = 0,
            cycleId = defaultCycle.id,
            date = LocalDate.now(),
            title = "Morning Strength",
            notes = "Clean form"
        )
        val block = SessionBlock(
            id = 0,
            sessionId = 0,
            position = 0,
            name = "Back Squat",
            kind = BlockKind.STRENGTH,
            mainExerciseId = backSquat.id
        )
        val sets = listOf(
            BlockSet(id = 0, blockId = 0, position = 1, reps = 5, weight = 100.0)
        )
        val sessionId = repository.saveSession(
            session = session,
            blocks = listOf(BlockInsert(block = block, sets = sets))
        )

        // Then session is persisted successfully without error
        assertTrue("Session id must be generated > 0", sessionId > 0L)
        val savedSession = fakeDb.sessionDao().getByIdOnce(sessionId)
        assertNotNull(savedSession)
        assertEquals(defaultCycle.id, savedSession?.session?.cycleId)
        assertEquals("Morning Strength", savedSession?.session?.title)
        assertEquals(1, savedSession?.blocks?.size)
        assertEquals(1, savedSession?.blocks?.first()?.sets?.size)
        assertEquals(100.0, savedSession?.blocks?.first()?.sets?.first()?.weight ?: 0.0, 0.01)
    }

    @Test
    fun appViewModel_initialization_provisionsDefaultCycleForRealData() = runTest(testDispatcher) {
        // Given an empty real database
        val dataModeManager = DataModeManager(context = null)
        dataModeManager.setRepositoryForTesting(repo = repository)

        // When AppViewModel initializes and UI collects activeCycle / cycles
        val viewModel = AppViewModel(dataModeManager)
        backgroundScope.launch { viewModel.activeCycle.collect {} }
        backgroundScope.launch { viewModel.cycles.collect {} }
        testDispatcher.scheduler.advanceUntilIdle()

        // Then activeCycle StateFlow reflects "General Training"
        val active = viewModel.activeCycle.value
        assertNotNull("AppViewModel.activeCycle must be provisioned on launch", active)
        assertEquals("General Training", active?.name)
        assertTrue(active?.isActive == true)

        val cycles = viewModel.cycles.value
        assertEquals("AppViewModel.cycles must contain provisioned cycle", 1, cycles.size)
        assertEquals("General Training", cycles.first().name)
    }
}