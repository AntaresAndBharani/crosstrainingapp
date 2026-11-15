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
import com.fractanomics.crosstraining.data.model.Routine
import com.fractanomics.crosstraining.data.model.RoutineBlock
import com.fractanomics.crosstraining.data.model.Session
import com.fractanomics.crosstraining.data.model.SessionBlock
import com.fractanomics.crosstraining.util.AVAILABLE_BLOCK_FORMAT
import com.fractanomics.crosstraining.util.AvailableBlock
import com.fractanomics.crosstraining.util.AvailableBlockCodec
import com.fractanomics.crosstraining.util.AvailableBlockExercise
import com.fractanomics.crosstraining.util.RoutineTargetCodec
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
    fun `updating tracked PRs on a legacy daily routine keeps its multi-block structure and rb_v2 targets`() = runTest {
        val squat = repository.getOrCreateExercise("Back Squat")
        val burpees = repository.getOrCreateExercise(
            name = "Burpees",
            category = ExerciseCategory.GYMNASTICS,
            metricType = MetricType.REPS
        )
        val routineId = repository.saveRoutineWithBlocks(
            Routine(name = "Thursday Engine", description = "Strength then engine", defaultFormat = "EMOM"),
            listOf(
                RoutineBlock(
                    name = "Strength", kind = BlockKind.STRENGTH, format = "EMOM", setsCount = 4,
                    exerciseIdsCsv = squat.id.toString(),
                    notes = "[rb_v2=REPS:PER_SET:5:5.0.4.0]\nCrisp reps"
                ),
                RoutineBlock(
                    name = "Engine", kind = BlockKind.METCON, format = "For Time", setsCount = 1,
                    exerciseIdsCsv = burpees.id.toString(), notes = "Negative split"
                )
            )
        )

        repository.updateAvailableBlockTrackedPrs(
            blockId = routineId,
            trackedPrs = listOf(TrackedPrSlot(squat.id, squat.name, 3))
        )

        val stored = repository.getAllRoutinesWithBlocksOnce().first { it.routine.id == routineId }
        assertEquals("EMOM", stored.routine.defaultFormat)
        assertEquals("Strength then engine", stored.routine.description)
        val blocks = stored.blocks.sortedBy { it.position }
        assertEquals(2, blocks.size)
        assertEquals("EMOM", blocks[0].format)
        assertEquals(4, blocks[0].setsCount)
        assertEquals("For Time", blocks[1].format)
        assertTrue(RoutineTargetCodec.hasStructuredHeader(blocks[0].notes))
        assertEquals("Crisp reps", RoutineTargetCodec.extractCleanNotes(blocks[0].notes))
        assertEquals("Negative split", blocks[1].notes)
        val decoded = AvailableBlockCodec.fromRoutineWithBlocks(stored, repository.getAllExercisesOnce())
        assertEquals(listOf(3), decoded.trackedPrs.map { it.rmReps })
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

    @Test
    fun `updateSession with reconcileRepMaxes reconciles weight updates additions and deletions in place`() = runTest {
        val cycleId = repository.saveCycle(
            Cycle(name = "Weightlifting", startDate = LocalDate.of(2026, 11, 1), isActive = true)
        )
        val snatch = repository.getOrCreateExercise("Snatch")
        val priorDate = LocalDate.of(2026, 11, 5)
        val editDate = LocalDate.of(2026, 11, 14)

        // Prior session establishes historical 3RM @ 32.5 kg and 1RM @ 37.5 kg
        repository.saveSession(
            session = Session(cycleId = cycleId, date = priorDate, title = "Prior Snatch"),
            blocks = listOf(
                BlockInsert(
                    block = SessionBlock(
                        sessionId = 0L, position = 0, name = snatch.name,
                        kind = BlockKind.STRENGTH, mainExerciseId = snatch.id
                    ),
                    sets = listOf(BlockSet(blockId = 0L, position = 0, reps = 3, weight = 32.5)),
                    newRepMaxes = listOf(
                        RepMax(exerciseId = snatch.id, reps = 3, weight = 32.5, date = priorDate),
                        RepMax(exerciseId = snatch.id, reps = 1, weight = 37.5, date = priorDate)
                    )
                )
            )
        )

        val editedSessionId = repository.saveSession(
            session = Session(cycleId = cycleId, date = editDate, title = "Snatch Wave"),
            blocks = listOf(
                BlockInsert(
                    block = SessionBlock(
                        sessionId = 0L, position = 0, name = snatch.name,
                        kind = BlockKind.STRENGTH, mainExerciseId = snatch.id, subBlock = "Snatch Wave"
                    ),
                    sets = listOf(BlockSet(blockId = 0L, position = 0, reps = 3, weight = 35.0)),
                    newRepMaxes = listOf(
                        RepMax(exerciseId = snatch.id, reps = 3, weight = 35.0, date = editDate)
                    )
                )
            )
        )
        val original3RmId = fakeDb.repMaxDao().getAllOnce()
            .first { it.sessionId == editedSessionId && it.reps == 3 }.id

        // Edit 1: Reweight 3RM (35.0 -> 37.5) and add a new 1RM (40.0)
        val saved = repository.sessionById(editedSessionId)!!
        repository.updateSession(
            session = saved.session,
            blocks = listOf(
                BlockInsert(
                    block = saved.blocks[0].block.copy(id = 0L),
                    sets = listOf(BlockSet(blockId = 0L, position = 0, reps = 3, weight = 37.5)),
                    newRepMaxes = listOf(
                        RepMax(exerciseId = snatch.id, reps = 3, weight = 37.5, date = editDate),
                        RepMax(exerciseId = snatch.id, reps = 1, weight = 40.0, date = editDate)
                    )
                )
            ),
            reconcileRepMaxes = true
        )

        val afterEdit1Rms = fakeDb.repMaxDao().getAllOnce().filter { it.sessionId == editedSessionId }
        assertEquals(2, afterEdit1Rms.size)
        val updated3Rm = afterEdit1Rms.first { it.reps == 3 }
        assertEquals(original3RmId, updated3Rm.id)
        assertEquals(37.5, updated3Rm.weight, 0.001)
        assertEquals(37.5, fakeDb.repMaxDao().bestWeight(snatch.id, 3) ?: 0.0, 0.001)
        assertEquals(40.0, fakeDb.repMaxDao().bestWeight(snatch.id, 1) ?: 0.0, 0.001)
        assertTrue(fakeDb.repMaxDao().getAllOnce().none { it.sessionId == editedSessionId && it.weight == 35.0 })

        // Edit 2: Remove 3RM from the edited session -> best 3RM falls back to prior session (32.5 kg)
        val afterEdit1 = repository.sessionById(editedSessionId)!!
        repository.updateSession(
            session = afterEdit1.session,
            blocks = listOf(
                BlockInsert(
                    block = afterEdit1.blocks[0].block.copy(id = 0L),
                    sets = listOf(BlockSet(blockId = 0L, position = 0, reps = 1, weight = 40.0)),
                    newRepMaxes = listOf(
                        RepMax(exerciseId = snatch.id, reps = 1, weight = 40.0, date = editDate)
                    )
                )
            ),
            reconcileRepMaxes = true
        )
        val afterEdit2Rms = fakeDb.repMaxDao().getAllOnce().filter { it.sessionId == editedSessionId }
        assertEquals(1, afterEdit2Rms.size)
        assertEquals(1, afterEdit2Rms[0].reps)
        assertEquals(32.5, fakeDb.repMaxDao().bestWeight(snatch.id, 3) ?: 0.0, 0.001)
    }

    @Test
    fun `mergeAvailableBlockTrackedPrs appends slots while preserving rb_v2 and notes`() = runTest {
        val snatch = repository.getOrCreateExercise("Snatch")
        val blockId = repository.saveRoutineWithBlocks(
            Routine(name = "Snatch Complex", description = "Technical pulls", defaultFormat = AVAILABLE_BLOCK_FORMAT),
            listOf(
                RoutineBlock(
                    name = "Snatch", kind = BlockKind.STRENGTH, setsCount = 3,
                    exerciseIdsCsv = snatch.id.toString(),
                    notes = "[rb_v2=REPS:FIXED:3:3.3.3]\n[ab_prs=Snatch:3]\nTechnical pulls"
                )
            )
        )

        repository.mergeAvailableBlockTrackedPrs(
            blockId = blockId,
            additionalSlots = listOf(TrackedPrSlot(snatch.id, snatch.name, 1))
        )

        val updated = repository.getAllRoutinesWithBlocksOnce().first { it.routine.id == blockId }
        assertTrue(RoutineTargetCodec.hasStructuredHeader(updated.blocks[0].notes))
        val decoded = AvailableBlockCodec.fromRoutineWithBlocks(updated, repository.getAllExercisesOnce())
        assertEquals(listOf(3, 1), decoded.trackedPrs.map { it.rmReps })
        assertEquals("Technical pulls", decoded.instructions)
    }

    @Test
    fun `downloadUserData deletes local rep_max on remote tombstone without resurrecting deleted exercises`() = runTest {
        val snatch = repository.getOrCreateExercise("Snatch")
        val date = LocalDate.of(2026, 11, 14)
        repository.recordRepMax(exerciseId = snatch.id, reps = 3, weight = 35.0, date = date, cycleId = null)
        assertEquals(1, fakeDb.repMaxDao().getAllOnce().size)

        UserCloudSyncManager.documentReaderForTesting = { uid, col ->
            if (uid != "ab-sync-user") emptyList()
            else when (col) {
                "rep_maxes" -> listOf(
                    // Older active entry vs newer tombstone on same natural key -> tombstone must win
                    mapOf(
                        "exerciseName" to "Snatch",
                        "reps" to 3,
                        "weight" to 35.0,
                        "date" to "2026-11-14",
                        "updatedAtMillis" to 1000L
                    ),
                    mapOf(
                        "exerciseName" to "Snatch",
                        "reps" to 3,
                        "weight" to 35.0,
                        "date" to "2026-11-14",
                        "updatedAtMillis" to 2000L,
                        "deletedAtMillis" to 2000L
                    ),
                    // Replacement PR recorded after edit
                    mapOf(
                        "exerciseName" to "Snatch",
                        "reps" to 3,
                        "weight" to 37.5,
                        "date" to "2026-11-14",
                        "updatedAtMillis" to 2500L,
                        "deletedAtMillis" to 0L
                    ),
                    // Tombstone for an exercise that does not exist locally -> must NOT resurrect exercise
                    mapOf(
                        "exerciseName" to "Deleted Ghost Lift",
                        "reps" to 1,
                        "weight" to 100.0,
                        "date" to "2026-11-14",
                        "updatedAtMillis" to 2000L,
                        "deletedAtMillis" to 2000L
                    )
                )
                else -> emptyList()
            }
        }

        val syncRes = UserCloudSyncManager.downloadUserData(repository, importUnmarkedRemoteRecords = false)
        assertTrue(syncRes.isSuccess)

        val localRms = fakeDb.repMaxDao().getAllOnce()
        assertEquals(1, localRms.size)
        assertEquals(37.5, localRms[0].weight, 0.001)
        assertTrue(repository.getAllExercisesOnce().none { it.name == "Deleted Ghost Lift" })
    }

    @Test
    fun `updateSession reconcile with managedRepMaxIds preserves PRs on legacy blocks the editor never surfaced`() = runTest {
        val cycleId = repository.saveCycle(
            Cycle(name = "Strength", startDate = LocalDate.of(2026, 11, 1), isActive = true)
        )
        val snatch = repository.getOrCreateExercise("Snatch")
        val frontSquat = repository.getOrCreateExercise("Front Squat")
        val date = LocalDate.of(2026, 11, 14)

        val sessionId = repository.saveSession(
            session = Session(cycleId = cycleId, date = date, title = "Mixed Day"),
            blocks = listOf(
                BlockInsert(
                    block = SessionBlock(
                        sessionId = 0L, position = 0, name = snatch.name,
                        kind = BlockKind.STRENGTH, mainExerciseId = snatch.id, subBlock = "Snatch Wave"
                    ),
                    sets = listOf(BlockSet(blockId = 0L, position = 0, reps = 3, weight = 35.0)),
                    newRepMaxes = listOf(RepMax(exerciseId = snatch.id, reps = 3, weight = 35.0, date = date))
                ),
                BlockInsert(
                    block = SessionBlock(
                        sessionId = 0L, position = 1, name = frontSquat.name,
                        kind = BlockKind.STRENGTH, mainExerciseId = frontSquat.id
                    ),
                    sets = listOf(
                        BlockSet(blockId = 0L, position = 0, reps = 5, weight = 60.0, isWarmup = true),
                        BlockSet(blockId = 0L, position = 1, reps = 5, weight = 100.0)
                    ),
                    newRepMaxes = listOf(RepMax(exerciseId = frontSquat.id, reps = 5, weight = 100.0, date = date))
                )
            )
        )
        val priorRms = fakeDb.repMaxDao().getAllOnce().filter { it.sessionId == sessionId }
        val snatchRmId = priorRms.first { it.exerciseId == snatch.id }.id
        val frontSquatRmId = priorRms.first { it.exerciseId == frontSquat.id }.id

        // Simplified card only manages the Snatch PR; the legacy Front Squat block re-submits no PR.
        val saved = repository.sessionById(sessionId)!!
        val sortedBlocks = saved.blocks.sortedBy { it.block.position }
        repository.updateSession(
            session = saved.session,
            blocks = listOf(
                BlockInsert(
                    block = sortedBlocks[0].block.copy(id = 0L),
                    sets = listOf(BlockSet(blockId = 0L, position = 0, reps = 3, weight = 37.5)),
                    newRepMaxes = listOf(RepMax(exerciseId = snatch.id, reps = 3, weight = 37.5, date = date))
                ),
                BlockInsert(
                    block = sortedBlocks[1].block.copy(id = 0L),
                    sets = sortedBlocks[1].sets.map { it.copy(id = 0L, blockId = 0L) }
                )
            ),
            reconcileRepMaxes = true,
            managedRepMaxIds = setOf(snatchRmId)
        )

        val afterRms = fakeDb.repMaxDao().getAllOnce().filter { it.sessionId == sessionId }
        assertEquals(2, afterRms.size)
        val snatchRm = afterRms.first { it.exerciseId == snatch.id }
        assertEquals(snatchRmId, snatchRm.id)
        assertEquals(37.5, snatchRm.weight, 0.001)
        val frontSquatRm = afterRms.first { it.exerciseId == frontSquat.id }
        assertEquals(frontSquatRmId, frontSquatRm.id)
        assertEquals(100.0, frontSquatRm.weight, 0.001)
        val newFrontSquatBlockId = repository.sessionById(sessionId)!!.blocks
            .first { it.block.mainExerciseId == frontSquat.id }.block.id
        assertEquals(newFrontSquatBlockId, frontSquatRm.blockId)
    }
}
