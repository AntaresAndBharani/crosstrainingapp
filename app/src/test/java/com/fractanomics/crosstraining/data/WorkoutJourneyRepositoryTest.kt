package com.fractanomics.crosstraining.data

import com.fractanomics.crosstraining.data.ai.WorkoutEntityResolver
import com.fractanomics.crosstraining.data.dao.BlockDao
import com.fractanomics.crosstraining.data.dao.CycleDao
import com.fractanomics.crosstraining.data.dao.CycleGoalDao
import com.fractanomics.crosstraining.data.dao.ExerciseDao
import com.fractanomics.crosstraining.data.dao.RepMaxDao
import com.fractanomics.crosstraining.data.dao.RoutineDao
import com.fractanomics.crosstraining.data.dao.SessionDao
import com.fractanomics.crosstraining.data.dao.WeightDao
import com.fractanomics.crosstraining.data.model.BlockKind
import com.fractanomics.crosstraining.data.model.BlockSet
import com.fractanomics.crosstraining.data.model.BlockWithSets
import com.fractanomics.crosstraining.data.model.Cycle
import com.fractanomics.crosstraining.data.model.CycleGoal
import com.fractanomics.crosstraining.data.model.Exercise
import com.fractanomics.crosstraining.data.model.ExerciseCategory
import com.fractanomics.crosstraining.data.model.MetricType
import com.fractanomics.crosstraining.data.model.RepMax
import com.fractanomics.crosstraining.data.model.Routine
import com.fractanomics.crosstraining.data.model.RoutineBlock
import com.fractanomics.crosstraining.data.model.RoutineWithBlocks
import com.fractanomics.crosstraining.data.model.Session
import com.fractanomics.crosstraining.data.model.SessionBlock
import com.fractanomics.crosstraining.data.model.SessionWithBlocks
import com.fractanomics.crosstraining.data.model.WeightEntry
import com.fractanomics.crosstraining.util.ParsedDocumentBlock
import com.fractanomics.crosstraining.util.ParsedDocumentSet
import com.fractanomics.crosstraining.util.ParsedWorkoutDocument
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Before
import org.junit.Test
import java.time.LocalDate

/**
 * Unit & integration tests for [Repository.persistWorkoutJourney] adhering to Issue #516 acceptance criteria:
 * - Scenario 3: Atomic Ingestion Transaction (commit and rollback)
 * - Composite Complex isolation and linkage (mainExerciseId and exerciseIdsCsv)
 * - In-transaction exercise deduplication across multiple blocks
 */
class WorkoutJourneyRepositoryTest {

    private lateinit var fakeDb: FakeTestAppDatabase
    private lateinit var transactionRunner: FakeTestTransactionRunner
    private lateinit var repository: Repository

    private val sessionDate = LocalDate.of(2026, 9, 8)

    @Before
    fun setUp() {
        fakeDb = FakeTestAppDatabase()
        fakeDb.populateSampleData()
        transactionRunner = FakeTestTransactionRunner(fakeDb)
        repository = Repository(
            db = fakeDb,
            transactionRunner = transactionRunner
        )
    }

