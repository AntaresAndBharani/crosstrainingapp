package com.fractanomics.crosstraining.data.firebase

import com.fractanomics.crosstraining.BuildConfig
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
import com.fractanomics.crosstraining.data.model.Session
import com.fractanomics.crosstraining.data.model.SessionBlock
import com.fractanomics.crosstraining.data.model.SessionWithBlocks
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
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withTimeout
import kotlinx.coroutines.withTimeoutOrNull
import java.math.BigDecimal
import java.math.RoundingMode
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

    private val syncMutex = Mutex()

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

    private fun normalizeWeight(value: Double): BigDecimal =
        BigDecimal.valueOf(value).setScale(2, RoundingMode.HALF_UP)

    /**
     * Id-independent content signature of a remote `sessions` payload entry (blocks + sets), so exact
     * remote copies of one workout collapse during download while distinct workouts sharing a date and
     * title are both kept. Mirrors the block/set fields used by `Repository.cleanupDuplicateSessions()`.
     */
    internal fun remoteSessionContentSignature(swbMap: Map<String, Any>): String {
        @Suppress("UNCHECKED_CAST")
        val bList = swbMap["blocks"] as? List<Map<String, Any>> ?: return ""
        fun str(map: Map<String, Any>, key: String): String = (map[key] as? String)?.trim()?.lowercase() ?: ""
        fun num(map: Map<String, Any>, key: String): BigDecimal =
            normalizeWeight((map[key] as? Number)?.toDouble() ?: 0.0)
        return bList
            .mapIndexed { idx, raw ->
                @Suppress("UNCHECKED_CAST")
                val bMap = raw["block"] as? Map<String, Any> ?: raw
                idx to (raw to bMap)
            }
            .sortedBy { (idx, pair) -> (pair.second["position"] as? Number)?.toInt() ?: idx }
            .joinToString("|") { (_, pair) ->
                val (raw, bMap) = pair
                val exerciseRef = str(bMap, "mainExerciseName").ifEmpty {
                    (bMap["mainExerciseId"] as? Number)?.toLong()?.toString() ?: ""
                }
                @Suppress("UNCHECKED_CAST")
                val sets = raw["sets"] as? List<Map<String, Any>> ?: emptyList()
                val setsSig = sets
                    .mapIndexed { sIdx, s -> ((s["position"] as? Number)?.toInt() ?: sIdx) to s }
                    .sortedBy { it.first }
                    .joinToString(",") { (_, s) ->
                        "${(s["groupIndex"] as? Number)?.toInt()}:${(s["reps"] as? Number)?.toInt()}:" +
                            "${num(s, "weight")}:${num(s, "metricValue")}:${s["isWarmup"] == true}:${s["isFailed"] == true}"
                    }
                "${str(bMap, "name")}#${bMap["kind"]}#${str(bMap, "format")}#${str(bMap, "scheme")}#" +
                    "$exerciseRef#${str(bMap, "resultText")}#${num(bMap, "resultValue")}#[$setsSig]"
            }
    }

    /**
     * Projects a local [SessionWithBlocks] into the minimal remote `sessions` payload shape consumed by
     * [remoteSessionContentSignature], so local and incoming sessions are compared by the same signature.
     */
    private fun localSessionAsRemotePayload(
        swb: SessionWithBlocks,
        exerciseNamesById: Map<Long, String>
    ): Map<String, Any> = mapOf(
        "blocks" to swb.blocks.map { bws ->
            val b = bws.block
            val blockMap = mutableMapOf<String, Any>(
                "position" to b.position,
                "name" to b.name,
                "kind" to b.kind.name,
                "format" to b.format,
                "scheme" to b.scheme,
                "resultText" to b.resultText
            )
            b.resultValue?.let { blockMap["resultValue"] = it }
            b.mainExerciseId?.let { id ->
                blockMap["mainExerciseId"] = id
                exerciseNamesById[id]?.let { blockMap["mainExerciseName"] = it }
            }
            mapOf(
                "block" to blockMap,
                "sets" to bws.sets.map { st ->
                    val setMap = mutableMapOf<String, Any>(
                        "position" to st.position,
                        "reps" to st.reps,
                        "isWarmup" to st.isWarmup,
                        "isFailed" to st.isFailed
                    )
                    st.groupIndex?.let { setMap["groupIndex"] = it }
                    st.weight?.let { setMap["weight"] = it }
                    st.metricValue?.let { setMap["metricValue"] = it }
                    setMap
                }
            )
        }
    )

    suspend fun uploadUserData(repo: Repository): Result<Unit> {
        val testHandler = uploadUserDataHandler
        if (testHandler != null) {
            return testHandler(repo)
        }
        return syncMutex.withLock {
            uploadUserDataInternal(repo)
        }
    }

    private suspend fun uploadUserDataInternal(repo: Repository): Result<Unit> {
        return runCatching {
            _syncState.value = SyncStatus.SYNCING

            withTimeout(20000L) {
                verifyTokenBinding().getOrThrow()
                ensureAuthenticated()
                val uid = currentUserId
                if (uid.isBlank()) error(CloudSyncErrorMapper.GUEST_AUTH_PROMPT)

                // 1. Explicit synchronous pre-flight deduplication across all tables
                repo.cleanupDuplicateData()
                val ninetyDaysAgo = System.currentTimeMillis() - 90L * 24 * 60 * 60 * 1000L
                repo.purgeOldWeightTombstones(ninetyDaysAgo)
                repo.purgeOldDailyLogTombstones(ninetyDaysAgo)

                val allExercises = repo.getAllExercisesOnce().distinctBy { it.name.trim().lowercase() }
                val exercisesById = allExercises.associateBy { it.id }
                val cyclesById = repo.getAllCyclesOnce().associateBy { it.id }

                val doc = if (documentWriterForTesting != null) null else userDoc(uid)
                val collectionErrors = mutableMapOf<String, Throwable>()

                // 2. Fault-isolated concurrent uploads inside supervisorScope
                supervisorScope {
                    val taskExercises = async {
                        runCatching {
                            val exPayload = allExercises.map { ex ->
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
                                isLocallyEmpty = allExercises.isEmpty()
                            )
                        }
                    }

                    val taskRoutines = async {
                        runCatching {
                            val routinesWithBlocks = repo.getAllRoutinesWithBlocksOnce()
                                .distinctBy { it.routine.name.trim().lowercase() }
                            val routinesPayload = routinesWithBlocks.map { rwb ->
                                val mainExName = rwb.routine.mainExerciseId?.let { exercisesById[it]?.name }
                                val routineMap = mutableMapOf<String, Any?>(
                                    "id" to rwb.routine.id,
                                    "name" to rwb.routine.name,
                                    "description" to rwb.routine.description,
                                    "mainExerciseId" to rwb.routine.mainExerciseId,
                                    "defaultFormat" to rwb.routine.defaultFormat
                                )
                                if (mainExName != null) {
                                    routineMap["mainExerciseName"] = mainExName
                                }
                                mapOf(
                                    "routine" to routineMap,
                                    "blocks" to rwb.blocks.map { b ->
                                        val exerciseNamesCsv = b.exerciseIdsCsv.split(",")
                                            .mapNotNull { it.trim().toLongOrNull()?.let { id -> exercisesById[id]?.name } }
                                            .joinToString(",")
                                        val blockMap = mutableMapOf<String, Any?>(
                                            "id" to b.id,
                                            "routineId" to b.routineId,
                                            "position" to b.position,
                                            "name" to b.name,
                                            "kind" to b.kind.name,
                                            "format" to b.format,
                                            "setsCount" to b.setsCount,
                                            "targetRepsScheme" to b.targetRepsScheme,
                                            "exerciseIdsCsv" to b.exerciseIdsCsv,
                                            "notes" to b.notes,
                                            "section" to b.section,
                                            "subBlock" to b.subBlock
                                        )
                                        if (exerciseNamesCsv.isNotEmpty()) {
                                            blockMap["exerciseNamesCsv"] = exerciseNamesCsv
                                        }
                                        blockMap
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
                            // Local duplicates were already collapsed by cleanupDuplicateData() using the full
                            // (cycleId, date, title, blocksSignature) natural key. Do NOT re-deduplicate here by
                            // (cycleId, date, title) alone: two distinct same-titled workouts logged on the same
                            // day would silently be dropped from the whole-list cloud backup.
                            val sessionsWithBlocks = repo.getAllSessionsWithBlocksOnce()
                            val sessionsPayload = sessionsWithBlocks.map { swb ->
                                val cycleName = cyclesById[swb.session.cycleId]?.name
                                val sessionMap = mutableMapOf<String, Any?>(
                                    "id" to swb.session.id,
                                    "cycleId" to swb.session.cycleId,
                                    "date" to swb.session.date.toString(),
                                    "title" to swb.session.title,
                                    "notes" to swb.session.notes
                                )
                                if (cycleName != null) {
                                    sessionMap["cycleName"] = cycleName
                                }
                                mapOf(
                                    "session" to sessionMap,
                                    "blocks" to swb.blocks.map { sb ->
                                        val mainExName = sb.block.mainExerciseId?.let { exercisesById[it]?.name }
                                        val exerciseNamesCsv = sb.block.exerciseIdsCsv.split(",")
                                            .mapNotNull { it.trim().toLongOrNull()?.let { id -> exercisesById[id]?.name } }
                                            .joinToString(",")
                                        val blockMap = mutableMapOf<String, Any?>(
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
                                            "notes" to sb.block.notes,
                                            "section" to sb.block.section,
                                            "exerciseIdsCsv" to sb.block.exerciseIdsCsv,
                                            "subBlock" to sb.block.subBlock,
                                            "isCompleted" to sb.block.isCompleted
                                        )
                                        if (mainExName != null) {
                                            blockMap["mainExerciseName"] = mainExName
                                        }
                                        if (exerciseNamesCsv.isNotEmpty()) {
                                            blockMap["exerciseNamesCsv"] = exerciseNamesCsv
                                        }
                                        mapOf(
                                            "block" to blockMap,
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
                                .distinctBy { listOf(it.cycleId, it.exerciseId, it.goalMetric.name, it.targetReps) }
                            val goalsPayload = cycleGoals.map { cg ->
                                val exName = exercisesById[cg.exerciseId]?.name
                                val cycName = cyclesById[cg.cycleId]?.name
                                val goalMap = mutableMapOf<String, Any?>(
                                    "id" to cg.id,
                                    "cycleId" to cg.cycleId,
                                    "exerciseId" to cg.exerciseId,
                                    "targetReps" to cg.targetReps,
                                    "startWeight" to cg.startWeight,
                                    "targetWeight" to cg.targetWeight,
                                    "notes" to cg.notes
                                )
                                if (exName != null) {
                                    goalMap["exerciseName"] = exName
                                }
                                if (cycName != null) {
                                    goalMap["cycleName"] = cycName
                                }
                                goalMap
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
                                .distinctBy { listOf(it.exerciseId, it.reps, normalizeWeight(it.weight), it.date) }
                            val repMaxesPayload = repMaxes.map { rm ->
                                val exName = exercisesById[rm.exerciseId]?.name
                                val cycName = rm.cycleId?.let { cyclesById[it]?.name }
                                val rmMap = mutableMapOf<String, Any?>(
                                    "id" to rm.id,
                                    "exerciseId" to rm.exerciseId,
                                    "reps" to rm.reps,
                                    "weight" to rm.weight,
                                    "date" to rm.date.toString(),
                                    "cycleId" to rm.cycleId
                                )
                                if (exName != null) {
                                    rmMap["exerciseName"] = exName
                                }
                                if (cycName != null) {
                                    rmMap["cycleName"] = cycName
                                }
                                rmMap
                            }
                            uploadCollectionWithGuard(
                                collectionName = "rep_maxes",
                                docRef = doc?.collection("data")?.document("rep_maxes"),
                                payload = repMaxesPayload,
                                isLocallyEmpty = repMaxes.isEmpty()
                            )
                        }
                    }

                    val taskWeightEntries = async {
                        runCatching {
                            val weightEntries = repo.getAllWeightEntriesIncludingTombstones()
                                .distinctBy { it.date }
                            val weightPayload = weightEntries.map { we ->
                                mapOf(
                                    "date" to we.date.toEpochDay(),
                                    "weightKg" to we.weightKg,
                                    "notes" to we.notes,
                                    "updatedAtMillis" to we.updatedAtMillis,
                                    "deletedAtMillis" to we.deletedAtMillis
                                )
                            }
                            uploadCollectionWithGuard(
                                collectionName = "weight_entries",
                                docRef = doc?.collection("data")?.document("weight_entries"),
                                payload = weightPayload,
                                isLocallyEmpty = weightPayload.isEmpty()
                            )
                        }
                    }

                    // Architecture Note: Cycle entities and their bitmask fields (fastDaysOfWeek, restDaysOfWeek)
                    // are strictly local-only and intentionally excluded from Cloud Firestore synchronization.
                    val taskDailyLogs = async {
                        runCatching {
                            val dailyLogs = repo.getAllDailyLogsIncludingTombstones()
                                .distinctBy { it.date }
                            val dailyLogsPayload = dailyLogs.map { dl ->
                                mapOf(
                                    "date" to dl.date.toEpochDay(),
                                    "fastCompleted" to dl.fastCompleted,
                                    "isRestDay" to dl.isRestDay,
                                    "caloriesKcal" to dl.caloriesKcal,
                                    "proteinGrams" to dl.proteinGrams,
                                    "carbsGrams" to dl.carbsGrams,
                                    "fatGrams" to dl.fatGrams,
                                    "notes" to dl.notes,
                                    "updatedAtMillis" to dl.updatedAtMillis,
                                    "deletedAtMillis" to dl.deletedAtMillis
                                )
                            }
                            uploadCollectionWithGuard(
                                collectionName = "daily_logs",
                                docRef = doc?.collection("data")?.document("daily_logs"),
                                payload = dailyLogsPayload,
                                isLocallyEmpty = dailyLogsPayload.isEmpty()
                            )
                        }
                    }

                    val resExercises = taskExercises.await()
                    val resRoutines = taskRoutines.await()
                    val resSessions = taskSessions.await()
                    val resGoals = taskGoals.await()
                    val resRepMaxes = taskRepMaxes.await()
                    val resWeightEntries = taskWeightEntries.await()
                    val resDailyLogs = taskDailyLogs.await()

                    resExercises.exceptionOrNull()?.let { collectionErrors["exercises"] = it }
                    resRoutines.exceptionOrNull()?.let { collectionErrors["routines"] = it }
                    resSessions.exceptionOrNull()?.let { collectionErrors["sessions"] = it }
                    resGoals.exceptionOrNull()?.let { collectionErrors["cycle_goals"] = it }
                    resRepMaxes.exceptionOrNull()?.let { collectionErrors["rep_maxes"] = it }
                    resWeightEntries.exceptionOrNull()?.let { collectionErrors["weight_entries"] = it }
                    resDailyLogs.exceptionOrNull()?.let { collectionErrors["daily_logs"] = it }
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

    /**
     * Downloads the signed-in user's cloud collections into the local [repo].
     *
     * @param importUnmarkedRemoteRecords When `true` (default — login / device-restore flows), every
     * remote exercise, routine, session, cycle goal and rep max missing locally is imported. When
     * `false` (manual [com.fractanomics.crosstraining.ui.AppViewModel.triggerCloudSync], which runs
     * download *before* upload), only records carrying an `updatedAtMillis` stamp are imported or
     * applied in place. The Android upload payload never writes `updatedAtMillis` for these five
     * collections, so the stamp identifies pending remote (MCP server) edits; unstamped remote
     * records are this device's own last upload, and re-importing them would resurrect rows the user
     * deleted locally (these collections have no tombstones) or overwrite newer local edits.
     * Weight entries and daily logs always use their tombstone-aware Last-Write-Wins merge.
     */
    suspend fun downloadUserData(
        repo: Repository,
        importUnmarkedRemoteRecords: Boolean = true
    ): Result<Unit> {
        val testHandler = downloadUserDataHandler
        if (testHandler != null) {
            return testHandler(repo)
        }
        return syncMutex.withLock {
            downloadUserDataInternal(repo, importUnmarkedRemoteRecords)
        }
    }

    private suspend fun downloadUserDataInternal(
        repo: Repository,
        importUnmarkedRemoteRecords: Boolean
    ): Result<Unit> {
        return runCatching {
            _syncState.value = SyncStatus.SYNCING

            withTimeout(20000L) {
                verifyTokenBinding().getOrThrow()
                ensureAuthenticated()
                val uid = currentUserId
                if (uid.isBlank()) error(CloudSyncErrorMapper.GUEST_AUTH_PROMPT)

                // Clean any existing local duplicate rows before reconciling remote data
                repo.cleanupDuplicateData()

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
                var weightList = fetchCollection(uid, "weight_entries")
                var dailyLogsList = fetchCollection(uid, "daily_logs")

                val isNewUidEmpty = exList.isEmpty() && routList.isEmpty() && sessList.isEmpty() && goalsList.isEmpty() && rmList.isEmpty() && weightList.isEmpty() && dailyLogsList.isEmpty()
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
                    var legacyWeight = fetchCollection(legacyUid, "weight_entries")
                    var legacyDailyLogs = fetchCollection(legacyUid, "daily_logs")

                    val normalizedLegacyUid = normalizeEmail(userEmail).replace("/", "_")
                    if (legacyEx.isEmpty() && legacyRout.isEmpty() && legacySess.isEmpty() && legacyGoals.isEmpty() && legacyRm.isEmpty() && legacyWeight.isEmpty() && legacyDailyLogs.isEmpty() && normalizedLegacyUid != legacyUid) {
                        legacyEx = fetchCollection(normalizedLegacyUid, "exercises")
                        legacyRout = fetchCollection(normalizedLegacyUid, "routines")
                        legacySess = fetchCollection(normalizedLegacyUid, "sessions")
                        legacyGoals = fetchCollection(normalizedLegacyUid, "cycle_goals")
                        legacyRm = fetchCollection(normalizedLegacyUid, "rep_maxes")
                        legacyWeight = fetchCollection(normalizedLegacyUid, "weight_entries")
                        legacyDailyLogs = fetchCollection(normalizedLegacyUid, "daily_logs")
                    }

                    val hasLegacyData = legacyEx.isNotEmpty() || legacyRout.isNotEmpty() || legacySess.isNotEmpty() || legacyGoals.isNotEmpty() || legacyRm.isNotEmpty() || legacyWeight.isNotEmpty() || legacyDailyLogs.isNotEmpty()
                    if (hasLegacyData) {
                        exList = legacyEx
                        routList = legacyRout
                        sessList = legacySess
                        goalsList = legacyGoals
                        rmList = legacyRm
                        weightList = legacyWeight
                        dailyLogsList = legacyDailyLogs
                        isMigratedFromLegacy = true
                    }
                }

                // Pending remote (MCP) edits carry an updatedAtMillis stamp; see KDoc above.
                // A legacy-account migration is always a full import.
                val importAllRemoteRecords = importUnmarkedRemoteRecords || isMigratedFromLegacy
                fun shouldImport(map: Map<String, Any>?): Boolean =
                    importAllRemoteRecords || map?.get("updatedAtMillis") != null

                // 1. Download Exercises & Build Remote-to-Local Exercise Mapping
                val remoteExerciseIdToLocalId = mutableMapOf<Long, Long>()
                if (exList.isNotEmpty()) {
                    val localExercises = if (importAllRemoteRecords) emptyList() else repo.getAllExercisesOnce()
                    exList.forEach { map ->
                        val name = map["name"] as? String ?: return@forEach
                        val category = runCatching { ExerciseCategory.valueOf(map["category"] as String) }.getOrDefault(ExerciseCategory.BARBELL)
                        val metricType = runCatching { MetricType.valueOf(map["metricType"] as String) }.getOrDefault(MetricType.WEIGHT)
                        val localEx = if (shouldImport(map)) {
                            repo.getOrCreateExercise(name, category, metricType)
                        } else {
                            // Map onto an existing local exercise only; never resurrect a locally deleted one.
                            localExercises.firstOrNull { it.name.trim().equals(name.trim(), ignoreCase = true) }
                                ?: return@forEach
                        }
                        val remoteId = (map["id"] as? Number)?.toLong()
                        if (remoteId != null) {
                            remoteExerciseIdToLocalId[remoteId] = localEx.id
                        }
                    }
                }

                suspend fun resolveLocalExerciseId(rawId: Long?, rawName: String?): Long? {
                    if (!rawName.isNullOrBlank()) {
                        return repo.getOrCreateExercise(rawName.trim()).id
                    }
                    if (rawId != null) {
                        return remoteExerciseIdToLocalId[rawId] ?: rawId
                    }
                    return null
                }

                suspend fun resolveExerciseCsv(idsCsv: String?, namesCsv: String?): String {
                    if (!namesCsv.isNullOrBlank()) {
                        val ids = namesCsv.split(",")
                            .mapNotNull { n -> n.trim().takeIf { it.isNotEmpty() } }
                            .map { name -> repo.getOrCreateExercise(name).id }
                            .distinct()
                        if (ids.isNotEmpty()) return ids.joinToString(",")
                    }
                    if (!idsCsv.isNullOrBlank()) {
                        return idsCsv.split(",")
                            .mapNotNull { s -> s.trim().toLongOrNull() }
                            .map { id -> remoteExerciseIdToLocalId[id] ?: id }
                            .distinct()
                            .joinToString(",")
                    }
                    return ""
                }

                // Cached once per download (cycles are local-only and only created below), instead of
                // re-reading the entire database via exportSnapshot() for every goal / session / rep max.
                var cachedLocalCycles: List<Cycle>? = null
                suspend fun resolveLocalCycleId(rawCycleId: Long?, rawCycleName: String?): Long? {
                    val localCycles = cachedLocalCycles ?: repo.getAllCyclesOnce().also { cachedLocalCycles = it }
                    if (localCycles.isEmpty()) {
                        if (!rawCycleName.isNullOrBlank()) {
                            val createdId = repo.saveCycle(
                                Cycle(name = rawCycleName.trim(), startDate = LocalDate.now(), isActive = true)
                            )
                            cachedLocalCycles = null
                            return createdId
                        }
                        return rawCycleId
                    }
                    if (!rawCycleName.isNullOrBlank()) {
                        localCycles.find { it.name.trim().equals(rawCycleName.trim(), ignoreCase = true) }?.let {
                            return it.id
                        }
                    }
                    if (rawCycleId != null && localCycles.any { it.id == rawCycleId }) {
                        return rawCycleId
                    }
                    return (localCycles.find { it.isActive } ?: localCycles.first()).id
                }

                // 2. Download Routines (deduplicated by normalized routine name)
                if (routList.isNotEmpty()) {
                    val deduplicatedRoutines = LinkedHashMap<String, Map<String, Any>>()
                    routList.forEach { rwbMap ->
                        @Suppress("UNCHECKED_CAST")
                        val rMap = rwbMap["routine"] as? Map<String, Any> ?: return@forEach
                        if (!shouldImport(rMap)) return@forEach
                        val name = (rMap["name"] as? String)?.trim() ?: return@forEach
                        if (name.isEmpty()) return@forEach
                        val key = name.lowercase()
                        val existing = deduplicatedRoutines[key]
                        if (existing == null) {
                            deduplicatedRoutines[key] = rwbMap
                        } else {
                            @Suppress("UNCHECKED_CAST")
                            val prevRMap = existing["routine"] as? Map<String, Any>
                            val prevTs = (prevRMap?.get("updatedAtMillis") as? Number)?.toLong() ?: 0L
                            val newTs = (rMap["updatedAtMillis"] as? Number)?.toLong() ?: 0L
                            if (newTs >= prevTs) {
                                deduplicatedRoutines[key] = rwbMap
                            }
                        }
                    }

                    deduplicatedRoutines.values.forEach { rwbMap ->
                        @Suppress("UNCHECKED_CAST")
                        val rMap = rwbMap["routine"] as? Map<String, Any> ?: return@forEach
                        val name = (rMap["name"] as? String)?.trim() ?: return@forEach
                        val description = rMap["description"] as? String ?: ""
                        val defaultFormat = rMap["defaultFormat"] as? String ?: ""
                        val mainExId = resolveLocalExerciseId(
                            (rMap["mainExerciseId"] as? Number)?.toLong(),
                            rMap["mainExerciseName"] as? String
                        )

                        @Suppress("UNCHECKED_CAST")
                        val bList = rwbMap["blocks"] as? List<Map<String, Any>> ?: emptyList()
                        val blocks = bList.mapIndexed { idx, bMap ->
                            val resolvedCsv = resolveExerciseCsv(
                                bMap["exerciseIdsCsv"] as? String,
                                bMap["exerciseNamesCsv"] as? String
                            )
                            RoutineBlock(
                                id = 0,
                                routineId = 0,
                                position = (bMap["position"] as? Number)?.toInt() ?: idx,
                                name = bMap["name"] as? String ?: "",
                                kind = runCatching { BlockKind.valueOf(bMap["kind"] as String) }.getOrDefault(BlockKind.WEIGHTLIFTING),
                                format = bMap["format"] as? String ?: "",
                                setsCount = (bMap["setsCount"] as? Number)?.toInt() ?: 1,
                                targetRepsScheme = bMap["targetRepsScheme"] as? String ?: "",
                                exerciseIdsCsv = resolvedCsv,
                                notes = bMap["notes"] as? String ?: "",
                                section = bMap["section"] as? String ?: "",
                                subBlock = bMap["subBlock"] as? String ?: ""
                            )
                        }
                        repo.saveRoutineWithBlocks(
                            Routine(name = name, description = description, mainExerciseId = mainExId, defaultFormat = defaultFormat),
                            blocks
                        )
                    }
                    repo.cleanupDuplicateRoutines()
                }

                // 3. Download Cycle Goals (Deduplicate incoming & upsert in place by natural key)
                if (goalsList.isNotEmpty()) {
                    data class IncomingGoalKey(
                        val cycleId: Long,
                        val exerciseId: Long,
                        val goalMetric: MetricType,
                        val targetReps: Int
                    )
                    data class IncomingGoalCandidate(
                        val goal: CycleGoal,
                        val updatedAtMillis: Long
                    )

                    val deduplicatedIncomingGoals = LinkedHashMap<IncomingGoalKey, IncomingGoalCandidate>()
                    goalsList.forEach { map ->
                        if (!shouldImport(map)) return@forEach
                        val rawCycleId = (map["cycleId"] as? Number)?.toLong()
                        val rawCycleName = map["cycleName"] as? String
                        if (rawCycleId == null && rawCycleName.isNullOrBlank()) return@forEach
                        val cycleId = resolveLocalCycleId(rawCycleId, rawCycleName) ?: return@forEach
                        val exerciseId = resolveLocalExerciseId(
                            (map["exerciseId"] as? Number)?.toLong(),
                            map["exerciseName"] as? String
                        ) ?: return@forEach
                        val targetReps = (map["targetReps"] as? Number)?.toInt() ?: 1
                        val startWeight = (map["startWeight"] as? Number)?.toDouble() ?: 0.0
                        val targetWeight = (map["targetWeight"] as? Number)?.toDouble() ?: 0.0
                        val notes = map["notes"] as? String ?: ""
                        val goalMetric = CycleGoal.parseMetadata(notes).first
                        val updatedAtMillis = (map["updatedAtMillis"] as? Number)?.toLong() ?: 0L

                        val key = IncomingGoalKey(cycleId, exerciseId, goalMetric, targetReps)
                        val candidateGoal = CycleGoal(
                            id = 0L,
                            cycleId = cycleId,
                            exerciseId = exerciseId,
                            targetReps = targetReps,
                            startWeight = startWeight,
                            targetWeight = targetWeight,
                            notes = notes
                        )
                        val existingCandidate = deduplicatedIncomingGoals[key]
                        if (existingCandidate == null) {
                            deduplicatedIncomingGoals[key] = IncomingGoalCandidate(candidateGoal, updatedAtMillis)
                        } else if (updatedAtMillis >= existingCandidate.updatedAtMillis) {
                            val mergedGoal = candidateGoal.copy(
                                startWeight = if (candidateGoal.startWeight > 0.0) candidateGoal.startWeight else existingCandidate.goal.startWeight,
                                targetWeight = if (candidateGoal.targetWeight > 0.0) candidateGoal.targetWeight else existingCandidate.goal.targetWeight,
                                notes = candidateGoal.notes.ifBlank { existingCandidate.goal.notes }
                            )
                            deduplicatedIncomingGoals[key] = IncomingGoalCandidate(mergedGoal, updatedAtMillis)
                        }
                    }

                    val existingGoals = repo.snapshotCycleGoals().toMutableList()
                    deduplicatedIncomingGoals.values.forEach { candidate ->
                        val incoming = candidate.goal
                        val existingIndex = existingGoals.indexOfFirst {
                            it.cycleId == incoming.cycleId &&
                                it.exerciseId == incoming.exerciseId &&
                                it.goalMetric == incoming.goalMetric &&
                                it.targetReps == incoming.targetReps
                        }
                        val existing = if (existingIndex >= 0) existingGoals[existingIndex] else null
                        val goalToSave = incoming.copy(id = existing?.id ?: 0L)
                        val savedId = repo.saveCycleGoal(goalToSave)
                        val savedGoal = goalToSave.copy(id = savedId)
                        if (existingIndex >= 0) {
                            existingGoals[existingIndex] = savedGoal
                        } else {
                            existingGoals.add(savedGoal)
                        }
                    }
                }

                // 4. Download Sessions & Logs (Workouts — Deduplicate incoming & upsert in place)
                if (sessList.isNotEmpty()) {
                    // Incoming natural key = (date, title, block/set content). Exact remote copies collapse,
                    // while distinct same-titled workouts logged on the same day are both preserved.
                    val deduplicatedSessions = LinkedHashMap<Triple<LocalDate, String, String>, Map<String, Any>>()
                    sessList.forEach { swbMap ->
                        @Suppress("UNCHECKED_CAST")
                        val sMap = swbMap["session"] as? Map<String, Any> ?: return@forEach
                        val dateStr = sMap["date"] as? String ?: return@forEach
                        val date = runCatching { LocalDate.parse(dateStr) }.getOrNull() ?: return@forEach
                        val title = (sMap["title"] as? String) ?: ""
                        val key = Triple(date, title.trim().lowercase(), remoteSessionContentSignature(swbMap))
                        val existing = deduplicatedSessions[key]
                        if (existing == null) {
                            deduplicatedSessions[key] = swbMap
                        } else {
                            @Suppress("UNCHECKED_CAST")
                            val prevSMap = existing["session"] as? Map<String, Any>
                            val prevTs = (prevSMap?.get("updatedAtMillis") as? Number)?.toLong() ?: 0L
                            val newTs = (sMap["updatedAtMillis"] as? Number)?.toLong() ?: 0L
                            if (newTs >= prevTs) {
                                deduplicatedSessions[key] = swbMap
                            }
                        }
                    }

                    val existingSessions = repo.getAllSessionsWithBlocksOnce().toMutableList()
                    val incomingSessions = deduplicatedSessions.values.toList()
                    // Local session ids already paired with an incoming record during this pass, so a second
                    // distinct same-titled remote session is never collapsed onto (or overwrites) the first.
                    val claimedLocalSessionIds = mutableSetOf<Long>()
                    // Pass 1: pair incoming sessions with the local session of identical date/title/content
                    // first, so a stamped (edited) remote session can only fall back to a local session that
                    // has no exact remote counterpart — independent of remote list order.
                    val exactPairByIncomingIndex = HashMap<Int, Long>()
                    run {
                        val exerciseNamesById = repo.getAllExercisesOnce().associate { it.id to it.name }
                        val localSignatures = existingSessions.associate {
                            it.session.id to remoteSessionContentSignature(localSessionAsRemotePayload(it, exerciseNamesById))
                        }
                        incomingSessions.forEachIndexed { incomingIndex, swbMap ->
                            @Suppress("UNCHECKED_CAST")
                            val sMap = swbMap["session"] as? Map<String, Any> ?: return@forEachIndexed
                            val date = (sMap["date"] as? String)?.let { runCatching { LocalDate.parse(it) }.getOrNull() }
                                ?: return@forEachIndexed
                            val title = (sMap["title"] as? String ?: "").trim()
                            val signature = remoteSessionContentSignature(swbMap)
                            existingSessions.firstOrNull {
                                it.session.id !in claimedLocalSessionIds &&
                                    it.session.date == date &&
                                    it.session.title.trim().equals(title, ignoreCase = true) &&
                                    localSignatures[it.session.id] == signature
                            }?.let {
                                exactPairByIncomingIndex[incomingIndex] = it.session.id
                                claimedLocalSessionIds.add(it.session.id)
                            }
                        }
                    }
                    // Pass 2: reconcile every incoming session (exact pair, else first unclaimed date/title match).
                    incomingSessions.forEachIndexed { incomingIndex, swbMap ->
                        @Suppress("UNCHECKED_CAST")
                        val sMap = swbMap["session"] as? Map<String, Any> ?: return@forEachIndexed
                        val dateStr = sMap["date"] as? String ?: return@forEachIndexed
                        val date = runCatching { LocalDate.parse(dateStr) }.getOrNull() ?: return@forEachIndexed
                        val title = sMap["title"] as? String ?: ""
                        val notes = sMap["notes"] as? String ?: ""
                        val rawCycleId = (sMap["cycleId"] as? Number)?.toLong()
                        val rawCycleName = sMap["cycleName"] as? String
                        val hasRemoteUpdateTimestamp = sMap["updatedAtMillis"] != null

                        val exactLocalId = exactPairByIncomingIndex[incomingIndex]
                        val existingIndex = if (exactLocalId != null) {
                            existingSessions.indexOfFirst { it.session.id == exactLocalId }
                        } else {
                            existingSessions.indexOfFirst {
                                it.session.id !in claimedLocalSessionIds &&
                                    it.session.date == date &&
                                    it.session.title.trim().equals(title.trim(), ignoreCase = true)
                            }
                        }
                        val existingSession = if (existingIndex >= 0) existingSessions[existingIndex] else null
                        existingSession?.let { claimedLocalSessionIds.add(it.session.id) }
                        // Do not resurrect a session the user deleted locally since the last upload.
                        if (existingSession == null && !shouldImport(sMap)) return@forEachIndexed
                        // Unchanged (unstamped) sessions that already exist locally need no work at all.
                        if (existingSession != null && !hasRemoteUpdateTimestamp) return@forEachIndexed
                        val cycleId = resolveLocalCycleId(rawCycleId, rawCycleName) ?: (rawCycleId ?: 0L)
                        if (existingSession == null || hasRemoteUpdateTimestamp) {
                            @Suppress("UNCHECKED_CAST")
                            val bList = swbMap["blocks"] as? List<Map<String, Any>> ?: emptyList()
                            val blockInserts = bList.mapIndexed { bIdx, bMapRaw ->
                                @Suppress("UNCHECKED_CAST")
                                val bMap = bMapRaw["block"] as? Map<String, Any> ?: bMapRaw
                                val isCompleted = when (val c = bMap["isCompleted"]) {
                                    is Boolean -> c
                                    is Number -> c.toInt() != 0
                                    null -> true
                                    else -> true
                                }
                                val resolvedMainExId = resolveLocalExerciseId(
                                    (bMap["mainExerciseId"] as? Number)?.toLong(),
                                    bMap["mainExerciseName"] as? String
                                )
                                val resolvedExCsv = resolveExerciseCsv(
                                    bMap["exerciseIdsCsv"] as? String,
                                    bMap["exerciseNamesCsv"] as? String
                                )
                                val block = SessionBlock(
                                    id = 0,
                                    sessionId = 0,
                                    position = (bMap["position"] as? Number)?.toInt() ?: bIdx,
                                    name = bMap["name"] as? String ?: "",
                                    kind = runCatching { BlockKind.valueOf(bMap["kind"] as String) }.getOrDefault(BlockKind.WEIGHTLIFTING),
                                    format = bMap["format"] as? String ?: "",
                                    scheme = bMap["scheme"] as? String ?: "",
                                    mainExerciseId = resolvedMainExId,
                                    routineId = (bMap["routineId"] as? Number)?.toLong(),
                                    description = bMap["description"] as? String ?: "",
                                    resultText = bMap["resultText"] as? String ?: "",
                                    resultValue = (bMap["resultValue"] as? Number)?.toDouble(),
                                    notes = bMap["notes"] as? String ?: "",
                                    section = bMap["section"] as? String ?: "",
                                    exerciseIdsCsv = resolvedExCsv,
                                    subBlock = bMap["subBlock"] as? String ?: "",
                                    isCompleted = isCompleted
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
                            if (existingSession == null) {
                                val newSession = Session(id = 0, cycleId = cycleId, date = date, title = title, notes = notes)
                                val savedId = repo.saveSession(newSession, blockInserts)
                                claimedLocalSessionIds.add(savedId)
                                repo.sessionById(savedId)?.let { existingSessions.add(it) }
                                    ?: existingSessions.add(SessionWithBlocks(newSession.copy(id = savedId), emptyList()))
                            } else {
                                val updatedSession = existingSession.session.copy(cycleId = cycleId, title = title, notes = notes)
                                repo.updateSession(updatedSession, blockInserts)
                                repo.sessionById(updatedSession.id)?.let { existingSessions[existingIndex] = it }
                            }
                        }
                    }
                }

                // 5. Download Rep Maxes (PR History — Deduplicate incoming & check live mutable list)
                if (rmList.isNotEmpty()) {
                    val existingRms = repo.exportSnapshot().repMaxes.toMutableList()
                    rmList.forEach { map ->
                        if (!shouldImport(map)) return@forEach
                        val exerciseId = resolveLocalExerciseId(
                            (map["exerciseId"] as? Number)?.toLong(),
                            map["exerciseName"] as? String
                        ) ?: return@forEach
                        val reps = (map["reps"] as? Number)?.toInt() ?: return@forEach
                        val weight = (map["weight"] as? Number)?.toDouble() ?: return@forEach
                        val dateStr = map["date"] as? String ?: return@forEach
                        val date = runCatching { LocalDate.parse(dateStr) }.getOrNull() ?: return@forEach
                        val cycleId = resolveLocalCycleId(
                            (map["cycleId"] as? Number)?.toLong(),
                            map["cycleName"] as? String
                        )
                        val normWeight = normalizeWeight(weight)

                        val exists = existingRms.any {
                            it.exerciseId == exerciseId &&
                                it.reps == reps &&
                                normalizeWeight(it.weight) == normWeight &&
                                it.date == date
                        }
                        if (!exists) {
                            val savedId = repo.recordRepMax(exerciseId, reps, weight, date, cycleId)
                            existingRms.add(
                                RepMax(
                                    id = savedId,
                                    exerciseId = exerciseId,
                                    reps = reps,
                                    weight = weight,
                                    date = date,
                                    cycleId = cycleId
                                )
                            )
                        }
                    }
                }

                // 6. Download Weight Entries with Tombstone-Aware Last-Write-Wins Merge
                if (weightList.isNotEmpty()) {
                    val localWeightEntries = repo.getAllWeightEntriesIncludingTombstones()
                    val localByDate = localWeightEntries.associateBy { it.date }

                    val remoteWinningByDate = LinkedHashMap<LocalDate, com.fractanomics.crosstraining.data.model.WeightEntry>()
                    weightList.forEach { map ->
                        val rawDate = map["date"] ?: return@forEach
                        val date = when (rawDate) {
                            is Number -> LocalDate.ofEpochDay(rawDate.toLong())
                            is String -> runCatching { LocalDate.parse(rawDate) }.getOrNull() ?: return@forEach
                            else -> return@forEach
                        }
                        val weightKg = (map["weightKg"] as? Number)?.toDouble() ?: return@forEach
                        val notes = map["notes"] as? String ?: ""
                        val updatedAtMillis = (map["updatedAtMillis"] as? Number)?.toLong() ?: 0L
                        val deletedAtMillis = (map["deletedAtMillis"] as? Number)?.toLong()
                        val candidate = com.fractanomics.crosstraining.data.model.WeightEntry(
                            date = date,
                            weightKg = weightKg,
                            notes = notes,
                            updatedAtMillis = updatedAtMillis,
                            deletedAtMillis = deletedAtMillis
                        )
                        val prev = remoteWinningByDate[date]
                        if (prev == null || maxOf(updatedAtMillis, deletedAtMillis ?: 0L) >= maxOf(prev.updatedAtMillis, prev.deletedAtMillis ?: 0L)) {
                            remoteWinningByDate[date] = candidate
                        }
                    }

                    val mergedWinningEntries = mutableListOf<com.fractanomics.crosstraining.data.model.WeightEntry>()
                    remoteWinningByDate.values.forEach { remoteEntry ->
                        val remoteEffectiveTs = maxOf(remoteEntry.updatedAtMillis, remoteEntry.deletedAtMillis ?: 0L)
                        val localEntry = localByDate[remoteEntry.date]
                        if (localEntry != null) {
                            val localEffectiveTs = maxOf(localEntry.updatedAtMillis, localEntry.deletedAtMillis ?: 0L)
                            if (remoteEffectiveTs >= localEffectiveTs) {
                                mergedWinningEntries.add(remoteEntry)
                            }
                        } else {
                            mergedWinningEntries.add(remoteEntry)
                        }
                    }

                    if (mergedWinningEntries.isNotEmpty()) {
                        repo.importWeightEntries(mergedWinningEntries)
                        repo.refreshActiveCycleBaseline()
                    }
                }

                // 7. Download Daily Logs with Tombstone-Aware Last-Write-Wins Merge
                if (dailyLogsList.isNotEmpty()) {
                    val localDailyLogs = repo.getAllDailyLogsIncludingTombstones()
                    val localByDate = localDailyLogs.associateBy { it.date }

                    val remoteWinningByDate = LinkedHashMap<LocalDate, com.fractanomics.crosstraining.data.model.DailyLog>()
                    dailyLogsList.forEach { map ->
                        val rawDate = map["date"] ?: return@forEach
                        val date = when (rawDate) {
                            is Number -> LocalDate.ofEpochDay(rawDate.toLong())
                            is String -> runCatching { LocalDate.parse(rawDate) }.getOrNull() ?: return@forEach
                            else -> return@forEach
                        }
                        val fastCompleted = when (val fc = map["fastCompleted"]) {
                            is Boolean -> fc
                            is Number -> fc.toInt() != 0
                            else -> null
                        }
                        val isRestDay = when (val rd = map["isRestDay"]) {
                            is Boolean -> rd
                            is Number -> rd.toInt() != 0
                            else -> false
                        }
                        val caloriesKcal = (map["caloriesKcal"] as? Number)?.toInt()
                        val proteinGrams = (map["proteinGrams"] as? Number)?.toInt()
                        val carbsGrams = (map["carbsGrams"] as? Number)?.toInt()
                        val fatGrams = (map["fatGrams"] as? Number)?.toInt()
                        val notes = map["notes"] as? String ?: ""
                        val updatedAtMillis = (map["updatedAtMillis"] as? Number)?.toLong() ?: 0L
                        val deletedAtMillis = (map["deletedAtMillis"] as? Number)?.toLong()

                        val candidate = com.fractanomics.crosstraining.data.model.DailyLog(
                            date = date,
                            fastCompleted = fastCompleted,
                            isRestDay = isRestDay,
                            caloriesKcal = caloriesKcal,
                            proteinGrams = proteinGrams,
                            carbsGrams = carbsGrams,
                            fatGrams = fatGrams,
                            notes = notes,
                            updatedAtMillis = updatedAtMillis,
                            deletedAtMillis = deletedAtMillis
                        )
                        val prev = remoteWinningByDate[date]
                        if (prev == null || maxOf(updatedAtMillis, deletedAtMillis ?: 0L) >= maxOf(prev.updatedAtMillis, prev.deletedAtMillis ?: 0L)) {
                            remoteWinningByDate[date] = candidate
                        }
                    }

                    val mergedWinningEntries = mutableListOf<com.fractanomics.crosstraining.data.model.DailyLog>()
                    remoteWinningByDate.values.forEach { remoteEntry ->
                        val remoteEffectiveTs = maxOf(remoteEntry.updatedAtMillis, remoteEntry.deletedAtMillis ?: 0L)
                        val localEntry = localByDate[remoteEntry.date]
                        if (localEntry != null) {
                            val localEffectiveTs = maxOf(localEntry.updatedAtMillis, localEntry.deletedAtMillis ?: 0L)
                            if (remoteEffectiveTs >= localEffectiveTs) {
                                mergedWinningEntries.add(remoteEntry)
                            }
                        } else {
                            mergedWinningEntries.add(remoteEntry)
                        }
                    }

                    if (mergedWinningEntries.isNotEmpty()) {
                        repo.importDailyLogs(mergedWinningEntries)
                    }
                }

                // Final pass to guarantee zero duplicates after download reconciliation
                repo.cleanupDuplicateData()

                // If migrated from legacy path, re-upload to users/{newUid}
                if (isMigratedFromLegacy) {
                    uploadUserDataInternal(repo).getOrThrow()
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
        return syncMutex.withLock {
            recoverAllCloudRoutinesInternal(repo)
        }
    }

    private suspend fun recoverAllCloudRoutinesInternal(repo: Repository): Result<Int> {
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
                repo.cleanupDuplicateData()

                if (migratedFromLegacy && count > 0) {
                    uploadUserDataInternal(repo).getOrThrow()
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
