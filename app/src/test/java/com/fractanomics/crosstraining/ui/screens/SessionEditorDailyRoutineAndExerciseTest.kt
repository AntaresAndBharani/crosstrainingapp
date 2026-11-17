package com.fractanomics.crosstraining.ui.screens

import com.fractanomics.crosstraining.data.model.BlockKind
import com.fractanomics.crosstraining.data.model.BlockSet
import com.fractanomics.crosstraining.data.model.BlockWithSets
import com.fractanomics.crosstraining.data.model.Exercise
import com.fractanomics.crosstraining.data.model.ExerciseCategory
import com.fractanomics.crosstraining.data.model.MetricType
import com.fractanomics.crosstraining.data.model.RepMax
import com.fractanomics.crosstraining.data.model.Session
import com.fractanomics.crosstraining.data.model.SessionBlock
import com.fractanomics.crosstraining.data.model.SessionWithBlocks
import com.fractanomics.crosstraining.ui.trimmed
import com.fractanomics.crosstraining.util.AvailableBlock
import com.fractanomics.crosstraining.ui.trimmed
import com.fractanomics.crosstraining.util.AvailableBlockCodec
import com.fractanomics.crosstraining.ui.trimmed
import com.fractanomics.crosstraining.util.AvailableBlockExercise
import com.fractanomics.crosstraining.util.DailyRoutine
import com.fractanomics.crosstraining.util.DailyRoutineCodec
import com.fractanomics.crosstraining.util.DailyRoutineItem
import com.fractanomics.crosstraining.util.STANDALONE_EXERCISE_SESSION_FORMAT
import com.fractanomics.crosstraining.util.TrackedPrSlot
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate

class SessionEditorDailyRoutineAndExerciseTest {

    private val backSquat = Exercise(
        id = 1L,
        name = "Back Squat",
        category = ExerciseCategory.BARBELL,
        metricType = MetricType.WEIGHT,
        unit = "kg",
        tracksRepMax = true
    )
    private val rdl = Exercise(
        id = 2L,
        name = "Romanian Deadlift",
        category = ExerciseCategory.BARBELL,
        metricType = MetricType.WEIGHT,
        unit = "kg",
        tracksRepMax = true
    )
    private val hipThrust = Exercise(
        id = 3L,
        name = "Barbell Hip Thrust",
        category = ExerciseCategory.ACCESSORY,
        metricType = MetricType.WEIGHT,
        unit = "kg",
        tracksRepMax = true
    )
    private val exercises = listOf(backSquat, rdl, hipThrust)

    private val squatBlock = AvailableBlock(
        id = 10L,
        title = "Squat Wave",
        instructions = "3x5 @ RPE 8",
        exercises = listOf(AvailableBlockExercise(1L, "Back Squat", true)),
        trackedPrs = listOf(TrackedPrSlot(1L, "Back Squat", 5))
    )