    @Test
    fun `Scenario 3 - Atomic Ingestion Transaction commits exercises, routine template, and session blocks`() = runTest {
        // Given parsed document with complex and triset
        val doc = ParsedWorkoutDocument(
            routineTitle = "Monday: Strength & Accessories",
            isRepeatable = true,
            sections = listOf("Strengh & Power block", "Accessories block"),
            blocks = listOf(
                ParsedDocumentBlock(
                    name = "Clean + Hang Clean + Front Squat + Push to OverHead",
                    section = "Strengh & Power block",
                    kind = BlockKind.COMPLEX,
                    format = "E3MOM",
                    movements = listOf("Clean", "Hang Clean", "Front Squat", "Push to OverHead"),
                    sets = listOf(
                        ParsedDocumentSet(reps = 1, weight = 52.5),
                        ParsedDocumentSet(reps = 1, weight = 55.0)
                    )
                ),
                ParsedDocumentBlock(
                    name = "Romanian Deadlift",
                    section = "Accessories block",
                    kind = BlockKind.SUPERSET,
                    format = "E3MOM",
                    scheme = "TRISET_1",
                    sets = listOf(
                        ParsedDocumentSet(reps = 10, weight = 70.0),
                        ParsedDocumentSet(reps = 10, weight = 75.0)
                    )
                ),
                ParsedDocumentBlock(
                    name = "Pullups",
                    section = "Accessories block",
                    kind = BlockKind.SUPERSET,
                    format = "E3MOM",
                    scheme = "TRISET_1",
                    sets = listOf(
                        ParsedDocumentSet(reps = 5, weight = 0.0),
                        ParsedDocumentSet(reps = 4, weight = 0.0)
                    )
                )
            ),
            rawText = "Raw workout note text"
        )

        val resolution = repository.entityResolver.resolveDocument(doc, fakeDb.exerciseDao().getAllOnce())

        // When Repository.persistWorkoutJourney executes
        val (routine, session) = repository.persistWorkoutJourney(
            document = doc,
            resolutionResult = resolution,
            sessionDate = sessionDate,
            saveAsRoutine = true,
            logAsSession = true
        )

        // Then routine and session are created atomically
        assertNotNull(routine)
        assertNotNull(session)
        assertTrue(routine!!.id > 0)
        assertTrue(session!!.id > 0)

        // And new exercises are persisted to Room
        val rdl = fakeDb.exerciseDao().byName("Romanian Deadlift")
        assertNotNull(rdl)
        assertEquals(ExerciseCategory.BARBELL, rdl!!.category)

        val composite = fakeDb.exerciseDao().byName("Clean + Hang Clean + Front Squat + Push to OverHead")
        assertNotNull(composite)
        assertEquals(ExerciseCategory.BARBELL, composite!!.category)

        // And session blocks are created with section and format
        val sessionWithBlocks = fakeDb.sessionDao().getByIdOnce(session.id)
        assertNotNull(sessionWithBlocks)
        assertEquals(3, sessionWithBlocks!!.blocks.size)

        val block1 = sessionWithBlocks.blocks[0]
        assertEquals("Clean + Hang Clean + Front Squat + Push to OverHead", block1.block.name)
        assertEquals(composite.id, block1.block.mainExerciseId)
        assertEquals("Strengh & Power block", block1.block.section)
        assertEquals("E3MOM", block1.block.format)
        assertTrue(block1.block.exerciseIdsCsv.isNotBlank())
        assertEquals(2, block1.sets.size)
        assertEquals(52.5, block1.sets[0].weight ?: 0.0, 0.001)

        val block2 = sessionWithBlocks.blocks[1]
        assertEquals("Romanian Deadlift", block2.block.name)
        assertEquals(rdl.id, block2.block.mainExerciseId)
        assertEquals("Accessories block", block2.block.section)
        assertEquals("TRISET_1", block2.block.scheme)
        assertEquals(2, block2.sets.size)
    }

    @Test
    fun `Scenario 3 - Atomic Transaction rolls back completely on failure`() = runTest {
        val doc = ParsedWorkoutDocument(
            routineTitle = "Rollback Workout",
            blocks = listOf(
                ParsedDocumentBlock(
                    name = "New Failing Exercise",
                    sets = listOf(ParsedDocumentSet(reps = 5, weight = 100.0))
                )
            )
        )
        val resolution = repository.entityResolver.resolveDocument(doc, fakeDb.exerciseDao().getAllOnce())

        fakeDb.simulateSetInsertFailure = true

        val exercisesBefore = fakeDb.exerciseDao().getAllOnce().size
        val sessionsBefore = fakeDb.sessionDao().getAllSessionsOnce().size
        val routinesBefore = fakeDb.routineDao().getAllOnce().size

        try {
            repository.persistWorkoutJourney(
                document = doc,
                resolutionResult = resolution,
                sessionDate = sessionDate,
                saveAsRoutine = true,
                logAsSession = true
            )
            fail("Expected exception during simulated failure")
        } catch (e: Exception) {
            // Expected
        }

        // Then all writes are completely rolled back
        assertEquals(exercisesBefore, fakeDb.exerciseDao().getAllOnce().size)
        assertEquals(sessionsBefore, fakeDb.sessionDao().getAllSessionsOnce().size)
        assertEquals(routinesBefore, fakeDb.routineDao().getAllOnce().size)
    }

    @Test
    fun `deduplicates missing exercise names across multiple blocks`() = runTest {
        val doc = ParsedWorkoutDocument(
            routineTitle = "Multi-Block Deduplication",
            blocks = listOf(
                ParsedDocumentBlock(name = "DB Hammer Curl", sets = listOf(ParsedDocumentSet(reps = 10, weight = 15.0))),
                ParsedDocumentBlock(name = "db hammer curl", sets = listOf(ParsedDocumentSet(reps = 10, weight = 15.0))),
                ParsedDocumentBlock(name = "DB HAMMER CURL ", sets = listOf(ParsedDocumentSet(reps = 10, weight = 15.0)))
            )
        )
        val resolution = repository.entityResolver.resolveDocument(doc, fakeDb.exerciseDao().getAllOnce())

        val (_, session) = repository.persistWorkoutJourney(
            document = doc,
            resolutionResult = resolution,
            sessionDate = sessionDate,
            saveAsRoutine = false,
            logAsSession = true
        )

        assertNotNull(session)
        val allCurls = fakeDb.exerciseDao().getAllOnce().filter { it.name.contains("hammer curl", ignoreCase = true) }
        assertEquals(1, allCurls.size)
    }

