package com.fractanomics.crosstraining.data

import androidx.room.testing.MigrationTestHelper
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.assertNotNull
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Instrumented test verifying Scenario 2 of Issue #502:
 * "[Subtask #501.0] Schema Export Infrastructure & v5 Baseline Generation"
 *
 * Gherkin Acceptance Scenario:
 * Scenario: Room Testing Dependencies Availability
 *   Given gradle/libs.versions.toml and app/build.gradle.kts
 *   When androidx-room-testing and androidx-test-runner dependencies are added to the androidTest configuration
 *   Then androidTest compilation succeeds without missing class definition errors for MigrationTestHelper
 */
@RunWith(AndroidJUnit4::class)
class RoomTestingDependencyAvailabilityTest {

    @get:Rule
    val helper: MigrationTestHelper = MigrationTestHelper(
        InstrumentationRegistry.getInstrumentation(),
        AppDatabase::class.java
    )

    @Test
    fun migrationTestHelper_isAvailableWithoutMissingClassDefinition() {
        assertNotNull("MigrationTestHelper must be instantiated successfully", helper)
    }
}
