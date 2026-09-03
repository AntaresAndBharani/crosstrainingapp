package com.fractanomics.crosstraining.data.firebase

import com.fractanomics.crosstraining.BuildConfig
import com.fractanomics.crosstraining.data.Repository
import com.fractanomics.crosstraining.data.model.BlockKind
import com.fractanomics.crosstraining.data.model.BlockSet
import com.fractanomics.crosstraining.data.model.Cycle
import com.fractanomics.crosstraining.data.model.Exercise
import com.fractanomics.crosstraining.data.model.ExerciseCategory
import com.fractanomics.crosstraining.data.model.MetricType
import com.fractanomics.crosstraining.data.model.RepMax
import com.fractanomics.crosstraining.data.model.Routine
import com.fractanomics.crosstraining.data.model.RoutineBlock
import com.fractanomics.crosstraining.data.model.Session
import com.fractanomics.crosstraining.data.model.SessionBlock
import com.fractanomics.crosstraining.data.model.UserRole
import com.google.firebase.auth.EmailAuthProvider
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.FirebaseUser
import com.google.firebase.auth.GoogleAuthProvider
import com.google.firebase.firestore.DocumentReference
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.supervisorScope
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withTimeout
import kotlinx.coroutines.withTimeoutOrNull
import java.time.LocalDate

data class AuthUser(
    val uid: String,
    val email: String?,
    val isAnonymous: Boolean
)

enum class SyncStatus { IDLE, SYNCING, SUCCESS, ERROR }

object UserCloudSyncManager {

    private val auth by lazy { FirebaseAuth.getInstance() }
    private val firestore by lazy { FirebaseFirestore.getInstance() }

    private val _userState = MutableStateFlow<AuthUser?>(null)
    val userState: StateFlow<AuthUser?> = _userState

    private val _syncState = MutableStateFlow(SyncStatus.IDLE)
    val syncState: StateFlow<SyncStatus> = _syncState

    val currentEnv: String
        get() = runCatching { BuildConfig.APP_ENV }.getOrDefault("snapshot")

    private fun userDoc(uid: String) =
        firestore.collection("environments").document(currentEnv).collection("users").document(uid)

    internal var authUidProviderForTesting: (() -> String?)? = null
    internal var asyncAuthUidProviderForTesting: (suspend () -> String?)? = null
    internal var documentReaderForTesting: (suspend (userId: String, collectionName: String) -> List<Map<String, Any>>?)? = null
    internal var authStateUserProviderForTesting: (suspend (timeoutMs: Long) -> FirebaseUser?)? = null

    val currentUserId: String
        get() {
            val user = _userState.value
            val authUid = authUidProviderForTesting?.invoke()
                ?: runCatching { auth.currentUser?.uid }.getOrNull()
            if (user != null && user.uid.isNotBlank()) {
                if (authUid != null && !user.isAnonymous && user.uid != authUid) {
                    return ""
                }
                return user.uid.replace("/", "_")
            }
            return authUid?.replace("/", "_") ?: ""
        }

    init {
        runCatching {
            auth.addAuthStateListener { firebaseAuth ->
                val user = firebaseAuth.currentUser
                if (_userState.value == null && user != null && !user.email.isNullOrBlank()) {
                    _userState.value = user.toAuthUser()
                }
            }
        }
    }

    /** Rehydrate auth state from persistent storage if available on launch. */
    fun setAuthenticatedUser(user: AuthUser?) {
        _userState.value = user
    }

    /** Normalizes usernames/shorthands to fully qualified email addresses. */
    fun normalizeEmail(input: String): String {
        val trimmed = input.trim().lowercase()
        return when (trimmed) {
            "jangelpv" -> "jangelpv@crosstraining.app"
            "coach" -> "coach@crosstraining.app"
            "athlete" -> "athlete@crosstraining.app"
            else -> trimmed
        }
    }

    suspend fun ensureAuthenticated() {
        if (_userState.value != null && _userState.value?.uid?.isNotBlank() == true) {
            return
        }
        if (auth.currentUser == null) {
            try {
                withTimeout(5000L) {
                    val result = auth.signInAnonymously().await()
                    if (_userState.value == null) {
                        _userState.value = result.user?.toAuthUser()
                    }
                }
            } catch (e: Exception) {
                // Ignore offline
            }
        }
    }

    suspend fun syncUserProfile(email: String, explicitRole: UserRole? = null): UserRole {
        val uid = currentUserId
        val normalized = normalizeEmail(email).lowercase()
        val determinedRole = when {
            normalized == "pv.joseangel@gmail.com" || normalized == "coach@crosstraining.app" -> UserRole.COACH
            normalized.startsWith("jangelpv") || normalized == "athlete@crosstraining.app" -> UserRole.ATHLETE
            explicitRole != null -> explicitRole
            else -> UserRole.ATHLETE
        }

        if (uid.isNotBlank()) {
            try {
                val doc = userDoc(uid)
                val snap = doc.get().await()
                if (!snap.exists()) {
                    doc.set(
                        mapOf(
                            "email" to email,
                            "role" to determinedRole.name,
                            "env" to currentEnv,
                            "createdAt" to System.currentTimeMillis(),
                            "lastLoginAt" to System.currentTimeMillis()
                        )
                    ).await()
                } else {
                    doc.update(
                        mapOf(
                            "lastLoginAt" to System.currentTimeMillis(),
                            "role" to determinedRole.name
                        )
                    ).await()
                }
            } catch (e: Exception) {
                // Firestore offline/error fallback
            }
        }
        return determinedRole
    }

