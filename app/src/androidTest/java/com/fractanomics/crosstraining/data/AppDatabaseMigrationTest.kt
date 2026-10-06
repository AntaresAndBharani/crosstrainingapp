package com.fractanomics.crosstraining.data

import android.content.ContentValues
import android.database.sqlite.SQLiteDatabase
import androidx.room.testing.MigrationTestHelper
import androidx.sqlite.db.framework.FrameworkSQLiteOpenHelperFactory
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.io.IOException

/**
 * Instrumented test verifying Scenario 1 of Issue #503:
 * "[Subtask #501.1] Room Database Schema, Backup & Snapshot Lifecycle"
 *
 * Gherkin Acceptance Scenario:
 * Scenario: Room Migration 5 to 6 Verification
 *   Given an existing Room database at version 5
 *   When MIGRATION_5_6 executes during database upgrade to version 6
 *   Then the weight_entries table is created with natural primary key date (INTEGER)
 *   And columns weightKg (REAL), notes (TEXT NOT NULL DEFAULT ''), updatedAtMillis (INTEGER), and deletedAtMillis (INTEGER NULL) exist
 *   And MigrationTestHelper validates the schema against 6.json with zero validation errors
 */
@RunWith(AndroidJUnit4::class)
class AppDatabaseMigrationTest {

    private val TEST_DB = "migration-test"

    @get:Rule
    val helper: MigrationTestHelper = MigrationTestHelper(
        InstrumentationRegistry.getInstrumentation(),
        AppDatabase::class.java,
        emptyList(),
        FrameworkSQLiteOpenHelperFactory()
    )

    @Test
    @Throws(IOException::class)
    fun migrate5To6_createsWeightEntriesTableAndValidatesSchema() {
        // Given an existing Room database at version 5
        val dbV5 = helper.createDatabase(TEST_DB, 5).apply {
            // Insert dummy cycle to verify pre-existing data is unaffected
            val cycleValues = ContentValues().apply {
                put("name", "Strength Cycle")
                put("startDate", 19000)
                put("goal", "1RM Bench")
                put("isActive", 1)
            }
            insert("cycles", SQLiteDatabase.CONFLICT_REPLACE, cycleValues)
            close()
        }

        // When MIGRATION_5_6 executes during database upgrade to version 6
        val dbV6 = helper.runMigrationsAndValidate(
            TEST_DB,
            6,
            true,
            AppDatabase.MIGRATION_5_6
        )

        // Then verify pre-existing data persists
        val cycleCursor = dbV6.query("SELECT name FROM cycles")
        assertTrue("Cycle data should survive migration", cycleCursor.moveToFirst())
        assertEquals("Strength Cycle", cycleCursor.getString(0))
        cycleCursor.close()

        // And insert and query a weight_entries record to verify columns & constraints
        val weightValues = ContentValues().apply {
            put("date", 19500)
            put("weightKg", 78.4)
            put("notes", "Morning weigh-in")
            put("updatedAtMillis", 1725700000000L)
            putNull("deletedAtMillis")
        }
        dbV6.insert("weight_entries", SQLiteDatabase.CONFLICT_REPLACE, weightValues)

        val weightCursor = dbV6.query("SELECT date, weightKg, notes, updatedAtMillis, deletedAtMillis FROM weight_entries WHERE date = 19500")
        assertTrue("Weight entry should be found", weightCursor.moveToFirst())
        assertEquals(19500L, weightCursor.getLong(0))
        assertEquals(78.4, weightCursor.getDouble(1), 0.001)
        assertEquals("Morning weigh-in", weightCursor.getString(2))
        assertEquals(1725700000000L, weightCursor.getLong(3))
        assertTrue("deletedAtMillis should be null", weightCursor.isNull(4))
        weightCursor.close()
    }

