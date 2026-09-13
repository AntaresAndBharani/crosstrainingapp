package com.fractanomics.crosstraining.ui

import com.fractanomics.crosstraining.data.DataModeManager
import com.fractanomics.crosstraining.data.DataModeManagerTest.FakeTrackingSharedPreferences
import com.fractanomics.crosstraining.data.FakeSampleAppDatabase
import com.fractanomics.crosstraining.data.FakeTransactionRunner
import com.fractanomics.crosstraining.data.Repository
import com.fractanomics.crosstraining.data.analytics.Timeframe
import com.fractanomics.crosstraining.data.analytics.WeightAnalytics
import com.fractanomics.crosstraining.data.model.WeightEntry
import com.fractanomics.crosstraining.ui.screens.ProgressMode
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.time.LocalDate

/**
 * Unit tests verifying presentation layer body weight actions, state flows,
 * undo restoration, and unit preference persistence (Issue #506 / Subtask #501.4).
 *
 * BDD Scenarios Covered:
 * 1. Log New Weight Entry via Bottom Sheet / ViewModel
 * 2. Unconditional Tab Availability on Fresh Installs
 * 3. Unit Toggle Metric and Imperial Persistence
 * 4. Soft Delete with Undo Affordance & Fresh Timestamp
 */
@OptIn(ExperimentalCoroutinesApi::class)
class AppViewModelWeightTest {

    private val testDispatcher = UnconfinedTestDispatcher()
    private lateinit var realDb: FakeSampleAppDatabase
    private lateinit var demoDb: FakeSampleAppDatabase
    private lateinit var realRepo: Repository
    private lateinit var demoRepo: Repository
    private lateinit var fakePrefs: FakeTrackingSharedPreferences
    private lateinit var dataModeManager: DataModeManager
    private lateinit var viewModel: AppViewModel

