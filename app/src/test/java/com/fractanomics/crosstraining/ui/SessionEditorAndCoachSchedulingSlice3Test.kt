package com.fractanomics.crosstraining.ui

import com.fractanomics.crosstraining.data.DataModeManager
import com.fractanomics.crosstraining.data.DataModeManagerTest.FakeTrackingSharedPreferences
import com.fractanomics.crosstraining.data.FakeSampleAppDatabase
import com.fractanomics.crosstraining.data.FakeTransactionRunner
import com.fractanomics.crosstraining.data.Repository
import com.fractanomics.crosstraining.data.analytics.FatLossAnalytics
import com.fractanomics.crosstraining.data.model.BlockKind
import com.fractanomics.crosstraining.data.model.Cycle
import com.fractanomics.crosstraining.data.model.CycleType
import com.fractanomics.crosstraining.data.model.DailyLog
import com.fractanomics.crosstraining.data.model.Exercise
import com.fractanomics.crosstraining.data.model.MetricType
import com.fractanomics.crosstraining.data.model.SessionBlock
import com.fractanomics.crosstraining.data.model.SessionWithBlocks
import com.fractanomics.crosstraining.ui.screens.BlockSeed
import com.fractanomics.crosstraining.ui.screens.BlockState
import com.fractanomics.crosstraining.ui.screens.buildBlockState
import com.fractanomics.crosstraining.ui.screens.sessionSeed
import com.fractanomics.crosstraining.ui.screens.toDraftOrNull
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.first
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
import java.time.DayOfWeek
import java.time.LocalDate

