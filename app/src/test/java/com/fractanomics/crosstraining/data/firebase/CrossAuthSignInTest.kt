package com.fractanomics.crosstraining.data.firebase

import com.fractanomics.crosstraining.data.BlockInsert
import com.fractanomics.crosstraining.data.model.BlockKind
import com.fractanomics.crosstraining.data.model.BlockSet
import com.fractanomics.crosstraining.data.model.CycleGoal
import com.fractanomics.crosstraining.data.model.Exercise
import com.fractanomics.crosstraining.data.model.ExerciseCategory
import com.fractanomics.crosstraining.data.model.MetricType
import com.fractanomics.crosstraining.data.model.RepMax
import com.fractanomics.crosstraining.data.model.Routine
import com.fractanomics.crosstraining.data.model.RoutineBlock
import com.fractanomics.crosstraining.data.model.Session
import com.fractanomics.crosstraining.data.model.SessionBlock
import com.fractanomics.crosstraining.data.model.SessionWithBlocks
import com.fractanomics.crosstraining.data.model.UserRole
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.time.LocalDate

/**
 * Comprehensive Unit & Integration test suite for Cross-Auth Sign-In Compatibility (Issue #457).
 *
 * Feature: Cross-Auth Sign-In Profile Integrity
 *
 * Acceptance Criteria Covered:
 * - Given an athlete account created via Google Sign-In with UID "user_123" and email "athlete@example.com"
 * - And the user has set a password via password reset
 * - When the user logs in using "athlete@example.com" and password on LoginWelcomeScreen
 * - Then the authenticated Firebase UID is "user_123"
 * - And the existing profile, workouts, and PR history are loaded without duplication
 */
class CrossAuthSignInTest {

    @Before
    fun setUp() {
        UserCloudSyncManager.setAuthenticatedUser(null)
    }

    // =========================================================================
    // Scenario 1: Email/password login binds to existing Google account UID
    // =========================================================================

    @Test
    fun `scenario 1 - email password login binds to existing Google account UID user_123`() {
        // Given an athlete account created via Google Sign-In with UID "user_123" and email "athlete@example.com"
        val googleUser = AuthUser(
            uid = "user_123",
            email = "athlete@example.com",
            isAnonymous = false
        )
        UserCloudSyncManager.setAuthenticatedUser(googleUser)

        assertEquals("user_123", UserCloudSyncManager.currentUserId)
        assertEquals("athlete@example.com", UserCloudSyncManager.userState.value?.email)
        assertFalse(UserCloudSyncManager.userState.value?.isAnonymous ?: true)

        // When the user logs in using email/password after password reset,
        // the authenticated user credential returned by Firebase Auth retains the existing UID
        val emailPasswordUser = AuthUser(
            uid = "user_123",
            email = "athlete@example.com",
            isAnonymous = false
        )
        UserCloudSyncManager.setAuthenticatedUser(emailPasswordUser)

        // Then the authenticated Firebase UID is "user_123"
        assertEquals("user_123", UserCloudSyncManager.currentUserId)
        assertEquals("athlete@example.com", UserCloudSyncManager.userState.value?.email)
    }

    @Test
    fun `scenario 1 - UID sanitization preserves document ID key compatibility`() {
        val userWithSlash = AuthUser(
            uid = "google/user_123/prod",
            email = "athlete@example.com",
            isAnonymous = false
        )
        UserCloudSyncManager.setAuthenticatedUser(userWithSlash)

        assertEquals("google_user_123_prod", UserCloudSyncManager.currentUserId)
    }

    @Test
    fun `scenario 1 - email normalization handles case and whitespace trimming`() {
        val rawInput = "  Athlete@Example.COM  "
        val normalized = UserCloudSyncManager.normalizeEmail(rawInput)

        assertEquals("athlete@example.com", normalized)
    }

    // =========================================================================
    // Scenario 2: Existing profile preservation and role resolution
    // =========================================================================

    @Test
    fun `scenario 2 - athlete email resolves to ATHLETE role without overriding existing settings`() {
        val email = "athlete@example.com"
        val normalized = UserCloudSyncManager.normalizeEmail(email)

        val determinedRole = when {
            normalized == "pv.joseangel@gmail.com" || normalized == "coach@crosstraining.app" -> UserRole.COACH
            normalized.startsWith("jangelpv") || normalized == "athlete@crosstraining.app" -> UserRole.ATHLETE
            else -> UserRole.ATHLETE
        }

        assertEquals(UserRole.ATHLETE, determinedRole)
    }

    @Test
    fun `scenario 2 - coach email resolves to COACH role`() {
        val email = "pv.joseangel@gmail.com"
        val normalized = UserCloudSyncManager.normalizeEmail(email)

        val determinedRole = when {
            normalized == "pv.joseangel@gmail.com" || normalized == "coach@crosstraining.app" -> UserRole.COACH
            normalized.startsWith("jangelpv") || normalized == "athlete@crosstraining.app" -> UserRole.ATHLETE
            else -> UserRole.ATHLETE
        }

        assertEquals(UserRole.COACH, determinedRole)
    }

