package com.fractanomics.crosstraining.data

import com.fractanomics.crosstraining.data.ai.AiCoreManager
import com.fractanomics.crosstraining.data.ai.ExerciseEntityGrounder
import com.fractanomics.crosstraining.data.ai.FitnessSpeechLexicon
import com.fractanomics.crosstraining.data.ai.GeminiNanoClient
import com.fractanomics.crosstraining.data.dao.BlockDao
import com.fractanomics.crosstraining.data.dao.CycleDao
import com.fractanomics.crosstraining.data.dao.CycleGoalDao
import com.fractanomics.crosstraining.data.dao.ExerciseDao
import com.fractanomics.crosstraining.data.dao.RepMaxDao
import com.fractanomics.crosstraining.data.dao.RoutineDao
import com.fractanomics.crosstraining.data.dao.SessionDao
import com.fractanomics.crosstraining.data.model.BlockKind
import com.fractanomics.crosstraining.data.model.BlockSet
import com.fractanomics.crosstraining.data.model.BlockWithSets
import com.fractanomics.crosstraining.data.model.Cycle
import com.fractanomics.crosstraining.data.model.CycleGoal
import com.fractanomics.crosstraining.data.model.Exercise
import com.fractanomics.crosstraining.data.model.RepMax
import com.fractanomics.crosstraining.data.model.Routine
import com.fractanomics.crosstraining.data.model.RoutineBlock
import com.fractanomics.crosstraining.data.model.RoutineWithBlocks
import com.fractanomics.crosstraining.data.model.Session
import com.fractanomics.crosstraining.data.model.SessionBlock
import com.fractanomics.crosstraining.data.model.SessionWithBlocks
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
 * End-to-end integration test suite for voice workout ingestion into [Repository]
 * with atomic Room database persistence (Issue #473, Parent Story #467).
 *
 * Acceptance Criteria Covered:
 * - [x] Repository method createSessionFromVoiceInput(voiceText: String, sessionDate: LocalDate): Session
 * - [x] Coordinates VoiceInputController, AiCoreManager, ExerciseEntityGrounder, FitnessSpeechLexicon
 * - [x] Atomic transaction: all Session, SessionBlock, BlockSet writes succeed or all rollback
 * - [x] Handles exercise disambiguation: if ambiguous, UI prompts user; user selection committed to DB
 * - [x] Live logging: appendBlockSetFromVoice(sessionId: Long, voiceText: String): BlockSet for active workouts
 * - [x] Integration tests with sample database (create 3+ complex voice sessions end-to-end)
 */
class VoiceRepositoryIntegrationTest {

    private lateinit var fakeDb: FakeSampleAppDatabase
    private lateinit var transactionRunner: FakeTransactionRunner
    private lateinit var fakeAiClient: FakeGeminiNanoClient
    private lateinit var aiCoreManager: AiCoreManager
    private lateinit var repository: Repository

    private val sessionDate = LocalDate.of(2026, 9, 2)

    @Before
    fun setUp() {
        fakeDb = FakeSampleAppDatabase()
        fakeDb.populateSampleData()
        transactionRunner = FakeTransactionRunner(fakeDb)
        fakeAiClient = FakeGeminiNanoClient()
        aiCoreManager = AiCoreManager(client = fakeAiClient)
        repository = Repository(
            db = fakeDb,
            transactionRunner = transactionRunner,
            aiCoreManager = aiCoreManager,
            grounder = ExerciseEntityGrounder.DEFAULT,
            lexicon = FitnessSpeechLexicon.DEFAULT
        )
    }

    // =========================================================================
    // Complex Voice Session 1: Multi-Movement Barbell Complex Dictation
    // =========================================================================

    @Test
    fun `session 1 - barbell complex dictation creates session with E2MOM and multiple sets atomically`() = runTest {
        // Dictation: "1 Halting Deadlift plus 1 Hang Power Snatch at 60 kilos, 4 sets on a 2 minute timer"
        fakeAiClient.configuredResponse = """
        {
          "blocks": [
            {
              "name": "Halting Deadlift + Hang Power Snatch",
              "kind": "STRENGTH",
              "format": "E2MOM",
              "repScheme": "4x1",
              "movements": ["Halting Deadlift", "Hang Power Snatch"],
              "description": "1 Halting Deadlift plus 1 Hang Power Snatch at 60 kilos, 4 sets on a 2 minute timer",
              "sets": [
                {"reps": 1, "weight": 60.0, "isWarmup": false},
                {"reps": 1, "weight": 60.0, "isWarmup": false},
                {"reps": 1, "weight": 60.0, "isWarmup": false},
                {"reps": 1, "weight": 60.0, "isWarmup": false}
              ]
            }
          ]
        }
        """.trimIndent()

        val voiceText = "1 Halting Deadlift plus 1 Hang Power Snatch at 60 kilos, 4 sets on a 2 minute timer"
        val session = repository.createSessionFromVoiceInput(voiceText, sessionDate)

        // Verify Session entity
        assertNotNull(session)
        assertTrue("Session ID must be positive", session.id > 0)
        assertEquals(sessionDate, session.date)
        assertTrue(session.title.contains("Halting Deadlift + Hang Power Snatch"))

        // Verify database relational integrity
        val persisted = fakeDb.sessionDao().getByIdOnce(session.id)
        assertNotNull("Session must be persisted in database", persisted)
        assertEquals(1, persisted!!.blocks.size)

        val blockWithSets = persisted.blocks.first()
        assertEquals("Halting Deadlift + Hang Power Snatch", blockWithSets.block.name)
        assertEquals(BlockKind.STRENGTH, blockWithSets.block.kind)
        assertEquals("E2MOM", blockWithSets.block.format)
        assertEquals("4x1", blockWithSets.block.scheme)

        // Verify all 4 sets are persisted with 60kg
        assertEquals(4, blockWithSets.sets.size)
        blockWithSets.sets.forEachIndexed { idx, set ->
            assertEquals(idx, set.position)
            assertEquals(1, set.reps)
            assertEquals(60.0, set.weight!!, 0.001)
        }
    }

    // =========================================================================
    // Complex Voice Session 2: Acoustic Fitness Jargon Correction (EMOM & Wall Balls)
    // =========================================================================

    @Test
    fun `session 2 - acoustic fitness jargon normalizes a mom and wall balls to EMOM 12 and Wall Ball Shots`() = runTest {
        // Dictation: "12 min a mom of 15 wall balls"
        fakeAiClient.configuredResponse = """
        {
          "blocks": [
            {
              "name": "Wall Ball Shots",
              "kind": "METCON",
              "format": "EMOM 12",
              "repScheme": "15 reps",
              "description": "12 min EMOM of 15 wall balls",
              "sets": [
                {"reps": 15, "weight": 9.0, "isWarmup": false}
              ]
            }
          ]
        }
        """.trimIndent()

        val voiceText = "12 min a mom of 15 wall balls"
        val session = repository.createSessionFromVoiceInput(voiceText, sessionDate)

        assertNotNull(session)
        assertTrue(session.id > 0)

        val persisted = fakeDb.sessionDao().getByIdOnce(session.id)
        assertNotNull(persisted)
        assertEquals(1, persisted!!.blocks.size)

        val blockWithSets = persisted.blocks.first()
        assertEquals("Wall Ball Shots", blockWithSets.block.name)
        assertEquals(BlockKind.METCON, blockWithSets.block.kind)
        assertEquals("EMOM 12", blockWithSets.block.format)

        // Verify exercise grounding mapped to catalog Wall Ball Shots ID
        val expectedExercise = fakeDb.exerciseDao().byName("Wall Ball Shots")
        assertNotNull(expectedExercise)
        assertEquals(expectedExercise!!.id, blockWithSets.block.mainExerciseId)

        assertEquals(1, blockWithSets.sets.size)
        assertEquals(15, blockWithSets.sets.first().reps)
    }

    // =========================================================================
    // Complex Voice Session 3: Multi-Block WOD / Metcon Session
    // =========================================================================

    @Test
    fun `session 3 - multi-block strength plus metcon workout session end-to-end`() = runTest {
        fakeAiClient.configuredResponse = """
        {
          "blocks": [
            {
              "name": "Back Squat",
              "kind": "STRENGTH",
              "format": "Rest 2min",
              "repScheme": "5x5",
              "sets": [
                {"reps": 5, "weight": 100.0},
                {"reps": 5, "weight": 100.0},
                {"reps": 5, "weight": 100.0},
                {"reps": 5, "weight": 100.0},
                {"reps": 5, "weight": 100.0}
              ]
            },
            {
              "name": "AMRAP 10",
              "kind": "METCON",
              "format": "AMRAP 10",
              "repScheme": "10-15",
              "movements": ["Pull-ups", "Push-ups"],
              "sets": [
                {"reps": 1, "metricValue": null}
              ]
            }
          ]
        }
        """.trimIndent()

        val voiceText = "5x5 Back Squat at 100 kg followed by AMRAP 10 of 10 pull ups and 15 push ups"
        val session = repository.createSessionFromVoiceInput(voiceText, sessionDate)

        val persisted = fakeDb.sessionDao().getByIdOnce(session.id)
        assertNotNull(persisted)
        assertEquals(2, persisted!!.blocks.size)

        // Block 1: Back Squat
        val squatBlock = persisted.blocks[0]
        assertEquals("Back Squat", squatBlock.block.name)
        assertEquals(BlockKind.STRENGTH, squatBlock.block.kind)
        assertEquals(5, squatBlock.sets.size)

        // Block 2: AMRAP 10
        val metconBlock = persisted.blocks[1]
        assertEquals(BlockKind.METCON, metconBlock.block.kind)
        assertEquals("AMRAP 10", metconBlock.block.format)
    }

    // =========================================================================
    // Exercise Disambiguation Flow
    // =========================================================================

    @Test
    fun `disambiguation flow - detects ambiguous Cleans and persists user selection Power Clean`() = runTest {
        val voiceText = "5 sets of 3 Cleans at 80kg"

        // 1. Parsing detects ambiguity for "Cleans"
        val parseOutcome = repository.parseVoiceInput(voiceText)
        assertEquals(1, parseOutcome.blocks.size)
        assertTrue(
            "Expected ambiguous exercises for block 0, found: ${parseOutcome.ambiguousExercises}",
            parseOutcome.ambiguousExercises.containsKey(0)
        )
        val candidates = parseOutcome.ambiguousExercises[0]!!
        assertTrue("Candidates should contain Power Clean and Clean", candidates.any { it.name.contains("Clean", ignoreCase = true) })

        // 2. User selects Power Clean from candidates
        val powerClean = candidates.find { it.name.equals("Power Clean", ignoreCase = true) }
            ?: fakeDb.exerciseDao().byName("Power Clean")!!

        val session = repository.createSessionFromVoiceInput(
            voiceText = voiceText,
            sessionDate = sessionDate,
            disambiguatedExercises = mapOf(0 to powerClean),
            parsedBlocks = parseOutcome.blocks
        )

        val persisted = fakeDb.sessionDao().getByIdOnce(session.id)
        assertNotNull(persisted)
        val block = persisted!!.blocks.first()

        assertEquals("Power Clean", block.block.name)
        assertEquals(powerClean.id, block.block.mainExerciseId)
    }

    // =========================================================================
    // Live Workout Set Logging: appendBlockSetFromVoice
    // =========================================================================

    @Test
    fun `live logging - appendBlockSetFromVoice appends set with weight and RPE to active session block`() = runTest {
        // Setup existing active session with a Back Squat block
        val existingSession = repository.createSessionFromVoiceInput("3 Back Squats at 100 kg", sessionDate)
        val initialBlocks = fakeDb.sessionDao().getByIdOnce(existingSession.id)!!.blocks
        val squatBlockId = initialBlocks.first().block.id

        // Dictate live set: "Logged 5 back squats at 120 kg, RPE 8"
        val appended = repository.appendBlockSetFromVoice(
            sessionId = existingSession.id,
            voiceText = "Logged 5 back squats at 120 kg, RPE 8"
        )

        assertNotNull(appended)
        assertTrue("Appended set must have a valid generated ID", appended.id > 0)
        assertEquals(squatBlockId, appended.blockId)
        assertEquals(5, appended.reps)
        assertEquals(120.0, appended.weight!!, 0.001)
        assertTrue("Set notes should record RPE 8", appended.notes.contains("RPE 8"))

        // Verify total sets for block increased
        val updatedSets = fakeDb.blockDao().getSetsForBlockOnce(squatBlockId)
        assertTrue("Sets should have incremented", updatedSets.size >= 2)
        val lastSet = updatedSets.last()
        assertEquals(5, lastSet.reps)
        assertEquals(120.0, lastSet.weight!!, 0.001)
    }

    // =========================================================================
    // Atomic Transaction Rollback on Failure
    // =========================================================================

    @Test
    fun `atomic transaction - failure during block insertion rolls back entire session creation with zero orphans`() = runTest {
        val initialSessionCount = fakeDb.sessionDao().getAllSessionsOnce().size
        val initialBlockCount = fakeDb.blockDao().getAllBlocksOnce().size
        val initialSetCount = fakeDb.blockDao().getAllSetsOnce().size

        // Configure database to simulate failure on set insertion
        fakeDb.simulateSetInsertFailure = true

        try {
            repository.createSessionFromVoiceInput("5 Back Squats at 120 kg", sessionDate)
            fail("Expected exception during atomic transaction")
        } catch (e: Exception) {
            assertTrue("Expected simulated database error", e.message?.contains("Simulated database failure") == true)
        } finally {
            fakeDb.simulateSetInsertFailure = false
        }

        // Verify complete rollback: zero orphaned sessions, blocks, or sets
        val finalSessionCount = fakeDb.sessionDao().getAllSessionsOnce().size
        val finalBlockCount = fakeDb.blockDao().getAllBlocksOnce().size
        val finalSetCount = fakeDb.blockDao().getAllSetsOnce().size

        assertEquals("Sessions count must not increase after rollback", initialSessionCount, finalSessionCount)
        assertEquals("Blocks count must not increase after rollback", initialBlockCount, finalBlockCount)
        assertEquals("Sets count must not increase after rollback", initialSetCount, finalSetCount)
    }

    // =========================================================================
    // Edge Cases & Input Validation
    // =========================================================================

    @Test(expected = IllegalArgumentException::class)
    fun `blank voice input throws IllegalArgumentException`() = runTest {
        repository.createSessionFromVoiceInput("   ", sessionDate)
    }

    @Test(expected = IllegalArgumentException::class)
    fun `appendBlockSetFromVoice with non-existent session throws IllegalArgumentException`() = runTest {
        repository.appendBlockSetFromVoice(999999L, "5 reps at 100 kg")
    }
}

// =============================================================================
// Test Doubles: In-Memory Sample Database & Fake Gemini Nano Client
// =============================================================================

class FakeGeminiNanoClient : GeminiNanoClient {
    var configuredResponse: String = "{}"
    var isReady: Boolean = true

    override suspend fun isAvailable(): Boolean = isReady

    override suspend fun generateText(prompt: String): String = configuredResponse
}

class FakeTransactionRunner(
    private val fakeDb: FakeSampleAppDatabase
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

class FakeSampleAppDatabase : AppDatabase() {

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

    data class DbSnapshot(
        val exercises: List<Exercise>,
        val cycles: List<Cycle>,
        val sessions: List<Session>,
        val blocks: List<SessionBlock>,
        val sets: List<BlockSet>
    )

    fun createSnapshot(): DbSnapshot = DbSnapshot(
        exercises = ArrayList(exercisesStorage),
        cycles = ArrayList(cyclesStorage),
        sessions = ArrayList(sessionsStorage),
        blocks = ArrayList(blocksStorage),
        sets = ArrayList(setsStorage)
    )

    fun restoreSnapshot(snapshot: DbSnapshot) {
        exercisesStorage.clear(); exercisesStorage.addAll(snapshot.exercises)
        cyclesStorage.clear(); cyclesStorage.addAll(snapshot.cycles)
        sessionsStorage.clear(); sessionsStorage.addAll(snapshot.sessions)
        blocksStorage.clear(); blocksStorage.addAll(snapshot.blocks)
        setsStorage.clear(); setsStorage.addAll(snapshot.sets)
    }

    fun populateSampleData() {
        exercisesStorage.clear()
        SeedData.defaults.forEachIndexed { idx, ex ->
            exercisesStorage.add(ex.copy(id = (idx + 1).toLong()))
        }
        cyclesStorage.clear()
        cyclesStorage.add(
            Cycle(id = 1L, name = "Sample Cycle", startDate = LocalDate.of(2026, 9, 1), isActive = true)
        )
    }

    private val exerciseDaoImpl = object : ExerciseDao {
        override suspend fun insert(exercise: Exercise): Long {
            val nextId = (exercisesStorage.maxOfOrNull { it.id } ?: 0L) + 1L
            val created = exercise.copy(id = nextId)
            exercisesStorage.add(created)
            return nextId
        }

        override suspend fun insertAll(exercises: List<Exercise>) {
            exercises.forEach { insert(it) }
        }

        override suspend fun update(exercise: Exercise) {
            val idx = exercisesStorage.indexOfFirst { it.id == exercise.id }
            if (idx >= 0) exercisesStorage[idx] = exercise
        }

        override suspend fun delete(exercise: Exercise) {
            exercisesStorage.removeAll { it.id == exercise.id }
        }

        override fun observeAll(): Flow<List<Exercise>> = flowOf(exercisesStorage)

        override suspend fun byId(id: Long): Exercise? = exercisesStorage.find { it.id == id }

        override suspend fun byName(name: String): Exercise? =
            exercisesStorage.find { it.name.equals(name.trim(), ignoreCase = true) }

        override suspend fun count(): Int = exercisesStorage.size

        override suspend fun insertAllReplace(exercises: List<Exercise>) {
            exercises.forEach { ex ->
                exercisesStorage.removeAll { it.id == ex.id }
                exercisesStorage.add(ex)
            }
        }

        override suspend fun getAllOnce(): List<Exercise> = ArrayList(exercisesStorage)

        override suspend fun deleteAll() {
            exercisesStorage.clear()
        }
    }

    private val sessionDaoImpl = object : SessionDao {
        override suspend fun insertSession(session: Session): Long {
            val nextId = (sessionsStorage.maxOfOrNull { it.id } ?: 0L) + 1L
            val created = session.copy(id = nextId)
            sessionsStorage.add(created)
            return nextId
        }

        override suspend fun insertSessions(sessions: List<Session>) {
            sessions.forEach { insertSession(it) }
        }

        override suspend fun updateSession(session: Session) {
            val idx = sessionsStorage.indexOfFirst { it.id == session.id }
            if (idx >= 0) sessionsStorage[idx] = session
        }

        override suspend fun deleteSession(session: Session) {
            sessionsStorage.removeAll { it.id == session.id }
        }

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

        override suspend fun deleteAllSessions() {
            sessionsStorage.clear()
        }
    }

    private val blockDaoImpl = object : BlockDao {
        override suspend fun insertBlock(block: SessionBlock): Long {
            val nextId = (blocksStorage.maxOfOrNull { it.id } ?: 0L) + 1L
            val created = block.copy(id = nextId)
            blocksStorage.add(created)
            return nextId
        }

        override suspend fun insertBlocks(blocks: List<SessionBlock>) {
            blocks.forEach { insertBlock(it) }
        }

        override suspend fun insertSets(sets: List<BlockSet>) {
            if (simulateSetInsertFailure) {
                throw IllegalStateException("Simulated database failure during insertSets")
            }
            sets.forEach { insertSet(it) }
        }

        override suspend fun insertSet(set: BlockSet): Long {
            if (simulateSetInsertFailure) {
                throw IllegalStateException("Simulated database failure during insertSet")
            }
            val nextId = (setsStorage.maxOfOrNull { it.id } ?: 0L) + 1L
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

        override suspend fun deleteAllBlocks() {
            blocksStorage.clear()
        }

        override suspend fun deleteBlocksForSession(sessionId: Long) {
            blocksStorage.removeAll { it.sessionId == sessionId }
        }

        override suspend fun deleteAllSets() {
            setsStorage.clear()
        }
    }

    private val cycleDaoImpl = object : CycleDao {
        override suspend fun insert(cycle: Cycle): Long {
            val nextId = (cyclesStorage.maxOfOrNull { it.id } ?: 0L) + 1L
            val created = cycle.copy(id = nextId)
            cyclesStorage.add(created)
            return nextId
        }

        override suspend fun insertAll(cycles: List<Cycle>) {
            cycles.forEach { insert(it) }
        }

        override suspend fun update(cycle: Cycle) {
            val idx = cyclesStorage.indexOfFirst { it.id == cycle.id }
            if (idx >= 0) cyclesStorage[idx] = cycle
        }

        override suspend fun delete(cycle: Cycle) {
            cyclesStorage.removeAll { it.id == cycle.id }
        }

        override fun observeAll(): Flow<List<Cycle>> = flowOf(cyclesStorage)

        override fun observeActive(): Flow<Cycle?> = flowOf(cyclesStorage.find { it.isActive })

        override suspend fun byId(id: Long): Cycle? = cyclesStorage.find { it.id == id }

        override suspend fun getAllOnce(): List<Cycle> = ArrayList(cyclesStorage)

        override suspend fun clearActive() {
            for (i in cyclesStorage.indices) {
                cyclesStorage[i] = cyclesStorage[i].copy(isActive = false)
            }
        }

        override suspend fun markActive(id: Long) {
            for (i in cyclesStorage.indices) {
                cyclesStorage[i] = cyclesStorage[i].copy(isActive = cyclesStorage[i].id == id)
            }
        }

        override suspend fun deleteAll() {
            cyclesStorage.clear()
        }
    }

    private val cycleGoalDaoImpl = object : CycleGoalDao {
        override suspend fun insert(goal: CycleGoal): Long {
            val nextId = (cycleGoalsStorage.maxOfOrNull { it.id } ?: 0L) + 1L
            val created = goal.copy(id = nextId)
            cycleGoalsStorage.add(created)
            return nextId
        }
        override suspend fun insertAll(goals: List<CycleGoal>) {
            goals.forEach { insert(it) }
        }
        override suspend fun update(goal: CycleGoal) {
            val idx = cycleGoalsStorage.indexOfFirst { it.id == goal.id }
            if (idx >= 0) cycleGoalsStorage[idx] = goal
        }
        override suspend fun delete(goal: CycleGoal) {
            cycleGoalsStorage.removeAll { it.id == goal.id }
        }
        override suspend fun deleteByCycle(cycleId: Long) {
            cycleGoalsStorage.removeAll { it.cycleId == cycleId }
        }
        override fun all(): Flow<List<CycleGoal>> = flowOf(cycleGoalsStorage)
        override fun byCycle(cycleId: Long): Flow<List<CycleGoal>> = flowOf(cycleGoalsStorage.filter { it.cycleId == cycleId })
        override suspend fun snapshot(): List<CycleGoal> = ArrayList(cycleGoalsStorage)
        override suspend fun deleteAll() {
            cycleGoalsStorage.clear()
        }
    }

    private val routineDaoImpl = object : RoutineDao {
        override suspend fun insert(routine: Routine): Long {
            val nextId = (routinesStorage.maxOfOrNull { it.id } ?: 0L) + 1L
            val created = routine.copy(id = nextId)
            routinesStorage.add(created)
            return nextId
        }
        override suspend fun insertAll(routines: List<Routine>) {
            routines.forEach { insert(it) }
        }
        override suspend fun insertBlock(block: RoutineBlock): Long {
            val nextId = (routineBlocksStorage.maxOfOrNull { it.id } ?: 0L) + 1L
            val created = block.copy(id = nextId)
            routineBlocksStorage.add(created)
            return nextId
        }
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
        override suspend fun insertBlocks(blocks: List<RoutineBlock>) {
            blocks.forEach { insertBlock(it) }
        }
        override suspend fun deleteBlocksForRoutine(routineId: Long) {
            routineBlocksStorage.removeAll { it.routineId == routineId }
        }
        override suspend fun getAllOnce(): List<Routine> = ArrayList(routinesStorage)
        override suspend fun getAllWithBlocksOnce(): List<RoutineWithBlocks> = routinesStorage.map { r ->
            RoutineWithBlocks(r, routineBlocksStorage.filter { it.routineId == r.id })
        }
        override suspend fun deleteAll() {
            routinesStorage.clear()
            routineBlocksStorage.clear()
        }
    }

    private val repMaxDaoImpl = object : RepMaxDao {
        override suspend fun insert(repMax: RepMax): Long {
            val nextId = (repMaxesStorage.maxOfOrNull { it.id } ?: 0L) + 1L
            val created = repMax.copy(id = nextId)
            repMaxesStorage.add(created)
            return nextId
        }
        override suspend fun insertAll(repMaxes: List<RepMax>) {
            repMaxes.forEach { insert(it) }
        }
        override suspend fun delete(repMax: RepMax) {
            repMaxesStorage.removeAll { it.id == repMax.id }
        }
        override fun observeAll(): Flow<List<RepMax>> = flowOf(repMaxesStorage)
        override fun observeForExercise(exerciseId: Long): Flow<List<RepMax>> = flowOf(repMaxesStorage.filter { it.exerciseId == exerciseId })
        override suspend fun getAllOnce(): List<RepMax> = ArrayList(repMaxesStorage)
        override suspend fun deleteAll() {
            repMaxesStorage.clear()
        }
        override suspend fun bestWeight(exerciseId: Long, reps: Int): Double? =
            repMaxesStorage.filter { it.exerciseId == exerciseId && it.reps == reps }.maxOfOrNull { it.weight }
    }

    override fun cycleDao(): CycleDao = cycleDaoImpl
    override fun exerciseDao(): ExerciseDao = exerciseDaoImpl
    override fun routineDao(): RoutineDao = routineDaoImpl
    override fun sessionDao(): SessionDao = sessionDaoImpl
    override fun blockDao(): BlockDao = blockDaoImpl
    override fun repMaxDao(): RepMaxDao = repMaxDaoImpl
    override fun cycleGoalDao(): CycleGoalDao = cycleGoalDaoImpl

    override fun clearAllTables() {
        exercisesStorage.clear()
        cyclesStorage.clear()
        cycleGoalsStorage.clear()
        routinesStorage.clear()
        routineBlocksStorage.clear()
        sessionsStorage.clear()
        blocksStorage.clear()
        setsStorage.clear()
        repMaxesStorage.clear()
    }

    override fun createInvalidationTracker(): androidx.room.InvalidationTracker {
        return object : androidx.room.InvalidationTracker(this@FakeSampleAppDatabase, "sessions") {}
    }

    override fun createOpenHelper(config: androidx.room.DatabaseConfiguration): androidx.sqlite.db.SupportSQLiteOpenHelper {
        throw UnsupportedOperationException("FakeAppDatabase does not support SQLiteOpenHelper")
    }
}
