package com.fractanomics.crosstraining.data

import com.fractanomics.crosstraining.data.firebase.AuthUser
import com.fractanomics.crosstraining.data.firebase.UserCloudSyncManager
import com.fractanomics.crosstraining.util.AvailableBlock
import com.fractanomics.crosstraining.util.AvailableBlockCodec
import com.fractanomics.crosstraining.util.AvailableBlockExercise
import com.fractanomics.crosstraining.util.DAILY_ROUTINE_FORMAT
import com.fractanomics.crosstraining.util.DailyRoutine
import com.fractanomics.crosstraining.util.DailyRoutineCodec
import com.fractanomics.crosstraining.util.DailyRoutineItem
import com.fractanomics.crosstraining.util.TrackedPrSlot
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class DailyRoutineRepositoryAndSyncTest {

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
            AuthUser(uid = "dr-sync-user", email = "athlete@crosstraining.app", isAnonymous = false)
        )
        UserCloudSyncManager.authUidProviderForTesting = { "dr-sync-user" }
    }

    @After
    fun tearDown() {
        UserCloudSyncManager.resetTestHandlers()
        UserCloudSyncManager.setAuthenticatedUser(null)
    }

    @Test
    fun `AvailableBlock and DailyRoutine sharing the same title do not overwrite each other`() = runTest {
        val clean = repository.getOrCreateExercise("Clean")
        val squat = repository.getOrCreateExercise("Back Squat")

        val blockId = repository.saveAvailableBlock(
            AvailableBlock(
                id = 0L,
                title = "Monday Clean",
                instructions = "3x3 Clean",
                exercises = listOf(AvailableBlockExercise(clean.id, clean.name, true)),
                trackedPrs = emptyList()
            )
        )
        val routineId = repository.saveDailyRoutine(
            DailyRoutine(
                id = 0L,
                title = "Monday Clean",
                notes = "Main clean + squat",
                items = listOf(
                    DailyRoutineItem.BlockRef(blockId, "Monday Clean"),
                    DailyRoutineItem.StandaloneExercise(squat.id, squat.name, "4x8", emptyList(), squat)
                )
            )
        )

        val allRoutines = repository.getAllRoutinesWithBlocksOnce()
        assertEquals(2, allRoutines.size)
        val abRow = allRoutines.first { it.routine.id == blockId }
        val drRow = allRoutines.first { it.routine.id == routineId }
        assertTrue(!DailyRoutineCodec.isDailyRoutine(abRow))
        assertTrue(DailyRoutineCodec.isDailyRoutine(drRow))

        // Cleanup duplicate data preserves both namespaces
        repository.cleanupDuplicateData()
        assertEquals(2, repository.getAllRoutinesWithBlocksOnce().size)

        // Template update replaces only items while keeping title/notes
        repository.updateDailyRoutineItems(
            routineId = routineId,
            items = listOf(
                DailyRoutineItem.BlockRef(blockId, "Monday Clean")
            )
        )
        val updatedDr = repository.getAllRoutinesWithBlocksOnce().first { it.routine.id == routineId }
        assertEquals("Main clean + squat", updatedDr.routine.description)
        assertEquals(1, updatedDr.blocks.size)
    }

    @Test
    fun `downloadUserData pre-creates DR_EXERCISE movements and isolates DailyRoutine tombstones`() = runTest {
        val clean = repository.getOrCreateExercise("Clean")
        val blockId = repository.saveAvailableBlock(
            AvailableBlock(
                id = 0L,
                title = "Monday Session",
                instructions = "Complex",
                exercises = listOf(AvailableBlockExercise(clean.id, clean.name, true)),
                trackedPrs = emptyList()
            )
        )
        assertTrue(blockId > 0L)

        UserCloudSyncManager.documentReaderForTesting = { uid, col ->
            if (uid != "dr-sync-user") emptyList()
            else when (col) {
                "routines" -> listOf(
                    mapOf(
                        "routine" to mapOf(
                            "id" to 88L,
                            "name" to "Monday Session",
                            "description" to "Full day routine",
                            "defaultFormat" to DAILY_ROUTINE_FORMAT,
                            "updatedAtMillis" to 2000L
                        ),
                        "blocks" to listOf(
                            mapOf(
                                "name" to "Monday Session",
                                "kind" to "COMPLEX",
                                "format" to "DR_BLOCK",
                                "position" to 0,
                                "notes" to DailyRoutineCodec.encodeBlockItemHeader(999L, "Monday Session"),
                                "subBlock" to "Monday Session"
                            ),
                            mapOf(
                                "name" to "Bulgarian Split Squat",
                                "kind" to "STRENGTH",
                                "format" to "DR_EXERCISE",
                                "position" to 1,
                                "notes" to "${DailyRoutineCodec.encodeExerciseItemHeader(500L, "Bulgarian Split Squat")}\n[ab_prs=Bulgarian+Split+Squat:5]\n3x10 each leg"
                            )
                        )
                    )
                )
                else -> emptyList()
            }
        }

        val res = UserCloudSyncManager.downloadUserData(repository, importUnmarkedRemoteRecords = false)
        assertTrue(res.isSuccess)

        val splitSquat = repository.getAllExercisesOnce().firstOrNull { it.name == "Bulgarian Split Squat" }
        assertNotNull(splitSquat)

        val allRwbs = repository.getAllRoutinesWithBlocksOnce()
        val blocks = allRwbs.filterNot { DailyRoutineCodec.isDailyRoutine(it) }
            .map { AvailableBlockCodec.fromRoutineWithBlocks(it, repository.getAllExercisesOnce()) }
        val routines = allRwbs.filter { DailyRoutineCodec.isDailyRoutine(it) }
            .map { DailyRoutineCodec.fromRoutineWithBlocks(it, blocks, repository.getAllExercisesOnce()) }

        assertEquals(1, blocks.size)
        assertEquals(1, routines.size)
        assertEquals(2, routines[0].items.size)
        val firstBlock = routines[0].items[0] as DailyRoutineItem.BlockRef
        assertEquals(blockId, firstBlock.blockId)
        val secondEx = routines[0].items[1] as DailyRoutineItem.StandaloneExercise
        assertEquals(splitSquat!!.id, secondEx.exerciseId)
        assertEquals("3x10 each leg", secondEx.note)
        assertEquals(listOf(TrackedPrSlot(splitSquat.id, splitSquat.name, 5)), secondEx.defaultTrackedPrs)
    }
}