    @Test
    fun `persistWorkoutJourney throws IllegalArgumentException when blockResolutions is empty`() = runTest {
        val doc = ParsedWorkoutDocument(
            routineTitle = "Empty Blocks Workout",
            blocks = emptyList()
        )
        val resolution = WorkoutEntityResolver.DEFAULT.resolveDocument(doc, fakeDb.exerciseDao().getAllOnce())

        try {
            repository.persistWorkoutJourney(
                document = doc,
                resolutionResult = resolution,
                sessionDate = sessionDate
            )
            fail("Expected IllegalArgumentException when blockResolutions is empty")
        } catch (e: IllegalArgumentException) {
            assertTrue(e.message?.contains("zero blocks", ignoreCase = true) == true)
        }
    }

    @Test
    fun `persistWorkoutJourney defensively filters missing exercises to only actively referenced ones`() = runTest {
        val doc = ParsedWorkoutDocument(
            routineTitle = "Active Blocks Only",
            blocks = listOf(
                ParsedDocumentBlock(name = "Active Exercise A", sets = listOf(ParsedDocumentSet(reps = 5, weight = 50.0)))
            )
        )
        val resolution = WorkoutEntityResolver.DEFAULT.resolveDocument(doc, fakeDb.exerciseDao().getAllOnce())

        // Simulate an unreferenced orphan exercise in missingExercises
        val orphanExercise = Exercise(
            id = 0L,
            name = "Orphan Exercise Unreferenced",
            category = ExerciseCategory.ACCESSORY,
            metricType = MetricType.WEIGHT
        )
        val resolutionWithOrphan = resolution.copy(
            missingExercises = resolution.missingExercises + orphanExercise
        )

        val (_, session) = repository.persistWorkoutJourney(
            document = doc,
            resolutionResult = resolutionWithOrphan,
            sessionDate = sessionDate,
            saveAsRoutine = false,
            logAsSession = true
        )

        assertNotNull(session)
        // Active exercise was persisted
        assertNotNull(fakeDb.exerciseDao().byName("Active Exercise A"))
        // Orphan exercise was NOT persisted to DB
        org.junit.Assert.assertNull(fakeDb.exerciseDao().byName("Orphan Exercise Unreferenced"))
    }