/**
 * Unit & Integration tests verifying Issue #566 / Pattern B - Slice 3 requirements:
 * 1. Propagate `isCompleted` through `BlockDraft` and `AppViewModel.buildBlockInserts()`.
 * 2. Block completion toggle (default false for Fat Loss, true for other cycles) and cardio minutes field in `SessionEditor.kt`.
 * 3. Fast Days and Rest Days bitmask chip selectors in `CyclesScreen.kt`.
 * 4. Daily Check-In bottom sheet / card for athlete fasting (Done / Ate something) and macros via `AppViewModel`.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class SessionEditorAndCoachSchedulingSlice3Test {

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

    // =========================================================================
    // 1. BlockDraft & AppViewModel Save Pipeline Propagation
    // =========================================================================

    @Test
    fun blockDraft_defaultsToIsCompletedFalse() {
        val draft = BlockDraft(
            name = "Warm-up",
            kind = BlockKind.WARMUP,
            format = "3 Rounds",
            scheme = "10-10-10",
            existingExerciseId = null,
            newExerciseName = null,
            routineId = null,
            description = "General warmup",
            resultText = "",
            resultValue = null,
            sets = emptyList(),
            newRepMaxReps = null,
            newRepMaxWeight = null
        )

        assertFalse("BlockDraft must default isCompleted to false", draft.isCompleted)
    }

    @Test
    fun appViewModelSaveSession_propagatesIsCompletedToDatabaseSessionBlock() = runTest {
        val cycleId = realRepo.saveCycle(Cycle(name = "Fat Loss Meso", startDate = LocalDate.now()))
        val exId = realRepo.getOrCreateExercise("Incline Dumbbell Press").id

        val blockDraftCompleted = BlockDraft(
            name = "Incline DB Press",
            kind = BlockKind.STRENGTH,
            format = "4x10",
            scheme = "10-10-10-10",
            existingExerciseId = exId,
            newExerciseName = null,
            routineId = null,
            description = "",
            resultText = "",
            resultValue = null,
            sets = listOf(SetDraft(reps = 10, weight = 30.0)),
            newRepMaxReps = null,
            newRepMaxWeight = null,
            isCompleted = true
        )

        val blockDraftIncomplete = BlockDraft(
            name = "LISS Incline Treadmill Walk",
            kind = BlockKind.CARDIO,
            format = "30 min",
            scheme = "",
            existingExerciseId = null,
            newExerciseName = null,
            routineId = null,
            description = "Zone 2",
            resultText = "30 min",
            resultValue = 30.0,
            sets = emptyList(),
            newRepMaxReps = null,
            newRepMaxWeight = null,
            isCompleted = false
        )

        val sessionDraft = SessionDraft(
            cycleId = cycleId,
            date = LocalDate.of(2026, 10, 5),
            title = "Chest & Cardio",
            notes = "Good pump",
            blocks = listOf(blockDraftCompleted, blockDraftIncomplete)
        )

        viewModel.saveSession(sessionDraft).join()

        val savedSessions = realDb.sessionDao().getAllSessionsOnce()
        assertEquals(1, savedSessions.size)
        val savedSessionWithBlocks = realDb.sessionDao().getByIdOnce(savedSessions[0].id)
        assertNotNull(savedSessionWithBlocks)
        assertEquals("Chest & Cardio", savedSessionWithBlocks!!.session.title)
        assertEquals(2, savedSessionWithBlocks.blocks.size)

        val block1 = savedSessionWithBlocks.blocks[0].block
        val block2 = savedSessionWithBlocks.blocks[1].block

        assertTrue("First block must have isCompleted = true", block1.isCompleted)
        assertFalse("Second block must have isCompleted = false", block2.isCompleted)
        assertEquals(30.0, block2.resultValue ?: 0.0, 0.001)
    }

    // =========================================================================
    // 2. SessionEditor BlockState & UI Defaults
    // =========================================================================

    @Test
    fun blockState_toDraftOrNull_propagatesIsCompletedAndCardioMinutes() {
        val exercise = Exercise(id = 10L, name = "Row Erg", metricType = MetricType.TIME)
        val blockState = BlockState(
            name = "Cardio Finisher",
            kind = BlockKind.CARDIO,
            format = "20 min",
            scheme = "",
            exercise = exercise,
            description = "Steady pace",
            resultText = "20 mins",
            resultValue = "20.5",
            isCompleted = true
        )

        val draft = blockState.toDraftOrNull()
        assertNotNull(draft)
        assertEquals("Cardio Finisher", draft!!.name)
        assertEquals(BlockKind.CARDIO, draft.kind)
        assertEquals(20.5, draft.resultValue ?: 0.0, 0.001)
        assertTrue(draft.isCompleted)
    }

    @Test
    fun buildBlockState_initializesWithCycleAwareDefault() {
        val seed = BlockSeed(name = "Barbell Squats", kind = BlockKind.STRENGTH)

        // For Fat Loss cycle: defaultCompleted = false
        val fatLossBlockState = buildBlockState(
            seed = seed,
            exercises = emptyList(),
            routines = emptyList(),
            defaultCompleted = false
        )
        assertFalse("In Fat Loss cycle, new blocks must default isCompleted to false", fatLossBlockState.isCompleted)

        // For Strength / other cycle: defaultCompleted = true
        val strengthBlockState = buildBlockState(
            seed = seed,
            exercises = emptyList(),
            routines = emptyList(),
            defaultCompleted = true
        )
        assertTrue("In Strength cycle, new blocks must default isCompleted to true", strengthBlockState.isCompleted)

        // If seed explicitly defines isCompleted, seed value must take precedence
        val explicitSeed = BlockSeed(name = "Deadlift", isCompleted = true)
        val explicitState = buildBlockState(
            seed = explicitSeed,
            exercises = emptyList(),
            routines = emptyList(),
            defaultCompleted = false
        )
        assertTrue("Explicit seed isCompleted = true must take precedence over default", explicitState.isCompleted)
    }

    @Test
    fun sessionSeed_preservesIsCompletedFromExistingSession() {
        val sessionBlock = SessionBlock(
            id = 42L,
            sessionId = 1L,
            position = 0,
            name = "Romanian Deadlift",
            isCompleted = false
        )
        val sessionWithBlocks = SessionWithBlocks(
            session = com.fractanomics.crosstraining.data.model.Session(
                id = 1L,
                cycleId = 1L,
                date = LocalDate.now(),
                title = "Posterior Chain"
            ),
            blocks = listOf(
                com.fractanomics.crosstraining.data.model.BlockWithSets(
                    block = sessionBlock,
                    sets = emptyList()
                )
            )
        )

        val seed = sessionSeed(sessionWithBlocks)
        assertEquals(1, seed.blocks.size)
        assertEquals(false, seed.blocks[0].isCompleted)
    }

    // =========================================================================
    // 3. Coach Cycle Scheduling & Bitmask Chip Selectors
    // =========================================================================

    @Test
    fun coachCycle_bitmaskChipSelectors_encodeAndDecodeFastAndRestDays() {
        // Scheduled fast days: Monday (bit 0 = 1) and Thursday (bit 3 = 8) -> Mask = 9
        val fastDays = listOf(DayOfWeek.MONDAY, DayOfWeek.THURSDAY)
        val fastMask = FatLossAnalytics.createDayOfWeekMask(fastDays)
        assertEquals(9, fastMask)

        // Scheduled rest days: Sunday (bit 6 = 64) -> Mask = 64
        val restDays = listOf(DayOfWeek.SUNDAY)
        val restMask = FatLossAnalytics.createDayOfWeekMask(restDays)
        assertEquals(64, restMask)

        val cycle = Cycle(
            name = "Summer Shred",
            startDate = LocalDate.of(2026, 10, 1),
            type = CycleType.FAT_LOSS_BODYBUILDING,
            fastDaysOfWeek = fastMask,
            restDaysOfWeek = restMask
        )

        assertEquals(CycleType.FAT_LOSS_BODYBUILDING, cycle.type)
        assertEquals(9, cycle.fastDaysOfWeek)
        assertEquals(64, cycle.restDaysOfWeek)

        // Verification of day detection
        assertTrue(FatLossAnalytics.isScheduledFastDay(LocalDate.of(2026, 10, 5), cycle.fastDaysOfWeek)) // 2026-10-05 is Monday
        assertFalse(FatLossAnalytics.isScheduledFastDay(LocalDate.of(2026, 10, 6), cycle.fastDaysOfWeek)) // 2026-10-06 is Tuesday
        assertTrue(FatLossAnalytics.isScheduledFastDay(LocalDate.of(2026, 10, 8), cycle.fastDaysOfWeek)) // 2026-10-08 is Thursday

        assertTrue(FatLossAnalytics.isScheduledRestDay(LocalDate.of(2026, 10, 11), cycle.restDaysOfWeek)) // 2026-10-11 is Sunday
        assertFalse(FatLossAnalytics.isScheduledRestDay(LocalDate.of(2026, 10, 10), cycle.restDaysOfWeek)) // 2026-10-10 is Saturday

        // Toggling a day using XOR
        val wednesdayBit = FatLossAnalytics.dayOfWeekToBit(DayOfWeek.WEDNESDAY) // bit 2 = 4
        val toggledMask = fastMask xor wednesdayBit // 9 xor 4 = 13
        assertTrue(FatLossAnalytics.isDayInMask(DayOfWeek.WEDNESDAY, toggledMask))
        assertTrue(FatLossAnalytics.isDayInMask(DayOfWeek.MONDAY, toggledMask))
        assertTrue(FatLossAnalytics.isDayInMask(DayOfWeek.THURSDAY, toggledMask))

        // Untoggling Wednesday
        val untoggledMask = toggledMask xor wednesdayBit
        assertFalse(FatLossAnalytics.isDayInMask(DayOfWeek.WEDNESDAY, untoggledMask))
        assertEquals(fastMask, untoggledMask)
    }

    // =========================================================================
    // 4. Daily Check-In (Fasting adherence & Macros) via AppViewModel
    // =========================================================================

    @Test
    fun dailyLog_saveAndRetrieveViaViewModel_emitsInDailyLogsFlow() = runTest {
        val collectJob = launch(testDispatcher) { viewModel.dailyLogs.collect {} }
        val date = LocalDate.of(2026, 10, 5)

        assertEquals(0, viewModel.dailyLogs.value.size)

        // Athlete logs fast as "Done" with macros
        val log1 = DailyLog(
            date = date,
            fastCompleted = true,
            isRestDay = false,
            caloriesKcal = 2100,
            proteinGrams = 185,
            carbsGrams = 190,
            fatGrams = 60,
            notes = "Fasted 16 hours cleanly",
            updatedAtMillis = System.currentTimeMillis()
        )
        viewModel.saveDailyLog(log1)

        val logsAfterSave = viewModel.dailyLogs.value
        assertEquals(1, logsAfterSave.size)
        val entry = logsAfterSave[0]
        assertEquals(date, entry.date)
        assertEquals(true, entry.fastCompleted)
        assertFalse(entry.isRestDay)
        assertEquals(2100, entry.caloriesKcal)
        assertEquals(185, entry.proteinGrams)
        assertEquals(190, entry.carbsGrams)
        assertEquals(60, entry.fatGrams)
        assertEquals("Fasted 16 hours cleanly", entry.notes)

        // Athlete updates fast to "Ate something" (fastCompleted = false)
        val updatedLog = entry.copy(
            fastCompleted = false,
            caloriesKcal = 2500,
            notes = "Ate early breakfast"
        )
        viewModel.saveDailyLog(updatedLog)

        val logsAfterUpdate = viewModel.dailyLogs.value
        assertEquals(1, logsAfterUpdate.size)
        assertEquals(false, logsAfterUpdate[0].fastCompleted)
        assertEquals(2500, logsAfterUpdate[0].caloriesKcal)
        assertEquals("Ate early breakfast", logsAfterUpdate[0].notes)

        // Delete DailyLog
        viewModel.deleteDailyLog(date)

        val logsAfterDelete = viewModel.dailyLogs.value
        assertEquals(0, logsAfterDelete.size)

        collectJob.cancel()
    }
}
