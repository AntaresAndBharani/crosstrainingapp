package com.fractanomics.crosstraining.ui.timer

import android.content.Context

/**
 * Provider that hoists [TimerEngine] to application scope, allowing TimerScreen,
 * ViewModels, and TimerService to observe and control the exact same timer state.
 */
object TimerEngineProvider {
    @Volatile
    private var instance: TimerEngine? = null

    fun get(context: Context? = null): TimerEngine {
        return instance ?: synchronized(this) {
            instance ?: TimerEngine(context?.applicationContext).also { instance = it }
        }
    }

    fun getInstance(context: Context? = null): TimerEngine = get(context)

    fun setInstanceForTesting(engine: TimerEngine?) {
        instance = engine
    }
}
