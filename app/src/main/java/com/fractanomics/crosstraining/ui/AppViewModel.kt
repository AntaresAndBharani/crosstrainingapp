package com.fractanomics.crosstraining.ui

import android.content.ContentResolver
import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.fractanomics.crosstraining.data.BackupCsv
import com.fractanomics.crosstraining.data.Repository
import com.fractanomics.crosstraining.data.model.Cycle
import com.fractanomics.crosstraining.data.model.Exercise
import com.fractanomics.crosstraining.data.model.ExerciseCategory
import com.fractanomics.crosstraining.data.model.MetricType
import com.fractanomics.crosstraining.data.model.RepMax
import com.fractanomics.crosstraining.data.model.Routine
import com.fractanomics.crosstraining.data.model.Session
import com.fractanomics.crosstraining.data.model.SessionSet
import com.fractanomics.crosstraining.data.model.SessionWithSets
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.time.LocalDate

/**
 * One ViewModel backing the whole app. The data set is small and entirely
 * local, so a single shared store keeps wiring simple while still exposing
 * lifecycle-aware [StateFlow]s for Compose.
 */
class AppViewModel(private val repo: Repository) : ViewModel() {

    val cycles: StateFlow<List<Cycle>> =
        repo.cycles.stateInDefault(emptyList())
    val activeCycle: StateFlow<Cycle?> =
        repo.activeCycle.stateInDefault(null)
    val exercises: StateFlow<List<Exercise>> =
        repo.exercises.stateInDefault(emptyList())
    val routines: StateFlow<List<Routine>> =
        repo.routines.stateInDefault(emptyList())
    val sessions: StateFlow<List<SessionWithSets>> =
        repo.allSessions.stateInDefault(emptyList())
    val repMaxes: StateFlow<List<RepMax>> =
        repo.allRepMaxes.stateInDefault(emptyList())

    private fun <T> kotlinx.coroutines.flow.Flow<T>.stateInDefault(initial: T): StateFlow<T> =
        stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), initial)

    // --- Cycles ---------------------------------------------------------------
    fun saveCycle(cycle: Cycle, makeActive: Boolean = false) = viewModelScope.launch {
        val id = repo.saveCycle(cycle)
        if (makeActive) repo.activateCycle(id)
    }

    fun activateCycle(id: Long) = viewModelScope.launch { repo.activateCycle(id) }

    fun deleteCycle(cycle: Cycle) = viewModelScope.launch { repo.deleteCycle(cycle) }

    // --- Exercises ------------------------------------------------------------
    fun saveExercise(exercise: Exercise) = viewModelScope.launch { repo.saveExercise(exercise) }

    fun deleteExercise(exercise: Exercise) = viewModelScope.launch { repo.deleteExercise(exercise) }

    // --- Routines -------------------------------------------------------------
    fun saveRoutine(routine: Routine) = viewModelScope.launch { repo.saveRoutine(routine) }

    fun deleteRoutine(routine: Routine) = viewModelScope.launch { repo.deleteRoutine(routine) }

    // --- Rep maxes ------------------------------------------------------------
    fun recordRepMax(exerciseId: Long, reps: Int, weight: Double, date: LocalDate, cycleId: Long?) =
        viewModelScope.launch { repo.recordRepMax(exerciseId, reps, weight, date, cycleId) }

    fun deleteRepMax(repMax: RepMax) = viewModelScope.launch { repo.deleteRepMax(repMax) }

    // --- Sessions -------------------------------------------------------------
    fun deleteSession(session: Session) = viewModelScope.launch { repo.deleteSession(session) }

    /**
     * Save a session. If [newExerciseName] is provided it is resolved (created
     * if missing) and used as the main exercise. If [newRepMaxReps]/[newRepMaxWeight]
     * are provided a rep-max is recorded for that exercise.
     */
    fun saveSession(
        cycleId: Long,
        routineId: Long?,
        existingExerciseId: Long?,
        newExerciseName: String?,
        date: LocalDate,
        format: String,
        repScheme: String,
        notes: String,
        sets: List<SessionSet>,
        newRepMaxReps: Int?,
        newRepMaxWeight: Double?
    ) = viewModelScope.launch {
        val exerciseId: Long? = when {
            !newExerciseName.isNullOrBlank() ->
                repo.getOrCreateExercise(newExerciseName).id
            existingExerciseId != null -> existingExerciseId
            else -> null
        }

        val repMax: RepMax? =
            if (exerciseId != null && newRepMaxReps != null && newRepMaxWeight != null) {
                RepMax(
                    exerciseId = exerciseId,
                    reps = newRepMaxReps,
                    weight = newRepMaxWeight,
                    date = date,
                    cycleId = cycleId
                )
            } else null

        repo.saveSession(
            session = Session(
                cycleId = cycleId,
                routineId = routineId,
                mainExerciseId = exerciseId,
                date = date,
                format = format,
                repScheme = repScheme,
                notes = notes
            ),
            sets = sets,
            newRepMax = repMax
        )
    }

    // --- Backup / restore -----------------------------------------------------
    /** Export the whole database as a CSV backup to [uri]. */
    fun exportBackup(resolver: ContentResolver, uri: Uri, onResult: (Boolean) -> Unit) =
        viewModelScope.launch {
            val ok = runCatching {
                val csv = BackupCsv.encode(repo.exportSnapshot())
                withContext(Dispatchers.IO) {
                    resolver.openOutputStream(uri, "rwt")?.use { out ->
                        out.write(csv.toByteArray(Charsets.UTF_8))
                    } ?: error("Could not open output stream")
                }
            }.isSuccess
            onResult(ok)
        }

    /** Replace all data with the CSV backup at [uri]. */
    fun importBackup(resolver: ContentResolver, uri: Uri, onResult: (Boolean) -> Unit) =
        viewModelScope.launch {
            val ok = runCatching {
                val text = withContext(Dispatchers.IO) {
                    resolver.openInputStream(uri)?.use { it.readBytes().toString(Charsets.UTF_8) }
                        ?: error("Could not open input stream")
                }
                repo.importSnapshot(BackupCsv.decode(text))
            }.isSuccess
            onResult(ok)
        }

    /** Create an exercise from a free-text name (used by quick-add flows). */
    fun quickAddExercise(
        name: String,
        category: ExerciseCategory,
        metricType: MetricType
    ) = viewModelScope.launch {
        repo.getOrCreateExercise(name, category, metricType)
    }

    companion object {
        fun factory(repo: Repository): ViewModelProvider.Factory =
            object : ViewModelProvider.Factory {
                @Suppress("UNCHECKED_CAST")
                override fun <T : ViewModel> create(modelClass: Class<T>): T =
                    AppViewModel(repo) as T
            }
    }
}
