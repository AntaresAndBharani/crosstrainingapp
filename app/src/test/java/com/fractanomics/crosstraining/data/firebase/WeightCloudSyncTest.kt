package com.fractanomics.crosstraining.data.firebase
 
import com.fractanomics.crosstraining.data.FakeSampleAppDatabase
import com.fractanomics.crosstraining.data.FakeTransactionRunner
import com.fractanomics.crosstraining.data.Repository
import com.fractanomics.crosstraining.data.model.WeightEntry
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
 * Comprehensive Unit & Integration Tests for Issue #505:
 * [Subtask #501.3] Cloud Synchronization with Tombstone-Aware Merge & Security Rule Documentation.
 *
 * Verifies all Given/When/Then scenarios:
 * 1. Tombstone-Aware Multi-Device Merge (Last-Write-Wins on maxOf(updatedAtMillis, deletedAtMillis ?: 0L)).
 * 2. Tombstone-Inclusive Overwrite Guard (all-tombstone local set is not considered empty and publishes to Firestore).
 * 3. Dual-Read Emptiness Predicates & Legacy Swap (weightList included in isNewUidEmpty, hasLegacyData, and legacyWeight reassigned).
 * 4. 90-Day Pre-Flight Tombstone Purge (purges tombstones older than 90 days before upload while preserving active entries and recent tombstones).
 */
@OptIn(ExperimentalCoroutinesApi::class)
class WeightCloudSyncTest {
 
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
             AuthUser(uid = "test-uid-505", email = "athlete@example.com", isAnonymous = false)
         )
         UserCloudSyncManager.authUidProviderForTesting = { "test-uid-505" }
 
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
     // Scenario 1: Tombstone-Aware Multi-Device Merge (Last-Write-Wins)
     // =========================================================================
 
     @Test
     fun downloadUserData_resolvesCollisionsByHigherEffectiveTimestamp_andWritesWinningTombstones() = runTest {
         val date1 = LocalDate.of(2026, 9, 1)
         val date2 = LocalDate.of(2026, 9, 2)
         val date3 = LocalDate.of(2026, 9, 3)
 
         // Local:
         // date1: local active entry updatedAt = 1000L
         // date2: local tombstone deletedAt = 3000L, updatedAt = 3000L
         // date3: local active entry updatedAt = 5000L
         db.weightDao().upsert(
             WeightEntry(date = date1, weightKg = 80.0, notes = "Local active", updatedAtMillis = 1000L, deletedAtMillis = null)
         )
         db.weightDao().upsert(
             WeightEntry(date = date2, weightKg = 81.0, notes = "Local tombstone", updatedAtMillis = 3000L, deletedAtMillis = 3000L)
         )
         db.weightDao().upsert(
             WeightEntry(date = date3, weightKg = 82.0, notes = "Local newer active", updatedAtMillis = 5000L, deletedAtMillis = null)
         )
 
         // Remote:
         // date1: remote tombstone deletedAt = 2000L, updatedAt = 2000L -> remote wins (2000 > 1000)
         // date2: remote active entry updatedAt = 4000L -> remote wins (4000 > 3000)
         // date3: remote older tombstone deletedAt = 4000L -> local wins (5000 > 4000)
        val remoteWeightList: List<Map<String, Any>> = listOf(
            mapOf(
                "date" to date1.toEpochDay(),
                "weightKg" to 80.0,
                "notes" to "Remote tombstone winning",
                "updatedAtMillis" to 2000L,
                "deletedAtMillis" to 2000L
            ),
            mapOf(
                "date" to date2.toEpochDay(),
                "weightKg" to 81.5,
                "notes" to "Remote active winning",
                "updatedAtMillis" to 4000L
            ),
            mapOf(
                "date" to date3.toEpochDay(),
                "weightKg" to 82.0,
                "notes" to "Remote older tombstone losing",
                "updatedAtMillis" to 4000L,
                "deletedAtMillis" to 4000L
            )
        )

        UserCloudSyncManager.documentReaderForTesting = { uid: String, collection: String ->
            if (uid == "test-uid-505" && collection == "weight_entries") {
                remoteWeightList
            } else {
                emptyList()
            }
        }
 
         val result = UserCloudSyncManager.downloadUserData(repo)
         assertTrue("Download must succeed", result.isSuccess)
 
         val entries = db.weightDao().getAllEntriesIncludingTombstones().associateBy { it.date }
 
         // date1: Winning tombstone is written to Room preventing resurrection of deleted weigh-in
         val e1 = entries[date1]
         assertNotNull("date1 entry must exist", e1)
         assertEquals("date1 must have winning tombstone deletedAtMillis = 2000L", 2000L, e1?.deletedAtMillis)
 
         // date2: Winning active entry is written to Room
         val e2 = entries[date2]
         assertNotNull("date2 entry must exist", e2)
         assertEquals(81.5, e2!!.weightKg, 0.001)
         assertNull("date2 must be active (deletedAtMillis = null)", e2.deletedAtMillis)
         assertEquals(4000L, e2.updatedAtMillis)
 
         // date3: Winning local active entry is preserved without loss
         val e3 = entries[date3]
         assertNotNull("date3 entry must exist", e3)
         assertEquals(82.0, e3!!.weightKg, 0.001)
         assertNull("date3 must remain active", e3.deletedAtMillis)
         assertEquals(5000L, e3.updatedAtMillis)
     }
 
     // =========================================================================
     // Scenario 2: Tombstone-Inclusive Overwrite Guard
     // =========================================================================
 
     @Test
     fun uploadUserData_evaluatesOverwriteGuardWithTombstoneInclusivePayload_andPublishesTombstones() = runTest {
        // Given an athlete has soft-deleted all weight entries producing an all-tombstone local list
        val date = LocalDate.of(2026, 9, 5)
        val recentTs = System.currentTimeMillis() - 1000L
        db.weightDao().upsert(
            WeightEntry(date = date, weightKg = 75.0, notes = "", updatedAtMillis = recentTs, deletedAtMillis = recentTs)
        )
 
         // Remote currently has populated data
         remoteCollectionsState["weight_entries"] = true
 
         // Active entries are empty, but tombstone entries are present
         assertTrue(repo.getActiveWeightEntriesOnce().isEmpty())
         assertEquals(1, repo.getAllWeightEntriesIncludingTombstones().size)
 
         // When uploadUserData executes
         val result = UserCloudSyncManager.uploadUserData(repo)
         assertTrue("Upload must succeed", result.isSuccess)
 
         // Then isLocallyEmpty is computed from tombstone-inclusive payload (payload.isEmpty() == false)
         // and tombstones are successfully published to Firestore without being blocked by overwrite guard
         assertTrue("weight_entries must be written", writtenCollections.containsKey("weight_entries"))
         @Suppress("UNCHECKED_CAST")
         val writtenList = writtenCollections["weight_entries"]?.get("list") as? List<Map<String, Any?>>
         assertNotNull("Payload must be present", writtenList)
         assertEquals(1, writtenList!!.size)
         assertEquals(date.toEpochDay(), (writtenList[0]["date"] as Number).toLong())
         assertEquals(recentTs, (writtenList[0]["deletedAtMillis"] as Number).toLong())
     }
 
     // =========================================================================
     // Scenario 3: Dual-Read Emptiness Predicates & Legacy Swap
     // =========================================================================
 
     @Test
     fun downloadUserData_includesWeightInEmptinessPredicates_andPerformsLegacySwap() = runTest {
         val legacyEmail = "athlete@example.com"
         val user = AuthUser(uid = "new-empty-uid", email = legacyEmail, isAnonymous = false)
         UserCloudSyncManager.setAuthenticatedUser(user)
         UserCloudSyncManager.authUidProviderForTesting = { "new-empty-uid" }
 
         val legacyDate = LocalDate.of(2026, 8, 20)
         val legacyWeightEntries: List<Map<String, Any>> = listOf(
             mapOf(
                 "date" to legacyDate.toEpochDay(),
                 "weightKg" to 84.0,
                 "notes" to "Legacy weight log",
                 "updatedAtMillis" to 1500L
             )
         )
 
         // Remote setup: new UID has NO data for any collection including weight_entries
         // Legacy email has ONLY weight_entries
         var legacyQueried = false
         UserCloudSyncManager.documentReaderForTesting = { targetUid: String, collectionName: String ->
             when (targetUid) {
                 "new-empty-uid" -> emptyList()
                 legacyEmail -> {
                     legacyQueried = true
                     if (collectionName == "weight_entries") legacyWeightEntries else emptyList()
                 }
                 else -> emptyList()
             }
         }
 
         val result = UserCloudSyncManager.downloadUserData(repo)
         assertTrue("downloadUserData must succeed", result.isSuccess)
         assertTrue("Legacy document must be queried via dual-read", legacyQueried)
 
         // Legacy weight entries must be saved to Room
         val localEntries = repo.getAllWeightEntriesIncludingTombstones()
         assertEquals(1, localEntries.size)
         assertEquals(legacyDate, localEntries[0].date)
         assertEquals(84.0, localEntries[0].weightKg, 0.001)
 
         // And re-uploaded to new UID
         assertTrue("Legacy data must be re-uploaded to newUid", writtenCollections.containsKey("weight_entries"))
     }
 
     @Test
     fun downloadUserData_doesNotQueryLegacy_whenNewUidHasOnlyWeightEntries() = runTest {
         val legacyEmail = "athlete@example.com"
         val user = AuthUser(uid = "new-uid-with-weight", email = legacyEmail, isAnonymous = false)
         UserCloudSyncManager.setAuthenticatedUser(user)
         UserCloudSyncManager.authUidProviderForTesting = { "new-uid-with-weight" }
 
         val newDate = LocalDate.of(2026, 9, 6)
         val newWeightEntries: List<Map<String, Any>> = listOf(
             mapOf(
                 "date" to newDate.toEpochDay(),
                 "weightKg" to 79.5,
                 "notes" to "Fresh UID weigh-in",
                 "updatedAtMillis" to 6000L
             )
         )
 
         var legacyQueried = false
         UserCloudSyncManager.documentReaderForTesting = { targetUid: String, collectionName: String ->
             when (targetUid) {
                 "new-uid-with-weight" -> {
                     if (collectionName == "weight_entries") newWeightEntries else emptyList()
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
         assertFalse("Legacy path must NOT be queried when newUid has weight entries", legacyQueried)
 
         val localEntries = repo.getAllWeightEntriesIncludingTombstones()
         assertEquals(1, localEntries.size)
         assertEquals(newDate, localEntries[0].date)
         assertEquals(79.5, localEntries[0].weightKg, 0.001)
     }
 
     // =========================================================================
     // Scenario 4: 90-Day Pre-Flight Tombstone Purge
     // =========================================================================
 
     @Test
     fun uploadUserData_purgesTombstonesOlderThan90Days_duringPreFlight() = runTest {
         val now = System.currentTimeMillis()
         val dayMillis = 24L * 60 * 60 * 1000L
 
         val oldTombstoneDate = LocalDate.of(2026, 5, 1)
         val recentTombstoneDate = LocalDate.of(2026, 9, 1)
         val activeDate = LocalDate.of(2026, 9, 7)
 
         // 1. Tombstone 100 days old (should be purged)
         db.weightDao().upsert(
             WeightEntry(date = oldTombstoneDate, weightKg = 85.0, notes = "Old", updatedAtMillis = now - 100 * dayMillis, deletedAtMillis = now - 100 * dayMillis)
         )
         // 2. Tombstone 10 days old (should remain)
         db.weightDao().upsert(
             WeightEntry(date = recentTombstoneDate, weightKg = 83.0, notes = "Recent", updatedAtMillis = now - 10 * dayMillis, deletedAtMillis = now - 10 * dayMillis)
         )
         // 3. Active entry (should remain)
         db.weightDao().upsert(
             WeightEntry(date = activeDate, weightKg = 81.0, notes = "Active", updatedAtMillis = now - 1 * dayMillis, deletedAtMillis = null)
         )
 
         assertEquals(3, db.weightDao().getAllEntriesIncludingTombstones().size)
 
         // When uploadUserData executes pre-flight
         val result = UserCloudSyncManager.uploadUserData(repo)
         assertTrue("Upload must succeed", result.isSuccess)
 
         // Then old tombstone is deleted from Room
         val remaining = db.weightDao().getAllEntriesIncludingTombstones().associateBy { it.date }
         assertEquals(2, remaining.size)
         assertFalse("100-day-old tombstone must be purged", remaining.containsKey(oldTombstoneDate))
         assertTrue("Recent tombstone must remain intact", remaining.containsKey(recentTombstoneDate))
         assertTrue("Active entry must remain intact", remaining.containsKey(activeDate))
 
         // And uploaded payload contains only the 2 remaining entries
         @Suppress("UNCHECKED_CAST")
         val uploadedList = writtenCollections["weight_entries"]?.get("list") as? List<Map<String, Any?>>
         assertNotNull("Payload must be written", uploadedList)
         assertEquals(2, uploadedList!!.size)
     }
}
