package com.fractanomics.crosstraining.data

import com.fractanomics.crosstraining.data.firebase.AuthUser
import com.fractanomics.crosstraining.data.firebase.UserCloudSyncManager
import com.fractanomics.crosstraining.data.model.BlockKind
import com.fractanomics.crosstraining.data.model.BlockSet
import com.fractanomics.crosstraining.data.model.Cycle
import com.fractanomics.crosstraining.data.model.CycleGoal
import com.fractanomics.crosstraining.data.model.ExerciseCategory
import com.fractanomics.crosstraining.data.model.MetricType
import com.fractanomics.crosstraining.data.model.RepMax
import com.fractanomics.crosstraining.data.model.Session
import com.fractanomics.crosstraining.data.model.SessionBlock
import com.fractanomics.crosstraining.util.AVAILABLE_BLOCK_FORMAT
import com.fractanomics.crosstraining.util.AvailableBlock
import com.fractanomics.crosstraining.util.AvailableBlockCodec
import com.fractanomics.crosstraining.util.AvailableBlockExercise
import com.fractanomics.crosstraining.util.TrackedPrSlot
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.time.LocalDate

class AvailableBlockRepositoryAndSyncTest {

    private lateinit var fakeDb: FakeSampleAppDatabase
    private lateinit var repository: Repository

    @Before
    fun setup() {
        fakeDb = FakeSampleAppDatabase()
        repository = Repository(
            db = fakeDb,
            transactionRunner = FakeTransactionRunner(fakeDb)
        )
        UserCloudSyncManager.resetTestHandlers()
        UserCloudSyncManager.setAuthenticatedUser(
            AuthUser(uid = "ab-sync-user", email = "athlete@crosstraining.app", isAnonymous = false)
        )
        UserCloudSyncManager.authUidProviderForTesting = { "ab-sync-user" }
    }

    @After
    fun tearDown() {
        UserCloudSyncManager.resetTestHandlers()
        UserCloudSyncManager.setAuthenticatedUser(null)
    }

    @Test
    fun `saveAvailableBlock and updateAvailableBlockTrackedPrs persist tracked PR links in repository`() = runTest {
        val squat = repository.getOrCreateExercise("Back Squat")
        val clean = repository.getOrCreateExercise("Power Clean")

        val blockId = repository.saveAvailableBlock(
            AvailableBlock(
                id = 0L,
                title = "Heavy Lower Wave",
                instructions = "3x3 Back Squat + 2x1 Power Clean",
                exercises = listOf(
                    AvailableBlockExercise(squat.id, squat.name, squat.tracksRepMax),
                    AvailableBlockExercise(clean.id, clean.name, clean.tracksRepMax)
                ),
                trackedPrs = listOf(
                    TrackedPrSlot(squat.id, squat.name, 3),
                    TrackedPrSlot(clean.id, clean.name, 1)
                )
            )
        )
        assertTrue(blockId > 0L)

        val stored = repository.getAllRoutinesWithBlocksOnce().first { it.routine.id == blockId }
        assertEquals(AVAILABLE_BLOCK_FORMAT, stored.routine.defaultFormat)

        val decoded = AvailableBlockCodec.fromRoutineWithBlocks(
            rw = stored,
            exercises = repository.getAllExercisesOnce()
        )
        assertEquals("Heavy Lower Wave", decoded.title)
        assertEquals("3x3 Back Squat + 2x1 Power Clean", decoded.instructions)
        assertEquals(2, decoded.trackedPrs.size)

        // Remove 1 tracked PR slot and persist back onto template
        repository.updateAvailableBlockTrackedPrs(
            blockId = blockId,
            trackedPrs = listOf(TrackedPrSlot(squat.id, squat.name, 3))
        )
        val afterUpdate = AvailableBlockCodec.fromRoutineWithBlocks(
            rw = repository.getAllRoutinesWithBlocksOnce().first { it.routine.id == blockId },
            exercises = repository.getAllExercisesOnce()
        )
        assertEquals(1, afterUpdate.trackedPrs.size)
        assertEquals(3, afterUpdate.trackedPrs[0].rmReps)
    }

