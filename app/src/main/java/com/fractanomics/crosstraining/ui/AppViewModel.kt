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
import kotlinx.coroutines.Job
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
import com.fractanomics.crosstraining.data.VoiceIngestionState
import com.fractanomics.crosstraining.data.VoiceParseResult
import com.fractanomics.crosstraining.data.ai.AiCoreManager
import com.fractanomics.crosstraining.data.ai.ParsedBlock
import com.fractanomics.crosstraining.data.voice.VoiceInputController
import com.fractanomics.crosstraining.data.voice.VoiceInputState
import com.fractanomics.crosstraining.ui.voice.VoiceWorkoutUiState
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.map
import java.time.LocalDate

/**
 * One ViewModel backing the whole app. The data set is small and entirely
 * local, so a single shared store keeps wiring simple while still exposing
 * lifecycle-aware [StateFlow]s for Compose. Every flow re-binds through
 * [DataModeManager] so switching between real and demo data updates the whole
 * UI live.
 */
data class CloudSyncResult(
    val uploadSuccess: Boolean,
    val downloadSuccess: Boolean,
    val uploadError: String? = null,
    val downloadError: String? = null
) {
    val isSuccess: Boolean get() = uploadSuccess && downloadSuccess
}

@OptIn(ExperimentalCoroutinesApi::class)
class AppViewModel(private val data: DataModeManager) : ViewModel() {

    private val repo: Repository
        get() = data.current

    private val _legacySessionRequiresReauth = MutableStateFlow(false)
    val legacySessionRequiresReauth: StateFlow<Boolean> = _legacySessionRequiresReauth.asStateFlow()

    init {
        viewModelScope.launch {
            runCatching { repo.provisionDefaultCycleIfNeeded() }
            runCatching { repo.cleanupDuplicateRoutines() }
            val persisted = runCatching { data.getPersistedAuthUser() }.getOrNull()
            if (persisted != null) {
                if (persisted.uid.contains("@")) {
                    data.clearAuthSession()
                    UserCloudSyncManager.setAuthenticatedUser(null)
                    _legacySessionRequiresReauth.value = true
                } else {
                    UserCloudSyncManager.setAuthenticatedUser(persisted)
                    val role = data.resolveRoleForUser(persisted.email)
                    data.setUserRole(role)
                }
            }
        }
    }

    fun dismissLegacyReauthPrompt() {
        _legacySessionRequiresReauth.value = false
    }

    val authUser: StateFlow<AuthUser?> = UserCloudSyncManager.userState
    val syncState: StateFlow<SyncStatus> = UserCloudSyncManager.syncState

    private val _lastSyncError = MutableStateFlow<String?>(null)
    val lastSyncError: StateFlow<String?> = _lastSyncError.asStateFlow()

    fun resetSyncStatus() {
        UserCloudSyncManager.resetSyncStatus()
        _lastSyncError.value = null
    }

    fun signUpWithEmail(email: String, pass: String, remember: Boolean = true, onResult: (Boolean, String?) -> Unit) = viewModelScope.launch {
        val res = UserCloudSyncManager.signUpWithEmail(email, pass)
        res.onSuccess {
            _legacySessionRequiresReauth.value = false
            val u = authUser.value
            if (u != null) {
                data.saveAuthSession(u.email, u.uid, u.isAnonymous, remember = remember)
            }
            UserCloudSyncManager.uploadUserData(data.realRepository)
            onResult(true, null)
        }.onFailure { err ->
            onResult(false, err.localizedMessage)
        }
    }

    fun logInWithEmail(email: String, pass: String, remember: Boolean = true, onResult: (Boolean, String?) -> Unit) = viewModelScope.launch {
        val res = UserCloudSyncManager.logInWithEmail(email, pass)
        res.onSuccess {
            _legacySessionRequiresReauth.value = false
            val u = authUser.value
            if (u != null) {
                data.saveAuthSession(u.email, u.uid, u.isAnonymous, remember = remember)
            }
            UserCloudSyncManager.downloadUserData(data.realRepository)
            onResult(true, null)
        }.onFailure { err ->
            onResult(false, err.localizedMessage)
        }
    }

    fun logInWithGoogle(idToken: String, remember: Boolean = true, onResult: (Boolean, String?) -> Unit) = viewModelScope.launch {
        val res = UserCloudSyncManager.signInWithGoogleCredential(idToken)
        res.onSuccess {
            _legacySessionRequiresReauth.value = false
            val u = authUser.value
            if (u != null) {
                data.saveAuthSession(u.email, u.uid, u.isAnonymous, remember = remember)
            }
            UserCloudSyncManager.downloadUserData(data.realRepository)
            onResult(true, null)
        }.onFailure { err ->
            onResult(false, err.localizedMessage)
        }
    }

