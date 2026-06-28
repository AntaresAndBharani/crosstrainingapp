package com.fractanomics.crosstraining.data

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import androidx.sqlite.db.SupportSQLiteDatabase
import com.fractanomics.crosstraining.data.dao.CycleDao
import com.fractanomics.crosstraining.data.dao.ExerciseDao
import com.fractanomics.crosstraining.data.dao.RepMaxDao
import com.fractanomics.crosstraining.data.dao.RoutineDao
import com.fractanomics.crosstraining.data.dao.SessionDao
import com.fractanomics.crosstraining.data.model.Cycle
import com.fractanomics.crosstraining.data.model.Exercise
import com.fractanomics.crosstraining.data.model.RepMax
import com.fractanomics.crosstraining.data.model.Routine
import com.fractanomics.crosstraining.data.model.Session
import com.fractanomics.crosstraining.data.model.SessionSet
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

@Database(
    entities = [
        Cycle::class,
        Exercise::class,
        Routine::class,
        Session::class,
        SessionSet::class,
        RepMax::class
    ],
    version = 1,
    exportSchema = false
)
@TypeConverters(Converters::class)
abstract class AppDatabase : RoomDatabase() {
    abstract fun cycleDao(): CycleDao
    abstract fun exerciseDao(): ExerciseDao
    abstract fun routineDao(): RoutineDao
    abstract fun sessionDao(): SessionDao
    abstract fun repMaxDao(): RepMaxDao

    companion object {
        @Volatile
        private var INSTANCE: AppDatabase? = null

        fun get(context: Context): AppDatabase =
            INSTANCE ?: synchronized(this) {
                INSTANCE ?: build(context).also { INSTANCE = it }
            }

        private fun build(context: Context): AppDatabase =
            Room.databaseBuilder(
                context.applicationContext,
                AppDatabase::class.java,
                "crosstraining.db"
            ).addCallback(object : Callback() {
                override fun onCreate(db: SupportSQLiteDatabase) {
                    super.onCreate(db)
                    // Seed the starter library of common lifts and machines.
                    val instance = INSTANCE ?: return
                    CoroutineScope(SupervisorJob() + Dispatchers.IO).launch {
                        SeedData.populate(instance.exerciseDao())
                    }
                }
            }).build()
    }
}