    @Test
    fun `reconcileLegacyWorkoutSubBlocks upgrades legacy Monday session and routine to normalized section and subBlocks`() = runTest {
        // Given a synthetic pre-migration Room test fixture representing the imported legacy workout:
        // Routine "Mondays" and Session "Monday: Strength & Accessories" with legacy section typo and empty subBlock
        val legacyRoutine = Routine(id = 1L, name = "Mondays", description = "Legacy import")
        val legacyRoutineBlocks = listOf(
            RoutineBlock(
                id = 1L, routineId = 1L, position = 0,
                name = "Clean + Hang Clean + Front Squat + Push to OverHead",
                kind = BlockKind.COMPLEX, format = "E3MOM", setsCount = 7, targetRepsScheme = "7x1",
                section = "Strengh & Power block", subBlock = ""
            ),
            RoutineBlock(
                id = 2L, routineId = 1L, position = 1,
                name = "Front Squats",
                kind = BlockKind.STRENGTH, format = "E3MOM", setsCount = 4, targetRepsScheme = "4x4",
                section = "Strengh & Power block", subBlock = ""
            ),
            RoutineBlock(
                id = 3L, routineId = 1L, position = 2,
                name = "Romanian Deadlift",
                kind = BlockKind.SUPERSET, format = "E3MOM", setsCount = 4, targetRepsScheme = "TRISET_1",
                section = "Accessories block", subBlock = ""
            ),
            RoutineBlock(
                id = 4L, routineId = 1L, position = 3,
                name = "Pullups",
                kind = BlockKind.SUPERSET, format = "E3MOM", setsCount = 4, targetRepsScheme = "TRISET_1",
                section = "Accessories block", subBlock = ""
            ),
            RoutineBlock(
                id = 5L, routineId = 1L, position = 4,
                name = "DB Twist Curl",
                kind = BlockKind.SUPERSET, format = "E3MOM", setsCount = 4, targetRepsScheme = "TRISET_1",
                section = "Accessories block", subBlock = ""
            ),
            RoutineBlock(
                id = 6L, routineId = 1L, position = 5,
                name = "Barbell Calves Raises",
                kind = BlockKind.SUPERSET, format = "E2,5MOM", setsCount = 4, targetRepsScheme = "TRISET_2",
                section = "Accessories block", subBlock = ""
            ),
            RoutineBlock(
                id = 7L, routineId = 1L, position = 6,
                name = "Banded Reverse Flys",
                kind = BlockKind.SUPERSET, format = "E2,5MOM", setsCount = 4, targetRepsScheme = "TRISET_2",
                section = "Accessories block", subBlock = ""
            ),
            RoutineBlock(
                id = 8L, routineId = 1L, position = 7,
                name = "SkiErg",
                kind = BlockKind.SUPERSET, format = "E2,5MOM", setsCount = 4, targetRepsScheme = "TRISET_2",
                section = "Accessories block", subBlock = ""
            )
        )
        fakeDb.routineDao().insert(legacyRoutine)
        fakeDb.routineDao().insertBlocks(legacyRoutineBlocks)

        val legacySession = Session(id = 100L, cycleId = 1L, date = sessionDate, title = "Monday: Strength & Accessories", notes = "")
        val legacySessionBlocks = legacyRoutineBlocks.mapIndexed { idx, rb ->
            BlockInsert(
                block = SessionBlock(
                    id = 1000L + idx,
                    sessionId = 100L,
                    position = idx,
                    name = rb.name,
                    kind = rb.kind,
                    format = rb.format,
                    scheme = rb.targetRepsScheme,
                    section = rb.section,
                    subBlock = rb.subBlock
                ),
                sets = listOf(BlockSet(id = 5000L + idx, blockId = 1000L + idx, position = 0, reps = 5))
            )
        }
        fakeDb.sessionDao().insertSession(legacySession)
        legacySessionBlocks.forEach { bi ->
            fakeDb.blockDao().insertBlock(bi.block)
            fakeDb.blockDao().insertSets(bi.sets)
        }

        // When reconcileLegacyWorkoutSubBlocks runs
        repository.reconcileLegacyWorkoutSubBlocks()

        // Then routine blocks are updated to normalized section and subBlock
        val updatedRoutines = fakeDb.routineDao().getAllWithBlocksOnce()
        val updatedRoutine = updatedRoutines.find { it.routine.id == 1L }!!
        val rBlocks = updatedRoutine.blocks.sortedBy { it.position }
        assertEquals(8, rBlocks.size)

        assertEquals("Strength & Power", rBlocks[0].section)
        assertEquals("E3MOM Complex", rBlocks[0].subBlock)

        assertEquals("Strength & Power", rBlocks[1].section)
        assertEquals("E3MOM Front Squats", rBlocks[1].subBlock)

        assertEquals("Accessories", rBlocks[2].section)
        assertEquals("E3MOM Trisets", rBlocks[2].subBlock)
        assertEquals("Accessories", rBlocks[3].section)
        assertEquals("E3MOM Trisets", rBlocks[3].subBlock)
        assertEquals("Accessories", rBlocks[4].section)
        assertEquals("E3MOM Trisets", rBlocks[4].subBlock)

        assertEquals("Accessories", rBlocks[5].section)
        assertEquals("E2,5MOM Trisets", rBlocks[5].subBlock)
        assertEquals("Accessories", rBlocks[6].section)
        assertEquals("E2,5MOM Trisets", rBlocks[6].subBlock)
        assertEquals("Accessories", rBlocks[7].section)
        assertEquals("E2,5MOM Trisets", rBlocks[7].subBlock)

        // And session blocks are likewise updated
        val updatedSessionWithBlocks = fakeDb.sessionDao().getByIdOnce(100L)!!
        val sBlocks = updatedSessionWithBlocks.blocks.sortedBy { it.block.position }.map { it.block }
        assertEquals(8, sBlocks.size)

        assertEquals("Strength & Power", sBlocks[0].section)
        assertEquals("E3MOM Complex", sBlocks[0].subBlock)

        assertEquals("Strength & Power", sBlocks[1].section)
        assertEquals("E3MOM Front Squats", sBlocks[1].subBlock)

        assertEquals("Accessories", sBlocks[2].section)
        assertEquals("E3MOM Trisets", sBlocks[2].subBlock)
        assertEquals("Accessories", sBlocks[3].section)
        assertEquals("E3MOM Trisets", sBlocks[3].subBlock)
        assertEquals("Accessories", sBlocks[4].section)
        assertEquals("E3MOM Trisets", sBlocks[4].subBlock)

        assertEquals("Accessories", sBlocks[5].section)
        assertEquals("E2,5MOM Trisets", sBlocks[5].subBlock)
        assertEquals("Accessories", sBlocks[6].section)
        assertEquals("E2,5MOM Trisets", sBlocks[6].subBlock)
        assertEquals("Accessories", sBlocks[7].section)
        assertEquals("E2,5MOM Trisets", sBlocks[7].subBlock)
    }

