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
}
