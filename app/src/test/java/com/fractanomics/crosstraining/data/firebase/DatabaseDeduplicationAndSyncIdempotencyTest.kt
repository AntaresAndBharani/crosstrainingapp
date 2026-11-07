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
import com.fractanomics.crosstraining.data.model.CycleType
import com.fractanomics.crosstraining.data.model.Exercise
import com.fractanomics.crosstraining.data.model.ExerciseCategory
import com.fractanomics.crosstraining.data.model.MetricType
import com.fractanomics.crosstraining.data.model.RepMax
import com.fractanomics.crosstraining.data.model.Routine
import com.fractanomics.crosstraining.data.model.RoutineBlock
import com.fractanomics.crosstraining.data.model.Session
import com.fractanomics.crosstraining.data.model.SessionBlock
import com.fractanomics.crosstraining.ui.AppViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.delay
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.time.LocalDate

@OptIn(ExperimentalCoroutinesApi::class)
class DatabaseDeduplicationAndSyncIdempotencyTest {

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
            AuthUser(uid = "sync-dedup-user-1", email = "pv.joseangel@gmail.com", isAnonymous = false)
        )
        UserCloudSyncManager.authUidProviderForTesting = { "sync-dedup-user-1" }
    }

    @After
    fun tearDown() {
        UserCloudSyncManager.resetTestHandlers()
        UserCloudSyncManager.setAuthenticatedUser(null)
        Dispatchers.resetMain()
    }

    @Test
    fun cleanupDuplicateData_reduces64DuplicatedCycleGoalsTo2UniqueGoals_andPreservesChipFormatting() = runTest {
        val cycleId = db.cycleDao().insert(
            Cycle(
                name = "Recomposition",
                startDate = LocalDate.of(2026, 9, 28),
                endDate = LocalDate.of(2026, 12, 28),
                type = CycleType.FAT_LOSS_BODYBUILDING,
                isActive = true
            )
        )
        val frontSquatId = db.exerciseDao().insert(
            Exercise(name = "Front Squat", category = ExerciseCategory.BARBELL, metricType = MetricType.WEIGHT)
        )
        val snatchId = db.exerciseDao().insert(
            Exercise(name = "Snatch", category = ExerciseCategory.BARBELL, metricType = MetricType.WEIGHT)
        )

        // Directly insert 64 interleaved duplicate cycle goals (32 Front Squat 3RM + 32 Snatch 2RM)
        // reproducing the exact state shown in the Training Cycles screenshot.
        repeat(32) {
            db.cycleGoalDao().insert(
                CycleGoal(
                    cycleId = cycleId,
                    exerciseId = frontSquatId,
                    targetReps = 3,
                    startWeight = 50.0,
                    targetWeight = 65.0
                )
            )
            db.cycleGoalDao().insert(
                CycleGoal(
                    cycleId = cycleId,
                    exerciseId = snatchId,
                    targetReps = 2,
                    startWeight = 0.0,
                    targetWeight = 40.0
                )
            )
        }

        assertEquals(64, repo.snapshotCycleGoals().size)

        // When cleanupDuplicateData runs
        repo.cleanupDuplicateData()

        // Then only the 2 real unique goals remain in original creation order
        val remainingGoals = repo.snapshotCycleGoals().sortedBy { it.id }
        assertEquals(2, remainingGoals.size)
        assertEquals(1L, remainingGoals[0].id)
        assertEquals(frontSquatId, remainingGoals[0].exerciseId)
        assertEquals("Front Squat (3RM): 50 → 65kg", remainingGoals[0].formatSummaryChip("Front Squat"))

        assertEquals(2L, remainingGoals[1].id)
        assertEquals(snatchId, remainingGoals[1].exerciseId)
        assertEquals("Snatch (2RM): 40kg", remainingGoals[1].formatSummaryChip("Snatch"))
    }

    @Test
    fun cleanupDuplicateData_deduplicatesExercisesRoutinesCyclesSessionsAndRepMaxes_andRemapsForeignKeys() = runTest {
        // 1. Duplicate exercises with case/whitespace differences
        val exPrimaryId = db.exerciseDao().insert(Exercise(name = "Front Squat", category = ExerciseCategory.BARBELL))
        val exDupId = db.exerciseDao().insert(Exercise(name = "  front squat ", category = ExerciseCategory.BARBELL))

        // 2. Duplicate cycles
        val start = LocalDate.of(2026, 9, 28)
        val end = LocalDate.of(2026, 12, 28)
        val cycleInactiveDupId = db.cycleDao().insert(
            Cycle(name = "Recomposition", startDate = start, endDate = end, type = CycleType.FAT_LOSS_BODYBUILDING, isActive = false)
        )
        val cycleActivePrimaryId = db.cycleDao().insert(
            Cycle(name = " recomposition ", startDate = start, endDate = end, type = CycleType.FAT_LOSS_BODYBUILDING, isActive = true)
        )

        // 3. Duplicate routines where child references duplicate exercise
        val routineEmptyDupId = db.routineDao().insert(Routine(name = "Monday Strength", mainExerciseId = exDupId))
        val routinePrimaryId = db.routineDao().insert(Routine(name = "  monday strength ", mainExerciseId = exDupId))
        db.routineDao().insertBlocks(
            listOf(
                RoutineBlock(
                    routineId = routinePrimaryId,
                    position = 0,
                    name = "Front Squat 5x3",
                    kind = BlockKind.STRENGTH,
                    exerciseIdsCsv = "$exDupId,$exPrimaryId"
                )
            )
        )

        // 4. Cycle goals referencing duplicate cycle and duplicate exercise
        db.cycleGoalDao().insert(
            CycleGoal(cycleId = cycleInactiveDupId, exerciseId = exDupId, targetReps = 3, startWeight = 50.0, targetWeight = 65.0)
        )
        db.cycleGoalDao().insert(
            CycleGoal(cycleId = cycleActivePrimaryId, exerciseId = exPrimaryId, targetReps = 3, startWeight = 50.0, targetWeight = 65.0)
        )

        // 5. Duplicate sessions referencing duplicate cycle, duplicate routine, and duplicate exercise
        val sessionDate = LocalDate.of(2026, 10, 5)
        val session1Id = repo.saveSession(
            Session(cycleId = cycleInactiveDupId, date = sessionDate, title = "Monday Session"),
            listOf(
                BlockInsert(
                    block = SessionBlock(
                        sessionId = 0,
                        position = 0,
                        name = "Front Squat",
                        kind = BlockKind.STRENGTH,
                        mainExerciseId = exDupId,
                        routineId = routineEmptyDupId,
                        exerciseIdsCsv = "$exDupId"
                    ),
                    sets = listOf(BlockSet(blockId = 0, position = 0, reps = 3, weight = 60.0))
                )
            )
        )
        val session2Id = repo.saveSession(
            Session(cycleId = cycleActivePrimaryId, date = sessionDate, title = "  monday session "),
            listOf(
                BlockInsert(
                    block = SessionBlock(
                        sessionId = 0,
                        position = 0,
                        name = "Front Squat",
                        kind = BlockKind.STRENGTH,
                        mainExerciseId = exPrimaryId,
                        routineId = routinePrimaryId,
                        exerciseIdsCsv = "$exPrimaryId"
                    ),
                    sets = listOf(BlockSet(blockId = 0, position = 0, reps = 3, weight = 60.0))
                )
            )
        )

        // 6. Duplicate rep maxes referencing duplicate exercise, cycle, and session
        db.repMaxDao().insert(
            RepMax(exerciseId = exDupId, reps = 3, weight = 60.0, date = sessionDate, sessionId = session2Id, cycleId = cycleInactiveDupId)
        )
        db.repMaxDao().insert(
            RepMax(exerciseId = exPrimaryId, reps = 3, weight = 60.001, date = sessionDate, sessionId = null, cycleId = null)
        )

        // Execute full deduplication
        repo.cleanupDuplicateData()

        // Verify exercises deduplicated & kept lowest ID
        val exercises = repo.getAllExercisesOnce()
        assertEquals(1, exercises.size)
        assertEquals(exPrimaryId, exercises[0].id)

        // Verify cycles deduplicated & kept active cycle
        val cycles = repo.exportSnapshot().cycles
        assertEquals(1, cycles.size)
        assertEquals(cycleActivePrimaryId, cycles[0].id)

        // Verify routines deduplicated & kept routine with blocks, remapping mainExerciseId and exerciseIdsCsv
        val routines = repo.getAllRoutinesWithBlocksOnce()
        assertEquals(1, routines.size)
        assertEquals(routinePrimaryId, routines[0].routine.id)
        assertEquals(exPrimaryId, routines[0].routine.mainExerciseId)
        assertEquals(exPrimaryId.toString(), routines[0].blocks[0].exerciseIdsCsv)

        // Verify cycle goals deduplicated and remapped to primary cycle and exercise
        val goals = repo.snapshotCycleGoals()
        assertEquals(1, goals.size)
        assertEquals(cycleActivePrimaryId, goals[0].cycleId)
        assertEquals(exPrimaryId, goals[0].exerciseId)

        // Verify sessions deduplicated and foreign keys remapped without cascade-losing sets
        val sessions = repo.getAllSessionsWithBlocksOnce()
        assertEquals(1, sessions.size)
        assertEquals(session1Id, sessions[0].session.id)
        assertEquals(cycleActivePrimaryId, sessions[0].session.cycleId)
        assertEquals(exPrimaryId, sessions[0].blocks[0].block.mainExerciseId)
        assertEquals(routinePrimaryId, sessions[0].blocks[0].block.routineId)
        assertEquals(exPrimaryId.toString(), sessions[0].blocks[0].block.exerciseIdsCsv)
        assertEquals(1, sessions[0].blocks[0].sets.size)

        // Verify rep maxes deduplicated and remapped to primary exercise, cycle, and session
        val repMaxes = repo.exportSnapshot().repMaxes
        assertEquals(1, repMaxes.size)
        assertEquals(exPrimaryId, repMaxes[0].exerciseId)
        assertEquals(cycleActivePrimaryId, repMaxes[0].cycleId)
        assertEquals(session1Id, repMaxes[0].sessionId)
    }

    @Test
    fun triggerCloudSync_tenConsecutiveTimes_neverDuplicatesAnyEntities_andHeals64PollutedRemoteGoals() = runTest {
        val cycleId = db.cycleDao().insert(
            Cycle(
                name = "Recomposition",
                startDate = LocalDate.of(2026, 9, 28),
                endDate = LocalDate.of(2026, 12, 28),
                type = CycleType.FAT_LOSS_BODYBUILDING,
                isActive = true
            )
        )
        val frontSquatId = db.exerciseDao().insert(Exercise(name = "Front Squat", category = ExerciseCategory.BARBELL))
        val snatchId = db.exerciseDao().insert(Exercise(name = "Snatch", category = ExerciseCategory.BARBELL))

        // Pollute local DB with 64 duplicate goals
        repeat(32) {
            db.cycleGoalDao().insert(
                CycleGoal(cycleId = cycleId, exerciseId = frontSquatId, targetReps = 3, startWeight = 50.0, targetWeight = 65.0)
            )
            db.cycleGoalDao().insert(
                CycleGoal(cycleId = cycleId, exerciseId = snatchId, targetReps = 2, startWeight = 0.0, targetWeight = 40.0)
            )
        }
        repo.saveRoutineWithBlocks(Routine(name = "Full Body A", mainExerciseId = frontSquatId), emptyList())
        repo.saveSession(
            Session(cycleId = cycleId, date = LocalDate.of(2026, 10, 1), title = "Workout 1"),
            listOf(
                BlockInsert(
                    block = SessionBlock(sessionId = 0, position = 0, name = "Front Squat", kind = BlockKind.STRENGTH, mainExerciseId = frontSquatId),
                    sets = listOf(BlockSet(blockId = 0, position = 0, reps = 3, weight = 55.0))
                )
            )
        )
        repo.recordRepMax(frontSquatId, 3, 55.0, LocalDate.of(2026, 10, 1), cycleId)

        // Simulate remote Firestore initially polluted with 64 duplicate cycle_goals
        val remoteStore = mutableMapOf<String, List<Map<String, Any>>>()
        val pollutedRemoteGoals = mutableListOf<Map<String, Any>>()
        repeat(32) { idx ->
            pollutedRemoteGoals.add(
                mapOf(
                    "id" to (idx * 2 + 1).toLong(),
                    "cycleId" to cycleId,
                    "exerciseId" to frontSquatId,
                    "exerciseName" to "Front Squat",
                    "targetReps" to 3,
                    "startWeight" to 50.0,
                    "targetWeight" to 65.0,
                    "notes" to ""
                )
            )
            pollutedRemoteGoals.add(
                mapOf(
                    "id" to (idx * 2 + 2).toLong(),
                    "cycleId" to cycleId,
                    "exerciseId" to snatchId,
                    "exerciseName" to "Snatch",
                    "targetReps" to 2,
                    "startWeight" to 0.0,
                    "targetWeight" to 40.0,
                    "notes" to ""
                )
            )
        }
        remoteStore["cycle_goals"] = pollutedRemoteGoals

        UserCloudSyncManager.documentReaderForTesting = { _, col ->
            remoteStore[col] ?: emptyList()
        }
        UserCloudSyncManager.documentWriterForTesting = { col, data ->
            @Suppress("UNCHECKED_CAST")
            val list = data["list"] as? List<Map<String, Any>> ?: emptyList()
            remoteStore[col] = list
        }

        val dataMode = DataModeManager(context = null)
        dataMode.setRepositoryForTesting(repo)
        val viewModel = AppViewModel(dataMode)
        advanceUntilIdle()

        // Press "Sync Now" 10 consecutive times AND also simulate sign-in downloadUserData
        repeat(10) {
            var syncSuccess = false
            viewModel.triggerCloudSync { res ->
                syncSuccess = res.isSuccess
            }
            advanceUntilIdle()
            assertTrue("Sync iteration $it must succeed", syncSuccess)

            // Also run full downloadUserData (as happens on sign-in) to verify full-import idempotency
            assertTrue(UserCloudSyncManager.downloadUserData(repo, importUnmarkedRemoteRecords = true).isSuccess)
        }

        // Verify local database has zero duplicates
        assertEquals(2, repo.getAllExercisesOnce().size)
        assertEquals(1, repo.getAllRoutinesWithBlocksOnce().size)
        assertEquals(1, repo.getAllSessionsWithBlocksOnce().size)
        assertEquals(2, repo.snapshotCycleGoals().size)
        assertEquals(1, repo.exportSnapshot().repMaxes.size)

        // Verify remote Firestore was healed from 64 polluted goals down to 2 clean goals
        assertEquals(2, remoteStore["cycle_goals"]?.size)
        assertEquals("Front Squat", remoteStore["cycle_goals"]?.get(0)?.get("exerciseName"))
        assertEquals("Recomposition", remoteStore["cycle_goals"]?.get(0)?.get("cycleName"))
        assertEquals("Snatch", remoteStore["cycle_goals"]?.get(1)?.get("exerciseName"))
    }

    @Test
    fun downloadUserData_resolvesCrossDeviceCycleIdMismatchAndExerciseName_withoutDuplicatingGoals() = runTest {
        val localCycleId = repo.saveCycle(
            Cycle(name = "Recomposition", startDate = LocalDate.of(2026, 9, 28), isActive = true)
        )
        val localFrontSquat = repo.getOrCreateExercise("Front Squat")
        val existingGoalId = repo.saveCycleGoal(
            CycleGoal(
                cycleId = localCycleId,
                exerciseId = localFrontSquat.id,
                targetReps = 3,
                startWeight = 50.0,
                targetWeight = 60.0
            )
        )

        // Remote goal comes from another device where cycleId was 999 and exerciseId was 777
        UserCloudSyncManager.documentReaderForTesting = { _, col ->
            if (col == "cycle_goals") {
                listOf(
                    mapOf(
                        "id" to 42L,
                        "cycleId" to 999L,
                        "cycleName" to "Recomposition",
                        "exerciseId" to 777L,
                        "exerciseName" to "Front Squat",
                        "targetReps" to 3,
                        "startWeight" to 50.0,
                        "targetWeight" to 65.0,
                        "notes" to "Updated target"
                    )
                )
            } else {
                emptyList()
            }
        }

        assertTrue(UserCloudSyncManager.downloadUserData(repo, importUnmarkedRemoteRecords = true).isSuccess)

        val goals = repo.snapshotCycleGoals()
        assertEquals("Must update existing local goal in place despite remote cycleId/exerciseId mismatch", 1, goals.size)
        assertEquals(existingGoalId, goals[0].id)
        assertEquals(localCycleId, goals[0].cycleId)
        assertEquals(localFrontSquat.id, goals[0].exerciseId)
        assertEquals(65.0, goals[0].targetWeight, 0.01)
    }

    @Test
    fun repositoryUpsertGuards_preventDuplicatesWhenIdIsZero() = runTest {
        val cycleId = repo.saveCycle(Cycle(name = "Strength Phase", startDate = LocalDate.of(2026, 11, 1), isActive = true))
        val ex1 = repo.saveExercise(Exercise(id = 0, name = "Snatch"))
        val ex2 = repo.saveExercise(Exercise(id = 0, name = "  snatch  "))
        assertEquals("saveExercise with id=0 must reuse existing exercise by normalized name", ex1, ex2)
        assertEquals(1, repo.getAllExercisesOnce().size)

        // Quick-add of an existing name (bare Exercise(name = ...)) must not reset its configuration
        val rowId = repo.saveExercise(
            Exercise(id = 0, name = "Row", category = ExerciseCategory.MACHINE, metricType = MetricType.CALORIES, unit = "cal", tracksRepMax = false)
        )
        assertEquals(rowId, repo.saveExercise(Exercise(name = "row")))
        val row = repo.getAllExercisesOnce().single { it.id == rowId }
        assertEquals(ExerciseCategory.MACHINE, row.category)
        assertEquals(MetricType.CALORIES, row.metricType)
        assertEquals("cal", row.unit)

        val r1 = repo.saveRoutine(Routine(id = 0, name = "Heavy Day"))
        val r2 = repo.saveRoutine(Routine(id = 0, name = " heavy day "))
        assertEquals("saveRoutine with id=0 must reuse existing routine by normalized name", r1, r2)
        assertEquals(1, repo.getAllRoutinesWithBlocksOnce().size)

        val g1 = repo.saveCycleGoal(CycleGoal(id = 0, cycleId = cycleId, exerciseId = ex1, targetReps = 2, targetWeight = 40.0))
        val g2 = repo.saveCycleGoal(CycleGoal(id = 0, cycleId = cycleId, exerciseId = ex1, targetReps = 2, targetWeight = 42.5))
        assertEquals("saveCycleGoal with id=0 must update existing natural key in place", g1, g2)
        assertEquals(1, repo.snapshotCycleGoals().size)
        assertEquals(42.5, repo.snapshotCycleGoals()[0].targetWeight, 0.01)

        // saveCycleWithGoals with duplicate draft goals in the same list
        repo.saveCycleWithGoals(
            Cycle(id = cycleId, name = "Strength Phase", startDate = LocalDate.of(2026, 11, 1), isActive = true),
            listOf(
                CycleGoal(cycleId = cycleId, exerciseId = ex1, targetReps = 2, targetWeight = 42.5),
                CycleGoal(cycleId = cycleId, exerciseId = ex1, targetReps = 2, targetWeight = 45.0)
            )
        )
        assertEquals(1, repo.snapshotCycleGoals().size)
        assertEquals(45.0, repo.snapshotCycleGoals()[0].targetWeight, 0.01)

        // recordRepMax and addRepMax with identical natural key
        val date = LocalDate.of(2026, 11, 7)
        val rm1 = repo.recordRepMax(ex1, 2, 40.0, date, cycleId)
        val rm2 = repo.recordRepMax(ex1, 2, 40.001, date, cycleId)
        val rm3 = repo.addRepMax(RepMax(exerciseId = ex1, reps = 2, weight = 40.0, date = date, cycleId = cycleId))
        assertEquals(rm1, rm2)
        assertEquals(rm1, rm3)
        assertEquals(1, repo.exportSnapshot().repMaxes.size)
    }

    @Test
    fun sync_preservesDistinctSameTitledSessionsOnSameDay_andCollapsesExactRemoteCopies() = runTest {
        val cycleId = repo.saveCycle(Cycle(name = "Recomposition", startDate = LocalDate.of(2026, 9, 28), isActive = true))
        val squat = repo.getOrCreateExercise("Back Squat")
        val date = LocalDate.of(2026, 10, 6)
        fun wod(blockName: String, reps: Int, weight: Double) = listOf(
            BlockInsert(
                block = SessionBlock(sessionId = 0, position = 0, name = blockName, kind = BlockKind.STRENGTH, mainExerciseId = squat.id),
                sets = listOf(BlockSet(blockId = 0, position = 0, reps = reps, weight = weight))
            )
        )
        repo.saveSession(Session(cycleId = cycleId, date = date, title = "WOD"), wod("AM Squat", 5, 80.0))
        repo.saveSession(Session(cycleId = cycleId, date = date, title = "WOD"), wod("PM Squat", 3, 90.0))

        val remoteStore = mutableMapOf<String, List<Map<String, Any>>>()
        UserCloudSyncManager.documentReaderForTesting = { _, col -> remoteStore[col] ?: emptyList() }
        UserCloudSyncManager.documentWriterForTesting = { col, data ->
            @Suppress("UNCHECKED_CAST")
            remoteStore[col] = data["list"] as? List<Map<String, Any>> ?: emptyList()
        }

        // Upload must not drop the second same-titled session from the whole-list cloud backup
        assertTrue(UserCloudSyncManager.uploadUserData(repo).isSuccess)
        assertEquals(2, remoteStore["sessions"]?.size)

        // Restoring on a fresh device must bring back both distinct sessions, idempotently
        val freshDb = FakeSampleAppDatabase()
        val freshRepo = Repository(freshDb, FakeTransactionRunner(freshDb))
        repeat(3) {
            assertTrue(UserCloudSyncManager.downloadUserData(freshRepo, importUnmarkedRemoteRecords = true).isSuccess)
        }
        val restored = freshRepo.getAllSessionsWithBlocksOnce()
        assertEquals(2, restored.size)
        assertEquals(
            setOf("AM Squat", "PM Squat"),
            restored.map { it.blocks.single().block.name }.toSet()
        )

        // Exact remote copies of one session (differing only by ids) must collapse to a single local row
        val original = remoteStore["sessions"]!!.first()
        @Suppress("UNCHECKED_CAST")
        val copies = (1..3).map { n ->
            original + ("session" to ((original["session"] as Map<String, Any>) + ("id" to 100L + n)))
        }
        remoteStore["sessions"] = copies
        val copyDb = FakeSampleAppDatabase()
        val copyRepo = Repository(copyDb, FakeTransactionRunner(copyDb))
        assertTrue(UserCloudSyncManager.downloadUserData(copyRepo, importUnmarkedRemoteRecords = true).isSuccess)
        assertEquals(1, copyRepo.getAllSessionsWithBlocksOnce().size)
    }

    @Test
    fun concurrentSyncCalls_areSerializedByMutex_andNeverDuplicateRecords() = runTest {
        val cycleId = repo.saveCycle(Cycle(name = "Recomposition", startDate = LocalDate.of(2026, 9, 28), isActive = true))
        val ex = repo.getOrCreateExercise("Front Squat")

        UserCloudSyncManager.documentReaderForTesting = { _, col ->
            delay(25)
            if (col == "cycle_goals") {
                listOf(
                    mapOf(
                        "id" to 1L,
                        "cycleId" to cycleId,
                        "cycleName" to "Recomposition",
                        "exerciseId" to ex.id,
                        "exerciseName" to "Front Squat",
                        "targetReps" to 3,
                        "startWeight" to 50.0,
                        "targetWeight" to 65.0
                    )
                )
            } else {
                emptyList()
            }
        }
        UserCloudSyncManager.documentWriterForTesting = { _, _ ->
            delay(25)
        }

        val jobs = (1..5).flatMap {
            listOf(
                async { UserCloudSyncManager.downloadUserData(repo, importUnmarkedRemoteRecords = true) },
                async { UserCloudSyncManager.uploadUserData(repo) }
            )
        }
        val results = jobs.awaitAll()
        assertTrue(results.all { it.isSuccess })
        assertEquals(1, repo.snapshotCycleGoals().size)
    }
}