    @Before
    fun setUp() {
        Dispatchers.setMain(testDispatcher)

        realDb = FakeSampleAppDatabase()
        demoDb = FakeSampleAppDatabase()

        realRepo = Repository(realDb, FakeTransactionRunner(realDb))
        demoRepo = Repository(demoDb, FakeTransactionRunner(demoDb))

        fakePrefs = FakeTrackingSharedPreferences()
        dataModeManager = DataModeManager(context = null, sharedPreferences = fakePrefs)
        dataModeManager.setRepositoryForTesting(repo = realRepo, demoRepo = demoRepo)

        viewModel = AppViewModel(dataModeManager)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun scenario1_logNewWeightEntry_savesToActiveDatabaseAndEmitsInStateFlow() = runTest {
        val collectJob = launch(testDispatcher) { viewModel.weightEntries.collect {} }
        val today = LocalDate.of(2026, 9, 7)

        // Given athlete is on ProgressScreen under Body weight tab
        assertEquals(0, viewModel.weightEntries.value.size)

        // When saving weight entry 78.4 kg
        viewModel.saveWeightEntry(weightKg = 78.4, date = today, notes = "Morning fasted")

        // Then entry is persisted and emitted via weightEntries StateFlow
        val entries = viewModel.weightEntries.value
        assertEquals(1, entries.size)
        val entry = entries.first()
        assertEquals(today, entry.date)
        assertEquals(78.4, entry.weightKg, 0.001)
        assertEquals("Morning fasted", entry.notes)
        assertNull("Active entry must have null deletedAtMillis", entry.deletedAtMillis)
        assertTrue("updatedAtMillis must be set", entry.updatedAtMillis > 0)

        collectJob.cancel()
    }

    @Test
    fun scenario2_unconditionalTabAvailability_freshInstallHasZeroExercisesAndWeightFlowOperates() = runTest {
        val collectJob = launch(testDispatcher) { viewModel.weightEntries.collect {} }

        // Given fresh install with zero exercises and zero sessions
        assertTrue("Exercises must be empty on clean state", viewModel.exercises.value.isEmpty())
        assertTrue("Sessions must be empty on clean state", viewModel.sessions.value.isEmpty())

        // Then weight tracker flows operate independently
        assertNotNull(viewModel.weightEntries.value)
        assertEquals("Default weight unit must be kg", "kg", viewModel.weightUnit.value)

        // When logging weight on clean install
        viewModel.saveWeightEntry(weightKg = 75.0, date = LocalDate.of(2026, 9, 1))

        assertEquals(1, viewModel.weightEntries.value.size)
        assertEquals(75.0, viewModel.weightEntries.value.first().weightKg, 0.001)

        collectJob.cancel()
    }

    @Test
    fun scenario3_unitToggle_persistsPreferenceInSharedPreferences() = runTest {
        // Given default weight unit is kg
        assertEquals("kg", viewModel.weightUnit.value)
        assertEquals("kg", dataModeManager.weightUnit.value)

        // When toggling unit to lbs
        viewModel.setWeightUnit("lbs")

        // Then StateFlow emits "lbs" and is persisted in SharedPreferences
        assertEquals("lbs", viewModel.weightUnit.value)
        assertEquals("lbs", dataModeManager.weightUnit.value)
        assertEquals("lbs", fakePrefs.getString("weightUnit", null))

        // When toggling back to kg
        viewModel.setWeightUnit("kg")

        assertEquals("kg", viewModel.weightUnit.value)
        assertEquals("kg", fakePrefs.getString("weightUnit", null))
    }

    @Test
    fun scenario4_softDeleteAndUndo_restoresEntryWithFreshTimestamp() = runTest {
        val collectJob = launch(testDispatcher) { viewModel.weightEntries.collect {} }
        val testDate = LocalDate.of(2026, 9, 5)

        viewModel.saveWeightEntry(weightKg = 80.0, date = testDate, notes = "Initial log")

        assertEquals(1, viewModel.weightEntries.value.size)
        val initialEntry = viewModel.weightEntries.value.first()
        val initialTimestamp = initialEntry.updatedAtMillis

        // Wait brief instant to ensure time progression
        Thread.sleep(10)

        // When deleting weight entry
        viewModel.deleteWeightEntry(testDate)

        // Then entry is marked deleted and excluded from active weightEntries flow
        assertEquals(0, viewModel.weightEntries.value.size)
        val tombstone = realDb.weightDao().getEntryByDate(testDate)
        assertNotNull("Tombstone must exist in database", tombstone)
        assertNotNull("deletedAtMillis must be set", tombstone?.deletedAtMillis)

        // Wait brief instant
        Thread.sleep(10)

        // When Undo action is triggered
        viewModel.undoDeleteWeightEntry(date = testDate, weightKg = 80.0, notes = "Initial log")

        // Then entry is restored to active flow with null tombstone and fresh updatedAtMillis
        assertEquals(1, viewModel.weightEntries.value.size)
        val restored = viewModel.weightEntries.value.first()
        assertEquals(testDate, restored.date)
        assertEquals(80.0, restored.weightKg, 0.001)
        assertNull("Restored entry must clear tombstone", restored.deletedAtMillis)
        assertTrue(
            "Restored entry must have fresh updatedAtMillis superseding initial timestamp",
            restored.updatedAtMillis > initialTimestamp
        )

        collectJob.cancel()
    }

    @Test
    fun analyticsIntegration_prepareChartSeries_alignsRawAndSmaPoints() = runTest {
        val collectJob = launch(testDispatcher) { viewModel.weightEntries.collect {} }
        val baseDate = LocalDate.of(2026, 8, 1)

        // Add 10 daily entries
        for (i in 0 until 10) {
            val d = baseDate.plusDays(i.toLong())
            viewModel.saveWeightEntry(weightKg = 70.0 + (i * 0.2), date = d)
        }

        val active = viewModel.weightEntries.value
        assertEquals(10, active.size)

        val series = WeightAnalytics.prepareChartSeries(active, Timeframe.THIRTY_DAYS)
        assertEquals(10, series.size)
        // Check SMA warm-up contract: first 2 entries have N < 3 in [t-6, t], so smaValue is null
        assertNull("Index 0 has N=1 < 3", series[0].smaValue)
        assertNull("Index 1 has N=2 < 3", series[1].smaValue)
        assertNotNull("Index 2 has N=3 >= 3", series[2].smaValue)

        collectJob.cancel()
    }

    @Test
    fun progressMode_defaultsToByExercise_andUpdatesReactivelyAndIdempotently() = runTest {
        // Given initial ViewModel state
        assertEquals("Initial progressMode must default to BY_EXERCISE", ProgressMode.BY_EXERCISE, viewModel.progressMode.value)

        // When updating mode to BODY_WEIGHT (e.g. from DrawerItem.WEIGHT or profile card)
        viewModel.setProgressMode(ProgressMode.BODY_WEIGHT)

        // Then progressMode StateFlow reflects BODY_WEIGHT immediately
        assertEquals(ProgressMode.BODY_WEIGHT, viewModel.progressMode.value)

        // When updating idempotently (rapid double-tap scenario)
        viewModel.setProgressMode(ProgressMode.BODY_WEIGHT)
        assertEquals(ProgressMode.BODY_WEIGHT, viewModel.progressMode.value)

        // When switching back to BY_ROUTINE or BY_EXERCISE (e.g. from DrawerItem.PROGRESS)
        viewModel.setProgressMode(ProgressMode.BY_ROUTINE)
        assertEquals(ProgressMode.BY_ROUTINE, viewModel.progressMode.value)

        viewModel.setProgressMode(ProgressMode.CYCLE_GOALS)
        assertEquals(ProgressMode.CYCLE_GOALS, viewModel.progressMode.value)

        viewModel.setProgressMode(ProgressMode.BY_EXERCISE)
        assertEquals(ProgressMode.BY_EXERCISE, viewModel.progressMode.value)
    }
}