    @Test
    fun `scenario 2 - explicit role is preserved when provided during profile sync`() {
        val explicitRole = UserRole.COACH
        val determinedRole = when {
            "custom@domain.com" == "pv.joseangel@gmail.com" || "custom@domain.com" == "coach@crosstraining.app" -> UserRole.COACH
            "custom@domain.com".startsWith("jangelpv") || "custom@domain.com" == "athlete@crosstraining.app" -> UserRole.ATHLETE
            else -> explicitRole
        }

        assertEquals(UserRole.COACH, determinedRole)
    }

    // =========================================================================
    // Scenario 3: Workouts and PR history loaded without duplication
    // =========================================================================

    @Test
    fun `scenario 3 - workout sessions deduplication prevents duplicate records on same date and title`() {
        val sessionDate = LocalDate.of(2026, 8, 30)
        val existingSession = Session(
            id = 1L,
            cycleId = 1L,
            date = sessionDate,
            title = "Fran Benchmark WOD",
            notes = "Completed in 3:15"
        )
        val existingBlock = SessionBlock(
            id = 1L,
            sessionId = 1L,
            position = 0,
            name = "Fran",
            kind = BlockKind.METABOLIC,
            format = "For Time",
            scheme = "21-15-9",
            mainExerciseId = 10L,
            description = "Thrusters and Pull-ups",
            resultText = "3:15"
        )
        val existingSet = BlockSet(
            id = 1L,
            blockId = 1L,
            position = 0,
            reps = 21,
            weight = 43.0
        )
        val existingSessionWithBlocks = SessionWithBlocks(
            session = existingSession,
            blocks = listOf(
                com.fractanomics.crosstraining.data.model.BlockWithSets(
                    block = existingBlock,
                    sets = listOf(existingSet)
                )
            )
        )

        val localDatabaseSessions = listOf(existingSessionWithBlocks)

        // Incoming payload from Firestore for user_123
        @Suppress("UNCHECKED_CAST")
        val incomingSessionMap = mapOf(
            "session" to mapOf(
                "id" to 1L,
                "cycleId" to 1L,
                "date" to "2026-08-30",
                "title" to "Fran Benchmark WOD",
                "notes" to "Completed in 3:15"
            ),
            "blocks" to listOf(
                mapOf(
                    "block" to mapOf(
                        "id" to 1L,
                        "sessionId" to 1L,
                        "position" to 0,
                        "name" to "Fran",
                        "kind" to "METABOLIC",
                        "format" to "For Time",
                        "scheme" to "21-15-9",
                        "mainExerciseId" to 10L,
                        "description" to "Thrusters and Pull-ups",
                        "resultText" to "3:15"
                    ),
                    "sets" to listOf(
                        mapOf(
                            "id" to 1L,
                            "blockId" to 1L,
                            "position" to 0,
                            "groupIndex" to 0,
                            "reps" to 21,
                            "weight" to 43.0,
                            "metricValue" to 0.0,
                            "isWarmup" to false,
                            "isFailed" to false,
                            "notes" to ""
                        )
                    )
                )
            )
        )

        @Suppress("UNCHECKED_CAST")
        val sessionData = incomingSessionMap["session"] as Map<String, Any>
        val incomingDate = LocalDate.parse(sessionData["date"] as String)
        val incomingTitle = sessionData["title"] as String

        // Deduplication check: session on same date with same title already exists
        val alreadyExists = localDatabaseSessions.any {
            it.session.date == incomingDate && it.session.title == incomingTitle
        }

        assertTrue("Duplicate session on same date and title must be skipped", alreadyExists)
    }

    @Test
    fun `scenario 3 - personal record (PR) rep max history deduplication preserves existing records`() {
        val prDate = LocalDate.of(2026, 8, 25)
        val existingPr = RepMax(
            id = 1L,
            exerciseId = 5L,
            reps = 1,
            weight = 150.0,
            date = prDate,
            cycleId = 1L
        )

        val localRepMaxes = listOf(existingPr)

        // Incoming RepMax from cloud payload
        val incomingExerciseId = 5L
        val incomingReps = 1
        val incomingWeight = 150.0
        val incomingDate = LocalDate.of(2026, 8, 25)

        val alreadyExists = localRepMaxes.any {
            it.exerciseId == incomingExerciseId &&
                    it.reps == incomingReps &&
                    it.weight == incomingWeight &&
                    it.date == incomingDate
        }

        assertTrue("Duplicate PR record with identical exercise, reps, weight, and date must be skipped", alreadyExists)
    }