    @Test
    @Throws(IOException::class)
    fun migrate6To7_addsSectionAndExerciseIdsCsvAndValidatesSchema() {
        // Given an existing Room database at version 6 with routine_blocks and session_blocks
        val dbV6 = helper.createDatabase(TEST_DB, 6).apply {
            // Seed routine
            execSQL("INSERT INTO routines (id, name, mainExerciseId, description, defaultFormat) VALUES (1, 'Murph', NULL, 'Hero WOD', 'For Time')")
            // Seed routine_block
            execSQL("INSERT INTO routine_blocks (id, routineId, position, name, kind, format, setsCount, targetRepsScheme, exerciseIdsCsv, notes) VALUES (10, 1, 0, 'Run', 'MONOSTRUCTURAL', 'Standard', 1, '1 Mile', '', '')")
            // Seed cycle & session
            execSQL("INSERT INTO cycles (id, name, startDate, endDate, goal, isActive) VALUES (1, 'Strength Cycle', 19000, NULL, 'Base', 1)")
            execSQL("INSERT INTO sessions (id, cycleId, date, title, notes) VALUES (100, 1, 19500, 'Murph Day', '')")
            // Seed session_block
            execSQL("INSERT INTO session_blocks (id, sessionId, position, name, kind, format, scheme, mainExerciseId, routineId, description, resultText, resultValue, notes) VALUES (1000, 100, 0, 'Run', 'MONOSTRUCTURAL', 'Standard', '', NULL, NULL, '', '8:30', 510.0, '')")
            close()
        }

        // When MIGRATION_6_7 executes during upgrade to version 7
        val dbV7 = helper.runMigrationsAndValidate(
            TEST_DB,
            7,
            true,
            AppDatabase.MIGRATION_6_7
        )

        // Then verify pre-existing data persists and defaults are applied
        val routineBlockCursor = dbV7.query("SELECT id, name, section FROM routine_blocks WHERE id = 10")
        assertTrue("RoutineBlock data should survive migration", routineBlockCursor.moveToFirst())
        assertEquals(10L, routineBlockCursor.getLong(0))
        assertEquals("Run", routineBlockCursor.getString(1))
        assertEquals("", routineBlockCursor.getString(2))
        routineBlockCursor.close()

        val sessionBlockCursor = dbV7.query("SELECT id, name, section, exerciseIdsCsv FROM session_blocks WHERE id = 1000")
        assertTrue("SessionBlock data should survive migration", sessionBlockCursor.moveToFirst())
        assertEquals(1000L, sessionBlockCursor.getLong(0))
        assertEquals("Run", sessionBlockCursor.getString(1))
        assertEquals("", sessionBlockCursor.getString(2))
        assertEquals("", sessionBlockCursor.getString(3))
        sessionBlockCursor.close()

        // And insert records with non-empty section and exerciseIdsCsv
        dbV7.execSQL("UPDATE routine_blocks SET section = 'Warmup' WHERE id = 10")
        dbV7.execSQL("UPDATE session_blocks SET section = 'Metcon', exerciseIdsCsv = '1,2,3' WHERE id = 1000")

        val updatedRoutineBlock = dbV7.query("SELECT section FROM routine_blocks WHERE id = 10")
        assertTrue(updatedRoutineBlock.moveToFirst())
        assertEquals("Warmup", updatedRoutineBlock.getString(0))
        updatedRoutineBlock.close()

        val updatedSessionBlock = dbV7.query("SELECT section, exerciseIdsCsv FROM session_blocks WHERE id = 1000")
        assertTrue(updatedSessionBlock.moveToFirst())
        assertEquals("Metcon", updatedSessionBlock.getString(0))
        assertEquals("1,2,3", updatedSessionBlock.getString(1))
        updatedSessionBlock.close()
    }

