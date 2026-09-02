package com.fractanomics.crosstraining.data.ai

import com.fractanomics.crosstraining.data.SeedData
import com.fractanomics.crosstraining.data.dao.ExerciseDao
import com.fractanomics.crosstraining.data.model.Exercise
import com.fractanomics.crosstraining.data.model.ExerciseCategory
import com.fractanomics.crosstraining.data.model.MetricType
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import kotlin.system.measureNanoTime

/**
 * Unit and integration tests for [ExerciseEntityGrounder].
 *
 * Acceptance Criteria for Issue #469:
 * - [x] ExerciseEntityGrounder class with fuzzy-match algorithm (Levenshtein distance <= 2)
 * - [x] Method resolveExercise(text: String, dbDao: ExerciseDao): List<Exercise> returns 0-3 candidate matches
 * - [x] Disambiguation logic: if 2+ matches, return ranked list; if 1 match, auto-select; if 0, return empty
 * - [x] Unit tests with 20+ real-world exercise name variations (e.g. "cleans" -> Power Clean + Clean, etc.)
 * - [x] Integration test against sample database (ExerciseDao + SeedData)
 * - [x] Performance: resolve within 50ms for typical queries
 * - [x] Return ranking confidence scores (0.0 - 1.0) for each candidate
 */
class ExerciseEntityGrounderTest {

    private lateinit var grounder: ExerciseEntityGrounder
    private lateinit var fakeDao: FakeExerciseDao
    private lateinit var catalog: List<Exercise>

    @Before
    fun setUp() {
        grounder = ExerciseEntityGrounder.DEFAULT
        catalog = SeedData.defaults.mapIndexed { idx, ex -> ex.copy(id = (idx + 1).toLong()) }
        fakeDao = FakeExerciseDao(catalog)
    }

    @Test
    fun `exact match resolves with 1_0 confidence and auto-selects single match`() = runTest {
        val matches = grounder.resolveExerciseWithConfidence("Back Squat", fakeDao)
        assertEquals(1, matches.size)
        assertEquals("Back Squat", matches.first().exercise.name)
        assertEquals(1.0, matches.first().confidence, 0.001)

        val resolved = grounder.resolveExercise("Back Squat", fakeDao)
        assertEquals(1, resolved.size)
        assertEquals("Back Squat", resolved.first().name)
    }

    @Test
    fun `disambiguation logic - returns ranked candidates up to 3 for ambiguous query cleans`() = runTest {
        val matches = grounder.resolveExerciseWithConfidence("cleans", fakeDao)
        assertTrue("Expected 2 or 3 candidates for 'cleans', found ${matches.size}", matches.size in 2..3)
        // Highest confidence should be Clean
        assertEquals("Clean", matches.first().exercise.name)
        assertTrue("Top candidate confidence must be >= 0.90", matches.first().confidence >= 0.90)

        // All candidates must be ranked descending
        assertTrue(
            "Candidates must be ranked descending by confidence",
            matches.zipWithNext().all { (a, b) -> a.confidence >= b.confidence }
        )

        val names = matches.map { it.exercise.name }
        assertTrue(names.contains("Clean"))
        assertTrue(names.contains("Power Clean"))
    }

    @Test
    fun `disambiguation logic - returns ranked candidates for squat root`() = runTest {
        val matches = grounder.resolveExerciseWithConfidence("squats", fakeDao)
        assertTrue(matches.size in 2..3)
        val names = matches.map { it.exercise.name }
        assertTrue(names.contains("Back Squat"))
        assertTrue(names.contains("Front Squat"))
    }

    @Test
    fun `disambiguation logic - returns empty list when no matches meet threshold`() = runTest {
        val matches = grounder.resolveExercise("xylophone gymnastics 99", fakeDao)
        assertTrue(matches.isEmpty())

        val matchesConfidence = grounder.resolveExerciseWithConfidence("unknown movement abc", fakeDao)
        assertTrue(matchesConfidence.isEmpty())

        val best = grounder.resolveBestMatch("completely unrelated text", fakeDao)
        assertNull(best)
    }

    @Test
    fun `resolves 20+ real-world exercise variations accurately`() = runTest {
        val variations = listOf(
            "back squats" to "Back Squat",
            "front squats" to "Front Squat",
            "overhead squat" to "Overhead Squat",
            "power snatches" to "Power Snatch",
            "power clean" to "Power Clean",
            "clean & jerk" to "Clean & Jerk",
            "clean and jerk" to "Clean & Jerk",
            "c and j" to "Clean & Jerk",
            "c & j" to "Clean & Jerk",
            "ohs" to "Overhead Squat",
            "t2b" to "Toes to Bar",
            "toast to bar" to "Toes to Bar",
            "toes to bar" to "Toes to Bar",
            "pullups" to "Pull-ups",
            "pull ups" to "Pull-ups",
            "pushups" to "Push-ups",
            "push ups" to "Push-ups",
            "deadlifts" to "Deadlift",
            "dead lift" to "Deadlift",
            "strict press" to "Strict Press",
            "push press" to "Push Press",
            "bench press" to "Bench Press",
            "thrusters" to "Thruster",
            "air squats" to "Air Squat",
            "box jump" to "Box Jumps",
            "box jumps" to "Box Jumps",
            "rowing" to "Rower",
            "bike" to "Air Bike",
            "air bike" to "Air Bike",
            "ski" to "SkiErg",
            "skierg" to "SkiErg",
            "running" to "Run",
            "snatch" to "Snatch",
            "clean" to "Clean",
            "jerk" to "Jerk"
        )

        assertTrue("Must test at least 20 variations", variations.size >= 20)

        for ((spoken, expectedName) in variations) {
            val candidates = grounder.resolveExercise(spoken, fakeDao)
            assertTrue(
                "Query '$spoken' should return at least 1 candidate, got none",
                candidates.isNotEmpty()
            )
            val topCandidate = candidates.first()
            assertEquals(
                "Query '$spoken' should resolve to '$expectedName'",
                expectedName,
                topCandidate.name
            )
        }
    }

