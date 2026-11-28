package com.fractanomics.crosstraining.ui.draft

import android.content.SharedPreferences
import com.fractanomics.crosstraining.data.DataModeManager
import com.fractanomics.crosstraining.data.FakeSampleAppDatabase
import com.fractanomics.crosstraining.data.FakeTransactionRunner
import com.fractanomics.crosstraining.data.Repository
import com.fractanomics.crosstraining.ui.AppViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.advanceUntilIdle
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

@OptIn(ExperimentalCoroutinesApi::class)
class ActiveWorkoutDraftPersistenceTest {

    private val testDispatcher = StandardTestDispatcher()
    private lateinit var sharedPrefs: InMemorySharedPreferences
    private lateinit var realRepository: Repository
    private lateinit var demoRepository: Repository
    private lateinit var dataModeManager: DataModeManager
    private lateinit var viewModel: AppViewModel

    @Before
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
        sharedPrefs = InMemorySharedPreferences()
        val realDb = FakeSampleAppDatabase().apply { populateSampleData() }
        val demoDb = FakeSampleAppDatabase().apply { populateSampleData() }
        realRepository = Repository(realDb, FakeTransactionRunner(realDb))
        demoRepository = Repository(demoDb, FakeTransactionRunner(demoDb))
        dataModeManager = DataModeManager(context = null, sharedPreferences = sharedPrefs)
        dataModeManager.setRepositoryForTesting(repo = realRepository, demoRepo = demoRepository)
        viewModel = AppViewModel(dataModeManager)
        testDispatcher.scheduler.advanceUntilIdle()
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    private fun sampleDraft(
        title: String,
        date: LocalDate = LocalDate.now(),
        uiKey: String = "ui-key-1"
    ): ActiveWorkoutDraftSnapshot = ActiveWorkoutDraftSnapshot(
        dateEpochDay = date.toEpochDay(),
        title = title,
        loadedBlocks = listOf(
            ActiveLoadedBlockSnapshot(
                uiKey = uiKey,
                itemKind = "BLOCK",
                blockId = 1L,
                blockTitle = title,
                exerciseSections = listOf(
                    ActiveExerciseSectionSnapshot(
                        exerciseId = 10L,
                        exerciseName = "Front Squat",
                        weights = listOf("90", "95"),
                        repsInputs = listOf("3", "3"),
                        metricInputs = listOf("", ""),
                        skippedFlags = listOf(false, false)
                    )
                )
            )
        )
    )

    @Test
    fun `debounced update and synchronous flush survive simulated cold app restart`() = runTest(testDispatcher) {
        val draft = sampleDraft("Monday Squat")
        viewModel.updateActiveWorkoutDraft(draft)

        // Immediately visible in memory for tab switches
        assertEquals(draft, viewModel.activeWorkoutDraft.value?.copy(updatedAtEpochMillis = 0L))
        assertTrue((viewModel.activeWorkoutDraft.value?.updatedAtEpochMillis ?: 0L) > 0L)
        assertTrue(viewModel.activeWorkoutSummary.value.isActive)
        // Not written to disk before 500ms debounce expires
        assertNull(dataModeManager.readActiveWorkoutDraftJson(demoMode = false))

        advanceTimeBy(550L)
        assertNotNull(dataModeManager.readActiveWorkoutDraftJson(demoMode = false))

        // Immediate flush writes immediately even without waiting for debounce
        val updated = draft.copy(scrollOffsetPx = 320, notes = "Beltless")
        viewModel.flushActiveWorkoutDraftNow(updated)

        // Simulate process death & cold relaunch against the same SharedPreferences
        val restartedManager = DataModeManager(context = null, sharedPreferences = sharedPrefs)
        restartedManager.setRepositoryForTesting(repo = realRepository, demoRepo = demoRepository)
        val restartedVm = AppViewModel(restartedManager)
        advanceUntilIdle()

        assertEquals(updated, restartedVm.activeWorkoutDraft.value?.copy(updatedAtEpochMillis = 0L))
        assertEquals(320, restartedVm.activeWorkoutDraft.value?.scrollOffsetPx)
        assertTrue(restartedVm.activeWorkoutSummary.value.isActive)
    }

    @Test
    fun `idle blank draft does not persist and clearActiveWorkoutDraft wipes memory and disk`() = runTest(testDispatcher) {
        viewModel.updateActiveWorkoutDraft(ActiveWorkoutDraftSnapshot())
        advanceUntilIdle()
        assertNull(viewModel.activeWorkoutDraft.value)
        assertFalse(viewModel.activeWorkoutSummary.value.isActive)
        assertNull(dataModeManager.readActiveWorkoutDraftJson(demoMode = false))

        val initialRev = viewModel.activeWorkoutDraftRevision.value
        viewModel.flushActiveWorkoutDraftNow(sampleDraft("Temp Session"))
        assertTrue(viewModel.activeWorkoutSummary.value.isActive)

        viewModel.clearActiveWorkoutDraft()
        assertNull(viewModel.activeWorkoutDraft.value)
        assertFalse(viewModel.activeWorkoutSummary.value.isActive)
        assertNull(dataModeManager.readActiveWorkoutDraftJson(demoMode = false))
        assertEquals(initialRev + 1, viewModel.activeWorkoutDraftRevision.value)
    }