    internal var signUpWithEmailHandler: (suspend (String, String) -> Result<Unit>)? = null
    internal var logInWithEmailHandler: (suspend (String, String) -> Result<Unit>)? = null
    internal var signInWithGoogleCredentialHandler: (suspend (String) -> Result<Unit>)? = null
    internal var logInWithGoogleAccountHandler: (suspend (String, String?) -> Result<Unit>)? = null

    suspend fun signUpWithEmail(email: String, pass: String): Result<Unit> {
        val testHandler = signUpWithEmailHandler
        if (testHandler != null) {
            return testHandler(email, pass)
        }
        return runCatching {
            withTimeout(10000L) {
                val normalized = normalizeEmail(email)
                val currentUser = auth.currentUser
                if (currentUser != null && currentUser.isAnonymous) {
                    val credential = EmailAuthProvider.getCredential(normalized, pass)
                    currentUser.linkWithCredential(credential).await()
                } else {
                    auth.createUserWithEmailAndPassword(normalized, pass).await()
                }
                _userState.value = auth.currentUser?.toAuthUser()
                syncUserProfile(normalized)
            }
        }
    }

    suspend fun logInWithEmail(emailInput: String, pass: String): Result<Unit> {
        val testHandler = logInWithEmailHandler
        if (testHandler != null) {
            return testHandler(emailInput, pass)
        }
        return runCatching {
            withTimeout(10000L) {
                val normalized = normalizeEmail(emailInput)
                val isKnownTestUser = (normalized == "jangelpv@crosstraining.app" && pass == "crossAthlet3") ||
                        (normalized == "coach@crosstraining.app" && pass == "coach") ||
                        (normalized == "athlete@crosstraining.app" && pass == "athlete")

                try {
                    auth.signInWithEmailAndPassword(normalized, pass).await()
                    _userState.value = auth.currentUser?.toAuthUser()
                } catch (e: Exception) {
                    if (isKnownTestUser) {
                        // Try auto-registration or direct session fallback
                        try {
                            val authPass = if (pass.length >= 6) pass else "${pass}1234"
                            auth.createUserWithEmailAndPassword(normalized, authPass).await()
                            _userState.value = auth.currentUser?.toAuthUser()
                        } catch (createErr: Exception) {
                            ensureAuthenticated()
                            _userState.value = AuthUser(
                                uid = auth.currentUser?.uid ?: normalized.replace("@", "_").replace(".", "_"),
                                email = normalized,
                                isAnonymous = false
                            )
                        }
                    } else {
                        throw e
                    }
                }
                syncUserProfile(normalized)
            }
        }
    }

    suspend fun signInWithGoogleCredential(idToken: String): Result<Unit> {
        val testHandler = signInWithGoogleCredentialHandler
        if (testHandler != null) {
            return testHandler(idToken)
        }
        return runCatching {
            withTimeout(10000L) {
                val credential = GoogleAuthProvider.getCredential(idToken, null)
                val currentUser = auth.currentUser
                if (currentUser != null && currentUser.isAnonymous) {
                    currentUser.linkWithCredential(credential).await()
                } else {
                    auth.signInWithCredential(credential).await()
                }
                val user = auth.currentUser?.toAuthUser()
                _userState.value = user
                if (user?.email != null) {
                    syncUserProfile(user.email)
                }
            }
        }
    }

    suspend fun logInWithGoogleAccount(email: String, displayName: String?): Result<Unit> {
        val testHandler = logInWithGoogleAccountHandler
        if (testHandler != null) {
            return testHandler(email, displayName)
        }
        return runCatching {
            withTimeout(10000L) {
                ensureAuthenticated()
                val user = AuthUser(
                    uid = auth.currentUser?.uid ?: email,
                    email = email,
                    isAnonymous = false
                )
                _userState.value = user
                syncUserProfile(email)
            }
        }
    }

    private var passwordResetHandlerForTesting: (suspend (String) -> Result<Unit>)? = null

    fun setPasswordResetHandlerForTesting(handler: (suspend (String) -> Result<Unit>)?) {
        passwordResetHandlerForTesting = handler
    }

    suspend fun sendPasswordReset(email: String): Result<Unit> {
        val testHandler = passwordResetHandlerForTesting
        if (testHandler != null) {
            return testHandler(normalizeEmail(email))
        }
        return runCatching {
            withTimeout(10000L) {
                auth.sendPasswordResetEmail(normalizeEmail(email)).await()
            }
        }
    }

