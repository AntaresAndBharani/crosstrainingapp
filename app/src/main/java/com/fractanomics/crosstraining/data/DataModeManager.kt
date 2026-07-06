package com.fractanomics.crosstraining.data

import android.content.Context
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map

/**
 * Switches the app between the user's real database and a separate,
 * pre-populated demo database (see [DemoData]). The two never mix: demo mode
 * points the UI at another database file, so anything logged while exploring
 * demo data cannot touch the user's history. The chosen mode is persisted so
 * the app reopens where it was left.
 */
class DataModeManager(context: Context) {

    private val appContext = context.applicationContext
    private val prefs = appContext.getSharedPreferences("crosstraining-prefs", Context.MODE_PRIVATE)

    private val realRepository by lazy { Repository(AppDatabase.get(appContext)) }
    private val demoRepository by lazy { Repository(AppDatabase.demo(appContext)) }

    private val _demoMode = MutableStateFlow(prefs.getBoolean(KEY_DEMO_MODE, false))
    val demoMode: StateFlow<Boolean> = _demoMode

    /** Repository currently backing the UI. */
    val current: Repository
        get() = if (_demoMode.value) demoRepository else realRepository

    /** Emits the active repository, switching live when the mode changes. */
    val repositoryFlow: Flow<Repository> =
        _demoMode.map { demo -> if (demo) demoRepository else realRepository }

    /** Enable/disable demo mode; seeds the demo database on first use. */
    suspend fun setDemoMode(enabled: Boolean) {
        if (enabled) seedIfEmpty()
        prefs.edit().putBoolean(KEY_DEMO_MODE, enabled).apply()
        _demoMode.value = enabled
    }

    /** Restore the demo database to its pristine generated dataset. */
    suspend fun resetDemoData() = demoRepository.importSnapshot(DemoData.snapshot())

    private suspend fun seedIfEmpty() {
        if (demoRepository.exportSnapshot().sessions.isEmpty()) resetDemoData()
    }

    private companion object {
        const val KEY_DEMO_MODE = "demoMode"
    }
}