    @Test
    fun `reconcileLegacyWorkoutSubBlocks is idempotent and leaves customized and unrelated sessions untouched`() = runTest {
        // Given an unrelated session ("Wednesday Metcon") and a customized Monday block with non-empty subBlock
        val wednesdaySession = Session(id = 200L, cycleId = 1L, date = sessionDate, title = "Wednesday Metcon", notes = "")
        val wednesdayBlock = SessionBlock(
            id = 2000L, sessionId = 200L, position = 0,
            name = "Front Squats", kind = BlockKind.STRENGTH, format = "5x5", scheme = "5x5",
            section = "Main Lift", subBlock = ""
        )
        fakeDb.sessionDao().insertSession(wednesdaySession)
        fakeDb.blockDao().insertBlock(wednesdayBlock)

        val mondayCustomSession = Session(id = 300L, cycleId = 1L, date = sessionDate, title = "Monday Heavy", notes = "")
        val mondayCustomBlock = SessionBlock(
            id = 3000L, sessionId = 300L, position = 0,
            name = "Front Squats", kind = BlockKind.STRENGTH, format = "E3MOM", scheme = "4x4",
            section = "Strength", subBlock = "Custom Front Squats Tag"
        )
        fakeDb.sessionDao().insertSession(mondayCustomSession)
        fakeDb.blockDao().insertBlock(mondayCustomBlock)

        // When reconcileLegacyWorkoutSubBlocks runs
        repository.reconcileLegacyWorkoutSubBlocks()

        // Then unrelated session block remains untouched (name matches Front Squats but section & title do not qualify)
        val s200 = fakeDb.sessionDao().getByIdOnce(200L)!!
        assertEquals("Main Lift", s200.blocks[0].block.section)
        assertEquals("", s200.blocks[0].block.subBlock)

        // And customized Monday block remains untouched (non-empty subBlock is protected)
        val s300 = fakeDb.sessionDao().getByIdOnce(300L)!!
        assertEquals("Strength", s300.blocks[0].block.section)
        assertEquals("Custom Front Squats Tag", s300.blocks[0].block.subBlock)
    }
}

private class FakeTestAppDatabase : AppDatabase() {
    var simulateSetInsertFailure = false

    private val exercisesStorage = mutableListOf<Exercise>()
    private val cyclesStorage = mutableListOf<Cycle>()
    private val cycleGoalsStorage = mutableListOf<CycleGoal>()
    private val routinesStorage = mutableListOf<Routine>()
    private val routineBlocksStorage = mutableListOf<RoutineBlock>()
    private val sessionsStorage = mutableListOf<Session>()
    private val blocksStorage = mutableListOf<SessionBlock>()
    private val setsStorage = mutableListOf<BlockSet>()
    private val repMaxesStorage = mutableListOf<RepMax>()
    private val weightStorage = mutableListOf<WeightEntry>()

    data class DbSnapshot(
        val exercises: List<Exercise>,
        val cycles: List<Cycle>,
        val routines: List<Routine>,
        val routineBlocks: List<RoutineBlock>,
        val sessions: List<Session>,
        val blocks: List<SessionBlock>,
        val sets: List<BlockSet>,
        val weightEntries: List<WeightEntry>
    )

    fun createSnapshot(): DbSnapshot = DbSnapshot(
        exercises = ArrayList(exercisesStorage),
        cycles = ArrayList(cyclesStorage),
        routines = ArrayList(routinesStorage),
        routineBlocks = ArrayList(routineBlocksStorage),
        sessions = ArrayList(sessionsStorage),
        blocks = ArrayList(blocksStorage),
        sets = ArrayList(setsStorage),
        weightEntries = ArrayList(weightStorage)
    )

    fun restoreSnapshot(snapshot: DbSnapshot) {
        exercisesStorage.clear(); exercisesStorage.addAll(snapshot.exercises)
        cyclesStorage.clear(); cyclesStorage.addAll(snapshot.cycles)
        routinesStorage.clear(); routinesStorage.addAll(snapshot.routines)
        routineBlocksStorage.clear(); routineBlocksStorage.addAll(snapshot.routineBlocks)
        sessionsStorage.clear(); sessionsStorage.addAll(snapshot.sessions)
        blocksStorage.clear(); blocksStorage.addAll(snapshot.blocks)
        setsStorage.clear(); setsStorage.addAll(snapshot.sets)
        weightStorage.clear(); weightStorage.addAll(snapshot.weightEntries)
    }