    @Test
    fun `expandDailyRoutineForSession expands mixed BlockRef and StandaloneExercise with unified prefill and 4 empty tiles`() {
        val priorDate = LocalDate.of(2026, 11, 10)
        // History has Back Squat inside a block AND RDL logged as a standalone exercise,
        // while Barbell Hip Thrust has never been logged yet.
        val priorSession = SessionWithBlocks(
            session = Session(id = 5L, cycleId = 1L, date = priorDate, title = "Prior Lower"),
            blocks = listOf(
                BlockWithSets(
                    block = SessionBlock(
                        id = 51L,
                        sessionId = 5L,
                        position = 0,
                        name = "Back Squat",
                        kind = BlockKind.STRENGTH,
                        mainExerciseId = 1L,
                        routineId = 10L,
                        subBlock = "Squat Wave"
                    ),
                    sets = listOf(
                        BlockSet(blockId = 51L, position = 0, reps = 5, weight = 100.0),
                        BlockSet(blockId = 51L, position = 1, reps = 5, weight = 105.0)
                    )
                ),
                BlockWithSets(
                    block = SessionBlock(
                        id = 52L,
                        sessionId = 5L,
                        position = 1,
                        name = "Romanian Deadlift",
                        kind = BlockKind.STRENGTH,
                        format = STANDALONE_EXERCISE_SESSION_FORMAT,
                        mainExerciseId = 2L
                    ),
                    sets = listOf(
                        BlockSet(blockId = 52L, position = 0, reps = 8, weight = 80.0),
                        BlockSet(blockId = 52L, position = 1, reps = 8, weight = 85.0),
                        BlockSet(blockId = 52L, position = 2, reps = 8, weight = 85.0)
                    )
                )
            )
        )

        val routine = DailyRoutine(
            id = 50L,
            title = "Lower Day",
            notes = "",
            items = listOf(
                DailyRoutineItem.BlockRef(10L, "Squat Wave", squatBlock),
                DailyRoutineItem.StandaloneExercise(2L, "Romanian Deadlift", note = "3s eccentric"),
                // Deleted block ref (unresolvable) should be safely skipped
                DailyRoutineItem.BlockRef(999L, "Deleted Block", resolvedBlock = null),
                DailyRoutineItem.StandaloneExercise(3L, "Barbell Hip Thrust", note = "")
            )
        )

        val expanded = expandDailyRoutineForSession(
            routine = routine,
            availableBlocks = listOf(squatBlock),
            sessions = listOf(priorSession),
            exercises = exercises,
            repMaxes = listOf(
                RepMax(id = 1L, exerciseId = 2L, reps = 8, weight = 85.0, date = priorDate, sessionId = 5L)
            ),
            weightUnit = "kg"
        )

        assertEquals(3, expanded.size)

        // Item 0: Block
        assertEquals(SessionItemKind.BLOCK, expanded[0].itemKind)
        assertEquals("Squat Wave", expanded[0].block.title)
        assertEquals(listOf("100", "105"), expanded[0].exerciseSections[0].weights.toList())
        assertTrue(expanded[0].exerciseSections[0].lastSessionDateLabel!!.startsWith("Pre-filled from last session ("))

        // Item 1: Standalone RDL (pre-filled + inherited 8RM from prior RM + Routine Note preserved)
        assertEquals(SessionItemKind.EXERCISE, expanded[1].itemKind)
        assertEquals("3s eccentric", expanded[1].block.instructions)
        assertEquals(listOf("80", "85", "85"), expanded[1].exerciseSections[0].weights.toList())
        assertTrue(expanded[1].exerciseSections[0].lastSessionDateLabel!!.startsWith("Pre-filled from last session ("))
        assertEquals(listOf(TrackedPrSlot(2L, "Romanian Deadlift", 8)), expanded[1].trackedPrSlots.toList())

        // Item 2: Unlogged Standalone Hip Thrust -> 4 empty tiles + "First time logging" subtitle
        assertEquals(SessionItemKind.EXERCISE, expanded[2].itemKind)
        assertEquals(listOf("", "", "", ""), expanded[2].exerciseSections[0].weights.toList())
        assertEquals("First time logging — enter set weights", expanded[2].exerciseSections[0].lastSessionDateLabel)
    }

    @Test
    fun `standalone exercise live PR auto-sync emits STANDALONE_EXERCISE draft with null routineId`() {
        val state = buildLoadedStandaloneExerciseState(
            exercise = rdl,
            note = "Paused at shin",
            defaultTrackedPrs = listOf(TrackedPrSlot(2L, "Romanian Deadlift", 5)),
            sessions = emptyList(),
            repMaxes = listOf(
                RepMax(id = 1L, exerciseId = 2L, reps = 5, weight = 90.0, date = LocalDate.of(2026, 11, 1), sessionId = 1L)
            ),
            weightUnit = "kg"
        )

        assertEquals(SessionItemKind.EXERCISE, state.itemKind)
        assertNull(state.inheritedTrackedPrSlotsForParent(exercises))

        val exState = state.exerciseSections.single()
        state.updateSetWeight(exState, setIndex = 0, rawValue = "95")

        val chips = state.evaluatePrChips(
            exercises = exercises,
            repMaxes = listOf(
                RepMax(id = 1L, exerciseId = 2L, reps = 5, weight = 90.0, date = LocalDate.of(2026, 11, 1), sessionId = 1L)
            )
        )
        assertEquals(1, chips.size)
        assertTrue(chips.single().second.isNewPr)
        assertEquals(95.0, chips.single().second.candidateWeight, 0.001)

        val drafts = state.toBlockDrafts(exercises, emptyList())
        assertEquals(1, drafts.size)
        val draft = drafts.single()
        assertEquals(STANDALONE_EXERCISE_SESSION_FORMAT, draft.format)
        assertNull(draft.routineId)
        assertEquals("", draft.subBlock)
        assertEquals("Paused at shin", draft.description)
        assertEquals(2L, draft.existingExerciseId)
        assertEquals(listOf(95.0), draft.sets.mapNotNull { it.weight })
        assertEquals(1, draft.newRepMaxes.size)
        assertEquals(95.0, draft.newRepMaxes.single().weight, 0.001)
    }

