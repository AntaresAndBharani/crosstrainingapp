package com.fractanomics.crosstraining.data.firebase

import com.fractanomics.crosstraining.data.FakeSampleAppDatabase
import com.fractanomics.crosstraining.data.FakeTransactionRunner
import com.fractanomics.crosstraining.data.Repository
import com.fractanomics.crosstraining.data.model.BlockKind
import com.fractanomics.crosstraining.data.model.Cycle
import com.fractanomics.crosstraining.data.model.CycleGoal
import com.fractanomics.crosstraining.data.model.Exercise
import com.fractanomics.crosstraining.data.model.ExerciseCategory
import com.fractanomics.crosstraining.data.model.MetricType
import com.fractanomics.crosstraining.data.model.Routine
import com.fractanomics.crosstraining.data.model.RoutineBlock
import com.fractanomics.crosstraining.data.model.Session
import com.fractanomics.crosstraining.data.model.SessionBlock
import com.fractanomics.crosstraining.data.model.BlockSet
import com.fractanomics.crosstraining.data.BlockInsert
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
import java.time.LocalDate

/**
 * Unit tests verifying Issue #485 Scenario 2:
 * Per-Document Empty Overwrite Guard in UserCloudSyncManager.uploadUserData.
 *
 * For any collection document (exercises, routines, sessions, cycle_goals, rep_maxes)
 * that is locally empty, uploadUserData checks remote document population before uploading.
 * If remotely populated, the destructive overwrite (.set()) is skipped to protect remote backups.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class UserCloudSyncManagerOverwriteGuardTest {

    private val testDispatcher = StandardTestDispatcher()
    private lateinit var db: FakeSampleAppDatabase
    private lateinit var repo: Repository

    private val remoteCollectionsState = mutableMapOf<String, Boolean>()
    private val writtenCollections = mutableMapOf<String, Map<String, Any?>>()

    @Before
    fun setUp() {
        Dispatchers.setMain(testDispatcher)

        db = FakeSampleAppDatabase()
        repo = Repository(db, FakeTransactionRunner(db))

        remoteCollectionsState.clear()
        writtenCollections.clear()

        UserCloudSyncManager.resetTestHandlers()
        UserCloudSyncManager.setAuthenticatedUser(
            AuthUser(uid = "test-user-id", email = "test@example.com", isAnonymous = false)
        )

        UserCloudSyncManager.remoteCollectionInspectorForTesting = { collectionName ->
            remoteCollectionsState[collectionName] == true
        }

        UserCloudSyncManager.documentWriterForTesting = { collectionName, data ->
            writtenCollections[collectionName] = data
        }
    }

    @After
    fun tearDown() {
        UserCloudSyncManager.resetTestHandlers()
        UserCloudSyncManager.setAuthenticatedUser(null)
        Dispatchers.resetMain()
    }

    @Test
    fun uploadUserData_skipsAllDestructiveOverwrites_whenLocalIsEmptyAndRemoteIsPopulated() = runTest {
        // Given all remote collections are populated in cloud
        remoteCollectionsState["exercises"] = true
        remoteCollectionsState["routines"] = true
        remoteCollectionsState["sessions"] = true
        remoteCollectionsState["cycle_goals"] = true
        remoteCollectionsState["rep_maxes"] = true

        // And local database is completely empty (no sample data populated)
        assertEquals(0, repo.getAllExercisesOnce().size)
        assertEquals(0, repo.getAllRoutinesWithBlocksOnce().size)
        assertEquals(0, repo.getAllSessionsWithBlocksOnce().size)
        assertEquals(0, repo.snapshotCycleGoals().size)
        assertEquals(0, repo.exportSnapshot().repMaxes.size)

        // When uploadUserData executes
        val result = UserCloudSyncManager.uploadUserData(repo)

        // Then upload succeeds without throwing
        assertTrue("Upload must succeed", result.isSuccess)

        // And all 5 collections must be skipped from overwrite
        assertTrue("No documents should be overwritten when local is empty and remote is populated", writtenCollections.isEmpty())
        assertFalse("exercises must not be overwritten", writtenCollections.containsKey("exercises"))
        assertFalse("routines must not be overwritten", writtenCollections.containsKey("routines"))
        assertFalse("sessions must not be overwritten", writtenCollections.containsKey("sessions"))
        assertFalse("cycle_goals must not be overwritten", writtenCollections.containsKey("cycle_goals"))
        assertFalse("rep_maxes must not be overwritten", writtenCollections.containsKey("rep_maxes"))
    }

    @Test
    fun uploadUserData_overwritesCollections_whenLocalIsPopulatedEvenIfRemoteIsPopulated() = runTest {
        // Given all remote collections are populated in cloud
        remoteCollectionsState["exercises"] = true
        remoteCollectionsState["routines"] = true
        remoteCollectionsState["sessions"] = true
        remoteCollectionsState["cycle_goals"] = true
        remoteCollectionsState["rep_maxes"] = true

        // And local database is populated with items for all 5 entities
        val exercise = Exercise(id = 1L, name = "Snatch", category = ExerciseCategory.BARBELL, metricType = MetricType.WEIGHT, unit = "kg", tracksRepMax = true)
        db.exerciseDao().insert(exercise)

        val routine = Routine(id = 0L, name = "Morning WOD", description = "Test", defaultFormat = "AMRAP")
        val routineBlock = RoutineBlock(id = 0L, routineId = 0L, position = 0, name = "Block 1", kind = BlockKind.WEIGHTLIFTING, format = "AMRAP", setsCount = 1, targetRepsScheme = "5", exerciseIdsCsv = "1", notes = "")
        repo.saveRoutineWithBlocks(routine, listOf(routineBlock))

        val session = Session(id = 0L, cycleId = 1L, date = LocalDate.now(), title = "Session 1", notes = "")
        val sessionBlock = SessionBlock(id = 1L, sessionId = 1L, position = 0, name = "Block 1", kind = BlockKind.WEIGHTLIFTING, format = "", scheme = "", mainExerciseId = 1L, routineId = 1L, description = "", resultText = "", resultValue = null, notes = "")
        val blockSet = BlockSet(id = 1L, blockId = 1L, position = 0, groupIndex = 0, reps = 5, weight = 100.0, metricValue = 0.0, isWarmup = false, isFailed = false, notes = "")
        repo.saveSession(session, listOf(BlockInsert(sessionBlock, listOf(blockSet))))

        val cycle = Cycle(id = 1L, name = "Cycle 1", startDate = LocalDate.now(), endDate = LocalDate.now().plusDays(30), isActive = true)
        val cycleGoal = CycleGoal(id = 1L, cycleId = 1L, exerciseId = 1L, targetReps = 5, startWeight = 80.0, targetWeight = 100.0, notes = "")
        repo.saveCycleWithGoals(cycle, listOf(cycleGoal))

        repo.recordRepMax(exerciseId = 1L, reps = 1, weight = 105.0, date = LocalDate.now(), cycleId = 1L)

        // When uploadUserData executes
        val result = UserCloudSyncManager.uploadUserData(repo)

        // Then all 5 collections are uploaded because local has data
        assertTrue("Upload must succeed", result.isSuccess)
        assertEquals("All 5 collections must be uploaded", 5, writtenCollections.size)
        assertTrue(writtenCollections.containsKey("exercises"))
        assertTrue(writtenCollections.containsKey("routines"))
        assertTrue(writtenCollections.containsKey("sessions"))
        assertTrue(writtenCollections.containsKey("cycle_goals"))
        assertTrue(writtenCollections.containsKey("rep_maxes"))
    }

    @Test
    fun uploadUserData_uploadsEmptyList_whenBothLocalAndRemoteAreEmpty() = runTest {
        // Given all remote collections are empty
        remoteCollectionsState["exercises"] = false
        remoteCollectionsState["routines"] = false
        remoteCollectionsState["sessions"] = false
        remoteCollectionsState["cycle_goals"] = false
        remoteCollectionsState["rep_maxes"] = false

        // When uploadUserData executes on empty local repo
        val result = UserCloudSyncManager.uploadUserData(repo)

        // Then upload proceeds and writes all 5 documents as there is no remote backup to protect
        assertTrue("Upload must succeed", result.isSuccess)
        assertEquals(5, writtenCollections.size)
        for (col in listOf("exercises", "routines", "sessions", "cycle_goals", "rep_maxes")) {
            val payload = writtenCollections[col]?.get("list") as? List<*>
            assertEquals("Collection $col must write empty list", 0, payload?.size)
        }
    }

    @Test
    fun uploadUserData_protectsSelectively_perCollection() = runTest {
        // Given remote has exercises and sessions, but no routines, goals, or rep maxes
        remoteCollectionsState["exercises"] = true
        remoteCollectionsState["sessions"] = true
        remoteCollectionsState["routines"] = false
        remoteCollectionsState["cycle_goals"] = false
        remoteCollectionsState["rep_maxes"] = false

        // And local has ONLY exercises populated (so routines, sessions, goals, rep maxes are empty)
        val exercise = Exercise(id = 1L, name = "Clean & Jerk", category = ExerciseCategory.BARBELL, metricType = MetricType.WEIGHT, unit = "kg", tracksRepMax = true)
        db.exerciseDao().insert(exercise)

        // When uploadUserData executes
        val result = UserCloudSyncManager.uploadUserData(repo)

        assertTrue(result.isSuccess)

        // 1. exercises is locally populated -> WRITTEN
        assertTrue("exercises must be written", writtenCollections.containsKey("exercises"))

        // 2. sessions is locally empty and remotely populated -> SKIPPED (protected)
        assertFalse("sessions must be protected from empty overwrite", writtenCollections.containsKey("sessions"))

        // 3. routines, cycle_goals, rep_maxes are locally empty AND remotely empty -> WRITTEN
        assertTrue("routines must be written when remote is empty", writtenCollections.containsKey("routines"))
        assertTrue("cycle_goals must be written when remote is empty", writtenCollections.containsKey("cycle_goals"))
        assertTrue("rep_maxes must be written when remote is empty", writtenCollections.containsKey("rep_maxes"))
    }
}
