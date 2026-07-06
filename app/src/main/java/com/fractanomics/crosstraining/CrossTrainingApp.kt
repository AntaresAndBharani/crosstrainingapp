package com.fractanomics.crosstraining

import android.app.Application
import com.fractanomics.crosstraining.data.DataModeManager

/** Application that owns the data-mode manager (real vs demo database). */
class CrossTrainingApp : Application() {
    val dataModes: DataModeManager by lazy { DataModeManager(this) }
}
