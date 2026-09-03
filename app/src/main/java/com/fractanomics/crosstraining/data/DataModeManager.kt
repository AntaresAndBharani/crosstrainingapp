package com.fractanomics.crosstraining.data

import android.content.Context
import com.fractanomics.crosstraining.data.model.UserRole
import com.fractanomics.crosstraining.ui.theme.AppThemeMode
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map

import com.fractanomics.crosstraining.data.firebase.AuthUser

/**
 * Switches the app between the user's real database and a separate,
 * pre-populated demo database (see [DemoData]). The two never mix: demo mode
 * points the UI at another database file, so anything logged while exploring
 * demo data cannot touch the user's history. The chosen mode is persisted so
 * Data mode is in-memory and session-scoped, defaulting to real data on every app launch
 * so demo exploration never persists across launches.
 *
 * Also manages app-level preference persistence, such as [AppThemeMode], [UserRole],
 * and authenticated user session persistence ("Remember Me").
 */
open class DataModeManager(
    context: Context? = null,
    sharedPreferences: android.content.SharedPreferences? = null
) {

    private val appContext = context?.applicationContext ?: context
    private val prefs = sharedPreferences ?: appContext?.getSharedPreferences("crosstraining-prefs", Context.MODE_PRIVATE)

    private val underlyingRealRepository by lazy {
        val ctx = appContext ?: error("Context required for real repository")
        Repository(AppDatabase.get(ctx))
    }
    private val demoRepository by lazy {
        val ctx = appContext ?: error("Context required for demo repository")
        Repository(AppDatabase.demo(ctx))
    }

    private val _demoMode = MutableStateFlow(false)
    val demoMode: StateFlow<Boolean> = _demoMode

    private val _themeMode = MutableStateFlow(
        runCatching {
            val saved = prefs?.getString(KEY_THEME_MODE, AppThemeMode.LIGHT.name)
            AppThemeMode.valueOf(saved ?: AppThemeMode.LIGHT.name)
        }.getOrDefault(AppThemeMode.LIGHT)
    )
    val themeMode: StateFlow<AppThemeMode> = _themeMode

    private val _userRole = MutableStateFlow(
        runCatching {
            val saved = prefs?.getString(KEY_USER_ROLE, null)
            if (saved != null) {
                UserRole.valueOf(saved)
            } else {
                val savedEmail = prefs?.getString(KEY_SAVED_USER_EMAIL, null)
                resolveRoleForUser(savedEmail)
            }
        }.getOrDefault(UserRole.ATHLETE)
    )
    val userRole: StateFlow<UserRole> = _userRole

    /** Set and persist the app theme mode. */
    fun setThemeMode(mode: AppThemeMode) {
        prefs?.edit()?.putString(KEY_THEME_MODE, mode.name)?.apply()
        _themeMode.value = mode
    }

    /** Set and persist the app user role (Athlete vs Coach). */
    fun setUserRole(role: UserRole) {
        prefs?.edit()?.putString(KEY_USER_ROLE, role.name)?.apply()
        _userRole.value = role
    }

    /** Save authenticated user session to survive app updates and reboots. */
    fun saveAuthSession(email: String?, uid: String?, isAnon: Boolean = false, remember: Boolean = true) {
        if (!remember || email.isNullOrBlank()) {
            clearAuthSession()
            return
        }
        if (uid.isNullOrBlank() || uid.contains("@")) {
            runCatching {
                android.util.Log.w("DataModeManager", "Rejected invalid UID (blank or email string): '$uid'")
            }
            return
        }
        val determinedRole = resolveRoleForUser(email)
        setUserRole(determinedRole)
        prefs?.edit()
            ?.putString(KEY_SAVED_USER_EMAIL, email)
            ?.putString(KEY_SAVED_USER_UID, uid)
            ?.putBoolean(KEY_SAVED_USER_IS_ANON, isAnon)
            ?.putBoolean(KEY_REMEMBER_ME, true)
            ?.apply()
    }

    /** Clear saved authenticated session on explicit sign out. */
    fun clearAuthSession() {
        prefs?.edit()
            ?.remove(KEY_SAVED_USER_EMAIL)
            ?.remove(KEY_SAVED_USER_UID)
            ?.remove(KEY_SAVED_USER_IS_ANON)
            ?.remove(KEY_REMEMBER_ME)
            ?.apply()
    }

    /** Rehydrate saved authenticated user session if available. */
    fun getPersistedAuthUser(): AuthUser? {
        val remember = prefs?.getBoolean(KEY_REMEMBER_ME, true) ?: true
        if (!remember) return null
        val email = prefs?.getString(KEY_SAVED_USER_EMAIL, null)
        val uid = prefs?.getString(KEY_SAVED_USER_UID, null)
        val isAnon = prefs?.getBoolean(KEY_SAVED_USER_IS_ANON, false) ?: false
        if (!email.isNullOrBlank() && !uid.isNullOrBlank()) {
            return AuthUser(uid = uid, email = email, isAnonymous = isAnon)
        }
        return null
    }

    /** Resolves the default user role given an email or username. */
    fun resolveRoleForUser(email: String?): UserRole {
        val normalized = email?.trim()?.lowercase() ?: return UserRole.ATHLETE
        return when {
            normalized == "pv.joseangel@gmail.com" || normalized == "coach@crosstraining.app" || normalized == "coach" -> UserRole.COACH
            normalized.startsWith("jangelpv") || normalized == "athlete@crosstraining.app" || normalized == "athlete" -> UserRole.ATHLETE
            else -> _userRole.value
        }
    }

    private var testRepository: Repository? = null
    private var testDemoRepository: Repository? = null

    /** Sets a repository override for unit and integration testing. */
    fun setRepositoryForTesting(repo: Repository?, demoRepo: Repository? = null) {
        testRepository = repo
        testDemoRepository = demoRepo
    }

    /** Real database repository backing athlete data; falls back to [testRepository] in tests. */
    val realRepository: Repository
        get() = testRepository ?: underlyingRealRepository

    /** Repository currently backing the UI. */
    val current: Repository
        get() = if (_demoMode.value) (testDemoRepository ?: testRepository ?: demoRepository) else realRepository

    /** Emits the active repository, switching live when the mode changes. */
    val repositoryFlow: Flow<Repository> =
        _demoMode.map { demo -> if (demo) (testDemoRepository ?: testRepository ?: demoRepository) else realRepository }

    /** Enable/disable demo mode in-memory; seeds the demo database on first use. */
    suspend fun setDemoMode(enabled: Boolean) {
        if (enabled) seedIfNeeded()
        _demoMode.value = enabled
    }

    /**
     * Re-seed if the app starts in demo mode with a dataset generated by an
     * older [DemoData.SEED_VERSION]. Demo edits are disposable by definition,
     * so upgrading the sample data wins over preserving them.
     */
    suspend fun refreshDemoIfStale() {
        if (_demoMode.value) seedIfNeeded()
    }

    /** Restore the demo database to its pristine generated dataset. */
    suspend fun resetDemoData() {
        if (appContext == null) return
        demoRepository.importSnapshot(DemoData.snapshot())
        prefs?.edit()?.putInt(KEY_SEED_VERSION, DemoData.SEED_VERSION)?.apply()
    }

    private suspend fun seedIfNeeded() {
        if (appContext == null) return
        val stale = (prefs?.getInt(KEY_SEED_VERSION, 0) ?: 0) < DemoData.SEED_VERSION
        if (stale || demoRepository.exportSnapshot().sessions.isEmpty()) resetDemoData()
    }

    private companion object {
        const val KEY_SEED_VERSION = "demoSeedVersion"
        const val KEY_THEME_MODE = "themeMode"
        const val KEY_USER_ROLE = "userRole"
        const val KEY_SAVED_USER_EMAIL = "savedUserEmail"
        const val KEY_SAVED_USER_UID = "savedUserUid"
        const val KEY_SAVED_USER_IS_ANON = "savedUserIsAnon"
        const val KEY_REMEMBER_ME = "rememberMe"
    }
}