    @Test
    fun `updateActiveDraftDateToToday clears stale flag and persists`() = runTest(testDispatcher) {
        val yesterday = LocalDate.now().minusDays(1)
        viewModel.flushActiveWorkoutDraftNow(sampleDraft("Yesterday Session", date = yesterday))
        assertTrue(viewModel.activeWorkoutSummary.value.isStale)

        val today = LocalDate.now()
        viewModel.updateActiveDraftDateToToday(today)
        assertFalse(viewModel.activeWorkoutSummary.value.isStale)
        assertEquals(today, viewModel.activeWorkoutDraft.value?.sessionDate())
    }

    @Test
    fun `demo mode isolates real workout draft and restores it untouched on return`() = runTest(testDispatcher) {
        val realDraft = sampleDraft("Real Athlete Workout", uiKey = "real-1")
        viewModel.flushActiveWorkoutDraftNow(realDraft)

        // Switch to Demo Data
        viewModel.setDemoMode(true)
        advanceUntilIdle()
        assertTrue(viewModel.demoMode.value)
        assertNull(viewModel.activeWorkoutDraft.value)
        assertFalse(viewModel.activeWorkoutSummary.value.isActive)

        // Log inside Demo Data
        val demoDraft = sampleDraft("Demo Exploration", uiKey = "demo-1")
        viewModel.flushActiveWorkoutDraftNow(demoDraft)
        assertEquals("Demo Exploration", viewModel.activeWorkoutSummary.value.displayTitle)

        // Switch back to Real Data -> Real draft is intact
        viewModel.setDemoMode(false)
        advanceUntilIdle()
        assertFalse(viewModel.demoMode.value)
        assertEquals(realDraft, viewModel.activeWorkoutDraft.value?.copy(updatedAtEpochMillis = 0L))
        assertEquals("Real Athlete Workout", viewModel.activeWorkoutSummary.value.displayTitle)
    }
}

private class InMemorySharedPreferences : SharedPreferences {
    private val data = linkedMapOf<String, Any?>()

    override fun getAll(): MutableMap<String, *> = data.toMutableMap()
    override fun getString(key: String?, defValue: String?): String? = data[key] as? String ?: defValue
    override fun getStringSet(key: String?, defValues: MutableSet<String>?): MutableSet<String>? = defValues
    override fun getInt(key: String?, defValue: Int): Int = (data[key] as? Int) ?: defValue
    override fun getLong(key: String?, defValue: Long): Long = (data[key] as? Long) ?: defValue
    override fun getFloat(key: String?, defValue: Float): Float = (data[key] as? Float) ?: defValue
    override fun getBoolean(key: String?, defValue: Boolean): Boolean = (data[key] as? Boolean) ?: defValue
    override fun contains(key: String?): Boolean = data.containsKey(key)
    override fun edit(): SharedPreferences.Editor = EditorImpl()
    override fun registerOnSharedPreferenceChangeListener(listener: SharedPreferences.OnSharedPreferenceChangeListener?) {}
    override fun unregisterOnSharedPreferenceChangeListener(listener: SharedPreferences.OnSharedPreferenceChangeListener?) {}

    private inner class EditorImpl : SharedPreferences.Editor {
        private val pending = linkedMapOf<String, Any?>()
        private val removals = linkedSetOf<String>()
        private var clearAll = false

        override fun putString(key: String?, value: String?): SharedPreferences.Editor = apply {
            if (key != null) pending[key] = value
        }
        override fun putStringSet(key: String?, values: MutableSet<String>?): SharedPreferences.Editor = this
        override fun putInt(key: String?, value: Int): SharedPreferences.Editor = apply {
            if (key != null) pending[key] = value
        }
        override fun putLong(key: String?, value: Long): SharedPreferences.Editor = apply {
            if (key != null) pending[key] = value
        }
        override fun putFloat(key: String?, value: Float): SharedPreferences.Editor = apply {
            if (key != null) pending[key] = value
        }
        override fun putBoolean(key: String?, value: Boolean): SharedPreferences.Editor = apply {
            if (key != null) pending[key] = value
        }
        override fun remove(key: String?): SharedPreferences.Editor = apply {
            if (key != null) removals += key
        }
        override fun clear(): SharedPreferences.Editor = apply { clearAll = true }
        override fun commit(): Boolean {
            apply()
            return true
        }
        override fun apply() {
            if (clearAll) data.clear()
            removals.forEach { data.remove(it) }
            data.putAll(pending)
        }
    }
}
