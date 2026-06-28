package com.fractanomics.crosstraining.data

import com.fractanomics.crosstraining.data.model.Cycle
import com.fractanomics.crosstraining.data.model.Exercise
import com.fractanomics.crosstraining.data.model.ExerciseCategory
import com.fractanomics.crosstraining.data.model.MetricType
import com.fractanomics.crosstraining.data.model.RepMax
import com.fractanomics.crosstraining.data.model.Routine
import com.fractanomics.crosstraining.data.model.Session
import com.fractanomics.crosstraining.data.model.SessionSet
import com.fractanomics.crosstraining.data.model.SessionWithSets
import kotlinx.coroutines.flow.Flow
import java.time.LocalDate

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

    suspend fun saveRoutine(routine: Routine): Long =
        if (routine.id == 0L) routineDao.insert(routine) else {
            routineDao.update(routine); routine.id
        }

    suspend fun deleteRoutine(routine: Routine) = routineDao.delete(routine)

    // --- Sessions -------------------------------------------------------------
    fun sessionsForCycle(cycleId: Long): Flow<List<SessionWithSets>> =
        sessionDao.observeByCycle(cycleId)

    val allSessions: Flow<List<SessionWithSets>> = sessionDao.observeAll()

    fun sessionsForRoutine(routineId: Long): Flow<List<SessionWithSets>> =
        sessionDao.observeByRoutine(routineId)

    fun sessionsForExercise(exerciseId: Long): Flow<List<SessionWithSets>> =
        sessionDao.observeByExercise(exerciseId)

    suspend fun deleteSession(session: Session) = sessionDao.deleteSession(session)

    /**
     * Persist a session, its sets, and (optionally) a new rep-max for the main
     * exercise in one go. [sets] are renumbered by their list order.
     */
    suspend fun saveSession(
        session: Session,
        sets: List<SessionSet>,
        newRepMax: RepMax? = null
    ): Long {
        val sessionId = sessionDao.insertSession(session)
        if (sets.isNotEmpty()) {
            sessionDao.insertSets(
                sets.mapIndexed { index, set ->
                    set.copy(id = 0, sessionId = sessionId, position = index)
                }
            )
        }
        newRepMax?.let {
            repMaxDao.insert(it.copy(id = 0, sessionId = sessionId, cycleId = session.cycleId))
        }
        return sessionId
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
}