    @Test
    @Throws(IOException::class)
    fun migrate7To8_addsSubBlockColumnAndValidatesSchema() {
        // Given an existing Room database at version 7 with routine_blocks and session_blocks
        val dbV7 = helper.createDatabase(TEST_DB, 7).apply {
            // Seed routine
            execSQL("INSERT INTO routines (id, name, mainExerciseId, description, defaultFormat) VALUES (1, 'Monday Functional', NULL, 'Complex & Trisets', 'E3MOM')")
            // Seed routine_block
            execSQL("INSERT INTO routine_blocks (id, routineId, position, name, kind, format, setsCount, targetRepsScheme, exerciseIdsCsv, notes, section) VALUES (10, 1, 0, 'Clean + Hang Clean + Front Squat + Push to OverHead', 'COMPLEX', 'E3MOM', 7, '7x1', '1', '', 'Strengh & Power block')")
            // Seed cycle & session
            execSQL("INSERT INTO cycles (id, name, startDate, endDate, goal, isActive) VALUES (1, 'Strength Cycle', 19000, NULL, 'Base', 1)")
            execSQL("INSERT INTO sessions (id, cycleId, date, title, notes) VALUES (100, 1, 19500, 'Monday Workout', '')")
            // Seed session_block
            execSQL("INSERT INTO session_blocks (id, sessionId, position, name, kind, format, scheme, mainExerciseId, routineId, description, resultText, resultValue, notes, section, exerciseIdsCsv) VALUES (1000, 100, 0, 'Front Squats', 'STRENGTH', 'E3MOM', '4x4', NULL, NULL, '', '', NULL, '', 'Strengh & Power block', '')")
            close()
        }

        // When MIGRATION_7_8 executes during upgrade to version 8
        val dbV8 = helper.runMigrationsAndValidate(
            TEST_DB,
            8,
            true,
            AppDatabase.MIGRATION_7_8
        )

        // Then verify pre-existing data survives migration and subBlock defaults to empty string
        val routineBlockCursor = dbV8.query("SELECT id, name, section, subBlock FROM routine_blocks WHERE id = 10")
        assertTrue("RoutineBlock data should survive migration", routineBlockCursor.moveToFirst())
        assertEquals(10L, routineBlockCursor.getLong(0))
        assertEquals("Clean + Hang Clean + Front Squat + Push to OverHead", routineBlockCursor.getString(1))
        assertEquals("Strengh & Power block", routineBlockCursor.getString(2))
        assertEquals("", routineBlockCursor.getString(3))
        routineBlockCursor.close()

        val sessionBlockCursor = dbV8.query("SELECT id, name, section, subBlock FROM session_blocks WHERE id = 1000")
        assertTrue("SessionBlock data should survive migration", sessionBlockCursor.moveToFirst())
        assertEquals(1000L, sessionBlockCursor.getLong(0))
        assertEquals("Front Squats", sessionBlockCursor.getString(1))
        assertEquals("Strengh & Power block", sessionBlockCursor.getString(2))
        assertEquals("", sessionBlockCursor.getString(3))
        sessionBlockCursor.close()

        // And verify updating subBlock persists correctly
        dbV8.execSQL("UPDATE routine_blocks SET subBlock = 'E3MOM Complex' WHERE id = 10")
        dbV8.execSQL("UPDATE session_blocks SET subBlock = 'E3MOM Front Squats' WHERE id = 1000")

        val updatedRoutineBlock = dbV8.query("SELECT subBlock FROM routine_blocks WHERE id = 10")
        assertTrue(updatedRoutineBlock.moveToFirst())
        assertEquals("E3MOM Complex", updatedRoutineBlock.getString(0))
        updatedRoutineBlock.close()

        val updatedSessionBlock = dbV8.query("SELECT subBlock FROM session_blocks WHERE id = 1000")
        assertTrue(updatedSessionBlock.moveToFirst())
        assertEquals("E3MOM Front Squats", updatedSessionBlock.getString(0))
        updatedSessionBlock.close()
    }

    @Test
    @Throws(IOException::class)
    fun migrate8To9_addsCycleFieldsSessionBlockCompletedDailyLogsAndValidatesSchema() {
        // Given an existing Room database at version 8 with existing cycles, sessions, and session blocks
        val dbV8 = helper.createDatabase(TEST_DB, 8).apply {
            // Seed cycle
            execSQL("INSERT INTO cycles (id, name, startDate, endDate, goal, isActive) VALUES (1, 'Strength Cycle', 19000, NULL, 'Base', 1)")
            // Seed session
            execSQL("INSERT INTO sessions (id, cycleId, date, title, notes) VALUES (100, 1, 19500, 'Monday Workout', '')")
            // Seed session_block
            execSQL("INSERT INTO session_blocks (id, sessionId, position, name, kind, format, scheme, mainExerciseId, routineId, description, resultText, resultValue, notes, section, exerciseIdsCsv, subBlock) VALUES (1000, 100, 0, 'Front Squats', 'STRENGTH', 'E3MOM', '4x4', NULL, NULL, '', '', NULL, '', 'Strengh & Power block', '', 'E3MOM Front Squats')")
            close()
        }

        // When MIGRATION_8_9 executes during upgrade to version 9
        val dbV9 = helper.runMigrationsAndValidate(
            TEST_DB,
            9,
            true,
            AppDatabase.MIGRATION_8_9
        )

        // Then verify pre-existing data persists and defaults are applied
        val cycleCursor = dbV9.query("SELECT id, name, type, fastDaysOfWeek, restDaysOfWeek FROM cycles WHERE id = 1")
        assertTrue("Cycle data should survive migration", cycleCursor.moveToFirst())
        assertEquals(1L, cycleCursor.getLong(0))
        assertEquals("Strength Cycle", cycleCursor.getString(1))
        assertEquals("STRENGTH_WEIGHTLIFTING", cycleCursor.getString(2))
        assertEquals(0L, cycleCursor.getLong(3))
        assertEquals(0L, cycleCursor.getLong(4))
        cycleCursor.close()

        val sessionBlockCursor = dbV9.query("SELECT id, name, isCompleted FROM session_blocks WHERE id = 1000")
        assertTrue("SessionBlock data should survive migration", sessionBlockCursor.moveToFirst())
        assertEquals(1000L, sessionBlockCursor.getLong(0))
        assertEquals("Front Squats", sessionBlockCursor.getString(1))
        assertEquals(1L, sessionBlockCursor.getLong(2))
        sessionBlockCursor.close()

        // And verify daily_logs table exists and allows insertion
        val dailyLogValues = ContentValues().apply {
            put("date", 19500)
            put("fastCompleted", 1)
            put("isRestDay", 0)
            put("caloriesKcal", 2200)
            put("proteinGrams", 180)
            put("carbsGrams", 200)
            put("fatGrams", 60)
            put("notes", "Clean eating")
            put("updatedAtMillis", 1728000000000L)
            putNull("deletedAtMillis")
        }
        dbV9.insert("daily_logs", SQLiteDatabase.CONFLICT_REPLACE, dailyLogValues)

        val logCursor = dbV9.query("SELECT date, fastCompleted, isRestDay, caloriesKcal, proteinGrams, carbsGrams, fatGrams, notes, updatedAtMillis, deletedAtMillis FROM daily_logs WHERE date = 19500")
        assertTrue("Daily log entry should be found", logCursor.moveToFirst())
        assertEquals(19500L, logCursor.getLong(0))
        assertEquals(1L, logCursor.getLong(1))
        assertEquals(0L, logCursor.getLong(2))
        assertEquals(2200L, logCursor.getLong(3))
        assertEquals(180L, logCursor.getLong(4))
        assertEquals(200L, logCursor.getLong(5))
        assertEquals(60L, logCursor.getLong(6))
        assertEquals("Clean eating", logCursor.getString(7))
        assertEquals(1728000000000L, logCursor.getLong(8))
        assertTrue("deletedAtMillis should be null", logCursor.isNull(9))
        logCursor.close()
    }

