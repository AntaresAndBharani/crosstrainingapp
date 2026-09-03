package com.fractanomics.crosstraining.data

import androidx.room.withTransaction
import com.fractanomics.crosstraining.data.ai.AiCoreManager
import com.fractanomics.crosstraining.data.ai.ExerciseEntityGrounder
import com.fractanomics.crosstraining.data.ai.FitnessSpeechLexicon
import com.fractanomics.crosstraining.data.ai.ParsedBlock
import com.fractanomics.crosstraining.data.ai.ParsedBlockSet
import com.fractanomics.crosstraining.data.model.BlockKind
import com.fractanomics.crosstraining.data.model.BlockSet
import com.fractanomics.crosstraining.data.model.Cycle
import com.fractanomics.crosstraining.data.model.CycleGoal
import com.fractanomics.crosstraining.data.model.CycleWithGoals
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
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.withContext
import java.time.LocalDate

/**
 * Input for one block when saving a session: the block (with [SessionBlock.mainExerciseId]
 * already resolved), its sets, and an optional new rep-max to record for the
 * block's main exercise.
 */
data class BlockInsert(
    val block: SessionBlock,
    val sets: List<BlockSet>,
    val newRepMax: RepMax? = null
)

/**
 * Single point of access to the persistence layer. Hides the DAOs from the UI
 * and hosts the small amount of write-time logic (auto-creating exercises,
 * saving a session with its sets and optional rep-max, swapping the active
 * cycle).
 */
