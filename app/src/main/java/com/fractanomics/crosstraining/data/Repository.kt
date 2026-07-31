package com.fractanomics.crosstraining.data

import androidx.room.withTransaction
import com.fractanomics.crosstraining.data.model.BlockSet
import com.fractanomics.crosstraining.data.model.Cycle
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
import kotlinx.coroutines.flow.Flow
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
class Repository(private val db: AppDatabase) {

    private val cycleDao = db.cycleDao()
    private val exerciseDao = db.exerciseDao()
    private val routineDao = db.routineDao()
    private val sessionDao = db.sessionDao()
    private val blockDao = db.blockDao()
    private val repMaxDao = db.repMaxDao()

    // --- Cycles ---------------------------------------------------------------
    val cycles: Flow<List<Cycle>> = cycleDao.observeAll()
    val activeCycle: Flow<Cycle?> = cycleDao.observeActive()

    suspend fun saveCycle(cycle: Cycle): Long =
        if (cycle.id == 0L) cycleDao.insert(cycle) else {
            cycleDao.update(cycle); cycle.id
        }

    suspend fun deleteCycle(cycle: Cycle) = cycleDao.delete(cycle)

    /** Make [id] the only active cycle. */
    suspend fun activateCycle(id: Long) {
        cycleDao.clearActive()
        cycleDao.markActive(id)
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
        db.withTransaction {
            val routineId = if (routine.id == 0L) routineDao.insert(routine) else {
                routineDao.update(routine)
                routine.id
            }
            routineDao.deleteBlocksForRoutine(routineId)
            if (blocks.isNotEmpty()) {
                routineDao.insertBlocks(blocks.mapIndexed { idx, b ->
                    b.copy(id = 0, routineId = routineId, position = idx)
                })
            }
            routineId
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
        db.withTransaction {
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
        db.withTransaction {
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
        db.withTransaction {
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
        SeedData.populate(exerciseDao, routineDao, cycleDao, force)
    }
}