    @Test
    fun `saveSession and updateSession preserve multi-PR block attribution and update cycle goal best`() = runTest {
        val cycleId = repository.saveCycle(
            Cycle(name = "Strength Peak", startDate = LocalDate.of(2026, 11, 1), isActive = true)
        )
        val squat = repository.getOrCreateExercise("Back Squat")
        val burpees = repository.getOrCreateExercise(
            name = "Burpees",
            category = ExerciseCategory.GYMNASTICS,
            metricType = MetricType.REPS
        )
        repository.saveCycleGoal(
            CycleGoal(
                cycleId = cycleId,
                exerciseId = squat.id,
                goalMetric = MetricType.WEIGHT,
                targetReps = 1,
                startWeight = 110.0,
                targetWeight = 130.0
            )
        )
        val routineId = repository.saveAvailableBlock(
            AvailableBlock(
                id = 0L,
                title = "Squat + Metcon",
                instructions = "Wave squats then burpees",
                exercises = listOf(
                    AvailableBlockExercise(squat.id, squat.name, true),
                    AvailableBlockExercise(burpees.id, burpees.name, false)
                ),
                trackedPrs = listOf(TrackedPrSlot(squat.id, squat.name, 1))
            )
        )

        val date = LocalDate.of(2026, 11, 14)
        val sessionId = repository.saveSession(
            session = Session(cycleId = cycleId, date = date, title = "Squat + Metcon"),
            blocks = listOf(
                BlockInsert(
                    block = SessionBlock(
                        sessionId = 0L,
                        position = 0,
                        name = squat.name,
                        kind = BlockKind.STRENGTH,
                        mainExerciseId = squat.id,
                        routineId = routineId,
                        subBlock = "Squat + Metcon"
                    ),
                    sets = listOf(
                        BlockSet(blockId = 0L, position = 0, reps = 1, weight = 115.0),
                        BlockSet(blockId = 0L, position = 1, reps = 1, weight = 122.5)
                    ),
                    newRepMaxes = listOf(
                        RepMax(exerciseId = squat.id, reps = 1, weight = 122.5, date = date),
                        RepMax(exerciseId = squat.id, reps = 3, weight = 110.0, date = date)
                    )
                ),
                BlockInsert(
                    block = SessionBlock(
                        sessionId = 0L,
                        position = 1,
                        name = burpees.name,
                        kind = BlockKind.STRENGTH,
                        mainExerciseId = burpees.id,
                        routineId = routineId,
                        subBlock = "Squat + Metcon"
                    ),
                    sets = listOf(
                        BlockSet(blockId = 0L, position = 0, reps = 1, weight = null)
                    )
                )
            )
        )

        val savedSession = repository.sessionById(sessionId)!!
        assertEquals(2, savedSession.blocks.size)
        val squatBlockId = savedSession.blocks[0].block.id
        assertNull(savedSession.blocks[1].sets[0].weight)

        val storedRms = fakeDb.repMaxDao().getAllOnce().filter { it.sessionId == sessionId }
        assertEquals(2, storedRms.size)
        assertTrue(storedRms.all { it.blockId == squatBlockId })
        assertEquals(122.5, fakeDb.repMaxDao().bestWeight(squat.id, 1) ?: 0.0, 0.001)

        // Updating the session re-links surviving RepMax rows to the newly created block IDs
        repository.updateSession(
            session = savedSession.session.copy(notes = "Edited weight"),
            blocks = listOf(
                BlockInsert(
                    block = savedSession.blocks[0].block.copy(id = 0L),
                    sets = listOf(
                        BlockSet(blockId = 0L, position = 0, reps = 1, weight = 122.5)
                    )
                )
            )
        )
        val updatedSession = repository.sessionById(sessionId)!!
        val newSquatBlockId = updatedSession.blocks[0].block.id
        val reLinkedRms = fakeDb.repMaxDao().getAllOnce().filter { it.sessionId == sessionId }
        assertEquals(2, reLinkedRms.size)
        assertTrue(reLinkedRms.all { it.blockId == newSquatBlockId })
    }

    @Test
    fun `downloadUserData pre-creates ab_prs exercises handles null bodyweight sets and links rep_maxes`() = runTest {
        val remoteRoutines = listOf(
            mapOf(
                "routine" to mapOf(
                    "id" to 77L,
                    "name" to "Wave Clean",
                    "description" to "3RM progression",
                    "defaultFormat" to AVAILABLE_BLOCK_FORMAT,
                    "updatedAtMillis" to 1000L
                ),
                "blocks" to listOf(
                    mapOf(
                        "name" to "Power Clean",
                        "kind" to "STRENGTH",
                        "position" to 0,
                        "notes" to "[ab_prs=Power+Clean:3]\n3RM progression",
                        "subBlock" to "Wave Clean"
                    )
                )
            )
        )
        val remoteSessions = listOf(
            mapOf(
                "session" to mapOf(
                    "id" to 88L,
                    "cycleId" to 1L,
                    "date" to "2026-11-12",
                    "title" to "Wave Clean",
                    "updatedAtMillis" to 2000L
                ),
                "blocks" to listOf(
                    mapOf(
                        "block" to mapOf(
                            "id" to 501L,
                            "name" to "Power Clean",
                            "kind" to "STRENGTH",
                            "mainExerciseName" to "Power Clean",
                            "routineId" to 9999L, // stale/deleted remote routineId must safely become null
                            "routineName" to "Wave Clean",
                            "subBlock" to "Wave Clean"
                        ),
                        "sets" to listOf(
                            mapOf("reps" to 1, "weight" to 95.0),
                            mapOf("reps" to 1, "weight" to 0.0)
                        )
                    )
                )
            )
        )
        val remoteRepMaxes = listOf(
            mapOf(
                "exerciseName" to "Power Clean",
                "reps" to 3,
                "weight" to 95.0,
                "date" to "2026-11-12",
                "cycleId" to 1L,
                "sessionTitle" to "Wave Clean",
                "sourceBlockTitle" to "Wave Clean",
                "updatedAtMillis" to 3000L
            )
        )

        UserCloudSyncManager.documentReaderForTesting = { uid, col ->
            if (uid != "ab-sync-user") emptyList()
            else when (col) {
                "routines" -> remoteRoutines
                "sessions" -> remoteSessions
                "rep_maxes" -> remoteRepMaxes
                else -> emptyList()
            }
        }

        val result = UserCloudSyncManager.downloadUserData(repository)
        assertTrue(result.isSuccess)

        val powerClean = repository.getAllExercisesOnce().firstOrNull { it.name == "Power Clean" }
        assertNotNull(powerClean)
        assertTrue(powerClean!!.tracksRepMax)

        val syncedSessions = repository.getAllSessionsWithBlocksOnce()
        assertEquals(1, syncedSessions.size)
        val syncedBlock = syncedSessions[0].blocks[0]
        assertEquals(95.0, syncedBlock.sets[0].weight ?: 0.0, 0.001)
        assertNull(syncedBlock.sets[1].weight)

        val syncedRm = fakeDb.repMaxDao().getAllOnce().first()
        assertEquals(syncedSessions[0].session.id, syncedRm.sessionId)
        assertEquals(syncedBlock.block.id, syncedRm.blockId)
    }
}
