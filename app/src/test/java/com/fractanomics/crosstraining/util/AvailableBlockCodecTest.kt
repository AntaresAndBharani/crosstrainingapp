package com.fractanomics.crosstraining.util

import com.fractanomics.crosstraining.data.model.BlockSet
import com.fractanomics.crosstraining.data.model.BlockWithSets
import com.fractanomics.crosstraining.data.model.Exercise
import com.fractanomics.crosstraining.data.model.ExerciseCategory
import com.fractanomics.crosstraining.data.model.MetricType
import com.fractanomics.crosstraining.data.model.RepMax
import com.fractanomics.crosstraining.data.model.Routine
import com.fractanomics.crosstraining.data.model.RoutineBlock
import com.fractanomics.crosstraining.data.model.RoutineWithBlocks
import com.fractanomics.crosstraining.data.model.Session
import com.fractanomics.crosstraining.data.model.SessionBlock
import com.fractanomics.crosstraining.data.model.SessionWithBlocks
import com.fractanomics.crosstraining.ui.resolveRepMaxAttribution
import com.fractanomics.crosstraining.ui.routineBlockPerformances
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate

class AvailableBlockCodecTest {

    private val backSquat = Exercise(
        id = 1L,
        name = "Back Squat",
        category = ExerciseCategory.BARBELL,
        metricType = MetricType.WEIGHT,
        unit = "kg",
        tracksRepMax = true
    )
    private val cleanAndJerk = Exercise(
        id = 2L,
        name = "Clean & Jerk",
        category = ExerciseCategory.BARBELL,
        metricType = MetricType.WEIGHT,
        unit = "kg",
        tracksRepMax = true
    )
    private val burpees = Exercise(
        id = 3L,
        name = "Burpees",
        category = ExerciseCategory.GYMNASTICS,
        metricType = MetricType.REPS,
        unit = "reps",
        tracksRepMax = false
    )

    @Test
    fun `encode and decode tracked PR header round-trips with spaces and special characters`() {
        val slots = listOf(
            TrackedPrSlot(movementId = 1L, movementName = "Back Squat", rmReps = 3),
            TrackedPrSlot(movementId = 2L, movementName = "Clean & Jerk", rmReps = 1),
            // Duplicate should be deduplicated
            TrackedPrSlot(movementId = 1L, movementName = "back squat", rmReps = 3)
        )
        val encoded = AvailableBlockCodec.encodeNotesWithTrackedPrs(
            cleanInstructions = "EMOM 10\nOdd: 3 Back Squat\nEven: 1 Clean & Jerk",
            trackedPrs = slots
        )
        assertTrue(AvailableBlockCodec.hasAbPrsHeader(encoded))

        val decodedSlots = AvailableBlockCodec.decodeTrackedPrHeader(
            notes = encoded,
            exercisesById = mapOf(1L to backSquat, 2L to cleanAndJerk),
            exercisesByLowerName = mapOf("back squat" to backSquat, "clean & jerk" to cleanAndJerk)
        )
        assertEquals(2, decodedSlots.size)
        assertEquals(TrackedPrSlot(1L, "Back Squat", 3), decodedSlots[0])
        assertEquals(TrackedPrSlot(2L, "Clean & Jerk", 1), decodedSlots[1])

        val cleanNotes = RoutineTargetCodec.extractCleanNotes(encoded)
        assertEquals("EMOM 10\nOdd: 3 Back Squat\nEven: 1 Clean & Jerk", cleanNotes)
    }

    @Test
    fun `extractCleanNotes strips ab_prs and rb_v2 headers in any order`() {
        val combined = """
            [ab_prs=Back+Squat:5]
            [rb_v2=REPS:FIXED:5:5.5.5]
            Score: WEIGHT
            Build to heavy 5
        """.trimIndent()

        assertEquals("Build to heavy 5", RoutineTargetCodec.extractCleanNotes(combined))
    }

