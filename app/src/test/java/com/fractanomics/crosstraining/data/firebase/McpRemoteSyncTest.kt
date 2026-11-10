package com.fractanomics.crosstraining.data.firebase

import com.fractanomics.crosstraining.data.BlockInsert
import com.fractanomics.crosstraining.data.DataModeManager
import com.fractanomics.crosstraining.data.FakeSampleAppDatabase
import com.fractanomics.crosstraining.data.FakeTransactionRunner
import com.fractanomics.crosstraining.data.Repository
import com.fractanomics.crosstraining.data.model.BlockKind
import com.fractanomics.crosstraining.data.model.BlockSet
import com.fractanomics.crosstraining.data.model.Cycle
import com.fractanomics.crosstraining.data.model.CycleGoal
import com.fractanomics.crosstraining.data.model.Routine
import com.fractanomics.crosstraining.data.model.Session
import com.fractanomics.crosstraining.data.model.SessionBlock
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
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
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
    fun downloadUserData_manualSyncMode_skipsUnstampedRemoteRecords_butAppliesMcpStampedEdits() = runTest {
        val date = LocalDate.of(2026, 11, 6)
        repo.saveRoutineWithBlocks(Routine(name = "Leg Day", description = "Local edit"), emptyList())

        // "Deleted Locally" is this device's previous upload (no updatedAtMillis) for a session the user
        // has since deleted; "Added via MCP" was written by the MCP server (stamped with updatedAtMillis).
        val remoteSessions: List<Map<String, Any>> = listOf(
            mapOf(
                "session" to mapOf("id" to 1L, "cycleId" to 1L, "date" to date.toString(), "title" to "Deleted Locally", "notes" to ""),
                "blocks" to emptyList<Map<String, Any>>()
            ),
            mapOf(
                "session" to mapOf(
                    "id" to 2L, "cycleId" to 1L, "date" to date.toString(), "title" to "Added via MCP",
                    "notes" to "From Claude", "updatedAtMillis" to 3000L
                ),
                "blocks" to emptyList<Map<String, Any>>()
            )
        )
        val remoteRoutines: List<Map<String, Any>> = listOf(
            mapOf(
                "routine" to mapOf("id" to 1L, "name" to "Leg Day", "description" to "Stale remote copy"),
                "blocks" to emptyList<Map<String, Any>>()
            ),
            mapOf(
                "routine" to mapOf("id" to 2L, "name" to "MCP Engine Builder", "description" to "From Gemini", "updatedAtMillis" to 3000L),
                "blocks" to emptyList<Map<String, Any>>()
            )
        )

        UserCloudSyncManager.documentReaderForTesting = { uid, col ->
            when {
                uid != "mcp-user-123" -> emptyList()
                col == "sessions" -> remoteSessions
                col == "routines" -> remoteRoutines
                else -> emptyList()
            }
        }

        assertTrue(UserCloudSyncManager.downloadUserData(repo, importUnmarkedRemoteRecords = false).isSuccess)

        val sessions = repo.getAllSessionsWithBlocksOnce()
        assertEquals(
            "Locally deleted (unstamped) sessions must not be resurrected by manual sync",
            listOf("Added via MCP"),
            sessions.map { it.session.title }
        )
        val routines = repo.getAllRoutinesWithBlocksOnce().associateBy { it.routine.name }
        assertEquals("Stale remote copy must not overwrite a newer local edit", "Local edit", routines["Leg Day"]?.routine?.description)
        assertEquals("From Gemini", routines["MCP Engine Builder"]?.routine?.description)
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

    @Test
    fun uploadUserData_stampsIsActiveCycleOnGoalsSessionsAndRepMaxes() = runTest {
        val cycle1Id = repo.saveCycle(
            Cycle(name = "Foundation", startDate = LocalDate.of(2026, 1, 1), isActive = false)
        )
        repo.saveCycle(Cycle(name = "Cycle 2", startDate = LocalDate.of(2026, 3, 1), isActive = false))
        repo.saveCycle(Cycle(name = "Cycle 3", startDate = LocalDate.of(2026, 6, 1), isActive = false))
        val cycle4Id = repo.saveCycle(
            Cycle(name = "Recomposition", startDate = LocalDate.of(2026, 9, 1), isActive = true)
        )
        repo.activateCycle(cycle4Id)

        val snatch = repo.getOrCreateExercise("Snatch")
        val backSquat = repo.getOrCreateExercise("Back Squat")

        repo.saveCycleGoal(
            CycleGoal(cycleId = cycle1Id, exerciseId = snatch.id, targetReps = 1, startWeight = 80.0, targetWeight = 90.0)
        )
        repo.saveCycleGoal(
            CycleGoal(cycleId = cycle4Id, exerciseId = snatch.id, targetReps = 3, startWeight = 85.0, targetWeight = 92.5)
        )

        repo.saveSession(
            Session(cycleId = cycle1Id, date = LocalDate.of(2026, 2, 10), title = "Cycle 1 Session"),
            emptyList()
        )
        repo.saveSession(
            Session(cycleId = cycle4Id, date = LocalDate.of(2026, 10, 10), title = "Cycle 4 Session"),
            emptyList()
        )

        repo.recordRepMax(snatch.id, reps = 1, weight = 88.0, date = LocalDate.of(2026, 2, 10), cycleId = cycle1Id)
        repo.recordRepMax(snatch.id, reps = 3, weight = 90.0, date = LocalDate.of(2026, 10, 10), cycleId = cycle4Id)
        repo.recordRepMax(backSquat.id, reps = 5, weight = 140.0, date = LocalDate.of(2026, 10, 11), cycleId = null)

        val writtenCollections = mutableMapOf<String, List<Map<String, Any?>>>()
        UserCloudSyncManager.documentWriterForTesting = { collectionName, data ->
            @Suppress("UNCHECKED_CAST")
            writtenCollections[collectionName] = (data["list"] as? List<Map<String, Any?>>).orEmpty()
        }

        assertTrue(UserCloudSyncManager.uploadUserData(repo).isSuccess)

        val goalsPayload = writtenCollections["cycle_goals"].orEmpty()
        assertEquals(2, goalsPayload.size)
        val c1Goal = goalsPayload.first { (it["cycleId"] as Number).toLong() == cycle1Id }
        val c4Goal = goalsPayload.first { (it["cycleId"] as Number).toLong() == cycle4Id }
        assertEquals(false, c1Goal["isActiveCycle"])
        assertEquals("Foundation", c1Goal["cycleName"])
        assertEquals(true, c4Goal["isActiveCycle"])
        assertEquals("Recomposition", c4Goal["cycleName"])

        val sessionsPayload = writtenCollections["sessions"].orEmpty()
        assertEquals(2, sessionsPayload.size)
        @Suppress("UNCHECKED_CAST")
        val c1Session = sessionsPayload.map { it["session"] as Map<String, Any?> }
            .first { (it["cycleId"] as Number).toLong() == cycle1Id }
        @Suppress("UNCHECKED_CAST")
        val c4Session = sessionsPayload.map { it["session"] as Map<String, Any?> }
            .first { (it["cycleId"] as Number).toLong() == cycle4Id }
        assertEquals(false, c1Session["isActiveCycle"])
        assertEquals(true, c4Session["isActiveCycle"])

        val rmPayload = writtenCollections["rep_maxes"].orEmpty()
        assertEquals(3, rmPayload.size)
        val c1Rm = rmPayload.first { (it["cycleId"] as? Number)?.toLong() == cycle1Id }
        val c4Rm = rmPayload.first { (it["cycleId"] as? Number)?.toLong() == cycle4Id }
        val unlinkedRm = rmPayload.first { it["cycleId"] == null }
        assertEquals(false, c1Rm["isActiveCycle"])
        assertEquals(true, c4Rm["isActiveCycle"])
        assertNull(unlinkedRm["isActiveCycle"])
    }

    @Test
    fun downloadUserData_appliesMcpDeletedAtMillisTombstones_andPreventsResurrectionOnUpload() = runTest {
        repo.saveCycle(Cycle(name = "Cycle 1", startDate = LocalDate.of(2026, 1, 1), isActive = false))
        repo.saveCycle(Cycle(name = "Cycle 2", startDate = LocalDate.of(2026, 3, 1), isActive = false))
        repo.saveCycle(Cycle(name = "Cycle 3", startDate = LocalDate.of(2026, 6, 1), isActive = false))
        val cycle4Id = repo.saveCycle(
            Cycle(name = "Recomposition", startDate = LocalDate.of(2026, 9, 1), isActive = true)
        )
        repo.activateCycle(cycle4Id)
        val snatch = repo.getOrCreateExercise("Snatch")
        val cleanAndJerk = repo.getOrCreateExercise("Clean & Jerk")

        // Local state before Sync Now: 2RM Snatch (to be converted to 3RM via MCP), 1RM Clean & Jerk,
        // 2 routines ("Old Oly Template" to be deleted via MCP, "Keep Strength" kept), and 2 sessions.
        repo.saveCycleGoal(
            CycleGoal(cycleId = cycle4Id, exerciseId = snatch.id, targetReps = 2, startWeight = 85.0, targetWeight = 95.0)
        )
        repo.saveCycleGoal(
            CycleGoal(cycleId = cycle4Id, exerciseId = cleanAndJerk.id, targetReps = 1, startWeight = 105.0, targetWeight = 115.0)
        )
        repo.saveRoutineWithBlocks(Routine(name = "Old Oly Template", description = "Delete me"), emptyList())
        repo.saveRoutineWithBlocks(Routine(name = "Keep Strength", description = "Keep me"), emptyList())

        val d1 = LocalDate.of(2026, 11, 5)
        val d2 = LocalDate.of(2026, 11, 6)
        repo.saveSession(
            Session(cycleId = cycle4Id, date = d1, title = "Deleted WOD", notes = "Remove via MCP"),
            listOf(
                BlockInsert(
                    block = SessionBlock(sessionId = 0, position = 0, name = "Snatch 2s", kind = BlockKind.WEIGHTLIFTING, mainExerciseId = snatch.id),
                    sets = listOf(BlockSet(blockId = 0, position = 0, reps = 2, weight = 85.0))
                )
            )
        )
        repo.saveSession(
            Session(cycleId = cycle4Id, date = d2, title = "Kept WOD", notes = "Still here"),
            emptyList()
        )

        // Simulate Firestore documents after MCP manage_cycle_goals (2RM -> 3RM), delete_routine, and delete_session
        val remoteGoals: List<Map<String, Any>> = listOf(
            mapOf(
                "id" to 1L,
                "cycleId" to 4L,
                "cycleName" to "Recomposition",
                "isActiveCycle" to true,
                "exerciseId" to snatch.id,
                "exerciseName" to "Snatch",
                "targetReps" to 2,
                "startWeight" to 85.0,
                "targetWeight" to 95.0,
                "notes" to "",
                "updatedAtMillis" to 5000L,
                "deletedAtMillis" to 5000L
            ),
            mapOf(
                "id" to 2L,
                "cycleId" to 4L,
                "cycleName" to "Recomposition",
                "isActiveCycle" to true,
                "exerciseId" to cleanAndJerk.id,
                "exerciseName" to "Clean & Jerk",
                "targetReps" to 1,
                "startWeight" to 105.0,
                "targetWeight" to 115.0,
                "notes" to ""
            ),
            mapOf(
                "id" to 3L,
                "cycleId" to 4L,
                "cycleName" to "Recomposition",
                "isActiveCycle" to true,
                "exerciseId" to snatch.id,
                "exerciseName" to "Snatch",
                "targetReps" to 3,
                "startWeight" to 85.0,
                "targetWeight" to 90.0,
                "notes" to "Converted to 3RM",
                "updatedAtMillis" to 5000L
            )
        )

        val remoteRoutines: List<Map<String, Any>> = listOf(
            mapOf(
                "routine" to mapOf(
                    "id" to 1L,
                    "name" to "Old Oly Template",
                    "description" to "Delete me",
                    "updatedAtMillis" to 5000L,
                    "deletedAtMillis" to 5000L
                ),
                "blocks" to emptyList<Map<String, Any>>()
            ),
            mapOf(
                "routine" to mapOf(
                    "id" to 2L,
                    "name" to "Keep Strength",
                    "description" to "Keep me"
                ),
                "blocks" to emptyList<Map<String, Any>>()
            )
        )

        val remoteSessions: List<Map<String, Any>> = listOf(
            mapOf(
                "session" to mapOf(
                    "id" to 1L,
                    "cycleId" to 4L,
                    "cycleName" to "Recomposition",
                    "isActiveCycle" to true,
                    "date" to d1.toString(),
                    "title" to "Deleted WOD",
                    "notes" to "Remove via MCP",
                    "updatedAtMillis" to 5000L,
                    "deletedAtMillis" to 5000L
                ),
                "blocks" to listOf(
                    mapOf(
                        "block" to mapOf(
                            "position" to 0,
                            "name" to "Snatch 2s",
                            "kind" to "WEIGHTLIFTING",
                            "mainExerciseId" to snatch.id,
                            "mainExerciseName" to "Snatch"
                        ),
                        "sets" to listOf(
                            mapOf("position" to 0, "reps" to 2, "weight" to 85.0, "isWarmup" to false, "isFailed" to false)
                        )
                    )
                )
            ),
            mapOf(
                "session" to mapOf(
                    "id" to 2L,
                    "cycleId" to 4L,
                    "cycleName" to "Recomposition",
                    "isActiveCycle" to true,
                    "date" to d2.toString(),
                    "title" to "Kept WOD",
                    "notes" to "Still here"
                ),
                "blocks" to emptyList<Map<String, Any>>()
            )
        )

        UserCloudSyncManager.documentReaderForTesting = { uid, col ->
            when {
                uid != "mcp-user-123" -> emptyList()
                col == "cycle_goals" -> remoteGoals
                col == "routines" -> remoteRoutines
                col == "sessions" -> remoteSessions
                else -> emptyList()
            }
        }

        // Step 1 of Sync Now: download (manual mode: importUnmarkedRemoteRecords = false)
        assertTrue(UserCloudSyncManager.downloadUserData(repo, importUnmarkedRemoteRecords = false).isSuccess)

        val localGoals = repo.snapshotCycleGoals()
        assertEquals("2RM Snatch must be deleted and 3RM Snatch + 1RM C&J retained", 2, localGoals.size)
        assertFalse(localGoals.any { it.exerciseId == snatch.id && it.targetReps == 2 })
        assertTrue(localGoals.any { it.exerciseId == snatch.id && it.targetReps == 3 && it.targetWeight == 90.0 })
        assertTrue(localGoals.any { it.exerciseId == cleanAndJerk.id && it.targetReps == 1 })

        val localRoutines = repo.getAllRoutinesWithBlocksOnce().map { it.routine.name }
        assertEquals(listOf("Keep Strength"), localRoutines)

        val localSessions = repo.getAllSessionsWithBlocksOnce().map { it.session.title }
        assertEquals(listOf("Kept WOD"), localSessions)

        // Step 2 of Sync Now: upload local state back to Firestore and verify ZERO resurrection
        val reuploaded = mutableMapOf<String, List<Map<String, Any?>>>()
        UserCloudSyncManager.documentWriterForTesting = { col, data ->
            @Suppress("UNCHECKED_CAST")
            reuploaded[col] = (data["list"] as? List<Map<String, Any?>>).orEmpty()
        }
        assertTrue(UserCloudSyncManager.uploadUserData(repo).isSuccess)

        val uploadedGoals = reuploaded["cycle_goals"].orEmpty()
        assertEquals(2, uploadedGoals.size)
        assertFalse(
            "2RM Snatch must never resurrect on subsequent upload",
            uploadedGoals.any { it["exerciseName"] == "Snatch" && (it["targetReps"] as Number).toInt() == 2 }
        )
        assertEquals(1, reuploaded["routines"].orEmpty().size)
        assertEquals(1, reuploaded["sessions"].orEmpty().size)
    }

    @Test
    fun downloadUserData_fullLoginImport_skipsMcpDeletedAtMillisTombstones() = runTest {
        repo.saveCycle(Cycle(name = "Recomposition", startDate = LocalDate.of(2026, 9, 1), isActive = true))

        UserCloudSyncManager.documentReaderForTesting = { uid, col ->
            when {
                uid != "mcp-user-123" -> emptyList()
                col == "cycle_goals" -> listOf(
                    mapOf(
                        "id" to 1L,
                        "cycleId" to 1L,
                        "cycleName" to "Recomposition",
                        "exerciseName" to "Ghost Lift",
                        "targetReps" to 2,
                        "targetWeight" to 90.0,
                        "updatedAtMillis" to 4000L,
                        "deletedAtMillis" to 4000L
                    ),
                    mapOf(
                        "id" to 2L,
                        "cycleId" to 1L,
                        "cycleName" to "Recomposition",
                        "exerciseName" to "Front Squat",
                        "targetReps" to 3,
                        "targetWeight" to 120.0,
                        "updatedAtMillis" to 4000L
                    )
                )
                col == "routines" -> listOf(
                    mapOf(
                        "routine" to mapOf(
                            "id" to 1L,
                            "name" to "Deleted Routine",
                            "updatedAtMillis" to 4000L,
                            "deletedAtMillis" to 4000L
                        ),
                        "blocks" to emptyList<Map<String, Any>>()
                    )
                )
                col == "sessions" -> listOf(
                    mapOf(
                        "session" to mapOf(
                            "id" to 1L,
                            "cycleId" to 1L,
                            "date" to "2026-11-01",
                            "title" to "Deleted Session",
                            "updatedAtMillis" to 4000L,
                            "deletedAtMillis" to 4000L
                        ),
                        "blocks" to emptyList<Map<String, Any>>()
                    )
                )
                else -> emptyList()
            }
        }

        assertTrue(UserCloudSyncManager.downloadUserData(repo, importUnmarkedRemoteRecords = true).isSuccess)

        val localGoals = repo.snapshotCycleGoals()
        assertEquals(1, localGoals.size)
        assertEquals(3, localGoals[0].targetReps)
        assertFalse(
            "Tombstoned goal must not pollute local exercises with Ghost Lift",
            repo.getAllExercisesOnce().any { it.name.equals("Ghost Lift", ignoreCase = true) }
        )
        assertTrue(repo.getAllRoutinesWithBlocksOnce().isEmpty())
        assertTrue(repo.getAllSessionsWithBlocksOnce().isEmpty())
    }
}
