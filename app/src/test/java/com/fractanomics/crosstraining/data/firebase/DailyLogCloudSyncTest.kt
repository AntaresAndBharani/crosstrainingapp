package com.fractanomics.crosstraining.data.firebase

import com.fractanomics.crosstraining.data.BlockInsert
import com.fractanomics.crosstraining.data.FakeSampleAppDatabase
import com.fractanomics.crosstraining.data.FakeTransactionRunner
import com.fractanomics.crosstraining.data.Repository
import com.fractanomics.crosstraining.data.model.BlockKind
import com.fractanomics.crosstraining.data.model.BlockSet
import com.fractanomics.crosstraining.data.model.Cycle
import com.fractanomics.crosstraining.data.model.CycleType
import com.fractanomics.crosstraining.data.model.DailyLog
import com.fractanomics.crosstraining.data.model.Session
import com.fractanomics.crosstraining.data.model.SessionBlock
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
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
 * Comprehensive Unit Tests for Issue #568:
 * feat: Cloud Synchronization for Daily Logs & Session Block Completion (Pattern B - Slice 5):
 *
 * Verifies:
 * 1. SessionBlock.isCompleted cloud serialization and deserialization (absent -> true on download).
 * 2. Daily logs collection upload with all nutritional and fasting fields.
 * 3. Daily logs download with Last-Write-Wins (LWW) tombstone reconciliation.
 * 4. Tombstone-inclusive overwrite guard for daily logs.
 * 5. 90-day pre-flight tombstone purge for daily logs.
 * 6. Dual-read legacy migration inclusion for daily logs.
 * 7. Verification that Cycle bitmasks and cycle entities remain local-only.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class DailyLogCloudSyncTest {

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
            AuthUser(uid = "test-uid-568", email = "athlete@example.com", isAnonymous = false)
        )
        UserCloudSyncManager.authUidProviderForTesting = { "test-uid-568" }

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

    // =========================================================================
    // 1. SessionBlock.isCompleted Cloud Serialization & Absent -> True Download
    // =========================================================================

    @Test
    fun uploadUserData_serializesSessionBlockIsCompletedField() = runTest {
        val date = LocalDate.of(2026, 10, 1)
        val session = Session(id = 0L, cycleId = 1L, date = date, title = "Fat Loss Circuit", notes = "")

        val completedBlock = SessionBlock(
            id = 0L,
            sessionId = 0L,
            position = 0,
            name = "Warmup",
            kind = BlockKind.CARDIO,
            isCompleted = true
        )
        val uncompletedBlock = SessionBlock(
            id = 0L,
            sessionId = 0L,
            position = 1,
            name = "HIIT Finisher",
            kind = BlockKind.CARDIO,
            isCompleted = false
        )

        val set1 = BlockSet(id = 0L, blockId = 0L, position = 0, reps = 10)
        val set2 = BlockSet(id = 0L, blockId = 0L, position = 0, reps = 15)

        repo.saveSession(
            session,
            listOf(
                BlockInsert(block = completedBlock, sets = listOf(set1)),
                BlockInsert(block = uncompletedBlock, sets = listOf(set2))
            )
        )

        val uploadResult = UserCloudSyncManager.uploadUserData(repo)
        assertTrue("Upload must succeed", uploadResult.isSuccess)

        @Suppress("UNCHECKED_CAST")
        val sessionList = writtenCollections["sessions"]?.get("list") as? List<Map<String, Any>>
        assertNotNull("sessions collection must be written", sessionList)
        assertEquals(1, sessionList!!.size)

        @Suppress("UNCHECKED_CAST")
        val blocks = sessionList[0]["blocks"] as? List<Map<String, Any>>
        assertNotNull("blocks must be written", blocks)
        assertEquals(2, blocks!!.size)

        @Suppress("UNCHECKED_CAST")
        val b0 = blocks[0]["block"] as Map<String, Any>
        @Suppress("UNCHECKED_CAST")
        val b1 = blocks[1]["block"] as Map<String, Any>

        assertEquals(true, b0["isCompleted"])
        assertEquals(false, b1["isCompleted"])
    }

    @Test
    fun downloadUserData_defaultsIsCompletedToTrue_whenFieldIsAbsentFromCloud() = runTest {
        val date = LocalDate.of(2026, 10, 2)

        // Remote payload simulating legacy cloud data without isCompleted,
        // and modern cloud data with explicit isCompleted = false and isCompleted = true
        val remoteSessions: List<Map<String, Any>> = listOf(
            mapOf(
                "session" to mapOf(
                    "id" to 1L,
                    "cycleId" to 1L,
                    "date" to date.toString(),
                    "title" to "Mixed Sessions",
                    "notes" to ""
                ),
                "blocks" to listOf(
                    mapOf(
                        "block" to mapOf(
                            "id" to 1L,
                            "sessionId" to 1L,
                            "position" to 0,
                            "name" to "Legacy Block Without isCompleted",
                            "kind" to "STRENGTH"
                            // isCompleted is absent
                        ),
                        "sets" to emptyList<Map<String, Any>>()
                    ),
                    mapOf(
                        "block" to mapOf(
                            "id" to 2L,
                            "sessionId" to 1L,
                            "position" to 1,
                            "name" to "Explicitly Uncompleted Block",
                            "kind" to "CARDIO",
                            "isCompleted" to false
                        ),
                        "sets" to emptyList<Map<String, Any>>()
                    ),
                    mapOf(
                        "block" to mapOf(
                            "id" to 3L,
                            "sessionId" to 1L,
                            "position" to 2,
                            "name" to "Explicitly Completed Block",
                            "kind" to "WEIGHTLIFTING",
                            "isCompleted" to true
                        ),
                        "sets" to emptyList<Map<String, Any>>()
                    )
                )
            )
        )

        UserCloudSyncManager.documentReaderForTesting = { uid, collection ->
            if (uid == "test-uid-568" && collection == "sessions") remoteSessions else emptyList()
        }

        val result = UserCloudSyncManager.downloadUserData(repo)
        assertTrue("Download must succeed", result.isSuccess)

        val savedSessions = repo.getAllSessionsWithBlocksOnce()
        assertEquals(1, savedSessions.size)
        val blocks = savedSessions[0].blocks.sortedBy { it.block.position }
        assertEquals(3, blocks.size)

        // Block 0: Absent -> true
        assertEquals("Legacy Block Without isCompleted", blocks[0].block.name)
        assertTrue("Absent isCompleted must default to true on download", blocks[0].block.isCompleted)

        // Block 1: Explicit false -> false
        assertEquals("Explicitly Uncompleted Block", blocks[1].block.name)
        assertFalse("Explicit false must download as false", blocks[1].block.isCompleted)

        // Block 2: Explicit true -> true
        assertEquals("Explicitly Completed Block", blocks[2].block.name)
        assertTrue("Explicit true must download as true", blocks[2].block.isCompleted)
    }

    // =========================================================================
    // 2. Daily Logs Upload Serialization
    // =========================================================================

    @Test
    fun uploadUserData_serializesDailyLogsCollectionCorrectly() = runTest {
        val date = LocalDate.of(2026, 10, 3)
        val now = System.currentTimeMillis()

        val dailyLog = DailyLog(
            date = date,
            fastCompleted = true,
            isRestDay = false,
            caloriesKcal = 2150,
            proteinGrams = 175,
            carbsGrams = 190,
            fatGrams = 65,
            notes = "Clean day, 18h fast",
            updatedAtMillis = now,
            deletedAtMillis = null
        )
        repo.saveDailyLog(dailyLog)

        val result = UserCloudSyncManager.uploadUserData(repo)
        assertTrue("Upload must succeed", result.isSuccess)

        assertTrue("daily_logs collection must be uploaded", writtenCollections.containsKey("daily_logs"))
        @Suppress("UNCHECKED_CAST")
        val dailyLogsList = writtenCollections["daily_logs"]?.get("list") as? List<Map<String, Any?>>
        assertNotNull("Payload must be present", dailyLogsList)
        assertEquals(1, dailyLogsList!!.size)

        val payload = dailyLogsList[0]
        assertEquals(date.toEpochDay(), (payload["date"] as Number).toLong())
        assertEquals(true, payload["fastCompleted"])
        assertEquals(false, payload["isRestDay"])
        assertEquals(2150, (payload["caloriesKcal"] as Number).toInt())
        assertEquals(175, (payload["proteinGrams"] as Number).toInt())
        assertEquals(190, (payload["carbsGrams"] as Number).toInt())
        assertEquals(65, (payload["fatGrams"] as Number).toInt())
        assertEquals("Clean day, 18h fast", payload["notes"])
        assertEquals(now, (payload["updatedAtMillis"] as Number).toLong())
        assertNull(payload["deletedAtMillis"])
    }

    // =========================================================================
    // 3. Daily Logs LWW Tombstone Conflict Resolution on Download
    // =========================================================================

    @Test
    fun downloadUserData_reconcilesDailyLogsWithLastWriteWins() = runTest {
        val date1 = LocalDate.of(2026, 10, 1)
        val date2 = LocalDate.of(2026, 10, 2)
        val date3 = LocalDate.of(2026, 10, 3)
        val date4 = LocalDate.of(2026, 10, 4)

        // Local state:
        // date1: local active entry updatedAt = 1000L
        // date2: local tombstone deletedAt = 3000L, updatedAt = 3000L
        // date3: local active entry updatedAt = 5000L
        // date4: nonexistent locally
        repo.saveDailyLog(
            DailyLog(date = date1, fastCompleted = true, notes = "Local active", updatedAtMillis = 1000L, deletedAtMillis = null)
        )
        repo.saveDailyLog(
            DailyLog(date = date2, fastCompleted = false, notes = "Local deleted", updatedAtMillis = 3000L, deletedAtMillis = 3000L)
        )
        repo.saveDailyLog(
            DailyLog(date = date3, fastCompleted = true, caloriesKcal = 2000, notes = "Local newer active", updatedAtMillis = 5000L, deletedAtMillis = null)
        )

        // Remote state:
        // date1: remote tombstone deletedAt = 2000L -> remote wins (2000 > 1000)
        // date2: remote active entry updatedAt = 4000L -> remote wins (4000 > 3000)
        // date3: remote older tombstone deletedAt = 4000L -> local wins (5000 > 4000)
        // date4: fresh remote entry updatedAt = 2500L -> remote inserted
        val remoteList: List<Map<String, Any>> = listOf(
            mapOf(
                "date" to date1.toEpochDay(),
                "fastCompleted" to false,
                "notes" to "Remote winning tombstone",
                "updatedAtMillis" to 2000L,
                "deletedAtMillis" to 2000L
            ),
            mapOf(
                "date" to date2.toEpochDay(),
                "fastCompleted" to true,
                "caloriesKcal" to 1950,
                "notes" to "Remote winning active",
                "updatedAtMillis" to 4000L
            ),
            mapOf(
                "date" to date3.toEpochDay(),
                "notes" to "Remote losing tombstone",
                "updatedAtMillis" to 4000L,
                "deletedAtMillis" to 4000L
            ),
            mapOf(
                "date" to date4.toEpochDay(),
                "fastCompleted" to true,
                "isRestDay" to true,
                "notes" to "New remote log",
                "updatedAtMillis" to 2500L
            )
        )

        UserCloudSyncManager.documentReaderForTesting = { uid, collection ->
            if (uid == "test-uid-568" && collection == "daily_logs") remoteList else emptyList()
        }

        val result = UserCloudSyncManager.downloadUserData(repo)
        assertTrue("Download must succeed", result.isSuccess)

        val localLogs = repo.getAllDailyLogsIncludingTombstones().associateBy { it.date }

        // date1: Remote tombstone wins and soft-deletes local
        val l1 = localLogs[date1]
        assertNotNull("date1 must exist", l1)
        assertEquals("date1 must have winning tombstone", 2000L, l1?.deletedAtMillis)

        // date2: Remote active entry wins and restores local
        val l2 = localLogs[date2]
        assertNotNull("date2 must exist", l2)
        assertNull("date2 must be active", l2?.deletedAtMillis)
        assertEquals(true, l2?.fastCompleted)
        assertEquals(1950, l2?.caloriesKcal)
        assertEquals(4000L, l2?.updatedAtMillis)

        // date3: Local newer active entry is preserved without resurrection or overwrite
        val l3 = localLogs[date3]
        assertNotNull("date3 must exist", l3)
        assertNull("date3 must remain active", l3?.deletedAtMillis)
        assertEquals("Local newer active", l3?.notes)
        assertEquals(5000L, l3?.updatedAtMillis)

        // date4: Fresh remote entry is inserted
        val l4 = localLogs[date4]
        assertNotNull("date4 must exist", l4)
        assertEquals(true, l4?.fastCompleted)
        assertEquals(true, l4?.isRestDay)
        assertEquals(2500L, l4?.updatedAtMillis)
    }

    // =========================================================================
    // 4. Overwrite Guard with Tombstone-Inclusive Daily Logs
    // =========================================================================

    @Test
    fun uploadUserData_evaluatesOverwriteGuardWithTombstoneInclusiveDailyLogs_andPublishes() = runTest {
        val date = LocalDate.of(2026, 10, 5)
        val recentTs = System.currentTimeMillis() - 500L

        // Local has ONLY a tombstone daily log
        repo.saveDailyLog(
            DailyLog(date = date, notes = "Soft deleted log", updatedAtMillis = recentTs, deletedAtMillis = recentTs)
        )

        remoteCollectionsState["daily_logs"] = true

        // Active daily logs is empty, but tombstone exists
        assertTrue(repo.getActiveDailyLogsOnce().isEmpty())
        assertEquals(1, repo.getAllDailyLogsIncludingTombstones().size)

        val result = UserCloudSyncManager.uploadUserData(repo)
        assertTrue("Upload must succeed", result.isSuccess)

        // Overwrite guard must NOT block upload since tombstone payload is not empty
        assertTrue("daily_logs must be published to cloud", writtenCollections.containsKey("daily_logs"))
        @Suppress("UNCHECKED_CAST")
        val writtenList = writtenCollections["daily_logs"]?.get("list") as? List<Map<String, Any?>>
        assertNotNull(writtenList)
        assertEquals(1, writtenList!!.size)
        assertEquals(date.toEpochDay(), (writtenList[0]["date"] as Number).toLong())
        assertEquals(recentTs, (writtenList[0]["deletedAtMillis"] as Number).toLong())
    }

    // =========================================================================
    // 5. 90-Day Pre-Flight Tombstone Purge for Daily Logs
    // =========================================================================

    @Test
    fun uploadUserData_purgesDailyLogTombstonesOlderThan90Days() = runTest {
        val now = System.currentTimeMillis()
        val dayMillis = 24L * 60 * 60 * 1000L

        val oldTombstoneDate = LocalDate.of(2026, 5, 10)
        val recentTombstoneDate = LocalDate.of(2026, 9, 25)
        val activeDate = LocalDate.of(2026, 10, 5)

        // 1. Tombstone 100 days old (should be purged)
        repo.saveDailyLog(
            DailyLog(date = oldTombstoneDate, notes = "Old", updatedAtMillis = now - 100 * dayMillis, deletedAtMillis = now - 100 * dayMillis)
        )
        // 2. Tombstone 10 days old (should remain)
        repo.saveDailyLog(
            DailyLog(date = recentTombstoneDate, notes = "Recent", updatedAtMillis = now - 10 * dayMillis, deletedAtMillis = now - 10 * dayMillis)
        )
        // 3. Active log (should remain)
        repo.saveDailyLog(
            DailyLog(date = activeDate, fastCompleted = true, notes = "Active", updatedAtMillis = now - 1 * dayMillis, deletedAtMillis = null)
        )

        assertEquals(3, repo.getAllDailyLogsIncludingTombstones().size)

        val result = UserCloudSyncManager.uploadUserData(repo)
        assertTrue("Upload must succeed", result.isSuccess)

        val remaining = repo.getAllDailyLogsIncludingTombstones().associateBy { it.date }
        assertEquals(2, remaining.size)
        assertFalse("100-day-old daily log tombstone must be purged", remaining.containsKey(oldTombstoneDate))
        assertTrue("Recent daily log tombstone must remain", remaining.containsKey(recentTombstoneDate))
        assertTrue("Active daily log must remain", remaining.containsKey(activeDate))

        @Suppress("UNCHECKED_CAST")
        val uploadedList = writtenCollections["daily_logs"]?.get("list") as? List<Map<String, Any?>>
        assertNotNull(uploadedList)
        assertEquals(2, uploadedList!!.size)
    }

    // =========================================================================
    // 6. Dual-Read Legacy Migration Inclusion
    // =========================================================================

    @Test
    fun downloadUserData_migratesDailyLogsFromLegacyDocument_whenNewUidIsEmpty() = runTest {
        val legacyEmail = "athlete@example.com"
        val user = AuthUser(uid = "new-empty-user-uid", email = legacyEmail, isAnonymous = false)
        UserCloudSyncManager.setAuthenticatedUser(user)
        UserCloudSyncManager.authUidProviderForTesting = { "new-empty-user-uid" }

        val legacyDate = LocalDate.of(2026, 8, 15)
        val legacyDailyLogs: List<Map<String, Any>> = listOf(
            mapOf(
                "date" to legacyDate.toEpochDay(),
                "fastCompleted" to true,
                "isRestDay" to false,
                "caloriesKcal" to 2200,
                "notes" to "Migrated log from legacy",
                "updatedAtMillis" to 12345L
            )
        )

        var legacyQueried = false
        UserCloudSyncManager.documentReaderForTesting = { targetUid, collectionName ->
            when (targetUid) {
                "new-empty-user-uid" -> emptyList()
                legacyEmail -> {
                    legacyQueried = true
                    if (collectionName == "daily_logs") legacyDailyLogs else emptyList()
                }
                else -> emptyList()
            }
        }

        val result = UserCloudSyncManager.downloadUserData(repo)
        assertTrue("downloadUserData must succeed", result.isSuccess)
        assertTrue("Legacy email doc must be queried", legacyQueried)

        val localLogs = repo.getAllDailyLogsIncludingTombstones()
        assertEquals(1, localLogs.size)
        assertEquals(legacyDate, localLogs[0].date)
        assertEquals(true, localLogs[0].fastCompleted)
        assertEquals(2200, localLogs[0].caloriesKcal)

        // And re-uploaded to new UID
        assertTrue("daily_logs must be re-uploaded to new UID", writtenCollections.containsKey("daily_logs"))
    }

    @Test
    fun downloadUserData_doesNotQueryLegacy_whenNewUidHasOnlyDailyLogs() = runTest {
        val legacyEmail = "athlete@example.com"
        val user = AuthUser(uid = "uid-with-daily-logs", email = legacyEmail, isAnonymous = false)
        UserCloudSyncManager.setAuthenticatedUser(user)
        UserCloudSyncManager.authUidProviderForTesting = { "uid-with-daily-logs" }

        val newDate = LocalDate.of(2026, 10, 6)
        val newDailyLogs: List<Map<String, Any>> = listOf(
            mapOf(
                "date" to newDate.toEpochDay(),
                "fastCompleted" to true,
                "updatedAtMillis" to 5000L
            )
        )

        var legacyQueried = false
        UserCloudSyncManager.documentReaderForTesting = { targetUid, collectionName ->
            when (targetUid) {
                "uid-with-daily-logs" -> {
                    if (collectionName == "daily_logs") newDailyLogs else emptyList()
                }
                legacyEmail -> {
                    legacyQueried = true
                    emptyList()
                }
                else -> emptyList()
            }
        }

        val result = UserCloudSyncManager.downloadUserData(repo)
        assertTrue("downloadUserData must succeed", result.isSuccess)
        assertFalse("Legacy path must NOT be queried when newUid has daily logs", legacyQueried)

        val localLogs = repo.getAllDailyLogsIncludingTombstones()
        assertEquals(1, localLogs.size)
        assertEquals(newDate, localLogs[0].date)
        assertEquals(true, localLogs[0].fastCompleted)
    }

    // =========================================================================
    // 7. Cycle Bitmask Fields Are Strictly Local-Only
    // =========================================================================

    @Test
    fun uploadUserData_doesNotUploadCyclesCollection_andDeclaresBitmasksLocalOnly() = runTest {
        // Save cycle with fastDaysOfWeek and restDaysOfWeek bitmasks
        val cycle = Cycle(
            id = 0L,
            name = "Fat Loss Cycle 1",
            startDate = LocalDate.of(2026, 10, 1),
            endDate = LocalDate.of(2026, 11, 1),
            type = CycleType.FAT_LOSS_BODYBUILDING,
            fastDaysOfWeek = 9,   // Mon (1) + Thu (8)
            restDaysOfWeek = 64,  // Sun (64)
            isActive = true
        )
        repo.saveCycle(cycle)

        val result = UserCloudSyncManager.uploadUserData(repo)
        assertTrue("Upload must succeed", result.isSuccess)

        // Cycles collection is never uploaded to Firestore
        assertFalse("cycles collection must not be uploaded", writtenCollections.containsKey("cycles"))

        // Cycle bitmasks must not appear in any written collection payload
        for ((name, data) in writtenCollections) {
            val serialized = data.toString()
            assertFalse("Collection $name must not leak fastDaysOfWeek", serialized.contains("fastDaysOfWeek"))
            assertFalse("Collection $name must not leak restDaysOfWeek", serialized.contains("restDaysOfWeek"))
        }
    }
}
