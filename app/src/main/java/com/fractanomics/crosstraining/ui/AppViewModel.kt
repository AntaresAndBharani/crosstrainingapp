package com.fractanomics.crosstraining.ui

import android.content.ContentResolver
import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.fractanomics.crosstraining.data.BackupCsv
import com.fractanomics.crosstraining.data.BlockInsert
import com.fractanomics.crosstraining.data.DataModeManager
import com.fractanomics.crosstraining.data.Repository
import com.fractanomics.crosstraining.data.model.BlockKind
import com.fractanomics.crosstraining.data.model.BlockSet
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
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import com.fractanomics.crosstraining.data.firebase.AuthUser
import com.fractanomics.crosstraining.data.firebase.FirebaseSyncManager
import com.fractanomics.crosstraining.data.firebase.SharedWorkoutPayload
import com.fractanomics.crosstraining.data.firebase.SyncStatus
import com.fractanomics.crosstraining.data.firebase.UserCloudSyncManager
import com.fractanomics.crosstraining.ui.components.PasswordResetErrorMapper
import kotlinx.coroutines.flow.MutableStateFlow
import java.time.LocalDate

/**
 * One ViewModel backing the whole app. The data set is small and entirely
 * local, so a single shared store keeps wiring simple while still exposing
 * lifecycle-aware [StateFlow]s for Compose. Every flow re-binds through
 * [DataModeManager] so switching between real and demo data updates the whole
 * UI live.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class AppViewModel(private val data: DataModeManager) : ViewModel() {

    private val repo: Repository
        get() = data.current

    init {
        viewModelScope.launch {
            runCatching { repo.cleanupDuplicateRoutines() }
            val persisted = runCatching { data.getPersistedAuthUser() }.getOrNull()
            if (persisted != null) {
                UserCloudSyncManager.setAuthenticatedUser(persisted)
                val role = data.resolveRoleForUser(persisted.email)
                data.setUserRole(role)
            }
        }
    }

    val authUser: StateFlow<AuthUser?> = UserCloudSyncManager.userState
    val syncState: StateFlow<SyncStatus> = UserCloudSyncManager.syncState

    fun signUpWithEmail(email: String, pass: String, remember: Boolean = true, onResult: (Boolean, String?) -> Unit) = viewModelScope.launch {
        val res = UserCloudSyncManager.signUpWithEmail(email, pass)
        res.onSuccess {
            val u = authUser.value
            if (u != null) {
                data.saveAuthSession(u.email, u.uid, u.isAnonymous, remember = remember)
            }
            UserCloudSyncManager.uploadUserData(repo)
            onResult(true, null)
        }.onFailure { err ->
            onResult(false, err.localizedMessage)
        }
    }

    fun logInWithEmail(email: String, pass: String, remember: Boolean = true, onResult: (Boolean, String?) -> Unit) = viewModelScope.launch {
        val res = UserCloudSyncManager.logInWithEmail(email, pass)
        res.onSuccess {
            val u = authUser.value
            if (u != null) {
                data.saveAuthSession(u.email, u.uid, u.isAnonymous, remember = remember)
            }
            UserCloudSyncManager.downloadUserData(repo)
            onResult(true, null)
        }.onFailure { err ->
            onResult(false, err.localizedMessage)
        }
    }

    fun logInWithGoogle(idToken: String, remember: Boolean = true, onResult: (Boolean, String?) -> Unit) = viewModelScope.launch {
        val res = UserCloudSyncManager.signInWithGoogleCredential(idToken)
        res.onSuccess {
            val u = authUser.value
            if (u != null) {
                data.saveAuthSession(u.email, u.uid, u.isAnonymous, remember = remember)
            }
            UserCloudSyncManager.downloadUserData(repo)
            onResult(true, null)
        }.onFailure { err ->
            onResult(false, err.localizedMessage)
        }
    }

    fun logInWithGoogleAccount(email: String, displayName: String?, remember: Boolean = true, onResult: (Boolean, String?) -> Unit) = viewModelScope.launch {
        val res = UserCloudSyncManager.logInWithGoogleAccount(email, displayName)
        res.onSuccess {
            val u = authUser.value
            if (u != null) {
                data.saveAuthSession(u.email, u.uid, u.isAnonymous, remember = remember)
            }
            UserCloudSyncManager.downloadUserData(repo)
            onResult(true, null)
        }.onFailure { err ->
            onResult(false, err.localizedMessage)
        }
    }

    fun sendPasswordReset(email: String, onResult: (Boolean, String?) -> Unit) = viewModelScope.launch {
        val sanitizedEmail = email.trim().lowercase()
        val res = UserCloudSyncManager.sendPasswordReset(sanitizedEmail)
        res.fold(
            onSuccess = { onResult(true, null) },
            onFailure = { error ->
                val uiMessage = PasswordResetErrorMapper.map(error)
                onResult(false, uiMessage)
            }
        )
    }

    fun signOut() {
        UserCloudSyncManager.signOut()
        data.clearAuthSession()
    }

    fun triggerCloudSync(onResult: (Boolean, String?) -> Unit) = viewModelScope.launch {
        val uploadRes = UserCloudSyncManager.uploadUserData(repo)
        val downloadRes = UserCloudSyncManager.downloadUserData(repo)
        val success = uploadRes.isSuccess || downloadRes.isSuccess
        val err = uploadRes.exceptionOrNull()?.localizedMessage ?: downloadRes.exceptionOrNull()?.localizedMessage
        onResult(success, err)
    }

    fun reseedDefaults(onComplete: () -> Unit) = viewModelScope.launch {
        repo.reseedDefaults(force = true)
        onComplete()
    }

    fun recoverCloudRoutines(onResult: (Int) -> Unit) = viewModelScope.launch {
        val res = UserCloudSyncManager.recoverAllCloudRoutines(repo)
        onResult(res.getOrDefault(0))
    }

    val cycles: StateFlow<List<Cycle>> =
        data.repositoryFlow.flatMapLatest { it.cycles }.stateInDefault(emptyList())
    val activeCycle: StateFlow<Cycle?> =
        data.repositoryFlow.flatMapLatest { it.activeCycle }.stateInDefault(null)
    val cycleGoals: StateFlow<List<CycleGoal>> =
        data.repositoryFlow.flatMapLatest { it.cycleGoals }.stateInDefault(emptyList())
    val exercises: StateFlow<List<Exercise>> =
        data.repositoryFlow.flatMapLatest { it.exercises }.stateInDefault(emptyList())

    val routines: StateFlow<List<Routine>> =
        data.repositoryFlow.flatMapLatest { it.routines }.stateInDefault(emptyList())
    val routinesWithBlocks: StateFlow<List<RoutineWithBlocks>> =
        data.repositoryFlow.flatMapLatest { it.routinesWithBlocks }.stateInDefault(emptyList())
    val sessions: StateFlow<List<SessionWithBlocks>> =
        data.repositoryFlow.flatMapLatest { it.allSessions }.stateInDefault(emptyList())
    val repMaxes: StateFlow<List<RepMax>> =
        data.repositoryFlow.flatMapLatest { it.allRepMaxes }.stateInDefault(emptyList())

    private fun <T> kotlinx.coroutines.flow.Flow<T>.stateInDefault(initial: T): StateFlow<T> =
        stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), initial)

    init {
        // Upgrade the demo dataset if the generator changed since it was seeded.
        viewModelScope.launch { data.refreshDemoIfStale() }
    }

    // --- Demo mode --------------------------------------------------------------
    val demoMode: StateFlow<Boolean> = data.demoMode

    fun setDemoMode(enabled: Boolean) = viewModelScope.launch { data.setDemoMode(enabled) }

    fun resetDemoData() = viewModelScope.launch { data.resetDemoData() }

    // --- Theme mode -------------------------------------------------------------
    val themeMode: StateFlow<com.fractanomics.crosstraining.ui.theme.AppThemeMode> = data.themeMode

    fun setThemeMode(mode: com.fractanomics.crosstraining.ui.theme.AppThemeMode) {
        data.setThemeMode(mode)
    }

    // --- User Role (Athlete vs Coach) --------------------------------------------
    val userRole: StateFlow<com.fractanomics.crosstraining.data.model.UserRole> = data.userRole

    fun setUserRole(role: com.fractanomics.crosstraining.data.model.UserRole) {
        data.setUserRole(role)
    }

    // --- Cycles ---------------------------------------------------------------
    fun saveCycle(cycle: Cycle, makeActive: Boolean = false) = viewModelScope.launch {
        val id = repo.saveCycle(cycle)
        if (makeActive) repo.activateCycle(id)
        UserCloudSyncManager.uploadUserData(repo)
    }

    fun saveCycleWithGoals(cycle: Cycle, goals: List<CycleGoal>, makeActive: Boolean = false) = viewModelScope.launch {
        val id = repo.saveCycleWithGoals(cycle, goals)
        if (makeActive) repo.activateCycle(id)
        UserCloudSyncManager.uploadUserData(repo)
    }

    fun deleteCycleGoal(goal: CycleGoal) = viewModelScope.launch {
        repo.deleteCycleGoal(goal)
        UserCloudSyncManager.uploadUserData(repo)
    }

    fun activateCycle(id: Long) = viewModelScope.launch { repo.activateCycle(id) }

    fun deleteCycle(cycle: Cycle) = viewModelScope.launch { repo.deleteCycle(cycle) }

    // --- Exercises ------------------------------------------------------------
    fun saveExercise(exercise: Exercise) = viewModelScope.launch { repo.saveExercise(exercise) }

    fun deleteExercise(exercise: Exercise) = viewModelScope.launch { repo.deleteExercise(exercise) }

    // --- Routines -------------------------------------------------------------
    fun saveRoutine(routine: Routine) = viewModelScope.launch { repo.saveRoutine(routine) }

    fun saveRoutineWithBlocks(routine: Routine, blocks: List<RoutineBlock>) =
        viewModelScope.launch { repo.saveRoutineWithBlocks(routine, blocks) }

    fun deleteRoutine(routine: Routine) = viewModelScope.launch { repo.deleteRoutine(routine) }

    // --- Rep maxes ------------------------------------------------------------
    fun recordRepMax(exerciseId: Long, reps: Int, weight: Double, date: LocalDate, cycleId: Long?) =
        viewModelScope.launch { repo.recordRepMax(exerciseId, reps, weight, date, cycleId) }

    fun deleteRepMax(repMax: RepMax) = viewModelScope.launch { repo.deleteRepMax(repMax) }

    // --- Sessions -------------------------------------------------------------
    fun deleteSession(session: Session) = viewModelScope.launch { repo.deleteSession(session) }

    /**
     * Save a new multi-block session. Each block's main exercise is resolved
     * from an existing id or a free-text name (created if missing).
     */
    fun saveSession(draft: SessionDraft) = viewModelScope.launch {
        repo.saveSession(draft.toSession(), buildBlockInserts(draft))
    }

    /** Update an existing session in place, replacing its blocks/sets. */
    fun updateSession(sessionId: Long, draft: SessionDraft) = viewModelScope.launch {
        repo.updateSession(draft.toSession(sessionId), buildBlockInserts(draft))
    }

    private fun SessionDraft.toSession(id: Long = 0): Session =
        Session(id = id, cycleId = cycleId, date = date, title = title, notes = notes)

    /** Resolve each block's exercise (creating new ones) and map drafts to entities. */
    private suspend fun buildBlockInserts(draft: SessionDraft): List<BlockInsert> =
        draft.blocks.map { bd ->
            val exerciseId: Long? = when {
                !bd.newExerciseName.isNullOrBlank() -> repo.getOrCreateExercise(bd.newExerciseName).id
                bd.existingExerciseId != null -> bd.existingExerciseId
                else -> null
            }
            val repMax: RepMax? =
                if (exerciseId != null && bd.newRepMaxReps != null && bd.newRepMaxWeight != null) {
                    RepMax(
                        exerciseId = exerciseId,
                        reps = bd.newRepMaxReps,
                        weight = bd.newRepMaxWeight,
                        date = draft.date
                    )
                } else null
            BlockInsert(
                block = SessionBlock(
                    sessionId = 0,
                    position = 0,
                    name = bd.name,
                    kind = bd.kind,
                    format = bd.format,
                    scheme = bd.scheme,
                    mainExerciseId = exerciseId,
                    routineId = bd.routineId,
                    description = bd.description,
                    resultText = bd.resultText,
                    resultValue = bd.resultValue
                ),
                sets = bd.sets.map { sd ->
                    BlockSet(
                        blockId = 0,
                        position = 0,
                        groupIndex = sd.groupIndex,
                        reps = sd.reps,
                        weight = sd.weight,
                        metricValue = sd.metricValue,
                        isWarmup = sd.isWarmup,
                        isFailed = sd.isFailed
                    )
                },
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

    val communityWorkouts = MutableStateFlow<List<SharedWorkoutPayload>>(emptyList())

    fun fetchCommunityWorkouts() = viewModelScope.launch {
        communityWorkouts.value = FirebaseSyncManager.fetchCommunityWorkouts()
    }

    fun shareRoutine(rwb: RoutineWithBlocks, onComplete: (String) -> Unit) = viewModelScope.launch {
        val exList = exercises.value
        val exMap = exList.associateBy({ it.id }, { it.name })
        val code = FirebaseSyncManager.publishRoutine(rwb.routine, rwb.blocks, exMap)
        onComplete(code)
    }

    fun importSharedWorkout(payload: SharedWorkoutPayload, onComplete: (Boolean) -> Unit) = viewModelScope.launch {
        try {
            val targetRoutine = Routine(
                name = payload.routineName,
                description = payload.description,
                defaultFormat = payload.defaultFormat
            )

            val blocks = payload.blocks.mapIndexed { idx, sb ->
                val exIds = sb.exerciseNamesCsv.split(",")
                    .mapNotNull { nameStr -> nameStr.trim().takeIf { it.isNotBlank() } }
                    .map { exName -> repo.getOrCreateExercise(exName).id }

                RoutineBlock(
                    routineId = 0,
                    position = idx,
                    name = sb.name,
                    kind = runCatching { BlockKind.valueOf(sb.kind) }.getOrDefault(BlockKind.WEIGHTLIFTING),
                    format = sb.format,
                    setsCount = sb.setsCount,
                    targetRepsScheme = sb.targetRepsScheme,
                    exerciseIdsCsv = exIds.joinToString(","),
                    notes = sb.notes
                )
            }

            repo.saveRoutineWithBlocks(targetRoutine, blocks)
            onComplete(true)
        } catch (e: Exception) {
            onComplete(false)
        }
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
        fun factory(data: DataModeManager): ViewModelProvider.Factory =
            object : ViewModelProvider.Factory {
                @Suppress("UNCHECKED_CAST")
                override fun <T : ViewModel> create(modelClass: Class<T>): T =
                    AppViewModel(data) as T
            }
    }
}