    fun populateSampleData() {
        exercisesStorage.clear()
        exercisesStorage.add(Exercise(id = 1L, name = "Clean", category = ExerciseCategory.BARBELL, metricType = MetricType.WEIGHT))
        exercisesStorage.add(Exercise(id = 2L, name = "Front Squat", category = ExerciseCategory.BARBELL, metricType = MetricType.WEIGHT))
        exercisesStorage.add(Exercise(id = 3L, name = "Pullups", category = ExerciseCategory.GYMNASTICS, metricType = MetricType.REPS))

        cyclesStorage.clear()
        cyclesStorage.add(Cycle(id = 1L, name = "Sample Cycle", startDate = LocalDate.of(2026, 9, 1), isActive = true))
    }

    private val exerciseDaoImpl = object : ExerciseDao {
        override suspend fun insert(exercise: Exercise): Long {
            val existing = exercisesStorage.find { it.name.equals(exercise.name.trim(), ignoreCase = true) }
            if (existing != null) return existing.id
            val nextId = (exercisesStorage.maxOfOrNull { it.id } ?: 0L) + 1L
            val created = exercise.copy(id = nextId)
            exercisesStorage.add(created)
            return nextId
        }
        override suspend fun insertAll(exercises: List<Exercise>) { exercises.forEach { insert(it) } }
        override suspend fun update(exercise: Exercise) {
            val idx = exercisesStorage.indexOfFirst { it.id == exercise.id }
            if (idx >= 0) exercisesStorage[idx] = exercise
        }
        override suspend fun delete(exercise: Exercise) { exercisesStorage.removeAll { it.id == exercise.id } }
        override fun observeAll(): Flow<List<Exercise>> = flowOf(exercisesStorage)
        override suspend fun byId(id: Long): Exercise? = exercisesStorage.find { it.id == id }
        override suspend fun byName(name: String): Exercise? = exercisesStorage.find { it.name.equals(name.trim(), ignoreCase = true) }
        override suspend fun count(): Int = exercisesStorage.size
        override suspend fun insertAllReplace(exercises: List<Exercise>) {
            exercises.forEach { ex ->
                exercisesStorage.removeAll { it.id == ex.id }
                exercisesStorage.add(ex)
            }
        }
        override suspend fun getAllOnce(): List<Exercise> = ArrayList(exercisesStorage)
        override suspend fun deleteAll() { exercisesStorage.clear() }
    }

    private val sessionDaoImpl = object : SessionDao {
        override suspend fun insertSession(session: Session): Long {
            val nextId = if (session.id > 0L) session.id else (sessionsStorage.maxOfOrNull { it.id } ?: 0L) + 1L
            val created = session.copy(id = nextId)
            sessionsStorage.add(created)
            return nextId
        }
        override suspend fun insertSessions(sessions: List<Session>) { sessions.forEach { insertSession(it) } }
        override suspend fun updateSession(session: Session) {
            val idx = sessionsStorage.indexOfFirst { it.id == session.id }
            if (idx >= 0) sessionsStorage[idx] = session
        }
        override suspend fun deleteSession(session: Session) { sessionsStorage.removeAll { it.id == session.id } }
        override fun observeByCycle(cycleId: Long): Flow<List<SessionWithBlocks>> = flowOf(emptyList())
        override fun observeAll(): Flow<List<SessionWithBlocks>> = flowOf(emptyList())
        override suspend fun getByIdOnce(id: Long): SessionWithBlocks? {
            val s = sessionsStorage.find { it.id == id } ?: return null
            val bList = blocksStorage.filter { it.sessionId == id }.map { b ->
                val sets = setsStorage.filter { it.blockId == b.id }.sortedBy { it.position }
                BlockWithSets(block = b, sets = sets)
            }
            return SessionWithBlocks(session = s, blocks = bList)
        }
        override suspend fun getAllSessionsOnce(): List<Session> = ArrayList(sessionsStorage)
        override suspend fun deleteAllSessions() { sessionsStorage.clear() }
    }