    @Test
    fun `edit session hydrates STANDALONE_EXERCISE into SimplifiedExerciseLogCard state`() {
        val date = LocalDate.of(2026, 11, 15)
        val session = SessionWithBlocks(
            session = Session(id = 33L, cycleId = 1L, date = date, title = "Lower Day"),
            blocks = listOf(
                BlockWithSets(
                    block = SessionBlock(
                        id = 301L,
                        sessionId = 33L,
                        position = 0,
                        name = "Romanian Deadlift",
                        kind = BlockKind.STRENGTH,
                        format = STANDALONE_EXERCISE_SESSION_FORMAT,
                        mainExerciseId = 2L,
                        routineId = null,
                        description = "Tempo 3010",
                        subBlock = ""
                    ),
                    sets = listOf(
                        BlockSet(blockId = 301L, position = 0, reps = 8, weight = 87.5),
                        BlockSet(blockId = 301L, position = 1, reps = 8, weight = 87.5)
                    )
                )
            )
        )
        val sessionRm = RepMax(id = 88L, exerciseId = 2L, reps = 8, weight = 87.5, date = date, sessionId = 33L, blockId = 301L)

        val hydrated = AvailableBlockCodec.hydrateSessionBlocksForEditor(
            session = session,
            availableBlocks = listOf(squatBlock),
            exercises = exercises,
            sessionRepMaxes = listOf(sessionRm)
        )

        assertTrue(hydrated.residualLegacyBlocks.isEmpty())
        assertEquals(1, hydrated.simplifiedBlocks.size)
        assertTrue(hydrated.simplifiedBlocks.single().isStandaloneExercise)

        val cardState = buildHydratedBlockState(hydrated.simplifiedBlocks.single(), exercises, editingSessionId = 33L)
        assertEquals(SessionItemKind.EXERCISE, cardState.itemKind)
        assertNull(cardState.exerciseSections.single().lastSessionDateLabel)
        assertEquals("Tempo 3010", cardState.block.instructions)
        assertEquals(listOf(87.5.trimmed(), 87.5.trimmed()), cardState.exerciseSections.single().weights.toList())
    }

    @Test
    fun `hasStructuralDiffFromRoutine stays false when unchanged and flips true on add remove reorder or custom block`() {
        val routine = DailyRoutine(
            id = 50L,
            title = "Lower Day",
            notes = "",
            items = listOf(
                DailyRoutineItem.BlockRef(10L, "Squat Wave", squatBlock),
                DailyRoutineItem.StandaloneExercise(2L, "Romanian Deadlift", note = "3s eccentric")
            )
        )
        val initialSig = DailyRoutineCodec.routineItemsSignature(routine.items)
        val states = expandDailyRoutineForSession(
            routine = routine,
            availableBlocks = listOf(squatBlock),
            sessions = emptyList(),
            exercises = exercises,
            repMaxes = emptyList(),
            weightUnit = "kg"
        ).toMutableList()

        // Unchanged structure
        assertFalse(hasStructuralDiffFromRoutine(initialSig, states))
        assertNotNull(deriveRoutineItemsFromSessionStates(states))

        // Weight edits alone do NOT trigger structural diff
        states[1].updateSetWeight(states[1].exerciseSections.single(), 0, "100")
        assertFalse(hasStructuralDiffFromRoutine(initialSig, states))

        // Adding a standalone exercise triggers structural diff
        states.add(
            buildLoadedStandaloneExerciseState(
                exercise = hipThrust,
                note = "Pause at top",
                sessions = emptyList(),
                repMaxes = emptyList()
            )
        )
        assertTrue(hasStructuralDiffFromRoutine(initialSig, states))
        val derived = deriveRoutineItemsFromSessionStates(states)!!
        assertEquals(3, derived.size)
        assertEquals("Barbell Hip Thrust", (derived[2] as DailyRoutineItem.StandaloneExercise).exerciseName)

        // Adding an unsaved custom block (id == 0L) marks structural diff and returns null template derivation
        states.add(
            buildCustomAvailableBlockState(
                AvailableBlock(0L, "Ad-hoc", "", listOf(AvailableBlockExercise(1L, "Back Squat", true)), emptyList())
            )
        )
        assertNull(deriveRoutineItemsFromSessionStates(states))
        assertTrue(hasStructuralDiffFromRoutine(initialSig, states))
    }
}