    @Test
    fun `toRoutineWithBlocks and fromRoutineWithBlocks preserve block metadata and exercises`() {
        val block = AvailableBlock(
            id = 42L,
            title = "Strength Wave A",
            instructions = "5-3-1+ wave loading",
            exercises = listOf(
                AvailableBlockExercise(id = 1L, name = "Back Squat", tracksRepMax = true),
                AvailableBlockExercise(id = 3L, name = "Burpees", tracksRepMax = false)
            ),
            trackedPrs = listOf(
                TrackedPrSlot(movementId = 1L, movementName = "Back Squat", rmReps = 1),
                TrackedPrSlot(movementId = 1L, movementName = "Back Squat", rmReps = 3)
            )
        )

        val rwb = AvailableBlockCodec.toRoutineWithBlocks(block)
        assertEquals(AVAILABLE_BLOCK_FORMAT, rwb.routine.defaultFormat)
        assertEquals("Strength Wave A", rwb.routine.name)

        val decoded = AvailableBlockCodec.fromRoutineWithBlocks(
            rw = rwb,
            exercises = listOf(backSquat, cleanAndJerk, burpees)
        )
        assertEquals(42L, decoded.id)
        assertEquals("Strength Wave A", decoded.title)
        assertEquals("5-3-1+ wave loading", decoded.instructions)
        assertEquals(listOf("Back Squat", "Burpees"), decoded.exercises.map { it.name })
        assertEquals(2, decoded.trackedPrs.size)
        assertEquals(1, decoded.trackedPrs[0].rmReps)
        assertEquals(3, decoded.trackedPrs[1].rmReps)
    }

    @Test
    fun `parseDecimalWeightInput and normalizedPositiveWeight support comma and dot decimals cleanly`() {
        assertEquals(82.5, AvailableBlockCodec.parseDecimalWeightInput("82.5")!!, 0.0001)
        assertEquals(82.5, AvailableBlockCodec.parseDecimalWeightInput(" 82,5 ")!!, 0.0001)
        assertEquals(0.0, AvailableBlockCodec.parseDecimalWeightInput("0")!!, 0.0001)
        assertNull(AvailableBlockCodec.parseDecimalWeightInput(""))
        assertNull(AvailableBlockCodec.parseDecimalWeightInput("abc"))

        assertEquals(100.25, AvailableBlockCodec.normalizedPositiveWeight("100,25")!!, 0.0001)
        assertNull(AvailableBlockCodec.normalizedPositiveWeight("0"))
        assertNull(AvailableBlockCodec.normalizedPositiveWeight("-10"))
        assertNull(AvailableBlockCodec.normalizedPositiveWeight("  "))
    }

    @Test
    fun `evaluateTrackedPrChip distinguishes new PR from existing best and blank weights`() {
        val slot = TrackedPrSlot(movementId = 1L, movementName = "Back Squat", rmReps = 3)

        val firstPr = AvailableBlockCodec.evaluateTrackedPrChip(slot, 120.0, 0.0)
        assertTrue(firstPr.isNewPr)
        assertEquals(0.0, firstPr.storedBestWeight, 0.001)

        val tiePr = AvailableBlockCodec.evaluateTrackedPrChip(slot, 120.0, 120.0)
        assertFalse(tiePr.isNewPr)
        assertEquals(120.0, tiePr.storedBestWeight, 0.001)

        val beatPr = AvailableBlockCodec.evaluateTrackedPrChip(slot, 122.5, 120.0)
        assertTrue(beatPr.isNewPr)

        val blankInput = AvailableBlockCodec.evaluateTrackedPrChip(slot, 0.0, 120.0)
        assertFalse(blankInput.isNewPr)
    }