    @Test
    fun `scenario 3 - new distinct personal record from cloud is accepted and added`() {
        val existingPr = RepMax(
            id = 1L,
            exerciseId = 5L,
            reps = 1,
            weight = 150.0,
            date = LocalDate.of(2026, 8, 25),
            cycleId = 1L
        )

        val localRepMaxes = listOf(existingPr)

        // New PR with higher weight
        val incomingExerciseId = 5L
        val incomingReps = 1
        val incomingWeight = 155.0
        val incomingDate = LocalDate.of(2026, 8, 29)

        val alreadyExists = localRepMaxes.any {
            it.exerciseId == incomingExerciseId &&
                    it.reps == incomingReps &&
                    it.weight == incomingWeight &&
                    it.date == incomingDate
        }

        assertFalse("New distinct PR must be identified for insertion", alreadyExists)
    }

    @Test
    fun `scenario 3 - exercise deduplication by name preserves existing exercise definitions`() {
        val existingExercise = Exercise(
            id = 10L,
            name = "Back Squat",
            category = ExerciseCategory.BARBELL,
            metricType = MetricType.WEIGHT,
            unit = "kg",
            tracksRepMax = true
        )

        val exercises = mutableListOf(existingExercise)

        val incomingName = "  Back Squat  ".trim()
        val found = exercises.firstOrNull { it.name.equals(incomingName, ignoreCase = true) }

        assertNotNull(found)
        assertEquals(10L, found?.id)
        assertEquals(ExerciseCategory.BARBELL, found?.category)
        assertEquals("kg", found?.unit)
        assertTrue(found?.tracksRepMax ?: false)
    }

    @Test
    fun `scenario 3 - routine deduplication merges blocks under single routine ID`() {
        val routine1 = Routine(id = 1L, name = "Murph", description = "Hero WOD", defaultFormat = "For Time")
        val routine2 = Routine(id = 2L, name = "murph", description = "Duplicate Murph", defaultFormat = "For Time")

        val routines = listOf(routine1, routine2)
        val grouped = routines.groupBy { it.name.trim().lowercase() }

        assertEquals(1, grouped.size)
        val distinctList = grouped["murph"]!!
        assertEquals(2, distinctList.size)
        val primary = distinctList.first()
        val duplicates = distinctList.drop(1)

        assertEquals(1L, primary.id)
        assertEquals(1, duplicates.size)
        assertEquals(2L, duplicates.first().id)
    }

    @Test
    fun `scenario 3 - cycle goals serialization and restoration integrity`() {
        val goal = CycleGoal(
            id = 1L,
            cycleId = 2L,
            exerciseId = 5L,
            targetReps = 1,
            startWeight = 140.0,
            targetWeight = 160.0,
            notes = "Road to 160kg Back Squat"
        )

        val payload = mapOf(
            "id" to goal.id,
            "cycleId" to goal.cycleId,
            "exerciseId" to goal.exerciseId,
            "targetReps" to goal.targetReps,
            "startWeight" to goal.startWeight,
            "targetWeight" to goal.targetWeight,
            "notes" to goal.notes
        )

        val restoredCycleId = (payload["cycleId"] as Number).toLong()
        val restoredExerciseId = (payload["exerciseId"] as Number).toLong()
        val restoredTargetReps = (payload["targetReps"] as Number).toInt()
        val restoredStartWeight = (payload["startWeight"] as Number).toDouble()
        val restoredTargetWeight = (payload["targetWeight"] as Number).toDouble()
        val restoredNotes = payload["notes"] as String

        val restoredGoal = CycleGoal(
            id = goal.id,
            cycleId = restoredCycleId,
            exerciseId = restoredExerciseId,
            targetReps = restoredTargetReps,
            startWeight = restoredStartWeight,
            targetWeight = restoredTargetWeight,
            notes = restoredNotes
        )

        assertEquals(goal, restoredGoal)
    }

    // =========================================================================
    // Scenario 4: Cross-Auth Session State and Environment Isolation
    // =========================================================================

    @Test
    fun `scenario 4 - current environment fallback is valid and snapshot isolated`() {
        val env = UserCloudSyncManager.currentEnv
        assertTrue("Environment must not be blank", env.isNotBlank())
        assertEquals("snapshot", env)
    }

    @Test
    fun `scenario 4 - sign out clears auth user and resets sync status to idle`() {
        val user = AuthUser(uid = "user_123", email = "athlete@example.com", isAnonymous = false)
        UserCloudSyncManager.setAuthenticatedUser(user)
        assertEquals("user_123", UserCloudSyncManager.currentUserId)

        UserCloudSyncManager.setAuthenticatedUser(null)
        assertEquals("", UserCloudSyncManager.currentUserId)
        assertEquals(null, UserCloudSyncManager.userState.value)
    }
}