    fun signOut() {
        runCatching { auth.signOut() }
        _userState.value = null
        _syncState.value = SyncStatus.IDLE
    }

    fun setSyncStatus(status: SyncStatus) {
        _syncState.value = status
    }

    fun resetSyncStatus() {
        _syncState.value = SyncStatus.IDLE
    }

    internal var uploadUserDataHandler: (suspend (Repository) -> Result<Unit>)? = null
    internal var downloadUserDataHandler: (suspend (Repository) -> Result<Unit>)? = null
    internal var recoverAllCloudRoutinesHandler: (suspend (Repository) -> Result<Int>)? = null
    internal var documentWriterForTesting: (suspend (collectionName: String, data: Map<String, Any?>) -> Unit)? = null
    internal var remoteCollectionInspectorForTesting: (suspend (collectionName: String) -> Boolean)? = null

    internal var recoverRoutinesReaderForTesting: (suspend (userId: String) -> List<Map<String, Any>>)? = null

    fun resetTestHandlers() {
        uploadUserDataHandler = null
        downloadUserDataHandler = null
        recoverAllCloudRoutinesHandler = null
        signUpWithEmailHandler = null
        logInWithEmailHandler = null
        signInWithGoogleCredentialHandler = null
        logInWithGoogleAccountHandler = null
        documentWriterForTesting = null
        remoteCollectionInspectorForTesting = null
        passwordResetHandlerForTesting = null
        authUidProviderForTesting = null
        asyncAuthUidProviderForTesting = null
        recoverRoutinesReaderForTesting = null
        documentReaderForTesting = null
        authStateUserProviderForTesting = null
    }

    suspend fun awaitAuthState(timeoutMs: Long = 3000L): FirebaseUser? {
        if (authStateUserProviderForTesting != null) {
            val user = authStateUserProviderForTesting!!.invoke(timeoutMs)
            if (user != null && !user.isAnonymous) {
                if (_userState.value == null && !user.email.isNullOrBlank()) {
                    _userState.value = user.toAuthUser()
                }
            }
            return user
        }

        if (asyncAuthUidProviderForTesting != null) {
            val testUid = withTimeoutOrNull(timeoutMs) {
                asyncAuthUidProviderForTesting!!.invoke()
            }
            if (testUid != null && testUid.isNotBlank()) {
                if (_userState.value == null) {
                    _userState.value = AuthUser(uid = testUid, email = "athlete@example.com", isAnonymous = false)
                }
            }
            return null
        }

        val current = runCatching { auth.currentUser }.getOrNull()
        if (current != null && !current.isAnonymous) {
            if (_userState.value == null && !current.email.isNullOrBlank()) {
                _userState.value = current.toAuthUser()
            }
            return current
        }

        val firebaseAuth = runCatching { auth }.getOrNull() ?: return null
        val deferred = CompletableDeferred<FirebaseUser?>()
        val listener = FirebaseAuth.AuthStateListener { fa ->
            val u = fa.currentUser
            if (u != null && !u.isAnonymous) {
                if (_userState.value == null && !u.email.isNullOrBlank()) {
                    _userState.value = u.toAuthUser()
                }
                deferred.complete(u)
            }
        }
        return try {
            firebaseAuth.addAuthStateListener(listener)
            withTimeoutOrNull(timeoutMs) {
                deferred.await()
            }
        } finally {
            runCatching { firebaseAuth.removeAuthStateListener(listener) }
        }
    }

    internal suspend fun verifyTokenBinding(): Result<Unit> {
        var user = _userState.value

        if (asyncAuthUidProviderForTesting != null) {
            val testUid = withTimeoutOrNull(3000L) {
                asyncAuthUidProviderForTesting!!.invoke()
            }
            if (testUid == null) {
                return Result.failure(IllegalStateException(CloudSyncErrorMapper.GUEST_AUTH_PROMPT))
            }
            if (user != null && !user.isAnonymous && user.uid.isNotBlank() && testUid != user.uid) {
                return Result.failure(IllegalStateException(CloudSyncErrorMapper.GUEST_AUTH_PROMPT))
            }
            if (user == null) {
                _userState.value = AuthUser(uid = testUid, email = "athlete@example.com", isAnonymous = false)
            }
            return Result.success(Unit)
        }

        if (authUidProviderForTesting != null) {
            val testUid = authUidProviderForTesting!!.invoke()
            if (user != null && !user.isAnonymous && user.uid.isNotBlank()) {
                if (testUid == null || testUid != user.uid) {
                    return Result.failure(IllegalStateException(CloudSyncErrorMapper.GUEST_AUTH_PROMPT))
                }
            } else if (user == null) {
                if (testUid == null) {
                    return Result.failure(IllegalStateException(CloudSyncErrorMapper.GUEST_AUTH_PROMPT))
                }
                _userState.value = AuthUser(uid = testUid, email = "athlete@example.com", isAnonymous = false)
            }
            return Result.success(Unit)
        }

        val isFirebaseAvailable = runCatching { FirebaseAuth.getInstance() }.isSuccess
        if (!isFirebaseAvailable) {
            if (documentWriterForTesting != null || documentReaderForTesting != null) {
                return Result.success(Unit)
            }
            return Result.failure(IllegalStateException(CloudSyncErrorMapper.GUEST_AUTH_PROMPT))
        }

        var currentUser = runCatching { auth.currentUser }.getOrNull()
        if (currentUser == null || currentUser.isAnonymous) {
            currentUser = awaitAuthState(3000L)
        }

        user = _userState.value
        if (user != null && !user.isAnonymous && user.uid.isNotBlank()) {
            if (currentUser == null || currentUser.isAnonymous || currentUser.uid != user.uid) {
                return Result.failure(IllegalStateException(CloudSyncErrorMapper.GUEST_AUTH_PROMPT))
            }
        } else {
            if (currentUser == null || currentUser.isAnonymous) {
                return Result.failure(IllegalStateException(CloudSyncErrorMapper.GUEST_AUTH_PROMPT))
            }
            if (_userState.value == null) {
                _userState.value = currentUser.toAuthUser()
            }
        }
        return Result.success(Unit)
    }