class Repository(
    private val db: AppDatabase,
    private val transactionRunner: TransactionRunner? = null,
    val aiCoreManager: AiCoreManager = AiCoreManager.DEFAULT,
    val grounder: ExerciseEntityGrounder = ExerciseEntityGrounder.DEFAULT,
    val lexicon: FitnessSpeechLexicon = FitnessSpeechLexicon.DEFAULT
) {

    internal suspend fun <R> withDatabaseTransaction(block: suspend () -> R): R {
        val runner = transactionRunner
        return if (runner != null) {
            runner.runInTransaction(block)
        } else {
            db.withTransaction(block)
        }
    }

    private val cycleDao = db.cycleDao()
    private val exerciseDao = db.exerciseDao()
    private val routineDao = db.routineDao()
    private val sessionDao = db.sessionDao()
    private val blockDao = db.blockDao()
    private val repMaxDao = db.repMaxDao()
    private val cycleGoalDao = db.cycleGoalDao()

    // --- Cycles ---------------------------------------------------------------
    val cycles: Flow<List<Cycle>> = flow {
        provisionDefaultCycleIfNeeded()
        emitAll(cycleDao.observeAll())
    }
    val activeCycle: Flow<Cycle?> = flow {
        provisionDefaultCycleIfNeeded()
        emitAll(cycleDao.observeActive())
    }
    val cycleGoals: Flow<List<CycleGoal>> = cycleGoalDao.all()

    fun cycleGoalsForCycle(cycleId: Long): Flow<List<CycleGoal>> = cycleGoalDao.byCycle(cycleId)

    suspend fun saveCycle(cycle: Cycle): Long =
        if (cycle.id == 0L) cycleDao.insert(cycle) else {
            cycleDao.update(cycle); cycle.id
        }

    suspend fun saveCycleGoal(goal: CycleGoal): Long =
        if (goal.id == 0L) cycleGoalDao.insert(goal) else {
            cycleGoalDao.update(goal); goal.id
        }

    suspend fun deleteCycleGoal(goal: CycleGoal) = cycleGoalDao.delete(goal)

    suspend fun snapshotCycleGoals(): List<CycleGoal> = cycleGoalDao.snapshot()

    suspend fun saveCycleWithGoals(cycle: Cycle, goals: List<CycleGoal>): Long =
        withDatabaseTransaction {
            val cycleId = if (cycle.id == 0L) cycleDao.insert(cycle) else {
                cycleDao.update(cycle); cycle.id
            }
            cycleGoalDao.deleteByCycle(cycleId)
            if (goals.isNotEmpty()) {
                cycleGoalDao.insertAll(goals.map { it.copy(cycleId = cycleId) })
            }
            cycleId
        }

    suspend fun deleteCycle(cycle: Cycle) = cycleDao.delete(cycle)

    /** Make [id] the only active cycle. */
    suspend fun activateCycle(id: Long) {
        cycleDao.clearActive()
        cycleDao.markActive(id)
    }

    /**
     * Startup verification check: if no training cycles exist in the database,
     * automatically provisions a default active training cycle named "General Training".
     * Covers both fresh installs and existing installs upgraded from v3.0.147.
     */
    suspend fun provisionDefaultCycleIfNeeded(): Cycle? = withDatabaseTransaction {
        val existing = cycleDao.getAllOnce()
        if (existing.isEmpty()) {
            val defaultCycle = Cycle(
                name = "General Training",
                startDate = LocalDate.now(),
                isActive = true
            )
            val id = cycleDao.insert(defaultCycle)
            defaultCycle.copy(id = id)
        } else {
            null
        }
    }

    /**
     * Ensures an active or default training cycle exists, provisioning "General Training"
     * if the database is currently empty.
     */
    suspend fun ensureDefaultCycleProvisioned(): Cycle = withDatabaseTransaction {
        val existing = cycleDao.getAllOnce()
        if (existing.isEmpty()) {
            val defaultCycle = Cycle(
                name = "General Training",
                startDate = LocalDate.now(),
                isActive = true
            )
            val id = cycleDao.insert(defaultCycle)
            defaultCycle.copy(id = id)
        } else {
            existing.find { it.isActive } ?: existing.first()
        }
    }

    // --- Exercises ------------------------------------------------------------
    val exercises: Flow<List<Exercise>> = exerciseDao.observeAll()

    suspend fun saveExercise(exercise: Exercise): Long =
        if (exercise.id == 0L) exerciseDao.insert(exercise) else {
            exerciseDao.update(exercise); exercise.id
        }

    suspend fun deleteExercise(exercise: Exercise) = exerciseDao.delete(exercise)

    /**
     * Returns the exercise with [name], creating it if it does not exist yet.
     * New exercises default to a weighted/rep-max profile unless [metricType]
     * says otherwise.
     */
    suspend fun getOrCreateExercise(
        name: String,
        category: ExerciseCategory = ExerciseCategory.BARBELL,
        metricType: MetricType = MetricType.WEIGHT
    ): Exercise {
        val trimmed = name.trim()
        exerciseDao.byName(trimmed)?.let { return it }
        val newId = exerciseDao.insert(
            Exercise(
                name = trimmed,
                category = category,
                metricType = metricType,
                unit = metricType.defaultUnit,
                tracksRepMax = metricType.tracksRepMax
            )
        )
        // insert() may IGNORE on a race; fall back to a lookup to be safe.
        return exerciseDao.byId(newId) ?: exerciseDao.byName(trimmed)!!
    }

    // --- Routines -------------------------------------------------------------
    val routines: Flow<List<Routine>> = routineDao.observeAll()
    val routinesWithBlocks: Flow<List<RoutineWithBlocks>> = routineDao.observeWithBlocks()

    suspend fun saveRoutine(routine: Routine): Long =
        if (routine.id == 0L) routineDao.insert(routine) else {
            routineDao.update(routine); routine.id
        }

    suspend fun saveRoutineWithBlocks(routine: Routine, blocks: List<RoutineBlock>): Long =
        withDatabaseTransaction {
            val existing = if (routine.id == 0L) routineDao.byName(routine.name.trim()) else null
            val targetRoutine = if (existing != null) routine.copy(id = existing.id) else routine
            val routineId = if (targetRoutine.id == 0L) routineDao.insert(targetRoutine) else {
                routineDao.update(targetRoutine)
                targetRoutine.id
            }
            routineDao.deleteBlocksForRoutine(routineId)
            if (blocks.isNotEmpty()) {
                routineDao.insertBlocks(blocks.mapIndexed { idx, b ->
                    b.copy(id = 0, routineId = routineId, position = idx)
                })
            }
            routineId
        }

    suspend fun cleanupDuplicateRoutines() = withDatabaseTransaction {
        val allRoutines = routineDao.getAllOnce()
        val grouped = allRoutines.groupBy { it.name.trim().lowercase() }
        grouped.forEach { (_, list) ->
            if (list.size > 1) {
                val primary = list.first()
                val duplicates = list.drop(1)
                duplicates.forEach { dup ->
                    routineDao.deleteBlocksForRoutine(dup.id)
                    routineDao.delete(dup)
                }
            }
        }
    }

    suspend fun deleteRoutine(routine: Routine) = routineDao.delete(routine)

    // --- Sessions -------------------------------------------------------------
    fun sessionsForCycle(cycleId: Long): Flow<List<SessionWithBlocks>> =
        sessionDao.observeByCycle(cycleId)

    val allSessions: Flow<List<SessionWithBlocks>> = sessionDao.observeAll()

    suspend fun deleteSession(session: Session) = sessionDao.deleteSession(session)

    suspend fun sessionById(id: Long): SessionWithBlocks? = sessionDao.getByIdOnce(id)

    /**
     * Persist a session with its ordered [blocks], each block's sets, and any
     * per-block new rep-maxes — all in one transaction. Blocks and sets are
     * renumbered by their list order; foreign keys (sessionId/blockId) are wired
     * up here.
     */
    suspend fun saveSession(session: Session, blocks: List<BlockInsert>): Long =
        withDatabaseTransaction {
            val sessionId = sessionDao.insertSession(session)
            blocks.forEachIndexed { blockIndex, item ->
                val blockId = blockDao.insertBlock(
                    item.block.copy(id = 0, sessionId = sessionId, position = blockIndex)
                )
                if (item.sets.isNotEmpty()) {
                    blockDao.insertSets(
                        item.sets.mapIndexed { setIndex, set ->
                            set.copy(id = 0, blockId = blockId, position = setIndex)
                        }
                    )
                }
                item.newRepMax?.let {
                    repMaxDao.insert(
                        it.copy(
                            id = 0,
                            sessionId = sessionId,
                            blockId = blockId,
                            cycleId = session.cycleId
                        )
                    )
                }
            }
            sessionId
        }

    /**
     * Update an existing [session] (its id must be set) and replace its blocks
     * and sets with [blocks]. Existing blocks are deleted (cascading to their
     * sets) and re-inserted in order. Historical rep-max records are kept; any
     * new rep-maxes in [blocks] are added.
     */
    suspend fun updateSession(session: Session, blocks: List<BlockInsert>) {
        withDatabaseTransaction {
            sessionDao.updateSession(session)
            blockDao.deleteBlocksForSession(session.id)
            blocks.forEachIndexed { blockIndex, item ->
                val blockId = blockDao.insertBlock(
                    item.block.copy(id = 0, sessionId = session.id, position = blockIndex)
                )
                if (item.sets.isNotEmpty()) {
                    blockDao.insertSets(
                        item.sets.mapIndexed { setIndex, set ->
                            set.copy(id = 0, blockId = blockId, position = setIndex)
                        }
                    )
                }
                item.newRepMax?.let {
                    repMaxDao.insert(
                        it.copy(
                            id = 0,
                            sessionId = session.id,
                            blockId = blockId,
                            cycleId = session.cycleId
                        )
                    )
                }
            }
        }
    }

    // --- Rep maxes ------------------------------------------------------------
    val allRepMaxes: Flow<List<RepMax>> = repMaxDao.observeAll()

    fun repMaxesForExercise(exerciseId: Long): Flow<List<RepMax>> =
        repMaxDao.observeForExercise(exerciseId)

    suspend fun addRepMax(repMax: RepMax): Long = repMaxDao.insert(repMax)

    suspend fun deleteRepMax(repMax: RepMax) = repMaxDao.delete(repMax)

    suspend fun recordRepMax(
        exerciseId: Long,
        reps: Int,
        weight: Double,
        date: LocalDate,
        cycleId: Long?
    ): Long = repMaxDao.insert(
        RepMax(
            exerciseId = exerciseId,
            reps = reps,
            weight = weight,
            date = date,
            cycleId = cycleId
        )
    )

    // --- Cloud Sync Getters ---------------------------------------------------
    suspend fun getAllExercisesOnce(): List<Exercise> = exerciseDao.getAllOnce()
    suspend fun getAllRoutinesWithBlocksOnce(): List<RoutineWithBlocks> = routineDao.getAllWithBlocksOnce()
    suspend fun getAllSessionsWithBlocksOnce(): List<SessionWithBlocks> =
        sessionDao.getAllSessionsOnce().mapNotNull { s -> sessionDao.getByIdOnce(s.id) }

    // --- Backup / restore -----------------------------------------------------
    /** Read the whole database into an in-memory snapshot. */
    suspend fun exportSnapshot(): BackupData = BackupData(
        cycles = cycleDao.getAllOnce(),
        exercises = exerciseDao.getAllOnce(),
        routines = routineDao.getAllOnce(),
        sessions = sessionDao.getAllSessionsOnce(),
        blocks = blockDao.getAllBlocksOnce(),
        sets = blockDao.getAllSetsOnce(),
        repMaxes = repMaxDao.getAllOnce()
    )

    /**
     * Replace all data with [data]. Tables are cleared first, then rows are
     * inserted in foreign-key order (exercises/cycles → routines → sessions →
     * blocks → sets → rep-maxes) so relationships restore intact.
     */
    suspend fun importSnapshot(data: BackupData) {
        withDatabaseTransaction {
            // Clear children before parents to respect foreign keys.
            repMaxDao.deleteAll()
            blockDao.deleteAllSets()
            blockDao.deleteAllBlocks()
            sessionDao.deleteAllSessions()
            routineDao.deleteAll()
            cycleDao.deleteAll()
            exerciseDao.deleteAll()
            // Insert parents before children.
            exerciseDao.insertAllReplace(data.exercises)
            cycleDao.insertAll(data.cycles)
            routineDao.insertAll(data.routines)
            sessionDao.insertSessions(data.sessions)
            blockDao.insertBlocks(data.blocks)
            blockDao.insertSets(data.sets)
            repMaxDao.insertAll(data.repMaxes)
        }
    }

    suspend fun reseedDefaults(force: Boolean = true) {
        SeedData.populate(exerciseDao, routineDao, cycleDao, cycleGoalDao, force)
    }

    // --- Voice Ingestion & Atomic Persistence ---------------------------------

    /**
     * Parses spoken or dictated workout text into structured [VoiceParseResult]
     * using [FitnessSpeechLexicon] for phonetic normalization, [AiCoreManager] for structured extraction,
     * and [ExerciseEntityGrounder] for detecting ambiguous movements against [ExerciseDao].
     */
    suspend fun parseVoiceInput(
        voiceText: String,
        customAiManager: AiCoreManager? = null
    ): VoiceParseResult = withContext(Dispatchers.IO) {
        val trimmed = voiceText.trim()
        if (trimmed.isBlank()) {
            return@withContext VoiceParseResult(transcript = "", blocks = emptyList())
        }

        val sanitizedText = lexicon.correct(trimmed)
        val activeAi = customAiManager ?: aiCoreManager

        val parseResult = activeAi.parseWorkoutText(sanitizedText)
        val validAiBlocks = parseResult.blocks.filter { it.name.isNotBlank() }
        val blocks = if (validAiBlocks.isNotEmpty()) {
            validAiBlocks
        } else {
            fallbackParseVoiceText(sanitizedText)
        }

        val allExercises = exerciseDao.getAllOnce()
        val ambiguousMap = mutableMapOf<Int, List<Exercise>>()

        blocks.forEachIndexed { index, block ->
            if (block.name.isNotBlank()) {
                val matches = grounder.resolveExerciseWithConfidence(block.name, allExercises)
                if (matches.size > 1 && matches.first().confidence < 1.0) {
                    ambiguousMap[index] = matches.map { it.exercise }
                }
            }
        }

        VoiceParseResult(
            transcript = sanitizedText,
            blocks = blocks,
            ambiguousExercises = ambiguousMap
        )
    }

    /**
     * Atomically creates and persists a complete workout session with its [SessionBlock]s
     * and [BlockSet]s from spoken text within [withDatabaseTransaction].
     *
     * @param voiceText Spoken or transcribed workout dictation.
     * @param sessionDate Training session date.
     * @param disambiguatedExercises User-resolved exercise mapping for ambiguous blocks.
     * @param parsedBlocks Optional pre-parsed blocks (e.g. from UI confirmation sheet).
     * @param cycleId Optional active cycle to link to.
     * @param customAiManager Optional AI manager override for testing.
     * @return The persisted [Session] entity.
     */
    suspend fun createSessionFromVoiceInput(
        voiceText: String,
        sessionDate: LocalDate = LocalDate.now(),
        disambiguatedExercises: Map<Int, Exercise> = emptyMap(),
        parsedBlocks: List<ParsedBlock>? = null,
        cycleId: Long? = null,
        customAiManager: AiCoreManager? = null
    ): Session = withContext(Dispatchers.IO) {
        val trimmed = voiceText.trim()
        require(trimmed.isNotBlank()) { "Voice input text cannot be blank" }

        val sanitizedText = lexicon.correct(trimmed)
        val activeAi = customAiManager ?: aiCoreManager

        val blocksToPersist = if (!parsedBlocks.isNullOrEmpty()) {
            parsedBlocks
        } else {
            val parseResult = activeAi.parseWorkoutText(sanitizedText)
            val validAiBlocks = parseResult.blocks.filter { it.name.isNotBlank() }
            if (validAiBlocks.isNotEmpty()) {
                validAiBlocks
            } else {
                fallbackParseVoiceText(sanitizedText)
            }
        }

        require(blocksToPersist.isNotEmpty()) {
            "Could not parse any workout blocks from voice input: \"$voiceText\""
        }

        withDatabaseTransaction {
            val resolvedCycleId = cycleId
                ?: cycleDao.getAllOnce().find { it.isActive }?.id
                ?: cycleDao.getAllOnce().firstOrNull()?.id
                ?: saveCycle(Cycle(name = "General Training", startDate = sessionDate, isActive = true))

            val sessionTitle = deriveSessionTitle(blocksToPersist, sanitizedText)
            val session = Session(
                id = 0,
                cycleId = resolvedCycleId,
                date = sessionDate,
                title = sessionTitle,
                notes = sanitizedText
            )
            val sessionId = sessionDao.insertSession(session)

            blocksToPersist.forEachIndexed { blockIndex, block ->
                val resolvedExercise = disambiguatedExercises[blockIndex]
                    ?: resolveExerciseForBlock(block)

                val blockName = if (disambiguatedExercises.containsKey(blockIndex)) {
                    disambiguatedExercises[blockIndex]!!.name
                } else if (block.name.isNotBlank()) {
                    block.name
                } else {
                    resolvedExercise?.name ?: "Block ${blockIndex + 1}"
                }
                val sessionBlock = SessionBlock(
                    id = 0,
                    sessionId = sessionId,
                    position = blockIndex,
                    name = blockName,
                    kind = block.kind,
                    format = block.format,
                    scheme = block.repScheme,
                    mainExerciseId = resolvedExercise?.id,
                    description = block.description,
                    notes = if (block.rpe != null) "RPE ${block.rpe}" else ""
                )
                val blockId = blockDao.insertBlock(sessionBlock)

                if (block.sets.isNotEmpty()) {
                    val sets = block.sets.mapIndexed { setIndex, set ->
                        BlockSet(
                            id = 0,
                            blockId = blockId,
                            position = setIndex,
                            reps = set.reps,
                            weight = set.weight,
                            metricValue = set.metricValue,
                            isWarmup = set.isWarmup,
                            notes = if (set.rpe != null) "RPE ${set.rpe}" else ""
                        )
                    }
                    blockDao.insertSets(sets)
                } else {
                    val defaultSet = BlockSet(
                        id = 0,
                        blockId = blockId,
                        position = 0,
                        reps = 1,
                        weight = null,
                        isWarmup = false
                    )
                    blockDao.insertSets(listOf(defaultSet))
                }
            }

            session.copy(id = sessionId)
        }
    }

    /**
     * Atomically parses and appends a single completed [BlockSet] to the active session block
     * from voice input during a live workout session.
     *
     * @param sessionId The active workout session ID.
     * @param voiceText Spoken set text (e.g. "Logged 5 back squats at 120 kg, RPE 8").
     * @param customAiManager Optional AI manager override for testing.
     * @return The persisted [BlockSet] with its generated ID.
     */
    suspend fun appendBlockSetFromVoice(
        sessionId: Long,
        voiceText: String,
        customAiManager: AiCoreManager? = null
    ): BlockSet = withContext(Dispatchers.IO) {
        val trimmed = voiceText.trim()
        require(trimmed.isNotBlank()) { "Voice input text cannot be blank" }

        val sanitizedText = lexicon.correct(trimmed)
        val activeAi = customAiManager ?: aiCoreManager

        withDatabaseTransaction {
            val sessionWithBlocks = sessionDao.getByIdOnce(sessionId)
                ?: throw IllegalArgumentException("Session not found with id: $sessionId")

            val parsedLiveSet = parseLiveVoiceSet(sanitizedText, activeAi)

            val existingBlocks = sessionWithBlocks.blocks.map { it.block }
            val targetBlock = resolveTargetBlockForSet(sessionId, existingBlocks, parsedLiveSet)

            val existingSets = blockDao.getSetsForBlockOnce(targetBlock.id)
            val nextPosition = existingSets.size

            val set = BlockSet(
                id = 0,
                blockId = targetBlock.id,
                position = nextPosition,
                reps = parsedLiveSet.reps,
                weight = parsedLiveSet.weight,
                metricValue = parsedLiveSet.metricValue,
                isWarmup = parsedLiveSet.isWarmup,
                isFailed = parsedLiveSet.isFailed,
                notes = parsedLiveSet.notes
            )

            val insertedId = blockDao.insertSet(set)
            set.copy(id = insertedId)
        }
    }

    private suspend fun resolveExerciseForBlock(block: ParsedBlock): Exercise? {
        if (block.name.isBlank()) return null
        val candidates = grounder.resolveExerciseWithConfidence(block.name, exerciseDao)
        return if (candidates.isNotEmpty()) {
            candidates.first().exercise
        } else {
            getOrCreateExercise(block.name)
        }
    }

    private fun deriveSessionTitle(blocks: List<ParsedBlock>, rawText: String): String {
        return when {
            blocks.size == 1 -> {
                val b = blocks.first()
                if (b.format.isNotBlank()) "${b.name} (${b.format})" else b.name
            }
            blocks.size in 2..3 -> {
                blocks.joinToString(" + ") { it.name.ifBlank { "Workout" } }
            }
            else -> "Voice Workout (${blocks.size} blocks)"
        }
    }

    private suspend fun resolveTargetBlockForSet(
        sessionId: Long,
        existingBlocks: List<SessionBlock>,
        liveSet: ParsedLiveSet
    ): SessionBlock {
        if (!liveSet.exerciseName.isNullOrBlank()) {
            val grounded = grounder.resolveBestMatch(liveSet.exerciseName, exerciseDao)
            val match = existingBlocks.find { b ->
                (grounded != null && b.mainExerciseId == grounded.id) ||
                b.name.equals(liveSet.exerciseName, ignoreCase = true) ||
                (grounded != null && b.name.equals(grounded.name, ignoreCase = true))
            }
            if (match != null) return match

            val exercise = grounded ?: getOrCreateExercise(liveSet.exerciseName)
            val newBlock = SessionBlock(
                id = 0,
                sessionId = sessionId,
                position = existingBlocks.size,
                name = exercise.name,
                kind = BlockKind.STRENGTH,
                mainExerciseId = exercise.id
            )
            val newBlockId = blockDao.insertBlock(newBlock)
            return newBlock.copy(id = newBlockId)
        }

        if (existingBlocks.isNotEmpty()) {
            return existingBlocks.last()
        }

        val initialBlock = SessionBlock(
            id = 0,
            sessionId = sessionId,
            position = 0,
            name = "Workout Block",
            kind = BlockKind.STRENGTH
        )
        val newBlockId = blockDao.insertBlock(initialBlock)
        return initialBlock.copy(id = newBlockId)
    }

    private data class ParsedLiveSet(
        val exerciseName: String? = null,
        val reps: Int = 1,
        val weight: Double? = null,
        val metricValue: Double? = null,
        val isWarmup: Boolean = false,
        val isFailed: Boolean = false,
        val notes: String = ""
    )

    private suspend fun parseLiveVoiceSet(text: String, aiManager: AiCoreManager): ParsedLiveSet {
        if (aiManager.client.isAvailable()) {
            val parseResult = aiManager.parseWorkoutText(text)
            if (parseResult.blocks.isNotEmpty()) {
                val block = parseResult.blocks.first()
                val set = block.sets.firstOrNull()
                val rpeStr = if (set?.rpe != null) "RPE ${set.rpe}" else if (block.rpe != null) "RPE ${block.rpe}" else ""
                return ParsedLiveSet(
                    exerciseName = block.name.takeIf { it.isNotBlank() && !it.equals("Workout Block", ignoreCase = true) },
                    reps = set?.reps ?: 1,
                    weight = set?.weight,
                    metricValue = set?.metricValue,
                    isWarmup = set?.isWarmup ?: false,
                    isFailed = false,
                    notes = rpeStr
                )
            }
        }

        val rpeRegex = Regex("""(?i)\b(?:rpe|r\s*pay)\s*(\d+(?:\.\d+)?)\b""")
        val rpeMatch = rpeRegex.find(text)
        val rpeNote = if (rpeMatch != null) "RPE ${rpeMatch.groupValues[1]}" else ""

        val isWarmup = text.contains("warmup", ignoreCase = true) || text.contains("warm up", ignoreCase = true)
        val isFailed = text.contains("fail", ignoreCase = true) || text.contains("missed", ignoreCase = true)

        val weightRegex = Regex("""(?i)(?:at|@)?\s*(\d+(?:\.\d+)?)\s*(?:kg|lbs|kilos|k)\b""")
        val weight = weightRegex.find(text)?.groupValues?.get(1)?.toDoubleOrNull()

        val repsWithUnitRegex = Regex("""(?i)\b(\d+)\s*(?:reps?|rep)\b""")
        val repsWithPrefixRegex = Regex("""(?i)(?:\blogged\s+)?(\d+)\s+(?:reps?\s+)?([a-zA-Z\s]+?)\s+(?:at|@)""")
        val repsBeforeAtRegex = Regex("""(?i)\b(\d+)\s*(?:at|@)""")

        val repsMatch = repsWithUnitRegex.find(text)
            ?: repsWithPrefixRegex.find(text)
            ?: repsBeforeAtRegex.find(text)

        val reps = repsMatch?.groupValues?.get(1)?.toIntOrNull() ?: 1

        var exerciseName: String? = null
        val exMatch = repsWithPrefixRegex.find(text)
        if (exMatch != null) {
            val ex = exMatch.groupValues[2].trim()
            if (ex.isNotBlank() && !ex.equals("reps", ignoreCase = true)) {
                exerciseName = ex
            }
        } else {
            val candidates = grounder.resolveExercise(text, exerciseDao)
            if (candidates.isNotEmpty()) {
                exerciseName = candidates.first().name
            }
        }

        return ParsedLiveSet(
            exerciseName = exerciseName,
            reps = reps,
            weight = weight,
            isWarmup = isWarmup,
            isFailed = isFailed,
            notes = rpeNote
        )
    }

    private fun fallbackParseVoiceText(sanitizedText: String): List<ParsedBlock> {
        val complexRegex = Regex("""(?i)(?:(\d+)\s+)?([a-zA-Z\s]+?)\s+\+\s+(?:(\d+)\s+)?([a-zA-Z\s]+?)(?:\s+at\s+(\d+(?:\.\d+)?)\s*(?:kg|lbs|kilos)?)?(?:,\s*(\d+)\s*sets)?(?:\s+on\s+a\s+(\d+)\s*min(?:ute)?\s*timer)?""")
        val complexMatch = complexRegex.find(sanitizedText)
        if (complexMatch != null) {
            val mov1 = complexMatch.groupValues[2].trim()
            val mov2 = complexMatch.groupValues[4].trim()
            val weight = complexMatch.groupValues[5].toDoubleOrNull()
            val numSets = complexMatch.groupValues[6].toIntOrNull() ?: 1
            val timerMins = complexMatch.groupValues[7].toIntOrNull()
            val formatStr = if (timerMins != null) "E${timerMins}MOM" else ""

            val sets = List(numSets) {
                ParsedBlockSet(reps = 1, weight = weight)
            }

            return listOf(
                ParsedBlock(
                    name = "$mov1 + $mov2",
                    kind = BlockKind.STRENGTH,
                    format = formatStr,
                    repScheme = "${numSets}x1",
                    sets = sets,
                    movements = listOf(mov1, mov2),
                    description = sanitizedText
                )
            )
        }

        val emomAmrapRegex = Regex("""(?i)\b(EMOM|AMRAP|E\d+MOM)\s*(\d+)?\s*(?:of\s+)?(?:(\d+)\s+)?([a-zA-Z\s]+)""")
        val eaMatch = emomAmrapRegex.find(sanitizedText)
        if (eaMatch != null) {
            val formatType = eaMatch.groupValues[1].uppercase()
            val duration = eaMatch.groupValues[2].trim()
            val reps = eaMatch.groupValues[3].toIntOrNull() ?: 1
            val exName = eaMatch.groupValues[4].trim()
            val fullFormat = if (duration.isNotBlank()) "$formatType $duration" else formatType

            return listOf(
                ParsedBlock(
                    name = exName.ifBlank { "Metcon" },
                    kind = BlockKind.METCON,
                    format = fullFormat,
                    repScheme = "${reps} reps",
                    sets = listOf(ParsedBlockSet(reps = reps)),
                    description = sanitizedText
                )
            )
        }

        val setsxRepsRegex = Regex("""(?i)(?:(\d+)\s*sets?\s*(?:of\s*)?(\d+)|(?:(\d+)\s*[xX*]\s*(\d+)))\s+(.+?)(?:\s+(?:at|@)\s+(\d+(?:\.\d+)?)\s*(?:kg|lbs|kilos)?|$|\s*$)""")
        val srMatch = setsxRepsRegex.find(sanitizedText)
        if (srMatch != null) {
            val numSets = srMatch.groupValues[1].toIntOrNull() ?: srMatch.groupValues[3].toIntOrNull() ?: 1
            val numReps = srMatch.groupValues[2].toIntOrNull() ?: srMatch.groupValues[4].toIntOrNull() ?: 1
            val exName = srMatch.groupValues[5].trim()
            val weight = srMatch.groupValues[6].toDoubleOrNull()

            val sets = List(numSets) {
                ParsedBlockSet(reps = numReps, weight = weight)
            }

            return listOf(
                ParsedBlock(
                    name = exName,
                    kind = BlockKind.STRENGTH,
                    repScheme = "${numSets}x${numReps}",
                    sets = sets,
                    description = sanitizedText
                )
            )
        }

        return listOf(
            ParsedBlock(
                name = sanitizedText.take(40),
                kind = BlockKind.STRENGTH,
                sets = listOf(ParsedBlockSet(reps = 1)),
                description = sanitizedText
            )
        )
    }
}
