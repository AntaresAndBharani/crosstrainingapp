package com.fractanomics.crosstraining.data.firebase

import com.fractanomics.crosstraining.data.DataModeManager
import com.fractanomics.crosstraining.data.FakeSampleAppDatabase
import com.fractanomics.crosstraining.data.FakeTransactionRunner
import com.fractanomics.crosstraining.data.Repository
import com.fractanomics.crosstraining.data.model.Cycle
import com.fractanomics.crosstraining.ui.AppViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.time.LocalDate

@OptIn(ExperimentalCoroutinesApi::class)
class McpRemoteSyncTest {

    private val testDispatcher = StandardTestDispatcher()
    private lateinit var db: FakeSampleAppDatabase
    private lateinit var repo: Repository

    @Before
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
        db = FakeSampleAppDatabase()
        repo = Repository(db, FakeTransactionRunner(db))

        UserCloudSyncManager.resetTestHandlers()
        UserCloudSyncManager.setAuthenticatedUser(
            AuthUser(uid = "mcp-user-123", email = "athlete@crosstraining.app", isAnonymous = false)
        )
        UserCloudSyncManager.authUidProviderForTesting = { "mcp-user-123" }
    }

    @After
    fun tearDown() {
        UserCloudSyncManager.resetTestHandlers()
        UserCloudSyncManager.setAuthenticatedUser(null)
        Dispatchers.resetMain()
    }

    @Test
    fun downloadUserData_updatesExistingSession_whenRemoteHasUpdatedAtMillisFromMcp() = runTest {
        val date = LocalDate.of(2026, 11, 6)

        // 1. Initial MCP session download
        var remoteSessions: List<Map<String, Any>> = listOf(
            mapOf(
                "session" to mapOf(
                    "id" to 1L,
                    "cycleId" to 1L,
                    "date" to date.toString(),
                    "title" to "MCP Strength Session",
                    "notes" to "Initial plan",
                    "updatedAtMillis" to 1000L
                ),
                "blocks" to listOf(
                    mapOf(
                        "block" to mapOf(
                            "name" to "Back Squat 3x5",
                            "kind" to "STRENGTH",
                            "mainExerciseName" to "Back Squat"
                        ),
                        "sets" to listOf(
                            mapOf("reps" to 5, "weight" to 110.0)
                        )
                    )
                )
            )
        )

        UserCloudSyncManager.documentReaderForTesting = { uid, col ->
            if (uid == "mcp-user-123" && col == "sessions") remoteSessions else emptyList()
        }

        assertTrue(UserCloudSyncManager.downloadUserData(repo).isSuccess)
        val firstPass = repo.getAllSessionsWithBlocksOnce()
        assertEquals(1, firstPass.size)
        assertEquals("Initial plan", firstPass[0].session.notes)
        assertEquals(110.0, firstPass[0].blocks[0].sets[0].weight ?: 0.0, 0.01)

        // 2. User tells Claude/Gemini/ChatGPT to update the weight to 125kg and add notes
        remoteSessions = listOf(
            mapOf(
                "session" to mapOf(
                    "id" to 1L,
                    "cycleId" to 1L,
                    "date" to date.toString(),
                    "title" to "MCP Strength Session",
                    "notes" to "Updated via MCP to 125kg",
                    "updatedAtMillis" to 2000L
                ),
                "blocks" to listOf(
                    mapOf(
                        "block" to mapOf(
                            "name" to "Back Squat 3x5",
                            "kind" to "STRENGTH",
                            "mainExerciseName" to "Back Squat"
                        ),
                        "sets" to listOf(
                            mapOf("reps" to 5, "weight" to 125.0)
                        )
                    )
                )
            )
        )

        assertTrue(UserCloudSyncManager.downloadUserData(repo).isSuccess)
        val secondPass = repo.getAllSessionsWithBlocksOnce()
        assertEquals("Must update in place without duplicating session", 1, secondPass.size)
        assertEquals("Updated via MCP to 125kg", secondPass[0].session.notes)
        assertEquals(125.0, secondPass[0].blocks[0].sets[0].weight ?: 0.0, 0.01)
    }

    @Test
    fun downloadUserData_upsertsCycleGoalsInPlace_andResolvesExerciseNameFromMcp() = runTest {
        repo.saveCycle(Cycle(id = 1L, name = "Winter Strength", startDate = LocalDate.of(2026, 11, 1), isActive = true))

        var remoteGoals: List<Map<String, Any>> = listOf(
            mapOf(
                "id" to 1L,
                "cycleId" to 1L,
                "exerciseId" to 999L,
                "exerciseName" to "Snatch",
                "targetReps" to 1,
                "startWeight" to 85.0,
                "targetWeight" to 95.0,
                "notes" to "Phase 1"
            )
        )

        UserCloudSyncManager.documentReaderForTesting = { uid, col ->
            if (uid == "mcp-user-123" && col == "cycle_goals") remoteGoals else emptyList()
        }

        assertTrue(UserCloudSyncManager.downloadUserData(repo).isSuccess)
        val goalsAfterFirst = repo.snapshotCycleGoals()
        assertEquals(1, goalsAfterFirst.size)
        assertEquals(95.0, goalsAfterFirst[0].targetWeight, 0.01)

        // Update goal targetWeight via MCP
        remoteGoals = listOf(
            mapOf(
                "id" to 1L,
                "cycleId" to 1L,
                "exerciseId" to 999L,
                "exerciseName" to "Snatch",
                "targetReps" to 1,
                "startWeight" to 85.0,
                "targetWeight" to 100.0,
                "notes" to "Phase 2 bump"
            )
        )

        assertTrue(UserCloudSyncManager.downloadUserData(repo).isSuccess)
        val goalsAfterSecond = repo.snapshotCycleGoals()
        assertEquals("CycleGoal must be updated in place, not duplicated", 1, goalsAfterSecond.size)
        assertEquals(100.0, goalsAfterSecond[0].targetWeight, 0.01)
        assertEquals("Phase 2 bump", goalsAfterSecond[0].notes)
    }

    @Test
    fun triggerCloudSync_downloadsRemoteMcpChangesBeforeUploadingLocalState() = runTest {
        val callOrder = mutableListOf<String>()
        UserCloudSyncManager.downloadUserDataHandler = { _ ->
            callOrder.add("download")
            Result.success(Unit)
        }
        UserCloudSyncManager.uploadUserDataHandler = { _ ->
            callOrder.add("upload")
            Result.success(Unit)
        }

        val dataMode = DataModeManager(context = null)
        dataMode.setRepositoryForTesting(repo)
        val viewModel = AppViewModel(dataMode)
        advanceUntilIdle()

        var completed = false
        viewModel.triggerCloudSync { res ->
            assertNotNull(res)
            assertTrue(res.isSuccess)
            completed = true
        }
        advanceUntilIdle()

        assertTrue(completed)
        assertEquals(listOf("download", "upload"), callOrder)
    }
}