    @Test
    fun `resolveLastBlockSession finds most recent matching session weights by routineId or subBlock`() {
        val block = AvailableBlock(
            id = 10L,
            title = "Front + Back Wave",
            instructions = "Wave sets",
            exercises = listOf(
                AvailableBlockExercise(1L, "Back Squat", true),
                AvailableBlockExercise(3L, "Burpees", false)
            ),
            trackedPrs = emptyList()
        )
        val oldSession = SessionWithBlocks(
            session = Session(id = 1L, cycleId = 1L, date = LocalDate.of(2026, 10, 1), title = "Old"),
            blocks = listOf(
                BlockWithSets(
                    block = SessionBlock(
                        id = 101L,
                        sessionId = 1L,
                        position = 0,
                        name = "Back Squat",
                        mainExerciseId = 1L,
                        routineId = 10L,
                        subBlock = "Front + Back Wave"
                    ),
                    sets = listOf(BlockSet(blockId = 101L, position = 0, reps = 1, weight = 90.0))
                )
            )
        )
        val latestSession = SessionWithBlocks(
            session = Session(id = 2L, cycleId = 1L, date = LocalDate.of(2026, 11, 10), title = "Latest"),
            blocks = listOf(
                BlockWithSets(
                    block = SessionBlock(
                        id = 201L,
                        sessionId = 2L,
                        position = 0,
                        name = "Back Squat",
                        mainExerciseId = 1L,
                        routineId = 10L,
                        subBlock = "Front + Back Wave"
                    ),
                    sets = listOf(
                        BlockSet(blockId = 201L, position = 0, reps = 1, weight = 100.0),
                        BlockSet(blockId = 201L, position = 1, reps = 1, weight = 105.0)
                    )
                ),
                BlockWithSets(
                    block = SessionBlock(
                        id = 202L,
                        sessionId = 2L,
                        position = 1,
                        name = "Burpees",
                        mainExerciseId = 3L,
                        routineId = 10L,
                        subBlock = "Front + Back Wave"
                    ),
                    sets = listOf(BlockSet(blockId = 202L, position = 0, reps = 1, weight = null))
                )
            )
        )

        val summary = AvailableBlockCodec.resolveLastBlockSession(
            block = block,
            sessions = listOf(oldSession, latestSession),
            exercises = listOf(backSquat, burpees),
            weightUnit = "kg"
        )!!
        assertEquals("2026-11-10", summary.dateIso)
        assertEquals(listOf(100.0, 105.0), summary.exerciseWeightsById[1L])
        assertEquals(listOf<Double?>(null), summary.exerciseWeightsById[3L])
        assertEquals(105.0, summary.topWeight!!, 0.001)
    }

    @Test
    fun `ProgressAnalytics isolates routineBlockPerformances and resolves RepMax block attribution`() {
        val routineA = Routine(id = 10L, name = "Squat Wave", mainExerciseId = 1L)
        val routineB = Routine(id = 20L, name = "Heavy Complex", mainExerciseId = 1L)

        val session = SessionWithBlocks(
            session = Session(id = 5L, cycleId = 1L, date = LocalDate.of(2026, 11, 12), title = "Friday"),
            blocks = listOf(
                BlockWithSets(
                    block = SessionBlock(
                        id = 50L,
                        sessionId = 5L,
                        position = 0,
                        name = "Back Squat",
                        mainExerciseId = 1L,
                        routineId = 20L,
                        subBlock = "Heavy Complex"
                    ),
                    sets = listOf(BlockSet(blockId = 50L, position = 0, reps = 1, weight = 140.0))
                )
            )
        )

        // routineA shares mainExerciseId = 1L, but the logged block explicitly belongs to routineB (id = 20L)!
        val perfA = listOf(session).routineBlockPerformances(routineId = 10L, routine = routineA)
        assertTrue(perfA.isEmpty())

        val perfB = listOf(session).routineBlockPerformances(routineId = 20L, routine = routineB)
        assertEquals(1, perfB.size)

        val attributedRm = RepMax(
            id = 99L,
            exerciseId = 1L,
            reps = 1,
            weight = 140.0,
            date = LocalDate.of(2026, 11, 12),
            cycleId = 1L,
            sessionId = 5L,
            blockId = 50L
        )
        val attribution = resolveRepMaxAttribution(
            repMax = attributedRm,
            sessions = listOf(session),
            routines = listOf(routineA, routineB),
            exerciseName = "Back Squat"
        )
        assertEquals("Heavy Complex", attribution.blockTitle)
        assertTrue(attribution.formattedLabel.startsWith("Achieved in Heavy Complex · "))

        val standaloneRm = RepMax(
            id = 100L,
            exerciseId = 1L,
            reps = 3,
            weight = 120.0,
            date = LocalDate.of(2026, 11, 12)
        )
        val standaloneAttribution = resolveRepMaxAttribution(
            repMax = standaloneRm,
            sessions = listOf(session),
            routines = listOf(routineA, routineB),
            exerciseName = "Back Squat"
        )
        assertNull(standaloneAttribution.blockTitle)
    }
    @Test
    fun `ab_prs header is never decoded as rb_v2 target slots and rb_v2 still decodes when ab_prs follows it`() {
        assertNull(RoutineTargetCodec.decodeHeaderSlots("[ab_prs=Back+Squat:3]\nBuild heavy", 3))
        // An empty `[ab_prs=]` payload must not be treated as an (empty) structured rb_v2 header.
        assertNull(RoutineTargetCodec.decodeHeaderSlots("[ab_prs=]\nBuild heavy", 3))

        val combined = "[rb_v2=REPS:PER_SET:5:5.0.4.0]\n[ab_prs=Back+Squat:3]\nCrisp reps"
        val slots = RoutineTargetCodec.decodeHeaderSlots(combined, 4)
        assertNotNull(slots)
        assertEquals(listOf(5, 0, 4, 0), slots!![0].perSetValues)
        assertEquals("Crisp reps", RoutineTargetCodec.extractCleanNotes(combined))
    }

