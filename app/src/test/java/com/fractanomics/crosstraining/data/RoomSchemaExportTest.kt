package com.fractanomics.crosstraining.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * Unit tests verifying Issue #502:
 * "[Subtask #501.0] Schema Export Infrastructure & v5 Baseline Generation"
 *
 * Gherkin Acceptance Scenarios:
 * Scenario: Room Schema Export Configuration
 *   Given the Android build configuration in app/build.gradle.kts
 *   When the KSP schemaLocation argument is configured pointing to $projectDir/schemas
 *   And exportSchema = true is enabled in AppDatabase.kt at database version 5
 *   Then building the project generates the schema artifact at app/schemas/com.fractanomics.crosstraining.data.AppDatabase/5.json
 */
class RoomSchemaExportTest {

    @Test
    fun appDatabaseSource_hasExportSchemaEnabledAtVersion5() {
        val candidatePaths = listOf(
            File("src/main/java/com/fractanomics/crosstraining/data/AppDatabase.kt"),
            File("app/src/main/java/com/fractanomics/crosstraining/data/AppDatabase.kt"),
            File("../app/src/main/java/com/fractanomics/crosstraining/data/AppDatabase.kt")
        )
        val sourceFile = candidatePaths.firstOrNull { it.exists() }
        assertNotNull("AppDatabase.kt source file must be found", sourceFile)

        val content = sourceFile!!.readText()
        assertTrue("AppDatabase must have version = 5", Regex("""version\s*=\s*5""").containsMatchIn(content))
        assertTrue("AppDatabase must have exportSchema = true", Regex("""exportSchema\s*=\s*true""").containsMatchIn(content))
    }

    @Test
    fun gradleConfiguration_hasKspSchemaLocationAndDependencies() {
        val candidatePaths = listOf(
            File("build.gradle.kts"),
            File("app/build.gradle.kts"),
            File("../app/build.gradle.kts")
        )
        val gradleFile = candidatePaths.firstOrNull { it.exists() }
        assertNotNull("app/build.gradle.kts must be found", gradleFile)

        val content = gradleFile!!.readText()
        assertTrue(
            "app/build.gradle.kts must configure room.schemaLocation pointing to schemas directory",
            content.contains("room.schemaLocation") && content.contains("\$projectDir/schemas")
        )
        assertTrue(
            "app/build.gradle.kts must declare room.testing in androidTest",
            content.contains("androidTestImplementation(libs.androidx.room.testing)")
        )
        assertTrue(
            "app/build.gradle.kts must declare test.runner in androidTest",
            content.contains("androidTestImplementation(libs.androidx.test.runner)")
        )
    }

    @Test
    fun schemaArtifact_existsAndMatchesVersion5Contract() {
        val candidatePaths = listOf(
            File("schemas/com.fractanomics.crosstraining.data.AppDatabase/5.json"),
            File("app/schemas/com.fractanomics.crosstraining.data.AppDatabase/5.json"),
            File("../app/schemas/com.fractanomics.crosstraining.data.AppDatabase/5.json")
        )
        val schemaFile = candidatePaths.firstOrNull { it.exists() }
        assertNotNull("Baseline schema artifact 5.json must exist in schemas directory", schemaFile)

        val content = schemaFile!!.readText()
        assertTrue("Schema must have formatVersion 1", content.contains("\"formatVersion\": 1"))
        assertTrue("Schema must specify database version 5", content.contains("\"version\": 5"))
        assertTrue("Schema must contain identityHash", content.contains("\"identityHash\":"))

        // Check required v5 tables
        val expectedTables = listOf(
            "\"tableName\": \"cycles\"",
            "\"tableName\": \"exercises\"",
            "\"tableName\": \"routines\"",
            "\"tableName\": \"routine_blocks\"",
            "\"tableName\": \"sessions\"",
            "\"tableName\": \"session_blocks\"",
            "\"tableName\": \"block_sets\"",
            "\"tableName\": \"rep_maxes\"",
            "\"tableName\": \"cycle_goals\""
        )
        for (table in expectedTables) {
            assertTrue("Schema 5.json must contain table definition: $table", content.contains(table))
        }

        // Must not contain weight_entries in version 5 baseline
        assertFalse("Schema 5.json must not contain weight_entries before v6 migration", content.contains("weight_entries"))
    }
}
