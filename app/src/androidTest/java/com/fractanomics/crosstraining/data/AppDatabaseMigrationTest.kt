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
}