    @Test
    fun `replaceTrackedPrHeader keeps leading rb_v2 header and swaps only the ab_prs line`() {
        val legacyNotes = "[rb_v2=REPS:PER_SET:5:5.0.4.0]\n[ab_prs=Back+Squat:3]\nCrisp reps"
        val replaced = AvailableBlockCodec.replaceTrackedPrHeader(
            legacyNotes,
            listOf(TrackedPrSlot(backSquat.id, backSquat.name, 1))
        )
        assertEquals("[rb_v2=REPS:PER_SET:5:5.0.4.0]\n[ab_prs=Back+Squat:1]\nCrisp reps", replaced)
        assertTrue(RoutineTargetCodec.hasStructuredHeader(replaced))
        assertEquals(
            "[rb_v2=REPS:PER_SET:5:5.0.4.0]\nCrisp reps",
            AvailableBlockCodec.replaceTrackedPrHeader(legacyNotes, emptyList())
        )
        assertEquals(
            "[ab_prs=Back+Squat:1]\nPlain notes",
            AvailableBlockCodec.replaceTrackedPrHeader("Plain notes", listOf(TrackedPrSlot(backSquat.id, backSquat.name, 1)))
        )
    }

    @Test
    fun `applyTrackedPrsToRoutineBlocks preserves legacy routine block structure`() {
        val legacyBlocks = listOf(
            RoutineBlock(
                id = 11L, routineId = 7L, position = 0, name = "Strength", format = "EMOM",
                setsCount = 4, targetRepsScheme = "5,0,4,0", exerciseIdsCsv = "1",
                notes = "[rb_v2=REPS:PER_SET:5:5.0.4.0]\nCrisp reps"
            ),
            RoutineBlock(
                id = 12L, routineId = 7L, position = 1, name = "Metcon", format = "For Time",
                setsCount = 1, exerciseIdsCsv = "3", notes = "[ab_prs=Back+Squat:5]\nNegative split"
            )
        )
        val updated = AvailableBlockCodec.applyTrackedPrsToRoutineBlocks(
            legacyBlocks,
            listOf(TrackedPrSlot(backSquat.id, backSquat.name, 3))
        )
        assertEquals(2, updated.size)
        assertEquals("EMOM", updated[0].format)
        assertEquals(4, updated[0].setsCount)
        assertEquals("[rb_v2=REPS:PER_SET:5:5.0.4.0]\n[ab_prs=Back+Squat:3]\nCrisp reps", updated[0].notes)
        assertEquals("Negative split", updated[1].notes)

        val decoded = AvailableBlockCodec.fromRoutineWithBlocks(
            RoutineWithBlocks(Routine(id = 7L, name = "Thursday Engine"), updated),
            listOf(backSquat, cleanAndJerk, burpees)
        )
        assertEquals(listOf(TrackedPrSlot(backSquat.id, backSquat.name, 3)), decoded.trackedPrs)
    }
}