    private suspend fun uploadCollectionWithGuard(
        collectionName: String,
        docRef: DocumentReference?,
        payload: List<Map<String, Any?>>,
        isLocallyEmpty: Boolean
    ) {
        if (isLocallyEmpty) {
            val testInspector = remoteCollectionInspectorForTesting
            val isPopulated = if (testInspector != null) {
                testInspector(collectionName)
            } else if (docRef != null) {
                try {
                    val snap = docRef.get().await()
                    val remoteList = snap.get("list") as? List<*>
                    snap.exists() && !remoteList.isNullOrEmpty()
                } catch (e: Exception) {
                    true // Fail-safe: assume populated to protect remote data
                }
            } else {
                false
            }

            if (isPopulated) {
                // Per-document overwrite guard: skip destructive .set() overwrite
                return
            }
        }

        val data = mapOf("list" to payload)
        val testWriter = documentWriterForTesting
        if (testWriter != null) {
            testWriter(collectionName, data)
        } else {
            docRef?.set(data)?.await()
        }
    }

    suspend fun uploadUserData(repo: Repository): Result<Unit> {
        val testHandler = uploadUserDataHandler
        if (testHandler != null) {
            return testHandler(repo)
        }
        return runCatching {
            _syncState.value = SyncStatus.SYNCING

            withTimeout(20000L) {
                verifyTokenBinding().getOrThrow()
                ensureAuthenticated()
                val uid = currentUserId
                if (uid.isBlank()) error(CloudSyncErrorMapper.GUEST_AUTH_PROMPT)

                // 1. Explicit synchronous pre-flight step
                repo.cleanupDuplicateRoutines()

                val doc = if (documentWriterForTesting != null) null else userDoc(uid)
                val collectionErrors = mutableMapOf<String, Throwable>()

                // 2. Fault-isolated concurrent uploads inside supervisorScope
                supervisorScope {
                    val taskExercises = async {
                        runCatching {
                            val exercises = repo.getAllExercisesOnce()
                            val exPayload = exercises.map { ex ->
                                mapOf(
                                    "id" to ex.id,
                                    "name" to ex.name,
                                    "category" to ex.category.name,
                                    "metricType" to ex.metricType.name,
                                    "unit" to ex.unit,
                                    "tracksRepMax" to ex.tracksRepMax
                                )
                            }
                            uploadCollectionWithGuard(
                                collectionName = "exercises",
                                docRef = doc?.collection("data")?.document("exercises"),
                                payload = exPayload,
                                isLocallyEmpty = exercises.isEmpty()
                            )
                        }
                    }

                    val taskRoutines = async {
                        runCatching {
                            val routinesWithBlocks = repo.getAllRoutinesWithBlocksOnce().distinctBy { it.routine.name.trim().lowercase() }
                            val routinesPayload = routinesWithBlocks.map { rwb ->
                                mapOf(
                                    "routine" to mapOf(
                                        "id" to rwb.routine.id,
                                        "name" to rwb.routine.name,
                                        "description" to rwb.routine.description,
                                        "mainExerciseId" to rwb.routine.mainExerciseId,
                                        "defaultFormat" to rwb.routine.defaultFormat
                                    ),
                                    "blocks" to rwb.blocks.map { b ->
                                        mapOf(
                                            "id" to b.id,
                                            "routineId" to b.routineId,
                                            "position" to b.position,
                                            "name" to b.name,
                                            "kind" to b.kind.name,
                                            "format" to b.format,
                                            "setsCount" to b.setsCount,
                                            "targetRepsScheme" to b.targetRepsScheme,
                                            "exerciseIdsCsv" to b.exerciseIdsCsv,
                                            "notes" to b.notes
                                        )
                                    }
                                )
                            }
                            uploadCollectionWithGuard(
                                collectionName = "routines",
                                docRef = doc?.collection("data")?.document("routines"),
                                payload = routinesPayload,
                                isLocallyEmpty = routinesWithBlocks.isEmpty()
                            )
                        }
                    }

                    val taskSessions = async {
                        runCatching {
                            val sessionsWithBlocks = repo.getAllSessionsWithBlocksOnce()
                            val sessionsPayload = sessionsWithBlocks.map { swb ->
                                mapOf(
                                    "session" to mapOf(
                                        "id" to swb.session.id,
                                        "cycleId" to swb.session.cycleId,
                                        "date" to swb.session.date.toString(),
                                        "title" to swb.session.title,
                                        "notes" to swb.session.notes
                                    ),
                                    "blocks" to swb.blocks.map { sb ->
                                        mapOf(
                                            "block" to mapOf(
                                                "id" to sb.block.id,
                                                "sessionId" to sb.block.sessionId,
                                                "position" to sb.block.position,
                                                "name" to sb.block.name,
                                                "kind" to sb.block.kind.name,
                                                "format" to sb.block.format,
                                                "scheme" to sb.block.scheme,
                                                "mainExerciseId" to sb.block.mainExerciseId,
                                                "routineId" to sb.block.routineId,
                                                "description" to sb.block.description,
                                                "resultText" to sb.block.resultText,
                                                "resultValue" to sb.block.resultValue,
                                                "notes" to sb.block.notes
                                            ),
                                            "sets" to sb.sets.map { st ->
                                                mapOf(
                                                    "id" to st.id,
                                                    "blockId" to st.blockId,
                                                    "position" to st.position,
                                                    "groupIndex" to st.groupIndex,
                                                    "reps" to st.reps,
                                                    "weight" to st.weight,
                                                    "metricValue" to st.metricValue,
                                                    "isWarmup" to st.isWarmup,
                                                    "isFailed" to st.isFailed,
                                                    "notes" to st.notes
                                                )
                                            }
                                        )
                                    }
                                )
                            }
                            uploadCollectionWithGuard(
                                collectionName = "sessions",
                                docRef = doc?.collection("data")?.document("sessions"),
                                payload = sessionsPayload,
                                isLocallyEmpty = sessionsWithBlocks.isEmpty()
                            )
                        }
                    }

                    val taskGoals = async {
                        runCatching {
                            val cycleGoals = repo.snapshotCycleGoals()
                            val goalsPayload = cycleGoals.map { cg ->
                                mapOf(
                                    "id" to cg.id,
                                    "cycleId" to cg.cycleId,
                                    "exerciseId" to cg.exerciseId,
                                    "targetReps" to cg.targetReps,
                                    "startWeight" to cg.startWeight,
                                    "targetWeight" to cg.targetWeight,
                                    "notes" to cg.notes
                                )
                            }
                            uploadCollectionWithGuard(
                                collectionName = "cycle_goals",
                                docRef = doc?.collection("data")?.document("cycle_goals"),
                                payload = goalsPayload,
                                isLocallyEmpty = cycleGoals.isEmpty()
                            )
                        }
                    }

                    val taskRepMaxes = async {
                        runCatching {
                            val repMaxes = repo.exportSnapshot().repMaxes
                            val repMaxesPayload = repMaxes.map { rm ->
                                mapOf(
                                    "id" to rm.id,
                                    "exerciseId" to rm.exerciseId,
                                    "reps" to rm.reps,
                                    "weight" to rm.weight,
                                    "date" to rm.date.toString(),
                                    "cycleId" to rm.cycleId
                                )
                            }
                            uploadCollectionWithGuard(
                                collectionName = "rep_maxes",
                                docRef = doc?.collection("data")?.document("rep_maxes"),
                                payload = repMaxesPayload,
                                isLocallyEmpty = repMaxes.isEmpty()
                            )
                        }
                    }

                    val resExercises = taskExercises.await()
                    val resRoutines = taskRoutines.await()
                    val resSessions = taskSessions.await()
                    val resGoals = taskGoals.await()
                    val resRepMaxes = taskRepMaxes.await()

                    resExercises.exceptionOrNull()?.let { collectionErrors["exercises"] = it }
                    resRoutines.exceptionOrNull()?.let { collectionErrors["routines"] = it }
                    resSessions.exceptionOrNull()?.let { collectionErrors["sessions"] = it }
                    resGoals.exceptionOrNull()?.let { collectionErrors["cycle_goals"] = it }
                    resRepMaxes.exceptionOrNull()?.let { collectionErrors["rep_maxes"] = it }
                }

                if (collectionErrors.isNotEmpty()) {
                    val errorDetails = collectionErrors.entries.joinToString("; ") { (name, err) ->
                        "$name: ${err.message ?: err.javaClass.simpleName}"
                    }
                    error("Upload failed for collections: $errorDetails")
                }
            }

            _syncState.value = SyncStatus.SUCCESS
        }.onFailure {
            _syncState.value = SyncStatus.ERROR
        }
    }