    fun logInWithGoogleAccount(email: String, displayName: String?, remember: Boolean = true, onResult: (Boolean, String?) -> Unit) = viewModelScope.launch {
        val res = UserCloudSyncManager.logInWithGoogleAccount(email, displayName)
        res.onSuccess {
            _legacySessionRequiresReauth.value = false
            val u = authUser.value
            if (u != null) {
                data.saveAuthSession(u.email, u.uid, u.isAnonymous, remember = remember)
            }
            UserCloudSyncManager.downloadUserData(data.realRepository)
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
        _legacySessionRequiresReauth.value = false
        _lastSyncError.value = null
    }

    fun triggerCloudSync(onResult: (CloudSyncResult) -> Unit): Job {
        UserCloudSyncManager.resetSyncStatus()
        _lastSyncError.value = null
        return viewModelScope.launch {
            val uploadRes = UserCloudSyncManager.uploadUserData(data.realRepository)
            val downloadRes = UserCloudSyncManager.downloadUserData(data.realRepository)

            val uploadOk = uploadRes.isSuccess
            val downloadOk = downloadRes.isSuccess
            val uploadErr = uploadRes.exceptionOrNull()?.localizedMessage
            val downloadErr = downloadRes.exceptionOrNull()?.localizedMessage

            if (!uploadOk || !downloadOk) {
                UserCloudSyncManager.setSyncStatus(SyncStatus.ERROR)
                val err = when {
                    !uploadOk && !downloadOk ->
                        "Upload failed: ${uploadErr ?: "Unknown"}; Download failed: ${downloadErr ?: "Unknown"}"
                    !uploadOk -> "Upload failed: ${uploadErr ?: "Unknown"}"
                    !downloadOk -> "Download failed: ${downloadErr ?: "Unknown"}"
                    else -> null
                }
                _lastSyncError.value = err
            } else {
                UserCloudSyncManager.setSyncStatus(SyncStatus.SUCCESS)
                _legacySessionRequiresReauth.value = false
                _lastSyncError.value = null
            }

            val result = CloudSyncResult(
                uploadSuccess = uploadOk,
                downloadSuccess = downloadOk,
                uploadError = uploadErr,
                downloadError = downloadErr
            )
            onResult(result)
        }
    }

    @JvmName("triggerCloudSyncLegacy")
    fun triggerCloudSync(onResult: (Boolean, String?) -> Unit) = triggerCloudSync { res ->
        val success = res.isSuccess
        val err = when {
            !res.uploadSuccess && !res.downloadSuccess ->
                "Upload failed: ${res.uploadError ?: "Unknown"}; Download failed: ${res.downloadError ?: "Unknown"}"
            !res.uploadSuccess -> "Upload failed: ${res.uploadError ?: "Unknown"}"
            !res.downloadSuccess -> "Download failed: ${res.downloadError ?: "Unknown"}"
            else -> null
        }
        onResult(success, err)
    }

    fun reseedDefaults(onComplete: () -> Unit) = viewModelScope.launch {
        repo.reseedDefaults(force = true)
        onComplete()
    }

    fun recoverCloudRoutines(onResult: (Int) -> Unit) = viewModelScope.launch {
        val res = UserCloudSyncManager.recoverAllCloudRoutines(data.realRepository)
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
        UserCloudSyncManager.uploadUserData(data.realRepository)
    }

    fun saveCycleWithGoals(cycle: Cycle, goals: List<CycleGoal>, makeActive: Boolean = false) = viewModelScope.launch {
        val id = repo.saveCycleWithGoals(cycle, goals)
        if (makeActive) repo.activateCycle(id)
        UserCloudSyncManager.uploadUserData(data.realRepository)
    }

    fun deleteCycleGoal(goal: CycleGoal) = viewModelScope.launch {
        repo.deleteCycleGoal(goal)
        UserCloudSyncManager.uploadUserData(data.realRepository)
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

    // --- Voice Ingestion Subsystem --------------------------------------------

    private val _voiceIngestionState = MutableStateFlow<VoiceIngestionState>(VoiceIngestionState.Idle)
    val voiceIngestionState: StateFlow<VoiceIngestionState> = _voiceIngestionState.asStateFlow()

    /**
     * Exposes UI state mapping [voiceIngestionState] into [VoiceWorkoutUiState] for [VoiceWorkoutIngestionSheet].
     */
    val voiceWorkoutUiState: StateFlow<VoiceWorkoutUiState> =
        _voiceIngestionState.map { state ->
            when (state) {
                is VoiceIngestionState.Idle -> VoiceWorkoutUiState(voiceState = VoiceInputState.Idle)
                is VoiceIngestionState.Listening -> VoiceWorkoutUiState(
                    voiceState = VoiceInputState.Listening(state.partialTranscript),
                    transcript = state.partialTranscript,
                    rmsDb = state.rmsDb,
                    isListening = true
                )
                is VoiceIngestionState.Parsing -> VoiceWorkoutUiState(
                    voiceState = VoiceInputState.Processing(state.transcript),
                    transcript = state.transcript,
                    isProcessing = true
                )
                is VoiceIngestionState.Disambiguating -> VoiceWorkoutUiState(
                    transcript = state.transcript,
                    parsedBlocks = state.parsedBlocks,
                    disambiguationCandidates = state.ambiguousBlocks,
                    isProcessing = false
                )
                is VoiceIngestionState.Saving -> VoiceWorkoutUiState(
                    transcript = state.transcript,
                    isProcessing = true
                )
                is VoiceIngestionState.Complete -> VoiceWorkoutUiState(
                    isProcessing = false
                )
                is VoiceIngestionState.Error -> VoiceWorkoutUiState(
                    errorMessage = state.message,
                    transcript = state.lastVoiceText
                )
            }
        }.stateIn(viewModelScope, SharingStarted.Eagerly, VoiceWorkoutUiState())

    private var voiceInputController: VoiceInputController? = null
    private var voiceListenerJob: kotlinx.coroutines.Job? = null

    fun setVoiceInputController(controller: VoiceInputController?) {
        voiceInputController = controller
    }

    /**
     * Starts listening via [VoiceInputController] and pipes states into [voiceIngestionState].
     */
    fun startVoiceListening(
        controller: VoiceInputController? = null,
        sessionDate: LocalDate = LocalDate.now(),
        customAiManager: AiCoreManager? = null
    ) {
        val active = controller ?: voiceInputController
        if (active == null) {
            _voiceIngestionState.value = VoiceIngestionState.Error(
                message = "Voice input controller is not initialized",
                canRetry = false
            )
            return
        }
        voiceInputController = active

        val started = active.startListening()
        if (!started) {
            val err = active.error.value
            _voiceIngestionState.value = VoiceIngestionState.Error(
                message = err?.userMessage ?: "Microphone permission or speech recognition unavailable",
                canRetry = true
            )
            return
        }

        _voiceIngestionState.value = VoiceIngestionState.Listening()

        voiceListenerJob?.cancel()
        voiceListenerJob = viewModelScope.launch {
            active.state.collect { st ->
                when (st) {
                    is VoiceInputState.Listening -> {
                        _voiceIngestionState.value = VoiceIngestionState.Listening(
                            partialTranscript = st.partialTranscript,
                            rmsDb = active.rmsDb.value
                        )
                    }
                    is VoiceInputState.Processing -> {
                        if (st.partialTranscript.isNotBlank() && _voiceIngestionState.value is VoiceIngestionState.Listening) {
                            processVoiceTranscript(st.partialTranscript, sessionDate, customAiManager)
                        }
                    }
                    is VoiceInputState.Success -> {
                        processVoiceTranscript(st.transcript, sessionDate, customAiManager)
                        voiceListenerJob?.cancel()
                    }
                    is VoiceInputState.Error -> {
                        _voiceIngestionState.value = VoiceIngestionState.Error(
                            message = st.message,
                            canRetry = true,
                            lastVoiceText = active.transcript.value
                        )
                        voiceListenerJob?.cancel()
                    }
                    VoiceInputState.Idle -> {}
                    VoiceInputState.Initializing -> {
                        _voiceIngestionState.value = VoiceIngestionState.Listening()
                    }
                }
            }
        }
    }

    fun stopVoiceListening() {
        voiceInputController?.stopListening()
        voiceListenerJob?.cancel()
    }

    fun cancelVoiceListening() {
        voiceInputController?.cancel()
        voiceListenerJob?.cancel()
        _voiceIngestionState.value = VoiceIngestionState.Idle
    }

    /**
     * Processes transcribed voice text: extracts structured blocks, resolves exercise ambiguity,
     * and transitions to [VoiceIngestionState.Disambiguating] or auto-commits.
     */
    fun processVoiceTranscript(
        transcript: String,
        sessionDate: LocalDate = LocalDate.now(),
        customAiManager: AiCoreManager? = null
    ): Job = viewModelScope.launch {
        val trimmed = transcript.trim()
        if (trimmed.isBlank()) {
            _voiceIngestionState.value = VoiceIngestionState.Error(
                message = "No speech was recognized. Please speak clearly into the microphone.",
                canRetry = true,
                lastVoiceText = ""
            )
            return@launch
        }

        _voiceIngestionState.value = VoiceIngestionState.Parsing(trimmed)

        try {
            val parseResult = repo.parseVoiceInput(trimmed, customAiManager)

            if (parseResult.blocks.isEmpty()) {
                _voiceIngestionState.value = VoiceIngestionState.Error(
                    message = "Could not parse any workout blocks from speech.",
                    canRetry = true,
                    lastVoiceText = trimmed
                )
                return@launch
            }

            if (parseResult.ambiguousExercises.isNotEmpty()) {
                _voiceIngestionState.value = VoiceIngestionState.Disambiguating(
                    transcript = trimmed,
                    parsedBlocks = parseResult.blocks,
                    ambiguousBlocks = parseResult.ambiguousExercises
                )
            } else {
                commitVoiceSession(
                    transcript = trimmed,
                    sessionDate = sessionDate,
                    disambiguatedExercises = emptyMap(),
                    parsedBlocks = parseResult.blocks,
                    customAiManager = customAiManager
                ).join()
            }
        } catch (e: Exception) {
            _voiceIngestionState.value = VoiceIngestionState.Error(
                message = e.localizedMessage ?: "Failed to parse workout from speech",
                canRetry = true,
                lastVoiceText = trimmed
            )
        }
    }

    /**
     * Resolves an ambiguous movement name for a given block index.
     * When all ambiguous blocks are resolved, automatically commits the session to Room.
     */
    fun resolveExerciseDisambiguation(
        blockIndex: Int,
        exercise: Exercise,
        sessionDate: LocalDate = LocalDate.now(),
        customAiManager: AiCoreManager? = null
    ): Job? {
        val current = _voiceIngestionState.value
        if (current !is VoiceIngestionState.Disambiguating) return null

        val updatedResolved = current.resolvedExercises.toMutableMap()
        updatedResolved[blockIndex] = exercise

        val remainingAmbiguous = current.ambiguousBlocks.filterKeys { it !in updatedResolved }

        return if (remainingAmbiguous.isEmpty()) {
            commitVoiceSession(
                transcript = current.transcript,
                sessionDate = sessionDate,
                disambiguatedExercises = updatedResolved,
                parsedBlocks = current.parsedBlocks,
                customAiManager = customAiManager
            )
        } else {
            _voiceIngestionState.value = current.copy(
                resolvedExercises = updatedResolved
            )
            null
        }
    }

    /**
     * Commits a voice-parsed workout session atomically to Room SQLite.
     */
    fun commitVoiceSession(
        transcript: String,
        sessionDate: LocalDate = LocalDate.now(),
        disambiguatedExercises: Map<Int, Exercise> = emptyMap(),
        parsedBlocks: List<ParsedBlock>? = null,
        customAiManager: AiCoreManager? = null,
        onSuccess: ((Session) -> Unit)? = null
    ): Job = viewModelScope.launch {
        _voiceIngestionState.value = VoiceIngestionState.Saving(transcript)

        try {
            val session = repo.createSessionFromVoiceInput(
                voiceText = transcript,
                sessionDate = sessionDate,
                disambiguatedExercises = disambiguatedExercises,
                parsedBlocks = parsedBlocks,
                customAiManager = customAiManager
            )
            _voiceIngestionState.value = VoiceIngestionState.Complete(
                session = session,
                message = "Workout session persisted successfully"
            )
            onSuccess?.invoke(session)
        } catch (e: Exception) {
            _voiceIngestionState.value = VoiceIngestionState.Error(
                message = e.localizedMessage ?: "Failed to save workout session",
                canRetry = true,
                lastVoiceText = transcript
            )
        }
    }

    /**
     * Appends a live completed [BlockSet] to the active session from voice dictation.
     */
    fun appendBlockSetFromVoice(
        sessionId: Long,
        voiceText: String,
        customAiManager: AiCoreManager? = null,
        onSuccess: ((BlockSet) -> Unit)? = null
    ): Job = viewModelScope.launch {
        _voiceIngestionState.value = VoiceIngestionState.Saving(voiceText)

        try {
            val set = repo.appendBlockSetFromVoice(
                sessionId = sessionId,
                voiceText = voiceText,
                customAiManager = customAiManager
            )
            _voiceIngestionState.value = VoiceIngestionState.Complete(
                appendedSet = set,
                message = "Set logged successfully"
            )
            onSuccess?.invoke(set)
        } catch (e: Exception) {
            _voiceIngestionState.value = VoiceIngestionState.Error(
                message = e.localizedMessage ?: "Failed to log set from voice",
                canRetry = true,
                lastVoiceText = voiceText
            )
        }
    }

    fun retryLastVoiceIngestion(sessionDate: LocalDate = LocalDate.now(), customAiManager: AiCoreManager? = null): Job? {
        val current = _voiceIngestionState.value
        return if (current is VoiceIngestionState.Error && current.lastVoiceText.isNotBlank()) {
            processVoiceTranscript(current.lastVoiceText, sessionDate, customAiManager)
        } else {
            _voiceIngestionState.value = VoiceIngestionState.Idle
            null
        }
    }

    fun resetVoiceIngestionState() {
        _voiceIngestionState.value = VoiceIngestionState.Idle
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