    @Test
    fun `fuzzy matching handles typos with Levenshtein distance up to 2`() = runTest {
        val typoQueries = listOf(
            "snathc" to "Snatch",       // distance 2
            "snatsh" to "Snatch",       // distance 1
            "thurster" to "Thruster",   // distance 2
            "puch press" to "Push Press", // distance 1
            "brench press" to "Bench Press", // distance 1
            "deallift" to "Deadlift",   // distance 1
            "rowwer" to "Rower",        // distance 1
            "pull-upz" to "Pull-ups"    // distance 1
        )

        for ((typo, expected) in typoQueries) {
            val matches = grounder.resolveExerciseWithConfidence(typo, fakeDao)
            assertTrue("Typo '$typo' should produce matches", matches.isNotEmpty())
            assertEquals("Typo '$typo' should match '$expected'", expected, matches.first().exercise.name)
            assertTrue("Fuzzy match score must be >= 0.70", matches.first().confidence >= 0.70)
        }
    }

    @Test
    fun `integration test against SeedData sample database`() = runTest {
        // Build an in-memory repository-like database state from SeedData
        val exercises = SeedData.defaults.mapIndexed { index, exercise ->
            exercise.copy(id = (index + 100).toLong())
        }
        val seedDao = FakeExerciseDao(exercises)

        val thrusterMatch = grounder.resolveBestMatch("15 thrusters", seedDao)
        assertNotNull(thrusterMatch)
        assertEquals("Thruster", thrusterMatch?.name)

        val cleanMatch = grounder.resolveExercise("Cleans", seedDao)
        assertTrue(cleanMatch.isNotEmpty())
        assertEquals("Clean", cleanMatch.first().name)

        val cjMatches = grounder.resolveExerciseWithConfidence("C&J", seedDao)
        assertEquals("Clean & Jerk", cjMatches.first().exercise.name)
        assertTrue(cjMatches.first().confidence >= 0.95)
    }

    @Test
    fun `performance test - resolves typical queries within 50ms`() = runTest {
        // Warmup
        grounder.resolveExercise("Back Squat", fakeDao)

        val queries = listOf(
            "1 Deadlift",
            "5 Back Squats at 120kg",
            "cleans",
            "C&J at 80kg",
            "o h s",
            "toast to bar",
            "15 thrusters",
            "snathc",
            "thurster",
            "20 min air bike"
        )

        for (query in queries) {
            val elapsedNanos = measureNanoTime {
                val results = grounder.resolveExercise(query, fakeDao)
                assertTrue("Query '$query' should return results", results.isNotEmpty())
            }
            val elapsedMs = elapsedNanos / 1_000_000.0
            assertTrue(
                "Query '$query' took ${elapsedMs}ms, which must be < 50ms",
                elapsedMs < 50.0
            )
        }
    }

    @Test
    fun `handles blank and empty queries safely`() = runTest {
        assertTrue(grounder.resolveExercise("", fakeDao).isEmpty())
        assertTrue(grounder.resolveExercise("   ", fakeDao).isEmpty())
        assertTrue(grounder.resolveExerciseWithConfidence("", fakeDao).isEmpty())
        assertNull(grounder.resolveBestMatch("", fakeDao))
    }

    @Test
    fun `confidence scores strictly between 0_0 and 1_0`() = runTest {
        val testQueries = listOf("snatch", "cleans", "press", "snathc", "unknown 123", "c and j", "run")
        for (q in testQueries) {
            val matches = grounder.resolveExerciseWithConfidence(q, fakeDao)
            for (m in matches) {
                assertTrue("Confidence ${m.confidence} must be >= 0.0", m.confidence >= 0.0)
                assertTrue("Confidence ${m.confidence} must be <= 1.0", m.confidence <= 1.0)
            }
        }
    }

    /**
     * In-memory mock/fake DAO for pure JVM testing.
     */
    private class FakeExerciseDao(
        private val storage: List<Exercise>
    ) : ExerciseDao {
        override suspend fun insert(exercise: Exercise): Long = 1L
        override suspend fun insertAll(exercises: List<Exercise>) {}
        override suspend fun update(exercise: Exercise) {}
        override suspend fun delete(exercise: Exercise) {}
        override fun observeAll(): Flow<List<Exercise>> = flowOf(storage)
        override suspend fun byId(id: Long): Exercise? = storage.find { it.id == id }
        override suspend fun byName(name: String): Exercise? = storage.find { it.name.equals(name, ignoreCase = true) }
        override suspend fun count(): Int = storage.size
        override suspend fun insertAllReplace(exercises: List<Exercise>) {}
        override suspend fun getAllOnce(): List<Exercise> = storage
        override suspend fun deleteAll() {}
    }
}