    private val blockDaoImpl = object : BlockDao {
        override suspend fun insertBlock(block: SessionBlock): Long {
            val nextId = if (block.id > 0L) block.id else (blocksStorage.maxOfOrNull { it.id } ?: 0L) + 1L
            val created = block.copy(id = nextId)
            blocksStorage.add(created)
            return nextId
        }
        override suspend fun insertBlocks(blocks: List<SessionBlock>) { blocks.forEach { insertBlock(it) } }
        override suspend fun insertSets(sets: List<BlockSet>) {
            if (simulateSetInsertFailure) throw IllegalStateException("Simulated failure")
            sets.forEach { insertSet(it) }
        }
        override suspend fun insertSet(set: BlockSet): Long {
            if (simulateSetInsertFailure) throw IllegalStateException("Simulated failure")
            val nextId = if (set.id > 0L) set.id else (setsStorage.maxOfOrNull { it.id } ?: 0L) + 1L
            val created = set.copy(id = nextId)
            setsStorage.add(created)
            return nextId
        }
        override suspend fun getBlocksForSessionOnce(sessionId: Long): List<SessionBlock> =
            blocksStorage.filter { it.sessionId == sessionId }.sortedBy { it.position }
        override suspend fun getSetsForBlockOnce(blockId: Long): List<BlockSet> =
            setsStorage.filter { it.blockId == blockId }.sortedBy { it.position }
        override suspend fun getAllBlocksOnce(): List<SessionBlock> = ArrayList(blocksStorage)
        override suspend fun getAllSetsOnce(): List<BlockSet> = ArrayList(setsStorage)
        override suspend fun deleteAllBlocks() { blocksStorage.clear() }
        override suspend fun deleteBlocksForSession(sessionId: Long) { blocksStorage.removeAll { it.sessionId == sessionId } }
        override suspend fun deleteAllSets() { setsStorage.clear() }
    }

    private val cycleDaoImpl = object : CycleDao {
        override suspend fun insert(cycle: Cycle): Long {
            val nextId = (cyclesStorage.maxOfOrNull { it.id } ?: 0L) + 1L
            val created = cycle.copy(id = nextId)
            cyclesStorage.add(created)
            return nextId
        }
        override suspend fun insertAll(cycles: List<Cycle>) { cycles.forEach { insert(it) } }
        override suspend fun update(cycle: Cycle) {
            val idx = cyclesStorage.indexOfFirst { it.id == cycle.id }
            if (idx >= 0) cyclesStorage[idx] = cycle
        }
        override suspend fun delete(cycle: Cycle) { cyclesStorage.removeAll { it.id == cycle.id } }
        override fun observeAll(): Flow<List<Cycle>> = flowOf(cyclesStorage)
        override fun observeActive(): Flow<Cycle?> = flowOf(cyclesStorage.find { it.isActive })
        override suspend fun byId(id: Long): Cycle? = cyclesStorage.find { it.id == id }
        override suspend fun getAllOnce(): List<Cycle> = ArrayList(cyclesStorage)
        override suspend fun clearActive() {
            for (i in cyclesStorage.indices) cyclesStorage[i] = cyclesStorage[i].copy(isActive = false)
        }
        override suspend fun markActive(id: Long) {
            for (i in cyclesStorage.indices) cyclesStorage[i] = cyclesStorage[i].copy(isActive = cyclesStorage[i].id == id)
        }
        override suspend fun deleteAll() { cyclesStorage.clear() }
    }

    private val routineDaoImpl = object : RoutineDao {
        override suspend fun insert(routine: Routine): Long {
            val nextId = if (routine.id > 0L) routine.id else (routinesStorage.maxOfOrNull { it.id } ?: 0L) + 1L
            val created = routine.copy(id = nextId)
            routinesStorage.add(created)
            return nextId
        }
        override suspend fun insertAll(routines: List<Routine>) { routines.forEach { insert(it) } }
        override suspend fun insertBlock(block: RoutineBlock): Long {
            val nextId = if (block.id > 0L) block.id else (routineBlocksStorage.maxOfOrNull { it.id } ?: 0L) + 1L
            val created = block.copy(id = nextId)
            routineBlocksStorage.add(created)
            return nextId
        }
        override suspend fun insertBlocks(blocks: List<RoutineBlock>) { blocks.forEach { insertBlock(it) } }
        override suspend fun deleteBlocksForRoutine(routineId: Long) { routineBlocksStorage.removeAll { it.routineId == routineId } }
        override suspend fun getAllOnce(): List<Routine> = ArrayList(routinesStorage)
        override suspend fun getAllWithBlocksOnce(): List<RoutineWithBlocks> = routinesStorage.map { r ->
            RoutineWithBlocks(r, routineBlocksStorage.filter { it.routineId == r.id })
        }
        override suspend fun deleteAll() { routinesStorage.clear(); routineBlocksStorage.clear() }
        override suspend fun update(routine: Routine) {
            val idx = routinesStorage.indexOfFirst { it.id == routine.id }
            if (idx >= 0) routinesStorage[idx] = routine
        }
        override suspend fun delete(routine: Routine) {
            routinesStorage.removeAll { it.id == routine.id }
            routineBlocksStorage.removeAll { it.routineId == routine.id }
        }
        override fun observeAll(): Flow<List<Routine>> = flowOf(routinesStorage)
        override fun observeWithBlocks(): Flow<List<RoutineWithBlocks>> = flowOf(
            routinesStorage.map { r -> RoutineWithBlocks(r, routineBlocksStorage.filter { it.routineId == r.id }) }
        )
        override suspend fun byId(id: Long): Routine? = routinesStorage.find { it.id == id }
        override suspend fun byName(name: String): Routine? = routinesStorage.find { it.name.equals(name, ignoreCase = true) }
    }