    @Test
    @Throws(IOException::class)
    fun migrate9To10_addsCycleWeightFieldsAndValidatesSchema() {
        // Given an existing Room database at version 9 with existing cycles
        val dbV9 = helper.createDatabase(TEST_DB, 9).apply {
            val cycleValues = ContentValues().apply {
                put("id", 1L)
                put("name", "Strength Cycle")
                put("startDate", 19000L)
                putNull("endDate")
                put("goal", "Base")
                put("isActive", 1)
                put("type", "STRENGTH_WEIGHTLIFTING")
                put("fastDaysOfWeek", 0)
                put("restDaysOfWeek", 0)
            }
            insert("cycles", SQLiteDatabase.CONFLICT_REPLACE, cycleValues)
            close()
        }

        // When MIGRATION_9_10 executes during upgrade to version 10
        val dbV10 = helper.runMigrationsAndValidate(
            TEST_DB,
            10,
            true,
            AppDatabase.MIGRATION_9_10
        )

        // Then verify pre-existing cycle survives migration and new fields have correct defaults
        val cursor = dbV10.query("SELECT id, name, startingWeightKg, targetWeightKg, isBaselineAutoDerived FROM cycles WHERE id = 1")
        assertTrue("Cycle data should survive migration", cursor.moveToFirst())
        assertEquals(1L, cursor.getLong(0))
        assertEquals("Strength Cycle", cursor.getString(1))
        assertTrue("startingWeightKg should default to null", cursor.isNull(2))
        assertTrue("targetWeightKg should default to null", cursor.isNull(3))
        assertEquals(0L, cursor.getLong(4)) // isBaselineAutoDerived == false (0)
        cursor.close()

        // And verify updating and inserting records with new weight fields
        val newCycleValues = ContentValues().apply {
            put("id", 2L)
            put("name", "Fat Loss Cycle")
            put("startDate", 19500L)
            putNull("endDate")
            put("goal", "Cut")
            put("isActive", 1)
            put("type", "FAT_LOSS_BODYBUILDING")
            put("fastDaysOfWeek", 5)
            put("restDaysOfWeek", 2)
            put("startingWeightKg", 85.5)
            put("targetWeightKg", 78.0)
            put("isBaselineAutoDerived", 1)
        }
        dbV10.insert("cycles", SQLiteDatabase.CONFLICT_REPLACE, newCycleValues)

        val newCursor = dbV10.query("SELECT id, name, startingWeightKg, targetWeightKg, isBaselineAutoDerived FROM cycles WHERE id = 2")
        assertTrue("New cycle data should be found", newCursor.moveToFirst())
        assertEquals(2L, newCursor.getLong(0))
        assertEquals("Fat Loss Cycle", newCursor.getString(1))
        assertEquals(85.5, newCursor.getDouble(2), 0.001)
        assertEquals(78.0, newCursor.getDouble(3), 0.001)
        assertEquals(1L, newCursor.getLong(4))
        newCursor.close()
    }
}
