package com.fractanomics.crosstraining

import android.app.Application
import com.fractanomics.crosstraining.data.DataModeManager
import com.fractanomics.crosstraining.ui.timer.TimerEngine
import com.fractanomics.crosstraining.ui.timer.TimerEngineProvider

/** Application that owns the data-mode manager and shared timer engine. */
class CrossTrainingApp : Application() {
    val dataModes: DataModeManager by lazy { DataModeManager(this) }
    val timerEngine: TimerEngine by lazy { TimerEngineProvider.get(this) }
}