    private val repMaxDaoImpl = object : RepMaxDao {
        override suspend fun insert(repMax: RepMax): Long = 1L
        override suspend fun insertAll(repMaxes: List<RepMax>) {}
        override suspend fun delete(repMax: RepMax) {}
        override fun observeAll(): Flow<List<RepMax>> = flowOf(repMaxesStorage)
        override fun observeForExercise(exerciseId: Long): Flow<List<RepMax>> = flowOf(emptyList())
        override suspend fun getAllOnce(): List<RepMax> = ArrayList(repMaxesStorage)
        override suspend fun deleteAll() {}
        override suspend fun bestWeight(exerciseId: Long, reps: Int): Double? = null
    }

    private val cycleGoalDaoImpl = object : CycleGoalDao {
        override suspend fun insert(goal: CycleGoal): Long = 1L
        override suspend fun insertAll(goals: List<CycleGoal>) {}
        override suspend fun update(goal: CycleGoal) {}
        override suspend fun delete(goal: CycleGoal) {}
        override suspend fun deleteByCycle(cycleId: Long) {}
        override fun all(): Flow<List<CycleGoal>> = flowOf(emptyList())
        override fun byCycle(cycleId: Long): Flow<List<CycleGoal>> = flowOf(emptyList())
        override suspend fun snapshot(): List<CycleGoal> = emptyList()
        override suspend fun deleteAll() {}
    }

    private val weightDaoImpl = object : WeightDao {
        private val weightFlow = kotlinx.coroutines.flow.MutableStateFlow<List<WeightEntry>>(emptyList())
        override suspend fun upsert(entry: WeightEntry): Long = 1L
        override suspend fun upsertAll(entries: List<WeightEntry>) {}
        override fun getAllActiveEntries(): Flow<List<WeightEntry>> = weightFlow
        override suspend fun getAllActiveEntriesOnce(): List<WeightEntry> = emptyList()
        override suspend fun getAllEntriesIncludingTombstones(): List<WeightEntry> = emptyList()
        override suspend fun getEntryByDate(date: LocalDate): WeightEntry? = null
        override suspend fun markDeleted(date: LocalDate, deletedAt: Long) {}
        override suspend fun purgeOldTombstones(cutoffMillis: Long) {}
        override suspend fun deleteAll() {}
    }

    override fun exerciseDao(): ExerciseDao = exerciseDaoImpl
    override fun routineDao(): RoutineDao = routineDaoImpl
    override fun cycleDao(): CycleDao = cycleDaoImpl
    override fun sessionDao(): SessionDao = sessionDaoImpl
    override fun blockDao(): BlockDao = blockDaoImpl
    override fun repMaxDao(): RepMaxDao = repMaxDaoImpl
    override fun cycleGoalDao(): CycleGoalDao = cycleGoalDaoImpl
    override fun weightDao(): WeightDao = weightDaoImpl
    override fun clearAllTables() {}
    override fun createInvalidationTracker(): androidx.room.InvalidationTracker {
        return object : androidx.room.InvalidationTracker(this@FakeTestAppDatabase, "sessions") {}
    }
    override fun createOpenHelper(config: androidx.room.DatabaseConfiguration): androidx.sqlite.db.SupportSQLiteOpenHelper {
        throw UnsupportedOperationException("FakeAppDatabase does not support SQLiteOpenHelper")
    }
}

private class FakeTestTransactionRunner(
    private val fakeDb: FakeTestAppDatabase
) : TransactionRunner {
    override suspend fun <R> runInTransaction(block: suspend () -> R): R {
        val snapshot = fakeDb.createSnapshot()
        return try {
            block()
        } catch (t: Throwable) {
            fakeDb.restoreSnapshot(snapshot)
            throw t
        }
    }
}
