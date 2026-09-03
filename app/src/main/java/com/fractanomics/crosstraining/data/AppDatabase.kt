package com.fractanomics.crosstraining.data

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import com.fractanomics.crosstraining.data.dao.BlockDao
import com.fractanomics.crosstraining.data.dao.CycleDao
import com.fractanomics.crosstraining.data.dao.CycleGoalDao
import com.fractanomics.crosstraining.data.dao.ExerciseDao
import com.fractanomics.crosstraining.data.dao.RepMaxDao
import com.fractanomics.crosstraining.data.dao.RoutineDao
import com.fractanomics.crosstraining.data.dao.SessionDao
import com.fractanomics.crosstraining.data.model.BlockSet
import com.fractanomics.crosstraining.data.model.Cycle
import com.fractanomics.crosstraining.data.model.CycleGoal
import com.fractanomics.crosstraining.data.model.Exercise
import com.fractanomics.crosstraining.data.model.RepMax
import com.fractanomics.crosstraining.data.model.Routine
import com.fractanomics.crosstraining.data.model.RoutineBlock
import com.fractanomics.crosstraining.data.model.Session
import com.fractanomics.crosstraining.data.model.SessionBlock
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import java.time.LocalDate

@Database(
    entities = [
        Cycle::class,
        Exercise::class,
        Routine::class,
        RoutineBlock::class,
        Session::class,
        SessionBlock::class,
        BlockSet::class,
        RepMax::class,
        CycleGoal::class
    ],
    version = 5,
    exportSchema = false
)
@TypeConverters(Converters::class)
abstract class AppDatabase : RoomDatabase() {
    abstract fun cycleDao(): CycleDao
    abstract fun exerciseDao(): ExerciseDao
    abstract fun routineDao(): RoutineDao
    abstract fun sessionDao(): SessionDao
    abstract fun blockDao(): BlockDao
    abstract fun repMaxDao(): RepMaxDao
    abstract fun cycleGoalDao(): CycleGoalDao

    companion object {
        val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("""
                    CREATE TABLE IF NOT EXISTS `routine_blocks` (
                        `id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                        `routineId` INTEGER NOT NULL,
                        `position` INTEGER NOT NULL,
                        `name` TEXT NOT NULL,
                        `kind` TEXT NOT NULL,
                        `format` TEXT NOT NULL,
                        `setsCount` INTEGER NOT NULL,
                        `exerciseIdsCsv` TEXT NOT NULL,
                        `notes` TEXT NOT NULL,
                        FOREIGN KEY(`routineId`) REFERENCES `routines`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE
                    )
                """.trimIndent())
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_routine_blocks_routineId` ON `routine_blocks` (`routineId`)")
            }
        }

        val MIGRATION_2_3 = object : Migration(2, 3) {
            override fun migrate(db: SupportSQLiteDatabase) {
                // Version 2 to 3 migration placeholder
            }
        }

        val MIGRATION_3_4 = object : Migration(3, 4) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE `routine_blocks` ADD COLUMN `targetRepsScheme` TEXT NOT NULL DEFAULT ''")
            }
        }

        val MIGRATION_4_5 = object : Migration(4, 5) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("""
                    CREATE TABLE IF NOT EXISTS `cycle_goals` (
                        `id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                        `cycleId` INTEGER NOT NULL,
                        `exerciseId` INTEGER NOT NULL,
                        `targetReps` INTEGER NOT NULL DEFAULT 1,
                        `startWeight` REAL NOT NULL DEFAULT 0.0,
                        `targetWeight` REAL NOT NULL DEFAULT 0.0,
                        `notes` TEXT NOT NULL DEFAULT '',
                        FOREIGN KEY(`cycleId`) REFERENCES `cycles`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE,
                        FOREIGN KEY(`exerciseId`) REFERENCES `exercises`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE
                    )
                """.trimIndent())
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_cycle_goals_cycleId` ON `cycle_goals` (`cycleId`)")
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_cycle_goals_exerciseId` ON `cycle_goals` (`exerciseId`)")
            }
        }

        @Volatile
        private var INSTANCE: AppDatabase? = null

        @Volatile
        private var DEMO: AppDatabase? = null

        fun get(context: Context): AppDatabase =
            INSTANCE ?: synchronized(this) {
                INSTANCE ?: build(context).also { INSTANCE = it }
            }

        /**
         * Separate database file backing demo mode. Populated from [DemoData]
         * by [DataModeManager]; never mixes with the real database.
         */
        fun demo(context: Context): AppDatabase =
            DEMO ?: synchronized(this) {
                DEMO ?: Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    "crosstraining-demo.db"
                )
                .addMigrations(MIGRATION_1_2, MIGRATION_2_3, MIGRATION_3_4, MIGRATION_4_5)
                .fallbackToDestructiveMigrationOnDowngrade()
                .build().also { DEMO = it }
            }

        /**
         * Provisions a default active training cycle ("General Training") if the database has no cycles.
         * Ensures fresh and upgraded production installs can immediately log workouts without error.
         */
        suspend fun provisionDefaultCycleIfNeeded(database: AppDatabase): Cycle? {
            val cycleDao = database.cycleDao()
            val existing = cycleDao.getAllOnce()
            if (existing.isEmpty()) {
                val defaultCycle = Cycle(
                    name = "General Training",
                    startDate = LocalDate.now(),
                    isActive = true
                )
                val id = cycleDao.insert(defaultCycle)
                return defaultCycle.copy(id = id)
            }
            return null
        }

        private fun build(context: Context): AppDatabase {
            lateinit var database: AppDatabase
            val callback = object : Callback() {
                override fun onCreate(db: SupportSQLiteDatabase) {
                    super.onCreate(db)
                    // Seed the starter library of common lifts and machines.
                    CoroutineScope(SupervisorJob() + Dispatchers.IO).launch {
                        val isProduction = com.fractanomics.crosstraining.BuildConfig.APP_ENV == "production"
                        SeedData.populate(
                            database.exerciseDao(),
                            database.routineDao(),
                            database.cycleDao(),
                            database.cycleGoalDao(),
                            isProduction = isProduction
                        )
                        provisionDefaultCycleIfNeeded(database)
                    }
                }

                override fun onOpen(db: SupportSQLiteDatabase) {
                    super.onOpen(db)
                    CoroutineScope(SupervisorJob() + Dispatchers.IO).launch {
                        provisionDefaultCycleIfNeeded(database)
                    }
                }
            }

            database = Room.databaseBuilder(
                context.applicationContext,
                AppDatabase::class.java,
                "crosstraining.db"
            )
            .addMigrations(MIGRATION_1_2, MIGRATION_2_3, MIGRATION_3_4, MIGRATION_4_5)
            .fallbackToDestructiveMigrationOnDowngrade()
            .addCallback(callback)
            .build()

            return database
        }
    }
}
