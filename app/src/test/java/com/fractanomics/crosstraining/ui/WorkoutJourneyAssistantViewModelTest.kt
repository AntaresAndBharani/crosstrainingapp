package com.fractanomics.crosstraining.ui

import com.fractanomics.crosstraining.data.DataModeManager
import com.fractanomics.crosstraining.data.FakeSampleAppDatabase
import com.fractanomics.crosstraining.data.FakeTransactionRunner
import com.fractanomics.crosstraining.data.Repository
import com.fractanomics.crosstraining.data.ai.ExerciseEntityGrounder
import com.fractanomics.crosstraining.data.ai.FitnessSpeechLexicon
import com.fractanomics.crosstraining.data.ai.WorkoutEntityResolver
import com.fractanomics.crosstraining.data.model.BlockKind
import com.fractanomics.crosstraining.data.model.ExerciseCategory
import com.fractanomics.crosstraining.data.model.MetricType
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
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
 * Unit tests for [AppViewModel] workout journey assistant state and actions (Issue #517):
 * - Step 1: Parsing workout text into structured draft document & resolution result
 * - Step 2: Cycle and Routine template options configuration
 * - Step 3: Exercise review with category and metric dropdown adjustments
 * - Step 4: Preview of macro-block sections and atomic persistence via confirmWorkoutJourney
 */
@OptIn(ExperimentalCoroutinesApi::class)
class WorkoutJourneyAssistantViewModelTest {

    private val testDispatcher = UnconfinedTestDispatcher()

    private lateinit var fakeDb: FakeSampleAppDatabase
    private lateinit var transactionRunner: FakeTransactionRunner
    private lateinit var repository: Repository
    private lateinit var dataModeManager: DataModeManager
    private lateinit var viewModel: AppViewModel

    private val sampleDate = LocalDate.of(2026, 9, 8)

    @Before
    fun setUp() {
        Dispatchers.setMain(testDispatcher)

        fakeDb = FakeSampleAppDatabase()
        fakeDb.populateSampleData()
        transactionRunner = FakeTransactionRunner(fakeDb)

        repository = Repository(
            db = fakeDb,
            transactionRunner = transactionRunner,
            grounder = ExerciseEntityGrounder.DEFAULT,
            lexicon = FitnessSpeechLexicon.DEFAULT,
            entityResolver = WorkoutEntityResolver.DEFAULT
        )

        dataModeManager = DataModeManager(null)
        dataModeManager.setRepositoryForTesting(repository)

        viewModel = AppViewModel(dataModeManager)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun `initial workout journey draft state is null`() {
        assertNull(viewModel.workoutJourneyDraft.value)
    }

    @Test
    fun `processWorkoutText parses raw text and populates workoutJourneyDraft with 4-step data`() = runTest {
        val rawText = """
            Mondays --- repeateble
            
            Strengh & Power block:
            E3MOM COMPLEX: Clean + Hang Clean + Front Squat + Push to OverHead
            52,5 55 55 55
            
            Accessories block:
            E3MOM Trisets
            Romanian Deadlift
            70 75
            Pullups
            5 4
            DB Twist Curl
            12 12
        """.trimIndent()

        val success = viewModel.processWorkoutText(rawText)
        assertTrue(success)

        val draft = viewModel.workoutJourneyDraft.value
        assertNotNull(draft)
        assertEquals("Mondays", draft?.routineTitle)
        assertTrue(draft?.saveAsRoutine == true)
        assertTrue(draft?.logAsSession == true)
        assertEquals(2, draft?.document?.sections?.size)
        assertEquals(4, draft?.document?.blocks?.size)

        // Verify exercises resolved
        assertNotNull(draft?.resolutionResult)
        assertTrue(draft?.missingExercises?.any { it.name.contains("Romanian Deadlift", ignoreCase = true) } == true)
        assertTrue(draft?.missingExercises?.any { it.name.contains("DB Twist Curl", ignoreCase = true) } == true)
    }

    @Test
    fun `processWorkoutText with blank text returns false and leaves draft null`() = runTest {
        val success = viewModel.processWorkoutText("   ")
        assertFalse(success)
        assertNull(viewModel.workoutJourneyDraft.value)
    }

    @Test
    fun `updateJourneyStep advances step from 1 to 2, 3, 4`() = runTest {
        val rawText = """
            # Strength block
            Back Squat
            100 105 110
        """.trimIndent()

        viewModel.processWorkoutText(rawText)
        assertEquals(1, viewModel.workoutJourneyDraft.value?.currentStep)

        viewModel.setJourneyStep(2)
        assertEquals(2, viewModel.workoutJourneyDraft.value?.currentStep)

        viewModel.setJourneyStep(3)
        assertEquals(3, viewModel.workoutJourneyDraft.value?.currentStep)

        viewModel.setJourneyStep(4)
        assertEquals(4, viewModel.workoutJourneyDraft.value?.currentStep)
    }

    @Test
    fun `updateJourneyOptions modifies cycle, routine and session options`() = runTest {
        val rawText = """
            # Strength block
            Back Squat
            100 105
        """.trimIndent()

        viewModel.processWorkoutText(rawText)
        viewModel.updateJourneyTemplateOptions(
            routineTitle = "Custom Routine Name",
            saveAsRoutine = false,
            sessionTitle = "Custom Session Name",
            logAsSession = true,
            cycleId = 42L,
            sessionDate = sampleDate
        )

        val draft = viewModel.workoutJourneyDraft.value
        assertNotNull(draft)
        assertEquals("Custom Routine Name", draft?.routineTitle)
        assertFalse(draft?.saveAsRoutine ?: true)
        assertEquals("Custom Session Name", draft?.sessionTitle)
        assertTrue(draft?.logAsSession ?: false)
        assertEquals(42L, draft?.cycleId)
        assertEquals(sampleDate, draft?.sessionDate)
    }

    @Test
    fun `updateMissingExerciseCategoryAndMetric updates proposed exercise before persistence`() = runTest {
        val rawText = """
            # Accessories
            Banded Reverse Flys x15
        """.trimIndent()

        viewModel.processWorkoutText(rawText)
        val missing = viewModel.workoutJourneyDraft.value?.missingExercises
        assertNotNull(missing)
        assertTrue(missing!!.isNotEmpty())

        val firstMissing = missing.first()
        viewModel.updateMissingExerciseConfig(
            exerciseName = firstMissing.name,
            category = ExerciseCategory.BARBELL,
            metricType = MetricType.WEIGHT
        )

        val updatedDraft = viewModel.workoutJourneyDraft.value
        val updatedMissing = updatedDraft?.missingExercises?.find { it.name == firstMissing.name }
        assertNotNull(updatedMissing)
        assertEquals(ExerciseCategory.BARBELL, updatedMissing?.category)
        assertEquals(MetricType.WEIGHT, updatedMissing?.metricType)
    }

    @Test
    fun `confirmWorkoutJourney persists entities to database and clears draft`() = runTest {
        val rawText = """
            Mondays --- repeateble
            # Strengh & Power block
            Back Squat
            100 110 120
        """.trimIndent()

        viewModel.processWorkoutText(rawText)

        var persistedRoutineId: Long? = null
        var persistedSessionId: Long? = null
        var callbackInvoked = false

        val job = viewModel.confirmWorkoutJourney { routine, session ->
            callbackInvoked = true
            persistedRoutineId = routine?.id
            persistedSessionId = session?.id
        }
        job.join()

        assertTrue(callbackInvoked)
        assertNotNull(persistedRoutineId)
        assertNotNull(persistedSessionId)
        assertNull(viewModel.workoutJourneyDraft.value)

        val allSessions = fakeDb.sessionDao().getAllSessionsOnce()
        assertTrue(allSessions.any { it.id == persistedSessionId })

        val allRoutines = fakeDb.routineDao().getAllOnce()
        assertTrue(allRoutines.any { it.id == persistedRoutineId })
    }

    @Test
    fun `clearWorkoutJourneyDraft resets draft to null`() = runTest {
        viewModel.processWorkoutText("Back Squat\n100 105")
        assertNotNull(viewModel.workoutJourneyDraft.value)

        viewModel.clearWorkoutJourneyDraft()
        assertNull(viewModel.workoutJourneyDraft.value)
    }
}
