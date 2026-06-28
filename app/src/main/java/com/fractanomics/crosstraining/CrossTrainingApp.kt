package com.fractanomics.crosstraining

import android.app.Application
import com.fractanomics.crosstraining.data.AppDatabase
import com.fractanomics.crosstraining.data.Repository

/** Application that owns the database/repository singletons. */
class CrossTrainingApp : Application() {
    val repository: Repository by lazy { Repository(AppDatabase.get(this)) }
}
