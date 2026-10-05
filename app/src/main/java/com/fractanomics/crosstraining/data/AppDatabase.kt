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
import com.fractanomics.crosstraining.data.dao.DailyLogDao
import com.fractanomics.crosstraining.data.dao.ExerciseDao
import com.fractanomics.crosstraining.data.dao.RepMaxDao
import com.fractanomics.crosstraining.data.dao.RoutineDao
import com.fractanomics.crosstraining.data.dao.SessionDao
import com.fractanomics.crosstraining.data.dao.WeightDao
import com.fractanomics.crosstraining.data.model.BlockSet
import com.fractanomics.crosstraining.data.model.Cycle
import com.fractanomics.crosstraining.data.model.CycleGoal
import com.fractanomics.crosstraining.data.model.DailyLog
import com.fractanomics.crosstraining.data.model.Exercise
import com.fractanomics.crosstraining.data.model.RepMax
import com.fractanomics.crosstraining.data.model.Routine
import com.fractanomics.crosstraining.data.model.RoutineBlock
import com.fractanomics.crosstraining.data.model.Session
import com.fractanomics.crosstraining.data.model.SessionBlock
import com.fractanomics.crosstraining.data.model.WeightEntry
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
        CycleGoal::class,
        WeightEntry::class,
        DailyLog::class
    ],
    version = 9,
    exportSchema = true
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
    abstract fun weightDao(): WeightDao
    abstract fun dailyLogDao(): DailyLogDao

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

        val MIGRATION_5_6 = object : Migration(5, 6) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("""
                    CREATE TABLE IF NOT EXISTS `weight_entries` (
                        `date` INTEGER NOT NULL,
                        `weightKg` REAL NOT NULL,
                        `notes` TEXT NOT NULL DEFAULT '',
                        `updatedAtMillis` INTEGER NOT NULL,
                        `deletedAtMillis` INTEGER,
                        PRIMARY KEY(`date`)
                    )
                """.trimIndent())
            }
        }

        val MIGRATION_6_7 = object : Migration(6, 7) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE `routine_blocks` ADD COLUMN `section` TEXT NOT NULL DEFAULT ''")
                db.execSQL("ALTER TABLE `session_blocks` ADD COLUMN `section` TEXT NOT NULL DEFAULT ''")
                db.execSQL("ALTER TABLE `session_blocks` ADD COLUMN `exerciseIdsCsv` TEXT NOT NULL DEFAULT ''")
            }
        }

        val MIGRATION_7_8 = object : Migration(7, 8) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE `session_blocks` ADD COLUMN `subBlock` TEXT NOT NULL DEFAULT ''")
                db.execSQL("ALTER TABLE `routine_blocks` ADD COLUMN `subBlock` TEXT NOT NULL DEFAULT ''")
            }
        }

        val MIGRATION_8_9 = object : Migration(8, 9) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE `cycles` ADD COLUMN `type` TEXT NOT NULL DEFAULT 'STRENGTH_WEIGHTLIFTING'")
                db.execSQL("ALTER TABLE `cycles` ADD COLUMN `fastDaysOfWeek` INTEGER NOT NULL DEFAULT 0")
                db.execSQL("ALTER TABLE `cycles` ADD COLUMN `restDaysOfWeek` INTEGER NOT NULL DEFAULT 0")
                db.execSQL("ALTER TABLE `session_blocks` ADD COLUMN `isCompleted` INTEGER NOT NULL DEFAULT 1")
                db.execSQL("""
                    CREATE TABLE IF NOT EXISTS `daily_logs` (
                        `date` INTEGER NOT NULL PRIMARY KEY,
                        `fastCompleted` INTEGER,
                        `isRestDay` INTEGER NOT NULL DEFAULT 0,
                        `caloriesKcal` INTEGER,
                        `proteinGrams` INTEGER,
                        `carbsGrams` INTEGER,
                        `fatGrams` INTEGER,
                        `notes` TEXT NOT NULL DEFAULT '',
                        `updatedAtMillis` INTEGER NOT NULL,
                        `deletedAtMillis` INTEGER
                    )
                """.trimIndent())
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
                .addMigrations(MIGRATION_1_2, MIGRATION_2_3, MIGRATION_3_4, MIGRATION_4_5, MIGRATION_5_6, MIGRATION_6_7, MIGRATION_7_8, MIGRATION_8_9)
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
                        Repository(database).reconcileLegacyWorkoutSubBlocks()
                    }
                }

                override fun onOpen(db: SupportSQLiteDatabase) {
                    super.onOpen(db)
                    CoroutineScope(SupervisorJob() + Dispatchers.IO).launch {
                        provisionDefaultCycleIfNeeded(database)
                        Repository(database).reconcileLegacyWorkoutSubBlocks()
                    }
                }
            }

            database = Room.databaseBuilder(
                context.applicationContext,
                AppDatabase::class.java,
                "crosstraining.db"
            )
            .addMigrations(MIGRATION_1_2, MIGRATION_2_3, MIGRATION_3_4, MIGRATION_4_5, MIGRATION_5_6, MIGRATION_6_7, MIGRATION_7_8, MIGRATION_8_9)
            .fallbackToDestructiveMigrationOnDowngrade()
            .addCallback(callback)
            .build()

            return database
        }
    }
}