    suspend fun downloadUserData(repo: Repository): Result<Unit> {
        val testHandler = downloadUserDataHandler
        if (testHandler != null) {
            return testHandler(repo)
        }
        return runCatching {
            _syncState.value = SyncStatus.SYNCING

            withTimeout(20000L) {
                verifyTokenBinding().getOrThrow()
                ensureAuthenticated()
                val uid = currentUserId
                if (uid.isBlank()) error(CloudSyncErrorMapper.GUEST_AUTH_PROMPT)

                suspend fun fetchCollection(targetUid: String, collectionName: String): List<Map<String, Any>> {
                    val testReader = documentReaderForTesting
                    if (testReader != null) {
                        return testReader(targetUid, collectionName) ?: emptyList()
                    }
                    val isAvailable = runCatching { FirebaseFirestore.getInstance() }.isSuccess
                    if (!isAvailable) {
                        return emptyList()
                    }
                    val snap = userDoc(targetUid).collection("data").document(collectionName).get().await()
                    if (snap.exists()) {
                        @Suppress("UNCHECKED_CAST")
                        return snap.get("list") as? List<Map<String, Any>> ?: emptyList()
                    }
                    return emptyList()
                }

                var exList = fetchCollection(uid, "exercises")
                var routList = fetchCollection(uid, "routines")
                var sessList = fetchCollection(uid, "sessions")
                var goalsList = fetchCollection(uid, "cycle_goals")
                var rmList = fetchCollection(uid, "rep_maxes")

                val isNewUidEmpty = exList.isEmpty() && routList.isEmpty() && sessList.isEmpty() && goalsList.isEmpty() && rmList.isEmpty()
                val userEmail = _userState.value?.email
                var isMigratedFromLegacy = false

                // Dual-read fallback: if users/{newUid} is empty, query legacy users/{email}
                if (isNewUidEmpty && !userEmail.isNullOrBlank() && userEmail != uid) {
                    val legacyUid = userEmail.replace("/", "_")
                    var legacyEx = fetchCollection(legacyUid, "exercises")
                    var legacyRout = fetchCollection(legacyUid, "routines")
                    var legacySess = fetchCollection(legacyUid, "sessions")
                    var legacyGoals = fetchCollection(legacyUid, "cycle_goals")
                    var legacyRm = fetchCollection(legacyUid, "rep_maxes")

                    val normalizedLegacyUid = normalizeEmail(userEmail).replace("/", "_")
                    if (legacyEx.isEmpty() && legacyRout.isEmpty() && legacySess.isEmpty() && legacyGoals.isEmpty() && legacyRm.isEmpty() && normalizedLegacyUid != legacyUid) {
                        legacyEx = fetchCollection(normalizedLegacyUid, "exercises")
                        legacyRout = fetchCollection(normalizedLegacyUid, "routines")
                        legacySess = fetchCollection(normalizedLegacyUid, "sessions")
                        legacyGoals = fetchCollection(normalizedLegacyUid, "cycle_goals")
                        legacyRm = fetchCollection(normalizedLegacyUid, "rep_maxes")
                    }

                    val hasLegacyData = legacyEx.isNotEmpty() || legacyRout.isNotEmpty() || legacySess.isNotEmpty() || legacyGoals.isNotEmpty() || legacyRm.isNotEmpty()
                    if (hasLegacyData) {
                        exList = legacyEx
                        routList = legacyRout
                        sessList = legacySess
                        goalsList = legacyGoals
                        rmList = legacyRm
                        isMigratedFromLegacy = true
                    }
                }

                // 1. Download Exercises
                if (exList.isNotEmpty()) {
                    exList.forEach { map ->
                        val name = map["name"] as? String ?: return@forEach
                        val category = runCatching { ExerciseCategory.valueOf(map["category"] as String) }.getOrDefault(ExerciseCategory.BARBELL)
                        val metricType = runCatching { MetricType.valueOf(map["metricType"] as String) }.getOrDefault(MetricType.WEIGHT)
                        repo.getOrCreateExercise(name, category, metricType)
                    }
                }

                // 2. Download Routines
                if (routList.isNotEmpty()) {
                    routList.forEach { rwbMap ->
                        @Suppress("UNCHECKED_CAST")
                        val rMap = rwbMap["routine"] as? Map<String, Any> ?: return@forEach
                        val name = rMap["name"] as? String ?: return@forEach
                        val description = rMap["description"] as? String ?: ""
                        val defaultFormat = rMap["defaultFormat"] as? String ?: ""

                        @Suppress("UNCHECKED_CAST")
                        val bList = rwbMap["blocks"] as? List<Map<String, Any>> ?: emptyList()
                        val blocks = bList.mapIndexed { idx, bMap ->
                            RoutineBlock(
                                id = 0,
                                routineId = 0,
                                position = (bMap["position"] as? Number)?.toInt() ?: idx,
                                name = bMap["name"] as? String ?: "",
                                kind = runCatching { BlockKind.valueOf(bMap["kind"] as String) }.getOrDefault(BlockKind.WEIGHTLIFTING),
                                format = bMap["format"] as? String ?: "",
                                setsCount = (bMap["setsCount"] as? Number)?.toInt() ?: 1,
                                targetRepsScheme = bMap["targetRepsScheme"] as? String ?: "",
                                exerciseIdsCsv = bMap["exerciseIdsCsv"] as? String ?: "",
                                notes = bMap["notes"] as? String ?: ""
                            )
                        }
                        repo.saveRoutineWithBlocks(Routine(name = name, description = description, defaultFormat = defaultFormat), blocks)
                    }
                    repo.cleanupDuplicateRoutines()
                }

                // 3. Download Cycle Goals
                if (goalsList.isNotEmpty()) {
                    goalsList.forEach { map ->
                        val cycleId = (map["cycleId"] as? Number)?.toLong() ?: return@forEach
                        val exerciseId = (map["exerciseId"] as? Number)?.toLong() ?: return@forEach
                        val targetReps = (map["targetReps"] as? Number)?.toInt() ?: 1
                        val startWeight = (map["startWeight"] as? Number)?.toDouble() ?: 0.0
                        val targetWeight = (map["targetWeight"] as? Number)?.toDouble() ?: 0.0
                        val notes = map["notes"] as? String ?: ""
                        repo.saveCycleGoal(com.fractanomics.crosstraining.data.model.CycleGoal(cycleId = cycleId, exerciseId = exerciseId, targetReps = targetReps, startWeight = startWeight, targetWeight = targetWeight, notes = notes))
                    }
                }

                // 4. Download Sessions & Logs (Workouts)
                if (sessList.isNotEmpty()) {
                    val existingSessions = repo.getAllSessionsWithBlocksOnce()
                    sessList.forEach { swbMap ->
                        @Suppress("UNCHECKED_CAST")
                        val sMap = swbMap["session"] as? Map<String, Any> ?: return@forEach
                        val dateStr = sMap["date"] as? String ?: return@forEach
                        val date = runCatching { LocalDate.parse(dateStr) }.getOrNull() ?: return@forEach
                        val title = sMap["title"] as? String ?: ""
                        val notes = sMap["notes"] as? String ?: ""
                        val cycleId = (sMap["cycleId"] as? Number)?.toLong() ?: 0L

                        val alreadyExists = existingSessions.any { it.session.date == date && it.session.title == title }
                        if (!alreadyExists) {
                            @Suppress("UNCHECKED_CAST")
                            val bList = swbMap["blocks"] as? List<Map<String, Any>> ?: emptyList()
                            val blockInserts = bList.mapIndexed { bIdx, bMapRaw ->
                                @Suppress("UNCHECKED_CAST")
                                val bMap = bMapRaw["block"] as? Map<String, Any> ?: bMapRaw
                                val block = SessionBlock(
                                    id = 0,
                                    sessionId = 0,
                                    position = (bMap["position"] as? Number)?.toInt() ?: bIdx,
                                    name = bMap["name"] as? String ?: "",
                                    kind = runCatching { BlockKind.valueOf(bMap["kind"] as String) }.getOrDefault(BlockKind.WEIGHTLIFTING),
                                    format = bMap["format"] as? String ?: "",
                                    scheme = bMap["scheme"] as? String ?: "",
                                    mainExerciseId = (bMap["mainExerciseId"] as? Number)?.toLong(),
                                    routineId = (bMap["routineId"] as? Number)?.toLong(),
                                    description = bMap["description"] as? String ?: "",
                                    resultText = bMap["resultText"] as? String ?: "",
                                    resultValue = (bMap["resultValue"] as? Number)?.toDouble(),
                                    notes = bMap["notes"] as? String ?: ""
                                )
                                @Suppress("UNCHECKED_CAST")
                                val setList = bMapRaw["sets"] as? List<Map<String, Any>> ?: emptyList()
                                val sets = setList.mapIndexed { sIdx, sMapItem ->
                                    BlockSet(
                                        id = 0,
                                        blockId = 0,
                                        position = (sMapItem["position"] as? Number)?.toInt() ?: sIdx,
                                        groupIndex = (sMapItem["groupIndex"] as? Number)?.toInt() ?: 0,
                                        reps = (sMapItem["reps"] as? Number)?.toInt() ?: 0,
                                        weight = (sMapItem["weight"] as? Number)?.toDouble() ?: 0.0,
                                        metricValue = (sMapItem["metricValue"] as? Number)?.toDouble() ?: 0.0,
                                        isWarmup = sMapItem["isWarmup"] as? Boolean ?: false,
                                        isFailed = sMapItem["isFailed"] as? Boolean ?: false,
                                        notes = sMapItem["notes"] as? String ?: ""
                                    )
                                }
                                com.fractanomics.crosstraining.data.BlockInsert(block = block, sets = sets)
                            }
                            repo.saveSession(Session(id = 0, cycleId = cycleId, date = date, title = title, notes = notes), blockInserts)
                        }
                    }
                }

                // 5. Download Rep Maxes (PR History)
                if (rmList.isNotEmpty()) {
                    val existingRms = repo.exportSnapshot().repMaxes
                    rmList.forEach { map ->
                        val exerciseId = (map["exerciseId"] as? Number)?.toLong() ?: return@forEach
                        val reps = (map["reps"] as? Number)?.toInt() ?: return@forEach
                        val weight = (map["weight"] as? Number)?.toDouble() ?: return@forEach
                        val dateStr = map["date"] as? String ?: return@forEach
                        val date = runCatching { LocalDate.parse(dateStr) }.getOrNull() ?: return@forEach
                        val cycleId = (map["cycleId"] as? Number)?.toLong()

                        val exists = existingRms.any {
                            it.exerciseId == exerciseId && it.reps == reps && it.weight == weight && it.date == date
                        }
                        if (!exists) {
                            repo.recordRepMax(exerciseId, reps, weight, date, cycleId)
                        }
                    }
                }

                // If migrated from legacy path, re-upload to users/{newUid}
                if (isMigratedFromLegacy) {
                    uploadUserData(repo)
                }
            }

            _syncState.value = SyncStatus.SUCCESS
        }.onFailure {
            _syncState.value = SyncStatus.ERROR
        }
    }

    suspend fun recoverAllCloudRoutines(repo: Repository): Result<Int> {
        val testHandler = recoverAllCloudRoutinesHandler
        if (testHandler != null) {
            return testHandler(repo)
        }
        return runCatching {
            withTimeout(15000L) {
                val uid = currentUserId
                if (uid.isBlank()) return@withTimeout 0

                suspend fun fetchRoutines(targetUid: String): List<Map<String, Any>> {
                    if (recoverRoutinesReaderForTesting != null) {
                        return recoverRoutinesReaderForTesting!!.invoke(targetUid)
                    }
                    val isAvailable = runCatching { FirebaseFirestore.getInstance() }.isSuccess
                    if (!isAvailable) {
                        return emptyList()
                    }
                    val routinesDoc = userDoc(targetUid).collection("data").document("routines").get().await()
                    if (routinesDoc.exists()) {
                        @Suppress("UNCHECKED_CAST")
                        return routinesDoc.get("list") as? List<Map<String, Any>> ?: emptyList()
                    }
                    return emptyList()
                }

                var routList = fetchRoutines(uid)
                var migratedFromLegacy = false
                val userEmail = _userState.value?.email

                if (routList.isEmpty() && !userEmail.isNullOrBlank() && userEmail != uid) {
                    val legacyUid = userEmail.replace("/", "_")
                    var legacyRoutList = fetchRoutines(legacyUid)
                    val normalizedLegacyUid = normalizeEmail(userEmail).replace("/", "_")
                    if (legacyRoutList.isEmpty() && normalizedLegacyUid != legacyUid) {
                        legacyRoutList = fetchRoutines(normalizedLegacyUid)
                    }
                    if (legacyRoutList.isNotEmpty()) {
                        routList = legacyRoutList
                        migratedFromLegacy = true
                    }
                }

                var count = 0
                routList.forEach { rwbMap ->
                    @Suppress("UNCHECKED_CAST")
                    val rMap = rwbMap["routine"] as? Map<String, Any> ?: return@forEach
                    val name = rMap["name"] as? String ?: return@forEach
                    val description = rMap["description"] as? String ?: ""
                    val defaultFormat = rMap["defaultFormat"] as? String ?: ""

                    @Suppress("UNCHECKED_CAST")
                    val bList = rwbMap["blocks"] as? List<Map<String, Any>> ?: emptyList()
                    val blocks = bList.mapIndexed { idx, bMap ->
                        RoutineBlock(
                            id = 0,
                            routineId = 0,
                            position = (bMap["position"] as? Number)?.toInt() ?: idx,
                            name = bMap["name"] as? String ?: "",
                            kind = runCatching { BlockKind.valueOf(bMap["kind"] as String) }.getOrDefault(BlockKind.WEIGHTLIFTING),
                            format = bMap["format"] as? String ?: "",
                            setsCount = (bMap["setsCount"] as? Number)?.toInt() ?: 1,
                            targetRepsScheme = bMap["targetRepsScheme"] as? String ?: "",
                            exerciseIdsCsv = bMap["exerciseIdsCsv"] as? String ?: "",
                            notes = bMap["notes"] as? String ?: ""
                        )
                    }
                    repo.saveRoutineWithBlocks(Routine(name = name, description = description, defaultFormat = defaultFormat), blocks)
                    count++
                }
                repo.cleanupDuplicateRoutines()

                if (migratedFromLegacy && count > 0) {
                    uploadUserData(repo)
                }

                count
            }
        }
    }

    private fun FirebaseUser.toAuthUser(): AuthUser = AuthUser(
        uid = uid,
        email = email,
        isAnonymous = isAnonymous
    )
}
